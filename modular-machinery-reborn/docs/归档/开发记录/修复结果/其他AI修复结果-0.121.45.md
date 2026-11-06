> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03通过[Codex独立离线验收](../验收记录/验收-第七批4项-2026-11-03.md)及隔离clean build/reobf内容复现。** 原交付状态保留为历史，实机/AE网/GPU未验，主任务保持暂停。

# 其他AI修复结果 0.121.45（2026-11-03）— 第七批 L 组

**状态：待 Codex 验收。** 本轮只执行交接中的 L1/L2 两项，M1/M2 未开工；未部署、未启动游戏、未改整合包/存档、未暂存/提交/推送、未创建 Release、未改累计账本（仍 72 完成 / 5 部分 / 45 待处理）、未解除暂停。

起点 **0.121.44 / 协议 12 / 340 份 Java / 568 份 class**，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。开工前用 `_audit/seventh-ai-handoff-4tasks-2026-11-03/production-sha256.json` 重核 340 份源码哈希，差异 0；真实起点与交接一致，无需记录并发差异。

## 结论

| 编号 | 结论 | 版本 |
|---|---|---|
| L1 / P07-05 多块修改器的激活判断执行 NBT 与 checker | **完成** | 0.121.45 |
| L2 / P07-06 最大重复长度处仍能识别重叠候选的收尾段 | **完成** | 0.121.45 |

两项均按白名单改动，无超范围、无协议变更（12 保持，wire 字节未动）。

## 范围与改动

白名单内 3 份源码，340 份中仅这 3 份哈希改变：

1. `machine/MachineMultiBlockModifier.java` — 新增 `matches(Level, BlockPos, int)`、`active(List, Level, BlockPos, int)`；原谓词重载保留并标 `@Deprecated`，注释明确它不执行规则；修正过时的「只转位置不转状态」类注释。
2. `block/MachineControllerBlockEntity.java` — 仅 `evaluateMultiBlockModifiers`：删除自拼的 `isLoaded + BlockMatcher` 谓词，改委托 Level 重载。
3. `machine/DynamicPattern.java` — 仅 `matchesSize` 的 `count >= maxSize` 分支与相关注释：先辨认锚点上的合法尾，命中返回 `count`，否则仍 `-1`。

未改：共享 `MachinePattern`/`BlockMatcher`、ME 存储算法、账本、网络编码、客户端预览、工厂引擎、第六批已验收的 expand 旋转修正。

## 证据

工件：`_audit/seventh-ai-4tasks-0.121.45/`

- `L1/MultiRuleCheck.java`、`L2/DynamicTailCheck.java`（夹具）、`L1/logs/*`、`L2/logs/*`（运行日志与退出码）。
- `compiled-source/`：白名单 3 份源码对 0.121.44 class 快照编译出的修复类；`before` 运行使用快照、`fixed` 运行把 `compiled-source` 放在 classpath 最前。
- `regression/J1`、`regression/J2`：第六批套件复跑日志（J1 8 组 + FactoryFacing 16 断言、J2 9 组，退出 0）。
- `source-hashes-before-0.121.44.json` / `source-hashes-after-0.121.45.json`、`production-classes-0.121.45/`（568 class）、`artifact-validation.txt`、`bytecode-evidence.txt`。

### 改前（0.121.44 快照，缺陷成功复现）

L1 全部 8 组「PASS」都是缺陷复现：NBT `Count=1` 对 `3b` 仍激活且扣 5FE；deny/allow/未注册/抛异常四种 checker 调用数全为 0；非实体方块 NBT 条件被忽略；工厂入口同样 5FE。

L2：最大锚点尾段（1 段+尾、2 段+尾、不同 step、垂直 step、max0 重叠）全部 `-1`，`match`/`Registry`/控制器一致拒绝；只把 `maxSize` 提到 2 就量得 1 节。

### 改后（0.121.45）

L1：未满足 NBT 不激活、基础机器仍成型并扣 **10FE**；满足时 **5FE**。deny 恰调用 1 次，回调收到世界坐标 `(1,1,0)`、真实熔炉 `BlockState` 与 `Count=1` 的 NBT；allow 调用 1 次并通过；未注册与抛异常由 `ScriptHooks` 安全拒绝；`nbt`+`script-checker` 必须同时成立；程序化 object/null token 被拒且不抛异常。四朝向 + 非轴心偏移 `(1,1,1)`：正确状态 1 条激活/5FE，错误朝向 0 条/10FE，`Count=1` 0 条/10FE。未加载锚点：状态读取 0、实体读取 0、checker 调用 0，基础仍成型/10FE。两条条件同时命中时按声明顺序激活、叠乘 **2FE**，单条失败只移除自己（另一条仍 5FE）。工厂入口：10FE vs 5FE。活动列表是不可变快照。

L2：真值表与真实 schema 两层都按实际重复数成立；`maxSize+1` 且无合法尾/缺尾/无尾超限继续拒绝；`min0/max0`、0 节尾锚、`expand` 坐标与 `feed_line_*`/`cap_line_end` 标签、四朝向 2 节 `expand`+`Registry`+控制器成型、两个相邻动态段独立计数、未加载短路（不读状态）全部通过。

## 构建与归档

- Java 17、离线、`--no-daemon`、`clean build` 42s，`BUILD SUCCESSFUL`。
- `build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.45.jar` = **1,634,497 字节**，SHA-256 `DC33A49541F272AFF4B7DA68F7E02A9122D2DFD81FCD2F0D194BA0DB99F225C3`。
- 973 entry / 911 文件 / 568 class；215 份 JSON 全部可解析；en_us 566 键 = zh_cn 566 键；`mods.toml` 版本 0.121.45；SRG `Registries.f_N_` 存在、无 `CREATIVE_MODE_TAB`；`PROTOCOL_VERSION = "12"`；未打包可选 API。
- 归档 `releases/modular-machinery-reborn/v0.121.45/`（逐字节 `cmp` 一致），旧 0.121.44 归档未改写。与 0.121.44 归档相比只有 6 个 class 条目 + `mods.toml` 变化；`DynamicPattern$MatchResult`、`$Status`、`MachineControllerBlockEntity$2` 的 `javap -c` 字节码完全相同（仅行号表位移）。

## 未验事项

- 未实机、未部署、未启动游戏；无真实区块装卸/事件总线、真实 Forge 服务端、Rhino、网络线程、GUI/GPU。
- 端口为真实世界匹配后注入的真实存储对象，不是物理仓口能力收集；未验证真实 AE2 网络、外部能源后端。
- `test` = NO-SOURCE，Gradle 未执行行为测试；行为证据来自上述夹具。
- 客户端预览/建造投影未复跑；未验证脚本 checker 在真实 Rhino 中的注册路径（本批只用 Java 回调）。
