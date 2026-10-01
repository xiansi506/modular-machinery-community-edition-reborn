package com.reborn.modularmachinery.upgrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.MachineRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads upgrade declarations from two places, merged the way machine definitions are:
 *
 * <ul>
 *   <li>the data pack directory {@code data/<namespace>/upgrade/};</li>
 *   <li>the author's directory {@code config/modular_machinery_reborn/upgrade/}, which wins on a name clash.</li>
 * </ul>
 *
 * <p>Two kinds of file live there, told apart by the file name — the same trick
 * {@code MachineLoader} uses for {@code *.var.json}:
 *
 * <ul>
 *   <li>{@code <name>.json} — an upgrade <b>type</b>: id, display name, level, stack limit, whether it is
 *       dynamic, which machines accept it, and its tooltip lines.</li>
 *   <li>{@code <name>.item.json} — an <b>item mapping</b>: which item carries the upgrade. The original had no
 *       file for this; CraftTweaker's {@code MachineUpgradeHelper.registerSupportedItem} filled the same table
 *       at runtime, and since this project has no CraftTweaker bridge, a file is the only way a pack author can
 *       declare it.</li>
 * </ul>
 *
 * <p>Types are read in a first pass and item mappings in a second, so a mapping may name a type declared in a
 * different file — or, failing that, a type left over from an earlier load, which is what keeps an item
 * mapping usable while its type file is being edited.
 *
 * <p>Failing files are logged and skipped rather than aborting the reload, matching {@code MachineLoader}.
 */
public final class UpgradeLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "upgrade";
    /** The suffix that marks an item mapping: {@code <name>.item.json}. */
    private static final String ITEM_SUFFIX = ".item";

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    public UpgradeLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // The config directory is merged last so an author's file wins over a data pack file with the same name.
        Map<ResourceLocation, JsonElement> all = new LinkedHashMap<>(files);
        all.putAll(UpgradeDirectory.readAll());

        // Pass 1: types, seeded from the previous load and overwritten by whatever this load declares. Seeding
        // is deliberate: it lets one pack declare an upgrade and another pack map an item to it, and it means
        // editing a mapping while its type file is being edited does not fail. Marking what this load produced
        // keeps a plain reload from being mistaken for two files colliding.
        Map<ResourceLocation, UpgradeType> loadedTypes = new LinkedHashMap<>(UpgradeRegistry.all());
        List<String> typeFailures = new ArrayList<>();
        Set<ResourceLocation> seenThisLoad = new LinkedHashSet<>();
        int declarations = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            if (isItemFile(entry.getKey())) {
                continue;
            }
            declarations++;
            try {
                UpgradeType type = readType(entry.getKey(), entry.getValue());
                if (!seenThisLoad.add(type.id())) {
                    LOGGER.warn("[{}] Two files both declare the upgrade '{}'; {} wins. Give one of them a "
                                    + "different \"name\".",
                            ModularMachineryReborn.MOD_ID, type.id(), entry.getKey());
                }
                loadedTypes.put(type.id(), type);
            } catch (Exception exception) {
                typeFailures.add(entry.getKey().toString());
                LOGGER.error("[{}] Failed to load upgrade declaration {}", ModularMachineryReborn.MOD_ID,
                        entry.getKey(), exception);
            }
        }

        // Pass 2: item mappings. Rebuilt from scratch rather than merged into the previous load, because an item
        // mapping that names an item that no longer exists, or simply disappeared with its pack, must not
        // survive a reload — that is what "the registry describes what is loaded" means. Types are the one thing
        // that does carry over (see pass 1), so a mapping may reference an upgrade another pack declared.
        Map<ResourceLocation, UpgradeType> typesNow = Map.copyOf(loadedTypes);
        Map<Item, UpgradeTarget.Targets> loadedTargets = new LinkedHashMap<>();
        Map<Item, List<UpgradeTarget>> byItem = new LinkedHashMap<>();
        List<String> itemFailures = new ArrayList<>();
        int mappings = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            if (!isItemFile(entry.getKey())) {
                continue;
            }
            mappings++;
            try {
                Mapping mapping = readMapping(entry.getKey(), entry.getValue(), typesNow);
                List<UpgradeTarget> merged = byItem.computeIfAbsent(mapping.item(), key -> new ArrayList<>());
                // Two declarations for the same (item, upgrade) pair would be a duplicate; the later file wins,
                // which is how the config directory wins over a data pack file that already held the same name.
                merged.removeIf(target -> target.upgrade().id().equals(mapping.target().upgrade().id()));
                merged.add(mapping.target());
            } catch (Exception exception) {
                itemFailures.add(entry.getKey().toString());
                LOGGER.error("[{}] Failed to load upgrade item mapping {}", ModularMachineryReborn.MOD_ID,
                        entry.getKey(), exception);
            }
        }
        for (Map.Entry<Item, List<UpgradeTarget>> entry : byItem.entrySet()) {
            loadedTargets.put(entry.getKey(), new UpgradeTarget.Targets(entry.getValue()));
        }

        UpgradeRegistry.replace(loadedTypes, loadedTargets);

        LOGGER.info("[{}] Loaded {} upgrade declaration(s) and {} item mapping(s){}",
                ModularMachineryReborn.MOD_ID, loadedTypes.size(), loadedTargets.size(),
                (typeFailures.isEmpty() && itemFailures.isEmpty()) ? ""
                        : ", failed: " + typeFailures + itemFailures);
        for (UpgradeType type : loadedTypes.values()) {
            LOGGER.info("[{}]   {} -> maxStack {}, {}, {}{}",
                    ModularMachineryReborn.MOD_ID, type.id(), type.maxStackSize(),
                    type.dynamic() ? "dynamic" : "fixed",
                    type.isRestricted()
                            ? "compatible with " + (type.compatibleMachines().isEmpty()
                                    ? "all except " + type.incompatibleMachines()
                                    : type.compatibleMachines().toString())
                            : "compatible with every machine",
                    type.hasModifiers()
                            ? ", " + type.modifiers().size() + " modifier(s)"
                                    + (type.stackable() ? ", applied per item in the stack" : ", stack counts once")
                            : ", no recipe modifier (a carrier with no effect yet)");
        }
        for (Map.Entry<Item, UpgradeTarget.Targets> entry : loadedTargets.entrySet()) {
            LOGGER.info("[{}]   item {} carries {}", ModularMachineryReborn.MOD_ID,
                    ForgeRegistries.ITEMS.getKey(entry.getKey()), entry.getValue().upgrades());
        }

        // A declaration that names a machine nothing defines is not an error — the pack holding that machine
        // may simply not be installed — but it is worth saying, because such an upgrade can never be used.
        List<ResourceLocation> missing = UpgradeRegistry.unresolvedMachines(
                machine -> MachineRegistry.byId(machine).isPresent());
        if (!missing.isEmpty()) {
            LOGGER.warn("[{}] {} upgrade declaration(s) name machine(s) that no loaded definition provides, so "
                            + "those upgrades can never be used there: {}. Check the spelling, or install the "
                            + "data pack that defines those machines.",
                    ModularMachineryReborn.MOD_ID, missing.size(), missing);
        }

        if (declarations == 0 && mappings == 0) {
            LOGGER.info("[{}] No upgrade declarations found. Add data/<namespace>/{}/<name>.json to a data "
                            + "pack, or {}/{}/<name>.json to this instance's config directory; see "
                            + "example-datapack/ for a worked example.",
                    ModularMachineryReborn.MOD_ID, DIRECTORY, ModularMachineryReborn.MOD_ID, DIRECTORY);
        }
    }

    /** {@code <name>.item.json} declares an item mapping; anything else in the directory declares a type. */
    private static boolean isItemFile(ResourceLocation id) {
        return id.getPath().endsWith(ITEM_SUFFIX);
    }

    // ------------------------------------------------------------------- types

    private static UpgradeType readType(ResourceLocation fileId, JsonElement element) {
        JsonObject root = asObject(element, fileId);

        String rawName = requireString(root, "name", fileId);
        ResourceLocation id = parseName(rawName, fileId, "name");
        String localizedName = root.has("localizedname")
                ? root.get("localizedname").getAsString()
                : id.getPath();

        float level = 0.0F;
        if (root.has("level")) {
            JsonElement raw = root.get("level");
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("In " + fileId + ": 'level' must be a number (a float, e.g. 1.5)."
                        + " It mirrors the original's UpgradeType.level, which the original never evaluated.");
            }
            level = raw.getAsFloat();
        }

        int maxStack = 1;
        if (root.has("max-stack")) {
            JsonElement raw = root.get("max-stack");
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("In " + fileId + ": 'max-stack' must be a whole number of at least "
                        + "1, e.g. 16. Leave the field out for a non-stackable upgrade.");
            }
            maxStack = raw.getAsInt();
            if (maxStack < 1) {
                throw new JsonParseException("In " + fileId + ": 'max-stack' is " + maxStack
                        + ", but it must be at least 1. Leave the field out for a non-stackable upgrade.");
            }
        }

        boolean dynamic = false;
        if (root.has("dynamic")) {
            JsonElement raw = root.get("dynamic");
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("In " + fileId + ": 'dynamic' must be either true or false. true "
                        + "means each copy of the carrier item keeps its own NBT (the original's "
                        + "DynamicMachineUpgrade); false keeps all state on the upgrade bus.");
            }
            dynamic = raw.getAsBoolean();
        }

        Set<ResourceLocation> compatible = readMachineList(root, "compatible-machines", fileId);
        Set<ResourceLocation> incompatible = readMachineList(root, "incompatible-machines", fileId);
        if (!compatible.isEmpty() && !incompatible.isEmpty()) {
            throw new JsonParseException("In " + fileId + ": 'compatible-machines' and 'incompatible-machines' "
                    + "are both set. They are a whitelist and a blacklist and cannot both apply — keep "
                    + "'compatible-machines' to allow only the machines you list, or keep "
                    + "'incompatible-machines' to allow every machine except the ones you list, and delete the "
                    + "other field.");
        }

        List<String> descriptions = new ArrayList<>();
        if (root.has("descriptions")) {
            for (JsonElement entry : asArray(root.get("descriptions"), fileId + " 'descriptions'")) {
                if (!entry.isJsonPrimitive()) {
                    throw new JsonParseException("In " + fileId + ": every entry of 'descriptions' must be a "
                            + "string. Write the tooltip line plainly, or as a translation key like "
                            + "\"mypack.upgrade.example.tooltip\".");
                }
                descriptions.add(entry.getAsString());
            }
        }

        // M6b: what the upgrade actually does. The original had no declaration for this — a CraftTweaker script
        // attached a tick handler that called controller.addModifier(...) (MachineUpgradeBuilder.java:138-159).
        // The shape of one entry is the machine definition's "modifier" object, verbatim, so an author who has
        // written a machine modifier already knows how to write an upgrade modifier.
        List<com.reborn.modularmachinery.recipe.RecipeModifier> modifiers = new ArrayList<>();
        if (root.has("modifiers")) {
            for (JsonElement entry : asArray(root.get("modifiers"), fileId + " 'modifiers'")) {
                modifiers.add(com.reborn.modularmachinery.recipe.RecipeModifier.parse(entry,
                        fileId + " 'modifiers'"));
            }
        }

        // M6b: the original's `stackAble`. Absent means false, which is the original's own default: it only
        // reacted to stacking when the script asked for it.
        boolean stackable = false;
        if (root.has("stackable")) {
            JsonElement raw = root.get("stackable");
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("In " + fileId + ": 'stackable' must be either true or false. true "
                        + "means a stack of n carrier items applies this upgrade's modifiers n times; false "
                        + "means the stack counts once.");
            }
            stackable = raw.getAsBoolean();
        }

        warnUnknownFields(root, fileId, KNOWN_TYPE_FIELDS);
        return new UpgradeType(id, localizedName, level, maxStack, dynamic, compatible, incompatible,
                descriptions, modifiers, stackable);
    }

    private static Set<ResourceLocation> readMachineList(JsonObject root, String key,
                                                         ResourceLocation fileId) {
        if (!root.has(key)) {
            return Set.of();
        }
        Set<ResourceLocation> machines = new LinkedHashSet<>();
        for (JsonElement entry : asArray(root.get(key), fileId + " '" + key + "'")) {
            if (!entry.isJsonPrimitive()) {
                throw new JsonParseException("In " + fileId + ": every entry of '" + key + "' must be a machine "
                        + "registry name string, e.g. \"modular_machinery_reborn:alloy_furnace\".");
            }
            String raw = entry.getAsString();
            ResourceLocation machine = parseName(raw, fileId, key);
            machines.add(machine);
        }
        return machines;
    }

    private static final String[] KNOWN_TYPE_FIELDS = {
            "name", "localizedname", "level", "max-stack", "dynamic",
            "compatible-machines", "incompatible-machines", "descriptions",
            "modifiers", "stackable"
    };

    // --------------------------------------------------------------- item mapping

    /** A mapping resolved to a concrete item and upgrade. */
    private record Mapping(Item item, UpgradeTarget target) {
    }

    private static Mapping readMapping(ResourceLocation fileId, JsonElement element,
                                       Map<ResourceLocation, UpgradeType> types) {
        JsonObject root = asObject(element, fileId);
        warnUnknownFields(root, fileId, KNOWN_MAPPING_FIELDS);

        String rawUpgrade = requireString(root, "upgrade", fileId);
        ResourceLocation upgradeId = parseName(rawUpgrade, fileId, "upgrade");
        UpgradeType upgrade = types.get(upgradeId);
        if (upgrade == null) {
            throw new JsonParseException("In " + fileId + ": no upgrade named '" + rawUpgrade + "' is declared. "
                    + "Declare it with data/<namespace>/{}/" + upgradeId.getPath() + ".json (or "
                    + "{}/{}/" + upgradeId.getPath() + ".json), giving it a \"name\": \"" + rawUpgrade
                    + "\" field, or point 'upgrade' at one that already exists.");
        }

        String rawItem = requireString(root, "item", fileId);
        ResourceLocation itemId = ResourceLocation.tryParse(rawItem);
        Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            throw new JsonParseException("In " + fileId + ": '" + rawItem + "' is not a registered item. Use an "
                    + "item registry name such as \"minecraft:golden_axe\", and make sure the mod that adds it "
                    + "is installed — an upgrade can only be attached to an item this game knows about.");
        }

        if (!upgrade.dynamic()) {
            return new Mapping(item, new UpgradeTarget.Fixed(item, upgrade));
        }
        return new Mapping(item, new UpgradeTarget.Dynamic(item, upgrade));
    }

    private static final String[] KNOWN_MAPPING_FIELDS = { "upgrade", "item" };

    // ------------------------------------------------------------------- helpers

    /**
     * Resolves a name written by hand. A bare path is read in this mod's namespace, the same rule
     * {@code MachineLoader} applies to {@code registryname} and {@code MachineRecipe} to {@code machine}.
     */
    private static ResourceLocation parseName(String raw, ResourceLocation fileId, String field) {
        ResourceLocation id = raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + raw);
        if (id == null) {
            throw new JsonParseException("In " + fileId + ": '" + field + "' is '" + raw
                    + "', which is not a valid resource location. Write it as \"namespace:path\" with lower-case "
                    + "letters, digits, '_', '-' or '.' on both sides, or as a bare path to use this mod's "
                    + "namespace.");
        }
        return id;
    }

    /** Reports fields the schema does not know, so a typo in a field name does not pass silently. */
    private static void warnUnknownFields(JsonObject root, ResourceLocation fileId, String[] known) {
        List<String> unknown = new ArrayList<>();
        for (String key : root.keySet()) {
            boolean found = false;
            for (String candidate : known) {
                if (candidate.equals(key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            LOGGER.warn("[{}] {} has field(s) this mod does not know, which are ignored: {}. Known fields are: "
                            + "{}. Check for a typo — every field name is lower-case and hyphenated.",
                    ModularMachineryReborn.MOD_ID, fileId, unknown, String.join(", ", known));
        }
    }

    private static JsonObject asObject(JsonElement element, Object where) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object in " + where);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray asArray(JsonElement element, Object where) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array in " + where);
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject root, String key, Object where) {
        if (!root.has(key)) {
            throw new JsonParseException("Missing required field '" + key + "' in " + where);
        }
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'" + key + "' in " + where + " must be a string");
        }
        return element.getAsString();
    }
}
