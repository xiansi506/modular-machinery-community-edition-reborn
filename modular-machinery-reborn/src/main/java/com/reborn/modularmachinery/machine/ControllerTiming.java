package com.reborn.modularmachinery.machine;

/**
 * The controller's timing record, the stand-in for the original's {@code TimeRecorder}.
 *
 * <p>The original's controller screen ended with one line that reports how expensive a machine is:
 * {@code Avg: 124μs/t (Search: 3.4ms), WorkMode: ASYNC}. {@code usedTimeCache} and
 * {@code searchUsedTimeCache} were <b>static fields on {@code TileMultiblockMachineController}</b>
 * (its lines 128-130) — one pair shared by every controller in the world, written by whichever controller
 * ticked last. That is plainly a bug in the original (a per-block cost reported from a global), and it is not
 * reproduced: this record lives on the block entity, so the number describes <b>this</b> machine.
 *
 * <p><b>What is averaged.</b> The original averaged over a time window filled by whichever work mode ran. This
 * averages the last {@link #WINDOW} measurements, which is the same idea with a fixed window, so the first few
 * ticks of a freshly loaded machine converge instead of being dominated by the load spike.
 *
 * <p><b>WorkMode.</b> The original had three modes — {@code ASYNC}, {@code SEMI_SYNC}, {@code SYNC} — chosen by
 * whether its recipe search ran off-thread. This project's engine is synchronous by construction (there is no
 * {@code RecipeCraftingContext}, no thread pool and no search task; see the M6 scoping document §5.1). So
 * {@link #workMode()} is always {@link WorkMode#SYNC}, and it is reported as such rather than faked, because
 * "which mode is this machine running in" is exactly the question that line exists to answer.
 */
public final class ControllerTiming {

    /** How many measurements the rolling average covers. */
    public static final int WINDOW = 20;

    /**
     * The original's {@code TileMultiblockMachineController.WorkMode}, display strings included.
     *
     * <p>Only {@link #SYNC} can occur here; the other two are kept because the screen's string is built from a
     * mode and a future slice that does run a search off-thread will want them.
     */
    public enum WorkMode {
        ASYNC("\u00a7aASYNC\u00a7f"),
        SEMI_SYNC("\u00a7eSEMI-SYNC\u00a7f"),
        SYNC("\u00a7cSYNC\u00a7f");

        private final String displayName;

        WorkMode(String displayName) {
            this.displayName = displayName;
        }

        /** The mode's name with the original's own colour codes already applied. */
        public String displayName() {
            return this.displayName;
        }
    }

    private long usedTimeTotal;
    private long searchTimeTotal;
    private int samples;

    /** Records one tick's work: the machine cost in microseconds, the recipe search in microseconds. */
    public void record(long usedMicros, long searchMicros) {
        if (this.samples >= WINDOW) {
            // Halve both totals rather than keeping a ring buffer: the average is all that is ever read, and
            // this makes the window decay smoothly instead of snapping when a sample falls out.
            this.usedTimeTotal /= 2;
            this.searchTimeTotal /= 2;
            this.samples /= 2;
        }
        this.usedTimeTotal += Math.max(0L, usedMicros);
        this.searchTimeTotal += Math.max(0L, searchMicros);
        this.samples++;
    }

    /** Average microseconds of work per tick, the original's {@code usedTimeAvg()}. */
    public int usedTimeAvg() {
        return this.samples == 0 ? 0 : (int) (this.usedTimeTotal / this.samples);
    }

    /** Average microseconds spent searching for a recipe, the original's {@code recipeSearchUsedTimeAvg()}. */
    public int searchUsedTimeAvg() {
        return this.samples == 0 ? 0 : (int) (this.searchTimeTotal / this.samples);
    }

    public WorkMode workMode() {
        return WorkMode.SYNC;
    }

    public void reset() {
        this.usedTimeTotal = 0L;
        this.searchTimeTotal = 0L;
        this.samples = 0;
    }
}
