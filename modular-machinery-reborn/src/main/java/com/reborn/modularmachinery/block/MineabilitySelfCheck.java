package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * One line at world start listing every one of this mod's blocks that is <b>not</b> in
 * {@code minecraft:mineable/pickaxe}.
 *
 * <p>Temporary, for the "a declared machine's controller still cannot be mined" report. The question it answers
 * cannot be answered from a file: the generated tag and the jar's tag are merged by the game at load time, so
 * whether a given block ended up in the final tag is a runtime fact. Reading it here beats reasoning about pack
 * order, merge rules and namespaces — which is what went wrong repeatedly on the fixed blocks.
 *
 * <p>It also prints the tag's total size, so an empty or missing merge is visible at a glance.
 */
public final class MineabilitySelfCheck {

    private static final TagKey<Block> MINEABLE_PICKAXE =
            TagKey.create(Registries.BLOCK, new ResourceLocation("minecraft", "mineable/pickaxe"));

    private MineabilitySelfCheck() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        var lookup = event.getServer().registryAccess().registryOrThrow(Registries.BLOCK);
        var tag = lookup.getTag(MINEABLE_PICKAXE);
        int size = tag.map(set -> set.size()).orElse(-1);

        List<String> missing = new ArrayList<>();
        List<String> present = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null || !isOurs(id, block)) {
                continue;
            }
            boolean in = tag.map(set -> set.contains(BuiltInRegistries.BLOCK.wrapAsHolder(block))).orElse(false);
            (in ? present : missing).add(id.toString());
        }
        java.util.Collections.sort(missing);
        java.util.Collections.sort(present);

        ModularMachineryReborn.LOGGER.info("[{}] mineability self-check: minecraft:mineable/pickaxe has {} entries;"
                        + " our blocks in it: {}; NOT in it: {}",
                ModularMachineryReborn.MOD_ID, size, present, missing);
        if (!missing.isEmpty()) {
            ModularMachineryReborn.LOGGER.warn("[{}] mineability self-check: {} of this mod's blocks are missing"
                            + " from the pickaxe tag and will be mined at one fifth speed and drop nothing: {}",
                    ModularMachineryReborn.MOD_ID, missing.size(), missing);
        }
    }

    /**
     * Whether this block belongs to the mod, in either namespace. The {@code modularcontroller} namespace is a
     * compatibility namespace this mod registers, so its blocks count too.
     */
    private static boolean isOurs(ResourceLocation id, Block block) {
        return id.getNamespace().equals(ModularMachineryReborn.MOD_ID)
                || id.getNamespace().equals(MocNamespace.NAMESPACE);
    }
}
