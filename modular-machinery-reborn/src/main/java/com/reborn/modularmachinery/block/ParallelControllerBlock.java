package com.reborn.modularmachinery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The parallel controller, one block with a {@code type} property and one block item per tier — the same shape
 * the original used ({@code BlockParallelController.java:34-35}, one block plus
 * {@code PropertyEnum<ParallelControllerData> CONTROLLER_TYPE}), and the same shape this project already uses
 * for hatch families and casings.
 *
 * <p>Right-clicking opens its screen ({@code BlockParallelController.java:136-145}).
 *
 * <p>Unlike a hatch, the block contributes nothing to a machine's storage. It only has to be <b>at a position
 * the machine definition accepts</b>; the controller then adds
 * {@code ParallelControllerBlockEntity#parallelism()} into the ceiling. The original worked the same way: its
 * built-in machines did not list {@code modularmachinery:blockparallelcontroller} in their structure at all —
 * only {@code assembly_line} did — so a parallel controller did nothing unless the machine's definition allowed
 * it.
 */
public final class ParallelControllerBlock extends Block implements EntityBlock {

    /** The original's {@code CONTROLLER_TYPE}. */
    public static final EnumProperty<ParallelControllerTier> TYPE =
            EnumProperty.create("type", ParallelControllerTier.class);

    public ParallelControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, ParallelControllerTier.NORMAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof ParallelControllerBlockEntity controller) {
            controller.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ParallelControllerBlockEntity(pos, state);
    }

    // No ticker: the original's TileParallelController had no update() override either. The value only changes
    // when a player sets it, and that goes through the block entity directly.
}
