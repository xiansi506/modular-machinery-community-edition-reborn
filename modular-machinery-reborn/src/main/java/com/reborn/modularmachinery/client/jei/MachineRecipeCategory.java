package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.magic.MagicRequirement;
import com.reborn.modularmachinery.recipe.CatalystRequirement;
import com.reborn.modularmachinery.recipe.FluidRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.IngredientArrayRequirement;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The JEI page of <b>one machine's</b> recipes, reproduced from the original's {@code CategoryDynamicRecipe} +
 * {@code DynamicRecipeWrapper} for the 1.20.1 JEI this mod ships against.
 *
 * <p>The single largest difference from 0.16.2 is the category's identity: the original registered one category
 * per machine, titled with the machine's localized name, sized to that machine's requirements
 * ({@code ModIntegrationJEI#registerCategories}: {@code for (DynamicMachine machine :
 * MachineRegistry.getRegistry()) { … registry.addRecipeCategories(new CategoryDynamicRecipe(machine)); }}, and
 * {@code CategoryDynamicRecipe#getTitle} returned {@code machine.getLocalizedName()}). This class is that
 * category, one instance per machine, and {@link ModularMachineryJeiPlugin} builds one per machine.
 *
 * <p><b>Why that is possible in JEI 15.49.</b> JEI does not register categories at plugin-discovery time. Its
 * {@code mezz.jei.forge.startup.StartEventObserver} starts the whole plugin pipeline from a
 * {@code ClientPlayerNetworkEvent.LoggingIn} — and, when {@code shouldWaitForRecipes()} is true, only after the
 * matching {@code RecipesUpdatedEvent} — by calling {@code JeiStarter#start()}, which is what runs
 * {@code PluginLoader.createRecipeCategories}. Both of those events are long after the client's resource reload,
 * so {@code MachineRegistry} is already filled by {@code MachineLoader} when this constructor runs. A client with
 * no machine definitions at all (a dedicated-server client, where the registry is empty) still gets a category
 * for every machine named by the synced recipes, titled with the registry name instead of a localized name.
 *
 * <p><b>What the original drew</b> (references are into
 * {@code _mmce-src/…/common/integration/recipe/} and {@code …/common/crafting/}):
 *
 * <ul>
 *   <li>{@code CategoryDynamicRecipe#buildRecipeComponents} laid every requirement out on one horizontal band:
 *       inputs on the left in descending {@code getComponentHorizontalSortingOrder()} (energy 1000, fluid 100,
 *       items 10), the process arrow, then outputs in ascending order. {@link MachineRecipeLayout} is that
 *       measurement, and it is per machine because {@code getWidth()}/{@code getHeight()} are.</li>
 *   <li>The page background was a <b>blank</b> drawable of exactly the measured size
 *       ({@code getBackground()} returned {@code jeiHelpers.getGuiHelper().createBlankDrawable(maxPoint.x,
 *       realHeight)}), so the art underneath was JEI's own recipe border — the
 *       {@code single_recipe_background.png} nine-slice, RGB(198,198,198) in <b>both</b> JEI 1.12.2 and JEI
 *       15.49, drawn at {@code (-4,-4,width+8,height+8)} by {@code RecipeLayout}. JEI 15.49 does the identical
 *       thing when {@code needsRecipeBorder()} is left at its {@code true} default, so this class overrides
 *       neither and the backdrop matches the original pixel for pixel.</li>
 *   <li>Every cell was the original's own art from {@code jeirecipeicons_ce.png}: an 18x18 opaque cell at
 *       {@code (54,0)} for both items and fluid tanks (the original's {@code PART_INVENTORY_CELL} and
 *       {@code PART_TANK_SHELL_BACKGROUND} are the same region), the transparent {@code (0,0)} tank shell as the
 *       fluid renderer's overlay, and an 18x54 energy column at {@code (36,0)} with the filled {@code (18,0)}
 *       column over it. See {@link MachineRecipeAtlas}.</li>
 *   <li>The process arrow was {@code (72,0,22,15)} with the {@code (72,15,22,15)} overlay revealed left to right,
 *       {@code ceil((tick + partialTick) / duration * 22)} pixels wide.</li>
 *   <li>{@code DynamicRecipeWrapper#drawInfo} wrote the requirement tips underneath the band at x=4 in white with
 *       a drop shadow, bottom-aligned against the measured height.</li>
 *   <li>The processing time was <b>not</b> a fixed label: {@code DynamicRecipeWrapper#getTooltipStrings}
 *       returned the two duration strings only while the mouse was inside
 *       {@code rectangleProcessArrow} — the 22x15 arrow rectangle.</li>
 *   <li>Chance and amount ranges lived in the slot tooltips
 *       ({@code JEIComponentItem#onJEIHoverTooltip}), never on the page itself.</li>
 * </ul>
 *
 * <p><b>Remaining divergences.</b>
 *
 * <ol>
 *   <li>The quantity on a slot is JEI's own stack-count overlay, because this mod shows an input's amount by
 *       sizing the ingredient stack to it. The original instead rendered the item bare and painted the number
 *       itself ({@code IngredientItemStackRenderer#renderRequirementOverlyIntoGUI}), which abbreviated anything
 *       from 1000 up to {@code 1K} at half scale and drew {@code min~max} for a range. For every amount below
 *       1000 — including the eight 泰拉钢锭 of the recipe this was reported against — the two are identical.</li>
 *   <li>Fluid amounts are not painted on the tank. The original's {@code FluidStackRenderer(1, false, 18, 18,
 *       PART_TANK_SHELL)} drew the fluid and the shell and nothing else, and JEI 1.20.1's fluid tooltip still
 *       carries the exact "1000 mB" reading, so nothing is lost — but 0.16.2 overlaid the number, and that
 *       overlay is gone.</li>
 *   <li>No fuel / smart-interface / recipe-tooltip rows: this mod's recipe model has no such fields. The energy
 *       rows are the original's own two, and they are the only ones its data can produce.</li>
 *   <li>Chance stays tooltip-only, exactly as in the original, so a 25% side output is only visible on hover.</li>
 *   <li>Gas (chemical) cells are <b>text cells</b> (E10, 0.100.0): the original rendered the gas sprite through
 *       Mekanism's own {@code GasStackRenderer} inside a gas tank shell ({@code JEIComponentGas} →
 *       {@code RecipeLayoutPart.GasTank}); this mod's base jar cannot reference Mekanism client classes (the X4
 *       isolation contract), so the cell draws {@code requirement.describe()} instead, half-scale, wrapped and
 *       clipped to the cell. The full line stays readable on hover via the category tooltip.</li>
 * </ol>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    /** The x the original wrote its tip block at: {@code fr.drawStringWithShadow(tip, 4, offsetY, 0xFFFFFF)}. */
    private static final int TEXT_X = 4;

    /**
     * White with a drop shadow, exactly as {@code DynamicRecipeWrapper#drawInfo} drew it. This is not an
     * approximation of the original's colours on a different backdrop: JEI 1.12.2's and JEI 15.49's
     * {@code single_recipe_background.png} are both RGB(198,198,198) in the centre, so the original's white
     * shadowed text sat on this very panel.
     */
    private static final int TEXT_COLOR = 0xFFFFFF;

    /**
     * 气体文本格的字号（E10，0.100.0）：18×18 的格子里只有半倍字高才装得下
     * 「1000 mB 氢氟酸」这类一句话；tip 块与悬停仍是全尺寸文本。
     */
    private static final float GAS_TEXT_SCALE = 0.5F;

    private final IGuiHelper helper;
    private final ResourceLocation machineId;
    private final Component title;
    private final IDrawable icon;
    private final List<MachineRecipe> recipes;
    private MachineRecipeLayout layout;
    private IDrawable background;

    /**
     * @param machineId the machine this category belongs to; it is also the category's recipe type id
     * @param machine   the loaded definition, or {@code null} on a client that has none (the recipe-synced
     *                  machine names still get a category, with the registry name as their title)
     * @param recipes   every recipe of this machine JEI is about to be given, which fixes the page size
     */
    public MachineRecipeCategory(IGuiHelper helper, ResourceLocation machineId, @Nullable MachineDefinition machine,
                                 List<MachineRecipe> recipes) {
        this.helper = helper;
        this.machineId = machineId;
        // The original's title was machine.getLocalizedName(); MachineDefinition#displayName is its counterpart,
        // and MachineLoader falls back to the registry path when a definition carries no 'localizedname'.
        this.title = machine != null ? machine.displayName() : Component.literal(machineId.getPath());
        Item controller = ModBlocks.controllerItemFor(machineId);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(controller));
        this.recipes = List.copyOf(recipes);
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return ModularMachineryJeiPlugin.typeFor(this.machineId);
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    /** The original's blank drawable, sized to this machine's own measured layout. */
    @Override
    public IDrawable getBackground() {
        MachineRecipeLayout measured = layout();
        if (this.background == null
                || this.background.getWidth() != measured.width()
                || this.background.getHeight() != measured.height()) {
            this.background = this.helper.createBlankDrawable(measured.width(), measured.height());
        }
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return layout().width();
    }

    @Override
    public int getHeight() {
        return layout().height();
    }

    /**
     * {@code IRecipeCategory#getWidth}/{@code getHeight} are fixed when JEI asks for them, so the page is
     * measured once, lazily, from every recipe this machine has — the same pass
     * {@code CategoryDynamicRecipe}'s constructor made over {@code RecipeRegistry.getRecipesFor(machine)}.
     * Measuring lazily also means the client's font and level are certainly up.
     */
    private MachineRecipeLayout layout() {
        if (this.layout == null) {
            this.layout = MachineRecipeLayout.measure(this.recipes, Minecraft.getInstance().font);
        }
        return this.layout;
    }

    // ------------------------------------------------------------------ slots

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        MachineRecipeLayout measured = layout();
        addItemSlots(builder, recipe, measured.inputItems(), RecipeIngredientRole.INPUT);
        addFluidSlots(builder, recipe, measured.inputFluids(), RecipeIngredientRole.INPUT);
        addItemSlots(builder, recipe, measured.outputItems(), RecipeIngredientRole.OUTPUT);
        addFluidSlots(builder, recipe, measured.outputFluids(), RecipeIngredientRole.OUTPUT);
    }

    /**
     * Item cells.
     *
     * <p>Geometry: the original's {@code RecipeLayoutPart.Item} is an 18x18 cell at {@code (x, y)} with the item
     * rendered at {@code (x+1, y+1)} ({@code getRendererPaddingX()/Y() == 1}). JEI's slot coordinate is the top
     * left of the <i>ingredient</i>, and a slot background given the offset {@code (-1,-1)} is drawn at
     * {@code (x-1, y-1)} — which is exactly what {@code setStandardSlotBackground()} does. So the slot goes at
     * the item position and the cell sprite one pixel up and left of it, and the pair lands on the original's
     * pixels.
     *
     * <p>Quantities: an input's stack is sized to the requirement's amount, which is what makes JEI draw the
     * count. An output's stack already carries {@code maxAmount}, and is never copied down to 1. Both follow the
     * original's {@code RequirementItem#asJEIIORequirementList}.
     */
    private static void addItemSlots(IRecipeLayoutBuilder builder, MachineRecipe recipe,
                                     List<MachineRecipeLayout.Cell> cells, RecipeIngredientRole role) {
        boolean output = role == RecipeIngredientRole.OUTPUT;
        List<MachineRequirement> requirements =
                MachineRecipeLayout.components(recipe, ioTypeOf(role), MachineRecipeLayout.Kind.ITEM);
        int count = Math.min(requirements.size(), cells.size());
        for (int i = 0; i < count; i++) {
            MachineRequirement requirement = requirements.get(i);
            // 「物品格」现在是一个**明确**的桶，不再是「其余都算物品」的默认桶。
            //
            // 这条守卫是一个真缺陷的修复：原来气体需求（GasRequirement）在 MachineRecipeLayout.kindOf
            // 里掉进物品桶，随后在下面被 cast 成 ItemRequirement —— 打开那个机器的 JEI 页就崩。
            // 现在它自己声明 tankKind() = GAS，于是根本不会走到这里；这条守卫是第二道保险。
            if (requirement.tankKind() != MachineRequirement.TankKind.NONE) {
                continue;
            }
            // An ingredient array is a group of alternatives and occupies one cell, showing each alternative.
            // It is not an ItemRequirement, which is why this dispatch exists: casting it would crash the page.
            List<ItemStack> stacks;
            float chance;
            boolean amountRange = false;
            CatalystRequirement catalyst = null;
            if (requirement instanceof CatalystRequirement catalystRequirement) {
                // A catalyst is an input group exactly like an ingredient array, plus its own effect lines. It is
                // not an ItemRequirement, so without this branch the cast below would crash the page (the same
                // shape as the gas crash guarded above).
                catalyst = catalystRequirement;
                stacks = catalystRequirement.entryStacksFlat();
                chance = catalystRequirement.chance();
            } else if (requirement instanceof IngredientArrayRequirement array) {
                stacks = array.entryStacksFlat();
                chance = array.chance();
            } else {
                ItemRequirement item = (ItemRequirement) requirement;
                stacks = output ? List.of(item.previewOutputStack()) : inputStacks(item);
                chance = item.chance();
                amountRange = true;
            }
            if (stacks.isEmpty() || stacks.get(0).isEmpty()) {
                continue;
            }
            MachineRecipeLayout.Cell cell = cells.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(role, cell.x() + 1, cell.y() + 1)
                    .setBackground(MachineRecipeAtlas.CELL, -1, -1);
            if (output) {
                slot.addItemStack(stacks.get(0));
            } else {
                // Every alternative goes into the one slot, which is what the original's
                // RequirementIngredientArray#asJEIIORequirementList produced for a cell.
                slot.addItemStacks(stacks);
            }
            ItemRequirement itemForRange = amountRange ? (ItemRequirement) requirement : null;
            ItemRequirement finalItemForRange = itemForRange;
            CatalystRequirement finalCatalyst = catalyst;
            slot.addRichTooltipCallback((view, tooltip) -> {
                MachineRecipeText.addScriptTooltip(tooltip, requirement);
                MachineRecipeText.addChanceTooltip(!output, tooltip, chance);
                if (finalItemForRange != null) {
                    MachineRecipeText.addAmountRangeTooltip(!output, tooltip, finalItemForRange);
                } else {
                    MachineRecipeText.addIngredientArrayTooltip(tooltip);
                    if (finalCatalyst != null) {
                        MachineRecipeText.addCatalystTooltip(tooltip, finalCatalyst.tooltips());
                    }
                }
            });
        }
    }

    /**
     * Fluid cells: JEI's fluid tank inside the original's cell sprite, with the original's tank shell over it.
     *
     * <p>{@code PART_TANK_SHELL_BACKGROUND} and {@code PART_INVENTORY_CELL} are the same region of the sheet, so
     * the background offset is {@code (0,0)} rather than the item cells' {@code (-1,-1)}: the original rendered
     * the fluid across the whole 18x18 cell ({@code FluidStackRenderer(1, false, 18, 18, …)} with renderer
     * padding 0), not in a 16x16 inset. The shell was that renderer's <i>overlay</i>, drawn after the fluid, and
     * JEI 1.20.1 draws a slot's overlay after its ingredient too, so {@code setOverlay} reproduces it — and it
     * is the only overlay this slot can have, which is why the fluid amount is tooltip-only here.
     */
    private static void addFluidSlots(IRecipeLayoutBuilder builder, MachineRecipe recipe,
                                      List<MachineRecipeLayout.Cell> cells, RecipeIngredientRole role) {
        List<MachineRequirement> requirements =
                MachineRecipeLayout.components(recipe, ioTypeOf(role), MachineRecipeLayout.Kind.FLUID);
        int count = Math.min(requirements.size(), cells.size());
        for (int i = 0; i < count; i++) {
            MachineRequirement requirement = requirements.get(i);
            MachineRecipeLayout.Cell cell = cells.get(i);
            // Chemical visuals arrive as atlas metadata, without optional client API linkage.
            if (requirement.tankKind() == MachineRequirement.TankKind.MAGIC) {
                builder.addSlot(role, cell.x(), cell.y()).setBackground(new MagicTankDrawable(requirement), 0, 0);
                continue;
            }
            if (requirement.tankKind() == MachineRequirement.TankKind.GAS) {
                addGasSlot(builder, role, cell, requirement);
                continue;
            }
            if (requirement.tankKind() != MachineRequirement.TankKind.FLUID) {
                continue;
            }
            FluidRequirement fluid = (FluidRequirement) requirement;
            int amount = fluid.amount();
            IRecipeSlotBuilder slot = builder.addSlot(role, cell.x(), cell.y())
                    .setBackground(MachineRecipeAtlas.CELL, 0, 0)
                    // The capacity is the requirement itself and showCapacity is false, so the tank draws full
                    // and the tooltip reads "1000 mB" — the same picture as the original's capacity of 1.
                    .setFluidRenderer(amount, false, MachineRecipeLayout.CELL, MachineRecipeLayout.CELL)
                    .addFluidStack(fluid.fluidType().getFluid(), amount);
            slot.setOverlay(MachineRecipeAtlas.TANK_SHELL, 0, 0);
            slot.addRichTooltipCallback((view, tooltip) -> {
                MachineRecipeText.addScriptTooltip(tooltip, requirement);
                MachineRecipeText.addChanceTooltip(role != RecipeIngredientRole.OUTPUT, tooltip, fluid.chance());
            });
        }
    }

    /** Gas sprite, tint and shell in the JEI slot pass; tooltip also works without an ingredient type. */
    private static void addGasSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role,
                                   MachineRecipeLayout.Cell cell, MachineRequirement requirement) {
        String text = requirement.describe();
        if (text == null || text.isEmpty()) {
            return;
        }
        IRecipeSlotBuilder slot = builder.addSlot(role, cell.x(), cell.y())
                .setBackground(new GasTankDrawable(requirement), 0, 0);
        // 槽位级 tooltip 保留（若 JEI 为空槽触发回调则与类别级重复一条，无害）；全文显示由
        // getTooltip 的气体格兜底保证——那条路径不依赖 JEI 对空槽的行为，离线也证不了它。
        slot.addRichTooltipCallback((view, tooltip) -> {
            tooltip.add(net.minecraft.network.chat.Component.literal(text));
            MachineRecipeText.addScriptTooltip(tooltip, requirement);
            MachineRecipeText.addChanceTooltip(role != RecipeIngredientRole.OUTPUT, tooltip, chanceOf(requirement));
        });
    }

    /** 需求自报的概率；没有这个概念时返回 1（不影响 tooltip 的既有行为）。 */
    private static float chanceOf(MachineRequirement requirement) {
        if (requirement instanceof FluidRequirement fluid) {
            return fluid.chance();
        }
        if (requirement instanceof ItemRequirement item) {
            return item.chance();
        }
        // 兼容层的需求（含气体）不住在主模组里，所以这里不去 instanceof 它们：
        // 概率已经包含在 describe() 的文字里（GasRequirement.describe 会写出百分比）。
        return 1.0F;
    }

    /**
     * The amount-carrying item stacks of an input requirement.
     *
     * <p>This is where the page used to lose its numbers: an {@link Ingredient} has no count, so handing it
     * straight to JEI showed every input as a single item. {@code RequirementItem#asJEIIORequirementList} did
     * the equivalent in the original — it copied each matching stack and set its count to the requirement's
     * amount.
     */
    private static List<ItemStack> inputStacks(ItemRequirement item) {
        Ingredient ingredient = item.ingredient();
        if (ingredient == null) {
            return List.of();
        }
        ItemStack[] matches = ingredient.getItems();
        int amount = item.amount();
        CompoundTag previewTag = item.previewTag();
        List<ItemStack> stacks = new ArrayList<>(matches.length);
        for (ItemStack match : matches) {
            if (!match.isEmpty()) {
                ItemStack preview = match.copyWithCount(amount);
                if (previewTag != null) {
                    preview.setTag(previewTag.copy());
                }
                stacks.add(preview);
            }
        }
        return stacks;
    }

    private static IOType ioTypeOf(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.OUTPUT ? IOType.OUTPUT : IOType.INPUT;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * {@code CategoryDynamicRecipe#drawExtras} plus {@code DynamicRecipeWrapper#drawInfo}, in their own order:
     * the static arrow and every part's background first, then the animated arrow over it and the tip block.
     */
    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        MachineRecipeLayout measured = layout();

        MachineRecipeAtlas.ARROW.draw(graphics, measured.arrowX(), measured.arrowY());

        // One energy part per energy requirement, and every one of them drew its side's summed rate
        // (drawInfo: inputComponents.stream().filter(Energy.class::isInstance).forEach(part -> part.drawEnergy(mc,
        // finalTotalEnergyIn))). The positive check goes through the exact sum so a legal total above
        // Long.MAX_VALUE still reads as "has energy demand" instead of wrapping negative.
        boolean energyIn = MachineRecipeText.energyPerTickExact(recipe, IOType.INPUT).signum() > 0;
        boolean energyOut = MachineRecipeText.energyPerTickExact(recipe, IOType.OUTPUT).signum() > 0;
        for (MachineRecipeLayout.Cell cell : measured.inputEnergy()) {
            drawEnergyCell(graphics, cell, energyIn);
        }
        for (MachineRecipeLayout.Cell cell : measured.outputEnergy()) {
            drawEnergyCell(graphics, cell, energyOut);
        }

        drawAuraCells(graphics, recipe, IOType.INPUT, measured.inputAura());
        drawAuraCells(graphics, recipe, IOType.OUTPUT, measured.outputAura());

        int revealed = revealedArrowWidth(recipe);
        if (revealed > 0) {
            MachineRecipeAtlas.ARROW_ACTIVE.drawPartial(graphics, measured.arrowX(), measured.arrowY(), revealed);
        }

        drawTips(graphics, recipe, measured.height());
    }

    private static void drawAuraCells(GuiGraphics graphics, MachineRecipe recipe, IOType side,
                                      List<MachineRecipeLayout.Cell> cells) {
        var requirements = MachineRecipeLayout.components(recipe, side, MachineRecipeLayout.Kind.AURA);
        for (int i = 0; i < Math.min(requirements.size(), cells.size()); i++) {
            var cell = cells.get(i);
            new AuraBarDrawable((MagicRequirement) requirements.get(i), recipe.recipeTime())
                    .draw(graphics, cell.x(), cell.y() + AuraBarDrawable.PADDING_Y);
        }
    }

    /**
     * 把一句话画进 18×18 的格子里：按半倍字高缩放，先在未缩放的文本空间折行（0.5 缩放下
     * 格内 16px ≈ 文本空间 32px 宽），超过格高的行数直接不画（scissor 兜底），整体水平垂直居中。
     * 颜色与 tip 块一致（白字带阴影，背景同为 JEI 灰面板）。
     *
     * <p><b>裁剪必须换算到绝对 GUI 坐标。</b>JEI 在画类别与槽位前会把 pose 平移到配方原点
     * （{@code RecipeLayout#drawRecipe} 的 {@code translate(area.x, area.y, 0)}），而
     * {@code GuiGraphics#enableScissor} 不读取 pose——它直接接收屏幕像素。宿主平移 (120,70) 时，
     * 文字跟着 pose 走，裁剪框却留在格子的局部坐标上，于是文字被裁到页面左上角之外。
     * 这里在进入文字自身的 {@code translate/scale} <b>之前</b>，把格子的四角用当前 pose 转换一次，
     * 向下取整左/上、向上取整右/下，保证 0.5 倍缩放后的字形不会因取整被削掉一列像素。
     *
     * <p>支持边界：JEI 常规二维平移，以及轴向缩放（四角转换对轴对齐缩放是精确的）。任意旋转/透视宿主
     * 只会得到一个保守的外接矩形，不属于本方法验收范围；这里也不手工乘窗口 GUI 倍率，不取消外层裁剪。
     *
     * <p>异常边界（P10-15）：每一行 push 成功后用 finally 归还，开启的 scissor 在外层 finally 归还；
     * 原异常照常抛出，不静默吞掉，也不清理调用者自己的 pose/scissor 栈。
     */
    static void drawTextInCell(GuiGraphics graphics, MachineRecipeLayout.Cell cell, String text) {
        Font font = Minecraft.getInstance().font;
        int inner = MachineRecipeLayout.CELL - 2;
        int wrapWidth = (int) (inner / GAS_TEXT_SCALE);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), wrapWidth);
        float lineHeight = font.lineHeight * GAS_TEXT_SCALE;
        int visibleLines = Math.max(1, (int) (inner / lineHeight));
        int drawn = Math.min(lines.size(), visibleLines);
        if (drawn <= 0) {
            return;
        }
        float totalHeight = drawn * lineHeight;
        float y = cell.y() + (MachineRecipeLayout.CELL - totalHeight) / 2.0F;
        int[] clip = absoluteCellRect(graphics, cell.x(), cell.y(), MachineRecipeLayout.CELL);
        graphics.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        try {
            for (int i = 0; i < drawn; i++) {
                FormattedCharSequence line = lines.get(i);
                float x = cell.x() + (MachineRecipeLayout.CELL - font.width(line) * GAS_TEXT_SCALE) / 2.0F;
                PoseStack pose = graphics.pose();
                pose.pushPose();
                try {
                    pose.translate(x, y, 0);
                    pose.scale(GAS_TEXT_SCALE, GAS_TEXT_SCALE, 1.0F);
                    graphics.drawString(font, line, 0, 0, TEXT_COLOR, true);
                } finally {
                    pose.popPose();
                }
                y += lineHeight;
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * 把调用者坐标系里的矩形四角转换到绝对 GUI 坐标，并按像素覆盖范围向外取整。
     *
     * <p>左/上取 {@link Mth#floor}、右/下取 {@link Mth#ceil}：GUI 坐标是连续的，而 scissor 只接受整数像素；
     * 向外取整保证非整数平移（例如 10.4）下最后半个像素列不会被裁掉，代价是可能多覆盖不到一个像素。
     * 负坐标同样适用。矩阵是 2D 仿射时（平移/轴缩放）这是精确边界；含旋转时得到的是保守外接框。
     */
    private static int[] absoluteCellRect(GuiGraphics graphics, int x, int y, int size) {
        org.joml.Matrix4f matrix = graphics.pose().last().pose();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int cornerX : new int[]{x, x + size}) {
            for (int cornerY : new int[]{y, y + size}) {
                float tx = matrix.m00() * cornerX + matrix.m10() * cornerY + matrix.m30();
                float ty = matrix.m01() * cornerX + matrix.m11() * cornerY + matrix.m31();
                minX = Math.min(minX, tx);
                minY = Math.min(minY, ty);
                maxX = Math.max(maxX, tx);
                maxY = Math.max(maxY, ty);
            }
        }
        return new int[]{Mth.floor(minX), Mth.floor(minY), Mth.ceil(maxX), Mth.ceil(maxY)};
    }

    /**
     * {@code RecipeLayoutPart.Energy}: the 18x54 background always, the 18x54 filled column only while that
     * side's summed rate is positive.
     */
    private static void drawEnergyCell(GuiGraphics graphics, MachineRecipeLayout.Cell cell, boolean hasEnergyDemand) {
        MachineRecipeAtlas.ENERGY_EMPTY.draw(graphics, cell.x(), cell.y());
        if (hasEnergyDemand) {
            MachineRecipeAtlas.ENERGY_FILLED.draw(graphics, cell.x(), cell.y());
        }
    }

    /**
     * {@code DynamicRecipeWrapper#drawInfo}'s animation:
     * {@code pxPart = ceil((clientTick % duration + partialTick) / duration * PART_PROCESS_ARROW_ACTIVE.xSize)},
     * revealed left to right over the static arrow.
     */
    private static int revealedArrowWidth(MachineRecipe recipe) {
        int total = recipe.recipeTime();
        if (total <= 0) {
            return MachineRecipeLayout.ARROW_WIDTH;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int tick = minecraft.gui.getGuiTicks() % total;
        float progress = (tick + minecraft.getFrameTime()) / (float) total;
        return Mth.clamp(Mth.ceil(progress * MachineRecipeLayout.ARROW_WIDTH), 0, MachineRecipeLayout.ARROW_WIDTH);
    }

    /**
     * The tip block, bottom-aligned against the measured height exactly as {@code drawInfo} aligned it: it walks
     * backwards from {@code realHeight} subtracting one {@code LINE_HEIGHT} per line and one
     * {@code SPLIT_HEIGHT} per group, adds one split back, then draws forwards.
     */
    private static void drawTips(GuiGraphics graphics, MachineRecipe recipe, int contentHeight) {
        Font font = Minecraft.getInstance().font;
        List<List<String>> groups = MachineRecipeText.tipGroupsWithRecipeTooltip(recipe);

        int y = contentHeight;
        for (List<String> group : groups) {
            y -= MachineRecipeLayout.LINE_HEIGHT * group.size();
            y -= MachineRecipeLayout.SPLIT_HEIGHT;
        }
        y += MachineRecipeLayout.SPLIT_HEIGHT;

        for (List<String> group : groups) {
            for (String line : group) {
                graphics.drawString(font, line, TEXT_X, y, TEXT_COLOR, true);
                y += MachineRecipeLayout.LINE_HEIGHT;
            }
            y += MachineRecipeLayout.SPLIT_HEIGHT;
        }
    }

    /**
     * The original showed the processing time <b>only</b> while the mouse was inside {@code rectangleProcessArrow}
     * — a {@code Rectangle} over the 22x15 arrow sprite. Nothing else on the page says how long a craft takes,
     * and neither does this one.
     */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineRecipe recipe, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        MachineRecipeLayout measured = layout();
        if (mouseX >= measured.arrowX() && mouseX < measured.arrowX() + MachineRecipeLayout.ARROW_WIDTH
                && mouseY >= measured.arrowY() && mouseY < measured.arrowY() + MachineRecipeLayout.ARROW_HEIGHT) {
            tooltip.add(MachineRecipeText.duration(recipe));
        }
        addGasCellTooltip(tooltip, recipe, measured, mouseX, mouseY);
        addAuraTooltip(tooltip, recipe, IOType.INPUT, measured.inputAura(), mouseX, mouseY);
        addAuraTooltip(tooltip, recipe, IOType.OUTPUT, measured.outputAura(), mouseX, mouseY);
    }

    private static void addAuraTooltip(ITooltipBuilder tooltip, MachineRecipe recipe, IOType side,
                                       List<MachineRecipeLayout.Cell> cells, double mouseX, double mouseY) {
        var requirements = MachineRecipeLayout.components(recipe, side, MachineRecipeLayout.Kind.AURA);
        for (int i = 0; i < Math.min(requirements.size(), cells.size()); i++) {
            if (AuraBarDrawable.contains(cells.get(i), mouseX, mouseY))
                AuraBarDrawable.addTooltip(tooltip, (MagicRequirement) requirements.get(i), recipe.recipeTime());
        }
    }

    /**
     * 气体格的兜底 tooltip（E10，0.100.0）：槽位本身没有 ingredient，JEI 是否会为空槽触发槽位级
     * tooltip 回调没有离线手段可证；气体格的全文由这条类别级路径给出——它只看鼠标是否落在
     * 气体格的 18×18 区域内，与槽位状态无关。
     */
    private static void addGasCellTooltip(ITooltipBuilder tooltip, MachineRecipe recipe,
                                          MachineRecipeLayout measured, double mouseX, double mouseY) {
        addGasCellTooltip(tooltip, recipe, IOType.INPUT, measured.inputFluids(), mouseX, mouseY);
        addGasCellTooltip(tooltip, recipe, IOType.OUTPUT, measured.outputFluids(), mouseX, mouseY);
    }

    private static void addGasCellTooltip(ITooltipBuilder tooltip, MachineRecipe recipe, IOType ioType,
                                          List<MachineRecipeLayout.Cell> cells, double mouseX, double mouseY) {
        List<MachineRequirement> fluidKind =
                MachineRecipeLayout.components(recipe, ioType, MachineRecipeLayout.Kind.FLUID);
        int count = Math.min(fluidKind.size(), cells.size());
        for (int i = 0; i < count; i++) {
            MachineRequirement requirement = fluidKind.get(i);
            if (requirement.tankKind() != MachineRequirement.TankKind.GAS && requirement.tankKind() != MachineRequirement.TankKind.MAGIC) {
                continue;
            }
            MachineRecipeLayout.Cell cell = cells.get(i);
            if (mouseX >= cell.x() && mouseX < cell.x() + MachineRecipeLayout.CELL
                    && mouseY >= cell.y() && mouseY < cell.y() + MachineRecipeLayout.CELL) {
                String text = requirement.describe();
                if (text != null && !text.isEmpty()) {
                    tooltip.add(Component.literal(text));
                    if (requirement instanceof MagicRequirement resource && resource.perTick()
                            && (resource.family == com.reborn.modularmachinery.magic.MagicFamily.AIR
                            || resource.family == com.reborn.modularmachinery.magic.MagicFamily.EMBER)) {
                        String total = java.math.BigDecimal.valueOf(resource.amount).multiply(java.math.BigDecimal.valueOf(recipe.recipeTime())).stripTrailingZeros().toPlainString();
                        tooltip.add(Component.translatable("jei.modular_machinery_reborn.resource.base_total",total,recipe.recipeTime()));
                    }
                    MachineRecipeText.addScriptTooltip(tooltip, requirement);
                }
            }
        }
    }
}
