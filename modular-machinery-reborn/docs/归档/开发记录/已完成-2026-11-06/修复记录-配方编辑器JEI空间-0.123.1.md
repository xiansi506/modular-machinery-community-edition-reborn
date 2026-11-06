# MMCER 0.123.1：配方编辑器右侧 JEI（2026-11-06）

用户反馈打开配方编辑器物品时看不到右侧 JEI。旧面板在 320 像素 GUI 宽度下占 296 像素并居中，只给右侧留下 12 像素。实际 JEI 15.49 的 `IngredientListOverlay.createDisplayArea` 从编辑器右边界裁出列表区域，`IngredientGrid` 空间不足就无法展示网格。其最小列数为 2；本轮专项采用更严格的 4 列条件。

## 修复

在真实 JEI screen handler 查询 GUI properties 时启用一次 JEI 布局，为右侧保留独立空间并重新排列编辑器。简易材料格、按钮、预览与高级表单共用新边界，标题也居中于编辑器内部。重新查询不会反复重建；缩放窗口仍保留 JEI 布局。首次启用会收集表单，非法输入则逐字保留，避免挤压界面时丢草稿。未由 JEI 接入时保留原居中布局；不强行修改 JEI 的全局显示开关。

只修改 `RecipeEditorScreen` 和 JEI 插件注册两处 Java，资源、协议 13、服务端配方、结构选块、无线仓模型／贴图均未改变。设计稿仍未接入游戏。

## 验证

- 改前在 0.123.0 类上跑原 UI 263 项断言通过，记录基线。
- 最终类上 **319 项专项断言**：捕获实际插件注册的 screen handler，运行 JEI 15.49 的真实 display-area 和网格计算；覆盖 GUI 宽度 320、427、480、640、800、1280，确认至少四列空间、全部控件／ghost targets 留在编辑器内、输入保留、高级模式、重复查询及 resize。
- 原 UI **263 项交互断言**回归通过。测试使用 shell Minecraft／字体／窗口参数，无实际 OpenGL、JEI 绘制或鼠标实机验收，不把几何测试称为完整游戏测试。
- Java 17.0.11 clean build/reobf 成功（`--offline --no-daemon -Puse_local_deps=true`）；Gradle test NO-SOURCE。独立编译 2 个源文件所得 5 个 class 与 Gradle 逐字节一致，616 个其他 class 与基线一致，资源字节不变。

正式包 1,808,049 字节，SHA-256 `1B3CF4507A011CB944FFC29BB9B80F56C244314D4498C13CF8288350A53BABB9`。[归档下载](../../../../../releases/modular-machinery-reborn/v0.123.1/README.md)。审计脚本和原始日志（本地审计工件，不随本次发布）。未部署／启动游戏，未暂存／提交／推送；旧归档保留。
