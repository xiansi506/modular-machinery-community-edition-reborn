# P09-d：Flux、龙之研究与Jade能量契约验收

2026-11-01；基线0.121.0 / 协议12，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。只勘测与保存验证记录，未改生产代码、版本、构建配置，未正式构建/reobf、部署、启动游戏、暂存、提交或推送。

**结论：本批5组实际API/原生存储/输出tick/Jade数据映射验证通过，无新增缺陷编号。** 补齐原P09静态报告在可选能源真实接口与Jade long显示方面的证据。P09还需公共魔法配置边界与覆盖责任收口，主进度仍8/12，不能提前记9/12。

## 实际范围和依赖

生产入口：OptionalEnergy及EnergyAccess必要路径、EnergyPort/LongEnergyStorage，MachineHatchBlockEntity.tickEnergyOutput，以及HatchEnergyStorageProvider的Server/Client。Jade其余四文件仅追踪边界和职责：插件注册、流体服务端视图、控制器信息服务端/客户端、FluidStorageSuppressor。**未宣称Jade所有tooltip布局与流体组合通过**，其客户端混排行/重复处理等由P10补验；控制器energySnapshot计算本身仍沿P06责任，不重做整套调度测试。

原生API来自整合包实际安装的 **Flux Networks7.2.1.15、Brandon's Core3.2.1.302、Draconic Evolution3.1.2.621、CodeChickenLib4.4.0.528** 原始JAR；Jade使用现有映射依赖 **11.13.3**。原始JAR只读复制到独立审计目录的ASCII文件名，不修改实例。`native-origins.json`记录原始路径，验证复制哈希一致，元数据保存mods.toml；依赖均不上传。

运行真实IFNEnergyStorage/FluxCapabilities、IOPStorage/CapabilityOP、FNEnergyStorage、OPStorage，以及实际Jade EnergyView、ClientViewGroup和DisplayHelper。DE当前集成通过Brandon的OP契约接入；实际DE TileEnergyPylon字节码确实引用CapabilityOP.OP，已保存。**没有实例化/运行DE能源核心或反应堆**。原始依赖未经Minecraft方法名重映射，因此仅运行本批可在当前环境调用的API/原生存储数值方法，不运行其NBT或MC方块生命周期方法。

## 执行和边界

工件：项目同级 `_audit/code-survey-0.121.0/review-p09-energy-jade/`，`EnergyJadeCheck.java`、`run.py`、`checks.log`、`javac.log`、`java.args`、`classpath.txt`、`exit-code.txt`，最终5条PASS，退出0。339生产源指纹与独立全量编译时相同，加1夹具共340项；关键运行类断言来源为review/classes，不用残留build/classes。字节码、依赖原件/复制件指纹和文档验证另存。

离线注入：Bootstrap后Unsafe创建ModList，仅indexedMods包含fluxnetworks/draconicevolution供实际isLoaded读取；不替换OptionalEnergy.load/add/Handle/find/External。邻接方块实体、世界和输出仓为Unsafe壳，能力由夹具实体提供真实native storage；直接反射调用实际私有tickEnergyOutput，不是完整仓口serverTick。配置使用内存CommentedConfig和实际ForgeConfigSpec，缺项自动补默认值的日志保留，未写玩家配置。Jade服务端组及客户端映射实际执行，没有绘图或OpenGL。

第一次运行缺少Quack CrashLock，CapabilityOP初始化失败，本项目按预期捕获LinkageError退化，夹具随后访问未初始化类失败；保存在`fixture-missing-quack.log`。补入原CodeChickenLib包含的Quack类后重新运行通过。此为离线依赖缺口，不登记成集成故障，也不把降级警告当作OP通过。未使用手写API替身绕过问题。

## 五组实际行为

| 组合 | 结果 |
|---|---|
| 实际OptionalEnergy.Handle与两类原生API | 能力注册引用匹配；Flux模拟接收80亿不改余额，实际接收后OP读同一80亿，FE夹到INT_MAX；输入端拒绝外部抽取；modifyEnergyStored(MIN)移除80亿并返回非负幅度，MAX直接修改受容量限制；满仓再收0；invalidate令已有LazyOptional失效 |
| find/External反射到原生存储 | 同时有Flux/OP/FE时只选Flux，装满15亿容量，不触OP/FE；关闭Flux配置后不再暴露Flux能力，find改选OP，实际收60亿；未查询FE |
| 真实输出仓单次跨方向预算 | 原缓存90亿/输出预算40亿，两侧分别原生Flux容量15亿及原生OP；实际tick后源50亿、目标15亿+25亿；均未走FE，无双通道重复输出 |
| 无可选能力时退回FE | find返回null，实际tick只查询一次FE，源100变0、目标100 |
| Jade server→native EnergyView→client | Cur=9007199254740993（2^53+1）、Capacity=LONG_MAX以NBT long保留，最终overrideText精确为9,007,199,254,740,993 / 9,223,372,036,854,775,807 FE；ratio保持0..1，Server优先级-1000 |

Jade自己的缩写数值/ratio使用浮点；本项目overrideText另从NBT long格式化，因此本批精确文本没有双精度取整。该结果不表示像素条有整数级精度，也不表示完整渲染已验收。

## 契约与静态对照

- Brandon IOPStorage源码注释明确modifyEnergyStored绕过普通方向/速率，并要求正负请求都返回非负实际幅度。项目的直接modify与方向门禁分开符合此契约；不能把负请求返回正数当漏洞。原始二进制签名及旧缓存API源码哈希保留。
- 当前Handle.invalidate关闭能力通知，但已持有代理对象本身没有额外valid门禁；本批只断言LazyOptional失效，不宣称裸引用已禁写。普通Forge调用者需要尊重能力失效，当前证据不把此点直接升级为可利用缺陷。
- Flux配置标注worldRestart；本批内存切换只是核对当前enabled分支，不宣称支持玩家热切配置后全部旧引用立即撤销。
- 外部External读余额/容量夹负到0，接收/提取再由EnergyAccess夹返回量，真实目标短收和通用异常回滚沿P05既有责任；本批不重复关闭其问题。
- Jade插件仅在registerClient引用客户端provider，Server使用EnergyAccess读long。Jade流体修正层按固定行操作的组合风险留P10，不用“API有接口”证明所有混排兼容。
- 本地旧版相关能源接口和移植沿革已存于原0.117.0资料；本批以1.20.1实际Flux/Brandon契约为依据，不把旧平台API形状当新平台必须相同的实现，也不新增其他能源兼容。

## 剩余范围

真实Flux网络拓扑/配额、DE核心和反应堆、Forge能力注册生命周期、多人与区块卸载、实际NBT存档重映射、Jade绘图及其他模组tooltip混排全部未实机验证。本批普通极值/单次预算通过不替代这些验证。

P09-a/b/c/d已分别补Aura/Will、Mana/LP、PNC/Embers和本批能源/Jade能量。下一步P09-e完成动态容量/公共魔法边界及25文件逐职责归属，给出明确剩余实机项后再决定主部分收口。此前P09-01/02与其他已报问题都未修复，当前8/12不变。
