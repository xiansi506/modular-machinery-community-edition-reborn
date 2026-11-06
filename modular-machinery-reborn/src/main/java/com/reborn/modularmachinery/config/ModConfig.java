package com.reborn.modularmachinery.config;

import com.reborn.modularmachinery.block.ParallelControllerTier;
import com.reborn.modularmachinery.block.UpgradeBusTier;
import com.reborn.modularmachinery.machine.StructureCheckScheduler;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mod's <b>single</b> Forge config, published as {@code config/modular_machinery_reborn-common.toml}.
 *
 * <h2>Why exactly one spec</h2>
 * Forge's {@code ConfigTracker} keys its file map on the config <i>file name</i>
 * ({@code ConfigTracker#trackConfig}, {@code fmlcore-1.20.1-47.2.0.jar}: the bytecode reads
 * {@code ModConfig#getFileName()} and throws when the key is already present), and every {@code COMMON} config of a
 * mod is named {@code <modid>-common.toml}. A second {@code registerConfig(COMMON, ...)} therefore lands on the
 * same key and Forge throws {@code RuntimeException("Config conflict detected!")} from the mod constructor. 0.21.0
 * did exactly that — a first config class registered the parallel-controller spec and a second one, added for the
 * upgrade bus, registered another — and the game died at construction.
 *
 * <p>Both key sets consequently live in the one spec below, and the one registration lives in {@link #register()}.
 * Adding a config section means adding a block to {@link #build()} — <b>never</b> a second call to
 * {@code ModLoadingContext.registerConfig}.
 *
 * <h2>The key paths</h2>
 * These are the original 1.12.2 mod's own key names, so they are a compatibility surface and must not be renamed:
 * <ul>
 *   <li>{@code parallel-controller.<tier>.max-parallelism} — defaults 4/16/64/256/512, range
 *       {@code 1 .. Integer.MAX_VALUE} ({@code ParallelControllerData#loadFromConfig}, {@code :23-29}).</li>
 *   <li>{@code upgrade-bus.<tier>.max-upgrade_slot} — defaults 3/6/9/12/18, range {@code 1 .. 18}, the underscore
 *       in {@code max-upgrade_slot} included ({@code UpgradeBusData#loadFromConfig}, {@code :23-29}). The bound of
 *       18 is the original's own clamp.</li>
 *   <li>{@code fluidhatch.size.<TIER>}, {@code energyhatch.size.<TIER>} and {@code energyhatch.limit.<TIER>} — the
 *       original's hatch keys, kept in the original's categories and with the enum constant as the upper-case key
 *       ({@code FluidHatchSize#loadFromConfig}, {@code :43-47}; {@code EnergyHatchData#loadFromConfig},
 *       {@code :67-85}). Defaults come from {@code HatchTier}; energy storage and transfer use the original long ranges. See {@link HatchConfig}.</li>
 *   <li>{@code factory-system.enable-factory-controller-bydefault} — default {@code false}
 *       ({@code Config.java:41} field initialiser and {@code :120-121} the config default);
 *       {@code factory-system.default-factory-max-thread} — default <b>10</b>, range {@code 1 .. 100}
 *       ({@code Config.java:122-124}). These two are the original's own key names inside its
 *       {@code factory-system} section, kept verbatim because the class comment above calls the key names a
 *       compatibility surface. See M6e-2.</li>
 *   <li>{@code smart-interface.enable-smart-interface-bydefault} — default {@code false}, M6d-b's single key.
 *       The original had no config entry for the smart data interface at all: its types were registered by a
 *       CraftTweaker script and a machine without one simply had none. This key exists only to make that same
 *       answer configurable for the message {@code MachineLoader} prints, mirroring
 *       {@code factory-system.enable-factory-controller-bydefault}. See D16.</li>
 * </ul>
 * Only the file's root name differs from the original's {@code modularmachinery.cfg}, because this project uses the
 * Forge default name for its mod id. Both sets are named in {@code 移植方案-v2.md} (the M6d and M6b entries) and in
 * the {@code 迁移日志.md} entries for 0.20.0 and 0.21.0.
 *
 * <h2>Why the paths are passed as lists</h2>
 * {@code ForgeConfigSpec.Builder} is <b>path-accumulating</b>: {@code push(x)} appends to a running context that
 * {@code pop()} does not restore to the caller's starting point, which is why 0.20.0's loop produced
 * {@code parallel-controller.normal.max-parallelism} for the first tier but
 * {@code parallel-controller.parallel-controller.reinforced.max-parallelism} for the second, and ever longer
 * duplications after that (the 0.20.0 jar's own generated file, kept at
 * {@code D:\.minecraft\versions\彩虹花园\config\modular_machinery_reborn-common.toml}, shows the nesting). Every
 * key here is therefore defined with an explicit path list, so each definition is independent and cannot inherit a
 * neighbour's prefix. The tier enums themselves stay in code: a block's variants have to exist when blocks are
 * registered, and 1.20.1 forbids registering them later (D8). Only the numbers move to the config.
 */
public final class ModConfig {

    /**
     * The one and only spec for this mod. It carries both sections; see the class comment for why there cannot be
     * a second spec of the same type.
     */
    public static final ForgeConfigSpec SPEC;

    /** The original's upper bound on an upgrade-bus slot count ({@code UpgradeBusData.java:26}). */
    public static final int MAX_UPGRADE_SLOTS_LIMIT = 18;

    /**
     * The original's upper bound on the factory system's default thread count
     * ({@code Config.java:122-124}, the {@code getInt} call's max), and its lower bound.
     */
    public static final int DEFAULT_FACTORY_MAX_THREAD_LIMIT = 100;
    public static final int DEFAULT_FACTORY_MAX_THREAD_FLOOR = 1;

    /**
     * The value used when {@link #factoryDefaultMaxThread()} is asked before the config file has been read —
     * the original's own config-file default ({@code Config.java:122-124} passes 10 to {@code getInt}).
     *
     * <p><b>Honest note on 10 versus 20.</b> The original's Java field is initialised to {@code 20}
     * ({@code Config.java:47}), but {@code Config.load()} unconditionally overwrites it with the config read,
     * whose default is {@code 10}. Whenever the original ran with a config file — always, in practice — the
     * effective default was therefore 10, and 20 was only ever visible in the window between class
     * initialisation and {@code loadFrom(…)}. This projection keeps the <i>observable</i> value.
     * M6e-1's {@code DEFAULT_MAX_THREADS = 20} was read off the field initialiser and is corrected here.
     * A pre-existing {@code config/modular_machinery_reborn-common.toml} is not invalidated: this is a new
     * section, so Forge simply appends it with the default.
     */
    private static final int FACTORY_MAX_THREAD_BEFORE_CONFIG = 10;

    private static final String PARALLEL_ROOT = "parallel-controller";
    private static final String PARALLEL_VALUE = "max-parallelism";
    private static final String UPGRADE_ROOT = "upgrade-bus";
    private static final String UPGRADE_VALUE = "max-upgrade_slot";
    private static final String FACTORY_ROOT = "factory-system";
    private static final String FACTORY_MAX_THREAD = "default-factory-max-thread";
    private static final String FACTORY_ENABLED = "enable-factory-controller-bydefault";
    private static final String SMART_INTERFACE_ROOT = "smart-interface";
    private static final String SMART_INTERFACE_ENABLED = "enable-smart-interface-bydefault";
    private static final String GENERAL_ROOT = "general";
    private static final String MOC_COMPATIBLE = "modular-controller-compatible-mode";
    private static final String MOC_DEPRECATED_TIP = "disable-moc-deprecated-tip";
    private static final String STRUCTURE_CHECK_DELAY = "structure-check-delay";
    private static final String DELAYED_STRUCTURE_CHECK = "delayed-structure-check";
    private static final String MAX_STRUCTURE_CHECK_DELAY = "max-structure-check-delay";
    /** The original's full-NBT switch: general.enable-full-data-sync. */
    private static final String ENABLE_FULL_DATA_SYNC = "enable-full-data-sync";
    /** The original's owner check switch: general.enable-security-system. */
    private static final String ENABLE_SECURITY_SYSTEM = "enable-security-system";
    /** The original's own category and key spelling: {@code display.energy.Display_Energy_Type}. */
    private static final String DISPLAY_ENERGY_ROOT = "display.energy";
    private static final String DISPLAY_ENERGY_KEY = "Display_Energy_Type";
    /** The original's duration-multiplier switch: general.enable-duration-multiplier. */
    private static final String ENABLE_DURATION_MULTIPLIER = "enable-duration-multiplier";
    /** The original machine defaults in the general category. */
    private static final String GENERAL_CASING_COLOR = "general-casing-color";
    private static final String MACHINE_PARALLELIZE_DEFAULT = "machine-parallelize-enabled-bydefault";
    private static final String MAX_MACHINE_PARALLELISM = "max-machine-parallelism";
    /** The original's auto-assembly category: ink/ikx/mmce AssemblyConfig "auto-assembly". */
    private static final String ASSEMBLY_ROOT = "auto-assembly";
    private static final String ASSEMBLY_ITEM = "itemName";
    private static final String ASSEMBLY_TICK = "tickBlock";
    private static final String ASSEMBLY_NEED_ALL = "needAllBlocks";

    private static final Map<ParallelControllerTier, ForgeConfigSpec.IntValue> PARALLELISM =
            new EnumMap<>(ParallelControllerTier.class);

    private static final Map<UpgradeBusTier, ForgeConfigSpec.IntValue> UPGRADE_SLOTS =
            new EnumMap<>(UpgradeBusTier.class);

    /**
     * {@code display.energy.Display_Energy_Type} — the unit every energy number is <b>printed</b> in.
     *
     * <p>Stored as a string rather than an enum so the file's own spelling is the four names the original used
     * ({@code FE} / {@code RF} / {@code IC2_EU} / {@code GT_EU}) and a typo is reported rather than crashing;
     * {@link EnergyDisplay#parse} does the tolerant read, exactly as the original's {@code EnergyType.getType}
     * did. Same reasoning as {@link #FACTORY_MAX_THREADS} for not being {@code final}: it is assigned in
     * {@link #build()}.
     */
    private static ForgeConfigSpec.ConfigValue<String> DISPLAY_ENERGY_TYPE;

    /**
     * {@code factory-system.default-factory-max-thread}.
     *
     * <p>Not {@code final}, unlike nothing else in this class, because it is assigned inside {@link #build()} —
     * a blank {@code final} static may only be assigned from a static initialiser or the class body, and
     * {@code build()} is neither. It is written exactly once, from the one static initialiser that calls
     * {@code build()}, and never afterwards.
     */
    private static ForgeConfigSpec.IntValue FACTORY_MAX_THREADS;

    // ---- auto-assembly.* (A8, 0.106.0) -------------------------------------------------------------
    private static ForgeConfigSpec.ConfigValue<String> ASSEMBLY_ITEM_NAME;
    private static ForgeConfigSpec.IntValue ASSEMBLY_TICK_BLOCK;
    private static ForgeConfigSpec.BooleanValue ASSEMBLY_NEED_ALL_BLOCKS;

    /**
     * {@code factory-system.enable-factory-controller-bydefault}. Same reasoning as
     * {@link #FACTORY_MAX_THREADS}.
     */
    private static ForgeConfigSpec.BooleanValue FACTORY_ENABLED_BY_DEFAULT;
    /**
     * {@code smart-interface.enable-smart-interface-bydefault}. Same reasoning as
     * {@link #FACTORY_MAX_THREADS}: a blank {@code final} cannot be assigned from {@code build()}.
     */
    private static ForgeConfigSpec.BooleanValue SMART_INTERFACE_ENABLED_BY_DEFAULT;
    /**
     * {@code general.modular-controller-compatible-mode}. Same reasoning as {@link #FACTORY_MAX_THREADS}.
     *
     * <p><b>Inert on 1.20.1, deliberately and visibly.</b> See {@link #mocCompatibleMode()}.
     */
    private static ForgeConfigSpec.BooleanValue MOC_COMPATIBLE_MODE;
    /**
     * {@code general.disable-moc-deprecated-tip}. Same reasoning as {@link #FACTORY_MAX_THREADS}.
     */
    private static ForgeConfigSpec.BooleanValue DISABLE_MOC_DEPRECATED_TIP;
    /** general.structure-check-delay. Assigned in build(), hence not final. */
    private static ForgeConfigSpec.IntValue STRUCTURE_CHECK_DELAY_VALUE;
    /** general.delayed-structure-check. Assigned in build(). */
    private static ForgeConfigSpec.BooleanValue DELAYED_STRUCTURE_CHECK_VALUE;
    /** general.max-structure-check-delay. Assigned in build(). */
    private static ForgeConfigSpec.IntValue MAX_STRUCTURE_CHECK_DELAY_VALUE;
    /** general.enable-full-data-sync. Assigned in build(). */
    private static ForgeConfigSpec.BooleanValue ENABLE_FULL_DATA_SYNC_VALUE;
    /** general.enable-security-system. Assigned in build(). */
    private static ForgeConfigSpec.BooleanValue ENABLE_SECURITY_SYSTEM_VALUE;
    /** general.enable-duration-multiplier. Assigned in build(). */
    private static ForgeConfigSpec.BooleanValue ENABLE_DURATION_MULTIPLIER_VALUE;
    private static ForgeConfigSpec.BooleanValue ENABLE_FLUXNETWORKS_INTEGRATION;
    /** general.general-casing-color, stored as the original six digit RGB string. */
    private static ForgeConfigSpec.ConfigValue<String> GENERAL_CASING_COLOR_VALUE;
    /** general.machine-parallelize-enabled-bydefault. */
    private static ForgeConfigSpec.BooleanValue MACHINE_PARALLELIZE_DEFAULT_VALUE;
    /** general.max-machine-parallelism. */
    private static ForgeConfigSpec.IntValue MAX_MACHINE_PARALLELISM_VALUE;
    /**
     * The original warned when structure-check-delay >= max-structure-check-delay and then reset both values
     * (TileMultiblockMachineController.java:184-188). The normalized scheduler applies the same reset; this
     * flag keeps the log line to one occurrence rather than one per controller tick.
     */
    private static final AtomicBoolean WARNED_ABOUT_STRUCTURE_CHECK = new AtomicBoolean(false);

    static {
        SPEC = build();
    }

    /**
     * Builds the one spec: both key sets, in one builder, with explicit per-key paths.
     *
     * <p>Do not call {@code build()} twice and do not hand the builder's output to a second
     * {@code registerConfig}: one spec per type per mod is a Forge invariant, not a style choice.
     */
    private static ForgeConfigSpec build() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ---- Section 1 of 2: parallel-controller.<tier>.max-parallelism -------------------------------
        // The comment is set once for the section root: Forge attaches a comment to the path it is handed, and a
        // comment handed the full key path would also be written above the value as a repeated "#<path>" line.
        builder.comment("Defines the max parallelism for the parallel controller.");
        for (ParallelControllerTier tier : ParallelControllerTier.values()) {
            List<String> path = Arrays.asList(PARALLEL_ROOT, tier.tierName(), PARALLEL_VALUE);
            PARALLELISM.put(tier, builder.defineInRange(path, tier.defaultMaxParallelism(),
                    1, Integer.MAX_VALUE));
        }

        // ---- Section 2 of 2: upgrade-bus.<tier>.max-upgrade_slot --------------------------------------
        // This is a block in the same builder, not a spec of its own: a second ForgeConfigSpec would mean a second
        // COMMON registration, which is the startup crash described in the class comment.
        builder.comment("Defines the max upgrade slot for the upgrade bus.");
        for (UpgradeBusTier tier : UpgradeBusTier.values()) {
            List<String> path = Arrays.asList(UPGRADE_ROOT, tier.tierName(), UPGRADE_VALUE);
            UPGRADE_SLOTS.put(tier, builder.defineInRange(path, tier.defaultMaxUpgradeSlots(),
                    1, MAX_UPGRADE_SLOTS_LIMIT));
        }

        // ---- Section 3 of 3: factory-system.* ----------------------------------------------------------
        // The two keys M6e-2 adds, again with explicit path lists. Both are flat keys inside one section
        // (`factory-system`), which is how the original's own config file looked: its `getBoolean` /
        // `getInt` calls named the category "factory-system" with the key directly under it, so no second
        // path element is involved — and no loop, so the path-accumulation hazard described above cannot
        // apply here at all.
        builder.comment("Defines the factory system's defaults.");
        FACTORY_MAX_THREADS = builder.defineInRange(
                List.of(FACTORY_ROOT, FACTORY_MAX_THREAD), FACTORY_MAX_THREAD_BEFORE_CONFIG,
                DEFAULT_FACTORY_MAX_THREAD_FLOOR, DEFAULT_FACTORY_MAX_THREAD_LIMIT);
        FACTORY_ENABLED_BY_DEFAULT = builder.define(
                List.of(FACTORY_ROOT, FACTORY_ENABLED), false);

        // ---- Section 4 of 4: smart-interface.* ---------------------------------------------------------
        // M6d-b's one key, again a literal path list, again no loop. It is a *message* switch and nothing
        // else: whether a machine may use a smart interface is decided by its own `smart-interfaces`
        // declaration, exactly as the original decided it by DynamicMachine#smartInterfaceTypesIsEmpty. The
        // key exists because that is the one thing about this feature a server operator might want to hear
        // less about, mirroring factory-system.enable-factory-controller-bydefault.
        builder.comment("Defines the smart data interface's defaults.");
        SMART_INTERFACE_ENABLED_BY_DEFAULT = builder.define(
                List.of(SMART_INTERFACE_ROOT, SMART_INTERFACE_ENABLED), false);

        // ---- Section 5 of 5: general.* — the ModularController compatibility keys ---------------------
        // The original's own two keys, in the original's own section ("general") and with the original's own
        // defaults (Config.java:100-105). Both are compatibility surfaces: a pack author's existing
        // modularmachinery.cfg, or a reader of the original's documentation, expects exactly these names.
        //
        // The first one's comment is the original's sentence with its promise CROSSED OUT, because the promise
        // cannot be kept here and an inert key that lies is worse than no key: on 1.20.1 a block id has to exist
        // before Forge has read any config file (RegisterEvent runs in the LOAD_REGISTRIES state, CONFIG_LOAD
        // after it — see MocNamespace), so nothing can gate registration on this value. See
        // mocCompatibleMode() for what the projection does instead.
        builder.comment("ModularController merge support (the original's `modular-controller-compatible-mode`).",
                "INERT ON 1.20.1: this key cannot gate anything. The original registered the compatibility blocks",
                "when it was true, but Forge fires the block registry event (LOAD_REGISTRIES) before it has read",
                "any config file (CONFIG_LOAD), so a block id must already exist by then — and a save is loaded",
                "by block id, which means a world holding such a block cannot be opened in order to read this",
                "key. The `modularcontroller` controllers are therefore always registered for every machine",
                "declared in config/modular_machinery_reborn/machinery/ (skip: factory-only). Their items are not",
                "put in the creative tab: they exist so an old save keeps working, not to be handed out. The key",
                "is kept only because the original had it.");
        MOC_COMPATIBLE_MODE = builder.define(List.of(GENERAL_ROOT, MOC_COMPATIBLE), false);
        builder.comment("Disable the ModularController is deprecated tooltip (the original's",
                "`disable-moc-deprecated-tip`). Live: a tooltip is built long after the config has been read.");
        DISABLE_MOC_DEPRECATED_TIP = builder.define(List.of(GENERAL_ROOT, MOC_DEPRECATED_TIP), false);

        // ---- general.*: the original's structure-check schedule (A10) ---------------------------------
        // The original read these three in loadFromConfig (TileMultiblockMachineController.java:171-189), in
        // the "general" category, with exactly these names. The defaults and bounds are the original's own:
        // 30 (1..1200), true, and 100 (2..1200). The numbers were hard-wired to STRUCTURE_CHECK_INTERVAL = 10
        // in 0.66.0 and earlier; this is the config surface the survey (A10) named as missing.
        //
        // The interaction rule is also the original's: structure-check-delay must be smaller than
        // max-structure-check-delay, or both are reset to 30/100 and the reset is logged once. The scheduler
        // applies the same rule so a value pasted from a 1.12.2 config cannot stall every check.
        builder.comment("Ticks between structure checks once a controller has formed (the original's",
                "structure-check-delay). Default 30, range 1..1200. Must be smaller than",
                "max-structure-check-delay; otherwise both fall back to 30/100.");
        STRUCTURE_CHECK_DELAY_VALUE = builder.defineInRange(
                List.of(GENERAL_ROOT, STRUCTURE_CHECK_DELAY),
                StructureCheckScheduler.DEFAULT_DELAY, 1, 1200);
        builder.comment("Keep the original's adaptive delay (the original's delayed-structure-check).",
                "When false, a controller checks every structure-check-delay ticks exactly, with no",
                "area-change shortcut and no backoff. Default true.");
        DELAYED_STRUCTURE_CHECK_VALUE = builder.define(
                List.of(GENERAL_ROOT, DELAYED_STRUCTURE_CHECK),
                StructureCheckScheduler.DEFAULT_DELAYED);
        builder.comment("Ceiling for the adaptive delay (the original's max-structure-check-delay).",
                "Default 100, range 2..1200. Must be greater than structure-check-delay.");
        MAX_STRUCTURE_CHECK_DELAY_VALUE = builder.defineInRange(
                List.of(GENERAL_ROOT, MAX_STRUCTURE_CHECK_DELAY),
                StructureCheckScheduler.DEFAULT_MAX_DELAY, 2, 1200);

        // ---- general.*: the original's full-NBT switch (A12) -----------------------------------------
        // The original read this in loadFromConfig (TileMultiblockMachineController.java:193-198), in the
        // "general" category, default false. When true, a recipe start or finish calls markForUpdateSync()
        // (TileMachineController.java:265-275/299-306, TileFactoryController.java:274-284/304-311), which
        // sends the whole tile-entity NBT; when false the original only marked the chunk dirty. This port's
        // getUpdateTag() is already the whole saved state, so the switch only decides whether to send it.
        builder.comment("Send the controller's full NBT to the client when a recipe starts and finishes",
                "(the original's enable-full-data-sync). Default false. A controller otherwise only sends",
                "a full block-entity update when its structure, failure text, colour or threads change.");
        ENABLE_FULL_DATA_SYNC_VALUE = builder.define(
                List.of(GENERAL_ROOT, ENABLE_FULL_DATA_SYNC), false);

        // ---- general.*: the original's owner check (A11) ----------------------------------------------
        // The original read this in loadFromConfig (TileMultiblockMachineController.java:195-196), in the
        // "general" category, default false. Despite its comment promising to deny "players using the
        // controller", the code only enforced it in two places: breaking a controller (EventHandler#onBlockBroken)
        // and placing one whose item carried another player's owner tag (EventHandler#onBlockPlaced). This port
        // keeps exactly those two gates; opening the screen and running the machine stay unrestricted.
        builder.comment("Only the controller's owner may break it, and only an owner may place a controller that",
                "carries another player's owner tag (the original's enable-security-system). Default false.",
                "The owner is always recorded on placement, whether this is on or off.");
        ENABLE_SECURITY_SYSTEM_VALUE = builder.define(
                List.of(GENERAL_ROOT, ENABLE_SECURITY_SYSTEM), false);

        // ---- general.*: the original's duration-multiplier switch (B11) --------------------------------
        // The original's Java field says true (Config.java:44), but load() always overwrites it from
        // getBoolean(..., false, ...) (Config.java:92), so the observable default is false. The switch only
        // affects per-tick energy: RecipeCraftingContext#getDurationMultiplier returns 1f while it is off
        // (RecipeCraftingContext.java:196-199), and the recipe's duration is reduced either way. This port had
        // no switch before 0.77.0 and always applied the compensation; see RecipeModifiers#durationMultiplier.
        builder.comment("Scale per-tick energy and already-supported per-tick magic resources when duration changes",
                "(the original's enable-duration-multiplier). Default false, the value the original's",
                "config file actually used. When false, a duration modifier still changes how many ticks",
                "the craft takes, but per-tick energy/magic costs are not compensated. Integer magic rounds",
                "once after copies and compensation, with a minimum of one for a positive total; explicit zero stays zero.");
        ENABLE_FLUXNETWORKS_INTEGRATION = builder.comment("Expose Flux Networks long-energy capabilities. Requires a world restart.")
                .worldRestart().define(List.of(GENERAL_ROOT, "enable-fluxnetworks-integration"), true);
        ENABLE_DURATION_MULTIPLIER_VALUE = builder.define(
                List.of(GENERAL_ROOT, ENABLE_DURATION_MULTIPLIER), false);

        // ---- general.*: machine defaults -------------------------------------------------------------
        // These are the three F1 entries whose defaults and consumers are established in the port: color is
        // FFFFFF (MachineDefinition.java's existing default), parallelizable is true (its existing default),
        // and max parallelism is 2048 (the existing constant, matching Config.maxMachineParallelism).
        // The other four F1 keys are intentionally not declared here: one is known to collide with early block
        // registration, and the remaining integration/render/recipe semantics do not yet have proven consumers.
        // An inert toggle would misrepresent the feature as migrated.
        builder.comment("Default RGB casing tint for machine definitions without an explicit 'color'.",
                "Six hexadecimal digits, without '#'. Default FFFFFF means no tint.");
        GENERAL_CASING_COLOR_VALUE = builder.define(List.of(GENERAL_ROOT, GENERAL_CASING_COLOR), "FFFFFF",
                value -> value instanceof String text && text.matches("[0-9a-fA-F]{6}"));
        builder.comment("Whether machine definitions without an explicit 'parallelizable' field may run in parallel.",
                "Default true, matching the original machine default.");
        MACHINE_PARALLELIZE_DEFAULT_VALUE = builder.define(
                List.of(GENERAL_ROOT, MACHINE_PARALLELIZE_DEFAULT), true);
        builder.comment("Default maximum parallelism for machine definitions without 'max-parallelism'.",
                "Default 2048, matching the original Config.maxMachineParallelism.");
        MAX_MACHINE_PARALLELISM_VALUE = builder.defineInRange(
                List.of(GENERAL_ROOT, MAX_MACHINE_PARALLELISM), 2048, 0, Integer.MAX_VALUE);

        // ---- Section 6 of 6: display.energy.Display_Energy_Type ---------------------------------------
        // The original's key, in the original's own category and with the original's own spelling and casing
        // (`EnergyDisplayUtil.loadFromConfig`: cfg.getString("Display_Energy_Type", "display.energy", …)), so a
        // pack author's existing modularmachinery.cfg line is understood as written.
        //
        // The comment states the two things a reader must not get wrong: this is a *display* choice, and the
        // stored numbers never change. The original's own description promised "energy be displayed in that type
        // of energy", which reads like a conversion; its implementation multiplied only for printing, and saying
        // so here is the one place this departs from the original's wording.
        builder.comment("The unit every energy number is displayed in (the original's `display.energy`).",
                "Available options: " + EnergyDisplay.vocabulary() + " - Default: 'FE'.",
                "DISPLAY ONLY: no stored value is converted. A hatch holding 8192 FE prints as '2048 EU' with",
                "'IC2_EU' selected and still holds 8192 FE, which is what the original did as well — it scaled the",
                "number it printed and nothing else. 'RF' is 1:1, i.e. a label rather than a conversion.",
                "The original's two tooltip switches (IC2_EU_Tooltip / GT_EU_Tooltip) are not implemented: each",
                "only had an effect with that mod installed, and neither IC2 (absent on 1.20.1) nor GregTech is a",
                "dependency of this mod, so both were no-ops here.");
        DISPLAY_ENERGY_TYPE = builder.define(List.of(DISPLAY_ENERGY_ROOT, DISPLAY_ENERGY_KEY),
                EnergyDisplay.FE.getSerializedName());

        // ---- Hatch capacities and transfer rates (A4) ------------------------------------------------
        // The original read these in three loops over its three hatch enums; HatchConfig owns the maps and the
        // accessors, and this call is the only place they are attached to the one spec above. Not a separate
        // spec: a second COMMON registration is the startup crash the class comment describes.
        HatchConfig.define(builder);
        com.reborn.modularmachinery.magic.MagicConfig.define(builder);

        // ---- Section 6 of 6: auto-assembly.* (A8) ------------------------------------------------------
        // The original's AssemblyConfig ("auto-assembly"), with the two keys that have no 1.20.1 meaning left
        // out: `itemMeta` (metadata does not exist here) and `skipBlockContainNBT` (the pattern's
        // BlockMatcher carries no tile data, so an ingredient's NBT is always empty and the switch would
        // never trigger).
        builder.comment("Defines the auto-assembly defaults (the original's AssemblyConfig).");
        ASSEMBLY_ITEM_NAME = builder.define(
                List.of(ASSEMBLY_ROOT, ASSEMBLY_ITEM), "minecraft:stick");
        ASSEMBLY_TICK_BLOCK = builder.defineInRange(
                List.of(ASSEMBLY_ROOT, ASSEMBLY_TICK), 5, 1, 1000);
        ASSEMBLY_NEED_ALL_BLOCKS = builder.define(
                List.of(ASSEMBLY_ROOT, ASSEMBLY_NEED_ALL), true);

        return builder.build();
    }

    /**
     * Registers {@link #SPEC} — the mod's only config registration.
     *
     * <p><b>Do not add a second call to {@code ModLoadingContext.registerConfig} anywhere.</b> Forge allows one
     * config per type per mod, and a second {@code COMMON} registration is a hard crash at mod construction
     * ("Config conflict detected!"), not a warning. New sections go into {@link #build()} instead.
     */
    public static void register() {
        net.minecraftforge.fml.ModLoadingContext.get()
                .registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, SPEC);
    }

    /**
     * The ceiling a parallel controller of this tier contributes. Falls back to the original's hardcoded default
     * while the config file has not been read yet, so a block entity built before that moment cannot end up with a
     * ceiling of {@code 0}.
     */
    public static int maxParallelism(ParallelControllerTier tier) {
        ForgeConfigSpec.IntValue value = PARALLELISM.get(tier);
        if (value == null || !SPEC.isLoaded()) {
            return tier.defaultMaxParallelism();
        }
        return value.get();
    }

    // ---- auto-assembly.* (A8, 0.106.0) ---------------------------------------------------------------

    /** The registry id of the item that triggers auto-assembly when used on a controller (default stick). */
    public static net.minecraft.resources.ResourceLocation assemblyTriggerItem() {
        String value = ASSEMBLY_ITEM_NAME == null || !SPEC.isLoaded()
                ? "minecraft:stick" : ASSEMBLY_ITEM_NAME.get();
        net.minecraft.resources.ResourceLocation parsed =
                net.minecraft.resources.ResourceLocation.tryParse(value);
        // An unparseable or unknown id would silently disable the trigger; falling back to the stick keeps the
        // original's own "getByNameOrId or STICK" fallback (AssemblyEventHandler:140, :186-189).
        if (parsed == null || !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(parsed)) {
            return new net.minecraft.resources.ResourceLocation("minecraft", "stick");
        }
        return parsed;
    }

    /** Ticks between two placed blocks while an assembly is running (the original's {@code tickBlock}). */
    public static int assemblyTickBlock() {
        return ASSEMBLY_TICK_BLOCK == null || !SPEC.isLoaded() ? 5 : ASSEMBLY_TICK_BLOCK.get();
    }

    /** Whether assembly refuses to start unless the player carries every ingredient (the original's key). */
    public static boolean assemblyNeedAllBlocks() {
        return ASSEMBLY_NEED_ALL_BLOCKS == null || !SPEC.isLoaded() || ASSEMBLY_NEED_ALL_BLOCKS.get();
    }

    /** The RGB tint assigned to a machine definition that omits its {@code color} field. */
    public static int generalCasingColor() {
        String fallback = "FFFFFF";
        String value = GENERAL_CASING_COLOR_VALUE == null || !SPEC.isLoaded()
                ? fallback : GENERAL_CASING_COLOR_VALUE.get();
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            // The spec validator rejects malformed persisted values. Keep the runtime fallback defensive too.
            return 0xFFFFFF;
        }
    }

    /** Default for {@code parallelizable} when a machine definition does not specify it. */
    public static boolean machineParallelizeEnabledByDefault() {
        return MACHINE_PARALLELIZE_DEFAULT_VALUE == null || !SPEC.isLoaded()
                ? true : MACHINE_PARALLELIZE_DEFAULT_VALUE.get();
    }

    /** Default for {@code max-parallelism} when a machine definition does not specify it. */
    public static int maxMachineParallelism() {
        return MAX_MACHINE_PARALLELISM_VALUE == null || !SPEC.isLoaded()
                ? 2048 : MAX_MACHINE_PARALLELISM_VALUE.get();
    }

    /**
     * The slots a bus of this tier provides. Falls back to the original's hardcoded default while the config file
     * has not been read yet, so a block entity built before that moment cannot end up with zero slots.
     */
    public static int maxUpgradeSlots(UpgradeBusTier tier) {
        ForgeConfigSpec.IntValue value = UPGRADE_SLOTS.get(tier);
        if (value == null || !SPEC.isLoaded()) {
            return tier.defaultMaxUpgradeSlots();
        }
        return value.get();
    }

    /**
     * {@code factory-system.default-factory-max-thread}: how many ordinary threads a factory controller may hold
     * when the machine's definition does not say — the original's {@code Config.defaultFactoryMaxThread}
     * ({@code Config.java:122-124}), which {@code AbstractMachine.maxThreads} was initialised from
     * ({@code AbstractMachine.java:24}).
     *
     * <p>Falls back to the original's own config default while the file has not been read yet, so a machine
     * definition loaded before that moment cannot end up with a thread count of {@code 0}.
     */
    public static int factoryDefaultMaxThread() {
        if (FACTORY_MAX_THREADS == null || !SPEC.isLoaded()) {
            return FACTORY_MAX_THREAD_BEFORE_CONFIG;
        }
        return FACTORY_MAX_THREADS.get();
    }

    /**
     * {@code display.energy.Display_Energy_Type}: the unit every energy number is printed in.
     *
     * <p>Read fresh on each call rather than cached, because the value is only ever consulted while a tooltip or a
     * GUI line is being built — long after {@code CONFIG_LOAD} — so the file's value is what the player sees and a
     * cached copy would only add a way for the two to disagree.
     *
     * <p>A value that names none of the four units falls back to {@link EnergyDisplay#FE} and is <b>reported
     * once</b>: the original's own parser swallowed the failure silently, which leaves a typo in the config
     * looking like the setting simply does not work.
     */
    public static EnergyDisplay energyDisplay() {
        if (DISPLAY_ENERGY_TYPE == null || !SPEC.isLoaded()) {
            return EnergyDisplay.FE;
        }
        String raw = DISPLAY_ENERGY_TYPE.get();
        EnergyDisplay parsed = EnergyDisplay.find(raw);
        if (parsed == null) {
            if (WARNED_ABOUT_ENERGY_TYPE.add(String.valueOf(raw))) {
                com.reborn.modularmachinery.ModularMachineryReborn.LOGGER.warn(
                        "[{}] {}.{} is '{}', which is not one of {}; displaying energy as '{}' instead. The value"
                                + " is a display choice only — no stored amount is converted.",
                        com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, DISPLAY_ENERGY_ROOT,
                        DISPLAY_ENERGY_KEY, raw, EnergyDisplay.vocabulary(),
                        EnergyDisplay.FE.getSerializedName());
            }
            return EnergyDisplay.FE;
        }
        return parsed;
    }

    /**
     * Values already complained about, so a per-frame caller reports once instead of flooding the log.
     *
     * <p>Keyed by the offending string, which means a corrected value that is then broken again is reported again
     * — the message is about what the file says now, not about how many times it has been read.
     */
    private static final Set<String> WARNED_ABOUT_ENERGY_TYPE = ConcurrentHashMap.newKeySet();

    /**
     * {@code factory-system.enable-factory-controller-bydefault}: whether the machine definition layer should
     * assume a machine has a factory when it does not say so — the original's
     * {@code Config.enableFactoryControllerByDefault} ({@code Config.java:41}, {@code :120-121}), which
     * {@code AbstractMachine.hasFactory} was initialised from ({@code AbstractMachine.java:27}).
     *
     * <p>It defaults to {@code false}, exactly as the original's field and config default do, so the projection's
     * behaviour with an untouched config is unchanged from M6e-1.
     */
    public static boolean factoryControllerEnabledByDefault() {
        if (FACTORY_ENABLED_BY_DEFAULT == null || !SPEC.isLoaded()) {
            return false;
        }
        return FACTORY_ENABLED_BY_DEFAULT.get();
    }

    /**
     * {@code smart-interface.enable-smart-interface-bydefault}: whether the loader stays quiet about a machine
     * that does not declare any {@code smart-interfaces} while some recipe on it asks for an interface value.
     *
     * <p>It defaults to {@code false}, which is the loud setting, and it deliberately does <b>not</b> decide
     * whether a machine may answer an {@code interface_number_input} requirement: that is the machine's own
     * declaration, which is how the original decided it ({@code DynamicMachine#smartInterfaceTypesIsEmpty}
     * gating {@code checkAndAddSmartInterface}). Turning this on cannot make a value appear — there would be no
     * declared type to hold it under — so it only switches a warning off.
     */
    public static boolean smartInterfaceEnabledByDefault() {
        if (SMART_INTERFACE_ENABLED_BY_DEFAULT == null || !SPEC.isLoaded()) {
            return false;
        }
        return SMART_INTERFACE_ENABLED_BY_DEFAULT.get();
    }

    /**
     * {@code general.modular-controller-compatible-mode} — the original's switch for registering a controller in
     * the {@code modularcontroller} namespace ({@code Config.java:100-102}).
     *
     * <p><b>This key is inert, and this accessor exists so that the fact is readable rather than implied.</b> On
     * 1.20.1 the blocks cannot be gated on it at all: Forge fires {@code RegisterEvent} from its
     * {@code LOAD_REGISTRIES} state, and {@code CONFIG_LOAD} — the state that hands a spec its file — runs
     * afterwards, so while blocks are being registered {@code SPEC.isLoaded()} is still false and this method
     * returns the fallback below whatever the file says. Nor could it be otherwise: a save deserialises a placed
     * block by its registry id, so the id has to exist before the game can open the world in which the key would
     * have been read. See {@code MocNamespace} for the chain and for what the projection does instead.
     *
     * <p>The accessor is kept, and {@code ModBlocks.MOC_CONTROLLERS} deliberately does <b>not</b> consult it, so
     * that a reader of this class sees the whole story in one place rather than a silently dropped key.
     */
    public static boolean mocCompatibleMode() {
        if (MOC_COMPATIBLE_MODE == null || !SPEC.isLoaded()) {
            return false;
        }
        return MOC_COMPATIBLE_MODE.get();
    }

    /**
     * {@code general.disable-moc-deprecated-tip} — the original's switch for the two deprecation lines on a
     * {@code modularcontroller} controller ({@code Config.java:103-105}, applied at
     * {@code BlockController.java:118}).
     *
     * <p>Unlike {@link #mocCompatibleMode()} this one is <b>live</b>: it is read while a tooltip is built, which
     * happens long after {@code CONFIG_LOAD}, so {@code SPEC.isLoaded()} is true by then and the file's value is
     * what the player sees. Defaults to {@code false}, the original's own default, i.e. the tips are shown.
     */
    public static boolean disableMocDeprecatedTip() {
        if (DISABLE_MOC_DEPRECATED_TIP == null || !SPEC.isLoaded()) {
            return false;
        }
        return DISABLE_MOC_DEPRECATED_TIP.get();
    }

    /**
     * {@code general.structure-check-delay} — the original's base structure-check delay
     * ({@code TileMultiblockMachineController.java:122}, read at {@code :171-179}). Default 30, range 1..1200.
     *
     * <p>Falls back to the original's config default while the file has not been read yet, so a controller built
     * before {@code CONFIG_LOAD} still checks on a sane cadence.
     */
    public static int structureCheckDelay() {
        if (STRUCTURE_CHECK_DELAY_VALUE == null || !SPEC.isLoaded()) {
            return StructureCheckScheduler.DEFAULT_DELAY;
        }
        return STRUCTURE_CHECK_DELAY_VALUE.get();
    }

    /**
     * {@code general.delayed-structure-check} — whether the original's adaptive schedule is used
     * ({@code TileMultiblockMachineController.java:123}, read at {@code :176}). Default true.
     *
     * <p>When false the original checked every {@code structure-check-delay} ticks exactly and never consulted
     * {@code MMWorldEventListener}; the same is true here, because {@link StructureCheckScheduler#shouldCheck}
     * returns before reading its {@code areaChanged} argument.
     */
    public static boolean delayedStructureCheck() {
        if (DELAYED_STRUCTURE_CHECK_VALUE == null || !SPEC.isLoaded()) {
            return StructureCheckScheduler.DEFAULT_DELAYED;
        }
        return DELAYED_STRUCTURE_CHECK_VALUE.get();
    }

    /**
     * {@code general.max-structure-check-delay} — the ceiling on the adaptive interval
     * ({@code TileMultiblockMachineController.java:123}, read at {@code :180}). Default 100, range 2..1200.
     */
    public static int maxStructureCheckDelay() {
        if (MAX_STRUCTURE_CHECK_DELAY_VALUE == null || !SPEC.isLoaded()) {
            return StructureCheckScheduler.DEFAULT_MAX_DELAY;
        }
        return MAX_STRUCTURE_CHECK_DELAY_VALUE.get();
    }

    /**
     * {@code general.enable-full-data-sync} — the original's opt-in full block-entity sync
     * ({@code TileMultiblockMachineController.java:126}, read at {@code :193-198}). Default false.
     *
     * <p>The original sent the whole tile-entity NBT on a recipe start and finish when this was true
     * ({@code TileMachineController.java:265-275/299-306}); when false it only marked the chunk dirty. The
     * controller still pushes its whole update tag for structure, failure and thread changes, because those are
     * how the screen learns about them at all.
     */
    public static boolean enableFullDataSync() {
        if (ENABLE_FULL_DATA_SYNC_VALUE == null || !SPEC.isLoaded()) {
            return false;
        }
        return ENABLE_FULL_DATA_SYNC_VALUE.get();
    }

    /**
     * {@code general.enable-security-system} — the original's opt-in owner check
     * ({@code TileMultiblockMachineController.java:125}, read at {@code :195-196}). Default false.
     *
     * <p>Enforced at the two places the original enforced it: {@code EventHandler#onBlockBroken} cancels a
     * non-owner breaking a formed controller, and {@code EventHandler#onBlockPlaced} cancels placing an item whose
     * owner tag names someone else. The controller screen and the machine itself are not gated.
     */
    public static boolean enableSecuritySystem() {
        if (ENABLE_SECURITY_SYSTEM_VALUE == null || !SPEC.isLoaded()) {
            return false;
        }
        return ENABLE_SECURITY_SYSTEM_VALUE.get();
    }

    /**
     * {@code general.enable-duration-multiplier} — whether a {@code duration} modifier also scales the energy a
     * craft draws per tick, keeping its total energy cost constant
     * ({@code RecipeCraftingContext.java:196-199} in the original).
     *
     * <p><b>Default false.</b> The original's Java field is initialised to {@code true}
     * ({@code Config.java:44}), but {@code Config.load()} always overwrites it from
     * {@code getBoolean("enable-duration-multiplier", "general", false, ...)} ({@code Config.java:92}); with a
     * config file present — always, in practice — the effective default was false. This port applied the
     * compensation unconditionally until 0.77.0 (B11) and its old comment claimed the opposite default.
     *
     * <p>The switch is about <b>energy only</b>. A duration modifier changes {@link
     * com.reborn.modularmachinery.recipe.MachineRecipe#duration} whether this is on or off; with it off, a
     * 100-tick craft shortened to 50 ticks still pays the recipe's normal per-tick rate for 50 ticks.
     */
    public static boolean enableDurationMultiplier() {
        if (ENABLE_DURATION_MULTIPLIER_VALUE == null || !SPEC.isLoaded()) {
            return false;
        }
        return ENABLE_DURATION_MULTIPLIER_VALUE.get();
    }

    public static boolean enableFluxNetworksIntegration() {
        return ENABLE_FLUXNETWORKS_INTEGRATION == null || !SPEC.isLoaded() || ENABLE_FLUXNETWORKS_INTEGRATION.get();
    }

    /**
     * The three structure-check keys as one normalized schedule.
     *
     * <p>This is the method the controller calls. It exists separately from the three accessors so the original's
     * "delay must be smaller than max" rule is applied in exactly one place: the scheduler's record constructor
     * resets both numbers to 30/100, and this method logs the original's warning once when that reset happens
     * ({@code TileMultiblockMachineController.java:184-188}). Before the config file is read, the three accessors
     * return 30/true/100, so no warning is produced for a controller that checks during startup.
     */
    public static StructureCheckScheduler.Settings structureCheckSettings() {
        int delay = structureCheckDelay();
        boolean delayed = delayedStructureCheck();
        int maxDelay = maxStructureCheckDelay();
        if (delay >= maxDelay && WARNED_ABOUT_STRUCTURE_CHECK.compareAndSet(false, true)) {
            com.reborn.modularmachinery.ModularMachineryReborn.LOGGER.warn(
                    "[{}] structure-check-delay ({}) is bigger than or equal max-structure-check-delay ({});"
                            + " using the original's fallback 30/100.",
                    com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, delay, maxDelay);
        }
        return StructureCheckScheduler.of(delay, delayed, maxDelay);
    }

    private ModConfig() {
    }
}
