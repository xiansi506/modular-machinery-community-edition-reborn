> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI修复结果：0.121.33（第三批D组D1～D4，2026-11-03）

执行 `修复交接-其他AI-第三批8项-2026-11-03.md` 的 D1～D4 四项，**全部完成，统一标待Codex验收**。E1～E4 属 E 组，随后续 0.121.34 批次；本批完成后暂停，不自动执行剩余任务。未部署/启动游戏、暂存/提交/推送、git reset/clean。

## 基线与增量

- 起点：HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`，0.121.32/协议12，340份Java；交接指纹 `docs/开发/_audit/third-ai-handoff-8tasks-2026-11-03/`（validation 全 PASS：340哈希与已验收0.121.32一致、11个白名单源文件存在）。全部已有修复保留，未从 HEAD/旧审计恢复任何整文件。
- 交付：0.121.33/协议12，Java17 `clean build --offline --no-daemon` 成功（37s），Gradle test=NO-SOURCE 如实记录。出货 `build/reobfJar/output.jar`（= `modular_machinery_reborn-0.121.33.jar`，1,629,503字节）与 build/libs 下划线命名 JAR 字节一致（cmp 验证），SHA-256 `69A3245CD49001E88379E8CA4E8F017D86FCA9406D127097CFC81D1CBEB43AEB`，归档 `releases/modular-machinery-reborn/v0.121.33/`（JAR+SHA256.txt+README，旧归档保留）。
- 增量：四个业务文件 diff 在 `_audit/third-ai-8tasks-0.121.33/diffs/`；改前指纹用交接基线 `production-sha256.txt`，改后 340 份源码哈希在 `_audit/third-ai-8tasks-0.121.33/hashes/production-src-sha256-after-D.txt`。后一批（E 组）将以此为前批起点，不把 D 组已有改动认作新增。

## 证据与方法（各任务共用规则）

每个任务独立目录 `_audit/third-ai-8tasks-0.121.33/<任务号>/`：含 `src-before/` 起点副本（取自交接 before 副本）、`run.py`（baseline→prebuild→fixed 三阶段，javac/java 命令与退出码写入日志与 exit-*.txt）、真实生产类调用。baseline 阶段用 0.121.32 构建类基线（`code-survey-0.121.0/fix-0.121.32/built-production-classes`）复现旧行为；prebuild 阶段单独编译修改后的生产源文件前置验证；fixed 阶段（构建后）以 0.121.33 完整 classes 快照（`third-ai-8tasks-0.121.33/production-classes-0.121.33`，568 class）复跑，全部通过。classpath 由 fix-0.121.32 的 P01 清单过滤旧 `review-glm-rework-0.121.31` 类目录后使用，避免旧类污染。

**首次夹具失败与更正（保留记录，未删）**：D1 夹具先在裸 JVM 直注册方块，经历 bootstrap 冻结（`Registry is already frozen`）、Not bootstrapped、`ForgeRegistries.FLUIDS`/`MOB_EFFECTS` 缺字段、LiquidBlock delegate 链共 6 次失败，最终采用"置位 Bootstrap 标志 + 固定 BuiltInRegistries/Registries 初始化顺序 + 最小替身注册表容器（无状态 probe 方块）"；D3 沿用 P06-a1 审计装置骨架。失败日志在各自 `checks-baseline.log`/`javac-*.log`。

| 任务 | 结果 | baseline | fixed | 生产文件与方法 |
|---|---|---|---|---|
| D1 P02-04 | 完成 | 6/6 复现 | 7/7 | `network/ProjectorResponsePacket.handle`：空候选位置 `continue` 跳过 |
| D2 P04-04 | 完成 | 8/8 复现 | 8/8 | `recipe/IngredientArrayParser.rejectUnknownKeys`：wrongCase 单独归类明确报错（CatalystParser 共享此方法，无改动） |
| D3 P06-10 | 完成 | 7/7 复现 | 8/8 | `block/MachineControllerBlockEntity` saveAdditional `putInt` + load 按 `getTagType` 分支 |
| D4 P10-01 | 完成 | 11/11 复现 | 11/11 | `client/MagicHatchScreen.format` + 新增私有 `compactSigned` |

## 逐项结论与验证边界

**D1（P02-04）**：baseline 复现单位置全无效候选抛 `JsonParseException: Pattern position BlockPos{x=2...} has an empty element list` 逃逸、跨位置混合在坏位置中断后续合法位置；fixed 后全无效不抛且零回调（不启动虚假投影）、混合位置合法位置全部进入 pattern、attached 坐标与 encode/decode 顺序保持、同位置混合保留 1 个合法 matcher、正常结构不变。真实 `handle`+真实 `Builder`；替身仅同步 `NetworkEvent.Context`、记录型 `DistExecutor`（不执行 client 渲染链）、注册表容器——**只验证线程回调/构建，不是真实 Forge 联网或 GPU 验收**，也不是普通玩家远程攻击面修复。

**D2（P04-04）**：baseline 复现交接样例 `Amount:8/Chance:0/NBT` 全部静默失效（entryAmount=1、chance=1.0、nbt=null），条目内 `Amount` 与 catalyst `Chance/Modifiers/Tooltip` 同样；fixed 后三类错误大小写均拒绝，消息含来源与原字段名并提示大小写敏感；正确小写 `amount=8/chance=0/nbt` 实际解析值核对、公共键（selector-tag 等 6 个）继续接受、真未知字段仍按原策略拒绝、**NBT 内自定义 `Amount` 键保留（不递归规范化）**。allowed 集合确认无合法驼峰键，无误杀。

**D3（P06-10）**：baseline 复现 40000 写 short 回绕 -25536、旧 short 负值原样读入、基础 max-threads=0 时恢复负额度；fixed 后 0/1/32767/32768/40000/Integer.MAX_VALUE 经真实 BE saveAdditional/load 完整往返、新档 TAG_INT、旧 short 正值原样、回绕负值校正为 0、max-threads=0+40000 恢复后 `engine.maxOrdinaryThreads=40000`（正额度，计数验证，未分配真实线程槽）、缺键回退 0、脚本桥 `MachineControllerAccess.setExtraThreadCount` 40000→32767/-5→0 不变。**旧 short 回绕丢失的位无法凭空恢复**，已写入代码注释与更新记录。

**D4（P10-01）**：baseline 复现灵气 -12345 绘制 `0 / 1M`、空气 -16000 `0 / 320k`、压力 -1 `0`（真实 renderBg 文本捕获 + 真实私有 format）；fixed 后分别 `-12k / 1M`、`-16k / 320k`、`-1`；正 16000→16k、0→0、-0.5→-0.500 原有显示保留；负大数 `-1.2M`、Long.MIN→`-9.2E` 不丢符号、Long.MAX→`9.2E` 不变；NaN/±Infinity 不抛异常（`Infinity`/`-Infinity`）；超 long 的 double 饱和缩写。精度边界说明：|值|>2^53 时 double 表示本就不精确。**未在服务端夹 0，未改 GuiAmounts 非负语义、容量/填充像素/服务端余额/压力契约**。

## 文档同步

项目 README 状态行与当前源码版本、工作区总览 README、`docs/en/README.md`、`docs/en/Introduction.md`（Current status 段与 Current version）、`docs/归档/版本记录/迁移日志.md` 顶部新条目、新建 `docs/归档/版本记录/更新记录-0.121.33.md`、release 索引顶部两条、`修复进度-2026-11-02.md`（状态行/下一入口/SHA-256/版本表行/新节）、`未修复事项清单-2026-11-02.md`（头部快照与 P02-04/P04-04/P06-10/P10-01 四行改"0.121.33已修，待Codex验收"）。已逐处 grep 验证命中（0.121.33 出现次数：项目README 2、工作区README 2、en README 1、Introduction 2、迁移日志 2、修复进度 5、未修清单 5、release索引 2；无残留"当前源码 0.121.32/Current version 0.121.32"行）。

## 限制声明

- 全部专项为离线原生调用验证；未实机、未启动/部署游戏、未验证真实网络/GPU/服务端调度。
- D1 混合样本通过反射从 client 回调 lambda 捕获 pattern 验证位置归属；D4 悬停 tooltip 层未运行（既有 Double.toString 路径未改动）。
- D3 的"真实普通批次可开工"以恢复计数与 `maxOrdinaryThreads` 正额度为据，未驱动完整开工 tick 链；旧 short 回绕值的恢复边界如上。
- 迁移日志/更新记录/结果文档的链接均为相对路径同级文件，已存在。
