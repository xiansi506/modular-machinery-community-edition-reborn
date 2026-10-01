package com.reborn.modularmachinery.machine;

import com.google.gson.JsonParseException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The static, data-driven structure of a machine definition.
 *
 * <p>Coordinates are relative to the controller, which sits at {@code (0,0,0)} and is not part of the pattern
 * — matching the original, where {@code DynamicMachine} added {@code parts} and then removed the origin. The
 * pattern may span any shape and any number of Y levels; the bounding box is derived from the coordinates,
 * also as in {@code BlockArray.updateSize}.
 *
 * <p>Each position holds a list of acceptable blocks (the original's {@code elements} array).
 */
public final class MachinePattern {

    private final Map<BlockPos, List<BlockMatcher>> positions;
    private final BlockPos min;
    private final BlockPos max;
    private final BlockPos size;

    private MachinePattern(Map<BlockPos, List<BlockMatcher>> positions,
                          BlockPos min, BlockPos max, BlockPos size) {
        this.positions = Collections.unmodifiableMap(positions);
        this.min = min;
        this.max = max;
        this.size = size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<BlockPos, List<BlockMatcher>> positions() {
        return this.positions;
    }

    public int partCount() {
        return this.positions.size();
    }

    public BlockPos min() {
        return this.min;
    }

    public BlockPos max() {
        return this.max;
    }

    public BlockPos size() {
        return this.size;
    }

    /** {@code true} when every pattern position holds one of its accepted blocks. */
    public boolean matches(Level level, BlockPos controller) {
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : this.positions.entrySet()) {
            BlockPos at = controller.offset(entry.getKey());
            if (!level.isLoaded(at)) {
                return false;
            }
            if (!anyMatches(level.getBlockState(at), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** The first relative position that does not match, or {@code null} when the structure is complete. */
    public BlockPos firstMismatch(Level level, BlockPos controller) {
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : this.positions.entrySet()) {
            BlockPos at = controller.offset(entry.getKey());
            if (!level.isLoaded(at) || !anyMatches(level.getBlockState(at), entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean anyMatches(BlockState state, List<BlockMatcher> accepted) {
        for (BlockMatcher matcher : accepted) {
            if (matcher.matches(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rotates the whole pattern one quarter turn counter-clockwise around Y, mirroring the original
     * {@code MiscUtils.rotateYCCW(pos)} = {@code (z, y, -x)}.
     */
    public MachinePattern rotateYCounterClockwise() {
        Builder builder = builder();
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : this.positions.entrySet()) {
            BlockPos pos = entry.getKey();
            builder.add(new BlockPos(pos.getZ(), pos.getY(), -pos.getX()), entry.getValue());
        }
        return builder.build();
    }

    public static final class Builder {

        private final Map<BlockPos, List<BlockMatcher>> positions = new LinkedHashMap<>();
        private BlockPos min = new BlockPos(0, 0, 0);
        private BlockPos max = new BlockPos(0, 0, 0);
        private boolean empty = true;

        /** Adds accepted blocks at a relative position, merging with anything already there. */
        public Builder add(BlockPos pos, List<BlockMatcher> accepted) {
            if (accepted.isEmpty()) {
                throw new JsonParseException("Pattern position " + pos + " has an empty element list");
            }
            positions.computeIfAbsent(pos, key -> new ArrayList<>()).addAll(accepted);
            grow(pos);
            return this;
        }

        private void grow(BlockPos pos) {
            if (empty) {
                min = pos;
                max = pos;
                empty = false;
                return;
            }
            min = new BlockPos(Math.min(min.getX(), pos.getX()),
                    Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()),
                    Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }

        public boolean isEmpty() {
            return positions.isEmpty();
        }

        public MachinePattern build() {
            BlockPos size = empty
                    ? new BlockPos(0, 0, 0)
                    : new BlockPos(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1,
                            max.getZ() - min.getZ() + 1);
            return new MachinePattern(new LinkedHashMap<>(positions), min, max, size);
        }
    }
}
