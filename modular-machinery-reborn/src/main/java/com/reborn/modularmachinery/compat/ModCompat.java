package com.reborn.modularmachinery.compat;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.recipe.RequirementTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 兼容层的接入点：判断哪些兼容模块可用，然后让它们注册自己。
 *
 * <h2>什么时候注册，以及为什么是这个时机</h2>
 *
 * <p>在 **{@code FMLCommonSetupEvent}** 里，也就是**所有模组都构造完之后**。理由有两条，一条是技术上的、
 * 一条是纪律上的：
 *
 * <ul>
 *   <li>「这个模组装了没」这个问题在 mod 构造阶段**能不能问**，我**未查到依据**：本机 fmlcore 没有源码，
 *       {@code ModList.setLoadedMods} 是包内方法，它相对构造期的顺序读不出来。构造期读一个还没填好的表，
 *       后果是兼容类型**静默地永不注册**——这种 bug 只在装了那个模组的玩家身上出现，而开发者本机永远复现不了。
 *       放到 {@code FMLCommonSetupEvent} 就没有这个不确定性：那个事件按定义在所有构造之后触发。</li>
 *   <li>需求类型**不是** Forge 注册表内容，所以这一步不违 D8（方块只能在构造期注册、注册表随后冻结）。
 *       这也是为什么兼容层的**方块**入口至今没写：那需要构造期就拿到模块清单，是另一条路。</li>
 * </ul>
 *
 * <h2>两道门，不是重复</h2>
 *
 * <ol>
 *   <li>{@link CompatModule#requiresModid()}：注册**之前**筛掉依赖没装的模块（这个类在做）。</li>
 *   <li>{@link RequirementTypes#removeUnavailable}：注册**之后**把所有声明了 {@code requiresModid} 而模组不在场的
 *       类型摘掉，照旧版 {@code IntegrationTypeHelper.filterModIdRequirementTypes}（{@code :45-58}）。</li>
 * </ol>
 *
 * <p>第二道门存在的意义正是「第一道门可能判断错」。只有第一道门时，一次时机错误就会在注册表里留下一种
 * 永远不可用的需求；有第二道门，最坏情况只是它被晚一点摘掉。
 *
 * <h2>其它模组怎么把模块交进来</h2>
 *
 * <p>在对方自己的 {@code @Mod} 构造器里调用 {@link #registerModule(CompatModule)}。不能等到
 * {@code FMLCommonSetupEvent} 再用事件发现：模块的 {@link CompatModule#registerCapabilities} 要在
 * {@code RegisterCapabilitiesEvent} 之前进入清单，而那个事件更早；本模组构造期又无法可靠地问
 * {@code ModList}。所以入口是**对方主动调用**，而不是本模组扫描。
 *
 * <p>两个拒绝是刻意的：同一个 id 第二次加入会抛 {@link IllegalStateException}（内置模块也算），
 * 清单封口后再加入同样会抛。后者尤其重要——晚加入的模块会静默漏掉能力注册，那种缺口只在特定加载
 * 顺序下出现，日志里没有、游戏里却少了端口。
 */
public final class ModCompat {

    /**
     * 已注册的兼容模块，**只增不减**。
     *
     * <p>不在每次 {@code FMLCommonSetupEvent} 时清空：那个事件在客户端可能因资源重载而多次触发，
     * 而重复注册同一种需求会被「重复 id 即报错」挡住。模块的集合在一次启动里不会变，
     * 所以「已经处理过」这个事实记在这里，比每次重新判断更诚实。
     */
    private static final Map<ResourceLocation, CompatModule> REGISTERED = new LinkedHashMap<>();

    /**
     * 其它模组通过 {@link #registerModule(CompatModule)} 交进来的模块，按加入顺序。
     *
     * <p>{@code CopyOnWriteArrayList}：加入发生在模组构造期，读取发生在能力事件与
     * {@code FMLCommonSetupEvent}，两个阶段不保证同一线程；而且加入后清单只会被整体读取，
     * 不需要随机写。
     */
    private static final List<CompatModule> EXTERNAL_MODULES = new CopyOnWriteArrayList<>();

    /**
     * 清单是否已经封口。封口之后再加模块会抛 {@link IllegalStateException}。
     *
     * <p>为什么必须封口：{@link CompatModule#registerCapabilities} 只在
     * {@code RegisterCapabilitiesEvent} 里跑一次，而那个事件比 {@code FMLCommonSetupEvent} 早。
     * 晚加入的模块不会漏注册需求类型（那一步在 common setup），却会**静默地**漏掉自己的能力注册，
     * 后果是它的端口永远问不到。宁可让调用方立刻看见异常，也不要留下这种只在特定加载顺序下出现的缺口。
     */
    private static volatile boolean SEALED = false;

    /**
     * 本模组**已知**的兼容模块清单：编译期写死的模块，加上其它模组通过
     * {@link #registerModule(CompatModule)} 交进来的模块。
     *
     * <p>波 5.3 起有第一个真模块：{@code compat/mekanism/MekanismModule}（Mekanism 化学，旧版的「气体」）。
     * 它的 {@code requiresModid()} 是 {@code "mekanism"}，所以没装那个模组时它不会被注册——
     * **而这条能成立的前提是：本清单里的模块类在构造时不触发它依赖的 API 的类加载。**
     * {@code MekanismModule} 满足这一条（构造器只赋值，所有 Mekanism 引用都在实例方法与方法体里）。
     * 后来者加模块时必须同样满足，否则没装该模组的玩家会在**注册判断之前**就
     * {@code NoClassDefFoundError}。
     *
     * <p>内置模块写成 {@code new ...} 而不是反射扫描：反射能省掉这一行，代价是「哪些模块在」变成运行时
     * 才知道的事情，构建期没有任何东西能检查它。而这里的模块都是编译期就存在的类（它们要引用该模组的 API）。
     * 外部模块不适用这条——它们本来就不在本模组的编译单元里，只能由对方自己交进来。
     */
    private static List<CompatModule> modules() {
        List<CompatModule> all = new ArrayList<>(1 + EXTERNAL_MODULES.size());
        all.add(new com.reborn.modularmachinery.compat.mekanism.MekanismModule());
        all.add(new com.reborn.modularmachinery.magic.MagicModule("botania"));
        all.add(new com.reborn.modularmachinery.magic.MagicModule("naturesaura"));
        all.add(new com.reborn.modularmachinery.magic.MagicModule("bloodmagic"));
        all.add(new com.reborn.modularmachinery.magic.MagicModule("pneumaticcraft"));
        all.add(new com.reborn.modularmachinery.magic.MagicModule("embers"));
        all.addAll(EXTERNAL_MODULES);
        return all;
    }

    private ModCompat() {
    }

    /**
     * 让**其它模组**把它的兼容模块交给本模组。在对方自己的 {@code @Mod} 构造器里调用。
     *
     * <p><b>为什么是静态方法而不是 Forge 事件。</b>模块的 {@code registerCapabilities} 必须在
     * {@code RegisterCapabilitiesEvent} 之前进入清单；那个事件按定义早于 {@code FMLCommonSetupEvent}，
     * 而本模组在构造期又无法可靠地问 {@code ModList}（见类注释）。让外部模组在构造器里主动调用，
     * 是唯一不依赖加载顺序的可靠时机。
     *
     * @param module 要加入的模块；它的 {@link CompatModule#requiresModid()} 可以指向一个没装的模组，
     *               那种模块会在注册阶段被跳过，而不是被拒绝加入
     * @throws NullPointerException  {@code module} 或它的 {@code id()} 为 {@code null}
     * @throws IllegalStateException 清单已经封口（能力事件或 common setup 已经开始），
     *                               或已有同 id 的模块（内置的或外部加入的都算）
     */
    public static void registerModule(CompatModule module) {
        if (module == null) {
            throw new NullPointerException("Cannot register a null compat module");
        }
        ResourceLocation id = module.id();
        if (id == null) {
            throw new NullPointerException("Cannot register a compat module with a null id");
        }
        synchronized (EXTERNAL_MODULES) {
            if (SEALED) {
                throw new IllegalStateException("Compat module " + id + " was registered after the module list "
                        + "was sealed; register it from the owning mod's constructor, before "
                        + "RegisterCapabilitiesEvent.");
            }
            for (CompatModule known : modules()) {
                if (id.equals(known.id())) {
                    throw new IllegalStateException("A compat module with id " + id + " is already known: "
                            + known.getClass().getName());
                }
            }
            EXTERNAL_MODULES.add(module);
        }
    }

    /**
     * 封口清单。{@code RegisterCapabilitiesEvent} 与 {@code FMLCommonSetupEvent} 都从这里进；
     * 两个事件谁先到都安全——先到的那一个把门关上，后到的只会看到同一份清单。
     */
    private static void sealModules() {
        synchronized (EXTERNAL_MODULES) {
            SEALED = true;
        }
    }

    /** Forge 事件入口；注册在 {@code ModularMachineryReborn} 里。 */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        sealModules();
        register(modules(), ModList.get()::isLoaded);
        if (ModList.get().isLoaded("ae2") && ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("appmek")) {
            event.enqueueWork(com.reborn.modularmachinery.compat.ae2.AppMekGasBridge::registerProviderBuffers);
        }
    }

    /**
     * 兼容层的真正逻辑，与 FML 无关。
     *
     * <p>{@code isLoaded} 传进来而不是在里面调 {@code ModList}：这一条是波 5.2 唯一能被离线断言的
     * 「框架行为」（哪个模块被调用、哪个被跳过、依赖没装的类型有没有被摘掉），
     * 而离线环境里没有 FML。
     *
     * @param modules  候选模块
     * @param isLoaded 某个 modid 装了没
     * @return 这次真的被调用过 {@code register} 的模块，按清单顺序
     */
    public static synchronized List<CompatModule> register(List<CompatModule> modules, Predicate<String> isLoaded) {
        List<CompatModule> registered = new ArrayList<>();
        for (CompatModule module : modules) {
            ResourceLocation id = module.id();
            if (REGISTERED.containsKey(id)) {
                // 同一个模块在一次启动里只会被注册一次：重复注册会撞上 RequirementTypes 的「重复 id 即报错」，
                // 而那条报错在这种情形下是**假警报**（不是命名冲突，是调用点重复），所以在这里挡掉。
                continue;
            }
            String required = module.requiresModid();
            if (required != null && !isLoaded.test(required)) {
                ModularMachineryReborn.LOGGER.info("[{}] Compat module {} is not loaded: it needs {}.",
                        ModularMachineryReborn.MOD_ID, id, required);
                continue;
            }
            CompatRegistry registry = new CompatRegistry();
            // Type changes are staged across both callbacks; a failed port must not leave an unusable kind.
            // One failed module must not disable the rest of the compatibility layer.
            // 而且这一层必须 catch Throwable：没装依赖的模块在这里最典型的失败是
            // NoClassDefFoundError（它的字段类型来自那个模组），那是 Error 不是 Exception。
            try {
                RequirementTypes.registerAtomically(() -> {
                    module.register(registry);
                    module.ports(registry);
                });
            } catch (Throwable failure) {
                ModularMachineryReborn.LOGGER.error("[{}] Compat module {} failed to register; it is skipped. "
                        + "If its required mod is installed, this is a defect in the module.",
                        ModularMachineryReborn.MOD_ID, id, failure);
                continue;
            }
            REGISTERED.put(id, module);
            registered.add(module);
            PORT_MAPPERS.addAll(registry.portMappers());
            ModularMachineryReborn.LOGGER.info("[{}] Compat module {} registered. Requirement kinds now: {}",
                    ModularMachineryReborn.MOD_ID, id, RequirementTypes.ids());
        }
        // 兜底：任何**声明了**依赖、而那个模组不在场的需求类型，一律从注册表里摘掉。
        // 这一条会把内置五种之外的一切都过一遍，而内置五种的 requiresModid() 是 null，所以不会误伤。
        List<ResourceLocation> removed = RequirementTypes.removeUnavailable(isLoaded);
        if (!removed.isEmpty()) {
            ModularMachineryReborn.LOGGER.info("[{}] Requirement kinds withdrawn because their mod is not "
                    + "installed: {}", ModularMachineryReborn.MOD_ID, removed);
        }
        return registered;
    }

    /** 已经注册的兼容模块，按注册顺序；给诊断与断言用。 */
    public static Map<ResourceLocation, CompatModule> registered() {
        return Map.copyOf(REGISTERED);
    }

    /** 把「已知模块清单」暴露给断言，免得断言自己抄一份（抄一份就迟早与实现不一致）。 */
    public static List<CompatModule> knownModules() {
        return List.copyOf(modules());
    }

    // ------------------------------------------------------------------ 端口映射器

    /**
     * 模块登记的能力映射器，按登记顺序，**只增不减**。
     *
     * <p>{@code CopyOnWriteArrayList} 而不是普通 {@code ArrayList}：映射器在
     * {@code FMLCommonSetupEvent} 里写一次，而 {@code MachineHatchBlockEntity#getCapability} 会从
     * **任意线程**（区块加载、管道抽液、客户端同步）读它。一个读写并发的 {@code ArrayList} 在这里
     * 可能给出一个中间状态，而那种失败极难复现。
     */
    private static final List<BlockEntityPortMapper> PORT_MAPPERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 按顺序问每个映射器「这个能力是不是你的」——主模组**不认识**任何一个兼容能力，这是唯一的入口。
     *
     * <p>在 {@code MachineHatchBlockEntity#getCapability} 里被调用，每次能力查询一次。映射器通常
     * 只有一个（每个装了模块一种），所以线性扫描不是问题；而它换来的是「加一种端口不必动主模组」。
     *
     * @return 匹配到的 optional，或 {@code null} 表示没有映射器认领这个能力
     */
    @Nullable
    public static LazyOptional<?> mapPort(Capability<?> capability, Object entity) {
        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            LazyOptional<?> mapped = mapper.map(capability, entity);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /**
     * 这个实体上**所有**模块提供的端口处理器，按登记顺序。
     *
     * <p>与 {@link #mapPort} 的区别是「问法」：那个方法是 Forge 的能力查询（一个实体 + 一个能力 → 一个结果），
     * 这个是**端口收集**（一个实体 → 它所有的兼容端口）。后者没有能力对象可比，所以只能让每个映射器
     * 自己回答「这个实体上你有什么」。
     *
     * <p>返回的是处理器本身而不是 {@code LazyOptional}，因为调用方（端口收集）要的是长期持有的引用，
     * 与 Forge 的能力查询语义不同——{@link BlockEntityPortMapper#handlers} 就是为这件事存在的。
     *
     * <p>这个无参家族的版本回答「**所有**家族」，只给诊断与「有没有任何端口」这类询问用。
     * 端口收集走 {@link #portHandlers(Object, String)}，因为主模组的桶是按家族分开的。
     */
    public static List<Object> portHandlers(Object entity) {
        return portHandlers(entity, null);
    }

    /**
     * 这个实体上属于 {@code family} 家族的兼容端口处理器，按登记顺序。
     *
     * <p><b>为什么要家族（勘测 X4）。</b>主模组把端口存成**不透明对象**：它不引用任何可选模组的
     * API 类型，也就不可能自己判断「这个对象是不是气体」。家族名（目前只有 {@code "gas"}）是它与
     * 模块之间唯一的契约，由模块回答「这个家族是不是我的」。不认识该家族的模块必须返回空列表。
     *
     * @param entity 被收集的方块实体
     * @param family 端口家族；{@code null} 表示所有家族
     */
    public static List<Object> portHandlers(Object entity, @Nullable String family) {
        List<Object> handlers = new ArrayList<>();
        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            handlers.addAll(mapper.handlers(entity, family));
        }
        return handlers;
    }

    /**
     * 能力查询这条路上**被问到过哪些能力**，按首次出现顺序，最多记 40 个。
     *
     * <p>为什么需要它：这正是「化学品进不去」那类缺陷唯一低成本的分岔点。管道如果**根本没问到**
     * 本模组的方块实体，或者问的是**另一个能力对象**，那么从需求那一侧看都只是「端口是空的」，
     * 而日志里**一个字都不会有**——本项目规矩 5 说的就是这一类。
     *
     * <p>只记前 40 个：能力查询是每方块每次管道询问都会走的路，全记会把日志淹掉。
     * 记的是能力的字符串形式（Forge 的 {@code Capability#toString} 会给出它的名字），
     * 于是「管道问的到底是不是 Mekanism 的气体能力」一眼可判。
     */
    private static final java.util.Set<String> QUERIED_CAPABILITIES =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private static final int CAPABILITY_LOG_LIMIT = 40;

    /** 让 {@code MachineHatchBlockEntity#getCapability} 把每一次查询记下来，供 {@code /mm-diagnose} 读。 */
    public static void recordCapability(Capability<?> capability) {
        if (QUERIED_CAPABILITIES.size() >= CAPABILITY_LOG_LIMIT) {
            return;
        }
        QUERIED_CAPABILITIES.add(String.valueOf(capability));
    }

    /** 到此刻为止被问到过的能力，按首次出现顺序。 */
    public static List<String> queriedCapabilities() {
        synchronized (QUERIED_CAPABILITIES) {
            return List.copyOf(QUERIED_CAPABILITIES);
        }
    }

    /**
     * 把 {@link #queriedCapabilities()} 的每一行配上**可读的名字**（映射器自己报的）。
     *
     * <p>没有名字的那些会原样保留——那本身就是信息：「这个能力不属于任何兼容模块」。
     */
    public static List<String> queriedCapabilitiesNamed() {
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            names.putAll(mapper.capabilityNames());
        }
        List<String> out = new ArrayList<>();
        for (String queried : queriedCapabilities()) {
            String name = names.get(queried);
            out.add(queried + (name == null ? "   ← 不属于任何兼容模块" : "   ← " + name));
        }
        return out;
    }

    /** 已登记的端口映射器数量——**0 表示没有任何兼容模块挂上端口**。 */
    public static int portMapperCount() {
        return PORT_MAPPERS.size();
    }

    /**
     * 这个实体上所有模块报出的**具名数字**，合并成一个 map（键如 {@code "gas.amount"}）。
     *
     * <p>主模组的方块实体用它填界面上的容量条：它只认识名字，不认识任何模块的类型。
     * 这就是「主模组一个 Mekanism 类都不出现」在**界面这一侧**的落点——
     * 与 {@link #mapPort}、{@link #portHandlers} 同一条思路。
     */
    public static Map<String, String> namedValues(Object entity) {
        Map<String, String> values = new LinkedHashMap<>();
        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            values.putAll(mapper.namedValues(entity));
        }
        return values;
    }

    /** 存档写：把每个模块的内容交给它自己序列化。由 {@code MachineHatchBlockEntity#saveAdditional} 调用。 */
    public static void savePorts(net.minecraft.nbt.CompoundTag tag, Object entity) {        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            mapper.save(tag, entity);
        }
    }

    /** 存档读：把每个模块的内容交给它自己反序列化。由 {@code MachineHatchBlockEntity#load} 调用。 */
    public static void loadPorts(net.minecraft.nbt.CompoundTag tag, Object entity) {
        for (BlockEntityPortMapper mapper : PORT_MAPPERS) {
            mapper.load(tag, entity);
        }
    }

    /**
     * Forge 的 {@code RegisterCapabilitiesEvent} 入口：把每个模块自己的 Forge 能力注册上。
     *
     * <p><b>为什么这是必要的。</b>{@code IChemicalHandler}（及其窄化别名 {@code IGasHandler}）是 Mekanism
     * 自己声明的 Forge 能力，主模组不会、也不该认识它。但一个能力**只有被注册**之后，
     * Forge 的 {@code getCapability} 才会把查询转给方块实体——没有这一步，管道与 Mekanism 的机器
     * 怎么问都问不到这台机器的气体仓。
     *
     * <p>同样必须 catch {@code Throwable}：没有 Mekanism 时，模块的
     * {@code MekanismAPI.gasRegistry().get()}（在 {@code CapabilityManager.get} 内部）会以
     * {@code NoClassDefFoundError} 告终。那本该如此——没装那个模组就不该有那种能力——但它必须是**安静地不做**，
     * 而不是让整个模组构造失败。
     */
    public static void onRegisterCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        sealModules();
        for (CompatModule module : modules()) {
            try {
                module.registerCapabilities(event);
            } catch (Throwable unavailable) {
                ModularMachineryReborn.LOGGER.info("[{}] Compat module {} declares no Forge capability here "
                        + "(its own mod is probably absent): {}", ModularMachineryReborn.MOD_ID, module.id(),
                        unavailable.getClass().getSimpleName());
            }
        }
    }
}
