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
 * A synthetic data pack holding one loot table per controller a pack author declared in
 * {@code config/modular_machinery_reborn/machinery/}.
 *
 * <p>This is the data-pack twin of {@code GeneratedControllerPack}, which does the same for blockstates and item
 * models. A controller's registry name is the author's, so no file in the jar can name it — and a block with no
 * loot table does not drop when broken, which would make an author's controller (and an old save's compatibility
 * controller) permanently unobtainable. The files themselves are built by
 * {@link GeneratedControllerLootTables}, which depends on the declarations alone so the offline harness can build
 * the same bytes.
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

    private final Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();

    public GeneratedLootTablePack(String packId) {
        super(packId, true);
        this.files.putAll(FILES);
        this.files.putAll(MOC_FILES);
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

    /** Both namespaces, because the pack carries tables for both. */
    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE, MocNamespace.NAMESPACE) : Set.of();
    }

    @Override
    public void close() {
        // Nothing to release: every file is a byte array this class owns.
    }
}
