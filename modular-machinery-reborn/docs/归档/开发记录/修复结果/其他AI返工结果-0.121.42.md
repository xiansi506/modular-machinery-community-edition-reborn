> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI返工结果：0.121.42（第五批 H1 单项续修，2026-11-03）

执行 [返工交接-第五批H1续修](../任务交接/返工交接-第五批H1续修-2026-11-03.md)，从 0.121.41 递增 **0.121.42**，协议 **12** 不变。真实起点为 0.121.41：改前 340 份源码哈希与验收指纹（`_audit/review-fifth-rework-0.121.41-2026-11-03/current-source-hashes.json`）逐份一致（checked 340 / mismatch 0）；起点 `RecipeEditorScreen.java` 从当前树逐字节复制，568 class 快照与 0.121.41 交付快照一致。I2 已通过保留，不重做。**标待 Codex 验收，不自行销号；累计账本与主任务暂停状态未改。**

## 回归原因与修复

验收链：原文输入合法未知高级需求 → 切需求表单 → 点“删除”（root.requirements 变空）→ 切原文页仍显示旧草稿 → 再切回需求页，collect 重新采纳旧草稿，删除的高级需求复活。

原因是 0.121.41 只在基本页/已知需求种类的 collect 分支清 `sourceDraft`；未知需求/空列表分支不经过这些分支，删除后的 root 与草稿脱节，原文页优先用了旧缓存。这不是“未知类型特判”能覆盖的——任何表单侧模型修改都可能留下旧原文。

- **事实来源身份校验**：新增 `sourceDraftModel` 记录草稿被接受时的 root 文本；`syncDraft()` 在 `collect()` 与 `init()` 前比较当前 root，二者不一致即作废旧草稿。这样删除、添加、类型/资源选择回调、IO 切换、JEI 拖入等所有实际修改入口都统一覆盖，不依赖逐个回调清缓存，也不改变原文页“未接受/切页失败时逐字保留”的能力。
- **原文页身份基线**：草稿失效后从当前 root 重新生成原文，并把这份文本记为新的身份基线；之后编辑只改草稿、不改 root，直到下一次成功提交。
- **保留 0.121.41 已验证行为**：collect 发布前结构校验、失败逐字保留与旧模型不半更新、resize/重建还原、未知高级需求 rawHelp、导出按钮门控均不变；`RecipeDocument`/`RecipeForm` 0 行 diff。

## 验证矩阵（H1 以 0.121.42 clean build 的 568 class 快照复跑）

| 套件 | 0.121.40 快照 | 0.121.41 快照 | prebuild | 0.121.42 快照 |
|---|---|---|---|---|
| Codex `MutationOnlyCheck`（3 项） | **3/3** | **1/3**（原始/往返复活，exit 3） | 3/3 | **3/3** |
| Codex `EditorMutationCheck`（14 项） | 8/14（6 条 0.121.41 才有的保留断言失败属预期） | **12/14**（2 条删除复活失败） | 14/14 | **14/14** |
| 本批 `MutationMatrixCheck`（23 项） | — | **13/23**（10 条删除/往返/添加失败） | 23/23 | **23/23** |
| `EditorDraftCheck`（11 项） | — | 11/11 | 11/11 | **11/11** |
| `EditorExtraCheck`（17 项） | — | 17/17 | 17/17 | **17/17** |
| 原 `EditorStructureCheck`（15 项） | — | 15/15 | 15/15 | **15/15** |
| I2 `SwitchExtraCheck`/`IntentRebuildCheck`/`SwitchSyncCheck` | — | — | — | **17/17、13/13、15/15** |
| H2 `PageSyncCheck` / I1 `LayoutMeasureCheck` | — | — | — | **12/12 / 4/4** |
| 五组必要回归 | — | — | — | FinalTick 15、SavedDefinition 14、SchemaDynamic 6、ConfigTypeIsolation 4、RegistrationSnapshot 5，全 exit 0 |

`MutationMatrixCheck` 覆盖：三个未知需求删除中间项、删除末项、单未知删除到空、空列表添加已知需求、已知/未知混合删除、IO 回调修改、resize/rebuild、原文/表单往返，以及“表单修改后再输入非法原文”仍逐字保留且旧模型不半更新。全部走真实按钮/输入框链，不反射写 root 或草稿；未复活旧项、未丢 `custom-key`。

## 源码与产物

- **改动面**：340 份 Java 仅 `client/RecipeEditorScreen.java` 改变（+29/−10，含注释）；`authoring/RecipeDocument.java`、`authoring/RecipeForm.java` 0 行 diff；568 class 仅 `RecipeEditorScreen.class` 改变。
- **归档**：`releases/modular-machinery-reborn/v0.121.42/`——JAR **1,633,273 字节**、SHA-256 `17658DDF013F916521530067DBEC8DEAB78C12D13746498836062F08ED1C6BBA`、README。归档为最终 `build/reobfJar/output.jar` 的逐字节复制（`cmp` 通过），与本次 `build/libs/modular_machinery_reborn-0.121.42.jar` 哈希相同；未重打包、未改写旧 0.121.39/40/41 JAR。
- **ZIP 容器观察**：与 0.121.41 归档同为 973 个 entry、名称顺序哈希一致，内容差异只有 2 个 entry（`META-INF/mods.toml` 版本、`RecipeEditorScreen.class`），911 个 entry 的 ZIP 时间戳不同；本归档取自最终构建，不声称“另一次 rebuild 得到同一 SHA”。
- **证据**：`_audit/fifth-h1-mutation-0.121.42/`——`src-before-0.121.41`（RecipeEditorScreen/RecipeDocument/RecipeForm）、568 class 起点快照、三基线日志与退出码（0.121.40 / 0.121.41 / prebuild）、fixed 日志、增量 diff、改前/改后 340 份源码哈希、0.121.42 快照、五组回归日志、归档条目对比 JSON。

## 文档

新增本文件、`更新记录-0.121.42.md`、归档 README；同步 `迁移日志.md`（新节置顶）、中英文 README/Introduction、root README、release 索引、修复进度、未修清单状态行。版本元数据 `gradle.properties` 0.121.41→0.121.42（`mods.toml` 随构建生成）。

## 边界声明

全部验证为离线原生调用（内存屏幕壳、真实控件与原生按钮回调；本轮导出只验按钮门控、未点击发送，不涉及网络替身），无真实 GUI/IME/GPU、完整 Forge/JEI、多人网络或区块 IO 验收；**实机未验**。未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送或创建 Release、未 reset/clean 工作区（构建按根规则执行 `clean build`，旧归档未动）。单项交付后停止，不扩展其他任务。
