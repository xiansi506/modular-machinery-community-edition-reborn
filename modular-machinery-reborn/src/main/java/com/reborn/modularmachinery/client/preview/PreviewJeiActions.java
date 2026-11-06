package com.reborn.modularmachinery.client.preview;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/**
 * 预览材料格 → JEI 查询的桥（E2，0.101.0；旧版 {@code SlotItemVirtualJEI} 的对应物）。
 *
 * <p><b>这个类只在 JEI 在场时才会被类加载</b>：JEI 是可选依赖（mods.toml {@code mandatory=false}），
 * 类本身是 {@code public}（JEI 插件跨包交运行时），但除 {@link #onRuntimeAvailable} 外的方法都是包私有；
 * <b>任何调用都必须先过 {@code ModList.get().isLoaded("jei")}</b>——1.20.1 没有
 * {@code @Optional.Method}，「守卫 + 独立类」是它的等价物（与 compat/ 对 Mekanism 的隔离设计同理）。
 *
 * <p>行为照旧版：左键 / R 键把物品作为<b>产出</b>（{@code OUTPUT}）打开 JEI 配方页，
 * 右键 / U 键作为<b>输入</b>（{@code INPUT}）查用途。旧版的 A 键书签没有对应物——
 * JEI 15 的公开 API 没有编程式书签入口（书签只经它自己的界面操作），有据不移植。
 *
 * <p>API 签名按 javap 核对（jei-15.49）：{@code IRecipesGui#show(IFocus)}、
 * {@code IFocusFactory#createFocus(RecipeIngredientRole, ITypedIngredient)}、
 * {@code IIngredientManager#createTypedIngredient(V)}、
 * {@code IJeiKeyMappings#getShowRecipe()/getShowUses()}、
 * {@code IJeiKeyMapping#isActiveAndMatches(InputConstants.Key)}。
 */
public final class PreviewJeiActions {

    private static IJeiRuntime runtime;

    private PreviewJeiActions() {
    }

    /** 由本模组的 JEI 插件在运行时就绪时交入；JEI 未装或未启动时保持 null。 */
    public static void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        PreviewJeiActions.runtime = jeiRuntime;
    }

    /** JEI 运行时关闭时由插件回调清空；此后所有查询都安全回退宿主，直到新的运行时就绪。 */
    public static void onRuntimeUnavailable() {
        PreviewJeiActions.runtime = null;
    }

    /**
     * 用给定的物品打开 JEI 的配方/用途页。
     *
     * @param stack  材料格里的物品
     * @param asInput {@code true} = 作为输入查用途（旧版 {@code IFocus.Mode.INPUT}），
     *                {@code false} = 作为产出查配方（{@code OUTPUT}）
     * @return 是否真的采取了动作（JEI 未就绪、物品不在 JEI 索引里时返回 {@code false}，点击落回宿主）
     */
    static boolean show(ItemStack stack, boolean asInput) {
        if (PreviewJeiActions.runtime == null || stack.isEmpty()) {
            return false;
        }
        ITypedIngredient<ItemStack> typed =
                PreviewJeiActions.runtime.getIngredientManager().createTypedIngredient(stack).orElse(null);
        if (typed == null) {
            return false;
        }
        IFocus<ItemStack> focus = PreviewJeiActions.runtime.getJeiHelpers().getFocusFactory()
                .createFocus(asInput ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT, typed);
        PreviewJeiActions.runtime.getRecipesGui().show(focus);
        return true;
    }

    /** 悬停材料格时的 R/U 快捷键；命中并执行返回 {@code true}。 */
    static boolean keyPressed(ItemStack stack, int keyCode, int scanCode) {
        if (PreviewJeiActions.runtime == null || stack.isEmpty()) {
            return false;
        }
        IJeiKeyMappings keys = PreviewJeiActions.runtime.getKeyMappings();
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keys.getShowRecipe().isActiveAndMatches(key)) {
            return show(stack, false);
        }
        if (keys.getShowUses().isActiveAndMatches(key)) {
            return show(stack, true);
        }
        return false;
    }
}
