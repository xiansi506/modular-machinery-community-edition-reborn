package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.util.StringRepresentable;

/**
 * The five parallel-controller tiers, with the original's numbers and names.
 *
 * <p>Straight out of {@code ParallelControllerData.java:8-14} of the 1.12.2 source:
 * <b>NORMAL 4, REINFORCED 16, ELITE 64, SUPER 256, ULTIMATE 512</b>. The original could also override each
 * number from its config file ({@code loadFromConfig}, {@code :23-29}); here that is
 * {@link ModConfig}, and {@link #maxParallelism()} is the resolved ceiling.
 *
 * <p>The enum itself stays in code for the reason the scoping document gives in §3.4: a block's variants have to
 * exist when blocks are registered, and 1.20.1 forbids registering them later (D8).
 */
public enum ParallelControllerTier implements StringRepresentable {

    NORMAL("normal", 4),
    REINFORCED("reinforced", 16),
    ELITE("elite", 64),
    SUPER("super", 256),
    ULTIMATE("ultimate", 512);

    private final String name;
    private final int defaultMaxParallelism;

    ParallelControllerTier(String name, int defaultMaxParallelism) {
        this.name = name;
        this.defaultMaxParallelism = defaultMaxParallelism;
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
    public int defaultMaxParallelism() {
        return this.defaultMaxParallelism;
    }

    /** The ceiling this tier contributes: the config value when the config is loaded, else the default. */
    public int maxParallelism() {
        return ModConfig.maxParallelism(this);
    }

    /**
     * Item registry name of this tier, following the project's hatch convention
     * ({@code item_input_hatch_tiny} → {@code parallel_controller_normal}).
     */
    public String itemId() {
        return "parallel_controller_" + this.name;
    }

    /** Per-variant item translation key, the counterpart of {@code tile.modularmachinery.blockparallelcontroller.<tier>.name}. */
    public String translationKey() {
        return "block.modular_machinery_reborn." + itemId();
    }
}
