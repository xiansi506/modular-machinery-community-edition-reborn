package com.reborn.modularmachinery.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The machine-definition schema: reading a definition and every rule it has to satisfy.
 *
 * <p>This class exists because there is now more than one way to hand this mod a machine definition. Until
 * 0.28.0 the only producer was {@link MachineLoader}, which parses the JSON files of
 * {@code data/<namespace>/machinery/}, so every rule — "must be one of …", "Write …" — lived inside that
 * parse. A KubeJS script is the second producer (D7/v2a), and a programmatic path that skimmed the fields it
 * cared about would give script authors <b>strictly worse errors for the same schema</b> than data-pack
 * authors get. That is the class of inconsistency this project has paid for repeatedly, so the rules were
 * lifted out of the loader instead: both paths hand a {@link JsonObject} to {@link #read} and get the same
 * {@link JsonParseException} with the same sentence in it.
 *
 * <p>The KubeJS side is therefore a <b>JSON builder</b> and nothing else: {@code MachineRegisterEventJS} and
 * {@code MachineBuilderJS} append the fields a script asked for to a {@code JsonObject} and call
 * {@link #read} — the writer of a script and the writer of a data pack are answered by the same code.
 * {@code mmverify} asserts that with two stacks: a script-shaped and a data-pack-shaped object with the same
 * defect must produce byte-identical messages.
 *
 * <h2>Two differences between the two paths, both deliberate</h2>
 *
 * <ol>
 *   <li><b>{@code strict}</b>. A data pack is parsed tolerantly for one reason only: backwards compatibility
 *       with definitions written before a field existed. The loader reports an unknown root field and carries
 *       on, which is what {@link #read(JsonElement, Object, Map)} does. A script has no such history — it is
 *       written against this API today — so {@link #read(JsonElement, Object, Map, boolean)} in strict mode
 *       <b>rejects</b> an unknown field instead. That closes the gap the survey named: through the loader the
 *       message for an unknown field is a warning, and through KubeJS the identical mistake can now be an
 *       error that names every field that does exist.</li>
 *   <li><b>{@code deferred}</b>. The v2a slice deliberately implements the core fields only. A field that the
 *       JSON loader accepts but the script API does not implement yet is <b>refused by name</b> rather than
 *       accepted and ignored, so "the script said {@code has-factory} and nothing happened" cannot occur; see
 *       {@link #DEFERRED_ROOT_FIELDS}.</li>
 * </ol>
 *
 * <p>Nothing here touches a registry, a level or a config file, which is what lets the offline harness drive
 * the whole schema — including the KubeJS builder — without a game.
 */
public final class MachineSchema {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Every root field this loader reads, for the unknown-field message. {@code has-factory} and
     * {@code factory-only} are the original's own schema ({@code DynamicMachinePreDeserializer:77-89});
     * {@code max-parallelism} / {@code internal-parallelism} / {@code parallelizable} (D12),
     * {@code max-threads} / {@code core-threads} (D15) and {@code smart-interfaces} (D16) are this project's
     * documented extensions, each for the same reason: the original's values lived on {@code AbstractMachine}
     * and only a CraftTweaker script could set them, and this project's authors have no script.
     */
    public static final List<String> KNOWN_ROOT_FIELDS = List.of(
            "registryname", "localizedname", "parts", "failure-action", "requires-blueprint",
            "max-parallelism", "internal-parallelism", "parallelizable", "modifiers",
            "has-factory", "factory-only", "max-threads", "core-threads", "smart-interfaces");

    /**
     * Root fields the JSON loader <b>does</b> evaluate but the v2a KubeJS machine API does not implement yet.
     *
     * <p>A script that spells one of these is refused, loudly. The alternative — accepting it and ignoring it —
     * is the failure mode this project keeps paying for: a definition that looks like it said something and
     * did nothing. {@code 移植方案-v2.md} §9 records the split; this list is the machine-readable half of it.
     *
     * <p>Why this is coherent rather than a hole in the API: every one of these fields has a default that
     * this project already uses for a definition that omits it — {@code modifiers} empty, no smart interfaces,
     * {@code has-factory} false, {@code max-threads} the config default, {@code core-threads} empty, parallelism
     * at one copy, {@code failure-action} {@code still}, {@code requires-blueprint} false. A machine added by a
     * script therefore behaves like a data-pack machine that wrote none of them; nothing about the core API
     * depends on any of them.
     *
     * <p>{@code requires-blueprint} deserves one sentence of its own, because it is the only entry here that
     * <b>could</b> have been supported cheaply: blueprints are minted on demand
     * ({@code BlueprintItem.forMachine} from {@code ModBlocks.TAB}, one per {@link MachineRegistry} entry), so a
     * script machine would get a working blueprint item. It is deferred anyway because the script's own idiom for
     * "this machine needs a blueprint" is not settled, and because {@code requires-blueprint} interacts with the
     * form-search order ({@code MachineRegistry.findMatch}); v2b will add it together with the rest.
     */
    public static final List<String> DEFERRED_ROOT_FIELDS = List.of(
            "modifiers", "smart-interfaces", "has-factory", "factory-only", "max-threads", "core-threads",
            "max-parallelism", "internal-parallelism", "parallelizable", "failure-action", "requires-blueprint");

    private MachineSchema() {
    }

    /** The original's tolerance: an unknown root field is reported and skipped. */
    public static MachineDefinition read(JsonElement element, Object where, Map<String, List<String>> variables) {
        return read(element, where, variables, false);
    }

    /**
     * Reads a machine definition.
     *
     * @param element   the definition, as a JSON object
     * @param where     what to name in an error message — a file id for a data pack, {@code machine 'foo'} for
     *                  a script
     * @param variables the {@code *.var.json} variable sets, empty when the caller has none
     * @param strict    {@code true} to reject an unknown root field instead of reporting it and carrying on
     * @throws JsonParseException when the definition is malformed, with a sentence saying what to write
     */
    public static MachineDefinition read(JsonElement element, Object where, Map<String, List<String>> variables,
                                         boolean strict) {
        JsonObject root = asObject(element, where);

        rejectDeferred(root, where, strict);

        String registryName = requireString(root, "registryname", where);
        ResourceLocation id = registryName.contains(":")
                ? ResourceLocation.tryParse(registryName)
                : new ResourceLocation(ModularMachineryReborn.MOD_ID, registryName);
        if (id == null) {
            throw new JsonParseException("Malformed 'registryname' '" + registryName + "' in " + where);
        }

        String localizedName = root.has("localizedname")
                ? root.get("localizedname").getAsString()
                : id.getPath();

        FailureAction failureAction = MachineDefinition.DEFAULT_FAILURE_ACTION;
        if (root.has("failure-action")) {
            failureAction = readFailureAction(root, where);
        }

        boolean requiresBlueprint = root.has("requires-blueprint")
                && root.get("requires-blueprint").getAsBoolean();

        int maxParallelism = MachineDefinition.DEFAULT_MAX_PARALLELISM;
        if (root.has("max-parallelism")) {
            maxParallelism = readParallelism(root, "max-parallelism", where);
        }

        int internalParallelism = MachineDefinition.DEFAULT_INTERNAL_PARALLELISM;
        if (root.has("internal-parallelism")) {
            internalParallelism = readParallelism(root, "internal-parallelism", where);
        }

        boolean parallelizable = MachineDefinition.DEFAULT_PARALLELIZABLE;
        if (root.has("parallelizable")) {
            JsonElement flag = root.get("parallelizable");
            if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("'parallelizable' of " + where + " must be true or false. "
                        + "Write \"parallelizable\": true to let a parallel controller raise this machine's "
                        + "parallelism, or false to forbid it.");
            }
            parallelizable = flag.getAsBoolean();
        }

        // "internal-parallelism may not exceed max-parallelism" is the same rule the original enforced, but the
        // original enforced it silently: AbstractMachine#setInternalParallelism quietly raised maxParallelism to
        // match. A definition file is read by a person, so a contradiction is reported instead.
        if (internalParallelism > maxParallelism) {
            throw new JsonParseException("'internal-parallelism' of " + where + " is " + internalParallelism
                    + ", which is above its 'max-parallelism' (" + maxParallelism + "). The ceiling must be at "
                    + "least the built-in amount. Raise \"max-parallelism\" to " + internalParallelism
                    + " or more, or lower \"internal-parallelism\".");
        }
        if (!parallelizable && internalParallelism > 1) {
            LOGGER.warn("[{}] {} sets parallelizable=false while internal-parallelism is {}; the machine will "
                            + "still not run in parallel. Set \"parallelizable\": true, or remove "
                            + "'internal-parallelism'.",
                    ModularMachineryReborn.MOD_ID, where, internalParallelism);
        }

        List<MachineModifier> modifiers = readModifiers(root, variables, where);

        // ---------------------------------------------------------------- M6e: the factory
        int maxThreads = MachineDefinition.DEFAULT_MAX_THREADS;
        if (root.has(FactoryThreadModel.JSON_MAX_THREADS)) {
            maxThreads = readThreadCount(root, FactoryThreadModel.JSON_MAX_THREADS, where);
        }

        boolean hasFactory = MachineDefinition.DEFAULT_HAS_FACTORY;
        if (root.has(FactoryThreadModel.JSON_HAS_FACTORY)) {
            hasFactory = readBoolean(root, FactoryThreadModel.JSON_HAS_FACTORY, where,
                    "Write \"has-factory\": true to give this machine a factory controller (a block that runs "
                            + "several different recipes at once), or remove the field.");
        }

        boolean factoryOnly = MachineDefinition.DEFAULT_FACTORY_ONLY;
        if (root.has(FactoryThreadModel.JSON_FACTORY_ONLY)) {
            factoryOnly = readBoolean(root, FactoryThreadModel.JSON_FACTORY_ONLY, where,
                    "Write \"factory-only\": true to say this machine may only be built around a factory "
                            + "controller, or remove the field.");
        }
        if (factoryOnly && !hasFactory) {
            LOGGER.warn("[{}] {} sets factory-only=true while has-factory is false. The two contradict each "
                            + "other: a factory-only machine has no ordinary controller, so nothing could ever "
                            + "form it. Add \"has-factory\": true, or remove 'factory-only'.",
                    ModularMachineryReborn.MOD_ID, where);
        }

        List<FactoryThreadModel.CoreThreadSpec> coreThreads = readCoreThreads(root, where);

        // ---------------------------------------------------------------- M6d-b: the smart interfaces
        List<SmartInterfaceType> smartInterfaces = readSmartInterfaces(root, where);

        JsonArray parts = asArray(root.get("parts"), where + " 'parts'");
        MachinePattern.Builder builder = MachinePattern.builder();
        boolean ignoredExtras = false;

        for (JsonElement partElement : parts) {
            JsonObject part = asObject(partElement, where + " part");
            List<Integer> xs = readCoordinates(part, "x", where);
            List<Integer> ys = readCoordinates(part, "y", where);
            List<Integer> zs = readCoordinates(part, "z", where);
            List<BlockMatcher> accepted = readElements(part, variables, where);

            if (part.has("nbt") || part.has("preview-nbt") || part.has("selector-tag")) {
                ignoredExtras = true;
            }

            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        if (x == 0 && y == 0 && z == 0) {
                            // The controller occupies the origin and is not part of the pattern.
                            continue;
                        }
                        builder.add(new BlockPos(x, y, z), accepted);
                    }
                }
            }
        }

        if (builder.isEmpty()) {
            throw new JsonParseException("Machine definition " + where + " has no parts besides the controller");
        }

        if (ignoredExtras) {
            LOGGER.warn("[{}] {} uses 'nbt', 'preview-nbt' or 'selector-tag', which M1 does not evaluate yet; "
                    + "those positions match on block state only", ModularMachineryReborn.MOD_ID, where);
        }

        List<String> unimplemented = new ArrayList<>();
        for (String key : UNIMPLEMENTED_ROOT_FIELDS) {
            if (root.has(key)) {
                unimplemented.add(key);
            }
        }
        if (!unimplemented.isEmpty()) {
            LOGGER.warn("[{}] {} declares {} — accepted but not evaluated; the fields are kept so the "
                    + "definitions stay faithful to the original", ModularMachineryReborn.MOD_ID, where,
                    String.join(", ", unimplemented));
        }

        // A "//"-prefixed key is the convention this project's examples use for an inline note. It is a legal
        // JSON member, so it is accepted — but a typo is not, and the two look alike in a diff. Only unknown
        // keys that do not start with "//" are reported.
        List<String> unknown = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.startsWith("//") || KNOWN_ROOT_FIELDS.contains(key)) {
                continue;
            }
            unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            if (strict) {
                throw new JsonParseException(where + " has root field(s) this schema does not know: "
                        + String.join(", ", unknown) + ". Nothing would read them, so the definition is "
                        + "refused rather than accepted with a field that does nothing. Check the spelling "
                        + "against the documented fields (" + String.join(", ", KNOWN_ROOT_FIELDS)
                        + "); the v2a script API implements the core fields only, and a note can be written "
                        + "as a \"//...\" key.");
            }
            LOGGER.warn("[{}] {} has root field(s) this loader does not know: {}. They are ignored. If they were "
                            + "meant to have an effect, check the spelling against the documented fields "
                            + "({}); a note can be written as a \"//...\" key.",
                    ModularMachineryReborn.MOD_ID, where, String.join(", ", unknown),
                    String.join(", ", KNOWN_ROOT_FIELDS));
        }

        return new MachineDefinition(id, localizedName, builder.build(), failureAction, requiresBlueprint,
                maxParallelism, internalParallelism, parallelizable, modifiers,
                maxThreads, hasFactory, factoryOnly, coreThreads, smartInterfaces);
    }

    /**
     * Refuses the fields the v2a script API does not implement — but only for a caller that asked for strict
     * reading.
     *
     * <p>A data pack may legitimately carry all of them: the JSON loader evaluates every one. This check is
     * therefore a property of the <b>script</b> path, where accepting a field the API cannot express would tell
     * an author their definition does something it does not.
     */
    private static void rejectDeferred(JsonObject root, Object where, boolean strict) {
        if (!strict) {
            return;
        }
        List<String> present = new ArrayList<>();
        for (String key : DEFERRED_ROOT_FIELDS) {
            if (root.has(key)) {
                present.add(key);
            }
        }
        if (present.isEmpty()) {
            return;
        }
        throw new JsonParseException(where + " declares " + String.join(", ", present)
                + ", which the KubeJS machine API does not implement yet. It is refused rather than accepted "
                + "and ignored, because a definition that looks like it said something and did nothing is the "
                + "failure this project keeps paying for. Remove the field to use the same default a data-pack "
                + "machine gets when it omits it (no modifiers, no smart interfaces, no factory, parallelism "
                + "of one copy, failure-action 'still', no blueprint requirement); the deferred slice is "
                + "recorded in 移植方案-v2.md §9.");
    }

    /**
     * {@code failure-action}: the original's root string, one of {@code reset} / {@code still} /
     * {@code decrease} ({@code RecipeFailureActions.java:15-18}), read exactly where the original read it
     * ({@code DynamicMachinePreDeserializer.java:44-51}) and carried on the definition
     * ({@code DynamicMachine.java:630-633}).
     *
     * <p>Two deliberate differences from the original, both this project's convention for a field a person
     * writes. A value that is not a string is reported <b>as such</b> — the original's type check existed, but
     * its message named the three values instead of the type, so `"failure-action": true` was answered with a
     * sentence about the vocabulary. And an unknown <b>name</b> is refused rather than silently becoming the
     * default, which is what {@code RecipeFailureActions#getFailureAction:49-56} did: a typo was invisible.
     */
    private static FailureAction readFailureAction(JsonObject root, Object where) {
        JsonElement element = root.get("failure-action");
        String vocabulary = "\"reset\", \"still\" or \"decrease\"";
        String guidance = " Write \"failure-action\": \"still\" to hold a craft's progress while it cannot be "
                + "served, \"decrease\" to lose one tick of progress per failed tick, or \"reset\" to drop the "
                + "whole craft's progress; remove the field to use the default ("
                + MachineDefinition.DEFAULT_FAILURE_ACTION.name().toLowerCase(Locale.ROOT) + ").";
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'failure-action' of " + where + " must be a string — one of "
                    + vocabulary + " (the original's RecipeFailureActions). Found " + element + "." + guidance);
        }
        String raw = element.getAsString();
        for (FailureAction action : FailureAction.values()) {
            if (action.name().equalsIgnoreCase(raw)) {
                return action;
            }
        }
        throw new JsonParseException("'failure-action' of " + where + " must be one of " + vocabulary
                + "; found \"" + raw + "\"." + guidance);
    }

    /**
     * {@code max-threads}: a non-negative whole number, reported rather than clamped so a typo cannot silently
     * make a factory single-threaded.
     */
    private static int readThreadCount(JsonObject root, String key, Object where) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be a whole number. Write a "
                    + "non-negative integer such as \"max-threads\": 4 — the number of recipes this machine's "
                    + "factory controller may run at once — or remove the field (the default is "
                    + MachineDefinition.DEFAULT_MAX_THREADS + ").");
        }
        int value = element.getAsInt();
        if (value < 0) {
            throw new JsonParseException("'" + key + "' of " + where + " is " + value + ", but a thread count "
                    + "cannot be negative. Write 0 for \"this machine may run nothing on a factory controller "
                    + "unless a core thread is declared\", or remove the field.");
        }
        return value;
    }

    /**
     * One of the two factory booleans. The original threw {@code JsonParseException("'has-factory' has to be
     * either 'true' or 'false'!")} ({@code DynamicMachinePreDeserializer:77-89}); the message here keeps that
     * shape and adds what the field actually does, which the original's did not.
     */
    private static boolean readBoolean(JsonObject root, String key, Object where, String advice) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be true or false. " + advice);
        }
        return element.getAsBoolean();
    }

    /**
     * {@code core-threads}: the preset of threads that always exist.
     *
     * <pre>{@code "core-threads": [ { "name": "smelter", "recipes": ["alloy_smelter_diamond"] } ] }</pre>
     *
     * <p>The original had no JSON for this either: {@code DynamicMachine#addCoreThread} was called from a
     * CraftTweaker script, and {@code FactoryRecipeThread#createCoreThread(threadName)} built the thread. Two
     * fields are all it needs — the name it is keyed by (the original's {@code threadName}) and the fixed recipe
     * set ({@code recipeSet}); an omitted or empty {@code recipes} means the thread may run anything the machine
     * offers, which is how the original read an empty set
     * ({@code FactoryRecipeThread.java:161-163}).
     */
    private static List<FactoryThreadModel.CoreThreadSpec> readCoreThreads(JsonObject root, Object where) {
        JsonElement element = root.get(FactoryThreadModel.JSON_CORE_THREADS);
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'" + FactoryThreadModel.JSON_CORE_THREADS + "' of " + where
                    + " must be an array of thread objects. Write \"core-threads\": [ { \"name\": \"smelter\", "
                    + "\"recipes\": [ \"alloy_smelter_diamond\" ] } ], or remove the field.");
        }
        List<FactoryThreadModel.CoreThreadSpec> specs = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject thread = asObject(entry, where + " core thread #" + index);
            if (!thread.has("name")) {
                throw new JsonParseException("Core thread #" + index + " of " + where + " has no 'name'. A core "
                        + "thread is identified by its name (the original's threadName), so write "
                        + "\"name\": \"smelter\".");
            }
            String name = thread.get("name").getAsString();
            if (name.isBlank()) {
                throw new JsonParseException("Core thread #" + index + " of " + where + " has an empty 'name'. "
                        + "Give it a name to write down, e.g. \"name\": \"smelter\".");
            }
            if (!names.add(name)) {
                throw new JsonParseException("Two core threads of " + where + " are both named '" + name
                        + "'. Names key the threads, so they have to differ — rename one of them.");
            }

            List<ResourceLocation> recipes = new ArrayList<>();
            if (thread.has("recipes")) {
                JsonElement raw = thread.get("recipes");
                if (!raw.isJsonArray()) {
                    throw new JsonParseException("'recipes' of core thread '" + name + "' in " + where
                            + " must be an array of recipe names. Write \"recipes\": [ \"alloy_smelter_diamond\" "
                            + "], or remove the field to let the thread run any recipe this machine has.");
                }
                for (JsonElement recipe : raw.getAsJsonArray()) {
                    if (!recipe.isJsonPrimitive()) {
                        throw new JsonParseException("'recipes' of core thread '" + name + "' in " + where
                                + " must contain only recipe names (strings)");
                    }
                    String raw2 = recipe.getAsString();
                    ResourceLocation id = raw2.contains(":")
                            ? ResourceLocation.tryParse(raw2)
                            : new ResourceLocation(ModularMachineryReborn.MOD_ID, raw2);
                    if (id == null) {
                        throw new JsonParseException("'" + raw2 + "' in the 'recipes' of core thread '" + name
                                + "' of " + where + " is not a valid recipe name. Write a recipe file's own id, "
                                + "e.g. \"modular_machinery_reborn:alloy_smelter_diamond\".");
                    }
                    recipes.add(id);
                }
            }
            specs.add(new FactoryThreadModel.CoreThreadSpec(name, recipes));
            index++;
        }
        return List.copyOf(specs);
    }

    /**
     * {@code smart-interfaces}: the interface types this machine declares — the JSON entry point the original
     * never had (see the class comment of {@link SmartInterfaceType} and the D16 record).
     *
     * <pre>{@code
     * "smart-interfaces": [
     *   { "type": "mode", "default": 0, "priority": 1000,
     *     "header": "gui.example.mode.header", "footer": "", "notequal": "gui.example.mode.mismatch" }
     * ]
     * }</pre>
     *
     * <p>Every field except {@code type} is optional, and {@code type} is the one thing the original also
     * required ({@code smartInterfaces} was a {@code Map<String, SmartInterfaceType>}, so a type with no name
     * could never be looked up). The loud half is that a malformed value says what to write instead — the
     * original had no schema here at all, so there is nothing to be faithful to but this project's own
     * convention (D12's "a definition file is read by a person").
     */
    private static List<SmartInterfaceType> readSmartInterfaces(JsonObject root, Object where) {
        JsonElement element = root.get("smart-interfaces");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'smart-interfaces' of " + where + " must be an array of interface "
                    + "type objects. Write \"smart-interfaces\": [ { \"type\": \"mode\", \"default\": 0, "
                    + "\"priority\": 1000 } ], or remove the field to declare no interface type at all (then no "
                    + "interface_number_input requirement can ever be satisfied on this machine).");
        }

        List<SmartInterfaceType> types = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject type = asObject(entry, where + " smart interface #" + index);
            if (!type.has("type")) {
                throw new JsonParseException("Smart interface #" + index + " of " + where + " has no 'type'. The "
                        + "type's name is what a recipe's interface_number_input requirement quotes, so write "
                        + "\"type\": \"mode\".");
            }
            String name = type.get("type").getAsString();
            if (name.isBlank()) {
                throw new JsonParseException("Smart interface #" + index + " of " + where + " has an empty "
                        + "'type'. Give it a name to quote from a recipe, e.g. \"type\": \"mode\".");
            }
            if (!names.add(name)) {
                throw new JsonParseException("Two smart interfaces of " + where + " are both named '" + name
                        + "'. The name is how a requirement finds the value, so a duplicate would make one of "
                        + "them unreachable — rename one of them.");
            }

            float defaultValue = 0.0F;
            if (type.has("default")) {
                JsonElement raw = type.get("default");
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("'default' of smart interface '" + name + "' in " + where
                            + " must be a number. Write the value a freshly formed machine starts from, e.g. "
                            + "\"default\": 0, or remove the field (the default is 0).");
                }
                defaultValue = readFloat(raw, "'default' of smart interface '" + name + "' in " + where);
            }

            int priority = 0;
            if (type.has("priority")) {
                JsonElement raw = type.get("priority");
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("'priority' of smart interface '" + name + "' in " + where
                            + " must be a whole number. Write the number that decides which declared type an "
                            + "undeclared value binds to (the largest wins), e.g. \"priority\": 1000, or remove "
                            + "the field (the default is 0).");
                }
                priority = raw.getAsInt();
            }

            types.add(new SmartInterfaceType(name, defaultValue,
                    readOptionalText(type, "header", name, where),
                    readOptionalText(type, "value", name, where),
                    readOptionalText(type, "footer", name, where),
                    priority,
                    readOptionalText(type, "notequal", name, where)));
            index++;
        }
        return List.copyOf(types);
    }

    /**
     * One of the three optional display strings, or {@code null} when absent. Present but not a string is an
     * error rather than a silently stringified number, because these are translation keys and a number here is
     * always a mistake.
     */
    @javax.annotation.Nullable
    private static String readOptionalText(JsonObject entry, String key, String name, Object where) {
        if (!entry.has(key)) {
            return null;
        }
        JsonElement element = entry.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'" + key + "' of smart interface '" + name + "' in " + where
                    + " must be a string. Write a translation key such as \"gui.example.mode." + key
                    + "\" (or remove the field); a number or an object here can never be printed.");
        }
        return element.getAsString();
    }

    /**
     * A float that has to survive the JSON parser's own number handling. Gson answers {@code getAsFloat} for
     * anything numeric, but a literal {@code true} is not numeric even though {@code getAsFloat} would happily
     * read it as 1 — hence the primitive-and-number test before the read, like {@code readParallelism}'s.
     */
    private static float readFloat(JsonElement element, String where) {
        try {
            return element.getAsFloat();
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new JsonParseException("The " + where + " is not a number that fits a float");
        }
    }

    /**
     * One of the two parallelism integers. Both are non-negative and both are reported rather than clamped when
     * malformed, so a typo in a definition cannot silently disable or unbound a machine.
     */
    private static int readParallelism(JsonObject root, String key, Object where) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be a whole number. Write a "
                    + "non-negative integer such as \"max-parallelism\": 64, or remove the field.");
        }
        int value = element.getAsInt();
        if (value < 0) {
            throw new JsonParseException("'" + key + "' of " + where + " is " + value + ", but a parallelism "
                    + "cannot be negative. The original floored it at 0 (a machine with no parallel controller "
                    + "still runs exactly one copy), so write 0 for \"no built-in parallelism\".");
        }
        return value;
    }

    /**
     * The {@code modifiers} array: the original's {@code BlockArray}-positioned recipe modifiers.
     *
     * <p>Each entry names one position (the original's {@code addModifierWithPattern} also accepted coordinate
     * arrays, which are expanded here the same way {@code parts} are) plus the blocks that may sit there and the
     * modifiers those blocks contribute.
     */
    private static List<MachineModifier> readModifiers(JsonObject root, Map<String, List<String>> variables,
                                                       Object where) {
        JsonElement element = root.get("modifiers");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'modifiers' of " + where + " must be an array of modifier objects. "
                    + "Write \"modifiers\": [ { \"elements\": ..., \"x\": 0, \"y\": 1, \"z\": 0, "
                    + "\"modifier\": { ... } } ], or remove the field.");
        }

        List<MachineModifier> modifiers = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject part = asObject(entry, where + " modifier");
            List<Integer> xs = readCoordinates(part, "x", where);
            List<Integer> ys = readCoordinates(part, "y", where);
            List<Integer> zs = readCoordinates(part, "z", where);
            List<BlockMatcher> accepted = readElements(part, variables, where);

            // The original accepted either a single 'modifier' object or a 'modifiers' array of them; both
            // spellings appear in the wild and both are kept.
            JsonElement rawModifiers = part.has("modifier") ? part.get("modifier")
                    : part.get("modifiers");
            if (rawModifiers == null) {
                throw new JsonParseException("A modifier of " + where + " has neither 'modifier' nor "
                        + "'modifiers'. Write \"modifier\": { \"target\": \"item\", \"io\": \"output\", "
                        + "\"operation\": 1, \"multiplier\": 2.0 }.");
            }
            List<RecipeModifier> parsed = new ArrayList<>();
            if (rawModifiers.isJsonArray()) {
                for (JsonElement single : rawModifiers.getAsJsonArray()) {
                    parsed.add(RecipeModifier.parse(single, where));
                }
                if (parsed.isEmpty()) {
                    throw new JsonParseException("The 'modifiers' array of a modifier in " + where + " is empty");
                }
            } else {
                parsed.add(RecipeModifier.parse(rawModifiers, where));
            }

            String description = part.has("description") ? part.get("description").getAsString() : "";
            boolean any = false;
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        if (x == 0 && y == 0 && z == 0) {
                            // The original's addModifierWithPattern skipped the controller's own position.
                            continue;
                        }
                        modifiers.add(new MachineModifier(new BlockPos(x, y, z), accepted, parsed, description));
                        any = true;
                    }
                }
            }
            if (!any) {
                LOGGER.warn("[{}] A modifier of {} sits at the controller's own position (0,0,0), which the "
                                + "original skipped; ignoring it. Give it the structure position whose block it "
                                + "reacts to, e.g. \"x\": 0, \"y\": 1, \"z\": 0.",
                        ModularMachineryReborn.MOD_ID, where);
            }
        }
        return List.copyOf(modifiers);
    }

    /**
     * Original root fields that the schema recognises but this project does not act on yet. They are reported at
     * load time instead of being silently dropped.
     *
     * <p>{@code modifiers} left this list in 0.19.0 (M6c): structure positions now really do contribute their
     * recipe modifiers. {@code has-factory} / {@code factory-only} left it in 0.22.0 (M6e) together with the two
     * new thread fields. {@code failure-action} left it in 0.25.0 (D17). What remains has no consumer at all yet:
     * {@code color} is only visual, and {@code prefix} / {@code hide-components-when-formed} /
     * {@code controller-bounding-box} / {@code dynamic-patterns} belong to slices that have not been scheduled.
     */
    private static final String[] UNIMPLEMENTED_ROOT_FIELDS = {
            "dynamic-patterns", "color", "prefix",
            "hide-components-when-formed", "controller-bounding-box"
    };

    private static List<BlockMatcher> readElements(JsonObject part, Map<String, List<String>> variables,
                                                   Object where) {
        JsonElement elements = part.get("elements");
        if (elements == null) {
            throw new JsonParseException("A part of " + where + " has no 'elements'");
        }
        List<String> descriptors = new ArrayList<>();
        if (elements.isJsonArray()) {
            for (JsonElement element : elements.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) {
                    throw new JsonParseException("'elements' entries of " + where + " must be strings");
                }
                descriptors.add(element.getAsString());
            }
        } else if (elements.isJsonPrimitive()) {
            descriptors.add(elements.getAsString());
        } else {
            throw new JsonParseException("'elements' of " + where + " must be a string or an array");
        }

        List<BlockMatcher> accepted = new ArrayList<>();
        for (String descriptor : descriptors) {
            if (descriptor.indexOf(':') >= 0) {
                accepted.add(BlockMatcher.parse(descriptor));
                continue;
            }
            List<String> resolved = variables.get(descriptor);
            if (resolved == null) {
                throw new JsonParseException("'" + descriptor + "' in " + where
                        + " is neither a namespaced block id nor a known variable");
            }
            if (resolved.isEmpty()) {
                throw new JsonParseException("Variable '" + descriptor + "' used by " + where + " is empty");
            }
            for (String value : resolved) {
                accepted.add(BlockMatcher.parse(value));
            }
        }
        return accepted;
    }

    private static List<Integer> readCoordinates(JsonObject part, String key, Object where) {
        List<Integer> out = new ArrayList<>(1);
        JsonElement element = part.get(key);
        if (element == null) {
            out.add(0);
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            out.add(element.getAsInt());
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("Coordinate '" + key + "' of " + where
                            + " must contain only numbers");
                }
                out.add(value.getAsInt());
            }
            if (out.isEmpty()) {
                throw new JsonParseException("Coordinate '" + key + "' of " + where + " is an empty array");
            }
            return out;
        }
        throw new JsonParseException("Coordinate '" + key + "' of " + where
                + " must be a number or an array of numbers");
    }

    /**
     * The "expected an object" check, shared with {@link MachineLoader}'s variable-set reader so both name the
     * offending place the same way.
     */
    public static JsonObject asObject(JsonElement element, Object where) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object in " + where);
        }
        return element.getAsJsonObject();
    }

    /** The "expected an array" check; see {@link #asObject(JsonElement, Object)}. */
    public static JsonArray asArray(JsonElement element, Object where) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array in " + where);
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject root, String key, Object where) {
        if (!root.has(key)) {
            throw new JsonParseException("Missing required field '" + key + "' in " + where);
        }
        return root.get(key).getAsString();
    }
}
