# Modular Machinery: Community Edition Reborn

> ## ⚠️ 开发中，**尚未完工**
>
> 当前已发布版本 **0.31.0**。「已完成」的部分均已在游戏里实际验证；
> 「未完成」的部分尚不可用。请勿据此期待一个功能完整的模组。
>
> ✅ **`0.31.0` 已发布**：KubeJS 能写完整机器定义（11 个扩展字段），并修复社区反馈的「所有方块挖得极慢且什么都不掉」。
> **已实机验证、已部署、已归档**（`releases/modular-machinery-reborn/v0.31.0/`）—— 记录见 `modular-machinery-reborn/迁移日志.md` 的 0.31.0 一节。

**Modular Machinery: Community Edition（1.12.2）在 Minecraft 1.20.1 / Forge 上的移植版。**

它是一个**框架**——**本模组不携带任何机器与配方**，机器与配方由你（整合包作者/玩家）提供。

---

## 从哪开始

| 你是 | 先读 |
|---|---|
| **想用这个模组** | [modular-machinery-reborn/docs/使用说明.md](modular-machinery-reborn/docs/使用说明.md) —— 从空实例到一台能跑的机器 |
| **想知道移植了什么** | [docs/简介.md](modular-machinery-reborn/docs/简介.md)（中文）/ [docs/Introduction.md](modular-machinery-reborn/docs/Introduction.md)（English） |
| **要写 KubeJS 配方** | [docs/KJS-配方指南.md](modular-machinery-reborn/docs/KJS-配方指南.md) |
| **遇到问题 / 想知道刻意不做什么** | [docs/已知限制.md](modular-machinery-reborn/docs/已知限制.md) |
| **想改代码** | [modular-machinery-reborn/交接文档.md](modular-machinery-reborn/交接文档.md)（架构与发布必查项） |

## 三条定义路径

- **数据包** JSON —— `/reload` 即生效
- **配置目录** `config/modular_machinery_reborn/machinery/` —— **重启后**机器可获得**专属控制器方块**
- **KubeJS** 服务器脚本 —— `/reload` 即生效

## 构建

```bash
cd modular-machinery-reborn
./gradlew build
```

`gradle.properties` 默认 `use_local_deps=false`（走 maven 分支，无需第三方 jar）。
**离线构建**：把它改成 `true`，并把 `jei-15.49.jar`、`kubejs-1.20.1.jar`、`rhino-1.20.1.jar`
放进 `modular-machinery-reborn/libs/`（这三个 jar **不随本仓库分发**）。

## 许可证与来源

**GPL-3.0**（见 [LICENSE](modular-machinery-reborn/LICENSE)）。

本模组是 **Modular Machinery: Community Edition**（上游为 **GPL-3.0**）的移植版；
因上游采用 copyleft 许可证，本移植版**同样以 GPL-3.0 发布**。
来源、修改声明与第三方依赖见 [NOTICE.md](modular-machinery-reborn/NOTICE.md)。

## 离线复现器

`_audit/m6c-verify/` 是本项目的离线验证器（1000+ 项断言，含红先行与故障注入记录）。
它不是构建的一部分，但它是这个项目的工作方式的一部分——**改动请连带跑它**。

## 发布版

二进制（jar）通过 **GitHub Releases** 分发，不入 git 历史；
版本索引与 SHA-256 见 [releases/modular-machinery-reborn/README.md](releases/modular-machinery-reborn/README.md)。