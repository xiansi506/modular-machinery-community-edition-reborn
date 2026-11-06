> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.69

**待Codex验收。本批编号：P05-05, P05-06；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## G1/P05-05、G2/P05-06：完成指定范围，待Codex验收

EnergyRequirement.parallelLimit以真实EnergyMath.draw(perCopy,N,factor)作单调整数搜索，不再将“单份先取整成本”当除数。保留精确long、最小正数、饱和及最终共享预留，不以double相除放行。原反例1FE×6×0.5=3FE，库存3时上界6。energy281项含独立小规模穷举oracle、0/低耗/因子0.5/1/2、输入输出及真实余额变化通过；改前68正确断言失败。

MagicRequirement整数族将修饰后单份×补偿×份数作为整组再floor，正总数取至少1，作者显式/修饰为0或补偿因子0仍0，不把所有资源/零成本强钳1。保留已实现mana/aura/LP和压缩空气继承的逐tick补偿，不无提示删除功能；ModConfig仅改已有文案说明它也作用已支持魔法资源。integer325项含mana/aura/LP、量0/1/3、P1/2/5、factor0.5/1/2、输入输出、fixed与实际共享事务扣款通过；1×2×0.5准确支付1而非0。controllers6条真实普通/工厂/核心，开启/关闭补偿配置后的实际FE/魔法余额与开工2煤准确。夹具曾缺mana类型注册导致copy失败，补现有可选类型注册后通过，未为装置改生产注册行为。

共同30调用及V/W/X/Y/A多核心保持，未新增魔法/能源体系或修改交易回滚。计数342 Java/570 class，资源不变、协议13。

## G3/P05-07：已复现，待实质契约决定，未实施/未收口

`before-GroupCostCheck-fractional.log`正确断言退出1：余额1e8/单位2^-30，8不可表示且余额不动，16一个ULP实际扣款可行，upper17共享搜索却选0。原movementMatches守卫保留，未通过余额不变的success冒充付款。小数实际可表示移动使可行集非前缀，不能直接套单调二分或只试上界。

详见审计`fractional-decision.md`：方案一保留名义计费，增加端口可表示成本/格点契约和有界候选求交（未知能力可能受限，需要H/I/P对齐）；方案二量化计费使搜索单调，但1份2^-30可能涨为1ULP即16倍名义费用，须用户决定。没有为了清零静默改变计费、从1扫描MAX、或删余额移动守卫。G3不在本批delivery.ids中；全局续做记录保留待决定，其余不依赖事项继续。

本批G1/G2的实际计费按总计划明确要求修复；G3另需兼容/计费决策，不混同为已完成。离线受控MagicPort/原生long存储不等于真实第三方网络或Rhino/服务器实测。99/5/18验收账本不改。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.69/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 570 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.69/modular-machinery-reborn-0.121.69.jar`，**1,660,653字节**。build/libs=reobfJar/output.jar=新归档SHA：

`A2CC3901683207AB15D2A189D4508EF318C72DEABA29098B442669CCF56776AE`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.69/README.md) · [版本记录](../../版本记录/更新记录-0.121.69.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 69 final <类名> 0 <模式>`；共同回归`regress 69 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
