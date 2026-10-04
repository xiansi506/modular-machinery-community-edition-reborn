# 用 KubeJS 编写机器配方与机器定义

> **状态**
>
> - **配方部分：已在游戏里确认可用**（0.27.1；2026-10-01 实测：日志出现 `KubeJS schema registered`，配方在游戏里正常显示）。
> - **机器定义部分（0.28.0 起）：已构建，但 §七 的实机步骤尚未执行**，因此按本项目的规矩标为**未实机验证**。脚本里的机器定义在离机复现器里能证到的是：它走的是**数据包那条解析路径**（同一份 `MachineSchema`，报错逐字相同）、以及合并/优先级/删脚本的行为；**证不到**的是「KubeJS 真的把这段脚本跑起来了、机器真的能成型」。
>
> 本文面向**整合包作者**。

---

## 一、把脚本放在哪里

```
<实例>/kubejs/server_scripts/<任意名字>.js
```

放好后进游戏执行 **`/reload`**（或重启）。KubeJS 会在日志里报告加载了几个脚本、几个错误。

**确认接线正常**：日志里应有这一行

```
[modular_machinery_reborn] KubeJS schema registered: event.recipes.modular_machinery_reborn.machine
```

没有这行就说明 KubeJS 没发现本模组的插件（0.27.0 及更早的版本有这个缺陷，0.27.1 已修）。

## 二、最小可用配方

```js
ServerEvents.recipes(event => {
  event.recipes.modular_machinery_reborn.machine({
    machine: 'alloy_furnace',        // 必填：机器注册名
    registryName: 'my_recipe',       // 可选：配方 id（缺省用脚本给的 id）
    recipeTime: 100,                 // 可选：耗时 tick，默认 100，必须 >= 1
    requirements: [                  // 必填、非空数组
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 40 },
      { type: 'modular_machinery_reborn:item',   'io-type': 'input',  item: 'minecraft:copper_ingot', amount: 2 },
      { type: 'modular_machinery_reborn:item',   'io-type': 'output', item: 'minecraft:iron_ingot', amount: 1, chance: 0.75 },
      { type: 'modular_machinery_reborn:fluid',  'io-type': 'input',  fluid: 'minecraft:water', amount: 100 }
    ]
  })
})
```

**根级 `type` 由 KubeJS 自动写入**。手写 JSON 文件则**必须自带** `"type": "modular_machinery_reborn:machine"`，否则 `RecipeManager` 会在本模组看到它之前就把文件丢掉。

## 三、两个省事的写法（已核对代码）

**① 命名空间可以省略。** 解析时**冒号前的内容被整个丢弃、只取冒号后**，所以下面三种完全等价：

```
modular_machinery_reborn:item     modularmachinery:item     item
```

这意味着**旧版（1.12.2 MMCE / ModularMachinery）的配方文件可以原样加载**，不必改命名空间。

**② `io-type` 决定输入还是输出**（`input` / `output`）。

## 四、字段速查

| 用途 | 字段 |
|---|---|
| 指定机器 | `machine`（**必填**） |
| 配方 id | `registryName`（可选） |
| 耗时 | `recipeTime`（可选，默认 100，**≥1**） |
| 需求列表 | `requirements`（**必填非空**） |
| 物品 | `item`（支持 `minecraft:xxx`、`#forge:...` 标签、旧版 `ore:ingotIron`）+ `amount`；输出可用 `chance` |
| 流体 | `fluid` + `amount`；可按 tick 消耗用 `perTick` |
| 能源 | `energyPerTick` |
| 输入/输出 | `io-type` |

**配方只在对应机器成型时生效**——机器没搭好就不会跑。

## 五、可用的需求类型

- `item` / `fluid` / `energy`（三类基础）
- `interface_number_input`（智能数据接口数值输入，0.24.0 起）
- `ingredient_array_input`（**组内只消耗其中一个**，0.26.0 起）

**逐 tick 的流体**不是单独的类型：在 `fluid` 需求上加 **`"perTick": true`** 即可（开工时一次性扣 vs 每 tick 扣，由这个布尔决定）。

**未支持**（写了会**加载即报错**，不会静默跳过）：`gas` / `gas_pertick`（依赖 Mekanism 气体 API）、`catalyst`、`item_durability`——后两者在原版里**根本不是需求类型**，所以**不是缺口**。原因见 [已知限制.md](已知限制.md)。
⚠️ `fluid_pertick` **不在此列**：原版那个类型的 JSON 入口 `createRequirement` 返回 `null`（只能 CraftTweaker 构造），逐 tick 流体用上面的 `perTick` 写。

## 六、用 KubeJS 定义机器（0.28.0 起，核心字段；0.28.2 起列表坐标叫 `.parts`）

**同一份脚本文件里**，除了配方事件，还可以监听机器事件：

```js
MachineRegistryEvents.registry(event => {
  event.machine('kubejs_furnace')                  // 必填：注册名，裸写路径会自动补 modular_machinery_reborn:
    .localizedName('脚本熔炉')                      // 可选：没有语言键时显示的字符串
    .part(1, -1, 0, 'minecraft:stone')             // 相对坐标 + 该位置接受的方块
    .part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=plain]',
                    'modular_machinery_reborn:blockcasing[casing=vent]')
    .register()                                    // ★ 必需，漏了不报错（§八）
})
```

**事件名写法（`onEvent(...)`）在 KubeJS 6 里已经取消**，不要用：它的绑定是一个占位函数，调用即抛
`onEvent() is no longer supported! Read more on wiki: https://kubejs.com/kjs6`。`MachineRegistryEvents.registry(...)`
是**唯一**入口。

**0.28.1 修的是一个「绑定形状」错误**：KubeJS 交给脚本的事件组**必须是 `EventGroupWrapper`**，
事件的「方法」来自这个包装器，而不是事件组本身。0.28.0 直接把原始事件组绑上去，于是脚本第一行就死在
`TypeError: Cannot find function registry in object MachineRegistryEvents.`——绑定**存在**（所以不是「未定义」而是
「找不到函数」），但**一个事件都没有**。修法见 `ModularMachineryKubeJSPlugin#registerBindings`；
离机断言见 `_audit/m6c-verify/mmverify/KubeJSBindingCheck.java`（它真的把 KubeJS 的 jar 载进来跑一遍）。

**0.28.2 修的是这个入口里的「重载歧义」**：0.28.1 的 `MachineBuilderJS` 同时声明了
`part(int,int,int,Object...)` 和 `part(List,List,List,Object...)`——**同名、同参数个数、同样以变长参数
结尾**。用户实机跑本文档的 `.part(1, -1, 0, 'minecraft:stone')` 时，报错来自**列表那个方法**
（`Coordinate 'x' was given no value.`，即它收到了空列表）。这是 **API 形状**的缺陷，不是脚本写错：
Rhino 先按名字找方法，两个重载的名字一样，怎么选就只剩「参数转换权重」和「`getMethods()` 返回顺序」——
而顺序部分**JVM 规范没有定义**。修法是把列表形式**改名**为 `.parts(…)`（见下表），
**标量 `.part(x, y, z, …)` 一字未改**；离机断言见同文件的**段 Z10**：它用 KubeJS 自己的 Rhino 求值
本文档那行调用，断言产出**逐字等于**数据包会携带的那份 JSON。

**字段与数据包 JSON 完全同名同义**——因为走的是**同一份校验代码**（`MachineSchema`），所以脚本里的报错与 JSON 里的报错是**逐字相同**的句子（只是把「文件名」换成「machine '某某'」）。已支持：

| 方法 | 对应 JSON 字段 | 说明 |
|---|---|---|
| `event.machine('名字')` | `registryname` | **必填**；裸写路径补本模组命名空间 |
| `.localizedName('显示名')` | `localizedname` | 可选；有语言键 `<命名空间>.<路径>` 时优先用语言键 |
| `.part(x, y, z, 方块…)` | `parts[]` | **一个位置**；可连写多次；`(0,0,0)` 是控制器自己，会被跳过 |
| `.parts([x…], [y…], [z…], 方块…)` | 同上 | **多个位置**：坐标给数组＝笛卡尔积，一次描述一圈/一面墙 |
| `.elements(方块…)` | `parts[].elements` | 覆盖**最近一个** `.part(...)` 接受的方块 |
| `.block(x, y, z, 方块)` | 同上 | `.part(x, y, z, 方块)` 的简写 |

**`.part` 与 `.parts` 是两个名字，这不是排版问题**（0.28.1 → 0.28.2 的修复）：Rhino
（KubeJS 用的脚本引擎）**先按名字找方法，再按参数类型挑重载**。0.28.1 里两个方法**同名 `part`**、
参数个数都是 4、末尾都是变长，于是「给一个坐标」和「给一组坐标」只能靠**参数转换权重**区分，而权重相等时
谁被选中由 `getMethods()` 的返回顺序决定——**JVM 规范没有规定这个顺序**。0.28.2 把列表形式改名为
`.parts(…)`，歧义从「靠运气」变成「不可能」，**标量写法一字未改，老脚本不用动**。

数组写法的坐标类型是 `Number` 而不是 `Integer`，也是实测决定的：脚本里的 `[1, -1, 0]` 到了 Java 是
**`Double` 的列表**，用 `List<Integer>` 接会在第一个元素上抛 `ClassCastException`（0.28.1 的数组写法
实际就是这样崩的）。小数坐标会被**点名拒绝**，不会悄悄取整。

方块写法与数据包一致：`命名空间:方块名`，可带方块状态 `[属性=值]`（例如
`modular_machinery_reborn:blockcasing[casing=plain]`）；也可以写**变量集名**，但变量集只在数据包里声明
（脚本没有声明变量集的入口）。

```js
// 一面 3×3 的墙，一次写完
event.machine('kubejs_wall')
  .parts([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')
  .part(0, 1, 0, 'minecraft:furnace[facing=north]')
```

### 这台机器用什么控制器？

**只能用通用 `machine_controller`。** 方块在模组构造期注册，而脚本在注册表冻结之后才跑（方案 D8），
所以脚本定义的机器**不可能**有 `<机器名>_controller`，也不可能有工厂控制器——这与「只在数据包里定义的
机器」待遇相同，加载器会为两者各打印一行说明。

**蓝图不受影响**：创造栏的蓝图是按 `MachineRegistry` 的当前内容现做的，脚本定义的机器会自动拿到一个。

### 合并规则（脚本 vs 数据包 vs 配置目录）

同一个 `registryname` 被两处定义时，**优先级从高到低**：

1. **KubeJS 脚本**（运行时最明确的一次声明，且能直接指出是哪个脚本）；
2. **配置目录** `config/modular_machinery_reborn/machinery/`（本机显式覆盖）；
3. **数据包**。

脚本覆盖了别的来源时，日志会**点名**这个 id 并说明两边各来自哪里——不会静默生效。

**脚本删掉，机器就没了**：每次 `/reload`（或启动）脚本重新跑一遍，机器层是**这一轮脚本**的产物，
不是「历史上所有脚本的并集」。所以改脚本 → `/reload`；删脚本 → `/reload` 之后那台机器不再存在。

### 还不能用脚本写的字段（写了会**报错**，不会静默忽略）

`modifiers`、`smart-interfaces`、`has-factory`、`factory-only`、`max-threads`、`core-threads`、
`max-parallelism`、`internal-parallelism`、`parallelizable`、`failure-action`、`requires-blueprint`。

这些字段在数据包里**照常可用**（本模组一如既往地求值）；只是 0.28.0 的 KubeJS 入口先做**核心字段**。
脚本里写了其中之一会得到一句明确报错，指出该字段尚未实现、以及去掉它之后这台机器会拿到哪些默认值
（无修改器、无智能接口、不是工厂、并行数为 1 份、`failure-action` 为 `still`、不需要蓝图）。
**用脚本定义的机器因此与「这些字段一个都没写的数据包机器」行为一致。**

> **后续计划**：上述字段是 v2b 的内容。勘测结论见 [专项/KJS-勘测.md](专项/KJS-勘测.md) §3b——加载器层面
> 不需要新机制，时序/合并/共用校验三件事已在 0.28.0 解决（时序：KubeJS 在 `onServerReload` 里、
> 数据包监听器之前跑完；合并：脚本层由加载器在 reload 时读取；校验：两条路径共用 `MachineSchema`）。

## 七、机器定义部分的实机核对步骤（0.28.0 起；0.28.1 修绑定形状；0.28.2 修 `.part`/`.parts` 重载歧义）

> 按本项目「必查 7」：**在用户确认之前，本版只标记为「已构建、未实机验证」。**
>
> 0.28.0 的这份清单**实机失败**过——第一行就 `TypeError: Cannot find function registry in object
> MachineRegistryEvents.`（根因与修法见 [专项/KJS-勘测.md](专项/KJS-勘测.md) §9）；0.28.1 的清单又失败在
> `.part(1, -1, 0, 'minecraft:stone')` 上，报错来自同名重载里的列表那个（根因与修法见同文 §10）。
> 0.28.2 修好后请重跑，其中「`/reload` 两次机器还在」与「删脚本 → `/reload` 机器消失」**两条最关键**。

把下面这段存成 `kubejs/server_scripts/mmce_machine_test.js`，然后进游戏 `/reload`：

```js
MachineRegistryEvents.registry(event => {
  event.machine('scripted_smoke')
    .localizedName('Scripted Smoke')
    .part(1, -1, 0, 'minecraft:stone')
    .part(-1, -1, 0, 'minecraft:stone')
    .part(0, -1, 1, 'minecraft:stone')
    .part(0, -1, -1, 'minecraft:stone')
    .register()
})
```

> **`.register()` 是必需的**（§八详述）：漏了它**不会报错**，机器也不会存在——日志只显示
> `posted; 0 machine definition(s)`。判据是代码级的：`MachineDefinitions.stage(...)` **只有
> `MachineBuilderJS.register()` 一个调用点**，所以 0.28.2 实机日志里的 `posted; 1` / `4 parts`
> 只可能来自带 `.register()` 的脚本。

- [ ] **脚本被加载**：日志出现 `KubeJS machine registry event posted; 1 machine definition(s) were staged`。
- [ ] **标量 `.part(x, y, z, 方块)` 真的落到标量方法**（0.28.2 的核心一条）：上面那段脚本**必须零报错**，
      且四个位置就是写的那四个。任何 `Coordinate 'x' was given no value.` 都是这个缺陷复发。
- [ ] **定义进入注册表**：日志出现 `Loaded N machine definition(s) ...`，其中包含
      `modular_machinery_reborn:scripted_smoke`；紧随其后有 `scripted_smoke -> 4 parts, size 3x1x3`。
- [ ] **没有专属控制器，且日志说明了**：同一轮日志里有
      `machine(s) have no controller block of their own and use the generic machine_controller: …
      modular_machinery_reborn:scripted_smoke …`，且句尾提到脚本机器拿不到控制器（D8）。
- [ ] **能用通用控制器成型**：在控制器四周按这个形状摆石头（控制器在中心、`(1,-1,0)` 等四格），
      用通用 `machine_controller` → 界面「找到结构：Scripted Smoke」。
- [ ] **蓝图自动有了**：创造栏里出现一个 tooltip 为 `Scripted Smoke` 的蓝图；放控制器槽里不影响成型
      （该机器没写 `requires-blueprint`）。
- [ ] **第二次 `/reload` 机器还在**：再执行一次 `/reload` → 上面两条日志**再来一遍**，机器仍能成型。
      这一条是**合并契约**的实机面（离机复现器已证脚本层不会被数据包 reload 冲掉）。
- [ ] **删脚本即删机器**：删掉那个 `.js` → `/reload` → 日志里 `scripted_smoke` 不再出现，
      原本能成型的结构**不再成型**（界面回到「找到结构：无」）。
- [ ] **数组写法 `.parts([x…], [y…], [z…], 方块)` 也能用**（0.28.2 才真的能用）：另存一个脚本，
      内容为 `event.machine('scripted_wall').parts([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')`
      → `/reload` → 日志里这台机器是 **9 parts**（3×3 的笛卡尔积），而不是 `ClassCastException`。
- [ ] **旧的数组写法（`.part([…], […], […], …)`）会被明确拒绝**，不是静默走错方法：同一段改成
      `.part([-1, 0, 1], [0], [-1, 0, 1], 'minecraft:iron_block')` → KubeJS 报一条类型错误，
      点名 `part(int, int, int, java.lang.Object[])`；改成 `.parts(…)` 即通过。
- [ ] **小数坐标被点名拒绝**：写 `.parts([1.5], [0], [0], 'minecraft:stone')` → 报错说明坐标必须是整数，
      机器**不注册**（不会被悄悄取整成 `1`）。
- [ ] **同 id 冲突按文档优先级**：把同一台机器同时写进数据包（或配置目录声明文件）和一个脚本，
      `/reload` → 日志出现一行点名警告，且**脚本的那份生效**（显示名是脚本写的那个）。
- [ ] **报错质量与数据包一致**：把 `.part(1, -1, 0)` 的方块参数删掉（或写一个不存在的方块 id）→
      `/reload` → KubeJS 报出**一条**错误，句子与「同一份 JSON 放进数据包」时报的**逐字相同**
      （例如 `A part of machine 'scripted_smoke' has no 'elements'`）。
- [ ] **未知字段被拒**：写一个不存在的字段——例如用 fluent API 之外的写法把
      `event.machine('x').localyzedName('typo')` 之类塞进去（或直接把 `{"registryname":"x","localyzedname":"y","parts":[…]}`
      交给 `MachineSchema`）→ 报错点名 `localyzedname` 并列出**所有**已知字段，机器**不注册**。
- [ ] **被推迟的字段被拒**：把 `.part(...)` 之后接一个 `has-factory`（例如用 `.toJson()` 拿到对象后加一个键，
      再走一遍解析）→ 报错说明该字段尚未在 KubeJS 入口实现。**数据包**里写同一个字段则**不报错**
      （它照常被求值）——这一条正是「推迟只针对脚本入口」的实机面。
- [ ] **顺序无关的坏味道不存在**：脚本里**不要**用 `ServerEvents.loaded` 去定义机器——定义机器的时机是
      `MachineRegistryEvents.registry`（由 `onServerReload` 触发，刚好在加载器读数据包之前）。

## 八、出问题时看什么

1. 日志里有没有 `KubeJS schema registered`（没有 = 插件没被发现）；
2. KubeJS 的 `Loaded N/N KubeJS server scripts ... with N errors`；
3. 配方写错时，本模组会**响亮报错**并告诉你该写什么（例如未知需求类型会列出可用的几种）。
---

## 八、机器定义速查（**含一个易漏点，漏了不报错**）

> **状态：已在游戏里确认**（0.28.2；2026-10-01 实测：`posted; 1`、`scripted_smoke -> 4 parts`、结构成型、`/reload` 两次仍在、删脚本后消失）。

```js
MachineRegistryEvents.registry(event => {
  event.machine('scripted_smoke')                    // registryname（裸路径补本模组命名空间）
    .localizedName('Scripted Smoke')                 // 显示名
    .part(1, -1, 0, 'minecraft:stone')               // 单坐标：x, y, z, 接受的方块…
    .part([-1,0,1], [0], [-1,0,1], 'minecraft:stone')// 数组 = 笛卡尔积（此例 9 格）→ 用 .parts(...)
    .register()                                      // ★ 必需
})
```

### ★ `.register()` 是必需的——**漏掉它不会报错**

这是本模组里**唯一一处「漏了就静默失效」**的地方（其余地方都响亮报错）。没有它，构建器只是**组装了一个 JSON 对象**：不登记、不报错、日志里只会看到

```
KubeJS machine registry event posted; 0 machine definition(s) were staged
```

**判据**：凡是「脚本加载 **0 错误** + **登记数 0**」，**第一个查的就是 `.register()` 有没有漏**，不要去查加载器。

### 两个名字的区别（0.28.2 起）

| 写法 | 用途 |
|---|---|
| `.part(x, y, z, …)` | **单格**，坐标是整数 |
| `.parts(xs, ys, zs, …)` | **多格**，坐标是数组，展开为笛卡尔积 |

> 0.28.1 及更早：数组形式也叫 `.part(...)`，与单格形式**同名同参数个数**，导致 Rhino 无法区分（候选顺序来自 `Class.getMethods()`，**JVM 规范未定义**）——而且数组写法本身就会抛 `ClassCastException`。**0.28.2 改名并修好。**

### 机器能拿到什么、拿不到什么

- **控制器**：脚本机器**没有专属控制器方块**（方块在模组构造期注册，那时脚本还没跑，D8）。用**通用机器控制器**，日志会明说。
- **蓝图**：**能拿到**（按 `MachineRegistry` 现做）。
- **推迟的字段**（脚本写了会**按名报错**，数据包不受影响）：`modifiers`、`smart-interfaces`、`has-factory`、`factory-only`、`max-threads`、`core-threads`、`max-parallelism`、`internal-parallelism`、`parallelizable`、`failure-action`、`requires-blueprint`。不写即取默认，与「这些字段一个都没写的数据包机器」等价。

### 一个可照抄的测试机器

```
上一层：              [控制器]                 ← 通用机器控制器
下一层：   石头   石头  ·  石头   石头          ← 十字，中心留空
                     （正东南西北各隔 1 格）
```

对应脚本：

```js
MachineRegistryEvents.registry(event => {
  event.machine('scripted_smoke')
    .localizedName('Scripted Smoke')
    .part(1, -1, 0, 'minecraft:stone')
    .part(-1, -1, 0, 'minecraft:stone')
    .part(0, -1, 1, 'minecraft:stone')
    .part(0, -1, -1, 'minecraft:stone')
    .register()
})
```

**注意是 `minecraft:stone`（石头）不是圆石**；用错不报错，只是显示「找到结构：无」。

### 三条验收（缺一不算通过）

1. 日志 `posted; 1` + `scripted_smoke -> 4 parts` + `Loaded 2 machine definition(s)`
2. 结构成型，控制器显示「找到结构：Scripted Smoke」
3. **`/reload` 两次它仍在**；**删脚本 → `/reload` → 它消失**（这是脚本层与加载器的合并契约）