# 0.121.3：控制器预声明冲突与动态段卡死风险修复（2026-11-02）

修复P03-01：声明预扫描先验证普通/工厂控制器完整注册计划。过滤占用machine_controller、factory_controller、parallel_controller的声明；不同声明生成相同控制器ID时双方均不注册专属控制器并记录文件/机器ID，避免任意选赢家或自动改名。完全相同的MachineRef合并一次；按文件路径排序读取。定义加载独立于此预声明，不等于删除机器配方。

修复P03-06：可重复动态段必须非零步进，schema给出带模式名/来源的错误，typed构造同样校验；公开matchesSize的零步进实参在读世界之前拒绝，重复计数在达到上限时先拒绝再递增，避免int回绕。maxSize为0的零重复段仍允许零向量。未新增全局结构数量上限，超大非零步进配置规模仍需作者控制。

修前/后3组专项：原生DeferredRegister冲突，实际schema/typed/runtime零步进门禁，原生0/1/2段/超过上限/收尾段/缺少尾段对照；未真正跑20亿次。另两个新JVM分别使用0.121.2生产类与本次正式构建类，实际ModBlocks从sandbox机器配置初始化：旧代码抛重复注册异常，修后成功保留2普通/1工厂/2兼容别名；没有完整FML构造/事件或游戏启动。构建后的独立production-classes再执行专项通过。

Java17 clean build/reobf成功（44s）。ForgeGradle离线配置阶段尝试获取asset index被网络权限拒绝，保留完整日志，使用已有缓存仍完成构建；没有下载资源、绕过权限或启动游戏。中文使用说明及注意事项已补充新的定义校验规则。所有旧版本和既有工作保留。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.3`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.3.jar`，1615003字节，SHA-256 `44C7276479F96A1DD464BA77835ABBF2FD6134FBCB39270DF9635E799C35B75D`；归档JAR与build/libs原下划线命名JAR字节一致。
