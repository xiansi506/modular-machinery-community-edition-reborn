package com.reborn.modularmachinery.block;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;

/**
 * C4-II 的流体搬运算法（0.94.0 重构：照高版 AE2 接口的**按目标量**语义）。
 *
 * <p>0.92.0 的第一版照旧版流体总线的「逐槽维持到满」；所有者拍板统一总线对齐高版 ME 接口模型后，
 * 配置格是物品/流体混排的标记（{@code GenericStackInv}/{@code FakeSlot} 的对应物），维持语义随之
 * 变为：**每条流体标记 = 目标量**——网络里补到目标量为止、超了送回；罐（9×8000）只是共享池，
 * 不与配置格一一对应。未标记的流体在罐里 → 全部送回。
 *
 * <p>与 {@link MeItemBusLogic} 同一条设计纪律：不 import AE2，网络侧只认 {@link Storage}。
 * 失败退避按**标记条目**计（{@code failures[i]} 对应第 i 条标记），全量检查每 100 tick 清零。
 */
public final class MeFluidBusLogic {

    /** 网络侧必须提供的两个操作；返回实际搬运量（mB）。 */
    public interface Storage {
        int insert(FluidStack stack);

        int extract(Fluid fluid, int maxAmount);
    }

    private MeFluidBusLogic() {
    }

    /**
     * 输入总线的流体维持（按目标量）。{@code marks}/{@code amounts} 与配置格一一对应，
     * {@code marks[i] == null} 表示该格不是流体标记（跳过）。
     *
     * <p>P08-01（0.121.36）：标记数组是全部 16 个配置格（{@code MeItemBusBlockEntity.CONFIG_GRID}），
     * 物理流体罐只有 9 格共享池——两个上界语义不同。旧代码把维护循环的上界错写成
     * {@code tanks.tankCount()}，第 9～15 格的标记变成死配置（独立时永不维护，与 0～8 同流体时
     * 又静默抬高目标）。现在标记遍历覆盖 {@code marks.length} 全部格；物理罐循环（补液容量探测、
     * 灌入、送回、未标记回送）仍按实际罐数。{@code failures} 数组长度是物理罐格数，可能短于
     * {@code marks}：高位标记没有独立退避计数（守卫使它们每次 tick 都照常检查），这不是退避
     * 机制的重做，存档/菜单协议也未改动。
     */
    public static boolean tickInput(MeFluidTanks tanks, Fluid[] marks, int[] amounts, Storage storage,
                                    int[] failures, boolean fullCheck) {
        boolean moved = false;
        java.util.Set<Fluid> visited = new java.util.HashSet<>();
        for (int entry = 0; entry < marks.length; entry++) {
            Fluid fluid = marks[entry];
            if (fluid == null || !visited.add(fluid)) {
                continue;
            }
            if (fullCheck && entry < failures.length) failures[entry] = 0;
            if (!fullCheck && entry < failures.length && failures[entry] > 0) {
                continue;
            }
            long configured = 0;
            for (int i = 0; i < marks.length; i++) if (marks[i] == fluid) configured += amounts[i];
            int target = (int) Math.min(Math.max(0, configured), tanks.totalCapacity());
            int current = held(tanks, fluid);
            if (current < target) {
                int missing = target - current;
                int capacity = 0;
                for (int slot = 0; slot < tanks.tankCount(); slot++) {
                    capacity += tanks.tank(slot).fill(new FluidStack(fluid, missing - capacity),
                            IFluidHandler.FluidAction.SIMULATE);
                    if (capacity >= missing) break;
                }
                int extracted = extract(storage, fluid, Math.min(missing, capacity));
                if (extracted > 0) {
                    pour(tanks, fluid, extracted);
                    moved = true;
                }
                if (extracted < missing && entry < failures.length) {
                    failures[entry]++;
                }
            } else if (current > target) {
                int excess = current - target;
                int pushed = pushBack(tanks, storage, fluid, excess);
                if (pushed > 0) {
                    moved = true;
                }
                if (pushed < excess && entry < failures.length) {
                    failures[entry]++;
                }
            }
        }
        // 罐里任何未标记的流体：全部送回（接口模型里它没有存在理由）。
        if (pushUnmarked(tanks, storage, marks)) {
            moved = true;
        }
        return moved;
    }

    /** 输出总线：整罐推网络，收不下的留在罐里（维持语义不变）。 */
    public static boolean tickOutput(MeFluidTanks tanks, Storage storage, int[] failures, boolean fullCheck) {
        boolean moved = false;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            if (!fullCheck && slot < failures.length && failures[slot] > 0) {
                continue;
            }
            if (fullCheck && slot < failures.length) {
                failures[slot] = 0;
            }
            FluidTank tank = tanks.tank(slot);
            FluidStack held = tank.getFluid();
            if (held.isEmpty()) {
                continue;
            }
            int inserted = insert(storage, held);
            if (inserted > 0) {
                tank.drain(inserted, IFluidHandler.FluidAction.EXECUTE);
                moved = true;
            }
            if (!tank.getFluid().isEmpty() && slot < failures.length) {
                failures[slot]++;
            }
        }
        return moved;
    }

    /** 罐池里某流体的总量（mB）。 */
    private static int held(MeFluidTanks tanks, Fluid fluid) {
        int total = 0;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            FluidStack contents = tanks.getFluidInTank(slot);
            if (!contents.isEmpty() && contents.getFluid() == fluid) {
                total += contents.getAmount();
            }
        }
        return total;
    }

    /** 把抽到的流体倒进罐池：优先同流体罐，其次空罐；装不下的部分留在返回值之外（抽前已按余量算过，正常装得下）。 */
    private static void pour(MeFluidTanks tanks, Fluid fluid, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < tanks.tankCount() && remaining > 0; slot++) {
            FluidTank tank = tanks.tank(slot);
            if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() == fluid) {
                remaining -= tank.fill(new FluidStack(fluid, remaining), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        for (int slot = 0; slot < tanks.tankCount() && remaining > 0; slot++) {
            if (tanks.tank(slot).getFluid().isEmpty()) {
                remaining -= tanks.tank(slot).fill(new FluidStack(fluid, remaining),
                        IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    /** 从罐池里抽出某流体的指定量并送回网络；返回送回量。 */
    private static int pushBack(MeFluidTanks tanks, Storage storage, Fluid fluid, int amount) {
        int toPush = 0;
        for (int slot = 0; slot < tanks.tankCount() && toPush < amount; slot++) {
            FluidStack contents = tanks.tank(slot).getFluid();
            if (!contents.isEmpty() && contents.getFluid() == fluid) {
                toPush += Math.min(contents.getAmount(), amount - toPush);
            }
        }
        if (toPush <= 0) {
            return 0;
        }
        int inserted = insert(storage, new FluidStack(fluid, toPush));
        if (inserted <= 0) {
            return 0;
        }
        int remaining = inserted;
        for (int slot = 0; slot < tanks.tankCount() && remaining > 0; slot++) {
            FluidStack contents = tanks.tank(slot).getFluid();
            if (!contents.isEmpty() && contents.getFluid() == fluid) {
                int drained = Math.min(contents.getAmount(), remaining);
                tanks.tank(slot).drain(drained, IFluidHandler.FluidAction.EXECUTE);
                remaining -= drained;
            }
        }
        return inserted;
    }

    /** 未标记流体的整池送回；有送回返回 true。 */
    private static boolean pushUnmarked(MeFluidTanks tanks, Storage storage, Fluid[] marks) {
        boolean moved = false;
        for (int slot = 0; slot < tanks.tankCount(); slot++) {
            FluidStack contents = tanks.tank(slot).getFluid();
            if (contents.isEmpty() || isMarked(marks, contents.getFluid())) {
                continue;
            }
            int inserted = insert(storage, contents);
            if (inserted > 0) {
                tanks.tank(slot).drain(inserted, IFluidHandler.FluidAction.EXECUTE);
                moved = true;
            }
        }
        return moved;
    }

    private static boolean isMarked(Fluid[] marks, Fluid fluid) {
        for (Fluid mark : marks) {
            if (mark == fluid) {
                return true;
            }
        }
        return false;
    }

    private static int insert(Storage storage, FluidStack stack) {
        return Math.max(0, Math.min(stack.getAmount(), storage.insert(stack.copy())));
    }

    private static int extract(Storage storage, Fluid fluid, int maxAmount) {
        if (maxAmount <= 0) return 0;
        return Math.max(0, Math.min(maxAmount, storage.extract(fluid, maxAmount)));
    }
}
