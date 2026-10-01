package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.machine.MachineDirectory;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two files a controller block needs when the mod cannot ship them: its blockstate and its item model.
 *
 * <p>A resource file's namespace is the directory its id names, so a controller in the {@code modularcontroller}
 * compatibility namespace needs its own copies under {@code assets/modularcontroller/} — the mod's own namespace
 * cannot describe it. Both namespaces' files come from one template, because an old save's block must render
 * exactly as an ordinary controller does; the original did the same by copying
 * {@code blockstates/block_machine_controller.json} once per machine
 * ({@code RegistryBlocks.writeControllerModel}).
 *
 * <h2>Why this is not inside {@code GeneratedControllerPack}</h2>
 *
 * <p>{@code GeneratedControllerPack}'s own static fields read {@code ModBlocks}, which cannot be initialised
 * outside Forge's mod loading — so a builder living there could not be driven by the offline acceptance harness,
 * and the file shape would go unasserted. Keeping the arithmetic in this class, which depends on nothing but the
 * declarations, means the harness can build the very files the live pack builds. The pack passes what it read;
 * the harness passes a fixture.
 *
 * <p>The template is a parameter rather than a constant here for the same reason: the caller owns the "which
 * model does a controller use" decision, and the harness wants to be told it, not to assume it.
 */
public final class GeneratedControllerAssets {

    /**
     * The blockstate every controller uses: the four horizontal facings, all pointing at one model. Matches
     * {@code assets/modular_machinery_reborn/blockstates/machine_controller.json} in the jar.
     */
    public static final String BLOCKSTATE_TEMPLATE = "{\"variants\":{"
            + "\"facing=north\":{\"model\":\"%s:block/machine_controller\"},"
            + "\"facing=south\":{\"model\":\"%s:block/machine_controller\",\"y\":180},"
            + "\"facing=west\":{\"model\":\"%s:block/machine_controller\",\"y\":270},"
            + "\"facing=east\":{\"model\":\"%s:block/machine_controller\",\"y\":90}}}";

    /** The item model every controller uses: a parent pointing at the block model. */
    public static final String ITEM_MODEL_TEMPLATE = "{\"parent\":\"%s:block/machine_controller\"}";

    /**
     * The mod's own directory inside a pack, resolved for writing. The generated files are handed to the pack
     * resource by id, so this is only the string the pack's own namespace is built under.
     */
    public static Map<ResourceLocation, byte[]> ordinary(String modelNamespace,
                                                        List<MachineDirectory.MachineRef> declarations) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (MachineDirectory.MachineRef ref : declarations) {
            out.put(new ResourceLocation(modelNamespace,
                            "blockstates/" + ref.path() + "_controller.json"),
                    blockstate(modelNamespace).getBytes(StandardCharsets.UTF_8));
            out.put(new ResourceLocation(modelNamespace, "models/item/" + ref.path() + "_controller.json"),
                    itemModel(modelNamespace).getBytes(StandardCharsets.UTF_8));
            if (!ref.factoryController()) {
                continue;
            }
            out.put(new ResourceLocation(modelNamespace,
                            "blockstates/" + ref.path() + "_factory_controller.json"),
                    blockstate(modelNamespace).getBytes(StandardCharsets.UTF_8));
            out.put(new ResourceLocation(modelNamespace,
                            "models/item/" + ref.path() + "_factory_controller.json"),
                    itemModel(modelNamespace).getBytes(StandardCharsets.UTF_8));
        }
        return Map.copyOf(out);
    }

    /**
     * The compatibility namespace's copies, under {@link MocNamespace#NAMESPACE}. Same template, different
     * namespace: the point is that a block placed by the older ModularController mod keeps rendering.
     *
     * <p>No factory files, because the original never registered a factory controller in this namespace
     * ({@code RegistryBlocks.java:408-420} has no {@code BlockFactoryController} branch), and
     * {@link MocNamespace#compatibilityMachines} has already skipped the {@code factory-only} machines.
     */
    public static Map<ResourceLocation, byte[]> compatibility(String modelNamespace,
                                                              List<MachineDirectory.MachineRef> declarations) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (MachineDirectory.MachineRef ref : declarations) {
            out.put(new ResourceLocation(MocNamespace.NAMESPACE,
                            "blockstates/" + ref.path() + "_controller.json"),
                    blockstate(modelNamespace).getBytes(StandardCharsets.UTF_8));
            out.put(new ResourceLocation(MocNamespace.NAMESPACE,
                            "models/item/" + ref.path() + "_controller.json"),
                    itemModel(modelNamespace).getBytes(StandardCharsets.UTF_8));
        }
        return Map.copyOf(out);
    }

    /** The blockstate for a controller whose model lives in {@code modelNamespace}. */
    public static String blockstate(String modelNamespace) {
        return String.format(BLOCKSTATE_TEMPLATE, modelNamespace, modelNamespace, modelNamespace,
                modelNamespace);
    }

    /** The item model for a controller whose model lives in {@code modelNamespace}. */
    public static String itemModel(String modelNamespace) {
        return String.format(ITEM_MODEL_TEMPLATE, modelNamespace);
    }

    private GeneratedControllerAssets() {
    }
}
