> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Q1修复完成：当前及最近整版离线通过0.121.52 / 协议12，第九批四项指定范围收口。累计84项完成、5项部分、33项待处理。未实机/真实网络/GPU验收，未部署或推送；本项完成后停止。** 下方旧状态为历史。 [52修复记录](修复记录-Q1-0.121.52-2026-11-04.md)。本交接/失败记录保留供追溯，不重复执行。

> **2026-11-04第九批验收：当前0.121.51 / 协议12，P1/P2/Q2通过指定范围离线验收，Q1线程身份仍需返工；最近整版通过0.121.50。累计83项完成、5项部分、34项待处理。主任务暂停，未实机/部署/推送。** 下方旧状态为历史。 [报告](../验收记录/验收-第九批4项-2026-11-04.md) · [返工](../任务交接/返工交接-第九批Q1-2026-11-04.md)。ItemCopySafetyCheck以参数fixed正确运行时49/51均通过，原“既有失败”表述不成立；下文为交付时记录。

# 其他AI修复结果 — 0.121.50（第九批P批，2026-11-04）

**结论：P1/P04-16 与 P2/P06-19 完成指定范围修复，标待 Codex 验收。** 起点 0.121.49 / 协议12 / HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`；Q 批未在本文件销号。未实机、未部署/启动游戏、未动整合包/存档、未暂存/提交/推送、未创建 Release；累计账本不改，仍为 80/5/37。

## P1 / P04-16 — 数字接口执行副本保留自定义失败文案

**完成。** 根因：`MachineRequirementParser.copy` 只给 `ItemRequirement` 走 definition copy；数字接口需求经 wire 往返，而该类型 wire 正文不携带 `notEqual`，构造器归一后服务端执行副本覆盖文案丢失。

- `InterfaceNumberInputRequirement` 新增 `definitionCopy()`，保留原始可空 `notEqual`。
- `MachineRequirementParser.copy` 为该类型走复制分支，复制 selector-tag、`parallelize-unaffected`、scriptOptions；触发状态不继承。
- null/空/空白副本不把 `notEqualKey()` 回退固化成显式覆盖，继续在实际端口读取机器类型文案或默认键。
- JSON 解析仍传 null；wire 正文字段、顺序与协议 12 未变。**客户端 wire 往返仍无程序化覆盖字段**，此边界如实保留。

复现/验证：`InterfaceCopyCorrectCheck` 在 0.121.49 接受类快照失败于 `definition copy preserves the explicit override`；0.121.50 final class 通过。覆盖显式/null/空/空白、机器类型自定义文案、缺接口、范围匹配、原需求→copy→再次copy、真实 `executionCopy`/`startFailure`、副本选项隔离、不继承 triggered、wire 字节不变。`ItemCopySafetyCheck` 未复用为通过证据，因同一源码夹具在 0.121.49 接受类上也失败（见下）。

## P2 / P06-19 — 工厂进度不回绕

**完成。** 按当前代码重新复现：`ActiveCraft.advance()` 为 `progress++`；真实脚本入口 `MachineControllerAccess.ThreadAccess.setProgress(MAX)` 后下一 tick 回绕为 `-2147483648`。49 已宽化的预判未重复改动。

- `advance()` 改为饱和推进：`MAX-1` 可到 `MAX` 并进入既有 final-tick/finish 收尾；`MAX` 本身进入既有合法收尾流程。
- 未直接看到 progress≥duration 跳过未支付需求；未处理 P06-11。START/END 取消、失败重试、输出容量、final-tick-paid 语义保留。

复现/验证：`FactoryProgressSaturationCheck` 在 0.121.49 类上失败于 `progress already at MAX ... -2147483648`；0.121.50 final class 通过。普通与核心线程各覆盖 duration `1`、`MAX-1`、`MAX`；阻塞输出先保持不额外 IO，解除后一次完成；END 取消与重启；真实 save→restore→pending tick 完成一次。普通控制器本批未改，仅作为对照语义由既有 `FinalTickCheck` 覆盖。

## 回归与既有失败说明

- `FinalTickCheck` 通过；`ControllerExtraCheck` 通过（D3/E3 全部 PASS）。
- `ItemCopySafetyCheck` 失败于 `baseline loses private output NBT`；`FactoryPersistenceAuditCheck` 前四段通过，但 `changedDefinitionFlags` 失败。两处均在 **0.121.49 接受类快照上同样失败**（对照日志 `regress49-baseline-*.log`），不是本批改动引入。P2 要求的保存/恢复另由本批 `saveRestore` 用例通过。
- 构建日志、退出码与 Java17 命令保存在 `_audit/ninth-ai-4tasks-0.121.50/logs/build-0.121.50.log`；`test NO-SOURCE` 如实记录。

## 产物与边界

- 340 Java / 568 class 无增删；相对 0.121.49 仅白名单 3 份源码：`InterfaceNumberInputRequirement.java`、`MachineRequirementParser.java`、`FactoryEngine.java`。
- JAR 1,638,259 字节，SHA-256 `0DAA0BC3D452CB8C78272391D6DAC8FD740988BB4FA5FBA3DA16FF0146BFA484`；`build/libs`、reobf 与归档一致。归档 `releases/modular-machinery-reborn/v0.121.50/`。
- 221 JSON/mcmeta 可解析；中英语言键各 566 且键集一致；`mods.toml` 0.121.50。
- 未做真实客户端/网络/Rhino/完整 Forge reload/实机；P2 极限进度由真实脚本入口受控设置，不循环数十亿 tick。旧 1.12.2 对照确认该类旧实现无服务端 copy 保留契约可照搬，本项按本版 ItemRequirement 复制族处理。
