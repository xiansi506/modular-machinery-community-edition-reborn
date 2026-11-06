# Modular Machinery: Community Edition Reborn（MMCER）

为 **Minecraft 1.20.1 / Forge / Java 17** 提供自定义多方块机械框架。当前源码 **0.123.1**，通信协议 **13**。

用 JSON 或 KubeJS 定义机器结构与加工配方，组合仓口、并行、工厂线程和升级系统，构建自己的工业流程。模组不内置机器和机器加工配方，仓库提供可选示例。

## 创作与自动化

| 功能 | 用法与入口 |
|---|---|
| **独立配方编辑器物品** | 创造栏取得，右键打开；对控制器使用可带入机器。简易面板支持输入／输出材料格、名称搜索、JEI 拖入、数量滚轮、秒数和 FE/t；高级表单、NBT、原文与 JSON／KubeJS 导出保留。[中文](modular-machinery-reborn/docs/可视化配方编辑器.md) · [English](modular-machinery-reborn/docs/en/Visual-recipe-editor.md) |
| **结构构造工具** | 逐块／两点框选、撤销恢复、材料和 NBT 筛选，导出完整机器定义。选块操作保持原样，仅简化导出面板。[中文](modular-machinery-reborn/docs/建造选择工具.md) · [English](modular-machinery-reborn/docs/en/Construct-tool.md) |
| **无线能源输入／输出仓** | 各八级，直接加入 Flux Networks；右键原生选网，使用 long 缓冲、权限、优先级与限额。[使用说明](modular-machinery-reborn/docs/联动/能源与特殊渲染.md) |
| **ME 自动化** | 统一 ME 总线、**ME机械样板供应器（ME Machinery Pattern Provider）**、**ME机械样板镜像（ME Machinery Pattern Mirror Image）**，支持物品、流体与可选气体。[指南](modular-machinery-reborn/docs/联动/ME自动化指南.md) |

0.123.1 为配方编辑器右侧 JEI 预留空间，适配简易／高级模式和窗口缩放。创作工具需要创造模式和权限等级 2；保存只导出服务端文件，需手动安装并重载。

## 教程与注意事项

| 需要做什么 | 阅读入口 |
|---|---|
| 安装、目录和重载 | [安装与重载](modular-machinery-reborn/docs/安装与重载.md) |
| 创建第一台机器 | [使用说明](modular-machinery-reborn/docs/使用说明.md) · [完整入门示例](modular-machinery-reborn/examples/docs-quickstart/README.md) |
| 编写 JSON／KubeJS 配方 | [配方指南](modular-machinery-reborn/docs/KJS-配方指南.md) |
| 升级与使用边界 | **[注意事项与限制](modular-machinery-reborn/docs/已知限制.md)** |
| 进阶与联动资料 | [全部教程](modular-machinery-reborn/docs/README.md) · [English](modular-machinery-reborn/docs/en/README.md) |

## 可选联动模组

按需安装对应的 **Minecraft 1.20.1 / Forge** 版本，并满足各模组自身依赖。具体操作见[联动教程](modular-machinery-reborn/docs/README.md#联动教程)。

| 模组 | 联动内容 | 接入状态 |
|---|---|---|
| JEI（Just Enough Items） | 机器配方浏览、多方块结构预览、材料表和资源需求显示 | 已接入 |
| KubeJS 6 | 定义机器与配方，提供服务端生命周期事件和客户端 GUI／模型／动画脚本 | 已接入 |
| Applied Energistics 2（AE2） | 统一 ME 输入／输出总线、ME机械样板供应器、ME机械样板镜像及物品／流体自动化 | 已接入 |
| Mekanism | 气体配方、仓口气体存储／传输和 JEI 气体贴图 | 已接入 |
| Applied Mekanistics 1.4.3 | 配合 AE2 与 Mekanism，为 ME 总线和加工样板接入气体 | 已接入 |
| Botania（植物魔法） | 魔力输入／输出仓及魔力网络接口 | 已接入 |
| Nature’s Aura（自然灵气） | 真实环境灵气、类型与阈值控制，以及 JEI 绿色灵气条 | 已接入 |
| Blood Magic 3（血魔法 3） | 绑定血球与 SoulNetwork 的 LP，以及区块恶魔意志资源 | 已接入 |
| Flux Networks（通量网络） | 普通仓 long 能源接口；八级无线能源输入／输出仓直接入网，原生选网与权限 | 已接入（无线仓 0.122.0） |
| Draconic Evolution（龙之研究） | 通过 Brandon’s Core 的 OP／long 能源接口连接能量仓 | 已接入 |
| Jade | 仓口能量／流体信息、控制器与工厂线程能量读数 | 已接入 |
| GeckoLib | 自定义控制器模型与动画，全亮、透明及光晕效果 | 已接入 |
| PneumaticCraft: Repressurized（气动工艺） | 压缩空气输入／输出仓、压力／真空条件、一次性及逐 tick 空气需求 | 已接入（0.119.0） |
| [Embers Rekindled（余烬复刻版）](https://github.com/RCXcrafter/EmbersRekindled) | Ember 输入／输出仓、小数能量结算与余烬传输接口 | 已接入（0.119.0） |


## 下载与验证

- 二进制下载以 [GitHub Releases](https://github.com/xiansi506/modular-machinery-community-edition-reborn/releases) 中实际发布的版本为准。本次推送源码和文档，不自动创建二进制 Release；本地归档的 JAR 不进入 Git。
- 客户端与服务端使用相同版本；新增专属控制器等注册内容需要重启，普通定义按教程重载。
- 已有正式 Java 17 clean build/reobf 和针对性的离线回归。真实 Forge 注册、画面、JEI 操作及多人联机仍需游戏验收，不能把离线通过视为所有游戏场景均已验证。
- 无线仓新贴图目前仅为设计稿，未接入游戏。

## 项目贡献者

| 贡献者 | 参与内容 |
|---|---|
| [xiansi506](https://github.com/xiansi506) | 项目维护、需求设计与发布 |
| [Codex](https://github.com/codex)（OpenAI AI 开发助手） | AI 辅助功能移植、代码审查与问题修复、回归验证、中英文文档整理 |

## 致谢

- 感谢 [Modular Machinery: Community Edition（MMCE）](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition) 的 **KasumiNova、youyihj、ikexing** 及所有社区贡献者。社区版的开源代码、资源与功能设计，是本项目进行 1.20.1 移植的基础。
- 感谢原版 Modular Machinery 的 **HellFirePvP** 与美术作者 **wiiv**，为模块化机械提供最初的框架与美术资源。
- 感谢 [咕咕工具（GuGu Utils）](https://github.com/ParaParty/gugu-utils) 的 **WarmthDawn、yuedu233_、Month_Hare** 及派瑞派对团队，提供资源联动与 JEI 显示的开源实现。本项目自然灵气条的绘制／贴图，以及气动工艺、余烬联动的适配，受益于这些工作。

来源、资源使用与许可证说明见 [NOTICE.md](modular-machinery-reborn/NOTICE.md)。

## 许可证与来源

**GPL-3.0**（见 [LICENSE](modular-machinery-reborn/LICENSE)）。

本模组是 **Modular Machinery: Community Edition**（上游为 GPL-3.0）的移植版；因上游采用 copyleft 许可证，本移植版同样以 GPL-3.0 发布。来源、修改声明与第三方依赖见 [NOTICE.md](modular-machinery-reborn/NOTICE.md)。


维护者入口：[开发与构建](modular-machinery-reborn/docs/开发/README.md) · [历史归档](modular-machinery-reborn/docs/归档/README.md)。
