package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.block.MeBusAccess;
import com.reborn.modularmachinery.compat.ae2.MeItemBusBlockEntity;
import com.reborn.modularmachinery.menu.MeItemBusMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * C4-II（0.94.0，照高版 AE2 接口的配置格模型）：在输入总线界面上写一个**配置格**——
 * 物品 ghost / 流体标记 / 清空（客户端 → 服务端）。一格要么物品要么流体，服务端写入时互斥。
 *
 * <p>服务端验证：发件人正开着这台输入总线的界面、槽号 0..15、物品/流体真实存在
 * （流体目标量夹到全池容量）。
 *
 * <p><b>物品格的身份来自服务端光标栈，不来自包里的注册名。</b>P02-01（0.121.46）：此前的 ITEM 分支用
 * {@code new ItemStack(id)} 重建物品，于是自定义名、附魔、药水等 NBT 全部丢失；而下游
 * {@link com.reborn.modularmachinery.block.MeItemBusLogic} 用 {@code ItemStack.isSameItemSameTags}
 * 维持目标量、AE2 的 {@code AEItemKey} 也按标签认身份，配置会指向错误的变体。现在先核对请求的 id 与
 * <strong>服务端菜单光标</strong>（{@link AbstractContainerMenu#getCarried()}，服务端权威）是同一物品，
 * 再从光标栈<strong>深拷贝</strong>一份设为目标量；id 不一致、光标为空或菜单已变一律拒绝本次写入并保留
 * 旧配置，绝不回退成无 NBT 的「近似物品」，也不移动或扣减光标/库存。
 *
 * <p>写回走 {@link MeBusAccess#setConfigItem}/{@link MeBusAccess#setConfigFluid}，
 * 由实现负责清退避/置脏/方块更新，客户端随 update tag 镜像刷新。滚轮调整数量走独立的菜单动作
 * （{@link com.reborn.modularmachinery.menu.MeConfigAmount}），不要求每次调数量都手持物品。
 */
public final class MeFluidConfigPacket {

    /** 该格写入什么。 */
    public enum Mode {
        CLEAR, ITEM, FLUID, GAS
    }

    private final int slot;
    private final Mode mode;
    @Nullable
    private final ResourceLocation id;
    private final int amount;

    public MeFluidConfigPacket(int slot, Mode mode, @Nullable ResourceLocation id, int amount) {
        this.slot = slot;
        this.mode = mode;
        this.id = id;
        this.amount = amount;
    }

    public MeFluidConfigPacket(FriendlyByteBuf buf) {
        this.slot = buf.readByte();
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Mode.values().length) throw new IllegalArgumentException("Unknown ME config mode " + ordinal);
        this.mode = Mode.values()[ordinal];
        this.id = buf.readBoolean() ? buf.readResourceLocation() : null;
        this.amount = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(this.slot);
        buf.writeByte(this.mode.ordinal());
        buf.writeBoolean(this.id != null);
        if (this.id != null) {
            buf.writeResourceLocation(this.id);
        }
        buf.writeVarInt(this.amount);
    }

    public static void handle(MeFluidConfigPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!(menu instanceof MeItemBusMenu busMenu) || !busMenu.isInput()) {
                return;
            }
            if (!busMenu.stillValid(player) || busMenu.hatch().getLevel() != player.level()) return;
            if (!(busMenu.hatch() instanceof MeBusAccess bus)) {
                return;
            }
            if (message.slot < 0 || message.slot >= MeItemBusBlockEntity.CONFIG_GRID) {
                return;
            }
            switch (message.mode) {
                case CLEAR -> bus.setConfigItem(message.slot, null);
                case ITEM -> {
                    ItemStack ghost = cursorCopyFor(busMenu, message.id, message.amount);
                    if (ghost == null) {
                        return;
                    }
                    bus.setConfigItem(message.slot, ghost);
                }
                case FLUID -> {
                    if (message.id == null) {
                        return;
                    }
                    Fluid fluid = BuiltInRegistries.FLUID.get(message.id);
                    if (fluid == Fluids.EMPTY) {
                        return;
                    }
                    int amount = Math.max(1, Math.min(message.amount,
                            com.reborn.modularmachinery.block.MeFluidTanks.SLOTS
                                    * com.reborn.modularmachinery.block.MeFluidTanks.CAPACITY));
                    bus.setConfigFluid(message.slot, fluid, amount);
                }
                case GAS -> {
                    if (message.id == null || bus.gasTanks() == null) return;
                    bus.setConfigGas(message.slot, message.id, Math.max(1, Math.min(message.amount,
                            com.reborn.modularmachinery.block.MeGasStorage.SLOTS
                                    * com.reborn.modularmachinery.block.MeGasStorage.CAPACITY)));
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 把「请求的物品 id + 数量」落到服务端光标栈的一份深拷贝上；任何不一致都返回 {@code null}（拒绝写入）。
     *
     * <p>为什么以光标栈为准：P02-01 的客户端入口只有 {@code MeItemBusScreen.mouseClicked} 一处，它发的
     * {@code carried} 就是 {@code menu.getCarried()}；服务端在同一菜单包里也持有同一份权威栈。包里只带
     * id/count（协议 12 未改），所以身份与全部 NBT 必须从服务端这一份取——只发 id 再重建，等于把
     * 「带自定义名的机器外壳」变成「同名白板」，下游按标签匹配就会去请求错误的变体。
     *
     * <p>{@code copyWithCount} 复制标签（1.20.1 的 {@code copy()} 会 {@code tag.copy()}），因此之后玩家
     * 改动手上那叠物品不会连带改配置。
     */
    @Nullable
    private static ItemStack cursorCopyFor(AbstractContainerMenu menu, @Nullable ResourceLocation requested,
                                           int amount) {
        if (requested == null) {
            return null;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()
                || !requested.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        carried.getItem()))) {
            return null;
        }
        int count = Math.max(1, Math.min(amount, carried.getMaxStackSize()));
        return carried.copyWithCount(count);
    }

    /** 手持物品 → 物品 ghost（数量 = 手持数，右键 = 1）。 */
    public static void sendItem(int slot, ItemStack carried, boolean single) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(carried.getItem());
        int count = single ? 1 : carried.getCount();
        ModNetwork.CHANNEL.sendToServer(new MeFluidConfigPacket(slot, Mode.ITEM, id, count));
    }

    /** 手持流体容器 → 流体标记（目标量 = 容器量）。 */
    public static void sendFluid(int slot, Fluid fluid, int amount) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        ModNetwork.CHANNEL.sendToServer(new MeFluidConfigPacket(slot, Mode.FLUID, id, amount));
    }

    public static void sendClear(int slot) {
        ModNetwork.CHANNEL.sendToServer(new MeFluidConfigPacket(slot, Mode.CLEAR, null, 0));
    }

    public static void sendGas(int slot, ResourceLocation gas, long amount) {
        int target = (int) Math.max(1, Math.min(amount,
                com.reborn.modularmachinery.block.MeGasStorage.SLOTS * com.reborn.modularmachinery.block.MeGasStorage.CAPACITY));
        ModNetwork.CHANNEL.sendToServer(new MeFluidConfigPacket(slot, Mode.GAS, gas, target));
    }
}
