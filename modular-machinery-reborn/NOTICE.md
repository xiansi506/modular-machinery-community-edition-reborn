# 来源与许可声明（Provenance and licence notice）

## 上游

本模组是 **Modular Machinery: Community Edition**（Minecraft 1.12.2）的移植版本，
上游以 **GNU General Public License v3.0** 发布（见 `LICENSE`）。

- 上游项目：Modular Machinery: Community Edition
- 上游许可证：**GPL-3.0**

因为上游是 GPL-3.0，**本移植版同样以 GPL-3.0 发布**（见 `LICENSE`）。
这不是可选项：GPL-3.0 是 copyleft 许可证，衍生作品必须沿用同一许可证，
并且必须提供对应源码。

## 本移植版

- 目标平台：**Minecraft 1.20.1 / Forge 47.x**
- 移植起始日期：**2026-09-29**（据最早的版本归档 v0.1.0 与迁移日志）
- 移植者：**闲肆**

## 修改说明（GPL-3.0 §5(a) 要求）

相对于上游 1.12.2 版本，本移植版**重写了几乎全部 Java 代码**（面向 1.20.1 的注册表、
数据组件、渲染与网络 API），并**保留、复用或重制了上游的美术资源**（方块/物品贴图、界面贴图、
语言文件条目、配方与机器定义的 JSON 语义）。

主要功能差异、已移植项与未移植项见 `docs/简介.md` / `docs/Introduction.md`。

## 美术与文本资源

本模组包含源自上游的资源文件。若有第三方对该资源的主张，请通过项目 issue 联系。

## 第三方依赖

本模组在运行时依赖（**不随本仓库分发**）：

- Minecraft Forge（1.20.1）
- JEI（Just Enough Items）
- KubeJS 与 Rhino（可选，用于脚本入口）

它们各自遵循自己的许可证。