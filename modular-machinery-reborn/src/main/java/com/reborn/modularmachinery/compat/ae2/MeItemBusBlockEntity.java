package com.reborn.modularmachinery.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import com.reborn.modularmachinery.block.HatchKind;
import com.reborn.modularmachinery.block.MeBusAccess;
import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import com.reborn.modularmachinery.block.MeItemBusLogic;
import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.menu.MeItemBusMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * C4（0.73.0）：AE2 ME 物品输入/输出总线的方块实体。
 *
 * <p>这里只做三件事：挂一个 AE2 受管节点、在服务端按固定间隔把内部槽与网络之间搬物品、
 * 把输入总线的 16 个配置槽存进 NBT。搬运的判定全部在
 * {@link MeItemBusLogic} 里（那个类不 import AE2，因此可以在离线复现器里直接测）。
 *
 * <p>旧版是 {@code github.kasuminova.mmce.common.tile.MEItemInputBus} /
 * {@code MEItemOutputBus}，基于 {@code AENetworkProxy}。1.20.1 的等价物是
 * {@link IManagedGridNode}：节点只在服务端创建，客户端只负责显示菜单里的同步值。
 */
public final class MeItemBusBlockEntity extends MachineHatchBlockEntity
        implements MeBusAccess, IInWorldGridNodeHost, IActionHost {

    private static final String TAG_CONFIG = "configInventory";
    private static final String TAG_NODE = "ae_node";
    /** C4-II（0.92.0）：统一总线流体半边的 NBT 键。 */
    private static final String TAG_FLUID_TANKS = "fluidTanks";
    private static final String TAG_FLUID_CONFIG = "fluidConfig";
    /** C4-II（0.94.0）：输入侧配置格数（物品 ghost 与流体标记共用这 16 格）。 */
    public static final int CONFIG_GRID = 16;

    private final IManagedGridNode mainNode;

    /** 输入总线才有；输出总线为 {@code null}。 */
    @Nullable
    private final ItemStackHandler config;

    /** C4-II：流体半边的内部罐（9 × 8000 mB，两种总线都有；所有者拍板并入现有总线，不另立方块）。 */
    private final com.reborn.modularmachinery.block.MeFluidTanks fluidTanks =
            new com.reborn.modularmachinery.block.MeFluidTanks();

    @Nullable private final com.reborn.modularmachinery.block.MeGasStorage gasTanks =
            net.minecraftforge.fml.ModList.get().isLoaded("mekanism")
                    ? new com.reborn.modularmachinery.compat.mekanism.MeGasTanks(this::markStorageChanged) : null;
    // Keep gas data intact if Mekanism is temporarily absent.
    private CompoundTag deferredGas = new CompoundTag();
    @Nullable private com.reborn.modularmachinery.block.MeGasStorage.Network gasNetwork;

    /**
     * C4-II（0.94.0 重构，照高版 AE2 接口）：输入总线的**流体标记**——与 16 个配置格一一对应，
     * 每条保存完整流体身份（含 NBT）和目标量。物品、流体和气体标记互斥。
     *
     * <p>维持语义随接口模型改为**按目标量**（不再是 0.92.0 的逐槽维持到满）：网络里取到目标量为止，
     * 多了送回；9 罐是共享池。{@code null} = 该格未标记流体。
     */
    @Nullable
    private final FluidStack[] fluidMarks;

    private final int[] failures;
    /** C4-II：流体半边的失败退避（与物品半边各自独立计数）。 */
    private final int[] fluidFailures;
    private int ticks;

    public MeItemBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ME_ITEM_BUS_ENTITY.get(), pos, state);

        ItemStackHandler itemHandler = itemHandler();
        this.failures = new int[itemHandler == null ? 0 : itemHandler.getSlots()];
        this.fluidFailures = new int[com.reborn.modularmachinery.block.MeFluidTanks.SLOTS];

        if (kind() == HatchKind.ME_ITEM_INPUT) {
            this.config = new ItemStackHandler(16) {
                @Override
                protected void onContentsChanged(int slot) {
                    MeItemBusBlockEntity.this.setChanged();
                    Arrays.fill(MeItemBusBlockEntity.this.failures, 0);
                }
            };
            this.fluidMarks = new FluidStack[CONFIG_GRID];
        } else {
            this.config = null;
            this.fluidMarks = null;
        }

        this.mainNode = GridHelper.createManagedNode(this, new IGridNodeListener<MeItemBusBlockEntity>() {
            @Override
            public void onSaveChanges(MeItemBusBlockEntity nodeOwner, IGridNode node) {
                nodeOwner.setChanged();
            }
        })
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .setInWorldNode(true)
                .setTagName(TAG_NODE)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(state.getBlock());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        this.mainNode.destroy();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.mainNode.destroy();
        super.onChunkUnloaded();
    }

    @Nullable
    @Override
    public ItemStackHandler configHandler() {
        return this.config;
    }

    @Override
    public boolean meOnline() {
        return this.mainNode.isActive() && this.mainNode.isOnline();
    }

    /** C4-II（0.93.0）：MeBusAccess 的流体半边——罐给界面显示，配置给 ghost 交互。 */
    @Override
    public com.reborn.modularmachinery.block.MeFluidTanks fluidTanks() {
        return this.fluidTanks;
    }

    @Override
    @Nullable
    public net.minecraft.world.item.ItemStack configItemAt(int slot) {
        if (this.config == null || slot < 0 || slot >= this.config.getSlots()) {
            return null;
        }
        return this.config.getStackInSlot(slot);
    }

    @Override
    @Nullable
    public Fluid fluidMarkAt(int slot) {
        FluidStack mark = fluidMark(slot);
        return mark.isEmpty() ? null : mark.getFluid();
    }

    @Override
    public int fluidMarkAmount(int slot) {
        return fluidMark(slot).getAmount();
    }

    @Override
    public FluidStack fluidMarkStackAt(int slot) {
        return fluidMark(slot).copy();
    }

    private FluidStack fluidMark(int slot) {
        if (this.fluidMarks == null || slot < 0 || slot >= this.fluidMarks.length || this.fluidMarks[slot] == null) {
            return FluidStack.EMPTY;
        }
        return this.fluidMarks[slot];
    }

    /** C4-II（0.94.0）：配置格写入总入口——物品 ghost / 清除，互斥于流体标记（流体侧见 setConfigFluid）。 */
    @Override
    public void setConfigItem(int slot, @Nullable net.minecraft.world.item.ItemStack ghost) {
        if (this.level == null || this.level.isClientSide || slot < 0 || slot >= CONFIG_GRID) {
            return;
        }
        // 0.94.4 修复：0.94.3 只拆了外层门卫，里面的条件仍是 `ghost != null &&` ——清除（null）
        // 依旧进不到流体侧，液体标记照旧清不掉（所有者复验报告）。正确的互斥语义是**对称的**：
        // 写物品 → 清流体；清空 → 物品与流体两侧都清。
        if (this.config != null) {
            this.config.setStackInSlot(slot, ghost == null ? net.minecraft.world.item.ItemStack.EMPTY : ghost);
        }
        if (this.fluidMarks != null) {
            this.fluidMarks[slot] = null;
        }
        if (this.gasTanks != null) this.gasTanks.setMark(slot, null, 0);
        this.afterConfigWrite(slot);
    }

    @Override
    public void setConfigFluid(int slot, @Nullable Fluid fluid, int amount) {
        setConfigFluidStack(slot, fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, Math.max(1, amount)));
    }

    @Override
    public void setConfigFluidStack(int slot, FluidStack stack) {
        if (this.level == null || this.level.isClientSide || this.fluidMarks == null
                || slot < 0 || slot >= CONFIG_GRID) {
            return;
        }
        this.fluidMarks[slot] = stack.isEmpty() ? null
                : new FluidStack(stack, Math.min(stack.getAmount(), this.fluidTanks.totalCapacity()));
        if (this.gasTanks != null) this.gasTanks.setMark(slot, null, 0);
        if (!stack.isEmpty() && this.config != null) {
            this.config.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
        this.afterConfigWrite(slot);
    }

    @Override public com.reborn.modularmachinery.block.MeGasStorage gasTanks() { return this.gasTanks; }
    @Override public boolean gasNetworkAvailable() { return net.minecraftforge.fml.ModList.get().isLoaded("appmek"); }
    @Override public void setConfigGas(int slot, @Nullable net.minecraft.resources.ResourceLocation gas, int amount) {
        if (this.level == null || this.level.isClientSide || this.config == null || this.gasTanks == null
                || slot < 0 || slot >= CONFIG_GRID || !this.gasTanks.setMark(slot, gas, amount)) return;
        this.config.setStackInSlot(slot, ItemStack.EMPTY);
        this.fluidMarks[slot] = null;
        this.afterConfigWrite(slot);
    }
    @Override public com.reborn.modularmachinery.block.MeGasStorage.Entry encodedGas(ItemStack stack) {
        return net.minecraftforge.fml.ModList.get().isLoaded("appmek")
                ? AppMekGasBridge.encodedGas(stack) : null;
    }

    /** 写配置后的公共动作：清该槽物品退避与流体退避、置脏、方块更新（客户端镜像随之刷新）。 */
    private void afterConfigWrite(int slot) {
        if (this.failures.length > slot) {
            this.failures[slot] = 0;
        }
        if (this.fluidFailures.length > slot) {
            this.fluidFailures[slot] = 0;
        }
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
    }


    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        // 旧版 AENetworkProxy 用 AECableType.SMART；默认的 GLASS 会让线缆连接表现不同。
        return AECableType.SMART;
    }

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    /**
     * 0.97.0：与供应器同修——afterConfigWrite/setFluidConfig 的 sendBlockUpdated 依赖本方法
     * 才能把配置镜像发到客户端；缺它 = 配置槽/流体标记在**客户端**永远不显示（服务端行为正常，
     * 所以「清除生效」但界面看不到）。
     */
    @Override
    @Nullable
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.config != null) {
            tag.put(TAG_CONFIG, this.config.serializeNBT());
        }
        // C4-II：流体罐进存档也进 update tag（客户端镜像留给界面片用）。
        tag.put(TAG_FLUID_TANKS, this.fluidTanks.serializeNBT());
        tag.put("meGas", this.gasTanks == null ? this.deferredGas.copy() : this.gasTanks.save());
        if (this.fluidMarks != null) {
            ListTag list = new ListTag();
            for (int i = 0; i < this.fluidMarks.length; i++) {
                CompoundTag entry = new CompoundTag();
                FluidStack mark = fluidMark(i);
                if (!mark.isEmpty()) {
                    entry.putString("fluid", BuiltInRegistries.FLUID.getKey(mark.getFluid()).toString());
                    entry.putInt("amount", mark.getAmount());
                    if (mark.hasTag()) entry.put("tag", mark.getTag().copy());
                }
                list.add(entry);
            }
            tag.put(TAG_FLUID_CONFIG, list);
        }
        this.mainNode.saveToNBT(tag);
    }

    /**
     * 0.73.3：chunk update tag 只需要同步配置/库存，不需要 AE2 节点数据。
     *
     * <p>父类的 {@code getUpdateTag()} 会走 {@code saveWithoutMetadata()}，进而调用本类的
     * {@code saveAdditional}，把 {@code mainNode.saveToNBT} 写进去。客户端节点由 {@code onLoad()}
     * 创建，这份数据本来就不会被读取（见 {@link #load(CompoundTag)}），不发可以减少每个总线的包体。
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(TAG_NODE);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (this.config != null && tag.contains(TAG_CONFIG)) {
            this.config.deserializeNBT(tag.getCompound(TAG_CONFIG));
        }
        // C4-II：流体罐与配置。解析不到的流体按「未配置」处理（坏数据包不该让总线拒绝载入）。
        if (tag.contains(TAG_FLUID_TANKS)) {
            this.fluidTanks.deserializeNBT(tag.getCompound(TAG_FLUID_TANKS));
        }
        this.deferredGas = tag.getCompound("meGas").copy();
        if (this.gasTanks != null) this.gasTanks.load(this.deferredGas);
        if (this.fluidMarks != null) {
            Arrays.fill(this.fluidMarks, null);
            ListTag list = tag.getList(TAG_FLUID_CONFIG, Tag.TAG_COMPOUND);
            for (int i = 0; i < this.fluidMarks.length && i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("fluid"));
                Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id);
                if (fluid == Fluids.EMPTY) continue;
                int amount = Math.max(1, Math.min(entry.getInt("amount"), this.fluidTanks.totalCapacity()));
                this.fluidMarks[i] = new FluidStack(fluid, amount,
                        entry.contains("tag", Tag.TAG_COMPOUND) ? entry.getCompound("tag") : null);
            }
        }
        // 0.73.3: 客户端收到 chunk update tag 时，BlockEntity.onLoad 已经 create 了 node；
        // AE2 的 ManagedGridNode.loadFromNBT 在「节点已初始化」时会抛
        // IllegalStateException: The node has already been initialized. Initialization data cannot be changed anymore.
        // 这个异常会让 LevelChunk 处理 chunk 包时中断，表现为整个区块不可见。
        // 节点数据只在服务端恢复；客户端节点由 onLoad 创建，不需要读 NBT。
        if (this.level == null || !this.level.isClientSide) {
            this.mainNode.loadFromNBT(tag);
        }
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,
            net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new MeItemBusMenu(id, inventory, this);
    }

    @Override
    public void serverTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.ticks++;
        if (!this.mainNode.isActive() || !this.mainNode.isOnline()) {
            return;
        }

        boolean input = kind() == HatchKind.ME_ITEM_INPUT;
        int interval = input ? 10 : 5;
        if (this.ticks % interval != 0) {
            return;
        }
        boolean fullCheck = this.ticks % 100 == 0;
        ItemStackHandler inventory = itemHandler();
        if (inventory == null) {
            return;
        }

        boolean moved;
        if (input && this.config != null) {
            moved = MeItemBusLogic.tickInput(inventory, this.config, new NetworkStorage(), this.failures, fullCheck);
        } else {
            moved = MeItemBusLogic.tickOutput(inventory, new NetworkStorage(), this.failures, fullCheck);
        }

        // C4-II（0.92.0）：流体半边与物品半边同一节奏、同一失败退避结构，各自独立计数。
        boolean fluidMoved;
        if (input && this.fluidMarks != null) {
            fluidMoved = com.reborn.modularmachinery.block.MeFluidBusLogic.tickInput(
                    this.fluidTanks, this.fluidMarks, new FluidNetworkStorage(),
                    this.fluidFailures, fullCheck);
        } else {
            fluidMoved = com.reborn.modularmachinery.block.MeFluidBusLogic.tickOutput(
                    this.fluidTanks, new FluidNetworkStorage(), this.fluidFailures, fullCheck);
        }
        boolean gasMoved = false;
        if (this.gasTanks != null && net.minecraftforge.fml.ModList.get().isLoaded("appmek")) {
            if (this.gasNetwork == null) this.gasNetwork = AppMekGasBridge.network(this.mainNode, this);
            gasMoved = this.gasTanks.tick(input, this.gasNetwork);
        }
        if (moved || fluidMoved || gasMoved) {
            this.setChanged();
        }
    }

    /**
     * {@link MeItemBusLogic.Storage} 的 AE2 适配器。
     *
     * <p>每次调用都从 {@code mainNode} 重新取 grid：节点可能在运行中掉线/重连，
     * 把 grid 缓存进字段会让「掉线后仍在往旧网络塞物品」成为可能。
     */
    private final class NetworkStorage implements MeItemBusLogic.Storage {

        @Override
        public int insert(ItemStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                return 0;
            }
            long moved = StorageHelper.poweredInsert(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(),
                    key,
                    stack.getCount(),
                    IActionSource.ofMachine(MeItemBusBlockEntity.this),
                    Actionable.MODULATE);
            return clamp(moved, stack.getCount());
        }

        @Override
        public int extract(ItemStack prototype, int amount) {
            if (prototype.isEmpty() || amount <= 0) {
                return 0;
            }
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }
            AEItemKey key = AEItemKey.of(prototype);
            if (key == null) {
                return 0;
            }
            long moved = StorageHelper.poweredExtraction(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(),
                    key,
                    amount,
                    IActionSource.ofMachine(MeItemBusBlockEntity.this),
                    Actionable.MODULATE);
            return clamp(moved, amount);
        }
    }

    /**
     * C4-II（0.92.0）：把流体罐作为**机器侧的流体端口**交给端口收集——统一总线的物品半边本来就是
     * ITEM 家族的物品端口，这里补上流体端口那一半（所有者拍板：并入现有总线，不另立方块）。
     */
    @Override
    public net.minecraftforge.fluids.capability.IFluidHandler extraFluidPort() {
        return this.fluidTanks;
    }

    /** {@link MeFluidBusLogic.Storage} 的 AE2 流体适配器：{@code AEFluidKey} 换 {@code AEItemKey}，其余照物品半边。 */
    private final class FluidNetworkStorage implements com.reborn.modularmachinery.block.MeFluidBusLogic.Storage {

        @Override
        public int insert(net.minecraftforge.fluids.FluidStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }
            AEFluidKey key = AEFluidKey.of(stack);
            if (key == null) {
                return 0;
            }
            long moved = StorageHelper.poweredInsert(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(),
                    key,
                    stack.getAmount(),
                    IActionSource.ofMachine(MeItemBusBlockEntity.this),
                    Actionable.MODULATE);
            return moved <= 0 ? 0 : (int) Math.min(moved, stack.getAmount());
        }

        @Override
        public int extract(FluidStack prototype, int amount) {
            if (prototype.isEmpty() || amount <= 0) {
                return 0;
            }
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }
            AEFluidKey key = AEFluidKey.of(prototype);
            if (key == null) {
                return 0;
            }
            long moved = StorageHelper.poweredExtraction(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(),
                    key,
                    amount,
                    IActionSource.ofMachine(MeItemBusBlockEntity.this),
                    Actionable.MODULATE);
            return moved <= 0 ? 0 : (int) Math.min(moved, amount);
        }
    }

    private static int clamp(long moved, int requested) {
        if (moved <= 0) {
            return 0;
        }
        return (int) Math.min(moved, requested);
    }
}
