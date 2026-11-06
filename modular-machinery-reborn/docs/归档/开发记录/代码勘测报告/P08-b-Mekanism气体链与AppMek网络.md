# P08-b：Mekanism 气体链与 AppMek 网络

2026-10-31。基线 0.121.0 / 协议 12，HEAD `f94ba299`，`src/` 零差异；只勘测，未修复/构建/部署/提交。夹具在 `_audit/code-survey-0.121.0/P08-b/`，生产类复用 P08-a 的全量重编译目录（运行时 provenance 断言 PASS）。

**新增编号：0 条。** 本批为覆盖收口与行为确认批；两条数值类维护线索已由 P05-c2 用原生执行登记，本批不重复编号。

## 范围与证据

覆盖 `compat/mekanism/` 全部 8 文件（HybridGasTank、MekanismAnchor、MekanismModule、GasRequirement、GasIo、MeGasTanks、MekanismProbe、MekanismEditorIngredient）与 `AppMekGasBridge` 的气体网络半边（AE2 侧已在 P08-a）。GasRequirement/GasIo/HybridGasTank 的事务与结算语义 P05-c2 已用真实 Mekanism 注册表原生执行（含溢出不对称、重复端口 simulate 分歧两条登记），本批不重跑。

本批夹具 `P08bAuditCheck` 六组 PASS（exit=0）：隔离 Gas 注册表（P05-c2 同款引导）+ 真实 `MeGasTanks`/`MekanismAnchor`/`MekanismModule`/`ModCompat`。

## 原生确认的行为（夹具）

1. **总线气体标记全 16 格维护**（对照 P08-01）：`MeGasTanks.tick` 循环上界是 `CONFIG_SLOTS`，第 12 格标记正常拉取；同气体多格求和为目标；未标记气体先退回；网络离线（insert/extract 全 0）时**每个 mB 都保留**。
2. **总线气体事务**：`transactionView` 快照-影子-提交，外部变更后旧事务提交被拒且无残留。
3. **总线气体存读档**：内容与标记往返；超量罐读盘按罐容量截断；未注册气体的**标记**在 load 时静默丢弃（内容仍保留）。
4. **MekanismAnchor 生命周期**：`tankOf` 每实体身份稳定（WeakHashMap 值随键存活，气体不会因 GC 丢失）；`savePorts/loadPorts` 走通用映射器（未建过气体半仓的仓不写盘）；物品仓无气体侧、非 gas 家族不给端口。
5. **网络往返**：`GasHandler.write` 的 ioType utf 是 Parser 帧的一部分（Parser.read 统一读 ioType），走 `MachineRequirementParser.copy`（与线上一致的帧）往返 ioType/量/概率/气体/selector-tag 全部保留；`gas_pertick` 用独立注册 id。夹具第一版直接调 `type.write/readBody` 绕过 Parser 层导致帧错位的失败日志保留，属夹具误用，非生产缺陷。

## 观察（不编号）

- **未知气体标记静默丢弃**：`MeGasTanks.load` 里 `setMark` 对解析不到的气体返回 false，标记不保留；对照 `MeItemBusBlockEntity` 在 Mekanism 缺席时把 `meGas` NBT 存进 `deferredGas` 完整保留。气体附加包移除再装回后，总线气体标记需重配（内容不丢）。属策略不一致，建议与 deferredGas 同策略。
- **超量读盘截断策略与流体侧相反**：气体侧 `HybridGasTank.readFromNbt`/`MeGasTanks.load` 都 `Math.min(saved, capacity)` 静默截断；流体侧 P07-13 是保留超量 + 负数灌入。P07-13 的修复方向明确说"不能仅以无声截断消除超量"——修 P07-13 时应把气体侧一并纳入同一策略决定。
- `GasIo.extract` 无 simulate/execute 分歧防护（`insert` 用 throw 防"半灌"，`extract` 分歧时静默 false 且已部分抽出）；同 tick 一致前提下不可达，且 P05-c2 已登记同族辅助契约线索。
- `AppMekGasBridge.network` 在节点离线时返回 0，`MeGasTanks.tick` 的行为是保留内容等重连（夹具验证）；`MekanismCapabilities.gasHandler()` 捕获 Throwable 返回 null，不把 NoClassDefFoundError 逃进能力查询。
- 每个流体仓的插入尝试统计（`InsertTally`）是每罐 synchronized 计数器，为 `/mm-diagnose` 服务；管道高频插入路径上有锁开销，无测量数据，只作 P10/性能候选线索。

## 边界

未运行：真实 Mekanism 自身 bootstrap（用隔离注册表替代，`requireGas` 的 JSON 查找按设计只在游戏内可达）、ME/AppMek 网格、真实管道/漏斗、区块卸载与 WeakHashMap 回收时机、多人同步。这些保持实机待验。

**P08-b 离线完成，实机待验。P08 主部分（a+b）离线勘测完成**；P08-01/02/03 与 P05-c2 两条线索待修复阶段处理。接续 **P09：魔法与可选能源后端（magic/、compat/magic/、compat/energy/、Jade 资源信息）**。
