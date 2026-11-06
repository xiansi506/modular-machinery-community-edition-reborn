> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.81

**待Codex验收。本批编号：P09-01；99/5/18验收账本不变，主任务保持暂停。** 用户已明确选择P继续完整规划、G3保持原价精度、H/I付款前安全拒绝；本批交付P的原生spot共享及有界迁移，其他余项继续串行，不宣称全部完成。

## P / P09-01续做：原生spot共享规划与有界迁移

指定原生39.4/离线范围交付，待Codex验收。用户本轮明确选择继续P完整规划，不采纳80的“重叠/迁移一律拒绝”作为收口；同时选择G3保持原价精度、H/I付款前安全拒绝。具体答复记录审计decisions-2026-11-05.md。80历史partial不改写成当时已完成，81提供新的完整支持范围证据，99/5/18验收账本不变。

### 原生语义与实现

39.4 InternalHooks.getAuraInArea以1000000为基值，累加真实半径1球内各AuraChunk spot；不存在“每个访问器各自一份独立可消费池”。本批共享coordinator保存每个实际BlockPos spot的initial/working整数，任何view.amount都从该view原native amount加所有球内共享spot增量求出。同位置/不同标签共享；相邻/垂直/跨chunk视图看到同一spot变化。distance2中心互不在对方radius1内时，中心付款只影响原生会包含它的采样球；若共同midpoint付款，两个采样都实际下降。不创造区块统一余额、不改名义收费。

原生spot±count超int范围时，是整次count继续到above，而非填满当前再分剩余。纯规划逐位置查真实初值＋候选增量，解析第一个不溢出目标；提交直接调用该最终位置的原native store/drain(false,false)，与原API递归最终写入等价，同时避免执行深层递归。目标可在原采样球之外：以**实际目标spot的精确整数差额**和采样预测值为收据，不再强制错误的“采样一定减delta”。没有ULP涨价/量化，也不把资源没动当付款。

每次目标查找最多4096次；每事务工作预算65536，包含spot读取/投影访问和操作登记。预算不足/高度无法安全上移/原生area整数总和不能安全表达时受控拒绝并使事务不可提交，不先动世界再restore做规划。不是无限规模/任意迁移深度保证；这是有限工作量支持边界，不是80按重叠关系一刀切拒绝。chunk snapshot键统一canonical y=0，防同chunk不同y重复登记独立快照。

保持80位置身份、全部已加载邻chunk验证、每步before/postimage、统一回滚及不覆盖外部更新的guard。自身付款后发生外部改动/卸载或未知native异常仍可能报unresolved并保留残余，不能称跨线程通用原子性。用户已选择H/I付款前拒绝未知能力，下一批按该决定完善通用支持矩阵；P不把这个未实现工作冒称也完成。

### 改前与最终验证

80完整类：新增AuraSpatialCheck overlap正确期望因第二视图未共享候选/受控拒绝真实退出1，migration正确期望因旧迁移拒绝退出1。最终同样期望全部退出0。没有修改80历史“有限支持拒绝”的旧日志或把其反向期望作为81回归；81新规划以原API独立对照为准。

- overlap8/migration2条：共享空间余额及exact spot付款、原int-min→above1迁移。
- **AuraSpatialOracleCheck random：120组×8步，4296断言**。为每例创建两个独立原生AuraChunk/InternalHooks/LevelData世界；生产在worldA纯规划，worldB按同一步次实际执行原API原位置调用（不使用生产ledger计算reference）。每步所有采样/返回收费及最终完整NBT分布相同；含distance1/2、共同位置/垂直/midpoint、跨chunk、正负输入输出、late false恢复。
- **native迁移oracle：8组、56断言**。正负饱和spot深度1～4，reference调用原API递归，生产纯规划后提交，两者完整原生NBT及sample amount逐一相等；包括迁移离开原radius时native sample不变但目标spot确有付款。
- spatial controllers6条：共享余额50的相邻两视图不能付款30+25，煤/Aura均不变；20+25精确支付并余5，真实预成型普通/工厂tick产出一次。
- budget4条：4096饱和链/65536投影工作耗尽，拒绝commit且不改原生分布，不能执行半条递归。
- overlap cross-chunk rollback6条：两写chunk统一恢复；late外部+5导致unresolved，保留+5与自身两笔残余而非覆盖/盲退款。

新增空间专项合计**4378条明确断言**。80已修的实际selector原反例、独立位置/邻chunk/threshold/input-output、失效/卸载/晚期外部变化、同位置别名、故障原异常等保持承接，80的313条中旧“overlap拒绝/迁移拒绝”期望由上述新正确支持测试替代，不再把过时策略期望当测试失败豁免。原生统计以本批实际final日志为准。

Java17正式clean build/reobf成功、test NO-SOURCE，最终完整597 class优先。共同30改前/最终及VWXYA多核心套件承接；optional剔除Aura/BloodMagic后common规划/controller初始化不依赖可选API。final-tests.py包含所有新增空间专项以及仍适用的旧正确断言，每JVM独立cwd/编译目录，原命令/真实退出码/类来源保留；改前正确失败/原类指纹/源码资源快照不覆盖。

### 范围与未验

346 Java/343资源/597 class，协议13，语言568键/资源不变；只改AuraBackend（含嵌套spot helper），未改资源计费/新增魔法/网络布局。生产规划没有临时写原生world；只在独立测试reference世界顺序实际调用API取oracle，这与“生产先改仓再恢复做模拟”不同。

真实原生39.4方法与NBT已执行，世界/LevelChunk仍是内存能力壳；控制器/工厂为已有预成型host装置、Aura环境为分离原生world。完整FML、真实ticket/chunk provider/磁盘、环境效果tick、在线网络/多人/KubeJS/Rhino/GPU和游戏未启动；不把reference测试等同实机。全chunk粗粒度外部更新冲突guard不承诺最细粒度并发支持，直接无postimage证明的restore仍拒绝，不隐藏残余。

未部署、启动游戏、改实例存档、暂存、提交、推送或创建Release。P是否通过登记验收由Codex确认，不自行改99/5/18账本。下一G3/P05-07按用户原价/精度规则实施，再H/I付款前安全拒绝；这三个决定已确认，不再当作未获授权，也不宣称总计划全部完成。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.81/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 597 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各568键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.81/modular-machinery-reborn-0.121.81.jar`，**1,721,486字节**。build/libs=reobfJar/output.jar=新归档SHA：

`D7E9E7840065787D69656A921E6BA0E5A5D559D5F49008EB80ADF92CD45C8F94`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.81/README.md) · [版本记录](../../版本记录/更新记录-0.121.81.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 81 final <类名> 0 <模式>`；共同回归`regress 81 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
