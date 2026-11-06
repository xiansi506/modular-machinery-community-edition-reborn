package com.reborn.modularmachinery.upgrade;

import com.reborn.modularmachinery.recipe.RecipeModifier;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The bridge from "an upgrade sits in a bus" to "the recipe engine's numbers change".
 *
 * <h2>Why this is a separate, pure class</h2>
 *
 * <p>The scoping document's §5.2 splits the two sources of {@link RecipeModifier} on purpose: a machine
 * definition's {@code modifiers} are properties of a <b>structure</b> (M6c evaluates them in
 * {@code MachineControllerBlockEntity#evaluateModifiers}), while an upgrade's are properties of an
 * <b>inventory</b>. They must not be merged into one code path — but they do have to meet in one
 * {@link RecipeModifiers}, which the machine JSON's array already feeds. {@link RecipeModifiers#andThen} is that
 * meeting point, and it existed before this slice with exactly this comment on it.
 *
 * <p>Everything here is a pure function of the bus contents plus one machine name, so the offline acceptance
 * harness in {@code _audit/m6c-parallel-verify/} can drive it: it can put a {@code duration x0.5} upgrade into a
 * synthetic bus, fold the result into a {@link RecipeModifiers}, and count the ticks and items that actually
 * moved. That is the evidence the plan demands, because "the bus GUI shows the upgrade" is explicitly not
 * accepted.
 *
 * <h2>How a bus upgrade becomes a modifier (the original's route, restated)</h2>
 *
 * <p>The original had no declaration for this. A CraftTweaker script called
 * {@code MachineUpgradeBuilder#addModifier(stackAble, key, modifier)} ({@code :138-159}), which attached a
 * handler to {@code MachineTickEvent} that, at {@code Phase.START}, called
 * {@code controller.addModifier(key, modifier)} — and that method fed the modifier into the craft's
 * {@code RecipeCraftingContext} alongside the structure's own modifiers. Three details are carried over:
 *
 * <ol>
 *   <li><b>One modifier per upgrade key.</b> The handler refused to add a second modifier under the same key,
 *       so two slots holding the same upgrade do not double up; their stack sizes are summed instead (the
 *       original's {@code TileUpgradeBus#updateUpgrades}, {@code :138-154}).</li>
 *   <li><b>Compatibility is checked before anything is added.</b>
 *       {@code UpgradeBusProvider#getUpgrades(controller)} returned only the upgrades
 *       {@code type.isCompatible(foundMachine)} accepted, so an incompatible upgrade contributed nothing at
 *       all. {@link #of} does the same through {@link UpgradeStack.Bag#compatibleWith}.</li>
 *   <li><b>Stacking multiplies the value, once per extra copy.</b> {@code stackAble} turned {@code n} carriers
 *       into {@code modifier.multiply(modifier.getModifier())} applied {@code n} times — {@code value^n}. That
 *       is {@link #stacked}, and it is only reachable for a {@code MULTIPLY} modifier:
 *       {@link RecipeModifier#scaled} returns an {@code ADD} modifier untouched, exactly as the original's
 *       multiply loop would have left it.</li>
 * </ol>
 */
public final class UpgradeEffects {

    /**
     * The recipe modifiers one bus contributes to one machine.
     *
     * @param bag       everything the bus holds, as read from its slots
     * @param machineId the machine the bus belongs to, or {@code null} for "do not filter by compatibility"
     *                  (the GUI's own listing, the original's {@code getUpgrades(null)})
     */
    public static RecipeModifiers of(UpgradeStack.Bag bag, @Nullable ResourceLocation machineId) {
        if (bag.isEmpty()) {
            return RecipeModifiers.EMPTY;
        }
        List<UpgradeStack> compatible = bag.compatibleWith(machineId);
        if (compatible.isEmpty()) {
            return RecipeModifiers.EMPTY;
        }
        List<RecipeModifier> flat = new ArrayList<>();
        // Dynamic callbacks are per slot, but declared modifiers are still once per type per bus.
        Map<ResourceLocation, UpgradeStack> aggregated = new LinkedHashMap<>();
        for (UpgradeStack stack : compatible) {
            UpgradeStack previous = aggregated.get(stack.type().id());
            int total = previous == null ? stack.count()
                    : (int) Math.min(stack.type().maxStackSize(), (long) previous.count() + stack.count());
            aggregated.put(stack.type().id(), new UpgradeStack(stack.target(), total));
        }
        for (UpgradeStack stack : aggregated.values()) {
            for (RecipeModifier modifier : stack.type().modifiers()) {
                flat.add(stacked(modifier, stack));
            }
        }
        return RecipeModifiers.of(flat);
    }

    /** The modifier one upgrade stack contributes, after the original's per-copy multiplication. */
    public static RecipeModifier stacked(RecipeModifier modifier, UpgradeStack stack) {
        if (!stack.type().stackable() || stack.count() <= 1
                || modifier.operation() != RecipeModifier.Operation.MULTIPLY) {
            // The original's loop was written for MULTIPLY and left ADD alone; `scaled` says the same thing, and
            // returning early here keeps the ADD path indistinguishable from the unstacked one.
            return modifier;
        }
        // `value^n` the way the original computed it — repeated multiplication, so the result is bit-identical
        // to its loop rather than a Math.pow that might round differently in the last bit.
        // Preserve the original float loop for ordinary inventories. Huge legal counts must not make
        // planning loop billions of times; the same power is evaluated in bounded time for that case.
        float stacked = modifier.value();
        if (stack.count() > 4096) {
            stacked = (float) Math.pow(stacked, stack.count());
            return new RecipeModifier(modifier.target(), modifier.ioTarget(), stacked, modifier.operation(),
                    modifier.affectsChance());
        }
        for (int i = 1; i < stack.count(); i++) {
            stacked *= modifier.value();
        }
        return new RecipeModifier(modifier.target(), modifier.ioTarget(), stacked, modifier.operation(),
                modifier.affectsChance());
    }

    // ------------------------------------------------------------------ reading a bus

    /**
     * Reads effective copies with one max-stack budget per declaration ID per bus. Fixed entries merge;
     * dynamic entries retain each effective slot's data, even when two slots have identical NBT.
     *
     * <p>This is the original's {@code TileUpgradeBus#onUpgradeInventoryChanged} ({@code :94-119}) plus
     * {@code updateUpgrades} ({@code :138-154}), without the {@code synchronized} block: this project's recipe
     * engine is single-threaded (the scoping document's §5.1 and §7 say so explicitly), so a lock here would
     * only create the impression that there is concurrency to defend against.
     *
     * <p>It takes an {@code IItemHandler} rather than an {@code ItemStackHandler} so the offline acceptance
     * harness can drive the very same method against a plain in-memory handler — no {@code Level} involved.
     *
     * <p>An empty slot, or one holding something the registry does not declare as a carrier, simply contributes
     * nothing. The original called {@code removeDynamicUpgrades} there; this design has no counterpart because
     * nothing is cached between reads.
     */
    public static UpgradeStack.Bag read(IItemHandler inventory) {
        return read(inventory, UpgradeRegistry::targetsFor);
    }

    /**
     * {@link #read(IItemHandler)} with the carrier lookup supplied — the live registry's answer by default, and
     * a standing table in the offline acceptance harness.
     *
     * <p>The indirection exists because the harness runs outside Forge's mod loading, where
     * {@code ForgeRegistries.ITEMS.getValue} answers {@code Items.AIR} for everything, so
     * {@code UpgradeRegistry.replace} cannot be populated with real items there. Resolving the slot's item
     * through a function keeps the merge arithmetic — the part worth asserting — on production code.
     */
    public static UpgradeStack.Bag read(IItemHandler inventory, Function<ItemStack, UpgradeTarget.Targets> lookup) {
        Map<ResourceLocation, Integer> used = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> fixedIndices = new LinkedHashMap<>();
        List<UpgradeStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }
            UpgradeTarget.Targets declared = lookup.apply(stack);
            if (declared == null || declared.isEmpty()) {
                continue;
            }
            java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
            for (UpgradeTarget target : declared.targets()) {
                UpgradeType type = target.upgrade();
                if (!seen.add(type.id())) continue;
                int previous = used.getOrDefault(type.id(), 0);
                int effective = Math.min(stack.getCount(), Math.max(0, type.maxStackSize() - previous));
                if (effective <= 0) continue;
                used.put(type.id(), previous + effective);
                Integer index = fixedIndices.get(type.id());
                if (type.dynamic() || index == null) {
                    if (!type.dynamic()) fixedIndices.put(type.id(), stacks.size());
                    stacks.add(new UpgradeStack(target, effective, UpgradeItemNbt.read(stack)));
                } else {
                    UpgradeStack first = stacks.get(index);
                    stacks.set(index, new UpgradeStack(first.target(), first.count() + effective, first.customDataCopy()));
                }
            }
        }
        if (stacks.isEmpty()) {
            return UpgradeStack.Bag.EMPTY;
        }
        return new UpgradeStack.Bag(stacks);
    }

    private UpgradeEffects() {
    }
}
