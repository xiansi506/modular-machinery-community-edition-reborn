package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.recipe.FluidRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.IngredientArrayRequirement;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The JEI page of <b>one machine's</b> recipes, reproduced from the original's {@code CategoryDynamicRecipe} +
 * {@code DynamicRecipeWrapper} for the 1.20.1 JEI this mod ships against.
 *
 * <p>The single largest difference from 0.16.2 is the category's identity: the original registered one category
 * per machine, titled with the machine's localized name, sized to that machine's requirements
 * ({@code ModIntegrationJEI#registerCategories}: {@code for (DynamicMachine machine :
 * MachineRegistry.getRegistry()) { … registry.addRecipeCategories(new CategoryDynamicRecipe(machine)); }}, and
 * {@code CategoryDynamicRecipe#getTitle} returned {@code machine.getLocalizedName()}). This class is that
 * category, one instance per machine, and {@link ModularMachineryJeiPlugin} builds one per machine.
 *
 * <p><b>Why that is possible in JEI 15.49.</b> JEI does not register categories at plugin-discovery time. Its
 * {@code mezz.jei.forge.startup.StartEventObserver} starts the whole plugin pipeline from a
 * {@code ClientPlayerNetworkEvent.LoggingIn} — and, when {@code shouldWaitForRecipes()} is true, only after the
 * matching {@code RecipesUpdatedEvent} — by calling {@code JeiStarter#start()}, which is what runs
 * {@code PluginLoader.createRecipeCategories}. Both of those events are long after the client's resource reload,
 * so {@code MachineRegistry} is already filled by {@code MachineLoader} when this constructor runs. A client with
 * no machine definitions at all (a dedicated-server client, where the registry is empty) still gets a category
 * for every machine named by the synced recipes, titled with the registry name instead of a localized name.
 *
 * <p><b>What the original drew</b> (references are into
 * {@code _mmce-src/…/common/integration/recipe/} and {@code …/common/crafting/}):
 *
 * <ul>
 *   <li>{@code CategoryDynamicRecipe#buildRecipeComponents} laid every requirement out on one horizontal band:
 *       inputs on the left in descending {@code getComponentHorizontalSortingOrder()} (energy 1000, fluid 100,
 *       items 10), the process arrow, then outputs in ascending order. {@link MachineRecipeLayout} is that
 *       measurement, and it is per machine because {@code getWidth()}/{@code getHeight()} are.</li>
 *   <li>The page background was a <b>blank</b> drawable of exactly the measured size
 *       ({@code getBackground()} returned {@code jeiHelpers.getGuiHelper().createBlankDrawable(maxPoint.x,
 *       realHeight)}), so the art underneath was JEI's own recipe border — the
 *       {@code single_recipe_background.png} nine-slice, RGB(198,198,198) in <b>both</b> JEI 1.12.2 and JEI
 *       15.49, drawn at {@code (-4,-4,width+8,height+8)} by {@code RecipeLayout}. JEI 15.49 does the identical
 *       thing when {@code needsRecipeBorder()} is left at its {@code true} default, so this class overrides
 *       neither and the backdrop matches the original pixel for pixel.</li>
 *   <li>Every cell was the original's own art from {@code jeirecipeicons_ce.png}: an 18x18 opaque cell at
 *       {@code (54,0)} for both items and fluid tanks (the original's {@code PART_INVENTORY_CELL} and
 *       {@code PART_TANK_SHELL_BACKGROUND} are the same region), the transparent {@code (0,0)} tank shell as the
 *       fluid renderer's overlay, and an 18x54 energy column at {@code (36,0)} with the filled {@code (18,0)}
 *       column over it. See {@link MachineRecipeAtlas}.</li>
 *   <li>The process arrow was {@code (72,0,22,15)} with the {@code (72,15,22,15)} overlay revealed left to right,
 *       {@code ceil((tick + partialTick) / duration * 22)} pixels wide.</li>
 *   <li>{@code DynamicRecipeWrapper#drawInfo} wrote the requirement tips underneath the band at x=4 in white with
 *       a drop shadow, bottom-aligned against the measured height.</li>
 *   <li>The processing time was <b>not</b> a fixed label: {@code DynamicRecipeWrapper#getTooltipStrings}
 *       returned the two duration strings only while the mouse was inside
 *       {@code rectangleProcessArrow} — the 22x15 arrow rectangle.</li>
 *   <li>Chance and amount ranges lived in the slot tooltips
 *       ({@code JEIComponentItem#onJEIHoverTooltip}), never on the page itself.</li>
 * </ul>
 *
 * <p><b>Remaining divergences.</b>
 *
 * <ol>
 *   <li>The quantity on a slot is JEI's own stack-count overlay, because this mod shows an input's amount by
 *       sizing the ingredient stack to it. The original instead rendered the item bare and painted the number
 *       itself ({@code IngredientItemStackRenderer#renderRequirementOverlyIntoGUI}), which abbreviated anything
 *       from 1000 up to {@code 1K} at half scale and drew {@code min~max} for a range. For every amount below
 *       1000 — including the eight 泰拉钢锭 of the recipe this was reported against — the two are identical.</li>
 *   <li>Fluid amounts are not painted on the tank. The original's {@code FluidStackRenderer(1, false, 18, 18,
 *       PART_TANK_SHELL)} drew the fluid and the shell and nothing else, and JEI 1.20.1's fluid tooltip still
 *       carries the exact "1000 mB" reading, so nothing is lost — but 0.16.2 overlaid the number, and that
 *       overlay is gone.</li>
 *   <li>No fuel / smart-interface / recipe-tooltip rows: this mod's recipe model has no such fields. The energy
 *       rows are the original's own two, and they are the only ones its data can produce.</li>
 *   <li>Chance stays tooltip-only, exactly as in the original, so a 25% side output is only visible on hover.</li>
 * </ol>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    /** The x the original wrote its tip block at: {@code fr.drawStringWithShadow(tip, 4, offsetY, 0xFFFFFF)}. */
    private static final int TEXT_X = 4;

    /**
     * White with a drop shadow, exactly as {@code DynamicRecipeWrapper#drawInfo} drew it. This is not an
     * approximation of the original's colours on a different backdrop: JEI 1.12.2's and JEI 15.49's
     * {@code single_recipe_background.png} are both RGB(198,198,198) in the centre, so the original's white
     * shadowed text sat on this very panel.
     */
    private static final int TEXT_COLOR = 0xFFFFFF;

    private final IGuiHelper helper;
    private final ResourceLocation machineId;
    private final Component title;
    private final IDrawable icon;
    private final List<MachineRecipe> recipes;
    private MachineRecipeLayout layout;
    private IDrawable background;

    /**
     * @param machineId the machine this category belongs to; it is also the category's recipe type id
     * @param machine   the loaded definition, or {@code null} on a client that has none (the recipe-synced
     *                  machine names still get a category, with the registry name as their title)
     * @param recipes   every recipe of this machine JEI is about to be given, which fixes the page size
     */
    public MachineRecipeCategory(IGuiHelper helper, ResourceLocation machineId, @Nullable MachineDefinition machine,
                                 List<MachineRecipe> recipes) {
        this.helper = helper;
        this.machineId = machineId;
        // The original's title was machine.getLocalizedName(); MachineDefinition#displayName is its counterpart,
        // and MachineLoader falls back to the registry path when a definition carries no 'localizedname'.
        this.title = machine != null ? machine.displayName() : Component.literal(machineId.getPath());
        Item controller = ModBlocks.controllerItemFor(machineId);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(controller));
        this.recipes = List.copyOf(recipes);
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return ModularMachineryJeiPlugin.typeFor(this.machineId);
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    /** The original's blank drawable, sized to this machine's own measured layout. */
    @Override
    public IDrawable getBackground() {
        MachineRecipeLayout measured = layout();
        if (this.background == null
                || this.background.getWidth() != measured.width()
                || this.background.getHeight() != measured.height()) {
            this.background = this.helper.createBlankDrawable(measured.width(), measured.height());
        }
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return layout().width();
    }

    @Override
    public int getHeight() {
        return layout().height();
    }

    /**
     * {@code IRecipeCategory#getWidth}/{@code getHeight} are fixed when JEI asks for them, so the page is
     * measured once, lazily, from every recipe this machine has — the same pass
     * {@code CategoryDynamicRecipe}'s constructor made over {@code RecipeRegistry.getRecipesFor(machine)}.
     * Measuring lazily also means the client's font and level are certainly up.
     */
    private MachineRecipeLayout layout() {
        if (this.layout == null) {
            this.layout = MachineRecipeLayout.measure(this.recipes, Minecraft.getInstance().font);
        }
        return this.layout;
    }

    // ------------------------------------------------------------------ slots

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        MachineRecipeLayout measured = layout();
        addItemSlots(builder, recipe, measured.inputItems(), RecipeIngredientRole.INPUT);
        addFluidSlots(builder, recipe, measured.inputFluids(), RecipeIngredientRole.INPUT);
        addItemSlots(builder, recipe, measured.outputItems(), RecipeIngredientRole.OUTPUT);
        addFluidSlots(builder, recipe, measured.outputFluids(), RecipeIngredientRole.OUTPUT);
    }

    /**
     * Item cells.
     *
     * <p>Geometry: the original's {@code RecipeLayoutPart.Item} is an 18x18 cell at {@code (x, y)} with the item
     * rendered at {@code (x+1, y+1)} ({@code getRendererPaddingX()/Y() == 1}). JEI's slot coordinate is the top
     * left of the <i>ingredient</i>, and a slot background given the offset {@code (-1,-1)} is drawn at
     * {@code (x-1, y-1)} — which is exactly what {@code setStandardSlotBackground()} does. So the slot goes at
     * the item position and the cell sprite one pixel up and left of it, and the pair lands on the original's
     * pixels.
     *
     * <p>Quantities: an input's stack is sized to the requirement's amount, which is what makes JEI draw the
     * count. An output's stack already carries {@code maxAmount}, and is never copied down to 1. Both follow the
     * original's {@code RequirementItem#asJEIIORequirementList}.
     */
    private static void addItemSlots(IRecipeLayoutBuilder builder, MachineRecipe recipe,
                                     List<MachineRecipeLayout.Cell> cells, RecipeIngredientRole role) {
        boolean output = role == RecipeIngredientRole.OUTPUT;
        List<MachineRequirement> requirements =
                MachineRecipeLayout.components(recipe, ioTypeOf(role), MachineRecipeLayout.Kind.ITEM);
        int count = Math.min(requirements.size(), cells.size());
        for (int i = 0; i < count; i++) {
            MachineRequirement requirement = requirements.get(i);
            // An ingredient array is a group of alternatives and occupies one cell, showing each alternative.
            // It is not an ItemRequirement, which is why this dispatch exists: casting it would crash the page.
            List<ItemStack> stacks;
            float chance;
            boolean amountRange = false;
            if (requirement instanceof IngredientArrayRequirement array) {
                stacks = array.entryStacksFlat();
                chance = array.chance();
            } else {
                ItemRequirement item = (ItemRequirement) requirement;
                stacks = output ? List.of(item.outputStack()) : inputStacks(item);
                chance = item.chance();
                amountRange = true;
            }
            if (stacks.isEmpty() || stacks.get(0).isEmpty()) {
                continue;
            }
            MachineRecipeLayout.Cell cell = cells.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(role, cell.x() + 1, cell.y() + 1)
                    .setBackground(MachineRecipeAtlas.CELL, -1, -1);
            if (output) {
                slot.addItemStack(stacks.get(0));
            } else {
                // Every alternative goes into the one slot, which is what the original's
                // RequirementIngredientArray#asJEIIORequirementList produced for a cell.
                slot.addItemStacks(stacks);
            }
            ItemRequirement itemForRange = amountRange ? (ItemRequirement) requirement : null;
            ItemRequirement finalItemForRange = itemForRange;
            slot.addRichTooltipCallback((view, tooltip) -> {
                MachineRecipeText.addChanceTooltip(!output, tooltip, chance);
                if (finalItemForRange != null) {
                    MachineRecipeText.addAmountRangeTooltip(!output, tooltip, finalItemForRange);
                } else {
                    MachineRecipeText.addIngredientArrayTooltip(tooltip);
                }
            });
        }
    }

    /**
     * Fluid cells: JEI's fluid tank inside the original's cell sprite, with the original's tank shell over it.
     *
     * <p>{@code PART_TANK_SHELL_BACKGROUND} and {@code PART_INVENTORY_CELL} are the same region of the sheet, so
     * the background offset is {@code (0,0)} rather than the item cells' {@code (-1,-1)}: the original rendered
     * the fluid across the whole 18x18 cell ({@code FluidStackRenderer(1, false, 18, 18, …)} with renderer
     * padding 0), not in a 16x16 inset. The shell was that renderer's <i>overlay</i>, drawn after the fluid, and
     * JEI 1.20.1 draws a slot's overlay after its ingredient too, so {@code setOverlay} reproduces it — and it
     * is the only overlay this slot can have, which is why the fluid amount is tooltip-only here.
     */
    private static void addFluidSlots(IRecipeLayoutBuilder builder, MachineRecipe recipe,
                                      List<MachineRecipeLayout.Cell> cells, RecipeIngredientRole role) {
        List<MachineRequirement> requirements =
                MachineRecipeLayout.components(recipe, ioTypeOf(role), MachineRecipeLayout.Kind.FLUID);
        int count = Math.min(requirements.size(), cells.size());
        for (int i = 0; i < count; i++) {
            FluidRequirement fluid = (FluidRequirement) requirements.get(i);
            MachineRecipeLayout.Cell cell = cells.get(i);
            int amount = fluid.amount();
            IRecipeSlotBuilder slot = builder.addSlot(role, cell.x(), cell.y())
                    .setBackground(MachineRecipeAtlas.CELL, 0, 0)
                    // The capacity is the requirement itself and showCapacity is false, so the tank draws full
                    // and the tooltip reads "1000 mB" — the same picture as the original's capacity of 1.
                    .setFluidRenderer(amount, false, MachineRecipeLayout.CELL, MachineRecipeLayout.CELL)
                    .addFluidStack(fluid.fluidType().getFluid(), amount);
            slot.setOverlay(MachineRecipeAtlas.TANK_SHELL, 0, 0);
            slot.addRichTooltipCallback((view, tooltip) -> MachineRecipeText.addChanceTooltip(
                    role != RecipeIngredientRole.OUTPUT, tooltip, fluid.chance()));
        }
    }

    /**
     * The amount-carrying item stacks of an input requirement.
     *
     * <p>This is where the page used to lose its numbers: an {@link Ingredient} has no count, so handing it
     * straight to JEI showed every input as a single item. {@code RequirementItem#asJEIIORequirementList} did
     * the equivalent in the original — it copied each matching stack and set its count to the requirement's
     * amount.
     */
    private static List<ItemStack> inputStacks(ItemRequirement item) {
        Ingredient ingredient = item.ingredient();
        if (ingredient == null) {
            return List.of();
        }
        ItemStack[] matches = ingredient.getItems();
        int amount = item.amount();
        List<ItemStack> stacks = new ArrayList<>(matches.length);
        for (ItemStack match : matches) {
            if (!match.isEmpty()) {
                stacks.add(match.copyWithCount(amount));
            }
        }
        return stacks;
    }

    private static IOType ioTypeOf(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.OUTPUT ? IOType.OUTPUT : IOType.INPUT;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * {@code CategoryDynamicRecipe#drawExtras} plus {@code DynamicRecipeWrapper#drawInfo}, in their own order:
     * the static arrow and every part's background first, then the animated arrow over it and the tip block.
     */
    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        MachineRecipeLayout measured = layout();

        MachineRecipeAtlas.ARROW.draw(graphics, measured.arrowX(), measured.arrowY());

        // One energy part per energy requirement, and every one of them drew its side's summed rate
        // (drawInfo: inputComponents.stream().filter(Energy.class::isInstance).forEach(part -> part.drawEnergy(mc,
        // finalTotalEnergyIn))).
        long energyIn = MachineRecipeText.energyPerTick(recipe, IOType.INPUT);
        long energyOut = MachineRecipeText.energyPerTick(recipe, IOType.OUTPUT);
        for (MachineRecipeLayout.Cell cell : measured.inputEnergy()) {
            drawEnergyCell(graphics, cell, energyIn);
        }
        for (MachineRecipeLayout.Cell cell : measured.outputEnergy()) {
            drawEnergyCell(graphics, cell, energyOut);
        }

        int revealed = revealedArrowWidth(recipe);
        if (revealed > 0) {
            MachineRecipeAtlas.ARROW_ACTIVE.drawPartial(graphics, measured.arrowX(), measured.arrowY(), revealed);
        }

        drawTips(graphics, recipe, measured.height());
    }

    /**
     * {@code RecipeLayoutPart.Energy}: the 18x54 background always, the 18x54 filled column only while that
     * side's summed rate is positive ({@code drawEnergy} guarded on {@code energy > 0}).
     */
    private static void drawEnergyCell(GuiGraphics graphics, MachineRecipeLayout.Cell cell, long energyPerTick) {
        MachineRecipeAtlas.ENERGY_EMPTY.draw(graphics, cell.x(), cell.y());
        if (energyPerTick > 0L) {
            MachineRecipeAtlas.ENERGY_FILLED.draw(graphics, cell.x(), cell.y());
        }
    }

    /**
     * {@code DynamicRecipeWrapper#drawInfo}'s animation:
     * {@code pxPart = ceil((clientTick % duration + partialTick) / duration * PART_PROCESS_ARROW_ACTIVE.xSize)},
     * revealed left to right over the static arrow.
     */
    private static int revealedArrowWidth(MachineRecipe recipe) {
        int total = recipe.recipeTime();
        if (total <= 0) {
            return MachineRecipeLayout.ARROW_WIDTH;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int tick = minecraft.gui.getGuiTicks() % total;
        float progress = (tick + minecraft.getFrameTime()) / (float) total;
        return Mth.clamp(Mth.ceil(progress * MachineRecipeLayout.ARROW_WIDTH), 0, MachineRecipeLayout.ARROW_WIDTH);
    }

    /**
     * The tip block, bottom-aligned against the measured height exactly as {@code drawInfo} aligned it: it walks
     * backwards from {@code realHeight} subtracting one {@code LINE_HEIGHT} per line and one
     * {@code SPLIT_HEIGHT} per group, adds one split back, then draws forwards.
     */
    private static void drawTips(GuiGraphics graphics, MachineRecipe recipe, int contentHeight) {
        Font font = Minecraft.getInstance().font;
        List<List<String>> groups = MachineRecipeText.tipGroupsWithRecipeTooltip(recipe);

        int y = contentHeight;
        for (List<String> group : groups) {
            y -= MachineRecipeLayout.LINE_HEIGHT * group.size();
            y -= MachineRecipeLayout.SPLIT_HEIGHT;
        }
        y += MachineRecipeLayout.SPLIT_HEIGHT;

        for (List<String> group : groups) {
            for (String line : group) {
                graphics.drawString(font, line, TEXT_X, y, TEXT_COLOR, true);
                y += MachineRecipeLayout.LINE_HEIGHT;
            }
            y += MachineRecipeLayout.SPLIT_HEIGHT;
        }
    }

    /**
     * The original showed the processing time <b>only</b> while the mouse was inside {@code rectangleProcessArrow}
     * — a {@code Rectangle} over the 22x15 arrow sprite. Nothing else on the page says how long a craft takes,
     * and neither does this one.
     */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineRecipe recipe, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        MachineRecipeLayout measured = layout();
        if (mouseX >= measured.arrowX() && mouseX < measured.arrowX() + MachineRecipeLayout.ARROW_WIDTH
                && mouseY >= measured.arrowY() && mouseY < measured.arrowY() + MachineRecipeLayout.ARROW_HEIGHT) {
            tooltip.add(MachineRecipeText.duration(recipe));
        }
    }
}
