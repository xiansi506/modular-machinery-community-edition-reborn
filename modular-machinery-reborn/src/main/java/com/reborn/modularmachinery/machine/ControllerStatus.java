package com.reborn.modularmachinery.machine;

import java.util.Locale;

/**
 * What the controller is doing, shown on the controller screen's status line.
 *
 * <p>Mirrors the original's {@code TileMultiblockMachineController.Type}, which rendered
 * {@code "gui.controller.status." + name().toLowerCase()}. The names are kept identical so the two line up
 * one-to-one; only the language key prefix differs, because this mod namespaces its keys.
 *
 * <p>Two notes on fidelity:
 * <ul>
 *   <li>The original also carried a per-failure message ({@code craftcheck.failure.*}) that replaced the
 *       generic text, saying exactly which requirement was short. That is not implemented yet, so a machine
 *       waiting on a requirement shows {@link #IDLE} rather than a specific reason.</li>
 *   <li>{@code redstone_stopped} is not a value here: as in the original, the screen checks the redstone state
 *       first and prints that line instead of everything else.</li>
 * </ul>
 */
public enum ControllerStatus {

    MISSING_STRUCTURE,
    CHUNK_UNLOADED,
    NO_RECIPE,
    IDLE,
    CRAFTING;

    public String translationKey() {
        return "gui.modular_machinery_reborn.controller.status." + name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a synchronised ordinal back to a status, tolerating a stale or unknown value. */
    public static ControllerStatus byOrdinal(int ordinal) {
        ControllerStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MISSING_STRUCTURE;
    }
}
