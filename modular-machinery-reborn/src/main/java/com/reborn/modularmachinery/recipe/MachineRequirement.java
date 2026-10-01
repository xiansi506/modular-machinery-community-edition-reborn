package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;

/**
 * One entry of a machine recipe's {@code requirements} array.
 *
 * <p>The original drove crafting through {@code ComponentRequirement} subclasses with three lifecycle hooks,
 * and that shape is what decides when things happen:
 *
 * <ul>
 *   <li>{@code startCrafting} consumes <b>inputs</b> — item and fluid inputs are taken <b>when the craft
 *       begins</b>, not when it completes;</li>
 *   <li>{@code doIOTick} runs every tick for the {@code PerTick} family — energy and per-tick fluids;</li>
 *   <li>{@code finishCrafting} inserts <b>outputs</b> at the end, rolling each output's {@code chance}
 *       independently.</li>
 * </ul>
 *
 * <p>Before finishing, the original re-ran the space check as if every output were guaranteed
 * ({@code ResultChance.GUARANTEED}), so a craft cannot start ending when the result would not fit.
 * {@link #canSatisfy} therefore always tests the full amount and ignores {@code chance}.
 *
 * <h2>Parallelism</h2>
 *
 * <p>The original's "parallelism" is <b>not</b> a thread pool: it is a number {@code N} on the recipe, and
 * every requirement moves {@code N} copies of its own amount per settlement
 * ({@code RequirementItem#doItemIOInternal}, {@code ComponentRequirement.Parallelizable}). The same shape is
 * reproduced here:
 *
 * <ul>
 *   <li>{@link #setParallelism} is called once, when the craft starts, with the value
 *       {@link #parallelLimit} computed. It stays fixed for the whole craft — the original also froze it for
 *       the duration of a craft and only revisited it in {@code canRestartCrafting}.</li>
 *   <li>{@link #canSatisfy} and {@link #apply} / {@link #tick} then work on
 *       {@code per-copy amount * parallelism}.</li>
 *   <li>{@link #parallelLimit} answers the original's
 *       {@code ComponentRequirement.Parallelizable#getMaxParallelism}: the largest {@code N} at or below the
 *       requested ceiling that this requirement could actually serve. The recipe takes the minimum over every
 *       parallelisable requirement, exactly as {@code RecipeCraftingContext#getMaxParallelism} did.</li>
 * </ul>
 *
 * <p>A requirement may opt out with {@link #setParallelizeUnaffected}, which is the original's
 * {@code isParallelizeUnaffected}: such a requirement is left out of the minimum and always works on one copy
 * (a catalyst is the original's example).
 */
public abstract class MachineRequirement {

    /**
     * The ceiling {@link #parallelLimit} returns when nothing bounds a requirement. The original had no such
     * constant: it passed in {@code activeRecipe.getMaxParallelism()}, which is itself capped by the machine's
     * {@code max-parallelism} (default 2048, {@code Config.maxMachineParallelism}). This value only has to be
     * larger than any machine ceiling, and is {@code Integer.MAX_VALUE / 4} so that the multiplications inside
     * the requirement classes cannot overflow.
     */
    public static final int UNBOUNDED_PARALLELISM = Integer.MAX_VALUE / 4;

    private final IOType ioType;
    private final boolean perTick;

    /** How many copies the current craft settles per requirement. Set once, at craft start. */
    private int parallelism = 1;

    /** When true this requirement never scales with parallelism, the original's {@code parallelizeUnaffected}. */
    private boolean parallelizeUnaffected;

    protected MachineRequirement(IOType ioType, boolean perTick) {
        this.ioType = ioType;
        this.perTick = perTick;
    }

    public IOType ioType() {
        return this.ioType;
    }

    public boolean perTick() {
        return this.perTick;
    }

    /** Inputs consumed once, when the craft starts. */
    public final boolean isStartPhase() {
        return this.ioType == IOType.INPUT && !this.perTick;
    }

    /** Requirements evaluated every tick of the craft. */
    public final boolean isTickPhase() {
        return this.perTick;
    }

    /** Outputs produced once, when the craft completes. */
    public final boolean isFinishPhase() {
        return this.ioType == IOType.OUTPUT && !this.perTick;
    }

    // ------------------------------------------------------------------ parallelism

    public int parallelism() {
        return this.parallelism;
    }

    /**
     * Fixes how many copies the rest of the craft settles. The original required exactly this: a parallelisable
     * requirement "should strictly adhere to that number of parallels when checking the requirement or the
     * work, and you should not increase or decrease the number of parallels on your own".
     */
    public void setParallelism(int parallelism) {
        this.parallelism = Math.max(1, parallelism);
    }

    public boolean isParallelizeUnaffected() {
        return this.parallelizeUnaffected;
    }

    public void setParallelizeUnaffected(boolean unaffected) {
        this.parallelizeUnaffected = unaffected;
    }

    /**
     * The largest number of copies at or below {@code ceiling} that this requirement can supply (inputs) or
     * hold (outputs). {@code 0} when it cannot even serve one copy.
     *
     * <p>Unlike {@link #canSatisfy}, which is an all-or-nothing check, this has to answer "how many" — that is
     * the whole content of the original's {@code getMaxParallelism} implementations, each of which fed a
     * multiplier into a simulation routine and divided the served amount by the per-copy amount.
     */
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        return this.parallelizeUnaffected ? 1 : UNBOUNDED_PARALLELISM;
    }

    /** Whether this requirement takes part in the recipe's parallelism minimum. */
    public final boolean isParallelizable() {
        return !this.parallelizeUnaffected;
    }

    /** Which modifier target this requirement belongs to, the original's {@code requirementType}. */
    public abstract RecipeModifier.Target modifierTarget();

    // ------------------------------------------------------------------ lifecycle

    /** Pure check: could this requirement be met right now? Never mutates the ports. */
    public abstract boolean canSatisfy(HatchCollection ports);

    /**
     * Why {@link #canSatisfy} answers {@code false}, as a translation key, or {@code null} when this requirement
     * has nothing to say.
     *
     * <p>The original's {@code canStartCrafting} returned a {@code CraftCheck} that carried this message and the
     * controller printed it. This projection's lifecycle is a boolean, so the message needs its own door — and it
     * is only ever asked after {@code canSatisfy} has already answered {@code false}, which keeps the two from
     * disagreeing (see {@code MachineRecipe#startFailure}).
     *
     * <p>Only {@code interface_number_input} implements it today; every other requirement keeps the original's
     * "just insufficient" behaviour of reporting a bare {@code idle}.
     */
    @javax.annotation.Nullable
    public String startFailure(HatchCollection ports) {
        return null;
    }

    /** Consumes (input) or produces (output) once. Used for the start and finish phases. */
    public boolean apply(HatchCollection ports, RandomSource random) {
        return true;
    }

    /** Runs once per tick, for per-tick requirements only. */
    public boolean tick(HatchCollection ports, RandomSource random) {
        return true;
    }

    /** Short human-readable summary, used by JEI. */
    public abstract String describe();
}
