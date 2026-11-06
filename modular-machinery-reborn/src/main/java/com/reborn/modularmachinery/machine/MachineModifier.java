package com.reborn.modularmachinery.machine;

import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * One entry of a machine definition's {@code modifiers} array — the original's
 * {@code SingleBlockModifierReplacement}.
 *
 * <p>The original's semantics, kept exactly: a modifier names <b>one position</b> (relative to the controller)
 * and the blocks that may sit there, plus the {@link RecipeModifier}s that apply while they do. When the
 * structure forms, every position whose block actually matches contributes its modifiers to the craft. That is
 * the machinery behind the alloy furnace's built-in trick — put a vent above the controller and its item output
 * is doubled ({@code alloy_furnace.json}).
 *
 * <p>这描述的是旧版三个 replacement 类里的 {@code SingleBlockModifierReplacement}：一个坐标、一格方块。
 * 另外两个的现状：
 *
 * <ul>
 *   <li>{@code MultiBlockModifierReplacement} 做的是「另一段结构是否还在」，<strong>不是</strong>替换动态段；
 *       0.72.0（C9）把它移植为 {@link MachineMultiBlockModifier}，见 {@code multi-block-modifiers}。</li>
 *   <li>{@code DynamicModifierReplacement} 是同一件事的动态机器形态；本版没有对应的动态 modifier 入口，
 *       仍不移植。</li>
 * </ul>
 */
public record MachineModifier(BlockPos offset, List<BlockMatcher> accepted, List<RecipeModifier> modifiers,
                              String description) {

    /**
     * Whether the block actually placed at this modifier's position is one the definition accepts, after the
     * controller's rotation has been applied to the accepted descriptors as well as to the offset.
     *
     * <p>The original rotated both the offset and each accepted block descriptor counter-clockwise once per
     * quarter turn of the controller ({@code TileMultiblockMachineController#updateModifiers}), so a definition
     * is written once, for the north-facing controller, and works in all four orientations.
     */
    public boolean matches(BlockState state, int rotationSteps) {
        for (BlockMatcher matcher : acceptedAt(rotationSteps)) {
            if (matcher.matches(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The accepted candidates turned by {@code rotationSteps} counter-clockwise quarter turns.
     *
     * <p>Public because the controller and any offline reproduction must be able to assert the same candidate
     * set the runtime uses; it is a pure function of this immutable definition entry. The per-matcher rotation
     * result is cached on {@link BlockMatcher} itself, so repeated structure checks only re-walk a short list.
     *
     * <p>候选列表<strong>不能只取第一个</strong>：一个部分状态描述符（例如耦合属性）旋转后可能拆成多个精确
     * 候选，所以这里把每个 matcher 的返回列表全部展开，并按描述符去重（同一描述符语义相同）。
     */
    public List<BlockMatcher> acceptedAt(int rotationSteps) {
        int turns = Math.floorMod(rotationSteps, 4);
        List<BlockMatcher> result = List.copyOf(this.accepted);
        for (int step = 0; step < turns; step++) {
            LinkedHashMap<String, BlockMatcher> deduplicated = new LinkedHashMap<>();
            for (BlockMatcher matcher : result) {
                for (BlockMatcher rotated : matcher.rotateYCounterClockwise()) {
                    deduplicated.putIfAbsent(rotated.descriptor(), rotated);
                }
            }
            result = List.copyOf(deduplicated.values());
        }
        return result;
    }

    /** The offset, rotated one quarter turn counter-clockwise per step — the original's {@code (z, y, -x)}. */
    public BlockPos rotatedOffset(int steps) {
        BlockPos pos = this.offset;
        for (int i = 0; i < Math.floorMod(steps, 4); i++) {
            pos = new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        }
        return pos;
    }
}
