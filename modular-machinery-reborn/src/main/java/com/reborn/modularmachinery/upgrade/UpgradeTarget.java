package com.reborn.modularmachinery.upgrade;

import net.minecraft.world.item.Item;

import java.util.List;

/**
 * What one item carries into an upgrade bus, as declared in a {@code <name>.item.json} file.
 *
 * <p>This is the data-driven replacement for the original's {@code RegistryUpgrade.ITEM_UPGRADES} map, which
 * CraftTweaker filled through {@code MachineUpgradeHelper.registerSupportedItem} and
 * {@code addFixedUpgrade}. A data pack is the only route this project has, since it has no scripting bridge
 * for that — the same reasoning that moved machine definitions out of the jar (decision D10).
 *
 * <p>It is <b>not</b> the upgrade instance layer. The original also had {@code CapabilityUpgrade}, running
 * {@code MachineUpgrade} objects attached to an {@code ItemStack} through a Forge capability, and that layer is
 * deliberately absent here: it has no consumer until the upgrade bus exists, so it belongs to the next slice.
 * What this layer answers is the only question the bus needs in order to filter its slots — "is this item an
 * upgrade carrier, and which upgrade does it carry".
 */
public sealed interface UpgradeTarget {

    /** The carrier item, already resolved — an id that names no registered item is a load error. */
    Item item();

    /** The upgrade this item carries. */
    UpgradeType upgrade();

    /** A fixed target: every copy of the item behaves identically, with all state kept on the bus. */
    record Fixed(Item item, UpgradeType upgrade) implements UpgradeTarget {
    }

    /**
     * A dynamic target: each copy of the item keeps its own NBT, the original's {@code DynamicMachineUpgrade}.
     * No prototype {@code ItemStack} is stored here — the instance layer adds that when it needs it, and storing
     * one now would invite a caller to mutate a shared declaration.
     */
    record Dynamic(Item item, UpgradeType upgrade) implements UpgradeTarget {
    }

    /** Every declaration for one item, in load order. */
    record Targets(List<UpgradeTarget> targets) {

        public Targets {
            targets = List.copyOf(targets);
        }

        public boolean isEmpty() {
            return this.targets.isEmpty();
        }

        /** Every upgrade this item carries. */
        public List<UpgradeType> upgrades() {
            return this.targets.stream().map(UpgradeTarget::upgrade).toList();
        }

        /** True when at least one declaration is dynamic, i.e. state lives on the item. */
        public boolean hasDynamic() {
            return this.targets.stream().anyMatch(Dynamic.class::isInstance);
        }
    }

    /**
     * The declarations attached to one {@code ItemStack}, resolved through the live registry.
     *
     * <p>This is the M6a {@link UpgradeTarget.Targets} plus the stack the bus actually holds. It exists so the
     * bus never has to touch {@code UpgradeRegistry} directly: everything that reads a slot goes through here,
     * which is what makes {@link UpgradeEffects} driveable from the offline harness.
     */
    record Bag(List<UpgradeTarget> targets) {

        public static final Bag EMPTY = new Bag(List.of());

        public Bag {
            targets = List.copyOf(targets);
        }

        /** The declarations one stack carries, or {@link #EMPTY} when it is not a carrier. */
        public static Bag of(net.minecraft.world.item.ItemStack stack) {
            Targets targets = UpgradeRegistry.targetsFor(stack);
            return targets == null ? EMPTY : new Bag(targets.targets());
        }

        public boolean isEmpty() {
            return this.targets.isEmpty();
        }
    }
}
