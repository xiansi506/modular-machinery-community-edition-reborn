package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.api.event.MachineEvents;
import com.reborn.modularmachinery.api.event.MachineStructureFormedEvent;
import com.reborn.modularmachinery.api.event.MachineStructureUpdateEvent;
import com.reborn.modularmachinery.api.event.MachineTickEvent;
import com.reborn.modularmachinery.api.event.Phase;
import com.reborn.modularmachinery.api.event.RecipeCheckEvent;
import com.reborn.modularmachinery.api.event.RecipeFinishEvent;
import com.reborn.modularmachinery.api.event.RecipeStartEvent;
import com.reborn.modularmachinery.api.event.RecipeTickEvent;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.factory.FactoryEngine;
import com.reborn.modularmachinery.factory.FactoryHost;
import com.reborn.modularmachinery.factory.FactoryThread;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import com.reborn.modularmachinery.machine.ControllerStatus;
import com.reborn.modularmachinery.machine.ControllerTiming;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.machine.InputGroupBinding;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineModifier;
import com.reborn.modularmachinery.machine.MachineMultiBlockModifier;
import com.reborn.modularmachinery.machine.DynamicPattern;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.machine.ParallelController;
import com.reborn.modularmachinery.machine.ParallelControllerCollection;
import com.reborn.modularmachinery.machine.ParallelismLimit;
import com.reborn.modularmachinery.machine.SmartInterfaceStore;
import com.reborn.modularmachinery.machine.SmartInterfaceType;
import com.reborn.modularmachinery.machine.SmartInterfaceValueSource;
import com.reborn.modularmachinery.machine.SmartInterfaceValues;
import com.reborn.modularmachinery.machine.StructureChangeTracker;
import com.reborn.modularmachinery.machine.StructureCheckScheduler;
import com.reborn.modularmachinery.menu.FactoryControllerMenu;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import com.reborn.modularmachinery.recipe.EnergyUsage;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import com.reborn.modularmachinery.recipe.RecipeCommands;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import com.reborn.modularmachinery.recipe.MachineRecipeIndex;
import com.reborn.modularmachinery.upgrade.UpgradeBusUtility;
import com.reborn.modularmachinery.upgrade.UpgradeEffects;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    private final ItemStackHandler items = newBlueprintHandler(this::setChanged);

    /**
     * 蓝图槽的物品处理器：**一格只放一张蓝图**。
     *
     * <p>旧版在控制器构造器里显式写了 `inventory.setStackLimit(1, BLUEPRINT_SLOT)`
     * （`TileMultiblockMachineController.java:168`；从存档重建之后 `:1385` 再设一次）。
     * 本项目搬这个槽位时漏了那一条，于是 {@link ItemStackHandler} 的默认上限 64 生效，
     * 而蓝图物品本身允许叠到 16（`ModItems.BLUEPRINT`），一个槽里就能塞下一叠（勘测 X9）。
     *
     * <p>上限写在这里、而不是写在菜单里：{@code SlotItemHandler} 的堆叠上限取自
     * {@code getSlotLimit}（`MachineControllerMenu.BlueprintSlot` 另外只管「只收蓝图」），
     * 所以 GUI 拖入、漏斗与任何 {@code IItemHandler} 插入都走同一个数。
     *
     * <p>抽成静态工厂是为了让离线复现器能直接驱动它：构造方块实体需要注册过的方块状态，
     * 那在离线环境里不存在，而「这个槽位的上限是 1」不需要世界。
     *
     * <p>没有做的事：存档里如果已经被 0.62.9 及以前写进过一叠，读档时不会被切开
     * （{@code deserializeNBT} 直接放回原位）。切开等于丢掉玩家背包里的物品，
     * 所以这一点留作已知限制，而不是在加载时静默修剪。
     */
    public static ItemStackHandler newBlueprintHandler(Runnable onChanged) {
        return new ItemStackHandler(1) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    private int progress;
    private java.util.UUID paidInputIdentity = java.util.UUID.randomUUID();
    public java.util.UUID paidInputIdentity() { return paidInputIdentity; }
    /** Conservative ownership: another paid batch on this controller may delay, never hasten unlock. */
    public boolean hasPaidInputWork() {
        return activeRecipeId != null || factoryEngine.workingThreadCount() > 0 || pendingFactoryState != null;
    }
    private int maxProgress = 1;
    private boolean formed;
    /** The adopted dynamic footprint remains required while its chunks are unavailable; paid work is paused. */
    private boolean structureChunksPaused;
    private boolean structureChunkUnavailable;
    private boolean structureSelectionKnown;
    @Nullable private MachineDefinition structureSelectionDefinition;
    @Nullable private ResourceLocation structureSelectionBlueprint;
    private int structureSelectionRotation;
    private String structureSelectionFingerprint = "";
    private boolean redstoneStopped;
    private ControllerStatus status = ControllerStatus.MISSING_STRUCTURE;

    /**
     * The original's scheduling state ({@code TileMultiblockMachineController.java:122-123}, {@code :351-370}).
     *
     * <p>{@code ticksExisted} is the controller's age, which the original inherited from {@code TileEntity}
     * and this projection has to count itself. {@code lastStructureCheckTick} starts at {@code -1} so the very
     * first tick always checks; the original used the same sentinel. The two failure counters are the
     * original's {@code structureCheckCounter} and {@code recipeResearchRetryCounter}.
     */
    private long ticksExisted;
    private int lastStructureCheckTick = -1;
    private int structureCheckFailures;
    private int recipeResearchRetryCounter;

    /**
     * Relative change-detection bounds: formed parts, optional modifiers and the next dynamic probes.
     * Cleared when no structure matches so a stale box cannot keep a broken machine's area flag alive.
     */
    @Nullable
    private BlockPos structureMinOffset;
    @Nullable
    private BlockPos structureMaxOffset;

    /** Registry name of the machine currently formed here, or {@code null} when nothing matches. */
    @Nullable
    private ResourceLocation machineId;

    /**
     * The player who placed this controller, or the owner copied from the placed item's tag (A11).
     *
     * <p>The original always wrote this on placement, independent of {@code enable-security-system}
     * ({@code ItemBlockController.java:35-63}); the switch only decides whether the owner is enforced. Saves
     * written before this release have no owner and load as {@code null}, which is deliberately not guessed.
     */
    @Nullable
    private UUID owner;
    /**
     * 这台机器外衣的染色；{@code null} = 没设过（不染色）。来源是定义的 {@code color}。
     *
     * <p>与部件一样存进存档并随方块实体同步到客户端——渲染时的 tint 由
     * {@code client.MachinePartColors} 读它。
     */
    @javax.annotation.Nullable private Integer machineColor;
    /** Relative positions of casing/hatch block entities tinted by this controller; persisted for teardown. */
    private final List<BlockPos> colorizedPartOffsets = new ArrayList<>();

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
     * 这台机器成型时<strong>定格</strong>下来的动态结构：每个模式量到了几节、在哪一面。
     *
     * <p>为什么必须存下来而不是每次重新量：配方的 {@code selector-tag} 里带着段号
     * （{@code input_line_3} 这种），而动态结构的长度在世界上是会变的。如果端口集合按「当前量到的长度」
     * 重建，玩家在线尾再加一节就会让正在跑的合成指向别的段——表现为「加了一节之后合成突然换料」，
     * 而且只在有人动结构时出现，极难复现。旧版同样把这三个值存进 NBT
     * （{@code TileMultiblockMachineController.java:1435/1490}）。
     */
    private List<DynamicPattern.Status> dynamicPatterns = List.of();

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
    private final java.util.Map<UpgradeBusUtility, Long> busInventoryRevisions = new java.util.IdentityHashMap<>();
    private long upgradeRegistryRevision = -1;

    /** Counter-clockwise quarter turns the matching rotation applied, needed to place modifiers. */
    private int rotationSteps;

    /** Structure modifiers whose block is present, recomputed with the structure. */
    private List<MachineModifier> activeModifiers = List.of();

    /** 额外多块结构条件中全部命中的那些，同样随结构重算。 */
    private List<MachineMultiBlockModifier> activeMultiBlockModifiers = List.of();

    /** The recipe modifiers they contribute, flattened once per structure check. */
    private RecipeModifiers modifiers = RecipeModifiers.EMPTY;

    /** How the craft being run was settled when it started: frozen for its whole duration. */
    private int activeParallelism = 1;

    /**
     * A2：正在跑的这条合成锁定在哪一个输入组，{@code null} 表示没有锁定（没有合成）。
     *
     * <p><b>为什么组号必须在开工时冻住。</b>旧版 {@code canStartCrafting} 在开工那一刻逐组试，第一组成功就
     * {@code setGroupId} 并一直用到合成结束（{@code RecipeCraftingContext.java:502-549}）。如果每 tick 重新选，
     * 玩家在线尾补一个仓或改一个组号就会让正在跑的合成中途换一组料——同样「只在有人动结构时出现」，
     * 与动态结构那类幽灵缺陷一样难查。
     *
     * <p>类型是 {@code Integer} 而不是 {@code int}：组 0 是**合法**的一组，不能拿 0 当「没锁」的哨兵。
     */
    @Nullable
    private Integer activeGroupId;
    private InputGroupBinding activeInputBinding = InputGroupBinding.manual();

    /** How long the running craft takes after duration modifiers. */
    private int activeDuration = 1;

    /**
     * The modifiers this craft froze at its start, catalyst modifiers included only for catalysts that were
     * present then. {@code null} when idle. Not written to NBT: a reload re-derives it from the ports, which is
     * the same thing the original's in-memory crafting context did.
     */
    @Nullable
    private RecipeModifiers activeCraftModifiers;
    private RecipeModifiers activeScriptModifiers = RecipeModifiers.EMPTY;
    private MachineRecipe executionRecipeSource;
    private MachineRecipe executionRecipe;
    private CompoundTag pendingFactoryState;
    private CompoundTag pendingExecutionState;
    private java.util.Set<BlockPos> formedPartOffsets = java.util.Set.of();
    private boolean formedPartSnapshotKnown;

    /** Actual matched offsets, already rotated; also synchronized for world model hiding. */
    public java.util.Set<BlockPos> formedPartOffsets() { return formedPartOffsets; }
    public boolean hasFormedPartSnapshot() { return formedPartSnapshotKnown; }
    private MachineDefinition inputDefinition;
    private MachinePattern inputPattern;
    private final com.reborn.modularmachinery.script.MachineScriptState scriptState = new com.reborn.modularmachinery.script.MachineScriptState();
    public com.reborn.modularmachinery.script.MachineScriptState scriptState() { return scriptState; }
    public ResourceLocation scriptActiveRecipeId() { return activeRecipeId; }
    public void setScriptProgress(int ticks) { progress = Math.max(0, Math.min(maxProgress, ticks)); setChanged(); }
    public RecipeModifiers scriptBaseModifiers() {
        refreshBusModifiers();
        return modifiers.andThen(scriptState.modifiers());
    }

    /** Refresh only the already-collected buses at new-start planning boundaries; never rescan the world.
     * Active crafts keep their frozen modifiers, while inventory and definition changes affect new batches. */
    private void refreshBusModifiers() {
        long registryRevision = com.reborn.modularmachinery.upgrade.UpgradeRegistry.revision();
        boolean changed = upgradeRegistryRevision != registryRevision
                || busInventoryRevisions.size() != upgradeBuses.size();
        for (UpgradeBusUtility bus : upgradeBuses) {
            if (!(bus instanceof UpgradeBusBlockEntity nativeBus)) { changed = true; continue; }
            Long previous = busInventoryRevisions.get(bus);
            if (previous == null || previous.longValue() != nativeBus.inventoryRevision()) changed = true;
        }
        if (!changed) return;
        busModifiers = collectBusModifiers(upgradeBuses.stream()
                .filter(bus -> !(bus instanceof UpgradeBusBlockEntity nativeBus) || !nativeBus.isRemoved())
                .toList(), machineId);
        modifiers = MachineDefinition.flatten(activeModifiers, activeMultiBlockModifiers).andThen(busModifiers);
        busInventoryRevisions.clear();
        for (UpgradeBusUtility bus : upgradeBuses) if (bus instanceof UpgradeBusBlockEntity nativeBus)
            busInventoryRevisions.put(bus, nativeBus.inventoryRevision());
        upgradeRegistryRevision = registryRevision;
    }
    public long scriptTicksExisted() { return ticksExisted; }
    public List<BlockPos> scriptPatternPositions() { return formedPartOffsets.stream().map(worldPosition::offset).toList(); }
    public void syncScriptState() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

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
    private int clientFactoryMaxThreads = ModConfig.factoryDefaultMaxThread();

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
     * C2b（0.88.0）：**任意字符串**的失败原因（第三方 {@code RecipeCheckEvent.setFailed} 写入），
     * 与固定失败键并行。菜单整数槽装不下字符串，所以它走方块实体的 update tag
     * （{@code saveAdditional} → {@code getUpdateTag} 即 {@code saveWithoutMetadata}），客户端在
     * {@code load} 里镜像到 {@link #clientFailureCause}。
     */
    @Nullable
    private String startFailureCause;
    /** 客户端镜像；服务端不看它，与 {@link #clientStartFailure} 的分工相同。 */
    @Nullable
    private String clientFailureCause;

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

    // ------------------------------------------------------- F3（0.86.0）：已加载控制器名单

    /**
     * 服务端当前加载的每一个控制器，{@code /mm-performance_report} 枚举用。弱键：区块卸载、世界关闭后
     * 方块实体可被回收，名单不会把它钉在内存里——静态强引用名单会在单机「退出世界再进另一个世界」时
     * 永久泄漏上一世界的实体。所有访问都在 {@code synchronized (LOADED_CONTROLLERS)} 下进行：名单基本只在
     * 服务端线程动，但命令可能在网络线程读，加锁比论证「其实单线程」便宜。
     */
    private static final com.reborn.modularmachinery.machine.InputDependencyIndex<MachineControllerBlockEntity> INPUT_DEPENDENCIES =
            new com.reborn.modularmachinery.machine.InputDependencyIndex<>();
    public static void inputProviderChanged(Level level, BlockPos position) {
        if (!level.isClientSide) INPUT_DEPENDENCIES.changed(level,position);
    }
    public static void inputChunkChanged(Level level, net.minecraft.world.level.ChunkPos position) {
        if (!level.isClientSide) INPUT_DEPENDENCIES.changedChunk(level,position.x,position.z);
    }

    private static final java.util.WeakHashMap<MachineControllerBlockEntity, Boolean> LOADED_CONTROLLERS =
            new java.util.WeakHashMap<>();

    private static void registerLoaded(MachineControllerBlockEntity controller, boolean add) {
        synchronized (LOADED_CONTROLLERS) {
            if (add) {
                LOADED_CONTROLLERS.put(controller, Boolean.TRUE);
            } else {
                LOADED_CONTROLLERS.remove(controller);
                INPUT_DEPENDENCIES.forget(controller.getLevel(),controller);
            }
        }
    }

    /** F3：当前加载的服务端控制器（快照拷贝）。弱键条目被回收与否由 GC 决定，条数只是下界。 */
    public static java.util.List<MachineControllerBlockEntity> loadedControllers() {
        synchronized (LOADED_CONTROLLERS) {
            return java.util.List.copyOf(LOADED_CONTROLLERS.keySet());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            registerLoaded(this, true);
        }
    }

    @Override
    public void onChunkUnloaded() {
        // 显式摘除（弱键最终也会被 GC 收走）：刚卸载但引用还在别处的实体，不该出现在下一份报告里。
        if (this.level == null || !this.level.isClientSide) {
            registerLoaded(this, false);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (this.level == null || !this.level.isClientSide) {
            registerLoaded(this, false);
        }
        super.setRemoved();
    }

    /** The player who owns this controller, or {@code null} for a save written before A11 or an invalid tag. */
    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner and marks the block entity dirty. Called on placement and on load; the security check itself
     * reads {@link #getOwner()} through {@link ControllerSecurity}.
     */
    public void setOwner(@Nullable UUID owner) {
        if (java.util.Objects.equals(this.owner, owner)) {
            return;
        }
        this.owner = owner;
        setChanged();
    }

    /**
     * C2: the controller's server tick, wrapped in the original's paired {@link MachineTickEvent}.
     *
     * <p>The original posted {@code MachineTickEvent(Phase.START)} before its restricted tick and
     * {@code Phase.END} after it, through {@code sendEventToComponents} and the controller's own listeners
     * ({@code TileMultiblockMachineController:123-136}). The wrapper here covers every early return inside
     * {@link #tickServer}: the END is in a {@code finally}, so a factory tick, a missing structure or a throwing
     * listener still sees the pair close. The client never posts either half.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, MachineControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.postMachineTick(Phase.START);
        try {
            com.reborn.modularmachinery.machine.PaidInputScope.run(be, () -> tickServer(level, pos, state, be));
        } finally {
            be.postMachineTick(Phase.END);
        }
    }

    private static void tickServer(Level level, BlockPos pos, BlockState state, MachineControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        long tickStart = System.nanoTime();
        long searchNanos = 0L;
        be.ticksExisted++;

        if (be.requiredDynamicChunksUnavailable(level, pos)) {
            boolean newlyPaused = !be.structureChunksPaused;
            be.structureChunksPaused = true;
            be.structureChunkUnavailable = true;
            be.status = ControllerStatus.CHUNK_UNLOADED;
            be.hatches = HatchCollection.builder().build();
            be.parallelControllers = ParallelControllerCollection.EMPTY;
            be.upgradeBuses = List.of();
            be.lastStructureCheckTick = -1;
            if (newlyPaused) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
            be.recordTiming(tickStart, searchNanos);
            return;
        }
        if (be.structureChunksPaused || (be.formed && be.structureSelectionKnown && !be.structureSelectionCurrent())) {
            // Includes a target/definition change while paused. Old unavailable offsets may not pin a new target.
            be.structureChunksPaused = false;
            be.lastStructureCheckTick = -1;
        }
        if (INPUT_DEPENDENCIES.takeDirty(level,be)) be.refreshInputPorts();

        // The original read the strong power into the controller every tick and refused to work while it was
        // powered. getDirectSignalTo is the 1.20.1 counterpart of 1.12.2's World#getStrongPower.
        boolean redstoneStopped = level.getDirectSignalTo(pos) > 0;
        if (be.redstoneStopped != redstoneStopped) {
            be.redstoneStopped = redstoneStopped;
            be.setChanged();
        }

        // A10: the original's canCheckStructure() (TileMultiblockMachineController.java:351-370). The area test
        // uses the dependency bounds of the last successful match; a
        // machine that is not formed has no bounds and therefore cannot take the shortcut.
        StructureCheckScheduler.Settings structureSchedule = ModConfig.structureCheckSettings();
        boolean structureAreaChanged = be.formed
                && be.structureMinOffset != null
                && be.structureMaxOffset != null
                && StructureChangeTracker.isAreaChanged(level.dimension(),
                        pos.offset(be.structureMinOffset), pos.offset(be.structureMaxOffset));
        int structureFailures = be.formed ? be.recipeResearchRetryCounter : be.structureCheckFailures;
        if (StructureCheckScheduler.shouldCheck(be.ticksExisted, be.lastStructureCheckTick, be.formed,
                structureAreaChanged, structureFailures, structureSchedule)) {
            be.lastStructureCheckTick = (int) Math.min(be.ticksExisted, Integer.MAX_VALUE);
            long searchStart = System.nanoTime();
            Direction facing = state.getValue(MachineControllerBlock.FACING);
            MachineRegistry.Inspection inspected = MachineRegistry.inspectMatch(level, pos, facing,
                    be.blueprintMachineId(), be.boundMachine);
            MachineRegistry.Match match = inspected.match();
            be.structureChunkUnavailable = inspected.unloaded();
            searchNanos = System.nanoTime() - searchStart;

            boolean wasFormed = be.formed;
            ResourceLocation previousMachine = be.machineId;
            java.util.Set<BlockPos> previousParts = be.formedPartOffsets;
            be.formed = match != null;
            be.machineId = match == null ? null : match.machine().id();
            if (!java.util.Objects.equals(previousMachine, be.machineId)) {
                // Paid work belongs to the machine that started it, even when a different definition can
                // immediately form here. Clear both live engines and deferred saved work before callbacks
                // or the new machine's first tick can observe/revive the previous owner's batch.
                be.abortCraft();
                be.executionRecipe = null;
                be.executionRecipeSource = null;
                be.pendingExecutionState = null;
                be.factoryEngine.reset();
                be.pendingFactoryState = null;
            }
            be.rotationSteps = match == null ? 0 : match.rotationSteps();
            if (match != null) be.rememberStructureSelection(match.machine(), match.rotationSteps());
            else be.structureSelectionKnown = false;
            // 波 3b：动态机器要用**有效结构**（基座 + 量出来的各段）来收集端口与控制器，而不是定义里的基座。
            // 没有动态结构的机器，match.pattern() 就是基座本身，所以这段改动对它们没有任何影响。
            MachinePattern effectivePattern = match == null ? null : match.pattern();
            be.formedPartOffsets = effectivePattern == null ? java.util.Set.of()
                    : java.util.Set.copyOf(effectivePattern.positions().keySet());
            be.formedPartSnapshotKnown = true;
            if (match == null) {
                // The original incremented structureCheckCounter and cleared the recipe retry counter when a
                // check failed (TileMultiblockMachineController.java:414 and :409-411, through resetMachine).
                be.structureCheckFailures++;
                be.recipeResearchRetryCounter = 0;
                be.structureMinOffset = null;
                be.structureMaxOffset = null;
            } else {
                be.structureCheckFailures = 0;
                // match.pattern() is already rotated to the world's facing (MachineRegistry.findMatch builds
                // it from the rotated variant), so these offsets can be added to the controller pos directly.
                be.structureMinOffset = effectivePattern.min();
                be.structureMaxOffset = effectivePattern.max();
                be.includeOptionalStructureBounds(match);
            }
            be.dynamicPatterns = match == null ? List.of() : match.dynamicPatterns();
            be.hatches = be.collectHatches(level, pos, match == null ? null : match.machine(), effectivePattern);
            be.parallelControllers = be.collectParallelControllers(level, pos,
                    match == null ? null : match.machine(), effectivePattern);
            be.upgradeBuses = be.collectUpgradeBuses(level, pos,
                    match == null ? null : match.machine(), effectivePattern);
            be.activeModifiers = match == null
                    ? List.of()
                    : evaluateModifiers(level, pos, match.machine(), match.rotationSteps());
            be.activeMultiBlockModifiers = match == null
                    ? List.of()
                    : evaluateMultiBlockModifiers(level, pos, match.machine(), match.rotationSteps());
            // The two sources of RecipeModifier meet here and only here. M6c evaluates the definition's
            // structure modifiers; M6b adds what the buses in the same structure contribute. They stay separate
            // computations (the scoping document's §5.2 forbids merging the two paths) and are concatenated into
            // the one RecipeModifiers the whole recipe lifecycle already takes - RecipeModifiers#andThen exists
            // for exactly this join.
            RecipeModifiers structural = MachineDefinition.flatten(be.activeModifiers,
                    be.activeMultiBlockModifiers);
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
            // 波 6：把定义的 `color` 推给这台机器的所有部件（旧版 distributeCasingColor 的对应物）。
            // 放在这里是因为它是「刚算完 match」之后、且与 `updateFactoryMode` 同一批的一次性状态更新。
            be.distributeMachineColor(match == null ? null : machineColorOf(match.machine()),
                    match == null ? null : effectivePattern);
            // M6d-b: reconcile the smart-interface values with the machine that just formed. Idempotent, so it
            // runs on every successful check exactly as the original's checkAndAddSmartInterface did.
            if (match != null) {
                be.bindSmartInterfaces(match.machine());
                // C2: the original's order (TileMultiblockMachineController:556-557): Formed, then Update.
                // "Formed" means the check made an unformed controller formed; a machine that changed from one
                // definition to another while staying formed only gets the update.
                be.postStructureEvents(!wasFormed);
            }
            if (wasFormed != be.formed || !java.util.Objects.equals(previousMachine, be.machineId)
                    || !previousParts.equals(be.formedPartOffsets)) {
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
            // Pause the paid batch just as the factory gate does. A signal must not discard one-shot
            // inputs, delayed-payment state, progress or frozen modifiers; structure loss still aborts above.
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        // ---- M6e: the factory path ----------------------------------------------------------------
        // A factory controller does not run the single-craft state machine at all: it runs N independent
        // settlements instead. Everything above is shared (structure check, hatches, buses and modifiers, the
        // redstone gate), and everything below belongs to the one-craft-per-machine engine.
        if (be.factoryEnabled) {
            if (be.pendingFactoryState != null) {
                CompoundTag pending = be.pendingFactoryState;
                be.pendingFactoryState = null;
                be.loadFactoryThreads(pending);
            }
            be.factoryEngine.tick();
            be.status = be.factoryEngine.isWorking() ? ControllerStatus.CRAFTING : ControllerStatus.IDLE;
            be.parallelCeiling = be.factoryEngine.availableParallelism(null);
            be.setChanged();
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        MachineRecipe recipe = be.resolveRecipe(level);
        if (recipe != null) {
            // The original's recipeResearchRetryCounter only grew while the asynchronous search returned no
            // context; a successful search left it alone. This projection's search is synchronous and runs every
            // tick, so it resets on success instead of leaving a stale ceiling behind. The deviation is recorded
            // in the A10 section of the migration log.
            be.recipeResearchRetryCounter = 0;
        }
        if (recipe == null) {
            // 「没有配方」与「有配方但付不起」是**两件完全不同的事**，而这个分支曾经把后者也吞成前者：
            // `findRecipe` 只返回能开工的配方，于是「配方在、只是料不够或料放错了段」也会走到这里，
            // 状态显示 NO_RECIPE 并把失败文案**清掉**——玩家看到的是「没有相匹配的配方」，
            // 而真正的原因（缺少物品输入 / 料不在配方要的那个部件上）被丢掉了。
            //
            // 实机验证时正是如此：配方绑着 `input_line_1`，料只放在 `input_line_0`，
            // 于是玩家的第一反应是「机器没成型」或「配方没加载」，而不是「料放错了段」。
            //
            // 现在：如果这台机器**确实**有配方、只是此刻付不起，就把那条配方给出的原因显示出来。
            String blockedReason = be.unaffordableStartFailure(level);
            if (blockedReason == null) {
                // The old asynchronous recipe search retried on currentRecipeSearchDelay() ticks and incremented
                // its failure counter only when a search found nothing. Mirror that cadence instead of counting
                // every tick: a per-tick counter would reach the 100-tick ceiling in two seconds.
                int recipeDelay = StructureCheckScheduler.recipeSearchDelay(be.recipeResearchRetryCounter);
                if (be.ticksExisted % recipeDelay == 0) {
                    be.recipeResearchRetryCounter = Math.min(be.recipeResearchRetryCounter + 1, 1000);
                }
            } else {
                be.recipeResearchRetryCounter = 0;
            }
            be.status = blockedReason == null ? ControllerStatus.NO_RECIPE : ControllerStatus.IDLE;
            // **必须走 recordStartFailureKey，不能直接写字段。** 那个方法除了赋值还负责
            // `clientStartFailure` 的镜像与 `sendBlockUpdated`——失败文案是同步到客户端才显示得出来的。
            // 第一版这里直接写 `be.startFailureKey = ...`，于是状态确实变成了 IDLE，
            // 但文案从来没离开服务端：实机看到的就是「闲置」而没有任何原因。
            be.recordStartFailureKey(blockedReason);
            be.abortCraft();
            be.maxProgress = 1;
            be.parallelCeiling = be.machineCeiling();
            be.recordTiming(tickStart, searchNanos);
            return;
        }

        // A2：这一 tick 用哪个端口视图。开工时从可开工的组里选一个并锁住；运行中沿用锁住的那组；
        // 没有组号的在途合成来自 A2 之前的存档，按旧行为用全部端口，不让升级把正在跑的东西打断。
        HatchCollection scope;
        GroupScope startGroup = null;
        if (be.activeRecipeId == null) {
            startGroup = be.startableGroup(recipe);
            if (startGroup == null) {
                // 今天不会走到这里（findRecipe 只返回至少有一组能开工的配方），但把「找不到组」说成
                // 「没有配方」会是另一种撒谎；保留这条分支，原因取最后一组，与旧版的 fkey 一致。
                HatchCollection last = be.lastGroupScope();
                be.recordStartFailureKey(recipe.startFailure(last,
                        be.modifiers.andThen(recipe.catalystModifiers(last))));
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            scope = startGroup.hatches();
        } else if (be.activeGroupId != null) {
            if (be.activeInputBinding.group(be.hatches, be.activeGroupId) == null) {
                be.recordStartFailureKey("craftcheck.failure.inputgroup.unavailable");
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            scope = be.activeInputBinding.scope(be.hatches, be.activeGroupId);
        } else {
            scope = be.hatches;
        }

        RecipeModifiers effective;
        if (be.activeRecipeId == null) {
            recipe.setExecutionTick(0);
            // B9：催化剂只在**这次开工确实带着**时并入，且一旦并入就冻结到合成结束。不能每 tick 按当前
            // 库存重算——催化剂在 start 时已被消耗，重算会让 modifier 中途消失。
            MachineRecipe.StartPlan prepared = recipe.prepareParallelStart(scope, be.scriptBaseModifiers(), be.machineCeiling());
            effective = prepared.modifiers();
            recipe.applyDurationMultiplier(effective);
            be.maxProgress = recipe.duration(effective);

            int parallelism = be.computeParallelism(recipe, scope, prepared);
            if (recipe.parallelSearchIncomplete()) {
                be.recordStartFailureKey("craftcheck.failure.parallel.search_incomplete");
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            be.parallelCeiling = Math.max(1, parallelism);
            if (parallelism < 1) {
                // The original fell back to a plain single-copy check when nothing could be parallelised.
                parallelism = 1;
            }
            recipe.setParallelism(parallelism);
            prepared = recipe.prepareStart(scope, be.scriptBaseModifiers());
            effective = prepared.modifiers();
            // C2: the original's RecipeCheckEvent, START half, before canStart/canTick. A listener may lower
            // the parallelism for this start; the event resolves through min(computed, requested) with a floor
            // of one, so it cannot exceed the structure's ceiling or ask for zero copies.
            RecipeCheckEvent startCheck = be.postRecipeCheck(Phase.START, recipe, parallelism);
            if (startCheck.isCanceled()) {
                // C2b（0.88.0）：监听器否决开工——理由经字符串通道显示，不问门槛、什么都不扣。
                be.recordStartFailureCause(startCheck.failureReason());
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            int requested = startCheck.resolvedParallelism(parallelism);
            if (requested != parallelism) {
                parallelism = requested;
                recipe.setParallelism(parallelism);
                prepared = recipe.prepareStart(scope, be.scriptBaseModifiers());
                effective = prepared.modifiers();
            }
            recipe.applyDurationMultiplier(effective);
            be.maxProgress = recipe.duration(effective);
            prepared = recipe.prepareStart(scope, be.scriptBaseModifiers());
            effective = prepared.modifiers();
            if (!recipe.canStart(scope, prepared) || !recipe.canTick(scope, effective)) {
                // M6d-b: say *which* start-phase requirement refused, so an interface_number_input mismatch is
                // reported as the original's own message rather than as a bare "idle".
                be.recordStartFailure(recipe, scope);
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // C2: the original's RecipeCheckEvent, END half, after both gates said yes and before the one-shot
            // inputs are paid. A second override still cannot raise the ceiling; the last write wins.
            RecipeCheckEvent endCheck = be.postRecipeCheck(Phase.END, recipe, parallelism);
            if (endCheck.isCanceled()) {
                // C2b：END 段否决——门槛已过、一次性输入**尚未支付**，同样不开工、不扣任何东西。
                be.recordStartFailureCause(endCheck.failureReason());
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            int adjusted = endCheck.resolvedParallelism(parallelism);
            if (adjusted != parallelism) {
                parallelism = adjusted;
                recipe.setParallelism(parallelism);
                prepared = recipe.prepareStart(scope, be.scriptBaseModifiers());
                effective = prepared.modifiers();
                recipe.applyDurationMultiplier(effective);
                be.maxProgress = recipe.duration(effective);
            }
            prepared = recipe.prepareStart(scope, be.scriptBaseModifiers());
            effective = prepared.modifiers();
            recipe.applyDurationMultiplier(effective);
            be.maxProgress = recipe.duration(effective);
            if (InputGroupBinding.capture(scope, startGroup.groupId()).group(be.hatches, startGroup.groupId()) == null) {
                be.recordStartFailureKey("craftcheck.failure.inputgroup.unavailable");
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            if (!recipe.start(scope, level.random, prepared)) {
                be.recordStartFailure(recipe, scope);
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // C2: posted only once start() really succeeded. The original posted its start event before calling
            // start(), but its start could not fail; here a refused one-shot requirement means no craft began,
            // and announcing one would be false. The event is read-only either way.
            be.clearStartFailure();
            be.activeCraftModifiers = effective;
            be.activeScriptModifiers = be.scriptState.modifiers();
            be.activeRecipeId = recipe.getId();
            be.activeParallelism = parallelism;
            be.activeDuration = be.maxProgress;
            be.activeGroupId = startGroup.groupId();
            be.activeInputBinding = InputGroupBinding.capture(scope, startGroup.groupId());
            be.postRecipeStart(recipe, parallelism);
            be.setChanged();
            // A12: the original's enable-full-data-sync, on the start half (TileMachineController.java:265-275).
            be.syncFullDataIfEnabled();
            // B8: startCommands run once the craft is really armed and its state is complete.
            be.runRecipeCommands(recipe, RecipeCommands.Phase.START, 0);
        } else {
            effective = be.activeCraftModifiers == null ? null : be.activeCraftModifiers.without(be.activeScriptModifiers).andThen(be.scriptState.modifiers());
            if (effective == null) {
                // A craft loaded from before this field existed (or from disk): re-derive once and freeze it.
                effective = be.scriptBaseModifiers().andThen(recipe.catalystModifiers(scope));
                be.activeCraftModifiers = effective;
            }
            recipe.applyDurationMultiplier(effective);
            be.maxProgress = recipe.duration(effective);
        }

        // Re-assert this craft's frozen parallelism on its execution copy before settling the tick.
        recipe.setParallelism(be.activeParallelism);

        // A live duration change can put already-paid progress at/past the new target. This is a
        // finish-only wait, not another execution tick. Finish still reserves unpaid delayed work.
        if (be.progress >= be.maxProgress) recipe.markFinalTickPaid();
        if (!recipe.hasPaidFinalTick()) {
            // C2: the original's RecipeTickEvent, START half, before this tick's requirements are paid. Preventing
            // here means nothing is drawn and the craft stays armed; the status stays CRAFTING because the recipe is
            // still selected and will be tried again next tick.
            RecipeTickEvent startTick = be.postRecipeTick(Phase.START, recipe);
            recipe.setExecutionTick(be.progress);
            if (startTick.isPreventProgressing()) {
                be.status = ControllerStatus.CRAFTING;
                be.recordTiming(tickStart, searchNanos);
                return;
            }

            long nextProgress = (long) be.progress + 1;
            boolean tickReady = recipe.canTick(scope, effective);
            if (nextProgress >= be.maxProgress && tickReady && !recipe.canTickAndFinish(scope, effective)) {
                // The craft would complete but the results do not fit. Hold without drawing this tick's resources
                // rather than draining energy while blocked.
                be.status = ControllerStatus.CRAFTING;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            if (!tickReady || !recipe.tick(scope, level.random, effective)) {
                // A per-tick requirement cannot be served right now: the original's failed ioTick
                // (RecipeCraftingContext:238-298) and the one place failure-action acts.
                be.applyFailureAction(recipe, recipe.tickFailure(scope, effective));
                be.status = ControllerStatus.IDLE;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // C2: the original's RecipeTickEvent, END half, after the tick's requirements were paid and before the
            // controller counts the tick. A listener that prevents here cannot be refunded — the original could not
            // refund either — so the net effect is one tick of resources for no progress, which the event documents.
            RecipeTickEvent endTick = be.postRecipeTick(Phase.END, recipe);
            if (endTick.isPreventProgressing()) {
                be.status = ControllerStatus.CRAFTING;
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // B8: processingCommands see the progress *before* this successful tick was counted, which is what
            // the original's ioTick/ActiveMachineRecipe ordering produced.
            be.runRecipeCommands(recipe, RecipeCommands.Phase.PROCESSING, be.progress);
            be.progress = be.progress < Integer.MAX_VALUE ? be.progress + 1 : Integer.MAX_VALUE;
            be.status = ControllerStatus.CRAFTING;
        }

        if (be.progress >= be.maxProgress || recipe.hasPaidFinalTick()) {
            recipe.markFinalTickPaid();
            if (!recipe.canFinish(scope, effective)) {
                be.setChanged();
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // C2 小件（0.91.0）：结算前发 ResultChanceCreateEvent——监听器可替换本次产物掷点的随机源
            // （旧版替换 ResultChance 的等价物）；不替换时就是 level.random，行为与从前一致。
            com.reborn.modularmachinery.api.event.ResultChanceCreateEvent chance =
                    com.reborn.modularmachinery.api.event.MachineEvents.post(
                            new com.reborn.modularmachinery.api.event.ResultChanceCreateEvent(be, be.machineId,
                                    recipe, be.progress, be.maxProgress, be.activeParallelism, level.random));
            if (!recipe.finish(scope, chance.random(), effective)) {
                // Keep the paid final tick; retry only finish, including after saving/loading.
                be.setChanged();
                be.recordTiming(tickStart, searchNanos);
                return;
            }
            // C2: after the outputs were settled and before the craft's state is cleared, so a listener sees the
            // finished craft's recipe, max progress and parallelism rather than the next idle tick.
            be.postRecipeFinish(recipe);
            // B8: finishCommands run after the outputs have been settled, even when one output's chance roll
            // failed to place anything -- the original's finishCrafting reached its command list regardless.
            be.runRecipeCommands(recipe, RecipeCommands.Phase.FINISH, 0);
            be.scriptState.clearCraft();
            be.progress = 0;
            be.activeRecipeId = null;
            be.activeGroupId = null;
            be.activeInputBinding = InputGroupBinding.manual();
            be.activeCraftModifiers = null;
            be.status = ControllerStatus.IDLE;
            // A12: the original's enable-full-data-sync, on the finish half (TileMachineController.java:299-306).
            be.syncFullDataIfEnabled();
        }
        be.setChanged();
        be.recordTiming(tickStart, searchNanos);
    }

    /**
     * A12: the original's {@code enable-full-data-sync}.
     *
     * <p>When the switch is on, a recipe start or finish sends the controller's whole update tag
     * ({@link #getUpdateTag()}, i.e. {@code saveWithoutMetadata()}) to nearby clients. The original did the same
     * through {@code markForUpdateSync()} ({@code TileMachineController.java:265-275/299-306}). The switch is
     * deliberately <b>not</b> applied to every idle or blocked tick: the original's {@code isNotWorking} path
     * could queue a full compound every tick, which is not worth copying for a client that only needs the
     * transition.
     *
     * <p>Server-only and null-level-safe: the controller tick already returns on the client, but the factory
     * callbacks are also reachable from tests and from a not-yet-added entity.
     */
    private void syncFullDataIfEnabled() {
        if (!ModConfig.enableFullDataSync()) {
            return;
        }
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                Block.UPDATE_ALL);
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
        MachineDefinition definition = MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide).orElse(null);
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
     * single-craft progress belongs to a machine that no longer forms here. A change of machine identity also
     * clears both engines in the structure-check branch; rechecking the same machine keeps its paid work.
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
        return new FactoryHostImpl().maxThreads();
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
        public MachineControllerBlockEntity controller() {
            return MachineControllerBlockEntity.this;
        }

        @Override
        public RecipeModifiers modifiers() {
            return MachineControllerBlockEntity.this.scriptBaseModifiers();
        }
        @Override public RecipeModifiers scriptModifiers() { return scriptState.modifiers(); }

        @Override
        public HatchCollection ports() {
            return MachineControllerBlockEntity.this.hatches;
        }

        @Override
        public List<FactoryThreadModel.CoreThreadSpec> coreThreads() {
            var threads = new java.util.LinkedHashMap<String, FactoryThreadModel.CoreThreadSpec>();
            MachineRegistry.byId(MachineControllerBlockEntity.this.machineId, level != null && level.isClientSide)
                    .map(MachineDefinition::coreThreads)
                    .orElse(List.of()).forEach(spec -> threads.put(spec.name(), spec));
            scriptState.coreThreads().forEach(spec -> threads.put(spec.name(), spec));
            return List.copyOf(threads.values());
        }

        @Override
        public List<MachineRecipe> availableRecipes() {
            ResourceLocation formed = MachineControllerBlockEntity.this.machineId;
            Level level = MachineControllerBlockEntity.this.level;
            if (formed == null || level == null) {
                return List.of();
            }
            // 工厂线程拿到的候选表必须和单配方控制器同序：否则同一台机器在并行与串行下会选到不同配方。
            return MachineRecipeIndex.forMachine(level.getRecipeManager(), formed);
        }

        @Override
        public MachineRecipe currentRecipe(ResourceLocation id) {
            Level world = MachineControllerBlockEntity.this.level;
            ResourceLocation formed = MachineControllerBlockEntity.this.machineId;
            if (world == null || formed == null) return null;
            return world.getRecipeManager().byKey(id)
                    .filter(MachineRecipe.class::isInstance).map(MachineRecipe.class::cast)
                    .filter(recipe -> recipe.belongsTo(formed)).orElse(null);
        }

        @Override
        public int parallelCeiling() {
            return MachineControllerBlockEntity.this.machineCeiling();
        }

        @Override
        public int maxThreads() {
            return (int) Math.min(Integer.MAX_VALUE, (long) MachineRegistry.byId(MachineControllerBlockEntity.this.machineId)
                    .map(FactoryThreadModel::maxThreads)
                    .orElseGet(ModConfig::factoryDefaultMaxThread)
                    // The original added its extra count here, not inside the machine definition: the definition's
                    // `max-threads` is what the pack author declared, and the extra count is what a controller
                    // added at runtime (`TileFactoryController.java:455-457`).
                    + MachineControllerBlockEntity.this.extraThreadCount);
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
            // A12: the factory's start half (TileFactoryController.java:274-284).
            MachineControllerBlockEntity.this.syncFullDataIfEnabled();
        }

        @Override
        public void onThreadFinished(FactoryThread thread) {
            MachineControllerBlockEntity.this.factoryTouched();
            // A12: the factory's finish half (TileFactoryController.java:304-311).
            MachineControllerBlockEntity.this.syncFullDataIfEnabled();
        }

        @Override
        public void onThreadIdle(FactoryThread thread) {
            MachineControllerBlockEntity.this.factoryTouched();
        }

        @Override
        public void runRecipeCommands(FactoryThread thread, MachineRecipe recipe, int phase, int tick) {
            RecipeCommands.Phase[] phases = RecipeCommands.Phase.values();
            if (phase < 0 || phase >= phases.length) {
                return;
            }
            MachineControllerBlockEntity.this.runRecipeCommands(recipe, phases[phase], tick);
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
    private int computeParallelism(MachineRecipe recipe, HatchCollection scope, MachineRecipe.StartPlan plan) {
        int machineCeiling = this.machineCeiling();
        if (machineCeiling <= 1) {
            return 0;
        }
        return recipe.parallelism(scope, plan, machineCeiling);
    }

    /**
     * The modifiers a craft of {@code recipe} runs with right now.
     *
     * <p>For the running craft this is the snapshot frozen at its start: B9's catalyst modifiers depend on what
     * was in the ports then, and the catalyst itself is consumed by {@code start}, so re-deriving from the
     * current inventory would make them vanish half-way. For anything else it is the structure/bus base only;
     * a catalyst belongs to one craft, not to the whole structure, so it is never written back into
     * {@link #modifiers} (the factory host reads that field).
     */
    private RecipeModifiers modifiersFor(MachineRecipe recipe) {
        if (this.activeCraftModifiers != null && this.activeRecipeId != null
                && this.activeRecipeId.equals(recipe.getId())) {
            return this.activeCraftModifiers.without(activeScriptModifiers).andThen(scriptState.modifiers());
        }
        return this.scriptBaseModifiers();
    }

    /**
     * 结构里第一个能凭自己让 {@code recipe} 开工的输入组（A2）。
     *
     * <p>旧版 {@code canStartCrafting} 逐组试整条配方，按组号顺序、第一组成功就锁定
     * （{@code RecipeCraftingContext.java:502-549}）。这里同序：{@link HatchCollection#groupIds} 已升序，
     * 第一个 {@code canStart} 为真的组就是旧版会锁的那一组。
     *
     * <p>只看 {@code canStart}，不看 {@code canTick}：旧版也只在开工检查里问前者。如果某组能开工却过不了
     * 每 tick 检查，旧版同样会锁它然后失败，而不是悄悄换下一组——换组会让「为什么这组料不够」变成
     * 「机器从别的组偷料」。
     */
    @Nullable
    private GroupScope startableGroup(MachineRecipe recipe) {
        MachineRecipe probe = recipe.executionCopy();
        for (int groupId : this.hatches.groupIds()) {
            probe.setParallelism(1);
            HatchCollection scope = this.hatches.withGroup(groupId);
            MachineRecipe.StartPlan plan = probe.prepareParallelStart(scope, this.scriptBaseModifiers(), machineCeiling());
            if (probe.canStart(scope, plan)) {
                return new GroupScope(groupId, scope, plan.modifiers());
            }
        }
        return null;
    }

    /** 最后一组的端口视图，用来取「所有组都开不了工」时的失败文案（旧版组键排序后的末项 fkey）。 */
    private HatchCollection lastGroupScope() {
        int last = 0;
        for (int groupId : this.hatches.groupIds()) {
            last = groupId;
        }
        return this.hatches.withGroup(last);
    }

    /**
     * 一个输入组、它筛出来的端口视图，以及这一组开工时会用的 modifiers（B9：催化剂按组在场与否并入，
     * 所以组和 modifiers 必须一起被选中并冻结）。
     */
    private record GroupScope(int groupId, HatchCollection hatches, RecipeModifiers modifiers) {
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
            // 位置与 accepted 用同一套旋转：MachineModifier.acceptedAt 对每个候选调用
            // BlockMatcher#rotateYCounterClockwise，和基座结构的 MachinePattern.rotated 同源。
            // 不能只转 offset 或只改 descriptor 里的 facing 文本——耦合属性会被这样改错。
            if (modifier.matches(state, rotationSteps)) {
                active.add(modifier);
            }
        }
        return List.copyOf(active);
    }

    /**
     * 哪些多块结构条件真正命中。与 {@link #evaluateModifiers} 分开调用，但共用同一个结构检查时机：
     * 结构一变成型/失效，两者一起重算，不会出现「单块条件已刷新、多块条件还是上一轮」的半状态。
     *
     * <p>P07-05（0.121.45）：这里此前传的是一个只查「区块加载 + 方块状态」的谓词，于是条件里的
     * {@code nbt} 与 {@code script-checker} 整条不执行——条件不满足的额外结构也照常贡献 modifier，
     * 玩家会少扣能源（复现见 {@code _audit/seventh-ai-4tasks-0.121.45/L1}）。现在改走带世界上下文的重载，
     * 匹配、NBT、checker 与安全策略全部由 {@link MachinePattern#matches(Level, BlockPos)} 一处负责；
     * 本方法不再自己拼谓词，也不重复实现任何规则。
     */
    private static List<MachineMultiBlockModifier> evaluateMultiBlockModifiers(Level level, BlockPos pos,
                                                                               MachineDefinition machine,
                                                                               int rotationSteps) {
        if (machine.multiBlockModifiers().isEmpty()) {
            return List.of();
        }
        return MachineMultiBlockModifier.active(machine.multiBlockModifiers(), level, pos, rotationSteps);
    }

    /**
     * True when the structure is incomplete at least partly because a position it covers is not loaded. The
     * original reported this separately as {@code chunk_unloaded}; 1.20.1's {@code Level#getBlockState} would
     * quietly answer air for an unloaded chunk, which is indistinguishable from a missing block.
     */
    private boolean chunkUnloaded(Level level, BlockPos pos) {
        return structureChunkUnavailable;
    }

    private boolean structureSelectionCurrent() {
        BlockState state = getBlockState();
        return structureSelectionKnown && structureSelectionDefinition != null
                && MachineRegistry.byId(machineId).orElse(null) == structureSelectionDefinition
                && state.hasProperty(MachineControllerBlock.FACING)
                && MachineRegistry.rotationStepsFor(state.getValue(MachineControllerBlock.FACING)) == structureSelectionRotation
                && java.util.Objects.equals(blueprintMachineId(), structureSelectionBlueprint);
    }

    private boolean requiredDynamicChunksUnavailable(Level level, BlockPos pos) {
        if (!formed || !formedPartSnapshotKnown || !structureSelectionCurrent()
                || !structureSelectionDefinition.hasDynamicPatterns()) return false;
        for (BlockPos relative : formedPartOffsets) if (!level.isLoaded(pos.offset(relative))) return true;
        return false;
    }

    private void rememberStructureSelection(MachineDefinition definition, int rotation) {
        if (structureSelectionDefinition != definition)
            structureSelectionFingerprint = structureFingerprint(definition);
        structureSelectionDefinition = definition;
        structureSelectionBlueprint = blueprintMachineId();
        structureSelectionRotation = rotation;
        structureSelectionKnown = true;
    }

    private static String structureFingerprint(@Nullable MachineDefinition definition) {
        com.google.gson.JsonObject source = definition == null ? null : definition.synchronizationSource();
        // Java-constructed definitions have no parse-time source; runtime object identity still applies.
        if (source == null) return "";
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java runtime is missing SHA-256", impossible);
        }
    }

    private void abortCraft() {
        if (this.progress != 0 || this.activeRecipeId != null) {
            this.progress = 0;
            this.activeRecipeId = null;
            this.activeParallelism = 1;
            this.activeGroupId = null;
            this.activeInputBinding = InputGroupBinding.manual();
            this.activeCraftModifiers = null;
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
     * The recipe-aware form of {@link #applyFailureAction()}: a recipe that sets the original's
     * {@code cancelIfPerTickFails} drops this craft outright on a failed per-tick check.
     *
     * <p><b>0.88.0（C2b）起走事件</b>：默认值仍是配方的 {@code cancelIfPerTickFails}（旧版
     * {@code recipe.doesCancelRecipeOnPerTickFailure()}），但先发
     * {@link com.reborn.modularmachinery.api.event.RecipeFailureEvent}，第三方可以按次改写
     * {@code destructRecipe}——0.75.0–0.87.1 期间直接读字段的差异（见 0.75.0 的交付记录）就此收回。
     * 旧版行为照搬：{@code true} → 清掉这次合成（进度、激活配方、组、冻结修改器；已付一次性输入
     * **不返还**）；{@code false} → 走机器的 {@code failure-action}。{@link #abortCraft()} 是与旧版
     * 事件路径相同的状态复位。
     *
     * <p>No NBT key is added. A cancel can only happen while a craft is in flight, and after a reload the
     * controller re-resolves the recipe by id; the flag lives on that recipe, so the next failed tick reaches
     * this method with the same answer.
     */
    private void applyFailureAction(@Nullable MachineRecipe recipe, @Nullable String cause) {
        if (recipe != null) {
            com.reborn.modularmachinery.api.event.RecipeFailureEvent event =
                    com.reborn.modularmachinery.api.event.MachineEvents.post(
                            new com.reborn.modularmachinery.api.event.RecipeFailureEvent(this, this.machineId,
                                    recipe, this.progress, this.maxProgress, this.activeParallelism,
                                    cause == null ? MachineRecipe.FAILURE_TICK : cause, recipe.cancelIfPerTickFails()));
            if (event.destructRecipe()) {
                this.abortCraft();
                return;
            }
        }
        this.applyFailureAction();
    }

    // ------------------------------------------------------------ C2 lifecycle events

    private void postMachineTick(Phase phase) {
        MachineEvents.post(new MachineTickEvent(this, this.machineId, phase));
    }

    /**
     * Posts the structure pair in the original's order. Both carry the controller's <b>current</b> machine id;
     * a formed event can therefore never arrive without one, and an update event after a failed check is not
     * sent at all (the caller only invokes this when a match exists).
     */
    private void postStructureEvents(boolean newlyFormed) {
        if (newlyFormed) {
            MachineEvents.post(new MachineStructureFormedEvent(this, this.machineId));
        }
        MachineEvents.post(new MachineStructureUpdateEvent(this, this.machineId));
    }

    private RecipeCheckEvent postRecipeCheck(Phase phase, MachineRecipe recipe, int parallelism) {
        return MachineEvents.post(new RecipeCheckEvent(this, this.machineId, phase, recipe, this.progress,
                this.maxProgress, parallelism));
    }

    private void postRecipeStart(MachineRecipe recipe, int parallelism) {
        MachineEvents.post(new RecipeStartEvent(this, this.machineId, recipe, this.progress, this.maxProgress,
                parallelism));
    }

    private RecipeTickEvent postRecipeTick(Phase phase, MachineRecipe recipe) {
        return MachineEvents.post(new RecipeTickEvent(this, this.machineId, phase, recipe, this.progress,
                this.maxProgress, this.activeParallelism));
    }

    private void postRecipeFinish(MachineRecipe recipe) {
        MachineEvents.post(new RecipeFinishEvent(this, this.machineId, recipe, this.progress, this.maxProgress,
                this.activeParallelism));
    }

    /**
     * The command source the original's recipe commands ran with: the controller's position, permission level
     * 2 and the name {@code @[MM-Controller]} ({@code ControllerCommandSender}).
     *
     * <p>It is created per call rather than cached because a controller can be moved by a structure change and
     * the command source has to report where it really is. {@code null} on the client and before the level is
     * attached; commands are a server-side effect only.
     */
    @Nullable
    private CommandSourceStack commandSource() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return null;
        }
        return new CommandSourceStack(CommandSource.NULL, Vec3.atLowerCornerOf(this.worldPosition), Vec2.ZERO,
                serverLevel, 2, "@[MM-Controller]", Component.literal("@[MM-Controller]"), server, null);
    }

    /**
     * Runs one of the recipe's three command lists at the given phase. Start and finish commands always pass
     * tick 0; processing commands pass the craft's progress before this tick was counted, as the original did
     * ({@code RecipeCraftingContext#ioTick} ran before {@code ActiveMachineRecipe} incremented its tick).
     */
    private void runRecipeCommands(MachineRecipe recipe, RecipeCommands.Phase phase, int tick) {
        if (recipe.commands().isEmpty()) {
            return;
        }
        CommandSourceStack source = commandSource();
        if (source == null) {
            return;
        }
        recipe.commands().run(phase, tick,
                command -> source.getServer().getCommands().performPrefixedCommand(source, command));
    }

    /**
     * The formed machine's {@code failure-action}, or the original's own default when no definition is loaded
     * (a controller between structures, or a data pack whose machine is gone).
     */
    private FailureAction failureAction() {
        return MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide)
                .map(MachineDefinition::failureAction)
                .orElse(MachineDefinition.DEFAULT_FAILURE_ACTION);
    }

    /**
     * The recipe being crafted, or a fresh match when idle. A running craft is looked up by id so that having
     * already consumed its inputs cannot make it stop matching.
     */
    @Nullable
    private MachineRecipe resolveRecipe(Level level) {
        if (this.activeRecipeId != null) {
            return level.getRecipeManager().byKey(this.activeRecipeId)
                    .filter(recipe -> recipe instanceof MachineRecipe)
                    .map(recipe -> {
                        MachineRecipe source = (MachineRecipe) recipe;
                        if (!level.isClientSide && executionRecipe == null
                                && (!source.belongsTo(this.machineId) || pendingExecutionState == null
                                    || !source.canRestoreExecutionState(pendingExecutionState))) {
                            // Legacy saves have no definition proof either. Cancel instead of applying
                            // paid trigger indices/frozen catalyst benefits to a different recipe layout.
                            abortCraft();
                            pendingExecutionState = null;
                            executionRecipe = null;
                            executionRecipeSource = null;
                            scriptState.clearCraft();
                            return null;
                        }
                        return executionCopyOf(source);
                    })
                    .orElse(null);
        }
        return this.findRecipe(level);
    }

    private MachineRecipe executionCopyOf(MachineRecipe source) {
        if (executionRecipe != null && activeRecipeId != null && activeRecipeId.equals(source.getId())
                && activeRecipeId.equals(executionRecipe.getId())) return executionRecipe;
        if (executionRecipeSource != source || executionRecipe == null) {
            CompoundTag state = executionRecipe != null && activeRecipeId != null && activeRecipeId.equals(source.getId())
                    ? executionRecipe.saveExecutionState() : pendingExecutionState;
            executionRecipeSource = source;
            executionRecipe = source.executionCopy();
            if (state != null && activeRecipeId != null && activeRecipeId.equals(source.getId())) executionRecipe.loadExecutionState(state);
            pendingExecutionState = null;
        }
        return executionRecipe;
    }

    /**
     * E9 能量行：当前合成的需求速率快照，{@code [消耗, 生成]}（FE/t），不是实际流量计。
     *
     * <p>旧版 {@code MMInfoProvider}（TOP）的能量行：普通机器取当前线程 context 的
     * {@code RequirementEnergy}（INPUT/OUTPUT 各取第一个），数值 = 需求值 × modifiers ×
     * durationMul × parallelism（{@code getEnergyRequired :306-320}）。本版同一条链已经住在
     * {@code EnergyRequirement.perTickDraw} 里——它就是逐 tick 结算用的算式，这里只做聚合展示，
     * 不另立口径。合成没在跑（或配方/修饰符丢失）时两个数都是 0，Jade 侧就不显示能量行。
     *
     * <p>0.115.0：工厂对非空闲核心／普通线程分别按各自并行、催化剂和脚本修改器求和；
     * 暂时阻塞但仍持有配方的线程仍计入需求。未成型或红石暂停时不显示工厂速率。
     * 普通机器继续使用当前合成的修改器；读取不更改配方执行状态，聚合溢出时饱和为 long 上限。
     *
     * <p>0.121.48（P06-17）：普通机器改为走 {@link #modifiersFor(MachineRecipe)}——也就是在途
     * 结算用的那条有效修改器链：开工时冻结的结构／升级／催化剂贡献保留，脚本贡献换成**当前**
     * 脚本修改器。旧版直接把 {@code activeCraftModifiers} 交给 {@code EnergyUsage.of}，于是服主
     * 在同一在途配方里改了能耗修改器后，真实扣费已经变成 5→10→15，名义快照还停在开工时的 5。
     * 这里只换只读快照的取值来源：不重建执行配方、不重新扫描催化剂或仓口、不改冻结贡献与付款。
     */
    public long[] energySnapshot(@Nullable Level level) {
        if (level == null || this.redstoneStopped) return new long[]{0L, 0L};
        if (this.factoryEnabled) {
            return this.formed && !this.redstoneStopped ? this.factoryEngine.energyUsage().toArray()
                    : new long[]{0L, 0L};
        }
        if (this.status != ControllerStatus.CRAFTING || this.activeRecipeId == null) {
            return new long[]{0L, 0L};
        }
        MachineRecipe recipe = resolveRecipe(level);
        if (recipe == null) {
            return new long[]{0L, 0L};
        }
        return EnergyUsage.of(recipe, modifiersFor(recipe), this.activeParallelism).toArray();
    }

    /**
     * 这台机器**有**配方、但此刻一条都开工不了时，第一条给出的失败原因；真的没有配方时返回 {@code null}。
     *
     * <p>它补上了 {@link #findRecipe} 丢掉的那一半信息：那个方法只返回<strong>能开工</strong>的配方，
     * 于是「配方在、只是料不够」与「一条配方都没有」都变成 {@code null}，控制器无法区分，
     * 只能显示「没有相匹配的配方」并把失败文案清掉。
     *
     * <p>只在「一条都开不了」的那一 tick 才走这条路（{@code findRecipe} 找不到时），所以它不进入
     * 正常合成的路径。代价是那一个 tick 多遍历一次配方表，换的是玩家能看到<strong>真正的原因</strong>。
     *
     * <p><b>没有原因时不编一个。</b>这里只返回需求自己给出的 {@code startFailure}；它答 {@code null} 时
     * 调用者把状态置为 {@code IDLE}（「有配方、在等条件」）而不写任何失败文案。
     * 早先的草稿在这里回退成「缺少物品输入」，那是<strong>撒谎</strong>——原因也可能是输出仓没有空间，
     * 而一句错的失败文案比没有文案更糟：它会把人的注意力引到一个不缺的东西上。
     */
    @Nullable
    private String unaffordableStartFailure(Level level) {
        if (this.machineId == null) {
            return null;
        }
        for (MachineRecipe recipe : MachineRecipeIndex.forMachine(level.getRecipeManager(), this.machineId)) {
            // **条件必须是「开不了工」，不能写成「开得了工」。** 这个方法只在 `findRecipe` 找不到配方时
            // 才被调用，所以走到这里就说明「有配方认这台机器、但它此刻过不了」。第一版把条件写反了
            // （`canStart && canTick` 时 return null），于是**能开工时给出原因、开不了工时什么都不给**——
            // 正好与需要的相反，而实机上就表现为「状态是闲置、原因空的」。
            //
            // 只问 `canStart` 那一半的原因：`startFailure` 是「开工阶段」的消息，
            // 而 `canTick` 失败（例如每 tick 的能源）本来就不由它描述。
            //
            // A2：按组问。旧版在每组都失败时用最后一组的失败文案（`RecipeCraftingContext.java:502-549`
            // 的 fkey），这里照做——组之间缺的东西可能不同，随便取一组会给出另一组的谎。
            if (this.startableGroup(recipe) != null) {
                continue;
            }
            return recipe.startFailure(this.lastGroupScope(), modifiersFor(recipe));
        }
        return null;
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
        for (MachineRecipe recipe : MachineRecipeIndex.forMachine(level.getRecipeManager(), this.machineId)) {
            // A2：能开工 = 至少有一组能开工。锁定哪一组由 tick 里那次 startableGroup 决定，两步必须
            // 问同一个问题，否则会出现「选出了配方、开工时却找不到组」。
            if (this.startableGroup(recipe) != null) {
                return executionCopyOf(recipe);
            }
        }
        return null;
    }

    /**
     * Walks the formed structure and buckets every hatch it finds.
     *
     * <p>走的是 {@code effectivePattern} 而不是 {@code machine.pattern()}：对**动态**机器（波 3b）来说，
     * 定义里的 {@code parts} 只是基座，有效结构才是基座加上量出来的各段。读基座会漏掉落在动态段上的仓口，
     * 而那正是装配线放输入/输出总线的地方。
     */
    private HatchCollection collectHatches(Level level, BlockPos pos, @Nullable MachineDefinition machine,
                                           @Nullable MachinePattern effectivePattern) {
        inputDefinition = machine;
        inputPattern = effectivePattern;
        java.util.Set<BlockPos> dependencies = new java.util.HashSet<>();
        HatchCollection ports = ControllerPortCollector.collect(this,level,pos,machine,effectivePattern,dependencies);
        INPUT_DEPENDENCIES.watch(level,this,dependencies);
        return ports;
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
                                                                    @Nullable MachineDefinition machine,
                                                                    @Nullable MachinePattern effectivePattern) {
        if (machine == null || effectivePattern == null) {
            return ParallelControllerCollection.EMPTY;
        }
        List<ParallelController> found = new ArrayList<>();
        for (BlockPos relative : effectivePattern.positions().keySet()) {
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
                                                        @Nullable MachineDefinition machine,
                                                        @Nullable MachinePattern effectivePattern) {
        if (machine == null || effectivePattern == null) {
            return List.of();
        }
        List<UpgradeBusUtility> found = new ArrayList<>();
        for (BlockPos relative : effectivePattern.positions().keySet()) {
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
        MachineDefinition definition = MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide).orElse(null);
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
        Float stored = this.smartInterfaces.set(MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide).orElse(null), type, value);
        if (stored == null) {
            return null;
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_ALL);
            // C2 小件（0.91.0）：成功写入后发事件（写入被拒不发）。所有服务端写入路径都过这里。
            com.reborn.modularmachinery.api.event.MachineEvents.post(
                    new com.reborn.modularmachinery.api.event.SmartInterfaceUpdateEvent(this, this.machineId,
                            type, stored));
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
    /**
     * The start-failure key for whichever side we are on.
     *
     * <p>Server side returns the raw key. Client side first resolves the menu's ordinal mirror, because that
     * channel updates the known keys without waiting for the block-entity update tag. When the ordinal is
     * "no failure", the full string mirrored by {@link #load} (the update tag carries it verbatim) is returned
     * <b>only if that string is itself not a known key</b> — script/author custom keys such as a typed
     * {@code interface_number_input} notequal or the magic requirement's fixed key have no ordinal slot, so
     * this is the only way they reach the client (P06-18, 0.121.34; reworked 0.121.35).
     *
     * <p><b>Rework (0.121.35): the fallback must not resurrect a known key.</b> The first cut returned the
     * mirrored string whenever the ordinal said "no failure", which also matched a <i>known</i> key's stale
     * snapshot: a known failure that the menu had already cleared came back until the clear's update tag
     * landed (8 keys × menu-clear/invalid-ordinal, a regression against 0.121.32). Known keys are now
     * governed by the ordinal channel alone — once it says "no failure", a known string in the mirror keeps
     * returning {@code null}. Only a string outside {@link #START_FAILURE_KEYS} uses the fallback, so
     * clearing such a key still requires its own update tag (the ordinal alone cannot distinguish "cleared"
     * from "custom key" — that limitation is inherent to the int channel and stays, documented rather than
     * hidden by a revision state machine).
     */
    @Nullable
    public String startFailureKey() {
        if (isServerSide()) {
            return this.startFailureKey;
        }
        String known = failureKeyAt(this.clientStartFailure);
        if (known != null) {
            return known;
        }
        String full = this.startFailureKey;
        return full != null && failureIndex(full) == NO_START_FAILURE ? full : null;
    }

    /**
     * The failure keys the menu can mirror, indexed by the integer it puts in its {@code ContainerData}. The
     * menu channel carries integers only — the same constraint that produced
     * {@code FactoryControllerMenu}'s status ordinals — so the string is mapped through this fixed list, and the
     * order is part of the wire format.
     *
     * <p><b>波 2 的六条 {@code craftcheck.failure.*} 曾经不在这份列表里，而那是一堵看不见的墙。</b>
     * {@code failureIndex} 对「不在列表里的键」返回 {@link #NO_START_FAILURE}，于是那些文案
     * <strong>从来没有可能到达客户端</strong>：服务端设了、`sendBlockUpdated` 发了、
     * 客户端读到的却是「没有失败」。实机验证时表现为「状态是闲置、但状态行下面什么都没有」，
     * 而真正的原因（缺少物品输入）在被吞掉之前就已经死在这一步了。
     *
     * <p>教训与本项目那条「日志里没有报错不提供任何信息」是同一类：**一份白名单漏了一项时，
     * 它不会报错，只会安静地把功能删掉。** 所以 `START_FAILURE_KEYS` 现在必须包含
     * {@code MachineRequirement} 那一侧可能产出的<strong>每一个</strong>键，
     * 而 {@code ParallelCraftCheck} 的 S4 段断言这几条键**确实能被镜像到客户端**（而不是只断言文案存在）。
     *
     * <p><b>顺序是通信格式的一部分</b>：新增的键只能<strong>追加</strong>在末尾。中间的插入或删除会让
     * 一个旧客户端把索引读到错误的键上——所以下面六条的相对顺序按波 2 的约定固定下来。
     */
    public static final List<String> START_FAILURE_KEYS = List.of(
            com.reborn.modularmachinery.recipe.InterfaceNumberInputRequirement.MISSING_KEY,
            SmartInterfaceType.DEFAULT_NOT_EQUAL_KEY,
            // 波 2（0.32.0）新增的六条，追加在末尾。
            com.reborn.modularmachinery.recipe.ItemRequirement.FAILURE_ITEM_INPUT,
            com.reborn.modularmachinery.recipe.ItemRequirement.FAILURE_ITEM_OUTPUT_SPACE,
            com.reborn.modularmachinery.recipe.FluidRequirement.FAILURE_FLUID_INPUT,
            com.reborn.modularmachinery.recipe.FluidRequirement.FAILURE_FLUID_OUTPUT_SPACE,
            com.reborn.modularmachinery.recipe.EnergyRequirement.FAILURE_ENERGY_INPUT,
            com.reborn.modularmachinery.recipe.EnergyRequirement.FAILURE_ENERGY_OUTPUT_SPACE);

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
    private void recordStartFailure(MachineRecipe recipe, HatchCollection scope) {
        recordStartFailureKey(recipe == null ? null : recipe.startFailure(scope, modifiersFor(recipe)));
    }

    /** {@link #recordStartFailure(MachineRecipe)} for the cases with no recipe at all. */
    private void clearStartFailure() {
        recordStartFailureKey(null);
        recordStartFailureCause(null);
    }

    /**
     * C2b（0.88.0）：记录**任意字符串**的失败原因（{@code RecipeCheckEvent.setFailed} 的通道半边），
     * 与固定失败键并行存在。与 {@link #recordStartFailureKey} 同一套镜像动作：赋值、setChanged、
     * 服务端发方块更新——字符串走 update tag，客户端在 {@code load} 里镜像。
     */
    private void recordStartFailureCause(@Nullable String cause) {
        if (java.util.Objects.equals(cause, this.startFailureCause)) {
            return;
        }
        this.startFailureCause = cause;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * 当前失败原因字符串（C2b 通道），没有则为 {@code null}。客户端读镜像字段——与
     * {@link #startFailureKey()} 的两侧分工相同。
     */
    @Nullable
    public String failureCauseText() {
        if (!scriptState.status().isEmpty()) return scriptState.status();
        return isServerSide() ? this.startFailureCause : this.clientFailureCause;
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
        return MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide).map(MachineDefinition::displayName).orElse(null);
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

    /** 建造/投影也要能在结构尚未成型时确定目标机器。 */
    @Nullable
    public ResourceLocation previewMachineId() {
        if (formed && machineId != null) return machineId;
        if (boundMachine != null) return boundMachine;
        return blueprintMachineId();
    }

    /** Display name of the blueprint's machine, or {@code null} when none is slotted or it is unknown here. */
    @Nullable
    public Component blueprintMachineName() {
        return MachineRegistry.byId(blueprintMachineId(), this.level != null && this.level.isClientSide).map(MachineDefinition::displayName).orElse(null);
    }

    public boolean isRedstoneStopped() { return redstoneStopped; }

    public ControllerStatus status() { return status; }

    /**
     * 这台控制器成型时用的旋转（相对 NORTH 的逆时针四分之一圈数）。
     *
     * <p>公开它是因为**动态结构的展开依赖朝向**：诊断命令要把「世界里实际生效的位置标签」重新算一遍，
     * 而只按定义展开（朝向 0）会得到一份与实际不同的标签表——那正好会让诊断给出错误答案。
     */
    public int rotationSteps() {
        BlockState state = getBlockState();
        return formed || state == null || !state.hasProperty(MachineControllerBlock.FACING)
                ? rotationSteps : MachineRegistry.rotationStepsFor(state.getValue(MachineControllerBlock.FACING));
    }

    /** The ports gathered from the formed structure. */
    public HatchCollection hatches() { return this.hatches; }
    public void refreshInputPorts() {
        if (level != null && !level.isClientSide
                && (structureChunksPaused || requiredDynamicChunksUnavailable(level, worldPosition))) {
            hatches = HatchCollection.builder().build();
            return;
        }
        if (formed && level != null && !level.isClientSide && inputPattern != null)
            hatches = collectHatches(level, worldPosition, inputDefinition, inputPattern);
    }
    public boolean usesInputProvider(Object identity) {
        if (!formed || level == null) return false;
        for (BlockPos relative : formedPartOffsets) {
            BlockPos at = worldPosition.offset(relative);
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof com.reborn.modularmachinery.machine.MachineInputProvider source && source.inputIdentity() == identity) return true;
        }
        return false;
    }

    /** 真实匹配结构中的成员关系，供应器不能以邻近搜索猜测归属。 */
    public boolean containsStructurePart(BlockPos part) {
        return formed && formedPartOffsets.contains(part.subtract(worldPosition));
    }

    /** A2：正在跑的合成锁定的输入组；{@code null} 表示没有合成在跑（或是在途的旧存档）。 */
    @Nullable
    public Integer activeGroupId() { return this.activeGroupId; }

    /**
     * The parallel controllers the formed structure contains, and the sum they contribute — the piece M6d adds
     * to the ceiling. Exposed so the sum's origin can be inspected (and asserted) without guessing.
     */
    public ParallelControllerCollection parallelControllers() { return this.parallelControllers; }

    /** The upgrade buses the formed structure contains, in pattern order. */
    public List<UpgradeBusUtility> upgradeBuses() { return this.upgradeBuses; }

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }

    /** E9（0.99.0）：Jade 信息用的只读访问器——当前并行份数（未开工时为 1）。 */
    public int activeParallelism() { return this.activeParallelism; }

    /** E9（0.99.0）：Jade 信息用的只读访问器——结构并行上限（delegating 到 machineCeiling）。 */
    public int parallelCeiling() { return this.machineCeiling(); }

    // ------------------------------------------------------- parallelism and timing

    /** Include optional dependencies even when they are absent from the formed part collection. */
    private void includeOptionalStructureBounds(MachineRegistry.Match match) {
        int rotation = match.rotationSteps();
        for (MachineModifier modifier : match.machine().modifiers()) {
            BlockPos at = modifier.rotatedOffset(rotation);
            includeStructureOffset(at.getX(), at.getY(), at.getZ());
        }
        for (MachineMultiBlockModifier modifier : match.machine().multiBlockModifiers()) {
            includeStructureSection(modifier.pattern(), rotation, BlockPos.ZERO, BlockPos.ZERO, 0);
        }
        for (int i = 0; i < match.machine().dynamicPatterns().size(); i++) {
            DynamicPattern dynamic = match.machine().dynamicPatterns().get(i);
            // A successful match can stop on the end cap, or one step past an overlapping cap. Watch
            // both the next section and that extra probe, including the overlength probe at maxSize.
            long probe = Math.min((long) match.dynamicPatterns().get(i).size() + 1L, dynamic.maxSize());
            BlockPos start = DynamicPattern.rotate(dynamic.startOffset(), rotation);
            BlockPos step = DynamicPattern.rotate(dynamic.stepOffset(), rotation);
            includeStructureSection(dynamic.part(), rotation, start, step, probe);
            if (dynamic.endPart() != null) {
                includeStructureSection(dynamic.endPart(), rotation, start, step, probe);
            }
        }
    }

    /** Bounds at the two anchor extremes cover every intervening repeated section in constant space. */
    private void includeStructureSection(MachinePattern section, int rotation, BlockPos start,
            BlockPos step, long probe) {
        for (BlockPos corner : List.of(section.min(), section.max())) {
            BlockPos at = DynamicPattern.rotate(corner, rotation);
            long x = (long) start.getX() + at.getX();
            long y = (long) start.getY() + at.getY();
            long z = (long) start.getZ() + at.getZ();
            includeStructureOffset(x, y, z);
            includeStructureOffset(x + step.getX() * probe, y + step.getY() * probe,
                    z + step.getZ() * probe);
        }
    }

    private void includeStructureOffset(long x, long y, long z) {
        int bx = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, x));
        int by = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, y));
        int bz = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, z));
        structureMinOffset = new BlockPos(Math.min(structureMinOffset.getX(), bx),
                Math.min(structureMinOffset.getY(), by), Math.min(structureMinOffset.getZ(), bz));
        structureMaxOffset = new BlockPos(Math.max(structureMaxOffset.getX(), bx),
                Math.max(structureMaxOffset.getY(), by), Math.max(structureMaxOffset.getZ(), bz));
    }

    /** Structure modifiers whose block is present right now. */
    public List<MachineModifier> activeModifiers() { return this.activeModifiers; }

    /** 当前命中的额外多块结构条件，按声明顺序。 */
    public List<MachineMultiBlockModifier> activeMultiBlockModifiers() {
        return this.activeMultiBlockModifiers;
    }

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

    /**
     * A3（0.64.1）：比较器看到的“正在工作”。
     *
     * <p>旧版有两个覆写：普通控制器 {@code TileMachineController.java:354-356} 是
     * {@code lastStrongPower <= 0 && getControllerStatus().isCrafting()}，工厂控制器
     * {@code TileFactoryController.java:720-730} 是「红石没停且任一线程有配方在跑」。
     * 本版一个方块实体同时承担两种模式，所以这里把两条并起来：
     * {@code redstoneStopped} 对应 {@code lastStrongPower}，{@code hasActiveCraft()} 对应单配方状态，
     * {@code factoryEngine.isWorking()} 对应工厂线程。
     */
    public boolean isWorking() {
        if (this.redstoneStopped) {
            return false;
        }
        return this.hasActiveCraft() || this.factoryEngine.isWorking();
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
        if (pendingFactoryState != null) {
            if (pendingFactoryState.contains("threadList")) tag.put("threadList", pendingFactoryState.get("threadList").copy());
            if (pendingFactoryState.contains("coreThreadList")) tag.put("coreThreadList", pendingFactoryState.get("coreThreadList").copy());
            return;
        }
        if (this.factoryEnabled && this.formed) {
            this.factoryEngine.save(tag);
        }
    }

    /**
     * Restores the factory threads a save wrote. Called from {@link #load} before the structure has been
     * re-checked, so the recipe lookup goes through the live recipe manager when the level is available.
     */
    public void loadFactoryThreads(CompoundTag tag) {
        if (this.level == null) {
            this.pendingFactoryState = tag.copy();
            return;
        }
        this.factoryEngine.load(tag, id -> {
            Level level = this.level;
            if (level == null || id == null) {
                return null;
            }
            return level.getRecipeManager().byKey(id)
                    .filter(recipe -> recipe instanceof MachineRecipe)
                    .map(recipe -> (MachineRecipe) recipe)
                    .orElse(null);
        }, new FactoryHostImpl().coreThreads());
    }

    /** 动态结构定格值的 NBT 键，与旧版同义（它把一组 {@code DynamicPattern.Status} 存进一个 tag）。 */
    private static final String TAG_DYNAMIC_PATTERNS = "dynamicPatterns";

    /**
     * 写出定格下来的动态结构：每个模式一条 {@code {name, size, facing}}。
     *
     * <p>形状与旧版相同（一个 list，每项一个 compound），但键名是本项目自己的拼法：旧版把整组
     * {@code Status} 交给 {@code PatternNBTString} 之类的辅助类去写，具体键名没有跨版本稳定的保证，
     * 所以这里不去猜它的字节布局，而是把这个<strong>信息</strong>照旧版的语义存下来。
     * 没有动态结构时不写这个键。
     */
    private void saveDynamicPatterns(CompoundTag tag) {
        if (this.dynamicPatterns.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (DynamicPattern.Status status : this.dynamicPatterns) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", status.patternName());
            entry.putInt("size", status.size());
            entry.putString("facing", status.face().getName());
            list.add(entry);
        }
        tag.put(TAG_DYNAMIC_PATTERNS, list);
    }

    /**
     * 读回动态结构定格值。
     *
     * <p>宽容失败是刻意的：这份数据<strong>描述</strong>结构，而结构在世界上随时会被重新核对并覆盖它
     * （{@code recheckStructure} 每次成功匹配都会重写）。所以一条读不动的记录只意味着「这一 tick 的端口集合
     * 按无标签处理」，下一次结构核对就修好了；为此让整个方块实体的加载抛异常是不划算的。
     */
    private void loadDynamicPatterns(CompoundTag tag) {
        this.dynamicPatterns = List.of();
        if (!tag.contains(TAG_DYNAMIC_PATTERNS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_DYNAMIC_PATTERNS, Tag.TAG_COMPOUND);
        List<DynamicPattern.Status> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Direction face = directionByName(entry.getString("facing"));
            if (face == null) {
                continue;
            }
            loaded.add(new DynamicPattern.Status(entry.getString("name"), entry.getInt("size"), face));
        }
        this.dynamicPatterns = List.copyOf(loaded);
    }

    /** 方向名到 {@link Direction}，不认识时返回 {@code null}（读者对坏数据保持宽容）。 */
    @Nullable
    private static Direction directionByName(String name) {
        for (Direction direction : Direction.values()) {
            if (direction.getName().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    /** 这台机器成型时定格下来的动态结构；没有动态结构时是空列表。 */
    public List<DynamicPattern.Status> dynamicPatterns() {
        return this.dynamicPatterns;
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
    /** 这台机器的染色，或 {@code null} 表示没设过。 */
    @javax.annotation.Nullable
    public Integer machineColor() {
        return this.machineColor;
    }

    /**
     * 把颜色推给这台机器**所有部件**（旧版 {@code TileMultiblockMachineController#distributeCasingColor}）。
     *
     * <p>为什么由控制器做这件事：部件本身**不知道自己属于哪台机器**（本版如此，旧版也如此——
     * 旧版是靠控制器遍历 pattern 里的方块位置去 `tryColorize` 的）。所以「哪个部件属于哪台机器」
     * 只有控制器知道，下发也就只能是它的活。
     *
     * <p>颜色会下发给结构中的仓与外壳。外壳原先没有方块实体，导致模型虽有 tint 层却无法取得机器颜色；
     * {@link MachineCasingBlockEntity} 现在负责保存和同步该值。
     */
    /** 定义的染色值；没有定义时 {@code null}（= 不染色）。 */
    @javax.annotation.Nullable
    private static Integer machineColorOf(@javax.annotation.Nullable MachineDefinition machine) {
        return machine == null ? null : machine.color();
    }
    private void distributeMachineColor(@javax.annotation.Nullable Integer color,
                                        @javax.annotation.Nullable MachinePattern pattern) {
        Integer previousColor = this.machineColor;
        List<BlockPos> nextOffsets = new ArrayList<>();
        if (pattern != null && color != null) {
            for (BlockPos relative : pattern.positions().keySet()) {
                BlockEntity part = this.level == null ? null : this.level.getBlockEntity(this.worldPosition.offset(relative));
                if (part instanceof MachineHatchBlockEntity || part instanceof MachineCasingBlockEntity || part instanceof com.reborn.modularmachinery.magic.MagicHatchBlockEntity || part instanceof WirelessEnergyHatch) {
                    nextOffsets.add(new BlockPos(relative.getX(), relative.getY(), relative.getZ()));
                }
            }
        }

        // If the formed pattern changed or disappeared, remove only the tint this controller previously applied
        // from parts that no longer belong to its current pattern.
        for (BlockPos previous : this.colorizedPartOffsets) {
            if (!nextOffsets.contains(previous)) {
                clearPartColor(previous, previousColor);
            }
        }

        this.machineColor = color;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        this.colorizedPartOffsets.clear();
        this.colorizedPartOffsets.addAll(nextOffsets);
        if (this.level == null || pattern == null) {
            return;
        }
        // 位置来源是**结构的 pattern**，与 collectHatches 走同一份位置集。
        // 不发明「搜索半径」：那会把相邻的、属于别的机器的部件也算进来（本项目对「看起来合理」的容忍度很低），
        // 而 pattern 恰好就是「这台机器占哪些格子」的权威答案。
        for (BlockPos relative : pattern.positions().keySet()) {
            BlockEntity part = this.level.getBlockEntity(this.worldPosition.offset(relative));
            if (part instanceof com.reborn.modularmachinery.magic.MagicHatchBlockEntity magic) {
                magic.setMachineColor(color);
            } else if (part instanceof WirelessEnergyHatch wireless) {
                wireless.setMachineColor(color);
            } else if (part instanceof MachineHatchBlockEntity hatch) {
                hatch.setMachineColor(color);
            } else if (part instanceof MachineCasingBlockEntity casing) {
                casing.setMachineColor(color);
            }
        }
    }

    /** Clear this controller's persisted tint assignments before its block entity is removed. */
    public void clearDistributedMachineColor(Direction facing) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        Integer colorToClear = this.machineColor;
        for (BlockPos relative : this.colorizedPartOffsets) {
            clearPartColor(relative, colorToClear);
        }
        // Compatibility with saves created before colorizedPartOffsets existed: while the controller and
        // structure are still present during onRemove, recover the matched pattern and clear its parts.
        if (this.colorizedPartOffsets.isEmpty() && this.formed && colorToClear != null) {
            MachineRegistry.Match match = MachineRegistry.findMatch(this.level, this.worldPosition, facing,
                    blueprintMachineId(), this.boundMachine);
            if (match != null) {
                for (BlockPos relative : match.pattern().positions().keySet()) {
                    clearPartColor(relative, colorToClear);
                }
            }
        }
        this.colorizedPartOffsets.clear();
        this.machineColor = null;
    }

    private void clearPartColor(BlockPos relative, @javax.annotation.Nullable Integer expectedColor) {
        if (this.level == null || expectedColor == null) {
            return;
        }
        BlockEntity part = this.level.getBlockEntity(this.worldPosition.offset(relative));
        if (part instanceof com.reborn.modularmachinery.magic.MagicHatchBlockEntity magic && java.util.Objects.equals(magic.machineColor(),expectedColor)) {
            magic.setMachineColor(null);
        } else if (part instanceof WirelessEnergyHatch wireless
                && java.util.Objects.equals(wireless.machineColor(), expectedColor)) {
            wireless.setMachineColor(null);
        } else if (part instanceof MachineHatchBlockEntity hatch
                && java.util.Objects.equals(hatch.machineColor(), expectedColor)) {
            hatch.setMachineColor(null);
        } else if (part instanceof MachineCasingBlockEntity casing
                && java.util.Objects.equals(casing.machineColor(), expectedColor)) {
            casing.setMachineColor(null);
        }
    }

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
        tag.putUUID("paidInputIdentity", paidInputIdentity);
        tag.put("scriptState", scriptState.save());
        if (executionRecipe != null && activeRecipeId != null) tag.put("scriptExecution", executionRecipe.saveExecutionState());
        else if (pendingExecutionState != null) tag.put("scriptExecution", pendingExecutionState.copy());
        tag.put("items", items.serializeNBT());
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        tag.putBoolean("formed", formed);
        if (formedPartSnapshotKnown) tag.putLongArray("formedParts", formedPartOffsets.stream()
                .mapToLong(BlockPos::asLong).toArray());
        if (structureSelectionKnown) {
            CompoundTag selection = new CompoundTag();
            selection.putInt("rotation", structureSelectionRotation);
            selection.putString("definition", structureSelectionFingerprint);
            if (structureSelectionBlueprint != null) selection.putString("blueprint", structureSelectionBlueprint.toString());
            tag.put("structureSelection", selection);
        }
        tag.putBoolean("redstoneStopped", redstoneStopped);
        tag.putInt("status", status.ordinal());
        tag.putInt("activeParallelism", activeParallelism);
        tag.putInt("activeDuration", activeDuration);
        if (activeCraftModifiers != null) tag.put("activeModifiers", activeCraftModifiers.save());
        tag.put("activeScriptModifiers", activeScriptModifiers.save());
        // A2：正在跑的那组。不存的话重进世界会退回「全部端口」，等于中途换料。
        if (activeGroupId != null) {
            tag.putInt("activeGroupId", activeGroupId);
            activeInputBinding.save(tag);
        }
        if (machineId != null) {
            tag.putString("machine", machineId.toString());
        }
        // A11: the original wrote this as a UUID string under "owner" (TileMultiblockMachineController:1415-1417).
        ControllerSecurity.putOwner(tag, this.owner);
        // C2b：失败原因字符串。既进存档（重进世界后界面仍显示第三方给的理由）也进 update tag（同步）。
        if (this.startFailureCause != null && !this.startFailureCause.isEmpty()) {
            tag.putString("failureCause", this.startFailureCause);
        }
        // 波 6：染色。与 machineId 同路同步到客户端——渲染时的 tint 读的就是它。
        if (machineColor != null) {
            tag.putInt("machineColor", machineColor);
        }
        tag.put("machineColorParts", new LongArrayTag(this.colorizedPartOffsets.stream()
                .mapToLong(BlockPos::asLong).toArray()));
        if (activeRecipeId != null) {
            tag.putString("activeRecipe", activeRecipeId.toString());
        }
        // M6e: the factory's threads, under the original's own keys.
        saveFactoryThreads(tag);
        // 波 3b：动态结构定格下来的节数与方向。配方的 selector-tag 带着段号，所以长度一旦确定就必须活过存档。
        saveDynamicPatterns(tag);
        // The original's extra thread count, under the original's own key — but now written as a full int:
        // the Java setter accepts any non-negative value (the original's own field was an int too), and the
        // old short write wrapped anything above 32767 into a negative on disk (40000 read back as -25536).
        // The key is unchanged; old saves still load, see the note in load().
        tag.putInt("extraThreadCount", this.extraThreadCount);
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
        if (tag.hasUUID("paidInputIdentity")) paidInputIdentity = tag.getUUID("paidInputIdentity");
        scriptState.load(tag.getCompound("scriptState"));
        pendingExecutionState = tag.contains("scriptExecution") ? tag.getCompound("scriptExecution").copy() : null;
        items.deserializeNBT(tag.getCompound("items"));
        progress = tag.getInt("progress");
        maxProgress = Math.max(1, tag.getInt("maxProgress"));
        formed = tag.getBoolean("formed");
        formedPartSnapshotKnown = tag.contains("formedParts", Tag.TAG_LONG_ARRAY);
        java.util.Set<BlockPos> loadedParts = new java.util.HashSet<>();
        if (formedPartSnapshotKnown) for (long packed : tag.getLongArray("formedParts")) loadedParts.add(BlockPos.of(packed));
        formedPartOffsets = java.util.Set.copyOf(loadedParts);
        redstoneStopped = tag.getBoolean("redstoneStopped");
        status = ControllerStatus.byOrdinal(tag.getInt("status"));
        activeParallelism = Math.max(1, tag.getInt("activeParallelism"));
        activeDuration = Math.max(1, tag.getInt("activeDuration"));
        activeCraftModifiers = tag.contains("activeModifiers")
                ? RecipeModifiers.load(tag.getList("activeModifiers", Tag.TAG_COMPOUND)) : null;
        activeScriptModifiers = RecipeModifiers.load(tag.getList("activeScriptModifiers", Tag.TAG_COMPOUND));
        executionRecipe = null;
        executionRecipeSource = null;
        // A2：缺这个键的存档（0.63.9 及更早）代表「在途合成没有组」——保持 null，tick 里按全部端口继续。
        activeGroupId = tag.contains("activeGroupId") ? tag.getInt("activeGroupId") : null;
        activeInputBinding = activeGroupId == null ? InputGroupBinding.manual() : InputGroupBinding.load(tag, activeGroupId);
        machineId = tag.contains("machine") ? ResourceLocation.tryParse(tag.getString("machine")) : null;
        // C2b：失败原因字符串。磁盘加载（服务端）与 update tag 加载（客户端）都会走这里——
        // 两侧各自要自己的字段，所以两边都写。
        String loadedCause = tag.contains("failureCause") ? tag.getString("failureCause") : null;
        this.startFailureCause = loadedCause;
        this.clientFailureCause = loadedCause;
        // A11: an absent or malformed owner stays null; the original only warned and left the field unset.
        this.owner = ControllerSecurity.ownerOf(tag);
        machineColor = tag.contains("machineColor") ? tag.getInt("machineColor") : null;
        this.colorizedPartOffsets.clear();
        if (tag.contains("machineColorParts", Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray("machineColorParts")) {
                this.colorizedPartOffsets.add(BlockPos.of(packed));
            }
        }
        activeRecipeId = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
        // M6e. A factory controller is a factory by virtue of its block plus the formed machine, and the
        // definition is available here, so the mode can be restored before the first tick. That matters: the
        // engine's thread list must not be read as "single-craft" state in the tick between load and the first
        // structure check.
        this.factoryEnabled = FactoryThreadModel.factoryEnabled(this.factoryBlock,
                MachineRegistry.byId(this.machineId, this.level != null && this.level.isClientSide).orElse(null));
        loadFactoryThreads(tag);
        // 波 3b：把定格下来的动态结构读回来。它必须在第一次结构核对之前就位，否则那一 tick 里
        // 配方的 selector-tag 会指向一个还不存在的段（读到空端口集合），表现为「重进世界后合成停一下」。
        loadDynamicPatterns(tag);
        structureChunksPaused = false;
        structureChunkUnavailable = status == ControllerStatus.CHUNK_UNLOADED;
        structureSelectionKnown = tag.contains("structureSelection", Tag.TAG_COMPOUND);
        structureSelectionDefinition = null;
        structureSelectionFingerprint = "";
        MachineDefinition currentDefinition = MachineRegistry.byId(machineId, level != null && level.isClientSide).orElse(null);
        if (structureSelectionKnown) {
            CompoundTag selection = tag.getCompound("structureSelection");
            structureSelectionRotation = selection.getInt("rotation");
            if (formed) rotationSteps = structureSelectionRotation & 3;
            structureSelectionBlueprint = selection.contains("blueprint")
                    ? ResourceLocation.tryParse(selection.getString("blueprint")) : null;
            structureSelectionFingerprint = selection.getString("definition");
            if (structureSelectionFingerprint.equals(structureFingerprint(currentDefinition)))
                structureSelectionDefinition = currentDefinition;
        } else if (formed && formedPartSnapshotKnown && currentDefinition != null
                && getBlockState().hasProperty(MachineControllerBlock.FACING)) {
            // Legacy saves did not carry a definition revision. Preserve their adopted footprint conservatively.
            rememberStructureSelection(currentDefinition,
                    MachineRegistry.rotationStepsFor(getBlockState().getValue(MachineControllerBlock.FACING)));
            rotationSteps = structureSelectionRotation;
        }
        // Widened to a full int (0.121.33); 0.121.32 and older wrote a short under the same key, so branch on
        // the stored NBT type. A legacy short that wrapped a larger value into a negative cannot be recovered —
        // the wrapped-away bits are gone — and the non-negative setter rule clamps it to 0, the same fallback
        // the original used for a missing or malformed tag. Positive short values (0/1/…/32767) survive as-is.
        this.extraThreadCount = tag.getTagType("extraThreadCount") == Tag.TAG_SHORT
                ? Math.max(0, tag.getShort("extraThreadCount"))
                : Math.max(0, tag.getInt("extraThreadCount"));
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

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        Integer previousColor = this.machineColor;
        super.onDataPacket(connection, packet);
        if (this.level != null && this.level.isClientSide
                && !java.util.Objects.equals(previousColor, this.machineColor)) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
