package com.reborn.modularmachinery.api.event;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * C2 收尾（0.89.0）：工厂线程逐 tick 需求失败时发出——旧版 {@code FactoryRecipeFailureEvent}
 * （{@code TileFactoryController:293}）的对应物。
 *
 * <p>{@link #cause()} 是这次的失败键（本版由当前 {@code recipe.tickFailure(...)} 产出；
 * 只读诊断仍可付时使用通用 tick 结算失败原因，不猜测不透明处理器拒绝细节。旧版是
 * {@code thread.getStatus().getUnlocMessage()}，同一位置）。{@link #destructRecipe()} 默认 =
 * 配方的 {@code cancelIfPerTickFails}（旧版 {@code doesCancelRecipeOnPerTickFailure()}），监听器可
 * 按次改写：{@code true} = 清空该槽位（已付一次性输入不返还，槽位回到空闲、下次 tick 可再开工）；
 * {@code false} = 走机器的 {@code failure-action}。0.75.0–0.88.0 期间本引擎直接读配方字段、
 * 第三方不能覆盖——这条已记差异就此收回（与单控制器 {@link RecipeFailureEvent} 同步收回）。
 */
public final class FactoryRecipeFailureEvent extends FactoryRecipeEvent {

    @Nullable
    private final String cause;
    private boolean destructRecipe;

    /** For Forge's listener-list probe only; see {@link MachineEvent#MachineEvent()}. */
    public FactoryRecipeFailureEvent() {
        this.cause = null;
        this.destructRecipe = false;
    }

    public FactoryRecipeFailureEvent(MachineControllerBlockEntity controller, @Nullable ResourceLocation machineId,
                                     MachineRecipe recipe, int progress, int maxProgress, int parallelism,
                                     int threadOrdinal, @Nullable String cause, boolean destructRecipe) {
        super(controller, machineId, recipe, progress, maxProgress, parallelism, threadOrdinal);
        this.cause = cause;
        this.destructRecipe = destructRecipe;
    }

    /** 这次失败的展示文案（失败键），可能为 {@code null}。 */
    @Nullable
    public String cause() {
        return this.cause;
    }

    /** 是否清空槽位；默认来自配方的 {@code cancelIfPerTickFails}，监听器可改写。 */
    public boolean destructRecipe() {
        return this.destructRecipe;
    }

    /** 改写清空与否——最后一个写入者生效。 */
    public void setDestructRecipe(boolean destructRecipe) {
        this.destructRecipe = destructRecipe;
    }
}
