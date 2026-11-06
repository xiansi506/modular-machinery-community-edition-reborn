package com.reborn.modularmachinery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

public final class WirelessEnergyHatchBlock extends MachineHatchBlock {
    public static final EnumProperty<HatchTier> SIZE = EnumProperty.create("size", HatchTier.class,
            HatchTier.ENERGY_LADDER.toArray(new HatchTier[0]));

    public WirelessEnergyHatchBlock(HatchKind kind, Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.ENERGY_LADDER.get(0)));
    }
    @Override public EnumProperty<HatchTier> sizeProperty() { return SIZE; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.reborn.modularmachinery.compat.fluxnetworks.FluxHatchRegistry.create(pos, state, kind().isInput());
    }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (world, pos, blockState, entity) -> {
            if (entity instanceof WirelessEnergyHatch hatch) hatch.serverTick();
        };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND
                && level.getBlockEntity(pos) instanceof WirelessEnergyHatch hatch) hatch.openNetwork(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                       @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof WirelessEnergyHatch hatch)
            hatch.setOwner(placer.getUUID());
    }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WirelessEnergyHatch hatch
                ? ComparatorLevels.energy(hatch.energyStorage()) : 0;
    }
}
