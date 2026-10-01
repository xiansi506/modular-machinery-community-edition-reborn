package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.ModularMachineryReborn;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The original's own recipe sprite sheet, shipped byte-for-byte, and the regions of it the original's recipe page
 * drew.
 *
 * <p><b>Provenance.</b> The file is
 * {@code _mmce-src/ModularMachinery-Community-Edition-master/src/main/resources/assets/modularmachinery/textures/gui/jeirecipeicons_ce.png}
 * — 256x256, SHA-256 {@code 652ebfcd42920be26a2126f5ae2fa43a3d037d91eac53132c78b03d0789b7e6b} — copied unmodified.
 * Every {@code (u, v, w, h)} below is transcribed from the original's
 * {@code common/integration/recipe/RecipeLayoutHelper#init} (lines 41-48) and nothing is guessed:
 *
 * <pre>
 * PART_TANK_SHELL            = ( 0,  0, 18, 18)   RecipeLayoutHelper.java:41
 * PART_GAS_TANK_SHELL        = ( 0, 18, 18, 18)   RecipeLayoutHelper.java:42  (Mekanism gas; unused here)
 * PART_TANK_SHELL_BACKGROUND = (54,  0, 18, 18)   RecipeLayoutHelper.java:43
 * PART_ENERGY_FOREGROUND     = (18,  0, 18, 54)   RecipeLayoutHelper.java:44
 * PART_ENERGY_BACKGROUND     = (36,  0, 18, 54)   RecipeLayoutHelper.java:45
 * PART_INVENTORY_CELL        = (54,  0, 18, 18)   RecipeLayoutHelper.java:46
 * PART_PROCESS_ARROW         = (72,  0, 22, 15)   RecipeLayoutHelper.java:47
 * PART_PROCESS_ARROW_ACTIVE  = (72, 15, 22, 15)   RecipeLayoutHelper.java:48
 * </pre>
 *
 * <p>{@code PART_TANK_SHELL_BACKGROUND} and {@code PART_INVENTORY_CELL} are the <em>same</em> region in the
 * original — one opaque grey cell with a black top/left edge and a white bottom/right edge — which is why a fluid
 * tank and an item cell look identical there. The sheet was re-checked against the PNG itself: {@code (0,0)} and
 * {@code (0,18)} are transparent inside with a 1px edge, carrying an "F" and a "G" glyph respectively (fluid and
 * gas shell), {@code (54,0)} is an opaque grey 18x18 cell, and {@code (72,0)}/{@code (72,15)} hold the two halves
 * of the process arrow.
 *
 * <p>Drawn with {@link GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int)}, the same call
 * {@code client/preview/PreviewAtlas} uses; the sheet is 256x256 so the implicit texture size of that overload is
 * correct.
 */
final class MachineRecipeAtlas {

    /** The original's {@code RecipeLayoutHelper.LOCATION_JEI_ICONS}, under this mod's namespace. */
    static final ResourceLocation TEXTURE = new ResourceLocation(
            ModularMachineryReborn.MOD_ID, "textures/gui/jeirecipeicons_ce.png");

    /** {@code PART_TANK_SHELL}: drawn as the fluid renderer's overlay, i.e. on top of the fluid. */
    static final Sprite TANK_SHELL = new Sprite(0, 0, 18, 18);

    /** {@code PART_INVENTORY_CELL} == {@code PART_TANK_SHELL_BACKGROUND}: the opaque cell every slot sits in. */
    static final Sprite CELL = new Sprite(54, 0, 18, 18);

    /** {@code PART_ENERGY_FOREGROUND}: the filled energy column, drawn only while the rate is above zero. */
    static final Sprite ENERGY_FILLED = new Sprite(18, 0, 18, 54);

    /** {@code PART_ENERGY_BACKGROUND}: the empty energy column, drawn for every energy part. */
    static final Sprite ENERGY_EMPTY = new Sprite(36, 0, 18, 54);

    /** {@code PART_PROCESS_ARROW}: the static process arrow, 22x15. */
    static final Sprite ARROW = new Sprite(72, 0, 22, 15);

    /** {@code PART_PROCESS_ARROW_ACTIVE}: revealed left-to-right over {@link #ARROW}, 22x15. */
    static final Sprite ARROW_ACTIVE = new Sprite(72, 15, 22, 15);

    private MachineRecipeAtlas() {
    }

    /**
     * A source rectangle in the sheet.
     *
     * <p>Implements {@link IDrawable} so it can be handed to JEI as a slot background or overlay; the
     * {@code (x, y)} JEI passes are recipe-local and already include the offset it was given, which is how the
     * original's own {@code createDrawable(...).draw(mc, x, y)} calls behaved.
     */
    record Sprite(int u, int v, int width, int height) implements IDrawable {

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.blit(TEXTURE, x, y, this.u, this.v, this.width, this.height);
        }

        /**
         * The leftmost {@code width} columns of this sprite, which is what the original's
         * {@code createDrawable(LOCATION_JEI_ICONS, 72, 15, pxPart, 15)} did for the animated arrow: it built a
         * drawable whose own width was the revealed part, so only that slice of the sheet was blitted.
         */
        void drawPartial(GuiGraphics graphics, int x, int y, int visibleWidth) {
            int width = Math.min(this.width, Math.max(0, visibleWidth));
            if (width <= 0) {
                return;
            }
            graphics.blit(TEXTURE, x, y, this.u, this.v, width, this.height);
        }
    }
}
