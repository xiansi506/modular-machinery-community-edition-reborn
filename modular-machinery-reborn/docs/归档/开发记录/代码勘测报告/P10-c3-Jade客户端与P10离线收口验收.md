# P10-c3：Jade客户端与P10离线收口验收

2026-11-01，源码0.121.0 / 协议12，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。依用户持续授权自行接续；只验收留证，不修生产代码、构建/reobf、部署、启动游戏、暂存、提交或推送。

**新增P10-19（P2）：Jade流体提示按固定行号清空，在混排时会删除其他模组行、留下旧流体行。** 4组PASS含反例，非修复通过。P10经a/b/c补证完成本轮离线复核，真实游戏/图形环境待验；主进度调整为10/12，P11/P12仍待验收。P10-01～19全部未修。

## 工件与测试桥

项目同级`_audit/code-survey-0.121.0/review-p10-c3-jade/`：JadeClientCheck.java、EventBridge.java、run.py、checks.log、exit-code.txt、source-sha256.txt、coverage.tsv、validate.py、validation.txt、evidence-sha256.txt。最终4条PASS、退出0；343源指纹为339生产源、新主夹具、事件桥、c1辅助源和a2翻译辅助源。不执行辅助main，源/独立编译类指纹未变，使用review/classes，无build/classes。

P09-e曾因无FML事件变换、NetworkEvent缺默认构造器而不能完成Jade流体client映射。本批EventBridge仅对NetworkEvent及其GatherLoginPayloadsEvent/LoginPayloadEvent，调用**当前依赖的原生EventSubclassTransformer**，classloader指向当前JVM。不是重写Jade CommonProxy，也不改变FluidView或本模组算法；原生变换日志保留。能力类型/键态桥仍明确存在，没有网络发包或连接服务器。

为让Jade主题/config初始化执行，把FMLPaths仅设到本批sandbox，生成` sandbox/config/jade/jade.json `测试配置；不读写实例/玩家配置。原生Forge fluid-type供应器只在测试JVM注册；字体6px、仓口/Accessor及Minecraft壳受控，Tooltip/FluidView/ProgressElement为真实Jade实现，未绘制GPU。

初版构造仓口时遇测试注册表已冻结，原日志留checks-frozen-fixture-registry.log；随后测试JVM解冻后构造实际FluidPort。另因未设置FMLPaths导致Jade主题初始化失败，原日志留checks-missing-fml-paths.log，最终仅用上述审计sandbox补齐。均为夹具环境边界，不计为游戏缺陷，原P09失败日志不改写。

## P10-19（P2）：固定ROW_FLUID=1删除无关行

实际原生Jade Tooltip依次包含：第0行通用仓名、第1行带review:other标签的其他模组状态、第2行旧流体行。实际FluidStorageSuppressor.onTooltipCollected先替换标题，再用固定第1行的live left/right lists.clear，清掉其他模组状态，却保留第2行旧流体条；随后又添加本模组新条。断言review:other元素已消失、旧jade:fluid_storage仍存在。第二次回调标题匹配去重，不再额外增行，但无法修复第一次误删。

Accessor/行顺序为夹具提供的有效混排组合，真实Tooltip变更和FluidView读取执行；未安装真实第三方提示addon或实机验证其事件顺序。影响限其他提示插入标题与流体行之间的混排，不说成所有流体仓总是丢行。是UI信息误删/旧条残留，不涉及库存或服务端数据，记P2。

建议以归属标记或确定的流体元素识别替换，无法确认归属时不要清掉任意第1行；优先从自己的provider给元素打稳定标记，保留左右两侧其他元素。仅把固定下标从1改为2不能解决可变混排。重复回调去重与内容行归属是两件事，应分别回归。未修复。

1.12.2对应提示集成主要使用其旧信息provider，当前Jade11固定行替换为1.20.1实现，不把此Jade行结构问题归为旧版同形逻辑。旧显示语义可对照，不能代替当前Jade实际行契约。

## 另外三组正常对照

- **服务器→客户端流体：** 实际仓口server provider写出water1000/32000，原生FluidView.readDefault及实际client.getClientGroups保留ratio=0.03125和数量/容量标签。本批关闭P09-e原生reader未执行的证据缺口，仍不称完整FML/网络/图形验收。
- **替换范围：** 原生Tooltip中普通仓名+流体两行能正确替换；空罐保留其他模组行；外部原版方块不被回调修改。说明误删来自行布局变化，不是所有回调都失败。重复回调对照见反例组。
- **控制器客户端信息：** 提供与server字段契约一致的NBT，实际ControllerInfoProviders.Client→原生Tooltip显示owner/结构/状态/并行，30,000,000/60,000,000为50%，energyIn=2^53+1在普通/详情文本保留精确数字，未成型早退仅owner+结构2行。此处NBT来自夹具；P09已执行的server快照证据不重跑。不能据此撤销P10-03控制器GUI百分比溢出。

## 责任收口与保留边界

coverage.tsv核对原P10-c的11文件指纹，并补5份Jade文件，共16项职责/证据/边界；b7另有原P10-b 26文件表，a4有普通GUI 30文件表。跨部分文件按职责，不把总表项相加当作不重复文件数，也不声称全方法动态执行。

采纳c1隐藏/光晕CPU清理、c2骨骼/动画状态及缓冲、c3真实Jade客户端映射/混排；加a1～a4和b1～b8的独立补证，P10本轮离线复核收口。原静态报告“无新增”不再代表完整结论。GeoRenderers/颜色事件实际注册、coremod变换及shader/模型资源引用交P12；不把这些责任转交写成已启动通过。

仍待真实环境：完整Forge/JEI/Rhino客户端生命周期、OS/IME/窗口与全局UI路由、资源拼接/动画/Gecko资产播放、FBO/GL/透明排序/emissive/bloom实际效果、地形工作线程与Embeddium/Oculus、真实区块/网络/多人时序。离线夹具只覆盖已明确的算法和调用契约，不能称整个客户端无问题。

下一入口P11实际脚本/事件重载生命周期及40文件职责收口，随后P12初始化/可选依赖/资源与全项目收口，按授权继续。
