package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.Set;

/** Collects ports, selector tags and isolated groups from the matched structure. */
final class ControllerPortCollector {
    private ControllerPortCollector() {}
    static HatchCollection collect(MachineControllerBlockEntity controller, Level level, BlockPos pos,
                                   @Nullable MachineDefinition machine, @Nullable MachinePattern effectivePattern,
                                   Set<BlockPos> dependencies) {
        if (machine == null || effectivePattern == null) {
            return HatchCollection.EMPTY;
        }
        for (BlockPos relative : effectivePattern.positions().keySet()) dependencies.add(pos.offset(relative));
        HatchCollection.Builder builder = HatchCollection.builder();
        for (BlockPos relative : effectivePattern.positions().keySet()) {
            if (!level.hasChunkAt(pos.offset(relative))) continue;
            if (level.getBlockEntity(pos.offset(relative)) instanceof com.reborn.modularmachinery.magic.MagicHatchBlockEntity magic) {
                builder.addMagicPort(magic.backend(), effectivePattern.tag(relative));
            }
            if (level.getBlockEntity(pos.offset(relative)) instanceof MachineHatchBlockEntity hatch) {
                // 把结构位置上的 `selector-tag` 一起交给端口：配方需求靠它筛出「属于那个部件」的仓口。
                // 绝大多数结构没有标签，此时 tag() 答 null，端口就按「无标签」入桶。
                // A2：组号与标签一起交给端口。仓口种类不可分组时 effectiveGroup() 已经是 -1；
                // Builder 会再按种类归一一次，两层都挡「输出仓被分组」。
                builder.add(hatch.kind(), hatch.itemHandler(), hatch.fluidTank(), hatch.energyStorage(),
                        effectivePattern.tag(relative), hatch.effectiveGroup());
                // C4-II（0.92.0，统一总线）：ME 总线的流体半边作为流体端口另入桶（ITEM 家族的分桶
                // 不会带上它）；普通仓口 extraFluidPort() 为 null，这里什么都不加。
                net.minecraftforge.fluids.capability.IFluidHandler extraFluid = hatch.extraFluidPort();
                if (extraFluid != null) {
                    builder.addFluidPort(extraFluid, hatch.kind().isInput(), effectivePattern.tag(relative),
                            hatch.kind().canGroupInput() ? hatch.effectiveGroup() : -1);
                }
                // 波 5.3：**兼容端口**。主模组不知道那是什么能力（气体？灵气？），所以只把实体交出去，
                // 由装了相应模组的兼容层回答。没装时这里什么都不加，桶保持为空。
                builder.gasPorts(hatch, hatch.kind().isInput(), effectivePattern.tag(relative),
                        hatch.kind().canGroupInput() ? hatch.effectiveGroup() : -1);
            }
        }
        java.util.Set<Integer> usedGroups = new java.util.HashSet<>();
        for (BlockPos relative : effectivePattern.positions().keySet()) {
            if (!level.hasChunkAt(pos.offset(relative))) continue;
            var entity = level.getBlockEntity(pos.offset(relative));
            if (entity instanceof MachineHatchBlockEntity hatch && hatch.effectiveGroup() >= 0) usedGroups.add(hatch.effectiveGroup());
            if (entity instanceof com.reborn.modularmachinery.machine.MachineInputProvider provider && provider.declaredInputGroup() >= 0) usedGroups.add(provider.declaredInputGroup());
        }
        java.util.IdentityHashMap<Object,Integer> allocated = new java.util.IdentityHashMap<>();
        com.reborn.modularmachinery.machine.MachineInputProvider.GroupAllocator groups = (identity, preferred) -> {
            Integer existing = allocated.get(identity); if (existing != null) return existing;
            int value = isolatedAddress(usedGroups, preferred);
            if (value < 0) return -1;
            usedGroups.add(value); allocated.put(identity, value); return value;
        };
        for (BlockPos relative : effectivePattern.positions().keySet()) {
            if (level.hasChunkAt(pos.offset(relative)) && level.getBlockEntity(pos.offset(relative)) instanceof com.reborn.modularmachinery.machine.MachineInputProvider provider) {
                dependencies.addAll(provider.inputDependencies());
                provider.appendInputs(builder, effectivePattern.tag(relative), groups);
            }
        }
        // M6d-b: the smart-interface values travel with the ports (see HatchCollection's class comment). The
        // source is this block entity, i.e. the same object the recipe lifecycle already reaches through the
        // original's controller -> foundSmartInterfaces -> provider chain, with the middle link removed.
        builder.smartInterfaces(controller);
        // …and the definition rides along for the declared type's own `notequal` message.
        builder.machine(machine);
        return builder.build();
    }

    /** Bounded by actual reservations; long arithmetic cannot wrap MAX_VALUE into a general/negative group. */
    private static int isolatedAddress(Set<Integer> used, int preferred) {
        if (preferred >= 65536 && !used.contains(preferred)) return preferred;
        long attempts = (long) used.size() + 1L;
        for (long candidate = 65536; candidate <= Integer.MAX_VALUE && attempts-- > 0; candidate++) {
            if (!used.contains((int) candidate)) return (int) candidate;
        }
        return -1;
    }
}
