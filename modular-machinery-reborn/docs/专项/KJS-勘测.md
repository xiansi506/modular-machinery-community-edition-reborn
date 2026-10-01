# KJS 勘测（机器与配方的脚本入口）

> **性质**：勘测，不是实施。用于决定 KJS 这一项**是一版还是拆几版**、以及最小可用面是什么。
>
> **委托说明**：本项最初委派给子代理，**子代理在产出前失败且未留结论**（本波第三个死在"通读+写文档"类大任务上的代理）。本文由主理人**自己用定点检查**得出，凡未验证处均明确标注。

---

## 1. 现在到底注册了什么

`kubejs/ModularMachineryKubeJSPlugin.java` —— **全文 17 行**，只注册一件事：

```java
public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
    event.namespace(MOD_ID).register("machine", JsonRecipeSchema.SCHEMA);   // L12
    event.mapRecipe("modular_machinery_reborn:machine",
                    "modular_machinery_reborn:machine");                     // L13
}
```

即：注册 KubeJS 配方 schema，脚本里可写 **`event.recipes.modular_machinery_reborn.machine`**，内容走 `JsonRecipeSchema`（**与我们数据包同一套 JSON 形状**），并把该 KJS 配方类型映射到我们的序列化器 `modular_machinery_reborn:machine`。

**没有任何「机器」相关的注册。** 机器目前只有两条来源：数据包（`MachineLoader`）与配置目录（`MachineDirectory`，D9/D10）。

## 2. 配方能不能通过 KJS 写？

**结构上成立**，理由：`JsonRecipeSchema.SCHEMA` + `mapRecipe` 到我们的序列化器，这正是 KJS 标准的"让脚本按模组原 JSON 形状写配方"的做法；而 `ModRecipeSerializers` 里**没有任何 kubejs 相关代码**（`MachineSerializer` 只做原版 `RecipeSerializer` 的读写），说明接线全在 KJS 侧完成，**不需要改我们的序列化器**。

**附带证据**：仓库里有 `kubejs-examples/server_scripts/machines.js`（33 行示例）。

**⚠️ 未验证**：本文**没有**在游戏里或离机端到端跑通一次 KJS 配方（没有启动过 KJS 环境）。所以正确的说法是「**结构上应当可用**」，而不是「已验证可用」——这一波已经三次证明**前提错不会失败、它会"通过"**，故此处按未验证登记。**实施第一件事就是端到端验一次。**

## 3. 机器走 KJS：缺什么

机器定义的字段集（见 `MachineDefinition` / `MachineLoader`）：图案与部件、`requires-blueprint`、`modifiers`、`smart-interfaces`、`has-factory` / `factory-only` / `max-threads` / `core-threads`、三个并行根字段、需求列表……

要让 KJS 产出机器定义，需要**程序化的定义入口**。**是否需要在加载器里新增机制，本次勘测未能确定**（未读 `MachineLoader` 的入口形态）——按项目规矩，**若需要新机制应当先报告而不是直接做**。

## 4. 原版暴露给脚本的表面有多大（这决定了规模）

对 `_mmce-src` 全量计数：

| 注解 | 数量 |
|---|---|
| `@ZenClass` | **63** |
| `@ZenMethod` | **319** |
| `@ZenGetter` | **77** |
| `@ZenSetter` | **20** |
| **合计** | **~479** |

**这是本次勘测最重要的数字。** 旧版给脚本暴露了近 480 个入口（机器构建、配方、升级、事件、GUI 提示…）。**完整等价面不可能一版做完**，必须切成「最小可用面 + 后续逐块补齐」。

## 5. 「只有脚本可达」的功能簇（KJS 入口的直接受益者）

这些功能在原版**没有任何 JSON 入口**，唯一入口是 CraftTweaker 桥。做 KJS 入口等于**同时给它们一个可用入口**（但按 D7，仍不计入迁移完成度）：

| 功能 | 原版唯一入口 |
|---|---|
| 智能数据接口的**类型** | `MachineModifier.addSmartInterfaceType` |
| `catalyst` 需求 | `RecipePrimer:831-873` |
| **耐久消耗**（真实机制） | `RequirementItem.consumeDurability` + `requiemPrimer:199-220` |
| 动态升级的物品 NBT | CraftTweaker |
| `extraThreadCount` | CraftTweaker |
| 工厂四个事件（Start/Tick/Finish/Failure） | CraftTweaker 桥（D7 已排除） |

## 6. 规模建议：**拆三版**

| 版本 | 内容 | 离线可验证比例 |
|---|---|---|
| **v1** | **端到端验证 KJS 配方**（可能无需改代码）+ 补一个真实可用的示例脚本 + 文档 | **高**（配方加载与产出可离机驱动） |
| **v2** | **机器定义走 KJS**：先做**核心字段**（图案/部件、需求、时间、`requires-blueprint`），后做扩展字段（`modifiers`、`smart-interfaces`、工厂与并行四字段） | **中—高**（定义可离机解析与断言） |
| **v3+** | 按需补脚本面（升级、事件、GUI 提示等），**不做完整 479 项等价** | 视具体项 |

**为什么拆**：本波已两次因**规模估错**而失败（M6e 整块、本勘测）。**完整且有证据的小块，胜过赶工的大块。**

## 7. 未记载 / 未验证（如实登记）

1. **未端到端验证 KJS 配方**（无 KJS 运行环境）；
2. **未读 `MachineLoader` 的程序化入口形态**，故「机器走 KJS 是否需要新机制」**未确定**；
3. 未清点原版 63 个 `@ZenClass` 各自的作用域（未逐类展开）；
4. `examples/` 与 `kubejs-examples/` 的关系未核对（是否重复/互补）。
---

## 2b. 示例脚本揭示的真实形状（**这改变了结论的重点**）

`kubejs-examples/server_scripts/machines.js`（33 行，注释详尽）给出：

```js
ServerEvents.recipes(event => {
  event.recipes.modular_machinery_reborn.machine({
    machine: 'alloy_furnace',
    registryName: 'kubejs_demo',
    recipeTime: 100,
    requirements: [
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 40 },
      { type: 'modular_machinery_reborn:item',   'io-type': 'input',  item: 'minecraft:copper_ingot', amount: 2 },
      { type: 'modular_machinery_reborn:item',   'io-type': 'output', item: 'minecraft:iron_ingot', amount: 1, chance: 0.75 },
      { type: 'modular_machinery_reborn:fluid',  'io-type': 'input',  fluid: 'minecraft:water', amount: 100 }
    ]
  })
})
```

脚本注释里还写明三件事：

1. 需求 schema 与数据包 JSON **同一套**；
2. **`modularmachinery:` 旧命名空间也接受**，所以旧版配方文件能原样加载；
3. KubeJS 会**替你写根级 `"type"`**；手写 JSON 必须自带，否则 `RecipeManager` 在模组看到之前就丢掉它（正是本项目早期修过的那个 bug）。

**结论修正**：

| 你想要的 | 现状 |
|---|---|
| **KJS 自定义配方** | ✅ **入口已存在且有可用示例**（`registerRecipeSchemas` + `mapRecipe` + 33 行示例）。**仍未端到端实机验证**，但**不是"没有"，而是"有但没验过"** |
| **KJS 自定义机器** | ❌ **完全没有**。示例注释明说机器来自「`alloy_furnace` / `iron_centrifuge` / `transformer`，**或者你自己声明的**」——即机器**只能由数据包/配置目录声明**，KJS 配方只是**引用**它们 |

**所以「KJS 自定义机器和配方」这项工作的重心，实际上几乎全在「机器」那一半**；配方那一半更像是**验证 + 补文档**，而不是开发。

这也让 v1 的范围更清楚：

| 版本 | 内容 | 说明 |
|---|---|---|
| **v1** | **验证 KJS 配方端到端** + 核对示例与文档 | 可能**不需要改代码**，只需实机跑一次并如实记录 |
| **v2** | **KJS 机器定义入口**（核心字段先行） | **这才是开发量所在** |
| **v3+** | 扩展字段与按需补脚本面 | 不做 479 项完整等价 |
---

## 3b. 补齐：机器定义能否程序化供给（原 §3 的未决项）

**结论：加载器层面不需要新机制。** 两条依据：

| 事实 | 位置 |
|---|---|
| 注册表接受**整张定义表**并可整体替换 | `MachineRegistry.replace(Map<ResourceLocation, MachineDefinition> loaded)` —— **public static** |
| 定义**可公开构造**（4 个构造器，接受 id / 本地化名 / `MachinePattern` / …） | `MachineDefinition` 的 `public MachineDefinition(...)`，L109/115/124/134 |
| 图案可构造（KJS 侧要能表达"部件/图案"） | 同上，构造器入参含 `MachinePattern` |

所以 KJS 一条绑定**可以自己造 `MachineDefinition` 再推进注册表**，**无需改加载器**。

### 但有三处必须解决，且都不是"加个 API"那么简单

1. **时序（与 MOC 那次同类问题）**。`MachineLoader` 是 `SimpleJsonResourceReloadListener`（`apply(Map<ResourceLocation, JsonElement>, ResourceManager, …)`），定义在**数据包 reload** 时进来；而 **KubeJS 的 server script 在服务器启动 / `/reload` 时执行**。**两者的先后顺序决定了实现方式**：若脚本先于我们的监听器，可直接合并；若在其后，则需要重新触发或延后合并。**本次未核实 KJS 6 与我们的 reload 监听器的实际执行顺序。**
2. **必须「合并」而不是「替换」**。`replace(...)` 是**整体替换**语义——若 KJS 定义只是塞进去，**下一次 reload 会被数据包那批覆盖掉**。需要一个明确的合并契约（谁优先、同名冲突怎么办）。
3. **校验必须共用**。加载器那些**响亮的报错**（"must be one of … Write …"）都长在 **JSON 解析**里；程序化路径会**绕过**它们。若不做处理，KJS 侧的错误信息会明显劣于数据包侧——**同一份 schema、两种质量**，属于文档里最容易被抱怨的那类不一致。**建议：把校验从解析里抽出来，让两条路径共用。**

### 对规模建议的影响

原 §6 的拆版**不变**，但 v2 的内部顺序更清楚了：**先定"合并契约 + 时序"，再抽共用校验，最后才是 KJS 绑定的 API 形状**。前两件都不做的话，v2 会做出一个"能用但一 reload 就没"的实现。
---

## 8. v1 离线核对结果：**示例脚本是准确的**

v1 的一半（离线可做的那一半）是**核对 `kubejs-examples/server_scripts/machines.js` 与代码是否一致**——因为**一个错的示例比没有示例更糟**：包作者会照着它写，然后怪模组。逐条核完，**示例的三项声明都成立**。

### 8.1 根级字段（对照 `ModRecipeSerializers` 的 `MachineSerializer`）

| 字段 | 代码行为 | 示例用法 | 结论 |
|---|---|---|---|
| `machine` | `requireString(json,"machine",id)`——**必填** | `machine: 'alloy_furnace'` | ✅ |
| `registryName` | 可选，缺省时取配方 id 的 path | `registryName: 'kubejs_demo'` | ✅ |
| `recipeTime` | 可选，默认 **100**，**必须 ≥1**（否则报错） | `recipeTime: 100` | ✅ |
| `requirements` | **必填非空数组** | 四项需求 | ✅ |
| `max-parallelism` | 可选（示例未用） | — | ✅ 无冲突 |

### 8.2 兼容性声明：**旧命名空间确实被接受**（已追到底）

示例注释称「`modularmachinery:item` 也能用，所以旧版配方文件能原样加载」。代码：

```java
String rawType = requireString(json, "type", where);
int colon = rawType.indexOf(':');
String kind = (colon >= 0 ? rawType.substring(colon + 1) : rawType).toLowerCase(Locale.ROOT);
```

**冒号前的内容被整个丢弃，只取冒号后当类型** → `modularmachinery:item`、`modular_machinery_reborn:item`、**裸写 `item`** 三者等价。`ModRecipeSerializers.java:27` 的类注释与 `RecipeModifier.java:26` 互相印证。**✅ 声明成立**（而且比示例说的更宽松：连命名空间都可以不写）。

### 8.3 根级 `type` 由 KubeJS 代写

示例称「KubeJS 会替你写根级 `type`；手写 JSON 必须自带，否则 `RecipeManager` 在模组看到之前就丢掉它」。这与插件里的 `mapRecipe("modular_machinery_reborn:machine", …)`（`ModularMachineryKubeJSPlugin:13`）以及 KJS 的 `JsonRecipeSchema` 机制一致 → **结构上成立**；「手写必须自带」那条也正是本项目早期踩过的坑。**⚠️ 仍未实机验证。**

### 8.4 v1 剩下的部分：**只能在游戏里做**

离线能证明的到此为止。**「KJS 真的加载了脚本、配方真的生效」必须有 KJS 运行环境**——复现器没有 KJS。

**请按以下步骤跑一次（最小验证）**：

1. 把 `modular-machinery-reborn/kubejs-examples/server_scripts/machines.js` 复制到
   `D:\.minecraft\versions\彩虹花园\kubejs\server_scripts\`
2. 启动游戏（需装 KubeJS 6 for 1.20.1），进世界后执行 **`/reload`**
3. 看日志里是否有插件注册成功的行：
   `[modular_machinery_reborn] KubeJS schema registered: event.recipes.modular_machinery_reborn.machine`（`ModularMachineryKubeJSPlugin:14`）
4. **搭好 `alloy_furnace` 结构**，放好仓口与能源
5. 确认那条 `kubejs_demo` 配方**能被这台机器识别并开工**（2 铜锭 + 40 FE/t + 100 mB 水 → 1 铁锭，**75% 概率**）
6. 顺带确认**不相关的机器不会执行它**（配方只绑定到声明的那台机器）

**把 3、5、6 的结果告诉我**（能用 / 报错 / 没反应，附日志片段）。

### 8.5 为什么先不写「面向包作者的 KJS 使用说明」

本项目的规矩是**未验证的行为不写进文档**（写"未验证"而不是"应该可以"）。在你在游戏里确认第 8.4 步之前，那份使用说明只会是一份**声称能用的文档**——若实际不能用，它就会变成误导包作者的源头。**确认后我立刻写**，并把实测结果（含任何限制）一并写进去。

---

## 9. 0.28.1 追记：绑定形状（0.28.0 的实机缺陷及其根因）

**现象**（用户实机，0.28.0）：

```
[KubeJS Server/]: mmce_machine_test.js#1: TypeError: Cannot find function registry in object MachineRegistryEvents.
[modular_machinery_reborn] KubeJS machine registry event posted; 0 machine definition(s) were staged by scripts
```

绑定对象**存在**、事件**已发布**、`GROUP.server("registry", …)` **确实被调用**（已从实机 jar 反汇编确认），
但脚本取不到 `registry`。

**根因**（用真实 KubeJS jar 跑出来的，不是推断）：

| 事实 | 证据 |
|---|---|
| 事件组不是直接交给脚本的；KubeJS 的 `BuiltinKubeJSPlugin#registerBindings` 会给**每个已注册的组**绑 `new EventGroupWrapper(type, group)` | `BuiltinKubeJSPlugin` 反汇编：遍历 `EventGroup.getGroups().values()`，按 `group.name` 绑定包装器 |
| 能成为「方法」的只有包装器：`EventGroupWrapper#get(String)` 才去查 `group.getHandlers()`，并返回 `EventHandler`（它继承 Rhino 的 `BaseFunction`，所以可调用） | `EventGroupWrapper.get` 反汇编 + 运行验证 |
| 原始 `EventGroup` 交给脚本时**一个属性都没有** | 同一段脚本对原始组求值：`typeof MachineRegistryEvents.registry === "undefined"`，调用即 `TypeError: Cannot find function registry in object MachineRegistryEvents.`——与实机**逐字相同** |
| 我们的 `registerBindings` 用**同一个键**覆盖了 KubeJS 绑好的包装器 | `BindingsEvent#add` 就是 `Context.addToScope`（直接覆写）；`KubeJS.init` 把 kubejs 自己插到插件列表**第 0 位**，所以我们的插件后跑 |
| `onEvent('machineRegistry', …)` **不是**等价写法 | KubeJS 6 的 `onEvent` 绑定是 `LegacyCodeHandler`，调用即抛 `onEvent() is no longer supported!` |

**修法**：`registerBindings` 绑 `new EventGroupWrapper(event.getType(), MachineEvents.group())`。
每个脚本类型各绑一次，包装器自带的错误报告用对应类型的 console。

**离机断言**：`_audit/m6c-verify/mmverify/KubeJSBindingCheck.java`（正式段 Z9）。它把 checkout 里
**反混淆**的 KubeJS + Rhino 载进子类加载器（实例里的 `libs/kubejs-1.20.1.jar` 是 SRG 重映射版，与复现器
的 Mojmap Minecraft 配不上，会 `NoSuchMethodError: Component.m_237113_`），用运行时编译的 Architectury
`Platform` 替身让 KubeJS 能初始化，然后**真的调用** `registerBindings`，再用 KubeJS 自己的 Rhino 求值
`typeof MachineRegistryEvents.registry` 与那次调用。

**它证明什么 / 不证明什么**：它证明**绑定形状**（包装器 vs 原始组）以及「脚本能把它当函数调用」——这正是
0.28.0 漏掉且其它所有检查都看不见的一件事；同一次运行里还把原始组走同一条查询，作为自带的反例。
它**不证明**游戏内可用：不启动服务器、不跑 `ScriptManager`、不真的把事件投递给脚本注册的监听器。
`/reload` 仍是唯一的最终证据。

---

## 10. 0.28.2 追记：`MachineBuilderJS` 的同名重载（0.28.1 的实机缺陷及其根因）

**现象**（用户实机，0.28.1，跑的是本仓库文档里那段最小脚本）：

```
[KubeJS Server/ERROR]: Error in 'MachineRegistryEvents.registry':
  Coordinate 'x' was given no value. Pass at least one whole number, e.g. .part(1, -1, 0, ...).
    at ...kubejs.MachineBuilderJS.coordinate(MachineBuilderJS.java:184)
    at ...kubejs.MachineBuilderJS.partInternal(MachineBuilderJS.java:98)
    at ...kubejs.MachineBuilderJS.part(MachineBuilderJS.java:83)
```

**帧 83 是关键**：0.28.1 里第 **83** 行属于
`public MachineBuilderJS part(List<Integer> x, …)`（列表那个重载），而脚本写的是
`.part(1, -1, 0, 'minecraft:stone')`（标量写法）。**两个重载同名、参数个数都是 4、末尾都是变长参数**，
于是这一次调用的落点由脚本引擎决定。

### 根因：名字之外的区分手段只有"转换权重"，而权重相等时靠顺序

Rhino（KubeJS 1.20.1 用的那个 fork）对 Java 对象的成员调用走
`NativeJavaMethod#findFunction`：

1. 先按**名字**取到同名方法表；
2. 逐个用 `NativeJavaObject#canConvert` 筛出参数可接受的那些；
3. 多于一个时用 `preferSignature` 两两比"转换权重"，得出 `PREFERENCE_FIRST_ARG` /
   `PREFERENCE_SECOND_ARG` / `PREFERENCE_AMBIGUOUS`；
4. **权重相等即 `AMBIGUOUS`，于是返回"最后一个被记下的候选"**——而候选的顺序来自
   `Class.getMethods()`，**JVM 规范明确不定义这个顺序**。

本次把权重**实测**出来（`NativeJavaObject#getConversionWeight`，见段 Z10）：

| 脚本里的值 | 目标类型 | 权重 |
|---|---|---|
| 数字 `1` | `Integer` | **2** |
| 数字 `1` | `List` | **99**（99 = `CONVERSION_NONE`，即"不能转换"） |
| 数组 `[1, -1, 0]` | `List` | 可转换，元素是 **`Double`** |
| 字符串 `'minecraft:stone'` | `String` | 1 |

这张表说明了两件事，而且两件都不利于 0.28.1 的形状：

1. **同名重载让"给数字"和"给数组"变成同一个名字下的一次解析**。只要哪天某个中间形态（JSR-223 包装、
   类型转换器、`TypeWrappers`、Rhino 自身的 `Integer` 缓存路径……）让数字也能"转换"到 `List`，
   那次调用就会落到列表方法上，而列表方法拿到的是一个**空列表**——正是用户看到的那句话。
   换句话说：**这不是"用户写错了"，而是这个 API 把一次调用的含义交给了未定义行为**。
2. **数组形式在 0.28.1 本来就是坏的**：`[1, -1, 0]` 到 Java 是 `List<Double>`，用
   `List<Integer>` 接会在第一个元素上抛 `ClassCastException`（段 Z10 实测复现），
   所以文档 §六 那张表里的数组写法**从来没能用过**。

### 修法（选"改名"，而不是"挑个顺序"）

```java
public MachineBuilderJS part(int x, int y, int z, Object... elements)                     // 标量：名字不变
public MachineBuilderJS parts(List<? extends Number> x, List<? extends Number> y,
                              List<? extends Number> z, Object... elements)              // 列表：改名 + 放宽到 Number
```

- **改名是结构性的**：Rhino 的解析**先按名字**，两个重载一旦不同名，`findFunction` 里那条
  "同名多个候选 → 比权重 → 相等就随顺序"的路径**根本不进入**。歧义从"靠运气"变成"不可能"。
  只调换参数顺序、或用 `int...` 之类的小把戏都不解决这件事——**只要同名同参数个数**，问题就还在。
- **标量写法一字未改**，所以任何已经写好的脚本不需要动。
- **`List<Integer>` → `List<? extends Number>`**：让文档里的数组写法真的能用；`coordinate()` 改为先
  证明"是整数"再 `intValue()`，小数坐标**点名拒绝**而不是悄悄截断。
- 旧写法 `.part([…], […], […], …)` 现在会落到标量方法上并被明确拒绝（报 `part(int, int, int,
  java.lang.Object[])` 的转换错误），比"静默走错方法"好。

### 离机断言（两段，各证一件事）

| 段 | 位置 | 证什么 |
|---|---|---|
| **Z8b** | `KubeJSMachineCheck.z8bBuilderMethodNamesAreUnambiguous` | 从**类文件的成员表**（`javap`，与 Z6 读 `register()` 同一套做法）读出：`part` **只声明一次**、`parts` 声明一次。同名歧义一旦回归，这里立刻红——不需要 Rhino、不需要游戏 |
| **Z10** | `KubeJSBindingCheck.kubeJsBuilderDispatch` | 用**真的 KubeJS 的 Rhino**（与 Z9 同一个 child-first 子加载器）求值文档里那行调用，断言产出的 JSON **逐字等于**数据包会携带的那份；并对数组形式断言笛卡尔积的 JSON；同时实测 `getConversionWeight` 的两条权重，作为"为什么需要改名 / 为什么参数是 Number"的**测量记录** |

**Z10 证明什么 / 不证明什么**：它证明**文档里那行调用会被 KubeJS 的脚本引擎接受、且产出正确 JSON**——
这正是 0.28.1 漏掉、而其它所有只读类文件的检查看不见的一件事。它**不证明**游戏内可用：不启动服务器、
不跑 `ScriptManager`、没有脚本注册的监听器、没有事件投递。**`/reload` 清单仍是最终证据**（§七）。

> **它也不声称"0.28.1 的运行时确实选了列表方法"**：`getMethods()` 的顺序不确定，所以"当年那次调用为什么
> 落在 83 行"无法在离机环境里复现成一个确定性断言。能做到的是**把那个选择本身取消掉**，并断言新的形状
> 只有一个 `part`——这也是 Z8b 与 Z10 分工的原因。

### RED FIRST（这一次同样是先写断言、先跑、贴失败输出，再修）

断言写在**未修**的 0.28.1 代码上跑，失败输出见
`_audit/m6c-verify/red-first-0.28.2-builder-overload.txt`，其中最关键的两行：

```
FAIL  ...and the array spelling, now `parts(...)`, still expands a list of coordinates into the
      cartesian product the guide documents: ... got THREW dev.latvian.mods.rhino.EcmaError:
      TypeError: Cannot find function parts in object MachineBuilderJS[{"registryname":"kubejs_wall"}].
FAIL  the class the script meets declares one 'parts' and no list-taking 'part'
      ('parts'=0, list-taking 'part'=1)
```

同一次运行里数组写法的**实测崩溃**也留在了证据里：
`java.lang.ClassCastException: class java.lang.Double cannot be cast to class java.lang.Integer`——
即 0.28.1 的 `.part([…], […], […], …)` 从来没能用。
