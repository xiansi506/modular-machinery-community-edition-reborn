package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonParseException;

import java.util.Locale;

/** Direction of a recipe requirement, matching the original {@code io-type} field. */
public enum IOType {

    INPUT,
    OUTPUT;

    public static IOType byName(String name) {
        for (IOType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new JsonParseException("Unknown io-type '" + name
                + "'; expected one of " + INPUT.name().toLowerCase(Locale.ROOT)
                + ", " + OUTPUT.name().toLowerCase(Locale.ROOT));
    }
}
