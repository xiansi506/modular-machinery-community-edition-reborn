# P09-b：植物魔法与LP网络契约验收

日期2026-11-01；基线0.121.0 / 协议12，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。只勘测、验证和写审计记录，未修改生产代码/版本、正式构建、部署、启动游戏、暂存、提交或推送。

**结论：补充植物魔法与LP的实际网络/能力证据，普通场景通过；新增P09-02（P3，极大存档缓存边界）：LP输出调用原生SoulNetwork.add时发生int溢出，返回值再次回绕成允许的正数，现有守卫未能拦截。** 不是普通容量配方必现。P09仍有PNC/Embers、Flux/DE和Jade契约未收口，总进度保持8/12。

## 实际验证与边界

工件：项目同级 `_audit/code-survey-0.121.0/review-p09-mana-lp/`，入口 `ManaLpCheck.java`、`run.py`；最终 `checks.log` 6条PASS，`exit-code.txt=0`。其中第六条是已知故障复现，不能当作修复通过。`source-sha256.txt`为339份生产源码+1夹具；使用此前独立全量编译的review/classes并核对源码指纹，关键生产类另断言运行来源，不用残留build/classes。字节码、编译日志、运行参数、依赖/原始JAR元数据和哈希一并保存。

生产范围：BotaniaBackend全部方法的API映射、输出注册/移除与能力状态，BloodMagicBackend的LP绑定/补给/缓存访问，MagicHatchBlockEntity的LP缓存load及必要关联结算链。原生依赖 **Botania1.20.1-455、Blood Magic3.3.8-50**，不把该结果扩张到整合包其他版本。使用实际ManaNetworkHandler的onNetworkEvent、getAllPoolsInWorld和getClosestPool，以及实际BMWorldSavedData、NetworkHelper、SoulNetwork、Binding、SoulTicket。

离线壳与桥明确如下：世界/仓口用Unsafe实例，仓口changed()为空避免存档副作用；测试OrbItem实现真实IBloodOrb/IBindable接口、容量5000，绑定经原生Binding NBT。BotaniaAPI入口代理只提供网络，fire事件桥直接交实际ManaNetworkHandler.onNetworkEvent；没有声称执行原生Xplat/Forge全事件派发。Blood Magic注入内存BMWorldSavedData，EVENT_BUS.post桥允许确定性取消或修改实际SoulNetworkEvent，再由真实SoulNetwork算法结算。没有完整FML/游戏、磁盘、实体魔力脉冲、真实火花传输、生存玩家/产能花tick、任意第三方监听器副作用、多人与完整区块生命周期。

与旧0.118.0夹具相比，本批不再用集合替身实现魔力网络存取/查找，并新增实际网络容量事件、跨仓共享LP网络、计划期间收款、真实load极值等组合；旧夹具/日志不重写。

## 六组结果

| 组 | 已执行结果 |
|---|---|
| 魔力输出网络与生命周期 | 实际Handler中只注册一次，最近池查询命中输出仓，不同world隔离；invalidate移除并关闭旧能力/访问，手持旧receiver不能写；revive重新注册，重复unload不重复移除 |
| 魔力接收、火花API及事务 | 输入不伪装ManaPool；正负int极值接收被夹到0..100000；client写拒绝；计划扣30后收到7，提交拒绝且保留107；后续record失败回滚保持107；火花可用空间/输入完成接口返回符合当前设计 |
| LP输入与共享网络 | 非5倍tick不补给；同网络900由两仓分别拿500、400，合计守恒；普通配方扣缓存100，不额外扣网络 |
| LP输出、事件与取消 | 网络4900/输出缓存500/容量5000，只回填100；实际Fill.setMaximum(75)将回填限定75；取消Syphon/Fill后网络及两侧缓存不变 |
| 绑定失效与回滚 | 规划后移除宝珠导致commit拒绝；畸形绑定NBT不崩；后续record失败恢复缓存，网络75不变 |
| 极值存档与正常对照 | 真实仓口load接受stored=Integer.MAX_VALUE及正常5000容量宝珠；native add后缓存0、网络-2147483549。普通缓存5000/网络100对照得到缓存100/网络5000 |

## P09-02（P3）：极大LP输出缓存绕过返回值守卫

触发前提：输出仓从当前load路径读入极大的正int缓存。本批用2147483647，正常绑定宝珠容量5000，网络余额100。**没有证明普通内置宝珠配方能自然累积此数值**，没有声称联网玩家可注入任意NBT；这是项目接受的存档数值边界，可能涉及异常/旧存档或额外模组扩展。当前普通结算容量限制仍在。

调用链与算术：

1. `MagicHatchBlockEntity.load`对LP的stored只做max(0,值)，没有按当前orb容量截断，真实load保留2147483647。
2. `BloodMagicBackend.tick`输出分支将全部缓存作为SoulTicket传给native SoulNetwork.add(ticket,capacity)。
3. Blood Magic3.3.8-50的add先算int `before + ticket.amount`再取min(maximum,结果)。100+2147483647回绕为-2147483549，负数被写入网络。
4. 其返回值是int `after-before`，-2147483549-100再次回绕为2147483647。项目仅检查moved<0或moved>stored，此时均不成立，于是把缓存减到0。
5. 以long看原合计2147483747变成-2147483549，守恒破坏。普通缓存5000对照不溢出，回填至5000后保留缓存100。

根因包含原生BM加法溢出及本集成未限制入参/未核对真实余额变动，不能仅凭moved合法就说已覆盖所有溢出。本轮不是修复：`checks-before-overflow-diagnosis.log`与`overflow-diagnosis.log`保留最初“应补偿”的断言失败及实际余额，最终改为故障复现断言，**没有改生产算法使其通过**。

建议先按安全的long算术限定本次回填量，保留高于当前容量的缓存，避免直接销毁；对实际前后网络余额及返回量做一致性核对，并考虑Fill事件改变maximum的契约。修复后补边界INT_MAX、普通部分回填、缩小orb容量、取消与修改事件、存读档组合。事件可能有额外副作用，不能把setCurrentEssence恢复就描述成任意事件“完全回滚”。

## 对原报告的采纳与旧版对照

- 采纳“LP事务只动仓内缓存、普通绑定失效会拒绝提交”，本批以实际绑定/网络与回滚对照补证；不扩大为网络补给、任意事件副作用均可事务回滚。
- 采纳输出ManaPool原生Handler可以注册/查询/移除；接收API、能力失效和revive的离线直接调用有依据；不把手工事件桥叫作完整Forge生命周期。
- 原报告关于溢出守卫“在位所以安全”的笼统结论需加P09-02边界。源码里对返回moved有检查，但不证明原生内部没有溢出。
- 本地1.12.2 `TileLifeEssenceProvider.readCustomNBT:69`读int缓存，Output.doRestrictedTick约143行也把整个缓存送SoulNetwork.add；旧设计同样有缓存和网络旁路。旧版 `TileManaProvider`输出约93～106行加池/移池，当前转能力和Handler桥。仅静态比较，未运行1.12.2，也不声称P09-02一定是新移植回归；普通缓存与方向的设计选择不列漏洞。

## 下一步

P09-a已记录Aura/Will，P09-b补Mana/LP。接续P09-c的PNC/Embers真实能力、均压/移动量及恢复，再补Flux/DE和Jade必要契约；P09整体仍未验收，8/12不变。P09-01/02与此前问题均未修复，保留全部旧材料。
