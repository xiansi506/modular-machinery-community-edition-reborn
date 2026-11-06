# P04-b：物品、NBT、备选原料与催化剂

日期：2026-10-29。基线 **0.121.0 / 协议 12**，Git HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。本轮只勘测、复现和记录，未修改业务代码，未构建正式包、启动游戏、部署或提交推送。

新增 **P04-02～06**：前两项直接影响执行副本，P04-04 是字段校验问题，P04-05 是沿袭旧版的匹配缺陷，P04-06 是 typed builder 的非有限概率校验遗漏。另扩展 P03-09 和 P04-01，不重复编号。没有确认普通玩家远程上传配方或越权执行脚本的路径。

## 范围与方法

实际检查 `RequirementTypes.ItemType/IngredientArrayType`、`LegacyIngredients`、`ItemRequirement` 的字段与纯匹配、`NbtPredicate`、`LiteralNbt`、`IngredientArrayParser/Entry/Requirement`、`CatalystParser/Requirement`，以及 `MachineRecipeJsonBuilder` 的物品/催化剂创建方法。追踪 `MachineRequirementParser.copy → MachineRecipe.executionCopy` 及控制器/工厂调用契约；`MachineRecipeJS` 仅检查参数转发，没有运行 Rhino。

当前源码的 18 个相关核心类在独立审计目录重新编译，包括实际 `IngredientIo` 和 `ResourceTransaction`。夹具运行真实解析器、原生注册表/标签绑定、物品仓、需求匹配、执行副本和输出插入；测试专用物品通过内存中的 Forge 注册表登记。沿用本地依赖 classpath/离线启动 agent，未启动 Forge 游戏环境。

| 类型 | 本轮核对的入口与默认值 |
|---|---|
| item | item 必填；amount 默认 1；min/max 缺省回退 amount；chance 默认 1；NBT 缺省不限制；nbt-display 只在 nbt 存在时读取 |
| ingredient_array_input | items 非空，仅输入；需求 amount 默认 1，条目 amount 优先；每条数量限制 1～64；组 chance 默认 1；组 NBT 缺省无条件 |
| catalyst | 复用备选条目语法；modifiers/tooltip 默认空；仅输入、默认固定一份、可缺省，不会仅因没有催化剂而阻止合成 |
| 旧 NBT 模板 | 数字字符串保留类型与比较符；普通字符串为忽略大小写的整串正则；compound 子集，list 要求不同元素分别满足 |
| literal NBT | 保留 SNBT 类型、字面字符串、数组、列表顺序和空容器；compound 仍按子集匹配；此批已验证 item 的 nbt/nbt-display 路径 |

不在本批完成的范围：多个需求共享资源的事务、完整并行规划、运行中的脚本变换、真实可选模组物品、JEI 交互、专用服务器和在线重载。分别归 P05/P06/P09/P10/P11。

## P04-02：原料数组的标签在复制后仅保留首个成员（P2）

**分类与确认：** 功能/数据一致性问题，离线原生复现。作者在 `ingredient_array_input.items` 使用包含多个物品的标签即可触发，不需要异常输入。

**原因与位置：** `recipe/RequirementTypes.java:453-498` 的 writer 写出每个条目的所有匹配栈，但 reader 只记录 `match == 0` 的栈，随后用 `IngredientArrayEntry.ofItem(first, ...)` 重建单物品 ingredient。其余栈虽然被读走，却没有加入结果。`MachineRecipe.executionCopy`（631-634）复用此读写过程，因此影响服务端加工，不只影响客户端显示。

**最小复现：** 在真实物品注册表绑定 `#audit:alternatives = [stone, dirt]`，解析：

```json
{"type":"ingredient_array_input","io-type":"input","items":[{"item":"#audit:alternatives","amount":2}]}
```

原定义的该条目有 2 个匹配成员；`executionCopy()` 后只有 1 个。在原生输入仓仅放第二个成员 2 个，实际 `MachineRecipe.canStart` 对原定义返回 true，对执行副本返回 false。夹具先读取实际标签展开顺序，再取第二项，没有假定注册表排序。

**对照与边界：** 普通 item 标签通过 `Ingredient.toNetwork/fromNetwork` 保留两个成员；catalyst 的标签往返也保留两个成员。本地 1.12.2 `RequirementIngredientArray.deepCopyModified` 逐条复制 `ChancedIngredientStack`，其 `copy()` 对 ORE_DICT 保留矿词名称与数量，没有只取首个成员的这条路径。这里的现代标签对应旧矿词，不要求两代平台的标签机制完全相同。

**建议：** 统一用能够保留整个 Ingredient 的读写契约，或把所有读回的匹配栈重建为多候选 Ingredient；不要为了修复显示名称而丢弃匹配集合。明确是否需要保留标签身份或仅冻结该次候选集合，并评估协议版本。回归含单/多成员、空标签、普通条目与标签混合、选择标签和真实控制器/工厂开工。

## P04-03：输出执行副本使用共享标签协议，会丢掉服务端 NBT（P2）

**分类与确认：** 输出数据一致性问题，原生复现到实际仓口插入。触发条件是输出物品实现了会过滤数据的合法 `getShareTag`，不是所有物品都会出现。

**原因与位置：** `recipe/RequirementTypes.java:318,336-338` 用 `FriendlyByteBuf.writeItem/readItem` 复制输出栈；Forge 的该协议用于同步共享数据，默认 writer 使用 limitedTag，调用物品 `getShareTag`，reader 使用 `readShareTag`。`MachineRequirementParser.copy`/`MachineRecipe.executionCopy` 将这套同步表示当作服务端完整复制。当前依赖的 Forge 补丁明确区分完整标签与共享标签，不能把默认 writeItem 视为无损 ItemStack.copy。

**最小复现：** 夹具在内存中注册一个测试 Item，其 `getShareTag` 保留 `client_value` 并去掉 `server_value`。经真实 item JSON 解析创建输出 `{server_value:42,client_value:"shown"}`，原配方栈字段完整；实际 `executionCopy()` 后缺少 server_value。再调用副本的实际 `apply` 插入 `ItemStackHandler`，产物同样缺少该字段，原定义中的 42 没有被修改。

**旧版与边界：** 本地 1.12.2 `RequirementItem.deepCopyModified:146-171` 使用 `required.copy()` 和 tag.copy()，没有借助客户端共享标签协议完成服务端复制。本轮测试物品是故障契约夹具，使用临时解冻的内存注册表，不是用户安装的某个第三方模组物品；没有确认具体第三方物品或 capability 数据损失，也没有实机测试。

**建议：** 为执行副本建立完整 ItemStack/需求复制契约，把服务端复制与客户端展示同步分开。不能只将 writer 改成 full-tag 而仍无条件调用可能有专用语义的 readShareTag；需要成对核对读取方式与物品能力。回归包括普通物品、过滤/封装 shareTag 的物品、literal 输出、显示 NBT 覆盖，以及原定义与不同加工副本相互隔离。

## P04-04：字段检查忽略大小写，读取却区分大小写，错误字段静默失效（P2）

**分类与确认：** 配方作者输入校验/语义问题，ingredient_array_input 和 catalyst 两条实际解析入口均复现。

`IngredientArrayParser.rejectUnknownKeys`（322-335）将每个键转为小写再对允许列表检查；`readEntryAmount`、`readChance`、`readNbt` 却只按原样的 `amount/chance/nbt` 查询。于是下面字段都通过“已知字段”检查，但没有被读取：

```json
{"type":"ingredient_array_input","io-type":"input","items":[{"item":"minecraft:stone","Amount":8}],"Chance":0,"NBT":{"locked":"yes"}}
```

实际结果为数量 1、概率 1、NBT 条件 null。把 type 换成 catalyst 结果相同；这意味着作者写下的数量、概率和匹配限制全部没有按预期应用。Canonical 小写字段的正常读取不受该复现影响。

**旧版与建议：** 旧版 JSON 也是按大小写敏感字段名查询，不能称这里丢失了旧版支持的大写功能。当前版新增了明确拒绝未知字段的校验，却放过了这些错误拼写。最小修复是按规范字段名严格检查并提示大小写；如果决定支持大小写别名，应在统一规范化后读取，并拒绝 `amount` 与 `Amount` 同时出现等冲突。测试覆盖根字段、条目和 catalyst 扩展字段。

## P04-05：重叠的 NBT 列表条件采用贪心分配，匹配依赖实际列表顺序（P2，沿袭旧版）

**分类与确认：** 复杂 NBT 匹配的继承缺陷，原生 `ItemRequirement.inputMatches` 已复现；不是新的移植回归。

模板 `{"list":[{"a":">=1"},{"a":"1"}]}` 对实际 `{list:[{a:1},{a:2}]}` 返回 false，对仅交换元素顺序的 `{list:[{a:2},{a:1}]}` 返回 true。两个实际列表都存在合法的不同元素分配：宽条件使用 2，精确条件使用 1。夹具独立穷举两个条件的不同索引分配，确认并不是“只有一个元素被同时匹配两次”。

**原因与对照：** `recipe/NbtPredicate.java:291-320` 给每个条件选择第一个能匹配的实际元素并立刻移除，遇到后续精确条件失败时不会重新分配；这不满足类注释中的无序多重集语义。本地 1.12.2 `NBTMatchingHelper.matchList:81-102` 使用同样贪心流程，故这是沿袭的局限。普通不重叠条件、类型检查和相同条件的重复元素计数已有正常验证。

**建议：** 若要完善无序匹配，采用能够找出不同元素分配的匹配算法，保留数字类型/比较/正则/compound 子集规则；仅将宽条件排序靠后不能保证一般情况正确。修复会使部分旧来失败的输入变为成功，应在兼容记录说明。回归包括重叠范围、正则与精确字符串、嵌套 compound、重复元素、不足元素和所有排列。没有进行性能基准，不声称更复杂算法一定更快。

## P04-06：部分 typed 概率入口接受 NaN，并静默恢复为必定发生（P3）

**分类与确认：** 边界输入校验问题，实际 Java builder → JSON → serializer 已复现。仅涉及非有限参数；普通有限概率的往返检查通过。

`MachineRecipeJsonBuilder.addChance`（400-410）只检查 `<0`/`>1`，没有检查有限性。NaN 两个比较都为 false，后续 `<1` 也为 false，于是删除 chance 字段。实际 `.itemOutput("minecraft:stone",1,3,Float.NaN)` 生成无 chance 的输出，解析为 1；`.catalyst(...,0.25f).catalystChance(Float.NaN)` 也把原来的 0.25 改成 1。

`MachineRecipeJS.itemOutput/catalyst/catalystChance`（274-275,314-325）直接向这些 builder 方法传参；同类 `.chance(float)`（60-62）已有 `Float.isFinite` 检查。本轮没有执行 Rhino，因此没有把 Java 参数复现写成完整 KubeJS 脚本复现，也没有把 NaN 当成合法标准 JSON 数字。

**建议：** 在共同概率写入方法先检查 `Float.isFinite`，再检查范围，并保持 1 清除旧字段、0 明确写出的既有语义。补 NaN、正负无穷、边界 0/1、覆盖已有概率和实际 Rhino 参数转换回归。

## 已登记问题的扩展与正常路径

- **P03-09 扩展，不新增编号：** item.amount 和数组条目 amount 的 `4294967297` 均经实际解析变成 1。相关入口为 `RequirementTypes.java:256-258`、`IngredientArrayParser.java:201-210,246-257`；catalyst 复用后者。完整字段精确数值校验需要一起修复。
- **P04-01 扩展，不新增编号：** 长度为 32,768 的普通字符串放入 item NBT 模板时，加载成功，创建执行副本因 NBT source 的 `writeUtf` 上限而失败。此问题不限于上一批的 tooltip/options。catalyst 文本及 NBT 也有同类写入位置，本轮没有逐个运行超长输入，不把它们逐项算作新复现。
- legacy 模板的数字类型、长整型大于 2^53 的比较、正则大小写、compound 子集，以及非法裸 number/boolean、混合列表、空旧列表和非法正则已实际检查。NBT 重复列表要求不同实际元素，不会用一个 1 满足两个 1。
- literal 路径实际保留 byte/short/int/long/float/double、三种 typed arrays、空列表/compound、类似数字和布尔的字符串、含正则符号的字面字符串；物品输入/输出/display NBT 经复制后保留这些值。literal compound 保持子集规则，literal list 则精确比较；这两者不同不是此次发现的缺陷。错误的 literal envelope 和损坏的 SNBT 被拒绝。
- 普通 item 标签、catalyst 标签候选和 modifier/tooltip 元数据，以及具体物品备选条目的数量/概率往返正常。实际 1..3 输出仍在范围内，具体备选可从仓口正常支付。未把一次合法输出视为对 RNG 分布的统计验证。
- 空催化剂、输出方向的原料数组、写到条目里的 chance、未知物品、旧 @meta 和 any:fuel 均明确拒绝；不把当前文档已说明的限制当成新移植缺口。现代燃料功能走具体物品和 fuel-time 选项。
- `nbt-display` 单独存在而没有 nbt 时被忽略，是源码明确保留的旧规则；typed previewNBT 会补空模板。JSON 物品数量 0 被归一为 1、反向范围 10..1 被归一为 10..10，而 typed builder 拒绝这类参数；本轮将其记录为校验/兼容性观察，没有将旧数量限幅重复报告成漏洞。
- 催化剂默认可缺省且保持一份，并行设置为 8 后仍为 1，符合已记录设计。没有把可选催化剂当成必须拥有的主原料，也没有依据 generic 并行开关另报一个重复问题。

## 跨区线索、材料与收口

`IngredientArrayRequirement.servedCopies` 逐条从未扣减的输入仓统计候选数量，重叠备选可能重复计算；`reserve` 又使用共享事务副本扣减。应在 P05 结合真正的并行规划、多个重叠需求和失败回滚验证最终行为。本轮没有完成组合场景，不声称已证实复制资源或实际错误开工。ItemRequirement 的 checker/modifier 解析结果在运行期的行为仍归 P11/P05。

复现材料位于项目同级 `_audit/code-survey-0.121.0/P04-b/`：`ItemNbtAuditCheck.java`、`run.py`、`checks.log`、`javac.log`、`source-sha256.txt`。最终运行退出码 0；日志中的 PASS 包含对当前错误行为的成功复现，**不表示问题已修复**。夹具仅写审计目录并修改其独立进程的内存注册表，没有修改玩家文件。

优先修复 P04-02 和 P04-03 的执行副本完整性，再修字段/非有限数校验；P04-05 需连同继承语义和匹配成本评估。所有发现尚未修复，真实控制器/工厂、具体可选模组物品、专用服务器、实际脚本和 JEI 仍待验。

P04-b 完成离线勘测，P04 主部分尚未完成，总进度仍为 3 / 12。下一批是 **P04-c：流体、气体及能源需求的解析**；本轮不提前开始。

## P07-a1补充：结构方块实体也受P04-05影响

原生熔炉的两个库存Count为1、2，实际MachinePattern.matches对Items模板Count>=1/Count=1失败，换成2、1则通过；两个排列都存在合法的一对一分配。共享NbtPredicate贪心根因未变，沿用P04-05，不新增P07缺陷编号。实测、旧版静态对照及真实世界边界见[P07-a1报告](P07-a1-静态结构朝向与NBT匹配.md)。
