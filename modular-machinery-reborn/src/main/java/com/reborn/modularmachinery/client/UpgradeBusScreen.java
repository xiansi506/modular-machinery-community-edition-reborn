package com.reborn.modularmachinery.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.UpgradeBusBlockEntity;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.menu.UpgradeBusMenu;
import com.reborn.modularmachinery.upgrade.UpgradeStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The upgrade bus screen, reproduced from the original {@code GuiContainerUpgradeBus} (177 lines).
 *
 * <h2>Panel</h2>
 *
 * <p>One image, {@code guiupgradebus.png}, blitted whole; {@code xSize} is {@code GuiContainer}'s unchanged
 * default of 176 and the original only raised {@code ySize} to <b>213</b>
 * ({@code GuiContainerUpgradeBus.java:42-43}), so the panel is 176×213 — the same window the controller and the
 * blueprint panel use. The texture here is a byte-for-byte copy of the original's.
 *
 * <h2>Slots</h2>
 *
 * <p>The original blitted one 18×18 frame per slot from the texture's {@code (7, 130)}, at
 * {@code (slotX - 1, slotY - 1)} for the slot origin {@code (8, 17)} with 18-pixel steps and a new row every
 * three ({@code :164-174}). The frames are painted by this method; the slots themselves are the menu's, at the
 * same coordinates.
 *
 * <h2>Text</h2>
 *
 * <p>This is the part that needed care. The original drew its title unscaled at {@code (7, 5)} and then the
 * whole information column inside a <b>0.72-scaled matrix</b>, starting at {@code (92, 23)} with a 10-pixel
 * line pitch, wrapped to {@code 89 * (1 / 0.72)} screen pixels, showing at most 15 lines
 * ({@code :27-35}, {@code :98-119}).
 *
 * <p>In 1.12.2 that scaled column lived in the foreground layer, whose coordinate system is already translated
 * to the panel's corner. 1.20.1's {@code renderLabels} does <b>not</b> scale, so a scaled column cannot simply
 * be drawn there with the original's numbers. Rather than approximate the visual result with screen-space
 * arithmetic, this screen draws the column in {@code renderBg} — the only layer {@code GuiGraphics} lets us
 * translate by the panel origin — with exactly the original's numbers inside the matrix:
 * {@code translate(leftPos + 92 * 0.72, topPos + 23 * 0.72)} then {@code scale(0.72)}. The row pitch of 10 and
 * the wrap width of {@code 89 / 0.72} are therefore applied in the very units the original applied them in,
 * which is the closest a reproduction can get without a 1.12.2 matrix stack.
 *
 * <p><b>Honest divergence:</b> the original drew this column <i>above</i> the item icons; here the slots are
 * drawn after {@code renderBg}, so an item in a bus slot overlays the text if the two ever overlap. With the
 * original's own coordinates they do not: the text starts at scaled x = 92 (screen x ≈ 66) and the slot grid
 * ends at x = 44, while the scrollbar sits at 156.
 *
 * <h2>Scrollbar</h2>
 *
 * <p>The original used {@code GuiScrollbar}, a 12×15 sprite from the <b>vanilla</b> creative-inventory tab
 * sheet ({@code GuiScrollbar.java:9,22-30}). Its two UVs are {@code (232, 0)} for the track and
 * {@code (244, 0)} for the thumb, which is what is drawn here from the same vanilla texture.
 */
public final class UpgradeBusScreen extends AbstractContainerScreen<UpgradeBusMenu> {

    /** The original's {@code TEXTURES_UPGRADE_BUS} ({@code GuiContainerUpgradeBus.java:26}). */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guiupgradebus.png");

    /** The original's {@code GuiScrollbar.TEXTURES_TABS} ({@code GuiScrollbar.java:9}) — a vanilla sheet. */
    private static final ResourceLocation SCROLLBAR_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    private static final String KEY = "gui.modular_machinery_reborn.upgradebus.";

    /** The original's {@code FONT_SCALE}. */
    private static final float FONT_SCALE = 0.72F;
    /** The original's {@code SLOT_START_X} / {@code SLOT_START_Y}, in panel pixels. */
    private static final int SLOT_START_X = 8;
    private static final int SLOT_START_Y = 17;
    /** The original's {@code MAX_DESC_LINES}. */
    private static final int MAX_DESC_LINES = 15;
    /** The original's {@code TEXT_DRAW_OFFSET_X} / {@code TEXT_DRAW_OFFSET_Y}, inside the scaled matrix. */
    private static final int TEXT_DRAW_OFFSET_X = 92;
    private static final int TEXT_DRAW_OFFSET_Y = 23;
    private static final int LINE = 10;
    /** The original's wrap width expression: {@code (int) (89 * (1 / FONT_SCALE))}. */
    private static final int WRAP_WIDTH = (int) (89 * (1.0 / FONT_SCALE));
    /** The original's title position, unscaled ({@code :98}). */
    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 5;
    /** The original's slot frame source rectangle ({@code :167}). */
    private static final int SLOT_FRAME_U = 7;
    private static final int SLOT_FRAME_V = 130;
    private static final int SLOT_FRAME_SIZE = 18;
    /** The original's {@code GuiScrollbar} metrics ({@code :31-33}, {@code GuiScrollbar.java:12-13}). */
    private static final int SCROLLBAR_LEFT = 156;
    private static final int SCROLLBAR_TOP = 17;
    private static final int SCROLLBAR_HEIGHT = 106;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_TRACK_U = 232;
    private static final int SCROLLBAR_THUMB_U = 244;
    private static final int SCROLLBAR_V = 0;

    private static final int WHITE = 0xFFFFFF;
    private static final int LABEL_COLOR = 0x404040;

    private int scroll;
    private int scrollRange;

    /** The wrapped lines the last frame produced; the scrollbar's range is derived from them. */
    private List<FormattedCharSequence> lines = List.of();

    public UpgradeBusScreen(UpgradeBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        // The original's setWidthHeight() was empty (:152-154), so xSize is GuiContainer's default and only
        // ySize was raised.
        this.imageHeight = 213;
        // Vanilla puts the label at imageHeight - 94 = 119, which is where the original's inherited 1.12.2
        // label landed too (ySize - 96 + 2).
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        drawSlotFrames(graphics);
        drawScrollbar(graphics);
        drawText(graphics);
    }

    /** One 18×18 frame per bus slot, from the texture's own slot sheet — the original's {@code :164-174}. */
    private void drawSlotFrames(GuiGraphics graphics) {
        int x = SLOT_START_X;
        int y = SLOT_START_Y;
        for (int index = 0; index < this.menu.slotCount(); index++) {
            graphics.blit(TEXTURE, this.leftPos + x - 1, this.topPos + y - 1, SLOT_FRAME_U, SLOT_FRAME_V,
                    SLOT_FRAME_SIZE, SLOT_FRAME_SIZE);
            x += 18;
            if ((index + 1) % 3 == 0) {
                x = SLOT_START_X;
                y += 18;
            }
        }
    }

    /**
     * The original's title, unscaled, in {@code renderLabels} — where 1.20.1 draws it at panel coordinates
     * without any further translation, exactly as 1.12.2's foreground layer did.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
        graphics.drawString(this.font, Component.translatable(UpgradeBusMenu.TITLE_KEY), TITLE_X, TITLE_Y, WHITE,
                true);
    }

    /**
     * The information column, inside a matrix scaled by the original's 0.72 and translated so that the original's
     * {@code TEXT_DRAW_OFFSET} lands where it would have.
     */
    private void drawText(GuiGraphics graphics) {
        this.lines = wrap(buildLines());
        this.scrollRange = Math.max(0, this.lines.size() - MAX_DESC_LINES);

        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + TEXT_DRAW_OFFSET_X * FONT_SCALE,
                this.topPos + TEXT_DRAW_OFFSET_Y * FONT_SCALE, 0.0F);
        graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0F);

        int y = 0;
        int last = Math.min(this.lines.size(), MAX_DESC_LINES + this.scroll);
        for (int i = this.scroll; i < last; i++) {
            graphics.drawString(this.font, this.lines.get(i), 0, y, WHITE, true);
            y += LINE;
        }
        graphics.pose().popPose();
    }

    /**
     * The original's description list, assembled in its own order ({@code :102-113}): the bound machines first,
     * each followed by the incompatibility warnings of the upgrades that do not fit it, then the upgrades the
     * bus holds, each as {@code "Nx name"} plus its description lines.
     *
     * <p>The "no binding machinery" / "bound N machinery" pair comes from
     * {@code collectBoundedMachineDescriptions} ({@code :45-69}); the {@code "Nx name"} line and the description
     * block come from {@code collectUpgradeDescriptions} ({@code :71-86}).
     */
    private List<Component> buildLines() {
        List<Component> description = new ArrayList<>();
        UpgradeStack.Bag held = held();

        Map<net.minecraft.core.BlockPos, ResourceLocation> bound = boundMachines();
        if (bound.isEmpty()) {
            description.add(Component.translatable(KEY + "bounded.empty"));
        } else {
            description.add(Component.translatable(KEY + "bounded", bound.size()));
        }
        bound.forEach((pos, machine) -> {
            Component name = MachineRegistry.byId(machine)
                    .map(definition -> (Component) definition.displayName())
                    .orElseGet(() -> Component.literal(machine.toString()));
            // The original's `"%s (%s)"` with MiscUtils.posToString, which is the bare (x, y, z) triple.
            description.add(Component.literal(name.getString() + " (" + pos.getX() + ", " + pos.getY() + ", "
                    + pos.getZ() + ")"));
            for (UpgradeStack upgrade : held.incompatibleWith(machine)) {
                // The original prefixed three spaces and let the language file's own §e colour the line.
                description.add(Component.literal("   " + Component.translatable(KEY + "incompatible",
                        upgrade.type().displayName().getString()).getString()));
            }
        });
        description.add(Component.empty());

        for (UpgradeStack upgrade : held.stacks()) {
            description.add(Component.literal(upgrade.describe()));
            if (upgrade.type().descriptions().isEmpty()) {
                continue;
            }
            for (String line : upgrade.type().descriptions()) {
                description.add(Component.literal(line));
            }
            description.add(Component.empty());
        }
        return description;
    }

    /**
     * The upgrades the bus currently holds.
     *
     * <p>Read straight from the block entity through {@link UpgradeBusBlockEntity#upgrades()}, which is the very
     * method the server uses — the client has the same slots (the menu syncs them) and the same registry (upgrade
     * declarations reload on both sides), so the list is rebuilt here rather than mirrored through a packet, and
     * the two sides cannot drift.
     *
     * <p><b>Per-copy NBT:</b> a <i>dynamic</i> upgrade's tag travels with its carrier item and is now carried
     * through {@link com.reborn.modularmachinery.upgrade.UpgradeStack#customData()} — the arithmetic behind it is
     * in {@code UpgradeEffects.read}, and {@code UpgradeItemNbt} is the single place the tag is read from and
     * written to an item. What this listing still does is show <b>the declaration</b>: the tag is opaque by design
     * (the original's own {@code readItemNBT} was a bare assignment, because the data belonged to whoever wrote
     * it), so there is nothing truthful to render from it here. Nothing that <i>affects a craft</i> depends on it
     * either — a declared modifier is the whole effect, which is why the handler layer that used to read this data
     * was not ported.
     */
    private UpgradeStack.Bag held() {
        return this.menu.bus().upgrades();
    }

    /** The bound machines, as the block entity received them in its update tag. */
    private Map<BlockPos, ResourceLocation> boundMachines() {
        return this.menu.bus().boundMachines();
    }

    private List<FormattedCharSequence> wrap(List<Component> description) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : description) {
            if (line.getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
                continue;
            }
            wrapped.addAll(this.font.split(line, WRAP_WIDTH));
        }
        return wrapped;
    }

    // ------------------------------------------------------------------ scrollbar

    /**
     * {@code GuiScrollbar#draw} ({@code GuiScrollbar.java:21-31}): the thumb slides proportionally between the
     * top and {@code height - 15}, and the track sprite is used when there is nothing to scroll — which is also
     * what makes the scrollbar invisible on a bus whose list fits.
     */
    private void drawScrollbar(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos + SCROLLBAR_LEFT;
        int y = this.topPos + SCROLLBAR_TOP;
        if (this.scrollRange == 0) {
            graphics.blit(SCROLLBAR_TEXTURE, x, y, SCROLLBAR_TRACK_U, SCROLLBAR_V, SCROLLBAR_WIDTH,
                    SCROLLBAR_THUMB_HEIGHT);
            return;
        }
        int offset = this.scroll * (SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT) / this.scrollRange;
        graphics.blit(SCROLLBAR_TEXTURE, x, y + offset, SCROLLBAR_THUMB_U, SCROLLBAR_V, SCROLLBAR_WIDTH,
                SCROLLBAR_THUMB_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // The original's handleMouseInput fed the wheel straight to the scrollbar (:126-133).
        if (delta != 0.0) {
            setScroll(this.scroll - (int) Math.signum(delta));
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overScrollbar(mouseX, mouseY) && this.scrollRange > 0) {
            // GuiScrollbar#click (:93-104): proportional, rounded — kept so the two implementations agree.
            int local = (int) (mouseY - (this.topPos + SCROLLBAR_TOP));
            int value = local * 2 * this.scrollRange / SCROLLBAR_HEIGHT;
            setScroll((value + 1) >> 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        double x = mouseX - (this.leftPos + SCROLLBAR_LEFT);
        double y = mouseY - (this.topPos + SCROLLBAR_TOP);
        return x > 0 && x <= SCROLLBAR_WIDTH && y > 0 && y <= SCROLLBAR_HEIGHT;
    }

    private void setScroll(int value) {
        this.scroll = Math.max(0, Math.min(this.scrollRange, value));
    }
}
