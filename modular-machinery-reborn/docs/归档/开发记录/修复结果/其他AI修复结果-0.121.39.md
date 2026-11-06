> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI修复结果：0.121.39（第五批H组 H1/H2，2026-11-03）

执行 [修复交接-其他AI-第五批4项-2026-11-03](../任务交接/修复交接-其他AI-第五批4项-2026-11-03.md) 的 **H1/H2**（I 组随 [0.121.40](其他AI修复结果-0.121.40.md) 累积交付），从 0.121.38 递增 **0.121.39**，协议 12 不变。真实起点 0.121.38：改前 340 份源码哈希与交接基线逐份一致（checked 340 / mismatch 0），起点副本取自交接 `_audit/fifth-ai-handoff-4tasks-2026-11-03/before/`。**标待 Codex 验收，不自行销号。**

## H1 / P01-01：编辑器原文→表单的结构校验与可恢复错误

- `RecipeDocument.formStructureError(JsonObject)`（新增，+52 行）：检查表单控件真正依赖的类型前提——`requirements` 必须是数组（缺失保留缺省策略）、条目必须是对象、`type` 必须是非空字符串（不特判三个样例，逐条目检查）、已知需求类型的 `perTick`/`parallelize-unaffected` 必须是布尔；错误消息带 `requirements[序号].字段` 与值类型。
- `RecipeEditorScreen.init`：切到需求页前运行校验，不符回落原文页显示错误——原文（multiline 逐字保留）、当前页状态、机器/配方 ID 与先前表单模型不半更新；修正原文后可再次切回并正常校验/导出；resize/rebuild 后错误原文仍在。`update()` valid 分支遍历补 isJsonArray 防御。
- 保留：语法错误/半成品原文继续编辑、空 requirements 缺省策略、未知但结构合法的高级需求（不拒绝、不删除自定义键、rawHelp 入口）、已修导出/会话边界。不做全树规范化、不替代服务端语义验证、不处理 P01-02/04。

## H2 / P01-06：翻页不重发未变选区

- `ConstructToolService.sync` 条目单位分流（+16 行）：成功的 PREVIOUS_PAGE/NEXT_PAGE 只发状态包（页码在快照内、使用最新 revision），省略全量坐标包（32768 坐标未压缩约 393240 字节——**编码体大小，非线上流量或性能基准**）；open/选区变更/点击/PREVIEW/EXPORT/生命周期/过期 revision 纠正保留完整同步；失败/拒绝路径纠正语义不变（不因操作名是翻页就跳过同步）。
- `SelectionSyncPacket`/`ConstructToolStatePacket` 未改（真实 FriendlyByteBuf 往返：坐标顺序/维度一致、48 字节小样本，格式与压缩边界说明保留）；无新增增量格式/压缩/ACK/缓存。

## 验证（0.121.39 构建 568 class 快照）

| 夹具 | repro（0.121.38） | fixed |
|---|---|---|
| H1 EditorStructureCheck 15 项 | `requirements:[1]`→IllegalStateException 逃出 tab 按钮回调、`{}`→CCE、`[{}]`→NPE、null/字符串条目→ISE | 15/15：全部反例回落原文页且错误含位置；不半更新+修正后切回；未知高级需求/空 requirements/语法错误路径保留 |
| H2 PageSyncCheck 12 项 | 翻页发全量坐标包（`selection=1`） | 12/12：成功翻页不发坐标包；open/APPLY/过期 revision 完整同步；两玩家独立；权限拒绝 0 包；FriendlyByteBuf 往返 |

夹具走真实控件/入口链（tab 按钮 onPress→change→collect→init；handle；containerTick；回车 keyPressed）；出站用 SimpleChannel 记录替身（同 FQN 覆盖，只记录发送意图，不代表服务器已执行）。0.121.40 快照复跑回归通过（15/15、12/12）。

## 交付物

- 归档 `releases/modular-machinery-reborn/v0.121.39/`：JAR **1,632,784 字节**、SHA-256 `FCBB4E1AC027D0B5BF2165456BECE568432E942B0B19A07A040EFD95E67FDCDB`、README/SHA256.txt（0.121.40 构建前以同一源码状态重建归档，H 批源码内容不变）
- 证据 `_audit/fifth-ai-4tasks-0.121.39/`：H1/H2 起点、repro/prebuild/fixed 日志与退出码、增量 diff（RecipeDocument 52 行、RecipeEditorScreen 16 行、ConstructToolService 16 行）、改前/后 340 份哈希
- Gradle test=NO-SOURCE（项目无测试源集）。实机未验：离线原生调用（真实控件/固定字宽外壳、内存世界壳与记录 sink；无真实 GUI/IME/网络/Forge 窗口）。

## 边界

未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送或创建 Release、未 reset/clean 工作区、未改累计账本、未解除暂停。保留 E3 失败提示、D3 int 存档、checker 部件定位、实际层索引、ME 16 格维护等既有修复。
