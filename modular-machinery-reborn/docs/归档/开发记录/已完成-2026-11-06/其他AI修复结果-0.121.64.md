> **2026-11-05 Codex验收：指定离线修复范围通过；真实游戏/完整网络仍未验。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.64

**待Codex验收。本批编号：P06-11；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## B / P06-11：指定离线范围完成，待Codex验收

起点63。普通控制器和FactoryEngine在重算当前有效工期、重申冻结并行之后，若已付进度已经达到/超过当前目标，就进入已有持久化finish-only阶段，不再经过普通逐tick扣款/处理命令/START-END tick事件。未到目标仍按原流程支付真正最后tick；不退款已有付款、不把新开工变免费。FactoryEngine保留A批核心快照遍历和58提交前守卫。

该阶段仍调用MachineRecipe既有canFinish/finish：缩期后尚未发生的延迟需求按原finish补齐义务，仅未triggered者在同一事务实际支付一次；repeatable标记不产生一个不存在的额外execution tick。满输出或缺延迟料时继续等待、不扣普通tick资源；保存恢复保留阶段。如果尚未进入finish-only前延长工期则按新目标继续真实tick；一旦已进入结束等待，后续延长不重开已完成执行阶段，与既有末tick已付等待契约一致。

正确最小断言：原工期6、4个真实tick已付20FE后缩到3、能源置0，63仍不能结束而失败；64不需第五tick，准确产生1石头、完成一次。`DurationReachedCheck minimal`2条、`matrix`72组360条、`boundaries`54条通过：普通/普通工厂/核心，目标小于/等于/大于当前进度及延长；0/1/5延迟触发及重复标志；无能量、堵塞释放、未付延迟实际扣料、红石暂停/恢复、尚未完成tick START hold、RESET/STILL/DECREASE、保存恢复及结束后延长。独立预期FE以真正执行tick数量计算，END事件数量与付费tick对应，不拿成功返回冒充支付。

共同30调用与前批V/W/X/Y、多核心40组623项全部保持。常规最终tick/输出重试、已付延迟、核心恢复及身份不回归。无需修改MachineRecipe/trigger契约或公共付款框架，scope仅两个生产文件。直接Java回调/内存Level不等于Rhino/完整服务器实测。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.64/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。341 Java / 342资源 / 569 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.64/modular-machinery-reborn-0.121.64.jar`，**1,652,722字节**。build/libs=reobfJar/output.jar=新归档SHA：

`FC96C3E78F49DA3EDAA876DB3E1B5DCB9AF4FAEDF0B9ABB6A1ADE14B7A962137`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.64/README.md) · [版本记录](../../版本记录/更新记录-0.121.64.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 64 final <类名> 0 <模式>`；共同回归`regress 64 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
