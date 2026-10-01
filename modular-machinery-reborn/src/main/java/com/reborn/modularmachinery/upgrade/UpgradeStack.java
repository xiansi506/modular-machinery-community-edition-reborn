package com.reborn.modularmachinery.upgrade;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One upgrade standing in an upgrade bus, with how many copies the slot holds.
 *
 * <p>The original kept a {@code Map<UpgradeType, MachineUpgrade> foundUpgrades} on
 * {@code TileUpgradeBus}, where the {@code MachineUpgrade} instance carried the stack size and lived on the bus
 * so that custom NBT could be read and written around every handler call
 * ({@code UpgradeMachineEventHandler.java:38-50}). This project has no handler layer and no per-instance state:
 * what the recipe engine needs is the upgrade's declaration plus its count, which is exactly this pair.
 *
 * @param target the declaration the carrier item resolved to
 * @param count  how many carrier items share this upgrade; at least 1
 */
public record UpgradeStack(UpgradeTarget target, int count) {

    public UpgradeStack {
        count = Math.max(1, count);
    }

    public UpgradeType type() {
        return this.target.upgrade();
    }

    /** The stack as the original printed it in the bus GUI: {@code "3x <name>"}. */
    public String describe() {
        return this.count + "x " + this.type().displayName().getString();
    }

    /**
     * Whether this upgrade may take effect in a machine. The original's
     * {@code UpgradeBusProvider#getUpgrades(controller)} dropped everything
     * {@code type.isCompatible(foundMachine)} rejected ({@code :251-273}), which is precisely "an incompatible
     * upgrade is refused rather than misapplied".
     */
    public boolean appliesTo(ResourceLocation machineId) {
        return this.type().isCompatible(machineId);
    }

    /**
     * Everything one bus holds, bundled so compatibility and the total count can be answered — and driven by
     * the offline acceptance harness — without a {@code Level}.
     *
     * <p>Deliberately a {@code List}, not a {@code java.util.Set}: two different upgrade declarations may sit in
     * two slots at once, and the original's {@code foundUpgrades} was a {@code HashMap} keyed by type only
     * because it merged same-type slots as it walked them.
     */
    public record Bag(List<UpgradeStack> stacks) {

        public static final Bag EMPTY = new Bag(List.of());

        public Bag {
            stacks = List.copyOf(stacks);
        }

        public boolean isEmpty() {
            return this.stacks.isEmpty();
        }

        /** How many upgrades the bus holds, i.e. one entry per distinct declaration. */
        public int size() {
            return this.stacks.size();
        }

        /** How many carrier items in total carry an upgrade. */
        public int itemCount() {
            int total = 0;
            for (UpgradeStack stack : this.stacks) {
                total += stack.count();
            }
            return total;
        }

        /**
         * The upgrades a machine accepts, in load order. A {@code null} machine means "do not filter", which is
         * how the original's bus GUI listed everything it held ({@code component.getUpgrades(null)}).
         */
        public List<UpgradeStack> compatibleWith(ResourceLocation machineId) {
            if (machineId == null) {
                return this.stacks;
            }
            return this.stacks.stream().filter(stack -> stack.appliesTo(machineId)).toList();
        }

        /** The upgrades a machine rejects — the original's {@code gui.upgradebus.incompatible} warning rows. */
        public List<UpgradeStack> incompatibleWith(ResourceLocation machineId) {
            if (machineId == null) {
                return List.of();
            }
            return this.stacks.stream().filter(stack -> !stack.appliesTo(machineId)).toList();
        }
    }
}
