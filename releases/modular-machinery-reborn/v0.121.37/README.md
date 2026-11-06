# 0.121.37：第四批G组四项修复（2026-11-03）

执行第四批交接的 **G1～G4**，从0.121.36递增为0.121.37（G批改前为真正0.121.36），协议12不变。F组改动全部保留；统一标 **待Codex验收**；不部署/启动游戏、暂存/提交/推送。

- **G1 / P10-06**：`MePatternProviderScreen.init` 区分干净旧值与真实草稿——只有 `groupDirty`（用户真实编辑）才携带旧文本，干净重建采用最新服务器摘要（settingGroup 包住程序化填框，不误置 dirty）；刷新契约不变。摘要到达与重建交错时未编辑的旧值不再滞留。
- **G2 / P10-12**：`MachineRecipeLayout.measure` 改用与 `MachineRecipeCategory.drawTips` 同一文本组（`tipGroupsWithRecipeTooltip`，含作者说明与空组间距），各配方取最大宽高；换行策略与绘制一致（逐行原样不换行）；真实 drawTips 捕获验证说明文本落在页面内、无说明对照干净。0.121.31 精确 EU、灵气条、槽位接线保留。
- **G3 / P10-17**：`WorldGhostPreview` 回退检查从"只看紧邻下一层"改为按**实际有方块的层**（start/clear 时重建的结构索引）从低到高找最低未完成层——隔空层（0/2）与连续层（0/1/2）拆除最底层都正确回退；不引入"某 y 无结构即整层失败"、不逐整数枚举坐标、不建跨世界监听。正常前进、相邻回退、整机清除、START 不推进、维度/距离32/logout 清除保持。
- **G4 / P02-08（加载边界子项）**：`DiagnoseCommand.findController` 候选位置先经 `isLoaded`（只读、不创建区块）检查，未加载位置跳过——不触发创建式 getChunk/getBlockEntity；8 格立方范围、已加载跨区块最近距离选择、相同距离稳定、无目标 null 保持；注释里"准星兜底"无实现，本批也不新增。**这是 P02-08 的加载边界子项**：扫描次数优化未做，整项账本不销号。

repro（0.121.36 构建类）四夹具按旧行为复现（G1 干净旧值滞留、G2 说明不参与测量/负y越界、G3 两例不回退、G4 未加载区块 getBlockEntity 调用）；0.121.37 构建快照（568 class）复跑 fixed 全部通过（G1 9/9、G2 6/6、G3 12/12、G4 5/5）。证据在 `_audit/fourth-ai-8tasks-0.121.37/`（每项 src-before-0.121.36 起点、repro/prebuild/fixed 日志与退出码、4 份增量 diff、改前/后 340 份源码哈希）。Java17 clean build/reobf成功（34s），Gradle test=NO-SOURCE。

归档`modular-machinery-reborn-0.121.37.jar`，1,631,228字节，SHA-256 `07455E8B7BBA2A81288EE08C3C70D23D132454A6904CAC31DD7A8DD223BF508B`；归档JAR与build/libs字节一致。第四批8项（F+G）交付完毕，本批完成后停止。
