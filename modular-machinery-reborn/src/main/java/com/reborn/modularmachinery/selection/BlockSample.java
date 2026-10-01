package com.reborn.modularmachinery.selection;

import javax.annotation.Nullable;

/**
 * One position's block, as the fragment needs it: the descriptor an {@code elements} array carries, plus the
 * tile entity's NBT as JSON when there is a tile entity.
 *
 * <p>A {@code null} entry in a sample list means "this position could not be read" (unloaded chunk, or a block no
 * registry knows about) — the original read the state anyway, which in 1.20.1 would chunk-load on the server
 * thread; see {@link SelectionExport#plan}.
 */
public record BlockSample(String descriptor, @Nullable String nbtJson) {
}
