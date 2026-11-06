> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI返工结果：0.121.38（第四批 F3/G3 补充返工，2026-11-03）

执行 [返工交接-第四批F3-G3-2026-11-03](../任务交接/返工交接-第四批F3-G3-2026-11-03.md) 两项，从 0.121.37 递增 **0.121.38**，协议 12 不变。真实起点为 0.121.37：改前 340 份源码哈希与验收指纹（`_audit/review-fourth-8tasks-2026-11-03/current-source-hashes.json`）逐份一致（checked 340 / mismatch 0），起点副本取自验收 `before-review/` 且与当前源码逐字节一致（cmp 验证）。其余 6 项（F1/F2/F4/G1/G2/G4）验收通过保留，不重做。**标待 Codex 验收，不自行销号。**

## 逐项

### F3 / P07-01（script-checker 校验）

- **修复 1（定位）**：`MachineSchema.readParts` 循环带序号，`validateScriptChecker` 的 where 追加 `part N (x=…, y=…, z=…)`——坐标范围单值给数字、多值给 `[min..max]`（新增 `partWhere`/`span`）。验收失败的定位断言（部件在 (17,3,5)）现在输出：`'script-checker' in coordinate_probe part 0 (x=17, y=3, z=5) must be …`。
- **修复 2（段标记）**：parts-end 读取处 where 加 `'parts-end'`——此前 parts 与 parts-end 的 where 相同。dynamic 段错误现为 `… dynamic pattern 'seg' 'parts-end' part 0 (x=2, y=0, z=0) …`。
- **保持**：类型校验（只接受非空白字符串）、正文原样保留、客户端跳过服务端 checker 契约、运行期受控拒绝（`MachinePattern` 本版未改，0 行 diff——0.121.36 已验收内容）。
- **夹具**（`CheckerReworkCheck` 10 项）：Codex 3 断言（定位/真实 tick 计数/注入规则旋转表重建后安全拒绝）+ 交接补齐：单元素数组、parts-end、多块修改器三条路径定位、四朝向每朝向真实触发（calls=4）、未注册 token 运行期实际拒绝（不抛、不 formed）、token 字符串身份原样（`Audit:Case Probe` 注册同名才命中）。tick 装置按验收报告纠正：**先注册真实机器表→回调计数证明真实结构检查触发→再注入非法规则**，无无条件 PASS。

### G3 / P10-17（世界投影层枚举）

- **修复**：`updateLayers` 的 `for (y = min..max)` 残留移除，收敛到 `lowestIncompleteLayer`（遍历 `structuredLayers` 实际层索引）——与回退检查同一实现；start/clear/换结构/回退/前进全部入口统一。验收装置的两层跨度 10000 场景：start 全位置表扫描 **10003 次 → 4 次**（透明 entrySet 计数，非要求特定方法名或精确次数）。
- **保持**：匹配语义（layerMatches 谓词）、不加载区块、不建世界全局监听。
- **夹具**（`GhostReworkCheck` 19 项）：Codex 13 断言全含（两种拆底层回退/相邻回退/START/整机清除/维度/距离32/logout/sparse 计数）+ 补齐：负 y 层排序与投影（Y=-2/Y=0 三态）、start→clear→start 与 start→start 换结构索引重建。

## 验证矩阵（全部以 0.121.38 构建 568 class 快照复跑）

| 套件 | repro（0.121.37） | prebuild | fixed（0.121.38） |
|---|---|---|---|
| F3 返工夹具 | 复现定位缺失（10/10 复现语义） | 10/10 | **10/10** |
| G3 返工夹具 | **复现 10003 次扫描**（19/19 复现语义） | 19/19 | **19/19** |
| Codex CheckerExtraCheck | — | — | failures=0（定位断言通过） |
| Codex GhostExtraCheck | — | — | **13/13（scans=4）** |
| F1/F2/F3/F4 原套件 | — | — | 12/4/**16**/11 全过 |
| G1/G2/G3/G4 原套件 | — | — | 9/6/**12**/5 全过 |

原套件复跑日志加 `-rework38` 后缀，不覆盖验收记录。

## 交付物

- **归档**：`releases/modular-machinery-reborn/v0.121.38/`——JAR **1,631,613 字节**、SHA-256 `502ED2B1FAF931CB6FE368FB776E179A9FE0F98C5CC8E2E7CFB388997503A08F`、README；与 `build/libs`、`build/reobfJar/output.jar` 字节一致（cmp）。clean build/reobf 43s。
- **证据**：`_audit/fourth-rework-f3g3-0.121.38/`——src-before（F3：MachineSchema/MachinePattern；G3：WorldGhostPreview）、repro/prebuild/fixed 日志与退出码、增量 diff（MachineSchema +25 行、WorldGhostPreview 19 行）、改前/后 340 份哈希、0.121.38 快照。
- **文档**：`更新记录-0.121.38.md`、迁移日志、双语 README/Introduction、release 索引、修复进度、未修清单同步。

## 元数据更正（按验收实测）

| 版本 | 实际字节数 | 原报告误写 | SHA-256 |
|---|---:|---:|---|
| 0.121.36 | 1,630,696 | 1,630,241 | `75FDAC64…`（不变） |
| 0.121.37 | 1,631,228 | 1,630,349 | `07455E8B…`（不变） |

原 JAR 未重写，仅更正 `其他AI修复结果-0.121.36.md`、`其他AI修复结果-0.121.37.md`、`其他AI修复结果-0.121.36-37.md`、更新记录与发布索引中的字节数。

## 边界声明

全部验证为离线原生调用（内存世界壳/内存方块表/记录 Storage），无真实 Forge 游戏、区块 IO、Rhino、完整 JEI 页面或 GPU 验收；**实机未验**。未部署/启动游戏、未暂存/提交/推送、未动玩家整合包/存档、未 reset/clean 工作区。两项交付后停止，未扩展其他 61 项历史任务。
