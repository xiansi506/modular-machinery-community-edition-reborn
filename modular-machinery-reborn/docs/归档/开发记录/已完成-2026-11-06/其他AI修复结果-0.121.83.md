> **独立验收结论：本批82回归返工通过指定离线复验；P05-07整体不销号，118/1/3保持。** [83验收](验收-83回归返工-2026-11-05.md)。以下交付时“待验收”等文字保留为历史。

# MMCER剩余总修复交付 — 0.121.83

**待Codex验收。本批编号：P05-07；独立82验收118/1/3账本不变，主任务保持暂停。** 本批先修附件指出的82零/固定成本误拒回归；P05-07原大范围/一次性start余项仍未完成，不自行销号。H/I已选方向保持有效，不混入本返工。

## 82小数搜索误拒回归返工 / P05-07（原G3余项仍未完成）

依据用户附件[返工交接](返工交接-82小数搜索误拒-2026-11-05.md)实施，仅修本次新增回归，**待Codex验收**。独立63～82验收已将账本更新为118完成/1部分/3待处理，本批保存该实际账本及验收文档指纹，不回退旧99/5/18，也不自行销号。82误拒不能归为“原大范围尚未支持”；本次先单独返工，不混改H/I。

### 根因与局部修改

82仅看WILL/EMBER和原始amount>0就进入有限非单调搜索，把已经修改为零、perTick补偿为零、parallelize-unaffected固定份数以及不参与当前阶段检查的约束一并误限4096。

83新增MagicRequirement纯只读`hasCandidateDependentFractionalCost(modifiers)`，直接复用真实付款的`total(modifiers,1)`数值/补偿顺序；非整数族、候选份数确实可变化且有效单份成本非零时才返回true。fixed成本仍参加shared付款检查，只是不再自行触发非单调候选分支；原始0经ADD变正仍视为正可变成本，非finite不会被当合法零。

MachineRecipe再复用实际`inPhase`，只将本次start/tick/finish真正会保留的约束纳入分类。忽略finish输出、已触发且不参与当前检查的延迟项不再误触发预算。**任意另一条实际正可变小数需求仍进入82完整shared候选检查**，不因一条zero/fixed就把整recipe当单调。

只改MachineRecipe/MagicRequirement两份Java及版本，无资源/协议/计费变化。82普通控制器/工厂的incomplete不fallback1守卫、MagicPort movementMatches和资源事务源码逐字与基线一致；不删除预算、不把4096改MAX、不临时修改实仓规划、不新增体系。

### 改前真实失败与最终正确断言

附件的ZeroAndFixedCheck逐字复制（校验相同bytes），使用真实82完整生产类分别运行modifier/duration/fixed，三种均真实退出1：选择0、incomplete=true，但显式8192 canTick/tick成功。modifier/duration实际余额不变；fixed只扣1。最终同样三条原正确期望均退出0、选择8192、incomplete=false，对应实际付款精确不变/只扣1，没有改成缺陷存在即PASS。

独立82审计源码指纹与本次开工源码逐项一致，build/libs=reobf=82旧归档SHA核对，83使用新审计/新归档不覆盖旧包。原前测失败、最终日志、原命令、退出码、独立cwd/编译目录、最终生产类来源完整保存。

### 专项范围与承接

- FractionalReworkCheck matrix：5036断言；WILL/EMBER×输入输出×start/tick×fixed/可并行×原始0/1×modifier0/正常×duration0/0.5/1/2×4095/4096/4097/8192/MAX，校验纯分类、实际最高并行/不误报incomplete、原份数恢复/实仓不变及真实精确付款。
- mixed：20断言；zero/fixed与另一正可变小数需求共享仍选16，超8192保持incomplete零付款；原始0被ADD改正不能绕过预算。
- phases：8断言；ignored finish、已付延迟、未付延迟的分类；极小固定正成本真的不可付款时仍由完整阶段检查拒绝，不能因fixed就当免费或放过要求。
- engines：12断言；WILL/EMBER真实预成型普通/工厂/核心，合法8192份准确消费8192煤、输出8192，modifier0魔法不扣、fixed只扣1；不是fallback1或只查getter。
- PurityCheck：3断言/1000次查询；成本查询不触碰port、不invoke item callback、不改需求parallelism（实际代码只调用纯modifier数学，无脚本桥）。

本次扩展专项5079断言，另附件三条原失败；82 FractionalSearchCheck oracle722/budget3/controllers6、FractionalBoundCheck2、GroupCostCheck fractional3及整数325保持。必要承接73实际Forge-filtered JS变换/native reload矩阵、77组绑定、78消费收据、81重叠Aura/迁移oracle与共同30/VWXYA多核心等均使用83最终完整生产类优先，以最后原始日志为准；未扩大失败白名单或删正确断言。

engines装置初版把8192煤塞一个原生槽，原生事务受真实单槽提取上限限制只做64份，失败attempt保留。改为128个合法64煤槽、129输出槽，仍断言真实8192消费/产出；未调整生产库存/支付规则或改期望为64。原始83生产只构建一次，无因装置修正重新改业务代码。

### 构建、账本与仍未完成事项

Java17正式clean build/reobf成功，test NO-SOURCE；346 Java/343资源/597 class，中英文569键，协议13，资源不变。校验器首次按旧63账本比对失败，查明确是用户附带的独立82验收新增文档/118/1/3；已保存其新保护快照，验证本次未改这些文件，不能把独立验收变化当工作区污染回退。旧历次报告保留历史，本批当前状态按独立82验收。

**本次只修82新回归，不称P05-07完整收口。** 原大范围精确格点模型、一次性start“单份不可表示但多份可付”预检余项保持未完成；真正正可变成本超过预算仍拒绝新付款/不丢已付状态。H/I付款前安全拒绝的已选方向继续有效，本批没有改事务或能力策略。结果待Codex验收，验收账本118/1/3不变。

离线世界/库存/直接Java脚本桥/控制器装置不等于完整Forge/Rhino/真实后端网络/权限/区块磁盘/GPU或游戏；未启动游戏、部署、修改实例/存档、暂存、提交、推送或创建Release。此返工完成后再按用户最新指示继续后续范围，不把返工自动替代剩余总任务。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.83/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 597 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各569键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.83/modular-machinery-reborn-0.121.83.jar`，**1,722,350字节**。build/libs=reobfJar/output.jar=新归档SHA：

`26378E7794BDD289F9565CC7E687561707C20AB3A15095E8A83D1FCD3F1AF857`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.83/README.md) · [版本记录](../../版本记录/更新记录-0.121.83.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 83 final <类名> 0 <模式>`；共同回归`regress 83 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
