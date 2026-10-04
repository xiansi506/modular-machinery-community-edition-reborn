# v2b：KubeJS 机器定义的 11 个扩展字段 —— 专项交接文档

> 交接范围：**仅 v2b 这一件事**（把 `MachineSchema` 已经求值的 11 个扩展根字段接进 KubeJS 构建器）。
> 项目其余部分的交接见 [`交接文档.md`](../../交接文档.md)；本文件不重复它。
>
> 交接时版本：`0.30.1`（已发布），SHA-256 `724D79FB2AD4A024B33C89D109EE5CED06191932ADFCDABEFDB035FEF76830F7`
> 交接时 HEAD：`8d85cc3`，工作树干净。
>
> 依据：`移植方案-v2.md` §9 优先级表的**第 1 项**（唯一未完成的发布阻塞项）；
> `docs/剩余项清点.md` §9 第 1 行；`docs/简介.md` §「❌ 未完成」第一条。
>
> ⚠️ 本文件是**勘测与交接**，不是实施记录。下面所有代码引用都在 `8d85cc3` 上逐处读过；
> 凡未核实的地方都明确标了「未核实」。

---

## 0. 一句话

**v2b 不是「实现 11 个字段」，是「给 11 个已经能用的字段补上脚本拼写方式」。**

`MachineSchema` 对这 11 个字段的**求值、校验、错误文案全部就绪**（数据包与配置目录早就用上了）；
缺的只是 `MachineBuilderJS` 上的方法——脚本目前根本拼不出这些字段，而且**被按名拒绝**
（`MachineSchema.DEFERRED_ROOT_FIELDS`，严格模式）。

所以 v2b 的工作量集中在**一个类 + 一处常量 + 三处过时文案 + 复现器的段 Z**，
**不新增任何行为语义**：接上之后，脚本定义的机器与「同样写了这些字段的数据包机器」必须**逐字段等价**。

---

## 1. 已经完成的一半（**不要再实现一遍**）

这一节是 `8d85cc3` 上的实测结论。下表每一行都指向真实代码，**行号会漂移，符号名不会**。

| 字段 | 已就绪的读取/求值 | 落在哪个类型上 |
|---|---|---|
| `modifiers` | `MachineSchema.readModifiers(...)` | `List<MachineModifier>` |
| `smart-interfaces` | `MachineSchema.readSmartInterfaces(...)` | `List<SmartInterfaceType>` |
| `has-factory` | `MachineSchema.readBoolean(root, FactoryThreadModel.JSON_HAS_FACTORY, …)` | `boolean` |
| `factory-only` | 同上（`JSON_FACTORY_ONLY`） | `boolean` |
| `max-threads` | `MachineSchema.readThreadCount(root, FactoryThreadModel.JSON_MAX_THREADS, …)` | `int` |
| `core-threads` | `MachineSchema.readCoreThreads(...)` | `List<FactoryThreadModel.CoreThreadSpec>` |
| `max-parallelism` | `MachineSchema.readParallelism(root, "max-parallelism", …)` | `int` |
| `internal-parallelism` | 同上 | `int` |
| `parallelizable` | 内联读布尔 | `boolean` |
| `failure-action` | `MachineSchema.readFailureAction(...)` | `FailureAction` |
| `requires-blueprint` | 内联读布尔 | `boolean` |

配套事实（都已存在，**不需要动**）：

- `MachineDefinition` 的**全参构造器**已经接受全部 11 个字段对应的值，并已被 `MachineSchema.read` 调用。
- 每个字段在 `MachineDefinition` 上都有 `DEFAULT_*` 常量（缺省时脚本机器与数据包机器拿到同一批默认值）：

  | 字段 | 缺省值 | 出处 |
  |---|---|---|
  | `max-parallelism` | `2048` | `MachineDefinition.DEFAULT_MAX_PARALLELISM` |
  | `internal-parallelism` | `0` | `MachineDefinition.DEFAULT_INTERNAL_PARALLELISM` |
  | `parallelizable` | `true` | `MachineDefinition.DEFAULT_PARALLELIZABLE` |
  | `max-threads` | 配置键 `factory-system.default-factory-max-thread`（默认 10） | `MachineDefinition.DEFAULT_MAX_THREADS` ← `FactoryThreadModel.DEFAULT_MAX_THREADS` ← `ModConfig.factoryDefaultMaxThread()` |
  | `has-factory` | 配置键 `factory-system.enable-factory-controller-bydefault`（默认 false） | `MachineDefinition.DEFAULT_HAS_FACTORY` |
  | `factory-only` | `false` | `MachineDefinition.DEFAULT_FACTORY_ONLY` |
  | `failure-action` | `FailureAction.STILL` | `MachineDefinition.DEFAULT_FAILURE_ACTION` |
  | `modifiers` / `smart-interfaces` / `core-threads` | 空列表 | `List.of()` 在 `read*` 里 |
  | `requires-blueprint` | `false` | 内联 |

- 复现器**已经断言过这批默认值**：`_audit/m6c-verify/mmverify/KubeJSMachineCheck.java` 的 `z1CoreFields()`
  末尾 8 条（`a script machine runs one copy …` 到 `...and it does not require a blueprint`）。
  **v2b 不得改动这些断言的含义**——它们是「省略字段＝拿默认值」的那一半证明。

---

## 2. 唯一真正缺的东西：`MachineBuilderJS` 的方法面

现状（`src/main/java/com/reborn/modularmachinery/kubejs/MachineBuilderJS.java`，259 行）的**公开方法全表**：

| 方法 | 作用 |
|---|---|
| `machine(id)`（static，包内） | 开一份定义，写入 `registryname` |
| `localizedName(String)` | 写 `localizedname` |
| `part(int, int, int, Object...)` | 一个位置 + 接受的方块（**唯一叫 `part` 的方法，见 §4.1**） |
| `parts(List<? extends Number>, List<? extends Number>, List<? extends Number>, Object...)` | 同上，多个位置（笛卡儿积） |
| `elements(Object...)` | 改最近一个 part 的 `elements` |
| `block(int, int, int, Object)` | `part(...)` 的简写 |
| `register()` | 交给 `MachineSchema.read(json, where, Map.of(), true)` 并 `MachineDefinitions.stage` |
| `toJson()` | 给复现器读回脚本产物（不是脚本 API 的一部分） |

**这 7 个之外一个扩展字段的方法都没有。** 这是 v2b 的全部缺口。

拒绝发生在 `MachineSchema.rejectDeferred(...)`：严格模式（脚本路径）下，只要根对象里出现
`DEFERRED_ROOT_FIELDS` 中的任何一个就抛异常，并列出字段名。数据包路径（`strict=false`）**直接跳过这个检查**，
照常求值——所以「同一个字段，数据包能用、脚本报错」是当前的真实状态。

---

## 3. 11 个字段的**精确形状**（照这一节写方法，不要重新推导）

本节是**权威来源的抄录**：`MachineSchema` 的 `read*` 方法与 `RecipeModifier.parse` / `SmartInterfaceType` /
`FactoryThreadModel` 的字段定义。构建器只需要产出**同一份 JSON**。

### 3.1 简单的 8 个（标量 / 布尔）

| JSON 字段 | 接受的 JSON 值 | 校验行为（已实现，别在构建器里重写） |
|---|---|---|
| `failure-action` | 字符串，`"reset"` / `"still"` / `"decrease"` | 大小写不敏感匹配枚举名；**不是字符串**时报「must be a string」；**名字不认识**时报「must be one of …」（不再静默取默认值） |
| `requires-blueprint` | 布尔 | 只接受真布尔 |
| `has-factory` / `factory-only` | 布尔 | 只接受真布尔；`factory-only=true` 而 `has-factory=false` 时**只告警**（不是错误） |
| `max-threads` | 非负整数 | 负数报错，不钳位 |
| `max-parallelism` / `internal-parallelism` | 非负整数 | 负数报错，不钳位；**`internal-parallelism > max-parallelism` 是错误**；`parallelizable=false` 而 `internal-parallelism > 1` 只告警 |
| `parallelizable` | 布尔 | 只接受真布尔 |

### 3.2 `modifiers`：**位置的**配方修改器

数组，每项一个「位置 + 接受的方块 + 贡献的修改器」：

```json
"modifiers": [
  { "elements": "modular_machinery_reborn:blockcasing[casing=vent]",
    "x": 0, "y": 1, "z": 0,
    "modifier": { "target": "item", "io": "output", "operation": 1, "multiplier": 2.0 },
    "description": "可选，给人看的" }
]
```

- `x` / `y` / `z`：与 `parts` **同一套坐标规则**（数组会被展开成笛卡儿积）；
  **`(0,0,0)` 会被跳过**（旧版 `addModifierWithPattern` 的行为），且跳过时**打一行告警**——
  也就是说「只在原点写了一个修改器」是**静默失效**的合法定义，这是 v2b 最该在实机清单里点出来的坑。
- `elements`：**必需**，写法与 `parts` 的 `elements` 完全一致（方块 id + 可选 `[state=value]`，或变量集名）。
- `modifier` **或** `modifiers`：两者都接受（旧版两个拼写都在野外出现过）。
  单个是对象，多个是数组；**两个都没有则报错**；数组为空也报错。
- `description`：可选字符串。
- **单个修改器对象的必需字段**（`RecipeModifier.parse`）：
  `target`、`io`、`operation`、`multiplier` 四个**必需**，`affectChance` 可选（默认 `false`）。
  - `target`：`item` / `fluid` / `energy` / `duration` / `*`，**可带命名空间**（命名空间被丢弃）；
    另有旧别名 `itemstack` ≡ `item`。
  - `io`：`input` / `in` / `output` / `out`（大小写不敏感）。
  - `operation`：**数字** `0`（add）/ `1`（multiply）——旧版就是数字，别改成字符串。
  - `multiplier`：数字（读成 `float`）。
  - `affectChance`：布尔，决定这个修改器是否作用在**概率**而不是数量上。

### 3.3 `smart-interfaces`：机器声明的接口类型

数组，每项一个类型：

```json
"smart-interfaces": [
  { "type": "mode", "default": 0, "priority": 1000,
    "header": "gui.example.mode.header", "value": "gui.example.mode.value",
    "footer": "", "notequal": "gui.example.mode.mismatch" }
]
```

- `type`：**必需**、非空、**一台机器内不可重名**（重名报错，理由：需求靠这个名字找值，重名会让其中一个不可达）。
- `default`：数字，缺省 `0`。
- `priority`：整数，缺省 `0`；**谁大谁赢**（未声明的值绑到谁身上）。
- `header` / `value` / `footer` / `notequal`：**可选字符串**，越界/非法一律报错而不是字符串化。
  ⚠️ 注意 Java 记录里的分量叫 `valueFormat`，**JSON/脚本键名是 `value`**（`readOptionalText(type, "value", …)`）——
  这对构建器的参数名是个陷阱，见 §3.6。
- 只有**数字**一种接口类型（旧版 `SmartInterfaceTypeEnum` 只声明 `NUMBER`），**没有字符串类型**，别加。

### 3.4 `core-threads`：工厂的核心线程

数组，每项：

```json
"core-threads": [ { "name": "smelter", "recipes": [ "alloy_smelter_diamond" ] } ]
```

- `name`：**必需**、非空、**不可重名**。
- `recipes`：可选字符串数组；**缺省或空数组**表示「这条线程可以跑这台机器的任何配方」。
  名字经 `ResourceLocation` 解析：**不带命名空间时补本模组命名空间**（与 `registryname` 同一规则）。
- 语义（写实机清单时要用）：核心线程**从成型那一刻就存在、闲置也不回收**；
  普通线程闲置 `FactoryThreadModel.IDLE_TIME_OUT = 200` tick 回收，清理间隔 `IDLE_SWEEP_INTERVAL = 20`。

### 3.5 `requires-blueprint` 的一个已登记的特殊性

`MachineSchema` 的类文档（§`DEFERRED_ROOT_FIELDS` 的说明）里写明：`requires-blueprint` 是这 11 个里
**唯一本可以便宜地支持**的——蓝图是按 `MachineRegistry` 现做的，脚本机器自动就有一张。
它当时被推迟，是因为**脚本侧的惯用写法没定**，且它与成型搜索顺序（`MachineRegistry.findMatch`）有交互。
**v2b 要顺手把这个「写法」定下来**，并在 `docs/KJS-配方指南.md` 里写成一句可照抄的话。

### 3.6 ⚠️ 一个必须先解决的技术风险：**脚本对象怎么变成 Gson 的 `JsonObject`**

**这是 v2b 唯一没有现成答案的地方。** `MachineBuilderJS` 的现有方法之所以全部是
`String` / `int` / `Object...`，是因为 Rhino 传进来的字符串与数字直接就是 Java 类型；
而 `modifiers` / `smart-interfaces` / `core-threads` 是**嵌套结构**，构建器现在**没有任何把脚本对象读成
`JsonElement` 的手段**。

三条候选路线，**必须先在 `mmverify` 的段 Z10 里驱动真实 Rhino 验证，再选**（`KubeJSBindingCheck.java` 的
Z9/Z10 已经有可用的驱动装置，`.tmp-kjs-api/probe/libs/kubejs-deobf.jar` + `rhino-deobf.jar` 在盘上）：

1. **收 `JsonObject` / `JsonElement` 参数**，靠 KubeJS 自己的 `JsonObject` 绑定做转换。
   签名最短、代码最少；**风险是转换是否存在、失败时抛什么**（未核实）。
2. **收 `Map<String, Object>` / `List<Object>`**，自己递归转成 `JsonElement`。
   多写一个递归转换器，但不依赖 KubeJS 的绑定细节。
3. **撤掉嵌套：拆成扁平的方法**（每个修改器一个方法链、每个接口类型一个方法链）。
   最贴合本项目「一个名字一个方法」的既有风格，也最容易在 Rhino 下确定行为；
   代价是脚本侧变啰嗦，且要在**构建器内部**暂存「当前正在描述的那一项」。

**推荐 3，并且把 1 作为「先花半小时用 Z10 排除」的选项。** 理由：0.28.1 的事故（`part` 的同名重载歧义）
就是**让 Rhino 的转换规则替我们做决定**造成的，而 JVM 规范不定义 `getMethods()` 的顺序。
选项 3 把不确定性全部消掉，代价只是脚本多写几行。

**无论选哪条，都不允许在构建器里复刻 schema 的校验**（见 §4.2）。

### 3.7 方法名建议（**每个名字只允许一个方法**）

沿用以 JSON 键名转驼峰的风格，并对「复数根字段数组 + 单项方法」用单数/复数区分：

| 字段 | 建议方法 | 备注 |
|---|---|---|
| `failure-action` | `failureAction(String)` | 值原样写进 JSON，校验交给 schema（大小写由 schema 处理） |
| `requiresBlueprint(boolean)` | 同名 | |
| `hasFactory(boolean)` / `factoryOnly(boolean)` | 同名 | |
| `maxThreads(int)` | 同名 | |
| `maxParallelism(int)` / `internalParallelism(int)` | 同名 | |
| `parallelizable(boolean)` | 同名 | |
| `modifiers` | `modifier(int x,int y,int z, Object element, …)` + 一个可变的修改器方法链 | **不要**叫 `modifiers`——`parts` 已经占用了「复数＝坐标列表」的形状，两个复数名做两件不同的事会误导作者 |
| `smart-interfaces` | `smartInterface(String type, …)` | 加上尾部可选的 `default` / `priority` / 四个文案 |
| `core-threads` | `coreThread(String name, String... recipes)` | |

**每一行都要在段 Z8b 的样式下过一遍**：它读 `javap` 的成员表，确认某个名字在类文件里**只声明一次**，
且带期望的描述符（`KubeJSBindingCheck` 的 Z10 再驱动真实 Rhino 确认**文档里写的那次调用**落到预期方法）。

---

## 4. 三条硬约束（违反任何一条都会退回一次事故）

### 4.1 Rhino 按名字先解析，权重相等时靠未定义的顺序

0.28.1 的实测缺陷：`part(int,int,int,Object...)` 与 `part(List,List,List,Object...)` 同名同元数，
脚本调用落到哪一个由**转换权重**和 `getMethods()` 顺序决定。
`MachineBuilderJS` 的类文档与 `KJS-勘测.md` §10 记着完整推导。
**规矩：一个新名字，一个方法。** 需要「标量 + 列表」两种拼写时，用**两个名字**（`part` / `parts` 的先例）。

### 4.2 构建器不复刻 schema 的校验

`MachineBuilderJS` 的类文档写明：它只做「Java 方法收到的是不是这个形状」这一层检查，
**任何可能被两条路径回答得不一样的判断都留给 `MachineSchema`**。
复现器段 Z6 用字节码断言这一点——它检查构建器的 class file 里**不出现** schema 的拒绝文案。
所以：新方法**只负责拼 JSON**；`"reset"` 拼错、`operation` 写成 2、`core-threads` 重名，
全部由 schema 报错，**并且报错文案必须与数据包路径逐字相同**（段 Z2 的机制自动覆盖新字段）。

### 4.3 `DEFERRED_ROOT_FIELDS` 只有两种合法终局

v2b 完成后它必须是**空列表**（或连同 `rejectDeferred` 一起删除），因为：

- 段 Z3 的第 (4) 条断言「这个列表只许写 schema 认识的字段，且不许写核心字段」；
- 段 Z3 的第 (3) 条**逐字段断言每个列表成员都被按名拒绝**——**把字段从列表里删掉就会让这条断言变红**，
  这是**故意的**：它逼着 v2b 同时改断言，而不是偷偷放宽。

**不要**为了少改复现器而保留 `rejectDeferred` 的调用路径却清空列表——`rejectDeferred` 在列表为空时
直接 return，功能上等价，但**段 Z3 的 (3) 会从「11 条断言」变成「0 条断言」**，
一个 0 条循环是**永远为真**的空断言。要么删掉那段循环并换成新的正向断言，要么让它改成断言「空列表」。
**这两种改法都要在复现器里留下能变红的证据**（必查 7 第 2 条：从未失败过的断言不算证据）。

---

## 5. 复现器（离机）要改什么

工作目录 `_audit/m6c-verify/mmverify/`，跑法见 `_tools/run-harness.ps1`。**六个检查文件，v2b 主要动两个。**

| 位置 | 现状 | v2b 要做的 |
|---|---|---|
| `KubeJSMachineCheck.java` `z3UnknownAndDeferredFields()` | 第 (3) 条**逐字段断言被拒**；第 (4) 条断言列表只写已知字段；第 (5) 条断言数据包路径**仍然求值**这 4 个字段 | **(3)(4) 反转**成「字段被接受」；(5) **保留**（它防的是「顺手把数据包 schema 也收窄」）；新增正向断言：**同一份 JSON 经两条路径产出逐字段相同的 `MachineDefinition`** |
| 同上，`z1CoreFields()` | 断言省略字段时拿到默认值（8 条） | **保留不动**；新增「脚本显式写这些字段时拿到写进去的值」 |
| 同上，`z8bBuilderMethodNamesAreUnambiguous()` | 只检查 `part` / `parts` | **扩到全部新方法**：每个名字只声明一次、描述符符合预期 |
| `KubeJSBindingCheck.java` 的 **Z9 / Z10** | Z9 证绑定形状（`EventGroupWrapper`）；Z10 用真实 Rhino 驱动 `.part(1,-1,0,'minecraft:stone')` | **照 Z10 的模板给每个新方法加一次真实 Rhino 调用**，确认脚本写法落到预期方法、产出的 JSON 正确。§3.6 的路线选择就靠这里 |
| `KubeJSMachineCheck.java` `deferredValue(String)` | 给 11 个字段各备一个「语法上合法」的值 | v2b 后**这些值正好可以复用成「正向样本」**：把它们塞进脚本路径，断言**不再抛异常**、且各字段的值与数据包路径一致 |

另外：`MachineBuilderJS.DEFERRED_ROOT_FIELDS` 的**引用点只有 `MachineBuilderJS` 的类文档**
（`grep` 全仓：`MachineSchema.java` 定义处、自己的注释、`MachineBuilderJS` 注释、复现器 Z3）。
把列表清空/删除时，**这四处注释也要一起改**，否则下一个人会读到「11 个字段被拒绝」的过期说明。

### 5.1 离线能证 / 不能证（照抄本项目一贯的诚实分界）

- **能证**：两条路径共用同一份 schema（同一句报错、同一份默认值）、构建器只拼 JSON 不复刻校验、
  新方法在 Rhino 下的解析落点与产出 JSON、`DEFERRED_ROOT_FIELDS` 的终局、以及
  「字段真的进了 `MachineDefinition`」——通过在 `MachineDefinition` 的访问器上断言（`modifiers()`、
  `hasSmartInterfaces()`、`hasFactory()`、`failureAction()`、`effectiveParallelCeiling()`、
  `requiresBlueprint()` 等等），**不是**断言 JSON 文本。
- **不能证**：KubeJS **真的把脚本跑起来了**。`KubeJSMachineCheck` 的类文档写明 KubeJS 运行时
  **故意不在**复现器 classpath 上（`NoClassDefFoundError: dev/latvian/mods/kubejs/event/EventJS`），
  所以「脚本第一行就 `TypeError`」这类缺陷**只有实机能发现**——0.28.0 与 0.28.1 各中过一次。

---

## 6. 交付物与版本

### 6.1 版本号与流程

- 版本 **`0.31.0`**（新增功能 → 递增 minor，见 `交接文档.md` §6 的版本规则）。
- 改 `gradle.properties` 的 `mod_version`。
- ⚠️ **`license` 与 `authors` 在 `src/main/resources/META-INF/mods.toml`，不在 `build.gradle`**
  ——0.30.1 为这两个字段白跑了三遍。v2b 不需要动它们，但别顺手改错文件。
- **构建、发布顺序（必查 2）**：**复现器 → `clean build` → SRG 重混淆检查 → 部署**。顺序反了会把已重混淆的
  jar 还原成未重混淆版本（v0.19.0 崩溃事故的实际成因）。

```powershell
# 离线构建必须显式覆盖（仓库默认 use_local_deps=false，会去 maven 找第三方 jar 而失败）
$env:GRADLE_USER_HOME='C:\Users\kk07H\Desktop\模组\.gradle-home'
gradle.bat -p "C:\Users\kk07H\Desktop\模组\modular-machinery-reborn" clean build `
  --offline --no-daemon --console=plain -Puse_local_deps=true
```

三个第三方 jar（`jei-15.49.jar` / `kubejs-1.20.1.jar` / `rhino-1.20.1.jar`）在本地 `libs/`，
**但没有入库**（开源仓克隆即构建走 maven）。`git` / `gh` 不在 PATH：

```powershell
$env:PATH = "C:\Program Files\Git\cmd;C:\Program Files\GitHub CLI;" + $env:PATH
```

### 6.2 ⚠️ 五处文案必须同步（这一版最容易漏的）

v2b 把「脚本只能写核心字段」这句话一次性变成假话。当前**至少五处**在说它，**漏一处就等于留了一份错文档**：

| # | 位置 | 现状 |
|---|---|---|
| 1 | `docs/KJS-配方指南.md` §「还不能用脚本写的字段（写了会**报错**，不会静默忽略）」 | 逐个列出 11 个字段 + 「后续计划」段 |
| 2 | `docs/KJS-配方指南.md` §八「机器定义速查」 | 需补 11 个字段的脚本写法（**这一节是作者唯一会照抄的地方**） |
| 3 | `docs/简介.md` §「❌ 未完成」第一条「KubeJS 机器定义的扩展字段」 | 整条删除或改写 |
| 4 | ~~`docs/简介.md` 顶部状态块~~ | ✅ **已修（2026-10-04）**：连同 `docs/Introduction.md` / `docs/使用说明.md` / `docs/已知限制.md` / `README.md` / `README-工作区索引.md` 一并统一为 `0.30.1` |
| 5 | ~~`README.md` 第 5 行~~ | ✅ **已修（2026-10-04）**，同上 |
| 6 | `移植方案-v2.md` §9 优先级表第 1 行 + `docs/剩余项清点.md` §9 第 1 行 | 标成已完成 |
| 7 | `交接文档.md` §1b「下一件该做的事」第 1 条、§3 第 9 点末段（「v2b 尚未实现的 11 个字段…按名拒绝」）、§7 相关行 | 同上 |
| 8 | `迁移日志.md` | 新增 `0.31.0` 小节 + SHA-256 |

> ⚠️ **与「简介」有关的额外提醒（2026-10-04 决定）**：项目所有者要求 `docs/简介.md` / `docs/Introduction.md`
> **不设独立的「曾被列为未完成但已做完」小节**，已完成的能力必须**并进「✅ 已完成」**里（每机器 JEI 类别、
> 预览面板按钮行、`fluid` 的 `perTick` 写法、`/reload` 与重启的区分已这样放）。
> **因此 v2b 交付时也要照这条来**：把「KubeJS 扩展字段」从「❌ 未完成」里**移除**，并把
> **11 个字段已可用**这句写进「✅ 已完成」的「机器定义」一节，**不要**新开一个小节解释它曾经是缺口。

> 第 4、5 两条的漂移**已在 2026-10-04 的整理中修掉**（`docs/简介.md`、`docs/Introduction.md`、`README.md`、
> `docs/使用说明.md`、`docs/已知限制.md`、`README-工作区索引.md` 的版本号已统一为 `0.30.1`；
> `README-工作区索引.md` 里指向旧位置的 `modular-machinery-reborn/移植计划.md` 也已改成
> `docs/历史/移植计划.md`）。**它们与 v2b 无关**，只是同一次整理顺手清掉的。

### 6.3 实机清单（**必查 7 要求在交付前写好，且用户确认前只标记「已构建、未实机验证」**）

v2b **新增了脚本可写字段**，因此**必须**附一份「把脚本粘进去、`/reload`、应该看到什么」的最小清单，
追加到 `交接文档.md` §8 的版本清单序列里。清单至少要覆盖：

1. 一条脚本同时写满 11 个字段 → `/reload` **不报错**，机器能成型（结构里要有仓口，否则永远不开工）。
2. **写完立刻查日志有没有 schema 的拒绝句**——脚本路径是严格的，任何字段拼错都会点名报错。
3. **`modifiers` 的 `(0,0,0)` 坑**：把修改器写在控制器自己那一格 → 机器照常成型但**修改器无效**，
   日志里应有一行告警。这一条是**最容易误判成「v2b 没生效」**的地方。
4. `failure-action` 三个值各一次：已开工后抽掉能源 → `still` 保持进度 / `decrease` 每 tick 退一格 /
   `reset` 清零（并注意 `reset` 会**重走开工、再扣一次一次性输入**，这是本项目唯一有意偏离旧版之处）。
5. `smart-interfaces` 声明一个类型 + 一条 `interface_number_input` 需求引用它 → 控制器界面能看到该值。
6. `max-parallelism` / `internal-parallelism` / `parallelizable`：并行控制器成型的机器上并行数真的变大；
   `internal-parallelism > max-parallelism` 的写法要**报错**（而不是静默）。
7. `has-factory` / `max-threads` / `core-threads`：脚本机器**拿不到专属工厂控制器方块**（D8：
   方块在构造期注册），所以这一条要么确认「脚本机器 + 工厂」当前**不可能**，要么登记为已知限制——
   **不要**在清单里写成「应该能用」。`docs/KJS-配方指南.md` §「这台机器用什么控制器？」已经写明脚本机器
   只能用通用控制器，**v2b 不改变这一点**。
8. `requires-blueprint`：脚本机器自动有蓝图（`MachineRegistry` 现做），确认写入该字段后
   **没有蓝图就不成型**、插入后成型、取走即解散。⚠️ 这条与 `07` 的「脚本机器没有专属控制器」并不矛盾，
   但**必须实测**，因为它是 §3.5 记下的那个「本可便宜支持」的字段。
9. `KubeJS 真的跑起来了` 的正面证据：日志里应有插件注册行
   （`KubeJS schema registered: event.recipes.modular_machinery_reborn.machine`）。
10. `/reload` **两次**机器还在；**删脚本 → `/reload` 机器消失**（0.28.2 那两条最关键的回归项）。

---

## 7. 建议的实施顺序（小步、每步可离机证明）

1. **先在 Z10 里做 §3.6 的路线选择实验**（不写生产代码）。三条候选各驱动一次真实 Rhino，
   把「脚本对象能不能变成 `JsonElement`」变成**有输出的事实**，而不是假设。
   **这一步不做，后面全部要返工。**
2. 按选定路线给 `MachineBuilderJS` 加 8 个标量/布尔方法（§3.1）——最小一批，
   跑复现器：新断言应为**红**（字段还不被接受）→ 改 `DEFERRED_ROOT_FIELDS` → **绿**。**先红后绿，贴输出**。
3. 加嵌套的三个（`modifier` / `smartInterface` / `coreThread`，§3.2–3.4），同样先红后绿。
4. 清空/删除 `DEFERRED_ROOT_FIELDS` 与 `rejectDeferred`，把段 Z3 的 (3)(4) 改成**正向**断言，
   并确认新的正向断言**注入缺陷后能变红**（必查 7 第 2 条）。
5. 扩 Z8b 到全部新方法名；给每个方法补一次 Z10 风格的真实 Rhino 调用。
6. 按 §6.2 同步八处文案；写 §6.3 的实机清单。
7. 必查 2 的顺序：复现器 → `clean build` → SRG 重混淆检查 → 部署 → 归档（`releases/` **由所有者管理**，
   子代理不得改动；归档要取**部署件**，整包哈希会随 ZIP 时间戳变化）。

---

## 8. 未核实 / 未决（如实登记，别当成结论）

1. **§3.6 的转换路线**——三条候选都**没有实测过**。这是 v2b 唯一的技术不确定性。
2. **`requires-blueprint` 的脚本惯用写法**——`MachineSchema` 的类文档只说它「没定」，没有候选写法。
   需要在 §6.3 第 8 条实机之前先定。
3. **`modifiers` 单复数方法名的最终拼写**（§3.7 是建议，不是定论）：`parts` 已经占用了
   「复数＝坐标列表」的形状，而 `modifiers` 是**根字段名**。两种拼写都说得通，
   选哪个要在 `docs/KJS-配方指南.md` 里**写成一句作者能照抄的话**。
4. **`smart-interfaces` 的 `value` 与 Java 分量 `valueFormat` 不同名**——
   构建器的参数名该跟 JSON 键（`value`）还是跟 Java（`valueFormat`）**未决**。
   倾向跟 JSON 键，因为作者看的是文档而不是 Java，但要在类文档里记一句为什么。
5. **本文件的代码引用是 `8d85cc3` 的**。行号会漂移，**符号名不会**；引用时用符号名。
6. **复现器当前的基线计数未在本次交接中重跑**（工作树干净、HEAD 与 0.30.1 发布件一致，
   故 `harness-run-0.30.0.txt` 的 `1168 PASS / 3 FAIL (1171 checks)` 仍是有效基线；
   其中 3 条 FAIL 是环境性的配置迁移检查，**按规矩保留、不允许搞绿**）。
   **下一个人第一步应该自己跑一次 `_tools/run-harness.ps1` 确认基线**，而不是相信这句话。
