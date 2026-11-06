package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.compat.ae2.MePatternProviderBlockEntity.WorkMode;
import com.reborn.modularmachinery.menu.MePatternProviderMenu;
import com.reborn.modularmachinery.network.MePatternPacket;
import com.reborn.modularmachinery.network.MePatternProviderActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.reborn.modularmachinery.menu.ProviderScreenLayout.*;

/** The original panel art, with real input slots, readable quantities and server-authoritative controls. */
public final class MePatternProviderScreen extends AbstractContainerScreen<MePatternProviderMenu> {
    private static final ResourceLocation PANEL = new ResourceLocation("modular_machinery_reborn", "textures/gui/mepatternprovider.png");
    private static final String KEY = "gui.modular_machinery_reborn.mepatternprovider.";
    private static final int[] MODE_U = {140, 176, 122, 158, 104};
    private EditBox groupBox;
    private Button applyButton, returnButton, viewButton, fluidButton, previousButton, nextButton;
    private ModeButton modeButton;
    private boolean bufferView, groupDirty, settingGroup, draggingScroll;
    private int scroll, lastServerGroup;
    private FluidStack manualFluid = FluidStack.EMPTY;

    public MePatternProviderScreen(MePatternProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 104;
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static Component modeName(int ordinal) {
        WorkMode[] modes = WorkMode.values();
        return text("mode." + modes[Math.max(0, Math.min(modes.length - 1, ordinal))].name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override protected void init() {
        // P10-06 (0.121.37): distinguish a *clean* old value from a real draft on rebuild. The old code
        // always carried the box text as a draft and then advanced lastServerGroup to the fresh summary,
        // so a summary arriving around a rebuild kept the stale unedited value forever. Carry the old
        // text only when groupDirty (a real user edit); a clean rebuild adopts the latest server summary,
        // and programmatic fill is wrapped in settingGroup so it never trips the responder into dirty.
        boolean carryDraft = groupBox != null && groupDirty;
        String carryText = carryDraft ? groupBox.getValue() : null;
        boolean focused = groupBox != null && groupBox.isFocused();
        super.init();
        groupBox = new EditBox(font, leftPos + CONTROLS_X, topPos + 66, 45, 14, text("group"));
        groupBox.setMaxLength(6);
        groupBox.setFilter(value -> value.matches("-?[0-9]{0,5}"));
        groupBox.setResponder(value -> { if (!settingGroup) groupDirty = true; });
        if (carryDraft) {
            groupBox.setValue(carryText);
        } else {
            settingGroup = true;
            groupBox.setValue(Integer.toString(serverGroup()));
            settingGroup = false;
            groupDirty = false;
        }
        addRenderableWidget(groupBox);
        if (focused) setFocused(groupBox);
        lastServerGroup = serverGroup();
        applyButton = addButton("apply", 227, 65, 21, 16, () -> applyGroup());
        returnButton = addButton("return", CONTROLS_X, 85, CONTROLS_WIDTH, 17, () -> action(1, 0));
        viewButton = addButton("buffer", CONTROLS_X, 107, CONTROLS_WIDTH, 17, () -> {
            bufferView = !bufferView;
            menu.setClientBufferView(bufferView);
            scroll = 0;
            draggingScroll = false;
        });
        fluidButton = addButton("fluid", CONTROLS_X, 128, CONTROLS_WIDTH, 17, () -> action(3, 0));
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(leftPos + CONTROLS_X, topPos + 149, 31, 16).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(leftPos + CONTROLS_X + 37, topPos + 149, 31, 16).build());
        modeButton = addRenderableWidget(new ModeButton());
        menu.setClientBufferView(bufferView);
        refreshControls();
    }

    private Button addButton(String key, int x, int y, int w, int h, Runnable action) {
        return addRenderableWidget(Button.builder(text(key), button -> action.run()).bounds(leftPos + x, topPos + y, w, h).build());
    }

    private int serverGroup() { return menu.clientSummary().contains("group") ? menu.clientSummary().getInt("group") : -1; }
    private void action(int type, int value) { MePatternProviderActionPacket.sendAction(menu.provider().getBlockPos(), type, value); }
    private void setGroupText(int value) {
        settingGroup = true;
        groupBox.setValue(Integer.toString(value));
        settingGroup = false;
        groupDirty = false;
    }
    private void applyGroup() {
        Integer value = parseGroup(groupBox.getValue());
        if (!applyButton.active || value == null) return;
        action(2, value);
        groupDirty = false;
        groupBox.setFocused(false);
        setFocused(null);
    }
    private void changePage(int delta) {
        int page = menu.clientSummary().getInt("page");
        int pages = Math.max(1, menu.clientSummary().getInt("pages"));
        int next = Math.max(0, Math.min(pages - 1, page + delta));
        if (next != page) { action(4, next); scroll = 0; }
    }
    private void changeMode(int delta) {
        CompoundTag summary = menu.clientSummary();
        int current = summary.contains("pending_mode") ? summary.getInt("pending_mode") : menu.clientWorkMode().ordinal();
        MePatternProviderActionPacket.sendSetMode(menu.provider().getBlockPos(), Math.floorMod(current + delta, WorkMode.values().length));
    }

    @Override protected void containerTick() {
        super.containerTick();
        groupBox.tick();
        refreshControls();
    }

    private void refreshControls() {
        if (groupBox == null) return;
        CompoundTag summary = menu.clientSummary();
        int group = serverGroup();
        if (group != lastServerGroup) {
            lastServerGroup = group;
            if (!groupBox.isFocused() || !groupDirty) setGroupText(group);
        }
        boolean pending = summary.getBoolean("pending");
        boolean automatic = menu.clientWorkMode() == WorkMode.ISOLATION_INPUT;
        boolean editable = !pending && !automatic;
        Integer draft = parseGroup(groupBox.getValue());
        groupBox.setEditable(editable);
        groupBox.setTextColor(draft == null ? 0xff7777 : editable ? 0xffffff : 0xa0a0a0);
        Component groupHint = text(automatic ? "group.automatic" : pending ? "pending.hint" : draft == null ? "group.invalid" : "group.hint").append("\n").append(text("group.current", group));
        groupBox.setTooltip(Tooltip.create(groupHint));
        applyButton.active = editable && draft != null && draft != group;
        applyButton.setTooltip(Tooltip.create(groupHint));
        returnButton.active = !summary.getBoolean("return_all") && (summary.getInt("total") > 0 || summary.getBoolean("locked"));
        returnButton.setMessage(text(pending ? "returning" : "return"));
        returnButton.setTooltip(Tooltip.create(text(pending ? "pending.hint" : "return.hint")));
        viewButton.setMessage(text(bufferView ? "patterns" : "buffer"));
        viewButton.setTooltip(Tooltip.create(text(bufferView ? "patterns.hint" : "buffer.hint")));
        fluidButton.active = !pending && FluidUtil.getFluidHandler(menu.getCarried()).isPresent();
        fluidButton.setTooltip(Tooltip.create(text(pending ? "pending.hint" : "fluid.hint")));
        int page = summary.getInt("page"), pages = Math.max(1, summary.getInt("pages"));
        previousButton.visible = nextButton.visible = bufferView;
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pages;
        previousButton.setTooltip(Tooltip.create(text("previous")));
        nextButton.setTooltip(Tooltip.create(text("next")));
        manualFluid = FluidStack.loadFluidStackFromNBT(summary.getCompound("manual_fluid"));
        scroll = Math.min(scroll, maxScroll(rows().size()));
        modeButton.setMessage(modeName(menu.clientWorkMode().ordinal()));
        modeButton.setTooltip(Tooltip.create(modeTooltip()));
    }

    private Component modeTooltip() {
        var result = text("work_mode.desc");
        String[] descriptions = {"default.desc", "crafting_lock_mode.desc", "enhanced_blocking_mode.desc", "blocking_mode.desc", "isolation_input.desc"};
        for (int i = 0; i < descriptions.length; i++) {
            result.append("\n").append(modeName(i).copy().withStyle(i == menu.clientWorkMode().ordinal() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (i == menu.clientWorkMode().ordinal()) result.append(text("current"));
        }
        result.append("\n\n").append(text(descriptions[menu.clientWorkMode().ordinal()]).withStyle(ChatFormatting.GRAY));
        if (menu.clientSummary().contains("pending_mode")) result.append("\n").append(text("mode.pending", modeName(menu.clientSummary().getInt("pending_mode"))).withStyle(ChatFormatting.GOLD));
        return result.append("\n").append(text("mode.hint"));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshControls();
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = mouseX - leftPos, y = mouseY - topPos;
        List<Component> tooltip = tooltipAt(x, y);
        if (!tooltip.isEmpty()) graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        else renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(PANEL, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // Cover unused tank/slot artwork; keep the original frame and player inventory.
        graphics.fill(leftPos + 177, topPos + 25, leftPos + 254, topPos + 194, 0xffc6c6c6);
        drawSlot(graphics, MANUAL_X, MANUAL_Y);
        drawSlot(graphics, MANUAL_X + 18, MANUAL_Y);
        drawSlot(graphics, FLUID_X, MANUAL_Y);
        int filled = manualFluid.isEmpty() ? 0 : 16;
        GuiFluidRenderer.draw(graphics, manualFluid, leftPos + FLUID_X, topPos + MANUAL_Y + 16 - filled, 16, filled);
        if (bufferView) drawBuffer(graphics);
        else {
            var patterns = menu.clientPatterns();
            for (int i = 0; i < Math.min(36, patterns.size()); i++) {
                if (!patterns.get(i).isEmpty()) {
                    int x = leftPos + GRID_X + i % 9 * 18, y = topPos + GRID_Y + i / 9 * 18;
                    graphics.renderItem(patterns.get(i), x, y);
                    graphics.renderItemDecorations(font, patterns.get(i), x, y);
                }
            }
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xff373737);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 17, topPos + y + 17, 0xffffffff);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xff8b8b8b);
    }

    private ListTag rows() { return menu.clientSummary().getList("rows", Tag.TAG_COMPOUND); }
    private Component rowName(CompoundTag row) {
        try {
            Component name = Component.Serializer.fromJson(row.getString("name"));
            if (name != null) return name;
        } catch (RuntimeException ignored) { }
        return text("unknown");
    }
    private Component shortened(Component name, int width) {
        if (font.width(name) <= width) return name;
        return Component.literal(font.plainSubstrByWidth(name.getString(), Math.max(0, width - font.width("…"))) + "…");
    }
    private void drawBuffer(GuiGraphics graphics) {
        ListTag rows = rows();
        graphics.fill(leftPos + GRID_X - 1, topPos + GRID_Y - 1, leftPos + GRID_X + GRID_WIDTH, topPos + GRID_Y + GRID_HEIGHT, 0xff282830);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, text("buffer.empty"), leftPos + 85, topPos + 60, 0xffaaaaaa);
            return;
        }
        for (int i = 0; i < VISIBLE_ROWS && i + scroll < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i + scroll);
            int y = topPos + GRID_Y + i * ROW_HEIGHT;
            graphics.fill(leftPos + 8, y, leftPos + 162, y + 17, i % 2 == 0 ? 0xff34343d : 0xff2d2d35);
            ItemStack icon = row.contains("icon") ? ItemStack.of(row.getCompound("icon")) : ItemStack.EMPTY;
            if (!icon.isEmpty()) graphics.renderItem(icon, leftPos + 10, y + 1);
            else {
                int color = switch (row.getString("kind")) { case "fluid" -> 0xff68aafa; case "gas" -> 0xffb789da; default -> 0xffe4b65e; };
                graphics.fill(leftPos + 12, y + 4, leftPos + 22, y + 14, color);
            }
            graphics.drawString(font, shortened(rowName(row), 100), leftPos + 29, y + 5, 0xffeeeeee, false);
            String amount = GuiAmounts.compact(row.getLong("amount"));
            graphics.drawString(font, amount, leftPos + 159 - font.width(amount), y + 5, 0xffffdf95, false);
        }
        int trackY = topPos + GRID_Y;
        graphics.fill(leftPos + SCROLL_X, trackY, leftPos + SCROLL_X + SCROLL_WIDTH, trackY + GRID_HEIGHT, 0xff19191e);
        int max = maxScroll(rows.size());
        if (max > 0) {
            int thumb = Math.max(10, GRID_HEIGHT * VISIBLE_ROWS / rows.size());
            int y = trackY + (GRID_HEIGHT - thumb) * scroll / max;
            graphics.fill(leftPos + SCROLL_X + 1, y, leftPos + SCROLL_X + SCROLL_WIDTH - 1, y + thumb, 0xffb4b4c6);
        }
    }

    private List<Component> tooltipAt(double x, double y) {
        if (x >= FLUID_X && x < FLUID_X + 16 && y >= MANUAL_Y && y < MANUAL_Y + 16) {
            return List.of(manualFluid.isEmpty() ? text("fluid.empty") : manualFluid.getDisplayName(),
                    text("fluid.amount", GuiAmounts.exact(manualFluid.getAmount()), GuiAmounts.exact(Integer.MAX_VALUE)), text("fluid.hint"));
        }
        if (x >= CONTROLS_X && x < 248 && y >= 181 && y < 194) {
            if (menu.clientSummary().contains("pending_mode")) return List.of(text("pending.hint"), text("mode.pending", modeName(menu.clientSummary().getInt("pending_mode"))));
            return List.of(text(menu.clientSummary().getBoolean("pending") ? "pending.hint" : menu.clientSummary().getBoolean("locked") ? "locked.hint" : "network.hint"));
        }
        if (!inGrid(x, y)) return List.of();
        if (!bufferView) {
            int slot = patternSlotAt(x, y);
            if (slot >= 0 && slot < menu.clientPatterns().size()) {
                ItemStack stack = menu.clientPatterns().get(slot);
                if (!stack.isEmpty()) return getTooltipFromContainerItem(stack);
                return List.of(text("pattern.empty"), text("patterns.hint"));
            }
            return List.of();
        }
        if (x >= SCROLL_X) return List.of(text("buffer.scroll"));
        int index = scroll + (int) ((y - GRID_Y) / ROW_HEIGHT);
        if (index >= rows().size()) return List.of(text("buffer.empty"));
        CompoundTag row = rows().getCompound(index);
        int slot = row.getInt("slot");
        List<Component> lines = new ArrayList<>();
        lines.add(rowName(row));
        lines.add(text(slot < 0 ? "buffer.shared" : "buffer.slot", slot + 1));
        String kind = row.getString("kind");
        if (List.of("item", "fluid", "gas", "unknown").contains(kind)) lines.add(text("buffer.kind." + kind));
        String quantityKey = switch (kind) { case "fluid", "gas" -> "buffer.millibuckets"; case "unknown" -> "buffer.unresolved"; case "item" -> "buffer.items"; default -> "buffer.quantity"; };
        lines.add(text(quantityKey, GuiAmounts.exact(row.getLong("amount"))));
        lines.add(text("buffer.scroll").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, shortened(title, 166), 8, 6, 0x404040, false);
        graphics.drawString(font, bufferView ? text("buffer.count", menu.clientSummary().getInt("total")) : text("patterns.count", menu.clientPatterns().stream().filter(stack -> !stack.isEmpty()).count(), 36), 8, 17, 0x606060, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, text("manual"), CONTROLS_X, 25, 0x404040, false);
        graphics.drawString(font, text("group"), CONTROLS_X, 55, 0x404040, false);
        CompoundTag summary = menu.clientSummary();
        if (bufferView) graphics.drawString(font, (summary.getInt("page") + 1) + "/" + Math.max(1, summary.getInt("pages")), CONTROLS_X, 14, 0x404040, false);
        graphics.drawString(font, GuiAmounts.compact(manualFluid.getAmount()) + " mB", CONTROLS_X, 170, 0x404040, false);
        boolean pending = summary.getBoolean("pending"), locked = summary.getBoolean("locked");
        String state = pending ? "returning" : locked ? "locked" : !summary.contains("online") ? "network.unknown" : summary.getBoolean("online") ? "network.online" : "network.offline";
        graphics.drawString(font, text(state), CONTROLS_X, 184, pending || locked ? 0x996000 : summary.getBoolean("online") ? 0x307030 : 0x904040, false);
    }

    private void setScroll(double localY) {
        int max = maxScroll(rows().size());
        if (max == 0) { scroll = 0; return; }
        int thumb = Math.max(10, GRID_HEIGHT * VISIBLE_ROWS / rows().size());
        scroll = Math.max(0, Math.min(max, (int) Math.round((localY - GRID_Y - thumb / 2.0) / (GRID_HEIGHT - thumb) * max)));
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (inGrid(x, y)) {
            groupBox.setFocused(false);
            setFocused(null);
            if (bufferView) {
                if (button == 0 && x >= SCROLL_X && maxScroll(rows().size()) > 0) { draggingScroll = true; setScroll(y); }
                return true;
            }
            int slot = patternSlotAt(x, y);
            if (slot >= 0 && (button == 0 || button == 1)) {
                if (button == 0 && !menu.getCarried().isEmpty()) MePatternPacket.sendSet(menu.provider().getBlockPos(), slot, menu.getCarried());
                else if (button == 1 && menu.getCarried().isEmpty()) MePatternPacket.sendClear(menu.provider().getBlockPos(), slot);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (draggingScroll && button == 0) { draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll && button == 0) { setScroll(y - topPos); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (bufferView && inGrid(x - leftPos, y - topPos)) {
            scroll = Math.max(0, Math.min(maxScroll(rows().size()), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (groupBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_TAB) return super.keyPressed(key, scanCode, modifiers);
            if (key == GLFW.GLFW_KEY_ESCAPE) { setGroupText(serverGroup()); groupBox.setFocused(false); setFocused(null); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { applyGroup(); return true; }
            return groupBox.keyPressed(key, scanCode, modifiers) || groupBox.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class ModeButton extends Button {
        ModeButton() { super(leftPos + 233, topPos + 7, 16, 16, modeName(menu.clientWorkMode().ordinal()), ignored -> changeMode(1), DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(PANEL, getX(), getY(), 194, 196, 16, 16);
            graphics.blit(PANEL, getX(), getY(), MODE_U[menu.clientWorkMode().ordinal()], 196, 16, 16);
            if (isHoveredOrFocused()) graphics.blit(PANEL, getX(), getY(), 212, 196, 16, 16);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button == 1 && active && visible && isMouseOver(x, y)) {
                playDownSound(minecraft.getSoundManager()); changeMode(-1); return true;
            }
            return super.mouseClicked(x, y, button);
        }
    }
}
