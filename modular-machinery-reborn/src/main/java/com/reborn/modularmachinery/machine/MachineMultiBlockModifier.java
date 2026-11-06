package com.reborn.modularmachinery.machine;

import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * 一条 `multi-block-modifiers`：额外一段多块结构条件，条件全部命中时给配方贡献 {@link RecipeModifier}。
 *
 * <p><b>名字不是「替换结构」。</b>旧版的 {@code MultiBlockModifierReplacement} 实际做的事是
 * `BlockArray.matches(world, controller, false, null)`：检查另一段结构是否还在，命中后把它的 modifier
 * 交给控制器。它<strong>不改基座匹配、不替换方块、不隐藏方块</strong>。本类保留这个真实语义。
 *
 * <p><b>与旧版的一处有意差异：没有名字，也不去重。</b>旧版把命中的多块 modifier 按
 * {@code getModifierName()} 放进一张表，所以后写的多块条件会覆盖同名的单块条件
 * （{@code TileMultiblockMachineController.java:874-881}）。本版的单块 {@link MachineModifier} 本来就没有
 * 名字，没有可覆盖的键；因此这里把每个命中的条件<strong>全部追加</strong>，声明顺序保持不变。
 * 需要互斥的作者应当把条件写成互不重叠的结构，而不是依赖覆盖。
 *
 * <p><b>旋转会同时转坐标、selector-tag、规则与方块状态。</b>{@link MachinePattern#rotated(int)} 走
 * {@link MachinePattern#rotateYCounterClockwise()}，它把每个 {@link BlockMatcher} 交给
 * {@link BlockMatcher#rotateYCounterClockwise()}（旧版 {@code BlockInformation#copyRotateYCCW()} 的对应物），
 * 规则与标签也跟着位置一起转。P07-04（0.121.43）修好动态段的同一问题后，本类这句旧注释
 * 「只转位置与 selector-tag，不转方块状态」已经不成立；实际行为由
 * {@code _audit/review-sixth-4tasks-2026-11-03/J1/ModifierRotationCheck} 与
 * {@code P07-a2a/DynamicConditionAuditCheck#multiStateAndLoadedControls} 的四朝向断言钉住。
 *
 * <p><b>两个 {@code matches} 重载不是等价的。</b>接收 {@link Level} 的那个走
 * {@link MachinePattern#matches(Level, BlockPos)}，因此带上区块加载判断、NBT 规则与 {@code script-checker}
 * 语义；接收谓词的那个只把「这个坐标上有没有允许的方块」问出去，<strong>不执行任何规则</strong>。
 * 生产激活判断必须用前者（P07-05），后者只留给没有世界的离线复现器。
 */
public record MachineMultiBlockModifier(MachinePattern pattern, List<RecipeModifier> modifiers,
                                       String description) {

    public MachineMultiBlockModifier {
        Objects.requireNonNull(pattern, "pattern");
        modifiers = List.copyOf(modifiers);
        description = description == null ? "" : description;
    }

    /**
     * 把条件按控制器的朝向转过去，再检查整段结构。
     *
     * @param controller    控制器位置
     * @param rotationSteps 相对 NORTH 的逆时针四分之一圈数
     * @param at            「这个绝对坐标上是否有允许的方块」；调用者负责区块加载判断
     * @deprecated 只比较方块状态，<strong>不执行</strong> NBT 与 {@code script-checker} 规则。生产路径请用
     *             {@link #matches(Level, BlockPos, int)}；这个重载只为没有世界的离线复现器保留。
     *             P07-05 的实际缺陷就是控制器用了它，于是多块条件里的规则整条丢失。
     */
    @Deprecated
    public boolean matches(BlockPos controller, int rotationSteps,
                           BiPredicate<BlockPos, List<BlockMatcher>> at) {
        return this.pattern.rotated(rotationSteps).matches(controller, at);
    }

    /**
     * 完整条件匹配：旋转后的结构与世界逐位置比对，<strong>包含</strong>区块加载、NBT 规则与
     * {@code script-checker}。
     *
     * <p>这条路径复用的是 {@link MachinePattern#matches(Level, BlockPos)} 本身，不另抄一份 NBT /
     * checker 逻辑：安全语义（未加载短路、无实体方块用空 NBT、客户端跳过服务端 checker、
     * P07-01 的坏 token 拒绝）只在那一处维护，多块条件与基座结构因此永远一致。
     *
     * @param level         世界
     * @param controller    控制器位置
     * @param rotationSteps 控制器朝向相对 NORTH 的逆时针四分之一圈数
     */
    public boolean matches(Level level, BlockPos controller, int rotationSteps) {
        return this.pattern.rotated(rotationSteps).matches(level, controller);
    }

    /** 返回命中的条件，保持声明顺序；不修改传入列表。 */
    @Deprecated
    public static List<MachineMultiBlockModifier> active(List<MachineMultiBlockModifier> declared,
                                                         BlockPos controller, int rotationSteps,
                                                         BiPredicate<BlockPos, List<BlockMatcher>> at) {
        if (declared.isEmpty()) {
            return List.of();
        }
        List<MachineMultiBlockModifier> found = new ArrayList<>();
        for (MachineMultiBlockModifier modifier : declared) {
            if (modifier.matches(controller, rotationSteps, at)) {
                found.add(modifier);
            }
        }
        return List.copyOf(found);
    }

    /**
     * 返回命中的条件，保持声明顺序；不修改传入列表。这是生产路径（控制器结构检查）用的重载。
     *
     * <p>条件失败只意味着<strong>这一条</strong>不贡献 modifier：基础结构仍然成型，其余条件照常参与。
     * 列表是快照，调用者不能借它改回定义里的声明列表。
     */
    public static List<MachineMultiBlockModifier> active(List<MachineMultiBlockModifier> declared, Level level,
                                                         BlockPos controller, int rotationSteps) {
        if (declared.isEmpty()) {
            return List.of();
        }
        List<MachineMultiBlockModifier> found = new ArrayList<>();
        for (MachineMultiBlockModifier modifier : declared) {
            if (modifier.matches(level, controller, rotationSteps)) {
                found.add(modifier);
            }
        }
        return List.copyOf(found);
    }
}
