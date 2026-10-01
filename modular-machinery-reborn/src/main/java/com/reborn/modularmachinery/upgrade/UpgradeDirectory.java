package com.reborn.modularmachinery.upgrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The upgrade directory a pack author controls: {@code config/modular_machinery_reborn/upgrade/}.
 *
 * <p>Same two-source arrangement as machine definitions (decision D9): the config directory is the author's own
 * copy on this instance and wins over a data pack entry with the same id, so a pack author can override a mod
 * pack's upgrade without editing it.
 *
 * <p>Unlike machines, upgrades need no construction-time scan: nothing about an upgrade has to exist before the
 * registries freeze, because an upgrade is not a block. So this reader is called only from the reload listener,
 * and there is no "declaration" half here — every file in this directory is a full definition or a full item
 * mapping.
 *
 * <p>{@link #readAll()} is deliberately forgiving in one place and one place only: a file that is not valid
 * JSON is logged and skipped rather than aborting the reload, matching {@code MachineDirectory}. Everything
 * else about a malformed file is a hard error, because a silently ignored upgrade declaration is exactly the
 * kind of failure that looks fine until a player wonders why nothing works.
 */
public final class UpgradeDirectory {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static final String DIRECTORY = "upgrade";
    private static final String JSON_SUFFIX = ".json";

    private UpgradeDirectory() {
    }

    /** {@code config/modular_machinery_reborn/upgrade/}. */
    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(ModularMachineryReborn.MOD_ID).resolve(DIRECTORY);
    }

    /**
     * Every file in the directory, keyed like a data pack entry under this mod's namespace so the loader can
     * treat both sources uniformly: {@code upgrade/<name>.json} and {@code upgrade/<name>.item.json}.
     *
     * <p>{@code FMLPaths.CONFIGDIR} is null outside a game run (the same NPE the machine directory has bitten
     * on before, see the 0.17.0 entry in the changelog), so resolution is guarded.
     */
    public static Map<ResourceLocation, JsonElement> readAll() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        Path root;
        try {
            root = directory();
        } catch (Throwable throwable) {
            LOGGER.warn("[{}] Could not resolve the upgrade directory, so only data pack upgrades will "
                    + "load: {}", ModularMachineryReborn.MOD_ID, throwable.toString());
            return files;
        }
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(JSON_SUFFIX)) {
                    continue;
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                String idPath = DIRECTORY + "/" + relative.substring(0, relative.length() - JSON_SUFFIX.length());
                ResourceLocation id = ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + idPath);
                if (id == null) {
                    LOGGER.warn("[{}] Skipping {} — its path is not a valid resource location",
                            ModularMachineryReborn.MOD_ID, file);
                    continue;
                }
                try {
                    JsonElement json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                            JsonElement.class);
                    if (json == null) {
                        LOGGER.warn("[{}] Skipping {} — it is empty", ModularMachineryReborn.MOD_ID, file);
                        continue;
                    }
                    files.put(id, json);
                } catch (Exception exception) {
                    LOGGER.error("[{}] Skipping {} — it is not valid JSON", ModularMachineryReborn.MOD_ID, file,
                            exception);
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[{}] Could not read the upgrade directory {}", ModularMachineryReborn.MOD_ID, root,
                    exception);
        }
        return files;
    }
}
