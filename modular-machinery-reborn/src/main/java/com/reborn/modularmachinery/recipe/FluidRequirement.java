package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fluids.FluidStack;

import java.util.Locale;

/**
 * A fluid input or output of a machine recipe.
 *
 * <p>Mirrors the original's {@code fluid} and {@code fluid_pertick} requirement types: the plain form is drawn
 * once when the craft starts (or produced when it ends), the per-tick form is drained or filled on every tick.
 *
 * <p><b>Modifiers.</b> {@code RecipeModifier.applyModifiers(context, this, this.required.amount, false)} in the
 * original's {@code RequirementFluid#doFluidIOInternal}, reproduced by {@link #modifiedAmount}.
 *
 * <p><b>Parallelism.</b> The amount is multiplied by {@link #parallelism()}
 * ({@code maxRequired = required * maxMultiplier} in the original). The chance is rolled once per settlement,
 * as it was there.
 */
public final class FluidRequirement extends MachineRequirement {

    private final FluidStack fluid;
    private final int amount;
    private final float chance;

    public FluidRequirement(IOType ioType, FluidStack fluid, int amount, float chance, boolean perTick) {
        super(ioType, perTick);
        this.fluid = new FluidStack(fluid, 1);
        this.amount = Math.max(1, amount);
        this.chance = chance;
    }

    @Override
    public RecipeModifier.Target modifierTarget() {
        return RecipeModifier.Target.FLUID;
    }

    public FluidStack fluid() {
        return new FluidStack(this.fluid, this.amount);
    }

    /** The fluid alone, at amount 1, for network transfer and display. */
    public FluidStack fluidType() {
        return new FluidStack(this.fluid, 1);
    }

    public int amount() {
        return this.amount;
    }

    public float chance() {
        return this.chance;
    }

    /** The amount one copy drains or fills, after this requirement's modifiers. */
    public int modifiedAmount(RecipeModifiers modifiers) {
        double modified = modifiers.apply(this, (double) this.amount, false);
        return (int) Math.max(0.0, Math.floor(modified));
    }

    /** The chance one copy is produced with, after the chance bucket of this requirement's modifiers. */
    public float modifiedChance(RecipeModifiers modifiers) {
        if (this.ioType() != IOType.OUTPUT) {
            return 1.0F;
        }
        return (float) Math.max(0.0, Math.min(1.0, modifiers.apply(this, (double) this.chance, true)));
    }

    @Override
    public boolean canSatisfy(HatchCollection ports) {
        return canSatisfy(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canSatisfy} with modifiers, for the whole {@code parallelism} copies the craft settles. */
    public boolean canSatisfy(HatchCollection ports, RecipeModifiers modifiers) {
        int total = modifiedAmount(modifiers) * this.parallelism();
        if (this.ioType() == IOType.INPUT) {
            return total <= 0 || IngredientIo.countFluid(ports.fluidInputs(), this.fluid) >= total;
        }
        return total <= 0 || IngredientIo.canFillFluid(ports.fluidOutputs(), this.fluid, total);
    }

    @Override
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        if (!isParallelizable()) {
            return 1;
        }
        int perCopy = modifiedAmount(modifiers);
        if (perCopy <= 0) {
            return ceiling;
        }
        if (this.ioType() == IOType.INPUT) {
            return Math.min(ceiling, IngredientIo.countFluid(ports.fluidInputs(), this.fluid) / perCopy);
        }
        return Math.min(ceiling, IngredientIo.insertCapacity(ports.fluidOutputs(), this.fluid) / perCopy);
    }

    @Override
    public boolean apply(HatchCollection ports, RandomSource random) {
        return transfer(ports, random, RecipeModifiers.EMPTY);
    }

    @Override
    public boolean tick(HatchCollection ports, RandomSource random) {
        return transfer(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #apply} with modifiers. */
    public boolean apply(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return transfer(ports, random, modifiers);
    }

    /** {@link #tick} with modifiers. */
    public boolean tick(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return transfer(ports, random, modifiers);
    }

    private boolean transfer(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        int total = modifiedAmount(modifiers) * this.parallelism();
        if (this.ioType() == IOType.INPUT) {
            return total <= 0 || IngredientIo.drainFluid(ports.fluidInputs(), this.fluid, total);
        }
        if (total <= 0 || modifiedChance(modifiers) < 1.0F && random.nextFloat() >= modifiedChance(modifiers)) {
            return true;
        }
        return IngredientIo.fillFluid(ports.fluidOutputs(), this.fluid, total);
    }

    @Override
    public String describe() {
        // Forge 1.20.1 has no FluidStack#getHoverName; the display name lives in getDisplayName().
        String name = this.fluid.getDisplayName().getString();
        String chanceText = this.ioType() == IOType.OUTPUT && this.chance < 1.0F
                ? String.format(Locale.ROOT, " (%.0f%%)", this.chance * 100.0F) : "";
        String perTickText = this.perTick() ? "/t" : "";
        return this.amount + " mB " + name + perTickText + chanceText;
    }
}
