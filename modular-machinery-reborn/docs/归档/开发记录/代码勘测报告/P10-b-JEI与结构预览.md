# P10-b：JEI 与结构预览（client/jei、client/preview）

2026-10-31。基线 0.121.0 / HEAD `f94ba299`，`src/` 零差异；只勘测。指纹在 `_audit/code-survey-0.121.0/P10-b/source-sha256.txt`（26 文件）。

**扩展旧问题：P07-16 新增 3 个客户端入口**（预览/投影材料表同样按 `Block.asItem` 取物品、丢等级）。新增编号：0 条。

## 范围与深度

逐行读：`MachineRecipeCategory`（610 行的核心段：槽位装配、材料栈、流体/气体/魔法格）、`StructurePreviews`（材料聚合与组件命中）、`MachineRecipeLayout`/`ModularMachineryJeiPlugin`（定向）、`WorldGhostPreview`（清理路径）。其余（PreviewPanel 1226 行、StructurePreviewRenderer、BlueprintPreviewScreen 等）按要点扫描（相机矩阵、状态清理、JEI 动作桥）。

## P07-16 扩展：预览材料表的等级丢失（继承编号，不新开）

P07-16 已确认 `MachineAssembly.asItemStack`（:327-332）按 `state.getBlock().asItem()` 取材料，无法表达 hatch/casing/并行/总线族的一物多 tier，导致"带对材料报缺料/耗错等级"。本批确认**同根因还有三个客户端入口**：

- `StructurePreviews.addComponent`（:280-294）：预览面板的材料清单按 `asItem` 聚合——多 tier 家族显示为 tier 梯的高位物品；
- `StructurePreviews:101`（投影侧同表）；
- `PreviewPanel.java:932`（预览构建时的另一处取物）。

行为是**显示与后续搭建对照失真**：预览说"要 ×N 个 LUDICROUS 物品总线"，玩家拿 SMALL 总线则搭不了（P07-16 的 consumeItem 拒绝）。修复时三处应与 `MachineAssembly` 一并走同一个"状态→物品"映射。

## 通过的静态契约

- **JEI 类别装配**：每机器一类别、id 与类别绑定（JEI 15.49 支持延迟注册的论证在注释）；物品格 `setBackground(CELL,-1,-1)` 复原原版 18×18 单元几何；输入栈 `copyWithCount(amount)` + previewTag（数量与 NBT 预览都不丢）；输出不降为 1。
- **两类历史上的"打开即崩"都有双保险**：气体需求 `tankKind()==GAS` 不再进物品桶（:248 守卫 + cast 前注释）；`IngredientArrayRequirement`/`CatalystRequirement` 有独立分支不走 ItemRequirement cast。魔法格以 atlas metadata（MagicTankDrawable）绘制，无可选 API 链接；气体格文字 0.5 倍缩放装 18 格（E10），tooltip 走 describe() 全文——不依赖 JEI 对空槽回调的行为（离线不可证的部分明确标注）。
- **能量显示**：`MachineRecipeText.energyPerTick` 按方向求和，与原版 drawInfo 的总量语义一致；long 传送不受 FE int 截断。
- **JEI ghost**：`RecipeEditorGhostHandler` 只挂 RecipeEditorScreen（P01 的编辑器），ghost 类型边界在 P01 报告。
- **预览状态清理**：`WorldGhostPreview.clear()` 有多个触发（卸载/维度变化/匹配成功等，:162-203）；`BlueprintPreviewScreen` 的打开/关闭走同一入口。

## 观察（不编号）

- `MachineRecipeCategory.layout()` 惰性测量一次并缓存（每机器每 JEI 刷新一次），`getBackground` 尺寸变化时重建——重建频率取决于机器数量，无测量，不立项。
- 预览面板（PreviewPanel 1226 行）的坐标拾取/材料轮换/相机逻辑体量大但注释完备；离线只验证了其引用的 `PreviewLayout` 常量族。完整交互链（点击换材料、动态长度展开、NBT 方块实体图元）保持实机待验，与计划一致。

## 实机待验清单

JEI 页面实际绘制（流体罐 overlay、气体 tint、魔法条）、tooltip 组合顺序、ghost 拖入路径；结构预览的相机/拾取/法线光照、材料点击轮换、动态长度；蓝图预览与方块实体（仓口真实 NBT）图元。

**P10-b 离线完成，实机待验。** 接续 **P10-c：模型/光晕/渲染（client/geo、MachineBloom、MachinePartColors、WorldComponentHider、render/）**。
