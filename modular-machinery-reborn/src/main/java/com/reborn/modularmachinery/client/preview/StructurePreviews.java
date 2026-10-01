package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.StructurePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds what a preview shows: the structure itself, and the blocks a player has to gather for it.
 *
 * <p>A machine with its own controller draws that one at the origin, so the preview shows the block the player
 * actually places; everything else draws the generic {@code machine_controller}.
 *
 * <p>The ingredient grid is the original's {@code IngredientList}: 174×36 at (5, 179), nine 18px slots per row,
 * two rows, left-aligned, with each slot drawn from the panel texture's own slot sprite at (184,194). The
 * original made that list scrollable; this panel does not have a scrolling widget, so a definition with more
 * than {@value PreviewLayout#COMPONENT_CAPACITY} distinct blocks uses the last slot for a {@code +N} marker
 * instead. Every component still reaches JEI's index through a hidden slot, so lookup is unaffected.
 */
public final class StructurePreviews {

    private StructurePreviews() {
    }

    public static StructurePreview of(MachineDefinition machine) {
        return StructurePreview.of(machine.pattern(), controllerState(machine));
    }

    /**
     * Every block item a machine is built from, merged with the same counts as the original BlockArray.
     *
     * <p>The controller is inserted first because the original preview adds it to the pattern before building
     * its descriptive ingredient list. A matcher list contributes its first representative state, matching the
     * original sample-state behaviour for replaceable positions.
     */
    public static List<ItemStack> components(MachineDefinition machine) {
        Map<Item, ItemStack> stacks = new LinkedHashMap<>();
        addComponent(stacks, controllerState(machine));
        for (List<BlockMatcher> accepted : machine.pattern().positions().values()) {
            if (!accepted.isEmpty()) {
                addComponent(stacks, accepted.get(0).representativeState());
            }
        }
        return List.copyOf(stacks.values());
    }

    /** How many components fit in the 174×36 grid, reserving the last slot for the overflow marker. */
    public static int visibleComponentCount(int total) {
        return total > PreviewLayout.COMPONENT_CAPACITY ? PreviewLayout.COMPONENT_CAPACITY - 1 : total;
    }

    /**
     * Draws the ingredient grid's items and slot art, plus the original's white hover overlay.
     *
     * <p>The screen calls this. The JEI category does not: there the same grid is made of real recipe slots
     * (placed at the item positions this method draws into) so its hover, focus and lookup behaviour stays
     * available, and JEI draws them.
     *
     * @param mouseX panel-space mouse X, for the hover overlay
     */
    public static void drawComponents(GuiGraphics graphics, List<ItemStack> components,
                                      int originX, int originY, double mouseX, double mouseY) {
        int shown = visibleComponentCount(components.size());
        for (int i = 0; i < shown; i++) {
            int x = originX + PreviewLayout.componentX(i);
            int y = originY + PreviewLayout.componentY(i);
            PreviewAtlas.INGREDIENT_SLOT.draw(graphics, x, y);
            // The original's SlotVirtual rendered its stack inset by one pixel inside the 18px slot.
            graphics.renderItem(components.get(i),
                    originX + PreviewLayout.componentItemX(i), originY + PreviewLayout.componentItemY(i));
            if (isInsideSlot(mouseX, mouseY, i)) {
                // The original's SlotVirtual.drawHoverOverlay: 16x16 of (255,255,255,150) inset by one pixel.
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x96FFFFFF);
            }
        }
    }

    /**
     * Draws the {@code +N} marker in the last grid slot when a definition has more distinct blocks than two rows
     * of nine can show.
     *
     * <p>Both hosts call this: the marker is not a slot in JEI, so nothing else would draw it. The original's
     * list scrolled instead; this panel has no scrolling widget.
     */
    public static void drawComponentOverflow(GuiGraphics graphics, List<ItemStack> components,
                                             int originX, int originY) {
        int shown = visibleComponentCount(components.size());
        if (components.size() <= shown) {
            return;
        }
        int x = originX + PreviewLayout.componentX(shown);
        int y = originY + PreviewLayout.componentY(shown);
        PreviewAtlas.INGREDIENT_SLOT.draw(graphics, x, y);
        graphics.drawString(Minecraft.getInstance().font, Component.literal("+" + (components.size() - shown)),
                x + 2, y + 5, 0xFF404040, false);
    }

    /**
     * The component under a panel-space mouse position.
     *
     * <p>Returns {@code null} for empty slots and for the overflow marker, which carries no item. Only the
     * screen needs this: in JEI the grid is made of real recipe slots and JEI resolves the hover itself.
     */
    public static ItemStack componentAt(List<ItemStack> components, double mouseX, double mouseY) {
        for (int i = 0; i < visibleComponentCount(components.size()); i++) {
            if (isInsideSlot(mouseX, mouseY, i)) {
                return components.get(i);
            }
        }
        return null;
    }

    private static boolean isInsideSlot(double mouseX, double mouseY, int index) {
        int x = PreviewLayout.componentX(index);
        int y = PreviewLayout.componentY(index);
        return mouseX >= x && mouseX < x + PreviewLayout.COMPONENT_SLOT_SIZE
                && mouseY >= y && mouseY < y + PreviewLayout.COMPONENT_SLOT_SIZE;
    }

    private static void addComponent(Map<Item, ItemStack> stacks, BlockState state) {
        if (state == null || state.isAir()) {
            return;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = stacks.get(item);
        if (stack == null) {
            stacks.put(item, new ItemStack(item));
        } else {
            stack.grow(1);
        }
    }

    private static BlockState controllerState(MachineDefinition machine) {
        return controllerBlock(machine).defaultBlockState();
    }

    private static Block controllerBlock(MachineDefinition machine) {
        RegistryObject<Block> bound = ModBlocks.boundControllerBlock(machine.id().getPath());
        if (bound != null) {
            return bound.get();
        }
        return ModBlocks.MACHINE_CONTROLLER.get();
    }
}
