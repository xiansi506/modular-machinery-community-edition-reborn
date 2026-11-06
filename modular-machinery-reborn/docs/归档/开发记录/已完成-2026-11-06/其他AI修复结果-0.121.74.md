> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.74

**待Codex验收。本批编号：P07-16；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## K1 / P07-16：状态到正确等级材料

指定离线范围交付，待Codex验收。K拆分为两个独立patch；74只修K1，**K2/P07-17仍待处理**，不把原贪心算法称完整规划。

### 实现

PropertyBlockItem在实际Forge block-item注册/注销回调维护variant映射，按block+属性值取独立材料栈。所有自有仓口、casing，以及继承它的ParallelControllerItem/UpgradeBusItem复用同一入口。非variant原生方块保留Block.asItem fallback；仍有variant家族但未找到该tier时返回EMPTY，不能偷换最后注册item。没有扩展可消费NBT规则、伪造BlockEntityTag或自动生成结构checker所需库存。

MachineAssembly的规划、每步检查、真实扣料和缺料报告，以及StructurePreviews的JEI/投影component计数和modifier图标、PreviewPanel的选中/候选图标统一使用此映射；没有改GUI几何、3D渲染或网络格式。AssemblyIngredient纠正旧的“BlockItem单映射足够/BlockMatcher没有NBT规则”注释。

### 改前真实失败与最终测试

真实73完整生产类中，KMaterialCheck mapping正确断言退出1：目标tiny应需要tiny，实际得到ludicrous；display正确断言亦退出1。保持旧失败工件，未使用旧审计中“缺陷存在即PASS”的反向期望。

最终正式完整生产类优先，KMaterialCheck六模式共884断言全部退出0：

- mapping：250断言；实际生产DeferredRegister suppliers、Forge registry/item callbacks产生的62个非AE2 variant物品全部逐一校验；正确tier可只读规划，错误tier拒绝，映射栈独立，原生stone/air回退保留。
- display：248断言；实际缺料报告、JEI/投影component分tier计数、modifier及选中/候选图标均为对应物品，不因共享block合并成最后等级。
- execute：248断言；62个variant逐一通过真实start→tickPlace，生存扣正确物品一次、放对应state；创造无材料仍放对应state且不扣料。世界为ServerLevel内存sink，不伪称完整物理放置。
- callbacks：4断言；重复注册不重复登记，注销后不fallback最后等级，重新注册可恢复；带私有NBT物品仍拒作无tag材料，null代表状态返回EMPTY。
- recheck：124断言；62个variant在规划后换成错误tier，真实tickPlace拒绝放置且不扣错误物品；已存在的正确tier不再列入材料清单。
- events：10断言；单独测试agent仅对原生Forge BlockEvent及其子类应用已安装的EventSubclassTransformer，未改模组类或放置方法。校准active bus确实派发EntityPlaceEvent后，真实允许/取消/事件中换错tier三分支分别校验精确扣料或原生BlockSnapshot.restore。取消不扣料；事件中换错tier不消耗新物品且恢复AIR。复跑入口`event-check.py`，agent源码/编译/原始命令/日志保留。

测试初版“其他属性”遍历曾误把casing属性当非variant，随后又把等值Property对象按引用比较；两次fixture断言失败保留attempt，最终按被测item声明的variant Property.equals排除该属性。没有放宽tier行为期望，没有为fixture修复再次改生产代码或构建。

承接W normal首次失败也保留：新建support目录使旧carry脚本整目录exists判断跳过了上一版UpgradeDataAuditCheck适配源码，classpath因而退到历史编译夹具，其attach未设置当前结构修饰器的source-of-truth字段。未改73基线同样失败；修正carry为仅补入缺少的support文件，逐字承接73源码后，同一正确断言在before通过，最终套件重新执行。没有改生产代码或放宽joined duration/output/payment期望。

共同30调用改前/最终和承接V/W/X/Y/A多核心套件均以最终日志为准；原生目标身份/卸载暂停恢复/创造矩阵持续回归。Java17离线正式clean build/reobf完成，test NO-SOURCE；343 Java /343资源/579 class，资源无变化。

### 边界与后续

fixture执行实际ModBlocks的生产block/item suppliers并交实际ForgeRegistry注册，RegistryObject.value由装置绑定；没有完整FML/全部registry生命周期。AE2不在场，62是此次实际非AE2注册数，不称可选ME bus实机已验。execute模式的bus处于inactive；额外events模式已用原生Forge事件变换及active bus校准验证真实取消/换料分支，但不等于真实保护模组、完整邻居/BE/物理/磁盘或GUI/GPU已验。结构NBT/checker自动合成不在本项范围，不以等级修复冒充这个能力。

按2026-11-05用户“继续修复”恢复后续，接K2/P07-17，之后继续剩余独立项；G3/H/I未决契约不改称完成。无部署、游戏、实例/存档修改或暂存/提交/推送。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.74/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。343 Java / 343资源 / 579 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.74/modular-machinery-reborn-0.121.74.jar`，**1,678,999字节**。build/libs=reobfJar/output.jar=新归档SHA：

`9C1FD2C68C0AE89FC08390B0D068F9212D98B1D022B7DF8FB952E35C33796920`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.74/README.md) · [版本记录](../../版本记录/更新记录-0.121.74.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 74 final <类名> 0 <模式>`；共同回归`regress 74 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
