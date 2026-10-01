package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The original's {@code hellfirepvp.modularmachinery.common.modifier.RecipeModifier}.
 *
 * <p>One modifier says: <i>for requirements of this target, on this side, and (optionally) for this kind of
 * value, add this much and multiply by this much.</i> The original's fields are kept one for one —
 * {@code target} / {@code io} / {@code operation} / {@code multiplier} / {@code affectChance} — including the
 * two operations' numeric ids, because the JSON in the wild spells them as numbers ({@code "operation": 1}).
 *
 * <p><b>What was simplified.</b> The original looked its {@code target} up in a registry of requirement types
 * and fell back to a table of deprecated aliases before failing. This mod has no such registry — requirement
 * kinds are three string constants in {@link ModRecipeSerializers} — so the target is parsed as a
 * {@link ResourceLocation} and its namespace is ignored. Every spelling the original accepted therefore still
 * parses here:
 *
 * <ul>
 *   <li>{@code modular_machinery_reborn:item}, {@code modularmachinery:item} and a bare {@code item} are all
 *       the same target; the same holds for {@code fluid} and {@code energy}.</li>
 *   <li>{@code duration} is the original's {@code REQUIREMENT_DURATION} pseudo-target. It is not a requirement
 *       kind — no requirement can be declared with it, exactly as in the original, where
 *       {@code RequirementDuration#createRequirement} threw — but a modifier may target it to change how long
 *       the craft takes.</li>
 *   <li>Anything else is rejected by {@link #parse} with the list of what may be written instead, rather than
 *       being silently kept as a modifier that can never match.</li>
 * </ul>
 */
public record RecipeModifier(@Nullable Target target, @Nullable IOType ioTarget, float value, Operation operation,
                             boolean affectsChance) {

    /** The two operations, with the original's numeric ids ({@code RecipeModifier.OPERATION_ADD/MULTIPLY}). */
    public enum Operation {
        ADD(0),
        MULTIPLY(1);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public static Operation byId(int id, Object where) {
            for (Operation operation : values()) {
                if (operation.id == id) {
                    return operation;
                }
            }
            throw new JsonParseException("'operation' in " + where + " is " + id
                    + "; the original only defines 0 (add) and 1 (multiply). Write \"operation\": 0 or "
                    + "\"operation\": 1.");
        }
    }

    /**
     * What a modifier acts on.
     *
     * <p>{@code target} may also be {@code null}, which in the original meant "the duration pseudo-type" — see
     * {@link RecipeModifiers#applyDuration}. A machine-structure modifier that is meant to hit every kind of
     * requirement instead writes {@code "*"} (see {@link RecipeModifiers#applyToAllKinds}); the original had no
     * way to express that, because its {@code target} was a registry entry and had to be concrete.
     */
    public enum Target {
        ITEM("item"),
        FLUID("fluid"),
        ENERGY("energy"),
        DURATION("duration"),
        /** Only reachable from the JSON wildcard {@code "*"}; matches every requirement kind. */
        ALL("*");

        private final String name;

        Target(String name) {
            this.name = name;
        }

        public String targetName() {
            return this.name;
        }

        /** Every {@code target} value a definition may write, for error messages. */
        public static String allowed() {
            return "\"item\", \"fluid\", \"energy\", \"duration\" or \"*\" (optionally namespaced, "
                    + "e.g. \"modular_machinery_reborn:item\")";
        }
    }

    /** A plain "+N" on every requirement of one target. */
    public static RecipeModifier add(Target target, float value) {
        return new RecipeModifier(target, null, value, Operation.ADD, false);
    }

    /** A plain "xN" on every requirement of one target. */
    public static RecipeModifier multiply(Target target, float value) {
        return new RecipeModifier(target, null, value, Operation.MULTIPLY, false);
    }

    /** The modifier the original wrote for {@code alloy_furnace}: every item <b>output</b> doubled. */
    public static RecipeModifier multiplyOutput(Target target, float value) {
        return new RecipeModifier(target, IOType.OUTPUT, value, Operation.MULTIPLY, false);
    }

    /**
     * Reads one modifier object.
     *
     * <p>{@code target}, {@code io}, {@code operation} and {@code multiplier} are required here just as they
     * were in the original's {@code Deserializer}; {@code affectChance} is optional and defaults to
     * {@code false}.
     */
    public static RecipeModifier parse(JsonElement element, Object where) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("A modifier of " + where + " must be a JSON object");
        }
        JsonObject json = element.getAsJsonObject();

        Target target = parseTarget(requireString(json, "target", where), where);
        IOType ioTarget = parseIo(requireString(json, "io", where), where);
        Operation operation = Operation.byId(requireInt(json, "operation", where), where);
        float multiplier = requireFloat(json, "multiplier", where);
        boolean affectsChance = json.has("affectChance") && json.get("affectChance").getAsBoolean();
        return new RecipeModifier(target, ioTarget, multiplier, operation, affectsChance);
    }

    private static Target parseTarget(String raw, Object where) {
        String path = raw.trim();
        int colon = path.indexOf(':');
        if (colon >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(path);
            if (id == null) {
                throw new JsonParseException("'target' in " + where + " is '" + raw + "', which is not a valid "
                        + "resource location. Write one of " + Target.allowed() + ".");
            }
            path = id.getPath();
        }
        // The original accepted both a fully qualified requirement type id and the bare path of a deprecated
        // alias; the namespace is not part of this mod's requirement identity either, so both collapse here.
        switch (path.toLowerCase(Locale.ROOT)) {
            case "item":
            case "itemstack":
                return Target.ITEM;
            case "fluid":
                return Target.FLUID;
            case "energy":
                return Target.ENERGY;
            case "duration":
                return Target.DURATION;
            case "*":
                return Target.ALL;
            default:
                throw new JsonParseException("'target' in " + where + " is '" + raw + "', which is not a "
                        + "requirement kind this mod has. Write one of " + Target.allowed() + ".");
        }
    }

    private static IOType parseIo(String raw, Object where) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        // The original's IOType#getByString also accepted the "input"/"output" strings the recipe schema uses.
        if (value.equals("input") || value.equals("in")) {
            return IOType.INPUT;
        }
        if (value.equals("output") || value.equals("out")) {
            return IOType.OUTPUT;
        }
        throw new JsonParseException("'io' in " + where + " is '" + raw + "'; write \"input\" or \"output\".");
    }

    private static String requireString(JsonObject json, String key, Object where) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("A modifier of " + where + " is missing the string '" + key + "'");
        }
        return element.getAsString();
    }

    private static int requireInt(JsonObject json, String key, Object where) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("A modifier of " + where + " is missing the number '" + key + "'");
        }
        return element.getAsInt();
    }

    private static float requireFloat(JsonObject json, String key, Object where) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("A modifier of " + where + " is missing the number '" + key + "'");
        }
        return element.getAsFloat();
    }

    /**
     * Whether this modifier applies to one value.
     *
     * <p>The three tests are the original's, in its order: target, side, and whether the value is a chance. The
     * side test passes when the modifier names no side ({@code null}), which is how the original expressed "both
     * directions" — a machine can write one modifier that hits inputs and outputs alike.
     */
    public boolean matches(Target requirementTarget, IOType requirementIo, boolean isChance) {
        if (this.target == null || this.target == Target.DURATION) {
            return false;
        }
        if (this.target != Target.ALL && this.target != requirementTarget) {
            return false;
        }
        // An unnamed side means either, but a direction-aware call (requirementIo != null) still excludes the
        // opposite one — the original's condition was exactly `ioType != null && mod.ioTarget != ioType`.
        // A direction-blind call (requirementIo == null) accepts any modifier side, which is what the
        // parallelism ceiling needs: at most copies every requirement scales together.
        if (this.ioTarget != null && requirementIo != null && this.ioTarget != requirementIo) {
            return false;
        }
        return this.affectsChance == isChance;
    }
}
