package com.reborn.modularmachinery.selection;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/**
 * One entry of the exported fragment: a position relative to the controller, the block that sits there, and the
 * tile entity's NBT when there is one.
 *
 * <p>{@code position} is already rotated (see {@link SelectionExport#offsets}), because the original rotated the
 * whole array after compressing it and the fragment is what the loader must read without rotating again.
 */
public record ExportPart(BlockPos position, String descriptor, @Nullable String nbtJson) {
}
