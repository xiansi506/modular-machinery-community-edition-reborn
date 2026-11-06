package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.config.HatchConfig;
import com.reborn.modularmachinery.energy.*;
import com.reborn.modularmachinery.compat.energy.OptionalEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Storage behind one hatch block.
 *
 * <p>This is where M3 puts the port capability layer. In the original mod the <b>hatches</b> owned the storage
 * and exposed the Forge capabilities, while the controller exposed none — a machine's inventory, tank and
 * energy buffer were simply the sum of its hatches. That is what this reproduces, and it is why an item bus of
 * tier {@code ludicrous} really does hold 32 slots and a {@code vacuum} fluid hatch really does hold 32,000 mB.
 *
 * <p>One block entity type serves all six families: the family and tier are read from the blockstate, which is
 * possible because each family is a single block with a {@code size} property. Capacity comes from
 * {@link HatchTier}, which carries the original {@code ItemBusSize} / {@code FluidHatchSize} /
 * {@code EnergyHatchData} numbers.
 */
public class MachineHatchBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    private static final String TAG_ITEMS = "items";
    private static final String TAG_TANK = "tank";
    private static final String TAG_ENERGY = "energy";
    /**
     * 机器外衣的染色（波 6 · {@code color}）。
     *
     * <p>语义照旧版：{@code TileColorableMachineComponent} 把颜色存在自己的状态里，
     * 由控制器在成型时推下来（{@code TileMultiblockMachineController#distributeCasingColor}）。
     * 本版把它放进方块实体的存档、**随既有的方块实体同步走到客户端**——因为客户端要靠它决定渲染时的
     * tint 颜色，而颜色是「这台机器实例的状态」，不是每 tick 变化的量（所以不走菜单数据槽）。
     */
    private static final String TAG_COLOR = "machineColor";

    /** A2：输入组号（仅物品输入与流体输入仓保存，见 {@link HatchKind#canGroupInput()}）。 */
    private static final String TAG_GROUP_ID = "groupId";

    /** A2：是否启用组隔离。旧版同名字段（{@code TileColorableMachineComponent#isGroupInput}）。 */
    private static final String TAG_GROUP_INPUT = "isGroupInput";

    private final HatchKind kind;
    private final HatchTier tier;

    /**
     * 机器外衣的染色；{@code null} 表示**从来没被设过**（= 不染色）。
     *
     * <p>为什么不直接用「白色 = 不染色」：白（{@code FFFFFF}）是一个**合法**的颜色值，
     * 包作者可能就想要白色。用 null 区分「没设过」与「设成了白」，才不会有歧义。
     */
    @Nullable private Integer machineColor;

    @Nullable private final ItemStackHandler items;
    @Nullable private final FluidTank tank;
    @Nullable private final EnergyStorage energy;

    /**
     * A4：这个仓口建成时配置给出的 FE/t。Forge 的 {@link EnergyStorage} 不暴露 {@code maxExtract} 的读法，
     * 而 {@code HatchConfig} 在改配置后可能与已建好的方块不同，所以把当时的值留在这里。
     */
    private final long energyTransfer;

    private LazyOptional<ItemStackHandler> itemCapability;
    private LazyOptional<FluidTank> fluidCapability;
    private LazyOptional<IEnergyStorage> energyCapability;
    private boolean capsValid = true;

    @Nullable private OptionalEnergy.Handle optionalEnergy;

    /** A1（0.63.9）：自动交换的节流状态，语义照旧版 {@code TileItemBus}。 */
    private int ticksExisted;
    private int successCounter;
    private boolean inventoryChanged;
    /** 同一游戏 tick 只跑一次：Forge 的 ticker 正常情况下就是一 tick 一次。 */
    private long lastTick = -1L;

    /**
     * A2（勘测 §4 第 115 行）：玩家给这个仓口分配的输入组号。
     *
     * <p>默认组 0、隔离关闭——旧版字段的默认值就是 {@code 0}/{@code false}
     * （{@code TileColorableMachineComponent.java:27-28}），而「隔离关闭」意味着本仓是**通用端口**，
     * 对每一组都可见（{@link HatchCollection#withGroup}）。两者必须分开记：组号 0 是合法的一组，
     * 不能拿它当「没启用」的哨兵。
     */
    private int groupId;
    private boolean groupInput;

    /** The ordinary six hatch families use the shared {@code machine_hatch} block entity type. */
    public MachineHatchBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlocks.HATCH_ENTITY.get(), pos, state);
    }

    /**
     * C4（0.73.0）：子类（ME 总线）有自己的方块实体类型。
     *
     * <p>{@code BlockEntity#getType()} 必须返回注册时用的那个类型，否则客户端在收到方块实体更新时
     * 会按 {@code machine_hatch} 去找一个实际注册为 {@code me_item_bus} 的实例。所以这里把类型作为
     * 参数传进来，而不是写死 {@code ModBlocks.HATCH_ENTITY}。
     */
    public MachineHatchBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                   BlockPos pos, BlockState state) {
        super(type, pos, state);
        MachineHatchBlock hatch = state.getBlock() instanceof MachineHatchBlock candidate ? candidate : null;
        this.kind = hatch != null ? hatch.kind() : HatchKind.ITEM_INPUT;
        // 0.73.2：走方块自己的 tier(BlockState)。普通仓口读 size 属性；ME 总线忽略状态值，
        // 始终返回家族阶梯里的唯一一项（见 MeItemBusBlock#tier）。
        this.tier = hatch != null ? hatch.tier(state) : HatchTier.ITEM_LADDER.get(0);

        // Locals first: javac's definite-assignment analysis does not treat a statement switch that covers
        // every enum constant as exhaustive when the selector is a method call, so it cannot prove the final
        // fields are assigned after the switch.
        ItemStackHandler itemStorage = null;
        FluidTank fluidStorage = null;
        EnergyStorage energyStorage = null;
        long energyTransferValue = 0;

        switch (this.kind.family()) {
            case ITEM -> {
                itemStorage = new HatchItems(Math.max(1, this.tier.itemSlots()));
            }
            case FLUID -> {
                fluidStorage = new FluidTankWithGasPriority(Math.max(1, HatchConfig.fluidHatchCapacity(this.tier)));
            }
            case ENERGY -> {
                long capacity = HatchConfig.energyHatchCapacityLong(this.tier);
                long transfer = HatchConfig.energyHatchTransferLong(this.tier);
                energyTransferValue = transfer;
                energyStorage = new LongEnergyStorage(capacity, transfer, transfer,
                        () -> onStorageChanged(false));
            }
        }

        this.items = itemStorage;
        this.tank = fluidStorage;
        this.energy = energyStorage;
        this.energyTransfer = energyTransferValue;
        this.itemCapability = itemStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> this.items);
        this.fluidCapability = fluidStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> this.tank);
        this.energyCapability = energyStorage == null ? LazyOptional.empty() : LazyOptional.of(() -> new EnergyPort(this.energy, this.kind.isInput()));
    }

    public HatchKind kind() {
        return this.kind;
    }

    /** Backing recipe inventory has native slot rules; the external capability owns sided IO gates. */
    private final class HatchItems extends com.reborn.modularmachinery.recipe.ResourceTransaction.NativeItems {
        HatchItems(int slots) { super(slots); }
        @Override public int candidateInsertLimit(int slot, net.minecraft.world.item.ItemStack current, net.minecraft.world.item.ItemStack offered) {
            return isItemValid(slot, offered) ? offered.getCount() : 0;
        }
        @Override public int candidateExtractLimit(int slot, net.minecraft.world.item.ItemStack current, int requested) { return requested; }
        @Override protected void onContentsChanged(int slot) {
            if (MachineHatchBlockEntity.this.kind == HatchKind.ITEM_OUTPUT) inventoryChanged = true;
            onStorageChanged(false);
        }
    }

    /**
     * C4-II（0.92.0，统一总线）：本仓口额外提供的**流体端口**，默认没有。
     *
     * <p>为什么需要它：端口收集按 {@code kind.family()} 分桶，统一总线（ME 输入/输出总线）是 ITEM
     * 家族——它的物品半边照常进物品桶，而流体半边（9 罐）需要经由这里作为**流体端口**另入流体桶。
     * 由 ME 总线子类覆写；那个类只在 AE2 在场时存在，主模组因此不引用任何 AE2 类型。
     */
    @Nullable
    public net.minecraftforge.fluids.capability.IFluidHandler extraFluidPort() {
        return null;
    }

    public HatchTier tier() {
        return this.tier;
    }

    /** The item storage, or {@code null} for fluid and energy hatches. */
    @Nullable
    public ItemStackHandler itemHandler() {
        return this.items;
    }

    /** The fluid tank, or {@code null} for item and energy hatches. */
    @Nullable
    public FluidTank fluidTank() {
        return this.tank;
    }

    /** The energy buffer, or {@code null} for item and fluid hatches. */
    @Nullable
    public EnergyStorage energyStorage() {
        return this.energy;
    }

    /**
     * 自动 IO 的节流状态，给 {@code /mm-diagnose} 用。
     *
     * <p>为什么值得单独问：节流计数决定「下一次什么时候尝试」，而它在游戏里没有任何显示；
     * 总线明明贴着箱子却不动时，先看这里能区分「ticker 没跑」和「跑了但一直失败」。
     */
    public String autoIoDescription() {
        String mode = switch (this.kind) {
            case ITEM_INPUT -> "抽入";
            case ITEM_OUTPUT -> "推出";
            case ENERGY_OUTPUT -> "推 FE";
            default -> null;
        };
        if (mode == null) {
            return "自动 IO=无（旧版也没有）";
        }
        return "自动 IO=" + mode + " 节流计数=" + this.successCounter
                + " tick=" + this.ticksExisted
                + (this.lastTick < 0 ? " 尚未跑过" : " 上次世界tick=" + this.lastTick)
                + (this.inventoryChanged ? " 库存变化=待重试" : "");
    }

    /** Human-readable capacity of this hatch, used by the tooltip and by hand-over notes. */
    public String capacityDescription() {
        // A4: read the storage the block entity actually built rather than the tier again. After a config
        // reload the two can differ until the chunk is rebuilt; this line then still reports what is true here.
        return switch (this.kind.family()) {
            case ITEM -> (this.items == null ? 0 : this.items.getSlots()) + " slots";
            case FLUID -> (this.tank == null ? 0 : this.tank.getCapacity()) + " mB";
            case ENERGY -> (this.energy == null ? 0 : EnergyAccess.capacity(this.energy)) + " FE, "
                    + this.energyTransfer + " FE/t";
        };
    }

    private void onStorageChanged(boolean syncToClient) {
        setChanged();
        if (syncToClient && this.level != null && !this.level.isClientSide) {
            // Only the fluid needs a block update: the client draws the fluid's own texture. Item slots travel
            // with the menu and the energy numbers travel in the menu's container data, so both would be waste.
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------- automatic IO (A1, 0.63.9)

    /**
     * 仓口的服务端 tick。
     *
     * <p>旧版三个方块各自在 {@code update()} 里做事：输入总线抽、输出总线推、能量输出仓推 FE。
     * 流体仓在旧版没有自动交换，这里也不加。
     */
    public void serverTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        long gameTime = this.level.getGameTime();
        if (gameTime == this.lastTick) {
            return;
        }
        this.lastTick = gameTime;
        switch (this.kind) {
            case ITEM_INPUT -> tickItemInput();
            case ITEM_OUTPUT -> tickItemOutput();
            case ENERGY_OUTPUT -> tickEnergyOutput();
            default -> { }
        }
        this.ticksExisted++;
    }

    private void tickItemInput() {
        if (!HatchAutoIo.shouldWork(this.ticksExisted, this.successCounter, this.inventoryChanged)) {
            return;
        }
        this.inventoryChanged = false;
        boolean moved = false;
        for (Direction face : Direction.values()) {
            BlockEntity neighbor = loadedNeighbor(this.worldPosition.relative(face));
            if (neighbor == null || isFamily(neighbor, HatchKind.Family.ITEM)) {
                continue;
            }
            LazyOptional<IItemHandler> capability =
                    neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite());
            IItemHandler handler = capability.orElse(null);
            if (handler != null) {
                moved |= HatchAutoIo.pull(handler, this.items);
            }
        }
        this.successCounter = moved
                ? HatchAutoIo.onSuccess(this.successCounter)
                : HatchAutoIo.onFailure(this.successCounter);
    }

    private void tickItemOutput() {
        if (!HatchAutoIo.shouldWork(this.ticksExisted, this.successCounter, this.inventoryChanged)) {
            return;
        }
        this.inventoryChanged = false;
        boolean moved = false;
        for (Direction face : Direction.values()) {
            BlockEntity neighbor = loadedNeighbor(this.worldPosition.relative(face));
            if (neighbor == null || isFamily(neighbor, HatchKind.Family.ITEM)) {
                continue;
            }
            LazyOptional<IItemHandler> capability =
                    neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite());
            IItemHandler handler = capability.orElse(null);
            if (handler != null) {
                moved |= HatchAutoIo.push(this.items, handler);
            }
        }
        if (moved) {
            this.successCounter = HatchAutoIo.onSuccess(this.successCounter);
            // 旧版 TileItemOutputBus.markNoUpdate() 的语义：下一 tick 立刻再试。
            this.inventoryChanged = true;
        } else {
            this.successCounter = HatchAutoIo.onFailure(this.successCounter);
        }
    }

    private void tickEnergyOutput() {
        if (this.energy == null) {
            return;
        }
        // A4: the transfer recorded at construction time (EnergyStorage has no max-extract getter).
        long remaining = Math.min(this.energyTransfer, EnergyAccess.stored(this.energy));
        if (remaining <= 0) {
            return;
        }
        for (Direction face : Direction.values()) {
            BlockEntity neighbor = loadedNeighbor(this.worldPosition.relative(face));
            if (neighbor == null || isFamily(neighbor, HatchKind.Family.ENERGY)) {
                continue;
            }
            IEnergyStorage target = OptionalEnergy.find(neighbor, face.getOpposite());
            if (target == null) target = neighbor.getCapability(ForgeCapabilities.ENERGY, face.getOpposite()).orElse(null);
            if (target == null) {
                continue;
            }
            remaining -= EnergyAccess.push(this.energy, target, remaining);
            if (remaining <= 0) {
                break;
            }
        }
    }

    @Nullable
    private BlockEntity loadedNeighbor(BlockPos pos) {
        if (level == null || level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return null;
        // Keep create=false on the actual lookup too, even if availability changes after the guard.
        var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    private static boolean isFamily(BlockEntity entity, HatchKind.Family family) {
        return entity instanceof MachineHatchBlockEntity hatch && hatch.kind.family() == family;
    }

    // ------------------------------------------------------- menu data

    /** Client-side mirror of the four numbers the hatch screen shows. */
    private final int[] clientEnergyWords = new int[8];
    private int clientEnergyStored;
    private int clientEnergyCapacity;
    private int clientFluidAmount;
    private int clientFluidCapacity;
    /**
     * 波 5.3：气体那一半的量与容量，同样走菜单的数据槽。
     *
     * <p>为什么要另开两个槽而不是复用流体那对：流体与气体共用同一个罐的**空间**，但它们是
     * **两笔不同的内容**（一个罐要么装流体要么装气体）。界面要把两者都画出来，就必须分别拿到数字。
     */
    private int clientGasAmount;
    private int clientGasCapacity;

    /**
     * One of the four values the hatch screen displays, in the order the menu's container data uses them.
     *
     * <p>The server answers from the live storage; the client answers from the mirrored values, which the menu
     * writes through {@link #setMenuValue}. This is the same arrangement vanilla furnaces use, and it exists
     * because a container data slot is the only place those numbers can live on the client.
     */
    public int menuValue(int index) {
        if (index >= 6 && index < 14) {
            if (this.level == null || this.level.isClientSide) return this.clientEnergyWords[index - 6];
            long value = this.energy == null ? 0 : index < 10 ? EnergyAccess.stored(this.energy) : EnergyAccess.capacity(this.energy);
            return EnergyMenuValues.word(value, (index - 6) % 4);
        }
        if (this.level == null || this.level.isClientSide) {
            return switch (index) {
                case 0 -> this.clientEnergyStored;
                case 1 -> this.clientEnergyCapacity;
                case 2 -> this.clientFluidAmount;
                case 3 -> this.clientFluidCapacity;
                case 4 -> this.clientGasAmount;
                case 5 -> this.clientGasCapacity;
                default -> 0;
            };
        }
        return switch (index) {
            case 0 -> this.energy == null ? 0 : this.energy.getEnergyStored();
            case 1 -> this.energy == null ? 0 : this.energy.getMaxEnergyStored();
            case 2 -> this.tank == null ? 0 : this.tank.getFluidAmount();
            case 3 -> this.tank == null ? 0 : this.tank.getCapacity();
            // 气体的两个数字从**兼容层**取，而且走的是通用入口 {@code ModCompat.namedValues}——
            // 本类**不认识任何模块**（它每个世界都会被加载，包括没装那个模组的进程）。
            // 没装 Mekanism 时这个 map 是空的，这两个槽就是 0，界面上那一截不画。
            case 4 -> gasMenuValue(true);
            case 5 -> gasMenuValue(false);
            default -> 0;
        };
    }

    /**
     * 流体仓的罐：**一个仓里只能有一种东西**（波 5.3）。
     *
     * <p>旧版 {@code HybridGasTank extends HybridTank} 的语义是「{@code setFluid} 与 {@code setGas} 互相覆盖
     * 同一个罐」，所以管道本来就**拿不到**一个「既有液体又有气体」的罐——它只有一个罐。
     * 本版把气体那一半放在兼容层里，于是必须**显式**做到这件事，否则流体管道会往同一个罐里灌液体，
     * 两者同时存在。
     *
     * <p>做法：罐里已经有气体时，流体那一侧**看起来是空的**——容量照报（管道据此知道这里能装流体），
     * 但已有的流体读成 0、灌入一律收 0。等气体被抽空，流体那一半自动恢复可用。
     * 挡住的只是**读与灌**，不是那道墙：没有任何状态被丢掉，也就没有「看不见的液体」。
     */
    private final class FluidTankWithGasPriority extends com.reborn.modularmachinery.recipe.ResourceTransaction.NativeTank {

        FluidTankWithGasPriority(int capacity) {
            super(capacity);
        }

        @Override public int candidateFillLimit(java.util.List<net.minecraftforge.fluids.FluidStack> contents,
                                               net.minecraftforge.fluids.FluidStack offered) {
            return gasBlocksFluid() ? 0 : offered.getAmount();
        }

        @Override protected void onContentsChanged() {
            // The screen renders the fluid's own texture, so the client needs to know which fluid it is.
            onStorageChanged(true);
        }

        @Override public net.minecraftforge.fluids.FluidStack getFluid() {
            if (gasBlocksFluid()) {
                // 计数：**闸门挡住过一次「真的装着东西」的读**。这是「界面上有液体、Jade 只显示 0」
                // 这类现象的唯一可能来源，而它发生在一瞬间，日志抓不到，所以记成持久计数。
                if (super.getFluidAmount() > 0) {
                    GATED_READS_WITH_FLUID.incrementAndGet();
                }
                return net.minecraftforge.fluids.FluidStack.EMPTY;
            }
            if (super.getFluidAmount() > 0) {
                OPEN_READS_WITH_FLUID.incrementAndGet();
            }
            return super.getFluid();
        }

        @Override public int getFluidAmount() {
            return gasBlocksFluid() ? 0 : super.getFluidAmount();
        }

        @Override public int fill(net.minecraftforge.fluids.FluidStack resource,
                                  net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
            // 灌不进去：气体占着这个罐。收 0 是「一点都没收」，管道据此就知道这里装不下。
            // Saved fluid may exceed a reduced capacity. Refuse more without letting FluidTank.fill
            // report negative reception or truncate the existing contents; draining remains available.
            return gasBlocksFluid() || super.getFluidAmount() >= getCapacity() ? 0 : super.fill(resource, action);
        }

        /**
         * 这个罐里**真的**有没有流体，绕过上面那层挡住逻辑。
         *
         * <p>气体罐据此拒绝向已有流体的仓注入——而被挡住时
         * {@code getFluidAmount()} 恒答 0，从外面问不出来。
         */
        int realFluidAmount() {
            return super.getFluidAmount();
        }

        net.minecraftforge.fluids.FluidStack realFluidStack() {
            return super.getFluid().copy();
        }
    }

    /**
     * 闸门的两个计数，给 {@code /mm-diagnose} 用。
     *
     * <p>为什么要静态：这两个数回答的是「**有没有**发生过闸门把真值挡成 0」，
     * 而那个瞬间在游戏里抓不到（Jade 每次照到方块都会读一次）。静态计数让它能被事后问出来。
     * 它们是**诊断量**而不是状态，不参与逻辑判断。
     */
    private static final java.util.concurrent.atomic.AtomicInteger GATED_READS_WITH_FLUID =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger OPEN_READS_WITH_FLUID =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 闸门计数的一行描述；给 {@code /mm-diagnose} 与探针用。 */
    public static String gateCounters() {
        return "放行且罐里有液体 " + OPEN_READS_WITH_FLUID.get() + " 次"
                + " / **挡住且罐里真有液体** " + GATED_READS_WITH_FLUID.get() + " 次"
                + "（后者 > 0 就说明这个闸门误伤了：界面上有液体而 Jade 读到 0）";
    }

    /**
     * 这个仓里现在是否有气体（于是流体那一侧要看起来是空的）。
     *
     * <p>走**通用入口** {@code ModCompat.namedValues}——本类不认识任何模块，也不该认识：
     * 它每个世界都会被加载，包括没装那个模组的进程。没装 Mekanism 时这个 map 是空的，本方法恒为 false。
     *
     * <p><b>它只在服务端返回 true（0.51.0 修正）。</b>理由：这条闸门是给**管道**用的，
     * 而管道的行为由服务端裁定；而客户端的气体罐是从**上一次同步过来的 NBT** 里读出来的，
     * 那份数据可能已经过时（气体被抽空时并不必然再同步一次）。若让闸门在客户端也生效，
     * 就会把客户端那个**明明装着液体**的罐读成空——症状正是所有者截图上那一幕：
     * 服务端的读数、界面 tooltip 都是 32000 mB，而 Jade 插件在客户端读到的却是「空」。
     * 客户端只该显示它真正存着的东西。
     */
    private boolean gasBlocksFluid() {
        if (this.level != null && this.level.isClientSide) {
            return false;
        }
        String amount = com.reborn.modularmachinery.compat.ModCompat.namedValues(this).get("gas.amount");
        if (amount == null) {
            return false;
        }
        try {
            return Long.parseLong(amount) > 0L;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** 事务恢复所需的真实流体副本，包含旧存档中可能被气体显示闸门隐藏的内容。 */
    public net.minecraftforge.fluids.FluidStack realFluidStack() {
        if (this.tank instanceof FluidTankWithGasPriority hybrid) {
            return hybrid.realFluidStack();
        }
        return this.tank == null ? net.minecraftforge.fluids.FluidStack.EMPTY : this.tank.getFluid().copy();
    }

    /** 真实流体量；兼容层据此拒绝另一种介质的注入。 */
    public int realFluidAmount() {
        return this.tank instanceof FluidTankWithGasPriority hybrid ? hybrid.realFluidAmount() : 0;
    }

    /** 气体那一半的量或容量，交给兼容层回答；没有气体那一半时是 0。 */
    private int gasMenuValue(boolean amount) {
        String raw = com.reborn.modularmachinery.compat.ModCompat.namedValues(this)
                .get(amount ? "gas.amount" : "gas.capacity");
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    public void setMenuValue(int index, int value) {
        if (index >= 6 && index < 14) { this.clientEnergyWords[index - 6] = value & 0xffff; return; }
        switch (index) {
            case 0 -> this.clientEnergyStored = value;
            case 1 -> this.clientEnergyCapacity = value;
            case 2 -> this.clientFluidAmount = value;
            case 3 -> this.clientFluidCapacity = value;
            case 4 -> this.clientGasAmount = value;
            case 5 -> this.clientGasCapacity = value;
            default -> { }
        }
    }

    /**
     * The saved state doubles as the update tag, so the client knows which fluid a fluid hatch holds and can
     * draw its texture. Only fluid hatches ask for that update.
     */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        Integer previousColor = this.machineColor;
        super.onDataPacket(connection, packet);
        if (this.level != null && this.level.isClientSide
                && !java.util.Objects.equals(previousColor, this.machineColor)) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------- screen

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(this.kind.itemTranslationKey(this.tier));
    }

    // ------------------------------------------------------- A2: input groups

    /** 这个仓口种类能不能被分组：只有物品输入与流体输入能（旧版 {@code canGroupInput}）。 */
    public boolean canGroupInput() {
        return this.kind.canGroupInput();
    }

    /** 玩家是否给这个仓启用过组隔离；不可分组的仓口恒 {@code false}。 */
    public boolean isGroupInput() {
        return this.canGroupInput() && this.groupInput;
    }

    /** 玩家填的组号（未启用隔离时仍是上次填的值，和旧版一样不擦除）。 */
    public int groupId() {
        return this.groupId;
    }

    /**
     * 这个仓口在配方端口集合里的组号：启用隔离时是自己的组号，否则 {@code -1}（通用）。
     *
     * <p>这就是旧版 {@code getGroupId()} 的对应物（{@code TileColorableMachineComponent.java:105-108}）。
     * 归一在这里做一次，{@link HatchCollection.Builder#add} 会再按仓口种类挡一次——UI 拦不住将来的
     * 调用点，但这两层至少能让「输出仓被分组」变成不可能。
     */
    public int effectiveGroup() {
        return this.isGroupInput() ? this.groupId : -1;
    }

    /** 设置组状态。不可分组的仓口静默忽略；组号下限 0（旧版也只拦了下限，见 GUI 里的钳制）。 */
    public void setGroup(boolean enabled, int id) {
        if (!this.canGroupInput()) {
            return;
        }
        int clamped = Math.max(0, id);
        if (this.groupInput == enabled && this.groupId == clamped) {
            return;
        }
        int previousGroup = effectiveGroup();
        this.groupInput = enabled;
        this.groupId = clamped;
        if (level != null && previousGroup != effectiveGroup()) {
            MachineControllerBlockEntity.inputProviderChanged(level, worldPosition);
        }
        this.setChanged();
    }

    /** `/mm-diagnose` 用：这个仓口属于哪一组。 */
    public String groupDescription() {
        if (!this.canGroupInput()) {
            return "组=不适用（旧版也不能分组）";
        }
        return this.groupInput ? "组=" + this.groupId : "组=通用（隔离未启用）";
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,
            net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new com.reborn.modularmachinery.menu.MachineHatchMenu(id, inventory, this);
    }

    /**
     * 兼容模块的能力从这里进来（波 5.3）。
     *
     * <p><b>本类不认识任何一种兼容能力</b>——它只知道 {@code ModCompat.mapPort} 这个通用入口，
     * 而那个入口背后的映射器由模块自己注册。这是「一个模块加一种端口不必改主模组」的落点，
     * 也是没装那个模组时本类**不会碰到它的任何类型**的保证（详见
     * {@code compat/BlockEntityPortMapper} 的类注释）。
     *
     * <p>映射器在 {@code FMLCommonSetupEvent} 里登记，而能力查询在那之后（玩家进世界才有方块实体），
     * 所以这里是「顺带问一句」，不需要自己判断时机。
     */
    /**
     * 诊断用：**父类**（Forge 的 {@code CapabilityDispatcher}）会为流体交出什么。
     *
     * <p>为什么需要它：{@code getCapability} 被本类重写了，所以「Jade 读到的」与「本类给出的」
     * 在正常情况下来自同一个方法。但如果**有别的模组用 {@code AttachCapabilitiesEvent}
     * 往这个方块实体上挂了自己的流体能力**，那么 Forge 的能力派发（也就是父类那一条路，
     * Jade 走的正是它）会先给出**第三方**的处理器——于是出现「本类读到 32000、
     * Jade 却读到 0」这种自相矛盾的读数。这个方法把父类那一条路单独问出来，用于分辨。
     */
    public LazyOptional<net.minecraftforge.fluids.capability.IFluidHandler> fluidCapabilityViaSuper() {
        return super.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        // 记下每一次查询的能力名：这是「管道到底问了没有、问的是不是那个能力」的唯一低成分证据。
        // 记在静态集合里而不打印，因为这条路径每 tick 每个方块都会走，打印会把日志淹掉；
        // 要看的时候用 /mm-diagnose。
        com.reborn.modularmachinery.compat.ModCompat.recordCapability(capability);
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return this.itemCapability.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return this.fluidCapability.cast();
        }
        if (capability == ForgeCapabilities.ENERGY) {
            return this.energyCapability.cast();
        }
        if (this.energy != null && this.capsValid) {
            if (this.optionalEnergy == null) this.optionalEnergy = new OptionalEnergy.Handle(this.energyCapability.orElseThrow(IllegalStateException::new));
            LazyOptional<?> wide = this.optionalEnergy.get(capability);
            if (wide != null) return wide.cast();
        }
        LazyOptional<?> compat = com.reborn.modularmachinery.compat.ModCompat.mapPort(capability, this);
        if (compat != null) {
            return compat.cast();
        }
        return super.getCapability(capability, side);
    }

    /**
     * 兼容层的内容变了：存盘，并（流体仓需要）同步给客户端。
     *
     * <p>{@code public} 而不是包内可见：它由 {@code compat} 包里的气体罐作为回调调用，
     * 而那个包不是本包。
     */
    /**
     * 这台机器部件的染色，或 {@code null} 表示没被设过。颜色处理器在客户端读它。
     */
    @Nullable
    public Integer machineColor() {
        return this.machineColor;
    }

    /**
     * 由控制器在成型/换机时推下来（旧版 {@code tryColorize} 的对应物）。
     *
     * <p>变了才写、才同步：这个值在一次成型里可能被推给几十个部件，而 {@code sendBlockUpdated}
     * 会把方块实体重新发给所有能看到它的客户端——无条件发就是在成型那一瞬间制造一阵无谓的流量。
     */
    public void setMachineColor(@Nullable Integer color) {
        if (java.util.Objects.equals(this.machineColor, color)) {
            return;
        }
        this.machineColor = color;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    public void markStorageChanged() {
        // 气体让客户端的方块实体数据过期（与流体同一条理由：界面要画出装的是什么），所以 syncToClient = true。
        onStorageChanged(true);
    }

    @Override
    public void invalidateCaps() {
        this.capsValid = false;
        super.invalidateCaps();
        this.itemCapability.invalidate();
        this.fluidCapability.invalidate();
        this.energyCapability.invalidate();
        if (this.optionalEnergy != null) this.optionalEnergy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.capsValid = true;
        this.itemCapability = this.items == null ? LazyOptional.empty() : LazyOptional.of(() -> this.items);
        this.fluidCapability = this.tank == null ? LazyOptional.empty() : LazyOptional.of(() -> this.tank);
        this.energyCapability = this.energy == null ? LazyOptional.empty() : LazyOptional.of(() -> new EnergyPort(this.energy, this.kind.isInput()));
        this.optionalEnergy = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.items != null) {
            tag.put(TAG_ITEMS, this.items.serializeNBT());
        }
        if (this.tank != null) {
            tag.put(TAG_TANK, this.tank.writeToNBT(new CompoundTag()));
        }
        if (this.energy != null) {
            tag.putLong(TAG_ENERGY, EnergyAccess.stored(this.energy));
        }
        if (this.machineColor != null) {
            tag.putInt(TAG_COLOR, this.machineColor);
        }
        // A2：只有可分组的仓口才写这两个键，理由同旧版（TileColorableMachineComponent.java:71-85）。
        if (this.kind.canGroupInput()) {
            tag.putInt(TAG_GROUP_ID, this.groupId);
            tag.putBoolean(TAG_GROUP_INPUT, this.groupInput);
        }
        // 兼容层的内容（例如流体仓上的气体）由各模块自己序列化，本类不知道里面是什么。
        com.reborn.modularmachinery.compat.ModCompat.savePorts(tag, this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (this.items != null && tag.contains(TAG_ITEMS)) {
            this.items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        }
        if (this.tank != null && tag.contains(TAG_TANK)) {
            this.tank.readFromNBT(tag.getCompound(TAG_TANK));
        }
        if (this.energy != null && tag.contains(TAG_ENERGY)) {
            // Exact replacement, independent of transfer limits; getLong accepts legacy IntTag saves.
            if (this.energy instanceof LongEnergyStorage wide) wide.setStored(tag.getLong(TAG_ENERGY));
            else this.energy.deserializeNBT(net.minecraft.nbt.IntTag.valueOf(EnergyAccess.asInt(tag.getLong(TAG_ENERGY))));
        }
        this.machineColor = tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : null;
        if (this.kind.canGroupInput()) {
            this.groupId = tag.contains(TAG_GROUP_ID) ? Math.max(0, tag.getInt(TAG_GROUP_ID)) : 0;
            this.groupInput = tag.getBoolean(TAG_GROUP_INPUT);
        }
        com.reborn.modularmachinery.compat.ModCompat.loadPorts(tag, this);
    }
}
