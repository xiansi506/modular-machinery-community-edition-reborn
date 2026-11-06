> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex结论：C1/C3单项通过，C2/C4返工，本批整体未通过；无真实游戏/FML/完整网络/GPU验收。** 详见项目docs/归档/开发记录/验收记录/验收-GLM第二批12项-2026-11-03.md。下方“待验收/自测全过”为交付时状态。

# GLM 修复结果 0.121.30（第二批 C 批 C1～C4，2026-11-03）

**执行者：GLM-5.3 Flash，按[修复交接-GLM5.3Flash-第二批12项-2026-11-03.md](../任务交接/修复交接-GLM5.3Flash-第二批12项-2026-11-03.md)交付 C 批。四项自测通过，状态统一为“待Codex验收”。** A 批见 [GLM修复结果-0.121.28.md](GLM修复结果-0.121.28.md)，B 批见 [GLM修复结果-0.121.29.md](GLM修复结果-0.121.29.md)；**至此第二批 12 项全部交付**。

## 结果总表

| 任务 | 状态 | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| C1/P03-05 | 完成，待Codex验收 | `machine/MachineDefinitions.stage`：`source.deepCopy()` 注册时快照；`stagedSources()` 深拷贝出读 | C1/checks-prefix.log：注册后 builder 加第 2 部件使客户端重建出 2（FIX 失败）、嵌套 NBT 对象改动泄入快照、读取侧污染回写（共 3 项 FIX 失败）；重复注册覆盖与无 source/beginCycle 2 项对照通过 | C1/checks-postfix.log 5/5、退出0：服务端与重建客户端均保持注册时 1 部件；嵌套 parts/NBT 深拷贝隔离；`stagedSources()` 读出为副本；覆盖/清理保留 | 真实 KubeJS 重载事件顺序、真实网络传输；单 JVM 离线（真实 builder/同步解析已用） |
| C2/P02-09 | 完成，待Codex验收 | `command/BlueprintCommand`：新增内联 `MachineIdArgument`（ResourceLocation 字符集读取+引号支持）；回调 `tryParse` 化并补 not_found 兜底；新增 `get_blueprint.invalid_id` 语言键（en/zh，交接允许） | C2/checks-prefix.log：裸 `review:missing` 被 StringArgumentType 拒于解析（CommandSyntaxException）、裸大写 `Foo` 抛未捕获 ResourceLocationException（2+1 项 FIX 失败）；带引号/控制台/权限 2 项对照通过 | C2/checks-postfix.log 5/5、退出0：未加引号完整 ID 解析并走 not_found；大写/非法字符/空输入均为普通命令错误；带引号与裸名保留（裸名默认本模组命名空间）；已注册测试物品+Inventory 收到蓝图；控制台拒绝、权限 0 拒绝 | 真实聊天栏/权限注解展示、真实玩家库存；未给未知机器发物品 |
| C3/P10-11 | 完成，待Codex验收 | `client/preview/StructurePreviewRenderer.pickBlockAt`：对角线 double 平方 + 拾取射线双精度重建（同相机约定 `cameraViewD`/double `unproject`/`rayBox(Vector3d)`）；`render` 的对角线同步 double | C3/checks-prefix.log：紧凑 x=100 命中而 x=46340/50000 与 -46340 稀疏结构返回 null（int 溢出→NaN，2 项 FIX 失败）；退化尺寸/旋转对照通过 | C3/checks-postfix.log 5/5、退出0：x=100、46340、50000、-46340、退化尺寸全部命中瞄准方块；不同 yaw/pitch 保持有限。参考投影为独立 double 实现（非抄生产算式） | 无 GPU：绘制分支仅静态/数学验证（同形对角线算式已同步）；真实鼠标手感与渲染精度为 GPU 浮点固有 |
| C4/P10-13 | 完成，待Codex验收 | `client/jei/MachineRecipeText`：`energyPerTickExact`（BigInteger 求和）替代原 long 求和；`energyTip` 总量 `perTick×recipeTime` 精确相乘，超 long 显示精确十进制数字（不回绕/不消失/不冒充 0）；`MachineRecipeCategory.draw` 能源分支与 `drawEnergyCell` 改读精确和正负 | C4/checks-prefix.log：MAX+1 之和使提示消失、MAX/2+1×2 显示 -9223372036854775808、输出侧同样回绕（3 项 FIX 失败）；4.0G/8.0G/4.0K 缩写与 0/负值边界 2 项对照通过 | C4/checks-postfix.log 5/5、退出0：MAX+1 显示精确 9223372036854775808（速率与总量）；MAX/2+1×2 总量精确 2^63；输出侧镜像；4.0G/8.0G/4.0K 缩写保留；0/负无提示 | 类别绘条为数据源核对+diff 静态（无 GPU）；不改 Recipe/EnergyRequirement/事务的 long 契约（静态核对，diff 内未触及） |

## 版本与基线

| 项 | 值 |
|---|---|
| 起始 | 0.121.29（B 批交付版）/ 协议12 / HEAD `f94ba299…` |
| 结束 | **0.121.30** / 协议12 |
| 改前类基线 | `_audit/glm53flash-12tasks-0.121.29/production-classes/`（B 批交付快照） |
| 目标文件改前指纹 | 与 `_audit/glm53flash-handoff-12tasks-2026-11-03/` before 副本一致 |

### 五文件改后 SHA-256（改前值见交接 before/）

| 文件 | 改后（0.121.30） |
|---|---|
| `machine/MachineDefinitions.java` | 见 `_audit/glm53flash-12tasks-0.121.30/after-hashes.txt` |
| `command/BlueprintCommand.java` | 同上 |
| `client/preview/StructurePreviewRenderer.java` | 同上 |
| `client/jei/MachineRecipeText.java` | 同上 |
| `client/jei/MachineRecipeCategory.java` | 同上（白名单内能源显示分支） |

另有 `lang/en_us.json`、`lang/zh_cn.json` 各新增一行 `get_blueprint.invalid_id`（C2 交接允许的错误文案同步）。

增量 diff 入口：`_audit/glm53flash-12tasks-0.121.30/incremental-vs-handoff.diff`（相对 12 项交接 before 副本，含上述五文件 +150 行左右）。

## 命令与退出码

| 步骤 | 命令要点 | 退出码 | 结果 |
|---|---|---|---|
| 夹具编译 | `javac -encoding UTF-8 -cp classpath-pre.txt -d fixture-classes C1..C4/*.java` | 0 | 无错误 |
| 改前运行 | `java -Xmx1G -javaagent:token-agent.jar` | C1=1 C2=1 C3=1 C4=1 | 各自仅 FIX 断言失败，对照全过（checks-prefix.log） |
| 正式构建 | `./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true`（Java17） | 0（第一次因 `ArgumentType.parse` 泛型签名编译失败，改为非泛型覆写后通过——失败日志保留于 build-0.121.30.log） | BUILD SUCCESSFUL in 54s；compileJava/reobfJar 实际执行；Gradle test=NO-SOURCE |
| 改后运行 | 同上（classpath-post.txt 仅含 0.121.30 快照） | C1=0 C2=0 C3=0 C4=0 | 5/5/5/5 项全部 PASS（checks-postfix.log） |

## 正式包与归档

| 项 | 值 |
|---|---|
| 归档 | `releases/modular-machinery-reborn/v0.121.30/modular-machinery-reborn-0.121.30.jar`，cmp 字节一致 |
| 大小 / SHA-256 | 1630069 字节 / `1F4CE06DB0701154425AEE4C79783B037A56EBB104BC7BE451A523B04537CAA2` |
| 同步位置 | gradle.properties、工作区/项目 README、docs/en/README.md、`docs/归档/版本记录/更新记录-0.121.30.md`、release 索引（旧归档保留） |

## 夹具说明与诚实记录

- C1 初版嵌套用例用 `builder.field("audit-extra", …)` 被 schema 拒绝（未知根字段），改用 schema 认可的 `nbt()` 嵌套对象与引用变异；调试探针在 `debugprov/`。
- C2 修夹具自身三处：`CommandSource.NULL` 的正确包（net.minecraft.commands）、post-bootstrap 构造 Item 需解冻 BuiltInRegistries.ITEM 并准备 intrusive holders（P04-b 模式）、控制台/权限对照改用带引号 ID（改前无法解析裸全 ID）。
- C3 改前夹具阶段的诊断发现：仅修对角线后稀疏结构仍无法命中——float 逆矩阵在数万格相机距离下射线偏离约 1500 格（探针 RayProbe/RayProbe2 记录 double 命中/float 未命中），据此把拾取射线升级为同约定的 double 实现；这不是“降低断言凑通过”，而是修复范围按证据扩大（仍在 C3 白名单文件内）。
- C4 的 `energyPerTick`（long）由 `energyPerTickExact` 取代（唯一消费者是类别能源分支，已同步切换）；契约边界（Recipe/EnergyRequirement/事务）未触及。
- 白名单外业务文件除两个语言文件新增一行（C2 交接允许）外未改；未暂存/提交/推送、未部署游戏；工作区其余未提交改动（他人批次）全部保留。
