package com.reborn.modularmachinery.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.factory.FactoryThreadModel;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 机器定义的<strong>schema</strong>：读取一份定义，并承载它必须满足的每一条规则。
 *
 * <p>这个类存在的原因是：现在有<strong>不止一条</strong>路可以把机器定义交给本模组。0.28.0 之前唯一的产出者是
 * {@link MachineLoader}，它解析 {@code data/<namespace>/machinery/} 下的 JSON 文件，于是每一条规则
 * （「必须是其中之一……」「请写……」）都长在那段解析里。KubeJS 脚本是第二个产出者（D7/v2a）——
 * 如果另写一条「只挑自己关心的字段看看」的路径，脚本作者就会为<strong>同一份 schema 拿到严格更差的报错</strong>，
 * 比数据包作者差。这正是本项目反复付过代价的那一类不一致，所以规则被<strong>从 loader 里抬了出来</strong>：
 * 两条路都把 {@link JsonObject} 交给 {@link #read}，拿到同一个 {@link JsonParseException}，连句子都一样。
 *
 * <p>因此 KubeJS 那一侧只是个 <strong>JSON 构建器</strong>，别的什么都不是：{@code MachineRegisterEventJS} 与
 * {@code MachineBuilderJS} 把脚本要求的字段追加到一个 {@code JsonObject} 上，然后调用 {@link #read}——
 * 写脚本的人和写数据包的人由同一段代码回答。{@code mmverify} 用两组输入断言了这件事：
 * 一个脚本形状的对象和一个数据包形状的对象，带着<strong>同样的缺陷</strong>，必须产出逐字节相同的消息。
 *
 * <h2>两条路，以及它们之间剩下的那一处差别</h2>
 *
 * <ol>
 *   <li><strong>{@code strict}</strong>（严格模式）。数据包被宽容地解析，只为一个理由：与「字段还不存在时写下的
 *       定义」保持向后兼容。loader 报出一个未知根字段然后继续走，这正是 {@link #read(JsonElement, Object, Map)}
 *       做的。脚本没有这段历史——它是<strong>今天</strong>照这套 API 写的——所以
 *       {@link #read(JsonElement, Object, Map, boolean)} 在严格模式下<strong>拒绝</strong>未知字段。
 *       这就补上了勘测指出的缺口：走 loader 时未知字段只是一条警告，走 KubeJS 时同样的错误现在可以是
 *       一个<strong>错误</strong>，并且把所有确实存在的字段都列出来。</li>
 *   <li><strong>{@code deferred}（延迟字段）—— 0.31.0 起已消失。</strong>直到 0.30.1，脚本 API 只实现了核心字段，
 *       而「loader 会求值、脚本 API 却写不了」的字段会被<strong>按名拒绝</strong>，而不是接受后忽略，
 *       这样「脚本写了 {@code has-factory} 却什么都没发生」就不可能发生。
 *       {@code MachineBuilderJS} 现在对全部十一个字段都有方法，所以没有东西可延迟，拒绝路径也删掉了
 *       （{@link #DEFERRED_ROOT_FIELDS} 作为一份<strong>空列表</strong>留着，但身上有一份契约）。
 *       于是两条路读的是<strong>同一组字段</strong>，它们之间唯一的差别只剩「对<strong>双方都不认识</strong>的
 *       字段有多严格」。</li>
 * </ol>
 *
 * <p>这里不碰注册表、不碰 level、不碰配置文件——正因如此，离线复现器才能在没有游戏的情况下驱动整套 schema，
 * 包括 KubeJS 构建器。
 */
public final class MachineSchema {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 本 loader 会读取的<strong>全部根字段</strong>，用于「未知字段」那条消息。
     * {@code has-factory} 与 {@code factory-only} 是旧版自己的 schema（{@code DynamicMachinePreDeserializer:77-89}）；
     * {@code max-parallelism} / {@code internal-parallelism} / {@code parallelizable}（D12）、
     * {@code max-threads} / {@code core-threads}（D15）与 {@code smart-interfaces}（D16）是本项目有文档记录的扩展，
     * 理由都一样：旧版把这些值放在 {@code AbstractMachine} 上，且只有 CraftTweaker 脚本能设置它们，
     * 而本项目的作者没有脚本可用。
     */
    public static final List<String> KNOWN_ROOT_FIELDS = List.of(
            "registryname", "localizedname", "parts", "failure-action", "requires-blueprint",
            "max-parallelism", "internal-parallelism", "parallelizable", "modifiers", "multi-block-modifiers",
            "has-factory", "factory-only", "max-threads", "core-threads", "smart-interfaces",
            "dynamic-patterns",
            // 0.60.0: `color`。它此前在 UNIMPLEMENTED_ROOT_FIELDS 里（「识别但不求值」），
            // 所以从来不需要出现在这份名单里。做完第一小步（解析与保存）之后**必须**加进来——
            // 否则加载器会把它当成陌生字段并警告「this loader does not know: color」，
            // 而那会让包作者以为字段名写错了。
            //
            // 这个坑是复现器输出里的 WARN 抓到的，不是看代码看出来的：断言全绿、日志里却在抱怨。
            // 与「过宽的断言」同类——**测试通过不等于没有警告**。
            
            "color",
            // 0.63.0（勘测 X1）：`prefix` / `hide-components-when-formed` / `controller-bounding-box`。
            // 这三个字段一直在 UNIMPLEMENTED_ROOT_FIELDS 里（「认得但不求值」），却**从来没有出现在这份名单里**，
            // 于是**数据包里**同一份合法定义会同时收到两句互相打脸的话：「accepted but not evaluated」（真话）与
            // 「this loader does not know」（假话）；而 strict 路径（`MachineBuilderJS.register()` →
            // `read(..., true)`）会把整份定义拒掉。**今天脚本 API 还没有写这三个字段的方法**，所以那条拒收
            // 路径眼下够不着——但「认得」这件事只能有一个答案；写成「够不着所以不用管」，下一次谁给脚本面
            // 加一个能写这些字段的入口（或谁照旧版 JSON 抄一份进脚本），就会当场被拒。
            //
            // **这两份名单是同一个契约**：UNIMPLEMENTED_ROOT_FIELDS 点名的每个字段都必须在这里。
            // 理由不是对称好看，而是「认识」这件事只能有一个答案——作者读到的警告要么是「我懂但不做」，
            // 要么是「我不认识，你大概拼错了」，两者不能同时成立。
            // 复现器的 RootFieldListCheck 用反射钉住这条恒等式（不为断言放宽 private：内部结构不该变成接口）。
            "prefix", "hide-components-when-formed", "controller-bounding-box",
            // 0.70.0（C7）：`geo-model`。旧版没有这个根字段——它走 CraftTweaker 注册表，
            // 本版没有脚本面，所以把同一组资源放进定义里（解析见 GeoModelSpec）。
            "geo-model",
            // 0.85.1（X6）：`registryName`——旧版 DynamicMachine.Deserializer 认的驼峰别名
            // （registryname 为空时回退，DynamicMachine.java:613-619），读取见 readRegistryName。
            // 必须同时列在这里：否则写了别名的合法定义会收到「this loader does not know: registryName」，
            // strict 路径（MachineBuilderJS.register）还会整份拒收——与上面 color/prefix 的坑同源，
            // 两份名单是同一个契约。
            "registryName");
    /**
     * Root fields the loader evaluates but the script API cannot yet write — <b>empty as of 0.31.0</b>, and kept
     * as an empty list on purpose.
     *
     * <h2>这份列表曾经是什么</h2>
     *
     * <p>直到 0.30.1，它列出十一个字段——{@code modifiers}、{@code smart-interfaces}、{@code has-factory}、
     * {@code factory-only}、{@code max-threads}、{@code core-threads}、{@code max-parallelism}、
     * {@code internal-parallelism}、{@code parallelizable}、{@code failure-action} 与
     * {@code requires-blueprint}——脚本只要写出其中一个，就会被<strong>按名拒绝</strong>。另一种做法
     * （接受它然后忽略它）是本项目一直在付代价的那种失败模式：一份看起来说了什么、实际什么都没做的定义。
     *
     * <h2>为什么是置空而不是删除</h2>
     *
     * <p>0.31.0（v2b）给 {@code MachineBuilderJS} 把十一个字段都补上了方法，于是「延迟」这件事没有东西可描述，
     * 拒绝路径也没了——{@link #read} 已经不再调用任何拒绝步骤。
     *
     * <p>这个常量留着，是因为<strong>仍有两个调用者把它当作契约</strong>，删掉它等于删掉一个测试而不是一个行为：
     * 离线复现器的 Z3 段断言这份列表<strong>永不</strong>点名一个 {@link #KNOWN_ROOT_FIELDS} 不认识的字段
     * （拒绝一个不存在的字段等于撒谎），并且<strong>永不</strong>点名一个核心字段（那会破坏 API），
     * 同时断言已经没有任何字段被延迟。因此，将来若有某一版重新开始延迟某个字段，它必须<strong>同时</strong>
     * 把拒绝路径和 {@code rejectDeferred} 调用加回来；Z3 段在那条断言旁边也写着同样的提醒。
     *
     * <p>空列表同时也是最诚实的形状：{@code DEFERRED_ROOT_FIELDS.contains(x)} 对任何 {@code x} 都是
     * {@code false}，这正是世界的现状。
     */
    public static final List<String> DEFERRED_ROOT_FIELDS = List.of();

    private MachineSchema() {
    }

    /** 旧版的宽容做法：未知根字段被报告出来，然后跳过。 */
    public static MachineDefinition read(JsonElement element, Object where, Map<String, List<String>> variables) {
        return read(element, where, variables, false);
    }

    /**
     * 读取一份机器定义。
     *
     * @param element   那份定义，作为一个 JSON 对象
     * @param where     出错消息里用来指代它的名字 —— 数据包用文件 id，脚本用 {@code machine 'foo'}
     * @param variables {@code *.var.json} 变量集；调用者没有时为空
     * @param strict    {@code true} 表示<strong>拒绝</strong>未知根字段，而不是报告后继续
     * @throws JsonParseException 当定义格式错误时抛出，消息里写明「应该怎么写」
     */
    public static MachineDefinition read(JsonElement element, Object where, Map<String, List<String>> variables,
                                         boolean strict) {
        JsonObject root = asObject(element, where);

        String registryName = readRegistryName(root, where);
        ResourceLocation id = registryName.contains(":")
                ? ResourceLocation.tryParse(registryName)
                : new ResourceLocation(ModularMachineryReborn.MOD_ID, registryName);
        if (id == null) {
            throw new JsonParseException("Malformed 'registryname' '" + registryName + "' in " + where);
        }

        String localizedName = root.has("localizedname")
                ? root.get("localizedname").getAsString()
                : id.getPath();

        FailureAction failureAction = MachineDefinition.DEFAULT_FAILURE_ACTION;
        if (root.has("failure-action")) {
            failureAction = readFailureAction(root, where);
        }

        boolean requiresBlueprint = root.has("requires-blueprint")
                && readBoolean(root, "requires-blueprint", where, "Write true or false, without quotes.");

        // 0.71.0（E5）：`hide-components-when-formed` 以前在 UNIMPLEMENTED_ROOT_FIELDS 里「认得但不求值」。
        // 现在求值：类型错误直接拒绝（与 1.12.2 旧版一致；0.70.0 未求值时曾照单全收），
        // 因为一件作者以为打开了的事悄悄关着，比一句明确的报错更贵。
        boolean hideComponentsWhenFormed = MachineRootFields.hideComponentsWhenFormed(root, String.valueOf(where));

        // 0.80.0（E7）：prefix 也离开 UNIMPLEMENTED_ROOT_FIELDS。与上面同一理由——旧版严格读取
        // （空串拒绝），本版照做；没有前缀时明确是 null，而不是把裸语言键画到标题上。
        String prefix = MachineRootFields.prefix(root, String.valueOf(where));

        int maxParallelism = com.reborn.modularmachinery.config.ModConfig.maxMachineParallelism();
        if (root.has("max-parallelism")) {
            maxParallelism = readParallelism(root, "max-parallelism", where);
        }

        int internalParallelism = MachineDefinition.DEFAULT_INTERNAL_PARALLELISM;
        if (root.has("internal-parallelism")) {
            internalParallelism = readParallelism(root, "internal-parallelism", where);
        }

        boolean parallelizable = com.reborn.modularmachinery.config.ModConfig.machineParallelizeEnabledByDefault();
        if (root.has("parallelizable")) {
            JsonElement flag = root.get("parallelizable");
            if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("'parallelizable' of " + where + " must be true or false. "
                        + "Write \"parallelizable\": true to let a parallel controller raise this machine's "
                        + "parallelism, or false to forbid it.");
            }
            parallelizable = flag.getAsBoolean();
        }

        // "internal-parallelism may not exceed max-parallelism" is the same rule the original enforced, but the
        // original enforced it silently: AbstractMachine#setInternalParallelism quietly raised maxParallelism to
        // match. A definition file is read by a person, so a contradiction is reported instead.
        if (internalParallelism > maxParallelism) {
            throw new JsonParseException("'internal-parallelism' of " + where + " is " + internalParallelism
                    + ", which is above its 'max-parallelism' (" + maxParallelism + "). The ceiling must be at "
                    + "least the built-in amount. Raise \"max-parallelism\" to " + internalParallelism
                    + " or more, or lower \"internal-parallelism\".");
        }
        if (!parallelizable && internalParallelism > 1) {
            LOGGER.warn("[{}] {} sets parallelizable=false while internal-parallelism is {}; the machine will "
                            + "still not run in parallel. Set \"parallelizable\": true, or remove "
                            + "'internal-parallelism'.",
                    ModularMachineryReborn.MOD_ID, where, internalParallelism);
        }

        List<MachineModifier> modifiers = readModifiers(root, variables, where);
        List<MachineMultiBlockModifier> multiBlockModifiers = readMultiBlockModifiers(root, variables, where);

        // ---------------------------------------------------------------- M6e: the factory
        int maxThreads = com.reborn.modularmachinery.config.ModConfig.factoryDefaultMaxThread();
        if (root.has(FactoryThreadModel.JSON_MAX_THREADS)) {
            maxThreads = readThreadCount(root, FactoryThreadModel.JSON_MAX_THREADS, where);
        }

        boolean hasFactory = com.reborn.modularmachinery.config.ModConfig.factoryControllerEnabledByDefault();
        if (root.has(FactoryThreadModel.JSON_HAS_FACTORY)) {
            hasFactory = readBoolean(root, FactoryThreadModel.JSON_HAS_FACTORY, where,
                    "Write \"has-factory\": true to give this machine a factory controller (a block that runs "
                            + "several different recipes at once), or remove the field.");
        }

        boolean factoryOnly = MachineDefinition.DEFAULT_FACTORY_ONLY;
        if (root.has(FactoryThreadModel.JSON_FACTORY_ONLY)) {
            factoryOnly = readBoolean(root, FactoryThreadModel.JSON_FACTORY_ONLY, where,
                    "Write \"factory-only\": true to say this machine may only be built around a factory "
                            + "controller, or remove the field.");
        }
        if (factoryOnly && !hasFactory) {
            LOGGER.warn("[{}] {} sets factory-only=true while has-factory is false. The two contradict each "
                            + "other: a factory-only machine has no ordinary controller, so nothing could ever "
                            + "form it. Add \"has-factory\": true, or remove 'factory-only'.",
                    ModularMachineryReborn.MOD_ID, where);
        }

        List<FactoryThreadModel.CoreThreadSpec> coreThreads = readCoreThreads(root, where);

        // ---------------------------------------------------------------- M6d-b: the smart interfaces
        List<SmartInterfaceType> smartInterfaces = readSmartInterfaces(root, where);

        JsonArray parts = asArray(root.get("parts"), where + " 'parts'");
        MachinePattern.Builder builder = MachinePattern.builder();
        boolean ignoredExtras = false;

        ignoredExtras |= readParts(parts, builder, variables, where, true);

        if (builder.isEmpty()) {
            throw new JsonParseException("Machine definition " + where + " has no parts besides the controller");
        }

        // 波 3b：动态结构。基座（上面的 `parts`）之外还可以声明若干「沿某个面重复」的段落。
        List<DynamicPattern> dynamicPatterns = readDynamicPatterns(root, variables, where);

        if (ignoredExtras) {
            LOGGER.warn("[{}] {} uses 'nbt' or 'preview-nbt', which M1 does not evaluate yet; "
                    + "those positions match on block state only", ModularMachineryReborn.MOD_ID, where);
        }

        List<String> unimplemented = new ArrayList<>();
        for (String key : UNIMPLEMENTED_ROOT_FIELDS) {
            if (root.has(key)) {
                unimplemented.add(key);
            }
        }
        if (!unimplemented.isEmpty()) {
            LOGGER.warn("[{}] {} declares {} — accepted but not evaluated; the fields are kept so the "
                    + "definitions stay faithful to the original", ModularMachineryReborn.MOD_ID, where,
                    String.join(", ", unimplemented));
        }

        // A "//"-prefixed key is the convention this project's examples use for an inline note. It is a legal
        // JSON member, so it is accepted — but a typo is not, and the two look alike in a diff. Only unknown
        // keys that do not start with "//" are reported.
        List<String> unknown = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.startsWith("//") || KNOWN_ROOT_FIELDS.contains(key)) {
                continue;
            }
            unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            if (strict) {
                throw new JsonParseException(where + " has root field(s) this schema does not know: "
                        + String.join(", ", unknown) + ". Nothing would read them, so the definition is "
                        + "refused rather than accepted with a field that does nothing. Check the spelling "
                        + "against the documented fields (" + String.join(", ", KNOWN_ROOT_FIELDS)
                        + "); a note can be written as a \"//...\" key.");
            }
            LOGGER.warn("[{}] {} has root field(s) this loader does not know: {}. They are ignored. If they were "
                            + "meant to have an effect, check the spelling against the documented fields "
                            + "({}); a note can be written as a \"//...\" key.",
                    ModularMachineryReborn.MOD_ID, where, String.join(", ", unknown),
                    String.join(", ", KNOWN_ROOT_FIELDS));
        }

        // 波 6 第 2 项（color）的第一小步：**解析并保存**颜色。染色本身（模型 tint 层 + 颜色处理器）
        // 是下一小步——这一步证明的是「定义里写什么颜色，读出来就是什么」，而那是离线可证的。
        // 解析规则见 MachineRootFields#color / MachineDefinition#parseColor。
        MachineDefinition definition = new MachineDefinition(id, localizedName, builder.build(), failureAction, requiresBlueprint,
                maxParallelism, internalParallelism, parallelizable, modifiers,
                maxThreads, hasFactory, factoryOnly, coreThreads, smartInterfaces, dynamicPatterns)
                .withColor(MachineRootFields.color(root, String.valueOf(where)))
                .withGeoModel(MachineRootFields.geoModel(root, String.valueOf(where)))
                .withHideComponentsWhenFormed(hideComponentsWhenFormed)
                .withMultiBlockModifiers(multiBlockModifiers)
                .withPrefix(prefix)
                .withControllerBoundingBox(
                        MachineRootFields.controllerBoundingBox(root, String.valueOf(where)));
        return definition.withSynchronizationSource(synchronizationSource(root, variables, definition));
    }

    /** Preserve the whole author document, expanding only schema-owned elements and pinning adopted defaults. */
    public static JsonObject synchronizationSource(JsonObject root, Map<String, List<String>> variables,
                                                   MachineDefinition definition) {
        JsonObject copy = root.deepCopy();
        expandSyncParts(copy.get("parts"), variables, definition.id());
        expandSyncParts(copy.get("modifiers"), variables, definition.id());
        if (copy.has("multi-block-modifiers")) for (JsonElement entry : copy.getAsJsonArray("multi-block-modifiers"))
            expandSyncParts(entry.getAsJsonObject().get("parts"), variables, definition.id());
        if (copy.has("dynamic-patterns")) for (JsonElement entry : copy.getAsJsonArray("dynamic-patterns")) {
            expandSyncParts(entry.getAsJsonObject().get("parts"), variables, definition.id());
            expandSyncParts(entry.getAsJsonObject().get("parts-end"), variables, definition.id());
        }
        copy.addProperty("max-parallelism", definition.maxParallelism());
        copy.addProperty("internal-parallelism", definition.internalParallelism());
        copy.addProperty("parallelizable", definition.parallelizable());
        copy.addProperty("max-threads", definition.maxThreads());
        copy.addProperty("has-factory", definition.hasFactory());
        copy.addProperty("color", String.format(java.util.Locale.ROOT, "%06X", definition.color()));
        if (copy.toString().length() > (1 << 20))
            throw new JsonParseException(definition.id() + " expanded synchronization JSON exceeds 1MiB characters");
        return copy;
    }

    private static void expandSyncParts(@javax.annotation.Nullable JsonElement raw, Map<String, List<String>> variables,
                                        Object where) {
        if (raw == null) return;
        for (JsonElement entry : raw.getAsJsonArray()) {
            JsonObject part = entry.getAsJsonObject();
            JsonArray expanded = new JsonArray();
            for (BlockMatcher matcher : readElements(part, variables, where)) expanded.add(matcher.descriptor());
            part.add("elements", expanded);
        }
    }

    // 0.31.0 removed rejectDeferred(JsonObject, Object, boolean) and its call above read()'s body. It existed to
    // refuse the fields in DEFERRED_ROOT_FIELDS by name on the script path; with the list empty it could only
    // return, so keeping it would have been a loop over nothing — the same "an assertion that cannot fail is not
    // evidence" reasoning the harness applies to its own checks. A future slice that defers a field again
    // re-introduces the call together with the list entry; section Z3 asserts the list's invariants either way.

    /**
     * {@code failure-action}：旧版的根级字符串，取值是 {@code reset} / {@code still} / {@code decrease} 之一
     * （{@code RecipeFailureActions.java:15-18}），读取位置与旧版一致
     * （{@code DynamicMachinePreDeserializer.java:44-51}），并挂在定义上
     * （{@code DynamicMachine.java:630-633}）。
     *
     * <p>与旧版有两处刻意不同，都遵循本项目对「由人手写的字段」的惯例。第一，值不是字符串时会被
     * <strong>如实</strong>报告成类型错误——旧版也有类型检查，但它的消息里写的是那三个取值而不是类型，
     * 于是 `"failure-action": true` 收到的是关于取值范围的话。第二，未知的<strong>名字</strong>会被拒绝，
     * 而不是静默地变成默认值——这正是 {@code RecipeFailureActions#getFailureAction:49-56} 的做法：
     * 打错一个字是看不见的。
     */
    private static FailureAction readFailureAction(JsonObject root, Object where) {
        JsonElement element = root.get("failure-action");
        String vocabulary = "\"reset\", \"still\" or \"decrease\"";
        String guidance = " Write \"failure-action\": \"still\" to hold a craft's progress while it cannot be "
                + "served, \"decrease\" to lose one tick of progress per failed tick, or \"reset\" to drop the "
                + "whole craft's progress; remove the field to use the default ("
                + MachineDefinition.DEFAULT_FAILURE_ACTION.name().toLowerCase(Locale.ROOT) + ").";
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'failure-action' of " + where + " must be a string — one of "
                    + vocabulary + " (the original's RecipeFailureActions). Found " + element + "." + guidance);
        }
        String raw = element.getAsString();
        for (FailureAction action : FailureAction.values()) {
            if (action.name().equalsIgnoreCase(raw)) {
                return action;
            }
        }
        throw new JsonParseException("'failure-action' of " + where + " must be one of " + vocabulary
                + "; found \"" + raw + "\"." + guidance);
    }

    /**
     * {@code max-threads}：一个非负整数。取值非法时<strong>报告</strong>而不是夹取，这样打错一个字不会静默地
     * 把一台工厂变成单线程。
     */
    private static int readThreadCount(JsonObject root, String key, Object where) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be a whole number. Write a "
                    + "non-negative integer such as \"max-threads\": 4 — the number of recipes this machine's "
                    + "factory controller may run at once — or remove the field (the default is "
                    + com.reborn.modularmachinery.config.ModConfig.factoryDefaultMaxThread() + ").");
        }
        int value = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 0, Integer.MAX_VALUE, where + " '" + key + "'");
        if (value < 0) {
            throw new JsonParseException("'" + key + "' of " + where + " is " + value + ", but a thread count "
                    + "cannot be negative. Write 0 for \"this machine may run nothing on a factory controller "
                    + "unless a core thread is declared\", or remove the field.");
        }
        return value;
    }

    /**
     * 两个工厂布尔值之一。旧版抛的是 {@code JsonParseException("'has-factory' has to be
     * either 'true' or 'false'!")}（{@code DynamicMachinePreDeserializer:77-89}）；这里的消息保留那个形状，
     * 并补上这个字段到底是干什么的——旧版的消息没有这一半。
     */
    private static boolean readBoolean(JsonObject root, String key, Object where, String advice) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be true or false. " + advice);
        }
        return element.getAsBoolean();
    }

    /**
     * {@code core-threads}：<strong>始终存在</strong>的那组预设线程。
     *
     * <pre>{@code "core-threads": [ { "name": "smelter", "recipes": ["alloy_smelter_diamond"] } ] }</pre>
     *
     * <p>旧版同样没有对应的 JSON：{@code DynamicMachine#addCoreThread} 是被 CraftTweaker 脚本调用的，而
     * {@code FactoryRecipeThread#createCoreThread(threadName)} 负责造出线程。它只需要两个字段——它被索引的名字
     * （旧版的 {@code threadName}）与固定的配方集（{@code recipeSet}）；{@code recipes} 省略或为空表示这条线程
     * 可以跑这台机器提供的任何配方，这也是旧版读到一个空集时的行为
     * （{@code FactoryRecipeThread.java:161-163}）。
     */
    private static List<FactoryThreadModel.CoreThreadSpec> readCoreThreads(JsonObject root, Object where) {
        JsonElement element = root.get(FactoryThreadModel.JSON_CORE_THREADS);
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'" + FactoryThreadModel.JSON_CORE_THREADS + "' of " + where
                    + " must be an array of thread objects. Write \"core-threads\": [ { \"name\": \"smelter\", "
                    + "\"recipes\": [ \"alloy_smelter_diamond\" ] } ], or remove the field.");
        }
        List<FactoryThreadModel.CoreThreadSpec> specs = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject thread = asObject(entry, where + " core thread #" + index);
            if (!thread.has("name")) {
                throw new JsonParseException("Core thread #" + index + " of " + where + " has no 'name'. A core "
                        + "thread is identified by its name (the original's threadName), so write "
                        + "\"name\": \"smelter\".");
            }
            String name = thread.get("name").getAsString();
            if (name.isBlank()) {
                throw new JsonParseException("Core thread #" + index + " of " + where + " has an empty 'name'. "
                        + "Give it a name to write down, e.g. \"name\": \"smelter\".");
            }
            if (!names.add(name)) {
                throw new JsonParseException("Two core threads of " + where + " are both named '" + name
                        + "'. Names key the threads, so they have to differ — rename one of them.");
            }

            List<ResourceLocation> recipes = new ArrayList<>();
            if (thread.has("recipes")) {
                JsonElement raw = thread.get("recipes");
                if (!raw.isJsonArray()) {
                    throw new JsonParseException("'recipes' of core thread '" + name + "' in " + where
                            + " must be an array of recipe names. Write \"recipes\": [ \"alloy_smelter_diamond\" "
                            + "], or remove the field to let the thread run any recipe this machine has.");
                }
                for (JsonElement recipe : raw.getAsJsonArray()) {
                    if (!recipe.isJsonPrimitive()) {
                        throw new JsonParseException("'recipes' of core thread '" + name + "' in " + where
                                + " must contain only recipe names (strings)");
                    }
                    String raw2 = recipe.getAsString();
                    ResourceLocation id = raw2.contains(":")
                            ? ResourceLocation.tryParse(raw2)
                            : new ResourceLocation(ModularMachineryReborn.MOD_ID, raw2);
                    if (id == null) {
                        throw new JsonParseException("'" + raw2 + "' in the 'recipes' of core thread '" + name
                                + "' of " + where + " is not a valid recipe name. Write a recipe file's own id, "
                                + "e.g. \"modular_machinery_reborn:alloy_smelter_diamond\".");
                    }
                    recipes.add(id);
                }
            }
            specs.add(new FactoryThreadModel.CoreThreadSpec(name, recipes));
            index++;
        }
        return List.copyOf(specs);
    }

    /**
     * {@code smart-interfaces}：这台机器声明的接口类型——旧版从来没有过的那个 JSON 入口
     * （见 {@link SmartInterfaceType} 的类注释与 D16 记录）。
     *
     * <pre>{@code
     * "smart-interfaces": [
     *   { "type": "mode", "default": 0, "priority": 1000,
     *     "header": "gui.example.mode.header", "footer": "", "notequal": "gui.example.mode.mismatch" }
     * ]
     * }</pre>
     *
     * <p>除 {@code type} 外每个字段都是可选的，而 {@code type} 正是旧版也要求的唯一一项
     * （{@code smartInterfaces} 是一个 {@code Map<String, SmartInterfaceType>}，所以没有名字的类型根本无法被查到）。
     * 这里「吵闹」的那一半是：取值格式错误时消息会告诉你应该怎么写——旧版在这里<strong>完全没有 schema</strong>，
     * 所以没有旧版可对齐，只有本项目自己的惯例（D12 那句「定义文件是给人读的」）。
     */
    private static List<SmartInterfaceType> readSmartInterfaces(JsonObject root, Object where) {
        JsonElement element = root.get("smart-interfaces");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'smart-interfaces' of " + where + " must be an array of interface "
                    + "type objects. Write \"smart-interfaces\": [ { \"type\": \"mode\", \"default\": 0, "
                    + "\"priority\": 1000 } ], or remove the field to declare no interface type at all (then no "
                    + "interface_number_input requirement can ever be satisfied on this machine).");
        }

        List<SmartInterfaceType> types = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject type = asObject(entry, where + " smart interface #" + index);
            if (!type.has("type")) {
                throw new JsonParseException("Smart interface #" + index + " of " + where + " has no 'type'. The "
                        + "type's name is what a recipe's interface_number_input requirement quotes, so write "
                        + "\"type\": \"mode\".");
            }
            String name = type.get("type").getAsString();
            if (name.isBlank()) {
                throw new JsonParseException("Smart interface #" + index + " of " + where + " has an empty "
                        + "'type'. Give it a name to quote from a recipe, e.g. \"type\": \"mode\".");
            }
            if (!names.add(name)) {
                throw new JsonParseException("Two smart interfaces of " + where + " are both named '" + name
                        + "'. The name is how a requirement finds the value, so a duplicate would make one of "
                        + "them unreachable — rename one of them.");
            }

            float defaultValue = 0.0F;
            if (type.has("default")) {
                JsonElement raw = type.get("default");
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("'default' of smart interface '" + name + "' in " + where
                            + " must be a number. Write the value a freshly formed machine starts from, e.g. "
                            + "\"default\": 0, or remove the field (the default is 0).");
                }
                defaultValue = readFloat(raw, "'default' of smart interface '" + name + "' in " + where);
            }

            int priority = 0;
            if (type.has("priority")) {
                JsonElement raw = type.get("priority");
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("'priority' of smart interface '" + name + "' in " + where
                            + " must be a whole number. Write the number that decides which declared type an "
                            + "undeclared value binds to (the largest wins), e.g. \"priority\": 1000, or remove "
                            + "the field (the default is 0).");
                }
                priority = com.reborn.modularmachinery.util.JsonNumbers.integer(raw, Integer.MIN_VALUE, Integer.MAX_VALUE, where + " smart-interface.priority");
            }

            types.add(new SmartInterfaceType(name, defaultValue,
                    readOptionalText(type, "header", name, where),
                    readOptionalText(type, "value", name, where),
                    readOptionalText(type, "footer", name, where),
                    priority,
                    readOptionalText(type, "notequal", name, where)));
            index++;
        }
        return List.copyOf(types);
    }

    /**
     * 三个可选显示字符串之一，缺失时为 {@code null}。写了但不是字符串会报错，而不是静默地把数字转成字符串——
     * 因为这些是翻译键，这里出现一个数字永远是错的。
     */
    @javax.annotation.Nullable
    private static String readOptionalText(JsonObject entry, String key, String name, Object where) {
        if (!entry.has(key)) {
            return null;
        }
        JsonElement element = entry.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'" + key + "' of smart interface '" + name + "' in " + where
                    + " must be a string. Write a translation key such as \"gui.example.mode." + key
                    + "\" (or remove the field); a number or an object here can never be printed.");
        }
        return element.getAsString();
    }

    /**
     * 一个必须扛得住 JSON 解析器自身数字处理的浮点数。Gson 对任何数字都能答出 {@code getAsFloat}，
     * 但字面量 {@code true} 并不是数字——尽管 {@code getAsFloat} 会乐意把它读成 1。所以在读取之前先做
     * 「是原始值且是数字」的检查，与 {@code readParallelism} 一样。
     */
    private static float readFloat(JsonElement element, String where) {
        try {
            float value = element.getAsFloat();
            if (!Float.isFinite(value)) {
                throw new JsonParseException("The " + where + " must be a finite float");
            }
            return value;
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new JsonParseException("The " + where + " is not a number that fits a float");
        }
    }

    /**
     * 两个并行度整数之一。两者都要求非负，且格式错误时都是<strong>报告</strong>而不是夹取，
     * 这样定义里打错一个字不会静默地把一台机器变成不能并行、或变成无上限。
     */
    private static int readParallelism(JsonObject root, String key, Object where) {
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' of " + where + " must be a whole number. Write a "
                    + "non-negative integer such as \"max-parallelism\": 64, or remove the field.");
        }
        int value = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 0, Integer.MAX_VALUE, where + " '" + key + "'");
        if (value < 0) {
            throw new JsonParseException("'" + key + "' of " + where + " is " + value + ", but a parallelism "
                    + "cannot be negative. The original floored it at 0 (a machine with no parallel controller "
                    + "still runs exactly one copy), so write 0 for \"no built-in parallelism\".");
        }
        return value;
    }

    /**
     * {@code modifiers} 数组：旧版那套<strong>按方块位置</strong>生效的配方修改器。
     *
     * <p>每一项点明一个位置（旧版的 {@code addModifierWithPattern} 也接受坐标数组，这里按 {@code parts}
     * 同样的方式展开），再加上「哪些方块可以放在那里」以及那些方块贡献的修改器。
     */
    private static List<MachineModifier> readModifiers(JsonObject root, Map<String, List<String>> variables,
                                                       Object where) {
        JsonElement element = root.get("modifiers");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'modifiers' of " + where + " must be an array of modifier objects. "
                    + "Write \"modifiers\": [ { \"elements\": ..., \"x\": 0, \"y\": 1, \"z\": 0, "
                    + "\"modifier\": { ... } } ], or remove the field.");
        }

        List<MachineModifier> modifiers = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject part = asObject(entry, where + " modifier");
            List<Integer> xs = readCoordinates(part, "x", where);
            List<Integer> ys = readCoordinates(part, "y", where);
            List<Integer> zs = readCoordinates(part, "z", where);
            List<BlockMatcher> accepted = readElements(part, variables, where);

            // The original accepted either a single 'modifier' object or a 'modifiers' array of them; both
            // spellings appear in the wild and both are kept.
            List<RecipeModifier> parsed = readRecipeModifiers(part, where);

            String description = part.has("description") ? part.get("description").getAsString() : "";
            boolean any = false;
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        if (x == 0 && y == 0 && z == 0) {
                            // The original's addModifierWithPattern skipped the controller's own position.
                            continue;
                        }
                        modifiers.add(new MachineModifier(new BlockPos(x, y, z), accepted, parsed, description));
                        any = true;
                    }
                }
            }
            if (!any) {
                LOGGER.warn("[{}] A modifier of {} sits at the controller's own position (0,0,0), which the "
                                + "original skipped; ignoring it. Give it the structure position whose block it "
                                + "reacts to, e.g. \"x\": 0, \"y\": 1, \"z\": 0.",
                        ModularMachineryReborn.MOD_ID, where);
            }
        }
        return List.copyOf(modifiers);
    }

    /**
     * 读 {@code multi-block-modifiers}：每项是一整段额外结构条件，命中时贡献它的 {@code modifier}。
     *
     * <p>与单块 {@code modifiers} 的区别只有两点：坐标描述的是<strong>一整段结构</strong>（因此复用
     * {@code readParts}，但<strong>不跳过原点</strong>——旧版 {@code BlockArray} 没有坐标限制），以及
     * 没有 {@code x/y/z} 这一层可按坐标展开的外壳。错误文案点名根字段与第几项，避免作者在几十条里找。
     */
    private static List<MachineMultiBlockModifier> readMultiBlockModifiers(JsonObject root,
                                                                          Map<String, List<String>> variables,
                                                                          Object where) {
        JsonElement element = root.get("multi-block-modifiers");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("'multi-block-modifiers' of " + where + " must be an array of "
                    + "structure conditions. Write \"multi-block-modifiers\": [ { \"parts\": [ ... ], "
                    + "\"modifier\": { ... } } ], or remove the field.");
        }
        List<MachineMultiBlockModifier> result = new ArrayList<>();
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            Object entryWhere = where + " 'multi-block-modifiers'[" + index + "]";
            JsonObject part = asObject(entry, entryWhere);
            if (!part.has("parts")) {
                throw new JsonParseException(entryWhere + " has no 'parts'. A multi-block modifier is a whole "
                        + "extra structure, so it needs at least one part (the controller itself need not be "
                        + "listed: coordinates are relative to it).");
            }
            JsonArray parts = asArray(part.get("parts"), entryWhere + " 'parts'");
            if (parts.isEmpty()) {
                throw new JsonParseException("'parts' of " + entryWhere + " is empty. A multi-block modifier "
                        + "needs at least one part; remove the field or list the structure it requires.");
            }
            MachinePattern.Builder builder = MachinePattern.builder();
            readParts(parts, builder, variables, entryWhere, false);
            List<RecipeModifier> parsed = readRecipeModifiers(part, entryWhere);
            String description = part.has("description") ? part.get("description").getAsString() : "";
            result.add(new MachineMultiBlockModifier(builder.build(), parsed, description));
            index++;
        }
        return List.copyOf(result);
    }

    /**
     * 读一个对象里的 {@code modifier} / {@code modifiers}，两种写法都收。
     *
     * <p>单块 {@code modifiers} 与多块 {@code multi-block-modifiers} 共用这段解析：两处对「写成什么、
     * 空数组怎么报」必须一字不差，否则作者会在两个相近字段上得到两套规则。错误文案里的 {@code where}
     * 由调用者给，单块路径保持原来的说法不变。
     */
    private static List<RecipeModifier> readRecipeModifiers(JsonObject part, Object where) {
        JsonElement rawModifiers = part.has("modifier") ? part.get("modifier") : part.get("modifiers");
        if (rawModifiers == null) {
            throw new JsonParseException("A modifier of " + where + " has neither 'modifier' nor "
                    + "'modifiers'. Write \"modifier\": { \"target\": \"item\", \"io\": \"output\", "
                    + "\"operation\": 1, \"multiplier\": 2.0 }.");
        }
        List<RecipeModifier> parsed = new ArrayList<>();
        if (rawModifiers.isJsonArray()) {
            for (JsonElement single : rawModifiers.getAsJsonArray()) {
                parsed.add(RecipeModifier.parse(single, where));
            }
            if (parsed.isEmpty()) {
                throw new JsonParseException("The 'modifiers' array of a modifier in " + where + " is empty");
            }
        } else {
            parsed.add(RecipeModifier.parse(rawModifiers, where));
        }
        return List.copyOf(parsed);
    }

    /**
     * 本 schema <strong>认得、但本项目还没作用</strong>的旧版根字段。它们在加载时被报告出来，而不是被静默丢弃。
     *
     * <p>{@code modifiers} 在 0.19.0（M6c）离开了这份列表：结构位置现在真的会贡献它的配方修改器。
     * {@code has-factory} / {@code factory-only} 在 0.22.0（M6e）连同两个新线程字段一起离开。
     * {@code failure-action} 在 0.25.0（D17）离开。{@code dynamic-patterns} 在 0.32.0（波 3b）离开：
     * 动态结构的长度现在是<strong>真的会被量出来</strong>的。
     * {@code hide-components-when-formed} 在 0.71.0（E5）离开：预览现在会读它（成型后隐藏组件），
     * 见 {@link MachineDefinition#hideComponentsWhenFormed()} 与 {@code StructurePreview#hidingComponents()}。
     * {@code prefix} 在 0.80.0（E7）离开：预览标题左侧会显示它，解析也恢复旧版的非空字符串检查。
     * {@code controller-bounding-box} 在 0.81.0（E6）离开：控制器的选中箱/碰撞箱真的会按这六个数字
     * 收窄，见 {@link MachineDefinition#controllerBoundingBox()} 与
     * {@code MachineControllerBlock#getShape}。
     *
     * <p>名单现在是<strong>空的</strong>，但结构保留：下一个「认得、还不求值」的字段仍然要先登记在这里，
     * 让加载日志说真话。空数组不是「这段代码没用」——它让「所有已知根字段都已求值」成为一条可检查的
     * 事实（复现器断言 {@code length == 0}）。
     *
     * <p><b>这份名单点名的每个字段，同时必须出现在 {@link #KNOWN_ROOT_FIELDS} 里。</b>这不是排版上的对称，
     * 而是一条会被作者读到的语义：不在那份名单里，加载器会在「认得但不求值」的警告之后紧接着再报一句
     * 「this loader does not know」——后者是假话——而 strict 路径（{@code MachineBuilderJS.register()}）
     * 会据此拒收整份合法定义。0.63.0 修的就是这件事（勘测 X1）：当时 prefix / hide-components-when-formed /
     * controller-bounding-box 只在这份名单里。
     */
    private static final String[] UNIMPLEMENTED_ROOT_FIELDS = {
    };

    /**
     * 一个部件的 {@code selector-tag}，没写时为 {@code null}。
     *
     * <p>旧版在这里只做了一件事：确认它是字符串，否则抛
     * {@code JsonParseException("The 'selector-tag' in an element must be a string!")}
     * （{@code DynamicMachine.java:289-292}）。这里保留那条规则，并沿用本项目对「人手写字段」的惯例把该写什么说清楚。
     *
     * <p>写成空串会被归一成「没有标签」，理由见 {@link MachinePattern.Builder#tag}：旧版的
     * {@code ComponentSelectorTag} 构造器也这么归一。
     */
    @javax.annotation.Nullable
    private static String readSelectorTag(JsonObject part, Object where) {
        if (!part.has("selector-tag")) {
            return null;
        }
        JsonElement element = part.get("selector-tag");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("'selector-tag' of " + where + " must be a string, but it is "
                    + element + ". Write it as a name a recipe can name back, for example "
                    + "\"selector-tag\": \"input\".");
        }
        String tag = element.getAsString();
        return tag.isEmpty() ? null : tag;
    }

    /**
     * 读一段 {@code parts} 数组，写进 {@code builder}，返回「是否遇到了本项目还不求值的字段」。
     *
     * <p>抽出来是因为波 3b 之后同一个解析要跑三处：根的 {@code parts}（固定基座）、
     * {@code dynamic-patterns[].parts}（重复单元）与 {@code parts-end}（收尾段）。
     *
     * <p><b>{@code skipOrigin} 是这三处唯一的差别，而它曾经是一个真实缺陷。</b>根 {@code parts} 里的
     * {@code (0,0,0)} 是<strong>控制器自己站的那一格</strong>，所以必须跳过；但动态段的 {@code parts}
     * 是<strong>相对每一段自己</strong>的坐标，{@code (0,0,0)} 就是「这一段的那一格」，是完全合法的位置——
     * 而它恰恰是作者会用来放 {@code selector-tag} 的那一格（装配线正中央的输入总线）。
     *
     * <p>第一版把「跳过原点」这条规则同时用在了三处，于是<strong>动态段的正中央那一格连同它的标签被
     * 整个丢掉</strong>，而症状是「仓口都在、机器也成型，但一个端口都收集不到」——诊断命令的世界扫描
     * 才把它抓出来。当时的离线断言没覆盖到，是因为那些测试定义把重复段的坐标写成了 {@code (0,0,1)}
     * 而不是 {@code (0,0,0)}，<strong>刚好绕过了这个分支</strong>。
     *
     * @param skipOrigin 仅根 {@code parts} 为 {@code true}：跳过控制器所在的原点
     */
    private static boolean readParts(JsonArray parts, MachinePattern.Builder builder,
                                     Map<String, List<String>> variables, Object where,
                                     boolean skipOrigin) {
        boolean ignoredExtras = false;
        int index = 0;
        for (JsonElement partElement : parts) {
            JsonObject part = asObject(partElement, where + " part");
            List<Integer> xs = readCoordinates(part, "x", where);
            List<Integer> ys = readCoordinates(part, "y", where);
            List<Integer> zs = readCoordinates(part, "z", where);
            List<BlockMatcher> accepted = readElements(part, variables, where);
            String selectorTag = readSelectorTag(part, where);

            JsonObject rule = new JsonObject();
            for (String key : List.of("nbt", "preview-nbt", "script-checker")) {
                if (part.has(key)) rule.add(key, part.get(key).deepCopy());
            }
            if (part.has("nbt")) com.reborn.modularmachinery.recipe.NbtPredicate.parse(part.get("nbt"), where);
            if (part.has("preview-nbt")) com.reborn.modularmachinery.recipe.NbtPredicate.previewTag(part.get("preview-nbt"), where);
            if (part.has("script-checker")) validateScriptChecker(part.get("script-checker"),
                    partWhere(where, index, xs, ys, zs));

            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        if (skipOrigin && x == 0 && y == 0 && z == 0) {
                            // 控制器占据原点，它不是结构的一部分。**只对根 parts 成立**。
                            continue;
                        }
                        BlockPos pos = new BlockPos(x, y, z);
                        builder.add(pos, accepted);
                        builder.rule(pos, rule);
                        // 一个部件展开了多个坐标时，每个坐标都拿到同一个标签——旧版也是这样：
                        // `selector-tag` 是**部件**的属性，坐标数组只是把同一个部件写在多处
                        // （`DynamicMachine.addParts` 对每个 permutation 设的是同一个 selector，
                        // `DynamicMachine.java:296-305`）。
                        builder.tag(pos, selectorTag);
                    }
                }
            }
            index++;
        }
        return ignoredExtras;
    }

    /**
     * P07-01 返工（0.121.38）：部件级定位——校验错误的 {@code where} 追加部件在数组中的序号与
     * 它覆盖的坐标范围（单值直接给数字，多值给 {@code [min..max]}）。一台机器几十个部件时，
     * 「同机多个部件中的哪一个写了非法 {@code script-checker}」由此一眼可辨，而不是只有机器名。
     */
    private static String partWhere(Object where, int index,
                                    List<Integer> xs, List<Integer> ys, List<Integer> zs) {
        return where + " part " + index + " (x=" + span(xs) + ", y=" + span(ys) + ", z=" + span(zs) + ")";
    }

    /** 单值给数字，多值给 {@code [min..max]}——部件坐标展开的最简定位。 */
    private static String span(List<Integer> values) {
        int min = java.util.Collections.min(values);
        int max = java.util.Collections.max(values);
        return min == max ? Integer.toString(min) : "[" + min + ".." + max + "]";
    }

    /**
     * P07-01（0.121.36）：{@code script-checker} 在定义读取时就地校验——只接受 JSON 字符串 token。
     *
     * <p>旧版这里只拷贝不校验（旁边的 {@code nbt}/{@code preview-nbt} 都有自己的解析），对象、null、
     * 数组等错误类型一路活到服务端结构检查：{@code MachinePattern.matchesRule} 在把 token 交给
     * {@code ScriptHooks.checkBlock} <strong>之前</strong>先求值 {@code getAsString()}，异常从参数求值
     * 阶段逃出匹配与控制器 tick。本批在加载入口拒绝：token 必须是字符串且非空白——正文的原始
     * 字符串按原样保留（这是已有脚本标识：不 trim、不改大小写、不要求是 ResourceLocation，
     * 也不要求加载时已注册回调；未注册的合法 token 仍由运行期安全拒绝）。错误信息带来源、
     * 字段名与值类型，方便发布前定位。根 parts、坐标数组、dynamic 段与多块修改器都经
     * {@link #readParts} 读取，这一处校验对四条路径同时生效。返工（0.121.38）后错误带
     * 部件序号与坐标范围（见 {@link #partWhere}），多部件机器可直接定位到具体部件。
     */
    private static void validateScriptChecker(com.google.gson.JsonElement value, Object where) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !value.getAsString().isBlank()) {
            return;
        }
        String kind = value == null ? "null" : value.isJsonPrimitive() ? value.getAsString()
                : value.isJsonObject() ? "an object" : value.isJsonArray() ? "an array"
                : value.isJsonPrimitive() ? "a number/boolean" : "this JSON value";
        throw new com.google.gson.JsonParseException("'script-checker' in " + where
                + " must be a non-empty script token string (the same id a KubeJS block checker is registered "
                + "with), but the definition wrote " + kind + ". Write the registered checker id once, e.g. "
                + "\"script-checker\": \"my_pack:checks/stone_only\".");
    }

    /**
     * 读 {@code dynamic-patterns}：一段可以沿某个面重复的结构。
     *
     * <p>字段与校验逐条对齐旧版的 {@code DynamicMachine.addDynamicPatterns} 及其四个 setter。
     * 旧版那套报错文案（{@code "Empty 'dynamic-patterns'!"}、{@code "Pattern must have a 'name'!"} 等）
     * 保留原意但补上「该写什么」，这是本项目对「人手写字段」的一贯做法。
     *
     * <p><strong>有一处校验是本版新加的，因为旧版那条是空转的。</strong>旧版 {@code setMaxSize} 里有
     * {@code if (pattern.getMinSize() > maxSize)}，但它在 {@code minSize} 被赋值<strong>之前</strong>运行
     * （解析顺序是 maxSize → parts → parts-end → minSize），所以读到的永远是构造器初值 0，那条检查
     * 从来不触发。这里把 {@code minSize <= maxSize} 在两者都读到之后检查一次。
     */
    private static List<DynamicPattern> readDynamicPatterns(JsonObject root, Map<String, List<String>> variables,
                                                            Object where) {
        if (!root.has("dynamic-patterns")) {
            return List.of();
        }
        JsonElement element = root.get("dynamic-patterns");
        if (!element.isJsonArray()) {
            throw new JsonParseException("'dynamic-patterns' of " + where + " must be an array of pattern "
                    + "objects. Write \"dynamic-patterns\": [ { \"name\": ..., \"faces\": [...], "
                    + "\"maxSize\": ..., \"parts\": [...] } ], or remove the field.");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            // 旧版：`Empty 'dynamic-patterns'!`。写了这个字段却什么都不写，几乎总是编辑到一半留下的。
            throw new JsonParseException("'dynamic-patterns' of " + where + " is empty. Remove the field, or "
                    + "declare at least one pattern with a 'name', 'faces', 'maxSize' and 'parts'.");
        }

        List<DynamicPattern> patterns = new ArrayList<>(array.size());
        // Saved structures are restored by segment name (MachineStructures looks the name up), so two same-named
        // entries would both rebuild with the last measured size. Names are compared by exact string identity.
        Map<String, Integer> seenNames = new LinkedHashMap<>(array.size());
        for (JsonElement patternElement : array) {
            JsonObject pattern = asObject(patternElement, where + " 'dynamic-patterns' entry");

            String name = requireString(pattern, "name", where + " dynamic pattern");
            Integer firstEntry = seenNames.putIfAbsent(name, patterns.size());
            if (firstEntry != null) {
                throw new JsonParseException("Dynamic pattern name '" + name + "' appears twice in " + where
                        + " (entries " + firstEntry + " and " + patterns.size() + "). One machine cannot declare two "
                        + "dynamic segments with the same name, because saved structures are restored by name; "
                        + "rename one of the two segments.");
            }
            Set<Direction> faces = readFaces(pattern, name, where);
            int maxSize = readPatternSize(pattern, "maxSize", name, where, true, 0);
            int minSize = readPatternSize(pattern, "minSize", name, where, false, 0);
            if (minSize > maxSize) {
                throw new JsonParseException("'minSize' of dynamic pattern '" + name + "' in " + where + " is "
                        + minSize + ", which is above its 'maxSize' (" + maxSize + "). Raise \"maxSize\" to "
                        + minSize + " or more, or lower \"minSize\".");
            }

            MachinePattern.Builder partBuilder = MachinePattern.builder();
            readParts(asArray(pattern.get("parts"),
                    "dynamic pattern '" + name + "' 'parts' in " + where), partBuilder, variables,
                    where + " dynamic pattern '" + name + "'", false);
            if (partBuilder.isEmpty()) {
                throw new JsonParseException("dynamic pattern '" + name + "' of " + where + " has no parts "
                        + "besides the origin; a repeating section cannot be empty.");
            }

            MachinePattern endPart = null;
            if (pattern.has("parts-end")) {
                MachinePattern.Builder endBuilder = MachinePattern.builder();
                readParts(asArray(pattern.get("parts-end"),
                        "dynamic pattern '" + name + "' 'parts-end' in " + where), endBuilder, variables,
                        where + " dynamic pattern '" + name + "' 'parts-end'", false);
                if (!endBuilder.isEmpty()) {
                    endPart = endBuilder.build();
                }
            }

            BlockPos startOffset = readPatternOffset(pattern, "structure-size-offset-start", name, where);
            BlockPos stepOffset = readPatternOffset(pattern, "structure-size-offset", name, where);
            if (maxSize > 0 && BlockPos.ZERO.equals(stepOffset)) {
                throw new JsonParseException("Repeating dynamic pattern '" + name + "' in " + where
                        + " requires a nonzero 'structure-size-offset'. For example, write {\"z\":1}. "
                        + "A zero step is only valid when maxSize is 0.");
            }

            patterns.add(new DynamicPattern(name, faces, minSize, maxSize, partBuilder.build(), endPart,
                    startOffset, stepOffset));
        }
        return List.copyOf(patterns);
    }

    /**
     * {@code faces}：这个模式沿哪些面延伸，也就是「往哪个方向一节一节接下去」。
     *
     * <p>旧版的取值与报错（{@code setFaces}）逐条对齐：缺字段、不是数组、元素不是字符串、名字不认识，
     * 各有自己的消息；最后一条会把六个合法值列出来。
     */
    private static Set<Direction> readFaces(JsonObject pattern, String name, Object where) {
        if (!pattern.has("faces")) {
            throw new JsonParseException("dynamic pattern '" + name + "' of " + where + " has no 'faces'. "
                    + "Write the directions the section repeats along, for example \"faces\": [\"north\"].");
        }
        JsonElement element = pattern.get("faces");
        if (!element.isJsonArray()) {
            throw new JsonParseException("'faces' of dynamic pattern '" + name + "' in " + where
                    + " must be an array of direction names, for example [\"north\"].");
        }
        Set<Direction> faces = new LinkedHashSet<>();
        for (JsonElement faceElement : element.getAsJsonArray()) {
            if (!faceElement.isJsonPrimitive() || !faceElement.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("'faces' entries of dynamic pattern '" + name + "' in " + where
                        + " must be strings, but one is " + faceElement + ".");
            }
            String face = faceElement.getAsString();
            Direction direction = directionByName(face);
            if (direction == null) {
                throw new JsonParseException("Invalid facing '" + face + "' in dynamic pattern '" + name
                        + "' of " + where + ". Expect one of 'up', 'down', 'north', 'south', 'west', 'east'.");
            }
            faces.add(direction);
        }
        if (faces.isEmpty()) {
            throw new JsonParseException("'faces' of dynamic pattern '" + name + "' in " + where
                    + " is empty. Name at least one direction, for example [\"north\"].");
        }
        return faces;
    }

    /** 方向名到 {@link Direction}，不认识时返回 {@code null}（旧版靠 catch 异常达成同一件事）。 */
    @javax.annotation.Nullable
    private static Direction directionByName(String name) {
        for (Direction direction : Direction.values()) {
            if (direction.getName().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * {@code minSize} / {@code maxSize}：整数，且 {@code maxSize} 是必需的。
     *
     * <p>旧版 {@code setMaxSize} 的报错原话是 {@code "Pattern must be has 'maxSize'!"}——语法有误，但它是
     * <strong>面向作者的契约</strong>。这里保留「必须写」这个意思，但不复制那个笔误。
     */
    private static int readPatternSize(JsonObject pattern, String key, String name, Object where,
                                       boolean required, int fallback) {
        if (!pattern.has(key)) {
            if (required) {
                throw new JsonParseException("dynamic pattern '" + name + "' of " + where + " must have a '"
                        + key + "'. Write how many sections the structure may repeat, for example "
                        + "\"maxSize\": 15.");
            }
            return fallback;
        }
        JsonElement element = pattern.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'" + key + "' of dynamic pattern '" + name + "' in " + where
                    + " must be a whole number, but it is " + element + ".");
        }
        int value = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 0, Integer.MAX_VALUE, where + " dynamic '" + key + "'");
        if (value < 0) {
            throw new JsonParseException("'" + key + "' of dynamic pattern '" + name + "' in " + where + " is "
                    + value + ". A pattern cannot repeat a negative number of times; write 0 or more.");
        }
        return value;
    }

    /**
     * {@code structure-size-offset-start} / {@code structure-size-offset}：一个 {@code {x, y, z}} 对象，
     * 省略时为原点。
     *
     * <p>旧版的 {@code getStructureSizeOffset} 走的是与 {@code parts} 同一套坐标读取（它也接受坐标<strong>数组</strong>，
     * 取第一个值）。这里通过 {@link #readCoordinates} 复用同一套解析，于是「坐标数组」这种写法在两处行为一致。
     *
     * <p><strong>省略时是零向量，这是照抄旧版的。</strong>旧版 {@code DynamicPattern} 对这两个字段的初值就是
     * {@code new BlockPos(0, 0, 0)}，而它们决定的是「第一节从哪儿开始量、每节之间走多远」。取零向量意味着
     * 「不写这两个字段的模式不会一格格往前走」。重复同一位置无法量得可靠长度，因此maxSize大于0时
     * 由调用方明确拒绝零步进；maxSize为0的仅收尾段模式仍允许零向量。不会替作者猜一个方向。
     *
     * <p>重复段必须显式给出非零structure-size-offset；起始偏移仍可省略为原点。
     */
    private static BlockPos readPatternOffset(JsonObject pattern, String key, String name, Object where) {
        if (!pattern.has(key)) {
            return BlockPos.ZERO;
        }
        JsonObject offset = asObject(pattern.get(key), "dynamic pattern '" + name + "' '" + key + "' of " + where);
        List<Integer> xs = readCoordinates(offset, "x", where);
        List<Integer> ys = readCoordinates(offset, "y", where);
        List<Integer> zs = readCoordinates(offset, "z", where);
        return new BlockPos(xs.get(0), ys.get(0), zs.get(0));
    }

    private static List<BlockMatcher> readElements(JsonObject part, Map<String, List<String>> variables,
                                                   Object where) {
        JsonElement elements = part.get("elements");
        if (elements == null) {
            throw new JsonParseException("A part of " + where + " has no 'elements'");
        }
        List<String> descriptors = new ArrayList<>();
        if (elements.isJsonArray()) {
            for (JsonElement element : elements.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) {
                    throw new JsonParseException("'elements' entries of " + where + " must be strings");
                }
                descriptors.add(element.getAsString());
            }
        } else if (elements.isJsonPrimitive()) {
            descriptors.add(elements.getAsString());
        } else {
            throw new JsonParseException("'elements' of " + where + " must be a string or an array");
        }

        List<BlockMatcher> accepted = new ArrayList<>();
        for (String descriptor : descriptors) {
            if (descriptor.indexOf(':') >= 0) {
                accepted.add(BlockMatcher.parse(descriptor));
                continue;
            }
            List<String> resolved = variables.get(descriptor);
            if (resolved == null) {
                throw new JsonParseException("'" + descriptor + "' in " + where
                        + " is neither a namespaced block id nor a known variable");
            }
            if (resolved.isEmpty()) {
                throw new JsonParseException("Variable '" + descriptor + "' used by " + where + " is empty");
            }
            for (String value : resolved) {
                accepted.add(BlockMatcher.parse(value));
            }
        }
        return accepted;
    }

    private static List<Integer> readCoordinates(JsonObject part, String key, Object where) {
        List<Integer> out = new ArrayList<>(1);
        JsonElement element = part.get(key);
        if (element == null) {
            out.add(0);
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            out.add(com.reborn.modularmachinery.util.JsonNumbers.integer(element, Integer.MIN_VALUE, Integer.MAX_VALUE, where + " coordinate '" + key + "'"));
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("Coordinate '" + key + "' of " + where
                            + " must contain only numbers");
                }
                out.add(com.reborn.modularmachinery.util.JsonNumbers.integer(value, Integer.MIN_VALUE, Integer.MAX_VALUE, where + " coordinate '" + key + "'"));
            }
            if (out.isEmpty()) {
                throw new JsonParseException("Coordinate '" + key + "' of " + where + " is an empty array");
            }
            return out;
        }
        throw new JsonParseException("Coordinate '" + key + "' of " + where
                + " must be a number or an array of numbers");
    }

    /**
     * 「期望一个对象」的检查，与 {@link MachineLoader} 的变量集读取器共用，好让两边用同样的方式指出出错的位置。
     */
    public static JsonObject asObject(JsonElement element, Object where) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object in " + where);
        }
        return element.getAsJsonObject();
    }

    /** The "expected an array" check; see {@link #asObject(JsonElement, Object)}. */
    public static JsonArray asArray(JsonElement element, Object where) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array in " + where);
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject root, String key, Object where) {
        if (!root.has(key)) {
            throw new JsonParseException("Missing required field '" + key + "' in " + where);
        }
        return root.get(key).getAsString();
    }

    /**
     * X6（0.85.1）：机器名的两个拼法，照旧版 {@code DynamicMachine.Deserializer}（DynamicMachine.java:613-619）
     * ——先读 {@code registryname}，为空再看驼峰别名 {@code registryName}；两个都为空才算缺字段。
     * 非字符串的原语（数字、布尔）在这里按「空」处理，走缺字段的报错，而不是让 {@code getAsString}
     * 把 {@code 123} 悄悄变成 {@code "123"}。
     */
    private static String readRegistryName(JsonObject root, Object where) {
        String name = stringFieldOrNull(root, "registryname");
        if (name == null || name.isEmpty()) {
            name = stringFieldOrNull(root, "registryName");
        }
        if (name == null || name.isEmpty()) {
            throw new JsonParseException("Missing required field 'registryname' in " + where
                    + " (the camel-case alias 'registryName' is also accepted)");
        }
        return name;
    }

    private static String stringFieldOrNull(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsJsonPrimitive().isString()
                ? root.get(key).getAsString() : null;
    }
}
