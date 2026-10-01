# 工具物品、选择预览行为、命令与升级系统

> 审计对象
> - 旧版（1.12.2，Modular Machinery: Community Edition 2.3.2）：`C:\Users\kk07H\Desktop\模组\_mmce-src\ModularMachinery-Community-Edition-master`
> - 新版（1.20.1 / Forge 47.2.0 / Java 17）：`C:\Users\kk07H\Desktop\模组\modular-machinery-reborn`
>
> 说明：任务书中给的部分路径在实际仓库中不存在，已按下述真实路径核查，并在第六节记录：
> - `hellfirepvp/modularmachinery/common/upgrade/` **不存在**；升级系统实际位于 `github/kasuminova/mmce/common/upgrade/`（含 `registry/` 子包）。
> - `hellfirepvp/modularmachinery/common/tile/` **不存在**；控制器方块实体实际位于 `hellfirepvp/modularmachinery/common/tiles/`（复数）。任务书提到的 `tiles/` 与 `tile/` 两个包，只有 `tiles/` 存在。

---

## 一、已迁移

本领域内**唯一**可以判定为"新版有对应实现且语义基本等价"的项是物品注册标识本身：

1. **蓝图 / 模块铀 / 构造工具的物品注册名与堆叠上限被沿用**
   - 旧版证据：`_mmce-src/.../common/registry/RegistryItems.java:44-46`（`blueprint = prepareRegister(new ItemBlueprint())` 等），`:54-56` 用「类名小写」生成注册名，即 `itemblueprint` / `itemmodularium` / `itemconstructtool`；`ItemBlueprint.java:49` 设定 `setMaxStackSize(16)`、`ItemModularium.java:26` 设定 `setMaxStackSize(64)`、`ItemConstructTool.java:42` 设定 `setMaxStackSize(1)`。
   - 新版证据：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/item/ModItems.java:12-14`，`register("itemblueprint", 16)` / `register("itemmodularium", 64)` / `register("itemconstructtool", 1)`，注册名与堆叠上限逐一对齐。

2. **投影器（Machine Projector）物品 ID 与堆叠上限被沿用**
   - 旧版证据：`_mmce-src/.../youyihj/mmce/common/item/MachineProjector.java:35-37`（`machine_projector`、`setMaxStackSize(1)`），经 `RegistryItems.java:47` 的 `prepareRegisterWithCustomName` 注册。
   - 新版证据：`ModItems.java:15`，`register("machine_projector", 1)`。

> 注：这两条只覆盖"注册名/堆叠数"这一层；同一物品的**行为**在第二节与第三节分别判定为部分迁移或未迁移。

---

## 二、部分迁移

1. **蓝图物品（ItemBlueprint）——只有物品外壳，无任何蓝图行为**
   - 旧版真实行为（`_mmce-src/.../common/item/ItemBlueprint.java`）：
     - 常量 `DYNAMIC_MACHINE_NBT_KEY = "dynamicmachine"`，:46；
     - 静态方法 `getAssociatedMachine(ItemStack)` 从 NBT 取机器注册名并查 `MachineRegistry`，:53-56；
     - `getAssociatedMachineKey(ItemStack)` / `setAssociatedMachine(...)` 读写 NBT，:58-78；
     - `getSubItems` 为**每一台已注册机器**生成一个带 NBT 的蓝图物品，:80-89；
     - `addInformation` 显示机器本地化名或 `tooltip.machinery.empty`，:91-100；
     - `onItemUse`（右键方块）/`onItemRightClick`（右键空气）在客户端打开 `GuiType.BLUEPRINT_PREVIEW` 界面，:102-117。
   - 客户端界面确实存在：`_mmce-src/.../client/ClientProxy.java`（`case BLUEPRINT_PREVIEW -> ... return new GuiScreenBlueprint(machine);`），界面类 `_mmce-src/.../client/gui/GuiScreenBlueprint.java:29`（`extends GuiScreenDynamic`）。
   - 新版状态：`ModItems.java:12` 仅 `ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(stackSize)))`（:18-20），**完全是裸 `Item`**。
   - 缺失：机器绑定 NBT、tooltip、每机器子物品、结构预览界面、右键打开交互。
   - 证据路径：旧 `ItemBlueprint.java:46,53-117`；新 `ModItems.java:12,18-20`。
   - 补充核实：新版全工程 `grep -i blueprint|preview|selection` 只命中 `ModItems.java:12`、`ModBlocks.java:50`、`models/item/itemblueprint.json:1`、`lang/*.json`（见第四节第 1 条命令——新版无任何命令），即蓝图只剩注册与贴图。

2. **构造工具（ItemConstructTool）——只有物品外壳，无选区构建行为**
   - 旧版真实行为（`_mmce-src/.../common/item/ItemConstructTool.java`）：`onItemUse` 内要求 `!worldIn.isRemote && player.isCreative() && server.getPlayerList().canSendCommands(...)`；命中 `BlockController` 时调 `PlayerStructureSelectionHelper.finalizeSelection(clicked.getValue(BlockController.FACING), worldIn, pos, player)` 并把选区固化为机器 JSON，随后 `purgeSelection` + `sendSelection`；非控制器方块则 `toggleInSelection` + `sendSelection`，:53-68；tooltip `tooltip.constructtool.creative`。
   - 新版状态：`ModItems.java:14` 同样是裸 `Item`。
   - 缺失：创造模式+权限判定、控制器收尾保存、方块逐个加入/移除选区、选区网络同步。
   - 证据路径：旧 `ItemConstructTool.java:46-68`；新 `ModItems.java:14,18-20`。

3. **机器投影器（Machine Projector）——只有物品外壳，无结构投影渲染**
   - 旧版真实行为：
     - `_mmce-src/.../youyihj/mmce/common/item/MachineProjector.java:41-61`：`onItemUse` 中若目标方块是 `BlockController` 或 `BlockFactoryController` **且 `worldIn.isRemote`**，取 `controller.getParentMachine()`，非空则调 `StructurePreviewHelper.renderMachinePreview(machine, pos)`；:63-66 tooltip `tooltip.modularmachinery.machine_projector`。
     - `_mmce-src/.../youyihj/mmce/common/preview/StructurePreviewHelper.java:16-32`：若点击位置变化先 `renderHelper.unloadWorld()`；给玩家发 `message.machine_projector.project`；用 `DynamicMachineRenderContext.createContext(machine)` + `snapSamples()` + `renderHelper.startPreview/placePreview()` 真正放置全息结构预览；`reset()` 在 :34-36。
   - 新版状态：`ModItems.java:15` 裸 `Item`。
   - 缺失：控制器识别、渲染上下文、方块采样、预览放置与卸载。
   - 证据路径：旧 `MachineProjector.java:41-66`、`StructurePreviewHelper.java:16-36`；新 `ModItems.java:15,18-20`。

4. **结构选择/预览辅助系统（selection 包）——整体缺失，仅实物外壳保留**
   - 旧版证据：`_mmce-src/.../common/selection/PlayerStructureSelectionHelper.java`：
     - `activeSelectionMap`（按 UUID 存选区）:55、`clientSelection`:56；
     - `toggleInSelection` :58-60、`purgeSelection` :62-67、`sendSelection`（发 `PktSyncSelection`）:69-75；
     - `finalizeSelection` :77-126：空选区报 `message.structurebuild.empty`；否则把 `StructureSelection.compressAsArray` 结果按控制器朝向 `rotateYCCW()` 旋转到 NORTH，:85-94；`serializeAsMachineJson()` :97；写文件到 `CommonProxy.dataHolder.getMachineryDirectory()`，文件名 `machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json`，重名追加 ` (n)`，:103-124；客户端断线时 `onDisconnect` 清理选区，:128-134；
     - `StructureSelection.togglePosition` :151-157、`compressAsArray` :159-179（把每个坐标转成 `IBlockStateDescriptor` + 方块实体 NBT（剔除 x/y/z）后以控制器为原点存相对坐标）。
   - 新版状态：**不存在**（新版 `src` 全域 grep `selection` 无结果；无 `PlayerStructureSelectionHelper`、无 `PktSyncSelection`、无机器 JSON 导出）。
   - 判定为"部分迁移"的唯一理由是蓝图/构造工具/投影器三个物品壳仍在；选择与预览的实体逻辑为零。
   - 证据路径：旧 `PlayerStructureSelectionHelper.java:55-179`；新：不存在（见第四节核实命令）。

5. **物品形态的方块物品映射（ItemBlock*）——方块物品存在，但动态着色/自定义名语义丢失**
   - 旧版证据：`_mmce-src/.../common/item/ItemBlockMachineComponent.java:23-35`（实现 `ItemDynamicColor`，按 `Config.machineColor` 染色）、`ItemBlockController.java:28-63`（显示名取 `ctrlBlock.getLocalizedName()`；`placeBlockAt` 把 `stack` 上的 `owner` UUID 或放置者 UUID 写入控制器 `setOwner`）、以及同目录 `ItemBlockCustomName.java`、`ItemBlockMachineComponentCustomName.java`、`ItemBlockMEMachineComponent.java`。
   - 新版证据：`modular-machinery-reborn/.../block/ModBlocks.java:55,58` 仅 `new BlockItem(block, new Item.Properties())`。
   - 缺失：`ItemDynamicColor` 动态着色（新版 `ModItems`/`ModBlocks` 无任何 tint 注册，`ClientSetup.java:9` 只注册了一个 `MenuScreens`）、控制器方块物品的机器名显示、owner UUID 写入。
   - 证据路径：旧 `ItemBlockMachineComponent.java:23-35`、`ItemBlockController.java:28-63`、`RegistryItems.java:38,68-70,84-91`；新 `ModBlocks.java:55,58`、`ClientSetup.java:9`。

6. **机器控制器方块实体的"结构判定 + 配方执行"骨架**
   - 旧版证据：`_mmce-src/.../common/tiles/TileMachineController.java`（`doControllerTick` 分 ASYNC/SEMI_SYNC/SYNC 三档 :64-114；`doRecipeTick` :143-205；`checkAllPatterns` 遍历 `MachineRegistry` 匹配结构 :308-320）＋ `tiles/TileFactoryController.java:74-124`。
   - 新版证据：`modular-machinery-reborn/.../block/MachineControllerBlockEntity.java:48-72`（每 tick `MachineStructure.isFormed` + `findRecipe`，能量不足则停，进度满则扣能量/扣输入/出产物）、`block/MachineStructure.java:10-22`（写死"控制器周围 8 格铁块环"，且仅认识 `ITEM_INPUT_HATCH/ITEM_OUTPUT_HATCH/ENERGY_INPUT_HATCH/FLUID_INPUT_HATCH` 四个无分级方块）。
   - 判定依据：新版从数据驱动结构（旧版 `TaggedPositionBlockArray` + 机器 JSON）退化为**硬编码 3×3 环**，只保留了最朴素的"单配方单线程"执行，因此只算部分迁移。
   - 证据路径：旧 `TileMachineController.java:64-114,143-205,308-320`；新 `MachineControllerBlockEntity.java:48-72`、`MachineStructure.java:10-22`。

7. **工厂控制器（Factory Controller）的多线程批量生产——仅保留"工厂控制器"这一概念的影子，无实现**
   - 旧版证据：`_mmce-src/.../common/tiles/TileFactoryController.java:45-54`（`coreRecipeThreads`、`recipeThreadList`、`waitToExecute`、`totalParallelism`、`extraThreadCount`、`recipeThread`/`SequentialTaskExecutor`），`:74-124` 三档工作模式，`:184-197` `doRecipeTick`，`:202-257` `doThreadRecipeTick`，`:271-311` 开始/失败/完成事件（`FactoryRecipeStartEvent/FailureEvent/FinishEvent/TickEvent`），`:322-372` `searchAndStartRecipe`（`FactoryRecipeSearchTask` 异步搜索 + `RecipeCraftingContextPool` + `SequentialTaskExecutor`），`:400-418` `getAvailableParallelism`（用总并行数减去各活动线程 `(parallelism-1)`），`:432-457` `offerRecipe`/`getMaxThreads`，`:474-528` `updateCoreThread`/`cleanIdleTimeoutThread`/`hasIdleThread`；方块侧 `common/block/BlockFactoryController.java:25-41`（每机器动态注册 `<machine>_factory_controller` 方块，`FACTORY_CONTROLLERS` 映射）。
   - 新版状态：**完全不存在**（新版无 `factory`、无 `TileFactoryController`、无 `FactoryRecipeThread`、无 `RecipeCraftingContextPool`、无多线程执行器）。
   - 判定为"部分迁移"的理由：新版 `ModBlocks` 中存在无分级的方块物品与 `MachineHatchBlock`，但没有任何与工厂/线程相关的概念留存。
   - 证据路径：旧 `TileFactoryController.java:45-54,184-197,322-372,400-457,474-528`、`BlockFactoryController.java:25-41`；新：不存在（见第四节核实命令）。

---

## 三、未迁移

1. **旧版全部四条服务端命令 + 两条客户端/重载命令**
   - 命令清单（依据 `_mmce-src/.../resources/assets/modularmachinery/lang/en_US.lang` 中 `command.*` 键，lang 第 3-24 行）：
     | 命令 | 键 / 用途 | 实现类 |
     |---|---|---|
     | `/mm-syntax` | `command.modularmachinery.syntax`（lang:3）检查机器与配方语法 | `common/command/CommandSyntax.java:26-51`（权限等级 2） |
     | `/mm-hand` | `command.modularmachinery.hand`（lang:4，另 lang:5 `.empty`）取手持物品 ID + NBT | `common/command/CommandHand.java:33-83`（权限等级 2） |
     | `/mm-get_blueprint <machineName>` | `command.modularmachinery.get_blueprint`（lang:6，另 7/8/9 为 player_only/not_found/success） | `common/command/CommandGetBluePrint.java:16-59`（权限等级 2） |
     | `/mm-performance_report [reset]` | `command.modularmachinery.performance_report`（lang:12，含 title/reset/total_executed/tasks_avg_per_execution/total_used_time/used_time_avg_per_execution/task_used_time/task_used_time_avg/used_time_avg，lang:13-24） | `common/command/CommandPerformanceReport.java:18-78`（权限等级 2，读 `TaskExecutor` 的 4 个静态计数器） |
     | `/mm-reload` | `command.modularmachinery.reload`（lang:10） | `common/integration/crafttweaker/command/CommandCTReload.java:14-33`（调 `ReloadCommand.reloadScripts`） |
     | `/mm-reload_client` | `command.modularmachinery.reload_client`（lang:11） | `common/integration/crafttweaker/command/CommandCTReloadClient.java:11-25` |
   - 注册证据：`_mmce-src/.../ModularMachinery.java:161-167`（`registerServerCommand(new CommandSyntax()/CommandHand()/CommandGetBluePrint()/CommandPerformanceReport())`，第 167 行注册 `CommandCTReload`）；客户端 `client/ClientProxy.java:239`（`ClientCommandHandler.instance.registerCommand(new CommandCTReloadClient())`）。
   - 新版状态：**不存在**。新版 `src` 全域 grep `command|RegisterCommands|LiteralArgumentBuilder|SimpleCommandExceptionType` **零命中**；`ModularMachineryReborn.java:22-35` 只注册 `BLOCKS/ITEMS/TABS/TYPES/SERIALIZERS/BLOCK_ENTITIES/MENUS`，无 `RegisterCommandsEvent` 监听。
   - 另外 `/mm-hand` 依赖的 `PktCopyToClipboard`、`/mm-hand` 的 `NBTJsonSerializer`、`/mm-performance_report` 依赖的 `TaskExecutor` 统计在旧版存在（`CommandHand.java:12,77`、`CommandPerformanceReport.java:13-16`），新版全部不存在。
   - 证据路径：旧 lang:3-24、`ModularMachinery.java:161-167`、`ClientProxy.java:239`、六个命令类；新：不存在。

2. **升级系统（common/upgrade 及 registry 子包）整个子系统**
   - 旧版注册表结构：
     - `github/kasuminova/mmce/common/upgrade/registry/RegistryUpgrade.java:20-21`：两张全局表 `UPGRADES: HashMap<String, MachineUpgrade>`（按类型名）与 `ITEM_UPGRADES: Map<Item, UpgradeInfo>`（`Reference2ObjectOpenHashMap`）；API 有 `clearAll()` :23-26、`getItemUpgradeList(ItemStack)` :28-35、`supportsUpgrade(ItemStack)` :37-43、`addFixedUpgrade(ItemStack, MachineUpgrade)` :45-47、`addSupportedItem(ItemStack)` :49-51、`registerUpgrade(String, MachineUpgrade)` :53-55、`getUpgrade(String)` :57-59。
     - `registry/UpgradeInfo.java:9-47`：`matches`（物品匹配列表，`ItemStack.areItemsEqual`）+ `upgrades`（固定升级列表），提供 `addMatch`/`addUpgrade`/`getMatches`/`getUpgrades`。
     - `UpgradeType.java:12-88`：字段 `compatibleMachines`/`incompatibleMachines`/`name`/`localizedName`/`level:float`/`maxStackSize:int`；`isCompatible(DynamicMachine)` :49-57（先看白名单再看黑名单，都空则全兼容）；构造即 `new UpgradeType(name, localizedName, level, maxStack)`。
     - 升级项类层次：抽象 `MachineUpgrade.java:21-95`（`UpgradeType type`、`eventProcessor: Map<Class<?>, List<UpgradeEventHandlerCT>>`、`parentBus: TileUpgradeBus`、`stackSize`、`readNBT/writeNBT`、`copy(ItemStack)`、`getDescriptions()`、`getBusGUIDescriptions()`、`addEventHandler`/`getEventHandlers`、`increment/decrementStackSize`）；`DynamicMachineUpgrade.java:7-61`（追加 `busInventoryIndex`、`valid`、`parentStack`、`readItemNBT/writeItemNBT`、`setParentBus/setParentStack`、`validate/invalidate`）；具体实现 `SimpleMachineUpgrade.java:20-90`（`descriptions` 列表 + `busGuiDescriptionHandler` + `customData` NBT，`@ZenClass("mods.modularmachinery.SimpleMachineUpgrade")`）与 `SimpleDynamicMachineUpgrade.java:21-161`（`descriptionHandler`/`busGuiDescriptionHandler` + `itemData`（存物品上）+ `customData`（存总线上）+ `decrementItemDurability` :85-99）。
     - 注册入口（CraftTweaker/Zen）：`MachineUpgradeBuilder.java:55-60`（`newBuilder(name, localizedName, level, maxStack)` → `new SimpleMachineUpgrade(new UpgradeType(...))`）、`:326-327` `buildAndRegister()` → `RegistryUpgrade.registerUpgrade(machineUpgrade.getType().getName(), machineUpgrade)`；`MachineUpgradeHelper.java:31`/`:42-52`（`addSupportedItem` / `addFixedUpgrade`）；`DynamicMachineUpgradeBuilder.java` 对应动态版。
     - 运行时承载：`hellfirepvp/modularmachinery/common/tiles/TileUpgradeBus.java:43-64`（`boundedMachine`、`foundDynamicUpgrades: Int2ObjectMap<List<DynamicMachineUpgrade>>`、`foundUpgrades: Map<UpgradeType, MachineUpgrade>`、`upgradeCustomData`、`IOInventory`），`:94-154`（`onUpgradeInventoryChanged` 扫描槽位 → `RegistryUpgrade.supportsUpgrade` → `CapabilityUpgrade.MACHINE_UPGRADE_CAPABILITY` → `updateUpgrades`/`updateDynamicUpgrades`；同类非动态升级按 `incrementStackSize(parentStack.getCount()-1)` 合并堆叠），`:251-282` `getUpgrades(controller)`（按 `type.isCompatible(foundMachine)` 过滤）、`getUpgradeCustomData/setUpgradeCustomData`；方块 `common/block/BlockUpgradeBus.java`（145 行）与数据枚举 `common/block/prop/UpgradeBusData.java:8-14`：等级 `NORMAL(3)/REINFORCED(6)/ELITE(9)/SUPER(12)/ULTIMATE(18)` 槽位，`loadFromConfig` :23-29 可配置，上限 18；GUI `client/gui/GuiContainerUpgradeBus.java`（177 行）、容器 `common/container/ContainerUpgradeBus.java`（94 行）、方块实体能力 `github/kasuminova/mmce/common/capability/CapabilityUpgrade`（由 `TileUpgradeBus.java:3,107` 引用）。
   - 新版状态：**不存在**。新版无 `upgrade`/`UpgradeType`/`RegistryUpgrade`/`TileUpgradeBus`/`UpgradeBusData`，无任何 `Capability` 扩展注册（`MachineControllerBlockEntity.java:90-96` 只挂了 `ITEM_HANDLER/ENERGY/FLUID_HANDLER` 三个原版/Forge 能力）。
   - 证据路径：旧 `registry/RegistryUpgrade.java:20-59`、`registry/UpgradeInfo.java:9-47`、`UpgradeType.java:12-88`、`MachineUpgrade.java:21-95`、`DynamicMachineUpgrade.java:7-61`、`SimpleMachineUpgrade.java:20-90`、`SimpleDynamicMachineUpgrade.java:21-161`、`integration/crafttweaker/upgrade/MachineUpgradeBuilder.java:55-60,326-327`、`MachineUpgradeHelper.java:31,42-52`、`tiles/TileUpgradeBus.java:43-64,94-154,251-282`、`block/prop/UpgradeBusData.java:8-14`；新：不存在。

3. **并行控制器（Parallel Controller）及其分级上限与算法**
   - 旧版证据：
     - 方块实体 `common/tiles/TileParallelController.java:13-82`：字段 `maxParallelism`/`parallelism`，构造入参 `maxParallelism`，`readCustomNBT/writeCustomNBT` 持久化 `maxParallelism`/`parallelism`（并在读取时把 `parallelism` 夹到 `maxParallelism`），内部类 `ParallelControllerProvider`（`super(IOType.INPUT)`，`getParallelism/setParallelism/getMaxParallelism`，`getComponentType()` 返回 `ComponentTypesMM.COMPONENT_PARALLEL_CONTROLLER`）。
     - 分级上限 `common/block/prop/ParallelControllerData.java:8-14`：`NORMAL(4) / REINFORCED(16) / ELITE(64) / SUPER(256) / ULTIMATE(512)`，`loadFromConfig` :23-29 从配置文件 `parallel-controller.<name>.max-parallelism` 覆盖（默认值即上表，范围 1..Integer.MAX_VALUE）。
     - 方块 `common/block/BlockParallelController.java:35`（`PropertyEnum<ParallelControllerData> CONTROLLER_TYPE`）、`:124-128`（`createTileEntity` 用 `state.getValue(CONTROLLER_TYPE).getMaxParallelism()` 构造 tile）、`:137-145`（右键开 `GuiType.PARALLEL_CONTROLLER` GUI）。
     - 算法（总并行数）`common/tiles/base/TileMultiblockMachineController.java:293-304`：`parallelism = foundMachine.getInternalParallelism()`，再逐一累加结构内每个 `ParallelControllerProvider.getParallelism()`，一旦 `>= foundMachine.getMaxParallelism()` 立即截断返回；否则返回 `Math.max(1, parallelism)`。即**机器级上限与内部并行数是硬上限，并行控制器只能补足差额**。
     - 并行数如何变成实际产出：`common/concurrent/RecipeSearchTask.java:15,20,40`（`maxParallelism` 字段 → `new ActiveMachineRecipe(recipe, maxParallelism)`）、`FactoryRecipeSearchTask.java:22-26,55` 同理；`common/crafting/ActiveMachineRecipe.java:43-48,138`（`maxParallelism, parallelism = 1`；`this.maxParallelism *= extraParallelism`）、`:156`（序列化 `maxParallelism`）、`:165-181`（`get/setMaxParallelism`、`get/setParallelism`）；`common/crafting/helper/RecipeCraftingContext.java:435-489`（`getMaxParallelism(parallelizable)` 计算 → `setParallelism(...)` → 夹到 `activeRecipe.getMaxParallelism()`，不满足则回落 1）；每个需求类型都要重写 `getMaxParallelism(components, context, maxParallelism)`（如 `requirement/RequirementItem.java:273-283`、`RequirementEnergy.java:119-129`、`RequirementFluid.java:153-163`、`RequirementGas.java:107-117`、`RequirementIngredientArray.java:182-192`）；事件侧 `event/recipe/RecipeCheckEvent.java:32-33` 可 `setParallelism(Math.min(activeRecipe.getParallelism(), parallelism))`。
     - GUI/同步：`client/gui/GuiContainerParallelController.java:56,98-131`（显示当前值、计算 `maxCanIncrement`、发 `PktParallelControllerUpdate(maxParallelism)`）、`common/network/PktParallelControllerUpdate.java:46`（`provider.setParallelism(newParallelism)`）；组件类型注册 `common/registry/RegistryComponentTypes.java:59`（`COMPONENT_PARALLEL_CONTROLLER`）+ 键 `common/lib/ComponentTypesMM.java:30`。
   - 新版状态：**不存在**。新版 grep `parallel` 零命中（第四节核实命令）。
   - 证据路径：旧 `TileParallelController.java:13-82`、`block/prop/ParallelControllerData.java:8-14`、`BlockParallelController.java:35,124-128,137-145`、`TileMultiblockMachineController.java:293-304`、`RecipeSearchTask.java:15-40`、`ActiveMachineRecipe.java:43-48,138,165-181`、`RecipeCraftingContext.java:435-489`、`RecipeCheckEvent.java:32-33`；新：不存在。

4. **智能接口（Smart Interface）及其作用**
   - 旧版证据：
     - 方块实体 `common/tiles/TileSmartInterface.java:25-35`（持有 `List<SmartInterfaceData> boundData` + `SmartInterfaceProvider`；`onDataUpdate` 在绑定的位置找到 `TileMultiblockMachineController` 后 post `SmartInterfaceUpdateEvent`）、`:44-72` `doRestrictedTick` 每 20 tick 清理失效绑定（目标不再是控制器就移除），`:74-100` NBT 读写 `boundData`，`:102-178` `SmartInterfaceProvider`（`super(IOType.INPUT)`）提供 `getMachineData(String type|BlockPos|int index)`、`addMachineData(pos, parent, type, defaultValue, override)`（重复位置可 `override` 覆盖，随后触发 `onDataUpdate`）、`removeMachineData(pos)`、`getBoundSize()`，`getComponentType()` 返回 `ComponentTypesMM.COMPONENT_SMART_INTERFACE`。
     - 作用（结合数据类型）：`common/util/SmartInterfaceData.java:15-99`——每条数据 = `pos`（控制器位置）+ `parent`（机器注册名）+ `type`（类型名）+ 可变 `value: float`，序列化字段为 `pos`/`parent`/`type`/`value`；即**智能接口是一块"把别处某台机器的浮点运行参数（超频速率一类）读进本机器配方判定"的桥**。
     - 类型定义 `common/util/SmartInterfaceType.java:10-133`：`type` + `defaultValue` + 一组 GUI/JEI 文案（`headerInfo`/`valueInfo`/`footerInfo`/`notEqualMessage`/`jeiTooltip`/`jeiTooltipArgsCount`）+ `priority`（:33-36 注释说明：结构内智能接口数量超过机器预定义类型数时，新接口优先使用优先级最高的类型）；`compareTo` 按 priority 降序（:130-133）。
     - 方块与界面：`common/block/BlockSmartInterface.java:30`（`PropertyEnum<SmartInterfaceTypeEnum> INTERFACE_TYPE`）、`:100-102` `createTileEntity` → `TileSmartInterface`、`:110-119` 右键开 `GuiType.SMART_INTERFACE`；分级枚举 `common/block/prop/SmartInterfaceTypeEnum.java`（12 行）；GUI `client/gui/GuiContainerSmartInterface.java`（203 行）；网络包 `common/network/PktSmartInterfaceUpdate.java:60 行`；事件 `github/kasuminova/mmce/common/event/machine/SmartInterfaceUpdateEvent.java`（33 行）。
   - 新版状态：**不存在**。新版无 `SmartInterface*`、无 `smart_interface` 方块或物品、无对应 GUI/事件。
   - 证据路径：旧 `TileSmartInterface.java:25-35,44-72,102-178`、`SmartInterfaceData.java:15-99`、`SmartInterfaceType.java:10-133`、`BlockSmartInterface.java:30,100-102,110-119`、`block/prop/SmartInterfaceTypeEnum.java`、`network/PktSmartInterfaceUpdate.java`、`event/machine/SmartInterfaceUpdateEvent.java`；新：不存在。

5. **装配系统（Assembly：MachineAssembly / MachineAssemblyManager）**
   - 旧版证据：`_mmce-src/.../ink/ikx/mmce/common/assembly/MachineAssembly.java:41-52`（`World`+`ctrlPos`+`EntityPlayer`+`StructureIngredient`）、`:54-101` `buildFluidIngredients` + `getFluidHandlerItems`（从玩家背包里的流体容器（**明确跳过桶**，见 :88-91 的 TODO 注释）扣除所需流体）、`:103-120` `buildItemIngredients`（从背包扣除所需物品，`consumeInventoryItem`），总计 529 行的"用玩家背包材料自动装配整台机器"实现；`MachineAssemblyManager.java:13-46`：静态 `Map<BlockPos, MachineAssembly> MACHINE_ASSEMBLY_MAP`，`addMachineAssembly`/`checkMachineExist`/`getMachineAssemblyListFromPlayer`（按 GameProfile UUID 过滤）/`removeMachineAssembly(pos|player)`。
   - 新版状态：**不存在**（新版无 `assembly`、无 `StructureIngredient`、无装配进度或材料消耗逻辑）。
   - 证据路径：旧 `MachineAssembly.java:41-52,54-101,103-120`、`MachineAssemblyManager.java:13-46`；新：不存在。

6. **旧版其余工具类物品：`ItemDebugStruct`、`ItemDynamicColor` 接口、`ItemBlockMEMachineComponent` 等**
   - 旧版证据：`common/item/ItemDebugStruct.java:35-78`（结构匹配调试器：右键控制器输出朝向与"Failed at relative position: <相对坐标>"，遍历四个朝向 rotateYCCW 尝试匹配）；`common/item/ItemDynamicColor.java:20-21`（染色接口，由 `RegistryItems.java:38,68-70,87-89` 收集）；`ItemBlockCustomName.java`、`ItemBlockMachineComponentCustomName.java`、`ItemBlockMEMachineComponent.java`。
   - 新版状态：`ItemDebugStruct`、`ItemDynamicColor`、`ItemBlockCustomName`、`ItemBlockMEMachineComponent` **均不存在**（新版仅 `ModItems.java` 六个裸 `Item` + `ModBlocks` 的 `BlockItem`）。
   - 证据路径：旧 `ItemDebugStruct.java:35-78`、`ItemDynamicColor.java:20-21`、`RegistryItems.java:38,68-70,84-91`；新：不存在。

7. **旧版命令的权限与国际化基础设施**
   - 旧版证据：每个命令类都声明 `getRequiredPermissionLevel() == 2`（`CommandSyntax.java:33-36`、`CommandHand.java:40-43`、`CommandGetBluePrint.java:27-30`、`CommandPerformanceReport.java:27-30`），且 `getUsage` 返回 lang 键；lang 文件中共 22 条 `command.modularmachinery.*` 键（`en_US.lang:3-24`）。
   - 新版状态：新版 `lang/en_us.json` 与 `lang/zh_cn.json`（各 1 行 JSON）**不含任何 `command.` 键**，无权限/命令基础设施。
   - 证据路径：旧 `en_US.lang:3-24` + 四个命令类；新 `resources/assets/modular_machinery_reborn/lang/en_us.json:1`（全文件仅一行，无 `command.` 前缀）。

---

## 四、设计差异

1. **新版自创：分级仓室（Tiered Hatches）取代旧版"组件 + 等级枚举"结构**
   - 旧版分级是"同一方块 + 元数据/属性枚举"：`BlockParallelController.java:35`（`PropertyEnum<ParallelControllerData>`）、`BlockSmartInterface.java:30`（`PropertyEnum<SmartInterfaceTypeEnum>`）、`UpgradeBusData.java:8-14`（枚举承载槽位数）。
   - 新版改为**每个等级注册一个独立方块 ID**：`ModBlocks.java:29-42`，`TIERS = {tiny, small, normal, reinforced, big, huge, ultimate, ludicrous}`（另 `FLUID_TIERS` 用 `vacuum` 取代 `ultimate` 位序），共生成 8×4 + 8×2 个分级仓室方块；但这批方块**全部是同一个空壳类** `MachineHatchBlock.java:13-19`（只发一条 `block.modular_machinery_reborn.hatch.port` 消息，源码注释直言 `Capability routing is attached in the next port integration slice.`），且不被 `MachineStructure.isFrameBlock` 的四个无分级方块之外识别（`MachineStructure.java:19-21` 只接受 4 个无分级 ID，**分级仓室不进结构判定**）。
   - 结论：旧版"等级 = 数值上限（并行数/槽位/流体容量）"，新版"等级 = 独立注册名"，等级语义被抽空。

2. **新版自创：从"数据驱动机器 JSON"退化为"硬编码 3×3 铁环"**
   - 旧版：`TileMachineController.java:308-320` 遍历 `MachineRegistry` 并用 `BlockArrayCache.getBlockArrayCache(machine.getPattern(), controllerRotation)` 做旋转匹配；结构来源是 JSON 机器定义（`PlayerStructureSelectionHelper.java:97` 的 `serializeAsMachineJson()` 正是导出这种 JSON）。
   - 新版：`MachineStructure.java:10-16` 直接双层 for 循环检查 `controller.offset(dx,0,dz)` 八格是否铁块或四种仓室；`MachineControllerBlockEntity.java:70-71` 用 `r.machine().equals("basic") || r.machine().equals("machine_controller")` 这种**字符串硬编码**筛选配方。

3. **语义改变：`requiresBlueprint` / `isFactoryOnly` 的机器筛选语义消失**
   - 旧版 `TileMachineController.java:311-312` 会跳过 `machine.isRequiresBlueprint() || machine.isFactoryOnly()` 的机器，即"蓝图机与工厂专用机不走普通控制器自动匹配"。
   - 新版无 `DynamicMachine`/`MachineRegistry` 概念（无此类），该语义既无实现也无对应字段。

4. **旧有新无：`ItemDynamicColor` 动态着色机制整体消失**
   - 旧版：`ItemDynamicColor.java:20-21` 接口 + `ItemModularium.java:30-33`、`ItemBlockMachineComponent.java:29-35`（都返回 `Config.machineColor`）+ `RegistryItems.java:38,68-70,87-89`（`pendingDynamicColorItems` 收集）。新版无任何 item color handler 注册（`ClientSetup.java:9` 只注册 `MenuScreens`）。

5. **旧有新无：升级总线的"堆叠合并"语义**
   - 旧版 `TileUpgradeBus.java:138-154`：同一 `UpgradeType` 的非动态升级会 `founded.incrementStackSize(upgrade.getStackSize())`，首次放入时用 `upgrade.incrementStackSize(parentStack.getCount() - 1)` 把整摞物品折成升级层数；动态升级则按槽位单独存 `foundDynamicUpgrades` 并绑定 `parentBus`/`parentStack`/`busInventoryIndex`（:130-136）。新版无对应概念。

6. **语义改变：投影器 tooltip 与代码判定不一致（旧版既有问题，新版无投影器因此继承不到）**
   - 旧版 lang `tooltip.modularmachinery.machine_projector = "Sneak and right-click the controller to project the structure of the machine"`（`en_US.lang:305`），但 `MachineProjector.java:43-61` 中**没有任何 `player.isSneaking()` 判断**，且限定 `worldIn.isRemote`。此处记录为旧版文档/实现漂移。

7. **新版自创：KubeJS 配方 schema 桥（旧版走 CraftTweaker/Zen）**
   - 新版 `kubejs/ModularMachineryKubeJSPlugin.java:9-15` 注册 `modular_machinery_reborn:machine` 的 JSON recipe schema。
   - 旧版对应的脚本暴露是 Zen/CraftTweaker：`MachineUpgradeBuilder`（`@ZenClass("mods.modularmachinery.MachineUpgrade")` 等）、`SmartInterfaceType`（`@ZenClass("mods.modularmachinery.SmartInterfaceType")`）、`SmartInterfaceData`（`@ZenClass("mods.modularmachinery.SmartInterfaceData")`）。这是整合方式的整体替换，不属于迁移。

---

## 五、关键发现

1. 本审计领域内**没有任何一项达到"语义等价迁移"**：新版 17 个 Java 文件共 567 行，只有 6 个裸物品注册（蓝图/模块铀/构造工具/投影器/红石信号器/扳手）与注册名、堆叠上限被沿用，其余全部为外壳或缺席。
2. 升级系统是彻底的零迁移：旧版 `RegistryUpgrade` 的双表结构（按类型名 + 按物品）、`UpgradeType` 的机器兼容白/黑名单、`MachineUpgrade → DynamicMachineUpgrade → Simple*Upgrade` 三层类树、事件处理器映射、`TileUpgradeBus` 的槽位/堆叠合并/自定义 NBT，以及 CraftTweaker 的 `MachineUpgradeBuilder` 全部不存在。
3. 并行控制器的完整算法链在新版归零：旧版按 `NORMAL(4)/REINFORCED(16)/ELITE(64)/SUPER(256)/ULTIMATE(512)` 五级上限供给并行数，并由 `TileMultiblockMachineController` 以机器级 `maxParallelism` 截断、经 `ActiveMachineRecipe` 传进 `RecipeCraftingContext` 逐需求类型收敛；新版 grep `parallel` 零命中。
4. 智能接口、工厂控制器多线程批量生产、装配系统三大子系统在新版均"不存在"：旧版分别是"跨机器浮点参数桥"（`SmartInterfaceData.value`）、"核心线程 + 动态线程池 + 异步配方搜索 + ForkJoin 顺序执行器"、以及"从玩家背包扣除材料自动搭机器"。
5. 命令层完全缺失：旧版 6 条命令（`/mm-syntax`、`/mm-hand`、`/mm-get_blueprint`、`/mm-performance_report`、`/mm-reload`、`/mm-reload_client`，全部权限等级 2）在新版无任何 `RegisterCommandsEvent` 监听，`lang` 中也无 `command.` 键。
6. 新版退化为硬编码结构（控制器周围 8 格铁块环）与字符串匹配配方（`"basic"` / `"machine_controller"`），并且新增的 8×4+8×2 个分级仓室全部是只发提示消息的空壳，且不参与结构判定——这是"有物品外壳但无行为"的最集中体现。

---

## 六、不确定项

1. **任务书给的 `hellfirepvp/modularmachinery/common/upgrade/` 与 `common/upgrade/registry/` 目录在旧版仓库中不存在**（已用 `Test-Path` 确认 `False`）。本报告按 `github/kasuminova/mmce/common/upgrade/` + `registry/` 核查；若任务书另有所指（例如其它分支或未检出目录），该部分结论需重新核对。
2. **任务书给的 `hellfirepvp/modularmachinery/common/tile/`（单数）不存在**，实际为 `common/tiles/`（复数）。本报告按 `tiles/` 核查。**因此任务书要求的"旧版还有 tiles/ 包，两个都要查"只完成了一侧**：另一个（不存在的）`tile/` 包无法核查。
3. **`MachineStructure.java:19-21` 仅接受 `ITEM_INPUT_HATCH/ITEM_OUTPUT_HATCH/ENERGY_INPUT_HATCH/FLUID_INPUT_HATCH` 四个无分级方块 ID**；我没有逐一验证新版分级仓室方块在游戏内是否真的完全无法参与结构判定（只做了静态代码阅读），需要一次运行期验证。
4. **`MachineHatchBlock` 的注释声称能力路由"将在下一个移植切片接入"**，我无法确认这是否已由其它未纳入本次审计范围的分支/提交实现。
5. **新版 `MachineControllerBlockEntity.findRecipe` 的 `r.machine().equals("basic") || r.machine().equals("machine_controller")` 字符串条件**，其对应的配方 JSON 语义（`machine` 字段的含义与取值域）我只读了 `recipe/MachineRecipe.java`（37 行）之外的调用点，未逐字段核对配方反序列化逻辑，故不判断其是否等价于旧版 `DynamicMachine` 匹配。
6. **旧版 `RegistryItems.prepareRegister` 生成的注册名是「类名小写」推断**（`ItemBlueprint` → `itemblueprint`），我据此认定与新版 `ModItems.java:12` 的同名注册一致；我没有实际启动旧版或在旧版 `lang` 中逐条核对 `item.itemblueprint.*` 键（`en_US.lang` 中未搜到该前缀的 item 键），因此"注册名完全一致"这一条属于**推断**而非直接证据，但`machine_projector`（`MachineProjector.java:36` 显式 `setRegistryName`）一条是直接证据。
7. **旧版 `redstonesignal` 与 `wrench` 两个物品**：我在旧版 Java 源码中 grep `redstonesignal`、`ItemWrench`、`ItemsMM.wrench` **均零命中**，因此无法确认这两个物品在旧版中的实现类与行为。新版 `ModItems.java:16-17` 注册了它们，本报告未对其做迁移判定（既未列入已迁移也未列入未迁移的实质条目）。
8. **旧版 `ItemBlockMEMachineComponent.java` 与 `ItemBlockCustomName.java` 的完整行为**我只做了文件枚举与 `ItemBlockMachineComponent.java` / `ItemBlockController.java` 的完整阅读，未逐行读完后两者（本次审计优先级较低）。
9. **新版不存在 `RegisterCommandsEvent` 监听**这一结论来自对 `modular-machinery-reborn/src` 的 grep（`command|RegisterCommands|LiteralArgumentBuilder|SimpleCommandExceptionType` 零命中）；我未检查 `META-INF/mods.toml` 与 `META-INF/services/` 是否引入了其它入口类（`META-INF/services/mezz.jei.api.IModPlugin` 已见，属 JEI）。
10. **新版 `MachineProjector` 等物品的贴图/模型完整性**：`assets/modular_machinery_reborn/models/item/` 下存在 `itemblueprint.json`、`itemconstructtool.json`、`itemmodularium.json`、`machine_projector.json`、`redstonesignal.json`、`wrench.json`，但 `textures/item/` 下只有 `itemblueprint.png`、`itemmodularium.png`、`machine_projector.png`、`redstonesignal.png`、`wrench.png`——**没有 `itemconstructtool.png`**。我未验证该物品在游戏内是否因此显示为紫黑格。
