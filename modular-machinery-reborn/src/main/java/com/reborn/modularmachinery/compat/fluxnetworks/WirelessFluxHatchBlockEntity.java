package com.reborn.modularmachinery.compat.fluxnetworks;

import com.reborn.modularmachinery.block.*;
import com.reborn.modularmachinery.compat.energy.OptionalEnergy;
import com.reborn.modularmachinery.config.HatchConfig;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.energy.*;
import com.reborn.modularmachinery.item.PropertyBlockItem;
import com.reborn.modularmachinery.machine.*;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.device.*;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import javax.annotation.Nullable;
import java.util.UUID;

/** A real Flux device, sharing one native long buffer with the machine's recipe port. */
public abstract class WirelessFluxHatchBlockEntity extends TileFluxDevice
        implements WirelessEnergyHatch, MachineInputProvider {
    private static final String COLOR = "mmcerMachineColor";
    private static final String RATE = "mmcerTransferRate";
    private final HatchKind kind;
    private final long rate;
    private long displayedRate;
    private final LongEnergyStorage storage;
    private final EnergyPort port;
    private final WirelessFluxTransfer transfer;
    private LazyOptional<IEnergyStorage> energy;
    private OptionalEnergy.Handle optionalEnergy;
    @Nullable private Integer color;
    private long lastTick = Long.MIN_VALUE;
    private boolean unloaded;

    protected WirelessFluxHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, HatchKind kind) {
        super(type, pos, state);
        this.kind = kind;
        HatchTier tier = ((MachineHatchBlock) state.getBlock()).tier(state);
        rate = HatchConfig.energyHatchTransferLong(tier);
        displayedRate = rate;
        storage = new LongEnergyStorage(HatchConfig.energyHatchCapacityLong(tier), rate, rate, this::energyChanged);
        port = new EnergyPort(storage, kind.isInput());
        energy = LazyOptional.of(() -> port);
        transfer = new WirelessFluxTransfer(storage, kind.isInput(), this::active, () -> rate,
                () -> {
                    var points = getNetwork().getLogicalDevices(FluxNetwork.POINT);
                    return points == null ? 0 : points.size();
                }, this::pruneUnloadedConnection);
    }
    private boolean active() {
        return ModConfig.enableFluxNetworksIntegration() && !unloaded && isChunkLoaded() && getNetwork().isValid()
                && getNetwork().getLogicalDevices(FluxNetwork.ANY) != null;
    }
    private void pruneUnloadedConnection() {
        // Flux's removal API cannot cancel an addition until it enters the logical list. A block removed
        // between the initial join and the first network tick needs this second removal opportunity.
        if ((unloaded || isRemoved()) && getNetwork().isValid())
            getNetwork().enqueueConnectionRemoval(this, unloaded && !isRemoved());
    }
    private void energyChanged() {
        if (transfer != null) transfer.refresh();
        setChanged();
        if (level != null && !level.isClientSide && level.hasChunkAt(worldPosition)) {
            markEnergyChanged();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    @Override public LongEnergyStorage energyStorage() { return storage; }
    @Override public WirelessFluxTransfer getTransferHandler() { return transfer; }
    @Override public FluxDeviceType getDeviceType() { return kind.isInput() ? FluxDeviceType.POINT : FluxDeviceType.PLUG; }
    @Override public long getMaxTransferLimit() { return level != null && level.isClientSide ? displayedRate : rate; }
    @Override public ItemStack getDisplayStack() { return PropertyBlockItem.stackForState(getBlockState()); }
    @Override public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(kind.itemTranslationKey(
                ((MachineHatchBlock) getBlockState().getBlock()).tier(getBlockState())));
    }
    @Override public boolean isChunkLoaded() {
        return level != null && !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
    }
    @Override public boolean connect(FluxNetwork network) {
        forgetDeletedNetwork();
        // Do not abandon a still-queued addition: Flux cannot cancel it before its first network cycle.
        // Native menus can retry once that cycle has completed, without leaving a device in two networks.
        FluxNetwork previous = getNetwork();
        if (previous != network && previous.isValid() && !previous.getLogicalDevices(FluxNetwork.ANY).contains(this))
            return false;
        return (!network.isValid() || ModConfig.enableFluxNetworksIntegration()) && super.connect(network);
    }
    @Override public void onLoad() { super.onLoad(); unloaded = false; lastTick = Long.MIN_VALUE; }
    @Override public void onChunkUnloaded() { unloaded = true; forgetDeletedNetwork(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { forgetDeletedNetwork(); super.setRemoved(); }
    private void forgetDeletedNetwork() {
        if (!getNetwork().isValid() || getNetwork().getLogicalDevices(FluxNetwork.ANY) != null) return;
        // Flux 7.2.1.15 clears a deleted network's logical lists, but pending additions never receive its
        // disconnect callback. Its public connect API would dereference those cleared lists. Only detach
        // this stale pointer; normal bindings/permissions continue through the native connect implementation.
        DeletedNetworkPointer.clear(this);
        CompoundTag saved = new CompoundTag();
        writeCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        saved.putInt(FluxConstants.NETWORK_ID, FluxConstants.INVALID_NETWORK_ID);
        super.readCustomTag(saved, FluxConstants.NBT_SAVE_ALL);
        transfer.onNetworkChanged();
        mFlags |= FLAG_SETTING_CHANGED;
    }
    private static final class DeletedNetworkPointer {
        private static final java.lang.reflect.Field NETWORK;
        static {
            try {
                NETWORK = TileFluxDevice.class.getDeclaredField("mNetwork");
                NETWORK.setAccessible(true);
            } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
        }
        static void clear(WirelessFluxHatchBlockEntity tile) {
            try { NETWORK.set(tile, FluxNetwork.INVALID); }
            catch (IllegalAccessException e) { throw new IllegalStateException("Cannot detach deleted Flux network", e); }
        }
    }
    @Override public void serverTick() {
        if (level == null || level.isClientSide || !isChunkLoaded() || lastTick == level.getGameTime()) return;
        lastTick = level.getGameTime();
        forgetDeletedNetwork();
        transfer.refresh();
        super.onServerTick();
    }
    @Override public void openNetwork(Player player) { onPlayerInteract(player); }
    @Override public void setOwner(UUID owner) { setOwnerUUID(owner); }
    @Override public Object inputIdentity() { return this; }
    @Override public void appendInputs(HatchCollection.Builder builder, String selectorTag, GroupAllocator groups) {
        builder.add(kind, null, null, storage, selectorTag, -1);
    }
    @Override @Nullable public Integer machineColor() { return color; }
    @Override public void setMachineColor(@Nullable Integer value) {
        if (java.util.Objects.equals(color, value)) return;
        color = value;
        setChanged();
        if (level != null && !level.isClientSide) sendBlockUpdate();
    }
    @Override public int networkColor() { return mClientColor; }
    @Override public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_UPDATE) {
            if (color != null) tag.putInt(COLOR, color);
            tag.putLong(RATE, rate);
        }
    }
    @Override public void readCustomTag(CompoundTag tag, byte type) {
        if (type == FluxConstants.NBT_TILE_SETTINGS) { super.readCustomTag(tag, type); return; }
        // Temporary preview entities and older/partial saves need not contain a Flux owner UUID.
        CompoundTag safe = tag.copy();
        if (!safe.hasUUID(FluxConstants.PLAYER_UUID)) safe.putUUID(FluxConstants.PLAYER_UUID, Util.NIL_UUID);
        if (!safe.contains(FluxConstants.NETWORK_ID)) safe.putInt(FluxConstants.NETWORK_ID, FluxConstants.INVALID_NETWORK_ID);
        String name = safe.getString(FluxConstants.CUSTOM_NAME);
        if (name.length() > MAX_CUSTOM_NAME_LENGTH) safe.putString(FluxConstants.CUSTOM_NAME, name.substring(0, MAX_CUSTOM_NAME_LENGTH));
        super.readCustomTag(safe, type);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_UPDATE) {
            color = safe.contains(COLOR) ? safe.getInt(COLOR) : null;
            displayedRate = safe.contains(RATE) ? Math.max(0, safe.getLong(RATE)) : rate;
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (isRemoved()) return LazyOptional.empty();
        if (capability == ForgeCapabilities.ENERGY) return energy.cast();
        if (optionalEnergy == null) optionalEnergy = new OptionalEnergy.Handle(port);
        LazyOptional<?> extra = optionalEnergy.get(capability);
        return extra == null ? super.getCapability(capability, side) : extra.cast();
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); energy.invalidate();
        if (optionalEnergy != null) { optionalEnergy.invalidate(); optionalEnergy = null; }
    }
    @Override public void reviveCaps() {
        super.reviveCaps(); energy = LazyOptional.of(() -> port);
    }

    public static final class Input extends WirelessFluxHatchBlockEntity implements IFluxPoint {
        public Input(BlockPos pos, BlockState state) {
            super(ModBlocks.WIRELESS_ENERGY_INPUT_ENTITY.get(), pos, state, HatchKind.WIRELESS_ENERGY_INPUT);
        }
    }
    public static final class Output extends WirelessFluxHatchBlockEntity implements IFluxPlug {
        public Output(BlockPos pos, BlockState state) {
            super(ModBlocks.WIRELESS_ENERGY_OUTPUT_ENTITY.get(), pos, state, HatchKind.WIRELESS_ENERGY_OUTPUT);
        }
    }
}
