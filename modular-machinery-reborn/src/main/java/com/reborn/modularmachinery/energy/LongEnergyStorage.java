package com.reborn.modularmachinery.energy;

import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.energy.EnergyStorage;

/** Long-backed storage with a clamped FE view and an exact long view of the same energy. */
public final class LongEnergyStorage extends EnergyStorage implements LongEnergyAccess, EnergyAccess.TransferAccounting {
    private final long capacityL, receiveL, extractL;
    private final Runnable changed;
    private long stored;
    private long received;
    @Override public Object transferSnapshot() { return received; }
    @Override public long receivedSince(Object snapshot) { return received - (Long) snapshot; }
    public LongEnergyStorage(long capacity, long receive, long extract, Runnable changed) {
        super(0, 0, 0, 0);
        capacityL = Math.max(1, capacity); receiveL = Math.max(0, receive); extractL = Math.max(0, extract);
        this.changed = changed;
    }
    @Override public long receiveEnergyL(long amount, boolean simulate) {
        long moved = Math.max(0, Math.min(amount, Math.min(receiveL, capacityL - stored)));
        if (!simulate && moved > 0) { stored += moved; received += moved; changed.run(); }
        return moved;
    }
    @Override public long extractEnergyL(long amount, boolean simulate) {
        long moved = Math.max(0, Math.min(amount, Math.min(extractL, stored)));
        if (!simulate && moved > 0) { stored -= moved; changed.run(); }
        return moved;
    }
    @Override public long getEnergyStoredL() { return stored; }
    @Override public long getMaxEnergyStoredL() { return capacityL; }
    public long maxReceiveL() { return receiveL; }
    public long maxExtractL() { return extractL; }
    @Override public int receiveEnergy(int amount, boolean simulate) { return LongEnergyAccess.super.receiveEnergy(amount, simulate); }
    @Override public int extractEnergy(int amount, boolean simulate) { return LongEnergyAccess.super.extractEnergy(amount, simulate); }
    @Override public int getEnergyStored() { return EnergyAccess.asInt(stored); }
    @Override public int getMaxEnergyStored() { return EnergyAccess.asInt(capacityL); }
    @Override public boolean canExtract() { return extractL > 0; }
    @Override public boolean canReceive() { return receiveL > 0; }
    public void setStored(long value) {
        long next = Math.max(0, Math.min(capacityL, value));
        if (next != stored) { stored = next; changed.run(); }
    }
    /** Brandon's Core specifies the absolute amount changed, including for negative requests. */
    public long modify(long amount) {
        long moved = amount >= 0 ? Math.min(amount, capacityL - stored)
                : Math.min(amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount, stored);
        if (moved > 0) { stored += amount >= 0 ? moved : -moved; changed.run(); }
        return moved;
    }
    @Override public Tag serializeNBT() { return LongTag.valueOf(stored); }
    @Override public void deserializeNBT(Tag tag) { setStored(tag instanceof NumericTag value ? value.getAsLong() : 0); }
}
