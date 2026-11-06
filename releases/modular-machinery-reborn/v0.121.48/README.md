# v0.121.48 — 第八批 O 组（P10-04 / P06-17，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.48.jar` **1,637,987 字节**，SHA-256 `12D4A38DD95C37B52CB42227A7FB44C014D41C20A2E7C2AA5109FF73D150617F`。起点 **0.121.47**（第八批 N 组交付后的最终源码/class，累积构建）。**标待 Codex 验收。**

## 本版内容

- **P10-04（O1，工厂队列行的内容预算与裁剪）**：`FactoryControllerScreen.drawRecipeStatus` 旧实现把 `font.split` 的全部结果一股脑画出来——脚本状态可以有 20 行，长状态越过 86×32 的队列元素盖住后面的行，第六行还会越过面板底边（右侧信息块的 scissor 在另一个绘制阶段，保护不到这里）。现在每行都有**独立内容区**（元素矩形减 2 px 文字内缩）、**行数预算**（标签 12 px，工作且有进度再留一行，剩余才是状态行）、**省略**（状态超出预算以「…」收尾，标签过长同样截断，宽度由 `plainSubstrByWidth` 实测）与**裁剪兜底**（整行在元素矩形内 scissor，`pushPose`/`popPose` 与 `enableScissor`/`disableScissor` 各自在 `finally` 归还，异常也不漏）。被省略的完整状态由 `shortenedStatusAt` 在真实 `render` 命中入口提供悬浮 tooltip，滚动后命中的是当前可见行。线程标签、并行标签、`progress * 100 / duration` 的 long 乘法、分页/滚动与线程身份都不变。
- **P06-17（O2，普通控制器能耗快照用实时有效修改器）**：`MachineControllerBlockEntity.energySnapshot` 旧版把开工时冻结的 `activeCraftModifiers` 直接交给 `EnergyUsage.of`——服主在同一在途配方里改了能耗修改器后，真实扣费已按 `modifiersFor(recipe)` 变成 5→10→15，名义快照还停在开工时的 5。现在快照与在途结算走**同一条有效修改器链**：开工时冻结的结构／升级／催化剂贡献保留，脚本贡献换成**当前**脚本修改器。只换只读快照的取值来源：不重建执行配方、不重新扫描催化剂或仓口、不改冻结贡献与付款。

## 验证

- `QueueRowBefore`（O1 改前复现，只用旧 API）：0.121.47 类上 4 过 / 6 失败——20 行状态只画出 21 行中越界的部分、无省略号、行内容越出 86×32 元素、无行级 scissor（`scissors=0`）。修复后同一断言组 10 条全过。
- `QueueRowCheck`（O1 修复后断言，63 条）：走真实 `renderBg` → `drawRecipeStatus`，原生 `GuiGraphics` 捕获实现记录每次 `drawString`/`enableScissor`；行级裁剪用**原生 `ScissorStack`** 求交验证（含与外层裁剪的交集、空交集返回空矩形）；滚动走真实 `mouseScrolled`（负 delta 向下），tooltip 命中走真实 `render` 的命中入口。覆盖短/长状态、20 行脚本状态、并行标签、进度行、标签省略、滚动前后命中、scissor 生命周期（进出各一次、finally 归还、PoseStack 平衡）。
- `EnergyLiveCheck`（O2，26 条）：复用第五批 `ControllerRuntimeAuditCheck.Run`/`FactoryRuntimeAuditCheck` 的真实控制器/工厂 tick 环境，能量走真实 `LongEnergyStorage`。0.121.47 类上 3 条失败（脚本修改器加倍后快照仍报开工值、移除后不跟随、冻结催化剂 ×2 与实时脚本 ×3 叠加时只报 ×2），0.121.48 上 26 条全过。含 duration 修改器对照（默认 `enable-duration-multiplier=false` 不缩放宽 per-tick，快照与实付一致）、工厂线程链不受影响的对照、Jade 读取路径不改变执行状态的断言。
- 回归：`ControllerExtraCheck`（第七批，E3 菜单/D3 恢复计数）`extra controller failures=0`；`FinalTickCheck` 退出 0。
- 源码边界：340 份源码只有白名单 2 份（O 批）相对 0.121.47 改变；累计（相对第八批 0.121.46 基线）共 5 份（N 3 + O 2），`568→568` class 无增删。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（39s，`test NO-SOURCE`，8 任务全执行）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.48.jar` 逐字节一致；973 entry / 568 class / 221 JSON 全可解析；中英各 566 键且键集一致；`mods.toml` 0.121.48；出货 class 仍为 SRG 名（`m_237110_`），协议 12 未变。归档为逐字节复制，旧归档未改写。

## 边界

真实 `renderBg`/`drawRecipeStatus`/`mouseScrolled`/`render` 命中入口、真实 `FactoryThread`/`CoreThreadSpec`/`ActiveCraft` 数据、真实 `LongEnergyStorage` 与第五批控制器/工厂 tick 框架；字体为 6 px/字符的桩 `Font`（含 `width`/`split`/`plainSubstrByWidth` 一致模型），无 GPU 渲染；scissor 交并用原生 `ScissorStack` 反射驱动（与生产同一实例）。未实机、未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.48.md)。
