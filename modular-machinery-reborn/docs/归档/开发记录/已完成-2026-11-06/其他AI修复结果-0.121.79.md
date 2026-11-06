> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.79

**待Codex验收。本批编号：P08-03；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## O / P08-03：不可达直推的显式兼容保留

**维护项指定离线范围交付，待Codex验收；不是声称消除了一个可利用漏洞。** 99/5/18验收账本不变。

### 有证据的维护决定

全部当前生产源码搜索没有PatternInputTransfer.push调用方；实际供应器pushPattern字节码调用ProviderState.accept。直推class/interface/method属于公开API，没有证据能断言外部模组无人使用，因此本批不删除、不改空实现、不复活成新供应器模式。

- PatternInputTransfer公开类标记`@Deprecated(since="0.121.79",forRemoval=false)`，文档明确其为外部显式兼容入口、无生产供应器路由，以及ResourceTransaction支持契约不等于任意外部能力通用原子性。原方法实现不动。
- AppMekGasBridge增加仅活跃缓冲注册入口registerProviderBuffers，正常三依赖门禁内enqueue此方法，只安装ProviderBuffer气体支持，**不碰显式legacy callback**。
- 原公开register仍保留“安装旧pushGas回调＋活跃气体buffer”的完整原行为；register/pushGas明确弃用但不删除、不no-op。原encodedGas与network仍供真实ME总线调用，不修改。ProviderBuffer/ProviderGasPort活跃缓冲没有删减。

### 改前与最终验证

改前78完整生产类中OCheck boundary真实退出1（不存在新独立注册入口），这是维护契约的前后断言，不冒称新的游戏故障；legacy实际物品转移2断言改前已通过，证明保留已有可用行为而非先造bug。最终两模式均通过：4条注册边界/弃用/公开签名断言＋2条直接调用兼容功能断言。

实际final ModCompat/AppMekGasBridge/PatternInputTransfer/MePatternProviderBlockEntity执行javap完整字节码，call-graph.json和日志证明：正常启动MethodHandle只指向registerProviderBuffers；显式旧register仍引用setAdditionalInput；provider路由只到ProviderState.accept，无直推调用。逐源引用也没有push调用方。类加载来源为本版完整生产类，不是历史patched副本。

OGasCheck承接原真实ProviderCheck测试，行为期望不改，只将两个正常初始化/重装入口改为registerProviderBuffers：**34组通过/0失败**。原生AEItemKey/AEFluidKey/AppMek MekanismKey/GasStack、真实ProviderState/Buffer/ProviderGasPort/事务执行，含物品/流体/气体大数量NBT、隔离36槽、别名不重复支付、三媒介事务/后置false恢复、外部更新拒绝、部分退回、手动输入保留、缺依赖gas保留后恢复。不是拿旧编译生产类测试。

optional-check四个独立JVM组合（无AE2、无Mekanism、无AppMek、三者全无）实际剔除依赖并断言API不存在，再初始化ModCompat/common controller成功；这不是完整FML启动。全部依赖在场的原生gas34组形成对照。启动门禁本身保持原三个isLoaded条件，未绕过gate强加载可选类。

共同30调用改前/最终及VWXYA多核心承接套件通过，以最后日志为准。Java17离线clean build/reobf退出0，test NO-SOURCE，完整592 class、346 Java/343资源、中英文568键，协议13/资源不变。

### 失败工件与装置边界

javap输出使用本机代码页，首次UTF-8读取失败，原日志保存；改为容错解码后仅匹配ASCII符号/方法描述符，正确断言不变。并行进度读取碰到另一runner正将exit文件归档成attempt造成FileNotFoundError；工具补窄范围读取消失文件的容错，初版缩进错误使suite在前两项后停止，已修工具并重跑完整suite。不是生产失败、不修改结果白名单，也不将中断runner当全部通过。

完整Forge/AE2 CPU/真实网络能耗、实际区块提供器/磁盘、保护模组、多人、Rhino、GPU或游戏未运行。native key/type registry由装置补齐初始化；气体网络使用受控装置，不声称在线AppMek网络实机通过。保留公开直推不会赋予H/I未知能力可逆性保证，调用者仍须满足对应事务支持契约。没有新增第三方适配器/能源后端/魔法体系。

未部署、启动游戏、修改实例/存档、暂存、提交、推送或创建Release。O以公开兼容保留及正常启动隔离方案交验，不以“删光死代码”冒充完成。下一P/P09-01；G3/H/I实质契约仍未决，不称全部事项完成。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.79/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。346 Java / 343资源 / 592 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各568键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.79/modular-machinery-reborn-0.121.79.jar`，**1,709,001字节**。build/libs=reobfJar/output.jar=新归档SHA：

`91B10D7333CF7924E2B3E31DEFD13441973AA7D6BAB105BE59CC81A8A6426081`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.79/README.md) · [版本记录](../../版本记录/更新记录-0.121.79.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 79 final <类名> 0 <模式>`；共同回归`regress 79 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
