package com.reborn.modularmachinery.machine;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The values one controller currently holds for its machine's declared interface types — the projection of the
 * original's {@code SmartInterfaceData} (99 lines) onto a controller that <b>is</b> the machine.
 *
 * <h2>What was dropped, and why nothing was lost</h2>
 *
 * <p>The original's {@code SmartInterfaceData} carried four things: {@code pos}, {@code parent}, {@code type}
 * and {@code value}. Two of them existed only because the interface was a <b>separate block</b> standing in the
 * structure:
 *
 * <ul>
 *   <li>{@code pos} — the position of the controller the value was bound to. The original's own
 *       {@code SmartInterfaceProvider#getMachineData(BlockPos)} used it to pick one bound machine out of several,
 *       and {@code TileSmartInterface#doRestrictedTick} used it every 20 ticks to drop a binding whose
 *       controller was gone. A controller has exactly one machine — itself — so the position is the controller's
 *       own and the reconcile has nothing to do.</li>
 *   <li>{@code parent} — the registry name of the bound machine, needed because a separate interface could be
 *       bound to a machine that was not the one whose screen was open, and because the interface's own screen
 *       printed it ({@code GuiContainerSmartInterface:79}). The value is stored on the controller, so the
 *       machine is the controller's formed machine by construction.</li>
 * </ul>
 *
 * <p>That leaves a type name and a number, which is exactly what this record holds. The value stays a
 * {@code float} — the original's own type — even though the screen prints it with {@code %.0f}, because a
 * requirement's range is compared as a float and a player may legitimately type {@code 3.5}.
 *
 * <h2>Why a map and not a list</h2>
 *
 * <p>{@code SmartInterfaceProvider#getMachineData(String)} was a linear scan of a list because the list could
 * hold several bindings for the <b>same</b> type on different controllers. One controller cannot hold two values
 * for one type — {@code checkAndAddSmartInterface} only ever bound a type once, and
 * {@code DynamicMachine#smartInterfaces} was itself a {@code Map<String, SmartInterfaceType>} keyed by type —
 * so a keyed map is the honest shape. The declaration order survives because the map is a {@link LinkedHashMap}.
 */
public record SmartInterfaceValues(Map<String, Float> values) {

    /** No source of values at all: every lookup misses, which is the "no interface" case. */
    public static final SmartInterfaceValues EMPTY = new SmartInterfaceValues(Map.of());

    public SmartInterfaceValues(Map<String, Float> values) {
        this.values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static SmartInterfaceValues of(Map<String, Float> values) {
        return values.isEmpty() ? EMPTY : new SmartInterfaceValues(values);
    }

    /** The stored value for one type, or {@code null} when this controller holds none. */
    @Nullable
    public Float get(String type) {
        return this.values.get(type);
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public int size() {
        return this.values.size();
    }
}
