# 资源、语言与默认内容

审计范围：
- 旧版根：`C:\Users\kk07H\Desktop\模组\_mmce-src\ModularMachinery-Community-Edition-master\src\main\resources\`（命名空间 `modularmachinery`、`modularmagic`）
- 新版根：`C:\Users\kk07H\Desktop\模组\modular-machinery-reborn\src\main\resources\`（命名空间 `modular_machinery_reborn`）

计数口径（本次亲自统计）：
| 项目 | 旧版 | 新版 |
|---|---|---|
| `lang/en_US.lang` / `en_us.json` key 数 | **397** | **65** |
| `lang/zh_CN.lang` / `zh_cn.json` key 数 | **397** | **65** |
| 旧版 `.name` 结尾显示名 key 数 | **113** | — |
| `textures/blocks`（旧）/ `textures/block`（新）PNG | **98**（modularmachinery）+ **21**（modularmagic） | **62** |
| `textures/items`（旧）/ `textures/item`（新）PNG | **5** | **5** |
| `textures/gui` PNG | **23**（modularmachinery）+ **1**（modularmagic） | **1** |
| blockstates 文件 | 40 | 52 |
| 数据包 recipe | 16（`recipes/`）+ 9（`default_recipes/`） | 1 |

新版命名空间与 modid 一致：`ModularMachineryReborn.java:18` → `MOD_ID = "modular_machinery_reborn"`，因此新版 lang/贴图路径解析正确。

---

## 一、已迁移

1. **核心物品的 4 个显示名 + 创造模式标签页已迁移（语义等价，仅命名空间与文案措辞不同）**
   - 旧版证据：`assets/modularmachinery/lang/en_US.lang:1`（`itemGroup.modularmachinery`）、`:307`（`item.modularmachinery.itemblueprint.name=Machine Blueprint`）、`:308`（`itemmodularium`）、`:309`（`itemconstructtool.name=Construct Selection Tool`）
   - 新版证据：`assets/modular_machinery_reborn/lang/en_us.json` 的 `itemGroup.modular_machinery_reborn`、`item.modular_machinery_reborn.itemblueprint`、`item.modular_machinery_reborn.itemmodularium`、`item.modular_machinery_reborn.itemconstructtool`；注册处 `java/com/reborn/modularmachinery/item/ModItems.java:12-15`
   - 备注：`itemconstructtool` 文案由 "Construct Selection Tool" 改为 "Construction Tool"，属措辞差异而非缺失。

2. **机器控制器显示名已迁移**
   - 旧版证据：`assets/modularmachinery/lang/en_US.lang:314`（`tile.modularmachinery.blockcontroller.name=Machine Controller`）
   - 新版证据：`lang/en_us.json` → `block.modular_machinery_reborn.machine_controller` = "Machine Controller"；注册 `java/.../block/ModBlocks.java:23`

3. **物品输入/输出总线 7 个等级全部迁移为 `item_input_hatch_*` / `item_output_hatch_*` 显示名**
   - 旧版证据：`lang/en_US.lang:333-339`（`blockinputbus.tiny/small/normal/reinforced/big/huge/ludicrous`）、`:341-347`（`blockoutputbus.*`，同 7 级）
   - 新版证据：`lang/en_us.json` → `block.modular_machinery_reborn.item_input_hatch_{tiny,small,normal,reinforced,big,huge,ultimate,ludicrous}`（8 条）与 `item_output_hatch_{...}`（8 条）；注册 `ModBlocks.java:29`（`TIERS` 数组）

4. **能源输入/输出仓 8 个等级（含 ultimate）显示名已迁移**
   - 旧版证据：`lang/en_US.lang` `tile.modularmachinery.blockenergyinputhatch.{tiny..ludicrous}.name` 与 `...ultimate.name=Ultimate Energy Input Hatch`（已逐条核对存在，en_US 与 zh_CN 各 8 条）；`zh_CN.lang:373` = `tile.modularmachinery.blockenergyinputhatch.ultimate.name=终极能源输入仓`
   - 新版证据：`lang/en_us.json` → `energy_input_hatch_{tiny..ultimate,ludicrous}`、`energy_output_hatch_{...}` 各 8 条；注册 `ModBlocks.java:32-37`

5. **流体输入/输出仓 8 个等级（含 vacuum）显示名已迁移**
   - 旧版证据：`lang/en_US.lang` `tile.modularmachinery.blockfluidinputhatch.{tiny..ludicrous,vacuum}.name`、`blockfluidoutputhatch.{...}.name`
   - 新版证据：`lang/en_us.json` → `fluid_input_hatch_{tiny..ludicrous,vacuum}`、`fluid_output_hatch_{...}` 各 8 条；注册 `ModBlocks.java:30`（`FLUID_TIERS`，含 `vacuum`）

6. **`zh_cn.json` 与 `en_us.json` 的 key 集合完全一致（65 = 65，差集为 0）**，即旧版 en/zh 双份 397 key 的“双文件对齐”惯例在新版被保留
   - 旧版证据：`lang/en_US.lang`（397 key）与 `lang/zh_CN.lang`（397 key）逐 key 对齐
   - 新版证据：`lang/en_us.json` 与 `lang/zh_cn.json` 各 65 key，`Compare-Object` 得 0 差异

7. **5 个 item 贴图全部迁移并改名到 `textures/item/`（旧版 `textures/items/` 复数 → 新版单数）**
   - 旧版证据：`assets/modularmachinery/textures/items/` 下 5 个 PNG：`blueprint.png`、`machine_projector.png`、`modularium.png`、`redstonesignal.png`、`wrench.png`
   - 新版证据：`assets/modular_machinery_reborn/textures/item/` 下 5 个 PNG：`itemblueprint.png`、`machine_projector.png`、`itemmodularium.png`、`redstonesignal.png`、`wrench.png`
   - 对应模型：`models/item/itemblueprint.json:1`（`item/itemblueprint`）、`models/item/itemmodularium.json:1`、`models/item/machine_projector.json:1`、`models/item/redstonesignal.json:1`、`models/item/itemconstructtool.json:1`+`models/item/wrench.json:1`（二者同指向 `item/wrench`）
   - 旧版对应模型：`models/item/itemblueprint.json:4`（`modularmachinery:items/blueprint`）、`models/item/itemmodularium.json:4`、`models/item/machineprojector.json:4`、`models/item/itemconstructtool.json:4`

8. **控制器方块模型与朝向 blockstate 已迁移（旧 `blockcontroller` overlay 模型 → 新 `machine_controller` 六面模型）**
   - 旧版证据：`assets/modularmachinery/models/block/blockcontroller.json:5-6`（`ov_top`/`ov_side` 用 `blocks/overlay_transparent`）
   - 新版证据：`models/block/machine_controller.json:4-11`（六面 `legacy_controller_*`）；`blockstates/machine_controller.json:1` 提供 `facing=north/south/west/east` 四朝向变体（对应旧版 `BlockController.FACING` 属性）

9. **40 个 blockstate 中与已注册方块对应的 52 个新版 blockstate 全部齐全，且每个都指向存在的模型文件**
   - 旧版证据：`assets/modularmachinery/blockstates/` 40 个 json（含 `blockinputbus.json`、`blockoutputbus.json`、`blockenergyinputhatch.json` 等）
   - 新版证据：`assets/modular_machinery_reborn/blockstates/` 52 个 json；`machine_controller.json` 带 `facing` 变体，其余为单变体

10. **新数据包 recipe 序列化器可读取旧版风味字段（machine/duration/energy/input/output）**
    - 旧版证据：`assets/modularmachinery/recipes/modularium_ingot.json`（Forge `forge:ore_shaped` 工作台配方）、`assets/modularmachinery/default_recipes/alloy_smelter/alloy_smelter_diamond.json:1-8`（`machine`=alloy_furnace、`recipeTime`:5、`requirements[].type`=energy/item、`io-type`、`energyPerTick`)
    - 新版证据：`java/.../recipe/ModRecipeSerializers.java:19-27`（读 `machine`/`input`/`output`/`duration`/`energy`）、`data/modular_machinery_reborn/recipes/iron_to_gold.json:1`
    - 判定依据：新版保留了「机器名 + 时长 + 能量 + 输入/输出」这一层语义，但字段名与结构已改写，详见「四、设计差异」第 2 条。

---

## 二、部分迁移

1. **旧版 `assets/modularmachinery/default_machinery/` 的 4 个机器定义：只迁移了“机器概念”，未迁移任何结构数据**
   - 旧版证据（4 个文件全部存在）：
     - `assets/modularmachinery/default_machinery/alloy_furnace.json`（`"registryname": "alloy_furnace"`, `"localizedname": "Alloy Smelter"`, `modifiers` + 数十个 `parts`）
     - `assets/modularmachinery/default_machinery/assembly_line.json`（`dynamic-patterns`、`selector-tag`、`structure-size-offset`）
     - `assets/modularmachinery/default_machinery/iron_centrifuge.json`（`localizedname: Iron Reinforced Centrifuge`，引用变量 `casings_all`）
     - `assets/modularmachinery/default_machinery/power_transformer.json`（`"registryname": "transformer"`）
   - 新版证据：全仓库（排除 `build/`、`.gradle/`）搜索 `registryname` / `localizedname` / `dynamic-patterns` / `requires-blueprint` 命中数为 **0**；`data/` 目录只有 `modular_machinery_reborn/recipes/iron_to_gold.json` 一个文件
   - 缺什么：新版的「结构」是硬编码的 8 方块铁环，见 `java/.../block/MachineStructure.java:10-22`（`isFormed` 只检查控制器同层 `dx,dz ∈ [-1,1]` 的 8 个方块是否为 `Blocks.IRON_BLOCK` 或 4 种基础仓）；旧版的 `parts`/`dynamic-patterns`/`modifiers`/自定义 `localizedname` 全部没有对应物。

2. **旧版 `assets/modularmachinery/default_variables/` 的变量系统：文件未迁移，仅“变量”这一概念在新版不存在**
   - 旧版证据：`assets/modularmachinery/default_variables/casings.var.json`（定义 `casings_all` / `casings_decorative` / `casings_fluid` / `casings_energy` / `casings_item` 五组变量，值形如 `modularmachinery:blockcasing`、`modularmachinery:blockinputbus`）
   - 新版证据：新版 `resources/` 下不存在 `default_variables` 或任何 `*.var.json`；`MachineStructure.java:19-21` 用的是 Java 硬编码的 4 个 `ModBlocks` 常量，等价于一个写死的 `casings_all` 子集，但**无法被 JSON 覆盖/扩展**
   - 缺什么：变量定义文件、变量名到方块集合的映射与运行时可配置性。

3. **旧版 `assets/modularmachinery/default_recipes/` 的 9 个机器配方：结构上“部分保留”，内容 0 迁移**
   - 旧版证据（9 个文件全部存在）：`default_recipes/power_transformer_energy_transform.json`、`default_recipes/alloy_smelter/alloy_smelter_diamond.json`、`alloy_smelter/alloy_smelter_furnaces.adapter.json`、`alloy_smelter/alloy_smelter_modularium.json`、`centrifuge/centrifuge_centrifuge_blaze_powder.json`、`centrifuge/centrifuge_centrifuge_grass.json`、`centrifuge/centrifuge_centrifuge_magma_cream.json`、`centrifuge/centrifuge_centrifuge_wool.json`、`centrifuge/centrifuge_wash_glowstone.json`、`centrifuge/centrifuge_wash_redstone.json`
   - 新版证据：`data/modular_machinery_reborn/recipes/` 只有一个 `iron_to_gold.json`（`{"type":"modular_machinery_reborn:machine","machine":"basic","input":{...iron_ingot},"output":{...gold_ingot,...},"duration":80,"energy":400}`），且 `machine` 值为 `"basic"`，**与旧版 4 台机器（alloy_furnace / transformer / iron_centrifuge / assembly_line）无一对应**
   - 缺什么：多输入/多输出、`chance`、`ore:` 矿辞、`recipeTime`/`io-type`/`requirements[]`、`adapter` 与 `modifiers`。

4. **旧版 98 个 `textures/blocks` 贴图：约 54 个被“改名复用”，44 个彻底未迁移（其中 34 个属于依赖模组专用仓）**
   - 改名复用示例（旧→新，语义等价，仅去掉 `overlay_`/`block` 前缀并按新命名空间重排）：
     - `textures/blocks/overlay_energyinputhatch_ultimate.png` → `textures/block/energy_input_hatch_ultimate.png`（模型 `models/block/energy_input_hatch_ultimate.json:1`）
     - `textures/blocks/overlay_inputbus_normal.png` → `textures/block/item_input_hatch_normal.png`（模型 `models/block/item_input_hatch_normal.json:1`）
     - `textures/blocks/blockcasing_plain.png` → `textures/block/legacy_casing_plain.png`
   - 未迁移的（旧版有、新版 `textures/block/` 无同名等价贴图）：全部 34 个 `overlay_*provider*` / `overlay_me*` / `overlay_parallel_controller_*` / `overlay_upgrade_bus_*` / `overlay_smartinterface_number` / `overlay_circuitry` / `overlay_firebox` / `overlay_gearbox` / `overlay_reinforced` / `overlay_vent` / `overlay_factory_controller` / `overlay_redstone_tiny` / `aspectprovider` / `impetus_hatch` / `lifeessenceprovider` / `orb1-8` / `orbnova` / `crushing_wheels_idle` / `crushing_wheels_working` / `overlay_transparent`（`overlay_transparent` 的“值”被 `legacy_transparent.png` 承接，但 `legacy_transparent.png` 在**新版中未被任何模型/Java 引用**，见「四、设计差异」第 5 条）

5. **旧版 `modularmachinery`/`modularmagic` 的 24 个 `textures/gui` 贴图：新版只用 1 个自造 GUI 贴图，其余 23 个未迁移，且新版 GUI 改为程序化绘制**
   - 旧版证据（Java 逐个引用，共 19 处）：`java/hellfirepvp/modularmachinery/client/gui/GuiMachineController.java:38`（`textures/gui/guicontroller_large.png`）、`GuiContainerItemBus.java:34`（`textures/gui/inventory_<size>.png`）、`GuiContainerEnergyHatch.java:36` + `GuiContainerFluidHatch.java:44`（`textures/gui/guibar.png`）、`GuiFactoryController.java:31-32`（`guifactory.png`、`guifactoryelements.png`）、`GuiScreenBlueprint.java:31`（`guiblueprint_new.png`）、`GuiContainerUpgradeBus.java:26`（`guiupgradebus.png`）、`GuiContainerBase.java:26`（`guismartinterface.png`）、`RecipeLayoutHelper.java:25`（`jeirecipeicons_ce.png`）、`kport/modularmagic/.../ManaRenderer.java:35`（`textures/gui/widgets.png`）等
   - 新版证据：`textures/gui/` 仅有 `.keep` 与 `controller_legacy.png`（共 1 个 PNG）；`MachineControllerScreen.java` 全文无 `ResourceLocation`/`bindTexture`，背景由 `g.fill(...)` 颜色块绘制（`PANEL`/`PANEL_EDGE`/`SLOT`/`GREEN`/`RED` 常量）
   - 缺什么：全部 23 个 GUI 图集，以及依赖它们的 JEI 图标 `jeirecipeicons_ce.png`。

6. **旧版 `assets/modularmachinery/recipes/` 的 16 个工作台配方：新版只有 1 个数据包配方，工作台配方 0 迁移**
   - 旧版证据（16 个文件）：`recipes/casing_plain.json`、`casing_firebox.json`、`casing_reinforced.json`、`controller.json`、`modularium_ingot.json`、`energy_input_tiny.json`、`energy_input_small.json`、`energy_output_tiny.json`、`energy_output_small.json`、`fluid_input_tiny.json`、`fluid_input_small.json`、`fluid_output_tiny.json`、`fluid_output_small.json`、`item_input_tiny.json`、`item_input_small.json`、`item_output_tiny.json`、`item_output_small.json`
   - 新版证据：`data/modular_machinery_reborn/recipes/` 只有 `iron_to_gold.json`，其 `type` 为 `modular_machinery_reborn:machine`（机器配方，不是 `minecraft:crafting_shaped`）；新项目内不存在 `minecraft:crafting_shaped` 或 `forge:ore_shaped` 类型的 json
   - 缺什么：全部合成途径——新版 `ModBlocks.java:43-52` 只是把它们塞进创造模式标签页，玩家无法通过合成获得控制器/外壳/仓。

7. **旧版 `modularmagic`（34 个贴图 + 18 个 blockstate + 2 个 lang）代表的依赖模组集成仓：新版仅有“不存在对应实现”的贴图缺口，未做任何替代**
   - 旧版证据：`assets/modularmagic/blockstates/` 18 个 json（`blockaspectproviderinput.json`、`blockauraproviderinput.json`、`blockconstellationprovider.json`、`blockgridproviderinput.json`、`blockimpetusproviderinput.json`、`blocklifeessenceproviderinput.json`、`blockmanaproviderinput.json`、`blockrainbowprovider.json`、`blockstarlightproviderinput.json`、`blockwillproviderinput.json` 及其 output/对称项）；`assets/modularmagic/textures/blocks/` 21 个 PNG；`assets/modularmagic/lang/en_US.lang`（49 key）/`zh_CN.lang`（44 key）
   - 旧版 Java 注册证据：`java/hellfirepvp/modularmachinery/common/registry/RegistryBlocks.java:243-305`（`Mods.BM2` / `Mods.TC6` / `Mods.TA` / `Mods.EXU2` / `Mods.ASTRAL_SORCERY` / `Mods.NATURESAURA` / `Mods.BOTANIA` 条件下注册 will/lifeessence/aspect/impetus/grid/rainbow/starlight/constellation/aura/mana 共 15 个仓）
   - 新版证据：`ModBlocks.java` 全文只注册 `machine_controller` + 4 个基础仓 + 8×4 tiered + 8×2 fluid，**无任何 provider/魔法类仓**；`lang/en_us.json`/`zh_cn.json` 无对应 key

---

## 三、未迁移

> 分类口径：新版完全不存在对应实现／文件。

1. **旧版 `assets/modularmagic/` 整个命名空间（18 blockstate + 34 模型 + 21 block 贴图 + 1 gui 贴图 + 2 lang 文件）新版完全不存在**
   - 旧版证据：`assets/modularmagic/blockstates/`（18 json）、`assets/modularmagic/models/block/`（19 json）、`assets/modularmagic/models/item/`（18 json）、`assets/modularmagic/textures/blocks/`（21 PNG）、`assets/modularmagic/textures/gui/widgets.png`、`assets/modularmagic/lang/en_US.lang`、`assets/modularmagic/lang/zh_CN.lang`
   - 新版证据：`resources/assets/` 下只有 `modular_machinery_reborn/` 一个命名空间目录

2. **旧版 `assets/modularmachinery/lang/*.lang` 的 397 个 key 中，**344 个**在新版 65 key 中完全没有对应项**
   - 旧版证据：`lang/en_US.lang`（397 key，465 行）、`lang/zh_CN.lang`（397 key，463 行）
   - 新版证据：`lang/en_us.json`（65 key）、`lang/zh_cn.json`（65 key）
   - 其中 **113 个 `.name` 显示名 key 里有 60 个没有新版对应**，代表性缺失项：
     - `tile.modularmachinery.blockfactorycontroller.name`（`lang/en_US.lang:319`）/ `tile.modularmachinery.machinefactorycontroller.name`（`:324`）：**工厂控制器整条产品线未迁移**
     - `tile.modularmachinery.machinecontroller.name`（`:315`，`%s Controller`）、`tile.modularmachinery.machinecontroller.deprecated.tip.0/1`（`:316-317`）：动态控制器名与弃用提示未迁移
     - `tile.modularmachinery.blockcasing.{plain,vent,firebox,gearbox,reinforced,circuitry}.name`（`lang/en_US.lang:326-331`；zh 对应 `zh_CN.lang:325` `机械外壳` 等）：**6 种机器外壳全部未迁移**，新版无 `blockcasing` 方块注册
     - `tile.modularmachinery.blocksmartinterface.{number,string}.name`（`:385-386`）、`tile.modularmachinery.blockparallelcontroller.{normal,reinforced,elite,super,ultimate}.name`（`:388-392`）、`tile.modularmachinery.blockupgradebus.{normal,reinforced,elite,super,ultimate}.name`（`:395-399`）：智能接口 / 并行控制器 / 升级总线共 12 个显示名未迁移
     - `tile.modularmachinery.blockme{item,fluid,gas}{input,output}bus.name`（`:402-407`）、`tile.modularmachinery.blockmepatternprovider.name`（`:408`）、`tile.modularmachinery.blockmepatternmirrorimage.name`（`:409`）：AE2/ME 集成 8 个显示名未迁移
     - `block.modularmachinery.crushing_wheels.name`（`lang/en_US.lang:312`）= `粉碎轮`（`zh_CN.lang:312`）：**`crushing_wheels` 方块未迁移**（旧版有 `blockstates/crushing_wheels.json:3-8`、`models/block/crushing_wheels_idle.json`、`models/block/crushing_wheels_working.json`、`textures/blocks/crushing_wheels_idle.png`、`crushing_wheels_working.png` 及其 `.mcmeta`）
     - 依赖模组仓的 17 个显示名（`lang/en_US.lang:445-462` 的 will/lifeessence/aspect/grid/starlight/constellation/aura/rainbow/mana/impetus 系列）
   - 另：全部 397 个 key 中剩余 284 个为 GUI/命令/消息/tooltip/错误/信息类 key（如 `command.modularmachinery.*` 11 条、`gui.controller.status.*` 7 条、`gui.preview.*` 33 条、`message.assembly.tip.*` 12 条、`tooltip.*` 约 80 条、`error.modularmachinery.*` 25 条），新版**一条都没有**——新版只有 `gui.modular_machinery_reborn.formed`、`jei.modular_machinery_reborn.machine.title`、`block.modular_machinery_reborn.hatch.port`、`block.modular_machinery_reborn.machine_controller.no_recipe`、`block.modular_machinery_reborn.machine_controller.completed` 共 5 条 GUI/提示类 key。

3. **旧版 16 个 `recipes/*.json` 工作台配方 100% 未迁移**（清单元数据见「二、部分迁移」第 6 条，此处不再重复逐条列举；证据目录：`assets/modularmachinery/recipes/`）

4. **旧版 9 个 `default_recipes/*.json` 机器配方 100% 未迁移**（清单见「二、部分迁移」第 3 条）

5. **旧版 `default_machinery/*.json` 4 个 + `default_variables/*.json` 1 个文件：0 个文件被复制到新版**，且新版没有任何 loader 代码会去读 `default_machinery`/`default_recipes`/`default_variables` 目录
   - 旧版证据：旧版有专门的加载入口 `java/hellfirepvp/modularmachinery/common/data/ModDataHolder.java:72`（`copy("default_machinery", machineryDir)`）、`:73`（`copy("default_recipes", recipeDir)`）、`:79`（`copy("default_variables", defaultVariableDir)`）
   - 新版证据：新版 java 全文搜索 `default_machinery` / `default_recipes` / `default_variables` 命中数为 **0**

6. **旧版 40 个 blockstate 中，未迁移方块对应的约 28 个 blockstate 文件新版不存在**（属于上述未迁移方块的产品线）
   - 旧版证据：`assets/modularmachinery/blockstates/` 中 `blockfactorycontroller.json`、`blockcontroller.json`、`blockcasing.json`、`blocksmartinterface.json`、`blockparallelcontroller.json`、`blockupgradebus.json`、`blockmeiteminputbus.json`、`blockmeitemoutputbus.json`、`blockmefluidinputbus.json`、`blockmefluidoutputbus.json`、`blockmegasinputbus.json`、`blockmegasoutputbus.json`、`blockmepatternprovider.json`、`blockmepatternmirrorimage.json`、`crushing_wheels.json`、`blockaspectproviderinput/output.json`、`blockauraproviderinput/output.json`、`blockconstellationprovider.json`、`blockgridproviderinput/output.json`、`blockimpetusproviderinput/output.json`、`blocklifeessenceproviderinput/output.json`、`blockmanaproviderinput/output.json`、`blockrainbowprovider.json`、`blockstarlightproviderinput/output.json`、`blockwillproviderinput/output.json`
   - 新版证据：`assets/modular_machinery_reborn/blockstates/` 无这些文件名

7. **旧版 `textures/blocks/crushing_wheels_working.png.mcmeta`、`overlay_parallel_controller_*.png.mcmeta`（5 个）、`overlay_smartinterface_number.png.mcmeta` 共 7 个动画/元数据文件未迁移**
   - 旧版证据：`assets/modularmachinery/textures/blocks/` 下 7 个 `.mcmeta`（`crushing_wheels_working.png.mcmeta`、`overlay_parallel_controller_elite.png.mcmeta`、`..._normal.png.mcmeta`、`..._reinforced.png.mcmeta`、`..._super.png.mcmeta`、`..._ultimate.png.mcmeta`、`overlay_smartinterface_number.png.mcmeta`）
   - 新版证据：`assets/modular_machinery_reborn/textures/block/` 与 `textures/item/`、`textures/gui/` 下 **0 个 `.mcmeta` 文件**（已用目录枚举确认）

8. **旧版 `textures/logo.png`（`mcmod.info` 的 `logoFile`）未迁移**
   - 旧版证据：`assets/modularmachinery/textures/logo.png`
   - 新版证据：`resources/` 下无 `logo.png`，`META-INF/mods.toml` 也无 `logoFile` 字段

---

## 四、设计差异

1. **贴图命名策略整体改写：旧版是「底色 + overlay 两层拼装」，新版改为「单张合成贴图」**
   - 旧版证据：`models/block/blockenergyinputhatch_ultimate.json:2-6` 用 `parent: modularmachinery:block/blockmodel_overlay_all`，纹理槽为 `bg_all: blocks/blockcasing_plain` + `ov_all: blocks/overlay_energyinputhatch_ultimate`（两层）；`models/block/blockinputbus_normal.json` 同结构
   - 新版证据：`models/block/energy_input_hatch_ultimate.json:1` 为 `{"parent":"minecraft:block/cube_all","textures":{"all":"...:block/energy_input_hatch_ultimate"}}`（单层，全部 52 个 hatch blockstate 同理）
   - 影响：旧版“换 overlay 即可换等级外观 / 用 `overlay_transparent` 表示无 overlay”的可组合机制在新版不存在。

2. **机器配方数据格式从 MMCE 专有 JSON 改成原版 Recipe 体系**
   - 旧版证据：`default_recipes/alloy_smelter/alloy_smelter_diamond.json`（`machine`/`registryName`/`recipeTime`/`requirements[]`，其中 `"type": "modularmachinery:item"`、`"io-type"`、`"item": "ore:ingotIron"` 矿辞、`"chance"`）
   - 新版证据：`ModRecipeSerializers.java:19-27` 只读 5 个字段（`machine`/`input`/`output`/`duration`/`energy`）+ `data/.../iron_to_gold.json`；`ModRecipeTypes.java:11` 注册 `RecipeType` `modular_machinery_reborn:machine`
   - 缺失语义：`requirements[]` 多元素数组、`chance` 概率、`ore:` 矿辞、`io-type` 输入/输出标记、`adapter`（把原版工作台配方适配成机器配方，见 `default_recipes/alloy_smelter/alloy_smelter_furnaces.adapter.json`）、`modifiers`（乘算修正）。

3. **GUI 语义变化：旧版 23 张图集皮肤化 GUI → 新版纯色块程序化 GUI**
   - 旧版证据：`GuiContainerItemBus.java:34` 返回 `textures/gui/inventory_<size>.png`；`GuiMachineController.java:38` 返回 `textures/gui/guicontroller_large.png`
   - 新版证据：`MachineControllerScreen.java` 全文仅用 `g.fill(...)` 与硬编码 ARGB 常量绘制；`textures/gui/` 只剩 `controller_legacy.png`（且**新项目中无任何引用**）
   - 结论：`controller_legacy.png` 是新版自造（旧版无此文件名），迁移了“控制器 GUI”这一概念，但采用了全新实现，旧图集不可复用。

4. **新版自造 `legacy_*` 贴图命名族（旧版无此后缀）**
   - 新版证据：`textures/block/` 下 16 个 `legacy_*.png`（`legacy_casing_plain`、`legacy_controller`、`legacy_controller_combined`、`legacy_controller_front`、`legacy_controller_side`、`legacy_controller_top`、`legacy_energy_input_hatch`、`legacy_energy_input_hatch_combined`、`legacy_fluid_input_hatch`、`legacy_fluid_input_hatch_combined`、`legacy_item_input_hatch`、`legacy_item_input_hatch_combined`、`legacy_item_output_hatch`、`legacy_item_output_hatch_combined`、`legacy_transparent`）
   - 旧版证据：`textures/blocks/` 中无任何以 `legacy` 开头的文件
   - 其中 **8 个从未被任何模型/Java 引用**（死资源）：`legacy_casing_plain`、`legacy_controller`、`legacy_controller_combined`、`legacy_energy_input_hatch`、`legacy_fluid_input_hatch`、`legacy_item_input_hatch`、`legacy_item_output_hatch`、`legacy_transparent`
   - 实际被引用的 5 个：`legacy_item_input_hatch_combined`（`models/block/item_input_hatch.json:1`）、`legacy_item_output_hatch_combined`（`models/block/item_output_hatch.json:1`）、`legacy_energy_input_hatch_combined`（`models/block/energy_input_hatch.json:1`）、`legacy_fluid_input_hatch_combined`（`models/block/fluid_input_hatch.json:1`）、`legacy_controller_side/top/front`（`models/block/machine_controller.json:4-10`）
   - 另：`models/block/overlay_all.json`、`models/block/overlay_controller.json`、`models/block/overlay_base.json` 三个 overlay 模型**在新版中被定义但无任何 blockstate 引用**（`models/block/overlay_all.json:2` 只被自身族引用，`blockstates/*.json` 无一指向 `overlay_*`）。

5. **旧版“能量/流体输出仓”的对称性在新版错位：`energy_output_hatch` 基座缺失、`fluid_output_hatch` 基座缺失，而 `item_*_hatch` 有基座**
   - 旧版证据：`lang/en_US.lang` 中 energy/fluid/item 的输入输出仓都是同一套 `block*hatch` 注册 + metadata 区分；`blockstates/blockenergyoutputhatch.json`、`blockstates/blockfluidoutputhatch.json` 均存在
   - 新版证据：`ModBlocks.java:24-27` 注册了 `ITEM_INPUT_HATCH`/`ITEM_OUTPUT_HATCH`/`ENERGY_INPUT_HATCH`/`FLUID_INPUT_HATCH` 四个基座（**没有 `energy_output_hatch` 基座、没有 `fluid_output_hatch` 基座**），但 `blockstates/energy_output_hatch_*.json`、`fluid_output_hatch_*.json` 的等级变体却存在（各 8 个）
   - 对应 lang：`block.modular_machinery_reborn.item_input_hatch`、`item_output_hatch`、`energy_input_hatch`、`fluid_input_hatch` 四条基座显示名存在，无 `energy_output_hatch`/`fluid_output_hatch` 基座显示名。

6. **旧有 Java 注册、新版缺贴图/模型：`ultimate` 等级物品总线**
   - 旧版**没有** `blockinputbus.ultimate` / `blockoutputbus.ultimate`：`lang/en_US.lang:333-339` 只有 7 个等级（tiny/small/normal/reinforced/big/huge/ludicrous），无 ultimate；`textures/blocks/` 只有 `overlay_inputbus_{7 级}.png` 与 `overlay_outputbus_{7 级}.png`，**没有** `overlay_inputbus_ultimate.png` / `overlay_outputbus_ultimate.png`
   - 但旧版 **`overlay_energyinputhatch_ultimate.png` 与 `overlay_energyoutputhatch_ultimate.png` 都存在**，且被 `models/block/blockenergyinputhatch_ultimate.json:5` / `models/block/blockenergyoutputhatch_ultimate.json:5` 引用；lang 也有 `tile.modularmachinery.blockenergyinputhatch.ultimate.name=Ultimate Energy Input Hatch`（zh: `zh_CN.lang:373` `终极能源输入仓`）
   - 新版证据：`ModBlocks.java:29` 的 `TIERS` 数组对**所有** hatch 类型（含 item 输入/输出）都生成 `ultimate` 等级，`lang/en_us.json` 有 `item_input_hatch_ultimate` / `item_output_hatch_ultimate`，且 `textures/block/item_input_hatch_ultimate.png`、`item_output_hatch_ultimate.png`、`models/block/item_input_hatch_ultimate.json`、`models/block/item_output_hatch_ultimate.json` 均存在
   - 结论：旧版 `overlay_inputbus_*`/`overlay_outputbus_*` 确实**缺 ultimate 贴图（旧版本身就是 7 级体系）**，而 `overlay_energyinputhatch_ultimate.png` **存在**；新版反过来把 ultimate 补齐到 8 级体系，是**新增等级**而非“补迁移旧贴图”。

7. **旧版 `textures/items/redstonesignal.png` 是死资源：旧版 Java 无注册、模型无引用**
   - 旧版证据（三路核查全部为空）：
     - 全仓库（`.java`/`.json`/`.lang`/`.mcmeta`/`.kt`/`.txt`/`.xml`）搜索 `redstonesignal` / `redstone_signal` → **0 命中**
     - `textures/items/` 目录确实存在 `redstonesignal.png`（5 个 PNG 之一）
     - `RegistryItems.java:43-52` 注册的物品只有 `ItemBlueprint`、`ItemModularium`、`ItemConstructTool`、`MachineProjector`（4 个），**无 redstone signal**
     - `lang` 中亦无任何 redstonesignal 显示名
   - 新版证据：`ModItems.java:16` 新增注册 `REDSTONE_SIGNAL = register("redstonesignal", 64)`，`models/item/redstonesignal.json:1` 引用 `item/redstonesignal`，`textures/item/redstonesignal.png` 存在，lang 有 `item.modular_machinery_reborn.redstonesignal` = "Redstone Signal"/"红石信号器"
   - 结论：新版把旧版的**死资源**激活成了正式物品（属于「旧有新无→新有旧无」的反向补齐）。

8. **旧版 `textures/items/wrench.png` 被模型引用，但旧版并无 wrench 物品；新版把它“转正”为独立物品**
   - 旧版证据：`models/item/itemconstructtool.json:4` → `"layer0": "modularmachinery:items/wrench"`（**唯一的引用**）
     - 全仓库其余 `wrench` 命中只有 1 处且是 Java 常量：`java/github/kasuminova/mmce/client/gui/GuiMEItemOutputBus.java:69`（`ActionItems.WRENCH`，是 GUI 图标枚举，不是物品注册）
     - `RegistryItems.java` 中不存在 wrench 物品
   - 新版证据：`models/item/itemconstructtool.json:1` 仍指向 `item/wrench`；同时 `ModItems.java:17` 新增 `WRENCH = register("wrench", 1)` + `models/item/wrench.json:1` + `textures/item/wrench.png` + lang `item.modular_machinery_reborn.wrench`
   - 结论：`wrench.png` **旧版确实被模型引用**（但通过 `itemconstructtool` 间接引用），新版同时保留旧引用并新增了独立 wrench 物品。

9. **旧版依赖模组条件注册（`Mods.AE2/BOTANIA/TC6/...`）+ 独立 `modularmagic` 命名空间 → 新版全部收敛为 4 类基础仓**
   - 旧版证据：`RegistryBlocks.java:219`（`if (Mods.AE2.isPresent())`）起至 `:305`（`if (Mods.BOTANIA.isPresent())`）
   - 新版证据：`ModBlocks.java` 全文无任何条件注册分支
   - 旧版 `assets/modularmagic/` 的模型文件用 `parent: modularmachinery:block/blockmodel_overlay_all` 但贴图指向 `modularmagic:blocks/overlay_*`（见 `assets/modularmagic/models/block/blockwillproviderinput.json:2-5`），说明旧版是「共享模型骨架 + 独立贴图命名空间」；新版没有这套分层。

10. **新版 `blockstates` 总量（52）超过旧版（40），多出的全部来自 tiered hatch（8×4 + 8×2 − 4 个基座对应的 12 个 tiered 变体），属于等级体系从 7 级扩到 8 级的自造扩充**

---

## 五、关键发现

1. **语言层几乎完全未迁移**：旧版 `modularmachinery` 的 en_US/zh_CN 各 397 个 key、`modularmagic` 各 49/44 个 key，新版合并后只有 65 个 key（en/zh 完全一致）；被迁移的只有 4 个物品 + 控制器 + 4 类仓的 56 个显示名，**344 个 key（含全部命令、tooltip、GUI 提示、错误消息、工厂控制器、6 种外壳、并行控制器、升级总线、AE2 集成）在新版不存在**。
2. **贴图层“名称重排 + 大面积丢弃”**：旧版 98 个 `textures/blocks` 中约 54 个以新命名（去 `overlay_`/加 `_combined`/`legacy_` 前缀）复活，其余 44 个未迁移；旧版 24 张 `textures/gui` 只剩 1 张自造的 `controller_legacy.png`，而新版 GUI 已改为 `g.fill()` 程序化绘制。旧版 `textures/items/` 5 张 PNG 全部迁移到 `textures/item/`。
3. **审查点已核实**：旧版 `overlay_inputbus_*` 与 `overlay_outputbus_*` 确实**缺 ultimate 贴图**（旧版本就是 7 级体系，`en_US.lang:333-339` 无 ultimate），而 `overlay_energyinputhatch_ultimate.png` **确实存在**并被 `models/block/blockenergyinputhatch_ultimate.json:5` 引用——新版反而把 ultimate 扩到全部 hatch（8 级），属新增而非补迁移。
4. **审查点已核实**：旧版 `textures/items/redstonesignal.png` 是**纯死资源**（全仓库 0 处 `redstonesignal` 命中、`RegistryItems.java` 未注册、无模型引用），新版却把它注册成正式物品；`textures/items/wrench.png` 旧版**被引用但仅一处**（`models/item/itemconstructtool.json:4`），新版同时保留该引用并新增独立 `wrench` 物品。
5. **默认内容（机器/配方/变量）实质未迁移**：旧版 `default_machinery`（4 个机器）、`default_recipes`（9 个机器配方）、`default_variables`（`casings.var.json` 5 组变量）、`recipes`（16 个工作台配方）共 30 个 JSON 一个都没进新版数据包；新版 `data/` 只有 1 个 `iron_to_gold.json`，其 `machine: "basic"` 与旧版 4 台机器无一对应，且旧版结构改用 `MachineStructure.java:10-22` 硬编码的 8 方块铁环。
6. **产物死资源与结构缺口并存**：新版自带 8 个未被引用的 `legacy_*.png`、3 个未被 blockstate 引用的 `overlay_*.json` 模型、1 张未被引用的 `controller_legacy.png`；同时 `ModBlocks.java:24-27` 缺少 `energy_output_hatch`/`fluid_output_hatch` 基座注册，而它们的 16 个等级 blockstate/贴图/lang key 却都已生成。

---

## 六、不确定项

1. 新版 `models/block/energy_input_hatch_big.json` 等 **52 个等级模型**中，`textures.all` 直接指向同名贴图；我**未逐一核对每个 PNG 的实际像素内容**（只核对了文件名存在性与引用路径），因此无法判断是否存在“文件在但内容为占位/纯色”的情况。
2. 我**未打开 PNG 二进制**核对 `itemblueprint.png`（19708 字节）与旧版 `blueprint.png`、`wrench.png`（20268 字节）与旧版 `wrench.png` 是否为同一素材；两者路径与文件名均已核实，但新旧像素一致与否未验证。
3. 旧版 `textures/blocks/` 中 44 个未迁移贴图里，`overlay_transparent` 与新 `legacy_transparent` 是否为同一张图，我**只核实了引用关系**（旧版被 `models/block/blockcasing_plain.json:5` 与 `blockcontroller.json:5-6`、`blockfactorycontroller.json:5-6` 引用；新版 `legacy_transparent` 未被任何文件引用），**未做像素比对**。
4. 旧版 `assets/modularmagic/lang/zh_CN.lang`（44 key）我**只统计了 key 数量**，未逐条读取内容与 en_US（49 key）做差集，因此不确定这 5 个 key 差在哪。
5. 旧版 `modularmagic` 是否为独立发布 mod：`src/main/resources/mcmod.info` 只声明 `modid: modularmachinery`（无 modularmagic 条目），而 Java 侧 `kport.modularmagic.*` 被主 mod 直接 import（如 `ModularMachinery.java:34-35`、`CommonProxy.java:163-165`），且 classpath 上是否存在独立 `modularmagic` modid 我**未从构建脚本（`build.gradle.kts`）核实**——因此在「二、部分迁移」第 7 条中我按“同 jar 内的内置集成模块”处理，若实际是独立 mod，该条的迁移动机判断需修正。
6. 我**未运行游戏或 Forge 数据包校验**，因此以下问题只是静态推断、未获运行时确认：新版是否真的能加载 `iron_to_gold.json`（`machine: "basic"` 无对应机器定义）、`energy_output_hatch`/`fluid_output_hatch` 无基座方块时 16 个等级 blockstate 是否只是无效资源、以及 `overlay_all.json`/`overlay_base.json`/`overlay_controller.json` 未被引用是否会导致模型加载告警。
7. 旧版 397 个 key 中「344 个新版无对应」的口径是**基于 key 名的机械比对 + 56 条人工配对表**（配对表见「一、已迁移」第 1-5 条的旧/新 key 对照）；其中 3 条配对我已判定为不成立（`blockinputbus.ultimate`/`blockoutputbus.ultimate`/`gui.controller.structure.found`——后者旧版实际 key 是 `gui.controller.structure`，见 `lang/en_US.lang:28`），但我**未对剩余 341 个 key 做逐条语义等价判断**，理论上可能存在我未识别出的「改名后语义等价」的 key。
8. 我**未核对旧版 `default_recipes/*.json` 的 `recipeTime` 单位与新版 `duration` 单位是否一致**（旧版 `alloy_smelter_diamond.json` 为 `5`，新版 `iron_to_gold.json` 为 `80`），因此「语义基本等价」的判断仅覆盖字段层面。
