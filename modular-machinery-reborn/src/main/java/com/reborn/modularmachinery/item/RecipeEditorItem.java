package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.authoring.RecipeEditorService;
import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

/** Dedicated creator tool. The service remains the server-side permission boundary. */
public final class RecipeEditorItem extends Item {
    public RecipeEditorItem(Properties properties) { super(properties); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useOn(context); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isCreative() || !player.hasPermissions(2)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            var entity = context.getLevel().getBlockEntity(context.getClickedPos());
            var machine = entity instanceof MachineControllerBlockEntity controller ? controller.previewMachineId() : null;
            RecipeEditorService.open(server, machine == null ? "" : machine.toString());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isCreative() || !player.hasPermissions(2)) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player instanceof ServerPlayer server) RecipeEditorService.open(server, "");
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("editor.modular_machinery_reborn.toolItemHelp").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("editor.modular_machinery_reborn.permission").withStyle(ChatFormatting.DARK_GRAY));
    }
}
