package com.reborn.modularmachinery.machine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads machine definitions from the data pack directory {@code data/<namespace>/machinery/}, from the config
 * directory {@code config/modular_machinery_reborn/machinery/}, and — from 0.28.0 — from whatever a KubeJS
 * server script staged before this listener ran.
 *
 * <p>Two kinds of file live in a machine directory, distinguished by name:
 * <ul>
 *   <li>{@code <name>.json} — a machine definition, the counterpart of the original
 *       {@code assets/modularmachinery/default_machinery/*.json}. Reads {@code registryname},
 *       {@code localizedname}, {@code parts}, {@code failure-action} and {@code requires-blueprint}.</li>
 *   <li>{@code <name>.var.json} — a variable set, the counterpart of the original
 *       {@code default_variables/casings.var.json}. Maps a name to a list of block descriptors, so machine
 *       definitions can say {@code "elements": "casings_all"} instead of repeating every casing and hatch.</li>
 * </ul>
 *
 * <p>Variables are collected in a first pass so machine files may reference them regardless of load order.
 * A failing definition is logged and skipped rather than aborting the whole reload, matching the original
 * loader's tolerance.
 *
 * <p>{@code failure-action} joins the consumed fields in 0.25.0: it was parsed and carried since M1 and read
 * by nobody, so a definition could say what a failed tick costs and nothing happened. See
 * {@code MachineSchema#readFailureAction}.
 *
 * <h2>0.28.0: the three sources and why the script one is merged, not replaced</h2>
 *
 * <p>{@link #apply} is a <b>whole-table</b> replacement ({@link MachineRegistry#replace}), and it runs on every
 * data-pack reload. A machine a script pushed into the registry therefore survives exactly until the next
 * {@code /reload} — the defect the survey predicted, and the one the v2a acceptance has to prove is gone. The
 * fix is not to make the registry additive (that would leak a definition whose script was deleted) but to make
 * the reload the single point where all three sources meet:
 *
 * <ol>
 *   <li>the data pack's files, then the config directory's (which wins on a {@code registryname} collision —
 *       D9), parsed here as before;</li>
 *   <li>then the <b>script layer</b>, read fresh from {@link MachineDefinitions} on every reload, which wins
 *       over both. A script is an explicit runtime statement about this server, and it is also the layer whose
 *       author can be told to remove the file; the log names every definition it shadows.</li>
 * </ol>
 *
 * <p>That ordering is possible because a KubeJS server script always runs <b>earlier in the same reload</b>
 * than this listener: KubeJS wraps the resource manager in {@code WorldLoader$PackConfig#createResourceManager}
 * (its own mixin), drives {@code ServerScriptManager.wrapResourceManager} there — which reloads the scripts and
 * calls {@code KubeJSPlugin#onServerReload} — and Forge only collects the {@code AddReloadListenerEvent}
 * listeners afterwards, inside {@code ReloadableServerResources#loadResources}. Both points are in the same
 * reload, so {@code onServerReload} has already staged the definitions when {@link #apply} runs. The full
 * evidence chain is in the v2a section of {@code 迁移日志.md}.
 *
 * <p>Because the script layer is re-read here rather than retained, deleting a script removes its machines on
 * the next reload and nothing else has to be cleaned up.
 */
public final class MachineLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "machinery";
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VARIABLE_SUFFIX = ".var";

    public MachineLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // The config directory is merged last, so a pack author's file wins over a data pack file for the same
        // 'registryname'. Both sources describe the same thing; the directory is the one that can also grant a
        // dedicated controller block, because it is readable during mod construction.
        Map<ResourceLocation, JsonElement> all = new LinkedHashMap<>(files);
        all.putAll(MachineDirectory.readAll());

        Map<String, List<String>> variables = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            if (!isVariableFile(entry.getKey())) {
                continue;
            }
            try {
                readVariables(entry.getKey(), entry.getValue(), variables);
            } catch (Exception exception) {
                LOGGER.error("[{}] Failed to read variable set {}", ModularMachineryReborn.MOD_ID,
                        entry.getKey(), exception);
            }
        }

        Map<ResourceLocation, MachineDefinition> loaded = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            if (isVariableFile(entry.getKey())) {
                continue;
            }
            try {
                MachineDefinition definition = readMachine(entry.getKey(), entry.getValue(), variables);
                loaded.put(definition.id(), definition);
            } catch (Exception exception) {
                failures.add(entry.getKey().toString());
                LOGGER.error("[{}] Failed to load machine definition {}", ModularMachineryReborn.MOD_ID,
                        entry.getKey(), exception);
            }
        }

        // 0.28.0: the script layer, re-read on every reload so a deleted script really removes its machine and a
        // surviving one really survives the reload. Applied here rather than pushed into the registry from
        // KubeJS, because this listener runs after the scripts and would otherwise overwrite them.
        MachineDefinitions.applyScriptLayer(loaded);

        MachineRegistry.replace(loaded);
        LOGGER.info("[{}] Loaded {} machine definition(s) from {} variable set(s){}",
                ModularMachineryReborn.MOD_ID, loaded.size(), variables.size(),
                failures.isEmpty() ? "" : ", " + failures.size() + " failed: " + failures);
        for (MachineDefinition definition : loaded.values()) {
            LOGGER.info("[{}]   {} -> {} parts, size {}x{}x{}, y {}..{}", ModularMachineryReborn.MOD_ID,
                    definition.id(), definition.pattern().partCount(),
                    definition.pattern().size().getX(), definition.pattern().size().getY(),
                    definition.pattern().size().getZ(),
                    definition.pattern().min().getY(), definition.pattern().max().getY());
        }

        // Controller blocks are registered once, at construction, and cannot be created later. So two things
        // need saying out loud: a registered controller whose machine never loaded is a dead block, and a
        // machine with no registered controller can only ever use the generic one. A script-defined machine
        // lands in the second group for the same reason a data-pack machine does — see the v2a record.
        for (MachineDirectory.MachineRef ref : ModBlocks.BOUND_CONTROLLERS) {
            if (!loaded.containsKey(ref.machineId())) {
                LOGGER.warn("[{}] {}_controller exists as a block but no machine definition '{}' was loaded, so "
                                + "it can never form. Add {}/{}.json (or data/{}/machinery/{}.json).",
                        ModularMachineryReborn.MOD_ID, ref.path(), ref.machineId(),
                        displayDirectory(), ref.path(), ModularMachineryReborn.MOD_ID, ref.path());
            }
        }

        List<String> withoutController = new ArrayList<>();
        for (ResourceLocation id : loaded.keySet()) {
            boolean bound = false;
            for (MachineDirectory.MachineRef ref : ModBlocks.BOUND_CONTROLLERS) {
                if (ref.machineId().equals(id)) {
                    bound = true;
                    break;
                }
            }
            if (!bound) {
                withoutController.add(id.toString());
            }
        }
        if (!withoutController.isEmpty()) {
            LOGGER.info("[{}] {} machine(s) have no controller block of their own and use the generic "
                    + "machine_controller: {}. Put a definition in {} to give one its own controller (it takes "
                    + "effect on the next launch). A machine defined by a KubeJS script cannot have one at all: "
                    + "blocks are registered before any script runs (see D8 in 移植方案-v2.md).",
                    ModularMachineryReborn.MOD_ID, withoutController.size(),
                    String.join(", ", withoutController), displayDirectory());
        }

        // M6e: a machine that asks for a factory needs a factory controller block, and that block could only be
        // registered during mod construction from the config directory. A definition that arrives from a data
        // pack alone therefore cannot have one however loudly it asks — and saying so is the loud half of the
        // rule. This runs here rather than inside readMachine because readMachine touches no registry, which is
        // what lets the offline harness drive it without a world.
        for (MachineDefinition definition : loaded.values()) {
            if (!definition.hasFactory() || ModBlocks.hasFactoryController(definition.id())) {
                continue;
            }
            LOGGER.warn("[{}] {} declares has-factory, but no factory controller block was registered for it, "
                            + "so this machine can never run as a factory. Put a declaration in {} naming it and "
                            + "relaunch: {{\"registryname\": \"{}\", \"has-factory\": true}}. A data pack cannot "
                            + "create blocks — they are registered before any pack is read (see D8/D15 in "
                            + "移植方案-v2.md).",
                    ModularMachineryReborn.MOD_ID, definition.id(), displayDirectory(),
                    definition.id().getPath());
        }

        // M6d-b: a machine that declares no interface type cannot answer an interface_number_input
        // requirement, and that is a fact about the definition rather than a bug — but it is invisible in the
        // definition file, so it gets said once. The switch is the mod's single smart-interface config key, and
        // it is a message switch and nothing more: no declaration means no value can exist to switch on.
        if (!com.reborn.modularmachinery.config.ModConfig.smartInterfaceEnabledByDefault()) {
            for (MachineDefinition definition : loaded.values()) {
                if (definition.hasSmartInterfaces()) {
                    continue;
                }
                LOGGER.info("[{}] {} declares no 'smart-interfaces', so no interface_number_input requirement "
                                + "on it can ever be satisfied. Declare the types a recipe asks for in its "
                                + "definition: \"smart-interfaces\": [ {{ \"type\": \"mode\", \"default\": 0 }} ]. "
                                + "Set smart-interface.enable-smart-interface-bydefault=true in {} to silence "
                                + "this note (see D16 in 移植方案-v2.md).",
                        ModularMachineryReborn.MOD_ID, definition.id(), displayDirectory());
            }
        }
    }

    private static boolean isVariableFile(ResourceLocation id) {
        return id.getPath().endsWith(VARIABLE_SUFFIX);
    }

    /**
     * The config directory as a message can name it, resolved lazily.
     *
     * <p>{@link MachineDirectory#directory()} reads {@code FMLPaths.CONFIGDIR}, which is null outside a game
     * run, so naming it eagerly turns a diagnostic into an {@code NullPointerException} — which is exactly what
     * the offline harness hit when it drove {@code MachineDefinitions.applyScriptLayer}. A log line that can
     * only be built inside a game is not a log line; the fallback says what the directory is for.
     */
    public static String displayDirectory() {
        try {
            return MachineDirectory.directory().toString();
        } catch (Throwable unavailable) {
            return "the machine definition directory (config/<modid>/machinery)";
        }
    }

    private static void readVariables(ResourceLocation id, JsonElement element, Map<String, List<String>> out) {
        com.google.gson.JsonObject root = MachineSchema.asObject(element, id);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            com.google.gson.JsonArray array = MachineSchema.asArray(entry.getValue(),
                    id + " variable '" + entry.getKey() + "'");
            List<String> values = new ArrayList<>(array.size());
            for (JsonElement value : array) {
                if (!value.isJsonPrimitive()) {
                    throw new com.google.gson.JsonParseException("Variable '" + entry.getKey()
                            + "' must contain only block descriptor strings");
                }
                values.add(value.getAsString());
            }
            out.put(entry.getKey(), List.copyOf(values));
        }
    }

    /**
     * Reads one definition through {@link MachineSchema} — the same entry point the KubeJS machine API uses,
     * so a data-pack author and a script author are answered by the same rules and the same sentences.
     *
     * <p>Kept as a private method of this class (rather than being inlined at the call site) because the offline
     * harness drives the loader's own parse by reflection, which is what makes "these are the loader's rules, not
     * a copy of them" checkable.
     */
    private static MachineDefinition readMachine(ResourceLocation fileId, JsonElement element,
                                                 Map<String, List<String>> variables) {
        return MachineSchema.read(element, fileId, variables);
    }
}
