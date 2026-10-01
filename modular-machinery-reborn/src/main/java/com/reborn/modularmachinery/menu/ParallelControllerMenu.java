package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.block.ParallelControllerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The parallel controller's container — empty, exactly like the original's
 * {@code ContainerParallelController} (10 lines, no slots at all).
 *
 * <p>The two numbers the screen prints travel through {@link ContainerData}, which is the pattern 0.19.0 already
 * used for the machine controller's parallelism rows. A container data slot only flows server → client, which is
 * the right direction here.
 */
public final class ParallelControllerMenu extends AbstractContainerMenu {

    /**
     * The panel heading, the original's {@code gui.parallelcontroller.title}. Declared here rather than in the
     * screen so the block entity can use it for the menu's display name without referencing a client-only class.
     */
    public static final String TITLE_KEY = "gui.modular_machinery_reborn.parallelcontroller.title";

    /** Container data index of the tier's ceiling, the original's {@code getMaxParallelism()}. */
    public static final int DATA_MAX_PARALLELISM = 0;
    /** Container data index of the value the machine adds, the original's {@code getParallelism()}. */
    public static final int DATA_PARALLELISM = 1;

    private final ParallelControllerBlockEntity controller;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return controller.menuValue(index);
        }

        @Override public void set(int index, int value) {
            controller.setMenuValue(index, value);
        }

        @Override public int getCount() {
            return 2;
        }
    };

    public ParallelControllerMenu(int id, Inventory inventory, ParallelControllerBlockEntity controller) {
        super(ModMenus.PARALLEL_CONTROLLER.get(), id);
        this.controller = controller;
        addDataSlots(this.data);
    }

    public ParallelControllerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, (ParallelControllerBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public ParallelControllerBlockEntity controller() {
        return this.controller;
    }

    public int maxParallelism() {
        return Math.max(1, this.data.get(DATA_MAX_PARALLELISM));
    }

    public int parallelism() {
        return Math.max(0, this.data.get(DATA_PARALLELISM));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // There are no slots to move anything into or out of.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.controller.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }
}
