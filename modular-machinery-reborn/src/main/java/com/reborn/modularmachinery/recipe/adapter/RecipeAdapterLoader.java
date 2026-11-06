package com.reborn.modularmachinery.recipe.adapter;

import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * C1 第一纵切（0.107.0）：适配器生成轮——reload 尾段把「适配器声明占位」换成生成的真实配方。
 *
 * <p><b>注入点勘测结论（为什么这里没有 AT、没有反射）</b>：1.20.1 的 {@code RecipeManager} 对外只暴露
 * 读路径（{@code getAllRecipesFor} 等）和两个公开写入口——{@code replaceRecipes(Iterable)} 与
 * {@code getRecipes()}（javap 对 1.20.1 mapped jar 核对）。本版全部配方消费端（控制器
 * {@code MachineControllerBlockEntity:1117}、JEI {@code ModularMachineryJeiPlugin:224}、命令）
 * 都从 {@code RecipeManager} 读，所以生成配方必须进 manager；进法就用这对公开 API：
 * 先读全量、剔掉声明占位、并上生成结果、整体 {@code replaceRecipes} 回注——正是原版 reload 与
 * 客户端配方同步自己走的那个方法。旧版的 AT/反射议题（「替换 RecipeMap」）在本版没有存在的必要。
 *
 * <p><b>时序</b>：注册在 MachineLoader / UpgradeLoader 之后（机器注册表先填好，生成轮才好做
 * 存在性校验）；Forge 把 {@code AddReloadListenerEvent} 收集的监听器追加在原版监听器之后
 * （证据链见 MachineLoader javadoc 的 0.28.0 节），而原版 RecipeManager 就在原版监听器里——
 * 所以本监听器的 {@code apply} 运行时 manager 已经装满 datapack 配方（含全部声明占位）。
 *
 * <p><b>客户端</b>：零额外工作。注入完成于 reload 内，玩家同步（{@code ClientboundUpdateRecipesPacket}）
 * 发生在 reload 之后——生成配方经序列化器的 {@code toNetwork/fromNetwork} 随原版同步包自动到客户端，
 * 占位已被剔除所以从不同步。JEI / 命令 / 控制器读到的是回注后的同一份 manager。
 *
 * <p><b>幂等</b>：每次 reload 从 JSON 重新解析声明、重新生成、整体替换——没有跨 reload 状态。
 * 没有声明时一个字节都不动 manager（无适配器的整合包零影响）。
 */
public final class RecipeAdapterLoader extends SimplePreparableReloadListener<Void> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 收集监听器时捕获（每次 reload 的 manager 实例贯穿整条链，apply 时已装满）。 */
    private final RecipeManager recipes;
    /** 熔炉适配器解析输出物品用（{@code SmeltingRecipe#getResultItem} 需要 RegistryAccess）。 */
    private final RegistryAccess registryAccess;

    public RecipeAdapterLoader(RecipeManager recipes, RegistryAccess registryAccess) {
        this.recipes = recipes;
        this.registryAccess = registryAccess;
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null; // 不读资源——生成轮的全部输入都在 manager 与机器注册表里。
    }

    @Override
    protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<Recipe<?>> all = new ArrayList<>(this.recipes.getRecipes());
        List<MachineRecipe> declarations = new ArrayList<>();
        for (Iterator<Recipe<?>> iterator = all.iterator(); iterator.hasNext(); ) {
            Recipe<?> recipe = iterator.next();
            if (recipe instanceof MachineRecipe machine && machine.isAdapterDeclaration()) {
                declarations.add(machine);
                iterator.remove();
            }
        }
        if (declarations.isEmpty()) {
            return;
        }

        List<MachineRecipe> generated;
        try {
            // 校验生成轮全部 ID 后才发布；冲突时尚未修改 manager，原配方和占位均保留。
            generated = RecipeAdapterRegistry.generateAll(declarations, this.recipes, this.registryAccess,
                    com.reborn.modularmachinery.machine.ReloadCycle::machineExists);
        } catch (IllegalArgumentException conflict) {
            LOGGER.error("[{}] Recipe adapters rejected; original RecipeManager retained: {}",
                    ModularMachineryReborn.MOD_ID, conflict.getMessage());
            return;
        }
        LOGGER.info("[{}] Recipe adapters: {} declaration(s) generated {} recipe(s); placeholder(s) removed",
                ModularMachineryReborn.MOD_ID, declarations.size(), generated.size());

        List<Recipe<?>> combined = new ArrayList<>(all.size() + generated.size());
        combined.addAll(all);
        combined.addAll(generated);
        this.recipes.replaceRecipes(combined);
    }
}
