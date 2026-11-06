package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.DynamicPattern;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineModifier;
import com.reborn.modularmachinery.machine.MachineMultiBlockModifier;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.reborn.modularmachinery.machine.StructurePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
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

    /** 预览的默认结构：动态段取各自动态模式的下限（见 {@link #initialDynamicPatternSize}）。 */
    public static StructurePreview of(MachineDefinition machine) {
        return of(machine, initialDynamicPatternSize(machine));
    }

    /** {@link #of(MachineDefinition)} for a chosen dynamic size — what the panel rebuilds when the player picks one. */
    public static StructurePreview of(MachineDefinition machine, int dynamicPatternSize) {
        return of(machine, dynamicPatternSize, controllerState(machine));
    }

    static StructurePreview of(MachineDefinition machine, int dynamicPatternSize, BlockState controller) {
        return StructurePreview.of(previewRenderPattern(machine, dynamicPatternSize), controller);
    }

    /** 预览渲染结构：本体、动态段和单块/多块 modifier 的候选方块。 */
    public static MachinePattern previewRenderPattern(MachineDefinition machine, int dynamicPatternSize) {
        MachinePattern merged = previewPattern(machine, dynamicPatternSize);
        for (MachineModifier modifier : machine.modifiers()) {
            merged = merged.withMerged(MachinePattern.builder()
                    .add(modifier.offset(), modifier.accepted()).build());
        }
        for (MachineMultiBlockModifier modifier : machine.multiBlockModifiers()) {
            merged = merged.withMerged(modifier.pattern());
        }
        return merged;
    }

    /**
     * Modifier 清单：每条对应旧版可点击的一个 UpgradeIngredient；多块项涵盖它的整个结构。
     * 坐标均相对控制器，预览固定朝向北，因此不旋转。
     */
    public static List<ModifierIngredient> modifierIngredients(MachineDefinition machine) {
        List<ModifierIngredient> result = new java.util.ArrayList<>();
        for (MachineModifier modifier : machine.modifiers()) {
            if (modifier.accepted().isEmpty()) continue;
            ItemStack icon = itemFor(modifier.accepted().get(0).representativeState());
            if (!icon.isEmpty()) result.add(new ModifierIngredient(icon, List.of(modifier.offset()), modifier.description()));
        }
        for (MachineMultiBlockModifier modifier : machine.multiBlockModifiers()) {
            List<BlockPos> positions = List.copyOf(modifier.pattern().positions().keySet());
            if (positions.isEmpty()) continue;
            List<BlockMatcher> first = modifier.pattern().positions().get(positions.get(0));
            ItemStack icon = first == null || first.isEmpty() ? ItemStack.EMPTY
                    : itemFor(first.get(0).representativeState());
            if (!icon.isEmpty()) result.add(new ModifierIngredient(icon, positions, modifier.description()));
        }
        return List.copyOf(result);
    }

    public record ModifierIngredient(ItemStack icon, List<BlockPos> positions, String description) {
        public ModifierIngredient {
            icon = icon.copy();
            positions = List.copyOf(positions);
            description = description == null ? "" : description;
        }
    }

    private static ItemStack itemFor(BlockState state) {
        return com.reborn.modularmachinery.item.PropertyBlockItem.stackForState(state);
    }

    /**
     * 预览用的结构：基座加上按 {@code dynamicPatternSize} 展开的动态段。
     *
     * <p>动态结构的长度在世界里是量出来的，而预览里没有世界，所以长度必须由玩家在界面上选
     * （旧版的 {@code WorldSceneRendererWidget.setDynamicPatternSize} 就是这么做的）。
     * 本方法负责把「基座 + 各段」合成一个结构，与运行时 {@code MachineRegistry} 的合并是同一个意思，
     * 只是这里的大小来自界面而不是来自世界。
     *
     * <p><strong>预览里的动态段按朝向 0（北）展开。</strong>预览画的是一个固定朝向的投影，
     * 而动态段的偏移本来就要按控制器朝向旋转；这里不转，是因为预览没有控制器朝向可言。
     * 玩家在世界里把机器朝别的方向摆，段会跟着转向——那是运行时的行为，不是预览要回答的问题。
     *
     * @param dynamicPatternSize 每个动态模式重复几节；机器没有动态结构时忽略
     */
    public static MachinePattern previewPattern(MachineDefinition machine, int dynamicPatternSize) {
        MachinePattern merged = machine.pattern();
        for (DynamicPattern dynamic : machine.dynamicPatterns()) {
            merged = merged.withMerged(dynamic.expand(dynamicPatternSize, 0));
        }
        return merged;
    }

    /**
     * 预览里应当用几节起手：旧版把渲染器的初始大小夹到<b>第一个</b>动态模式的 {@code minSize}
     * （{@code WorldSceneRendererWidget.java:191}），本方法照做。
     *
     * <p>机器没有动态结构时返回 0——那个值不会被用到，返回 0 只是为了让调用者少一处判空。
     */
    public static int initialDynamicPatternSize(MachineDefinition machine) {
        if (machine.dynamicPatterns().isEmpty()) {
            return 0;
        }
        return Math.max(0, machine.dynamicPatterns().get(0).minSize());
    }

    /**
     * 把玩家选的大小夹进<b>第一个</b>动态模式允许的范围，与旧版
     * {@code Math.min(Math.max(pattern.getMinSize(), size), pattern.getMaxSize())} 一致
     * （{@code WorldSceneRendererWidget.java:196}）。
     */
    public static int clampDynamicPatternSize(MachineDefinition machine, int size) {
        if (machine.dynamicPatterns().isEmpty()) {
            return 0;
        }
        DynamicPattern first = machine.dynamicPatterns().get(0);
        return Math.min(Math.max(first.minSize(), size), first.maxSize());
    }

    /**
     * Every block item a machine is built from, merged with the same counts as the original BlockArray.
     *
     * <p>The controller is inserted first because the original preview adds it to the pattern before building
     * its descriptive ingredient list. A matcher list contributes its first representative state, matching the
     * original sample-state behaviour for replaceable positions.
     */
    public static List<ItemStack> components(MachineDefinition machine) {
        return components(machine, initialDynamicPatternSize(machine));
    }

    /** Search index includes alternatives and zero-length dynamic sections, without expanding maxSize. */
    public static List<ItemStack> indexedComponents(MachineDefinition machine) {
        return indexedComponents(machine, controllerState(machine));
    }

    static List<ItemStack> indexedComponents(MachineDefinition machine, BlockState controller) {
        Map<Item,ItemStack> items = new LinkedHashMap<>();
        addComponent(items,controller);
        java.util.function.Consumer<MachinePattern> addPattern = pattern -> {
            if (pattern != null) for (var accepted : pattern.positions().values()) for (BlockMatcher matcher : accepted)
                addComponent(items,matcher.representativeState());
        };
        addPattern.accept(machine.pattern());
        for (DynamicPattern dynamic : machine.dynamicPatterns()) {
            addPattern.accept(dynamic.part()); addPattern.accept(dynamic.endPart());
        }
        for (MachineModifier modifier : machine.modifiers()) for (BlockMatcher matcher : modifier.accepted())
            addComponent(items,matcher.representativeState());
        for (MachineMultiBlockModifier modifier : machine.multiBlockModifiers()) addPattern.accept(modifier.pattern());
        return items.values().stream().map(stack->stack.copyWithCount(1)).toList();
    }

    /**
     * {@link #components(MachineDefinition)} for a chosen dynamic size.
     *
     * <p>读者会问「为什么材料清单要跟着大小变」：动态段本身是由方块组成的，玩家加长一节就要多备一节的料，
     * 所以清单必须与预览画出来的结构一致。旧版的 {@code IngredientList} 也是从它当时渲染的那个结构取的。
     */
    public static List<ItemStack> components(MachineDefinition machine, int dynamicPatternSize) {
        return components(machine, dynamicPatternSize, controllerState(machine));
    }

    static List<ItemStack> components(MachineDefinition machine, int dynamicPatternSize, BlockState controller) {
        Map<Item, ItemStack> stacks = new LinkedHashMap<>();
        addComponent(stacks, controller);
        for (List<BlockMatcher> accepted : previewPattern(machine, dynamicPatternSize).positions().values()) {
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
        ItemStack material = itemFor(state);
        if (material.isEmpty()) {
            return;
        }
        Item item = material.getItem();
        ItemStack stack = stacks.get(item);
        if (stack == null) {
            stacks.put(item, material);
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
