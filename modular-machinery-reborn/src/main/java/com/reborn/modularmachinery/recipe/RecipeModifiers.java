package com.reborn.modularmachinery.recipe;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An immutable list of {@link RecipeModifier}s, with the original's application rule.
 *
 * <p>One instance is built per formed machine (see {@code MachineControllerBlockEntity#activeModifiers}) and
 * handed to the recipe each tick, because a machine's modifiers are properties of its <b>structure</b>: whether
 * the vent above the alloy furnace is there decides whether its item outputs are doubled.
 *
 * <p><b>The rule is the original's, verbatim</b> ({@code RecipeModifier#applyModifiers}, its 269-line class'
 * final implementation): gather every matching modifier, sum the {@code ADD} values, multiply the
 * {@code MULTIPLY} values, and return {@code (value + add) * mul}. Order therefore does not matter, which is
 * why one instance can be shared by every requirement of a recipe.
 *
 * <p><b>What was simplified.</b> The original pre-bucketed its modifiers into a {@code ModifierApplier} per
 * requirement type and per chance flag, so applying one was two lookups. This mod's machines carry a handful of
 * modifiers at most, so {@link #apply} scans the list directly; the arithmetic is identical, and the
 * {@code ModifierApplier} bookkeeping (including its "default applier" identity shortcut) has no counterpart
 * here.
 */
public final class RecipeModifiers {

    /** No modifiers at all: every value passes through unchanged. */
    public static final RecipeModifiers EMPTY = new RecipeModifiers(List.of());

    private final List<RecipeModifier> modifiers;

    public RecipeModifiers(List<RecipeModifier> modifiers) {
        this.modifiers = List.copyOf(modifiers);
    }

    public static RecipeModifiers of(List<RecipeModifier> modifiers) {
        return modifiers.isEmpty() ? EMPTY : new RecipeModifiers(modifiers);
    }

    public List<RecipeModifier> modifiers() {
        return this.modifiers;
    }

    public boolean isEmpty() {
        return this.modifiers.isEmpty();
    }

    /**
     * Applies every matching modifier to one numeric value: {@code (value + sum of adds) * product of
     * multipliers} — the original's final line.
     *
     * @param target   the requirement kind the value belongs to, or {@code null} for the duration pseudo-target
     * @param ioType   the side the value belongs to, or {@code null} when there is no side (duration)
     * @param isChance whether the value is a chance, which only chance-affecting modifiers may touch
     */
    public double apply(@Nullable RecipeModifier.Target target, @Nullable IOType ioType, double value,
                        boolean isChance) {
        if (this.modifiers.isEmpty()) {
            return value;
        }
        if (target == null) {
            // The original routed a targetless modifier to REQUIREMENT_DURATION before it ever reached this
            // method; applyDuration below is the only caller that takes that path.
            return applyDuration(value);
        }
        float add = 0.0F;
        float multiply = 1.0F;
        boolean touched = false;
        for (RecipeModifier modifier : this.modifiers) {
            if (!modifier.matches(target, ioType, isChance)) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD -> add += modifier.value();
                case MULTIPLY -> multiply *= modifier.value();
            }
            touched = true;
        }
        return touched ? (value + add) * multiply : value;
    }

    /**
     * The duration pseudo-target: a modifier written with {@code "target": "duration"} <b>or</b> with no usable
     * target at all. The original coerced a {@code null} target to {@code REQUIREMENT_DURATION} in
     * {@code RecipeCraftingContext#addModifier}, and this is that behaviour.
     */
    public double applyDuration(double value) {
        if (this.modifiers.isEmpty()) {
            return value;
        }
        float add = 0.0F;
        float multiply = 1.0F;
        boolean touched = false;
        for (RecipeModifier modifier : this.modifiers) {
            if (modifier.affectsChance()) {
                continue;
            }
            if (modifier.target() != RecipeModifier.Target.DURATION) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD -> add += modifier.value();
                case MULTIPLY -> multiply *= modifier.value();
            }
            touched = true;
        }
        return touched ? (value + add) * multiply : value;
    }

    /** Convenience for an item/fluid/energy value, using that requirement's own kind and side. */
    public double apply(MachineRequirement requirement, double value, boolean isChance) {
        return apply(requirement.modifierTarget(), requirement.ioType(), value, isChance);
    }

    /** Same as {@link #apply(MachineRequirement, double, boolean)} for a float, as the original overloaded it. */
    public float apply(MachineRequirement requirement, float value, boolean isChance) {
        return (float) apply(requirement, (double) value, isChance);
    }

    /**
     * The duration multiplier the original derived from the same modifiers
     * ({@code RecipeCraftingContext#getDurationMultiplier}): {@code duration / modifiedDuration}, so a modifier
     * that <i>shortens</i> the recipe yields a multiplier above 1. Per-tick requirements consume
     * {@code rate * parallelism * multiplier} to keep the total cost of a shortened craft unchanged.
     *
     * <p>The original guarded this behind {@code Config.enableDurationMultiplier} (default {@code true}). This
     * mod always applies it, because the only way to shorten a craft here is an explicit {@code duration}
     * modifier in a machine definition — there is no global switch to leave on.
     */
    public float durationMultiplier(int baseTicks) {
        if (this.modifiers.isEmpty() || baseTicks <= 0) {
            return 1.0F;
        }
        double modified = applyDuration(baseTicks);
        if (modified <= 0.0) {
            return 1.0F;
        }
        return (float) (baseTicks / modified);
    }

    /** Flattens and concatenates two sets, for combining a machine's structure modifiers with an upgrade's. */
    public RecipeModifiers andThen(RecipeModifiers other) {
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        List<RecipeModifier> combined = new java.util.ArrayList<>(this.modifiers.size() + other.modifiers.size());
        combined.addAll(this.modifiers);
        combined.addAll(other.modifiers);
        return new RecipeModifiers(combined);
    }
}
