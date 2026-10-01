package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.block.UpgradeBusBlock;
import com.reborn.modularmachinery.block.UpgradeBusTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The block item of one upgrade-bus tier.
 *
 * <p>{@link PropertyBlockItem} does the two things a per-variant item has to do here — stamp the tier into the
 * {@code type} blockstate property on placement, and report the tier's own translation key. This subclass adds
 * the one line the original printed under every tier ({@code BlockUpgradeBus.java:50-54}): how many upgrades the
 * tier can hold, read from the same source the block entity sizes its inventory from, so the tooltip and the
 * block can never disagree.
 */
public final class UpgradeBusItem extends PropertyBlockItem {

    /** The original's {@code tile.modularmachinery.blockupgradebus.tip}, under this project's namespace. */
    public static final String TOOLTIP_KEY = "tooltip.modular_machinery_reborn.upgrade_bus";

    private final UpgradeBusTier tier;

    public UpgradeBusItem(Block block, UpgradeBusTier tier, Properties properties) {
        super(block, UpgradeBusBlock.TYPE, tier, tier.translationKey(), properties);
        this.tier = tier;
    }

    public UpgradeBusTier tier() {
        return this.tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // The original passed the formatted string straight through, colour codes and all; so does this.
        tooltip.add(Component.translatable(TOOLTIP_KEY, this.tier.maxUpgradeSlots()));
    }
}
