package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.HatchKind;
import com.reborn.modularmachinery.menu.MachineHatchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Locale;

/**
 * The hatch screens, reproduced from the original {@code GuiContainerItemBus}, {@code GuiContainerFluidHatch}
 * and {@code GuiContainerEnergyHatch}.
 *
 * <p>Every hatch is a 176×166 window with the player's inventory at (8, 84) and the hotbar at (8, 142). Item
 * hatches swap in {@code inventory_<tier>.png}, whose slot holes are cut for that tier only — which is why the
 * slot coordinates in {@link MachineHatchMenu} cannot be adjusted on their own. Fluid and energy hatches share
 * {@code guibar.png}, which carries both the frame (u = 176) and the red fill (u = 196) for the bar at
 * (15, 10, 20×61).
 *
 * <p>The fluid screen draws the fluid's own atlas texture, tinted with its colour, and then lays the frame over
 * it — the original's order. Clicking the tank with something in hand asks the server to run that container
 * against the tank, so buckets can be filled and emptied from here.
 */
public final class MachineHatchScreen extends AbstractContainerScreen<MachineHatchMenu> {

    private static final ResourceLocation BAR_TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guibar.png");

    /** The bar's rectangle inside the window, which is also the tooltip's hover area in the original. */
    private static final int BAR_X = 15;
    private static final int BAR_Y = 10;
    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = 61;
    /** guibar.png: the empty frame is at u = 176, the filled bar at u = 196. */
    private static final int FRAME_U = 176;
    private static final int FILL_U = 196;

    private static final int LABEL_COLOR = 0x404040;

    public MachineHatchScreen(MachineHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private HatchKind.Family family() {
        return menu.hatch().kind().family();
    }

    /** Item hatches select their texture by tier; fluid and energy share the bar texture. */
    private ResourceLocation panelTexture() {
        if (family() != HatchKind.Family.ITEM) {
            return BAR_TEXTURE;
        }
        return new ResourceLocation(ModularMachineryReborn.MOD_ID,
                "textures/gui/inventory_" + menu.hatch().tier().name().toLowerCase(Locale.ROOT) + ".png");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(panelTexture(), leftPos, topPos, 0, 0, imageWidth, imageHeight);
        switch (family()) {
            case ENERGY -> drawEnergyBar(graphics);
            case FLUID -> drawFluid(graphics);
            case ITEM -> { }
        }
    }

    /** The original drew the fill from the bottom up: v runs from {@code 61 - pxFilled} to 61. */
    private void drawEnergyBar(GuiGraphics graphics) {
        int filled = filledPixels((float) menu.energyStored() / menu.energyCapacity());
        if (filled <= 0) {
            return;
        }
        graphics.blit(BAR_TEXTURE, leftPos + BAR_X, topPos + BAR_Y + BAR_HEIGHT - filled,
                FILL_U, BAR_HEIGHT - filled, BAR_WIDTH, filled);
    }

    private void drawFluid(GuiGraphics graphics) {
        FluidStack fluid = menu.hatch().fluidTank().getFluid();
        if (fluid.isEmpty() || menu.fluidAmount() <= 0) {
            return;
        }
        int filled = filledPixels((float) menu.fluidAmount() / menu.fluidCapacity());
        if (filled > 0) {
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(extensions.getStillTexture(fluid));
            int tint = extensions.getTintColor(fluid);
            graphics.setColor(((tint >> 16) & 0xFF) / 255F, ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
            graphics.blit(leftPos + BAR_X, topPos + BAR_Y + BAR_HEIGHT - filled, 0, BAR_WIDTH, filled, sprite);
            graphics.setColor(1F, 1F, 1F, 1F);
        }
        // The frame is drawn over the fluid, as in the original.
        graphics.blit(BAR_TEXTURE, leftPos + BAR_X, topPos + BAR_Y, FRAME_U, 0, BAR_WIDTH, BAR_HEIGHT);
    }

    private static int filledPixels(float percent) {
        return Mth.ceil(Mth.clamp(percent, 0F, 1F) * BAR_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The original hatch panels carry no title, so only the player inventory label is drawn.
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isOverBar(mouseX, mouseY)) {
            List<Component> lines = tooltipLines();
            if (!lines.isEmpty()) {
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private boolean isOverBar(int mouseX, int mouseY) {
        if (family() == HatchKind.Family.ITEM) {
            return false;
        }
        return mouseX >= leftPos + BAR_X && mouseX <= leftPos + BAR_X + BAR_WIDTH
                && mouseY >= topPos + BAR_Y && mouseY <= topPos + BAR_Y + BAR_HEIGHT;
    }

    private List<Component> tooltipLines() {
        if (family() == HatchKind.Family.ENERGY) {
            // The one energy line the original scaled, abbreviated <b>and</b> labelled from the config, all three in
            // the same place (`GuiContainerEnergyHatch.java:56-64`): it scaled both numbers with
            // `formatEnergyForDisplay`, printed them with `MiscUtils.formatNumber` and took the unit from
            // `EnergyDisplayUtil.type.getUnlocalizedFormat()`. This port had none of the three.
            com.reborn.modularmachinery.config.EnergyDisplay display =
                    com.reborn.modularmachinery.config.ModConfig.energyDisplay();
            return List.of(Component.translatable("tooltip.modular_machinery_reborn.energyhatch.charge",
                    com.reborn.modularmachinery.config.DisplayNumbers.abbreviated(
                            display.display(menu.energyStored())),
                    com.reborn.modularmachinery.config.DisplayNumbers.abbreviated(
                            display.display(menu.energyCapacity())),
                    display.labelComponent()));
        }
        FluidStack fluid = menu.hatch().fluidTank().getFluid();
        if (fluid.isEmpty() || menu.fluidAmount() <= 0) {
            return List.of(Component.translatable("tooltip.modular_machinery_reborn.fluidhatch.empty"),
                    Component.translatable("tooltip.modular_machinery_reborn.fluidhatch.tank", 0,
                            menu.fluidCapacity()));
        }
        return List.of(fluid.getDisplayName(),
                Component.translatable("tooltip.modular_machinery_reborn.fluidhatch.tank", menu.fluidAmount(),
                        menu.fluidCapacity()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && family() == HatchKind.Family.FLUID
                && isOverBar((int) mouseX, (int) mouseY)
                && minecraft != null && minecraft.gameMode != null && minecraft.player != null
                && !minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            // The original sent a packet here; a menu button is the vanilla route for the same thing.
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineHatchMenu.BUTTON_INTERACT_TANK);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
