package com.reborn.modularmachinery.client;

import java.util.List;

/**
 * The screen geometry both information-block screens share, as pure arithmetic.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>The controller and the factory controller draw the same thing — a block of wrapped text inside a panel
 * that also carries the player's slots — and both learned the same lesson in 0.24.2: <b>the text block must be
 * unable to write outside its region.</b> The original 1.12.2 code had no such guard; it appended lines to a
 * cursor and let the closing {@code Avg: …} line land wherever the cursor ended up. With the original's own
 * (short) content that was fine, so the overflow path was never exercised. 0.24.0's smart-data-interface rows
 * made the block taller and the closing line was pushed down onto the player-inventory label and the panel's
 * bottom edge — exactly what the owner saw in game, and what no offline check had ever asked about.
 *
 * <p>This class is the single place that decides how many lines fit. Both screens compute their layout through
 * {@link #block} and then draw <i>that</i>, and the M6c harness asserts the same predicate on the same numbers,
 * so "the drawing code and the check agree" is a property of the code rather than of a hand-copied metric table.
 * See {@code ParallelCraftCheck} section U.
 *
 * <h2>The mechanism</h2>
 *
 * <p>The caller hands in one {@link Measured} per drawn line, in drawing order, each carrying the blank space
 * that follows it. {@link #block} returns how many of those lines fit before the region's end, where the last
 * drawn line ends, and where the closing line must go so that it, too, stays inside. The closing line is the
 * part that shipped broken, so it is non-negotiable: its position is clamped to the region's floor whenever the
 * cursor would otherwise push it out.
 *
 * <p>All values are in the <b>scaled text space</b> the caller draws in — panel-local pixels divided by the
 * caller's text scale — and all are relative to the panel origin, which is the only origin either screen uses.
 */
public final class ScreenLayout {

    private ScreenLayout() {
    }

    /** One drawn line: its height, and the blank space that follows it, both in scaled text units. */
    public static final class Measured {
        public final int height;
        public final int gap;

        public Measured(int height, int gap) {
            this.height = height;
            this.gap = gap;
        }
    }

    /** The outcome of laying a payload out inside its region. */
    public static final class Block {
        /** The scaled y the first line is drawn at. */
        public final int startY;
        /** How many of the payload's lines fit. */
        public final int drawnLines;
        /** How many lines the payload has. */
        public final int requestedLines;
        /** The scaled y one unit past the last line that will be drawn. */
        public final int payloadEndY;
        /** The scaled y the closing line is drawn at. */
        public final int footerY;
        /** The closing line's own height, in the same units. */
        public final int footerHeight;
        /** The scaled y nothing in this block may reach. */
        public final int availableEnd;

        Block(int startY, int drawnLines, int requestedLines, int payloadEndY, int footerY,
                int footerHeight, int availableEnd) {
            this.startY = startY;
            this.drawnLines = drawnLines;
            this.requestedLines = requestedLines;
            this.payloadEndY = payloadEndY;
            this.footerY = footerY;
            this.footerHeight = footerHeight;
            this.availableEnd = availableEnd;
        }

        /** Whether the region had to drop payload lines to stay inside itself. */
        public boolean truncated() {
            return this.drawnLines < this.requestedLines;
        }

        /** The scaled y one unit past the closing line, i.e. the whole block's bottom. */
        public int blockEndY() {
            return this.footerY + this.footerHeight;
        }

        /** Whether every line this block will draw ends strictly above the region's boundary. */
        public boolean insideRegion() {
            return this.payloadEndY <= this.availableEnd && this.blockEndY() <= this.availableEnd;
        }

        @Override
        public String toString() {
            return "Block[start=" + this.startY + ", drawn=" + this.drawnLines + "/" + this.requestedLines
                    + ", payloadEnd=" + this.payloadEndY + ", footer=" + this.footerY + ", end="
                    + blockEndY() + ", limit=" + this.availableEnd + "]";
        }
    }

    /**
     * Lays {@code lines} out from {@code startY}, keeping every line and then the closing line inside a region
     * that ends at {@code availableEnd}.
     *
     * <p>The closing line is placed <i>first</i>: its own height is reserved at the region's bottom, and the
     * payload may then use only the space above that reservation. That is what makes the fix structural — the
     * line that shipped broken is not something appended after the payload, it is the anchor the payload is
     * fitted around, and no payload length can push it out.
     *
     * @param startY       the scaled y of the first line
     * @param availableEnd the scaled y nothing may reach: the last drawn pixel must be strictly above it
     * @param footerHeight the closing line's own height, in the same units
     * @param lines        the payload, in drawing order
     */
    public static Block block(int startY, int availableEnd, int footerHeight, List<Measured> lines) {
        int requested = lines.size();
        int footerFloor = Math.max(startY, availableEnd - footerHeight);
        int drawn = 0;
        int cursor = startY;
        // A payload line is drawn only when it and its trailing gap fit entirely above the reserved closing
        // line. The trailing gap is included because the drawing loops advance the cursor by it, so excluding it
        // would let a gap push the cursor onto the anchor.
        for (Measured line : lines) {
            int after = cursor + line.height + line.gap;
            if (after > footerFloor) {
                break;
            }
            cursor = after;
            drawn++;
        }
        int payloadEnd = cursor;
        int footerY = footerFloor;
        return new Block(startY, drawn, requested, payloadEnd, footerY, footerHeight, availableEnd);
    }

    /**
     * The <b>0.24.1</b> layout, kept only so the M6c harness can inject it and watch its assertions fail.
     *
     * <p>0.24.1 put the closing line at {@code max(cursor, the original's fixed position)}: the cursor always
     * won, so a tall payload pushed the line down instead of being clamped by it. That is the shipped defect —
     * the {@code Avg: …} line landed on the player-inventory label and the hotbar. This method exists so
     * "the check has failed at least once" is provable offline rather than asserted.
     */
    public static Block brokenBlock(int startY, int availableEnd, int footerHeight, int originalFooterY,
            List<Measured> lines) {
        int cursor = startY;
        for (Measured line : lines) {
            cursor += line.height + line.gap;
        }
        return new Block(startY, lines.size(), lines.size(), cursor, Math.max(cursor, originalFooterY),
                footerHeight, availableEnd);
    }

    /**
     * The <b>0.24.1</b> factory queue rectangle, kept only for fault injection: the queue drawn one queue width
     * to the left of the panel origin, which is the "free-standing strip to the LEFT of the panel" the owner
     * photographed.
     */
    public static int[] brokenQueueRect() {
        return new int[] {FactoryPanel.QUEUE_X - FactoryPanel.ELEMENT_WIDTH, FactoryPanel.QUEUE_Y,
                FactoryPanel.ELEMENT_WIDTH, FactoryPanel.MAX_PAGE_ELEMENTS * FactoryPanel.ROW_STEP - 1};
    }

    /**
     * The information-block geometry of the 176-wide controller panel, in panel-local pixels.
     *
     * <p>The original put the block at (12, 12) inside a 0.72-scaled matrix and the player's slots at
     * {@code (8, 131)…(170, 207)} ({@code ContainerController#addPlayerSlots}, quoted by
     * {@code MachineControllerMenu}); the inherited player-inventory label sits at {@code imageHeight - 94} = 119.
     * The block therefore has to end above 119, and the four-pixel allowance below that is what stops an
     * exactly-fitting block from touching the label's first pixel row.
     */
    public static final class ControllerPanel {
        public static final int TEXT_X = 12;
        public static final int TEXT_Y = 12;
        public static final float SCALE = 0.72F;
        public static final int LINE = 10;
        /** {@code MachineControllerMenu.playerSlotsTopY()}: the first player row's top. */
        public static final int PLAYER_INVENTORY_Y = 131;
        /** {@code imageHeight - 94}, the label's own top. */
        public static final int INVENTORY_LABEL_Y = 119;
        /** How much clearance the block leaves above the label row, in panel pixels. */
        public static final int CLEARANCE = 4;

        private ControllerPanel() {
        }

        /**
         * How far left of the text origin the first glyph column still paints: Minecraft's font draws a
         * drop shadow offset by one unit to the right and down, and the glyph's own coverage starts at the
         * origin. A clip edge must therefore sit this many panel pixels <i>left</i> of {@link #textRect()}'s
         * left edge, or it shaves the shadow — and, once it reaches past that, the glyph itself.
         */
        public static final int TEXT_SHADOW_MARGIN = 1;

        /**
         * How much clearance the clip rectangle leaves left of {@link #textRect()}.
         *
         * <p>0.24.2 clipped at {@code TEXT_X - 2} = 10 while the 0.72-scaled block is drawn from panel x
         * {@code 12 * 0.72} = 8, so the clip cut <b>2 panel pixels</b> off the first glyph column — the whole
         * left edge of 「找」 in every row, which is the owner's 「第一列的文字被挡住了一半」. The margin is now
         * stated once, here, and the clip and the check that the clip cannot reach the glyphs are both derived
         * from it.
         */
        public static final int SCISSOR_MARGIN_X = 4;

        /**
         * The scaled y nothing in the block may reach: four panel pixels above the label row, converted into
         * the scaled space the block is drawn in.
         */
        public static int availableEnd() {
            return (int) ((INVENTORY_LABEL_Y - CLEARANCE) / SCALE);
        }

        // ------------------------------------------------------------------ the block's own rectangles
        //
        // These are the panel-local rectangles section U of the M6c harness checks, and the same ones the
        // screen derives its clip rectangle from. Drawing and checking therefore share one description.

        /**
         * The information block's rectangle, in <b>panel</b> pixels: its left edge is where the 0.72-scaled
         * matrix puts {@code TEXT_X}, its top is where it puts {@code TEXT_Y}, its right is the panel's own
         * right edge, and its bottom is the region's floor reconverted to panel pixels.
         *
         * <p>This is the panel-local origin the screen's matrix is translated to and the rectangle the clip
         * must contain. 0.24.2 scaled the block without translating it, so the block was drawn at
         * {@code 12 * 0.72} = 8 instead of 12 and the clip edge — computed from {@code TEXT_X} — cut a sliver
         * off its first glyph column. Deriving both from this one rectangle is what makes that impossible.
         */
        public static int[] textRect() {
            int left = (int) (TEXT_X * SCALE);
            int top = (int) (TEXT_Y * SCALE);
            int right = 176;
            int bottom = (int) (availableEnd() * SCALE);
            return new int[] {left, top, right - left, bottom - top};
        }

        /**
         * The first glyph column: the leftmost pixels the block's first character can paint, shadow
         * included. A clip rectangle that shares even one pixel with this rectangle shaves the text.
         */
        public static int[] glyphColumnRect() {
            int[] text = com.reborn.modularmachinery.client.MachineControllerScreen.textBlockRect();
            int shadow = (int) (TEXT_SHADOW_MARGIN * SCALE);
            return new int[] {text[0] - shadow, text[1], shadow + 1, text[3]};
        }

        /**
         * The clip rectangle as {@code {left, top, right, bottom}}, in panel pixels, derived from the block's
         * own {@code textBlockRect()} rather than from {@code TEXT_X} a second time.
         */
        public static int[] scissorRect() {
            int[] text = com.reborn.modularmachinery.client.MachineControllerScreen.textBlockRect();
            return new int[] {text[0] - SCISSOR_MARGIN_X, 0, 176, 213};
        }

        /** Whether {@code a} and {@code b}, {@code [x, y, w, h]} in panel pixels, share a pixel. */
        public static boolean sharesPixel(int[] a, int[] b) {
            return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                    && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
        }

        /** The closing line's height in scaled units — the original's font line height, ten units. */
        public static int footerHeight() {
            return LINE;
        }

        /** The scaled y of the block's first line. */
        public static int startY() {
            return TEXT_Y;
        }

        /** One drawn line, with {@code gap} panel pixels of blank space after it. */
        public static Measured line(int gapPanelPixels) {
            return new Measured(LINE, (int) (gapPanelPixels / SCALE));
        }

        /**
         * A payload entry of {@code lines} drawn lines, the last one followed by {@code gapPanelPixels} panel
         * pixels of blank space. {@code lines} = 0 is the original's bare blank line.
         */
        public static Measured row(int lines, int gapPanelPixels) {
            return new Measured(Math.max(0, lines) * LINE, (int) (gapPanelPixels / SCALE));
        }

        /** The block for a payload measured in this space. */
        public static Block block(List<Measured> lines) {
            return ScreenLayout.block(startY(), availableEnd(), footerHeight(), lines);
        }
    }

    /**
     * The information-block and recipe-queue geometry of the 280-wide factory panel, in panel-local pixels.
     *
     * <h2>One origin</h2>
     *
     * <p>The original blitted the panel at {@code (x, y)} from {@code GuiFactoryController:86-88}, drew the
     * player's slots at panel-local {@code x = 112 + col * 18}, {@code y = 131/189}
     * ({@code ContainerFactoryController:94-100}), and drew the queue's rows from the same
     * {@code drawGuiContainerForegroundLayer} — which 1.12.2 calls with the matrix already translated by
     * {@code (x, y)}. So queue, slots and text all share the panel origin. {@link #QUEUE_X} being 8 rather than
     * 112 is the whole point: the queue is the <i>left</i> half of the same panel, and {@code 112 - 8 = 104} is
     * the shift the factory container applied to the plain controller's layout, which is exactly the value the
     * queue must not be added to a second time.
     */
    public static final class FactoryPanel {
        public static final int PANEL_WIDTH = 280;
        public static final int PANEL_HEIGHT = 213;

        /** {@code GuiFactoryController:41-42}: the queue's rows start here, inside the panel. */
        public static final int QUEUE_X = 8;
        public static final int QUEUE_Y = 8;
        public static final int ELEMENT_WIDTH = 86;
        public static final int ELEMENT_HEIGHT = 32;
        public static final int MAX_PAGE_ELEMENTS = 6;
        /** {@code FACTORY_ELEMENT_HEIGHT + 1}, the original's own row step ({@code :107}). */
        public static final int ROW_STEP = ELEMENT_HEIGHT + 1;

        /** {@code GuiScrollbar}'s metrics ({@code GuiFactoryController:33-35}). */
        public static final int SCROLLBAR_X = 94;
        public static final int SCROLLBAR_Y = 8;
        public static final int SCROLLBAR_WIDTH = 12;
        public static final int SCROLLBAR_HEIGHT = 197;

        /** {@code GuiFactoryController:39-40, 191-192}: the text block's origin and scale. */
        public static final int TEXT_DRAW_OFFSET_X = 113;
        public static final int TEXT_DRAW_OFFSET_Y = 12;
        public static final float SCALE = 0.72F;
        public static final int LINE = 10;

        /** {@code ContainerFactoryController:94,98}: 104 panel pixels right of the plain controller's (8, 131). */
        public static final int PLAYER_INVENTORY_X = 112;
        public static final int PLAYER_INVENTORY_Y = 131;
        public static final int HOTBAR_Y = 189;

        /** {@code imageHeight - 94}, the row the plain controller's inherited label occupies. */
        public static final int INVENTORY_LABEL_Y = PANEL_HEIGHT - 94;
        /** How much clearance the block leaves above the label row, in panel pixels. */
        public static final int CLEARANCE = 4;

        private FactoryPanel() {
        }

        /**
         * The scaled y nothing in the block may reach.
         *
         * <p>The factory panel's text block and player grid overlap in the texture: the block starts at
         * {@code TEXT_DRAW_OFFSET_X} = 113 while the grid starts at 112, and the block's origin is 12 while the
         * grid's first row is 131. The original lived with that because its payload was short. The 0.24.2 rule
         * is that the block stops before the player's slots: the region's bottom is the earlier of the label row
         * and the grid's top, less the same four-pixel clearance the controller keeps.
         *
         * <p><b>The factory screen draws no player-inventory label at all</b> — the original's
         * {@code GuiFactoryController#drawGuiContainerForegroundLayer} ({@code :76-80}) overrides the 1.12.2
         * superclass method and never calls {@code super}, which is the only place vanilla draws
         * {@code container.inventory} (the plain controller, {@code GuiMachineController:49-50}, does call
         * super and keeps its label). {@link #INVENTORY_LABEL_Y} is therefore kept as the block's floor rather
         * than as a label's row, and it survives the change of reason: it also keeps the block inside the
         * texture's black preview box, whose last row is texel 122 (which the harness derives from the PNG and
         * checks the block against). Moving the floor <i>down</i> to the grid's top would change what the block
         * draws, so the row stays where the original had it.
         */
        public static int availableEnd() {
            int limit = Math.min(INVENTORY_LABEL_Y, PLAYER_INVENTORY_Y);
            return (int) ((limit - CLEARANCE - TEXT_DRAW_OFFSET_Y) / SCALE);
        }

        /** The scaled y of the block's first line. */
        public static int startY() {
            return 0;
        }

        /** The closing line's height in scaled units. */
        public static int footerHeight() {
            return LINE;
        }

        /** One drawn line, with {@code gap} panel pixels of blank space after it. */
        public static Measured line(int gapPanelPixels) {
            return new Measured(LINE, (int) (gapPanelPixels / SCALE));
        }

        /** The block for a payload measured in this space. */
        public static Block block(List<Measured> lines) {
            return ScreenLayout.block(startY(), availableEnd(), footerHeight(), lines);
        }

        // ------------------------------------------------------------------ rectangles the harness checks
        //
        // Every value below is panel-local, i.e. additive to the screen's leftPos/topPos. They are the drawing
        // code's own numbers, expressed once, so a check can assert containment rather than re-derive it.

        /** The panel's own rectangle: {@code [0, 0, 280, 213]}. Nothing the screen draws may leave it. */
        public static int[] panelRect() {
            return new int[] {0, 0, PANEL_WIDTH, PANEL_HEIGHT};
        }

        /** The recipe queue's rows: the original's {@code (8, 8)} plus six 86×32 elements 33 apart. */
        public static int[] queueRect() {
            return new int[] {QUEUE_X, QUEUE_Y, ELEMENT_WIDTH,
                    MAX_PAGE_ELEMENTS * ROW_STEP - 1};
        }

        /** The scrollbar's track, {@code GuiScrollbar} at {@code (94, 8)} height 197. */
        public static int[] scrollbarRect() {
            return new int[] {SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT};
        }

        /**
         * The information block's rectangle, in panel pixels.
         *
         * <p>Its left edge is {@code TEXT_DRAW_OFFSET_X * SCALE} = 81.36 rounded down — the block is drawn
         * inside a matrix translated by that much and then scaled — and its bottom is the region's boundary
         * reconverted to panel pixels, so the rectangle is by construction above the player-inventory label.
         */
        public static int[] textRect() {
            int left = (int) (TEXT_DRAW_OFFSET_X * SCALE);
            int top = (int) (TEXT_DRAW_OFFSET_Y * SCALE);
            int right = PANEL_WIDTH;
            int bottom = (int) (availableEnd() * SCALE);
            return new int[] {left, top, right - left, bottom - top};
        }

        /**
         * How far left of the text origin the first glyph column still paints; see
         * {@link ControllerPanel#TEXT_SHADOW_MARGIN}.
         */
        public static final int TEXT_SHADOW_MARGIN = 1;

        /** The first glyph column, shadow included; see {@link ControllerPanel#glyphColumnRect()}. */
        public static int[] glyphColumnRect() {
            int[] text = textRect();
            int shadow = (int) (TEXT_SHADOW_MARGIN * SCALE);
            return new int[] {text[0] - shadow, text[1], shadow + 1, text[3]};
        }

        /**
         * The clip rectangle as {@code {left, top, right, bottom}}, in panel pixels: the panel's own
         * rectangle, derived from {@link #panelRect()} so the blit, the queue and the clip cannot disagree
         * about where the panel is.
         */
        public static int[] scissorRect() {
            int[] panel = panelRect();
            return new int[] {panel[0], panel[1], panel[0] + panel[2], panel[1] + panel[3]};
        }

        /** Whether {@code a} and {@code b}, {@code [x, y, w, h]} in panel pixels, share a pixel. */
        public static boolean sharesPixel(int[] a, int[] b) {
            return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                    && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
        }

        /** The player's 3×9 grid and hotbar, {@code (112, 131)…(274, 206)}. */
        public static int[] playerGridRect() {
            return new int[] {PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y,
                    com.reborn.modularmachinery.menu.FactoryControllerMenu.playerSlotsRightX()
                            - PLAYER_INVENTORY_X,
                    com.reborn.modularmachinery.menu.FactoryControllerMenu.playerSlotsBottomY()
                            - PLAYER_INVENTORY_Y};
        }

        /** The blueprint slot the texture has a hole for, {@code (255, 8)}, 16×16. */
        public static int[] blueprintSlotRect() {
            return new int[] {com.reborn.modularmachinery.menu.FactoryControllerMenu.BLUEPRINT_SLOT_X,
                    com.reborn.modularmachinery.menu.FactoryControllerMenu.BLUEPRINT_SLOT_Y,
                    com.reborn.modularmachinery.menu.FactoryControllerMenu.SLOT_SIZE,
                    com.reborn.modularmachinery.menu.FactoryControllerMenu.SLOT_SIZE};
        }

        /** Whether {@code rect}, an {@code [x, y, w, h]} in panel pixels, is inside the panel's rectangle. */
        public static boolean insidePanel(int[] rect) {
            int[] panel = panelRect();
            return rect[0] >= panel[0] && rect[1] >= panel[1]
                    && rect[0] + rect[2] <= panel[0] + panel[2]
                    && rect[1] + rect[3] <= panel[1] + panel[3];
        }

        /** Whether two {@code [x, y, w, h]} rectangles share at least one pixel. */
        public static boolean overlaps(int[] a, int[] b) {
            return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                    && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
        }
    }
}
