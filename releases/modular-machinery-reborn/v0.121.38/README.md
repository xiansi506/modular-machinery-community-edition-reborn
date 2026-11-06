> **2026-11-03收口：0.121.38通过[Codex独立离线复验](../../../modular-machinery-reborn/docs/归档/开发记录/验收记录/验收-第四批返工-0.121.38-2026-11-03.md)，仍未实机，主任务暂停。下方交付/交接内容保留为记录。**

# v0.121.38 — 第四批 F3/G3 补充返工（2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.38.jar` **1,631,613 字节**，SHA-256 见 `SHA256.txt`。与 `build/libs`、`build/reobfJar/output.jar` 字节一致（cmp 验证）。

## 本版内容

第四批 8 项验收 **6 项通过保留**，F3/P07-01 与 G3/P10-17 按返工交接补充修复，一次交 0.121.38：

- **F3/P07-01（script-checker 校验）**：加载校验错误补**部件定位**——`readParts` 循环带序号，错误 where 追加 `part N (x=…, y=…, z=…)`（单值给数字、多值给 `[min..max]`）；parts-end 读取处 where 加 `'parts-end'` 段标记，四条读取路径（根 parts / 坐标数组 / dynamic 段 / multi-block-modifiers）的非法规则都能定位到具体部件。类型校验与客户端跳过契约不变；`MachinePattern` 本版未改（运行期受控拒绝为 0.121.36 已验收内容）。夹具补齐交接组合：单元素数组、parts-end、多块修改器、四朝向真实 tick（每朝向一次触发）、未注册 token 运行期实际拒绝、token 字符串身份原样（含大小写与空格）；tick 装置按验收模式纠正——先注册真实机器表、回调计数证明真实结构检查触发、再注入非法规则旋转表重建后安全拒绝，无无条件 PASS。
- **G3/P10-17（世界投影层枚举）**：`updateLayers` 收敛到 `lowestIncompleteLayer`（遍历 `structuredLayers` 实际层索引）——0.121.37 残留的 `for (y = min..max)` 逐整数枚举移除，start/clear/换结构/回退/前进全部入口统一；两层跨度 10000 的结构 start 从 **10003 次**全位置表扫描降到 **4 次**。负 y 层、start→clear→start 与 start→start 换结构、两种拆底层回退、START 相位、整机清除、维度/距离32/logout 全部保持。

## 验证

- 返工夹具（`_audit/fourth-rework-f3g3-0.121.38/`）：F3 `CheckerReworkCheck` 10 项、G3 `GhostReworkCheck` 19 项，repro（0.121.37 快照，sparse 扫描 10003 精确复现）→ prebuild → fixed（0.121.38 快照）三阶段全过。
- Codex 补测夹具复跑（0.121.38 快照）：`CheckerExtraCheck` failures=0（定位断言通过）、`GhostExtraCheck` 13/13（scans=4）。
- 原 8 项套件回归（0.121.38 快照）：F1 12、F2 4、F3 16、F4 11、G1 9、G2 6、G3 12、G4 5，全部通过；日志加 `-rework38` 后缀不覆盖旧记录。

## 元数据更正

0.121.36/0.121.37 归档 JAR 实际字节数分别为 **1,630,696** 与 **1,631,228**（此前报告写 1,630,241 / 1,630,349 系文档错误；JAR/SHA 未改，仅更正文字）。

## 边界

实机未验，未部署/启动游戏、未暂存/提交/推送；标待 Codex 验收，不自行销号。其余 6 项保留不重做；历史待处理项未扩展。
