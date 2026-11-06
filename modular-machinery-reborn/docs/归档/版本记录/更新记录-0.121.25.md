# 0.121.25：能源需求必填速率并精确读取long（2026-11-02）

修复 **P04-08** 及 **P03-09 的energyPerTick字段分支**：能源JSON需求必须明确声明数值型energyPerTick，再以BigDecimal.longValueExact精确读取非负signed long。不再把遗漏/大小写拼错默认补0，不接受数字字符串、布尔/null/容器、非整数数值、负数和越界数值；不会把2^64+1截成1。原始JSON显式0仍兼容，40.0/4e1等数学上精确整数允许；typed builder继续要求正数，没有改变Java构造器及网络格式。

对照本地1.12.2 RequirementTypeEnergy.createRequirement，旧实现同样要求字段存在且是number；新实现再补足精确long及非负范围。0.121.24基线实际复现遗漏/拼错字段为0，真实控制器无能源端口仍产出石头。专项覆盖两类缺字段、输入/输出各12类无效值与8类合法边界值，明确JSON零、Long.MAX、超int、整值小数/指数，核对执行副本和FriendlyByteBuf。生产typed/raw JSON builder、完整MachineSerializer加载/网络往返及错误定位均覆盖；实际控制器按4294967297 FE/t从输入扣除并原量输出，显式零配方仍可无能源仓完成。

Java17 clean build/reobf成功（1m15s），构建后完整生产类复跑专项、RegistryDefaultsCheck、TickFailureCheck、SavedDefinitionCheck退出0；Gradle test为NO-SOURCE，协议12。配方指南补充必填、精确范围、JSON/typed零值区别及旧错误配方修正说明。复用的注册表专项复制到本审计目录，临时config未写入旧证据目录。

原生codec/long仓储/控制器真实，Level为内存壳；typed纯JSON builder已运行，未启动实际Rhino事件、完整FML/服务器/网络传输/游戏。只解决该long字段，其他JSON整数/布尔字段的宽松转换继续保留待修编号。不部署、暂存、提交、推送。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.25`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.25.jar`，1625705字节，SHA-256 `EE8325E0CF54212DB218E0AA239C1272971C20422B0D5991436E57DEAE6EFA7A`；归档JAR与build/libs原下划线命名JAR字节一致。
