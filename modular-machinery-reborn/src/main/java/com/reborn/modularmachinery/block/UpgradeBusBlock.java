package com.reborn.modularmachinery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The upgrade bus, one block with a {@code type} property and one block item per tier — the original's
 * {@code BlockUpgradeBus} ({@code :38-39}, one block plus {@code PropertyEnum<UpgradeBusData> BUS_TYPE}), and
 * the same shape this project already uses for hatch families, casings and the parallel controller.
 *
 * <p>Right-clicking opens its screen ({@code BlockUpgradeBus.java:156-164}).
 *
 * <p>Unlike a hatch, the bus holds nothing the recipe engine can draw from: the controller never reads its
 * slots, only the recipe modifiers its upgrades contribute. That separation is why the bus is collected by its
 * own path rather than through {@code HatchKind}.
 */
public final class UpgradeBusBlock extends Block implements EntityBlock {

    /** The original's {@code BUS_TYPE}. */
    public static final EnumProperty<UpgradeBusTier> TYPE =
            EnumProperty.create("type", UpgradeBusTier.class);

    public UpgradeBusBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, UpgradeBusTier.NORMAL));
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
        if (level.getBlockEntity(pos) instanceof UpgradeBusBlockEntity bus) {
            bus.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UpgradeBusBlockEntity(pos, state);
    }

    /**
     * The original ticked its bus to prune controllers it had lost ({@code TileUpgradeBus#doRestrictedTick},
     * {@code :66-92}); the modern equivalent is a block entity ticker, which also means the reconcile costs
     * nothing while the chunk is unloaded.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : (tickLevel, pos, tickState, entity) -> {
                    if (entity instanceof UpgradeBusBlockEntity bus) {
                        UpgradeBusBlockEntity.serverTick(tickLevel, pos, tickState, bus);
                    }
                };
    }

    /** Spill contents on actual replacement, including non-player removal, exactly once. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof UpgradeBusBlockEntity bus) {
            ItemStackDrops.pop(level, pos, bus.items());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
