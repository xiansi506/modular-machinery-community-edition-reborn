package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.selection.StructureSelection;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The client's copy of the construct tool's selection, mirroring the original's
 * {@code PlayerStructureSelectionHelper.clientSelection} ({@code :56}), which {@code PktSyncSelection} wrote and
 * cleared on disconnect ({@code SelectionBoxRenderHelper.java:75}).
 *
 * <p>Client-only: the server never reads it, and it is only ever written from
 * {@link com.reborn.modularmachinery.network.SelectionSyncPacket}'s handler.
 */
public final class ClientSelection {

    private static StructureSelection selection = new StructureSelection();

    private ClientSelection() {
    }

    public static void set(List<BlockPos> positions) {
        selection = StructureSelection.of(positions);
    }

    public static List<BlockPos> positions() {
        return selection.positions();
    }

    public static void clear() {
        selection = new StructureSelection();
    }
}
