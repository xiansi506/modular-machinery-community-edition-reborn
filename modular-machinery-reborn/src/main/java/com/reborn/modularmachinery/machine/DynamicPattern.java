package com.reborn.modularmachinery.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一段<strong>沿某个面重复</strong>的结构：装配线的「一节」。
 *
 * <h2>它解决什么问题</h2>
 *
 * <p>普通机器的结构是固定的。装配线不是：它是一条<strong>可以任意加长</strong>的线，同一个世界里 5 节、
 * 另一个世界里 15 节都是合法的同一台机器。所以结构定义里要有一段「重复单元」，让结构长度成为变量。
 *
 * <p>本类对应旧版的 {@code github.kasuminova.mmce.common.util.DynamicPattern}（395 行）。
 *
 * <h2>三个键在两个层级上同名，这是最容易读错的地方</h2>
 *
 * <p>机器定义的根上有 {@code parts}（固定基座，含控制器），而 {@code dynamic-patterns} 里的每一项也有
 * {@code parts}（重复单元）。同一个键名、两种含义。{@code parts-end} 是第三段：只出现<strong>一次</strong>的收尾单元。
 *
 * <pre>{@code
 * 控制器 + 基座（根 parts）        ← 头，固定
 *   ↓ 沿 faces 方向重复
 * [重复段]（本类的 parts）× N      ← 中间，N 在 [minSize, maxSize] 之间
 *   ↓
 * [收尾段]（parts-end，可选）× 1   ← 尾
 * }</pre>
 *
 * <p><strong>N 可以是 0。</strong> {@code minSize} 的缺省值就是 0，读作「这一段可有可无」：不写 {@code parts-end}
 * 时是「一节都没有」，写了时是「收尾段紧贴起点」。旧版同样把 0 当作成立——它的 {@code MatchResult#isMatched}
 * 只看面、不看节数（{@code kasuminova/.../DynamicPattern.java:381-392}）；0.62.9–0.63.1 曾要求
 * {@code size > 0}，于是 {@code minSize: 0} 的机器永远不成型（勘测 X3，0.63.2 修复）。
 *
 * <h2>匹配算法要照抄的两个细节</h2>
 *
 * <p>见 {@link #matchesSize}：太长的结构是<strong>不匹配</strong>而不是截断接受；以及回退一格之后还要再验一次
 * 收尾段。第二条的理由旧版写在注释里（{@code Prevent overlapping structures}），而它防的是「两段结构互相
 * 吃掉对方的格子」——在世界里表现为机器偶尔不成型，极难查。
 *
 * <p><b>有一处不照抄：</b>旧版在数满 {@code maxSize} 的那一格直接判超限，连「这一格其实是合法收尾段」
 * 都不看，于是「{@code maxSize} 段 + 尾」这种合法结构反而失败（P07-06）。本版在那一格先辨认尾，命中就按
 * 实际重复数成立；真正的 {@code maxSize+1} 段仍然拒绝。这是修正，不是旧行为的复制。
 */
public final class DynamicPattern {

    private final String name;
    private final Set<Direction> faces;

    /** 最少重复几节才算成型。旧版默认 0，且它自己的读写器不给下限检查。 */
    private final int minSize;

    /** 最多重复几节。超过就<strong>不匹配</strong>，不是截断。 */
    private final int maxSize;

    /** 重复单元：沿 {@link #faces} 重复的那一节。 */
    private final MachinePattern part;

    /** 收尾单元，只出现一次；没有就是 {@code null}。 */
    @Nullable
    private final MachinePattern endPart;

    /** 第一节的起点偏移（相对控制器）。 */
    private final BlockPos startOffset;

    /** 每一节之间的步进偏移。 */
    private final BlockPos stepOffset;

    /**
     * 重复段按四个朝向预先转好的布局，下标就是 {@code rotationSteps & 3}。
     *
     * <p>{@code match} 与 {@link #expand(int, int)} 必须用<strong>同一份</strong>旋转后的段内位置，
     * 否则机器能不能成型会取决于它的段内布局是否旋转对称（见 {@link #rotatedSection}）。
     * 预先算好而不是每次匹配现转，是因为量长度发生在结构检查里，而 {@code rotateYCCW} 每次都要重建整张位置表。
     */
    private final MachinePattern[] partRotations;

    /** 收尾段的四个朝向；没有收尾段时整个数组为 {@code null}。 */
    @Nullable
    private final MachinePattern[] endPartRotations;

    public DynamicPattern(String name, Set<Direction> faces, int minSize, int maxSize,
                          MachinePattern part, @Nullable MachinePattern endPart,
                          BlockPos startOffset, BlockPos stepOffset) {
        if (minSize < 0 || maxSize < minSize) {
            throw new IllegalArgumentException("Dynamic pattern sizes require 0 <= minSize <= maxSize");
        }
        if (maxSize > 0 && BlockPos.ZERO.equals(stepOffset)) {
            throw new IllegalArgumentException("Repeating dynamic pattern '" + name + "' requires a nonzero step offset");
        }
        this.name = name;
        this.faces = Set.copyOf(faces);
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.part = part;
        this.endPart = endPart;
        this.startOffset = startOffset;
        this.stepOffset = stepOffset;
        this.partRotations = rotateAll(part);
        this.endPartRotations = endPart == null ? null : rotateAll(endPart);
    }

    private static MachinePattern[] rotateAll(MachinePattern section) {
        MachinePattern[] out = new MachinePattern[4];
        out[0] = section;
        for (int steps = 1; steps < 4; steps++) {
            out[steps] = rotatedSection(section, steps);
        }
        return out;
    }

    public String name() {
        return this.name;
    }

    /** 定义里声明的那几个延伸方向（控制器朝北时的字面值）。 */
    public Set<Direction> faces() {
        return this.faces;
    }

    public int minSize() {
        return this.minSize;
    }

    public int maxSize() {
        return this.maxSize;
    }

    public MachinePattern part() {
        return this.part;
    }

    @Nullable
    public MachinePattern endPart() {
        return this.endPart;
    }

    public BlockPos startOffset() {
        return this.startOffset;
    }

    public BlockPos stepOffset() {
        return this.stepOffset;
    }

    /** 这个模式的收尾段是否存在。 */
    public boolean hasEnd() {
        return this.endPart != null;
    }

    /**
     * 量出这个模式在世界里当前重复了几节，并给出实际匹配上的方向；节数不足 {@link #minSize} 时算不匹配。
     *
     * <p><strong>0 节是合法节数。</strong>一个动态结构可以「没有重复段」而仍然成立：它没有收尾段
     * （那一段本来就不存在），或者收尾段就压在起点上。旧版就是如此——{@code kasuminova/.../DynamicPattern.java:108}
     * 的 {@code if (size >= minSize)} 把 0 也收进结果表，而它的 {@code MatchResult#isMatched} 只看
     * {@code matchFacing != null}，不看节数。{@code minSize} 缺省是 0，所以「不写 minSize」在旧版读作
     * 「这段可有可无」。0.62.9–0.63.1 的 {@code match} 用 {@code size > best.size()} 选结果，而 {@code best}
     * 的初值正是 0 节，于是 0 节永远选不上：{@code minSize: 0} 的机器在世界里<strong>永远不成型</strong>
     * （勘测 X3「0 节动态段的语义反转」）。
     *
     * <p><strong>与旧版仍有的一处偏离：量失败（{@code -1}）不当作 0。</strong>旧版把「超过 maxSize」、
     * 「有收尾段却两处都找不到它」和「合法的 0 节」统统编码成 0，于是 {@code minSize: 0} 时前两种也会让机器
     * 以 0 节成型，{@code maxSize} 在那个配置下形同虚设。本版让 {@link #matchesSize} 用 {@code -1} 表示量失败，
     * {@code -1} 一律不成立：真正量到的 0 节成立，量失败不成立。代价是玩家乱搭（多余节、收尾段放错位置）时
     * 旧版会「缩成」0 节机器而本版不会——这是有意的，理由见 {@link #matchesSize}。
     *
     * @param level         世界
     * @param controller    控制器位置（所有偏移都相对它）
     * @param rotationSteps 控制器朝向相对 NORTH 的逆时针四分之一圈数（北 0、西 1、南 2、东 3）
     */
    public MatchResult match(Level level, BlockPos controller, int rotationSteps) {
        return inspect(level, controller, rotationSteps).match();
    }

    public record Inspection(MatchResult match, boolean unloaded) {}

    public Inspection inspect(Level level, BlockPos controller, int rotationSteps) {
        Set<Direction> actual = trueFacing(this.faces, rotationSteps);
        BlockPos start = rotate(this.startOffset, rotationSteps);
        BlockPos step = rotate(this.stepOffset, rotationSteps);

        // 段内布局必须与 expand 用同一套旋转：玩家照着预览（或建造投影）搭出来的结构，段里的方块已经
        // 跟着朝向转过了，这里再拿未旋转的段内偏移去比，量出来的节数就会偏小甚至一节都量不到。
        int index = rotationSteps & 3;
        MachinePattern part = this.partRotations[index];
        MachinePattern endPart = this.endPartRotations == null ? null : this.endPartRotations[index];

        // 多个方向都能匹配时取**最长**的那个，这是旧版的行为
        // （`DynamicPattern.matches` 末尾的 max(comparingInt(value))）。取最长而不是取第一个，
        // 是因为一台机器的长度是它自己的事实，不该取决于我们遍历方向的顺序。
        MatchResult best = MatchResult.NO_MATCH;
        boolean unloaded = false;
        for (Direction face : actual) {
            Measurement measured = measure(pos -> part.inspect(level, pos),
                    pos -> endPart == null ? new MachinePattern.Inspection(false, false) : endPart.inspect(level, pos),
                    controller, start, step);
            int size = measured.size();
            unloaded |= measured.unloaded();
            // -1 是量失败，不是「比 0 小的一点」：先挡掉它，再谈下限。
            if (size < 0 || size < this.minSize) {
                continue;
            }
            MatchResult candidate = new MatchResult(size, face);
            // 用 betterThan 而不是内联的 size > best.size()：0 节也必须能在「还没有候选」时胜出，
            // 而「还没有候选」与「候选是 0 节」在节数上无法区分，只能看 face 是否为空。
            if (candidate.betterThan(best)) {
                best = candidate;
            }
        }
        return new Inspection(best, !best.isMatched() && unloaded);
    }

    /**
     * {@link #match} 的核心，抽成「问一个位置成不成立」的谓词形式。
     *
     * <p><strong>为什么抽成谓词形式：</strong>这段算术是这个特性里最值得测、也最容易写错的部分（上限判定、
     * 收尾段的两处检查、回退一格防重叠），而它的自然形态需要一个 {@code Level}——本项目的离线复现器没有世界，
     * 于是这段逻辑会变成「测不到的代码」。把「世界」缩小成两个 {@code BlockPos -> boolean} 之后，复现器可以
     * 用一个位置集合造出任意布局，把每一种边界都验一遍。{@link #match} 自己只是把真实的
     * {@link MachinePattern} 匹配包成这两个谓词，不含算术。
     *
     * <p>量长度：从起点开始一节一节地数，直到不匹配、或者数到收尾段。
     *
     * <p>这是旧版 {@code DynamicPattern#matchesSize} 的对应实现，而下面几处<strong>必须照抄</strong>：
     *
     * <ol>
     *   <li>节数超过 {@code maxSize} 时返回 {@code -1}。<strong>过长的结构是不匹配，不是被截断接受</strong>——
     *       如果截断接受，玩家可以把线无限接长而机器仍然成型，长度上限就形同虚设。<strong>唯一的边界例外
     *       见下一项</strong>：数满上限的那一格如果本身就是合法收尾段，它不算第 {@code maxSize+1} 节。</li>
     *   <li><b>（P07-06 修正，旧版没有）</b>达到 {@code maxSize} 时，先辨认<strong>该锚点</strong>上的收尾段：
     *       命中就是「{@code maxSize} 节 + 尾」，返回 {@code maxSize}；不命中才返回 {@code -1}。
     *       旧版与 0.121.44 及以前的本版都在这里不看尾就失败，把一个合法的最大长度结构判成超限。</li>
     *   <li>遇到不匹配时，先看<strong>当前这一格</strong>是不是收尾段；是的话节数就是已经数过的那些。</li>
     *   <li>当前这一格不是收尾段时，<strong>回退一格再验一次</strong>。旧版给这一步写的理由是
     *       {@code Prevent overlapping structures}：两个模式相邻时，一方的收尾段可能正好落在另一方重复段的位置上，
     *       不验这一下会让两段结构互相吃掉格子——在世界里表现为「机器偶尔不成型」，极难查。</li>
     *   <li>收尾段也顶上第一节的位置时（回退到起点、收尾段在那里匹配），结果是 <b>0 节</b>且<strong>成立</strong>：
     *       一个只有收尾段的动态结构是合法的。</li>
     *   <li><strong>没有收尾段时数到 0 节也成立</strong>（返回 0，不是 -1）：作者没写 {@code parts-end} 又让
     *       {@code minSize} 取 0，就是在声明「这段可有可无」。旧版在这里同样返回 0，而它的 0 节是能成立的
     *       （{@code MatchResult#isMatched} 只看面）。这一条与上一条合起来，就是勘测 X3 要恢复的语义。</li>
     * </ol>
     *
     * <p>只有两种情形算<strong>量失败</strong>并返回 {@code -1}：数过 {@code maxSize} 节之后的那一格既不是
     * 收尾段、也不是重复段（真正的超限）；有收尾段、但当前格与回退格都不是它。旧版把这两种也返回 0，
     * 于是 {@code minSize: 0} 时它们会被当成「0 节成立」。本版不照抄这一点（理由见 {@link #match}）：
     * 量失败不是一种长度。
     *
     * @param partAt    某个位置是否是合法的重复段
     * @param endPartAt 某个位置是否是合法的收尾段（没有收尾段时恒为 false）
     * @return 节数（{@code >= 0}；0 是合法节数），或者 {@code -1} 表示量失败
     *
     * <p><strong>为什么是 {@code public} 而不是包私有：</strong>本项目的离线复现器在另一个包里，
     * 而这段算术<strong>必须</strong>被它覆盖到。它没有副作用、不碰世界、只读自己的两个字段与 {@code maxSize}，
     * 所以公开它不会给任何人留下破坏不变量的机会——换来的是一段否则永远测不到的算法。
     */
    public int matchesSize(java.util.function.Predicate<BlockPos> partAt,
                    java.util.function.Predicate<BlockPos> endPartAt,
                    BlockPos controller, BlockPos start, BlockPos step) {
        return measure(at -> new MachinePattern.Inspection(partAt.test(at), false),
                at -> new MachinePattern.Inspection(endPartAt.test(at), false), controller, start, step).size();
    }

    private record Measurement(int size, boolean unloaded) {}

    /** Shared arithmetic for public predicates and real world inspection; no second diagnostic length algorithm. */
    private Measurement measure(java.util.function.Function<BlockPos, MachinePattern.Inspection> partAt,
                                java.util.function.Function<BlockPos, MachinePattern.Inspection> endPartAt,
                                BlockPos controller, BlockPos start, BlockPos step) {
        // Public predicate callers may supply a step other than the validated declaration's step.
        if (this.maxSize > 0 && BlockPos.ZERO.equals(step)) return new Measurement(-1, false);
        BlockPos offset = controller.offset(start);
        int count = 0;

        while (true) {
            MachinePattern.Inspection part = partAt.apply(offset);
            if (part.matched()) {
                if (count >= this.maxSize) {
                    // P07-06（0.121.45）：数到上限的这一格仍可能是**合法的收尾段**，而不是第 maxSize+1 个
                    // 重复段。最小反例是「1 段 + 类型重叠的尾」：重复单元写任意熔炉、尾段也是熔炉但要求
                    // NBT Count=1，世界第一炉 Count=2、第二炉 Count=1。第二炉同时符合重复段候选，旧代码
                    // 在这里直接失败，收尾段检查根本没执行，于是合法的 maxSize 段结构不成型，而只把
                    // maxSize 调大 1 就能量到同样的 1 节。
                    //
                    // 所以先辨认**这个锚点**上的尾：命中就是「maxSize 节 + 尾」，按实际重复数 count 成立
                    // （expand(count) 把尾放在 start + count*step，正是这一格）。不命中才是真正的超限——
                    // maxSize+1 个重复段且没有合法尾，仍必须拒绝，绝不截断成 maxSize 或 0 节。
                    // 非上限位置的回退策略（下面那段 endPartAt(back)）不在这里复刻：上游的行为是
                    // 「重复段优先、最长匹配优先」，只有数不动了才回退去看上一格。
                    if (this.endPart != null) {
                        MachinePattern.Inspection end = endPartAt.apply(offset);
                        if (end.matched()) return new Measurement(count, false);
                        return new Measurement(-1, end.unloaded());
                    }
                    // 太长：不匹配，而不是截断接受。
                    return new Measurement(-1, false);
                }
                count++;
                offset = offset.offset(step);
                continue;
            }

            // 这一格不是重复段。它要么是收尾段本身，要么是收尾段之后的那一格。
            if (this.endPart != null) {
                MachinePattern.Inspection end = endPartAt.apply(offset);
                if (end.matched()) {
                    return new Measurement(count, part.unloaded() && count < this.minSize);
                }
                // 回退一格再验一次——旧版注释里写的 Prevent overlapping structures。
                BlockPos back = offset.subtract(step);
                MachinePattern.Inspection previousEnd = endPartAt.apply(back);
                if (previousEnd.matched()) {
                    return new Measurement(count - 1, part.unloaded() && count - 1 < this.minSize);
                }
                return new Measurement(-1, part.unloaded() || end.unloaded() || previousEnd.unloaded());
            }
            // 没有收尾段：数到几节就是几节，0 节也成立。这就是旧版这一支的返回（它同样 return size，可能为 0），
            // 也是 minSize 缺省为 0 时「这段可有可无」的字面含义。0.62.9–0.63.1 在这里返回 -1，把「没有重复段」
            // 当成了失败，于是不写 minSize 的动态机器在世界里永远不成型（勘测 X3）。
            // An unknown NEXT section is optional once a legal prefix exists. Before minSize it is required.
            return new Measurement(count, part.unloaded() && count < this.minSize);
        }
    }

    /**
     * 把「重复 {@code size} 节」展开成一个完整结构，供端口收集与建造预览使用。
     *
     * <p>这是旧版 {@code DynamicPattern#addPatternToBlockArray} 的对应实现。它产出的标签名就是作者在配方里
     * 引用的名字，格式逐字沿用旧版：
     *
     * <ul>
     *   <li>第 {@code i} 节的部件：{@code <selector-tag>_<模式名>_<i>}，节号从 0 开始</li>
     *   <li>收尾段的部件：{@code <selector-tag>_<模式名>_end}（没有节号——一个模式只有一个尾）</li>
     * </ul>
     *
     * <p><strong>没有 {@code selector-tag} 的部件不会得到标签</strong>，这也与旧版一致：它遍历的是
     * {@code getTaggedPositions()}，而只有打了标签的位置才在那个映射里。于是「结构里的普通方块」
     * 对配方而言仍然是无标签的，只有作者显式命名过的部件才能被配方指认。
     *
     * @param size          重复几节（{@code match} 量出来的数）
     * @param rotationSteps 控制器朝向相对 NORTH 的逆时针四分之一圈数；段内坐标、方块状态、规则与标签
     *                      都取构造时缓存的同一份已旋转段，避免二次旋转
     */
    public MachinePattern expand(int size, int rotationSteps) {
        MachinePattern.Builder builder = MachinePattern.builder();
        int turns = Math.floorMod(rotationSteps, 4);
        BlockPos offset = rotate(this.startOffset, turns);
        BlockPos step = rotate(this.stepOffset, turns);

        // 段内位置、方块状态、NBT 规则和标签必须来自**同一份已旋转段**。此前这里只把 entry 坐标转过去、
        // 却把原始 matcher 写回 builder，于是量长度一侧（match 用 partRotations）说 2 节、展开后的有效结构
        // 却仍要求北向的定义状态——非北向机器「按预览搭对了也不成型」。旋转信息已经在构造时缓存，
        // 这里不能再对坐标或 matcher 二次旋转。
        MachinePattern part = this.partRotations[turns];
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                offset = offset.offset(step);
            }
            addSection(builder, part, offset, index, false);
        }

        if (this.endPart != null) {
            // 收尾段锚在「第 size 节的位置」上：start + size * step。循环里 offset 停在最后一节，
            // 所以 size >= 1 时再走一步就是终点；size == 0 时循环一次都没跑、offset 还停在起点，
            // 终点就是起点本身（旧版 addPatternToBlockArray 在这里无条件 +step，把 0 节的收尾段多推了一格）。
            // 量长度一侧从来是「收尾段顶在起点上算 0 节」，两侧对齐之后，玩家照预览搭出来的 0 节机器才既量得到、
            // 也拼得成——这是 X3 的另一半。
            BlockPos endAnchor = size == 0 ? offset : offset.offset(step);
            addSection(builder, this.endPartRotations[turns], endAnchor, 0, true);
        }
        return builder.build();
    }

    private void addSection(MachinePattern.Builder builder, MachinePattern section, BlockPos offset,
                            int index, boolean end) {
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : section.positions().entrySet()) {
            // section 已经按控制器朝向转过；这里只做「段内坐标 + 段起点」，不能再调 rotate。
            BlockPos pos = entry.getKey().offset(offset);
            builder.add(pos, entry.getValue());
            builder.rule(pos, section.rule(entry.getKey()));
            String selectorTag = section.tag(entry.getKey());
            if (selectorTag != null) {
                // 这个部件在定义里被作者命名过，于是它在**这一段**里得到一个带段号的标签。
                builder.tag(pos, end ? endTag(selectorTag, this.name)
                        : segmentTag(selectorTag, this.name, index));
            }
        }
    }

    /**
     * 把定义里的朝向换算成世界里实际要验的朝向，对应旧版 {@code DynamicPattern#getTrueFacing}。
     *
     * <p><strong>上、下不参与旋转</strong>，这是旧版明确的行为（它在那两个值上直接返回原值）。
     * 理由很自然：绕 Y 轴旋转不影响「上」和「下」这两个方向本身。
     */
    public static Set<Direction> trueFacing(Set<Direction> declared, int rotationSteps) {
        Set<Direction> out = new LinkedHashSet<>();
        for (Direction face : declared) {
            if (face == Direction.UP || face == Direction.DOWN) {
                out.add(face);
                continue;
            }
            Direction rotated = face;
            for (int i = 0; i < rotationSteps; i++) {
                rotated = rotated.getCounterClockWise();
            }
            out.add(rotated);
        }
        return out;
    }

    /**
     * 把一段结构绕 Y 轴逆时针旋转 {@code rotationSteps} 步，位置与标签一起转（走
     * {@link MachinePattern#rotateYCounterClockwise()}）。
     *
     * <p><strong>为什么匹配与展开必须共用它：</strong>{@link #expand(int, int)} 用
     * {@link #rotate(BlockPos, int)} 把段内每个位置转到世界方向——玩家照着建造预览搭出来的就是这个布局，
     * 机器成型之后 {@code selector-tag} 认的也是这些位置。如果量长度时拿<strong>未旋转</strong>的段内位置去比，
     * 那么朝向不是北的机器会量出偏小的节数（更常见的是第一节就不过、直接不成型），
     * 而段内布局恰好旋转对称时又完全正常——这个缺陷因此只在不对称的段上显形。
     *
     * <p>旧版把动态模式预先生成四个朝向放进 {@code BlockArrayCache}（
     * {@code BlockArrayCache#buildDynamicPatternCache} 对每个水平朝向各调一次 {@code BlockArray#rotateYCCW}），
     * {@code DynamicPattern#matches} 再按控制器朝向取用；此处是同一个语义。
     *
     * @param rotationSteps 逆时针四分之一圈数；按 4 取模（转四步等于没转）
     */
    public static MachinePattern rotatedSection(MachinePattern section, int rotationSteps) {
        MachinePattern out = section;
        for (int i = 0; i < (rotationSteps & 3); i++) {
            out = out.rotateYCounterClockwise();
        }
        return out;
    }

    /**
     * 把一个偏移按朝向旋转，对应旧版 {@code DynamicPattern#rotatePosTo}（它从 NORTH 起逐次逆时针转）。
     *
     * <p>用的是与 {@link MachinePattern#rotateYCounterClockwise} 同一个变换 {@code (x, y, z) -> (z, y, -x)}——
     * 两处必须是同一个，否则结构会成型而它的动态部分错位。
     */
    public static BlockPos rotate(BlockPos pos, int rotationSteps) {
        BlockPos rotated = pos;
        for (int i = 0; i < rotationSteps; i++) {
            rotated = new BlockPos(rotated.getZ(), rotated.getY(), -rotated.getX());
        }
        return rotated;
    }

    /**
     * 一次匹配的结果。
     *
     * @param size 数到的节数（{@code >= 0}）；{@code 0} 是<strong>合法节数</strong>，不是「不匹配」——
     *             {@code minSize: 0} 的动态结构允许一节都没有
     * @param face 实际匹配上的那个面；{@code null} 表示没有匹配（{@link #NO_MATCH}）
     */
    public record MatchResult(int size, @Nullable Direction face) {

        public static final MatchResult NO_MATCH = new MatchResult(0, null);

        /**
         * 是否成立。
         *
         * <p>判据是<strong>有面</strong>，与旧版 {@code MatchResult#isMatched}（{@code return matchFacing != null;}）
         * 逐字一致。这与 {@link #NO_MATCH} 的表示法互相咬合：没有匹配才没有面，而 0 节是可以有面的。
         * 0.62.9–0.63.1 这里写的是 {@code size > 0 && face != null}，正是 X3 那条「0 节永不成立」的另一半。
         */
        public boolean isMatched() {
            return this.face != null;
        }

        /**
         * 这个候选是否比现有结果更值得采纳：还没有任何候选时它赢（<strong>包括 0 节</strong>），
         * 否则取更长的那一个。
         *
         * <p>旧版在结果表上取 {@code max(comparingInt(value))}；本版把「取最长」放在这里，
         * 是因为它同时是「0 节能否被选中」的判据，而这件事必须能被离线复现器直接断言。
         */
        public boolean betterThan(MatchResult other) {
            return other.face == null || this.size > other.size();
        }
    }

    /**
     * 一个模式在某台已成型机器上的<strong>定格</strong>：哪一面、几节。
     *
     * <p>为什么必须定格并存进 NBT：动态结构的长度在世界上是变化的，而配方对某一段的绑定
     * （{@code selector-tag} 里的段号）必须在成型那一刻确定下来。如果每一 tick 重新量一次，
     * 玩家加长一节就会让配方指向别的段，正在跑的合成会静默换料。旧版把这三个值存进 NBT 也是这个原因
     * （{@code TileMultiblockMachineController.java:1435/1490}）。
     */
    public record Status(String patternName, int size, Direction face) {
    }

    /**
     * 第 {@code index} 节的标签名，对应旧版 {@code DynamicPattern#addPatternToBlockArray} 里的格式串
     * {@code "%s_%s_%d"}（{@code 标签_模式名_段号}）。
     *
     * <p><strong>这个格式是硬约定</strong>：作者写的配方里就是靠它指认「第几段」的，改一个字符就与旧版不兼容，
     * 也会让作者既有的配方失效。段号从 0 开始，与旧版的循环变量一致。
     */
    public static String segmentTag(String selectorTag, String patternName, int index) {
        return selectorTag + "_" + patternName + "_" + index;
    }

    /**
     * 收尾段的标签名，对应旧版的 {@code "%s_%s_end"}。
     *
     * <p>收尾段不带段号：一个模式只有一个尾，加段号只会让作者多写一个没意义的数字。
     */
    public static String endTag(String selectorTag, String patternName) {
        return selectorTag + "_" + patternName + "_end";
    }
}
