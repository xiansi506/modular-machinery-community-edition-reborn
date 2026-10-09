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
        for (UpgradeStack stack : compatible) {
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
        float stacked = modifier.value();
        for (int i = 1; i < stack.count(); i++) {
            stacked *= modifier.value();
        }
        return new RecipeModifier(modifier.target(), modifier.ioTarget(), stacked, modifier.operation(),
                modifier.affectsChance());
    }

    // ------------------------------------------------------------------ reading a bus

    /**
     * Reads every upgrade out of a bus's inventory, merging slots that hold the same upgrade.
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
        Map<UpgradeType, Integer> merged = new LinkedHashMap<>();
        Map<UpgradeType, UpgradeTarget> targets = new LinkedHashMap<>();
        // The per-copy tag of the FIRST slot that contributed each declaration, which is the closest counterpart of
        // what the original kept. It merged same-type slots by summing their sizes (`TileUpgradeBus#updateUpgrades`,
        // `:146-148`) and kept the machine-upgrade instance that arrived first, so a second slot holding the same
        // declaration never overwrote the first one's data either. Two copies with *different* tags therefore
        // resolve to the first's — the original had the same property, and inventing a merge here would be this
        // port deciding something upstream never decided.
        Map<UpgradeType, net.minecraft.nbt.CompoundTag> customData = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            UpgradeTarget.Targets declared = lookup.apply(stack);
            if (declared == null || declared.isEmpty()) {
                continue;
            }
            for (UpgradeTarget target : declared.targets()) {
                UpgradeType type = target.upgrade();
                Integer total = merged.get(type);
                if (total == null) {
                    merged.put(type, stack.getCount());
                    targets.put(type, target);
                    customData.put(type, UpgradeItemNbt.read(stack));
                } else {
                    // The original summed the stack sizes of same-type slots rather than letting the second
                    // slot overwrite the first (TileUpgradeBus#updateUpgrades, :146-148).
                    merged.put(type, total + stack.getCount());
                }
            }
        }
        if (merged.isEmpty()) {
            return UpgradeStack.Bag.EMPTY;
        }
        List<UpgradeStack> stacks = new ArrayList<>(merged.size());
        for (Map.Entry<UpgradeType, Integer> entry : merged.entrySet()) {
            stacks.add(new UpgradeStack(targets.get(entry.getKey()), entry.getValue(),
                    customData.get(entry.getKey())));
        }
        return new UpgradeStack.Bag(stacks);
    }

    private UpgradeEffects() {
    }
}
