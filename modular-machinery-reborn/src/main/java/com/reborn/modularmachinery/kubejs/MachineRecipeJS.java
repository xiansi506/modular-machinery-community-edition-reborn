package com.reborn.modularmachinery.kubejs;

import com.google.gson.JsonObject;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.recipe.MachineRecipeJsonBuilder;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipeKey;

/**
 * The {@link RecipeJS} behind {@code event.recipes.modular_machinery_reborn.machine(...)}.
 *
 * <p>Two shapes share one argument:
 *
 * <pre>{@code
 * // typed: the builder fills in machine/recipeTime/requirements
 * event.recipes.modular_machinery_reborn.machine('alloy_furnace')
 *     .time(200)
 *     .itemInput('minecraft:iron_ore', 2)
 *     .itemOutput('minecraft:iron_ingot', 1)
 *
 * // raw: the object is the same body a datapack file would contain
 * event.recipes.modular_machinery_reborn.machine({
 *     machine: 'alloy_furnace',
 *     recipeTime: 200,
 *     requirements: [ ... ]
 * })
 * }</pre>
 *
 * <p>Raw recipes retain their complete body as the schema value. KubeJS checks required schema keys in
 * {@code afterLoaded}, including for {@code event.custom(...)}, so leaving that value null rejects an
 * otherwise valid raw recipe before it reaches the serializer.
 * {@link #serialize()} decides which shape the current recipe is from the key's type.
 */
public final class MachineRecipeJS extends RecipeJS {

    public static final RecipeKey<Object> MACHINE = new RecipeKey<>(new MachineTargetComponent(), "machine");

    private static final String RECIPE_TYPE = ModularMachineryReborn.MOD_ID + ":machine";

    /** Same canonical WILL variants as the JSON reader and the requirement constructor. */
    private static final java.util.Set<String> WILL_VARIANTS = java.util.Set.of(
            "default", "corrosive", "destructive", "vengeful", "steadfast");

    /**
     * Normalize and validate a WILL variant at the typed boundary. The single returned value is used
     * both for the pre-validation requirement and for the generated JSON, so the two entrances agree.
     * Null, blank, unknown and padded values are rejected explicitly: no silent default, no trim and
     * no dependency on the process default locale.
     */
    private static String willVariant(String raw) {
        if (raw == null) throw new IllegalArgumentException("will-type must be a non-null string");
        String canonical = raw.toLowerCase(java.util.Locale.ROOT);
        if (!WILL_VARIANTS.contains(canonical)) {
            throw new IllegalArgumentException("Unknown will-type: " + raw
                    + "; expected one of default, corrosive, destructive, vengeful, steadfast");
        }
        return canonical;
    }

    private final MachineRecipeJsonBuilder builder = new MachineRecipeJsonBuilder();
    private final ScriptCallbacksJS callbacks = new ScriptCallbacksJS();
    private final java.util.Map<Integer, com.reborn.modularmachinery.script.ScriptHooks.ItemChecker> checkers = new java.util.LinkedHashMap<>();
    private final java.util.Map<Integer, java.util.List<com.reborn.modularmachinery.script.ScriptHooks.ItemModifier>> itemModifiers = new java.util.LinkedHashMap<>();

    public MachineRecipeJS on(String event, java.util.function.Consumer<LifecycleEventsJS.ControllerEventJS> callback) {
        callbacks.add(event, callback); return this;
    }
    public MachineRecipeJS itemChecker(com.reborn.modularmachinery.script.ScriptHooks.ItemChecker checker) {
        checkers.put(builder.lastRequirementIndex(), checker); return this;
    }
    public MachineRecipeJS itemModifier(com.reborn.modularmachinery.script.ScriptHooks.ItemModifier modifier) {
        itemModifiers.computeIfAbsent(builder.lastRequirementIndex(), ignored -> new java.util.ArrayList<>()).add(modifier); return this;
    }
    public MachineRecipeJS field(String key, Object value) {
        builder.field(key, dev.latvian.mods.kubejs.util.JsonIO.of(value)); return this;
    }
    public MachineRecipeJS requirementOption(String key, Object value) {
        builder.requirementOption(key, dev.latvian.mods.kubejs.util.JsonIO.of(value)); return this;
    }
    public MachineRecipeJS chance(float chance) {
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be in [0,1]");
        return requirementOption("chance", chance);
    }
    public MachineRecipeJS selectorTag(String tag) { return requirementOption("selector-tag", tag); }
    public MachineRecipeJS parallelizeUnaffected(boolean unaffected) { return requirementOption("parallelize-unaffected", unaffected); }
    public MachineRecipeJS nbt(Object nbt) { return requirementOption("nbt", nbt); }
    public MachineRecipeJS previewNBT(Object nbt) {
        builder.requirementOptionIfAbsent("nbt", new JsonObject());
        return requirementOption("nbt-display", nbt);
    }
    public MachineRecipeJS adapter(String id) { return field("adapter", id); }
    public MachineRecipeJS adapterModifiers(Object modifiers) { return field("modifiers", modifiers); }
    public MachineRecipeJS consumeDurability() { return requirementOption("consume-durability", true); }
    public MachineRecipeJS ignoreOutputCheck() { return requirementOption("ignore-output-check", true); }
    public MachineRecipeJS triggerTime(int tick) { return requirementOption("trigger-time", tick); }
    public MachineRecipeJS triggerRepeatable(boolean repeat) { return requirementOption("trigger-repeatable", repeat); }
    public MachineRecipeJS requirementTooltip(String text) { return requirementOption("script-tooltip", text); }
    public MachineRecipeJS tooltip(String... text) {
        com.google.gson.JsonArray lines = new com.google.gson.JsonArray();
        for (String line : text) lines.add(line);
        builder.field("tooltips", lines); return this;
    }
    public MachineRecipeJS gasInput(String gas, long amount) { return gas(gas, amount, "input", false); }
    public MachineRecipeJS gasOutput(String gas, long amount) { return gas(gas, amount, "output", false); }
    public MachineRecipeJS gasPerTick(String gas, long amount, String io) { return gas(gas, amount, io, true); }
    private MachineRecipeJS gas(String gas, long amount, String io, boolean perTick) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) throw new IllegalArgumentException("gas amount must be in 1..2147483647");
        JsonObject req = new JsonObject(); req.addProperty("type", perTick ? "gas_pertick" : "gas"); req.addProperty("io-type", io);
        req.addProperty("gas", gas); req.addProperty("amount", amount); req.addProperty("perTick", perTick);
        builder.requirement(req); return this;
    }
    public MachineRecipeJS manaInput(int amount) { return manaInput(amount,false); }
    public MachineRecipeJS compressedAirInput(double pressure, int air) { return compressedAir(pressure, air, "input", false); }
    public MachineRecipeJS compressedAirOutput(int air) { return compressedAir(0, air, "output", false); }
    public MachineRecipeJS compressedAirPerTick(double pressure, int air, String io) { return compressedAir(pressure, air, io, true); }
    private MachineRecipeJS compressedAir(double pressure, int air, String io, boolean perTick) {
        new com.reborn.modularmachinery.magic.CompressedAirRequirement(com.reborn.modularmachinery.recipe.IOType.valueOf(io.toUpperCase(java.util.Locale.ROOT)),air,pressure,perTick);
        JsonObject req = new JsonObject(); req.addProperty("type",perTick ? "compressed_air_pertick" : "compressed_air");
        req.addProperty("io-type",io); req.addProperty("pressure",pressure); req.addProperty("air",air); return requirement(req);
    }
    public MachineRecipeJS emberInput(double amount) { return emberInput(amount,false); }
    public MachineRecipeJS emberInput(double amount, boolean perTick) { return magic("EMBER","input",amount,"",0,Double.MAX_VALUE,perTick); }
    public MachineRecipeJS emberOutput(double amount) { return emberOutput(amount,false); }
    public MachineRecipeJS emberOutput(double amount, boolean perTick) { return magic("EMBER","output",amount,"",0,Double.MAX_VALUE,perTick); }
    public MachineRecipeJS manaInput(int amount, boolean perTick) { return magic("MANA","input",amount,"",0,Integer.MAX_VALUE,perTick); }
    public MachineRecipeJS manaOutput(int amount) { return manaOutput(amount,false); }
    public MachineRecipeJS manaOutput(int amount, boolean perTick) { return magic("MANA","output",amount,"",0,Integer.MAX_VALUE,perTick); }
    public MachineRecipeJS lifeEssenceInput(int amount) { return lifeEssenceInput(amount,false); }
    public MachineRecipeJS lifeEssenceInput(int amount, boolean perTick) { return magic("LIFEESSENCE","input",amount,"",0,Integer.MAX_VALUE,perTick); }
    public MachineRecipeJS lifeEssenceOutput(int amount) { return lifeEssenceOutput(amount,false); }
    public MachineRecipeJS lifeEssenceOutput(int amount, boolean perTick) { return magic("LIFEESSENCE","output",amount,"",0,Integer.MAX_VALUE,perTick); }
    public MachineRecipeJS auraInput(String type, int amount, int minimum) { return magic("AURA","input",amount,type,minimum,Integer.MAX_VALUE,false); }
    public MachineRecipeJS auraOutput(String type, int amount, int maximum) { return magic("AURA","output",amount,type,Integer.MIN_VALUE,maximum,false); }
    public MachineRecipeJS auraPerTick(String type, int amount, String io, int minimum, int maximum) { return magic("AURA",io,amount,type,minimum,maximum,true); }
    public MachineRecipeJS willInput(String type, double amount, double minimum) { return magic("WILL","input",amount,type,minimum,100,false); }
    public MachineRecipeJS willOutput(String type, double amount, double maximum) { return magic("WILL","output",amount,type,0,maximum,false); }
    public MachineRecipeJS willPerTick(String type, double amount, String io, double minimum, double maximum) { return magic("WILL",io,amount,type,minimum,maximum,true); }
    private MachineRecipeJS magic(String family, String io, double amount, String variant, double min, double max, boolean perTick) {
        var kind = com.reborn.modularmachinery.magic.MagicFamily.valueOf(family);
        if (kind == com.reborn.modularmachinery.magic.MagicFamily.WILL) variant = willVariant(variant);
        new com.reborn.modularmachinery.magic.MagicRequirement(com.reborn.modularmachinery.recipe.IOType.valueOf(io.toUpperCase(java.util.Locale.ROOT)),kind,amount,variant,min,max,perTick);
        JsonObject req = new JsonObject(); req.addProperty("type",kind.path()); req.addProperty("io-type",io); req.addProperty("amount",amount); req.addProperty("perTick",perTick);
        if (kind == com.reborn.modularmachinery.magic.MagicFamily.AURA) { req.addProperty("aura-type",variant); req.addProperty("min-aura",min); req.addProperty("max-aura",max); }
        if (kind == com.reborn.modularmachinery.magic.MagicFamily.WILL) { req.addProperty("will-type",variant); req.addProperty("min",min); req.addProperty("max",max); }
        builder.requirement(req); return this;
    }
    public MachineRecipeJS ingredientArray(Object entries) {
        JsonObject req = new JsonObject(); req.addProperty("type", "ingredient_array_input"); req.addProperty("io-type", "input");
        req.add("items", dev.latvian.mods.kubejs.util.JsonIO.of(entries)); builder.requirement(req); return this;
    }
    public MachineRecipeJS smartInterface(String type, float value) {
        JsonObject req = new JsonObject(); req.addProperty("type", "interface_number_input"); req.addProperty("io-type", "input");
        req.addProperty("interface", type); req.addProperty("value", value); builder.requirement(req); return this;
    }
    public MachineRecipeJS fuelInput(int burnTicks) {
        JsonObject req = new JsonObject(); req.addProperty("type", "item"); req.addProperty("io-type", "input");
        req.addProperty("item", "minecraft:coal"); req.addProperty("amount", burnTicks); req.addProperty("fuel-time", true);
        builder.requirement(req); return this;
    }

    public MachineRecipeJS() {
    }

    @Override
    public void deserialize(boolean merge) {
        if (this.json != null && this.json.has("machine")) setValue(MACHINE, this.json.deepCopy());
    }

    @Override
    public void serialize() {
        JsonObject output;
        if (this.json != null && this.json.has("requirements")) {
            output = this.json.deepCopy();
        } else {
            Object target = getValue(MACHINE);
            if (target instanceof CharSequence machine) {
                output = this.builder.build(machine.toString());
            } else if (target instanceof JsonObject raw) {
                output = raw.deepCopy();
            } else {
                throw new IllegalStateException("A modular_machinery_reborn:machine recipe needs either a "
                        + "machine id (typed API) or a recipe JSON object (raw API).");
            }
        }
        if (!output.has("type")) {
            output.addProperty("type", RECIPE_TYPE);
        }
        this.json = output;
        if (callbacks.isEmpty() && checkers.isEmpty() && itemModifiers.isEmpty()) return;
        net.minecraft.resources.ResourceLocation id = getOrCreateId();
        callbacks.bind(true, id);
        checkers.forEach((index, checker) -> {
            String key = id + "/item/" + index + "/checker";
            com.reborn.modularmachinery.script.ScriptHooks.itemChecker(key, checker);
            output.getAsJsonArray("requirements").get(index).getAsJsonObject().addProperty("script-checker", key);
        });
        itemModifiers.forEach((index, modifiers) -> {
            com.google.gson.JsonArray keys = new com.google.gson.JsonArray();
            for (int n = 0; n < modifiers.size(); n++) {
                String key = id + "/item/" + index + "/modifier/" + n;
                keys.add(key); com.reborn.modularmachinery.script.ScriptHooks.itemModifier(key, modifiers.get(n));
            }
            output.getAsJsonArray("requirements").get(index).getAsJsonObject().add("script-modifiers", keys);
        });
    }

    public MachineRecipeJS time(int ticks) {
        this.builder.time(ticks);
        return this;
    }

    public MachineRecipeJS parallelism(int max) {
        this.builder.parallelism(max);
        return this;
    }

    /** Makes the recipe belong to several machines (the original's {@code machine} array). */
    public MachineRecipeJS machines(String... machineIds) {
        this.builder.machines(machineIds);
        return this;
    }

    public MachineRecipeJS startCommand(String command) {
        this.builder.startCommand(command);
        return this;
    }

    public MachineRecipeJS startCommand(String command, int interval) {
        this.builder.startCommand(command, interval);
        return this;
    }

    public MachineRecipeJS processingCommand(String command) {
        this.builder.processingCommand(command);
        return this;
    }

    public MachineRecipeJS processingCommand(String command, int interval) {
        this.builder.processingCommand(command, interval);
        return this;
    }

    public MachineRecipeJS finishCommand(String command) {
        this.builder.finishCommand(command);
        return this;
    }

    public MachineRecipeJS finishCommand(String command, int interval) {
        this.builder.finishCommand(command, interval);
        return this;
    }

    public MachineRecipeJS cancelIfPerTickFails(boolean cancel) {
        this.builder.cancelIfPerTickFails(cancel);
        return this;
    }

    /** Shorthand for {@code cancelIfPerTickFails(true)}. */
    public MachineRecipeJS cancelIfPerTickFails() {
        return cancelIfPerTickFails(true);
    }

    /**
     * The original's recipe-level {@code priority}; lower layers are tried first, ties broken by input weights.
     *
     * <p>Exposed by name rather than through a generic {@code set} so KubeJS scripts get the same spelling as the
     * JSON field.
     */
    public MachineRecipeJS priority(int priority) {
        this.builder.priority(priority);
        return this;
    }

    public MachineRecipeJS registryName(String name) {
        this.builder.registryName(name);
        return this;
    }

    public MachineRecipeJS requirement(JsonObject requirement) {
        this.builder.requirement(requirement);
        return this;
    }

    public MachineRecipeJS itemInput(String item, int amount) {
        this.builder.itemInput(item, amount);
        return this;
    }

    public MachineRecipeJS itemOutput(String item, int amount) {
        this.builder.itemOutput(item, amount);
        return this;
    }

    public MachineRecipeJS itemOutput(String item, int minAmount, int maxAmount, float chance) {
        this.builder.itemOutput(item, minAmount, maxAmount, chance);
        return this;
    }

    public MachineRecipeJS fluidInput(String fluid, int amount) {
        this.builder.fluidInput(fluid, amount);
        return this;
    }

    public MachineRecipeJS fluidInput(String fluid, int amount, boolean perTick) {
        this.builder.fluidInput(fluid, amount, perTick);
        return this;
    }

    public MachineRecipeJS fluidOutput(String fluid, int amount) {
        this.builder.fluidOutput(fluid, amount);
        return this;
    }

    public MachineRecipeJS fluidOutput(String fluid, int amount, boolean perTick) {
        this.builder.fluidOutput(fluid, amount, perTick);
        return this;
    }

    public MachineRecipeJS energyInput(long perTick) {
        this.builder.energyInput(perTick);
        return this;
    }

    public MachineRecipeJS energyOutput(long perTick) {
        this.builder.energyOutput(perTick);
        return this;
    }

    public MachineRecipeJS catalyst(String item, int amount) {
        this.builder.catalyst(item, amount);
        return this;
    }

    public MachineRecipeJS catalyst(String item, int amount, float chance) {
        this.builder.catalyst(item, amount, chance);
        return this;
    }

    public MachineRecipeJS catalystEntry(String item, int amount) {
        this.builder.catalystEntry(item, amount);
        return this;
    }

    public MachineRecipeJS catalystChance(float chance) {
        this.builder.catalystChance(chance);
        return this;
    }

    public MachineRecipeJS catalystModifier(String target, String io, int operation, float multiplier) {
        this.builder.catalystModifier(target, io, operation, multiplier);
        return this;
    }

    public MachineRecipeJS catalystTooltip(String text) {
        this.builder.catalystTooltip(text);
        return this;
    }
}
