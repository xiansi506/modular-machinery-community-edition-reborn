package com.reborn.modularmachinery.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-player selection the server owns, mirroring the original's
 * {@code PlayerStructureSelectionHelper.activeSelectionMap} ({@code :55}).
 *
 * <p>The original purged a player's selection when their connection dropped
 * ({@code :128-134}, {@code ServerDisconnectionFromClientEvent}) as well as after every finalize
 * ({@code ItemConstructTool.java:60}), so a reconnecting player starts empty in both. Kept identical: nothing
 * here survives a disconnect, and nothing is persisted to disk.
 */
public final class ServerSelections {

    private static final Map<UUID, StructureSelection> SELECTIONS = new HashMap<>();

    private ServerSelections() {
    }

    /**
     * The player's selection, created empty on first use — the original's
     * {@code computeIfAbsent(player.getUniqueID(), uuid -> new StructureSelection())}, which both the toggle and
     * the sync did ({@code :59}, {@code :72}).
     */
    public static StructureSelection get(Player player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), id -> new StructureSelection());
    }

    /** Toggles one position and answers the selection it now belongs to (or no longer belongs to). */
    public static StructureSelection toggle(Player player, BlockPos pos) {
        StructureSelection selection = get(player);
        selection.toggle(pos);
        return selection;
    }

    /** Forgets the player's selection entirely, as after a finalize or a disconnect. */
    public static void purge(Player player) {
        if (player != null) {
            SELECTIONS.remove(player.getUUID());
        }
    }

    /** The server-bus listener for the original's disconnect purge ({@code :128-134}). */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        purge(event.getEntity());
    }
}
