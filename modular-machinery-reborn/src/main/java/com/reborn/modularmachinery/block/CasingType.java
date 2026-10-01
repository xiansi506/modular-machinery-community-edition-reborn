package com.reborn.modularmachinery.block;

import net.minecraft.util.StringRepresentable;

/**
 * The original {@code BlockCasing.CasingType}: six decorative machine casings.
 *
 * <p>The original stored this in a {@code casing} blockstate property whose metadata was the enum ordinal
 * ({@code BlockCasing.getMetaFromState} returns {@code state.getValue(CASING).ordinal()}), which is why machine
 * definitions such as {@code alloy_furnace} reference {@code blockcasing@4} — ordinal 4 is {@code REINFORCED},
 * not {@code CIRCUITRY} as the name might suggest. In 1.20.1 there is no metadata, so those references become
 * {@code blockcasing[casing=reinforced]}.
 */
public enum CasingType implements StringRepresentable {

    PLAIN("plain"),
    VENT("vent"),
    FIREBOX("firebox"),
    GEARBOX("gearbox"),
    REINFORCED("reinforced"),
    CIRCUITRY("circuitry");

    private final String name;

    CasingType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Registry path and language key suffix of this variant's block item. */
    public String itemId() {
        return "blockcasing_" + this.name;
    }
}
