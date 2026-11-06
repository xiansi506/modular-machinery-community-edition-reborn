> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex独立验收：U1通过指定范围离线验收；U2常规路径改善但晚需求回调后仍扣料开工，未收口，57不能标整版通过。** 当前57/协议12，累计91/5/26；[验收](../验收记录/验收-第十一批4项-2026-11-04.md) · [仅U2返工](../任务交接/返工交接-第十一批U2-2026-11-04.md)。原交付记录保留供追溯；未实机/部署/推送，主任务暂停。

# MMCER第十一批U修复结果 — 0.121.57

**待Codex验收。U1/P07-02完成指定离线范围；U2/P06-12部分完成，晚需求回调最后准入仍有真实失败。累计88项完成、5项部分、29项待处理不改，主任务保持暂停。**

## 起点与生产范围

真实起点T56完整交付，HEAD f94ba299e37f0975ac5255f90671f990bacfc61a。340 Java、342资源、568 class逐项与T56最终哈希一致；56三处JAR为1646317字节，SHA-256 `9918538E9D5377A9494E635E78F926AB5B53AB385477530C8EA85D1824AA7F95`。独立U目录保存完整before源码、before-classes、哈希、Git状态；未按HEAD恢复任何既有改动。

相对56仅修改白名单machine/MachinePattern.java、machine/MachineRegistry.java、factory/FactoryEngine.java三份生产源码。340 Java/568 class无增删，7个变化class均属于这三份源码（含内部类行号变化）；342资源逐项哈希不变。T三份适配器修复保留，协议12、wire、公共复制、通用事务、旋转和动态测量未改。

## U1/P07-02：完成指定离线范围

MachinePattern.withModifierAlternatives仅在已有基础坐标追加单块修改器候选，保留基础候选及完整基础rules/tags；坐标/候选复用rotatedOffset/acceptedAt全部旋转结果，不修改缓存基础列表。MachineRegistry首次基座匹配、动态段合并后的最终匹配及Match.pattern均使用同一有效基座。结构外可选效果探针不变成必需格；缺其他基础部件仍拒绝。

正确行为夹具BatchUCheck：Registry/规则174项，真实控制器端口收集和支付32项，耦合候选16项，静态替代+动态max-tail+多块NBT/checker组合5项通过。真实控制器tick从Match.pattern经原ControllerPortCollector.collect收集：MachineHatchBlockEntity受控子类位于世界实体表，kind/原生ItemStackHandler/LongEnergyStorage由夹具提供，未反射注入be.hatches；核对收集后处理器对象身份、selector标签、煤输入和石头产出，四朝向基础256FE/t、单个能源输入×0.5替代128FE/t均精确支付。没有把此受控世界/仓口子类描述为完整Forge仓口注册或真实能力网络实测。

覆盖基础石头、金块/钻石多个替代候选、无关块拒绝、重复匹配、四朝向、非笛卡尔耦合状态16态、基础NBT/checker通过与拒绝、有效结构rules/tags、缓存基础候选不变、未加载零状态读取、外部可选探针、缺其他基础格。组合验证最终effective.matches及原动态selector，缺动态尾拒绝；多块checker失败只去掉效果。

旧版只读对照BlockArray.matches:305-333的同坐标replacement后备、TileMultiblockMachineController.matchesRotation:418-438的旋转replacement。未运行旧游戏；本版遵守交接新增限定：替代只放宽方块候选，不能绕过基础NBT/checker。默认材料预览仍可显示基础材料；未宣称P07-16 tier映射/完整替代材料UI已完成。

## U2/P06-12：部分完成，保留失败交回

FactoryEngine普通准入统一为当前普通在途数小于host.maxThreads，排除正在完成并尝试重开的当前槽；核心独立。canAccept、nextFreeThread、begin、tryRestart共用准入，空槽按稳定ordinal升序选择，创建仍保留既有身份高水位与槽总量限制。已付任务允许排空，不取消、不退款；排空期间可暂时超过新额度。普通并发与并行份额账本分开。

真实控制器/工厂tick与库存产出验证2→0、2→1、恢复、保留旧槽、直接searchAndStart、核心额度0工作/重开、低额度实际NBT save/load、堵输出后排空、同ID既有去重、重载降低基础额度。quota10项、extraQuota5项、RecipeCheck START/END回调降额2项通过；改前相同正确断言失败，修后未发生额外开工扣料。Q1原身份套件、3条补充、428矩阵全部保持。

**仍失败的最后准入边界**：BatchUCheck late注册真实ScriptHooks.itemChecker；用StackWalker只在生产FactoryThread.begin内部再次需求检查/支付链命中时降额，之前预检保持额度1。同一正确断言在56、修后增量类及57正式完整类均失败：`late calls=12 quota=0 input=7 workers=1`，初始煤8，仍扣1并开新批次。`final-late-exit.txt`为1，原始AssertionError保留；测试工具的总进程退出不代表此正确行为通过。

FactoryEngine.begin检查之后，FactoryThread.begin内部再次planStart/canStart/canTick，MachineRecipe.start又canStart并调用需求apply，ItemRequirement.resolve的checker/modifier可再次写控制器。现有调用参数没有支付前守卫入口；MachineRecipe与HatchCollection均final，不能用安全的引擎局部子类覆盖；在引擎外层再做有限次数预检也不能保证内部回调不变额。未绕过回调、未用支付后清槽伪装拒绝、未改通用事务或越白名单。交回建议为单独授权最小的需求规划后、支付前准入钩子及对应FactoryThread/MachineRecipe调用边界，仍需进一步设计与验证；本批不实施，不声称所有回调下严格准入完成。

旧版TileFactoryController.offerRecipe/hasIdleThread及FactoryRecipeThread.tryRestartRecipe同样缺运行期缩容约束，只作源码对照，不据此免除本版契约。P06-08待排空核心读档、P06-11额外IO、P05补偿未扩做。

## 构建与最终回归

Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf成功，退出0；test NO-SOURCE。原始build-normal.log、命令JSON、Java版本日志及完整final-classes保存，无隔离fallback。原始Gradle中文警告部分为系统编码；日志保留原始字节，解码检查不重写日志。

改前56及最终57完整正式类各跑20套回归，全部退出0：T AdapterBatchTCheck14组、AdapterPrivateStackCheck12项、ItemCopySafetyCheck fixed、InterfaceCopyCorrectCheck、AssemblyTargetCheck60、CreativePauseCheck、CreativeResumeCheck23、CreativeMatrixCheck14、WillTypedBoundaryCheck1121、LpOverflowCheck41、BloomCleanupCheck120、Q1原身份、IdentitySupplement3、IdentityMatrixCheck428、FinalTickCheck、ControllerExtraCheck，以及实核review-seventh目录的J1 ModifierRotationCheck8组、J2 DynamicExpandCheck9组、L1 MultiRuleCheck fixed、L2 DynamicTailCheck fixed。最终回归classpath首项为本批完整final-classes，删旧compiled/生产目录；class-load日志可核实生产来源。

U专项最终7组正确行为通过、late独立失败；早期夹具后续分支曾有NBT标量缺引号、耦合候选字符串替换未命中，已修为合法输入/结构化JSON并复测，不将夹具错误作为生产缺陷。两并行测试进程曾争用本批logs/latest.log，原始控制台日志与独立退出码完整保留，不涉及生产文件或构建失败。

## 归档与复跑

证据根：`C:/Users/kk07H/Desktop/模组/_audit/eleventh-ai-4tasks-0.121.57/`。含before/final完整类、源码/资源/类哈希、production.diff、每条命令/日志/退出码、delivery-verification.json与夹具源码。Python入口 `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，带`-I`：

```text
python.exe -I C:/mmwork/_audit/eleventh-ai-4tasks-0.121.57/run-u57.py final
python.exe -I C:/mmwork/_audit/eleventh-ai-4tasks-0.121.57/regress-u57.py final
```

前者逐项保留late失败，不会将它改成“缺陷存在即成功”；务必查看final-late-exit.txt。后者对任何回归非零立即退出失败。

正式包 `releases/modular-machinery-reborn/v0.121.57/modular-machinery-reborn-0.121.57.jar`，**1,647,443字节**，SHA-256：

`E648548C4242DFDEBB334BFFD8D656987C9F0E07D004DB9BCDFF98C6FC9D17D8`

build/libs、reobfJar/output.jar、新归档逐字节一致，mods.toml为57；221个JSON/mcmeta解析通过，中英各566键且集合一致。56旧包保留。离线Level、Java回调、受控仓口和原生资源存储不等于完整Forge/Rhino/实际区块/真实网络/GPU/磁盘游戏验收。

工作区/项目README、英文状态、使用说明、已知限制、迁移日志、57版本记录和releases索引同步。没有动累计账本、解除暂停、启动游戏、部署、实例/存档、暂存、提交、推送或Release。第十一批工作到此停止，待Codex验收与晚回调边界后续授权。
