# M6d-b 设计：智能数据接口并入控制器

> **状态：已于 0.24.0 实施。** 本文件是设计记录；决策已提炼为 `移植方案-v2.md` 的 **D16** 记录，实施详情
> 见 `迁移日志.md` 的 0.24.0 一节。
>
> 决策时间：0.22.0 之后、0.23.0（M6e-2）进行中。实施：0.24.0。
>
> **实施时的两处落定**（设计文档留给实施阶段定的两件事）：
>
> 1. **字符串类型不做。** 证据：旧版方块枚举 `SmartInterfaceTypeEnum` 只声明 `NUMBER`；旧版里读接口值的
>    **唯一**地方是 `RequirementInterfaceNumInput`（两个 `float` 字段）；`IMachineController` 的两个取值
>    方法没有第二个消费者。故不做字符串变体（旧版的 `blocksmartinterface_string` 模型/贴图与
>    `.*string.name` 语言键是死资源）。
> 2. **机器 JSON 字段名。** 旧版 schema 里接口类型名也叫 `type`，与需求的种类判别字段同名——这正是旧版
>    `RequirementTypeInterfaceNumInput#createRequirement` 返回 `null`、该需求**从来无法从 JSON 出现**的
>    原因。本项目机器定义用根字段 `smart-interfaces`（数组，元素含 `type`/`default`/`priority`/`header`/
>    `value`/`footer`/`notequal`），需求里用 `interface` 字段指类型名，先读种类再读它，撞车因此不可能发生。

## 决定

**不移植独立方块「智能数据接口」，把它的功能并入控制器。**

## 这是对原版结构的**有意偏离**，理由

原版里它是**独立方块**，且是**结构组件**：

- `BlockSmartInterface`(120) / `TileSmartInterface`(179) / `GuiContainerSmartInterface`(203) / `PktSmartInterfaceUpdate`(60)
- `TileSmartInterface.SmartInterfaceProvider extends MachineComponent<…>` —— 与仓口同性质，会被结构扫描收集
- 控制器通过 `foundSmartInterfaces`（`TileMultiblockMachineController:135`）收集它，并在 `checkAndAddSmartInterface`（`:909`）里**把控制器自己登记为该接口的「绑定机械」**
- 需求取值走控制器：`getSmartInterfaceData(String requiredType)`（`:1113`）、`getSmartInterfaceDataList()`（`:1128`）

**也就是说：接口是独立方块，但控制器本来就是唯一的访问入口。** 并入后，值的存放位置从「方块实体」搬到「控制器方块实体」，界面从「独立界面」搬到「控制器界面」——**访问路径没有变化**，消失的只是一个中间方块。

代价（必须在 `D` 记录里写清）：与原版**方块清单不一致**；旧存档/旧数据包里若已放置该方块，迁移时需处理（本项目尚无该方块，故无历史包袱）。

## 但它依然是三件套，缺一即空壳

合并只解决第 3 件。**只做第 3 件会得到一个没有任何用途的数值输入框**——而方案第 5 节明令禁止占位 GUI。

| # | 组件 | 为什么必需 |
|---|---|---|
| **1** | **机器定义的「接口类型」声明** | 旧版的类型（数值/字符串、默认值、header/value/footer 文案、优先级）**只由 CraftTweaker 注册**（`MachineModifier.addSmartInterfaceType:27-38`、`MachineBuilder.addSmartInterfaceType:337-341`），`DynamicMachine.smartInterfaces` 在 JSON 里**没有任何入口**。本项目已改用 KubeJS（D7），所以需要新的机器 JSON 根字段（**第四处 schema 偏离**）。没有它，控制器不知道该提供几个、什么类型的数值。 |
| **2** | **`interface_number_input` 需求类型**（消费者） | 原版唯一读这个值的就是 `RequirementInterfaceNumInput`，而它在 M2 的「未移植需求类型」清单里（`交接文档.md`）。**没有它，数值没人读**。相关文案已存在：`craftcheck.failure.interface.number.notequal` = 「智能数据接口输入的数值不同！」、`component.missing.modularmachinery.interface.number` = 「没有找到对应类型的智能数据接口！」、`tooltip.machinery.smartinterface.{value,minvalue,maxvalue}`。 |
| **3** | **值的存放与编辑界面**（本次决定并入控制器） | 原版的独立方块与界面。并入后：值存在控制器方块实体，编辑在控制器界面。原版界面的功能要保留：显示**已绑定对象**、**上一个/下一个**切换、**当前值**编辑（`gui.smartinterface.title` = 「智能数据接口（已绑定：%s，当前：%s）」、`.value`、`.prev`、`.next`、`.notfound`）。 |

## 界面并入后的形态（与原版独立界面的对应）

| 原版独立界面 | 并入控制器后 |
|---|---|
| 标题「智能数据接口（已绑定：%s，当前：%s）」 | 控制器界面里的一块区域，显示当前值 |
| 「上一个 / 下一个」切换绑定对象 | 并入后**不需要**——控制器只有一台机器，就是它自己（这正是合并带来的简化） |
| 「当前值：%.0f」 | 保留，可编辑 |
| 「未找到绑定机械。」 | **不再需要**（控制器必然知道自己是谁） |

## 明确不做

- **不移植 `GuiContainerSmartInterface` 做成独立界面**（本次决定的目的就是不做它）
- **不做字符串类型**，除非第 1 件的 schema 字段明确要求——原版有数值/字符串两种（`SmartInterfaceTypeEnum`），字符串类型的消费者是否存在需在实施时确认；**若找不到消费者，就不做，并如实记录**（避免做空壳）。
- 不做 `PktSmartInterfaceUpdate` 那种独立同步包：并入控制器后走控制器既有的菜单 `ContainerData` 机制。

## 实施时的硬约束

- 配置若需要，**只能加进现有的唯一 `config/ModConfig.java`**——**第二个 `COMMON` Spec 就是 0.21.0 启动崩溃的原因**。
- 机器 JSON 的新字段要按项目惯例**响亮校验**（写错要说该写成什么）。
- 发布前跑**四条必查项**（见 `交接文档.md` 的「发布必查项」）。
- 不得改动 `client/preview/**` 的渲染数学与输入设计（`getArea()` 整面板、`PointerClaim`）——除非要往机器信息里加行，那也只允许扩文字。