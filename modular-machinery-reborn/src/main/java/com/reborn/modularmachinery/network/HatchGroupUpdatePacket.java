package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import com.reborn.modularmachinery.menu.HatchGroupMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 「把这个仓口的组配置设成这样」——旧版 {@code PktGroupInputConfig} 的对应物。
 *
 * <p>协议13明确区分只改组号、只改开关及显式同时修改两项，不能把另一项的旧菜单镜像回写。
 * 服务端像旧版一样只认玩家**当前打开的**
 * 那个窗口（{@code PktGroupInputConfig.java:44-56}），并额外核对方块实体还是同一个、以及这个仓口
 * 种类真能分组。
 */
public final class HatchGroupUpdatePacket {

    private static final int GROUP_ID = 1;
    private static final int ENABLED = 2;
    private static final int BOTH = GROUP_ID | ENABLED;
    private final int operation;

    private final int groupId;
    private final boolean groupInput;

    public HatchGroupUpdatePacket(int groupId, boolean groupInput) {
        this(BOTH, groupId, groupInput);
    }

    private HatchGroupUpdatePacket(int operation, int groupId, boolean groupInput) {
        if (operation < GROUP_ID || operation > BOTH || ((operation & GROUP_ID) != 0 && groupId < 0)) {
            throw new IllegalArgumentException("Invalid hatch group operation");
        }
        this.operation = operation;
        this.groupId = groupId;
        this.groupInput = groupInput;
    }

    public HatchGroupUpdatePacket(FriendlyByteBuf buf) {
        this(buf.readUnsignedByte(), buf);
    }

    private HatchGroupUpdatePacket(int operation, FriendlyByteBuf buf) {
        this(operation, (operation & GROUP_ID) != 0 ? buf.readVarInt() : 0,
                (operation & ENABLED) != 0 && buf.readBoolean());
    }

    public static HatchGroupUpdatePacket setGroupId(int groupId) {
        return new HatchGroupUpdatePacket(GROUP_ID, groupId, false);
    }

    public static HatchGroupUpdatePacket setEnabled(boolean enabled) {
        return new HatchGroupUpdatePacket(ENABLED, 0, enabled);
    }

    public int groupId() {
        return this.groupId;
    }

    public boolean groupInput() {
        return this.groupInput;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(this.operation);
        if ((this.operation & GROUP_ID) != 0) buf.writeVarInt(this.groupId);
        if ((this.operation & ENABLED) != 0) buf.writeBoolean(this.groupInput);
    }

    public static void handle(HatchGroupUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> apply(context.getSender(), message));
        context.setPacketHandled(true);
    }

    private static void apply(Player sender, HatchGroupUpdatePacket message) {
        if (!(sender instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof HatchGroupMenu menu)) {
            return;
        }
        MachineHatchBlockEntity hatch = menu.hatch();
        if (hatch == null || player.level().getBlockEntity(hatch.getBlockPos()) != hatch
                || !menu.stillValid(player)
                || !hatch.canGroupInput()) {
            return;
        }
        hatch.setGroup((message.operation & ENABLED) != 0 ? message.groupInput : hatch.isGroupInput(),
                (message.operation & GROUP_ID) != 0 ? message.groupId : hatch.groupId());
    }
}
