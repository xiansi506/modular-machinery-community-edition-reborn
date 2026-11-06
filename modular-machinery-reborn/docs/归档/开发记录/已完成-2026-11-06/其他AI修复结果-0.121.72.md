> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.72

**待Codex验收。本批编号：P02-08, P07-14；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## L2/P07-14、L3/P02-08：独立只读范围交付，待Codex验收

实际顺序调整：G3计费/格点契约、H/I未知外部能力仍待实质决定；J整次reload在71真实SimpleReloadInstance已重新复现后置失败，尚未实施其高风险生命周期协调器。按总交接“阻塞不放弃独立项”要求，72先做不依赖这些状态机的L2/L3。**L1动态区块诊断、J/K/M/N/O/P均不因此称已修**，后续仍按总授权继续。

## L2 / P07-14

query-matrix.tsv列出MachineControllerAccess所有世界读取入口。getBlocksInPattern/findBlocks在实际Level查询前检查当前服务端控制器身份/未移除、own与全部目标已加载/高度有效；不可用用接口已有异常表达，不返回伪AIR/半份结构。遍历时再核对world/owner/目标，防predicate内卸载/替换导致后续读取。getBlockPosInPattern等纯元数据与cached blockstate/facing不强行做不必要区块查询；普通仓口已修IO/失效引用路径不动。

LoadedQueryCheck scripts8项使用**真正ServerLevel类型**的探针：记录查询及是否进入FULL create请求；未加载目标受控拒绝，forced/create0，不静默返回部分；加载正常石/金顺序与findBlocks谓词正确；负坐标/跨chunk、谓词回调后卸载、超高度、removed/切换世界无owner均拒绝。此探针没有运行真实区块提供器生成流程，不声称实测TPS/生成收益。

## L3 / P02-08

DiagnoseCommand.findController只枚举±8立方覆盖的现有loaded chunk BE集合，经getChunkNow取得，缺失不请求创建；不建立全局强引用索引。过滤removed/其他Level，按旧平方距离取最近，同距显式xyz顺序保持旧dx/dy/dz扫描顺序，不依赖Map顺序。球外但在立方角落(8,8,8)仍合法，不擅自改范围或增加准星fallback。其他详细诊断扫描不在本编号nearest-search根因，本批没有顺手重写。

LoadedQueryCheck scan205项：手工逆Map次序同距、removed、角落边界、未加载无控制器，100组随机近邻/负坐标/跨chunk集合与独立距离/xyz oracle比较；每次最多4个现有chunk probes、0逐位置BE查询/0FULL create，对照旧路径4913次BE查询。改前71的正确“非固定逐位置lookup”断言失败；最终72退出0。只记录可复现计数，不称已证明实际服务器TPS提升。

## 验证及剩余

普通控制器/工厂付款、Q1身份/创造暂停恢复等共同30调用及V/W/X/Y/A多核心全部保持，未涉及包布局或语言，协议13、资源不变。脚本世界只读失败不会偷偷加载来满足查询，不扩大到所有外部脚本自定义函数。服务器权限、Rhino和真实提供器待验。

J最新正确断言失败日志保留在`remaining-all-0.121.71/final-ReloadCycleCheck.log`（oldServer=false,newServer=true,oldClient=true，退出1），不是已解决。原未知能源写前异常同样仍有退出1证据。续做必须从实际72完整类/源码指纹接续，保留所有旧失败，不因为本批两个独立项完成而把总计划23项标全过。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.72/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 574 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.72/modular-machinery-reborn-0.121.72.jar`，**1,666,905字节**。build/libs=reobfJar/output.jar=新归档SHA：

`7E9F8239F1E758B1BCA45B65F241935BCA12D18953DACFD7D68B775CA0E0DFC5`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.72/README.md) · [版本记录](../../版本记录/更新记录-0.121.72.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 72 final <类名> 0 <模式>`；共同回归`regress 72 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
