# 0.121.20：区块生命周期刷新机器仓口引用（2026-11-02）

修复 **P07-09**：位置依赖索引同时索引仓口所在区块，服务端 ChunkEvent.Load/Unload 只使已订阅该区块的控制器端口集合失效。下一次加工结算前重新收集，移除的方块实体及旧原始 handler 不再参与资源结算；加载后重新收集替代实体。索引保留弱世界/控制器引用和事件合并，不查询或强加载世界，不逐 tick 扫描所有控制器。

0.121.19 完整生产类基线复现原始旧仓口在 native LevelChunk.clearAllBlockEntities 后仍支付并接收产物。修复专项使用实际 ChunkEvent、原生区块清理及仓口存档/重建方法，覆盖普通机器、工厂线程、核心线程各有/无已付开工费的六组组合；已付延迟材料仅在新加载库存支付一次，旧库存不变、产物不重复。另覆盖负坐标、跨世界、同区块多个监听者、反复通知、重新订阅/取消、实际 ClientLevel 事件类型门控和编译后 Load/Unload 监听委托。构建后完整生产类复跑专项及 LoadedNeighborCheck、TickFailureCheck 均退出0；Java17 clean build/reobf成功（46s），Gradle test为NO-SOURCE。

不新增“卸载无限暂停”规则：缺资源时仍执行既有失败动作，周期结构复查也可能取消批次；持续加工仍需整个结构加载。使用说明已补充。夹具直接调用事件处理函数，虽检查注册字节码委托，但未启动完整 Forge 事件总线；ServerLevel 为内存壳，未做真实区块票据/磁盘加载、第三方后端生命周期、多人网络或游戏验收。P07-07 结构变化 START 清空问题另行修复。协议12，不部署、暂存、提交或推送。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.20`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.20.jar`，1623801字节，SHA-256 `F399A67C44B3E8FB71E8767E77E3FE56295E0FEA5F0923D7142D416699F8DDD7`；归档JAR与build/libs原下划线命名JAR字节一致。
