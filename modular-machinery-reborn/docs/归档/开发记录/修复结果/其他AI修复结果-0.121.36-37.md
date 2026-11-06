> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI修复结果：0.121.36（第四批F组）+ 0.121.37（第四批G组，2026-11-03）

执行 `docs/归档/开发记录/任务交接/修复交接-其他AI-第四批8项-2026-11-03.md` 的 F1～F4 与 G1～G4，分两批累积交付：**F 批 0.121.36、G 批 0.121.37**（G 批改前为真正 0.121.36），协议12不变。全部已验收改动（第三批含 E3 返工）保留；八项统一标 **待Codex验收**；交付完毕后停止。未部署/启动游戏、暂存/提交/推送、reset/clean；HEAD 保持 `f94ba299`。

## 起点核对

- F 批起点：0.121.35 / HEAD `f94ba299` / 340 份 Java，与 0.121.35 复验指纹 `current-source-hashes.json` 逐份一致；改前全源码哈希 `fourth-ai-8tasks-0.121.36/hashes-before-f.txt`。
- G 批起点：真正 0.121.36 / 340 份，改前哈希 `fourth-ai-8tasks-0.121.37/hashes-before-g.txt`；不沿用 0.121.35 副本。

## 逐项结果

| 任务 | 结果 | repro | fixed | 改动 |
|---|---|---|---|---|
| F1 P04-15 | 完成 | 复现 | 12/12 | `RecipeModifiers.applyDuration` 兑现 null=时长契约 |
| F2 P06-07 | 完成（文档） | 4/4 | 4/4 | `docs/进阶/高级脚本指南.md` 契约改写+安全示例；无生产代码改动 |
| F3 P07-01 | 完成 | 复现 | 16/16 | `MachineSchema.readParts` 校验 + `MachinePattern.matchesRule` 受控拒绝 |
| F4 P08-01 | 完成 | 复现 | 11/11 | `MeFluidBusLogic.tickInput` 上界改 `marks.length`（16 格） |
| G1 P10-06 | 完成 | 复现 | 9/9 | `MePatternProviderScreen.init` 只携带真实草稿 |
| G2 P10-12 | 完成 | 复现 | 6/6 | `MachineRecipeLayout.measure` 换用与绘制同一文本组 |
| G3 P10-17 | 完成 | 复现 | 12/12 | `WorldGhostPreview` 按实际层集合回查最低未完成层 |
| G4 P02-08 | 完成（子项） | 复现 | 5/5 | `DiagnoseCommand.findController` 跳过未加载区块 |

**F1**：null 目标与显式 DURATION 同等参与时长；ADD/MULTIPLY 真实顺序、合并、FriendlyByteBuf 与 NBT 往返、实际 `MachineRecipe.duration`/补偿对照；具体目标、affectsChance、io 筛选不混入。
**F2**：指南改写为实际契约（busy 抛错不排队、空闲可删、固定声明不删、持续供料不自然排空、finish 内仍 working），安全示例（getCoreThread/isWorking 判断）并声明不保证排空；不编造"停止下一批"流程。轻量装置核实三点契约。
**F3**：加载入口只接受非空白字符串 token（四条读取路径同校验，错误含来源与字段路径；不 trim/不要求注册）；运行期服务端对注入的非法规则受控拒绝（不抛、不吞 true），客户端跳过契约保持在类型校验之前；合法/未注册 token、四朝向、控制器 tick 冒烟通过。
**F4**：16 配置格全遍历（第 9/12/15 格独立维护、超量送回、删除标记全量送回、同流体合计单次维护）；物理罐循环按实际罐数；failures 守卫不越界（高位标记无独立退避计数边界写明）；容量夹限/部分抽取对照。
**G1**：干净重建采用最新摘要（程序化填框不误置 dirty），真实草稿保留；复现"干净旧值滞留且刷新无法纠正"。
**G2**：测量与绘制同一文本组（含作者说明与空组间距、逐配方取最大、不换行策略一致）；真实 drawTips 捕获说明文本落在页面内；0.121.31 精确 EU 等保留。
**G3**：实际层集合索引（start/clear 重建）+ 最低未完成层回退；两例复现回退 0；相邻回退/START/整机清除/维度/距离32/logout 对照保持；不逐整数枚举坐标、不建全局监听。
**G4**：候选位置 `isLoaded` 先行（只读不创建区块），未加载跳过（创建式查询 0 次）；范围/最近距离/相同距离稳定/null 保持；**P02-08 加载边界子项**，扫描次数优化未做、账本不销号；准星兜底不新增。

## 证据

每项 `_audit/fourth-ai-8tasks-<版本>/<任务号>/`：src-before-0.121.35（F）/src-before-0.121.36（G）起点副本、repro/prebuild/fixed 三阶段日志与退出码（repro=上一版构建类复现，prebuild=单文件修复验证，fixed=本版构建快照复跑）；增量 diff 与改前/后 340 份源码哈希（`hashes-before-f/after-f/before-g/after-g.txt`）。构建后完整 classes 快照（568 class）复跑全部 fixed。F2 复用 P06-b1/b2 装置、F3 复用 P06-a1/P07-a1、G1 复用 P10-a3、G2 复用 P10-a2/b3、G3 复用 P10-b7 装置骨架（复制到新目录，旧记录未覆盖）。夹具首次失败（import/包名/bootstrap/字体桩/Unsafe final/场景序列/期望标记）日志保留并更正。

## 构建与归档

- 0.121.36：clean build/reobf 1m21s，`modular_machinery_reborn-0.121.36.jar` 1,630,696字节，SHA-256 `75FDAC64B97DBE6325838862720CF6F46051CCDFF8B15DF02F781971E38F07E0`，归档 `releases/modular-machinery-reborn/v0.121.36/`。
- 0.121.37：clean build/reobf 34s，`modular_machinery_reborn-0.121.37.jar` 1,631,228字节，SHA-256 `07455E8B7BBA2A81288EE08C3C70D23D132454A6904CAC31DD7A8DD223BF508B`，归档 `releases/modular-machinery-reborn/v0.121.37/`。
- 两版归档 JAR 与 build/libs / reobf output 字节一致（cmp）；Gradle test=NO-SOURCE 如实记录。

## 文档同步

项目 README 状态行与当前源码版本（0.121.37）、工作区总览 README、`docs/en/README.md`、`docs/en/Introduction.md`（Current status 两条 + Current version）、迁移日志顶部两条、新建 `docs/归档/版本记录/更新记录-0.121.36.md`/`-0.121.37.md`、release 索引顶部四条、修复进度（状态行/下一入口/SHA-256/版本表两行/两节）、未修复事项清单（顶部快照与 P04-15/P07-01/P08-01/P10-06/P10-12/P10-17/P02-08 七行；P06-07 为文档项不在未修清单正文则在结果文档单独说明）。逐处 grep 验证命中。

## 边界与限制

- 夹具替身（Unsafe 壳、记录 Storage/绘制/区块计数、标志位或真实 bootstrap）只验证解析/维持/持久化/UI 重建/层状态/查询边界；未实机、未验证真实 AE2 网络/Rhino 实跑/GPU/JEI 页面视觉/服务端时延与区块 IO。
- F1 不宣传普通 JSON 时长失效；F2 保留 busy 保护只修文档；F3 不重构脚本回调框架；F4 不扩 27 格、不做退避重做；G2 沿用不换行策略并记录过长页面宿主限制；G4 是加载边界子项（扫描优化未做、账本不销号）。
- 累计账本（57 项完成/4 部分/61 待处理）待 Codex 验收后由验收方更新；本报告不自行销号、不解除主任务暂停。8 项交付完毕即停止。
