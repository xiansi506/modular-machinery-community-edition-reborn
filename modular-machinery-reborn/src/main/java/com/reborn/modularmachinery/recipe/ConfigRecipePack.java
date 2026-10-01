package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The server data pack view of {@code config/modular_machinery_reborn/recipes/}.
 *
 * <p>Resources are addressed by the same {@code recipes/<path>.json} locations a real data pack would use; the
 * pack framework turns those into {@code data/<namespace>/recipes/<path>.json} on its own. Because the file map
 * is rebuilt every time the pack is opened, an edited file takes effect on {@code /reload}.
 */
public final class ConfigRecipePack extends AbstractPackResources {

    private static final String NAMESPACE = ModularMachineryReborn.MOD_ID;

    private final Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();

    public ConfigRecipePack(String packId) {
        super(packId, true);
        this.files.putAll(ConfigRecipes.readAll());
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... path) {
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
        if (type != PackType.SERVER_DATA || !NAMESPACE.equals(namespace)) {
            return;
        }
        for (Map.Entry<ResourceLocation, byte[]> entry : this.files.entrySet()) {
            if (!entry.getKey().getPath().startsWith(path)) {
                continue;
            }
            byte[] data = entry.getValue();
            output.accept(entry.getKey(), () -> new ByteArrayInputStream(data));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE) : Set.of();
    }

    @Override
    public void close() {
        // Nothing to release; the files are in memory.
    }
}
