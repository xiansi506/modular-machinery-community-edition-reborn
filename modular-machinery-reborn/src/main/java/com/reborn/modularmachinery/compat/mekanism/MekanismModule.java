package com.reborn.modularmachinery.compat.mekanism;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.compat.CompatModule;
import com.reborn.modularmachinery.compat.CompatRegistry;
import com.reborn.modularmachinery.recipe.IOType;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import com.reborn.modularmachinery.recipe.RequirementType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 兼容模块：Mekanism 化学（旧版的「气体」）。
 *
 * <p>它注册两种需求，对应旧版的两个注册项（{@code RequirementTypesMM.KEY_REQUIREMENT_GAS} /
 * {@code KEY_REQUIREMENT_GAS_PERTICK}，{@code RequirementTypesMM.java:38-39}）：
 *
 * <table border="1">
 *   <caption>旧版 → 本版</caption>
 *   <tr><th>旧版 CT / JSON</th><th>本版的种类 id</th></tr>
 *   <tr><td>{@code gas}（开工/完工一次性结算）</td><td>{@code modular_machinery_reborn:gas}</td></tr>
 *   <tr><td>{@code gas_pertick}（每 tick 结算）</td><td>{@code modular_machinery_reborn:gas_pertick}</td></tr>
 * </table>
 *
 * <p><b>注册时机由波 5.2 的框架负责</b>：{@code ModCompat} 在 {@code FMLCommonSetupEvent} 里
 * 先看 {@link #requiresModid()}，再调 {@link #register}。所以没装 Mekanism 时
 * {@link #register} 一次都不会被调用，这两个种类也就不在注册表里——那时配方里写
 * {@code "type": "modular_machinery_reborn:gas"} 会得到「未知需求类型」的响亮报错，
 * 而不是一条永远无法满足的需求。
 *
 * <p><b>本类刻意不 import 任何 chemical 以外的 Mekanism 类</b>：旧版那一大堆
 * （{@code TileFluidTank} / {@code HybridGasTank} / {@code HybridFluidUtils}）属于「端口」那一半，
 * 不在本模块里。
 */
public final class MekanismModule implements CompatModule {

    /** 这个模块的 id；命名空间用本模组的（理由见 {@code CompatModule#id()}）。 */
    public static final ResourceLocation ID = new ResourceLocation(ModularMachineryReborn.MOD_ID, "mekanism");

    /** 旧版的 modid，就是这个模块要求在场的那一个。 */
    public static final String MODID = "mekanism";

    /**
     * 已注册的两个种类。
     *
     * <p>为什么不写成 {@code static final RequirementType TYPE_GAS = ...}：注册要给
     * {@link CompatRegistry#requirement} 一个实例，而 {@link CompatModule#register} 是按设计
     * **在模组构造之后**才被调的。写成静态初始化就把「注册」从模块手里挪回了类加载，也就绕开了
     * 波 5.2 那套「先查依赖再注册」的顺序——那是这一层存在的全部意义。
     *
     * <p>它们是 {@code @Nullable} 的：一个没装 Mekanism 的进程里两个都是 {@code null}，
     * 这正是「这个模块没生效」的诚实表示。
     */
    @Nullable
    public static RequirementType TYPE_GAS;
    @Nullable
    public static RequirementType TYPE_GAS_PER_TICK;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public String requiresModid() {
        return MODID;
    }

    @Override
    public void register(CompatRegistry registry) {
        TYPE_GAS = registry.requirement(RequirementType.of(
                new ResourceLocation(ModularMachineryReborn.MOD_ID, "gas"),
                new GasHandler(false), MODID));
        TYPE_GAS_PER_TICK = registry.requirement(RequirementType.of(
                new ResourceLocation(ModularMachineryReborn.MOD_ID, "gas_pertick"),
                new GasHandler(true), MODID));
        ModularMachineryReborn.LOGGER.info("[{}] Mekanism chemical requirement kinds registered: gas, "
                + "gas_pertick (per-tick).", ModularMachineryReborn.MOD_ID);
    }

    /**
     * 把气体端口挂到流体仓上。
     *
     * <p>本方法**不直接引用** {@code MekanismAnchor}：它通过方法引用的形式传出去，而那个类在
     * {@code ports} 被调用的**那一刻**才会被解析。这一条不是洁癖——本方法所在的类会被主模组的
     * {@code ModCompat.modules()} 构造（每次启动都会，包括没装 Mekanism 的启动），
     * 所以本类的**静态初始化与方法体之外的一切**都不能引用 Mekanism 类型。字段与方法体可以有，
     * 因为它们在模块真的被注册之前不会被执行到。
     */
    @Override
    public void ports(CompatRegistry registry) {
        registry.port(MekanismAnchor.portMapper());
        ModularMachineryReborn.LOGGER.info("[{}] Mekanism gas ports attached to the fluid hatches.",
                ModularMachineryReborn.MOD_ID);
        // 探针：能力对象到底拿到没有。这是「管道问不到」这一类缺陷的第一个分岔点——
        // 拿到 null 就说明 Mekanism 的能力还没注册（或者本模组比它先跑），后面什么都不必查了。
        MekanismProbe.startup("ports(): gas capability available = " + MekanismAnchor.gasHandlerAvailable()
                + ", probe enabled = " + MekanismProbe.enabled()
                + " (turn on with -Dmodular_machinery_reborn.compat.debug=true)");
    }

    /**
     * 注册 Mekanism 的化学能力。
     *
     * <p><b>这里什么都不用做</b>，而这一点值得写下来，免得下一个人（或我自己）再加一遍：
     * Forge 的能力是**按类型**的身份对象，而 Mekanism 已经用 {@code @AutoRegisterCapability}
     * 声明了 {@code IGasHandler}——所以 {@code Capabilities.GAS_HANDLER} 就是那个对象，
     * 本模组必须**读**它而不是再注册一个（注册第二个只会得到另一个身份，两边的查询永远匹配不上）。
     *
     * <p>也不在这里缓存那个字段：能力对象的建立时机取决于模组加载顺序，而本方法跑的时机
     * （{@code FMLCommonSetupEvent}）并不保证它已经建立。读取被推迟到能力查询真正发生的时候，
     * 见 {@code MekanismAnchor} 里的说明。
     */
    @Override
    public void registerCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        // 有意留空，理由见方法注释。
    }

    /** 气体读盘的计数与最近一次「存档里有却读不出来」的内容；给 /mm-diagnose 用。 */
    public static String gasRestoreCounters() {
        return HybridGasTank.restoreCounters();
    }
    /** 气体端口那一半是否可用（给诊断用）。 */
    public static boolean gasPortReady() {
        return MekanismAnchor.gasHandlerAvailable();
    }

    /**
     * 一个仓的气体内容，给 {@code /mm-diagnose} 用；不是流体仓、或还没被建过气体那一半时返回 {@code null}。
     *
     * <p>**不新建**气体罐：诊断一个仓不该改变它的状态（那会让「看一眼」产生副作用）。
     * 只有已经因为管道询问或端口收集建过的罐才会被报出来——而那恰好就是
     * 「这条路上到底有没有被走到」的证据，正是这个缺陷最需要的信息。
     */
    @javax.annotation.Nullable
    public static String describeGasPort(Object entity) {
        return MekanismAnchor.describeExistingTank(entity);
    }

    /**
     * 这个仓上的气体**插入尝试**统计，给 {@code /mm-diagnose} 用；没有气体那一半时返回 {@code null}。
     *
     * <p>为什么要单独一个方法：`气体=空` 只说明「没进去」，而它有两种完全相反的原因——
     * 管道**根本没灌过**（不是我这边的事）与管道**灌了但被拒**（是我这边的事）。
     * 这一行把它们分开。
     */
    @javax.annotation.Nullable
    public static String describeGasInserts(Object entity) {
        return MekanismAnchor.describeTankInserts(entity);
    }

    /**
     * 界面 tooltip 要的三个字段：{@code {注册名, 气体量, 容量}}；没有气体时返回 {@code null}。
     *
     * <p>三个字段都是原始字符串，由界面拼成与流体仓相同的那一行——不是给人看的成品文字。
     * 读的是同步过来的存档 NBT 而不是内存里的气体罐，理由见 {@code MekanismAnchor#gasTooltip}。
     */
    @javax.annotation.Nullable
    public static String[] gasTooltip(Object entity) {
        return MekanismAnchor.gasTooltip(entity);
    }

    /**
     * 某种气体的本地化名字（如「氢氟酸」），给界面 tooltip 的第一行；查不到时返回 {@code null}。
     *
     * <p>返回的是原版 {@code Component}：主模组只认识它，不认识 Mekanism 的气体类型（勘测 X4 的隔离）。
     */
    @javax.annotation.Nullable
    public static net.minecraft.network.chat.Component gasDisplayName(String registryName) {
        return MekanismAnchor.gasDisplayName(registryName);
    }

    /**
     * 菜单数据槽要的两个数字：{@code {气体量 mB, 容量 mB}}；没有气体那一半时返回 {@code null}。
     *
     * <p>给界面的**容量条**用：主模组不认识气体，但它需要两个 int 才能把那一条按比例画出来，
     * 所以这两个数字由模块报（与波 5.3 起 `HatchCollection` 里那对气体端口同一条思路：
     * 主模组只知道「有个模块愿意报数」）。
     */
    @javax.annotation.Nullable
    public static String[] menuGas(Object entity) {
        return MekanismAnchor.menuGas(entity);
    }

    /**
     * 某种气体的颜色（按注册名查），给界面的容量条上色用；查不到时中性灰。
     *
     * <p>颜色由 Mekanism 自己给出（{@code ChemicalStack#getChemicalTint()}），不是界面里编的。
     */
    public static int gasTint(String registryName) {
        return MekanismAnchor.gasTint(registryName);
    }

    /** {@code gas} 与 {@code gas_pertick} 共用的一份读写器，只差 {@code perTick} 一位。 */
    private static final class GasHandler implements RequirementType.Handler {

        private final boolean perTick;

        private GasHandler(boolean perTick) {
            this.perTick = perTick;
        }

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            // **先校验本需求自己的字段，最后才去碰注册表。**顺序是有意的：查找气体要走
            // MekanismAPI.gasRegistry()，而那个注册表只在游戏里存在（离线复现器里它的静态初始化会以
            // "Not bootstrapped" 失败）。若先查气体，一条 amount 写错的配方就会报「气体不认识」，
            // 把人指到完全错误的字段上——而「先把自己能答的问题答完」也让离线断言能覆盖全部字段校验。
            int amount = requireAmount(json, where);
            float chance = json.has("chance") ? readChance(json, where) : 1.0F;
            Gas gas = requireGas(json, where);
            return new GasRequirement(ioType, new GasStack(gas, amount), amount, chance, this.perTick);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            GasRequirement gas = (GasRequirement) requirement;
            buf.writeUtf(gas.ioType().name());
            // ChemicalStack 自己的过网方法：父类写（含类型与数量），子类静态读。
            // 数量用 long 写、用 long 读——ChemicalStack 的 amount 就是 long。
            gas.gasType().writeToPacket(buf);
            buf.writeVarInt(gas.amount());
            buf.writeFloat(gas.chance());
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            GasStack stack = GasStack.readFromPacket(buf);
            int amount = buf.readVarInt();
            float chance = buf.readFloat();
            return new GasRequirement(ioType, stack, amount, chance, this.perTick);
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            // 见 GasRequirement#modifierTarget 的说明：闭枚举没有气体项，用 ALL 而不是某个具体项。
            return RecipeModifier.Target.ALL;
        }

        /**
         * 读出 {@code gas} 字段并解析成一个真实的气体。
         *
         * <p>完整资源 ID 只查写出的命名空间，不允许本模组的同路径气体遮蔽它。只有裸名称
         * 保留历史顺序：先查本模组命名空间，再按 ResourceLocation 的 Minecraft 默认命名空间查找。
         * 需求种类的旧名称别名由 RequirementTypes 处理，不改变气体资源本身的注册身份。
         */
        private static Gas requireGas(JsonObject json, Object where) {
            String raw = rawString(json, "gas", where);
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new JsonParseException("'gas' in " + where + " is empty. Write a gas id, e.g. "
                        + "\"gas\": \"mekanism:oxygen\".");
            }
            ResourceLocation literal = ResourceLocation.tryParse(trimmed);
            if (literal == null) {
                throw new JsonParseException("'gas' in " + where + " is '" + raw + "', which is not a valid "
                        + "resource location. Write a gas id, e.g. \"gas\": \"mekanism:oxygen\".");
            }
            Gas gas = trimmed.indexOf(':') < 0
                    ? lookup(ModularMachineryReborn.MOD_ID + ":" + literal.getPath()) : null;
            if (gas == null) {
                gas = lookup(literal.toString());
            }
            if (gas == null) {
                throw new JsonParseException("Unknown Mekanism gas '" + raw + "' in " + where + ". Gases are a "
                        + "registry in 1.20.1, so the id is fully qualified — and that is the one thing about "
                        + "this kind that does NOT carry over from the original: the original read a bare name "
                        + "with GasRegistry.getGas(\"oxygen\"), this version needs \"gas\": \"mekanism:oxygen\". "
                        + "Check the exact id (JEI shows a gas's name in its tooltip). This port does not guess "
                        + "at names or map old ones.");
            }
            return gas;
        }

        /**
         * 查一个气体；离线/注册表未就绪时返回 {@code null}。
         *
         * <p><b>为什么这里 catch {@code Throwable}。</b>实测过两种离线状态，它们**不是同一种失败**：
         *
         * <ul>
         *   <li>没有 bootstrap 的进程（一个裸的 {@code java} 探针）：{@code MekanismAPI} 的静态初始化走到
         *       {@code registryKey(...)} 抛 {@code IllegalArgumentException: Not bootstrapped}，
         *       于是这个类的**每一次**触碰都变成 {@code NoClassDefFoundError: Could not initialize class
         *       mekanism.api.MekanismAPI}；</li>
         *   <li>引导过 Minecraft 注册表、但 Forge 注册表尚未接上的进程（离线复现器就是这个状态）：
         *       {@code gasRegistry()} 直接**返回 null**，于是 {@code getValue} 是一个 NPE。</li>
         * </ul>
         *
         * <p>两种都表现为「这个气体的名字在这里无法判定」，而调用方要给出的答案是同一句：
         * 这个气体不认识。所以这里把一个宽泛的 catch 收在一个方法里，而不是让 NPE 或
         * {@code NoClassDefFoundError} 逃到配方加载器上——那会让一条写错气体名的配方报出与气体无关的崩溃信息。
         *
         * <p>代价如实说：**注册表真的坏掉时也会走到「气体不认识」这条消息**。这是可接受的取舍，
         * 因为另一条路的症状（配方加载期抛 NPE）明显更难查，而注册表坏掉本身在别处会更响亮地暴露。
         */
        @Nullable
        private static Gas lookup(String id) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) {
                return null;
            }
            try {
                var registry = MekanismAPI.gasRegistry();
                if (registry == null) {
                    return null;
                }
                Gas gas = registry.getValue(key);
                return gas == MekanismAPI.EMPTY_GAS ? null : gas;
            } catch (Throwable unavailable) {
                return null;
            }
        }

        /** {@code amount}：必填、必须是整数、必须 ≥ 1。 */
        private static int requireAmount(JsonObject json, Object where) {
            JsonElement element = json.get("amount");
            if (element == null) {
                throw new JsonParseException("Missing 'amount' in " + where + ". A gas requirement needs "
                        + "\"amount\" — how many mB one copy of the craft consumes or produces, e.g. "
                        + "\"amount\": 1000.");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("'amount' in " + where + " must be a number. Write how many mB one "
                        + "copy of the craft consumes or produces, e.g. \"amount\": 1000.");
            }
            int amount = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 1, Integer.MAX_VALUE, where + " amount");
            if (amount < 1) {
                throw new JsonParseException("'amount' in " + where + " is " + amount + ", but every copy has to "
                        + "consume or produce at least 1 mB. The original clamped this to 0 and then behaved as "
                        + "though the requirement did nothing (RequirementTypeGas:60); this port refuses it "
                        + "instead. Write a whole number of at least 1.");
            }
            return amount;
        }

        /**
         * {@code chance}：可选，写了就必须落在 {@code 0..1}。
         *
         * <p>这里**有意与旧版不同**：旧版 {@code RequirementTypeGas:69-71} 对越界值
         * 静默丢弃（回落到 1.0），于是「写了 50 想要 50%」的人会得到一个 100% 的需求而不自知。
         * 本版对这种「看起来写了、实际没生效」的形态一律报错——与
         * {@code IngredientArrayParser#readChance} 的处理一致。
         */
        private static float readChance(JsonObject json, Object where) {
            JsonElement element = json.get("chance");
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("'chance' in " + where + " must be a number between 0 and 1. "
                        + "Write the probability that this requirement is settled, e.g. \"chance\": 0.5.");
            }
            float chance = com.reborn.modularmachinery.util.JsonNumbers.finiteFloat(element, where + " chance");
            if (chance < 0.0F || chance > 1.0F) {
                throw new JsonParseException("'chance' in " + where + " is " + chance + ", but it has to be "
                        + "between 0 and 1. The original silently ignored a value outside that range "
                        + "(RequirementTypeGas:69-71) and used 1.0 instead, so a \"chance\": 50 meant a "
                        + "guaranteed result; write a fraction such as 0.5.");
            }
            return chance;
        }

        /** 一个必填的字符串字段，消息里说清该写什么。 */
        private static String rawString(JsonObject json, String key, Object where) {
            JsonElement element = json.get(key);
            if (element == null) {
                throw new JsonParseException("Missing '" + key + "' in " + where + ". A gas requirement needs "
                        + "\"" + key + "\" (the gas id) and \"amount\" (mB per copy), e.g. "
                        + "{\"type\": \"modular_machinery_reborn:gas\", \"io-type\": \"input\", "
                        + "\"gas\": \"mekanism:oxygen\", \"amount\": 1000}.");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("'" + key + "' in " + where + " must be a string. Write a gas id, "
                        + "e.g. \"" + key + "\": \"mekanism:oxygen\".");
            }
            return element.getAsString();
        }
    }
}
