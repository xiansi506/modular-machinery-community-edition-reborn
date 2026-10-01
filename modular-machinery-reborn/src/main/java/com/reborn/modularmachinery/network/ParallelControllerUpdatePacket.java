package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.block.ParallelControllerBlockEntity;
import com.reborn.modularmachinery.menu.ParallelControllerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One "set my controller's parallelism to this" message — the counterpart of the original's
 * {@code PktParallelControllerUpdate}.
 *
 * <p>It carries the value and nothing else: like the original, the message is applied to whatever parallel
 * controller the sender currently has open, and the server re-validates
 * ({@code PktParallelControllerUpdate.java:38-47}).
 *
 * <p>It lives in its own file rather than nested in {@link ModNetwork} so that it can be encoded and decoded
 * without touching the channel, which the offline acceptance harness does.
 */
public final class ParallelControllerUpdatePacket {

    private final int newParallelism;

    public ParallelControllerUpdatePacket(int newParallelism) {
        this.newParallelism = newParallelism;
    }

    public ParallelControllerUpdatePacket(FriendlyByteBuf buf) {
        this.newParallelism = buf.readVarInt();
    }

    public int newParallelism() {
        return this.newParallelism;
    }

    public void encode(FriendlyByteBuf buf) {
        // The original wrote a fixed-width int; a varint says the same thing in fewer bytes and, unlike the
        // vanilla menu-button channel (a single signed byte), has no small range limit.
        buf.writeVarInt(this.newParallelism);
    }

    public static void handle(ParallelControllerUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> apply(context.getSender(), message.newParallelism));
        context.setPacketHandled(true);
    }

    private static void apply(Player sender, int newParallelism) {
        if (!(sender instanceof ServerPlayer player)) {
            return;
        }
        // The original's guard: only the container the player actually has open may be written to.
        if (!(player.containerMenu instanceof ParallelControllerMenu menu)) {
            return;
        }
        ParallelControllerBlockEntity controller = menu.controller();
        if (player.level().getBlockEntity(controller.getBlockPos()) != controller || !menu.stillValid(player)) {
            return;
        }
        // setParallelism clamps to [0, maxParallelism], so every value the screen can produce lands exactly where
        // the original's explicit range test would have put it.
        controller.setParallelism(newParallelism);
    }
}
