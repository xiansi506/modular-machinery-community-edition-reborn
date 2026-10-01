package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.machine.MachineDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The blueprint preview: right-clicking a bound blueprint shows that machine's structure in 3D, with the blocks
 * it needs along the bottom.
 *
 * <p>This class owns nothing but the panel's position and the mouse plumbing. Everything inside the 184×220
 * frame — the structure, the title, the two-row ingredient grid, the button strip, the layer selector and the
 * extra-info overlay — lives in {@link PreviewPanel}, which the JEI category draws too, so the two entry points
 * cannot drift apart. The original did the same thing through {@code PreviewPanels.getPanel}.
 *
 * <p>The panel texture is the original's {@code guiblueprint_new.png}, blitted whole.
 */
public final class BlueprintPreviewScreen extends Screen {

    public static final ResourceLocation TEXTURE = PreviewAtlas.TEXTURE;

    private final PreviewPanel panel;

    private int left;
    private int top;

    public BlueprintPreviewScreen(MachineDefinition machine) {
        super(machine.displayName());
        this.panel = new PreviewPanel(machine);
    }

    @Override
    protected void init() {
        this.left = (this.width - PreviewLayout.PANEL_WIDTH) / 2;
        this.top = (this.height - PreviewLayout.PANEL_HEIGHT) / 2;
    }

    /** The original's screen did not pause the game either. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This version of the GUI API only offers the single-argument background call.
        renderBackground(graphics);
        PreviewAtlas.PANEL.draw(graphics, this.left, this.top);

        double localX = mouseX - this.left;
        double localY = mouseY - this.top;
        this.panel.render(graphics, this.left, this.top, localX, localY);

        // Inside a Screen the pose is untranslated, which is why the panel can take panel-space coordinates
        // and add the origin itself. Tooltips come last so nothing paints over them.
        ItemStack component = this.panel.componentAt(localX, localY);
        if (component != null) {
            graphics.renderTooltip(this.font, component, mouseX, mouseY);
            return;
        }
        List<Component> lines = this.panel.tooltipLines(localX, localY);
        if (!lines.isEmpty()) {
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.panel.mousePressed(mouseX - this.left, mouseY - this.top, button).claimed()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.panel.mouseReleased(mouseX - this.left, mouseY - this.top, button).claimed()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.panel.mouseDragged(mouseX - this.left, mouseY - this.top, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.panel.mouseScrolled(mouseX - this.left, mouseY - this.top, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
