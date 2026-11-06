> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.65

**待Codex验收。本批编号：P03-02, P04-01；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## C1 / P04-01：本批范围完成，待Codex验收

实际改前64的fromJson接受32768字符tooltips，正确“加载即拒绝”断言失败。最终在MachineSerializer返回定义前检查根machine/registryName/tooltips/commands文本，MachineRequirementParser检查selector、整个compact options及item/array/catalyst NBT source，并调用真实注册类型writer探测其他文本字段；错误包含配方ID/requirements索引和字段/编码位置。MachineRequirement.setScriptOptions也限制整个序列化聚合，4000个短键不能绕过。不扩大wire字符上限，不将item/接口服务端复制退回wire。

DefinitionBoundaryCheck text27项通过：ASCII/中文tooltip32767合法、32768加载拒绝；selector/script-tooltip/长接口文本/NBT/4000回调键/命令/registryName；转义/中文compact options边界及精确32766/32767/32768长度；数组/催化剂NBT；合法数据实际toNetwork/fromNetwork、executionCopy及控制器扣1煤开工。已存在创作者RecipeDocument的16000字符/24000字节文档门禁更早拒绝本例，属于不可复现的已安全入口，未冒称本批新增修复、也未放宽它。

字段矩阵：root.machine[*]/registryName/tooltips[*]/commands[*]均默认writeUtf32767；type-id/IO枚举为内部标识；selector与整个options JSON同上；item输入/array/catalyst NBT source、catalyst tooltip、interface type及magic variant由对应writer探测。真实ItemStack/显示NBT仍用原生NBT编码，不错误套用source字符上限，也不改完整服务端私有NBT副本。第三方新增注册类型的writer按当前提供实现验证，不声称可验证任意尚未安装类型。

## C2 / P03-02：本批范围完成，待Codex验收

实际改前64的MachineLoader.apply采用变量casing成功，但snapshot/native packet/ClientMachines.accept拒绝同一变量机器，正确断言失败。最终MachineSchema在解析成功后保留**完整源JSON的深副本**，仅展开schema所属的parts、单块修饰器、多块parts、动态unit/tail的elements，并显式写入服务端已采用的max/internal parallel、parallelizable、max-threads、has-factory及配置color；规则/selector/preview-NBT/其余原字段不以简化模型重建。不可变MachineDefinition持有此解析上下文并只返回深副本；snapshot优先使用已采用上下文，不再依赖客户端变量表或本地COMMON默认值。原MachineRegistry.sources仍保留原作者源及来源优先级。

DefinitionBoundaryCheck sync41/defaults16项通过：实际Loader→snapshot→真实编解码→客户端accept，普通/单块/多块/动态变量及重载/删除、base规则/selector、每个pattern及修饰值、动态几何/尾段、根属性逐字段比较；服务端max64、factory默认4/true、parallelizable=false、color112233后换客户端max128/factory20/false仍重建服务端值，显式7优先，断线清空/重连后保持。SourcePriorityCorrectCheck完整机器/升级来源优先级及删除链正常对照保持。

单定义仍用既有1MiB字符UTF上限，聚合加保守2MiB字节边界（包含UTF前缀/ID），不截断字段/条目。MachineLoader在发布前验证整个候选snapshot；超规模明确拒绝而非到登录才编码失败。limits3项验证单份JSON/聚合/解析展开超限拒绝。这里是机器同步规模预校验，不冒称J批全局reload事务已完成。原有JSON包布局/字段表达都不变，显式已有字段及展开数组旧客户端也能解析，因此协议保持13；未强行新增不兼容封套。

## 验证与未验边界

共同30调用、V/W/X/Y及A多核心623均在最终完整类通过。B工期专项在65继续保持（minimal2/matrix360/boundaries54）。没有改资源/语言文件或新增集成。解析期校验经实际writer，不运行脚本checker/modifier或付款。

装置曾用record.equals比较BlockMatcher对象身份而非描述符，改为逐候选描述符/字段比较；author校验先抛既有IllegalArgumentException而非JsonParseException，改为如实记录已安全入口。一次早期回归尚在运行时，重构建将旧final类目录归档，使最后结构测试找不到ModBlocks；原始失败保留，停止该混用后最终完整30调用全部重新跑0。该失败是测试装置/构建目录生命周期错误，不当作生产缺陷或通过。本批其余初次文本路径误差日志保留。

未运行真实Forge登录/网络握手/JEI/Rhino；配置差异为真实ForgeConfigSpec在离线进程中的两次独立配置视图，不能称两机联机实测。源JSON快照覆盖经Schema解析采用的定义；无可解析来源且直接程序构造的任意新模型仍沿用原警告/同步边界，不虚构自动反序列化所有外部对象。所有旧验收账本保持。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.65/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。341 Java / 342资源 / 569 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.65/modular-machinery-reborn-0.121.65.jar`，**1,656,808字节**。build/libs=reobfJar/output.jar=新归档SHA：

`DE3D01D5BDAAEEFC9C4B91D4DDBFF9FD14D084821FE4B0CEC2ACA5E3D7160800`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.65/README.md) · [版本记录](../../版本记录/更新记录-0.121.65.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 65 final <类名> 0 <模式>`；共同回归`regress 65 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
