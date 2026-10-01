package com.reborn.modularmachinery.machine;

/**
 * The one place the machine's parallelism ceiling is computed.
 *
 * <p>Line for line the original's {@code TileMultiblockMachineController#getMaxParallelism}
 * ({@code :289-300}):
 *
 * <pre>
 * int parallelism = foundMachine.getInternalParallelism();
 * int maxParallelism = foundMachine.getMaxParallelism();
 * for (ParallelControllerProvider provider : foundParallelControllers) {
 *     parallelism += provider.getParallelism();
 *     if (parallelism &gt;= maxParallelism) return maxParallelism;
 * }
 * return Math.max(1, parallelism);
 * </pre>
 *
 * <p>Three details are preserved:
 *
 * <ul>
 *   <li>the <b>controllers' current values</b> are added, not their tier ceilings, so a controller dialled down
 *       from 64 to 8 really contributes 8;</li>
 *   <li>the sum is clamped to {@code max-parallelism} — never raised above it, whatever the tiers say;</li>
 *   <li>the result is floored at <b>1</b>, so a machine is always able to run one copy.</li>
 * </ul>
 *
 * <p>The original's early {@code return maxParallelism} inside the loop is the same clamp, just evaluated early;
 * summing first and clamping once is equivalent except for overflow, which the {@code long} accumulation rules
 * out. {@code parallelizable: false} collapses the ceiling to 1, which is the original's
 * {@code isParallelized()} doing the same job one level up ({@code :318-324}).
 *
 * <p>It is a pure function of the definition plus one integer on purpose: the offline acceptance harness lives
 * outside {@code src/} and cannot build a {@code Level}, and "a number appeared in the GUI" is explicitly not
 * accepted as evidence for this slice.
 */
public final class ParallelismLimit {

    /**
     * The ceiling for a machine with the given controllers.
     *
     * @param definition            the formed machine's definition
     * @param controllerParallelism the summed {@code parallelism()} of the parallel controllers in the structure
     *                              ({@link ParallelControllerCollection#parallelism()})
     * @return at least 1, at most {@code max-parallelism}
     */
    public static int resolve(MachineDefinition definition, int controllerParallelism) {
        if (definition == null || !definition.parallelizable()) {
            return 1;
        }
        long sum = Math.max(0, definition.internalParallelism()) + Math.max(0L, controllerParallelism);
        long ceiling = Math.min(sum, Math.max(0, definition.maxParallelism()));
        return (int) Math.max(1L, ceiling);
    }

    private ParallelismLimit() {
    }
}
