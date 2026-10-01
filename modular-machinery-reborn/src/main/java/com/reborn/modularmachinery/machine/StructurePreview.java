package com.reborn.modularmachinery.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The blocks a structure preview draws, and the box they occupy.
 *
 * <p>This is the server-safe half of the preview: it turns a {@link MachinePattern} into a list of positions and
 * concrete block states, which the client renderer then draws. Keeping it free of any client class means the
 * "what does this machine look like" question can be answered — and tested — without a renderer.
 *
 * <p>Three things it does that a naive reading of the pattern would miss:
 *
 * <ul>
 *   <li><b>It draws the controller at the origin.</b> The pattern never includes the controller's own position,
 *       but the controller is precisely the block a player must place to know where the machine goes, so a
 *       preview without it is unhelpful.</li>
 *   <li><b>It picks a representative state per position</b> using {@link BlockMatcher#representativeState()},
 *       so variants such as {@code blockcasing[casing=firebox]} appear as themselves rather than as whatever
 *       the block's default state happens to be.</li>
 *   <li><b>It keeps every accepted alternative per position</b>, so the preview can cycle through the blocks an
 *       element list allows (the original's "cycle replaceable blocks" button). The first alternative is always
 *       the state the static preview shows, so cycling is additive and changes nothing while it is off.</li>
 * </ul>
 *
 * <p>It also pre-groups the blocks by Y level for the original's slice ("片式") preview mode, so the renderer
 * can be handed one horizontal layer without filtering per frame.
 */
public final class StructurePreview {

    /** One block to draw, at a position relative to the controller. */
    public record Block(BlockPos pos, BlockState state, List<BlockState> alternatives) {

        public Block(BlockPos pos, BlockState state) {
            this(pos, state, List.of(state));
        }

        public Block {
            alternatives = List.copyOf(alternatives);
        }

        /** How many blocks this position accepts; always at least one. */
        public int variantCount() {
            return this.alternatives.size();
        }

        /**
         * The state to draw for a cycling index.
         *
         * <p>The index is taken modulo this position's own alternative count, so positions with different
         * numbers of alternatives all change at once but wrap independently — the same behaviour the original
         * got from indexing each element list with its shared tick counter.
         */
        public BlockState variantState(int variant) {
            if (this.alternatives.size() <= 1) {
                return this.state;
            }
            return this.alternatives.get(Math.floorMod(variant, this.alternatives.size()));
        }
    }

    private final List<Block> blocks;
    private final Map<Integer, List<Block>> layersByY;
    private final List<Integer> layers;
    private final BlockPos min;
    private final BlockPos max;
    private final BlockPos size;

    private StructurePreview(List<Block> blocks, BlockPos min, BlockPos max) {
        this.blocks = List.copyOf(blocks);
        this.min = min;
        this.max = max;
        this.size = new BlockPos(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1);

        Map<Integer, List<Block>> layers = new LinkedHashMap<>();
        for (Block block : this.blocks) {
            layers.computeIfAbsent(block.pos().getY(), ignored -> new ArrayList<>()).add(block);
        }
        Map<Integer, List<Block>> immutable = new LinkedHashMap<>();
        layers.forEach((y, layer) -> immutable.put(y, List.copyOf(layer)));
        this.layersByY = Map.copyOf(immutable);
        List<Integer> ordered = new ArrayList<>(layers.keySet());
        ordered.sort(Integer::compareTo);
        this.layers = List.copyOf(ordered);
    }

    /**
     * Builds the preview for a pattern.
     *
     * @param controllerState the controller block to draw at the origin, or {@code null} to omit it
     */
    public static StructurePreview of(MachinePattern pattern, @Nullable BlockState controllerState) {
        List<Block> blocks = new ArrayList<>();
        BlockPos min = null;
        BlockPos max = null;

        for (var entry : pattern.positions().entrySet()) {
            List<BlockState> alternatives = alternatives(entry.getValue());
            if (alternatives.isEmpty()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            blocks.add(new Block(pos, alternatives.get(0), alternatives));
            min = min == null ? pos : min(min, pos);
            max = max == null ? pos : max(max, pos);
        }

        if (controllerState != null && !controllerState.isAir()) {
            // The controller is a single block with nothing to cycle to.
            blocks.add(new Block(BlockPos.ZERO, controllerState));
            min = min == null ? BlockPos.ZERO : min(min, BlockPos.ZERO);
            max = max == null ? BlockPos.ZERO : max(max, BlockPos.ZERO);
        }

        if (min == null) {
            // An empty pattern still needs a box, or the renderer would divide by a zero extent. The original
            // loader rejects a definition with no parts, so this only guards hand-made patterns.
            min = BlockPos.ZERO;
            max = BlockPos.ZERO;
        }
        return new StructurePreview(blocks, min, max);
    }

    /**
     * Every state an element list accepts, in declaration order and without duplicates.
     *
     * <p>The first entry is the state the static preview draws, which is what the definition author listed
     * first and therefore the most typical choice for that position.
     */
    private static List<BlockState> alternatives(@Nullable List<BlockMatcher> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<BlockState> states = new LinkedHashSet<>();
        for (BlockMatcher matcher : accepted) {
            BlockState state = matcher.representativeState();
            if (state != null && !state.isAir()) {
                states.add(state);
            }
        }
        return List.copyOf(states);
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public List<Block> blocks() {
        return this.blocks;
    }

    /** The blocks of one horizontal level, empty when nothing sits at that Y. */
    public List<Block> blocksInLayer(int y) {
        return this.layersByY.getOrDefault(y, List.of());
    }

    /** The Y levels that hold at least one block, in ascending order. */
    public List<Integer> layers() {
        return this.layers;
    }

    public BlockPos min() {
        return this.min;
    }

    public BlockPos max() {
        return this.max;
    }

    /** Extent in blocks, always at least 1 on every axis. */
    public BlockPos size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.blocks.isEmpty();
    }

    /** The largest extent on any axis, for choosing a zoom that fits. */
    public int largestExtent() {
        return Math.max(1, Math.max(this.size.getX(), Math.max(this.size.getY(), this.size.getZ())));
    }
}
