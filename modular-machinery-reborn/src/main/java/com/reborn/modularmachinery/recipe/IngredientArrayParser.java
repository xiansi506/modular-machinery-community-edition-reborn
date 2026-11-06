package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The JSON reader for {@code ingredient_array_input}.
 *
 * <h2>Why this is not a private method of the serializer</h2>
 *
 * <p>The same reason {@link InterfaceNumberInputParser} is not: {@code ModRecipeSerializers} holds a
 * {@code DeferredRegister}, whose static initialiser touches Forge's {@code ObjectHolderRegistry} and therefore
 * {@code net.minecraftforge.fml.common.Mod} — a class that only exists inside FML's own class loader. Nothing
 * here touches a registry holder or a level; it is a pure {@code JsonObject} → requirement function, so the
 * offline harness drives the <b>production</b> parser rather than a copy of it.
 *
 * <h2>The schema, and where each field is read from</h2>
 *
 * <p>The original's reader is {@code RequirementTypeIngredientArray#createRequirement}
 * ({@code :45-143}), and its own example is the contract:
 *
 * <pre>{@code
 * { "type": "modularmachinery:ingredient_array_input", "io-type": "input",
 *   "items": [ { "item": "contenttweaker:programming_circuit_a", "amount": 2 },
 *              { "item": "contenttweaker:programming_circuit_b", "amount": 2 } ],
 *   "chance": 0.5 }
 * }</pre>
 *
 * <p>One property of that reader is easy to get wrong: <b>the original read {@code amount} and {@code chance}
 * off the requirement, not off the entry.</b> Its loops read {@code jsonObject.has("amount")} ({@code :79}) and
 * {@code jsonObject.has("chance")} ({@code :105}) while iterating {@code items}, so every entry received the same
 * pair; a per-entry {@code amount} was ignored. This port keeps the requirement-level {@code amount} as the
 * fallback but honours an entry's own {@code amount} when one is written (D19, below), because the original's own
 * documented example writes it inside every entry. A per-entry {@code chance} or {@code nbt} is still rejected
 * instead of silently ignored, and the message says where it belongs.
 *
 * <p>The fields the original read but this port does not carry — {@code minAmount}/{@code maxAmount} on the
 * requirement — are refused by name rather than ignored, the same rule {@link ModRecipeSerializers} applies to
 * an unknown requirement kind. {@code nbt} <b>is</b> carried: the original read one template off the requirement
 * and copied it onto every entry ({@code RequirementTypeIngredientArray:115-125}), and so does this reader.
 */
public final class IngredientArrayParser {

    /** The keys the original's reader understood at the requirement level. */
    private static final Set<String> REQUIREMENT_KEYS = Set.of("items", "amount", "chance", "nbt");
    /**
     * The keys an entry may carry.
     *
     * <p>{@code amount} is one, and that is a <b>deliberate divergence</b> recorded as D19. The original's reader
     * read {@code amount} from the requirement object ({@code :79-84}) while its own documented example wrote it
     * inside every entry ({@code :26-42, "amount": 2}), so in the original a per-entry {@code amount} is
     * silently ignored and the entry only ever consumes 1. This port reads the entry's own {@code amount} when
     * one is written and falls back to the requirement-level one, which makes the original's own example mean
     * what it says. Refusing it instead would reject the original's documented form.
     */
    private static final Set<String> ENTRY_KEYS = Set.of("item", "amount");
    /**
     * Fields the original read from the requirement, which must not be written inside an entry.
     *
     * <p>{@code chance} is here and not in {@link #ENTRY_KEYS} because no per-entry chance was ever implemented:
     * the original rolled the requirement's single chance once for the whole settlement
     * ({@code startCrafting}, {@code :161-166}), so a per-entry value would have to <i>become</i> a feature. The
     * original's JEI tooltip labelled the entries "weights" ({@code tooltip.machinery.ingredient_array_output
     * .weight}), which hints at intent the code never carried — and this port does not invent behaviour.
     */
    private static final Set<String> MISPLACED_IN_ENTRY = Set.of("chance", "nbt");

    private IngredientArrayParser() {
    }

    /**
     * Reads one {@code ingredient_array_input}.
     *
     * @param ioType the requirement's own {@code io-type}, which this reader insists is {@code input}
     */
    public static IngredientArrayRequirement parse(JsonObject json, IOType ioType, Object where) {
        if (ioType != IOType.INPUT) {
            throw new JsonParseException("An ingredient_array_input in " + where + " is written with "
                    + "\"io-type\": \"output\", but an array is a group of alternative <inputs> — the original's "
                    + "reader always built an input (RequirementIngredientArray's IOType.INPUT constructor, "
                    + ":48-52). Write \"io-type\": \"input\"; for several possible outputs, list one "
                    + "\"modularmachinery:item\" output per stack.");
        }

        rejectUnknownKeys(json, REQUIREMENT_KEYS, where);

        List<IngredientArrayEntry> entries = readEntries(json, where, "ingredient_array_input");

        float chance = readChance(json, where);
        if (chance < 1.0F) {
            List<IngredientArrayEntry> withChance = new ArrayList<>(entries.size());
            for (IngredientArrayEntry entry : entries) {
                withChance.add(entry.withChance(chance));
            }
            entries = withChance;
        }
        return new IngredientArrayRequirement(entries, chance, readNbt(json, where));
    }

    /**
     * Reads the entries of an {@code items} array, sharing the entry grammar with {@code catalyst}.
     *
     * <p>The caller owns the requirement-level key set: {@code ingredient_array_input} allows
     * {@code items/amount/chance/nbt}, while {@code catalyst} additionally allows {@code modifiers} and
     * {@code tooltip}. {@code amount} is read here because both types put it next to {@code items}.
     */
    static List<IngredientArrayEntry> readEntries(JsonObject json, Object where, String typeName) {
        JsonElement rawItems = json.get("items");
        if (rawItems == null || !rawItems.isJsonArray()) {
            throw new JsonParseException("Missing or malformed 'items' in " + where + ". A "
                    + typeName + " needs \"items\" as an array of {\"item\": \"namespace:path\"} "
                    + "objects, e.g. \"items\": [{\"item\": \"minecraft:diamond\"}, "
                    + "{\"item\": \"#forge:gems/emerald\"}]. 'amount' and 'chance', if you want them, go next to "
                    + "'items' and apply to the whole group.");
        }
        JsonArray items = rawItems.getAsJsonArray();
        if (items.isEmpty()) {
            throw new JsonParseException("'items' in " + where + " is empty. A " + typeName + " needs "
                    + "at least one entry — the group's members are the items it may consume one of.");
        }

        int requirementAmount = readAmount(json, where);
        List<IngredientArrayEntry> entries = new ArrayList<>(items.size());
        for (JsonElement element : items) {
            entries.add(readEntry(element, requirementAmount, where));
        }
        return preferPlainItems(entries);
    }

    /**
     * Reads the requirement-level {@code nbt}, the one the original copied onto every entry.
     *
     * <p>Like {@code RequirementTypeItem:95-119}, the value has to be a JSON compound; everything inside it
     * follows the same quoted-string rules as an item requirement's {@code nbt}.
     */
    static NbtPredicate readNbt(JsonObject json, Object where) {
        JsonElement raw = json.get("nbt");
        if (raw == null) {
            return null;
        }
        if (!raw.isJsonObject()) {
            throw new JsonParseException("The ComponentType 'nbt' expects a json compound that defines the NBT "
                    + "tag! (in " + where + ")");
        }
        return NbtPredicate.parse(raw, where);
    }

    // ------------------------------------------------------------------ the pieces

    private static IngredientArrayEntry readEntry(JsonElement element, int requirementAmount, Object where) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("An entry of the 'items' array in " + where + " is not a JSON object. "
                    + "Each entry is written as {\"item\": \"namespace:path\"}.");
        }
        JsonObject entry = element.getAsJsonObject();
        rejectMisplaced(entry, where);
        rejectUnknownKeys(entry, ENTRY_KEYS, where);
        JsonElement rawItem = entry.get("item");
        if (rawItem == null) {
            throw new JsonParseException("Missing 'item' in an entry of the 'items' array in " + where
                    + ". Write the item the group may consume, e.g. \"item\": \"minecraft:diamond\", or a tag, "
                    + "e.g. \"item\": \"#forge:gems/diamond\".");
        }
        if (!rawItem.isJsonPrimitive() || !rawItem.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'item' in an entry of the 'items' array in " + where + " must be a "
                    + "string. Write the item id, e.g. \"item\": \"minecraft:diamond\".");
        }
        String raw = rawItem.getAsString();
        if (raw.isBlank()) {
            throw new JsonParseException("'item' in an entry of the 'items' array in " + where + " is empty. "
                    + "Write the item id, e.g. \"item\": \"minecraft:diamond\".");
        }

        String value = raw.trim();
        boolean tagged = value.startsWith("#") || value.startsWith("ore:");
        int amount = readEntryAmount(entry, requirementAmount, where);
        // Own the parse so the shared item/tag/legacy-ore rules live in exactly one place; an unknown id or an
        // unsupported legacy ore name throws out of here with LegacyIngredients' own message. The entry then
        // carries that parsed Ingredient, so it can never re-resolve the reference differently.
        Ingredient ingredient = LegacyIngredients.parse(raw, String.valueOf(where));
        return tagged
                ? IngredientArrayEntry.ofTag(ingredient, value, amount, 1.0F)
                : IngredientArrayEntry.ofItem(LegacyIngredients.firstStack(ingredient), amount, 1.0F);
    }

    /**
     * The amount one copy of this entry consumes: its own when written, otherwise the requirement's.
     *
     * <p>See {@link #ENTRY_KEYS} for why the entry's own value wins — the original's documented example relied
     * on it and the original's code ignored it.
     */
    private static int readEntryAmount(JsonObject entry, int requirementAmount, Object where) {
        JsonElement element = entry.get("amount");
        if (element == null) {
            return requirementAmount;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("An entry's 'amount' in " + where + " must be a number. Write how many "
                    + "of this item one copy of the craft consumes, e.g. \"amount\": 2.");
        }
        int amount = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 1, IngredientArrayRequirement.MAX_AMOUNT, where + " entry.amount");
        if (amount < 1) {
            throw new JsonParseException("An entry's 'amount' in " + where + " is " + amount + ", but every copy "
                    + "has to consume at least 1. Write a whole number of at least 1.");
        }
        if (amount > IngredientArrayRequirement.MAX_AMOUNT) {
            throw new JsonParseException("An entry's 'amount' in " + where + " is " + amount + ", above the "
                    + IngredientArrayRequirement.MAX_AMOUNT + " an item stack can hold. Write "
                    + IngredientArrayRequirement.MAX_AMOUNT + " or less.");
        }
        return amount;
    }

    /**
     * Concrete items first, tags last, both keeping their relative order.
     *
     * <p>Consumption is greedy in array order — the original's own loop order ({@code consumeAllItems} walks
     * {@code ingredients} front to back, {@code RequirementIngredientArray:234}) — so a tag placed first would
     * swallow a copy that an explicit item later in the list was named for. The original had no such ordering
     * rule; ordering the group so the specific members win is this port's one deliberate refinement, and it only
     * changes which of two <b>satisfiable</b> entries pays, never whether the craft can run.
     */
    private static List<IngredientArrayEntry> preferPlainItems(List<IngredientArrayEntry> entries) {
        List<IngredientArrayEntry> ordered = new ArrayList<>(entries.size());
        for (IngredientArrayEntry entry : entries) {
            if (!entry.isTag()) {
                ordered.add(entry);
            }
        }
        for (IngredientArrayEntry entry : entries) {
            if (entry.isTag()) {
                ordered.add(entry);
            }
        }
        return ordered;
    }

    static int readAmount(JsonObject json, Object where) {
        JsonElement element = json.get("amount");
        if (element == null) {
            return 1;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'amount' in " + where + " must be a number. Write how many of the "
                    + "chosen item each copy of the craft consumes, e.g. \"amount\": 2. It applies to the whole "
                    + "group, which is where the original read it from.");
        }
        int amount = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 1, IngredientArrayRequirement.MAX_AMOUNT, where + " amount");
        if (amount < 1) {
            throw new JsonParseException("'amount' in " + where + " is " + amount + ", but every copy has to "
                    + "consume at least 1. Write a whole number of at least 1, e.g. \"amount\": 2.");
        }
        if (amount > IngredientArrayRequirement.MAX_AMOUNT) {
            throw new JsonParseException("'amount' in " + where + " is " + amount + ", above the "
                    + IngredientArrayRequirement.MAX_AMOUNT + " an item stack can hold. The original clamped it "
                    + "silently (MathHelper.clamp(amount, 1, 64)); write " + IngredientArrayRequirement.MAX_AMOUNT
                    + " or less, and list more entries if one group has to consume more.");
        }
        return amount;
    }

    static float readChance(JsonObject json, Object where) {
        JsonElement element = json.get("chance");
        if (element == null) {
            return 1.0F;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'chance' in " + where + " must be a number between 0 and 1. Write the "
                    + "probability that the group is consumed, e.g. \"chance\": 0.5.");
        }
        float chance = com.reborn.modularmachinery.util.JsonNumbers.finiteFloat(element, where + " chance");
        if (chance < 0.0F || chance > 1.0F) {
            throw new JsonParseException("'chance' in " + where + " is " + chance + ", but it has to be between "
                    + "0 and 1. The original silently ignored a value outside that range "
                    + "(RequirementTypeIngredientArray:110-112); write a fraction such as 0.5.");
        }
        return chance;
    }

    /**
     * Refuses {@code chance} or {@code nbt} written <b>inside an entry</b>.
     *
     * <p>{@code chance} is the trap this closes: the original read a single chance off the requirement
     * ({@code :105}) and rolled it once for the whole settlement ({@code :161-166}), so a per-entry value was
     * silently ignored — and the original's own example did exactly that. Reporting it as an unknown key would be
     * wrong (it is a known field in the wrong place), and honouring it would be inventing a feature.
     */
    private static void rejectMisplaced(JsonObject entry, Object where) {
        Set<String> misplaced = new LinkedHashSet<>();
        for (String key : entry.keySet()) {
            if (MISPLACED_IN_ENTRY.contains(key.toLowerCase(Locale.ROOT))) {
                misplaced.add(key);
            }
        }
        if (!misplaced.isEmpty()) {
            throw new JsonParseException("Field" + (misplaced.size() == 1 ? " " : "s ") + misplaced
                    + " belongs next to 'items', not inside an entry, in " + where + ". The original read "
                    + "'chance' and 'nbt' off the requirement itself (RequirementTypeIngredientArray:105/115), "
                    + "so a value written inside an entry was ignored; write it once next to 'items', where this "
                    + "reader now honours both. (An entry's own 'amount' is honoured here — see D19.)");
        }
    }

    /** Refuses a key this reader does not understand, naming the ones it does. */
    private static void rejectUnknownKeys(JsonObject json, Set<String> allowed, Object where) {
        rejectUnknownKeys(json, allowed, where, "An ingredient_array_input understands 'items' (required), "
                + "'amount', 'chance' and 'nbt' (all applying to the whole group). The original's 'minAmount' and "
                + "'maxAmount' are not ported; write one 'modularmachinery:item' requirement per stack if you "
                + "need them, or put 'amount'/'chance'/'nbt' next to 'items' where the original read them.");
    }

    /** The option keys both requirement kinds accept, spelled exactly as {@code MachineRequirementParser} reads them. */
    private static final Set<String> COMMON_KEYS = Set.of("selector-tag", "parallelize-unaffected", "trigger-time",
            "trigger-repeatable", "script-tooltip", "ignore-output-check");

    private static boolean knownKey(String key, Set<String> allowed) {
        return allowed.contains(key) || key.equals("type") || key.equals("io-type") || COMMON_KEYS.contains(key);
    }

    /**
     * The same refusal with a caller-supplied description of the accepted keys, for {@code catalyst}.
     *
     * <p>Field names are checked against the <b>exact spelling the readers use</b>
     * ({@code readAmount}/{@code readChance}/{@code readNbt} all query the original key). The old check compared a
     * lower-cased copy, so {@code "Amount"}/ {@code "Chance"}/ {@code "NBT"} passed as "known" and were then
     * silently dropped by the readers. Such keys are now rejected with a case-sensitivity hint; none of the
     * allowed sets contains a mixed-case spelling, so nothing legitimate can collide with this.
     */
    static void rejectUnknownKeys(JsonObject json, Set<String> allowed, Object where, String description) {
        Set<String> unknown = new LinkedHashSet<>();
        Set<String> wrongCase = new LinkedHashSet<>();
        for (String key : json.keySet()) {
            if (knownKey(key, allowed)) {
                continue;
            }
            String normalised = key.toLowerCase(Locale.ROOT);
            if (knownKey(normalised, allowed)) {
                wrongCase.add(key);
            } else {
                unknown.add(key);
            }
        }
        if (!wrongCase.isEmpty()) {
            throw new JsonParseException("Field" + (wrongCase.size() == 1 ? " " : "s ") + wrongCase
                    + " in " + where + " cannot be read: field names are case-sensitive and must be spelled in "
                    + "lower case, exactly as this reader reads them. " + description);
        }
        if (!unknown.isEmpty()) {
            throw new JsonParseException("Unsupported " + (unknown.size() == 1 ? "field " : "fields ") + unknown
                    + " in " + where + ". " + description);
        }
    }
}
