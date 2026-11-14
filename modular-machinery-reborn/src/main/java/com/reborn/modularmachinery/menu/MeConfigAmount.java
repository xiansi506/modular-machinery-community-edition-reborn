package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.MeBusAccess;
import com.reborn.modularmachinery.block.MeFluidTanks;
import com.reborn.modularmachinery.block.MeGasStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Relative ghost-target edits, calculated from the server's current mark rather than a client snapshot. */
public final class MeConfigAmount {
    private MeConfigAmount() {}

    /** 16 slots × 8 modifier/direction combinations fit the vanilla button packet's signed byte (0..127). */
    public static int action(int slot, boolean up, boolean shift, boolean control) {
        if (slot < 0 || slot >= MeItemBusMenu.CONFIG_SLOTS) return -1;
        return slot * 8 + (up ? 4 : 0) + (control ? 2 : 0) + (shift ? 1 : 0);
    }

    public static int adjusted(long current, boolean up, boolean shift, boolean control, int limit) {
        long value = Math.max(1L, Math.min((long) limit, current));
        long next;
        if (shift && control) next = up ? (value > limit / 2L ? limit : value * 2L) : value / 2L;
        else next = value + (up ? 1L : -1L) * (control ? 100L : shift ? 10L : 1L);
        return (int) Math.max(1L, Math.min(limit, next));
    }

    public static boolean hasMark(MeBusAccess bus, int slot) {
        if (bus == null || slot < 0 || slot >= MeItemBusMenu.CONFIG_SLOTS) return false;
        ItemStack item = bus.configItemAt(slot);
        return item != null && !item.isEmpty() || bus.fluidMarkAt(slot) != null
                || bus.gasTanks() != null && bus.gasTanks().mark(slot) != null;
    }

    public static boolean apply(MeBusAccess bus, int action) {
        if (action < 0 || action >= MeItemBusMenu.CONFIG_SLOTS * 8 || bus == null) return false;
        int slot = action / 8;
        boolean up = (action & 4) != 0, shift = (action & 1) != 0, control = (action & 2) != 0;
        ItemStack item = bus.configItemAt(slot);
        if (item != null && !item.isEmpty()) {
            int next = adjusted(item.getCount(), up, shift, control, item.getMaxStackSize());
            if (next == item.getCount()) return false;
            bus.setConfigItem(slot, item.copyWithCount(next));
            return true;
        }
        var gas = bus.gasTanks() == null ? null : bus.gasTanks().mark(slot);
        if (gas != null) {
            int next = adjusted(gas.amount(), up, shift, control, MeGasStorage.SLOTS * MeGasStorage.CAPACITY);
            if (next == gas.amount()) return false;
            bus.setConfigGas(slot, gas.id(), next);
            return true;
        }
        FluidStack fluid = bus.fluidMarkStackAt(slot);
        if (!fluid.isEmpty()) {
            int current = fluid.getAmount();
            int next = adjusted(current, up, shift, control, MeFluidTanks.SLOTS * MeFluidTanks.CAPACITY);
            if (next == current) return false;
            bus.setConfigFluidStack(slot, new FluidStack(fluid, next));
            return true;
        }
        return false;
    }
}
