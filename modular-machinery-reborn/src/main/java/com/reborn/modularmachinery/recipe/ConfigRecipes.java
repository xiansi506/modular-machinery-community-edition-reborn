package com.reborn.modularmachinery.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.event.AddPackFindersEvent;
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
 * Recipes a pack author keeps as loose files: {@code config/modular_machinery_reborn/recipes/}.
 *
 * <p>This is the other half of the original's config-directory story. The original loaded
 * {@code config/modularmachinery/recipes/} directly; 1.20.1 has no equivalent hook, because recipes only ever
 * come from a {@code RecipeManager} reload. So instead of a second loading path, the directory is exposed
 * <b>as</b> a data pack: {@link ConfigRecipePack} serves these files under
 * {@code data/modular_machinery_reborn/recipes/}, and vanilla loads them like any other pack.
 *
 * <p>The layout below the directory mirrors the data pack layout, so a file at
 * {@code recipes/alloy_smelter/diamond.json} becomes the recipe id
 * {@code modular_machinery_reborn:alloy_smelter/diamond}. Editing a file is picked up by {@code /reload}.
 */
public final class ConfigRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String DIRECTORY = "recipes";
    private static final String JSON_SUFFIX = ".json";
    /** The value every machine recipe's root {@code type} must carry. */
    public static final String RECIPE_TYPE = ModularMachineryReborn.MOD_ID + ":machine";

    private ConfigRecipes() {
    }

    /** {@code config/modular_machinery_reborn/recipes/}. */
    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(ModularMachineryReborn.MOD_ID).resolve(DIRECTORY);
    }

    /** Every recipe file, keyed the way the pack framework addresses server data resources. */
    public static Map<ResourceLocation, byte[]> readAll() {
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        Path root;
        try {
            root = directory();
        } catch (Throwable throwable) {
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
                ResourceLocation location;
                try {
                    location = new ResourceLocation(ModularMachineryReborn.MOD_ID, DIRECTORY + "/" + relative);
                } catch (RuntimeException exception) {
                    LOGGER.warn("[{}] Skipping {} — its path is not a valid resource location",
                            ModularMachineryReborn.MOD_ID, file);
                    continue;
                }
                try {
                    byte[] data = Files.readAllBytes(file);
                    validate(file, data);
                    files.put(location, data);
                } catch (IOException exception) {
                    LOGGER.error("[{}] Could not read recipe file {}", ModularMachineryReborn.MOD_ID, file,
                            exception);
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[{}] Could not read the recipe directory {}", ModularMachineryReborn.MOD_ID, root,
                    exception);
        }
        return files;
    }

    /**
     * Checks the one field Minecraft itself insists on before this mod ever sees the recipe.
     *
     * <p>Vanilla's {@code RecipeManager} reads a root {@code type} to decide which serializer to hand the JSON to.
     * The original mod had no {@code type} at all, because it opened its recipe files itself — so a recipe
     * carried over unchanged from the original is dropped with nothing but a terse
     * {@code "Missing type, expected to find a string"}. This says what to write instead.
     */
    private static void validate(Path file, byte[] data) {
        JsonObject root;
        try {
            root = GSON.fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
        } catch (RuntimeException exception) {
            LOGGER.error("[{}] {} is not valid JSON and will not load", ModularMachineryReborn.MOD_ID, file,
                    exception);
            return;
        }
        if (root == null) {
            LOGGER.error("[{}] {} is empty and will not load", ModularMachineryReborn.MOD_ID, file);
            return;
        }
        if (!root.has("type")) {
            LOGGER.error("[{}] {} has no root 'type' field. Minecraft reads it to pick a recipe serializer and "
                            + "drops the file before this mod sees it. Add \"type\": \"{}\" as the first field.",
                    ModularMachineryReborn.MOD_ID, file, RECIPE_TYPE);
            return;
        }
        String type = root.get("type").getAsString();
        if (!RECIPE_TYPE.equals(type)) {
            LOGGER.error("[{}] {} declares type '{}'; machine recipes must use '{}'",
                    ModularMachineryReborn.MOD_ID, file, type, RECIPE_TYPE);
        }
    }

    /** Contributes the directory as a required server data pack, when it holds anything. */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        int found = readAll().size();
        if (found == 0) {
            return;
        }
        LOGGER.info("[{}] Exposing {} recipe file(s) from {} as a data pack",
                ModularMachineryReborn.MOD_ID, found, directory());
        event.addRepositorySource(packs -> packs.accept(Pack.create(
                ModularMachineryReborn.MOD_ID + "_config_recipes",
                Component.literal("Modular Machinery Reborn: recipes from the config directory"),
                true,
                ConfigRecipePack::new,
                new Pack.Info(Component.literal("Recipes found in config/" + ModularMachineryReborn.MOD_ID
                        + "/recipes/"), 15, FeatureFlags.VANILLA_SET),
                PackType.SERVER_DATA,
                Pack.Position.TOP,
                true,
                PackSource.BUILT_IN)));
    }
}
