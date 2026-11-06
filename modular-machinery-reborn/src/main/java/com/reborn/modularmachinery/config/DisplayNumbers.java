package com.reborn.modularmachinery.config;

import java.text.DecimalFormat;

/**
 * The two number formats this mod prints energy with, both copied from the original's {@code MiscUtils}.
 *
 * <p>The original had two and used them in different places, so keeping them apart is part of matching it:
 *
 * <ul>
 *   <li>{@link #abbreviated} is {@code MiscUtils.formatNumber} — a suffix ({@code K} / {@code M} / {@code G} / …)
 *       for anything at or above a thousand. It is what the energy hatch's own GUI, and the JEI recipe preview,
 *       printed their numbers with.</li>
 *   <li>{@link #grouped} is {@code MiscUtils.formatDecimal} — a thousands separator, no suffix. It is what the
 *       <b>block tooltip</b> used for a hatch's capacity and transfer rate. P10-21（0.121.46）给它补了
 *       {@code long} 入口：这些容量/速率本来就是 {@code long}，先转 {@code double} 会在
 *       {@code 2^53} 以上丢低位，{@code 10^18-1} 之类会显示成错误数字。</li>
 * </ul>
 *
 * <p><b>完整数字与有意缩写是两件事。</b>{@code grouped} 承诺「换算成文本再去掉分隔符后与输入的整数
 * 完全一致」；{@code abbreviated} 是旧版有意的近似（带 K/M/G… 后缀，大数按千分位缩略），它的输出本来
 * 就不承诺逐位精确。需要精确值的地方用 {@code grouped(long)}，需要旧版外观的地方用
 * {@code abbreviated(long)}。
 *
 * <p><b>The abbreviation's arithmetic is the original's, quirk included.</b> Its {@code M}, {@code G}, {@code T},
 * {@code P} and {@code E} branches divide by the prefix below them <i>and then</i> by 1000, so
 * {@code 5_000_000} prints as {@code 5.0M} while {@code 5_000_000_000} prints as {@code 5.0G} — the same string,
 * a factor of a thousand apart, because every branch lands on "value in thousands of the next unit". That is
 * upstream behaviour and is reproduced rather than corrected: this is a display string, the project's rule is to
 * mirror the original where it is observable, and a "fixed" version would print different text than the mod it
 * reimplements. Anything that needs the true magnitude reads the {@code long}, not this string.
 */
public final class DisplayNumbers {

    /** {@code MiscUtils.DECIMAL_FORMAT}: the original's own pattern. */
    private static final DecimalFormat GROUPED = new DecimalFormat("#,###");

    /**
     * {@code MiscUtils.formatNumber}: abbreviate at a thousand and above.
     *
     * <p>See the class comment for why the larger branches are kept as they are.
     */
    public static String abbreviated(long value) {
        if (value < 1_000L) {
            return String.valueOf(value);
        } else if (value < 1_000_000L) {
            return (double) Math.round((double) value) / 1000.0 + "K";
        } else if (value < 1_000_000_000L) {
            return (double) Math.round((double) (value / 1_000L)) / 1000.0 + "M";
        } else if (value < 1_000_000_000_000L) {
            return (double) Math.round((double) (value / 1_000_000L)) / 1000.0 + "G";
        } else if (value < 1_000_000_000_000_000L) {
            return (double) Math.round((double) (value / 1_000_000_000L)) / 1000.0 + "T";
        } else if (value < 1_000_000_000_000_000_000L) {
            return (double) Math.round((double) (value / 1_000_000_000_000L)) / 1000.0 + "P";
        }
        return (double) Math.round((double) (value / 1_000_000_000_000_000L)) / 1000.0 + "E";
    }

    /**
     * {@code MiscUtils.formatDecimal}: a thousands separator, and no suffix.
     *
     * <p>保留给既有 {@code double} 消费者（既有调用契约不变）。整数请用 {@link #grouped(long)}：
     * 这个重载接收的 {@code double} 在 {@code 2^53} 以上就已经不是原来那个整数了。
     */
    public static String grouped(double value) {
        return GROUPED.format(value);
    }

    /**
     * 完整数字的精确分组（P10-21）：{@code long} 直接交给 {@link DecimalFormat}，不经过 {@code double}。
     *
     * <p>{@code DecimalFormat.format(Object)} 对 {@code Long} 走整数路径，输出与输入逐位一致；分组符与
     * 其它本地化行为沿用同一个 {@code GROUPED} 实例（{@code "#,###"}），所以外观与原来的
     * {@code grouped(double)} 在它能正确表示的范围里完全相同。负数照常带前导 {@code -}；
     * {@code Long.MIN_VALUE} 也不会因为取绝对值而溢出。
     */
    public static String grouped(long value) {
        return GROUPED.format(Long.valueOf(value));
    }

    private DisplayNumbers() {
    }
}
