> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.67

**待Codex验收。本批编号：P04-13；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## E / P04-13：已有适配器精确烘入完成指定范围，待Codex验收

起点66。DynamicMachineAdapter不再将long能源先转double；通过兼容新增EnergyRequirement.bakedEnergyPerTick复用EnergyMath原精确long+float聚合算式，保留适配器正数Math.round等价HALF_UP。运行期EnergyMath.modified/draw仍保持原FLOOR及正数最小/饱和语义，不把所有round改floor。空修饰保持原long，LongMAX正溢出沿既有精确能源契约饱和到MAX，不回绕。

两种已有适配器的duration/item/fluid在Math.round后、窄化为int前检查范围及finite；超int烘入受控拒绝整个生成轮，不能负数夹1导致MAX工期×2变1。各适配器已有低值floor保持（机器复用min1，熔炉数量min0）。复用唯一包内checked-int方法，不新增适配器/第三方类型。

AdapterPrecisionCheck precision16/overflow67通过：2^53前后/2^53+1/LongMAX空修饰精确保留；加1、乘2、乘0.5及负结果下界；3×0.5按原round得2，不误改成floor1。生成执行实际精确支付9007199254740993FE、余额归0。MAX工期×2、MAX物品×2、熔炉极大工期全部拒绝，真实RecipeAdapterLoader.apply保留完整旧manager与占位，不发生部分发布。合法熔炉120×0.5=60，实际60tick支付1200FE并只产1铁锭。生成ID跨数值修饰保持稳定。

改前66 precision在空修饰long丢1处失败，overflow在MAX×2未拒处失败；最终两种正确断言退出0。T14/NBT12及已有复制/执行状态/私有物品NBT隔离、归属去重/冲突发布前拒绝随共同30调用保持。前批V/W/X/Y/A多核心套件在67完整类保持。

仅四份scope生产文件；能源helper是数值烘入直接依赖，不改变常规付款账本、补偿因子或单批能力。旧版已有Math.round/窄化沿袭问题按原P04-e报告对照，不声称旧游戏已实测。真实生成/加载/执行、原生资源不等于完整Forge reload/Rhino/实际服务器验收；全局失败reload仍由J处理。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.67/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 570 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.67/modular-machinery-reborn-0.121.67.jar`，**1,660,539字节**。build/libs=reobfJar/output.jar=新归档SHA：

`3D8C0C77CB0888D31FFD94B7E066F6DD72292D1E5E1AFDDB757179FB58383E7B`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.67/README.md) · [版本记录](../../版本记录/更新记录-0.121.67.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 67 final <类名> 0 <模式>`；共同回归`regress 67 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
