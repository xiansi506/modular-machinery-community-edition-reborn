package mmverify;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 0.29.0 — the construct tool ({@code itemconstructtool}): the offline half of its acceptance evidence.
 *
 * <h2>What the original's tool is, established from its own code</h2>
 *
 * <p>{@code ItemConstructTool.java:53-68} has exactly <b>one</b> handler, {@code onItemUse} (right click). There
 * is no left-click handler and no block placement anywhere in the class or in anything it calls: repository-wide
 * searches for {@code onLeftClick} / {@code LeftClick} / {@code attackEntity} / {@code setBlockState} find
 * nothing for this item. Right-clicking a non-controller block toggles that position in a <b>set</b>
 * ({@code PlayerStructureSelectionHelper.StructureSelection:138,151-157} — a {@code LinkedList} plus
 * {@code contains}, i.e. a WorldEdit-style wand, not a cuboid). Right-clicking a {@code BlockController}
 * ({@code ItemConstructTool.java:57-61}) finalizes it: the set is compressed to offsets relative to the clicked
 * controller ({@code PlayerStructureSelectionHelper.java:84,159-179}), rotated counter-clockwise until the
 * controller faces north while the player is told the degrees ({@code :85-94}), and written to
 * {@code machine-<player>-<timestamp>.json} in the machinery directory ({@code :96-125}) as
 * {@code BlockArray.serializeAsMachineJson}'s {@code {"parts": …}} fragment ({@code BlockArray.java:395-443}).
 * The gate is server side + creative + {@code canSendCommands} ({@code ItemConstructTool.java:54}). There is
 * <b>no size cap</b> and <b>no build step</b>: the tool never places a block.
 *
 * <p>So the thing that can be proven without a game is: the toggle arithmetic, the rotation arithmetic, the
 * descriptor text, the fragment text, the file-name scheme, the packet round trip and the gate's truth table.
 * The thing that cannot be is where a click lands, what the highlight looks like, and whether the file appears
 * in the right directory at runtime — those are the in-game checklist's job.
 *
 * <p>Everything here is <b>reflection-first on purpose</b>, the same shape sections X/Y/Z use: the section is
 * written before the production classes exist, so the run that precedes the implementation <b>compiles</b> and
 * reports failed checks (one per missing class, i.e. a checklist of the deliverable) instead of failing to
 * build. A check that has never been seen to fail is not evidence.
 */
final class ConstructToolCheck {

    private static final String SELECTION = "com.reborn.modularmachinery.selection.StructureSelection";
    private static final String EXPORT = "com.reborn.modularmachinery.selection.SelectionExport";
    private static final String PART = "com.reborn.modularmachinery.selection.ExportPart";
    private static final String SAMPLE = "com.reborn.modularmachinery.selection.BlockSample";
    private static final String WRITER = "com.reborn.modularmachinery.selection.MachineFragmentWriter";
    private static final String PACKET = "com.reborn.modularmachinery.network.SelectionSyncPacket";
    private static final String ITEM = "com.reborn.modularmachinery.item.ConstructToolItem";

    /** The original's own lang file, for the wording comparison. */
    private static final String ORIGINAL_LANG =
            "C:/mmwork/_mmce-src/ModularMachinery-Community-Edition-master/src/main/resources/assets/"
                    + "modularmachinery/lang";

    private ConstructToolCheck() {
    }

    // ================================================================== the section

    static void constructTool() {
        section("AA. the construct tool: selection arithmetic, the export fragment, and the gate");

        // ---- (0) the deliverable itself: one check per class, so a red run reads as a checklist -----------
        List<String> missing = new ArrayList<>();
        for (String name : List.of(SELECTION, EXPORT, PART, SAMPLE, WRITER, PACKET, ITEM)) {
            Class<?> type = find(name);
            report("the production class " + name + " exists (red-first: it does not, yet)", type != null);
            if (type == null) {
                missing.add(name);
            }
        }

        // ---- (1) two things that do not depend on any of them ---------------------------------------------
        registryProbe();
        languageKeys();

        if (!missing.isEmpty()) {
            report("skipping AA's arithmetic checks: " + missing + " are absent, which the checks above already "
                    + "reported. Arithmetic that does not exist cannot be asserted.", true);
            return;
        }

        selectionState();
        rotationArithmetic();
        descriptorsAndTheLoaderRoundTrip();
        fragmentText();
        nbtCapture();
        fileNameScheme();
        permissionGate();
        packetRoundTrip();
        placesNothing();
    }

    /**
     * Can the loader's own block registry be read here at all?
     *
     * <p>This decides how much of the round trip is provable offline, so it is asserted rather than assumed:
     * section O recorded that {@code ForgeRegistries.ITEMS.getValue(…)} answers air for everything outside Forge's
     * mod loading. {@code BlockMatcher.parse} resolves through {@code ForgeRegistries.BLOCKS}, so if that answers
     * {@code Blocks.AIR} here, the "the loader accepts the fragment and matches the same states" checks cannot be
     * run and the fragment's <b>text</b> is all that is left.
     */
    private static void registryProbe() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        net.minecraft.world.level.block.Block resolved =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                        new ResourceLocation("minecraft", "stone"));
        report("the loader's own registry resolves minecraft:stone in this harness (" + resolved + "), which is "
                        + "what lets the fragment be fed back through BlockMatcher.parse",
                resolved == stone.getBlock());
    }

    // ================================================================== the seven language keys

    /**
     * The tool's own strings, against the original's own wording.
     *
     * <p>The keys are the <b>original's</b> keys, not namespaced ones, for the same reason section R keeps
     * {@code craftcheck.failure.*} verbatim: the wording can then be diffed against {@code _mmce-src}'s lang files
     * key for key, and a future porter reads the mapping directly.
     */
    private static void languageKeys() {
        section("AA1. the tool's language keys, against the original's wording");

        Path zh = Path.of("C:/mmwork/modular-machinery-reborn/src/main/resources/assets/"
                + "modular_machinery_reborn/lang/zh_cn.json");
        Path en = Path.of("C:/mmwork/modular-machinery-reborn/src/main/resources/assets/"
                + "modular_machinery_reborn/lang/en_us.json");
        Map<String, String> zhMap = flatJson(zh);
        Map<String, String> enMap = flatJson(en);
        Map<String, String> sourceZh = flatLang(Path.of(ORIGINAL_LANG + "/zh_CN.lang"));
        Map<String, String> sourceEn = flatLang(Path.of(ORIGINAL_LANG + "/en_US.lang"));
        if (zhMap.isEmpty() || enMap.isEmpty() || sourceZh.isEmpty() || sourceEn.isEmpty()) {
            report("the language files were readable (this mod " + zhMap.size() + "/" + enMap.size()
                    + " keys, the original " + sourceZh.size() + "/" + sourceEn.size() + ")", false);
            return;
        }

        for (String key : List.of("tooltip.constructtool.creative",
                "message.structurebuild.empty",
                "message.structurebuild.confirmrotation",
                "message.structurebuild.confirmrotation.rotating",
                "message.structurebuild.warndedicated",
                "message.structurebuild.save",
                "message.structurebuild.fail")) {
            String expectedZh = sourceZh.get(key);
            String expectedEn = sourceEn.get(key);
            report("zh '" + key + "' is the original's wording (" + expectedZh + ")",
                    expectedZh != null && expectedZh.equals(zhMap.get(key)));
            report("en '" + key + "' is the original's wording (" + expectedEn + ")",
                    expectedEn != null && expectedEn.equals(enMap.get(key)));
        }

        report("the two language files carry the same key set (" + zhMap.size() + " zh / " + enMap.size()
                        + " en, difference " + symmetricDifference(zhMap, enMap) + ")",
                zhMap.size() == enMap.size()
                        && new TreeSet<>(zhMap.keySet()).equals(new TreeSet<>(enMap.keySet())));
    }

    private static String symmetricDifference(Map<String, String> zh, Map<String, String> en) {
        TreeSet<String> only = new TreeSet<>(zh.keySet());
        only.removeAll(en.keySet());
        TreeSet<String> other = new TreeSet<>(en.keySet());
        other.removeAll(zh.keySet());
        only.addAll(other);
        return only.isEmpty() ? "none" : only.toString();
    }

    // ================================================================== the selection set

    /**
     * The toggle arithmetic, including the boundaries the acceptance brief names: an empty selection, a single
     * block, and a size far past anything a machine uses (there is <b>no cap</b> in the original, so what is
     * asserted is that a large one is neither refused nor truncated — inventing a cap would be a divergence).
     */
    private static void selectionState() {
        section("AA2. the selection: a toggled set, in insertion order, with no cap");

        Class<?> type = find(SELECTION);
        Method toggle = methodOrNull(type, "toggle", BlockPos.class);
        Method size = methodOrNull(type, "size");
        Method isEmpty = methodOrNull(type, "isEmpty");
        Method positions = methodOrNull(type, "positions");
        Method of = methodOrNull(type, "of", java.util.Collection.class);
        Method contains = methodOrNull(type, "contains", BlockPos.class);
        if (toggle == null || size == null || isEmpty == null || positions == null || of == null) {
            report("StructureSelection's own API is present (toggle/size/isEmpty/positions/of)", false);
            return;
        }

        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(4, 5, 6);
        BlockPos c = new BlockPos(-7, 0, 8);

        // (1) the empty boundary.
        Object empty = newSelection(type);
        check("a fresh selection's size", 0, asInt(call(size, empty)));
        report("...and it is empty", asBoolean(call(isEmpty, empty)));
        report("...and its position list is empty", asList(call(positions, empty)).isEmpty());

        // (2) a single block, then the same block again.
        Object one = newSelection(type);
        report("toggling one position reports it as added", asBoolean(call(toggle, one, a)));
        check("...size is 1", 1, asInt(call(size, one)));
        check("...and it is that position", "[" + a + "]", asList(call(positions, one)).toString());
        report("toggling the same position again reports it as removed", !asBoolean(call(toggle, one, a)));
        check("...size is back to 0", 0, asInt(call(size, one)));
        report("...and it is empty again", asBoolean(call(isEmpty, one)));

        // (3) insertion order, and the remove/re-add case: the position goes to the END.
        Object ordered = newSelection(type);
        call(toggle, ordered, a);
        call(toggle, ordered, b);
        call(toggle, ordered, c);
        check("three toggles keep insertion order", "[" + a + ", " + b + ", " + c + "]",
                asList(call(positions, ordered)).toString());
        call(toggle, ordered, a);
        check("...toggling a selected position again removes it", "[" + b + ", " + c + "]",
                asList(call(positions, ordered)).toString());
        call(toggle, ordered, a);
        check("...and re-adding it appends it at the end, rather than restoring its old place",
                "[" + b + ", " + c + ", " + a + "]", asList(call(positions, ordered)).toString());
        check("...and no position is ever stored twice", 3, asInt(call(size, ordered)));

        // (4) `positions()` is a copy: a caller cannot corrupt the selection through it.
        List<Object> copy = asList(call(positions, ordered));
        copy.clear();
        check("the list returned by positions() is a copy", 3, asInt(call(size, ordered)));

        // (5) `of(...)` copies its argument and deduplicates.
        List<BlockPos> seed = new ArrayList<>(List.of(a, b, a, c));
        Object from = call(of, null, seed);
        check("of(...) deduplicates but keeps the first occurrence's order", "[" + a + ", " + b + ", " + c + "]",
                asList(call(positions, from)).toString());
        seed.clear();
        check("...and it is a copy of the argument, not a view", 3, asInt(call(size, from)));
        if (contains != null) {
            report("contains(another position) is false", !asBoolean(call(contains, from, new BlockPos(0, 0, 0))));
        }

        // (6) no cap: 4096 positions (a 64x64 floor, far past any machine) survive a toggle round trip.
        Object large = newSelection(type);
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                call(toggle, large, new BlockPos(x, 64, z));
            }
        }
        check("4096 toggled positions are all kept (the original has no size cap)", 4096, asInt(call(size, large)));
        List<Object> largePositions = asList(call(positions, large));
        check("...the first is still the first", new BlockPos(0, 64, 0).toString(),
                String.valueOf(largePositions.get(0)));
        check("...and the last is still the last", new BlockPos(63, 64, 63).toString(),
                String.valueOf(largePositions.get(63 * 64 + 63)));
    }

    // ================================================================== the rotation

    /**
     * The rotation rule, as arithmetic a pack author can act on: "the exported offsets are written as if the
     * controller faced north; a controller facing north needs no rotation, and each quarter turn counter-clockwise
     * from north costs one".
     *
     * <p>The last group binds the tool's arithmetic to the <b>loader's</b> own rotator
     * ({@code MachinePattern.rotateYCounterClockwise}), which is the invariant that matters: the fragment the tool
     * writes must be the pattern the loader would have built anyway, so loading it does not rotate it again.
     */
    private static void rotationArithmetic() {
        section("AA3. the rotation: offsets are written as if the controller faced north");

        Class<?> type = find(EXPORT);
        Method steps = methodOrNull(type, "rotationSteps", Direction.class);
        Method rotate = methodOrNull(type, "rotate", BlockPos.class, int.class);
        Method offsets = methodOrNull(type, "offsets", List.class, BlockPos.class, Direction.class);
        if (steps == null || rotate == null || offsets == null) {
            report("SelectionExport's own API is present (rotationSteps/rotate/offsets)", false);
            return;
        }

        check("rotationSteps(NORTH)", 0, asInt(call(steps, null, Direction.NORTH)));
        check("rotationSteps(EAST)", 1, asInt(call(steps, null, Direction.EAST)));
        check("rotationSteps(SOUTH)", 2, asInt(call(steps, null, Direction.SOUTH)));
        check("rotationSteps(WEST)", 3, asInt(call(steps, null, Direction.WEST)));

        // The original's MiscUtils.rotateYCCW(pos) = (z, y, -x).
        check("rotate((1,2,3), 1)", new BlockPos(3, 2, -1).toString(),
                String.valueOf(call(rotate, null, new BlockPos(1, 2, 3), 1)));
        check("rotate((1,2,3), 0) is the identity", new BlockPos(1, 2, 3).toString(),
                String.valueOf(call(rotate, null, new BlockPos(1, 2, 3), 0)));
        check("rotate(p, 4) is the identity", new BlockPos(1, 2, 3).toString(),
                String.valueOf(call(rotate, null, new BlockPos(1, 2, 3), 4)));
        check("rotate(p, 5) == rotate(p, 1)", String.valueOf(call(rotate, null, new BlockPos(1, 2, 3), 1)),
                String.valueOf(call(rotate, null, new BlockPos(1, 2, 3), 5)));

        BlockPos controller = new BlockPos(10, 20, 30);
        List<BlockPos> selection = List.of(new BlockPos(11, 20, 30), new BlockPos(10, 21, 30),
                new BlockPos(10, 20, 31));

        // NORTH: nothing is rotated, and the offsets are simply the difference. The expected values are built as
        // BlockPos instances rather than as text, so the assertion does not depend on how 1.20.1 spells one.
        List<Object> north = asList(call(offsets, null, selection, controller, Direction.NORTH));
        check("offsets for a north-facing controller are relative to it",
                List.of(new BlockPos(1, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 0, 1)).toString(),
                north.toString());

        // Every other facing: rotate that same north-shaped set `rotationSteps` times, and the tool must agree.
        for (Direction facing : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            int turns = asInt(call(steps, null, facing));
            List<String> expected = new ArrayList<>();
            for (Object offset : north) {
                expected.add(String.valueOf(call(rotate, null, offset, turns)));
            }
            check("offsets for a " + facing + "-facing controller are the north ones turned " + turns
                    + " quarter turn(s)", expected.toString(), asList(call(offsets, null, selection, controller,
                    facing)).toString());
        }

        // The cross-check against the loader's own rotator: its pattern, turned `rotationSteps` times, must have
        // exactly the tool's offsets as its positions. Wrapped, because it needs a BlockMatcher and therefore the
        // registry the check at the top of this section probed.
        try {
            com.reborn.modularmachinery.machine.MachinePattern.Builder builder =
                    com.reborn.modularmachinery.machine.MachinePattern.builder();
            List<com.reborn.modularmachinery.machine.BlockMatcher> stoneMatcher =
                    List.of(com.reborn.modularmachinery.machine.BlockMatcher.parse("minecraft:stone"));
            for (Object offset : north) {
                builder.add((BlockPos) offset, stoneMatcher);
            }
            com.reborn.modularmachinery.machine.MachinePattern pattern = builder.build();
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                com.reborn.modularmachinery.machine.MachinePattern turned = pattern;
                for (int i = 0; i < asInt(call(steps, null, facing)); i++) {
                    turned = turned.rotateYCounterClockwise();
                }
                report("the tool's offsets for " + facing + " are the loader's own rotation of the same set ("
                                + turned.positions().keySet() + ")",
                        turned.positions().keySet().equals(new java.util.LinkedHashSet<>(
                                castPositions(call(offsets, null, selection, controller, facing)))));
            }
        } catch (RuntimeException exception) {
            report("the loader's own rotation could be compared against the tool's (it needs a BlockMatcher, "
                    + "so it needs the registry): " + exception, false);
        }

        // An empty selection has no offsets and must not throw.
        report("an empty selection produces no offsets",
                asList(call(offsets, null, List.of(), controller, Direction.EAST)).isEmpty());

        // The controller's own position, if the author selected it too: the original keeps it as the origin and
        // the loader drops it at load time (MachineSchema:231-234). Kept faithfully, asserted here.
        List<Object> withOrigin = asList(call(offsets, null, List.of(controller, new BlockPos(11, 20, 30)), controller,
                Direction.NORTH));
        check("a selected controller position becomes the origin, as in the original",
                List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)).toString(), withOrigin.toString());
        report("...which is exactly what the loader skips later (see AA4's round trip)", containsOrigin(withOrigin));
    }

    private static boolean containsOrigin(List<Object> offsets) {
        for (Object offset : offsets) {
            if (new BlockPos(0, 0, 0).equals(offset)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== descriptors, and the loader

    /**
     * The descriptor, and the strongest offline claim available: the fragment the tool writes is fed back through
     * the <b>loader's own schema</b> ({@code MachineSchema.read}) and the resulting pattern accepts exactly the
     * states that were selected. 1.12.2 wrote {@code block@meta}; 1.20.1 has no metadata, so the faithful
     * translation of "the state that was there" is <b>every property, named</b>.
     */
    private static void descriptorsAndTheLoaderRoundTrip() {
        section("AA4. the descriptor, and the loader accepting the fragment");

        Class<?> type = find(EXPORT);
        Method describe = methodOrNull(type, "describe", BlockState.class);
        Method offsets = methodOrNull(type, "offsets", List.class, BlockPos.class, Direction.class);
        Method parts = methodOrNull(type, "parts", List.class, List.class);
        Method toJson = methodOrNull(type, "toMachineJson", List.class);
        if (describe == null || offsets == null || parts == null || toJson == null) {
            report("SelectionExport's own API is present (describe/parts/toMachineJson)", false);
            return;
        }

        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        check("describe(stone)", "minecraft:stone", String.valueOf(call(describe, null, stone)));
        check("describe(oak_stairs) names every property, sorted",
                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                String.valueOf(call(describe, null, stairs)));

        // The loader's own parser must accept that text and match the very state it came from.
        try {
            com.reborn.modularmachinery.machine.BlockMatcher parsed =
                    com.reborn.modularmachinery.machine.BlockMatcher.parse(String.valueOf(call(describe, null, stairs)));
            report("BlockMatcher.parse accepts the descriptor the tool writes", true);
            report("...and it matches the state it was written from (a state round trip)", parsed.matches(stairs));
            report("...and it does not match a different state of the same block",
                    !parsed.matches(stairs.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)));
        } catch (RuntimeException exception) {
            report("BlockMatcher.parse accepts the descriptor the tool writes: " + exception, false);
        }

        // The whole fragment, back through the schema: the parts, and every one of them accepting its own state.
        BlockPos controller = new BlockPos(5, 5, 5);
        List<BlockPos> selection = List.of(new BlockPos(6, 5, 5), new BlockPos(5, 6, 5));
        List<Object> planOffsets = asList(call(offsets, null, selection, controller, Direction.NORTH));
        List<Object> samples = new ArrayList<>();
        samples.add(sampleFor(String.valueOf(call(describe, null, stone)), null));
        samples.add(sampleFor(String.valueOf(call(describe, null, stairs)), null));
        List<Object> exportParts = asList(call(parts, null, planOffsets, samples));
        String fragment = String.valueOf(call(toJson, null, exportParts));
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(fragment);
            report("the fragment parses as JSON", parsed.isJsonObject());
            report("...and it carries 'parts' as its only root field, so it is a fragment rather than a "
                            + "definition (" + ((com.google.gson.JsonObject) parsed).keySet() + ")",
                    ((com.google.gson.JsonObject) parsed).keySet().equals(java.util.Set.of("parts")));
            // What the author does next, asserted rather than promised: the loader refuses the fragment BY NAME
            // until it has a registryname...
            try {
                com.reborn.modularmachinery.machine.MachineSchema.read(parsed, "the construct tool's fragment",
                        Map.of());
                report("the loader refuses the fragment until it has a registryname", false);
            } catch (com.google.gson.JsonParseException expected) {
                report("the loader refuses the fragment until it has a registryname, in its own words: "
                                + expected.getMessage(),
                        expected.getMessage().contains("registryname"));
            }
            // ...and accepts it once it has one, which is the whole documented next step.
            com.google.gson.JsonObject asDefinition = parsed.getAsJsonObject().deepCopy();
            asDefinition.addProperty("registryname", "probe");
            com.reborn.modularmachinery.machine.MachineDefinition definition =
                    com.reborn.modularmachinery.machine.MachineSchema.read(asDefinition,
                            "the construct tool's fragment plus a registryname", Map.of());
            check("with a registryname added the loader reads it into one part per selected position", 2,
                    definition.pattern().partCount());
            check("...and their relative positions are the tool's offsets",
                    List.of(new BlockPos(1, 0, 0), new BlockPos(0, 1, 0)).toString(),
                    new ArrayList<>(definition.pattern().positions().keySet()).toString());
            boolean all = true;
            all &= definition.pattern().positions().get(new BlockPos(1, 0, 0)).get(0).matches(stone);
            all &= definition.pattern().positions().get(new BlockPos(0, 1, 0)).get(0).matches(stairs);
            report("...and each part accepts the block state it was written from", all);
        } catch (RuntimeException exception) {
            report("the loader reads the tool's fragment with the same schema a data pack uses: " + exception,
                    false);
        }
    }

    // ================================================================== the fragment text

    /**
     * The fragment text, byte for byte, against the original's own writer
     * ({@code BlockArray.serializeAsMachineJson}, {@code :395-443}): four-space indents, the original's key
     * names and order ({@code x}, {@code y}, {@code z}, {@code nbt} when there is one, {@code elements}), and
     * {@code parts} as the only root field. Newlines are compared LF-normalised, because the original used the
     * platform separator.
     */
    private static void fragmentText() {
        section("AA5. the fragment text");

        Class<?> type = find(EXPORT);
        Method toJson = methodOrNull(type, "toMachineJson", List.class);
        Method parts = methodOrNull(type, "parts", List.class, List.class);
        if (toJson == null || parts == null) {
            report("SelectionExport's own API is present (parts/toMachineJson)", false);
            return;
        }

        List<Object> one = List.of(part(new BlockPos(1, 0, 0), "minecraft:stone", null));
        // The original closes with `    ]}` on one line — `sb.append(move).append("]"); sb.append("}")` with no
        // newline between them (BlockArray.java:440-441) — so the expected text says the same. The first draft of
        // this assertion inserted a newline there and was wrong, not the writer.
        check("one part, no nbt", lines(
                        "{",
                        "    \"parts\": [",
                        "        {",
                        "            \"x\": 1,",
                        "            \"y\": 0,",
                        "            \"z\": 0,",
                        "            \"elements\": [",
                        "                \"minecraft:stone\"",
                        "            ]",
                        "        }",
                        "    ]}"),
                normalize(String.valueOf(call(toJson, null, one))));

        List<Object> withNbt = List.of(part(new BlockPos(-1, 2, 3), "minecraft:chest", "{\"LootTable\":\"x\"}"));
        String text = normalize(String.valueOf(call(toJson, null, withNbt)));
        check("one part with nbt", lines(
                        "{",
                        "    \"parts\": [",
                        "        {",
                        "            \"x\": -1,",
                        "            \"y\": 2,",
                        "            \"z\": 3,",
                        "            \"nbt\": {\"LootTable\":\"x\"},",
                        "            \"elements\": [",
                        "                \"minecraft:chest\"",
                        "            ]",
                        "        }",
                        "    ]}"), text);

        // Two parts: the comma after the first object, and nothing after the second.
        List<Object> two = List.of(part(new BlockPos(1, 0, 0), "minecraft:stone", null),
                part(new BlockPos(0, 1, 0), "minecraft:oak_stairs", null));
        String both = normalize(String.valueOf(call(toJson, null, two)));
        report("two parts are comma-separated and the last has no trailing comma",
                both.contains("        },\n        {") && both.endsWith("        }\n    ]}"));
        report("...and the element of each is the descriptor it was given",
                both.contains("\"minecraft:stone\"") && both.contains("\"minecraft:oak_stairs\""));

        // The empty case: unreachable in production (finalize returns early on an empty selection) but the
        // writer must still produce valid JSON rather than throw.
        String emptyText = normalize(String.valueOf(call(toJson, null, List.of())));
        check("an empty part list is still valid JSON", lines(
                "{",
                "    \"parts\": [",
                "    ]}"), emptyText);
        try {
            com.google.gson.JsonParser.parseString(emptyText);
            report("...which the JSON parser accepts", true);
        } catch (RuntimeException exception) {
            report("...which the JSON parser accepts: " + exception, false);
        }

        // parts(): one entry per offset, a null sample meaning "that position was not available" is skipped.
        List<BlockPos> planOffsets = List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0));
        List<Object> samples = new ArrayList<>();
        samples.add(sampleFor("minecraft:stone", null));
        samples.add(null);
        samples.add(sampleFor("minecraft:dirt", "{\"a\":1}"));
        List<Object> built = asList(call(parts, null, planOffsets, samples));
        check("an unavailable position is skipped, in order", 2, built.size());
        report("...the first kept part is the first available position with its descriptor (" + built.get(0) + ")",
                built.get(0).toString().contains(new BlockPos(1, 0, 0).toString())
                        && built.get(0).toString().contains("minecraft:stone")
                        && !built.get(0).toString().contains("{\"a\":1}"));
        report("...and the second carries its own position, descriptor and nbt (" + built.get(1) + ")",
                built.get(1).toString().contains(new BlockPos(3, 0, 0).toString())
                        && built.get(1).toString().contains("minecraft:dirt")
                        && built.get(1).toString().contains("{\"a\":1}"));
        check("...and the unavailable count is what the caller must report", 1,
                countUnavailable(planOffsets, samples));
        try {
            call(parts, null, planOffsets, new ArrayList<>(List.of(samples.get(0))));
            report("a sample list of the wrong length is refused rather than silently zipped", false);
        } catch (RuntimeException exception) {
            report("a sample list of the wrong length is refused (" + exception.getClass().getSimpleName() + ")",
                    true);
        }
    }

    private static int countUnavailable(List<BlockPos> offsets, List<Object> samples) {
        int count = 0;
        for (int i = 0; i < offsets.size(); i++) {
            if (samples.get(i) == null) {
                count++;
            }
        }
        return count;
    }

    // ================================================================== the tile-entity capture

    /**
     * The tile-entity capture, offline: the original wrote the tile's NBT with {@code x}/{@code y}/{@code z}
     * removed ({@code PlayerStructureSelectionHelper.java:165-175}). 1.20.1's equivalent source is
     * {@code BlockEntity.saveWithoutMetadata()}, and the JSON text is what can be asserted here — the block
     * entity itself needs a level.
     */
    private static void nbtCapture() {
        section("AA6. the tile entity's NBT becomes a JSON object without its coordinates");

        Class<?> type = find(EXPORT);
        Method nbtToJson = methodOrNull(type, "nbtToJson", CompoundTag.class);
        Method withoutCoordinates = methodOrNull(type, "withoutCoordinates", CompoundTag.class);
        if (nbtToJson == null || withoutCoordinates == null) {
            report("SelectionExport's own API is present (nbtToJson/withoutCoordinates)", false);
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt("x", 7);
        tag.putInt("y", 8);
        tag.putInt("z", 9);
        tag.putString("id", "modular_machinery_reborn:item_input_hatch");
        tag.putString("Mode", "input");
        tag.putLong("energy", 1234L);
        tag.putBoolean("formed", true);
        tag.putDouble("ratio", 0.5D);
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.IntTag.valueOf(1));
        list.add(net.minecraft.nbt.IntTag.valueOf(2));
        tag.put("tiers", list);
        tag.put("slots", new IntArrayTag(new int[] {1, 2, 3}));

        Object stripped = call(withoutCoordinates, null, tag);
        // Asked of the tag itself rather than of its text: "energy:" contains "y:", which a text search for the
        // coordinate keys matches (this assertion's own first draft failed on exactly that).
        report("...the coordinates are gone ("
                        + (((CompoundTag) stripped).contains("x") ? "x " : "")
                        + (((CompoundTag) stripped).contains("y") ? "y " : "")
                        + (((CompoundTag) stripped).contains("z") ? "z " : "")
                        + "keys left: " + stripped + ")",
                !((CompoundTag) stripped).contains("x") && !((CompoundTag) stripped).contains("y")
                        && !((CompoundTag) stripped).contains("z"));
        String json = String.valueOf(call(nbtToJson, null, stripped));
        // Keys sorted, because CompoundTag is backed by a HashMap and the original's order was undefined
        // anyway; JSON objects are unordered, so sorting changes nothing but the file's readability. An NBT
        // boolean IS a byte in 1.20.1 and JSON has no distinction, so `formed` flattens to 1 — which is what the
        // original's own NBT-to-JSON serializer did with a byte as well.
        check("the NBT as a JSON object (keys sorted, arrays as arrays, types flattened as JSON has no byte/short)",
                "{\"Mode\":\"input\",\"energy\":1234,\"formed\":1,"
                        + "\"id\":\"modular_machinery_reborn:item_input_hatch\",\"ratio\":0.5,"
                        + "\"slots\":[1,2,3],\"tiers\":[1,2]}", json);
        report("...which is a JSON object the fragment can embed",
                com.google.gson.JsonParser.parseString(json).isJsonObject());
        check("a compound with nothing left is an empty JSON object", "{}",
                String.valueOf(call(nbtToJson, null, call(withoutCoordinates, null, new CompoundTag()))));
    }

    // ================================================================== the file name

    /** The original's {@code machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json} and its {@code " (N)"} collision suffix. */
    private static void fileNameScheme() {
        section("AA7. the file name, the collision suffix, and UTF-8");

        Class<?> type = find(WRITER);
        Method fileNameFor = methodOrNull(type, "fileNameFor", String.class, LocalDateTime.class);
        Method write = methodOrNull(type, "write", Path.class, String.class, String.class);
        if (fileNameFor == null || write == null) {
            report("MachineFragmentWriter's own API is present (fileNameFor/write)", false);
            return;
        }

        check("the file name is the original's scheme",
                "machine-Alice-2026-10-02_03.04.05.json",
                String.valueOf(call(fileNameFor, null, "Alice", LocalDateTime.of(2026, 10, 2, 3, 4, 5))));

        try {
            Path dir = Files.createTempDirectory("mmverify-construct");
            String name = String.valueOf(call(fileNameFor, null, "Alice", LocalDateTime.of(2026, 10, 2, 3, 4, 5)));
            String json = "{\n  \"parts\": [],\n  \"note\": \"\u673a\u5668\"\n}";
            Path first = (Path) call(write, null, dir, name, json);
            check("the first write uses the plain name", name, first.getFileName().toString());
            report("...the file exists", Files.isRegularFile(first));
            check("...and its bytes are UTF-8, exactly as written", json,
                    Files.readString(first, StandardCharsets.UTF_8));

            Path second = (Path) call(write, null, dir, name, json);
            // The original's collision counter starts at zero and is incremented AFTER it is used
            // (PlayerStructureSelectionHelper.java:108-112), so the first collision is " (0)".
            check("the second write of the same name collides as ' (0)'",
                    "machine-Alice-2026-10-02_03.04.05 (0).json", second.getFileName().toString());
            check("...and the first file is untouched", json, Files.readString(first, StandardCharsets.UTF_8));
            Path third = (Path) call(write, null, dir, name, json);
            check("the third as ' (1)'", "machine-Alice-2026-10-02_03.04.05 (1).json",
                    third.getFileName().toString());

            // A directory that does not exist yet: the tool has to be able to create the machinery folder.
            Path nested = dir.resolve("machinery").resolve("deeper");
            Path created = (Path) call(write, null, nested, name, json);
            report("a missing directory is created (" + created + ")", Files.isRegularFile(created));

            // The name itself never escapes the directory.
            report("...and the returned path is inside the directory it was given",
                    created.normalize().startsWith(nested.normalize()));
        } catch (Exception exception) {
            report("the writer could be driven on a temporary directory: " + exception, false);
        }
    }

    // ================================================================== the gate

    /**
     * The gate, as arithmetic: the original's {@code !worldIn.isRemote && player.isCreative() &&
     * …canSendCommands(…)} ({@code ItemConstructTool.java:54}). Exactly one of the eight combinations may act,
     * and the client never does.
     */
    private static void permissionGate() {
        section("AA8. the gate: server side, creative, and allowed to use commands");

        Class<?> type = find(ITEM);
        Method maySelect = methodOrNull(type, "maySelect", boolean.class, boolean.class, boolean.class);
        if (maySelect == null) {
            report("ConstructToolItem.maySelect(boolean,boolean,boolean) is present", false);
            return;
        }

        int allowed = 0;
        for (boolean clientSide : List.of(false, true)) {
            for (boolean creative : List.of(false, true)) {
                for (boolean commands : List.of(false, true)) {
                    boolean result = asBoolean(call(maySelect, null, clientSide, creative, commands));
                    boolean expected = !clientSide && creative && commands;
                    if (result) {
                        allowed++;
                    }
                    report("maySelect(clientSide=" + clientSide + ", creative=" + creative + ", commands="
                                    + commands + ") is " + expected, result == expected);
                }
            }
        }
        check("...so exactly one of the eight combinations may select", 1, allowed);
        report("...and a survival player is refused even when allowed to use commands",
                !asBoolean(call(maySelect, null, false, false, true)));
        report("...and an op in creative who may not use commands is refused (the original demands both)",
                !asBoolean(call(maySelect, null, false, true, false)));
    }

    // ================================================================== the packet

    /**
     * The selection sync: the original's {@code PktSyncSelection} wrote a count and then three ints per position
     * ({@code PktSyncSelection.java:39-56}), and the server sent it on every toggle — including the empty list
     * after finalize ({@code PlayerStructureSelectionHelper.java:69-75}).
     */
    private static void packetRoundTrip() {
        section("AA9. the selection sync packet");

        Class<?> type = find(PACKET);
        Constructor<?> fromBuffer = constructorOrNull(type, FriendlyByteBuf.class);
        Constructor<?> fromList = constructorOrNull(type, List.class);
        Method encode = methodOrNull(type, "encode", FriendlyByteBuf.class);
        Method positions = methodOrNull(type, "positions");
        if (fromBuffer == null || fromList == null || encode == null || positions == null) {
            report("SelectionSyncPacket's own API is present (ctor(FriendlyByteBuf)/ctor(List)/encode/positions)",
                    false);
            return;
        }

        for (int count : new int[] {0, 1, 3, 4096}) {
            List<BlockPos> sent = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                sent.add(new BlockPos(i * 3 - 7, 64 - i, i * 5));
            }
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            Object message = construct(fromList, sent);
            call(encode, message, buffer);
            // The original's shape: a count, then three ints per position (PktSyncSelection.java:49-56). Three
            // ints and not one packed long, because writeBlockPos gives y 12 bits and would silently change a
            // position above 2047 (see SelectionSyncPacket).
            check("a packet of " + count + " position(s) is " + (4 + 12 * count) + " bytes (a count, then three"
                            + " ints per position)", 4 + 12 * count, buffer.readableBytes());
            Object decoded = construct(fromBuffer, buffer);
            List<Object> decodedPositions = asList(call(positions, decoded));
            boolean same = decodedPositions.equals(new ArrayList<>(sent));
            // Reported compactly: a 4096-position check whose message prints both lists fills the evidence file
            // with 280 KB of coordinates and hides the next failure.
            report("...and decodes to the same " + count + " position(s) in the same order"
                            + (same ? "" : " — first difference: " + firstDifference(sent, decodedPositions)),
                    same);
            check("...leaving nothing unread", 0, buffer.readableBytes());
        }

        // A truncated buffer must not quietly produce a shorter selection.
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeInt(3);
        truncated.writeLong(0L);
        try {
            construct(fromBuffer, truncated);
            report("a truncated packet is refused rather than silently yielding a partial selection", false);
        } catch (RuntimeException exception) {
            report("a truncated packet is refused (" + exception.getClass().getSimpleName() + ")", true);
        }
    }

    /**
     * The structural half of the central finding: <b>the tool places nothing</b>.
     *
     * <p>Read out of the class file, the same cheap way section Z8b reads the builder's member list: no call to
     * any of the world-writing methods may appear in {@code ConstructToolItem}. If a future release wants a real
     * block placer, this check is the place that says so out loud instead of letting it appear silently.
     */
    private static void placesNothing() {
        section("AA10. the tool places nothing (there is no build half in the original)");

        String disassembly = disassemble(ITEM);
        report("javap read ConstructToolItem (" + (disassembly == null ? "no output"
                : disassembly.length() + " chars") + ")", disassembly != null && !disassembly.isBlank());
        if (disassembly == null || disassembly.isBlank()) {
            return;
        }
        for (String forbidden : List.of("setBlock", "setBlockAndUpdate", "destroyBlock", "removeBlock",
                "placeBlock", "fillBlocks")) {
            report("...and it never calls " + forbidden, !disassembly.contains(forbidden));
        }
        report("...while it does override useOn (the click that the original handled in onItemUse)",
                disassembly.contains("useOn("));
    }

    // ================================================================== small helpers

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Method methodOrNull(Class<?> type, String name, Class<?>... parameters) {
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Constructor<?> constructorOrNull(Class<?> type, Class<?>... parameters) {
        if (type == null) {
            return null;
        }
        try {
            return type.getConstructor(parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object call(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object construct(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object newSelection(Class<?> type) {
        return construct(constructorOrNull(type));
    }

    private static Object sampleFor(String descriptor, String nbtJson) {
        return construct(constructorOrNull(find(SAMPLE), String.class, String.class), descriptor, nbtJson);
    }

    private static Object part(BlockPos position, String descriptor, String nbtJson) {
        return construct(constructorOrNull(find(PART), BlockPos.class, String.class, String.class),
                position, descriptor, nbtJson);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value == null ? new ArrayList<>() : new ArrayList<>((List<Object>) value);
    }

    private static List<BlockPos> castPositions(Object value) {
        List<BlockPos> out = new ArrayList<>();
        for (Object entry : asList(value)) {
            out.add((BlockPos) entry);
        }
        return out;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    /** The first index at which two lists of positions differ, for a compact failure message. */
    private static String firstDifference(List<BlockPos> sent, List<Object> decoded) {
        int limit = Math.min(sent.size(), decoded.size());
        for (int i = 0; i < limit; i++) {
            if (!sent.get(i).equals(decoded.get(i))) {
                return "at " + i + ": " + sent.get(i) + " vs " + decoded.get(i);
            }
        }
        return sent.size() == decoded.size() ? "none" : "length " + sent.size() + " vs " + decoded.size();
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean flag && flag;
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    /** {@code javap -p -c -classpath <this harness's classpath> <class>}, or {@code null}. */
    private static String disassemble(String className) {
        String javap = Path.of(System.getProperty("java.home"), "bin", "javap").toString();
        List<String> command = List.of(javap, "-p", "-c", "-classpath", System.getProperty("java.class.path"),
                className);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (java.io.IOException | InterruptedException failure) {
            return null;
        }
    }

    private static Map<String, String> flatJson(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : root.keySet()) {
                out.put(key, root.get(key).getAsString());
            }
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    private static Map<String, String> flatLang(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals > 0) {
                    out.put(trimmed.substring(0, equals), trimmed.substring(equals + 1));
                }
            }
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
        return out;
    }

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
