# v0.121.47 — 第八批 N 组（P01-02 / P01-04，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.47.jar` **1,636,536 字节**，SHA-256 `D6BFBB12AF098B0517178C81E32DB4745D3C0A09D3E3D1DD39E817611A2B878C`. 起点 **0.121.46**（第七批 M 组已修的最终源码/class）。**标待 Codex 验收。**

## 本版内容

- **P01-02（N1，创作者界面的迟到回包）**：配方编辑器与结构工具都增加“本地编辑代次”——控件每次真实改动让 `localEdits` 前进，`collect()` 真正把值并入模型后才追平 `collectedEdits`。两者不等时，迟到的 `valid`/`saved`/`error` 回包只能更新服务端 revision 与提示，不能重建表单覆盖请求之后输入的内容、不能替作者切预览页，也不能把未验证的新草稿恢复成可导出。程序化初始化与逐字回填走 `programmatic` 开关，因此缩窗/重建不会被误判成“永远脏”；没有本地编辑时行为完全不变（校验成功照旧切预览并开导出）。结构工具的 `update` 只在没有本地编辑时才用服务端 `options` 覆盖并跟随服务端页面，避免超时后改的显示名称被旧值顶回去。
- **P01-04（N2，原文深度边界）**：`RecipeDocument.read` 的深度检查从“只认双引号的自制字符扫描”改为**解析器自己的 `JsonReader`（lenient）流式 token 检查**，语法与随后的 `JsonParser` 同源：单引号字符串、注释、未加引号的键名里的括号不再影响计数，16000 字符能表达的极深树也在任何递归解析/`toString`/`deepCopy`/表单构建之前被拒。上限 64（根对象计 1）、16000 字符 / 24000 UTF-8 字节、顶层必须是对象等既有契约不变；错误仍是可恢复的受控异常，编辑器保留原文。

## 验证

- `LateReplyCheck`（P01-02）：68 条断言全部走真实入口——`RecipeEditorScreen.accept(state,false)` → `update` → `rebuild`，`ConstructToolClient.accept` → `ConstructToolScreen.update`，超时走真实 `tick()` 的 `waiting>100` 分支，控件是原生 `EditBox`/`MultiLineEditBox`。覆盖 100→200 未收集、非法数值 `abc`、半成品原文、改 ID/格式、结构工具的显示名称/状态/NBT 选项、迟到 `valid`/`saved`/`error`、重复回复、缩窗、关闭重开、以及“无本地变更时仍切预览/开导出”的对照。0.121.46 类上 17 条失败（复现），0.121.47 最终 class 上 0 失败。
- `DepthCheck`（P01-04）：期望深度由生成器自己记账并由独立计数器复核，含 1/2/32/63/64/65/66/100/200 容器树、报告里的单引号括号抵消（65/70/200）、双引号对照、单/双引号转义、字符串与注释内的括号、畸形/未闭合/顶层非对象、字符与 UTF-8 字节上限、5000 层深树必须是被控异常而不是 `StackOverflowError`。0.121.46 上 4 条失败（单引号抵消），0.121.47 上 37 条全过。
- H1 回归（第五批，草稿/表单身份）：`EditorDraftCheck` 11、`EditorMutationCheck` 14、`EditorStructureCheck` 15、`EditorExtraCheck` 17、`MutationMatrixCheck` 23、`MutationOnlyCheck` 3 全过；`FinalTickCheck` 退出 0。
- 源码边界：340 份源码中只有白名单 3 份改变，`340→340`、`568→568` class 无增删。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（1m 4s，`test NO-SOURCE`）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.47.jar` 均为 1,636,536 字节、同一 SHA-256；973 entry / 568 class / 221 JSON 全可解析；中英各 566 键且键集一致；`mods.toml` 0.121.47；出货 class 仍为 SRG 名（`m_237110_`），协议 12 未变。归档为逐字节复制，旧归档未改写。

## 边界

真实 Screen 接收入口、真实 `EditBox`/`MultiLineEditBox`、真实 `RecipeEditorService.state`/`ConstructOptions`/`FactoryThread` 数据，壳 `Minecraft`/`ClientLevel`（只提供维度键）与壳 player。离线没有 Netty 连接：`ModNetwork.CHANNEL.sendToServer` 在类初始化即失败，故“已发送”状态由 `send()` 会设置的同一 `waiting` 字段驱动，真实联机时序未验。未实机、未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.47.md)。
