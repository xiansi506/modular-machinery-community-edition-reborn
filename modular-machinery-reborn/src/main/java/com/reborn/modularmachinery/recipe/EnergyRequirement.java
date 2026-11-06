package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

/**
 * An energy input or output of a machine recipe.
 *
 * <p>Always per-tick, because the original's {@code energy} requirement type takes {@code energyPerTick} and
 * belongs to the {@code PerTick} family. This is why the built-in recipes specify rates rather than totals:
 * {@code alloy_smelter_diamond} is 100 FE/t for 5 ticks, {@code transformer_energy_transform} moves 128 FE/t.
 *
 * <p><b>Modifiers.</b> The rate goes through the machine's {@link RecipeModifiers}, as
 * {@code RecipeModifier.applyModifiers(context, this, this.requirementPerTick, false)} did.
 *
 * <p><b>The duration multiplier.</b> This is the one requirement that consumes it. The original scaled every
 * per-tick energy draw by {@code parallelism * durationMultiplier} ({@code RequirementEnergy#doEnergyIO},
 * {@code maxMultiplier = parallelism * durationMultiplier}), so a modifier that shortens a craft does not also
 * reduce what that craft costs in total. The multiplier is pushed in by the recipe through
 * {@link #setDurationMultiplier}.
 *
 * <p><b>Parallelism.</b> The original's ceiling is exact rather than approximate: {@code getMaxParallelism}
 * ran the simulation at the requested multiplier and divided by the per-copy rate, which for energy is the
 * same as {@code floor(available / ratePerCopy)} — the arithmetic below.
 */
public final class EnergyRequirement extends MachineRequirement {

    /** The original's own two messages ({@code RequirementEnergy.java:138-146}), key for key. */
    public static final String FAILURE_ENERGY_INPUT = "craftcheck.failure.energy.input";
    public static final String FAILURE_ENERGY_OUTPUT_SPACE = "craftcheck.failure.energy.output.space";

    private final long energyPerTick;

    /** {@code duration / modifiedDuration}; pushed in by {@link MachineRecipe} when modifiers shorten a craft. */
    private float durationMultiplier = 1.0F;
    private record ModifiedRate(RecipeModifiers modifiers, long value) {}
    private volatile ModifiedRate cachedRate;

    public EnergyRequirement(IOType ioType, long energyPerTick) {
        super(ioType, true);
        this.energyPerTick = Math.max(0L, energyPerTick);
    }

    @Override
    public RecipeModifier.Target modifierTarget() {
        return RecipeModifier.Target.ENERGY;
    }

    @Override
    public int sortingWeight() {
        return PRIORITY_WEIGHT_ENERGY;
    }

    @Override
    public RequirementType type() {
        return Holder.TYPE;
    }

    /** 见 {@code ItemRequirement.Holder}：这个嵌套类存在的唯一理由是避开类初始化顺序。 */
    private static final class Holder {
        private static final RequirementType TYPE = RequirementTypes.ENERGY;
    }

    public long energyPerTick() {
        return this.energyPerTick;
    }

    public float durationMultiplier() {
        return this.durationMultiplier;
    }

    public void setDurationMultiplier(float multiplier) {
        this.durationMultiplier = multiplier <= 0.0F ? 1.0F : multiplier;
    }

    /** The energy one copy draws or produces per tick, after this requirement's modifiers. */
    public long modifiedEnergyPerTick(RecipeModifiers modifiers) {
        ModifiedRate cached = cachedRate;
        if (cached != null && cached.modifiers() == modifiers) return cached.value();
        long value = EnergyMath.modified(energyPerTick, modifiers, ioType());
        cachedRate = new ModifiedRate(modifiers, value);
        return value;
    }

    /** Existing adapters round the final positive baked amount; runtime payments still use floor. */
    public long bakedEnergyPerTick(RecipeModifiers modifiers) {
        return EnergyMath.baked(energyPerTick, modifiers, ioType());
    }

    /** What one tick of this craft draws, across every copy and accounting for a shortened duration.
     *
     * <p>0.105.0（E9 能量行）改为 public：旧版 {@code MMInfoProvider.getEnergyRequired :306-320}
     * 展示的正是这条链（需求值 × modifiers × durationMul × parallelism），Jade 的能量快照
     * （{@code MachineControllerBlockEntity#energySnapshot}）复用同一算式——展示与结算
     * 永远是同一个数字，不会各算各的。
     */
    public long perTickDraw(RecipeModifiers modifiers) {
        return perTickDraw(modifiers, parallelism(), durationMultiplier);
    }

    /** Explicit craft parameters for read-only displays; unaffected requirements still settle one copy. */
    public long perTickDraw(RecipeModifiers modifiers, int copies, float durationMultiplier) {
        long perCopy = modifiedEnergyPerTick(modifiers);
        if (perCopy <= 0L) {
            return 0L;
        }
        int parallelism = isParallelizeUnaffected() ? 1 : Math.max(1, copies);
        return EnergyMath.draw(perCopy, parallelism, durationMultiplier);
    }

    @Override
    public boolean canSatisfy(HatchCollection ports) {
        return canSatisfy(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canSatisfy} with modifiers, for the whole {@code parallelism} copies the craft settles. */
    public boolean canSatisfy(HatchCollection ports, RecipeModifiers modifiers) {
        long draw = perTickDraw(modifiers);
        if (draw <= 0L) {
            return true;
        }
        if (this.ioType() == IOType.INPUT) {
            return ports.storedEnergy() >= draw;
        }
        return IngredientIo.canInsertEnergy(ports.energyOutputs(), draw);
    }

    /**
     * The original's two energy messages ({@code RequirementEnergy.java:138-146}): not enough stored energy, or
     * output hatches that are already full. Only asked after {@link #canSatisfy} has already answered {@code false}.
     */
    @javax.annotation.Nullable
    @Override
    public String startFailure(HatchCollection ports) {
        return this.ioType() == IOType.INPUT ? FAILURE_ENERGY_INPUT : FAILURE_ENERGY_OUTPUT_SPACE;
    }

    @Override
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        if (!isParallelizable()) {
            return 1;
        }
        long perCopy = modifiedEnergyPerTick(modifiers);
        if (perCopy <= 0L) {
            return ceiling;
        }
        long available;
        if (this.ioType() == IOType.INPUT) {
            available = ports.storedEnergy();
        } else {
            available = IngredientIo.insertCapacity(ports.energyOutputs());
        }
        // Rounding/minimums apply once to the entire draw, not to each copy's divisor. This monotone
        // exact search is an upper bound only; shared resource reservation still decides admission.
        int low = 0, high = Math.max(0, ceiling);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (EnergyMath.draw(perCopy, mid, durationMultiplier) <= available) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    @Override
    public boolean reserve(HatchCollection ports, RecipeModifiers modifiers) {
        return tick(ports, null, modifiers);
    }

    @Override
    public boolean tick(HatchCollection ports, RandomSource random) {
        return tick(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #tick} with modifiers. */
    public boolean tick(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        long draw = perTickDraw(modifiers);
        if (draw <= 0L) {
            return true;
        }
        if (this.ioType() == IOType.INPUT) {
            return IngredientIo.extractEnergy(ports.energyInputs(), draw);
        }
        return IngredientIo.insertEnergy(ports.energyOutputs(), draw);
    }

    @Override
    public String describe() {
        // The unit is a display choice (`display.energy.Display_Energy_Type`), and the number is scaled for display
        // only — the stored amount and the draw are untouched. The original scaled every energy number it printed
        // the same way, in `TooltipEnergyInput` as much as in the hatch GUI.
        com.reborn.modularmachinery.config.EnergyDisplay display =
                com.reborn.modularmachinery.config.ModConfig.energyDisplay();
        return (this.ioType() == IOType.INPUT ? "" : "+")
                + com.reborn.modularmachinery.config.DisplayNumbers.abbreviated(display.display(this.energyPerTick))
                + " " + display.label() + "/t";
    }
}
