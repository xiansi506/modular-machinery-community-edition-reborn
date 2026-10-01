package com.reborn.modularmachinery.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The original {@code blockcasing}: one block, six variants selected by a {@code casing} property.
 *
 * <p>Machine definitions use it heavily — {@code alloy_furnace} is almost entirely casing, and the built-in
 * variable sets {@code casings_all} / {@code casings_decorative} are casing plus hatches. It therefore has to
 * exist before the data-driven machine definitions can be ported.
 *
 * <p>Unlike the hatches there is only one casing block, so the property can be a plain static constant.
 */
public class BlockCasing extends Block {

    public static final EnumProperty<CasingType> CASING = EnumProperty.create("casing", CasingType.class);

    public BlockCasing(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CASING, CasingType.PLAIN));
    }

    public static CasingType casing(BlockState state) {
        return state.getValue(CASING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CASING);
    }
}
