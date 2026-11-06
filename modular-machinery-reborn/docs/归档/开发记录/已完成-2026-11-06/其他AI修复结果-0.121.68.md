> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.68

**待Codex验收。本批编号：P05-03；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## F / P05-03：输出需求整组概率完成指定范围，待Codex验收

仅ItemRequirement输出分支修改：每条需求先判一次chance门，命中后才逐份抽取范围数量；不同输出需求仍各判一次，不把全配方共用一次概率。概率1不额外消耗随机数，概率0/中间按一门处理，机会修饰符及fixed flag走原解析/执行路径。保守空间预留与事务提交未改。

改前67 OutputGroupChanceCheck direct同一正确断言失败；最终direct5400项通过：100独立种子×P1/2/5×机会0/1/0.5×固定/范围数量，以独立参考“先整组门控，再逐份范围抽样”逐项比较实际产量及nextFloat次数，不以平均产量相同判通过。

真实普通/工厂 controllers60项：两条输出分别门控、P2及fixed、一条范围输出、满输出多tick等待时0次机会抽取，释放后正确产出且不重付煤。triggers16项：延迟/重复触发、机会修饰×2及固定/随并行、结束阶段不重放已触发结果。既有FinalTickCheck和30个共同回归保持，前批V/W/X/Y/A多核心保持。

不新增输出需求或随机策略。测试的输出拒绝路径是生产保守预检/满仓等待，确认没有机会抽取和重付；未知外部能力在commit过程中拒绝/抛错不属于本批新增的缓存/回滚承诺，仍按I/H通用事务边界处理，不将它声称已全局原子解决。初版夹具误写事件名resultChanceCreate，已改成现有resultChance合法事件；装置失败保留attempt，不改生产事件接口。

对照原P05-b中旧版chance.canWork整组门控契约，本版恢复该既有计费/概率含义；逐份范围数值仍保持原规则。未运行旧游戏或Rhino，原生随机源/事件及库存/世界壳不等于实机。99/5/18账本保持。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.68/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 570 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.68/modular-machinery-reborn-0.121.68.jar`，**1,660,490字节**。build/libs=reobfJar/output.jar=新归档SHA：

`FD4A3A182EAAF3BA2322B7C53D0999E93BE81A7DA4B936C068ACA848E751545A`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.68/README.md) · [版本记录](../../版本记录/更新记录-0.121.68.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 68 final <类名> 0 <模式>`；共同回归`regress 68 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
