> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04验收收口：U2/P06-12在58通过指定范围离线验收，第十一批四项完成。当前/最近整版离线通过58，累计92/5/25。** [验收报告](../验收记录/验收-第十一批U2返工-0.121.58-2026-11-04.md)。本交接/待验状态为历史，不重复执行已完成事项；主任务暂停，未实机/部署/推送。

# MMCER 第十一批 U2 单项返工 — 0.121.58

**待Codex验收。U2/P06-12完成本次授权的离线修复范围；累计91/5/26不变，不自行销号，主任务保持暂停。最近已验收整版仍为56。** 未启动游戏、部署、修改实例/存档、暂存、提交、推送或创建Release。

## 起点与修改范围

用户明确授权执行[U2返工方案](../任务交接/返工交接-第十一批U2-2026-11-04.md)。真实起点57/协议12，340 Java、342资源、568 class；源码逐项核对57交付哈希，完整类逐项核对57 final-classes和当前build/classes。57归档1,647,443字节，SHA-256 `E648548C4242DFDEBB334BFFD8D656987C9F0E07D004DB9BCDFF98C6FC9D17D8`。开工核实58未占用，保留所有未提交工作，没有恢复到HEAD或旧版本。

证据根：`C:/mmwork/_audit/eleventh-u2-rework-0.121.58/`（对应工作区`_audit/eleventh-u2-rework-0.121.58/`）。保存完整改前源码/资源/568类、前后340份源码哈希、资源与类哈希、基线、Git状态及`production.diff`。相对57仅三个授权生产文件变化：

- `factory/FactoryEngine.java`：把现有`admits(thread)`作为纯守卫传入，普通/核心及排除当前槽的计数逻辑不变。
- `factory/FactoryThread.java`：在原`begin`路径传递守卫，拒绝后清除本次临时激活，不发成功通知，不调用清空旧批次资源的补偿逻辑。
- `recipe/MachineRecipe.java`：增加兼容`start(..., StartPlan, BooleanSupplier)`重载；原公共入口默认允许。先完成`canStart`、所有开工需求`apply`与所选催化剂`applyRequired`规划，然后检查守卫；拒绝不调用`transaction.commit()`。触发状态重置、`finalTickPaid`及`executionTick`重置仍只在成功提交后进行。

340 Java/342资源/568 class无增删；变化5个class均来自这三份源码（含内部类行号变化）。资源逐项哈希不变，协议12/wire、公共定义复制、ResourceTransaction/HatchCollection及需求实现未修改。版本按patch升58，必要中英文说明、版本记录、迁移日志及归档索引同步。

## 正确行为证据

### 原late：相同源码、相同断言，失败转绿

本批`BatchUCheck.java`与57工具逐字节一致，保留StackWalker要求真实`FactoryThread.begin`命中断言，未改名或跳过回调。

| 完整生产类 | checker真实命中 | 最终额度 | 煤 | workers | 真实退出码 |
|---|---:|---:|---:|---:|---:|
| 改前57 `before-classes` | 12 | 0 | 8→7 | 1 | 1 |
| 最终58 `final-classes` | 12 | 0 | 8→8 | 0 | 0 |

见`before-late.log`、`final-late.log`及对应`*-command.json`/`*-exit.txt`。本批`run-u58.py`会汇总非零退出为失败，不再用总进程0掩盖late失败。

### 新增真实规划/付款边界矩阵

`U2BoundaryCheck.java`：10组、172项断言通过。StackWalker只在真实`MachineRecipe.start`及`ItemRequirement.apply`且位于`FactoryThread.begin`内降额，不依赖第几次回调，也不只测试BooleanSupplier。

- checker与itemModifier两类，各覆盖有/无输出、固定/随并行输入；一批实际并行3，普通额度1，证明两本账不混用。
- 两个开工需求，第二个回调降额；一个可选催化剂、一个延迟输入（trigger-time=2）、逐tick能源并存。真实原生库存的受控子类记录写入：拒绝时0次真实提取，4槽均保持8、能源保持100、成功通知0。记录催化剂`applyRequired`规划真实到达，拒绝不付催化剂。
- 恢复额度后原身份旧槽开工成功，按固定标记准确付1或3份输入、催化剂1份、延迟料开工时不扣。完成已付批次后，`tryRestart`再次真实命中晚回调并降额，禁止下一批付款与重复成功通知。
- 已付批次4tick精确支付36FE，延迟料只扣3份，有输出时产出3份；先前拒绝不污染恢复后的fresh execution。
- 核心在普通额度0时仍可真实开工；已成功开工事件再降额的另例，已付批次完成一次，下一批拒绝。
- 对这些受控原生端口，记录的item checker/modifier调用没有处于`ResourceTransaction.commit`栈内；结论仅限本次已测脚本规划链，不声称任意第三方能力commit无回调。

最终同一矩阵在57完整类仍退出1，58退出0。初版夹具使用旧helper（配方最大并行固定1）却期望3，曾在58的“parallel independent of quota”处失败；这是夹具配置错误，已改为显式最大并行3，不改生产代码绕过。初次该夹具最终失败日志被复跑覆盖，shell输出仍可追溯；原57 late失败证据从未覆盖，后续runner对旧日志使用previous编号保留。见`fixture-iteration-notes.txt`。

### 既有专项/回归

修改前已完成原U8模式（7通过、late正确断言失败）与20套回归（全通过），再动生产源码。

最终58完整正式类：

- U8模式全通过：structure174、ports32、quota10、callbacks2、late2、combinations5、extraQuota5、coupled16。包括普通上限0/1/N、忙/空混合、2→0/1、恢复、直接search、核心独立、NBT保存/恢复、输出堵塞排空、同ID既有去重、重载降低基础额度、静态替代/四向/耦合候选/真实端口及动态多块组合。
- 20套回归全通过：AdapterBatchTCheck14组、AdapterPrivateStackCheck12、ItemCopySafetyCheck **fixed**、InterfaceCopyCorrectCheck；AssemblyTargetCheck60、CreativePauseCheck、CreativeResumeCheck23、CreativeMatrixCheck14；WillTypedBoundaryCheck1121、LpOverflowCheck41、BloomCleanupCheck120；IdentitySupplement3、FactoryIdentityCorrectCheck原身份、IdentityMatrixCheck428、FinalTickCheck、ControllerExtraCheck；ModifierRotationCheck8组、DynamicExpandCheck9组、MultiRuleCheck **fixed**、DynamicTailCheck **fixed**。

`final-reg-*-exit.txt`均0；回归classpath剔除旧生产目录，首项为58完整`final-classes`，保留class-load原始日志。边界矩阵亦有最终FactoryEngine/MachineRecipe类加载来源。没有把增量预编译类当正式类。

## 构建及归档

JDK Microsoft 17.0.11，`JAVA_HOME=C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`；`GRADLE_USER_HOME=C:/mmwork/.gradle-home`；cwd `C:/mmwork/modular-machinery-reborn`：

```text
"C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot/bin/java.exe" -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf成功，真实退出0，无隔离fallback；`test NO-SOURCE`如实记录。保留`java17-version.log`、`build-normal-command.json`（含环境/cwd）、`build-normal.log`原始字节、`build-normal-exit.txt`与完整final-classes。

新正式包：`releases/modular-machinery-reborn/v0.121.58/modular-machinery-reborn-0.121.58.jar`，**1,647,725字节**。

SHA-256：`08100D86486B669AAA863A220B1FBFA8CC55820909CEE6B1BED298071803315B`

build/libs、reobfJar/output.jar与新归档逐字节一致，mods.toml为58。221个JSON/mcmeta可解析，中英各566语言键且集合一致。56/57旧包保留且未覆盖。见`delivery-verification.json`、归档README/SHA256.txt。

复跑入口（Python `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，使用`-I`）：

```text
python.exe -I C:/mmwork/_audit/eleventh-u2-rework-0.121.58/run-u58.py final
python.exe -I C:/mmwork/_audit/eleventh-u2-rework-0.121.58/run-boundary.py final
python.exe -I C:/mmwork/_audit/eleventh-u2-rework-0.121.58/regress-u58.py final
```

## 未做、超范围及未验边界

- 原late可复现，未列为“不可复现”；没有扩做T1/T2/U1已验收修复，没有新增适配器/魔法/能源集成。
- P06-08核心排空读档、P06-11额外IO、P05通用事务、P04-13数值烘入均未做。未重写调度、并行账本或身份分配。
- 旧版FactoryController/FactoryRecipeThread运行期缩容缺口只按原交接只读对照，不声称旧版游戏验证。本版仍遵守已付排空/新批准入契约。
- 守卫仅在当前已知需求/催化剂脚本规划链之后。commit期间外部能力任意改变额度/其他状态、任意回调重入及并发全局原子性不是本次承诺，未修改通用事务框架。
- 测试为真实生产Java调用链、ScriptHooks直接Java回调、壳Level与受控端口/原生资源存储，不等于真实Rhino、完整Forge启动/服务端权限/能力网络、真实磁盘区块、GPU或游戏验收。离线FMLPaths/终端警告保留，未声称已启动游戏。

本项交付后停止，待Codex验收，不解除暂停或修改91/5/26累计账本。
