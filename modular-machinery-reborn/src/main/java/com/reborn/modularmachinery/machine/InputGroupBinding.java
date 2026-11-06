package com.reborn.modularmachinery.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** A paid input source is not a collector-local number. No optional-mod types belong in this contract. */
public record InputGroupBinding(@Nullable Key isolated, boolean resolved) {
    private static final String TAG = "inputGroupBinding";

    public record Key(UUID incarnation, int slot, ResourceLocation dimension, long position) {
        public Key {
            Objects.requireNonNull(incarnation);
            Objects.requireNonNull(dimension);
            if (slot < 0) throw new IllegalArgumentException("Negative isolated input slot");
        }
    }

    public static InputGroupBinding manual() { return new InputGroupBinding(null, true); }
    public static InputGroupBinding capture(HatchCollection ports, int group) {
        return new InputGroupBinding(ports.isolatedGroupKey(group), true);
    }

    public boolean available(HatchCollection ports) {
        return resolved && (isolated == null || ports.isolatedGroupNumber(isolated) != null);
    }

    /** Returns null when this exact source is unavailable; never falls back to a reused numeric address. */
    @Nullable public Integer group(HatchCollection ports, int manualGroup) {
        if (!resolved) return null;
        if (isolated == null) return ports.isolatedGroupKey(manualGroup) == null ? manualGroup : null;
        return ports.isolatedGroupNumber(isolated);
    }

    public HatchCollection scope(HatchCollection ports, int manualGroup) {
        Integer number = group(ports, manualGroup);
        return number == null ? HatchCollection.EMPTY : ports.withGroup(number);
    }

    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", !resolved ? "unresolved" : isolated == null ? "manual" : "isolated");
        if (resolved && isolated != null) {
            tag.putUUID("source", isolated.incarnation());
            tag.putInt("slot", isolated.slot());
            tag.putString("dimension", isolated.dimension().toString());
            tag.putLong("position", isolated.position());
        }
        root.put(TAG, tag);
    }

    public static InputGroupBinding load(CompoundTag root, int oldGroup) {
        if (!root.contains(TAG)) {
            // Old isolated allocations occupied this range, but legal ordinary groups did too. The old
            // number alone cannot identify which resource a paid craft used. Preserve it, do not guess.
            return new InputGroupBinding(null, oldGroup < 65536);
        }
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) return new InputGroupBinding(null, false);
        CompoundTag tag = root.getCompound(TAG);
        if (tag.getString("kind").equals("manual")) return manual();
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (!tag.getString("kind").equals("isolated") || !tag.hasUUID("source") || dimension == null
                || !tag.contains("dimension", Tag.TAG_STRING) || tag.getString("dimension").isBlank()
                || !tag.contains("slot", Tag.TAG_INT) || tag.getInt("slot") < 0
                || !tag.contains("position", Tag.TAG_LONG)) return new InputGroupBinding(null, false);
        return new InputGroupBinding(new Key(tag.getUUID("source"), tag.getInt("slot"), dimension,
                tag.getLong("position")), true);
    }
}
