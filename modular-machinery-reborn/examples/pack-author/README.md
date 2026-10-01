# 整合包作者示例

三份可直接照抄的最小例子，演示「自定义机器」与「专属控制器」的三种组合方式。

**前提**：0.11.0 起 mod 本身不含任何机器与配方。这里假设你**没有**安装 `example-datapack`，完全自己做一台机器。

## 场景 1：定义放配置目录 —— 机器与控制器一起得到

把 `config/` 复制进实例根目录并**重启**：

- `config/modular_machinery_reborn/machinery/example_machine.json` 是一份**完整定义**（含 `parts`）。
- 因为它在配置目录里，启动时会注册 `example_machine_controller`，绑定 `modular_machinery_reborn:example_machine`，
  且**只校验这一台机器**。

适合：自己写的机器、想立刻拿到专属控制器。

## 场景 2：定义放数据包 + 配置目录只放「声明」

想让结构可以 `/reload` 热改，就把定义放进数据包，配置目录只留一份**声明**——只写 `registryname`，不写 `parts`：

```json
{ "registryname": "example_machine" }
```

- `datapack/` 是数据包，含 `pack.mcmeta` 与 `data/example_pack/machinery/example_machine.json`（完整定义）。
- 把 `claim-example.json` 复制成 `config/modular_machinery_reborn/machinery/example_machine.json`（**替换掉**场景 1 的那份完整定义）。
- 重启后同样得到 `example_machine_controller`；此后改 `datapack/` 里的结构只需 `/reload`，不必重启。

适合：反复调整结构的开发过程，或把内容作为数据包分发。

**判定规则很简单：文件含 `parts` 就是定义，不含就是声明。** 有声明但没有任何来源提供定义时，加载器会告警说明该方块永远无法成型。

## 场景 3：配方放配置目录

`config/modular_machinery_reborn/recipes/example_machine_demo.json`：

- **根级必须有 `"type": "modular_machinery_reborn:machine"`**（`RecipeManager` 靠它分派序列化器；缺了配方会在 mod 看到之前被丢弃）。
- `recipes/a/b.json` → 配方 id `modular_machinery_reborn:a/b`。
- 改文件后 `/reload` 即生效，**不需要重启**。

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "example_machine",
  "recipeTime": 40,
  "requirements": [
    { "type": "modular_machinery_reborn:energy", "io-type": "input", "energyPerTick": 10 },
    { "type": "modular_machinery_reborn:item", "io-type": "input", "item": "minecraft:cobblestone", "amount": 1 },
    { "type": "modular_machinery_reborn:item", "io-type": "output", "item": "minecraft:stone", "amount": 1 }
  ]
}
```

配方也可以放数据包（`datapack/data/example_pack/recipes/example_machine_demo.json` 是同一份内容）。两种来源同 id 时会按常规数据包优先级竞争，**建议使用不同 id**。

## 场景 4：升级声明放配置目录（0.18.0 起）
升级是**数据**，不是方块，所以两个来源都能用、都能 `/reload`，**不需要重启**：

- `config/modular_machinery_reborn/upgrade/example_machine_overclock.json` —— 一份升级**类型**声明。
- `config/modular_machinery_reborn/upgrade/wrench.item.json` —— 一份**物品映射**：哪个物品携带哪个升级。

判定规则与机器一致：**文件名以 `.item.json` 结尾的是物品映射，其余是升级类型。**

```json
{
  "name": "example_pack:example_machine_overclock",
  "localizedname": "Example Machine Overclock",
  "level": 2.0,
  "max-stack": 8,
  "dynamic": false,
  "stackable": true,
  "compatible-machines": ["modular_machinery_reborn:example_machine"],
  "modifiers": [
    { "target": "duration", "io": "input", "operation": 1, "multiplier": 0.8 }
  ],
  "descriptions": ["An upgrade declared from the config directory."]
}
```

```json
{
  "upgrade": "example_pack:example_machine_overclock",
  "item": "modular_machinery_reborn:wrench"
}
```

| 字段 | 必填 | 缺省 | 说明 |
|---|---|---|---|
| `name` | **是** | — | 升级的唯一 id。可以只写路径，缺命名空间时补 `modular_machinery_reborn` |
| `localizedname` | 否 | `name` 的路径 | 显示名。**有同名语言键 `<命名空间>.<路径>` 时以语言键为准** |
| `level` | 否 | `0` | 抄自旧版 `UpgradeType.level`。**旧版自己注释写着「暂无作用」，这里同样只解析不求值** |
| `max-stack` | 否 | `1` | 一份升级物品最多叠几层，至少 1 |
| `dynamic` | 否 | `false` | `true` = 每份升级在**物品上**存自己的 NBT（旧版 `DynamicMachineUpgrade`）；`false` = 状态存在**升级总线**上 |
| `compatible-machines` | 否 | 空 | **白名单**。非空时只有列出的机器接受它 |
| `incompatible-machines` | 否 | 空 | **黑名单**。白名单为空时才生效。**两个同时写会加载报错** |
| `descriptions` | 否 | 空 | 升级物品 tooltip 的附加行；升级总线界面也会在 `Nx 名字` 下面逐行显示它们 |
| `modifiers` | 否 | 空 | **0.21.0 新增。** 该升级贡献的配方修改器。每个条目的形状与机器定义 `modifiers[].modifier` **逐字段相同** |
| `stackable` | 否 | `false` | **0.21.0 新增。** `true` = 一份 n 个的载体把修改器按份数重复施加（旧版 `stackAble`）；`false` = 整叠只算一次。只对 `operation: 1`（乘）有意义 |

`modifiers` 的一个条目就四种形状（与机器 `modifiers` 共用同一套解析器）：

```json
{ "target": "duration", "io": "input",  "operation": 1, "multiplier": 0.5 }
{ "target": "item",     "io": "output", "operation": 1, "multiplier": 2.0 }
{ "target": "energy",   "io": "input",  "operation": 0, "multiplier": -10 }
{ "target": "fluid",    "io": "input",  "operation": 1, "multiplier": 1.5, "affectChance": false }
```

`target` 写 `item` / `fluid` / `energy` / `duration` / `*`；`io` 写 `input` / `output`；
`operation` 写 `0`（加）或 `1`（乘）。

物品映射只有两个字段：`upgrade`（升级 id，找不到会**报错**）与 `item`（物品注册名，**必须已注册**，否则报错）。

> **升级怎么才能生效。** 声明只是「这个物品携带这个升级」；真正改变数值需要**升级总线**（0.21.0 起）
> 摆在机器结构里一个**定义允许**的位置上，且升级的兼容范围包含那台机器。升级物品放在背包里、或放在
> 不兼容的机器的总线里都不改变任何数值——后者会在总线界面里显示一行不兼容警告。
>
> ⚠️ **本示例的 `example_machine` 只有一层 `y=-1` 的 3×3，所以在它里面放不下升级总线。** 要在它上面
> 试升级，得先给它的 `parts` 加一格——例如加一条
> `{ "x": 0, "y": 0, "z": -1, "elements": ["modular_machinery_reborn:blockcasing", "modular_machinery_reborn:upgrade_bus"] }`。
> 结构位置**接受哪些方块**是机器定义说了算，这与并行控制器完全同理（见 `example-datapack/README.md`
> 的「并行控制器」一节）。

## 场景 5：工厂机器（0.22.0 起，M6e-1）

工厂控制器让**一台**控制器同时跑**多条不同**配方，每个「线程」一条。这与并行不是一回事：
并行是**同一条**配方一次结算 N 份，工厂是**N 条不同**配方同时各结算一次。

| 文件 | 作用 |
|---|---|
| `config/modular_machinery_reborn/machinery/example_factory.json` | **完整定义**（含 `parts`），演示 `has-factory` / `factory-only` / `max-threads` / `core-threads` 四个字段 |
| `claim-factory-example.json` | 同一台机器的**声明**版本：只想注册方块、定义留数据包时用它（复制成 `config/.../machinery/example_factory.json`，**替换**上面那份） |
| `config/modular_machinery_reborn/recipes/example_factory_demo.json` | 这台机器的一份配方（配置目录来源） |

**两半同时成立才算工厂**：① 结构原点上放的是**工厂控制器**方块，② 这台机器的定义带 `has-factory`。
本目录里的 `example_factory.json` 两半都有，所以复制 `config/` 进实例并重启后即可用。

```json
// claim-factory-example.json —— 场景 2 的工厂版：定义留数据包，配置目录只声明
{ "registryname": "example_factory", "has-factory": true }
```

`example_factory.json`（定义版）与 `claim-factory-example.json`（声明版）**不要同时放进配置目录**：
前者含 `parts`、是定义，后者不含、是声明，同名文件只会有一份生效（按 D9 的规则，配置目录胜出）。

```json
// config/modular_machinery_reborn/machinery/example_factory.json 的关键字段
{
  "registryname": "example_factory",
  "has-factory": true,
  "factory-only": false,
  "max-threads": 3,
  "core-threads": [
    { "name": "smelter", "recipes": [] },
    { "name": "only_demo", "recipes": ["modular_machinery_reborn:recipes/example_factory_demo"] }
  ],
  "parts": [ /* ... */ ]
}
```

机器定义里的四个字段：

| 字段 | 缺省 | 语义 | 旧版对应 |
|---|---|---|---|
| `has-factory` | `false` | 这台机器**能不能**用工厂控制器。**两半同时成立才算工厂**：方块必须是工厂控制器，且定义必须带这个字段 | `AbstractMachine.hasFactory`（`Config.enableFactoryControllerByDefault`，旧版默认也是 `false`） |
| `factory-only` | `false` | 「这台机器只允许用工厂控制器」。旧版用它决定**不注册普通控制器**；本项目的方块集在定义加载之前就冻结了，所以对数据包定义无法执行，只**解析 + 一致性告警**（写成 true 而 `has-factory` 为 false 会告警） | `AbstractMachine.factoryOnly` |
| `max-threads` | `20` | **普通**槽位最多几个。核心槽位不受它约束 | `AbstractMachine.maxThreads` / `Config.defaultFactoryMaxThread` |
| `core-threads` | 空 | **常驻**槽位列表：`{"name": "...", "recipes": [...]}`。`recipes` 省略或为空 = 这个槽位什么配方都能跑；列出即为**固定配方集**。名字是键，不能重复 | 旧版只有 CraftTweaker 的 `createCoreThread` / `addCoreThread`，没有 JSON |

三条容易踩的规则：

1. **`max-threads` 与 `core-threads` 都是本项目对旧版 schema 的扩展**，理由与 `max-parallelism`
   完全相同（D12/D15）：旧版这两个值只在 `AbstractMachine` 上，唯一入口是 CraftTweaker 脚本，而数据包作者没有脚本。
2. **一个槽位只持有一条配方。** 配方的一次性输入在开工时就被扣走，所以同一条配方不会被交给两个槽位——
   否则会重复付款。这意味着 `max-threads: 0` 且核心槽位都被固定配方占住时，剩下的配方**无处可去**。
3. **闲置的普通槽位 200 tick（10 秒）后被回收**，核心槽位不会。工厂「什么都不做」时不会再造出空槽位。

## 场景 6：工厂控制器与普通控制器的关系

| 装配 | 结果 |
|---|---|
| 工厂控制器 + 定义带 `has-factory` | **工厂**：多条配方同时跑 |
| 工厂控制器 + 定义**没有** `has-factory` | **不成型**（该机器根本没注册工厂方块） |
| 普通 `machine_controller` + 定义带 `has-factory` | 成型，但是**普通单配方机器**，没有工厂线程 |
| 普通 `machine_controller` + 定义没有 `has-factory` | 成型，单配方（既有行为） |

旧版为**每台**机器同时注册普通控制器与工厂控制器（除非 `factory-only`），所以一台机器用哪种由玩家放哪个方块决定——本片保持这一点。

## 目录对照

| 文件 | 作用 |
|---|---|
| `config/modular_machinery_reborn/machinery/example_machine.json` | 场景 1：完整定义（会得到控制器） |
| `claim-example.json` | 场景 2：声明文件，复制过去替换上面那份 |
| `config/modular_machinery_reborn/machinery/example_factory.json` | 场景 5：工厂机器的完整定义 |
| `claim-factory-example.json` | 场景 5：工厂机器的声明版本 |
| `config/modular_machinery_reborn/recipes/example_machine_demo.json` | 场景 3：配置目录配方 |
| `config/modular_machinery_reborn/recipes/example_factory_demo.json` | 场景 5：工厂机器的配方 |
| `config/modular_machinery_reborn/upgrade/example_machine_overclock.json` | 场景 4：升级类型声明 |
| `config/modular_machinery_reborn/upgrade/wrench.item.json` | 场景 4：物品 → 升级映射 |
| `datapack/pack.mcmeta` + `datapack/data/example_pack/...` | 场景 2/3：数据包形式的定义与配方 |

## 结构长什么样

`example_machine.json` 声明的是控制器脚下那层 3×3 机箱（`y = -1`）：

```
y = -1（控制器在 y = 0 的原点）
  ■ ■ ■
  ■ □ ■     □ = 控制器所在位置，不参与匹配
  ■ ■ ■     所以这个结构共 9 个位置
```

材料：任意 `modular_machinery_reborn:blockcasing`（`elements` 只写方块 id 时匹配该方块的**任意** `casing` 变体）。

## 一个提醒

**必须重启，不能只 `/reload`。** 方块只能在 mod 构造阶段注册，而配置目录在构造期被扫描。启动之后才放进目录的机器，`/reload` 能加载它的定义（可用通用 `machine_controller` 成型），但要**下次启动**才有专属控制器。日志会明确告诉你哪些机器处于这种状态。
