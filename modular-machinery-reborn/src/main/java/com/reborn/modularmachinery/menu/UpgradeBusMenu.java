package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.UpgradeBusBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The upgrade bus's container — the original {@code ContainerUpgradeBus} (94 lines).
 *
 * <h2>Slots</h2>
 *
 * <p>The original laid its upgrade slots out from {@code (8, 17)}, three per row, 18 apart, and the player's
 * 3×9 inventory at {@code (8, 131)} with the hotbar at {@code y = 189} ({@code ContainerUpgradeBus.java:13-14},
 * {@code :37-46}). All of that is copied here unchanged, because the panel's own slot frames are painted into
 * {@code guiupgradebus.png} at the matching positions.
 *
 * <h2>What the slots accept</h2>
 *
 * <p><b>Everything.</b> That is not an omission: the original's {@code ContainerUpgradeBus} added plain
 * {@code SlotItemHandler}s with no {@code isItemValid} override, so any item could be placed and
 * {@code TileUpgradeBus#onUpgradeInventoryChanged} ({@code :94-119}) simply ignored anything
 * {@code RegistryUpgrade.supportsUpgrade} rejected. Filtering here would be a Reborn-only change to what a
 * player can do, and the bus's whole point is that a pack author can put several different upgrades in it.
 *
 * <p>What <b>is</b> enforced is the same thing the original enforced one level up: an item that carries no
 * upgrade contributes nothing, and an upgrade the machine rejects contributes nothing (the original's
 * {@code UpgradeBusProvider#getUpgrades}). Both are reported by the screen rather than blocked at the slot.
 */
public final class UpgradeBusMenu extends AbstractContainerMenu {

    /**
     * The panel heading, the original's {@code gui.upgradebus.title}. Declared here so the block entity can use
     * it for the menu's display name without referencing a client-only class — the same arrangement as
     * {@link ParallelControllerMenu}.
     */
    public static final String TITLE_KEY = "gui.modular_machinery_reborn.upgradebus.title";

    /** The bus's first slot in this menu; the original put the block's slots before the player's, too. */
    public static final int BUS_SLOT_FIRST = 0;

    private static final int PLAYER_INVENTORY_Y = 131;
    private static final int HOTBAR_Y = 189;

    private final UpgradeBusBlockEntity bus;

    private final ContainerData busData = new ContainerData() {
        @Override public int get(int index) {
            return bus.menuValue(index);
        }

        @Override public void set(int index, int value) {
            bus.setMenuValue(index, value);
        }

        @Override public int getCount() {
            return UpgradeBusBlockEntity.DATA_COUNT;
        }
    };

    public UpgradeBusMenu(int id, Inventory inventory, UpgradeBusBlockEntity bus) {
        super(ModMenus.UPGRADE_BUS.get(), id);
        this.bus = bus;
        addBusSlots(bus.items());
        addDataSlots(this.busData);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public UpgradeBusMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, (UpgradeBusBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** The original's three-per-row layout, from the same origin ({@code ContainerUpgradeBus.java:21-34}). */
    private void addBusSlots(IItemHandler handler) {
        int x = 8;
        int y = 17;
        for (int index = 0; index < handler.getSlots(); index++) {
            addSlot(new SlotItemHandler(handler, index, x, y));
            x += 18;
            if ((index + 1) % 3 == 0) {
                x = 8;
                y += 18;
            }
        }
    }

    public UpgradeBusBlockEntity bus() {
        return this.bus;
    }

    /** How many slots the bus has — the screen draws one frame per slot and sizes its scrollable column after. */
    public int slotCount() {
        return Math.max(0, this.busData.get(UpgradeBusBlockEntity.DATA_SLOT_COUNT));
    }

    /** How many distinct upgrades the bus holds right now; the screen's scrollbar range depends on the text. */
    public int upgradeCount() {
        return Math.max(0, this.busData.get(UpgradeBusBlockEntity.DATA_UPGRADE_COUNT));
    }

    /** How many machines the bus is bound to, the original's {@code gui.upgradebus.bounded} count. */
    public int boundMachineCount() {
        return Math.max(0, this.busData.get(UpgradeBusBlockEntity.DATA_BOUND_COUNT));
    }

    /**
     * The original's {@code transferStackInSlot} moved bus contents to the player and back
     * ({@code ContainerUpgradeBus.java:48-93}); this is the same two-way move over the mod's usual slot ranges.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int busSlots = this.bus.items().getSlots();
        int playerFirst = busSlots;
        int playerLast = this.slots.size() - 1;

        if (index < busSlots) {
            if (!moveItemStackTo(stack, playerFirst, playerLast + 1, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, BUS_SLOT_FIRST, busSlots, false)) {
            // No room in the bus: shuffle inside the player's inventory, as the original's fallback did.
            if (index < playerFirst + 27) {
                if (!moveItemStackTo(stack, playerFirst + 27, playerLast + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, playerFirst, playerFirst + 27, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.bus.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }
}
