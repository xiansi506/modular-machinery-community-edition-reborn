> **2026-11-03收口：0.121.42通过[Codex独立离线复验](../../../modular-machinery-reborn/docs/归档/开发记录/验收记录/验收-第五批H1续修-0.121.42-2026-11-03.md)，仍未实机，主任务暂停。下文保留原交接/交付说明。**

# v0.121.42 — 第五批 H1 单项续修（2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.42.jar` **1,633,273 字节**，SHA-256 `17658DDF013F916521530067DBEC8DEAB78C12D13746498836062F08ED1C6BBA`。真实起点为 **0.121.41**（340 份源码哈希与验收指纹逐份一致，mismatch 0；起点 568 class 快照一致）。I2 已通过保留。

## 本版内容（H1 / P01-01 续修）

- **问题**：0.121.41 的逐字草稿只在基本页/已知需求种类的 collect 分支清除。原文输入合法未知高级需求→表单删除→root 为空→切原文页仍显示旧草稿→切回需求页重新采纳，删除的高级需求复活。
- **修复**：`RecipeEditorScreen` 新增 `sourceDraftModel`，记录草稿被接受时的 root 文本；`syncDraft()` 在 `collect()` 与 `init()` 前比较当前 root，不一致即作废旧草稿。删除、添加、类型/资源选择回调、IO 切换、JEI 拖入等所有表单修改入口统一生效；原文页随后从当前模型重建，并把生成文本记为新的身份基线。
- **保留**：collect 发布前结构校验、失败原文逐字保留/旧模型不半更新、resize/重建还原、未知高级需求 rawHelp、导出按钮门控。`RecipeDocument`/`RecipeForm` 未改。

## 验证

- Codex `MutationOnlyCheck`：0.121.40 快照 **3/3**（证明不是新增要求）、0.121.41 快照 **1/3**（两条复活断言失败）、0.121.42 快照 **3/3**；`EditorMutationCheck` 0.121.41 12/14 → 0.121.42 **14/14**。
- 补充 `MutationMatrixCheck` 23 项（0.121.41 13/23）：三个未知需求删中间/删末项、单未知删空、空列表添加、已知/未知混合、IO 回调、resize/rebuild、往返、表单修改后非法原文逐字保留；fixed 全过。
- 回归：`EditorDraftCheck` 11/11、`EditorExtraCheck` 17/17、`EditorStructureCheck` 15/15；I2 17/17、13/13、15/15；H2 12/12；I1 4/4；五组必要回归全 exit 0（FinalTick 15、SavedDefinition 14、SchemaDynamic 6、ConfigTypeIsolation 4、RegistrationSnapshot 5）。
- 340 份源码仅 1 份改变（`RecipeEditorScreen` +29/−10）；568 class 仅 1 个改变。Gradle test=NO-SOURCE。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（38s）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.42.jar` 均为 1,633,273 字节、同一 SHA-256；本归档为该产物的逐字节复制（`cmp` 通过）。旧 0.121.39/40/41 JAR 未改写。

与 0.121.41 归档同为 973 个 entry、名称顺序哈希一致；内容差异仅 `META-INF/mods.toml`（版本）与 `RecipeEditorScreen.class`，另有 911 个 entry 的 ZIP 时间戳不同。本归档取自最终构建，不声称另一次 rebuild 会得到同一 SHA。

## 边界

离线原生调用（内存屏幕壳、真实控件与原生按钮回调），无真实 GUI/IME/GPU、完整 Forge/JEI、多人网络或区块 IO 验收；实机未验。未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区；标 **待 Codex 验收**。单项交付后停止。
