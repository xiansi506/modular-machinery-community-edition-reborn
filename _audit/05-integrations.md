# 集成层（模组联动与脚本入口）

审计范围：旧版 1.12.2（Modular Machinery: Community Edition 2.3.2）的集成层目录 vs 新版 1.20.1 重实现工程 `modular-machinery-reborn`（17 个 Java 文件）。

约定：
- 旧版根 = `_mmce-src/ModularMachinery-Community-Edition-master`，下称「旧」。
- 新版根 = `modular-machinery-reborn`，下称「新」。
- 未迁移条目的「新版证据」为否定性证据：新版唯一的注册入口、依赖声明文件、以及对 `modular-machinery-reborn/src/main/java` 全树的关键字 grep 结果（下文各条注明关键字）。

---

## 一、已迁移

### M1. JEI 模组联动入口与基础配方展示
- **要点**：新版通过 `@JeiPlugin` 实现 `IModPlugin`，并额外用 `META-INF/services/mezz.jei.api.IModPlugin` 做服务发现；注册 1 个 `machine` 配方类别、把 `RecipeManager` 中所有 `modular_machinery_reborn:machine` 配方推入 JEI、并把机器控制器注册为催化剂。旧版同样以 `@JEIPlugin implements IModPlugin` 注册机器配方类别与控制器类催化剂。就「在 JEI 中可浏览机器配方、控制器可跳转」这一基础语义而言，两者等价。
- **旧版证据**：`_mmce-src/ModularMachinery-Community-Edition-master/src/main/java/hellfirepvp/modularmachinery/common/integration/ModIntegrationJEI.java:66-67`（`@JEIPlugin` + `IModPlugin`）、`:181-192`（`registerCategories`）、`:195-207`（控制器/蓝图催化剂）、`:221-233`（`addRecipes`）
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/client/jei/ModularMachineryJeiPlugin.java:16-24`、`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/client/jei/MachineRecipeCategory.java:20-38`、`modular-machinery-reborn/src/main/resources/META-INF/services/mezz.jei.api.IModPlugin:1`

---

## 二、部分迁移

### P1. 脚本入口（CraftTweaker → KubeJS）只有外壳
- **要点**：新版提供一个 KubeJS 插件类，但全部行为只有 `registerRecipeSchemas` 一个方法体：注册 `machine` 的 `JsonRecipeSchema` 并做一次 recipe 映射；配套示例脚本仅暴露 `machine / input / output / duration / energy` 五个字段（`ModRecipeSerializers.java:19-25` 的解析字段与之逐一对应）。旧版同名位置（外部脚本编写入口）暴露 22 个 `@ZenClass`：`MachineBuilder`、`RecipeBuilder`/`RecipePrimer`、`RecipeAdapterBuilder`、`RecipeModifierBuilder`、`BlockArrayBuilder`、`IngredientArrayBuilder`/`IngredientArrayPrimer`、`MachineModifier`、`MMEvents`、`MultiblockModifierBuilder`、`StatedMachineComponentBuilder`、`GeoMachineModel`、`AdvancedBlockChecker`、`AdvancedItemCheckerCT`、`AdvancedItemModifierCT`、`IFunction`、`UpgradeEventHandler(Wrapper)`、`MachineUpgradeBuilder`/`DynamicMachineUpgradeBuilder`/`MachineUpgradeHelper`、`RegistryUpgrade`、`SimpleMachineUpgrade` 等，覆盖机器结构、需求组件、配方事件、并行度、升级注册。新版没有任何对应 API。
- **缺什么**：机器注册/结构定义 API、需求组件（物品/流体/能量/气体）API、配方事件与回调、升级与能力注册、脚本热重载钩子。
- **旧版证据**：`.../common/integration/crafttweaker/MachineBuilder.java:48-49`、`RecipeBuilder.java:25-30`、`RecipePrimer.java:80-81`、`MachineModifier.java:21-22`、`event/MMEvents.java:37-38`、`upgrade/MachineUpgradeBuilder.java:37`、`upgrade/DynamicMachineUpgradeBuilder.java:35`、`upgrade/MachineUpgradeHelper.java:19`、`modifier/MultiBlockModifierBuilder.java:19-20`、`helper/UpgradeEventHandlerWrapper.java:11-12`、`github/kasuminova/mmce/common/upgrade/registry/RegistryUpgrade.java:17`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/kubejs/ModularMachineryKubeJSPlugin.java:9-15`（全文 16 行，仅此一个方法）、`kubejs-examples/server_scripts/machines.js:3-10`、`src/main/resources/META-INF/kubejs.plugins.txt:1`、`src/main/java/com/reborn/modularmachinery/recipe/ModRecipeSerializers.java:19-25`

---

## 三、未迁移

### N1. CraftTweaker 全量脚本 API
- **要点**：旧版以 CraftTweaker 4 为主体脚本层，仅集成目录内就有 16 个 `@ZenClass`，全工程共 63 处 `@ZenClass` 注解。新版对 `crafttweaker|CraftTweakerAPI|zenscript` 全树无匹配，`build.gradle` 无 CraftTweaker 依赖。
- **旧版证据**：`.../common/integration/crafttweaker/`（16 个 `@ZenClass`，见 P1 行号）；`.../ModularMachinery.java:61`（`required-after:crafttweaker@[4.0.4,)`）
- **新版证据**：`modular-machinery-reborn/build.gradle:28-37`；对 `modular-machinery-reborn/src/main/java` grep `crafttweaker|CraftTweakerAPI|zenscript` → 无匹配

### N2. 脚本/JSON 定义机器与实际结构
- **要点**：旧版 `MachineBuilder` 可从脚本注册机器（注册名、译名、是否工厂、失败动作、颜色、是否需蓝图）并逐格构建 `TaggedPositionBlockArray` 结构；`MachineLoader` 从 `.json` 文件读取 `DynamicMachine` 定义（支持 `.var.json` 变量文件）。新版机器结构为 Java 硬编码：控制器水平相邻一圈必须是铁块或四种端口方块，无数据文件、无脚本入口。
- **旧版证据**：`.../common/integration/crafttweaker/MachineBuilder.java:121-128`（`registerMachine`）、`:58-110`（四种构造器）、`.../common/machine/MachineLoader.java:86-87`、`:104-107`、`:132`、`:149-150`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/block/MachineStructure.java:10-22`（硬编码 3×3 判定）；`src/main/resources/data/modular_machinery_reborn/` 下只有 `recipes/iron_to_gold.json`，无机器定义 JSON

### N3. 脚本热重载命令与脚本生命周期事件
- **要点**：旧版注册 `/mmce` 系列重载命令（依赖 zenutils），并订阅 `ScriptRunEvent.Pre/Post`、`ScriptReloadEvent.Pre/Post` 完成配方清空、机器重载、升级清空、缓存重建、JEI wrapper 重载。新版无命令注册、无脚本生命周期钩子。
- **旧版证据**：`.../common/integration/ModIntegrationCrafttweaker.java:51-55`、`:57-60`、`:62-98`、`:100-141`；`.../common/integration/crafttweaker/command/CommandCTReload.java`、`command/CommandCTReloadClient.java`；`.../ModularMachinery.java:166-168`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/ModularMachineryReborn.java:22-35`（无命令/事件注册）

### N4. 机器升级体系与升级能力（Capability）
- **要点**：旧版有完整升级体系：`MachineUpgrade`/`SimpleMachineUpgrade`/`DynamicMachineUpgrade`/`SimpleDynamicMachineUpgrade`/`UpgradeType`/`registry.RegistryUpgrade`/`registry.UpgradeInfo`，配套 `CapabilityUpgrade` + `CapabilityUpgradeProvider`，并有三套 CraftTweaker 构建器。新版无任何升级相关代码。
- **旧版证据**：`.../github/kasuminova/mmce/common/upgrade/MachineUpgrade.java:20`、`SimpleMachineUpgrade.java:19`、`SimpleDynamicMachineUpgrade.java:20`、`registry/RegistryUpgrade.java:17-30`；`.../common/capability/CapabilityUpgrade.java`、`CapabilityUpgradeProvider.java`；`.../common/integration/crafttweaker/upgrade/MachineUpgradeBuilder.java:37`
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `upgrade|Upgrade`（作为集成面）无对应类；`ModularMachineryReborn.java:22-35` 注册列表仅方块/物品/配方/菜单

### N5. TheOneProbe 集成（两个 Provider + 进度条 + 配置）
- **要点**：旧版向 TOP 注册两个 provider：`MMInfoProvider`（控制器/工厂控制器/并行控制器状态、所有者、结构是否成形、CPU/配方搜索耗时、工作模式、能量消耗/产出、线程与配方进度条、并行度）与 `MachineryHatchInfoProvider`（ME 仓的在线/离线/缺频道）。另有 `display.theoneprobe` 配置节 9 项颜色与显示开关。新版无 TOP 代码、无 `top.*` 语言键、无配置。
- **旧版证据**：`.../common/integration/ModIntegrationTOP.java:20-25`（注册 provider）、`:27-69`（9 项配置）；`.../common/integration/theoneprobe/MMInfoProvider.java:387-413`、`:44-51`、`:234-304`；`.../github/kasuminova/mmce/common/integration/theoneprobe/MachineryHatchInfoProvider.java:15-44`；`.../src/main/resources/assets/modularmachinery/lang/en_US.lang:136-149`（`top.*` 14 条）
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `theoneprobe|TheOneProbe` → 无匹配；`.../build.gradle:28-37` 无 TOP 依赖；`.../lang/en_us.json:1` 无 `top.` 前缀键

### N6. FluxNetworks 集成
- **要点**：旧版把 `MMEnergyHandler` 插到 `TileEntityHandler.tileEnergyHandlers` 列表头部，使通量网络能传输超过 `Integer.MAX_VALUE` 的能量，并提供 `canAddEnergy/canRemoveEnergy/addEnergy/removeEnergy` 长整型接口。新版完全不存在。
- **旧版证据**：`.../common/integration/fluxnetworks/ModIntegrationFluxNetworks.java:6-10`；`fluxnetworks/MMEnergyHandler.java:16-77`；`.../common/base/Mods.java:56`
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `fluxnetworks|FluxNetworks` → 无匹配

### N7. IC2 集成
- **要点**：旧版有 `IntegrationIC2EventHandlerHelper`，在能量输入/输出仓加载与卸载时向 Forge 事件总线抛 `EnergyTileLoadEvent`/`EnergyTileUnloadEvent`。新版不存在。
- **旧版证据**：`.../common/integration/IntegrationIC2EventHandlerHelper.java:38-56`；`.../common/base/Mods.java:49`
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `ic2` → 无匹配

### N8. AE2 集成（方块、升级、网络、数据包、JEI 转移）
- **要点**：旧版 AE2 联动规模最大：`ModIntegrationAE2` 注册容量升级（ME 流体/气体输入输出总线各 5 级）与安全/供电校验；`common/block/appeng/` 下 12 个 ME 总线方块（`BlockMEItemBus`、`BlockMEItemInputBus`、`BlockMEOutputBus`、`BlockMEFluidBus`、`BlockMEGasBus`、`BlockMEPatternProvider`、`BlockMEPatternMirrorImage`、`BlockMEMachineComponent` 等）；`ModularMachinery.java` 注册 6 个仅 AE2 存在时启用的数据包；JEI 注册 `MEInputRecipeTransferHandler`。新版全部不存在。
- **旧版证据**：`.../github/kasuminova/mmce/common/integration/ModIntegrationAE2.java:16-36`；`.../common/block/appeng/`（12 个文件，含 `BlockMEItemBus.java`、`BlockMEFluidBus.java`、`BlockMEPatternProvider.java`）；`.../ModularMachinery.java:112-137`；`.../common/integration/ModIntegrationJEI.java:209-215`；`.../common/base/Mods.java:59-80`
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `appeng|AE2` → 无匹配

### N9. GregTech CEu 集成（组件代理 / Handler 代理 / 结构代理）
- **要点**：旧版注册 3 个机器组件代理（`GTEnergyHatchProxy`、`GTItemBusProxy`、`GTFluidHatchProxy`）、2 个 handler 代理（`GTEnergyHandlerProxy`、`GTFluidTankProxy`）与 1 个特殊结构方块代理（`GTBlockMachineProxy`），使 GT 的仓室能直接充当机器组件。新版不存在。
- **旧版证据**：`.../github/kasuminova/mmce/common/integration/gregtech/ModIntegrationGTCEU.java:12-18`；`gregtech/componentproxy/GTEnergyHatchProxy.java`、`GTItemBusProxy.java`、`GTFluidHatchProxy.java`；`gregtech/handlerproxy/GTEnergyHandlerProxy.java`、`GTFluidTankProxy.java`；`gregtech/patternproxy/GTBlockMachineProxy.java`；`.../common/base/Mods.java:24-45`
- **新版证据**：对 `modular-machinery-reborn/src/main/java` grep `gregtech|GregTech` → 无匹配；`build.gradle:28-37` 无 GT 依赖

### N10. JEI 结构预览类别
- **要点**：旧版有独立 JEI 类别 `modularmachinery.preview`，把每台机器的结构预览包成 `StructurePreviewWrapper` 加入 JEI，并以控制器/工厂控制器作为催化剂；`ModIntegrationJEI` 还暴露 `PREVIEW_WRAPPERS` 与 `reloadPreviewWrappers`。新版只有一个 `machine` 配方类别，无结构预览。
- **旧版证据**：`.../common/integration/ModIntegrationJEI.java:68-69`、`:185`、`:217-221`、`:123-125`；`.../common/integration/preview/CategoryStructurePreview.java:32-44`、`preview/StructurePreviewWrapper.java`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/client/jei/ModularMachineryJeiPlugin.java:19`（只注册 1 个类别 `MachineRecipeCategory`）

### N11. JEI 自定义 ingredient、每机器动态类别、蓝图 subtype 与书签注入
- **要点**：旧版为每台机器单独创建 `CategoryDynamicRecipe` 并注册 `DynamicRecipeWrapper`；用 `registerItemSubtypes` 为蓝图按关联机器做 subtype 解释；注册 `HybridFluid` 与（Mekanism 存在时的）`HybridFluidGas` 自定义 JEI ingredient 及渲染器/stack helper；并通过反射取 JEI 的 `InputHandler.bookmarkList` 实现配方书签注入。新版只有单类别、只显示 `Ingredient` 输入与 `ItemStack` 输出，以上能力全部不存在。
- **旧版证据**：`.../common/integration/ModIntegrationJEI.java:97-99`（每机器类别名）、`:101-121`（动态 wrapper 与 reload）、`:146-154`（subtype）、`:160-178`（自定义 ingredient）、`:127-143`（书签）；`ingredient/HybridFluid.java`、`HybridFluidGas.java`、`HybridFluidRenderer.java`、`HybridStackHelper.java`、`IngredientItemStack.java`；`recipe/CategoryDynamicRecipe.java`、`recipe/DynamicRecipeWrapper.java`、`recipe/RecipeLayoutHelper.java`、`recipe/RecipeLayoutPart.java`
- **新版证据**：`.../client/jei/MachineRecipeCategory.java:21`（单例 `TYPE`）、`:31-34`（仅 INPUT/OUTPUT 两个槽）

### N12. ModularMagic（kport）JEI 十种魔法量 ingredient 与 RecipePrimer 扩展
- **要点**：旧版 `kport.modularmagic` 提供 8 个 JEI 自定义 ingredient（`Aura`、`Constellation`、`DemonWill`、`Grid`、`Impetus`、`LifeEssence`、`Mana`、`Rainbow`、`Starlight`，含 helper 与 renderer 与 layout part），以及 `@ZenExpansion("mods.modularmachinery.RecipePrimer")` 的 `MagicPrimer`，暴露 22 个脚本方法（aspect/aura/constellation/grid/life essence/starlight/demon will/mana 的输入输出）。新版不存在。
- **旧版证据**：`.../kport/modularmagic/common/integration/JeiPlugin.java:43-80`；`.../integration/crafttweaker/MagicPrimer.java:28-30`、`:32-264`；`.../integration/jei/ingredient/`（9 个）、`jei/helper/`（10 个）、`jei/recipelayoutpart/`（10 个）、`jei/render/`（10 个）
- **新版证据**：`modular-machinery-reborn` 全树无 `kport`/`modularmagic` 包（文件清单仅 17 个 Java 文件）；`build.gradle:28-37` 无相关依赖

### N13. Mixin 集成（AE2 / NAE2 / JEI / Minecraft 渲染钩子）
- **要点**：旧版有 4 个 mixin 配置：`mixins.mmce_minecraft.json`（早期加载，渲染管线钩子）、`mixins.mmce_jei_hacky.json`（`MixinRecipeLayout` 重定向 GL translate、`MixinRecipesGui` 注入蓝图界面）、`mixins.mmce_ae2.json`（`MixinContainerInterfaceTerminal`、`MixinDualityInterface` 对接 `MEPatternProvider`）、`mixins.mmce_nae2.json`（`MixinContainerPatternMultiTool`）。新版无 mixin 配置、无 mixin 包。
- **旧版证据**：`.../github/kasuminova/mmce/mixin/MMCELateMixinLoader.java:12-29`、`mixin/MMCEEarlyMixinLoader.java:25-27`、`mixin/ae2/MixinDualityInterface.java`、`mixin/ae2/MixinContainerInterfaceTerminal.java`、`mixin/ae2/nae2/MixinContainerPatternMultiTool.java`、`mixin/jei/MixinRecipeLayout.java`、`mixin/jei/MixinRecipesGui.java`、`mixin/minecraft/MixinRenderGlobal.java`、`mixin/minecraft/MixinTileEntityRendererDispatcher.java`；`src/main/resources/mixins.mmce_ae2.json`、`mixins.mmce_jei_hacky.json`、`mixins.mmce_minecraft.json`、`mixins.mmce_nae2.json`
- **新版证据**：对 `modular-machinery-reborn` grep `mixin|Mixin` → 无匹配；`src/main/resources/` 下无 mixin 配置

### N14. 配方检查失败提示的本地化暴露面（`craftcheck.*`）
- **要点**：旧版有 9 条 `craftcheck.failure.*` 键，逐项区分缺物品输入 / 输出空间不足 / 缺流体输入 / 储罐空间不足 / 缺气体输入 / 气体输出空间不足 / 能量不足 / 能量输出仓已满 / 智能接口数值不一致。新版只有一条笼统的 `block.modular_machinery_reborn.machine_controller.no_recipe`（"No matching machine recipe"），无逐项失败原因。
- **旧版证据**：`.../src/main/resources/assets/modularmachinery/lang/en_US.lang:151-159`
- **新版证据**：`modular-machinery-reborn/src/main/resources/assets/modular_machinery_reborn/lang/en_us.json:1`（单行 JSON，无 `craftcheck.` 前缀键）；`lang/zh_cn.json:1`

---

## 四、设计差异

### D1. 脚本技术栈整体替换（新版自创）
- **要点**：旧版脚本层是 CraftTweaker 4 / ZenScript（`@ZenClass`/`@ZenMethod`/`@ZenRegister`/`@ZenExpansion`，全工程 63 个 `@ZenClass`）；新版改用 KubeJS 6，入口为新自创的 `event.recipes.modular_machinery_reborn.machine({...})`，插件通过 `META-INF/kubejs.plugins.txt` 发现。旧版全树 grep `kubejs|KubeJS|rhino` 无匹配——旧版从来没有 KubeJS 入口。
- **旧版证据**：`.../common/integration/crafttweaker/MachineBuilder.java:4-5`、`:48-49`；旧版全树 grep `kubejs|KubeJS|rhino` → 无匹配
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/kubejs/ModularMachineryKubeJSPlugin.java:9-15`；`src/main/resources/META-INF/kubejs.plugins.txt:1`；`kubejs-examples/server_scripts/machines.js:4`

### D2. JEI 类别模型由「每机器 + 预览」退化为「全局单类别」
- **要点**：旧版每台机器一个动态类别（`modularmachinery.recipes.<machine>`）外加一个结构预览类别，蓝图按机器做 subtype 与催化剂；新版固定一个 `modular_machinery_reborn:machine` 类别，配方用 `machine` 字段区分但不分组显示。
- **旧版证据**：`.../common/integration/ModIntegrationJEI.java:97-99`、`:187-191`、`:203-207`
- **新版证据**：`.../client/jei/MachineRecipeCategory.java:21`；`.../client/jei/ModularMachineryJeiPlugin.java:19-23`

### D3. 外部依赖声明方式改变
- **要点**：旧版在 mod 元数据里以字符串约束声明前置（含 `required-after:crafttweaker@[4.0.4,)`），并在 `Mods` 枚举里维护 30+ 条模组 id 与特化检测（如 GTCEU 用 `Class.forName("gregtech.client.utils.BloomEffectUtil")` 探测）。新版 `mods.toml` 只声明 `forge` 与 `minecraft` 两个强制依赖，JEI/KubeJS 用 `compileOnly` 获取且未列为 optional 依赖。
- **旧版证据**：`.../ModularMachinery.java:61`；`.../common/base/Mods.java:21-112`
- **新版证据**：`modular-machinery-reborn/src/main/resources/META-INF/mods.toml:10-21`；`.../build.gradle:28-37`

### D4. 本地 jar 依赖（新版特有）
- **要点**：新版 `gradle.properties` 置 `use_local_deps=true`，`build.gradle` 走 `flatDir { dirs 'libs' }` 并以 `fg.deobf('libs:kubejs:1.20.1')` / `fg.deobf('libs:jei:15.49')` 引用；`libs/` 内实际只有两个本地 jar：`jei-15.49.jar`（1669096 字节）、`kubejs-1.20.1.jar`（1658792 字节）。非本地模式下会改为 Maven 坐标 `dev.latvian.mods:kubejs-forge:2001.6.5-build.16` 与 `mezz.jei:jei-1.20.1-forge:15.2.0.27`。旧版无 `libs/` 本地 jar 机制。
- **旧版证据**：旧版工程根无 `libs/` 目录、无 flatDir 仓库声明（构建配置为 1.12.2 ForgeGradle 体系）
- **新版证据**：`modular-machinery-reborn/gradle.properties:12`；`.../build.gradle:25`、`:30-36`；`modular-machinery-reborn/libs/jei-15.49.jar`、`libs/kubejs-1.20.1.jar`

### D5. 旧有新无：TOP 进度条可视化配置面
- **要点**：旧版把配方进度条填充色/交替填充色/边框色/背景色（正常与失败两套）、是否显示小数、是否显示并行控制器信息做成了 `display.theoneprobe` 配置节共 9 项；新版没有任何可视化配置项。
- **旧版证据**：`.../common/integration/ModIntegrationTOP.java:10-18`、`:27-69`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/ModularMachineryReborn.java:22-35`（无配置加载）；工程内无 config 相关文件

### D6. 机器定义从数据驱动改为硬编码（语义改变，且与文档不符）
- **要点**：旧版机器定义来自 `.json`（`MachineLoader` 用 Gson 反序列化 `DynamicMachine`，支持 `.var.json` 变量文件），结构可随数据文件变化；新版结构判定硬编码为控制器同一水平面相邻 8 格必须是铁块或四种端口方块。但新版 `README.md` 与 `mods.toml` 描述为 "data-driven machines"。
- **旧版证据**：`.../common/machine/MachineLoader.java:86-87`、`:104-107`、`:132`、`:149-150`
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/block/MachineStructure.java:10-22`；`modular-machinery-reborn/README.md:8`；`src/main/resources/META-INF/mods.toml:9`

### D7. 能量字段语义收窄
- **要点**：旧版能量需求为 `long`，并在展示时经 `RecipeModifier.applyModifiers` 叠加时长倍率与并行度（TOP 中 `getEnergyRequired` 返回 `long`）。新版 `MachineRecipe.energy` 为 `int`（单位 FE，`Math.max(0, energy)`），无修饰器与并行度修正链。
- **旧版证据**：`.../common/integration/theoneprobe/MMInfoProvider.java:306-320`（`long` 计算与 `durationMul`、`parallelism`）
- **新版证据**：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/recipe/MachineRecipe.java:19`、`:23`；`.../recipe/ModRecipeSerializers.java:24`

---

## 五、关键发现

1. **旧版集成层最核心的 5 类模组联动在新版全部为 0 命中**：对 `modular-machinery-reborn/src/main/java` 全树 grep `appeng|AE2`、`gregtech|GregTech`、`theoneprobe|TheOneProbe`、`fluxnetworks|FluxNetworks`、`ic2` 均无匹配，集成能力整体归零。
2. **脚本入口被换了技术栈并大幅缩水**：旧版 CraftTweaker 侧有 16 个集成层 `@ZenClass`（全工程 63 个），覆盖机器构建、需求组件、配方事件与升级；新版 KubeJS 插件全文 16 行、只有 1 个 `registerRecipeSchemas`，脚本可表达的内容仅 `machine/input/output/duration/energy` 五个字段。
3. **旧版完全没有 KubeJS 入口**：旧版全树 grep `kubejs|KubeJS|rhino` 无匹配，KubeJS 桥（插件类 + `META-INF/kubejs.plugins.txt` + 示例脚本）是新版自创的入口。
4. **新版对外部模组只做编译期依赖、不做运行期声明**：`build.gradle` 以 `compileOnly` 引入 KubeJS/JEI（`use_local_deps=true` 时取自 `libs/` 的两个本地 jar），`mods.toml` 只声明 forge 与 minecraft，JEI/KubeJS 均未列为 optional 依赖。
5. **JEI 联动从"每机器动态类别 + 结构预览 + 自定义 ingredient + ME 配方转移 + 蓝图 subtype + 书签注入"退化为单一类别**：新版只有一个 `machine` 类别、INPUT/OUTPUT 两个槽位，蓝图/结构预览等旧版作者入口全部消失。
6. **配方检查失败提示与 TOP 语言键面归零**：旧版 `en_US.lang` 有 14 条 `top.*` 与 9 条 `craftcheck.failure.*`，新版 `en_us.json` 中两者均无对应键，只剩一条笼统的 `machine_controller.no_recipe`。

---

## 六、不确定项

1. **未编译/未运行新版工程**：无法确认 KubeJS 缺席时 `ModularMachineryKubeJSPlugin` 是否确实不被加载（代码里没有 `@Optional` 或模组存在性判断，仅依赖 `META-INF/kubejs.plugins.txt` 的发现时机）。
2. **`libs/` 内两个 jar 的内部内容与版本兼容性未解包核实**：仅确认文件名与字节大小（`jei-15.49.jar` 1669096 字节、`kubejs-1.20.1.jar` 1658792 字节），未验证其中类与 `build.gradle` 引用路径 `libs:kubejs:1.20.1` / `libs:jei:15.49` 是否匹配。
3. **旧版其余模组联动未逐一核对**：`Mods` 枚举里的 Mekanism、Astral Sorcery、Botania、Thaumcraft、Draconic Evolution、Thermal Expansion、Blood Magic、Nature's Aura、Extra Utilities 2、Multiblocked、GeckoLib3、AE2FC 等，其代码分散在 `Requirement`/`RecipeAdapter`/`client.model` 等非本次指定目录，本次未逐文件核实其集成实现位置与规模。
4. **旧版前置依赖清单不完整**：仅读到 `ModularMachinery.java:61` 的 `required-after:crafttweaker@[4.0.4,)` 一条字符串片段，未读完整的 `mcmod.info`/依赖字符串全文。
5. **旧版 4 个 mixin 配置文件的注入细节未逐字读取**：仅确认文件存在、被 `MMCEEarlyMixinLoader`/`MMCELateMixinLoader` 引用，以及相应 mixin 类头部；未通读 `mixins.mmce_*.json` 的目标类与方法列表。
6. **新版 `client/` 与 `menu/` 之间是否存在未在本次读取范围内的集成相关代码**：本次只读了 `client/jei/` 全部 2 个文件与主类，`ClientSetup.java`、`MachineControllerScreen.java` 未通读，可能含未识别的集成钩子（例如进度条或 tooltip 展示），但其对第三方模组的引用已由全树 grep 排除。
