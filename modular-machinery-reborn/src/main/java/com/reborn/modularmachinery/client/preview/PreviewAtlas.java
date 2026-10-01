package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The panel texture and every sprite region this UI draws from it.
 *
 * <p>{@code guiblueprint_new.png} is the original's own panel art, shipped byte-for-byte: the 184×220 panel
 * occupies the top-left corner and a column of button sprites runs down the right-hand side starting at
 * {@code x = 184}, in 15-pixel steps. Each 13×13 button has four states at
 * {@code x = 184, 199, 214, 229}: normal, hovered, mouse-down, and the persistent "clicked" state a toggle
 * button shows while it is on.
 *
 * <p><b>Provenance.</b> Every coordinate below is copied from the original's own widget code — mostly
 * {@code MachineStructurePreviewPanel}'s button assembly (:102-183) plus {@code IngredientList} (:39-44, :60)
 * and {@code LayerRenderScrollbar} (:30-67, :18-22) — and then checked against the shipped PNG to confirm the
 * region really holds the sprite the original expected (the chevrons at y=121/132, the plus/minus boxes at
 * y=60/75, the 18×18 slot at (184,194) and the 9×90 track at (244,0) are all present). <b>Nothing here is
 * guessed</b>; sprites the original never exposed a coordinate for are simply not used.
 */
public final class PreviewAtlas {

    /** The original's {@code guiblueprint_new.png}, shared with the JEI category's background. */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guiblueprint_new.png");

    /** The full 184×220 panel, as the original's {@code GuiScreenBlueprint} blitted it. */
    public static final Sprite PANEL = new Sprite(0, 0, PreviewLayout.PANEL_WIDTH, PreviewLayout.PANEL_HEIGHT);

    // ----- 13x13 toggles and buttons, rows taken from MachineStructurePreviewPanel -------------------------

    /** {@code machineExtraInfo} (:155-159). The original had no "clicked" art because it was not a toggle. */
    public static final Sprite MACHINE_INFO = new Sprite(184, 214, 13, 13);
    public static final Sprite MACHINE_INFO_HOVERED = new Sprite(199, 214, 13, 13);

    /** {@code resetCenter} (:178-183). */
    public static final Sprite RESET_CENTER = new Sprite(184, 229, 13, 13);
    public static final Sprite RESET_CENTER_HOVERED = new Sprite(199, 229, 13, 13);
    public static final Sprite RESET_CENTER_PRESSED = new Sprite(214, 229, 13, 13);

    /** {@code enableCycleReplaceableBlocks} (:127-137). */
    public static final Sprite CYCLE_BLOCKS = new Sprite(184, 105, 13, 13);
    public static final Sprite CYCLE_BLOCKS_HOVERED = new Sprite(199, 105, 13, 13);
    public static final Sprite CYCLE_BLOCKS_PRESSED = new Sprite(214, 105, 13, 13);
    public static final Sprite CYCLE_BLOCKS_ACTIVE = new Sprite(229, 105, 13, 13);

    /** {@code toggleLayerRender} (:110-118): 3D preview ↔ slice preview. */
    public static final Sprite LAYER_TOGGLE = new Sprite(184, 30, 13, 13);
    public static final Sprite LAYER_TOGGLE_HOVERED = new Sprite(199, 30, 13, 13);
    public static final Sprite LAYER_TOGGLE_PRESSED = new Sprite(214, 30, 13, 13);
    public static final Sprite LAYER_TOGGLE_ACTIVE = new Sprite(229, 30, 13, 13);

    // ----- 9x9 layer arrows and the 9x90 track, from LayerRenderScrollbar ----------------------------------

    public static final Sprite LAYER_UP = new Sprite(184, 121, 9, 9);
    public static final Sprite LAYER_UP_HOVERED = new Sprite(195, 121, 9, 9);
    public static final Sprite LAYER_UP_PRESSED = new Sprite(206, 121, 9, 9);
    public static final Sprite LAYER_DOWN = new Sprite(184, 132, 9, 9);
    public static final Sprite LAYER_DOWN_HOVERED = new Sprite(195, 132, 9, 9);
    public static final Sprite LAYER_DOWN_PRESSED = new Sprite(206, 132, 9, 9);

    /** The scrollbar background the original drew itself, {@code drawScrollbarBg} (:102-110). */
    public static final Sprite LAYER_TRACK = new Sprite(244, 0, 9, 90);
    /** The scrollbar thumb, {@code scrollbar.getScroll()} (:62-66). */
    public static final Sprite LAYER_THUMB = new Sprite(184, 158, 7, 15);
    public static final Sprite LAYER_THUMB_HOVERED = new Sprite(193, 158, 7, 15);
    public static final Sprite LAYER_THUMB_PRESSED = new Sprite(202, 158, 7, 15);

    // ----- ingredient grid ---------------------------------------------------------------------------------

    /** The 18×18 slot {@code IngredientList} drew for every item and fluid ({@code :60}, {@code :72}). */
    public static final Sprite INGREDIENT_SLOT = new Sprite(184, 194, 18, 18);

    private PreviewAtlas() {
    }

    /** A source rectangle in the panel texture, drawn at its own size. */
    public record Sprite(int u, int v, int width, int height) {

        /** Blits this sprite at panel coordinates {@code (x, y)}, which the caller has already offset. */
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.blit(TEXTURE, x, y, this.u, this.v, this.width, this.height);
        }
    }
}
