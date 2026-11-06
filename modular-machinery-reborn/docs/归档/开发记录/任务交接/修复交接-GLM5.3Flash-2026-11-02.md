> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# GLM-5.3 Flash 小范围修复交接（2026-11-02）

这份交接将四项范围较小、结果可直接核对的任务分配给 **GLM-5.3 Flash**，完成后交回原聊天，由 Codex 独立验收。当前只完成任务分配和基线记录，没有调用 GLM，也没有开始修改本批业务代码。

**本批仅做 G1～G4。做完停止，不接续历史“直到5小时额度用完”的授权，不自行扩展其余问题。** 若某项当前已不存在，提供当前代码和行为证据，标记“无需重复修复/待复核”，继续其他独立任务；不要为匹配旧报告强行改代码。

## 1. 开始前核对

| 项目 | 本次核对结果 |
|---|---|
| 工作区 | `C:/Users/kk07H/Desktop/模组`；当前可用等价入口 `C:/mmwork` |
| 模组目录 | `C:/Users/kk07H/Desktop/模组/modular-machinery-reborn` |
| 当前源码版本 | **0.121.26** |
| 网络协议 | **12**，本批无需改变 |
| HEAD | `f94ba299e37f0975ac5255f90671f990bacfc61a` |
| 生产 Java 文件 | 340份 |
| 当前状态 | 原连续修复任务已暂停；本交接仅分配四项简单修复 |
| 正式本地归档 | `releases/modular-machinery-reborn/v0.121.26/`，JAR为1625761字节 |
| 归档SHA-256 | `8D64030E367E5404EE38E64952A63A67434A329A5D3C604B61AFD38ECDAD080D` |

**HEAD不代表实际工作区源码。** 0.121.1～0.121.26修复及文档包含大量未提交修改；不能从HEAD重新导出整文件替换当前文件。先读工作区根目录[注意事项](../../../../../注意事项.md)、[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)、[未修清单](../已完成-2026-11-06/未修复事项清单-2026-11-02.md)，再看相关报告和真实代码。

交接基线在工作区同级 `_audit/glm53flash-repair-handoff-2026-11-02/`，含本次HEAD/版本/协议、340份生产源码和四个目标文件的指纹、四文件改前副本及Git状态。开始时比较这些指纹。若有人已改了同一位置，以当前代码重新核对，保留其改动；不覆盖，不回退。

## 2. 分配清单与顺序

| 顺序 | 任务 | 对应登记 | 主要代码白名单 | 预计难度 |
|---|---|---|---|---|
| G1 | 流体省略数量时，编辑器摘要与默认值一致 | P01-03 | `authoring/RecipeDocument.java` | 低 |
| G2 | 未选NBT字段时跳过方块实体NBT读取/序列化 | P01-05 | `selection/ConstructExport.java` | 低，调用次数优化 |
| G3 | 核心线程名称为语言键时显示翻译 | P10-09 | `client/FactoryControllerScreen.java` | 低，客户端显示 |
| G4 | 工厂菜单接收并显示同步的最大并行数 | P02-07 | `menu/FactoryControllerMenu.java` | 低至中，菜单数据流 |

代码路径均相对 `src/main/java/com/reborn/modularmachinery/`。这四项可以在一个补丁批次交付，不必为四个小改动创建四个发行包。G4放最后；遇到需要重做协议、控制器状态机或其他菜单的情况，先保存该项阻塞原因，其他项正常交付。

除四个白名单文件外，允许新增独立审计夹具、修复记录，并按版本规则更新元数据和必要说明。若发现必须扩展业务修改范围，记录具体依赖交回验收，不顺手改其他系统。

## 3. G1：流体默认数量摘要

入口：[RecipeDocument.java](../../../../src/main/java/com/reborn/modularmachinery/authoring/RecipeDocument.java) 的 `summary()`。原报告：[P01](../代码勘测报告/P01-创作者工具与文件导出.md) 的P01-03。

当前真实代码只给省略数量的物品显示默认1，其他种类显示0。正式流体读取器在省略amount时默认1，所以以下合法需求实际为1 mB，摘要却显示0 mB/craft：

```json
{"type":"fluid","io-type":"input","fluid":"minecraft:water"}
```

目标：流体省略amount时，输入、输出、一过性/逐tick摘要均采用默认1。明确填写的数量照实显示。若给逐tick的默认流体展示基础总量，按1乘以配方时长计算；至少不得显示与正式默认值冲突的0。

验收：

- 用实际 `RequirementTypes`/`MachineRequirementParser` 或完整serializer解析上述需求，核对amount为1；同时调用真正的 `RecipeDocument.summary()`，不能在夹具里重写摘要算法。
- 输入/输出各测amount省略、显式1、显式250；逐tick加一个recipeTime=20的对照，核对单位和默认速率；若有总量则为20 mB。
- 物品默认1、显式能源速率40、带selector/chance的现有摘要仍正常。
- 不把气体等必填数量缺失时也伪造为1，不改实际配方成本、解析器默认值或整数字段规则。
- 摘要仍须服务于未完成草稿；不要为了生成一行文本强制完整解析整个草稿，导致原先可编辑的中间状态全部报错。

## 4. G2：按需获取NBT快照

入口：[ConstructExport.java](../../../../src/main/java/com/reborn/modularmachinery/selection/ConstructExport.java) 的 `definition()`。原报告：[P01](../代码勘测报告/P01-创作者工具与文件导出.md) 的P01-05。

当前 `definition()` 先读取实体并调用 `saveWithoutMetadata()`，之后才检查matchingNbt/previewNbt是否为空。默认两者均空，序列化结果完全不用。

目标：两种NBT选择都为空时，不为导出NBT获取/序列化实体；至少一种非空时才获取实体，对符合条件的实体调用一次 `saveWithoutMetadata()`，同一次扫描中两个输出共享该快照。

验收使用真正的 `ConstructExport.definition()` 和计数BlockEntity：

| 情况 | 同一次definition调用中的序列化次数 | 输出要求 |
|---|---:|---|
| 两种NBT选择均空 | 0 | 无nbt/preview-nbt，其他结构信息正常 |
| 仅matchingNbt非空 | 1 | 只输出所选匹配NBT |
| 仅previewNbt非空 | 1 | 只输出所选预览NBT |
| 两者都非空，选不同字段 | 1 | 两份筛选结果正确，互不污染 |
| 材料已被replacements替换 | 0 | 保持原本不导出原实体NBT的行为 |
| casing、无实体、忽略空气 | 0 | 保持原有处理结果 |

另外对照 `*`、嵌套路径、坐标字段清理及源CompoundTag不被修改；复用现有filter语义，不改路径语言。需要NBT时，prepare和save复核是**两次独立扫描**，每次都应重新获取当时的快照；不能跨扫描缓存，绕过“世界数据已变化”的保存保护。未加载位置仍必须拒绝，不能以优化为由查询未加载区块。

这项只记录序列化调用数量。没有性能基准就不能写“TPS提升”“快了百分之多少”。

## 5. G3：核心线程名称翻译

入口：[FactoryControllerScreen.java](../../../../src/main/java/com/reborn/modularmachinery/client/FactoryControllerScreen.java) 的 `threadLabel(FactoryThread)`。原报告：[P10-a4](../代码勘测报告/P10-a4-其余普通界面与职责收口验收.md) 的P10-09。

该方法注释已经承诺“名称为语言键时翻译”，当前核心线程分支却直接返回threadName。使用已有语言键 `container.inventory` 可复现；字面名称 `Literal name` 是正向对照。

目标与验收：

- 实际Language含该键时，调用真正的threadLabel，结果应与当前语言下 `Component.translatable(name).getString()` 一致；不能硬编码英文Inventory。
- Language不含该键时，普通名称和含点号的字面名称保持原文；null保持现有空字符串行为。
- 普通非核心线程仍走现有的 `gui.factory.thread` 翻译及稳定threadNumber；不能改成当前分页下标。
- 只改显示，不改变线程内部名称、ID、配方关联、存档键或服务端脚本状态；客户端本地化API不能引入服务端公共类。
- 不用永久静态缓存翻译结果；切换语言后再次求label应使用当前Language。

原审计使用真实Language/Component和真实FactoryThread，再反射调用threadLabel，不需要OpenGL。若无法建立同等夹具，明确标为“静态检查/未执行”，不要把构建成功写成显示通过。

## 6. G4：最大并行数客户端镜像

入口：[FactoryControllerMenu.java](../../../../src/main/java/com/reborn/modularmachinery/menu/FactoryControllerMenu.java) 的 `ContainerData.get/set`、`DATA_MAX_PARALLELISM`、`maxParallelism()`。原报告：[P02-b](../代码勘测报告/P02-b-菜单与命令.md) 的P02-07。

当前get发送 `machine.factoryParallelCeiling()`，set却忽略DATA_MAX_PARALLELISM。客户端控制器并行上限初始为1，结果 `maxParallelism()`仍返回1，屏幕可能隐藏并行信息。DATA_MAX_THREADS已有独立处理，不要把两项混用。

目标：菜单接收到该字段时保存有效的客户端镜像；客户端getter读取新值。服务端仍取当前权威上限，不得用陈旧的开窗快照覆盖实际加工上限。

验收：

- 使用实际菜单ContainerData，模拟接收最大线程5和最大并行128，两项getter分别返回5和128。
- 同一客户端菜单连续接收128→4→1，getter分别更新，不能保留历史最大值；新开客户端菜单未同步时维持安全缺省。
- 服务端权威上限改变后，发送侧get读取新的实际值；客户端set不应回写服务端配方/并行控制状态。
- 检查负数/0接收的现有下限语义、失效菜单保护、普通物品转移不受影响；避免初始化顺序导致data访问空machine。
- UI消费者使用的仍是同一菜单getter；若执行了屏幕判断，用它核对并行行的显示条件。

**本项只补已存在的同步字段接收。** P02-02的有符号short截断是另一个未修问题；128/4/1的镜像通过不能声称40000等大值网络同步已修。这里不要增加消息、改变索引/协议或拆分所有ContainerData字段。若小修必须触及繁重的控制器公共类，先报告原因而不是重构它。

## 7. 测试材料和如何使用

已有材料在工作区同级 `_audit/code-survey-0.121.0/`：

| 任务 | 历史夹具与相关入口 |
|---|---|
| G1/G2 | `P01/CreatorAuditCheck.java`；流体摘要复现、CountingEntity计数和导出控制 |
| G3 | `review-p10-a4-rest/RestGuiCheck.java`；辅助 `review-p10-a2-layout/LayoutCheck.java` |
| G4 | `P02-b/MenuAuditCheck.java`；实际菜单ContainerData的最大线程/并行镜像对照 |
| 当前可用依赖 | `fix-0.121.26/classpath.txt`；其中可能含旧生产class目录，使用时必须过滤/替换 |
| 当前改前生产类 | `fix-0.121.26/production-classes/`，仅作0.121.26基线，不得冒充修复后运行类 |

**历史夹具的PASS常常表示“成功复现了缺陷”。** 例如CreatorAuditCheck断言默认流体摘要为0、默认导出序列化1次；RestGuiCheck断言线程返回未翻译语言键。直接运行它们得到PASS，不是修复验收。应在自己的审计目录建立对应“正确行为”断言，对同一输入证明旧类不满足、新类满足。保留原夹具，不为通过而改写旧证据。

旧run.py常锁定339份0.121.0源码指纹，当前340份不能直接套用。可以复制必要辅助代码到新目录，调整来源和运行断言；不能删除旧脚本的哈希校验后覆盖原日志、把339改成340就算新验收。

新证据放 `_audit/glm53flash-repair-<实际版本>/G1/` 等各自目录，至少保存：输入/期望/实测、命令、stdout/stderr、退出码、源码指纹、加载的生产class来源。失败夹具日志保留并注明是夹具错误还是生产缺陷。测试必须调用实际生产方法，不能只搜方法名、比字符串补丁、运行自行抄写的替代算法。

离线UI引导材料（仅按需要使用，不要求弱模型重写整套UI框架）：

- `review-p10-a1-input/key-agent.jar`
- `review-p10-a1-input/native/`
- `review-p10-a4-rest/ui-send-agent.jar`、`UiSendAgent.java`
- `_audit/remaining-0.117.0/token-agent.jar`

这些agent用于离线夹具的API初始化，不是游戏实测。不得修改或插桩被验收的生产方法来制造正确结果。若沿用绘图捕获夹具，说明捕获的是原生方法调用而非真实GPU画面。

## 8. 版本、构建与发布文件

执行修复时先重新核实实际版本。如果仍为0.121.26且没有别的批次占用，则这四项作为 **0.121.27** 一批；否则依根目录规则递增当前patch，不能倒退/复用现有归档。

Java17当前路径：`C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`。在模组目录执行，并保存完整日志：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot'
./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true
```

确认Java版本、build成功、reobfJar实际执行。Gradle test=NO-SOURCE意味着没有Gradle测试，不能据此声称四项行为已测试。构建完成后才拷贝完整生产classes到本批只读快照，再用它优先于旧类目录复跑专项；检查实际类加载来源，不要把旧0.121.26放在修复类前面。不要在clean/build删除classes时并行运行依赖同一可变目录的测试。

按根目录要求同步gradle.properties、实际元数据、项目/工作区README、对应中英文状态、版本日志、release索引；新建版本归档，核对JAR版本、大小与SHA-256，保留旧归档。构建输出通常为 `modular_machinery_reborn-<版本>.jar`，归档命名通常为 `modular-machinery-reborn-<版本>.jar`，两者应字节一致。

若只能完成部分任务，不写“四项全部完成”。如专项或构建失败，应保留当前代码和日志、报告未完成步骤；不得把失败包当正式归档。旧协议12、约定ME名称、教程不写参考了MMCE等约束继续有效。

## 9. 不分配给GLM的范围

本批不处理P05通用事务回滚/外部端口异常补偿、P02完整int协议、P03全局重载原子性、P06线程状态机/已付款恢复/动态升级实例、P07旋转匹配与自动搭建回退、P08共享供应器分组、JEI/OpenGL状态管理。它们需要更强的跨模块推理和独立回归，留给Codex后续处理。

不部署或启动游戏，不接触用户整合包、世界存档，不暂存/提交/推送、上传GitHub或创建Release。禁止git reset/clean、整仓覆盖、恢复HEAD版本、修改历史审计证据、批量格式化四项之外文件。无需为了“整理工作区”处理其他AI的未提交改动。

**旧报告需复核的例子：P04-10暂不分配修复。** 本次实际读到当前 `MachineRecipeJS.magic()` 已在创建MagicRequirement前对WILL使用Locale.ROOT转小写，且HEAD也已有该行。旧报告对构造器拒绝大写的复现不能单独证明当前typed入口仍有问题。后续应运行实际入口再决定更正登记，不能直接按旧建议再次修改构造器。本交接未据静态发现直接更改累计未修数量。

## 10. 交付给Codex的包

完成后新增 `docs/开发/GLM修复结果-<实际版本>.md`，使用以下结构：

| 任务 | 状态（完成/部分/未做/当前不可复现） | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| G1/P01-03 |  |  |  |  |  |
| G2/P01-05 |  |  |  |  |  |
| G3/P10-09 |  |  |  |  |  |
| G4/P02-07 |  |  |  |  |  |

另外列出：起止版本/HEAD、四项改前/改后文件哈希、此次增量diff入口、所有构建/测试命令与退出码、类加载来源、正式包路径/大小/SHA-256、是否有夹具失败、是否触及白名单之外文件。区分本次修改和接手时就存在的修改，不能把整个HEAD diff全部认领为自己的成果。

GLM自测通过后状态统一写 **“待Codex验收”**。不要修改原始勘测报告为“已验收”，不要自行调整122项总账或解除主任务暂停。可以在自己结果文档记录自测结论。版本说明也须明确“GLM交付，Codex未验收”。

Codex后续验收流程：

1. 与交接基线比较，检查白名单及保留既有修改；对任何额外改动逐一解释。
2. 逐项重新读取生产代码，排查空值、客户端/服务端边界、草稿容错、并发/同步和NBT快照生命周期。
3. 独立执行正确行为断言和组合对照，检查旧bug断言是否被误当成功；确认运行的是交付版本classes。
4. 核对版本、构建/reobf、归档字节/哈希与相关回归；未实机的继续标明未实机。
5. 验收通过后才更新累计修复账本和未修清单；不通过则给出具体返工点。是否再修其他任务由用户决定。

## 11. 可直接复制给GLM的启动语

> 请执行 `C:/Users/kk07H/Desktop/模组/modular-machinery-reborn/docs/归档/开发记录/任务交接/修复交接-GLM5.3Flash-2026-11-02.md` 分配的G1～G4四项小范围修复。先核对当前Git、实际源码、版本和交接指纹，保留全部已有未提交改动。每项按实际生产行为复现和验证；旧夹具的“成功复现bug”不能当修复通过。按根目录版本/构建归档要求交付，禁止部署游戏、暂存、提交或推送，禁止扩展任务。做完保存结果文档与证据，标记待Codex验收并停止；若某项已修或无法完成，如实记录，不用虚构通过。
