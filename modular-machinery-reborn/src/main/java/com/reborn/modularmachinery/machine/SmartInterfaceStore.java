package com.reborn.modularmachinery.machine;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The smart-interface values one machine controller holds — the whole of M6d-b's state, deliberately kept out
 * of the block entity so it can be driven without a world.
 *
 * <h2>Why this is its own class</h2>
 *
 * <p>The same reason {@code ParallelismLimit} and {@code UpgradeEffects} are: the offline acceptance harness has
 * to be able to assert <b>the real arithmetic</b> rather than a copy of it, and a block entity cannot be
 * constructed outside a running game ({@code BlockEntity}'s constructor reads its {@code Level}). Everything
 * here is a pure function of a definition and a map, so the harness drives the production object directly — and
 * the block entity's own methods are one-line delegations to it.
 *
 * <h2>What it replaces</h2>
 *
 * <p>The original kept this list on the <b>separate interface block</b>
 * ({@code TileSmartInterface#boundData}) and reconciled it every 20 ticks against the controllers it was bound
 * to. With the block gone there is exactly one controller and exactly one machine, so the reconcile collapses to
 * {@link #bind}: fill in a declared type's default, drop a value whose type is no longer declared. That is the
 * original's {@code checkAndAddSmartInterface} ({@code TileMultiblockMachineController:909-938}) with the
 * "which of my several bindings is this" half removed — which is the merge's simplification and not a loss.
 */
public final class SmartInterfaceStore implements SmartInterfaceValueSource {

    /** The original's NBT key ({@code TileSmartInterface#writeCustomNBT}). */
    public static final String NBT_KEY = "boundData";

    /** LinkedHashMap so the values keep the definition's declaration order for the screen. */
    private final Map<String, Float> values = new LinkedHashMap<>();

    /** The values, as the read-only view a requirement or a screen sees. */
    public SmartInterfaceValues values() {
        return SmartInterfaceValues.of(this.values);
    }

    @Override
    @Nullable
    public Float valueOf(String type) {
        return this.values.get(type);
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public int size() {
        return this.values.size();
    }

    /** The declared type names that currently have a value, in declaration order. */
    public List<String> typeNames() {
        return List.copyOf(this.values.keySet());
    }

    /**
     * The projection of the original's {@code checkAndAddSmartInterface}.
     *
     * <ol>
     *   <li>Every declared type with no value yet gets its {@code default}. The original also decided <b>which</b>
     *       type a freshly placed interface binds to — the highest-priority one not already taken
     *       ({@code getFilteredType} then {@code sorted().findFirst()}) — and wrote that default in. Since the
     *       interface block is gone, every declared type is already a slot, so the "which slot" question no
     *       longer exists and a player can only ever <b>edit</b> a declared value.</li>
     *   <li>A value whose type the current machine does not declare is dropped, which is the original's
     *       {@code smartInterface.removeMachineData(realPos)} branch. It is what stops a torn-down machine from
     *       leaving a value behind for a requirement to read.</li>
     * </ol>
     *
     * <p>Idempotent, so the caller may run it on every successful structure check.
     *
     * @return whether anything changed, so the caller knows whether to mark itself dirty
     */
    public boolean bind(MachineDefinition definition) {
        boolean changed = false;
        List<String> declared = definition.smartInterfaceNames();
        java.util.Iterator<String> stale = this.values.keySet().iterator();
        while (stale.hasNext()) {
            if (!declared.contains(stale.next())) {
                stale.remove();
                changed = true;
            }
        }
        for (SmartInterfaceType type : definition.smartInterfaces()) {
            if (!this.values.containsKey(type.type())) {
                this.values.put(type.type(), type.defaultValue());
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Sets one declared type's value — what the controller's screen submits.
     *
     * <p>Returns the value actually stored, or {@code null} when the machine does not declare that type or the
     * value is not finite. The original's write path ({@code PktSmartInterfaceUpdate} →
     * {@code SmartInterfaceProvider#addMachineData}) checked neither: the screen's field was the only guard, and
     * the message was applied to whatever interface the sender had open. Re-checking here is the same
     * "the server re-validates" rule {@code ParallelControllerUpdatePacket} follows, and it also gives the
     * offline harness a single call that really moves the number a requirement will read.
     *
     * <p>An undeclared type is refused rather than remembered: the original could create a binding for any type
     * the machine declared and dropped every other one on the next controller check.
     */
    @Nullable
    public Float set(MachineDefinition definition, String type, float value) {
        if (definition == null || definition.smartInterface(type) == null) {
            return null;
        }
        if (!Float.isFinite(value)) {
            return null;
        }
        this.values.put(type, value);
        return value;
    }

    /** Forgets everything — used when the state is being restored from NBT. */
    public void clear() {
        this.values.clear();
    }

    /** Writes the values under the original's own key, or writes nothing when there are none. */
    public void save(CompoundTag tag) {
        if (this.values.isEmpty()) {
            return;
        }
        CompoundTag smart = new CompoundTag();
        for (Map.Entry<String, Float> entry : this.values.entrySet()) {
            smart.putFloat(entry.getKey(), entry.getValue());
        }
        tag.put(NBT_KEY, smart);
    }

    /**
     * Restores what {@link #save} wrote. The values are the player's own input, so they are restored verbatim
     * and <b>not</b> reconciled against the definition here: the definition may not be available during
     * {@code load}, and the first structure check calls {@link #bind} anyway.
     */
    public void load(CompoundTag tag) {
        this.values.clear();
        if (!tag.contains(NBT_KEY)) {
            return;
        }
        CompoundTag smart = tag.getCompound(NBT_KEY);
        for (String type : smart.getAllKeys()) {
            this.values.put(type, smart.getFloat(type));
        }
    }

    /**
     * The value a requirement will read, or {@code null} — the same question a {@code SmartInterfaceData} lookup
     * answered in the original ({@code RequirementInterfaceNumInput:77-80}).
     */
    @Nullable
    public Float valueFor(String type) {
        return valueOf(type);
    }
}
