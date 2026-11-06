package com.reborn.modularmachinery.energy;

import net.minecraftforge.energy.IEnergyStorage;

public final class EnergyAccess {
    /** Opt-in receipt contract: returns credit attributable ONLY to this operation, including a throwing
     * execute. Ordinary arbitrary balance getters are not this proof and are never treated as one.
     */
    public interface TransferAccounting {
        Object transferSnapshot();
        long receivedSince(Object snapshot);
    }
    /** Only exact native buffers or explicitly receipted destinations can receive an automatic push.
     * Forge simulation/getters alone cannot distinguish write-before-throw from write-after-throw. */
    public static boolean supportsReceipts(IEnergyStorage storage) {
        if (storage instanceof EnergyPort port) return port.supportsReceipts();
        return storage != null && (storage.getClass() == net.minecraftforge.energy.EnergyStorage.class
                || storage instanceof TransferAccounting);
    }
    public static Object transferSnapshot(IEnergyStorage storage) {
        if (storage instanceof TransferAccounting accounting) return accounting.transferSnapshot();
        if (storage.getClass() == net.minecraftforge.energy.EnergyStorage.class) return stored(storage);
        throw new IllegalArgumentException("Energy destination has no operation receipt contract");
    }
    public static long receivedSince(IEnergyStorage storage, Object snapshot) {
        if (storage instanceof TransferAccounting accounting) return accounting.receivedSince(snapshot);
        if (storage.getClass() == net.minecraftforge.energy.EnergyStorage.class) return stored(storage) - (Long) snapshot;
        throw new IllegalArgumentException("Energy destination has no operation receipt contract");
    }
    private static boolean recoverable(IEnergyStorage storage) {
        return storage instanceof LongEnergyStorage || storage != null
                && storage.getClass() == net.minecraftforge.energy.EnergyStorage.class;
    }
    private static void refund(IEnergyStorage storage, long before, long debit, long credit) {
        if (stored(storage) != before - debit)
            throw new IllegalStateException("Energy source changed outside this transfer; refusing to overwrite it");
        long value = before - credit;
        if (storage instanceof LongEnergyStorage wide) wide.setStored(value);
        else ((net.minecraftforge.energy.EnergyStorage) storage).deserializeNBT(net.minecraft.nbt.IntTag.valueOf((int) value));
    }
    public static final class TransferFailure extends IllegalStateException {
        private final long debited, credited;
        TransferFailure(long debited, long credited, Throwable cause) {
            super("Energy transfer failed: debited=" + debited + ", credited="
                    + (credited < 0 ? "unknown" : credited) + "; no unproven refund", cause);
            this.debited = debited; this.credited = credited;
        }
        public long debited() { return debited; }
        /** -1 means destination credit is unknown, not zero. */
        public long credited() { return credited; }
    }
    private EnergyAccess() {}
    public static int asInt(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value)); }
    public static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    public static long stored(IEnergyStorage storage) { return storage instanceof LongEnergyAccess wide ? wide.getEnergyStoredL() : storage.getEnergyStored(); }
    public static long capacity(IEnergyStorage storage) { return storage instanceof LongEnergyAccess wide ? wide.getMaxEnergyStoredL() : storage.getMaxEnergyStored(); }
    public static long receive(IEnergyStorage storage, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long received = storage instanceof LongEnergyAccess wide ? wide.receiveEnergyL(amount, simulate) : storage.receiveEnergy(asInt(amount), simulate);
        return Math.max(0, Math.min(amount, received));
    }
    public static long extract(IEnergyStorage storage, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long extracted = storage instanceof LongEnergyAccess wide ? wide.extractEnergyL(amount, simulate) : storage.extractEnergy(asInt(amount), simulate);
        return Math.max(0, Math.min(amount, extracted));
    }
    /** One output budget. Unsupported source recovery/target receipts are refused before any debit. */
    public static long push(IEnergyStorage source, IEnergyStorage target, long limit) {
        if (!recoverable(source) || !supportsReceipts(target) || source == target || limit <= 0) return 0;
        if (!target.canReceive()) return 0;
        long before = stored(source);
        long offered = Math.min(limit, before);
        long accepted = receive(target, offered, true);
        if (accepted <= 0) return 0;
        Object receipt = transferSnapshot(target);
        long extracted = extract(source, accepted, false);
        long moved;
        try { moved = receive(target, extracted, false); }
        catch (RuntimeException | Error cause) {
            long credited = -1;
            {
                try {
                    long known = receivedSince(target, receipt);
                    if (known >= 0 && known <= extracted) {
                        credited = known;
                        if (extracted > known) refund(source, before, extracted, known);
                    } else cause.addSuppressed(new IllegalStateException("Invalid external transfer receipt=" + known));
                } catch (RuntimeException | Error accountingFailure) {
                    if (accountingFailure != cause) cause.addSuppressed(accountingFailure);
                }
            }
            throw new TransferFailure(extracted, credited, cause);
        }
        if (moved < extracted) refund(source, before, extracted, moved);
        return moved;
    }
}
