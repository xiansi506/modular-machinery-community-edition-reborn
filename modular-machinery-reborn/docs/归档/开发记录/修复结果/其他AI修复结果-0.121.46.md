> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03通过[Codex独立离线验收](../验收记录/验收-第七批4项-2026-11-03.md)及隔离clean build/reobf内容复现。** 原交付状态保留为历史，实机/AE网/GPU未验，主任务保持暂停。

# 其他AI修复结果 0.121.46（2026-11-03）— 第七批 M 组

**状态：待 Codex 验收。** 本轮执行交接的 M1/M2，接在 L 组 0.121.45 之后累积交付；4 项已全部交付并停止。未部署、未启动游戏、未改整合包/存档、未暂存/提交/推送、未创建 Release、未改累计账本（仍 72 完成 / 5 部分 / 45 待处理）、未解除暂停。

起点是 **0.121.45 的真实 class 快照**（`_audit/seventh-ai-4tasks-0.121.45/production-classes-0.121.45`，568 class），不是 0.121.44。

## 结论

| 编号 | 结论 | 版本 |
|---|---|---|
| M1 / P02-01 ME 物品配置保留服务端光标栈的 NBT | **完成** | 0.121.46 |
| M2 / P10-21 long 能量单位显示与完整数字保持整数精度 | **完成** | 0.121.46 |

## 范围与改动

白名单内 3 份源码（另 1 份 `MachineHatchBlock` 只改调用点绑定，源码文本未改）：

1. `network/MeFluidConfigPacket.java` — ITEM 处理改为 `cursorCopyFor(...)`：核对服务端光标 id 后 `copyWithCount` 深拷贝，失败即拒绝；类注释同步。
2. `config/EnergyDisplay.java` — 新增整数除数，`display(long)` 走原样返回 / `Math.floorDiv`。
3. `config/DisplayNumbers.java` — 新增 `grouped(long)`；`grouped(double)` 保留。
4. `block/MachineHatchBlock.java`（未改文本）— 重新编译后 `grouped` 调用从 `(D)` 改绑到 `(J)`。

未改 wire/协议、ME 存储算法、AE2 总线实现、账本、单位配置、缩写算法、`DecimalFormat` 线程模型。

## 证据

工件：`_audit/seventh-ai-4tasks-0.121.46/`

- `M1/MeItemConfigCheck.java` + `M1/AuditSenderContext.java`（`net.minecraftforge.network` 包内，同步执行 enqueueWork 并返回受控 sender）、`M2/EnergyTextCheck.java`（`client.jei` 包内，可直接调 `MachineRecipeText.energyTip`）。
- `compiled-source/`：白名单 3 份源码对 0.121.45 class 快照编译出的修复类。
- `M1/logs/*`、`M2/logs/*`：before/fixed 运行日志与退出码；`artifact-validation.txt`；`source-hashes-after-0.121.46.json`；`production-classes-0.121.46/`（568 class）。
- L 批跨版本回归：`_audit/seventh-ai-4tasks-0.121.45/L1/logs/L1-after-0.121.46.log`（10 组 PASS）、`L2/logs/L2-after-0.121.46.log`（9 组 PASS），退出 0。

### 改前（0.121.45 快照，缺陷成功复现）

M1：配置格只剩 `{Damage:0}` 这类构造残留、药水变体与嵌套数据丢失；空光标仍按包里的 id/count 写入（3 → 7）；手持珍珠却发钻石剑 id 也照写；下游 `tickInput` 请求的是 tag-less 身份——tagged-only 网络取不到，plain-only 反而取到。

M2：`FE.display(2^53+1)=2^53`、`FE.display(10^18-1)=10^18`、`EU.display(10^18-1)` 高一位；`grouped` 只有 double 入口，`2^53+1` 与 `10^18-1` 丢位；真实 `MachineHatchScreen.tooltipLines()` 对容量 10^18 / 存量 10^18-1 显示 `1.0E / 1.0E`。

### 改后（0.121.46）

M1：自定义名 + 附魔 + 嵌套 NBT 逐字节保留且为深拷贝；光标数量/库存不变；空光标、伪造/未知/null id、槽 16 与 -1、空菜单、null sender、跨维度全部拒绝并保留旧配置；`Count` 夹到 1/16/堆叠上限；CLEAR/FLUID/GAS 与同格互斥、`minecraft:empty` 拒绝、无气体半边 no-op；滚轮在无手持物品时改数量；`AEItemKey` 请求键 = 带标签光标键 ≠ 白板键。

M2：四单位 × 16 值对照独立 `BigInteger` floor 全部精确（含 `Long.MIN_VALUE`）；`10^18-1` 留在 P、`10^18` 为 E；`grouped(long)` 12 值逐位一致、double 入口保留且被证明丢 `2^53+1`；`describe()`/`energyTip`/`PropertyBlockItem.appendHoverText`/`MachineHatchScreen.tooltipLines()` 全部走真实入口，界面文本由 `1.0E / 1.0E` 变为 `999.999P / 1.0E`。

## 构建与归档

- Java 17、离线、`--no-daemon`、`clean build` 1m，`BUILD SUCCESSFUL`。
- `build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.46.jar` = **1,634,763 字节**，SHA-256 `DD1465DA12E22EB249607141CD1D68C13C9ADB5BBDB7AF3D2817B273F6113B80`。
- 973 entry / 911 文件 / 568 class；215 份 JSON 全可解析；中英各 566 键；`mods.toml` 0.121.46；SRG 与协议 12 通过；未打包可选 API。
- 归档 `releases/modular-machinery-reborn/v0.121.46/` 逐字节一致；与 0.121.45 相比 7 个条目变化（6 class + toml），无增删类。
- wire 证据：`MeFluidConfigPacket.encode` 与 `<init>(FriendlyByteBuf)` 的 `javap -c` 与 0.121.45 完全相同，协议 12 未变。

## 未验事项

- 未实机、未部署；无真实网络线程/AE 网/区块事件/Rhino/GUI 字体绘制/GPU。
- `MeItemBusBlockEntity` 是真实类与真实方法，但构造器里的 AE2 受管节点未建立；MenuType 由夹具注入占位（工厂方法不调用）。
- 界面文本断言取自 `tooltipLines()` 组件参数与注入的测试语言表，不是真实 locale 文件或字体渲染；`ModConfig` 单位固定默认 FE，四单位算术在 `EnergyDisplay.display` 层验证。
- `test` = NO-SOURCE，行为证据来自上述夹具。
