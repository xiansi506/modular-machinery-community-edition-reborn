package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.menu.HatchGroupMenu;
import com.reborn.modularmachinery.network.HatchGroupUpdatePacket;
import com.reborn.modularmachinery.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 「输入组 ID 配置」窗口，照旧版 {@code GuiContainerGroupInputConfig}（146 行）复刻（A2）。
 *
 * <h2>版面</h2>
 *
 * <p>旧版 {@code setWidthHeight()} 是空实现（{@code :30-32}），所以面板就是
 * {@code GuiContainer} 的默认 {@code 176 × 166}，贴图是 {@code guismartinterface.png}——与本模组
 * 其它控制窗口同一张。文字位置照旧版（{@code :40-50}）：
 *
 * <pre>
 * 标题     (4,  4)
 * 说明     (6, 24)     标题后 +2 / +20
 * 当前ID： (6, 49)     说明后 +25
 * </pre>
 *
 * <p>控件也照旧版（{@code :132-144}），坐标都以屏幕中心为基准：
 *
 * <pre>
 * 输入框   (w/2 - 15, h/2 - 35)  95 x 10，最多 10 个字符
 * 启用 / 禁用按钮  (w/2 + 31, h/2 - 23)  50 x 20，共用一个位置、按状态只显示一个
 * </pre>
 *
 * <h2>与旧版的两处取舍</h2>
 *
 * <p>① 旧版点「启用组」时只发包告诉服务端「启用」，组号用服务端记着的旧值，并把输入框重置成旧值
 * （{@code :102-115}）——也就是刚敲进去的数字会被丢掉。本版单项开关操作不改组号，
 * 已开启时输入框失焦先单独提交数字，不再把旧组号跟随开关写回。② 旧版没有回车提交，只有「点别处」才提交（{@code :73-89}）；这里
 * 补了回车，因为 1.20.1 的输入框本来就该能这么用，且两种提交走同一个方法。
 */
public final class HatchGroupScreen extends AbstractContainerScreen<HatchGroupMenu> {

    /** 与并行控制器窗口同一张贴图（旧版 {@code GuiContainerBase.TEXTURES_EMPTY_GUI}）。 */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guismartinterface.png");

    private static final int WHITE = 0xFFFFFF;
    private static final int LABEL_COLOR = 0x404040;

    private static final int TITLE_X = 4;
    private static final int TITLE_Y = 4;
    private static final int TOOLTIP_X = 6;
    private static final int TOOLTIP_Y = 24;
    private static final int VALUE_LABEL_X = 6;
    private static final int VALUE_LABEL_Y = 49;

    private static final int FIELD_OFFSET_X = -15;
    private static final int FIELD_OFFSET_Y = -35;
    private static final int FIELD_WIDTH = 95;
    private static final int FIELD_HEIGHT = 10;
    private static final int FIELD_MAX_LENGTH = 10;

    private static final int BUTTON_OFFSET_X = 31;
    private static final int BUTTON_OFFSET_Y = -23;
    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 20;

    private EditBox valueField;
    private Button enableButton;
    private Button closeButton;
    /** 本地认为的开关状态：按下按钮立刻翻转，服务端下一次同步会把它纠正成真相。 */
    private boolean groupInput;
    /**
     * P10-08（0.121.40）：显式本地切换意图。点按钮后置位——这段时间里 containerTick 不用
     * 尚未确认的旧菜单值覆盖镜像（旧协议无法区分拒绝与延迟，意图在确认前如实保持）；
     * 菜单值追上镜像（服务器确认到达）后清除。
     *
     * <p>0.121.41 返工：{@link #init()} 重建时不再无条件回到菜单快照。等候确认期间重建
     * （窗口缩放等）保留仍待确认的同一意图与镜像，否则会留下「旧镜像 + 旧意图」的组合，
     * 确认到达后 containerTick 永远等不到 menu==mirror，按钮也停在旧状态。没有待确认
     * 意图时，重建照旧采用菜单快照。
     */
    private boolean localIntent;
    private boolean draftDirty;
    private boolean updatingValue;
    @javax.annotation.Nullable private Integer pendingValue;

    public HatchGroupScreen(HatchGroupMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int centreX = this.width / 2;
        int centreY = this.height / 2;

        // P10-08（0.121.41 返工）：重建策略与未确认意图保持一致——有意图时保留本地镜像
        // （containerTick 会在菜单追上后清除意图），没有意图时才采用菜单快照。
        if (!this.localIntent) {
            this.groupInput = this.menu.groupInput();
        }

        boolean focused = this.valueField != null && this.valueField.isFocused();
        String retained = this.valueField != null && (focused || this.draftDirty || this.pendingValue != null)
                ? this.valueField.getValue() : null;
        this.valueField = new EditBox(this.font, centreX + FIELD_OFFSET_X, centreY + FIELD_OFFSET_Y,
                FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        this.valueField.setMaxLength(FIELD_MAX_LENGTH);
        this.valueField.setValue(retained != null ? retained : Integer.toString(this.menu.groupId()));
        this.valueField.setFocused(focused);
        this.valueField.setResponder(value -> {
            if (!this.updatingValue) this.draftDirty = true;
        });

        this.enableButton = Button.builder(
                Component.translatable("gui.modular_machinery_reborn.groupinputconfig.enable"),
                pressed -> setGroupInput(true))
                .bounds(centreX + BUTTON_OFFSET_X, centreY + BUTTON_OFFSET_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.closeButton = Button.builder(
                Component.translatable("gui.modular_machinery_reborn.groupinputconfig.close"),
                pressed -> setGroupInput(false))
                .bounds(centreX + BUTTON_OFFSET_X, centreY + BUTTON_OFFSET_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.enableButton.visible = !this.groupInput;
        this.closeButton.visible = this.groupInput;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.valueField.render(graphics, mouseX, mouseY, partialTick);
        if (this.enableButton.visible) {
            this.enableButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (this.closeButton.visible) {
            this.closeButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);

        // 旧版这三行的坐标与顺序（GuiContainerGroupInputConfig:40-50）。
        graphics.drawString(this.font, Component.translatable(HatchGroupMenu.TITLE_KEY),
                TITLE_X, TITLE_Y, WHITE, true);
        graphics.drawString(this.font, Component.translatable("gui.modular_machinery_reborn.groupinputconfig.tooltip"),
                TOOLTIP_X, TOOLTIP_Y, WHITE, true);
        graphics.drawString(this.font, Component.translatable("gui.modular_machinery_reborn.groupinputconfig.value"),
                VALUE_LABEL_X, VALUE_LABEL_Y, WHITE, true);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.valueField.tick();
        // P10-08（0.121.40）：消费已到达的开关同步。镜像此前只在 init 读过一次菜单值，之后
        // 菜单数据经 ContainerData 持续到达而本类从不回读——服务器把 enabled 改 0 后屏幕仍
        // 显示启用，回车提交组号时把旧开关一并重放。现在：没有本地切换意图时，菜单值与本
        // 镜像不同就采纳菜单值（按钮可见性随之刷新；正在编辑的数字不受影响，程序化刷新也
        // 不发任何包）；有本地意图时等菜单值追上镜像再清除意图。
        if (this.localIntent) {
            if (this.menu.groupInput() == this.groupInput) {
                this.localIntent = false;
            }
        } else if (this.menu.groupInput() != this.groupInput) {
            this.groupInput = this.menu.groupInput();
            this.enableButton.visible = !this.groupInput;
            this.closeButton.visible = this.groupInput;
        }
        if (this.pendingValue != null && this.menu.groupId() == this.pendingValue) {
            this.pendingValue = null;
        }
        // Value equality acknowledges only the submitted value, never subsequent unsubmitted edits.
        // Protocol13 has no request generation: a rejected write and a delayed one remain indistinguishable.
        if (!this.valueField.isFocused() && !this.draftDirty && this.pendingValue == null) {
            updateValueField(Integer.toString(this.menu.groupId()));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean fieldHadFocus = this.valueField.isFocused();
            this.valueField.setFocused(false);
            // 旧版：隔离开启时点别处就提交输入框里的数（:73-89，`closeIsolation.visible` 就是那个
            // 条件）。这里把「点别处」收窄成「输入框原本有焦点」，避免随便点一下就发包；「隔离开着」
            // 则照旧版保留——关着的时候点别处只是失焦，不会把数写进仓口。
            if (this.valueField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (fieldHadFocus && this.groupInput) {
                commitValueField();
            }
            if (this.enableButton.visible && this.enableButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.closeButton.visible && this.closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            // 回车是本版新增的提交方式（旧版没有）；与「点别处」同一条规则：只在隔离开启时生效。
            if (this.groupInput) {
                commitValueField();
            }
            this.valueField.setFocused(false);
            return true;
        }
        if (this.valueField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 旧版只放数字和编辑键（:121-129）；组号不可能是负数，所以没有减号。
        if (Character.isDigit(codePoint) && this.valueField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ writes

    /**
     * 提交输入框：只设置组号，保留服务端当前开关。
     *
     * <p>旧版用 {@code Long.parseLong} 再夹到 {@code Integer.MAX_VALUE}（{@code :79-84}），
     * 超过 int 的数不会报错而是被夹住；这里保持同一口径，解析失败就什么都不发。
     */
    private void commitValueField() {
        int value;
        try {
            long parsed = Long.parseLong(this.valueField.getValue());
            if (parsed < 0) return;
            value = (int) Math.min(Integer.MAX_VALUE, parsed);
        } catch (NumberFormatException ignored) {
            return;
        }
        this.draftDirty = false;
        updateValueField(Integer.toString(value));
        if (this.pendingValue != null && this.pendingValue == value) return;
        if (this.pendingValue == null && this.menu.groupId() == value) return;
        this.pendingValue = value;
        ModNetwork.CHANNEL.sendToServer(HatchGroupUpdatePacket.setGroupId(value));
    }

    /** 切换开关：只切开关，不提交输入框里的数字——旧版就是这样（{@code :102-115}）。 */
    private void setGroupInput(boolean enabled) {
        this.groupInput = enabled;
        this.localIntent = true;
        this.enableButton.visible = !enabled;
        this.closeButton.visible = enabled;
        ModNetwork.CHANNEL.sendToServer(HatchGroupUpdatePacket.setEnabled(enabled));
    }

    private void updateValueField(String value) {
        if (this.valueField.getValue().equals(value)) return;
        this.updatingValue = true;
        try {
            this.valueField.setValue(value);
        } finally {
            this.updatingValue = false;
        }
    }
}
