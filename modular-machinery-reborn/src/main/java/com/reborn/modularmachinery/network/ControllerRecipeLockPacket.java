package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.block.ControllerSecurity;
import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.menu.FactoryControllerMenu;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Sets the recipe for future batches through the player's open controller menu. */
public record ControllerRecipeLockPacket(int containerId, BlockPos position, @Nullable ResourceLocation recipe) {
    public ControllerRecipeLockPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBlockPos(), buffer.readBoolean() ? buffer.readResourceLocation() : null);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeBlockPos(position);
        buffer.writeBoolean(recipe != null);
        if (recipe != null) buffer.writeResourceLocation(recipe);
    }

    public static void handle(ControllerRecipeLockPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> packet.apply(ctx.getSender()));
        ctx.setPacketHandled(true);
    }

    private void apply(@Nullable ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        var menu = player.containerMenu;
        if (menu.containerId != containerId) return;
        MachineControllerBlockEntity controller;
        if (menu instanceof MachineControllerMenu machine) controller = machine.machine();
        else if (menu instanceof FactoryControllerMenu factory) controller = factory.machine();
        else return;
        if (!controller.getBlockPos().equals(position) || !menu.stillValid(player)) return;

        if (!player.mayBuild() || !ControllerSecurity.canBreak(ModConfig.enableSecuritySystem(),
                player.isCreative(), controller.getOwner(), player.getUUID())) {
            player.displayClientMessage(Component.translatable("gui.modular_machinery_reborn.recipe_lock.denied"), true);
            return;
        }
        if (!controller.setLockedRecipe(recipe)) {
            player.displayClientMessage(Component.translatable("gui.modular_machinery_reborn.recipe_lock.rejected"), true);
        }
    }
}
