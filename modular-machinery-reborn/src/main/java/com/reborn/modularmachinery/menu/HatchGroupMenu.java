package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * 「输入组 ID 配置」窗口的容器（A2）。
 *
 * <p>旧版对应 {@code ContainerGroupInputConfig}——同样没有槽位，只有两个数：玩家是否启用了组隔离、
 * 以及组号。这两个数走 {@link ContainerData} 从服务端送到客户端，和并行控制器窗口用的是同一条路。
 *
 * <p><b>写入方向只有一条</b>：客户端改值走 {@code HatchGroupUpdatePacket}，因为原版菜单按钮通道
 * 一个按钮只有一个字节，装不下组号（详见 {@code ModNetwork} 的类注释）。所以这里的
 * {@link ContainerData#set} 在服务端是空操作——它只在客户端收到同步包时被调用。
 *
 * <p><b>客户端为什么不能直接读仓口</b>：组号与开关不在方块实体的 update tag 里（{@code setChanged()}
 * 只存档、不广播），客户端那份 {@code MachineHatchBlockEntity} 的字段永远是旧值；而
 * {@link ContainerData} 的同步包正是写到本菜单的 {@code set} 上。所以两侧各读各的：服务端读仓口
 * （权威值），客户端读 {@code received} 镜像（同步包写进来）。若两端都读仓口，屏上显示的就是客户端
 * 的陈旧值，{@code set} 里的值无处可去——这正是 A2 第一次实现时的缺陷。
 */
public final class HatchGroupMenu extends AbstractContainerMenu {

    /** 窗口标题，旧版 {@code gui.groupinputconfig.title}。 */
    public static final String TITLE_KEY = "gui.modular_machinery_reborn.groupinputconfig.title";

    /** 数据槽 0：是否启用组隔离（0/1）。 */
    public static final int DATA_GROUP_INPUT = 0;
    /** 数据槽 1：组号。 */
    public static final int DATA_GROUP_ID = 1;
    /** 数据槽个数（两个）。 */
    private static final int DATA_COUNT = 2;

    private final MachineHatchBlockEntity hatch;

    /** 客户端镜像：同步包到达时由 {@code data.set} 写进来，{@code data.get} 在客户端只读这里。 */
    private final int[] received = new int[DATA_COUNT];

    /** 菜单建在哪一侧。客户端不碰 {@link #hatch}（可能为空，也可能落后）。 */
    private final boolean clientSide;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            if (clientSide) {
                return index >= 0 && index < received.length ? received[index] : 0;
            }
            return switch (index) {
                case DATA_GROUP_INPUT -> hatch.isGroupInput() ? 1 : 0;
                case DATA_GROUP_ID -> hatch.groupId();
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            // 见类注释：客户端收到同步包时写镜像；服务端这一侧不应被调用。
            if (clientSide && index >= 0 && index < received.length) {
                received[index] = value;
            }
        }

        @Override public int getCount() {
            return DATA_COUNT;
        }
    };

    public HatchGroupMenu(int id, Inventory inventory, MachineHatchBlockEntity hatch) {
        super(ModMenus.HATCH_GROUP.get(), id);
        this.hatch = hatch;
        this.clientSide = inventory.player.level().isClientSide();
        addDataSlots(new IntContainerData(this.data, DATA_GROUP_ID));
    }

    public HatchGroupMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, (MachineHatchBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public MachineHatchBlockEntity hatch() {
        return this.hatch;
    }

    public boolean groupInput() {
        return this.data.get(DATA_GROUP_INPUT) != 0;
    }

    public int groupId() {
        return Math.max(0, this.data.get(DATA_GROUP_ID));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return BlockEntityMenuAccess.stillValid(player, hatch);
    }
}
