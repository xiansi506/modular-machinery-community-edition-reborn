package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.MachineCasingBlockEntity;
import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 机器部件方块的染色处理器：把定义的 {@code color} 画到部件模型的**背景层**上。
 *
 * <h2>整条链（旧版 → 本版）</h2>
 *
 * <ol>
 *   <li>定义里的 {@code color} 被解析进 {@code MachineDefinition}（0.60.0）；</li>
 *   <li>控制器在成型/换机时把颜色**推给它的每个部件方块实体**（旧版
 *       {@code TileMultiblockMachineController#distributeCasingColor} → {@code tryColorize}）；</li>
 *   <li>本处理器在渲染时按位置读回那个颜色——**这就是它需要 {@code BlockPos} 的原因**，
 *       而 1.20.1 的 {@link BlockColor#getColor} 恰好带位置参数，于是旧版那种做法可以照搬。</li>
 * </ol>
 *
 * <h2>哪些模型参与染色</h2>
 *
 * <p>只有**背景层带 {@code tintindex}** 的模型会被调用。本版给 {@code overlay_base} 的背景层
 * （{@code bg_*}）加了 tint——与旧版 {@code blockmodel_overlay.json} 完全一致（那一层的六个面带
 * {@code "tintindex": 1}，而覆盖层 {@code ov_*} 不带，所以 overlay 图案保持原色）。
 * 控制器与升级总线用的是这套父子模型。
 *
 * <p>控制器、结构外壳与各级仓都注册到这个处理器。外壳和仓使用整面 tint 模型；控制器与升级总线
 * 使用两层模型，只有背景层参与染色。控制器正面使用单独贴图，按当前外观要求也参与染色。
 *
 * <h2>为什么「没设过」返回白色而不是「不返回」</h2>
 *
 * <p>{@code getColor} 没有「不染色」这个返回值，只能给一个颜色，而白色是乘法的单位元
 * （{@code 0xFFFFFF} 相乘不改变贴图）。所以 {@code null} → 白 = 外观与不染色完全相同。
 */
@Mod.EventBusSubscriber(modid = ModularMachineryReborn.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MachinePartColors implements BlockColor {

    /** 白色：乘法的单位元，也就是「不改变贴图」。 */
    private static final int NO_TINT = 0xFFFFFF;

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Block event) {
        MachinePartColors handler = new MachinePartColors();
        // 控制器（含数据化控制器：它们共用同一个方块实体类型）。
        // 全部控制器（普通 / 工厂 / 每台机器自己那两个）——它们共用同一个方块实体与同一个模型。
        event.register(handler, com.reborn.modularmachinery.block.ModBlocks.controllerBlocks());
        event.register(handler, com.reborn.modularmachinery.magic.MagicBlocks.blocks());
        // 六种外壳共用一个方块与一个颜色存储实体。
        event.register(handler, com.reborn.modularmachinery.block.ModBlocks.BLOCK_CASING.get());
        // 六个仓族各一个方块（等级由 blockstate 的 size 属性决定，所以一族一个块）。
        // 逐个注册而不是注册给 Block.class：Jade 那次教训说明「全量注册」会把别的模组方块也卷进来。
        for (com.reborn.modularmachinery.block.HatchKind kind
                : com.reborn.modularmachinery.block.HatchKind.values()) {
            var block = com.reborn.modularmachinery.block.ModBlocks.hatchBlock(kind);
            if (block != null) event.register(handler, block.get());
        }
    }

    @Override
    public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (level == null || pos == null) {
            return NO_TINT;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof com.reborn.modularmachinery.block.WirelessEnergyHatch wireless) {
            if (tintIndex == 2) return wireless.networkColor();
            return wireless.machineColor() == null ? NO_TINT : wireless.machineColor();
        }
        if (entity instanceof MachineCasingBlockEntity casing && casing.machineColor() != null) {
            return casing.machineColor();
        }
        if (entity instanceof MachineHatchBlockEntity hatch && hatch.machineColor() != null) {
            return hatch.machineColor();
        }
        if (entity instanceof com.reborn.modularmachinery.magic.MagicHatchBlockEntity magic && magic.machineColor() != null) return magic.machineColor();
        // 控制器自己那一份由 MachineControllerBlockEntity 提供（它不属于上一种类型）。
        if (entity instanceof com.reborn.modularmachinery.block.MachineControllerBlockEntity controller
                && controller.machineColor() != null) {
            return controller.machineColor();
        }
        return NO_TINT;
    }
}
