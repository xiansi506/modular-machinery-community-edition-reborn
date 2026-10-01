# 注册表（方块与物品）

> 审计方式：直接读取双方注册真源，逐项比对注册名。本文所有行号均已由审计者本人打开文件核对。
>
> 旧版真源：`_mmce-src/ModularMachinery-Community-Edition-master/src/main/java/hellfirepvp/modularmachinery/common/registry/RegistryBlocks.java`、`RegistryItems.java`、`common/lib/BlocksMM.java`、`ItemsMM.java`
> 新版真源：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/block/ModBlocks.java`、`item/ModItems.java`

## 结论摘要

1. **旧版没有任何「等级仓口方块」。** 每个仓口家族只有 **1 个方块**，等级由 BlockState 的 `PropertyEnum` 状态 + 物品 metadata 变体表达，资源层只体现为多张 overlay 贴图。
2. **新版把等级做成了 48 个独立方块**，这是新版自创的方块化设计，不属于「旧方块的迁移」；真正迁移过来的只有等级命名与 overlay 贴图资源。
3. **旧版与新版注册名无任何交集**：旧版 `modularmachinery:blockinputbus`，新版 `modular_machinery_reborn:item_input_hatch`。
4. 旧版注册名由 `prepareRegister` 用**类名小写**自动生成（`RegistryBlocks.java:492-494`、`RegistryItems.java:54-56`），这是推导旧注册名的唯一依据。

## 一、旧版无条件注册（核心 13 方块 + 4 物品）

| 旧注册名 | 来源 | 新版对应 | 状态 |
|---|---|---|---|
| `blockcontroller` | `RegistryBlocks.java:187` | `machine_controller` | 已迁移（改名） |
| `blockfactorycontroller` | `RegistryBlocks.java:189-190` | 无 | **未迁移** |
| `blockcasing` | `RegistryBlocks.java:197-198` | 无（仅复用底材贴图） | **未迁移** |
| `blockinputbus` | `RegistryBlocks.java:200-201` | `item_input_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blockoutputbus` | `RegistryBlocks.java:202-203` | `item_output_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blockfluidinputhatch` | `RegistryBlocks.java:204-205` | `fluid_input_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blockfluidoutputhatch` | `RegistryBlocks.java:206-207` | `fluid_output_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blockenergyinputhatch` | `RegistryBlocks.java:208-209` | `energy_input_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blockenergyoutputhatch` | `RegistryBlocks.java:210-211` | `energy_output_hatch` ×8 级 | 部分迁移（仅外壳） |
| `blocksmartinterface` | `RegistryBlocks.java:212-213` | 无 | **未迁移** |
| `blockparallelcontroller` | `RegistryBlocks.java:214-215` | 无 | **未迁移** |
| `blockupgradebus` | `RegistryBlocks.java:216-217` | 无 | **未迁移** |
| `crushing_wheels` | `RegistryBlocks.java:371-374` | 无 | **未迁移** |

物品（`RegistryItems.java:44-47`）：

| 旧注册名 | 新版对应 | 状态 |
|---|---|---|
| `itemblueprint` | `itemblueprint` | 部分迁移（裸 Item，无 NBT 绑定与蓝图界面） |
| `itemmodularium` | `itemmodularium` | 部分迁移（旧版实现 `ItemDynamicColor` 动态染色，新版为裸 Item） |
| `itemconstructtool` | `itemconstructtool` | 部分迁移（裸 Item，无结构选区/建造） |
| `machine_projector` | `machine_projector` | 部分迁移（裸 Item，无结构预览） |

## 二、旧版条件注册（按模组存在与否）

全部在新版**零迁移**（`RegistryBlocks.java:219-305`）：

| 依赖模组 | 旧版注册名 |
|---|---|
| AE2 | `blockmeitemoutputbus`、`blockmeiteminputbus`、`blockmefluidoutputbus`、`blockmefluidinputbus`、`blockmegasoutputbus`、`blockmegasinputbus`（需 Mekanism+MekEng）、`blockmepatternprovider`、`blockmepatternmirrorimage` |
| Blood Magic | `blockwillproviderinput`、`blockwillprovideroutput`、`blocklifeessenceproviderinput`、`blocklifeessenceprovideroutput` |
| Thaumcraft 6 | `blockaspectproviderinput`、`blockaspectprovideroutput` |
| Tinkers' | `blockimpetusproviderinput`、`blockimpetusprovideroutput` |
| Extra Utilities 2 | `blockgridproviderinput`、`blockgridprovideroutput`、`blockrainbowprovider` |
| Astral Sorcery | `blockstarlightproviderinput`、`blockstarlightprovideroutput`、`blockconstellationprovider` |
| Nature's Aura | `blockauraproviderinput`、`blockauraprovideroutput` |
| Botania | `blockmanaproviderinput`、`blockmanaprovideroutput` |

另有**按机器动态注册**的方块（`RegistryBlocks.java:398-441`，受 `Config.onlyOneMachineController` 控制，默认 false 即**开启**）：每台非 `factory-only` 机器生成 `<机器注册名>_controller`；`has-factory` 或 `enableFactoryControllerByDefault` 时额外生成 `<机器注册名>_factory_controller`；`mocCompatibleMode` 时再在 `modularcontroller` 命名空间生成一份兼容控制器。新版无此机制，采用单一控制器——对应旧版 `only-one-machine-controller=true` 的模式。

## 三、等级枚举真值（关键证据）

等级不是方块，而是枚举。三方真值完全自洽（Java 枚举 ↔ blockstate ↔ lang 键）：

| 枚举 | 文件 | 取值 | 级数 |
|---|---|---|---|
| `ItemBusSize` | `.../common/block/prop/ItemBusSize.java:22-28` | TINY(1槽) SMALL(4) NORMAL(6) REINFORCED(9) BIG(12) HUGE(16) LUDICROUS(32) | **7，无 ULTIMATE** |
| `FluidHatchSize` | `.../common/block/prop/FluidHatchSize.java:27-34` | TINY(100) SMALL(400) NORMAL(1000) REINFORCED(2000) BIG(4500) HUGE(8000) LUDICROUS(16000) VACUUM(32000) | 8，含 VACUUM |
| `EnergyHatchData` | `.../common/block/prop/EnergyHatchData.java:33-40` | TINY…LUDICROUS(524288) ULTIMATE(2097152) | 8，含 ULTIMATE |

三重交叉验证（三条独立证据链一致）：

- **blockstate**：`assets/modularmachinery/blockstates/blockinputbus.json` 只有 7 个 `size=` 变体；`blockenergyinputhatch.json` 有 8 个含 `size=ultimate`；`blockfluidinputhatch.json` 有 8 个含 `size=vacuum`。
- **overlay 贴图**：`textures/blocks/` 下 `overlay_inputbus_*.png` 与 `overlay_outputbus_*.png` 各只有 7 张（tiny/small/normal/reinforced/big/huge/ludicrous，**无 ultimate**）；`overlay_energyinputhatch_ultimate.png` 存在。
- **语言文件**：`lang/en_US.lang` 中 `tile.modularmachinery.blockinputbus.*` 与 `blockoutputbus.*` 各 7 个键；`blockenergy*hatch` 各 8 个含 ultimate；`blockfluid*hatch` 各 8 个含 vacuum。

等级由方块状态属性承载：`BlockBus.java:38` `PropertyEnum.create("size", ItemBusSize.class)`、`BlockEnergyHatch.java:34`、`BlockFluidHatch.java:39`；变体物品由 `getSubBlocks` 生成（`BlockBus.java:66-70`），等级经 `getMetaFromState` 存入 item metadata（`BlockBus.java:107-114`）。

## 四、新版注册表（全量）

`ModBlocks.java`：

- `machine_controller`（`:23`，唯一带 BlockEntity 的方块，`:54`）
- 4 个无等级仓口（`:24-27`）：`item_input_hatch`、`item_output_hatch`、`energy_input_hatch`、`fluid_input_hatch`
- 48 个等级仓口（`:31-42`）：
  - `TIERS` = tiny, small, normal, reinforced, big, huge, **ultimate**, ludicrous（`:29`，8 个）× {item_input, item_output, energy_input, energy_output} = 32
  - `FLUID_TIERS` = tiny, small, normal, reinforced, big, huge, ludicrous, **vacuum**（`:30`，8 个）× {fluid_input, fluid_output} = 16
- 方块合计 **53** = 与 `assets/modular_machinery_reborn/blockstates/` 下 53 个文件完全吻合

`ModItems.java`：`itemblueprint`(16)、`itemmodularium`(64)、`itemconstructtool`(1)、`machine_projector`(1)、`redstonesignal`(64)、`wrench`(1)，全部为 `new Item(...)` 裸物品（`:18-19`），无一有行为。

## 五、设计差异

### D1 等级表达：单方块多变体 → 一方块一等级（新版自创）
见「结论摘要 1/2」。影响：旧版存档与机器 JSON 中的 `modularmachinery:blockinputbus@0` 这类描述符在新版完全无对应物；新版注册表项数因此膨胀约 10 倍，而等级之间在代码上完全等价（无任何数值差异）。

### D2 物品仓口多出旧版不存在的 `ultimate` 等级（新版自创）
`ModBlocks.java:29` 的 `TIERS` 含 `ultimate` 并被 `:33-34` 用于物品输入/输出仓口，于是产生 `item_input_hatch_ultimate` / `item_output_hatch_ultimate` 两个旧版从未存在的等级。旧版 `ItemBusSize` 只有 7 级、也没有对应 overlay。项目自己的迁移日志已承认此事并说明用荒谬级贴图顶替。

### D3 `redstonesignal` 与 `wrench` 是新版自创注册，不是迁移
全量检索旧版 `src`，`redstonesignal` **零命中**（连贴图都无引用，是死资源）；`wrench` 仅命中 `models/item/itemconstructtool.json:4` 的贴图引用，**无 Java 注册**。旧版 `RegistryItems.java:44-47` 只注册 4 个物品。新版把两者注册为可获取物品，属于新增内容。

### D4 六个核心方块家族整体未迁移
`blockcasing`（6 种 `CasingType`：PLAIN/VENT/FIREBOX/GEARBOX/REINFORCED/CIRCUITRY，`BlockCasing.java:37,104-111`）、`crushing_wheels`、`blocksmartinterface`、`blockparallelcontroller`（5 级）、`blockupgradebus`（5 级）、`blockfactorycontroller` 在新版全部不存在。新版仅把 `legacy_casing_plain.png` 当作模型底材使用，未注册方块。

### D5 每机器动态控制器未迁移
旧版为每台机器注册独立控制器与工厂控制器方块，并把 `block_machine_controller.json` 复制成各控制器自己的 blockstate（`RegistryBlocks.java:508-546`）。新版只有单一 `machine_controller`，靠配方里的 `machine` 字符串区分。

### D6 命名空间与目录约定变化（审计陷阱）
旧版命名空间 `modularmachinery`、物品贴图目录 `textures/items/`；新版 `modular_machinery_reborn`、`textures/item/`。按旧路径查找新版资产必然找不到。

### D7 注册管线能力丢失
旧版有 `pendingIBlockColorBlocks` 动态染色收集（`RegistryBlocks.java:169`、`499-506`）、自定义名物品（`ItemBlockCustomName` / `ItemBlockMachineComponentCustomName`）、状态机组件等待注册队列（`RegistryBlocks.java:376-395`）。新版只有静态 `DeferredRegister` 列表，这些管线能力全部没有。

### D8 新版注册集合自身不对称
新版给物品/能源/流体的**输入侧**保留了无等级基础方块，但 `energy_output_hatch` 与 `fluid_output_hatch` **只有等级版本**（`ModBlocks.java:36,40`）；而旧版每个家族本就是单一注册名，不存在这种不对称。

## 六、关键发现

1. 旧版没有等级仓口方块，等级是 `PropertyEnum` + item metadata 的多变体机制；新版 48 个等级方块是自创设计，迁移的只是命名与贴图。
2. 新版物品仓口的 `ultimate` 等级是自创的，旧版 `ItemBusSize` 只有 7 级且无对应 overlay——这直接解释了 0.4.3 为什么要拿荒谬级贴图顶替。
3. `redstonesignal` 是旧版死资源、`wrench` 在旧版无注册，新版把两者做成物品属于新增，不是迁移。
4. 旧版 6 个核心方块家族（机箱、破碎轮、智能接口、并行控制器、升级总线、工厂控制器）与全部 26 个模组兼容方块在新版零迁移。
5. 新版仓口除 `machine_controller` 外**没有任何 BlockEntity**（`ModBlocks.java:53-54` 只注册了控制器），53 个方块里 52 个是无能力、无库存、无等级的占位壳。

## 七、不确定项

1. 本审计只覆盖 `_mmce-src/ModularMachinery-Community-Edition-master` 这一份源码快照；其他分支或历史版本是否曾注册 `redstonesignal`/`wrench` 未核实。
2. 新版 48 个等级仓口是否会按 `移植计划.md` 所述在后续阶段接入能力，只能依据当前源码判断，无法预判后续版本。
3. `blockcasing@N` 中 N 的具体语义（各 CasingType 与朝向的映射）未逐项展开。
4. `ModBlocks.java` 的 4 个无等级仓口是否在后续版本会被移除或改作别名，属设计意图问题，无法从代码判定。
