# 0.121.14：原生仓储事务候选状态规划修复（2026-11-02）

修复 **P05-02的已复现原生仓储路径**：精确原生ItemStackHandler与FluidTank按虚拟库存/容量规划操作；final LongEnergyStorage按虚拟能量与原生maxReceiveL/maxExtractL限制规划。支持在同一事务中先释放空间再接收/换种、先插入再提取，实际提交仍按顺序执行并检查真实结果和源快照。原生超容量流体视图填入拒绝且不截断原存量。

严格限制：只有精确原生类或本模组final存储使用上述规则，未知外部处理器、任意子类、单向/方向包装器仍走实仓SIMULATE，不删除权限检查。它们可能仍保守拒绝依赖候选变化的组合，需独立事务适配契约，不能宣称任意外部能力P05-02全面解决。P05-01通用受限回滚问题仍待修。

基线原生物品/流体/long三路重现候选释放空间仍拒绝；修复6组专项覆盖顺序交换、反向提取、只读预检、真实commit、long速率及大于int存量、子类禁止插取与单向能源权限、真实配方canTick/tick、晚到false后原生准确回退、源版本变化拒绝保留外部修改、超容量拒绝无损。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；正式完整生产class独立快照复跑，另执行最终tick、存档校验、物品完整性、实际Rhino、结构重绑定、红石六个相关入口回归，全部退出0。

class快照拷贝开始时构建尚处于编译末段，构建完成后已逐一验证全部568个class文件集合及字节哈希与快照一致，记录snapshot-validation.txt；不以未完成快照冒充验证完成。未运行实际游戏/FML完整启动、存档磁盘/区块/管道/联机/GPU。中英文状态、限制和交接同步；不部署、暂存、提交或推送。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.14`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.14.jar`，1621021字节，SHA-256 `70552208A3E74A2F1DD2EB9032A2816BB22AD684477CBF8D2CF0B64DFA5EF36F`；归档JAR与build/libs原下划线命名JAR字节一致。
