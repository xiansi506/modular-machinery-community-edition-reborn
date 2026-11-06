# v0.121.41 — 第五批 H1/I2 返工（2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.41.jar` **1,633,168 字节**，SHA-256 `2E36D2026CAE75C2FFE1DF5738AC58E3765CB816E776DE77466674EDC442167B`。真实起点为 **0.121.40**（340 份源码哈希与验收指纹逐份一致，mismatch 0；起点 568 class 快照与 0.121.40 交付快照逐字节一致）。H2/I1 已通过，不重做。

## 本版内容（第五批 H1/I2 返工）

- **H1 / P01-01（结构校验提前）**：`RecipeEditorScreen.collect()` 的原文页分支先在局部变量解析并调用 `RecipeDocument.formStructureError`，通过才发布 `root`；失败只设带 `requirements[序号].字段` 位置的消息并留在原文页。新增 `sourceDraft` 逐字草稿：编辑实时写入、重建前从原文框收集、原文页从草稿还原、表单提交后清空；缩窗走既有 `savedValues` 并在应用后同步草稿。`init()` 的同一检查保留为进入表单模型前的最后防线。校验范围不变（requirements 数组/条目对象/type 非空字符串/已知类型布尔），不要求完整服务端语义，不删除未知高级需求字段。
- **I2 / P10-08（重建意图一致）**：`HatchGroupScreen.init()` 在 `localIntent` 为 true 时保留本地镜像（仍待确认的同一意图），无意图时才采用菜单快照；确认到达后由既有 `containerTick` 清除意图。菜单 wire/服务端处理/数字提交/关闭不提交数字的语义未改，旧协议拒绝/延迟不可区分限制如实保留。

## 验证

- 夹具（`_audit/fifth-rework-h1i2-0.121.41/`）：0.121.40 快照按正确行为 repro 复现——Codex `EditorExtraCheck` 15/17（两条 EXTRA 失败：原文被格式化、root 变 `test:other`）、`SwitchExtraCheck` 15/17（`mirror=false` 卡住）；本批补充 `EditorDraftCheck` 5/11、`IntentRebuildCheck` 7/13。prebuild 与 0.121.41 的 568 class 快照 fixed 全过：H1 17/17、11/11、原 15/15；I2 17/17、13/13、原 15/15；H2 12/12、I1 4/4；五组必要回归全 exit 0（FinalTick 15、SavedDefinition 14、SchemaDynamic 6、ConfigTypeIsolation 4、RegistrationSnapshot 5）。
- 源码 340 份仅 2 份改变（`RecipeEditorScreen` +32/−10、`HatchGroupScreen` +12/−3），568 class 仅 2 个改变。Gradle test=NO-SOURCE。
- 离线原生调用，无真实 GUI/IME/GPU、完整 Forge/JEI、多人网络或区块 IO 验收；实机未验。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（44s）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.41.jar` 均为 1,633,168 字节、同一 SHA-256；本归档为该产物的逐字节复制（`cmp` 通过）。旧 0.121.39/0.121.40 JAR 未改写。

**ZIP 容器差异说明**：与 0.121.40 归档同为 973 个 entry、名称集合与顺序哈希一致；内容差异只有 3 个 entry（`META-INF/mods.toml` 版本号与两个改动 class），另有 911 个 entry 的 ZIP 时间戳不同。上一轮“build 产物与归档 SHA 不同、解压内容一致”属同类容器元数据差异，不是代码差异；本轮归档直接取自最终构建产物，逐字节一致是同一文件的复制校验，不声称另一次 rebuild 会得到同一 SHA。

## 边界

未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区；标 **待 Codex 验收**。两项交付后停止，不扩展其他任务。
