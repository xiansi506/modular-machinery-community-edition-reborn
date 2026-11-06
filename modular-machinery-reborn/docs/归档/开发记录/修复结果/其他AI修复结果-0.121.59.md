> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex独立验收：本批指定范围通过离线验收。** 当前62/协议13、最近整版离线通过61；累计99/5/18。[验收](../已完成-2026-11-06/验收-第十二批8项-2026-11-04.md) · [仅Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)。原待验/完成描述保留供追溯；主任务暂停，未实机/部署/推送。

# MMCER 第十二批V修复结果 — 0.121.59

**待Codex验收；累计92/5/25不变，主任务保持暂停。最近已验收整版仍58。**

五种菜单完整int分段同步；组号与开关单项消息分离；数字草稿/待确认/服务端镜像分离，协议升13、两端须同更。

## V1 / P02-02：完成指定范围

新增唯一获准helper `menu/IntContainerData.java`，只作菜单数据编码：原逻辑索引承载低16位，在末尾追加高16位。五种菜单逻辑getter/ContainerData索引契约保持，完整int字段使用两段；接收独立保留原始位段，不从夹取后的实体getter恢复另一半。能源既有四段long与魔法分段没有重新包装。FactoryControllerMenu增加客户端收到值镜像，保留parallel ceiling镜像。

`VCheck words`以真实菜单构造/注册的DataSlot、真实ClientboundContainerSetDataPacket.write/解码和menu.setData测试，111项通过。覆盖0/1/32767/32768/40000/65535/65536/MAX、进位、只变高/低字、dirty-only广播、重复/首次全量与重开窗口；组号、流体/气体数量容量、并行控制器、普通进度/时长/并行/计时、工厂上限/并行/计时读取正确。原四段能源Long.MAX_VALUE保持。分段中间态如196610→236608→40000如实记录，不承诺原子快照；界面程序化更新不发配置包。改前58同一断言在组号32768失败，最终59退出0。

## V2 / P02-03：完成指定范围

HatchGroupUpdatePacket协议13采用字段掩码：1只组号、2只开关、3显式两项；`(int,boolean)`合法Java构造继续表示显式两项，GUI只用单项工厂入口。服务端单项操作保留另一项当前权威值；非法掩码/负组号拒绝，类型/当前菜单/距离/实体身份/可分组校验保持，另保护null hatch。其他消息布局未改；ModNetwork只将协议12升13，两端必须同更。

`VCheck click`真实mouseClicked先提交9再关闭、两条消息按发送顺序真实编解码后调用生产服务端apply。改前58将9恢复7而失败；最终59保持9且关闭，2项通过。`VCheck ui`另覆盖回车立即切换、40000大组号同次点击、启用反方向、两个观察者按单项操作交错、距离/实体失效拒绝、空值/超int受控处理。

## V3 / P02-06：完成指定范围

数字字段分开记录未提交草稿与已提交待确认值。非编辑干净字段采用菜单镜像，正在编辑不覆盖；确认只清对应pending，不抹掉新输入；重建保留应保留的文本/焦点/开关意图。保留P10-08既有开关同步/确认/重建，不称该已修部分仍完全失效。无固定猜测超时。

`VCheck ui`19项通过：init0后同步启用7、干净7→9、草稿12遇旧7、提交12后输入13遇12确认、重建保留13、确认后采用远端15、程序化同步0发包、开关待确认缩窗/确认消除、空值/超int及V2组合。改前58同一断言在首次数字更新失败；最终59退出0。

## 对照、装置与边界

只读旧版PktGroupInputConfig:19–26/50–55分别组号/开关操作，本批恢复这种字段级语义并增加既有服务端保护。未运行旧游戏。

屏幕使用真实EditBox/Button/init/containerTick/mouseClicked；Font固定字宽、声音为受控替身，客户端/玩家/Level为壳装置。出站捕获agent**仅替换ModNetwork.<clinit>的CHANNEL初始化**，不替换屏幕、菜单、包编码或服务端apply；classpath仍最终生产类优先。代理源码`_audit/OutboundCaptureAgent.java`与构建脚本保存；真实网络初始化在裸JVM缺NetworkEvent无参构造，原始装置失败以attempt保留，不冒充自然连接测试。消息按同一连接发送顺序处理，未伪造乱序可达性。

初版装置曾试图继承final控制器，已改用真实实体字段/镜像；真实点击因无Minecraft声音管理器失败后加入受控静音管理器。全部失败原始记录保留。V1测试实体侧部分数值由受控字段提供，不称已形成真实巨量线程工厂或真实Mekanism罐。P02-05已失效菜单保护保持，未扩张会话协议；协议13无请求代次，仍无法无条件区分拒绝/延迟或任意多人确认乱序。分段可见中间值仅用于显示，不被程序化回写。


## 基线、构建与归档

本批起点0.121.58，前后源码/资源/完整class逐项指纹、改前src完整副本、改前/最终完整类、`production.diff`、原始命令/真实退出码与class-load日志见工作区`_audit/twelfth-ai-8tasks-0.121.59/`。保留大量既有未提交改动，不按HEAD恢复。相对本批起点变更源码9份：

- `com/reborn/modularmachinery/client/HatchGroupScreen.java`
- `com/reborn/modularmachinery/menu/FactoryControllerMenu.java`
- `com/reborn/modularmachinery/menu/HatchGroupMenu.java`
- `com/reborn/modularmachinery/menu/IntContainerData.java`
- `com/reborn/modularmachinery/menu/MachineControllerMenu.java`
- `com/reborn/modularmachinery/menu/MachineHatchMenu.java`
- `com/reborn/modularmachinery/menu/ParallelControllerMenu.java`
- `com/reborn/modularmachinery/network/HatchGroupUpdatePacket.java`
- `com/reborn/modularmachinery/network/ModNetwork.java`

本批最终341 Java / 342资源 / 569 class；资源逐项哈希不变，中英各566语言键一致、JAR JSON/mcmeta解析通过。新增helper/类如实计数，不假称仍340/568。

Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf真实退出0，test NO-SOURCE；未使用隔离fallback。`java17-version.log`、`build-normal-command.json`（含环境/cwd）、`build-normal.log`和`build-normal-exit.txt`保存。最终回归先放本批完整final-classes、剔除旧production/before/patched/compiled目录，进程各自独立cwd与日志，不以增量预编译当正式类。

每批改前及最终30个已验收回归调用全部通过：U8模式、U2BoundaryCheck172、LateBusyCheck13、T14、私有NBT12、ItemCopySafetyCheck **fixed**、InterfaceCopyCorrectCheck、FinalTickCheck、ControllerExtraCheck、Q1三条/原身份/428矩阵、创造目标60/暂停/恢复23/独立矩阵14，以及Will1121/LP41/Bloom120、旋转8组/动态9组/多块fixed/尾段fixed。单项专项见上文，所有原始失败/装置失败以attempt编号保留；不把脚本总退出0当某个失败通过。

JAR：`releases/modular-machinery-reborn/v0.121.59/modular-machinery-reborn-0.121.59.jar`，**1,650,552字节**。build/libs、reobfJar/output.jar、新归档SHA一致：

`F91ABBF13A320DEAA34C296A725438542CA357AD2E4E91839E0B936C991F5715`

旧包保留未覆盖；[版本记录](../../版本记录/更新记录-0.121.59.md)，[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.59/README.md)。

复跑工具：Python `C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，加`-I`；`C:/mmwork/_audit/twelfth-tools.py regress 59 final`重跑共同回归；`test 59 final <专项类> 0 <模式>`编译/执行本批专项，详细命令在本批`*-command.json`。复跑保留旧结果为attempt文件，不覆盖初始失败。

## 边界及未做

仅做本批白名单，其他批按串行后续交付；未扩做其他剩余编号、事务框架、AE2供应器或魔法/能源集成。旧版为本地只读源码对照，未运行旧游戏。离线原版包编解码、真实生产Java链、ScriptHooks直接Java桥、壳Level/原生或受控端口不等于真实Rhino/完整FML/服务端权限/多人网络/磁盘区块/GPU或游戏验收。不承诺任意外部能力回调、重入或并发全局原子性。

未部署/启动游戏、改实例/存档、reset/clean工作区、暂存、提交、推送或创建Release；Gradle clean只作用于项目build。账本不销号，不解除暂停。
