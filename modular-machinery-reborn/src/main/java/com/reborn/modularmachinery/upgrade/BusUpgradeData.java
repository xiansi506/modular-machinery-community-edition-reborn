package com.reborn.modularmachinery.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The bus-side custom NBT: one opaque tag per upgrade <b>declaration</b>, held by the bus and saved with it.
 *
 * <h2>What the original did</h2>
 *
 * <p>{@code TileUpgradeBus} carried a single {@code NBTTagCompound upgradeCustomData} keyed by upgrade name
 * ({@code TileUpgradeBus.java:50}), read and written one entry at a time —
 * {@code getUpgradeCustomData(upgrade)} returned {@code upgradeCustomData.getCompoundTag(name)} and
 * {@code setUpgradeCustomData} put it back ({@code :275-282}) — and the whole compound was serialised under the
 * key {@code "upgradeCustomData"} ({@code :193-194} read, {@code :217} write).
 *
 * <p>That is the key and the shape kept here, because a world carried over from the original holds exactly it.
 *
 * <h2>Why it is separate from the item's own tag</h2>
 *
 * <p>Two different stores, and the original had both: {@link UpgradeItemNbt} holds what the carrier <b>item</b>
 * carries, and this holds what the <b>bus</b> keeps for a declaration. A bus slot's item can be taken out, and
 * the declaration's data must not vanish with it; equally, an item moved to another bus brings its own tag and
 * finds the new bus's declaration data waiting. Collapsing the two would lose one of those behaviours.
 */
public final class BusUpgradeData {

    /**
     * The original's own NBT key for the whole store ({@code TileUpgradeBus.java:193/217}).
     *
     * <p>Spelled exactly as the original spelled it, because it is a <b>save-format</b> name: a world written by
     * the original must be readable here, and the only thing that makes that work is agreeing on this string.
     */
    public static final String TAG = "upgradeCustomData";

    private final Map<ResourceLocation, CompoundTag> byUpgrade = new LinkedHashMap<>();

    /** The declared upgrade names this bus holds data for, in insertion order. */
    public Set<ResourceLocation> names() {
        return Set.copyOf(this.byUpgrade.keySet());
    }

    /** Whether this store has anything at all — the check a sync decision wants. */
    public boolean isEmpty() {
        return this.byUpgrade.isEmpty();
    }

    /**
     * The tag stored for {@code upgrade}, or an empty tag when there is none.
     *
     * <p>Empty-never-null, matching the original's {@code getCompoundTag}: it answered an empty compound for a
     * name it did not hold, and a caller could hand that straight back to {@link #set} without a null check.
     */
    public CompoundTag get(ResourceLocation upgrade) {
        CompoundTag held = this.byUpgrade.get(upgrade);
        return held == null ? new CompoundTag() : held.copy();
    }

    /**
     * Stores {@code data} for {@code upgrade}, or <b>removes the entry</b> when there is nothing to store.
     *
     * <p>Same reasoning as {@link UpgradeItemNbt#write}: an empty compound and no compound have to mean the same
     * thing, or a save would accumulate empty sections that later read as "this declaration has data".
     *
     * @return whether this call changed anything, so the caller can decide about syncing
     */
    public boolean set(ResourceLocation upgrade, CompoundTag data) {
        if (upgrade == null) {
            return false;
        }
        if (data == null || data.isEmpty()) {
            return this.byUpgrade.remove(upgrade) != null;
        }
        CompoundTag previous = this.byUpgrade.put(upgrade, data.copy());
        return !data.equals(previous);
    }

    /** Writes this store into {@code tag} under {@link #TAG}, or removes the key when it is empty. */
    public void save(CompoundTag tag) {
        if (this.byUpgrade.isEmpty()) {
            tag.remove(TAG);
            return;
        }
        CompoundTag out = new CompoundTag();
        for (Map.Entry<ResourceLocation, CompoundTag> entry : this.byUpgrade.entrySet()) {
            out.put(entry.getKey().toString(), entry.getValue().copy());
        }
        tag.put(TAG, out);
    }

    /**
     * Reads {@code tag}'s store back, replacing whatever this instance held.
     *
     * <p>A name that is not a valid resource location is <b>skipped</b> rather than allowed to throw: this runs
     * while a chunk is loading, and a malformed entry in a save must not make the world unopenable.
     */
    public void load(CompoundTag tag) {
        this.byUpgrade.clear();
        if (tag == null || !tag.contains(TAG, CompoundTag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag stored = tag.getCompound(TAG);
        for (String name : stored.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id == null || !stored.contains(name, CompoundTag.TAG_COMPOUND)) {
                continue;
            }
            this.byUpgrade.put(id, stored.getCompound(name).copy());
        }
    }

    /** A stable, printable view for diagnostics and assertions: {@code {name={…}, …}} with sorted names. */
    public String describe() {
        return new TreeMap<>(this.byUpgrade).toString();
    }
}
