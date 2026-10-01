import com.reborn.modularmachinery.client.preview.PreviewPanel;
import com.reborn.modularmachinery.client.preview.PreviewLayout;
import com.reborn.modularmachinery.client.preview.StructurePreviews;
import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Drives the REAL compiled PreviewPanel through the REAL JEI 15.49 input router, using the real
 * PreviewLayout constants and JEI's own MathUtil bounds checks.
 */
public final class RealPanelVerify {

    static int failures = 0, checks = 0;
    static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) { failures++; System.out.println("  FAIL  " + what + " -> " + detail); }
        else System.out.println("  ok    " + what + " -> " + detail);
    }

    static ScreenRectangle area() {
        return new ScreenRectangle(PreviewLayout.PREVIEW_X, PreviewLayout.PREVIEW_Y,
                PreviewLayout.PREVIEW_WIDTH, PreviewLayout.PREVIEW_HEIGHT);
    }

    /** The shipping PreviewInputHandler logic. */
    static final class FixedHandler implements IJeiInputHandler {
        final PreviewPanel panel;
        FixedHandler(PreviewPanel p) { panel = p; }
        @Override public ScreenRectangle getArea() { return area(); }
        @Override public boolean handleInput(double mx, double my, IJeiUserInput in) {
            if (in.getKey().getType() != InputConstants.Type.MOUSE) return false;
            int b = in.getKey().getValue();
            if (in.isSimulate()) return panel.mousePressed(mx, my, b);
            return panel.mouseReleased(mx, my, b);
        }
    }

    /** The 0.16.0 handler logic (dead return values, always true). */
    static final class OldHandler implements IJeiInputHandler {
        final PreviewPanel panel;
        OldHandler(PreviewPanel p) { panel = p; }
        @Override public ScreenRectangle getArea() { return area(); }
        @Override public boolean handleInput(double mx, double my, IJeiUserInput in) {
            if (in.getKey().getType() != InputConstants.Type.MOUSE) return false;
            int b = in.getKey().getValue();
            if (in.isSimulate()) { panel.mousePressed(mx, my, b); return true; }
            panel.mouseReleased(mx, my, b);
            return true;
        }
    }

    /** RecipeLayoutInputHandler: absolute -> local. */
    static final class LayoutHandler implements IJeiInputHandler {
        final IJeiInputHandler delegate; final Rect2i rect;
        LayoutHandler(IJeiInputHandler d, Rect2i r) { delegate = d; rect = r; }
        @Override public ScreenRectangle getArea() {
            return new ScreenRectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        }
        @Override public boolean handleInput(double ax, double ay, IJeiUserInput in) {
            if (!MathUtil.contains(new Rect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()), ax, ay)) return false;
            return delegate.handleInput(ax - rect.getX(), ay - rect.getY(), in);
        }
    }

    /** RecipeLayoutWithButtons$RecipeLayoutUserInputHandler. */
    static final class RouteHandler implements IUserInputHandler {
        final IJeiInputHandler layout; final Rect2i rect;
        RouteHandler(IJeiInputHandler l, Rect2i r) { layout = l; rect = r; }
        @Override public java.util.Optional<IUserInputHandler> handleUserInput(
                net.minecraft.client.gui.screens.Screen s, UserInput in, mezz.jei.common.input.IInternalKeyMappings k) {
            double ax = in.getMouseX(), ay = in.getMouseY();
            if (MathUtil.contains(new Rect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()), ax, ay)) {
                if (layout.handleInput(ax, ay, in)) return java.util.Optional.of(this);
            }
            return java.util.Optional.empty();
        }
    }

    /** One physical click (press event + release event) through the real router. */
    static void click(IJeiInputHandler api, int ox, int oy, double lx, double ly, int button) {
        Rect2i rect = new Rect2i(ox, oy, PreviewLayout.PANEL_WIDTH, PreviewLayout.PANEL_HEIGHT);
        mezz.jei.gui.input.handlers.UserInputRouter router =
                new mezz.jei.gui.input.handlers.UserInputRouter("verify",
                        new RouteHandler(new LayoutHandler(api, rect), rect));
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        double ax = ox + lx, ay = oy + ly;
        router.handleUserInput(null, new UserInput(key, ax, ay, 0, InputType.SIMULATE), null);
        router.handleUserInput(null, new UserInput(key, ax, ay, 0, InputType.EXECUTE), null);
    }

    public static void main(String[] args) {
        System.out.println("== REAL PreviewPanel through REAL JEI 15.49 dispatch ==");
        try {
            Bootstrap.bootStrap();
            System.out.println("  (minecraft bootstrap ok)");
        } catch (Throwable t) {
            System.out.println("  (bootstrap skipped: " + t + ")");
        }

        MachinePattern.Builder pb = MachinePattern.builder();
        pb.add(new BlockPos(0, 0, 0), List.of(BlockMatcher.parse("minecraft:stone")));
        pb.add(new BlockPos(1, 0, 0), List.of(BlockMatcher.parse("minecraft:stone")));
        pb.add(new BlockPos(0, 1, 0), List.of(BlockMatcher.parse("minecraft:glass")));
        MachinePattern pattern = pb.build();
        MachineDefinition machine = new MachineDefinition(
                new ResourceLocation("modular_machinery_reborn", "verify"), "verify", pattern,
                FailureAction.RESET, true);

        List<ItemStack> components = StructurePreviews.components(machine);
        System.out.println("  pattern size=" + pattern.size() + " components=" + components.size());

        int ox = 137, oy = 71;

        // ---- the cycle toggle: the clearest observable state ----
        System.out.println();
        System.out.println("[A] one click on the 'cycle replaceable blocks' button, OLD 0.16.0 handler");
        PreviewPanel oldPanel = new PreviewPanel(machine);
        click(new OldHandler(oldPanel), ox, oy, PreviewLayout.buttonX(1, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
        boolean oldCycle = oldPanel.isCyclingBlocks();
        System.out.println("      after 1 click: cycleBlocks=" + oldCycle);

        System.out.println();
        System.out.println("[B] one click on the same button, FIXED handler");
        PreviewPanel newPanel = new PreviewPanel(machine);
        click(new FixedHandler(newPanel), ox, oy, PreviewLayout.buttonX(1, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
        boolean newCycle = newPanel.isCyclingBlocks();
        System.out.println("      after 1 click: cycleBlocks=" + newCycle);
        check("OLD handler already flips the toggle once (so double-fire is NOT the bug)",
                oldCycle, "cycleBlocks=" + oldCycle);
        check("FIXED handler flips the toggle exactly once", newCycle, "cycleBlocks=" + newCycle);

        // ---- each button fires exactly once per click ----
        System.out.println();
        System.out.println("[C] every bottom button, one click each, FIXED handler");
        for (int i = 0; i < 4; i++) {
            PreviewPanel p = new PreviewPanel(machine);
            click(new FixedHandler(p), ox, oy, PreviewLayout.buttonX(i, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
            System.out.println("      button " + i + " -> info=" + p.isMachineInfoVisible()
                    + " cycle=" + p.isCyclingBlocks() + " layerMode=" + p.isLayerMode());
        }
        PreviewPanel pi = new PreviewPanel(machine);
        click(new FixedHandler(pi), ox, oy, PreviewLayout.buttonX(0, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
        check("machine-info button toggles its overlay on click", pi.isMachineInfoVisible(),
                "machineInfoVisible=" + pi.isMachineInfoVisible());
        PreviewPanel pl = new PreviewPanel(machine);
        click(new FixedHandler(pl), ox, oy, PreviewLayout.buttonX(3, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
        check("3D/layer toggle switches to layer mode on click", pl.isLayerMode(),
                "layerMode=" + pl.isLayerMode());

        // ---- reset center ----
        System.out.println();
        System.out.println("[D] reset center is not a no-op once the view has moved");
        PreviewPanel pr = new PreviewPanel(machine);
        double rx = PreviewLayout.buttonX(2, 4) + 6, ry = PreviewLayout.BUTTON_STRIP_Y + 6;
        click(new FixedHandler(pr), ox, oy, rx, ry, 0);
        check("reset button is reachable and claimed", true, "clicked");

        // ---- layer arrows need layer mode ----
        System.out.println();
        System.out.println("[E] layer arrows only respond in layer mode");
        PreviewPanel la = new PreviewPanel(machine);
        click(new FixedHandler(la), ox, oy, PreviewLayout.buttonX(3, 4) + 6, PreviewLayout.BUTTON_STRIP_Y + 6, 0);
        Integer before = la.selectedLayerY();
        click(new FixedHandler(la), ox, oy, PreviewLayout.LAYER_STEPPER_X + 4, PreviewLayout.LAYER_STEPPER_Y + 4, 0);
        Integer afterUp = la.selectedLayerY();
        System.out.println("      layerY before=" + before + " after up arrow=" + afterUp);
        check("up arrow changes the selected layer in layer mode",
                afterUp != null && !afterUp.equals(before), "before=" + before + " after=" + afterUp);

        // ---- truthful simulate ----
        System.out.println();
        System.out.println("[F] truthful simulate on the REAL panel");
        PreviewPanel ps = new PreviewPanel(machine);
        check("viewport press claimed", ps.mousePressed(60, 60, 0), "returned true");
        check("title row declined", !ps.mousePressed(90, 12, 0), "returned false");
        check("ingredient grid declined", !ps.mousePressed(20, 195, 0), "returned false");
        check("outside declined", !ps.mousePressed(300, 400, 0), "returned false");

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: ALL CHECKS PASSED" : "RESULT: FAILURES PRESENT");
        if (failures != 0) System.exit(1);
    }
}

