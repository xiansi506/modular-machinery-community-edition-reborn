# P07-a1：静态结构、朝向与NBT匹配

日期2026-10-31（北京时间）。基线 **0.121.0 / 协议12**，HEAD `f94ba299e37f0975ac5255f90671f990bacfc61a`。本轮只勘测/复现，未修生产代码、教程或玩家配置，未正式构建、部署、启动游戏、暂存、提交或推送。保留既有README、gradle.properties外部改动及其他审计成果。

新增 **P07-01～03（均P2）**：非法结构checker类型延迟到控制器tick抛错、单块修改器缺少替代基座的匹配路径、方向属性的单块修改器未随控制器旋转。另将共享NBT列表问题扩展原编号P04-05，登记 **P07-O1重复解析NBT的优化候选**；可变Builder/API观察不算正常游戏漏洞。各发现均未修复。**P07-a1离线完成，实机待验；P07主部分仍进行中，总进度6 / 12。**

## 范围与证据

检查BlockMatcher的描述符/部分状态/原版与自定义rotate回调、MachinePattern的静态展开/边界/规则/旋转/Level匹配，MachineRegistry的静态朝向及候选选择，MachineSchema.readParts/readModifiers、控制器evaluateModifiers与真实结算。结构NBT调用实际NbtPredicate/LiteralNbt和原生FurnaceBlockEntity.saveWithFullMetadata；熔炉子类只对saveAdditional计数后调用super，不替换原生NBT内容。

重编译 **55个当前生产类、2个夹具类，共57条源码哈希**；只运行StaticPatternAuditCheck.main，复用a1控制器注册辅助，不运行旧main。最终 **11组PASS**，含三项缺陷成功复现。世界为Unsafe分配的Level壳，提供内存状态/实体/loaded集合和原生RecipeManager；没有真实区块提供器、物理存档、Forge游戏、Rhino、网络、OpenGL或已安装第三方方块验收。

单块修改器结算样例先实际匹配世界/形成机器/汇总效果，再注入原生物品/long能源端口存储并继续实际控制器tick；不是物理仓口能力收集验收。匹配器耦合属性样例注册了仅存在于夹具进程的自定义方块，用独立真值表验证精确候选；Forge注册表解冻只发生在该离线进程。旧1.12.2只做本地源码对照，没有执行旧版游戏。

## P07-01：非法script-checker通过加载后，在结构检查中抛异常（P2）

最小机器部件为 `(1,0,0)` 的石头：

```json
{"registryname":"audit:bad","localizedname":"Audit","parts":[{"x":1,"elements":"minecraft:stone","script-checker":{}}]}
```

实际MachineSchema.read接受对象、null、空数组三种script-checker值，MachineRegistry.replace也能建立旋转表。内存世界放对应石头后，MachinePattern.matches及实际MachineControllerBlockEntity.tick均抛出未捕获的JSON转换RuntimeException；夹具核对了直接匹配与完整tick抛出的异常类型及信息一致。已注册的正确字符串token可正常匹配，未注册的正确字符串token由ScriptHooks捕获并安全拒绝，不会产生这次转换异常；日志中未注册token的错误属于预期控制样例。

**原因：** MachineSchema.readParts:813-817只拷贝script-checker，对旁边nbt/preview-nbt有验证，对此字段没有类型约束。MachinePattern.matchesRule:150在调用ScriptHooks.checkBlock之前求值getAsString，异常发生在参数求值阶段，进不到checkBlock内部catch。

**边界：** 需要有权限作者提供错误配置/原始JSON；正常KubeJS构建器产生字符串token。没有证明普通玩家能注入字段。异常已实际逃出控制器tick，未启动真实Forge服务器，不能写成已实机崩服/存档破坏。客户端按代码跳过服务端checker，样例客户端分支可匹配，这是静态/壳Level对照，不代表实际GUI验收。

**建议：** 在定义读取时只接受合规的非空字符串token，对错误类型给出机器ID、部件坐标和字段路径；发布前拒绝无效规则。运行期仍应有受控的失败关闭/诊断边界。不能只把异常吞成true，也不能只在checker回调内部catch。回归有效/缺失token、对象/null/数组/数字/空串、四朝向、服务器与客户端重建、实际控制器tick。

## P07-02：单块修改器的替代方块无法代替基础方块成型（P2）

实际schema定义基础parts在 `(1,0,0)` 要石头，modifiers在同坐标允许金块并提供能源输入×0.5。石头世界可匹配；把它换成声明中的金块后，修改器候选本身匹配金块，但MachineRegistry.findMatch返回null。只在基础elements里额外手写 `["minecraft:stone","minecraft:gold_block"]`，相同世界才可形成。

**根因：** MachineRegistry.matchMachine:254先让纯basePattern.matches决定整机是否成立，未把单块修改器的候选作为替代路径；MachineSchema.readModifiers只是创建效果定义，没有接入结构替代检查。控制器稍后的evaluateModifiers因此没有机会处理被基座拒绝的替代块。`MachinePattern.withMerged`的候选并集并未在此实际调用，不能用其存在证明已支持修改器替代。

**1.12.2对照：** BlockArray.matches:305-333明确在基础BlockInformation不匹配时，尝试该坐标的modifierReplacementPattern；TileMultiblockMachineController.matchesRotation:427-434传入随朝向旋转的替代表。旧版正常允许这个金块替代石头的语义。本版MachineModifier也将自身称为SingleBlockModifierReplacement并声称保留该语义。本批未运行旧版代码，不把静态旧链说成旧游戏实测。

**影响与建议：** 作者采用替代基座的常规单块升级布局时，放上升级块反而拆散机器。基础elements手动加入全部替代块是绕过方式，不能视为自动替代机制已实现。匹配时应按同坐标候选/规则受控地接受有效替代，保留原基础候选及NBT/标签等约束；不应无条件放宽整个结构。与P07-03一起处理朝向，回归基础/替代/无效块、多个候选、已有基础NBT、缺失区块、旋转、动态/多块交互；后两类完整几何留P07-a2。

## P07-03：修改器只旋转偏移，没有旋转方块状态约束（P2）

部件基座在控制器东侧为石头；单块修改器在正上方，接受 `minecraft:furnace[facing=east]`，贡献能源输入×0.5。实际schema、世界匹配、效果汇总和后续控制器结算：

| 控制器朝向 | 正上方熔炉朝向 | 实际效果/1tick结算 |
|---|---|---|
| 北 | 东 | 1煤→1石头，扣5FE，符合定义 |
| 西，基座也正确转到北侧 | 北（按逆时针规则正确旋转） | 修改器消失，仍产1石头但扣10FE |
| 西，基座正确 | 东（保留未旋转朝向） | 错误获得修改器，扣5FE |

正上方坐标旋转后不变，这样独立隔离了状态约束问题；一般偏移自身也会旋转。实际端口存储在结构匹配后注入，见范围边界。

**原因与旧版：** MachineControllerBlockEntity.evaluateModifiers:1395使用modifier.rotatedOffset(rotationSteps)，但:1400直接遍历modifier.accepted里的北向原始matcher。MachineModifier.matches同样只是直接匹配。旧TileMultiblockMachineController.updateModifiers:883-905既旋转offset，也反复info.copyRotateYCCW旋转候选状态。本批普通控制器已原生复现；工厂共用这段汇总是静态延伸，没有单独运行工厂本例。

**建议：** 使用与基础结构一致的BlockMatcher旋转规则，既旋转位置也旋转约束，并保留未指定属性的通配语义。不能只改descriptor中的facing文本：原版/模组方块可能耦合属性或需要精确候选集合；本批的自定义耦合属性真值表验证了现有基础BlockMatcher能正确保留集合。回归四朝向、部分属性、多候选、无方向块、普通/工厂和同位置替代。

**兼容差异记录：** 旧BlockArray.matchesState在没有TileEntity时会跳过非空matchingTag并放行，本版以空NBT参加比较、非空模板失败关闭；旧高级NBT checker也要求TileEntity，本版通用script-checker可检查普通方块。这里只记录接口/失败策略差别，没有仅凭旧版更宽松就新增漏洞或擅自改变现行语义。

## 既有根因扩展：P04-05也会影响结构NBT

原生熔炉两个库存槽的Count依次为1、2，结构NBT模板的两个Items条件依次要求Count>=1、Count=1。存在合法的一对一分配，但实际结构匹配失败；把原生槽里的Count换成2、1后匹配成功。夹具独立穷举两个元素的不同下标，验证两种顺序都有合法分配。

根因仍是NbtPredicate.ListNode的贪心消耗，与[P04-05](P04-b-物品NBT与备选原料.md)相同，沿用旧编号。本地1.12.2 NBTMatchingHelper.matchList:81-102也使用贪心流程，属于继承局限，不新增移植回归编号。字面NBT保留自身列表语义，不能为修复旧模板列表而一并改掉字面快照的类型/顺序契约。

## 优化候选P07-O1：每次结构检查重复构建NBT谓词

MachineSchema.readParts:816已解析验证nbt，但没有保留结果；MachinePattern.matchesRule:148每次再次NbtPredicate.parse。同一不变规则、同一原生熔炉，连续25次实际pattern.matches，校准过的合法JSON字符串探针getAsString被读25次，熔炉序列化25次；对预先解析的同一真实NbtPredicate连续matches25次，源探针读取0次。校准为每次parse恰1次读源，探针只用于计数，不是用户JSON中的特殊格式。

这是重复解析路径/次数证据，不是TPS、正则耗时、内存或真实大型结构基准，也未证明它是主瓶颈。可在每份不可变定义/规则构建时保存已解析谓词，旋转仅移动其位置；重载时更换该代定义。缓存模板，不缓存世界NBT或匹配结果，不跨世界保存Level/实体引用。仍须每次读取当前方块实体数据；本轮没有把序列化25次当作可以直接删除的开销。

## 正常控制、维护观察与范围清单

- 明确枚举非零原点的北/西/南/东坐标与楼梯朝向，实际registry匹配四向均正确，错误朝向被拒；没有用同一个旋转函数生成期望值。耦合属性自定义方块按4状态独立真值表检查四次旋转，不误拓宽候选，返回列表缓存不可修改。
- 部分状态约束不影响未声明属性；未知方块/属性/值和旧@meta错误被拒。坐标数组展开为明确7点，根原点被跳过，标签/包围盒/负步数归一正确。普通parts同位置候选并集是本版既有明确规则；旧BlockArray.addBlock为put覆盖，不把这种已明确差别单独算缺陷。
- 实际原生熔炉template/literal部分匹配、库存变化、数字类型拒绝、缺失BE、旋转后规则位置正确；preview-nbt不限制匹配，也不会因此读取BE。卸载/方块不符在状态或NBT访问前短路。这只是loaded集合的调用保护，真实区块边界/加载触发仍P07-a2。
- 实际候选选择符合显式顺序：首个注册的可匹配机器、蓝图先于绑定、未知蓝图回退绑定、绑定失败不退回其他机器、requires-blueprint在通用/绑定路径生效。没有重复P03-07的错误布尔解析输入。
- Builder.build共享内部候选列表，positions外层只读而内层可改，MutableBlockPos参数可改变持有键和包围盒。实际API样例已复现；当前schema/动态/投影等生产者使用新不可变位置且构建后不再修改，未找到正常在树内调用者触发这种修改。仅建议防御性快照，不声称正常机器定义已被外部玩家篡改。它也不同于P03-05的KubeJS来源JSON别名问题。

文件/方法边界保存在scope.tsv。MachineRegistry动态matchMachine之后的段量测、MachinePattern.withMerged复杂交互、多块修改器、结构变更索引及物理区块完整留P07-a2；仓口能力/共享别名/存读档/拆除掉落与自动搭建继续分批。KubeJS/Rhino生命周期和回调权限留P11，JEI/投影/OpenGL留P10，真实能源后端留P09。

## 验证记录与接续

项目同级 `_audit/code-survey-0.121.0/P07-a1/`保留StaticPatternAuditCheck.java、run.py、57条源码哈希、旧版参考源码哈希、参数、javac.log/checks.log、scope.tsv与validation.txt。四份fixture-attempt日志保留夹具纠错过程：熔炉库存操作起初没有Level、错误沿用旧short类型假设、优化计数探针起初使用不被旧模板接受的裸数字；最终改用实际原生Level与当前Forge熔炉保存的int BurnTime、合法字符串探针。**这些夹具错误没有计成生产缺陷。** 当前依赖中的实际NBT类型由saveWithFullMetadata确认，不将旧1.12.2的标签类型直接套到1.20.1 Forge。未注册checker控制样例的预期错误日志也保留。

最终11组完成、进程退出0；PASS包含问题复现，并非修复验收。生产Java、build.gradle、HEAD与版本不变，原有gradle.properties三行外部改动保持d1记录的哈希。

**本子批离线完成，实机待验。下一轮P07-a2：动态段/多块条件的几何与合并、结构变更索引及区块边界。** P07整体未完成，总进度仍6 / 12；本轮到此停止。
