> **2026-11-05独立验收63～82：19项指定离线范围通过，P05-01/02/07/09仍未收口；82新增零/固定魔法成本大并行误拒需返工。当前82/协议13，最近整版指定离线通过81，累计118完成/1部分/3待处理。** [验收](验收-63至82-2026-11-05.md) · [82回归返工](返工交接-82小数搜索误拒-2026-11-05.md)。仅验收未改业务代码/构建，未实机/部署/推送，主任务暂停；下方旧状态为历史。

# MMCER剩余总修复交付 — 0.121.82

**待Codex验收。本批编号：P05-07；99/5/18验收账本不变，主任务保持暂停。** 本批G3有限范围搜索仅部分改善，大范围格点/单份start预检问题仍未完成；继续按用户保持原价精度及H/I付款前安全拒绝方向实施，不自行销号。

## G3 / P05-07：非单调有限候选搜索（仅部分改善）

**本批仍partial，待Codex验收。没有实现大范围原生格点模型，不宣称G3完成。** 用户已明确选择保持原价与精度、H/I付款前安全拒绝，本批不涨价量化、不移除余额实际移动守卫；后续方向已获授权，不需重问同一选择。99/5/18账本不变。

### 实现与限定

包含非零WILL/EMBER小数族需求时，整配方shared start/tick/finish可行集合可能非单调，不能用共享二分。保留现有纯容量上界，在最终候选上界<=4096且上界×需求数<=65536时降序完整检查，第一项通过就是该完整有限域内真实最高可行数。恢复每需求原parallelism，不改实仓。没有从1线性扫描到MAX，没有只试最高/少数候选就声称最优。

超出上述支持工作范围返回0并明确parallelSearchIncomplete；这是“未完成搜索”，不是证明所有候选都失败。普通控制器显示新增全字符串诊断并拒绝新开工，不把0回退为1付款；工厂/核心的prepareCraft也拒绝新开始，不丢旧已付线程。工厂当前没有新增独立GUI原因展示，不能称该原因已显示在每个工厂UI。

金额、duration compensation、fixed flag、合法零成本、浮点movementMatches、整数魔法/FE补偿均不改。原单条parallelLimit只用来算容量上界，不承担实际共享精度可行性证明；最后降序完整shared阶段验证实际支付规则。

### 真实改前失败与最终证据

81完整生产类承接原69 GroupCostCheck fractional正确断言：余额1e8、单位2^-30，上限17，8份余额不动拒绝、16份实际付一个ULP，但共享搜索选0，退出1；最终同一原断言选择16并实际付款，退出0，3条原断言未改。

FractionalSearchCheck oracle：240个独立小规模穷举case、**722断言**，涵盖WILL/EMBER、输入/输出、两需求共享余额、fixed、duration0.5/1/2与临界精度，逐个从1..N参考检查IEEE实际余额差额及既有容差，得最高值；生产只读结果与其一致，选中后实际tick余额精确一致，没有提高名义成本。

budget3断言：Integer.MAX_VALUE上限有界拒绝不扫描MAX；同一对象降低至17后incomplete复位并找到16；4096×17需求超过加权工作限拒绝，余额不动。controllers6断言：实际预成型普通/工厂/核心都选择16、煤17→1、输出16、魔法付一个ULP；8192支持范围外无煤/魔法付款，不fallback1。普通诊断同步键明确；工厂拒绝但未扩GUI。

FractionalBoundCheck2断言：4096个全部不可支付的候选完整检查后为确定0而非incomplete，实仓不变；明确零成本在MAX仍可走原路径，不因魔法family笼统限4096。计时仅记录受控JVM方法，不作实机TPS收益承诺。G2整数魔法325条保持。新增/原G3明确断言共**736条**（3+722+3+6+2），另G2及共同/承接回归。

fixture初版重复注册已被基础装置注册的bloodmagic要求type而失败；另原FactoryRuntimeAuditCheck.recipe默认max-parallelism=1，不能拿它验证16，改用实际显式cap构造器并给大上限对照足够输出槽，保持真实付款/不付款期望。失败日志attempt与修装置脚本保留，不改生产规则凑测试。额外bound脚本第一次缺原Aura support依赖，补既有映射JAR后通过。shell错误转义未进入runner亦未当测试通过。

Java17实际clean build/reobf成功，test NO-SOURCE，最终完整597 class优先；共同30改前/最终及VWXYA多核心承接套件以最后日志为准，未删除正确断言/扩大失败白名单。346 Java/343资源/597 class，协议13，中英文各569键，只有两语言各加一条incomplete诊断。

### G3剩余工作（不是等待原计费决定）

1. 大范围精确移动格点及多个原生端口/共享需求交集尚未实现；4097及以上可行容量可能被保守拒绝，是兼容/性能支持限制，不是完整修复。不能把有限枚举写成“格点已证完备”。
2. 现有普通/工厂候选筛选先按parallel1准备start；如果离散小数成本在一次性start而非perTick，单份不可付但16份可付可能在进入最大搜索前就被排除。本批引擎证明为原反例perTick形状，不宣称所有小数start生命周期已修。下一步还需并行选择/催化剂/脚本准入的安全先后契约。
3. 用户接受不可靠能力付款前拒绝；H/I支持矩阵下一独立批先做，不以本批修正证明未知外部对象安全。可先处理H/I独立范围，再继续G3大范围/起始gate，不混改支付核心。见审计remaining-gaps.md。

控制器/factory装置是真实生产入口、原生库存/执行副本，魔法Store为受控通用double Access，不冒称真实BloodMagic/Embers网络、Forge/FML、Rhino、多人/权限/磁盘/GPU或游戏已验。没有部署/游戏/改实例存档/暂存/提交/推送或创建Release。结果待Codex验收，G3及H/I仍未完成，总计划不称全部完成。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.82/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 597 class；相对本批起点只修改scope.tsv允许文件，资源仅按resource-scope.json在中英文各新增一条小数并行搜索未完成诊断，其余资源哈希不变、中英各569键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.82/modular-machinery-reborn-0.121.82.jar`，**1,722,230字节**。build/libs=reobfJar/output.jar=新归档SHA：

`F2038837EC1728AA5CB63B9A2F27E1220997457D36E0EBCFF548A12E125E060B`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.82/README.md) · [版本记录](../../版本记录/更新记录-0.121.82.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 82 final <类名> 0 <模式>`；共同回归`regress 82 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
