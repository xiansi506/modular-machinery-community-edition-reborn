# 0.121.36：第四批F组四项修复（2026-11-03）

执行第四批交接的 **F1～F4**，从0.121.35递增为0.121.36，协议12不变。全部已验收改动保留；统一标 **待Codex验收**；不部署/启动游戏、暂存/提交/推送。

- **F1 / P04-15**：`RecipeModifiers.applyDuration` 兑现 null=时长伪目标契约（旧版 `RecipeCraftingContext#addModifier` 同义）——`multiply(null,…)` 构造、网络/存读档、合并后都参与时长计算；显式 DURATION、具体目标、affectsChance、io 筛选语义不变；实际 MachineRecipe 执行副本 duration 与补偿对照通过。
- **F2 / P06-07（文档项）**：`docs/进阶/高级脚本指南.md` 的 `removeCoreThread` 改写为实际契约（busy 抛 `IllegalStateException`、不排队、空闲可删、固定声明不删、持续供料不自然排空），附"先 getCoreThread/isWorking 判断、空闲才调用"的安全示例并明确该例不保证排空；未编造"停止下一批→等空闲→删除"流程。轻量装置核实空闲成功/busy 抛错资源不变/finish 内仍 working 三点。
- **F3 / P07-01**：`MachineSchema.readParts` 对 `script-checker` 就地校验（只接受非空白字符串 token；对象/null/数组/数字/布尔/空串/空白串报错，消息含来源与字段名；根 parts/坐标数组/dynamic 段路径同校验；不 trim/不要求注册）；`MachinePattern.matchesRule` 对程序化/旧缓存注入的非法规则受控拒绝（服务端该部件匹配失败，不抛出也不吞成 true；客户端跳过契约保持在类型校验之前，行为与旧版一致）。
- **F4 / P08-01**：`MeFluidBusLogic.tickInput` 标记遍历上界从 `tankCount()`（9）改为 `marks.length`（16 配置格）；物理罐循环仍按实际罐数；`failures` 短于 marks 的守卫保留（高位标记无独立退避计数，边界写明）；第 9/12/15 格独立维护、超量送回、删除标记全量送回、同流体合计只维护一次、容量夹限与部分接收/抽取对照全部通过。

repro（0.121.35 构建类）四夹具按旧行为复现；0.121.36 构建快照（568 class）复跑 fixed 全部通过（F1 12/12、F2 4/4、F3 16/16、F4 11/11）。证据在 `_audit/fourth-ai-8tasks-0.121.36/`（每项 src-before-0.121.35 起点、repro/prebuild/fixed 日志与退出码、4 份增量 diff、改前/后 340 份源码哈希）。Java17 clean build/reobf成功（1m21s），Gradle test=NO-SOURCE。

归档`modular-machinery-reborn-0.121.36.jar`，1,630,696字节，SHA-256 `75FDAC64B97DBE6325838862720CF6F46051CCDFF8B15DF02F781971E38F07E0`；归档JAR与build/libs字节一致。
