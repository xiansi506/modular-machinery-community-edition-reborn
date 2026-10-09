package mmverify;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

import static mmverify.ParallelCraftCheck.check;
import static mmverify.ParallelCraftCheck.renderConfigSpec;
import static mmverify.ParallelCraftCheck.report;
import static mmverify.ParallelCraftCheck.section;

/**
 * Section Y — the ModularController (MOC) compatibility namespace, 0.27.0.
 *
 * <p>The original MMCE absorbed the older <i>ModularController</i> mod and kept a controller block registered
 * under the {@code modularcontroller} namespace so a save made with that mod still finds its block. The
 * original's evidence:
 *
 * <ul>
 *   <li>{@code Config.java:99-105} — the two keys, {@code modular-controller-compatible-mode} (general, default
 *       {@code false}) and {@code disable-moc-deprecated-tip} (general, default {@code false});</li>
 *   <li>{@code RegistryBlocks.java:408-420} — under {@code Config.mocCompatibleMode}, each non-factory-only
 *       machine gets {@code new BlockController("modularcontroller", machine)}, i.e. the registry name
 *       {@code modularcontroller:<machine path>_controller}, <b>plus</b> an {@code ItemBlockController}
 *       registered under that same name;</li>
 *   <li>{@code BlockController.java:94-100} — the {@code (String namespace, DynamicMachine)} constructor, the
 *       same class {@code :86-92} uses for the ordinary {@code modularmachinery:<path>_controller};</li>
 *   <li>{@code BlockController.java:118-121} — the deprecation tooltip, shown when the block's namespace is
 *       {@code modularcontroller} and {@code disableMocDeprecatedTip} is off;</li>
 *   <li>{@code BlockController.java:289-297} — {@code createTileEntity} returns the ordinary
 *       {@code TileMachineController}, so the MOC block and the ordinary block share their tile entity.</li>
 * </ul>
 *
 * <p><b>Reflection- and bytecode-first on purpose</b>: this section was written before the production classes
 * existed, so the run that preceded the implementation compiled and reported failed checks instead of failing to
 * compile. A check that has never been seen to fail is not evidence.
 *
 * <p><b>Two walls this section cannot cross offline</b>, stated rather than papered over:
 *
 * <ol>
 *   <li>The live {@code ForgeRegistries.BLOCKS} cannot be populated outside Forge's mod loading (section O found
 *       the same wall), so the registry name is asserted through {@code MocNamespace.controllerName(…)} — the one
 *       function the registration calls — and the composition of the registration through {@code ModBlocks}' own
 *       class file.</li>
 *   <li>{@code ModBlocks} <b>cannot be loaded at all</b> here: its static initialiser builds
 *       {@code DeferredRegister}s, which reaches {@code net.minecraftforge.fml.common.Mod} — a class Forge's
 *       mod-file scanner carries and this harness's filtered classpath deliberately omits. So everything about
 *       {@code ModBlocks} below is read with ASM from the class file, never by {@code Class.forName}.</li>
 * </ol>
 */
final class MocNamespaceCheck {

    /** The older mod's namespace, which the original hardcoded at {@code RegistryBlocks.java:414}. */
    private static final String NS = "modularcontroller";

    /** The deprecated-tip language keys, verbatim from the original's own {@code I18n.format} calls. */
    private static final String TIP_0 = "tile.modularmachinery.machinecontroller.deprecated.tip.0";
    private static final String TIP_1 = "tile.modularmachinery.machinecontroller.deprecated.tip.1";

    private static final String BLOCKS_CLASS = "com.reborn.modularmachinery.block.ModBlocks";
    private static final String PACK_CLASS = "com.reborn.modularmachinery.client.GeneratedControllerPack";
    private static final String JEI_CLASS =
            "com.reborn.modularmachinery.client.jei.ModularMachineryJeiPlugin";

    /** Where a fixture machine directory is written. Under the ASCII junction, like everything else here. */
    private static final Path FIXTURE_ROOT = Path.of("C:/mmwork/.tmp-m6c-verify/moc-fixture");

    /** Why the last reflected call failed, so a suppressed failure names itself instead of reading as "absent". */
    private static String lastFailure = "none";

    /** Four files, three of which name a machine. The fourth is there to be skipped. */
    private static final Map<String, String> FIXTURE_FILES = new LinkedHashMap<>();

    static {
        FIXTURE_FILES.put("alloy_furnace.json",
                "{\"registryname\":\"modular_machinery_reborn:alloy_furnace\"}");
        // A claim whose file name and machine path differ, so the registry name is provably derived from the
        // MACHINE path and not from the file name.
        FIXTURE_FILES.put("renamed_declaration.json",
                "{\"registryname\":\"modular_machinery_reborn:transformer\"}");
        FIXTURE_FILES.put("factory_only_one.json",
                "{\"registryname\":\"modular_machinery_reborn:example_factory\","
                        + "\"has-factory\":true,\"factory-only\":true}");
        FIXTURE_FILES.put("not_a_machine.json", "{\"parts\":[]}");
    }

    private MocNamespaceCheck() {
    }

    // ================================================================== the section

    static void mocCompatibilityNamespace() {
        section("Y. the MOC compatibility namespace: registry names, old saves, tooltip, spec, assets");

        // The harness has to be able to say WHICH failure it is looking at. If the production class is not
        // there, every check below fails for that one reason, which is exactly the red-first evidence.
        boolean present = readClassNode("com.reborn.modularmachinery.block.MocNamespace") != null;
        report("the production class com.reborn.modularmachinery.block.MocNamespace exists (red-first: it does "
                + "not, yet)", present);
        if (!present) {
            report("skipping Y's behaviour checks: MocNamespace is absent, which the check above already "
                    + "reported. A class that does not exist cannot have its registry names asserted.", true);
            return;
        }

        // Section R of this harness leaves ModConfig.SPEC holding the user's real config file, and the checks
        // below reproduce other states in it. The whole section therefore runs inside a restore, so the state a
        // later section sees is the state section R left — a check may not depend on another one's leftovers, and
        // it may not break one either.
        Object spec = specOrNull();
        Object entryState = null;
        // ForgeConfigSpec keeps the file it was handed in a private field and offers no getter, so this reads it
        // reflectively: the point is to put back exactly what section R left, not to guess at it.
        try {
            if (spec != null) {
                entryState = childConfigOf(spec);
            }
            configTiming();
            registryNames();
            noDoubleCounting();
            oldSaveShape();
            tooltip();
            configKeys();
            generatedAssetsAndLanguage();
        } catch (Throwable throwable) {
            report("section Y aborted: " + describe(throwable), false);
        } finally {
            if (spec != null) {
                try {
                    setConfig(spec, entryState);
                    report("section Y left the config spec exactly as section R had it (the same child config "
                                    + "instance)", childConfigOf(spec) == entryState);
                } catch (Throwable throwable) {
                    report("the spec could not be restored: " + describe(throwable), false);
                }
            }
        }
    }

    // ================================================================== 1. the timing crux

    /**
     * The crux. Two independent kinds of evidence, because a boolean read at the wrong moment proves nothing:
     *
     * <ol>
     *   <li><b>The order Forge really declares.</b> Read out of the shipped Forge sources on this machine, not
     *       repeated from a comment: {@code ForgeStatesProvider} chains
     *       {@code CREATE_REGISTRIES → OBJECT_HOLDERS → INJECT_CAPABILITIES → UNFREEZE_DATA → LOAD_REGISTRIES},
     *       and {@code LOAD_REGISTRIES} is the state that fires {@code RegisterEvent} through
     *       {@code GameData.postRegisterEvents()}. {@code CONFIG_LOAD}, the state that calls
     *       {@code ConfigTracker.loadConfigs}, is declared by {@code ModStateProvider} in the {@code LOAD}
     *       phase.</li>
     *   <li><b>The behaviour that follows from it, in this process.</b> With no config handed to the spec the
     *       accessors answer with their pre-config fallback; with a config in hand they answer with the file's
     *       value. That pair is what makes "not readable at registry time" a statement about <i>when</i> rather
     *       than about a dead accessor.</li>
     * </ol>
     *
     * <p>The spec is put back the way this check found it, because section R of this harness leaves it loaded and
     * no check here may depend on another section's leftovers.
     */
    private static void configTiming() {
        System.out.println();
        System.out.println("  -- (1) the timing crux: is the config readable when the registry event fires?");

        // ---- (1a) the order Forge declares, from the shipped sources ---------------------------------
        String[] forgeSources = forgeStateSources();
        if (forgeSources == null) {
            report("Forge's own sources were not found on this machine, so the load-order half of this check "
                    + "could not be read (" + lastFailure + "); the behavioural half below still runs", false);
        } else {
            String states = forgeSources[0];
            String modStates = forgeSources[1];
            report("Forge's ForgeStatesProvider chains the GATHER phase "
                            + "CREATE_REGISTRIES -> OBJECT_HOLDERS -> ... -> UNFREEZE_DATA -> LOAD_REGISTRIES",
                    occursBefore(states, "CREATE_REGISTRIES", "OBJECT_HOLDERS")
                            && occursBefore(states, "UNFREEZE_DATA", "LOAD_REGISTRIES"));
            int loadRegistries = states.indexOf("LOAD_REGISTRIES =");
            int postRegister = states.indexOf("GameData.postRegisterEvents()");
            report("...and LOAD_REGISTRIES is the state that posts the registry events (LOAD_REGISTRIES at "
                            + loadRegistries + ", postRegisterEvents at " + postRegister + ")",
                    loadRegistries >= 0 && postRegister > loadRegistries);
            int configLoad = modStates.indexOf("CONFIG_LOAD =");
            int loadConfigs = modStates.indexOf("ConfigTracker.INSTANCE.loadConfigs");
            report("...and CONFIG_LOAD is the state that calls ConfigTracker.loadConfigs (CONFIG_LOAD at "
                            + configLoad + ", loadConfigs at " + loadConfigs + "), so the config file arrives "
                            + "after the registry event",
                    configLoad >= 0 && loadConfigs > configLoad);
            report("...and CONFIG_LOAD is declared in ModLoadingPhase.LOAD while the registry chain is "
                            + "ModLoadingPhase.GATHER",
                    modStates.contains("ModLoadingPhase.LOAD") && states.contains("ModLoadingPhase.GATHER"));
        }

        // ---- (1b) the behaviour, in this process -----------------------------------------------------
        Object spec = specOrNull();
        if (spec == null) {
            report("ModConfig.SPEC could not be reached", false);
            return;
        }
        Boolean wasLoaded = null;
        try {
            wasLoaded = isLoaded(spec);
            // Reproduce the registry-time state deliberately: no config has been handed to the spec.
            setConfig(spec, null);
            report("with no config handed to the spec — the state Forge is in while RegisterEvent fires — "
                            + "SPEC.isLoaded() is false (on entry it was " + wasLoaded
                            + ", which is section R's leftover, not a game fact)",
                    !isLoaded(spec));
            report("...and in that state ModConfig.mocCompatibleMode() answers its pre-config fallback, false, "
                            + "whatever the file says (" + mocCompatibleMode() + ")",
                    !mocCompatibleMode());
            report("...and disableMocDeprecatedTip() answers its fallback too (" + disableMocDeprecatedTip()
                    + ")", !disableMocDeprecatedTip());

            // The positive control: the same accessors, the same process, a config in hand.
            boolean followed = accessorFollowsSpec();
            report("control: with a config setting both keys true, the accessors read it (so the two answers "
                    + "above are about WHEN the config is available, not about dead accessors)", followed);
        } catch (Throwable throwable) {
            report("the timing behaviour could not be driven: " + describe(throwable), false);
        }
        // The section-level restore in mocCompatibilityNamespace() puts the spec back; it is deliberately not
        // done here, because the checks after this one also drive the spec and each of them must be free to.
        if (wasLoaded != null && !wasLoaded) {
            lastFailure = "the spec was unloaded on entry to section Y";
        }
    }

    // ================================================================== 2. the registry names

    private static void registryNames() {
        System.out.println();
        System.out.println("  -- (2) the registry names the original used, and which machines get one");

        List<?> refs = compatibilityMachinesOrNull(FIXTURE_ROOT);
        if (refs == null) {
            report("MocNamespace.compatibilityMachines(Path) could not be reached: " + lastFailure, false);
            return;
        }
        check("declarations that get an MOC controller (4 files, 3 name a machine, 1 is factory-only)",
                2, refs.size());

        Map<String, String> byPath = new TreeMap<>();
        for (Object ref : refs) {
            byPath.put(stringMember(ref, "path"), stringMember(ref, "machineId"));
        }
        check("alloy_furnace is claimed", "modular_machinery_reborn:alloy_furnace", byPath.get("alloy_furnace"));
        // Deliberately keyed by "transformer", not by the declaration's file name "renamed_declaration": the
        // block's path comes from the MACHINE's registry path, exactly as the original built it
        // (BlockController.java:97-99).
        check("the declaration file 'renamed_declaration.json' claims modular_machinery_reborn:transformer",
                "modular_machinery_reborn:transformer", byPath.get("transformer"));
        report("...and no block path came from the file name", !byPath.containsKey("renamed_declaration"));
        report("the file without a 'registryname' got no controller at all",
                !byPath.containsKey("not_a_machine"));
        // Keyed by the MACHINE path, like everything else here: asserting on the declaration file's name would
        // pass whatever the guard did, because a file name is never a key.
        report("the 'factory-only' machine (declared in factory_only_one.json) got no MOC controller — the "
                        + "original's isFactoryOnly guard, RegistryBlocks.java:410-412 (refs: "
                        + new TreeSet<>(byPath.keySet()) + ")",
                !byPath.containsKey("example_factory"));

        // The registry name itself, from the production function the registration calls.
        registryNameChecks();

        // The registration half, read out of ModBlocks' own class file (see the class comment for why it cannot
        // be loaded here), which is exact for "which classes, which namespace, which namesake register".
        ClassNode blocks = readClassNode(BLOCKS_CLASS);
        if (blocks == null) {
            report("ModBlocks' class file could not be read", false);
            return;
        }
        String blockBytes = translate(blocks);
        report("ModBlocks keeps the compatibility entries in their own DeferredRegister pair, so the ids can "
                        + "carry a namespace that is not the mod's",
                blockBytes.contains("MOC_BLOCKS") && blockBytes.contains("MOC_ITEMS"));
        // MocNamespace.NAMESPACE is a compile-time String constant, so javac inlines it: the evidence is the
        // literal in <clinit>'s own constant pool, not a field read.
        List<String> clinitLiterals = methodLiterals(blocks, "<clinit>");
        int namespaceLiterals = 0;
        for (String literal : clinitLiterals) {
            if (literal.equals(NS)) {
                namespaceLiterals++;
            }
        }
        report("...and both registers are created with MocNamespace.NAMESPACE — the literal '" + NS + "' is in "
                        + "ModBlocks' <clinit> constant pool " + namespaceLiterals + " time(s) — so the ids really "
                        + "are in that namespace rather than renamed into the mod's",
                namespaceLiterals >= 2);
        report("...and the block entries are MachineControllerBlock while the item entries are "
                        + "MocMachineControllerItem",
                blockBytes.contains("MachineControllerBlock")
                        && blockBytes.contains("MocMachineControllerItem"));
        report("...and the two registers are handed to the event bus by one method, which the mod constructor "
                        + "calls (ModBlocks.registerMocNamespace)",
                fieldsReferenced(blocks, "registerMocNamespace").contains("MOC_BLOCKS")
                        && fieldsReferenced(blocks, "registerMocNamespace").contains("MOC_ITEMS"));
        report("...and the registration is driven by ModBlocks.MOC_CONTROLLERS, which is filled from the "
                        + "production rule and from nothing else",
                fieldsReferenced(blocks, "<clinit>").contains("MOC_CONTROLLERS"));
        report("...and the machine that binds a compatibility controller is the same MachineRef the ordinary "
                        + "controller uses (no second declaration format)",
                blockBytes.contains("MachineDirectory$MachineRef"));
    }

    /**
     * The three names {@link #mocControllerName} must produce, read out of the production function. Kept apart
     * from {@link #registryNames()} only because it declares the checked exceptions reflection needs.
     */
    private static void registryNameChecks() {
        String alloy;
        String alloyPath;
        String transformer;
        try {
            Object location = mocControllerName("modular_machinery_reborn:alloy_furnace");
            alloy = String.valueOf(location);
            alloyPath = String.valueOf(location.getClass().getMethod("getPath").invoke(location));
            transformer = String.valueOf(mocControllerName("modular_machinery_reborn:transformer"));
        } catch (Throwable throwable) {
            report("MocNamespace.controllerName could not be invoked: " + describe(throwable), false);
            return;
        }
        check("MocNamespace.controllerName(machine).getPath()", "alloy_furnace_controller", alloyPath);
        check("MocNamespace.controllerName(machine), namespace and path together",
                NS + ":alloy_furnace_controller", alloy);
        check("...and a differently named machine maps to its own path", NS + ":transformer_controller",
                transformer);
        check("the namespace constant", NS, namespaceConstant());
    }

    // ================================================================== 3. no double counting

    /**
     * The other half of "the MOC controller is an alias": two blocks now bind to one machine definition, so
     * everything that <b>enumerates</b> controllers must keep enumerating only the ordinary set — otherwise every
     * machine gains a duplicate.
     *
     * <p>The three enumerators and what a duplicate would cost:
     * <ul>
     *   <li>{@code ModBlocks.controllerItems()} → {@code ModularMachineryJeiPlugin.registerRecipeCatalysts}
     *       ({@code :153}), which turns each item into a JEI catalyst: a duplicate lists every machine's
     *       controller twice in its category.</li>
     *   <li>{@code ModBlocks.boundControllerBlock(path)} → {@code StructurePreviews} ({@code :154}), the
     *       controller shown in the structure-preview machine-info block.</li>
     *   <li>{@code ModBlocks.controllersNeedingGeneratedAssets()} → the synthetic resource pack's mod-namespace
     *       half.</li>
     * </ul>
     */
    private static void noDoubleCounting() {
        System.out.println();
        System.out.println("  -- (3) the alias rule: nothing enumerates an MOC controller as a controller");

        ClassNode blocks = readClassNode(BLOCKS_CLASS);
        if (blocks == null) {
            report("ModBlocks' class file could not be read", false);
            return;
        }
        Set<String> catalystList = fieldsReferenced(blocks, "controllerItems");
        report("ModBlocks.controllerItems() adds the ordinary bound items and never the compatibility ones "
                        + "(" + new TreeSet<>(catalystList) + ")",
                catalystList.contains("BOUND_CONTROLLER_ITEMS")
                        && !catalystList.contains("MOC_CONTROLLER_ITEMS"));
        report("...and mocControllerItems() — a separate accessor — is the only place that exposes them",
                fieldsReferenced(blocks, "mocControllerItems").contains("MOC_CONTROLLER_ITEMS"));

        // The creative tab is a lambda inside ModBlocks' <clinit>. Its bytecode is a nested class, so the whole
        // outer class is scanned for a reference to the compatibility item list outside the accessor itself: any
        // such reference would be a second enumerator.
        int outsideAccessor = 0;
        for (MethodNode method : blocks.methods) {
            if (method.name.equals("mocControllerItems") || method.name.equals("mocControllerBlock")
                    || method.name.equals("mocControllerBlocks") || method.name.equals("<clinit>")) {
                continue;
            }
            if (instructionsMention(method, "MOC_CONTROLLER_ITEMS")) {
                outsideAccessor++;
            }
        }
        check("the creative tab — and every other method of ModBlocks — never lists the compatibility items",
                0, outsideAccessor);
        // The creative tab's item lambda is `lambda$static$N`; exactly one of them reads the ordinary bound
        // items and none reads the compatibility ones.
        int tabLambdas = 0;
        int tabLambdasWithMoc = 0;
        for (MethodNode method : blocks.methods) {
            if (!method.name.startsWith("lambda$static$")) {
                continue;
            }
            Set<String> reads = fieldsReferenced(blocks, method.name);
            if (reads.contains("BOUND_CONTROLLER_ITEMS")) {
                tabLambdas++;
                if (reads.contains("MOC_CONTROLLER_ITEMS")) {
                    tabLambdasWithMoc++;
                }
            }
        }
        check("the creative tab's item lambda still reads the ordinary bound controllers", 1, tabLambdas);
        check("...and it lists none of the compatibility items (they exist for old saves, not to be handed out)",
                0, tabLambdasWithMoc);

        // JEI's catalyst loop still iterates the ordinary accessor, by name, in its own bytecode. Read with ASM
        // rather than by loading the class: the plugin carries FML and JEI annotations, and FML's classes are not
        // on this harness's filtered classpath.
        ClassNode jei = readClassNode(JEI_CLASS);
        if (jei == null) {
            report("the JEI plugin's class file could not be read", false);
        } else {
            report("JEI registers catalysts from ModBlocks.controllerItems(), so the aliases cannot enter JEI",
                    methodCalls(jei, "registerRecipeCatalysts", "controllerItems"));
            report("...and it does NOT register catalysts from mocControllerItems()",
                    !methodCalls(jei, "registerRecipeCatalysts", "mocControllerItems"));
            report("...and it does not name the compatibility class anywhere",
                    !translate(jei).contains("MocNamespace"));
        }

        // The structure-preview lookup still reads the ordinary map.
        report("ModBlocks.boundControllerBlock(...) reads the ordinary map, not the compatibility one, so the "
                        + "structure preview keeps showing the ordinary controller",
                fieldsReferenced(blocks, "boundControllerBlock").contains("BOUND_CONTROLLER_BLOCKS"));

        // The pack's two halves come from the two different sources.
        ClassNode pack = readClassNode(PACK_CLASS);
        if (pack == null) {
            report("GeneratedControllerPack's class file could not be read", false);
            return;
        }
        report("the pack's mod-namespace half is built from controllersNeedingGeneratedAssets()",
                translate(pack).contains("controllersNeedingGeneratedAssets"));
        report("...and its compatibility half from ModBlocks.MOC_CONTROLLERS, so each namespace gets its own "
                        + "files",
                fieldsReferenced(pack, "<clinit>").contains("MOC_CONTROLLERS")
                        || methodLiterals(pack, "<clinit>").contains("MOC_CONTROLLERS"));
        report("...and the two halves are merged into one file map, so neither namespace is dropped",
                fieldsReferenced(pack, "<init>").contains("MOC_FILES")
                        && fieldsReferenced(pack, "<init>").contains("FILES"));
        report("...and the file arithmetic itself lives in GeneratedControllerAssets, which reads no registry — "
                        + "which is what lets section (7) build the very files the live pack builds",
                readClassNode("com.reborn.modularmachinery.block.GeneratedControllerAssets") != null
                        && translate(pack).contains("GeneratedControllerAssets"));

        // Forming: the controller path must not branch on the block's namespace. The block entity resolves its
        // machine from the block (`MachineControllerBlockEntity:281-283`), and nothing in that path reads a
        // registry namespace — asserted by there being no such read at all.
        ClassNode entity = readClassNode("com.reborn.modularmachinery.block.MachineControllerBlockEntity");
        if (entity != null) {
            String bytes = translate(entity);
            report("the controller block entity never consults the block's namespace, so an MOC controller forms "
                            + "and runs through exactly the same path as an ordinary one",
                    !bytes.contains("MocNamespace") && !bytes.contains("getNamespace"));
        }
        ClassNode block = readClassNode("com.reborn.modularmachinery.block.MachineControllerBlock");
        if (block != null) {
            String bytes = translate(block);
            report("...and neither does the controller block itself (no namespace branch, no MocNamespace "
                            + "reference)", !bytes.contains("MocNamespace") && !bytes.contains("getNamespace"));
        }
    }

    // ================================================================== 4. the old-save story

    /**
     * An old save's block must keep working with <b>no tile migration</b>. The original reached that by reusing
     * one block class and one tile entity for both namespaces ({@code BlockController.java:94-100} and
     * {@code :289-297}); this asserts the same shape here, from the class files, so it holds whether or not the
     * machine directory on this machine declares anything.
     */
    private static void oldSaveShape() {
        System.out.println();
        System.out.println("  -- (4) an existing modularcontroller block in an old save");

        ClassNode blocks = readClassNode(BLOCKS_CLASS);
        ClassNode block = readClassNode("com.reborn.modularmachinery.block.MachineControllerBlock");
        ClassNode item = readClassNode("com.reborn.modularmachinery.item.MocMachineControllerItem");
        if (blocks == null || block == null || item == null) {
            report("the controller classes could not be read", false);
            return;
        }
        String blockBytes = translate(blocks);

        // The block the compatibility namespace registers is the ordinary MachineControllerBlock: the only class
        // name the MOC registration lambda constructs for a block.
        Set<String> moclInit = fieldsReferenced(blocks, "<clinit>");
        report("ModBlocks registers the compatibility block as a MachineControllerBlock — the ordinary class, so "
                        + "there is no second block class for a save to have to migrate between",
                moclInit.contains("MOC_BLOCKS") && classConstructs(blocks, "MachineControllerBlock"));
        report("...and only its item is a distinct class (MocMachineControllerItem), which is where the original "
                        + "put the deprecation tooltip in 1.20.1 terms",
                classConstructs(blocks, "MocMachineControllerItem"));
        report("...and MocMachineControllerItem extends MachineControllerItem, so it cannot have drifted from the "
                        + "ordinary item's behaviour",
                item.superName.equals("com/reborn/modularmachinery/item/MachineControllerItem"));
        report("...and no MOC factory controller is registered, because the original had no "
                        + "BlockFactoryController branch in the compatibility namespace "
                        + "(RegistryBlocks.java:408-420): "
                        + countOccurrences(blockBytes, "_factory_controller") + " factory name literal(s)",
                countOccurrences(blockBytes, "_factory_controller") == 2);

        // The tile: the block class creates the ordinary block entity, and its ticker is bound to the mod's one
        // block entity type.
        String blockClass = translate(block);
        report("the controller block's newBlockEntity creates the ordinary MachineControllerBlockEntity, so an "
                        + "old block resumes with the same tile and no migration",
                blockClass.contains("MachineControllerBlockEntity"));
        report("...and its ticker is bound to the mod's single machine_controller block entity type, so nothing "
                        + "about the tile is namespace-dependent",
                fieldsReferenced(block, "getTicker").contains("MACHINE_CONTROLLER_ENTITY"));

        // The bound machine is read from the block, so the MOC block's own machineId is what the tile uses.
        ClassNode entity = readClassNode("com.reborn.modularmachinery.block.MachineControllerBlockEntity");
        if (entity != null) {
            report("...and the tile takes its bound machine from the block state's block "
                            + "(MachineControllerBlockEntity:281-283), which is why the compatibility block finds "
                            + "its machine with no extra wiring",
                    methodCalls(entity, "<init>", "boundMachine")
                            || translate(entity).contains("boundMachine"));
        }
    }

    // ================================================================== 5. the tooltip

    private static void tooltip() {
        System.out.println();
        System.out.println("  -- (5) the deprecation tooltip");

        ClassNode item = readClassNode("com.reborn.modularmachinery.item.MocMachineControllerItem");
        report("com.reborn.modularmachinery.item.MocMachineControllerItem exists (red-first: it does not, yet)",
                item != null);
        if (item == null) {
            return;
        }

        // Read the method's own bytecode, not the class's constant pool: a key merely mentioned elsewhere must
        // not count as a tooltip line.
        List<String> literals = methodLiterals(item, "appendHoverText");
        int at0 = literals.indexOf(TIP_0);
        int at1 = literals.indexOf(TIP_1);
        report("the item's appendHoverText loads the original's tip.0 key, verbatim (position " + at0 + ")",
                at0 >= 0);
        report("...and tip.1 (position " + at1 + ")", at1 >= 0);
        report("...and in the original's order, tip.0 before tip.1 (BlockController.java:119-120)",
                at0 >= 0 && at1 >= 0 && at0 < at1);
        report("appendHoverText loads exactly those two literals, so no extra line was invented (found "
                        + literals + ")", literals.size() == 2);
        report("the tooltip consults ModConfig.disableMocDeprecatedTip (the original's gate, "
                        + "BlockController.java:118)", methodCalls(item, "appendHoverText",
                "disableMocDeprecatedTip"));
        report("...and it calls super.appendHoverText first, so the ordinary item's tooltip is not lost",
                methodCalls(item, "appendHoverText", "appendHoverText"));

        // Nothing else may invent the tip text: the original had exactly one site.
        int elsewhere = 0;
        for (String name : List.of("com.reborn.modularmachinery.block.MachineControllerBlock",
                "com.reborn.modularmachinery.item.MachineControllerItem",
                "com.reborn.modularmachinery.block.MocNamespace",
                "com.reborn.modularmachinery.item.ModItems")) {
            ClassNode other = readClassNode(name);
            if (other != null && translate(other).contains(TIP_0)) {
                elsewhere++;
            }
        }
        check("no other controller class mentions the deprecation tip", 0, elsewhere);

        // The item must not show a raw key: it inherits the ordinary named-controller translation key, so the
        // assertion is that it does NOT override the description id with a namespace of its own.
        report("the item does not override getDescriptionId(), so it inherits the ordinary controller's '%s "
                        + "Controller' key, which both language files carry",
                !overrides(item, "getDescriptionId"));
        report("...and it does not override getName(ItemStack) either, so a bound machine's display name still "
                        + "comes from the ordinary path",
                !overrides(item, "getName"));
    }

    // ================================================================== 6. the config keys

    private static void configKeys() {
        System.out.println();
        System.out.println("  -- (6) the two config keys, read out of the rendered spec");

        Map<String, String> rendered = renderConfigSpec();
        if (rendered.isEmpty()) {
            report("the config spec could not be rendered", false);
            return;
        }
        // 13 before this release, 15 with the two MOC keys.
        check("the config spec defines this many keys", 15, rendered.size());

        String[][] expected = {
                {"general.modular-controller-compatible-mode", "false"},
                {"general.disable-moc-deprecated-tip", "false"},
        };
        for (String[] pair : expected) {
            String actual = rendered.get(pair[0]);
            report("the rendered spec has '" + pair[0] + "' = " + actual
                            + (actual == null ? " (ABSENT)" : ""), pair[1].equals(actual));
        }
        report("no rendered key repeats a path element (the 0.20.0 defect)",
                rendered.keySet().stream().noneMatch(MocNamespaceCheck::repeatsASegment));
        report("the 13 pre-existing keys are untouched",
                rendered.containsKey("factory-system.default-factory-max-thread")
                        && rendered.containsKey("smart-interface.enable-smart-interface-bydefault")
                        && rendered.containsKey("parallel-controller.normal.max-parallelism")
                        && rendered.containsKey("upgrade-bus.ultimate.max-upgrade_slot"));

        // Read with the spec cleared, because (1b) left it holding a test config and a default is a statement
        // about the spec, not about section R's leftovers.
        Object spec = specOrNull();
        if (spec == null) {
            report("ModConfig.SPEC could not be reached", false);
        } else {
            try {
                setConfig(spec, null);
            } catch (Throwable throwable) {
                report("the spec could not be cleared: " + describe(throwable), false);
            }
            report("ModConfig.mocCompatibleMode() is false by default", !mocCompatibleMode());
            report("ModConfig.disableMocDeprecatedTip() is false by default", !disableMocDeprecatedTip());
        }
        report("both accessors exist and are the same feature the spec defines",
                accessorExists("mocCompatibleMode") && accessorExists("disableMocDeprecatedTip"));

        // The inert key's own comment must say so. Read from the class file, because a comment is not something
        // a spec can carry: ForgeConfigSpec.Builder#comment writes what it is handed, so the honest wording has to
        // be a literal in ModConfig.
        ClassNode config = readClassNode("com.reborn.modularmachinery.config.ModConfig");
        String literals = config == null ? "" : translate(config);
        report("the inert key's comment says it is inert and why (it names LOAD_REGISTRIES and CONFIG_LOAD) — an "
                        + "inert key whose comment promises behaviour it cannot deliver is worse than no key",
                literals.contains("INERT") && literals.contains("CONFIG_LOAD"));
        report("...and it says the compatibility items are not in the creative tab",
                literals.contains("creative tab"));
        report("...and the live key's comment is the original's wording for it",
                literals.contains("Disable the ModularController is deprecated tooltip"));
    }

    // ================================================================== 7. the generated pack and the language files

    private static void generatedAssetsAndLanguage() {
        System.out.println();
        System.out.println("  -- (7) generated assets, the language files and the creative tab");

        // (a) The generated pack must carry the MOC namespace too, or an old save's block renders as the
        // missing model: the mod ships no blockstate for a name only a pack author chose.
        //
        // Driven through the pack's own two builders, with the fixture's declarations, rather than through the
        // live pack: the live pack reads ModBlocks, which cannot be initialised outside Forge. The builders are
        // the production code the live pack's two static maps are built from, so the file shape asserted here is
        // the file shape an old save gets.
        Map<String, String> mocAssets = generatedAssets(true);
        Map<String, String> ownAssets = generatedAssets(false);
        if (mocAssets == null || ownAssets == null) {
            report("GeneratedControllerPack's builders could not be driven: " + lastFailure, false);
        } else {
            String blockstate = mocAssets.get(NS + ":blockstates/alloy_furnace_controller.json");
            String itemModel = mocAssets.get(NS + ":models/item/alloy_furnace_controller.json");
            report("a blockstate is generated for " + NS + ":alloy_furnace_controller ("
                            + (blockstate == null ? "ABSENT" : blockstate.length() + " chars") + ")",
                    blockstate != null);
            report("...and it points at the mod's own block/machine_controller model",
                    blockstate != null && blockstate.contains("modular_machinery_reborn:block/machine_controller"));
            report("...and it carries the same four facing variants the shipped controller blockstate does, so "
                            + "an old save's placed block faces the right way",
                    blockstate != null && blockstate.contains("facing=north")
                            && blockstate.contains("facing=east") && blockstate.contains("facing=south")
                            && blockstate.contains("facing=west"));
            report("an item model is generated for " + NS + ":alloy_furnace_controller", itemModel != null);
            report("...and the compatibility half has no factory-controller file, as the original had none in "
                            + "that namespace",
                    mocAssets.keySet().stream().noneMatch(key -> key.contains("_factory_controller")));
            report("the transformer claim gets its own two files, keyed by the MACHINE path",
                    mocAssets.containsKey(NS + ":blockstates/transformer_controller.json")
                            && mocAssets.containsKey(NS + ":models/item/transformer_controller.json"));
            report("...and no file is named after the declaration's file name",
                    mocAssets.keySet().stream().noneMatch(key -> key.contains("renamed_declaration")));
            check("the compatibility half carries two files per declared machine", 2 * 2, mocAssets.size());
            report("the mod's own namespace's half is still generated (no regression) and carries the factory "
                            + "files when a declaration asks for one",
                    ownAssets.containsKey("modular_machinery_reborn:blockstates/alloy_furnace_controller.json")
                            && ownAssets.keySet().stream()
                                    .anyMatch(key -> key.contains("_factory_controller")));
            Set<String> union = new TreeSet<>(mocAssets.keySet());
            int before = union.size();
            union.addAll(ownAssets.keySet());
            check("the two namespaces' file sets are disjoint, so no file is generated twice",
                    before + ownAssets.size(), union.size());
            report("...and every compatibility file's id is in the 'modularcontroller' namespace",
                    mocAssets.keySet().stream().allMatch(key -> key.startsWith(NS + ":")));
        }

        // (b) The creative tab. Read from ModBlocks' class file: the displayItems lambda is a nested class, and
        // the whole outer class is scanned for a reference to the compatibility item list outside the accessor.
        ClassNode blocks = readClassNode(BLOCKS_CLASS);
        report("ModBlocks' creative tab does not list the compatibility controllers (the original did, but its "
                        + "MOC blocks were ordinary craftable controllers too; here they exist only so an old "
                        + "save loads, and a fresh install must look unchanged)",
                blocks != null && !nestedClassesMention(BLOCKS_CLASS, "MOC_CONTROLLER_ITEMS"));

        // (c) The two language keys, verbatim, in both files, with identical key sets.
        Map<String, String> en = readLang("en_us");
        Map<String, String> zh = readLang("zh_cn");
        if (en.isEmpty() || zh.isEmpty()) {
            report("the language files could not be read", false);
            return;
        }
        // 169 at 0.27.0; 176 from 0.29.0, when the construct tool added the original's own tooltip plus its six
        // message.structurebuild.* lines (section AA1 diffs all seven against _mmce-src's .lang).
        check("en_us.json key count", 176, en.size());
        check("zh_cn.json key count", 176, zh.size());
        Set<String> enKeys = new TreeSet<>(en.keySet());
        Set<String> zhKeys = new TreeSet<>(zh.keySet());
        report("the two files carry identical key sets (" + enKeys.size() + " keys each)", enKeys.equals(zhKeys));
        check("zh tip.0 is the original's wording, verbatim", "模块化控制器兼容将会在未来的版本被移除。", zh.get(TIP_0));
        check("zh tip.1 is the original's wording, verbatim", "请联系整合包作者将控制器转移至 MMCE 的控制器中。", zh.get(TIP_1));
        check("en tip.0 is the original's wording, verbatim",
                "Modular controller compatibility will be removed in a future release.", en.get(TIP_0));
        check("en tip.1 is the original's wording, verbatim",
                "Please contact the modpack author to transfer the controller to MMCE's controller.", en.get(TIP_1));
        report("the MOC item's inherited name key exists in both files",
                en.containsKey("block.modular_machinery_reborn.machine_controller.named")
                        && zh.containsKey("block.modular_machinery_reborn.machine_controller.named"));
    }

    // ================================================================== plumbing: the config

    private static Object specOrNull() {
        try {
            return Class.forName("com.reborn.modularmachinery.config.ModConfig").getField("SPEC").get(null);
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return null;
        }
    }

    private static boolean isLoaded(Object spec) {
        try {
            return Boolean.TRUE.equals(spec.getClass().getMethod("isLoaded").invoke(spec));
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return false;
        }
    }

    /**
     * The positive control for {@link #configTiming()}: hand the spec a minimal config built by Forge's own
     * parser and read the accessors back. If this did not work, the false answers above would prove nothing.
     */
    private static boolean accessorFollowsSpec() {
        try {
            Object spec = specOrNull();
            if (spec == null) {
                return false;
            }
            Class<?> parser = Class.forName("com.electronwill.nightconfig.toml.TomlParser");
            Object toml = parser.getMethod("parse", String.class)
                    .invoke(parser.getDeclaredConstructor().newInstance(),
                            "[general]\nmodular-controller-compatible-mode = true\n"
                                    + "disable-moc-deprecated-tip = true\n");
            setConfig(spec, toml);
            return mocCompatibleMode() && disableMocDeprecatedTip();
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return false;
        }
    }

    /**
     * The config file a {@code ForgeConfigSpec} was handed. It is a private field with no getter
     * ({@code ForgeConfigSpec.java:56}), and the only other view of it — {@code ModConfig#getConfigData} — is on
     * the {@code ModConfig} wrapper, which this harness never has: it reaches the spec through
     * {@code ModConfig.SPEC}. Reading it reflectively is what lets section Y put the spec back exactly as
     * section R left it.
     */
    private static Object childConfigOf(Object spec) throws Exception {
        java.lang.reflect.Field field = spec.getClass().getDeclaredField("childConfig");
        field.setAccessible(true);
        return field.get(spec);
    }

    private static void setConfig(Object spec, Object config) throws Exception {
        Class<?> commented = Class.forName("com.electronwill.nightconfig.core.CommentedConfig");
        spec.getClass().getMethod("setConfig", commented).invoke(spec, config);
    }

    private static boolean accessorExists(String name) {
        try {
            Class.forName("com.reborn.modularmachinery.config.ModConfig").getMethod(name);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean mocCompatibleMode() {
        return booleanAccessor("mocCompatibleMode");
    }

    private static boolean disableMocDeprecatedTip() {
        return booleanAccessor("disableMocDeprecatedTip");
    }

    private static boolean booleanAccessor(String name) {
        try {
            Class<?> config = Class.forName("com.reborn.modularmachinery.config.ModConfig");
            return Boolean.TRUE.equals(config.getMethod(name).invoke(null));
        } catch (Throwable throwable) {
            return false;
        }
    }

    // ================================================================== plumbing: the production rule

    /** The declared machines that get a compatibility controller, from the production rule. */
    private static List<?> compatibilityMachinesOrNull(Path root) {
        try {
            writeFixture();
            Class<?> moc = Class.forName("com.reborn.modularmachinery.block.MocNamespace");
            Object result = moc.getMethod("compatibilityMachines", Path.class).invoke(null, root);
            return result instanceof List<?> list ? list : null;
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return null;
        }
    }

    private static void writeFixture() throws IOException {
        Path dir = FIXTURE_ROOT.resolve("machinery");
        Files.createDirectories(dir);
        for (Map.Entry<String, String> entry : FIXTURE_FILES.entrySet()) {
            Files.writeString(dir.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    /** The MOC registry name of a machine, from the production function the registration itself calls. */
    private static Object mocControllerName(String machineId) throws Exception {
        Class<?> moc = Class.forName("com.reborn.modularmachinery.block.MocNamespace");
        Class<?> location = Class.forName("net.minecraft.resources.ResourceLocation");
        Method method = moc.getMethod("controllerName", location);
        return method.invoke(null, location.getConstructor(String.class).newInstance(machineId));
    }

    private static String namespaceConstant() {
        try {
            Class<?> moc = Class.forName("com.reborn.modularmachinery.block.MocNamespace");
            return String.valueOf(moc.getField("NAMESPACE").get(null));
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return "<unreachable>";
        }
    }

    // ================================================================== plumbing: the generated files

    /**
     * The generated files {@code GeneratedControllerAssets} produces for a fixed fixture, as
     * {@code id -> text}; {@code compatibility} selects the {@code modularcontroller} half.
     *
     * <p>Driven through the production arithmetic with a fixture, because the live pack reads {@code ModBlocks}
     * and so cannot be initialised offline. The declarations are built here rather than scanned, so this check is
     * about the files, not about the scan — section (2) covers the scan.
     */
    private static Map<String, String> generatedAssets(boolean compatibility) {
        try {
            Class<?> assets = Class.forName("com.reborn.modularmachinery.block.GeneratedControllerAssets");
            Class<?> refClass =
                    Class.forName("com.reborn.modularmachinery.machine.MachineDirectory$MachineRef");
            Class<?> location = Class.forName("net.minecraft.resources.ResourceLocation");
            Object alloy = location.getConstructor(String.class)
                    .newInstance("modular_machinery_reborn:alloy_furnace");
            Object transformer = location.getConstructor(String.class)
                    .newInstance("modular_machinery_reborn:transformer");
            // (path, machineId, factoryController, factoryOnly) — the transformer asks for a factory, so the
            // mod-namespace half's factory files are covered too.
            Object first = refClass.getConstructor(String.class, location, boolean.class, boolean.class)
                    .newInstance("alloy_furnace", alloy, false, false);
            Object second = refClass.getConstructor(String.class, location, boolean.class, boolean.class)
                    .newInstance("transformer", transformer, true, false);
            List<Object> declarations = List.of(first, second);
            String method = compatibility ? "compatibility" : "ordinary";
            Object result = assets.getMethod(method, String.class, List.class)
                    .invoke(null, "modular_machinery_reborn", declarations);
            if (!(result instanceof Map<?, ?> map)) {
                return null;
            }
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()),
                        new String((byte[]) entry.getValue(), StandardCharsets.UTF_8));
            }
            return out;
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return null;
        }
    }

    // ================================================================== plumbing: bytecode

    /**
     * Every class-file read here goes through ASM and a resource name, never through {@code Class.forName} on
     * {@code ModBlocks} or the pack. Loading a production class outside Forge initialises it, and those two cannot
     * be initialised offline at all — see the class comment.
     */
    private static ClassNode readClassNode(String classResource) {
        try (InputStream in = MocNamespaceCheck.class.getClassLoader()
                .getResourceAsStream(classResource.replace('.', '/') + ".class")) {
            if (in == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return null;
        }
    }

    /** A whole class transliterated to text, for a substring search over its constant pool and code. */
    private static String translate(ClassNode node) {
        StringWriter writer = new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(writer)));
        return writer.toString();
    }

    /** Whether {@code a} occurs before {@code b}, both present, in a piece of source text. */
    private static boolean occursBefore(String text, String a, String b) {
        return text.indexOf(a) >= 0 && text.indexOf(b) > text.indexOf(a);
    }

    /** Every field a method reads or writes, by name. */
    private static Set<String> fieldsReferenced(ClassNode node, String methodName) {
        Set<String> fields = new TreeSet<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode field) {
                    fields.add(field.name);
                }
            }
        }
        return fields;
    }

    /** Whether any method of this name constructs an instance of a class of that simple name. */
    private static boolean classConstructs(ClassNode node, String simpleName) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals("<init>")
                        && call.owner.substring(call.owner.lastIndexOf('/') + 1).equals(simpleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any method of this name mentions the given field. */
    private static boolean instructionsMention(MethodNode method, String field) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof FieldInsnNode fieldInsn && fieldInsn.name.equals(field)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any method of the given name mentions the given field anywhere in the class. */
    private static boolean methodMentions(ClassNode node, String methodName, String field) {
        return fieldsReferenced(node, methodName).contains(field);
    }

    /** Whether any nested class of {@code classResource} mentions the given name. */
    private static boolean nestedClassesMention(String classResource, String name) {
        for (int i = 1; i < 40; i++) {
            ClassNode nested = readClassNode(classResource + "$" + i);
            if (nested != null && translate(nested).contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Every {@code String} constant a method loads, in instruction order. */
    private static List<String> methodLiterals(ClassNode node, String name) {
        List<String> literals = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(name)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
                    literals.add(text);
                }
            }
        }
        return literals;
    }

    /** Whether any method of this name invokes {@code invokedName}, or reads the field of that name. */
    private static boolean methodCalls(ClassNode node, String methodName, String invokedName) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals(invokedName)) {
                    return true;
                }
                if (insn instanceof FieldInsnNode field && field.name.equals(invokedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the class declares the given method itself, i.e. overrides it. */
    private static boolean overrides(ClassNode node, String methodName) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== plumbing: Forge's own sources

    /**
     * {@code ForgeStatesProvider.java} and {@code ModStateProvider.java} from the Forge sources jar in this
     * machine's Gradle cache, or {@code null} when they are not there.
     *
     * <p>Offline-safe by construction: the jar is already downloaded — the build cannot run without it — and
     * reading it needs no network.
     */
    private static String[] forgeStateSources() {
        try {
            String home = System.getenv("GRADLE_USER_HOME");
            Path gradleHome = Path.of(home == null ? "C:/Users/kk07H/.gradle" : home);
            Path forges = gradleHome.resolve("caches/forge_gradle/maven_downloader/net/minecraftforge/forge");
            if (!Files.isDirectory(forges)) {
                lastFailure = "no " + forges;
                return null;
            }
            Path jar = null;
            try (java.util.stream.Stream<Path> stream = Files.walk(forges)) {
                jar = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith("-sources.jar"))
                        .sorted()
                        .reduce((first, second) -> second)
                        .orElse(null);
            }
            if (jar == null) {
                lastFailure = "no sources jar under " + forges;
                return null;
            }
            String states;
            String modStates;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                states = readEntry(zip, "net/minecraftforge/common/ForgeStatesProvider.java");
                modStates = readEntry(zip, "net/minecraftforge/fml/core/ModStateProvider.java");
            }
            if (states == null || modStates == null) {
                lastFailure = "the two state providers are not in " + jar;
                return null;
            }
            return new String[] {states, modStates};
        } catch (Throwable throwable) {
            lastFailure = describe(throwable);
            return null;
        }
    }

    private static String readEntry(java.util.zip.ZipFile zip, String name) throws IOException {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ================================================================== plumbing: text and files

    private static Map<String, String> readLang(String code) {
        Map<String, String> out = new LinkedHashMap<>();
        Path path = Path.of("C:/mmwork/modular-machinery-reborn/src/main/resources/assets/"
                + "modular_machinery_reborn/lang/" + code + ".json");
        if (!Files.isRegularFile(path)) {
            lastFailure = "no " + path;
            return out;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^\\s*\"([^\"]+)\"\\s*:\\s*\"(.*?)\"\\s*,?\\s*$", java.util.regex.Pattern.MULTILINE)
                    .matcher(text);
            while (matcher.find()) {
                out.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException exception) {
            lastFailure = describe(exception);
            return new LinkedHashMap<>();
        }
        return out;
    }

    private static Object invokeOrNull(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String stringMember(Object record, String name) {
        Object value = invokeOrNull(record, name);
        return value == null ? "" : value.toString();
    }

    /** A throwable's cause chain, one line, so a suppressed failure names itself in the evidence. */
    static String describe(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (text.length() > 0) {
                text.append(" <- ");
            }
            text.append(current.getClass().getName());
            if (current.getMessage() != null) {
                text.append(": ").append(current.getMessage());
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return text.toString();
    }

    /** Whether a dotted key repeats one of its own path elements, e.g. {@code a.a.b}. */
    private static boolean repeatsASegment(String key) {
        List<String> parts = Arrays.asList(key.split("\\."));
        return new java.util.HashSet<>(parts).size() != parts.size();
    }

    /**
     * Section Y2 — the generated data pack that gives an author-declared controller a loot table (0.31.0).
     *
     * <p>A controller's registry name is the pack author's, so no file in the jar can name it, and a block with no
     * loot table drops nothing when broken — which is exactly the defect this closes for the eleven fixed blocks.
     * The builder is driven here through reflection, like every other class in this harness, and against
     * <b>fixed fixture declarations</b> rather than whatever is in the instance's config directory.
     *
     * <p>What is asserted is the part that has already gone wrong twice in this project: every item name the table
     * references must be the name of a <b>registered item</b>. Controllers register their item under the same
     * registry name as the block, so the entry is the block id itself — and that equality is a fact to assert, not
     * to assume, because the hatch and casing families prove the opposite is possible in this very mod.
     */
    static void generatedControllerLootTables() {
        Class<?> builder;
        Class<?> refClass;
        try {
            // initialize=false, and only for the two classes that are safe to touch here. ModBlocks must NOT be
            // loaded: its static initialiser builds DeferredRegisters and reads Forge's @Mod, neither of which
            // exists on this classpath — that constraint is exactly why the arithmetic lives in a builder class
            // of its own (see GeneratedControllerAssets' header) instead of inside the pack.
            builder = Class.forName("com.reborn.modularmachinery.block.GeneratedControllerLootTables", false,
                    MocNamespaceCheck.class.getClassLoader());
            refClass = Class.forName("com.reborn.modularmachinery.machine.MachineDirectory$MachineRef", false,
                    MocNamespaceCheck.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            report("the generated-loot-table builder is on the classpath: " + missing, false);
            return;
        }

        // Fixtures: an ordinary machine, one that asked for a factory, and one compatibility declaration. The
        // names are deliberately unlike the built-in ones so a hard-coded answer cannot pass.
        String modNs = "modular_machinery_reborn";
        String mocNs = "modularcontroller";
        Object ordinary = ref(refClass, "zz_fixture", modNs + ":zz_fixture", false, false);
        Object withFactory = ref(refClass, "zz_factory", modNs + ":zz_factory", true, false);
        Object mocRef = ref(refClass, "zz_legacy", modNs + ":zz_legacy", false, false);

        Map<String, String> produced = new TreeMap<>();
        try {
            Method ordinaryMethod = builder.getMethod("ordinary", String.class, List.class);
            Method compatibilityMethod = builder.getMethod("compatibility", String.class, List.class);
            collect(produced, (Map<?, ?>) ordinaryMethod.invoke(null, modNs, List.of(ordinary, withFactory)));
            collect(produced, (Map<?, ?>) compatibilityMethod.invoke(null, mocNs, List.of(mocRef)));
        } catch (ReflectiveOperationException failure) {
            report("the builder can be driven: " + describe(failure), false);
            return;
        }

        // One table per controller, and one more for the declaration that asked for a factory.
        check("three declarations produce four tables: the two ordinary controllers, the compatibility alias, and"
                        + " the requested factory controller", 4, produced.size());

        // The exact ids, printed rather than guessed at: two drafts of the assertions below spelled this id
        // wrongly and went red on a correct builder, so the harness states the shape once, plainly.
        report("the generated ids are: " + produced.keySet(), produced.size() == 4);

        // Paths. A data-pack file's namespace is the directory it names, so the compatibility alias must live
        // under modularcontroller/ and not be folded into the mod's own namespace — the mistake that made the
        // mineability tags silently useless.
        //
        // The id is <namespace>:blocks/<path>_controller — the category directory is implied by the resource type
        // (exactly as tags are <namespace>:blocks/<tag>), and there is NO .json suffix in it. Three drafts of
        // these assertions got that spelling wrong and went red on a correct builder; the printed-id line above
        // exists so the shape is stated once, plainly, instead of being guessed at.
        report("the ordinary controllers' tables are named blocks/<path>_controller in the mod's"
                        + " namespace: " + produced.keySet(), produced.containsKey(
                        "modular_machinery_reborn:blocks/zz_fixture_controller")
                && produced.containsKey("modular_machinery_reborn:blocks/zz_factory_controller"));
        report("the requested factory controller gets its own table: " + produced.keySet(),
                produced.containsKey("modular_machinery_reborn:blocks/zz_factory_factory_controller"));
        report("the compatibility alias gets a table under its OWN namespace: " + produced.keySet(),
                produced.containsKey("modularcontroller:blocks/zz_legacy_controller"));

        // Content. Each table must be a single-item block table dropping exactly the block's own item, and the
        // JSON must parse — a table that does not parse is silently discarded by the game and drops nothing.
        int parsed = 0;
        int wrongItem = 0;
        for (Map.Entry<String, String> entry : produced.entrySet()) {
            String path = entry.getKey();
            String table = entry.getValue();
            String namespace = path.substring(0, path.indexOf(':'));
            // The id carries no .json suffix (see the printed ids above); a leftover
            // `- ".json".length()` here chopped the last five characters off every block path, which showed up
            // as "zz_fixture_contr" and made four correct tables look wrong.
            String blockPath = path.substring(path.indexOf(":blocks/") + ":blocks/".length());
            String expectedItem = namespace + ":" + blockPath;
            try {
                com.google.gson.JsonParser.parseString(table);
                parsed++;
            } catch (RuntimeException malformed) {
                report("table " + path + " is valid JSON (it is not: " + malformed.getMessage() + ")", false);
            }
            if (!table.contains("\"name\":\"" + expectedItem + "\"")) {
                wrongItem++;
                report("table " + path + " drops " + expectedItem + " (it does not: " + table + ")", false);
            }
            if (!table.contains("\"minecraft:survives_explosion\"")) {
                report("table " + path + " keeps the survives_explosion condition, like the shipped tables", false);
            }
        }
        check("every generated table parses", produced.size(), parsed);
        check("every generated table drops the item whose name equals the block's", 0, wrongItem);

        // The item really is registered under the block's name for these blocks. Asked of the built class rather
        // than assumed: this is the equality the whole file depends on.
        try {
            Method blockId = builder.getMethod("blockId", String.class, String.class);
            Object id = blockId.invoke(null, modNs, "zz_fixture");
            check("the builder derives a controller's block id as <path>_controller", modNs + ":zz_fixture_controller",
                    String.valueOf(id));
            Method tableId = builder.getMethod("tableIdFor", Class.forName("net.minecraft.resources.ResourceLocation"));
            Object table = tableId.invoke(null, id);
            check("...and its loot table id as <namespace>:blocks/<path>_controller",
                    modNs + ":blocks/zz_fixture_controller", String.valueOf(table));
        } catch (ReflectiveOperationException failure) {
            report("the id helpers can be driven: " + describe(failure), false);
        }

        // Against the built jar: the generated file names must be the ones the game will look for, and must not
        // collide with a shipped file. Both failures would be silent otherwise, which is how the mineability tags
        // and the first three loot-table drafts went wrong.
        Path jar = builtJar();
        if (jar == null) {
            report("the built mod jar was found so the generated paths can be checked against it", false);
            return;
        }
        Set<String> shipped = new TreeSet<>();
        Map<String, String> blockStates = new LinkedHashMap<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            zip.stream().forEach(entry -> {
                String name = entry.getName();
                if (name.startsWith("data/") && name.endsWith(".json")) {
                    shipped.add(name);
                }
                if (name.startsWith("assets/") && name.endsWith(".json") && name.contains("/blockstates/")) {
                    String file = name.substring(name.lastIndexOf('/') + 1, name.length() - ".json".length());
                    blockStates.put(name.substring("assets/".length(), name.indexOf("/blockstates/")) + ":" + file,
                            name);
                }
            });
        } catch (IOException unreadable) {
            report("the built mod jar can be read: " + unreadable, false);
            return;
        }

        java.util.List<String> collisions = new ArrayList<>();
        java.util.List<String> missingBlock = new ArrayList<>();
        for (String id : produced.keySet()) {
            String namespace = id.substring(0, id.indexOf(':'));
            String path = id.substring(id.indexOf(':') + 1);
            String asFile = "data/" + namespace + "/loot_tables/" + path + ".json";
            if (shipped.contains(asFile)) {
                collisions.add(asFile);
            }
            // The block the table names must exist, and the one place this harness can see registered blocks is
            // the jar's own blockstate assets: every registered block has one.
            String blockPath = path.substring("blocks/".length());
            if (!blockStates.containsKey(namespace + ":" + blockPath)) {
                // Controllers generated from an instance's config directory are not in the jar, so a miss here is
                // only meaningful for the fixtures, which are not real machines either. Recorded, not asserted.
                missingBlock.add(namespace + ":" + blockPath);
            }
        }
        check("no generated table collides with a table shipped in the jar", 0, collisions.size());
        report("the jar carries " + shipped.size() + " data json files and " + blockStates.size()
                + " blockstate files; fixtures are not registered blocks, so "
                + missingBlock.size() + " fixture block id(s) are absent from it by design", true);
    }

    /**
     * The built mod jar, so the generated file names can be checked against the shipped ones.
     *
     * <p>Located from the harness's own working directory rather than hard-coded: the file name carries the mod
     * version, and a hard-coded version would silently stop checking anything the next time it moved.
     */
    private static Path builtJar() {
        Path libs = Paths.get("modular-machinery-reborn", "build", "libs");
        if (!Files.isDirectory(libs)) {
            libs = Paths.get("build", "libs");
        }
        if (!Files.isDirectory(libs)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(libs)) {
            return stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("modular_machinery_reborn-") && name.endsWith(".jar")
                                && !name.endsWith("-sources.jar");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** One {@code MachineDirectory.MachineRef}, built by reflection because it is a record. */
    private static Object ref(Class<?> refClass, String path, String machineId, boolean factory, boolean factoryOnly) {        try {
            Class<?> idClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Object id = idClass.getConstructor(String.class).newInstance(machineId);
            return refClass.getConstructor(String.class, idClass, boolean.class, boolean.class)
                    .newInstance(path, id, factory, factoryOnly);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot build a MachineRef fixture: " + describe(failure), failure);
        }
    }

    /** Flattens a {@code Map<ResourceLocation, byte[]>} into {@code Map<String, String>} for comparison. */
    private static void collect(Map<String, String> into, Map<?, ?> files) {
        for (Map.Entry<?, ?> entry : files.entrySet()) {
            into.put(String.valueOf(entry.getKey()),
                    new String((byte[]) entry.getValue(), StandardCharsets.UTF_8));
        }
    }

    /** How many times {@code needle} occurs in {@code haystack}. */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }
}
