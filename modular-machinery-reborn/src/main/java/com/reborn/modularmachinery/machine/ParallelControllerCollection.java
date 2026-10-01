package com.reborn.modularmachinery.machine;

import java.util.List;

/**
 * The parallel controllers found inside one formed structure.
 *
 * <p>Deliberately a <b>separate</b> collection from {@link HatchCollection}: a parallel controller is not a
 * hatch and must not be routed into the item/fluid/energy buckets, which is what the scoping document warns
 * about (§5.2, "总线归属无现成机制 … 不要把它们伪装成仓口").
 *
 * <p>The one number the rest of the machine cares about is {@link #parallelism()}: the sum the original added
 * into {@code getInternalParallelism()} ({@code TileMultiblockMachineController.java:292-293}). Everything else
 * about the arithmetic — the clamp to {@code max-parallelism} and the floor at 1 — lives in
 * {@link ParallelismLimit}, where the offline acceptance harness can drive it without a world.
 */
public final class ParallelControllerCollection {

    public static final ParallelControllerCollection EMPTY = new ParallelControllerCollection(List.of());

    private final List<ParallelController> controllers;

    private ParallelControllerCollection(List<ParallelController> controllers) {
        this.controllers = List.copyOf(controllers);
    }

    public static ParallelControllerCollection of(List<? extends ParallelController> controllers) {
        return controllers.isEmpty() ? EMPTY : new ParallelControllerCollection(List.copyOf(controllers));
    }

    /** How many controllers the structure contains; shown in diagnostics and asserted by the harness. */
    public int controllerCount() {
        return this.controllers.size();
    }

    /**
     * The summed contribution, the original's running {@code parallelism += provider.getParallelism()}. A
     * controller whose stored value somehow went negative contributes nothing rather than lowering the sum.
     */
    public int parallelism() {
        long total = 0L;
        for (ParallelController controller : this.controllers) {
            total += Math.max(0, controller.parallelism());
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    public List<ParallelController> controllers() {
        return this.controllers;
    }

    @Override
    public String toString() {
        return "ParallelControllerCollection[" + this.controllers.size() + " controllers, parallelism="
                + parallelism() + "]";
    }
}
