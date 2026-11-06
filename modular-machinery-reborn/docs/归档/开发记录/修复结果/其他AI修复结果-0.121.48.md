> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex[独立验收](../验收记录/验收-第八批4项-2026-11-03.md)：O1/O2通过，但累计48仍含N1未收口；末尾“H1仍待复验”是过时表述，42此前已验收，本轮六套H1回归也通过。** [N1返工](../任务交接/返工交接-第八批N1-2026-11-03.md)。下文原交付结论为历史，主任务保持暂停。

# 其他AI修复结果：第八批 O 组（0.121.48，2026-11-03）

**标待 Codex 验收。** 本批交付第八批交接的 O1（P10-04）与 O2（P06-17）两项，从 0.121.47（第八批 N 组交付后的最终源码/class）累积递增为 **0.121.48**，协议 **12** 不变。N 组（P01-02 / P01-04）已在 0.121.47 交付并通过专项与 H1 回归，见[其他AI修复结果-0.121.47](其他AI修复结果-0.121.47.md)。

## 起点核对

- 开工前 0.121.47 已交付并归档：`releases/modular-machinery-reborn/v0.121.47/`（JAR 逐字节核对）、`_audit/eighth-ai-4tasks-0.121.47/production-classes-0.121.47`（568 映射名 class）与 `before/` 源码副本。本批改前源码副本在 `_audit/eighth-ai-4tasks-0.121.48/before/`（2 份，含 SHA-256）。
- 交接文档白名单：O1 = `client/FactoryControllerScreen.java`；O2 = `block/MachineControllerBlockEntity.java`。本批实际只改这 2 份。
- 0.121.48 是**累积构建**：源码同时含 N 组 3 份改动，`validate_jar.py` 以第八批 0.121.46 基线核得累计白名单 5 份（N 3 + O 2），无其他源码改变。

## 完成情况

| 编号 | 任务 | 状态 | 版本 |
|---|---|---|---|
| P10-04 | 工厂队列长状态限高/裁剪/完整 tooltip | **完成** | 0.121.48 |
| P06-17 | 普通控制器能耗快照用实时有效修改器 | **完成** | 0.121.48 |
| P01-02 | 迟到回包保留尚未收集的本地编辑 | 完成（N 组，0.121.47） | 0.121.47 |
| P01-04 | 原文深度检查与实际 JSON 语法一致 | 完成（N 组，0.121.47） | 0.121.47 |

不可复现项：无。超范围项：无。第八批 4 项至此全部交付。

## P10-04：工厂队列行的内容预算与裁剪

### 改前事实（0.121.47 真实 class 复现）

`FactoryControllerScreen.drawRecipeStatus` 把 `font.split` 的全部结果一股脑画出来。脚本状态可以有 20 行：在 86×32 的队列元素里，长状态越过元素底边盖住后面的行，第六行还会越过面板底边——右侧信息块的 scissor 属于另一个绘制阶段（`render` 里对整个信息区），保护不到队列行。改前复现（`QueueRowBefore`，0.121.47 类上 6 条失败）：

- 20 行状态实际画出 21 条 draw（未按元素容量截断），也没有任何省略号标记；
- 每条状态行没有限制在自己的 86×32 元素内；
- 整行没有任何 scissor（`scissors=0`），测量误差或换行差异直接漏到邻行与面板外。

### 修复

`client/FactoryControllerScreen.java`（`drawRecipeStatus` 重写，新增 5 个私有成员）：

- **独立内容区**：元素矩形（`queueRowRect`，`FACTORY_ELEMENT_WIDTH×FACTORY_ELEMENT_HEIGHT` = 86×32，去掉 2 px 文字内缩）就是这一行能用的全部空间；裁剪发生在真实 GUI 空间（`enableScissor` 不看 pose 栈，而本方法从 `renderBg` 进入、尚未随 `AbstractContainerScreen` 平移）。
- **行数预算**：`statusLineBudget(working)` = 元素高度减标签行 `LABEL_LINE`（12 px，原版 `y += 12` 的量化）与进度行（`LINE`，工作且有进度时）后能放下的状态行数。
- **省略**：状态行数超预算时最后一行以「…」收尾（`statusLines`，收尾前实测宽度保证省略号本身放得下）；标签用 `ellipsize` 同样按实测宽度截断。省略号是 Unicode `…`，不是三个句点。
- **裁剪兜底**：整行内容在元素矩形内 scissor；`pushPose`/`popPose` 与 `enableScissor`/`disableScissor` 各自在 `finally` 归还本方法取得的那一层，任何绘制异常都不会泄漏裁剪或 pose。
- **完整 tooltip**：`shortenedStatusAt(mouseX, mouseY)` 在真实 `render` 的命中入口按**当前** `scroll` 反查命中行，给出完整状态（含被省略的部分）；滚轮滚动后命中的是现在看见的行，不是绘制时存下的快照。
- 未改：线程标签与并行标签拼接、`progress * 100 / duration` 的 long 乘法、每行 33 px 下移、分页/滚动逻辑、线程身份。

### 验证

`_audit/eighth-ai-4tasks-0.121.48/O1`：

- `QueueRowBefore`（只用改前 API 的 10 条断言）：0.121.47 类 **4 PASS / 6 FAIL**（复现）；0.121.48 最终 class **10 PASS / 0 FAIL**。
- `QueueRowCheck`（63 条断言，走真实 `renderBg` → `drawRecipeStatus`）：原生 `GuiGraphics` 捕获实现记录每次 `drawString`/`enableScissor`；行级裁剪用**原生 `GuiGraphics$ScissorStack`**（反射拿同一实例）求交验证——含与外层裁剪的交集矩形、空交集返回原生空矩形；`push`/`pop` 各一次、进出对称、异常路径也在 `finally` 归还；`PoseStack#clear` 证明 push/pop 平衡。覆盖：短状态一行、20 行脚本状态按预算截断并以省略号收尾、并行标签、进度行、长标签省略、进度百分比仍用 long 乘法、滚动走真实 `mouseScrolled`（负 delta 向下）且命中行随滚动更新、tooltip 未命中不弹。0.121.48 最终 class **63 PASS / 0 FAIL**。
- 字体是 6 px/字符的桩 `Font`（`width`/`split`/`plainSubstrByWidth` 用同一模型），无 GPU/图集；scissor 与生产走同一个原生栈。

## P06-17：能耗快照用实时有效修改器

### 改前事实（0.121.47 真实 class 复现）

普通控制器的 `energySnapshot` 直接把开工时冻结的 `activeCraftModifiers` 交给 `EnergyUsage.of`。而**在途结算**（每 tick 扣费）走的是 `modifiersFor(recipe)`：结构/升级/催化剂贡献冻结 + **当前**脚本修改器。于是服主在同一在途配方里通过脚本改能耗修改器后，真实扣费已经变了，Jade/快照显示的名义值还停在开工时。复现（`EnergyLiveCheck`，0.121.47 类上 3 条失败）：

- 脚本把输入需求 ×2：真实扣费变为 10 FE/t，快照仍报 5（`expected=10 actual=5`）；
- 替换修改器为 ×3：真实扣费 15，快照仍报 5；
- 开工时带冻结催化剂 ×2、随后脚本再加 ×3：真实扣费 30，快照只报冻结部分的 10。

### 修复

`block/MachineControllerBlockEntity.java`（`energySnapshot` 一处取值来源，核心 1 行 + 文档）：

```java
return EnergyUsage.of(recipe, modifiersFor(recipe), this.activeParallelism).toArray();
```

与在途结算走同一条有效修改器链：开工时冻结的结构/升级/催化剂贡献保留，脚本贡献换成**当前**脚本修改器。工厂分支（`factoryEngine.energyUsage()`）不受影响。只换只读快照的取值来源：不重建执行配方、不重新扫描催化剂或仓口、不改冻结贡献与付款、不改变配方执行状态。

### 验证

`_audit/eighth-ai-4tasks-0.121.48/O2`（`EnergyLiveCheck`，26 条断言）：

- 复用第五批 `ControllerRuntimeAuditCheck.Run` / `FactoryRuntimeAuditCheck` 的真实控制器/工厂 tick 框架与真实 `LongEnergyStorage`；修改器经真实 `MachineScriptState#add/remove` 写入（与脚本桥同一入口）。
- 覆盖：脚本 ×2/替换 ×3/移除回基线、开工冻结催化剂 ×2 与实时脚本 ×3 叠加、duration 修改器对照（默认 `enable-duration-multiplier=false` 不缩放宽 per-tick，快照与实付一致）、并行下基线与修改器一致、工厂线程链（快照走 FactoryEngine 自己的聚合，不受普通分支改动影响）、Jade 读取路径不改变执行状态。
- 结果：0.121.47 类 **23 PASS / 3 FAIL**（复现）；0.121.48 最终 class **26 PASS / 0 FAIL**。

## 回归

- 第七批 `ControllerExtraCheck`（E3 菜单清除/恢复/D3 NBT 计数恢复）：`extra controller failures=0`，退出 0。
- `FinalTickCheck`（终刻支付/等待/回调矩阵）：退出 0。
- N 组专项未重跑（源码自 0.121.47 未再改动 N 组 3 份文件，`validate_jar.py` 白名单核得 0.121.48 相对 0.121.47 只有 O 组 2 份改变）。

## 构建与归档

- 环境：`JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot`、`GRADLE_USER_HOME=C:/mmwork/.gradle-home`，**JDK 17 的 `java.exe` 启动 wrapper**（同 0.121.47 教训），离线 `--no-daemon` 的 `clean build`。
- 完整日志：`_audit/eighth-ai-4tasks-0.121.48/logs/build-0.121.48.log`，命令 `build-0.121.48-command.txt`，退出码 `build-0.121.48-exit.txt` = 0；`BUILD SUCCESSFUL in 39s`，8 任务全执行，`test NO-SOURCE` 如实保留。
- 产物：`build/reobfJar/output.jar` = `build/libs/modular_machinery_reborn-0.121.48.jar`，**1,637,987 字节**，SHA-256 `12D4A38DD95C37B52CB42227A7FB44C014D41C20A2E7C2AA5109FF73D150617F`；973 entry / 568 class / 221 JSON+mcmeta 全部可解析；中英各 566 键且键集一致；`mods.toml` 0.121.48；出货 class 仍为 SRG 名（`m_237110_`），协议 12。
- 归档：`releases/modular-machinery-reborn/v0.121.48/`（JAR 逐字节复制 + README.md + SHA256.txt），`cmp` 与 `output.jar` 一致；旧归档（含 v0.121.47）未改写。
- 最终 class 快照：`_audit/eighth-ai-4tasks-0.121.48/production-classes-0.121.48`（568 份映射名 class，取自 `build/classes/java/main`）。
- 源码边界：`evidence-0.121.48.txt` —— 相对第八批 0.121.46 基线只有累计白名单 5 份（N 3 + O 2）改变，`568 → 568` class 无增删；改后哈希清单 `source-hashes-after-0.121.48.json`。

## 未验事项

- 未实机、未部署、未启动游戏；未动玩家整合包/存档。
- O1 无 GPU/OpenGL：`drawString` 落进捕获实现的记录列表，不做真实光栅化；字体为一致模型的桩，真实字体图集下的换行/省略宽度以 `plainSubstrByWidth` 的同一契约保证。scissor 求交用反射驱动**原生** `ScissorStack`（与生产同一实例、同一 `ScreenRectangle` 语义）。
- O2 的 duration 修改器在默认配置（`enable-duration-multiplier=false`）下不缩放宽 per-tick，故该项为对照断言（快照与实付一致）；开启该配置的真实游戏路径未验（该开关属既有配置面，本批未改）。
- 未暂存/提交/推送、未创建 GitHub Release、未 reset 工作区、未改累计修复账本（仍 76/5/41）、未解除暂停；H1 仍待 Codex 复验（0.121.42 返工后未变）。
