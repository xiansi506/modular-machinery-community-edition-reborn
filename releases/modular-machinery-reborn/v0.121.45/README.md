# v0.121.45 — 第七批 L 组（P07-05 / P07-06，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.45.jar` **1,634,497 字节**，SHA-256 `DC33A49541F272AFF4B7DA68F7E02A9122D2DFD81FCD2F0D194BA0DB99F225C3`。真实起点 **0.121.44**（源码/class 快照取自改前实态）。**标待 Codex 验收。**

## 本版内容

- **P07-05**：多块条件（`multi-block-modifiers`）的激活判断改走带世界上下文的完整匹配。此前控制器只用「区块加载 + 方块状态」的谓词，条件里的 `nbt` 与 `script-checker` 整条不执行——条件不满足也贡献 modifier，1 煤→1 石头只扣 5FE 而非基础 10FE。现在 `MachineMultiBlockModifier` 新增 `matches(Level, BlockPos, int)` / `active(List, Level, BlockPos, int)`，复用 `MachinePattern.matches(Level, …)` 的加载、NBT、checker 与安全语义；原谓词重载保留并标 `@Deprecated`（只答方块状态，不声称验证 NBT）。
- **P07-06**：`DynamicPattern.matchesSize` 数满 `maxSize` 时先辨认锚点上的合法收尾段。最小反例 `min1/max1`、重复单元任意熔炉、尾段熔炉要求 NBT `Count=1b`：`expand(1,0)` 本来就匹配，`match`/`Registry` 却拒绝。现在该锚点命中尾即按实际重复数成立，`maxSize+1` 且无合法尾仍拒绝。

## 验证

- `MultiRuleCheck`（P07-05）：0.121.44 快照 8 组缺陷复现（checker 调用数 0、全部 5FE）；修复类上未满足条件 → 10FE、满足 → 5FE，checker 恰 1 次并收到坐标/状态/NBT，四朝向+非轴心偏移、未加载不读状态/实体、多条件叠乘 2FE、工厂入口 10/5FE 全部通过。
- `DynamicTailCheck`（P07-06）：独立 `partAt/endPartAt` 真值表 + 真实 schema；改前最大锚点尾段全部 `-1`，改后按真实节数成立，超限/缺尾/无尾仍拒绝，min0/max0、0 节尾锚、四朝向 expand/Registry/控制器、相邻动态段与未加载短路保留。
- 第六批 J1/J2 套件复跑通过（J1 8 组 + 16 断言、J2 9 组）；未重做 expand 旋转修正。
- 340 份源码仅 3 份改变；568 class 无增删。Gradle `test` = NO-SOURCE。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（42s）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.45.jar` 均为 1,634,497 字节、同一 SHA-256；973 entry / 568 class；215 份 JSON 可解析，中英各 566 键一致；SRG 与协议 12 检查通过。归档为该产物逐字节复制，旧 0.121.44 JAR 未改写。

## 边界

真实 schema/registry/控制器 tick/配方结算/FactoryEngine 与真实熔炉 NBT、Java checker 回调、shell Level；端口为匹配后注入的真实存储对象，不是物理仓口能力收集。无真实区块事件、Rhino、网络、GUI/GPU 或实机验收。未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.45.md)。
