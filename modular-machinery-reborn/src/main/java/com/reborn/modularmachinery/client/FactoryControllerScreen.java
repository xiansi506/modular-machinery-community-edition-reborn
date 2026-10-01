package com.reborn.modularmachinery.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.factory.FactoryThread;
import com.reborn.modularmachinery.machine.ControllerStatus;
import com.reborn.modularmachinery.menu.FactoryControllerMenu;
import com.reborn.modularmachinery.menu.MachineControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The factory controller screen, reproduced from the original {@code GuiFactoryController} (389 lines).
 *
 * <h2>Panel</h2>
 *
 * <p>One image, {@code guifactory.png}, blitted whole. It is <b>280×213</b>, and that is an <i>actual</i>
 * texture size rather than an atlas region: the original set {@code xSize = 280} and {@code ySize = 213} in its
 * constructor ({@code :50-51}) and drew
 * {@code drawModalRectWithCustomSizedTexture(x, y, 0, 0, xSize, ySize, xSize, ySize)} ({@code :88}), so the
 * source rectangle is exactly the panel. The dimensions were re-measured from the PNG itself before this screen
 * was written (it is 280×213), not taken from a document.
 *
 * <p>The blit is <b>1:1 and must stay 1:1</b> — see {@link #panelTextureSize()}. The texture has the slot
 * holes drawn into it at exactly the coordinates {@code ContainerFactoryController} registers its slots at
 * (player holes at texels {@code 112 + 18c} × {@code 131/149/167} and {@code 189}, the blueprint's at
 * {@code (255, 8)}), so any scaling of the blit moves the frames away from the items and the click hitboxes.
 * 0.24.3's {@code blit(…, 280, 213)} took the 256×256 default and did exactly that.
 *
 * <h2>Recipe queue (left half, panel x 8 … 94)</h2>
 *
 * <p>The original listed core threads first and ordinary ones after ({@code :98-102}), six per page
 * ({@code MAX_PAGE_ELEMENTS = 6}), each drawn as an 86×32 element from {@code guifactoryelements.png}'s own
 * {@code (0, 0)} ({@code :36-38, :123}), stepping 33 panel pixels down ({@code FACTORY_ELEMENT_HEIGHT + 1},
 * {@code :107}). Three colourings, all reproduced:
 *
 * <ol>
 *   <li>the element's own tint — {@code (0.7, 0.9, 1.0)} for a core thread, white for an ordinary one
 *       ({@code :118-122});</li>
 *   <li>a <b>progress fill</b> drawn as the same sprite cropped to {@code width * progress}, tinted
 *       {@code (0.6, 1.0, 0.75)} while crafting and {@code (1.0, 0.6, 0.6)} otherwise
 *       ({@code :126-135}). The original tinted the whole quad and cropped the source; here the source stays
 *       whole and the <i>destination</i> is cropped, which draws the same pixels of the same sprite;</li>
 *   <li>the label and status text at {@code x = 8 / 0.72 + 2} inside a 0.72-scaled matrix, dark
 *       ({@code 0x222222}), wrapping at {@code (86 - 6) / 0.72} ({@code :141-187}).</li>
 * </ol>
 *
 * <p>The scrollbar is the original's {@code GuiScrollbar}: a 12×15 sprite from the <b>vanilla</b>
 * creative-inventory tab sheet at {@code (232, 0)} for the track and {@code (244, 0)} for the thumb
 * ({@code GuiScrollbar.java:9, 22-31}), positioned at {@code (94, 8)} with height 197 ({@code :33-35}) and a
 * range of {@code max(0, threads - 6)} ({@code :369-371}).
 *
 * <h2>Text (right half, panel x 113.3 … )</h2>
 *
 * <p>{@code drawFactoryStatus} ({@code :189-246}) draws the information block inside a 0.72-scaled matrix
 * starting at {@code (113, 12)}, each line stepping 10 and wrapped to {@code 135 / 0.72} — the same wrap width
 * the plain controller screen uses. The order is exactly the original's:
 *
 * <pre>
 * redstone stopped (and nothing else at all)
 * blueprint machine            (+15)
 * structure                    (+15 once formed)
 * factory status               (the original gated this on hasIdleThread(); see the divergence note)
 * "N 线程运行中 / M 最大线程数"   (only when max-threads &gt; 0)
 * parallelism, max parallelism (only when there is something to add up and the ceiling is &gt; 1)
 * the closing "Avg: … μs/t (Search: … ms), WorkMode: …" line
 * </pre>
 *
 * <h2>Honest divergences from the original</h2>
 *
 * <ol>
 *   <li><b>The status block is drawn whenever the structure is formed.</b> The original wrapped it in
 *       {@code if (factory.hasIdleThread())} ({@code :248-251}), which in this project is
 *       {@code FactoryEngine#hasIdleThread()}. That test is about whether a <i>new thread slot</i> is free, so
 *       a factory at its {@code max-threads} ceiling with every slot busy would print nothing about its state
 *       at all; the same five status values the plain controller screen shows are more useful than a blank.
 *       The gate is kept available and asserted on the server side, but the screen does not hide behind it.</li>
 *   <li><b>The parallelism rows do not depend on a loop over threads.</b> The original recomputed
 *       {@code parallelism} on the client by walking both thread collections and summing
 *       {@code activeRecipe.getParallelism() - 1} ({@code :280-297}). Here the number is the controller's own
 *       ledger — {@code activeParallelism} while a craft runs, the ceiling otherwise — which is the value the
 *       plain controller screen already shows, and the guard is the original's
 *       {@code maxParallelism > 1 && parallelism > 1}.</li>
 *   <li><b>A thread row prints its own status, not per-thread recipe names.</b> The original's row showed the
 *       thread name (or {@code gui.factory.thread}) and then {@code thread.getStatus().getUnlocMessage()}; that
 *       is reproduced. What is <i>not</i> reproduced is anything the client cannot know: this screen shows the
 *       same five status strings, and it does not invent a recipe name for a thread whose recipe the client was
 *       not sent.</li>
 *   <li><b>{@code gui.factory.threads} counts ordinary threads.</b> The original passed
 *       {@code getFactoryRecipeThreadList().size()} — the ordinary list only, core threads excluded — while the
 *       recipe queue above it lists core threads first. That asymmetry is the original's and is kept
 *       ({@code FactoryControllerMenu#ordinaryThreads()}).</li>
 * </ol>
 */
public final class FactoryControllerScreen extends AbstractContainerScreen<FactoryControllerMenu> {

    /** The original's {@code TEXTURES_FACTORY} ({@code GuiFactoryController.java:31}). */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guifactory.png");
    /** The original's {@code TEXTURES_FACTORY_ELEMENTS} ({@code :32}). */
    public static final ResourceLocation TEXTURE_ELEMENTS =
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "textures/gui/guifactoryelements.png");

    /**
     * The original's {@code GuiScrollbar.TEXTURES_TABS} ({@code GuiScrollbar.java:9}) — a vanilla sheet, the
     * same one the upgrade bus screen uses.
     */
    private static final ResourceLocation SCROLLBAR_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    private static final String KEY = "gui.modular_machinery_reborn.factory.";
    private static final String CONTROLLER_KEY = "gui.modular_machinery_reborn.controller.";

    /** The original's {@code xSize} / {@code ySize} ({@code GuiFactoryController.java:50-51}). */
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 213;

    /**
     * The texture's own size in texels, i.e. the value the blit must declare as its <b>texture</b> size.
     *
     * <p>The same two numbers as the panel — because the original blitted the whole sheet onto the whole
     * panel 1:1 — but they are a different quantity: {@link #PANEL_WIDTH}/{@link #PANEL_HEIGHT} are the
     * destination, these are the sheet. The M6c harness reads the PNG's IHDR and requires them to agree, so a
     * texture swapped for a differently sized one fails offline instead of on screen.
     */
    private static final int TEXTURE_WIDTH = 280;
    private static final int TEXTURE_HEIGHT = 213;

    /** The original's {@code FONT_SCALE} ({@code :30}). */
    private static final float FONT_SCALE = 0.72F;

    // ---- the recipe queue (the original's constants, :33-42) ------------------------------------
    private static final int SCROLLBAR_TOP = 8;
    private static final int SCROLLBAR_LEFT = 94;
    private static final int SCROLLBAR_HEIGHT = 197;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_TRACK_U = 232;
    private static final int SCROLLBAR_THUMB_U = 244;
    private static final int SCROLLBAR_V = 0;
    private static final int MAX_PAGE_ELEMENTS = 6;
    private static final int FACTORY_ELEMENT_WIDTH = 86;
    private static final int FACTORY_ELEMENT_HEIGHT = 32;
    /** The original's {@code TEXT_DRAW_OFFSET_X} / {@code TEXT_DRAW_OFFSET_Y} ({@code :39-40}). */
    private static final int TEXT_DRAW_OFFSET_X = 113;
    private static final int TEXT_DRAW_OFFSET_Y = 12;
    /** The original's {@code RECIPE_QUEUE_OFFSET_X} / {@code RECIPE_QUEUE_OFFSET_Y} ({@code :41-42}). */
    private static final int RECIPE_QUEUE_OFFSET_X = 8;
    private static final int RECIPE_QUEUE_OFFSET_Y = 8;
    /** The original's label offset inside a row: {@code (int) (8 / 0.72) + 2} ({@code :147}). */
    private static final int ROW_LABEL_X = (int) (RECIPE_QUEUE_OFFSET_X / FONT_SCALE) + 2;
    /** The original's row-text wrap width: {@code (int) ((86 - 6) / 0.72)} ({@code :173}). */
    private static final int ROW_WRAP_WIDTH = (int) ((FACTORY_ELEMENT_WIDTH - 6) / FONT_SCALE);
    /** The original's information-block wrap width: {@code MathHelper.floor(135 * (1 / 0.72))}. */
    private static final int WRAP_WIDTH = (int) (135 * (1 / FONT_SCALE));

    private static final int WHITE = 0xFFFFFF;
    private static final int ROW_TEXT = 0x222222;
    private static final int LINE = 10;
    /** The original's {@code if (tmp != offsetY) offsetY += 5} spacer ({@code :230-232}). */
    private static final int SPACER_GAP = 5;
    /** The original's {@code offsetY += 15} after a name or status block. */
    private static final int BLOCK_GAP = 15;
    /** The original's row text starts 2 panel pixels below the element's top ({@code :138}). */
    private static final int ROW_TEXT_Y = 2;

    private int scroll;
    private int scrollRange;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // The original set both in its constructor (:50-51); the panel really is this size.
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        // No inventoryLabelY: this screen draws no label and no title — see renderLabels below.
    }

    /** The threads the queue shows, core ones first — the original's own list assembly ({@code :98-102}). */
    private List<FactoryThread> threads() {
        MachineControllerBlockEntity machine = this.menu.machine();
        return machine == null ? List.of() : machine.factoryThreads();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        blitPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        List<FactoryThread> threads = threads();
        this.scrollRange = Math.max(0, threads.size() - MAX_PAGE_ELEMENTS);
        this.scroll = Math.max(0, Math.min(this.scrollRange, this.scroll));

        drawRecipeQueue(graphics, threads);
        drawScrollbar(graphics);
        // The information block is deliberately NOT drawn here — see renderLabels. This pass runs BEFORE
        // AbstractContainerScreen#render translates the pose by (leftPos, topPos) (verified against the mapped
        // 1.20.1 bytecode: the renderBg call is at offset 18, the pushPose/translate pair at 53-71, the
        // renderLabels call at 205), so every draw in it has to add the panel origin itself — which the queue
        // and the scrollbar above do, and which the block did not.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The panel blit's six arguments, computed from the screen's own origin and size.
     *
     * <p>Extracted so the M6c harness can assert the <b>source offset</b> rather than a derived position:
     * {@code GuiFactoryController:88} blitted the texture whole, so the source rectangle is exactly
     * {@code (0, 0, imageWidth, imageHeight)} and the destination starts at the panel origin. {@code x} and
     * {@code y} are therefore the same values every other draw call adds its panel-local offset to — which is
     * the one-origin property the 0.24.1 layout broke.
     *
     * @return {@code {destX, destY, srcU, srcV, width, height}}
     */
    public static int[] panelBlit(int leftPos, int topPos, int imageWidth, int imageHeight) {
        return new int[] {leftPos, topPos, 0, 0, imageWidth, imageHeight};
    }

    /**
     * The size the panel blit must declare as its <b>texture</b> size, in texels — the PNG's own IHDR
     * (280×213, read off the file by the M6c harness, which asserts this pair against it).
     *
     * <p>This is the argument whose absence shipped 0.24.3's visible bug. {@code GuiGraphics} has two blit
     * families: {@code blit(ResourceLocation, x, y, u, v, w, h)} takes <i>no</i> texture size and hard-codes
     * {@code 256, 256}, while {@code blit(ResourceLocation, x, y, blitOffset, u, v, w, h, textureWidth,
     * textureHeight)} takes the sheet's real size. 1.20.1's 7-argument overload is exactly
     * {@code this.blit(..., 256, 256)} (verified against the mapped bytecode), and this panel is
     * <b>280×213</b>, so it sampled {@code u ∈ [0, 280/256]} and {@code v ∈ [0, 213/256]}: the texture was
     * stretched by 1.09375× across the panel in x (the right-hand 26.25 texels wrapped around, which is the
     * second border column visible at the panel's right edge) and squeezed by 0.832 in y (so texels 177…212 —
     * the whole hotbar row of slot holes — were never drawn at all). The slots stayed where the container put
     * them, so the items no longer sat in their frames. The original never had the problem because
     * {@code GuiFactoryController:88} passed {@code xSize, ySize} as the <i>texture</i> size of
     * {@code drawModalRectWithCustomSizedTexture} — a 1:1 blit.
     */
    public static int[] panelTextureSize() {
        return new int[] {TEXTURE_WIDTH, TEXTURE_HEIGHT};
    }

    /**
     * The ten arguments the panel blit is made of, in the order the 9-argument
     * {@code GuiGraphics#blit(ResourceLocation, int, int, int, float, float, int, int, int, int)} takes them
     * minus the destination offset's {@code blitOffset}: {@code {destX, destY, uOffset, vOffset, width,
     * height, textureWidth, textureHeight}}.
     *
     * <p>Extracted so that the drawing call and the checks that reason about <i>where a texel lands</i> read
     * one array instead of two hand-copied argument lists. The harness maps the texture's own slot holes
     * through exactly these numbers and requires the result to coincide with the menu's slots.
     */
    public static int[] panelBlitFull(int leftPos, int topPos, int imageWidth, int imageHeight) {
        int[] blit = panelBlit(leftPos, topPos, imageWidth, imageHeight);
        int[] texels = panelTextureSize();
        return new int[] {blit[0], blit[1], blit[2], blit[3], blit[4], blit[5], texels[0], texels[1]};
    }

    /**
     * Blits the panel — the screen's only draw of it, so the texture size cannot drift away from the call.
     *
     * <p>The source region and the destination rectangle are both {@code imageWidth × imageHeight} (the 9-arg
     * overload has no separate source size) and the declared texture size is the PNG's, which makes the
     * mapping texel → panel pixel the identity: texel {@code (112, 131)} is painted at panel {@code (112,
     * 131)}, which is where {@code ContainerFactoryController} puts the slot it belongs to
     * ({@code ContainerFactoryController.java:94}).
     */
    public static void blitPanel(GuiGraphics graphics, int leftPos, int topPos, int imageWidth, int imageHeight) {
        int[] blit = panelBlitFull(leftPos, topPos, imageWidth, imageHeight);
        // The size comes from panelTextureSize(), not from blit[6]/blit[7] and never from a literal: the M6c
        // harness reads the call's descriptor AND requires this call site to ask for the size, so neither a
        // revert to the 256-default overload nor a "256, 256" typed in here can pass unnoticed.
        int[] texels = panelTextureSize();
        graphics.blit(TEXTURE, blit[0], blit[1], 0, blit[2], blit[3], blit[4], blit[5], texels[0], texels[1]);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The information block — and nothing else. Neither the container title nor the player-inventory label.
        //
        // The original drew neither: GuiFactoryController#drawGuiContainerForegroundLayer (:76-80) overrides
        // the 1.12.2 superclass method and never calls super, and that superclass method is the *only* place
        // vanilla draws 「物品栏」/«Inventory» (1.12.2 GuiContainer#drawGuiContainerForegroundLayer draws
        // I18n.format("container.inventory") at (8, ySize - 96 + 2)). Its own override drew the recipe queue
        // and the status block instead. GuiContainerBase (63 lines) does not add the label back either.
        //
        // The plain controller is the opposite case and keeps its label: GuiMachineController:49-50 *does*
        // call super.drawGuiContainerForegroundLayer(...). The 1.20.1 analogue of that inherited method is
        // AbstractContainerScreen#renderLabels, which draws *both* the title and the label (two drawString
        // calls reading title/titleLabelX/titleLabelY and playerInventoryTitle/inventoryLabelX/inventoryLabelY,
        // read out of the mapped 1.20.1 class by section V of the M6c harness); this override simply never
        // calls it, and reads none of those fields.
        //
        // 0.24.3 kept the inherited AbstractContainerScreen implementation and so painted 「物品栏」 at panel
        // (8, 119) — the far left of a 280-wide panel, over the recipe queue, exactly as in the screenshot.
        //
        // This is the foreground pass on purpose, and that is the whole of the 0.24.5 fix. Because the pose is
        // already translated by (leftPos, topPos) when renderLabels runs, the block can be positioned by the
        // panel-local textBlockRect() alone: the panel origin is *inherited*, not re-typed, so the block cannot
        // drift off the panel.
        //
        // The defect, read out of the three release jars' bytecode: 0.24.0, 0.24.1 and 0.24.2 all called
        // drawFactoryStatus from renderBg — as this screen still does for the queue — and translated by
        // `leftPos + 81.36F, topPos + 8.64F`, so the block was 1:1 on the panel and visible. 0.24.3 replaced
        // those two literals with `textBlockRect()[0]` and `[1]` (so section U's pose check could read the
        // rectangle rather than a copy of it) and **dropped the `+ leftPos` / `+ topPos` in the same edit**.
        // From then on the translate was panel-local in a pass that has no panel origin, so the whole block was
        // painted at absolute (81, 8) — outside the clip rectangle drawFactoryStatus installs
        // (leftPos..leftPos+280 × topPos..topPos+213) — and was scissored away completely. 0.24.4 then emptied
        // renderLabels to get rid of the inherited title/label and left the block where 0.24.3 had put it, which
        // is the owner's 「gui没啥问题了，但是没有文字，找到蓝图那两行」: the two lines were never deleted, they
        // were drawn 80-odd pixels up and to the left of the panel, behind the clip.
        //
        // The queue's row text is unaffected because drawRecipeStatus does add leftPos/topPos itself
        // (:353-355) — it is the one text block in this screen that was already origin-correct.
        drawFactoryStatus(graphics);
    }

    // ------------------------------------------------------------------ the recipe queue

    /** {@code GuiFactoryController#drawRecipeQueue} ({@code :94-109}). */
    private void drawRecipeQueue(GuiGraphics graphics, List<FactoryThread> threads) {
        int offsetY = RECIPE_QUEUE_OFFSET_Y;
        int last = Math.min(MAX_PAGE_ELEMENTS, Math.max(0, threads.size() - this.scroll));
        for (int i = 0; i < last; i++) {
            drawRecipeInfo(graphics, threads.get(this.scroll + i), offsetY);
            offsetY += FACTORY_ELEMENT_HEIGHT + 1;
        }
    }

    /**
     * The queue element blit's four arguments from the element sheet, as {@code {srcU, srcV, width, height}}.
     *
     * <p>Extracted for the same reason {@link #panelBlit} was: the harness asserts the arguments themselves
     * rather than a position derived from them. The original read the element from its own sheet's
     * {@code (0, 0)} at 86×32 ({@code GuiFactoryController:123}).
     */
    public static int[] elementBlit() {
        return new int[] {0, 0, FACTORY_ELEMENT_WIDTH, FACTORY_ELEMENT_HEIGHT};
    }

    /** {@code GuiFactoryController#drawRecipeInfo} ({@code :111-139}). */
    private void drawRecipeInfo(GuiGraphics graphics, FactoryThread thread, int offsetY) {
        int x = this.leftPos + RECIPE_QUEUE_OFFSET_X;
        int y = this.topPos + offsetY;
        int[] element = elementBlit();

        // (1) the element itself, tinted by "is this a core thread" — the original's first setShaderColor.
        RenderSystem.setShaderColor(thread.isCoreThread() ? 0.7F : 1.0F, thread.isCoreThread() ? 0.9F : 1.0F,
                1.0F, 1.0F);
        graphics.blit(TEXTURE_ELEMENTS, x, y, element[0], element[1], element[2], element[3]);

        // (2) the same sprite again, tinted by status, then cropped to the progress.
        //
        // The 1.12.2 original blended this quad into the one above at the same alpha, which is what gives a
        // working thread its green element and an idle one its red. Cropping the destination rather than the
        // source shows the identical pixels of a source-sized-at-86x32 sprite.
        RenderSystem.setShaderColor(thread.isWorking() ? 0.6F : 1.0F, thread.isWorking() ? 1.0F : 0.6F,
                thread.isWorking() ? 0.75F : 0.6F, 1.0F);
        int width = thread.isWorking() ? (int) (FACTORY_ELEMENT_WIDTH * thread.fraction())
                : FACTORY_ELEMENT_WIDTH;
        if (width > 0) {
            graphics.blit(TEXTURE_ELEMENTS, x, y, 0, 0, width, FACTORY_ELEMENT_HEIGHT);
        }

        drawRecipeStatus(graphics, thread, offsetY + ROW_TEXT_Y);
    }

    /** {@code GuiFactoryController#drawRecipeStatus} ({@code :141-187}). */
    private void drawRecipeStatus(GuiGraphics graphics, FactoryThread thread, int panelY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0F);

        int x = ROW_LABEL_X;
        int y = (int) (panelY / FONT_SCALE);

        String label = threadLabel(thread);
        if (thread.isWorking() && thread.parallelism() > 1) {
            // The original appended the parallelism in the same parentheses form (:162-170).
            label = label + " (" + Component.translatable(CONTROLLER_KEY + "parallelism",
                    thread.parallelism()).getString() + ")";
        }
        graphics.drawString(this.font, label, x, y, ROW_TEXT, false);
        y += 12;

        ControllerStatus status = thread.isWorking() ? ControllerStatus.CRAFTING : ControllerStatus.IDLE;
        for (FormattedCharSequence line : this.font.split(
                Component.translatable(status.translationKey()), ROW_WRAP_WIDTH)) {
            graphics.drawString(this.font, line, x, y, ROW_TEXT, false);
            y += LINE;
        }

        if (thread.isWorking() && thread.duration() > 0) {
            int percent = thread.progress() * 100 / thread.duration();
            graphics.drawString(this.font, Component.translatable(
                    CONTROLLER_KEY + "status.crafting.progress", percent + "%"), x, y, ROW_TEXT, false);
        }

        graphics.pose().popPose();
    }

    /**
     * The row's label: a core thread prints its declared name (translated when the name happens to be a key, the
     * original's {@code I18n.hasKey(name)} test), an ordinary one prints {@code gui.factory.thread} with its
     * number. The number is the thread's own stable one, not its position on this page.
     */
    private String threadLabel(FactoryThread thread) {
        if (thread.isCoreThread()) {
            String name = thread.threadName();
            return name == null ? "" : name;
        }
        return Component.translatable(KEY + "thread", thread.threadNumber()).getString();
    }

    // ------------------------------------------------------------------ the information block

    /**
     * The rows the information block draws, in the original's own order and with the original's own gaps
     * ({@code GuiFactoryController:189-246}).
     *
     * <p>Row for row, the order is: the blueprint heading and its name (or the "none" line), the structure
     * heading and its name, then — only once the structure is formed — the status heading and its value, the
     * thread count, the parallelism pair, and the closing line. The last entry is a zero-height cursor row, so
     * the list's length is the number of rows the cursor <i>visits</i> rather than the number of glyphs, which
     * is what lets the drawing loop and this description agree line for line.
     *
     * <p>This is the only description of the block's content; {@link #drawFactoryStatus} draws from it and
     * section U of the M6c harness measures it.
     */
    public static List<ScreenLayout.Measured> orderedInfoRows(boolean pRedstone, boolean pFormed, boolean pBlueprint,
            int pBlueprintLines, int pStructureLines, int pStatusLines, int pThreadsLines,
            int pParallelismLines, int pSpacer) {
        List<ScreenLayout.Measured> rows = new ArrayList<>();
        if (pRedstone) {
            rows.add(line(0));
            return rows;
        }
        if (pBlueprint) {
            rows.addAll(walk(1 + pBlueprintLines, 0, BLOCK_GAP));
        } else if (!pFormed) {
            rows.add(line(BLOCK_GAP));
        }
        rows.addAll(walk(pStructureLines, 0, 0));
        if (!pFormed) {
            return rows;
        }
        rows.addAll(walk(1 + pStatusLines, 0, BLOCK_GAP));
        rows.addAll(walk(pThreadsLines, 0, 0));
        rows.addAll(walk(pParallelismLines, 0, 0));
        if (pThreadsLines + pParallelismLines > 0) {
            rows.add(line(pSpacer));
        }
        return rows;
    }

    /**
     * {@code count} rows of height {@link #LINE}, the first {@code leadIn} of them without a gap, the gap
     * {@code gapPanelPixels} attached to the last one. A blank gap line is expressed with {@code count} = 0.
     */
    private static List<ScreenLayout.Measured> walk(int count, int leadIn, int gapPanelPixels) {
        List<ScreenLayout.Measured> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(line(i == count - 1 ? gapPanelPixels : leadIn));
        }
        return rows;
    }

    /** One drawn line with {@code gap} panel pixels of blank space after it, in this screen's text space. */
    private static ScreenLayout.Measured line(int gapPanelPixels) {
        return ScreenLayout.FactoryPanel.line(gapPanelPixels);
    }

    /** {@code GuiFactoryController#drawFactoryStatus} ({@code :189-246}), now bounded to its region. */
    private void drawFactoryStatus(GuiGraphics graphics) {
        MachineControllerBlockEntity machine = this.menu.machine();
        int structureLines = 0;
        if (machine != null && machine.machineName() != null) {
            for (FormattedCharSequence ignored : wrap(machine.machineName())) {
                structureLines++;
            }
        } else if (machine != null) {
            structureLines = 1;
        }
        int blueprintLines = 1;
        int statusLines = wrap(Component.translatable(this.menu.status().translationKey())).size();
        int threadsLines = this.menu.maxThreads() > 0 ? 1 : 0;
        int parallelismLines = this.menu.maxParallelism() > 1 && parallelism() > 1 ? 2 : 0;
        boolean hasBlueprint = machine != null && blueprintLabel(machine) != null;
        boolean formed = this.menu.formedValue();
        boolean redstone = this.menu.redstoneStopped();

        List<ScreenLayout.Measured> rows = orderedInfoRows(redstone, formed, hasBlueprint, blueprintLines,
                structureLines, statusLines, threadsLines, parallelismLines, 0);
        ScreenLayout.Block block = ScreenLayout.FactoryPanel.block(rows);

        // The panel's own rectangle in absolute GUI coordinates, taken from panelRect() rather than typed out
        // again here: enableScissor does not consult the pose stack, so these are leftPos/topPos plus the
        // panel-local rectangle the blit above and section U of the M6c harness use.
        int[] panel = ScreenLayout.FactoryPanel.scissorRect();
        graphics.enableScissor(this.leftPos + panel[0], this.topPos + panel[1],
                this.leftPos + panel[2], this.topPos + panel[3]);

        graphics.pose().pushPose();
        // textBlockRect() is the block's panel-local origin, and it is the same rectangle the clip above was
        // derived from and that section U of the M6c harness checks. The translate consumes it; the scale then
        // makes the block's own scaled y units line up with it. It is panel-local on purpose and is NOT added to
        // leftPos/topPos here: this method runs in the foreground pass, whose pose already carries the panel
        // origin (see renderLabels). Adding the origin a second time would put the block 80-odd pixels past the
        // panel's right edge and the clip would hide it — which is the mirror image of the 0.24.3 defect.
        int[] text = textBlockRect();
        graphics.pose().translate(text[0], text[1], 0.0F);
        graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0F);

        Counter counter = new Counter(block.drawnLines);
        int x = 0;
        int y = 0;

        if (redstone) {
            for (FormattedCharSequence line : wrap(Component.translatable(
                    CONTROLLER_KEY + "status.redstone_stopped"))) {
                if (!counter.take()) {
                    break;
                }
                graphics.drawString(this.font, line, x, y, WHITE, true);
                y += LINE;
            }
            graphics.pose().popPose();
            graphics.disableScissor();
            return;
        }

        Component blueprint = machine == null ? null : blueprintLabel(machine);
        if (blueprint != null) {
            boolean heading = counter.index() == 0;
            counter.take();
            if (heading) {
                graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "blueprint", ""), x, y,
                        WHITE, true);
            }
            for (FormattedCharSequence line : wrap(blueprint)) {
                if (!counter.take()) {
                    break;
                }
                y += LINE;
                graphics.drawString(this.font, line, x, y, WHITE, true);
            }
            y += BLOCK_GAP;
        } else if (!formed) {
            if (counter.take()) {
                graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "blueprint",
                        Component.translatable(CONTROLLER_KEY + "blueprint.none")), x, y, WHITE, true);
            }
            y += BLOCK_GAP;
        }

        Component found = machine == null ? null : machine.machineName();
        if (found != null) {
            boolean heading = counter.index() == 0;
            counter.take();
            if (heading) {
                graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "structure", ""), x, y,
                        WHITE, true);
            }
            for (FormattedCharSequence line : wrap(found)) {
                if (!counter.take()) {
                    break;
                }
                y += LINE;
                graphics.drawString(this.font, line, x, y, WHITE, true);
            }
        } else if (counter.take()) {
            graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "structure",
                    Component.translatable(CONTROLLER_KEY + "structure.none")), x, y, WHITE, true);
        }

        if (!formed) {
            graphics.pose().popPose();
            graphics.disableScissor();
            return;
        }
        y += 15;

        // The controller's own status — see divergence 1 in the class comment for the one gate that differs.
        if (counter.take()) {
            graphics.drawString(this.font, Component.translatable(MachineControllerMenu.STATUS_KEY), x, y, WHITE,
                    true);
        }
        for (FormattedCharSequence line : wrap(Component.translatable(this.menu.status().translationKey()))) {
            if (!counter.take()) {
                break;
            }
            y += LINE;
            graphics.drawString(this.font, line, x, y, WHITE, true);
        }
        y += BLOCK_GAP;

        int threadsY = y;
        y = drawThreadsLine(graphics, x, y, counter);
        y = drawParallelismLines(graphics, x, y, counter);
        if (threadsY != y) {
            // The original's `tmp != offsetY` spacer: it only appears when one of those two blocks drew (:227-232).
            y += SPACER_GAP;
        }

        // The original drew this after the last line (:237-242). It is now positioned by the layout, so a tall
        // payload moves it up instead of pushing it onto the player grid or off the panel.
        graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "footer",
                this.menu.usedTimeAvg(), formatSearchMillis(this.menu.searchUsedTimeAvg()), workMode()), x,
                Math.min(y, block.footerY), WHITE, true);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    /**
     * The block's row budget: one counter shared by every drawing step, so the number of rows painted cannot
     * exceed the number the layout measured.
     */
    private static final class Counter {
        private final int budget;
        private int index;

        Counter(int budget) {
            this.budget = budget;
        }

        int index() {
            return this.index;
        }

        boolean take() {
            if (this.index >= this.budget) {
                return false;
            }
            this.index++;
            return true;
        }
    }

    /** {@code GuiFactoryController#drawFactoryThreadsInfo} ({@code :267-275}). */
    private int drawThreadsLine(GuiGraphics graphics, int x, int y, Counter counter) {
        if (this.menu.maxThreads() <= 0) {
            return y;
        }
        if (counter.take()) {
            graphics.drawString(this.font, Component.translatable(KEY + "threads",
                    this.menu.ordinaryThreads(), this.menu.maxThreads()), x, y, WHITE, true);
        }
        return y + LINE;
    }

    /** {@code GuiFactoryController#drawParallelismInfo} ({@code :277-311}). */
    private int drawParallelismLines(GuiGraphics graphics, int x, int y, Counter counter) {
        int maximum = this.menu.maxParallelism();
        if (maximum <= 1) {
            return y;
        }
        int parallelism = parallelism();
        if (parallelism <= 1) {
            return y;
        }
        if (counter.take()) {
            graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "parallelism", parallelism), x,
                    y, WHITE, true);
        }
        y += LINE;
        if (counter.take()) {
            graphics.drawString(this.font, Component.translatable(CONTROLLER_KEY + "max_parallelism", maximum), x,
                    y, WHITE, true);
        }
        return y + LINE;
    }

    /**
     * The sum the original computed on the client by walking both thread collections
     * ({@code :280-297}): one, plus every running thread's extra copies. Core and ordinary threads both count.
     */
    private int parallelism() {
        int parallelism = 1;
        for (FactoryThread thread : threads()) {
            if (thread.isWorking()) {
                parallelism += Math.max(0, thread.parallelism() - 1);
            }
        }
        return parallelism;
    }

    /**
     * The closing line's work mode. {@code SYNC} here for the reason the plain controller screen records: this
     * project's engine is synchronous by construction, and the original's three modes all computed the same
     * number (D15).
     */
    private String workMode() {
        MachineControllerBlockEntity machine = this.menu.machine();
        return machine == null ? "SYNC" : machine.timing().workMode().name();
    }

    /** {@code MiscUtils.formatFloat(searchUsedTimeCache / 1000F, 2)} ({@code :239}). */
    private static String formatSearchMillis(int micros) {
        return String.format(Locale.ROOT, "%.2f", micros / 1000.0F);
    }

    /**
     * The blueprint line's name, read from the client block entity — never recomputed from the machine registry,
     * which a dedicated-server client does not populate.
     */
    @Nullable
    private Component blueprintLabel(MachineControllerBlockEntity machine) {
        if (!this.menu.hasBlueprintMachine()) {
            return null;
        }
        Component name = machine.blueprintMachineName();
        if (name != null) {
            return name;
        }
        var id = machine.blueprintMachineId();
        return id != null ? Component.literal(id.toString())
                : Component.translatable(CONTROLLER_KEY + "blueprint.none");
    }

    private List<FormattedCharSequence> wrap(Component text) {
        return this.font.split(text, WRAP_WIDTH);
    }

    // ------------------------------------------------------------------ scrollbar

    /** {@code GuiScrollbar#draw} ({@code GuiScrollbar.java:21-31}), with the original's metrics. */
    private void drawScrollbar(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos + SCROLLBAR_LEFT;
        int y = this.topPos + SCROLLBAR_TOP;
        if (this.scrollRange == 0) {
            graphics.blit(SCROLLBAR_TEXTURE, x, y, SCROLLBAR_TRACK_U, SCROLLBAR_V, SCROLLBAR_WIDTH,
                    SCROLLBAR_THUMB_HEIGHT);
            return;
        }
        int offset = this.scroll * (SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT) / this.scrollRange;
        graphics.blit(SCROLLBAR_TEXTURE, x, y + offset, SCROLLBAR_THUMB_U, SCROLLBAR_V, SCROLLBAR_WIDTH,
                SCROLLBAR_THUMB_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // The original's handleMouseInput fed the wheel straight to the scrollbar with page size 1 (:126-133).
        if (delta != 0.0) {
            setScroll(this.scroll - (int) Math.signum(delta));
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overScrollbar(mouseX, mouseY) && this.scrollRange > 0) {
            // GuiScrollbar#click (:93-104): proportional, rounded — kept so the two implementations agree.
            int local = (int) (mouseY - (this.topPos + SCROLLBAR_TOP));
            int value = local * 2 * this.scrollRange / SCROLLBAR_HEIGHT;
            setScroll((value + 1) >> 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        double x = mouseX - (this.leftPos + SCROLLBAR_LEFT);
        double y = mouseY - (this.topPos + SCROLLBAR_TOP);
        return x > 0 && x <= SCROLLBAR_WIDTH && y > 0 && y <= SCROLLBAR_HEIGHT;
    }

    private void setScroll(int value) {
        this.scroll = Math.max(0, Math.min(this.scrollRange, value));
    }

    /** Every thread row the queue can show on one page, for the harness and for diagnostics. */
    public static int pageElements() {
        return MAX_PAGE_ELEMENTS;
    }

    /** The scrollbar's metrics, for the harness: {@code {left, top, height, width, thumbHeight}}. */
    public static int[] scrollbarMetrics() {
        return new int[] {SCROLLBAR_LEFT, SCROLLBAR_TOP, SCROLLBAR_HEIGHT, SCROLLBAR_WIDTH,
                SCROLLBAR_THUMB_HEIGHT};
    }

    /** The element sheet's per-row source size, for the harness: {@code {width, height}}. */
    public static int[] elementSize() {
        return new int[] {FACTORY_ELEMENT_WIDTH, FACTORY_ELEMENT_HEIGHT};
    }

    /** The panel size, for the harness — the original's constructor's own two numbers. */
    public static int[] panelSize() {
        return new int[] {280, 213};
    }

    /**
     * The information block's origin in <b>panel-local</b> pixels — the point {@code drawFactoryStatus}
     * translates its drawing matrix to. Section U checks that the block drawn here lies inside the clip
     * rectangle taken from {@link ScreenLayout.FactoryPanel#scissorRect()}, so a screen whose matrix is set up
     * anywhere else than the clip's own space fails the check instead of silently agreeing with a copied table.
     *
     * <p>Panel-local is only the right space because {@code drawFactoryStatus} is called from
     * {@link #renderLabels}, i.e. from the foreground pass whose pose {@code AbstractContainerScreen#render} has
     * already translated by {@code (leftPos, topPos)}. Section V reads that ordering out of the vanilla class's
     * own bytecode and requires the factory screen's call to sit on the foreground side of it, which is what
     * makes "panel-local + inherited origin" the one origin this screen uses.
     */
    public static int[] textBlockRect() {
        int left = (int) (TEXT_DRAW_OFFSET_X * FONT_SCALE);
        int top = (int) (TEXT_DRAW_OFFSET_Y * FONT_SCALE);
        int bottom = (int) (ScreenLayout.FactoryPanel.availableEnd() * FONT_SCALE);
        return new int[] {left, top, PANEL_WIDTH - left, bottom - top};
    }

    /**
     * The screen's fixed metric table, for the offline harness (which cannot open a window and must not be
     * satisfied with "a number appeared in a GUI"). Ordered and documented so the harness's indices are
     * readable:
     *
     * <pre>
     *  [0]  panel width                       280
     *  [1]  panel height                      213
     *  [2]  recipe element width              86
     *  [3]  recipe element height             32
     *  [4]  elements per page                 6
     *  [5]  recipe queue origin x             8
     *  [6]  recipe queue origin y             8
     *  [7]  scrollbar left                    94
     *  [8]  scrollbar top                     8
     *  [9]  scrollbar height                  197
     *  [10] text block origin x (panel px)     113 * 0.72
     *  [11] text block origin y (panel px)     12  * 0.72
     *  [12] information wrap width (scaled px) 135 / 0.72
     *  [13] font scale                         0.72
     *  [14] row label x (scaled px)            8 / 0.72 + 2
     *  [15] row wrap width (scaled px)         (86 - 6) / 0.72
     *  [16] row text y offset (panel px)       2
     *  [17] line pitch in the scaled block     10
     *  [18] spacer before the closing line     5
     *  [19] gap after a name/status block      15
     *  [20] row step (element height + 1)      33
     * </pre>
     */
    public static int[] metrics() {
        return new int[] {
                PANEL_WIDTH, PANEL_HEIGHT,
                FACTORY_ELEMENT_WIDTH, FACTORY_ELEMENT_HEIGHT, MAX_PAGE_ELEMENTS,
                RECIPE_QUEUE_OFFSET_X, RECIPE_QUEUE_OFFSET_Y,
                SCROLLBAR_LEFT, SCROLLBAR_TOP, SCROLLBAR_HEIGHT,
                (int) (TEXT_DRAW_OFFSET_X * FONT_SCALE), (int) (TEXT_DRAW_OFFSET_Y * FONT_SCALE),
                WRAP_WIDTH, (int) (FONT_SCALE * 100), ROW_LABEL_X, ROW_WRAP_WIDTH, ROW_TEXT_Y,
                LINE, SPACER_GAP, BLOCK_GAP, FACTORY_ELEMENT_HEIGHT + 1,
                // [21] and [22] are the text block's origin in *panel* pixels, which is what the queue area has
                // to be compared against: [10]/[11] are inside the scaled matrix and are not the same space.
                TEXT_DRAW_OFFSET_X, TEXT_DRAW_OFFSET_Y,
        };
    }
}
