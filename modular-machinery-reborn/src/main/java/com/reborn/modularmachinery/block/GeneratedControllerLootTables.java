package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.machine.MachineDirectory;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The loot table a controller block needs when the mod cannot ship one: a single-item drop of itself.
 *
 * <p>Why these tables cannot be files in the jar: a controller is registered per machine <b>declaration</b>, in the
 * namespace the mod owns and in the {@code modularcontroller} compatibility namespace, and a declaration's name is
 * the pack author's. The jar ships loot tables for its eleven fixed blocks only
 * ({@code data/modular_machinery_reborn/loot_tables/blocks/}); without this builder a declared machine's controller
 * would be minable and would drop <b>nothing</b>, and an old save's controller block would be destroyed for good.
 *
 * <p>This mirrors {@link GeneratedControllerAssets}, which solves the same "the name is the author's" problem for
 * blockstates and item models. One important difference: that class feeds a {@code CLIENT_RESOURCES} pack, while
 * this one feeds a data pack. Both are built the same way and for the same reason.
 *
 * <h2>Why the file is not written inside the pack class</h2>
 *
 * <p>Same reason as {@link GeneratedControllerAssets#ordinary}: a class whose static fields read {@code ModBlocks}
 * cannot be initialised outside Forge's mod loading, so a builder living there could not be driven by the offline
 * harness. Keeping the arithmetic here, over the declarations alone, lets the harness build the very bytes the live
 * pack builds.
 *
 * <h2>The item is the block</h2>
 *
 * <p>{@code MachineControllerBlock}s register their item with the <b>same</b> registry name as the block
 * ({@code ITEMS.register(ref.path() + "_controller", …)} beside {@code BLOCKS.register(ref.path() + "_controller",
 * …)}), so one id names both and the entry is simply that id. That equality is a fact about this mod, not a
 * convention to assume — the pack authors' declared names are arbitrary, and the static blocks proved the point by
 * <b>not</b> having it (hatches and casings register one item per variant under a different name).
 */
public final class GeneratedControllerLootTables {

    /**
     * One pool, one entry, the block itself. Kept byte-identical in shape to the eleven tables in the jar so a
     * controller's drop behaves exactly like a fixed block's.
     */
    private static final String TEMPLATE = "{\"type\":\"minecraft:block\",\"pools\":[{"
            + "\"bonus_rolls\":0.0,"
            + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}],"
            + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"%s:%s\"}],"
            + "\"rolls\":1.0}]}";

    /** Loot table id for a controller block id. The data-pack path is {@code loot_tables/blocks/<path>.json}. */
    public static ResourceLocation tableIdFor(ResourceLocation blockId) {
        return new ResourceLocation(blockId.getNamespace(), "blocks/" + blockId.getPath());
    }

    /** The block id of a controller in {@code namespace}, from its machine path. */
    public static ResourceLocation blockId(String namespace, String machinePath) {
        return new ResourceLocation(namespace, machinePath + "_controller");
    }

    /**
     * Tables for the mod's own namespace: one per declaration, plus one per declaration that asked for a factory.
     *
     * <p>Written under the block's own namespace because a data-pack file's namespace is the directory it names —
     * exactly the rule that made the mineability tags silently useless when they were written under the wrong one.
     */
    public static Map<ResourceLocation, byte[]> ordinary(String namespace,
                                                         List<MachineDirectory.MachineRef> declarations) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (MachineDirectory.MachineRef ref : declarations) {
            out.put(tableIdFor(blockId(namespace, ref.path())),
                    table(namespace, ref.path() + "_controller").getBytes(StandardCharsets.UTF_8));
            if (ref.factoryController()) {
                out.put(tableIdFor(blockId(namespace, ref.path() + "_factory")),
                        table(namespace, ref.path() + "_factory_controller").getBytes(StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /**
     * Tables for the {@code modularcontroller} compatibility namespace.
     *
     * <p>Those blocks exist so an old save's controller can be loaded and broken; if they dropped nothing, breaking
     * one would delete it. They are aliases of the mod's own controllers, but a data-pack file cannot live in two
     * namespaces at once, so they get their own copies built from the same template.
     */
    public static Map<ResourceLocation, byte[]> compatibility(String namespace,
                                                              List<MachineDirectory.MachineRef> declarations) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (MachineDirectory.MachineRef ref : declarations) {
            out.put(tableIdFor(blockId(namespace, ref.path())),
                    table(namespace, ref.path() + "_controller").getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    /** The whole pack: the mod's namespace and the compatibility namespace in one map. */
    public static Map<ResourceLocation, byte[]> all(String namespace,
                                                    List<MachineDirectory.MachineRef> declarations,
                                                    String mocNamespace,
                                                    List<MachineDirectory.MachineRef> mocDeclarations) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>(ordinary(namespace, declarations));
        out.putAll(compatibility(mocNamespace, mocDeclarations));
        return out;
    }

    /** The table's text, for callers that assert on it. */
    public static String table(String namespace, String itemPath) {
        return String.format(TEMPLATE, namespace, itemPath);
    }

    private GeneratedControllerLootTables() {
    }
}
