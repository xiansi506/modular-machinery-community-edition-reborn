package com.reborn.modularmachinery.api.event;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * C2b（0.88.0）：一条**运行中的配方**放弃时发出——旧版 {@code RecipeFailureEvent}
 * （{@code TileMachineController.java:282-294} 的 {@code onFailure}）的对应物。
 *
 * <p>{@link #cause()} 是这次的逐 tick 失败原因（{@code craftcheck.failure.*}），
 * 旧版是 {@code thread.getStatus().getUnlocMessage()}——同样的位置，同样的「可直接展示的键/文案」角色。
 * 原生事件载荷供监听器读取，不等同于控制器的开工失败界面字段；诊断无法确认具体资源时使用通用结算失败键。
 *
 * <p>{@link #destructRecipe()} 的默认值是配方的 {@code cancelIfPerTickFails}（旧版
 * {@code recipe.doesCancelRecipeOnPerTickFailure()}），监听器可以改写：{@code true} =
 * 放弃这次合成（进度、激活配方、组、冻结的修改器全部清空，已付的一次性输入**不返还**——与旧版一致）；
 * {@code false} = 走机器的 {@code failure-action}（默认冻结进度）。0.75.0 起本版直接读配方字段，
 * 0.88.0 起改回事件通道，第三方可以按次改写。
 */
public final class RecipeFailureEvent extends RecipeEvent {

    @Nullable
    private final String cause;
    private boolean destructRecipe;

    /** For Forge's listener-list probe only; see {@link MachineEvent#MachineEvent()}. */
    public RecipeFailureEvent() {
        this.cause = null;
        this.destructRecipe = false;
    }

    public RecipeFailureEvent(MachineControllerBlockEntity controller, @Nullable ResourceLocation machineId,
                              MachineRecipe recipe, int progress, int maxProgress, int parallelism,
                              @Nullable String cause, boolean destructRecipe) {
        super(controller, machineId, recipe, progress, maxProgress, parallelism);
        this.cause = cause;
        this.destructRecipe = destructRecipe;
    }

    /** 这次失败的展示文案（失败键），可能为 {@code null}（旧版同样允许无名失败）。 */
    @Nullable
    public String cause() {
        return this.cause;
    }

    /** 是否放弃这次合成；默认来自配方的 {@code cancelIfPerTickFails}，监听器可改写。 */
    public boolean destructRecipe() {
        return this.destructRecipe;
    }

    /** 改写放弃与否——旧版 {@code setDestructRecipe} 的对应物，最后一个写入者生效。 */
    public void setDestructRecipe(boolean destructRecipe) {
        this.destructRecipe = destructRecipe;
    }
}
