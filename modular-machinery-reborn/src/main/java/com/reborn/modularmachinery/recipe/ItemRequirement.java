package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * An item input or output of a machine recipe.
 *
 * <p>Inputs accept an {@link Ingredient}, which covers plain items as well as the item tags that replace the
 * original's ore dictionary. Outputs carry a concrete stack plus the original's {@code chance} — each output
 * requirement is rolled on its own, which is why the centrifuge wool recipe lists four separate string outputs
 * with different chances instead of one output with an amount range.
 *
 * <p>Outputs also honour the original's {@code minAmount}/{@code maxAmount} pair: the amount is rolled per
 * craft. The space check always uses {@code maxAmount}, so the worst case is reserved before the craft is
 * allowed to end. Inputs require a fixed {@code amount}; the loader rejects a range there rather than picking
 * an interpretation.
 *
 * <p><b>Modifiers.</b> Both the amount and the chance go through the machine's {@link RecipeModifiers} first,
 * which is what the original's {@code applyModifierAmount} and
 * {@code RecipeModifier.applyModifiers(context, this, this.chance, true)} did. Amount and chance are separate
 * modifier buckets there and stay separate here; that is the whole purpose of {@code affectChance}.
 *
 * <p><b>Parallelism.</b> Every amount is multiplied by {@link #parallelism()} — the original's
 * {@code maxConsume = toConsume * maxMultiplier}. The chance is <b>not</b> multiplied: the original rolled
 * {@code chance.canWork(...)} once per settlement and then moved {@code parallelism} copies through that one
 * decision, so a 25% output at parallelism 4 yields 4 items 25% of the time rather than 4 independent rolls.
 * The roll is taken per copy only where the recipe itself rolls a random amount, because
 * {@code minAmount}/{@code maxAmount} is a property of a single craft's result.
 */
public final class ItemRequirement extends MachineRequirement {

    @Nullable private final Ingredient ingredient;
    @Nullable private final ItemStack stack;
    private final int minAmount;
    private final int maxAmount;
    private final float chance;

    private ItemRequirement(IOType ioType, @Nullable Ingredient ingredient, @Nullable ItemStack stack,
                            int minAmount, int maxAmount, float chance) {
        super(ioType, false);
        this.ingredient = ingredient;
        this.stack = stack == null ? null : stack.copyWithCount(1);
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.chance = chance;
    }

    public static ItemRequirement input(Ingredient ingredient, int amount) {
        return new ItemRequirement(IOType.INPUT, ingredient, null, amount, amount, 1.0F);
    }

    public static ItemRequirement output(ItemStack stack, int minAmount, int maxAmount, float chance) {
        return new ItemRequirement(IOType.OUTPUT, null, stack, minAmount, maxAmount, chance);
    }

    @Override
    public RecipeModifier.Target modifierTarget() {
        return RecipeModifier.Target.ITEM;
    }

    /** Upper bound of the amount: what the space check must reserve. */
    public int amount() {
        return this.maxAmount;
    }

    public int minAmount() {
        return this.minAmount;
    }

    public int maxAmount() {
        return this.maxAmount;
    }

    public boolean hasAmountRange() {
        return this.minAmount != this.maxAmount;
    }

    public float chance() {
        return this.chance;
    }

    @Nullable
    public Ingredient ingredient() {
        return this.ingredient;
    }

    /** The output stack at its maximum amount, or an empty stack for inputs. */
    public ItemStack outputStack() {
        return this.stack == null ? ItemStack.EMPTY : this.stack.copyWithCount(this.maxAmount);
    }

    // ------------------------------------------------------------------ modifiers

    /** The amount one copy consumes or produces, after this requirement's modifiers. */
    public int modifiedAmount(RecipeModifiers modifiers) {
        double modified = modifiers.apply(this, (double) this.maxAmount, false);
        return (int) Math.max(0.0, Math.floor(modified));
    }

    /** The chance one copy is produced with, after the chance bucket of this requirement's modifiers. */
    public float modifiedChance(RecipeModifiers modifiers) {
        if (this.ioType() != IOType.OUTPUT || this.stack == null) {
            return 1.0F;
        }
        return (float) Math.max(0.0, Math.min(1.0, modifiers.apply(this, (double) this.chance, true)));
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public boolean canSatisfy(HatchCollection ports) {
        return canSatisfy(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canSatisfy} with modifiers, for the whole {@code parallelism} copies the craft settles. */
    public boolean canSatisfy(HatchCollection ports, RecipeModifiers modifiers) {
        return canSatisfy(ports, modifiers, this.parallelism());
    }

    /** {@link #canSatisfy} for an explicit number of copies. */
    public boolean canSatisfy(HatchCollection ports, RecipeModifiers modifiers, int copies) {
        int perCopy = modifiedAmount(modifiers);
        if (this.ioType() == IOType.INPUT) {
            return this.ingredient != null && perCopy > 0
                    && IngredientIo.count(ports.itemInputs(), this.ingredient) >= perCopy * copies;
        }
        // Space is checked for the guaranteed maximum, so a craft cannot end when the result would not fit.
        return this.stack != null && IngredientIo.canInsertItems(ports.itemOutputs(), this.stack, perCopy * copies);
    }

    @Override
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        if (!isParallelizable()) {
            return 1;
        }
        int perCopy = modifiedAmount(modifiers);
        if (perCopy <= 0) {
            // A modifier reduced the amount to nothing, so this requirement never bounds the craft.
            return ceiling;
        }
        if (this.ioType() == IOType.INPUT) {
            if (this.ingredient == null) {
                return 0;
            }
            return Math.min(ceiling, IngredientIo.count(ports.itemInputs(), this.ingredient) / perCopy);
        }
        if (this.stack == null) {
            return ceiling;
        }
        return Math.min(ceiling, IngredientIo.insertCapacity(ports.itemOutputs(), this.stack) / perCopy);
    }

    @Override
    public boolean apply(HatchCollection ports, RandomSource random) {
        return apply(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #apply} with modifiers, moving {@code parallelism} copies per settlement. */
    public boolean apply(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        int perCopy = modifiedAmount(modifiers);
        int copies = this.parallelism();
        if (this.ioType() == IOType.INPUT) {
            int total = perCopy * copies;
            return total <= 0
                    || (this.ingredient != null && IngredientIo.extract(ports.itemInputs(), this.ingredient, total));
        }
        if (this.stack == null || perCopy <= 0 || copies <= 0) {
            return true;
        }

        float decidedChance = modifiedChance(modifiers);
        boolean rollChance = decidedChance < 1.0F;
        int total = 0;
        for (int copy = 0; copy < copies; copy++) {
            // The original rolled the chance once for the whole settlement (chance.canWork gated doItemIO), and
            // rolled a random amount per craft. With the default chance of 1.0 there is nothing to roll.
            if (rollChance && random.nextFloat() >= decidedChance) {
                continue;
            }
            total += this.hasAmountRange()
                    ? this.minAmount + random.nextInt(this.maxAmount - this.minAmount + 1)
                    : perCopy;
        }
        if (total <= 0) {
            return true;
        }
        // One pair: a range has already been rolled out above, and the plain case repeats the same count.
        return IngredientIo.insertAll(ports.itemOutputs(),
                List.of(new IngredientIo.StackAmount(this.stack, total)));
    }

    @Override
    public boolean tick(HatchCollection ports, RandomSource random) {
        return true;
    }

    @Override
    public String describe() {
        String what = this.ingredient != null
                ? Arrays.stream(this.ingredient.getItems())
                        .findFirst().map(ItemRequirement::nameOf).orElse("?")
                : nameOf(this.stack);
        String amountText = this.hasAmountRange()
                ? this.minAmount + "-" + this.maxAmount
                : Integer.toString(this.maxAmount);
        String chanceText = this.ioType() == IOType.OUTPUT && this.chance < 1.0F
                ? String.format(Locale.ROOT, " (%.0f%%)", this.chance * 100.0F) : "";
        return amountText + "x " + what + chanceText;
    }

    private static String nameOf(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? "?" : stack.getHoverName().getString();
    }
}
