> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex复验：两项通过离线验收，原第二批12项全部通过；真实游戏/网络/GPU待验。** 项目docs/归档/开发记录/验收记录/验收-GLM返工-0.121.31-2026-11-03.md为当前验收记录，下方待复验文字为交付时状态。

# GLM 返工结果 0.121.31（C2 与 C4 两项，2026-11-03）

**执行者：GLM-5.3 Flash，按[返工交接-GLM第二批C2-C4-2026-11-03.md](../任务交接/返工交接-GLM第二批C2-C4-2026-11-03.md)只返工 C2/C4；其余 10 项（含 0.121.27 四项）未改、未回退。两项自测通过，状态为“待Codex复验”。** 未修改累计账本或未修清单。

## 结果总表

| 任务 | 状态 | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| R1 / C2 / P02-09 | 完成，待Codex复验 | `command/BlueprintCommand.java`：删除私有 `MachineIdArgument`，改用原生 `StringArgumentType.greedyString()`；回调 `strip()` + 剥离一对匹配引号 + `tryParse` 校验；移除 0.121.30 的 `get_blueprint.invalid_id` 语言键 | R1/checks-prefix.log：内置参数对照树往返通过，但含蓝图节点的树在 `ClientboundCommandsPacket` 构造期抛 `Unrecognized argument type …$MachineIdArgument`（FIX 失败 2 项）；服务端解析/权限/发物品 5 项对照通过 | R1/checks-postfix.log 7/7、退出0：真实 `ClientboundCommandsPacket` 构造→写 `FriendlyByteBuf`→读回→`getRoot` 重建成功，重建树含 `mm-get_blueprint` 且参数为原生 `string()`；客户端树可解析全部接受写法；服务端完整 ID/裸名/引号/大写/多余文本/空输入/权限0与2/控制台/已知未知/发物品全部符合 | 真实服务器启动与玩家登录、真实客户端聊天栏与连接未跑（离线命令树+dispatcher 验证）；未插桩命令包或参数注册器 |
| R2 / C4 / P10-13 | 完成，待Codex复验 | `client/jei/MachineRecipeText.java` 的 `displayValue`：分组改为按值比较 `Long.MAX_VALUE`；超长分支用 `BigDecimal(value) × multiplier` 后 `setScale(0, FLOOR)` 精确换算再输出 | R2/checks-prefix.log：MAX+1 在 FE/RF 正常，但 IC2_EU/GT_EU 输出 9223372036854775808（应为 2305843009213693952，4 倍错误；FIX 失败 2 项）；long 内缩写、零/负边界、输出侧 3 项对照通过 | R2/checks-postfix.log 5/5、退出0：四单位逐一对照——FE/RF 1:1；IC2_EU/GT_EU 精确 1/4 向下取整（2^63+2 → 2^61，2^63+4 → 2^61+1）；MAX/2+1×2 与 (MAX+1)×2 总量精确；long 内保留 4.0K/8.0G/40→10 EU；0/负无提示；输出侧镜像 | 真实 JEI GUI/GPU 未跑；类别“有能量需求”经真实精确访问器核对，未截图 |

## 版本与基线

| 项 | 值 |
|---|---|
| 起始 | 0.121.30 / 协议12 / HEAD `f94ba299…`（`BlueprintCommand.java`、`MachineRecipeText.java` 改前 SHA-256 与 12 项交接 before 副本一致，确认返回的是 0.121.30 状态） |
| 结束 | **0.121.31** / 协议12 |
| 改前类基线 | `_audit/glm53flash-12tasks-0.121.30/production-classes/`（0.121.30 交付快照） |

### 两文件改后 SHA-256

| 文件 | 改后（0.121.31） |
|---|---|
| `command/BlueprintCommand.java` | 见 `_audit/glm53flash-rework-0.121.31/after-hashes.txt` |
| `client/jei/MachineRecipeText.java` | 同上 |

增量 diff 入口：`_audit/glm53flash-rework-0.121.31/incremental-vs-handoff.diff`（相对 12 项交接 before；`BlueprintCommand` 净变化为自定义类型→原生 greedyString + 引号/校验回调，`MachineRecipeText` 只动 `displayValue`）。0.121.30 新增而本次不再需要的 `get_blueprint.invalid_id` 语言键已从 en/zh 删除（净零）。

## 命令与退出码

| 步骤 | 命令要点 | 退出码 | 结果 |
|---|---|---|---|
| 夹具编译 | `javac -encoding UTF-8 -cp classpath-pre.txt -d fixture-classes R1/CommandTreeSyncCheck.java R2/EnergyUnitExactCheck.java` | 0 | 无错误 |
| 改前运行 | `java -Xmx1G -javaagent:token-agent.jar` | R1=1 R2=1 | R1 2 项 FIX 失败（其余 5 通过）；R2 2 项 FIX 失败（其余 3 通过）（checks-prefix.log） |
| 正式构建 | `./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true`（Java17） | 0 | BUILD SUCCESSFUL in 27s；compileJava/reobfJar 实际执行；Gradle test=NO-SOURCE（build-0.121.31.log） |
| 类加载来源 | classpath-post.txt 首位为 0.121.31 快照 | — | 无旧生产类目录混入 |
| 改后运行 | 同上 | R1=0 R2=0 | R1 7/7、R2 5/5 全部 PASS（checks-postfix.log） |

## 正式包与归档

| 项 | 值 |
|---|---|
| 归档 | `releases/modular-machinery-reborn/v0.121.31/modular-machinery-reborn-0.121.31.jar`，cmp 字节一致 |
| 大小 / SHA-256 | 1628118 字节 / `FA9C351D3CD9526ED0481264CA7BB773FAC325A786338C4E9B6C55A47CA6A3E9` |
| JAR 内版本 | `META-INF/mods.toml` `version="0.121.31"` |
| 同步位置 | gradle.properties、工作区/项目 README、`docs/en/README.md`、**`docs/en/Introduction.md`（补齐 0.121.28～31 状态，含 0.121.27 漏更）**、**迁移日志（补齐 0.121.28～31 条目）**、`docs/归档/版本记录/更新记录-0.121.31.md`、release 索引（旧归档全部保留） |

## 夹具说明与诚实记录

- R1 夹具自身修正过程保留：对照树原先与蓝图命令同在一个 dispatcher，整树序列化仍会因自定义类型失败——改为独立 dispatcher；随后确认原版 `ClientboundCommandsPacket` 重建树不下发命令回调（`getCommand()` 为 null），故客户端侧断言为结构（节点存在、参数类型为原生 `string()`）与 Brigadier 解析，执行/发物品断言放在服务端 dispatcher。调试探针 `debugprov/RootProbe|EntryProbe` 证明重建树含 literal→argument 两层。
- R2 夹具曾误用 `Long.MAX_VALUE + 2`（long 溢出为负），改为以“多需求求和”构造 2^63+2 / 2^63+4 来验证非 4 整除的向下取整；期望值全部用 BigInteger 手算，不以溢出 long 为答案。
- 两夹具均为“正确行为必须通过”的断言，未复用 Codex 的缺陷观测程序（`_audit/review-glm-12tasks-2026-11-03/BoundaryCheck.java` 及其 checks.log 未改写、未作为通过依据）。
- 白名单外业务文件未改；未暂存/提交/推送、未部署游戏；0.121.27～30 的归档与结果文档全部保留，工作区他人未提交改动未触碰。
