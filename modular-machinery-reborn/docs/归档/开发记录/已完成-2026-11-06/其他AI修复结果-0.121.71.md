> **2026-11-05 Codex验收：仅部分改善，未知外部能力支持/安全拒绝仍未完成，不销号。** 统一见[63至82验收](验收-63至82-2026-11-05.md)。当前82有新增搜索误拒，118/1/3；原待验文字为历史。

# MMCER剩余总修复交付 — 0.121.71

**待Codex验收。本批编号：P05-01, P05-09；99/5/18验收账本不变，主任务保持暂停。** 本次执行授权覆盖全部23项，本批交付后继续下一批，不宣称全部完成。

## I1/P05-01、I2/P05-09：最小安全改善，**仍部分交付/待决定，不销号**

实施前invariants.md先列状态/恢复不变量，绝不声称任意外部能力可原子回滚。ResourceTransaction删除未知物品/流体回滚的“清空残余再全量补回”破坏性顺序，仅对可确认同资源且当前与候选完成状态一致的可寻址数量差额尝试补偿；多罐未知/资源变化不猜测回滚，残余保留且抛包含before/current/unreturned的未决诊断。原生可修改仓精确快照恢复路径保持。generic回滚没有临时修改权限/伪造返回栈。

RollbackResidualCheck all9条：改前70的只取10煤→5后late false把残余也清0、100水→60后late false同样毁0，正确“保留残余”断言失败；最终71保留5煤/60水，缺少返还权限的5/40明确报未决而不是silent success。exact原生item/fluid仍精确恢复10/100，外部新异物不被覆盖，多个恢复步骤失败被聚合挂在原始后置异常，两个残余都保留。

EnergyAccess增加显式TransferAccounting收据契约，仅当外部能力能证明**本次调用**的收到量（包括抛错后）才补已知未用差额；未知普通余额getter不当可靠收据。TransferFailure保留原cause及debit/credit（-1=未知），不能把未知写入当0后盲目退款。正常短收仍原差额返还，自动输出仓原来就传backing this.energy，未改成禁止receive的方向壳。

EnergyTransferFailureCheck known6/unknown2条：显式收据下目标写前/收到13/收到40后抛，源余额分别100/87/60、目标0/13/40，实际总量100不变且原异常可追溯。未知目标收到40后抛时不盲目复制退款，源60/目标40，结构化credit unknown；不根据读余额猜测并发安全。

## 未解决与需要的兼容决定

原未知ThrowsBefore反例EnergyFailureRepro在70和71**仍退出1**，后者源60/目标0、debit40/credit unknown；原正确行为断言没有改成通过，不把runner期望1当修复成功。日志/命令/真实退出码及known-unresolved-tests.json明确这一点。

任意外部能力无通用原子/回滚接口；当前仍按兼容策略尽力执行，可能出现无法恢复量或未知余额。下一决定是：无可恢复/收据能力的多步骤付款及能源转移是否在付款前受控拒绝（兼容影响具体为现有仅模拟/执行的外部仓可能不再接受这类跨步骤），还是保留尽力模式并为未决资源建立审核/恢复流程。没有擅自一刀切断第三方能力，也没有盲目退款/删资源。未知IItemHandlerModifiable子类原生式snapshot写回及外部同资源并发变化仍需显式可恢复版本契约，未冒充已证明不覆盖所有新值。

本批不计完成I1/I2，delivery状态为partial，续做JSON仍保留两ID。共同30调用及前批V/W/X/Y/A保持，不扩做P自然灵气足迹或M共享供应器身份。余项继续，不能把本批最小改善充作通用事务收口。


## 构建、归档与证据

证据`_audit/remaining-all-0.121.71/`：scope.tsv、真实改前src/资源/完整类、前后源码/资源/类哈希、production.diff、各条原始命令/独立cwd/退出码/日志及最终生产类加载来源。旧失败按attempt保留；前后基线按真实前一交付连续，不按HEAD覆盖。342 Java / 342资源 / 574 class；相对本批起点只修改scope.tsv允许文件，资源哈希不变、中英各566键一致，JAR JSON/mcmeta可解析。

Java17 Microsoft17.0.11；JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规clean/build/reobf退出0；test NO-SOURCE。完整原始日志、JDK及环境/cwd记录保存，无锁清理/未知进程终止。正式回归使用final-classes完整生产类优先，排除旧production/before/patched/compiled目录；测试/编译使用独立目录。实际测试名称及退出码见审计目录，不以总runner0掩盖专项非零。

新正式JAR：`releases/modular-machinery-reborn/v0.121.71/modular-machinery-reborn-0.121.71.jar`，**1,665,653字节**。build/libs=reobfJar/output.jar=新归档SHA：

`B0CED73C3EB933681F1DA6EDA491CA7CB2F76B436FB804E786B1C3A3E502CB2F`

旧62及更早包保留。[归档说明](../../../../../releases/modular-machinery-reborn/v0.121.71/README.md) · [版本记录](../../版本记录/更新记录-0.121.71.md)。

复跑Python加`-I`：`C:/mmwork/_audit/remaining-tools.py test 71 final <类名> 0 <模式>`；共同回归`regress 71 final`。重跑会保留旧结果attempt；原始命令在本批`*-command.json`。独立续做记录`_audit/remaining-all-progress.json`及[执行进度](剩余总修复-执行进度.md)。

## 未验与边界

仅交付本批编号，不把登记范围扩大为新适配器或新的魔法/能源后端。离线真实生产Java/原生包、NBT/库存与直接Java脚本桥、受控事件/世界壳不等于完整Forge/Rhino/服务器权限/多人网络/磁盘区块/GPU或游戏；未授权启动游戏，因此未实机。未知外部能力回调/并发原子性不作无证据承诺。

没有部署、修改实例/存档、暂存、提交、推送或创建Release，没有reset/clean工作区。Gradle clean仅构建目录。结果待Codex验收，不自行销号或解除暂停。
