package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Static helpers for reading and writing the machine's ports.
 *
 * <p>Every check simulates before it commits, and every commit is all-or-nothing across the whole handler
 * list, so a craft can never half-consume its inputs. That ordering is what fixed the 0.5.0
 * resource-destruction bug and it is applied to every requirement type here.
 */
public final class IngredientIo {

    private IngredientIo() {
    }

    // ---------------------------------------------------------------- items

    /** How many items matching the ingredient are currently available across the given ports. */
    public static int count(List<IItemHandler> handlers, Ingredient ingredient) {
        int total = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** Removes exactly {@code amount} matching items, or nothing at all when they do not all fit. */
    public static boolean extract(List<IItemHandler> handlers, Ingredient ingredient, int amount) {
        if (count(handlers, ingredient) < amount) {
            return false;
        }
        int remaining = amount;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack extracted = handler.extractItem(slot, take, false);
                remaining -= extracted.getCount();
            }
        }
        return remaining == 0;
    }

    /** Whether {@code amount} copies of the stack fit across the given ports. */
    public static boolean canInsertItems(List<IItemHandler> handlers, ItemStack stack, int amount) {
        return simulateInsert(handlers, stack, amount);
    }

    public static boolean insertItems(List<IItemHandler> handlers, ItemStack stack, int amount) {
        return insertAll(handlers, List.of(new StackAmount(stack, amount)));
    }

    /**
     * Inserts several distinct (stack, amount) pairs, all or nothing: everything is simulated first, and only
     * when every pair fits is anything actually inserted.
     *
     * <p>This is the parallelism-aware form of {@link #insertItems}. Parallel item outputs each roll their own
     * chance, so one settlement can produce "3 of A and 1 of B" and has to commit both or neither — the
     * all-or-nothing property every other helper in this class keeps.
     */
    public static boolean insertAll(List<IItemHandler> handlers, List<StackAmount> inserts) {
        if (!simulateInsertAll(handlers, inserts)) {
            return false;
        }
        for (StackAmount insert : inserts) {
            int remaining = insert.amount();
            for (IItemHandler handler : handlers) {
                if (remaining <= 0) {
                    break;
                }
                for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                    ItemStack offer = insert.stack().copyWithCount(remaining);
                    ItemStack leftover = handler.insertItem(slot, offer, false);
                    remaining = leftover.getCount();
                }
            }
            if (remaining > 0) {
                // The simulation said this could not happen; leaving half the batch inserted would be worse
                // than reporting it loudly.
                throw new IllegalStateException("Item insertion disagreed with its own simulation for "
                        + insert.stack() + " x" + insert.amount());
            }
        }
        return true;
    }

    private static boolean simulateInsertAll(List<IItemHandler> handlers, List<StackAmount> inserts) {
        for (StackAmount insert : inserts) {
            if (!simulateInsert(handlers, insert.stack(), insert.amount())) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many copies of {@code stack} the given handlers could accept, as an upper bound derived from each
     * slot's own limit.
     *
     * <p>The original simulated the insert itself and divided what fitted by the per-copy amount
     * ({@code RequirementItem#insertAllItems} returns {@code inserted / toInsert}). Simulating a whole machine's
     * worth of slots is cheap, but it cannot answer "how many" more accurately than this: a slot's free space
     * for a given stack is <b>not</b> a public property of {@link IItemHandler} — only {@code insertItem} knows,
     * and it only answers for the stack it is handed. Feeding it increasing amounts to find the exact maximum
     * would turn a bounded O(slots) pass into a search over up to a machine's whole ceiling.
     *
     * <p>So this is an upper bound, computed exactly where the API allows it (an empty slot accepts
     * {@code getSlotLimit}, a slot already holding the same item accepts up to the limit) and optimistically
     * (full slot limit) where it does not. Being an upper bound is the safe direction: the exact all-or-nothing
     * check in {@link #insertAll} still decides whether the settlement happens, this only decides how many
     * copies are attempted.
     */
    public static int insertCapacity(List<IItemHandler> handlers, ItemStack stack) {
        long capacity = 0L;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                int limit = handler.getSlotLimit(slot);
                if (limit <= 0) {
                    continue;
                }
                ItemStack current = handler.getStackInSlot(slot);
                if (current.isEmpty()) {
                    capacity += limit;
                } else if (ItemStack.isSameItemSameTags(current, stack)) {
                    capacity += Math.max(0, limit - current.getCount());
                }
                if (capacity >= UNBOUNDED) {
                    return UNBOUNDED;
                }
            }
        }
        return (int) Math.min(UNBOUNDED, capacity);
    }

    /** The largest capacity this class ever reports, so callers can compare without overflow. */
    public static final int UNBOUNDED = Integer.MAX_VALUE / 4;

    /** One (stack, amount) pair of an all-or-nothing multi-insert. */
    public record StackAmount(ItemStack stack, int amount) {
    }

    private static boolean simulateInsert(List<IItemHandler> handlers, ItemStack stack, int amount) {
        int remaining = amount;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack offer = stack.copyWithCount(remaining);
                ItemStack leftover = handler.insertItem(slot, offer, true);
                remaining = leftover.getCount();
            }
        }
        return remaining == 0;
    }

    // ---------------------------------------------------------------- fluids

    public static int countFluid(List<IFluidHandler> handlers, FluidStack fluid) {
        int total = 0;
        for (IFluidHandler handler : handlers) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stored = handler.getFluidInTank(tank);
                if (!stored.isEmpty() && stored.isFluidEqual(fluid)) {
                    total += stored.getAmount();
                }
            }
        }
        return total;
    }

    public static boolean drainFluid(List<IFluidHandler> handlers, FluidStack fluid, int amount) {
        if (countFluid(handlers, fluid) < amount) {
            return false;
        }
        int remaining = amount;
        for (IFluidHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            FluidStack drained = handler.drain(new FluidStack(fluid, remaining), IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
        }
        return remaining == 0;
    }

    public static boolean canFillFluid(List<IFluidHandler> handlers, FluidStack fluid, int amount) {
        return simulateFill(handlers, fluid, amount);
    }

    public static boolean fillFluid(List<IFluidHandler> handlers, FluidStack fluid, int amount) {
        int remaining = amount;
        for (IFluidHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            int accepted = handler.fill(new FluidStack(fluid, remaining), IFluidHandler.FluidAction.EXECUTE);
            remaining -= accepted;
        }
        return remaining == 0;
    }

    private static boolean simulateFill(List<IFluidHandler> handlers, FluidStack fluid, int amount) {
        int remaining = amount;
        for (IFluidHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            int accepted = handler.fill(new FluidStack(fluid, remaining), IFluidHandler.FluidAction.SIMULATE);
            remaining -= accepted;
        }
        return remaining == 0;
    }

    /**
     * How much of {@code fluid} the given handlers could accept, added up over every tank.
     *
     * <p>Unlike items this needs no approximation: {@code IFluidHandler#getTankCapacity} and
     * {@code #getFluidInTank} between them say exactly what a tank would take (the original relied on the same
     * pair through {@code HybridFluidUtils#doSimulateDrainOrFill}).
     */
    public static int insertCapacity(List<IFluidHandler> handlers, FluidStack fluid) {
        long capacity = 0L;
        for (IFluidHandler handler : handlers) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                int free = handler.getTankCapacity(tank);
                FluidStack stored = handler.getFluidInTank(tank);
                if (!stored.isEmpty()) {
                    if (!stored.isFluidEqual(fluid)) {
                        continue;
                    }
                    free -= stored.getAmount();
                }
                if (free > 0) {
                    capacity += free;
                }
                if (capacity >= UNBOUNDED) {
                    return UNBOUNDED;
                }
            }
        }
        return (int) Math.min(UNBOUNDED, capacity);
    }

    // ---------------------------------------------------------------- energy

    public static long availableEnergy(HatchCollection ports) {
        return ports.storedEnergy();
    }

    /** Drains FE across the ports, or nothing at all when the total is insufficient. */
    public static boolean extractEnergy(List<IEnergyStorage> handlers, long amount) {
        long available = 0L;
        for (IEnergyStorage storage : handlers) {
            available += storage.getEnergyStored();
        }
        if (available < amount) {
            return false;
        }
        long remaining = amount;
        for (IEnergyStorage storage : handlers) {
            if (remaining <= 0) {
                break;
            }
            int taken = storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, remaining), false);
            remaining -= taken;
        }
        return remaining == 0;
    }

    public static boolean canInsertEnergy(List<IEnergyStorage> handlers, long amount) {
        long accepted = 0L;
        for (IEnergyStorage storage : handlers) {
            accepted += storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount - accepted), true);
            if (accepted >= amount) {
                return true;
            }
        }
        return accepted >= amount;
    }

    public static boolean insertEnergy(List<IEnergyStorage> handlers, long amount) {
        long remaining = amount;
        for (IEnergyStorage storage : handlers) {
            if (remaining <= 0) {
                break;
            }
            int accepted = storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, remaining), false);
            remaining -= accepted;
        }
        return remaining == 0;
    }

    /**
     * How much FE the given storages could accept, added up over every one of them.
     *
     * <p>Exact, because {@code IEnergyStorage#receiveEnergy(..., simulate = true)} is the API's own way of
     * asking — the original used {@code getRemainingCapacity()} for the same purpose. Each storage is asked for
     * everything it can take, which is why the probe is the maximum an {@code int} can express.
     */
    public static long insertCapacity(List<IEnergyStorage> handlers) {
        long capacity = 0L;
        for (IEnergyStorage storage : handlers) {
            capacity += storage.receiveEnergy(Integer.MAX_VALUE, true);
            if (capacity >= UNBOUNDED) {
                return UNBOUNDED;
            }
        }
        return Math.min(UNBOUNDED, capacity);
    }
}
