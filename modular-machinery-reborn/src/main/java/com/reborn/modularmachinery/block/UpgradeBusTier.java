package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.util.StringRepresentable;

/**
 * The five upgrade-bus tiers, with the original's numbers and names.
 *
 * <p>Straight out of {@code UpgradeBusData.java:8-14} of the 1.12.2 source:
 * <b>NORMAL 3, REINFORCED 6, ELITE 9, SUPER 12, ULTIMATE 18</b> slots. The original could also override each
 * number from its config file ({@code loadFromConfig}, {@code :23-29}), here that is
 * {@link ModConfig}, and {@link #maxUpgradeSlots()} is the resolved count.
 *
 * <p>The enum itself stays in code for the reason the scoping document gives in §3.4 and M6d already applied to
 * the parallel controller: a block's variants have to exist when blocks are registered, and 1.20.1 forbids
 * registering them later (D8). Only the numbers move to the config — exactly the split the original had.
 */
public enum UpgradeBusTier implements StringRepresentable {

    NORMAL("normal", 3),
    REINFORCED("reinforced", 6),
    ELITE("elite", 9),
    SUPER("super", 12),
    ULTIMATE("ultimate", 18);

    private final String name;
    private final int defaultMaxUpgradeSlots;

    UpgradeBusTier(String name, int defaultMaxUpgradeSlots) {
        this.name = name;
        this.defaultMaxUpgradeSlots = defaultMaxUpgradeSlots;
    }

    /** {@code IStringSerializable#getName} of the original, i.e. the {@code type} blockstate value. */
    @Override
    public String getSerializedName() {
        return this.name;
    }

    public String tierName() {
        return this.name;
    }

    /** The original's hardcoded value, before any config override. */
    public int defaultMaxUpgradeSlots() {
        return this.defaultMaxUpgradeSlots;
    }

    /**
     * The slot count this tier provides: the config value when the config is loaded, else the default.
     *
     * <p>The original clamped the config entry to {@code 1 .. 18} ({@code UpgradeBusData.java:25-27}), and
     * {@link ModConfig} declares that same range, so the ceiling of 18 stays the original's.
     */
    public int maxUpgradeSlots() {
        return ModConfig.maxUpgradeSlots(this);
    }

    /**
     * Item registry name of this tier, following the project's hatch convention
     * ({@code item_input_hatch_tiny} → {@code upgrade_bus_normal}). The scoping document's language-key table
     * spells the family {@code upgrade_bus}, which is what the original's own item path was too.
     */
    public String itemId() {
        return "upgrade_bus_" + this.name;
    }

    /** Per-variant item translation key, the counterpart of {@code tile.modularmachinery.blockupgradebus.<tier>.name}. */
    public String translationKey() {
        return "block.modular_machinery_reborn." + itemId();
    }
}
