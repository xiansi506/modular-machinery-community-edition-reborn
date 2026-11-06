> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.77

**待Codex验收。本批编号：P08-04；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## M / P08-04：隔离缓冲逻辑身份与本地组号分离

本批指定离线范围交付，待Codex验收；99/5/18验收账本不变。旧存档缺失历史来源的兼容限制单列，不冒称历史在途状态全自动修复。

### 实现

- 隔离缓冲身份由供应器持久UUID、维度、位置和slot组成，镜像/直接入口复用同一来源。供应器旧groups[]只保留legacy偏好，append不再将控制器本地分配回写共享状态。普通组号仍允许Integer.MAX_VALUE，不用限制普通仓口绕过反例。
- 新增AE2无关InputGroupBinding契约；HatchCollection携带逻辑key↔本地数字映射，保留到group/tag/transaction视图。普通控制器和工厂/核心在真实start冻结实际来源，tick/恢复按key解析当前本地地址。活跃保存的旧数字可不变，但不会据此消费新占用该号的P/Q/另一个slot。
- 来源丢失、镜像重绑到Q、供应器同位置新实体时保留已付工作并暂停，不重付煤、不偷扣其他缓冲、不假finish。实际保存/加载同一UUID来源回来可以继续；正常完成后的restart按该来源当前本地地址重新准备新批次。manual旧数字也不会转成占用同号的isolated来源。
- 完整save保留incarnation；物理掉落的contents移除incarnation，重新放置是新来源。将整个原存档NBT显式恢复到原位置等同恢复该incarnation，不承诺防恶意存档回放。复制同UUID到不同位置仍因位置不同而不合并。
- 分配使用long有界探针；失败返回-1时不把isolated缓冲接成公共输入；组偏好数组save/load均防共享别名。没有更改AE网络CPU、配方后端、制作锁中止或死直推路径。

### 改前失败与修正过程

76完整生产类的MCheck两条正确断言真实退出1：A已付煤停在2/4，B普通组65536冲突把共享P偏好改65537；B先收集Q的第二场景改为65572。两例钻石仍1、输出0；最终必须完成原已付批次并精确扣原钻石，不以“缺陷存在即PASS”验收。

首次正式Gradle构建任务前等待600秒后超时，未得到Gradle成功/退出码；日志、daemon记录和Timeout说明保留，核对无残留java后重试，未清锁/缓存或杀未知进程。之后正式Java17 clean build/reobf成功。第一次完整构建类测试还暴露factory恢复初始空映射被三元条件表达式自动拆箱null而NPE，修为显式nullable分支；第二次正式类的BindingCheck另揭示Minecraft接受空ResourceLocation路径，坏tag丢失dimension没有被明确拒绝，补齐NBT类型/非空检查。最终第三次正式clean build/reobf成功，完整590 class，前两次完整快照保留build1/build2；所有最终专项/回归重新使用最后正式类，不把旧build回归冒称最终。

离线装置第一次保存加载缺AEKeyTypes registry，真实AEKey.fromTagGeneric报未初始化；补入真实Forge AE item/fluid key registry（沿既有装置方式）后继续测试，没有替换生产反序列化、AEKey或ProviderState。旧失败attempt全部保留。

并行装置最初用tiny输出仓的单槽测试两种异物产出，第二种被真实空间门禁阻塞；补为实际原生handler两槽后保持“各一次产出/各一次付款”期望通过。不是放宽生产输出检查或吞掉失败。

### 验证证据

MCheck：两原反例，实际两个绑定控制器、供应器/镜像构造器、真实AE ManagedGridNode与KeyCounter/AEItemKey、真实collector/依赖通知/start/tick。

MMatrixCheck：普通/工厂/核心×manual冲突/第二供应器×实时/存读档重新编号；绑定key不改，实际新本地号可从65536变65537/65572，原钻石付一次、其他来源9钻石保持；重绑/同位置替换/卸载均暂停，保存原来源重新加载恢复；完成后restart精确；并行两槽分别付款/产出；新manual MAX_VALUE与低组legacy恢复、歧义高组legacy保留暂停。

MContractCheck：直接P＋两个镜像同组不重复算量，10煤拒11/付6余4；selector和transaction视图身份保持；两个位置复制同UUID不会误合并、保存恢复/新实体/掉落边界；MAX_VALUE和受控分配耗尽不溢出到通用组、不暴露缓冲；偏好数组无别名。

另以真实AEFluidKey水1500→1000事务付款验证collector/tag/逻辑重映射及镜像不重复计量，最终余500、第二次1000拒绝。

BindingCheck：不存在的映射nullable、manual不切isolated、坏tag/缺字段fail closed、保存恢复、legacy判定。MOptionalCheck：去掉AE2/Mekanism/AppMek依赖后common controller/factory/HatchCollection/InputGroupBinding类实际初始化及binding契约通过，不靠改生产optional门禁。

复跑完整M专项：`python.exe -I C:/mmwork/_audit/remaining-all-0.121.77/final-tests.py`；共同30调用及VWXYA多核心承接按最终日志核对。所有专项独立cwd/class目录，最终生产完整类优先，不加载旧production/before/patched类替身。原始命令、实际退出码、类加载来源和失败attempt保留。

最终M专项数字：原反例4+4；remap96、source-loss45、restart12、legacy/manual28、parallel10；aliases9、ownership6、allocation-limits6、fluid4；BindingCheck12，**合计236条明确断言**，另有optional依赖剔除/实际初始化契约检查。L1已付动态卸载/存读档42条承接也重新通过。正式资源快照/原源码资源（mods.toml按Gradle正常展开版本）与JAR内资源逐字校验，build/libs=reobf一致。

### 旧存档限制与未验

77以前只保存高数字组，既可能是合法manual，也可能是旧isolated本地号；共享偏好又可能已被B改写，不能唯一重建历史来源。**无binding的legacy >=65536默认保留已付状态并暂停，不猜源、不退款、不自动迁移实际存档。** <=65535可证明不在旧隔离自动分配范围，保留manual恢复；新格式manual仍完整支持高组号。详见审计legacy-decision.md。普通控制器明确全字符串诊断，工厂恢复歧义标记时记录警告，线程仍保留但不结算。

真实AE2节点仅构造，未create/联网；装置缓冲经state.accept注入，不是实际pushPattern/CPU/网络能量。世界是ServerLevel内存壳，卸载方法/依赖通知实际调用，不等于完整Forge总线/区块票据/磁盘/多人/KubeJS/游戏验证。没有启动游戏/部署/改实例存档/暂存/提交/推送。任意外部能力的并发/可逆性仍是H/I未决契约，不能因本项绑定修复而销号。

345 Java/343资源/590 class，协议13；中英文各568键，资源仅两语言文件各一条来源诊断。待Codex验收，不宣称全23项完成；后续N/P08-02，G3/H/I仍待决定。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.77/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。345 Java / 343资源 / 590 class；相对本批起点只修改scope.tsv允许文件，资源仅按resource-scope.json在中英文各新增一条输入来源不可用/旧组不明确的诊断，其余资源哈希不变、中英各568键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.77/modular-machinery-reborn-0.121.77.jar`，**1,702,929字节**。build/libs=reobfJar/output.jar=新归档SHA：

`3A3FA3EBD5B15707C731CEB27FE168A25D91DDA657AEBC5D74D853F6F45A86F5`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.77/README.md) · [版本记录](../../版本记录/更新记录-0.121.77.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 77 final <类名> 0 <模式>`；共同回归`regress 77 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
