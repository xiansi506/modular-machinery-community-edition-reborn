package com.reborn.modularmachinery.client.preview;

/**
 * The blueprint panel's geometry, shared by the preview screen and the JEI category so a structure looks the
 * same in both.
 *
 * <p>Every number is the original's, taken from {@code GuiScreenBlueprint} (184×220 panel),
 * {@code MachineStructurePreviewPanel} (viewport, title, button rows, ingredient list) and {@code IngredientList}
 * (174×36 grid, nine stacks per row). The original's widget framework is not ported, so its rows are reproduced
 * as explicit rectangles here:
 *
 * <ul>
 *   <li><b>Title</b> (5, 5) 174×18. The original split this row into a 36-wide {@code prefix} label and a
 *       136-wide name label, with a 2px gap. {@code MachineDefinition} has no {@code prefix} field, so the name
 *       is centred across the whole 174 instead — the one deliberate difference inside the title row.</li>
 *   <li><b>3D viewport</b> (6, 26) 172×150, exactly {@code .setWidthHeight(172,150).setAbsXY(6,26)}.</li>
 *   <li><b>Ingredient grid</b> (5, 179) 174×36 — two rows of nine 18px slots.</li>
 *   <li><b>Button strip</b> right-aligned at y=161 with a 6px right margin, 13×13 buttons 2px apart. The
 *       original put its four bottom buttons at y=161, which is <i>inside</i> the viewport band (26..176): the
 *       strip is an overlay on the preview, not a separate region below it.</li>
 *   <li><b>Layer selector</b> right-aligned at y=44, the position of the original's {@code rightMenu}; it holds
 *       the 9×9 up/down arrows and the 9×90 scrollbar track.</li>
 * </ul>
 */
public final class PreviewLayout {

    public static final int PANEL_WIDTH = 184;
    public static final int PANEL_HEIGHT = 220;

    // ----- title row: the original's StructurePreviewTitle at (5,5), 174x18 --------------------------------

    public static final int TITLE_X = 5;
    public static final int TITLE_Y = 5;
    public static final int TITLE_WIDTH = 174;
    public static final int TITLE_HEIGHT = 18;

    /** Baseline of an 8px-tall line centred in the 18px title row, for {@code drawCenteredString}. */
    public static final int TITLE_TEXT_Y = TITLE_Y + (TITLE_HEIGHT - 8) / 2;

    // ----- 3D viewport: the original's WorldSceneRendererWidget at (6,26), 172x150 -------------------------

    public static final int PREVIEW_X = 6;
    public static final int PREVIEW_Y = 26;
    public static final int PREVIEW_WIDTH = 172;
    public static final int PREVIEW_HEIGHT = 150;

    /** The square the camera fits the structure into. */
    public static final int PREVIEW_BOX = Math.min(PREVIEW_WIDTH, PREVIEW_HEIGHT);

    // ----- ingredient grid: the original's IngredientList at (5,179), 174x36, 9 per row --------------------

    public static final int COMPONENTS_X = 5;
    public static final int COMPONENTS_Y = 179;
    public static final int COMPONENTS_WIDTH = 174;
    public static final int COMPONENTS_HEIGHT = 36;
    public static final int COMPONENT_SLOT_SIZE = 18;
    /** The original's {@code IngredientList.MAX_STACK_PER_ROW}. */
    public static final int COMPONENTS_PER_ROW = 9;
    /** Slots that fit in the 174×36 grid: two rows of nine. */
    public static final int COMPONENT_CAPACITY = COMPONENTS_PER_ROW * (COMPONENTS_HEIGHT / COMPONENT_SLOT_SIZE);
    /**
     * Inset of an item inside its 18px slot, which is where the original's {@code SlotVirtual} drew it
     * ({@code rx += 1; ry += 1}).
     *
     * <p>JEI's recipe slots describe a 16×16 ingredient rectangle rather than an 18px slot, so the JEI category
     * registers its slots at {@link #componentItemX}/{@link #componentItemY} and offsets the slot sprite by
     * {@code -COMPONENT_ITEM_INSET} to land exactly where this panel paints it.
     */
    public static final int COMPONENT_ITEM_INSET = 1;

    // ----- bottom button strip: the original's bottomMenu, right-aligned at y=161 --------------------------

    public static final int BUTTON_SIZE = 13;
    public static final int BUTTON_STRIP_Y = 161;
    /** The original's {@code setMarginRight(2)} between buttons. */
    public static final int BUTTON_SPACING = 2;
    /** The original's {@code PANEL_WIDTH - (row.getWidth() + 6)}. */
    public static final int BUTTON_RIGHT_MARGIN = 6;

    // ----- layer selector: the original's rightMenu at (184 - w - 6, 44) -----------------------------------

    public static final int LAYER_STEPPER_Y = 44;
    public static final int LAYER_ARROW_SIZE = 9;
    public static final int LAYER_STEPPER_WIDTH = 9;
    public static final int LAYER_STEPPER_X = PANEL_WIDTH - LAYER_STEPPER_WIDTH - BUTTON_RIGHT_MARGIN;
    public static final int LAYER_TRACK_WIDTH = 9;
    public static final int LAYER_TRACK_HEIGHT = 90;
    public static final int LAYER_TRACK_Y = LAYER_STEPPER_Y + LAYER_ARROW_SIZE + 3;
    public static final int LAYER_THUMB_WIDTH = 7;
    public static final int LAYER_THUMB_HEIGHT = 15;
    /** The down arrow sits under the track, as the original's column laid it out. */
    public static final int LAYER_DOWN_Y = LAYER_TRACK_Y + LAYER_TRACK_HEIGHT + 2;

    /** Text colour of the original's {@code MultiLineLabel}s. */
    public static final int LABEL_COLOR = 0x404040;

    private PreviewLayout() {
    }

    /** Total width of a right-aligned strip of {@code count} buttons, gaps included but no outer margin. */
    public static int buttonStripWidth(int count) {
        return count <= 0 ? 0 : count * BUTTON_SIZE + (count - 1) * BUTTON_SPACING;
    }

    /** Panel-space x of the {@code index}-th button of a right-aligned strip of {@code count} buttons. */
    public static int buttonX(int index, int count) {
        return PANEL_WIDTH - BUTTON_RIGHT_MARGIN - buttonStripWidth(count)
                + index * (BUTTON_SIZE + BUTTON_SPACING);
    }

    /** Panel-space x of the {@code index}-th ingredient slot, left-aligned as the original's row was. */
    public static int componentX(int index) {
        return COMPONENTS_X + (index % COMPONENTS_PER_ROW) * COMPONENT_SLOT_SIZE;
    }

    /** Panel-space y of the {@code index}-th ingredient slot. */
    public static int componentY(int index) {
        return COMPONENTS_Y + (index / COMPONENTS_PER_ROW) * COMPONENT_SLOT_SIZE;
    }

    /** Panel-space x of the item inside the {@code index}-th ingredient slot. */
    public static int componentItemX(int index) {
        return componentX(index) + COMPONENT_ITEM_INSET;
    }

    /** Panel-space y of the item inside the {@code index}-th ingredient slot. */
    public static int componentItemY(int index) {
        return componentY(index) + COMPONENT_ITEM_INSET;
    }
}
