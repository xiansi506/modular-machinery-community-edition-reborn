> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-03 Codex结论：本批通过离线验收；无真实游戏/FML/完整网络/GPU验收。** 详见项目docs/归档/开发记录/验收记录/验收-GLM第二批12项-2026-11-03.md。下方“待验收/自测全过”为交付时状态。

# GLM 修复结果 0.121.29（第二批 B 批 B1～B4，2026-11-03）

**执行者：GLM-5.3 Flash，按[修复交接-GLM5.3Flash-第二批12项-2026-11-03.md](../任务交接/修复交接-GLM5.3Flash-第二批12项-2026-11-03.md)交付 B 批。四项自测通过，状态统一为“待Codex验收”。** A 批见 [GLM修复结果-0.121.28.md](GLM修复结果-0.121.28.md)；C 批随后交付。

## 结果总表

| 任务 | 状态 | 实际修改文件与方法 | 改前证据 | 改后结果 | 未验证边界 |
|---|---|---|---|---|---|
| B1/P10-02 | 完成，待Codex验收 | `client/MachineControllerScreen.keyPressed`：`keyPressed(...) \|\| canConsumeInput()` 认领编辑键，`keyCode != KEY_ESCAPE` 保留ESC路径 | B1/checks-prefix.log：聚焦时 keyPressed(69) 落到关窗哨兵（FIX 失败），退格/箭头/ESC/未聚焦E/重绑定K/隐藏/不可编辑 4 项对照通过 | B1/checks-postfix.log 5/5、退出0：聚焦认领E→charTyped('E')得"1E"；退格原地删字；左右箭头认领；ESC 关闭且仅一次；未聚焦 E 与重绑定 K 关闭；隐藏/不可编辑聚焦字段不认领 | 真实OS键盘事件顺序/IME/真实窗口；未只屏蔽键码69（走 EditBox canConsumeInput 语义） |
| B2/P10-03 | 完成，待Codex验收 | `MachineControllerScreen.drawInfo` 与 `FactoryControllerScreen.drawRecipeStatus` 百分比点：`(long) progress * 100 / duration` | B2/checks-prefix.log：30M/60M 两处均捕获 `-21%`（2 项 FIX 失败），空闲线程无百分比对照通过 | B2/checks-postfix.log 3/3、退出0：普通控制器与工厂均 50%；50/100、1/100、100/100、21474837/214748365→10%；fraction 对照 0.5；scissor 正常归还 | 真实像素/字体/数千万tick实机等待；仅显示层，菜单/线程实际进度未变（断言内含） |
| B3/P10-07 | 完成，待Codex验收 | `client/UpgradeBusScreen.drawText`：重算 scrollRange 后 `if (scroll > scrollRange) scroll = scrollRange` | B3/checks-prefix.log：真实 handleUpdateTag 清空绑定后 scrollRange=0 而 scroll 保留、drawText 0 行（FIX 失败）；有效中位保留与变短后再滚 2 项对照通过 | B3/checks-postfix.log 3/3、退出0：缩短后立即恢复可见文字（无滚轮）；有效位置保留；短内容滚到底仍 0；再增长从保留位置绘制 | 真实滚轮/库存/升级效果/绑定存档规则未动（diff 静态核对） |
| B4/P10-16 | 完成，待Codex验收 | `client/jei/ModularMachineryJeiPlugin.onRuntimeUnavailable()` 覆写 → `PreviewJeiActions.onRuntimeUnavailable()`（新公共生命周期入口，置空桥） | B4/checks-prefix.log：插件默认 unavailable 后桥仍引用 A 且 show 继续调用（FIX 失败）；初始空桥安全回退对照通过 | B4/checks-postfix.log 2/2、退出0：available(A)→A 被调用；unavailable→桥空、show/R 回退；双重 unavailable 幂等；available(B)→按 sink 计数仅 B 被调用（A=1 不再增） | 真实 JEI 关闭时序/重启窗口/GUI页面/堆存活分析；桥为 JEI 在场才加载的类，未装 JEI 时行为不变（静态核对） |

## 版本与基线

| 项 | 值 |
|---|---|
| 起始 | 0.121.28（A 批交付版）/ 协议12 / HEAD `f94ba299…` |
| 结束 | **0.121.29** / 协议12 |
| 改前类基线 | `_audit/glm53flash-12tasks-0.121.28/production-classes/`（A 批交付快照，符合“后批使用实际前批交付快照”） |
| 目标文件改前指纹 | 与 `_audit/glm53flash-handoff-12tasks-2026-11-03/` before 副本一致（上轮验收后未动） |

### 五文件改后 SHA-256（改前值见交接 before/）

| 文件 | 改后（0.121.29） |
|---|---|
| `client/MachineControllerScreen.java` | `FBCB37AAD891BD3CDC90B4ADA31A95E0185C49CF1E4BD476E455B6E3855ED51B` |
| `client/FactoryControllerScreen.java` | `8424C09866BB15B4F8E0E4B91A3A7A1EA6527BE4F41947AF72CFF7872D9E92E5` |
| `client/UpgradeBusScreen.java` | `DE2547443F66466C73609287731A82886223CC741D5C6341E53A8564F7F8A262` |
| `client/jei/ModularMachineryJeiPlugin.java` | `14B07D4D7CCC2E254D1D839DDD5E50383179ABF70533F0951ED0D2FAEA0A78F0` |
| `client/preview/PreviewJeiActions.java` | `A87C4CD3D47BDEE5483180A59DBEBC9089933D808A9D44E36E64EE739DB68B88` |

增量 diff 入口：`_audit/glm53flash-12tasks-0.121.29/incremental-vs-handoff.diff`（相对 12 项交接 before 副本；MachineControllerScreen 同时含 B1 的 keyPressed 分支与 B2 的百分比行，属两项各自白名单内的不同方法）。注意：diff 相对 0.121.0 时代基线者不可混用；本 diff 的 MachineControllerScreen/FactoryControllerScreen 包含 G 批/A 批已验收修改之后的最新状态（before 副本为 0.121.27 验收后内容）。

## 命令与退出码

| 步骤 | 命令要点 | 退出码 | 结果 |
|---|---|---|---|
| 夹具编译 | `javac -encoding UTF-8 -cp classpath-pre.txt -d fixture-classes B1..B4/*.java` | 0 | 无错误 |
| 改前运行 | `java -Xmx1G -javaagent:token-agent.jar [-javaagent:key-agent.jar]`（B1 需 KeyStateAgent 替换 InputConstants.isKeyDown 硬件查询，与勘测夹具一致） | B1=1 B2=1 B3=1 B4=1 | 各自仅 FIX 断言失败，对照全过（checks-prefix.log） |
| 正式构建 | `./gradlew.bat -g C:/mmwork/.gradle-home clean build --offline --no-daemon -Puse_local_deps=true`（Java17） | 0 | BUILD SUCCESSFUL in 55s；compileJava/reobfJar 实际执行；Gradle test=NO-SOURCE（build-0.121.29.log） |
| 改后运行 | 同上（classpath-post.txt 仅含 0.121.29 快照，无旧生产类目录） | B1=0 B2=0 B3=0 B4=0 | 5/3/3/2 项全部 PASS（checks-postfix.log） |

## 正式包与归档

| 项 | 值 |
|---|---|
| 归档 | `releases/modular-machinery-reborn/v0.121.29/modular-machinery-reborn-0.121.29.jar`，cmp 字节一致 |
| 大小 / SHA-256 | 1626905 字节 / `C15189C6C926D5FABAE5C1079A0F63069811507A4091F9C6D2436F19DC786401` |
| 同步位置 | gradle.properties、工作区/项目 README、docs/en/README.md、`docs/归档/版本记录/更新记录-0.121.29.md`、release 索引（旧归档保留） |

## 夹具说明与诚实记录

- B1 初版用绝对关窗计数断言，改前失败会污染后续用例的计数（最终版改为增量断言 `before + n` 后两类各跑一遍）；退格路径需要 key-agent（InputConstants.isKeyDown 恒 false），与勘测 P10-a1 的桥一致，未对假窗口句柄做原生查询。
- B2 普通控制器夹具曾捕获为空：根因是夹具未把项目 en_us.json 装入真实 Language，未翻译的长状态值被换行 4 行耗尽绘制预算，进度行被布局截断（勘测夹具当年加载了翻译表）。补 translations 逻辑后，同一夹具改前捕获 `-21%`、改后捕获 `50%`；调试探针保留在 `debugprov/`。
- B4 夹具位于 `com.reborn.modularmachinery.client.preview` 包内以调用包私有 `show/keyPressed`（与勘测 P10-b6 相同）；运行需用全限定类名。`PreviewJeiActions` 属白名单“必要时限生命周期入口”，本次只加了 `onRuntimeUnavailable()` 一个入口。
- 白名单外业务文件未改；未暂存/提交/推送、未部署游戏；工作区其余未提交改动（他人批次）全部保留。
