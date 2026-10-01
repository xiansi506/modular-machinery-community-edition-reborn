import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the REAL JEI 15.49 routing classes with (a) the shipping PreviewInputHandler logic and (b) the old
 * always-true logic, and counts how many times one physical click fires a panel action.
 */
public final class JeiInputVerify {

    // ---- the real PreviewLayout values, transcribed from the source under test ---------------------------
    static final int PANEL_WIDTH = 184;
    static final int PANEL_HEIGHT = 220;
    static final int PREVIEW_X = 6;
    static final int PREVIEW_Y = 26;
    static final int PREVIEW_WIDTH = 172;
    static final int PREVIEW_HEIGHT = 150;
    static final int BUTTON_SIZE = 13;
    static final int BUTTON_STRIP_Y = 161;
    static final int BUTTON_SPACING = 2;
    static final int BUTTON_RIGHT_MARGIN = 6;
    static final int LAYER_STEPPER_Y = 44;
    static final int LAYER_ARROW_SIZE = 9;
    static final int LAYER_STEPPER_WIDTH = 9;
    static final int LAYER_STEPPER_X = PANEL_WIDTH - LAYER_STEPPER_WIDTH - BUTTON_RIGHT_MARGIN;
    static final int LAYER_TRACK_WIDTH = 9;
    static final int LAYER_TRACK_HEIGHT = 90;
    static final int LAYER_TRACK_Y = LAYER_STEPPER_Y + LAYER_ARROW_SIZE + 3;
    static final int LAYER_DOWN_Y = LAYER_TRACK_Y + LAYER_TRACK_HEIGHT + 2;

    static int failures = 0;
    static int checks = 0;

    static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL  " + what + "  -> " + detail);
        } else {
            System.out.println("  ok    " + what + "  -> " + detail);
        }
    }

    // ---- the panel under test: real PreviewPanel.mousePressed hit-testing, no GL -------------------------
    static class Panel {
        boolean layerMode = false;
        int pressed = 0;
        int released = 0;
        boolean lastPressClaimed;
        // covered by mousePressed: the 4 bottom buttons, the layer arrows and track (in layer mode), the viewport
        boolean draggingLayer;
        boolean draggingView;

        static int buttonStripWidth(int count) { return count * BUTTON_SIZE + (count - 1) * BUTTON_SPACING; }
        static int buttonX(int index, int count) {
            return PANEL_WIDTH - BUTTON_RIGHT_MARGIN - buttonStripWidth(count)
                    + index * (BUTTON_SIZE + BUTTON_SPACING);
        }
        static boolean over(double x, double y, int rx, int ry, int rw, int rh) {
            // MathUtil/ScreenRectangle semantics: min inclusive, max exclusive
            return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
        }
        boolean isOverPreview(double x, double y) {
            return over(x, y, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        }
        boolean isOverTrack(double x, double y) {
            return layerMode && over(x, y, LAYER_STEPPER_X, LAYER_TRACK_Y, LAYER_TRACK_WIDTH, LAYER_TRACK_HEIGHT);
        }
        boolean isOverUpArrow(double x, double y) {
            return layerMode && over(x, y, LAYER_STEPPER_X, LAYER_STEPPER_Y, LAYER_ARROW_SIZE, LAYER_ARROW_SIZE);
        }
        boolean isOverDownArrow(double x, double y) {
            return layerMode && over(x, y, LAYER_STEPPER_X, LAYER_DOWN_Y, LAYER_ARROW_SIZE, LAYER_ARROW_SIZE);
        }
        boolean isOverAnyButton(double x, double y) {
            for (int i = 0; i < 4; i++) {
                if (over(x, y, buttonX(i, 4), BUTTON_STRIP_Y, BUTTON_SIZE, BUTTON_SIZE)) return true;
            }
            return false;
        }

        /** Exactly PreviewPanel.mousePressed's decision, including the button==2 clause added by the fix. */
        boolean mousePressed(double x, double y, int button) {
            pressed++;
            draggingLayer = false;
            draggingView = false;
            if (button == 0) {
                if (isOverAnyButton(x, y)) { lastPressClaimed = true; return true; }
                if (layerMode && (isOverUpArrow(x, y) || isOverDownArrow(x, y))) { lastPressClaimed = true; return true; }
                if (isOverTrack(x, y)) { draggingLayer = true; lastPressClaimed = true; return true; }
                draggingView = isOverPreview(x, y);
                lastPressClaimed = draggingView;
                return draggingView;
            }
            if (button == 1 || button == 2) {
                draggingView = isOverPreview(x, y);
                lastPressClaimed = draggingView;
                return draggingView;
            }
            lastPressClaimed = false;
            return false;
        }

        boolean mouseReleased(double x, double y, int button) {
            if (button == 0) { released++; return true; }
            if (button == 2 && isOverPreview(x, y)) { released++; return true; }
            return false;
        }
    }

    // ---- the shipping handler, verbatim logic ------------------------------------------------------------
    static final class FixedHandler implements IJeiInputHandler {
        final Panel panel;
        FixedHandler(Panel panel) { this.panel = panel; }
        @Override public ScreenRectangle getArea() {
            return new ScreenRectangle(PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        }
        @Override public boolean handleInput(double x, double y, IJeiUserInput input) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE) return false;
            int button = input.getKey().getValue();
            if (input.isSimulate()) return this.panel.mousePressed(x, y, button);
            return this.panel.mouseReleased(x, y, button);
        }
    }

    // ---- the old handler, verbatim logic -----------------------------------------------------------------
    static final class OldHandler implements IJeiInputHandler {
        final Panel panel;
        OldHandler(Panel panel) { this.panel = panel; }
        @Override public ScreenRectangle getArea() {
            return new ScreenRectangle(PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        }
        @Override public boolean handleInput(double x, double y, IJeiUserInput input) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE) return false;
            int button = input.getKey().getValue();
            if (input.isSimulate()) { this.panel.mousePressed(x, y, button); return true; }
            this.panel.mouseReleased(x, y, button);
            return true;
        }
    }

    /** Reproduces RecipeLayoutUserInputHandler: own the event, delegate to the API handler. */
    static final class RouteHandler implements IUserInputHandler {
        final IJeiInputHandler delegate;
        final Rect2i absoluteArea;
        RouteHandler(IJeiInputHandler delegate, Rect2i absoluteArea) {
            this.delegate = delegate;
            this.absoluteArea = absoluteArea;
        }
        @Override public java.util.Optional<IUserInputHandler> handleUserInput(
                net.minecraft.client.gui.screens.Screen screen, UserInput input,
                mezz.jei.common.input.IInternalKeyMappings keys) {
            double ax = input.getMouseX();
            double ay = input.getMouseY();
            if (ax < absoluteArea.getX() || ax >= absoluteArea.getX() + absoluteArea.getWidth()
                    || ay < absoluteArea.getY() || ay >= absoluteArea.getY() + absoluteArea.getHeight()) {
                return java.util.Optional.empty();
            }
            // RecipeLayoutInputHandler then converts absolute -> recipe-local before calling the API handler.
            double localX = ax - absoluteArea.getX();
            double localY = ay - absoluteArea.getY();
            if (this.delegate.handleInput(localX, localY, input)) {
                return java.util.Optional.of(this);
            }
            return java.util.Optional.empty();
        }
    }

    static UserInput press(double screenX, double screenY, int button) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        return new UserInput(key, screenX, screenY, 0, InputType.SIMULATE);
    }

    static UserInput release(double screenX, double screenY, int button) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        return new UserInput(key, screenX, screenY, 0, InputType.EXECUTE);
    }

    /** One physical click = one MouseButtonPressed event + one MouseButtonReleased event. */
    static class Click {
        final mezz.jei.gui.input.handlers.UserInputRouter router;
        final Panel panel;
        Click(IJeiInputHandler handler, int originX, int originY) {
            this.panel = (handler instanceof FixedHandler f) ? f.panel : ((OldHandler) handler).panel;
            Rect2i area = new Rect2i(originX, originY, PANEL_WIDTH, PANEL_HEIGHT);
            this.router = new mezz.jei.gui.input.handlers.UserInputRouter("verify",
                    new RouteHandler(handler, area));
        }
        void fire(int originX, int originY, int localX, int localY, int button) {
            double sx = originX + localX;
            double sy = originY + localY;
            router.handleUserInput(null, press(sx, sy, button), null);
            router.handleUserInput(null, release(sx, sy, button), null);
        }
    }

    public static void main(String[] args) {
        System.out.println("== JEI 15.49 input dispatch verification ==");
        System.out.println();

        // ------ 0. the routing chain itself: one press event, one release event -> one handler call each -----
        System.out.println("[0] does one physical click reach the handler once per event?");
        Panel p0 = new Panel();
        Click c0 = new Click(new FixedHandler(p0), 100, 50);
        c0.fire(100, 50, 20, 30, 0); // a point inside the viewport
        check("fixed: presses for 1 click", p0.pressed == 1, "pressed=" + p0.pressed);
        check("fixed: releases for 1 click", p0.released == 1, "released=" + p0.released);

        Panel p1 = new Panel();
        Click c1 = new Click(new OldHandler(p1), 100, 50);
        c1.fire(100, 50, 20, 30, 0);
        System.out.println("      (old logic, same single click)");
        check("old: presses for 1 click", p1.pressed >= 1, "pressed=" + p1.pressed);
        check("old: releases for 1 click", p1.released >= 1, "released=" + p1.released);
        check("old logic fires the release handler the SAME number of times as the fixed one",
                p1.released == p0.released,
                "old released=" + p1.released + " vs fixed released=" + p0.released);

        // ------ 1. simulate truthfulness over every panel region ---------------------------------------------
        System.out.println();
        System.out.println("[1] simulate pass truthfulness (return value = 'would I handle this?')");
        int[][] buttonCentres = new int[4][2];
        for (int i = 0; i < 4; i++) {
            buttonCentres[i][0] = Panel.buttonX(i, 4) + BUTTON_SIZE / 2;
            buttonCentres[i][1] = BUTTON_STRIP_Y + BUTTON_SIZE / 2;
        }
        String[] names = {"machine-info", "cycle", "reset", "3D/layer toggle"};

        for (int lm = 0; lm <= 1; lm++) {
            boolean layerMode = lm == 1;
            System.out.println("  -- layerMode=" + layerMode + " --");
            Panel p = new Panel();
            p.layerMode = layerMode;
            FixedHandler h = new FixedHandler(p);
            for (int i = 0; i < 4; i++) {
                int bx = buttonCentres[i][0];
                int by = buttonCentres[i][1];
                boolean r = h.handleInput(bx, by, press(0, 0, 0));
                check("button " + names[i] + " at (" + bx + "," + by + ") claimed", r, "returned " + r);
            }
            boolean rUp = h.handleInput(LAYER_STEPPER_X + 4, LAYER_STEPPER_Y + 4, press(0, 0, 0));
            check("layer up arrow claimed only in layer mode", rUp == layerMode, "returned " + rUp);
            boolean rDown = h.handleInput(LAYER_STEPPER_X + 4, LAYER_DOWN_Y + 4, press(0, 0, 0));
            check("layer down arrow claimed only in layer mode", rDown == layerMode, "returned " + rDown);
            boolean rTrack = h.handleInput(LAYER_STEPPER_X + 4, LAYER_TRACK_Y + 40, press(0, 0, 0));
            check("layer track claimed only in layer mode", rTrack == layerMode, "returned " + rTrack);
            boolean rView = h.handleInput(60, 60, press(0, 0, 0));
            check("viewport claimed (drag/pan)", rView, "returned " + rView);
            boolean rRight = h.handleInput(60, 60, press(0, 0, 1));
            check("viewport claimed for right button", rRight, "returned " + rRight);
            boolean rMid = h.handleInput(60, 60, press(0, 0, 2));
            check("viewport claimed for middle button (reset gesture)", rMid, "returned " + rMid);

            // points the panel has nothing to act on
            boolean outside = h.handleInput(200, 200, press(0, 0, 0));
            check("point outside the panel declined", !outside, "returned " + outside);
            boolean titleRow = h.handleInput(90, 12, press(0, 0, 0));
            check("title row declined", !titleRow, "returned " + titleRow);
            boolean grid = h.handleInput(20, 195, press(0, 0, 0));
            check("ingredient grid declined (JEI owns the slots)", !grid, "returned " + grid);
            if (!layerMode) {
                boolean arrowsWhen3d = h.handleInput(LAYER_STEPPER_X + 4, LAYER_STEPPER_Y + 4, press(0, 0, 0));
                check("layer arrows inert in 3D mode", !arrowsWhen3d, "returned " + arrowsWhen3d);
            }
        }

        // ------ 2. every button geometry really is inside the registered area ---------------------------------
        System.out.println();
        System.out.println("[2] button strip (y=" + BUTTON_STRIP_Y + ") inside getArea() band "
                + PREVIEW_Y + ".." + (PREVIEW_Y + PREVIEW_HEIGHT));
        for (int i = 0; i < 4; i++) {
            int bx = buttonCentres[i][0];
            int by = buttonCentres[i][1];
            check(names[i] + " centre inside area", Panel.over(bx, by, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT),
                    "(" + bx + "," + by + ")");
        }
        check("layer down arrow inside area",
                Panel.over(LAYER_STEPPER_X + 4, LAYER_DOWN_Y + 4, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT),
                "(" + (LAYER_STEPPER_X + 4) + "," + (LAYER_DOWN_Y + 4) + ")");
        check("layer track inside area",
                Panel.over(LAYER_STEPPER_X + 4, LAYER_TRACK_Y + 40, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT),
                "(" + (LAYER_STEPPER_X + 4) + "," + (LAYER_TRACK_Y + 40) + ")");

        // ------ 3. key type for a mouse click ----------------------------------------------------------------
        System.out.println();
        System.out.println("[3] InputConstants.Type for the click JEI delivers");
        for (int b = 0; b <= 2; b++) {
            InputConstants.Key k = InputConstants.Type.MOUSE.getOrCreate(b);
            check("button " + b + " key type is MOUSE", k.getType() == InputConstants.Type.MOUSE,
                    "type=" + k.getType() + " value=" + k.getValue());
        }

        // ------ 4. the toggle double-fire the user reported --------------------------------------------------
        System.out.println();
        System.out.println("[4] toggle outcome for one physical click (a real toggle flips once per release)");
        boolean[] toggled = {false};
        final int[] releaseCount = {0};
        Panel pt = new Panel() {
            @Override boolean mouseReleased(double x, double y, int button) {
                releaseCount[0]++;
                toggled[0] = !toggled[0];
                return true;
            }
        };
        Click ct = new Click(new FixedHandler(pt), 0, 0);
        int bx0 = buttonCentres[1][0];
        int by0 = buttonCentres[1][1];
        ct.fire(0, 0, bx0, by0, 0);
        check("fixed handler: toggle ends ON after one click", toggled[0], "toggled=" + toggled[0]
                + " (releases=" + releaseCount[0] + ")");

        boolean[] toggled2 = {false};
        final int[] releaseCount2 = {0};
        Panel pt2 = new Panel() {
            @Override boolean mouseReleased(double x, double y, int button) {
                releaseCount2[0]++;
                toggled2[0] = !toggled2[0];
                return true;
            }
        };
        Click ct2 = new Click(new OldHandler(pt2), 0, 0);
        ct2.fire(0, 0, bx0, by0, 0);
        System.out.println("      (old logic, same single click)");
        System.out.println("      old releases=" + releaseCount2[0] + " -> toggle ends "
                + (toggled2[0] ? "ON" : "OFF (user sees nothing happen)"));
        check("old logic leaves the toggle OFF, i.e. visibly dead", !toggled2[0], "toggled=" + toggled2[0]);

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: ALL CHECKS PASSED" : "RESULT: FAILURES PRESENT");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
