# 0.116.0 · ME 输入总线滚轮

ME 输入总线支持滚轮调节已有物品／流体／气体标记的目标维持量：普通 ±1、Shift ±10、Ctrl ±100、Shift+Ctrl 翻倍／减半。服务端按当前标记逐次计算，保留物品 NBT，并检查界面、距离和维度；沿用物品堆叠及共享池容量上限。

本轮滚轮专项 26、UI／供应器／气体相关回归 90、可选依赖类加载 60 项均零失败；build/reobf 32s、178 JSON、中英各 403 键、SRG 与协议 5 检查通过。完整 2669 项套件在 0.115.0 通过，本轮未重复运行。未实机、未部署、未上传。

- 字节：1,311,508
- SHA-256：`FA81A411C143EAE34FD67439075F4F506B0DAE2BDC67E78CB7F8DB8CD7584DB0`
- 来源：`modular-machinery-reborn/build/reobfJar/output.jar`

[使用说明](../../../modular-machinery-reborn/docs/联动/ME总线滚轮.md) · 验证记录（本地审计工件，不随本次发布）
