package com.reborn.modularmachinery.machine;

/**
 * The original {@code common.machine.RecipeFailureActions}: what a machine does when its active recipe stops
 * being satisfiable.
 *
 * <p>Parsed by {@code MachineLoader} since M1 and <b>consumed from 0.25.0</b>. The original's only consumer is
 * a failed per-tick IO check of a craft that has <b>already started</b>: {@code ActiveMachineRecipe#tick:87-98}
 * applies it when {@code RecipeCraftingContext#ioTick:238-298} fails, once per failed tick, and the entire
 * effect is {@link #afterFailedTick}. A failed <i>start</i> never reaches it, and neither does the other hold —
 * a craft whose result does not fit ({@code RecipeThread#onFinished:64-86}).
 *
 * <p>The field belongs to the <b>machine</b>, not to the recipe, and a factory thread ticks the same
 * {@code ActiveMachineRecipe}, so both engines are governed by it.
 *
 * <p>The original left the craft armed at progress 0, so a reset cost time and nothing else. This project's
 * factory engine behaves that way too ({@code FactoryThread} is armed by its slot, not by its progress), but the
 * single-craft engine's {@code progress == 0} means "no craft" — see
 * {@code MachineControllerBlockEntity#applyFailureAction}.
 */
public enum FailureAction {

    /** Drop all progress and start over. */
    RESET,
    /** Keep the current progress. */
    STILL,
    /** Lose progress gradually. */
    DECREASE;

    public static FailureAction byName(String name) {
        for (FailureAction action : values()) {
            if (action.name().equalsIgnoreCase(name)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown failure-action '" + name
                + "'; expected one of reset, still, decrease");
    }

    /**
     * The progress a craft keeps after one tick that could not be served — the original's
     * {@code ActiveMachineRecipe#doFailureAction:106-115}, whose three cases are exactly {@code tick = 0},
     * nothing at all, and {@code tick--} while it is above zero.
     *
     * <p>Pure, so both engines and the offline harness can drive it without a world.
     */
    public int afterFailedTick(int progress) {
        return switch (this) {
            case RESET -> 0;
            case DECREASE -> Math.max(0, progress - 1);
            case STILL -> progress;
        };
    }
}
