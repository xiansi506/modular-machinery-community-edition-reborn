> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex独立验收：本批指定范围通过离线验收。** 当前62/协议13、最近整版离线通过61；累计99/5/18。[验收](../已完成-2026-11-06/验收-第十二批8项-2026-11-04.md) · [仅Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)。原待验/完成描述保留供追溯；主任务暂停，未实机/部署/推送。

# MMCER 第十二批X修复结果 — 0.121.61

**待Codex验收；累计92/5/25不变，主任务保持暂停。最近已验收整版仍58。**

legacy无序NBT列表采用一对一增广匹配；物品/流体库存long安全聚合，旧int接口饱和，合法单批范围不变。

## X1 / P04-05：完成指定范围

NbtPredicate.ListNode先建立模板/实际索引的可匹配二分图，再用迭代增广路径求完整一对一分配。实际相同值按不同索引区分，每个索引最多使用一次；不再贪心固定首匹配，不用全排列，也不以递归深度实现增广。仅改legacy无序列表分配，literal精确顺序、类型/数字比较、compound子集、正则忽略大小写、空列表既有规则保持。

`XCheck nbt`1811项通过：宽条件>=1后精确1，对实际1/2或2/1都接受；900组固定种子的小规模样例以独立穷举分配oracle检查，再交换模板排列；重复值不足拒绝、重叠正则、嵌套compound/list、缺失/类型不符、中等96元素重叠案例完成。真实ItemRequirement.inputMatches、定义复制/wire后实际start付款、输入NBT不变、原生熔炉Items序列化进入结构NBT匹配均通过，literal列表顺序仍精确。

改前60相同正确断言在最小1/2分配失败，最终61退出0。只读旧NBTMatchingHelper:81–101同样首匹配贪心，本次是完善继承局限：旧来误拒的合法输入可变为接受。仅测96案例有界完成，不据此宣称任意规模更快。

## X2 / P05-04：完成指定范围

IngredientIo新增countLong（Ingredient/Predicate）与countFluidLong，正数库存安全累加到long，异常非正不增加；极端总量饱和Long.MAX_VALUE，旧public int接口饱和Integer.MAX_VALUE、签名保持。ItemRequirement普通容量消费真实long、特殊燃料/耐久容量也防聚合回绕；FluidRequirement准入/并行提示用long库存。数组条目保留int旧入口并增加局部long读取，数组并行/预留消费它。催化剂仍调用兼容int入口，阈值请求本来是int，饱和保证判断准确，不需要越白名单修改CatalystRequirement。

数组预留将份数×单份数量保持精确int乘法：总库存大可提高并行上界提示，但不能因新long可用量使原单批3,000,000,000数量误变为免费负数支付。未扩张单批long能力、其他流体乘法分支、公共事务或回滚框架。

`XCheck stock`26项通过：0/1/恰好/MAX附近/以上、多槽罐，两个15亿库存long实际总量30亿、旧int饱和；真实FluidTank与大容量受控物品handler，预检不改实仓，真实共享事务实际只付1煤/1mB；普通/工厂各正确开工，数组并行3+催化剂1实扣4，异物品/流体与负数量过滤保持；long消费者正确读到两份15亿的上界，但实际单批3b物品/流体/数组均受控拒绝。只使用少量数量字段，未创建/遍历数十亿对象。

改前60相同正确断言在“只需1煤的合法请求”因总量回绕失败，最终61退出0。按原P05-b报告区分库存聚合与请求量乘法，不把辅助上界当最终付款能力；未将其他未修数值项一并销号。

## 回归及装置说明

本批30个共同回归含旋转/动态/多块/尾段正确模式全部通过；V111/19、W55/54组合也在61完整类通过。资源不变；没有修改LiteralNbt、ResourceTransaction、HatchCollection或其他需求文件。

初版夹具误用了不存在的MachinePattern.Builder.add三参数重载，已改用既有add+rule；熔炉setItem在无Level时会查RecipeManager，改为填原生inventory后由原生save序列化，结构规则仍用真实匹配路径；数组单份15亿先被既有parser64上限合法拒绝，改为合法单份2×15亿并行测试同一总量3b边界，未放宽解析器。全部装置失败保留attempt日志，不称生产回归。真实Java桥/原生NBT与存储不等于Rhino/游戏实测。


## 基线、构建与归档

本批起点0.121.60，前后源码/资源/完整class逐项指纹、改前src完整副本、改前/最终完整类、`production.diff`、原始命令/真实退出码与class-load日志见工作区`_audit/twelfth-ai-8tasks-0.121.61/`。保留大量既有未提交改动，不按HEAD恢复。相对本批起点变更源码6份：

- `com/reborn/modularmachinery/recipe/FluidRequirement.java`
- `com/reborn/modularmachinery/recipe/IngredientArrayEntry.java`
- `com/reborn/modularmachinery/recipe/IngredientArrayRequirement.java`
- `com/reborn/modularmachinery/recipe/IngredientIo.java`
- `com/reborn/modularmachinery/recipe/ItemRequirement.java`
- `com/reborn/modularmachinery/recipe/NbtPredicate.java`

本批最终341 Java / 342资源 / 569 class；资源逐项哈希不变，中英各566语言键一致、JAR JSON/mcmeta解析通过。新增helper/类如实计数，不假称仍340/568。

Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf真实退出0，test NO-SOURCE；未使用隔离fallback。`java17-version.log`、`build-normal-command.json`（含环境/cwd）、`build-normal.log`和`build-normal-exit.txt`保存。最终回归先放本批完整final-classes、剔除旧production/before/patched/compiled目录，进程各自独立cwd与日志，不以增量预编译当正式类。

每批改前及最终30个已验收回归调用全部通过：U8模式、U2BoundaryCheck172、LateBusyCheck13、T14、私有NBT12、ItemCopySafetyCheck **fixed**、InterfaceCopyCorrectCheck、FinalTickCheck、ControllerExtraCheck、Q1三条/原身份/428矩阵、创造目标60/暂停/恢复23/独立矩阵14，以及Will1121/LP41/Bloom120、旋转8组/动态9组/多块fixed/尾段fixed。单项专项见上文，所有原始失败/装置失败以attempt编号保留；不把脚本总退出0当某个失败通过。

JAR：`releases/modular-machinery-reborn/v0.121.61/modular-machinery-reborn-0.121.61.jar`，**1,652,089字节**。build/libs、reobfJar/output.jar、新归档SHA一致：

`F318CF60B9407F364C268D2D4CB6125F4B9729D5C99DE759CF043B06BC61C7FF`

旧包保留未覆盖；[版本记录](../../版本记录/更新记录-0.121.61.md)，[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.61/README.md)。

复跑工具：Python `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，加`-I`；`C:/mmwork/_audit/twelfth-tools.py regress 61 final`重跑共同回归；`test 61 final <专项类> 0 <模式>`编译/执行本批专项，详细命令在本批`*-command.json`。复跑保留旧结果为attempt文件，不覆盖初始失败。

## 边界及未做

仅做本批白名单，其他批按串行后续交付；未扩做其他剩余编号、事务框架、AE2供应器或魔法/能源集成。旧版为本地只读源码对照，未运行旧游戏。离线原版包编解码、真实生产Java链、ScriptHooks直接Java桥、壳Level/原生或受控端口不等于真实Rhino/完整FML/服务端权限/多人网络/磁盘区块/GPU或游戏验收。不承诺任意外部能力回调、重入或并发全局原子性。

未部署/启动游戏、改实例/存档、reset/clean工作区、暂存、提交、推送或创建Release；Gradle clean只作用于项目build。账本不销号，不解除暂停。
