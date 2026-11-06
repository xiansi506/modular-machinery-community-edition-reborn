> **2026-11-03 Codex：0.121.35 E3返工通过独立离线复验，第三批8项收口；仍未实机，未部署或推送。** [复验报告](../../../modular-machinery-reborn/docs/归档/开发记录/验收记录/验收-其他AI返工-0.121.35-2026-11-03.md)。

# 0.121.35：第三批E3单项返工（2026-11-03）

本批处理[第三批E3单项返工交接](../../../modular-machinery-reborn/docs/归档/开发记录/任务交接/返工交接-第三批E3-2026-11-03.md)：修复 0.121.34 在 E3/P06-18 引入的**已知键菜单清除回归**，从0.121.34递增为0.121.35，协议12不变。其余7项（D组全部、E1/E2/E4）验收通过的改动全部保留；不部署、暂存、提交或推送。

## 回归与修复

0.121.34 的 `startFailureKey()` 客户端分支在 ordinal 为"无失败/无效"时无条件回退更新标签里的旧完整字符串，把**已知键的旧快照**也当成自定义键：已知失败被菜单清除后（ordinal 先到、清除标签未到）仍显示旧键，8个已知键×菜单清除/无效ordinal共16处退化（0.121.32 无此行为）。

0.121.35：回退仅对**不在 `START_FAILURE_KEYS` 已知表**的有效完整字符串生效（`failureIndex(full) == NO_START_FAILURE` 才回退）；已知键的显示与清除完全由菜单 ordinal 通道管理，清除/无效 ordinal 立即返回 null、不复活。自定义键与清除在旧 ordinal 通道不可区分的限制如实保留（菜单清除无法区分自定义键，旧自定义文本保持到清除标签到达——该窗口已准确描述，不要求离线夹具证明真实网络延迟；清除标签抵达后消失）。不向白名单塞键、不扩展协议、不加修订状态机。

## 验证

- 返工夹具 `_audit/third-ai-rework-e3-0.121.35/ReworkE3Check.java`（同一客户端BE贯穿全部场景，不每包新建对象）：合并 Codex 验收 `ControllerExtraCheck` 的32项已知键断言+D3四项（真实单线程启动/结算：存读档恢复40000→真实tick开工1普通线程扣1煤→产出1石头；负int NBT归零），并补齐交接要求组合——已知A→B（标签/ordinal两种先到顺序）、自定义→已知、已知→自定义、自定义A→B（含不可区分窗口如实断言）、清除后再失败、魔法键真实tick。repro（0.121.34快照）55项中18项回归按预期复现；fixed（0.121.35快照）55/55。
- 复跑 Codex 验收夹具（`run_extra_controller.py` 的0.121.35副本）：SUMMARY failures=0，其中"E3 existing known-key menu clear before tag, actual=null"与"invalid ordinal normalizes to no failure, actual=null"均通过。
- 全部 D/E 已通过套件以 0.121.35 构建快照复跑：D1 7/7、D2 8/8、D3 8/8、D4 11/11、E1 13/13、E2 15/15、E4 12/12、原E3套件 20/20，全部退出0；副本与日志在 `third-ai-rework-e3-0.121.35/rerun/`，旧记录未覆盖。

## 构建与归档

Java17 clean build/reobf成功（36s），Gradle test=NO-SOURCE。出货 `modular_machinery_reborn-0.121.35.jar` 1,630,036字节（build/reobfJar/output.jar 与 build/libs 字节一致），SHA-256 `48ADCEE784BF18CE5002E2F87420E23C91123C3AE078C0DE7423585A659131FB`，归档 `releases/modular-machinery-reborn/v0.121.35/`。真实 0.121.34 起点副本（与验收指纹 before-review 逐字节一致）在 `third-ai-rework-e3-0.121.35/src-before-0.121.34/`，本次增量 diff（仅 startFailureKey 访问器一处，49行）单独保存；改前/改后 340 份源码哈希齐备。

Java17，协议 **12**。**标待Codex验收**；累计账本按验收口径 56 项完成/4 项部分/62 项待处理（E3 返工完成后仍待验收，不自行销号）。未启动/部署游戏、暂存/提交/推送或创建GitHub Release；交付完成后停止。
