package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.client.ClientSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One "this is your selection now" message — the counterpart of the original's {@code PktSyncSelection}
 * (64 lines), which carried a count and then three ints per position ({@code :39-56}) and was sent on every
 * toggle, including the empty list after a finalize.
 *
 * <h2>Why this packet is unavoidable</h2>
 *
 * <p>The selection lives on the <b>server</b> (the tool is a server-side interaction: {@code ItemConstructTool}
 * gates on {@code !worldIn.isRemote}), and the highlight is drawn on the <b>client</b>, so the client has to be
 * told. The original's own design is the same one packet, one direction, no acknowledgement — see
 * {@link ModNetwork} for why a message rather than a menu channel is the project's rule for anything a menu
 * cannot carry.
 *
 * <p>Two small differences from the original's wire format, neither observable by a client of this build: the
 * count is still an {@code int} and each position is still three {@code int}s — but the positions are read with
 * {@code readBlockPos} nowhere here on purpose. {@code FriendlyByteBuf.writeBlockPos} packs the three coordinates
 * into <b>one long</b>, and that packing gives y only 12 bits: a position above 2047 would come back changed, and
 * a selection is not a place where a silent truncation is acceptable. The original's three ints are kept.
 *
 * <p>On the reading side the initial capacity is capped, because a count is attacker-controlled data and
 * {@code new ArrayList<>(count)} on a hand-written packet is a way to ask the client for all its memory.
 */
public final class SelectionSyncPacket {

    /** The most positions a single message may announce, for the initial allocation only. */
    private static final int INITIAL_CAPACITY_CAP = 4096;

    private final List<BlockPos> positions;

    public SelectionSyncPacket(List<BlockPos> positions) {
        this.positions = List.copyOf(positions);
    }

    public SelectionSyncPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BlockPos> read = new ArrayList<>(Math.max(0, Math.min(count, INITIAL_CAPACITY_CAP)));
        for (int i = 0; i < count; i++) {
            read.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        this.positions = List.copyOf(read);
    }

    public List<BlockPos> positions() {
        return this.positions;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.positions.size());
        for (BlockPos pos : this.positions) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
    }

    public static void handle(SelectionSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Client-only, through DistExecutor so no common class ever names the client state directly.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSelection.set(message.positions())));
        context.setPacketHandled(true);
    }

    /** Sends the player's whole selection, which is what the original sent after every click. */
    public static void sendTo(ServerPlayer player, List<BlockPos> positions) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelectionSyncPacket(positions));
    }
}
