> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 MMCER第十批返工验收通过：当前及最近整版离线通过55/协议12，第十批四项指定范围收口。累计88项完成、5项部分、29项待处理。** 未实机/真实网络/GPU验收，未部署或推送；主任务暂停，下方旧状态为历史。 [55复验报告](../验收记录/验收-第十批R2返工-0.121.55-2026-11-04.md)。已结束的返工不重复执行。

> **2026-11-04 MMCER第十批验收：当前54/协议12，R1/S1/S2通过指定范围离线验收，R2创造模式遇未加载目标仍会无进展循环，需要返工。最近整版通过52；累计87项完成、5项部分、30项待处理。** 主任务暂停，未实机/部署/推送，下方旧状态为历史。 [验收](../验收记录/验收-第十批4项-2026-11-04.md) · [R2返工](../任务交接/返工交接-第十批R2-2026-11-04.md)。

# 其他AI修复结果 — 0.121.54（第十批S批，2026-11-04）

**结论：S1/P09-02 与 S2/P10-18 完成指定范围修复，标待 Codex 验收。** 起点为**实际交付的 0.121.53**（1,642,959 字节，SHA `C8A192DF…6EFD07`），保留 R 批全部改动；HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。累计账本 **仍为 84 项完成 / 5 项部分 / 33 项待处理**，本批不销号、不解除主任务暂停。未实机、未部署/启动游戏、未改实例/存档、未暂存/提交/推送/创建 Release。**第十批 4 项至此全部交付后停止。**

## S1 / P09-02 — 极大 LP 缓存安全回填 Blood Magic 网络

| 项 | 结论 |
|---|---|
| 调用原生 `add` 前用 long 计算安全本次请求（缓存、网络余额、容量/剩余空间、int 范围） | **完成** |
| 超出当前宝珠容量的缓存保留等待以后回填，不按容量截断/丢弃 | **完成** |
| 比较真实前后余额与返回量一致性，按明确故障策略保护本仓资源 | **完成** |
| 输入补给与 Will 路径保持原行为；负网络余额不静默修成 0 | **完成** |
| 真实 Fill 事件取消 / 改变 maximum 契约 | **完成（受控事件桥）** |
| 任意第三方监听器改写集合的通用事务回滚 | **超范围／未做**（见边界） |
| 已证实的玩家远程注入漏洞 | **不适用**（极端存档/API 边界） |

**核对的原生契约（Blood Magic 3.3.8-50）：** `SoulNetwork.add(SoulTicket, int maximum)` 先 post `SoulNetworkEvent.Fill`（可取消、可 `setMaximum`），再取 `int before = getCurrentEssence()`；若 `before >= maximum` 返回 0；否则 `int after = Math.min(maximum, before + ticket.getAmount())` 里的 **`iadd` 是 int 加法**，`setCurrentEssence(after)` 后返回 `int after - before`。两次 int 运算都可能回绕；`SoulTicket` 的 amount 也是 int。原实现把整个 `stored` 作为 ticket 送入，INT_MAX 缓存 + 网络 100 时 `before+amount` 回绕为负，返回值再次回绕成正数，既有 `moved<0 || moved>stored` 守卫漏检。

**修复：** 输出分支以 long 计算 `request = min(stored, capacity - balance, Integer.MAX_VALUE - balance)`；`request <= 0` 时不回填、缓存原样保留（网络已满/超过容量时不丢弃）。只把 `(int) request` 送进原生 add，保证 `before + request` 不会溢出 int。调用后再次读 `getCurrentEssence()`，用 `long credited = after - balance` 与返回的 `moved` 核对：`moved != credited` 或 `credited` 越界时记录明确诊断，范围内按**真实余额差**结算本仓缓存，范围外保留缓存等待后续 tick；不调用 `setCurrentEssence` 强行恢复余额，不盲目退款形成复制，也不宣称回滚了事件的全部副作用。异常/`LinkageError` 同样保留缓存并告警。输入分支、`access`、Will 变体与 5 tick 门控未改。

**验证（`LpOverflowCheck`）：** 真实 `BMWorldSavedData` / `NetworkHelper` / `Binding` / `IBloodOrb` / `SoulNetwork.add` / `SoulTicket` 与生产 `BloodMagicBackend.tick`；极大缓存一律经真实 `MagicHatchBlockEntity.load` 进入，不直接写字段；`MinecraftForge.EVENT_BUS.post` 用确定性事件桥驱动取消与 `setMaximum(75)`。改前 41 项中 12 项失败，最终 Gradle 类 41/41 通过。覆盖：INT_MAX 缓存/网络100/容量5000 不回绕且守恒；普通 5000 缓存对照；网络空/满/超过容量；接近 INT_MAX 余额只回填 7；宝珠容量缩小；负网络余额保留可观察；多个 5tick 周期；`save/load` 往返；真实 Fill 取消与改上限；失效/畸形绑定；输入方向与 Will 普通对照。

## S2 / P10-18 — 清理中的异常不能跳过后续恢复

| 项 | 结论 |
|---|---|
| 每项恢复独立保护，前一失败仍尝试后续步骤 | **完成** |
| 首因保留、后续清理错误作为 suppressed 与明确日志，不覆盖首因、不全吞不报 | **完成** |
| pending / BUFFERS 可靠清空，失败 chain 引用置空且不重复 close | **完成** |
| 只有实际 push 成功才 pop；更早初始化失败不多 pop | **完成** |
| discard 单个 builder 失败仍继续其他缓冲并清映射 | **完成** |
| `reset()` 中 close 失败仍继续 discard，重复 reset/登出不重复 close | **完成** |
| 不笼统 catch Throwable、不改共享 BufferBuilder/其他 renderer/shader | **完成** |
| 真实 FBO/shader/GPU/驱动与实机画面恢复 | **未做**（受控替身隔离 GPU） |

**修复：** `onRenderLevelStage` 把 `pushPose` 移入 try 并用 `pushed` 标志控制 pop；每个清理步骤经 `attempt(primary, step)` 单独捕获 `Exception | LinkageError`，用 `addSuppressed` 累积后续错误，首因不被覆盖。纯标志赋值（`pending=false`）放在可能抛错的清理之前。失败时先 `chain=null; main=null` 再 close，避免失败链被复用或重复关闭；渲染主体的可恢复异常仍沿用既有降级（warn + `failed`），纯清理异常按原 finally 语义传播首因。`discard()` 对每个 `BufferBuilder` 单独保护，循环结束后无条件 `BUFFERS.clear()`，再重抛首因。`reset()` 同样先置空引用、分别保护 close 与 discard，并一定清 `pending/failed`。

**验证（`BloomCleanupCheck`）：** 真实生产 `onRenderLevelStage` / `discard` / `reset` / `onLoggingOut`，明确标识的受控 `RenderTarget` / `PostChain` / `PoseStack` / `BufferBuilder` 替身；`RenderSystem.recordRenderCall` 的入队 lambda 在隔离进程中受控 replay。改前 120 项中 62 项失败，最终 Gradle 类 120/120 通过。故障矩阵：正常帧、process 失败、copyDepthFrom 失败、bindWrite 失败、close 失败、单 buffer end 失败、多处同时失败、push 失败、失败后的下一帧、reset 重复调用/登出；逐项核对 CPU projection、模型视栈 push/pop 次数、pending、缓冲映射、每项清理调用计数、首因消息与 suppressed 数量。

**一处夹具契约说明（不是弱化断言）：** `chain.close()` 只在降级路径执行，因此「close 失败」用例必须伴随一个触发降级的失败（本夹具用 depth restore 失败）；单独把 close 设为失败而其余步骤健康时，生产代码本来就不会 close 一个正常 chain。改前该组合的多条断言确实失败，改后全过。

## 回归

以最终 Gradle 类运行：Q1 三条 `IdentitySupplement` 3/3、`FactoryIdentityCorrectCheck`、`IdentityMatrixCheck` 428 条、`FinalTickCheck` 全部通过（`-Dstage=fixed` 恒传）；机器/工厂仅只读回归。S 批魔法普通后端/输入路径与 Bloom 正常/异常帧由上述两个夹具覆盖。没有把混有「旧缺陷应存在」断言的旧 main 当作正确行为回归。

## 构建与证据

- Java 17 的 `java.exe` 启动 Gradle wrapper，`clean build --offline --no-daemon`，`JAVA_HOME` 指向 JDK17、`GRADLE_USER_HOME=C:/mmwork/.gradle-home`；**常规 build 目录未被占用，无需隔离 buildDirectory**。42s `BUILD SUCCESSFUL`，含 `> Task :clean` 与 `> Task :reobfJar`，`test NO-SOURCE` 如实记录。命令/JDK/原始日志/退出码在 `_audit/tenth-ai-4tasks-0.121.54/logs/`。
- 证据目录 `_audit/tenth-ai-4tasks-0.121.54/`：`baseline.json`（53 起点）、`before/`（5 份白名单改前源码与改前 `gradle.properties`）、`diffs/`（2 份源码＋版本属性 unified diff）、`final-classes/`（完整 568 类快照）、`production-sha256-after.json`（340）、`accepted-class-hashes-after.json`（568）、`resources-sha256-after.json`（342）、`logs/`（改前失败日志、最终成功日志、原生 API 契约、命令与退出码）、`validation.txt`、`fixtures/`。
- 相对 53 基线仅 2 份白名单源码与 `gradle.properties` 改变；资源 342 文件哈希全等；类名集合与 53 完全一致，无新增类。

## 产物与边界

- 正式 JAR **1,645,158 字节**，SHA-256 `4552B840748871C389C80A7028A4812ED08878E5BF80AFCCAAAEB5F826FCC39C`；`build/libs`、`build/reobfJar/output.jar`、归档 `releases/modular-machinery-reborn/v0.121.54/` 三处逐字节一致。旧 53 及更早归档未覆盖。
- JAR 内 568 class；221 个 JSON/mcmeta 可解析；中英语言键各 566 且键集一致；`mods.toml` 为 0.121.54；协议 12 与 wire 格式未变。
- **未验边界：** 真实 GPU/FBO/shader/驱动、实机画面、完整 FML/Forge 事件总线与任意第三方 `SoulNetworkEvent` 监听器副作用、磁盘存档、多人网络、真实 Botania/Blood Magic 游戏生命周期。S1 的事件桥只覆盖取消与改 maximum，若原生事件还能改变其他写集合且白名单内无法安全处理，需另带真实复现交回，本批未扩成通用事务框架；本缺陷是极端存档/API 边界，不描述为已证实的玩家远程注入漏洞。S2 的 CPU 状态恢复不等于实机画面恢复。
