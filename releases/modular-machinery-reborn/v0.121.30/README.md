> **2026-11-03 Codex结论：C1/C3单项通过，C2/C4返工，本批整体未通过；无真实游戏/FML/完整网络/GPU验收。** 详见项目docs/归档/开发记录/验收记录/验收-GLM第二批12项-2026-11-03.md。下方“待验收/自测全过”为交付时状态。

# 0.121.30：C批快照与展示边界四项修复（2026-11-03）

**本批由 GLM-5.3 Flash 按[第二批12项交接](../../../modular-machinery-reborn/docs/归档/开发记录/任务交接/修复交接-GLM5.3Flash-第二批12项-2026-11-03.md)交付 C 批（C1～C4），Codex 尚未验收。** 至此第二批 12 项（A/B/C 三批）全部交付。

- **C1/P03-05**（`machine/MachineDefinitions` 的 stage/source 边界）：注册时对同步源做 `deepCopy()` 快照，`stagedSources()` 访问器按深拷贝出读——脚本在 `.register()` 后继续改 builder（含嵌套 parts/NBT 对象）不再改变客户端按快照重建的结果；读取侧改动也不污染快照。显式重复注册的覆盖规则与无 source 定义、beginCycle 清理行为保留。未禁止注册后 builder 操作，未改来源优先级或 reload 事务。
- **C2/P02-09**（`command/BlueprintCommand`）：自定义 `MachineIdArgument` 按 ResourceLocation 的未加引号字符集读取，完整 ID（`review:missing`）无需引号即可解析，带引号写法保留，裸名称仍由命令默认到本模组命名空间（不会落到 `minecraft:`）；大写/非法字符/空输入走普通命令错误（解析期 `invalid_id` 文案或回调期 not_found），不再抛未捕获 ResourceLocationException；权限 2 与控制台拒绝不变。新增语言键 `get_blueprint.invalid_id`（en/zh，交接允许）。
- **C3/P10-11**（`client/preview/StructurePreviewRenderer` 的 pick/render 共同几何）：对角线平方和先提升 double（x≈46340/50000 不再 int 溢出为 NaN）；拾取射线以同一相机约定的 double 版本构建（float 逆矩阵在数万格外相机会让射线偏离目标数千格、永远无法命中），render 仍用 float 链但共享同一有限尺度。
- **C4/P10-13**（`client/jei/MachineRecipeText` + 白名单内 `MachineRecipeCategory` 能源分支）：速率求和与总量相乘改用 BigInteger 精确中间量；超过 long 的速率/总量（MAX+1、MAX/2+1×2）以精确十进制数字显示，不回绕为负、不消失、不冒充 0；正常值保留原 scale→abbreviate 格式（4.0K/8.0G），0 与负精确和照旧无提示；类别"是否有能源需求"改读精确和的正负。

## 验证与边界

同一夹具在 0.121.29 改前生产类（B 批交付快照）上：C1 失败3项、C2 失败3项、C3 失败2项、C4 失败3项（全部为修复断言，对照通过）；构建后用完整 0.121.30 生产类快照复跑：C1 5/5、C2 5/5、C3 5/5、C4 5/5 全部通过且退出0。夹具要点：C1 用真实 builder→register→`MachineDefinitionSyncPacket.parseForClient` 重建；C2 用真实 Brigadier dispatcher+注册测试物品+普通 Inventory 对象；C3 的参考投影为独立 double 实现（用于瞄准），再调用真实 `pickBlockAt` 验证命中；C4 调用真实 `tips/energyTip` 与精确访问器。证据在 `_audit/glm53flash-12tasks-0.121.30/`。

Gradle test 为 NO-SOURCE。未验证边界：真实 KubeJS 重载事件顺序与网络同步包传输、真实游戏画面/鼠标拾取手感、JEI 真实关闭生命周期、GPU 渲染（C3 绘制分支为数学/静态验证，浮动精度为 GPU 固有）；C3 拾取双精度是展示/交互层，不改预览 NBT、手势或状态清理。未部署/启动游戏、暂存、提交、推送。

协议 **12**。Java17 clean build/reobf 成功（54s）。归档 `modular-machinery-reborn-0.121.30.jar`，1630069字节，SHA-256 `1F4CE06DB0701154425AEE4C79783B037A56EBB104BC7BE451A523B04537CAA2`；归档 JAR 与 build/libs 字节一致（cmp 通过）。
