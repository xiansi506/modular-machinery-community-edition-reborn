package com.reborn.modularmachinery.energy;

import net.minecraftforge.energy.IEnergyStorage;

/** External hatch direction; recipes use the backing buffer directly. */
public final class EnergyPort implements LongEnergyAccess, EnergyAccess.TransferAccounting {
    private final IEnergyStorage storage;
    private final boolean input;
    public EnergyPort(IEnergyStorage storage, boolean input) { this.storage = storage; this.input = input; }
    public boolean supportsReceipts() { return EnergyAccess.supportsReceipts(storage); }
    @Override public Object transferSnapshot() { return EnergyAccess.transferSnapshot(storage); }
    @Override public long receivedSince(Object snapshot) { return EnergyAccess.receivedSince(storage, snapshot); }
    @Override public long receiveEnergyL(long amount, boolean simulate) { return canReceive() ? EnergyAccess.receive(storage, amount, simulate) : 0; }
    @Override public long extractEnergyL(long amount, boolean simulate) { return canExtract() ? EnergyAccess.extract(storage, amount, simulate) : 0; }
    @Override public long getEnergyStoredL() { return EnergyAccess.stored(storage); }
    @Override public long getMaxEnergyStoredL() { return EnergyAccess.capacity(storage); }
    @Override public boolean canReceive() { return input && storage.canReceive(); }
    @Override public boolean canExtract() { return !input && storage.canExtract(); }
    public long maxReceiveL() { return !canReceive() ? 0 : storage instanceof LongEnergyStorage nativeStorage ? nativeStorage.maxReceiveL() : Integer.MAX_VALUE; }
    public long maxExtractL() { return !canExtract() ? 0 : storage instanceof LongEnergyStorage nativeStorage ? nativeStorage.maxExtractL() : Integer.MAX_VALUE; }
    public long modify(long delta) {
        if (storage instanceof LongEnergyStorage nativeStorage) return nativeStorage.modify(delta);
        return delta >= 0 ? EnergyAccess.receive(storage, delta, false)
                : EnergyAccess.extract(storage, delta == Long.MIN_VALUE ? Long.MAX_VALUE : -delta, false);
    }
}
