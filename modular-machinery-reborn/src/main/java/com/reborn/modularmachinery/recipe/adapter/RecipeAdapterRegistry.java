package com.reborn.modularmachinery.recipe.adapter;

import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C1 第一纵切（0.107.0）：适配器分派与生成配方 id 的唯一出处。
 *
 * <p>旧版对应物是 {@code RecipeAdapterRegistry}（Forge 注册表 {@code RegistriesMM.ADAPTER_REGISTRY} +
 * 按名取适配器）。本版第一纵切只有两个内置适配器，不值得一张 Forge 注册表——按 key 直接分派：
 * {@code minecraft:furnace} 走 {@link AdapterMinecraftFurnace}，其余 key 按另一台机器的 id 走
 * {@link DynamicMachineAdapter}（旧版 {@code CommonProxy:198} 给每台机器注册一个
 * {@code DynamicMachineRecipeAdapter}，效果等价：adapter key = 父机器 id）。11 个第三方适配器
 * 等所有者点名后再进这张分派（届时若数量多再升级成注册表）。
 *
 * <p>未知 adapter key 的行为照旧版语义（{@code createRecipesFor} 查无此适配器 → 返回 null →
 * 该声明产出零配方），但旧版静默，这里 warn 一行——pack 作者写错 id 应当有地方可查。
 */
public final class RecipeAdapterRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 原版熔炉适配器的 key（旧版 {@code AdapterMinecraftFurnace} 的注册名同款）。 */
    public static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "furnace");

    private RecipeAdapterRegistry() {
    }

    /**
     * 生成轮：把每条适配器声明展开成真实配方。
     *
     * <p>声明里的每台归属机器独立展开；未知机器（{@link MachineRegistry} 查无）照旧版
     * {@code RecipeRegistry:121-124} 的「unknown machine」处理——跳过并 warn，不炸整个 reload。
     * 生成 id 由声明 id、归属机器 id 和源配方 id 的 SHA-256 派生；
     * 新增其他声明或改变枚举顺序不影响已有配方身份。
     */
    public static List<MachineRecipe> generateAll(List<MachineRecipe> declarations, RecipeManager manager,
                                                  RegistryAccess access) {
        return generateAll(declarations, manager, access, MachineRegistry::contains);
    }

    /** Loading may validate against this cycle's candidate, without exposing it to gameplay readers. */
    public static List<MachineRecipe> generateAll(List<MachineRecipe> declarations, RecipeManager manager,
                                                  RegistryAccess access,
                                                  java.util.function.Predicate<ResourceLocation> machineExists) {
        List<MachineRecipe> generated = new ArrayList<>();
        Map<ResourceLocation, String> generatedOrigins = new LinkedHashMap<>();
        for (MachineRecipe declaration : declarations) {
            if (!declaration.requirements().isEmpty()) {
                // 旧版两个第一纵切适配器（熔炉/机器间）都无视声明里的附加需求
                //（createRecipesFor 收下 additionalRequirements 但从不读），这里同语义 + 提示一行。
                LOGGER.warn("[{}] Adapter declaration {} carries {} requirement(s); additional requirements on "
                                + "adapter declarations are not applied",
                        ModularMachineryReborn.MOD_ID, declaration.getId(), declaration.requirements().size());
            }
            // machineIds 已完成裸名/完整 ID 解析；按首次出现顺序只展开同一归属一次。
            for (ResourceLocation owning : new LinkedHashSet<>(declaration.machineIds())) {
                if (!machineExists.test(owning)) {
                    LOGGER.warn("[{}] Adapter declaration {} names unknown machine {} — skipped",
                            ModularMachineryReborn.MOD_ID, declaration.getId(), owning);
                    continue;
                }
                List<MachineRecipe> expanded = FURNACE.equals(declaration.adapterKey())
                        ? AdapterMinecraftFurnace.generate(declaration, owning, manager, access)
                        : DynamicMachineAdapter.generate(declaration, owning, manager);
                for (MachineRecipe recipe : expanded) {
                    String origin = "declaration=" + declaration.getId() + ", target=" + owning
                            + ", id=" + recipe.getId();
                    String previous = generatedOrigins.putIfAbsent(recipe.getId(), origin);
                    if (previous != null || manager.byKey(recipe.getId()).isPresent()) {
                        ResourceLocation source = manager.getRecipes().stream()
                                .map(net.minecraft.world.item.crafting.Recipe::getId)
                                .filter(id -> generatedId(declaration, owning, id).equals(recipe.getId()))
                                .findFirst().orElse(null);
                        throw new IllegalArgumentException("Adapter recipe ID conflict: " + origin
                                + ", source=" + source
                                + (previous == null ? "; existing RecipeManager recipe" : "; previous " + previous));
                    }
                    generated.add(recipe);
                }
            }
        }
        return generated;
    }

    /**
     * 生成配方的确定性 id：{@code <modid>:adapter/<SHA-256>}。
     * 控制器与工厂会保存该 id，必须跨 reload 和重启保持稳定。
     */
    static ResourceLocation generatedId(MachineRecipe declaration, ResourceLocation owning, ResourceLocation source) {
        // 声明、目标、源配方的身份决定 id，不受遍历顺序或其他配方增删影响。
        String identity = declaration.getId() + "\n" + owning + "\n" + source;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new ResourceLocation(ModularMachineryReborn.MOD_ID, "adapter/" + java.util.HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java runtime lacks SHA-256", impossible);
        }
    }
}
