# 0.121.8：全局KubeJS异常拒绝放行加工（2026-11-02）

修复 **P11-01**：服务端全局KubeJS生命周期桥观察原生EventHandler的错误回调和EventResult，避免KubeJS已经记录错误但返回PASS时放行。RecipeCheck异常写入失败并拒绝开工，普通RecipeTick异常阻止推进，工厂Tick等可取消事件异常置取消位。通知事件与脚本主动世界副作用没有通用回滚，不把本批描述成脚本事务。

原生Rhino基线已执行抛错监听器并观察真实控制器仍扣煤和FE。修复后9组原生组合覆盖普通/工厂 × 检查/逐tick × START/END，核对物品与FE总账、进度及移除坏监听器后不重付开工料的继续加工；另含正常cancel及清空监听器控制。4组相关回归覆盖专属回调EventExit/错误、全局cancel、客户端GUI/model/animation异常回退与CLIENT卸载隔离。正式clean build/reobf后独立复制完整生产class，两套共13组再次通过。故意抛错的KubeJS日志按预期保留。

START检查异常不付费；逐tick START异常不扣该tick资源，END发生于资源支付后，暂停推进但不返还该tick支付。测试使用真实KubeJS/Rhino与Forge派发，控制ScriptManager及内存Level；尚未验收完整FML/KubeJS插件发现、真实脚本文件重载、游戏专服/多人网络/GPU。高级脚本指南已补异常处理与边界。

协议 **12**。Java17 clean build/reobf成功，Gradle test为NO-SOURCE；专项运行结果与边界见审计目录`_audit/code-survey-0.121.0/fix-0.121.8`。未启动/部署游戏、暂存/提交/推送或创建GitHub Release。

归档`modular-machinery-reborn-0.121.8.jar`，1616039字节，SHA-256 `A7FDC2C163C037B0DDC3B5835CCA2F90BA53A2627740C46C35B3747E95994BDA`；归档JAR与build/libs原下划线命名JAR字节一致。
