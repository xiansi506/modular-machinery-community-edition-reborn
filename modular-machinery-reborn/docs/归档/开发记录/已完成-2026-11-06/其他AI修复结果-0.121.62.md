> **2026-11-04 Codex独立验收：Y1仍待返工：多个撤销核心排空触发ConcurrentModificationException，62不能标整版通过。** 当前62/协议13、最近整版离线通过61；累计99/5/18。[验收](验收-第十二批8项-2026-11-04.md) · [仅Y1返工](返工交接-第十二批Y1-2026-11-04.md)。原待验/完成描述保留供追溯；主任务暂停，未实机/部署/推送。

# MMCER 第十二批Y修复结果 — 0.121.62

**待Codex验收；累计92/5/25不变，主任务保持暂停。最近已验收整版仍58。**

已撤销声明但持有合法已付批次的核心可保存/恢复排空，只完成一次且不重开；身份/高水位及58准入守卫保持。

## Y1 / P06-08：完成指定范围

本批仅FactoryEngine.java生产源码变化。load先沿用原声明槽恢复，再按保存顺序处理已不在当前声明中的条目：只有非空名字、有效活动配方且通过原restore的注册/机器归属/执行定义校验，才恢复为待排空核心。空闲/无效未知槽丢弃，不转普通槽，也不永久恢复声明。冻结的进度、组、并行、时长、修饰符、脚本及触发/付款状态仍走既有restore路径，不重新开工或重付料。

所有保存的有效核心身份仍先预留/统计，再分配缺失或重复身份；高水位与耗尽行为保持。恢复未知名的保存顺序改用LinkedHashMap确保确定性。没有新增NBT必填字段，58旧保存可读，协议仍13、配方wire不变。

begin的原58纯守卫仍在规划后提交前，普通额度/排除自身逻辑不变；核心不占普通额度，但已撤销声明的核心不能开新批。tryRestart拒绝撤销核心并在已付完成后清理；同名声明在清理前返回沿用当前槽，真正移除后再建则新身份。FactoryThread无需修改，未重写调度。

## 正确断言与分支矩阵

`YCheck branches`8组/84项：静态声明/脚本声明撤销、核心单独/与普通并行、输出畅通/满仓阻塞的笛卡尔组合。真实开工已付的核心持有并行2、组1、催化剂使时长8→4、永久能源×2、临时输出×2、delayed输入已付标记及customData。保存点先复制库存/输出/能源真实快照，分别运行原实例和新实例读档的**替代分支**；不把两个分支的产物相加。

读档前立即重存保留pending payload，第一次真实tick恢复原ordinal/组/并行/时长/数据；中途再次保存/新实例恢复继续。所有组合最终库存/能源/产出逐项与不读档分支等价：核心只出一次4石头、已付煤/催化剂/延迟料不再扣，普通批次同时独立排空；无新增成功开工事件，核心完成一次后被清理。输出堵塞时在既有final边界等待，释放后完成。

同一正确断言在改前61完整类退出1：保存真实存在的已付核心在当前无声明时没有恢复；最终62退出0。见`before-YCheck-branches.log`与`final-YCheck-branches.log`及对应真实命令/退出码。

`YCheck extras`29项：同名声明在pending恢复前/恢复后返回均与不读档分支保持同一身份；真正清理后再建获得新高水位身份；配方缺失、归属变、定义变、空闲/无效配方ID均按原校验丢弃且不退款/造产物；legacy缺失ordinal迁移一次，重存后稳定直到正常完成清理。

`YCheck failures`21项：RESET/STILL/DECREASE下撤销核心遇缺能量仍保留已付工作，读档/不读档失败进度与资源一致；恢复能源后等价完成一次，没有再次扣开工料/催化剂/已付延迟料。

本批30个共同回归全部通过，含58 late、172边界、LateBusy13及Q1三条/原身份/428矩阵。最终62也重跑V111/19、W55/54、X1811/26，前批组合保持。另补MenuSegmentsCheck8项，原生短包经MagicHatchMenu24段还原6个double（含高位/极大有限值），未重新包装魔法数据；测试直接提供原生菜单字，不称实际魔法后端生成该数值。

## 旧版与装置迭代

只读旧TileFactoryController.readCustomNBT:607–614直接遍历coreThreadList反序列化，并非仅遍历当前声明；本版在自己的归属/执行定义校验及已接受身份规则下补回合法排空恢复，未启动旧版游戏。

不以公开removeCoreThread必然能删忙槽为前提。静态路径真实MachineRegistry.replace撤销定义声明；脚本路径修改声明集合表示撤销，不宣称公开忙核心删除权限改变。原P06-07忙时受控拒绝的Java公开方法未改。

初版夹具错误期望满输出在进度2就停，真实既有final边界为3，已调整为进入并保持final边界后再释放，生产代码未为装置改调度；另一次夹具试图在正常完成清理后继续取核心，改为完成前验证保存身份、完成后验证清理/产物。原始attempt日志保留。分支库存均由保存点快照复制，不从已完成分支凭空拼产物；真实Level/FML/磁盘读档仍未验。

第十二批V/W/X/Y共8项已交付，四份报告均待Codex验收。92/5/25账本不变，主任务暂停，本批交付后停止，不自动接其他事项。

最终补充VPacketBoundaryCheck11项通过：单项保留服务端另一字段，兼容Java构造表示显式两项；null/客户端sender、非当前菜单、removed/不可分组实体、非法掩码0/4/255与负组号拒绝。现行中英文教程的当前协议提示同步13，历史验收/旧版本记录保留原协议12。


## 基线、构建与归档

本批起点0.121.61，前后源码/资源/完整class逐项指纹、改前src完整副本、改前/最终完整类、`production.diff`、原始命令/真实退出码与class-load日志见工作区`_audit/twelfth-ai-8tasks-0.121.62/`。保留大量既有未提交改动，不按HEAD恢复。相对本批起点变更源码1份：

- `com/reborn/modularmachinery/factory/FactoryEngine.java`

本批最终341 Java / 342资源 / 569 class；资源逐项哈希不变，中英各566语言键一致、JAR JSON/mcmeta解析通过。新增helper/类如实计数，不假称仍340/568。

Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf真实退出0，test NO-SOURCE；未使用隔离fallback。`java17-version.log`、`build-normal-command.json`（含环境/cwd）、`build-normal.log`和`build-normal-exit.txt`保存。最终回归先放本批完整final-classes、剔除旧production/before/patched/compiled目录，进程各自独立cwd与日志，不以增量预编译当正式类。

每批改前及最终30个已验收回归调用全部通过：U8模式、U2BoundaryCheck172、LateBusyCheck13、T14、私有NBT12、ItemCopySafetyCheck **fixed**、InterfaceCopyCorrectCheck、FinalTickCheck、ControllerExtraCheck、Q1三条/原身份/428矩阵、创造目标60/暂停/恢复23/独立矩阵14，以及Will1121/LP41/Bloom120、旋转8组/动态9组/多块fixed/尾段fixed。单项专项见上文，所有原始失败/装置失败以attempt编号保留；不把脚本总退出0当某个失败通过。

JAR：`releases/modular-machinery-reborn/v0.121.62/modular-machinery-reborn-0.121.62.jar`，**1,652,670字节**。build/libs、reobfJar/output.jar、新归档SHA一致：

`560973BF5701F49F41293AA21C192336B698CB7C5E313B19AB8FFE41F21B399E`

旧包保留未覆盖；[版本记录](../../版本记录/更新记录-0.121.62.md)，[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.62/README.md)。

复跑工具：Python `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，加`-I`；`C:/mmwork/_audit/twelfth-tools.py regress 62 final`重跑共同回归；`test 62 final <专项类> 0 <模式>`编译/执行本批专项，详细命令在本批`*-command.json`。复跑保留旧结果为attempt文件，不覆盖初始失败。

## 边界及未做

仅做本批白名单，其他批按串行后续交付；未扩做其他剩余编号、事务框架、AE2供应器或魔法/能源集成。旧版为本地只读源码对照，未运行旧游戏。离线原版包编解码、真实生产Java链、ScriptHooks直接Java桥、壳Level/原生或受控端口不等于真实Rhino/完整FML/服务端权限/多人网络/磁盘区块/GPU或游戏验收。不承诺任意外部能力回调、重入或并发全局原子性。

未部署/启动游戏、改实例/存档、reset/clean工作区、暂存、提交、推送或创建Release；Gradle clean只作用于项目build。账本不销号，不解除暂停。
