package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.event.AddPackFindersEvent;

/**
 * Contributes the two synthetic packs that give an author-declared machine its files.
 *
 * <p>A controller is registered per declaration in {@code config/modular_machinery_reborn/machinery/}, and its
 * name is the author's, so the jar cannot carry its blockstate, its item model or its loot table. Two packs close
 * that gap:
 *
 * <ul>
 *   <li>{@link PackType#CLIENT_RESOURCES} — blockstates and item models, via
 *       {@code client.GeneratedControllerPack} (so the block renders);</li>
 *   <li>{@link PackType#SERVER_DATA} — loot tables, via {@link GeneratedLootTablePack}
 *       (so the block drops itself when broken).</li>
 * </ul>
 *
 * <p>This class used to live in the {@code client} package and only handled resources. The data pack has nothing
 * to do with the client — a dedicated server needs it just as much — so the dispatcher moved here where both
 * halves are decided in one place.
 *
 * <p>Each pack is only worth adding when there is something in it, so a normal install with no declarations
 * contributes neither and the pack list stays clean.
 */
public final class GeneratedPacks {

    /** 1.20.1 pack format, shared by both pack types. */
    private static final int PACK_FORMAT = 15;

    public static void onAddPackFinders(AddPackFindersEvent event) {
        // Both halves count the same declarations: a machine declared in the config directory gets a controller in
        // the mod's namespace and an alias in the compatibility namespace. The same two lists feed both packs, so
        // "there is something to generate" is one question, not two.
        int declarations = ModBlocks.controllersNeedingGeneratedAssets().size()
                + ModBlocks.MOC_CONTROLLERS.size();
        if (declarations == 0) {
            return;
        }

        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            ModularMachineryReborn.LOGGER.info("[{}] Generating controller models for {} machine declaration(s) "
                            + "in the config directory, in the mod's own namespace and in '{}'",
                    ModularMachineryReborn.MOD_ID, declarations, MocNamespace.NAMESPACE);
            event.addRepositorySource(packs -> packs.accept(Pack.create(
                    ModularMachineryReborn.MOD_ID + "_generated_controllers",
                    Component.literal("Modular Machinery Reborn: generated controller models"),
                    true,
                    com.reborn.modularmachinery.client.GeneratedControllerPack::new,
                    new Pack.Info(Component.literal("Controller blockstates and item models for machines declared "
                            + "in config/modular_machinery_reborn/machinery/"), PACK_FORMAT, FeatureFlags.VANILLA_SET),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    true,
                    PackSource.BUILT_IN)));
            return;
        }

        if (event.getPackType() == PackType.SERVER_DATA) {
            ModularMachineryReborn.LOGGER.info("[{}] Generating controller loot tables for {} machine "
                            + "declaration(s) in the config directory, in the mod's own namespace and in '{}'",
                    ModularMachineryReborn.MOD_ID, declarations, MocNamespace.NAMESPACE);
            event.addRepositorySource(packs -> packs.accept(Pack.create(
                    ModularMachineryReborn.MOD_ID + "_generated_loot_tables",
                    Component.literal("Modular Machinery Reborn: generated controller loot tables"),
                    true,
                    GeneratedLootTablePack::new,
                    new Pack.Info(Component.literal("Block loot tables for controllers of machines declared "
                            + "in config/modular_machinery_reborn/machinery/"), PACK_FORMAT, FeatureFlags.VANILLA_SET),
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    true,
                    PackSource.BUILT_IN)));
        }
    }

    private GeneratedPacks() {
    }
}
