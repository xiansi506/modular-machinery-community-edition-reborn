> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.75

**待Codex验收。本批编号：P07-17；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## K2 / P07-17：有界完整材料分配与容器来源绑定

本批指定离线范围交付，待Codex验收；与74的K1合起来完成K的两个编号，不把其他未决项称完成。

### 实现与证明边界

- 新增一个局部AssemblyPlanner。固体按精确item/tag的单格需求与库存容量建立匹配：先给合法贪心seed，对未分配格执行**迭代增广路重分配**，不是候选排序。完整遍历结束得到最大基数匹配，允许partial时只执行已证实可行部分。
- 含液体/混合候选时，容器有drain→返回容器的状态转换；先检查完整合法快速解，否则在副本上按实际执行顺序对state及容器来源有界回溯，追踪最大可执行分配。不能预支未来返回物。原生桶按1000mB/格，保留返回桶。
- start保存所选流体输入item/tag；每步可用检查与事件后真实扣料只选择该来源，不能重新贪吃另一个留给后续格的容器。实际执行仍先完整扣副本，再写玩家库存。固体保留K1等级映射、strict sameItemSameTags与每步重查。
- 工作预算250000（候选/槽位/增广访问/复制等计量）、格数16384；混合回溯深度96。超过预算/格数/回溯深度是**规划未完成，材料是否足够尚未确定**：不列确定缺料、不修改实仓、不注册partial任务。中英文各新增一条明确诊断，不新增配置。简单完整解不受回溯深度96限制；复杂大计划可能需缩小后重试，不能声称无限规模全部求解。
- 世界/任务身份、方向/重载取消、卸载暂停/恢复、创造语义不变；没有NBT/checker库存合成、免费桶或自动退款。单独私有bindCandidates桥接仍保留正确旧测试入口；start用结构化结果区分预算与缺料。

### 改前失败与正式验证

真实74完整生产类中，原[stone|dirt],[stone]＋各1库存的正确断言退出1，观察unresolved=1；最终两种位置顺序均unresolved=0，实际start/tickPlace放DIRT/STONE并各扣1。容器来源竞争的正确断言在before也退出1，最终通过，没有把缺陷存在写成PASS。初版夹具曾在bootstrap前静态初始化Blocks，失败attempt保留；修正装置初始化时序后才取得真实生产反例。

最终第二次Java17正式clean build/reobf的完整584 class用于所有最终专项（第一次完整类保留build1，未在依赖测试执行中替换目录）：

| 专项 | 最终断言 | 覆盖 |
| --- | ---: | --- |
| KPlanCheck original | 8 | 原反例、顺序反转、只读规划、实际放置和各一次扣料 |
| oracle | 96498 | 2500个独立小规模穷举最大基数oracle ×3位置/候选/背包排列、重复容量、每格候选合法性、实仓不变 |
| fluidOracle | 6930 | 500个原生水桶/岩浆桶/固体混合穷举oracle ×2排列，实际来源数量与无实仓drain |
| fluid | 16 | 原生桶及solid/fluid竞争、正反顺序、真实放置与空桶返回 |
| partial | 8 | needAll=true零扣料拒绝；false执行两个有解竞争格而非只贪到一格 |
| budget | 10 | 显式预算不足、不伪报缺料、16385格拒绝且不注册partial；1500格简单容量计划成功 |
| limits | 29 | 混合回溯深度、低于格数上限但真实工作预算耗尽的2000格计划、各槽原库存不变 |
| KFluidSourceCheck | 21 | 原生水桶＋受控可转换容器：实际先用所选来源保留后续来源；来源tag变化拒绝替代；返回材料只可用于后续，不能预支 |
| KFluidSourceCheck events | 15 | 原生Forge事件变换＋active bus正向校准，允许/取消/事件中改变容器三路径，恢复BlockSnapshot、拒绝错误扣料且保留后续来源 |

K1承接62变体mapping250/execute248通过；共同30调用改前/最终均退出0，最终V/W/X/Y/A多核心承接套件全部通过。事件复跑：`python.exe -I C:/mmwork/_audit/remaining-event-check.py 75 KFluidSourceCheck`，只变换原生Forge BlockEvent子类，不替换生产放置/规划逻辑。原命令、cwd、类加载来源、退出码和所有失败attempt均保留。

### 未验与兼容边界

真实原生桶/生产入口/Inventory/Forge事件/BlockSnapshot已执行；世界是ServerLevel内存sink、玩家是受控壳。可转换容器是明确标注的受控能力，不代表任意外部能力的纯复制/权限/并发/异常契约均得到证明，也不改变H/I未决结论。未运行完整Forge/FML、保护模组、真实流体物理/邻居更新/区块磁盘或游戏；离线计时只记录装置方法，不作TPS收益承诺。

Java17离线构建成功，test NO-SOURCE；344 Java /343资源/584 class，中英文各567键。资源只有两语言文件各加一条planning_incomplete，其他资源包括J hook不变。下一独立范围为L1/P07-10动态区块诊断，L2/L3已在72交付不重复修；G3/H/I契约仍待实质决定。无部署/启动游戏/实例存档修改/暂存/提交/推送。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.75/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。344 Java / 343资源 / 584 class；相对本批起点只修改scope.tsv允许文件，资源仅按resource-scope.json在中英文各新增一条规划未完成文案，其余资源哈希不变、中英各567键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.75/modular-machinery-reborn-0.121.75.jar`，**1,688,686字节**。build/libs=reobfJar/output.jar=新归档SHA：

`D0C19453F16AAC8FB9A7078AF7B1017111F57D9D709676247840073FCB702BDC`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.75/README.md) · [版本记录](../../版本记录/更新记录-0.121.75.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 75 final <类名> 0 <模式>`；共同回归`regress 75 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
