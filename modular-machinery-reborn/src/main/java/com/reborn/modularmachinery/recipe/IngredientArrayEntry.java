package com.reborn.modularmachinery.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * One entry of an {@code ingredient_array_input}: either one concrete item, or one item tag.
 *
 * <p>The original's array carried either a {@code ChancedIngredientStack} built from an {@code ItemStack} or one
 * built from an ore-dictionary name ({@code RequirementTypeIngredientArray.java:90-103}), so a two-way entry is
 * the faithful shape. The 1.12.2 ore dictionary is this port's item tags, which is what every other requirement
 * resolves through {@link LegacyIngredients} — so this class holds the <b>parsed</b> {@link Ingredient} and does
 * not re-resolve anything, and an entry can therefore never disagree with the shared reader about what an item
 * reference means.
 *
 * <p>One {@code amount} is shared by the whole array, exactly as the original read it: the reader looked at
 * {@code jsonObject} (the requirement) rather than {@code subItem} (the entry) for both {@code amount}
 * ({@code :79-84}) and {@code chance} ({@code :105-113}), so every entry of one requirement got the same pair.
 * That is the original's behaviour and it is reproduced rather than repaired.
 */
final class IngredientArrayEntry {

    private final Ingredient ingredient;
    /** What a report or a tooltip prints: an item id, or {@code #tag}. */
    private final String itemId;
    /** Whether this entry is a group (a tag) rather than a named item — the ordering rule uses it. */
    private final boolean tagged;
    private final int amount;
    private final float chance;

    private IngredientArrayEntry(Ingredient ingredient, String itemId, boolean tagged, int amount,
                                 float chance) {
        this.ingredient = ingredient;
        this.itemId = itemId;
        this.tagged = tagged;
        this.amount = amount;
        this.chance = chance;
    }

    /**
     * One concrete item.
     *
     * <p>The template is forced to a count of <b>one</b> and the amount is kept in the entry's own field. That
     * matters: {@code LegacyIngredients#firstStack} hands back the stack at whatever count the JSON wrote, and
     * an {@link Ingredient} built from a count of 2 still matches single items — with a required count of 2
     * baked into it, {@code IngredientIo.count} would report availability the ports do not have and the craft
     * would be allowed to start and then fail to pay.
     */
    static IngredientArrayEntry ofItem(ItemStack stack, int amount, float chance) {
        ItemStack template = stack.copy();
        template.setCount(1);
        return new IngredientArrayEntry(Ingredient.of(template), idOf(template), false, amount, chance);
    }

    /** One tag, already parsed by {@link LegacyIngredients} so the two cannot diverge. */
    static IngredientArrayEntry ofTag(Ingredient ingredient, String itemId, int amount, float chance) {
        return new IngredientArrayEntry(ingredient, itemId, true, amount, chance);
    }

    /** The same entry with another chance — the reader's one value applied to every entry. */
    IngredientArrayEntry withChance(float newChance) {
        return new IngredientArrayEntry(this.ingredient, this.itemId, this.tagged, this.amount, newChance);
    }

    int amount() {
        return this.amount;
    }

    float chance() {
        return this.chance;
    }

    /** Whether this entry is a tag rather than a named item. */
    boolean isTag() {
        return this.tagged;
    }

    /** The id printed in reports and tooltips. */
    String itemId() {
        return this.itemId;
    }

    /** What one copy of this entry matches. */
    Ingredient ingredient() {
        return this.ingredient;
    }

    /** The usable copies of this entry that the given ports hold right now. */
    int availableIn(List<IItemHandler> handlers) {
        return IngredientIo.count(handlers, this.ingredient);
    }

    private static String idOf(ItemStack stack) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "?" : key.toString();
    }
}
