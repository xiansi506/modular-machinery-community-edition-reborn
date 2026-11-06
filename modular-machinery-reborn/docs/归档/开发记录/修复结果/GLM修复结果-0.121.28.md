> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex结论：本批通过离线验收；无真实游戏/FML/完整网络/GPU验收。** 详见项目docs/归档/开发记录/验收记录/验收-GLM第二批12项-2026-11-03.md。下方“待验收/自测全过”为交付时状态。

# GLM 修复结果 0.121.28（第二批 A 批 A1～A4，2026-11-03）

**执行者：GLM-5.3 Flash，按[修复交接-GLM5.3Flash-第二批12项-2026-11-03.md](../任务交接/修复交接-GLM5.3Flash-第二批12项-2026-11-03.md)交付 A 批。四项自测通过，状态统一为“待Codex验收”；未修改累计账本或未修清单。** B 批（0.121.29）与 C 批（0.121.30）随后按同一流程交付，本批文件副本与快照保留。

## 结果总表

| 任务 | 状态 | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| A1/P04-06 | 完成，待Codex验收 | `recipe/MachineRecipeJsonBuilder.addChance`：增加 `Float.isFinite` 前置拒绝 | A1/checks-prefix.log：NaN 经 `itemOutput` 被接受且 catalystChance(NaN) 把已有 0.25 改掉（2 项 FIX 失败；0/0.25/1 边界与覆盖语义 2 项对照通过） | A1/checks-postfix.log 4/4、退出0：NaN/±Infinity 拒绝且不动已有 0.25；-0.1/1.1 拒绝；0/0.25/1 经真实 serializer 值正确；1 清除、0 显式保留 | KubeJS/Rhino 层参数转换未跑（Java typed 入口已复现）；实际游戏未测 |
| A2/P04-14 | 完成，待Codex验收 | `recipe/RecipeModifier.requireFloat`：`getAsFloat` 后检查 `Float.isFinite`，报错含字段名与来源 | A2/checks-prefix.log：±1e1000 被接受为 ±Infinity（2 项 FIX 失败）；读取契约与有限系数语义 3 项对照通过 | A2/checks-postfix.log 5/5、退出0：±1e1000、3.5e38、3.4028235e39 拒绝且消息含 `multiplier` 与来源；Float.MAX_VALUE/3.4e38 有限保留；真实 EnergyRequirement：x2→200、ADD -30→70（负系数仍生效）、x-3→既有下限 0 | 多个有限系数相乘溢出、operation 精确整数校验、P05 聚合策略按交接未处理 |
| A3/P03-08 | 完成，待Codex验收 | `machine/MachineSchema.readDynamicPatterns`：LinkedHashMap 记录首见条目，重名抛 `JsonParseException`（含机器来源、名称、两个条目下标） | A3/checks-prefix.log：同机重名（不同长度/相同配置）被接受为两个段（2 项 FIX 失败）；异机同名、同机异名 findMatch 1/2 + runtimeStructure 一致、缺name/零节/parts-end/min-max 范围 4 项对照通过 | A3/checks-postfix.log 6/6、退出0：同机重名拒绝，观测消息含 `test`/`same`/`entries 0 and 1`；其余行为全部保留；`Same`/`same` 按精确身份视为不同名且原文保留 | 真实四朝向实机匹配未跑（夹具 NORTH + 静态说明 diff 仅加名称检查）；已存在于存档的重名旧定义按交接不做迁移，需作者更名 |
| A4/P03-11 | 完成，待Codex验收 | `recipe/ConfigRecipes.validate`：改为逐文件抛出含路径原因（损坏JSON/空/缺type/非字符串type/错误type字符串）；`readAll` 按文件捕获 RuntimeException 记录后继续 | A4/checks-prefix.log：`{"type":{}}` 使实际 readAll 与 AddPackFinders 监听抛 UnsupportedOperationException（3 项 FIX 失败）；正确字符串文件 1 项对照通过 | A4/checks-postfix.log 4/4、退出0：对象/数组/null/数字/布尔/缺失/错误字符串/损坏JSON 八类逐文件报告（log4j 捕获含路径）并排除，子目录正确文件保留；ConfigRecipePack 只服务正确文件；finder 正常完成 | 真实 Forge 事件总线、启动与 `/reload` 未跑；行为变化说明：缺type/错误字符串文件此前被提供由原版丢弃，修复后不再进入提供列表 |

## 版本与基线

| 项 | 值 |
|---|---|
| 起始 | 0.121.27 / 协议12 / HEAD `f94ba299…`（与 12 项交接基线一致，四目标文件改前 SHA-256 与 `_audit/glm53flash-handoff-12tasks-2026-11-03/` 完全一致） |
| 结束 | **0.121.28** / 协议12 |
| 改前类基线 | `_audit/review-glm-0.121.27-2026-11-03/production-classes/`（交接指定；与我方交付快照源码相同、编译产物字节不同，属编译环境差异） |

### 四文件改前/改后 SHA-256

| 文件（相对 `src/main/java/com/reborn/modularmachinery/`） | 改前（=交接基线） | 改后（0.121.28） |
|---|---|---|
| `recipe/MachineRecipeJsonBuilder.java` | `FB42F203A8F20720534F7A57220D43FF0722C7F718765B75042C3FA5F0315F48` | 见 `_audit/glm53flash-12tasks-0.121.28/after-hashes.txt` |
| `recipe/RecipeModifier.java` | `3C1293AE262DCE780C3092CAC070F9A5069A5BE697985C45ECEFE6B85EF659CE` | 同上 |
| `machine/MachineSchema.java` | `93C293EFC5F07568293764A5E8555E1974F0ABE587465A4215F69DD2EC5CD016` | 同上 |
| `recipe/ConfigRecipes.java` | `2EAF7CFCCAF124D8B14B4B50824E2CDEA7D15216E0E179256D81ED18085EEEF6` | 同上 |

增量 diff 入口：`_audit/glm53flash-12tasks-0.121.28/incremental-vs-handoff.diff`（相对 12 项交接 before 副本，仅含四处白名单修改）。

## 命令与退出码

| 步骤 | 命令要点 | 退出码 | 结果 |
|---|---|---|---|
| 夹具编译 | `javac -encoding UTF-8 -cp classpath-pre.txt -d fixture-classes A1..A4/*.java`（Java 17.0.11） | 0 | 无错误 |
| 改前运行 | `java -Xmx1G -javaagent:token-agent.jar -Dfile.encoding=UTF-8 -cp fixture-classes;<classpath-pre>` | A1=1 A2=1 A3=1 A4=1 | 各自仅 FIX 断言失败，对照全过（checks-prefix.log） |
| 正式构建 | `JAVA_HOME=C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot` `./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true` | 0 | BUILD SUCCESSFUL in 28s；compileJava/reobfJar 实际执行；Gradle test=NO-SOURCE（build-0.121.28.log） |
| 类加载来源 | Provenance 打印五个相关类 CodeSource | 0 | 全部仅指向 `…/glm53flash-12tasks-0.121.28/production-classes/`（class-loading-source.log；classpath-post.txt 无旧生产类目录） |
| 改后运行 | 同上 | A1=0 A2=0 A3=0 A4=0 | 4/5/6/4 项全部 PASS（checks-postfix.log） |

## 正式包与归档

| 项 | 值 |
|---|---|
| 构建输出 | `modular_machinery_reborn-0.121.28.jar`（build/libs） |
| 归档 | `releases/modular-machinery-reborn/v0.121.28/modular-machinery-reborn-0.121.28.jar`，cmp 字节一致 |
| 大小 / SHA-256 | 1626741 字节 / `15A5DE4B674A6D080FAD215BE3E3A1F304A2ABDA4A538EE17E5346D56FBDC153` |
| 同步位置 | gradle.properties、工作区/项目 README、docs/en/README.md、`docs/归档/版本记录/更新记录-0.121.28.md`、release 索引（新条目置顶，旧归档保留） |

## 夹具说明与诚实记录

- A1 初版夹具两次自身错误已修正并重跑（生产代码未返工）：`build()` 产物不含根 `type`（由 KubeJS 层补齐，夹具按实际管线补 `"type": "modular_machinery_reborn:machine"`）；一次漏 import 导致旧类残留被误读为运行结果，已 clean rebuild 后重跑。
- A2 正向对照按实际语义修正：负 MULTIPLY 系数在既有 `EnergyMath.floor` 下取 0（与旧复现 `-Infinity→0` 同源），改用负 ADD 系数（-30→70）证明负系数未禁用；断言与生产语义一致，非降低断言凑通过。
- A3 夹具 World 改为 Unsafe 分配（跳过 ClientLevel 构造器），与历史 P03-b 夹具一致。
- A4 用 log4j 内存 Appender 捕获真实错误报告，断言每个错误文件的路径出现在 ERROR 消息中；FMLPaths.CONFIGDIR 绑定审计临时目录并在 finally 恢复，未写玩家 config。
- 白名单外业务文件未改；P04-10、P03-12、P03-14 等未分配项未动；未暂存/提交/推送、未部署游戏。工作区其余未提交改动（他人批次）全部保留。
