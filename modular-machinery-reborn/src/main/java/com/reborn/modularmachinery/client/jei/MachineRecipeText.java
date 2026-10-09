package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.config.DisplayNumbers;
import com.reborn.modularmachinery.config.EnergyDisplay;
import com.reborn.modularmachinery.config.ModConfig;
import com.reborn.modularmachinery.recipe.EnergyRequirement;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.InterfaceNumberInputRequirement;
import com.reborn.modularmachinery.recipe.ItemRequirement;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * The words and the numbers the original's recipe page printed.
 *
 * <p>Every string here is the original's own, read from its {@code common/crafting/} and re-keyed under this
 * mod's namespace:
 *
 * <ul>
 *   <li>The tip block under the band came from the {@code RequirementTip} registry plus the recipe's own
 *       tooltip. Three of those tips can fire for this mod's data model — {@code TooltipEnergyInput},
 *       {@code TooltipEnergyOutput} (each exactly two lines) and, from M6d-b,
 *       {@code TooltipInterfaceNumberInput} — which is why {@link #tips} returns at most three groups. The
 *       original's {@code TooltipFuelInput} needs a recipe field this mod does not have, and there is no fluid
 *       tip at all: {@code JEIComponentFluid} only added a chance line to the slot tooltip.</li>
 *   <li>{@code TooltipEnergyInput#buildTooltip} printed {@code "tooltip.machinery.energy.in"} concatenated with
 *       {@code "tooltip.machinery.energy.tick"}, and then a {@code "…energy.total"} line — the second line is
 *       {@code rate x recipeTime}, exactly {@link #energyTip}.</li>
 *   <li>{@code JEIComponentItem#onJEIHoverTooltip} added the chance line, then the min/max line.</li>
 *   <li>{@code MiscUtils#formatNumber} abbreviated the energy rate for the text block, and
 *       {@code MiscUtils#formatFloat} formatted the chance percentage.</li>
 * </ul>
 */
final class MachineRecipeText {

    /** The original's {@code tooltip.machinery.duration.seconds}. */
    private static final String KEY_DURATION_SECONDS = "tooltip.modular_machinery_reborn.machine.duration.seconds";
    /** The original's {@code tooltip.machinery.duration.tick}. */
    private static final String KEY_DURATION_TICKS = "tooltip.modular_machinery_reborn.machine.duration.tick";
    private static final String KEY_CHANCE_IN = "tooltip.modular_machinery_reborn.machine.chance.in";
    private static final String KEY_CHANCE_OUT = "tooltip.modular_machinery_reborn.machine.chance.out";
    private static final String KEY_CHANCE_IN_NEVER = "tooltip.modular_machinery_reborn.machine.chance.in.never";
    private static final String KEY_CHANCE_OUT_NEVER = "tooltip.modular_machinery_reborn.machine.chance.out.never";
    private static final String KEY_MIN_MAX_IN = "tooltip.modular_machinery_reborn.machine.min_max_amount.input";
    private static final String KEY_MIN_MAX_OUT = "tooltip.modular_machinery_reborn.machine.min_max_amount.output";
    /** The original's {@code tooltip.machinery.ingredient_array_input}, re-keyed (0.26.0). */
    private static final String KEY_INGREDIENT_ARRAY_INPUT =
            "tooltip.modular_machinery_reborn.machine.ingredient_array_input";
    private static final String KEY_ENERGY_IN = "tooltip.modular_machinery_reborn.machine.energy.in";
    private static final String KEY_ENERGY_OUT = "tooltip.modular_machinery_reborn.machine.energy.out";
    private static final String KEY_ENERGY_TICK = "tooltip.modular_machinery_reborn.machine.energy.tick";
    private static final String KEY_ENERGY_TOTAL = "tooltip.modular_machinery_reborn.machine.energy.total";
    private static final String KEY_ENERGY_TYPE = "tooltip.modular_machinery_reborn.machine.energy.type";
    /**
     * M6d-b: the smart interface's three tip lines, which are the original's
     * {@code tooltip.machinery.smartinterface.value} / {@code .minvalue} / {@code .maxvalue}, re-namespaced.
     */
    private static final String KEY_INTERFACE_VALUE = "tooltip.modular_machinery_reborn.smartinterface.value";
    private static final String KEY_INTERFACE_MIN = "tooltip.modular_machinery_reborn.smartinterface.minvalue";
    private static final String KEY_INTERFACE_MAX = "tooltip.modular_machinery_reborn.smartinterface.maxvalue";

    private MachineRecipeText() {
    }

    /**
     * The original's {@code dynamicRecipeWrapper#getTooltipStrings}: the processing time was shown <b>only</b>
     * while the mouse was over the process arrow, as {@code duration.seconds} followed by {@code duration.tick}.
     */
    static Component duration(MachineRecipe recipe) {
        return Component.translatable(KEY_DURATION_SECONDS, recipe.recipeTime() / 20.0F)
                .append(Component.translatable(KEY_DURATION_TICKS, recipe.recipeTime()));
    }

    /** The summed rate of every energy requirement on one side, as the original's energy tips summed it. */
    static long energyPerTick(MachineRecipe recipe, IOType ioType) {
        long total = 0L;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement instanceof EnergyRequirement energy && energy.ioType() == ioType) {
                total += energy.energyPerTick();
            }
        }
        return total;
    }

    /**
     * The text groups the original wrote under the band, in its registry order: energy input, energy output,
     * then — from M6d-b — the smart interface's min/max lines.
     *
     * <p>A group is only present when its tip produced output, exactly as in
     * {@code CategoryDynamicRecipe#buildRecipeComponents}. The registry order is
     * {@code RegistryRequirementTips#initialize}: energy input, energy output, fuel input (not ported),
     * smart interface number input. {@code DynamicRecipeWrapper#drawInfo} then appended the recipe's own
     * {@code getFormattedTooltip()} as one more group unconditionally — see
     * {@link #tipGroupsWithRecipeTooltip}.
     */
    static List<List<String>> tips(MachineRecipe recipe) {
        List<List<String>> tips = new ArrayList<>(3);
        List<String> input = energyTip(recipe, IOType.INPUT);
        if (!input.isEmpty()) {
            tips.add(input);
        }
        List<String> output = energyTip(recipe, IOType.OUTPUT);
        if (!output.isEmpty()) {
            tips.add(output);
        }
        List<String> interfaces = interfaceTip(recipe);
        if (!interfaces.isEmpty()) {
            tips.add(interfaces);
        }
        return tips;
    }

    /**
     * {@link #tips} plus the original's trailing group for {@code recipe.getFormattedTooltip()}.
     *
     * <p>This mod's recipe schema has no tooltip field, so that group is always empty — but it still costs one
     * 2px divider when {@code drawInfo} lays the block out, which is why it is kept rather than optimised away:
     * that method walks the block backwards subtracting one divider per group before drawing it forwards, so
     * dropping the empty group would shift every line down.
     */
    static List<List<String>> tipGroupsWithRecipeTooltip(MachineRecipe recipe) {
        List<List<String>> groups = tips(recipe);
        groups.add(List.of());
        return groups;
    }

    /** {@code TooltipEnergyInput#buildTooltip} / {@code TooltipEnergyOutput#buildTooltip}, verbatim. */
    static List<String> energyTip(MachineRecipe recipe, IOType ioType) {
        long perTick = energyPerTick(recipe, ioType);
        if (perTick <= 0L) {
            return List.of();
        }
        // The display unit, and the original's own order of operations for it: scale the number, then abbreviate,
        // then name the unit (`TooltipEnergyInput.java:56-57`: `formatEnergyForDisplay` first, `formatNumber`
        // after). Scaling is display-only — nothing about the recipe's real draw changes.
        EnergyDisplay display = ModConfig.energyDisplay();
        String unit = display.label();
        String label = Component.translatable(ioType == IOType.INPUT ? KEY_ENERGY_IN : KEY_ENERGY_OUT).getString();
        return List.of(
                label + Component.translatable(KEY_ENERGY_TICK,
                        DisplayNumbers.abbreviated(display.display(perTick)), unit).getString(),
                Component.translatable(KEY_ENERGY_TOTAL,
                        DisplayNumbers.abbreviated(display.display(perTick * recipe.recipeTime())), unit).getString());
    }

    /**
     * The original's {@code TooltipInterfaceNumberInput#buildTooltip} (M6d-b), line for line: one
     * {@code tooltip.machinery.smartinterface.value} line when the requirement asks for an exact value, and the
     * {@code .minvalue} / {@code .maxvalue} pair when it asks for a range.
     *
     * <p>The original's <b>first</b> branch is not reproduced: when a type declared its own {@code jeiTooltip},
     * that string was {@code String.format}-ed with the two bounds and printed instead
     * ({@code :42-58}). This projection does not carry author-supplied format strings into the client — see the
     * class comment of {@code machine/SmartInterfaceType} — so every interface tip comes from the mod's own
     * keys. That is a deliberate, recorded divergence (D16), not an oversight.
     *
     * <p>All interface requirements are grouped into <b>one</b> group, which is what makes the lines appear in
     * recipe order under a single divider: the original iterated every filtered component and appended to one
     * {@code ArrayList}, so a recipe with two interface requirements printed four lines and still counted as one
     * tip.
     */
    static List<String> interfaceTip(MachineRecipe recipe) {
        List<String> tooltip = new ArrayList<>();
        for (MachineRequirement requirement : recipe.requirements()) {
            if (!(requirement instanceof InterfaceNumberInputRequirement interfaceRequirement)) {
                continue;
            }
            float minValue = interfaceRequirement.minValue();
            float maxValue = interfaceRequirement.maxValue();
            if (minValue == maxValue) {
                tooltip.add(Component.translatable(KEY_INTERFACE_VALUE, formatFloat(minValue, 2)).getString());
            } else {
                tooltip.add(Component.translatable(KEY_INTERFACE_MIN, formatFloat(minValue, 2)).getString());
                tooltip.add(Component.translatable(KEY_INTERFACE_MAX, formatFloat(maxValue, 2)).getString());
            }
        }
        return tooltip;
    }

    /** The original's {@code JEIComponentItem#addChanceTooltip}. */
    static void addChanceTooltip(boolean input, ITooltipBuilder tooltip, float chance) {
        if (chance >= 1.0F || chance < 0.0F) {
            return;
        }
        if (chance == 0.0F) {
            tooltip.add(Component.translatable(input ? KEY_CHANCE_IN_NEVER : KEY_CHANCE_OUT_NEVER));
            return;
        }
        String percent = chance < 0.0001F ? "< 0.01%" : formatFloat(chance * 100.0F, 2) + "%";
        tooltip.add(Component.translatable(input ? KEY_CHANCE_IN : KEY_CHANCE_OUT, percent));
    }

    /** The original's {@code JEIComponentItem#addMinMaxTooltip}. */
    static void addAmountRangeTooltip(boolean input, ITooltipBuilder tooltip, ItemRequirement item) {
        if (!item.hasAmountRange()) {
            return;
        }
        tooltip.add(Component.translatable(input ? KEY_MIN_MAX_IN : KEY_MIN_MAX_OUT,
                item.minAmount(), item.maxAmount()));
    }

    /**
     * The original's {@code tooltip.machinery.ingredient_array_input}, re-keyed under this mod's namespace and
     * reused <b>verbatim</b>: "Accepts the following inputs (only one of them is consumed):".
     *
     * <p>The original printed it from {@code JEIComponentIngredientArray#onJEIHoverTooltip} for every entry of
     * the group ({@code JEIComponentIngredientArray.java:44}); the alternatives themselves happen to be the
     * slot's own ingredients here, so the line is the whole tip. An array is accepted as an input only, which is
     * why there is no output variant to go with {@code tooltip.machinery.ingredient_array_output}.
     */
    static void addIngredientArrayTooltip(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable(KEY_INGREDIENT_ARRAY_INPUT));
    }

    /**
     * {@code MiscUtils#formatNumber}: the abbreviation printed next to the energy rate.
     *
     * <p>Kept as a named alias because the harness and this class's own callers refer to it by the original's name;
     * the arithmetic lives in {@link DisplayNumbers#abbreviated} so the hatch GUI and the toolbar cannot drift
     * apart.
     */
    static String formatNumber(long value) {
        return DisplayNumbers.abbreviated(value);
    }

    /** {@code MiscUtils#formatFloat}, as used for the chance percentage. */
    static String formatFloat(float value, int decimalFraction) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(decimalFraction);
        return format.format(value);
    }
}
