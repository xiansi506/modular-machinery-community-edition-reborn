package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ModularMachineryReborn.MOD_ID);
    public static final RegistryObject<RecipeType<MachineRecipe>> MACHINE = TYPES.register("machine", () -> new RecipeType<>() {});
    private ModRecipeTypes() {}
}
