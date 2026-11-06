> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex独立验收：本批指定范围通过离线验收。** 当前62/协议13、最近整版离线通过61；累计99/5/18。[验收](../已完成-2026-11-06/验收-第十二批8项-2026-11-04.md) · [仅Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)。原待验/完成描述保留供追溯；主任务暂停，未实机/部署/推送。

# MMCER 第十二批W修复结果 — 0.121.60

**待Codex验收；累计92/5/25不变，主任务保持暂停。最近已验收整版仍58。**

每总线每升级ID限制有效份数；动态升级按有效槽保留独立itemData与回调，声明效果按类型汇总，库存不删除。

## W1 / P06-13：完成指定范围

UpgradeEffects.read以每总线/每声明ID为预算，按稳定槽序分配不超过max-stack的有效份数。固定实例仍汇总；超额载体不修改、丢弃或截断。UpgradeStack构造保留旧签名，count变为有效非负份数并受声明上限；空/非正不凭空成为1，Bag过滤无效项，旧int总数接口饱和。脚本从同一Bag读取，getCount与实际效果一致；GUI列表的数量现在是有效份数，而不是物理库存。

`WCheck cap`55项通过：固定/动态、stackable两值、上限1/2/5/MAX、槽数2+3、实体库存不变；普通/工厂真实开工中256FE、4载体、上限2均实际支付64FE（余额1000→936），库存仍4，脚本count=2。两个15亿载体总计按MAX有效预算而不int回绕，×1在有界时间返回。大于4096份的幂使用Math.pow，普通数量保留原float逐次乘法；不声称所有极大幂的最后一位舍入与旧十亿次循环逐位相同。没有更改升级schema/Loader数值解析。

改前59同一cap正确断言在上限1时失败，最终60退出0。正常兼容筛选（包括machine=null的GUI/控制器区别）、nonstackable只一次、ADD只一次及跨总线独立效果保持。

## W2 / P06-14：完成指定范围

动态升级每个有效槽单独UpgradeStack视图，一个槽多份只一个实例/count为分得份数；相同NBT的不同槽也不合并。预算可能使第二槽只有部分份数或后续槽完全无效，无效实例不参加回调。声明修饰符另按同总线同ID汇总：nonstackable MULTIPLY和ADD只一次，stackable按总有效份数；不会因实例拆开把×2变×4。公开构造/方法和单项查询入口保留。

`WCheck dynamic`54项通过：普通/工厂、allow=true/false交换槽序、RecipeCheck START/END均看到两个槽自己的NBT，任一否决不扣开工料；实际RecipeTick/FactoryRecipeTick START阻止本tick付款、END保留本tick已付但不进度、下一tick恢复且不重付开工料。itemData读副本不改库存；getMachineUpgrades列两个，单项getMachineUpgrade保持稳定首实例。预算2+1且第三槽无效；同ID共享总线customData写后其他视图可读，真实总线保存/恢复保持，两条总线不串数据、效果仍各自一次。

`WCheck mixed`5项：同一载体映射固定+动态、第二载体也映射动态，相同NBT两槽独立回调看到2/1份；实际输出×2只一次、20FE×0.5²=5FE，ID重载后上限1立即反映到新Bag，物理槽不删。原UpgradeData正常对照（固定汇总、兼容、NBT防御副本、同ID重载、普通/工厂结构×3+升级×2输出6与20FE、跨总线独立）保持。V的111项int与19项屏幕组合也用60完整类通过。

改前59动态同一断言只看到[first]而失败；最终60两种顺序及两个阶段通过，未通过换槽首项掩盖第二实例。

## 旧版与装置说明

只读旧TileUpgradeBus:130–135按slotID保留动态实例，:138–153只对固定升级按类型合并。本版这次完善有效预算与动态视图，不将旧版当作运行期所有边界正确的证明，未启动旧游戏。

专项复用原UpgradeDataAuditCheck的解析/注册/总线辅助方法，只选正常对照，不全跑要求旧缺陷存在的main。现版本开工会从activeModifiers刷新升级效果，旧装置只反射写过时modifiers缓存导致结构×3丢失；改为把同一结构贡献放入当前activeModifiers来源，保留输出6/20FE正确断言，改前59复测通过。装置失败原始attempt日志保留。这是预成型结构/端口和原生总线库存，真实生产开工/事件与直接Java脚本桥，不声称完整结构收集/能力网络/服务端线程权限/Rhino已实测。MachineControllerAccess无需修改；没有把总线共享customData拆成逐槽存储。


## 基线、构建与归档

本批起点0.121.59，前后源码/资源/完整class逐项指纹、改前src完整副本、改前/最终完整类、`production.diff`、原始命令/真实退出码与class-load日志见工作区`_audit/twelfth-ai-8tasks-0.121.60/`。保留大量既有未提交改动，不按HEAD恢复。相对本批起点变更源码2份：

- `com/reborn/modularmachinery/upgrade/UpgradeEffects.java`
- `com/reborn/modularmachinery/upgrade/UpgradeStack.java`

本批最终341 Java / 342资源 / 569 class；资源逐项哈希不变，中英各566语言键一致、JAR JSON/mcmeta解析通过。新增helper/类如实计数，不假称仍340/568。

Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf真实退出0，test NO-SOURCE；未使用隔离fallback。`java17-version.log`、`build-normal-command.json`（含环境/cwd）、`build-normal.log`和`build-normal-exit.txt`保存。最终回归先放本批完整final-classes、剔除旧production/before/patched/compiled目录，进程各自独立cwd与日志，不以增量预编译当正式类。

每批改前及最终30个已验收回归调用全部通过：U8模式、U2BoundaryCheck172、LateBusyCheck13、T14、私有NBT12、ItemCopySafetyCheck **fixed**、InterfaceCopyCorrectCheck、FinalTickCheck、ControllerExtraCheck、Q1三条/原身份/428矩阵、创造目标60/暂停/恢复23/独立矩阵14，以及Will1121/LP41/Bloom120、旋转8组/动态9组/多块fixed/尾段fixed。单项专项见上文，所有原始失败/装置失败以attempt编号保留；不把脚本总退出0当某个失败通过。

JAR：`releases/modular-machinery-reborn/v0.121.60/modular-machinery-reborn-0.121.60.jar`，**1,651,148字节**。build/libs、reobfJar/output.jar、新归档SHA一致：

`AA6E2754F4A0A515B6E2D9BDF875A468C7133FD124CE170EE1CB596215C70DBF`

旧包保留未覆盖；[版本记录](../../版本记录/更新记录-0.121.60.md)，[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.60/README.md)。

复跑工具：Python `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，加`-I`；`C:/mmwork/_audit/twelfth-tools.py regress 60 final`重跑共同回归；`test 60 final <专项类> 0 <模式>`编译/执行本批专项，详细命令在本批`*-command.json`。复跑保留旧结果为attempt文件，不覆盖初始失败。

## 边界及未做

仅做本批白名单，其他批按串行后续交付；未扩做其他剩余编号、事务框架、AE2供应器或魔法/能源集成。旧版为本地只读源码对照，未运行旧游戏。离线原版包编解码、真实生产Java链、ScriptHooks直接Java桥、壳Level/原生或受控端口不等于真实Rhino/完整FML/服务端权限/多人网络/磁盘区块/GPU或游戏验收。不承诺任意外部能力回调、重入或并发全局原子性。

未部署/启动游戏、改实例/存档、reset/clean工作区、暂存、提交、推送或创建Release；Gradle clean只作用于项目build。账本不销号，不解除暂停。
