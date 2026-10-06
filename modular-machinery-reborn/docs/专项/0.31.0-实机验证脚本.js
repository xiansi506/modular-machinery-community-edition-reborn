// ============================================================================================
//  0.31.0 实机验证脚本（v2b：KubeJS 写完整机器定义）
// ============================================================================================
//
//  怎么用：
//    1. 把本文件复制到  <实例>/kubejs/server_scripts/  （例如  D:\.minecraft\versions\彩虹花园\kubejs\server_scripts\）
//    2. 进游戏 →  /reload  →  先看聊天栏与日志有没有报错
//    3. 按下面「一看就知道」的预期逐条核对；每一步都写明了**看哪里**。
//
//  ------------------------------------------------------------------------------------------
//  要搭的结构（控制器放在 C 那一格，朝向**北**）
//
//      y+1 :        V                V = modular_machinery_reborn:blockcasing[casing=vent]
//      y+0 :        C                C = 通用 machine_controller
//      y-1 :   S    S    .    S    S     S = minecraft:stone（正东/正南/正西/正北各隔 1 格）
//
//  ⚠️ 本机器的 parts **只接受通风机箱**（见下面 .modifier 与 .part），所以：
//     - 若 .modifier / .part 没被接受 → **结构根本不成型**，界面显示「找到结构：无」；
//     - 若成型 → 说明这两个字段真的进了定义。
//     这是本脚本最省事的一条判据：**成型本身就是断言**。
//  ------------------------------------------------------------------------------------------
//
//  ------------------------------------------------------------------------------------------
//  ⚠️ 关于这个实例（彩虹花园）的一个已知事实，先说明免得误判：
//     该实例里**没有任何内置机器** —— jar 内 data/ 是空的（内容外置，M10），
//     而 datapacks/ 目录为空、配置目录只有作者自己的机器（immortal_bloom_assembler 与 machine_1）。
//     所以：
//       * 本脚本自己在 KubeJS 里定义机器，不依赖任何内置机器；
//       * 实例里那个旧的 mmce_reborn_demo.js 把配方绑到 `alloy_furnace`，**这台机器在此实例不存在**，
//         那条配方不会生效 —— 这是环境事实，**不是 0.31.0 的问题**；
//       * 别指望用 /mm-get_blueprint alloy_furnace 之类拿到蓝图。
//  ------------------------------------------------------------------------------------------
//
//  预期（对应 迁移日志.md 的 0.31.0 清单）
//    [1] /reload 后日志：`posted; 2 machine definition(s) were staged`（2 = 下面两台机器）
//    [2] 界面「找到结构：v2b 全字段机器」
//    [3] 右键蓝图开预览 →「额外信息：」里有「内置并行数：3」        ← internalParallelism(3)
//    [4] 同一处有「基础线程数（仅集成控制器）：7」                   ← hasFactory(true) + maxThreads(7)
//    [5] 同一处有「特殊线程数（仅集成控制器）：2」                   ← coreThread(...) ×2
//    [6] v2b_blueprint_machine：不插蓝图**不成型**；插上它自己的蓝图才成型
//        右键它的蓝图开预览 → 额外信息里应有红色的「需要蓝图。」
//    [7] 界面/预览里的「最大并行数：64」                            ← maxParallelism(64)
//        （它只在**不等于默认 2048** 时才显示，所以能看到它本身就是证据）
//
//  注：文案取自 lang 文件，冒号是**全角**「：」；界面里数字带颜色代码，所以只要看到那个词与数字即可。
//      这三行各自有**显示条件**，这也帮我们区分「字段没读到」与「界面不显示」：
//        内置并行数  只在 internalParallelism > 0 时显示  → 我们写 3，应出现
//        最大并行数  只在 ≠ 2048 时显示                  → 我们写 64，应出现
//        基础线程数  只在 hasFactory 时显示              → 我们设 true，应出现
//        特殊线程数  只在 coreThreads 非空时显示          → 我们写了 2 条，应出现
//      所以「这四行都出现」= 这五个字段全部真的进了定义。
// ============================================================================================

MachineRegistryEvents.registry(event => {

  // ================================================================ 主测试机：写满 11 个字段
  event.machine('v2b_all_fields')
    .localizedName('v2b 全字段机器')

    // --- 三个并行字段。用 3 而不是默认的 0/1，这样「字段被读到了」在界面上可见（内置并行数 [3]）
    .maxParallelism(64)
    .internalParallelism(3)
    .parallelizable(true)

    // --- 工厂四字段。has-factory 让「额外信息」多出基础线程数一行；core-threads 再加特殊线程数一行
    //     ⚠️ 脚本机器**拿不到**专属工厂控制器方块（方块在构造期注册，D8），
    //        所以这里只验证「字段进了定义」，**不验证「能放工厂控制器」**。
    .hasFactory(true)
    .factoryOnly(false)
    .maxThreads(7)
    .coreThread('smelter')                              // 不写配方 = 什么配方都能跑
    // 钉死到一条**本脚本没定义**的配方名 —— 这是故意的：本机器（只有石头、没有仓口）
    // 根本不可能加工，所以这条核心线程只会**一直闲置**。它存在的唯一目的是让
    // 「特殊线程数（仅集成控制器）：2」那一行出现（该行按**条目数**显示，不要求配方存在）。
    // ⚠️ 所以：**不要**期待它跑起来，也**不要**因此判定核心线程有问题。
    //    真要验证加工行为，用第四步那份脚本（docs/专项/0.31.0-第四步-行为验证脚本.js）。
    .coreThread('pinned', 'v2b_demo_recipe')

    // --- failure-action：合法值之一。它的**行为**（冻结/退格/清零）要一台真在加工的机器才看得出，
    //     见文件末尾「第二阶段」。
    .failureAction('decrease')

    // --- 结构修改器：控制器上方那格，只接受通风机箱 → 物品产出翻倍
    //     用对象字面量写法（0.31.0 新增支持的另一种写法是 JSON 字符串，两者等价）
    .modifier(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=vent]',
              { target: 'item', io: 'output', operation: 1, multiplier: 2.0 })

    // --- 智能数据接口类型声明（数值类型）。要在界面上看到那行，需要一条引用它的配方，
    //     见「第二阶段」；此处只验证字段进了定义。
    .smartInterface('mode', 0, 1000)
    .smartInterface('temp', 20, 0, { header: 'gui.example.temp' })

    // --- 主测试机留 false，否则没蓝图就不成型（蓝图那条规则由第二台验证）
    .requiresBlueprint(false)

    // --- 结构：坐标相对控制器，(0,0,0) 是控制器自己那格（会被 schema 跳过）
    .part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=vent]')   // 上方：通风机箱
    .parts([-2, -1, 1, 2], [-1], [0], 'minecraft:stone')                  // 下层沿 X 的四个
    .parts([0], [-1], [-2, -1, 1, 2], 'minecraft:stone')                  // 下层沿 Z 的四个
    // 注：上面两条合起来就是搭法里的「十字」，中心 (0,-1,0) 留空
    .register()

  // ================================================================ 第二台：验证 requires-blueprint
  event.machine('v2b_blueprint_machine')
    .localizedName('v2b 需要蓝图')
    .requiresBlueprint(true)
    .part(0, -1, 0, 'minecraft:stone')
    .register()

})

// ---------------------------------------------------------------- 配套配方（第二阶段用）
// 给 core-thread 里写的 'v2b_demo_recipe' 一个真身，并引用上面声明的 'mode' 接口类型。
// ⚠️ interface_number_input 的字段名是 interface / minValue / maxValue（不是 min/max），
//    且只接受 io-type: input。
ServerEvents.recipes(event => {
  event.recipes.modular_machinery_reborn.machine({
    machine: 'v2b_all_fields',
    registryName: 'v2b_demo_recipe',
    recipeTime: 60,
    requirements: [
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 20 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'input', item: 'minecraft:stone', amount: 1 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'output', item: 'minecraft:diamond', amount: 1 },
      { type: 'modular_machinery_reborn:interface_number_input', 'io-type': 'input',
        interface: 'mode', minValue: 0, maxValue: 10 }
    ]
  })
})

// ============================================================================================
//  第四步（验证「字段的**行为**」，而不只是「字段进了定义」）另起一份脚本：
//
//      docs/专项/0.31.0-第四步-行为验证脚本.js
//
//  那一份是**可整段替换**的：自带一台**能真的加工**的机器（石头十字那四格同时接受石头与三种仓口
//  —— 所以放石头就成型、换仓口就能加工，不用改脚本）、三条配方（基础 / 高能耗必断电 / 带接口闸门），
//  并逐条写明 A–E「改哪里 → 看哪里 → 应该是什么」：
//
//      A  并行真的结算 N 份
//      B  failure-action 真的起作用（含 reset 会重扣输入的提醒）
//      C  smart-interfaces 真的是闸门
//      D  modifier 真的改产出 —— **含那个最容易误判成「0.31.0 没生效」的 (0,0,0) 坑**
//      E  校验仍在 schema（改错脚本看报错，并强调要与数据包**逐字相同**）
//
//  本文件（第三步那份）只需要前三步用到的部分。
// ============================================================================================
