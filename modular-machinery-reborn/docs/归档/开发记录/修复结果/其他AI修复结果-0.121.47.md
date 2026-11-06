> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex[独立验收](../验收记录/验收-第八批4项-2026-11-03.md)：N2通过，N1新增组合仍丢本地草稿，需要返工。** [N1返工](../任务交接/返工交接-第八批N1-2026-11-03.md)。下文原交付结论为历史，主任务保持暂停。

# 其他AI修复结果：第八批 N 组（0.121.47，2026-11-03）

**标待 Codex 验收。** 本批交付第八批交接的 N1（P01-02）与 N2（P01-04）两项，从 0.121.46 的真实源码/class 递增为 **0.121.47**，协议 **12** 不变。O 组（P10-04 / P06-17）随后在 0.121.48 交付，本文件只覆盖 N 组。

## 起点核对

- 交接基线 `_audit/eighth-ai-handoff-4tasks-2026-11-03/`：340 份源码哈希、`accepted-class-hashes.json`、`git-status.txt`、`existing-tracked.diff`。开工前逐份重算 `production-sha256.json`：**340/340 一致，0 缺失、0 改动**，HEAD 仍为 `f94ba299e37f0975ac5255f90671f990bacfc61a`，工作区 782 项未提交改动原样保留（未 reset、未 checkout 整文件）。
- 改前类快照 `_audit/eighth-ai-4tasks-0.121.47/classes-0.121.46`（568 class，取自第七批交付后的 `build/classes/java/main`），改前源码副本在 `before/`（3 份，含 SHA-256）。
- 交接文档本身列出的白名单：N1 = `client/RecipeEditorScreen.java`、`client/ConstructToolScreen.java`；N2 = `authoring/RecipeDocument.java`。本批实际只改这 3 份。

## 完成情况

| 编号 | 任务 | 状态 | 版本 |
|---|---|---|---|
| P01-02 | 迟到回包保留尚未收集的本地编辑 | **完成** | 0.121.47 |
| P01-04 | 原文深度检查与实际 JSON 语法一致 | **完成** | 0.121.47 |
| P10-04 | 工厂队列长状态限高/裁剪/完整 tooltip | 未做（O 组） | — |
| P06-17 | 普通控制器能耗快照用实时有效修改器 | 未做（O 组） | — |

不可复现项：无。超范围项：无。

## P01-02：迟到回包不再覆盖请求之后的输入

### 改前事实（0.121.46 真实 class 复现）

`RecipeEditorScreen.update` 只用 `root` 与 `reviewedSource` 比较，屏幕上有、但还没被 `collect()` 收进 `root` 的控件文本不参与判断；`dirty()` 只取消已校验标志，不阻止旧回包重建控件。`ConstructToolScreen.update` 虽然先 `collect()`，成功分支紧接着用服务端旧 `options` 覆盖。

复现路径（`LateReplyCheck`，0.121.46 上 17 条正确行为断言失败）：

- 配方时长 `100` → 发送 → 客户端 100 tick 超时恢复输入 → 改 `200`（未 collect）→ 旧 `valid` 回包到达：界面直接跳到预览页，`200` 丢失（`actual=<absent>`）。`saved` 回包把 `300` 直接重建回 `100`；`error` 回包把改过的 recipe id `custom:other` 重建回 `custom:recipe`；非法值 `abc` 同样被重建丢失。
- 原文页输入半成品 `{"machine":"test:machine","requirements":[` 后收到旧 `valid`：被切到预览页，原文不可找回。
- 结构工具：超时后把显示名称改成 `New local draft`，旧 APPLY 回包到达后界面被切回选区页、`options.displayName` 恢复为 `Custom Machine`。

### 修复

- `client/RecipeEditorScreen.java`
  - 新增 `localEdits` / `collectedEdits` 代次：控件的真实改动（`EditBox` responder、`MultiLineEditBox` 的 value listener）让前者前进；`collect()` 真正把值并进 `root`（基本页 `RecipeForm.root`、需求页 `RecipeForm.requirement`、原文页解析成功）后才把后者追平。
  - `update()` 先算 `pending = localEdits != collectedEdits`：有未收集输入时只更新 `state`（服务端 revision）与提示，`valid` 不再切预览、不再 `previewItems.clear()`，并强制 `dirty()` 让导出保持关闭；未收集的控件内容在 `rebuild()` 后由 `restore()` 逐字回填。
  - 程序化回填（建立控件、回包还原、缩窗还原）走 `programmatic` 开关（`init()` 薄包装 + `try/finally`），因此 rebuild 不会被当成一次编辑，编辑器不会“永远脏”。
  - 方案不是“忽略所有回包”，也不是“超时后永久禁用”：没有本地编辑时 `valid` 照旧切预览并开导出（R9/R10 对照）。
- `client/ConstructToolScreen.java`
  - 同一套代次（`localEdits`/`collectedEdits`/`programmatic`），`field()` 安装 responder。`update()` 在有待收集输入时**不**调用 `collect()`、**不**用服务端 `options` 覆盖、**不**跟随服务端页面切换，只更新 `state`（revision、选区计数等）并逐字回填控件；无本地编辑时行为与改前一致。
- 未改任何网络编码/协议、服务端会话、资源支付、FactoryEngine、全局渲染；`token`/维度隔离与服务端复验仍在原处。

### 验证

`_audit/eighth-ai-4tasks-0.121.47/N1`（`LateReplyCheck`，68 条断言）：

- 真实接收入口：`RecipeEditorScreen.accept(state,false)`（真实 token/维度校验 → `update` → `rebuild`）、`ConstructToolClient.accept(state,false,0)` → `ConstructToolScreen.update`。
- 真实超时分支：`tick()` 的 `waiting>0 && ++waiting>100`；真实控件 `EditBox`/`MultiLineEditBox`（`setValue` 走原生 `onValueChange`，与按键同路径）。
- 回包负载由真实 `RecipeEditorService.state()` 从真实 `Session`/`Draft` 渲染（`validate()` 需要在线 MachineRegistry/ModList，离线不可用，故 `draft` 与 `revision` 按 `Session#validate` 的赋值方式给出）。
- 覆盖：100→200 未 collect、非法数值、半成品原文、改 ID、改格式、结构工具显示名称/状态/NBT 选项、迟到 `valid`/`error`/`saved`、同 token 同内容重复回复、缩窗、切页、关闭重开、无本地变更对照、程序化 rebuild 对照。
- 结果：0.121.46 class **17 FAIL / 51 PASS**（缺陷复现）；0.121.47 最终 class **68 PASS / 0 FAIL**。
- 重跑第五批 H1 回归（`_audit/fifth-h1-mutation-0.121.42/H1/fixture`）：`EditorDraftCheck` 11、`EditorMutationCheck` 14、`EditorStructureCheck` 15、`EditorExtraCheck` 17、`MutationMatrixCheck` 23、`MutationOnlyCheck` 3 全过（`SUMMARY ... fail=0`）。草稿/旧模型身份、非法原文保留、删除/添加/类型切换/资源选择/拖入后旧原文不复活均保持。

### 相关性边界（如实记录）

协议 12 的回包只有 token 与 revision，没有请求序号，同一 token 下内容相同的两次请求在网络上不可区分。本项保证的是“可观察到的新本地草稿不被旧内容覆盖”，不宣称任意乱序/重组包都具备请求级一致性；服务端 revision 仍会拒绝过期请求。

## P01-04：深度检查改用它自己的 tokenizer

### 改前事实

`read()` 在 `JsonParser` 之前自行扫字符，只把双引号当字符串。`JsonParser` 用宽松 `JsonReader` 解析（`JsonParser.parseReader(JsonReader)` 字节码里先 `setLenient(true)`），因此单引号字符串、注释、未加引号的键名都合法。单引号值里放一串 `}` 会把计数压低：`{"pad":'}}…',"deep":[…66 层…]}` 在 0.121.46 上被接受（实测 66/71/201 层三种规模都 `accepted`）。

### 修复

`authoring/RecipeDocument.java`：删除自制字符扫描，改为 `JsonReader`（`setLenient(true)`，与 `JsonParser` 同一设置）**流式 token** 深度检查：`BEGIN_OBJECT`/`BEGIN_ARRAY` 计数 +1 并即时比较上限，`END_*` 减 1，`NAME` 用 `nextName`、标量用 `skipValue`，因此每个 token 只走一次且不递归。检查在任何递归消费者（`toString`/`deepCopy`/表单构建）之前完成，所以 16000 字符能表达的极深树不会先把栈走穿。上限常量提取为 `MAX_DEPTH = 64`（根对象计 1）；16000 字符 / 24000 UTF-8 字节、顶层必须是对象、错误为可恢复受控异常等契约不变。未改通用配方加载器、KubeJS 语法或任何协议长度。

### 验证

`_audit/eighth-ai-4tasks-0.121.47/N2`（`DepthCheck`，37 条断言）：

- 期望深度由生成器自己记账，并由本文件里一个**独立计数器**复核生成文本（不用生产扫描器算期望）。
- 深度 1/2/32/63/64 接受、65/66/100/200 拒绝；报告形状的单引号括号抵消在 65/70/200 三种规模下均拒绝，63 层对照接受；双引号同形为对照（改前也已拒绝）。
- 单/双引号转义、字符串内 20 个 `}`、注释内 `}`/`]` 均不影响计数；`{machine:'test:machine',requirements:[]}` 这类宽松写法仍被接受（没有被悄悄改成严格模式）。
- 畸形/未闭合/多余收尾/尾随内容/两个顶层值/顶层数组/顶层数字/空串全部是受控拒绝（`IllegalArgumentException` 或 Gson 的 `JsonSyntaxException`）。
- 5000 层（10000 字符）是被控拒绝，**没有** `StackOverflowError`；16000 字符与 24000 UTF-8 字节上限保持。
- 结果：0.121.46 class **4 FAIL / 33 PASS**（单引号抵消 + 64 层边界）；0.121.47 最终 class **37 PASS / 0 FAIL**，整套 84 ms。

## 构建与归档

- 环境：`JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot`、`GRADLE_USER_HOME=C:/mmwork/.gradle-home`，离线 `--no-daemon` 的 `clean build`。**必须由 JDK 17 的 `java.exe` 启动 wrapper**（用系统默认的 Zulu 25 启动时 Gradle 8.1.1 检测不到 17 toolchain，`compileJava` 直接失败；两次失败调用记录在 `logs/build-0.121.47-attempts.txt`，不是交付构建）。
- 完整日志：`logs/build-0.121.47.log`，命令 `logs/build-0.121.47-command.txt`，退出码 `logs/build-0.121.47-exit.txt` = 0；`BUILD SUCCESSFUL in 40s`（首次成功构建 1m 4s，改掉一处只为消除合成 class 的 `switch` 后重建），`test NO-SOURCE` 如实保留在日志中。
- 产物：`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.47.jar`，**1,636,536 字节**，SHA-256 `D6BFBB12AF098B0517178C81E32DB4745D3C0A09D3E3D1DD39E817611A2B878C`；973 entry / 568 class / 221 JSON+mcmeta 全部可解析；中英各 566 键且键集一致；`mods.toml` 0.121.47；出货 class 仍为 SRG 名（`m_237110_`），协议 12。
- 归档：`releases/modular-machinery-reborn/v0.121.47/`（JAR 逐字节复制 + README.md + SHA256.txt），`cmp` 与 `output.jar` 一致；旧归档未改写。
- 最终 class 快照：`_audit/eighth-ai-4tasks-0.121.47/production-classes-0.121.47`（568 份**映射名** class，取自 `build/classes/java/main`，供夹具运行；出货的 SRG jar 另存，二者 class 数一致）。
- 源码边界：`validate_jar.py` 输出 `evidence-0.121.47.txt` —— 340 份源码中只有白名单 3 份改变，`568 → 568` class 无增删；改后 340 份哈希清单 `source-hashes-after-0.121.47.json`。

## 未验事项

- 未实机、未部署、未启动游戏；未动玩家整合包/存档。
- 离线没有 Netty 连接：`ModNetwork.CHANNEL` 在类初始化即抛 `ExceptionInInitializerError`（夹具已记录该探针），所以“已发送 → 等待”这一状态由 `send()` 会写入的同一 `waiting` 字段驱动，真实联机时序、真实乱序/重发未验。
- `Minecraft`/`ClientLevel`/`LocalPlayer` 是壳对象（只提供 `dimension()` 的维度键与 `screen`），没有真实客户端循环、字体图集或 OpenGL；界面行为由真实 Screen/控件逻辑验证，不是截图。
- 真实服务端 `RecipeEditorService.validate()`（需要在线 `MachineRegistry`/`ModList`）未执行；回包负载由真实 `state()` 渲染，`Draft` 由真实 record 构造。
- 未暂存/提交/推送、未创建 GitHub Release、未 reset 工作区、未改累计修复账本（仍 76/5/41）、未解除暂停。
