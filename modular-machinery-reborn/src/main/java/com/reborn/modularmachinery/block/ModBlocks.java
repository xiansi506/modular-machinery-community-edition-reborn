package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.item.MachineControllerItem;
import com.reborn.modularmachinery.item.MocMachineControllerItem;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.item.ParallelControllerItem;
import com.reborn.modularmachinery.item.PropertyBlockItem;
import com.reborn.modularmachinery.item.UpgradeBusItem;
import com.reborn.modularmachinery.machine.MachineDirectory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block, item and block entity registration.
 *
 * <p>Hatch tiers follow the original mod's model: <b>one block per family</b> carrying a {@code size}
 * blockstate property, plus <b>one block item per tier</b>. Casing works the same way with a {@code casing}
 * property.
 *
 * <p>Controllers exist in both flavours the original supported — the generic one plus one bound to each
 * built-in machine.
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ModularMachineryReborn.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModularMachineryReborn.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ModularMachineryReborn.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModularMachineryReborn.MOD_ID);

    /**
     * Every machine that gets a controller of its own, as {@code <path>_controller}: one per machine
     * declaration in {@code config/modular_machinery_reborn/machinery/}.
     *
     * <p>The mod ships no machines, so it ships no controllers of its own either — content arrives as a data
     * pack. A machine only gets a block when a file in that directory names it, which is the one moment a block
     * can be registered. A declaration may be a full machine definition, or just a {@code registryname} that
     * claims a controller while the definition stays in a data pack and remains reloadable.
     */
    public static final List<MachineDirectory.MachineRef> BOUND_CONTROLLERS = discoverBoundControllers();

    private static List<MachineDirectory.MachineRef> discoverBoundControllers() {
        java.nio.file.Path root = machineDirectoryOrNull();
        if (root == null) {
            return List.of();
        }
        return discoverBoundControllers(root);
    }

    /**
     * {@link MachineDirectory#directory()} resolves through {@code FMLPaths.CONFIGDIR}, which is null outside a
     * game run (the same NPE {@code UpgradeDirectory} documents). One guard here, so every caller below — and the
     * acceptance harness — gets a usable answer.
     */
    private static java.nio.file.Path machineDirectoryOrNull() {
        try {
            return MachineDirectory.directory();
        } catch (Throwable throwable) {
            ModularMachineryReborn.LOGGER.warn("[{}] Could not resolve the machine directory during startup, so "
                    + "only the controllers the mod registers unconditionally will exist: {}",
                    ModularMachineryReborn.MOD_ID, throwable.toString());
            return null;
        }
    }

    /**
     * The same discovery against an explicit root, so the "which declarations get a controller" rule can be
     * driven offline.
     */
    static List<MachineDirectory.MachineRef> discoverBoundControllers(java.nio.file.Path root) {
        Map<String, MachineDirectory.MachineRef> refs = new LinkedHashMap<>();
        List<MachineDirectory.MachineRef> declared = MachineDirectory.scanMachinePaths(root);
        for (MachineDirectory.MachineRef ref : declared) {
            MachineDirectory.MachineRef previous = refs.put(ref.path(), ref);
            if (previous != null && !previous.machineId().equals(ref.machineId())) {
                ModularMachineryReborn.LOGGER.warn("[{}] Two machine declarations both want the controller "
                                + "block {}_controller; '{}' wins over '{}'",
                        ModularMachineryReborn.MOD_ID, ref.path(), ref.machineId(), previous.machineId());
            }
        }
        if (!declared.isEmpty()) {
            ModularMachineryReborn.LOGGER.info("[{}] {} controller block(s) registered for machines declared "
                    + "in {}", ModularMachineryReborn.MOD_ID, refs.size(), root);
        }
        return List.copyOf(refs.values());
    }

    public static final RegistryObject<Block> MACHINE_CONTROLLER = BLOCKS.register("machine_controller",
            () -> new MachineControllerBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> MACHINE_CONTROLLER_ITEM = ITEMS.register("machine_controller",
            () -> new MachineControllerItem(MACHINE_CONTROLLER.get(), null, new Item.Properties()));

    // ------------------------------------------------------- M6e: factory controllers

    /**
     * {@code factory_controller} — the generic factory controller, the counterpart of the original's
     * {@code BlockController} / {@code BlockFactoryController} pair: the ordinary controller is bound to
     * nothing and finds any machine that fits, and so is this one.
     *
     * <p>It forms machines whose definition carries {@code has-factory}, and only those. That is enforced in
     * {@code MachineControllerBlockEntity} through
     * {@link com.reborn.modularmachinery.factory.FactoryThreadModel#factoryEnabled}, so a machine without the
     * flag cannot be run by a factory controller even if the block is placed correctly.
     *
     * <p>Its blockstate and item model are shipped (they are two small JSON files in the mod's own namespace),
     * unlike the per-declaration controllers below whose names only exist in a player's config directory.
     */
    public static final RegistryObject<Block> FACTORY_CONTROLLER = BLOCKS.register("factory_controller",
            () -> new MachineControllerBlock(null, true,
                    BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> FACTORY_CONTROLLER_ITEM = ITEMS.register("factory_controller",
            () -> new MachineControllerItem(FACTORY_CONTROLLER.get(), null, new Item.Properties()));

    /**
     * Machines that get a factory controller of their own, as {@code <path>_factory_controller} — the original's
     * {@code RegistryBlocks.java:422-429}, where a machine with {@code isHasFactory()} got one block in addition
     * to (not instead of) its ordinary controller.
     *
     * <p>Membership comes from a {@code has-factory: true} in the config-directory declaration, which is the one
     * place readable before the block registry freezes. See the D15 record.
     */
    public static final List<MachineDirectory.MachineRef> FACTORY_CONTROLLERS = discoverFactoryControllers();

    private static List<MachineDirectory.MachineRef> discoverFactoryControllers() {
        List<MachineDirectory.MachineRef> refs = new ArrayList<>();
        for (MachineDirectory.MachineRef ref : BOUND_CONTROLLERS) {
            if (ref.factoryController()) {
                refs.add(ref);
            }
        }
        if (!refs.isEmpty()) {
            ModularMachineryReborn.LOGGER.info("[{}] {} factory controller block(s) registered for machines "
                    + "whose declaration sets 'has-factory': {}", ModularMachineryReborn.MOD_ID, refs.size(),
                    machineDirectoryOrNull());
        }
        return List.copyOf(refs);
    }

    /** {@code <machine path>_factory_controller} blocks and their items, keyed by machine path. */
    private static final Map<String, RegistryObject<Block>> FACTORY_CONTROLLER_BLOCKS = new LinkedHashMap<>();
    private static final List<RegistryObject<Item>> FACTORY_CONTROLLER_ITEMS = new ArrayList<>();

    static {
        for (MachineDirectory.MachineRef ref : FACTORY_CONTROLLERS) {
            RegistryObject<Block> block = BLOCKS.register(ref.path() + "_factory_controller",
                    () -> new MachineControllerBlock(ref.machineId(), true,
                            BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
            FACTORY_CONTROLLER_BLOCKS.put(ref.path(), block);
            FACTORY_CONTROLLER_ITEMS.add(ITEMS.register(ref.path() + "_factory_controller",
                    () -> new MachineControllerItem(block.get(), ref.machineId(), new Item.Properties())));
        }
    }

    /** {@code <machine path>_controller} blocks and their items, keyed by machine path. */
    private static final Map<String, RegistryObject<Block>> BOUND_CONTROLLER_BLOCKS = new LinkedHashMap<>();
    private static final List<RegistryObject<Item>> BOUND_CONTROLLER_ITEMS = new ArrayList<>();

    static {
        for (MachineDirectory.MachineRef ref : BOUND_CONTROLLERS) {
            RegistryObject<Block> block = BLOCKS.register(ref.path() + "_controller",
                    () -> new MachineControllerBlock(ref.machineId(),
                            BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
            BOUND_CONTROLLER_BLOCKS.put(ref.path(), block);
            BOUND_CONTROLLER_ITEMS.add(ITEMS.register(ref.path() + "_controller",
                    () -> new MachineControllerItem(block.get(), ref.machineId(), new Item.Properties())));
        }
    }

    /** One block entity type serves every controller, generic and bound alike. */
    public static final RegistryObject<BlockEntityType<MachineControllerBlockEntity>> MACHINE_CONTROLLER_ENTITY =
            BLOCK_ENTITIES.register("machine_controller", () -> BlockEntityType.Builder
                    .of(MachineControllerBlockEntity::new, controllerBlocks()).build(null));

    // ------------------------------------------------------- 0.27.0: the MOC compatibility namespace

    /** {@code modularcontroller:<machine path>_controller} blocks, keyed by machine path. */
    private static final Map<String, RegistryObject<Block>> MOC_CONTROLLER_BLOCKS = new LinkedHashMap<>();
    private static final List<RegistryObject<Item>> MOC_CONTROLLER_ITEMS = new ArrayList<>();

    /**
     * The {@code modularcontroller} namespace needs its own {@code DeferredRegister} pair: a registry name's
     * namespace comes from the {@code DeferredRegister}'s own mod id, not from the name handed to
     * {@code register}, so the mod's own registers cannot produce an id in another namespace.
     *
     * <p>Every field the static block below touches is declared above it, because a static field initialiser in
     * this class runs in declaration order (the same reason {@link #BLOCKS} is the first field in the file).
     */
    private static final DeferredRegister<Block> MOC_BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MocNamespace.NAMESPACE);
    private static final DeferredRegister<Item> MOC_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MocNamespace.NAMESPACE);

    /**
     * Machines that get a controller in the {@code modularcontroller} compatibility namespace, as the original's
     * {@code RegistryBlocks.java:408-420} did — every declared machine that is not {@code factory-only}.
     *
     * <p><b>Not gated on {@code general.modular-controller-compatible-mode}.</b> That key cannot be read this
     * early: Forge fires {@code RegisterEvent} from {@code LOAD_REGISTRIES}, and the state that hands a spec its
     * file ({@code CONFIG_LOAD}) runs after it, so {@code ForgeConfigSpec.isLoaded()} is still false here and
     * every {@code ModConfig} accessor answers with its pre-config fallback. See {@link MocNamespace} for the
     * full chain. And the blocks cannot be gated on it in principle either: a save deserialises a placed block by
     * its id, so a missing id means the world does not load — the flag could only be read after the moment it
     * would have had to matter. Always registering is therefore the only shape that does the job, and it keeps
     * D8's rule intact: the block set is a pure increment that depends on no config value.
     *
     * <p>The cost is {@code 2 × N} extra registry entries on an install whose config directory declares machines.
     * The mitigation is that these items are <b>not</b> in the creative tab: they exist so an old save's block has
     * an item form again, not to be handed out.
     */
    public static final List<MachineDirectory.MachineRef> MOC_CONTROLLERS = discoverMocControllers();

    private static List<MachineDirectory.MachineRef> discoverMocControllers() {
        java.nio.file.Path root = machineDirectoryOrNull();
        if (root == null) {
            return List.of();
        }
        return discoverMocControllers(root);
    }

    /**
     * The same rule against an explicit root, for the offline harness. The rule itself lives in
     * {@link MocNamespace#compatibilityMachines(java.nio.file.Path)}, which can be driven without a game run —
     * this class cannot even be loaded there, because its static initialiser builds {@code DeferredRegister}s.
     */
    public static List<MachineDirectory.MachineRef> discoverMocControllers(java.nio.file.Path root) {
        List<MachineDirectory.MachineRef> refs = MocNamespace.compatibilityMachines(root);
        if (!refs.isEmpty()) {
            ModularMachineryReborn.LOGGER.info("[{}] {} controller block(s) registered in the '{}' "
                            + "compatibility namespace so saves made with the older ModularController mod still "
                            + "find their block: {}", ModularMachineryReborn.MOD_ID, refs.size(),
                    MocNamespace.NAMESPACE, root);
        }
        return refs;
    }

    static {
        for (MachineDirectory.MachineRef ref : MOC_CONTROLLERS) {
            // The name the original built in BlockController.java:94-100 — the namespace differs, the path is
            // the machine's own. The namespace here comes from MOC_BLOCKS's own mod id above.
            RegistryObject<Block> block = MOC_BLOCKS.register(ref.path() + "_controller",
                    () -> new MachineControllerBlock(ref.machineId(),
                            BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
            MOC_CONTROLLER_BLOCKS.put(ref.path(), block);
            MOC_CONTROLLER_ITEMS.add(MOC_ITEMS.register(ref.path() + "_controller",
                    () -> new MocMachineControllerItem(block.get(), ref.machineId(), new Item.Properties())));
        }
    }

    /** Wires the compatibility namespace's two registers onto the mod event bus, from the mod constructor. */
    public static void registerMocNamespace(net.minecraftforge.eventbus.api.IEventBus bus) {
        MOC_BLOCKS.register(bus);
        MOC_ITEMS.register(bus);
    }

    /**
     * Every compatibility-namespace controller block, in declaration order.
     *
     * <p>Deliberately separate from {@link #BOUND_CONTROLLER_BLOCKS}: an MOC controller is an <b>alias</b> of the
     * ordinary one, not a second controller, so it must never be enumerated where controllers are listed — the
     * JEI catalysts ({@code ModularMachineryJeiPlugin}), the structure previews
     * ({@code StructurePreviews}) and the generated resource pack's own-namespace half all keep reading the
     * ordinary collections.
     */
    public static List<RegistryObject<Block>> mocControllerBlocks() {
        return List.copyOf(MOC_CONTROLLER_BLOCKS.values());
    }

    /**
     * Every compatibility-namespace controller item, in declaration order.
     *
     * <p><b>Not</b> part of {@link #controllerItems()}, which is what JEI registers catalysts from, and not part
     * of the creative tab: adding the aliases to either would give every machine a duplicate entry. See
     * {@link #mocControllerBlocks()}.
     */
    public static List<RegistryObject<Item>> mocControllerItems() {
        return List.copyOf(MOC_CONTROLLER_ITEMS);
    }

    /** The compatibility-namespace controller block for a machine path, or {@code null} when it has none. */
    public static RegistryObject<Block> mocControllerBlock(String machinePath) {
        return MOC_CONTROLLER_BLOCKS.get(machinePath);
    }

    /** The decorative casing block; see {@link CasingType}. */
    public static final RegistryObject<Block> BLOCK_CASING = BLOCKS.register("blockcasing",
            () -> new BlockCasing(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()));

    public static final List<RegistryObject<Item>> CASING_ITEMS = new ArrayList<>();

    /** One block per hatch family; see {@link HatchKind}. */
    private static final Map<HatchKind, RegistryObject<Block>> HATCH_BLOCKS = new EnumMap<>(HatchKind.class);

    /** One block item per (family, tier): 7×2 item + 8×2 fluid + 8×2 energy = 46. */
    private static final List<RegistryObject<Item>> HATCH_ITEMS = new ArrayList<>();

    static {
        for (HatchKind kind : HatchKind.values()) {
            RegistryObject<Block> block = BLOCKS.register(kind.id(), () -> createHatchBlock(kind));
            HATCH_BLOCKS.put(kind, block);
            for (HatchTier tier : kind.ladder()) {
                HATCH_ITEMS.add(ITEMS.register(kind.itemId(tier),
                        () -> new PropertyBlockItem(block.get(), kind.sizeProperty(), tier,
                                kind.itemTranslationKey(tier), new Item.Properties())));
            }
        }
        for (CasingType casing : CasingType.values()) {
            String id = casing.itemId();
            CASING_ITEMS.add(ITEMS.register(id,
                    () -> new PropertyBlockItem(BLOCK_CASING.get(), BlockCasing.CASING, casing,
                            "block.modular_machinery_reborn." + id, new Item.Properties())));
        }
    }

    /**
     * One block entity type serves all six hatch families. The family and tier are read from the blockstate,
     * so a single type can be bound to every hatch block.
     */
    public static final RegistryObject<BlockEntityType<MachineHatchBlockEntity>> HATCH_ENTITY =
            BLOCK_ENTITIES.register("machine_hatch", () -> BlockEntityType.Builder.of(
                    MachineHatchBlockEntity::new,
                    HATCH_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    // ------------------------------------------------------- M6d: parallel controller

    /**
     * One block with a {@code type} property for all five parallel-controller tiers — the original's
     * {@code BlockParallelController}, and the same "one block per family" model the hatches and casings use.
     *
     * <p>A parallel controller does nothing on its own: it only counts when it stands at a position the machine
     * definition accepts, which is exactly how the original worked (only its {@code assembly_line} listed
     * {@code modularmachinery:blockparallelcontroller} as a structure element).
     */
    public static final RegistryObject<Block> PARALLEL_CONTROLLER = BLOCKS.register("parallel_controller",
            () -> new ParallelControllerBlock(BlockBehaviour.Properties.of().strength(5.0f, 10.0f)
                    .requiresCorrectToolForDrops()));

    /** One block item per tier, in enum order; 5 items. */
    public static final List<RegistryObject<Item>> PARALLEL_CONTROLLER_ITEMS;

    public static final RegistryObject<BlockEntityType<ParallelControllerBlockEntity>> PARALLEL_CONTROLLER_ENTITY =
            BLOCK_ENTITIES.register("parallel_controller", () -> BlockEntityType.Builder
                    .of(ParallelControllerBlockEntity::new, PARALLEL_CONTROLLER.get()).build(null));

    static {
        List<RegistryObject<Item>> parallelItems = new ArrayList<>();
        for (ParallelControllerTier tier : ParallelControllerTier.values()) {
            parallelItems.add(ITEMS.register(tier.itemId(),
                    () -> new ParallelControllerItem(PARALLEL_CONTROLLER.get(), tier, new Item.Properties())));
        }
        PARALLEL_CONTROLLER_ITEMS = List.copyOf(parallelItems);
    }

    // ------------------------------------------------------- M6b: upgrade bus

    /**
     * One block with a {@code type} property for all five upgrade-bus tiers — the original's
     * {@code BlockUpgradeBus} ({@code :38}), the same "one block per family" model as the hatches, casings and
     * the parallel controller.
     *
     * <p>A bus does nothing on its own either: it only counts when it stands at a position the machine
     * definition accepts, and its upgrades only apply to a machine whose name they are declared compatible
     * with. That is exactly the original's arrangement — its built-in machines did not list
     * {@code modularmachinery:blockupgradebus} at all.
     */
    public static final RegistryObject<Block> UPGRADE_BUS = BLOCKS.register("upgrade_bus",
            () -> new UpgradeBusBlock(BlockBehaviour.Properties.of().strength(5.0f, 10.0f)
                    .requiresCorrectToolForDrops()));

    /** One block item per tier, in enum order; 5 items. */
    public static final List<RegistryObject<Item>> UPGRADE_BUS_ITEMS;

    public static final RegistryObject<BlockEntityType<UpgradeBusBlockEntity>> UPGRADE_BUS_ENTITY =
            BLOCK_ENTITIES.register("upgrade_bus", () -> BlockEntityType.Builder
                    .of(UpgradeBusBlockEntity::new, UPGRADE_BUS.get()).build(null));

    static {
        List<RegistryObject<Item>> busItems = new ArrayList<>();
        for (UpgradeBusTier tier : UpgradeBusTier.values()) {
            busItems.add(ITEMS.register(tier.itemId(),
                    () -> new UpgradeBusItem(UPGRADE_BUS.get(), tier, new Item.Properties())));
        }
        UPGRADE_BUS_ITEMS = List.copyOf(busItems);
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.modular_machinery_reborn"))
            .icon(() -> new ItemStack(MACHINE_CONTROLLER_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(MACHINE_CONTROLLER_ITEM.get());
                output.accept(FACTORY_CONTROLLER_ITEM.get());
                BOUND_CONTROLLER_ITEMS.forEach(item -> output.accept(item.get()));
                FACTORY_CONTROLLER_ITEMS.forEach(item -> output.accept(item.get()));
                CASING_ITEMS.forEach(item -> output.accept(item.get()));
                HATCH_ITEMS.forEach(item -> output.accept(item.get()));
                PARALLEL_CONTROLLER_ITEMS.forEach(item -> output.accept(item.get()));
                UPGRADE_BUS_ITEMS.forEach(item -> output.accept(item.get()));
                // The original listed one blueprint per registered machine rather than one unbound item. When
                // no definitions are known here — a dedicated-server client never receives them — fall back to
                // a single empty blueprint so the item is still reachable.
                var blueprints = com.reborn.modularmachinery.machine.MachineRegistry.all();
                if (blueprints.isEmpty()) {
                    output.accept(ModItems.BLUEPRINT.get());
                } else {
                    for (var definition : blueprints) {
                        output.accept(com.reborn.modularmachinery.item.BlueprintItem.forMachine(
                                ModItems.BLUEPRINT.get(), definition.id()));
                    }
                }
                output.accept(ModItems.MODULARIUM.get());
                output.accept(ModItems.CONSTRUCT_TOOL.get());
                output.accept(ModItems.MACHINE_PROJECTOR.get());
                output.accept(ModItems.REDSTONE_SIGNAL.get());
                output.accept(ModItems.WRENCH.get());
            }).build());

    private static Block[] controllerBlocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(MACHINE_CONTROLLER.get());
        blocks.add(FACTORY_CONTROLLER.get());
        BOUND_CONTROLLER_BLOCKS.values().forEach(block -> blocks.add(block.get()));
        FACTORY_CONTROLLER_BLOCKS.values().forEach(block -> blocks.add(block.get()));
        return blocks.toArray(new Block[0]);
    }

    private static Block createHatchBlock(HatchKind kind) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops();
        return switch (kind) {
            case ITEM_INPUT, ITEM_OUTPUT -> new MachineHatchBlock.ItemPort(kind, properties);
            case FLUID_INPUT, FLUID_OUTPUT -> new MachineHatchBlock.FluidPort(kind, properties);
            case ENERGY_INPUT, ENERGY_OUTPUT -> new MachineHatchBlock.EnergyPort(kind, properties);
        };
    }

    /** The single block registered for a hatch family. */
    public static RegistryObject<Block> hatchBlock(HatchKind kind) {
        return HATCH_BLOCKS.get(kind);
    }

    /** Every hatch block item, in family then tier order. */
    public static List<RegistryObject<Item>> hatchItems() {
        return List.copyOf(HATCH_ITEMS);
    }

    /** The five parallel-controller block items, in tier order (normal … ultimate). */
    public static List<RegistryObject<Item>> parallelControllerItems() {
        return PARALLEL_CONTROLLER_ITEMS;
    }

    /** The five upgrade-bus block items, in tier order (normal … ultimate). */
    public static List<RegistryObject<Item>> upgradeBusItems() {
        return UPGRADE_BUS_ITEMS;
    }

    /** Every controller item: the generic one first, then the machine-bound ones. */
    public static List<RegistryObject<Item>> controllerItems() {
        List<RegistryObject<Item>> items = new ArrayList<>();
        items.add(MACHINE_CONTROLLER_ITEM);
        items.addAll(BOUND_CONTROLLER_ITEMS);
        return List.copyOf(items);
    }

    /** The bound controller block for a machine path, or {@code null} when that machine has none. */
    public static RegistryObject<Block> boundControllerBlock(String machinePath) {
        return BOUND_CONTROLLER_BLOCKS.get(machinePath);
    }

    /**
     * The controller item that belongs to a machine: the machine-bound one when the mod registered a block for it,
     * otherwise the generic {@code machine_controller}.
     *
     * <p>The original could always answer this with a per-machine controller, because a machine could not exist
     * without one. Here a machine may be declared by a data pack alone, and the generic controller genuinely can
     * form it, so that is the honest answer rather than "none".
     */
    public static Item controllerItemFor(ResourceLocation machineId) {
        for (int i = 0; i < BOUND_CONTROLLERS.size(); i++) {
            if (BOUND_CONTROLLERS.get(i).machineId().equals(machineId)) {
                return BOUND_CONTROLLER_ITEMS.get(i).get();
            }
        }
        return MACHINE_CONTROLLER_ITEM.get();
    }

    /**
     * Bound controllers whose blockstate and item model the mod does not ship. Since the mod ships no machines,
     * that is <b>all</b> of them. Their models are generated at runtime into a synthetic resource pack, the same
     * trick the original used when it wrote {@code blockstates/block_machine_controller.json} for every machine
     * it registered.
     *
     * <p>M6e adds the per-declaration factory controllers to that list for exactly the same reason: their names
     * are the author's, so no file in the jar can describe them.
     */
    public static List<MachineDirectory.MachineRef> controllersNeedingGeneratedAssets() {
        List<MachineDirectory.MachineRef> all = new ArrayList<>(BOUND_CONTROLLERS);
        for (MachineDirectory.MachineRef ref : FACTORY_CONTROLLERS) {
            if (!all.contains(ref)) {
                all.add(ref);
            }
        }
        return List.copyOf(all);
    }

    /** Every factory controller item: the generic one first, then the machine-bound ones. */
    public static List<RegistryObject<Item>> factoryControllerItems() {
        List<RegistryObject<Item>> items = new ArrayList<>();
        items.add(FACTORY_CONTROLLER_ITEM);
        items.addAll(FACTORY_CONTROLLER_ITEMS);
        return List.copyOf(items);
    }

    /** The factory controller block for a machine path, or {@code null} when that machine has none. */
    public static RegistryObject<Block> factoryControllerBlock(String machinePath) {
        return FACTORY_CONTROLLER_BLOCKS.get(machinePath);
    }

    /** Whether a factory controller block was registered for this machine, i.e. its declaration asked for one. */
    public static boolean hasFactoryController(ResourceLocation machineId) {
        for (MachineDirectory.MachineRef ref : FACTORY_CONTROLLERS) {
            if (ref.machineId().equals(machineId)) {
                return true;
            }
        }
        return false;
    }

    private ModBlocks() {
    }
}
