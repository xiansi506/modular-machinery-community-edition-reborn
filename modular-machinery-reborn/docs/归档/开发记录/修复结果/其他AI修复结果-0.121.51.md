> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Q1修复完成：当前及最近整版离线通过0.121.52 / 协议12，第九批四项指定范围收口。累计84项完成、5项部分、33项待处理。未实机/真实网络/GPU验收，未部署或推送；本项完成后停止。** 下方旧状态为历史。 [52修复记录](修复记录-Q1-0.121.52-2026-11-04.md)。本交接/失败记录保留供追溯，不重复执行。

> **2026-11-04第九批验收：当前0.121.51 / 协议12，P1/P2/Q2通过指定范围离线验收，Q1线程身份仍需返工；最近整版通过0.121.50。累计83项完成、5项部分、34项待处理。主任务暂停，未实机/部署/推送。** 下方旧状态为历史。 [报告](../验收记录/验收-第九批4项-2026-11-04.md) · [返工](../任务交接/返工交接-第九批Q1-2026-11-04.md)。ItemCopySafetyCheck以参数fixed正确运行时49/51均通过，原“既有失败”表述不成立；下文为交付时记录。

# 其他AI修复结果 — 0.121.51（第九批Q批，2026-11-04）

**结论：Q1/P06-09 与 Q2/P03-03 完成指定范围修复，标待 Codex 验收。** 起点 0.121.50 / 协议12，保留 P 批修复；HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。未实机、未部署/启动游戏、未动整合包/存档、未暂存/提交/推送、未创建 Release；累计账本不改，仍为 80/5/37。

## Q1 / P06-09 — 同一快照反复接收保留线程身份

**完成。** 按当前 0.121.50 代码复现：load 清空集合后仍 `createdOrdinary++/createdCore++`，线程 save 无 ordinal；同一服务器快照第 2 次同步即令客户端普通编号漂移。

- `FactoryThread` NBT 增加可缺省 `threadOrdinal`；`FactoryEngine.save` 写普通/核心下一次分配高水位（即使当前无存活槽）。
- load 恢复真实槽身份和核心名称对应身份；同名现存核心保身份，删除后真正重建取新身份；普通清理留下的编号间隔不压缩。
- 旧 NBT 无新字段时按列表/预设顺序确定性迁移；缺失、非法、重复身份不创建相同身份，重复/非法改取下一个未用身份；普通身份为 `[0, MAX)`，核心 `<= -2`，`-1` 保留；threadNumber 转换不溢出。
- 计数器到上限时显式拒绝新建并记日志，不溢出、不静默复用。未以菜单/界面重排掩盖漂移，未重写调度器；P06-08/P06-12 未扩做。

复现/验证：`FactoryIdentityCorrectCheck` 在 0.121.50 类上失败于同一快照 replay 2；0.121.51 final class 全过。真实 getUpdateTag payload→onDataPacket/load 重复 10 次；真实 save/load 重复 10 次；普通 #1/#2 保留；清理后高水位新建 `#4`；核心同名保留/重建新身份；旧格式无字段、重复身份、MAX 高水位均通过。脚本状态、限制配方、activeCraft 支付/触发标记与冻结修改器由既有 `FactoryPersistenceAuditCheck` 前四段及 `FinalTickCheck` 覆盖。

## Q2 / P03-03 — 来源优先级按逻辑 ID 生效

**完成。** 升级层复现当前缺陷：数据包键与脚本文件键相同时，`putAll` 不移动插入位置，配置别名 x3 反压脚本 x5。机器层检查确认脚本已有最终应用路径，保留；同层数据/配置冲突与 source JSON 一致性纳入验证。

- 机器 `MachineLoader.apply` 先数据包、后配置解析；同一逻辑 ID 冲突输出来源与赢家告警，赢家定义与客户端重建 JSON 同源。高优先级坏文件跳过后低层有效声明保留，语义与既有失败跳过一致。
- `UpgradeLoader` 类型与载体映射均按 **脚本 > 配置 > 数据包** 三层读取；层内保留“后解析有效声明胜出”。仍分两遍：先读全部本次类型，再解析载体，不复活已删类型；载体映射对应本次类型赢家。
- 未改注册/重载事务，不处理 P03-02/P03-14；变量文件分层与原子合并保护未回退。

复现/验证：`SourcePriorityCorrectCheck` 在 0.121.50 类上失败于 `script type x5 wins...`；0.121.51 final class 全过。真实 `MachineLoader.apply`/`UpgradeLoader.apply`：数据→配置→脚本与删除逐层回落；机器 winner source JSON 同源；升级类型 x2/x3/x5 得 x5 且载体对应赢家；同层数据正反顺序；坏文件跳过；临时 CONFIGDIR finally 恢复。

## 回归与既有失败说明

- H1 六套、`LateReplyCheck`、`CollectedDraftCheck`、`ReworkComboCheck`、`FinalTickCheck`、`ControllerExtraCheck`、P2 饱和夹具通过。
- `ItemCopySafetyCheck` 失败于 `baseline loses private output NBT`；`FactoryPersistenceAuditCheck` 前四段通过，但 `changedDefinitionFlags` 失败。两处在 **0.121.49 接受类快照与 0.121.50 基线均同样失败**（`regress49-baseline-*.log`），不是本批改动引入。
- 构建日志与退出码：`_audit/ninth-ai-4tasks-0.121.51/logs/build-0.121.51.log`；`test NO-SOURCE` 如实记录。

## 产物与边界

- 340 Java / 568 class 无增删；相对 0.121.50 仅白名单 4 份源码：`FactoryEngine.java`、`FactoryThread.java`、`MachineLoader.java`、`UpgradeLoader.java`。
- JAR 1,641,030 字节，SHA-256 `9D345B983CCC8C8EE56B2C54F4EC11C8320EF032954248B29D74B2A0DF4A6F00`；`build/libs`、reobf 与归档一致。归档 `releases/modular-machinery-reborn/v0.121.51/`。
- 221 JSON/mcmeta 可解析；中英语言键各 566 且键集一致；`mods.toml` 0.121.51。
- 未做真实 Netty、磁盘区块、Rhino、完整 Forge reload或实机；Q1 网络幂等为真实 BE payload + 受控客户端 Level。Q1 新增字段只写入既有线程 NBT 复合标签与引擎工厂 NBT，不改 ModNetwork/消息字段/协议号。
