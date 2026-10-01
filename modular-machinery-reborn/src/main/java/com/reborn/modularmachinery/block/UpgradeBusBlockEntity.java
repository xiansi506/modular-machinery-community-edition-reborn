package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.menu.UpgradeBusMenu;
import com.reborn.modularmachinery.upgrade.UpgradeBusUtility;
import com.reborn.modularmachinery.upgrade.UpgradeStack;
import com.reborn.modularmachinery.upgrade.UpgradeEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The upgrade bus's block entity — the original {@code TileUpgradeBus} (298 lines).
 *
 * <h2>What it keeps, and what it does not</h2>
 *
 * <p>Two things, exactly as the original did:
 *
 * <ul>
 *   <li><b>Upgrade slots.</b> One {@code ItemStackHandler} whose size comes from the block's tier
 *       ({@link UpgradeBusTier}, original slot counts 3/6/9/12/18, overridable from the config). The slots are
 *       not filtered — see {@link com.reborn.modularmachinery.menu.UpgradeBusMenu} for why, and note that the
 *       original did not filter them either: an item that carries no upgrade simply contributes nothing.</li>
 *   <li><b>The controllers it belongs to.</b> {@code Map<BlockPos, ResourceLocation>} of controller position to
 *       machine registry name, the original's {@code Map<BlockPos, DynamicMachine> boundedMachine}. The machine
 *       is held as a <b>name</b> rather than an object for the same reason {@code UpgradeType} holds names:
 *       a {@code /reload} replaces every {@code MachineDefinition} instance, so an object reference would point
 *       at the previous generation while a name survives (M6a, §3.1).</li>
 * </ul>
 *
 * <h2>What was deliberately left out</h2>
 *
 * <ul>
 *   <li><b>The {@code synchronized} blocks.</b> The original synchronised around
 *       {@code onUpgradeInventoryChanged} ({@code :94}) and {@code getUpgrades} ({@code :256}). This project's
 *       recipe engine is single-threaded — the scoping document's §5.1 states it and §7 lists the locks under
 *       "explicitly not ported" — so copying them would only suggest a concurrency that does not exist.</li>
 *   <li><b>An inventory listener.</b> The original cached {@code foundUpgrades} and rebuilt it on every slot
 *       change. Nothing here is cached: {@link #upgrades()} reads the slots, and the read is a handful of map
 *       lookups. That removes the whole class of bugs the original's listener had to avoid by re-listing
 *       dynamic upgrades for a specific slot ({@code :115-117}).</li>
 *   <li><b>Per-upgrade custom NBT on the bus.</b> The original let an upgrade's state live on the bus, keyed by
 *       upgrade name ({@code getUpgradeCustomData}, {@code :275-282}), because a CraftTweaker handler could
 *       write to it. With no script bridge (D7) and no handler layer, nothing could read a value written there;
 *       a declared upgrade carries only its declaration. This is recorded as a gap rather than faked.</li>
 * </ul>
 *
 * <h2>How it learns which machine it belongs to</h2>
 *
 * <p>From the controller, which finds it by walking the structure's own pattern positions — the second
 * collection path D13 introduced for the parallel controller, reused here. See
 * {@link com.reborn.modularmachinery.block.MachineControllerBlockEntity#collectUpgradeBuses}. The pairing is
 * dropped again by {@link #serverTick}'s reconcile, which is the original's {@code doRestrictedTick}
 * ({@code :66-92}) running every 20 ticks.
 */
public final class UpgradeBusBlockEntity extends BlockEntity implements MenuProvider, UpgradeBusUtility {

    /** The original's NBT key for the bus inventory ({@code TileUpgradeBus.java:188}). */
    public static final String TAG_INVENTORY = "inv";
    /** The original's NBT key ({@code :198}). */
    public static final String TAG_BOUNDED_MACHINE = "boundedMachine";
    /** Per-entry keys of that list ({@code :220-224}). */
    private static final String TAG_BOUND_POS = "pos";
    private static final String TAG_BOUND_MACHINE = "machine";

    /** How often the bound-controller list is reconciled, the original's {@code ticksExisted % 20}. */
    private static final int RECONCILE_INTERVAL = 20;

    /** Container-data indices the screen reads; see {@link #menuValue}. */
    public static final int DATA_UPGRADE_COUNT = 0;
    public static final int DATA_BOUND_COUNT = 1;
    public static final int DATA_SLOT_COUNT = 2;

    public static final int DATA_COUNT = 3;

    private final UpgradeBusTier tier;
    private final ItemStackHandler items;

    /** Controller position to machine name — the original's {@code boundedMachine}. */
    private final Map<BlockPos, ResourceLocation> boundMachines = new LinkedHashMap<>();

    private int reconcileCountdown = RECONCILE_INTERVAL;

    public UpgradeBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.UPGRADE_BUS_ENTITY.get(), pos, state);
        this.tier = state.hasProperty(UpgradeBusBlock.TYPE)
                ? state.getValue(UpgradeBusBlock.TYPE) : UpgradeBusTier.NORMAL;
        // The original created its tile entity with the slot count read from the tier
        // (BlockUpgradeBus.java:145-147), so the size is fixed for the block's lifetime.
        this.items = new ItemStackHandler(this.tier.maxUpgradeSlots()) {
            @Override protected void onContentsChanged(int slot) {
                setChanged();
                // The client builds its own upgrade list from the synced slot contents, so a change here is
                // visible without any extra packet - the menu's slot sync carries it.
            }
        };
    }

    public UpgradeBusTier tier() {
        return this.tier;
    }

    /** The bus's upgrade slots, as the menu and the block's own reader see them. */
    public ItemStackHandler items() {
        return this.items;
    }

    /** How many slots this bus has; the screen places one 18×18 frame per slot. */
    public int slotCount() {
        return this.items.getSlots();
    }

    // ------------------------------------------------------- upgrade bus utility

    @Override
    public boolean bindMachine(BlockPos controllerPos, ResourceLocation machineId) {
        ResourceLocation existing = this.boundMachines.get(controllerPos);
        if (machineId.equals(existing)) {
            return false;
        }
        this.boundMachines.put(controllerPos, machineId);
        this.setChanged();
        syncToClient();
        return true;
    }

    @Override
    public Map<BlockPos, ResourceLocation> boundMachines() {
        // The client's copy arrives through this block's own update tag and lands in the same field via load(),
        // so there is exactly one place the list lives on either side.
        return Map.copyOf(this.boundMachines);
    }

    @Override
    public UpgradeStack.Bag upgrades() {
        return UpgradeEffects.read(this.items);
    }

    // ------------------------------------------------------- ticking

    /**
     * The original's {@code doRestrictedTick} ({@code :66-92}): every 20 ticks, drop every recorded controller
     * that is gone or has stopped being the machine it was when recorded.
     *
     * <p>The controller itself never unbinds. It <b>binds</b> on each structure check, which is the original's
     * direction of travel too — {@code UpgradeBusProvider#boundMachine} was called by the controller, while the
     * removal was the bus's own reconcile. Keeping that split means a machine that is broken and rebuilt, or
     * replaced by a different machine in the same place, ends up with exactly the upgrades it can use.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, UpgradeBusBlockEntity bus) {
        if (level.isClientSide) {
            return;
        }
        if (bus.reconcileCountdown-- > 0 || bus.boundMachines.isEmpty()) {
            return;
        }
        bus.reconcileCountdown = RECONCILE_INTERVAL;
        bus.reconcile(level);
    }

    /** One pass of the reconcile, split out so it can be driven without a ticker. */
    public void reconcile(Level level) {
        boolean changed = this.boundMachines.entrySet().removeIf(entry -> {
            if (!level.isLoaded(entry.getKey())) {
                return true;
            }
            if (!(level.getBlockEntity(entry.getKey()) instanceof MachineControllerBlockEntity controller)) {
                return true;
            }
            // The original compared the DynamicMachine object (:85); a registry name carries the same
            // information across a reload, which an object reference cannot.
            return !entry.getValue().equals(controller.machineId());
        });
        if (changed) {
            this.setChanged();
            syncToClient();
        }
    }

    /** The original's {@code markNoUpdateSync}: the recorded set is what the bus screen prints. */
    private void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------- menu data

    /**
     * One of the values the screen reads. The server answers from live state; the client answers from the
     * mirrors the menu writes through {@link #setMenuValue}.
     *
     * <p>Follows the pattern M6d established for the parallel controller and 0.19.0 for the machine controller:
     * a server → client read goes through {@code ContainerData}, never a packet.
     */
    public int menuValue(int index) {
        return switch (index) {
            case DATA_UPGRADE_COUNT -> upgrades().size();
            case DATA_BOUND_COUNT -> this.boundMachines.size();
            case DATA_SLOT_COUNT -> slotCount();
            default -> 0;
        };
    }

    public void setMenuValue(int index, int value) {
        // Nothing in this menu is writable. The three values are derived; the screen only reads them.
    }

    // ------------------------------------------------------- screen and menu

    @Override
    public Component getDisplayName() {
        return Component.translatable(UpgradeBusMenu.TITLE_KEY);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new UpgradeBusMenu(id, inventory, this);
    }

    public void open(Player player) {
        NetworkHooks.openScreen((ServerPlayer) player, this, worldPosition);
    }

    // ------------------------------------------------------- persistence and sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_INVENTORY, this.items.serializeNBT());
        saveBoundMachines(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_INVENTORY)) {
            this.items.deserializeNBT(tag.getCompound(TAG_INVENTORY));
        }
        this.boundMachines.clear();
        if (tag.contains(TAG_BOUNDED_MACHINE, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_BOUNDED_MACHINE, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ResourceLocation machine = ResourceLocation.tryParse(entry.getString(TAG_BOUND_MACHINE));
                // The original skipped an entry whose machine no longer resolved (:202-205); a machine that is
                // not installed right now is the same situation, and the controller will simply re-bind later.
                if (machine != null) {
                    this.boundMachines.put(BlockPos.of(entry.getLong(TAG_BOUND_POS)), machine);
                }
            }
        }
    }

    /**
     * The bound-machine list, and only that. This is what reaches the client: the screen prints the machines the
     * bus is attached to, and that list is not a slot the menu would sync. The upgrade slots themselves travel
     * through the menu like every other inventory in this mod, and the upgrade <i>declarations</i> are data the
     * client already has from its own reload — so no packet carries them either.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveBoundMachines(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private void saveBoundMachines(CompoundTag tag) {
        if (this.boundMachines.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        this.boundMachines.forEach((pos, machine) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_BOUND_POS, pos.asLong());
            entry.putString(TAG_BOUND_MACHINE, machine.toString());
            list.add(entry);
        });
        tag.put(TAG_BOUNDED_MACHINE, list);
    }
}
