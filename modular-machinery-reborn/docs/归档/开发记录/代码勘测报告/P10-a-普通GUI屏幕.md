# P10-a：普通 GUI 屏幕（client/ 除 jei/preview/geo）

2026-10-31。基线 0.121.0 / HEAD `f94ba299`，`src/` 零差异；只勘测。指纹在 `_audit/code-survey-0.121.0/P10-a/source-sha256.txt`（client/ 全部 29 文件 + menu/MeConfigAmount）。

**新增编号：0 条。** GUI 的离线可验证部分（布局度量、数值解析、发送入口、同步解码）静态覆盖；真实渲染/字体/命中/缩放按计划实机待验。

## 范围与深度

逐行读：`MachineControllerScreen`（705 行，全文）、`FactoryControllerScreen`（919 行，全文）、`MagicHatchScreen`、`ParallelControllerScreen`（输入/发送路径全文）、`MeItemBusScreen`（配置格交互段全文）、`GuiAmounts`。定向核对：`ScreenLayout`（布局度量与断言钩子）、`UpgradeBusScreen`/`MachineHatchScreen`/`HatchGroupScreen`/`MePatternProviderScreen`（发送点、槽位交互、标题裁剪）、`GuiFluidRenderer`/`GuiGasRenderer`、`ClientMachines`/`ClientPatternSync`/`ClientClipboard`/`ClientPresentationLifecycle`（摘要级）。表单编辑器主体（RecipeEditorScreen/RecipeResourcePicker）与建造工具屏归 P01 已有报告；`ClientPreview`/preview/、`MachineBloom`/`MachinePartColors`/`WorldComponentHider`/geo 归 P10-b/c。

## 通过的静态契约

- **布局单源**：两块信息屏的行序列只在一处描述（`infoRows`/`orderedInfoRows`），绘制与离线 M6c 断言共用同一套数字（`metrics()`/`textBlockRect()`/`scissorRect()`）；行预算 Counter 让"画过的行数不可能超过测得的行数"，页脚线 `min(FOOTER_Y, block.footerY)` 被夹回面板。0.24.1～0.24.5 的坐标空间缺陷史都在注释里有字节码级还原，不重复登记。
- **面板 1:1 blit**：`blitPanel` 强制 9 参重载并显式传 PNG 的 IHDR 尺寸（280×213），harness 读文件头断言；0.24.3 的 256 默认拉伸缺陷不可回归。
- **数值输入**：控制器智能接口字段按原版字符集（数字/`.`/`E`，负号缺失为记录在案的保留怪癖）+ `Float.isFinite` 才发送；并行控制器 `Integer.parseInt` + [0,max] 夹取，+amount 溢出跳满、-amount 夹 0、`+1` 在顶格为 no-op——逐条对齐原版 ：102-169。`GuiAmounts.compact` 用 BigDecimal + RoundingMode.DOWN（从不虚增）与 stripTrailingZeros；`filledPixels` 非负夹取。
- **发送路径全部服务端权威**：智能接口值（SmartInterfaceUpdatePacket）、并行值、输入组开关（HatchGroupUpdatePacket）、ME 配置格（MeFluidConfigPacket 三类写 + `MeConfigAmount` 走原版 `handleInventoryButtonClick` 容器按钮路由）、供应器模式/样板（MePatternProviderActionPacket/MePatternPacket）——屏上只做初步解析，拒绝逻辑在 P02-a 已勘测的服务端处理。
- **同步解码**：魔法仓六值 24 字解码有 `Double.isFinite` 防 NaN；`ClientMachines` 在登出时 `clear()`（P03-a 的同步生命周期）；`MeItemBusScreen` 标题超宽用 `plainSubstrByWidth` 裁剪加省略号。
- **MagicHatchScreen**：WILL 五变体条各自以 value(5)=100 归一（超过夹满条），空气行显示压力/容积/危险-临界阈值；悬停 tooltip 显示 `Double.toString` 精确值（小字才用 compact）——符合"悬停始终精确"的约定。

## 观察（不编号）

- `ParallelControllerScreen.submitValueField` 在值超范围时仍清空输入框（清空在 try 内）——与原版一致的已记录分歧，反馈略弱但服务端会拒绝，不算缺陷。
- `MeItemBusScreen.renderConfigMarks` 每帧对 16 格逐个 `configItemAt/fluidMarkAt/gasTanks().mark`（gas mark 每次构造 Entry 记录对象）——每帧分配极小，无测量，不立项。
- 工厂屏队列行文本用 `thread.scriptState().status()` 任意脚本字符串直接 `Component.literal` 绘制——文本来源是服务器同步的脚本状态，客户端绘制无转义问题。
- 两块信息屏的 `extraInfo`（ControllerGuiInfoEvent）每帧各 post 一次 Forge 事件——addon 挂钩的既定契约（原版同构），数量取决于已装 addon。

## 实机待验清单（离线不可判）

GUI 缩放各档下的 0.72 缩放文本可读性；中文/长文本换行与 `WRAP_WIDTH`；输入焦点/回车提交/复制粘贴（EditBox 原版行为）；滚动条拖动命中（比例公式已对齐原版）；tooltip 层叠；供应商/总线屏与真实 AE2 贴图叠加；`ControllerGuiInfoEvent` addon 实际行高。

**P10-a 离线完成，实机待验。** 接续 **P10-b：JEI 与结构预览（client/jei、client/preview）**。
