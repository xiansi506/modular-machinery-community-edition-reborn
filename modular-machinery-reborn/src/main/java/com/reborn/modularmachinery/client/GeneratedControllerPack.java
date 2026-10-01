package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.GeneratedControllerAssets;
import com.reborn.modularmachinery.block.MocNamespace;
import com.reborn.modularmachinery.block.ModBlocks;
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
 * A synthetic client resource pack holding the blockstate and item model of every controller a pack author
 * declared in {@code config/modular_machinery_reborn/machinery/}.
 *
 * <p>Without this those blocks would render as the missing model, because a blockstate file has to exist for
 * every registered block and the mod jar only ships files for its own machines. The original solved the same
 * problem the same way: {@code RegistryBlocks.writeControllerModel} wrote a copy of
 * {@code blockstates/block_machine_controller.json} into a resource manager for each machine it registered.
 *
 * <p>All controllers share one model, so the generated files only differ by name. This class is safe to load on
 * a dedicated server — it touches no client-only types, and the pack is only ever contributed for
 * {@link PackType#CLIENT_RESOURCES}.
 *
 * <p>M6e adds the same treatment for {@code <machine>_factory_controller}: a declaration with
 * {@code "has-factory": true} registers such a block, and nothing in the jar can name it. The generated
 * blockstate points at the same {@code block/machine_controller} model the ordinary bound controllers use —
 * the original's factory controller was the same shape of block with a different tile entity, and this slice
 * adds no textures (the factory's own GUI is M6e-2).
 *
 * <h2>0.27.0: a second namespace</h2>
 *
 * <p>{@code ModBlocks} also registers each declared machine's controller in the {@code modularcontroller}
 * compatibility namespace, and a resource file's namespace is the directory its id names — so those blocks need
 * their <b>own</b> copies under {@code assets/modularcontroller/}. They are generated from the same template,
 * because an old save's block must render exactly as it did before; they are also why {@link #getNamespaces}
 * now answers with two namespaces rather than one, and why {@link #listResources} cannot filter on a single
 * namespace any more.
 */
public final class GeneratedControllerPack extends AbstractPackResources {

    private static final String NAMESPACE = ModularMachineryReborn.MOD_ID;

    /**
     * The mod's own namespace: one blockstate plus one item model per declared controller.
     *
     * <p>The files themselves are built by {@link GeneratedControllerAssets}, which depends on nothing but the
     * declarations — see that class for why it is not inlined here.
     */
    private static final Map<ResourceLocation, byte[]> FILES =
            GeneratedControllerAssets.ordinary(NAMESPACE, ModBlocks.controllersNeedingGeneratedAssets());

    /** {@code modularcontroller}: the same files, for the compatibility namespace's controllers. */
    private static final Map<ResourceLocation, byte[]> MOC_FILES =
            GeneratedControllerAssets.compatibility(NAMESPACE, ModBlocks.MOC_CONTROLLERS);

    private final Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();

    public GeneratedControllerPack(String packId) {
        super(packId, true);
        this.files.putAll(FILES);
        this.files.putAll(MOC_FILES);
    }

    /** How many files this pack carries, for the startup log. */
    public int fileCount() {
        return this.files.size();
    }

    /** The mod's own namespace's generated files, exposed for the offline acceptance harness. */
    public static Map<ResourceLocation, byte[]> files() {
        return FILES;
    }

    /** The {@code modularcontroller} namespace's generated files, exposed for the offline acceptance harness. */
    public static Map<ResourceLocation, byte[]> mocFiles() {
        return MOC_FILES;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        byte[] data = this.files.get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
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
     * Both namespaces, because this pack really does carry files for both: the mod's own for the ordinary
     * controllers and {@code modularcontroller} for the compatibility ones. A pack that claimed only one would
     * leave the other namespace's blocks with no blockstate.
     */
    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(NAMESPACE, MocNamespace.NAMESPACE) : Set.of();
    }

    @Override
    public void close() {
        // Nothing to release; the files are in memory.
    }
}
