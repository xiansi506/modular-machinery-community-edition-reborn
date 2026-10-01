package com.reborn.modularmachinery.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A block entity that carries upgrades for whatever machine it stands in.
 *
 * <p>This is the upgrade bus's narrow face, and it exists for exactly one reason: <b>this project has no
 * mechanism for attributing a non-hatch block to a machine</b>. Before M6d the only thing a structure scan ever
 * collected was a {@code MachineHatchBlockEntity}, and the scoping document is explicit (M6b/M6d rows, §5.2)
 * that such a block must <b>not</b> be disguised as a hatch — that would add it to {@code HatchKind} and to the
 * set of blocks a structure position accepts, and would let the recipe engine draw from it as a port.
 *
 * <p>M6d answered that with a second collection path walking the same {@code machine.pattern().positions()},
 * keyed off the narrow marker {@link com.reborn.modularmachinery.machine.ParallelController}. The bus needs
 * <b>two-way</b> attribution rather than one-way: the parallel controller only had to be <i>counted</i>, while
 * the bus has to be <i>asked</i> what it holds, and it has to show the machine it belongs to in its own GUI. So
 * the same path gains one extra call — the controller passing itself to the bus it just found
 * ({@link #bindMachine}) — which is the original's {@code UpgradeBusProvider#boundMachine}
 * ({@code TileUpgradeBus.java:236-245}).
 *
 * <p>Nothing here mentions hatches, items, fluids or energy: a bus contributes recipe modifiers and nothing
 * else, which is what keeps the two collection paths from bleeding into each other.
 */
public interface UpgradeBusUtility {

    /**
     * Registers the controller standing at {@code controllerPos} as belonging to the machine {@code machineId}.
     *
     * <p>This is the original's {@code boundMachine(controller)}: it records the pair, and does nothing when the
     * pair is already recorded. The bus drops the pairing again on its own, from its periodic reconcile, when
     * the controller is gone or has become a different machine — which is how the original's
     * {@code doRestrictedTick} ({@code TileUpgradeBus.java:66-92}) worked too.
     *
     * @return {@code true} when this call changed the recorded set, so the caller can decide whether the change
     *         needs to reach the client
     */
    boolean bindMachine(BlockPos controllerPos, ResourceLocation machineId);

    /** Every machine this bus is currently bound to, as controller position to machine name. */
    Map<BlockPos, ResourceLocation> boundMachines();

    /** Everything the bus's slots hold right now, merged by upgrade declaration. */
    UpgradeStack.Bag upgrades();

    /**
     * The recipe modifiers this bus contributes to a machine: the upgrades it holds that
     * {@code machineId} accepts, each after the original's per-copy stacking.
     *
     * @param machineId the machine the modifiers are for, or {@code null} to skip the compatibility filter
     */
    default com.reborn.modularmachinery.recipe.RecipeModifiers modifiersFor(@Nullable ResourceLocation machineId) {
        return UpgradeEffects.of(upgrades(), machineId);
    }
}
