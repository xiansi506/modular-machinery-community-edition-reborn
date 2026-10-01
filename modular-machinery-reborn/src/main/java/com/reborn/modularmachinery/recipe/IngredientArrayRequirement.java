package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code ingredient_array_input} requirement — the original's {@code RequirementIngredientArray}
 * ({@code :36-391}) reached through its own JSON reader
 * ({@code RequirementTypeIngredientArray#createRequirement}, {@code :45-143}).
 *
 * <h2>What it does</h2>
 *
 * <p>The original's own doc-comment states the whole contract ({@code :44-47}): "物品组输入，仅消耗组内的其中一个"
 * — an input array <b>consumes one of the group</b>. The entries are tried <b>in array order</b> and the first
 * one that can still pay is the one that is taken, which is what the original's
 * {@code consumeAllItems} ({@code :228-286}) does: it walks {@code ingredients} in list order, tracks how many
 * copies the earlier entries already served in {@code ingredientConsumed}, and asks each entry only for what is
 * still missing ({@code maxConsume = toConsume * (maxMultiplier - ingredientConsumed)}). An array therefore
 * makes the <b>earlier</b> entries the preferred ones, exactly as the original did.
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li><b>can-start</b>: {@code canSatisfy} simulates the whole settlement and answers whether {@code
 *       parallelism} copies can be paid. The original's {@code canStartCrafting} ({@code :176-179}) ran the same
 *       consumption with {@code ResultChance.GUARANTEED} and failed with
 *       {@code craftcheck.failure.item.input} when fewer than {@code parallelism} copies could be served
 *       ({@code doItemIO}, {@code :201-207}). That key is reused verbatim.</li>
 *   <li><b>per-tick</b>: nothing. The original's class has no {@code doIOTick}; an array is a one-shot cost paid
 *       when the craft starts, like every other item input.</li>
 *   <li><b>finish</b>: nothing on the input side. {@code startCrafting} ({@code :161-166}) consumed only for
 *       {@code IOType.INPUT} and {@code finishCrafting} ({@code :168-173}) only for {@code IOType.OUTPUT}; this
 *       port accepts input only, because the original's JSON reader always built an input
 *       ({@code RequirementIngredientArray(ingredients)}, {@code :130} — the {@code IOType.INPUT} constructor,
 *       {@code :48-52}) and the port's {@code item} type already covers outputs one stack at a time.</li>
 * </ul>
 *
 * <h2>Chance</h2>
 *
 * <p>The original's reader read {@code chance} off the <b>requirement</b>, not off the entry
 * ({@code :105-113} inside the entry loop, reading {@code jsonObject}), so one value reached every entry; the
 * original then rolled it <b>once per settlement</b> in {@code startCrafting}
 * ({@code chance.canWork(…)} gating {@code doItemIO}). Both properties are kept: the field is one value for the
 * array, and {@link #apply} rolls it once. The original's {@code canStartCrafting} ignored chance entirely
 * ({@code ResultChance.GUARANTEED}), so a sub-1 chance leaves a craft that may start and then pay nothing —
 * the original's own behaviour, not a repair.
 *
 * <h2>Parallelism</h2>
 *
 * <p>The original's class extends {@code ComponentRequirement.MultiCompParallelizable} and does <b>not</b> call
 * {@code setParallelizeUnaffected} — that flag is the catalyst's ({@code RequirementCatalyst.java:25,30,35}) —
 * so {@code RecipeCraftingContext#getMaxParallelism} included it and it <b>does</b> bound the copy count. The
 * arithmetic is {@link #servedCopies}: the greedy number of complete groups the ports can supply, which is the
 * original's {@code getMaxParallelism} ({@code :182-193}) run against the same {@code consumeAllItems}. One
 * deliberate difference: the original's {@code getMaxParallelism} <b>really consumed</b> the items it measured
 * (it called {@code doItemIOInternal} with no simulation flag), so asking for a limit destroyed the inputs when
 * the craft then failed to start. This port answers the same number without touching the ports, which is the
 * all-or-nothing contract {@link IngredientIo} keeps for every other requirement.
 *
 * <h2>Modifiers</h2>
 *
 * <p>Nothing modifies an array's amounts or its chance: the original registered this type with the requirement
 * key of {@code item} for its modifiers ({@code :83-89} used {@code REQUIREMENT_ITEM}), and this port's
 * {@code MachineRecipe} dispatch ({@code canSatisfy}/{@code applyPhase}) reaches this class only through the
 * no-modifier overloads, so amounts and chance come out of the file unchanged — the same treatment
 * {@code interface_number_input} gets.
 */
public final class IngredientArrayRequirement extends MachineRequirement {

    /** The original's {@code craftcheck.failure.item.input} ({@code RequirementIngredientArray.java:204}). */
    public static final String FAILURE_KEY = "craftcheck.failure.item.input";

    /** The original's clamp: {@code MathHelper.clamp(amount, 1, 64)} ({@code RequirementTypeIngredientArray:83}). */
    public static final int MAX_AMOUNT = 64;

    private final List<IngredientArrayEntry> entries;

    /** The reader's default when the requirement wrote no {@code chance} ({@code int amount = 1} era default). */
    private final float chance;

    public IngredientArrayRequirement(List<IngredientArrayEntry> entries, float chance) {
        // START phase and not per-tick: the original built an input array and consumed it in startCrafting.
        super(IOType.INPUT, false);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("an ingredient array needs at least one entry");
        }
        this.entries = List.copyOf(entries);
        this.chance = chance;
    }

    // ------------------------------------------------------------------ the shape the reader produced

    public int entryCount() {
        return this.entries.size();
    }

    public int entryAmount(int index) {
        return this.entries.get(index).amount();
    }

    public float entryChance(int index) {
        return this.entries.get(index).chance();
    }

    /** The entry's item id, or {@code "#tag"} for a tag entry. */
    public String entryItemId(int index) {
        return this.entries.get(index).itemId();
    }

    /** The requirement's own chance, the one value the original's reader put on every entry. */
    public float chance() {
        return this.chance;
    }

    /** The entries, for JEI and for the network writer. */
    List<IngredientArrayEntry> entries() {
        return this.entries;
    }

    /**
     * The concrete stacks entry {@code index} can match, at that entry's own amount.
     *
     * <p>Used by the network writer (which sends stacks) and by JEI (which shows them). It deliberately returns
     * the <b>matching</b> stacks rather than one representative, because an entry may be a tag and the whole
     * point of a tag in a group is that any of its members satisfies it.
     */
    public List<ItemStack> entryStacks(int index) {
        IngredientArrayEntry entry = this.entries.get(index);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack match : entry.ingredient().getItems()) {
            if (!match.isEmpty()) {
                out.add(match.copyWithCount(entry.amount()));
            }
        }
        return out;
    }

    @Override
    public RecipeModifier.Target modifierTarget() {
        return RecipeModifier.Target.ITEM;
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * How many complete copies the ports can pay, the original's {@code getMaxParallelism}
     * ({@code RequirementIngredientArray:182-193}) without its side effect.
     *
     * <p>Entry {@code i} costs {@code amount_i} per copy and may serve at most {@code maxMultiplier -
     * servedSoFar} copies; it serves {@code floor(available_i / amount_i)} of them. The total never exceeds
     * {@code maxMultiplier} and never exceeds what the ports hold, which is why {@link #canSatisfy} can be
     * written in terms of it: {@code servedCopies(ports, parallelism) == parallelism} is exactly the original's
     * {@code mul < parallelism -> failure} test.
     */
    int servedCopies(HatchCollection ports, int maxMultiplier) {
        if (maxMultiplier <= 0) {
            return 0;
        }
        List<IItemHandler> handlers = ports.itemInputs();
        int served = 0;
        for (IngredientArrayEntry entry : this.entries) {
            int perCopy = entry.amount();
            if (perCopy <= 0) {
                continue;
            }
            int stillWanted = maxMultiplier - served;
            if (stillWanted <= 0) {
                break;
            }
            served += Math.min(stillWanted, entry.availableIn(handlers) / perCopy);
        }
        return served;
    }

    @Override
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        return Math.min(ceiling, servedCopies(ports, ceiling));
    }

    @Override
    public boolean canSatisfy(HatchCollection ports) {
        return servedCopies(ports, this.parallelism()) == this.parallelism();
    }

    @Nullable
    @Override
    public String startFailure(HatchCollection ports) {
        return FAILURE_KEY;
    }

    // ------------------------------------------------------------------ the settlement

    @Override
    public boolean apply(HatchCollection ports, RandomSource random) {
        List<IItemHandler> handlers = ports.itemInputs();
        if (this.chance < 1.0F && random.nextFloat() >= this.chance) {
            // The original rolled this once for the whole settlement (`chance.canWork` gating doItemIO), and it
            // rolled it after the decision to start, so a failed roll pays nothing while the craft still runs.
            return true;
        }
        int copies = this.parallelism();
        int served = 0;
        int[] perEntry = new int[this.entries.size()];
        for (int index = 0; index < this.entries.size(); index++) {
            IngredientArrayEntry entry = this.entries.get(index);
            int perCopy = entry.amount();
            if (perCopy <= 0) {
                continue;
            }
            int stillWanted = copies - served;
            if (stillWanted <= 0) {
                break;
            }
            int fromThisEntry = Math.min(stillWanted, entry.availableIn(handlers) / perCopy);
            perEntry[index] = fromThisEntry * perCopy;
            served += fromThisEntry;
        }
        for (int index = 0; index < this.entries.size(); index++) {
            if (perEntry[index] > 0 && !IngredientIo.extract(handlers, this.entries.get(index).ingredient(),
                    perEntry[index])) {
                // canSatisfy already proved this cannot happen; reporting it loudly beats half-consuming a craft.
                throw new IllegalStateException("the ingredient array agreed with its own simulation and then "
                        + "could not extract " + perEntry[index] + "x " + this.entries.get(index).itemId());
            }
        }
        return true;
    }

    @Override
    public boolean tick(HatchCollection ports, RandomSource random) {
        return true;
    }

    /**
     * How many items entry {@code index} can see in the ports right now.
     *
     * <p>Exposed so the offline acceptance harness can prove the <b>input</b> to {@link #servedCopies} rather
     * than only its output: a limit that comes out wrong because availability was miscounted and a limit that
     * comes out wrong because the division is wrong are different defects.
     */
    public int entryAvailableIn(int index, HatchCollection ports) {
        return this.entries.get(index).availableIn(ports.itemInputs());
    }

    /**
     * Every alternative of the whole group, in consumption order, each sized to its entry's amount — one JEI
     * cell's ingredient list.
     */
    public List<ItemStack> entryStacksFlat() {
        List<ItemStack> out = new ArrayList<>();
        for (int index = 0; index < this.entries.size(); index++) {
            out.addAll(entryStacks(index));
        }
        return out;
    }

    @Override
    public String describe() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < this.entries.size(); index++) {
            if (index > 0) {
                builder.append(" / ");
            }
            builder.append(this.entries.get(index).amount()).append("x ")
                    .append(this.entries.get(index).itemId());
        }
        return this.chance < 1.0F
                ? builder.append(String.format(Locale.ROOT, " (%.0f%%)", this.chance * 100.0F)).toString()
                : builder.toString();
    }
}
