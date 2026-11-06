package com.reborn.modularmachinery.recipe.adapter;

import com.reborn.modularmachinery.recipe.EnergyRequirement;
import com.reborn.modularmachinery.recipe.FluidRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.reborn.modularmachinery.recipe.MachineRequirementParser;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * C1 第一纵切（0.107.0）：机器间配方复用——旧版 {@code DynamicMachineRecipeAdapter} 的对应物。
 *
 * <p>旧版机制（:42-62）：每台已注册机器获得一个以自身 registryName 为名的适配器；配方 JSON 声明
 * {@code "adapter": "<机器 id>"} 即把那台机器的配方整批复制过来——
 * {@code MachineRecipe.copy}（1.12.2 :244-265）做三件事：时长按 duration 修饰符重算、
 * 每条需求 {@code deepCopyModified(modifiers)}、其余字段（priority/failure 行为/tooltip）原样保留。
 * 本版逐条对应，adapter key 就是父机器的 id。
 *
 * <p><b>深拷贝的通道</b>：需求的 parallelism 是可变的结算状态（{@code setParallelism}），
 * 两条配方共享同一条需求实例会互相污染——所以每条需求必须深拷贝。这里使用
 * {@link MachineRequirementParser#copy} 的服务端定义复制入口，保留完整物品栈和服务端文案，
 * 同时重置执行状态。客户端网络同步的可见数据不等于完整服务端定义。
 *
 * <p><b>修饰符烘入的覆盖范围（第一纵切）</b>：item / fluid / energy 三类值承载型需求逐字段重建
 * （数量、机会、能耗都过 {@link RecipeModifiers#apply}）；catalyst / ingredient_array /
 * interface_number_input 透传不烘——旧版对它们各有 deepCopyModified 实现，但前两类本身携带
 * 运行期修饰符、第三类是等值判断没有数值可烘，等有真实场景再逐类补。
 */
final class DynamicMachineAdapter {

    private DynamicMachineAdapter() {
    }

    static List<MachineRecipe> generate(MachineRecipe declaration, ResourceLocation owning,
                                        RecipeManager manager) {
        RecipeModifiers mods = RecipeModifiers.of(declaration.adapterModifiers());
        ResourceLocation parent = declaration.adapterKey();
        List<MachineRecipe> generated = new ArrayList<>();
        for (MachineRecipe original : manager.getAllRecipesFor(ModRecipeTypes.MACHINE.get())) {
            // 占位（别的适配器声明）不是可执行配方，不复制——旧版没有这一类，没有对应物可言。
            if (original.isAdapterDeclaration() || !original.belongsTo(parent)) {
                continue;
            }
            generated.add(copyOf(original, owning, mods, RecipeAdapterRegistry.generatedId(declaration, owning, original.getId())));
        }
        return generated;
    }

    /** 旧版 {@code MachineRecipe.copy}（1.12.2 :244-265）的对应物，见类注释的三件事。 */
    private static MachineRecipe copyOf(MachineRecipe original, ResourceLocation owning, RecipeModifiers mods,
                                        ResourceLocation newId) {
        int duration = bakedInt(mods.applyDuration(original.recipeTime()), 1, original.getId() + " duration");
        List<MachineRequirement> requirements = new ArrayList<>(original.requirements().size());
        for (MachineRequirement requirement : original.requirements()) {
            requirements.add(copyModified(requirement, mods));
        }
        return new MachineRecipe(newId, List.of(owning.toString()),
                original.registryName() + ".copy." + newId.getPath(), duration, requirements,
                original.maxParallelism(), original.cancelIfPerTickFails(), original.commands(),
                original.priority(), original.tooltips());
    }

    /** 服务端定义深拷贝 + 三类值承载型需求的修饰符烘入；其余类型不烘入数值。 */
    private static MachineRequirement copyModified(MachineRequirement requirement, RecipeModifiers mods) {
        MachineRequirement copy = MachineRequirementParser.copy(requirement);
        MachineRequirement result = copy;
        if (copy instanceof ItemRequirement item) {
            result = copyItem(item, mods);
        } else if (copy instanceof FluidRequirement fluid) {
            result = copyFluid(fluid, mods);
        } else if (copy instanceof EnergyRequirement energy) {
            long perTick = energy.bakedEnergyPerTick(mods);
            result = new EnergyRequirement(energy.ioType(), perTick);
        }
        result.setSelectorTag(copy.selectorTag());
        result.setParallelizeUnaffected(copy.isParallelizeUnaffected());
        result.setScriptOptions(copy.scriptOptions());
        return result;
    }

    private static ItemRequirement copyItem(ItemRequirement item, RecipeModifiers mods) {
        if (item.ioType() == IOType.INPUT) {
            int amount = bakedInt(mods.apply(RecipeModifier.Target.ITEM, IOType.INPUT,
                    item.amount(), false), 1, "item input amount");
            float chance = (float) mods.apply(RecipeModifier.Target.ITEM, IOType.INPUT, item.chance(), true);
            return ItemRequirement.input(item.ingredient(), amount, item.nbt(), item.displayTag(), chance);
        }
        int min = bakedInt(mods.apply(RecipeModifier.Target.ITEM, IOType.OUTPUT,
                item.minAmount(), false), 1, "item output minAmount");
        int max = Math.max(min, bakedInt(mods.apply(RecipeModifier.Target.ITEM, IOType.OUTPUT,
                item.maxAmount(), false), 1, "item output maxAmount"));
        float chance = (float) mods.apply(RecipeModifier.Target.ITEM, IOType.OUTPUT, item.chance(), true);
        return ItemRequirement.output(item.outputStack().copyWithCount(min), min, max, chance, item.displayTag());
    }

    private static FluidRequirement copyFluid(FluidRequirement fluid, RecipeModifiers mods) {
        int amount = bakedInt(mods.apply(RecipeModifier.Target.FLUID, fluid.ioType(),
                fluid.amount(), false), 1, "fluid amount");
        float chance = (float) mods.apply(RecipeModifier.Target.FLUID, fluid.ioType(), fluid.chance(), true);
        return new FluidRequirement(fluid.ioType(), fluid.fluidType(), amount, chance, fluid.isTickPhase());
    }

    /** Round in the original long domain, check before narrowing, retain each adapter's lower floor. */
    static int bakedInt(double value, int minimum, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Adapter " + field + " is not finite");
        long rounded = Math.round(value);
        if (rounded > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Adapter " + field + " exceeds the int range: " + rounded);
        return (int) Math.max(minimum, rounded);
    }
}
