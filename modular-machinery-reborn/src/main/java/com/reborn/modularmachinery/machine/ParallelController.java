package com.reborn.modularmachinery.machine;

/**
 * A block entity that contributes {@code parallelism} to whatever machine it stands in.
 *
 * <p>This is the successor of the original's {@code TileParallelController.ParallelControllerProvider}, which
 * was one of the {@code MachineComponent}s the structure scan collected
 * ({@code TileMultiblockMachineController#getMaxParallelism}, {@code :289-300}).
 *
 * <p>M6d needs a marker of its own because <b>this project has no mechanism for attributing a non-hatch block to
 * a machine</b>: {@code MachineControllerBlockEntity#collectHatches} only recognises
 * {@code MachineHatchBlockEntity}, and the scoping document is explicit (M6b/M6d rows, §5.2) that a component
 * must <b>not</b> be disguised as a hatch — that would pollute {@code HatchKind} and the set of blocks a
 * structure position accepts. A narrow interface keeps the two collection paths separate and lets the same walk
 * be reused by M6b's upgrade bus later.
 */
public interface ParallelController {

    /** The copies per craft this controller currently contributes, the original's {@code getParallelism()}. */
    int parallelism();

    /** This controller's own ceiling, the original's {@code getMaxParallelism()} — the screen shows it. */
    int maxParallelism();
}
