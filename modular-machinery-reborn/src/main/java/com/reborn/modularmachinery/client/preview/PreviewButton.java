package com.reborn.modularmachinery.client.preview;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One of the panel's small atlas buttons.
 *
 * <p>Behaviour copied from the original's {@code Button4State}/{@code Button5State}: pick the sprite by state,
 * hovering wins over normal, a toggle that is on shows its "clicked" sprite instead, and a tooltip appears on
 * hover. The sprites are the original's own atlased art ({@link PreviewAtlas}); nothing here is invented.
 *
 * <p>Two deliberate differences from the original widget:
 *
 * <ul>
 *   <li><b>Hovering also draws a light outline.</b> Several of the original's hover sprites differ from the
 *       normal sprite only in their rightmost border column, which is very hard to see at GUI scale 2–3. The
 *       outline is drawn <i>on top of</i> the original's hovered sprite, never instead of it.</li>
 *   <li><b>A toggle whose row has no "clicked" sprite is tinted instead.</b> {@code machineExtraInfo} was a
 *       hover-tooltip button in the original and only has normal and hovered art; tinting keeps the on-state
 *       visible without claiming a UV the original never used.</li>
 * </ul>
 */
public final class PreviewButton {

    /** Translucent white used for the extra hover outline. */
    private static final int HOVER_OUTLINE = 0x80FFFFFF;
    /** Translucent white used for the on-state of a toggle that has no "clicked" sprite. */
    private static final int ACTIVE_TINT = 0x60FFFFFF;
    /** How long the mouse-down sprite stays up after a click, standing in for the held-button state. */
    private static final long PRESS_FLASH_MILLIS = 120L;

    private final String name;
    private final boolean toggle;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final PreviewAtlas.Sprite normal;
    private final PreviewAtlas.Sprite hovered;
    private final PreviewAtlas.Sprite pressed;
    /** Sprite shown while a toggle is on; {@code null} means tint {@link #normal} instead. */
    private final PreviewAtlas.Sprite active;
    private final Supplier<List<Component>> tooltip;
    /** Invoked with the button's new on-state; plain buttons always report {@code false}. */
    private final Consumer<Boolean> action;
    private final BooleanSupplier visible;

    private boolean toggled;
    private boolean armed;
    private long pressFlashUntil;

    private PreviewButton(String name, boolean toggle, int x, int y, int width, int height,
                          PreviewAtlas.Sprite normal, PreviewAtlas.Sprite hovered, PreviewAtlas.Sprite pressed,
                          PreviewAtlas.Sprite active, Supplier<List<Component>> tooltip, Consumer<Boolean> action,
                          BooleanSupplier visible) {
        this.name = name;
        this.toggle = toggle;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.normal = normal;
        this.hovered = hovered;
        this.pressed = pressed;
        this.active = active;
        this.tooltip = tooltip;
        this.action = action;
        this.visible = visible;
    }

    /** A plain button: it fires on every click and never shows a persistent on-state. */
    public static PreviewButton button(String name, int x, int y, int size, PreviewAtlas.Sprite normal,
                                       PreviewAtlas.Sprite hovered, PreviewAtlas.Sprite pressed,
                                       Supplier<List<Component>> tooltip, Consumer<Boolean> action) {
        return button(name, x, y, size, normal, hovered, pressed, tooltip, action, () -> true);
    }

    /** A plain button that is only drawn and only clickable while {@code visible} says so. */
    public static PreviewButton button(String name, int x, int y, int size, PreviewAtlas.Sprite normal,
                                       PreviewAtlas.Sprite hovered, PreviewAtlas.Sprite pressed,
                                       Supplier<List<Component>> tooltip, Consumer<Boolean> action,
                                       BooleanSupplier visible) {
        return new PreviewButton(name, false, x, y, size, size, normal, hovered, pressed, null, tooltip, action,
                visible);
    }

    /** A toggle: flips its on-state on every click and reports the new state to {@code action}. */
    public static PreviewButton toggle(String name, int x, int y, int size, PreviewAtlas.Sprite normal,
                                       PreviewAtlas.Sprite hovered, PreviewAtlas.Sprite pressed,
                                       PreviewAtlas.Sprite active, Supplier<List<Component>> tooltip,
                                       Consumer<Boolean> action) {
        return new PreviewButton(name, true, x, y, size, size, normal, hovered, pressed, active, tooltip, action,
                () -> true);
    }

    /**
     * Stable identifier of this widget, used only to name the element a press claimed in the JEI input
     * diagnostic ({@code [preview] JEI simulate: ... -> button machine-info}). Never shown to the player.
     */
    public String name() {
        return this.name;
    }

    public boolean isVisible() {
        return this.visible.getAsBoolean();
    }

    public boolean isToggled() {
        return this.toggled;
    }

    /** Sets the on-state without firing the action, for hosts that drive the panel themselves. */
    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public boolean contains(double mouseX, double mouseY) {
        return isVisible()
                && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    /**
     * Records a press at panel coordinates: the button is armed, but nothing happens yet.
     *
     * <p>Vanilla's {@code Button} acts on release, and so does this. That is also what JEI needs: its input router
     * delivers the mouse press as a simulated input and the matching release as an executed one, so the press
     * pass arms the button and the release pass fires it — see {@code PreviewInputHandler}. Arming has no visible
     * side effect, so nothing is drawn for it beyond the hover state.
     *
     * @return {@code true} when the press landed on this button
     */
    public boolean mousePressed(double mouseX, double mouseY) {
        this.armed = contains(mouseX, mouseY);
        return this.armed;
    }

    /**
     * Ends a press. The action fires only when the release is on the same button that was pressed, exactly like
     * a vanilla button, so a press that slides off and releases elsewhere does nothing.
     */
    public boolean mouseReleased(double mouseX, double mouseY) {
        boolean fire = this.armed && contains(mouseX, mouseY);
        this.armed = false;
        if (!fire) {
            return false;
        }
        if (this.toggle) {
            this.toggled = !this.toggled;
        }
        this.pressFlashUntil = System.currentTimeMillis() + PRESS_FLASH_MILLIS;
        this.action.accept(this.toggled);
        return true;
    }

    /** Cancels an armed press without firing, used when another button takes the press instead. */
    public void disarm() {
        this.armed = false;
    }

    /** Draws the button, with panel coordinates offset by {@code (originX, originY)}. */
    public void render(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY) {
        if (!isVisible()) {
            return;
        }
        boolean hoveredNow = contains(mouseX, mouseY);
        // Precedence follows the original's Button5State.render: the persistent "clicked" state wins over the
        // mouse-down state, which wins over hover.
        PreviewAtlas.Sprite sprite = this.normal;
        boolean tint = false;
        if (this.toggled) {
            if (this.active != null) {
                sprite = this.active;
            } else {
                tint = true;
            }
        } else if (System.currentTimeMillis() < this.pressFlashUntil) {
            sprite = this.pressed;
        } else if (hoveredNow) {
            sprite = this.hovered;
        }

        int drawX = originX + this.x;
        int drawY = originY + this.y;
        sprite.draw(graphics, drawX, drawY);
        if (tint) {
            graphics.fill(drawX, drawY, drawX + this.width, drawY + this.height, ACTIVE_TINT);
        }
        if (hoveredNow) {
            graphics.fill(drawX, drawY, drawX + this.width, drawY + 1, HOVER_OUTLINE);
            graphics.fill(drawX, drawY + this.height - 1, drawX + this.width, drawY + this.height, HOVER_OUTLINE);
            graphics.fill(drawX, drawY, drawX + 1, drawY + this.height, HOVER_OUTLINE);
            graphics.fill(drawX + this.width - 1, drawY, drawX + this.width, drawY + this.height, HOVER_OUTLINE);
        }
    }

    public List<Component> tooltipLines() {
        return this.tooltip.get();
    }
}
