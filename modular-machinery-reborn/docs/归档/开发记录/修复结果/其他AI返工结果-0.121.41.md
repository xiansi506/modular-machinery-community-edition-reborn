> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI返工结果：0.121.41（第五批 H1/I2 返工，2026-11-03）

执行 [返工交接-第五批H1-I2-2026-11-03](../任务交接/返工交接-第五批H1-I2-2026-11-03.md) 两项，从 0.121.40 递增 **0.121.41**，协议 **12** 不变。真实起点为 0.121.40：改前 340 份源码哈希与验收指纹（`_audit/review-fifth-4tasks-2026-11-03/current-source-hashes.json`）逐份一致（checked 340 / mismatch 0）；三份起点源码从当前树逐字节复制到新审计目录，起点 568 class 快照与 0.121.40 交付快照逐字节一致（`RecipeEditorScreen.class`、`HatchGroupScreen.class` cmp 相同）。H2/I1 已通过，不重做。**标待 Codex 验收，不自行销号；累计账本与主任务暂停状态未改。**

## 逐项

### H1 / P01-01（编辑器原文→表单的结构校验时机）

验收失败的实际链：原文页 `collect()` 先执行 `root=RecipeDocument.read(...)`，`change` 随后切 tab 并 rebuild；直到 `init()` 才检查结构并回落原文页。此时 root 已被非法对象替换，原文框又由 `editable(root)` 生成格式化文本——原文不再逐字相等，旧表单模型也变成非法对象（machine 变 `test:other`）。

- **修复（先校验、后发布）**：`RecipeEditorScreen.collect()` 的原文页分支现在先在局部变量解析，调用 `RecipeDocument.formStructureError(parsed)`；只有结构通过才 `root=parsed`。失败时只设可定位 `message` 并返回 false，`change` 不执行切页，**root 与原文框都不动**。`init()` 的同一检查保留为进入表单模型前的最后防线（覆盖非原文来源的 root），不再承担原文页的正常拦截。
- **保留独立原文草稿**：新增 `sourceDraft` 字段。原文页编辑经 `MultiLineEditBox.setValueListener` 实时写入；`init()` 开头在重建前把当前原文框内容收进草稿；`init()` 的原文页从草稿还原，不再用格式化 JSON 冒充逐字保留。表单页提交成功时清空草稿（root 重新成为唯一事实来源）。缩窗走既有 `savedValues` 路径，并在应用后同步草稿。
- **校验范围不变**：只查表单控件真正依赖的结构（requirements 数组、条目对象、`type` 非空字符串、已知类型的 `perTick`/`parallelize-unaffected` 布尔），不要求完整服务端语义，不删除未知高级需求的自定义字段。
- **夹具**（`EditorDraftCheck` 11 项，全部走真实 tab 按钮→collect→init、真实输入框，不反射写 root）：失败切页后原文逐字与旧模型保持；`resize(900×500)` 与直接 `init()` 重建仍逐字还原；切基本页/需求页同样被结构门控挡下且草稿不丢；修正原文后可正常进入表单并采纳新模型；基本页/需求页来回切；未知高级需求保留 rawHelp 与 custom-key；已接受校验（reviewedId/Source/Format 与 ready 匹配）时导出按钮正常启用（不点击发送，不依赖真实连接）。

### I2 / P10-08（确认前重建的意图一致性）

验收失败的实际链：点启用后 `localIntent=true`，确认到达前重建界面；`init()` 把 `groupInput` 重设为旧菜单 false，却没有清理或一致恢复 `localIntent`。随后确认 true 到达，`containerTick` 看到 `localIntent=true` 且菜单≠镜像，永远等待两者相等，无法采纳确认；重复 tick 仍为 false 且意图不清除。

- **修复（一致的重建策略）**：`init()` 不再无条件回到菜单快照——`localIntent` 为 true 时保留本地镜像（仍待确认的同一意图），按钮可见性沿用镜像；无意图时才采用菜单快照。确认到达后由既有 `containerTick` 清除意图。菜单 wire、服务端处理、数字提交与“关闭不提交数字”的既有语义均未改。
- **夹具**：Codex `SwitchExtraCheck` 17 项（新增两断言：重建后确认到达镜像更新、重建状态不卡在旧意图）+ 本批补充 `IntentRebuildCheck` 13 项，覆盖双方向“点击→确认前重建→确认到达”（0→1 与 1→0）、确认已到达但 tick 未消费时重建、无意图重建仍采用菜单快照并跟随后续变化、连续两次重建不发包。

## 验证矩阵（全部以 0.121.41 clean build 的 568 class 快照复跑）

| 套件 | repro（0.121.40 快照） | prebuild（单文件编译） | fixed（0.121.41 快照） |
|---|---|---|---|
| Codex `EditorExtraCheck`（H1） | 15/17（2 条新增断言失败，exit 3） | 17/17 | **17/17** |
| 本批 `EditorDraftCheck`（H1 补充） | 5/11（6 条失败） | 11/11 | **11/11** |
| 原 `EditorStructureCheck`（H1 15 项） | — | 15/15 | **15/15** |
| Codex `SwitchExtraCheck`（I2） | 15/17（2 条新增断言失败，exit 3） | 17/17 | **17/17** |
| 本批 `IntentRebuildCheck`（I2 双方向） | 7/13（6 条失败） | 13/13 | **13/13** |
| 原 `SwitchSyncCheck`（I2 15 项） | — | 15/15 | **15/15** |
| H2 `PageSyncCheck` / I1 `LayoutMeasureCheck` | — | — | **12/12 / 4/4** |
| 五组必要回归 | — | — | FinalTick 15、SavedDefinition 14、SchemaDynamic 6、ConfigTypeIsolation 4、RegistrationSnapshot 5，全 exit 0 |

repro 的 fixed 期望断言在 0.121.40 上按“正确行为”运行：H1 两条 EXTRA 失败时可见原文被格式化为 `machine: test:other`、root 被替换；I2 两条 EXTRA 失败时 `mirror=false`。prebuild 只重新编译白名单源码（H1：`RecipeEditorScreen`/`RecipeDocument`；I2：`HatchGroupScreen`），生产类目录前置。

## 源码与产物

- **改动面**：340 份 Java 中仅 2 份改变——`client/RecipeEditorScreen.java`（+32/−10）、`client/HatchGroupScreen.java`（+12/−3）；`authoring/RecipeDocument.java` 0 行 diff（本批只调用既有 `formStructureError`，注释未改）。其余 338 份字节不变。568 个 class 中仅对应 2 个 class 改变。
- **归档**：`releases/modular-machinery-reborn/v0.121.41/`——JAR **1,633,168 字节**、SHA-256 `2E36D2026CAE75C2FFE1DF5738AC58E3765CB816E776DE77466674EDC442167B`、README。归档是最终 `build/reobfJar/output.jar` 的逐字节复制（`cmp` 通过），同时与该次 `build/libs/modular_machinery_reborn-0.121.41.jar` 哈希相同；**未重打包、未改写旧 0.121.39/0.121.40 JAR**。
- **ZIP 容器差异说明**：与 0.121.40 归档相比，两份包均 973 个 entry、名称集合与顺序哈希完全一致，只有 3 个 entry 内容不同（`META-INF/mods.toml` 版本号、两个改动 class）；但 911 个 entry 的 ZIP 时间戳不同，因此两个版本的容器字节不可互比。上一轮验收记录的“build 产物与归档 SHA 不同、解压内容一致”属同类容器元数据差异，不是代码差异；本轮归档直接取自最终构建产物，逐字节一致是同一文件的复制校验，**不声称“另一次 rebuild 也会得到同一 SHA”**。
- **证据**：`_audit/fifth-rework-h1i2-0.121.41/`——三份 `src-before-0.121.40`、568 class 起点快照、repro/prebuild/fixed 日志与退出码、增量 diff、改前/改后 340 份源码哈希、0.121.41 快照、五组回归日志、归档条目对比 JSON。

## 文档

新增本文件、`更新记录-0.121.41.md`、归档 README；同步 `迁移日志.md`（新节置顶）、中英文 README/Introduction、release 索引、修复进度、未修清单状态行。版本元数据 `gradle.properties` 0.121.40→0.121.41（`mods.toml` 随构建生成）。

## 边界声明

全部验证为离线原生调用（内存屏幕/菜单壳、真实控件与原生按钮回调、替身 CHANNEL 只记录发送意图、`SimpleContainerData` 替身代表已到达菜单同步），无真实 GUI/IME/GPU、完整 Forge/JEI、多人网络或区块 IO 验收；**实机未验**。未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送或创建 Release、未 reset/clean 工作区（构建按根规则执行 `clean build`，旧归档未动）。两项交付后停止，不扩展其他任务；旧协议拒绝/延迟不可区分限制如实保留。
