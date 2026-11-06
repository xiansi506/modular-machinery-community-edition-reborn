package com.reborn.modularmachinery.machine;

import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The hatches belonging to one formed machine, bucketed by family and direction.
 *
 * <p>The original collected its structure's {@code MachineComponent}s the same way and then let the recipe
 * requirement engine draw from whichever component could serve a requirement. M3 keeps the simple recipe model
 * of M1 but routes it through these ports instead of the controller's own slots: items come from item input
 * hatches, FE from energy input hatches, and results go to item output hatches.
 *
 * <p>Fluid hatches are collected and fully functional as storage (pipes can fill and drain them), but the
 * current recipe type has no fluid fields — those arrive with the requirement engine.
 *
 * <h2>M6d-b: the smart-interface values travel with the ports</h2>
 *
 * <p>{@code interface_number_input} is a requirement that reads a number the controller holds rather than one a
 * hatch holds, and the recipe lifecycle has no per-controller context object to pass it through
 * ({@code RecipeCraftingContext} is M2's deliberate omission — the modifiers are method parameters instead). The
 * per-craft object the lifecycle does already pass to every requirement is this one, and it is rebuilt from the
 * controller on every structure check, so the value source rides along here.
 *
 * <p>It is a {@link java.util.function.Supplier} rather than a snapshot because a player may edit the value
 * while a craft is running: the requirement re-reads it on each check, which is what reading a live block
 * entity's field did in the original.
 */
public final class HatchCollection {

    public static final HatchCollection EMPTY = new HatchCollection(List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            () -> SmartInterfaceValueSource.NONE, () -> null);

    /**
     * 「这个端口没有标签」的哨兵值。
     *
     * <p>为什么不用 {@code null}：{@code List} 与 {@code List.copyOf} 都<strong>接受</strong> {@code null} 元素，
     * 所以一个漏打了标签的端口会安静地混进去，直到某次筛选把它当成「没标签」而<strong>错误地</strong>交给一个
     * 无标签的需求。一个显式哨兵让这种遗漏无处可藏——{@link #withTag} 里两个分支都只认这两种值之一。
     */
    private static final String UNTAGGED = "\u0000untagged";

    private final List<IItemHandler> itemInputs;
    private final List<IItemHandler> itemOutputs;
    private final List<IFluidHandler> fluidInputs;
    private final List<IFluidHandler> fluidOutputs;
    private final List<IEnergyStorage> energyInputs;
    private final List<IEnergyStorage> energyOutputs;

    /**
     * 气体端口（Mekanism 化学），波 5.3 起。
     *
     * <p><b>为什么它住在流体仓上而不是一种新方块。</b>旧版就是这么做的：{@code HybridGasTank extends
     * HybridTank} 的宿主是 {@code TileFluidTank} / {@code GuiContainerFluidHatch}，
     * 全部 {@code @Optional.Method(modid = "mekanism")}。所以「气体端口」在本版等于
     * 「流体仓多一种能力」，而这条路的形状与 D8（方块只能在构造期注册）不冲突。
     *
     * <p>这两个桶**只有在装了 Mekanism 时才可能非空**：往里放端口的映射器由那个模块注册，
     * 没装它的时候 {@link Builder#gasPorts} 找不到任何映射器，桶就是空的。
     *
     * <p><b>装的是 {@code Object}，不是 {@code IGasHandler}（勘测 X4）。</b>主模组不引用任何可选模组的
     * API 类型：{@link com.reborn.modularmachinery.compat.ModCompat} 的隔离契约要求「没装那个模组时，
     * 它的类一个都不加载」，而字段类型在类校验时就会被解析——只要这个字段还写着 {@code IGasHandler}，
     * 一个手工放进去的端口（任何第二个注册端口映射器的兼容模块都会造成这种情况）就会在**每次结构成型**时
     * 抛 {@code NoClassDefFoundError}。这里只当**不透明对象**搬运，解释它的是拥有这个家族的兼容模块
     * （Mekanism 侧 {@code GasIo} 自己转型）。
     */
    private final List<Object> gasInputs;
    private final List<Object> gasOutputs;

    /**
     * 每个端口来自哪个打了标签的结构位置，与上面六个桶<strong>逐个对应</strong>。
     *
     * <p>这是旧版 {@code ProcessingComponent} 携带 {@code ComponentSelectorTag} 的对应层
     * （{@code ProcessingComponent.java:41}），也是 {@code selector-tag} 能起作用的全部依据：
     * 需求通过标签筛选端口，靠的就是「每个端口知道自己属于哪个部件」。
     *
     * <p>与端口桶分开存而不是把端口包成一个带标签的记录，是为了不惊动已有的六个访问器——
     * {@link #itemInputs()} 这些方法是整个配方引擎的读取面，它们的形状不变，标签只是多出来的一层筛选。
     */
    private final List<String> itemInputTags;
    private final List<String> itemOutputTags;
    private final List<String> fluidInputTags;
    private final List<String> fluidOutputTags;
    private final List<String> energyInputTags;
    private final List<String> energyOutputTags;

    /** 与气体端口逐个对应，规则同上面六个。 */
    private final List<String> gasInputTags;
    private final List<String> gasOutputTags;

    /**
     * 每个端口属于哪个「输入组」，与端口桶、标签列表<strong>三向对应</strong>（A2，勘测 §4 第 115 行）。
     *
     * <p>{@code -1} 是这个端口的「通用」标记，不是「没有组」：旧版
     * {@code TileColorableMachineComponent#getGroupId} 对不可分组仓与未启用隔离的仓一律答 {@code -1}
     * （{@code :105-108}），而 {@code TileMultiblockMachineController#updateComponents}
     * （{@code :757-790}）把 {@code -1} 的组件放进 {@code generalComponents}，再加进<strong>每一个</strong>
     * 候选组。所以通用端口对每组都可见，却不定义任何组——候选组只来自非负组号。
     *
     * <p>为什么要和 {@code selector-tag} 分开存：两者筛的是不同的东西。标签按<strong>结构位置</strong>分组，
     * 组号按<strong>玩家分配</strong>分组，旧版里它们同时生效（{@code getComponentsFor} 先按标签收，
     * 再按组桶分，{@code RecipeCraftingContext.java:212-235}）。合并成一个字段就没法表达
     * 「同一段里的两个仓分属两组」。
     */
    private final List<Integer> itemInputGroups;
    private final List<Integer> itemOutputGroups;
    private final List<Integer> fluidInputGroups;
    private final List<Integer> fluidOutputGroups;
    private final List<Integer> energyInputGroups;
    private final List<Integer> energyOutputGroups;
    private final List<Integer> gasInputGroups;
    private final List<Integer> gasOutputGroups;

    /**
     * Where a smart-interface requirement reads its value. Never {@code null}: {@link
     * SmartInterfaceValueSource#NONE} is the honest answer for a collection built without a controller, and it
     * answers {@code null} for every type — which is the original's "no interface of that type found" case.
     */
    private final java.util.function.Supplier<SmartInterfaceValueSource> smartInterfaces;

    /**
     * The formed machine's definition, for the one thing a requirement needs from it: the interface type's own
     * {@code notequal} message. It is a supplier for the same reason the values are — the machine a controller
     * forms can change between structure checks.
     */
    private final java.util.function.Supplier<MachineDefinition> machine;

    private HatchCollection(List<IItemHandler> itemInputs, List<IItemHandler> itemOutputs,
                            List<IFluidHandler> fluidInputs, List<IFluidHandler> fluidOutputs,
                            List<IEnergyStorage> energyInputs, List<IEnergyStorage> energyOutputs,
                            List<Object> gasInputs, List<Object> gasOutputs,
                            List<String> itemInputTags, List<String> itemOutputTags,
                            List<String> fluidInputTags, List<String> fluidOutputTags,
                            List<String> energyInputTags, List<String> energyOutputTags,
                            List<String> gasInputTags, List<String> gasOutputTags,
                            List<Integer> itemInputGroups, List<Integer> itemOutputGroups,
                            List<Integer> fluidInputGroups, List<Integer> fluidOutputGroups,
                            List<Integer> energyInputGroups, List<Integer> energyOutputGroups,
                            List<Integer> gasInputGroups, List<Integer> gasOutputGroups,
                            java.util.function.Supplier<SmartInterfaceValueSource> smartInterfaces,
                            java.util.function.Supplier<MachineDefinition> machine) {
        this.itemInputs = List.copyOf(itemInputs);
        this.itemOutputs = List.copyOf(itemOutputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.energyInputs = List.copyOf(energyInputs);
        this.energyOutputs = List.copyOf(energyOutputs);
        this.gasInputs = List.copyOf(gasInputs);
        this.gasOutputs = List.copyOf(gasOutputs);
        // 断言而不是信任：一个长度不匹配的标签列表会让「端口 i 的标签」整体错位，
        // 而错位是静默的——它只会让某个需求读到另一个部件的端口。与其在世界里查，不如在这里就炸。
        this.itemInputTags = checkedTags(itemInputTags, this.itemInputs.size(), "itemInputs");
        this.itemOutputTags = checkedTags(itemOutputTags, this.itemOutputs.size(), "itemOutputs");
        this.fluidInputTags = checkedTags(fluidInputTags, this.fluidInputs.size(), "fluidInputs");
        this.fluidOutputTags = checkedTags(fluidOutputTags, this.fluidOutputs.size(), "fluidOutputs");
        this.energyInputTags = checkedTags(energyInputTags, this.energyInputs.size(), "energyInputs");
        this.energyOutputTags = checkedTags(energyOutputTags, this.energyOutputs.size(), "energyOutputs");
        this.gasInputTags = checkedTags(gasInputTags, this.gasInputs.size(), "gasInputs");
        this.gasOutputTags = checkedTags(gasOutputTags, this.gasOutputs.size(), "gasOutputs");
        // 第三组与端口、标签同一条理由：错位是静默的，只会让某个组读到另一个仓的端口。
        this.itemInputGroups = checkedGroups(itemInputGroups, this.itemInputs.size(), "itemInputs");
        this.itemOutputGroups = checkedGroups(itemOutputGroups, this.itemOutputs.size(), "itemOutputs");
        this.fluidInputGroups = checkedGroups(fluidInputGroups, this.fluidInputs.size(), "fluidInputs");
        this.fluidOutputGroups = checkedGroups(fluidOutputGroups, this.fluidOutputs.size(), "fluidOutputs");
        this.energyInputGroups = checkedGroups(energyInputGroups, this.energyInputs.size(), "energyInputs");
        this.energyOutputGroups = checkedGroups(energyOutputGroups, this.energyOutputs.size(), "energyOutputs");
        this.gasInputGroups = checkedGroups(gasInputGroups, this.gasInputs.size(), "gasInputs");
        this.gasOutputGroups = checkedGroups(gasOutputGroups, this.gasOutputs.size(), "gasOutputs");
        this.smartInterfaces = smartInterfaces;
        this.machine = machine;
    }

    /** 所有标签与组保留，仅把存储换成同一次事务的副本。 */
    public record MagicEntry(com.reborn.modularmachinery.magic.MagicPort port, String tag) {}
    private List<MagicEntry> magic = List.of();
    private boolean transactional;
    private java.util.Map<Integer, InputGroupBinding.Key> isolatedGroups = java.util.Map.of();
    private java.util.Map<InputGroupBinding.Key, Integer> isolatedNumbers = java.util.Map.of();
    public InputGroupBinding.Key isolatedGroupKey(int group) { return isolatedGroups.get(group); }
    public Integer isolatedGroupNumber(InputGroupBinding.Key key) { return isolatedNumbers.get(key); }
    public java.util.Map<Integer, InputGroupBinding.Key> isolatedGroupKeys() { return isolatedGroups; }

    private HatchCollection withIsolatedGroups(java.util.Map<Integer, InputGroupBinding.Key> groups) {
        isolatedGroups = java.util.Map.copyOf(groups);
        java.util.Map<InputGroupBinding.Key, Integer> reverse = new java.util.HashMap<>();
        groups.forEach((number, key) -> {
            Integer previous = reverse.putIfAbsent(key, number);
            if (previous != null && !previous.equals(number))
                throw new IllegalArgumentException("Isolated input identity has multiple local addresses");
        });
        isolatedNumbers = java.util.Map.copyOf(reverse);
        return this;
    }

    private HatchCollection copyIsolatedGroups(HatchCollection from) {
        isolatedGroups = from.isolatedGroups;
        isolatedNumbers = from.isolatedNumbers;
        return this;
    }
    public boolean isTransactionView() { return transactional; }
    private HatchCollection withTransactionFlag(boolean flag) { transactional = flag; return this; }
    private HatchCollection withMagic(List<MagicEntry> values) { this.magic = List.copyOf(values); return this; }
    public List<com.reborn.modularmachinery.magic.MagicPort> magicPorts() { return magic.stream().map(MagicEntry::port).toList(); }

    public HatchCollection transactionView(com.reborn.modularmachinery.recipe.ResourceTransaction transaction) {
        return new HatchCollection(
                itemInputs.stream().map(transaction::items).toList(), itemOutputs.stream().map(transaction::items).toList(),
                fluidInputs.stream().map(transaction::fluids).toList(), fluidOutputs.stream().map(transaction::fluids).toList(),
                energyInputs.stream().map(transaction::energy).toList(), energyOutputs.stream().map(transaction::energy).toList(),
                gasInputs.stream().map(transaction::opaque).toList(), gasOutputs.stream().map(transaction::opaque).toList(),
                itemInputTags, itemOutputTags, fluidInputTags, fluidOutputTags, energyInputTags, energyOutputTags,
                gasInputTags, gasOutputTags, itemInputGroups, itemOutputGroups, fluidInputGroups, fluidOutputGroups,
                energyInputGroups, energyOutputGroups, gasInputGroups, gasOutputGroups, smartInterfaces, machine).withMagic(magic.stream().map(e -> new MagicEntry(e.port().transactionView(transaction), e.tag())).toList()).withTransactionFlag(true).copyIsolatedGroups(this);
    }

    private static List<String> checkedTags(List<String> tags, int ports, String bucket) {
        if (tags.size() != ports) {
            throw new IllegalStateException("HatchCollection." + bucket + " has " + ports + " ports but "
                    + tags.size() + " tags; the two lists are positional and must line up");
        }
        return List.copyOf(tags);
    }

    private static List<Integer> checkedGroups(List<Integer> groups, int ports, String bucket) {
        if (groups.size() != ports) {
            throw new IllegalStateException("HatchCollection." + bucket + " has " + ports + " ports but "
                    + groups.size() + " groups; the two lists are positional and must line up");
        }
        return List.copyOf(groups);
    }

    public List<IItemHandler> itemInputs() {
        return this.itemInputs;
    }

    public List<IItemHandler> itemOutputs() {
        return this.itemOutputs;
    }

    public List<IFluidHandler> fluidInputs() {
        return this.fluidInputs;
    }

    public List<IFluidHandler> fluidOutputs() {
        return this.fluidOutputs;
    }

    public List<IEnergyStorage> energyInputs() {
        return this.energyInputs;
    }

    public List<IEnergyStorage> energyOutputs() {
        return this.energyOutputs;
    }

    /**
     * 气体（Mekanism 化学体系）**输入**端口。
     *
     * <p>与物品/流体/能源那六个桶同一套规则：桶由端口来自哪个方向决定（{@link Builder#gasPorts} 按
     * {@code HatchKind#isInput()} 分桶），标签由结构位置决定，{@link #withTag} 按同一规则筛选——
     * 于是一条带 {@code selector-tag} 的气体需求只看得到它那一段的端口，与别的需求类型完全一致。
     *
     * <p><b>只有装了 Mekanism 时才可能非空。</b>往这里放端口的映射器由那个模块注册；没装它时
     * {@link Builder#gasPorts} 找不到任何映射器，桶就是空的。而**空桶的语义是安全的**：
     * {@code GasIo} 对空列表一律答「没有 / 抽不到 / 灌不进」，于是一条气体配方在没有气体端口的机器上
     * 表现为**开工失败**（并给出 {@code craftcheck.failure.gas.input}），不是崩溃，也不是静默成功。
     */
    public List<Object> gasInputs() {
        return this.gasInputs;
    }

    /** 气体**输出**端口。规则同 {@link #gasInputs()}。 */
    public List<Object> gasOutputs() {
        return this.gasOutputs;
    }

    /**
     * 只看属于 {@code selectorTag} 那个部件的端口；{@code null} 表示只看<strong>没有标签</strong>的端口。
     *
     * <p>这是 {@code selector-tag} 生效的地方，也是旧版 {@code RecipeCraftingContext} 里那行
     * {@code tag.equals(typeComponent.getTag())}（{@code RecipeCraftingContext.java:222}）的对应实现。
     *
     * <p><strong>为什么「没有标签」是「只看无标签的端口」而不是「全都看」</strong>：旧版的比较是
     * {@code tag.equals(...)}，而 {@code ComponentSelectorTag} 把 {@code null}/空串归一成一个空标签
     * （{@code ComponentSelectorTag.java:26-29}）。于是<strong>无标签需求与无标签部件互相匹配</strong>，
     * 而一个指定了标签的需求<strong>看不到</strong>无标签的端口。这个区分正是这个特性的意义所在：
     * 装配线上「原料进第 3 段」必须真的只进第 3 段，而不是「随便进哪个输入仓都行」。
     *
     * <p>六个桶各自筛选，未知的桶（智能接口与机器定义）原样带过去——它们不是位置性的端口，
     * 没有标签可言，也从不参与筛选。
     *
     * @param selectorTag 要筛的标签名，或 {@code null} 表示只看无标签的端口
     */
    public HatchCollection withTag(@javax.annotation.Nullable String selectorTag) {
        // 旧版 getComponentsFor：未指定标签时不增加限制。
        if (selectorTag == null || selectorTag.isEmpty()) return this;
        String wanted = (selectorTag == null || selectorTag.isEmpty()) ? UNTAGGED : selectorTag;
        return new HatchCollection(
                filter(this.itemInputs, this.itemInputTags, wanted),
                filter(this.itemOutputs, this.itemOutputTags, wanted),
                filter(this.fluidInputs, this.fluidInputTags, wanted),
                filter(this.fluidOutputs, this.fluidOutputTags, wanted),
                filter(this.energyInputs, this.energyInputTags, wanted),
                filter(this.energyOutputs, this.energyOutputTags, wanted),
                filter(this.gasInputs, this.gasInputTags, wanted),
                filter(this.gasOutputs, this.gasOutputTags, wanted),
                filterTags(this.itemInputTags, wanted),
                filterTags(this.itemOutputTags, wanted),
                filterTags(this.fluidInputTags, wanted),
                filterTags(this.fluidOutputTags, wanted),
                filterTags(this.energyInputTags, wanted),
                filterTags(this.energyOutputTags, wanted),
                filterTags(this.gasInputTags, wanted),
                filterTags(this.gasOutputTags, wanted),
                filterGroups(this.itemInputTags, this.itemInputGroups, wanted),
                filterGroups(this.itemOutputTags, this.itemOutputGroups, wanted),
                filterGroups(this.fluidInputTags, this.fluidInputGroups, wanted),
                filterGroups(this.fluidOutputTags, this.fluidOutputGroups, wanted),
                filterGroups(this.energyInputTags, this.energyInputGroups, wanted),
                filterGroups(this.energyOutputTags, this.energyOutputGroups, wanted),
                filterGroups(this.gasInputTags, this.gasInputGroups, wanted),
                filterGroups(this.gasOutputTags, this.gasOutputGroups, wanted),
                this.smartInterfaces,
                this.machine).withMagic(magic.stream().filter(e -> wanted.equals(e.tag())).toList()).withTransactionFlag(transactional).copyIsolatedGroups(this);
    }

    /**
     * 只看属于输入组 {@code groupId} 的端口，外加所有通用端口（组号 {@code -1}）。
     *
     * <p>这是 A2 的核心：旧版 {@code TileMultiblockMachineController#updateComponents:757-790}
     * 把通用组件并进<strong>每一个</strong>组，而 {@code canStartCrafting:502-549} 逐组试整条配方，
     * 成功才锁定那一组。所以这里的过滤必须保留通用端口——否则玩家把两个仓分到 1 组、其余仓留在通用，
     * 1 组就会看不见那些通用仓。
     *
     * <p>{@code groupId} 为 {@code -1} 时只留下通用端口（旧版的 {@code generalComponents} 视图，
     * {@code getComponentsFor} 里组键 {@code -1} 的那一桶）。
     *
     * <p>输出与能量端口永远带 {@code -1}（{@link Builder#add} 按 {@code canGroupInput} 归一），
     * 所以它们对每组都可见——这正是旧版输出侧不受组隔离影响的原因
     * （{@code canStartCrafting(false, ...)} 只看输出的第一条路径）。
     */
    public HatchCollection withGroup(int groupId) {
        return new HatchCollection(
                filterByGroup(this.itemInputs, this.itemInputGroups, groupId),
                filterByGroup(this.itemOutputs, this.itemOutputGroups, groupId),
                filterByGroup(this.fluidInputs, this.fluidInputGroups, groupId),
                filterByGroup(this.fluidOutputs, this.fluidOutputGroups, groupId),
                filterByGroup(this.energyInputs, this.energyInputGroups, groupId),
                filterByGroup(this.energyOutputs, this.energyOutputGroups, groupId),
                filterByGroup(this.gasInputs, this.gasInputGroups, groupId),
                filterByGroup(this.gasOutputs, this.gasOutputGroups, groupId),
                filterByGroup(this.itemInputTags, this.itemInputGroups, groupId),
                filterByGroup(this.itemOutputTags, this.itemOutputGroups, groupId),
                filterByGroup(this.fluidInputTags, this.fluidInputGroups, groupId),
                filterByGroup(this.fluidOutputTags, this.fluidOutputGroups, groupId),
                filterByGroup(this.energyInputTags, this.energyInputGroups, groupId),
                filterByGroup(this.energyOutputTags, this.energyOutputGroups, groupId),
                filterByGroup(this.gasInputTags, this.gasInputGroups, groupId),
                filterByGroup(this.gasOutputTags, this.gasOutputGroups, groupId),
                filterByGroup(this.itemInputGroups, this.itemInputGroups, groupId),
                filterByGroup(this.itemOutputGroups, this.itemOutputGroups, groupId),
                filterByGroup(this.fluidInputGroups, this.fluidInputGroups, groupId),
                filterByGroup(this.fluidOutputGroups, this.fluidOutputGroups, groupId),
                filterByGroup(this.energyInputGroups, this.energyInputGroups, groupId),
                filterByGroup(this.energyOutputGroups, this.energyOutputGroups, groupId),
                filterByGroup(this.gasInputGroups, this.gasInputGroups, groupId),
                filterByGroup(this.gasOutputGroups, this.gasOutputGroups, groupId),
                this.smartInterfaces,
                this.machine).withMagic(magic).withTransactionFlag(transactional).copyIsolatedGroups(this);
    }

    /**
     * 这台机器上出现过的输入组号，升序去重；没有带组端口时返回 {@code [0]}。
     *
     * <p>「没有带组端口时是组 0」不是随手挑的默认值，而是旧版 {@code updateComponents} 的分支：
     * 找不到任何带组组件时 {@code foundComponents.put(0L, generalComponents)}。也就是说一台从未用过
     * 组隔离的机器本来就在组 0 里跑——不这么补，{@code withGroup} 会筛出空集合，配方永远开不了工。
     *
     * <p>只扫输入桶：{@code canGroupInput} 只允许物品输入与流体输入，输出与能量端口的组号恒为
     * {@code -1}，扫它们不会得到任何新组号。
     */
    public List<Integer> groupIds() {
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
        collectGroupIds(this.itemInputGroups, ids);
        collectGroupIds(this.fluidInputGroups, ids);
        collectGroupIds(this.gasInputGroups, ids);
        return ids.isEmpty() ? List.of(0) : List.copyOf(ids);
    }

    private static void collectGroupIds(List<Integer> groups, java.util.Set<Integer> into) {
        for (int group : groups) {
            if (group >= 0) {
                into.add(group);
            }
        }
    }

    /** 这个位置上的端口在筛选后是否仍然可见。 */
    private static boolean keeps(String tag, String wanted) {
        return tag.equals(wanted);
    }

    /**
     * 每个端口一行的人类可读描述，带上它所属的 {@code selector-tag}（没有标签的显示为 {@code -}）。
     *
     * <p>为什么需要它：`selector-tag` 是<strong>看不见</strong>的。结构成型之后，玩家在世界上看到的只是一堆
     * 仓口方块，没有任何东西告诉他某个仓属于 `input_line_0` 还是 `input_line_1`——而配方的成败完全取决于
     * 这个看不见的名字。实测时就卡在这里：一台成型、配方已加载的机器不开工，而唯一能看出原因的方式是
     * 把「哪个仓带哪个标签」打出来。
     *
     * <p>顺序与 {@link #withTag} 筛选前的桶顺序一致，便于对照。
     */
    public List<String> describePorts() {
        List<String> out = new ArrayList<>();
        describeBucket(out, "item input", this.itemInputs.size(), this.itemInputTags, this.itemInputGroups);
        describeBucket(out, "item output", this.itemOutputs.size(), this.itemOutputTags, this.itemOutputGroups);
        describeBucket(out, "fluid input", this.fluidInputs.size(), this.fluidInputTags, this.fluidInputGroups);
        describeBucket(out, "fluid output", this.fluidOutputs.size(), this.fluidOutputTags, this.fluidOutputGroups);
        describeBucket(out, "energy input", this.energyInputs.size(), this.energyInputTags, this.energyInputGroups);
        describeBucket(out, "energy output", this.energyOutputs.size(), this.energyOutputTags, this.energyOutputGroups);
        describeBucket(out, "gas input", this.gasInputs.size(), this.gasInputTags, this.gasInputGroups);
        describeBucket(out, "gas output", this.gasOutputs.size(), this.gasOutputTags, this.gasOutputGroups);
        return List.copyOf(out);
    }

    private static void describeBucket(List<String> out, String family, int ports, List<String> tags,
                                       List<Integer> groups) {
        for (int i = 0; i < ports; i++) {
            String tag = tags.get(i);
            int group = groups.get(i);
            out.add(family + " #" + i + " tag=" + (UNTAGGED.equals(tag) ? "-" : tag)
                    + " group=" + (group < 0 ? "general" : Integer.toString(group)));
        }
    }

    private static <T> List<T> filter(List<T> ports, List<String> tags, String wanted) {
        List<T> kept = new ArrayList<>(ports.size());
        for (int i = 0; i < ports.size(); i++) {
            if (keeps(tags.get(i), wanted)) {
                kept.add(ports.get(i));
            }
        }
        return kept;
    }

    private static List<String> filterTags(List<String> tags, String wanted) {
        List<String> kept = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (keeps(tag, wanted)) {
                kept.add(tag);
            }
        }
        return kept;
    }

    /** 组号{@code -1}的端口（通用端口）在每一组里都可见；其余端口只在自己的那一组里可见。 */
    private static boolean keepsGroup(int group, int wanted) {
        return group < 0 || group == wanted;
    }

    private static <T> List<T> filterByGroup(List<T> values, List<Integer> groups, int wanted) {
        List<T> kept = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (keepsGroup(groups.get(i), wanted)) {
                kept.add(values.get(i));
            }
        }
        return kept;
    }

    private static List<Integer> filterGroups(List<String> tags, List<Integer> groups, String wanted) {
        List<Integer> kept = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            if (keeps(tags.get(i), wanted)) {
                kept.add(groups.get(i));
            }
        }
        return kept;
    }

    /**
     * Where a smart-interface requirement reads the value it compares against: the controller this collection was
     * gathered from.
     *
     * <p>Called on every check rather than captured once, so an edit made while a craft runs is seen at once —
     * the original read the live {@code SmartInterfaceData} object the same way.
     */
    public SmartInterfaceValueSource smartInterfaces() {
        SmartInterfaceValueSource source = this.smartInterfaces.get();
        return source == null ? SmartInterfaceValueSource.NONE : source;
    }

    /** The value stored for one declared interface type, or {@code null} when there is none. */
    @javax.annotation.Nullable
    public Float smartInterfaceValue(String type) {
        return smartInterfaces().valueOf(type);
    }

    /**
     * The formed machine's declared interface type of that name, or {@code null} when the machine declares no
     * such type — the original's {@code DynamicMachine#getSmartInterfaceType}, reached the same way the value is.
     */
    @javax.annotation.Nullable
    public SmartInterfaceType declaredSmartInterface(String type) {
        MachineDefinition definition = this.machine.get();
        return definition == null ? null : definition.smartInterface(type);
    }

    public boolean isEmpty() {
        return this.itemInputs.isEmpty() && this.itemOutputs.isEmpty() && this.fluidInputs.isEmpty()
                && this.fluidOutputs.isEmpty() && this.energyInputs.isEmpty() && this.energyOutputs.isEmpty()
                && this.gasInputs.isEmpty() && this.gasOutputs.isEmpty() && this.magic.isEmpty();
    }

    public int hatchCount() {
        return this.itemInputs.size() + this.itemOutputs.size() + this.fluidInputs.size()
                + this.fluidOutputs.size() + this.energyInputs.size() + this.energyOutputs.size()
                + this.gasInputs.size() + this.gasOutputs.size() + this.magic.size();
    }

    /** Total FE currently held by the structure's energy input hatches. */
    public long storedEnergy() {
        long total = 0L;
        for (IEnergyStorage storage : this.energyInputs) {
            total = com.reborn.modularmachinery.energy.EnergyAccess.add(total, com.reborn.modularmachinery.energy.EnergyAccess.stored(storage));
        }
        return total;
    }

    /** Total FE the structure's energy input hatches can hold. */
    public long storedEnergyCapacity() {
        long total = 0L;
        for (IEnergyStorage storage : this.energyInputs) {
            total = com.reborn.modularmachinery.energy.EnergyAccess.add(total, com.reborn.modularmachinery.energy.EnergyAccess.capacity(storage));
        }
        return total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final java.util.Map<Integer, InputGroupBinding.Key> isolatedGroups = new java.util.HashMap<>();
        public Builder isolatedGroup(int number, InputGroupBinding.Key key) {
            if (number < 0) throw new IllegalArgumentException("Isolated inputs require a nonnegative local address");
            InputGroupBinding.Key previous = isolatedGroups.putIfAbsent(number, key);
            if (previous != null && !previous.equals(key))
                throw new IllegalArgumentException("Two isolated sources share a local address");
            return this;
        }
        private final List<MagicEntry> magic = new ArrayList<>();
        public Builder addMagicPort(com.reborn.modularmachinery.magic.MagicPort port, String tag) {
            magic.add(new MagicEntry(port, tag == null || tag.isEmpty() ? UNTAGGED : tag)); return this;
        }

        private final List<IItemHandler> itemInputs = new ArrayList<>();
        private final List<IItemHandler> itemOutputs = new ArrayList<>();
        private final List<IFluidHandler> fluidInputs = new ArrayList<>();
        private final List<IFluidHandler> fluidOutputs = new ArrayList<>();
        private final List<IEnergyStorage> energyInputs = new ArrayList<>();
        private final List<IEnergyStorage> energyOutputs = new ArrayList<>();
        private final List<Object> gasInputs = new ArrayList<>();
        private final List<Object> gasOutputs = new ArrayList<>();

        /** 与上面六个桶逐个对应，见 {@link HatchCollection#itemInputTags} 的说明。 */
        private final List<String> itemInputTags = new ArrayList<>();
        private final List<String> itemOutputTags = new ArrayList<>();
        private final List<String> fluidInputTags = new ArrayList<>();
        private final List<String> fluidOutputTags = new ArrayList<>();
        private final List<String> energyInputTags = new ArrayList<>();
        private final List<String> energyOutputTags = new ArrayList<>();
        private final List<String> gasInputTags = new ArrayList<>();
        private final List<String> gasOutputTags = new ArrayList<>();

        /** 与端口桶、标签逐个对应，见 {@link HatchCollection#itemInputGroups} 的说明。 */
        private final List<Integer> itemInputGroups = new ArrayList<>();
        private final List<Integer> itemOutputGroups = new ArrayList<>();
        private final List<Integer> fluidInputGroups = new ArrayList<>();
        private final List<Integer> fluidOutputGroups = new ArrayList<>();
        private final List<Integer> energyInputGroups = new ArrayList<>();
        private final List<Integer> energyOutputGroups = new ArrayList<>();
        private final List<Integer> gasInputGroups = new ArrayList<>();
        private final List<Integer> gasOutputGroups = new ArrayList<>();

        /** Defaults to "nothing", so a collection built without a controller keeps behaving as it always did. */
        private SmartInterfaceValueSource smartInterfaces = SmartInterfaceValueSource.NONE;

        /** Defaults to "no machine", for the same reason. */
        private java.util.function.Supplier<MachineDefinition> machine = () -> null;

        /**
         * Where smart-interface values come from — the controller the structure was gathered from. The single
         * argument is the very object whose own accessor the requirement needs, which is the original's access
         * path with the one intermediate block removed.
         */
        public Builder smartInterfaces(SmartInterfaceValueSource source) {
            this.smartInterfaces = source == null ? SmartInterfaceValueSource.NONE : source;
            return this;
        }

        /** The formed machine's definition, for the interface type's own {@code notequal} message. */
        public Builder machine(MachineDefinition definition) {
            this.machine = () -> definition;
            return this;
        }

        /**
         * Routes one hatch's storage into the bucket its kind and direction call for, under the
         * {@code selector-tag} of the structure position it was found at.
         *
         * @param selectorTag the position's {@code selector-tag}, or {@code null} when the position has none —
         *                    which is every position of a structure that declares no tags at all
         */
        public Builder add(com.reborn.modularmachinery.block.HatchKind kind,
                           IItemHandler items, IFluidHandler fluids, IEnergyStorage energy,
                           @javax.annotation.Nullable String selectorTag) {
            return add(kind, items, fluids, energy, selectorTag, -1);
        }

        /**
         * {@link #add} 带输入组号的版本（A2）。
         *
         * <p>组号在这里<strong>归一</strong>：{@code canGroupInput()} 为 {@code false} 的仓口恒记为
         * {@code -1}（通用），不管调用者传了什么。这是旧版 {@code getGroupId} 的写法，也是唯一能保证
         * 「输出仓不能被隔离」的地方——UI 只能拦住玩家，拦不住将来某个调用点。
         */
        public Builder add(com.reborn.modularmachinery.block.HatchKind kind,
                           IItemHandler items, IFluidHandler fluids, IEnergyStorage energy,
                           @javax.annotation.Nullable String selectorTag, int groupId) {
            String tag = (selectorTag == null || selectorTag.isEmpty()) ? UNTAGGED : selectorTag;
            int group = kind.canGroupInput() ? Math.max(-1, groupId) : -1;
            switch (kind.family()) {
                case ITEM -> {
                    if (items != null) {
                        (kind.isInput() ? this.itemInputs : this.itemOutputs).add(items);
                        (kind.isInput() ? this.itemInputTags : this.itemOutputTags).add(tag);
                        (kind.isInput() ? this.itemInputGroups : this.itemOutputGroups).add(group);
                    }
                }
                case FLUID -> {
                    if (fluids != null) {
                        (kind.isInput() ? this.fluidInputs : this.fluidOutputs).add(fluids);
                        (kind.isInput() ? this.fluidInputTags : this.fluidOutputTags).add(tag);
                        (kind.isInput() ? this.fluidInputGroups : this.fluidOutputGroups).add(group);
                    }
                }
                case ENERGY -> {
                    if (energy != null) {
                        (kind.isInput() ? this.energyInputs : this.energyOutputs).add(energy);
                        (kind.isInput() ? this.energyInputTags : this.energyOutputTags).add(tag);
                        (kind.isInput() ? this.energyInputGroups : this.energyOutputGroups).add(group);
                    }
                }
            }
            return this;
        }

        /** {@link #add} for a position that declares no {@code selector-tag}. */
        public Builder add(com.reborn.modularmachinery.block.HatchKind kind,
                           IItemHandler items, IFluidHandler fluids, IEnergyStorage energy) {
            return add(kind, items, fluids, energy, null, -1);
        }

        /**
         * C4-II（0.92.0，统一总线）：把一个额外的**流体端口**入流体桶——供 {@code extraFluidPort()}
         * 路径使用（ME 总线的流体半边：物品半边已按 ITEM 家族入桶，流体半边从这里补）。
         * 输入性由调用者给（跟随该仓口种类的输入性），标签与组号与主端口一致。
         */
        public Builder addFluidPort(IFluidHandler fluids, boolean input,
                                    @javax.annotation.Nullable String selectorTag, int groupId) {
            String tag = (selectorTag == null || selectorTag.isEmpty()) ? UNTAGGED : selectorTag;
            int group = Math.max(-1, groupId);
            (input ? this.fluidInputs : this.fluidOutputs).add(fluids);
            (input ? this.fluidInputTags : this.fluidOutputTags).add(tag);
            (input ? this.fluidInputGroups : this.fluidOutputGroups).add(group);
            return this;
        }

        public Builder addItemPort(IItemHandler items, boolean input, String selectorTag, int groupId) {
            String tag = selectorTag == null || selectorTag.isEmpty() ? UNTAGGED : selectorTag;
            (input ? itemInputs : itemOutputs).add(items);
            (input ? itemInputTags : itemOutputTags).add(tag);
            (input ? itemInputGroups : itemOutputGroups).add(Math.max(-1, groupId));
            return this;
        }

        public Builder addGasPort(Object gas, boolean input, String selectorTag, int groupId) {
            String tag = selectorTag == null || selectorTag.isEmpty() ? UNTAGGED : selectorTag;
            (input ? gasInputs : gasOutputs).add(gas);
            (input ? gasInputTags : gasOutputTags).add(tag);
            (input ? gasInputGroups : gasOutputGroups).add(Math.max(-1, groupId));
            return this;
        }

        /**
         * 把某个方块实体的**兼容能力**也收成端口（波 5.3 起）。
         *
         * <p>与 {@link #add} 分开，是因为它走的是**完全不同的机制**：那个方法拿的是主模组自己知道的三样东西
         * （物品/流体/能源），而这里问的是「有没有哪个兼容模块愿意对这个实体提供它自己的能力」——
         * 主模组并不知道那是什么能力。所以本方法只把实体交给
         * {@link com.reborn.modularmachinery.compat.ModCompat#portHandlers}，由模块自己回答。
         *
         * <p>没装那个模组时这里什么都不会发生（没有映射器），于是气体桶保持为空——而空桶的语义是安全的：
         * {@code GasIo} 对空列表一律答「没有」，一条气体配方于是**开工失败**而不是崩溃或静默成功。
         *
         * <p>问的是 {@code "gas"} 这个家族，而桶里存的是**不透明对象**：主模组不认识
         * {@code IGasHandler}，也不该认识（勘测 X4）。家族名就是主模组与兼容层之间的全部契约。
         *
         * @param entity      结构位置上的方块实体
         * @param input       这个位置是输入方向还是输出方向（由仓口种类决定，与物品/流体同一条规则）
         * @param selectorTag 该位置的 {@code selector-tag}
         */
        public Builder gasPorts(Object entity, boolean input, @javax.annotation.Nullable String selectorTag) {
            return gasPorts(entity, input, selectorTag, -1);
        }

        /**
         * {@link #gasPorts} 带输入组号的版本（A2）。
         *
         * <p>气体端口挂在流体仓上，而流体输入仓是可以分组的（{@code canGroupInput}），所以它必须跟着宿主仓
         * 一起被组过滤——旧版 {@code HybridGasTank} 就是 {@code TileFluidTank} 的一部分，组号也是那个
         * 组件的组号。{@code input == false} 的那一半在调用点上恒传 {@code -1}（输出仓不可分组）。
         */
        public Builder gasPorts(Object entity, boolean input, @javax.annotation.Nullable String selectorTag,
                                int groupId) {
            String tag = (selectorTag == null || selectorTag.isEmpty()) ? UNTAGGED : selectorTag;
            int group = Math.max(-1, groupId);
            for (Object handler : com.reborn.modularmachinery.compat.ModCompat.portHandlers(entity, "gas")) {
                (input ? this.gasInputs : this.gasOutputs).add(handler);
                (input ? this.gasInputTags : this.gasOutputTags).add(tag);
                (input ? this.gasInputGroups : this.gasOutputGroups).add(group);
            }
            return this;
        }

        /**
         * Registers a bare handler as a port. Used to fold the controller's own slots and buffer in as fallback
         * ports so the recipe engine sees a single uniform set of ports.
         *
         * <p>These are always <b>untagged</b>, and that is deliberate rather than incidental: the controller is
         * not a structure position, so it belongs to no {@code selector-tag} and must stay invisible to every
         * tagged requirement. Folding it into a tagged bucket would let a recipe that asked for "the input at
         * line segment 3" be satisfied by the controller's own slot.
         */
        public Builder addItemHandler(IItemHandler handler, boolean input) {
            if (handler != null) {
                (input ? this.itemInputs : this.itemOutputs).add(handler);
                (input ? this.itemInputTags : this.itemOutputTags).add(UNTAGGED);
                (input ? this.itemInputGroups : this.itemOutputGroups).add(-1);
            }
            return this;
        }

        public Builder addFluidHandler(IFluidHandler handler, boolean input) {
            if (handler != null) {
                (input ? this.fluidInputs : this.fluidOutputs).add(handler);
                (input ? this.fluidInputTags : this.fluidOutputTags).add(UNTAGGED);
                (input ? this.fluidInputGroups : this.fluidOutputGroups).add(-1);
            }
            return this;
        }

        public Builder addEnergyStorage(IEnergyStorage storage, boolean input) {
            if (storage != null) {
                (input ? this.energyInputs : this.energyOutputs).add(storage);
                (input ? this.energyInputTags : this.energyOutputTags).add(UNTAGGED);
                (input ? this.energyInputGroups : this.energyOutputGroups).add(-1);
            }
            return this;
        }

        public HatchCollection build() {
            return new HatchCollection(this.itemInputs, this.itemOutputs, this.fluidInputs, this.fluidOutputs,
                    this.energyInputs, this.energyOutputs, this.gasInputs, this.gasOutputs,
                    this.itemInputTags, this.itemOutputTags, this.fluidInputTags, this.fluidOutputTags,
                    this.energyInputTags, this.energyOutputTags, this.gasInputTags, this.gasOutputTags,
                    this.itemInputGroups, this.itemOutputGroups, this.fluidInputGroups, this.fluidOutputGroups,
                    this.energyInputGroups, this.energyOutputGroups, this.gasInputGroups, this.gasOutputGroups,
                    () -> this.smartInterfaces, this.machine).withMagic(this.magic).withIsolatedGroups(this.isolatedGroups);
        }
    }
}
