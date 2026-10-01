package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.client.preview.PreviewAtlas;
import com.reborn.modularmachinery.client.preview.PreviewLayout;
import com.reborn.modularmachinery.client.preview.PreviewPanel;
import com.reborn.modularmachinery.client.preview.StructurePreviews;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * JEI's structure preview: one entry per machine, drawn with the same panel, camera and buttons as the in-game
 * blueprint screen.
 *
 * <p>The category title is the original's {@code jei.category.preview} — 「结构预览」 / "Structure Preview". The
 * project's own namespaced key {@code jei.modular_machinery_reborn.structure.title} carries the identical text
 * and is what the code reads; {@code jei.category.preview} is defined alongside it so the wording stays
 * traceable to the original's language file. (No collision is possible: that key belongs to the 1.12.2 mod,
 * which cannot be installed next to this one.)
 *
 * <p><b>Slots.</b> The original's {@code CategoryStructurePreview} registered the machine's components as item
 * stacks at {@code (-999999,-999999)} — hidden, existing only so that searching an item finds structures that
 * use it — and then drew the visible ingredient grid itself on the panel. Ours registers the visible grid as
 * <b>real</b> {@link RecipeIngredientRole#INPUT} slots at the same coordinates the screen paints, which is a
 * deliberate improvement: hover tooltips, ingredient focus and recipe lookup all work, instead of a hand-drawn
 * grid that answers to nothing. The hidden {@link RecipeIngredientRole#CATALYST} slot is kept as well, so a
 * component that does not fit the two-row grid is still in JEI's index.
 *
 * <p><b>Interaction.</b> JEI hands a category local coordinates for {@code draw}, {@code getTooltip} and
 * {@code handleInput}, and allows an input handler per area, so the panel's buttons, the layer selector, the
 * rotate/pan drag and the wheel work here exactly as they do on the screen. JEI's input router delivers the mouse
 * <i>press</i> as a simulated input ("would you handle this?") and the matching <i>release</i> as an executed one,
 * which is the press/release pair the shared buttons are built around — see {@code PreviewInputHandler} for the
 * bytecode this was verified against.
 *
 * <p>The one trap in that API is that the input handler's {@code getArea()} is not only a filter: it is also the
 * handler's coordinate origin, so an area that does not start at the recipe origin shifts every event. Read the
 * {@code PreviewInputHandler} class comment before changing {@code getArea()} — the viewport rectangle that used
 * to be declared there is what made every button on this page unclickable.
 */
public final class StructurePreviewCategory implements IRecipeCategory<MachineDefinition> {

    public static final RecipeType<MachineDefinition> TYPE =
            RecipeType.create(ModularMachineryReborn.MOD_ID, "structure", MachineDefinition.class);

    /** Where the original parked its hidden ingredient stacks. */
    private static final int HIDDEN_SLOT = -10000;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final Map<MachineDefinition, PreviewPanel> panels = new WeakHashMap<>();

    public StructurePreviewCategory(IGuiHelper helper) {
        this.background = helper.createDrawable(PreviewAtlas.TEXTURE, PreviewAtlas.PANEL.u(), PreviewAtlas.PANEL.v(),
                PreviewLayout.PANEL_WIDTH, PreviewLayout.PANEL_HEIGHT);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(com.reborn.modularmachinery.item.ModItems.BLUEPRINT.get()));
        // The panel's own 18x18 slot sprite, so the JEI page's grid looks like the screen's. Without this JEI
        // would draw its standard light slot, which does not appear anywhere on this panel's art.
        this.slotBackground = helper.createDrawable(PreviewAtlas.TEXTURE, PreviewAtlas.INGREDIENT_SLOT.u(),
                PreviewAtlas.INGREDIENT_SLOT.v(), PreviewAtlas.INGREDIENT_SLOT.width(),
                PreviewAtlas.INGREDIENT_SLOT.height());
    }

    @Override public RecipeType<MachineDefinition> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.modular_machinery_reborn.structure.title"); }
    @Override public IDrawable getBackground() { return background; }
    @Override @Nullable public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return PreviewLayout.PANEL_WIDTH; }
    @Override public int getHeight() { return PreviewLayout.PANEL_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineDefinition machine, IFocusGroup focuses) {
        List<ItemStack> components = StructurePreviews.components(machine);
        if (components.isEmpty()) {
            return;
        }
        // Keep every component in JEI's index, including the ones that do not fit in the two-row grid.
        builder.addSlot(RecipeIngredientRole.CATALYST, HIDDEN_SLOT, HIDDEN_SLOT).addItemStacks(components);

        // Real, visible slots at the grid's own coordinates. These replace the original's hand-painted row.
        // JEI's slot coordinate is the top-left of its 16x16 ingredient area, while this panel's 18px slot
        // sprite holds the item inset by one pixel, so the slot is registered at the item position and the
        // sprite hung one pixel up and left of it — the same -1/-1 JEI's own plugins use with an 18px slot
        // drawable. The result is pixel-identical to what StructurePreviews.drawComponents paints on the screen.
        int shown = StructurePreviews.visibleComponentCount(components.size());
        for (int i = 0; i < shown; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT,
                            PreviewLayout.componentItemX(i), PreviewLayout.componentItemY(i))
                    .setBackground(this.slotBackground,
                            -PreviewLayout.COMPONENT_ITEM_INSET, -PreviewLayout.COMPONENT_ITEM_INSET)
                    .addItemStack(components.get(i));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineDefinition machine, IFocusGroup focuses) {
        builder.addInputHandler(new PreviewInputHandler(panel(machine)));
    }

    @Override
    public void draw(MachineDefinition machine, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        // JEI calls this with the pose already translated to RecipeLayout's area, which is exactly the panel's
        // origin, and with panel-space mouse coordinates (RecipeLayout.drawRecipe subtracts area.getX()/getY(),
        // the same pair the input handler subtracts), so the origin is simply zero here. The ingredient grid is
        // drawn by JEI (real recipe slots), so the panel only paints everything else.
        panel(machine).render(graphics, 0, 0, mouseX, mouseY, false);
    }

    /**
     * JEI's tooltip hook. Coordinates here are panel space, the same space {@link #draw} receives:
     * {@code RecipeLayout.drawOverlays} subtracts the same {@code area} the pose is translated by.
     *
     * <p>Only the panel's chrome answers: over the ingredient grid this returns nothing, so JEI's own slot
     * tooltips are the ones the player sees.
     */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineDefinition machine, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        tooltip.addAll(panel(machine).tooltipLines(mouseX, mouseY));
    }

    private PreviewPanel panel(MachineDefinition machine) {
        return this.panels.computeIfAbsent(machine, PreviewPanel::new);
    }

    /**
     * Forwards JEI's events to the shared panel, in panel space.
     *
     * <p><b>The area is an origin, not just a filter.</b> JEI does not hand a registered input handler
     * recipe-local coordinates. {@code RecipeLayoutInputHandler.handleInput} (verified against JEI 15.49's
     * bytecode) does this:
     *
     * <pre>
     *   0: recipeLayout.isMouseOver(mouseX, mouseY)      // false -&gt; return false
     *  14: Rect2i rect = recipeLayout.getRect()          // RecipeLayout.area, origin = the recipe's (0,0)
     *  23: localX = mouseX - rect.getX()
     *  33: localY = mouseY - rect.getY()
     *  85: ScreenRectangle area = handler.getArea()
     *  91: MathUtil.contains(area, localX, localY)       // outside the handler's own area -&gt; try the next one
     * 104: x = localX - area.getPosition().getX()        // the handler's AREA ORIGIN is subtracted as well
     * 115: y = localY - area.getPosition().getY()
     * 134: handler.handleInput(x, y, input)
     * </pre>
     *
     * <p>{@code mezz.jei.library.gui.OffsetJeiInputHandler} does the same thing twice over — subtract its own
     * offset, test the delegate's area in that offset space, then subtract the area's origin before delegating —
     * which settles the convention: <b>the coordinates a handler receives are relative to its own
     * {@code getArea()}</b>, and the area is tested in the parent's (here: recipe-local) space. An area whose
     * origin is not the recipe origin therefore shifts every event even when it is never left.
     *
     * <p>Drawing and tooltips have no such offset. {@code RecipeLayout.drawRecipe} translates the pose by
     * {@code area} and passes {@code mouseX - area.getX(), mouseY - area.getY()} to
     * {@code IRecipeCategory.draw}; {@code RecipeLayout.drawOverlays} computes the same pair and passes it to
     * {@code IRecipeCategory.getTooltip}. Both are therefore in <i>panel space</i>: {@code (0,0)} is the panel's
     * top-left, which is what {@link StructurePreviewCategory#draw} and {@link PreviewPanel} assume.
     *
     * <p>So this handler must declare the whole panel, because {@code RecipeLayout}'s area is exactly
     * {@code (0, 0, IRecipeCategory.getWidth(), getHeight())} — the RecipeLayout constructor builds
     * {@code new ImmutableRect2i(0, 0, recipeCategory.getWidth(), recipeCategory.getHeight())} — and declaring
     * the viewport rectangle (6, 26, 172, 150) here instead made JEI hand the panel every mouse position shifted
     * by {@code -(PREVIEW_X, PREVIEW_Y) = -(6, 26)}. That is the bug this page shipped with: the button strip at
     * y=161..174 never saw a press (a click on it arrived at y≈141, the visible 20-21px gap), the shifted press
     * still fell inside the 26..176 viewport band so the simulate pass claimed the viewport and returned
     * {@code true} regardless, and the execute pass then found nothing armed and returned {@code false}. The
     * whole panel also rejects nothing JEI would otherwise have sent, since every recipe-local point is inside it.
     */
    private static final class PreviewInputHandler implements IJeiInputHandler {

        /**
         * The handler's area, and therefore its coordinate origin: the whole panel, so that the mouse positions
         * JEI dispatches are panel space — identical to the space {@code draw} and {@code getTooltip} receive.
         */
        private static final ScreenRectangle AREA =
                new ScreenRectangle(0, 0, PreviewLayout.PANEL_WIDTH, PreviewLayout.PANEL_HEIGHT);

        private final PreviewPanel panel;

        private PreviewInputHandler(PreviewPanel panel) {
            this.panel = panel;
        }

        @Override
        public ScreenRectangle getArea() {
            return AREA;
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE) {
                return false;
            }
            int button = input.getKey().getValue();
            // Verified against JEI 15.49's own bytecode:
            //
            //   ForgeUserInput.fromEvent(ScreenEvent.MouseButtonPressed)
            //       -> new UserInput(key, x, y, 0, InputType.SIMULATE)
            //   ForgeUserInput.fromEvent(ScreenEvent.MouseButtonReleased)
            //       -> new UserInput(key, x, y, 0, InputType.EXECUTE)
            //   UserInput.isSimulate() == (inputType == InputType.SIMULATE)
            //
            // A press is therefore a SIMULATE input and the matching release is an EXECUTE input - one pass per
            // physical event, not two passes over one event. Press during the simulate pass, release during the
            // execute pass: the action fires exactly once per click, and the button state machine stays on the
            // single code path the in-game screen uses. UserInputRouter.handleExecuteClick pops the handler that
            // answered true during the simulate pass out of its pending map and routes the execute to that
            // handler alone, so the press must claim its element for the release to arrive here at all.
            //
            // The claim is the truthful "would I handle this?" answer: a button, a layer arrow, the layer track
            // or the viewport for a rotate/pan/middle-click-reset all claim the press, and everything else -
            // the title row, the ingredient grid - reports none, so that click still reaches JEI's own slots.
            // The claimed element is logged, because a bare boolean could not distinguish "hit a button" from
            // "claimed the viewport", and the viewport covers the strip.
            if (input.isSimulate()) {
                PreviewPanel.PointerClaim claim = this.panel.mousePressed(mouseX, mouseY, button);
                logInput("simulate", mouseX, mouseY, button, claim);
                return claim.claimed();
            }
            PreviewPanel.PointerClaim claim = this.panel.mouseReleased(mouseX, mouseY, button);
            logInput("execute", mouseX, mouseY, button, claim);
            return claim.claimed();
        }

        @Override
        public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key key,
                                          double dragX, double dragY) {
            if (key.getType() != InputConstants.Type.MOUSE) {
                return false;
            }
            return this.panel.mouseDragged(mouseX, mouseY, key.getValue(), dragX, dragY);
        }

        @Override
        public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta) {
            return this.panel.mouseScrolled(mouseX, mouseY, scrollDelta);
        }
    }

    /**
     * Diagnostics for the JEI page, printed unconditionally on purpose.
     *
     * <p>It was gated behind {@code -Dmodular_machinery_reborn.preview.debug=true}, but the launcher passed that
     * flag as a <i>game</i> argument instead of a JVM argument, the system property was never set, the log stayed
     * silent and an entire test run proved nothing. The JEI input path cannot be exercised headlessly: this line
     * is the only way to see whether JEI reaches the panel, with which coordinates and which pass, when a click
     * appears to do nothing. The cost is one INFO line per mouse press and release on the JEI page. Remove it
     * once the page is confirmed working in game.
     *
     * <p>Reads as: the pass, the mouse position JEI dispatched, the button and what the panel claimed. Since the
     * handler's area is the whole panel, that position is panel space, and the geometry is repeated on every line
     * so a coordinate-space regression is visible without cross-referencing anything: a line such as
     * {@code mouse=(123.75, 141.37) -> none | buttonStrip y=[161, 174)} means the press arrived about 20px above
     * the strip, which is exactly how the old {@code (6, 26)} origin shift presented itself.
     */
    private static void logInput(String pass, double mouseX, double mouseY, int button,
                                 PreviewPanel.PointerClaim claim) {
        ModularMachineryReborn.LOGGER.info(
                "[preview] JEI {}: mouse=({}, {}) button={} -> {}"
                        + " | panel space, handler area = whole panel {}x{} at (0, 0);"
                        + " buttonStrip y=[{}, {}) viewport y=[{}, {})",
                pass, mouseX, mouseY, button, claim.describe(),
                PreviewLayout.PANEL_WIDTH, PreviewLayout.PANEL_HEIGHT,
                PreviewLayout.BUTTON_STRIP_Y, PreviewLayout.BUTTON_STRIP_Y + PreviewLayout.BUTTON_SIZE,
                PreviewLayout.PREVIEW_Y, PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT);
    }
}
