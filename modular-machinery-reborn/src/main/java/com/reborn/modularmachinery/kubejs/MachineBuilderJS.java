package com.reborn.modularmachinery.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineDefinitions;
import com.reborn.modularmachinery.machine.MachineSchema;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.util.JsonIO;

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
 *
 * <p><b>0.31.0 (v2b) closed the last gap here.</b> Through 0.30.1 this API offered the core fields only and the
 * eleven extended root fields were refused by name on the script path; every one of them now has a method below,
 * and {@link MachineSchema#DEFERRED_ROOT_FIELDS} is empty. The rule did not change with it: these methods still
 * append fields and nothing else, so a mistyped {@code failure-action}, a negative {@code max-threads} or a
 * duplicate {@code core-threads} name is answered by the schema, in the same sentence a definition file gets.
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

    // ---------------------------------------------------------------------------- v2b: the extended fields
    //
    // Every method below does one thing: append a root field to the JSON object, spelled exactly as a definition
    // file spells it. Not one of them validates a value or an interaction between fields, and that is deliberate
    // rather than unfinished — MachineSchema is the single rule source for both paths, so a rule written here
    // would be a second opinion that the data pack does not share. Section Z2 asserts that both paths answer a
    // defect with the same sentence; a check added here would go red there.
    //
    // Two consequences worth stating once, because both are observable:
    //  * The value checks that *can* be reported here are the ones Java's type system already made: a script
    //    cannot pass an object where an int belongs without Rhino saying so first.
    //  * The four validation rules that are NOT of that kind are left to the schema and are reachable from a
    //    script exactly as from a data pack: `internal-parallelism > max-parallelism`, a negative thread or
    //    parallelism count, an unknown `failure-action` name, and a duplicate `core-threads` name.

    /** The JSON root keys, spelled as the schema spells them — one place, so a rename is one edit. */
    private static final String KEY_FAILURE_ACTION = "failure-action";
    private static final String KEY_REQUIRES_BLUEPRINT = "requires-blueprint";
    private static final String KEY_MAX_PARALLELISM = "max-parallelism";
    private static final String KEY_INTERNAL_PARALLELISM = "internal-parallelism";
    private static final String KEY_PARALLELIZABLE = "parallelizable";
    private static final String KEY_HAS_FACTORY = "has-factory";
    private static final String KEY_FACTORY_ONLY = "factory-only";
    private static final String KEY_MAX_THREADS = "max-threads";
    private static final String KEY_MODIFIERS = "modifiers";
    private static final String KEY_SMART_INTERFACES = "smart-interfaces";
    private static final String KEY_CORE_THREADS = "core-threads";

    /**
     * {@code failure-action}: what a craft's progress does on a tick it cannot pay for.
     *
     * <p>The value is written through as a string without being checked against the vocabulary. That is the same
     * division of labour the rest of this class keeps, and here it also keeps the better message: the schema
     * knows the three names, their meanings, the default, and which of the two things was wrong — "must be a
     * string" versus "must be one of …". A check for "is it one of the three" here would pre-empt that sentence
     * with a worse one, and would have to be kept in step with {@link FailureAction} by hand.
     */
    public MachineBuilderJS failureAction(Object value) {
        this.json.add(KEY_FAILURE_ACTION, new JsonPrimitive(requireText(value, "failureAction")));
        return this;
    }

    /** {@code requires-blueprint}: the machine forms only around a controller holding its own blueprint. */
    public MachineBuilderJS requiresBlueprint(boolean value) {
        this.json.addProperty(KEY_REQUIRES_BLUEPRINT, value);
        return this;
    }

    /**
     * {@code max-parallelism}: the ceiling a parallel controller may raise a craft to.
     *
     * <p>A negative value is refused by the schema, not here, and its sentence is the one worth showing: the
     * original floored it at zero, so "write 0 for 'no built-in parallelism'" is the advice a script needs.
     */
    public MachineBuilderJS maxParallelism(int value) {
        this.json.addProperty(KEY_MAX_PARALLELISM, value);
        return this;
    }

    /** {@code internal-parallelism}: the copies this machine runs on its own, before any controller raises it. */
    public MachineBuilderJS internalParallelism(int value) {
        this.json.addProperty(KEY_INTERNAL_PARALLELISM, value);
        return this;
    }

    /**
     * {@code parallelizable}: whether a parallel controller may raise this machine at all.
     *
     * <p>The contradiction {@code parallelizable: false} with {@code internal-parallelism: 2} is the loader's
     * business: it warns rather than refuses (the original's own default was a machine that ran one copy), and a
     * script declaring it gets the same warning a definition file does.
     */
    public MachineBuilderJS parallelizable(boolean value) {
        this.json.addProperty(KEY_PARALLELIZABLE, value);
        return this;
    }

    /** {@code has-factory}: this machine can be built around a factory controller. */
    public MachineBuilderJS hasFactory(boolean value) {
        this.json.addProperty(KEY_HAS_FACTORY, value);
        return this;
    }

    /**
     * {@code factory-only}: the machine may only be built around a factory controller.
     *
     * <p>{@code factory-only} without {@code has-factory} is contradictory. The loader warns and carries on
     * rather than refusing, so a script can write the pair in either order and still be told what is wrong.
     */
    public MachineBuilderJS factoryOnly(boolean value) {
        this.json.addProperty(KEY_FACTORY_ONLY, value);
        return this;
    }

    /**
     * {@code max-threads}: how many recipes this machine's factory controller may run at once.
     *
     * <p>Omitting it means the config key's value — the same default a definition file that omits it gets — so
     * writing it is only needed to pin a machine to something other than the pack's setting.
     */
    public MachineBuilderJS maxThreads(int value) {
        this.json.addProperty(KEY_MAX_THREADS, value);
        return this;
    }

    // ---------------------------------------------------------------- the three structured fields
    //
    // These three are the reason this class takes a JsonIO dependency it did not need before. A machine
    // definition's `modifiers`, `smart-interfaces` and `core-threads` are arrays of *objects*, and a script has
    // no way to hand this class one: the existing methods take String/int/Object... precisely because Rhino
    // passes those through, and nothing about a nested object survives that treatment.
    //
    // So a script passes the entry as either a JSON string or a KubeJS object literal, and requireObject(…)
    // turns both into the same JsonObject (JsonIO is KubeJS's own converter, the very one its recipe schema
    // uses). The entry is then NOT validated here — the schema reads the assembled array and refuses a
    // malformed entry with the sentence it would use for a definition file.

    /**
     * One {@code modifiers} entry: a structure position whose block contributes recipe modifiers.
     *
     * <pre>{@code
     * .modifier(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=vent]',
     *           '{"target":"item","io":"output","operation":1,"multiplier":2.0}')
     * }</pre>
     *
     * <p>{@code (0,0,0)} is the controller's own position and is skipped by the schema, as it always was — with
     * a warning rather than silently, because an author who writes one has probably meant a neighbouring
     * position. This method writes it and lets the schema say so.
     *
     * @param element  the block descriptor(s) that may sit at this position; the same spelling {@link #part}
     *                 takes, and a list of them is accepted exactly as a data pack's {@code elements} array is
     */
    public MachineBuilderJS modifier(int x, int y, int z, Object element, Object modifier) {
        return modifier(x, y, z, element, modifier, null);
    }

    /** The same, with the definition file's optional {@code description} for the entry. */
    public MachineBuilderJS modifier(int x, int y, int z, Object element, Object modifier, Object description) {
        JsonObject entry = new JsonObject();
        entry.addProperty("x", x);
        entry.addProperty("y", y);
        entry.addProperty("z", z);
        entry.add("elements", elementsOf(element, "modifier"));
        entry.add("modifier", requireObject(modifier, "modifier"));
        if (description != null) {
            entry.addProperty("description", requireText(description, "description"));
        }
        array(KEY_MODIFIERS).add(entry);
        return this;
    }

    /**
     * One {@code smart-interfaces} entry: an interface type this machine declares, which a recipe's
     * {@code interface_number_input} requirement quotes by name.
     *
     * <pre>{@code
     * .smartInterface('mode', 0, 1000)
     * .smartInterface('temp', 20, 0, '{ "header": "gui.example.temp", "notequal": "gui.example.temp.bad" }')
     * }</pre>
     *
     * <p>The positional form is a convenience for the three fields nearly every declaration sets; the optional
     * entry carries the rest ({@code header} / {@code value} / {@code footer} / {@code notequal}). Both end up in
     * the same object, and {@code type} is written last so the flat form always wins the one key it owns. The
     * schema reads the result exactly as it reads a definition file's.
     *
     * @param type         the type's name, unique within this machine; the thing a recipe quotes
     * @param defaultValue the value a freshly formed machine starts from
     * @param priority     which declared type an undeclared value binds to; the largest wins
     */
    public MachineBuilderJS smartInterface(Object type, double defaultValue, int priority) {
        return smartInterface(type, defaultValue, priority, null);
    }

    /** The same, with an object (or the same text as a JSON string) carrying the optional display fields. */
    public MachineBuilderJS smartInterface(Object type, double defaultValue, int priority, Object extra) {
        JsonObject entry = requireObject(extra, "smartInterface");
        entry.addProperty("type", requireText(type, "type"));
        entry.addProperty("default", defaultValue);
        entry.addProperty("priority", priority);
        array(KEY_SMART_INTERFACES).add(entry);
        return this;
    }

    /**
     * One {@code core-threads} entry: a factory thread that exists from the moment the machine forms and is
     * never reclaimed, as opposed to an ordinary thread, which an idle sweep drops after 200 ticks.
     *
     * <pre>{@code
     * .coreThread('smelter')                              // may run any recipe this machine has
     * .coreThread('diamond', 'alloy_smelter_diamond')     // pinned to one recipe
     * }</pre>
     *
     * <p>No recipes, or an empty list, means "any recipe" — the original read an empty set that way — and this
     * method then writes no {@code recipes} field at all rather than an empty array, so the JSON a script
     * produces is the JSON a hand-written definition would carry.
     */
    public MachineBuilderJS coreThread(Object name, Object... recipes) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", requireText(name, "name"));
        if (recipes != null && recipes.length > 0) {
            JsonArray names = new JsonArray();
            for (Object recipe : recipes) {
                names.add(requireText(recipe, "recipes"));
            }
            entry.add("recipes", names);
        }
        array(KEY_CORE_THREADS).add(entry);
        return this;
    }

    /** The root array under {@code key}, created on first use — a definition with no entries writes no field. */
    private JsonArray array(String key) {
        JsonElement existing = this.json.get(key);
        if (existing instanceof JsonArray present) {
            return present;
        }
        JsonArray created = new JsonArray();
        this.json.add(key, created);
        return created;
    }

    /**
     * One accepted block of a structured entry: a single descriptor, or several.
     *
     * <p>A list is written as an array and a single descriptor as a bare string — the same two shapes the schema
     * reads for {@code parts} and {@code modifiers} alike, so a script-built entry and a hand-written one are
     * byte-identical when they describe the same thing.
     */
    private static JsonElement elementsOf(Object element, String field) {
        if (element instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new JsonParseException("'" + field + "' was given an empty list of elements. Pass at "
                        + "least one block descriptor, or use the single-descriptor spelling.");
            }
            JsonArray accepted = new JsonArray();
            for (Object single : list) {
                accepted.add(requireText(single, "elements"));
            }
            return accepted;
        }
        // A script's `['a', 'b']` can arrive as an Object[] rather than a List, depending on which conversion
        // Rhino picks; both spellings mean "several descriptors" and both are written as an array.
        if (element instanceof Object[] objects) {
            JsonArray accepted = new JsonArray();
            for (Object single : objects) {
                accepted.add(requireText(single, "elements"));
            }
            if (accepted.isEmpty()) {
                throw new JsonParseException("'" + field + "' was given an empty array of elements. Pass at "
                        + "least one block descriptor, or use the single-descriptor spelling.");
            }
            return accepted;
        }
        return new JsonPrimitive(requireText(element, "elements"));
    }

    /**
     * The object a structured entry is built from: a KubeJS object literal, a JSON string, or an object that is
     * already a {@link JsonObject}.
     *
     * <p>The string form is the one that is guaranteed to work and is what the guide documents. The object-literal
     * form goes through {@link JsonIO#of(Object)}, KubeJS's own converter — the same one its recipe schema uses —
     * so a script may write either and get the same JSON. {@code null} yields a fresh empty object, which is how
     * the optional-argument overload of {@link #smartInterface} spells "no extra fields".
     */
    private static JsonObject requireObject(Object value, String field) {
        if (value == null) {
            return new JsonObject();
        }
        JsonElement element;
        if (value instanceof String text) {
            try {
                element = JsonParser.parseString(text);
            } catch (JsonParseException malformed) {
                throw new JsonParseException("'" + field + "' was given a string that is not valid JSON ("
                        + malformed.getMessage() + "). Write the entry as an object literal, or as a JSON string "
                        + "such as '{\"target\":\"item\",\"io\":\"output\",\"operation\":1,\"multiplier\":2.0}'.");
            }
        } else {
            element = JsonIO.of(value);
        }
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("'" + field + "' must be an object (a JSON object literal, or the same "
                    + "text as a string), but a " + describe(value) + " was given.");
        }
        return element.getAsJsonObject();
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
