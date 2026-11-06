# 0.121.17：工厂派单跳过无法接单的空闲槽（2026-11-02）

修复 **P06-05**：当前可开工候选无法进入首个空闲槽时，不再直接停止派单，而是尝试其余现有空闲槽/通用核心fallback，并在普通线程额度允许时尝试一个新槽。各槽的配方限制、修改器、核心身份和总线程上限照旧生效；成功派单后重算候选。新槽完全预检失败且没有显式脚本状态时移除空壳，避免逐tick堆积；检查回调有意写入的配方列表或自定义状态保留。

基线真实工厂控制器复现受限slot0阻塞可用slot1、移除slot0限制才恢复。修复9组专项包含后续空闲槽、新槽额度、全限制且满额拒绝、首槽修改器不可付、缺电重复预检无空壳增长、检查否决后的配置保留及恢复、昂贵通用核心不阻塞新普通槽、原多配方即时重开/资源总账、输入组锁/共享输入预算。正式完整生产class再次执行全部专项，并运行最终tick、存档签名、实际Rhino和升级刷新四个相关入口，均退出0。Java17 clean build/reobf成功（1m16s），Gradle test为NO-SOURCE。

此改动处理创建者已公开的普通线程配方限制与核心fallback调度，不扩展新调度策略。Level为内存壳、实际controller/host/engine/thread及Forge仓储均执行；线程配置为直接Java API，未验收Rhino setRecipes转换、真实Forge世界/网络/GUI或性能TPS。保留源前快照与原失败日志，不部署/暂存/提交/推送。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.17`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.17.jar`，1621958字节，SHA-256 `C192F9B078E9DEAA1BD06075B1F4DD978418990F76468E4F4AAA2FDFFD686B53`；归档JAR与build/libs原下划线命名JAR字节一致。
