package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Locale;
import java.util.Map;

/**
 * Turns the item references used by recipe files into 1.20.1 {@link Ingredient}s.
 *
 * <p>Three forms are accepted:
 * <ul>
 *   <li>{@code namespace:path} — a plain item;</li>
 *   <li>{@code #namespace:path} — an item tag, the native 1.20.1 form;</li>
 *   <li>{@code ore:legacyName} — the original mod's ore dictionary form, mapped onto the nearest Forge common
 *       tag.</li>
 * </ul>
 *
 * <p>The ore dictionary has no automatic successor, and guessing would silently match the wrong items, so only
 * a curated set of names whose Forge tag is known to exist is mapped. Anything else is a load error that says
 * what to write instead. This is the same "fail loudly rather than mis-match" rule applied to the 1.12.2
 * {@code @meta} syntax.
 */
public final class LegacyIngredients {

    private static final String FORGE = "forge";

    /** Legacy ore dictionary names that have a confirmed Forge 1.20.1 counterpart. */
    private static final Map<String, ResourceLocation> ORE_TO_TAG = Map.ofEntries(
            Map.entry("ingotiron", forge("ingots/iron")),
            Map.entry("ingotgold", forge("ingots/gold")),
            Map.entry("ingotcopper", forge("ingots/copper")),
            Map.entry("dustredstone", forge("dusts/redstone")),
            Map.entry("dustglowstone", forge("dusts/glowstone")),
            Map.entry("dustprismarine", forge("dusts/prismarine")),
            Map.entry("gemdiamond", forge("gems/diamond")),
            Map.entry("gememerald", forge("gems/emerald")),
            Map.entry("gemlapis", forge("gems/lapis")),
            Map.entry("gemquartz", forge("gems/quartz")),
            Map.entry("gemprismarine", forge("gems/prismarine"))
    );

    private LegacyIngredients() {
    }

    private static ResourceLocation forge(String path) {
        return new ResourceLocation(FORGE, path);
    }

    /** Parses one {@code item} field into an ingredient. */
    public static Ingredient parse(String raw, String where) {
        if (raw == null || raw.isBlank()) {
            throw new JsonParseException("Missing 'item' in " + where);
        }
        String value = raw.trim();

        if (value.indexOf('@') >= 0) {
            throw new JsonParseException("'" + value + "' in " + where + " uses the 1.12.2 '@meta' syntax. "
                    + "Metadata does not exist in 1.20.1; use the modern item id instead "
                    + "(for example minecraft:light_blue_dye rather than minecraft:dye@3).");
        }

        if (value.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(value.substring(1));
            if (tag == null) {
                throw new JsonParseException("Malformed item tag '" + value + "' in " + where);
            }
            return Ingredient.of(TagKey.create(Registries.ITEM, tag));
        }

        if (value.startsWith("ore:")) {
            String legacy = value.substring(4).toLowerCase(Locale.ROOT);
            ResourceLocation tag = ORE_TO_TAG.get(legacy);
            if (tag == null) {
                throw new JsonParseException("The ore dictionary entry '" + value + "' in " + where
                        + " has no known 1.20.1 equivalent. Write an item tag instead, for example "
                        + "\"#forge:ingots/iron\". Mapped legacy names: " + String.join(", ", ORE_TO_TAG.keySet()));
            }
            return Ingredient.of(TagKey.create(Registries.ITEM, tag));
        }

        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Malformed item id '" + value + "' in " + where);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new JsonParseException("Unknown item '" + id + "' in " + where);
        }
        return Ingredient.of(new ItemStack(item));
    }

    /** The first concrete stack an ingredient can be satisfied with, for output-shaped bookkeeping. */
    public static ItemStack firstStack(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }
}
