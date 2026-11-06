# v0.121.43 — 第六批 J 组（P07-03 / P07-04，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.43.jar` **1,633,780 字节**，SHA-256 `B838E25AA4F4E9A60DE6FED52BCF928BA9D70E05DC0A6AB5473286D81DB22062`。真实起点 **0.121.42**（340 份源码哈希逐份一致，mismatch 0）。**标待 Codex 验收。**

## 本版内容

- **J1 / P07-03**：单块修改器的 `accepted` 候选改用 `BlockMatcher.rotateYCounterClockwise()` 随控制器旋转；`MachineModifier.acceptedAt` 展开全部候选并按描述符去重，`evaluateModifiers` 不再只转 offset。四朝向正确/错误状态的实际 5/10 FE 结算、非轴心偏移与高度、无方向方块、多候选、耦合属性真值表、同位置双修改器、未加载位置、真实工厂 `FactoryEngine.tick` 均覆盖。
- **J2 / P07-04**：`DynamicPattern.expand` 复用构造时缓存的 `partRotations/endPartRotations`，`addSection` 只做“已旋转段内坐标 + 段起点”，不再二次旋转坐标或状态。四向显式表、方向性尾段、不对称单元/高度、部分属性/多候选、耦合属性、0 节、节数边界、NBT/checker、双动态段标签、`runtimeStructure` 与 `expand` 一致均覆盖。

## 验证

- `ModifierRotationCheck`：0.121.42 快照 **25 项失败** → prebuild/最终 class **8 组全过**。
- `DynamicExpandCheck`：0.121.42 快照 **38 项失败** → prebuild/最终 class **9 组全过**。
- 同批 `FactoryRuntimeAuditCheck` 在 0.121.42/0.121.43 同一 P06-05 处失败，属既有夹具差异，非本批回归。
- 340 份源码仅 3 份改变；568 class 变化 3 主类 + 2 个 `DynamicPattern` 嵌套记录。Gradle test=NO-SOURCE。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（45s）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.43.jar` 均为 1,633,780 字节、同一 SHA-256；归档为该产物逐字节复制。旧 0.121.42 JAR 未改写。

## 边界

离线原生调用（内存/壳 Level、真实控制器 tick/工厂引擎、注入端口存储），无真实 Forge 游戏、物理区块、仓口能力收集、网络、OpenGL 或外部第三方方块；实机未验。未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.43.md)。
