package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.machine.ControllerStatus;
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
 * The machine controller's container.
 *
 * <p>Layout follows the original {@code ContainerController} exactly: a single blueprint slot at (151, 8),
 * the player's 3×9 inventory at (8, 131) and the hotbar at (8, 189). The window is 176×213, which is why the
 * player slots sit much lower than the usual 84/142.
 */
public final class MachineControllerMenu extends AbstractContainerMenu {

    /** Slot 0 is the blueprint; the original controller had no other slot. */
    private static final int BLUEPRINT = 0;
    private static final int PLAYER_INVENTORY_FIRST = 1;
    private static final int PLAYER_INVENTORY_LAST = 27;
    private static final int HOTBAR_FIRST = 28;
    private static final int HOTBAR_LAST = 36;

    /**
     * The prefix of every controller status key, the original's {@code gui.controller.status…}. Shared with the
     * factory controller's menu ({@code FactoryControllerMenu}), which prints the same
     * {@code gui.controller.status} heading — the original's {@code GuiFactoryController:254} used it too.
     */
    public static final String STATUS_KEY = "gui.modular_machinery_reborn.controller.status";

    private static final int PLAYER_INVENTORY_Y = 131;
    private static final int HOTBAR_Y = 189;

    /** The original controller's blueprint slot, {@code ContainerController}'s {@code (151, 8)}. */
    public static final int BLUEPRINT_SLOT_X = 151;
    public static final int BLUEPRINT_SLOT_Y = 8;
    /** {@code ContainerController#addPlayerSlots}: {@code 8 + j * 18}. */
    public static final int PLAYER_SLOT_X = 8;
    /** {@code 18 * 9} — the width the nine player columns occupy, hotbar included. */
    public static final int SLOT_PITCH = 18;
    public static final int SLOT_COLUMNS = 9;
    /** A slot's drawn size, one pixel smaller than the pitch. */
    public static final int SLOT_SIZE = 16;

    /**
     * The topmost panel-local y any player-owned slot occupies: the 3×9 inventory's first row.
     *
     * <p>This is the boundary the information block must stay above. It is derived here rather than written
     * into the screen so the check and the {@code addSlot} calls below cannot drift apart, and it is what the
     * M6c harness reads (section U): the block's own bottom has to be strictly above this value.
     */
    public static int playerSlotsTopY() {
        return PLAYER_INVENTORY_Y;
    }

    /** The bottom panel-local y any player-owned slot reaches, hotbar row included. */
    public static int playerSlotsBottomY() {
        return HOTBAR_Y + SLOT_SIZE;
    }

    /** The rightmost panel-local x any player-owned slot reaches. */
    public static int playerSlotsRightX() {
        return PLAYER_SLOT_X + (SLOT_COLUMNS - 1) * SLOT_PITCH + SLOT_SIZE;
    }

    private final MachineControllerBlockEntity machine;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> machine.progress();
                case 1 -> machine.maxProgress();
                case 2 -> machine.isFormed() ? 1 : 0;
                case 3 -> machine.status().ordinal();
                case 4 -> machine.isRedstoneStopped() ? 1 : 0;
                case 5 -> machine.blueprintMachineId() != null ? 1 : 0;
                case 6 -> machine.parallelism();
                case 7 -> machine.maxParallelism();
                case 8 -> machine.usedTimeAvg();
                case 9 -> machine.searchUsedTimeAvg();
                // M6d-b: the interface_number_input failure, as its index in the fixed key list.
                case 10 -> MachineControllerBlockEntity.failureIndex(machine.startFailureKey());
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> machine.setClientProgress(value);
                case 1 -> machine.setClientMaxProgress(value);
                case 2 -> machine.setClientFormed(value != 0);
                case 3 -> machine.setClientStatus(value);
                case 4 -> machine.setClientRedstoneStopped(value != 0);
                case 6 -> machine.setClientParallelism(value, machine.maxParallelism());
                case 7 -> machine.setClientParallelism(machine.parallelism(), value);
                case 8 -> machine.setClientTiming(value, machine.searchUsedTimeAvg());
                case 9 -> machine.setClientTiming(machine.usedTimeAvg(), value);
                case 10 -> machine.setClientStartFailure(value);
                default -> { }
            }
        }
        @Override public int getCount() { return 11; }
    };

    public MachineControllerMenu(int id, Inventory inv, MachineControllerBlockEntity machine) {
        super(ModMenus.MACHINE_CONTROLLER.get(), id);
        this.machine = machine;
        addSlot(new BlueprintSlot(machine.items(), MachineControllerBlockEntity.BLUEPRINT_SLOT, 151, 8));
        addDataSlots(data);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public MachineControllerMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, (MachineControllerBlockEntity) inv.player.level().getBlockEntity(data.readBlockPos()));
    }

    public MachineControllerBlockEntity machine() { return machine; }
    public int progressValue() { return data.get(0); }
    public int maxProgressValue() { return Math.max(1, data.get(1)); }
    public boolean formedValue() { return data.get(2) != 0; }
    public ControllerStatus status() { return ControllerStatus.byOrdinal(data.get(3)); }
    public boolean redstoneStopped() { return data.get(4) != 0; }
    public boolean hasBlueprintMachine() { return data.get(5) != 0; }

    /** The copies the running craft settles; while idle, the machine's ceiling. */
    public int parallelism() { return Math.max(1, data.get(6)); }

    /** Whether a craft is running here, so the screen knows whether to show the two parallelism rows. */
    public boolean hasActiveCraft() { return machine.hasActiveCraft(); }

    /** The most copies this machine could settle. */
    public int maxParallelism() { return Math.max(1, data.get(7)); }

    /** Average microseconds of work per tick, for the screen's closing line. */
    public int usedTimeAvg() { return data.get(8); }

    /** Average microseconds spent searching for a recipe, for the screen's closing line. */
    public int searchUsedTimeAvg() { return data.get(9); }

    /**
     * The smart-interface failure of the last recipe search, as a translation key, or {@code null}.
     *
     * <p>Read from the block entity rather than from the container data because only the key is useful to the
     * screen; the index in slot 10 exists to make the client mirror arrive at all, and the client block entity
     * turns it back into the key through the same fixed list.
     */
    @javax.annotation.Nullable
    public String startFailureKey() {
        return machine.startFailureKey();
    }

    /**
     * The declared smart-interface types of the formed machine, in declaration order — what the screen's
     * interface block lists. The machine definition is loaded from the same data pack on both sides, so the
     * client can read it directly; only the <b>values</b> have to arrive over the wire.
     */
    public java.util.List<com.reborn.modularmachinery.machine.SmartInterfaceType> smartInterfaces() {
        return machine.declaredSmartInterfaces();
    }

    /** The value the controller currently holds for one declared type, or {@code null} when it holds none. */
    @javax.annotation.Nullable
    public Float smartInterfaceValue(String type) {
        return machine.valueOf(type);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == BLUEPRINT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_FIRST, HOTBAR_LAST + 1, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BLUEPRINT.get())) {
            // The original only accepted a blueprint into that slot, and only when it was empty.
            if (!moveItemStackTo(stack, BLUEPRINT, BLUEPRINT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index <= PLAYER_INVENTORY_LAST) {
            if (!moveItemStackTo(stack, HOTBAR_FIRST, HOTBAR_LAST + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_FIRST, PLAYER_INVENTORY_LAST + 1, false)) {
            return ItemStack.EMPTY;
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
        return machine.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }

    /** The original's {@code SlotBlueprint}: only blueprints may go in. */
    private static final class BlueprintSlot extends SlotItemHandler {
        private BlueprintSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.BLUEPRINT.get()) && super.mayPlace(stack);
        }
    }
}
