# P11-a：Rhino 注册、回调与重载验收

2026-11-01；0.121.0 / HEAD f94ba299；只验收，不修生产代码。主进度仍10/12，P11尚未收口。

独立工件：`_audit/code-survey-0.121.0/review-p11-a-rhino/`。复用经哈希核对的339份当前源码独立编译类，不使用遗留0.122构建输出。真实安装的KubeJS/Rhino依赖版本及字节哈希见classpath与evidence清单。

三组实际执行通过：

1. 插件BindingsEvent绑定的EventGroupWrapper经真实Rhino求值，注册MachineRegistryEvents.registry监听器；调用实际onServerReload，经原生KubeJS EventHandler.post，实际MachineBuilder.register登记机器。保存的Rhino Consumer闭包由ScriptHooks派发至真实RecipeCheckEvent，实际失败理由及脚本计数均断言。
2. 原生ScriptType.SERVER.unload后新建作用域、重绑并执行第二份脚本，再次onServerReload。新结构和新回调替换旧值，旧闭包计数保持1，新闭包调用一次。
3. 再卸载并执行空作用域，实际重载清空staged机器与ScriptHooks回调；旧回调不再触发。

本批无新增问题。补足原报告把真实引擎全部留待实机的部分证据；仅此三条不能证明全脚本能力无缺陷。

边界：ScriptManager与控制器由Unsafe分配，脚本类型manager supplier、Context属性/ThreadLocal、topLevelScope由夹具准备；实际Rhino、KubeJS包装、事件分发和生产重载入口未替换。FMLPaths仅指向审计sandbox。不运行完整FML/KubeJS插件发现、ServerScriptManager.load、磁盘监听、服务器资源应用、存读档或游戏。下一批补全取消、异常隔离及客户端脚本桥。
