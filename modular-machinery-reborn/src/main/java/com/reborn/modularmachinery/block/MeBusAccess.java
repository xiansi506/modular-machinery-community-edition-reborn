package com.reborn.modularmachinery.block;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * C4（0.73.0）：菜单与方块共享的 ME 总线视图。
 *
 * <p>这个接口刻意不 import 任何 {@code appeng} 类型：菜单和 {@code MeItemBusBlock} 只需要
 * 「有没有配置槽」和「网络在不在线」两件事，网络节点本身留在
 * {@code compat.ae2.MeItemBusBlockEntity} 里。0.93.0 起它还暴露**流体半边**（统一总线拍板）：
 * 罐、ghost 配置的读与服务端写——类型全部来自主模组，AE2 隔离不变。
 */
public interface MeBusAccess {

    /** 输入总线的 16 个配置槽；输出总线没有配置槽，返回 {@code null}。 */
    @Nullable
    ItemStackHandler configHandler();

    /** 节点是否已连接并有频道；没有 AE2 时这个实现根本不会被创建。 */
    boolean meOnline();

    /** C4-II（0.92.0 起有值）：流体半边的 9 罐；默认没有（接口也被非 ME 仓口实现时安全）。 */
    @Nullable
    default MeFluidTanks fluidTanks() {
        return null;
    }

    /** C4-II（0.94.0）：输入总线配置格的**物品 ghost**（真实堆栈，仅作标记与数量；空 = 无）。 */
    @Nullable
    default net.minecraft.world.item.ItemStack configItemAt(int slot) {
        return null;
    }

    /** C4-II：某配置格的流体标记；{@code null} = 该格不是流体标记。 */
    @Nullable
    default Fluid fluidMarkAt(int slot) {
        return null;
    }

    /** C4-II：某配置格流体标记的目标量（mB）；无标记为 0。 */
    default int fluidMarkAmount(int slot) {
        return 0;
    }

    default FluidStack fluidMarkStackAt(int slot) {
        Fluid fluid = fluidMarkAt(slot);
        return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, fluidMarkAmount(slot));
    }

    /**
     * C4-II（0.94.0）：配置格写入三入口（服务端，互斥于同一格）：物品 ghost / 流体标记 / 清除。
     * 实现负责清退避、置脏、方块更新；默认无操作（安全空实现）。
     */
    default void setConfigItem(int slot, @Nullable net.minecraft.world.item.ItemStack ghost) {
    }

    default void setConfigFluid(int slot, @Nullable Fluid fluid, int amount) {
    }

    default void setConfigFluidStack(int slot, FluidStack stack) {
        setConfigFluid(slot, stack.isEmpty() ? null : stack.getFluid(), stack.getAmount());
    }

    @Nullable default MeGasStorage gasTanks() { return null; }
    default boolean gasNetworkAvailable() { return false; }
    default void setConfigGas(int slot, @Nullable net.minecraft.resources.ResourceLocation gas, int amount) { }
    /** Encoded AE gas stacks are supported only by the optional AppMek bridge. */
    @Nullable default MeGasStorage.Entry encodedGas(net.minecraft.world.item.ItemStack stack) { return null; }
}
