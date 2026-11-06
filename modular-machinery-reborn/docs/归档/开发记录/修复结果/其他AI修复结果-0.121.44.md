> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **Codex独立离线验收通过，2026-11-03。** [验收报告](../验收记录/验收-第六批4项-2026-11-03.md)。下文原交付状态为历史；44报告裁剪语义已经勘误。实机/GPU未验，主任务暂停。

# 其他AI修复结果：第六批 K 组（0.121.44，2026-11-03）

**状态：待 Codex 验收。** 本批只处理交接指定的 K1/P10-14 与 K2/P10-15；J 组已在 0.121.43 交付。未部署、未启动游戏、未暂存/提交/推送、未改累计修复账本、未解除主任务暂停。

## 起点与构建

- 真实起点 **0.121.43 / 协议 12**，340 份生产源码、568 份 class。以 0.121.43 的最终源码哈希与 class 快照为 K 批改前基线（**没有拿 0.121.42 冒充**）。
- Java 17、`clean build --offline --no-daemon` 成功（1m10s）。`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.44.jar`，**1,634,525 字节**，973 entry / 568 class，SHA-256 `2E5A21A525325E5357061DE9E97FF08AF27A96239E8B5EE87D23862BBD313F64`。归档为最终产物逐字节复制。
- 340 份源码只改 **1 份**：`client/jei/MachineRecipeCategory.java`（`drawTextInCell` 及其必要私有辅助）。568 份 class 只变 **1 份**：`MachineRecipeCategory.class`；未增删类。
- `gradle test` = **NO-SOURCE**。

## K1 / P10-14：文字裁剪框随宿主原点移动

**根因。** JEI 在 `RecipeLayout#drawRecipe` 中先把 pose 平移到配方区域原点（`translate(area.x, area.y, 0)`），槽位背景再以页面局部坐标调用 `IDrawable.draw`。文字因为 `drawString` 读 pose 而跟着平移，`GuiGraphics.enableScissor` 却不读 pose——它直接接收像素坐标。改前把真实槽背景放在 `translate(120,70)` 下调用，文字落在 `120+cell.x`，裁剪框仍停在 `cell.x`；零原点对照正常，负/非整数原点同样错位。

**修复。** 在进入文字自身的 `translate/scale` **之前**，取当前 pose 的矩阵，把 18×18 格子的四个角转换到绝对 GUI 坐标：左/上 `floor`、右/下 `ceil`（向外取整，保证非整数平移下最后半个像素列不被削掉）。随后照常 `enableScissor`/`disableScissor`，不取消裁剪、不手工乘窗口 GUI 倍率、不修改共享 3D `StructurePreviewRenderer.clip`。【Codex勘误】1.20.1原生scissor栈会将新矩形与外层求交，无交集时为空，弹出后恢复外层。生产调用保留该行为；原夹具替身只记录栈，新增原生CPU栈验证见验收报告。

**证据（`_audit/sixth-ai-4tasks-0.121.44/K1/`）。** `TextClipCheck K1` 走与 P10-b4 相同的真实路径：原生 `RecipeLayoutBuilder` → 生产 `setRecipe` → 原生 `buildRecipeLayout` → 从真实槽取出 `OffsetDrawable` 背景并调用 → `GasTankDrawable`/`MagicTankDrawable` → `drawTextInCell`。改前 **7 项失败**，改后在 prebuild 与最终 class 上均通过：

- 零原点：裁剪＝格子矩形，文字在内；
- (120,70) 与 (-37,-12)：裁剪矩形整体随宿主平移，文字与裁剪同空间；
- (10.4,7.6)：外向取整为 `[cell.x+10, cell.y+7, cell.x+29, cell.y+26]`，文字四角均在框内；
- 多格：气体格与魔力格各一对 scissor，第二格用自己的绝对矩形，不串味；
- 多行长文本：一个格子只开一次 scissor（不是每行一次），文字行数>1；
- 已有外层 scissor：格内绘制结束后外层矩形恢复，调用者仍可正常 `disableScissor`；
- 空文字：生产 `addGasSlot` 根本不建气体槽；helper 对空字符串不取裁剪；
- 有效气体 sprite 路径：`GuiGasRenderer` 成功 blit 时不上裁剪、不写文字；null/缺失贴图拒绝；
- 槽位/箭头/魔力条/能源格/布局：1 个物品格、3 个流体类格、1 个 Aura 计量格；箭头在页面内；Aura 总量/高度算式不变（Aura 的 `draw` 需要渲染线程，本夹具不执行，只验数据）；能源格空/填充分别 1/2 次 blit，不碰文字或裁剪。

**边界。** 支持范围是 JEI 常规二维平移与轴对齐缩放（四角转换对轴缩放精确）；二维旋转宿主只能得到轴对齐外接框；透视矩阵未支持/未验，不能承诺其保守边界。未跑真实 GPU/OpenGL 截图，未执行完整 `RecipeSlot.draw`（含 `RenderSystem.enableBlend`），未验真实图集装载与 `GasTankDrawable` 成功贴图分支。缺贴图文字被错裁不等于“所有气体贴图失效”。

## K2 / P10-15：局部异常清理

**根因。** `enableScissor`、`pose.pushPose()` 之后没有 `finally`。向真实槽背景的文字 sink 注入 `IllegalStateException`，异常传播后 scissor 深度仍为 1、pose 栈也未回到基线。

**修复。** 每一行 `pushPose` 成功后用内层 `finally` 归还；开启的 scissor 在外层 `finally` 归还。不 catch、不改写原异常；只归还本方法成功取得的资源：`enableScissor` 失败时不会执行 `disableScissor`，`pushPose` 失败时不会多 `pop`；不清空调用者的 pose/scissor 栈，不重置 GPU 全局状态。

**证据（同目录 `K2/`）。** `TextClipCheck K2` 改前 **12 项失败**，改后在 prebuild 与最终 class 上通过：

- 首行/第二行 `drawString` 异常：原异常类型与消息保留；scissor 深度回 0；pose 回基座；调用者的 (120,70) 平移仍在；
- `pushPose` 后 `translate` 异常（夹具用自定义 `PoseStack` 可靠注入）：scissor 归还，只弹出本方法那一层，调用者额外层仍可自行 pop；
- `enableScissor` 本身失败：没有取得裁剪，`disableScissor` 调用数为 0，不误弹外层；
- 组合：外层 scissor + 非零原点 + 第二行异常 + 下一格正常——外层矩形恢复、pose 恢复、下一格照常绘制、最后外层仍可弹出。

**边界。** 这是条件性渲染异常后的状态污染，不是已证明原版 `Font` 会自然抛错，也不是 3D renderer 全局状态问题；未执行真实 GL 污染或声称游戏崩溃。离线验证的是捕获 `GuiGraphics` 的栈平衡，不等于实机 OpenGL 通过。

## 回归与边界

- 同一夹具同一最终 class 上复跑 K1/K2（`fixed`）均退出 0；`K1` 的裁剪组与 `K2` 的异常组不互相替代。
- 未改共享 `BlockMatcher`/`MachinePattern`、3D 预览 renderer、网络/协议、全局渲染状态；协议仍为 12。
- **未验**：真实 JEI 页面 GPU 绘制、实际图集/动画、完整槽位绘制、窗口 GUI 缩放倍率、真实异常来源、多人网络与实机。
- 证据目录含改前源码、改前 568 class 快照、最终 class 快照、源码 diff、运行命令/退出码/日志与改前失败断言。
