package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.menu.ParallelControllerMenu;
import com.reborn.modularmachinery.network.ModNetwork;
import com.reborn.modularmachinery.network.ParallelControllerUpdatePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The parallel controller screen, reproduced from the original {@code GuiContainerParallelController} (223 lines).
 *
 * <h2>Panel</h2>
 *
 * <p>The original blitted {@code TEXTURES_EMPTY_GUI} whole at {@code (0, 0, xSize, ySize)}
 * ({@code :68-73}). {@code TEXTURES_EMPTY_GUI} is {@code textures/gui/guismartinterface.png}
 * ({@code GuiContainerBase.java:26}), and {@code setWidthHeight()} is empty ({@code :62-65}), so
 * {@code xSize/ySize} are {@code GuiContainer}'s own defaults: <b>176 × 166</b>. That is the whole panel here
 * too, and the texture is a byte-for-byte copy of the original's.
 *
 * <h2>Text</h2>
 *
 * <p>{@code :45-57}, unscaled and with shadow, in the original's order and at the original's offsets:
 *
 * <pre>
 * title          (4,  4)
 * max value      (6, 16)     offsetX +2, offsetY +12 after the title
 * current value  (6, 49)     offsetY +33 after the max line
 * </pre>
 *
 * <p>{@code super.drawGuiContainerForegroundLayer} in 1.12.2 draws the player-inventory label at
 * {@code (8, ySize - 96 + 2)} = {@code (8, 72)}; 1.20.1's own default is {@code imageHeight - 94} = 72, so the
 * inherited label is drawn exactly where the original's was, and the title line is <b>not</b> drawn (the
 * original's superclass did not draw one either).
 *
 * <h2>Widgets</h2>
 *
 * <p>Six plain buttons and one text field, at the original's coordinates relative to the <b>screen centre</b> —
 * {@code this.width / 2} in 1.12.2 is the same number 1.20.1 calls {@code width / 2}:
 *
 * <pre>
 * field         (w/2 - 15, h/2 - 35)  95 x 10, max length 10
 * -1 -10 -100   (w/2 - 81 | -16 | +51, h/2 - 57)  30 x 20
 * +1 +10 +100   (w/2 - 81 | -16 | +51, h/2 - 23)  30 x 20
 * </pre>
 *
 * <p>They are drawn and hit-tested by hand rather than registered as screen widgets, which is what the original
 * did — it kept its own {@code List<GuiButton>} and called {@code drawButton} / {@code mousePressed} in the
 * background layer ({@code :76-79}, {@code :102-169}).
 */
public final class ParallelControllerScreen extends AbstractContainerScreen<ParallelControllerMenu> {

    /** The original's {@code GuiContainerBase.TEXTURES_EMPTY_GUI}; also this project's {@code guismartinterface.png}. */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guismartinterface.png");

    private static final int WHITE = 0xFFFFFF;
    /** Vanilla's inventory-label colour, and the original's {@code 4210752}. */
    private static final int LABEL_COLOR = 0x404040;

    private static final int TITLE_X = 4;
    private static final int TITLE_Y = 4;
    private static final int MAX_VALUE_X = 6;
    private static final int MAX_VALUE_Y = 16;
    private static final int CURRENT_VALUE_X = 6;
    private static final int CURRENT_VALUE_Y = 49;

    private static final int FIELD_OFFSET_X = -15;
    private static final int FIELD_OFFSET_Y = -35;
    private static final int FIELD_WIDTH = 95;
    private static final int FIELD_HEIGHT = 10;
    private static final int FIELD_MAX_LENGTH = 10;

    private static final int BUTTON_WIDTH = 30;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_DECREMENT_Y = -57;
    private static final int ROW_INCREMENT_Y = -23;
    private static final int COLUMN_1_X = -81;
    private static final int COLUMN_10_X = -16;
    private static final int COLUMN_100_X = 51;

    private final List<Button> buttons = new ArrayList<>(6);
    private EditBox valueField;

    public ParallelControllerScreen(ParallelControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // The original's setWidthHeight() was empty, so these are GuiContainer's own defaults.
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int centreX = this.width / 2;
        int centreY = this.height / 2;

        this.valueField = new EditBox(this.font, centreX + FIELD_OFFSET_X, centreY + FIELD_OFFSET_Y,
                FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        this.valueField.setMaxLength(FIELD_MAX_LENGTH);

        this.buttons.clear();
        // The original's initGui() order: the three decrements, then the three increments.
        this.buttons.add(button(centreX + COLUMN_1_X, centreY + ROW_DECREMENT_Y, "-1", () -> decrement(1)));
        this.buttons.add(button(centreX + COLUMN_10_X, centreY + ROW_DECREMENT_Y, "-10", () -> decrement(10)));
        this.buttons.add(button(centreX + COLUMN_100_X, centreY + ROW_DECREMENT_Y, "-100", () -> decrement(100)));
        this.buttons.add(button(centreX + COLUMN_1_X, centreY + ROW_INCREMENT_Y, "+1", () -> increment(1)));
        this.buttons.add(button(centreX + COLUMN_10_X, centreY + ROW_INCREMENT_Y, "+10", () -> increment(10)));
        this.buttons.add(button(centreX + COLUMN_100_X, centreY + ROW_INCREMENT_Y, "+100", () -> increment(100)));
    }

    private Button button(int x, int y, String label, Runnable action) {
        return Button.builder(Component.literal(label), pressed -> action.run())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        // The original drew the field first and the buttons after it (:75-79); they do not overlap.
        this.valueField.render(graphics, mouseX, mouseY, partialTick);
        for (Button button : this.buttons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only the inherited player-inventory label; the panel has no title area.
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);

        graphics.drawString(this.font, Component.translatable(ParallelControllerMenu.TITLE_KEY),
                TITLE_X, TITLE_Y, WHITE, true);
        graphics.drawString(this.font, Component.translatable("gui.modular_machinery_reborn.parallelcontroller.max_value",
                this.menu.maxParallelism()), MAX_VALUE_X, MAX_VALUE_Y, WHITE, true);
        graphics.drawString(this.font, Component.translatable("gui.modular_machinery_reborn.parallelcontroller.current_value",
                this.menu.parallelism()), CURRENT_VALUE_X, CURRENT_VALUE_Y, WHITE, true);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.valueField.tick();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.valueField.setFocused(false);
            if (this.valueField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (Button candidate : this.buttons) {
                if (candidate.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            submitValueField();
            return true;
        }
        if (this.valueField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // The original only let digits through (:192).
        if (Character.isDigit(codePoint) && this.valueField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * The original's return-key branch ({@code :178-190}): parse the field, send it when it is inside
     * {@code [0, max]}, then clear the field — but only when it parsed at all, which is why the clear sits inside
     * the {@code try}.
     */
    private void submitValueField() {
        try {
            int value = Integer.parseInt(this.valueField.getValue());
            if (value >= 0 && value <= this.menu.maxParallelism()) {
                send(value);
            }
            this.valueField.setValue("");
        } catch (NumberFormatException ignored) {
            // The original left unparseable text in the box.
        }
    }

    /**
     * The original's {@code +amount} branches ({@code :102-136}). When there is room it adds {@code amount}; when
     * there is not it jumps to the maximum — <b>except</b> for {@code +1}, which the original left as a no-op
     * rather than a jump.
     */
    private void increment(int amount) {
        int current = this.menu.parallelism();
        int maximum = this.menu.maxParallelism();
        if (maximum - current >= amount) {
            send(current + amount);
        } else if (amount > 1) {
            send(maximum);
        }
    }

    /**
     * The original's {@code -amount} branches ({@code :137-169}), including the {@code max(0, parallelism - 1)}
     * test it used for the room left below — so {@code -1} at 0 or 1 both land on 0, and an overshoot clamps to 0
     * rather than stopping short.
     */
    private void decrement(int amount) {
        int current = this.menu.parallelism();
        if (Math.max(0, current - 1) >= amount) {
            send(current - amount);
        } else {
            send(0);
        }
    }

    private void send(int value) {
        ModNetwork.CHANNEL.sendToServer(new ParallelControllerUpdatePacket(value));
    }
}
