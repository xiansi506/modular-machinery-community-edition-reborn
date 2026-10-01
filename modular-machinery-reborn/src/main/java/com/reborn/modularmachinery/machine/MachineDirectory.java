package com.reborn.modularmachinery.machine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The machine definition directory a pack author controls:
 * {@code config/modular_machinery_reborn/machinery/}, with variable sets in {@code variables/}.
 *
 * <p>This mirrors the original, where {@code ModDataHolder} kept {@code config/modularmachinery/machinery/} and
 * {@code MachineRegistry.preloadMachines()} discovered definitions there <b>before</b> blocks were registered.
 * That ordering is the whole point: it is what lets a pack author add a machine and get a controller block of
 * its own, which a data pack alone can never do, because blocks may only be registered while the mod is being
 * constructed.
 *
 * <p>Files here are read twice, for two different reasons:
 * <ul>
 *   <li>{@link #scanMachinePaths()} runs during mod construction and only extracts {@code registryname}, so the
 *       controller blocks can be registered. It must not depend on the data pack, which is not available
 *       yet.</li>
 *   <li>{@link #readAll()} runs on every reload and feeds the full definitions into {@link MachineLoader}.</li>
 * </ul>
 *
 * <p>A machine added here after the game started is still loaded, but it cannot gain a block until the next
 * launch; the loader says so in the log.
 */
public final class MachineDirectory {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static final String DIRECTORY = "machinery";
    private static final String VARIABLES = "variables";
    private static final String JSON_SUFFIX = ".json";

    private MachineDirectory() {
    }

    /**
     * One machine that should get a controller of its own.
     *
     * @param path          the file's name without {@code .json}, i.e. the block's registry path
     * @param machineId     the machine the controller is bound to
     * @param factoryController whether this declaration also asks for {@code <path>_factory_controller}
     * @param factoryOnly   whether the declaration is a {@code factory-only} machine, i.e. one that gets a
     *                      factory controller <b>instead of</b> an ordinary one — the original's
     *                      {@code isFactoryOnly} guard, which is also what decides whether a machine gets a
     *                      controller in the {@code modularcontroller} namespace
     *                      ({@code RegistryBlocks.java:410-412})
     */
    public record MachineRef(String path, ResourceLocation machineId, boolean factoryController,
                             boolean factoryOnly) {

        /**
         * Whether the declaration's value is usable at all. {@code has-factory} has to be the boolean
         * {@code true}; a machine definition that omits it gets no factory controller, which is the original's
         * default ({@code AbstractMachine.hasFactory}, seeded from
         * {@code Config.enableFactoryControllerByDefault = false}).
         */
        public MachineRef {
        }
    }

    /** {@code config/modular_machinery_reborn/machinery/}. */
    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(ModularMachineryReborn.MOD_ID).resolve(DIRECTORY);
    }

    /**
     * Machines declared by files in the directory, for controller block registration.
     *
     * <p>Runs during mod construction, so it is deliberately forgiving: any failure degrades to "no extra
     * machines" with a log line rather than aborting startup.
     *
     * <p>M6e reads one field beyond {@code registryname}: {@code has-factory}. A declaration that asks for a
     * factory gets {@code <path>_factory_controller} registered as well. This is the same mechanism D9/D10 built
     * for the ordinary controllers, reused rather than extended — the reason is in the D15 record, and the short
     * version is that "the block set is frozen at construction, the definition loads later and is reloadable" is
     * one problem, not two.
     *
     * <p>0.27.0 reads {@code factory-only} the same way, because it decides two things before the block set is
     * frozen: whether the machine gets an ordinary controller at all, and whether it gets one in the
     * {@code modularcontroller} compatibility namespace — the original's {@code isFactoryOnly} guard
     * ({@code RegistryBlocks.java:410-412}, {@code :431-433}).
     */
    public static List<MachineRef> scanMachinePaths() {
        List<MachineRef> found = new ArrayList<>();
        Path root = null;
        try {
            root = directory();
        } catch (Throwable throwable) {
            LOGGER.warn("[{}] Could not resolve the machine directory during startup, so only the built-in "
                    + "controllers will exist: {}", ModularMachineryReborn.MOD_ID, throwable.toString());
            return found;
        }
        return scanMachinePaths(root);
    }

    /**
     * The same scan against an explicit root, so the rule can be driven without a game run.
     *
     * <p>{@link #scanMachinePaths()} resolves the root through {@code FMLPaths.CONFIGDIR}, which is null
     * outside a game run — the NPE {@code UpgradeDirectory} also guards against. Splitting the two means the
     * "which declarations get a controller" rule is provable offline while the production path is still the one
     * the game uses.
     */
    public static List<MachineRef> scanMachinePaths(Path root) {
        List<MachineRef> found = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return found;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(JSON_SUFFIX) || name.endsWith(".var.json") || isUnderVariables(root, file)) {
                    continue;
                }
                MachineRef ref = readRef(root, file);
                if (ref != null) {
                    found.add(ref);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("[{}] Could not read the machine directory {}; only the built-in controllers will "
                    + "exist", ModularMachineryReborn.MOD_ID, root, exception);
        }
        return found;
    }

    private static boolean isUnderVariables(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() > 1 && VARIABLES.equals(relative.getName(0).toString());
    }

    private static MachineRef readRef(Path root, Path file) {
        try {
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (json == null || !json.has("registryname")) {
                LOGGER.warn("[{}] {} has no 'registryname', so it gets no controller block",
                        ModularMachineryReborn.MOD_ID, file);
                return null;
            }
            String raw = json.get("registryname").getAsString();
            ResourceLocation id = raw.contains(":")
                    ? ResourceLocation.tryParse(raw)
                    : new ResourceLocation(ModularMachineryReborn.MOD_ID, raw);
            if (id == null) {
                LOGGER.warn("[{}] {} has a malformed 'registryname' ({}), so it gets no controller block",
                        ModularMachineryReborn.MOD_ID, file, raw);
                return null;
            }
            boolean factory = readFactoryFlag(json, file);
            boolean factoryOnly = readBooleanFlag(json, FactoryThreadModel.JSON_FACTORY_ONLY, file,
                    "so no factory-only controller block is registered for it and it keeps an ordinary "
                            + "controller. Write \"" + FactoryThreadModel.JSON_FACTORY_ONLY + "\": true.");
            return new MachineRef(id.getPath(), id, factory, factoryOnly);
        } catch (Exception exception) {
            LOGGER.warn("[{}] Could not read {} while registering controllers; it gets no controller block "
                    + "this launch", ModularMachineryReborn.MOD_ID, file, exception);
            return null;
        }
    }

    /**
     * {@code has-factory} as a declaration may spell it.
     *
     * <p>Only the boolean {@code true} counts. Anything else — including a present-but-false field, and
     * including a malformed value — leaves the declaration without a factory controller, but a malformed value
     * is <b>said out loud</b>: this runs before the loader exists, so it is the only chance to tell the author
     * that what they wrote is not going to be read the way they meant it.
     */
    private static boolean readFactoryFlag(JsonObject json, Path file) {
        if (!json.has(FactoryThreadModel.JSON_HAS_FACTORY)) {
            return false;
        }
        JsonElement element = json.get(FactoryThreadModel.JSON_HAS_FACTORY);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            LOGGER.warn("[{}] {} has a 'has-factory' that is not true or false, so no factory controller block "
                            + "is registered for it. Write \"has-factory\": true.",
                    ModularMachineryReborn.MOD_ID, file);
            return false;
        }
        return element.getAsBoolean();
    }

    /**
     * A boolean declaration field, with the same "only the boolean counts, but a malformed value is said out
     * loud" rule {@link #readFactoryFlag} follows.
     *
     * <p>{@code factory-only} is read here for a second reason: the original skipped such a machine when it
     * registered the {@code modularcontroller} controllers ({@code RegistryBlocks.java:410-412}), so this one
     * bit has to be known before the block set is frozen, exactly like {@code has-factory}.
     */
    private static boolean readBooleanFlag(JsonObject json, String key, Path file, String consequence) {
        if (!json.has(key)) {
            return false;
        }
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            LOGGER.warn("[{}] {} has a '{}' that is not true or false, {}", ModularMachineryReborn.MOD_ID, file,
                    key, consequence);
            return false;
        }
        return element.getAsBoolean();
    }

    /**
     * Every machine and variable file in the directory, keyed like a data pack entry so the loader can treat
     * both sources uniformly. Variable sets keep their {@code variables/} path and {@code .var} suffix.
     *
     * <p>Files that name a machine but carry no {@code parts} are <b>controller claims</b>: they exist so the
     * block can be registered at startup, while the definition itself lives in a data pack and stays
     * reloadable. They are left out here so the loader does not try to read them as definitions.
     */
    public static Map<ResourceLocation, JsonElement> readAll() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        Path root;
        try {
            root = directory();
        } catch (Throwable throwable) {
            return files;
        }
        if (!Files.isDirectory(root)) {
            return files;
        }
        int claims = 0;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(JSON_SUFFIX)) {
                    continue;
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                String idPath = relative.substring(0, relative.length() - JSON_SUFFIX.length());
                ResourceLocation id = ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + idPath);
                if (id == null) {
                    LOGGER.warn("[{}] Skipping {} — its path is not a valid resource location",
                            ModularMachineryReborn.MOD_ID, file);
                    continue;
                }
                try {
                    JsonElement json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class);
                    if (json == null) {
                        LOGGER.warn("[{}] Skipping {} — it is empty", ModularMachineryReborn.MOD_ID, file);
                        continue;
                    }
                    if (isControllerClaim(id, json)) {
                        claims++;
                        continue;
                    }
                    files.put(id, json);
                } catch (Exception exception) {
                    LOGGER.error("[{}] Skipping {} — it is not valid JSON", ModularMachineryReborn.MOD_ID, file,
                            exception);
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[{}] Could not read the machine directory {}", ModularMachineryReborn.MOD_ID, root,
                    exception);
        }
        if (claims > 0) {
            LOGGER.info("[{}] {} file(s) in {} name a machine without defining one, so they only claim a "
                            + "controller block; those definitions come from a data pack",
                    ModularMachineryReborn.MOD_ID, claims, root);
        }
        return files;
    }

    /** True when a file names a machine but carries no {@code parts}, i.e. it only claims a controller. */
    private static boolean isControllerClaim(ResourceLocation id, JsonElement json) {
        if (id.getPath().endsWith(".var") || !json.isJsonObject()) {
            return false;
        }
        JsonObject object = json.getAsJsonObject();
        return object.has("registryname") && !object.has("parts");
    }
}
