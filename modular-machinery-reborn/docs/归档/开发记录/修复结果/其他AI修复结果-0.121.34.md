> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

# 其他AI修复结果：0.121.34（第三批E组E1～E4，2026-11-03）

执行 `修复交接-其他AI-第三批8项-2026-11-03.md` 的 E1～E4 四项，**全部完成，统一标待Codex验收**。连同 0.121.33 的 D1～D4，第三批八项交付完毕，本批完成后停止。未部署/启动游戏、暂存/提交/推送、git reset/clean。

## 基线与增量

- 前批基线：0.121.33（D组四项已交付，源码含全部改动）；E批夹具的 baseline 阶段仍以 **0.121.32 构建类**复现原始缺陷（与 D 批同一基线语义），prebuild/fixed 验证累积状态——E 批不把 D 组已有改动认作新增，增量 diff 仅含 E 批四个文件。
- HEAD 保持 `f94ba299`；Java17 `clean build --offline --no-daemon` 成功（32s），Gradle test=NO-SOURCE。出货 `build/reobfJar/output.jar`（= `modular_machinery_reborn-0.121.34.jar`，1,630,170字节）与 build/libs 字节一致（cmp），SHA-256 `2F8DE80E7FB1052892BEB6C135E39CB68A70626573630B9C22BC74611A2AB444`，归档 `releases/modular-machinery-reborn/v0.121.34/`（旧归档保留）。
- 构建后完整 classes 快照（568 class，`third-ai-8tasks-0.121.34/production-classes-0.121.34`）复跑全部 fixed 阶段通过。classpath 复用各旧审计清单并过滤旧类目录（review-p10-c3/review-p12-d 的 review/classes、review-glm-rework-0.121.31）；@argfile 按 GBK 读取，JVM 相关路径一律放 `C:/mmwork`（ASCII）。
- 增量：`_audit/third-ai-8tasks-0.121.34/diffs/`（E1～E4 四个 diff）；改后 340 份源码哈希 `hashes/production-src-sha256-after-E.txt`（改前=交接基线+`third-ai-8tasks-0.121.33/hashes/`）。

## 逐项结论与验证边界

| 任务 | 结果 | baseline | fixed | 生产文件与方法 |
|---|---|---|---|---|
| E1 P10-10 | 完成 | 5/5 复现 | 13/13 | `client/preview/PreviewPanel.mousePressed` 非JEI宿主分支改用 `ingredientAt` |
| E2 P10-20 | 完成 | 3/3 复现 | 15/15 | `client/jei/ModularMachineryJeiPlugin.registerItemSubtypes` 改机器ID解释器 |
| E3 P06-18 | 完成 | 18/18 复现 | 20/20 | `block/MachineControllerBlockEntity.startFailureKey()` 客户端分支加完整字符串回退 |
| E4 P10-19 | 完成 | 5/5 复现 | 12/12 | `integration/jade/FluidStorageSuppressor.replaceFluidRow` 按归属tag逐元素替换 |

**E1**：真实 BlueprintPreviewScreen（left=90/top=45）→真实 panel 事件链→真实 PreviewJeiActions 查询 sink（shows/roles 计数）。baseline 复现主图标/候选四向查询全静默；fixed 后主图标/候选左/右键各恰一次正确 recipes/uses 查询、release 不重复、底部材料对照保持、空白视口/中键不消费、键盘 R/U 有效、JEI 未加载与 JEI 原生槽宿主不误触。不直接调用 show 跳过事件链；未改 3D 相机/拾取，0.121.31 几何修复与生命周期清理保留。

**E2**：真实插件注册→原生 JEI SubtypeManager/StackHelper/IngredientManager/RecipeCatalystRegistration/RecipeMap 索引查询（不只比较解释器字符串）。baseline 复现改名蓝图 Recipe 身份改变、原生索引丢类别；fixed 后同机改名/无关自定义 NBT 与原蓝图等价（Recipe 与 Ingredient 两上下文）、异机区分、白板/缺键/格式坏id归"无子类型"不映射任何有效机器、未知合法 id 自成一类；悬停名与自定义键未被修改（不改真实物品 NBT）；类别注册与全局生命周期未动。

**E3**：真实服务端 tick 生成失败→真实 getUpdatePacket→真实客户端 onDataPacket→访问器链。baseline 复现自定义接口键 `audit.mode.mismatch` 与魔法键 `craftcheck.failure.magic.input` 客户端为 null（包内字符串完整）；fixed 后两键可见，八键 ordinal 往返不变，自定义→已知键替换→清除链正确；**两种到达顺序的快照语义如实记录**——已知键 ordinal 先到即正确；自定义键显示中"菜单单独清除"与"自定义键"在 ordinal 通道不可区分，旧文本保持到清除的 update tag 到达（不虚称两通道跨包严格同步）。未扩展协议、未向白名单追加键、failureCauseText（C2b）与 MachineControllerScreen 无改动。

**E4**：真实 snownee.jade.impl.Tooltip + 真实回调；Jade 11 官方归属契约取自依赖本体常量 `Identifiers.UNIVERSAL_FLUID_STORAGE/_DETAILED`（= `jade:fluid_storage[.detailed]`，javap 确认，不猜名称）。baseline 复现 `review:other` 被清、旧流体行残留；fixed 后逐元素只删归属确定的原生流体元素（活列表 iterator.remove），同行左右第三方与无 tag 元素保留、新条写回旧行位置、同行左右混排保留右侧行、重复回调稳定、普通替换/空罐保留第三方/外部方块门控两态对照均通过。只把下标 1 改 2 的降级未采用；provider 侧标记（HatchFluidStorageProvider）确认无需改动；访问器注册/跨mod反射/大范围provider结构未触碰。

## 首次夹具失败记录（保留，未删）

- E1：classpath 合并项带换行破坏 @argfile（agent ASM 找不到），修正为逐项 strip + JVM 路径放 ASCII 目录；及 KEI/IFocus 等 import 缺失 4 次编译失败。
- E2：`SubtypeRegistration` 包名错误 1 次编译失败；typesOf lambda 需显式函数式接口。
- E4：`IElement.getMessage()` 签名为 String（代理返回 Component ClassCastException）；`new FluidPort` 在冻结注册表上构造失败（补 c3 同款显式解冻）。

## 文档同步

项目 README 状态行与版本、工作区总览 README、`docs/en/README.md`、`docs/en/Introduction.md`（Current status 与 Current version）、迁移日志顶部条目、新建 `docs/归档/版本记录/更新记录-0.121.34.md`、release 索引顶部两条、`修复进度-2026-11-02.md`（状态行/下一入口/SHA-256/版本表 E 行/新节）、`未修复事项清单-2026-11-02.md`（头部快照与 P10-10/P10-20/P06-18/P10-19 四行"0.121.34已修，待Codex验收"）。逐处 grep 验证命中（0.121.34：项目README 2、工作区README 2、en README 1、Introduction 2、迁移日志 2、修复进度 5、未修清单 5、release索引 3；无残留"当前源码 0.121.33/Current version 0.121.33"行）。

## 限制声明

- 全部专项为离线原生调用验证；未实机、未启动/部署游戏、未验证真实网络/GPU/完整JEI启动/真实第三方插件事件顺序/服务端调度。
- E3 的"两种顺序"是当前可保证的快照语义，不是严格同步；E4 混排覆盖勘测登记的组合，不声称所有第三方 addon 事件顺序已验。
- E 批交付后停止，不自动执行剩余 61 项；历史验收记录未重写。
