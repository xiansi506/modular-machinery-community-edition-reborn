package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.util.RandomSource;

/**
 * One entry of a machine recipe's {@code requirements} array.
 *
 * <p>The original drove crafting through {@code ComponentRequirement} subclasses with three lifecycle hooks,
 * and that shape is what decides when things happen:
 *
 * <ul>
 *   <li>{@code startCrafting} consumes <b>inputs</b> — item and fluid inputs are taken <b>when the craft
 *       begins</b>, not when it completes;</li>
 *   <li>{@code doIOTick} runs every tick for the {@code PerTick} family — energy and per-tick fluids;</li>
 *   <li>{@code finishCrafting} inserts <b>outputs</b> at the end, rolling each output's {@code chance}
 *       independently.</li>
 * </ul>
 *
 * <p>Before finishing, the original re-ran the space check as if every output were guaranteed
 * ({@code ResultChance.GUARANTEED}), so a craft cannot start ending when the result would not fit.
 * {@link #canSatisfy} therefore always tests the full amount and ignores {@code chance}.
 *
 * <h2>Parallelism</h2>
 *
 * <p>The original's "parallelism" is <b>not</b> a thread pool: it is a number {@code N} on the recipe, and
 * every requirement moves {@code N} copies of its own amount per settlement
 * ({@code RequirementItem#doItemIOInternal}, {@code ComponentRequirement.Parallelizable}). The same shape is
 * reproduced here:
 *
 * <ul>
 *   <li>{@link #setParallelism} is called once, when the craft starts, with the value
 *       {@link #parallelLimit} computed. It stays fixed for the whole craft — the original also froze it for
 *       the duration of a craft and only revisited it in {@code canRestartCrafting}.</li>
 *   <li>{@link #canSatisfy} and {@link #apply} / {@link #tick} then work on
 *       {@code per-copy amount * parallelism}.</li>
 *   <li>{@link #parallelLimit} answers the original's
 *       {@code ComponentRequirement.Parallelizable#getMaxParallelism}: the largest {@code N} at or below the
 *       requested ceiling that this requirement could actually serve. The recipe takes the minimum over every
 *       parallelisable requirement, exactly as {@code RecipeCraftingContext#getMaxParallelism} did.</li>
 * </ul>
 *
 * <p>A requirement may opt out with {@link #setParallelizeUnaffected}, which is the original's
 * {@code isParallelizeUnaffected}: such a requirement is left out of the minimum and always works on one copy
 * (a catalyst is the original's example).
 */
public abstract class MachineRequirement {

    /**
     * The ceiling {@link #parallelLimit} returns when nothing bounds a requirement. The original had no such
     * constant: it passed in {@code activeRecipe.getMaxParallelism()}, which is itself capped by the machine's
     * {@code max-parallelism} (default 2048, {@code Config.maxMachineParallelism}). This value only has to be
     * larger than any machine ceiling, and is {@code Integer.MAX_VALUE / 4} so that the multiplications inside
     * the requirement classes cannot overflow.
     */
    public static final int UNBOUNDED_PARALLELISM = Integer.MAX_VALUE / 4;

    /**
     * 旧版的排序权重（{@code ComponentRequirement} 的三个常量）：排序器从 {@code sortId} 里减去这些数，
     * 于是「输入需求越多、越偏能源的配方」在同一 priority 层里越靠前。
     *
     * <p>数字照抄旧版 {@code ComponentRequirement.java:33-35}，不要"顺手"调整：它们决定的是配方选取顺序，
     * 不是界面上的美观。
     */
    public static final int PRIORITY_WEIGHT_ENERGY = 50_000_000;
    public static final int PRIORITY_WEIGHT_FLUID = 100;
    public static final int PRIORITY_WEIGHT_ITEM = 50_000;

    private final IOType ioType;
    private final boolean perTick;

    /** How many copies the current craft settles per requirement. Set once, at craft start. */
    private int parallelism = 1;

    /** When true this requirement never scales with parallelism, the original's {@code parallelizeUnaffected}. */
    private boolean parallelizeUnaffected;
    private com.google.gson.JsonObject scriptOptions = new com.google.gson.JsonObject();
    private boolean triggered;
    public com.google.gson.JsonObject scriptOptions() { return scriptOptions.deepCopy(); }
    public boolean option(String key) { return scriptOptions.has(key) && scriptOptions.get(key).getAsBoolean(); }
    public String scriptKey(String key) { return scriptOptions.has(key) ? scriptOptions.get(key).getAsString() : ""; }
    public int triggerTime() { return scriptOptions.has("trigger-time") ? scriptOptions.get("trigger-time").getAsInt() : 0; }
    public boolean triggered() { return triggered; }
    public void setTriggered(boolean value) { triggered = value; }
    public void setScriptOptions(com.google.gson.JsonObject options) {
        for (String key : java.util.List.of("script-checker", "script-tooltip")) {
            if (options.has(key) && options.get(key).isJsonPrimitive() && options.get(key).getAsJsonPrimitive().isString())
                MachineRequirementParser.validateText(options.get(key).getAsString(), "options." + key, "requirement");
        }
        MachineRequirementParser.validateText(options.toString(), "options", "requirement");
        for (String key : java.util.List.of("script-checker", "script-tooltip")) if (options.has(key)
                && (!options.get(key).isJsonPrimitive() || !options.get(key).getAsJsonPrimitive().isString()))
            throw new com.google.gson.JsonParseException(key + " must be a string");
        if (options.has("script-modifiers")) {
            if (!options.get("script-modifiers").isJsonArray()) throw new com.google.gson.JsonParseException("script-modifiers must be an array of callback keys");
            for (var key : options.getAsJsonArray("script-modifiers")) if (!key.isJsonPrimitive() || !key.getAsJsonPrimitive().isString() || key.getAsString().isBlank())
                throw new com.google.gson.JsonParseException("script-modifiers entries must be non-empty strings");
        }
        for (String key : java.util.List.of("consume-durability", "fuel-time", "ignore-output-check", "trigger-repeatable")) {
            if (options.has(key) && (!options.get(key).isJsonPrimitive() || !options.get(key).getAsJsonPrimitive().isBoolean()))
                throw new com.google.gson.JsonParseException(key + " must be true or false");
        }
        if (options.has("trigger-time")) {
            var tick = options.get("trigger-time");
            com.reborn.modularmachinery.util.JsonNumbers.integer(tick, 0, Integer.MAX_VALUE, "trigger-time");
            if (perTick) throw new com.google.gson.JsonParseException("trigger-time is for one-shot requirements; remove perTick");
        }
        boolean itemOnly = options.has("script-checker") || options.has("script-modifiers")
                || options.has("consume-durability") || options.has("fuel-time");
        if (itemOnly && !(this instanceof ItemRequirement)) throw new com.google.gson.JsonParseException("Item callbacks, fuel and durability require an item requirement");
        if ((options.has("consume-durability") || options.has("fuel-time")) && ioType != IOType.INPUT)
            throw new com.google.gson.JsonParseException("Durability and fuel requirements are inputs only");
        scriptOptions = options.deepCopy();
        if (option("fuel-time") && option("consume-durability")) throw new com.google.gson.JsonParseException("fuel-time and consume-durability cannot be combined");
    }

    protected MachineRequirement(IOType ioType, boolean perTick) {
        this.ioType = ioType;
        this.perTick = perTick;
    }

    public IOType ioType() {
        return this.ioType;
    }

    public boolean perTick() {
        return this.perTick;
    }

    /**
     * 旧版 {@code ComponentRequirement#getSortingWeight}：这条输入需求在配方排序里"值多少钱"。
     *
     * <p>只有 {@link IOType#INPUT} 的需求会被 {@link MachineRecipe#buildWeight()} 扣掉；输出不参与，
     * 因为旧版比较器遇到输出直接 {@code continue}。默认 0 让没有自己权重的需求（催化剂、数字接口）
     * 不改变顺序，与旧版基类一致。
     */
    public int sortingWeight() {
        return 0;
    }

    /** Inputs consumed once, when the craft starts. */
    public final boolean isStartPhase() {
        return this.ioType == IOType.INPUT && !this.perTick && triggerTime() <= 0;
    }

    /** Requirements evaluated every tick of the craft. */
    public final boolean isTickPhase() {
        return this.perTick;
    }

    /** Outputs produced once, when the craft completes. */
    public final boolean isFinishPhase() {
        return this.ioType == IOType.OUTPUT && !this.perTick && triggerTime() <= 0;
    }

    // ------------------------------------------------------------------ parallelism

    public int parallelism() {
        return this.parallelism;
    }

    /**
     * Fixes how many copies the rest of the craft settles. The original required exactly this: a parallelisable
     * requirement "should strictly adhere to that number of parallels when checking the requirement or the
     * work, and you should not increase or decrease the number of parallels on your own". An unaffected
     * requirement ignores the call and stays on one copy, as the original's own setter did.
     */
    public void setParallelism(int parallelism) {
        // The original's setParallelism did nothing for an unaffected requirement; its field stayed at 1 so that
        // "one copy" was the only thing the rest of the craft could ever observe (ComponentRequirement.java:484-489).
        if (!this.parallelizeUnaffected) {
            this.parallelism = Math.max(1, parallelism);
        }
    }

    public boolean isParallelizeUnaffected() {
        return this.parallelizeUnaffected;
    }

    public void setParallelizeUnaffected(boolean unaffected) {
        this.parallelizeUnaffected = unaffected;
        if (unaffected) {
            this.parallelism = 1;
        }
    }

    /**
     * The largest number of copies at or below {@code ceiling} that this requirement can supply (inputs) or
     * hold (outputs). {@code 0} when it cannot even serve one copy.
     *
     * <p>Unlike {@link #canSatisfy}, which is an all-or-nothing check, this has to answer "how many" — that is
     * the whole content of the original's {@code getMaxParallelism} implementations, each of which fed a
     * multiplier into a simulation routine and divided the served amount by the per-copy amount.
     */
    public int parallelLimit(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        return this.parallelizeUnaffected ? 1 : UNBOUNDED_PARALLELISM;
    }

    /** Whether this requirement takes part in the recipe's parallelism minimum. */
    public final boolean isParallelizable() {
        return !this.parallelizeUnaffected;
    }

    /** Which modifier target this requirement belongs to, the original's {@code requirementType}. */
    public abstract RecipeModifier.Target modifierTarget();

    /**
     * 这条需求是**哪一种**需求，也就是它在 {@link RequirementTypes} 里的那一行。
     *
     * <p>它决定两件事，而这两件事在改动前各有各的硬编码：
     *
     * <ul>
     *   <li>{@link MachineRequirementParser#write} 往包里写哪个种类字符串。改动前是一条
     *       {@code instanceof} 链，所以一个兼容模块的需求**写不出去**（见 {@link RequirementType}）；</li>
     *   <li>「一条需求对象回头找到它的解析器」，于是客户端读回来的是同一个种类，而不是「看起来像」的另一种。</li>
     * </ul>
     *
     * <p>返回的一定是注册表里那**一个**实例（实现类写 {@code return RequirementTypes.ITEM;} 这种形式），
     * 所以它是身份而不是描述：{@code byId(type().id()) == type()} 恒成立。
     */
    public abstract RequirementType type();

    // ------------------------------------------------------------------ lifecycle

    /**
     * 这条需求绑定的结构部件（旧版的 {@code selector-tag}），没绑定时为 {@code null}。
     *
     * <p>旧版把它放在 {@code ComponentRequirement} 上（{@code ComponentRequirement.java:39}），由配方 JSON 的
     * {@code selector-tag} 字段设入（{@code MachineRecipe.java:474}），运行时用于在候选端口里
     * <strong>筛出属于那个部件的那几个</strong>（{@code RecipeCraftingContext.java:222} 的
     * {@code tag.equals(typeComponent.getTag())}）。
     *
     * <p>没有它，「原料送进装配线第 3 段」就无法表达——需求只能看到整个结构的全部输入仓。
     * 所以它不是可选的装饰：它是 {@code dynamic-patterns} 这个特性能够<strong>用起来</strong>的前提。
     *
     * <p>{@code setSelectorTag} 存在而不是走构造器，理由与旧版相同：需求有十来个子类、每个都有自己的工厂方法，
     * 把标签塞进每个构造器会改动所有调用点，而它的语义是「在这条需求建好之后挂上去的一个限定」。
     */
    @javax.annotation.Nullable
    private String selectorTag;

    /** 这条需求绑定的部件标签，或 {@code null}（表示只作用于没有标签的端口）。 */
    @javax.annotation.Nullable
    public String selectorTag() {
        return this.selectorTag;
    }

    /** 绑定部件标签；空串与 {@code null} 同义，都表示「没有标签」。 */
    public void setSelectorTag(@javax.annotation.Nullable String selectorTag) {
        this.selectorTag = (selectorTag == null || selectorTag.isEmpty()) ? null : selectorTag;
    }

    /**
     * 这条需求能看到的那部分端口。
     *
     * <p>配方生命周期的四个阶段都必须走这里，而不是直接用传进来的 {@code ports}——
     * 否则「筛端口」会在某个阶段被漏掉，表现为「能开工但料消耗到了别的仓」这类极难查的不一致。
     */
    public HatchCollection visiblePorts(HatchCollection ports) {
        return ports.withTag(this.selectorTag);
    }

    /** Pure check: could this requirement be met right now? Never mutates the ports. */
    public abstract boolean canSatisfy(HatchCollection ports);

    /** 修改器感知的多态入口，兼容模块不再经过主模组的 instanceof 白名单。 */
    public boolean canSatisfy(HatchCollection ports, RecipeModifiers modifiers) {
        return canSatisfy(ports);
    }

    /** 在事务副本上预留保证数量；只读需求默认只检查条件。 */
    public boolean reserve(HatchCollection ports, RecipeModifiers modifiers) {
        return canSatisfy(ports, modifiers);
    }

    /**
     * Why {@link #canSatisfy} answers {@code false}, as a translation key, or {@code null} when this requirement
     * has nothing to say.
     *
     * <p>The original's {@code canStartCrafting} returned a {@code CraftCheck} that carried this message and the
     * controller printed it. This projection's lifecycle is a boolean, so the message needs its own door — and it
     * is only ever asked after {@code canSatisfy} has already answered {@code false}, which keeps the two from
     * disagreeing (see {@code MachineRecipe#startFailure}).
     *
     * <p>The method keeps its historical name but also supplies refusal messages after a tick-phase reserve.
     * Requirements without a specific reason may return null; the recipe provides a generic tick fallback.
     */
    @javax.annotation.Nullable
    public String startFailure(HatchCollection ports) {
        return null;
    }

    /** Consumes (input) or produces (output) once. Used for the start and finish phases. */
    public boolean apply(HatchCollection ports, RandomSource random) {
        return true;
    }

    public boolean apply(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return apply(ports, random);
    }

    /** Runs once per tick, for per-tick requirements only. */
    public boolean tick(HatchCollection ports, RandomSource random) {
        return true;
    }

    public boolean tick(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return tick(ports, random);
    }

    /** Short human-readable summary, used by JEI. */
    public abstract String describe();

    /**
     * 这条需求在 **JEI 页面上**该占哪种格子。
     *
     * <p><b>为什么由需求自己报，而不是让 JEI 去 instanceof。</b>JEI 的绘制代码在主模组里，而气体需求
     * （{@code GasRequirement}）住在兼容层、**只在装了 Mekanism 的进程里存在**。让 JEI 写
     * {@code instanceof GasRequirement} 就等于让主模组引用那个类型——没装 Mekanism 时会缺类。
     * 所以反过来：需求自己声明它要哪种格子，绘制侧只认这个枚举（一个纯主模组的类型）。
     *
     * <p>这也顺手修掉一个**真缺陷**：原来的分派是「能源 → 流体 → 其余都当物品」，于是气体需求
     * 掉进物品那条路、随后被 cast 成 {@code ItemRequirement}——打开那个机器的 JEI 页就崩。
     * 现在「其余」不再是默认桶，物品必须是**明确**的物品。
     */
    public enum TankKind {
        /** 不是罐：占物品格。 */
        NONE,
        /** 真正的流体：JEI 用自己的流体渲染器画。 */
        FLUID,
        /** Chemical atlas sprite and tank shell; text is a fallback for an unavailable sprite. */
        GAS, MAGIC
    }

    /** 这条需求在 JEI 里占哪种格子；默认物品格。 */
    public TankKind tankKind() {
        return TankKind.NONE;
    }

    /** Optional animated block-atlas visual; no chemical client classes cross this common API. */
    @javax.annotation.Nullable public net.minecraft.resources.ResourceLocation tankTexture() { return null; }
    public int tankTint() { return 0xFFFFFFFF; }
}
