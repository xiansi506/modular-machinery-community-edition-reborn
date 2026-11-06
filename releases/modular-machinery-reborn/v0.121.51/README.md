# v0.121.51 — 第九批 Q 批（P06-09 / P03-03，2026-11-04）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.51.jar` **1,641,030 字节**，SHA-256 `9D345B983CCC8C8EE56B2C54F4EC11C8320EF032954248B29D74B2A0DF4A6F00`。起点 **0.121.50**。**标待 Codex 验收。**

## 本版内容

- **Q1 / P06-09**：工厂槽保存/恢复真实逻辑身份。每个线程 NBT 增加可缺省 `threadOrdinal`；引擎 NBT 增加普通/核心下一次分配高水位。旧存档无字段时按列表/预设顺序确定性迁移；非法或重复身份不发新槽，改取下一个未用身份；普通非负、核心 `<= -2`、`-1` 保留，显示编号不溢出。同一服务器快照重复 `onDataPacket/load` 幂等；清理后的编号间隔不压缩；同名现存核心保身份，真正重建核心取新身份；计数到上限拒绝新建，不溢出、不静默复用。
- **Q2 / P03-03**：机器与升级逻辑 ID 来源显式分层 **脚本 > 配置 > 数据包**。机器 `apply` 先数据包、后配置，赢家定义与客户端重建 JSON 同源；升级类型与载体映射都按三层读取，层内仍“后解析有效声明胜出”。冲突输出可定位来源与赢家告警；高优先级坏文件跳过，后续正常声明不污染。升级仍两遍：先读全部本次类型，再解析载体，不复活已删类型。

## 验证

- Q1 夹具：0.121.50 类上同一快照第 2 次同步即漂移；0.121.51 final class 全过。真实 getUpdateTag payload→onDataPacket/load 重复 10 次；真实 save/load 重复 10 次；清理后高水位新建 `#4`；核心同名保留与删除重建新身份；旧格式无字段、重复身份、`MAX` 高水位均受控。
- Q2 夹具：0.121.50 类上脚本 x5 被配置别名 x3 反压；0.121.51 final class 全过。真实 `MachineLoader.apply` 与 `UpgradeLoader.apply`：数据→配置→脚本、删除逐层回落、同层数据正反顺序、坏文件跳过、机器赢家 source JSON 同源、升级 x2/x3/x5 得 x5 且载体对应本次赢家。
- 回归：H1 六套、`LateReplyCheck`、`CollectedDraftCheck`、`ReworkComboCheck`、`FinalTickCheck`、`ControllerExtraCheck`、P2 饱和夹具通过。`ItemCopySafetyCheck` 与 `FactoryPersistenceAuditCheck` 各有一处在 0.121.49 接受类上同样失败的旧断言（与 0.121.50 相同），已保留基线对照，不计为本批回退。
- 构建：Java 17 `clean build --offline --no-daemon` 成功，含 reobf；`test NO-SOURCE` 如实记录。

## 边界

未实机、未连接真实网络/完整 FML/磁盘区块；Q1 网络幂等使用真实 BE payload 与受控客户端 Level，不是 Netty 联机验收。高水位耗尽策略为显式拒绝并记日志，不是动态换号。Q2 不重写全局失败重载事务，不处理 P03-02/P03-14。未部署、未动整合包/存档、未提交/推送、未创建 Release。证据 `_audit/ninth-ai-4tasks-0.121.51/`。

## 构建与归档

340 Java / 568 class 无增删；相对 0.121.50 仅白名单 4 份源码改变。JAR 973 entry / 568 class；221 JSON+mcmeta 全可解析；中英语言键各 566 且键集一致；`mods.toml` 0.121.51。`build/libs`、`build/reobfJar/output.jar` 与归档逐字节一致。旧归档未改写。
