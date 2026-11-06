# 0.121.24：拒绝空流体与空气升级载体的默认注册表回退（2026-11-02）

修复 **P04-07** 及已在P06-c2a归入同根因的升级载体入口：Forge注册表查不到键时会返回EMPTY流体或AIR物品，而非null。流体JSON读取器现在拒绝null/Fluids.EMPTY；升级映射拒绝null/Items.AIR，均带原始名称与定义上下文。显式minecraft:empty/air也不可作为这些实际资源。按解析结果校验，而非只看containsKey，从而保留真实Forge注册别名、裸Minecraft名称和有效现代完整ID。

0.121.23原生注册表基线复现未知流体/显式空流体成功加载为空需求、未知升级载体成功映射AIR。修复专项覆盖无效ResourceLocation/不存在键/显式空资源的定位异常，16组水/岩浆/裸名称/实际注册别名的输入输出与逐tick/一次性组合、执行副本与真实FriendlyByteBuf往返、固定/动态升级载体别名。实际控制器加工扣水1000→750、产出250岩浆及完成物品。另使用审计专用空config路径直接调用UpgradeLoader.apply，确认合法映射被错误映射替换时，旧钻石和AIR均无目标，独立红石映射及当前类型正常保留。

Java17 clean build/reobf成功（47s），构建后完整生产类复跑专项及CandidateStateCheck、SavedDefinitionCheck退出0；Gradle test为NO-SOURCE，协议12。配方指南和英文状态已更新。原生注册表/别名、读写包和库存真实，Level为内存壳；apply直接调用，非完整FML协调器、真实服务器重载、网络传输或游戏验收。不部署、暂存、提交、推送。其他数值校验问题继续处理。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.24`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.24.jar`，1625115字节，SHA-256 `AB2CBAF3284E0C7DD823F1D0514782A91F458E1ADAB978A4AC381DA9583105E7`；归档JAR与build/libs原下划线命名JAR字节一致。
