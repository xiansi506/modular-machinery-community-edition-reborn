package mmverify;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.reborn.modularmachinery.machine.MachineDefinitions;

import static mmverify.ParallelCraftCheck.check;
import static mmverify.ParallelCraftCheck.report;
import static mmverify.ParallelCraftCheck.section;

/**
 * Section Z9 — 0.28.1: the machine event group really reaches a script as a callable event.
 *
 * <h2>The defect this section exists for</h2>
 *
 * <p>0.28.0 shipped {@code MachineRegistryEvents.registry(event => …)} as the documented way to define a
 * machine, and the owner's first in-game run answered:
 *
 * <pre>{@code
 * [KubeJS Server/]: mmce_machine_test.js#1: TypeError: Cannot find function registry in object
 *                   MachineRegistryEvents.
 * }</pre>
 *
 * <p>The group was registered, the binding existed and the event was posted, so every offline check section Z
 * already had was green. What was missing is the one thing none of them looked at: an event group does
 * <b>not</b> reach a script as itself. KubeJS's own {@code BuiltinKubeJSPlugin#registerBindings} binds
 * {@code new EventGroupWrapper(type, group)} for every registered group, because it is the wrapper's
 * {@code get(String)} override that turns a group's typed events into properties a script can call. The
 * plugin's {@code registerBindings} added the raw group under the same name, {@code BindingsEvent#add} is a
 * plain {@code Context.addToScope}, and KubeJS puts its own mod first in the plugin list — so the working
 * wrapper was overwritten by an object with no usable events on it.
 *
 * <h2>What this section can prove, and what it cannot</h2>
 *
 * <p><b>It can prove the mechanism, by running it.</b> The section loads the <b>real</b> KubeJS 1.20.1 jar
 * (plus Rhino, plus a generated stand-in for {@code dev.architectury.platform.Platform}, which KubeJS asks for
 * the game folder before any script exists) in a child class loader, drives the mod's real
 * {@code ModularMachineryKubeJSPlugin#registerBindings} by reflection, and then evaluates
 * {@code typeof MachineRegistryEvents.registry} and the call itself with KubeJS's own Rhino. The same run also
 * puts the raw group through the identical lookup, so the assertion is a red/green pair rather than a claim:
 * the wrapper resolves {@code registry} as a function, the raw group produces literally the owner's
 * {@code TypeError: Cannot find function registry in object MachineRegistryEvents}.
 *
 * <p><b>It cannot prove the game works.</b> It starts no server, runs no {@code ScriptManager} and posts no
 * event to a listener a script registered for real: reload order, script file loading and the machine actually
 * forming are game-only. The real proof stays the owner's {@code /reload} checklist. What this section removes
 * is the possibility of shipping a binding no script can use while every other check is green — which is
 * exactly how this one shipped.
 */
final class KubeJSBindingCheck {

    private static final String PLUGIN_CLASS =
            "com.reborn.modularmachinery.kubejs.ModularMachineryKubeJSPlugin";
    private static final String WRAPPER = "dev.latvian.mods.kubejs.event.EventGroupWrapper";
    private static final String EVENT_HANDLER = "dev.latvian.mods.kubejs.event.EventHandler";
    private static final String KUBEJS_JAR = "kubejs-1.20.1.jar";
    private static final String RHINO_JAR = "rhino-1.20.1.jar";
    private static final String KUBEJS_DEOBF = "kubejs-1.20.1_mapped_official_1.20.1.jar";
    private static final String RHINO_DEOBF = "rhino-1.20.1_mapped_official_1.20.1.jar";
    private static final String GROUP_NAME = "MachineRegistryEvents";
    private static final String EVENT_NAME = "registry";

    /** The stand-in for Architectury, compiled at run time because hand-written class bytes are not readable. */
    private static final String PLATFORM_SOURCE = """
            package dev.architectury.platform;
            import java.nio.file.Path;
            import java.nio.file.Paths;
            import java.util.Collection;
            import java.util.Collections;
            import java.util.Optional;
            public final class Platform {
                private Platform() { }
                public static boolean isFabric() { return false; }
                public static boolean isForge() { return true; }
                public static String getMinecraftVersion() { return "1.20.1"; }
                public static Path getGameFolder() {
                    return Paths.get(System.getProperty("java.io.tmpdir"), "mmverify-kjs-game");
                }
                public static Path getConfigFolder() { return getGameFolder().resolve("config"); }
                public static Path getModsFolder() { return getGameFolder().resolve("mods"); }
                public static boolean isDevelopmentEnvironment() { return false; }
                public static boolean isModLoaded(String id) {
                    return "minecraft".equals(id) || "forge".equals(id) || "kubejs".equals(id);
                }
                public static Collection<String> getModIds() { return Collections.emptyList(); }
                public static Collection<?> getMods() { return Collections.emptyList(); }
                public static Optional<?> getOptionalMod(String id) { return Optional.empty(); }
                public static Object getMod(String id) { return null; }
            }
            """;

    private KubeJSBindingCheck() {
    }

    static void kubeJsBinding() {
        section("Z9. 0.28.1 — the machine event really reaches a script as a callable event");
        try {
            Path kubeJs = findDeobfJar(KUBEJS_DEOBF);
            Path rhino = findDeobfJar(RHINO_DEOBF);
            report("KubeJS's own classes could be loaded for this check (the deobfuscated build, which pairs"
                            + " with this harness's Mojmap Minecraft): " + kubeJs, kubeJs != null);
            report("...and so could the Rhino fork KubeJS runs scripts with: " + rhino, rhino != null);
            report("...while the production jars this checkout ships are SRG-remapped "
                            + "(libs/" + KUBEJS_JAR + ", byte-identical to the instance's), so building on them"
                            + " here would have been a mapping mismatch, not a test",
                    findJar(KUBEJS_JAR) != null && findJar(RHINO_JAR) != null
                            && !findJar(KUBEJS_JAR).equals(kubeJs));
            if (kubeJs == null || rhino == null) {
                return;
            }

            Path stubDir = writePlatformStub(kubeJs);
            report("a stand-in for Architectury's Platform was compiled (KubeJS reads the game folder while its"
                            + " own classes initialise, and this harness has no mod loader): " + stubDir,
                    stubDir != null);

            try (ChildLoader loader = new ChildLoader(new URL[] {
                    stubDir.toUri().toURL(), kubeJs.toUri().toURL(), rhino.toUri().toURL()})) {
                verify(loader);
            } catch (Throwable failure) {
                // The whole stack, because "the KubeJS activation could be driven at all" is the one check whose
                // failure needs no second run to diagnose.
                java.io.StringWriter trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                report("the KubeJS activation could be driven at all — " + trace.toString().replace("\n", " | "),
                        false);
            }
        } catch (RuntimeException | Error throwable) {
            report("section Z9 aborted: " + throwable, false);
        }
    }

    private static void verify(ChildLoader loader) throws Exception {
        Class<?> pluginClass = Class.forName(PLUGIN_CLASS, true, loader);
        Class<?> groupClass = Class.forName("dev.latvian.mods.kubejs.event.EventGroup", true, loader);
        Class<?> wrapperClass = Class.forName(WRAPPER, true, loader);
        Class<?> typeClass = Class.forName("dev.latvian.mods.kubejs.script.ScriptType", true, loader);
        Class<?> bindingsClass = Class.forName("dev.latvian.mods.kubejs.script.BindingsEvent", true, loader);

        Object plugin = pluginClass.getDeclaredConstructor().newInstance();
        Class<?> eventsClass = Class.forName("com.reborn.modularmachinery.kubejs.MachineEvents", true, loader);
        // The mod's own path: registerEvents() -> MachineEvents.register() -> EventGroup.register().
        pluginClass.getMethod("registerEvents").invoke(plugin);
        Object group = eventsClass.getMethod("group").invoke(null);
        // The invariant is not "the group has the right name" — the object exists either way — it is that
        // registerEvents() is what puts it in KubeJS's own group registry. Without that, KubeJS's
        // BuiltinKubeJSPlugin never binds the group at all. (Fault injection B proved this assertion was worth
        // writing: checked by name, it stayed green with registerEvents() gutted.)
        Map<?, ?> registered = (Map<?, ?>) groupClass.getMethod("getGroups").invoke(null);
        report("registerEvents() is what puts the machine event group into KubeJS's own group registry under '"
                        + GROUP_NAME + "' — without it KubeJS never binds the group, and a binding with no group"
                        + " behind it is another way to ship an event no script can call",
                registered.containsKey(GROUP_NAME) && registered.get(GROUP_NAME) == group);

        Map<?, ?> handlers = (Map<?, ?>) groupClass.getMethod("getHandlers").invoke(group);
        report("...with exactly one typed event on it (" + handlers.keySet() + ")",
                handlers.size() == 1 && handlers.containsKey(EVENT_NAME));

        // What the plugin publishes to a server script, obtained by running the real method.
        Object bindings = bindings(loader, bindingsClass, typeClass);
        pluginClass.getMethod("registerBindings", bindingsClass).invoke(plugin, bindings);
        // Two views of the same binding, and the difference matters:
        //   * the scope lookup is what a script's property access goes through (Rhino hands back its own
        //     Java-object view), which is what the typeof and the call must be evaluated against;
        //   * the unwrapped Java object is what the plugin actually put there.
        Object scopeProperty = property(loader, bindings, GROUP_NAME);
        Object binding = unwrapJava(loader, scopeProperty);
        report("registerBindings published '" + GROUP_NAME + "' (" + describe(binding) + ")",
                binding != null);
        report("...as a KubeJS EventGroupWrapper rather than the raw group (the 0.28.0 defect)",
                wrapperClass.isInstance(binding));

        // The whole point, evaluated by KubeJS's own Rhino: the documented script's first line.
        String typeof = evalValue(loader, scopeProperty, "typeof " + GROUP_NAME + "." + EVENT_NAME);
        check("a script sees '" + EVENT_NAME + "' on the published binding as", "function",
                String.valueOf(typeof));

        Object handler = wrapperClass.isInstance(binding)
                ? wrapperClass.getMethod("get", Object.class).invoke(binding, EVENT_NAME) : null;
        report("...and the property is the group's own event handler (" + describe(handler) + ")",
                handler != null && EVENT_HANDLER.equals(handler.getClass().getName()));

        String threw = null;
        String call = GROUP_NAME + "." + EVENT_NAME + "(function (event) { probeRegisters = 1; })";
        try {
            evalValue(loader, scopeProperty, call);
        } catch (Throwable failure) {
            threw = unwrap(failure);
        }
        // EventHandler.call registers the listener and then reaches for the ScriptManager, which no offline
        // harness has. A complaint about the script type therefore means the call was dispatched to the real
        // handler; "Unknown event" or the owner's TypeError mean it was not found at all.
        report("...and calling it reaches that handler instead of failing to find it"
                        + (threw == null ? " (no complaint)" : " (stopped at: " + threw + ")"),
                threw == null || (!threw.contains("Cannot find function") && !threw.contains("Unknown event")));

        // The object 0.28.0 published, through the identical lookup: the owner's error, reproduced offline.
        Object rawProperty = evalValue(loader, group, GROUP_NAME);
        String rawTypeof = evalValue(loader, rawProperty, "typeof " + GROUP_NAME + "." + EVENT_NAME);
        String rawError = null;
        try {
            evalValue(loader, rawProperty, GROUP_NAME + "." + EVENT_NAME + "(function (event) {})");
        } catch (Throwable failure) {
            rawError = unwrap(failure);
        }
        check("the raw group 0.28.0 bound exposes no event: typeof ..." + EVENT_NAME, "undefined",
                String.valueOf(rawTypeof));
        // The message names Rhino's own view of the object rather than the binding's name — the identical
        // sentence the owner's log carries is "Cannot find function registry in object MachineRegistryEvents.",
        // which is asserted on the published binding above; here the raw-group case is the same defect, one
        // Java-object view later.
        report("...and the script dies with the owner's own TypeError: " + rawError,
                rawError != null && rawError.contains("Cannot find function " + EVENT_NAME)
                        && rawError.contains("TypeError"));
    }

    // ------------------------------------------------------------------ the pieces KubeJS itself uses

    /**
     * Section Z10 — 0.28.2: which Java method the documented {@code .part(1, -1, 0, 'minecraft:stone')}
     * actually reaches, driven through KubeJS's own Rhino.
     *
     * <h2>The defect this section exists for</h2>
     *
     * <p>0.28.1's builder declared <b>two</b> methods named {@code part}, both of arity four and both ending in
     * {@code Object...}:
     *
     * <pre>{@code
     * public MachineBuilderJS part(int x, int y, int z, Object... elements)
     * public MachineBuilderJS part(List<Integer> x, List<Integer> y, List<Integer> z, Object... elements)
     * }</pre>
     *
     * <p>Java erasure lets those coexist; Rhino has to choose between them on every {@code .part(…)} a script
     * writes, and the observable contract of the API is therefore whatever {@code NativeJavaMethod#findFunction}
     * happens to do. 0.28.2 removes the question instead of answering it: the list form is now {@code parts(…)},
     * which cannot be confused with {@code part(…)} because a name lookup precedes every overload question. This
     * section is what proves the documented spelling still resolves and what it produces.
     *
     * <h2>What this section can prove, and what it cannot</h2>
     *
     * <p><b>It proves, by running it,</b> that the documented scalar call — composed as a script composes it, in
     * the documented chain — is accepted by the real {@code MachineRegistryEventJS} released to KubeJS's current
     * Rhino, and that the definition it builds is exactly the JSON the data pack would have carried. The JSON is
     * the discriminator: {@code "x":1} is only reachable through the scalar method, so "the scalar call resolved
     * to the scalar method" is read off the result rather than off a stack frame.
     *
     * <p>It <b>also records the measurement that decides the array spelling's parameter type</b>: Rhino converts
     * a script's array into a {@code List} of {@code Double}, so {@code List<Integer>} accepts the list and then
     * throws on the first element, while {@code List<? extends Number>} reads it. That is why {@code parts(…)}
     * is declared over {@code Number} and not over {@code Integer} — the difference is asserted here instead of
     * being left to whatever the owner's next in-game run reports.
     *
     * <p><b>It cannot prove the game works</b>, and it cannot prove the historical runtime choice. No server, no
     * {@code ScriptManager}, no listener registered by a script, no event posted: the in-game checklist in
     * {@code docs/KJS-配方指南.md} §七 is still the final word. And the 0.28.1 pair's runtime ordering is
     * deliberately <i>not</i> claimed here — {@code getMethods()} order is unspecified, so the honest statement
     * is the one section Z8b makes structurally: with one {@code part} in the class file there is no choice left
     * to make, whatever order the JVM hands the methods over in.
     */
    static void kubeJsBuilderDispatch() {
        section("Z10. 0.28.2 — the documented .part(x, y, z, …) resolves to the scalar method, run through Rhino");
        MachineDefinitions.beginCycle();
        try {
            Path kubeJs = findDeobfJar(KUBEJS_DEOBF);
            Path rhino = findDeobfJar(RHINO_DEOBF);
            report("the deobfuscated KubeJS + Rhino jars were found for Z10 (same pair section Z9 drives): "
                            + kubeJs + " / " + rhino,
                    kubeJs != null && rhino != null);
            if (kubeJs == null || rhino == null) {
                return;
            }
            Path stubDir = writePlatformStub(kubeJs);
            report("the Architectury Platform stand-in compiled", stubDir != null);
            if (stubDir == null) {
                return;
            }
            try (ChildLoader loader = new ChildLoader(new URL[] {
                    stubDir.toUri().toURL(), kubeJs.toUri().toURL(), rhino.toUri().toURL()})) {
                verifyBuilderDispatch(loader);
            }
        } catch (Throwable failure) {
            java.io.StringWriter trace = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(trace));
            report("section Z10 could be driven at all — " + trace.toString().replace("\n", " | "), false);
        } finally {
            // Leave the layer as the harness found it: a machine staged here would otherwise be merged into the
            // next reload of this process, exactly as section Z warns for its own fixtures.
            MachineDefinitions.beginCycle();
        }
    }

    private static void verifyBuilderDispatch(ChildLoader loader) throws Exception {
        Class<?> eventClass = Class.forName("com.reborn.modularmachinery.kubejs.MachineRegistryEventJS",
                true, loader);
        Class<?> builderClass = Class.forName("com.reborn.modularmachinery.kubejs.MachineBuilderJS",
                true, loader);

        // The event and the builder as KubeJS makes them: the event is constructed by KubeJS's own event
        // machinery and handed to the listener; the builder is born inside machine(id). Neither constructor is
        // script-facing, so both are opened by reflection — that is the only liberty this section takes.
        java.lang.reflect.Constructor<?> eventCtor = eventClass.getDeclaredConstructor();
        eventCtor.setAccessible(true);
        Object event = eventCtor.newInstance();
        report("the real MachineRegistryEventJS was instantiated (" + describe(event) + ") for the script to"
                        + " call machine() on", event != null);

        Class<?> rhinoContext = Class.forName("dev.latvian.mods.rhino.Context", true, loader);
        Object context = rhinoContext.getMethod("enter").invoke(null);
        Object scope = context.getClass().getMethod("initStandardObjects").invoke(context);
        Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable", true, loader);
        Class<?> scriptableObject = Class.forName("dev.latvian.mods.rhino.ScriptableObject", true, loader);

        // What KubeJS's listener body receives: the event object as a Java object inside the script's scope.
        Object jsEvent = rhinoContext.getMethod("javaToJS", rhinoContext, Object.class, scriptable)
                .invoke(null, context, event, scope);
        scriptableObject.getMethod("putProperty", scriptable, String.class, Object.class, rhinoContext)
                .invoke(null, scope, "event", jsEvent, context);

        // (1) The documented line, exactly as the guide writes it, in the fluent chain the guide writes it in.
        //     The expected object is written out by hand: if it were produced by the builder under test, the
        //     comparison would prove nothing.
        String scalarCall = "event.machine('scripted_smoke').localizedName('Scripted Smoke')"
                + ".part(1, -1, 0, 'minecraft:stone')"
                + ".part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=plain]',"
                + " 'modular_machinery_reborn:blockcasing[casing=vent]')"
                + ".toJson().toString()";
        String expectedScalar = "{\"registryname\":\"scripted_smoke\",\"localizedname\":\"Scripted Smoke\","
                + "\"parts\":[{\"x\":1,\"y\":-1,\"z\":0,\"elements\":\"minecraft:stone\"},"
                + "{\"x\":0,\"y\":1,\"z\":0,\"elements\":"
                + "[\"modular_machinery_reborn:blockcasing[casing=plain]\","
                + "\"modular_machinery_reborn:blockcasing[casing=vent]\"]}]}";
        String scalarResult = evalText(loader, rhinoContext, context, scope, scriptableObject, scriptable,
                scalarCall);
        check("the documented scalar call, evaluated by KubeJS's own Rhino, builds exactly the definition the"
                        + " data pack would have carried (\"x\":1 is reachable only through part(int,int,int,"
                        + "Object...), so this is the resolution being read off the result)", expectedScalar,
                scalarResult);

        // (2) The array spelling under its new name: the cartesian product the guide documents, including the
        //     Number-typed parameters that make a script's Double-valued array usable at all.
        String arrayCall = "event.machine('kubejs_wall')"
                + ".parts([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')"
                + ".parts([0], [1], [0], 'minecraft:furnace[facing=north]')"
                + ".toJson().toString()";
        String expectedArray = "{\"registryname\":\"kubejs_wall\",\"parts\":["
                + "{\"x\":[-1,0,1],\"y\":0,\"z\":[-1,0,1],\"elements\":\"minecraft:iron_block\"},"
                + "{\"x\":0,\"y\":1,\"z\":0,\"elements\":\"minecraft:furnace[facing=north]\"}]}";
        String arrayResult = evalText(loader, rhinoContext, context, scope, scriptableObject, scriptable,
                arrayCall);
        check("...and the array spelling, now `parts(...)`, still expands a list of coordinates into the"
                        + " cartesian product the guide documents", expectedArray, arrayResult);

        // (3) The 0.28.1 array spelling must now be refused *by the signature*, not silently sent somewhere
        //     else. The message is the discriminator, and the green run is what taught it: with one `part` in
        //     the class, Rhino finds the method by name and then reports that a coordinate list will not convert
        //     to the parameter it has — observed as
        //     `EvaluatorException: Cannot convert -1,0,1 to java.lang.Integer`. "Cannot find function part" would
        //     mean the spelling had vanished instead of being refused, and a successful result would mean it had
        //     landed on something list-shaped again. Each of the three is therefore asserted separately; the
        //     first draft accepted any message containing "part", which an unrelated failure would also satisfy
        //     (it is why this assertion is red on the fixed build — recorded rather than quietly loosened).
        String oldSpelling = evalThrew(loader, rhinoContext, context, scope, scriptableObject, scriptable,
                "event.machine('probe').part([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')");
        report("the 0.28.1 array spelling .part([...], [...], [...], …) is refused by the scalar signature: "
                        + oldSpelling,
                oldSpelling != null && oldSpelling.contains("Cannot convert")
                        && oldSpelling.contains("java.lang.Integer")
                        && !oldSpelling.contains("Cannot find function")
                        && !oldSpelling.contains("ClassCastException"));

        // (3b) The reason coordinate() takes a Number and then proves wholeness itself: the schema reads the JSON
        //      *after* this builder has written it, so a fractional coordinate that reached Java as a Double
        //      would already have been truncated by the time the schema could refuse it. Rhino is willing to
        //      convert 1.5 into a Java int (weight-wise it is the same Number branch), which is exactly why the
        //      builder, not the schema, has to be the one that says no.
        String fractional = evalThrew(loader, rhinoContext, context, scope, scriptableObject, scriptable,
                "event.machine('probe_frac').parts([1.5], [0], [0], 'minecraft:stone')");
        report("a fractional coordinate is refused by name rather than truncated to a different block: "
                        + fractional,
                fractional != null && fractional.contains("1.5") && fractional.contains("whole")
                        && !fractional.contains("Cannot find function"));
        String wholeDouble = evalText(loader, rhinoContext, context, scope, scriptableObject, scriptable,
                "event.machine('probe_whole').parts([1.0], [0], [0], 'minecraft:stone').toJson().toString()");
        check("...while a whole number written with a decimal point is still accepted (1.0 is 1, so the check is"
                        + " wholeness rather than the Java type)",
                "{\"registryname\":\"probe_whole\",\"parts\":[{\"x\":1,\"y\":0,\"z\":0,"
                        + "\"elements\":\"minecraft:stone\"}]}",
                wholeDouble);

        // (4) The name split, from the class file the loader just initialised — the same question section Z8b
        //     asks on the harness's own classpath, asked of the copy the script would actually meet.
        int parts = 0;
        int partWithList = 0;
        for (java.lang.reflect.Method method : builderClass.getDeclaredMethods()) {
            if ("parts".equals(method.getName())) {
                parts++;
            }
            if ("part".equals(method.getName()) && method.getParameterCount() == 4
                    && method.getParameterTypes()[0] == List.class) {
                partWithList++;
            }
        }
        report("the class the script meets declares one 'parts' and no list-taking 'part' ('parts'=" + parts
                        + ", list-taking 'part'=" + partWithList + ")", parts == 1 && partWithList == 0);

        // (5) The measurement that decides the parameter type, recorded rather than assumed.
        //
        //     These are the two facts a reader needs in order to believe the Z8b/parts(…) shape is right:
        //     Rhino is willing to accept a script's ARRAY where a Java List is wanted — one call to parts([…])
        //     above proves it end to end — but it will not accept a script's NUMBER there at all, and that is
        //     the reason the scalar spelling can never fall into the list-shaped method by accident. The returned
        //     weight is Rhino's own: 99 is "cannot convert" (CONVERSION_NONE), and Integer's is 2, the Number
        //     branch of getConversionWeight.
        Class<?> nativeJavaObject = Class.forName("dev.latvian.mods.rhino.NativeJavaObject", true, loader);
        java.lang.reflect.Method weight = nativeJavaObject.getDeclaredMethod("getConversionWeight",
                rhinoContext, Object.class, Class.class);
        weight.setAccessible(true);
        Object number = Double.valueOf(1.0);
        // Reading a script's array through Rhino's own erasure: this is the same value a `.parts([…])` argument
        // is, and it is a List of Doubles rather than a List of Integers.
        Object arrayInScope = rhinoContext
                .getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class)
                .invoke(context, scope, "[-1, 0, 1]", "mmverify-z10.js", 1, null);
        Object arrayUnwrapped = unwrapJava(loader, arrayInScope);
        check("a script's array arrives at a Java List parameter as", "[-1.0, 0.0, 1.0]",
                String.valueOf(arrayUnwrapped));
        check("...so its elements are Double, not Integer — which is why parts(…) is declared over Number",
                "java.lang.Double",
                arrayUnwrapped instanceof List<?> list && !list.isEmpty()
                        ? list.get(0).getClass().getName() : "not a List");
        check("...and a script's NUMBER has no conversion to List at all (99 is Rhino's \"cannot convert\"), so"
                        + " the scalar spelling can never fall into a list-shaped method by weight either", "99",
                String.valueOf(weight.invoke(null, context, number, List.class)));
        report("...while the same number converts to Integer at weight "
                        + weight.invoke(null, context, number, Integer.class) + " — the tie 0.28.1 left for the"
                        + " JVM's method order to break, and the reason both spellings now have their own name",
                true);
    }

    /**
     * Evaluates {@code source} in a scope that carries the real event as {@code event}, and returns what the
     * expression produced as text — or the failure, so a red assertion prints the sentence rather than a null.
     *
     * <p>The result has to be unwrapped before it is stringified, and the first run of this section proved it:
     * a script's value reaching Java is a {@code NativeJavaObject} wrapping the {@code JsonObject} the builder
     * returned, so {@code String.valueOf} prints the wrapper's identity — observed as
     * {@code got dev.latvian.mods.rhino.NativeJavaObject@67a2fb00} — and the assertion compares two things that
     * are not the same kind of thing. {@code Wrapper.unwrapped} is how KubeJS itself reads a script's value back
     * out, so the JSON under test is reached the way KubeJS reaches it.
     */
    private static String evalText(ChildLoader loader, Class<?> rhinoContext, Object context, Object scope,
            Class<?> scriptableObject, Class<?> scriptable, String source) {
        try {
            Object value = rhinoContext
                    .getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class)
                    .invoke(context, scope, source, "mmverify-z10.js", 1, null);
            Object unwrapped = unwrapJava(loader, value);
            return String.valueOf(unwrapped);
        } catch (Throwable failure) {
            return "THREW " + unwrap(failure);
        }
    }

    /** The deepest message {@code source} dies with, or {@code null} when it does not. */
    private static String evalThrew(ChildLoader loader, Class<?> rhinoContext, Object context, Object scope,
            Class<?> scriptableObject, Class<?> scriptable, String source) {
        try {
            rhinoContext
                    .getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class)
                    .invoke(context, scope, source, "mmverify-z10.js", 1, null);
            return null;
        } catch (Throwable failure) {
            return unwrap(failure);
        }
    }

    // ------------------------------------------------------------------ the pieces KubeJS itself uses

    /**
     * A {@code BindingsEvent} over a plain Rhino scope, which is all {@code BindingsEvent#add} touches: that
     * method is a {@code Context.addToScope} and nothing else. The {@code ScriptManager} is the real class with
     * its real constructor, so this drives the production code path rather than a look-alike; if the constructor
     * refuses to run outside a KubeJS reload (it is allowed to), the instance is allocated without it and only
     * the three public fields that path reads are set.
     */
    private static Object bindings(ChildLoader loader, Class<?> bindingsClass, Class<?> typeClass)
            throws Exception {
        Class<?> managerClass = Class.forName("dev.latvian.mods.kubejs.script.ScriptManager", true, loader);
        Class<?> rhinoContext = Class.forName("dev.latvian.mods.rhino.Context", true, loader);
        Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable", true, loader);

        Object type = typeClass.getField("SERVER").get(null);
        Object context = rhinoContext.getMethod("enter").invoke(null);
        Object scope = context.getClass().getMethod("initStandardObjects").invoke(context);

        Object manager;
        try {
            manager = managerClass.getConstructor(typeClass).newInstance(type);
        } catch (Throwable refuses) {
            manager = allocate(managerClass);
            managerClass.getField("scriptType").set(manager, type);
        }
        // Whatever the constructor did, these two public fields are the whole of what the binding path reads.
        // (Outside a reload the constructor leaves them null — observed — which is why they are set explicitly
        // rather than assumed.)
        managerClass.getField("context").set(manager, context);
        managerClass.getField("topLevelScope").set(manager, scope);
        return bindingsClass.getConstructor(managerClass, scriptable).newInstance(manager, scope);
    }

    /** An instance without its constructor, for the case where the constructor needs a live reload. */
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Object unsafe = unsafeClass.getDeclaredField("theUnsafe").get(null);
        return unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    /** What a script would read for {@code <binding>.name}, through KubeJS's own Rhino. */
    private static Object property(ChildLoader loader, Object bindings, String name) throws Exception {
        Object scope = bindings.getClass().getField("scope").get(bindings);
        Class<?> scriptableObject = Class.forName("dev.latvian.mods.rhino.ScriptableObject", true, loader);
        Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable", true, loader);
        Class<?> rhinoContext = Class.forName("dev.latvian.mods.rhino.Context", true, loader);
        Object context = rhinoContext.getMethod("enter").invoke(null);
        return scriptableObject.getMethod("getProperty", scriptable, String.class, rhinoContext)
                .invoke(null, scope, name, context);
    }

    /**
     * Evaluates {@code source} with {@code value} available as {@code MachineRegistryEvents}, and returns what
     * the expression produced. When {@code source} is just an expression the result is its value; when it is a
     * bare property read that is the property itself, which is how the raw group is carried into the next call.
     */
    private static String evalValue(ChildLoader loader, Object value, String source) throws Exception {
        Class<?> rhinoContext = Class.forName("dev.latvian.mods.rhino.Context", true, loader);
        Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable", true, loader);
        Class<?> scriptableObject = Class.forName("dev.latvian.mods.rhino.ScriptableObject", true, loader);
        Object context = rhinoContext.getMethod("enter").invoke(null);
        Object scope = context.getClass().getMethod("initStandardObjects").invoke(context);
        // javaToJS is static in KubeJS's Rhino fork: (Context, Object, Scriptable).
        Object jsValue = rhinoContext.getMethod("javaToJS", rhinoContext, Object.class, scriptable)
                .invoke(null, context, value, scope);
        scriptableObject.getMethod("putProperty", scriptable, String.class, Object.class, rhinoContext)
                .invoke(null, scope, GROUP_NAME, jsValue, context);
        Object result = context.getClass()
                .getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class)
                .invoke(context, scope, source, "mmverify-z9.js", 1, null);
        return result == null ? "null" : result.toString();
    }

    /**
     * The Java object Rhino is showing, out of its own view of it. A scope lookup returns a
     * {@code NativeJavaObject} wrapper, and asking that wrapper for its member set would ask the wrong object.
     */
    private static Object unwrapJava(ChildLoader loader, Object value) throws Exception {
        Class<?> wrapper = Class.forName("dev.latvian.mods.rhino.Wrapper", true, loader);
        return wrapper.getMethod("unwrapped", Object.class).invoke(null, value);
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    /** The message of the deepest cause, so an Rhino error reads as the script's own sentence. */
    private static String unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.toString();
    }

    // ------------------------------------------------------------------ the two jars and the stand-in

    /**
     * A jar of this checkout's own {@code libs/}, found by walking up from the working directory exactly as
     * section Z7 finds KubeJS, so a moved checkout keeps working.
     */
    private static Path findJar(String name) {
        Path candidate = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        while (candidate != null) {
            Path jar = candidate.resolve("modular-machinery-reborn/libs/" + name);
            if (Files.isRegularFile(jar)) {
                return jar;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    /**
     * The <b>deobfuscated</b> copy of a KubeJS-side jar, from this checkout's Gradle cache.
     *
     * <p>Which copy matters, and this is the one subtlety of the section. {@code libs/kubejs-1.20.1.jar} is the
     * production jar — <b>SRG-remapped</b>, so it calls {@code Component.m_237113_} — while this harness's
     * Minecraft is Mojmap ({@code Component.literal}). Pairing them throws
     * {@code NoSuchMethodError: Component.m_237113_} the moment KubeJS initialises its console. The Gradle cache
     * holds the deobfuscated counterpart, which is what the mod itself compiles against; that is the copy used
     * here. It also makes the check honest about which API is being exercised: the same classes, the same
     * method signatures, just the mapping the build uses.
     */
    private static Path findDeobfJar(String name) {
        Path candidate = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        while (candidate != null) {
            Path cache = candidate.resolve(".gradle-home/caches/forge_gradle");
            if (Files.isDirectory(cache)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(cache, 8)) {
                    return walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().equals(name))
                            .findFirst()
                            .orElse(null);
                } catch (java.io.IOException failure) {
                    return null;
                }
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    /**
     * Compiles the {@code Platform} stand-in with the JDK compiler and returns its output directory, or
     * {@code null} if that could not be done.
     *
     * <p>The real {@code Platform} needs a mod loader: outside one it hands KubeJS a {@code null} game folder
     * and KubeJS's own class initialiser dies with a {@code NullPointerException} (observed with a standalone
     * probe before this was written). This answers only what that initialiser asks for, and points the game
     * folder at a temporary directory so no real instance is touched.
     */
    private static Path writePlatformStub(Path kubeJsJar) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir", "."), "mmverify-kjs-stub");
            Path source = dir.resolve("src/dev/architectury/platform/Platform.java");
            Path classes = dir.resolve("classes");
            Files.createDirectories(source.getParent());
            Files.createDirectories(classes);
            Files.writeString(source, PLATFORM_SOURCE, StandardCharsets.UTF_8);
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return null;
            }
            int status = compiler.run(null, null, null, "-nowarn", "-proc:none", "-encoding", "UTF-8",
                    "-classpath", System.getProperty("java.class.path", ""),
                    "-d", classes.toString(), source.toString());
            return status == 0 && Files.isRegularFile(classes.resolve("dev/architectury/platform/Platform.class"))
                    ? classes : null;
        } catch (java.io.IOException failure) {
            return null;
        }
    }

    // ------------------------------------------------------------------ child loader

    /**
     * A <b>child-first</b> loader for the KubeJS and Rhino jars, over this harness's own classpath.
     *
     * <p>Child-first is not a preference here, it is the whole point. The instance's KubeJS and Rhino are
     * <b>SRG-remapped</b> for production, while the mod's own classes come from a build whose Minecraft names
     * are Mojmap; the harness's own Minecraft is the SRG {@code client-extra.jar}. Loading KubeJS from the
     * parent and Minecraft from anywhere else mixes the two, and the first thing that happens is
     * {@code NoSuchMethodError: Component.m_237113_} (observed). Keeping KubeJS, Rhino <b>and</b> the mod's own
     * {@code kubejs} classes in one loader over the SRG jars makes the names agree, because the mod's KubeJS
     * classes use no Minecraft names at all — only {@code EventGroup}, {@code EventHandler} and Rhino.
     */
    private static final class ChildLoader extends URLClassLoader {

        private static final String MOD_KUBEJS_PACKAGE = "com.reborn.modularmachinery.kubejs.";

        ChildLoader(URL[] urls) {
            super(urls, KubeJSBindingCheck.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException fromUrls) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        /**
         * This loader's own URLs, plus the mod's {@code kubejs} classes read from the parent's classpath: those
         * have to be defined here, or their references to KubeJS and Rhino would resolve against the parent and
         * the two worlds would not meet.
         */
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException fromUrls) {
                if (!name.startsWith(MOD_KUBEJS_PACKAGE)) {
                    throw fromUrls;
                }
                try (java.io.InputStream in = getParent()
                        .getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) {
                        throw fromUrls;
                    }
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        }
    }
}
