package com.reborn.modularmachinery.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The per-copy NBT a <b>dynamic</b> upgrade keeps on its carrier item.
 *
 * <h2>What the original did</h2>
 *
 * <p>Its {@code CapabilityUpgrade} was a Forge item capability holding one {@code MachineUpgrade} per declaration
 * ({@code CapabilityUpgrade.java:29-76}). Only the dynamic flavour had anything to say about the item: its
 * {@code readItemNBT} / {@code writeItemNBT} were
 * <b>a straight assignment and a straight return</b> ({@code SimpleDynamicMachineUpgrade.java:110-116}) — the tag
 * was opaque to the mod and belonged to whoever wrote it. The layer existed so that a CraftTweaker event handler
 * could read that data before it ran and write it back afterwards
 * ({@code UpgradeMachineEventHandler.java:38-50}), reading and writing it through the bus's own per-upgrade store
 * ({@code TileUpgradeBus#getUpgradeCustomData} / {@code setUpgradeCustomData}, {@code :275-282}).
 *
 * <h2>What this port does with it</h2>
 *
 * <p>The handler layer is deliberately not ported (the owner's decision: the declared modifiers <i>are</i> the
 * whole effect), so nothing in this build interprets the tag. It is <b>carried</b>, not read — and carrying it is
 * the part that would otherwise be lost data: a save from a pack that used dynamic upgrades holds this NBT, and a
 * port that dropped it would silently downgrade those items to their declaration.
 *
 * <p>The key is chosen to be unambiguous rather than to match the original byte for byte, because the original's
 * layout was a capability over a <b>list</b> of upgrades keyed by upgrade name, which has no single-tag
 * counterpart. {@link #readLegacy} understands that layout so a save written by the original still yields its
 * data.
 */
public final class UpgradeItemNbt {

    /**
     * Where this mod keeps a dynamic upgrade's opaque tag on the carrier item.
     *
     * <p>Namespaced under the mod's own id so it cannot collide with a pack author's own item NBT — the original
     * had the same property for free, because a capability is not a tag name.
     */
    public static final String CUSTOM_DATA = "modular_machinery_reborn:upgrade_custom_data";

    /**
     * The original capability's own registry name ({@code CapabilityUpgrade.CAPABILITY_NAME}), kept as a
     * <b>lookup</b> only.
     *
     * <p>The original never wrote this string into the item — a capability's name is not serialised onto the
     * stack — so this is not a key to read. The layout it <i>did</i> write is
     * {@code {<upgrade name>: <that upgrade's tag>}}, which {@link #readLegacy} understands; this constant is
     * recorded here so the next reader can find the original's shape without re-deriving it.
     */
    public static final String LEGACY_CAPABILITY_NAME = "modularmachinery:upgrade_cap";

    /** The tag on {@code stack}, or an empty tag when it carries none. Never {@code null}. */
    public static CompoundTag read(ItemStack stack) {
        CompoundTag all = stack.getTag();
        if (all == null || !all.contains(CUSTOM_DATA, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return all.getCompound(CUSTOM_DATA).copy();
    }

    /**
     * Stores {@code data} on {@code stack}, or <b>removes the key</b> when there is nothing to store.
     *
     * <p>Removing rather than writing an empty compound matters for stacking: two otherwise identical carriers
     * only merge when their tags match, and a leftover empty tag would keep a written-to copy apart from an
     * untouched one forever. An empty tag and no tag therefore have to mean the same thing, and this is what makes
     * them.
     */
    public static void write(ItemStack stack, CompoundTag data) {
        if (data == null || data.isEmpty()) {
            CompoundTag all = stack.getTag();
            if (all != null) {
                all.remove(CUSTOM_DATA);
                if (all.isEmpty()) {
                    // A tag holding nothing but our key is itself worth removing: an item with an empty compound
                    // tag does not stack with a pristine one either.
                    stack.setTag(null);
                }
            }
            return;
        }
        stack.getOrCreateTag().put(CUSTOM_DATA, data.copy());
    }

    /** Whether this stack carries any per-copy upgrade NBT. */
    public static boolean has(ItemStack stack) {
        return !read(stack).isEmpty();
    }

    /**
     * The original's layout for one upgrade: {@code {<upgrade name>: {…}}}, read back as that upgrade's tag.
     *
     * <p>Offered because the original stored dynamic data this way and a world carried over from it holds exactly
     * this shape. Returns an empty tag when the name is absent, so a caller can pass the result straight to
     * {@link #write} and end up with an item that carries nothing — the same state as never having had data.
     *
     * @param tag         the original's per-upgrade compound
     * @param upgradeName the upgrade declaration's name, as the original keyed it
     */
    public static CompoundTag readLegacy(CompoundTag tag, String upgradeName) {
        if (tag == null || upgradeName == null || !tag.contains(upgradeName, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return tag.getCompound(upgradeName).copy();
    }

    /** {@link #readLegacy}'s counterpart: the original's one-entry layout for this upgrade. */
    public static CompoundTag writeLegacy(String upgradeName, CompoundTag data) {
        CompoundTag out = new CompoundTag();
        if (upgradeName != null && data != null && !data.isEmpty()) {
            out.put(upgradeName, data.copy());
        }
        return out;
    }

    private UpgradeItemNbt() {
    }
}
