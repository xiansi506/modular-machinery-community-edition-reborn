> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.78

**待Codex验收。本批编号：P08-02；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## N / P08-02：真实消费拥有者结束后的制作锁释放

指定离线范围交付，待Codex验收；不将旧报告的红石行为或静态风险直接当成当前事实。99/5/18验收账本不变。

### 当前反例与契约

真实77完整生产类：实际锁模式ProviderState.accept→真实控制器start已消耗供应器1煤→结构失型真正丢弃批次→供应器serverTick，仍观察`locked=true busy=true diamond=2`，正确断言退出1。最终相同过程观察`locked=false busy=false diamond=2`，退出0；没有产物、没有退款/回网或丢弃未用钻石。

红石当前已经是暂停，76动态必需区块不可用也保留已付状态；它们不是中止，不能解锁。当前源码没有发现旧分析假设的公开scriptCancelCraft方法，因此没有发明一个方法来假装脚本取消已测；破坏性取消用真实cancelIfPerTickFails路径验证。事件脚本桥采用直接Java handlers，未声称Rhino运行。

### 实现

- 一个AE2无关的PaidInputScope包住真实controller服务端tick，ProviderBuffer live take/事务commit记录前后净减少；只有tick正常返回且live资源确有减少才登记实际消费控制器。影子规划、预检、完整回滚、异常退出不发表确定消费收据，ThreadLocal在finally恢复。并非把所有结构接触者/附近控制器都算锁持有者。
- 每次锁模式成功派发有独立lock-cycle UUID；实际消费收据绑定控制器持久incarnation UUID及位置，供应器原生NBT保存该cycle与拥有者列表。旧scope捕获的cycle不能登记进后来新派发的锁。
- 供应器serverTick只查询已加载拥有者位置。未加载不等于失去拥有者，不强载；已加载处确实没有同incarnation控制器、实体被移除/替换或控制器全部已付工作结束，才移除此owner。所有已观察到的消费owner均结束后才能释放。正常Finish仅作为reconcile提示，不是按结构成员身份无条件解锁；支付scope内延后reconcile，防新收据未发表时提前释放。
- **同一控制器仍有任何已付普通/工厂/核心工作时保守保锁**。这可能因同controller无关批次而延后释放，不能称每需求最早解锁；但不会一个核心中止就解开另一核心/控制器仍使用的锁。多个控制器各有独立收据。无须改每个abort分支或伪发RecipeFinishEvent。
- 手动模式切换/全量退回仍是既有明确策略覆盖，原缓冲/返回余量保留；新cycle清旧追踪。物理掉落不携带旧控制器引用。没有复活O不可达直推或更改M隔离缓冲身份、AE2 CPU/能源网络协议。

### 正式验证

Java17离线clean build/reobf退出0，test NO-SOURCE，最终592完整生产类优先。N专项明确断言**141条**，全部退出0：

| 专项 | 断言 | 覆盖 |
| --- | ---: | --- |
| NCheck structure | 5 | 原77实际失型卡锁反例及未用材料/输出核算 |
| NBoundaryCheck lifecycle | 60 | 普通/工厂/核心×实时/保存恢复×实际失型/cancelIfPerTickFails；真实消费收据、末owner结束释放、余料不变 |
| pauses | 24 | 红石/动态区块暂停40tick及存读档继续保锁，真正恢复完成才解锁，一次产出/付款 |
| shared | 12 | 两个真实消费控制器共享锁，一个中止不释放另一已付批次；保存恢复与最后拥有者中止 |
| missing | 5 | 未加载拥有者保锁/无unloaded查询；已加载真正移除/同位置新controller UUID不同后释放 |
| NReceiptCheck shadows | 5 | 未提交影子、真实事务后置false回滚净余额不变无收据；异常清理scope，不臆断拥有者 |
| cycles | 6 | 手动模式、全量退回精确余料及无重复返回、前cycle取消不能释放新派发 |
| legacy | 4 | 缺失/损坏来源证明保持锁，物理掉落移除旧消费者引用 |
| core | 3 | 同一工厂两个真实已付核心，一个破坏性失败、另一个继续，末核心完成一次后释放 |
| fluid | 3 | 实际AEFluidKey水1500→1000已付→中止释放，500余量保留 |
| events | 9 | Java ScriptHooks正向校准实际start；实际中止finish=0，正常完成finish=1，三种引擎 |
| reentrant | 2 | scope内reconcile延后、旧支付回调不越新lock cycle登记 |
| waiting | 3 | 未消费待加工锁、结构接触/失败预检、真实finish-handler提示与无关失型均不误解锁 |

另完成剔除AE2/Mekanism/AppMek后的common controller/PaidInputScope实际初始化及收据/清理契约；M重映射普通/工厂/核心及保存恢复96条承接通过。共同30调用改前/最终与VWXYA多核心承接套件全部通过。原命令、cwd、真实退出码、完整class加载来源、源码/资源指纹保留。

fixture第一次新增Host列表写成ArrayList<MachineRecipe>不满足原生replaceRecipes的Iterable<Recipe<?>>，javac失败attempt保留；改正装置泛型后编译通过。另一次错误Python绝对路径未进入runner，不算生产测试或隐藏为通过。最终生产仅一次正式clean build，无以装置修正更改正确行为期望。

### 兼容边界与未验

**旧锁存档缺失/损坏消费证明、掉落重放、未曾消费的派发锁，不因“当前没机器”自动释放。** 保持锁并可沿既有手动模式切换/全量退回处置；没有自动改实际存档、退款或猜历史拥有者。异常tick不发表不确定收据，可能需明确人工处置。详见审计legacy-lock-boundary.md。这是保守安全策略，不承诺所有历史锁最早自动解开。

观察只适用于实际ProviderBuffer自有状态及正常串行server tick，非任意外部能力、跨线程或脚本任意副作用的原子性承诺；H/I契约仍未决。物品/流体原生AEKey与实际Provider/Collector/控制器/事务执行，节点只构造未create/接网，缓冲通过state.accept注入，不等于真实pushPattern/AE CPU/网络能耗运行。ServerLevel是内存壳，完整Forge总线、真实区块票据/磁盘/多人/Rhino/GPU及游戏尚未运行；finish-handler直接调用与Java桥校准不冒称完整总线验收。没有实机TPS收益承诺。

346 Java /343资源/592 class，资源不变，中英文各568键，协议13。未部署、启动游戏、修改实例/存档、暂存、提交、推送或创建Release。后续O/P08-03维护项，G3/H/I等未决不称完成，全部结果待Codex验收。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.78/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 592 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各568键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.78/modular-machinery-reborn-0.121.78.jar`，**1,708,772字节**。build/libs=reobfJar/output.jar=新归档SHA：

`71442E13C2AF44BA30176DE8B2D9971ABCFD820A79C685944FA7E0FF2ED375A1`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.78/README.md) · [版本记录](../../版本记录/更新记录-0.121.78.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 78 final <类名> 0 <模式>`；共同回归`regress 78 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
