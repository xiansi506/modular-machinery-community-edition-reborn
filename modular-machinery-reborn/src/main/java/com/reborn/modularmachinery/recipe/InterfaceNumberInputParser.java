package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * The JSON reader for {@code interface_number_input}.
 *
 * <h2>Why this is not a private method of the serializer</h2>
 *
 * <p>It was, and the offline acceptance harness could not reach it: {@code ModRecipeSerializers} holds a
 * {@code DeferredRegister}, whose static initialiser touches Forge's {@code ObjectHolderRegistry} and therefore
 * {@code net.minecraftforge.fml.common.Mod} — a class that only exists inside FML's own class loader. Loading
 * the holder class to reach a private static parser is what {@code NoClassDefFoundError:
 * net/minecraftforge/fml/common/Mod} means when the harness calls it.
 *
 * <p>Nothing in this class touches a registry, a level or a client: it is a pure {@code JsonObject} →
 * requirement function, so the harness drives the <b>production</b> parser rather than a copy of it — the same
 * split (and the same reason) as the original's {@code RequirementTypeInterfaceNumInput} being a data class
 * while the registry entry lived elsewhere.
 */
public final class InterfaceNumberInputParser {

    private InterfaceNumberInputParser() {
    }

    /**
     * Reads one {@code interface_number_input} requirement.
     *
     * <pre>{@code
     * { "type": "modularmachinery:interface_number_input", "io-type": "input",
     *   "interface": "mode", "minValue": 10, "maxValue": 20 }
     * }</pre>
     *
     * <p><b>About the field name.</b> The original's own JSON schema called it {@code type} — the same key the
     * recipe format uses for the requirement's <i>kind</i>, so the two collided. That is not a guess: the
     * original's reader could never produce this requirement at all
     * ({@code RequirementTypeInterfaceNumInput#createRequirement} returned {@code null}, {@code :9-11}) and the
     * only constructors ever called came from the CraftTweaker bridge
     * ({@code RecipePrimer#addSmartInterfaceDataInput}). This projection reads the kind first and then the
     * interface type under its own key, {@code interface}, which is why the collision cannot happen here.
     *
     * <p><b>About {@code io-type}.</b> Required like every other requirement kind, but only {@code input} is
     * accepted: the original's constructor passed {@code IOType.INPUT} unconditionally, so an {@code output}
     * would be a field this requirement can never honour. Reported rather than quietly coerced.
     */
    public static InterfaceNumberInputRequirement parse(JsonObject json, IOType ioType, Object where) {
        if (ioType != IOType.INPUT) {
            throw new JsonParseException("An interface_number_input in " + where + " is written with "
                    + "\"io-type\": \"output\", but the original's requirement is an input only — it gates a "
                    + "craft on a value, it never produces one. Write \"io-type\": \"input\".");
        }
        String type = requireString(json, "interface", where);
        if (type.isBlank()) {
            throw new JsonParseException("'interface' in " + where + " is empty. Write the name of an interface "
                    + "type the machine declares, e.g. \"interface\": \"mode\".");
        }

        if (!json.has("minValue") && !json.has("maxValue")) {
            // The original's convenience overload (addSmartInterfaceDataInput(type, value)) in JSON form.
            float value = requireNumber(json, "value", where);
            return new InterfaceNumberInputRequirement(type, value, value, null);
        }

        float min = requireNumber(json, "minValue", where);
        float max = json.has("maxValue") ? requireNumber(json, "maxValue", where) : min;
        if (min > max) {
            throw new JsonParseException("'minValue' (" + min + ") is above 'maxValue' (" + max + ") in "
                    + where + ". The two ends are inclusive, so write the lower bound first — or write "
                    + "\"value\": " + min + " for an exact match.");
        }
        return new InterfaceNumberInputRequirement(type, min, max, null);
    }

    /** A required string field, with the message saying what to write instead. */
    private static String requireString(JsonObject json, String key, Object where) {
        JsonElement element = json.get(key);
        if (element == null) {
            throw new JsonParseException("Missing '" + key + "' in " + where + ". An interface_number_input "
                    + "needs \"interface\" (the interface type the machine declares) plus either \"value\" or "
                    + "\"minValue\"/\"maxValue\".");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'" + key + "' in " + where + " must be a string. Write the interface "
                    + "type's name, e.g. \"interface\": \"mode\".");
        }
        return element.getAsString();
    }

    /** A required number field, with the message saying which one and what to write instead. */
    private static float requireNumber(JsonObject json, String key, Object where) {
        JsonElement element = json.get(key);
        if (element == null) {
            throw new JsonParseException("Missing '" + key + "' in " + where + ". An interface_number_input "
                    + "needs \"interface\" (the interface type the machine declares) plus either \"value\" or "
                    + "\"minValue\"/\"maxValue\".");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' in " + where + " must be a number. Write the value the "
                    + "machine's smart data interface has to hold, e.g. \"" + key + "\": 4.");
        }
        return element.getAsFloat();
    }
}
