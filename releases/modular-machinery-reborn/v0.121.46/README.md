# v0.121.46 — 第七批 M 组（P02-01 / P10-21，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.46.jar` **1,634,763 字节**，SHA-256 `DD1465DA12E22EB249607141CD1D68C13C9ADB5BBDB7AF3D2817B273F6113B80`。起点 **0.121.45**（L 组已修的真实 class 快照）。**标待 Codex 验收。**

## 本版内容

- **P02-01**：ME 物品配置的 ITEM 分支不再用注册名重建物品。服务端先核对包里的 id 与**服务端菜单光标**（`menu.getCarried()`）是同一物品，再 `copyWithCount` 深拷贝并夹到堆叠上限；光标为空、id 伪造/未知、菜单或维度不符、无 sender、槽位越界一律拒绝并保留旧配置。自定义名、附魔、药水与嵌套 NBT 因此完整保留，下游 `MeItemBusLogic`/`AEItemKey` 拿到正确身份。wire 仍只有 id/count。
- **P10-21**：`EnergyDisplay` 改整数运算——FE/RF 原样返回 `long`，EU 用 `Math.floorDiv(stored, 4)`（负数也向负无穷取整）；`DisplayNumbers` 新增 `grouped(long)`，能量仓容量/速率 tooltip 改走精确入口，旧的 `double` 入口保留。`2^53+1` 不再丢位，`10^18-1` 不再被舍入成 `10^18` 而跨到 E 缩写。

## 验证

- `MeItemConfigCheck`（P02-01）：0.121.45 快照下配置丢全部标签、空光标/伪造 id 照写、下游请求 tag-less 身份；修复后深拷贝保留 NBT、九类非法输入全部拒绝、数量夹限、CLEAR/FLUID/GAS 与互斥、滚轮免手持、`AEItemKey` 身份正确。
- `EnergyTextCheck`（P10-21）：四单位 × 16 值对照独立 `BigInteger` floor；`10^18` 边界 P/E 正确；`grouped(long)` 12 值逐位一致；`EnergyRequirement.describe()`、`MachineRecipeText.energyTip`、`PropertyBlockItem.appendHoverText`、`MachineHatchScreen.tooltipLines()` 全部通过，界面文本由 `1.0E / 1.0E` 变为 `999.999P / 1.0E`。
- L 批 `MultiRuleCheck`（10 组）与 `DynamicTailCheck`（9 组）以 0.121.46 类复跑通过。
- `MeFluidConfigPacket.encode`/decode 字节码与 0.121.45 相同，协议 12 保持；340 份源码累计改变 6 份，568 class 无增删。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（1m）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.46.jar` 均为 1,634,763 字节、同一 SHA-256；973 entry / 568 class；215 份 JSON、中英各 566 键、SRG 与协议检查通过。归档为逐字节复制，旧归档未改写。

## 边界

真实包处理、真实 `MeItemBusMenu`/`MeItemBusBlockEntity` 字段与方法、真实 `MeItemBusLogic`/`AEItemKey`、真实显示消费者与 shell level/player；AE2 节点未建立、无网络线程/真实 AE 网/字体绘制。未实机、未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.46.md)。
