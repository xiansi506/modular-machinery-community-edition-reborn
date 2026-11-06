# 0.121.18：普通仓口加载边界与输入组刷新修复（2026-11-02）

修复 **P07-14普通仓口自动IO路径**及 **P07-15**。物品输入/输出和能量输出仓查询邻居前检查世界高度及已加载状态；实际区块查找继续明确FULL/create=false，区块状态在检查后改变也不绕回强制获取。输入组有效值改变时，在保存/邻居通知前按既有InputDependencyIndex使相关控制器端口快照失效，下一次tick定向刷新分组而不完整重扫结构。

基线实际三类仓口tick在未加载邻块发出FULL/create=true；实际HatchGroupUpdatePacket.apply与普通/工厂controller形成后分组1/2仍混用材料。修复6组专项覆盖正常加载物品/NBT守恒、反面能力、槽限、拒绝、同tick/节流，正负边界与检查后状态变化/空区块、高度边界，实际分组包/门禁/两种控制器不混料及强制重查对照，能量总128预算与短接收退款，组/标签视图及资源别名。Java17 clean build/reobf成功（1m11s），完整生产class复跑全部专项并执行菜单失效/转移/原生掉落三组相关回归，均退出0。Gradle test为NO-SOURCE。

加载探针只观测实际请求参数，不是已运行真实区块票据/生成/加载或TPS测量。Level/玩家/菜单为内存壳，未完整游戏/活跃NeighborNotify/网络/GUI/磁盘验收。**P07-14脚本块查询/其他处理器的相同读取问题仍待修**，本批不宣称全部入口已处理；P07-09卸载后旧仓口引用也独立待修。不部署/启动游戏/暂存/提交/推送，教程补加载与分组行为。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.18`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.18.jar`，1622345字节，SHA-256 `DC1F5B39AFE2A56C4ACA4B6867B37C9299181E69E4043DAD6A062F68CBE0DEE8`；归档JAR与build/libs原下划线命名JAR字节一致。
