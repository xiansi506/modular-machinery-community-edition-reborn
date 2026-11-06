package com.reborn.modularmachinery.client.preview;

import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.reborn.modularmachinery.machine.StructurePreview;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Everything inside the 184×220 blueprint panel: the 3D/slice preview, the ingredient grid, the title, the
 * buttons and the overlays.
 *
 * <p>The original had one {@code MachineStructurePreviewPanel} that both entry points instantiated through
 * {@code PreviewPanels.getPanel(machine, widgetGui)}, which is why the in-game screen and the JEI page looked
 * identical. This class is that panel: {@link BlueprintPreviewScreen} and
 * {@code client.jei.StructurePreviewCategory} each own one and draw it at their own origin, so there is a single
 * implementation of the layout, the buttons and the camera gestures rather than two that drift apart.
 *
 * <p><b>Coordinates.</b> Every mouse argument this class accepts and every tooltip rectangle it returns is in
 * <i>panel space</i>: {@code (0,0)} is the panel's top-left, {@code (184,220)} its bottom-right. The host adds
 * its origin only when drawing, which is what lets one implementation serve a {@code Screen} (absolute
 * coordinates) and a JEI category (whose pose JEI has already translated to the recipe origin).
 *
 * <p>That is only true if the JEI host also declares its input area as the whole panel, because JEI dispatches a
 * registered {@code IJeiInputHandler} with coordinates relative to <i>that handler's own</i> {@code getArea()}
 * origin, not relative to the recipe — see {@code StructurePreviewCategory.PreviewInputHandler}. Declaring the
 * viewport there instead handed this class its mouse positions shifted by {@code (PREVIEW_X, PREVIEW_Y) = (6,
 * 26)}, which moved the whole button strip out from under every click.
 *
 * <p><b>What is not the original's.</b> Two intentional differences are documented at their site: the ingredient
 * grid shows an overflow marker instead of scrolling, and the "extra info" button is a toggle that pins its list
 * over the viewport, where the original showed the same list as a hover tooltip only. The title keeps the
 * original's 36 + 2 + 136 split when a {@code prefix} exists, and deliberately does not fall back to drawing the
 * bare translation key when it does not.
 */
public final class PreviewPanel {

    private static final float DRAG_SENSITIVITY = 0.7F;
    private static final float SCROLL_SENSITIVITY = 0.15F;
    /** The original's cycle period for replaceable blocks: {@code gui.preview.button.enable_cycle...} says 1.5s. */
    private static final long CYCLE_PERIOD_MILLIS = 1500L;

    private final MachineDefinition machine;
    /**
     * 宿主是不是 JEI 类别（E2，0.101.0）。
     *
     * <p>Screen 宿主的材料格是本面板手绘的，点击要接给 JEI 查询；JEI 类别宿主的同一批格子是
     * <strong>真 JEI 槽位</strong>（R/U 由 JEI 原生处理），面板再接一次就会一格两触发。
     * 所以材料格的点击与快捷键只在 Screen 宿主生效。
     */
    private final boolean jeiHost;
    private StructurePreview preview;
    private List<ItemStack> components;
    /** 最近一次 render() 收到的面板空间鼠标位置，供 keyPressed 判断悬停格（旧版 mouseOver 的等价物）。 */
    private double lastMouseX;
    private double lastMouseY;
    private final List<StructurePreviews.ModifierIngredient> modifierIngredients;
    private int selectedModifier = -1;
    private int modifierScroll;

    private final List<PreviewButton> buttons = new ArrayList<>();
    private final PreviewButton layerUp;
    private final PreviewButton layerDown;
    private final PreviewButton machineInfoButton;
    private final PreviewButton layerToggleButton;
    private final PreviewButton cycleButton;
    /** 右上角的成型预览开关；只有机器声明了 {@code hide-components-when-formed} 才可见。 */
    private final PreviewButton formedButton;
    private final PreviewButton toolbarButton;
    private boolean toolbarExpanded = true;
    /**
     * 「额外信息」面板的行。
     *
     * <p>不是 {@code final}，因为其中的<strong>长/高/宽</strong>一行取决于动态结构当前多长：
     * 加长一节会真的让投影变长，那一行必须跟着变，否则它会报一个与画面对不上的尺寸。
     * 旧版每帧从渲染器重新取值，所以没有这个问题；本项目的这组行是构建出来的，因此要在结构变化时重算。
     */
    private List<Component> machineInfoLines;

    /**
     * 预览里动态结构重复几节。
     *
     * <p>动态结构的长度在世界里是<strong>量出来的</strong>，而预览里没有世界，所以这个长度只能由玩家在界面上选——
     * 旧版的做法完全一样：它把这个值放在 {@code WorldSceneRendererWidget} 上，由按钮条上的两个按钮增减
     * （{@code MachineStructurePreviewPanel.java:388-394}），并在增减时夹进模式的
     * {@code [minSize, maxSize]}（{@code WorldSceneRendererWidget.java:191/196}）。
     *
     * <p>机器没有动态结构时这个字段恒为 0，两个按钮也不会出现在按钮条上。
     */
    private int dynamicPatternSize;

    private float yaw = StructurePreviewRenderer.DEFAULT_YAW;
    private float pitch = StructurePreviewRenderer.DEFAULT_PITCH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;

    private boolean machineInfoVisible;
    private boolean cycleBlocks;
    /**
     * 是否按“已成型”的样子预览：组件全部收起，只画控制器。
     *
     * <p>这个开关<strong>不改变结构、不改变材料清单</strong>，只改变 {@link #render} 交给渲染器的方块列表，
     * 所以它永远只是观察角度，不会让玩家以为机器真的已经搭好。
     */
    private boolean formedPreview;
    private boolean layerMode;
    private boolean draggingLayer;
    /** True when the press that started the current drag landed on the viewport rather than on a button. */
    private boolean draggingView;
    private boolean viewportClickCandidate;
    private boolean viewportMoved;
    private double viewportPressX;
    private double viewportPressY;
    /** Relative structure position selected by a click in the 3D preview. */
    private BlockPos selectedBlockPos;

    /** Index into {@link StructurePreview#layers()}, selected index 0 being the lowest level. */
    private int layerIndex;

    public PreviewPanel(MachineDefinition machine) {
        this(machine, false);
    }

    /** Screen 宿主走这个构造器；JEI 类别宿主传 {@code true}（理由见 {@link #jeiHost}）。 */
    public PreviewPanel(MachineDefinition machine, boolean jeiHost) {
        this(machine, jeiHost, StructurePreviews.of(machine), StructurePreviews.components(machine));
    }

    /** Prepared data lets input handling run independently of world registries and a render context. */
    PreviewPanel(MachineDefinition machine, boolean jeiHost, StructurePreview prepared, List<ItemStack> ingredients) {
        this.machine = machine;
        this.jeiHost = jeiHost;
        this.dynamicPatternSize = StructurePreviews.initialDynamicPatternSize(machine);
        this.preview = prepared;
        this.components = List.copyOf(ingredients);
        this.modifierIngredients = StructurePreviews.modifierIngredients(machine);
        this.machineInfoLines = buildMachineInfo(machine, this.dynamicPatternSize);

        // The original built its bottom strip as a right-aligned Row and **only** added the two dynamic-size
        // buttons when the machine actually has dynamic patterns (MachineStructurePreviewPanel.java:193-203),
        // which is why the whole strip shifts when they are present. The count is computed up front so the
        // indices below stay in the original's order: the two dynamic buttons come first, then the features
        // this port has (machine info, cycle, reset, layer toggle) plus the original preview-options menu.
        boolean hasDynamic = machine.hasDynamicPatterns();
        // 两个索引起点：有动态结构时后移两位，因为旧版把动态按钮放在条的最前面。
        int machine_infoX;
        int cycleX;
        int count = hasDynamic ? 7 : 5;
        int index = 0;
        if (hasDynamic) {
            PreviewButton plus = PreviewButton.button("dynamic-plus", PreviewLayout.buttonX(index++, count),
                    PreviewLayout.BUTTON_STRIP_Y, PreviewLayout.BUTTON_SIZE,
                    PreviewAtlas.DYNAMIC_PLUS, PreviewAtlas.DYNAMIC_PLUS_HOVERED,
                    PreviewAtlas.DYNAMIC_PLUS_PRESSED, () -> dynamicTooltip(true),
                    ignored -> stepDynamicPatternSize(1), () -> this.toolbarExpanded);
            PreviewButton minus = PreviewButton.button("dynamic-minus", PreviewLayout.buttonX(index++, count),
                    PreviewLayout.BUTTON_STRIP_Y, PreviewLayout.BUTTON_SIZE,
                    PreviewAtlas.DYNAMIC_MINUS, PreviewAtlas.DYNAMIC_MINUS_HOVERED,
                    PreviewAtlas.DYNAMIC_MINUS_PRESSED, () -> dynamicTooltip(false),
                    ignored -> stepDynamicPatternSize(-1), () -> this.toolbarExpanded);
            this.buttons.add(plus);
            this.buttons.add(minus);
        }
        // The remaining buttons keep their relative order; `count` includes the two slots the original
        // reserved for placeWorldPreview and its options menu, so the strip stays right-aligned at the same
        // margin the original used.
        machine_infoX = index;
        cycleX = index + 1;
        // Keep the existing controls in order and reserve the rightmost slot for the folding menu.
        this.machineInfoButton = PreviewButton.toggle("machine-info", PreviewLayout.buttonX(machine_infoX, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.MACHINE_INFO, PreviewAtlas.MACHINE_INFO_HOVERED,
                // The atlas has no mouse-down sprite for this row either: the original used machineExtraInfo as a
                // hover-tooltip button, never as a toggle. Reusing the hovered sprite for the press flash and
                // tinting the normal one while the toggle is on keeps the state visible without inventing a UV.
                PreviewAtlas.MACHINE_INFO_HOVERED,
                // No "clicked" sprite exists for this row, so PreviewButton tints the normal sprite while on.
                null,
                this::machineInfoTooltip, this::onMachineInfoToggled, () -> this.toolbarExpanded);
        this.cycleButton = PreviewButton.toggle("cycle-blocks", PreviewLayout.buttonX(cycleX, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.CYCLE_BLOCKS, PreviewAtlas.CYCLE_BLOCKS_HOVERED,
                PreviewAtlas.CYCLE_BLOCKS_PRESSED, PreviewAtlas.CYCLE_BLOCKS_ACTIVE,
                this::cycleTooltip, this::onCycleToggled, () -> this.toolbarExpanded);
        PreviewButton resetButton = PreviewButton.button("reset-center", PreviewLayout.buttonX(cycleX + 1, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.RESET_CENTER, PreviewAtlas.RESET_CENTER_HOVERED,
                PreviewAtlas.RESET_CENTER_PRESSED, () -> List.of(Component.translatable(
                        "gui.preview.button.reset_center.tip")), ignored -> resetView(), () -> this.toolbarExpanded);
        this.layerToggleButton = PreviewButton.toggle("layer-toggle", PreviewLayout.buttonX(cycleX + 2, count),
                PreviewLayout.BUTTON_STRIP_Y,
                PreviewLayout.BUTTON_SIZE, PreviewAtlas.LAYER_TOGGLE, PreviewAtlas.LAYER_TOGGLE_HOVERED,
                PreviewAtlas.LAYER_TOGGLE_PRESSED, PreviewAtlas.LAYER_TOGGLE_ACTIVE,
                this::layerToggleTooltip, this::setLayerMode, () -> this.toolbarExpanded);
        this.buttons.add(this.machineInfoButton);
        this.buttons.add(this.cycleButton);
        this.buttons.add(resetButton);
        this.buttons.add(this.layerToggleButton);
        this.toolbarButton = PreviewButton.toggle("toolbar-menu", PreviewLayout.buttonX(count - 1, count),
                PreviewLayout.BUTTON_STRIP_Y, PreviewLayout.BUTTON_SIZE,
                PreviewAtlas.MENU, PreviewAtlas.MENU_HOVERED, PreviewAtlas.MENU_ACTIVE, PreviewAtlas.MENU_ACTIVE,
                () -> List.of(Component.translatable("gui.modular_machinery_reborn.preview.toolbar." + (this.toolbarExpanded ? "hide" : "show"))),
                enabled -> this.toolbarExpanded = enabled);
        this.toolbarButton.setToggled(true);
        this.buttons.add(this.toolbarButton);
        // 旧版把它放在 rightTopMenu（右上角），而不是底部条；补进底部条会改变既有按钮的右对齐位置。
        this.formedButton = PreviewButton.toggle("formed-preview",
                PreviewLayout.FORMED_BUTTON_X, PreviewLayout.FORMED_BUTTON_Y, PreviewLayout.BUTTON_SIZE,
                PreviewAtlas.FORMED, PreviewAtlas.FORMED_HOVERED, PreviewAtlas.FORMED_PRESSED,
                PreviewAtlas.FORMED_ACTIVE,
                () -> List.of(Component.translatable(formedTooltipKey(this.formedPreview))),
                this::onFormedToggled, () -> showsFormedToggle(this.machine));
        this.buttons.add(this.formedButton);

        // The original's layer selector lived in its rightMenu at (184 - w - 6, 44), and only while slicing.
        this.layerUp = PreviewButton.button("up", PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_STEPPER_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_UP, PreviewAtlas.LAYER_UP_HOVERED,
                PreviewAtlas.LAYER_UP_PRESSED, () -> layerArrowTooltip("up"),
                ignored -> stepLayer(1), this::isLayerMode);
        this.layerDown = PreviewButton.button("down", PreviewLayout.LAYER_STEPPER_X, PreviewLayout.LAYER_DOWN_Y,
                PreviewLayout.LAYER_ARROW_SIZE, PreviewAtlas.LAYER_DOWN, PreviewAtlas.LAYER_DOWN_HOVERED,
                PreviewAtlas.LAYER_DOWN_PRESSED, () -> layerArrowTooltip("down"),
                ignored -> stepLayer(-1), this::isLayerMode);
    }

    private void onMachineInfoToggled(boolean enabled) {
        this.machineInfoVisible = enabled;
    }

    /**
     * 增减预览里的动态结构长度，并重建预览与材料清单。
     *
     * <p>夹取用 {@link StructurePreviews#clampDynamicPatternSize}（对应旧版的
     * {@code Math.min(Math.max(pattern.getMinSize(), size), pattern.getMaxSize())}）。
     *
     * <p><strong>夹取之后的「没变」要提前返回。</strong>否则按到边界时仍会重建预览与材料清单——
     * 那是一次没有可见结果的重算，而它在每一帧的点击里都会发生。旧版没有这个问题，因为它的渲染器每帧
     * 从字段重新取值；本项目的预览是<strong>构建出来</strong>的对象，所以必须自己判断有没有变化。
     */
    private void stepDynamicPatternSize(int delta) {
        int wanted = StructurePreviews.clampDynamicPatternSize(this.machine, this.dynamicPatternSize + delta);
        if (wanted == this.dynamicPatternSize) {
            return;
        }
        this.dynamicPatternSize = wanted;
        var controller = this.preview.blocks().stream().filter(block -> block.pos().equals(BlockPos.ZERO))
                .map(StructurePreview.Block::state).findFirst().orElse(null);
        this.preview = StructurePreviews.of(this.machine, wanted, controller);
        this.components = StructurePreviews.components(this.machine, wanted, controller);
        // 长/高/宽那一行与画面对齐：结构变长了，尺寸行也必须跟着变。
        this.machineInfoLines = buildMachineInfo(this.machine, wanted);
        // 层索引指向的是旧结构的某一层，而结构的层数刚刚变了（动态段会加高/降低）。
        // 不重算会让切片预览停在一个不存在的层上，看起来像「预览空了」。
        this.layerIndex = Math.min(this.layerIndex, Math.max(0, this.preview.layers().size() - 1));
    }

    /**
     * 两个动态长度按钮的提示，措辞取自旧版自己的两条语言键。
     *
     * <p>留意旧版这两条键的措辞是<strong>反的</strong>：{@code dynamic_pattern_plus.tip} 写的是
     * 「点击以<strong>减小</strong>动态结构的尺寸」。本版按<strong>按钮的实际作用</strong>取句子
     * （加号说加、减号说减），因为照抄一句说反的话会让玩家按错方向——这是本项目对「面向作者的文案」
     * 的一贯取舍：意思对齐旧版，错误不去复制。
     */
    private List<Component> dynamicTooltip(boolean plus) {
        return List.of(Component.translatable(
                plus ? "gui.preview.button.dynamic_pattern_plus.tip"
                        : "gui.preview.button.dynamic_pattern_subtract.tip",
                this.dynamicPatternSize));
    }

    /** 预览当前用的动态结构长度；机器没有动态结构时是 0。供复现器与界面核对。 */
    public int dynamicPatternSize() {
        return this.dynamicPatternSize;
    }

    private void onCycleToggled(boolean enabled) {
        this.cycleBlocks = enabled;
    }

    private void onFormedToggled(boolean enabled) {
        this.formedPreview = enabled;
    }

    // -------------------------------------------------------------------------------------------------------
    // State used by the hosts
    // -------------------------------------------------------------------------------------------------------

    public MachineDefinition machine() {
        return this.machine;
    }

    public StructurePreview preview() {
        return this.preview;
    }

    public List<ItemStack> components() { return this.components.stream().map(ItemStack::copy).toList(); }

    public boolean isToolbarExpanded() { return this.toolbarExpanded; }

    public boolean isLayerMode() {
        return this.layerMode;
    }

    /** Whether the pinned machine-info overlay is currently shown; the machine-info button toggles it. */
    public boolean isMachineInfoVisible() {
        return this.machineInfoVisible;
    }

    /** Whether the replaceable-block art is cycling; the cycle button toggles it. */
    public boolean isCyclingBlocks() {
        return this.cycleBlocks;
    }

    /** 是否正在按“已成型”的样子预览（组件收起）。 */
    public boolean isFormedPreview() {
        return this.formedPreview;
    }

    /**
     * 这个机器是否显示成型预览开关。
     *
     * <p>判据是机器自己声明了 {@code hide-components-when-formed}：没有这个字段的机器，成型与否在预览里
     * 没有区别，给它一个永远不改变画面的按钮只会让人以为按钮坏了。显式写 {@code false} 的机器同样不显示，
     * 因为它的行为与没写一样。
     */
    public static boolean showsFormedToggle(MachineDefinition machine) {
        return machine != null && machine.hideComponentsWhenFormed();
    }

    /** 成型预览开关当前状态对应的提示键：按下去会切到哪一种视图。 */
    public static String formedTooltipKey(boolean formed) {
        return formed ? "gui.preview.button.toggle_unformed.tip" : "gui.preview.button.toggle_formed.tip";
    }

    /**
     * 选出这一帧要画的方块。
     *
     * <p>顺序是固定的：<strong>先按成型状态裁剪，再按层过滤</strong>。反过来会让层模式的层号在裁剪前后指向
     * 不同的高度，玩家在切片视图里点成型开关就会看到一层「消失」的方块，而不是收起组件后的同一层。
     *
     * <p>这是 static 的：它只依赖传入的参数，不依赖面板的鼠标、相机或按钮状态，所以复现器可以离线核对，
     * 不需要打开界面。
     */
    public static List<StructurePreview.Block> selectBlocks(StructurePreview preview, boolean formed,
                                                            boolean hideComponentsWhenFormed, boolean layerMode,
                                                            int layer) {
        StructurePreview shown = formed && hideComponentsWhenFormed ? preview.hidingComponents() : preview;
        return layerMode ? shown.blocksInLayer(layer) : shown.blocks();
    }

    /** The Y level the slice preview is showing, or {@code null} when the structure has no blocks at all. */
    public Integer selectedLayerY() {
        List<Integer> layers = this.preview.layers();
        if (layers.isEmpty()) {
            return null;
        }
        return layers.get(Mth.clamp(this.layerIndex, 0, layers.size() - 1));
    }

    private void setLayerMode(boolean enabled) {
        this.layerMode = enabled;
        this.layerToggleButton.setToggled(enabled);
        if (enabled) {
            // The original called setRenderLayer(minY) when slice mode was switched on, so start at the bottom.
            this.layerIndex = 0;
        }
    }

    private void stepLayer(int delta) {
        int last = Math.max(0, this.preview.layers().size() - 1);
        this.layerIndex = Mth.clamp(this.layerIndex + delta, 0, last);
    }

    private void resetView() {
        this.yaw = StructurePreviewRenderer.DEFAULT_YAW;
        this.pitch = StructurePreviewRenderer.DEFAULT_PITCH;
        this.zoom = 1.0F;
        this.panX = 0.0F;
        this.panY = 0.0F;
    }

    // -------------------------------------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------------------------------------

    /**
     * Draws the whole panel into the caller's pose at {@code (originX, originY)}.
     *
     * <p>The panel background itself is the host's job: the screen blits {@link PreviewAtlas#PANEL} and JEI
     * draws it through {@code getBackground()}.
     *
     * @param mouseX panel-space mouse X, used for hover states
     */
    public void render(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY) {
        render(graphics, originX, originY, mouseX, mouseY, true);
    }

    /**
     * @param drawIngredientItems whether this class paints the ingredient grid. The screen does; the JEI
     *        category does not, because there the grid is made of real recipe slots that JEI draws itself. The
     *        overflow marker is drawn either way, since no host can draw that one.
     */
    public void render(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY,
                       boolean drawIngredientItems) {
        Font font = Minecraft.getInstance().font;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // The structure goes down first, scissored to the viewport, then the buttons and overlays are painted
        // over it: the original's bottom strip sits at y=161, inside the 26..176 viewport band, so it has to be
        // drawn after the blocks or the structure would cover it.
        boolean hideComponents = this.machine.hideComponentsWhenFormed();
        List<StructurePreview.Block> blocks;
        if (this.layerMode) {
            Integer layer = selectedLayerY();
            blocks = layer == null ? List.of()
                    : selectBlocks(this.preview, this.formedPreview, hideComponents, true, layer);
        } else {
            blocks = selectBlocks(this.preview, this.formedPreview, hideComponents, false, 0);
        }
        int variant = this.cycleBlocks ? (int) (Util.getMillis() / CYCLE_PERIOD_MILLIS) : 0;
        long renderStart = System.nanoTime();
        StructurePreviewRenderer.clip(graphics, originX + PreviewLayout.PREVIEW_X, originY + PreviewLayout.PREVIEW_Y,
                PreviewLayout.PREVIEW_WIDTH, PreviewLayout.PREVIEW_HEIGHT);
        try {
            StructurePreviewRenderer.render(graphics, this.preview, blocks, variant,
                    originX + PreviewLayout.PREVIEW_X + PreviewLayout.PREVIEW_WIDTH / 2.0F + this.panX,
                    originY + PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT / 2.0F + this.panY,
                    PreviewLayout.PREVIEW_BOX, this.yaw, this.pitch, this.zoom, this.selectedBlockPos,
                    selectedModifierPositions(),this.machine.id(),this.formedPreview);
            PreviewRenderStats.record(blocks.size(), System.nanoTime() - renderStart, Util.getMillis());
        } finally { graphics.disableScissor(); }
        drawStatus(graphics, font, originX, originY, blocks.size());

        if (drawIngredientItems) {
            StructurePreviews.drawComponents(graphics, this.components, originX, originY, mouseX, mouseY);
        }
        StructurePreviews.drawComponentOverflow(graphics, this.components, originX, originY);
        drawSelectedBlock(graphics, originX, originY);
        drawModifierIngredients(graphics, originX, originY);

        drawTitle(graphics, font, originX, originY);

        for (PreviewButton button : this.buttons) {
            button.render(graphics, originX, originY, mouseX, mouseY);
        }
        if (this.layerMode) {
            drawLayerSelector(graphics, originX, originY, mouseX, mouseY);
        }
        if (this.machineInfoVisible) {
            drawMachineInfo(graphics, font, originX, originY);
        }
    }

    private void drawStatus(GuiGraphics graphics, Font font, int originX, int originY, int shown) {
        Component state = preview.isEmpty() ? Component.translatable("gui.modular_machinery_reborn.preview.status.empty")
                : Component.translatable("gui.modular_machinery_reborn.preview.status.blocks", shown, preview.blocks().size());
        int width = showsFormedToggle(machine) ? 151 : 169;
        String label = state.getString();
        if (font.width(label) > width) label = font.plainSubstrByWidth(label, width - font.width("…")) + "…";
        graphics.fill(originX + 7, originY + 27, originX + 7 + Math.min(width + 2, font.width(label) + 4), originY + 39, 0x99000000);
        graphics.drawString(font, label, originX + 9, originY + 29, 0xffeeeeee, false);
    }

    /**
     * 标题行：旧版把 174px 分成「36px prefix + 2px 间隔 + 136px 机器名」三段。
     *
     * <p>前缀的翻译键是 {@code <namespace>.<path>.prefix}（{@link MachineDefinition#prefixTranslationKey()}）。
     * 旧版先看这个键有没有翻译，没有再用 {@code prefix} 字面量；两样都没有时，旧版会把<strong>裸语言键</strong>
     * 画在 36px 槽里——那是旧 UI 的缺陷，本版不复刻：没有前缀时机器名直接占满整行 174px。
     *
     * <p>两段各自用 scissor 裁到自己的宽度。旧版的 MultiLineLabel 高度固定 18px，超长文本同样只会露出一部分；
     * 不裁的话，一个长前缀会压到机器名上，那比截断更难读。
     */
    private void drawTitle(GuiGraphics graphics, Font font, int originX, int originY) {
        String prefixKey = this.machine.prefixTranslationKey();
        String literal = this.machine.prefix();
        Component prefix = null;
        if (literal != null) {
            prefix = Component.translatableWithFallback(prefixKey, literal);
        } else if (Language.getInstance().has(prefixKey)) {
            prefix = Component.translatable(prefixKey);
        }

        Component name = this.machine.displayName();
        int textY = originY + PreviewLayout.TITLE_TEXT_Y;
        if (prefix == null) {
            String caption = name.getString();
            if (font.width(caption) > PreviewLayout.TITLE_WIDTH) caption = font.plainSubstrByWidth(caption, PreviewLayout.TITLE_WIDTH - font.width("…")) + "…";
            graphics.drawCenteredString(font, caption, originX + PreviewLayout.PANEL_WIDTH / 2, textY,
                    PreviewLayout.LABEL_COLOR);
            return;
        }

        int prefixRight = originX + PreviewLayout.TITLE_X + 36;
        graphics.enableScissor(originX + PreviewLayout.TITLE_X, originY + PreviewLayout.TITLE_Y,
                prefixRight, originY + PreviewLayout.TITLE_Y + PreviewLayout.TITLE_HEIGHT);
        try {
            graphics.drawCenteredString(font, prefix, originX + PreviewLayout.TITLE_X + 36 / 2, textY,
                    PreviewLayout.LABEL_COLOR);
        } finally { graphics.disableScissor(); }

        int nameLeft = prefixRight + 2;
        graphics.enableScissor(nameLeft, originY + PreviewLayout.TITLE_Y,
                nameLeft + 136, originY + PreviewLayout.TITLE_Y + PreviewLayout.TITLE_HEIGHT);
        try {
            graphics.drawCenteredString(font, name, nameLeft + 136 / 2, textY, PreviewLayout.LABEL_COLOR);
        } finally { graphics.disableScissor(); }
    }

    private void drawLayerSelector(GuiGraphics graphics, int originX, int originY, double mouseX, double mouseY) {
        PreviewAtlas.LAYER_TRACK.draw(graphics, originX + PreviewLayout.LAYER_STEPPER_X,
                originY + PreviewLayout.LAYER_TRACK_Y);

        PreviewAtlas.Sprite thumb = PreviewAtlas.LAYER_THUMB;
        if (isOverTrack(mouseX, mouseY)) {
            thumb = PreviewAtlas.LAYER_THUMB_HOVERED;
        }
        int thumbX = originX + PreviewLayout.LAYER_STEPPER_X + 1;
        int thumbY = originY + layerThumbY();
        thumb.draw(graphics, thumbX, thumbY);

        this.layerUp.render(graphics, originX, originY, mouseX, mouseY);
        this.layerDown.render(graphics, originX, originY, mouseX, mouseY);
    }

    /** Panel-space Y of the scrollbar thumb: the top of the track is the highest level, as in the original. */
    private int layerThumbY() {
        int last = this.preview.layers().size() - 1;
        int travel = PreviewLayout.LAYER_TRACK_HEIGHT - PreviewLayout.LAYER_THUMB_HEIGHT;
        int progress = last <= 0 ? 0 : (last - Mth.clamp(this.layerIndex, 0, last)) * travel / last;
        return PreviewLayout.LAYER_TRACK_Y + progress;
    }

    private void drawMachineInfo(GuiGraphics graphics, Font font, int originX, int originY) {
        int padding = 2;
        int lineHeight = font.lineHeight;
        int width = 0;
        for (Component line : this.machineInfoLines) {
            width = Math.max(width, font.width(line));
        }
        int x = originX + PreviewLayout.PREVIEW_X + padding;
        int y = originY + PreviewLayout.PREVIEW_Y + 16;
        width = PreviewLayout.PREVIEW_WIDTH - padding * 4;
        int shown = Math.min(this.machineInfoLines.size(), (PreviewLayout.BUTTON_STRIP_Y - PreviewLayout.PREVIEW_Y - 20) / lineHeight);
        int boxWidth = width + padding * 2;
        int boxHeight = shown * lineHeight + padding * 2;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xC0000000);
        for (int i = 0; i < shown; i++) {
            String line = this.machineInfoLines.get(i).getString();
            if (font.width(line) > width) line = font.plainSubstrByWidth(line, Math.max(0, width - font.width("…"))) + "…";
            graphics.drawString(font, line, x + padding, y + padding + i * lineHeight,
                    0xFFFFFF, false);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------------------------------------

    /**
     * What the panel did with one mouse event, in panel space: which kind of element claimed it and, for the
     * widgets that have one, that element's name.
     *
     * <p>This replaced a plain boolean for one reason: a boolean could not tell "the press landed on a button"
     * apart from "the press fell through to the viewport", and the viewport covers the whole 26..176 band that
     * also contains the button strip and the layer selector, so <i>every</i> press inside that band returned
     * {@code true} whether or not it hit anything. The JEI input diagnostic now reports the kind, which is what
     * makes the next log conclusive.
     */
    public record PointerClaim(Kind kind, String name) {

        /** The kinds of element a press or a release can land on. */
        public enum Kind {
            /** Nothing interactive: the event falls through to the host. */
            NONE,
            /** One of the four 13px buttons of the bottom strip. */
            BUTTON,
            /** One of the layer selector's two 9px arrows. */
            LAYER_ARROW,
            /** The layer selector's 9x90 scrollbar track. */
            LAYER_TRACK,
            /** The 3D viewport, claimed for rotate (left), pan (right) or the middle-click reset. */
            VIEWPORT,
            /** 材料格的一格（E2，仅 Screen 宿主；JEI 宿主的格子是真槽位，走 JEI 自己的 R/U）。 */
            MATERIAL
        }

        /** Nothing claimed; the host may act on the event itself. */
        public static final PointerClaim NONE = new PointerClaim(Kind.NONE, "");
        /** The layer selector's track. */
        public static final PointerClaim LAYER_TRACK = new PointerClaim(Kind.LAYER_TRACK, "layer-track");
        /** The 3D viewport. */
        public static final PointerClaim VIEWPORT = new PointerClaim(Kind.VIEWPORT, "viewport");
        /** 材料格的一格（E2；点击已被 JEI 查询消费）。 */
        public static final PointerClaim MATERIAL = new PointerClaim(Kind.MATERIAL, "ingredient-grid");

        static PointerClaim button(PreviewButton button) {
            return new PointerClaim(Kind.BUTTON, button.name());
        }

        static PointerClaim layerArrow(PreviewButton arrow) {
            return new PointerClaim(Kind.LAYER_ARROW, arrow.name());
        }

        /** Whether an element was claimed, i.e. whether the host should treat the event as handled. */
        public boolean claimed() {
            return this.kind != Kind.NONE;
        }

        /** The claimed element as {@code button machine-info} / {@code layer-arrow up} / {@code viewport} / {@code none}. */
        public String describe() {
            switch (this.kind) {
                case BUTTON:
                    return "button " + this.name;
                case LAYER_ARROW:
                    return "layer-arrow " + this.name;
                case LAYER_TRACK:
                    return "layer-track";
                case VIEWPORT:
                    return "viewport";
                case MATERIAL:
                    return "material-grid";
                default:
                    return "none";
            }
        }
    }

    /**
     * Handles a press at panel coordinates.
     *
     * <p>Buttons are tested before the viewport because the button strip is an overlay on the viewport; a press
     * on a button must not also start a rotation. Nothing is acted on here except the layer track, which follows
     * the press so that a drag can continue from it.
     *
     * <p>The claim is what the JEI entry point reports as its simulated "would you handle this?" answer, so it
     * has to be truthful: a press the panel will act on (a button, a layer arrow, the layer track, or the
     * viewport for a rotate/pan/middle-click-reset gesture) claims it, anything else reports
     * {@link PointerClaim#NONE} so the click still reaches whatever is underneath. The viewport is a claim in its
     * own right and is reported as {@link Kind#VIEWPORT} rather than as a hit, so a press that only landed in the
     * 3D view can never be mistaken for a press that found a button.
     *
     * @return which element claimed the press, never {@code null}
     */
    public PointerClaim mousePressed(double mouseX, double mouseY, int button) {
        // Only one button at a time may be armed, so a press anywhere resets the others.
        for (PreviewButton candidate : this.buttons) {
            candidate.disarm();
        }
        this.layerUp.disarm();
        this.layerDown.disarm();
        this.draggingLayer = false;
        this.draggingView = false;
        this.viewportClickCandidate = false;
        this.viewportMoved = false;

        // E2（0.101.0）：材料格的 JEI 查询，照旧版 SlotItemVirtualJEI.onMouseClick——
        // 左键把该物品作为产出查配方、右键作为输入查用途，点击即消费。
        // 材料格（y≥179）与按钮条（161..174）、视口（26..176）不相交，提前返回不影响其它手势。
        // P10-10（0.121.34）：查询范围从仅底部材料（componentAt）扩为 ingredientAt——
        // 与键盘 R/U 同一查找：选中主图标（落在 overStatus 矩形里，旧路径随后 return NONE）
        // 与候选轮换图标（旧路径被当视口手势）都接入鼠标左/右键查询。
        // JEI 类只在 ModList 确认在场后才被类加载（JEI 是可选依赖，mandatory=false）。
        if (!this.jeiHost && (button == 0 || button == 1) && ModList.get().isLoaded("jei")) {
            ItemStack hovered = this.ingredientAt(mouseX, mouseY);
            if (!hovered.isEmpty() && PreviewJeiActions.show(hovered, button == 1)) {
                return PointerClaim.MATERIAL;
            }
        }

        if (button == 0) {
            if (overStatus(mouseX, mouseY) || overInfo(mouseX, mouseY)) return PointerClaim.NONE;
            for (PreviewButton candidate : this.buttons) {
                if (candidate.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.button(candidate);
                }
            }
            if (this.layerMode) {
                if (this.layerUp.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.layerArrow(this.layerUp);
                }
                if (this.layerDown.mousePressed(mouseX, mouseY)) {
                    return PointerClaim.layerArrow(this.layerDown);
                }
                if (isOverTrack(mouseX, mouseY)) {
                    this.draggingLayer = true;
                    setLayerFromTrack(mouseY);
                    return PointerClaim.LAYER_TRACK;
                }
            }
            PointerClaim claim = claimViewport(mouseX, mouseY);
            if (claim.claimed()) {
                this.viewportClickCandidate = true;
                this.viewportPressX = mouseX;
                this.viewportPressY = mouseY;
            }
            return claim;
        }
        // Right-drag pans, and a middle click over the viewport is the original's reset-center gesture, which is
        // resolved on release. Both claim the press over the viewport so the host does not treat it as a click on
        // something else. Arming draggingView for the middle button is inert: mouseDragged only acts on buttons 0
        // and 1, so a middle-button drag still turns or pans nothing.
        if (button == 1 || button == 2) {
            return claimViewport(mouseX, mouseY);
        }
        return PointerClaim.NONE;
    }

    /** Arms the viewport drag when the press is inside the 3D view, and reports the claim either way. */
    private PointerClaim claimViewport(double mouseX, double mouseY) {
        if (!isOverPreview(mouseX, mouseY)) {
            return PointerClaim.NONE;
        }
        this.draggingView = true;
        return PointerClaim.VIEWPORT;
    }

    /** True while a scrollbar-thumb drag is in progress, so the host can stop forwarding to other gestures. */
    public boolean isDraggingLayer() {
        return this.draggingLayer;
    }

    /**
     * Handles a release: fires the button that was pressed, if the release is still on it, and resets the
     * middle-click "reset center" gesture.
     *
     * <p>At most one button can be armed, because a press disarms the others, so at most one can fire here; the
     * others are still asked so that their {@code armed} flag is cleared. The claim names the widget that fired,
     * which is what lets the JEI diagnostic print a press/release pair that reads as
     * {@code simulate -> button machine-info} followed by {@code execute -> button machine-info}.
     */
    public PointerClaim mouseReleased(double mouseX, double mouseY, int button) {
        PreviewButton fired = null;
        for (PreviewButton candidate : this.buttons) {
            if (candidate.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = candidate;
            }
        }
        if (this.layerMode) {
            if (this.layerUp.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = this.layerUp;
            }
            if (this.layerDown.mouseReleased(mouseX, mouseY) && fired == null) {
                fired = this.layerDown;
            }
        }
        boolean endedLayerDrag = this.draggingLayer;
        boolean selectOnRelease = button == 0 && this.viewportClickCandidate && !this.viewportMoved
                && Math.abs(mouseX - this.viewportPressX) <= 5.0
                && Math.abs(mouseY - this.viewportPressY) <= 5.0;
        this.draggingLayer = false;
        this.draggingView = false;
        this.viewportClickCandidate = false;
        this.viewportMoved = false;
        if (fired != null) {
            return PointerClaim.button(fired);
        }
        if (endedLayerDrag) {
            return PointerClaim.LAYER_TRACK;
        }
        if (selectOnRelease) {
            int modifier = modifierIngredientAt(mouseX, mouseY);
            if (modifier >= 0) {
                this.selectedModifier = this.selectedModifier == modifier ? -1 : modifier;
            } else {
                selectBlockAt(mouseX, mouseY);
            }
            return PointerClaim.VIEWPORT;
        }
        if (button == 2 && isOverPreview(mouseX, mouseY)) {
            // Middle click is the original's reset gesture as well as its reset button.
            resetView();
            return PointerClaim.VIEWPORT;
        }
        return PointerClaim.NONE;
    }

    /**
     * Handles a mouse drag.
     *
     * <p>Left drag turns the structure — horizontal motion changes {@code pitch}, vertical motion changes the
     * clamped {@code yaw}, following the original's {@code WorldSceneRendererWidget} naming. Right drag pans.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingLayer) {
            setLayerFromTrack(mouseY);
            return true;
        }
        if (!this.draggingView) {
            // The drag started on a button or outside the viewport; do not turn the structure.
            return false;
        }
        if (button == 0) {
            if (Math.abs(mouseX - this.viewportPressX) > 5.0 || Math.abs(mouseY - this.viewportPressY) > 5.0) {
                this.viewportMoved = true;
            }
            this.pitch += (float) dragX * DRAG_SENSITIVITY;
            this.yaw = Mth.clamp(this.yaw - (float) dragY * DRAG_SENSITIVITY,
                    StructurePreviewRenderer.MIN_YAW, StructurePreviewRenderer.MAX_YAW);
            return true;
        }
        if (button == 1) {
            // The original's right-drag moved the camera target; pixel panning is equivalent here and stable
            // at every zoom level.
            this.panX += (float) dragX;
            this.panY += (float) dragY;
            return true;
        }
        return false;
    }

    /**
     * Handles the wheel over the viewport: zoom in 3D mode, level stepping in slice mode.
     *
     * <p>Scrolling up selects the level above, matching the up arrow, which is also the direction the original's
     * scrollbar moved for a positive wheel delta.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (overStatus(mouseX, mouseY) || overInfo(mouseX, mouseY)) return true;
        if (!isOverPreview(mouseX, mouseY)) {
            return false;
        }
        if (isOverModifierList(mouseX, mouseY)) {
            int last = Math.max(0, this.modifierIngredients.size() - 6);
            this.modifierScroll = Mth.clamp(this.modifierScroll + (delta > 0 ? -1 : 1), 0, last);
            return true;
        }
        if (this.layerMode) {
            stepLayer(delta > 0 ? 1 : -1);
        } else {
            this.zoom = Mth.clamp(this.zoom + (float) delta * SCROLL_SENSITIVITY,
                    StructurePreviewRenderer.MIN_ZOOM, StructurePreviewRenderer.MAX_ZOOM);
        }
        return true;
    }

    public boolean isOverPreview(double mouseX, double mouseY) {
        return mouseX >= PreviewLayout.PREVIEW_X
                && mouseX < PreviewLayout.PREVIEW_X + PreviewLayout.PREVIEW_WIDTH
                && mouseY >= PreviewLayout.PREVIEW_Y
                && mouseY < PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT;
    }

    /**
     * E2（0.101.0）：悬停在材料格上时的 JEI 快捷键（R=查配方、U=查用途），照旧版
     * {@code SlotItemVirtualJEI.onKeyTyped}——只在悬停于格上时响应。键位本身由 JEI 的
     * {@code IJeiKeyMappings} 给出（改键跟随 JEI 配置）。旧版的 A 键书签在 JEI 15 的公开
     * API 里没有编程式入口，有据不移植。Screen 宿主专用（JEI 宿主不转发按键）。
     */
    public boolean keyPressed(int keyCode, int scanCode) {
        if (this.jeiHost || !ModList.get().isLoaded("jei")) {
            return false;
        }
        ItemStack hovered = ingredientAt(this.lastMouseX,this.lastMouseY);
        return hovered != null && !hovered.isEmpty() && PreviewJeiActions.keyPressed(hovered, keyCode, scanCode);
    }

    private List<StructurePreview.Block> visibleBlocks() {
        if (this.layerMode) {
            Integer layer = selectedLayerY();
            return layer == null ? List.of()
                    : selectBlocks(this.preview, this.formedPreview, this.machine.hideComponentsWhenFormed(), true, layer);
        }
        return selectBlocks(this.preview, this.formedPreview, this.machine.hideComponentsWhenFormed(), false, 0);
    }

    private void selectBlockAt(double mouseX, double mouseY) {
        if (!isOverPreview(mouseX,mouseY)) { this.selectedBlockPos=null; return; }
        StructurePreview.Block hit = StructurePreviewRenderer.pickBlockAt(this.preview, visibleBlocks(), mouseX, mouseY,
                PreviewLayout.PREVIEW_X + PreviewLayout.PREVIEW_WIDTH / 2.0 + this.panX,
                PreviewLayout.PREVIEW_Y + PreviewLayout.PREVIEW_HEIGHT / 2.0 + this.panY,
                PreviewLayout.PREVIEW_BOX, this.yaw, this.pitch, this.zoom);
        this.selectedBlockPos = hit == null ? null : hit.pos();
    }

    private void drawSelectedBlock(GuiGraphics graphics, int originX, int originY) {
        StructurePreview.Block selected = selectedBlock();
        if (selected == null) {
            return;
        }
        int variant = this.cycleBlocks ? (int) (Util.getMillis() / CYCLE_PERIOD_MILLIS) : 0;
        ItemStack main = itemFor(selected.variantState(variant));
        if (main.isEmpty()) {
            return;
        }
        List<ItemStack> alternatives = selected.alternatives().stream()
                .map(PreviewPanel::itemFor)
                .filter(stack -> !stack.isEmpty() && !ItemStack.isSameItem(stack, main))
                .toList();
        int shown = Math.min(6, alternatives.size());
        // This is the original selected-block area (main ingredient at 8,28; alternatives below it), drawn over
        // the viewport only after an explicit block click so it does not obscure the default structure view.
        int x = selectedItemX();
        graphics.fill(originX + x - 3, originY + 27, originX + x + 19, originY + 49 + shown * 18,
                0xB0202020);
        if (!this.jeiHost) graphics.renderItem(main, originX + x, originY + 30);
        for (int index = 0; index < shown; index++) {
            if (!this.jeiHost) graphics.renderItem(alternatives.get(index), originX + x, originY + 51 + index * 18);
        }
        if (alternatives.size() > shown) {
            graphics.drawString(Minecraft.getInstance().font, Component.literal("+" + (alternatives.size() - shown)),
                    originX + x + 1, originY + 52 + shown * 18, 0xFFFFFFFF, false);
        }
    }

    private StructurePreview.Block selectedBlock() {
        if (this.selectedBlockPos == null) {
            return null;
        }
        for (StructurePreview.Block block : visibleBlocks()) {
            if (block.pos().equals(this.selectedBlockPos)) {
                return block;
            }
        }
        return null;
    }

    private static ItemStack itemFor(net.minecraft.world.level.block.state.BlockState state) {
        return com.reborn.modularmachinery.item.PropertyBlockItem.stackForState(state);
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return this.layerMode
                && mouseX >= PreviewLayout.LAYER_STEPPER_X
                && mouseX < PreviewLayout.LAYER_STEPPER_X + PreviewLayout.LAYER_TRACK_WIDTH
                && mouseY >= PreviewLayout.LAYER_TRACK_Y
                && mouseY < PreviewLayout.LAYER_TRACK_Y + PreviewLayout.LAYER_TRACK_HEIGHT;
    }

    /** Maps a click or drag on the track to the nearest level, with the top of the track as the highest one. */
    private void setLayerFromTrack(double mouseY) {
        int last = this.preview.layers().size() - 1;
        if (last <= 0) {
            this.layerIndex = 0;
            return;
        }
        int travel = PreviewLayout.LAYER_TRACK_HEIGHT - PreviewLayout.LAYER_THUMB_HEIGHT;
        double offset = mouseY - PreviewLayout.LAYER_TRACK_Y - PreviewLayout.LAYER_THUMB_HEIGHT / 2.0;
        double progress = Mth.clamp(offset / travel, 0.0, 1.0);
        this.layerIndex = Mth.clamp((int) Math.round((1.0 - progress) * last), 0, last);
    }

    // -------------------------------------------------------------------------------------------------------
    // Tooltips
    // -------------------------------------------------------------------------------------------------------

    /**
     * The tooltip lines for a panel-space mouse position, or an empty list when nothing there has one.
     *
     * <p>Both hosts use this: the screen renders the result itself, and the JEI category returns it from
     * {@code IRecipeCategory.getTooltip}. Ingredient tooltips are deliberately not included — JEI provides them
     * for its own recipe slots, and the screen asks {@link #componentAt} for the item directly.
     */
    private boolean overInfo(double x, double y) { return machineInfoVisible && x >= 8 && x < 176 && y >= 42 && y < 42 + Math.min(machineInfoLines.size(), 12) * 9 + 4; }

    private boolean overStatus(double x, double y) { return x >= 7 && x < (showsFormedToggle(machine) ? 160 : 177) && y >= 27 && y < 40; }

    public List<Component> tooltipLines(double mouseX, double mouseY) {
        if (mouseX >= PreviewLayout.TITLE_X && mouseX < PreviewLayout.TITLE_X + PreviewLayout.TITLE_WIDTH
                && mouseY >= PreviewLayout.TITLE_Y && mouseY < PreviewLayout.TITLE_Y + PreviewLayout.TITLE_HEIGHT) return List.of(machine.displayName());
        if (overInfo(mouseX, mouseY)) return List.copyOf(machineInfoLines);
        for (PreviewButton button : this.buttons) {
            if (button.contains(mouseX, mouseY)) {
                return button.tooltipLines();
            }
        }
        if (this.layerMode) {
            if (this.layerUp.contains(mouseX, mouseY)) {
                return layerArrowTooltip("up");
            }
            if (this.layerDown.contains(mouseX, mouseY)) {
                return layerArrowTooltip("down");
            }
            if (isOverTrack(mouseX, mouseY)) {
                return List.of(Component.translatable("gui.preview.button.layer_render_scrollbar.tip"),
                        layerStateLine());
            }
        }
        if (overStatus(mouseX, mouseY)) return List.of(Component.translatable("gui.modular_machinery_reborn.preview.status.blocks", visibleBlocks().size(), preview.blocks().size()),
                Component.translatable("gui.modular_machinery_reborn.preview.status.hint"));
        int modifier = modifierIngredientAt(mouseX, mouseY);
        if (modifier >= 0) {
            String description = this.modifierIngredients.get(modifier).description();
            if (!description.isBlank()) {
                return List.of(Component.translatable("gui.modular_machinery_reborn.preview.modifier"),
                        Component.literal(description));
            }
            return List.of(Component.translatable("gui.modular_machinery_reborn.preview.modifier"));
        }
        if (isOverPreview(mouseX, mouseY)) {
            return List.of(Component.translatable("gui.modular_machinery_reborn.preview.hint"));
        }
        return List.of();
    }

    /** The item under a panel-space position in the ingredient grid, or {@code null}. */
    public ItemStack componentAt(double mouseX, double mouseY) {
        return StructurePreviews.componentAt(this.components, mouseX, mouseY);
    }

    /** The selected block item under a panel-space position, for the blueprint screen's item tooltip. */
    public ItemStack selectedItemAt(double mouseX, double mouseY) {
        for (SelectedIngredient entry : selectedIngredients()) if (insideItem(mouseX,mouseY,entry.x(),entry.y())) return entry.stack();
        return ItemStack.EMPTY;
    }

    private int selectedItemX() { return modifierIngredients.isEmpty() ? 10 : 30; }

    public record SelectedIngredient(ItemStack stack, int x, int y) {}

    public List<SelectedIngredient> selectedIngredients() {
        StructurePreview.Block selected = selectedBlock();
        if (selected == null) {
            return List.of();
        }
        int variant = this.cycleBlocks ? (int) (Util.getMillis() / CYCLE_PERIOD_MILLIS) : 0;
        ItemStack main = itemFor(selected.variantState(variant));
        if (main.isEmpty()) return List.of();
        List<SelectedIngredient> result = new ArrayList<>();
        result.add(new SelectedIngredient(main,selectedItemX(),30));
        List<ItemStack> alternatives = selected.alternatives().stream()
                .map(PreviewPanel::itemFor)
                .filter(stack -> !stack.isEmpty() && !ItemStack.isSameItem(stack, main))
                .limit(6)
                .toList();
        for (int index = 0; index < alternatives.size(); index++) {
            result.add(new SelectedIngredient(alternatives.get(index),selectedItemX(),51+index*18));
        }
        return List.copyOf(result);
    }

    public ItemStack ingredientAt(double mouseX, double mouseY) {
        ItemStack selected=selectedItemAt(mouseX,mouseY);
        if (!selected.isEmpty()) return selected;
        ItemStack component=componentAt(mouseX,mouseY);
        return component==null ? ItemStack.EMPTY : component;
    }

    private void drawModifierIngredients(GuiGraphics graphics, int originX, int originY) {
        int shown = Math.min(6, this.modifierIngredients.size() - this.modifierScroll);
        for (int i = 0; i < shown; i++) {
            int x = originX + 7;
            int y = originY + 54 + i * 18;
            int ingredientIndex = this.modifierScroll + i;
            graphics.fill(x, y, x + 18, y + 18,
                    this.selectedModifier == ingredientIndex ? 0xC0008050 : 0xB0202020);
            graphics.renderItem(this.modifierIngredients.get(ingredientIndex).icon(), x + 1, y + 1);
            if (this.selectedModifier == ingredientIndex) {
                graphics.fill(x, y, x + 18, y + 1, 0xFF00FF80);
                graphics.fill(x, y + 17, x + 18, y + 18, 0xFF00FF80);
            }
        }
        if (this.modifierScroll > 0 || this.modifierIngredients.size() > this.modifierScroll + shown) {
            String more = (this.modifierScroll > 0 ? "↑" : "")
                    + (this.modifierIngredients.size() > this.modifierScroll + shown ? "↓" : "");
            graphics.drawString(Minecraft.getInstance().font, Component.literal(more),
                    originX + 9, originY + 54 + shown * 18, 0xFFFFFFFF, false);
        }
    }

    private int modifierIngredientAt(double mouseX, double mouseY) {
        if (mouseX < 7 || mouseX >= 25 || mouseY < 54) return -1;
        int visibleIndex = (int) ((mouseY - 54) / 18);
        int shown = Math.min(6, this.modifierIngredients.size() - this.modifierScroll);
        return visibleIndex >= 0 && visibleIndex < shown ? this.modifierScroll + visibleIndex : -1;
    }

    private boolean isOverModifierList(double mouseX, double mouseY) {
        return !this.modifierIngredients.isEmpty() && mouseX >= 7 && mouseX < 25
                && mouseY >= 54 && mouseY < 54 + Math.min(6, this.modifierIngredients.size()) * 18;
    }

    private Set<BlockPos> selectedModifierPositions() {
        if (this.selectedModifier < 0 || this.selectedModifier >= this.modifierIngredients.size()) return Set.of();
        return Set.copyOf(this.modifierIngredients.get(this.selectedModifier).positions());
    }

    private static boolean insideItem(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    private List<Component> machineInfoTooltip() {
        return this.machineInfoLines;
    }

    private List<Component> cycleTooltip() {
        if (this.cycleBlocks) {
            return List.of(Component.translatable("gui.preview.button.disable_cycle_replaceable_blocks.tip"));
        }
        return List.of(Component.translatable("gui.preview.button.enable_cycle_replaceable_blocks.tip.0"),
                Component.translatable("gui.preview.button.enable_cycle_replaceable_blocks.tip.1"));
    }

    private List<Component> layerToggleTooltip() {
        String key = this.layerMode
                ? "gui.preview.button.toggle_3d_render.tip"
                : "gui.preview.button.toggle_layer_render.tip";
        return List.of(Component.translatable(key));
    }

    private List<Component> layerArrowTooltip(String direction) {
        return List.of(Component.translatable("gui.preview.button.layer_render_scrollbar." + direction + ".tip"),
                layerStateLine());
    }

    /**
     * The original's {@code layer_render_scrollbar.state.tip} arguments: the level being drawn, relative to the
     * controller, over the {@code getMaxScroll()} the original passed — which for its scrollbar was the pattern's
     * highest Y. Our selector walks the levels that actually hold blocks, so the numerator is one of those Y
     * values; the denominator keeps the original's meaning.
     */
    private Component layerStateLine() {
        Integer layer = selectedLayerY();
        return Component.translatable("gui.preview.button.layer_render_scrollbar.state.tip",
                layer == null ? 0 : layer, this.preview.max().getY());
    }

    /**
     * M6e-2: the very list {@link #buildMachineInfo} produces, one string per line, with no {@code Font} and no
     * client state involved.
     *
     * <p>It exists because the two rows this release adds are the whole point of touching the panel, and the
     * acceptance rule forbids concluding anything from "a number appeared in a GUI". The offline harness cannot
     * open a window, so it drives this method with real {@link MachineDefinition}s and asserts the resulting
     * row sequence: <b>which</b> rows appear, in which order, and under which guard. It is the same list the
     * panel draws, because it is produced by the same method — the strings are only unwrapped for printing.
     */
    public static List<String> machineInfoRows(MachineDefinition machine) {
        return machineInfoRows(machine, StructurePreviews.initialDynamicPatternSize(machine));
    }

    /**
     * {@link #machineInfoRows(MachineDefinition)} for a chosen dynamic size.
     *
     * <p>复现器用它把「加长之后尺寸行是否跟着变」这件事断言下来——那不是一句界面文案的问题，
     * 而是「信息面板与画面是否一致」的问题，而后者是玩家能直接看出来的。
     */
    public static List<String> machineInfoRows(MachineDefinition machine, int dynamicPatternSize) {
        List<String> rows = new ArrayList<>();
        for (Component line : buildMachineInfo(machine, dynamicPatternSize)) {
            // Language data is only loaded inside a running client, so getString() answers the key itself here;
            // the arguments are appended so the numbers the rows would print are assertable too.
            Object[] args = line.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                    ? translatable.getArgs() : new Object[0];
            rows.add(args.length == 0 ? line.getString()
                    : line.getString() + " " + java.util.Arrays.toString(args));
        }
        return List.copyOf(rows);
    }

    /**
     * The original's {@code getMachineExtraInfo} list, minus the fields this project does not have.
     *
     * <p>M6c made two more of them computable, so they are printed here in the original's order — internal
     * parallelism, then max parallelism — straight off {@link MachineDefinition}, which is machine data and
     * needs no world. M6e-2 made the remaining two computable as well, so they now join the list in the
     * original's own position: right after max parallelism and before the blueprint line, each under the
     * original's guard.
     *
     * <p><b>波 3c：动态结构那一行现在有条件地打印了。</b>此前它一直在被省略，理由是「动态结构还没实现，
     * 打印一个 0 会声称机器没有动态结构」。现在动态结构真的会被展开，所以它可以按旧版的方式出现：
     * 只在机器<strong>确实有</strong>动态模式时加一行，对应旧版的
     * {@code if (!machine.getDynamicPatterns().isEmpty())}（{@code MachineStructurePreviewPanel.java:319}）。
     *
     * <p>长/高/宽那一行改用<strong>当前预览的那个结构</strong>而不是定义里的基座：动态段是真方块，
     * 加长一节投影就真的变长，尺寸行必须与画面一致。
     */
    private static List<Component> buildMachineInfo(MachineDefinition machine, int dynamicPatternSize) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.preview.button.machine_info"));
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.0"));
        MachinePattern shown = StructurePreviews.previewRenderPattern(machine, dynamicPatternSize);
        BlockPos size = shown.size();
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.1",
                size.getX(), size.getY(), size.getZ()));
        lines.add(Component.translatable("gui.preview.button.machine_info.controller_y_pos",
                Math.abs(shown.min().getY())));
        // The original only listed these two when the machine was not at the defaults
        // (MachineStructurePreviewPanel:298-307: internal parallelism > 0, max parallelism != the config
        // default), which is why a machine that has not opted in stays quiet.
        if (machine.internalParallelism() > 0) {
            lines.add(Component.translatable("gui.preview.button.machine_info.internal_parallelism",
                    machine.internalParallelism()));
        }
        if (machine.maxParallelism() != MachineDefinition.DEFAULT_MAX_PARALLELISM) {
            lines.add(Component.translatable("gui.preview.button.machine_info.max_parallelism",
                    machine.maxParallelism()));
        }
        // M6e-2. The original's guards, verbatim (MachineStructurePreviewPanel:308-317): the thread count only
        // for a machine that has a factory at all, the core-thread count only when the preset is not empty — so
        // a machine with no factory stays as quiet as it was, and a factory with an empty preset reports one
        // number rather than a misleading zero.
        if (machine.hasFactory()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.max_threads",
                    machine.maxThreads()));
        }
        if (!machine.coreThreads().isEmpty()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.core_threads",
                    machine.coreThreads().size()));
        }
        // 波 3c：存在动态结构时加这一行。位置按旧版——在 core_threads 之后、requires_blueprint 之前
        // （MachineStructurePreviewPanel 的 getMachineExtraInfo）。
        if (machine.hasDynamicPatterns()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.dynamic_pattern"));
        }
        if (machine.requiresBlueprint()) {
            lines.add(Component.translatable("gui.preview.button.machine_info.requires_blueprint"));
        }
        return List.copyOf(lines);
    }
}
