package com.reborn.modularmachinery.config;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The energy unit this mod <b>displays</b>, and the arithmetic that goes with it.
 *
 * <p>The original had exactly this and nothing more: {@code client/util/EnergyDisplayUtil.java} carried
 * {@code display.energy.Display_Energy_Type} with the four values {@code FE} / {@code RF} / {@code IC2_EU} /
 * {@code GT_EU} and a per-type multiplier used <b>only for display</b>. Its own comment says so —
 * <i>"to have GUI, recipe preview and energy be displayed in that type of energy in ALL ModularMachinery things"</i>
 * — and its {@code formatEnergyForDisplay} is a multiply-and-floor. <b>No stored value is ever converted</b>:
 * an 8192 FE hatch prints as {@code 2048 EU} with IC2 selected, and still holds 8192 FE.
 *
 * <p>That matters for faithfulness: the original's {@code FE(1), RF(1), IC2_EU(0.25F), GT_EU(0.25F)} is copied
 * here verbatim, including the fact that RF is <b>1:1</b> (it is a label, not a conversion) and that both EU
 * flavours are a <b>quarter</b>.
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p>The original's two tooltip switches, {@code display.energy.IC2_EU_Tooltip} and {@code GT_EU_Tooltip}, are
 * <b>not</b> implemented. Each only had an effect when that mod was installed — IC2 does not exist for 1.20.1, and
 * GregTech is not a dependency of this project — so on any install this mod can actually run on, both switches
 * were no-ops. The unit <i>label</i> they gated is covered by {@link #label()} instead.
 */
public enum EnergyDisplay implements StringRepresentable {

    /** The default, and what Forge's own energy unit is. */
    FE("FE", 1.0D, 1L),
    /** The label only: the original divides by nothing, so RF is 1:1. */
    RF("RF", 1.0D, 1L),
    /** IC2's EU — a display scale of one quarter. */
    IC2_EU("EU", 0.25D, 4L),
    /** GregTech's EU, same scale, and the same label as IC2's in the original's own language file. */
    GT_EU("EU", 0.25D, 4L);

    /** What the config file calls each value — the original's own spellings, so old notes still apply. */
    private final String configName;

    /** The suffix printed after a number. Note IC2_EU and GT_EU both print {@code EU}. */
    private final String label;

    /** Display-only scale, copied from the original's {@code EnergyType}. */
    private final double multiplier;

    /**
     * 同一比例的<strong>整数</strong>表示：1（FE/RF，原样输出）或 4（EU，除以 4 并向下取整）。
     *
     * <p>P10-21（0.121.46）：显示值必须从 {@code long} 直接算，不能先乘 {@code double}。{@code (long)}
     * 与 {@code (double)} 互转会丢低位：{@code 2^53+1} 变成 {@code 2^53}，{@code 10^18-1} 先被舍入成
     * {@code 10^18}，缩写因此跨到 E。这里只保留一个整数除数，比例的语义与 {@link #multiplier()} 完全一致。
     */
    private final long divisor;

    EnergyDisplay(String label, double multiplier, long divisor) {
        this.configName = name();
        this.label = label;
        this.multiplier = multiplier;
        this.divisor = divisor;
    }

    /** The value's spelling in the config file, e.g. {@code IC2_EU}. */
    @Override
    public String getSerializedName() {
        return this.configName;
    }

    /** The suffix printed after an energy number, e.g. {@code FE}, {@code RF} or {@code EU}. */
    public String label() {
        return this.label;
    }

    /**
     * The translation key whose value is {@link #label()}, e.g.
     * {@code tooltip.modular_machinery_reborn.machine.energy.type.ic2_eu}.
     *
     * <p>The original had exactly these keys — {@code tooltip.energy.type.fe} / {@code .rf} / {@code .ic2_eu} /
     * {@code .gt_eu}, with {@code ic2_eu} and {@code gt_eu} both reading {@code EU} — and took the printed unit
     * from {@code getUnlocalizedFormat()}. Routing through a key keeps the unit translatable rather than baked
     * into the code, which matters for a mod that ships both {@code en_us} and {@code zh_cn}.
     */
    public String labelKey() {
        return "tooltip.modular_machinery_reborn.machine.energy.type." + this.configName.toLowerCase(Locale.ROOT);
    }

    /** The localised unit label, or {@link #label()} when no translation is loaded. */
    public net.minecraft.network.chat.Component labelComponent() {
        return net.minecraft.network.chat.Component.translatable(labelKey());
    }

    /** Display-only scale. Never applied to a stored value. */
    public double multiplier() {
        return this.multiplier;
    }

    /**
     * The stored value rendered for display: the original's {@code MathHelper.lfloor(energy * multiplier)}.
     *
     * <p>{@code floor} rather than rounding, because the original floored — an 8192 FE hatch reads {@code 2048 EU}
     * and a 100 FE/t draw reads {@code 25 EU/t}, with no value ever rounded up past what is really there.
     *
     * <p><b>整数运算，不经过 {@code double}（P10-21）。</b>FE/RF 的乘数是 1，因此原样返回：{@code 2^53+1}
     * 仍然是 {@code 2^53+1}，{@code 10^18-1} 仍然落在 P 段而不是被舍入成 {@code 10^18} 跨到 E。两个 EU
     * 单位仍然按 1/4 显示，但用 {@link Math#floorDiv(long, long)}——负数也向负无穷取整，不会像
     * {@code /} 那样朝零截断（{@code -3 FE} 显示为 {@code -1 EU}，不是 {@code 0}）。
     */
    public long display(long stored) {
        if (this.divisor == 1L) {
            return stored;
        }
        return Math.floorDiv(stored, this.divisor);
    }

    /**
     * Parses a config value, falling back to {@link #FE}.
     *
     * <p>The original's {@code EnergyType.getType} caught the failure and returned FE, so a typo in the config
     * silently meant "FE" rather than refusing to start. That tolerance is kept: this is a display preference, and
     * a mis-spelled one is not worth a crash. {@link #isValid} exists so the loader can say so out loud in the log
     * — the original said nothing, which is the one place this improves on it.
     */
    public static EnergyDisplay parse(@Nullable String value) {
        EnergyDisplay found = find(value);
        return found == null ? FE : found;
    }

    /** The matching value, or {@code null} when the string is not one of the four. */
    @Nullable
    public static EnergyDisplay find(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        for (EnergyDisplay candidate : values()) {
            if (candidate.configName.equals(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    /** Whether a config value names one of the four units. */
    public static boolean isValid(@Nullable String value) {
        return find(value) != null;
    }

    /** The four spellings, for the config comment and for error messages. */
    public static String vocabulary() {
        StringBuilder out = new StringBuilder();
        for (EnergyDisplay value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append('\'').append(value.configName).append('\'');
        }
        return out.toString();
    }
}
