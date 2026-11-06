# P08-a：AE2 供应器、镜像与统一总线

2026-10-31。基线 0.121.0 / 协议 12，HEAD `f94ba299`，本轮 `src/`、`gradle.properties`、`build.gradle` 全程零差异；未修复、未构建发布、未部署、未提交推送。夹具与日志在项目同级 `_audit/code-survey-0.121.0/P08-a/`。

新增 **P08-01（P2，流体标记第 9～15 格永不维护）、P08-02（P3，锁模式配方中止后供应器卡 busy）、P08-03（P3，PatternInputTransfer 为无调用方死代码）**。主进度 7 / 12（P07 离线收口、P08 进行中）。

## 范围与证据

本轮覆盖 `compat/ae2/` 全部 12 文件与配套的 `block/MeItemBusLogic`、`MeFluidBusLogic`、`MeFluidTanks`、`MeBusAccess`、`MeGasStorage`（接口）。为彻底绕开污染的 `build/classes`（外部 0.122 残留），本批把 **全部 339 个当前生产源**重新编译进 `P08-a/classes` 并把该目录置于 classpath 首位；夹具运行时断言 `MachineControllerBlockEntity` 的类来源即本目录（PASS provenance）。93 项 classpath 均校验存在；javac 以 `C:/mmwork` ASCII 别名路径避免码页乱码。

离线夹具 `P08aAuditCheck` 五组 PASS（exit=0）：真实运行 ProviderState 模式机、ProviderBuffer/ResourceTransaction 影子事务、PatternInputTransfer、MeItemBusLogic、MeFluidBusLogic。**未运行** AE2 网格/能耗服务、Mekanism/AppMek 注册表、方块实体生命周期、菜单与网络；AE2 契约用官方映射反编译 jar 的字节码核对（CraftingCpuLogic）。

## P08-01：流体标记只有前 9 格生效（P2，已原生复现）

`MeFluidBusLogic.tickInput`（MeFluidBusLogic.java:41）循环上界写 `entry < tanks.tankCount()`（=9，`MeFluidTanks.SLOTS`），而流体标记数组是 16 格（`MeItemBusBlockEntity.CONFIG_GRID`，`setConfigFluid` 允许 0..15，菜单 `MeConfigAmount` 正常可达）。夹具用真实 `MeFluidTanks` + 真实 `tickInput` 驱动：

1. 仅第 12 格标记水 500 mB → 网络零抽取，标记完全不维护；
2. 同流体第 3 格也标 500 → 目标按**全部 16 格求和**成 1000，经第 3 格入口补到 1000（第 12 格的量计入目标）；
3. 清掉第 3 格后 → 罐中 1000 mB 既不被拉取也不被送回（`isMarked` 扫全部 16 格，维护循环却扫不到）。

即配置格 9～15 的流体标记行为取决于同一流体是否也在 0～8 有标记：独立时死配置，共存时静默抬高目标。`failures` 数组长 9，作者已写 `entry < failures.length` 守卫，说明上界本意不是 9。修复方向：循环上界改 `marks.length`。旧版无此功能（0.94.0 新增），非移植回归。

## P08-02：配方中止不释放 CRAFTING_LOCK（P3，调用链确认）

`MePatternProviderBlockEntity.registerListeners/unlockByHost`（:114-123）只在 `RecipeFinishEvent` / `FactoryRecipeFinishEvent` 解锁；全项目仅两处 post（`MachineControllerBlockEntity.java:1558`、`FactoryEngine.java:539`），都在配方**正常完成**路径上。而控制器失型与红石停止都走 `abortCraft()`（:1457-1466），静默清 craft、不发事件。因此锁模式供应器在"结构中断/红石打断"后保持 `busy()`（AE2 视为不可派发），直到：玩家手动切换工作模式（`setMode` 换档清锁）、请求全量退回、或该控制器重新成型并跑完任一配方（`unlockByHost` 按 `usesInputProvider` 命中）。有手动与自动恢复路径，故 P3；建议 abort 路径补一个失效通知或把解锁条件扩到 craft 丢弃。锁的隔离语义本身成立：锁存期间第二块样板被拒（夹具 PASS）。

## P08-03：PatternInputTransfer 是死代码（P3，夹具佐证）

`PatternInputTransfer.push` 在生产代码中**没有任何调用方**：供应器 `pushPattern` 直接 `storage.accept(i, definition, inputs)`（KeyCounter 直入缓冲，不经 IItemHandler）；`AppMekGasBridge.register()`（ModCompat.java:171 启动时调用）把气体处理挂进它的静态 `additionalInput`，但 push 永不执行。夹具按其契约直调验证：整批成功才提交、部分失败零残留、流体+物品同事务、超 int 整批拒绝——当前实现是健全的。附带发现：`GuardedItems/GuardedFluid.transactionView` 返回 `tx.items(manualItems)` 通用包装，"只出不进"与 readable 门禁在事务视图下**不生效**（插入直达 2 格手动槽），本批仅死代码可达；若未来恢复"AE2→机器端口直推"路径需先收口该门禁。建议：要么删除死路径（连同气体注册），要么把供应器 `pushPattern` 改走它以获得逐端口语义，二选一并注明。

## 通过的对照与不新增编号的观察

- **AE2 推送契约闭环**：反编译 `CraftingCpuLogic` 字节码中 `extractPatternInputs` 与 `reinjectPatternInputs` 成对出现（offset 451/459），push 返回 false 时 AE2 退回输入；我方 accept 失败零副作用（`ProviderState.accept` 先查 busy 再整体入桶，`Math.addExact` 溢出拒绝）。无复制/丢失路径。
- 供应器缓冲事务：影子提交、修订号守卫、未解析键保留（unknown 行显示，不丢弃）全部 PASS；`accept` 在 ENHANCED_BLOCKING 下空缓冲可换样板、非空拒换（夹具逐条）。
- `MeItemBusLogic` 维护语义（拉到配置量/超出送回/错物先退再拉/网络满退避到 fullCheck）、输出部分接收守恒 7=3+4：PASS。
- 镜像 `MePatternMirrorBlockEntity.provider()` 全程 `hasChunkAt` 保护、维度校验、卸载标志；`inputIdentity()` 透传供应器身份避免同缓冲双计（与 b2 已验证的身份共享一致）。
- 类加载隔离：`Ae2ItemBusRegistry/Factory` 不在无 AE2 时解析 appeng 类型；`getUpdateTag` 移除 `ae_node` 防"节点已初始化"崩溃；掉线每次重取 grid（不缓存）；Mekanism 缺席时 gas NBT 存 `deferredGas` 保数据。
- 掉落：`MePatternProviderBlock.getDrops` 把 `dropContents()` 写进 `BlockEntityTag`，vanilla `BlockItem` 放置时回放——仓储与退回状态随物品保留，AE2 节点数据不进掉落。
- 观察不编号：① `load()` 读 `tag.getBoolean("lock_active")` 但 `saveContents` 从不写该键——仅 0.108.0 前旧档升级路径相关，本批未验证旧档格式，留待验；② ME 气体标记在"有 Mekanism 无 AppMek"时可设置但永不 tick（`serverTick` 要求 appmek 在场），纯 UX 边界；③ `summary()` 分页逐行序列化显示名 JSON（单行超 2048 字符已有降级）——P10 实机再评估。

## 交付与接续

`_audit/.../P08-a/` 保存：夹具、339 条源指纹、93 项 classpath、javac/运行参数、javac.log、javac-fixture.log、checks.log（5 组 PASS 原文）、validation.txt。错误与中间断言失败日志全部保留（第一版夹具对 PatternInputTransfer 用法的两次断言失败属夹具误用，已在报告与日志中如实记录）。

**P08-a 离线完成，实机待验。** 接续 **P08-b：Mekanism 气体链（HybridGasTank/MekanismAnchor/MeGasTanks/GasIo/GasRequirement/MekanismModule/MekanismProbe）与 AppMek 气体网络**。
