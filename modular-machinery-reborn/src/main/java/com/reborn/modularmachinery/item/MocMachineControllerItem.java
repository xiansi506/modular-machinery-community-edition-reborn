package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The item form of a controller in the {@code modularcontroller} compatibility namespace.
 *
 * <p>It is the ordinary {@link MachineControllerItem} with one addition, which is where the original put it too:
 * the two deprecation lines. The original showed them from the <b>block</b>'s
 * {@code addInformation} ({@code BlockController.java:116-122}), guarded by the block's own registry namespace
 * being {@code modularcontroller}. In 1.20.1 a block's tooltip is its {@code BlockItem}'s
 * {@code appendHoverText}, so the same rule lives here, on the item the MOC block is registered with — and,
 * like the original, it is a property of the item class rather than a namespace lookup at render time.
 *
 * <p>{@link ModConfig#disableMocDeprecatedTip()} is read when the tooltip is built, which is long after Forge
 * has loaded the config, so unlike {@link ModConfig#mocCompatibleMode()} this key really is live.
 *
 * <p>The two keys are the original's own, verbatim:
 * {@code assets/modularmachinery/lang/zh_CN.lang:316-317}. Nothing here hardcodes the wording — a translation
 * key with no entry shows as its raw key, which is why both files carry both entries and the acceptance harness
 * asserts that they do.
 */
public final class MocMachineControllerItem extends MachineControllerItem {

    public MocMachineControllerItem(Block block, @Nullable ResourceLocation boundMachine, Properties properties) {
        super(block, boundMachine, properties);
    }

    /**
     * The original's two lines, in the original's order, unless the pack author silenced them
     * ({@code disable-moc-deprecated-tip}).
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ModConfig.disableMocDeprecatedTip()) {
            return;
        }
        tooltip.add(Component.translatable("tile.modularmachinery.machinecontroller.deprecated.tip.0"));
        tooltip.add(Component.translatable("tile.modularmachinery.machinecontroller.deprecated.tip.1"));
    }
}
