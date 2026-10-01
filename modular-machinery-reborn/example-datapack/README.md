# 默认机器与配方（数据包）

**mod 本身不再携带任何机器与配方**。内容都在这里，作为一个普通数据包，你可以安装、修改，也可以完全不装。

## 安装

把 `data/` 与 `pack.mcmeta` 放进 `saves/<存档>/datapacks/default-machines/`，或放进
`<实例>/datapacks/` 以对所有存档生效，然后 `/reload`（或重启）。

内容包括：

- `data/modular_machinery_reborn/machinery/` —— `alloy_furnace`、`iron_centrifuge`、`power_transformer`，M6e 新增的 `example_factory`（工厂示范机），以及变量集 `casings.var.json`。
- `data/modular_machinery_reborn/recipes/` —— 上述机器使用的 12 份配方（其中 `alloy_smelter_parallel_demo` 是 M6d 为并行控制器专设的示范配方，见下；`example_factory/` 下的两份是 M6e 的工厂示范配方，见「工厂控制器」一节）。
- `data/modular_machinery_reborn/upgrade/` —— 3 份升级声明 + 2 份物品映射（见「升级声明」一节）。

> ⚠️ **每份配方根级都必须有 `"type": "modular_machinery_reborn:machine"`**（见下面示例）。原版 `RecipeManager`
> 先读它决定交给哪个序列化器；缺了就在 mod 看到之前被丢弃，日志只留 `Missing type, expected to find a string`。
> 这是 0.13.1 修掉的坑——**从旧版 `default_recipes/` 直接抄来的配方一律缺这个字段**，因为旧版是 mod 自己
> 加载配方的。`ConfigRecipes` 现在会在缺失时明确报错。

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "alloy_furnace",
  "recipeTime": 5,
  "requirements": [
    { "type": "modular_machinery_reborn:energy", "io-type": "input",  "energyPerTick": 100 },
    { "type": "modular_machinery_reborn:item",   "io-type": "input",  "item": "minecraft:coal", "amount": 32 },
    { "type": "modular_machinery_reborn:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
  ]
}
```

## 怎么让这些机器拥有专属控制器

数据包**无法创建方块**：方块注册发生在 mod 构造阶段，而数据包的读取在其之后。所以这些机器默认只能用
**通用** `machine_controller`。

想让某台机器有专属控制器，就在 mod 的配置目录里放一份**控制器声明**——只写机器名、不写定义：

```
config/modular_machinery_reborn/machinery/alloy_furnace.json
{
  "registryname": "alloy_furnace"
}
```

`claims/` 目录里就是这三台机器的声明文件，复制进上述目录并**重启**即可。定义仍然留在这个数据包里，
所以你继续用 `/reload` 热改结构，而控制器方块在启动时创建。

| 声明文件 | 得到 | 绑定 |
|---|---|---|
| `claims/alloy_furnace.json` | `alloy_furnace_controller` | `modular_machinery_reborn:alloy_furnace` |
| `claims/iron_centrifuge.json` | `iron_centrifuge_controller` | `modular_machinery_reborn:iron_centrifuge` |
| `claims/transformer.json` | `transformer_controller` | `modular_machinery_reborn:transformer` |
| `claims/example_factory.json` | `example_factory_controller` **与** `example_factory_factory_controller` | `modular_machinery_reborn:example_factory` |

**M6e 起声明可以多写一个字段**：`"has-factory": true`。它让这个声明同时注册一个**工厂控制器**方块
`<机器名>_factory_controller`。声明文件的形状因此是：

```json
{ "registryname": "example_factory", "has-factory": true }
```

判定规则仍然是「含 `parts` 即定义，不含即声明」——`has-factory` 只是声明能多说的那一件事。

> ⚠️ **`has-factory` 必须在声明里写，不能只写在数据包的机器定义里。** 方块只能在 mod 构造阶段注册，
> 而数据包在构造之后才被读取；所以"这台机器有没有工厂控制器方块"这一位只能在配置目录里说。
> 数据包里的定义写了 `has-factory: true` 但配置目录没有对应声明时，加载器会**告警并给出该写的文件内容**。

声明若没有对应定义，会留下一个永远无法成型的方块；加载器会在启动时告警，不会让它悄悄存在。

## 升级声明

升级是**数据**，与机器定义同一套来源规则：数据包目录 `data/<命名空间>/upgrade/`，以及配置目录
`config/modular_machinery_reborn/upgrade/`（**同名时配置目录胜出**）。两者都不需要重启，`/reload` 即生效。

一个目录里有两类文件，**靠文件名区分**：

| 文件名 | 是什么 |
|---|---|
| `<名字>.json` | 升级**类型**声明 |
| `<名字>.item.json` | **物品映射**：哪个物品携带哪个升级 |

```json
// upgrade/example_speed.json
{
  "name": "modular_machinery_reborn:example_speed",
  "localizedname": "Example Speed Upgrade",
  "level": 1.5,
  "max-stack": 4,
  "dynamic": false,
  "stackable": true,
  "modifiers": [
    { "target": "duration", "io": "input", "operation": 1, "multiplier": 0.5 }
  ],
  "descriptions": ["An example upgrade declared by a data pack."]
}
```

```json
// upgrade/wrench.item.json
{
  "upgrade": "modular_machinery_reborn:example_speed",
  "item": "modular_machinery_reborn:wrench"
}
```

**`modifiers` 与 `stackable` 是 0.21.0 新增的**（M6b）：

| 字段 | 缺省 | 语义 |
|---|---|---|
| `modifiers` | 空 | 该升级贡献的配方修改器。每个条目的形状**与机器定义 `modifiers[].modifier` 逐字段相同**（`target` / `io` / `operation` / `multiplier`，可选 `affectChance`），因为它是同一个 `RecipeModifier`。以 `duration` 为目标就是改时长，`item` + `io: output` 就是改产出 |
| `stackable` | `false` | `true` 时一份 n 个的载体把该修改器**按份数重复施加**（`0.5` 在 2 份时是 `0.25`）；`false` 时整叠只算一次。只对 `operation: 1`（乘）有意义 |

本数据包里的五份文件分别是：

| 文件 | 内容 |
|---|---|
| `upgrade/example_speed.json` | 固定升级，可叠 4 层，全部机器可用；`stackable`，时长 ×0.5 |
| `upgrade/example_output_doubler.json` | 固定升级，**白名单**只有合金炉；物品产出 ×2 |
| `upgrade/example_charge.json` | 动态升级（每份在物品上存自己的 NBT），**白名单**只有合金炉，不带修改器 |
| `upgrade/wrench.item.json` | 扳手 → `example_speed` |
| `upgrade/modularium.item.json` | 模块化合金锭 → `example_output_doubler` |

字段含义、`dynamic` 的区别、以及为什么白名单与黑名单不能同时写，见
`examples/pack-author/README.md` 的「场景 4」。

> ⚠️ **一个升级只有在升级总线里、且总线所在机器接受它时才会生效。** 升级物品放在背包里、或放在
> **不兼容**的机器的总线里都不会改变任何数值——后者会在总线界面里显示一行 `§e警告：… 与此机械不兼容。`

## 升级总线（M6b）

**总线本身不会改变任何东西。** 与并行控制器同理，它必须在机器结构里占据一个**定义允许**的位置。

因此 0.21.0 起，`alloy_furnace` 的**屋顶前沿中央**那一格（`(0,1,0)`）除了原本的
`blockcasing[casing=firebox]` 之外，**也接受 `modular_machinery_reborn:upgrade_bus`**。
（`(0,1,1)` 是并行控制器那一格，两者互不影响。）

| 装配 | 按 `alloy_smelter_parallel_demo` 开工一次 |
|---|---|
| 空总线 | 1 圆石 → 1 石头，耗时 **5 tick** |
| 总线里 1 个扳手（`example_speed`） | 1 圆石 → 1 石头，耗时 **3 tick** |
| 总线里 2 个扳手（`stackable`） | 耗时 **1 tick**（5 × 0.25 = 1.25 → 1） |
| 总线里 1 个模块化合金锭（`example_output_doubler`） | 1 圆石 → **2 石头**，耗时 5 tick |

升级总线有五个等级：普通 **3** 槽、强化 **6**、精英 **9**、超级 **12**、终极 **18**
（数值在 `config/modular_machinery_reborn-common.toml` 的 `upgrade-bus.<等级>.max-upgrade_slot`）。
**一个物品只要携带升级声明就能放进任何总线槽**，放错了不会被拦，只会不生效。

> ⚠️ **动态升级（`dynamic: true`）目前只显示它不带修改器。** 每份在物品上存自己 NBT 的那一层
> （旧版 `CapabilityUpgrade`）尚未实现，而它的唯一用途是给 CraftTweaker 回调读写——本模组没有回调。
> 影响配方的全部信息都在 `modifiers` 里，所以这不影响任何数值。

## 并行控制器（M6d）

**并行控制器本身不会让任何机器并行。** 它必须在机器结构里占据一个**定义允许**的位置——这与旧版一致：
旧版内置机器里只有 `assembly_line` 的结构清单列了 `modularmachinery:blockparallelcontroller`。

因此 0.20.0 起，`alloy_furnace` 的**屋顶正中**那一格（控制器上方那一格的后一格，即 3×3 屋顶的中心）
除了原本的 `blockcasing[casing=plain]` 之外，**也接受 `modular_machinery_reborn:parallel_controller`**。

示范配方 `alloy_smelter_parallel_demo`（1 圆石 → 1 石头，5 tick，100 FE/t）与合金炉另外两份配方
**不共用任何输入**，所以它不会和它们抢配方；它的存在只是为了让「并行数」可以数出来：

| 装配 | 一次完工产出 |
|---|---|
| 不放并行控制器 | **1** 个石头（1 圆石） |
| 放 §a普通§r 并行控制器（4） | 最多 **5** 个石头 |
| 放 §6强化§r 并行控制器（16） | 最多 **17** 个石头 |
| 放 §b精英§r 并行控制器（64） | 最多 **65** 个石头 |
| 放 §5超级§r 并行控制器（256） | 最多 **257** 个石头 |
| 放 §c终极§r 并行控制器（512） | 最多 **513** 个石头 |

上限是 `max(1, min(max-parallelism, internal-parallelism + Σ控制器当前值))`，这台机器是 `1 + 等级值`
（`internal-parallelism` 为 1），再被「仓口里有多少圆石 / 产物仓放不放得下 / 能源够不够」夹小。
**想数出上限就把圆石恰好放成 `1 + 等级值` 个**：一次开工全部扣掉，一次完工产出同样多个石头。

> ⚠️ **三处会被别的仓口掐住，别误判成并行没生效：**
> 1. **产物空间**：`终极`要 513 个石头 = 9 组，普通产物仓只有 6 槽（384 个），请用 **大型（12 槽）** 以上。
> 2. **能源速率**：每条每 tick 100 FE，`终极`一次要 **51300 FE/t**，只有**终极能源输入仓**（131072 FE/t）
>    供得上。速率不够时并行上限仍会算出 513，但每 tick 抽不满 → 机器停开工、输入已扣（这是既有的
>    「能源上限只看储量、不看速率」的行为，不是并行控制器的问题）。对应关系：
>    普通/强化用 `中型`(512 FE/t，够 5 份)、精英用 `大型`(8192，够 81 份)、超级用 `巨型`(32768，够 327 份)、
>    终极用 `终极`(131072)。
> 3. **能源储量与总耗**：一次 513 份要 513 × 100 × 5 = **256500 FE**，同理要先充满。

> ⚠️ 配方选择是「遍历注册表取第一条能开工的」。同一台合金炉里同时备齐圆石与「煤 + TNT」或
> 「铁锭 + 金锭 + 红石粉 + 荧石粉」时，选到哪一条取决于注册顺序。做并行计数时只放圆石即可。

## 工厂控制器（M6e-1，0.22.0）

**「并行」与「工厂」是两件事，别混。**

| | 并行（M6c/M6d） | 工厂（M6e） |
|---|---|---|
| 是什么 | **一条**配方一次结算 **N 份** | **N 个槽位**各跑**一条不同**配方的**一次**结算 |
| 数量来自 | `max-parallelism` / `internal-parallelism` / 结构里的并行控制器 | 机器定义的 `max-threads` 与 `core-threads` |
| 数什么 | 一次完工产出 N 份 | 同一 tick 里有几条不同配方在推进 |

旧版自己的类注释就写着工厂的「线程」**不是真正意义上的线程**（`FactoryRecipeThread.java:33-38`）：它只是一个**配方运行状态槽**，由服务端主线程顺序 tick。本模组如实照此实现——没有线程池、没有并发。

### 示范机：`example_factory`

| 文件 | 内容 |
|---|---|
| `data/modular_machinery_reborn/machinery/example_factory.json` | 3×3 铁块地板 + 金块屋顶，`has-factory: true`、`max-threads: 4`、一条核心槽位 `smelter` |
| `data/modular_machinery_reborn/recipes/example_factory/example_factory_stone.json` | 1 圆石 → 1 石头，**5 tick**，无能耗 |
| `data/modular_machinery_reborn/recipes/example_factory/example_factory_smelt.json` | 1 煤 → 1 钻石，**10 tick**，100 FE/t |
| `claims/example_factory.json` | 声明，多了 `"has-factory": true` |

两份配方**不共用任何输入或产物**，所以「哪条线程做了什么」是可以数出来的，而不是推断的。

> 为什么这台示范机用**原版方块**（铁块/金块）而不是机箱？因为离机复现器要能读这份定义，而它在
> Forge 的方块注册表之外运行——`modular_machinery_reborn:blockcasing` 在那里解析不出方块状态属性。
> 用原版方块让**同一份** JSON 既能在游戏里搭、又能在复现器里被 `readMachine` 解析。仓口引用不受影响
> （不需要属性），所以结构里的仓口仍然照常。

### 怎么在游戏里看到它

1. 把 `claims/example_factory.json` 复制进 `config/modular_machinery_reborn/machinery/` 并**重启**。
2. 用创造栏里的**工厂控制器**（`factory_controller`）或 `example_factory_factory_controller` 搭出这台机器的结构。
3. 物品输入仓放 **8 圆石 + 8 煤**，能源输入仓备好 **≥8000 FE**（每 tick 100 FE × 10 tick × 8 次）。
4. 观察：**圆石与煤同时减少、两条进度同时推进**。约 42 tick 后得到 **8 石头 + 4 钻石**。
   - 这就是「N 条线程跑 N 条不同配方」的实机证据：两条配方的时长不同（5 与 10 tick），所以 42 tick 内分别结算 8 次与 4 次。

### 三件用机器定义就能改的事

| 改什么 | 会看到什么 |
|---|---|
| `"max-threads": 1` | 只剩**一条**配方在跑，另一条的输入完全不动；同样 tick 数内产出明显更少 |
| `"core-threads": [{"name":"smelter","recipes":[]}]` | 这个槽位从成型那刻就存在，**闲置也不会被回收** |
| `"core-threads": [{"name":"pin","recipes":["不存在的配方"]}]` | 这个核心槽位**永远闲置**（它只接受列出的配方），其余配方照常由普通槽位跑 |

普通槽位闲置 **200 tick**（10 秒）后会被回收；核心槽位不会。

> ⚠️ **「没有 `has-factory` 的机器不能用工厂控制器」** 是**两个条件同时成立**才算工厂：①方块是工厂控制器，
> ②这台机器的定义带 `has-factory`。拿掉任何一个都没有工厂——把工厂控制器摆在 `alloy_furnace` 的结构里
> 不会成型（该机器没有对应的工厂方块声明），而把普通 `machine_controller` 摆在 `example_factory` 里也不会
> 产生工厂线程。离线复现器的 Q 组直接断言这四种组合。

> ⚠️ **0.22.0 还没有工厂控制器界面。** 工厂方块目前复用普通控制器的模型与界面，所以**看不到**线程列表——
> 上面要数的东西全在仓口里（输入减少、产出增加）。界面、`guifactory.png` 贴图与两个工厂配置键属 M6e-2。

## 内容为什么搬出 mod
把机器留在 mod 里，等于把机器清单冻进 jar。以数据包形式发布的内容可以按整合包替换、扩展或整体去掉，
并且能用 `/reload` 即时修改。代价就是上面那条：光有数据包无法产生方块，这正是「控制器声明」存在的意义。
