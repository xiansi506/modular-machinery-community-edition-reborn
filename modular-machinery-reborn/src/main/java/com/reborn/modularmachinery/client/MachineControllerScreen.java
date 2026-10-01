package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.api.ControllerGuiInfoEvent;
import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.machine.SmartInterfaceType;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import com.reborn.modularmachinery.network.ModNetwork;
import com.reborn.modularmachinery.network.SmartInterfaceUpdatePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The machine controller screen, reproduced from the original {@code GuiMachineController}.
 *
 * <p>The original panel is one image — {@code guicontroller_large.png}, blitted whole at (0, 0, 176, 213) —
 * with a black text area in the top left and the blueprint slot at (151, 8). There are no progress or energy
 * bars: everything the controller has to say is text. The text block is scaled to <b>0.72</b> and starts at
 * (12, 12), wrapped to 135 screen pixels, which is 135 / 0.72 — 187 units inside the scaled matrix.
 *
 * <p>The line order and the gaps between lines are copied from the original one for one, including the
 * asymmetric ones (the status block leaves 15, the name lines leave 10). Addons can append lines through
 * {@link ControllerGuiInfoEvent}, which replaces the original's {@code ControllerGUIRenderEvent}.
 *
 * <h2>0.24.2 —the information block is bounded</h2>
 *
 * <p>The original appended every line to a cursor and drew the closing {@code Avg: …} line wherever the cursor
 * ended up ({@code GuiMachineController:157-172}). Its own payload was short, so the block never reached the
 * player-inventory label at panel y 119 and the overflow path was never exercised. 0.24.0's smart-data-interface
 * rows made the block taller, and in game the closing line landed on the inventory label while the block's last
 * rows ran into the player grid. The fix is structural rather than a nudge: the payload is described once, as a
 * list of rows, {@link ScreenLayout#informationBlock} decides how many of those rows fit above the label, only
 * that many are drawn, and the whole block is additionally scissored to the panel on the way out. The same
 * arithmetic is asserted offline by section U of the M6c harness.
 */
public final class MachineControllerScreen extends AbstractContainerScreen<MachineControllerMenu> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/controller_legacy.png");

    private static final String KEY = "gui.modular_machinery_reborn.controller.";
    /**
     * M6d-b: the smart data interface's value line, under its own prefix because it is a <b>shared</b> line —     * the key is the original's {@code gui.smartinterface.value}, re-namespaced, and answers for every type that
     * does not bring a format of its own.
     */
    private static final String SMART_KEY = "gui.modular_machinery_reborn.smartinterface.";

    /** The original scaled the entire text block to 0.72. */
    private static final float TEXT_SCALE = 0.72F;
    private static final int TEXT_X = 12;
    private static final int TEXT_Y = 12;
    /** The original wrapped to {@code 135 * (1 / scale)} screen pixels. */
    private static final int WRAP_WIDTH = (int) (135 / TEXT_SCALE);
    private static final int LINE = 10;
    private static final int WHITE = 0xFFFFFF;
    private static final int LABEL_COLOR = 0x404040;
    /** The red a failure line is drawn in —the original used white for everything, but a failure is not text. */
    private static final int RED = 0xFF5555;
    /**
     * The lowest row the closing line may be drawn at, inside the 0.72-scaled block. {@code 187 * 0.72} is about
     * 135px into the 213px panel, which keeps it clear of both the player inventory label and the panel's bottom
     * edge while still sitting well below the information block.
     */
    private static final int FOOTER_Y = 187;

    /**
     * The smart data interface's value field, in <b>panel</b> coordinates (inside the 0.72-scaled block, like
     * the text around it). M6d-b: the original's separate {@code GuiContainerSmartInterface} had a 70×10 field at
     * the screen centre and a return key that sent the parsed value; here the same field sits inside the
     * controller's own text block.
     */
    private static final int FIELD_X = 12;
    private static final int FIELD_Y = 138;
    private static final int FIELD_WIDTH = 70;
    private static final int FIELD_HEIGHT = 10;
    /** The original's {@code textField.setMaxStringLength(16)}, and the only characters the original let in
     *  were digits, {@code .} and {@code E} ({@code GuiContainerSmartInterface:188}). */
    private static final int FIELD_MAX_LENGTH = 16;

    private EditBox valueField;

    public MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 213;
        // Vanilla puts the label at imageHeight - 94, which is 119 here —the same place the original's
        // inherited 1.12.2 label landed (ySize - 96 + 2).
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // The field lives in panel coordinates, so it has to be placed through the same 0.72 scale the text
        // block uses — and it is only offered when the machine really declares a smart interface type.
        // FIELD_X/FIELD_Y are measured from the block's own origin (see TEXT_X/TEXT_Y), so the field moves with
        // the block rather than with a second copy of the same arithmetic.
        SmartInterfaceType shown = shownInterface();
        int[] text = textBlockRect();
        int x = this.leftPos + text[0] + (int) ((FIELD_X - TEXT_X) * TEXT_SCALE);
        int y = this.topPos + text[1] + (int) ((FIELD_Y - TEXT_Y) * TEXT_SCALE);
        this.valueField = new EditBox(this.font, x, y, (int) (FIELD_WIDTH * TEXT_SCALE),
                (int) (FIELD_HEIGHT * TEXT_SCALE), Component.empty());
        this.valueField.setMaxLength(FIELD_MAX_LENGTH);
        this.valueField.setVisible(shown != null);
        this.valueField.setEditable(shown != null);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // M6d-b: the value field. It is positioned and sized in screen pixels (see init), while the rows
        // around it are inside the 0.72-scaled text matrix, so it is drawn here rather than in renderLabels.
        if (this.valueField != null && this.valueField.isVisible()) {
            this.valueField.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.valueField != null) {
            this.valueField.tick();
        }
    }

    // ---------------------------------------------------------------- editing the interface value

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.valueField != null && this.valueField.isVisible()) {
            this.valueField.setFocused(false);
            if (this.valueField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The original submitted on return and let the field see everything else
        // (GuiContainerSmartInterface:172-191).
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && this.valueField != null && this.valueField.isFocused()) {
            submitValueField();
            return true;
        }
        if (this.valueField != null && this.valueField.isVisible()
                && this.valueField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // The original's filter was `Character.isDigit(c) || c == '.' || c == 'E'` (`:188`). It dropped the
        // minus sign, so a negative value could only ever be typed here through the source default; that quirk
        // is kept rather than fixed, because a type whose author wants negative values can still declare them.
        if (this.valueField != null && this.valueField.isVisible()
                && (Character.isDigit(codePoint) || codePoint == '.' || codePoint == 'E')
                && this.valueField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * The original's return-key branch ({@code :172-191}): parse the field and send it, leaving unparseable text
     * in the box. A number that fails to parse is simply not sent —the server would refuse it anyway, and the
     * field staying filled is the feedback.
     */
    private void submitValueField() {
        SmartInterfaceType type = shownInterface();
        if (type == null || this.valueField == null) {
            return;
        }
        try {
            float value = Float.parseFloat(this.valueField.getValue());
            if (Float.isFinite(value)) {
                ModNetwork.CHANNEL.sendToServer(new SmartInterfaceUpdatePacket(type.type(), value));
                this.valueField.setValue("");
            }
        } catch (NumberFormatException ignored) {
            // The original left unparseable text in the box.
        }
    }

    private List<Component> extraInfo(MachineControllerBlockEntity machine) {
        ControllerGuiInfoEvent event = new ControllerGuiInfoEvent(machine);
        MinecraftForge.EVENT_BUS.post(event);
        return event.extraInfo();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The original panel has no title area, so only the player inventory label is drawn.
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        drawInfo(graphics);
    }

    // ---------------------------------------------------------------- the information block

    /**
     * The payload, as measured rows in the original's own order and with the original's own gaps
     * ({@code GuiMachineController:75-176}). Every row is one drawn line; {@code gap} is the blank space the
     * original left after it, in <b>panel</b> pixels (the original added 10 or 15 inside the scaled matrix, so
     * this is that number times the scale).
     *
     * <p>This is the only description of the block's content. {@link #drawInfo} draws from it and section U of
     * the M6c harness measures it, so the check and the drawing cannot drift apart.
     */
    public static List<ScreenLayout.Measured> infoRows(boolean pRedstone, boolean pFormed, boolean pBlueprint,
            int pNameLines, int pExtraLines, int pInterfaceLines, int pStatusLines, int pFailures,
            boolean pProgress) {
        List<ScreenLayout.Measured> rows = new ArrayList<>();
        if (pRedstone) {
            // The original drew nothing else at all while the controller was powered (GuiMachineController:62-73).
            rows.add(ScreenLayout.ControllerPanel.row(1, 10));
            return rows;
        }
        if (pBlueprint) {
            rows.add(ScreenLayout.ControllerPanel.row(1, 0));
            rows.add(ScreenLayout.ControllerPanel.row(pNameLines, 15));
        } else if (!pFormed) {
            rows.add(ScreenLayout.ControllerPanel.row(1, 15));
        }
        rows.add(ScreenLayout.ControllerPanel.row(1, 0));
        if (pNameLines > 0) {
            rows.add(ScreenLayout.ControllerPanel.row(pNameLines, 0));
            if (pExtraLines > 0) {
                rows.add(ScreenLayout.ControllerPanel.row(pExtraLines, 0));
            }
        }
        // The gap the original left after the structure block (GuiMachineController:123).
        rows.add(ScreenLayout.ControllerPanel.row(0, 15));
        if (pInterfaceLines > 0) {
            // M6d-b: the value line plus the type's header and footer, then its own five-pixel gap.
            rows.add(ScreenLayout.ControllerPanel.row(1, 0));
            rows.add(ScreenLayout.ControllerPanel.row(Math.max(0, pInterfaceLines - 1), 5));
        }
        rows.add(ScreenLayout.ControllerPanel.row(1, 0));
        rows.add(ScreenLayout.ControllerPanel.row(pStatusLines, 15));
        for (int i = 0; i < pFailures; i++) {
            rows.add(ScreenLayout.ControllerPanel.row(1, 5));
        }
        if (pProgress) {
            rows.add(ScreenLayout.ControllerPanel.row(1, 15));
        }
        rows.add(ScreenLayout.ControllerPanel.row(1, 15));
        rows.add(ScreenLayout.ControllerPanel.row(1, 0));
        return rows;
    }

    /** The block {@link #drawInfo} uses for the current payload. */
    public static ScreenLayout.Block blockFor(List<ScreenLayout.Measured> rows) {
        return ScreenLayout.ControllerPanel.block(rows);
    }

    /**
     * The information block's origin in <b>panel-local</b> pixels — the point {@link #drawInfo} translates its
     * drawing matrix to, which is where the block therefore lands.
     *
     * <p>This exists so section U of the M6c harness can assert that <b>the drawn block lies inside the clip</b>
     * rather than that two hand-copied tables agree. 0.24.2 scaled the block without translating it, so it was
     * drawn at {@code (12 * 0.72, 12 * 0.72)} = (8, 8) while the clip edge sat at {@code TEXT_X - 2} = 10: the
     * predicate below and this method describe the same two pixels, so a screen that skips the translate again
     * fails the harness instead of passing it.
     */
    public static int[] textBlockRect() {
        return ScreenLayout.ControllerPanel.textRect();
    }

    /**
     * The payload the current menu and machine state will draw, in drawing order.
     *
     * <p>The harness cannot build a font, so it hands {@link #infoRows} its own line counts and measures the
     * result with {@link #blockFor}; the drawing path builds the same list from the font's real wrapping. Only
     * one description of the block exists, which is what makes the offline check meaningful.
     */
    private List<ScreenLayout.Measured> payload(MachineControllerBlockEntity machine) {
        if (menu.redstoneStopped()) {
            return infoRows(true, false, false, 0, 0, 0, 0, 0, false);
        }
        int extraLines = 0;
        if (machine != null) {
            for (Component info : extraInfo(machine)) {
                for (FormattedCharSequence ignored : wrap(info)) {
                    extraLines++;
                }
            }
        }
        boolean hasBlueprint = machine != null && blueprintLabel(machine) != null;
        int nameLines = 0;
        if (machine != null && machine.machineName() != null) {
            for (FormattedCharSequence ignored : wrap(machine.machineName())) {
                nameLines++;
            }
        }
        int interfaceLines = 0;
        SmartInterfaceType type = shownInterface();
        if (type != null) {
            interfaceLines = 1;
            for (String line : new String[] {type.header(), type.footer()}) {
                if (!line.isEmpty()) {
                    for (FormattedCharSequence ignored : wrap(Component.translatable(line))) {
                        interfaceLines++;
                    }
                }
            }
        }
        int statusLines = wrap(Component.translatable(menu.status().translationKey())).size();
        int failures = 0;
        for (String failure : failureLines(menu.startFailureKey())) {
            for (FormattedCharSequence ignored : wrap(Component.translatable(failure))) {
                failures++;
            }
        }
        return infoRows(false, menu.formedValue(), hasBlueprint, nameLines, extraLines, interfaceLines,
                statusLines, failures, menu.progressValue() > 0);
    }

    /**
     * Ports {@code GuiMachineController#drawGuiContainerForegroundLayer} line for line, plus 0.24.2's one
     * structural addition: <b>the block cannot leave its region.</b>
     *
     * <h2>How the region is enforced</h2>
     *
     * <p>The payload is measured first, {@link ScreenLayout#informationBlock} then answers how many rows fit
     * above the player-inventory label and where the closing line goes, only that many rows are drawn, and the
     * whole block is scissored to the panel on the way out. A row count the clamp did not anticipate therefore
     * still cannot paint a pixel outside the panel.
     *
     * <h2>Two coordinate spaces, and the 0.24.2 defect they caused</h2>
     *
     * <p>{@code GuiGraphics#enableScissor} takes <b>absolute GUI</b> coordinates and does not consult the pose
     * stack (verified against the mapped 1.20.1 bytecode, which converts by {@code windowGuiScale} only). The
     * text, on the other hand, is drawn inside a translated-and-scaled matrix. 0.24.2 passed absolute
     * coordinates to the clip and then <b>failed to translate the matrix at all</b>, so the block was painted
     * from panel x {@code 12 * 0.72} = 8 while the clip edge sat at {@code TEXT_X - 2} = 10: every row lost the
     * left 2 panel pixels of its first glyph column — the owner's 「第一列的文字被挡住了一半」.
     *
     * <p>There is now exactly one origin. The clip is {@link ScreenLayout.ControllerPanel#scissorRect()} —
     * panel-local, additive to {@code leftPos/topPos} — the matrix is translated by
     * {@link ScreenLayout.ControllerPanel#textRect()}'s own left/top (which is the same space the render
     * matrix's implicit {@code (leftPos, topPos)} translation leaves), and the block is drawn from {@code (0, 0)}
     * inside the scaled matrix. The clip therefore <i>contains</i> the block by construction, and section U of
     * the M6c harness asserts that containment on these same numbers.
     */
    private void drawInfo(GuiGraphics graphics) {
        MachineControllerBlockEntity machine = menu.machine();
        ScreenLayout.Block block = blockFor(payload(machine));

        int[] panel = ScreenLayout.ControllerPanel.scissorRect();
        int[] text = textBlockRect();
        graphics.enableScissor(this.leftPos + panel[0], this.topPos + panel[1],
                this.leftPos + panel[2], this.topPos + panel[3]);

        graphics.pose().pushPose();
        // The matrix's origin is where textBlockRect() says the block is, so `text` and the drawing below are
        // one coordinate space and the clip above cannot slice the glyphs.
        graphics.pose().translate(text[0], text[1], 0.0F);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);

        // One counter for the whole block. Every drawing step consumes exactly as many rows as `payload` put
        // into the list, and the counter is what stops the drawing; that is the structural fix, and section U
        // of the M6c harness is what checks the two stay in step.
        Counter counter = new Counter(block.drawnLines);
        int y = 0;
        // Inside the scaled matrix the block sits at its own origin: the block's x and y are in the translate
        // above, so every drawing call below uses this x rather than x a second time.
        final int x = 0;

        if (menu.redstoneStopped()) {
            // The original drew nothing else at all while the controller was powered, and left a blank line
            // after the message (GuiMachineController:64-69).
            for (FormattedCharSequence line : wrap(Component.translatable(KEY + "status.redstone_stopped"))) {
                if (!counter.take()) {
                    break;
                }
                graphics.drawString(font, line, x, y, WHITE, true);
                y += LINE * 2;
            }
        } else {
            Component blueprint = machine == null ? null : blueprintLabel(machine);
            if (blueprint != null) {
                if (counter.index() == 0) {
                    // The payload's first row is the heading when there is a blueprint to name (only the
                    // no-blueprint/no-structure case wants no heading at all), so it is spent here.
                    graphics.drawString(font, Component.translatable(KEY + "blueprint", ""), x, y, WHITE,
                            true);
                }
                counter.take();
                y += LINE;
                for (FormattedCharSequence line : wrap(blueprint)) {
                    if (!counter.take()) {
                        break;
                    }
                    graphics.drawString(font, line, x, y, WHITE, true);
                    y += LINE;
                }
                y += 15;
            } else if (!menu.formedValue()) {
                if (counter.take()) {
                    graphics.drawString(font, Component.translatable(KEY + "blueprint",
                            Component.translatable(KEY + "blueprint.none")), x, y, WHITE, true);
                }
                y += 15;
            }

            Component found = machine == null ? null : machine.machineName();
            if (found != null) {
                if (counter.index() == 0) {
                    graphics.drawString(font, Component.translatable(KEY + "structure", ""), x, y, WHITE,
                            true);
                }
                counter.take();
                y += LINE;
                for (FormattedCharSequence line : wrap(found)) {
                    if (!counter.take()) {
                        break;
                    }
                    graphics.drawString(font, line, x, y, WHITE, true);
                    y += LINE;
                }
                if (machine != null) {
                    List<Component> extra = extraInfo(machine);
                    if (!extra.isEmpty()) {
                        y += 5;
                        for (Component info : extra) {
                            for (FormattedCharSequence line : wrap(info)) {
                                if (!counter.take()) {
                                    break;
                                }
                                graphics.drawString(font, line, x, y, WHITE, true);
                                y += LINE;
                            }
                        }
                    }
                }
            } else {
                if (counter.take()) {
                    graphics.drawString(font, Component.translatable(KEY + "structure",
                            Component.translatable(KEY + "structure.none")), x, y, WHITE, true);
                }
            }
            y += 15;

            // M6d-b: the smart data interface's value, and why the craft is refusing to start. The original
            // printed the failure in its own screen; folding the interface in means this screen prints it.
            y = drawSmartInterface(graphics, x, y, counter);

            // The controller's own status heading and its current value.
            if (counter.take()) {
                graphics.drawString(font, Component.translatable(KEY + "status"), x, y, WHITE, true);
            }
            y += LINE;
            for (FormattedCharSequence line : wrap(Component.translatable(menu.status().translationKey()))) {
                if (!counter.take()) {
                    break;
                }
                graphics.drawString(font, line, x, y, WHITE, true);
                y += LINE;
            }
            y += 15;

            for (String failure : failureLines(menu.startFailureKey())) {
                for (FormattedCharSequence line : wrap(Component.translatable(failure))) {
                    if (counter.take()) {
                        graphics.drawString(font, line, x, y, RED, true);
                        y += LINE;
                    }
                }
                y += 5;
            }

            if (menu.progressValue() > 0) {
                int percent = menu.progressValue() * 100 / menu.maxProgressValue();
                if (counter.take()) {
                    graphics.drawString(font, Component.translatable(KEY + "status.crafting.progress",
                            percent + "%"), x, y, WHITE, true);
                }
                y += 15;
            }

            // The original drew these two lines only while a recipe was active and only when it really did
            // run more than one copy (GuiMachineController:136-154).
            if (counter.take()) {
                graphics.drawString(font, Component.translatable(KEY + "parallelism",
                        Math.max(menu.parallelism(), 1)), x, y, WHITE, true);
            }
            y += 15;
            if (counter.take()) {
                graphics.drawString(font, Component.translatable(KEY + "max_parallelism",
                        menu.maxParallelism()), x, y, WHITE, true);
            }
            y += LINE;
        }

        // The original drew this after the last rendered line (GuiMachineController:157-172). 0.24.2 clamps it:
        // `block.footerY` is the original's position while the block is short, and the region's floor the moment
        // the block is tall —so a long payload moves the closing line up instead of pushing it onto the player
        // grid, which is the shipped 0.24.1 defect.
        graphics.drawString(font, Component.translatable(KEY + "footer",
                menu.usedTimeAvg(), formatSearchMillis(menu.searchUsedTimeAvg()), workMode()), x,
                Math.min(FOOTER_Y, block.footerY), WHITE, true);

        graphics.pose().popPose();
        graphics.disableScissor();
    }

    /**
     * The block's row budget: one counter shared by every drawing step, so the number of rows painted cannot
     * exceed the number the layout measured. A step that runs out is skipped rather than drawn over the player's
     * slots.
     */
    private static final class Counter {
        private final int budget;
        private int index;

        Counter(int budget) {
            this.budget = budget;
        }

        /** How many rows have been spent so far. */
        int index() {
            return this.index;
        }

        /** Consumes one row; {@code false} when the budget is spent and the caller must not draw. */
        boolean take() {
            if (this.index >= this.budget) {
                return false;
            }
            this.index++;
            return true;
        }
    }

    /**
     * The failure messages the information block prints, given the controller's failure key.
     *
     * <p>Returns the empty list when there is no failure, so the caller's loop draws <b>nothing</b> —not a
     * translated empty string, not a gap. That is the whole contract of the "no failure" sentinel, and it is why
     * this is a real method rather than an inline {@code if}: the offline harness cannot open a window or build a
     * font, so it drives this instead, and the call site in {@code drawInfo} is the same code.
     */
    static List<String> failureLines(@Nullable String failureKey) {
        return failureKey == null ? List.of() : List.of(failureKey);
    }

    /**
     * The work mode the original printed last on that line. It is {@code SYNC} here because this engine is
     * synchronous by construction —there is no recipe-search task and no thread pool —and saying so is more
     * useful than naming a mode the machine is not in.
     */
    private String workMode() {
        return menu.machine().timing().workMode().name();
    }

    /**
     * The original printed the search time in milliseconds with two decimals
     * ({@code MiscUtils.formatFloat(searchUsedTimeCache / 1000F, 2)}); the recorder keeps microseconds, so this
     * is that division.
     */
    private static String formatSearchMillis(int micros) {
        return String.format(java.util.Locale.ROOT, "%.2f", micros / 1000.0F);
    }

    /**
     * The blueprint line's name. When the machine's definition is not available here —a dedicated-server client
     * never receives machine definitions —the registry name is shown instead, which is still informative and
     * far better than claiming the slot is empty.
     */
    @Nullable
    private Component blueprintLabel(MachineControllerBlockEntity machine) {
        if (!menu.hasBlueprintMachine()) {
            return null;
        }
        Component name = machine.blueprintMachineName();
        if (name != null) {
            return name;
        }
        var id = machine.blueprintMachineId();
        return id != null ? Component.literal(id.toString())
                : Component.translatable(KEY + "blueprint.none");
    }

    /**
     * The merged smart data interface: the machine's <b>single</b> declared interface type —its heading, its
     * current value, its footer —and the field the value is edited through.
     *
     * <h2>What the original's screen had, and what survives</h2>
     *
     * <p>{@code GuiContainerSmartInterface} printed a title naming the bound machine and the current index, then
     * the type's {@code header} / value / {@code footer} lines, with previous/next buttons switching between
     * bound machines and a value field. Merged into the controller:
     *
     * <ul>
     *   <li><b>The previous/next pair is gone</b>, and that is the merge's whole simplification rather than a
     *       loss: a separate interface could be bound to several controllers, so it had to page through them. A
     *       controller has exactly one machine —itself —so there is nothing to switch to. The declared types of
     *       that one machine are a short list, and the loader rejects duplicate names, so this draws the
     *       highest-priority declaration, which is the one the original's own
     *       {@code getFirstSmartInterfaceType} chose for a fresh binding.</li>
     *   <li><b>"未找到绑定机械。" is gone</b> for the same reason: a controller always knows its own machine, and
     *       when it has not formed this method draws nothing at all.</li>
     *   <li><b>The header/value/footer lines and the edit field survive.</b> The value line is the type's own
     *       {@code value} format when it has one, and the original's {@code gui.smartinterface.value} wording
     *       otherwise ({@code SmartInterfaceType#formatValue}).</li>
     * </ul>
     *
     * @return the next free text row
     */
    private int drawSmartInterface(GuiGraphics graphics, int x, int y, Counter counter) {
        SmartInterfaceType type = shownInterface();
        if (type == null) {
            return y;
        }
        Float value = menu.smartInterfaceValue(type.type());
        float current = value == null ? type.defaultValue() : value;

        if (counter.take()) {
            graphics.drawString(font, interfaceLabel(type, current), x, y, WHITE, true);
        }
        y += LINE;

        for (String line : new String[] {type.header(), type.footer()}) {
            if (line.isEmpty()) {
                continue;
            }
            for (FormattedCharSequence wrapped : wrap(Component.translatable(line))) {
                if (counter.take()) {
                    graphics.drawString(font, wrapped, x, y, WHITE, true);
                }
                y += LINE;
            }
        }
        return y + 5;
    }

    /**
     * The one declared interface type this screen shows: the highest-priority declaration of the formed
     * machine, exactly the one the original bound a fresh interface to
     * ({@code DynamicMachine#getFirstSmartInterfaceType}). {@code null} when nothing is formed or the machine
     * declares no interface at all —in which case the field is not offered either.
     */
    @Nullable
    private SmartInterfaceType shownInterface() {
        return SmartInterfaceType.highestPriority(menu.smartInterfaces()).orElse(null);
    }

    /**
     * The value line: the type's own name, then the type's {@code value} format when it has one, and the
     * original's {@code gui.smartinterface.value} wording otherwise.
     *
     * <p>Naming the type is the one thing this line adds over the original's, and it exists only because the
     * original's paging buttons are gone: the original's screen showed whichever interface had been selected,
     * while every declared type of this machine now shares one line and one field.
     */
    private Component interfaceLabel(SmartInterfaceType type, float value) {
        Component name = Component.literal(type.type());
        String formatted = type.formatValue(value);
        return Component.translatable(SMART_KEY + "label", name,
                formatted != null ? Component.literal(formatted)
                        : Component.translatable(SMART_KEY + "value", value));
    }

    private List<FormattedCharSequence> wrap(Component text) {
        return font.split(text, WRAP_WIDTH);
    }
}
