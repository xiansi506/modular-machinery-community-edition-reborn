# 0.121.19：运行失败事件使用当前tick阶段诊断（2026-11-02）

修复 **P06-06**并统一普通运行失败事件的同类诊断：MachineRecipe.tickFailure在当前执行tick的虚拟账本中检查逐tick及已到期延迟需求，使用当前分组/selector视图，不再次要求已付开工原料。数量乘并行溢出继续转为quantity-range，不抛诊断异常。普通与工厂失败事件读取该阶段原因；若再次预留均可付但实际结算失败，给通用tick结算失败键，不猜测某个外部处理器的内部拒绝细节。中英文新增该键，现各566项，键集合一致。未更改包结构或协议12。

基线实际工厂缺FE且煤已付时报告缺煤，纯FE配方报告null。14组修复专项覆盖普通/工厂普通/核心三类的有/无开工费能源原因、共享流体第二需求不足、延迟带标签锁组输入、正向预检后实际拒绝写入、两并行运行期流体溢出；核对原库存/已付款和原生回退，不因读取原因再支付。构建后完整生产class复跑；相关FactoryFailureRegression覆盖RESET/STILL/DECREASE、检查/逐tick两阶段取消及destructRecipe双向覆盖，另最终tick和实际Rhino回归均退出0。Java17 clean build/reobf成功（1m14s），Gradle test为NO-SOURCE。

新诊断是事件载荷，不声称已同步到控制器开工失败界面字段；事件Javadoc及高级脚本指南已纠正区分。诊断为只读重新预留，无法证明任意状态性外部处理器的确切执行失败原因，故保留通用结算失败。Level和拒绝写入为内存契约夹具，未实机/完整FML、真实网络、GUI或Rhino监听器逐事件验收。不部署、暂存、提交、推送。P05-01任意受限外部端口回滚等仍待修。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.19`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.19.jar`，1622822字节，SHA-256 `8A0A5E33873B2BB61629F1C0FB99B71355A05D9A44994AB764960FB7AF7A2FC0`；归档JAR与build/libs原下划线命名JAR字节一致。
