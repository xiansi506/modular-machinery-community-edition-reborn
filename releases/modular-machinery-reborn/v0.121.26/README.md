# 0.121.26：显式气体ID按命名空间精确解析（2026-11-02）

修复 **P04-09**：气体字段带命名空间时，只解析其指定注册ID，不再让MMR命名空间同路径气体优先遮蔽。裸名称保留原有MMR→Minecraft默认命名空间顺序；不新增自动Mekanism补全。有效原生Forge气体注册别名仍可使用，需求种类历史别名和化学资源身份分开处理。更新源Javadoc、配方指南和英文状态。

0.121.25完整生产类基线在真实Mekanism API气体注册表中注册同路径MMR气体，复现明确mekanism:oxygen实际解析/副本却持有MMR气体。修复专项在Mek/MMR/附属/Minecraft四方同路径碰撞下核对显式ID，覆盖单边存在、空白trim、裸名称既有顺序、资源别名、旧需求种类别名、gas/gas_pertick输入输出及副本/真实FriendlyByteBuf身份和元数据。显式不存在的命名空间及EMPTY化学物不再因MMR同路径资源而被接受。原有正常gas解析/校验组合同时通过。

构建后完整生产类复跑专项，并运行原生HybridGasTank事务回归：共享预算/selector别名、规划不变更实仓、并发类型变化拒绝、晚期false/异常恢复原生气体、气液互斥与同事务切换、原生回退、整型边界/并行。新增实际解析→配方start→真实HybridGasTank支付：同路径MMR气体1000保持不变且拒绝开工；换为明确指定Mek气体后恰好支付1000。旧GasTransactionAuditCheck的duplicateBoundary缺陷观察方法未被误用为成功回归，该别名端口边界仍待处理。

Java17 clean build/reobf成功（47s）；专项、GasSettlementRegression均退出0。Gradle test为NO-SOURCE，协议12。注册表/原生仓储/codec真实，新增气体为审计注册对象；仓口没有真实Level，未做完整FML/真实附属模组注册生命周期、管道运输、游戏/网络或GPU验收。不部署、暂存、提交、推送。

用户在本批构建期间要求“手头的工作完成后就停止，之后告诉我一下还有那些漏洞没修”。本批归档、更新剩余事项清单后暂停，不开始已规划的流体/气体amount精确校验，不继续耗额度。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.26`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.26.jar`，1625761字节，SHA-256 `8D64030E367E5404EE38E64952A63A67434A329A5D3C604B61AFD38ECDAD080D`；归档JAR与build/libs原下划线命名JAR字节一致。
