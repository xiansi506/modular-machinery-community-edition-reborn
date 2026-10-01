import com.reborn.modularmachinery.client.preview.PreviewAtlas;
import com.reborn.modularmachinery.client.preview.PreviewButton;
import com.reborn.modularmachinery.client.preview.PreviewLayout;
import com.reborn.modularmachinery.client.preview.PreviewPanel;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the REAL compiled PreviewPanel (constructor-less, with its REAL PreviewButton state machine injected)
 * through the REAL JEI 15.49 input router.
 */
public final class ButtonStateVerify {

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

    /** Allocate without running the constructor (which needs Minecraft's block registry). */
    @SuppressWarnings("unchecked")
    static PreviewPanel allocatePanel(int buttonCount, List<PreviewButton> buttons,
                                      PreviewButton layerUp, PreviewButton layerDown,
                                      boolean[] infoFlag, boolean[] cycleFlag, boolean[] layerFlag) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field uf = unsafeClass.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        Object unsafe = uf.get(null);
        java.lang.reflect.Method alloc = unsafeClass.getMethod("allocateInstance", Class.class);
        PreviewPanel panel = (PreviewPanel) alloc.invoke(unsafe, PreviewPanel.class);

        set(panel, "buttons", buttons);
        set(panel, "layerUp", layerUp);
        set(panel, "layerDown", layerDown);
        set(panel, "machineInfoVisible", false);
        set(panel, "cycleBlocks", false);
        set(panel, "layerMode", false);
        set(panel, "draggingLayer", false);
        set(panel, "draggingView", false);
        set(panel, "layerIndex", 0);
        set(panel, "yaw", 0.0F);
        set(panel, "pitch", 0.0F);
        set(panel, "zoom", 1.0F);
        set(panel, "panX", 0.0F);
        set(panel, "panY", 0.0F);
        return panel;
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static Object get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Real PreviewButton.release() flips `armed`->action; count via the real action consumer. */
    static final int[] FIRE = {0};

    static PreviewPanel newPanel() throws Exception {
        FIRE[0] = 0;
        List<PreviewButton> buttons = new ArrayList<>();
        // The four bottom-strip buttons, built with the REAL factory and the REAL atlas sprites, with the
        // same actions PreviewPanel's constructor wires up.
        buttons.add(PreviewButton.toggle(PreviewLayout.buttonX(0, 4), PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.MACHINE_INFO, PreviewAtlas.MACHINE_INFO_HOVERED,
                PreviewAtlas.MACHINE_INFO_HOVERED, null,
                () -> List.of(Component.literal("info")), enabled -> { FIRE[0]++; setQuiet("info", enabled); }));
        buttons.add(PreviewButton.toggle(PreviewLayout.buttonX(1, 4), PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.CYCLE_BLOCKS, PreviewAtlas.CYCLE_BLOCKS_HOVERED,
                PreviewAtlas.CYCLE_BLOCKS_PRESSED, PreviewAtlas.CYCLE_BLOCKS_ACTIVE,
                () -> List.of(Component.literal("cycle")), enabled -> { FIRE[0]++; setQuiet("cycle", enabled); }));
        buttons.add(PreviewButton.button(PreviewLayout.buttonX(2, 4), PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.RESET_CENTER, PreviewAtlas.RESET_CENTER_HOVERED,
                PreviewAtlas.RESET_CENTER_PRESSED, () -> List.of(Component.literal("reset")),
                ignored -> FIRE[0]++));
        buttons.add(PreviewButton.toggle(PreviewLayout.buttonX(3, 4), PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.LAYER_TOGGLE, PreviewAtlas.LAYER_TOGGLE_HOVERED,
                PreviewAtlas.LAYER_TOGGLE_PRESSED, PreviewAtlas.LAYER_TOGGLE_ACTIVE,
                () -> List.of(Component.literal("layer")), enabled -> { FIRE[0]++; setQuiet("layer", enabled); }));

        PreviewButton up = PreviewButton.button(PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_STEPPER_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_UP, PreviewAtlas.LAYER_UP_HOVERED,
                PreviewAtlas.LAYER_UP_PRESSED, () -> List.of(Component.literal("up")),
                ignored -> FIRE[0]++, () -> true);
        PreviewButton down = PreviewButton.button(PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_DOWN_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_DOWN, PreviewAtlas.LAYER_DOWN_HOVERED,
                PreviewAtlas.LAYER_DOWN_PRESSED, () -> List.of(Component.literal("down")),
                ignored -> FIRE[0]++, () -> true);

        PreviewPanel panel = allocatePanel(4, buttons, up, down, null, null, null);
        // the real setLayerMode() calls layerToggleButton.setToggled(...), so wire the real field
        set(panel, "layerToggleButton", buttons.get(3));
        set(panel, "cycleButton", buttons.get(1));
        set(panel, "machineInfoButton", buttons.get(0));
        set(panel, "machineInfoLines", List.of(Component.literal("info")));
        LISTENERS.put(panel, new boolean[]{false, false});
        return panel;
    }

    static final java.util.Map<PreviewPanel, boolean[]> LISTENERS = new java.util.IdentityHashMap<>();
    static void setQuiet(String which, boolean value) {
        // record on the panel currently being exercised
        boolean[] rec = LISTENERS.get(CURRENT[0]);
        if (rec == null) return;
        if (which.equals("info")) rec[0] = value;
        if (which.equals("cycle")) rec[1] = value;
    }
    static final PreviewPanel[] CURRENT = {null};

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

    static final class LayoutHandler implements IJeiInputHandler {
        final IJeiInputHandler delegate; final Rect2i rect;
        LayoutHandler(IJeiInputHandler d, Rect2i r) { delegate = d; rect = r; }
        @Override public ScreenRectangle getArea() { return new ScreenRectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()); }
        @Override public boolean handleInput(double ax, double ay, IJeiUserInput in) {
            if (!MathUtil.contains(new Rect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()), ax, ay)) return false;
            return delegate.handleInput(ax - rect.getX(), ay - rect.getY(), in);
        }
    }

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

    public static void main(String[] args) throws Exception {
        System.out.println("== REAL compiled PreviewPanel + REAL PreviewButton through REAL JEI dispatch ==");
        int ox = 137, oy = 71;
        String[] names = {"machine-info", "cycle", "reset", "3D/layer toggle"};
        double[] bx = new double[4];
        for (int i = 0; i < 4; i++) bx[i] = PreviewLayout.buttonX(i, 4) + PreviewLayout.BUTTON_SIZE / 2.0;
        double by = PreviewLayout.BUTTON_STRIP_Y + PreviewLayout.BUTTON_SIZE / 2.0;

        System.out.println();
        System.out.println("[A] one physical click per bottom button -> how many times does the action fire?");
        for (int i = 0; i < 4; i++) {
            PreviewPanel pOld = newPanel();
            CURRENT[0] = pOld;
            click(new OldHandler(pOld), ox, oy, bx[i], by, 0);
            int oldFires = FIRE[0];

            PreviewPanel pNew = newPanel();
            CURRENT[0] = pNew;
            click(new FixedHandler(pNew), ox, oy, bx[i], by, 0);
            int newFires = FIRE[0];

            System.out.println("      " + names[i] + ": OLD 0.16.0 fires=" + oldFires + ", FIXED fires=" + newFires);
            check(names[i] + " fires exactly once with the FIXED handler", newFires == 1, "fires=" + newFires);
        }

        System.out.println();
        System.out.println("[B] does the OLD 0.16.0 handler double-fire? (the H1 hypothesis)");
        int oldTotal = 0, newTotal = 0;
        for (int i = 0; i < 4; i++) {
            PreviewPanel a = newPanel(); CURRENT[0] = a;
            click(new OldHandler(a), ox, oy, bx[i], by, 0);
            oldTotal += FIRE[0];
            PreviewPanel b = newPanel(); CURRENT[0] = b;
            click(new FixedHandler(b), ox, oy, bx[i], by, 0);
            newTotal += FIRE[0];
        }
        check("OLD handler total fires for 4 clicks == 4 (no double-fire)", oldTotal == 4, "old total=" + oldTotal);
        check("FIXED handler total fires for 4 clicks == 4", newTotal == 4, "fixed total=" + newTotal);

        System.out.println();
        System.out.println("[C] toggle END STATE after one click (what the user actually sees)");
        for (int i : new int[]{0, 1, 3}) {
            boolean[] rec = new boolean[2];
            PreviewPanel p2 = newPanel();
            CURRENT[0] = p2;
            LISTENERS.put(p2, rec);
            click(new FixedHandler(p2), ox, oy, bx[i], by, 0);
            System.out.println("      " + names[i] + " state after 1 click: info=" + rec[0] + " cycle=" + rec[1]
                    + " layerMode=" + p2.isLayerMode());
        }
        boolean[] recCycle = new boolean[2];
        PreviewPanel pc = newPanel(); CURRENT[0] = pc; LISTENERS.put(pc, recCycle);
        click(new FixedHandler(pc), ox, oy, bx[1], by, 0);
        check("cycle toggle is ON after one click (visible change)", recCycle[1], "cycle=" + recCycle[1]);
        boolean[] recInfo = new boolean[2];
        PreviewPanel pin = newPanel(); CURRENT[0] = pin; LISTENERS.put(pin, recInfo);
        click(new FixedHandler(pin), ox, oy, bx[0], by, 0);
        check("machine-info overlay is ON after one click", recInfo[0], "info=" + recInfo[0]);

        System.out.println();
        System.out.println("[D] layer toggle then layer arrows");
        boolean[] recLayer = new boolean[2];
        PreviewPanel pl = newPanel(); CURRENT[0] = pl; LISTENERS.put(pl, recLayer);
        click(new FixedHandler(pl), ox, oy, bx[3], by, 0);
        System.out.println("      after layer-toggle click: isLayerMode=" + pl.isLayerMode());
        check("layer toggle switched the panel to layer mode", pl.isLayerMode(), "layerMode=" + pl.isLayerMode());
        // the arrows' visibility supplier is wired to the panel in the real constructor; inject ours
        set(pl, "layerMode", true);
        FIRE[0] = 0;
        click(new FixedHandler(pl), ox, oy, PreviewLayout.LAYER_STEPPER_X + 4, PreviewLayout.LAYER_STEPPER_Y + 4, 0);
        check("layer up arrow fires once in layer mode", FIRE[0] == 1, "fires=" + FIRE[0]);
        FIRE[0] = 0;
        click(new FixedHandler(pl), ox, oy, PreviewLayout.LAYER_STEPPER_X + 4, PreviewLayout.LAYER_DOWN_Y + 4, 0);
        check("layer down arrow fires once in layer mode", FIRE[0] == 1, "fires=" + FIRE[0]);
        FIRE[0] = 0;
        click(new FixedHandler(pl), ox, oy, PreviewLayout.LAYER_STEPPER_X + 4, PreviewLayout.LAYER_TRACK_Y + 40, 0);
        check("layer scrollbar track: drag claimed, no button action", FIRE[0] == 0, "fires=" + FIRE[0]);

        System.out.println();
        System.out.println("[E] truthful simulate on the REAL panel class");
        PreviewPanel ps = newPanel(); CURRENT[0] = ps;
        check("viewport press claimed", ps.mousePressed(60, 60, 0), "true");
        check("title row declined", !ps.mousePressed(90, 12, 0), "false");
        check("ingredient grid declined", !ps.mousePressed(20, 195, 0), "false");
        check("bottom strip claimed", ps.mousePressed(bx[1], by, 0), "true");
        check("outside panel declined", !ps.mousePressed(400, 400, 0), "false");
        check("middle click over viewport claimed (reset reachable)",
                newPanel().mousePressed(60, 60, 2), "true");

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: ALL CHECKS PASSED" : "RESULT: FAILURES PRESENT");
        if (failures != 0) System.exit(1);
    }
}
