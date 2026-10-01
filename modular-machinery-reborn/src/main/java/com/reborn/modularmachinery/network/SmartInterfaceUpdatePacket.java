package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One "set this machine's smart data interface value to this" message — the counterpart of the original's
 * {@code PktSmartInterfaceUpdate} (60 lines).
 *
 * <h2>Why the packet survives the merge</h2>
 *
 * <p>The design document's "明确不做" section rules out porting {@code PktSmartInterfaceUpdate} <b>as a packet of
 * its own class hierarchy</b>: the write direction now targets the controller's own block entity, and reading
 * goes through the controller's existing menu. It does not rule out a message: the value a player types is a
 * {@code float} plus a type name, and the only vanilla client → server hook a menu offers
 * ({@code ServerboundContainerButtonClickPacket}) encodes both of its fields as a single signed byte
 * ({@code readByte}/{@code readByte}, verified with {@code javap -c -p} — see {@link ModNetwork}). A type name
 * is a string, so no menu button can carry it however the value were encoded.
 *
 * <p>So the shape follows the original's own: a small message carrying the type and the new value, applied to
 * whatever controller the sender has open, with the server re-checking both the menu and the declared type
 * ({@code PktSmartInterfaceUpdate.java:38-47} did the former). The difference is only what the server does with
 * it: the original created a {@code SmartInterfaceData} bound to a controller position and let the separate
 * block store it; here the controller stores it directly.
 */
public final class SmartInterfaceUpdatePacket {

    private final String type;
    private final float value;

    public SmartInterfaceUpdatePacket(String type, float value) {
        this.type = type;
        this.value = value;
    }

    public SmartInterfaceUpdatePacket(FriendlyByteBuf buf) {
        this.type = buf.readUtf();
        this.value = buf.readFloat();
    }

    public String type() {
        return this.type;
    }

    public float value() {
        return this.value;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.type);
        // The original wrote the value as a float (`SmartInterfaceData#serialize` -> setFloat). Kept as a float
        // rather than an int so a recipe may compare a fractional value.
        buf.writeFloat(this.value);
    }

    public static void handle(SmartInterfaceUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> apply(context.getSender(), message.type, message.value));
        context.setPacketHandled(true);
    }

    private static void apply(Player sender, String type, float value) {
        if (!(sender instanceof ServerPlayer player)) {
            return;
        }
        // The original's guard: only the container the player actually has open may be written to.
        if (!(player.containerMenu instanceof MachineControllerMenu menu)) {
            return;
        }
        MachineControllerBlockEntity controller = menu.machine();
        if (player.level().getBlockEntity(controller.getBlockPos()) != controller || !menu.stillValid(player)) {
            return;
        }
        // setSmartInterfaceValue refuses a type the formed machine does not declare, so a hand-written packet
        // cannot plant a value that no requirement could ever read.
        controller.setSmartInterfaceValue(type, value);
    }
}
