> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 MMCER第十批返工验收通过：当前及最近整版离线通过55/协议12，第十批四项指定范围收口。累计88项完成、5项部分、29项待处理。** 未实机/真实网络/GPU验收，未部署或推送；主任务暂停，下方旧状态为历史。 [55复验报告](../验收记录/验收-第十批R2返工-0.121.55-2026-11-04.md)。已结束的返工不重复执行。

> **2026-11-04 MMCER第十批验收：当前54/协议12，R1/S1/S2通过指定范围离线验收，R2创造模式遇未加载目标仍会无进展循环，需要返工。最近整版通过52；累计87项完成、5项部分、30项待处理。** 主任务暂停，未实机/部署/推送，下方旧状态为历史。 [验收](../验收记录/验收-第十批4项-2026-11-04.md) · [R2返工](../任务交接/返工交接-第十批R2-2026-11-04.md)。

# 其他AI修复结果 — 0.121.53（第十批R批，2026-11-04）

**结论：R1/P04-10 与 R2/P07-18 完成指定范围修复，标待 Codex 验收。** 起点 0.121.52 / 协议12 / 340 Java / 568 class，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`；串行执行，保留全部既有未提交改动。累计账本 **仍为 84 项完成 / 5 项部分 / 33 项待处理**，本批不销号、不解除主任务暂停。未实机、未部署/启动游戏、未改实例/存档、未暂存/提交/推送/创建 Release。S 批（0.121.54）以本版为基线另起。

## R1 / P04-10 — 意志 typed 参数与 JSON 归一化一致

| 项 | 结论 |
|---|---|
| 大写/混合大小写变体在 typed 入口提前失败 | **不可复现**（见下） |
| typed 边界显式归一化＋校验、同一规范值用于预校验与最终 JSON | **完成** |
| null / 空白 / 未知值受控拒绝，不 trim、不 fallback 到 default | **完成** |
| 保留内部构造器严格规范值契约；AURA 及其他家族不变 | **完成** |
| 真实 Rhino 脚本引擎验收 | **未做**（边界写明） |

**不可复现的事实：** 交接单沿用 P04-d 原报告（0.121.0）描述「大写变体先被构造器拒绝」。核对 0.121.52 源码 `MachineRecipeJS.magic()` 与 `javap -c` 的 52 编译产物，两者**都已有** `variant.toLowerCase(Locale.ROOT)` 且该规范值同时用于构造校验和 `will-type` JSON。最终版夹具的五种变体 × 小写/大写/混合大小写 × 三个 typed 方法 × raw JSON 对照，以及土耳其默认 Locale 用例，在**改前 52 类上即全部通过**。本批没有把这段既有行为当成新修复，也没有为制造失败而改写或弱化断言。

**实际修复：** `MachineRecipeJS` 新增私有 `willVariant(String)`：以 `Locale.ROOT` 归一化并显式校验五种合法变体，返回唯一规范值同时交给预校验构造器与生成 JSON。null 原先抛 `NullPointerException`，现在与空白、未知、带空白填充的值一样抛带 `will-type` 明确说明的 `IllegalArgumentException`；不做 `trim()`，不把非法值静默改成 `default`，不依赖进程默认语言。内部 `MagicRequirement` 构造器仍只接受规范小写；MANA/AURA/LIFEESSENCE/EMBER 与 `io-type` 处理未改，WILL 合法小数和其他资源的整数量约束未放宽。

**验证：** `WillTypedBoundaryCheck` 改前 1121 项中 3 项失败（三个 typed 方法传 null 得到 NPE），最终 Gradle 类 1121/1121 通过。覆盖真实 `MachineRecipeJsonBuilder.build` → Gson → `MachineRequirementParser.parse` → `copy` → `ModRecipeSerializers` 的 `FriendlyByteBuf` 往返，逐项核对 family、variant、方向、数量、min/max、perTick 与包尾零字节；`Locale.setDefault(tr-TR)` 后恢复进程 Locale。

## R2 / P07-18 — 旧搭建计划不能推进到已更换的目标

**完成（保守策略）。** 任务在启动时绑定控制器实例、解析出的机器目标（`previewMachineId` / `blueprintMachineId` / `machineId`）、注册表中解析出的**定义对象身份**与朝向（方块 FACING、rotationSteps）。这些是标量字段，不序列化结构块列表，也没有新增生产类文件。

- `requestAssembly` 把刚解析的控制器与定义传入 `MachineAssembly.start`；兼容的三参 `start` 仍可调用，自行从已加载区块解析控制器。
- `MachineAssembly.checkTarget` 先用 `isLoaded` 判断可用性：临时未加载 → 暂停（不取消、不做加载型实体查询）；加载后重新确认，不匹配即取消。
- `onPlayerTick` 与 `tickPlace` **各自**校验；直接调用 `tickPlace` 无法绕过。失效即置 cancelled、清空剩余步骤、移除 Manager 任务并发送 cancelled 消息：不扣后续材料、不报 success、不继续其他旧步骤。
- 创造模式逐格路径同样在每步校验；中途更换目标后停止剩余格并报 cancelled。
- 现有放置事件取消/回滚、每步材料消费、维度隔离、登出清理语义未改；未增加跨重启持久化，不做热迁移。

**使用说明：** 更换蓝图/机器 ID、替换控制器实体、注册表重载替换定义（即使 ID 相同）、改变朝向、或卸载后重建了控制器实体，都会使旧计划保守取消并保留未使用材料；**作者需要重新触发一次搭建任务**。任务只在内存排队，不跨重启续建，这是刻意的保守行为，不承诺自动续建。

**验证：** `AssemblyTargetCheck` 走真实 `requestAssembly → start → Manager → onPlayerTick`（含原生 `PlayerTickEvent`），改前 60 项中 31 项失败，最终 Gradle 类 60/60 通过。最低组合全部覆盖：A 排队换 B 无放置/扣料、同 ID 新实体、同实体改蓝图、同 ID 定义替换、朝向变化、未变化正常完成、未加载暂停且无实体查询、重载后重新确认、维度来回、登出、直接 `tickPlace`、创造模式中途更换、以及取消后不继续后续步骤；观察实际 Manager 移除、消息、库存与 `setBlock` sink。

## 回归

以最终 Gradle 类（非重编译替身）运行：Q1 三条 `IdentitySupplement` 3/3、`FactoryIdentityCorrectCheck`、`IdentityMatrixCheck` 428 条、`FinalTickCheck` 全部通过（`-Dstage=fixed` 恒传）；机器/工厂仅只读回归。R 批 typed/raw 魔法编解码与建造正常材料消耗/生命周期由上述两个夹具覆盖。没有把混有「旧缺陷应存在」断言的旧 main 当作正确行为回归。

## 构建与证据

- Java 17 的 `java.exe` 启动 Gradle wrapper，`clean build --offline --no-daemon`，`JAVA_HOME` 指向 JDK17、`GRADLE_USER_HOME=C:/mmwork/.gradle-home`；**常规 build 目录未被占用，本批无需隔离 buildDirectory**。最终一次 41s `BUILD SUCCESSFUL`，含 `> Task :clean` 与 `> Task :reobfJar`，`test NO-SOURCE` 如实记录。命令/JDK/原始日志/退出码在 `_audit/tenth-ai-4tasks-0.121.53/logs/`。
- 迭代如实记录：首个内部实现曾引入 `Target`/`TargetState` 两个内部类，使 class 数变为 570；为守住「340 Java / 568 class 无增删、不新建生产 helper」的边界，改为已有类的标量字段与 int 状态常量后重新完整构建，最终为 568。第一次构建也是成功的，产物已被最终构建替换。
- 证据目录 `_audit/tenth-ai-4tasks-0.121.53/`：`before/`（5 份白名单改前源码、改前 gradle.properties）、`diffs/`（3 份源码＋版本属性 unified diff）、`final-classes/`（完整 568 类快照）、`production-sha256.json`（340）、`accepted-class-hashes.json`（568）、`resources-sha256.json`（342）、`logs/`（改前失败日志、最终成功日志、命令与退出码）、`validation.txt`、`fixtures/`。
- 相对 52 基线仅 3 份白名单源码与 `gradle.properties` 改变；资源 342 文件哈希全等；类名集合与 52 完全一致。

## 产物与边界

- 正式 JAR **1,642,959 字节**，SHA-256 `C8A192DFE4B0E6876E7D31EDDD01F78D78ACADC5EF558A0BFE02A79CE76EFD07`；`build/libs`、`build/reobfJar/output.jar`、归档 `releases/modular-machinery-reborn/v0.121.53/` 三处逐字节一致。旧 52 及更早归档未覆盖。
- JAR 内 568 class；221 个 JSON/mcmeta 可解析；中英语言键各 566 且键集一致；`mods.toml` 为 0.121.53；协议 12 与 wire 格式未变。
- **未验边界：** 真实 Rhino/KubeJS 脚本引擎、完整 FML/Forge 事件总线与保护/放置取消事件、真实区块加载与磁盘、网络、GPU/实机画面、部署与实机。R1 的「大写提前失败」在 52 上不可复现，已如实列出；R2 的卸载后重建实体属于保守取消场景，不自动续建。夹具使用 Unsafe 分配的 ServerLevel/ServerPlayer 与内存状态 sink，不把 CPU 状态验证称为实机通过。
