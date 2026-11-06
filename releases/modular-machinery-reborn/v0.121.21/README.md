# 0.121.21：结构变化保留至下一轮控制器检查（2026-11-02）

修复 **P07-07**：结构变化记录使用当前与上一轮两代集合，START 只轮换并淘汰更旧一代，不再无条件删除两个 tick 之间刚产生的变化。当前 tick 的变化立即可见，并为已经先 tick 的控制器保留到下一 tick；查询不消耗记录，多个控制器可见同一变化。标记、轮换、查询、维度/全局清理使用同一同步边界，维度卸载同时清理两代，不保留过期状态。

对照本地1.12.2，旧监听器同样在START清空；此次纠正1.20.1任务泵时序中已原生复现的漏检，不简单延续该清空方式。0.121.20完整生产类基线通过真实MinecraftServer.waitUntilNextTick执行队列任务，移除必需方块并记录事件后，START丢失变化，控制器仍消耗1煤并产出1石。修复专项核对相同队列路径在扣料前取消成型、START之后修改、控制器已 tick 后才修改、当前/保留代合并、自然过期、重复坐标、多次只读查询、维度与全局清理、ClientLevel事件/卸载门控。delayed=false仍遵守固定周期，不更改用户配置语义。

构建后完整生产类复跑TickChangeRetentionCheck、ChunkPortRefreshCheck、LoadedNeighborCheck，均退出0。Java17 clean build/reobf成功（1m11s）；Gradle test为NO-SOURCE。首次夹具编译的LevelEvent通配导入歧义已修正，失败日志保留，不计为通过。使用说明补充变化保留规则。协议12。

原生服务端任务与事件/控制器方法在内存ServerLevel壳中执行，事件处理函数直接调用，未做完整Forge总线、真实服务器循环、游戏/网络或性能验收。现有事件种类覆盖与P07-08可选修饰器/动态探测范围另行处理；不声称记录到了原本未投递的事件。不部署、暂存、提交、推送。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.21`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.21.jar`，1623967字节，SHA-256 `0899286A3CD7374EAA5AF31F525C00782AB3CAB5112C803E086E3038ADB24E65`；归档JAR与build/libs原下划线命名JAR字节一致。
