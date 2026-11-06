> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex[独立验收](../验收记录/验收-第八批N1返工-0.121.49-2026-11-04.md)：N1指定范围返工通过，第八批四项收口；累计80/5/37，最近整体通过0.121.49。** 原4条失败已独立复现并转绿。真实游戏/网络/GPU未验，主任务保持暂停。下文为执行方交付记录。

# 其他AI返工结果：第八批 N1（0.121.49，2026-11-04）

**标待 Codex 验收。** 按第八批 N1 返工交接交付单项返工：起点 0.121.48 / 协议 12 / 340 Java / 568 class，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a` 与 791 项未提交改动原样保留。开工前核对 `_audit/review-eighth-4tasks-2026-11-03/current-source-hashes.json`：**340/340 一致**。生产白名单仅 `client/RecipeEditorScreen.java`、`client/ConstructToolScreen.java`，递增为 **0.121.49**，协议 12 不变。N2/O1/O2 未动、未重做。

## 根因与修复

### 0.121.47 方案为何不够（Codex 复现确认）

`pending = localEdits != collectedEdits` 只识别「还在控件、未进模型」的输入。三处漏判，已在 0.121.48 真实 class 上用 Codex 夹具复现（`CollectedDraftCheck` 3 过 / 4 失败）：

1. **resize 收集后覆盖**：改名 → resize（`resize()` 先 `collect()` 把新名称并进 options）→ 旧 APPLY 到达：`collectedEdits` 已追平 → 判定"无未收集输入" → 服务端旧 options 覆盖新名称、页面被切走（2 条失败）。
2. **切页收集后覆盖**：改名 → 按"选区"按钮（`page()` 内 `collect()`）→ 旧 APPLY → 新名称丢失（1 条失败）。
3. **格式按钮不计入**：超时后按格式按钮直接把 `options.format` 从 FRAGMENT 改成 JSON——按钮不经过文本监听器，代次不动 → 旧 APPLY 把格式改回去（1 条失败）。

另用 `ReworkComboBefore`（B1，只用改前 API）补一个最强复现：改名 → 切页收集 → 旧 valid 回包**内容恰好匹配当前模型**（`reviewedSource == root.toString()`）→ 0.121.48 上照样切到预览页（tab=3，原页面控件不在而返回`<absent>`）、打开导出；这未证明root模型被清空（3 条失败）。证明收集动作把"发送后出现过编辑"的事实洗掉了。

### 修复：请求发送时的编辑代次基线

两个 Screen 同一套（白名单内各改 5 处，核心逻辑一致）：

- `private long localEdits, sentEdits;`（删除 `collectedEdits`——它就是"收集后即可覆盖"错误的载体）。
- **`sentBaseline()`**（包私有）：`sentEdits = localEdits`。由真实 `send()` 在发包前调用（编辑器在 `RecipeDocument.read` 校验后、`ModNetwork.CHANNEL.sendToServer` 前；工具在 `collect()` 后、发包前）。
- **回包判定改为 `stale = localEdits != sentEdits`**：collect/切页/缩窗只搬运值、不推进 `localEdits`，"发送之后出现过本地修改"无法被收集动作消除。stale 时回包只更新服务端 revision 与提示——编辑器不重建表单/不切预览/不恢复导出（未收集控件内容照旧由 `pendingValues` 逐字回填）；工具不用旧 options 覆盖表单、不替作者换页、不动 scroll。
- **非文本入口计入编辑**：工具的 format/ignoreAir/replace/resetReplacements/cycle 五个直接改 options 的按钮在修改后调用 `edited()`；编辑器的 format 按钮同样推进。send 型按钮（ignoreAir 等）修改后立即发送，`sentBaseline()` 记录含本次修改的基线，回包相符照常生效；若发送被 `waiting>0` 挡下，修改已计入基线、后续旧回包被抑制。
- **不会永远脏**：新请求发包时基线追平 `localEdits`，其相符回复照常生效——编辑器 valid 且 `reviewMatches` 则切预览并开导出；无修改时行为与 0.121.47 完全一致（C4/C5/R9/R10 对照保持）。
- 未改：协议/wire/服务端、`token`/维度隔离、服务端复验、`sourceDraftModel` 身份规则、`programmatic` 屏蔽、O 批渲染与 O2 结算、FactoryEngine。

### 配方编辑器的覆盖面复核（交接要求"两个界面都要复核"）

编辑器的 `update()` 从不把回包内容写进 `root`/控件（valid 只消费 `state.preview`/`summary` 渲染预览页），模型覆盖风险不存在；风险集中在 `checked*`/tab 侧，由 `stale` 闸门 + 既有 `reviewMatches` 内容身份双闸把关。因此编辑器侧除基线改造外只补了 format 按钮计数；add/remove/io/perTick 等表单按钮修改 root 后导出由 `checkedSource.equals(root.toString())` 自动失效、旧 valid 由内容身份拒绝，行为等价，未扩大改动面（如实记录，非假定既有检查覆盖一切——是逐条路径核对后的结论）。

## 验证（全部走真实入口）

证据目录 `_audit/eighth-n1-rework-0.121.49/`（`before/` 改前源码副本含 SHA-256、`classes-0.121.48` 568 改前类快照、`N1/fixture` 与 `fixture-before`、`logs/` 前后全部夹具与构建日志、`production-classes-0.121.49` 最终快照）。

| 夹具 | 0.121.48（改前） | 0.121.49（最终） |
|---|---|---|
| `CollectedDraftCheck`（Codex 7 条，原样） | 3 过 / **4 失败** | **7 全过** |
| `ReworkComboBefore`（B1，4 条） | 1 过 / **3 失败** | **4 全过** |
| `ReworkComboCheck`（新组合 25 条） | —（引用新 API 编译不过） | **25 全过** |
| `LateReplyCheck`（68 条，断言未动） | 68 全过 | **68 全过** |

- **markSent 升级**（受控发送边界）：离线没有 Netty 通道，夹具按生产 `send()` 发包点前的同一方法、同一顺序驱动——`collect()` → `sentBaseline()` → `waiting=1`——**不反射写保护字段**。旧版（只设 waiting）保留在 `fixture-before` 供改前类编译，B1 在两种基线下双向验证。
- **新组合**（Codex 要求的组合矩阵）：旧回复抑制 → 新请求 → 相符回复恢复预览/导出（编辑器+工具各一）；输入后切页收集 → 内容匹配的旧回复仍被抑制（B1 的修复后形态）；resize（收集）→ 旧回复抑制（编辑器版，工具版在 CollectedDraftCheck）；格式按钮 → 旧回复保持；**N2 组合**——原文页输入 65 层超深 JSON → 按 validate → `collect()` 拒绝（"JSON nesting exceeds 64"）→ 未发送 → 草稿逐字保留 → 旧回复不碰草稿；无修改时新请求后服务端数据照旧生效。程序化初始化/重建不算编辑（R10/C6 保持）。
- **协议 12 边界如实保留**：无请求序号，同一 token 下"新请求在途时旧回复先到"不可识别（该顺序下旧回复会被消费、新回复随后再消费，最终态正确）——与修复前一致，未宣称请求级一致性。

## 零回退矩阵（regress49.py，双向）

0.121.48 改前类与 0.121.49 最终类**各跑一遍、全部通过**：

- H1 六套（第五批）：`EditorDraftCheck` 11、`EditorMutationCheck` 14、`EditorStructureCheck` 15、`EditorExtraCheck` 17、`MutationMatrixCheck` 23、`MutationOnlyCheck` 3。
- `FinalTickCheck` 退出 0；第七批 `ControllerExtraCheck` failures=0。
- N2：`DepthCheck` 37 全过。
- O1：`QueueRowCheck` 63、`QueueRowBefore` 10、`RenderHookCheck` 4（parent-render agent 受控边界：原生容器父渲染为受控 no-op）。
- O2：`EnergyLiveCheck` 26、`EnergyMatrixCheck` 44（duration compensation on/off × unaffected on/off × 实时修改器）。

## 构建与归档

- `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot`、`GRADLE_USER_HOME=C:/mmwork/.gradle-home`，JDK 17 `java.exe` 启动 wrapper，离线 `--no-daemon` 的 `clean build`：`BUILD SUCCESSFUL in 41s`，8 任务全执行，`test NO-SOURCE` 如实保留。日志 `logs/build-0.121.49.log` / 命令 `build-0.121.49-command.txt` / 退出码 `build-0.121.49-exit.txt`=0。
- 产物：`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.49.jar`，**1,638,071 字节**，SHA-256 `E8769BFAC4A1F2F6AB280A90F6FB28C8E37FF095993C5963A04A79A1E28CDD84`；973 entry / 568 class / 221 JSON+mcmeta 全可解析；中英各 566 键且键集一致；`mods.toml` 0.121.49；SRG 名（`m_237110_`）；协议 12。
- 体检：`evidence-0.121.49.txt` —— 340 份源码相对第八批基线仅白名单 5 份（返工 2 份含于 N 的 2 份），`568→568` class 无增删；改后哈希清单 `source-hashes-after-0.121.49.json`。
- 归档：`releases/modular-machinery-reborn/v0.121.49/`（JAR 逐字节 cmp 一致 + README + SHA256.txt）；旧归档（含 v0.121.48）未改写。

## 未验事项

- 未实机、未部署、未启动游戏；未动玩家整合包/存档。
- 真实联机的网络时序、真实 GPU 渲染未验（O1 的 parent 渲染为受控 no-op；N1 的发送基线在受控边界驱动）。
- 未暂存/提交/推送、未创建 Release、未 reset 工作区、未改累计账本（79 完成/5 部分/38 待处理）、未解除暂停。
