> **2026-11-05 Codex验收：本版P09-01部分改善，后续81完成所列支持范围；保留本版历史边界。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.80

**待Codex验收。本批编号：P09-01；99/5/18验收账本不变，主任务保持暂停。** 本批P仅部分交付，重叠视图/极值迁移支持契约仍待决定；连同G3/H/I余项交回，不自动prepare81或称全部完成。

## P / P09-01：位置身份、已加载足迹与共享回滚（部分交付）

**仅部分交付，待Codex验收。P09-01未完成，不销号。** 重叠不同位置视图与原生极值spot迁移采用受控拒绝，仍需支持契约/兼容决定；同G3/H/I余项交回，不自动以新patch清零。99/5/18验收账本不变。

### 已实现支持范围

- Aura视图身份含实际Level、BlockPos与radius1。同位置多标签共享候选余额；不同位置不再被同chunk第一来源替代。距离>2的独立视图可分别从各自native spot付款，输入/输出与阈值保持原需求契约。
- 半径1包围区域涉及的x/z区块必须全部已加载，然后才调用optional API，包含邻chunk；access/variants/valid/amount/snapshot/move不请求未知chunk。快照验证对象身份、type和完整NBT，所以邻spot+5不会再被本区块空快照漏过。
- MagicPort.Access增加默认prepare/plan hook；非Aura仍用原generic Planned。同事务/Level的Aura coordinator先建立共享footprint，再在各位置建立Planned；避免一个chunk被多个独立快照反复覆盖。
- 真实commit前检查全部原snapshot/valid，逐步检查本事务expected image再调用native move；每个正常返回操作记录实际postimage。late false且无外部改动时统一恢复所有自身写过的chunk，未写依赖不恢复。外部变化/卸载/对象替换后不覆盖外部新值，报rollback unresolved保留残余；native异常没有可信postimage时保留原异常并附未决恢复异常，不凭猜测退款。
- 不同位置半径1球可能重叠（中心距离<=2）的第二次非零reserve会poison整事务，不能部分扣第一视图；同位置别名仍共享余额。原生当前spot±count会越signed-int并递归above时写前拒绝，避免超出已验证足迹。不为这些场景暗改计费/静默吞单位。

### 未完成/兼容损失（必须保留）

1. **不同位置重叠Aura视图暂不支持同次结算**，不称已经实现spot级共享规划。是否接受此限制作为正式支持契约，或继续明确重叠基值/阈值计费并实现原生spot共享模型，需要实质决定。
2. 当前spot溢出后的上方迁移暂拒绝，未证明任意高度/链长的有界写足迹。全chunk快照会保守拒绝同chunk无关spot更新，不能称最细粒度并发支持。
3. 自身已付款后外部更新/卸载可能留下已付残余，回滚会明确抛未决而不覆盖外部新值。**不是通用原子性保证**，仍与H/I未决一致。直接Access.restore不再允许无postimage证明的盲whole-chunk恢复，明确要求走受控事务coordinator；这是安全支持边界，不是承诺旧任意直接restore调用兼容。

决定依据及两个方向见审计supported-contract-decision.md；id-statuses.json显式partial，执行方不能自行认定登记项完成。

### 真实改前失败

使用79完整生产类，AuraReproCheck实际MachineRecipe+selector a/b：原生A=1000000、B=50，B需求100却`canStart=true start=true Aspot=-110 Bspot=-999950`，正确断言退出1；最终同一断言`canStart=false start=false Aspot=0 Bspot=-999950`、退出0。不是只改identity字符串或模拟余额。

AuraCheck原位置身份断言改前失败，邻chunk spot+5仍unchanged的正确断言改前失败；最终均通过真实39.4 AuraChunk/InternalHooks/LevelData。没有用不同版本源码压缩包取代二进制证据。

### 正式验证与失败工件

Java17正式clean build/reobf成功，test NO-SOURCE；第一次完整596类发现新的planning hook在HashMap.computeIfAbsent内嵌套写同map引发ConcurrentModificationException，旧失败attempt/构建完整类保留build1。修为先prepare共享coordinator、再原per-view shared构造；第二次正式clean build/reobf成功，最终596类用于全部最终测试。

已实现支持范围明确断言**313条**（不是整个P全部通过）：

| 专项 | 最终断言 | 覆盖 |
| --- | ---: | --- |
| AuraReproCheck | 1 | 原生a/b selector原错扣实际配方反例 |
| AuraCheck original/neighbor | 7＋2 | 不同位置各付10/25、低余额拒绝、邻spot变更与unknown邻chunk拒绝 |
| AuraBoundaryCheck overlap | 17 | 同位置别名、距离1/2冲突整次拒绝、距离3独立付款 |
| rollback | 6 | 多视图同chunk/跨chunk late false，实际分布完整恢复 |
| updates | 5 | 提交前外部更改零付款拒绝；已写后外部+5不覆盖、保留自身-10并报未决 |
| invalid | 12 | 邻chunk卸载/替换、真实hatch移除、type变更，提交前不付，unknown读计数0 |
| output | 4 | 输入最小保留/输出上限拒绝，实际start只付输入、finish再写指定输出位置 |
| AuraExtremeCheck extrema/aliases/unload | 2＋3＋3 | int极值原生above迁移拒绝、同位置30不能付40/精确付25、后置卸载不盲恢复/强载 |
| AuraOracleCheck | 240 | 80组独立整数余额/需求oracle，真实原生两个spot精确付款与late false分布回滚 |
| AuraControllerCheck | 8 | 实际预成型普通/工厂tick，原生Aura环境不足不扣煤/灵气；足够分别精确支付一次并输出一次 |
| AuraFailureCheck | 3 | 原生AuraChunk子类仅注入postwrite故障，保留原异常/附加unresolved/真实残余状态 |

另剔除Nature's Aura/BloodMagic后的common MagicPort/事务/controller实际初始化通过；共同30改前/最终与VWXYA多核心承接全部通过，generic WILL/LP等正确断言保持。最终支持范围复跑脚本final-tests.py，所有原命令/独立cwd/退出码/类加载来源与失败attempt保留，不扩白名单遮盖失败。

fixture起初缺原生magic测试依赖，已从现有环境审计classpath仅补Nature's Aura39.4/BloodMagic3.3.8-50映射JAR；第一次路径过滤漏掉名称中连字符，保留编译失败，修装置后才取得真实改前失败。hatch用Unsafe壳时需补backend/itemCapability使真实setRemoved可执行；输出测试初版误把finish-only输出当start付款，修为真实start/finish分阶段正确期望；控制器执行副本需真实naturesaura MagicModule注册。均未放宽资源断言/修改native Aura数学。

### 原生与实机边界

真实AuraChunk/InternalHooks/LevelData和原生NBT、实际MagicRequirement/MachineRecipe/selector/事务执行；ServerLevel/LevelChunk为内存能力壳，API INSTANCE仅测试JVM指向原生InternalHooks。普通/工厂为已有预成型host装置，Aura环境与host世界分开，证明结算入口而非物理仓口绑定/完整控制器结构加载。故障子类明确标注，不冒称真实后端自然会抛此异常。

未运行完整Forge/KubeJS/Rhino、真实chunk provider/票据/生成/磁盘、在线网络/环境效果tick、多人/GPU或游戏。没有部署、启动游戏、修改实例存档、暂存、提交、推送或创建Release。346 Java/343资源/596 class、中英文568键、协议13，资源不变。共18个编号已交付指定离线范围待验、4个仅部分改善（含P）、1个G3待实质决定，**总计划仍未全部完成**。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.80/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 596 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各568键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.80/modular-machinery-reborn-0.121.80.jar`，**1,719,207字节**。build/libs=reobfJar/output.jar=新归档SHA：

`86E3B257ADD8861F518018F59B95C83891B95AC6A6057E3B9756EA1CAAD3FFE7`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.80/README.md) · [版本记录](../../版本记录/更新记录-0.121.80.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 80 final <类名> 0 <模式>`；共同回归`regress 80 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。


[剩余5编号的实质决策清单](剩余修复-实质决策清单-2026-11-05.md)
