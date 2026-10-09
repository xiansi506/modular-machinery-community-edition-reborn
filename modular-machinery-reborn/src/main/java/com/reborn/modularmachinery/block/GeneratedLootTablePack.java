package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A synthetic data pack holding the data files of every controller a pack author declared in
 * {@code config/modular_machinery_reborn/machinery/}: one loot table each, plus the mineability tags that
 * name them.
 *
 * <p>This is the data-pack twin of {@code GeneratedControllerPack}, which does the same for blockstates and item
 * models. A controller's registry name is the author's, so no file in the jar can name it — and a block that is
 * neither in {@code mineable/pickaxe} nor has a loot table takes five times as long to break and then drops
 * nothing. The files themselves are built by {@link GeneratedControllerLootTables} and
 * {@link GeneratedControllerTags}, which depend on the declarations alone so the offline harness can build the
 * same bytes.
 *
 * <p>Safe on a dedicated server: it touches no client-only type, and the pack is only ever contributed for
 * {@link PackType#SERVER_DATA}.
 */
public final class GeneratedLootTablePack extends AbstractPackResources {

    private static final String NAMESPACE = ModularMachineryReborn.MOD_ID;

    /** 1.20.1 data pack format (the same number as the resource format). */
    private static final int PACK_FORMAT = 15;

    private static final Map<ResourceLocation, byte[]> FILES = GeneratedControllerLootTables.ordinary(
            NAMESPACE, ModBlocks.controllersNeedingGeneratedAssets());

    private static final Map<ResourceLocation, byte[]> MOC_FILES = GeneratedControllerLootTables.compatibility(
            MocNamespace.NAMESPACE, ModBlocks.MOC_CONTROLLERS);

    /**
     * The tags, for the same declarations. Kept in this pack rather than a second one: the game merges tag files
     * across packs, and a controller needs both its table and its tag to behave like a fixed block, so they are
     * generated and shipped together.
     */
    private static final Map<ResourceLocation, byte[]> TAG_FILES = GeneratedControllerTags.all(
            NAMESPACE,
            ModBlocks.controllersNeedingGeneratedAssets(),
            MocNamespace.NAMESPACE,
            ModBlocks.MOC_CONTROLLERS);

    private final Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();

    public GeneratedLootTablePack(String packId) {
        super(packId, true);
        this.files.putAll(FILES);
        this.files.putAll(MOC_FILES);
        this.files.putAll(TAG_FILES);
    }

    /** How many tables this pack carries, for the startup log. */
    public int fileCount() {
        return this.files.size();
    }

    /** The mod's own namespace's generated tables, exposed for the offline acceptance harness. */
    public static Map<ResourceLocation, byte[]> files() {
        return FILES;
    }

    /** The {@code modularcontroller} namespace's generated tables, exposed for the offline acceptance harness. */
    public static Map<ResourceLocation, byte[]> mocFiles() {
        return MOC_FILES;
    }

    /** The generated tag files, exposed for the offline acceptance harness. */
    public static Map<ResourceLocation, byte[]> tagFiles() {
        return TAG_FILES;
    }

    /**
     * {@code pack.mcmeta}. A repository-source pack is read from this method rather than from a real file, and a
     * data pack without one is not usable — the format is the part that matters here.
     */
    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && "pack.mcmeta".equals(path[0])) {
            String meta = "{\"pack\":{\"pack_format\":" + PACK_FORMAT
                    + ",\"description\":\"Modular Machinery Reborn: generated controller loot tables\"}}";
            return () -> new ByteArrayInputStream(meta.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        byte[] data = this.files.get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            return;
        }
        for (Map.Entry<ResourceLocation, byte[]> entry : this.files.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace) || !location.getPath().startsWith(path)) {
                continue;
            }
            byte[] data = entry.getValue();
            output.accept(location, () -> new ByteArrayInputStream(data));
        }
    }

    /**
     * <b>Every</b> namespace this pack serves files for: the mod's own, the compatibility namespace, and
     * {@code minecraft} — because a tag that adds to a vanilla tag lives under {@code data/minecraft/…}.
     *
     * <p>Leaving {@code minecraft} out is what kept the generated tags (and, before them, the generated loot
     * tables' namespaced paths) from ever being read: a synthetic pack is asked which namespaces it serves, and a
     * namespace it does not name is not searched, however correct the files inside it are. The build succeeded,
     * the pack loaded, and nothing anywhere reported an error — the same silent shape as writing the tags under
     * the wrong namespace in the first place.
     */
    private static final Set<String> SERVED_NAMESPACES =
            Set.of(NAMESPACE, MocNamespace.NAMESPACE, "minecraft");

    /** The namespaces this pack serves, exposed for the offline acceptance harness. */
    public static Set<String> namespaces() {
        return SERVED_NAMESPACES;
    }

    /** Both namespaces, because the pack carries tables for both. */
    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? SERVED_NAMESPACES : Set.of();
    }

    @Override
    public void close() {
        // Nothing to release: every file is a byte array this class owns.
    }
}
