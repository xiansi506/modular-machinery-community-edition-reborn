package com.reborn.modularmachinery.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded machine definitions, replacing the hardcoded 3×3 iron ring and the hardcoded {@code "basic"}
 * machine name.
 *
 * <p>Patterns are pre-rotated for all four horizontal controller facings at load time. The controller's
 * {@code FACING} selects which rotation is tested, mirroring the original
 * {@code TileMultiblockMachineController} which rotated the pattern counter-clockwise until it matched the
 * controller's facing. Testing every rotation instead would let a machine form in an orientation that
 * disagrees with its controller.
 */
public final class MachineRegistry {

    private static volatile Map<ResourceLocation, MachineDefinition> machines = Map.of();
    private static volatile Map<ResourceLocation, MachinePattern[]> rotations = Map.of();

    private MachineRegistry() {
    }

    /** Replaces the registry contents; called by {@link MachineLoader} on every resource reload. */
    public static void replace(Map<ResourceLocation, MachineDefinition> loaded) {
        // Insertion order is preserved so that, when two definitions could match the same structure, the
        // outcome does not depend on hash iteration order. Map.copyOf would not guarantee that.
        Map<ResourceLocation, MachineDefinition> copy = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        Map<ResourceLocation, MachinePattern[]> rotated = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineDefinition> entry : copy.entrySet()) {
            MachinePattern base = entry.getValue().pattern();
            MachinePattern[] variants = new MachinePattern[4];
            variants[0] = base;
            for (int i = 1; i < 4; i++) {
                variants[i] = variants[i - 1].rotateYCounterClockwise();
            }
            rotated.put(entry.getKey(), variants);
        }
        rotations = Collections.unmodifiableMap(rotated);
        machines = copy;
    }

    public static Collection<MachineDefinition> all() {
        return machines.values();
    }

    public static int size() {
        return machines.size();
    }

    public static Optional<MachineDefinition> byId(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id));
    }

    public static boolean contains(@Nullable ResourceLocation id) {
        return id != null && machines.containsKey(id);
    }

    /**
     * Counter-clockwise quarter turns from {@code NORTH} to {@code facing}: north 0, west 1, south 2, east 3.
     */
    public static int rotationStepsFor(Direction facing) {
        Direction direction = Direction.NORTH;
        int steps = 0;
        while (direction != facing && steps < 4) {
            direction = direction.getCounterClockWise();
            steps++;
        }
        return steps % 4;
    }

    /**
     * Finds the first registered machine whose pattern matches the world for the controller's facing.
     *
     * @return the match, or {@code null} when no machine is formed here
     */
    @Nullable
    public static Match findMatch(Level level, BlockPos controller, Direction facing) {
        return findMatch(level, controller, facing, null, null);
    }

    /**
     * Finds a match using the original {@code checkStructure} search order:
     *
     * <ol>
     *   <li><b>The blueprint's machine is tried first.</b> A blueprint in the controller's slot is an explicit
     *       statement of which machine this controller is for, so it outranks everything else — including a
     *       bound controller's own machine, which is what the original did when it checked
     *       {@code getBlueprintMachine()} before {@code parentMachine}.</li>
     *   <li><b>A machine-bound controller then checks its own machine</b>, and never anything else. If that
     *       machine requires a blueprint and the slotted one is not it, nothing forms — the original returned
     *       early here rather than falling through.</li>
     *   <li><b>Otherwise every definition is tried except those requiring a blueprint.</b> The original's
     *       {@code checkAllPatterns} skips them, and that skip is the whole point of {@code requires-blueprint}:
     *       such a machine can only ever form through its blueprint.</li>
     * </ol>
     *
     * @param blueprintMachine the machine named by the blueprint in the controller's slot, or {@code null}
     * @param boundMachine     the machine a per-machine controller is bound to, or {@code null} when generic
     */
    @Nullable
    public static Match findMatch(Level level, BlockPos controller, Direction facing,
                                  @Nullable ResourceLocation blueprintMachine,
                                  @Nullable ResourceLocation boundMachine) {
        int steps = rotationStepsFor(facing);

        if (blueprintMachine != null) {
            Match fromBlueprint = tryMachine(level, controller, steps, blueprintMachine);
            if (fromBlueprint != null) {
                return fromBlueprint;
            }
        }

        if (boundMachine != null) {
            MachineDefinition bound = machines.get(boundMachine);
            if (bound != null && bound.requiresBlueprint() && !boundMachine.equals(blueprintMachine)) {
                return null;
            }
            return tryMachine(level, controller, steps, boundMachine);
        }

        for (Map.Entry<ResourceLocation, MachinePattern[]> entry : rotations.entrySet()) {
            MachineDefinition machine = machines.get(entry.getKey());
            if (machine == null || machine.requiresBlueprint()) {
                continue;
            }
            if (entry.getValue()[steps].matches(level, controller)) {
                return new Match(machine, steps);
            }
        }
        return null;
    }

    @Nullable
    private static Match tryMachine(Level level, BlockPos controller, int steps, ResourceLocation id) {
        MachinePattern[] variants = rotations.get(id);
        MachineDefinition machine = machines.get(id);
        if (variants == null || machine == null) {
            return null;
        }
        return variants[steps].matches(level, controller) ? new Match(machine, steps) : null;
    }

    /** A formed machine plus the rotation that matched it. */
    public record Match(MachineDefinition machine, int rotationSteps) {
    }
}
