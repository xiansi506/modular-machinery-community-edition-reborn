# 0.121.33：第三批D组四项修复（2026-11-03）

本批完成交接第三批的 **D1～D4**：**P02-04、P04-04、P06-10、P10-01**，从0.121.32递增为0.121.33，协议12不变。保留已有各批修复与全部未提交改动，不部署、暂存、提交或推送。

- 投影应答中某位置候选描述符全部解析失败时跳过该位置，不再把空候选列表交给 `MachinePattern.Builder.add` 触发 `JsonParseException` 中断后续位置；全无效/空消息保持不启动投影语义。
- ingredient_array_input 与 catalyst 的字段名按读取器实际规范拼写严格检查，错误大小写（`Amount`/`Chance`/`NBT`/`Modifiers`/`Tooltip`）明确报错并含来源；正确小写、公共选项键与 NBT 内自定义键保留。
- 控制器额外线程数存档升为完整 int（键名不变），旧 short 档兼容：正值原样恢复、回绕负值按非负规则校正为 0（回绕丢失的位无法恢复，边界写明）；Java setter 与脚本桥 0~32767 限制不变。
- 魔法仓负整数（负灵气/负空气/负压力）显示保留符号并按共享规则缩写，不再被非负仓储工具夹成 0；非负路径、负小数与 NaN/Infinity 行为不变，`Long.MIN_VALUE` 边界说明写明。

0.121.32 生产类基线逐项复现四种旧行为；0.121.33 构建后完整 classes 快照上全部通过。证据与增量基线在 `_audit/third-ai-8tasks-0.121.33/`（D1 baseline 6/6 复现、fixed 7/7；D2 8/8→8/8；D3 7/7→8/8；D4 11/11→11/11）。夹具替身（同步网络上下文、DistExecutor 记录、注册表容器、ClientLevel 壳）只验证解析/构建/持久化/绘制文本，不是真实联网、GPU 或服务端调度验收。

Java17 clean build/reobf成功（37s），Gradle test为NO-SOURCE。协议 **12**。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。**全部四项统一标待Codex验收**；E组四项（P10-10、P10-20、P06-18、P10-19）由 0.121.34 批次执行，本批完成后暂停。

归档`modular-machinery-reborn-0.121.33.jar`，1629503字节，SHA-256 `69A3245CD49001E88379E8CA4E8F017D86FCA9406D127097CFC81D1CBEB43AEB`；归档JAR与build/libs下划线命名JAR字节一致。
