package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.recipe.EnergyRequirement;
import com.reborn.modularmachinery.recipe.FluidRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of one machine's JEI page, measured the way the original measured it.
 *
 * <p>This is a transcription of {@code CategoryDynamicRecipe#buildRecipeComponents}
 * ({@code common/integration/recipe/CategoryDynamicRecipe.java:79-240}), which ran once per machine and produced
 * the page's {@code Point(maxPoint.x, highestY + longestTooltip)} together with the fixed cell positions every
 * recipe of that machine was then slotted into. Because the original registered <b>one category per machine</b>,
 * {@code getBackground()} returned a blank drawable of exactly that size, and JEI drew it with a 4px recipe
 * border around it ({@code RecipeLayout#drawRecipe}: {@code recipeBorder.draw(-4, -4, width + 8, height + 8)}).
 * This class reproduces that measurement only; {@link MachineRecipeCategory} reports it through
 * {@code getWidth()}/{@code getHeight()} and a blank drawable of the same size.
 *
 * <p><b>The measurement, step for step.</b>
 *
 * <ol>
 *   <li>Inputs start at x=4, inputs and outputs both start at y=0. Component <i>classes</i> are visited in
 *       descending {@code getComponentHorizontalSortingOrder}: energy (1000), fluid (100), item (10). For each
 *       class the original placed as many parts as the <b>largest</b> recipe of that machine needed, wrapping
 *       after {@code getMaxHorizontalCount(amount)} parts.</li>
 *   <li>The arrow sits at {@code (endOfInputs + 4, (highestY / 2) / 2)} and is 22x15
 *       ({@code RecipeLayoutHelper.PART_PROCESS_ARROW}). Outputs then start at {@code arrow + 22 + 4} and are
 *       visited in ascending sorting order — item, fluid, energy — which is why the energy cell ends up
 *       rightmost.</li>
 *   <li>The page is {@code max(rightmost cell, widest tip line + 4)} wide and {@code highestY + longest tip
 *       block} tall.</li>
 * </ol>
 *
 * <p><b>Two quirks worth stating, because both are visible.</b> The energy part reports a height of <b>63</b>
 * ({@code RecipeLayoutPart.Energy#getComponentHeight}) while its texture is only 54 tall, so an energy cell
 * always reserves 63 rows of band; and cells are 18x18 with no gap in either direction, because every part
 * returns 0 for both {@code getComponentHorizontalGap} and {@code getComponentVerticalGap}.
 */
final class MachineRecipeLayout {

    /** Every part is 18 wide ({@code getComponentWidth}) with a 0 horizontal gap. */
    static final int CELL = 18;
    /** {@code RequirementTip.LINE_HEIGHT}. */
    static final int LINE_HEIGHT = 9;
    /** {@code RequirementTip.SPLIT_HEIGHT}: the divider between two tip groups. */
    static final int SPLIT_HEIGHT = 2;
    /** {@code RecipeLayoutPart.Energy#getComponentHeight} — 63, nine more than the 54px column it draws. */
    static final int ENERGY_PART_HEIGHT = 63;
    /** {@code RecipeLayoutHelper.PART_PROCESS_ARROW.xSize}. */
    static final int ARROW_WIDTH = 22;
    /** {@code RecipeLayoutHelper.PART_PROCESS_ARROW.zSize}. */
    static final int ARROW_HEIGHT = 15;
    /**
     * The 4px margin the original used three times: the band's starting x ({@code int offsetX = 4}), the room
     * on each side of the process arrow, and the "+4 //Initial offset" added to the widest tip line.
     */
    private static final int MARGIN = 4;

    /** The original's requirement classes, with the sorting order that fixes their left-to-right position. */
    enum Kind {
        /** {@code RecipeLayoutPart.Energy#getComponentHorizontalSortingOrder}. */
        ENERGY(1000),
        /** {@code RecipeLayoutPart.FluidTank#getComponentHorizontalSortingOrder}. */
        FLUID(100),
        /** {@code RecipeLayoutPart.Item#getComponentHorizontalSortingOrder}. */
        ITEM(10);

        final int sortingOrder;

        Kind(int sortingOrder) {
            this.sortingOrder = sortingOrder;
        }
    }

    /** One part's top-left corner in page-local coordinates. */
    record Cell(int x, int y) {
    }

    private final List<Cell> inputEnergy;
    private final List<Cell> inputFluids;
    private final List<Cell> inputItems;
    private final List<Cell> outputItems;
    private final List<Cell> outputFluids;
    private final List<Cell> outputEnergy;
    private final int width;
    private final int height;
    private final int arrowX;
    private final int arrowY;

    private MachineRecipeLayout(List<Cell> inputEnergy, List<Cell> inputFluids, List<Cell> inputItems,
                                List<Cell> outputItems, List<Cell> outputFluids, List<Cell> outputEnergy,
                                int width, int height, int arrowX, int arrowY) {
        this.inputEnergy = List.copyOf(inputEnergy);
        this.inputFluids = List.copyOf(inputFluids);
        this.inputItems = List.copyOf(inputItems);
        this.outputItems = List.copyOf(outputItems);
        this.outputFluids = List.copyOf(outputFluids);
        this.outputEnergy = List.copyOf(outputEnergy);
        this.width = width;
        this.height = height;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
    }

    List<Cell> inputEnergy() {
        return this.inputEnergy;
    }

    List<Cell> inputFluids() {
        return this.inputFluids;
    }

    List<Cell> inputItems() {
        return this.inputItems;
    }

    List<Cell> outputItems() {
        return this.outputItems;
    }

    List<Cell> outputFluids() {
        return this.outputFluids;
    }

    List<Cell> outputEnergy() {
        return this.outputEnergy;
    }

    int width() {
        return this.width;
    }

    int height() {
        return this.height;
    }

    int arrowX() {
        return this.arrowX;
    }

    int arrowY() {
        return this.arrowY;
    }

    /**
     * Measures the page for one machine from every recipe that machine has, exactly as
     * {@code CategoryDynamicRecipe}'s constructor did — the band has to fit the machine's <b>largest</b> recipe,
     * and every recipe is then slotted into those fixed positions.
     *
     * @param font used for the tip-width term of the page width, as {@code buildRecipeComponents} used
     *             {@code Minecraft.getMinecraft().fontRenderer}
     */
    static MachineRecipeLayout measure(List<MachineRecipe> recipes, Font font) {
        List<Cell> inputEnergy = new ArrayList<>();
        List<Cell> inputFluids = new ArrayList<>();
        List<Cell> inputItems = new ArrayList<>();
        List<Cell> outputItems = new ArrayList<>();
        List<Cell> outputFluids = new ArrayList<>();
        List<Cell> outputEnergy = new ArrayList<>();

        // The original tracked highestY across both sides in one variable, and the arrow's y depends on it.
        int[] highestY = {0};
        int offsetX = MARGIN;
        offsetX = place(Kind.ENERGY, maxCount(recipes, IOType.INPUT, Kind.ENERGY), offsetX, inputEnergy, highestY);
        offsetX = place(Kind.FLUID, maxCount(recipes, IOType.INPUT, Kind.FLUID), offsetX, inputFluids, highestY);
        offsetX = place(Kind.ITEM, maxCount(recipes, IOType.INPUT, Kind.ITEM), offsetX, inputItems, highestY);

        offsetX += MARGIN;
        int arrowX = offsetX;
        offsetX += ARROW_WIDTH;
        offsetX += MARGIN;

        offsetX = place(Kind.ITEM, maxCount(recipes, IOType.OUTPUT, Kind.ITEM), offsetX, outputItems, highestY);
        offsetX = place(Kind.FLUID, maxCount(recipes, IOType.OUTPUT, Kind.FLUID), offsetX, outputFluids, highestY);
        offsetX = place(Kind.ENERGY, maxCount(recipes, IOType.OUTPUT, Kind.ENERGY), offsetX, outputEnergy, highestY);

        // offsetProcessArrow = new Point(tempArrowOffsetX, highestY / 2 / 2)
        int arrowY = (highestY[0] / 2) / 2;

        int longestTooltip = 0;
        int widestTooltip = 0;
        for (MachineRecipe recipe : recipes) {
            int tipLength = 0;
            for (List<String> tip : MachineRecipeText.tips(recipe)) {
                for (String line : tip) {
                    widestTooltip = Math.max(widestTooltip, font.width(line));
                }
                tipLength += LINE_HEIGHT * tip.size() + SPLIT_HEIGHT;
            }
            longestTooltip = Math.max(longestTooltip, tipLength);
        }

        // widestTooltip += 4; if (widestTooltip > offsetX) offsetX = widestTooltip;
        int width = Math.max(offsetX, widestTooltip + MARGIN);
        int height = highestY[0] + longestTooltip;
        // A machine with no recipes still gets a category — the original did that too — and JEI must not be
        // handed a zero-sized drawable. The band alone is 34 wide (4 + 4 + 22 + 4) and empty otherwise.
        return new MachineRecipeLayout(inputEnergy, inputFluids, inputItems, outputItems, outputFluids,
                outputEnergy, Math.max(1, width), Math.max(1, height), arrowX, arrowY);
    }

    /**
     * The inner loop of {@code buildRecipeComponents}: {@code amount} parts of one class, wrapping to a new row
     * after {@code getMaxHorizontalCount(amount)} of them, returning the band's new right edge.
     */
    private static int place(Kind kind, int amount, int startX, List<Cell> out, int[] highestY) {
        if (amount <= 0) {
            return startX;
        }
        int height = kind == Kind.ENERGY ? ENERGY_PART_HEIGHT : CELL;
        int perRow = maxHorizontalCount(kind, amount);
        int offsetX = startX;
        int partOffsetX = startX;
        int originalOffsetX = startX;
        int partOffsetY = 0;
        for (int i = 0; i < amount; i++) {
            if (i > 0 && i % perRow == 0) {
                // + getComponentVerticalGap(), which is 0 for all three parts.
                partOffsetY += height;
                partOffsetX = originalOffsetX;
            }
            out.add(new Cell(partOffsetX, partOffsetY));
            // + getComponentHorizontalGap(), also 0 for all three parts.
            partOffsetX += CELL;
            if (partOffsetX > offsetX) {
                offsetX = partOffsetX;
            }
            if (partOffsetY + height > highestY[0]) {
                highestY[0] = partOffsetY + height;
            }
        }
        return offsetX;
    }

    /** {@code RecipeLayoutPart#getMaxHorizontalCount(int)} for each of the three parts. */
    private static int maxHorizontalCount(Kind kind, int amount) {
        if (amount <= 0) {
            return 1;
        }
        return switch (kind) {
            case ENERGY -> 1;
            // FluidTank/GasTank: Math.max((int) Math.ceil(partAmount / 4.0), 1)
            case FLUID -> Math.max((int) Math.ceil((double) amount / 4.0), 1);
            case ITEM -> itemMaxHorizontalCount(amount);
        };
    }

    /** {@code RecipeLayoutPart.Item#getMaxHorizontalCount(int)}, verbatim. */
    private static int itemMaxHorizontalCount(int amount) {
        if (amount <= 3) {
            return 1;
        }
        if (amount == 4) {
            return 2;
        }
        if (amount <= 9) {
            return 3;
        }
        if (amount == 12) {
            return 4;
        }
        int sqrt = (int) Math.round(Math.sqrt(amount));
        if (sqrt <= 0) {
            return 1;
        }
        if (amount % sqrt == 0) {
            return sqrt;
        }
        int range = sqrt <= 3 ? sqrt : sqrt / 2;
        for (int i = 1; i < range; i++) {
            if (amount % (sqrt + i) == 0) {
                return sqrt + i;
            }
            if (sqrt - i > 0 && amount % (sqrt - i) == 0) {
                return sqrt - i;
            }
        }
        return sqrt;
    }

    /** How many parts of one class the machine's largest recipe needs — the {@code componentCounts} step. */
    private static int maxCount(List<MachineRecipe> recipes, IOType ioType, Kind kind) {
        int max = 0;
        for (MachineRecipe recipe : recipes) {
            max = Math.max(max, components(recipe, ioType, kind).size());
        }
        return max;
    }

    /**
     * One recipe's requirements of a given class and side, in the recipe's own order.
     *
     * <p>{@code DynamicRecipeWrapper#finalOrderedComponents} grouped the recipe's requirements by requirement
     * class <b>while preserving their order in the file</b>, and {@code CategoryDynamicRecipe#setRecipe} then
     * handed {@code list.get(index)} to the {@code index}-th slot of that class. So the n-th item input of a
     * recipe goes into the n-th item input cell this layout produced.
     */
    static List<MachineRequirement> components(MachineRecipe recipe, IOType ioType, Kind kind) {
        List<MachineRequirement> out = new ArrayList<>();
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement.ioType() == ioType && kindOf(requirement) == kind) {
                out.add(requirement);
            }
        }
        return out;
    }

    private static Kind kindOf(MachineRequirement requirement) {
        if (requirement instanceof EnergyRequirement) {
            return Kind.ENERGY;
        }
        if (requirement instanceof FluidRequirement) {
            return Kind.FLUID;
        }
        return Kind.ITEM;
    }
}
