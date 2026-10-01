# 配方系统与需求类型

审计范围：Modular Machinery: Community Edition 2.3.2（Minecraft 1.12.2）配方系统与需求类型 vs. `modular-machinery-reborn`（Minecraft 1.20.1 / Forge 47.2.0）重实现工程。

**路径缩写约定（全文证据引用使用）**

- `[旧]` = `C:\Users\kk07H\Desktop\模组\_mmce-src\ModularMachinery-Community-Edition-master\src\main\`
- `[新]` = `C:\Users\kk07H\Desktop\模组\modular-machinery-reborn\src\main\`

新版配方相关代码仅 3 个生产类 + 1 份配方 JSON：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java`（37 行）、`ModRecipeSerializers.java`（35 行）、`ModRecipeTypes.java`（13 行）、`[新]resources/data/modular_machinery_reborn/recipes/iron_to_gold.json`（1 行）。`[新]` 下不存在 `requirement` / `component` / `adapter` / `tooltip` / `serialize` 任何包。

---

## 一、已迁移

1. **JSON 数据驱动的配方加载与自定义配方注册**
   旧版以 GSON 反序列化目录中的 `.json`，注册到自建 `RECIPE_REGISTRY`；新版走原版 `RecipeType`/`RecipeSerializer`/`RecipeManager`，配方类型 key 为 `modular_machinery_reborn:machine`。两者都是「JSON → 配方对象 → 按所属机器匹配」的同构流程。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/RecipeLoader.java:82-104`（扫目录 + GSON 反序列化）、`RecipeRegistry.java:128-182`（注册与按机器索引）
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeTypes.java:11`、`ModRecipeSerializers.java:16-26`、`[新]resources/data/modular_machinery_reborn/recipes/iron_to_gold.json:1`

2. **配方归属机器字段 `machine`（字符串形态）**
   旧版 `machine` 接受字符串；新版同样接受字符串（缺省回退 `"basic"`），语义均为「本配方属于哪台机器」。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/MachineRecipe.java:329-331`（缺 machine 报错）、`MachineRecipe.java:356-357`（字符串分支）
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:20`、`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:70`

3. **配方时长字段（改名保留）**
   旧版 `recipeTime`（int，单位 tick）→ 新版 `duration`（int，tick，缺省 100）。语义等价，仅字段名与可选性不同。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/MachineRecipe.java:335-336`（必填校验）、`MachineRecipe.java:368-371`、`MachineRecipe.java:397`
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:23`、`MachineRecipe.java:18,28`、`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:52-53`

4. **单一物品输入 + 物品输出（含输出数量）**
   旧版 `type=modularmachinery:item` + `io-type=input/output` + `item` + `amount`；新版以固定字段 `input`（Ingredient）与 `output`（ItemStack，带 `count`）表达，`amount` 的语义由 `output.count` 承接。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/requirement/type/RequirementTypeItem.java:37-58`（item + amount，1..64 夹取）
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:21-22`、`[新]resources/data/modular_machinery_reborn/recipes/iron_to_gold.json:1`

---

## 二、部分迁移

1. **物品需求（`modularmachinery:item`）** —— 只有最简形态，核心行为大量缺失
   已具备：单一物品输入/输出、`item` 注册名解析、输出数量。
   缺失：`ore:<name>` 矿辞写法、`ns:path@meta` 元数据后缀、`chance` 概率、`nbt` / `nbt-display`、`any:fuel` + `time` 燃料总燃烧时长、`minAmount`/`maxAmount` 随机数量、`consumeDurability` 耐久消耗、`ignoreOutputCheck`、并行度倍数消耗。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/requirement/type/RequirementTypeItem.java:43-51`（@meta 解析）、`:61-70`（any:fuel + time）、`:71-72`（ore:）、`:86-94`（chance）、`:95-119`（nbt/nbt-display）；`requirement/RequirementItem.java:78-79`（min/maxAmount）、`:123-133`（setConsumeDurability/supportsDurability）、`:253-264`（start/finishCrafting 按 chance 触发）、`:472-521`（insertAllItems 空间模拟）
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:21-22`（仅 `Ingredient.fromJson` + `ShapedRecipe.itemStackFromJson`）、`MachineRecipe.java:13-37`（配方对象仅 id/machine/input/output/duration/energy 六个字段）

2. **能量需求（`modularmachinery:energy`）** —— 字段保留但语义改变
   旧版为 `energyPerTick`，在配方持续期间**每 tick** 抽取，并可作为输出（`io-type=output` 向机器充能）；新版为 `energy`，在配方**完成的瞬间一次性**扣除，且无输入/输出方向概念。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/requirement/type/RequirementTypeEnergy.java:27-32`（energyPerTick 必填）、`requirement/RequirementEnergy.java:41-49`（per-tick 语义）、`:99-101`（doIOTick）、`:119-130`（并行度）；`crafting/ActiveMachineRecipe.java:78-118`（每 tick 调 ioTick）、`helper/RecipeCraftingContext.java:238-279`（ioTick 分发）
   新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:24`、`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:54-58`（`energy.getEnergyStored() < recipe.energy()` 后整段抽取）

3. **JEI 配方展示** —— 只有最简外壳，逐需求渲染全部缺失
   旧版有 7 个 `JEIComponent*`（item / fluid / fluid_pertick / gas / gas_pertick / energy / catalyst / ingredient_array）与 5 个 `RequirementTip` tooltip 类，按需求类型渲染槽位与提示；新版只有一个品类，固定「1 输入槽 + 1 输出槽 + 一行 duration/energy 文本」。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/crafting/requirement/jei/`（7 个类，如 `JEIComponentFluidPerTick.java:14-44`：流体槽 + `tooltip.fluid_pertick.in/out`）、`crafting/tooltip/RequirementTip.java:28-46`、`tooltip/TooltipFuelInput.java:30-55`、`tooltip/TooltipInterfaceNumberInput.java:18-68`
   新版证据：`[新]java/com/reborn/modularmachinery/client/jei/MachineRecipeCategory.java:31-36`（仅 2 槽 + 文本）、`client/jei/ModularMachineryJeiPlugin.java:19-23`

4. **脚本化配方接口** —— 新版只有 schema 外壳
   新版注册了 KubeJS 的 `JsonRecipeSchema.SCHEMA`，使 `event.recipes.modular_machinery_reborn.machine` 可用；但旧版 CraftTweaker 侧的能力（`setMinMaxAmount`、`consumeDurability`、`setChance`、`setTriggerTime`、`addItemModifier`、`RequirementCatalyst` 的 modifier/tooltip、`RecipeAdapterBuilder` 建适配器）在 1.20.1 工程内无任何对应实现。
   旧版证据：`[旧]java/hellfirepvp/modularmachinery/common/integration/crafttweaker/RecipePrimer.java:166-229`（addItemModifier / setMinMaxAmount / consumeDurability / setTriggerTime）、`RecipePrimer.java:836-867`（构造 RequirementCatalyst）
   新版证据：`[新]java/com/reborn/modularmachinery/kubejs/ModularMachineryKubeJSPlugin.java:11-14`

---

## 三、未迁移

### 参考对照表 A：旧版 requirement 类型完整清单（注册表 + 注册 key + 模组依赖 + JSON 可用性）

注册入口 `[旧]java/hellfirepvp/modularmachinery/common/registry/RegistryRequirementTypes.java:60-74`；key 定义 `[旧]java/hellfirepvp/modularmachinery/common/lib/RequirementTypesMM.java:33-43`。

| 注册 key（`modularmachinery:` 命名空间） | 类型类 / 需求类 | 依赖模组 | JSON 路径可用性 |
| --- | --- | --- | --- |
| `item` | `RequirementTypeItem` / `RequirementItem` | 无 | 可用 |
| `item_durability` | `RequirementTypeItemDurability` / `RequirementItemDurability` | 无 | **不可用**：`createRequirement` 恒 `return null` |
| `ingredient_array_input` | `RequirementTypeIngredientArray` / `RequirementIngredientArray` | 无 | 可用 |
| `fluid` | `RequirementTypeFluid` / `RequirementFluid` | 无 | 可用 |
| `fluid_pertick` | `RequirementTypeFluidPerTick` / `RequirementFluidPerTick` | 无 | **不可用**：恒 `return null` |
| `gas` | `RequirementTypeGas` / `RequirementGas` | `mekanism` | 可用（仅装 Mekanism 时注册） |
| `gas_pertick` | `RequirementTypeGasPerTick` / `RequirementGasPerTick` | `mekanism` | **不可用**：恒 `return null` |
| `energy` | `RequirementTypeEnergy` / `RequirementEnergy` | 无 | 可用 |
| `duration` | `RequirementDuration` | 无 | **不可用**：仅作 modifier 目标，`createRequirement` 抛 `UnsupportedOperationException` |
| `interface_number_input` | `RequirementTypeInterfaceNumInput` / `RequirementInterfaceNumInput` | 无 | **不可用**：恒 `return null` |
| （无注册 key） | `RequirementTypeCatalyst` / `RequirementCatalyst` | 无 | **不可用**：未在 `RegistryRequirementTypes` 注册，且 `createRequirement` 返回 `null` |

依赖判定依据：`RequirementType.requiresModid()`（`[旧].../requirement/type/RequirementType.java:29-32`）、`RequirementTypeGas.requiresModid()=="mekanism"`（`RequirementTypeGas.java:32-36`）、`Mods.MEKANISM.isPresent()` 条件注册（`RegistryRequirementTypes.java:65-71`）、加载期剔除逻辑（`crafting/IntegrationTypeHelper.java:45-58`）。

### 参考对照表 B：旧版 component 类型完整清单

注册入口 `[旧]java/hellfirepvp/modularmachinery/common/registry/RegistryComponentTypes.java:53-60`；key 定义 `[旧]java/hellfirepvp/modularmachinery/common/lib/ComponentTypesMM.java:24-31`。

| 注册 key（`modularmachinery:`） | 类型类 | `requiresModid()` |
| --- | --- | --- |
| `item` | `ComponentItem` | 无（`ComponentItem.java:32-36` 返回 null） |
| `fluid` | `ComponentFluid` | 无（`ComponentFluid.java:32-36`） |
| `item_fluid` | `ComponentItemFluid` | 无（`ComponentItemFluid.java:9-13`） |
| `gas` | `ComponentGas` | `mekanism`（`ComponentGas.java:34-35`） |
| `energy` | `ComponentEnergy` | 无（`ComponentEnergy.java:32-36`） |
| `interface_number` | `ComponentSmartInterface` | 无（`ComponentSmartInterface.java:10-14`） |
| `parallel_controller` | `ComponentParallelController` | 无（`ComponentParallelController.java:9-13`） |
| `upgrade` | `ComponentUpgradeBus` | 无（`ComponentUpgradeBus.java:9-13`） |

### 参考对照表 C：旧版配方 / 适配器 JSON 全部字段

普通配方（`MachineRecipe.Deserializer`，`[旧].../crafting/MachineRecipe.java:326-428`）：

| 字段 | 类型 | 必填 | 说明与代码位置 |
| --- | --- | --- | --- |
| `machine` | string 或 string[] | 必填 | 归属机器，自动加 `modularmachinery` 命名空间；数组表示一配方多机器（`:329-331, :338-360`） |
| `registryName` / `registryname` | string | 必填 | 配方唯一名（`:332-334, :361-367`） |
| `recipeTime` | int | 必填 | 总时长 tick（`:335-337, :368-371`） |
| `priority` | int | 可选（默认 0） | 匹配优先级（`:373-379`） |
| `cancelIfPerTickFails` | bool | 可选（默认 false） | 每 tick 失败是否取消整条配方（`:382-388`） |
| `requirements` | object[] | 必填，≥1 项 | 需求数组（`:405-422`） |
| `startCommands` / `processingCommands` / `finishCommands` | array | 可选 | 三条命令数组（`:312-324, :424-426`） |

requirement 对象公共字段（`MachineRecipe.ComponentDeserializer`，`:432-480`）：`type`（string，必填，`:441-445`）、`io-type`（string，必填，`:446-450`，取值 input/output）、`selector-tag`（string，可选，`:468-476`）。

按需求的私有字段：

| 需求类型 | 私有字段 | 代码位置 |
| --- | --- | --- |
| `item` | `item`（支持 `ns:path`、`ns:path@meta`、`ore:name`、`any:fuel`）、`amount`（1..64 夹取）、`chance`（0..1）、`nbt`、`nbt-display`、`time`（仅 `any:fuel` 时必填，燃烧总时长） | `RequirementTypeItem.java:37-119` |
| `fluid` | `fluid`（string）、`amount`（int，≥0）、`chance`、`nbt`、`nbt-display` | `RequirementTypeFluid.java:34-84` |
| `gas` | `gas`（string）、`amount`（int，≥0）、`chance` | `RequirementTypeGas.java:46-72` |
| `energy` | `energyPerTick`（long） | `RequirementTypeEnergy.java:27-32` |
| `ingredient_array_input` | `items`（object[]，必填，元素含 `item`/`amount`/`nbt`）、`chance`（外层，同时写入每个子项与需求本身） | `RequirementTypeIngredientArray.java:48-53, :105-125, :132-140` |

适配器写法（`.adapter.json`，`RecipeAdapterAccessor.Deserializer`，`[旧].../crafting/adapter/RecipeAdapterAccessor.java:116-167`）：`machine`（string，必填）、`adapter`（string，必填，如 `minecraft:furnace`）、`modifiers`（array，可选，元素字段 `io`/`target`/`multiplier`/`operation`/`affectChance`，见 `[旧].../modifier/RecipeModifier.java:225-266`）、`requirements`（array，可选，元素为 requirement 对象）。

**仅 CraftTweaker 可表达、JSON 完全无法表达者**：`minAmount`/`maxAmount` 随机数量（`RequirementItem.java:78-79` 默认 1/1；`RecipePrimer.java:179-196`）、`consumeDurability` 耐久消耗（`RecipePrimer.java:198-220`）、触发时间 `setTriggerTime`（`RecipePrimer.java:222-229`）、`AdvancedItemChecker`/`AdvancedItemModifier`（`RequirementItem.java:115-121`）、`RequirementCatalyst` 的 modifier/tooltip 列表（`requirement/RequirementCatalyst.java:19-48`）。

### 条目清单

**N1. requirement 类型注册与解析体系**
`RequirementType` 抽象基类（`createRequirement` / `requiresModid`）、`RegistryRequirementTypes.initialize()` 逐项注册、`RegistriesMM.REQUIREMENT_TYPE_REGISTRY` 查表、以及旧 key 名回退 `IntegrationTypeHelper.searchRequirementType` 全部缺失；新版配方只认固定 `input`/`output` 字段，无任何 requirement 类型概念。
旧版证据：`[旧].../crafting/requirement/type/RequirementType.java:25-32`、`registry/RegistryRequirementTypes.java:59-81`、`crafting/IntegrationTypeHelper.java:60-68`、`crafting/MachineRecipe.java:452-461`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`（配方模型无 requirement 结构）、`ModRecipeSerializers.java:19-26`

**N2. 流体需求 `fluid` 与每 tick 流体需求 `fluid_pertick`**
含流体名/数量校验、`HybridFluidUtils` 多流体仓模拟抽注、per-tick 版本。新版仅有 `FluidTank` 存储与 capability 暴露，配方完全不消费/产出流体。
旧版证据：`[旧].../requirement/type/RequirementTypeFluid.java:34-60`、`requirement/RequirementFluid.java`、`requirement/RequirementFluidPerTick.java:33-141`、`lib/RequirementTypesMM.java:36-37`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:38,83,93`（仅有流体仓与 capability，配方字段无流体）

**N3. 气体需求 `gas` 与每 tick 气体需求 `gas_pertick`（依赖 Mekanism）**
含 `mekanism` 依赖声明与条件注册。
旧版证据：`[旧].../requirement/type/RequirementTypeGas.java:32-72`、`requirement/type/RequirementTypeGasPerTick.java:8-12`、`registry/RegistryRequirementTypes.java:65-71`、`lib/RequirementTypesMM.java:38-39`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`（无气体字段）；`[新]` 全工程无 `mekanism` 引用

**N4. 物品组需求 `ingredient_array_input`**
`items` 数组、组内任一物品满足即可、子项独立 `chance`/`nbt`。
旧版证据：`[旧].../requirement/type/RequirementTypeIngredientArray.java:44-143`、`requirement/RequirementIngredientArray.java:36-97`、`lib/RequirementTypesMM.java:35`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:21`（单一 `Ingredient`，无数组语义）

**N5. 物品耐久需求 `item_durability`**
旧版本身即为半成品（`RequirementItemDurability` 多数方法返回 null/0/emptyList），但类型与需求类、JEI 挂点骨架存在；新版无任何对应物。
旧版证据：`[旧].../requirement/type/RequirementTypeItemDurability.java:9-14`（`createRequirement` 返回 null）、`requirement/RequirementItemDurability.java:21-88`、`lib/RequirementTypesMM.java:34`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`

**N6. 智能接口数值需求 `interface_number_input`**
依赖 `ComponentSmartInterface`（注册 key `interface_number`）与 `TileSmartInterface.SmartInterfaceProvider`，做 `minValue<=value<=maxValue` 范围校验与自定义失败消息。
旧版证据：`[旧].../requirement/RequirementInterfaceNumInput.java:23-111`、`requirement/type/RequirementTypeInterfaceNumInput.java:7-12`、`registry/RegistryComponentTypes.java:58`、`lib/ComponentTypesMM.java:29`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`；`[新]` 无 smart interface 相关类

**N7. 催化器需求（`RequirementCatalyst`）**
可选输入 + `parallelizeUnaffected`，满足时向 `RecipeCraftingContext` 注入永久 `RecipeModifier`（并行时按 parallelism 倍增）；只有 CraftTweaker 可达，无注册 key。
旧版证据：`[旧].../requirement/RequirementCatalyst.java:18-133`、`requirement/type/RequirementTypeCatalyst.java:9-15`、`crafting/requirement/jei/JEIComponentCatalyst.java:14-16`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`（无 catalyst/modifier 概念）

**N8. `duration` 需求类型（helper-only）**
旧版显式禁止实例化，仅作为 modifier 的 `target`（`modularmachinery:duration`）；新版 `duration` 是普通 int 字段，不存在可被 modifier 定位的类型对象。
旧版证据：`[旧].../requirement/type/RequirementDuration.java:22-28`、`lib/RequirementTypesMM.java:42`、`registry/RegistryRequirementTypes.java:74`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:18,28`

**N9. 输入/输出（`io-type` + `IOType`）概念**
旧版每个需求必须声明 `io-type`，同一类型可作输入或输出；新版输入与输出是两个互不相干的固定字段。
旧版证据：`[旧].../crafting/MachineRecipe.java:446-465`（io-type 必填 + `IOType.getByString`）
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:21-22`、`MachineRecipe.java:25-27`

**N10. `chance` 概率产出与概率消耗**
`ComponentRequirement.ChancedRequirement`、`RequirementItem.chance`（默认 1F）、`startCrafting`（输入）/`finishCrafting`（输出）经 `ResultChance` 判定、默认配方中的 0.95/0.4/0.7/0.05/0.1/0.3/0.01/0.6/0.75/0.2 全部为概率产出。
旧版证据：`[旧].../requirement/RequirementItem.java:76,232-234,253-264`、`crafting/helper/ComponentRequirement.java:274-277`、`resources/assets/modularmachinery/default_recipes/centrifuge/centrifuge_centrifuge_grass.json`（9 条带 chance 的输出）、`.../alloy_smelter/alloy_smelter_diamond.json`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:19-26`（无 chance 字段）、`[新]resources/data/modular_machinery_reborn/recipes/iron_to_gold.json:1`

**N11. 每 tick 资源消耗机制**
`ComponentRequirement.PerTick` / `PerTickMultiComponent` / `PerTickParallelizable` 抽象族、`doIOTick(components, context, durationMultiplier)`、`RecipeCraftingContext.ioTick(currentTick)`、`ActiveMachineRecipe.tick()` 每 tick 调用；新版在主 tick 里只累加 `progress`，能量在完成时一次性扣。
旧版证据：`[旧].../crafting/helper/ComponentRequirement.java:492-584`、`crafting/helper/RecipeCraftingContext.java:238-279`、`crafting/ActiveMachineRecipe.java:78-118`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:55-58`（仅 `be.progress++` 与完成时 `extractEnergy`）

**N12. 并行度机制**
`Parallelizable`、`getMaxParallelism`、`parallelizeUnaffected`、`ignoreOutputCheck`、`parallelism` 倍率，被每个需求类逐一实现；新版无并行概念（一次一配方）。
旧版证据：`[旧].../crafting/helper/ComponentRequirement.java:284-330,446-490`、`requirement/RequirementItem.java:272-284`、`requirement/RequirementEnergy.java:118-130`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:55-62`（单次进度循环）

**N13. 配方级字段 `registryName` / `priority` / `cancelIfPerTickFails` / 三条命令数组**
新版 JSON 只识别 `machine`/`input`/`output`/`duration`/`energy` 五键（加 `type`），其余一律忽略，也无「每 tick 失败取消配方」与「命令阶段」概念。
旧版证据：`[旧].../crafting/MachineRecipe.java:332-334,361-367`（registryName）、`:373-379`（priority）、`:382-388`（cancelIfPerTickFails）、`:312-324,424-426`（三条命令数组）
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:19-26`

**N14. `machine` 数组形式（一配方属多台机器）**
旧版数组会把同一配方复制到每台机器；新版 `machine` 只接受字符串。
旧版证据：`[旧].../crafting/MachineRecipe.java:340-355`、`MachineRecipe.java:403`（`recipeOwnerList.addAll`）
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:20`（`getAsString()`，数组会抛异常/取首元素由 Gson 语义决定）

**N15. `selector-tag`（`ComponentSelectorTag`）**
多组件机器中用标签筛选参与该需求的 hatch。
旧版证据：`[旧].../crafting/MachineRecipe.java:468-476`、`crafting/helper/ComponentSelectorTag.java`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`

**N16. 配方修改器 `RecipeModifier`**
`target`（requirement key）/`io`/`operation`（0 加法、1 乘法）/`multiplier`/`affectChance`，既能由 `.adapter.json` 的 `modifiers` 声明，也能由脚本注入；影响数量、概率与时长。
旧版证据：`[旧].../modifier/RecipeModifier.java:51-57,130-160,222-266`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`；`[新]` 无 modifier 包

**N17. 适配器系统核心**
`RecipeAdapter` 基类（`createRecipesFor` / `createRecipeShell`）、`RecipeAdapterRegistry.createRecipesFor`、`RecipeAdapterAccessor`（`adapter` + `modifiers` + `requirements` 反序列化）、`RecipeAdapterRegistry` 为每台机器注册 `DynamicMachineRecipeAdapter`、以及 `RecipeLoader.loadAdapterRecipes` 的 `.adapter.json` 分流加载。
旧版证据：`[旧].../crafting/adapter/RecipeAdapter.java:37-72`、`adapter/RecipeAdapterRegistry.java:36-51`、`adapter/RecipeAdapterAccessor.java:42-167`、`adapter/DynamicMachineRecipeAdapter.java:37-54`、`crafting/RecipeLoader.java:106-136,150-162`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/`（仅 3 文件，无 adapter）、`ModRecipeSerializers.java:19-26`

**N18. 12 个内置适配器注册 key**
`minecraft:furnace`（`AdapterMinecraftFurnace.java:43`）、`ic2:te_compressor`（`AdapterIC2Compressor.java:23`）、`ic2:te_macerator`（`AdapterIC2Macerator.java:23`）、`nuclearcraft:alloy_furnace`（`AdapterNCOAlloyFurnace.java:34`）、`nuclearcraft:infuser`（`AdapterNCOInfuser.java:38`）、`nuclearcraft:chemical_reactor`（`AdapterNCOChemicalReactor.java:29`）、`nuclearcraft:melter`（`AdapterNCOMelter.java:38`）、`tconstruct:smeltery_melting`（`AdapterSmelteryMeltingRecipe.java:32`）、`tconstruct:smeltery_alloy`（`AdapterSmelteryAlloyRecipe.java:27`）、`thaumcraft:infusion_matrix`（`AdapterTC6InfusionMatrix.java:34`）、`thermalexpansion:insolator` / `thermalexpansion:insolator_tree`（`InsolatorRecipeAdapter.java:39`）；注册与条件门控见 `registry/RegistryRecipeAdapters.java:41-62`，唯一对外静态字段 `RecipeAdaptersMM.MINECRAFT_FURNACE`（`lib/RecipeAdaptersMM.java:22`）。
新版证据：`[新]java/com/reborn/modularmachinery/recipe/`（无等价物）

**N19. component 类型注册体系（8 个 key）**
见对照表 B；`ComponentType.requiresModid()` 与 `IntegrationTypeHelper.filterModIdComponents()` 的加载期剔除逻辑在新版完全不存在（新版以方块注册表区分 hatch 类型，无 component 类型对象）。
旧版证据：`[旧].../crafting/ComponentType.java:22-40`、`registry/RegistryComponentTypes.java:52-67`、`crafting/IntegrationTypeHelper.java:30-43`
新版证据：`[新]java/com/reborn/modularmachinery/block/ModBlocks.java:26-40`（按方块键注册 hatch，无 component 类型/依赖声明机制）

**N20. 配方命令系统**
`RecipeRunnableCommand`（含 Deserializer）、`RecipeCommandContainer`（start/processing/finish 三类命令，带 tick 偏移）、`ControllerCommandSender`。
旧版证据：`[旧].../crafting/command/RecipeRunnableCommand.java`、`command/RecipeCommandContainer.java`、`command/ControllerCommandSender.java`、`crafting/MachineRecipe.java:424-426`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/MachineRecipe.java:13-37`

**N21. Kotlin 序列化层（9 个文件）**
`DataStructure`（类型化列定义：`byte`/`short`/`integer`/`long`/`float`/`double`/`boolean`/`string`/`list`/`subStructure`/`multipleStructure`/`enum`，字段名即 JSON key，取值失败抛 `IllegalArgumentException("Failed to parse column '<name>'")`）、`DataValue`/`DataValueImpl`（`nullable`/`notnull`/`def` 默认值/`map` 变换）、`RawData`/`RawDataStructure` 抽象、`JsonRawData`/`JsonRawDataStructure`（kotlinx.serialization 实现）、`ItemRequirementData`（声明 `item`/`amount`/`chance`/`time`/`nbt`/`nbt-display`）、`EnergyRequirementData`（声明 `energyPerTick`）。
新版无 Kotlin 源文件（全工程 Java 17）。
旧版证据：`[旧]kotlin/common/serialize/DataStructure.kt:8-113`、`DataValue.kt:5-31`、`DataValueImpl.kt:4-58`、`json/JsonRawData.kt:9-17`、`json/JsonRawDataStructure.kt:14-37`、`raw/RawData.kt:3-7`、`raw/RawDataStructure.kt:3-24`、`crafting/requirement/ItemRequirementData.kt:10-53`、`crafting/requirement/EnergyRequirementData.kt:6-9`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:3-13`（用 Gson `JsonObject` 手工取键）

**N22. tooltip 包（5 个类）**
`RequirementTip` 抽象（`filterRequirements` + `buildTooltip`，注册为 Forge 注册项）、`TooltipEnergyInput`、`TooltipEnergyOutput`、`TooltipFuelInput`（汇总 `fuelBurntime`）、`TooltipInterfaceNumberInput`（按 `SmartInterfaceType` 的 jeiTooltip 模板格式化 min/max）。
新版 JEI 无任何需求级 tooltip。
旧版证据：`[旧].../crafting/tooltip/RequirementTip.java:28-46`、`tooltip/TooltipFuelInput.java:30-55`、`tooltip/TooltipInterfaceNumberInput.java:18-68`
新版证据：`[新]java/com/reborn/modularmachinery/client/jei/MachineRecipeCategory.java:31-36`

**N23. requirement 检查与执行引擎（helper 包）**
`ComponentRequirement`（`canStartCrafting`/`startCrafting`/`finishCrafting`/`deepCopy`/`deepCopyModified`/`provideJEIComponent`）、`CraftCheck`、`CraftingStatus`、`RecipeCraftingContext`、`ProcessingComponent`、`RequirementComponents`、`ComponentOutputRestrictor`、`ComponentSelectorTag`、`MultiComponent`/`MultiCompParallelizable`。
新版对应的执行逻辑全部内联在 `MachineControllerBlockEntity.tick` 的十几行里，无检查/模拟/失败消息体系。
旧版证据：`[旧].../crafting/helper/ComponentRequirement.java:31-248,339-410,431-490`、`helper/CraftCheck.java`、`helper/RecipeCraftingContext.java`、`helper/ProcessingComponent.java`、`helper/RequirementComponents.java`、`helper/CraftingStatus.java`、`helper/ComponentOutputRestrictor.java`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:48-66`

**N24. 配方加载失败诊断与注册/重载机制**
`RecipeLoader.failedAttempts` + `captureFailedAttempts()`、`FileType`（区分 `.adapter.json` 与 `.json`）、`RecipeRegistry`（按机器与优先级建 TreeMap 索引、重名告警、重载时移除缓存）、`PreparedRecipe`（脚本侧预置配方）。
旧版证据：`[旧].../crafting/RecipeLoader.java:52,93-98,144-148,150-162`、`crafting/RecipeRegistry.java:62,128-182`、`crafting/PreparedRecipe.java`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:19-26`（仅 `fromJson`，无失败收集与重载钩子）

---

## 四、设计差异

**D1. 输入/输出表达方式：`type` + `io-type` 的 requirement 数组 → 固定 `input`/`output` 字段**
旧版：`requirements` 数组，每项先解析 `type` 再解析 `io-type`，同一类型可输入可输出；新版：`input` 与 `output` 是配方对象的两个独立固定键，没有 IOType 概念，也无法表达「同类型多份输入」或「同类型多份输出」。
旧版证据：`[旧].../crafting/MachineRecipe.java:441-465`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:21-22`、`MachineRecipe.java:16-17`

**D2. 能量语义：per-tick 抽取 → 完成瞬间一次性扣除**
旧版 `RequirementEnergy` 每 tick 抽取并按 `durationMultiplier` 缩放，中途失败按 `CraftingStatus` 判定重试/重置 tick；新版只在 `progress>=maxProgress` 的分支里 `extractEnergy(recipe.energy(), false)`。
旧版证据：`[旧].../requirement/RequirementEnergy.java:99-101`、`crafting/ActiveMachineRecipe.java:78-118`、`helper/RecipeCraftingContext.java:238-279`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:54-58`

**D3. 机器归属：带命名空间 + 数组 → 裸字符串 + 运行期硬编码白名单**
旧版 `machine` 值会被加上 `modularmachinery` 命名空间构成 `ResourceLocation`，并支持数组；新版保留裸字符串，且运行期只接受字面量 `"basic"` 或 `"machine_controller"`，其他机器名的配方会被静默忽略。
旧版证据：`[旧].../crafting/MachineRecipe.java:347,357,403`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:20`、`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:70`

**D4. 配方数据来源：config 目录扫描 → 原版 data pack / RecipeManager**
旧版从文件系统目录递归发现 JSON，用 `FileType` 区分 `.adapter.json` 与普通 `.json`，配 `CURRENTLY_READING_PATH` 记录来源路径；新版走原版 `RecipeManager.getAllRecipesFor`，配方资源位于 `data/modular_machinery_reborn/recipes/`，无 adapter 分流。
旧版证据：`[旧].../crafting/RecipeLoader.java:54-80,86-98,150-162`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeTypes.java:11`、`[新]resources/data/modular_machinery_reborn/recipes/iron_to_gold.json:1`

**D5. 输出空间不足时的行为不一致（旧版先模拟，新版可能重复消耗）**
旧版 `RequirementItem.insertAllItems` 返回实际插入数，`doItemIO` 据 `mul < parallelism` 判定 `craftcheck.failure.item.output.space`，配方在启动阶段即被拒绝，不会消耗输入与能量。新版在完成分支里**先**扣能量、**先** `extractItem(0,1,false)`，再尝试 `insertItem`；若返回非空则把 `progress` 置回 `maxProgress`，下一 tick 会重新进入该分支再次扣能量与输入——存在重复消耗/吞输入的语义差异。
旧版证据：`[旧].../requirement/RequirementItem.java:286-295`、`RequirementItem.java:472-521`
新版证据：`[新]java/com/reborn/modularmachinery/block/MachineControllerBlockEntity.java:56-62`

**D6. 缺字段容错：旧版严格抛异常 → 新版静默默认值**
旧版缺 `machine`/`registryName`/`recipeTime` 立即 `JsonParseException`，缺 `requirements` 或需求为空也报错；新版 `machine` 默认 `"basic"`、`duration` 默认 100、`energy` 默认 0，缺字段不会产生任何提示。
旧版证据：`[旧].../crafting/MachineRecipe.java:329-337,405-422`
新版证据：`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:20,23,24`

**D7. 脚本集成从 CraftTweaker 换成 KubeJS，且能力大幅缩水**
旧版通过 `RecipePrimer`（ZenMethod 链式 API）与 `RecipeAdapterBuilder` 暴露 setMinMaxAmount / consumeDurability / setChance / setTriggerTime / addItemModifier / 建适配器；新版只注册了一个 KubeJS 配方 schema（外壳），没有对应的能力 API。
旧版证据：`[旧].../integration/crafttweaker/RecipePrimer.java:165-229,836-867`、`integration/crafttweaker/RecipeAdapterBuilder.java`
新版证据：`[新]java/com/reborn/modularmachinery/kubejs/ModularMachineryKubeJSPlugin.java:11-14`

**D8. JEI 展示粒度：逐需求类型渲染 → 固定两槽**
旧版为每种需求提供 `JEIComponent`（`getJEIRequirementClass`/`getJEIIORequirements`/`getLayoutPart`/`onJEIHoverTooltip`）与 `RequirementTip`，可渲染流体罐、气体罐、能量、催化器并输出带单位含义的 tooltip；新版固定「输入槽(20,26) + 输出槽(110,26)」加一行 `duration + " ticks  " + energy + " FE"` 文本。
旧版证据：`[旧].../crafting/requirement/jei/JEIComponentFluidPerTick.java:14-44`、`crafting/tooltip/RequirementTip.java:28-46`
新版证据：`[新]java/com/reborn/modularmachinery/client/jei/MachineRecipeCategory.java:31-36`

**D9. 旧版内部存在两套并存的序列化设计（Kotlin DataStructure 层与 Gson 层）**
`ItemRequirementData` / `EnergyRequirementData` 用 `string("item")`/`integer("amount")`/`float("chance")`/`integer("time")`/`long("energyPerTick")` 声明了与 JSON 一一对应的字段（含 `def` 默认值），但全仓库除自身定义外没有任何引用点，主流程走的是 `RecipeLoader` 的 Gson `MachineRecipe.Deserializer`。这属于旧版遗留/未接线实现，不是新版差异，但会造成「文档字段集」与「实际字段集」不一致。
旧版证据：`[旧]kotlin/common/serialize/crafting/requirement/ItemRequirementData.kt:10-53`、`EnergyRequirementData.kt:6-9`、`[旧]java/hellfirepvp/modularmachinery/common/crafting/RecipeLoader.java:45-51`
新版证据：（不适用，新版只有单层 Gson 解析）`[新]java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:3-13`

---

## 五、关键发现

1. 新版配方系统只剩「一个输入 → 一个输出 + 时长 + 一次性能量」四个概念，旧版 10 个 requirement 类型、8 个 component 类型、12 个适配器、修改器与并行度机制**全部为零**，配方系统整体迁移率约 4/33 条目。
2. 旧版需求类型的 JSON 可用性本身参差不齐：`item_durability`、`fluid_pertick`、`gas_pertick`、`interface_number_input` 的 `createRequirement` 直接 `return null`，`duration` 抛 `UnsupportedOperationException`，`RequirementCatalyst` 未注册——**这 6 项在旧版 JSON 路径下就已经不可用**，只经 CraftTweaker 可达。
3. `chance` 概率产出与每 tick 资源消耗是旧版配方的核心玩法（默认配方中 10 处 `chance`、能量全部为 `energyPerTick`），新版两者均无：能量改为完成时一次性扣除，概率字段被静默忽略。
4. 旧版默认配方依赖的字段（`registryName`、`recipeTime`、`modularmachinery:item` 的 `ore:ingotIron` 等矿辞写法、`io-type`）在新版 JSON schema 下会全部失效，`alloy_smelter`/`centrifuge` 10 份配方与 `alloy_smelter_furnaces.adapter.json` 无一可直接沿用。
5. 新版存在一个明确的资源重复消耗缺陷：产物放不下时 `progress` 被置回 `maxProgress`，而能量与输入物品已在同一分支扣除，下一 tick 会重复扣除（旧版靠 `insertAllItems` 的模拟插入避免）。
6. 旧版 Kotlin 序列化层（9 个文件）虽然声明了完整的 `item`/`amount`/`chance`/`time`/`nbt`/`energyPerTick` 字段契约，却在全仓库无任何引用，属未接线的遗留实现；新版也没有继承这套设计。

---

## 六、不确定项

1. **未编译、未运行任何一侧工程**。全部结论来自静态阅读，未通过构建、启动或单元测试验证运行期行为。
2. **未穷尽新版工程外部来源**。只在 `[新]src/main/java` 与 `[新]src/main/resources` 内检索；若 requirement 行为由外部 datapack、KubeJS 脚本、其他 mod 的 mixin/插件补充，本报告未覆盖。
3. **新版 `Ingredient.fromJson` 的能力边界未核实**。`ModRecipeSerializers.java:21` 直接委托 Forge 的 `Ingredient.fromJson`，我未核实它在 1.20.1/Forge 47.2.0 下是否支持旧版惯用的 `ore:` 矿辞写法与 `@meta` 后缀，也未核实是否支持 `count` 字段。
4. **旧版 `ItemRequirementData`/`EnergyRequirementData` 是否被反射或外部代码调用未核实**。我只在 `[旧]src` 全树 grep 到定义处（2 个匹配），不能排除编译产物、第三方模组或未入库代码的引用。
5. **旧版 `RequirementTypeIngredientArray` 的数量取值疑似 bug，未核实是否为预期**。`RequirementTypeIngredientArray.java:79-84` 在循环内读取的是**外层** `jsonObject` 的 `amount` 而非子项 `subItem` 的 `amount`，导致数组内每个元素共用同一数量。
6. **旧版 `default_recipes` 目录的用途边界未核实**。我只确认这些 JSON 存在于源码树，未核实它们是随 jar 分发并被自动加载，还是仅作示例/首次运行模板（未读取其加载入口代码）。
7. **旧版适配器总数是否只有 12 个未完全排除**。`RegistryRecipeAdapters.java:41-62` 内联注册 12 个具体适配器（`AdapterNCOMachine` 为抽象类，不计数）；我未核实 `RegistriesMM.ADAPTER_REGISTRY` 的冻结/后置注册路径或 CraftTweaker `RecipeAdapterBuilder` 是否存在额外内置适配器。
8. **新版注册名一致性未核实**。`ModRecipeSerializers.java:17` 以 `"machine"` 注册序列化器，而 `ModularMachineryKubeJSPlugin.java:13` 使用 `modular_machinery_reborn:machine` 做 `mapRecipe`；两者是否指向同一注册项、KubeJS schema 是否能真正写出配方，我未通过运行验证。
9. **新版 `machine` 字段为 JSON 数组时的行为未核实**。`ModRecipeSerializers.java:20` 调用 `getAsString()`，Gson 在元素为数组时的具体异常/取值语义我未实测（结论 D3/N14 中「数组形式未迁移」基于该调用本身不含数组分支推理）。
10. **未核实新旧两侧是否还有其他配方入口**。例如旧版 `PreparedRecipe` 的脚本预置路径、新版可能的硬编码配方或 JEI 隐藏配方，我只读了任务指定范围内的文件。
