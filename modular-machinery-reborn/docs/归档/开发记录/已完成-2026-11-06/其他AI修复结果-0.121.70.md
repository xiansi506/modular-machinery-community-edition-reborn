> **2026-11-05 Codex验收：仅部分改善，未知外部能力支持/安全拒绝仍未完成，不销号。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.70

**待Codex验收。本批编号：P05-02；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## H/P05-02：部分交付，明确支持矩阵；不自行销号

实施前`support-matrix.md`列明exact原生、自有仓口、显式候选能力、未知子类及Opaque共享视图。ResourceTransaction新增两个显式opt-in候选权限接口：CandidateItems/CandidateFluids接收候选内容副本，仅回答权限/速率；原候选副本仍负责容量/资源同一性，最终commit仍执行真实能力并核对实际移动。未知外部子类仍使用source.simulate，不以instanceof原生基类擅自放宽，不临时写实仓预检。

自有MachineHatch backing inventory原为仅增加onContentsChanged的匿名ItemStackHandler，却被错分为未知子类。本批命名HatchItems并显式声明纯原生库存候选规则，保留原输出inventoryChanged/存储通知。未改变仓容/外部方向门禁或IO路径。原生流体/LongEnergy及既有OpaquePort/ME耦合身份维持原路径。

真实69自有仓口先取1煤后在候选空位放铁的正确断言失败；70通过。CandidatePlanningCheck21条验证自有仓口交换实际commit、规划实仓仍煤；显式受限item/fluid候选取空→异物/异液、允许/禁止、同源视图身份、0真实写入、真实commit及late false原生恢复；未知forbidden insert始终拒绝而非绕权限。没有改通用回滚安全（I另做）。初次build漏ItemStack限定类名导致compile退出1，修正后完整clean build0；原日志attempt保留。

## 未收口范围

没有候选权限契约的任意外部子类仍可能保守误拒；不能靠source.simulate推断“候选先取空后”的允许规则。支持契约使自有/明确声明能力的登记场景真正可规划，但不保证所有未知capability透明支持。仍需决定具体希望支持的外部端口及是否能声明候选/可逆契约；不删除simulate、不临时改仓、不宣称通用透明已修。因此本编号在机器进度标**partial-supported-contract**，仍留未交付完成集合，不自行销号。

原共同30调用、V/W/X/Y/A多核心保持。资源不变、协议13，Java342/class572（接口与命名库存替换如实计数）。没有硬引用新的可选API或新增集成。真实候选操作与原生库存不等于Forge/Rhino网络实测。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.70/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 572 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.70/modular-machinery-reborn-0.121.70.jar`，**1,662,602字节**。build/libs=reobfJar/output.jar=新归档SHA：

`4B6F1B5238F6879D2D96E195F72681138C301A3C93D439EB93BA74C844CECDAE`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.70/README.md) · [版本记录](../../版本记录/更新记录-0.121.70.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 70 final <类名> 0 <模式>`；共同回归`regress 70 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
