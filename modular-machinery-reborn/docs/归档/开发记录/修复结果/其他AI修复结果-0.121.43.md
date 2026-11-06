> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **Codex独立离线验收通过，2026-11-03。** [验收报告](../验收记录/验收-第六批4项-2026-11-03.md)。下文原交付状态为历史；44报告裁剪语义已经勘误。实机/GPU未验，主任务暂停。

# 其他AI修复结果：第六批 J 组（0.121.43，2026-11-03）

**状态：待 Codex 验收。** 本批只处理交接指定的 J1/P07-03 与 J2/P07-04；K 组在 0.121.44 单独交付。未部署、未启动游戏、未暂存/提交/推送、未改累计修复账本、未解除主任务暂停。

## 起点与构建

- 真实起点 **0.121.42 / 协议 12**，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`，340 份生产源码、568 份 class。执行前用交接基线 `_audit/sixth-ai-handoff-4tasks-2026-11-03/production-sha256.json` 逐份重算，**0 missing / 0 mismatch**；4 个目标 class 的改前哈希另存。
- Java 17（`C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`）、`clean build --offline --no-daemon` 成功（45s）。`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.43.jar`，**1,633,780 字节**，973 entry / 568 class，SHA-256 `B838E25AA4F4E9A60DE6FED52BCF928BA9D70E05DC0A6AB5473286D81DB22062`。归档为最终产物逐字节复制。
- 340 份源码只改 **3 份**：`MachineModifier.java`、`DynamicPattern.java`、`MachineControllerBlockEntity.java`。568 份 class 有 5 份变化：上述 3 个主类，另加 `DynamicPattern$MatchResult`/`$Status`（重新编译同一外部类的嵌套类型；无源码语义变化，未增删类）。
- `gradle test` = **NO-SOURCE**（如实记录，不是测试通过）。

## J1 / P07-03：单块修改器的状态约束随控制器旋转

**根因。** `MachineControllerBlockEntity.evaluateModifiers` 只把 offset 旋转（`modifier.rotatedOffset(rotationSteps)`），随后直接遍历 `modifier.accepted()` 的北向原始 matcher；`MachineModifier.matches` 也未使用任何旋转。改前实际控制器（非静态推断）四向观测：

| 控制器 | 正确状态 | 观测 | 错误状态 | 观测 |
|---|---|---|---|---|
| 北 | 熔炉东 | active=1，扣 5 FE | 熔炉北 | active=0，扣 10 FE |
| 西 | 熔炉北 | active=0，扣 10 FE | 熔炉东（未旋转） | active=1，扣 5 FE |
| 南 | 熔炉西 | active=0，扣 10 FE | 熔炉东 | active=1，扣 5 FE |
| 东 | 熔炉南 | active=0，扣 10 FE | 熔炉东 | active=1，扣 5 FE |

**修复。** `MachineModifier` 新增 `acceptedAt(rotationSteps)`：对每个候选调用既有 `BlockMatcher.rotateYCounterClockwise()`，展开其**返回列表的全部元素**（耦合属性会把一个部分描述符拆成多个精确候选），按描述符去重；`matches(state, rotationSteps)` 用它匹配。`evaluateModifiers` 改为调用该入口，位置与候选共用同一套 `(z,y,-x)` 逆时针语义；每个修改器仍在每个位置只激活一次，定义里的 `accepted` 描述符不被改写。

**证据（`_audit/sixth-ai-4tasks-0.121.43/J1/`）。** `ModifierRotationCheck` 走实际 schema → 控制器 tick → `activeModifiers()` → `RecipeModifiers` → 真实 `LongEnergyStorage` 结算，改前 **25 项失败**、改后 **8 组全过**（prebuild 与最终 class 各一次）。覆盖：

- 四朝向正确/错误状态、5/10 FE 实际结算；
- 非轴心偏移 (1,1,0)：西向转 (0,1,-1)、东向转 (0,1,1)，Y 保持 1；未旋转位置的正确炉子不激活；
- 无方向方块（金块）位置旋转；
- 多候选：东/西两个炉子候选在西向控制器分别转成北/南，两个世界都成型，只取返回列表第一个会漏南向；
- 自定义耦合属性：独立 4 状态 × 4 转向真值表（偶数转 `b=false`、奇数转 `b==a`）与控制器实际激活逐项一致；旋转结果 2 个精确候选且不可变；
- 同一位置两个修改器各激活一次（`floor(10×0.25)=2 FE`）、炉子/金块条目相互独立、未加载位置跳过且扣 10 FE；
- **工厂真实入口**：带 `has-factory` 的控制器经结构检查后，`FactoryEngine.tick` 读同一份 `be.modifiers()`，正确态扣 5 FE、错误态扣 10 FE；
- 活跃列表不可变、来源描述符不变。

## J2 / P07-04：动态重复段与收尾段的展开状态

**根因。** `DynamicPattern.match` 使用构造时缓存的 `partRotations/endPartRotations`（已旋转状态），`expand→addSection` 却只把坐标 `rotate(...)` 后写回 segment 的**原始** matcher。改前主四向世界都能量出 2 节，但 `expanded.matches`/`MachineRegistry` 只有北向成型；`MachineStructures.runtimeStructure`（先 `expand(2,0)` 再整体 `rotated`）四向都接受，形成内部矛盾。夹具日志逐向记录：

```
OBS north  expandedMatches=true  runtimeUnit=[furnace[facing=east]]
OBS west   expandedMatches=false runtimeUnit=[furnace[facing=north]]
OBS south  expandedMatches=false runtimeUnit=[furnace[facing=west]]
OBS east   expandedMatches=false runtimeUnit=[furnace[facing=south]]
```

**修复。** `expand(size, rotationSteps)` 按 `Math.floorMod(steps,4)` 取**同一份已旋转段**（`partRotations`/`endPartRotations`），`addSection` 只做“段内已旋转坐标 + 段起点”，不再二次旋转坐标或状态；规则、标签、段号随段一起取自已旋转副本。量长度、有效结构、诊断/投影因此使用同一套约束。

**证据（`_audit/sixth-ai-4tasks-0.121.43/J2/`）。** `DynamicExpandCheck` 改前 **38 项失败**、改后 **9 组全过**（prebuild 与最终 class 各一次）。主四向使用交接给定的显式表（(1,0,2)/(1,0,3)、金块 (0,0,4) 等），不用生产旋转函数生成期望；另覆盖：

- 方向性尾段四向（尾炉状态旋转）；
- 不对称单元 (1,0,0)+(0,1,1) 与非零高度、四向坐标/标签；
- 部分属性（楼梯 half/shape/waterlogged 通配）与多候选集合（东/西→北/南）；
- 自定义耦合属性动态单元 4 状态 × 4 向真值表，测量与展开同时一致；
- 无方向/无尾、min2/max3 的 0/1/2/3/4 边界；min0 无尾 0 节成立；
- 0 节 + 尾段锚在旋转后的 start（不是 start+step），四向坐标/标签/registry；
- NBT Count=1 拒绝、Count=3 允许，规则随旋转仍在正确部件；注册 checker allow/deny 在测量与展开中**实际被调用**（计数>0）；
- 两个动态段组合的方向、标签（`feedA_lineA_0`、`cap_lineA_end`、`feedB_lineB_0`、`cap_lineB_end`）与最终 registry 成型；
- `runtimeStructure` 与 `expand` 的位置及约束描述符逐位置相等。

**未扩项。** P07-05（多块条件漏检 NBT/checker）、P07-06（重复/尾候选重叠的 max 边界）、P07-02（修改器替代基座）未改；共享 `BlockMatcher`/`MachinePattern`/网络/协议/3D renderer 未改。协议仍为 12。

## 回归与边界

- 同一批 `FactoryRuntimeAuditCheck`（P06-b1，0.121.0 夹具）在 **0.121.42 与 0.121.43 上都在 P06-05“restricted idle starvation”处失败**，失败点与信息一致；这是夹具与后续版本的既有差异，不是本批引入。本批自己的工厂入口断言（J1 `factoryConsumption`）通过。
- 动态 0 节、标签、规则、诊断/预览数据由 J2 覆盖；普通控制器结算与工厂结算由 J1 覆盖。
- **未验**：真实 Forge 游戏、物理区块加载/卸载、真实仓口能力收集、OpenGL/投影画面、多人网络、外部第三方方块。自定义耦合方块只在夹具进程内注册。
- 证据目录含改前/后 340 份源码哈希、改前 568 class 快照、最终 class 快照、源码 diff、运行命令/退出码/日志与失败断言。未复制生产算法生成期望；改前日志保留真实失败。
