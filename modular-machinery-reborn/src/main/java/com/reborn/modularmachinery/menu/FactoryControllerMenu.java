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
 * The factory controller's container, the counterpart of the original's {@code ContainerFactoryController}
 * (117 lines).
 *
 * <h2>Layout — the one place the factory differs from the plain controller</h2>
 *
 * <p>The original's factory container put its blueprint slot at <b>(255, 8)</b> and its player slots at
 * {@code x = 112 + col * 18}, whereas {@code ContainerController} used (151, 8) and {@code x = 8}. Both are
 * inside a 280-wide panel: the factory panel carries the recipe-queue elements on its left, so the blueprint
 * slot and the player inventory were both shifted right by {@code 280 - 176 = 104} panels' worth of the same
 * layout. Those numbers are the original's ({@code ContainerFactoryController.java:36-38, :94, :98}) and are
 * reproduced here verbatim, because the panel texture has the slot holes drawn into it.
 *
 * <p>That last clause is checkable and is the property the screen has to keep: the PNG's hole interiors sit at
 * texels {@code 112 + 18c} × {@code 131/149/167}, {@code 189} and {@code (255, 8)} — the same coordinates — so
 * an item drawn at {@code slot.x + 8} is centred in its frame <i>only while the panel is blitted 1:1</i>. The
 * M6c harness derives the holes from the texture's pixels and requires each {@link #playerSlotRects()} entry's
 * centre, mapped through the screen's own blit arguments, to land on one; see
 * {@code FactoryControllerScreen#panelTextureSize()} for the 0.24.3 defect that broke it.
 *
 * <h2>What travels through {@link ContainerData}</h2>
 *
 * <p>Only integers can: the thread count, the machine's {@code max-threads}, the list size the scrollbar's
 * range is derived from, and the per-thread status ordinals. Everything else the screen prints — a thread's
 * recipe id and progress, the machine's name, the thread's own name — is a string and therefore comes from the
 * client block entity, which receives it in the block's update tag (see
 * {@code MachineControllerBlockEntity#getUpdateTag}). That is the M6e-1 mechanism this screen reads; the
 * integers added here are the same {@code ContainerData} mechanism 0.19.0/0.20.0/0.21.0 used for their screens.
 */
public final class FactoryControllerMenu extends AbstractContainerMenu {

    /** Slot 0 is the blueprint; the original factory container had no other slot. */
    private static final int BLUEPRINT = 0;
    private static final int PLAYER_INVENTORY_FIRST = 1;
    private static final int PLAYER_INVENTORY_LAST = 27;
    private static final int HOTBAR_FIRST = 28;
    private static final int HOTBAR_LAST = 36;

    /** The original's {@code ContainerFactoryController.java:94} / {@code :98}. */
    private static final int PLAYER_SLOT_X = 112;
    private static final int PLAYER_INVENTORY_Y = 131;
    private static final int HOTBAR_Y = 189;

    /** The original's {@code ContainerFactoryController.java:38} — the 280-wide panel's slot. */
    public static final int BLUEPRINT_SLOT_X = 255;
    public static final int BLUEPRINT_SLOT_Y = 8;

    /** {@code 18 * 9} — the width the nine player columns occupy, hotbar included. */
    public static final int SLOT_PITCH = 18;
    public static final int SLOT_COLUMNS = 9;
    /** A slot's drawn size, one pixel smaller than the pitch. */
    public static final int SLOT_SIZE = 16;
    /** The three inventory rows the player's 36 slots are split into. */
    public static final int PLAYER_INVENTORY_ROWS = 3;
    /** {@code 9 * 3}: the main inventory's slot count, the hotbar's first slot index after the blueprint. */
    public static final int PLAYER_INVENTORY_SLOTS = SLOT_COLUMNS * PLAYER_INVENTORY_ROWS;

    /**
     * The 36 player-owned slot rectangles in registration order — the three inventory rows first, then the
     * hotbar — as panel-local {@code {x, y}} pairs.
     *
     * <p>The constructor registers its slots from this table rather than re-typing the arithmetic, so "where
     * the menu puts a slot" is defined once. The M6c harness needs exactly that: it derives the slot holes the
     * panel texture actually draws (from the PNG's own pixels) and requires each of these rectangles' centres
     * to coincide with its hole's, which is the property the owner can see and the one that shipped broken.
     */
    public static int[][] playerSlotRects() {
        int[][] rects = new int[PLAYER_INVENTORY_SLOTS + SLOT_COLUMNS][];
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int col = 0; col < SLOT_COLUMNS; col++) {
                rects[row * SLOT_COLUMNS + col] = new int[] {PLAYER_SLOT_X + col * SLOT_PITCH,
                        PLAYER_INVENTORY_Y + row * SLOT_PITCH};
            }
        }
        for (int col = 0; col < SLOT_COLUMNS; col++) {
            rects[PLAYER_INVENTORY_SLOTS + col] = new int[] {PLAYER_SLOT_X + col * SLOT_PITCH, HOTBAR_Y};
        }
        return rects;
    }

    /**
     * The topmost panel-local y any player-owned slot occupies: the 3×9 inventory's first row.
     *
     * <p>Derived here rather than written into the screen, so the M6c harness's section-U check and the
     * {@code addSlot} calls below cannot drift apart.
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

    // ---- container data indices ----------------------------------------------------------------

    /** Whether the structure is formed, the original's {@code isStructureFormed()}. */
    public static final int DATA_FORMED = 0;
    /** Whether redstone is holding the factory stopped. */
    public static final int DATA_REDSTONE_STOPPED = 1;
    /** Whether the blueprint slot names a machine. */
    public static final int DATA_HAS_BLUEPRINT_MACHINE = 2;
    /** How many <b>ordinary</b> threads exist — the original's {@code getFactoryRecipeThreadList().size()}. */
    public static final int DATA_ORDINARY_THREADS = 3;
    /** How many core threads exist — the original's {@code getCoreRecipeThreads().size()}. */
    public static final int DATA_CORE_THREADS = 4;
    /** The machine's {@code max-threads}, the original's {@code getMaxThreads()}. */
    public static final int DATA_MAX_THREADS = 5;
    /** The engine's whole list size, which the scrollbar's range is computed from. */
    public static final int DATA_TOTAL_THREADS = 6;
    /** The parallelism ceiling, the original's {@code getTotalParallelism()}. */
    public static final int DATA_MAX_PARALLELISM = 7;
    /** The factory's own {@link ControllerStatus} ordinal, for the status block. */
    public static final int DATA_STATUS = 8;
    /** Whether a new ordinary thread could still be created — the original's {@code hasIdleThread()}. */
    public static final int DATA_CAN_ACCEPT = 9;
    /** Average microseconds of work per tick, the original's closing line's first number. */
    public static final int DATA_USED_TIME_AVG = 10;
    /** Average microseconds spent searching, the same line's second number. */
    public static final int DATA_SEARCH_TIME_AVG = 11;

    /**
     * How many per-thread status ordinals are mirrored. The original showed at most 6 elements per page
     * ({@code MAX_PAGE_ELEMENTS}) and the scrollbar moves that window, so 6 is the exact number the screen can
     * need; the value is spelled out here so the count is visible at the registration site.
     */
    public static final int MAX_FACTORY_THREADS_FOR_SYNC = 6;
    /** Container data index of thread {@code i}'s {@link ControllerStatus} ordinal. */
    public static final int DATA_THREAD_STATUS_BASE = 12;
    /** How many per-thread status ordinals {@link #data} carries. */
    private static final int THREAD_STATUS_COUNT = MAX_FACTORY_THREADS_FOR_SYNC;

    private static final int DATA_COUNT = DATA_THREAD_STATUS_BASE + THREAD_STATUS_COUNT;

    private final MachineControllerBlockEntity machine;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case DATA_FORMED -> machine.isFormed() ? 1 : 0;
                case DATA_REDSTONE_STOPPED -> machine.isRedstoneStopped() ? 1 : 0;
                case DATA_HAS_BLUEPRINT_MACHINE -> machine.blueprintMachineId() != null ? 1 : 0;
                case DATA_ORDINARY_THREADS -> machine.factoryEngine().ordinaryThreadCount();
                case DATA_CORE_THREADS -> machine.factoryEngine().coreThreadCount();
                case DATA_MAX_THREADS -> machine.factoryMaxThreads();
                case DATA_TOTAL_THREADS -> machine.factoryThreads().size();
                case DATA_MAX_PARALLELISM -> machine.factoryParallelCeiling();
                case DATA_STATUS -> machine.status().ordinal();
                case DATA_CAN_ACCEPT -> machine.factoryCanAccept() ? 1 : 0;
                case DATA_USED_TIME_AVG -> machine.usedTimeAvg();
                case DATA_SEARCH_TIME_AVG -> machine.searchUsedTimeAvg();
                default -> threadStatus(index - DATA_THREAD_STATUS_BASE);
            };
        }

        private int threadStatus(int slot) {
            if (slot < 0 || slot >= THREAD_STATUS_COUNT) {
                return -1;
            }
            return machine.factoryThreadStatus(slot);
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case DATA_FORMED -> machine.setClientFormed(value != 0);
                case DATA_REDSTONE_STOPPED -> machine.setClientRedstoneStopped(value != 0);
                case DATA_STATUS -> machine.setClientStatus(value);
                case DATA_MAX_THREADS -> machine.setClientFactoryMaxThreads(value);
                case DATA_USED_TIME_AVG -> machine.setClientTiming(value, machine.searchUsedTimeAvg());
                case DATA_SEARCH_TIME_AVG -> machine.setClientTiming(machine.usedTimeAvg(), value);
                default -> { }
            }
        }

        @Override public int getCount() {
            return DATA_COUNT;
        }
    };

    public FactoryControllerMenu(int id, Inventory inventory, MachineControllerBlockEntity machine) {
        super(ModMenus.FACTORY_CONTROLLER.get(), id);
        this.machine = machine;
        addSlot(new BlueprintSlot(machine.items(), MachineControllerBlockEntity.BLUEPRINT_SLOT,
                BLUEPRINT_SLOT_X, BLUEPRINT_SLOT_Y));
        addDataSlots(this.data);
        // One layout table, consumed here: the slots the menu registers and the rectangles the checks compare
        // against the texture's holes cannot drift apart.
        int[][] playerSlots = playerSlotRects();
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int col = 0; col < SLOT_COLUMNS; col++) {
                int[] rect = playerSlots[row * SLOT_COLUMNS + col];
                addSlot(new Slot(inventory, col + row * SLOT_COLUMNS + SLOT_COLUMNS, rect[0], rect[1]));
            }
        }
        for (int col = 0; col < SLOT_COLUMNS; col++) {
            int[] rect = playerSlots[PLAYER_INVENTORY_SLOTS + col];
            addSlot(new Slot(inventory, col, rect[0], rect[1]));
        }
    }

    public FactoryControllerMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, (MachineControllerBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
    }

    public MachineControllerBlockEntity machine() { return this.machine; }

    public boolean formedValue() { return this.data.get(DATA_FORMED) != 0; }
    public boolean redstoneStopped() { return this.data.get(DATA_REDSTONE_STOPPED) != 0; }
    public boolean hasBlueprintMachine() { return this.data.get(DATA_HAS_BLUEPRINT_MACHINE) != 0; }
    public int ordinaryThreads() { return Math.max(0, this.data.get(DATA_ORDINARY_THREADS)); }
    public int coreThreads() { return Math.max(0, this.data.get(DATA_CORE_THREADS)); }

    /**
     * The machine's {@code max-threads} — what the original's
     * {@code gui.factory.threads} line prints as its second number.
     */
    public int maxThreads() { return Math.max(0, this.data.get(DATA_MAX_THREADS)); }

    /** Core threads first, then ordinary ones; the drawn list order ({@code allThreads()}). */
    public int totalThreads() { return Math.max(0, this.data.get(DATA_TOTAL_THREADS)); }

    /** The parallelism ceiling, the original's {@code getTotalParallelism()} guard value. */
    public int maxParallelism() { return Math.max(1, this.data.get(DATA_MAX_PARALLELISM)); }

    /** The factory's status, the same five values the plain controller screen prints. */
    public ControllerStatus status() { return ControllerStatus.byOrdinal(this.data.get(DATA_STATUS)); }

    /** Whether another ordinary thread may still be created — the original's {@code hasIdleThread()} gate. */
    public boolean canAccept() { return this.data.get(DATA_CAN_ACCEPT) != 0; }

    public int usedTimeAvg() { return this.data.get(DATA_USED_TIME_AVG); }
    public int searchUsedTimeAvg() { return this.data.get(DATA_SEARCH_TIME_AVG); }

    /** Thread {@code slot}'s status ordinal, or {@code -1} when that slot has no thread. */
    public int threadStatusRaw(int slot) {
        return this.data.get(DATA_THREAD_STATUS_BASE + slot);
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
        return this.machine.getBlockPos().closerToCenterThan(player.position(), 8.0);
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
