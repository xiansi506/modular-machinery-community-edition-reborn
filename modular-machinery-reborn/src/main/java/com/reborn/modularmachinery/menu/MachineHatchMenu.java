package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.HatchTier;
import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;

/**
 * One container serves every hatch family and tier, mirroring the original's {@code ContainerItemBus} /
 * {@code ContainerFluidHatch} / {@code ContainerEnergyHatch}.
 *
 * <p>The window is 176×166 with the player's 3×9 inventory at (8, 84) and the hotbar at (8, 142) — the layout
 * {@code ContainerBase} used for every hatch. Item hatches then add their slots at the coordinates
 * {@code ContainerItemBus} used, which are <b>not arbitrary</b>: each {@code inventory_<tier>.png} has that
 * tier's holes drawn into it, so the slot coordinates and the texture are one specification.
 *
 * <p>Following the original, the player's slots come first (0..35) and the hatch's own slots after them. That
 * ordering is what makes the shift-click rules below read the same way they do in the original.
 */
public final class MachineHatchMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int PLAYER_INVENTORY_FIRST = 0;
    private static final int PLAYER_INVENTORY_LAST = 26;
    private static final int HOTBAR_FIRST = 27;
    private static final int HOTBAR_LAST = 35;
    /** First index belonging to the hatch itself. */
    private static final int HATCH_FIRST = 36;

    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    /** Container data indices; also the order {@link MachineHatchBlockEntity#menuValue} expects. */
    public static final int DATA_ENERGY_STORED = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_FLUID_AMOUNT = 2;
    public static final int DATA_FLUID_CAPACITY = 3;

    /** Menu button id for "the player clicked the tank with a fluid container in hand". */
    public static final int BUTTON_INTERACT_TANK = 0;

    private final MachineHatchBlockEntity hatch;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return hatch.menuValue(index); }
        @Override public void set(int index, int value) { hatch.setMenuValue(index, value); }
        @Override public int getCount() { return 4; }
    };

    public MachineHatchMenu(int id, Inventory inv, MachineHatchBlockEntity hatch) {
        super(ModMenus.MACHINE_HATCH.get(), id);
        this.hatch = hatch;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        }

        if (hatch.kind().family() == com.reborn.modularmachinery.block.HatchKind.Family.ITEM) {
            addItemBusSlots(hatch.itemHandler(), hatch.tier());
        }
        addDataSlots(data);
    }

    public MachineHatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (MachineHatchBlockEntity) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public MachineHatchBlockEntity hatch() { return hatch; }

    public int energyStored() { return data.get(DATA_ENERGY_STORED); }
    public int energyCapacity() { return Math.max(1, data.get(DATA_ENERGY_CAPACITY)); }
    public int fluidAmount() { return data.get(DATA_FLUID_AMOUNT); }
    public int fluidCapacity() { return Math.max(1, data.get(DATA_FLUID_CAPACITY)); }

    /**
     * The original's {@code ContainerItemBus.addInventorySlots}, coordinate for coordinate. Each tier's texture
     * already has its holes cut at these positions.
     */
    private void addItemBusSlots(IItemHandler handler, HatchTier tier) {
        if (handler == null) {
            LOGGER.error("Item hatch at {} has no item storage; its screen would show no slots", hatch.getBlockPos());
            return;
        }
        switch (tier) {
            case TINY -> addSlot(new SlotItemHandler(handler, 0, 81, 30));
            case SMALL -> {
                addSlot(new SlotItemHandler(handler, 0, 70, 18));
                addSlot(new SlotItemHandler(handler, 1, 88, 18));
                addSlot(new SlotItemHandler(handler, 2, 70, 36));
                addSlot(new SlotItemHandler(handler, 3, 88, 36));
            }
            case NORMAL -> grid(handler, 2, 3, 61, 18);
            case REINFORCED -> grid(handler, 3, 3, 61, 13);
            case BIG -> grid(handler, 3, 4, 52, 18);
            case HUGE -> grid(handler, 4, 4, 53, 8);
            case LUDICROUS -> grid(handler, 4, 8, 17, 8);
            default -> LOGGER.error("Item hatch at {} has tier {}, which has no item-bus layout",
                    hatch.getBlockPos(), tier);
        }
    }

    private void grid(IItemHandler handler, int rows, int columns, int originX, int originY) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                addSlot(new SlotItemHandler(handler, row * columns + column,
                        originX + column * 18, originY + row * 18));
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_INTERACT_TANK || hatch.kind().family() != com.reborn.modularmachinery.block.HatchKind.Family.FLUID) {
            return false;
        }
        // The original sent a packet so the server would run the held container against the tank. A menu button
        // is the vanilla route for exactly this, so no custom packet is needed.
        return FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, hatch.fluidTank());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        boolean moved = false;
        if (index <= HOTBAR_LAST) {
            // Player slot: push into the hatch if there is room.
            moved = moveItemStackTo(stack, HATCH_FIRST, this.slots.size(), false);
        }
        if (!moved) {
            if (index <= PLAYER_INVENTORY_LAST) {
                if (!moveItemStackTo(stack, HOTBAR_FIRST, HOTBAR_LAST + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index <= HOTBAR_LAST) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_FIRST, PLAYER_INVENTORY_LAST + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_FIRST, HOTBAR_LAST + 1, false)) {
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
        return hatch.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }
}
