package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.factory.FactoryEngine;
import com.reborn.modularmachinery.factory.FactoryHost;
import com.reborn.modularmachinery.factory.FactoryThread;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import com.reborn.modularmachinery.machine.ControllerStatus;
import com.reborn.modularmachinery.machine.ControllerTiming;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineModifier;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.machine.ParallelController;
import com.reborn.modularmachinery.machine.ParallelControllerCollection;
import com.reborn.modularmachinery.machine.ParallelismLimit;
import com.reborn.modularmachinery.machine.SmartInterfaceStore;
import com.reborn.modularmachinery.machine.SmartInterfaceType;
import com.reborn.modularmachinery.machine.SmartInterfaceValueSource;
import com.reborn.modularmachinery.machine.SmartInterfaceValues;
import com.reborn.modularmachinery.menu.FactoryControllerMenu;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import com.reborn.modularmachinery.upgrade.UpgradeBusUtility;
import com.reborn.modularmachinery.upgrade.UpgradeEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The machine controller.
 *
 * <p>It gathers its ports from the formed structure and drives crafting through the recipe lifecycle the
 * original used: item and fluid <b>inputs are paid when the craft begins</b>, energy and per-tick fluids are
 * drawn <b>every tick</b>, and outputs are produced at the end with each output's chance rolled on its own.
 *
 * <p>The controller keeps <b>one slot</b> — the blueprint — and exposes <b>no capabilities</b>, exactly as the
 * original did. Everything a machine consumes or produces has to pass through a hatch, which is what makes
 * hatches worth placing. Earlier releases gave the controller two working slots plus an FE buffer and fluid
 * tank, and folded them in as fallback ports; that Reborn-only convenience is gone.
 *
 * <h2>M6c: modifiers, parallelism and timing</h2>
 *
 * <p>Three things were added in 0.19.0, all of them per-formed-structure state:
 *
 * <ul>
 *   <li>{@link #activeModifiers()} — the recipe modifiers contributed by the definition's {@code modifiers}
 *       entries whose block really is present at the stated position. Recomputed whenever the structure is
 *       re-checked, so removing the vent above the alloy furnace takes its doubled output away on the next
 *       check.</li>
 *   <li>{@link #parallelLimit()} / {@link #parallelism()} — the original's
 *       {@code TileMultiblockMachineController#getMaxParallelism} and {@code ActiveMachineRecipe#getParallelism}:
 *       a numerical reduction over the structure, not a thread pool.</li>
 *   <li>{@link #timing()} — the {@code TimeRecorder} stand-in behind the screen's closing line.</li>
 * </ul>
 *
 * <h2>M6b: the upgrade buses</h2>
 *
 * <p>{@link #upgradeBuses()} is a third bucket beside the hatches and the parallel controllers, filled by the
 * same walk over the definition's own positions. It contributes recipe modifiers and nothing else, and they are
 * concatenated onto the structural ones through {@link RecipeModifiers#andThen} — so a craft sees
 * <b>one</b> {@code RecipeModifiers}, exactly as it did before, with the bus's share arriving through the same
 * path the machine JSON's {@code modifiers} array already used.
 *
 * <h2>M6e: the two engines</h2>
 *
 * <p>A controller now has two mutually exclusive ways to run recipes, and which one is used is decided once per
 * structure check by {@link #updateFactoryMode}:
 *
 * <ul>
 *   <li><b>The single-craft engine</b> — everything from M6c/M6d/M6b, unchanged. One recipe, one progress bar,
 *       the parallelism reduction applied to it.</li>
 *   <li><b>The factory engine</b> ({@link FactoryEngine}) — several <b>different</b> recipes at once, one per
 *       {@link FactoryThread}. This is the original's {@code TileFactoryController}: a thread is a recipe-running
 *       state slot, not an OS thread (the original's own class comment says so, and its code agrees).</li>
 * </ul>
 *
 * <p>The split is deliberately a branch and not a mode flag threaded through the single-craft code: the
 * non-factory path is regression-free by construction, and the factory path is a pure, {@code Level}-free
 * package ({@code com.reborn.modularmachinery.factory}) that the offline harness drives directly.
 *
 * <h2>M6d-b: the smart data interface, folded in</h2>
 *
 * <p>The original had a <b>separate block</b> for this — {@code BlockSmartInterface} /
 * {@code TileSmartInterface}, a structure component collected through
 * {@code foundSmartInterfaces} ({@code TileMultiblockMachineController:135}) and bound by
 * {@code checkAndAddSmartInterface} ({@code :909}). The project owner's decision (the design document
 * {@code docs/专项/M6d-b-智能数据接口并入控制器.md}) is that this block is <b>not</b> ported: its value storage
 * moves here and its editing UI into this controller's screen. See the D16 record in {@code 移植方案-v2.md}.
 *
 * <p><b>The access path is unchanged</b>, which is what makes the merge lossless: the original's only readers
 * were already controller methods ({@code getSmartInterfaceData(String)}, {@code getSmartInterfaceDataList()}),
 * and the only consumer in the whole original — {@code RequirementInterfaceNumInput} — received its data from
 * the controller's own {@code ProcessingComponent}. Here the controller <b>is</b> the
 * {@link SmartInterfaceValueSource}, {@link #collectHatches} hands itself to the per-craft
 * {@link HatchCollection}, and the requirement reads {@code ports.smartInterfaceValue(type)}. The intermediate
 * block disappears; the chain
 * {@code requirement -> controller -> value} does not.
 *
 * <p>{@link #bindSmartInterfaces} is the projection of {@code checkAndAddSmartInterface}: it fills in a declared
 * type's default when the controller has no value for it yet, and it drops values whose type the current machine
 * does not declare — the original's own behaviour, and the reason a torn-down structure leaves no stale value
 * behind to be read.
 */
public final class MachineControllerBlockEntity extends BlockEntity implements MenuProvider, SmartInterfaceValueSource {

    /** The original controller had exactly one slot, at {@code TileMultiblockMachineController.BLUEPRINT_SLOT}. */
    public static final int BLUEPRINT_SLOT = 0;

    /** Structure checks are retried on this interval instead of every tick. */
    private static final int STRUCTURE_CHECK_INTERVAL = 10;

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    private int progress;
    private int maxProgress = 1;
    private boolean formed;
    private boolean redstoneStopped;
    private ControllerStatus status = ControllerStatus.MISSING_STRUCTURE;
    private int structureCheckCountdown;

    /** Registry name of the machine currently formed here, or {@code null} when nothing matches. */
    @Nullable
    private ResourceLocation machineId;

    /** The machine this controller is tied to, or {@code null} for the generic controller. */
    @Nullable
    private final ResourceLocation boundMachine;

    /**
     * Whether this block is a factory controller — the original's {@code BlockFactoryController}.
     *
     * <p>It is read from the block at construction and never changes. Together with the formed machine's
     * {@code has-factory} it decides which of the two engines in {@link #tick} runs; see
     * {@link FactoryThreadModel#factoryEnabled}.
     */
    private final boolean factoryBlock;

    /** The recipe currently being crafted, kept so a running craft is not re-matched away from it. */
    @Nullable
    private ResourceLocation activeRecipeId;

    /** The ports gathered from the formed structure. */
    private HatchCollection hatches = HatchCollection.EMPTY;

    /**
     * The parallel controllers found in the formed structure — M6d's contribution to the ceiling.
     *
     * <p>It is a collection of its own rather than a bucket inside {@link HatchCollection}, because a parallel
     * controller is <b>not</b> a hatch: routing it through {@code HatchKind} would make it a port the recipe
     * engine may draw from, and would claim a place in the set of blocks a structure position accepts. The
     * scoping document calls this out explicitly (§5.2). The walk is shared, the buckets are not.
     */
    private ParallelControllerCollection parallelControllers = ParallelControllerCollection.EMPTY;

    /**
     * The upgrade buses found in the formed structure — M6b's contribution to the recipe modifiers.
     *
     * <p>A third bucket beside {@link HatchCollection} and {@link ParallelControllerCollection}, collected by
     * the <b>same</b> walk over {@code machine.pattern().positions()}. A bus is not a hatch for the same reason
     * a parallel controller is not: routing it through {@code HatchKind} would make it a port the recipe engine
     * may draw from (scoping document §5.2).
     *
     * <p>The upgrade slots are never read here. What the controller takes from a bus is
     * {@link UpgradeBusUtility#modifiersFor}, one {@code RecipeModifiers} — the numbers, not the inventory.
     */
    private List<UpgradeBusUtility> upgradeBuses = List.of();

    /** The recipe modifiers the buses in the structure contribute, recomputed with the structure. */
    private RecipeModifiers busModifiers = RecipeModifiers.EMPTY;

    /** Counter-clockwise quarter turns the matching rotation applied, needed to place modifiers. */
    private int rotationSteps;

    /** Structure modifiers whose block is present, recomputed with the structure. */
    private List<MachineModifier> activeModifiers = List.of();

    /** The recipe modifiers they contribute, flattened once per structure check. */
    private RecipeModifiers modifiers = RecipeModifiers.EMPTY;

    /** How the craft being run was settled when it started: frozen for its whole duration. */
    private int activeParallelism = 1;

    /** How long the running craft takes after duration modifiers. */
    private int activeDuration = 1;

    /** The ceiling the last recipe search computed, i.e. what the screen's "max parallelism" line shows. */
    private int parallelCeiling = 1;

    // ------------------------------------------------------- M6e: the factory engine

    /**
     * The multi-thread engine, used only while this block is a factory controller <b>and</b> the formed machine
     * declares {@code has-factory}.
     *
     * <p>One controller therefore has two mutually exclusive engines rather than a mode flag threaded through
     * every method of {@link #tick}: {@link #factoryEnabled()} decides which one runs, and only the single-craft
     * engine was touched by M6c/M6d/M6b, so the non-factory path is bit-for-bit what it was. See the D15 record.
     */
    private final FactoryEngine factoryEngine;

    /** Whether the last structure check concluded "factory": a factory block plus a {@code has-factory} machine. */
    /** The original's {@code IMachineController#getExtraThreadCount} ({@code IMachineController.java:109-110}). */
    public int extraThreadCount() {
        return this.extraThreadCount;
    }

    /**
     * The original's {@code IMachineController#setExtraThreadCount} ({@code IMachineController.java:115-116}).
     *
     * <p>Clamped at zero from below: the original's setter stored whatever it was handed, and a negative count
     * would subtract from a machine's ceiling — turning "give this controller more threads" into a way to make it
     * unable to run anything. The upper bound is the same one the schema enforces on a declared {@code max-threads}
     * ({@code MachineSchema}: a thread count cannot be negative), applied here for the same reason.
     *
     * <p><b>No in-tree caller yet.</b> The original reached this from its controller GUI and from scripts; this port
     * has neither wired to it, so the value only ever arrives from a save file today. It is public and documented
     * because the KubeJS entry point is where it will be called from, and a setter nobody can see is worse than one
     * that says out loud that nothing calls it.
     */
    public void setExtraThreadCount(int value) {
        this.extraThreadCount = Math.max(0, value);
        setChanged();
    }

    private boolean factoryEnabled;

    /**
     * The original's {@code extraThreadCount} ({@code TileFactoryController.java:51}): a delta added on top of the
     * machine definition's {@code max-threads}, not a replacement for it.
     *
     * <p>Its own arithmetic is {@code getMaxThreads() == extraThreadCount + foundMachine.getMaxThreads()}
     * ({@code TileFactoryController.java:455-457}), and the original persisted it under {@code "extraThreadCount"}
     * as a <b>short</b> ({@code :591} / {@code :659}) — kept here, because a save's value should survive a round
     * trip through either build.
     *
     * <p><b>Why the field is here at all, given nothing raises it yet.</b> The original reached it two ways and
     * neither exists in this port today: it exposed it to scripts as {@code IMachineController}'s
     * {@code extraThreadCount} ZenGetter/ZenSetter ({@code IMachineController.java:109-116}), and it carried an NBT
     * value across loads. The first is the KubeJS entry point's business (a later wave), but the second is a
     * <b>persistence contract</b>: a save written by the original holds this number, and a port that dropped it
     * would silently lower a machine's thread ceiling on load. So the storage, the round trip and the arithmetic
     * land now; the setter has no in-tree caller yet and is documented as such rather than left to look used.
     */
    private int extraThreadCount;

    /** Last working status the factory reported, so a client update is sent only when it flips. */
    private boolean factoryWasWorking;

    /** Signature of the last thread list pushed to clients; see {@link #syncFactoryThreads}. */
    private String factorySyncSignature = "";

    /**
     * The client's mirror of the machine's {@code max-threads}, written by {@code FactoryControllerMenu}'s
     * container data. The machine definition is not available on a dedicated-server client, so this number has
     * to arrive from the server rather than being recomputed here.
     */
    private int clientFactoryMaxThreads = FactoryThreadModel.DEFAULT_MAX_THREADS;

    private final ControllerTiming timing = new ControllerTiming();

    /** Last recorded averages, mirrored to the client through the menu. */
    private int clientUsedTimeAvg;
    private int clientSearchTimeAvg;

    // ------------------------------------------------------- M6d-b: the smart data interface

    /**
     * The values this controller holds for its machine's declared interface types.
     *
     * <p>A {@link SmartInterfaceStore} rather than a bare map, so the whole of M6d-b's state — the reconcile, the
     * write path and the NBT round trip — is drivable without a world; see that class for why.
     */
    private final SmartInterfaceStore smartInterfaces = new SmartInterfaceStore();

    /**
     * The failure key the last recipe search produced, or {@code null} when nothing failed.
     *
     * <p>Kept so {@code interface_number_input}'s two failure messages — the original's
     * {@code craftcheck.failure.interface.number.notequal} and
     * {@code component.missing.modularmachinery.interface.number} — can be shown instead of a bare "idle". The
     * key itself is a string, so the menu mirrors it as one of a small fixed set of indices
     * ({@link #failureIndex}).
     */
    @Nullable
    private String startFailureKey;

    /**
     * The client's mirror of {@link #startFailureKey}; see {@code MachineControllerMenu#startFailureOrdinal}.
     *
     * <p>Initialised to {@link #NO_START_FAILURE}, not left at the field's {@code 0}. {@code 0} is a <b>valid</b>
     * index — the first entry of {@link #START_FAILURE_KEYS} — so a freshly opened screen would render a spurious
     * failure message for a machine that has none until the first {@code ContainerData} sync arrived.
     */
    private int clientStartFailure = NO_START_FAILURE;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MACHINE_CONTROLLER_ENTITY.get(), pos, state);
        this.boundMachine = state.getBlock() instanceof MachineControllerBlock controller
                ? controller.boundMachine() : null;
        this.factoryBlock = state.getBlock() instanceof MachineControllerBlock controller && controller.factory();
        this.factoryEngine = new FactoryEngine(new FactoryHostImpl());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MachineControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        long tickStart = System.nanoTime();
        long searchNanos = 0L;

        // The original read the strong power into the controller every tick and refused to work while it was
        // powered. getDirectSignalTo is the 1.20.1 counterpart of 1.12.2's World#getStrongPower.
        be.redstoneStopped = level.getDirectSignalTo(pos) > 0;

        if (be.structureCheckCountdown-- <= 0) {
            be.structureCheckCountdown = STRUCTURE_CHECK_INTERVAL;
            long searchStart = System.nanoTime();
            Direction facing = state.getValue(MachineControllerBlock.FACING);
            MachineRegistry.Match match = MachineRegistry.findMatch(level, pos, facing,
                    be.blueprintMachineId(), be.boundMachine);
            searchNanos = System.nanoTime() - searchStart;

            boolean wasFormed = be.formed;
            ResourceLocation previousMachine = be.machineId;
            be.formed = match != null;
            be.machineId = match == null ? null : match.machine().id();
            be.rotationSteps = match == null ? 0 : match.rotationSteps();
            be.hatches = be.collectHatches(level, pos, match == null ? null : match.machine());
            be.parallelControllers = be.collectParallelControllers(level, pos, match == null ? null : match.machine());
            be.upgradeBuses = be.collectUpgradeBuses(level, pos, match == null ? null : match.machine());
            be.activeModifiers = match == null
                    ? List.of()
                    : evaluateModifiers(level, pos, match.machine(), match.rotationSteps());
            // The two sources of RecipeModifier meet here and only here. M6c evaluates the definition's
            // structure modifiers; M6b adds what the buses in the same structure contribute. They stay separate
            // computations (the scoping document's §5.2 forbids merging the two paths) and are concatenated into
            // the one RecipeModifiers the whole recipe lifecycle already takes - RecipeModifiers#andThen exists
            // for exactly this join.
            RecipeModifiers structural = MachineDefinition.flatten(be.activeModifiers);
            if (match != null) {
                // The controller binds itself to each bus it finds, which is the original's
                // UpgradeBusProvider#boundMachine (TileUpgradeBus.java:236-245). Nothing unbinds here: the bus
                // drops a pairing on its own 20-tick reconcile when the controller is gone or has become a
                // different machine, which is also how the original worked (its doRestrictedTick, :66-92).
                for (UpgradeBusUtility bus : be.upgradeBuses) {
                    if (bus.bindMachine(pos, match.machine().id())) {
                        be.setChanged();
                    }
                }
            }
            be.busModifiers = collectBusModifiers(be.upgradeBuses, be.machineId);
            be.modifiers = structural.andThen(be.busModifiers);
            be.updateFactoryMode(match == null ? null : match.machine());
            // M6d-b: reconcile the smart-interface values with the machine that just formed. Idempotent, so it
            // runs on every successful check exactly as the original's checkAndAddSmartInterface did.
            if (match != null) {
                be.bindSmartInterfaces(match.machine());
            }
            if (wasFormed != be.formed || !java.util.Objects.equals(previousMachine, be.machineId)) {
                // The screen reads the machine name from the block entity, so the client needs the new tag.
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }

        if (!be.formed) {
            be.status = be.chunkUnloaded(level, pos) ? ControllerStatus.CHUNK_UNLOADED
                    : ControllerStatus.MISSING_STRUCTURE;
            be.clearStartFailure();
            be.abortCraft();
            be.parallelCeiling = 1;
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        if (be.redstoneStopped) {
            be.abortCraft();
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        // ---- M6e: the factory path ----------------------------------------------------------------
        // A factory controller does not run the single-craft state machine at all: it runs N independent
        // settlements instead. Everything above is shared (structure check, hatches, buses and modifiers, the
        // redstone gate), and everything below belongs to the one-craft-per-machine engine.
        if (be.factoryEnabled) {
            be.factoryEngine.tick();
            be.status = be.factoryEngine.isWorking() ? ControllerStatus.CRAFTING : ControllerStatus.IDLE;
            be.parallelCeiling = be.factoryEngine.availableParallelism(null);
            be.setChanged();
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        MachineRecipe recipe = be.resolveRecipe(level);
        if (recipe == null) {
            be.status = ControllerStatus.NO_RECIPE;
            be.clearStartFailure();
            be.abortCraft();
            be.maxProgress = 1;
            be.parallelCeiling = be.machineCeiling();
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        recipe.applyDurationMultiplier(be.modifiers);
        be.maxProgress = recipe.duration(be.modifiers);

        if (be.progress == 0) {
            int parallelism = be.computeParallelism(recipe);
            be.parallelCeiling = Math.max(1, parallelism);
            if (parallelism < 1) {
                // The original fell back to a plain single-copy check when nothing could be parallelised.
                parallelism = 1;
            }
            recipe.setParallelism(parallelism);
            if (!recipe.canStart(be.hatches, be.modifiers) || !recipe.canTick(be.hatches, be.modifiers)) {
                // M6d-b: say *which* start-phase requirement refused, so an interface_number_input mismatch is
                // reported as the original's own message rather than as a bare "idle".
                be.recordStartFailure(recipe);
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            if (!recipe.start(be.hatches, level.random, be.modifiers)) {
                be.recordStartFailure(recipe);
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            be.clearStartFailure();
            be.activeRecipeId = recipe.getId();
            be.activeParallelism = parallelism;
            be.activeDuration = be.maxProgress;
            be.setChanged();
        }

        // The craft's parallelism is frozen at start, so re-assert it every tick: the recipe object is shared
        // with every other controller in the level.
        recipe.setParallelism(be.activeParallelism);
        recipe.applyDurationMultiplier(be.modifiers);

        int nextProgress = be.progress + 1;
        if (nextProgress >= be.maxProgress && !recipe.canFinish(be.hatches, be.modifiers)) {
            // The craft would complete but the results do not fit. Hold without drawing this tick's resources
            // rather than draining energy while blocked.
            be.status = ControllerStatus.CRAFTING;
            be.recordTiming(tickStart, searchNanos);
            return;
        }
        if (!recipe.canTick(be.hatches, be.modifiers)) {
            // A per-tick requirement cannot be served right now: the original's failed ioTick
            // (RecipeCraftingContext:238-298) and the one place failure-action acts.
            be.applyFailureAction();
            be.status = ControllerStatus.IDLE;
            be.recordTiming(tickStart, searchNanos);
            return;
        }
        recipe.tick(be.hatches, level.random, be.modifiers);
        be.progress = nextProgress;
        be.status = ControllerStatus.CRAFTING;

        if (be.progress >= be.maxProgress) {
            recipe.finish(be.hatches, level.random, be.modifiers);
            be.progress = 0;
            be.activeRecipeId = null;
            be.status = ControllerStatus.IDLE;
        }
        be.setChanged();
        be.recordTiming(tickStart, searchNanos);
    }

    private void recordTiming(long tickStartNanos, long searchNanos) {
        long usedMicros = (System.nanoTime() - tickStartNanos) / 1000L;
        long searchMicros = searchNanos / 1000L;
        // The original's arrays are static, so every controller overwrote the same numbers. Here each machine
        // keeps its own, and the client mirror is refreshed whenever a sample lands outside the rounding.
        int previousUsed = this.timing.usedTimeAvg();
        int previousSearch = this.timing.searchUsedTimeAvg();
        this.timing.record(usedMicros, searchMicros);
        if (this.timing.usedTimeAvg() != previousUsed || this.timing.searchUsedTimeAvg() != previousSearch) {
            this.clientUsedTimeAvg = this.timing.usedTimeAvg();
            this.clientSearchTimeAvg = this.timing.searchUsedTimeAvg();
        }
    }

    /**
     * The original's {@code TileMultiblockMachineController#getMaxParallelism}: start from the machine's
     * {@code internal-parallelism}, add every parallel controller in the structure — the sum M6d now supplies —
     * clamp to {@code max-parallelism}, and floor at 1. The arithmetic itself lives in
     * {@link ParallelismLimit} so it can be driven offline.
     *
     * <p>{@code parallelizable: false} collapses it to 1 exactly as the original's {@code isParallelized} did.
     */
    private int machineCeiling() {
        MachineDefinition definition = MachineRegistry.byId(this.machineId).orElse(null);
        if (definition == null) {
            return 1;
        }
        return ParallelismLimit.resolve(definition, this.parallelControllers.parallelism());
    }

    /**
     * Decides, once per structure check, whether this controller is a factory and keeps the two engines from
     * running at the same time.
     *
     * <p>The decision is the conjunction {@link FactoryThreadModel#factoryEnabled} spells out: the <b>block</b>
     * must be a factory controller (the original's separate {@code BlockFactoryController}) <i>and</i> the formed
     * machine's definition must carry {@code has-factory}. A player who builds a machine around a factory block
     * without the flag gets no factory at all — not a silently downgraded one.
     *
     * <p>Switching engines discards the other one's work rather than resuming it, because neither state means
     * anything under the other engine: a factory thread holds a port set and a paid input reservation, and the
     * single-craft progress belongs to a machine that no longer forms here. The single-craft path already
     * discarded its progress on every structure change ({@code abortCraft}).
     */
    private void updateFactoryMode(@Nullable MachineDefinition definition) {
        boolean factoryNow = FactoryThreadModel.factoryEnabled(this.factoryBlock, definition);
        if (factoryNow == this.factoryEnabled) {
            return;
        }
        this.factoryEnabled = factoryNow;
        if (factoryNow) {
            this.abortCraft();
        } else {
            this.factoryEngine.reset();
        }
        this.setChanged();
    }

    /** Whether this controller is currently running as a factory — see {@link #updateFactoryMode}. */
    public boolean isFactory() {
        return this.factoryEnabled;
    }

    /** The multi-thread engine. Its thread list is empty unless {@link #isFactory()}. */
    public FactoryEngine factoryEngine() {
        return this.factoryEngine;
    }

    /** The threads of the factory, core ones first; empty for a non-factory controller. */
    public List<FactoryThread> factoryThreads() {
        return this.factoryEnabled ? this.factoryEngine.allThreads() : List.of();
    }

    // ------------------------------------------------------- M6e-2: what the factory screen reads

    /**
     * The machine's {@code max-threads}, or the config default when it does not say — the number the factory
     * screen's {@code gui.factory.threads} line prints as its second half.
     *
     * <p>Unlike {@link #factoryEngine()}'s own answer it does not depend on a thread existing, so the screen can
     * print the row on an idle factory too, which is what the original did
     * ({@code GuiFactoryController:267-275} guards on {@code getMaxThreads() <= 0} rather than on any thread).
     * The client gets it through the menu's container data rather than recomputing it, because a dedicated-server
     * client never receives machine definitions; {@link #setClientFactoryMaxThreads} is that mirror and
     * {@code FactoryControllerMenu#maxThreads()} is how the screen reads it.
     */
    public int factoryMaxThreads() {
        if (!isServerSide()) {
            return Math.max(0, this.clientFactoryMaxThreads);
        }
        return MachineRegistry.byId(this.machineId)
                .map(FactoryThreadModel::maxThreads)
                .orElse(FactoryThreadModel.DEFAULT_MAX_THREADS);
    }

    /** The parallelism ceiling the factory's threads settle against, the original's {@code getTotalParallelism()}. */
    public int factoryParallelCeiling() {
        return Math.max(1, this.parallelCeiling);
    }

    /**
     * Whether the engine could still take on another ordinary thread — the original's {@code hasIdleThread()}
     * ({@code TileFactoryController.java:521-533}), which gated the status block
     * ({@code GuiFactoryController:248-251}).
     */
    public boolean factoryCanAccept() {
        return this.factoryEnabled && this.factoryEngine.canAccept();
    }

    /**
     * The status of the thread at <b>display position</b> {@code slot} (core threads first, then ordinary ones),
     * or {@code -1} when there is no thread there.
     *
     * <p>An idle thread answers {@link ControllerStatus#IDLE} and a working one
     * {@link ControllerStatus#CRAFTING}, which is the pair of keys the original printed from
     * {@code thread.getStatus().getUnlocMessage()} ({@code GuiFactoryController:173}).
     */
    public int factoryThreadStatus(int slot) {
        List<FactoryThread> threads = factoryThreads();
        if (slot < 0 || slot >= threads.size()) {
            return -1;
        }
        return (threads.get(slot).isWorking() ? ControllerStatus.CRAFTING : ControllerStatus.IDLE).ordinal();
    }

    /** The client's mirror of {@link #factoryMaxThreads()}, written by the menu's container data. */
    public void setClientFactoryMaxThreads(int value) {
        this.clientFactoryMaxThreads = Math.max(0, value);
    }

    /**
     * The host the engine talks to. It is what makes the engine testable without a world: every value it asks
     * for is either an existing accessor on this block entity or a small lookup, and the two sides of the
     * interface are the only coupling between "the arithmetic" and "the block".
     */
    private final class FactoryHostImpl implements FactoryHost {

        @Override
        public RecipeModifiers modifiers() {
            return MachineControllerBlockEntity.this.modifiers;
        }

        @Override
        public HatchCollection ports() {
            return MachineControllerBlockEntity.this.hatches;
        }

        @Override
        public List<FactoryThreadModel.CoreThreadSpec> coreThreads() {
            return MachineRegistry.byId(MachineControllerBlockEntity.this.machineId)
                    .map(MachineDefinition::coreThreads)
                    .orElse(List.of());
        }

        @Override
        public List<MachineRecipe> availableRecipes() {
            ResourceLocation formed = MachineControllerBlockEntity.this.machineId;
            Level level = MachineControllerBlockEntity.this.level;
            if (formed == null || level == null) {
                return List.of();
            }
            List<MachineRecipe> candidates = new ArrayList<>();
            for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MACHINE.get())) {
                if (formed.equals(recipe.machineId())) {
                    candidates.add(recipe);
                }
            }
            return candidates;
        }

        @Override
        public int parallelCeiling() {
            return MachineControllerBlockEntity.this.machineCeiling();
        }

        @Override
        public int maxThreads() {
            return MachineRegistry.byId(MachineControllerBlockEntity.this.machineId)
                    .map(FactoryThreadModel::maxThreads)
                    .orElse(FactoryThreadModel.DEFAULT_MAX_THREADS)
                    // The original added its extra count here, not inside the machine definition: the definition's
                    // `max-threads` is what the pack author declared, and the extra count is what a controller
                    // added at runtime (`TileFactoryController.java:455-457`).
                    + MachineControllerBlockEntity.this.extraThreadCount;
        }

        @Override
        public FailureAction failureAction() {
            return MachineControllerBlockEntity.this.failureAction();
        }

        @Override
        public RandomSource random() {
            Level level = MachineControllerBlockEntity.this.level;
            return level == null ? RandomSource.create() : level.random;
        }

        @Override
        public void onThreadStarted(FactoryThread thread) {
            MachineControllerBlockEntity.this.factoryTouched();
        }

        @Override
        public void onThreadFinished(FactoryThread thread) {
            MachineControllerBlockEntity.this.factoryTouched();
        }

        @Override
        public void onThreadIdle(FactoryThread thread) {
            MachineControllerBlockEntity.this.factoryTouched();
        }

        @Override
        public void onThreadsChanged() {
            MachineControllerBlockEntity.this.setChanged();
            MachineControllerBlockEntity.this.syncFactoryThreads();
        }
    }

    /**
     * Pushes the thread list to nearby clients, but only when it actually changed shape.
     *
     * <p>The whole saved state doubles as the update tag (see {@link #getUpdateTag}), and a thread's own progress
     * changes every tick, so an unthrottled push here would broadcast the full compound sixty times a second for
     * as long as anything is crafting. The signature below is "which slots hold which recipes and how far along
     * they are, rounded to the second" — the coarsest thing that still answers "is that slot running, and what".
     * A thread starting, finishing or being reaped changes it immediately; a craft merely advancing changes it at
     * most once a second.
     *
     * <p>It is also the only channel for this state: a menu carries integers, and a thread's recipe id is not
     * one. The factory screen is M6e-2's, so nothing reads it yet — but the state has to be there for it to read.
     */
    private void syncFactoryThreads() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        StringBuilder signature = new StringBuilder();
        for (FactoryThread thread : this.factoryEngine.allThreads()) {
            signature.append(thread.label()).append('=').append(thread.recipeId()).append('/')
                    .append(thread.progress() / 20).append(';');
        }
        String current = signature.toString();
        if (current.equals(this.factorySyncSignature)) {
            return;
        }
        this.factorySyncSignature = current;
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                Block.UPDATE_ALL);
    }

    /**
     * One thread moved. The original fired {@code FactoryRecipeStartEvent} / {@code FactoryRecipeFinishEvent}
     * here, which existed for the CraftTweaker bridge (D7); what is left is the bookkeeping both the single-craft
     * path and the factory need — mark the block entity dirty, and push the state to clients when the working
     * status flipped so the block's own visual state (if any) and the screen stay in step.
     */
    private void factoryTouched() {
        this.setChanged();
        boolean working = this.factoryEngine.isWorking();
        if (working != this.factoryWasWorking) {
            this.factoryWasWorking = working;
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                        Block.UPDATE_ALL);
            }
        }
    }


    /**
     * How many copies this craft may settle, capped by what the ports actually hold: the original's
     * {@code RecipeCraftingContext#getMaxParallelism}, reached through the machine's own ceiling.
     *
     * <p>{@code 0} means "not parallelisable right now", in which case the caller runs a single copy. That is
     * the original's {@code canStartCrafting} fallback.
     */
    private int computeParallelism(MachineRecipe recipe) {
        int machineCeiling = this.machineCeiling();
        if (machineCeiling <= 1) {
            return 0;
        }
        return recipe.parallelism(this.hatches, this.modifiers, machineCeiling);
    }

    /**
     * Which of the definition's structure modifiers are actually satisfied — the original's
     * {@code TileMultiblockMachineController#updateModifiers}, including its rotation of both the offset and
     * the accepted descriptors.
     */
    private static List<MachineModifier> evaluateModifiers(Level level, BlockPos pos, MachineDefinition machine,
                                                           int rotationSteps) {
        if (machine.modifiers().isEmpty()) {
            return List.of();
        }
        List<MachineModifier> active = new ArrayList<>();
        for (MachineModifier modifier : machine.modifiers()) {
            BlockPos at = pos.offset(modifier.rotatedOffset(rotationSteps));
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            for (var matcher : modifier.accepted()) {
                if (matcher.matches(state)) {
                    active.add(modifier);
                    break;
                }
            }
        }
        return List.copyOf(active);
    }

    /**
     * True when the structure is incomplete at least partly because a position it covers is not loaded. The
     * original reported this separately as {@code chunk_unloaded}; 1.20.1's {@code Level#getBlockState} would
     * quietly answer air for an unloaded chunk, which is indistinguishable from a missing block.
     */
    private boolean chunkUnloaded(Level level, BlockPos pos) {
        if (this.boundMachine == null) {
            // Without a bound machine there is no single pattern to test, so the distinction cannot be made.
            return false;
        }
        MachineDefinition definition = MachineRegistry.byId(this.boundMachine).orElse(null);
        if (definition == null) {
            return false;
        }
        for (BlockPos relative : definition.pattern().positions().keySet()) {
            if (!level.hasChunkAt(pos.offset(relative))) {
                return true;
            }
        }
        return false;
    }

    private void abortCraft() {
        if (this.progress != 0 || this.activeRecipeId != null) {
            this.progress = 0;
            this.activeRecipeId = null;
            this.activeParallelism = 1;
            this.setChanged();
        }
    }

    /**
     * What one tick that could not be served costs this craft: the machine's {@code failure-action} applied to
     * the progress — the original's {@code ActiveMachineRecipe#doFailureAction:106-115}, reached from its
     * {@code tick:87-98} when {@code RecipeCraftingContext#ioTick} fails.
     *
     * <p>It is a method rather than three lines inside the tick loop so the offline harness can drive the real
     * resolution: {@link #failureAction()} is the original's {@code ctrl.getFoundMachine().getFailureAction()},
     * including its fallback when no definition is loaded.
     *
     * <p><b>The one place this project differs from the original, deliberately.</b> The original dropped
     * {@code tick} to 0 and left the craft <i>armed</i>, so {@code reset} cost the time already spent and
     * nothing else. Here {@code progress == 0} is this engine's "no craft" — {@code tick}'s start gate runs the
     * start phase at zero, and the start phase is what pays the one-shot inputs — so a reset ends the craft and
     * restarts it, re-paying those inputs. Keeping a zero-progress craft armed would mean representing "armed"
     * separately from "progress", i.e. changing the engine's state model rather than consuming this field; see
     * D17 in {@code 移植方案-v2.md}. The factory engine, which is armed by its thread slot, does stay armed.
     */
    private void applyFailureAction() {
        int kept = failureAction().afterFailedTick(this.progress);
        if (kept != this.progress) {
            this.progress = kept;
            this.setChanged();
        }
    }

    /**
     * The formed machine's {@code failure-action}, or the original's own default when no definition is loaded
     * (a controller between structures, or a data pack whose machine is gone).
     */
    private FailureAction failureAction() {
        return MachineRegistry.byId(this.machineId)
                .map(MachineDefinition::failureAction)
                .orElse(MachineDefinition.DEFAULT_FAILURE_ACTION);
    }

    /**
     * The recipe being crafted, or a fresh match when idle. A running craft is looked up by id so that having
     * already consumed its inputs cannot make it stop matching.
     */
    @Nullable
    private MachineRecipe resolveRecipe(Level level) {
        if (this.progress > 0 && this.activeRecipeId != null) {
            return level.getRecipeManager().byKey(this.activeRecipeId)
                    .filter(recipe -> recipe instanceof MachineRecipe)
                    .map(recipe -> (MachineRecipe) recipe)
                    .orElse(null);
        }
        return this.findRecipe(level);
    }

    /**
     * The first recipe bound to the formed machine whose one-shot inputs this structure can pay.
     *
     * <p>M6c extends the original's ordering rule here: a recipe whose <b>modified</b> values are payable is
     * preferred over one that is only payable unmodified, because a machine modifier may be what makes it
     * affordable (or what makes it unaffordable). With no modifiers — the only case that existed before M6c —
     * this is exactly the previous first-match rule.
     */
    @Nullable
    private MachineRecipe findRecipe(Level level) {
        if (this.machineId == null) {
            return null;
        }
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MACHINE.get())) {
            if (!this.machineId.equals(recipe.machineId())) {
                continue;
            }
            if (recipe.canStart(this.hatches, this.modifiers)) {
                return recipe;
            }
        }
        return null;
    }

    /** Walks the formed pattern and buckets every hatch it finds. Positions come from the definition. */
    private HatchCollection collectHatches(Level level, BlockPos pos, @Nullable MachineDefinition machine) {
        if (machine == null) {
            return HatchCollection.EMPTY;
        }
        HatchCollection.Builder builder = HatchCollection.builder();
        for (BlockPos relative : machine.pattern().positions().keySet()) {
            if (level.getBlockEntity(pos.offset(relative)) instanceof MachineHatchBlockEntity hatch) {
                builder.add(hatch.kind(), hatch.itemHandler(), hatch.fluidTank(), hatch.energyStorage());
            }
        }
        // M6d-b: the smart-interface values travel with the ports (see HatchCollection's class comment). The
        // source is this block entity, i.e. the same object the recipe lifecycle already reaches through the
        // original's controller -> foundSmartInterfaces -> provider chain, with the middle link removed.
        builder.smartInterfaces(this);
        // …and the definition rides along for the declared type's own `notequal` message.
        builder.machine(machine);
        return builder.build();
    }

    /**
     * Walks the same pattern positions again and collects the parallel controllers among them.
     *
     * <p>This is the second of the two collection paths a formed structure now has — the answer to the scoping
     * document's warning that only hatches were ever attributed to a machine. It reuses the <b>existing</b> walk
     * (the pattern's own positions) rather than inventing a search radius or a block-update handshake, and it
     * keys off {@link ParallelController} rather than {@code HatchKind}, so neither the hatch buckets nor the
     * blocks a position accepts are affected.
     */
    private ParallelControllerCollection collectParallelControllers(Level level, BlockPos pos,
                                                                    @Nullable MachineDefinition machine) {
        if (machine == null) {
            return ParallelControllerCollection.EMPTY;
        }
        List<ParallelController> found = new ArrayList<>();
        for (BlockPos relative : machine.pattern().positions().keySet()) {
            if (level.getBlockEntity(pos.offset(relative)) instanceof ParallelController controller) {
                found.add(controller);
            }
        }
        return ParallelControllerCollection.of(found);
    }

    /**
     * Walks the same pattern positions once more and collects the upgrade buses among them — M6b reusing M6d's
     * second collection path rather than inventing a third.
     *
     * <p>The shape is deliberately identical to {@link #collectParallelControllers}: same
     * {@code machine.pattern().positions()} walk, same "no search radius, no block-update handshake" rule, and a
     * marker interface ({@link UpgradeBusUtility}) that has nothing to do with {@code HatchKind} so neither the
     * hatch buckets nor the blocks a structure position accepts are touched.
     *
     * <p><b>Where the bus genuinely needed more.</b> A parallel controller only had to be <i>counted</i>, so a
     * one-method interface sufficed. A bus has to be <i>asked</i> what it holds, and it has to print the machine
     * it belongs to in its own GUI — which means the attribution has to run in both directions. That is the one
     * extra call the controller makes in {@code tick} ({@link UpgradeBusUtility#bindMachine}), and it is the
     * original's own direction of travel: {@code UpgradeBusProvider#boundMachine} was called by the controller,
     * while the bus pruned the pairing itself. The walk is still shared; only the interface is wider.
     */
    private List<UpgradeBusUtility> collectUpgradeBuses(Level level, BlockPos pos,
                                                        @Nullable MachineDefinition machine) {
        if (machine == null) {
            return List.of();
        }
        List<UpgradeBusUtility> found = new ArrayList<>();
        for (BlockPos relative : machine.pattern().positions().keySet()) {
            if (level.getBlockEntity(pos.offset(relative)) instanceof UpgradeBusUtility bus) {
                found.add(bus);
            }
        }
        return List.copyOf(found);
    }

    /**
     * The recipe modifiers every bus in the structure contributes to this machine.
     *
     * <p>Each bus answers with its own compatibility-filtered set ({@link UpgradeBusUtility#modifiersFor}), which
     * is the original's {@code UpgradeBusProvider#getUpgrades(controller)} dropping upgrades the formed machine
     * does not accept. Several buses add up, exactly as several {@code MachineComponent}s did.
     *
     * <p>Static and free of the block entity so the arithmetic can also be asserted without a world: the bulk of
     * it lives in {@link UpgradeEffects}, which the offline acceptance harness drives directly. The harness calls
     * this method rather than re-implementing the join, which is what makes the assertion about production code.
     */
    public static RecipeModifiers collectBusModifiers(List<UpgradeBusUtility> buses,
                                                      @Nullable ResourceLocation machineId) {
        if (buses.isEmpty() || machineId == null) {
            return RecipeModifiers.EMPTY;
        }
        RecipeModifiers combined = RecipeModifiers.EMPTY;
        for (UpgradeBusUtility bus : buses) {
            combined = combined.andThen(bus.modifiersFor(machineId));
        }
        return combined;
    }

    // ------------------------------------------------------- M6d-b: the smart data interface

    /**
     * The values this controller holds, keyed by declared interface type — the read-only view a requirement
     * sees. Stored in {@link SmartInterfaceStore}, which owns the whole of M6d-b's state so it can be driven
     * offline; every method below is a delegation to it.
     */
    public SmartInterfaceValues smartInterfaceValues() {
        return this.smartInterfaces.values();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This is the whole of the original's access chain once the interface block is gone: the controller was
     * always the object {@code RequirementInterfaceNumInput} asked, through
     * {@code SmartInterfaceProvider#getMachineData(String)}.
     */
    @Override
    @Nullable
    public Float valueOf(String type) {
        return this.smartInterfaces.valueOf(type);
    }

    /** The declared interface types of the formed machine, in declaration order. */
    public List<SmartInterfaceType> declaredSmartInterfaces() {
        MachineDefinition definition = MachineRegistry.byId(this.machineId).orElse(null);
        return definition == null ? List.of() : definition.smartInterfaces();
    }

    /**
     * The projection of the original's {@code checkAndAddSmartInterface}
     * ({@code TileMultiblockMachineController:909-938}), reached from the structure check instead of from a walk
     * over collected components. The arithmetic lives in {@link SmartInterfaceStore#bind}.
     */
    public void bindSmartInterfaces(MachineDefinition definition) {
        if (this.smartInterfaces.bind(definition)) {
            this.setChanged();
        }
    }

    /**
     * Sets one declared interface type's value — what the controller's screen submits. Returns the value
     * actually stored, or {@code null} when the machine does not declare that type. See
     * {@link SmartInterfaceStore#set} for why the server re-checks what the screen already guarded.
     */
    @Nullable
    public Float setSmartInterfaceValue(String type, float value) {
        Float stored = this.smartInterfaces.set(MachineRegistry.byId(this.machineId).orElse(null), type, value);
        if (stored == null) {
            return null;
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_ALL);
        }
        return stored;
    }

    /**
     * The failure key the last recipe search produced, or {@code null}.
     *
     * <p>Only {@code interface_number_input} produces one today, and only two keys exist —
     * {@code craftcheck.failure.interface.number.notequal} (the value is outside the range the recipe asked for)
     * and {@code component.missing.modularmachinery.interface.number} (the machine declares no such type). Both
     * are the original's own keys, kept verbatim.
     */
    @Nullable
    public String startFailureKey() {
        return isServerSide() ? this.startFailureKey : failureKeyAt(this.clientStartFailure);
    }

    /**
     * The failure keys the menu can mirror, indexed by the integer it puts in its {@code ContainerData}. The
     * menu channel carries integers only — the same constraint that produced
     * {@code FactoryControllerMenu}'s status ordinals — so the string is mapped through this fixed list, and the
     * order is part of the wire format.
     */
    public static final List<String> START_FAILURE_KEYS = List.of(
            com.reborn.modularmachinery.recipe.InterfaceNumberInputRequirement.MISSING_KEY,
            SmartInterfaceType.DEFAULT_NOT_EQUAL_KEY);

    /**
     * The sentinel index meaning "no failure". {@link #failureIndex} returns it for {@code null} and
     * {@code List#indexOf} returns it for a key that is not in the list, so it is a legitimate value that
     * <b>must never</b> be handed to {@link List#get(int)}.
     */
    public static final int NO_START_FAILURE = -1;

    /**
     * The key at {@code index} in {@link #START_FAILURE_KEYS}, or {@code null} for anything that is not a valid
     * index — in particular {@link #NO_START_FAILURE} ({@code -1}) and {@code List#indexOf}'s "not found"
     * sentinel.
     *
     * <p>This is the <b>only</b> place the list is indexed. {@link #clientStartFailure} arrives over the wire
     * ({@code MachineControllerMenu#setClientStartFailure}), so its value is untrusted and is bounds-checked
     * here rather than used as an index directly: an out-of-range value means "no failure", never an exception.
     * {@code startFailureKey()} is called every frame by {@code MachineControllerScreen#drawInfo}, so an
     * unchecked lookup here makes the controller GUI unopenable.
     */
    @Nullable
    public static String failureKeyAt(int index) {
        return index >= 0 && index < START_FAILURE_KEYS.size() ? START_FAILURE_KEYS.get(index) : null;
    }

    /**
     * The index of a failure key in {@link #START_FAILURE_KEYS}, or {@link #NO_START_FAILURE} for "no failure".
     */
    public static int failureIndex(@Nullable String key) {
        return key == null ? NO_START_FAILURE : START_FAILURE_KEYS.indexOf(key);
    }

    /** Records a start failure, pushing it to clients only when it actually changed. */
    private void recordStartFailure(MachineRecipe recipe) {
        recordStartFailureKey(recipe == null ? null : recipe.startFailure(this.hatches, this.modifiers));
    }

    /** {@link #recordStartFailure(MachineRecipe)} for the cases with no recipe at all. */
    private void clearStartFailure() {
        recordStartFailureKey(null);
    }

    private void recordStartFailureKey(@Nullable String key) {
        if (java.util.Objects.equals(key, this.startFailureKey)) {
            return;
        }
        this.startFailureKey = key;
        this.clientStartFailure = failureIndex(key);
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * The client's mirror of {@link #startFailureKey}, written by the menu's container data.
     *
     * <p>{@code index} is untrusted — it is whatever the menu sent — so it is normalised through
     * {@link #failureKeyAt(int)}: anything that is not a valid index becomes {@link #NO_START_FAILURE}, which is
     * the value {@link #startFailureKey()} reads as "no failure". Storing the raw out-of-range value would leave
     * a sentinel in the field for some later reader to hand to {@link List#get(int)}.
     */
    public void setClientStartFailure(int index) {
        this.clientStartFailure = failureIndex(failureKeyAt(index));
    }

    public boolean isFormed() { return formed; }

    @Nullable
    public ResourceLocation machineId() { return machineId; }

    /** Display name of the formed machine, or {@code null} when the structure does not match anything. */
    @Nullable
    public Component machineName() {
        return MachineRegistry.byId(this.machineId).map(MachineDefinition::displayName).orElse(null);
    }

    /**
     * The blueprint machine: the machine named by the blueprint in the slot, or {@code null} when the slot is
     * empty or holds something that is not a bound blueprint.
     *
     * <p>It is read straight from the slot rather than mirrored through the menu, because the menu already syncs
     * slot contents — so this answers correctly on both sides.
     */
    @Nullable
    public ResourceLocation blueprintMachineId() {
        return com.reborn.modularmachinery.item.BlueprintItem.machineOf(items.getStackInSlot(BLUEPRINT_SLOT));
    }

    /** Display name of the blueprint's machine, or {@code null} when none is slotted or it is unknown here. */
    @Nullable
    public Component blueprintMachineName() {
        return MachineRegistry.byId(blueprintMachineId()).map(MachineDefinition::displayName).orElse(null);
    }

    public boolean isRedstoneStopped() { return redstoneStopped; }

    public ControllerStatus status() { return status; }

    /** The ports gathered from the formed structure. */
    public HatchCollection hatches() { return this.hatches; }

    /**
     * The parallel controllers the formed structure contains, and the sum they contribute — the piece M6d adds
     * to the ceiling. Exposed so the sum's origin can be inspected (and asserted) without guessing.
     */
    public ParallelControllerCollection parallelControllers() { return this.parallelControllers; }

    /** The upgrade buses the formed structure contains, in pattern order. */
    public List<UpgradeBusUtility> upgradeBuses() { return this.upgradeBuses; }

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }

    // ------------------------------------------------------- parallelism and timing

    /** Structure modifiers whose block is present right now. */
    public List<MachineModifier> activeModifiers() { return this.activeModifiers; }

    /** The recipe modifiers the formed structure contributes, flattened. */
    public RecipeModifiers modifiers() { return this.modifiers; }

    /**
     * The share of {@link #modifiers()} the upgrade buses contribute — M6b's half of the two modifier sources.
     * Exposed so the offline harness can assert the split rather than only the total.
     */
    public RecipeModifiers busModifiers() { return this.busModifiers; }

    /**
     * The copies the current craft settles — the original's {@code ActiveMachineRecipe#getParallelism}.
     *
     * <p>While a craft is running this is the value frozen at start; when nothing is being crafted it is the
     * ceiling the last recipe search computed. The screen shows both rows only while {@link #hasActiveCraft()},
     * which is the original's {@code activeRecipe != null} guard.
     */
    public int parallelism() {
        return this.hasActiveCraft() ? this.activeParallelism : Math.max(1, this.parallelCeiling);
    }

    /** Whether a craft is currently running, the original's {@code controller.getActiveRecipe() != null}. */
    public boolean hasActiveCraft() {
        return this.progress > 0 || this.activeRecipeId != null;
    }

    /** The most copies this machine could settle — the original's {@code getMaxParallelism} as the screen read it. */
    public int maxParallelism() {
        return Math.max(1, Math.max(this.parallelCeiling, this.machineCeiling()));
    }

    /**
     * The factory's whole thread list, serialised the way the original's {@code writeCustomNBT} did — the keys
     * {@code threadList} and {@code coreThreadList}, one entry per thread.
     *
     * <p>The original returned early when the structure was not formed, so a non-formed controller wrote no
     * threads; this does the same, which is also why a torn-down factory loses its threads rather than keeping
     * stale ones to resume.
     */
    public void saveFactoryThreads(CompoundTag tag) {
        if (this.factoryEnabled && this.formed) {
            this.factoryEngine.save(tag);
        }
    }

    /**
     * Restores the factory threads a save wrote. Called from {@link #load} before the structure has been
     * re-checked, so the recipe lookup goes through the live recipe manager when the level is available.
     */
    public void loadFactoryThreads(CompoundTag tag) {
        this.factoryEngine.load(tag, id -> {
            Level level = this.level;
            if (level == null || id == null) {
                return null;
            }
            return level.getRecipeManager().byKey(id)
                    .filter(recipe -> recipe instanceof MachineRecipe)
                    .map(recipe -> (MachineRecipe) recipe)
                    .orElse(null);
        }, MachineRegistry.byId(this.machineId).map(MachineDefinition::coreThreads).orElse(List.of()));
    }

    /** The duration of the running craft, after duration modifiers. */
    public int activeDuration() { return Math.max(1, this.activeDuration); }

    public ControllerTiming timing() { return this.timing; }

    public int usedTimeAvg() {
        // On the client the block entity's own recorder never ran, so the mirrored values are what to show.
        return isServerSide() ? this.timing.usedTimeAvg() : this.clientUsedTimeAvg;
    }

    public int searchUsedTimeAvg() {
        return isServerSide() ? this.timing.searchUsedTimeAvg() : this.clientSearchTimeAvg;
    }

    public ControllerTiming.WorkMode workMode() { return this.timing.workMode(); }

    public void setClientTiming(int usedTimeAvg, int searchTimeAvg) {
        this.clientUsedTimeAvg = usedTimeAvg;
        this.clientSearchTimeAvg = searchTimeAvg;
    }

    private boolean isServerSide() {
        return this.level != null && !this.level.isClientSide;
    }

    // ------------------------------------------------------- client mirrors

    public void setClientProgress(int value) { progress = value; }
    public void setClientMaxProgress(int value) { maxProgress = Math.max(1, value); }
    public void setClientFormed(boolean value) { formed = value; }
    public void setClientStatus(int ordinal) { status = ControllerStatus.byOrdinal(ordinal); }
    public void setClientRedstoneStopped(boolean value) { redstoneStopped = value; }
    public void setClientParallelism(int current, int ceiling) {
        this.activeParallelism = Math.max(1, current);
        this.parallelCeiling = Math.max(1, ceiling);
    }
    public ItemStackHandler items() { return items; }

    @Override public Component getDisplayName() { return Component.translatable("block.modular_machinery_reborn.machine_controller"); }

    /**
     * The server-side menu, which is the plain controller's: the factory controller registers a menu type of its
     * own ({@code ModMenus.FACTORY_CONTROLLER}) and the client builds <i>that</i> one from the position it is
     * sent, so this method is never the factory's.
     */
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new MachineControllerMenu(id, inv, this); }

    public void open(Player player) { NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, this, worldPosition); }

    /**
     * Opens the <b>factory</b> screen, which is a menu type of its own
     * ({@code ModMenus.FACTORY_CONTROLLER}).
     *
     * <p><b>Why this cannot just be {@link #createMenu}.</b> One block entity class serves both the plain and
     * the factory controller. {@code NetworkHooks#openScreen(player, this, pos)} asks <i>this</i> object for the
     * menu, and it can only answer with one type — so a factory controller opened through {@link #open} would
     * be announced to the client under {@code machine_controller}'s id and the client would build the plain
     * screen for it. The original did not have the problem because {@code BlockFactoryController} had its own
     * tile entity subclass and therefore its own {@code createMenu}. Here the distinction is made at the call
     * site instead, from the block's own {@code factory()} bit, which is the same bit
     * {@link FactoryThreadModel#factoryEnabled} uses.
     */
    public void openFactory(net.minecraft.server.level.ServerPlayer player) {
        NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return MachineControllerBlockEntity.this.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) {
                return new FactoryControllerMenu(id, inventory, MachineControllerBlockEntity.this);
            }
        }, this.worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        tag.putBoolean("formed", formed);
        tag.putBoolean("redstoneStopped", redstoneStopped);
        tag.putInt("status", status.ordinal());
        tag.putInt("activeParallelism", activeParallelism);
        tag.putInt("activeDuration", activeDuration);
        if (machineId != null) {
            tag.putString("machine", machineId.toString());
        }
        if (activeRecipeId != null) {
            tag.putString("activeRecipe", activeRecipeId.toString());
        }
        // M6e: the factory's threads, under the original's own keys.
        saveFactoryThreads(tag);
        // The original's extra thread count, under the original's own key and its own width — see
        // extraThreadCount() for why it is a short there and an int here.
        tag.putShort("extraThreadCount", (short) this.extraThreadCount);
        // M6d-b: the smart-interface values. The original stored them as a list under `boundData`
        // (`TileSmartInterface#writeCustomNBT`), one compound per binding; with the block gone the binding *is*
        // this controller, so the compound is a plain type -> value map. The key is the original's, so a
        // developer reading a save file finds what they expect.
        smartInterfaces.save(tag);
        if (startFailureKey != null) {
            tag.putString("startFailure", startFailureKey);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("items"));
        progress = tag.getInt("progress");
        maxProgress = Math.max(1, tag.getInt("maxProgress"));
        formed = tag.getBoolean("formed");
        redstoneStopped = tag.getBoolean("redstoneStopped");
        status = ControllerStatus.byOrdinal(tag.getInt("status"));
        activeParallelism = Math.max(1, tag.getInt("activeParallelism"));
        activeDuration = Math.max(1, tag.getInt("activeDuration"));
        machineId = tag.contains("machine") ? ResourceLocation.tryParse(tag.getString("machine")) : null;
        activeRecipeId = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
        // M6e. A factory controller is a factory by virtue of its block plus the formed machine, and the
        // definition is available here, so the mode can be restored before the first tick. That matters: the
        // engine's thread list must not be read as "single-craft" state in the tick between load and the first
        // structure check.
        this.factoryEnabled = FactoryThreadModel.factoryEnabled(this.factoryBlock,
                MachineRegistry.byId(this.machineId).orElse(null));
        loadFactoryThreads(tag);
        // Read back with `getShort`, matching the width the original wrote it in. A save written by this build
        // therefore reads identically to one written by the original, and a value from a malformed tag falls back
        // to 0 — which is also the original's own field default.
        this.extraThreadCount = tag.getShort("extraThreadCount");
        // M6d-b. Unlike the structure-derived state above, the values are the player's own input and must
        // survive a reload verbatim — the original persisted them for the same reason.
        smartInterfaces.load(tag);
        this.startFailureKey = tag.contains("startFailure") ? tag.getString("startFailure") : null;
        // The mirror is derived, never left implicit: a save with no failure (or a key this build does not know)
        // must come back as NO_START_FAILURE, not as the field's old default of key 0.
        this.clientStartFailure = failureIndex(this.startFailureKey);
    }

    /**
     * The whole saved state doubles as the update tag, so the client block entity carries the formed machine's
     * registry name — the screen has to print it, and an int-only container channel cannot.
     */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
