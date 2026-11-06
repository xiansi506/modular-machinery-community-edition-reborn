package com.reborn.modularmachinery.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.math.BigDecimal;

/** Author JSON numbers are checked in their original decimal representation, before any narrowing. */
public final class JsonNumbers {
    private JsonNumbers() {}

    public static int integer(JsonElement element, int min, int max, Object where) {
        return (int) integral(element, min, max, where);
    }

    public static long integral(JsonElement element, long min, long max, Object where) {
        try {
            BigDecimal decimal = decimal(element, false, where);
            if (decimal.compareTo(BigDecimal.valueOf(min)) < 0 || decimal.compareTo(BigDecimal.valueOf(max)) > 0)
                throw new ArithmeticException("out of range");
            return decimal.longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw new JsonParseException(where + " must be an exact integer in " + min + ".." + max, error);
        }
    }

    private static BigDecimal decimal(JsonElement element, boolean numericString, Object where) {
        if (element == null || !element.isJsonPrimitive()
                || !(element.getAsJsonPrimitive().isNumber()
                || numericString && element.getAsJsonPrimitive().isString()))
            throw new JsonParseException(where + " must be a JSON number");
        return new BigDecimal(element.getAsString());
    }

    public static double finite(JsonElement element, Object where) { return finite(element, false, where); }

    /** Only explicitly documented legacy fields opt in to numeric strings. */
    public static double finite(JsonElement element, boolean numericString, Object where) {
        try {
            double value = decimal(element, numericString, where).doubleValue();
            if (!Double.isFinite(value)) throw new ArithmeticException("non-finite");
            return value;
        } catch (ArithmeticException | NumberFormatException error) {
            throw new JsonParseException(where + " must be a finite number", error);
        }
    }

    public static float finiteFloat(JsonElement element, Object where) {
        float value = (float) finite(element, where);
        if (!Float.isFinite(value)) throw new JsonParseException(where + " must fit a finite float");
        return value;
    }

    public static boolean bool(JsonElement element, Object where) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
            throw new JsonParseException(where + " must be true or false");
        return element.getAsBoolean();
    }
}
