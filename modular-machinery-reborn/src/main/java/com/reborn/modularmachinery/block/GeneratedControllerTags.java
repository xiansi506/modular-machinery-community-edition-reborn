package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.machine.MachineDirectory;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mineability tags a declared controller needs, built from the declarations.
 *
 * <p><b>Why this exists.</b> The same reason {@link GeneratedControllerLootTables} does: a controller's registry
 * name is the pack author's, so no file in the jar can name it. The jar's tag files therefore list the eleven fixed
 * blocks and a handful of guessed controller names that happen to be baked-in machines; an author's
 * {@code my_machine_controller} is not among them, and a block that is not in {@code minecraft:mineable/pickaxe}
 * is mined at one fifth speed and, with {@code requiresCorrectToolForDrops}, never drops.
 *
 * <p>That was the 0.31.0 omission this class closes: the loot tables were generated for declared controllers but
 * the tags were not, so a declared controller dropped nothing <i>and</i> took forever to break — the very first
 * bug report, still present for every author-declared machine.
 *
 * <p><b>Merging, not replacing.</b> These files carry only the declared names and set {@code replace: false}, so
 * the game merges them with the jar's own tag files. The two lists must be able to describe the same tag without
 * either winning: the jar keeps the fixed blocks mineable on an install with no declarations at all, and this pack
 * adds whatever the declarations require.
 *
 * <p>Deliberately produces plain-string entries. Unlike the loot tables, a tag entry names a <b>block</b>, and the
 * block id is what the declaration determines — there is no second name to get wrong.
 */
public final class GeneratedControllerTags {

    /** The tag the game asks when deciding whether a pickaxe is the right tool. */
    public static final String MINEABLE_PICKAXE = "mineable/pickaxe";

    /** The tag that raises the requirement from "any pickaxe" to "stone or better", matching the original's
     * {@code setHarvestLevel("pickaxe", 1)}. */
    public static final String NEEDS_STONE_TOOL = "needs_stone_tool";

    /**
     * One tag file's text: {@code replace:false} so it merges, and one plain entry per declared controller.
     *
     * <p>An empty list still produces a valid file with an empty array rather than no file at all — a pack that
     * silently omits a file is harder to diagnose than one that states it has nothing to add.
     */
    public static String tag(List<String> blockIds) {
        StringBuilder values = new StringBuilder();
        for (String id : blockIds) {
            if (values.length() > 0) {
                values.append(',');
            }
            values.append('"').append(id).append('"');
        }
        return "{\"replace\":false,\"values\":[" + values + "]}";
    }

    /**
     * Every controller block id the declarations produce, in both namespaces and including factory controllers.
     *
     * <p>Both tags get the same list: a controller that is mineable but needs no particular tier would be a
     * deviation from the fixed blocks, which carry both.
     */
    public static List<String> controllerBlockIds(String namespace,
                                                  List<MachineDirectory.MachineRef> declarations,
                                                  String mocNamespace,
                                                  List<MachineDirectory.MachineRef> mocDeclarations) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (MachineDirectory.MachineRef ref : declarations) {
            out.add(namespace + ":" + ref.path() + "_controller");
            if (ref.factoryController()) {
                out.add(namespace + ":" + ref.path() + "_factory_controller");
            }
        }
        for (MachineDirectory.MachineRef ref : mocDeclarations) {
            out.add(mocNamespace + ":" + ref.path() + "_controller");
        }
        return List.copyOf(out);
    }

    /** The two tag files, keyed by their data-pack resource id. */
    public static Map<ResourceLocation, byte[]> all(String namespace,
                                                    List<MachineDirectory.MachineRef> declarations,
                                                    String mocNamespace,
                                                    List<MachineDirectory.MachineRef> mocDeclarations) {
        List<String> ids = controllerBlockIds(namespace, declarations, mocNamespace, mocDeclarations);
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        // A tag that adds to a vanilla tag lives in the VANILLA namespace, not the mod's. Writing
        // data/<modid>/tags/... instead creates a tag nothing reads — silently, with no error anywhere. That
        // mistake cost several rounds on the fixed blocks; it is encoded here so it cannot recur.
        out.put(new ResourceLocation("minecraft", "tags/blocks/" + MINEABLE_PICKAXE + ".json"),
                tag(ids).getBytes(StandardCharsets.UTF_8));
        out.put(new ResourceLocation("minecraft", "tags/blocks/" + NEEDS_STONE_TOOL + ".json"),
                tag(ids).getBytes(StandardCharsets.UTF_8));
        return out;
    }

    private GeneratedControllerTags() {
    }
}
