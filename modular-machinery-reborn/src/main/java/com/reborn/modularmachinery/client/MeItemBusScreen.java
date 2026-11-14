package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.block.MeBusAccess;
import com.reborn.modularmachinery.block.MeFluidTanks;
import com.reborn.modularmachinery.compat.ae2.MeItemBusBlockEntity;
import com.reborn.modularmachinery.menu.MeItemBusMenu;
import com.reborn.modularmachinery.menu.MeConfigAmount;
import com.reborn.modularmachinery.network.MeFluidConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * C4（0.73.0）：ME 物品总线的窗口。
 *
 * <p>输入窗口沿用旧版 {@code textures/gui/meiteminputbus.png}（176×204）；输出窗口照旧版直接
 * 引用 AE2 的 {@code ae2:textures/guis/skychest.png}（176×195）。不 import 任何 {@code appeng}
 * 类型，只在资源路径上依赖 AE2——这两个方块本来就只有装了 AE2 才会注册。
 *
 * <h2>C4-II（0.94.0，照高版 AE2 接口）：配置格 = ghost 标记</h2>
 *
 * <p>输入侧的 16 个配置格是**物品/流体混排的 ghost 标记**（高版接口 {@code FakeSlot} +
 * {@code GenericStackInv} 的对应物）：手持物品左键标记（数量 = 手持数）、右键标记 1 个；手持流体
 * 容器标记流体（目标量 = 容器量）；空手（或容器无流体）点击清除。物品不再被吃进配置格，
 * 破坏也不掉落配置内容。写入经 {@link MeFluidConfigPacket} 上行、服务端验证后写回，
 * 客户端随 update tag 镜像刷新。
 *
 * <p>罐条（输入竖条/输出横条）自 0.94.0 起纯显示（配置都在格子里）。渲染 v1 简化：
 * 流体使用方块图集贴图和原生 tint，按比例从罐底画到液面；悬停显示精确数量。
 */
public final class MeItemBusScreen extends AbstractContainerScreen<MeItemBusMenu> {

    private static final ResourceLocation INPUT =
            new ResourceLocation("modular_machinery_reborn", "textures/gui/me_item_input_bus.png");
    private static final ResourceLocation OUTPUT =
            new ResourceLocation("ae2", "textures/guis/skychest.png");

    /** 输入侧 4×4 配置格的左上角（占位槽同位）。 */
    private static final int GRID_X = 8;
    private static final int GRID_Y = 35;
    /** 输入侧罐条（纯显示）：配置格与内部格之间的空带。 */
    private static final int IN_STRIP_X = 81;
    private static final int GAUGE_W = 12;
    private static final int GAUGE_H = 8;
    /** 输出侧罐条（纯显示）：内部格下方。 */
    private static final int OUT_STRIP_Y = 98;

    @Nullable
    private MeBusAccess bus;
    private List<Component> hoveredTooltip;

    public MeItemBusScreen(MeItemBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = menu.isInput() ? 204 : 195;
        this.inventoryLabelY = menu.isInput() ? 111 : 101;
    }

    @Nullable
    private MeBusAccess bus() {
        if (this.bus == null) {
            if (this.menu.hatch() instanceof MeBusAccess access) {
                this.bus = access;
            }
        }
        return this.bus;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredTooltip != null) graphics.renderComponentTooltip(font, hoveredTooltip, mouseX, mouseY);
        else renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(this.menu.isInput() ? INPUT : OUTPUT,
                this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        hoveredTooltip = null;
        renderFluidStrip(graphics, mouseX, mouseY);
        renderGasStrip(graphics, mouseX, mouseY);
        renderConfigMarks(graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------ 配置格（0.94.0 ghost 标记）

    private int configSlotAt(double mouseX, double mouseY) {
        if (!this.menu.isInput()) {
            return -1;
        }
        int relX = (int) mouseX - this.leftPos - GRID_X;
        int relY = (int) mouseY - this.topPos - GRID_Y;
        if (relX < 0 || relY < 0 || relX >= 4 * 18 || relY >= 4 * 18) {
            return -1;
        }
        int col = relX / 18;
        int row = relY / 18;
        if (relX - col * 18 >= 16 || relY - row * 18 >= 16) {
            return -1;
        }
        return col + row * 4;
    }

    private void renderConfigMarks(GuiGraphics graphics, int mouseX, int mouseY) {
        MeBusAccess bus = bus();
        if (bus == null || !this.menu.isInput()) {
            return;
        }
        int hovered = configSlotAt(mouseX, mouseY);
        for (int slot = 0; slot < MeItemBusBlockEntity.CONFIG_GRID; slot++) {
            int x = this.leftPos + GRID_X + (slot % 4) * 18;
            int y = this.topPos + GRID_Y + (slot / 4) * 18;
            ItemStack item = bus.configItemAt(slot);
            Fluid fluid = bus.fluidMarkAt(slot);
            var gas = bus.gasTanks() == null ? null : bus.gasTanks().mark(slot);
            if (item != null && !item.isEmpty()) {
                graphics.renderItem(item, x, y);
                graphics.renderItemDecorations(this.font, item, x, y);
            } else if (gas != null) {
                graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000 | gas.color());
                String label = GuiAmounts.compact(gas.amount());
                drawAmountLabel(graphics, label, x, y);
            } else if (fluid != null) {
                // 流体标记：12×12 的流体块 + 目标量小字。
                drawFluidBlock(graphics, fluid, x + 2, y + 2, 12, 12);
                String label = GuiAmounts.compact(bus.fluidMarkAmount(slot));
                drawAmountLabel(graphics, label, x, y);
            }
            if (slot == hovered) {
                hoveredTooltip = configTooltip(bus, slot);
            }
        }
    }

    private List<Component> configTooltip(MeBusAccess bus, int slot) {
        List<Component> lines = new ArrayList<>();
        ItemStack item = bus.configItemAt(slot);
        FluidStack fluid = bus.fluidMarkStackAt(slot);
        var gas = bus.gasTanks() == null ? null : bus.gasTanks().mark(slot);
        if (item != null && !item.isEmpty()) {
            lines.add(item.getHoverName());
            lines.add(Component.literal("× " + item.getCount()));
        } else if (gas != null) {
            lines.add(gas.name());
            lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.gas.target", gas.amount()));
            if (!bus.gasNetworkAvailable()) lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.gas.requires_appmek"));
        } else if (!fluid.isEmpty()) {
            lines.add(fluid.getDisplayName());
            lines.add(Component.literal(fluid.getAmount() + " mB"));
        } else {
            lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.fluid.unfiltered"));
        }
        lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.fluid.hint"));
        if (MeConfigAmount.hasMark(bus, slot)) {
            lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.scroll.step"));
            lines.add(Component.translatable("screen.modular_machinery_reborn.me_bus.scroll.scale"));
        }
        return lines;
    }

    private void drawAmountLabel(GuiGraphics graphics, String label, int x, int y) {
        float scale = Math.min(1F, 16F / Math.max(1, font.width(label)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 16, y + 8, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, label, -font.width(label), 0, 0xffffff, true);
        graphics.pose().popPose();
    }

    private static void drawFluidBlock(GuiGraphics graphics, Fluid fluid, int x, int y, int w, int h) {
        GuiFluidRenderer.draw(graphics, new FluidStack(fluid, 1), x, y, w, h);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component status = Component.translatable(menu.meOnline() ? "screen.modular_machinery_reborn.me_bus.online" : "screen.modular_machinery_reborn.me_bus.offline");
        int statusX = imageWidth - 8 - font.width(status);
        String caption = title.getString();
        if (font.width(caption) > statusX - 16) caption = font.plainSubstrByWidth(caption, Math.max(0, statusX - 16 - font.width("…"))) + "…";
        graphics.drawString(font, caption, 8, 6, 0x404040, false);
        graphics.drawString(font, status, statusX, 6, menu.meOnline() ? 0x308030 : 0x903030, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // C4-II（0.94.2，照高版 AE2 接口的实际语义——所有者截图对照）：
        // **左键** = 按手上的东西标记（水桶 → 标记水桶物品；普通物品 → 标记该物品，数量 = 手持数）；
        // **右键** = 有流体容器时标记**容器里的流体**（水桶 → 水，目标量 = 容器量），空手 = 清除该格。
        // 0.94.1 把流体放在左键优先，与高版相反；清除入口当时只挂在「空手左键」上，手持东西时无法取消。
        int slot = configSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            ItemStack carried = this.menu.getCarried();
            Optional<FluidStack> contained = FluidUtil.getFluidContained(carried);
            var access = bus();
            var gas = access == null || access.gasTanks() == null ? null : access.gasTanks().containedGas(carried);
            if (gas == null && access != null) gas = access.encodedGas(carried);
            if (button == 0) {
                if (carried.isEmpty()) {
                    MeFluidConfigPacket.sendClear(slot);
                } else {
                    MeFluidConfigPacket.sendItem(slot, carried, false);
                }
            } else if (button == 1) {
                if (gas != null) {
                    MeFluidConfigPacket.sendGas(slot, gas.id(), gas.amount());
                } else if (contained.isPresent()) {
                    MeFluidConfigPacket.sendFluid(slot, contained.get().getFluid(),
                            contained.get().getAmount());
                } else {
                    MeFluidConfigPacket.sendClear(slot);
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int slot = configSlotAt(mouseX, mouseY);
        if (slot >= 0 && Double.isFinite(delta) && delta != 0 && MeConfigAmount.hasMark(bus(), slot)
                && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    MeConfigAmount.action(slot, delta > 0, hasShiftDown(), hasControlDown()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------ 罐条（纯显示）

    private int gaugeX(int slot) {
        return this.leftPos + (this.menu.isInput() ? IN_STRIP_X : 8 + slot * 18) + 1;
    }

    private int gaugeY(int slot) {
        return this.topPos + (this.menu.isInput() ? GRID_Y + slot * GAUGE_H : OUT_STRIP_Y) + 1;
    }

    private int gaugeWidth() {
        return this.menu.isInput() ? (bus() != null && bus().gasTanks() != null ? 5 : GAUGE_W - 2) : 16;
    }

    private int gaugeHeight() {
        return this.menu.isInput() ? GAUGE_H - 2 : (bus() != null && bus().gasTanks() != null ? 5 : 12);
    }

    private int hoveredGauge(double mouseX, double mouseY) {
        MeBusAccess bus = bus();
        if (bus == null || bus.fluidTanks() == null) {
            return -1;
        }
        for (int slot = 0; slot < MeFluidTanks.SLOTS; slot++) {
            if (bus.fluidTanks().getFluidInTank(slot).isEmpty()) {
                continue;
            }
            int x = gaugeX(slot);
            int y = gaugeY(slot);
            if (mouseX >= x && mouseX < x + gaugeWidth() && mouseY >= y && mouseY < y + gaugeHeight()) {
                return slot;
            }
        }
        return -1;
    }

    private void renderFluidStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        MeBusAccess bus = bus();
        MeFluidTanks tanks = bus == null ? null : bus.fluidTanks();
        if (tanks == null) {
            return;
        }
        // 0.94.1（所有者反馈）：空罐不画——罐条只在有内容时可见。
        int hovered = hoveredGauge(mouseX, mouseY);
        for (int slot = 0; slot < MeFluidTanks.SLOTS; slot++) {
            FluidStack contents = tanks.getFluidInTank(slot);
            if (contents.isEmpty()) {
                continue;
            }
            int x = gaugeX(slot);
            int y = gaugeY(slot);
            int w = gaugeWidth();
            int h = gaugeHeight();
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, hovered == slot ? 0xFFE0E0E0 : 0xFF606060);
            graphics.fill(x, y, x + w, y + h, 0xFF101010);
            int fill = GuiAmounts.filledPixels(contents.getAmount(), tanks.getTankCapacity(slot), h);
            drawFluidBlock(graphics, contents.getFluid(), x, y + (h - fill), w, fill);
        }
        if (hovered >= 0) {
            FluidStack contents = tanks.getFluidInTank(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(contents.isEmpty()
                    ? Component.translatable("screen.modular_machinery_reborn.me_bus.fluid.empty")
                    : contents.getDisplayName());
            lines.add(Component.literal(contents.getAmount() + " / " + tanks.getTankCapacity(hovered) + " mB"));
            hoveredTooltip = lines;
        }
    }

    /** Separate strip: the gas and fluid pools never hide or overwrite each other's contents. */
    private void renderGasStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        MeBusAccess bus = bus();
        var tanks = bus == null ? null : bus.gasTanks();
        if (tanks == null) return;
        for (int tank = 0; tank < com.reborn.modularmachinery.block.MeGasStorage.SLOTS; tank++) {
            var gas = tanks.contents(tank);
            if (gas == null) continue;
            int x = this.leftPos + (this.menu.isInput() ? IN_STRIP_X + 9 : 9 + tank * 18);
            int y = this.topPos + (this.menu.isInput() ? GRID_Y + tank * GAUGE_H + 1 : OUT_STRIP_Y + 9);
            int w = this.menu.isInput() ? 6 : 16;
            int h = this.menu.isInput() ? GAUGE_H - 2 : 4;
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF606060);
            graphics.fill(x, y, x + w, y + h, 0xFF101010);
            int fill = GuiAmounts.filledPixels(gas.amount(), com.reborn.modularmachinery.block.MeGasStorage.CAPACITY, h);
            graphics.fill(x, y + h - fill, x + w, y + h, 0xFF000000 | gas.color());
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                hoveredTooltip = List.of(gas.name(), Component.literal(GuiAmounts.exact(gas.amount()) + " / " + GuiAmounts.exact(com.reborn.modularmachinery.block.MeGasStorage.CAPACITY) + " mB"));
            }
        }
    }
}
