package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.machine.SmartInterfaceType;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

/**
 * The {@code interface_number_input} requirement — the original's {@code RequirementInterfaceNumInput} (112
 * lines), and the <b>only</b> consumer of a smart-interface value in the whole original.
 *
 * <h2>What it does</h2>
 *
 * <p>It consumes nothing and produces nothing. It <b>gates</b>: the craft may only start when the value the
 * machine's smart data interface currently holds for the named type is inside the range the recipe asked for.
 * The original's comparison was inclusive on both ends ({@code value >= minValue && value <= maxValue},
 * {@code :82}), and it is reproduced as written — a recipe that wants a strictly-greater test writes
 * {@code minValue} just above the value it rejects.
 *
 * <h2>The two failure messages, verbatim</h2>
 *
 * <p>The original produced two distinct failures and both are reused here word for word:
 *
 * <ul>
 *   <li>{@code component.missing.modularmachinery.interface.number} — the machine holds no value for this type
 *       ({@code getMachineData(type) == null}, {@code :78-80}). In this projection that means the machine's
 *       definition does not declare the type, which is exactly the original's
 *       {@code foundMachine.smartInterfaceTypesIsEmpty} outcome: an interface the machine never asked for
 *       cannot answer a requirement.</li>
 *   <li>{@code craftcheck.failure.interface.number.notequal} — a value exists but is outside the range
 *       ({@code :86-89}). The type may replace it with its own message through {@code notequal}, which is the
 *       original's {@code type.getNotEqualMessage()}.</li>
 * </ul>
 *
 * <h2>Parallelism</h2>
 *
 * <p>The original's class did <b>not</b> implement {@code ComponentRequirement.Parallelizable}, so
 * {@code RecipeCraftingContext#getMaxParallelism} skipped it entirely and a value check never bounded the copy
 * count — the same value is simply tested once for the whole settlement. {@link #parallelLimit} is therefore
 * left at the base class's answer, and this requirement is deliberately <b>not</b> marked
 * {@code parallelizeUnaffected}: that flag exists for requirements that must not be multiplied, and this one is
 * not multiplied either way.
 *
 * <h2>Modifiers</h2>
 *
 * <p>No modifier touches it. The original had no target for this requirement type
 * ({@code RequirementTypeInterfaceNumInput} registered a bare type with no modifier table), which is why
 * {@link #modifierTarget()} answers a fixed kind and why nothing here ever calls
 * {@code RecipeModifiers#apply}: {@code min} / {@code max} come from the recipe file unchanged.
 */
public final class InterfaceNumberInputRequirement extends MachineRequirement {

    /** The original's {@code component.missing.modularmachinery.interface.number}, verbatim. */
    public static final String MISSING_KEY = "component.missing.modularmachinery.interface.number";

    private final String type;
    private final float minValue;
    private final float maxValue;
    /** The declaring machine's own mismatch message, or {@code null} for the original's default key. */
    @Nullable
    private final String notEqual;

    public InterfaceNumberInputRequirement(String type, float minValue, float maxValue, @Nullable String notEqual) {
        // INPUT and not per-tick: the original built it with IOType.INPUT and it belongs to the start phase,
        // which is the phase the controller checks before paying for anything.
        super(IOType.INPUT, false);
        this.type = type;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.notEqual = notEqual == null || notEqual.isBlank() ? null : notEqual;
    }

    /** The original's {@code addSmartInterfaceDataInput(type, value)} overload: min and max are the same. */
    public InterfaceNumberInputRequirement(String type, float value) {
        this(type, value, value, null);
    }

    public String interfaceType() {
        return this.type;
    }

    public float minValue() {
        return this.minValue;
    }

    public float maxValue() {
        return this.maxValue;
    }

    /** Whether the range is a single value, i.e. the original's {@code minValue == maxValue} case. */
    public boolean isExactValue() {
        return this.minValue == this.maxValue;
    }

    /** The key a failed range check reports, the original's {@code notEqualMessage} fallback included. */
    public String notEqualKey() {
        return this.notEqual == null ? SmartInterfaceType.DEFAULT_NOT_EQUAL_KEY : this.notEqual;
    }

    /**
     * The modifier kind this requirement reports. It is only ever read by
     * {@code RecipeModifiers#apply(MachineRequirement, …)}, which this class never calls, so its value cannot
     * change an outcome — it is a fixed kind rather than {@code ALL} so that a wildcard structure modifier does
     * not appear to reach a requirement it cannot touch either.
     */
    @Override
    public RecipeModifier.Target modifierTarget() {
        return RecipeModifier.Target.ITEM;
    }

    /** The value this machine currently holds for the named type, or {@code null} when it holds none. */
    @Nullable
    public Float currentValue(HatchCollection ports) {
        return ports.smartInterfaceValue(this.type);
    }

    @Override
    public boolean canSatisfy(HatchCollection ports) {
        return canSatisfy(ports, this.type, this.minValue, this.maxValue);
    }

    /**
     * The check as a pure function of a value source and a range, so the offline acceptance harness can drive
     * the exact arithmetic the block entity reaches at runtime without building a controller.
     *
     * <p>{@code null} is the "no such interface" case and is <b>not</b> the same as zero: a machine that declares
     * the type and was never edited holds the declared default, which may itself be zero.
     */
    public static boolean canSatisfy(HatchCollection ports, String type, float minValue, float maxValue) {
        Float value = ports.smartInterfaceValue(type);
        return value != null && value >= minValue && value <= maxValue;
    }

    /**
     * Why {@link #canSatisfy} answered {@code false} — the original's {@code canStartCrafting} body, returning
     * the message instead of {@code CraftCheck.failure(...)} so the controller can print it.
     *
     * <p>Reusing the same value lookup as {@link #canSatisfy} is deliberate: {@code MachineRecipe#startFailure}
     * only asks a requirement that has already answered {@code false}, so the two can never disagree about what
     * happened, and there is no second copy of the comparison to drift.
     *
     * <p>The range message is the <b>declared type's</b> own when the machine gave one
     * ({@code SmartInterfaceType#notequal}, the original's {@code type.getNotEqualMessage()}), and the
     * original's default otherwise. The type is read off the same ports object the value came from, so the
     * message cannot be produced by a machine other than the one that supplied the value.
     */
    @Nullable
    @Override
    public String startFailure(HatchCollection ports) {
        Float value = ports.smartInterfaceValue(this.type);
        if (value == null) {
            return MISSING_KEY;
        }
        if (value >= this.minValue && value <= this.maxValue) {
            return null;
        }
        if (this.notEqual != null) {
            return this.notEqual;
        }
        SmartInterfaceType declared = ports.declaredSmartInterface(this.type);
        return declared == null ? SmartInterfaceType.DEFAULT_NOT_EQUAL_KEY : declared.notEqualMessage();
    }

    /**
     * Nothing to consume. The original set inputs up in {@code startCrafting} only through
     * {@code doItemIO}/{@code doEnergyIO} hooks it never implemented; its whole body was the range check, which
     * already happened in {@code canStartCrafting}. Returning {@code true} keeps the phase honest: the check
     * refused the craft, not the payment.
     */
    @Override
    public boolean apply(HatchCollection ports, RandomSource random) {
        return true;
    }

    @Override
    public String describe() {
        return "interface " + this.type + (isExactValue()
                ? " = " + trim(this.minValue)
                : " in [" + trim(this.minValue) + ", " + trim(this.maxValue) + "]");
    }

    /** The value as the recipe file wrote it: {@code 3} rather than {@code 3.0}, {@code 3.5} kept as-is. */
    private static String trim(float value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Float.toString(value);
    }
}
