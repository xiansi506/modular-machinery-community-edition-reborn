# v0.121.49 — 第八批 N1 返工（P01-02 收集后旧回包覆盖，2026-11-04）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.49.jar` **1,638,071 字节**，SHA-256 `E8769BFAC4A1F2F6AB280A90F6FB28C8E37FF095993C5963A04A79A1E28CDD84`。起点 **0.121.48**（第八批 O 组交付后的最终源码/class，累积构建）。**标待 Codex 验收。**

## 本版内容（N1/P01-02 单项返工）

0.121.47 的"本地编辑代次"只识别「还在控件、未进模型」的修改：collect（含 resize/切页自动触发）把 `collectedEdits` 追平后，旧回包照常覆盖刚刚收集的新草稿；格式按钮等直接修改 options 的非文本入口根本不推进代次。返工改为**请求发送时的编辑代次基线**：

- `send()` 在发包前通过 `sentBaseline()` 记录当时的 `localEdits` 基线（`sentEdits`）；回包到达时比较 `localEdits != sentEdits`——collect/切页/缩窗重建只搬运值、不推进 `localEdits`，因此"发送之后出现过本地修改"这个事实无法被收集动作抹掉。
- 直接修改模型的非文本入口全部计入编辑：结构工具的 format/ignoreAir/replace/resetReplacements/cycle 按钮、配方编辑器的 format 按钮在修改后调用 `edited()`。
- 有未确认修改时，回包只更新服务端 revision 与提示（工具侧不用旧 options 覆盖表单、不替作者换页；编辑器不重建表单、不切预览、不恢复导出，未收集控件内容照旧逐字回填）。
- **不会永远脏**：新请求发包时基线追平 `localEdits`，其相符回复照常生效——编辑器 valid 且内容匹配则切预览并开导出；无任何修改时行为与 0.121.47 完全一致。
- N2/O1/O2 未动（源码级确认：相对第八批基线白名单仍为 5 份，0.121.49 相对 0.121.48 只有 N1 两份 Screen 改变）。

## 验证

- Codex 夹具 `CollectedDraftCheck`（7 条）：0.121.48 类上 3 过 / 4 失败（resize 收集后旧 APPLY 覆盖新名称并换页、切页收集后覆盖、格式按钮后格式被改回），0.121.49 最终 class 上 **7 条全过**。
- `ReworkComboBefore`（B1，只用改前 API）：0.121.48 上 **3 失败**——回包内容**恰好匹配**当前模型时，collect 后的旧 valid 照样清空模型（`<absent>`）、切到预览页（tab=3）并打开导出；0.121.49 上 4 条全过。这是"收集不能消除编辑事实"的最强复现。
- `ReworkComboCheck`（25 条新组合）：旧回复抑制 → 新请求（真实发送序列）→ 相符回复恢复预览/导出；输入后切页收集 → 内容匹配的旧回复仍被抑制；编辑器 resize（收集）→ 旧回复抑制；**N2 组合**——原文页输入 65 层超深 JSON → validate 拒绝（"JSON nesting exceeds 64"）→ 草稿逐字保留 → 旧回复不碰草稿；无修改时新请求后服务端数据照旧生效（C4 语义）。0.121.49 上 **25 条全过**。
- `LateReplyCheck` 68 条（0.121.47 交付集）：markSent 升级为真实发送序列（`collect()` → `sentBaseline()` → `waiting=1`，与生产 send() 发包点前完全一致，受控离线边界）后 **68 条全过**；断言未删未放宽。
- 零回退矩阵（`regress49.py`，0.121.48 基线与 0.121.49 最终类**双向**各跑一遍，全部通过）：H1 六套 83 条、`FinalTickCheck`、`DepthCheck` 37（N2）、`QueueRowCheck` 63 + `QueueRowBefore` 10 + `RenderHookCheck` 4（O1，parent-render agent 受控边界）、`EnergyLiveCheck` 26 + `EnergyMatrixCheck` 44（O2）、`ControllerExtraCheck`（第七批）。
- 源码边界：340 份源码相对第八批 0.121.46 基线仅白名单 5 份（N 3 + O 2，返工 2 份含于 N 的 2 份）改变；568→568 class 无增删。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（41s，8 任务全执行，`test NO-SOURCE` 如实保留）。`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.49.jar`，**1,638,071 字节**，SHA-256 `E8769BFAC4A1F2F6AB280A90F6FB28C8E37FF095993C5963A04A79A1E28CDD84`；973 entry / 568 class / 221 JSON+mcmeta 全可解析；中英各 566 键且键集一致；`mods.toml` 0.121.49；出货 class 仍为 SRG 名（`m_237110_`），协议 12。归档 `releases/modular-machinery-reborn/v0.121.49/`（逐字节复制 + README + SHA256.txt），旧归档未改写。

## 边界

真实 `accept`/`update`/`tick` 超时/`EditBox`/`MultiLineEditBox`/`RecipeEditorService.state` 路径；离线无 Netty 通道——发送基线在**受控边界**由真实 `collect()`+`sentBaseline()`+`waiting=1` 序列驱动（与 send() 发包点前同一方法同一顺序，未反射写保护字段），真实联机时序未验；协议 12 无请求序号，同一 token 下"新请求在途时旧回复先到"仍不可识别（与修复前一致，如实保留）。未实机、未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未改累计账本（79 完成/5 部分/38 待处理）、未解除暂停。详见[返工结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI返工结果-0.121.49.md)。
