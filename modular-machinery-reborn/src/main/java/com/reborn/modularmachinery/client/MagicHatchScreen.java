package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.magic.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

public final class MagicHatchScreen extends AbstractContainerScreen<MagicHatchMenu> {
    public MagicHatchScreen(MagicHatchMenu menu, Inventory inventory, Component title) { super(menu,inventory,title); }
    @Override protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x,y,x+176,y+166,0xFFC6C6C6);
        graphics.fill(x,y,x+176,y+2,0xFFFFFFFF); graphics.fill(x,y,x+2,y+166,0xFFFFFFFF);
        graphics.fill(x+174,y,x+176,y+166,0xFF555555); graphics.fill(x,y+164,x+176,y+166,0xFF555555);
        for (var slot : menu.slots) {
            graphics.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xFF555555);
            graphics.fill(x+slot.x,y+slot.y,x+slot.x+17,y+slot.y+17,0xFFFFFFFF);
            graphics.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF8B8B8B);
        }
        MagicFamily family = menu.hatch.family();
        int count = family == MagicFamily.WILL ? 5 : 1;
        for (int i = 0; i < count; i++) {
            int rowY = y+20+i*10;
            double amount = menu.hatch.value(i), maximum = menu.hatch.value(5);
            int width = (int)(Math.max(0,Math.min(1,maximum > 0 ? amount/maximum : 0))*156);
            graphics.fill(x+10,rowY,x+166,rowY+8,0xFF444444);
            graphics.fill(x+10,rowY,x+10+width,rowY+8,family.color);
            String text = family == MagicFamily.WILL ? Component.translatable("magic.modular_machinery_reborn.will."+new String[]{"default","corrosive","destructive","vengeful","steadfast"}[i]).getString()+" "+format(amount)
                    : format(amount)+" / "+format(maximum);
            graphics.drawString(font,text,x+12,rowY,0xFFFFFFFF,true);
        }
        if (family == MagicFamily.LIFEESSENCE) graphics.drawString(font,Component.translatable(menu.hatch.boundOrb()
                ? "gui.modular_machinery_reborn.magic.orb_bound" : "gui.modular_machinery_reborn.magic.orb_unbound"),x+8,y+36,0xFF404040,false);
        if (family == MagicFamily.AIR) {
            graphics.drawString(font,Component.translatable("gui.modular_machinery_reborn.air.pressure", format(menu.hatch.value(1))),x+8,y+36,0xFF404040,false);
            graphics.drawString(font,Component.translatable("gui.modular_machinery_reborn.air.volume", format(menu.hatch.value(2))),x+8,y+48,0xFF404040,false);
            graphics.drawString(font,Component.translatable("gui.modular_machinery_reborn.air.limits", format(menu.hatch.value(4)), format(menu.hatch.value(3))),x+8,y+60,0xFF404040,false);
        }
    }
    /**
     * Integral values use the shared {@link GuiAmounts#compact} shorthand; fractional values keep their
     * three-decimal form. {@code compact} clamps sub-1000 values to 0 because every other caller shows a
     * <b>non-negative stock</b>, so a negative integral reading would be labelled "0" — an information error,
     * since negative will/air/pressure are legal states (P09). Negative integrals therefore go through
     * {@link #compactSigned}, which keeps the minus sign and abbreviates the magnitude the same way.
     * Boundaries: NaN/Infinity fail the {@code n == Math.floor(n)} test and fall into the decimal branch,
     * which renders {@code Infinity}/{@code NaN} without throwing; magnitudes beyond 2^53 were already
     * inexact the moment they lived in a double, and {@code Long.MIN_VALUE} abbreviates as {@code -9.2E}
     * (its true magnitude exceeds {@code Long.MAX_VALUE} by one ulp — indistinguishable at shorthand width,
     * exact value stays available via the hover tooltip).
     */
    private static String format(double n) {
        if (n == Math.floor(n) && Double.isFinite(n)) {
            long v = (long) n;
            return v >= 0 ? GuiAmounts.compact(v) : compactSigned(v);
        }
        return String.format(Locale.ROOT, "%,.3f", n);
    }

    /** Negative shorthand: keeps the sign, abbreviates the magnitude with the shared non-negative rules. */
    private static String compactSigned(long v) {
        long magnitude = v == Long.MIN_VALUE ? Long.MAX_VALUE : -v;
        String body = magnitude < 1000 ? Long.toString(magnitude) : GuiAmounts.compact(magnitude);
        return "-" + body;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        renderBackground(graphics); super.render(graphics,mouseX,mouseY,tick); renderTooltip(graphics,mouseX,mouseY);
        int count = menu.hatch.family() == MagicFamily.WILL ? 5 : 1;
        if (mouseX >= leftPos+10 && mouseX < leftPos+166) for(int i=0;i<count;i++) if(mouseY>=topPos+20+i*10&&mouseY<topPos+28+i*10)
            graphics.renderTooltip(font,Component.literal(Double.toString(menu.hatch.value(i))),mouseX,mouseY);
    }
}
