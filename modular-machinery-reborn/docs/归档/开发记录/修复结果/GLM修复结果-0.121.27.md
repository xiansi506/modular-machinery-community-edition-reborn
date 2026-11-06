> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex复核：G1～G4通过离线代码验收，实机/完整网络仍待验；下方待验文字为交付时历史状态。**

# GLM 修复结果 0.121.27（G1～G4，2026-11-02）

**执行者：GLM-5.3 Flash，按[修复交接-GLM5.3Flash-2026-11-02.md](../任务交接/修复交接-GLM5.3Flash-2026-11-02.md)交付。四项自测通过，状态统一为“待Codex验收”；未修改任何原始勘测报告、累计账本或未修清单，未解除主任务暂停。** 本批只做 G1～G4，未接续历史任务，未部署/启动游戏，未暂存/提交/推送。

## 结果总表

| 任务 | 状态 | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| G1/P01-03 | 完成，待Codex验收 | `authoring/RecipeDocument.java` `summary()`：amount 缺省分支对 fluid 同 item 取 1；逐tick基础总量条件从 `raw != null` 放宽为 `raw != null || kind.equals("fluid")` | 改前夹具 G1/checks-prefix.log：3 项 FIX 断言失败（输入/输出/逐tick 缺省均显示 `0 mB`），解析器对照确认缺省实为 1 | G1/checks-postfix.log 9/9 通过、退出0：缺省显示 `1 mB/craft`、逐tick `1 mB/tick | base total 20 mB`（recipeTime=20）；显式 1/250、物品默认 1、能源 40 FE/tick（total 4000）、chance/selector 行为不变；气体缺省仍 0、未完成草稿摘要不再解析整个草稿 | 真实编辑器界面渲染、全部 TYPES 的正式默认值完整对照（P04 范围）未做 |
| G2/P01-05 | 完成，待Codex验收 | `selection/ConstructExport.java` `definition()`：`wantsNbt = matchingNbt/previewNbt 至少一个非空`；仅此时 `getBlockEntity`，每符合条件实体一次 `saveWithoutMetadata()`，两输出共享快照 | 改前夹具 G2/checks-prefix.log：两种 NBT 均空时 `saveAdditional` 计 1 次（both-empty 与 prepare/save 两用例失败），其余 9 项对照通过 | G2/checks-postfix.log 11/11 通过、退出0：均空 0 次序列化且无 nbt/preview-nbt、其余结构信息正常；仅 matching / 仅 preview / 两者不同字段各恰 1 次、互不污染；replacements、casing（Unsafe 实例）、无实体、忽略空气 0 次；未加载位置仍拒绝；`*`/嵌套路径/坐标清理/源tag不变性/prepare+save 两次独立扫描均复验 | 真实大型模组库存耗时、真实区块上下文未测；无性能百分比声明（仅序列化计数） |
| G3/P10-09 | 完成，待Codex验收 | `client/FactoryControllerScreen.java` `threadLabel()`：核心线程名称改为 `Component.translatable(name).getString()`（缺键自动回退原文） | 改前夹具 G3/checks-prefix.log：`container.inventory` 已在 Language 中但 threadLabel 返回原键名（2 项 FIX 失败），字面/含点号字面/null/普通线程 4 项对照通过 | G3/checks-postfix.log 6/6 通过、退出0：键名显示 `Inventory`（=当前语言 translatable 结果）；字面 `Literal name`、非键含点号名保持原文；null 保持 `""`；普通线程仍 `gui...thread`+自身稳定线程号（观测 `Thread #5`）；删键→回原文、恢复→再翻译，无永久静态缓存 | 真实窗口/字体渲染、语言切换的完整资源重载流程未做（夹具直接改真实 Language 表验证） |
| G4/P02-07 | 完成，待Codex验收 | `menu/FactoryControllerMenu.java`：新增 `clientMaxParallelism` 字段（初始 1）；匿名 `ContainerData.set` 新增 `DATA_MAX_PARALLELISM` 分支写入镜像（`Math.max(1,value)`）；`maxParallelism()` 改读镜像 | 改前夹具 G4/checks-prefix.log：接收 128 后 getter 仍 1（128→4→1 各步失败、独立性失败，共 4 项 FIX 失败）；fresh 默认、floor、客户端不回写 3 项对照通过 | G4/checks-postfix.log 7/7 通过、退出0：接收 5/128 两 getter 分别为 5/128；128→4→1 依次替换；新开未同步菜单保持 1；服务端权威 ceiling=42 时 get 恒读 42 且客户端 set 不改机器状态；0/-5 保持 ≥1 下限；threads/parallelism 两镜像互不影响 | 真实多人网络同步时序、重连/开窗初值组合、真实屏幕绘制未做；`quickMoveStack`/`stillValid` 及其余索引未改动（见增量 diff），未动态重测 |

## 版本与基线

| 项 | 值 |
|---|---|
| 起始 | 0.121.26 / 协议12 / HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`（与交接基线一致） |
| 结束 | **0.121.27** / 协议12（本批未改协议） |
| 交接指纹核对 | 四个目标文件改前 SHA-256 与 `_audit/glm53flash-repair-handoff-2026-11-02/` 的 `before/`、`target_sha256` 完全一致，无人先改 |

### 四文件改前/改后 SHA-256

| 文件（相对 `src/main/java/com/reborn/modularmachinery/`） | 改前（=交接基线） | 改后（0.121.27） |
|---|---|---|
| `authoring/RecipeDocument.java` | `38864B2F13C6B1A6F9F09C3E6F357B6DBC2E6429C190D46B98C9000A2C81492E` | `C7EBBE602D4ACD24063A5CB123C85F01F9FCF23B2ABFF38D113EE73B1F4B19D3` |
| `selection/ConstructExport.java` | `7C53176A7BFBF34B2700E187595B29167164C210D0C5E7EE0B81953B94FF4EF3` | `209385C44C5C43339C6CB1372A70B655D5F5018C3D5F29CE1044E8E1F37BF4DF` |
| `client/FactoryControllerScreen.java` | `FC8B92D9941383F6BA5A2D92D816CB3C8CF36E5F4A23C9D6F2A6D6B05DD80890` | `23786AE09D57B925ED5FD6ED1522B81D84CAB039697FA007985D6CDA4861DAA8` |
| `menu/FactoryControllerMenu.java` | `9A00EB310C11457940F70723114AB14DFF5458E610BE58ED2DE94A2CBC9901C8` | `4E5791E700EE5AEC2B8B4296C132397F349297F25E559D31803463AAC692EB03` |

本次增量 diff 入口：`_audit/glm53flash-repair-0.121.27/incremental-vs-handoff.diff`（相对交接 before 副本，共 +29/−4 行，只含上述四处；不把整个 HEAD diff 认领为本批成果）。`git status` 与交接基线逐项对照：本批新增/内容变更的跟踪文件仅四个白名单文件与 `gradle.properties`（mod_version 一行）、工作区及项目 `README.md`（各一行版本串）、`docs/en/README.md`（一行版本串）；这些 README/gradle.properties 在交接时即处于他人修改状态，本批只叠加了版本号这一处。

## 命令与退出码（日志均在 `_audit/glm53flash-repair-0.121.27/`）

| 步骤 | 命令要点 | 退出码 | 结果 |
|---|---|---|---|
| 夹具编译 | `javac -encoding UTF-8 -cp <classpath> -d fixture-classes G1..G4/*.java`（Java 17.0.11，Microsoft JDK） | 0 | 无错误（fixture-javac.log 仅历史失败尝试记录，最终版编译干净） |
| 改前运行（0.121.26 生产类，classpath-pre.txt 首位为 `fix-0.121.26/production-classes`，无其他生产类目录） | `java -Xmx1G -javaagent:token-agent.jar -Dfile.encoding=UTF-8 -cp fixture-classes;<cp>` 各夹具 | G1=1 / G2=1 / G3=1 / G4=1 | 各自按预期仅 FIX 断言失败：4/2/2/4 项，对照全过（checks-prefix.log） |
| 正式构建 | `JAVA_HOME=C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot` `./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true`（模组目录内） | 0 | BUILD SUCCESSFUL in 55s；`:compileJava`、`:reobfJar` 实际执行；Gradle test=NO-SOURCE（本项目无 Gradle 测试，不据此声称行为已测）（build-0.121.27.log） |
| 类加载来源 | ProvenanceCheck 打印五个相关类的 CodeSource | 0 | 全部仅指向 `…/glm53flash-repair-0.121.27/production-classes/`（class-loading-source.log；classpath-post.txt 中无任何旧生产类目录） |
| 改后运行（同一夹具最终版，production-classes 为 0.121.27 完整生产类只读快照） | 同上 | G1=0 / G2=0 / G3=0 / G4=0 | 9/11/6/7 项全部 PASS（checks-postfix.log） |

## 正式包与归档

| 项 | 值 |
|---|---|
| 构建输出 | `modular-machinery-reborn/build/libs/modular_machinery_reborn-0.121.27.jar`（下划线命名） |
| 归档 | `releases/modular-machinery-reborn/v0.121.27/modular-machinery-reborn-0.121.27.jar`，与构建输出 `cmp` 字节一致 |
| 大小 | 1625938 字节 |
| SHA-256 | `4B6A23CAB66A0BFBE8DFF86736EAD57BFE1B4D1489CBA139355F1DE72D2B9221` |
| JAR 内版本 | `META-INF/mods.toml` `version="0.121.27"`（构建注入，源文件保持 `${version}`） |
| 同步位置 | gradle.properties、工作区/项目 README、docs/en/README.md、`docs/归档/版本记录/更新记录-0.121.27.md`、`releases/modular-machinery-reborn/README.md` 索引（新条目置顶，旧归档保留） |

## 夹具说明与诚实记录

- 夹具全部调用真实生产方法：G1 用实际 `RecipeDocument.validate`（完整 serializer）核对解析量并调用真正 `summary()`；G2 用计数 `FurnaceBlockEntity`（覆写 `saveAdditional`）驱动真实 `definition()/filter()/prepare()/save()`；G3 用真实 `Language/Component` 与真实 `FactoryThread`（反射调用 `threadLabel`，无 OpenGL）；G4 实例化真实菜单匿名 `ContainerData`（Unsafe 合成控制器，沿用历史 MenuAuditCheck 模式）。夹具源码与全部输入/期望/实测、命令、stdout/stderr、退出码保存在 `G1/…G4/`。
- 开发过程中修正过三处夹具自身错误并重跑（生产代码未因此返工）：selector 渲染实为带引号的 `selector "audit"`、`"0 mB"` 子串误匹配 `20 mB`、filter 路径语言需前导 `/`（`audit/deep` 按现有语义是单键名）、`prepare()` 返回 Draft 需调用方存回 session、furnace 描述符含 blockstate 属性。**最终版夹具在改前/改后两类上各跑一遍**，上表两行即同一夹具版本的成对证据；首次运行的旧日志已被最终版覆盖，修正原因如上如实记录。
- G4 的“发送侧 get 权威”用例在改前日志中于“client mirror updated”断言处失败（该用例同时含修复断言与对照断言，执行到失败点前其 get==42 对照已通过）；改后全通过。
- 未触及白名单外业务代码；未修改 P04-10（WILL 大写，交接明确不分配）；未运行旧报告夹具（其 PASS 语义是“成功复现缺陷”，按交接要求未误用）；未删除/改写任何历史审计证据；`build.gradle`、协议、语言文件、其他菜单/控制器类均未改。

## 遗留给 Codex 验收

1. 逐项复读四处生产改动（尤其 G4 的菜单内镜像 vs 块实体镜像的取舍、`maxParallelism()` 服务端语义变化——现仅屏幕这一客户端消费者，服务端读取仍走 `machine.factoryParallelCeiling()`）。
2. 复核 `docs/en/README.md` 的版本行是否还需补充英文批次说明（本批只做最小版本串同步）。
3. 验收通过后再更新累计修复账本（P01-03、P01-05、P10-09、P02-07）与未修清单；未实机项（真实画面/网络/开窗组合）仍标未实机。
