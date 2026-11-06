# 0.121.32：配置原子读取、严格开关与重载默认值修复（2026-11-03）

本批完成用户指定数量的五项修复：**P03-04、P03-07、P03-10、P03-12、P03-13**，从0.121.31递增为0.121.32，协议12不变。保留已有GLM各批修复与全部其他未提交改动，不部署、暂存、提交或推送。

- 变量文件先解析到临时表，整份成功后合并；后续字段失败不再留下前面已覆盖的值。保留有效文件及同名变量原有覆盖规则，不等于整次重载回滚。
- requires-blueprint和流体perTick严格接受JSON布尔，字符串/数值/null/容器拒绝并带字段与来源；合法真假及缺省保留，不再将误拼布尔静默读成false。
- 智能接口默认值、需求value/minValue/maxValue检查转换后的float有限性，直接Java构造入口同样拒绝非有限默认/边界。旧NBT非有限或非数字值丢弃，下次bind填入声明的有限默认；有限旧值及编辑值保留。
- 服务端配置配方来源在缺目录/空目录时也注册；同一PackRepository重载可发现第一份新增配方，最后一份删除后无旧资源。无需通过重新触发AddPackFinders事件才能发现首份配置。
- schema、Java便捷构造器、工厂模型/控制器缺定义回退在使用时查询当前工厂默认配置。已解析定义不追溯变更，显式字段覆盖配置。旧公开DEFAULT_MAX_THREADS/DEFAULT_HAS_FACTORY为兼容外部API保留为已弃用首载快照，内部不再用于动态默认值；外部调用者应使用ModConfig getter。

0.121.31完整生产类基线逐项复现五种旧行为；同一最终专项在构建后完整生产类上通过，覆盖失败变量不部分覆盖/有效提交、机器与流体布尔类型、真实一次性10mB对照3tick共30mB支付、智能接口解析/Java入口/旧NBT清理及有限存读档、原生PackRepository首次新增/末次删除、同进程配置10/false→20/true→4/false、显式与旧定义不变及便捷构造器。相关FinalTickCheck、SavedDefinitionCheck、SchemaDynamicCheck、ConfigTypeIsolationCheck、RegistrationSnapshotCheck全部退出0。

Java17 clean build/reobf成功（1m3s），Gradle test为NO-SOURCE。首次夹具误用PackRepository.close()及字段名，在业务改动前更正，编译失败日志保留；正式构建无失败。证据在同级_audit/code-survey-0.121.0/fix-0.121.32/。内存Forge配置、原生资源仓库/解析/流体结算和审计专用文件，不是实际FML配置监听、游戏/reload命令、多人网络、GUI/GPU验收。

对照本地1.12.2：MachineLoader.prepareContext先反序列化整份变量再写上下文；DynamicMachinePreDeserializer.getRequireBlueprint明确校验JSON布尔；AbstractMachine实例字段从当前Config初始化工厂默认值。配置资源包首份发现属于现代资源仓库生命周期，不声称旧版有相同API。

本批不解决P03-02/03-14客户端默认来源差异/整体重载回滚、P03-09其余数字精确性、运行中线程规则迁移或动态注册新控制器。工厂配置须先被Forge实际读入，新的解析才读取新值；不承诺改toml后无条件热注册方块。用户要求只选五项，本批完成后停止。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.32`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.32.jar`，1628938字节，SHA-256 `5D7AC419BD3E1AC951801982F16B861301C0A8F889CF05FDB0C3F77E9BECFDCA`；归档JAR与build/libs原下划线命名JAR字节一致。
