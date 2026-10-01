package mmverify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.26.0 — {@code ingredient_array_input}: the offline half of the acceptance evidence.
 *
 * <p>Everything here is <b>reflection-first on purpose</b>. The section is written before the production
 * classes exist, so that the run which precedes the implementation <b>compiles</b> and reports a failed check
 * instead of failing to compile. A check that has never been seen to fail is not evidence.
 *
 * <p>The schema under test is the original's own reader ({@code RequirementTypeIngredientArray.java:45-143}),
 * which is the only one of the three "missing" types that is real: it is registered at
 * {@code RegistryRequirementTypes.java:62} and its {@code createRequirement} returns a built requirement. The
 * semantics come from the requirement class's own doc-comment
 * ({@code RequirementIngredientArray.java:44-47}): an input array <b>consumes exactly one of the group</b>.
 */
final class IngredientArrayCheck {

    /** The production parser this section drives instead of a copy of it. */
    private static final String PARSER = "com.reborn.modularmachinery.recipe.IngredientArrayParser";
    /** The production requirement class the parser must return. */
    private static final String REQUIREMENT = "com.reborn.modularmachinery.recipe.IngredientArrayRequirement";
    /** The original's type id, which is what a recipe file writes. */
    private static final String WHERE = "a requirement of mmverify:array";
    /**
     * The original's type id, which is what a recipe file writes. The parser takes it off the {@code type} key
     * and never sees it as text, so a rejection is recognised as <i>this</i> reader's by the recipe it names.
     */
    private static final String TYPE = "modularmachinery:ingredient_array_input";

    private IngredientArrayCheck() {
    }

    // ================================================================== the section

    static void ingredientArrayInput() {
        section("X. ingredient_array_input: the original's own reader, and one-of-the-group consumption");

        // The harness has to be able to say WHICH failure it is looking at. If the production class is not
        // there, every check below fails for that one reason, which is exactly the red-first evidence.
        boolean present = productionClass() != null;
        report("the production parser class " + PARSER + " exists (red-first: it does not, yet)",
                present);
        if (!present) {
            report("skipping X's behaviour checks: " + PARSER + " is absent, which the check above already "
                    + "reported. A class that does not exist cannot have its arithmetic asserted.", true);
            return;
        }

        parserShapes();
        parserRejections();
        wireShape();
        lifecycleQuantities();
        parallelismArithmetic();
        doesNotConsumeWhenShort();
    }

    // ================================================================== 1. the shapes

    private static void parserShapes() {
        System.out.println();
        System.out.println("  -- (1) the shape, read through the production parser");

        Object parsed = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":["
                + "{\"item\":\"minecraft:diamond\",\"amount\":2},"
                + "{\"item\":\"minecraft:emerald\",\"amount\":5}],\"chance\":0.5}");
        report("the original's documented example parses at all", parsed != null);
        if (parsed == null) {
            report("...and the reason it did not is reported here rather than as a NullPointerException "
                    + "somewhere later: " + whyNot(), false);
        }
        if (parsed == null) {
            return;
        }
        check("the array carries both entries", 2, entryCount(parsed));
        check("entry 0's amount", 2, entryAmount(parsed, 0));
        check("entry 1's amount", 5, entryAmount(parsed, 1));
        // The original's reader reads `chance` from the OUTER object, so one value reaches every entry. That is
        // the reader's own behaviour, not a simplification, and the settlement rolls it once.
        check("entry 0's chance (the original reads 'chance' off the requirement, not off the entry)",
                "0.5", String.valueOf(entryChance(parsed, 0)));
        check("entry 1's chance", "0.5", String.valueOf(entryChance(parsed, 1)));
        check("the requirement is an input", "true", String.valueOf(ioType(parsed) == IOType.INPUT));
        check("...and a start-phase requirement, because an input array is paid when the craft starts",
                "true", String.valueOf(((MachineRequirement) parsed).isStartPhase()));
        check("...and it is NOT a tick requirement", "false",
                String.valueOf(((MachineRequirement) parsed).perTick()));
        check("...and it is not a finish-phase requirement", "false",
                String.valueOf(((MachineRequirement) parsed).isFinishPhase()));

        // The default amount is 1, which the original gets from `int amount = 1` before it looks at the field.
        Object defaulted = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                + "\"items\":[{\"item\":\"minecraft:diamond\"}]}");
        check("an entry with no 'amount' defaults to 1", 1, entryAmount(defaulted, 0));
        check("an entry with no 'chance' defaults to 1.0 (consumed unconditionally)",
                "1.0", String.valueOf(entryChance(defaulted, 0)));

        // Amount is clamped into [1, 64] — the original's `MathHelper.clamp(..., 1, 64)` ({@code :83}). This port
        // refuses an over-large value instead of silently shrinking it, because an author who wrote 1000 meant
        // 1000 and a quiet clamp is the same class of surprise as the original's ignored per-entry amount.
        Object tooLarge = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":1000}]}");
        report("an 'amount' above 64 is refused rather than silently clamped (" + whyNot() + ")",
                tooLarge == null && whyNot().contains("above the 64"));

        // The original's 1.12.2 '@meta' suffix does not exist in 1.20.1, and every other requirement refuses it
        // loudly rather than guessing which variant was meant (LegacyIngredients:64-68).
        Object meta = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":["
                + "{\"item\":\"minecraft:wool@14\",\"amount\":1}]}");
        report("an '@meta' suffix is refused, not guessed at", meta == null);

        // Both namespaces, and a bare kind, are accepted — the rule every other type follows.
        for (String type : new String[] {"modular_machinery_reborn:ingredient_array_input",
                "ingredient_array_input"}) {
            Object requirement = parseOrNull("{\"type\":\"" + type + "\",\"io-type\":\"input\","
                    + "\"items\":[{\"item\":\"minecraft:diamond\"}]}");
            report("the type id '" + type + "' is accepted as the same requirement", requirement != null);
        }
    }

    // ================================================================== 2. the rejections

    private static void parserRejections() {
        System.out.println();
        System.out.println("  -- (2) a malformed requirement is refused loudly, and the message says what to write");

        Object[][] cases = {
                // missing / malformed 'items'
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\"}", "'items'"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":{}}", "'items'"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":\"minecraft:diamond\"}",
                        "'items'"},
                // empty array
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":[]}", "at least one"},
                // an element that is not an object
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":[\"minecraft:diamond\"]}",
                        "JSON object"},
                // an element whose 'item' is not a string
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":[{\"item\":5}]}",
                        "must be a string"},
                // an element with no 'item' at all (and no other key, so the missing 'item' is what is reported)
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":[{}]}", "Missing 'item'"},
                // 'chance' inside an entry: the original read one chance off the requirement and rolled it once
                // for the whole group, so a per-entry value was silently ignored. Named, not ignored.
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"chance\":0.5}]}", "next to 'items'"},
                // an entry's own amount is honoured (D19), so an invalid one is still refused by that path
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":0}]}", "at least 1"},
                // an unknown item
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:not_a_real_item\"}]}", "not_a_real_item"},
                // an empty item id
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":[{\"item\":\"\"}]}",
                        "'item'"},
                // 'amount' as a zero, a negative and a non-number
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":0}]}", "at least 1"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":-3}]}", "at least 1"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":\"two\"}]}",
                        "must be a number"},
                // 'chance' outside 0..1, which the original silently ignored
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\"}],\"chance\":5}", "between 0 and 1"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\"}],\"chance\":-1}", "between 0 and 1"},
                // the original's un-ported fields must be refused, not silently ignored
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\",\"nbt\":{}}]}", "nbt"},
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\"}],\"minAmount\":2}", "minAmount"},
                // an output array: the original's JSON reader could never build one, and this port's `item`
                // type already covers outputs one stack at a time.
                {"{\"type\":\"" + TYPE + "\",\"io-type\":\"output\","
                        + "\"items\":[{\"item\":\"minecraft:diamond\"}]}", "input"},
        };
        for (Object[] c : cases) {
            String json = (String) c[0];
            String expected = (String) c[1];
            try {
                Object requirement = parse(json);
                report("expected a rejection for " + json + " but got " + requirement, false);
            } catch (RuntimeException exception) {
                String message = String.valueOf(exception.getMessage());
                report("rejected for the right reason: " + message,
                        message.contains(expected) && message.contains("mmverify:array"));
            }
        }
    }

    // ================================================================== 3. the wire shape

    private static void wireShape() {
        System.out.println();
        System.out.println("  -- (3) an array must survive being sent to the client, or a server's recipes render "
                + "wrongly");
        Object parsed = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":["
                + "{\"item\":\"minecraft:diamond\",\"amount\":2},"
                + "{\"item\":\"minecraft:emerald\",\"amount\":5}],\"chance\":0.5}");
        report("the requirement carries the entries the wire path needs " + describeEntries(parsed),
                parsed != null && entryCount(parsed) == 2
                        && entryAmount(parsed, 0) == 2 && entryAmount(parsed, 1) == 5);
    }

    // ================================================================== 4. what actually moves

    private static void lifecycleQuantities() {
        System.out.println();
        System.out.println("  -- (4) the quantities that actually move, driven in MachineControllerBlockEntity#tick's "
                + "order");

        // A recipe whose only input is the array, plus a plain output so the settle has something to write.
        MachineRecipe recipe = craftRecipe(2, 1, 1.0F);

        // --- exactly enough, parallelism 1: one diamond leaves, the emerald stays
        Rig exact = new Rig(ItemStackHandler_of(Items.DIAMOND, 2, Items.EMERALD, 1));
        check("canStart with exactly the diamond the group asks for", "true",
                String.valueOf(recipe.canStart(exact.ports, RecipeModifiers.EMPTY)));
        settle(recipe, exact, 1);
        check("diamonds after one copy (the group's first entry)", 0, exact.count(Items.DIAMOND));
        check("emeralds after one copy (the group's second entry is untouched)", 1, exact.count(Items.EMERALD));
        check("the output the craft produced", 1, exact.outputCount(Items.STONE));

        // --- more than needed: only the amount is taken, the remainder stays
        Rig surplus = new Rig(ItemStackHandler_of(Items.DIAMOND, 9, Items.EMERALD, 4));
        settle(recipe, surplus, 1);
        check("diamonds consumed when 9 were available (only the 2 asked for)", 2,
                9 - surplus.count(Items.DIAMOND));
        check("emeralds consumed when 4 were available (the group stops at the first entry)", 0,
                4 - surplus.count(Items.EMERALD));
        check("and the surplus is still there", "7 / 4",
                surplus.count(Items.DIAMOND) + " / " + surplus.count(Items.EMERALD));

        // --- One entry is enough: the entries are ALTERNATIVES, not a conjunctive list. The original walks them
        //     and takes each one's `floor(available / amount)`, stopping once the target is served
        //     (`maxConsume = toConsume * (maxMultiplier - ingredientConsumed)`, :236). So a group whose FIRST
        //     entry cannot pay a copy still starts when a LATER entry can.
        MachineRecipe oneCopy = craftRecipe(2, 1, 1.0F);
        oneCopy.setParallelism(1);
        Rig short1 = new Rig(ItemStackHandler_of(Items.DIAMOND, 1, Items.EMERALD, 1));
        Object traced = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\",\"items\":["
                + "{\"item\":\"minecraft:diamond\",\"amount\":2},"
                + "{\"item\":\"minecraft:emerald\",\"amount\":1}]}");
        report("the requirement the short case uses reads back as 2x " + itemId(traced, 0)
                        + " then " + entryAmount(traced, 1) + "x " + itemId(traced, 1)
                        + " (entries=" + entryCount(traced) + ")",
                entryCount(traced) == 2 && entryAmount(traced, 0) == 2 && entryAmount(traced, 1) == 1);
        check("entry 0 sees exactly the 1 diamond the rig holds", 1,
                entryAvailable(traced, 0, short1.ports));
        check("entry 1 sees exactly the 1 emerald the rig holds", 1,
                entryAvailable(traced, 1, short1.ports));
        check("one emerald alone pays the one copy, so the limit is 1 (entry 0's shortfall is not fatal)",
                1, oneCopy.parallelism(short1.ports, RecipeModifiers.EMPTY, 8));
        check("canStart with a diamond short but the emerald alternative present", "true",
                String.valueOf(oneCopy.canStart(short1.ports, RecipeModifiers.EMPTY)));
        settle(oneCopy, short1, 1);
        check("...and the emerald is what was consumed", 0, short1.count(Items.EMERALD));
        check("...while the unusable diamond stayed", 1, short1.count(Items.DIAMOND));

        // --- both entries unable to pay a copy: nothing can start, and asking consumes nothing
        Rig short2 = new Rig(ItemStackHandler_of(Items.DIAMOND, 1, Items.EMERALD, 0));
        check("canStart when neither entry can pay a copy", "false",
                String.valueOf(oneCopy.canStart(short2.ports, RecipeModifiers.EMPTY)));
        check("...and the first entry is still there", 1, short2.count(Items.DIAMOND));
        check("...and the limit is 0", 0,
                oneCopy.parallelism(short2.ports, RecipeModifiers.EMPTY, 8));

        // --- 3 copies of a group whose FIRST entry can carry all 3 on its own: the later entry is a top-up, so
        //     it is only reached when the earlier one runs out. That is the original's
        //     `maxConsume = toConsume * (maxMultiplier - ingredientConsumed)` (:236).
        Rig firstEntryOnly = new Rig(ItemStackHandler_of(Items.DIAMOND, 6, Items.EMERALD, 3));
        settle(recipe, firstEntryOnly, 3);
        check("diamonds consumed by 3 copies (2 per copy, first entry)", 6,
                6 - firstEntryOnly.count(Items.DIAMOND));
        check("emeralds consumed by 3 copies (the first entry satisfied every copy, so the group stops there)",
                0, 3 - firstEntryOnly.count(Items.EMERALD));
        check("stones produced by 3 copies", 3, firstEntryOnly.outputCount(Items.STONE));

        // --- and now with only 1 diamond available, so copy 1 falls through to the second entry and copies 2
        //     and 3 come from it too: the ceiling is raised to 8 so the port supply, not the ceiling, decides.
        Rig fallsThrough = new Rig(ItemStackHandler_of(Items.DIAMOND, 1, Items.EMERALD, 3));
        check("with no payable diamond entry, 3 emeralds carry 3 copies", 3,
                recipe.parallelism(fallsThrough.ports, RecipeModifiers.EMPTY, 8));
        settle(recipe, fallsThrough, 3);
        check("diamonds consumed when the diamond entry cannot pay a copy", 0,
                1 - fallsThrough.count(Items.DIAMOND));
        check("emeralds consumed when every copy fell through to the second entry", 3,
                3 - fallsThrough.count(Items.EMERALD));
        check("stones produced by that mix of entries", 3, fallsThrough.outputCount(Items.STONE));

        // --- and a real mix, where the ceiling keeps the first entry from taking everything: at a ceiling of 2
        //     entry A serves both copies from 4 diamonds and the emerald is never reached
        Rig mixed = new Rig(ItemStackHandler_of(Items.DIAMOND, 4, Items.EMERALD, 1));
        check("at a ceiling of 2 the diamond entry serves both copies", 2,
                recipe.parallelism(mixed.ports, RecipeModifiers.EMPTY, 2));
        settle(recipe, mixed, 2);
        check("...consuming 4 diamonds", 4, 4 - mixed.count(Items.DIAMOND));
        check("...and leaving the emerald untouched", 1, mixed.count(Items.EMERALD));

        // --- the original's OWN documented example, whose per-entry amounts its own reader ignored (D19)
        MachineRecipe documented = arrayRecipe("\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":2},"
                + "{\"item\":\"minecraft:emerald\",\"amount\":2}]");
        documented.setParallelism(1);
        Rig doc = new Rig(ItemStackHandler_of(Items.DIAMOND, 2, Items.EMERALD, 2));
        settle(documented, doc, 1);
        check("the original's documented example consumes its entry's amount of 2 diamonds, not 1", 2,
                2 - doc.count(Items.DIAMOND));
        check("...and leaves the emerald alone, because one entry satisfies the copy", 2,
                doc.count(Items.EMERALD));

        // --- the requirement-level amount is the fallback for an entry that writes none
        MachineRecipe inherited = arrayRecipe("\"amount\":3,\"items\":[{\"item\":\"minecraft:diamond\"}]");
        inherited.setParallelism(1);
        Rig fallback = new Rig(ItemStackHandler_of(Items.DIAMOND, 3, Items.EMERALD, 1));
        settle(inherited, fallback, 1);
        check("an entry with no 'amount' inherits the requirement's 3", 3,
                3 - fallback.count(Items.DIAMOND));

        // --- and an entry's own amount overrides that fallback
        MachineRecipe overriding = arrayRecipe(
                "\"amount\":2,\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":1}]");
        overriding.setParallelism(1);
        Rig override = new Rig(ItemStackHandler_of(Items.DIAMOND, 1, Items.EMERALD, 1));
        settle(overriding, override, 1);
        check("an entry's own 'amount' wins over the requirement-level one", 1,
                1 - override.count(Items.DIAMOND));
    }

    // ================================================================== 5. the arithmetic

    private static void parallelismArithmetic() {
        System.out.println();
        System.out.println("  -- (5) the copy count: the original marks this class Parallelizable, so it bounds the "
                + "settlement");

        // Requirements: entry A costs 2 per copy, entry B costs 1 per copy, and the entries are ALTERNATIVES:
        // entry A takes as many copies as it can (up to the ceiling) and entry B only tops up what is left,
        // which is the original's `maxConsume = toConsume * (maxMultiplier - ingredientConsumed)` (:236).
        MachineRecipe recipe = craftRecipe(2, 1, 1.0F);

        int[][] cases = {
                // diamonds, emeralds, ceiling, expected copies
                // Entry A costs 2 per copy and entry B costs 1, and they are alternatives: A takes what it can
                // first, B tops up. So 4 diamonds alone already pay 2 copies (A), and 2 emeralds add 2 more.
                {4, 2, 8, 4},
                {100, 2, 8, 8},
                {40, 20, 8, 8},
                {4, 0, 8, 2},
                // A cannot carry the ceiling, so B tops up
                {2, 1, 8, 2},
                {5, 1, 8, 3},
                // A cannot serve a copy at all (1 diamond < 2), so every copy falls through to B
                {1, 100, 8, 8},
                // a ceiling below what one entry alone could pay
                {0, 100, 4, 4},
        };
        for (int[] c : cases) {
            Rig rig = new Rig(ItemStackHandler_of(Items.DIAMOND, c[0], Items.EMERALD, c[1]));
            int limit = recipe.parallelism(rig.ports, RecipeModifiers.EMPTY, c[2]);
            check("limit with " + c[0] + " diamonds (2/copy) + " + c[1] + " emeralds (1/copy) at ceiling " + c[2],
                    c[3], limit);
        }

        // ...and the limit is really what the settle then consumes.
        Rig rig = new Rig(ItemStackHandler_of(Items.DIAMOND, 5, Items.EMERALD, 1));
        int limit = recipe.parallelism(rig.ports, RecipeModifiers.EMPTY, 8);
        check("the limit from 5 diamonds + 1 emerald, entry A taking 2 copies and entry B the third", 3, limit);
        settle(recipe, rig, limit);
        check("diamonds consumed by that limit (2 copies x 2)", 4, 5 - rig.count(Items.DIAMOND));
        check("emeralds consumed by that limit (the third copy fell through to entry B)", 1,
                1 - rig.count(Items.EMERALD));

        // A requirement that is NOT parallelizable would answer 1 and bound the craft that way. This one must
        // not: the original's class does not call setParallelizeUnaffected (RequirementCatalyst does, :25).
        Object requirement = parseOrNull("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                + "\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":2}]}");
        check("the array takes part in the recipe's parallelism minimum (it is Parallelizable, as in the "
                        + "original)", "true",
                String.valueOf(((MachineRequirement) requirement).isParallelizable()));
    }

    // ================================================================== 6. the nothing-consumed case

    private static void doesNotConsumeWhenShort() {
        System.out.println();
        System.out.println("  -- (6) a group that cannot be paid consumes nothing at all");

        MachineRecipe recipe = craftRecipe(2, 1, 1.0F);
        Rig rig = new Rig(ItemStackHandler_of(Items.DIAMOND, 2, Items.EMERALD, 0));
        int limit = recipe.parallelism(rig.ports, RecipeModifiers.EMPTY, 8);
        check("a group with no emerald can still serve one copy from its diamond entry", 1, limit);
        check("...and asking did not consume the diamond the first entry wanted", 2, rig.count(Items.DIAMOND));

        // The all-or-nothing property the port keeps everywhere else (IngredientIo's own contract).
        Rig zeroed = new Rig(ItemStackHandler_of(Items.DIAMOND, 0, Items.EMERALD, 64));
        check("a group with no diamond at all serves its copies from the emerald entry instead", 8,
                recipe.parallelism(zeroed.ports, RecipeModifiers.EMPTY, 8));
        check("...and the emerald is untouched by merely asking", 64, zeroed.count(Items.EMERALD));

        // Neither entry can pay: nothing is served, and nothing is taken while asking.
        Rig none = new Rig(ItemStackHandler_of(Items.DIAMOND, 1, Items.EMERALD, 0));
        check("a group with neither entry payable serves nothing", 0,
                recipe.parallelism(none.ports, RecipeModifiers.EMPTY, 8));
        check("...and the one diamond is still there", 1, none.count(Items.DIAMOND));
    }

    // ================================================================== driving the engine

    /** A recipe whose only input is a two-entry array (2 diamonds then 1 emerald) and whose output is stone. */
    private static MachineRecipe craftRecipe(int diamonds, int emeralds, float chance) {
        return arrayRecipe("\"items\":[{\"item\":\"minecraft:diamond\",\"amount\":" + diamonds + "},"
                + "{\"item\":\"minecraft:emerald\",\"amount\":" + emeralds + "}]"
                + (chance < 1.0F ? ",\"chance\":" + chance : ""));
    }

    /** The same, with the requirement's inner JSON written out by the caller. */
    private static MachineRecipe arrayRecipe(String inner) {
        MachineRequirement array = (MachineRequirement) parse("{\"type\":\"" + TYPE + "\",\"io-type\":\"input\","
                + inner + "}");
        return new MachineRecipe(new ResourceLocation("mmverify", "ingredient_array_demo"), "demo",
                "ingredient_array_demo", 10, List.of(
                        array,
                        ItemRequirement.output(new ItemStack(Items.STONE), 1, 1, 1.0F)));
    }

    /**
     * One full craft in {@code MachineControllerBlockEntity#tick}'s order: freeze the parallelism the limit
     * computed, canStart, start, per-tick, canFinish, finish.
     */
    private static void settle(MachineRecipe recipe, Rig rig, int parallelism) {
        recipe.setParallelism(Math.max(1, parallelism));
        if (!recipe.canStart(rig.ports, RecipeModifiers.EMPTY)) {
            throw new IllegalStateException("canStart refused a craft the test expected to run");
        }
        if (!recipe.start(rig.ports, RandomSource.create(7L), RecipeModifiers.EMPTY)) {
            throw new IllegalStateException("start failed");
        }
        RandomSource random = RandomSource.create(42L);
        for (int tick = 0; tick < recipe.duration(RecipeModifiers.EMPTY); tick++) {
            if (!recipe.canTick(rig.ports, RecipeModifiers.EMPTY)) {
                throw new IllegalStateException("a tick could not be paid");
            }
            recipe.tick(rig.ports, random, RecipeModifiers.EMPTY);
        }
        if (!recipe.canFinish(rig.ports, RecipeModifiers.EMPTY)) {
            throw new IllegalStateException("the output did not fit");
        }
        recipe.finish(rig.ports, random, RecipeModifiers.EMPTY);
    }

    /** One machine's ports: a fixed item input handler and an output handler, plus a full energy buffer. */
    private static final class Rig {
        private final ItemStackHandler inputs;
        private final ItemStackHandler outputs = new ItemStackHandler(8);
        private final EnergyStorage energy = fullEnergy();
        private final HatchCollection ports;

        private Rig(ItemStackHandler inputs) {
            this.inputs = inputs;
            this.ports = HatchCollection.builder()
                    .addItemHandler(inputs, true)
                    .addItemHandler(outputs, false)
                    .addEnergyStorage(energy, true)
                    .build();
        }

        private int count(Item item) {
            int total = 0;
            for (int slot = 0; slot < inputs.getSlots(); slot++) {
                ItemStack stack = inputs.getStackInSlot(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private int outputCount(Item item) {
            int total = 0;
            for (int slot = 0; slot < outputs.getSlots(); slot++) {
                ItemStack stack = outputs.getStackInSlot(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }
    }

    private static EnergyStorage fullEnergy() {
        EnergyStorage storage = new EnergyStorage(1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        storage.receiveEnergy(1_000_000, false);
        return storage;
    }

    /** An input handler holding the given (item, amount) pairs, one per slot, each capped at 64. */
    private static ItemStackHandler ItemStackHandler_of(Item first, int firstAmount, Item second, int secondAmount) {
        ItemStackHandler handler = new ItemStackHandler(4);
        handler.setStackInSlot(0, new ItemStack(first, firstAmount));
        handler.setStackInSlot(1, new ItemStack(second, secondAmount));
        return handler;
    }

    // ================================================================== the production parser, by reflection

    private static Class<?> productionClass() {
        try {
            return Class.forName(PARSER);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** The production parser's own method, or a check failure — never a compile failure. */
    private static Object parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        IOType ioType = IOType.byName(object.get("io-type").getAsString());
        Class<?> parser = productionClass();
        if (parser == null) {
            throw new UnsupportedOperationException(PARSER + " does not exist yet");
        }
        try {
            Method method = parser.getMethod("parse", JsonObject.class, IOType.class, Object.class);
            return method.invoke(null, object, ioType, WHERE);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(PARSER
                    + "#parse(JsonObject, IOType, Object) is not callable: " + exception);
        }
    }

    /** {@link #parse} for the cases where a rejection is not meaningful; {@code null} means "it did not parse". */
    private static Object parseOrNull(String json) {
        try {
            why = null;
            return parse(json);
        } catch (RuntimeException exception) {
            why = String.valueOf(exception);
            return null;
        }
    }

    /** Why the last {@link #parseOrNull} answered {@code null}, so a failure names its own cause. */
    private static String why;

    private static String whyNot() {
        return why == null ? "(no reason recorded)" : why;
    }

    // ================================================================== reading the requirement

    private static Class<?> requirementType() {
        Class<?> type = productionClass();
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod("entryCount").getDeclaringClass();
        } catch (NoSuchMethodException exception) {
            return type;
        }
    }

    private static long entryCount(Object requirement) {
        return ((Number) read(requirement, "entryCount", "()I")).longValue();
    }

    private static long entryAmount(Object requirement, int index) {
        return ((Number) read(requirement, "entryAmount", "(I)I", index)).longValue();
    }

    private static float entryChance(Object requirement, int index) {
        return ((Number) read(requirement, "entryChance", "(I)F", index)).floatValue();
    }

    private static String itemId(Object requirement, int index) {
        Object value = read(requirement, "entryItemId", "(I)Ljava/lang/String;", index);
        return value == null ? "?" : String.valueOf(value);
    }

    private static IOType ioType(Object requirement) {
        return (IOType) read(requirement, "ioType", "()Lcom/reborn/modularmachinery/recipe/IOType;");
    }

    /** How many items entry {@code index} can see in the ports, via the production requirement's own method. */
    private static long entryAvailable(Object requirement, int index, HatchCollection ports) {
        return ((Number) read(requirement, "entryAvailableIn",
                "(ILcom/reborn/modularmachinery/machine/HatchCollection;)I", index, ports)).longValue();
    }

    /** Calls one getter by name, saying what the harness needed when it is missing. */
    private static Object read(Object requirement, String name, String descriptor, Object... args) {
        try {
            Method method = requirement.getClass().getMethod(name, parameterTypes(descriptor));
            return method.invoke(requirement, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("the production requirement does not expose " + name + descriptor
                    + ", which the acceptance evidence needs: " + exception);
        }
    }

    /** A method descriptor's parameter types, for the three shapes this class uses. */
    private static Class<?>[] parameterTypes(String descriptor) {
        String parameters = descriptor.substring(1, descriptor.indexOf(')'));
        if (parameters.isEmpty()) {
            return new Class<?>[0];
        }
        List<Class<?>> types = new ArrayList<>();
        for (int i = 0; i < parameters.length(); i++) {
            switch (parameters.charAt(i)) {
                case 'I' -> types.add(int.class);
                case 'F' -> types.add(float.class);
                case 'L' -> {
                    int end = parameters.indexOf(';', i);
                    String name = parameters.substring(i + 1, end).replace('/', '.');
                    try {
                        types.add(Class.forName(name));
                    } catch (ClassNotFoundException exception) {
                        throw new IllegalStateException("unknown parameter type " + name);
                    }
                    i = end;
                }
                default -> throw new IllegalStateException("unsupported descriptor " + descriptor);
            }
        }
        return types.toArray(new Class<?>[0]);
    }

    private static String describeEntries(Object requirement) {
        if (requirement == null) {
            return "(no requirement)";
        }
        StringBuilder builder = new StringBuilder("(");
        int count = (int) entryCount(requirement);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(entryAmount(requirement, i)).append("x ").append(itemId(requirement, i));
        }
        return builder.append(')').toString();
    }

    /** The harness's own check/report pair, reached from this class without making them public. */
    private static void check(String what, long expected, long actual) {
        ParallelCraftCheck.check(what, expected, actual);
    }

    private static void check(String what, String expected, String actual) {
        ParallelCraftCheck.check(what, expected, actual);
    }

    private static void report(String line, boolean ok) {
        ParallelCraftCheck.report(line, ok);
    }

    private static void section(String title) {
        ParallelCraftCheck.section(title);
    }
}
