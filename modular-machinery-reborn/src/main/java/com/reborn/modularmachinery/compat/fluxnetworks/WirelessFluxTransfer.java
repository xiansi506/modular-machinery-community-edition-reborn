package com.reborn.modularmachinery.compat.fluxnetworks;

import com.reborn.modularmachinery.energy.LongEnergyStorage;
import net.minecraft.nbt.CompoundTag;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.connection.TransferHandler;
import java.util.function.*;

/** A Flux cycle transfers directly to/from the recipe buffer; mBuffer is only its display mirror. */
public final class WirelessFluxTransfer extends TransferHandler {
    private final LongEnergyStorage storage;
    private final boolean input;
    private final BooleanSupplier active;
    private final LongSupplier hardwareLimit;
    private final IntSupplier pointCount;
    private final Runnable beforeCycle;
    private boolean cycling;
    private long spent;

    public WirelessFluxTransfer(LongEnergyStorage storage, boolean input, BooleanSupplier active,
                                LongSupplier hardwareLimit, IntSupplier pointCount) {
        this(storage, input, active, hardwareLimit, pointCount, () -> {});
    }
    public WirelessFluxTransfer(LongEnergyStorage storage, boolean input, BooleanSupplier active,
                                LongSupplier hardwareLimit, IntSupplier pointCount, Runnable beforeCycle) {
        super(hardwareLimit.getAsLong());
        this.storage = storage;
        this.input = input;
        this.active = active;
        this.hardwareLimit = hardwareLimit;
        this.pointCount = pointCount;
        this.beforeCycle = beforeCycle;
    }
    private long budget() {
        // Flux sums point requests in a signed long. Share this extreme ceiling across logical points.
        long safeShare = Long.MAX_VALUE / Math.max(1, pointCount.getAsInt());
        return Math.min(safeShare, Math.min(Math.max(0, hardwareLimit.getAsLong()), Math.max(0, getLimit())));
    }
    private long available() { return Math.max(0, budget() - (cycling ? spent : 0)); }
    public void refresh() { mBuffer = storage.getEnergyStoredL(); }
    @Override public void onCycleStart() { beforeCycle.run(); refresh(); spent = 0; cycling = true; mChange = 0; }
    @Override public long getRequest() {
        return input && active.getAsBoolean() ? Math.min(storage.getMaxEnergyStoredL() - storage.getEnergyStoredL(), available()) : 0;
    }
    @Override public void addToBuffer(long amount) {
        // The native network has already debited the plug. Its promised request must fit in full.
        if (!cycling || amount < 0 || amount > getRequest()) throw new IllegalArgumentException("Flux transfer exceeds request");
        storage.setStored(storage.getEnergyStoredL() + amount);
        spent += amount;
        refresh();
    }
    @Override public long removeFromBuffer(long amount) {
        if (input || !cycling || !active.getAsBoolean() || amount <= 0) return 0;
        long moved = Math.min(amount, Math.min(storage.getEnergyStoredL(), available()));
        storage.setStored(storage.getEnergyStoredL() - moved);
        spent += moved;
        refresh();
        return moved;
    }
    @Override public void onCycleEnd() { mChange = input ? -spent : spent; cycling = false; refresh(); }
    @Override public void onNetworkChanged() { super.onNetworkChanged(); cycling = false; spent = 0; refresh(); }
    @Override public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type != FluxConstants.NBT_TILE_SETTINGS) tag.putLong(FluxConstants.BUFFER, storage.getEnergyStoredL());
    }
    @Override public void readCustomTag(CompoundTag tag, byte type) {
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        super.readCustomTag(tag, type);
        long receivedBuffer = Math.max(0, mBuffer);
        storage.setStored(receivedBuffer);
        setLimit(getRawLimit());
        setPriority(getRawPriority());
        cycling = false; spent = 0;
        // Client config may differ; Flux's GUI must display the exact server buffer.
        mBuffer = receivedBuffer;
    }
}
