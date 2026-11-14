package com.reborn.modularmachinery.block;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/** Maintains the ME bus's fluid pool and sends outputs back to the network. */
public final class MeFluidBusLogic {

    /** Network moves return the amount actually transferred, in mB. */
    public interface Storage {
        int insert(FluidStack stack);

        default int extract(Fluid fluid, int maxAmount) {
            return 0;
        }

        default int extract(FluidStack prototype, int maxAmount) {
            return prototype.hasTag() ? 0 : extract(prototype.getFluid(), maxAmount);
        }
    }

    private MeFluidBusLogic() {
    }

    /** Compatibility entry point for callers whose filters have no NBT. */
    public static boolean tickInput(MeFluidTanks tanks, Fluid[] marks, int[] amounts, Storage storage,
                                    int[] failures, boolean fullCheck) {
        FluidStack[] prototypes = new FluidStack[marks.length];
        for (int slot = 0; slot < marks.length; slot++) {
            if (marks[slot] != null && amounts[slot] > 0) {
                prototypes[slot] = new FluidStack(marks[slot], amounts[slot]);
            }
        }
        return tickInput(tanks, prototypes, storage, failures, fullCheck);
    }

    /** The sixteen mark slots are independent of the nine shared tanks. */
    public static boolean tickInput(MeFluidTanks tanks, FluidStack[] marks, Storage storage,
                                    int[] failures, boolean fullCheck) {
        boolean moved = pushUnmarked(tanks, storage, marks);
        List<FluidStack> visited = new ArrayList<>();
        for (int entry = 0; entry < marks.length; entry++) {
            FluidStack mark = marks[entry];
            if (mark == null || mark.isEmpty() || visited.stream().anyMatch(mark::isFluidEqual)) {
                continue;
            }
            visited.add(mark);
            if (!shouldCheck(failures, entry, fullCheck)) {
                continue;
            }
            long configured = 0;
            for (FluidStack other : marks) {
                if (other != null && mark.isFluidEqual(other)) configured += other.getAmount();
            }
            int target = (int) Math.min(configured, tanks.totalCapacity());
            int current = held(tanks, mark);
            if (current < target) {
                int missing = target - current;
                int capacity = tanks.fill(new FluidStack(mark, missing), IFluidHandler.FluidAction.SIMULATE);
                int extracted = capacity > 0 ? Math.max(0, Math.min(capacity, storage.extract(mark.copy(), capacity))) : 0;
                if (extracted > 0) {
                    tanks.fill(new FluidStack(mark, extracted), IFluidHandler.FluidAction.EXECUTE);
                    moved = true;
                }
                if (extracted < missing && entry < failures.length) failures[entry]++;
            } else if (current > target) {
                int excess = current - target;
                int pushed = pushBack(tanks, storage, mark, excess);
                moved |= pushed > 0;
                if (pushed < excess && entry < failures.length) failures[entry]++;
            }
        }
        return moved;
    }

    /** Output buses leave anything the network cannot accept in its original tank. */
    public static boolean tickOutput(MeFluidTanks tanks, Storage storage, int[] failures, boolean fullCheck) {
        boolean moved = false;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            if (!shouldCheck(failures, slot, fullCheck)) {
                continue;
            }
            FluidTank tank = tanks.tank(slot);
            FluidStack held = tank.getFluid();
            if (held.isEmpty()) {
                continue;
            }
            moved |= pushTank(tank, storage, held.getAmount()) > 0;
            if (!tank.isEmpty() && slot < failures.length) failures[slot]++;
        }
        return moved;
    }

    private static boolean shouldCheck(int[] failures, int slot, boolean fullCheck) {
        if (slot >= failures.length) return true;
        if (fullCheck) failures[slot] = 0;
        return failures[slot] == 0;
    }

    private static int held(MeFluidTanks tanks, FluidStack prototype) {
        int total = 0;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            FluidStack contents = tanks.getFluidInTank(slot);
            if (!contents.isEmpty() && prototype.isFluidEqual(contents)) {
                total += contents.getAmount();
            }
        }
        return total;
    }

    private static int pushBack(MeFluidTanks tanks, Storage storage, FluidStack prototype, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < tanks.tankCount() && remaining > 0; slot++) {
            FluidTank tank = tanks.tank(slot);
            if (prototype.isFluidEqual(tank.getFluid())) {
                remaining -= pushTank(tank, storage, Math.min(remaining, tank.getFluidAmount()));
            }
        }
        return amount - remaining;
    }

    private static boolean pushUnmarked(MeFluidTanks tanks, Storage storage, FluidStack[] marks) {
        boolean moved = false;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            FluidTank tank = tanks.tank(slot);
            FluidStack contents = tank.getFluid();
            if (contents.isEmpty()) continue;
            boolean marked = false;
            for (FluidStack mark : marks) {
                if (mark != null && contents.isFluidEqual(mark)) {
                    marked = true;
                    break;
                }
            }
            if (!marked) moved |= pushTank(tank, storage, contents.getAmount()) > 0;
        }
        return moved;
    }

    private static int pushTank(FluidTank tank, Storage storage, int requested) {
        FluidStack offered = new FluidStack(tank.getFluid(), requested);
        int inserted = Math.max(0, Math.min(requested, storage.insert(offered)));
        if (inserted > 0) tank.drain(inserted, IFluidHandler.FluidAction.EXECUTE);
        return inserted;
    }
}
