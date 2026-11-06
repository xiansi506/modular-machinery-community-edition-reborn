> **2026-11-03 Codex复验：两项通过离线验收，原第二批12项全部通过；真实游戏/网络/GPU待验。** 项目docs/归档/开发记录/验收记录/验收-GLM返工-0.121.31-2026-11-03.md为当前验收记录，下方待复验文字为交付时状态。

# 0.121.31：C2/C4 两项返工（2026-11-03）

**本版由 GLM-5.3 Flash 按[返工交接](../../../modular-machinery-reborn/docs/归档/开发记录/任务交接/返工交接-GLM第二批C2-C4-2026-11-03.md)交付 C2/C4 两项返工，其余 10 项成果完整保留，Codex 尚未复验。**

- **R1 / C2 / P02-09**（`command/BlueprintCommand.java`）：0.121.30 引入的自定义 `MachineIdArgument` 没有 `ArgumentTypeInfo`，原生 `ClientboundCommandsPacket` 序列化命令树时抛 `Unrecognized argument type`，含该节点的树无法同步到客户端。现改用已注册的 `StringArgumentType.greedyString()` 承载输入（greedy 让完整 ID 免引号通过，普通 string 会在冒号处截断），回调内剥离一对匹配引号并做 `tryParse` 校验：大小写/非法字符/多余文本/空输入都是普通命令错误（not_found 文案），不再是未捕获异常。权限 2、控制台拒绝、裸名默认本模组命名空间、未知机器不发物品、带引号写法均保留；未新增客户端参数协议或注册项，0.121.30 新增的 `get_blueprint.invalid_id` 语言键已随之移除。
- **R2 / C4 / P10-13**（`client/jei/MachineRecipeText.java`）：0.121.30 的超长分支直接输出原始 FE 数字却套上单位标签，选 IC2_EU/GT_EU 时数值是正确值的 4 倍；`bitLength() < 63` 也把部分合法正 long 误分到超范围分支。现先以 BigDecimal 精确应用单位倍率并按原版规则向下取整，再格式化；分组按实际值与 `Long.MAX_VALUE` 比较。9223372036854775808 FE 在 EU 显示 2305843009213693952 EU。long 范围内仍走原有 scale→abbreviate（4.0K/8.0G）；0/负值照旧无提示；能源存储/成本/事务的 long 契约未改。

## 验证与边界

同一夹具在 0.121.30 生产类上：R1 失败2项（内置参数对照通过、自定义类型在原生包路径抛 Unrecognized argument type）、R2 失败2项（EU 显示 4 倍、大值分组错误）；构建后用完整 0.121.31 生产类快照复跑：R1 7/7、R2 5/5 全部通过且退出0。R1 使用真实 `ClientboundCommandsPacket` 构造→`FriendlyByteBuf` 写出→读回→`getRoot` 重建，断言重建树包含 `mm-get_blueprint` 节点且参数类型为原生 `string()`，客户端树可解析全部接受写法，服务端 dispatcher 覆盖解析/权限/发物品；未插桩命令包或参数注册器。R2 用真实 `energyTip` 与逐例切换的真实 ForgeConfigSpec 四种单位（含 2^63+2 / 2^63+4 的非 4 整除求和下限验证）。证据在 `_audit/glm53flash-rework-0.121.31/`。

Gradle test 为 NO-SOURCE。未验证边界：真实服务器启动与玩家登录、真实客户端连接/聊天栏展示、真实 JEI GUI 与 GPU 渲染；离线命令树与 dispatcher 验证，未写“已实测登录修复”。0.121.28～30 与 0.121.27 的既有成果、归档均保留。未部署/启动游戏、暂存、提交、推送。

协议 **12**。Java17 clean build/reobf 成功（27s）。归档 `modular-machinery-reborn-0.121.31.jar`，1628118字节，SHA-256 `FA9C351D3CD9526ED0481264CA7BB773FAC325A786338C4E9B6C55A47CA6A3E9`；归档 JAR 与 build/libs 字节一致（cmp 通过）。
