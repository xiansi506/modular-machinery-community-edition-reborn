package com.reborn.modularmachinery.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineDefinitions;
import com.reborn.modularmachinery.machine.MachineSchema;
import dev.latvian.mods.kubejs.event.EventJS;

import java.util.List;
import java.util.Map;

/**
 * The script-facing machine builder: {@code MachineRegistryEvents.registry(event => event.machine('x').part(…))}.
 *
 * <p>Every method here only appends a field to a {@link JsonObject}. {@link #register()} hands that object to
 * {@link MachineSchema#read}, which is the very code the data-pack loader runs, so a malformed script machine
 * cannot produce a different (or worse) sentence than a malformed JSON one — {@code mmverify} asserts that
 * literally, by parsing the same defect through both stacks and comparing the messages.
 *
 * <p>That is also why this class does no validation of its own beyond "was a value of the right shape given to a
 * Java method": anything that could be answered differently by the two paths is deliberately left to the schema.
 * A field the schema does not implement yet ({@code has-factory}, {@code modifiers}, …) is not offered as a
 * method at all, so a script cannot spell it by accident, and one that arrives from a data pack merge is refused
 * by name — see {@link MachineSchema#DEFERRED_ROOT_FIELDS}.
 *
 * <p>Coordinates follow the JSON schema exactly: one part carries a position (or a list of them, giving the
 * cartesian product the original's {@code parts} supported) and one or more accepted blocks. {@code (0,0,0)} is
 * the controller's own position and is skipped by the schema, as it always was. One position is spelled
 * {@link #part(int, int, int, Object...)} and several at once {@link #parts(List, List, List, Object...)}: the
 * two names are the whole reason the array spelling cannot be confused with the scalar one by a script engine
 * that resolves a call by name before it weighs the arguments. 0.28.1 had both as {@code part}, and 0.28.2
 * renamed the list form rather than leaving the resolution to Rhino's overload weights.
 */
public final class MachineBuilderJS extends EventJS {

    private final MachineRegistryEventJS owner;
    private final JsonObject json = new JsonObject();
    private final String where;
    private JsonArray parts;
    private JsonObject currentPart;

    private MachineBuilderJS(MachineRegistryEventJS owner, String where) {
        this.owner = owner;
        this.where = where;
    }

    /**
     * Starts a definition. {@code id} is the machine's registry name; a bare path gets this mod's namespace,
     * exactly as the {@code registryname} field behaves in a definition file.
     */
    static MachineBuilderJS machine(MachineRegistryEventJS owner, String id) {
        MachineBuilderJS builder = new MachineBuilderJS(owner, "machine '" + id + "'");
        builder.json.addProperty("registryname", id);
        return builder;
    }

    /** {@code localizedname}: the literal display name when no translation key exists for the machine. */
    public MachineBuilderJS localizedName(String name) {
        this.json.addProperty("localizedname", name);
        return this;
    }

    /**
     * One structure position, described by the blocks that may stand there.
     *
     * <pre>{@code
     * .part(1, -1, 0, 'minecraft:stone')
     * .part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=plain]',
     *                  'modular_machinery_reborn:parallel_controller')
     * }</pre>
     *
     * <p>This is the <b>only</b> method named {@code part}, and that is a rule rather than a coincidence: Rhino
     * resolves a script's call by name first and by argument conversion second, so a second same-named overload
     * with the same arity would leave the two spellings to be told apart by conversion weight and by the order
     * {@code getMethods()} happens to return — which the JVM specification does not define. Several positions at
     * once are spelled {@link #parts(List, List, List, Object...)}, a different name.
     *
     * @param x        a coordinate, relative to the controller at {@code (0,0,0)}
     * @param elements block descriptors; a namespaced block id, optionally with {@code [state=value]}, or the
     *                 name of a variable set that the data pack (not a script) declared
     */
    public MachineBuilderJS part(int x, int y, int z, Object... elements) {
        return partInternal(List.of(x), List.of(y), List.of(z), elements);
    }

    /**
     * The same, for several positions at once: the schema expands the lists into their cartesian product, which
     * is how one entry describes a whole ring or wall.
     *
     * <pre>{@code
     * .parts([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')
     * }</pre>
     *
     * <p>It is {@code parts} and not a second {@code part} because of what a name costs in Rhino: see
     * {@link #part(int, int, int, Object...)}. Every coordinate list must be non-empty; an empty one is refused
     * with the axis it belongs to rather than being expanded to nothing.
     *
     * <p>The elements are typed {@code ? extends Number} rather than {@code Integer} for a measured reason. A
     * script's array reaches Java as a {@code List} of {@code Double} — asserted in {@code mmverify}'s section Z10
     * — so a {@code List<Integer>} parameter accepts the list and then throws {@code ClassCastException} on its
     * first element, while a {@code Number}-typed one reads it. {@link #coordinate} does the conversion, and any
     * value that is not a whole number is still refused by name.
     */
    public MachineBuilderJS parts(List<? extends Number> x, List<? extends Number> y, List<? extends Number> z,
            Object... elements) {
        return partInternal(x, y, z, elements);
    }

    private MachineBuilderJS partInternal(List<? extends Number> x, List<? extends Number> y,
            List<? extends Number> z, Object[] elements) {
        if (elements == null || elements.length == 0) {
            throw new JsonParseException("A part of " + this.where + " has no 'elements'. Pass at least one "
                    + "block descriptor (or the name of a variable set) as the last argument, e.g. "
                    + ".part(1, -1, 0, 'minecraft:stone').");
        }
        JsonArray accepted = new JsonArray();
        for (Object element : elements) {
            accepted.add(requireText(element, "elements"));
        }

        this.currentPart = new JsonObject();
        JsonArray xs = coordinate(x, "x");
        JsonArray ys = coordinate(y, "y");
        JsonArray zs = coordinate(z, "z");
        // A single number and a one-element array are the same thing to the schema, so the shorter spelling is
        // used whenever it applies: a hand-written definition and a script-built one then look alike.
        this.currentPart.add("x", xs.size() == 1 ? xs.get(0) : xs);
        this.currentPart.add("y", ys.size() == 1 ? ys.get(0) : ys);
        this.currentPart.add("z", zs.size() == 1 ? zs.get(0) : zs);
        if (accepted.size() == 1) {
            this.currentPart.addProperty("elements", accepted.get(0).getAsString());
        } else {
            this.currentPart.add("elements", accepted);
        }

        if (this.parts == null) {
            this.parts = new JsonArray();
            this.json.add("parts", this.parts);
        }
        this.parts.add(this.currentPart);
        return this;
    }

    /**
     * Replaces the accepted blocks of the part most recently added — the counterpart of writing two descriptors
     * in one {@code elements} array, for a caller that decides them after the position.
     */
    public MachineBuilderJS elements(Object... elements) {
        if (this.currentPart == null) {
            throw new JsonParseException("elements() on " + this.where + " has no part to describe: call "
                    + ".part(x, y, z, ...) first.");
        }
        if (elements == null || elements.length == 0) {
            throw new JsonParseException("elements() on " + this.where + " was given nothing to accept. Pass at "
                    + "least one block descriptor.");
        }
        JsonArray accepted = new JsonArray();
        for (Object element : elements) {
            accepted.add(requireText(element, "elements"));
        }
        if (accepted.size() == 1) {
            this.currentPart.addProperty("elements", accepted.get(0).getAsString());
        } else {
            this.currentPart.add("elements", accepted);
        }
        return this;
    }

    /** A shorthand for {@code part(x, y, z, descriptor)} with a single accepted block. */
    public MachineBuilderJS block(int x, int y, int z, Object element) {
        return part(x, y, z, element);
    }

    /**
     * Finishes the definition and hands it to the script layer.
     *
     * <p>All validation happens inside {@link MachineSchema#read} — a malformed definition throws here, with the
     * loader's own sentence, and the machine is not registered. The throw reaches KubeJS, which reports it
     * against the script line it came from and skips the rest of that listener.
     */
    public MachineDefinition register() {
        MachineDefinition definition = MachineSchema.read(this.json, this.where, Map.of(), true);
        MachineDefinitions.stage(definition);
        this.owner.countRegistered(definition);
        return definition;
    }

    /**
     * The definition as it would be written to a data pack.
     *
     * <p>Present because the offline harness compares exactly what this builder produces with what the JSON
     * loader consumes — the two stacks must answer a defect identically, and that is only checkable if the
     * script's object can be read back out. It is not part of the script-facing API's intent: a script uses
     * {@link #register()}.
     */
    public JsonObject toJson() {
        return this.json;
    }

    @Override
    public String toString() {
        return "MachineBuilderJS[" + this.json + "]";
    }

    /**
     * A coordinate list that the schema can expand.
     *
     * <p>Each entry has to be a whole number, and the check is deliberately here rather than in the schema: the
     * schema's {@code readCoordinates} sees the JSON <i>after</i> this class has written it, so a coordinate that
     * arrived as {@code 1.5} would already have been truncated to {@code 1} and the schema would have nothing to
     * refuse. The parameter is a {@code Number} because that is what survives Rhino — a script's array is a list
     * of {@code Double} — so "is it whole?" is a question Java's type system no longer answers for us.
     *
     * <p>The message therefore names the fractional value instead of borrowing the schema's own wording: section
     * Z6 asserts that this class re-implements none of the schema's rejection sentences, so a sentence that
     * belongs to {@code MachineSchema} must not appear here even when the rule sounds similar.
     */
    private static JsonArray coordinate(List<? extends Number> values, String axis) {
        if (values == null || values.isEmpty()) {
            throw new JsonParseException("Coordinate '" + axis + "' was given no value. Pass at least one whole "
                    + "number, e.g. .part(1, -1, 0, ...).");
        }
        JsonArray array = new JsonArray();
        for (Number value : values) {
            if (value == null) {
                throw new JsonParseException("Coordinate '" + axis + "' contains a null. Coordinates are whole "
                        + "numbers, e.g. .part(1, -1, 0, ...).");
            }
            double number = value.doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number)) {
                throw new JsonParseException("Coordinate '" + axis + "' is " + number + ", which is not whole: "
                        + "a fractional coordinate would be truncated to a different block. Write a whole "
                        + "number, e.g. .part(1, -1, 0, ...).");
            }
            array.add((int) number);
        }
        return array;
    }

    /**
     * A string a script handed us. A Rhino string literal arrives as a Java {@link String}; anything else is
     * refused here rather than being stringified, because {@code JsonPrimitive#getAsString} would happily turn a
     * number or a boolean into a block descriptor and the schema would then report a confusing missing-variable
     * error for a mistake that is really a type mistake.
     */
    private static String requireText(Object value, String field) {
        if (value instanceof String text) {
            return text;
        }
        throw new JsonParseException("'" + field + "' must be a string, but a " + describe(value) + " was given. "
                + "Write a block descriptor such as 'minecraft:stone', or a variable set's name.");
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
