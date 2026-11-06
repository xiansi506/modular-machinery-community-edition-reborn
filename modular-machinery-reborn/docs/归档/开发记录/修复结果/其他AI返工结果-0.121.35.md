> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI返工结果：0.121.35（第三批E3/P06-18单项返工，2026-11-03）

执行 `docs/归档/开发记录/任务交接/返工交接-第三批E3-2026-11-03.md`，只处理 E3/P06-18，**完成，标待Codex验收**。其余7项验收通过的改动（D组全部、E1/E2/E4）全部保留，尤其 D3 的 int 存读档。未部署/启动游戏、暂存/提交/推送、reset/clean；HEAD 保持 `f94ba299`。

## 回归与修复

- **回归（Codex 验收确认）**：0.121.34 的 `startFailureKey()` 客户端分支在 ordinal 无失败/无效时无条件回退更新标签旧字符串，已知键的旧快照被当自定义键——菜单清除先到或无效 ordinal 时，8 个已知键各复现 1 处复活（16 处退化），0.121.32 无此行为。
- **修复**：回退仅对镜像字符串**不在 `START_FAILURE_KEYS` 已知表**（`failureIndex(full) == NO_START_FAILURE`）时生效；已知键完全服从菜单 ordinal 通道（清除/无效 ordinal 立即 null）。自定义接口键与魔法键可见性保留；自定义键清除的不可区分窗口如实保留（清除标签抵达后消失），未塞白名单、未扩协议、未加修订状态机；`failureCauseText` 与菜单八 ordinal 契约不变；`MachineControllerScreen.java` 无改动。

## 起点与证据

- 真实起点核对：验收指纹 `_audit/review-third-ai-8tasks-2026-11-03/current-source-hashes.json`（340 份）与当前源码**逐份一致**；0.121.34 控制器副本取自验收 `before-review/`（与当前源码 cmp 一致）存于 `third-ai-rework-e3-0.121.35/src-before-0.121.34/`。本次增量 diff（仅 startFailureKey 访问器一处，49 行）单独保存，不再混入 D3 改动；改前/改后 340 份源码哈希在 `hashes/`。
- **ReworkE3Check**（`_audit/third-ai-rework-e3-0.121.35/`，同一客户端 BE 贯穿全部场景，不每包新建对象）：合并 Codex `ControllerExtraCheck` 32 项已知键断言 + D3 四项（真实单线程启动/结算：恢复 40000 → 真实 tick 开工 1 普通线程、扣 1 煤、产出 1 石头；负 int NBT 归零），并补齐交接组合：已知A→B、自定义→已知、已知→自定义、自定义A→B、清除后再失败，各测标签先到/ordinal 先到两种顺序，外加魔法键真实 tick。**repro**（0.121.34 快照）55 项中 18 项回归按预期复现；**fixed**（0.121.35 快照）55/55。
- **Codex 验收夹具复跑**：`run_extra_controller.py`（0.121.35 快照副本 `run_extra_controller_rework.py`）SUMMARY **failures=0**——32 项已知键（含"known-key menu clear before tag returns null, actual=null"、"invalid ordinal normalizes to no failure, actual=null"）+ D3 四项全部通过。
- **全部 D/E 套件以 0.121.35 构建快照复跑**（副本在 `rerun/`，旧记录未覆盖）：D1 7/7、D2 8/8、D3 8/8、D4 11/11、E1 13/13、E2 15/15、E4 12/12、原 E3 套件 20/20，全部退出 0。
- 夹具首次失败保留：repro 后首轮 prebuild 曾因 stage 语义标记错误出现假 FAIL（prebuild 已是修复代码却按复现语义断言），修正后复跑；日志在 `checks-prebuild.log` 与修正后日志。

## 构建与归档

Java17 clean build/reobf成功（36s），Gradle test=NO-SOURCE。出货 `modular_machinery_reborn-0.121.35.jar` 1,630,036字节（build/reobfJar/output.jar 与 build/libs 字节一致），SHA-256 `48ADCEE784BF18CE5002E2F87420E23C91123C3AE078C0DE7423585A659131FB`，归档 `releases/modular-machinery-reborn/v0.121.35/`；构建后完整 classes 快照（568 class，`production-classes-0.121.35/`）复跑上列全部套件。

## 文档同步

项目 README 状态行与当前源码版本、工作区总览 README、`docs/en/README.md`、`docs/en/Introduction.md`（Current status 返工条与 Current version）、迁移日志顶部条目、新建 `docs/归档/版本记录/更新记录-0.121.35.md`、release 索引顶部条目、`修复进度-2026-11-02.md`（状态行/下一入口/SHA-256/版本表 0.121.35 行/返工节）、`未修复事项清单-2026-11-02.md`（顶部返工快照与 P06-18 行"0.121.35返工交付，待Codex复验"）。逐处 grep 验证命中（0.121.35：项目README 2、工作区README 2、en README 1、Introduction 3、迁移日志 2、修复进度 5、未修清单 5、release索引 3）。

## 边界与账本

- 未实机、未验证真实网络延迟/真实GUI；"自定义键清除窗口"按交接要求准确描述，未用离线夹具冒充网络时序证明。
- 累计账本按验收口径 **56 项完成 / 4 项部分 / 62 项待处理**；E3 返工完成后仍标待Codex复验，不自行销号、不解除主任务暂停。
- 交付完成后停止，不扩展其他任务；既有名称与集成范围保持。
