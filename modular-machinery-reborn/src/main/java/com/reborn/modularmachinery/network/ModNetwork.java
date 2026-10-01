package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The project's first custom network channel.
 *
 * <h2>Why a packet is needed at all</h2>
 *
 * <p>The scoping document recommended the menu route instead (M6d row, §5.1), and everything it can carry
 * <b>does</b> travel through the menu here: the controller's ceiling and current value reach the screen as
 * {@code ContainerData} in {@code ParallelControllerMenu}, exactly like the machine controller's parallelism
 * rows added in 0.19.0.
 *
 * <p>The divergence is only in the <b>other</b> direction, and it is forced by the wire format of the vanilla
 * menu channel. {@code ServerboundContainerButtonClickPacket} — the only vanilla client → server hook a menu
 * offers — encodes <em>both</em> of its fields as a single byte:
 *
 * <pre>
 * ServerboundContainerButtonClickPacket(FriendlyByteBuf buf):
 *     readByte()   // container id
 *     readByte()   // button id
 * </pre>
 *
 * <p>verified with {@code javap -c -p} against {@code joined-1.20.1-20230612.114412-srg.jar}. A parallel
 * controller's ceiling is 512 by default and {@code max-parallelism} runs to 2048 and beyond, so the original's
 * "send the absolute value the player typed" ({@code GuiContainerParallelController.java:178-190}) cannot be
 * expressed in one signed byte. Sending deltas instead would need a multi-click encoding with server-side
 * reassembly state, which is strictly worse than one varint message.
 *
 * <p>So: <b>menu {@code ContainerData} for server → client, one packet for client → server</b>.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final int ID_PARALLEL_CONTROLLER_UPDATE = 0;
    /**
     * M6d-b's message. The protocol version deliberately stays {@code "1"}: nothing about the existing message
     * changed, and a message the other side does not know is only a problem for a mismatched pair of builds —
     * which Forge already refuses on the version string. Adding an id is the compatible direction.
     */
    private static final int ID_SMART_INTERFACE_UPDATE = 1;
    /**
     * 0.29.0's message: the construct tool's selection, server → client (the original's {@code PktSyncSelection}).
     * The protocol version stays {@code "1"} for the same reason the id above did — adding an id is the
     * compatible direction, and a mismatched pair of builds is refused on the version string anyway.
     */
    private static final int ID_SELECTION_SYNC = 2;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(ID_PARALLEL_CONTROLLER_UPDATE, ParallelControllerUpdatePacket.class,
                ParallelControllerUpdatePacket::encode,
                ParallelControllerUpdatePacket::new,
                ParallelControllerUpdatePacket::handle);
        CHANNEL.registerMessage(ID_SMART_INTERFACE_UPDATE, SmartInterfaceUpdatePacket.class,
                SmartInterfaceUpdatePacket::encode,
                SmartInterfaceUpdatePacket::new,
                SmartInterfaceUpdatePacket::handle);
        CHANNEL.registerMessage(ID_SELECTION_SYNC, SelectionSyncPacket.class,
                SelectionSyncPacket::encode,
                SelectionSyncPacket::new,
                SelectionSyncPacket::handle);
    }

    private ModNetwork() {
    }
}
