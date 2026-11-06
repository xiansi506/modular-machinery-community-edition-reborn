package com.reborn.modularmachinery.integration.jade;

import com.reborn.modularmachinery.block.MachineHatchBlock;
import com.reborn.modularmachinery.block.HatchKind;
import com.reborn.modularmachinery.block.MachineHatchBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.view.FluidView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将本模组仓的 Jade 标题改为白色，并保留其流体容量条。
 *
 * <h2>三次失败学到的三件事（都由实机读数定出来，不是猜的）</h2>
 *
 * <ol>
 *   <li><b>按 id 移除无效</b>：Jade 的 {@code ClientViewGroup.tooltip(...)} 用
 *       {@code ITooltip.add(IElement)}（**不带 id**）加流体元素，那些元素没有 tag。</li>
 *   <li><b>只按 {@code Align.LEFT} 取元素不够</b>：通用流体那一块（图标与空框）在**另一侧**
 *       （它的空框画在提示的**另一端**，即 Align.RIGHT 侧），所以只按 LEFT 取到的列表里看不到它（Align 只有 LEFT/RIGHT 两个值）。</li>
 *   <li><b>方块显示名不能直接用</b>：{@code getDisplayName()} 会带等级前缀
 *       （实测 {@code "真空流体输入仓"}），而提示里印的是简称（{@code "流体输入仓"}），
 *       于是「包含」判据永远不成立、每次都走兜底。</li>
 * </ol>
 *
 * <h2>这一版的定位方式</h2>
 *
 * <p>不再靠名字，而是**按已知的固定下标**：Jade 提示的前段是
 * {@code 第 0 行 = 方块名+图标}。本回调**只对本模组的 {@link MachineHatchBlock} 注册**，
 * 所以它看到的提示必然就是本模组方块那一张，不存在误伤别的方块的可能。
 *
 * <p><b>第 1 行不一定是流体行（0.63.5 更正）。</b>流体仓为空时本模组的 storage provider 返回空列表，
 * Jade 因此**不写** {@code JadeFluidStorage}，提示里没有流体行；装气体的混合仓第 1 行是 Mekanism 的
 * {@code mekanism:gas} 元素。所以「清第 1 行」只对**有流体**的仓成立，空流体时一律不碰任何行。
 *
 * <h2>为什么还要一个「已经处理过」的标记</h2>
 *
 * <p>所有者实测到「回调被调用 410 次 / 改动提示 325 次」，提示里因此堆了两份本模组的行
 * ——{@code onTooltipCollected} 会在一次悬停里被多次调用（每次重绘都可能是一次），
 * 所以**只能动手一次**。标记用元素自己的注册 id（{@code modular_machinery_reborn:hatch_fluid}），
 * 于是「本模组的行在不在提示里」是个可直接查的事实。
 */
public final class FluidStorageSuppressor implements JadeTooltipCollectedCallback {

    public static final FluidStorageSuppressor INSTANCE = new FluidStorageSuppressor();

    /** 本模组组件自己的 id：既用于注册，也用作「提示里已经有本模组的行」的标记。 */
    public static final ResourceLocation OWN_MARKER =
            new ResourceLocation(com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, "hatch_fluid");

    /** Jade 提示里方块名所在的行（第 0 行）。 */
    private static final int ROW_NAME = 0;

    /** 回调被调用的次数。 */
    private static final AtomicInteger CALLED = new AtomicInteger();

    /** 其中真的改动了提示的次数。 */
    private static final AtomicInteger ACTED = new AtomicInteger();

    /** 其中因为「已经处理过」而跳过的次数（用来确认重复调用这件事发生过）。 */
    private static final AtomicInteger SKIPPED = new AtomicInteger();

    private static volatile String lastReport = "（还没被调用过）";

    private FluidStorageSuppressor() {
    }

    /** 给 {@code /mm-diagnose} 的读数。 */
    public static String counters() {
        return "回调被调用 " + CALLED.get() + " 次 / 改动提示 " + ACTED.get() + " 次 / 因已处理而跳过 "
                + SKIPPED.get() + " 次；最近一次: " + lastReport;
    }

    @Override
    public void onTooltipCollected(ITooltip tooltip, Accessor<?> accessor) {
        CALLED.incrementAndGet();
        if (!(accessor instanceof BlockAccessor block)
                || !(block.getBlockState().getBlock() instanceof MachineHatchBlock)
                || !(block.getBlockEntity()
                        instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch)) {
            return;
        }
        // **已经处理过就跳过**（0.56.0 修正）。
        //
        // 本模组标题带等级前缀（例如「真空流体输入仓」），可用它判断重复调用。
        //
        // 为什么必须去重：一次悬停里这个回调会被调用几百次，每次都「先删再加」虽然结果幂等，
        // 却等于把整张提示重建几百遍，而且留下一个坏习惯——让人以为「碰巧对」就等于「设计对」。
        if (rowStartsWith(tooltip, ROW_NAME, hatch.getDisplayName().getString())) {
            SKIPPED.incrementAndGet();
            return;
        }
        ACTED.incrementAndGet();
        boolean removedName = removeRow(tooltip, ROW_NAME);
        // 名字行删掉后，后面各行会前移；replaceFluidRow 只处理**有流体**时的第 1 行，
        // 空流体时保留化学兼容层的气体条（见该方法的注释）。
        lastReport = "删除名字行=" + removedName + " 处理后剩 " + tooltip.size() + " 行";
        // 方块名由本模组补回（它原来那一行连同图标被删掉了）。
        List<Component> mine = new ArrayList<>();
        mine.add(hatch.getDisplayName().copy().withStyle(ChatFormatting.WHITE));
        for (int i = 0; i < mine.size(); i++) {
            tooltip.add(i, mine.get(i), OWN_MARKER);
        }
        replaceFluidRow(tooltip, hatch);
        lastReport = lastReport + " ｜ 提示=" + describeTooltip(tooltip);
    }

    /**
     * 用客户端方块实体已同步的罐内容替换 Jade capability 查询生成的流体行。
     *
     * <p>实机诊断证明服务端 extension 已交出 32000/32000 mB，但画面仍显示 empty/0，说明错误发生在
     * Jade 的 extension 客户端映射之后。这里读取同一个客户端罐（仓界面也使用它），并用 Jade 官方
     * {@link FluidView} 与进度条 helper 绘制，避免再经过 capability/provider 选择。
     *
     * <p><b>为什么空流体必须最先返回（0.63.5）。</b>本模组的 storage provider 在流体为空时返回
     * 空列表，而 Jade 11.13.3 的 {@code ViewGroup.saveList(empty)} 返回 {@code true} 且不写
     * {@code JadeFluidStorage}；{@code FluidStorageProvider.putData} 取第一个非 null 的 groups
     * 就 return。于是**空流体仓的提示里根本没有流体行**，第 1 行是排在它后面的别的东西——
     * 装气体的混合仓这里是 Mekanism 的 {@code mekanism:gas} 元素。旧代码无条件清空第 1 行，
     * 正好把气体条删掉（0.62.9 的验收只要求「化学品不被画成空液体」，没要求气体可见，
     * 所以那时看不出来）。0.63.5 起：空流体时不碰任何行，只认准 {@code mekanism:gas}；
     * 兼容层没有交出气体条时，本模组自己补一条（无图标，见已知限制）。
     */
    private static void replaceFluidRow(ITooltip tooltip, MachineHatchBlockEntity hatch) {
        if (hatch.kind().family() != HatchKind.Family.FLUID || hatch.fluidTank() == null) {
            return;
        }
        net.minecraftforge.fluids.FluidStack fluid = hatch.fluidTank().getFluid();
        String[] gas = com.reborn.modularmachinery.compat.mekanism.MekanismModule.gasTooltip(hatch);
        if (fluid.isEmpty()) {
            if (gas == null) {
                lastReport = "流体为空、无气体：不画内容行";
            } else if (hasGasElement(tooltip)) {
                lastReport = "流体为空、气体存在：保留化学兼容层的气体条";
            } else {
                addGasBar(tooltip, gas);
                lastReport = "流体为空、气体存在：兼容层没有交出气体条，补一条本模组的";
            }
            return;
        }

        // P10-19（0.121.34）：不再按固定下标清空整行。Jade 11 的原生流体内容带官方归属 tag
        // （Identifiers.UNIVERSAL_FLUID_STORAGE / _DETAILED，即 jade:fluid_storage[.detailed]），
        // 而第三方插在相邻行的元素（例如别的模组状态、Mekanism 气体条）有各自不同的 tag。
        // 固定下标的 left/right.clear() 在「其他模组状态插进标题与流体行之间」的混排里
        // 会删掉别人的行、留下真正的旧流体行。现在逐元素只删除确认归属的原生流体内容，
        // 同一行左右两侧的其余元素原样保留；新条写回被删内容所在的行。没有可确认归属的
        // 旧流体内容时不动任何行（空罐/气体场景已提前返回，这里也不会有新条可画）。
        List<IElement> targetLeft = null;
        List<IElement> targetRight = null;
        for (int row = 0; row < tooltip.size(); row++) {
            List<IElement> left = tooltip.get(row, IElement.Align.LEFT);
            List<IElement> right = tooltip.get(row, IElement.Align.RIGHT);
            boolean hit = stripLegacyFluidElements(left) | stripLegacyFluidElements(right);
            if (hit) {
                targetLeft = left;
                targetRight = right;
            }
        }
        if (targetLeft == null && targetRight == null) {
            return;
        }

        int capacity = Math.max(1, hatch.fluidTank().getCapacity());
        FluidView view = FluidView.readDefault(FluidView.writeDefault(
                JadeFluidObject.of(fluid.getFluid(), fluid.getAmount()), capacity));
        if (view == null) {
            return;
        }
        Component label = Component.translatable("jade.fluid2",
                IDisplayHelper.get().stripColor(view.fluidName).withStyle(ChatFormatting.WHITE),
                Component.literal(view.current).withStyle(ChatFormatting.WHITE),
                Component.literal(view.max)).withStyle(ChatFormatting.GRAY);
        IElement bar = tooltip.getElementHelper().progress(view.ratio, label,
                tooltip.getElementHelper().progressStyle().overlay(view.overlay), BoxStyle.DEFAULT, true);
        bar.align(IElement.Align.LEFT).tag(OWN_MARKER);
        List<IElement> host = targetLeft != null ? targetLeft : targetRight;
        host.add(bar);
        lastReport = "客户端自有流体条 " + fluid.getFluid().builtInRegistryHolder().key().location()
                + " " + fluid.getAmount() + "/" + capacity + " mB";
    }

    /**
     * 从一行的一侧移除确认归属 Jade 原生流体条的元素，保留该侧其余元素。
     *
     * <p>Jade 的 {@code tooltip.get(row, align)} 返回的是**活列表**，所以可以逐元素
     * {@code iterator.remove()}——这正是「移除自己的元素而非整个 list」的实现。
     * 只有 tag 恰好是 {@code Identifiers.UNIVERSAL_FLUID_STORAGE}（或 detailed 变体）的元素才算
     * Jade 原生流体内容；无 tag 元素与其他任何 tag（第三方状态、气体条、本模组已处理标记）一律不动。
     */
    private static boolean stripLegacyFluidElements(List<IElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (java.util.Iterator<IElement> it = elements.iterator(); it.hasNext(); ) {
            IElement element = it.next();
            if (isLegacyFluidTag(element.getTag())) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** Jade 11 原生流体内容的官方归属 tag（常量来自 Jade 自己的 {@code Identifiers}，不猜名称）。 */
    private static boolean isLegacyFluidTag(net.minecraft.resources.ResourceLocation tag) {
        return snownee.jade.api.Identifiers.UNIVERSAL_FLUID_STORAGE.equals(tag)
                || snownee.jade.api.Identifiers.UNIVERSAL_FLUID_STORAGE_DETAILED.equals(tag);
    }

    /**
     * 兼容层没交出气体条时，由本模组补一条。
     *
     * <p>与流体条同一形态（名称 + 当前量/容量），但没有气体图标——图标在 Mekanism 的
     * {@code ChemicalElement} 里，主模组不引用那个类型（勘测 X4 的隔离契约）。这一条只会在
     * 「提示里确实没有 {@code mekanism:gas} 元素」时执行，所以不会与兼容层重复。
     */
    private static void addGasBar(ITooltip tooltip, String[] gas) {
        Component name = com.reborn.modularmachinery.compat.mekanism.MekanismModule.gasDisplayName(gas[0]);
        long amount = parseLong(gas[1]);
        long capacity = Math.max(1L, parseLong(gas[2]));
        Component label = Component.translatable("jade.fluid2",
                (name == null ? Component.literal(gas[0]) : name).copy().withStyle(ChatFormatting.WHITE),
                Component.literal(amount + " mB").withStyle(ChatFormatting.WHITE),
                Component.literal(capacity + " mB")).withStyle(ChatFormatting.GRAY);
        float ratio = Math.min(1.0F, amount / (float) capacity);
        IElement bar = tooltip.getElementHelper().progress(ratio, label,
                tooltip.getElementHelper().progressStyle(), BoxStyle.DEFAULT, true);
        bar.align(IElement.Align.LEFT).tag(OWN_MARKER);
        tooltip.add(bar);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException malformed) {
            return 0L;
        }
    }

    /** 化学兼容层的气体元素 tag。Mekanism 的 {@code LookingAtUtils.GAS} 就是 {@code mekanism:gas}。 */
    private static final ResourceLocation MEKANISM_GAS_TAG = new ResourceLocation("mekanism", "gas");

    /** 这个提示里有没有化学兼容层交出的气体条（可能在任何一行）。 */
    private static boolean hasGasElement(ITooltip tooltip) {
        for (int row = 0; row < tooltip.size(); row++) {
            if (rowHasGasElement(tooltip, row, IElement.Align.LEFT)
                    || rowHasGasElement(tooltip, row, IElement.Align.RIGHT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rowHasGasElement(ITooltip tooltip, int row, IElement.Align align) {
        List<IElement> elements = tooltip.get(row, align);
        if (elements == null) {
            return false;
        }
        for (IElement element : elements) {
            if (isGasTag(element.getTag())) {
                return true;
            }
        }
        return false;
    }

    /** 只认精确的 {@code mekanism:gas}：同命名空间的能量/其他化学元素不算。 */
    private static boolean isGasTag(ResourceLocation tag) {
        return MEKANISM_GAS_TAG.equals(tag);
    }

    /**
     * 提示的逐行摘要（下标、左右两侧元素的 tag）。
     *
     * <p>给 {@code /mm-diagnose} 用：气体条到底在不在、在第几行，下次不必再靠猜。
     * 只记 tag 不记文本：文本可能含玩家内容，且这里要回答的是「哪一行是谁的」。
     */
    private static String describeTooltip(ITooltip tooltip) {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < tooltip.size() && out.length() < 240; row++) {
            out.append(row).append('[');
            appendTags(out, tooltip.get(row, IElement.Align.LEFT));
            out.append('|');
            appendTags(out, tooltip.get(row, IElement.Align.RIGHT));
            out.append(']');
        }
        return out.toString();
    }

    private static void appendTags(StringBuilder out, List<IElement> elements) {
        if (elements == null) {
            return;
        }
        for (IElement element : elements) {
            ResourceLocation tag = element.getTag();
            out.append(tag == null ? "?" : tag).append(';');
        }
    }

    /**
     * 删掉一行的**所有**元素（左右两侧都试），返回是否删到了东西。
     *
     * <p>逐个元素按它自己的 tag 删除，方块标题的图标等未标记元素会留在原位。
     */
    private static boolean removeRow(ITooltip tooltip, int index) {
        List<IElement> elements = new ArrayList<>();
        List<IElement> left = tooltip.get(index, IElement.Align.LEFT);
        if (left != null) {
            elements.addAll(left);
        }
        List<IElement> right = tooltip.get(index, IElement.Align.RIGHT);
        if (right != null) {
            elements.addAll(right);
        }
        boolean removed = false;
        for (IElement element : elements) {
            ResourceLocation tag = element.getTag();
            if (tag != null) {
                tooltip.remove(tag);
                removed = true;
            }
        }
        return removed;
    }
    /** 这一行第一个元素的文本是不是以给定字符串开头。 */
    private static boolean rowStartsWith(ITooltip tooltip, int index, String prefix) {
        List<IElement> left = tooltip.get(index, IElement.Align.LEFT);
        if (left == null || left.isEmpty()) {
            return false;
        }
        String message = left.get(0).getMessage();
        return message != null && message.startsWith(prefix);
    }
}
