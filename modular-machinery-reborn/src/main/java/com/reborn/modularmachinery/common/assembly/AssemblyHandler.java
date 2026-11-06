package com.reborn.modularmachinery.common.assembly;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.item.BlueprintItem;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.machine.MachineStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A8（0.106.0）：自动搭建的事件接线——旧版
 * {@code ink/ikx/mmce/core/AssemblyEventHandler}（248 行）的对应物，全部 server 侧。
 *
 * <p>旧版的三个入口原样保留：
 *
 * <ul>
 *   <li><b>蓝图入槽</b>（:122-137）：手持蓝图右键控制器、槽为空 → 复制入槽并扣手上一个
 *       （创造不扣），取消原版交互。1.20.1 里槽操作走 {@code ItemStackHandler}。</li>
 *   <li><b>搭建触发</b>（:139-152 + :168-212）：手持配置物品（默认木棍）右键控制器、非潜行 →
 *       1 秒 token 防刷 → {@code assemblyBefore}。旧版是客户端发包带动态段 size；本版
 *       服务端事件直接处理、无网络包，动态段取各段 {@code minSize}（差异见迁移日志勘测节）。</li>
 *   <li><b>逐 tick 推进</b>（:214-242）：玩家 tick 每 {@code tickBlock} tick 放一块；
 *       控制器失效 → cancelled + 移除；完成 → success + 移除。登出清空（:244-247）。</li>
 * </ul>
 *
 * <p>注册在 Forge 总线（{@code ModularMachineryReborn} 构造处 {@code EVENT_BUS.register}）。
 */
public final class AssemblyHandler {

    /** 旧版 {@code ASSEMBLY_ACCESS_TOKEN}（1 秒过期）的等价：上次请求时间戳。 */
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();
    private static final long REQUEST_COOLDOWN_MILLIS = 1000L;

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isShiftKeyDown()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(level.getBlockEntity(event.getPos())
                        instanceof MachineControllerBlockEntity controller)) {
            return;
        }
        ItemStack held = event.getItemStack();

        // 蓝图入槽（旧版 :122-137）。
        if (held.getItem() instanceof BlueprintItem) {
            ItemStackHandler slots = controller.items();
            if (slots.getStackInSlot(MachineControllerBlockEntity.BLUEPRINT_SLOT).isEmpty()) {
                ItemStack copy = held.copy();
                copy.setCount(1);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                slots.setStackInSlot(MachineControllerBlockEntity.BLUEPRINT_SLOT, copy);
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
            return;
        }

        // 搭建触发（旧版 :139-152）。
        if (!held.is(net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(ModConfig.assemblyTriggerItem()))) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < REQUEST_COOLDOWN_MILLIS) {
            player.displayClientMessage(Component.translatable(
                    "message.modular_machinery_reborn.assembly.too_quickly")
                    .withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        if (requestAssembly(player, controller, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** 旧版 {@code processAutoAssembly} + {@code assemblyBefore} 的服务端直连形态。 */
    private boolean requestAssembly(ServerPlayer player, MachineControllerBlockEntity controller, BlockPos pos) {
        ResourceLocation machineId = controller.previewMachineId();
        if (machineId == null) {
            // 已成型控制器的兜底（旧版 getParentMachine 分支）：成型时 machineId 就是绑定机器。
            machineId = controller.machineId();
        }
        MachineDefinition machine = machineId == null ? null : MachineRegistry.byId(machineId).orElse(null);
        if (machine == null) {
            player.displayClientMessage(Component.translatable(
                    "message.modular_machinery_reborn.assembly.no_machine")
                    .withStyle(ChatFormatting.RED), false);
            return true;
        }
        if (MachineAssemblyManager.exists(player.level().dimension(), pos)) {
            player.displayClientMessage(Component.translatable(
                    "message.modular_machinery_reborn.assembly.already_assembly")
                    .withStyle(ChatFormatting.YELLOW), false);
            return true;
        }
        // 未成型控制器：dynamicPatterns 为空（结构检查没跑过）→ assembleUnrotated 用各段 minSize，
        // rotationSteps 从方块朝向取得；已成型时沿用匹配得到的朝向。
        var structure = MachineStructures.runtimeStructure(controller, machine);
        MachineAssembly.start(player, pos, AssemblyIngredient.missing(structure, player.level(), pos),
                controller, machine);
        return true;
    }

    /** 旧版 {@code onPlayerTick :214-242}：每 tickBlock tick 放一块。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % Math.max(1, ModConfig.assemblyTickBlock()) != 0) {
            return;
        }
        for (MachineAssembly assembly : MachineAssemblyManager.ofPlayer(player.getUUID())) {
            if (!level.dimension().equals(assembly.dimension())) continue;
            int state = assembly.checkTarget(level);
            if (state == MachineAssembly.TARGET_PAUSED) continue;
            if (state == MachineAssembly.TARGET_INVALID) {
                MachineAssemblyManager.remove(level.dimension(), assembly.ctrlPos());
                player.displayClientMessage(Component.translatable(
                        "message.modular_machinery_reborn.assembly.cancelled")
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            boolean finished = assembly.tickPlace(level, player);
            // tickPlace validates again: direct callers cannot bypass the target check.
            if (assembly.isCancelled()) {
                MachineAssemblyManager.remove(level.dimension(), assembly.ctrlPos());
                player.displayClientMessage(Component.translatable(
                        "message.modular_machinery_reborn.assembly.cancelled")
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            if (finished && assembly.isCompleted()) {
                MachineAssemblyManager.remove(level.dimension(), assembly.ctrlPos());
                player.displayClientMessage(Component.translatable(
                        "message.modular_machinery_reborn.assembly.success")
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }
    }

    /** 旧版 {@code onPlayerLogOut :244-247}。 */
    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        MachineAssemblyManager.removeAllOf(event.getEntity().getUUID());
        LAST_REQUEST.remove(event.getEntity().getUUID());
    }
}
