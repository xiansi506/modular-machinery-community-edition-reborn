> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.63

**待Codex验收。本批编号：P06-08；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## A / P06-08：本批指定范围完成，待Codex验收

起点62/协议13/341 Java/342资源/569 class。仅FactoryEngine.tickThreads变更为稳定核心快照，并在每个tick前核对`coreThreads.get(name)==原对象`。tryRestart的原对象条件删除保留，不吞异常、不break跳过后续核心，不回退61、不禁用62合法已付恢复。新同名槽本tick不被当成旧快照槽处理；普通线程顺序、58提交前准入、核心/普通配额、身份与高水位均保持。

原MultiCoreDrainCheck源码从独立验收目录逐字节复制，正确断言不变：62完整类live/restore均退出1，ConcurrentModificationException；63两种入口均退出0，煤/铁各仅8→7、石头/金锭各1，最终无核心。原独立失败文件未覆盖，新证据在本批目录。

新增MultiCoreMatrixCheck40组/623项通过：两/三核心、相同/不同工期、撤销掩码涵盖首/中/末、仍声明与撤销混合、普通任务同时进行、live/Engine.load两入口。每个本tick应推进的后续核心与普通任务均推进，已付输入、完整FE、产出及start/finish通知顺序逐一精确断言；仍声明2tick核心可重开一次，再撤销后排空，其余只完成一次。补满输出阻塞/释放、RESET/STILL/DECREASE、阻塞中再次恢复、回调显式reset/同名新槽的身份排除。

既有Y84/29/21、V111/点击2/UI19/包11/魔法分段8、W55/54/mixed5/normal、X1811/26全部通过；第十二批共同30调用在改前62及最终63均通过，包括58 late/172/Busy13、T14/NBT12、复制安全fixed、FinalTick、ControllerExtra、Q1三条/原身份/428、创造60/暂停/恢复23/矩阵14、Will/LP/Bloom与结构各正确模式。原单核心通过不能代替本批新增多核心断言，因此两组都保留。

装置一次并行javac共用输出目录导致class读取异常（日志已保留attempt）；测试编译现按stage/类/模式使用独立目录，各JVM独立cwd，不再竞争夹具class。没有因此改生产逻辑或删断言。

生产改动只涉及一个循环，单机原生存储与直接Java回调/内存Level测试不等于Forge服务器或真实磁盘读档，未扩称修复服务器整体崩溃。协议仍13，资源哈希不变。按总授权继续B，不受旧单项交接“本项后停止”历史范围限制。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.63/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。341 Java / 342资源 / 569 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.63/modular-machinery-reborn-0.121.63.jar`，**1,652,694字节**。build/libs=reobfJar/output.jar=新归档SHA：

`435D8B4088998B22B894E559EE6B93B0AAB2219B23F81EF89E0AEEA7A4DBB83D`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.63/README.md) · [版本记录](../../版本记录/更新记录-0.121.63.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 63 final <类名> 0 <模式>`；共同回归`regress 63 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
