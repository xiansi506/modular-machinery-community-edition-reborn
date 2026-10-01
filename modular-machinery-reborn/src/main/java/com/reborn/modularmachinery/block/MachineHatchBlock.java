package com.reborn.modularmachinery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

import java.util.List;

/**
 * One block per hatch family, with the tier carried by a {@code size} blockstate property.
 *
 * <p>This reproduces the original mod's layout: it registered a single block per family
 * ({@code blockinputbus}, {@code blockenergyoutputhatch}, …) and exposed the seven or eight tiers as
 * item variants of that one block, instead of registering a separate block per tier.
 *
 * <p>The three nested classes exist because {@link Block#createBlockStateDefinition} runs inside the
 * {@code Block} constructor, before any instance field is assigned — so the size property has to be a
 * static constant of a concrete class rather than a constructor argument.
 *
 * <p>Each hatch has its own screen, reached by right-clicking: item hatches show the tier's slots, fluid and
 * energy hatches show the tank or buffer as a bar. The window is always 176×166.
 */
public abstract class MachineHatchBlock extends Block implements EntityBlock {

    private final HatchKind kind;

    protected MachineHatchBlock(HatchKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public HatchKind kind() {
        return this.kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineHatchBlockEntity(pos, state);
    }

    /** The tier this block currently carries. */
    public HatchTier tier(BlockState state) {
        return state.getValue(sizeProperty());
    }

    /** The family's {@code size} property. Safe to call during construction in the nested classes. */
    public abstract EnumProperty<HatchTier> sizeProperty();

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // The original opened the hatch's own screen on right-click; the screen is what shows the tier's slots
        // or the tank's contents, so there is no separate chat message any more.
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (level.getBlockEntity(pos) instanceof MachineHatchBlockEntity hatch) {
                net.minecraftforge.network.NetworkHooks.openScreen(
                        (net.minecraft.server.level.ServerPlayer) player, hatch, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The capacity a tier grants for a family, e.g. {@code 32 slots} or {@code 32000 mB}. */
    public static Component capacityComponent(HatchKind kind, HatchTier tier) {
        return switch (kind.family()) {
            case ITEM -> Component.translatable("tooltip.modular_machinery_reborn.hatch.slots", tier.itemSlots());
            case FLUID -> Component.translatable("tooltip.modular_machinery_reborn.hatch.tank", tier.fluidCapacity());
            case ENERGY -> Component.translatable("tooltip.modular_machinery_reborn.hatch.energy",
                    tier.energyCapacity(), tier.energyTransfer());
        };
    }

    private static EnumProperty<HatchTier> propertyFor(List<HatchTier> ladder) {
        return EnumProperty.create("size", HatchTier.class, ladder.toArray(new HatchTier[0]));
    }

    /** Hatches whose ladder is {@code ItemBusSize}: seven tiers, no ultimate. */
    public static final class ItemPort extends MachineHatchBlock {
        public static final EnumProperty<HatchTier> SIZE = propertyFor(HatchTier.ITEM_LADDER);

        public ItemPort(HatchKind kind, Properties properties) {
            super(kind, properties);
            registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.ITEM_LADDER.get(0)));
        }

        @Override
        public EnumProperty<HatchTier> sizeProperty() {
            return SIZE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE);
        }
    }

    /** Hatches whose ladder is {@code FluidHatchSize}: the item ladder plus vacuum. */
    public static final class FluidPort extends MachineHatchBlock {
        public static final EnumProperty<HatchTier> SIZE = propertyFor(HatchTier.FLUID_LADDER);

        public FluidPort(HatchKind kind, Properties properties) {
            super(kind, properties);
            registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.FLUID_LADDER.get(0)));
        }

        @Override
        public EnumProperty<HatchTier> sizeProperty() {
            return SIZE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE);
        }
    }

    /** Hatches whose ladder is {@code EnergyHatchData}: the item ladder plus ultimate. */
    public static final class EnergyPort extends MachineHatchBlock {
        public static final EnumProperty<HatchTier> SIZE = propertyFor(HatchTier.ENERGY_LADDER);

        public EnergyPort(HatchKind kind, Properties properties) {
            super(kind, properties);
            registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.ENERGY_LADDER.get(0)));
        }

        @Override
        public EnumProperty<HatchTier> sizeProperty() {
            return SIZE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE);
        }
    }
}
