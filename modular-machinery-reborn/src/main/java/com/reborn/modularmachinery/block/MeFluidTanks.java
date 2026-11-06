package com.reborn.modularmachinery.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;

/**
 * C4-II（0.92.0，统一总线流体半边）：ME 总线的内部流体存储——旧版 {@code MEFluidBus} 的
 * {@code AEFluidInventoryUpgradeable(TANK_SLOT_AMOUNT, TANK_DEFAULT_CAPACITY)} 对应物，**9 罐 × 8000 mB**
 * 逐槽独立，常量照搬旧版（{@code MEFluidBus.java:42-43,56}）。
 *
 * <p>两套读写口：{@link #tank(int)} 给搬运逻辑用**指定槽**直读直写（旧版逐槽语义）；
 * {@link IFluidHandler} 视图（本类实现）给端口/管道用——找「同流体或第一个空罐」的普通罐语义。
 * 不 import AE2：网络侧的搬运由 {@link MeFluidBusLogic.Storage} 适配。
 */
public final class MeFluidTanks implements IFluidHandler,
        com.reborn.modularmachinery.recipe.ResourceTransaction.CandidateFluids,
        com.reborn.modularmachinery.recipe.ResourceTransaction.SnapshotParticipant {

    @Override public int candidateFillLimit(java.util.List<FluidStack> contents, FluidStack offered) { return offered.getAmount(); }
    @Override public int candidateDrainLimit(java.util.List<FluidStack> contents, FluidStack requested) { return requested.getAmount(); }
    @Override public Object transactionSnapshot() { return serializeNBT(); }
    @Override public boolean transactionUnchanged(Object snapshot) { return serializeNBT().equals(snapshot); }
    @Override public void transactionRestore(Object snapshot) { deserializeNBT(((CompoundTag) snapshot).copy()); }

    /** 旧版 {@code MEFluidBus.TANK_SLOT_AMOUNT}。 */
    public static final int SLOTS = 9;
    /** 旧版 {@code MEFluidBus.TANK_DEFAULT_CAPACITY}（mB）。 */
    public static final int CAPACITY = 8000;

    private final FluidTank[] tanks = new FluidTank[SLOTS];

    public MeFluidTanks() {
        for (int i = 0; i < SLOTS; i++) {
            this.tanks[i] = new FluidTank(CAPACITY);
        }
    }

    /** 罐数（常量 SLOTS 的实例侧别名，逻辑代码用它数池子）。 */
    public int tankCount() {
        return SLOTS;
    }

    /** 全池总容量（mB）——目标量的上限按它夹。 */
    public int totalCapacity() {
        return SLOTS * CAPACITY;
    }

    /** 指定槽的罐本体，搬运逻辑直用。 */
    public FluidTank tank(int slot) {
        return this.tanks[slot];
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (FluidTank tank : this.tanks) {
            list.add(tank.writeToNBT(new CompoundTag()));
        }
        tag.put("tanks", list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        ListTag list = tag.getList("tanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOTS && i < list.size(); i++) {
            this.tanks[i].readFromNBT(list.getCompound(i));
        }
    }

    // ------------------------------------------------------------ IFluidHandler 视图（端口/管道）

    @Override
    public int getTanks() {
        return SLOTS;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.tanks[tank].getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, @Nullable FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        // 同一次 fill 可以跨多个罐；SIMULATE 与 EXECUTE 使用相同顺序。
        int remaining = resource.getAmount();
        for (FluidTank tank : this.tanks) {
            if (!tank.getFluid().isEmpty() && tank.getFluid().isFluidEqual(resource)) {
                remaining -= tank.fill(new FluidStack(resource, remaining), action);
            }
            if (remaining <= 0) return resource.getAmount();
        }
        for (FluidTank tank : this.tanks) {
            if (tank.getFluid().isEmpty()) {
                remaining -= tank.fill(new FluidStack(resource, remaining), action);
            }
            if (remaining <= 0) return resource.getAmount();
        }
        return resource.getAmount() - remaining;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        int remaining = resource.getAmount();
        for (FluidTank tank : this.tanks) {
            if (!tank.getFluid().isEmpty() && tank.getFluid().isFluidEqual(resource)) {
                remaining -= tank.drain(new FluidStack(resource, remaining), action).getAmount();
            }
            if (remaining <= 0) break;
        }
        return remaining == resource.getAmount() ? FluidStack.EMPTY
                : new FluidStack(resource, resource.getAmount() - remaining);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        for (FluidTank tank : this.tanks) {
            if (!tank.getFluid().isEmpty()) {
                return drain(new FluidStack(tank.getFluid(), maxDrain), action);
            }
        }
        return FluidStack.EMPTY;
    }
}
