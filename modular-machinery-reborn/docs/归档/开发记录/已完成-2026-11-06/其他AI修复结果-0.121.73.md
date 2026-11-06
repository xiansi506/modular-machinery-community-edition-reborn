> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.73

**待Codex验收。本批编号：P03-14；99/5/18验收账本不变，主任务保持暂停。** 按用户最新要求，J交付后立即暂停全部修复，不进入K或其他余项；不宣称全部完成。

## J / P03-14：整次reload失败保留上一完整周期

本批指定离线范围完成，待Codex验收。机器定义、用于同步的源JSON、旋转缓存与同周期升级类型/物品映射/revision先构建候选快照，仅在真实SimpleReloadInstance的整个owning future成功后，于apply executor发布；任一listener失败丢弃候选。普通运行与登录snapshot继续读取上一已发布周期。适配器加载显式读取本周期候选机器；原公开generateAll入口仍保持live读取语义。失败不把候选RecipeManager移植到旧server manager。

### 生命周期与限定改动

- 7份生产Java及2份资源的直接依赖范围见scope.tsv/resource-scope.json。Forge AddReloadListenerEvent不暴露owning future，追加末listener不能覆盖其后的失败，因此增加局部constructor hook，保留原world-hiding注册项、协议13及网络结构。
- 实际生产JS经Forge过滤的Nashorn执行，分别变换官方mapped/SRG原版SimpleReloadInstance并校验操作数/结构。运行测试加载实际变换后的原版reload，模组生产类始终优先；没有手工commit/restore冒充协调器验证。
- listener任务携带所属session，ThreadLocal在finally恢复；失败后迟到任务拒绝stage，不退回全局即时发布。完成后移除owner/RM记录。直接Java/反射apply没有native session时保持原即时发布兼容语义。
- 初版hook曾在JVM运行时触发`VerifyError: Bad local variable type`：constructor末尾原栈帧已将参数locals标为TOP。最终在begin按owner捕获RM/executor，末尾只读取this；初次构建与失败attempt均保留，第二次正式clean build及真实JVM运行通过。

### 改前失败与最终验证

从71逐字复制的ReloadCycleCheck正确断言未改。在72完整生产类改前真实退出1，观察`oldServer=false newServer=true oldClient=true`；最终使用真实hooked native reload退出0，观察`oldServer=true newServer=false oldClient=true`。

ReloadConsistencyCheck各独立模式最终全部退出0：

| 模式 | 断言 | 覆盖 |
| --- | ---: | --- |
| matrix | 138 | 普通/profiled reload的前置apply、prepare、同步reload调用、后置apply失败；成功重试；定义/源JSON/旋转身份、升级revision、旧manager/index、客户端视图；旧已付加工失败后继续并完成一次，无重复起始付款 |
| body | 10 | 实际MachineLoader聚合源JSON超过2MiB的fatal失败不发布 |
| late | 14 | 已失败周期的迟到捕获任务拒绝发布，session清理 |
| async | 43 | 排队prepare/apply executor，pending期间旧表可见，成功/失败完成边界 |
| changes | 20 | 变量内容更改、改名、删除，失败保持旧周期及成功重试，实际block与客户端去陈旧项 |
| forge | 14 | AddReloadListenerEvent真实WrappedStateAwareListener执行整条加载链，后置失败保留旧周期 |
| threaded | 21 | 双prepare线程与单apply线程真实执行池，成功/失败发布及清理 |

真实MachineLoader、UpgradeLoader、RecipeAdapterLoader在native reload内执行；候选机器尚不在live表时适配器真实生成成功。共同30次回归改前/最终均通过，最终承接V/W/X/Y/A及多核心套件全部通过。旧失败日志不删除、不以总runner退出0替代专项结果。

### 如实保留的未验范围与暂停

未启动完整Forge/FML、KubeJS/Rhino、实际datapack解析全链、真实服务器登录/网络或游戏。Forge wrapper测试仅由fixture提供ModLoader.INSTANCE/loadingStateValid=true；候选RecipeManager由测试listener用原生replaceRecipes填入模拟首轮配方输入，不宣称完整FML启动。提交边界是SimpleReloadInstance owning future，不承诺其外层任意操作、其他mod/KubeJS全局副作用回滚或任意线程多表数据库原子性。正常server串行apply边界以外的并发/外部能力契约仍按既有未决记录处理。

J交付后按用户最新要求立即暂停，不进入K或其他余项。G3计费/可表示格点与H/I未知外部能力契约仍未决；部分改善不改称完成。所有验收账本、旧归档及既有未提交工作保留。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.73/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。343 Java / 343资源 / 579 class；相对本批起点只修改scope.tsv允许文件，资源仅按resource-scope.json修改coremods.json并新增reload_cycle.js，其余资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.73/modular-machinery-reborn-0.121.73.jar`，**1,677,889字节**。build/libs=reobfJar/output.jar=新归档SHA：

`F29638ACB2BEB37BA149155BFA25B92EC68EB785528481590D22483AB925A460`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.73/README.md) · [版本记录](../../版本记录/更新记录-0.121.73.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 73 final <类名> 0 <模式>`；共同回归`regress 73 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
