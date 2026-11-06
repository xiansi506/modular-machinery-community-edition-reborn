# P10-b6：JEI运行时与场景缓存验收

2026-11-01，源码0.121.0 / 协议12，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。用户已授权后续自行逐批验收；本批仅勘测，不修代码、构建、部署、启动游戏、暂存、提交或推送。

**新增P10-16（P3）：JEI运行时不可用后，蓝图预览查询桥仍保留并调用旧运行时。** 最终3组PASS含此反例，不是修复通过；主进度仍9/12，P10整体未收口。

## 执行与证据

工件：项目同级`_audit/code-survey-0.121.0/review-p10-b6-lifecycle/`，LifecycleCheck.java、run.py、checks.log、exit-code.txt、source-sha256.txt、validate.py、validation.txt、evidence-sha256.txt。最终退出0。

341项源指纹为339生产源、新夹具及复用b2 GeometrySceneCheck辅助源。只调用注册表/场景辅助方法，不执行旧main；生产源/独立编译类指纹未变，使用review/classes而非build/classes。仅复用能力转换和键态桥。

实际执行插件的available/unavailable生命周期、PreviewJeiActions.show；JEI原料/焦点/GUI运行时接口是受控sink，未关闭真实JEI界面。场景用原生注册表、PreviewLevel与箱子，执行真实RegisterClientReloadListenersEvent注册和注册所得监听器回调，不是全文搜索方法名；仍未派发真实Forge事件、完整资源重载执行器或GPU。

## P10-16（P3）：不可用通知未清空查询桥

ModularMachineryJeiPlugin.onRuntimeAvailable把runtime交给静态PreviewJeiActions.runtime，却没有覆盖IModPlugin.onRuntimeUnavailable；当前JEI15.49接口的默认实现不清理本模组桥。

实际顺序：桥为null时show安全返回false；实际插件available(runtime A)后查询调用A；再调用原生接口unavailable后，桥仍严格引用A，show仍向A发出INPUT查询。之后available(runtime B)替换引用，显式向桥传null也能让show安全返回false。因此缺的是生命周期接线，不是null守卫失效。

影响限定于JEI不可用/重启窗口中的旧运行时引用和调用意图；未执行真实关闭中的运行时对象、实际页面切换或堆存活分析，不称为已证明崩溃或永久世界泄漏。正常新运行时可覆盖旧值，记P3。

建议在unavailable清空桥引用，检查登出/重启次序，测试可用→不可用→再次可用、未就绪时R/U/鼠标和连续通知，保证无运行时则回退宿主。未实施修复。

本地1.12.2 ModIntegrationJEI约250行也只保存available运行时；两代JEI生命周期API不同，不能因为旧版未见清理就认定当前保留失效实例符合语义。这里只对照旧保存方式，不宣称旧版具有同一不可用回调反例。

## 两组正常组合

- **真实监听注册→场景清理→重建：** 新建原生ReloadableResourceManager与RegisterClientReloadListenersEvent，调用实际PreviewBlockEntities.ReloadListener.register，得到1个实际ResourceManagerReloadListener。先给原生箱子场景设置受控failedModel标记，再调用注册所得回调；缓存清空，同一preview/context的下一次prepare生成新箱子和无失败标记的场景。日志中的受控标记异常是测试输入，不是未处理进程崩溃。未执行完整资源加载或总线派发。
- **重载后缓存容量/淘汰/登出组合：** 实际prepare产生9种场景，CACHE保持8；最早项重新访问时确实重建，实际logout回调清空剩余图。未据此宣称实际GC或真实玩家登出事件已经验收。

因此不把查询桥的不可用缺接线，误推广为PreviewBlockEntities重载/登出也未清理。P10-01～16均未修。

下一入口继续世界投影/全局查询与26文件职责收口，随后P10-c模型/隐藏/光晕/Jade客户端、P11/P12；依用户最新授权自动接续，仍逐批保留真实边界。
