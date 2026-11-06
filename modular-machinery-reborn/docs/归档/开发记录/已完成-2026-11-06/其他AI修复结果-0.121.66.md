> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.66

**待Codex验收。本批编号：P03-09；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## D / P03-09：字段矩阵范围完成，待Codex验收

开工先保存`fields.tsv`，逐项列字段→类型/范围→旧兼容→解析→消费者→测试。没有只修数个根字段就覆盖整编号。新增唯一共享helper `util/JsonNumbers.java`：从原JSON数值文本构造BigDecimal，在范围检查后精确longValueExact/int转换，不能先getAsDouble再判断整数；极大指数/极小非整数/2^64+1等受控拒绝，不申请巨大整数展开。有限float/double与boolean验证按字段契约使用；仅既有bounding-box数字字符串显式例外。

覆盖机器线程/并行、所有复用坐标标量/数组、动态大小、接口priority；配方recipeTime/max-parallelism/priority/command interval；modifier operation/affectChance；item amount/min/max、fluid、数组/催化剂份数；trigger-time；gas amount；整数魔法amount/min/max；压缩空气air/time/pressure；升级max-stack/level。原本严格boolean和有限系数路径保持；energyPerTick既有精确非负long不改。chance保证有限数值，item/fluid原执行端范围夹取保持，数组/气体既有0..1规则保持。

合法priority/坐标负值、命令-1/0、部分整数零值保持。物品/流体amount=0原构造归一1的已验兼容保持，不将0误当免费资源；整数魔法amount=0仍合法显式零。没有全局允许quoted number，也没有把已有bounding-box兼容写法一刀禁止。动态/stackable/perTick/parallelize-unaffected等已严格字段不重造缺陷。

## 正确行为证据

起点65中recipeTime `1.0000000000000001`被截断1，正确拒绝断言失败；最终66 minimum1条通过。ExactNumberCheck matrix219项通过：1.9、负小数、4294967297、LongMAX+1、2^64+1、1.0000000000000001、1e-1000、1e1000000000、字符串/boolean误类型；真实机器/配方/需求/升级解析，动态/接口priority、反射调用真实气体amount读取器、整数魔法各族；level/chance/bbox极大有限转非有限拒绝；合法MIN priority/MAX工期、LongMAX能源、0/1能源、命令-1/0、legacy bbox字符串及1.0精确整数保持。合法需求实际parser→wire→fresh copy→start准确扣1煤。创作者RecipeDocument也受控拒绝非整数recipeTime；初版装置漏必需recipe type导致NPE，补合法type后保持同一行为断言，失败attempt保留。

现有共同30调用、V/W/X/Y全套、A多核心40组623全部通过；C text27/sync41及B duration360组合通过。能源9007199254740993/LongMAX精度、typed Will1121、LP41、已有注册ID/有限系数/接口自定义文案、W预算和T定义复制保持。新增helper使计数如实变为342 Java/570 class，资源342不变、协议13不变。

## 实际边界

本批做作者JSON直接入口与同解析链typed/创作者校验，不修改原生NBT数值访问、GUI内部同步JSON、BooleanSupplier或世界运行规则。公开Java typed参数一旦已是int/double，本来无法恢复调用前丢失的小数文本；不把这一事实伪装为JSON能静默截断的理由，也不声称完整Rhino验收。气体字段验证运行真实方法但没有启动真实Mekanism网络；可选后端和真实客户端兼容仍需实机。字段表标注原本已安全路径与非本根因路径；不扩张整数魔法/单批数量能力。

旧版原报告已确认recipeTime/priority/interval等也使用getAsInt，本批修的是继承/新增入口中的精确校验缺口，不称全部都是新移植回归。未改99/5/18验收账本，后续E仍单独处理烘入数值，运行期计费/概率等不在本批冒销号。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.66/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 570 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.66/modular-machinery-reborn-0.121.66.jar`，**1,659,897字节**。build/libs=reobfJar/output.jar=新归档SHA：

`BE122D3F01C4E9C12C1849757171E545A67B002FBB9759F6273A83E83A1DE340`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.66/README.md) · [版本记录](../../版本记录/更新记录-0.121.66.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 66 final <类名> 0 <模式>`；共同回归`regress 66 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
