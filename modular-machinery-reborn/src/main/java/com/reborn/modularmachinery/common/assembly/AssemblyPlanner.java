package com.reborn.modularmachinery.common.assembly;

import com.reborn.modularmachinery.item.PropertyBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local, read-only assembly planning. A bounded search failure is not evidence of missing materials. */
final class AssemblyPlanner {
    private static final int WORK_LIMIT = 250_000;
    private static final int CELL_LIMIT = 16_384;
    private static final int TRANSITION_DEPTH_LIMIT = 96;

    record Plan(List<AssemblyIngredient> resolved, List<AssemblyIngredient> missing,
                Map<BlockPos, ItemStack> fluidInputs, boolean complete) {
        static Plan incomplete() { return new Plan(List.of(), List.of(), Map.of(), false); }
    }

    private static final class Exhausted extends RuntimeException {
        Exhausted() { super("Assembly planning budget exhausted", null, false, false); }
    }

    private static final class Budget {
        int left;
        Budget(int left) { this.left = left; }
        void use() { if (--left < 0) throw new Exhausted(); }
    }

    private AssemblyPlanner() {}

    static Plan plan(Inventory inventory, List<AssemblyIngredient> ingredients) {
        return plan(inventory, ingredients, WORK_LIMIT);
    }

    /** The explicit limit also lets offline tests check the exhausted/not-missing distinction. */
    static Plan plan(Inventory inventory, List<AssemblyIngredient> ingredients, int workLimit) {
        if (ingredients.size() > CELL_LIMIT) return Plan.incomplete();
        Budget budget = new Budget(workLimit);
        try {
            List<ItemStack> stock = new ArrayList<>();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                budget.use();
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) stock.add(stack.copy());
            }
            boolean transitions = false;
            for (AssemblyIngredient ingredient : ingredients) {
                budget.use();
                for (BlockState state : ingredient.candidates()) {
                    budget.use();
                    if (state.getBlock() instanceof LiquidBlock) transitions = true;
                }
            }
            if (!transitions) return solids(ingredients, stock, budget);
            return new Transitions(ingredients, budget).solve(stock);
        } catch (Exhausted exhausted) {
            return Plan.incomplete();
        }
    }

    /** Unit demands, capacitated inventory slots. Iterative augmenting paths, not candidate-count sorting. */
    private static Plan solids(List<AssemblyIngredient> cells, List<ItemStack> stock, Budget budget) {
        int[] assignment = new int[cells.size()];
        Arrays.fill(assignment, -1);
        AssemblyIngredient[] chosen = new AssemblyIngredient[cells.size()];
        int[] seenCell = new int[cells.size()];
        List<Set<Integer>> owners = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) owners.add(new LinkedHashSet<>());
        // Seed a valid matching cheaply. Only unresolved cells need an augmenting path; a full greedy
        // assignment is already a complete proof and large simple structures need no displacement work.
        for (int cell = 0; cell < cells.size(); cell++) {
            for (BlockState state : cells.get(cell).candidates()) {
                budget.use();
                ItemStack material = PropertyBlockItem.stackForState(state);
                if (material.isEmpty()) continue;
                for (int slot = 0; slot < stock.size(); slot++) {
                    budget.use();
                    if (owners.get(slot).size() < stock.get(slot).getCount()
                            && ItemStack.isSameItemSameTags(material, stock.get(slot))) {
                        assignment[cell] = slot;
                        owners.get(slot).add(cell);
                        chosen[cell] = single(cells.get(cell), state);
                        break;
                    }
                }
                if (assignment[cell] >= 0) break;
            }
        }
        for (int root = 0; root < cells.size(); root++) {
            budget.use();
            if (assignment[root] >= 0) continue;
            int[] fromCell = new int[stock.size()];
            Arrays.fill(fromCell, -1);
            BlockState[] edgeState = new BlockState[stock.size()];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(root);
            seenCell[root] = root + 1;
            int free = -1;
            while (!queue.isEmpty() && free < 0) {
                int cell = queue.remove();
                for (BlockState state : cells.get(cell).candidates()) {
                    budget.use();
                    ItemStack material = PropertyBlockItem.stackForState(state);
                    if (material.isEmpty()) continue;
                    for (int slot = 0; slot < stock.size(); slot++) {
                        budget.use();
                        if (fromCell[slot] >= 0 || !ItemStack.isSameItemSameTags(material, stock.get(slot))) continue;
                        fromCell[slot] = cell;
                        edgeState[slot] = state;
                        if (owners.get(slot).size() < stock.get(slot).getCount()) { free = slot; break; }
                        for (int previous : owners.get(slot)) {
                            budget.use();
                            if (seenCell[previous] != root + 1) { seenCell[previous] = root + 1; queue.add(previous); }
                        }
                    }
                    if (free >= 0) break;
                }
            }
            while (free >= 0) {
                budget.use();
                int cell = fromCell[free];
                int previous = assignment[cell];
                if (previous >= 0) owners.get(previous).remove(cell);
                assignment[cell] = free;
                owners.get(free).add(cell);
                chosen[cell] = single(cells.get(cell), edgeState[free]);
                free = previous;
            }
        }
        return result(cells, chosen, new ItemStack[cells.size()]);
    }

    private static AssemblyIngredient single(AssemblyIngredient cell, BlockState state) {
        return new AssemblyIngredient(cell.relPos(), List.of(state), state.getBlock() instanceof LiquidBlock);
    }

    private static Plan result(List<AssemblyIngredient> cells, AssemblyIngredient[] chosen, ItemStack[] sources) {
        List<AssemblyIngredient> resolved = new ArrayList<>(cells.size());
        List<AssemblyIngredient> missing = new ArrayList<>();
        Map<BlockPos, ItemStack> inputs = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            resolved.add(chosen[i] == null ? cells.get(i) : chosen[i]);
            if (chosen[i] == null) missing.add(cells.get(i));
            else if (sources[i] != null) inputs.put(chosen[i].relPos(), sources[i].copyWithCount(1));
        }
        return new Plan(List.copyOf(resolved), List.copyOf(missing), Map.copyOf(inputs), true);
    }

    /** Ordered container state transitions: returned buckets are available only to later steps. */
    private static final class Transitions {
        final List<AssemblyIngredient> cells;
        final Budget budget;
        final AssemblyIngredient[] path;
        final ItemStack[] sources;
        AssemblyIngredient[] best;
        ItemStack[] bestSources;
        int bestCount;

        Transitions(List<AssemblyIngredient> cells, Budget budget) {
            this.cells = cells;
            this.budget = budget;
            path = new AssemblyIngredient[cells.size()];
            sources = new ItemStack[cells.size()];
            best = path.clone();
            bestSources = sources.clone();
        }

        List<ItemStack> copy(List<ItemStack> stock) {
            List<ItemStack> copy = new ArrayList<>(stock.size());
            for (ItemStack stack : stock) { budget.use(); copy.add(stack.copy()); }
            return copy;
        }

        Plan solve(List<ItemStack> stock) {
            List<ItemStack> greedy = copy(stock);
            for (int i = 0; i < cells.size(); i++) {
                boolean found = false;
                for (BlockState state : cells.get(i).candidates()) {
                    budget.use();
                    for (int slot = 0; slot < greedy.size(); slot++) {
                        budget.use();
                        ItemStack input = greedy.get(slot).copyWithCount(1);
                        if (consume(greedy, slot, state)) {
                            best[i] = single(cells.get(i), state);
                            bestSources[i] = best[i].fluidBlock() ? input : null;
                            bestCount++;
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            }
            if (bestCount == cells.size()) return result(cells, best, bestSources);
            if (cells.size() > TRANSITION_DEPTH_LIMIT) return Plan.incomplete();
            search(0, stock, 0);
            return result(cells, best, bestSources);
        }

        boolean consume(List<ItemStack> stock, int slot, BlockState state) {
            ItemStack stack = stock.get(slot);
            if (stack.isEmpty()) return false;
            if (state.getBlock() instanceof LiquidBlock) {
                return MachineAssembly.consumeFluidAt(stock, new FluidStack(state.getFluidState().getType(), 1000),
                        slot, IFluidHandler.FluidAction.EXECUTE);
            }
            ItemStack material = PropertyBlockItem.stackForState(state);
            if (material.isEmpty() || !ItemStack.isSameItemSameTags(stack, material)) return false;
            stack.shrink(1);
            return true;
        }

        boolean search(int index, List<ItemStack> stock, int count) {
            budget.use();
            if (index == cells.size()) {
                if (count > bestCount) { bestCount = count; best = path.clone(); bestSources = sources.clone(); }
                return count == cells.size();
            }
            if (count + cells.size() - index <= bestCount) return false;
            for (BlockState state : cells.get(index).candidates()) {
                budget.use();
                for (int slot = 0; slot < stock.size(); slot++) {
                    budget.use();
                    if (stock.get(slot).isEmpty()) continue;
                    List<ItemStack> trial = copy(stock);
                    ItemStack input = stock.get(slot).copyWithCount(1);
                    if (!consume(trial, slot, state)) continue;
                    path[index] = single(cells.get(index), state);
                    sources[index] = path[index].fluidBlock() ? input : null;
                    if (search(index + 1, trial, count + 1)) return true;
                }
            }
            path[index] = null;
            sources[index] = null;
            return search(index + 1, stock, count);
        }
    }
}
