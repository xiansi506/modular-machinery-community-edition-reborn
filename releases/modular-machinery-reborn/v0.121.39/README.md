# v0.121.39 — 第五批 H 组（H1/H2，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.39.jar` **1,632,784 字节**，SHA-256 `FCBB4E1AC027D0B5BF2165456BECE568432E942B0B19A07A040EFD95E67FDCDB`。真实起点 0.121.38（340 份源码哈希与第五批交接基线逐份一致）。

## 本版内容（第五批 H 组两项，I 组随后续 0.121.40）

- **H1 / P01-01（编辑器原文→表单结构边界）**：`RecipeDocument.formStructureError` 新增表单可显示性结构校验——requirements 必须是数组、条目必须是对象、`type` 必须是非空字符串、已知需求类型的 `perTick`/`parallelize-unaffected` 必须是布尔；`RecipeEditorScreen.init` 切到需求页前运行该校验，结构不符的原文回落原文页并显示带 `requirements[序号].字段` 位置的错误。原文、当前页状态、机器/配方 ID 与先前表单模型不半更新；修正后可再切回。语法错误/半成品原文的既有语义保留；空 requirements 缺省策略保留；未知但结构合法的高级需求不被拒绝也不被删除（rawHelp 入口保留）；不做全树规范化，不替代服务端配方语义验证。
- **H2 / P01-06（翻页不重发未变选区）**：`ConstructToolService.sync` 增加条目单位分流——成功的 PREVIOUS_PAGE/NEXT_PAGE 省略全量选区坐标包（32768 坐标未压缩约 393240 字节，只发实际页状态）；初次 open、选区变更（APPLY/UNDO/REDO/CLEAR 与点击）、PREVIEW/EXPORT、生命周期清理与过期 revision 纠正保留完整同步；失败/拒绝路径纠正语义不变。选区包编码格式未改（真实 FriendlyByteBuf 往返验证），无新增增量格式/压缩/ACK/缓存。

## 验证

- 夹具（`_audit/fifth-ai-4tasks-0.121.39/`）：H1 `EditorStructureCheck` 15 项（真实控件链 tab 按钮 onPress→change→collect→init；三种勘测反例+null/字符串条目+缺 type/错误类型 type+布尔反例全部回落原文页且错误含位置；不半更新+修正后切回；未知高级需求/空 requirements/语法错误路径保留）；H2 `PageSyncCheck` 12 项（真实 handle→sync 链、SimpleChannel 记录替身捕获包对象——成功翻页不发坐标包、open/APPLY/过期 revision 完整同步、两玩家独立、权限拒绝 0 包、FriendlyByteBuf 往返）。
- 三阶段：repro（0.121.38 快照复现：`requirements:[1]`→IllegalStateException 逃出按钮回调、翻页 `selection=1` 全量包）→ prebuild → fixed（0.121.39 快照）全过；fixed 阶段在 0.121.40 快照复跑回归通过（日志 `-fixed40` 后缀）。
- Gradle test=NO-SOURCE（项目无测试源集），如实说明。实机未验：全部离线原生调用，无真实 GUI/IME/网络/Forge 游戏窗口。

## 边界

未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送或创建 Release、未 reset/clean 工作区；标 **待 Codex 验收**，不自行销号。保留全部既有未提交修改与其他 AI 修复（E3 失败提示、D3 int 存档、checker 部件定位、实际层索引、ME 16 格维护等）。
