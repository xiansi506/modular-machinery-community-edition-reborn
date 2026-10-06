package mmverify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineDefinitions;
import com.reborn.modularmachinery.machine.MachineSchema;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static mmverify.ParallelCraftCheck.check;
import static mmverify.ParallelCraftCheck.report;
import static mmverify.ParallelCraftCheck.section;

/**
 * Section Z — 0.28.0 (v2a): the KubeJS machine-definition entry point.
 *
 * <h2>What this section can and cannot prove, stated once</h2>
 *
 * <p><b>It cannot load the KubeJS classes.</b> KubeJS is a mod, not a library: this harness's classpath keeps
 * only what the mod's own classes need, and {@code MachineRegistryEventJS} extends
 * {@code dev.latvian.mods.kubejs.event.EventJS}. The class <b>files</b> are present (Z0 asserts that), but
 * {@code Class.forName} on any of the four dies with
 * {@code NoClassDefFoundError: dev.latvian.mods.kubejs/event/EventJS} — verified with a standalone probe, not
 * assumed. The script-execution half is therefore <b>game-only</b> and is reported as such; the in-game
 * checklist in {@code docs/KJS-配方指南.md} is what closes it.
 *
 * <p><b>It does prove the three things the design turns on</b>, none of which needs KubeJS at runtime:
 *
 * <ol>
 *   <li><b>One schema, two paths.</b> The loader's own private {@code readMachine} (reflection, exactly as
 *       section K drives it) and the strict script entry point {@link MachineSchema#read} are handed the same
 *       defect and must produce <b>byte-identical</b> messages: same sentence, same place. Add a check to the
 *       script path that the loader does not have and this goes red. The scripts' own JSON is what the KubeJS
 *       builder produces, and the builder's class file is checked — by bytecode — not to contain any of the
 *       schema's rejection sentences, i.e. it delegates rather than re-implements.</li>
 *   <li><b>Merge, not replace</b> (the defect the survey predicted). {@link MachineDefinitions} is production
 *       code, reachable without KubeJS, and carries the whole merge contract: a script machine survives one
 *       reload and a second one, an id collision resolves script &gt; config dir &gt; data pack, and deleting
 *       the script deletes the machine.</li>
 *   <li><b>Ordering.</b> Stated as an ordering constraint on the loader's constant pool — {@code MachineSchema}
 *       is referenced before {@code applyScriptLayer}, which is referenced before {@code MachineRegistry} — so a
 *       later edit that applies the script layer before parsing the data pack fails here. Why the script always
 *       runs before the loader in the first place is a fact about KubeJS's own bytecode, read out of the KubeJS
 *       jar in section Z7 and recorded in {@code 迁移日志.md}.</li>
 * </ol>
 */
final class KubeJSMachineCheck {

    /**
     * The one position every fixture uses; (0,0,0) is skipped by the schema, so it has to be non-zero.
     *
     * <p>The accepted blocks are <b>vanilla</b> on purpose. This harness runs against a bootstrapped vanilla
     * registry, so a modded block id resolves to null and {@code BlockMatcher.parse} legitimately refuses it —
     * the same wall sections K and P work inside. The in-game checklist uses the mod's own casings and hatches,
     * which is where the modded descriptors are covered.
     */
    private static final String PARTS =
            "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"}]";

    /**
     * One {@code modifiers} entry, complete — the shape the original's {@code alloy_furnace} used (a vent above
     * the controller doubling every item output). Shared by the two places that need a modifier: the section
     * Z3 body below, and the Rhino driving in section Z10, so a change to the documented shape is one edit.
     */
    private static final String MODIFIER_ENTRY =
            "[{\"elements\":\"minecraft:stone\",\"x\":0,\"y\":1,\"z\":0,\"modifier\":"
                    + "{\"target\":\"item\",\"io\":\"output\",\"operation\":1,\"multiplier\":2.0}}]";

    private static final String PLUGIN_CLASS =
            "com/reborn/modularmachinery/kubejs/ModularMachineryKubeJSPlugin";
    private static final String EVENTS_CLASS =
            "com/reborn/modularmachinery/kubejs/MachineEvents";
    private static final String BUILDER_CLASS =
            "com/reborn/modularmachinery/kubejs/MachineBuilderJS";
    private static final String EVENT_CLASS =
            "com/reborn/modularmachinery/kubejs/MachineRegistryEventJS";
    private static final String KUBEJS_PLUGINS_TXT = "kubejs.plugins.txt";

    private KubeJSMachineCheck() {
    }

    static void kubeJsMachineApi() {
        section("Z. 0.28.0 — the KubeJS machine-definition entry point (v2a)");
        try {
            z0ClassesExist();
            z1CoreFields();
            z2SharedValidation();
            z3UnknownAndDeferredFields();
            z4StrictVersusTolerant();
            z5MergeContract();
            z6BuilderDelegatesAndLoaderIsWired();
            z7PluginWiringAndTheOrderingOfTheReload();
            z8PluginDeclaration();
            z8bBuilderMethodNamesAreUnambiguous();
        } catch (RuntimeException | Error throwable) {
            // Every assertion counts itself, so an escape here would lose the section's remaining count and
            // read as "the harness crashed" rather than "this check failed". Section Y does the same.
            report("section Z aborted: " + throwable, false);
        } finally {
            // Leave the layer as the harness found it: a machine left staged here would be merged into the next
            // reload of this process (and Z5 is the only writer).
            MachineDefinitions.beginCycle();
        }
    }

    // ---------------------------------------------------------------- Z0: the classes are really there

    private static void z0ClassesExist() {
        // Not Class.forName: the KubeJS classes extend KubeJS types, which are not on this classpath (see the
        // class comment). Their class files are what this section can read, and reading them is enough for
        // everything below.
        report("the KubeJS builder's class file is on the classpath", classBytes(BUILDER_CLASS) != null);
        report("the KubeJS machine-event class file is on the classpath", classBytes(EVENT_CLASS) != null);
        report("the KubeJS event group's class file is on the classpath", classBytes(EVENTS_CLASS) != null);
        report("the KubeJS plugin's class file is on the classpath", classBytes(PLUGIN_CLASS) != null);
        // The KubeJS supertypes really are absent, so "the harness cannot run a script" is a stated limit
        // rather than an untested guess. If a future harness puts KubeJS on the classpath, this goes red and the
        // Z2 故事 can be upgraded from "same schema" to "same builder".
        report("...and the KubeJS runtime is deliberately not on this classpath (so the script half is game-only)",
                !kubeJsRuntimePresent());
    }

    private static boolean kubeJsRuntimePresent() {
        try {
            Class.forName("dev.latvian.mods.kubejs.event.EventJS");
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

    // ---------------------------------------------------------------- Z1: the core field set

    private static void z1CoreFields() {
        // This is the JSON the builder produces for a core-field machine; Z6 asserts the builder's own class file
        // really builds it that way.
        String script = "{\"registryname\":\"kubejs_smoke\",\"localizedname\":\"Scripted Smoke\","
                + "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"},"
                + "{\"x\":0,\"y\":1,\"z\":0,\"elements\":"
                + "[\"minecraft:furnace[facing=north]\",\"minecraft:iron_block\"]}]}";
        JsonObject json = JsonParser.parseString(script).getAsJsonObject();
        check("the script-shaped definition carries the core fields and nothing else",
                "[registryname, localizedname, parts]", json.keySet().toString());

        MachineDefinition definition = scriptPath(script);
        check("a script machine's id", "modular_machinery_reborn:kubejs_smoke", definition.id().toString());
        check("...its localized name", "Scripted Smoke", definition.localizedName());
        check("...its part count", 2, definition.pattern().partCount());
        check("...and the blocks a single part accepted",
                "[minecraft:furnace[facing=north], minecraft:iron_block]",
                definition.pattern().positions()
                        .get(new net.minecraft.core.BlockPos(0, 1, 0)).toString());
        check("...and the other part's block", "[minecraft:stone]",
                definition.pattern().positions()
                        .get(new net.minecraft.core.BlockPos(1, -1, 0)).toString());
        // A machine defined without a namespace gets this mod's, exactly as a definition file's registryname does.
        check("a bare registry name gets this mod's namespace", "modular_machinery_reborn:bare",
                scriptPath("{\"registryname\":\"bare\"," + PARTS + "}").id().toString());
        check("a namespaced registry name is kept", "verify:other",
                scriptPath("{\"registryname\":\"verify:other\"," + PARTS + "}").id().toString());

        // Every field the slice does not implement keeps the value a definition that omits it gets, so a script
        // machine behaves like a data-pack machine that wrote none of them.
        check("a script machine runs one copy (no parallel fields)", 1, definition.effectiveParallelCeiling());
        report("...so it is not parallel in practice", !definition.parallelEnabled());
        report("...carries no modifiers", definition.modifiers().isEmpty());
        report("...declares no smart interfaces", !definition.hasSmartInterfaces());
        report("...is not a factory", !definition.hasFactory() && !definition.factoryOnly());
        check("...its failure action is the original's default", "STILL",
                definition.failureAction().name());
        report("...and it does not require a blueprint", !definition.requiresBlueprint());
    }

    // ---------------------------------------------------------------- Z2: one schema, two paths

    private static void z2SharedValidation() {
        String[][] defects = {
                {"{\"registryname\":\"t\"}", "no parts at all"},
                {"{\"registryname\":\"t\",\"parts\":[{\"x\":1,\"y\":-1,\"z\":0}]}",
                        "a part with no 'elements'"},
                {"{\"registryname\":\"t\",\"parts\":\"nope\"}", "'parts' that is not an array"},
                {"{\"registryname\":\"t\",\"parts\":[{\"x\":\"left\",\"y\":0,\"z\":0,"
                        + "\"elements\":\"minecraft:stone\"}]}", "a coordinate that is not a number"},
                {"{\"registryname\":\"t\",\"parts\":[{\"x\":1,\"y\":0,\"z\":0,\"elements\":\"not_a_variable\"}]}",
                        "an element that is neither a block id nor a variable"},
                {"{\"parts\":[{\"x\":1,\"y\":0,\"z\":0,\"elements\":\"minecraft:stone\"}]}",
                        "no registryname"},
                {"{\"registryname\":\"t\",\"localizedname\":\"T\"}", "no 'parts' field"},
        };
        for (String[] defect : defects) {
            String jsonMessage = messageOf(() -> jsonPath(defect[0]));
            String scriptMessage = messageOf(() -> scriptPath(defect[0]));
            // The two callers name the definition differently on purpose — a file id for a data pack, the
            // machine for a script — so "the same error" means: the same sentence once that name is taken out,
            // and each side really says its own name. Anything else in the message has to match exactly, which
            // is what makes a check added to one path and not the other fail here.
            String withoutJsonName = jsonMessage == null ? null
                    : jsonMessage.replace("mmverify:z_json", "<where>");
            String withoutScriptName = scriptMessage == null ? null
                    : scriptMessage.replace("machine 't'", "<where>");
            report("the same sentence from both paths for " + defect[1] + ": "
                            + (withoutJsonName != null && withoutJsonName.equals(withoutScriptName)
                                    ? jsonMessage
                                    : "json=" + jsonMessage + " | script=" + scriptMessage),
                    withoutJsonName != null && withoutJsonName.equals(withoutScriptName));
            report("...and each path names its own definition for " + defect[1],
                    jsonMessage != null && jsonMessage.contains("mmverify:z_json")
                            && scriptMessage != null && scriptMessage.contains("machine 't'"));
        }

        // A valid definition has to come out the same through both stacks, not merely succeed.
        String valid = "{\"registryname\":\"t\",\"localizedname\":\"T\"," + PARTS + "}";
        MachineDefinition fromJson = jsonPath(valid);
        MachineDefinition fromScript = scriptPath(valid);
        check("a valid definition yields the same id through both paths",
                fromJson.id().toString(), fromScript.id().toString());
        check("...the same localized name", fromJson.localizedName(), fromScript.localizedName());
        check("...the same pattern", fromJson.pattern().positions().toString(),
                fromScript.pattern().positions().toString());
        check("...and the same ceiling", fromJson.effectiveParallelCeiling(),
                fromScript.effectiveParallelCeiling());

        // The 'where' a caller passes is what names the definition in the message: a file for a data pack, the
        // machine for a script. Asserted, because "the script author sees which machine failed" rests on it.
        String scriptWhere = messageOf(() -> MachineSchema.read(JsonParser.parseString(
                "{\"registryname\":\"t\"}"), "machine 't'", variables(), true));
        report("a script-side message names the machine: " + scriptWhere,
                scriptWhere != null && scriptWhere.contains("machine 't'"));
        String packWhere = messageOf(() -> jsonPath("{\"registryname\":\"t\"}"));
        report("a data-pack-side message names the file: " + packWhere,
                packWhere != null && packWhere.contains("mmverify:z_json"));
    }

    // ---------------------------------------------------------------- Z3: unknown and deferred fields

    private static void z3UnknownAndDeferredFields() {
        // (1) An unknown field is refused, loudly, and the refusal names the fields that do exist — this is the
        //     "unknown field is rejected loudly" requirement, and it is strictly better than the loader's warning.
        String unknown = messageOf(() -> scriptPath(
                "{\"registryname\":\"t\"," + PARTS + ",\"localyzedname\":\"typo\"}"));
        report("an unknown script field is refused: " + unknown,
                unknown != null && unknown.contains("does not know") && unknown.contains("localyzedname"));
        report("...and the refusal lists the documented fields",
                unknown != null && unknown.contains("registryname") && unknown.contains("parts")
                        && unknown.contains("smart-interfaces"));
        // (2) A "//"-comment key stays legal: it is this project's convention for an inline note.
        report("a \"//\"-note key is accepted",
                messageOf(() -> scriptPath("{\"registryname\":\"t\"," + PARTS
                        + ",\"//note\":\"a note\"}")) == null);
        // (3) v2b (0.31.0): every extended root field the loader evaluates can now be written by a script. Until
        //     0.31.0 this block asserted the opposite — that each one was refused by name — and the deferral
        //     existed so a script could not spell a field whose effect would be silently missing. The fields are
        //     implemented now, so the assertion is inverted rather than deleted: what has to hold is that a script
        //     CAN write each one, and that both paths then agree on what it means. The fault injection that
        //     proves this block can still go red is the field list being narrowed, which is what
        //     fault-injection-0.31.0-*.txt records.
        String extended = "{\"registryname\":\"t\"," + PARTS + ","
                + "\"failure-action\":\"decrease\","
                + "\"requires-blueprint\":true,"
                + "\"max-parallelism\":64,"
                + "\"internal-parallelism\":2,"
                + "\"parallelizable\":true,"
                + "\"has-factory\":true,"
                + "\"factory-only\":false,"
                + "\"max-threads\":4,"
                + "\"modifiers\":" + MODIFIER_ENTRY + ","
                + "\"smart-interfaces\":[{\"type\":\"mode\",\"default\":3.0,\"priority\":7}],"
                + "\"core-threads\":[{\"name\":\"smelter\"},{\"name\":\"pinned\",\"recipes\":[\"t:one\"]}]}";
        report("every extended root field is accepted by a script (0.31.0 inverted this from a refusal)",
                messageOf(() -> scriptPath(extended)) == null);

        // The two paths must agree on the *meaning*, not merely both succeed. Each accessor is compared rather
        // than the JSON text, so "the script wrote the field" and "the field reached the definition" are the
        // same statement — a builder that spelled a key the schema does not read would fail here.
        MachineDefinition extendedScript = scriptPath(extended);
        MachineDefinition extendedPack = jsonPath(extended);
        check("...and both paths read 'failure-action' the same way", extendedPack.failureAction().name(),
                extendedScript.failureAction().name());
        check("...its value is the one the script wrote", "DECREASE",
                extendedScript.failureAction().name());
        // The four boolean fields are compared with report(... == ...) rather than check(...): there is no
        // check(String, boolean, boolean) overload in this harness, and adding one would be a new helper for
        // four call sites. (0.23.0 lost a round to exactly that missing overload — see 必查 4.)
        report("...'requires-blueprint' reads the same on both paths and is the value the script wrote: "
                        + extendedScript.requiresBlueprint(),
                extendedPack.requiresBlueprint() == extendedScript.requiresBlueprint()
                        && extendedScript.requiresBlueprint());
        check("...'max-parallelism'", extendedPack.maxParallelism(), extendedScript.maxParallelism());
        // The ceiling is a separate number from the field: it is min(max-parallelism, max(1, internal-parallelism)),
        // so with internal=2 the ceiling is 2 while the field is 64. Asserting the field is what makes this a
        // check of "the script's value arrived"; asserting the ceiling here would pass even if max-parallelism had
        // never been read. (Both are asserted: this one for the value, the next for the interaction.)
        check("...and it is the written value, not the default", 64, extendedScript.maxParallelism());
        check("...'internal-parallelism'", extendedPack.internalParallelism(),
                extendedScript.internalParallelism());
        check("...and the effective ceiling is the smaller of the two, as the model defines it: "
                        + extendedScript.effectiveParallelCeiling(), 2,
                extendedScript.effectiveParallelCeiling());
        report("...'parallelizable' agrees on both paths, and is the value the script wrote",
                extendedPack.parallelizable() == extendedScript.parallelizable()
                        && extendedScript.parallelizable());
        report("...'has-factory' agrees on both paths, and is the value the script wrote",
                extendedPack.hasFactory() == extendedScript.hasFactory() && extendedScript.hasFactory());
        report("...'factory-only' agrees on both paths, and is the value the script wrote (false)",
                extendedPack.factoryOnly() == extendedScript.factoryOnly() && !extendedScript.factoryOnly());
        check("...'max-threads'", extendedPack.maxThreads(), extendedScript.maxThreads());
        check("...and the thread count is the written one, not the config default", 4,
                extendedScript.maxThreads());
        check("...'modifiers': one entry, at the same position, contributing the same modifier",
                extendedPack.modifiers().toString(), extendedScript.modifiers().toString());
        check("...'smart-interfaces': one type, same name and default",
                extendedPack.smartInterfaces().get(0).type() + "/"
                        + extendedPack.smartInterfaces().get(0).defaultValue(),
                extendedScript.smartInterfaces().get(0).type() + "/"
                        + extendedScript.smartInterfaces().get(0).defaultValue());
        report("...its priority survived", extendedScript.smartInterfaces().get(0).priority() == 7);
        check("...'core-threads': both threads, names and pinned recipes included",
                extendedPack.coreThreads().toString(), extendedScript.coreThreads().toString());
        check("...and the pinned thread really carries its recipe", 1,
                extendedScript.coreThreads().get(1).recipes().size());
        // A script that says nothing must keep the same defaults a data pack gets — the property the old block
        // proved from the other side, and the one a narrower field list would still pass while breaking meaning.
        MachineDefinition bare = scriptPath("{\"registryname\":\"t\"," + PARTS + "}");
        report("a script that writes none of them still gets a data pack's defaults",
                bare.failureAction() == MachineDefinition.DEFAULT_FAILURE_ACTION
                        && !bare.requiresBlueprint()
                        && bare.maxParallelism() == MachineDefinition.DEFAULT_MAX_PARALLELISM
                        && bare.internalParallelism() == MachineDefinition.DEFAULT_INTERNAL_PARALLELISM
                        && bare.parallelizable() == MachineDefinition.DEFAULT_PARALLELIZABLE
                        && bare.modifiers().isEmpty()
                        && !bare.hasSmartInterfaces()
                        && bare.coreThreads().isEmpty());
        // (3b) The deferral list is empty in 0.31.0, and that is a statement about the *set*, not about a count:
        //      the schema no longer carries one. If a future slice reintroduces a deferred field, this check is
        //      the reminder that it also needs the refusal path back.
        report("no root field is deferred any more — the script API covers everything the loader reads",
                MachineSchema.KNOWN_ROOT_FIELDS.stream().noneMatch(MachineSchema.DEFERRED_ROOT_FIELDS::contains));
        // (4) The deferred list may not name a field the schema does not know (a refusal that names a
        //     non-existent field is a lie) and may not name a core field (that would break the API).
        List<String> lying = new ArrayList<>();
        for (String field : MachineSchema.DEFERRED_ROOT_FIELDS) {
            if (!MachineSchema.KNOWN_ROOT_FIELDS.contains(field)) {
                lying.add(field + " (not a known field)");
            }
        }
        for (String field : List.of("registryname", "localizedname", "parts")) {
            if (MachineSchema.DEFERRED_ROOT_FIELDS.contains(field)) {
                lying.add(field + " (a core field)");
            }
        }
        report("the deferred list only names fields the schema knows and the core API does not use: " + lying,
                lying.isEmpty());
        // (5) The data-pack path still evaluates the same fields — the deferral is a property of the script API,
        //     and this is what keeps a later tidy-up from quietly narrowing the data-pack schema too.
        report("the data-pack path still evaluates 'modifiers' (the deferral is script-only)",
                messageOf(() -> jsonPath("{\"registryname\":\"t\"," + PARTS + ",\"modifiers\":"
                        + "[{\"elements\":\"minecraft:stone\",\"x\":0,\"y\":1,\"z\":0,\"modifier\":"
                        + "{\"target\":\"item\",\"io\":\"output\",\"operation\":1,\"multiplier\":2.0}}]}"))
                        == null);
        report("...and 'has-factory'", messageOf(() -> jsonPath(
                "{\"registryname\":\"t\"," + PARTS + ",\"has-factory\":true}")) == null);
        report("...and 'failure-action'", messageOf(() -> jsonPath(
                "{\"registryname\":\"t\"," + PARTS + ",\"failure-action\":\"reset\"}")) == null);
        report("...and 'requires-blueprint'", messageOf(() -> jsonPath(
                "{\"registryname\":\"t\"," + PARTS + ",\"requires-blueprint\":true}")) == null);
    }

    // ---------------------------------------------------------------- Z4: strict versus tolerant

    private static void z4StrictVersusTolerant() {
        String typo = "{\"registryname\":\"t\"," + PARTS + ",\"registrynam\":\"t\"}";
        report("strict reading rejects an unknown root field",
                messageOf(() -> readSchema(typo, true)) != null);
        report("tolerant reading accepts the same field (the original's behaviour, kept for data packs)",
                messageOf(() -> readSchema(typo, false)) == null);
        report("the loader's own readMachine stays tolerant", messageOf(() -> jsonPath(typo)) == null);
        report("...and both readings accept a valid definition",
                messageOf(() -> readSchema("{\"registryname\":\"t\"," + PARTS + "}", true)) == null
                        && messageOf(() -> readSchema("{\"registryname\":\"t\"," + PARTS + "}", false)) == null);
        // Tolerant mode must still report the unknown field rather than swallow it; the loader's log line is not
        // capturable here, so what is asserted is that the tolerant path does not silently *become* strict.
        report("tolerant mode does not become strict when several unknown fields are present",
                messageOf(() -> readSchema("{\"registryname\":\"t\"," + PARTS
                        + ",\"a\":1,\"b\":2}", false)) == null);
        report("...while strict mode refuses them all at once",
                messageOf(() -> readSchema("{\"registryname\":\"t\"," + PARTS + ",\"a\":1,\"b\":2}", true))
                        != null);
    }

    // ---------------------------------------------------------------- Z5: the merge contract

    private static void z5MergeContract() {
        // Start from an empty layer: another section's fixtures must not leak into this one's arithmetic.
        MachineDefinitions.beginCycle();
        // A data pack with two machines: one the script will shadow, one it will not.
        Map<ResourceLocation, MachineDefinition> dataPack = new LinkedHashMap<>();
        dataPack.put(id("from_pack_kept"), jsonPath("{\"registryname\":\"from_pack_kept\"," + PARTS + "}"));
        dataPack.put(id("from_pack_shadowed"), jsonPath(
                "{\"registryname\":\"from_pack_shadowed\",\"localizedname\":\"from the data pack\","
                        + PARTS + "}"));

        // --- reload 1: two scripts define three machines, one of them colliding.
        MachineDefinitions.beginCycle();
        stagedScriptPath("{\"registryname\":\"from_pack_shadowed\",\"localizedname\":\"from the script\","
                + PARTS + "}");
        stagedScriptPath("{\"registryname\":\"from_script_only\"," + PARTS + "}");
        check("the script layer staged two definitions in cycle 1", 2, MachineDefinitions.stagedCount());

        Map<ResourceLocation, MachineDefinition> merged = merge(dataPack);
        check("after reload 1 there are three machines (2 data pack + 1 new, one shadowed)",
                3, merged.size());
        report("the data pack's untouched machine survives", merged.containsKey(id("from_pack_kept")));
        report("the script-only machine is present", merged.containsKey(id("from_script_only")));
        report("the script's definition won the collision",
                merged.containsKey(id("from_pack_shadowed"))
                        && "from the script".equals(merged.get(id("from_pack_shadowed")).localizedName()));
        check("the shadowed id was reported", "[modular_machinery_reborn:from_pack_shadowed]",
                MachineDefinitions.shadowedIds(dataPack).toString());
        check("...and applyScriptLayer returns the same ids",
                MachineDefinitions.shadowedIds(dataPack).toString(),
                MachineDefinitions.applyScriptLayer(new LinkedHashMap<>(dataPack)).toString());

        // --- reload 2: the very same scripts stage the very same machines. This is the defect the survey
        //     predicted ("a naive push is wiped by the next reload"), so it is asserted as an equality of the
        //     whole resulting table rather than as a count.
        Map<ResourceLocation, MachineDefinition> afterFirst = new LinkedHashMap<>(merged);
        MachineDefinitions.beginCycle();
        stagedScriptPath("{\"registryname\":\"from_pack_shadowed\",\"localizedname\":\"from the script\","
                + PARTS + "}");
        stagedScriptPath("{\"registryname\":\"from_script_only\"," + PARTS + "}");
        Map<ResourceLocation, MachineDefinition> second = merge(dataPack);
        check("the script machine is still there after a second reload (same ids, same order)",
                afterFirst.keySet().toString(), second.keySet().toString());
        report("...and it is still the script's definition, not the data pack's",
                "from the script".equals(second.get(id("from_pack_shadowed")).localizedName()));
        report("...and the script-only machine did not displace anything",
                second.containsKey(id("from_pack_kept")) && second.containsKey(id("from_script_only")));

        // --- reload 3: one script was deleted. What must go is the <b>script's</b> machine; for an id the data
        //     pack also defines, "gone" means the data pack's definition is the one in the table again.
        MachineDefinitions.beginCycle();
        stagedScriptPath("{\"registryname\":\"from_script_only\"," + PARTS + "}");
        check("the script layer holds only the surviving script's machine", 1,
                MachineDefinitions.stagedCount());
        check("the baseline really is the two data-pack machines", 2, dataPack.size());
        report("...and the shadowing machine is one of them", dataPack.containsKey(id("from_pack_shadowed")));
        Map<ResourceLocation, MachineDefinition> baselineCopy = new LinkedHashMap<>(dataPack);
        check("a copy of the baseline still has two machines", 2, baselineCopy.size());
        MachineDefinitions.applyScriptLayer(baselineCopy);
        check("...applying a layer that no longer mentions the shadowed id neither restores nor removes "
                        + "anything: the data pack's two plus the surviving script's one",
                3, baselineCopy.size());
        check("...and the data pack's own definition of the shadowed id is the one in the table",
                "from the data pack", baselineCopy.get(id("from_pack_shadowed")).localizedName());
        Map<ResourceLocation, MachineDefinition> third = merge(dataPack);
        report("the shadowing script's definition is gone", !"from the script".equals(
                third.get(id("from_pack_shadowed")).localizedName()));
        report("...and the data pack's definition of that id is back, unshadowed",
                "from the data pack".equals(third.get(id("from_pack_shadowed")).localizedName()));
        report("...and the surviving script's machine is untouched", third.containsKey(id("from_script_only")));
        check("...so the table is the two data-pack machines plus one", 3, third.size());

        // --- ...and for a machine <b>only</b> a script ever defined, deleting that script really does remove it
        //     from the table. This is the requirement stated without the collision in the way.
        Map<ResourceLocation, MachineDefinition> withoutScript = merge(dataPack);
        report("a machine no script defined is not in the table (the control)",
                !withoutScript.containsKey(id("gone")));
        MachineDefinitions.beginCycle();
        stagedScriptPath("{\"registryname\":\"gone\"," + PARTS + "}");
        report("...it is staged", merge(dataPack).containsKey(id("gone")));
        MachineDefinitions.beginCycle();
        report("...and with the script deleted it is not in the table at all",
                !merge(dataPack).containsKey(id("gone")));
        report("...while every data-pack machine is untouched",
                merge(dataPack).keySet().toString().equals(dataPack.keySet().toString()));

        // --- two scripts claiming one id: the later definition wins, and the layer holds one machine.
        MachineDefinitions.beginCycle();
        stagedScriptPath("{\"registryname\":\"both\",\"localizedname\":\"first\"," + PARTS + "}");
        stagedScriptPath("{\"registryname\":\"both\",\"localizedname\":\"second\"," + PARTS + "}");
        check("two scripts claiming one id stage one definition", 1, MachineDefinitions.stagedCount());
        check("...and the later one wins", "second", MachineDefinitions.staged(id("both")).localizedName());

        // --- a reload with no scripts at all empties the layer, which is what makes a deleted script's machine
        //     disappear rather than linger.
        MachineDefinitions.beginCycle();
        check("a reload with no scripts stages nothing", 0, MachineDefinitions.stagedCount());
        check("...and merging it leaves the data pack untouched", dataPack.keySet().toString(),
                merge(dataPack).keySet().toString());

        // --- a script-defined machine gets no controller block, and the consequence is not left to a sentence
        //     in a design document. It cannot be asked through ModBlocks: that class's static initialiser builds
        //     DeferredRegisters and resolves FMLPaths, so loading it here dies with
        //     "NullPointerException: FMLPaths.get() is null" — verified, and the same wall the harness README
        //     records for section Y. What is asserted instead is the shape of the answer: the script path never
        //     registers anything, the per-machine list is a construction-time constant, and the loader says out
        //     loud what such a machine gets.
        byte[] definitions = classBytes("com/reborn/modularmachinery/machine/MachineDefinitions");
        report("the script-layer holder never registers a block or an item (D8)",
                definitions != null && !containsConstant(definitions, "DeferredRegister")
                        && !containsConstant(definitions, "block/ModBlocks"));
        byte[] directory = classBytes("com/reborn/modularmachinery/machine/MachineDirectory");
        report("the per-machine controller list comes from the config-directory scan, which runs at "
                        + "construction and cannot see a script",
                directory != null && containsConstant(directory, "scanMachinePaths"));
        byte[] loader = classBytes("com/reborn/modularmachinery/machine/MachineLoader");
        report("...and the loader says what a machine with no controller block of its own gets, for data-pack "
                        + "and script machines alike",
                loader != null && containsConstant(loader,
                        "machine(s) have no controller block of their own"));
        report("...and that message mentions the script case, so the answer is not left to a design document",
                loader != null && containsConstant(loader, "blocks are registered before any script runs"));
    }

    // ---------------------------------------------------------------- Z6: the builder delegates, the loader is wired

    private static void z6BuilderDelegatesAndLoaderIsWired() {
        byte[] builder = classBytes(BUILDER_CLASS);
        if (builder == null) {
            report("the builder's class file was found", false);
            return;
        }
        // The one claim that makes "same quality of error" a property rather than a promise: for a defect the
        // schema can be reached with, the builder holds no rejection sentence of its own, so the complaint must
        // come from the schema. The candidate list is filtered against the schema's own class file, so a sentence
        // that exists only in the builder is not counted here — the builder does refuse a *malformed call*
        // ("coordinate was given no value") and that is Java-side type handling, not a schema rule.
        byte[] schema = classBytes("com/reborn/modularmachinery/machine/MachineSchema");
        String[] candidates = {
                "must be one of", "must be a whole number", "must be an array", "cannot be negative",
                "has no parts besides the controller", "does not know", "does not implement yet",
                "must be true or false", "above its 'max-parallelism'",
        };
        List<String> shared = new ArrayList<>();
        for (String sentence : candidates) {
            if (containsConstant(schema, sentence)) {
                shared.add(sentence);
            }
        }
        report("the schema's own rejection sentences were found in its class file (the filter is not empty): "
                        + shared,
                shared.size() >= 6);
        List<String> reimplemented = new ArrayList<>();
        for (String sentence : shared) {
            if (containsConstant(builder, sentence)) {
                reimplemented.add(sentence);
            }
        }
        report("the builder re-implements none of the schema's rejection sentences: " + reimplemented,
                reimplemented.isEmpty());
        // ...and it really calls the schema, in strict mode (the two-argument read is the tolerant one).
        report("the builder calls MachineSchema.read",
                containsConstant(builder, "com/reborn/modularmachinery/machine/MachineSchema"));
        report("...and it stages what it built",
                containsConstant(builder, "com/reborn/modularmachinery/machine/MachineDefinitions")
                        && containsConstant(builder, "stage"));
        // The builder's register() must read the schema in STRICT mode: that is what makes an unknown field an
        // error on the script side instead of the loader's warning. The harness cannot run the builder (see the
        // class comment), so this is read from the bytecode. javap's rendering, once the whitespace is gone, is
        //     aload_0 getfield json:… aload_0 getfield where:… invokestatic Map.of:()… 11: iconst_1
        //     12: invokestatic #124 // Method …MachineSchema.read:(…)…
        // — i.e. the literal is separated from the invocation by the instruction's own byte offset, so the
        // pattern has to allow that. Passing `false` there is caught here and nowhere else offline; the in-game
        // checklist has the same case.
        String builderDisassembly = disassemble("com.reborn.modularmachinery.kubejs.MachineBuilderJS");
        String register = builderDisassembly == null ? null : builderDisassembly.replaceAll("\\s+", "");
        int strictFlag = register == null ? -1
                : firstMatch(register, "iconst_1[0-9]*:invokestatic");
        int tolerantFlag = register == null ? -1
                : firstMatch(register, "iconst_0[0-9]*:invokestatic");
        int schemaCall = register == null ? -1
                : firstMatch(register, "MachineSchema\\.read:\\(Lcom/google/gson/JsonElement;");
        report("the builder calls MachineSchema.read with strict=true and never with false "
                        + "(iconst_1+static call at " + strictFlag + ", iconst_0+static call at "
                        + tolerantFlag + ", the read itself at " + schemaCall + "; text before the read: "
                        + before(register, "iconst_1", 10) + ")",
                strictFlag >= 0 && schemaCall > strictFlag && (tolerantFlag < 0 || tolerantFlag < strictFlag));        report("...and never touches ModBlocks (it runs while scripts run, before blocks matter)",
                !containsConstant(builder, "block/ModBlocks"));
        report("the plugin never touches ModBlocks either",
                !containsConstant(classBytes(PLUGIN_CLASS), "block/ModBlocks"));

        // The loader's wiring. The ordering half cannot be read out of the constant pool — its layout is the
        // compiler's business, not the program's — so the loader is disassembled and the call sites are compared
        // in `apply`'s own instruction order. That is the check the "naive push" defect fails: applying the
        // script layer before parsing the data pack moves its call in front of readMachine's.
        byte[] loader = classBytes("com/reborn/modularmachinery/machine/MachineLoader");
        if (loader == null) {
            report("the loader's class file was found", false);
            return;
        }
        report("the loader reads definitions through MachineSchema (one schema, two paths)",
                containsConstant(loader, "com/reborn/modularmachinery/machine/MachineSchema"));
        report("the loader applies the KubeJS script layer", containsConstant(loader, "applyScriptLayer"));
        report("the loader replaces the registry (which is why the merge has to live there)",
                containsConstant(loader, "MachineRegistry"));
        report("the loader does not decide anything about KubeJS itself — it reads the staged layer only",
                !containsConstant(loader, "dev/latvian"));

        String disassembly = disassemble("com.reborn.modularmachinery.machine.MachineLoader");
        report("javap ran on MachineLoader (" + (disassembly == null ? "no output" : disassembly.length()
                        + " chars") + ")",
                disassembly != null && !disassembly.isBlank());
        if (disassembly == null) {
            return;
        }
        // Compared inside `apply`'s own body: the loader's other methods (and the whole-class disassembly) would
        // otherwise let a mention in a different method stand in for a call in this one.
        String apply = disassembly.replaceAll("\\s+", "");
        report("javap showed apply()'s instructions (" + apply.length() + " chars of the disassembly)",
                !apply.isBlank());
        // Each of the three references occurs exactly once in this class's disassembly — readMachine and
        // applyScriptLayer only inside apply(), replace only inside apply() — so their positions in the text are
        // their positions in the instruction stream. That is what makes this an ordering assertion and not a
        // spelling one: moving the script-layer call above the parse loop fails it.
        //
        // The searched form is javap's `// Method …` reference, with the class name left out: javap abbreviates
        // a call to an overload in the class it is disassembling — the observed text is literally
        // `// Method readMachine:(…` for the loader's own private method, while a foreign owner keeps its full
        // name. Searching for the method name plus javap's colon therefore matches either spelling, and the
        // colon is what keeps `readMachine` from also matching `readMachine2`.
        report("the disassembly's call-site shapes are the ones this check looks for: "
                        + shapeOf(apply, "readMachine") + " / " + shapeOf(apply, "applyScriptLayer") + " / "
                        + shapeOf(apply, "replace"),
                true);
        int applyRead = apply.indexOf("readMachine:");
        int applyLayer = apply.indexOf("applyScriptLayer:");
        int replace = apply.indexOf("MachineRegistry.replace:");
        report("all three call sites are visible in the disassembly (readMachine " + applyRead
                        + ", applyScriptLayer " + applyLayer + ", replace " + replace + ")",
                applyRead >= 0 && applyLayer >= 0 && replace >= 0);
        report("the script layer is applied after the data pack has been parsed "
                        + "(readMachine at " + applyRead + " < applyScriptLayer at " + applyLayer + ")",
                applyRead >= 0 && applyLayer > applyRead);
        report("...and before the registry is replaced, so the merged table is what the registry sees "
                        + "(applyScriptLayer at " + applyLayer + " < replace at " + replace + ")",
                applyLayer >= 0 && replace > applyLayer);
    }

    /**
     * The instructions of one method out of {@code javap -p -c} output: from its declaration line to the next
     * line that starts a new member.
     *
     * <p><b>All whitespace is removed.</b> javap wraps a long method reference at a fixed column, and it wraps
     * <i>inside</i> the identifier — the observed break is {@code "rea"} / {@code "dMachine"} — so collapsing
     * runs of whitespace is not enough: a search for {@code readMachine} would still miss. With the gaps gone,
     * a call site is a contiguous string again.
     */
    private static String methodBody(String disassembly, String declaration) {
        int start = disassembly.indexOf(declaration);
        if (start < 0) {
            return null;
        }
        String[] lines = disassembly.substring(start).split("\n");
        // Skip the declaration line itself, then stop at the first line that starts a new member: javap renders
        // members at two spaces of indentation and instructions at six or more.
        int end = lines.length;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("  ") && !line.startsWith("      ") && !line.isBlank()) {
                end = i;
                break;
            }
        }
        return String.join("\n", java.util.Arrays.copyOfRange(lines, 0, end)).replaceAll("\\s+", "");
    }

    /**
     * {@code javap -p -c -classpath <jar> <class>}, or {@code null} when it could not run.
     *
     * <p>The classpath is the same one this harness runs on, so every class the mod's own bytecode mentions is
     * resolvable. {@code javap} ships with the JDK the harness is running in, so no tool outside the build is
     * needed.
     */
    private static String disassemble(String className) {
        String javap = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "javap").toString();
        List<String> command = List.of(javap, "-p", "-c", "-classpath", System.getProperty("java.class.path"),
                className);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (java.io.IOException | InterruptedException failure) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Z7: the plugin's wiring

    private static void z7PluginWiringAndTheOrderingOfTheReload() {
        byte[] plugin = classBytes(PLUGIN_CLASS);
        if (plugin == null) {
            report("the plugin's class file was found", false);
            return;
        }
        // A class that overrides a hook has to name it, and naming it means the UTF-8 constant is in the pool.
        for (String hook : List.of("onServerReload", "registerEvents", "registerBindings",
                "registerRecipeSchemas")) {
            report("the plugin implements " + hook + "()", containsConstant(plugin, hook));
        }
        report("...and delegates the machine event to MachineEvents, so the sequence has a testable home",
                containsConstant(plugin, "com/reborn/modularmachinery/kubejs/MachineEvents"));
        // 0.28.1: what registerBindings publishes has to be a wrapper.
        //
        // A constant-pool search is not enough here, and the first fault injection proved it: with the body
        // reverted to the raw group but the `import` left in place, the pool still contains the class name, so a
        // "containsConstant" assertion stayed green while the defect was back. javap's text turned out to be no
        // better a place to look (its member/attribute layout is the tool's business, and a search for the bare
        // method name lands in the LocalVariableTable at the end of its output). This reads the class file
        // directly instead: the class constant for EventGroupWrapper must exist, and registerBindings' own code
        // must carry the `new` instruction that references it.
        int wrapperConstructions = newInstructionIn(plugin, "registerBindings",
                "(Ldev/latvian/mods/kubejs/script/BindingsEvent;)V",
                "dev/latvian/mods/kubejs/event/EventGroupWrapper");
        report("registerBindings' code constructs KubeJS's EventGroupWrapper rather than publishing the raw"
                        + " group (the 0.28.0 defect; a pool search alone misses this, as injection A showed)"
                        + " — " + wrapperConstructions
                        + (parseFailure == null ? "" : " (the class could not be read: " + parseFailure + ")"),
                wrapperConstructions > 0);
        report("...and it still never touches ModBlocks (it runs while scripts run, before blocks matter)",
                !containsConstant(plugin, "block/ModBlocks"));

        // The sequence that makes a deleted script really delete its machine: the cycle is opened before the
        // event is posted. Read out of MachineEvents' own instructions rather than its constant pool, because
        // pool layout is the compiler's business and instruction order is the program's.
        byte[] machineEvents = classBytes("com/reborn/modularmachinery/kubejs/MachineEvents");
        report("MachineEvents exists and mentions both halves of the sequence",
                machineEvents != null && containsConstant(machineEvents, "beginCycle")
                        && containsConstant(machineEvents, "MachineRegistryEventJS"));
        String disassembly = disassemble("com.reborn.modularmachinery.kubejs.MachineEvents");
        report("javap ran on MachineEvents (" + (disassembly == null ? "no output" : disassembly.length()
                        + " chars") + ")",
                disassembly != null && !disassembly.isBlank());
        String post = disassembly == null ? null : disassembly.replaceAll("\\s+", "");
        report("javap showed MachineEvents' instructions", post != null && !post.isBlank());
        if (post != null) {
            // Same reasoning as the loader: beginCycle is called from post() and from nowhere else in this
            // class, so its position is a call position. The event's instantiation is searched for as
            // `<class>."<init>"`, which javap writes once, in post() — the bare class name also appears in the
            // field declaration and in a lambda's own method, both of which sit earlier in the output for
            // reasons that have nothing to do with the order the two statements run in.
            int clear = post.indexOf("MachineDefinitions.beginCycle:");
            int dispatch = post.indexOf("MachineRegistryEventJS.\"<init>\"");
            report("post() clears the script layer before it instantiates the event "
                            + "(beginCycle at " + clear + " < new MachineRegistryEventJS at " + dispatch + ")",
                    clear >= 0 && dispatch > clear);
        }

        // The ordering evidence itself: KubeJS calls onServerReload from ServerScriptManager.wrapResourceManager,
        // whose one host is the mixin on WorldLoader$PackConfig#createResourceManager — the point a server start
        // and /reload both pass through, and one that is inside ReloadableServerResources#loadResources, before
        // Forge collects AddReloadListenerEvent's listeners. The KubeJS jar is not on this classpath, so the
        // facts are read from the checkout's own jar when it is present, and reported as unverified otherwise.
        java.nio.file.Path kubeJsJar = findKubeJsJar();
        report("the KubeJS jar was found, so the reload-order chain is checked against KubeJS's own bytecode",
                kubeJsJar != null);
        if (kubeJsJar == null) {
            return;
        }
        byte[] serverScriptManager = jarEntry(kubeJsJar,
                "dev/latvian/mods/kubejs/server/ServerScriptManager.class");
        report("ServerScriptManager loads the scripts and calls the plugin hook (onServerReload)",
                serverScriptManager != null && containsConstant(serverScriptManager, "onServerReload"));
        byte[] packConfigMixin = jarEntry(kubeJsJar,
                "dev/latvian/mods/kubejs/core/mixin/common/inject_resources/WorldLoaderPackConfigMixin.class");
        report("...and wrapResourceManager is entered from the mixin on "
                        + "WorldLoader$PackConfig.createResourceManager, the point /reload passes through",
                packConfigMixin != null && containsConstant(packConfigMixin, "createResourceManager")
                        && containsConstant(packConfigMixin, "wrapResourceManager"));
        byte[] refmap = jarEntry(kubeJsJar, "kubejs-common-refmap.json");
        report("...and that mixin's target really is PackConfig#createResourceManager (KubeJS's own refmap)",
                refmap != null && containsConstant(refmap, "WorldLoader$PackConfig;m_214399_"));
    }

    /**
     * The KubeJS jar, if this machine has one.
     *
     * <p>The search walks up from the working directory instead of hard-coding an absolute path, so it keeps
     * working when the checkout moves; the only literal it needs is the file name.
     */
    private static java.nio.file.Path findKubeJsJar() {
        java.nio.file.Path candidate = java.nio.file.Paths.get(System.getProperty("user.dir", "."))
                .toAbsolutePath();
        while (candidate != null) {
            java.nio.file.Path jar = candidate.resolve("modular-machinery-reborn/libs/kubejs-1.20.1.jar");
            if (java.nio.file.Files.isRegularFile(jar)) {
                return jar;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static byte[] jarEntry(java.nio.file.Path jar, String entry) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry found = zip.getEntry(entry);
            if (found == null) {
                return null;
            }
            try (java.io.InputStream stream = zip.getInputStream(found)) {
                return stream.readAllBytes();
            }
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Z8: the plugin declaration

    private static void z8PluginDeclaration() {
        // 0.27.1 fixed this: the declaration has to sit at the classpath root. Under META-INF/ KubeJS never
        // reads it and the whole bridge silently disappears again, so its location is asserted, not assumed.
        java.net.URL root = KubeJSMachineCheck.class.getClassLoader().getResource(KUBEJS_PLUGINS_TXT);
        report("kubejs.plugins.txt is at the classpath root (the 0.27.1 fix)", root != null);
        java.net.URL wrong = KubeJSMachineCheck.class.getClassLoader()
                .getResource("META-INF/" + KUBEJS_PLUGINS_TXT);
        report("...and not under META-INF/ (where KubeJS would not read it)", wrong == null);
        if (root == null) {
            return;
        }
        try (java.io.InputStream stream = root.openStream()) {
            String text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            report("...and it declares this mod's plugin: " + text.trim(),
                    text.contains("com.reborn.modularmachinery.kubejs.ModularMachineryKubeJSPlugin"));
        } catch (java.io.IOException exception) {
            report("kubejs.plugins.txt could be read: " + exception, false);
        }
    }

    // ------------------------------------------------- Z8b: one name, one shape

    /**
     * 0.28.2 — the builder must not offer two same-named {@code part} methods.
     *
     * <h2>Why this assertion exists</h2>
     *
     * <p>0.28.1 shipped {@code part(int,int,int,Object...)} <b>and</b> {@code part(List,List,List,Object...)}:
     * two overloads with the same name, the same arity and a varargs tail, both of which Rhino's {@code
     * NativeJavaMethod#findFunction} has to weigh for every {@code .part(…)} a script writes. Which one a call
     * reaches is then decided by conversion weight and by {@code getMethods()}' order — and the JVM
     * specification does not define that order.
     *
     * <p>This is the cheap, deterministic half of the proof and it is deliberately read out of the class file's
     * own member list (through {@code javap}, exactly as section Z6 reads {@code register()} out of the same
     * class): the ambiguity is caught here, with no Rhino, no script and no game, the moment a second
     * {@code part} appears again. Section Z10 is the other half — it drives the real classes through KubeJS's own
     * Rhino and asserts which JSON the documented call produces.
     *
     * <p><b>Not</b> {@code Class.forName} on this class, which section Z's own notes rule out: {@code
     * MachineBuilderJS} extends KubeJS's {@code EventJS} and even {@code initialize=false} resolves the
     * superclass, so it dies with {@code NoClassDefFoundError: dev/latvian/mods/kubejs/event/EventJS} on this
     * classpath (observed). Reading the class file asks the same question without loading anything.
     */
    private static void z8bBuilderMethodNamesAreUnambiguous() {
        String disassembly = disassemble("com.reborn.modularmachinery.kubejs.MachineBuilderJS");
        report("javap read the builder's member list for the overload check ("
                        + (disassembly == null ? "no output" : disassembly.length() + " chars") + ")",
                disassembly != null && !disassembly.isBlank());
        if (disassembly == null) {
            return;
        }

        List<String> partDeclarations = declarationsOf(disassembly, "part");
        List<String> partsDeclarations = declarationsOf(disassembly, "parts");

        report("part(int,int,int,Object...) is declared — the spelling every existing script uses: "
                        + partDeclarations, partDeclarations.size() == 1
                        && partDeclarations.get(0).contains("(int, int, int, java.lang.Object...)"));
        // The heart of it: 0.28.1 declared a second, List-taking `part` in this very class file, and Java's
        // erasure let both exist because their descriptors differ (I vs Ljava/util/List;). A script could not
        // tell them apart by name, arity or varargs — only by conversion weight.
        report("...and it is the class's only 'part' (0.28.1 declared a second, List-taking 'part' here, so which"
                        + " of the two a script reached was Rhino's business): 'part' declared "
                        + partDeclarations.size() + " time(s) " + partDeclarations,
                partDeclarations.size() == 1);
        report("...while the list spelling has its own name: 'parts' declared " + partsDeclarations.size()
                        + " time(s) " + partsDeclarations,
                partsDeclarations.size() == 1
                        && partsDeclarations.get(0).contains("java.util.List"));

        // 0.31.0 (v2b): the eleven extended fields arrived as new method names. The rule that made `parts`
        // necessary applies to every one of them, so it is checked for every one of them rather than trusted.
        //
        // The check is "no two declarations share a descriptor", not "each name is declared once": `modifier` and
        // `smartInterface` legitimately carry an optional-tail overload, and an overload of a *different arity* is
        // not the 0.28.1 defect — Rhino resolves by argument count before it weighs conversions, so there is no
        // tie for the JVM's method order to break. What would be the defect is two declarations with identical
        // parameter types, which is what this catches. (The first draft asserted "exactly one and it contains the
        // prefix", which flagged those two legitimate overloads; section Z10's driving of the real Rhino is what
        // showed the resolution is unambiguous in practice.)
        String[][] expected = {
                {"failureAction", "(java.lang.Object)"},
                {"requiresBlueprint", "(boolean)"},
                {"maxParallelism", "(int)"},
                {"internalParallelism", "(int)"},
                {"parallelizable", "(boolean)"},
                {"hasFactory", "(boolean)"},
                {"factoryOnly", "(boolean)"},
                {"maxThreads", "(int)"},
                // A varargs parameter is declared once, so its descriptor is pinned exactly as the scalars' are.
                {"coreThread", "(java.lang.Object, java.lang.Object...)"},
        };
        List<String> wrong = new ArrayList<>();
        for (String[] pair : expected) {
            List<String> declared = declarationsOf(disassembly, pair[0]);
            if (declared.size() != 1 || !declared.get(0).contains(pair[1])) {
                wrong.add(pair[0] + " declared " + declared.size() + " time(s) as " + declared
                        + " (wanted exactly one with " + pair[1] + ")");
            }
        }
        report("every single-declaration v2b field has exactly one method, with the documented descriptor: "
                        + wrong,
                wrong.isEmpty());
        // The two optional-tail families: the same name may appear twice, but never twice with the same
        // descriptor, and the longer form must really be the longer one.
        String[][] families = {
                {"modifier", "(int, int, int, java.lang.Object, java.lang.Object)"},
                {"smartInterface", "(java.lang.Object, double, int)"},
        };
        for (String[] family : families) {
            List<String> declared = declarationsOf(disassembly, family[0]);
            boolean distinct = new java.util.HashSet<>(declared).size() == declared.size();
            boolean longest = declared.stream().anyMatch(line -> line.contains(family[1]));
            report("'" + family[0] + "' declares " + declared.size() + " distinct overloads, one of them the"
                            + " documented " + family[1] + ": distinct=" + distinct + ", longest-present="
                            + longest,
                    declared.size() == 2 && distinct && longest);
        }
    }

    /**
     * The declaration lines of {@code javap}'s output for one method name.
     *
     * <p>A declaration line is the only kind whose text ends in {@code ;} <i>and</i> whose text ends with
     * {@code name(…)}: the body's call sites end in {@code ;} too but carry a descriptor after the name, and the
     * {@code LocalVariableTable} rows javap prints at the bottom of a method carry variable names instead.
     * Requiring the name to be followed immediately by {@code (} is what keeps {@code part} from also counting
     * {@code parts} — the trap the first draft of this helper fell into (<i>{@code "parts(".indexOf("part(")}
     * is 0</i>).
     */
    private static List<String> declarationsOf(String disassembly, String name) {
        List<String> found = new ArrayList<>();
        for (String line : disassembly.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.endsWith(";")) {
                continue;
            }
            int open = trimmed.indexOf(name + "(");
            if (open < 0 || !trimmed.substring(0, open).endsWith(" ")) {
                continue;
            }
            // `parts(` starts with `part(`; only a declaration of exactly `name` counts.
            int afterName = open + name.length();
            if (afterName >= trimmed.length() || trimmed.charAt(afterName) != '(') {
                continue;
            }
            found.add(trimmed);
        }
        return found;
    }

    // ================================================================== the two stacks

    /** The data-pack stack: the loader's own private parse, by reflection, exactly as section K does. */
    private static MachineDefinition jsonPath(String json) {
        try {
            Class<?> loader = Class.forName("com.reborn.modularmachinery.machine.MachineLoader");
            Method method = loader.getDeclaredMethod("readMachine", ResourceLocation.class,
                    com.google.gson.JsonElement.class, Map.class);
            method.setAccessible(true);
            return (MachineDefinition) method.invoke(null, new ResourceLocation("mmverify", "z_json"),
                    JsonParser.parseString(json), variables());
        } catch (InvocationTargetException exception) {
            throw rethrow(exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** The script stack's parse: the same object, through the same entry point in strict mode. */
    private static MachineDefinition scriptPath(String json) {
        return readSchema(json, true);
    }

    /**
     * What a script's {@code .register()} does, in the order the builder does it: validate through the schema,
     * then stage the result. Kept as one call site here because that pair <b>is</b> the script path, and because
     * {@code MachineBuilderJS} cannot be loaded on this classpath (see the class comment).
     */
    private static MachineDefinition stagedScriptPath(String json) {
        // 'MachineRegistryEvents.registry' names the definition the way a script's error message would.
        MachineDefinition definition = MachineSchema.read(JsonParser.parseString(json), "machine 't'",
                variables(), true);
        MachineDefinitions.stage(definition);
        return definition;
    }

    private static MachineDefinition readSchema(String json, boolean strict) {
        return MachineSchema.read(JsonParser.parseString(json), "machine 't'", variables(), strict);
    }

    private static Map<String, List<String>> variables() {
        return new LinkedHashMap<>();
    }

    private static Map<ResourceLocation, MachineDefinition> merge(
            Map<ResourceLocation, MachineDefinition> baseline) {
        Map<ResourceLocation, MachineDefinition> merged = new LinkedHashMap<>(baseline);
        MachineDefinitions.applyScriptLayer(merged);
        return merged;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("modular_machinery_reborn", path);
    }

    // 0.31.0 deleted deferredValue(String): it existed to give each deferred field a syntactically legal value so
    // the refusal could be asserted to be about the field rather than about typing. With nothing deferred there
    // is no refusal to feed, and the values it carried now live in the v2b fixture in z3, which asserts the
    // opposite thing about them.

    private static String messageOf(Runnable body) {
        try {
            body.run();
            return null;
        } catch (RuntimeException exception) {
            return String.valueOf(exception.getMessage());
        }
    }

    private static RuntimeException rethrow(InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(cause);
    }

    // ================================================================== bytecode helpers

    private static boolean containsConstant(byte[] bytes, String text) {
        return bytes != null && indexOfConstant(bytes, text) >= 0;
    }

    /**
     * The offset of a UTF-8 constant in a class file, which is enough to answer "does this class mention X" and
     * — when X and Y both appear once — "which is mentioned first". Byte-scanning rather than decompiling keeps
     * this independent of any tool the build does not have; section Y uses the same trick for {@code ModBlocks}.
     */
    private static int indexOfConstant(byte[] bytes, String text) {
        if (bytes == null) {
            return -1;
        }
        byte[] needle = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i + needle.length <= bytes.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] classBytes(String resource) {
        try (java.io.InputStream stream = KubeJSMachineCheck.class.getClassLoader()
                .getResourceAsStream(resource + ".class")) {
            return stream == null ? null : stream.readAllBytes();
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    // ================================================================== the class file, read directly

    /**
     * How many {@code new} instructions in {@code method} reference the class {@code target}, or {@code -1} when
     * the class or the method cannot be read at all — the two answers are kept apart so a parse failure cannot be
     * mistaken for "the code does not do it".
     *
     * <p>This exists because both weaker forms of the check were tried and both were fooled or useless. Searching
     * the constant pool for the class name passes while the field only survives in an {@code import} (fault
     * injection A). Searching javap's text depends on how that tool lays out members and attributes: the bare
     * method name also appears in the class's {@code LocalVariableTable}, so the search lands there and reads no
     * instructions at all.
     *
     * <p>What it proves: the built artifact's {@code registerBindings} bytecode contains {@code new} of that
     * class. What it cannot prove: that the constructed object is what reaches a script, that the wrapper is the
     * KubeJS one this build would bind at runtime, or anything about the game. Section Z9 runs the method for
     * that, and the {@code /reload} checklist remains the final word.
     */
    private static int newInstructionIn(byte[] classFile, String method, String descriptor, String target) {
        if (classFile == null) {
            return -1;
        }
        try {
            int[] at = {8};                     // past magic, minor and major
            int count = u2(classFile, at);
            String[] utf8 = new String[count];
            int[] className = new int[count];   // CONSTANT_Class -> name index
            for (int i = 1; i < count; i++) {
                int tag = u1(classFile, at);
                switch (tag) {
                    case 1 -> utf8[i] = utf8(classFile, at);
                    case 7 -> className[i] = u2(classFile, at);
                    case 8, 16, 19, 20 -> at[0] += 2;
                    case 15 -> at[0] += 3;
                    case 12 -> at[0] += 4;
                    case 3, 4, 9, 10, 11, 17, 18 -> at[0] += 4;
                    case 5, 6 -> {
                        at[0] += 8;
                        i++;                    // the two-wide constants take two slots
                    }
                    default -> {
                        parseFailure = "unknown constant pool tag " + tag + " at entry " + i;
                        return -1;
                    }
                }
            }
            at[0] += 6;                         // access flags, this class, super class
            int interfaces = u2(classFile, at);
            at[0] += 2 * interfaces;             // interfaces
            int fields = u2(classFile, at);
            for (int i = 0; i < fields; i++) {
                skipMember(classFile, at);
            }
            int methods = u2(classFile, at);
            for (int i = 0; i < methods; i++) {
                u2(classFile, at);               // access flags
                int nameIndex = u2(classFile, at);
                int descriptorIndex = u2(classFile, at);
                boolean wanted = method.equals(utf8[nameIndex]) && descriptor.equals(utf8[descriptorIndex]);
                int attributes = u2(classFile, at);
                for (int a = 0; a < attributes; a++) {
                    int attributeName = u2(classFile, at);
                    int length = u4(classFile, at[0]);
                    at[0] += 4;
                    int payload = at[0];
                    if (wanted && "Code".equals(utf8[attributeName])) {
                        int codeLength = u4(classFile, payload + 4);
                        int codeStart = payload + 8;
                        int found = countNew(classFile, codeStart, codeLength, utf8, className, target);
                        if (found > 0) {
                            return found;
                        }
                        // The remaining Code attributes (LineNumberTable, LocalVariableTable) carry no
                        // instructions, so an empty answer here needs no second look. The walk continues to
                        // the next attribute by its declared length, which is what keeps the cursor honest:
                        // skipping "assume no exception table" is an 8-byte error that silently misreads every
                        // later method (it did, before this was written).
                    }
                    at[0] = payload + length;
                }
            }
            return 0;
        } catch (RuntimeException malformed) {
            parseFailure = malformed.toString();
            return -1;
        }
    }

    /** Why the last {@link #newInstructionIn} could not read the class, or {@code null} if it could. */
    private static String parseFailure;

    /** Every `new` in a byte range, with its class, for the diagnostic trace only. */
    private static void skipMember(byte[] bytes, int[] at) {
        at[0] += 6;                             // access flags, name, descriptor
        int attributes = u2(bytes, at);
        for (int i = 0; i < attributes; i++) {
            at[0] += 2;
            at[0] += u4(bytes, at[0]);
        }
    }

    /** Walks one method's instructions: only {@code new} (0xbb) matters, and it carries a class constant. */
    private static int countNew(byte[] bytes, int start, int length, String[] utf8, int[] className,
            String target) {
        int found = 0;
        int i = start;
        int end = start + length;
        while (i < end) {
            int opcode = bytes[i] & 0xFF;
            if (opcode == 0xbb) {
                int index = ((bytes[i + 1] & 0xFF) << 8) | (bytes[i + 2] & 0xFF);
                if (index > 0 && index < className.length && className[index] > 0
                        && target.equals(utf8[className[index]])) {
                    found++;
                }
                i += 3;
                continue;
            }
            i += instructionLength(opcode, bytes, i);
        }
        return found;
    }

    /** One instruction's length. Only the forms this code emits need to be right; an unknown one ends the walk. */
    private static int instructionLength(int opcode, byte[] bytes, int at) {
        switch (opcode) {
            case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc:
                return 2;
            case 0x11, 0x13, 0x14, 0x84, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2,
                 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd,
                 0xc0, 0xc1, 0xc6, 0xc7:
                return 3;
            case 0xc5:
                return 4;
            case 0xb9, 0xba, 0xc8, 0xc9:
                return 5;
            case 0xaa: {                        // tableswitch: pad, default, low, high, then the offsets
                int pad = 3 - ((at + 1) % 4 == 0 ? 3 : (at + 1) % 4);
                pad = (4 - ((at + 1) % 4)) % 4;
                int base = at + 1 + pad;
                int low = i4(bytes, base + 4);
                int high = i4(bytes, base + 8);
                return 1 + pad + 12 + (high - low + 1) * 4;
            }
            case 0xab: {                        // lookupswitch
                int pad = (4 - ((at + 1) % 4)) % 4;
                int base = at + 1 + pad;
                int pairs = i4(bytes, base + 4);
                return 1 + pad + 8 + pairs * 8;
            }
            case 0xc4: {                        // wide
                int widened = bytes[at + 1] & 0xFF;
                return widened == 0x84 ? 6 : 4;
            }
            default:
                if (opcode >= 0x1a && opcode <= 0x35) {
                    return 1;                   // the implicit local loads
                }
                if (opcode >= 0x3b && opcode <= 0x83) {
                    return 1;                   // the implicit local stores
                }
                if (opcode >= 0x02 && opcode <= 0x0f) {
                    return 1;                   // constants
                }
                if (opcode >= 0x60 && opcode <= 0x83) {
                    return 1;                   // arithmetic
                }
                if (opcode >= 0xac && opcode <= 0xb1) {
                    return 1;                   // returns
                }
                if (opcode >= 0x2e && opcode <= 0x35) {
                    return 1;                   // array loads
                }
                return 1;
        }
    }

    private static int u1(byte[] bytes, int[] at) {
        return bytes[at[0]++] & 0xFF;
    }

    private static int u2(byte[] bytes, int[] at) {
        int value = ((bytes[at[0]] & 0xFF) << 8) | (bytes[at[0] + 1] & 0xFF);
        at[0] += 2;
        return value;
    }

    private static int u4(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
    }

    private static int i4(byte[] bytes, int at) {
        return u4(bytes, at);
    }

    private static String utf8(byte[] bytes, int[] at) {
        int length = u2(bytes, at);
        String value = new String(bytes, at[0], length, java.nio.charset.StandardCharsets.UTF_8);
        at[0] += length;
        return value;
    }

    /** A short excerpt around a name in the whitespace-stripped disassembly, for a diagnostic line. */
    private static String shapeOf(String disassembly, String name) {
        if (disassembly == null) {
            return name + "=<no disassembly>";
        }
        int at = disassembly.indexOf(name);
        if (at < 0) {
            return name + "=<absent>";
        }
        int end = Math.min(disassembly.length(), at + name.length() + 8);
        return disassembly.substring(at, end);
    }

    /** A window of the whitespace-stripped disassembly ending at a name, for a diagnostic line. */
    private static String before(String disassembly, String name, int window) {
        if (disassembly == null) {
            return name + "=<no disassembly>";
        }
        int at = disassembly.indexOf(name);
        if (at < 0) {
            return name + "=<absent>";
        }
        return disassembly.substring(Math.max(0, at - window), at);
    }

    /** The offset of the first match of a pattern, or -1 — {@code String#indexOf} for the two regexes used. */
    private static int firstMatch(String text, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }
}
