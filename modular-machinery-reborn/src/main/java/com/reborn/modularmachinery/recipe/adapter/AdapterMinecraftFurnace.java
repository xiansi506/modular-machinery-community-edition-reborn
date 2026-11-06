package com.reborn.modularmachinery.recipe.adapter;

import com.reborn.modularmachinery.recipe.EnergyRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * C1 第一纵切（0.107.0）：原版熔炉适配器——旧版 {@code AdapterMinecraftFurnace} 的对应物。
 *
 * <p>旧版（1.12.2，:53-92）读 {@code FurnaceRecipes.instance().getSmeltingList()}，每条熔炼配比生成一条
 * 机器配方：基础时长 120t（REQUIREMENT_DURATION 修饰符可改）、物品输入/输出按 REQUIREMENT_ITEM 修饰符缩放、
 * 能耗固定 20 FE/t（REQUIREMENT_ENERGY 修饰符可改）。本版逐条对应：
 *
 * <ul>
 *   <li>数据源改为 {@code RecipeManager} 的 SMELTING 类型（1.20.1 熔炼配方不再是一张静态 map）——
 *       这也意味着 tag 输入直接可用（旧版 map 只收具体物品，这里是超集）。</li>
 *   <li>修饰符施加走 {@link RecipeModifiers#applyDuration} / {@link RecipeModifiers#apply}——
 *       旧版 {@code RecipeModifier.applyModifiers} 的同款算式 {@code (value + add) * mul}。</li>
 *   <li>经验的差异：旧版把 {@code getSmeltingExperience} 存进配方的 dataCompound
 *       （RecipeCheckEvent 处理器，:67-71），供 CraftTweaker/兼容层消费；本版没有那条消费链，
 *       有据不生成（生成配方里不携带经验）。</li>
 * </ul>
 */
final class AdapterMinecraftFurnace {

    /** 旧版硬编码的基础时长（{@code AdapterMinecraftFurnace:60} 的 {@code 120}）。 */
    private static final int BASE_DURATION = 120;
    /** 旧版硬编码的每 tick 能耗（:83 的 {@code 20}）。 */
    private static final long BASE_ENERGY_PER_TICK = 20;

    private AdapterMinecraftFurnace() {
    }

    static List<MachineRecipe> generate(MachineRecipe declaration, ResourceLocation owning,
                                        RecipeManager manager, RegistryAccess access) {
        RecipeModifiers mods = RecipeModifiers.of(declaration.adapterModifiers());
        List<MachineRecipe> generated = new ArrayList<>();
        for (SmeltingRecipe smelting : manager.getAllRecipesFor(RecipeType.SMELTING)) {
            if (smelting.getIngredients().isEmpty()) {
                continue;
            }
            ItemStack result = smelting.getResultItem(access);
            if (result == null || result.isEmpty()) {
                continue;
            }

            // 数值全部先过修饰符（旧版 :60/:73/:78/:83 的四处 applyModifiers）。
            int duration = DynamicMachineAdapter.bakedInt(mods.applyDuration(BASE_DURATION), 1, "furnace duration");
            int inAmount = DynamicMachineAdapter.bakedInt(mods.apply(RecipeModifier.Target.ITEM, IOType.INPUT,
                    1, false), 0, "furnace input amount");
            int outAmount = DynamicMachineAdapter.bakedInt(mods.apply(RecipeModifier.Target.ITEM, IOType.OUTPUT,
                    result.getCount(), false), 0, "furnace output amount");
            long energy = new EnergyRequirement(IOType.INPUT, BASE_ENERGY_PER_TICK).bakedEnergyPerTick(mods);

            List<MachineRequirement> requirements = new ArrayList<>(3);
            if (inAmount > 0) {
                requirements.add(ItemRequirement.input(smelting.getIngredients().get(0), inAmount));
            }
            if (outAmount > 0) {
                requirements.add(ItemRequirement.output(result.copyWithCount(outAmount), outAmount, outAmount, 1.0F));
            }
            if (energy > 0) {
                requirements.add(new EnergyRequirement(IOType.INPUT, energy));
            }
            if (requirements.isEmpty()) {
                continue;
            }

            ResourceLocation id = RecipeAdapterRegistry.generatedId(declaration, owning, smelting.getId());
            // 五参 String-machine 版本（resolveMachineId 解析 "ns:path"）；不要传 List——List 版从八参起。
            generated.add(new MachineRecipe(id, owning.toString(), id.getPath(), duration, requirements));
        }
        return generated;
    }
}
