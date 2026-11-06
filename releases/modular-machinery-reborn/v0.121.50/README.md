# v0.121.50 — 第九批 P 批（P04-16 / P06-19，2026-11-04）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.50.jar` **1,638,259 字节**，SHA-256 `0DAA0BC3D452CB8C78272391D6DAC8FD740988BB4FA5FBA3DA16FF0146BFA484`。起点 **0.121.49**。**标待 Codex 验收。**

## 本版内容

- **P1 / P04-16**：数字接口需求的**服务端定义/执行复制**保留原始可空覆盖文案。`InterfaceNumberInputRequirement` 新增 `definitionCopy()`，`MachineRequirementParser.copy` 为该类型走同一复制族；显式 `notEqual` 保留，null/空/空白副本继续在实际端口读取机器类型文案或默认键，不把 `notEqualKey()` 回退固化成显式覆盖。selector-tag、`parallelize-unaffected`、scriptOptions 照旧复制；触发状态全新，修改副本选项不影响原对象。wire 正文未加字段、顺序未改。
- **P2 / P06-19**：工厂 `ActiveCraft.advance()` 改为**饱和推进**，`Integer.MAX_VALUE` 不再回绕为 `Integer.MIN_VALUE`。`MAX-1` 可到 `MAX` 并进入既有 final-tick/finish 收尾；`MAX` 本身走既有合法收尾流程。未跳过未支付需求，未处理 P06-11。

## 验证

- P1 正确行为夹具：0.121.49 类上 `definition copy preserves the explicit override` 失败；0.121.50 final class 全过。覆盖显式/null/空/空白、机器类型自定义文案、缺接口、范围、原需求→copy→再次copy、真实 `executionCopy`/`startFailure`、副本选项隔离、不继承 triggered、wire 字节不变。
- P2 正确行为夹具：0.121.49 类上 `progress already at MAX ... -2147483648` 失败；0.121.50 final class 全过。普通/核心线程各覆盖 duration `1`、`MAX-1`、`MAX`；真实脚本进度入口设置受控极限；阻塞输出先保持不额外 IO，解除后一次完成；END 取消、重试与重启语义保留；真实 save→restore→pending tick 完成一次。
- 回归：`FinalTickCheck` 与 `ControllerExtraCheck` 通过；`ItemCopySafetyCheck`/`FactoryPersistenceAuditCheck` 中各有一处在 **0.121.49 接受类快照上也失败**的旧夹具断言（`baseline loses private output NBT`、`changedDefinitionFlags`），已留 49 基线对照，不因本批改动变更；FactoryPersistence 前四段存读档用例通过。
- 构建：Java 17 `clean build --offline --no-daemon` 成功，含 reobf；`test NO-SOURCE` 如实记录。

## 边界

客户端 wire 往返仍不携带程序化覆盖字段，本项是服务端定义/执行复制契约，不称为客户端同步补齐。未实机、未连接真实网络/Rhino/完整 FML；P2 极限进度由真实脚本入口受控设置，未循环数十亿 tick；未部署、未动整合包/存档、未提交/推送、未创建 Release。证据 `_audit/ninth-ai-4tasks-0.121.50/`。

## 构建与归档

340 Java / 568 class 无增删；相对 0.121.49 仅白名单 3 份源码改变。JAR 973 entry / 568 class；221 JSON+mcmeta 全可解析；中英语言键各 566 且键集一致；`mods.toml` 0.121.50。`build/libs`、`build/reobfJar/output.jar` 与归档逐字节一致。旧归档未改写。
