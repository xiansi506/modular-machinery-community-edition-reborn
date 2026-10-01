package mmverify;

import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.EnergyRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.reborn.modularmachinery.machine.ParallelController;
import com.reborn.modularmachinery.machine.ParallelControllerCollection;
import com.reborn.modularmachinery.machine.ParallelismLimit;
import com.reborn.modularmachinery.block.ParallelControllerBlockEntity;
import com.reborn.modularmachinery.block.ParallelControllerTier;
import com.reborn.modularmachinery.block.UpgradeBusTier;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.network.ParallelControllerUpdatePacket;
import com.reborn.modularmachinery.upgrade.UpgradeBusUtility;
import com.reborn.modularmachinery.upgrade.UpgradeEffects;
import com.reborn.modularmachinery.upgrade.UpgradeStack;
import com.reborn.modularmachinery.upgrade.UpgradeTarget;
import com.reborn.modularmachinery.upgrade.UpgradeType;
import com.reborn.modularmachinery.block.MachineControllerBlock;
import com.reborn.modularmachinery.factory.FactoryEngine;
import com.reborn.modularmachinery.factory.FactoryHost;
import com.reborn.modularmachinery.factory.FactoryThread;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Offline acceptance harness for M6c's parallel settlement.
 *
 * <p>It drives {@link MachineRecipe} in the same order {@code MachineControllerBlockEntity#tick} does — compute
 * the limit, freeze it, canStart, start, per-tick, canFinish, finish — against in-memory ports, and prints what
 * actually moved. That is the evidence the scoping document asked for: N inputs consumed and N outputs produced,
 * not a label rendering.
 *
 * <p>Not part of the mod: it lives outside {@code src/} and is compiled and run by a throwaway Gradle init
 * script.
 */
public final class ParallelCraftCheck {

    private static int failures = 0;

    private static ItemStackHandler itemIn;
    private static ItemStackHandler itemOut;
    private static EnergyStorage energyIn;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // The machine: 8x coal -> 1x diamond, 10 ticks, 100 FE/t. The shape of the alloy furnace's recipes.
        MachineRecipe recipe = new MachineRecipe(new ResourceLocation("mmverify", "parallel_demo"), "demo", "demo",
                10, List.of(
                        ItemRequirement.input(ingredient(Items.COAL), 8),
                        ItemRequirement.output(new ItemStack(Items.DIAMOND), 1, 1, 1.0F),
                        new EnergyRequirement(IOType.INPUT, 100L)));

        section("A. no modifiers, machine ceiling 8");
        reset(128, 4, 1_000_000);
        int limit = recipe.parallelism(ports(), RecipeModifiers.EMPTY, 8);
        check("parallelism limit from 128 coal / 8 per copy", 8, limit);
        craft(recipe, RecipeModifiers.EMPTY, limit);
        check("coal consumed", 8 * 8, 128 - coalLeft());
        check("diamonds produced", 8, outputCount(Items.DIAMOND));
        check("FE drawn over the whole craft", 8 * 100L * 10L, 1_000_000 - energyIn.getEnergyStored());

        section("B. the alloy furnace's own modifier: item output x2 at the vent position");
        reset(128, 4, 1_000_000);
        RecipeModifiers doubler = RecipeModifiers.of(List.of(
                RecipeModifier.multiplyOutput(RecipeModifier.Target.ITEM, 2.0F)));
        limit = recipe.parallelism(ports(), doubler, 8);
        check("parallelism limit is unaffected by an output modifier", 8, limit);
        craft(recipe, doubler, limit);
        check("diamonds produced (doubled)", 16, outputCount(Items.DIAMOND));

        section("C. a modifier that doubles the input cost divides the limit");
        reset(128, 4, 1_000_000);
        RecipeModifiers inputDoubler = RecipeModifiers.of(List.of(
                new RecipeModifier(RecipeModifier.Target.ITEM, IOType.INPUT, 2.0F,
                        RecipeModifier.Operation.MULTIPLY, false)));
        // 128 coal / (8 * 2) per copy = 8 copies, and the machine ceiling is 8.
        limit = recipe.parallelism(ports(), inputDoubler, 8);
        check("limit from 128 coal at 16 per copy", 8, limit);
        craft(recipe, inputDoubler, limit);
        check("coal consumed", 8 * 16, 128 - coalLeft());
        check("diamonds produced", 8, outputCount(Items.DIAMOND));

        // Halve the supply with the ceiling raised, so the supply really is what bounds it: 64 / 16 = 4.
        reset(64, 4, 1_000_000);
        limit = recipe.parallelism(ports(), inputDoubler, 64);
        check("limit from 64 coal at 16 per copy against a ceiling of 64", 4, limit);
        craft(recipe, inputDoubler, limit);
        check("coal consumed", 4 * 16, 64 - coalLeft());
        check("diamonds produced", 4, outputCount(Items.DIAMOND));

        // Without the modifier the same supply supports twice as many copies: 64 / 8 = 8.
        reset(64, 4, 1_000_000);
        limit = recipe.parallelism(ports(), RecipeModifiers.EMPTY, 64);
        check("the same 64 coal without the modifier", 8, limit);

        section("D. an output hatch with only 4 free slots caps the limit");
        reset(128, 4, 1_000_000);
        ItemStackHandler tightOut = new ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 4; }
        };
        limit = recipe.parallelism(portsWith(tightOut), RecipeModifiers.EMPTY, 8);
        check("limit from an output hatch that holds 4", 4, limit);

        section("E. a recipe-level max-parallelism overrides a large machine ceiling");
        reset(128, 4, 1_000_000);
        MachineRecipe capped = new MachineRecipe(new ResourceLocation("mmverify", "capped"), "demo", "capped",
                10, recipe.requirements(), 2);
        limit = capped.parallelism(ports(), RecipeModifiers.EMPTY, 64);
        check("limit with max-parallelism 2 on a machine that allows 64", 2, limit);

        section("F. an energy-starved machine is throttled by the energy requirement");
        reset(128, 4, 1_000_000);
        energyIn.extractEnergy(energyIn.getEnergyStored(), false);
        energyIn.receiveEnergy(250, false);
        limit = recipe.parallelism(ports(), RecipeModifiers.EMPTY, 8);
        check("limit from 250 FE at 100 FE/copy/tick", 2, limit);

        section("G. duration modifiers");
        RecipeModifiers faster = RecipeModifiers.of(List.of(
                new RecipeModifier(RecipeModifier.Target.DURATION, null, 0.5F,
                        RecipeModifier.Operation.MULTIPLY, false)));
        check("10 ticks x 0.5", 5, recipe.duration(faster));
        check("duration multiplier", "2.0", String.valueOf(faster.durationMultiplier(10)));
        reset(128, 4, 1_000_000);
        craft(recipe, faster, 2);
        check("FE drawn is unchanged in total for a halved duration", 2 * 100L * 10L,
                1_000_000 - energyIn.getEnergyStored());

        section("H. the limit is 0 when even one copy cannot be paid");
        reset(3, 4, 1_000_000);
        check("limit with 3 of 8 coal", 0, recipe.parallelism(ports(), RecipeModifiers.EMPTY, 8));

        section("I. a 50% output at parallelism 4");
        MachineRecipe chanced = new MachineRecipe(new ResourceLocation("mmverify", "chanced"), "demo", "chanced",
                10, List.of(
                        ItemRequirement.input(ingredient(Items.COAL), 1),
                        ItemRequirement.output(new ItemStack(Items.DIAMOND), 1, 1, 1.0F),
                        ItemRequirement.output(new ItemStack(Items.EMERALD), 1, 1, 0.5F)));
        long diamonds = 0L;
        long emeralds = 0L;
        int rounds = 400;
        for (int i = 0; i < rounds; i++) {
            reset(4, 8, 1_000_000);
            craft(chanced, RecipeModifiers.EMPTY, 4);
            diamonds += outputCount(Items.DIAMOND);
            emeralds += outputCount(Items.EMERALD);
        }
        check("guaranteed diamonds over " + rounds + " crafts at parallelism 4", (long) rounds * 4, diamonds);
        double expected = rounds * 4.0 * 0.5;
        boolean within = emeralds > expected * 0.9 && emeralds < expected * 1.1;
        report("chance emeralds over " + rounds + " crafts: got " + emeralds + ", expected about " + expected,
                within);

        section("J. default machine values preserve today's behaviour");
        check("default max-parallelism", 2048,
                com.reborn.modularmachinery.machine.MachineDefinition.DEFAULT_MAX_PARALLELISM);
        check("default internal-parallelism", 0,
                com.reborn.modularmachinery.machine.MachineDefinition.DEFAULT_INTERNAL_PARALLELISM);
        reset(128, 4, 1_000_000);
        // A definition with no parallelism fields at all: ceiling 1, so the controller never parallelises.
        int defaultLimit = recipe.parallelism(ports(), RecipeModifiers.EMPTY, 1);
        check("limit on a machine whose ceiling is 1 (the pre-M6c behaviour)", 1, defaultLimit);
        craft(recipe, RecipeModifiers.EMPTY, defaultLimit);
        check("coal consumed", 8, 128 - coalLeft());
        check("diamonds produced", 1, outputCount(Items.DIAMOND));

        section("K. the machine loader's field rules and validation messages");
        loaderDefaults();
        loaderErrors();

        parallelControllerLimit();
        shippedContentMatches();
        packetRoundTrip();
        upgradeBusEffects();
        factoryLoaderFields();
        factoryEngine();        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    // =================================================================== M6e: the factory

    private static final ResourceLocation EXAMPLE_FACTORY =
            new ResourceLocation("modular_machinery_reborn", "example_factory");
    private static final ResourceLocation STONE_RECIPE =
            new ResourceLocation("mmverify", "factory_stone");
    private static final ResourceLocation SMELT_RECIPE =
            new ResourceLocation("mmverify", "factory_smelt");

    /**
     * Section P — M6e's four machine-JSON fields, read through the loader's own {@code readMachine} exactly as
     * section K reads the parallelism fields.
     *
     * <p>Two of them ({@code has-factory} / {@code factory-only}) are the original's own schema and were
     * "recognised but not evaluated" until this release; the other two ({@code max-threads} / {@code
     * core-threads}) are this project's documented extensions, for D12's reason. Every malformed value must be
     * rejected with a message that says what to write instead — including the two places where a <b>legal</b>
     * value contradicts another field, which are warned about rather than rejected because the original's own
     * default ({@code enableFactoryControllerByDefault = false}) makes "has-factory absent" the normal case.
     */
    private static void factoryLoaderFields() {
        section("P. the machine loader's factory fields");

        String parts = "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]";

        // (1) Defaults: a definition that says nothing about factories is exactly the pre-M6e machine.
        MachineDefinition bare = readDefinition("{\"registryname\":\"t\",\"localizedname\":\"T\"," + parts + "}");
        report("no factory fields -> has-factory false (the original's own default)", !bare.hasFactory());
        report("no factory fields -> factory-only false", !bare.factoryOnly());
        check("no factory fields -> max-threads 20 (Config.defaultFactoryMaxThread)",
                FactoryThreadModel.DEFAULT_MAX_THREADS, bare.maxThreads());
        check("no factory fields -> no core threads", 0, bare.coreThreads().size());
        report("a machine without has-factory is refused by a factory controller",
                !FactoryThreadModel.factoryEnabled(true, bare));
        report("...and a plain controller is refused too, even with the flag on",
                !FactoryThreadModel.factoryEnabled(false, new MachineDefinition(EXAMPLE_FACTORY, "F",
                        bare.pattern(), FailureAction.RESET, false, 2048, 0, true, List.of(),
                        4, true, false, List.of())));

        // (2) The fields as written.
        MachineDefinition full = readDefinition("{\"registryname\":\"t\",\"localizedname\":\"T\","
                + "\"has-factory\":true,\"factory-only\":false,\"max-threads\":4,\"core-threads\":["
                + "{\"name\":\"smelter\",\"recipes\":[\"modular_machinery_reborn:recipes/demo\"]},"
                + "{\"name\":\"anything\"}]," + parts + "}");
        report("has-factory true is read", full.hasFactory());
        report("factory-only false is read", !full.factoryOnly());
        check("max-threads 4 is read", 4, full.maxThreads());
        check("two core threads are read", 2, full.coreThreads().size());
        check("the first core thread's name", "smelter", full.coreThreads().get(0).name());
        check("...and its pinned recipe set", "[modular_machinery_reborn:recipes/demo]",
                full.coreThreads().get(0).recipes().toString());
        check("a core thread with no 'recipes' may run anything", 0,
                full.coreThreads().get(1).recipes().size());
        check("...and its name", "anything", full.coreThreads().get(1).name());
        check("a bare recipe name gets this mod's namespace",
                "[modular_machinery_reborn:alloy_smelter_diamond]",
                readDefinition("{\"registryname\":\"t\",\"core-threads\":[{\"name\":\"a\","
                        + "\"recipes\":[\"alloy_smelter_diamond\"]}]," + parts + "}")
                        .coreThreads().get(0).recipes().toString());
        report("a machine with has-factory is accepted by a factory controller",
                FactoryThreadModel.factoryEnabled(true, full));
        report("...but not by a plain controller", !FactoryThreadModel.factoryEnabled(false, full));
        check("the factory model's default thread count is the original's",
                FactoryThreadModel.DEFAULT_MAX_THREADS, 20);
        check("the factory model's idle timeout is the original's",
                FactoryThreadModel.IDLE_TIME_OUT, 200);
        report("the factory model's has-factory default is the original's (false)",
                !FactoryThreadModel.DEFAULT_HAS_FACTORY);

        // (3) Every malformed value, and the sentence that has to be in the message.
        Object[][] bad = {
                {"{\"registryname\":\"t\",\"has-factory\":\"yes\"," + parts + "}", "must be true or false"},
                {"{\"registryname\":\"t\",\"factory-only\":1," + parts + "}", "must be true or false"},
                {"{\"registryname\":\"t\",\"max-threads\":-1," + parts + "}", "cannot be negative"},
                {"{\"registryname\":\"t\",\"max-threads\":\"lots\"," + parts + "}", "must be a whole number"},
                {"{\"registryname\":\"t\",\"core-threads\":{}," + parts + "}", "must be an array"},
                {"{\"registryname\":\"t\",\"core-threads\":[{\"recipes\":[]}]," + parts + "}", "has no 'name'"},
                {"{\"registryname\":\"t\",\"core-threads\":[{\"name\":\"\"}]," + parts + "}", "empty 'name'"},
                {"{\"registryname\":\"t\",\"core-threads\":[{\"name\":\"a\"},{\"name\":\"a\"}]," + parts + "}",
                        "both named 'a'"},
                {"{\"registryname\":\"t\",\"core-threads\":[{\"name\":\"a\",\"recipes\":\"demo\"}]," + parts + "}",
                        "must be an array of recipe names"},
        };
        for (Object[] c : bad) {
            try {
                MachineDefinition definition = readDefinition((String) c[0]);
                report("expected a rejection for " + c[0] + " but got " + definition, false);
            } catch (RuntimeException exception) {
                String message = String.valueOf(exception.getMessage());
                report("rejected: " + message, message.contains((String) c[1]));
            }
        }

        // (4) The shipped example datapack really carries the machine and both recipes.
        Path base = exampleDatapack();
        if (base == null) {
            report("example-datapack/ not found", false);
            return;
        }
        Path machineFile = base.resolve("data/modular_machinery_reborn/machinery/example_factory.json");
        report("the shipped factory machine definition exists", Files.isRegularFile(machineFile));
        if (Files.isRegularFile(machineFile)) {
            try {
                MachineDefinition shipped = readDefinition(Files.readString(machineFile));
                report("the shipped factory machine declares has-factory", shipped.hasFactory());
                check("...and max-threads", 4, shipped.maxThreads());
                check("...and one core thread", 1, shipped.coreThreads().size());
                check("...named", "smelter", shipped.coreThreads().get(0).name());
                check("...with no pinned recipes", 0, shipped.coreThreads().get(0).recipes().size());
                report("...and it is accepted by a factory controller",
                        FactoryThreadModel.factoryEnabled(true, shipped));
                report("...and refused by a plain controller",
                        !FactoryThreadModel.factoryEnabled(false, shipped));
                // The two demo recipes must NOT share an input or an output, or "which thread did that" stops
                // being countable. That property is read out of the shipped JSON.
                //
                // It goes through JsonParser rather than the recipe serialiser on purpose: deserialising a
                // vanilla Recipe outside FML's own class loader reaches net.minecraftforge.fml.common.Mod (an
                // annotation Forge's mod-file scanner carries), so the live path cannot be driven here. What is
                // asserted is the shipped JSON; the recipes the arithmetic below runs on are built to match it.
                JsonObject stoneJson = JsonParser.parseString(Files.readString(base.resolve(
                        "data/modular_machinery_reborn/recipes/example_factory/example_factory_stone.json")))
                        .getAsJsonObject();
                JsonObject smeltJson = JsonParser.parseString(Files.readString(base.resolve(
                        "data/modular_machinery_reborn/recipes/example_factory/example_factory_smelt.json")))
                        .getAsJsonObject();
                check("stone recipe machine", "example_factory", stoneJson.get("machine").getAsString());
                check("stone recipe time", 5, stoneJson.get("recipeTime").getAsInt());
                check("smelt recipe machine", "example_factory", smeltJson.get("machine").getAsString());
                check("smelt recipe time", 10, smeltJson.get("recipeTime").getAsInt());
                check("stone recipe requirement kinds",
                        "[modular_machinery_reborn:item, modular_machinery_reborn:item]",
                        requirementKinds(stoneJson).toString());
                check("smelt recipe requirement kinds",
                        "[modular_machinery_reborn:energy, modular_machinery_reborn:item, "
                                + "modular_machinery_reborn:item]",
                        requirementKinds(smeltJson).toString());
                check("the two recipes share no output item",
                        "[]", sharedItemIds(stoneJson, smeltJson, "output").toString());
                check("...and share no input item",
                        "[]", sharedItemIds(stoneJson, smeltJson, "input").toString());
            } catch (Exception exception) {
                report("reading the shipped factory definition failed: " + exception, false);
            }
        }
        Path claim = base.resolve("claims/example_factory.json");
        report("the shipped factory claim exists", Files.isRegularFile(claim));
        if (Files.isRegularFile(claim)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(claim)).getAsJsonObject();
                check("the claim's registryname", "example_factory", json.get("registryname").getAsString());
                report("the claim sets has-factory",
                        json.has("has-factory") && json.get("has-factory").getAsBoolean());
            } catch (Exception exception) {
                report("reading the factory claim failed: " + exception, false);
            }
        }
    }

    /** The {@code type} of every requirement a shipped recipe JSON declares, in order. */
    private static List<String> requirementKinds(JsonObject recipe) {
        List<String> kinds = new ArrayList<>();
        for (JsonElement element : recipe.getAsJsonArray("requirements")) {
            kinds.add(element.getAsJsonObject().get("type").getAsString());
        }
        return kinds;
    }

    /**
     * The item names two shipped recipes share on one side ({@code "input"} or {@code "output"}).
     *
     * <p>Empty is what the demonstrator needs: if the two recipes competed for the same items, "the stone thread
     * consumed 8 cobblestone" would stop meaning anything.
     */
    private static List<String> sharedItemIds(JsonObject a, JsonObject b, String ioType) {
        List<String> first = itemIdsOnSide(a, ioType);
        first.retainAll(itemIdsOnSide(b, ioType));
        return first;
    }

    private static List<String> itemIdsOnSide(JsonObject recipe, String ioType) {
        List<String> ids = new ArrayList<>();
        for (JsonElement element : recipe.getAsJsonArray("requirements")) {
            JsonObject requirement = element.getAsJsonObject();
            if (!"modularmachinery:item".equals(requirement.get("type").getAsString())) {
                continue;
            }
            if (!ioType.equals(requirement.get("io-type").getAsString())) {
                continue;
            }
            ids.add(requirement.get("item").getAsString());
        }
        return ids;
    }

    /**
     * Section Q — M6e's actual claim, and the one the plan forbids accepting on a GUI: <b>N threads run
     * different recipes at the same time</b>, driven by {@code max-threads} and by the core-thread preset, and
     * the quantities that move are the sum of the threads' settlements.
     *
     * <p>The engine under test is the production {@link FactoryEngine}; only its host is a stand-in, because a
     * host needs a {@code Level} and this harness has none. Everything the host answers with is either taken
     * from the shipped example datapack or is the same integer the controller would compute.
     */
    private static void factoryEngine() {
        section("Q. several different recipes run at once, one per thread");

        MachineRecipe stone = new MachineRecipe(STONE_RECIPE, "example_factory", "factory_stone", 5, List.of(
                ItemRequirement.input(ingredient(Items.COBBLESTONE), 1),
                ItemRequirement.output(new ItemStack(Items.STONE), 1, 1, 1.0F)));
        MachineRecipe smelt = new MachineRecipe(SMELT_RECIPE, "example_factory", "factory_smelt", 10, List.of(
                new EnergyRequirement(IOType.INPUT, 100L),
                ItemRequirement.input(ingredient(Items.COAL), 1),
                ItemRequirement.output(new ItemStack(Items.DIAMOND), 1, 1, 1.0F)));
        report("the two demonstrator recipes are different", !stone.getId().equals(smelt.getId()));

        // (1) Two different recipes progress in the SAME tick. This is the direct evidence for "at the same
        // time": progress is captured after each engine tick, and the check is that one tick moved two crafts
        // that were both already running. A sum-of-quantities check would not distinguish "concurrent" from
        // "one after the other with the inputs pre-counted".
        //
        // The parallelism ceiling is 1 on purpose, so each thread settles exactly one copy per settlement and
        // every figure below is "one craft = one item". The ledger itself is section (6).
        FactoryRig rig = new FactoryRig(List.of(stone, smelt), 4, 8, List.of());
        rig.parallelCeiling = 1;
        rig.reset();
        rig.tick(82);
        report("two threads run at once with max-threads 4: " + rig.snapshotLog(), rig.twoRecipesRanInOneTick());
        report("...and the only recipes involved are the two distinct ones",
                rig.maxDistinctRecipesInOneTick() == 2);
        report("two crafts ADVANCED in the same tick (not merely existed): "
                        + rig.maxCraftsAdvancedInOneTick(),
                rig.maxCraftsAdvancedInOneTick() >= 2);

        // (2) What actually moved. Both recipes ran out of inputs after 8 settlements each, so the totals are
        // the sum of what the threads really settled — and they are disjoint, so neither can be the other's.
        check("cobblestone consumed by the stone thread", 8, 8 - rig.cobblestoneLeft());
        check("stone produced by the stone thread", 8, rig.outputCount(Items.STONE));
        check("coal consumed by the coal thread", 8, 8 - rig.coalLeft());
        check("diamond produced by the coal thread", 8, rig.outputCount(Items.DIAMOND));
        check("FE drawn by the coal thread (8 x 100 x 10)",
                8L * 100L * 10L, rig.energyDrawn());
        check("no thread is left working when the inputs run out", 0, rig.engine.workingThreadCount());
        report("the ordinary pool is at most max-threads", rig.engine.ordinaryThreadCount() <= 4);

        // (3) max-threads really bounds the ordinary pool. The direct evidence is the peak pool size; the
        // consequence is throughput, which is what a player would see as the difference.
        FactoryRig single = new FactoryRig(List.of(stone, smelt), 1, 8, List.of());
        single.parallelCeiling = 1;
        single.reset();
        single.tick(40);
        FactoryRig four = new FactoryRig(List.of(stone, smelt), 4, 8, List.of());
        four.parallelCeiling = 1;
        four.reset();
        four.tick(40);
        check("a one-slot factory can only ever hold one thread", 1, single.peakOrdinaryThreads);
        report("...where a four-slot one holds more than one at a time", four.peakOrdinaryThreads > 1);
        int singleSettlements = single.outputCount(Items.STONE) + single.outputCount(Items.DIAMOND);
        int fourSettlements = four.outputCount(Items.STONE) + four.outputCount(Items.DIAMOND);
        report("in 40 ticks the one-slot factory settled " + singleSettlements + " crafts and the four-slot one "
                + fourSettlements, fourSettlements > singleSettlements);
        check("...and the one-slot factory's crafts are one recipe at a time", 1, single.peakOrdinaryThreads);
        check("max-threads 0 means no ordinary thread may ever run", 0,
                new FactoryRig(List.of(stone), 0, 8, List.of()).parallelCeiling(1).tick(12)
                        .engine.ordinaryThreadCount());

        // (4) The honest version of "several threads at once", and the strongest statement the engine can make:
        // each recipe is pinned to its own core thread, so neither can starve the other, and both move inside the
        // same tick. Two different recipes, two separate settlements, one tick.
        //
        // max-threads is 4 here only so the ordinary pool is allowed to exist at all: with max-threads 0 a core
        // thread is the only thing that can hold work, and the engine's rule "one recipe per slot" then leaves
        // the second recipe with nowhere to go. The pump primitive in the block entity uses that same
        // configuration on purpose (only core threads may work), which is why the loader warns rather than errors
        // when a machine declares core threads and no ordinary ones.
        FactoryRig pinned2 = new FactoryRig(List.of(stone, smelt), 4, 8, List.of(
                new FactoryThreadModel.CoreThreadSpec("stone", List.of(STONE_RECIPE)),
                new FactoryThreadModel.CoreThreadSpec("coal", List.of(SMELT_RECIPE))));
        pinned2.parallelCeiling = 1;
        pinned2.reset();
        pinned2.engine.syncCoreThreads();
        pinned2.tick(42);
        report("two pinned core threads: " + pinned2.snapshotLog(), pinned2.twoRecipesRanInOneTick());
        report("...two crafts advanced in the same tick: " + pinned2.maxCraftsAdvancedInOneTick(),
                pinned2.maxCraftsAdvancedInOneTick() >= 2);
        // 42 ticks: the 5-tick recipe settles 8 times, the 10-tick one 4 times.
        check("...the pinned stone thread settled 8", 8, pinned2.outputCount(Items.STONE));
        check("...and the pinned coal thread settled 4", 4, pinned2.outputCount(Items.DIAMOND));
        check("...the pinned threads are still there", 2, pinned2.engine.coreThreadCount());

        // (5) A core thread exists as soon as the engine syncs, and is never reaped.
        FactoryRig withCore = new FactoryRig(List.of(stone, smelt), 4, 8,
                List.of(new FactoryThreadModel.CoreThreadSpec("smelter", List.of())));
        withCore.parallelCeiling = 1;
        withCore.reset();
        withCore.engine.syncCoreThreads();
        check("the core thread is there before anything is crafted", 1,
                withCore.engine.coreThreadCount());
        withCore.tick(82);
        check("...and is still there after the work is done", 1, withCore.engine.coreThreadCount());
        check("...and the machine's two recipes were both settled", 16,
                withCore.outputCount(Items.STONE) + withCore.outputCount(Items.DIAMOND));

        // (6) A pinned core thread accepts its set and refuses everything else. The set names a recipe that this
        // machine does not have, so the thread must stay idle while the machine's own recipe runs.
        FactoryRig pinned = new FactoryRig(List.of(stone), 4, 4,
                List.of(new FactoryThreadModel.CoreThreadSpec("pinned",
                        List.of(new ResourceLocation("mmverify", "not_a_recipe")))));
        pinned.parallelCeiling = 1;
        pinned.reset();
        pinned.engine.syncCoreThreads();
        pinned.tick(20);
        report("a core thread pinned to an unknown recipe stays idle",
                pinned.engine.coreThreads().get(0).isIdle());
        check("...while the ordinary pool did the work", 4, pinned.outputCount(Items.STONE));

        // A set that names a recipe the machine does have is accepted, and the ordinary pool is the control: with
        // the same budget the pinned core thread settles the same number.
        FactoryRig pinnedOk = new FactoryRig(List.of(stone), 4, 4,
                List.of(new FactoryThreadModel.CoreThreadSpec("pinned", List.of(STONE_RECIPE))));
        pinnedOk.parallelCeiling = 1;
        pinnedOk.reset();
        pinnedOk.engine.syncCoreThreads();
        pinnedOk.tick(20);
        check("a core thread pinned to a real recipe runs it, and keeps running it", 4,
                pinnedOk.outputCount(Items.STONE));
        report("...and the ordinary pool was there to pick up anything it could not take",
                pinnedOk.peakOrdinaryThreads >= 0);

        // (7) The parallelism ledger, which is a different quantity from the thread count: a thread's settlement
        // can move several copies, and the copies come out of the machine's ceiling — the original's
        // getAvailableParallelism, which subtracts what the other threads already claimed. Each thread is added
        // one per tick, so the ledger can be read as it fills.
        FactoryRig ledger = new FactoryRig(List.of(stone, smelt, thirdRecipe()), 3, 64, List.of());
        ledger.parallelCeiling = 8;
        ledger.reset();
        int claims = 0;
        int[] perThread = new int[3];
        for (int i = 0; i < 3; i++) {
            ledger.tick(1);
            perThread[i] = ledger.engine.allThreads().get(i).parallelism();
            claims += perThread[i];
        }
        check("the first thread claims the whole ceiling", 8, perThread[0]);
        check("...and the next thread is reduced to one copy", 1, perThread[1]);
        check("...and so is the third", 1, perThread[2]);
        check("so the three claims come to the ceiling plus the two extra threads",
                10, claims);
        report("each thread's claim is what the ceiling had left, floored at 1",
                perThread[0] == 8 && perThread[1] == 1 && perThread[2] == 1);
        // And the claims are real: a lone thread settles exactly its claim in one settlement.
        FactoryRig solo = new FactoryRig(List.of(stone), 1, 8, List.of());
        solo.parallelCeiling = 8;
        solo.reset();
        solo.tick(1);
        check("a lone thread settles its whole claim in one settlement", 8,
                solo.engine.allThreads().get(0).parallelism());

        // (8) Saving and reloading keeps the threads and their progress.
        FactoryRig persist = new FactoryRig(List.of(stone, smelt), 4, 8, List.of());
        persist.parallelCeiling = 1;
        persist.reset();
        persist.tick(3);
        int before = persist.engine.workingThreadCount();
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        persist.engine.save(saved);
        report("the save carries the original's own NBT keys",
                saved.contains("threadList") || saved.contains("coreThreadList"));
        FactoryEngine reloaded = new FactoryEngine(persist);
        reloaded.load(saved, id -> id.equals(STONE_RECIPE) ? stone : id.equals(SMELT_RECIPE) ? smelt : null,
                List.of());
        check("every thread came back", persist.engine.allThreads().size(), reloaded.allThreads().size());
        check("...still working", before, reloaded.workingThreadCount());
        check("...with their progress", persist.engine.allThreads().get(0).progress(),
                reloaded.allThreads().get(0).progress());
        check("...and their recipes", String.valueOf(persist.engine.allThreads().get(0).recipeId()),
                String.valueOf(reloaded.allThreads().get(0).recipeId()));

        // (9) The sweep. An ordinary thread with nothing left to do is reaped after IDLE_TIME_OUT; a core thread
        // is not. Driven by taking the recipes away, which is what a full output hatch does in game.
        FactoryRig idle = new FactoryRig(List.of(stone), 4, 8, List.of());
        idle.parallelCeiling = 1;
        idle.reset();
        idle.tick(82);
        report("ordinary threads were created while there was work", idle.peakOrdinaryThreads > 0);
        idle.recipes.clear();
        idle.tick(80);
        report("an idle ordinary thread is still there before its timeout", idle.engine.ordinaryThreadCount() > 0);
        idle.tick(200);
        check("with nothing left to craft, the idle ordinary threads are reaped", 0,
                idle.engine.ordinaryThreadCount());
        report("...and the pool did not refill while there was nothing to do",
                idle.engine.ordinaryThreadCount() == 0);

        FactoryRig coreKept = new FactoryRig(List.of(stone), 4, 8,
                List.of(new FactoryThreadModel.CoreThreadSpec("keeper", List.of())));
        coreKept.parallelCeiling = 1;
        coreKept.reset();
        coreKept.tick(82);
        coreKept.recipes.clear();
        coreKept.tick(300);
        check("...while a core thread survives the same sweep", 1, coreKept.engine.coreThreadCount());
        check("...and the ordinary one still went", 0, coreKept.engine.ordinaryThreadCount());

        // (9) The negative claim the task calls for: a machine WITHOUT has-factory cannot use a factory
        // controller at all. This is the whole decision, driven through the production methods.
        MachineDefinition noFactory = readDefinition("{\"registryname\":\"t\","
                + "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]}");
        MachineDefinition withFactory = readDefinition("{\"registryname\":\"t\",\"has-factory\":true,"
                + "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]}");
        report("block=factory + machine without has-factory -> no factory",
                !FactoryThreadModel.factoryEnabled(true, noFactory));
        report("block=factory + machine with has-factory -> factory",
                FactoryThreadModel.factoryEnabled(true, withFactory));
        report("block=plain + machine with has-factory -> no factory",
                !FactoryThreadModel.factoryEnabled(false, withFactory));
        report("block=plain + machine without has-factory -> no factory",
                !FactoryThreadModel.factoryEnabled(false, noFactory));
        report("a null machine is never a factory", !FactoryThreadModel.factoryEnabled(true, null));
        check("maxThreads() on a null machine falls back to the original's default",
                FactoryThreadModel.DEFAULT_MAX_THREADS, FactoryThreadModel.maxThreads(null));

        // (10) The shipped example datapack's claim: `has-factory` is what makes the block set contain a
        // factory controller, which is asserted through the same reader the loader uses.
        Path base = exampleDatapack();
        if (base == null) {
            report("example-datapack/ not found", false);
            return;
        }
        try {
            JsonObject claim = JsonParser.parseString(Files.readString(
                    base.resolve("claims/example_factory.json"))).getAsJsonObject();
            report("the shipped factory claim would register a factory controller",
                    claim.has("has-factory") && claim.get("has-factory").getAsBoolean());
            JsonObject plain = JsonParser.parseString(Files.readString(
                    base.resolve("claims/alloy_furnace.json"))).getAsJsonObject();
            report("the alloy furnace claim does NOT ask for a factory controller",
                    !plain.has("has-factory") || !plain.get("has-factory").getAsBoolean());
        } catch (Exception exception) {
            report("reading the shipped claims failed: " + exception, false);
        }
    }

    /**
     * Section R is intentionally not driven here — see its own comment. The list it would assert is
     * {@code ModBlocks.controllersNeedingGeneratedAssets()}, which the shipping checks in section Q cover from
     * the declaration side (a claim without {@code has-factory} gets no factory controller).
     */
    private static void factoryGeneratedAssetsUnused() {
    }

    // ------------------------------------------------------------------ loader rules

    /** The three machine fields, as the loader reads them out of a definition JSON. */
    private static void loaderDefaults() {
        record Case(String json, int max, int internal, boolean parallelizable, int modifierCount) {
        }
        String parts = "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]";
        Case[] cases = {
                new Case("{\"registryname\":\"t\",\"localizedname\":\"T\"," + parts + "}", 2048, 0, true, 0),
                new Case("{\"registryname\":\"t\",\"max-parallelism\":64,\"internal-parallelism\":4,"
                        + "\"parallelizable\":false," + parts + "}", 64, 4, false, 0),
                new Case("{\"registryname\":\"t\",\"modifiers\":[{\"elements\":\"minecraft:stone\","
                        + "\"x\":0,\"y\":1,\"z\":0,\"modifier\":{\"target\":\"item\",\"io\":\"output\","
                        + "\"operation\":1,\"multiplier\":2.0}}]," + parts + "}", 2048, 0, true, 1),
        };
        for (Case c : cases) {
            com.reborn.modularmachinery.machine.MachineDefinition definition = readDefinition(c.json());
            if (definition == null) {
                report("definition failed to load: " + c.json(), false);
                continue;
            }
            boolean ok = definition.maxParallelism() == c.max()
                    && definition.internalParallelism() == c.internal()
                    && definition.parallelizable() == c.parallelizable()
                    && definition.modifiers().size() == c.modifierCount();
            report("loaded " + c.json() + " -> max=" + definition.maxParallelism() + " internal="
                    + definition.internalParallelism() + " parallelizable=" + definition.parallelizable()
                    + " modifiers=" + definition.modifiers().size(), ok);
        }
    }

    /** Every malformed field must be rejected, and the message must say what to write instead. */
    private static void loaderErrors() {
        String parts = "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]";
        Object[][] cases = {
                {"{\"registryname\":\"t\",\"max-parallelism\":-1," + parts + "}", "cannot be negative"},
                {"{\"registryname\":\"t\",\"max-parallelism\":\"lots\"," + parts + "}", "must be a whole number"},
                {"{\"registryname\":\"t\",\"internal-parallelism\":64,\"max-parallelism\":8," + parts + "}",
                        "above its 'max-parallelism'"},
                {"{\"registryname\":\"t\",\"parallelizable\":\"yes\"," + parts + "}", "must be true or false"},
                {"{\"registryname\":\"t\",\"modifiers\":{}}", "must be an array"},
                {"{\"registryname\":\"t\",\"modifiers\":[{\"elements\":\"minecraft:stone\",\"x\":0,\"y\":1,\"z\":0,"
                        + "\"modifier\":{\"target\":\"mana\",\"io\":\"output\",\"operation\":1,\"multiplier\":2.0}}],"
                        + parts + "}", "not a requirement kind"},
                {"{\"registryname\":\"t\",\"modifiers\":[{\"elements\":\"minecraft:stone\",\"x\":0,\"y\":1,\"z\":0,"
                        + "\"modifier\":{\"target\":\"item\",\"io\":\"output\",\"operation\":7,\"multiplier\":2.0}}],"
                        + parts + "}", "only defines 0 (add) and 1 (multiply)"},
                {"{\"registryname\":\"t\",\"modifiers\":[{\"elements\":\"minecraft:stone\",\"x\":0,\"y\":1,\"z\":0,"
                        + "\"modifier\":{\"target\":\"item\",\"io\":\"sideways\",\"operation\":1,\"multiplier\":2.0}}],"
                        + parts + "}", "write \"input\" or \"output\""},
        };
        for (Object[] c : cases) {
            try {
                com.reborn.modularmachinery.machine.MachineDefinition definition = readDefinition((String) c[0]);
                report("expected a rejection for " + c[0] + " but got " + definition, false);
            } catch (RuntimeException exception) {
                String message = String.valueOf(exception.getMessage());
                boolean ok = message.contains((String) c[1]);
                report("rejected: " + message, ok);
            }
        }
    }

    /** Calls the loader's private {@code readMachine} on a JSON string. */
    private static com.reborn.modularmachinery.machine.MachineDefinition readDefinition(String json) {
        try {
            Class<?> loader = Class.forName("com.reborn.modularmachinery.machine.MachineLoader");
            java.lang.reflect.Method method = loader.getDeclaredMethod("readMachine", ResourceLocation.class,
                    com.google.gson.JsonElement.class, java.util.Map.class);
            method.setAccessible(true);
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            return (com.reborn.modularmachinery.machine.MachineDefinition) method.invoke(null,
                    new ResourceLocation("mmverify", "test"), element, new java.util.HashMap<String, List<String>>());
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ------------------------------------------------------------------ M6d: the parallel controller

    /** A stand-in for one placed parallel controller; the block entity is this interface plus NBT. */
    private record FakeController(int maxParallelism, int parallelism) implements ParallelController {
    }

    /** A definition with a one-position pattern, so the arithmetic can be driven without a world. */
    private static MachineDefinition definition(String path, int maxParallelism, int internalParallelism,
                                                boolean parallelizable) {
        MachinePattern pattern = MachinePattern.builder()
                .add(new BlockPos(1, -1, 0), List.of(BlockMatcher.parse("minecraft:stone")))
                .build();
        return new MachineDefinition(new ResourceLocation("mmverify", path), "Verify " + path, pattern,
                FailureAction.RESET, false, maxParallelism, internalParallelism, parallelizable, List.of());
    }

    /**
     * Section L — M6d's actual claim: a placed parallel controller raises the machine's ceiling, the raised
     * ceiling reaches the recipe, and that many copies really settle.
     *
     * <p>Driven in the same order {@code MachineControllerBlockEntity#tick} uses: the ceiling comes from
     * {@link ParallelismLimit#resolve} fed by {@link ParallelControllerCollection#parallelism()}, the ceiling is
     * handed to {@code MachineRecipe#parallelism}, and the result is counted in <b>items that moved</b>.
     */
    private static void parallelControllerLimit() {
        section("L. a parallel controller raises the limit, and the raised limit settles that many copies");

        // The tier ladder, straight out of the original's ParallelControllerData (:9-14).
        check("NORMAL ceiling", 4, ParallelControllerTier.NORMAL.defaultMaxParallelism());
        check("REINFORCED ceiling", 16, ParallelControllerTier.REINFORCED.defaultMaxParallelism());
        check("ELITE ceiling", 64, ParallelControllerTier.ELITE.defaultMaxParallelism());
        check("SUPER ceiling", 256, ParallelControllerTier.SUPER.defaultMaxParallelism());
        check("ULTIMATE ceiling", 512, ParallelControllerTier.ULTIMATE.defaultMaxParallelism());
        check("tier count", 5, ParallelControllerTier.values().length);
        // No config file is ever loaded by this harness, so the resolved value must be the original's default.
        check("ELITE resolved with no config file", 64, ParallelControllerTier.ELITE.maxParallelism());

        MachineDefinition alloyFurnace = definition("alloy_furnace", 2048, 1, true);
        MachineDefinition bare = definition("bare", 2048, 0, true);
        MachineDefinition clamped = definition("clamped", 64, 0, true);
        MachineDefinition frozen = definition("frozen", 2048, 0, false);

        // (1) No controller: M6c's behaviour has to be bit-for-bit what it was.
        check("alloy furnace with no controller", 1, ParallelismLimit.resolve(alloyFurnace, 0));
        check("internal-parallelism 0 with no controller", 1, ParallelismLimit.resolve(bare, 0));
        check("effectiveParallelCeiling() is the zero-controller case", 1, alloyFurnace.effectiveParallelCeiling());

        // (2) The collection: what the structure walk feeds in.
        ParallelController elite = new FakeController(64, 64);
        ParallelController dialledDown = new FakeController(64, 8);
        check("no controllers: count", 0, ParallelControllerCollection.EMPTY.controllerCount());
        check("no controllers: sum", 0, ParallelControllerCollection.EMPTY.parallelism());
        check("one elite: count", 1, ParallelControllerCollection.of(List.of(elite)).controllerCount());
        check("one elite: sum", 64, ParallelControllerCollection.of(List.of(elite)).parallelism());
        check("two elites: sum", 128, ParallelControllerCollection.of(List.of(elite, elite)).parallelism());
        check("a controller dialled down to 8 contributes 8", 8,
                ParallelControllerCollection.of(List.of(dialledDown)).parallelism());

        // (3) The same machine, measurably different limits with and without a controller.
        int withoutController = ParallelismLimit.resolve(bare, 0);
        int withOneElite = ParallelismLimit.resolve(bare, ParallelControllerCollection.of(List.of(elite)).parallelism());
        check("bare machine without a controller", 1, withoutController);
        check("the same machine with one elite controller", 64, withOneElite);
        report("one elite controller raises the limit from " + withoutController + " to " + withOneElite,
                withOneElite > withoutController);
        check("bare machine + super controller", 256, ParallelismLimit.resolve(bare, 256));
        check("bare machine + ultimate controller", 512, ParallelismLimit.resolve(bare, 512));
        check("alloy furnace (internal-parallelism 1) + elite", 65, ParallelismLimit.resolve(alloyFurnace, 64));
        check("a machine whose max-parallelism is 64 clamps an ultimate controller", 64,
                ParallelismLimit.resolve(clamped, 512));
        check("parallelizable:false ignores even an ultimate controller", 1,
                ParallelismLimit.resolve(frozen, 512));

        // (4) End to end, on the datapack's demonstrator: 1 cobblestone -> 1 stone, 5 ticks, 100 FE/t.
        MachineRecipe demo = new MachineRecipe(new ResourceLocation("mmverify", "parallel_demo"), "alloy_furnace",
                "alloy_furnace_parallel_demo", 5, List.of(
                        new EnergyRequirement(IOType.INPUT, 100L),
                        ItemRequirement.input(ingredient(Items.COBBLESTONE), 1),
                        ItemRequirement.output(new ItemStack(Items.STONE), 1, 1, 1.0F)));

        int noControllerCeiling = ParallelismLimit.resolve(alloyFurnace, 0);
        resetCobblestone(512, 4_000_000L);
        int noControllerLimit = demo.parallelism(ports(), RecipeModifiers.EMPTY, noControllerCeiling);
        check("copies with no controller, from 512 cobblestone", 1, noControllerLimit);
        craft(demo, RecipeModifiers.EMPTY, noControllerLimit);
        check("cobblestone consumed with no controller", 1, 512 - cobblestoneLeft());
        check("stone produced with no controller", 1, outputCount(Items.STONE));

        for (ParallelControllerTier tier : ParallelControllerTier.values()) {
            int tierCeiling = tier.maxParallelism();
            int ceiling = ParallelismLimit.resolve(alloyFurnace, tierCeiling);
            check(tier.tierName() + " controller on the alloy furnace: ceiling", tierCeiling + 1, ceiling);
            // Exactly `ceiling` cobblestone, so the supply is not what bounds the craft.
            resetCobblestone(ceiling, 4_000_000L);
            int limit = demo.parallelism(ports(), RecipeModifiers.EMPTY, ceiling);
            check(tier.tierName() + ": copies within " + ceiling + " cobblestone", ceiling, limit);
            craft(demo, RecipeModifiers.EMPTY, limit);
            check(tier.tierName() + ": cobblestone consumed in one craft", ceiling, ceiling - cobblestoneLeft());
            check(tier.tierName() + ": stone produced in one craft", ceiling, outputCount(Items.STONE));
        }

        // (5) The same supply, one copy short, must settle one copy fewer — the supply really is consulted.
        int superCeiling = ParallelismLimit.resolve(alloyFurnace, 256);
        resetCobblestone(superCeiling - 1, 4_000_000L);
        check("one cobblestone short of 257", 256, demo.parallelism(ports(), RecipeModifiers.EMPTY, superCeiling));

        // (6) The clamp the screen and the server both rely on.
        check("clamp above the ceiling", 64, ParallelControllerBlockEntity.clampParallelism(999, 64));
        check("clamp below zero", 0, ParallelControllerBlockEntity.clampParallelism(-5, 64));
        check("clamp inside the range", 37, ParallelControllerBlockEntity.clampParallelism(37, 64));
        check("clamp with a zero ceiling", 0, ParallelControllerBlockEntity.clampParallelism(10, 0));
    }

    /**
     * Section M — the numbers above only mean something if the shipped example datapack really contains the
     * machine and recipe they were taken from.
     */
    private static void shippedContentMatches() {
        section("M. the shipped example datapack carries the demonstrator");
        Path base = exampleDatapack();
        if (base == null) {
            report("example-datapack/ not found from " + System.getProperty("user.dir"), false);
            return;
        }
        try {
            JsonObject machine = JsonParser.parseString(Files.readString(base.resolve(
                    "data/modular_machinery_reborn/machinery/alloy_furnace.json"))).getAsJsonObject();
            check("alloy_furnace max-parallelism", 2048, machine.get("max-parallelism").getAsInt());
            check("alloy_furnace internal-parallelism", 1, machine.get("internal-parallelism").getAsInt());
            check("alloy_furnace parallelizable", "true", String.valueOf(machine.get("parallelizable").getAsBoolean()));

            boolean acceptsController = false;
            for (JsonElement part : machine.getAsJsonArray("parts")) {
                JsonObject entry = part.getAsJsonObject();
                if (entry.get("x").getAsInt() != 0 || entry.get("y").getAsInt() != 1
                        || entry.get("z").getAsInt() != 1) {
                    continue;
                }
                for (JsonElement element : entry.getAsJsonArray("elements")) {
                    if ("modular_machinery_reborn:parallel_controller".equals(element.getAsString())) {
                        acceptsController = true;
                    }
                }
            }
            report("the roof centre (0,1,1) accepts modular_machinery_reborn:parallel_controller",
                    acceptsController);

            JsonObject recipe = JsonParser.parseString(Files.readString(base.resolve(
                    "data/modular_machinery_reborn/recipes/alloy_smelter/alloy_smelter_parallel_demo.json")))
                    .getAsJsonObject();
            check("demo recipe machine", "alloy_furnace", recipe.get("machine").getAsString());
            check("demo recipe time", 5, recipe.get("recipeTime").getAsInt());
            List<String> requirements = new ArrayList<>();
            for (JsonElement element : recipe.getAsJsonArray("requirements")) {
                JsonObject requirement = element.getAsJsonObject();
                requirements.add(requirement.get("type").getAsString());
            }
            check("demo recipe requirement kinds",
                    "[modularmachinery:energy, modularmachinery:item, modularmachinery:item]",
                    requirements.toString());
        } catch (Exception exception) {
            report("failed to read the example datapack: " + exception, false);
        }
    }

    /** Section N — the one packet this release adds must survive a varint round trip. */
    private static void packetRoundTrip() {
        section("N. the parallel-controller update packet");
        for (int value : new int[]{0, 1, 4, 64, 512, 2048, 100_000, Integer.MAX_VALUE}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ParallelControllerUpdatePacket(value).encode(buffer);
                int decoded = new ParallelControllerUpdatePacket(buffer).newParallelism();
                check("round trip of " + value, value, decoded);
            } finally {
                buffer.release();
            }
        }
    }

    /**
     * A third recipe for the ledger check, with its own input and output so three threads never compete for the
     * same items. 20 ticks, so it is still running while the ledger is read.
     */
    private static MachineRecipe thirdRecipe() {
        return new MachineRecipe(new ResourceLocation("mmverify", "factory_third"), "example_factory",
                "factory_third", 20, List.of(
                        ItemRequirement.input(ingredient(Items.IRON_INGOT), 1),
                        ItemRequirement.output(new ItemStack(Items.GOLD_INGOT), 1, 1, 1.0F)));
    }

    /** The example datapack, looked for relative to the working directory and at the ASCII junction. */
    private static Path exampleDatapack() {
        Path[] candidates = {
                Path.of("example-datapack"),
                Path.of("..", "example-datapack"),
                Path.of("C:/mmwork/modular-machinery-reborn/example-datapack"),
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // =================================================================== M6e: the factory rig

    /**
     * A factory under test: the production {@link FactoryEngine} plus a {@link FactoryHost} that answers with
     * in-memory ports and the recipes handed to it.
     *
     * <p>The host is the only fiction. Every decision the harness asserts — which thread gets a recipe, how many
     * copies it settles, when it restarts, when it is reaped, what the loader does with the fields — is
     * production code. The framework the harness would otherwise have to build to use the controller's own host
     * is a {@code Level}, which is precisely why the engine takes a host interface at all.
     */
    private static final class FactoryRig implements FactoryHost {

        private final List<MachineRecipe> recipes = new ArrayList<>();
        private final List<FactoryThreadModel.CoreThreadSpec> coreSpecs;
        private final int maxThreads;
        private final int cobblestone;
        private final int coal;

        private net.minecraftforge.items.ItemStackHandler itemIn;
        private net.minecraftforge.items.ItemStackHandler itemOut;
        private EnergyStorage energyIn;

        private FactoryEngine engine;
        private int parallelCeiling = 8;
        private int ticksUsed;
        private int peakOrdinaryThreads;
        private final List<String> progressLog = new ArrayList<>();
        private final List<String> concurrentTicks = new ArrayList<>();

        FactoryRig(List<MachineRecipe> recipes, int maxThreads, int inputs,
                   List<FactoryThreadModel.CoreThreadSpec> coreSpecs) {
            this.recipes.addAll(recipes);
            this.maxThreads = maxThreads;
            this.cobblestone = inputs;
            this.coal = inputs;
            this.coreSpecs = List.copyOf(coreSpecs);
            this.engine = new FactoryEngine(this);
        }

        /** Fills the ports and forgets every thread, the way a fresh structure would. */
        FactoryRig reset() {
            this.itemIn = new net.minecraftforge.items.ItemStackHandler(4);
            this.itemOut = new net.minecraftforge.items.ItemStackHandler(16);
            this.energyIn = new EnergyStorage(4_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            this.itemIn.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, this.cobblestone));
            this.itemIn.setStackInSlot(1, new ItemStack(Items.COAL, this.coal));
            // A third input for the ledger check's third recipe; harmless when nothing uses it.
            this.itemIn.setStackInSlot(2, new ItemStack(Items.IRON_INGOT, this.cobblestone));
            this.energyIn.receiveEnergy(4_000_000, false);
            this.engine.reset();
            this.ticksUsed = 0;
            this.peakOrdinaryThreads = 0;
            this.progressLog.clear();
            this.concurrentTicks.clear();
            return this;
        }

        /** Runs the engine for {@code count} ticks, capturing what was running after each one. */
        FactoryRig tick(int count) {
            for (int i = 0; i < count; i++) {
                this.engine.tick();
                this.ticksUsed++;
                this.peakOrdinaryThreads = Math.max(this.peakOrdinaryThreads, this.engine.ordinaryThreadCount());
                capture();
            }
            return this;
        }

        /** Sets the machine's parallelism ceiling, so each thread's settlement size is controlled. */
        FactoryRig parallelCeiling(int value) {
            this.parallelCeiling = value;
            return this;
        }

        /**
         * Records, per tick, which recipes had an active craft and what its progress was. From this the harness
         * can tell "two crafts advanced in the same tick" from "two crafts existed in the same tick" — the
         * former is the evidence, the latter is not.
         */
        private void capture() {
            List<String> running = new ArrayList<>();
            for (FactoryThread thread : this.engine.allThreads()) {
                if (thread.isWorking()) {
                    running.add(thread.recipeId() + "@" + thread.progress());
                }
            }
            this.progressLog.add(running.toString());
            if (running.size() > 1) {
                boolean distinct = new java.util.HashSet<>(running.stream()
                        .map(entry -> entry.substring(0, entry.indexOf('@'))).toList()).size() > 1;
                if (distinct) {
                    this.concurrentTicks.add(running.toString());
                }
            }
        }

        /** Whether one engine tick advanced two crafts whose recipes differ. */
        boolean twoRecipesRanInOneTick() {
            return !this.concurrentTicks.isEmpty();
        }

        int maxDistinctRecipesInOneTick() {
            int best = 0;
            for (String entry : this.progressLog) {
                String trimmed = entry.substring(1, Math.max(1, entry.length() - 1));
                if (trimmed.isBlank()) {
                    continue;
                }
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (String piece : trimmed.split(", ")) {
                    ids.add(piece.substring(0, piece.indexOf('@')));
                }
                best = Math.max(best, ids.size());
            }
            return best;
        }

        String snapshotLog() {
            return this.concurrentTicks.isEmpty()
                    ? "no tick ran two different recipes (log: " + this.progressLog + ")"
                    : this.concurrentTicks.size() + " ticks ran two different recipes, first was "
                            + this.concurrentTicks.get(0);
        }

        /**
         * How many crafts advanced during one engine tick — the strongest form of the claim, computed from the
         * progress log rather than from anything the engine reports about itself.
         */
        int maxCraftsAdvancedInOneTick() {
            int best = 0;
            for (int i = 1; i < this.progressLog.size(); i++) {
                int advanced = 0;
                for (String before : pieces(this.progressLog.get(i - 1))) {
                    int at = before.indexOf('@');
                    String recipe = before.substring(0, at);
                    int progress = Integer.parseInt(before.substring(at + 1));
                    for (String after : pieces(this.progressLog.get(i))) {
                        int at2 = after.indexOf('@');
                        if (after.substring(0, at2).equals(recipe)
                                && Integer.parseInt(after.substring(at2 + 1)) == progress + 1) {
                            advanced++;
                        }
                    }
                }
                best = Math.max(best, advanced);
            }
            return best;
        }

        private static List<String> pieces(String entry) {
            String trimmed = entry.substring(1, Math.max(1, entry.length() - 1));
            return trimmed.isBlank() ? List.of() : List.of(trimmed.split(", "));
        }

        int cobblestoneLeft() {
            return this.itemIn.getStackInSlot(0).getCount();
        }

        int coalLeft() {
            return this.itemIn.getStackInSlot(1).getCount();
        }

        long energyDrawn() {
            return 4_000_000L - this.energyIn.getEnergyStored();
        }

        int outputCount(Item item) {
            int total = 0;
            for (int slot = 0; slot < this.itemOut.getSlots(); slot++) {
                ItemStack stack = this.itemOut.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        // ---------------------------------------------------------------- FactoryHost

        @Override
        public RecipeModifiers modifiers() {
            return RecipeModifiers.EMPTY;
        }

        @Override
        public HatchCollection ports() {
            return HatchCollection.builder()
                    .addItemHandler(this.itemIn, true)
                    .addItemHandler(this.itemOut, false)
                    .addEnergyStorage(this.energyIn, true)
                    .build();
        }

        @Override
        public List<FactoryThreadModel.CoreThreadSpec> coreThreads() {
            return this.coreSpecs;
        }

        @Override
        public List<MachineRecipe> availableRecipes() {
            return List.copyOf(this.recipes);
        }

        @Override
        public int parallelCeiling() {
            return this.parallelCeiling;
        }

        @Override
        public int maxThreads() {
            return this.maxThreads;
        }

        @Override
        public RandomSource random() {
            return RandomSource.create(7L);
        }

        @Override
        public void onThreadStarted(FactoryThread thread) {
        }

        @Override
        public void onThreadFinished(FactoryThread thread) {
        }

        @Override
        public void onThreadIdle(FactoryThread thread) {
        }

        @Override
        public void onThreadsChanged() {
        }
    }

    // =================================================================== M6b: the upgrade bus

    /**
     * Section O — M6b's actual claim: <b>inserting an upgrade into a bus changes the craft outcome</b>.
     *
     * <p>This is the evidence the plan demands. "The bus GUI shows the upgrade" is explicitly not accepted, so
     * every check below counts something that really moved — ticks of crafting, or items consumed and produced —
     * and the upgrade is put into an in-memory bus inventory and taken out again through the very same method
     * the block entity uses ({@code UpgradeEffects.read}), then folded through the same
     * {@code MachineControllerBlockEntity.collectBusModifiers} the server calls.
     *
     * <p>The recipe is the datapack's own alloy-furnace demonstrator (1 cobblestone → 1 stone, 5 ticks,
     * 100 FE/t) because it is the smallest thing whose arithmetic is unambiguous.
     *
     * <p>The declarations under test are read out of the shipped {@code example-datapack}, through the loader's
     * own private reader, so this also proves the shipped JSON carries what the section claims.
     */
    private static void upgradeBusEffects() {
        section("O. an upgrade in a bus changes what the craft does");

        UpgradeType speed = loadUpgrade("example_speed");
        UpgradeType doubler = loadUpgrade("example_output_doubler");
        UpgradeType charge = loadUpgrade("example_charge");
        if (speed == null || doubler == null || charge == null) {
            report("could not read the shipped upgrade declarations", false);
            return;
        }

        // (1) The declarations themselves, as the loader reads them.
        check("example_speed declares one modifier", 1, speed.modifiers().size());
        check("example_speed is stackable", "true", String.valueOf(speed.stackable()));
        check("example_speed's modifier target", "DURATION",
                String.valueOf(speed.modifiers().get(0).target()));
        check("example_speed's modifier operation", "MULTIPLY",
                String.valueOf(speed.modifiers().get(0).operation()));
        check("example_speed's modifier value", "0.5",
                String.valueOf(speed.modifiers().get(0).value()));
        check("example_output_doubler declares one modifier", 1, doubler.modifiers().size());
        check("example_output_doubler is NOT stackable", "false", String.valueOf(doubler.stackable()));
        check("example_output_doubler's io", "OUTPUT", String.valueOf(doubler.modifiers().get(0).ioTarget()));
        check("example_charge declares no modifier", 0, charge.modifiers().size());

        // (1b) The five tiers, straight out of the original's UpgradeBusData (:8-14), and the resolved value with
        // no config file loaded — the same pair of assertions section L makes for the parallel controller.
        check("NORMAL slots", 3, UpgradeBusTier.NORMAL.defaultMaxUpgradeSlots());
        check("REINFORCED slots", 6, UpgradeBusTier.REINFORCED.defaultMaxUpgradeSlots());
        check("ELITE slots", 9, UpgradeBusTier.ELITE.defaultMaxUpgradeSlots());
        check("SUPER slots", 12, UpgradeBusTier.SUPER.defaultMaxUpgradeSlots());
        check("ULTIMATE slots", 18, UpgradeBusTier.ULTIMATE.defaultMaxUpgradeSlots());
        check("tier count", 5, UpgradeBusTier.values().length);
        check("ULTIMATE resolved with no config file", 18, UpgradeBusTier.ULTIMATE.maxUpgradeSlots());
        check("the original's config upper bound", 18, ModConfig.MAX_UPGRADE_SLOTS_LIMIT);

        // The two shipped item mappings, through the loader's own reader. See checkItemMappings for why the live
        // registry cannot be populated offline, and what that means for the checks below.
        checkItemMappings();

        // (2) The shipped example datapack really carries these files (section M's style).
        Path base = exampleDatapack();
        if (base == null) {
            report("example-datapack/ not found", false);
        } else {
            for (String name : new String[] {"example_speed", "example_output_doubler"}) {
                Path file = base.resolve("data/modular_machinery_reborn/upgrade/" + name + ".json");
                report("shipped declaration " + name + ".json exists", Files.isRegularFile(file));
            }
            try {
                JsonObject machine = JsonParser.parseString(Files.readString(base.resolve(
                        "data/modular_machinery_reborn/machinery/alloy_furnace.json"))).getAsJsonObject();
                boolean acceptsBus = false;
                for (JsonElement part : machine.getAsJsonArray("parts")) {
                    JsonObject entry = part.getAsJsonObject();
                    if (entry.get("x").getAsInt() != 0 || entry.get("y").getAsInt() != 1
                            || entry.get("z").getAsInt() != 0) {
                        continue;
                    }
                    for (JsonElement element : entry.getAsJsonArray("elements")) {
                        if ("modular_machinery_reborn:upgrade_bus".equals(element.getAsString())) {
                            acceptsBus = true;
                        }
                    }
                }
                report("the alloy furnace's roof front centre (0,1,0) accepts modular_machinery_reborn:upgrade_bus",
                        acceptsBus);
                check("the alloy furnace still has 26 parts", 26, machine.getAsJsonArray("parts").size());
            } catch (Exception exception) {
                report("failed to read the alloy furnace definition: " + exception, false);
            }
        }

        // (3) A base craft with an empty bus: 8 cobblestone -> 8 stone in 5 ticks.
        MachineRecipe recipe = parallelDemo();
        resetCobblestone(128, 4_000_000L);
        RecipeModifiers emptyBus = busModifiers(UpgradeStack.Bag.EMPTY, ALLOY_FURNACE);
        check("an empty bus contributes nothing", 0, emptyBus.modifiers().size());
        int limit = recipe.parallelism(ports(), emptyBus, 8);
        check("limit with an empty bus", 8, limit);
        craft(recipe, emptyBus, limit);
        check("cobblestone consumed with an empty bus", 8, 128 - cobblestoneLeft());
        check("stone produced with an empty bus", 8, outputCount(Items.STONE));

        // (4) A duration upgrade halves the craft, measured in ticks the harness actually ticked.
        RecipeModifiers fast = busModifiers(bag(stack(speed, 1)), ALLOY_FURNACE);
        check("a speed upgrade contributes one modifier", 1, fast.modifiers().size());
        check("duration with the speed upgrade (5 ticks x 0.5)", 3, recipe.duration(fast));
        check("duration multiplier with the speed upgrade", "2.0",
                String.valueOf(fast.durationMultiplier(recipe.recipeTime())));
        // The per-tick rate compensation: the shortened craft draws `rate * parallelism * durationMultiplier`
        // for `duration(modifiers)` ticks. 8 copies x 100 FE/t x 2.0 x 3 ticks = 4800.
        // NOTE: this is NOT the unmodified total of 4000, and that is a pre-existing M6c behaviour rather than a
        // property of this slice: `applyDurationMultiplier` is computed from the *unrounded* duration while the
        // tick count is `round(recipeTime * value)`, so the two disagree by the rounding. Section G asserts the
        // same identity on a machine with no bus in the way.
        resetCobblestone(16, 4_000_000L);
        long shortenedFeBefore = energyIn.getEnergyStored();
        int ticks = craftCountingTicks(recipe, fast, 8);
        long shortenedFe = shortenedFeBefore - energyIn.getEnergyStored();
        check("ticks actually ticked with the speed upgrade", 3, ticks);
        check("cobblestone consumed with the speed upgrade", 8, 16 - cobblestoneLeft());
        check("stone produced with the speed upgrade", 8, outputCount(Items.STONE));
        check("FE drawn over the shortened craft (compensated per tick)", 8 * 100L * 3L * 2L, shortenedFe);

        // (5) The same upgrade at stack size 2: `stackable` squares the value, as the original's loop did.
        RecipeModifiers faster = busModifiers(bag(stack(speed, 2)), ALLOY_FURNACE);
        check("duration with two speed upgrades (5 x 0.25)", 1, recipe.duration(faster));
        RecipeModifiers faster3 = busModifiers(bag(stack(speed, 3)), ALLOY_FURNACE);
        check("duration with three speed upgrades is floored at one tick", 1, recipe.duration(faster3));

        // A non-stackable upgrade must NOT scale: that is the difference the field carries.
        UpgradeStack doublerStack2 = stack(doubler, 2);
        check("a non-stackable upgrade's multiplier is unchanged at count 2", "2.0",
                String.valueOf(UpgradeEffects.stacked(doubler.modifiers().get(0), doublerStack2).value()));
        check("a stackable upgrade's multiplier squares at count 2", "0.25",
                String.valueOf(UpgradeEffects.stacked(speed.modifiers().get(0),
                        stack(speed, 2)).value()));

        // (6) An OUTPUT x2 upgrade doubles what the craft produces.
        resetCobblestone(8, 4_000_000L);
        RecipeModifiers doubled = busModifiers(bag(stack(doubler, 1)), ALLOY_FURNACE);
        check("an output doubler contributes one modifier", 1, doubled.modifiers().size());
        int doubledLimit = recipe.parallelism(ports(), doubled, 4);
        check("the doubled output divides the limit from 8 cobblestone", 4, doubledLimit);
        craft(recipe, doubled, doubledLimit);
        check("cobblestone consumed with the output doubler", 4, 8 - cobblestoneLeft());
        check("stone produced with the output doubler", 8, outputCount(Items.STONE));

        // (7) An incompatible upgrade is refused, not misapplied.
        UpgradeStack.Bag incompatibleBag = bag(stack(doubler, 1));
        check("the doubler's whitelist names the alloy furnace", "true",
                String.valueOf(doubler.isCompatible(ALLOY_FURNACE)));
        check("the doubler is refused by a different machine", "false",
                String.valueOf(doubler.isCompatible(OTHER_MACHINE)));
        RecipeModifiers refused = busModifiers(incompatibleBag, OTHER_MACHINE);
        check("a bus upgrade incompatible with the machine contributes nothing", 0,
                refused.modifiers().size());
        resetCobblestone(4, 4_000_000L);
        int refusedLimit = recipe.parallelism(ports(), refused, 2);
        check("the refused craft settles the unmodified number of copies", 2, refusedLimit);
        craft(recipe, refused, refusedLimit);
        check("stone produced with the refused upgrade (undoubled)", 2, outputCount(Items.STONE));
        // The GUI's own listing is unfiltered, which is how the warning rows get their content.
        check("the unfiltered bag still holds the upgrade", 1, incompatibleBag.size());
        check("...and reports it as incompatible with that machine", 1,
                incompatibleBag.incompatibleWith(OTHER_MACHINE).size());

        // (8) Merging slots: the original summed same-type slots instead of applying twice.
        // Driven through the real reader with real carrier ItemStacks. The declaration table is a standing map
        // rather than the live registry, because this harness runs outside Forge's mod loading, where
        // ForgeRegistries.ITEMS.getValue answers AIR for everything — a fact this section found the hard way.
        Map<Item, UpgradeTarget.Targets> carriers = new java.util.LinkedHashMap<>();
        carriers.put(Items.IRON_INGOT, new UpgradeTarget.Targets(List.of(
                new UpgradeTarget.Fixed(Items.IRON_INGOT, speed))));
        carriers.put(Items.COPPER_INGOT, new UpgradeTarget.Targets(List.of(
                new UpgradeTarget.Fixed(Items.COPPER_INGOT, doubler))));
        java.util.function.Function<ItemStack, UpgradeTarget.Targets> lookup =
                stack -> stack.isEmpty() ? null : carriers.get(stack.getItem());

        net.minecraftforge.items.ItemStackHandler busInventory = new net.minecraftforge.items.ItemStackHandler(3);
        busInventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        busInventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 1));
        busInventory.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 5));
        UpgradeStack.Bag merged = UpgradeEffects.read(busInventory, lookup);
        check("two carrier slots merge into one upgrade", 1, merged.size());
        check("...with their counts summed", 2, merged.itemCount());
        check("a non-carrier item in a slot contributes nothing", 1, merged.stacks().size());
        check("the merged upgrade is the one the declaration names", "modular_machinery_reborn:example_speed",
                merged.stacks().get(0).type().id().toString());
        check("...and reading the bus with two carriers gives the squared multiplier", "0.25",
                String.valueOf(UpgradeEffects.of(merged, ALLOY_FURNACE).applyDuration(1.0)));
        // Two different carriers in one bus: two upgrades, and the reader tells them apart.
        busInventory.setStackInSlot(1, new ItemStack(Items.COPPER_INGOT, 1));
        UpgradeStack.Bag mixed = UpgradeEffects.read(busInventory, lookup);
        check("two different carriers give two upgrades", 2, mixed.size());
        check("...and their counts are separate", 2, mixed.itemCount());
        check("...and the output doubler is among them", "true",
                String.valueOf(mixed.stacks().stream()
                        .anyMatch(s -> s.type().id().equals(doubler.id()))));
        // The live-registry entry point must still be the one the block entity uses.
        check("the block entity's reader is the registry-backed overload", 0,
                UpgradeEffects.read(busInventory).size());

        // (9) The controller's own join, driven directly: structural modifiers and bus modifiers meet in one set.
        // Two independent x2 output modifiers multiply to x4, which is countable in stone.
        RecipeModifiers structural = RecipeModifiers.of(List.of(
                RecipeModifier.multiplyOutput(RecipeModifier.Target.ITEM, 2.0F)));
        RecipeModifiers joined = structural.andThen(busModifiers(bag(stack(doubler, 1)), ALLOY_FURNACE));
        check("the joined set carries both modifiers", 2, joined.modifiers().size());
        resetCobblestone(8, 4_000_000L);
        int joinedLimit = recipe.parallelism(ports(), joined, 3);
        // 8 cobblestone at 1 per copy allows 8; space allows 16 / 4 per copy = 4; the ceiling is 3.
        check("the x4 output limits the copies a 16-slot hatch can hold", 3, joinedLimit);
        craft(recipe, joined, joinedLimit);
        check("cobblestone consumed with two x2 output modifiers", 3, 8 - cobblestoneLeft());
        check("stone produced with two x2 output modifiers (3 copies x 4)", 12, outputCount(Items.STONE));

        // (10) The static join the block entity calls is reachable and behaves as the inline one does.
        // The FakeBus stands in for a placed block; this harness never builds a Level.
        List<UpgradeBusUtility> buses = List.of(new FakeBus(mixed));
        RecipeModifiers viaController = com.reborn.modularmachinery.block.MachineControllerBlockEntity
                .collectBusModifiers(buses, ALLOY_FURNACE);
        check("controller join over one bus holding two upgrades", 2, viaController.modifiers().size());
        check("controller join with no machine", 0,
                com.reborn.modularmachinery.block.MachineControllerBlockEntity
                        .collectBusModifiers(buses, null).modifiers().size());
        check("controller join over two buses", 4,
                com.reborn.modularmachinery.block.MachineControllerBlockEntity
                        .collectBusModifiers(List.of(new FakeBus(mixed), new FakeBus(mixed)), ALLOY_FURNACE)
                        .modifiers().size());
    }

    /** A bus standing in for a placed block: this harness never builds a {@code Level}. */
    private record FakeBus(UpgradeStack.Bag contents) implements UpgradeBusUtility {
        @Override public boolean bindMachine(net.minecraft.core.BlockPos pos, ResourceLocation machineId) {
            return false;
        }

        @Override public java.util.Map<net.minecraft.core.BlockPos, ResourceLocation> boundMachines() {
            return java.util.Map.of();
        }

        @Override public UpgradeStack.Bag upgrades() {
            return contents;
        }
    }

    private static final ResourceLocation ALLOY_FURNACE =
            new ResourceLocation("modular_machinery_reborn", "alloy_furnace");
    private static final ResourceLocation OTHER_MACHINE =
            new ResourceLocation("modular_machinery_reborn", "iron_centrifuge");

    private static UpgradeStack.Bag bag(UpgradeStack... stacks) {
        return new UpgradeStack.Bag(List.of(stacks));
    }

    /**
     * An {@link UpgradeStack} for a declaration read straight from JSON. The carrier target's item is only used
     * by the bus's slot reader, never by the arithmetic, so a fixed target without one is the right stand-in —
     * and {@code UpgradeStack} takes one because a dynamic upgrade would need its item's NBT.
     */
    private static UpgradeStack stack(UpgradeType type, int count) {
        return new UpgradeStack(new UpgradeTarget.Fixed(null, type), count);
    }

    /**
     * The controller's own arithmetic for one bus, reached through the same static method the block entity calls.
     * Using it here rather than a local lambda is the point: the harness drives production code, not a copy.
     */
    private static RecipeModifiers busModifiers(UpgradeStack.Bag contents, ResourceLocation machineId) {
        return com.reborn.modularmachinery.block.MachineControllerBlockEntity
                .collectBusModifiers(List.of(new FakeBus(contents)), machineId);
    }

    /** The datapack's alloy-furnace demonstrator: 1 cobblestone → 1 stone, 5 ticks, 100 FE/t. */
    private static MachineRecipe parallelDemo() {
        return new MachineRecipe(new ResourceLocation("mmverify", "upgrade_bus_demo"), "alloy_furnace",
                "alloy_furnace_parallel_demo", 5, List.of(
                        new EnergyRequirement(IOType.INPUT, 100L),
                        ItemRequirement.input(ingredient(Items.COBBLESTONE), 1),
                        ItemRequirement.output(new ItemStack(Items.STONE), 1, 1, 1.0F)));
    }

    /** {@link #craft} but reporting how many ticks the loop really ran. */
    private static int craftCountingTicks(MachineRecipe recipe, RecipeModifiers modifiers, int parallelism) {
        HatchCollection ports = ports();
        recipe.setParallelism(Math.max(1, parallelism));
        recipe.applyDurationMultiplier(modifiers);
        int duration = recipe.duration(modifiers);
        if (!recipe.canStart(ports, modifiers) || !recipe.canTick(ports, modifiers)) {
            throw new IllegalStateException("craft could not start");
        }
        if (!recipe.start(ports, RandomSource.create(1L), modifiers)) {
            throw new IllegalStateException("start failed");
        }
        RandomSource random = RandomSource.create(42L);
        int ticked = 0;
        for (int tick = 0; tick < duration; tick++) {
            if (!recipe.canTick(ports, modifiers)) {
                throw new IllegalStateException("tick " + tick + " could not be paid");
            }
            recipe.tick(ports, random, modifiers);
            ticked++;
        }
        if (!recipe.canFinish(ports, modifiers)) {
            throw new IllegalStateException("outputs do not fit");
        }
        recipe.finish(ports, random, modifiers);
        return ticked;
    }

    /**
     * FE one craft draws, measured as the difference across exactly that craft.
     *
     * <p>The harness shares one {@code EnergyStorage} across a section, so an absolute {@code start - stored}
     * figure would include every earlier craft. Measuring the delta is what makes the number mean "this craft".
     */
    private static long energyDrawnBy(Runnable craft, int cobblestone) {
        resetCobblestone(cobblestone, 4_000_000L);
        long before = energyIn.getEnergyStored();
        craft.run();
        return before - energyIn.getEnergyStored();
    }

    /**
     * One upgrade declaration, read from the shipped example datapack through the loader's own private reader —
     * the same reflection trick section K uses for {@code readMachine}.
     */
    @Nullable
    private static UpgradeType loadUpgrade(String name) {
        Path base = exampleDatapack();
        if (base == null) {
            report("example-datapack/ not found", false);
            return null;
        }
        Path file = base.resolve("data/modular_machinery_reborn/upgrade/" + name + ".json");
        if (!Files.isRegularFile(file)) {
            report("no such upgrade declaration: " + file, false);
            return null;
        }
        try {
            Class<?> loader = Class.forName("com.reborn.modularmachinery.upgrade.UpgradeLoader");
            java.lang.reflect.Method method = loader.getDeclaredMethod("readType", ResourceLocation.class,
                    com.google.gson.JsonElement.class);
            method.setAccessible(true);
            com.google.gson.JsonElement element = JsonParser.parseString(Files.readString(file));
            return (UpgradeType) method.invoke(null, new ResourceLocation("mmverify", name), element);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            report("reading " + name + " failed: " + exception.getCause(), false);
            return null;
        } catch (Exception exception) {
            report("reading " + name + " failed: " + exception, false);
            return null;
        }
    }

    /**
     * One {@code <name>.item.json} mapping, read through the loader's own private {@code readMapping}, so the
     * harness exercises the real item resolution rather than constructing a target by hand. Its own type table is
     * primed with the declarations under test, which is what the loader's second pass does.
     */
    @Nullable
    private static UpgradeTarget loadUpgradeTarget(String name) {
        Path base = exampleDatapack();
        if (base == null) {
            return null;
        }
        Path file = base.resolve("data/modular_machinery_reborn/upgrade/" + name + ".json");
        if (!Files.isRegularFile(file)) {
            report("no such item mapping: " + file, false);
            return null;
        }
        try {
            Object mapping = readMapping(file, name);
            if (mapping == null) {
                return null;
            }
            java.lang.reflect.Method target = mapping.getClass().getDeclaredMethod("target");
            target.setAccessible(true);
            return (UpgradeTarget) target.invoke(mapping);
        } catch (Exception exception) {
            report("reading " + name + " failed: " + exception, false);
            return null;
        }
    }

    /** Calls the loader's private {@code readMapping} on a shipped file, with the declarations installed. */
    @Nullable
    private static Object readMapping(Path file, String name) {
        try {
            Class<?> loader = Class.forName("com.reborn.modularmachinery.upgrade.UpgradeLoader");
            java.lang.reflect.Method method = loader.getDeclaredMethod("readMapping", ResourceLocation.class,
                    com.google.gson.JsonElement.class, java.util.Map.class);
            method.setAccessible(true);
            com.google.gson.JsonElement element = JsonParser.parseString(Files.readString(file));
            return method.invoke(null, new ResourceLocation("mmverify", name), element, upgradeTypes());
        } catch (java.lang.reflect.InvocationTargetException exception) {
            report("reading " + name + " failed: " + exception.getCause(), false);
            return null;
        } catch (Exception exception) {
            report("reading " + name + " failed: " + exception, false);
            return null;
        }
    }

    /** Every upgrade declaration the shipped datapack carries, keyed as the loader would key it. */
    private static Map<ResourceLocation, UpgradeType> upgradeTypes() {
        Map<ResourceLocation, UpgradeType> types = new java.util.HashMap<>();
        for (String name : new String[] {"example_speed", "example_output_doubler", "example_charge"}) {
            UpgradeType type = loadUpgrade(name);
            if (type != null) {
                types.put(type.id(), type);
            }
        }
        return types;
    }

    /**
     * Reads the shipped item mappings through the loader's own reader, and reports what the two item names in
     * them resolve to.
     *
     * <p>This is where the harness has to be honest about its own limits. It runs outside Forge's mod loading,
     * so {@code ForgeRegistries.ITEMS.getValue} answers {@code Items.AIR} for <b>every</b> name, including this
     * mod's own items — which is why the live registry cannot be populated here and why the bus reader takes a
     * lookup function (see {@code UpgradeEffects#read}). This method therefore asserts the part that <i>is</i>
     * verifiable offline: that the parser accepts both files and yields the upgrade each one names.
     */
    private static void checkItemMappings() {
        for (String name : new String[] {"wrench.item", "modularium.item"}) {
            Path base = exampleDatapack();
            if (base == null) {
                report("example-datapack/ not found", false);
                return;
            }
            Object mapping = readMapping(
                    base.resolve("data/modular_machinery_reborn/upgrade/" + name + ".json"), name);
            if (mapping == null) {
                continue;
            }
            try {
                java.lang.reflect.Method itemOf = mapping.getClass().getDeclaredMethod("item");
                java.lang.reflect.Method targetOf = mapping.getClass().getDeclaredMethod("target");
                itemOf.setAccessible(true);
                targetOf.setAccessible(true);
                Item item = (Item) itemOf.invoke(mapping);
                UpgradeTarget target = (UpgradeTarget) targetOf.invoke(mapping);
                report("mapping " + name + " parses and names " + target.upgrade().id()
                        + " (its item resolves to " + item + " here, because ForgeRegistries is empty offline)",
                        target.upgrade() != null);
            } catch (Exception exception) {
                report("inspecting " + name + " failed: " + exception, false);
            }
        }
    }

    /** Fills the input bus with cobblestone and the output bus with empty slots. */
    private static void resetCobblestone(int amount, long energy) {
        itemIn = new ItemStackHandler(Math.max(4, (amount + 63) / 64));
        itemOut = new ItemStackHandler(16);
        energyIn = new EnergyStorage(1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        int remaining = amount;
        for (int slot = 0; slot < itemIn.getSlots() && remaining > 0; slot++) {
            int put = Math.min(64, remaining);
            itemIn.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, put));
            remaining -= put;
        }
        energyIn.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.max(0L, energy)), false);
    }

    private static int cobblestoneLeft() {
        int total = 0;
        for (int slot = 0; slot < itemIn.getSlots(); slot++) {
            total += itemIn.getStackInSlot(slot).getCount();
        }
        return total;
    }

    // ------------------------------------------------------------------ harness

    /** One full craft, driven in the order {@code MachineControllerBlockEntity#tick} uses. */
    private static void craft(MachineRecipe recipe, RecipeModifiers modifiers, int parallelism) {
        HatchCollection ports = ports();
        recipe.setParallelism(Math.max(1, parallelism));
        recipe.applyDurationMultiplier(modifiers);
        int duration = recipe.duration(modifiers);
        if (!recipe.canStart(ports, modifiers) || !recipe.canTick(ports, modifiers)) {
            throw new IllegalStateException("craft could not start");
        }
        if (!recipe.start(ports, RandomSource.create(1L), modifiers)) {
            throw new IllegalStateException("start failed");
        }
        RandomSource random = RandomSource.create(42L);
        for (int tick = 0; tick < duration; tick++) {
            if (!recipe.canTick(ports, modifiers)) {
                throw new IllegalStateException("tick " + tick + " could not be paid");
            }
            recipe.tick(ports, random, modifiers);
        }
        if (!recipe.canFinish(ports, modifiers)) {
            throw new IllegalStateException("outputs do not fit");
        }
        recipe.finish(ports, random, modifiers);
    }

    private static HatchCollection ports() {
        return portsWith(itemOut);
    }

    private static HatchCollection portsWith(ItemStackHandler out) {
        return HatchCollection.builder()
                .addItemHandler(itemIn, true)
                .addItemHandler(out, false)
                .addEnergyStorage(energyIn, true)
                .build();
    }

    private static void reset(int coal, int outSlots, int energy) {
        itemIn = new ItemStackHandler(4);
        itemOut = new ItemStackHandler(outSlots);
        energyIn = new EnergyStorage(1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        int remaining = coal;
        for (int slot = 0; slot < 4 && remaining > 0; slot++) {
            int put = Math.min(64, remaining);
            itemIn.setStackInSlot(slot, new ItemStack(Items.COAL, put));
            remaining -= put;
        }
        energyIn.receiveEnergy(energy, false);
    }

    private static int coalLeft() {
        int total = 0;
        for (int slot = 0; slot < itemIn.getSlots(); slot++) {
            total += itemIn.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static int outputCount(Item item) {
        int total = 0;
        for (int slot = 0; slot < itemOut.getSlots(); slot++) {
            ItemStack stack = itemOut.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Ingredient ingredient(Item item) {
        return Ingredient.of(item);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
    }

    private static void check(String what, long expected, long actual) {
        report(what + ": expected " + expected + ", got " + actual, expected == actual);
    }

    private static void check(String what, String expected, String actual) {
        report(what + ": expected " + expected + ", got " + actual, expected.equals(actual));
    }

    private static void report(String line, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + line);
        if (!ok) {
            failures++;
        }
    }

    private ParallelCraftCheck() {
    }
}
