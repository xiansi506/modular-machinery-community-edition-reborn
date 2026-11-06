package com.reborn.modularmachinery.common.assembly;

import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.MachinePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A8（0.106.0）：一次自动搭建的「材料清单」——旧版
 * {@code ink/ikx/mmce/common/utils/StructureIngredient}（61 行）的对应物。
 *
 * <p>旧版扫世界得到 {@code List<ItemIngredient>}（相对坐标 + 候选 ItemStack/state 元组 +
 * TE NBT），再把候选里含流体的格子拆成 {@code FluidIngredient}。本版形态更薄：
 *
 * <ul>
 *   <li>候选来自 {@link MachinePattern}（{@code MachineStructures.ghostStructure} 的产物）
 *       而不是另存一份 BlockArray——扫描与虚影/诊断是同一份结构，不会各说各话；</li>
 *   <li>候选是 {@link BlockState} 列表（{@link BlockMatcher#representativeState()} 去重），
 *       物品形态通过 {@code PropertyBlockItem.stackForState} 按实际等级取，普通方块保留原生映射；</li>
 *   <li>材料记录不携带 tile 数据；结构 matcher 可以另有 NBT/checker 规则，但自动搭建不会
 *       凭规则合成实体库存或伪造 NBT 满足它们。</li>
 * </ul>
 *
 * <p>液体格（候选 state 自带流体态，如水/熔岩块）单独标 {@link #fluidBlock}，
 * 消耗走背包流体容器（与旧版把流体拆到另一个列表同义）。
 */
public record AssemblyIngredient(BlockPos relPos, List<BlockState> candidates, boolean fluidBlock) {

    /** 从（已按朝向旋转、含动态段与 modifier 候选的）结构出发生成材料清单。 */
    public static List<AssemblyIngredient> of(MachinePattern structure) {
        Map<BlockPos, List<BlockState>> solids = new LinkedHashMap<>();
        Map<BlockPos, List<BlockState>> fluids = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : structure.positions().entrySet()) {
            List<BlockState> candidates = new ArrayList<>();
            for (BlockMatcher matcher : entry.getValue()) {
                BlockState representative = matcher.representativeState();
                if (representative == null || representative.isAir()) {
                    continue;
                }
                if (!candidates.contains(representative)) {
                    candidates.add(representative);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            BlockPos rel = entry.getKey().immutable();
            if (candidates.get(0).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                fluids.put(rel, candidates);
            } else {
                solids.put(rel, candidates);
            }
        }
        // 旧版 assembly() 先物品后液体（两个列表依序消费）；这里保持同一顺序。
        List<AssemblyIngredient> result = new ArrayList<>(solids.size() + fluids.size());
        solids.forEach((pos, candidates) -> result.add(new AssemblyIngredient(pos, candidates, false)));
        fluids.forEach((pos, candidates) -> result.add(new AssemblyIngredient(pos, candidates, true)));
        return List.copyOf(result);
    }

    /** 旧版只列世界里尚未匹配的方块，已建部分不再要求玩家重复携带材料。 */
    public static List<AssemblyIngredient> missing(MachinePattern structure, net.minecraft.world.level.Level level,
                                                    BlockPos controller) {
        return of(structure).stream().filter(ingredient -> structure.positions().get(ingredient.relPos()).stream()
                .noneMatch(matcher -> matcher.matches(level.getBlockState(controller.offset(ingredient.relPos()))))).toList();
    }
}
