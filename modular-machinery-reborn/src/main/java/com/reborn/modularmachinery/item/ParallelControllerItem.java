package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.block.ParallelControllerBlock;
import com.reborn.modularmachinery.block.ParallelControllerTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The block item of one parallel-controller tier.
 *
 * <p>{@link PropertyBlockItem} already does the two things a per-variant item has to do here — stamp the tier
 * into the {@code type} blockstate property on placement, and report the tier's own translation key. This
 * subclass adds the one line the original printed under every tier
 * ({@code BlockParallelController.java:46-51}): the tier's ceiling, read from the same source the block entity
 * reads it from so the tooltip and the block can never disagree.
 */
public final class ParallelControllerItem extends PropertyBlockItem {

    /** The original's {@code tile.modularmachinery.blockparallelcontroller.tip}, under this project's namespace. */
    public static final String TOOLTIP_KEY = "tooltip.modular_machinery_reborn.parallel_controller";

    private final ParallelControllerTier tier;

    public ParallelControllerItem(Block block, ParallelControllerTier tier, Properties properties) {
        super(block, ParallelControllerBlock.TYPE, tier, tier.translationKey(), properties);
        this.tier = tier;
    }

    public ParallelControllerTier tier() {
        return this.tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // The original passed the formatted string straight through, colour codes and all; so does this.
        tooltip.add(Component.translatable(TOOLTIP_KEY, this.tier.maxParallelism()));
    }
}
