# 0.24.5 证据状态（如实记录，计数全部从磁盘文件读回）

出货源码哈希（jar 就是从这个状态构建的）：
`F03F0874D846D2263CC5B5999F4DD323D2CEAF6856E456E5B66D21A8121FB5D1`
（`modular-machinery-reborn/src/main/java/com/reborn/modularmachinery/client/FactoryControllerScreen.java`）

## 在磁盘上、内容真实的证据

| 文件 | 内容 | 从该文件读回的计数 | 行数 |
|---|---|---|---|
| `acceptance-0.24.5-information-block.txt` | 完整 Gradle + 复现器 stdout（A–V 节） | **660 PASS / 3 FAIL** | 779 |
| `harness-stdout-0.24.5.txt` | 复现器自身 stdout（A–V 节） | **660 PASS / 3 FAIL** | 733 |
| `section-V-0.24.5.txt` | 仅 V 节 | **35 PASS / 0 FAIL** | 46 |
| `fault-injection-0.24.5-info-block-deleted.txt` | 注入：删掉信息块绘制调用 | **659 PASS / 4 FAIL** | 774 |
| `fault-injection-0.24.5-inventory-label-restored.txt` | 注入：加回物品栏标签绘制 | **658 PASS / 5 FAIL** | 779 |
| `release-build-0.24.5.txt` | `clean build` | `BUILD SUCCESSFUL in 34s`，含 `:jar` 与 `:reobfJar` | 46 |
| `reobf-0.24.5-ModBlocks.txt` | 构建产物 `javap` | `CREATIVE_MODE_TAB` 0 次 / `f_NNN_` 1 次 | 1236 |
| `reobf-0.24.5-deployed.txt` | 部署文件 `javap` | `CREATIVE_MODE_TAB` 0 次 / `f_NNN_` 1 次 | 1236 |
| `deploy-0.24.5.txt` | 部署与三处哈希 | 构建产物 == 部署文件 == 归档件 | 21 |

三个 FAIL 是 R 节既有的环境性配置迁移检查（实例配置已迁移，未被改写）。

**这次没有从 Gradle 守护进程日志抢救任何东西**：复现器任务的 stdout 是通过 `Tee-Object` 直接落盘的，
上表每一行的计数都是用 `[System.IO.File]::ReadAllLines` 从该文件重新数出来的，不是从别处搬来的。

## 新增/替换的断言与其反例

| 方向 | 注入 | 期望 | 实测 |
|---|---|---|---|
| 正向（必须有） | 删掉 `renderLabels` 里的 `drawFactoryStatus(graphics);` | 须 FAIL | 新增 1 条 FAIL：`…invokes drawFactoryStatus (0 call)…` |
| 否定（必须没有） | 把 `graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, …)` 加回 `renderLabels` | 须 FAIL | 新增 2 条 FAIL：字段读取 4 次 / 标签字段任一处读取 3 次 |

两次注入的完整 stdout 都在上表里；注入后源码已还原并校验哈希（树里不残留 `FAULT INJECTION` 标记），
随后重跑验收（660/3）才执行 `clean build`。

## 仍然缺失 / 存疑的（与本版无关，但必须写下来）

1. **0.24.4 那次「6 参数 blit」注入（声称 646 PASS / 4 FAIL）的完整输出至今仍未可靠落盘** ——
   见 `EVIDENCE-STATUS-0.24.4.md`。本版没有补做它，也没有把本版的任何结论建立在它上面。
2. **渲染与交互仍然没有被复现器证明。** V 节证明的是「信息块在渲染路径上、调用绘制、载入的键就是那两行
   文字、落点是 U 节算裁剪用的同一个矩形、且只可能被画在带面板原点的那一个 pass 里」；它**证明不了像素**。
   0.24.5 在用户实机确认之前只标记为「已构建、未实机验证」。
