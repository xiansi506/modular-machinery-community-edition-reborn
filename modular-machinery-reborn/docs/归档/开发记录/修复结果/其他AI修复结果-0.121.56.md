> **历史归档（2026-11-04整理）** 本次交付/验收记录已结束；原结论、失败和当时版本/待验状态保留，不代表当前授权。当前请看[剩余23项总交接](../已完成-2026-11-06/修复总交接-剩余23项-2026-11-04.md)、[Y1返工](../已完成-2026-11-06/返工交接-第十二批Y1-2026-11-04.md)与[修复进度](../已完成-2026-11-06/修复进度-2026-11-02.md)。

> **2026-11-04 Codex独立验收：T1/T2通过指定范围离线验收，56为最近整版离线通过版本。** 当前57/协议12，累计91/5/26；[验收](../验收记录/验收-第十一批4项-2026-11-04.md) · [仅U2返工](../任务交接/返工交接-第十一批U2-2026-11-04.md)。原交付记录保留供追溯；未实机/部署/推送，主任务暂停。

# MMCER第十一批T修复结果 — 0.121.56

**待Codex验收。仅T1/P04-11、T2/P04-12完成指定离线范围；U未开始。累计88项完成、5项部分、29项待处理不改，主任务保持暂停。**

## 基线与范围

基线0.121.55、HEAD f94ba299e37f0975ac5255f90671f990bacfc61a，协议12。改前独立核对340 Java、342资源、568 class与交接/55完整快照一致，保存完整before源码及before-classes。55归档仍为1645286字节，SHA-256 `55804980FE20744744744C5DCAAEC3029086506BF2A22F72BE36F01ABF31C7C6`。56原未占用。既有未提交修改全部保留，批次差异以before而非HEAD计算。

仅修改三份生产文件：recipe/adapter/DynamicMachineAdapter.java、RecipeAdapterRegistry.java、RecipeAdapterLoader.java；340 Java/568 class无增删，最终仅这三份class改变；342资源逐项哈希不变。公共复制实现、wire、协议和U文件均未修改。

## T1/P04-11：完成

DynamicMachineAdapter改用MachineRequirementParser.copy服务端定义复制；item/fluid/energy数值重建后复制基类的selector-tag、parallelize-unaffected及完整scriptOptions，平行度/triggered/能源临时倍率按新实例重置。非重建类型仍不烘入数值，自定义数字接口覆盖文案保留。删除把客户端网络往返当作完整定义的过时说明。

实际generateAll→MachineRecipe→executionCopy验证无/有修饰符，三分支元数据、运行状态新鲜/隔离，延迟与重复触发、忽略输出检查、燃料和耐久的合法独立组合；实际start/tick/finish、原生库存/流体/能源支付及Java ScriptHooks checker/modifier回调通过。另以filtered/wrapped自定义share-tag物品经过适配器验证真实输出私有NBT、独立显示NBT及第二副本隔离12项；同一正确断言55失败、56通过。数字接口自定义文案组合及原copy回归保持。

旧版只读对照RequirementItem.deepCopyModified明确复制checker、itemModifierList、consumeDurability等定义；本次恢复现有定义保留语义。未运行旧游戏。P04-13 long精度/时长溢出保留既有待修边界，没有改舍入、修饰符顺序或烘入类型。

## T2/P04-12：完成

按解析后的ResourceLocation使用LinkedHashSet去重，保留首次顺序及原SHA-256 generatedId算法。全轮生成ID对已有manager及本轮生成集合校验，真实冲突诊断含声明、目标、源、冲突ID。Loader在replaceRecipes前捕获拒绝，原manager及其占位/原配方不变；正常轮仍只去声明占位。无随机后缀或Map覆盖原配方。

真实generateAll/apply覆盖重复字符串、裸名/完整名别名及逆序、未知/有效混合、不同目标/声明/源独立、连续apply、原生reload重建及声明删除。用原配方恰好使用生产generatedId触发碰撞：55真实replace抛错，56受控返回且原对象身份保持。旧版RecipeRegistry归属展开与DynamicMachineRecipeAdapter作为只读对照；不声称旧版实机正确，也未扩做全局reload事务或全部删除机制。

## 证据与结果

证据根：`C:/Users/kk07H/Desktop/模组/_audit/eleventh-ai-4tasks-0.121.56/`。

| 验证 | 改前55 | 最终56完整正式类 |
|---|---|---|
| AdapterBatchTCheck | 同一正确行为14组失败，退出1 | 14组通过，退出0 |
| AdapterPrivateStackCheck | 私有NBT正确断言失败，退出1 | 12项通过，退出0 |
| ItemCopySafetyCheck fixed、InterfaceCopyCorrectCheck | 均退出0 | 均退出0 |
| AssemblyTargetCheck / CreativePauseCheck / CreativeResumeCheck / CreativeMatrixCheck | 60 / 正常返回 / 23 / 14，通过 | 同样全部通过 |
| WillTypedBoundaryCheck / LpOverflowCheck / BloomCleanupCheck | 沿用55已验基线 | 1121 / 41 / 120，通过 |
| Q1三条IdentitySupplement、原FactoryIdentityCorrectCheck、428 IdentityMatrixCheck、FinalTickCheck | 沿用55已验基线 | 全部退出0 |

每条执行命令、原始日志、真实退出码分别见`*-command.json`、`*.log`、`*-exit.txt`；源码与资源前后哈希、568完整before/final-classes、production.diff、delivery-verification.json均保存。最终classpath将完整final-classes放首位并移除旧compiled/旧生产目录，没有以增量类或生产替身冒充正式类；日志输出生产加载来源。工具准备期曾修正夹具FluidStack参数及修饰符缺io，最终改前/后日志使用同一合法夹具，不能把准备期编译/输入错误算生产复现。

构建Java17 Microsoft17.0.11，JAVA_HOME `C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot`，GRADLE_USER_HOME `C:/mmwork/.gradle-home`，cwd `C:/mmwork/modular-machinery-reborn`：

```text
java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean build --offline --no-daemon -Puse_local_deps=true
```

常规build目录clean/build/reobf成功，退出0，test为NO-SOURCE；无隔离fallback。原始build-normal.log、命令JSON及java17-version.log保留。

正式包`releases/modular-machinery-reborn/v0.121.56/modular-machinery-reborn-0.121.56.jar`，**1,646,317字节**，SHA-256：

`9918538E9D5377A9494E635E78F926AB5B53AB385477530C8EA85D1824AA7F95`

build/libs、reobfJar/output.jar、新归档逐字节一致，mods.toml为56；221个JSON/mcmeta解析通过，中英各566键且集合一致。55旧包未覆盖。工作区/项目README、英文状态、配方说明、迁移日志、版本记录和releases索引已同步。

## 边界及U可复用命令

离线真实生产生成/加载器/资源结算和Java回调已验；机器类型离线绑定、世界/玩家/事件/GPU为既有受控壳，不等于完整Forge reload、Rhino、真实网络、磁盘存档或游戏验收。没有启动游戏、部署、操作实例/存档、git恢复/暂存/提交/推送、创建Release或解除暂停。

Python入口：`C:/Users/kk07H/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`；使用`-I`。复跑T可执行：

```text
python.exe -I C:/mmwork/_audit/run-eleventh-t56.py final
```

准确回归参数在本目录`final-*-command.json`，包括ItemCopySafetyCheck末参数fixed及Q1/FinalTick/创造四套。U使用时将-cp第一项换成U最终完整类目录，并删所有旧compiled/生产目录；其余测试fixture和依赖保留。构建沿用上面的Java17命令和环境；若遇锁文件，保留失败日志后参考`C:/mmwork/_audit/ninth-q1-rework-0.121.52/isolated.gradle`改成本批独立buildDirectory，不删除未知锁或结束未知进程。U以56完整快照与哈希为真实起点，先按U白名单复现和专项回归，本交付不启动U。
