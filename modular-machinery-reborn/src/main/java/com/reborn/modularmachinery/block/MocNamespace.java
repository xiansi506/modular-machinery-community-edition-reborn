package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * The <b>ModularController compatibility namespace</b> — the original MMCE absorbed the older
 * <i>ModularController</i> mod and kept a controller block registered under the {@code modularcontroller}
 * namespace so a save made with that mod still finds its block.
 *
 * <h2>What the original did</h2>
 * <ul>
 *   <li>{@code RegistryBlocks.java:408-420} — under {@code Config.mocCompatibleMode}, every machine that is not
 *       {@code isFactoryOnly()} gets {@code new BlockController("modularcontroller", machine)} plus an
 *       {@code ItemBlockController} registered under the same name, i.e.
 *       <b>{@code modularcontroller:<machine path>_controller}</b>, block and item.</li>
 *   <li>{@code BlockController.java:94-100} — that constructor differs from the ordinary
 *       {@code BlockController(DynamicMachine)} at {@code :86-92} in the namespace alone.</li>
 *   <li>{@code BlockController.java:289-297} — {@code createTileEntity} returns the ordinary
 *       {@code TileMachineController}, so the MOC block and the ordinary block share their tile entity.</li>
 *   <li>{@code BlockController.java:118-121} — the deprecation tooltip, gated on
 *       {@code Config.disableMocDeprecatedTip}.</li>
 * </ul>
 *
 * <h2>Why the blocks are registered unconditionally, and why the original's own switch cannot work here</h2>
 *
 * <p>The original decided this at construction time from a {@code ModConfig} value. <b>On 1.20.1 that value is
 * not readable when blocks are registered.</b> Forge 47.2.0 orders its mod-loading states
 * {@code CREATE_REGISTRIES → OBJECT_HOLDERS → INJECT_CAPABILITIES → UNFREEZE_DATA → LOAD_REGISTRIES}
 * ({@code ForgeStatesProvider.java:21-25}), and {@code LOAD_REGISTRIES} is the state that fires
 * {@code RegisterEvent} through {@code GameData.postRegisterEvents()}. {@code CONFIG_LOAD}
 * ({@code ModStateProvider.java:63-69}), the state that hands each spec its file through
 * {@code ConfigTracker.loadConfigs → ModConfig.setConfigData → ForgeConfigSpec.setConfig}, runs after it — its
 * declared successor is {@code COMMON_SETUP}. Since {@code ForgeConfigSpec.isLoaded()} is simply
 * {@code childConfig != null} ({@code ForgeConfigSpec.java:104-106}), a registry-time read of any
 * {@link ModConfig} accessor returns that accessor's pre-config fallback, never the file's value.
 *
 * <p>So a gate is not merely inconvenient here — it is <b>impossible</b>, and a "false" gate would be the worst
 * of the three options: the flag could never turn the blocks on, so the one thing the feature exists for (an
 * old save's block resolving to a registered id) would still fail. The block ids have to exist <i>before</i> the
 * config is read, because a chunk deserialises a placed block by its id: if the id is not registered, the save
 * does not load, and "turn the flag on first" cannot be followed — the game has to start to read the flag, and
 * it cannot start.
 *
 * <p>This projection therefore registers the MOC blocks <b>always</b>, for exactly the machines the ordinary
 * bound controllers exist for (the {@code config/modular_machinery_reborn/machinery/} declarations, which is
 * D9/D10's construction-time source; {@code factory-only} machines are skipped, as the original skipped them at
 * {@code :410-412}). Consequences, stated rather than hidden:
 *
 * <ul>
 *   <li><b>The block set is a pure increment.</b> It depends on no config value, so D8's rule — the block set is
 *       frozen before machine definitions load — still holds, and nothing about the ordinary controllers
 *       changes.</li>
 *   <li><b>The MOC items are not in the creative tab.</b> They exist so an old save's block has an item form
 *       again, not to be handed out; a fresh install's visible surface is unchanged. This is also the
 *       mitigation for the one real cost of always registering: {@code 2 × N} extra registry entries.</li>
 *   <li><b>An MOC controller is an alias, not a second controller.</b> It is the same block class, the same
 *       tile, and the same {@code boundMachine}; it must therefore never be enumerated as an additional
 *       controller anywhere that lists them — JEI catalysts, the structure-preview machine info, or the
 *       generated resource pack. {@link ModBlocks} keeps its own list for this namespace for that reason, and
 *       section Y of the acceptance harness asserts it.</li>
 * </ul>
 *
 * <h2>The config key that says this</h2>
 *
 * <p>{@code general.modular-controller-compatible-mode} is kept because the original had it and a pack author's
 * existing file must not become invalid, but it is <b>inert</b> here and its own comment says so. Only the
 * tooltip switch is live, because a tooltip is rendered long after the config is loaded.
 */
public final class MocNamespace {

    /**
     * The older mod's namespace, hardcoded — the original passed the literal {@code "modularcontroller"} to
     * {@code new BlockController(String, DynamicMachine)} at {@code RegistryBlocks.java:414}.
     */
    public static final String NAMESPACE = "modularcontroller";

    /**
     * The MOC registry name of a machine's controller: {@code modularcontroller:<machine path>_controller}.
     *
     * <p>One function, called both by the registration and by the acceptance harness, so the name the harness
     * asserts is the name the registry actually receives. The original built it in
     * {@code BlockController.java:94-100} from {@code parentMachine.getRegistryName().getPath()}.
     */
    public static ResourceLocation controllerName(ResourceLocation machineId) {
        return new ResourceLocation(NAMESPACE, machineId.getPath() + "_controller");
    }

    /**
     * Whether a block id belongs to this compatibility namespace — the original's condition for showing the
     * deprecation tooltip ({@code BlockController.java:118}).
     */
    public static boolean isCompatibilityName(ResourceLocation blockId) {
        return NAMESPACE.equals(blockId.getNamespace());
    }

    /**
     * The machines that get a controller in this namespace: every declared machine that is <b>not</b>
     * {@code factory-only}, which is the original's own guard ({@code RegistryBlocks.java:410-412}).
     *
     * <p>This is the whole rule, and it lives here rather than inside {@link ModBlocks} so that it can be driven
     * without a game run: {@code ModBlocks} cannot even be loaded outside Forge's own class loader, because its
     * static initialiser builds {@code DeferredRegister}s. The block registry then walks this list, which is the
     * only other half of the feature.
     */
    public static java.util.List<com.reborn.modularmachinery.machine.MachineDirectory.MachineRef>
            compatibilityMachines(java.nio.file.Path machineDirectoryRoot) {
        java.util.List<com.reborn.modularmachinery.machine.MachineDirectory.MachineRef> refs =
                new java.util.ArrayList<>();
        for (com.reborn.modularmachinery.machine.MachineDirectory.MachineRef ref
                : com.reborn.modularmachinery.machine.MachineDirectory.scanMachinePaths(machineDirectoryRoot)) {
            if (ref.factoryOnly()) {
                continue;
            }
            refs.add(ref);
        }
        return java.util.List.copyOf(refs);
    }

    /** The mod id the ordinary controllers live under, for a readable failure when the two are confused. */
    public static String ordinaryNamespace() {
        return ModularMachineryReborn.MOD_ID;
    }

    private MocNamespace() {
    }
}
