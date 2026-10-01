# GUI、客户端渲染与结构预览

> 审计范围
> - 旧版（1.12.2，MMCE 2.3.2）
>   - `_mmce-src\ModularMachinery-Community-Edition-master\src\main\java\github\kasuminova\mmce\client\`（77 个 Java 文件，8495 行）
>   - `...\src\main\java\hellfirepvp\modularmachinery\client\`（23 个 Java 文件，3458 行）
>   - `...\src\main\java\com\cleanroommc\client\`（22 个 Java 文件，2413 行）
>   - `...\src\main\resources\assets\modularmachinery\textures\gui\`（23 个 png）
> - 新版（1.20.1 / Forge 47.2.0 / Java 17）
>   - `modular-machinery-reborn\src\main\java\com\reborn\modularmachinery\client\`（4 个 Java 文件，**113 行**）
>   - `modular-machinery-reborn\src\main\java\com\reborn\modularmachinery\menu\MachineControllerMenu.java`（58 行）
>   - `modular-machinery-reborn\src\main\resources\assets\modular_machinery_reborn\textures\gui\`（`controller_legacy.png` + `.keep`）
>
> 结论方向：本领域旧版是一个 **4730 行的通用动态组件框架 + 8000+ 行的 3D 结构预览/切片/世界投影渲染栈**，新版只有 **113 行客户端代码**，其中 GUI 为 46 行的单屏程序化绘制，结构预览渲染整体为 0。

---

## 一、已迁移

本领域**没有任何条目达到"语义基本等价"的已迁移标准**。唯一在语义层面成立的对应关系是"控制器 GUI 有 GUI"，但绘制方式、贴图来源、信息量均已改变，按口径归入「四、设计差异」。

已迁移条目数：**0**。

---

## 二、部分迁移

### 2.1 控制器 GUI 外壳（同尺寸面板 + 槽位 + 能量/进度条）——"部分迁移"

- **旧版证据**
  - GUI 基类与窗口尺寸：`...\hellfirepvp\modularmachinery\client\gui\GuiMachineController.java:38`（`textures/gui/guicontroller_large.png`）、`:45`（`this.ySize = 213;`）、`:179-185`（`drawGuiContainerBackgroundLayer` 绑定贴图并 `drawTexturedModalRect(i, j, 0, 0, xSize, ySize)` 整图铺底）
  - 动态组件挂载：`...\github\kasuminova\mmce\client\gui\GuiContainerDynamic.java:21`（`protected WidgetController widgetController`）、`:60-68`（背景层 `widgetController.render(...)`、前景层 `widgetController.postRender(...)`）、`:89-128`（把鼠标/滚轮/键盘事件优先转交组件树）
- **新版证据**
  - `modular-machinery-reborn\src\main\java\com\reborn\modularmachinery\client\MachineControllerScreen.java:17`（`imageWidth = 176; imageHeight = 166;`）、`:18-37`（`renderBg` 内逐段 `g.fill(...)` 画面板、能量条、进度条、槽位）、`:47`（`render` 中 `renderBackground` + `super.render` + `renderTooltip`）
  - 容器与数据同步：`...\menu\MachineControllerMenu.java:36-43`（2 个机器槽 + 36 个玩家槽 + 4 个 `ContainerData` 同步字段）、`:46-49`（`progressValue/maxProgressValue/energyPercent/formedValue`）
- **缺什么**
  1. 旧版面板高 213（`GuiMachineController.java:45`），新版 166（`MachineControllerScreen.java:17`），信息区被压缩；
  2. 旧版背景是 256×256 贴图整图绘制，新版是纯色矩形拼接，**没有任何贴图绑定或 blit 调用**；
  3. 旧版控制器 GUI 展示蓝图机名/结构名/状态/配方进度/并行度/Avg μs per t 与 WorkMode（`GuiMachineController.java:49-176`），新版只有"结构未形成/已形成"、能源百分比、进度分数（`MachineControllerScreen.java:42-44`）；
  4. 旧版有 `ControllerGUIRenderEvent` 扩展点（`GuiMachineController.java:104-107`），新版不存在任何 GUI 扩展事件。

### 2.2 控制器 GUI 的本地化外壳——"部分迁移"

- **旧版证据**：全部文案走 `I18n.format(...)`：`...\client\gui\GuiMachineController.java:63`、`:78`、`:87`、`:95`、`:120`、`:125`、`:140`、`:148`、`:151`
- **新版证据**：`...\client\MachineControllerScreen.java:42`（`Component.translatable("gui.modular_machinery_reborn.formed")`）、`:45`（`Component.translatable("container.inventory")`）；对应语言键确实存在：`modular-machinery-reborn\src\main\resources\assets\modular_machinery_reborn\lang\zh_cn.json:1`、`...\lang\en_us.json:1`
- **缺什么**：同一屏内三条文案（`:42` 的 `"结构未形成"`、`:43` 的 `"能源 "`、`:44` 的 `"进度 "`）用 `Component.literal` 硬编码中文，未接入语言键，英文本地化下会直接显示中文。

### 2.3 JEI 配方界面——"部分迁移"

- **旧版证据**：`...\github\kasuminova\mmce\client\gui\integration\GuiBlueprintScreenJEI.java:14`（`GuiBlueprintScreenJEI extends GuiScreenDynamic`）；预览面板工厂 `...\client\preivew\PreviewPanels.java:25-38`（按 `DynamicMachine` 缓存并创建 `MachineStructurePreviewPanel`）；旧版 JEI 配方图标贴图 `assets\modularmachinery\textures\gui\jeirecipeicons_ce.png`
- **新版证据**：`...\client\jei\ModularMachineryJeiPlugin.java:19-23`（注册类别/配方/催化剂）、`...\client\jei\MachineRecipeCategory.java:20-38`（`createBlankDrawable(150, 70)` 空白背景 + 输入/输出两个槽 + 一行 tick/FE 文本）
- **缺什么**：旧版 JEI 里能打开**带 3D 结构预览的蓝图界面**；新版 JEI 类别是空白背景的占位实现，无结构预览、无自定义图标贴图（用 `createDrawableIngredient` 取控制器物品栈充当图标，`MachineRecipeCategory.java:24`）。

---

## 三、未迁移

### 3.1 动态 GUI 框架核心：`DynamicWidget` + `WidgetController` + `WidgetGui`

- **旧版证据**：`...\github\kasuminova\mmce\client\gui\widget\base\DynamicWidget.java:22`（抽象组件基类，560 行）、`:71/:92/:112`（preRender/render/postRender 三阶段）、`:126/:138`（update/onGUIClosed）、`:159/:179/:201/:221/:244/:261/:279`（鼠标点击/全局点击/拖拽/释放/滚轮/键盘/自定义 GuiEvent 七类事件钩子）、`:305/:311/:319`（tooltip 与命中判定）、`:355-460`（绝对坐标 + 四边 margin）；`...\base\WidgetController.java:24`（控制器）、`:48-82/:84-109`（两趟渲染 + `TRANSLATE_STATE` 位移栈）、`:122/:130/:135`（init/update/onGUIClosed）、`:148-237`（六类事件分发 + 取消传播）、`:239-265`（tooltip 命中链）；`...\base\WidgetGui.java:12`、`:27-31`（可绑定 `GuiContainer` 或 `GuiScreen` 两种宿主）
- **新版证据**：`modular-machinery-reborn\src\main\java\com\reborn\modularmachinery\client\` 仅 4 个文件（`ClientSetup.java` 10 行、`MachineControllerScreen.java` 48 行、`jei/MachineRecipeCategory.java` 39 行、`jei/ModularMachineryJeiPlugin.java` 24 行），全项目客户端无 `DynamicWidget`/`WidgetController`/`WidgetGui` 任何同名或等价实现（对该目录 grep `(?i)widget|scrollbar|button` 仅命中 `MachineControllerScreen.java` 内的 `renderBg`/`renderLabels`）
- **规模对比**：旧版 `gui\widget\` 共 38 个文件、4730 行；`gui\` 整体 6675 行；新版客户端 UI 相关 0 行。

### 3.2 布局容器组件：`WidgetContainer` / `Column` / `Row` / `ScrollingColumn` / `SingletonWidgetColumn`

- **旧版证据**：`...\gui\widget\container\WidgetContainer.java:23`（抽象容器基类）、`:153/:155`（`addWidget`/`addWidgets`）；`...\container\Column.java:16`（纵向布局，328 行，含 `getWidgetRenderOffset` `:270`、左/右/居中对齐 `:353-377`）；`...\container\Row.java:16`（横向布局，325 行，`getWidgetRenderOffset:267`、上/下/居中对齐 `:350-374`）；`...\container\ScrollingColumn.java:16`（滚动纵向布局，319 行，含 `getTotalHeight:366` 与滚轮裁剪）；`...\container\SingletonWidgetColumn.java:5`（单子组件占位列）；`...\container\Selectable.java:3`（可选接口）
- **新版证据**：不存在。新版槽位坐标全部硬编码在 `...\menu\MachineControllerMenu.java:38-42` 与 `...\client\MachineControllerScreen.java:35-36`。

### 3.3 按钮组件族：`Button` / `Button4State` / `Button5State` / `ButtonElements`

- **旧版证据**：`...\gui\widget\Button.java:20`（三态按钮：normal/hovered/unavailable，`:22-27` 三个 `TextureProperties`，`:33-46` 状态选择渲染，`:144` 点击回调，`:153` tooltip 函数）；`...\widget\Button4State.java:17`（四态，增加 mouseDown，`:72`）；`...\widget\Button5State.java:15`（五态，增加 clicked，`:59`、`:73/:77`）；`...\widget\ButtonElements.java:13`（多元素轮换按钮，`findNextElement:48`、`addElement:79`）
- **新版证据**：不存在。新版仅 JEI 类别内有 `IDrawable` 背景/图标（`...\client\jei\MachineRecipeCategory.java:22-24`），无任何可交互按钮。

### 3.4 `Scrollbar` 滚动条组件

- **旧版证据**：`...\gui\widget\Scrollbar.java:13`、`:14/:15`（默认滑轨宽 6 / 滑块高 27）、`:17`（默认贴图坐标）；被预览层滚动条复用：`...\widget\impl\preview\LayerRenderScrollbar.java:121`（`getScrollbar()`）
- **新版证据**：不存在。

### 3.5 其它通用组件：`MultiLineLabel` / `TextureOverlay` / `HorizontalLine` / `ButtonElements` 文本与装饰件

- **旧版证据**：`...\gui\widget\MultiLineLabel.java:15`（多行文本，`getMaxStringWidth:97`、`getTotalHeight:111`、`getScale:129`）；`...\widget\TextureOverlay.java:12`；`...\widget\HorizontalLine.java:11`
- **新版证据**：不存在，新版文本全部用 `g.drawString(...)` 单行直写（`...\client\MachineControllerScreen.java:40-45`）。

### 3.6 GUI 宿主基类：`GuiScreenDynamic` / `AEBaseGuiContainerDynamic`

- **旧版证据**：`...\gui\GuiScreenDynamic.java:13`（无容器的组件化 Screen 基类）、`:15`（`protected WidgetController widgetController`）、`:22`（`updateScreen` 内 `widgetController.update()`）；`...\gui\AEBaseGuiContainerDynamic.java:18`（AE2 版容器基类）
- **新版证据**：不存在，新版直接继承 `AbstractContainerScreen`（`...\client\MachineControllerScreen.java:10`）、注册仅一行 `MenuScreens.register`（`...\client\ClientSetup.java:9`）。

### 3.7 虚拟槽位与槽位渲染（物品/流体/气体 + JEI 变体）

- **旧版证据**：`...\gui\widget\slot\SlotVirtual.java:12`（抽象虚拟槽）；`...\slot\SlotItemVirtual.java:23`；另有 `SlotItemVirtualJEI`、`SlotItemVirtualSelectable`、`SlotItemVirtualSelectableJEI`、`SlotFluidVirtual`、`SlotFluidVirtualJEI`、`SlotGasVirtual`、`SlotGasVirtualJEI`（`...\gui\widget\slot\` 下共 9 个文件）；容器侧槽位扩展 `...\gui\slot\GuiFullCapFluidTank.java:11`、`...\gui\slot\Size1Slot.java:22`
- **新版证据**：不存在。新版只有两个原版 `SlotItemHandler`（`...\menu\MachineControllerMenu.java:38-39`），无虚拟槽、无流体/气体槽渲染。

### 3.8 结构预览渲染器核心：`WorldSceneRenderer` 抽象族

- **旧版证据**：`...\com\cleanroommc\client\preview\renderer\scene\WorldSceneRenderer.java:70`（抽象类，847 行）、`:71-75`（矩阵/视口/深度缓冲）、`:130-147`（`setDefaultPassRenderState` SOLID/TRANSLUCENT 两趟渲染状态）、`:149-163`（`useCacheBuffer`，依赖 `OpenGlHelper.useVbo()`）、`:165-205`（`deleteCacheBuffer`，异步回收 `VertexBuffer`）、`:207-222`（`needCompileCache`/`stopCompileCache`）、`:265-286`（`render` 入口：相机 → 世界 → 射线拾取）、`:300-320`（`setCameraLookAt`：eye/lookAt/worldUp 与球坐标两种寻址）、`:326-387`（`setupCamera`/`clearView`/`resetCamera`，含 `gluPerspective(60, aspect, 0.1, 10000)`）、`:438-444`（`getCompileProgress`）、`:454-465`（每 `BlockRenderLayer` 一个 `VertexBuffer`）、`:481-498`（`refreshCache` 起编译线程 `MMCE-PreviewCompiler-N`）、`:500-528`（`renderDefault` 逐渲染层 Tessellator 绘制）、`:530-576`（`renderCacheBuffer` VBO `drawArrays`）、`:578-636`（`compileCache` 后台线程编译）、`:645-669`（`renderBlocks` 按 layer 分发）、`:680-713`（`renderTESR` 两个 pass）、`:715-720`（`rayTrace`）、`:722-753`（`project`，`gluProject` 世界→屏幕）、`:755-798`（`unProject`，`glReadPixels` 深度 + `gluUnProject`）、`:840-845`（`CacheState` 状态机 UNUSED/NEED/COMPILING/COMPILED）
- **同族实现**：`...\preview\renderer\scene\ImmediateWorldSceneRenderer.java`（47 行）、`...\preview\renderer\scene\FBOWorldSceneRenderer.java`（117 行，FBO 离屏渲染）、`...\preview\renderer\scene\ISceneRenderHook.java`（9 行渲染钩子）
- **新版证据**：不存在。全项目 java 源码 grep `preview|Preview|PoseStack|RenderType|VertexConsumer|ShaderInstance` **零命中**（仅命中语言文件中的 `machine_projector` 名称字符串与 `ModItems.java:15` 的物品注册名）。

### 3.9 3D 预览组件与切片预览：`WorldSceneRendererWidget` + `LayerRenderScrollbar`

- **旧版证据**：`...\gui\widget\impl\preview\WorldSceneRendererWidget.java:56`（569 行预览组件）、`:58-60`（用 `ImmediateWorldSceneRenderer` + `LRDummyWorld(TrackedDummyWorld)`）、`:67-73`（`useLayerRender`/`renderLayer` 切片态、`rotationYaw=25`/`rotationPitch=-135`）、`:75-76`（`AnimationValue` 缩放动画与默认缩放 5）、`:119-162`（`initPattern` 把 `BlockArray` 灌入 dummy world，支持 `useLayerRender` 按 Y 过滤 `:132`、`structureFormed` 隐藏组件 `:135`）、`:188-200`（动态结构 `DynamicPattern` 尺寸注入）、`:235-268`（`initRenderer`：选中块高亮 `:241`、叠加色块 `:243-250`、已成形时渲染控制器模型 `:251-263`、相机初始化 `:265`）、`:270-280`（`checkCacheRenderer`：加载着色器包时禁用 VBO 缓存，否则启用）、`:285-291`（每 1.5s 轮换可替换方块 `cycleBlocks`）、`:306-331`（渲染期处理缩放动画与鼠标拖拽）、`:338-344`（滚轮缩放 `clamp(defaultZoom/80, defaultZoom*40)`）、`:347-356`（左键拖拽旋转）、`:368-402`（左键旋转、右键平移的相机算法）、`:409-427`（点击拾取方块）、`:429-445`（`preInitNextRenderedCore`：按包围盒自动定焦距 `3.5*sqrt(maxSpan)`）、`:477-505`（`useLayerRender`/`use3DRender`/`setRenderLayer` 切换）
- **切片滚动条**：`...\gui\widget\impl\preview\LayerRenderScrollbar.java:16`（`extends Column`）、`:112-121`（滚动回调与 `Scrollbar` 暴露）；面板侧联动 `...\preview\MachineStructurePreviewPanel.java:374-378`（`handleLayerScrollbarChanged` 把滑块值映射到 Y 层）、`:407-421`（切换 2D/3D：设置 `layerScrollbar` 范围 `minY..maxY`）
- **新版证据**：不存在。

### 3.10 世界投影放置预览（蓝图"投影到世界"）

- **旧版证据**：`...\hellfirepvp\modularmachinery\client\util\BlockArrayPreviewRenderHelper.java:81-96`（`startPreview` 接收 `DynamicMachineRenderContext` 并提示 `gui.blueprint.popout.place`）、`:98-128`（`placePreview` 射线拾取 20 格内方块并吸附/按控制器朝向旋转 `:108-113`）、`:130-149`（`tick` 逐层校验并自适应下降层）、`:151-191`（`renderTranslucentBlocks` 用显示列表 + `GL_ONE_MINUS_DST_COLOR` 混合渲染半透明幽灵结构）、`:193-215`（结构哈希，变化即重建显示列表）、`:217-280`（`batchBlocks` 编译显示列表，逐块 0.75 缩放渲染）、`:299-348`（`doesPlacedLayerMatch`/`hasLowerLayer`/`updateLayers` 逐层匹配进度）
- **渲染入口**：`...\client\util\SelectionBoxRenderHelper.java:36-56`（`RenderWorldLastEvent` 中绘制选中方块白框并调用 `ClientProxy.renderHelper.renderTranslucentBlocks()`）、`:59-71`（右键触发 `placePreview` 并取消原事件）、`:73-84`（断线/换世界清理）；helper 实例：`...\client\ClientProxy.java:105`（`public static final BlockArrayPreviewRenderHelper renderHelper = new BlockArrayPreviewRenderHelper();`）；白框绘制 `...\client\util\RenderingUtils.java:42`（`drawWhiteOutlineCubes`）
- **新版证据**：不存在。新版 `machine_projector` 只是一个无逻辑物品名：`...\item\ModItems.java:15`（`MACHINE_PROJECTOR = register("machine_projector", 1)`）+ 贴图模型 `assets\modular_machinery_reborn\models\item\machine_projector.json:1`；全项目无 `RenderLevelStageEvent` / `EntityBlockRenderer` / `registerBlockEntityRenderer` 注册。

### 3.11 切片/世界预览的渲染辅助与假世界（dummy world）栈

- **旧版证据**：`...\hellfirepvp\modularmachinery\client\util\BlockArrayRenderHelper.java:114-118`（`render3DGUI`，含可选 `slice` 参数）、`:295-296`（`respectRenderSlice`/`currentRenderSlice`）、`:366-367`（按层裁剪渲染）；`...\client\util\DynamicMachineRenderContext.java:53-54`（`renderSlice`/`scale`）、`:227`（`getScale`）、`:247-278`（`sliceUp`/`sliceDown` 与 `hasSliceUp/Down`）；`...\com\cleanroommc\client\util\world\DummyWorld.java`、`LRDummyWorld.java`、`DummyChunkProvider.java`、`DummySaveHandler.java`、`TrackedDummyWorld.java`、`EntityCamera.java`、`LRVertexBuffer.java`、`LRMap.java`、`Quat.java`、`Vector3.java`、`BlockInfo.java`
- **新版证据**：不存在。

### 3.12 VBO / 顶点缓冲池与网格渲染优化

- **旧版证据**：`...\github\kasuminova\mmce\client\util\ReusableVBOUploader.java:13`（继承 `WorldVertexBufferUploader` 的复用上传器）、`:49-92`（`drawMultiple` 合并多 `BufferBuilder` 一次 `glDrawArrays`）、`:94-99`（`checkBufferSize` 复用 direct ByteBuffer）；`...\client\util\BufferBuilderPool.java`（62 行缓冲池）、`BufferProvider.java`、`...\client\model\ModelPool.java`、`ModelBufferSize.java`
- **新版证据**：不存在。

### 3.13 控制器 3D 模型 / 几何模型 / 泛光（Bloom）着色器互通

- **旧版证据**：`...\client\renderer\BloomGeoModelRenderer.java:20`（`implements IRenderSetup, IBloomEffect`）、`:30-36`（`BloomEffectUtil.registerBloomRender(this, BloomType.UNREAL, ...)`）、`:51-63`（`renderBloomEffect`）；`...\client\renderer\ControllerModelRenderManager.java`（97 行）、`...\client\renderer\GeoModelRenderTask.java`（350 行）、`...\client\renderer\MachineControllerRenderer.java`（309 行）、`...\client\renderer\RenderType.java`、`...\client\model\MachineControllerModel.java`、`DynamicMachineModelRegistry.java`、`StaticModelBones.java`、`...\client\resource\GeoModelExternalLoader.java`、`...\client\util\MatrixStack.java:12`（GeoBone/GeoCube 矩阵栈）
- **着色器检测**：`...\com\cleanroommc\client\shader\ShaderManager.java:13-33`（反射读取 OptiFine `net.optifine.shaders.Shaders.shaderPackLoaded`）、`:36-38`（`isOptifineShaderPackLoaded()`）；消费方 `...\gui\widget\impl\preview\WorldSceneRendererWidget.java:271-279`、`...\gui\widget\impl\preview\PreviewStatusBar.java:48`、`:63-67`（着色器包加载 / 不支持 VBO 的警告文案 `gui.preview.optifine_shader_pack_warn`、`gui.preview.vbo_unsupported_warn`）
- **新版证据**：不存在（无模型注册、无 `ShaderInstance`、无泛光）。

### 3.14 蓝图预览界面（Blueprint Screen）

- **旧版证据**：`...\hellfirepvp\modularmachinery\client\gui\GuiScreenBlueprint.java:29`（`GuiScreenBlueprint extends GuiScreenDynamic`）、`:30-34`（贴图 `guiblueprint_new.png`、面板 184×220）、`:47`（创建 `WidgetController`）、`:63-67`（`drawScreen` 中清空并挂载 `PreviewPanels.getPanel(machine, ...)`）；面板本体 `...\github\kasuminova\mmce\client\gui\widget\impl\preview\MachineStructurePreviewPanel.java:37`（`extends Row`）、`:38-47`（两套贴图 + 184×220 面板 + 172×150 渲染区）、`:51-260`（标题栏、状态栏、成分列表、选中方块槽、升级列表、层滚动条、底部/右上一/右侧三组按钮装配）、`:284-325`（机器额外信息 tooltip：XYZ 尺寸、控制器 Y、并行度、线程、动态结构、是否需蓝图）、`:327-351`（点击结构块显示该块与可替换成分）、`:353-372`（随结构更新刷新成分列表）；面板缓存 `...\client\preivew\PreviewPanels.java:17-23`（10 个、60 秒过期、移除时派发 `WorldRendererCacheCleanEvent`）；JEI 版入口 `...\github\kasuminova\mmce\client\gui\integration\GuiBlueprintScreenJEI.java:14`
- **新版证据**：不存在。`itemblueprint` 仅有名称与模型（`...\lang\zh_cn.json:1`、`assets\modular_machinery_reborn\models\item\itemblueprint.json`），无 Screen、无 `MenuType`、无右键打开逻辑。

### 3.15 预览辅助组件：成分列表 / 升级列表 / 状态栏 / 标题 / 模式提供器列表

- **旧版证据**：`...\gui\widget\impl\preview\IngredientList.java:17`（`extends ScrollingColumn`，`:51` `setStackList(List<ItemStack>, List<FluidStack>)`）、`:28-34`（更新与初始化）、`:60/:72`（槽位贴图 `TextureProperties.of(WIDGETS_TEX_LOCATION, 184, 194)`）；`...\preview\IngredientListVertical.java:17`、`:87`；`...\preview\UpgradeIngredientList.java:24`（升级替换成分）；`...\preview\PreviewStatusBar.java:16`（`:60` 编译进度、`:63-67` 警告）、`...\preview\StructurePreviewTitle.java:9`（`extends Row`）；`...\widget\impl\patternprovider\PatternProviderIngredientList.java:21`、`:24`
- **新版证据**：不存在。

### 3.16 ME 总线与模式提供器 GUI、Smart Interface、Fluid/Gas 槽 GUI

- **旧版证据**：`...\gui\GuiMEPatternProvider.java`（224 行，贴图见 `...\widget\impl\patternprovider\PatternProviderIngredientList.java:24` 引用 `GuiMEPatternProvider.GUI_TEXTURE`）、`GuiMEItemInputBus.java`（229 行）、`GuiMEItemOutputBusStackSize.java`（265 行）、`GuiMEFluidInputBus.java`、`GuiMEFluidOutputBus.java`、`GuiMEGasInputBus.java`、`GuiMEGasOutputBus.java`、`GuiMEItemBus.java`、`GuiMEItemOutputBus.java`；`...\hellfirepvp\...\client\gui\GuiContainerSmartInterface.java`（176 行）、`GuiContainerParallelController.java`（198 行）、`GuiContainerUpgradeBus.java`（148 行）、`GuiContainerGroupInputConfig.java`（122 行）、`GuiContainerFluidHatch.java`（182 行）、`GuiContainerEnergyHatch.java`、`GuiContainerItemBus.java`、`GuiContainerBase.java:51`、`GuiFactoryController.java`（326 行）
- **新版证据**：不存在，仅 `MachineControllerScreen.java` 一个 Screen。

### 3.17 GUI 贴图资源（23 个 png）全部未迁移

- **旧版证据**（`...\src\main\resources\assets\modularmachinery\textures\gui\`）：`guibar.png`、`guiblueprint_new.png`、`guiblueprint_new_second.png`、`guicontroller_large.png`、`guifactory.png`、`guifactoryelements.png`、`guismartinterface.png`、`guitank.png`、`guiupgradebus.png`、`inventory_big/huge/ludicrous/normal/reinforced/small/tiny.png`、`jeirecipeicons_ce.png`、`mefluidinputbus.png`、`mefluidoutputbus.png`、`meiteminputbus.png`、`mepatternprovider.png`、`stacksize.png`、`widgets.png`（均为 256×256 或贴图集切分）
- **新版证据**：`modular-machinery-reborn\src\main\resources\assets\modular_machinery_reborn\textures\gui\` 下**只有** `controller_legacy.png` 与 `.keep`（glob `**/textures/gui/**` 确认）；且 `controller_legacy.png` 在全部 Java 源码中**零引用**（grep `controller_legacy|textures/gui|blit` 无命中）——即除控制器大图外的 22 个贴图全部缺失，唯一拷贝过来的那张也没被绑定使用。

### 3.18 客户端 tick 调度工具 `ClientScheduler`

- **旧版证据**：`...\hellfirepvp\modularmachinery\client\ClientScheduler.java`（78 行）；消费者 `...\gui\widget\impl\preview\WorldSceneRendererWidget.java:90`（`ClientScheduler.getClientTick()`）、`:287`
- **新版证据**：不存在（1.20.1 可直接用 `Level.getGameTime()`，故此处属"未迁移但可替代"）。

---

## 四、设计差异

### 4.1 新版自创：纯程序化 GUI 绘制，放弃贴图与数据驱动

- 旧版：控制器面板由 256×256 贴图整图绘制（`...\GuiMachineController.java:181-184`），其余界面由 `TextureProperties(texRes, texX, texY, width, height)` 描述并 `drawTexturedModalRect` 切片（`...\gui\util\TextureProperties.java:17`、`:45-60`、`:180-187`），预览面板以 `widgets.png`/`guiblueprint_new.png` 的固定坐标（如 `(184,15)`、`(184,30)`、`(184,214)`、`(229,105)`，见 `...\MachineStructurePreviewPanel.java:102-183`）取图。
- 新版：`MachineControllerScreen.java:18-38` 全部用 `g.fill(x1,y1,x2,y2,argb)` 画 11 个纯色矩形与 2 个机器槽 + 36 个玩家槽，无任何贴图绑定或 blit；常量色板定义在 `:11-16`。
- 差异性质：**渲染范式整体替换**，不是移植。

### 4.2 新版自创：已拷贝旧贴图但未复用

- `controller_legacy.png` 与旧版 `guicontroller_large.png` 的 SHA-256 完全一致（`FB73DAE4...D94B4`，9633 字节，256×256），说明是有意拷贝的"旧贴图备件"；但新版源码零引用，GUI 仍为程序化重绘。
- 结论：**"是否复用旧贴图"的答案是：贴图被搬了过来，但一张都没被用上。**

### 4.3 语义改变：单屏控制器 GUI 取代组件化控制器 GUI

- 旧版控制器 GUI 只承担信息展示（蓝图机名、结构名、状态、配方进度%、并行度/最大并行度、`Avg μs/t` 与 WorkMode，`...\GuiMachineController.java:75-172`），功能控件由 `GuiFactoryController` 等独立界面提供。
- 新版把"能量百分比 + 进度分数 + 结构是否形成"压进 176×166 的单一面板（`...\MachineControllerScreen.java:17`、`:39-46`），旧版 213 高的面板缩到 166，且无并行度/工作模式/耗时统计/结构名称显示。
- 语义上"控制器界面"这一名称保留，但**承载的信息模型被替换**（`ContainerData` 4 个 int，`...\MachineControllerMenu.java:15-35`）。

### 4.4 设计差异：i18n 纪律回退

- 旧版客户端文案**全部**走 `I18n.format`（例如 `...\GuiMachineController.java` 9 处、`...\LayerRenderScrollbar.java:40-74`、`...\PreviewStatusBar.java:60-67`、`...\MachineStructurePreviewPanel.java:107-322`）。
- 新版在 `lang\zh_cn.json:1` / `lang\en_us.json:1` 已准备 `gui.modular_machinery_reborn.formed` 等键，却在 `...\MachineControllerScreen.java:42-44` 用 `Component.literal` 硬编码中文 `"结构未形成"`、`"能源 "`、`"进度 "`，绕过了自己的语言文件。

### 4.5 设计差异：缺失的结构预览生态位被"物品名"占位

- 旧版"结构预览"有 4 个层次：GUI 内 3D 预览（`WorldSceneRendererWidget`）、GUI 内 2D 切片预览（`useLayerRender` + `LayerRenderScrollbar`）、世界内投影放置（`BlockArrayPreviewRenderHelper`）、JEI 蓝图界面（`GuiBlueprintScreenJEI`）。
- 新版保留 `machine_projector`（`...\item\ModItems.java:15`）、`itemblueprint`、`itemconstructtool`、`wrench` 四个物品**名称与贴图**，但无任何客户端行为实现——属于"命名占位而非功能迁移"。

### 4.6 旧有新无：预览渲染的性能工程

- 旧版有：VBO 缓存（`WorldSceneRenderer.java:149-163`、`:530-576`）、后台编译线程与进度（`:481-498`、`:578-636`、`:438-444`）、`BlockRenderLayer` 分层缓冲（`:454-465`）、缓冲区池（`BufferBuilderPool`、`ReusableVBOUploader.java:49-99`）、OptiFine 着色器互斥降级（`ShaderManager.java:36-38` + `WorldSceneRendererWidget.java:270-280`）、面板缓存与过期清理（`PreviewPanels.java:17-23`）。
- 新版：无对应物（当前无预览功能，故暂不需要）。

---

## 五、关键发现

1. **旧版 GUI 是一个可独立成库的通用组件框架，规模 38 个文件、4730 行**（`gui\widget\` 全量统计），含三阶段渲染、七类事件钩子、Row/Column/ScrollingColumn 布局与五态按钮；新版客户端 UI 代码合计 **0 行**属于该框架，控制器屏只有 46 行程序化绘制。
2. **旧版结构预览渲染栈是本领域最重的资产：`WorldSceneRenderer` 单类 847 行**，配合 slice 切片、`gluProject/unProject` 世界↔屏幕投影、每渲染层 VBO 与后台编译线程、假世界（DummyWorld/TrackedDummyWorld）；新版全项目源码 grep `preview/PoseStack/RenderType/VertexConsumer` **零命中**，即整条渲染栈未迁移一行。
3. **蓝图预览界面（`GuiScreenBlueprint` 184×220 + `MachineStructurePreviewPanel` 422 行）与世界投影放置（`BlockArrayPreviewRenderHelper` 367 行）在 1.20.1 侧完全不存在**，新版只剩 `machine_projector`/`itemblueprint` 之类无行为的物品名与贴图占位。
4. **新版 GUI 是程序化重绘，不是复用旧贴图**：`MachineControllerScreen.java:20-38` 全部是 `g.fill` 纯色矩形；同时 `...\textures\gui\controller_legacy.png` 与旧版 `guicontroller_large.png` 字节完全一致（SHA-256 `FB73DAE4...`），却被源码零引用，旧版 23 个 GUI 贴图中 22 个连文件都没有拷过来。
5. **新版存在 3 处硬编码中文**：`...\client\MachineControllerScreen.java:42`（`"结构未形成"`）、`:43`（`"能源 "`）、`:44`（`"进度 "`），而同一屏的 `:42` 另一分支与 `:45` 已正确使用语言键，说明项目已建好 i18n 机制却未贯彻。
6. **控制器 GUI 的信息模型被大幅裁剪**：旧版展示蓝图机名/结构名/状态/配方进度/并行度/`Avg μs/t` 与 WorkMode 并支持 `ControllerGUIRenderEvent` 扩展（`GuiMachineController.java:75-172`），新版仅剩"结构是否形成 + 能源% + 进度分数"（`MachineControllerScreen.java:42-44`），面板高度由 213 降到 166。

---

## 六、不确定项

1. 我**未运行**游戏或客户端，新版 1.20.1 侧 `MachineControllerScreen` 的实际渲染效果、是否有运行期异常（例如 1.20.1 中 `AbstractContainerScreen.renderBg` 与 `render` 的叠加是否导致背景重复绘制）均未验证。
2. 我未核实新版 `MachineControllerBlock` / `MachineControllerBlockEntity` 是否通过其它途径（如 BlockEntity 自带 renderer、blockstate 模型）承担了部分客户端渲染；我只在 `src\main\java` 范围内 grep 了 `registerBlockEntityRenderer`/`RenderLevelStageEvent`/`RenderType`，结果为 0，但未逐一阅读全部 17 个 Java 文件。
3. 我未核实 `controller_legacy.png` 是否被新版之外的其他模块（如 datagen、资源打包脚本、`build\` 产物）引用；grep 范围限于 `modular-machinery-reborn\src`。
4. 我未逐行阅读旧版全部 38 个 widget 文件（例如 `WidgetContainer.java`、`Scrollbar.java` 仅读取关键行与符号清单），因此组件清单可能遗漏个别次要方法或子类细节（文件清单本身由目录枚举完整获得）。
5. 我未核实 `MachineRecipeCategory.java:24` 的 `createDrawableIngredient` 图标在 JEI 中的实际呈现样式，也未核实旧版 `jeirecipeicons_ce.png` 是否在新版由其它方式替代。
6. 我未核实数据驱动层面（如 KubeJS 注册的机器 schema）是否会在后续版本触发客户端预览；`kubejs\ModularMachineryKubeJSPlugin.java` 不在本次审计范围内，未阅读。
7. 本报告沿用任务给定的目录清单；`...\mmce\client\preivew\`（拼写为 preivew）与 `...\client\preview\`（`com.cleanroommc` 下）为两处不同目录，我按实际枚举结果分别处理，未发现其它同名预览目录。
