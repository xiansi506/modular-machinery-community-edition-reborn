package com.reborn.modularmachinery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Storage behind one hatch block.
 *
 * <p>This is where M3 puts the port capability layer. In the original mod the <b>hatches</b> owned the storage
 * and exposed the Forge capabilities, while the controller exposed none — a machine's inventory, tank and
 * energy buffer were simply the sum of its hatches. That is what this reproduces, and it is why an item bus of
 * tier {@code ludicrous} really does hold 32 slots and a {@code vacuum} fluid hatch really does hold 32,000 mB.
 *
 * <p>One block entity type serves all six families: the family and tier are read from the blockstate, which is
 * possible because each family is a single block with a {@code size} property. Capacity comes from
 * {@link HatchTier}, which carries the original {@code ItemBusSize} / {@code FluidHatchSize} /
 * {@code EnergyHatchData} numbers.
 */
public class MachineHatchBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    private static final String TAG_ITEMS = "items";
    private static final String TAG_TANK = "tank";
    private static final String TAG_ENERGY = "energy";

    private final HatchKind kind;
    private final HatchTier tier;

    @Nullable private final ItemStackHandler items;
    @Nullable private final FluidTank tank;
    @Nullable private final EnergyStorage energy;

    private final LazyOptional<ItemStackHandler> itemCapability;
    private final LazyOptional<FluidTank> fluidCapability;
    private final LazyOptional<EnergyStorage> energyCapability;

    public MachineHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HATCH_ENTITY.get(), pos, state);
        this.kind = state.getBlock() instanceof MachineHatchBlock hatch ? hatch.kind() : HatchKind.ITEM_INPUT;
        this.tier = state.getValue(this.kind.sizeProperty());

        // Locals first: javac's definite-assignment analysis does not treat a statement switch that covers
        // every enum constant as exhaustive when the selector is a method call, so it cannot prove the final
        // fields are assigned after the switch.
        ItemStackHandler itemStorage = null;
        FluidTank fluidStorage = null;
        EnergyStorage energyStorage = null;

        switch (this.kind.family()) {
            case ITEM -> {
                itemStorage = new ItemStackHandler(Math.max(1, this.tier.itemSlots())) {
                    @Override protected void onContentsChanged(int slot) { onStorageChanged(false); }
                };
            }
            case FLUID -> {
                fluidStorage = new FluidTank(Math.max(1, this.tier.fluidCapacity())) {
                    // The screen renders the fluid's own texture, so the client needs to know which fluid it is.
                    @Override protected void onContentsChanged() { onStorageChanged(true); }
                };
            }
            case ENERGY -> {
                int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, this.tier.energyCapacity()));
                int transfer = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, this.tier.energyTransfer()));
                energyStorage = new EnergyStorage(capacity, transfer, transfer, 0) {
                    @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                        int received = super.receiveEnergy(maxReceive, simulate);
                        if (received > 0 && !simulate) { onStorageChanged(false); }
                        return received;
                    }
                    @Override public int extractEnergy(int maxExtract, boolean simulate) {
                        int extracted = super.extractEnergy(maxExtract, simulate);
                        if (extracted > 0 && !simulate) { onStorageChanged(false); }
                        return extracted;
                    }
                };
            }
        }

        this.items = itemStorage;
        this.tank = fluidStorage;
        this.energy = energyStorage;
        this.itemCapability = itemStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> this.items);
        this.fluidCapability = fluidStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> this.tank);
        this.energyCapability = energyStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> this.energy);
    }

    public HatchKind kind() {
        return this.kind;
    }

    public HatchTier tier() {
        return this.tier;
    }

    /** The item storage, or {@code null} for fluid and energy hatches. */
    @Nullable
    public ItemStackHandler itemHandler() {
        return this.items;
    }

    /** The fluid tank, or {@code null} for item and energy hatches. */
    @Nullable
    public FluidTank fluidTank() {
        return this.tank;
    }

    /** The energy buffer, or {@code null} for item and fluid hatches. */
    @Nullable
    public EnergyStorage energyStorage() {
        return this.energy;
    }

    /** Human-readable capacity of this hatch, used by the tooltip and by hand-over notes. */
    public String capacityDescription() {
        return switch (this.kind.family()) {
            case ITEM -> this.tier.itemSlots() + " slots";
            case FLUID -> this.tier.fluidCapacity() + " mB";
            case ENERGY -> this.tier.energyCapacity() + " FE, " + this.tier.energyTransfer() + " FE/t";
        };
    }

    private void onStorageChanged(boolean syncToClient) {
        setChanged();
        if (syncToClient && this.level != null && !this.level.isClientSide) {
            // Only the fluid needs a block update: the client draws the fluid's own texture. Item slots travel
            // with the menu and the energy numbers travel in the menu's container data, so both would be waste.
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------- menu data

    /** Client-side mirror of the four numbers the hatch screen shows. */
    private int clientEnergyStored;
    private int clientEnergyCapacity;
    private int clientFluidAmount;
    private int clientFluidCapacity;

    /**
     * One of the four values the hatch screen displays, in the order the menu's container data uses them.
     *
     * <p>The server answers from the live storage; the client answers from the mirrored values, which the menu
     * writes through {@link #setMenuValue}. This is the same arrangement vanilla furnaces use, and it exists
     * because a container data slot is the only place those numbers can live on the client.
     */
    public int menuValue(int index) {
        if (this.level == null || this.level.isClientSide) {
            return switch (index) {
                case 0 -> this.clientEnergyStored;
                case 1 -> this.clientEnergyCapacity;
                case 2 -> this.clientFluidAmount;
                case 3 -> this.clientFluidCapacity;
                default -> 0;
            };
        }
        return switch (index) {
            case 0 -> this.energy == null ? 0 : this.energy.getEnergyStored();
            case 1 -> this.energy == null ? 0 : this.energy.getMaxEnergyStored();
            case 2 -> this.tank == null ? 0 : this.tank.getFluidAmount();
            case 3 -> this.tank == null ? 0 : this.tank.getCapacity();
            default -> 0;
        };
    }

    public void setMenuValue(int index, int value) {
        switch (index) {
            case 0 -> this.clientEnergyStored = value;
            case 1 -> this.clientEnergyCapacity = value;
            case 2 -> this.clientFluidAmount = value;
            case 3 -> this.clientFluidCapacity = value;
            default -> { }
        }
    }

    /**
     * The saved state doubles as the update tag, so the client knows which fluid a fluid hatch holds and can
     * draw its texture. Only fluid hatches ask for that update.
     */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------- screen

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(this.kind.itemTranslationKey(this.tier));
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,
            net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new com.reborn.modularmachinery.menu.MachineHatchMenu(id, inventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return this.itemCapability.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return this.fluidCapability.cast();
        }
        if (capability == ForgeCapabilities.ENERGY) {
            return this.energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.itemCapability.invalidate();
        this.fluidCapability.invalidate();
        this.energyCapability.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.items != null) {
            tag.put(TAG_ITEMS, this.items.serializeNBT());
        }
        if (this.tank != null) {
            tag.put(TAG_TANK, this.tank.writeToNBT(new CompoundTag()));
        }
        if (this.energy != null) {
            tag.putInt(TAG_ENERGY, this.energy.getEnergyStored());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (this.items != null && tag.contains(TAG_ITEMS)) {
            this.items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        }
        if (this.tank != null && tag.contains(TAG_TANK)) {
            this.tank.readFromNBT(tag.getCompound(TAG_TANK));
        }
        if (this.energy != null && tag.contains(TAG_ENERGY)) {
            // EnergyStorage has no direct setter; fill it back up to the stored amount.
            this.energy.receiveEnergy(tag.getInt(TAG_ENERGY), false);
        }
    }
}
