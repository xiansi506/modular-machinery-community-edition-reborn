package com.reborn.modularmachinery.block;

import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * Tier ladder of the original Modular Machinery: Community Edition hatches.
 *
 * <p>The original mod expressed hatch tiers with three separate enums — {@code ItemBusSize},
 * {@code FluidHatchSize} and {@code EnergyHatchData} — each attached to a single block per hatch
 * family through a {@code size} blockstate property. This port keeps that model: one block per
 * family plus a {@code size} property, and one block item per tier.
 *
 * <p>The three ladders are deliberately <b>not</b> identical, exactly as in the original:
 * <ul>
 *   <li>item buses stop at {@code ludicrous} — seven tiers, with no ultimate art in the source;</li>
 *   <li>fluid hatches add {@code vacuum};</li>
 *   <li>energy hatches add {@code ultimate}.</li>
 * </ul>
 * A value of {@code 0} means "this tier does not exist for that family".
 */
public enum HatchTier implements StringRepresentable {

    TINY("tiny", 1, 100, 2_048L, 128L),
    SMALL("small", 4, 400, 4_096L, 512L),
    NORMAL("normal", 6, 1_000, 8_192L, 512L),
    REINFORCED("reinforced", 9, 2_000, 16_384L, 2_048L),
    BIG("big", 12, 4_500, 32_768L, 8_192L),
    HUGE("huge", 16, 8_000, 131_072L, 32_768L),
    LUDICROUS("ludicrous", 32, 16_000, 524_288L, 131_072L),
    ULTIMATE("ultimate", 0, 0, 2_097_152L, 131_072L),
    VACUUM("vacuum", 0, 32_000, 0L, 0L);

    /** {@code ItemBusSize}: seven tiers, matching the original exactly. */
    public static final List<HatchTier> ITEM_LADDER =
            List.of(TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS);

    /** {@code FluidHatchSize}: the item ladder plus {@code vacuum}. */
    public static final List<HatchTier> FLUID_LADDER =
            List.of(TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS, VACUUM);

    /** {@code EnergyHatchData}: the item ladder plus {@code ultimate}. */
    public static final List<HatchTier> ENERGY_LADDER =
            List.of(TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS, ULTIMATE);

    private final String name;
    private final int itemSlots;
    private final int fluidCapacity;
    private final long energyCapacity;
    private final long energyTransfer;

    HatchTier(String name, int itemSlots, int fluidCapacity, long energyCapacity, long energyTransfer) {
        this.name = name;
        this.itemSlots = itemSlots;
        this.fluidCapacity = fluidCapacity;
        this.energyCapacity = energyCapacity;
        this.energyTransfer = energyTransfer;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Inventory slots for an item bus of this tier; {@code 0} when the tier is not an item tier. */
    public int itemSlots() {
        return this.itemSlots;
    }

    /** Tank size in mB for a fluid hatch of this tier; {@code 0} when the tier is not a fluid tier. */
    public int fluidCapacity() {
        return this.fluidCapacity;
    }

    /** Stored FE for an energy hatch of this tier; {@code 0} when the tier is not an energy tier. */
    public long energyCapacity() {
        return this.energyCapacity;
    }

    /** FE per tick accepted or emitted by an energy hatch of this tier; {@code 0} when not an energy tier. */
    public long energyTransfer() {
        return this.energyTransfer;
    }
}
