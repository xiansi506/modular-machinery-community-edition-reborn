package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.block.HatchTier;
import com.reborn.modularmachinery.block.MachineHatchBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayList;

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

    /** Forge's ordinary block-item map has only one slot per block, not one per variant. */
    private static final Map<Block, List<PropertyBlockItem>> VARIANTS = new IdentityHashMap<>();

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
    public void registerBlocks(Map<Block, Item> blockToItem, Item item) {
        super.registerBlocks(blockToItem, item);
        synchronized (VARIANTS) {
            List<PropertyBlockItem> variants = VARIANTS.computeIfAbsent(getBlock(), block -> new ArrayList<>());
            if (!variants.contains(this)) variants.add(this);
        }
    }

    @Override
    public void removeFromBlockToItemMap(Map<Block, Item> blockToItem, Item item) {
        super.removeFromBlockToItemMap(blockToItem, item);
        synchronized (VARIANTS) {
            List<PropertyBlockItem> variants = VARIANTS.get(getBlock());
            if (variants != null) {
                variants.remove(this);
                if (variants.isEmpty()) VARIANTS.remove(getBlock());
            }
        }
    }

    /** Shared material/icon lookup. Do not synthesize block-entity or inventory NBT from a structure rule. */
    public static ItemStack stackForState(@Nullable BlockState state) {
        if (state == null || state.isAir()) return ItemStack.EMPTY;
        synchronized (VARIANTS) {
            List<PropertyBlockItem> variants = VARIANTS.get(state.getBlock());
            if (variants != null) {
                for (PropertyBlockItem variant : variants) {
                    if (state.hasProperty(variant.property) && state.getValue(variant.property).equals(variant.value))
                        return new ItemStack(variant);
                }
                // An unsupported tier must not silently consume whichever item registered last.
                return ItemStack.EMPTY;
            }
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
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
            if (hatch.kind().isWirelessEnergy()) tooltip.add(Component.translatable(
                    hatch.kind().isInput() ? "tooltip.modular_machinery_reborn.wireless_input"
                            : "tooltip.modular_machinery_reborn.wireless_output").withStyle(ChatFormatting.GRAY));
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
