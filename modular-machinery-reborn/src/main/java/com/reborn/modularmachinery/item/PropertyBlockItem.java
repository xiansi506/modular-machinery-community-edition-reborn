package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.block.HatchTier;
import com.reborn.modularmachinery.block.MachineHatchBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Block item for the "one block, many variants" families of the original mod.
 *
 * <p>The original carried the variant in item metadata, which no longer exists in 1.20.1, so the item has to
 * stamp the blockstate property on placement. Two things therefore have to be overridden:
 *
 * <ul>
 *   <li>{@link #getPlacementState} writes the variant into the blockstate property;</li>
 *   <li>{@link #getDescriptionId} — {@code BlockItem} delegates it to {@code Block.getDescriptionId()}, so
 *       without this override every variant of a family would resolve to the same family translation key and
 *       could not be named individually. This is what the per-variant language entries key on. The family key
 *       is still shipped for the block's own description id.</li>
 * </ul>
 */
public class PropertyBlockItem extends BlockItem {

    private final String descriptionId;
    private final EnumProperty<?> property;
    private final Comparable<?> value;

    public <T extends Enum<T> & net.minecraft.util.StringRepresentable> PropertyBlockItem(
            Block block, EnumProperty<T> property, T value, String descriptionId, Properties properties) {
        super(block, properties);
        this.descriptionId = descriptionId;
        this.property = property;
        this.value = value;
    }

    /** Translation key this variant reports. */
    public String translationKey() {
        return this.descriptionId;
    }

    /** The variant value this item places. */
    public Comparable<?> variantValue() {
        return this.value;
    }

    @Override
    public String getDescriptionId() {
        return this.descriptionId;
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : applyVariant(state);
    }

    /**
     * Hatch items report the capacity their tier grants, which makes the tier ladder checkable in game
     * (a ludicrous item bus really holds 32 slots, a vacuum fluid hatch 32,000 mB).
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (this.value instanceof HatchTier tier && this.getBlock() instanceof MachineHatchBlock hatch) {
            tooltip.add(MachineHatchBlock.capacityComponent(hatch.kind(), tier).copy()
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BlockState applyVariant(BlockState state) {
        if (!state.hasProperty(this.property)) {
            return state;
        }
        return state.setValue((EnumProperty) this.property, (Comparable) this.value);
    }
}
