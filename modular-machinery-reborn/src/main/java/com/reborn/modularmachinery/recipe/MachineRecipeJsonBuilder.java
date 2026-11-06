package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JSON body of a {@code modular_machinery_reborn:machine} recipe.
 *
 * <p>This class deliberately knows nothing about KubeJS. It only appends the fields that
 * {@link ModRecipeSerializers} and {@link MachineRequirementParser} already understand, so the
 * typed KubeJS API ({@code event.recipes.modular_machinery_reborn.machine(...)}) and the offline
 * verifier can both use it without dragging Rhino into the test classpath. Validation stays in
 * the readers: the builder checks only the things that have no reader-side check (positive
 * counts, a non-blank machine id) and leaves every optional key absent until asked for.
 *
 * <pre>{@code
 * new MachineRecipeJsonBuilder()
 *     .time(200)
 *     .itemInput("minecraft:iron_ore", 2)
 *     .itemOutput("minecraft:iron_ingot", 1)
 *     .energyInput(40)
 *     .catalyst("minecraft:diamond", 1)
 *     .build("alloy_furnace");
 * }</pre>
 */
public final class MachineRecipeJsonBuilder {

    private static final int DEFAULT_RECIPE_TIME = 100;

    private final JsonArray requirements = new JsonArray();
    private final List<String> machines = new ArrayList<>();
    private final JsonArray startCommands = new JsonArray();
    private final JsonArray processingCommands = new JsonArray();
    private final JsonArray finishCommands = new JsonArray();
    private int recipeTime = DEFAULT_RECIPE_TIME;
    private int maxParallelism;
    private int priority;
    private boolean cancelIfPerTickFails;
    private String registryName;
    private JsonObject lastCatalyst;
    private final JsonObject extra = new JsonObject();

    public int lastRequirementIndex() {
        if (requirements.isEmpty()) throw new IllegalStateException("Add a requirement first");
        return requirements.size() - 1;
    }
    public MachineRecipeJsonBuilder requirementOption(String key, JsonElement value) {
        requirements.get(lastRequirementIndex()).getAsJsonObject().add(key, value.deepCopy()); return this;
    }
    public MachineRecipeJsonBuilder requirementOptionIfAbsent(String key, JsonElement value) {
        if (!requirements.get(lastRequirementIndex()).getAsJsonObject().has(key)) requirementOption(key, value);
        return this;
    }
    public MachineRecipeJsonBuilder field(String key, JsonElement value) {
        extra.add(key, value.deepCopy()); return this;
    }

    /**
     * Makes this recipe belong to several machines. The original accepted the {@code machine} field as one
     * string or a non-empty array of strings; this is the typed way to write the array. Calling it replaces any
     * earlier call.
     */
    public MachineRecipeJsonBuilder machines(String... machineIds) {
        this.machines.clear();
        if (machineIds != null) {
            for (String machine : machineIds) {
                this.machines.add(requireId(machine, "machine"));
            }
        }
        return this;
    }

    /** Appends a {@code startCommands} entry that runs when the craft starts. */
    public MachineRecipeJsonBuilder startCommand(String command) {
        return startCommand(command, -1);
    }

    /** Appends a {@code startCommands} entry with an optional interval; a negative interval means every time. */
    public MachineRecipeJsonBuilder startCommand(String command, int interval) {
        this.startCommands.add(commandJson(command, interval));
        return this;
    }

    /** Appends a {@code processingCommands} entry that runs after every successful craft tick. */
    public MachineRecipeJsonBuilder processingCommand(String command) {
        return processingCommand(command, -1);
    }

    /** Appends a {@code processingCommands} entry with an optional interval. */
    public MachineRecipeJsonBuilder processingCommand(String command, int interval) {
        this.processingCommands.add(commandJson(command, interval));
        return this;
    }

    /** Appends a {@code finishCommands} entry that runs when the craft finishes. */
    public MachineRecipeJsonBuilder finishCommand(String command) {
        return finishCommand(command, -1);
    }

    /** Appends a {@code finishCommands} entry with an optional interval. */
    public MachineRecipeJsonBuilder finishCommand(String command, int interval) {
        this.finishCommands.add(commandJson(command, interval));
        return this;
    }

    public MachineRecipeJsonBuilder time(int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("recipe time must be at least 1 tick, got " + ticks);
        }
        this.recipeTime = ticks;
        return this;
    }

    public MachineRecipeJsonBuilder parallelism(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max parallelism must be at least 1, got " + max);
        }
        this.maxParallelism = max;
        return this;
    }

    /**
     * The original's {@code cancelIfPerTickFails}: a failed per-tick check drops this craft instead of applying
     * the machine's failure-action. Only the {@code true} value is written — omitting the field already means
     * {@code false}.
     */
    public MachineRecipeJsonBuilder cancelIfPerTickFails(boolean cancel) {
        this.cancelIfPerTickFails = cancel;
        return this;
    }

    /** Shorthand for {@code cancelIfPerTickFails(true)}. */
    public MachineRecipeJsonBuilder cancelIfPerTickFails() {
        return cancelIfPerTickFails(true);
    }

    /**
     * The original's recipe-level {@code priority}; lower layers are tried first.
     *
     * <p>Any integer is accepted, including negatives — the original wrote whatever number was in the file
     * straight into a {@code TreeMap} key. Only non-zero values are written back, because omitting the field
     * already means {@code 0}.
     */
    public MachineRecipeJsonBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Overrides the recipe registry name. The reader defaults it to the recipe id path, so this is
     * only needed when two recipe files share a path in different namespaces or when the JEI page
     * should show a stable name.
     */
    public MachineRecipeJsonBuilder registryName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("registryName must not be blank");
        }
        this.registryName = name.trim();
        return this;
    }

    /** Appends an already shaped requirement object. The object is copied. */
    public MachineRecipeJsonBuilder requirement(JsonObject requirement) {
        if (requirement == null) {
            throw new IllegalArgumentException("requirement must not be null");
        }
        this.requirements.add(requirement.deepCopy());
        return this;
    }

    public MachineRecipeJsonBuilder itemInput(String item, int amount) {
        JsonObject json = requirement(RequirementTypes.ITEM, IOType.INPUT);
        json.addProperty("item", requireId(item, "item"));
        json.addProperty("amount", requirePositive(amount, "item amount"));
        this.requirements.add(json);
        return this;
    }

    public MachineRecipeJsonBuilder itemOutput(String item, int amount) {
        JsonObject json = requirement(RequirementTypes.ITEM, IOType.OUTPUT);
        json.addProperty("item", requireId(item, "item"));
        json.addProperty("amount", requirePositive(amount, "item amount"));
        this.requirements.add(json);
        return this;
    }

    /**
     * A random item output. {@code minAmount} and {@code maxAmount} are inclusive; {@code chance}
     * is the probability that the output happens at all.
     */
    public MachineRecipeJsonBuilder itemOutput(String item, int minAmount, int maxAmount, float chance) {
        if (minAmount < 1 || maxAmount < minAmount) {
            throw new IllegalArgumentException("item output range must satisfy 1 <= min <= max, got "
                    + minAmount + ".." + maxAmount);
        }
        JsonObject json = requirement(RequirementTypes.ITEM, IOType.OUTPUT);
        json.addProperty("item", requireId(item, "item"));
        json.addProperty("amount", minAmount);
        json.addProperty("minAmount", minAmount);
        json.addProperty("maxAmount", maxAmount);
        addChance(json, chance);
        this.requirements.add(json);
        return this;
    }

    public MachineRecipeJsonBuilder fluidInput(String fluid, int amount) {
        return fluidRequirement(IOType.INPUT, fluid, amount, false);
    }

    public MachineRecipeJsonBuilder fluidInput(String fluid, int amount, boolean perTick) {
        return fluidRequirement(IOType.INPUT, fluid, amount, perTick);
    }

    public MachineRecipeJsonBuilder fluidOutput(String fluid, int amount) {
        return fluidRequirement(IOType.OUTPUT, fluid, amount, false);
    }

    public MachineRecipeJsonBuilder fluidOutput(String fluid, int amount, boolean perTick) {
        return fluidRequirement(IOType.OUTPUT, fluid, amount, perTick);
    }

    private MachineRecipeJsonBuilder fluidRequirement(IOType ioType, String fluid, int amount, boolean perTick) {
        JsonObject json = requirement(RequirementTypes.FLUID, ioType);
        json.addProperty("fluid", requireId(fluid, "fluid"));
        json.addProperty("amount", requirePositive(amount, "fluid amount"));
        if (perTick) {
            json.addProperty("perTick", true);
        }
        this.requirements.add(json);
        return this;
    }

    public MachineRecipeJsonBuilder energyInput(long perTick) {
        return energyRequirement(IOType.INPUT, perTick);
    }

    public MachineRecipeJsonBuilder energyOutput(long perTick) {
        return energyRequirement(IOType.OUTPUT, perTick);
    }

    private MachineRecipeJsonBuilder energyRequirement(IOType ioType, long perTick) {
        if (perTick < 1L) {
            throw new IllegalArgumentException("energy per tick must be at least 1, got " + perTick);
        }
        JsonObject json = requirement(RequirementTypes.ENERGY, ioType);
        json.addProperty("energyPerTick", perTick);
        this.requirements.add(json);
        return this;
    }

    public MachineRecipeJsonBuilder catalyst(String item, int amount) {
        return catalyst(item, amount, 1.0F);
    }

    /**
     * Adds a catalyst group with one entry. Further entries can be appended with
     * {@link #catalystEntry(String, int)}; modifiers and tooltips attach with
     * {@link #catalystModifier(String, String, int, float)} and {@link #catalystTooltip(String)}.
     */
    public MachineRecipeJsonBuilder catalyst(String item, int amount, float chance) {
        JsonObject json = requirement(RequirementTypes.CATALYST, IOType.INPUT);
        JsonArray items = new JsonArray();
        items.add(catalystEntryJson(item, amount));
        json.add("items", items);
        addChance(json, chance);
        this.requirements.add(json);
        this.lastCatalyst = json;
        return this;
    }

    public MachineRecipeJsonBuilder catalystEntry(String item, int amount) {
        JsonObject catalyst = requireCatalyst("catalystEntry");
        catalyst.getAsJsonArray("items").add(catalystEntryJson(item, amount));
        return this;
    }

    public MachineRecipeJsonBuilder catalystChance(float chance) {
        addChance(requireCatalyst("catalystChance"), chance);
        return this;
    }

    /**
     * Adds a modifier to the most recent catalyst. {@code operation} is 0 for additive and 1 for
     * multiplicative; {@code target} is one of item, fluid, energy, duration or {@code *}.
     */
    public MachineRecipeJsonBuilder catalystModifier(String target, String io, int operation, float multiplier) {
        JsonObject catalyst = requireCatalyst("catalystModifier");
        if (operation != 0 && operation != 1) {
            throw new IllegalArgumentException("modifier operation must be 0 (add) or 1 (multiply), got "
                    + operation);
        }
        JsonObject modifier = new JsonObject();
        modifier.addProperty("target", requireId(target, "modifier target"));
        modifier.addProperty("io", requireId(io, "modifier io"));
        modifier.addProperty("operation", operation);
        modifier.addProperty("multiplier", multiplier);
        JsonArray modifiers = catalyst.getAsJsonArray("modifiers");
        if (modifiers == null) {
            modifiers = new JsonArray();
            catalyst.add("modifiers", modifiers);
        }
        modifiers.add(modifier);
        return this;
    }

    public MachineRecipeJsonBuilder catalystTooltip(String text) {
        JsonObject catalyst = requireCatalyst("catalystTooltip");
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("catalyst tooltip must not be empty");
        }
        JsonArray tooltips = catalyst.getAsJsonArray("tooltip");
        if (tooltips == null) {
            tooltips = new JsonArray();
            catalyst.add("tooltip", tooltips);
        }
        tooltips.add(text);
        return this;
    }

    /** Builds the recipe body. The returned tree is independent of the builder. */
    public JsonObject build(String machine) {
        JsonObject json = extra.deepCopy();
        if (this.machines.isEmpty()) {
            json.addProperty("machine", requireId(machine, "machine"));
        } else {
            JsonArray array = new JsonArray();
            for (String id : this.machines) {
                array.add(id);
            }
            json.add("machine", array);
        }
        if (this.registryName != null) {
            json.addProperty("registryName", this.registryName);
        }
        json.addProperty("recipeTime", this.recipeTime);
        if (this.maxParallelism > 0) {
            json.addProperty("max-parallelism", this.maxParallelism);
        }
        if (this.priority != 0) {
            json.addProperty("priority", this.priority);
        }
        if (this.cancelIfPerTickFails) {
            json.addProperty("cancelIfPerTickFails", true);
        }
        if (!this.startCommands.isEmpty()) {
            json.add("startCommands", this.startCommands.deepCopy());
        }
        if (!this.processingCommands.isEmpty()) {
            json.add("processingCommands", this.processingCommands.deepCopy());
        }
        if (!this.finishCommands.isEmpty()) {
            json.add("finishCommands", this.finishCommands.deepCopy());
        }
        json.add("requirements", this.requirements.deepCopy());
        return json;
    }

    /** A string for a plain command, or {@code {command, interval}} when an interval was given. */
    private static JsonElement commandJson(String command, int interval) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String text = command;
        if (interval < 0) {
            return new JsonPrimitive(text);
        }
        JsonObject object = new JsonObject();
        object.addProperty("command", text);
        object.addProperty("interval", interval);
        return object;
    }

    private JsonObject requireCatalyst(String method) {
        if (this.lastCatalyst == null) {
            throw new IllegalStateException(method + "() needs a preceding catalyst(...) call");
        }
        return this.lastCatalyst;
    }

    private static JsonObject requirement(RequirementType type, IOType ioType) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.id().toString());
        json.addProperty("io-type", ioType.name().toLowerCase(java.util.Locale.ROOT));
        return json;
    }

    private static JsonObject catalystEntryJson(String item, int amount) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", requireId(item, "catalyst item"));
        entry.addProperty("amount", requirePositive(amount, "catalyst amount"));
        return entry;
    }

    private static void addChance(JsonObject json, float chance) {
        // NaN fails both comparisons below and would silently delete the field (parsing back as 1), so
        // finiteness has to be checked before the range, and before any existing chance value is touched.
        if (!Float.isFinite(chance)) {
            throw new IllegalArgumentException("chance must be a finite number between 0 and 1, got " + chance);
        }
        if (chance < 0.0F || chance > 1.0F) {
            throw new IllegalArgumentException("chance must be between 0 and 1, got " + chance);
        }
        if (chance < 1.0F) {
            json.addProperty("chance", chance);
        } else {
            // Calling catalystChance(1.0F) after an earlier 0.5 must clear the old value, not silently keep it.
            json.remove("chance");
        }
    }

    private static String requireId(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String what) {
        if (value < 1) {
            throw new IllegalArgumentException(what + " must be at least 1, got " + value);
        }
        return value;
    }
}
