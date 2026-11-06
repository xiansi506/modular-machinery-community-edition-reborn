# P12-d：原生注册表与 JEI 蓝图索引验收

2026-11-01；0.121.0/f94ba299，仅验收。工件`_audit/code-survey-0.121.0/review-p12-d-jei-registry/`，3组PASS，退出0。补足P10-b8留给P12的实际物品注册/catalyst索引证据。

## 实际执行

当前ModBlocks.BLOCKS/ITEMS、ModItems.ITEMS→实际DeferredRegister监听器→原生RegisterEvent→实际Forge注册表。RegistryObject取得真实BlueprintItem、控制器BlockItem，蓝图注册名正确；本批没有把物品替换成原版纸/铁块。显式解冻测试JVM注册表，所用Native Forge EventSubclassTransformer仅补RegisterEvent/NewRegistryEvent缺失的启动变换，两个变换类名写入BRIDGE日志。

实际插件registerItemSubtypes→原生SubtypeRegistration/SubtypeManager/StackHelper，再到原生IngredientManager、RecipeCatalystRegistration、RecipeMap。给定两个机器分组快照，实际登记每组控制器+绑定蓝图，另保留结构类别catalyst。renderer为未调用的受控接口，JEI助手未用于此链。

## P10-20（P3）：蓝图无关NBT参与机器类别匹配

实际注册的蓝图绑定review:a，改名后BlueprintItem.machineOf仍是review:a；当前useNbtForSubtypes把整个NBT用于Recipe上下文身份，原生索引中未改名蓝图能找到自身加工类别，改名副本失去该类别；resetHoverName后恢复。不同机器review:a/review:b的正常区分通过。

旧1.12.2实际ModIntegrationJEI.registerItemSubtypes只返回关联机器注册名，不纳入display等无关标签。建议换成只返回有效机器ID的子类型解释器，并明确空白/无效蓝图处理；补显示名、自定义标签及不同机器对照。

当前确认的是原生类别查询后端，不是完整R/U按键/全局界面演示；结构类别或其他查询退路不等同于原机器加工类别仍匹配。未修，不影响控制器读取蓝图绑定ID本身。

初次夹具误把“全部catalyst数”断言为4，忽略额外结构类别；保留原失败日志，修正为每机器加工类别合计4，并使用完整索引验证。不是因生产漏注册导致该初次失败。

边界：未运行完整模组构造、FML注册顺序、其他注册表/可选模组、JEI启动与GUI、资源烘焙或GPU；分组快照由夹具提供。只写审计工件，不构建或部署。
