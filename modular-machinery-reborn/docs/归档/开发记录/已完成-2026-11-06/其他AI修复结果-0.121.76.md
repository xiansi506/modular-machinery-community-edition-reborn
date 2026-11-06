> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.76

**待Codex验收。本批编号：P07-10；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## L1 / P07-10：动态区块诊断与已采用结构的卸载暂停

指定离线范围交付，**待Codex验收**。本批仅L1；72已交付的L2/P07-14、L3/P02-08不重计。99/5/18验收账本不变。

### 实现与策略

- MachinePattern.inspect确认真实required位置已加载，再进入原匹配器，未加载位置不读state/BE。DynamicPattern使用同一份measure算术服务原matchesSize谓词入口和真实世界Inspection，保留min/max、零段、重叠尾和回退规则，不复制第二套诊断长度算法。
- 匹配成功的合法已知prefix，其后的额外unknown仍是可选探针；匹配失败所需重复段/尾段未知才携带CHUNK_UNLOADED。控制器读取MachineRegistry同次实际尝试的Inspection，不在失败清零朝向之后重新盲扫未旋转/最大可能结构。
- findMatch公开签名与选择顺序保留。bound失败以bound诊断；无绑定但有明确蓝图可诊断该蓝图；无任何明确目标的全库查找保持原“没有单一候选”的MISSING_STRUCTURE边界。明确requires-blueprint门禁本身不当成缺chunk。
- **已经形成并采用的动态完整footprint不可因卸载重新量短继续。** tick推进/脏标刷新前检查已采用位置；不可用时清可执行hatches、parallel controllers与upgrade bus引用，报告CHUNK_UNLOADED，保留逻辑formed拥有关系、节数/足迹、已付执行状态和进度。不abort、不退款、不假finish。refreshInputPorts在暂停中不能重新发布半套端口。
- 必需区块恢复后强制重检、重采集新实体，不依赖delayed开关的事件捷径。朝向、蓝图意图或定义对象更换也强制重检，旧unloaded足迹不会把新目标永远锁住。仅动态机器采用此暂停策略，不顺带重写静态卸载/外部能力事务政策。
- 存读档增加兼容性的compact structureSelection tag：朝向、蓝图和解析源SHA-256，不把整份JSON塞入每台BE；同定义恢复暂停，已换定义可重试新目标。旧75及更早无此tag时保守采用已有formedParts及加载时声明，不能重建其历史定义修订；Java直接构造无解析源的定义没有跨存档源修订指纹，运行期仍以对象身份检查。这些边界不隐瞒。

### 真实改前失败

75完整生产类：L1Check diagnosis正确断言退出1，必需动态第二节未知却观察`MISSING_STRUCTURE`；paid正确断言退出1，已采用两节卸载远段后观察`CRAFTING size=1`。最终同一正确行为断言分别得到`CHUNK_UNLOADED`、`CHUNK_UNLOADED size=2`。没有以手工恢复/直接改端口表冒充实际控制器状态机。

初次编译旧RegionLifecycleAuditCheck因其未调用的历史静态审计方法依赖未承接的StaticPatternAuditCheck而失败；attempt保留。最终support只保留原ServerLevel/world/controller装置，不运行旧“缺陷存在即PASS”的反向断言。生产正确断言在此后真实改前JVM中失败，再由正式构建类验证通过。

### 正式验证

最终Java17 clean build/reobf完整生产588 class优先加载，未拿旧patched/before/build目录替换：

| 专项 | 断言/范围 |
| --- | --- |
| L1Check diagnosis | 40：四方向×正负延伸，必需未知/已加载错误/合法prefix/未知尾 |
| L1Check paid | 42：普通/工厂/核心 ×实时/恢复，101tick暂停、延迟材料不扣、端口不可刷新复活、恢复单次产出 |
| L1BoundaryCheck edges | 24：零段无尾/有尾、合法重叠尾之后的可选unknown、min不足的必需unknown、已加载错误与max+tail |
| targets | 6：generic无明确候选边界、明确蓝图、暂停时蓝图优先切换/朝向更换、无unloaded读取 |
| revisions | 8：运行期同ID换定义、磁盘恢复源指纹不同、compact SHA、旧tag迁移与西向恢复 |
| schedule | 1配置＋21 paid断言：真实delayed=false下三种引擎保存恢复后仍强制重检 |
| matrix | 48组、336 paid断言＋1组数断言：四方向×正负边界×普通/工厂/核心×实时/存读档 |
| chunks | 18：原生LevelChunk.clearAllBlockEntities、移除BE/能力失效、真实ChunkEvent对象直接交已实现handler、动态远端新实体恢复，三种引擎一次延迟付款/产物 |
| RotatedChunkStatusCheck fixed | 既有静态四向/正负20、缺chunk/无关raw chunk/已加载错误/恢复、generic边界全部保持 |

共同30调用改前/最终、V/W/X/Y/A及多核心承接套件均通过。动态旋转/尾重叠/多个模式、创造建造目标/暂停恢复、付款/身份/同步等既有正确期望未删除。test NO-SOURCE如实保留。

补充复跑：`python.exe -I C:/mmwork/_audit/remaining-all-0.121.76/l1-boundary.py <edges|targets|revisions|schedule|matrix|chunks>`，该脚本编译本批L1Check与support，原始命令/cwd/日志/退出码及类加载来源均保留。

### 未验与兼容边界

世界为真正ServerLevel类型的内存装置、区块为补齐原生方法所需字段的LevelChunk装置；执行原生清理/能力生命周期及真实生产匹配/控制器/结算，不是运行中的chunk provider、票据/生成/磁盘I/O、完整Forge事件总线、KubeJS/Rhino、真实玩家网络或游戏。ChunkEvent是实际原生对象直接调用handler，不冒称总线注册派发/完整FML已验。未测实机TPS或作收益承诺。

暂停保留formed只表示“既有结构的逻辑拥有关系/足迹仍保留”，**并不表示不可用期间允许加工**；状态CHUNK_UNLOADED且可执行端口为空。首次未成型失败后的恢复沿既有结构重试调度；已采用结构的暂停恢复则强制检查。本批没有实现任意线程/外部mod全局副作用原子事务。

344 Java /343资源/588 class，协议13、中英文各567键，资源不变。未部署、启动游戏、改实例/存档、暂存、提交、推送或创建Release。下一独立范围M/P08-04；G3/H/I契约仍未决，不称全部事项完成。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.76/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。344 Java / 343资源 / 588 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各567键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.76/modular-machinery-reborn-0.121.76.jar`，**1,695,765字节**。build/libs=reobfJar/output.jar=新归档SHA：

`4C2AE12975E2C2BC0F8D552B565F896206A67884B4CF5F2F4B4111F644019F89`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.76/README.md) · [版本记录](../../版本记录/更新记录-0.121.76.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 76 final <类名> 0 <模式>`；共同回归`regress 76 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
