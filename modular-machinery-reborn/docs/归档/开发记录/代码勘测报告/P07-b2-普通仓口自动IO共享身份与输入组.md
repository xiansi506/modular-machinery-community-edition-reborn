# P07-b2：普通仓口自动IO、共享身份与输入组

日期2026-10-31（北京时间）。0.121.0/协议12，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。只勘测与离线复现，生产代码/版本未改，保留全部既有修改；未正式构建、启动游戏、部署、暂存、提交或推送。

新增 **P07-14（P2，自动IO查未加载邻居走强制FULL区块请求）、P07-15（P2，输入组配置生效后控制器仍使用旧组集合混料）**。P07累计01～15全部未修。P07-b2离线完成、实机待验；主部分仍进行中，总进度6 / 12。

## 执行范围与边界

重编译75个当前生产源文件、5个夹具，共80条源码指纹，执行AutoIoGroupAuditCheck.main五组PASS。实际执行普通仓口serverTick/HatchAutoIo、当前EnergyAccess、HatchGroupUpdatePacket.apply、Level查块实体与比较器通知、结构匹配/端口收集、绑定普通和工厂控制器，以及HatchCollection和实际配方事务。旧版7个对应源码、当前Level/LevelChunk字节码及辅助agent源码/JAR另存11条指纹；依赖类指纹复用并校验b1记录，没有修改旧夹具。

World继承ServerLevel并Unsafe分配，提供内存状态/实体/原生RecipeManager；比较器通知调用原始Level实现。邻接容器只是明确的能力适配夹具，背后用真实Forge ItemStackHandler/EnergyStorage。未运行原版箱子、漏斗、第三方管道或完整Forge事件生命周期。

区块探针执行实际Level.getBlockEntity/getChunkAt和三/四参数getChunk调用链，正确设置Level.thread为当前线程，在四参数提供器边界截获请求并返回Unsafe的ProbeChunk空壳；没有真实ServerChunkCache、生成、磁盘加载或耗时测量。分组实验用Unsafe的ServerPlayer和HatchGroupMenu提供本次apply确实读取的level/position/containerMenu/hatch字段，真实身份/距离检查通过；未运行网络传输/排队、真实玩家登录或GUI，不是越权复现。

## P07-14：自动IO会强制请求未加载的邻居区块（P2）

**最小复现：** 输入物品、输出物品、输出能量三种实际仓口分别置于(15,64,8)，世界明确报告邻接(16,64,8)所在chunk(1,0)未加载；能量仓提供100 FE保证进入输出扫描。实际serverTick三者均通过原生Level.getBlockEntity向chunk(1,0)发出**FULL/create=true**请求。同一世界tick重复调用无新增请求，是lastTick保护的有效对照。

**原因：** MachineHatchBlockEntity.tickItemInput:282、tickItemOutput:305、tickEnergyOutput:336直接getBlockEntity，没有isLoaded/hasChunkAt检查。当前Level字节码先检查高度/线程，再getChunkAt；后者走FULL状态，默认请求create=true。实际探针跑到了该参数边界，故不只是按方法名猜测。形成结构时ControllerPortCollector反而已有hasChunkAt保护。

**影响边界：** 普通仓口可能让相邻本不应加载的区块进入同步FULL请求；这不是已测得的生成次数、TPS下降或永久强加载。真实区块提供器/票据/边缘状态需要游戏复核，报告不把探针返回空壳说成真的生成了新区块。旧版输入/输出/能量邻居路径也直接getTileEntity，尚未完整运行1.12.2世界读取链，因此不宣称旧版从无此问题。

**修复方向：** 邻居能力查找前验证已加载，并避免加载与实际lookup之间绕回强制获取；保持负坐标、世界高度、同tick保护及正常已加载邻居行为。应在真实区块边界观测邻块卸载后是否被意外加载，而非只测试静态hasChunkAt方法名。

## P07-15：输入组界面配置没有使控制器的分组快照失效（P2）

**实际流程（普通/工厂各一次）：** 用真正绑定机器ID的控制器和两个实际输入仓、一个输出仓形成空闲机器。初次两输入均通用，配方需1煤+1钻石产1石头。通过实际HatchGroupUpdatePacket.apply将两仓分别设为隔离组1和2，再放入煤与钻石。包的当前菜单、世界方块实体身份、8格距离和canGroupInput门槛均实际通过，hatch.effectiveGroup确实为1/2。原生setChanged/Level比较器通知也执行，但没有结构变更标记。

紧接的控制器tick开启**新批次**，仍把煤与钻石混用并产1石头。补回相同材料、仅显式StructureChangeTracker.mark输入仓位置，下一tick重新收集组后不再开工：煤/钻石各1保留，石头仍1。普通控制器和工厂结果相同。

**原因：** setGroup:620–632只修改字段/setChanged；实际包apply:69没有补输入拓扑/结构失效通知。ControllerPortCollector在结构重查时把effectiveGroup作为整数拷入HatchCollection，后者withGroup按旧整数过滤；改变物品本身不会重建组元数据。实际普通/工厂选择新配方调用的仍是该缓存集合。

**影响边界：** 玩家已经设置隔离却仍可能在结构重新检查前混料；不是已开工任务冻结原组的合法行为，也不是网络权限绕过。当前控制器默认周期50重查仍是纠正路径，本批没有重放50次，仅用显式重查证明；不写成永久绕过。P06-15是升级物品改变后修改器缓存不失效，触发数据和缓存字段不同，故这里登记单独编号。

**1.12.2对照：** PktGroupInputConfig只调setGroupId/setGroupInput，旧TileColorableMachineComponent相应setter只markChunkDirty；旧控制器也具有周期结构/组件更新。没有运行完整旧线程调度，不能声称本项必然是全新回归。当前实际行为与用户配置即时可见不一致已被上述新批次对照确认。

**修复方向：** 分组改变后使相关控制器下一次新开工前重建输入元数据；保持既有付费加工如何锁组的约定，覆盖通用→隔离、改ID、取消隔离、共享仓口/多个控制器、进行中任务与工厂线程。改setter或发拓扑通知都要验证确实到达所有使用者。

## 已通过的对照与优化判断

- 输入tick从真实Forge邻库抽入带batch标签64煤和7钻石，总数71且标签保留；能力按相反方向查询。同世界tick不重复执行，成功后的输入节流生效；相邻同类仓口跳过，不凭空在总线间循环搬料。
- 输出11煤到上限5的目标，得到目标5/源6；清空目标后下一tick因inventoryChanged立即再推5，剩源1。拒收目标不丢物。这是正常契约处理器的守恒验证，不能泛化为任意违反IItemHandler契约的模组也安全。
- 能量输出一个tick的128 FE总预算在两个目标间分为32+96，源1000→872；同tick不重复，下一tick源744。显式短执行目标模拟128、实收40时，88确实退回本仓，源960/目标40；可选Flux/DE后端仍P09，旧未使用HatchAutoIo.pushEnergy不作为实际ticker入口。
- 实际HatchCollection的通用端口对组可见、组号升序候选、tag/group筛选次序一致、null selector保留所有；普通输出通过Builder.add按家族归一为通用，即使传入99也不会被输入组隔离。
- 同一个实际物品handler以两个标签加入集合，库存10煤无法让实际canStart通过11煤成本，库存不变；事务按身份共享有效。P05-08气体别名问题保留旧编号；不同wrapper共享后端、ME/供应器远端别名还需P08，不能用本例宣称所有共享能力都安全。
- 5–60 tick自适应节流及方向扫描与旧实现对照；本轮没有证实需要新增性能优化编号，不把“改集合/少写循环”当已测得收益。P07-14只确认强制请求参数，不虚构TPS数字。
- 一次夹具javac误用了不存在的ItemRequirement.output双参数重载，改用实际四参数后通过；编译错误日志保留。初次四组通过日志另保存，之后补能量/输出组归一对照再运行五组。失败属于夹具，不是生产故障。

## 交付与接续

`_audit/code-survey-0.121.0/P07-b2/`保存源夹具、run.py/编译运行参数、最终checks.log、初次日志、80条源指纹、11条参考指纹、12行责任表及validation.txt。未实施修复。先处理会改变世界加载边界的P07-14和混料P07-15，并与已有失效通知问题联合回归。

**P07-b2离线完成，实机待验。** 继续 **P07-c1：自动搭建的材料计划、放置/失败与回滚**；其余工具物品/世界责任覆盖随后收口。P07仍未完成，主进度6 / 12。

## P07-c1后续证据勘误

本批原始Forge总线也处于默认shutdown；虽注册NeighborNotifyEvent观察器，“未看到事件”的负向结果没有正向校准，现撤回其运行证据效力。真实packet.apply、setGroup、原始Level通知、普通/工厂新批次混料及显式重查对照仍实际执行；生产setter无拓扑通知和整数分组快照的静态链保持。P07-c1尝试显式start和校准，遇BlockEvent缺少ModLauncher无参变换而无法完整派发，未把失败写成游戏故障。当前P07-15仍为实际缓存行为复现+静态调用链，活跃总线端到端验证待实机；后续应复核事件相关邻居布置。
