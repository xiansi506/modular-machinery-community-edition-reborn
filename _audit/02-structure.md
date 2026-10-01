# 机器定义与多方块结构

> 审计方式：读取旧版机器定义反序列化源码、4 台内置机器 JSON，以及新版结构判定与控制器实现。根字段清单已用 grep 逐条核对行号。
>
> 旧版：`_mmce-src/ModularMachinery-Community-Edition-master/src/main/java/hellfirepvp/modularmachinery/common/machine/`、`src/main/resources/assets/modularmachinery/default_machinery/`、`default_variables/`
> 新版：`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/block/MachineStructure.java`、`MachineControllerBlock.java`、`MachineControllerBlockEntity.java`

## 结论摘要

**整个「数据驱动的机器定义」层——本模组的核心——没有迁移。** 新版结构是硬编码的「控制器同层 3×3 环去掉中心」共 8 格，形状、尺寸、层数全部写死在代码里。旧版示例机器用到 3~5 层、可伸缩产线（5~15 格）。

## 一、旧版机器 JSON 根字段（已逐条核实）

证据：`common/machine/DynamicMachinePreDeserializer.java` 与 `common/machine/DynamicMachine.java`。下方行号为实际匹配行。

| 根字段 | 解析位置 | 语义 | 新版 |
|---|---|---|---|
| `registryname` / `registryName` | `DynamicMachinePreDeserializer.java:14,16`；`DynamicMachine.java:613,615` | 机器唯一注册名，缺失即抛异常 | **无**（无机器注册表） |
| `localizedname` | `DynamicMachinePreDeserializer.java:35`；`DynamicMachine.java:620` | 机器显示名 | **无**（控制器名固定） |
| `prefix` | `DynamicMachinePreDeserializer.java:25,103` | GUI 标题前缀 | **无** |
| `color` | `DynamicMachinePreDeserializer.java:62,118`；`DynamicMachine.java:641` | 机器主题色（十六进制） | **无** |
| `failure-action` | `DynamicMachinePreDeserializer.java:45,108`；`DynamicMachine.java:631` | 配方失败行为（reset/still/decrease） | **无**（固定为进度清零） |
| `requires-blueprint` | `DynamicMachinePreDeserializer.java:54,113`；`DynamicMachine.java:636` | 是否需要蓝图 | **无** |
| `has-factory` | `DynamicMachinePreDeserializer.java:77,123`；`DynamicMachine.java:646` | 是否生成工厂控制器 | **无** |
| `factory-only` | `DynamicMachinePreDeserializer.java:85,128`；`DynamicMachine.java:651` | 是否只作工厂 | **无** |
| `hide-components-when-formed` | `DynamicMachine.java:397,656` | 成型后隐藏结构方块 | **无** |
| `controller-bounding-box` | `DynamicMachine.java:596,661` | 控制器 6 值包围盒 | **无** |
| `parts` | `DynamicMachine.java:624` | 静态结构定义（必填） | **无** |
| `modifiers` | `DynamicMachine.java:494,671` | 单方块替换修饰器 | **无** |
| `dynamic-patterns` | `DynamicMachine.java:330,676` | 可伸缩结构 | **无** |

13 个根字段中，**13 项全部在新版无对应实现。**

### parts[] 子字段

| 字段 | 位置 | 语义 |
|---|---|---|
| `x` / `y` / `z` | — | 相对控制器（控制器隐式位于原点并从结构中剔除）的坐标；支持标量或数组，数组按笛卡尔积铺开多个位置 |
| `elements` | `DynamicMachine.java:543-591` | 方块状态描述符 `ns:block@meta`（字符串或数组）；`@meta` 即仓口等级声明，如 `modularmachinery:blockinputbus@0` |
| `selector-tag` | `DynamicMachine.java:289-290` | 结构位点选择器标签（输入/输出分组） |
| `nbt` / `preview-nbt` | `DynamicMachine.java:518-541` | 结构方块的 NBT 匹配与预览条件 |

`elements` 还支持**引用变量文件**（`*.var.json`），见 `default_variables/casings.var.json`，定义了 `casings_all` / `casings_decorative` / `casings_fluid` / `casings_energy` / `casings_item` 五组方块集合。

### modifiers[] 子字段

`elements`、`x`、`y`、`z`、`description`、`modifier{target, io, operation, multiplier}`。
实例：`alloy_furnace.json:4-18` 把控制器上方方块换成 vent 机箱，以 `"multiplier": 2.0` 翻倍物品输出。**这是一个「结构影响产能」的机制，新版完全没有。**

### dynamic-patterns[] 子字段（已核实）

| 字段 | 位置 |
|---|---|
| `name` | `DynamicMachine.java:355` 区块 |
| `faces` | 按朝向附着 |
| `minSize` / `maxSize` | 可伸缩长度范围 |
| `parts` / `parts-end` | `DynamicMachine.java:355,358-360` |
| `structure-size-offset` / `structure-size-offset-start` | `DynamicMachine.java:369-375` |

实例：`assembly_line.json` 的 `dynamic-patterns[0]` 字段实测为 `name, faces, minSize, maxSize, parts, parts-end, structure-size-offset-start, structure-size-offset`，`minSize=5`、`maxSize=15`、`faces=["north"]`——一条**长度可变 5~15 格的装配产线**。

## 二、内置 4 台机器实测（本人用 JSON 解析逐台核对）

| 机器 | 根字段 | 结构规模 |
|---|---|---|
| `alloy_furnace` | `registryname, localizedname, modifiers, parts` | y = −1..1（**3 层**），x,z 各 3 格 |
| `iron_centrifuge` | `registryname, localizedname, parts` | y = −1..1（**3 层**） |
| `power_transformer` | `registryname, localizedname, parts` | 32 个 parts，y = −1..3（**5 层**） |
| `assembly_line` | `registryname, localizedname, failure-action, requires-blueprint, color, dynamic-patterns, parts` | 静态 parts + 可伸缩产线 5~15 格 |

旧版结构尺寸由 parts 坐标极值自动推导，**没有 3×3 限制**，也**没有层数限制**。

## 三、新版实现（全部 17 个 Java 文件中结构相关者）

`MachineStructure.java` 全文 23 行，核心是：

```java
for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
    if (dx == 0 && dz == 0) continue;
    if (!isFrameBlock(level, controller.offset(dx, 0, dz))) return false;
}
```

- Y 偏移**写死为 0**（`:13`），只能扫描控制器所在单层
- 扫描范围**恒为 dx,dz ∈ [−1,1]**，形状只能是「3×3 缺中心的 8 格环」
- `isFrameBlock`（`:17-22`）白名单仅 5 项：`Blocks.IRON_BLOCK` + `ITEM_INPUT_HATCH` + `ITEM_OUTPUT_HATCH` + `ENERGY_INPUT_HATCH` + `FLUID_INPUT_HATCH`

`MachineControllerBlockEntity.java`：
- `:50` `be.formed = MachineStructure.isFormed(level, pos)` — 结构判定的唯一入口，**不接收朝向参数**
- `:70` 配方匹配用硬编码字符串：`r.machine().equals("basic") || r.machine().equals("machine_controller")`

**新版全项目检索确认：不存在任何机器定义层。** 对 17 个 Java 文件检索 `ReloadListener|ResourceManager|registryname|dynamic-pattern|Modifier|parallel|factory|upgrade|preview|blueprint`，仅命中 `ModItems.BLUEPRINT` 物品名两处，无一是机器加载逻辑；`src/main/resources` 下也没有 `default_machinery` 或 `default_variables` 目录。

## 四、设计差异

### D1 结构数据源：数据驱动 → 硬编码几何
旧版形状/尺寸/层数全部由 JSON 决定并自动推导包围盒；新版写死在双层循环里。

### D2 控制器朝向被结构性架空
新版 `MachineControllerBlock.java:23-29` 已保存并可旋转/镜像 `FACING`（0.4.2/0.4.3 的工作），但 `MachineStructure.isFormed` **不接收朝向参数**，结构判定完全忽略朝向；旧版则由 `TileMultiblockMachineController` 用控制器朝向 `rotateYCCW` 整个 pattern 后再匹配。

### D3 等级仓口不被结构接受
新版注册了 48 个等级仓口，但 `isFrameBlock` 白名单只认 4 个无等级基础仓口，**等级仓口放进环里结构不会成型**；`energy_output_hatch` 与 `fluid_output_hatch` 连基础版本都没有，同样不被接受。

### D4 「机器」从注册表实体降级为配方字符串
旧版机器是注册进 `MachineRegistry` 的独立对象，配方 `machine` 字段可校验存在性与归属；新版无注册表，控制器直接比对字符串 `"basic"` / `"machine_controller"`。

### D5 资源容器归属反转（新版自创）
旧版每种仓口各自是带能力与容量等级的 TileEntity，控制器按成型结构收集这些组件；新版把物品/能源/流体容器全部塞进控制器本体并直接对外暴露能力（`MachineControllerBlockEntity.java:34-41,90-95`），仓口退化为装饰。

### D6 旧版的结构表达体系整体缺席
`ns:block@meta` 描述符、`*.var.json` 变量集、`selector-tag`、`nbt`/`preview-nbt` 匹配、`modifiers` 替换、`dynamic-patterns` 伸缩结构——**六种机制无一在新版存在对应实现**。

## 五、关键发现

1. 新版没有机器定义层：无 JSON schema、无加载器、无注册表、无 reload 监听，`MachineStructure.java` 全文 23 行即为「结构系统」的全部。
2. 旧版机器 JSON 的 13 个根字段在新版**全部无对应实现**；旧版唯一被等价承接的只有「控制器方块 + 水平朝向状态」和「结构成型判定驱动运行」两点。
3. 多层结构与任意形状完全不支持：新版 Y 偏移写死为 0，而旧版内置机器就要 3~5 层，可伸缩产线要 5~15 格。
4. 新版结构白名单只认 5 种方块，导致 48 个等级仓口无法参与结构——等级仓口在功能上等同于装饰方块。
5. 控制器朝向已实现却被判定逻辑忽略，旧版则用朝向旋转整个结构 pattern。
6. `modifiers` 这一「结构布局影响产能」的玩法机制（如合金炉顶部加 vent 翻倍产出）在新版没有任何替代物。

## 六、不确定项

1. 旧版 `common/machine/ComponentRestriction.java` 检索 `new ComponentRestriction` 无命中，疑似未被使用的死代码；是否经反射等间接路径使用未进一步验证。
2. 旧版 `blockcasing@N` 中各 N 与 CasingType 的视觉对应关系未逐项展开。
3. 新版 `build/` 目录下存在编译产物与资源副本，本审计只针对 `src/main` 源码树。
4. 旧版并行数、线程数由 `Config` 默认值与 CraftTweaker 注入，已确认不是机器 JSON 字段；但是否存在其他数据包可用的等价入口未审计。
5. 玩家自定义机器目录的实际文件系统位置未追踪。
