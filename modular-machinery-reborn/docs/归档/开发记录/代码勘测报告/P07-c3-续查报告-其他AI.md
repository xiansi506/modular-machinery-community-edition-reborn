# P07-c3 续查报告（其他 AI 执行，待原 AI 复核）

日期：2026-10-31。执行方：接手交接文档《代码勘测交接-供其他AI执行.md》的另一 AI。
基线：0.121.0 / 协议 12 / HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`，`src/`、`gradle.properties`、`build.gradle` 全程无差异。
本轮范围：**仅 P07-c3**（`ControllerPortCollector`、`MachineControllerBlock`、`ParallelControllerBlock`、`UpgradeBusBlock` 共 31 条入口的覆盖收口）。未修复任何代码、未构建、未部署、未暂存/提交/推送、未进入 P08。

审计工件：项目同级 `_audit/code-survey-0.121.0/P07-c3-review/`（旧 `P07-c3/` 目录未改写一个字节）。

## 1. 一页结论

- **新确认问题：0 条，无新编号。** 31 条入口逐条静态复核完毕，行号 31/31 与当前源码对齐；未发现此前 01～18 之外的新缺陷。
- **扩展/细化既有项（均不新增编号）：**
  1. P07-11 现状确认：`UpgradeBusBlock` 当前源码仍只有 `playerDestroy` 弹库存（无 `onRemove` 覆写）；反编译证实创造模式根本不走 `playerDestroy`（`ServerPlayerGameMode.destroyBlock:246-248`），即创造破坏同样丢内容物——这与原版箱子行为一致，归入 P07-11 待验清单的"创造破坏"项。
  2. P07-b1 既有观察"动态改变 tier 后实体 final 字段不同步"扩展到并行控制器/升级总线：`ParallelControllerBlockEntity` 的 `tier` 为 final（ctor:50-52），管理命令原位改 `TYPE` 不会重建实体；无支持入口，维持"不编号"。
- **优化候选（无测量数据，按规则只标候选）：** `MachineControllerBlock.getShape` 对绑定控制器每次查询都做 `MachineRegistry.byId` 查表 + Optional 链 + `Shapes.create(box)` 分配；渲染/射线高频调用。建议按定义代次缓存 `VoxelShape`。未测得任何数字。
- **静态备注（不编号）：** ① `collectParallelControllers`/`collectUpgradeBuses` 缺 `hasChunkAt` 保护，但唯一调用点在成功匹配同 tick 之后，而 `MachinePattern.matches` 谓词要求所有位置 `level.isLoaded`（MachinePattern.java:139），当前调用图不可达；若未来按 `refreshInputPorts` 模式复用须先补保护。② `MachineControllerBlock.use` 的 `(ServerPlayer)` 强转只可能被非 ServerPlayer 假玩家触发。③ 3 参 `Block.dropResources` 恒传 null BE（Block.java:261-269），第三方若用它掉落控制器物品则 owner 丢失；1.12.2 同机制，parity 成立。
- **判定：P07-c3 的 31 条占位标签已全部解除；建议将 P07 标为"离线勘测完成、实机待验"，附三个条件**（见 §7），由原 AI 审查确认。
- 本轮未新写运行夹具，理由见 §4。

## 2. 基线与生产差异核对

开始（22:05）与结束（22:22）两次核对均已保存：

- `_audit/.../P07-c3-review/baseline/git-baseline-2026-10-31.txt`（开工时）
- `_audit/.../P07-c3-review/baseline/git-final-2026-10-31.txt`（收尾时）

两次均为：HEAD `f94ba299` 不变、`git diff -- src gradle.properties build.gradle` 为空（含暂存区）、`mod_version=0.121.0` 不变。项目内未提交内容仍是交接时的 `docs/开发/README.md` 修改与三个未跟踪勘测路径；本批只新增了本报告与 `P07-c3-review/` 夹具，未覆盖他人工作。反编译 jar 的提取只写入 `P07-c3-review/mcsrc/`，不触碰项目。

注意：`_audit/.../P07-c3/source-sha256.txt` 与当前树比对为 39/41 匹配，不匹配的正是外部改动过的 `MachineControllerBlockEntity.java`、`MachineHatchBlockEntity.java`——与 c1 交接一致，属历史指纹，本报告未改写它们，也未把这两个类的旧行号当作当前源码使用。

## 3. 方法范围表

31 条逐条结果见修订表 `_audit/.../P07-c3-review/method-responsibility-revised.tsv`（列：本轮结论 / 归属与证据级别 / 必要依赖 / 未覆盖待验）。旧表 `P07-c3/method-responsibility.tsv` 未改动；本表由旧表 31 条占位行（统一标签"P07 remaining static; P02 UI/P10 rendering"）逐条得出，其余 556 行未动。

行号校验：`range-check.py` + `range-check-output.txt`，31/31 的 TSV 区间与当前源码对齐（个别条目 ±1 注解行计法差异）。

摘录（完整见修订表）：

| 文件 | 结论要点 |
|---|---|
| ControllerPortCollector (2) | `collect` 唯一调用方 `MachineControllerBlockEntity.collectHatches:1744`，两触发路径：成型 tick（:658）与 `refreshInputPorts`（:2085←`INPUT_DEPENDENCIES.takeDirty:603`）。主路径（成型→收集→HatchCollection→组过滤→配方事务）已有 P07-b2 原生运行覆盖；组快照=P07-15；未加载位置跳过=P07-14 报告已记载。本轮补齐：GroupAllocator 全分支静态核对无碰撞；`dependencies` 先全量入 watch 集再跳过收集是有意设计。自动分配分支现生产实现者只有 AE2 供应器→运行验证留 P08；MagicHatch→P09；`extraFluidPort`/`gasPorts` 实现侧在 P08 与外部改后的 MachineHatchBlockEntity |
| MachineControllerBlock (19) | 三注册形态共用同一 `MACHINE_CONTROLLER_ENTITY` 类型（ModBlocks:193、:547），`getTicker` 单类型检查正确。owner 闭环：`MachineControllerItem.place`（canPlace 门禁→setOwner）↔ BE 存读（:2497/:2559）↔ `getDrops`（BE→物品标签）↔ `BreakEvent`（canBreak），爆炸路径携带 BLOCK_ENTITY（见 §4）。`onRemove` 无客户端保护是安全的：反编译 `LevelChunk.setBlockState:247-248` 仅服务端调 onRemove |
| ParallelControllerBlock (4) | tier 接线闭环：`PropertyBlockItem.getPlacementState→applyVariant` 写 TYPE → 实体 ctor 读 state（final）。`use` 与控制器同形。无 ticker 与原版一致 |
| UpgradeBusBlock (6) | `getTicker` 客户端 null、服务端 reconcile 先 `isLoaded` 再查（P06-c2b 原生覆盖）。`playerDestroy` 即 P07-11 现状（上） |

范围核对（41 文件 / 587 方法）：每个 inventory 行都有责任行（588 行表）；按文件→报告映射核实，除 31 条占位外其余标签均落到具体报告：HatchCollection(51)→P05/P06-a1/d2 + b2 原生；HatchKind/HatchTier/UpgradeBusTier(25)→P03/P06/P07-b1；MachineControllerBlockEntity(161)→P06 细粒度索引；ParallelControllerBlockEntity/Tier(20)→P06-c1；UpgradeBusBlockEntity(24)→P06-c2a/c2b；MachineInputProvider(5)→P06-d1；machine/*(81)→P03/P07-a1/a2a/a2b；assembly/*(32)→P07-c1/c2；item/*(43)→P01/P02/P07-c1/P10/P12（file-responsibility 明示静态通过、非全量执行）；BlockCasing/CasingType/MachineCasingBlockEntity(16)→静态 tint/存储/state，packet/render 留 P10。**未发现因"继承旧责任标签"而漏掉的方法。**

## 4. 问题证据（本轮关键查证与边界）

**证据级别说明：本轮为"静态 + 原版反编译源码引用 + 复用旧原生覆盖"。** 三个载重结论均以反编译源码原文定位，可 grep 复核（提取件与哈希在 `P07-c3-review/mcsrc/` 与 `fingerprints.txt`，来源 jar 指纹已记录）：

1. **爆炸掉落携带 BLOCK_ENTITY（支撑 owner 闭环与 getDrops 注释的主张）**
   `Explosion.java:255-262`：对每个被炸方块，在 `onBlockExploded`（触发 setBlock→onRemove）**之前**取 `level.getBlockEntity(blockpos)`，并以 `withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockentity)` 构建 LootParams 后调 `blockstate.getDrops(builder)`。因此爆炸摧毁控制器时 owner 会写入掉落物品；`Block.dropResources` 6 参重载（玩家破坏/destroyBlock）同样传 BE。边界：3 参重载恒传 null（`Block.java:261-269`）。
2. **onRemove 仅服务端调用（支撑控制器/仓口 onRemove 不写 isClientSide 保护的现状）**
   `LevelChunk.java:247-248`：`if (!this.level.isClientSide) { blockstate.onRemove(...); }`。且 `ItemStackDrops.pop` 自身再挡一次 `level.isClientSide`（双保险）。b1 已原生验证过 `LevelChunk.setBlockState` 调 onRemove 的链路。
3. **创造破坏不走 playerDestroy（支撑 P07-11 创造破坏项细化）**
   `ServerPlayerGameMode.java:227-262`：creative 分支 `removeBlock` 后直接 return；仅生存分支调 `block.playerDestroy(...)`。同文件 ：229 证实 `ForgeHooks.onBlockBreakEvent` 在两条分支前触发——ControllerSecurity 的 BreakEvent 门禁对所有玩家破坏生效（creative 由 canBreak 豁免），不受影响。

**静态链条核对（关键调用链已逐行读过）：**
- collect 三条实例检查（magic / hatch / provider）互斥性、usedGroups 种子与 add 时机、allocator 边界（preferred>65535 未占用→用之；否则 65536 递增；`allocated` 为局部，组号只在单次集合内有效——配方引用的是声明组，自动组是集合内部值，不跨 collect 持久）。
- 匹配→收集的区块前提：`MachinePattern.java:139` 谓词含 `level.isLoaded(at)`，任一位置未加载即匹配失败，故成型当 tick 三条 collect 路径区块必然全部已加载；`refreshInputPorts` 只走带保护的 `collect()`。

**为何不新写运行夹具（如实记录，非隐瞒）：** 本轮没有新编号问题需要复现；三个载重结论的不确定性都在**原版行为**侧，反编译源码即确定性依据，mock 夹具反而更弱；collect 主路径与 reconcile 已有 b2/c2b 的原生运行覆盖（指纹与断言俱在），重复搭建 Unsafe 世界壳夹具只复述旧 PASS。`collect` 自动组分配分支在当前生产代码中没有非 AE2 实现者可驱动，写一个临时实现去跑它对"当前生产行为"的证明价值有限，留给 P08 用真实 AE2 供应器验证更合适。**本轮没有任何检查失败或被跳过日志被删除的情况；不存在"未能运行的检查"。**

## 5. 指纹清单

见 `_audit/.../P07-c3-review/fingerprints.txt`（生成于 22:22:03），包含：

- 反编译来源 jar：`forge-1.20.1-47.2.0_mapped_official_1.20.1-sources.jar`（sha256 `0196eae9…`），提取的 9 个原版源文件（Explosion、Block、LevelChunk、ServerPlayerGameMode、ClientLevel、ServerLevel、BlockBehaviour、BlockEntity、ItemEntity）。
- 4 个目标生产源文件 + 11 个必要依赖生产源文件（ControllerSecurity、ItemStackDrops、ModBlocks、MachineControllerItem、PropertyBlockItem、ParallelControllerItem、MachineInputProvider、MachinePattern、MachineRegistry、两个 BlockEntity）的当前 sha256。
- `range-check.py`/输出、修订表、baseline 两份 git 核对为本批新增工件；旧 `P07-c3/` 与其他历史夹具零改动。

## 6. 本轮边界（如实声明）

- 未运行任何游戏/服务器/完整 Forge 事件总线；未构建 jar；未执行 1.12.2 对照源码运行。
- 未读的外围：`HatchCollection` 51 个方法的逐方法复核依赖 b2/P05 已有原生覆盖；`MachineControllerBlockEntity`/`MachineHatchBlockEntity` 两外部类的增量复核未做（按 c1 交接单列，本报告未把它们的旧行号用于当前源码）；AE2 气体/魔法端口实现细节留 P08/P09。
- `use()` 无 owner 门、`collect()` 跳过未加载位置、比较器读数等均为既定接受范围或已有编号，未重新报告。

## 7. 给原 AI 的复核入口

**最值得重跑（成本极低、grep 级）：**
1. `Explosion.java:255-262`（BLOCK_ENTITY 携带 + 先取 BE 后 onBlockExploded 的顺序）与 `LevelChunk.java:247-248`（onRemove 服务端 gated）——直接在 `P07-c3-review/mcsrc/` 或你自己的映射源里核对两处原文。这两条是本轮唯一把"旧注释主张"升级为"有原文依据"的地方。
2. `MachinePattern.java:139` 的 `level.isLoaded` 谓词——collect 路径"当前不可达缺保护"判断的前提。

**最不确定的三个结论：**
1. `getShape` 优化候选的价值：无任何测量，是否值得做缓存请按 P10 实机 profiling 决定，不要因本轮提及而直接立项。
2. "动态 tier 原位变更"扩展到并行/升级总线：我按 b1 既有观察归并为"无支持入口，不编号"；若你认为它应独立编号，请改判。
3. P07 收口建议本身：我的依据是范围表 31/31 + 全文件映射核实 + 主路径有旧原生覆盖；"离线勘测完成"的最终判定权在原 AI。

**依赖的假设：** 反编译 sources jar（指纹已存）与实际游戏运行的 1.20.1 行为一致（官方映射反编译，风险低）；`MachineControllerBlockEntity` 的 owner/collect 接线方法（:1744/:2497/:2559/:603/:658/:2085）当前与 HEAD 一致（文件有 git 无差异）。

**"本批是否可收口"建议：** 建议在满足以下三点后，把 P07 标为"离线勘测完成、实机待验"：① 原 AI 复核 §7 两处反编译原文与 `MachinePattern.java:139`；② 外部改后的 `MachineControllerBlockEntity`/`MachineHatchBlockEntity` 增量复核单列（不阻塞 c3 结论，因为本轮未引用其旧行号）；③ P07-01～18 与"实机待验"清单原样带入下一阶段。若原 AI 不接受"无新夹具"的证据形态，最合适的补测点是 P08 的 AE2 供应器 collect 分支，而不是为本批再造 Unsafe 世界壳。
