# v0.121.44 — 第六批 K 组（P10-14 / P10-15，2026-11-03）

协议 **12**；Java 17 / Forge 1.20.1；JAR `modular-machinery-reborn-0.121.44.jar` **1,634,525 字节**，SHA-256 `2E5A21A525325E5357061DE9E97FF08AF27A96239E8B5EE87D23862BBD313F64`。真实起点 **0.121.43**（源码/class 快照取自改前实态）。**标待 Codex 验收。**

## 本版内容

- **K1 / P10-14**：JEI 气体/魔力文本格在进入文字自身 `translate/scale` 前，用当前 pose 把 18×18 格子四角转换到绝对 GUI 坐标（左/上 floor、右/下 ceil），裁剪随宿主原点移动；外层 scissor 结束时恢复，不取消裁剪、不乘窗口 GUI 倍率。
- **K2 / P10-15**：每行 `pushPose` 用内层 `finally` 归还，scissor 用外层 `finally` 归还；原异常保留，`enableScissor` 失败不多 pop，不清空调用者 pose/scissor 栈。

## 验证

- `TextClipCheck K1`：0.121.43 快照 **7 项失败** → prebuild/最终 class 通过（零/正/负/非整数原点、多格、多行、外层裁剪、空文字、有效 sprite 旁路、布局数据）。
- `TextClipCheck K2`：0.121.43 快照 **12 项失败** → prebuild/最终 class 通过（首/二行异常、translate 异常、enable 失败、非零原点+外层裁剪+异常+下一格正常的组合）。
- 340 份源码仅 1 份改变；568 class 仅 1 个改变。Gradle test=NO-SOURCE。

## 构建与归档

Java 17、离线、`--no-daemon` 的 `clean build` 成功（1m10s）。`build/reobfJar/output.jar` 与 `build/libs/modular_machinery_reborn-0.121.44.jar` 均为 1,634,525 字节、同一 SHA-256；归档为该产物逐字节复制。旧 0.121.43 JAR 未改写。

## 边界

真实 JEI 15.49 原生 builder/槽位/`OffsetDrawable` 背景与生产文字 helper，GPU/裁剪为捕获 sink；未执行完整 `RecipeSlot.draw`、真实图集/动画或实机 OpenGL。支持边界为 JEI 二维平移/轴缩放，任意旋转/透视宿主不在本批。实机未验；未部署/启动游戏、未动玩家整合包/存档、未暂存/提交/推送、未创建 Release、未 reset 工作区。详见[修复结果](../../../modular-machinery-reborn/docs/归档/开发记录/修复结果/其他AI修复结果-0.121.44.md)。
