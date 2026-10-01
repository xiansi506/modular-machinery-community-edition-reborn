package com.reborn.modularmachinery.machine;

import javax.annotation.Nullable;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * One interface type a machine definition declares — the data-driven counterpart of the original's
 * {@code SmartInterfaceType} (134 lines), which only a CraftTweaker script could create
 * ({@code MachineModifier#addSmartInterfaceType}, {@code MachineBuilder#addSmartInterfaceType}).
 *
 * <h2>Why this is a record and not a builder</h2>
 *
 * <p>The original's class was a mutable builder-shaped object because ZenScript built it step by step
 * ({@code SmartInterfaceType.create("mode", 0).setHeaderInfo(...).setPriority(1000)}). A machine definition is
 * one JSON object, so the whole thing is read at once and then never changes. The fields are the original's,
 * with the same meanings and the same defaults.
 *
 * <h2>The one thing deliberately not carried over</h2>
 *
 * <p>The original also had {@code jeiTooltip} / {@code jeiTooltipArgsCount}, a per-type replacement for the
 * JEI min/max lines ({@code TooltipInterfaceNumberInput:42-58}). This projection ships those lines through the
 * project's own translation keys instead (see {@code MachineRecipeText#interfaceTip}), which is the same
 * decision the rest of the JEI text makes: every tip in this mod comes from {@code tooltip.modular_machinery_reborn.*},
 * not from author-supplied format strings the client would have to trust. Recorded in the D16 record.
 *
 * <h2>Only the number kind exists</h2>
 *
 * <p>The task that produced this class asked for the string variant to be settled by evidence, and the evidence
 * says no: the original's block enum {@code SmartInterfaceTypeEnum} declares <b>only</b> {@code NUMBER}
 * (12 lines, {@code enum SmartInterfaceTypeEnum { NUMBER; }}), and the only reader of any interface value is
 * {@code RequirementInterfaceNumInput} — a number input, whose two numeric fields would be meaningless for a
 * string. Nothing in the original reads a string interface. So there is no {@code kind} field here: a machine
 * either declares numbers or declares nothing. See the D16 record for the full evidence list.
 *
 * @param type         the type's name, unique within one machine definition; a requirement names it
 * @param defaultValue the value a freshly formed machine starts from, the original's {@code defaultValue}
 * @param header       a translation key or literal printed above the value in the controller's screen
 * @param valueFormat  a {@code String.format} pattern for the value line, or {@code null} / blank for the
 *                     default {@code gui.modular_machinery_reborn.smartinterface.value} line
 * @param footer       a translation key or literal printed below the value line
 * @param priority     which declared type an undeclared machine's value binds to; larger wins, clashing with
 *                     the original's {@code Comparable} implementation
 * @param notEqual     a translation key or literal reported when a requirement's range does not hold, or
 *                     {@code null} for the original's default
 *                     {@code craftcheck.failure.interface.number.notequal}
 */
public record SmartInterfaceType(String type, float defaultValue, String header, @Nullable String valueFormat,
                                 String footer, int priority, @Nullable String notEqual) {

    /** The original's default message when a value is outside the range a requirement asked for. */
    public static final String DEFAULT_NOT_EQUAL_KEY = "craftcheck.failure.interface.number.notequal";

    public SmartInterfaceType(String type, float defaultValue, String header, @Nullable String valueFormat,
                              String footer, int priority, @Nullable String notEqual) {
        this.type = type;
        this.defaultValue = defaultValue;
        this.header = header == null ? "" : header;
        this.valueFormat = valueFormat == null || valueFormat.isBlank() ? null : valueFormat;
        this.footer = footer == null ? "" : footer;
        this.priority = priority;
        this.notEqual = notEqual == null || notEqual.isBlank() ? null : notEqual;
    }

    /**
     * The original's {@code getFirstSmartInterfaceType}: the highest-priority declared type, ties broken by the
     * declaration order the loader preserved. Empty only when the definition declares no type at all.
     */
    public static java.util.Optional<SmartInterfaceType> highestPriority(java.util.List<SmartInterfaceType> types) {
        SmartInterfaceType best = null;
        for (SmartInterfaceType candidate : types) {
            if (best == null || candidate.priority() > best.priority()) {
                best = candidate;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /**
     * The line the controller's screen prints for a value: the author's own {@code valueFormat} when it is
     * formattable, otherwise the project's default line.
     *
     * <p>The original wrapped the author's format in a {@code try}/{@code catch (IllegalFormatException)} and
     * logged a warning before falling back ({@code GuiContainerSmartInterface:86-94}). A log-and-continue is the
     * right shape for a GUI, but the fallback line has to survive being called from a plain unit context, so this
     * method returns the fallback <b>key</b> and the caller decides how to translate it.
     *
     * @return a formatted literal line, or {@code null} to mean "use
     *         {@code gui.modular_machinery_reborn.smartinterface.value} with this value"
     */
    @Nullable
    public String formatValue(float value) {
        if (this.valueFormat == null) {
            return null;
        }
        try {
            return String.format(Locale.ROOT, this.valueFormat, value);
        } catch (IllegalFormatException exception) {
            return null;
        }
    }

    /** The message a failed range check reports: the author's own, or the original's default. */
    public String notEqualMessage() {
        return this.notEqual == null ? DEFAULT_NOT_EQUAL_KEY : this.notEqual;
    }

    @Override
    public String toString() {
        return "SmartInterfaceType[" + this.type + ", default=" + this.defaultValue + ", priority=" + this.priority + "]";
    }
}
