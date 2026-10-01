package com.reborn.modularmachinery.selection;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The set of positions a player has selected with the construct tool.
 *
 * <p>This is the original's {@code PlayerStructureSelectionHelper.StructureSelection}
 * ({@code _mmce-src/.../common/selection/PlayerStructureSelectionHelper.java:136-181}) without the world: a
 * <b>toggled set</b>, not a cuboid. The original used a {@code LinkedList} plus {@code contains}; the observable
 * behaviour is the same with a {@link LinkedHashSet} (insertion order, no duplicates, toggling removes), and the
 * lookup is O(1) instead of O(n) — which matters because there is no size cap and a player may click hundreds of
 * positions.
 *
 * <p>There is deliberately <b>no maximum size</b> here: the original has none
 * ({@code togglePosition} adds unconditionally), so inventing a cap would be a divergence. What the tool does
 * about a very large selection is warn, not refuse — see {@code ConstructToolItem}.
 *
 * <p>Contains no {@code Level}, so the whole toggle arithmetic is drivable offline (harness section AA).
 */
public final class StructureSelection {

    private final LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();

    public StructureSelection() {
    }

    /**
     * The selection a client is told about. A copy, deduplicated, keeping the first occurrence's order — a
     * hand-written packet cannot make one position render twice.
     */
    public static StructureSelection of(Collection<BlockPos> positions) {
        StructureSelection selection = new StructureSelection();
        selection.positions.addAll(positions);
        return selection;
    }

    /**
     * The original's {@code togglePosition}: in when it was out, out when it was in.
     *
     * @return {@code true} when the position is now <b>in</b> the selection
     */
    public boolean toggle(BlockPos pos) {
        if (this.positions.remove(pos)) {
            return false;
        }
        this.positions.add(pos);
        return true;
    }

    public boolean contains(BlockPos pos) {
        return this.positions.contains(pos);
    }

    public int size() {
        return this.positions.size();
    }

    public boolean isEmpty() {
        return this.positions.isEmpty();
    }

    /** The selected positions in the order they were clicked, as a copy. */
    public List<BlockPos> positions() {
        return new ArrayList<>(this.positions);
    }

    public void clear() {
        this.positions.clear();
    }

    @Override
    public String toString() {
        return this.positions.toString();
    }
}
