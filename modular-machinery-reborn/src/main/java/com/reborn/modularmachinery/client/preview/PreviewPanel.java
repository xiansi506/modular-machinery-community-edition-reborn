package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.StructurePreview;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything inside the 184×220 blueprint panel: the 3D/slice preview, the ingredient grid, the title, the
 * buttons and the overlays.
 *
 * <p>The original had one {@code MachineStructurePreviewPanel} that both entry points instantiated through
 * {@code PreviewPanels.getPanel(machine, widgetGui)}, which is why the in-game screen and the JEI page looked
 * identical. This class is that panel: {@link BlueprintPreviewScreen} and
 * {@code client.jei.StructurePreviewCategory} each own one and draw it at their own origin, so there is a single
 * implementation of the layout, the buttons and the camera gestures rather than two that drift apart.
 *
 * <p><b>Coordinates.</b> Every mouse argument this class accepts and every tooltip rectangle it returns is in
 * <i>panel space</i>: {@code (0,0)} is the panel's top-left, {@code (184,220)} its bottom-right. The host adds
 * its origin only when drawing, which is what lets one implementation serve a {@code Screen} (absolute
 * coordinates) and a JEI category (whose pose JEI has already translated to the recipe origin).
 *
 * <p>That is only true if the JEI host also declares its input area as the whole panel, because JEI dispatches a
 * registered {@code IJeiInputHandler} with coordinates relative to <i>that handler's own</i> {@code getArea()}
 * origin, not relative to the recipe — see {@code StructurePreviewCategory.PreviewInputHandler}. Declaring the
 * viewport there instead handed this class its mouse positions shifted by {@code (PREVIEW_X, PREVIEW_Y) = (6,
 * 26)}, which moved the whole button strip out from under every click.
 *
 * <p><b>What is not the original's.</b> Three intentional differences are documented at their site: the title is
 * centred across the full 174px row because we have no {@code prefix} field; the ingredient grid shows an
 * overflow marker instead of scrolling; and the "extra info" button is a toggle that pins its list over the
 * viewport, where the original showed the same list as a hover tooltip only.
 */
public final class PreviewPanel {

    private static final float DRAG_SENSITIVITY = 0.7F;
    private static final float SCROLL_SENSITIVITY = 0.15F;
    /** The original's cycle period for replaceable blocks: {@code gui.preview.button.enable_cycle...} says 1.5s. */
    private static final long CYCLE_PERIOD_MILLIS = 1500L;

    private final MachineDefinition machine;
    private final StructurePreview preview;
    private final List<ItemStack> components;

    private final List<PreviewButton> buttons = new ArrayList<>();
    private final PreviewButton layerUp;
    private final PreviewButton layerDown;
    private final PreviewButton machineInfoButton;
    private final PreviewButton layerToggleButton;
    private final PreviewButton cycleButton;
    private final List<Component> machineInfoLines;

    private float yaw = StructurePreviewRenderer.DEFAULT_YAW;
    private float pitch = StructurePreviewRenderer.DEFAULT_PITCH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;

    private boolean machineInfoVisible;
    private boolean cycleBlocks;
    private boolean layerMode;
    private boolean draggingLayer;
    /** True when the press that started the current drag landed on the viewport rather than on a button. */
    private boolean draggingView;

    /** Index into {@link StructurePreview#layers()}, selected index 0 being the lowest level. */
    private int layerIndex;

    public PreviewPanel(MachineDefinition machine) {
        this.machine = machine;
        this.preview = StructurePreviews.of(machine);
        this.components = StructurePreviews.components(machine);
        this.machineInfoLines = buildMachineInfo(machine);

        int count = 4;
        // Bottom strip order, left to right. The original's own strip was
        // [placeWorldPreview, cycleReplaceable, toggleLayerRender, menu]; place-to-world and the options menu
        // are not implemented, so the two features we do have keep their relative order and the two additions
        // take the remaining places.
        this.machineInfoButton = PreviewButton.toggle("machine-info", PreviewLayout.buttonX(0, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.MACHINE_INFO, PreviewAtlas.MACHINE_INFO_HOVERED,
                // The atlas has no mouse-down sprite for this row either: the original used machineExtraInfo as a
                // hover-tooltip button, never as a toggle. Reusing the hovered sprite for the press flash and
                // tinting the normal one while the toggle is on keeps the state visible without inventing a UV.
                PreviewAtlas.MACHINE_INFO_HOVERED,
                // No "clicked" sprite exists for this row, so PreviewButton tints the normal sprite while on.
                null,
                this::machineInfoTooltip, this::onMachineInfoToggled);
        this.cycleButton = PreviewButton.toggle("cycle-blocks", PreviewLayout.buttonX(1, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.CYCLE_BLOCKS, PreviewAtlas.CYCLE_BLOCKS_HOVERED,
                PreviewAtlas.CYCLE_BLOCKS_PRESSED, PreviewAtlas.CYCLE_BLOCKS_ACTIVE,
                this::cycleTooltip, this::onCycleToggled);
        PreviewButton resetButton = PreviewButton.button("reset-center", PreviewLayout.buttonX(2, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.RESET_CENTER, PreviewAtlas.RESET_CENTER_HOVERED,
                PreviewAtlas.RESET_CENTER_PRESSED, () -> List.of(Component.translatable(
                        "gui.preview.button.reset_center.tip")), ignored -> resetView());
        this.layerToggleButton = PreviewButton.toggle("layer-toggle", PreviewLayout.buttonX(3, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.LAYER_TOGGLE, PreviewAtlas.LAYER_TOGGLE_HOVERED,
                PreviewAtlas.LAYER_TOGGLE_PRESSED, PreviewAtlas.LAYER_TOGGLE_ACTIVE,
                this::layerToggleTooltip, this::setLayerMode);
        this.buttons.add(this.machineInfoButton);
        this.buttons.add(this.cycleButton);
        this.buttons.add(resetButton);
        this.buttons.add(this.layerToggleButton);

        // The original's layer selector lived in its rightMenu at (184 - w - 6, 44), and only while slicing.
        this.layerUp = PreviewButton.button("up", PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_STEPPER_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_UP, PreviewAtlas.LAYER_UP_HOVERED,
                PreviewAtlas.LAYER_UP_PRESSED, () -> layerArrowTooltip("up"),
                ignored -> stepLayer(1), this::isLayerMode);
        this.layerDown = PreviewButton.button("down", PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_DOWN_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_DOWN, PreviewAtlas.LAYER_DOWN_HOVERED,
                PreviewAtlas.LAYER_DOWN_PRESSED, () -> layerArrowTooltip("down"),
                ignored -> stepLayer(-1), this::isLayerMode);
    }

    private void onMachineInfoToggled(boolean enabled) {
        this.machineInfoVisible = enabled;
    }

    private void onCycleToggled(boolean enabled) {
        this.cycleBlocks = enabled;
    }

    // -------------------------------------------------------------------------------------------------------
    // State used by the hosts
    // -------------------------------------------------------------------------------------------------------

    public MachineDefinition machine() {
        return this.machine;
    }

    public StructurePreview preview() {
        return this.preview;
    }

    public boolean isLayerMode() {
        return this.layerMode;
    }

    /** Whether the pinned machine-info overlay is currently shown; the machine-info button toggles it. */
    public boolean isMachineInfoVisible() {
        return this.machineInfoVisible;
    }

    /** Whether the replaceable-block art is cycling; the cycle button toggles it. */
    public boolean isCyclingBlocks() {
        return this.cycleBlocks;
    }

    /** The Y level the slice preview is showing, or {@code null} when the structure has no blocks at all. */
    public Integer selectedLayerY() {
        List<Integer> layers = this.preview.layers();
        if (layers.isEmpty()) {
            return null;
        }
        return layers.get(Mth.clamp(this.layerIndex, 0, layers.size() - 1));
    }

    private void setLayerMode(boolean enabled) {
        this.layerMode = enabled;
        this.layerToggleButton.setToggled(enabled);
        if (enabled) {
            // The original called setRenderLayer(minY) when slice mode was switched on, so start at the bottom.
            this.layerIndex = 0;
        }
    }

    private void stepLayer(int delta) {
        int last = Math.max(0, this.preview.layers().size() - 1);
        this.layerIndex = Mth.clamp(this.layerIndex + delta, 0, last);
    }

    private void resetView() {
        this.yaw = StructurePreviewRenderer.DEFAULT_YAW;
        this.pitch = StructurePreviewRenderer.DEFAULT_PITCH;
        this.zoom = 1.0F;
        this.panX = 0.0F;
        this.panY = 0.0F;
    }

    // -------------------------------------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------------------------------------

    /**
     * Draws the whole panel into the caller's pose at {@code (originX, originY)}.
     *
     * <p>The panel background itself is the host's job: the screen blits {@link PreviewAtlas#PANEL} and JEI
     * draws it through {@code getBackground()}.
     *
     * @param mouseX panel-space mouse X, used for hover states
     */
    public void render(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY) {
        render(graphics, originX, originY, mouseX, mouseY, true);
    }

    /**
     * @param drawIngredientItems whether this class paints the ingredient grid. The screen does; the JEI
     *        category does not, because there the grid is made of real recipe slots that JEI draws itself. The
     *        overflow marker is drawn either way, since no host can draw that one.
     */
    public void render(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY,
                       boolean drawIngredientItems) {
        Font font = Minecraft.getInstance().font;

        // The structure goes down first, scissored to the viewport, then the buttons and overlays are painted
        // over it: the original's bottom strip sits at y=161, inside the 26..176 viewport band, so it has to be
        // drawn after the blocks or the structure would cover it.
        StructurePreviewRenderer.clip(graphics, originX + PreviewLayout.PREVIEW_X, originY + PreviewLayout.PREVIEW_Y,
                PreviewLayout.PREVIEW_WIDTH, PreviewLayout.PREVIEW_HEIGHT);
        List<StructurePreview.Block> blocks = this.preview.blocks();
        if (this.layerMode) {
            Integer layer = selectedLayerY();
            blocks = layer == null ? List.of() : this.preview.blocksInLayer(layer);
        }
        int variant = this.cycleBlocks ? (int) (Util.getMillis() / CYCLE_PERIOD_MILLIS) : 0;
        StructurePreviewRenderer.render(graphics, this.preview, blocks, variant,
                originX + PreviewLayout.PREVIEW_X + PreviewLayout.PREVIEW_WIDTH / 2.0F + this.panX,
                originY + PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT / 2.0F + this.panY,
                PreviewLayout.PREVIEW_BOX, this.yaw, this.pitch, this.zoom);
        graphics.disableScissor();

        if (drawIngredientItems) {
            StructurePreviews.drawComponents(graphics, this.components, originX, originY, mouseX, mouseY);
        }
        StructurePreviews.drawComponentOverflow(graphics, this.components, originX, originY);

        // The original's title row was a 36px prefix label plus a 136px machine-name label. We have no prefix
        // field in MachineDefinition, so the name is centred across the whole 174px row instead.
        graphics.drawCenteredString(font, this.machine.displayName(),
                originX + PreviewLayout.PANEL_WIDTH / 2, originY + PreviewLayout.TITLE_TEXT_Y,
                PreviewLayout.LABEL_COLOR);

        for (PreviewButton button : this.buttons) {
            button.render(graphics, originX, originY, mouseX, mouseY);
        }
        if (this.layerMode) {
            drawLayerSelector(graphics, originX, originY, mouseX, mouseY);
        }
        if (this.machineInfoVisible) {
            drawMachineInfo(graphics, font, originX, originY);
        }
    }

    private void drawLayerSelector(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY) {
        PreviewAtlas.LAYER_TRACK.draw(graphics, originX + PreviewLayout.LAYER_STEPPER_X,
                originY + PreviewLayout.LAYER_TRACK_Y);

        PreviewAtlas.Sprite thumb = PreviewAtlas.LAYER_THUMB;
        if (isOverTrack(mouseX, mouseY)) {
            thumb = PreviewAtlas.LAYER_THUMB_HOVERED;
        }
        int thumbX = originX + PreviewLayout.LAYER_STEPPER_X + 1;
        int thumbY = originY + layerThumbY();
        thumb.draw(graphics, thumbX, thumbY);

        this.layerUp.render(graphics, originX, originY, mouseX, mouseY);
        this.layerDown.render(graphics, originX, originY, mouseX, mouseY);
    }

    /** Panel-space Y of the scrollbar thumb: the top of the track is the highest level, as in the original. */
    private int layerThumbY() {
        int last = this.preview.layers().size() - 1;
        int travel = PreviewLayout.LAYER_TRACK_HEIGHT - PreviewLayout.LAYER_THUMB_HEIGHT;
        int progress = last <= 0 ? 0 : (last - Mth.clamp(this.layerIndex, 0, last)) * travel / last;
        return PreviewLayout.LAYER_TRACK_Y + progress;
    }

    private void drawMachineInfo(GuiGraphics graphics, Font font, int originX, int originY) {
        int padding = 2;
        int lineHeight = font.lineHeight;
        int width = 0;
        for (Component line : this.machineInfoLines) {
            width = Math.max(width, font.width(line));
        }
        int x = originX + PreviewLayout.PREVIEW_X + padding;
        int y = originY + PreviewLayout.PREVIEW_Y + padding;
        int boxWidth = width + padding * 2;
        int boxHeight = this.machineInfoLines.size() * lineHeight + padding * 2;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xC0000000);
        for (int i = 0; i < this.machineInfoLines.size(); i++) {
            graphics.drawString(font, this.machineInfoLines.get(i), x + padding, y + padding + i * lineHeight,
                    0xFFFFFF, false);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------------------------------------

    /**
     * What the panel did with one mouse event, in panel space: which kind of element claimed it and, for the
     * widgets that have one, that element's name.
     *
     * <p>This replaced a plain boolean for one reason: a boolean could not tell "the press landed on a button"
     * apart from "the press fell through to the viewport", and the viewport covers the whole 26..176 band that
     * also contains the button strip and the layer selector, so <i>every</i> press inside that band returned
     * {@code true} whether or not it hit anything. The JEI input diagnostic now reports the kind, which is what
     * makes the next log conclusive.
     */
    public record PointerClaim(Kind kind, String name) {

        /** The kinds of element a press or a release can land on. */
        public enum Kind {
            /** Nothing interactive: the event falls through to the host. */
            NONE,
            /** One of the four 13px buttons of the bottom strip. */
            BUTTON,
            /** One of the layer selector's two 9px arrows. */
            LAYER_ARROW,
            /** The layer selector's 9x90 scrollbar track. */
            LAYER_TRACK,
            /** The 3D viewport, claimed for rotate (left), pan (right) or the middle-click reset. */
            VIEWPORT
        }

        /** Nothing claimed; the host may act on the event itself. */
        public static final PointerClaim NONE = new PointerClaim(Kind.NONE, "");
        /** The layer selector's track. */
        public static final PointerClaim LAYER_TRACK = new PointerClaim(Kind.LAYER_TRACK, "layer-track");
        /** The 3D viewport. */
        public static final PointerClaim VIEWPORT = new PointerClaim(Kind.VIEWPORT, "viewport");

        static PointerClaim button(PreviewButton button) {
            return new PointerClaim(Kind.BUTTON, button.name());
        }

        static PointerClaim layerArrow(PreviewButton arrow) {
            return new PointerClaim(Kind.LAYER_ARROW, arrow.name());
        }

        /** Whether an element was claimed, i.e. whether the host should treat the event as handled. */
        public boolean claimed() {
            return this.kind != Kind.NONE;
        }

        /** The claimed element as {@code button machine-info} / {@code layer-arrow up} / {@code viewport} / {@code none}. */
        public String describe() {
            switch (this.kind) {
                case BUTTON:
                    return "button " + this.name;
                case LAYER_ARROW:
                    return "layer-arrow " + this.name;
                case LAYER_TRACK:
                    return "layer-track";
                case VIEWPORT:
                    return "viewport";
                default:
                    return "none";
            }
        }
    }

    /**
     * Handles a press at panel coordinates.
     *
     * <p>Buttons are tested before the viewport because the button strip is an overlay on the viewport; a press
     * on a button must not also start a rotation. Nothing is acted on here except the layer track, which follows
     * the press so that a drag can continue from it.
     *
     * <p>The claim is what the JEI entry point reports as its simulated "would you handle this?" answer, so it
     * has to be truthful: a press the panel will act on (a button, a layer arrow, the layer track, or the
     * viewport for a rotate/pan/middle-click-reset gesture) claims it, anything else reports
     * {@link PointerClaim#NONE} so the click still reaches whatever is underneath. The viewport is a claim in its
     * own right and is reported as {@link Kind#VIEWPORT} rather than as a hit, so a press that only landed in the
     * 3D view can never be mistaken for a press that found a button.
     *
     * @return which element claimed the press, never {@code null}
     */
    public PointerClaim mousePressed(double mouseX, double mouseY, int button) {
        // Only one button at a time may be armed, so a press anywhere resets the others.
        for (PreviewButton candidate : this.buttons) {
            candidate.disarm();
        }
        this.layerUp.disarm();
        this.layerDown.disarm();
        this.draggingLayer = false;
        this.draggingView = false;

        if (button == 0) {
            for (PreviewButton candidate : this.buttons) {
                if (candidate.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.button(candidate);
                }
            }
            if (this.layerMode) {
                if (this.layerUp.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.layerArrow(this.layerUp);
                }
                if (this.layerDown.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.layerArrow(this.layerDown);
                }
                if (isOverTrack(mouseX, mouseY)) {
                    this.draggingLayer = true;
                    setLayerFromTrack(mouseY);
                    return PointerClaim.LAYER_TRACK;
                }
            }
            return claimViewport(mouseX, mouseY);
        }
        // Right-drag pans, and a middle click over the viewport is the original's reset-center gesture, which is
        // resolved on release. Both claim the press over the viewport so the host does not treat it as a click on
        // something else. Arming draggingView for the middle button is inert: mouseDragged only acts on buttons 0
        // and 1, so a middle-button drag still turns or pans nothing.
        if (button == 1 || button == 2) {
            return claimViewport(mouseX, mouseY);
        }
        return PointerClaim.NONE;
    }

    /** Arms the viewport drag when the press is inside the 3D view, and reports the claim either way. */
    private PointerClaim claimViewport(double mouseX, double mouseY) {
        if (!isOverPreview(mouseX, mouseY)) {
            return PointerClaim.NONE;
        }
        this.draggingView = true;
        return PointerClaim.VIEWPORT;
    }

    /** True while a scrollbar-thumb drag is in progress, so the host can stop forwarding to other gestures. */
    public boolean isDraggingLayer() {
        return this.draggingLayer;
    }

    /**
     * Handles a release: fires the button that was pressed, if the release is still on it, and resets the
     * middle-click "reset center" gesture.
     *
     * <p>At most one button can be armed, because a press disarms the others, so at most one can fire here; the
     * others are still asked so that their {@code armed} flag is cleared. The claim names the widget that fired,
     * which is what lets the JEI diagnostic print a press/release pair that reads as
     * {@code simulate -> button machine-info} followed by {@code execute -> button machine-info}.
     */
    public PointerClaim mouseReleased(double mouseX, double mouseY, int button) {
        PreviewButton fired = null;
        for (PreviewButton candidate : this.buttons) {
            if (candidate.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = candidate;
            }
        }
        if (this.layerMode) {
            if (this.layerUp.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = this.layerUp;
            }
            if (this.layerDown.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = this.layerDown;
            }
        }
        boolean endedLayerDrag = this.draggingLayer;
        this.draggingLayer = false;
        this.draggingView = false;
        if (fired != null) {
            return PointerClaim.button(fired);
        }
        if (endedLayerDrag) {
            return PointerClaim.LAYER_TRACK;
        }
        if (button == 2 && isOverPreview(mouseX, mouseY)) {
            // Middle click is the original's reset gesture as well as its reset button.
            resetView();
            return PointerClaim.VIEWPORT;
        }
        return PointerClaim.NONE;
    }

    /**
     * Handles a mouse drag.
     *
     * <p>Left drag turns the structure — horizontal motion changes {@code pitch}, vertical motion changes the
     * clamped {@code yaw}, following the original's {@code WorldSceneRendererWidget} naming. Right drag pans.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingLayer) {
            setLayerFromTrack(mouseY);
            return true;
        }
        if (!this.draggingView) {
            // The drag started on a button or outside the viewport; do not turn the structure.
            return false;
        }
        if (button == 0) {
            this.pitch += (float) dragX * DRAG_SENSITIVITY;
            this.yaw = Mth.clamp(this.yaw - (float) dragY * DRAG_SENSITIVITY,
                    StructurePreviewRenderer.MIN_YAW, StructurePreviewRenderer.MAX_YAW);
            return true;
        }
        if (button == 1) {
            // The original's right-drag moved the camera target; pixel panning is equivalent here and stable
            // at every zoom level.
            this.panX += (float) dragX;
            this.panY += (float) dragY;
            return true;
        }
        return false;
    }

    /**
     * Handles the wheel over the viewport: zoom in 3D mode, level stepping in slice mode.
     *
     * <p>Scrolling up selects the level above, matching the up arrow, which is also the direction the original's
     * scrollbar moved for a positive wheel delta.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isOverPreview(mouseX, mouseY)) {
            return false;
        }
        if (this.layerMode) {
            stepLayer(delta > 0 ? 1 : -1);
        } else {
            this.zoom = Mth.clamp(this.zoom + (float) delta * SCROLL_SENSITIVITY,
                    StructurePreviewRenderer.MIN_ZOOM, StructurePreviewRenderer.MAX_ZOOM);
        }
        return true;
    }

    public boolean isOverPreview(double mouseX, double mouseY) {
        return mouseX >= PreviewLayout.PREVIEW_X
                && mouseX < PreviewLayout.PREVIEW_X + PreviewLayout.PREVIEW_WIDTH
                && mouseY >= PreviewLayout.PREVIEW_Y
                && mouseY < PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT;
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return this.layerMode
                && mouseX >= PreviewLayout.LAYER_STEPPER_X
                && mouseX < PreviewLayout.LAYER_STEPPER_X + PreviewLayout.LAYER_TRACK_WIDTH
                && mouseY >= PreviewLayout.LAYER_TRACK_Y
                && mouseY < PreviewLayout.LAYER_TRACK_Y + PreviewLayout.LAYER_TRACK_HEIGHT;
    }

    /** Maps a click or drag on the track to the nearest level, with the top of the track as the highest one. */
    private void setLayerFromTrack(double mouseY) {
        int last = this.preview.layers().size() - 1;
        if (last <= 0) {
            this.layerIndex = 0;
            return;
        }
        int travel = PreviewLayout.LAYER_TRACK_HEIGHT - PreviewLayout.LAYER_THUMB_HEIGHT;
        double offset = mouseY - PreviewLayout.LAYER_TRACK_Y - PreviewLayout.LAYER_THUMB_HEIGHT / 2.0;
        double progress = Mth.clamp(offset / travel, 0.0, 1.0);
        this.layerIndex = Mth.clamp((int) Math.round((1.0 - progress) * last), 0, last);
    }

    // -------------------------------------------------------------------------------------------------------
    // Tooltips
    // -------------------------------------------------------------------------------------------------------

    /**
     * The tooltip lines for a panel-space mouse position, or an empty list when nothing there has one.
     *
     * <p>Both hosts use this: the screen renders the result itself, and the JEI category returns it from
     * {@code IRecipeCategory.getTooltip}. Ingredient tooltips are deliberately not included — JEI provides them
     * for its own recipe slots, and the screen asks {@link #componentAt} for the item directly.
     */
    public List<Component> tooltipLines(double mouseX, double mouseY) {
        for (PreviewButton button : this.buttons) {
            if (button.contains(mouseX, mouseY)) {
                return button.tooltipLines();
            }
        }
        if (this.layerMode) {
            if (this.layerUp.contains(mouseX, mouseY)) {
                return layerArrowTooltip("up");
            }
            if (this.layerDown.contains(mouseX, mouseY)) {
                return layerArrowTooltip("down");
            }
            if (isOverTrack(mouseX, mouseY)) {
                return List.of(Component.translatable("gui.preview.button.layer_render_scrollbar.tip"),
                        layerStateLine());
            }
        }
        if (isOverPreview(mouseX, mouseY)) {
            return List.of(Component.translatable("gui.modular_machinery_reborn.preview.hint"));
        }
        return List.of();
    }

    /** The item under a panel-space position in the ingredient grid, or {@code null}. */
    public ItemStack componentAt(double mouseX, double mouseY) {
        return StructurePreviews.componentAt(this.components, mouseX, mouseY);
    }

    private List<Component> machineInfoTooltip() {
        return this.machineInfoLines;
    }

    private List<Component> cycleTooltip() {
        if (this.cycleBlocks) {
            return List.of(Component.translatable("gui.preview.button.disable_cycle_replaceable_blocks.tip"));
        }
        return List.of(Component.translatable("gui.preview.button.enable_cycle_replaceable_blocks.tip.0"),
                Component.translatable("gui.preview.button.enable_cycle_replaceable_blocks.tip.1"));
    }

    private List<Component> layerToggleTooltip() {
        String key = this.layerMode
                ? "gui.preview.button.toggle_3d_render.tip"
                : "gui.preview.button.toggle_layer_render.tip";
        return List.of(Component.translatable(key));
    }

    private List<Component> layerArrowTooltip(String direction) {
        return List.of(Component.translatable("gui.preview.button.layer_render_scrollbar." + direction + ".tip"),
                layerStateLine());
    }

    /**
     * The original's {@code layer_render_scrollbar.state.tip} arguments: the level being drawn, relative to the
     * controller, over the {@code getMaxScroll()} the original passed — which for its scrollbar was the pattern's
     * highest Y. Our selector walks the levels that actually hold blocks, so the numerator is one of those Y
     * values; the denominator keeps the original's meaning.
     */
    private Component layerStateLine() {
        Integer layer = selectedLayerY();
        return Component.translatable("gui.preview.button.layer_render_scrollbar.state.tip",
                layer == null ? 0 : layer, this.preview.max().getY());
    }

    /**
     * M6e-2: the very list {@link #buildMachineInfo} produces, one string per line, with no {@code Font} and no
     * client state involved.
     *
     * <p>It exists because the two rows this release adds are the whole point of touching the panel, and the
     * acceptance rule forbids concluding anything from "a number appeared in a GUI". The offline harness cannot
     * open a window, so it drives this method with real {@link MachineDefinition}s and asserts the resulting
     * row sequence: <b>which</b> rows appear, in which order, and under which guard. It is the same list the
     * panel draws, because it is produced by the same method — the strings are only unwrapped for printing.
     */
    public static List<String> machineInfoRows(MachineDefinition machine) {
        List<String> rows = new ArrayList<>();
        for (Component line : buildMachineInfo(machine)) {
            // Language data is only loaded inside a running client, so getString() answers the key itself here;
            // the arguments are appended so the numbers the rows would print are assertable too.
            Object[] args = line.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                    ? translatable.getArgs() : new Object[0];
            rows.add(args.length == 0 ? line.getString()
                    : line.getString() + " " + java.util.Arrays.toString(args));
        }
        return List.copyOf(rows);
    }

    /**
     * The original's {@code getMachineExtraInfo} list, minus the fields this project does not have.
     *
     * <p>M6c made two more of them computable, so they are printed here in the original's order — internal
     * parallelism, then max parallelism — straight off {@link MachineDefinition}, which is machine data and
     * needs no world. M6e-2 made the remaining two computable as well, so they now join the list in the
     * original's own position: right after max parallelism and before the blueprint line, each under the
     * original's guard.
     *
     * <p>The dynamic-pattern line needs dynamic patterns, which are still unimplemented, and stays omitted for
     * the reason it always was: printing a zero would claim the machine has none when the field is not read at
     * all. The length/height/width line uses {@code pattern().size()} and the controller line the controller's Y
     * inside the pattern box, exactly as the original computed them.
     */
    private static List<Component> buildMachineInfo(MachineDefinition machine) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.preview.button.machine_info"));
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.0"));
        BlockPos size = machine.pattern().size();
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.1",
                size.getX(), size.getY(), size.getZ()));
        lines.add(Component.translatable("gui.preview.button.machine_info.controller_y_pos",
                Math.abs(machine.pattern().min().getY())));
        // The original only listed these two when the machine was not at the defaults
        // (MachineStructurePreviewPanel:298-307: internal parallelism > 0, max parallelism != the config
        // default), which is why a machine that has not opted in stays quiet.
        if (machine.internalParallelism() > 0) {
            lines.add(Component.translatable("gui.preview.button.machine_info.internal_parallelism",
                    machine.internalParallelism()));
        }
        if (machine.maxParallelism() != MachineDefinition.DEFAULT_MAX_PARALLELISM) {
            lines.add(Component.translatable("gui.preview.button.machine_info.max_parallelism",
                    machine.maxParallelism()));
        }
        // M6e-2. The original's guards, verbatim (MachineStructurePreviewPanel:308-317): the thread count only
        // for a machine that has a factory at all, the core-thread count only when the preset is not empty — so
        // a machine with no factory stays as quiet as it was, and a factory with an empty preset reports one
        // number rather than a misleading zero.
        if (machine.hasFactory()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.max_threads",
                    machine.maxThreads()));
        }
        if (!machine.coreThreads().isEmpty()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.core_threads",
                    machine.coreThreads().size()));
        }
        if (machine.requiresBlueprint()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.requires_blueprint"));
        }
        return List.copyOf(lines);
    }
}
