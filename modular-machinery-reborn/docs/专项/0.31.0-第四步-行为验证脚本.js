// ============================================================================================
//  0.31.0 实机验证 · 第四步脚本（验证「行为」，不只是「字段进了定义」）
// ============================================================================================
//
//  用途：前三步证明了「11 个字段都进了 MachineDefinition」。本脚本让机器**真的跑起来**，
//        逐条验证这些字段的**行为**（并行真的结算 N 份、failure-action 真的起作用、
//        接口数值真的是闸门、modifier 真的改了产出）。
//
//  怎么用：
//    1. 把本文件放进  <实例>/kubejs/server_scripts/       （和第三步那个脚本可以共存）
//    2. /reload
//    3. 按下面 A–E 逐步做。每一步都写了「改哪里 → 看哪里 → 应该是什么」。
//
//  ------------------------------------------------------------------------------------------
//  结构（控制器朝北；C = 通用 machine_controller）
//
//      y+1 :        V              V = blockcasing[casing=vent]（**必须有**，见下）
//      y+0 :        C              C = machine_controller
//      y-1 :   [1]  [2]  .  [3]  [4]     四格是「石头十字」的位置，从左到右记作 ①②③④
//
//  ①–④ 每格**接受石头、也接受仓口**。所以：
//     - 先全放石头 → 机器成型但不会加工（没有仓口）；
//     - 做 A–D 时按下面点名把某格换成仓口 → 同一台机器立刻能加工。
//  方块 id：
//     物品输入仓  modular_machinery_reborn:item_input_hatch[size=<等级>]
//     物品输出仓  modular_machinery_reborn:item_output_hatch[size=<等级>]
//     能源输入仓  modular_machinery_reborn:energy_input_hatch[size=<等级>]
//     等级：tiny / small / normal / reinforced / big / huge / ludicrous（能源另有 ultimate）
//     例：item_input_hatch[size=normal]
//
//  ⚠️ 这一台**不要**把 y+1 那格换成别的方块 —— .modifier 与 .part 都只接受通风机箱，
//     换掉就不成型。这正是 D 项要用的性质。
//  ------------------------------------------------------------------------------------------

MachineRegistryEvents.registry(event => {
  event.machine('v2b_phase2')
    .localizedName('v2b 行为验证机')

    // --- 并行：先用 3（A 项要看的就是它）
    .maxParallelism(64)
    .internalParallelism(3)
    .parallelizable(true)

    // --- 工厂/线程：让预览的四行齐备
    .hasFactory(true)
    .maxThreads(7)
    .coreThread('smelter')
    .coreThread('pinned', 'v2b_p2_basic')     // 钉死到不需要接口的那条配方，方便测试
    .coreThread('pinned_energy', 'v2b_p2_energy')
    .coreThread('pinned_gated', 'v2b_p2_gated')

    // --- failure-action：B 项要改的就是这个值
    .failureAction('decrease')

    // --- 修改器：控制器上方那格放通风机箱 → 物品产出 ×2（D 项）
    .modifier(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=vent]',
              { target: 'item', io: 'output', operation: 1, multiplier: 2.0 })

    // --- 接口类型：C 项用 'mode' 当闸门；'temp' 只是陪衬
    .smartInterface('mode', 0, 1000)
    .smartInterface('temp', 20, 0, { header: 'gui.example.temp' })

    .requiresBlueprint(false)

    // --- 结构：上方通风机箱（也可放石头做对照实验，见 E2）
    .part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=vent]',
                    'minecraft:stone')
    // --- 下层十字：每一格都接受石头或三种仓口
    .parts([-2, -1, 1, 2], [-1], [0],
           'minecraft:stone',
           'modular_machinery_reborn:item_input_hatch[size=normal]',
           'modular_machinery_reborn:item_output_hatch[size=normal]',
           'modular_machinery_reborn:energy_input_hatch[size=normal]')
    .parts([0], [-1], [-2, -1, 1, 2],
           'minecraft:stone',
           'modular_machinery_reborn:item_input_hatch[size=normal]',
           'modular_machinery_reborn:item_output_hatch[size=normal]',
           'modular_machinery_reborn:energy_input_hatch[size=normal]')
    .register()
})

ServerEvents.recipes(event => {

  // 基础配方：1 石头 → 1 钻石（带 modifier 时变 2），无接口闸门。A / B / D 用它。
  event.recipes.modular_machinery_reborn.machine({
    machine: 'v2b_phase2',
    registryName: 'v2b_p2_basic',
    recipeTime: 60,
    requirements: [
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 20 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'input', item: 'minecraft:stone', amount: 1 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'output', item: 'minecraft:diamond', amount: 1 }
    ]
  })

  // 能源配方：每 tick 200，总 12000 FE。normal 能源仓只有 8192 → **一定在途中耗尽**，
  // 于是 failure-action 一定会被触发（B 项用它，省得等能源自然见底）。
  event.recipes.modular_machinery_reborn.machine({
    machine: 'v2b_phase2',
    registryName: 'v2b_p2_energy',
    recipeTime: 60,
    requirements: [
      { type: 'modular_machinery_reborn:energy', 'io-type': 'input', energyPerTick: 200 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'input', item: 'minecraft:stone', amount: 1 },
      { type: 'modular_machinery_reborn:item', 'io-type': 'output', item: 'minecraft:diamond', amount: 1 }
    ]
  })

  // 接口闸门配方：mode 必须落在 [0,10] 才开工。C 项用它。
  event.recipes.modular_machinery_reborn.machine({
    machine: 'v2b_phase2',
    registryName: 'v2b_p2_gated',
    recipeTime: 40,
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
//  A. 并行是不是真的结算 N 份
//
//     搭好：①②③④ 全放石头 → 机器成型（无加工）。
//     把 ① 换成 item_input_hatch[size=normal]、② 换成 energy_input_hatch[size=normal]、
//     ③ 换成 item_output_hatch[size=normal]。
//     ① 里放 3 块石头；② 里用「能源输入仓界面」充电到 ≥1200 FE（创造模式直接拿 FE 充源最快）。
//
//     看控制器界面（右键 C）：
//       - 跑起来后应出现一行「并行数：3」   ← internalParallelism(3)
//       - 另一行「最大并行数：64」          ← maxParallelism(64)
//       （这两行**只在有一条配方真的在跑、且并行 >1 时**才画，所以「没看到」先确认配方在跑。）
//     产出：③ 里应一次得到 **3 个钻石**（1 石头 ×3 份，再 ×2 由 modifier 翻倍 → 见 D，注意叠加）
//       ⚠️ 这里 modifier ×2 与并行 ×3 是**叠加**的：3 份 × 2 = 6。若你只想单独验并行，
//          把 .modifier(...) 那一行注释掉再 /reload（结构上方那格仍需是通风机箱，因为 .part 也接受它）。
//
//     改 .internalParallelism(5) → /reload → 再跑一次：数字应变成 5，且一次吃 5 块石头。
//     若它始终是 1 → 字段没进定义。
//
//  ------------------------------------------------------------------------------------------
//  B. failure-action 是不是真的起作用
//
//     用上面同一套仓口，但 ① 里放石头、② 充满 FE 后，改用**能源配方**（v2b_p2_energy，
//     每 tick 200、总额 12000 > 一仓 8192）——它一定在途中耗尽。
//     更方便的做法：把 ② 换成 energy_input_hatch[size=tiny]（容量 2048），
//     配方会很快开始、很快断电，于是你只需要盯进度条。
//
//     观察控制器界面的进度：
//       .failureAction('decrease') → 进度**每失败一 tick 退一格**（本条默认值）
//       改成 'still'    → 进度**冻住**不动
//       改成 'reset'    → 进度**清零**
//                         ⚠️ 本项目里 reset 会**重走开工、再扣一次一次性输入**（唯一有意偏离旧版处，见 D17）
//     三个值各试一次，每次改完 /reload。**这是清单第 4 项。**
//
//  ------------------------------------------------------------------------------------------
//  C. smart-interfaces 是不是真的当闸门
//
//     把配方换成 v2b_p2_gated（把上面 A 用的输入/输出/能源仓留着即可，因为 coreThread 钉了三条，
//     机器会自己挑可跑的那条：① 里有石头、② 有电时，gated 与 basic 都满足；想看闸门效果，
//     最简单的办法是**临时把 basic 与 energy 两条配方注释掉**再 /reload）。
//
//     控制器界面会多出一个 **mode 输入框**（因为机器声明了 mode 这个接口类型）：
//       - 填 5  → 配方开工，约 40 tick 后出钻石
//       - 填 50 → **拒绝开工**，界面给出「智能数据接口输入的数值不同！」一类提示
//     「填 50 被拒」就是闸门生效的证据。若界面根本没有那个输入框 → smart-interfaces 没进定义。
//
//  ------------------------------------------------------------------------------------------
//  D. modifier 是不是真的改了产出（**最容易误判的一条**）
//
//     用 basic 配方，① 放 1 块石头，② 充满电，③ 是输出仓：
//       - y+1 是**通风机箱** → 成型，产出 **2 个钻石**（output ×2.0）
//       - 把 y+1 换成 air      → **不成型**（说明它确实是结构件，不是普通方块）
//
//     ⚠️ 必看的坑：把 .modifier 的坐标改成 (0, 0, 0)（控制器自己那格）
//        → **机器照常成型，但修改器无效**（产出仍是 1），日志里只有一行告警。
//        这条最容易误判成「0.31.0 没生效」——它不是 bug，是旧版行为（控制器自己那格会被跳过）。
//
//  ------------------------------------------------------------------------------------------
//  E. 校验仍然在 schema，没有被搬进构建器（改脚本故意写错，看报错）
//
//     E1. 把 .maxThreads(7) 改成 .maxThreads(-1) → /reload
//         应报：'max-threads' of machine 'v2b_phase2' is -1, but a thread count cannot be negative. …
//     E2. 写两条同名核心线程：.coreThread('same').coreThread('same') → /reload
//         应报：Two core threads of machine 'v2b_phase2' are both named 'same'. …
//     E3. .maxParallelism(2) 配 .internalParallelism(5) → /reload
//         应报：'internal-parallelism' … is 5, which is above its 'max-parallelism' (2). …
//     E4. .failureAction('reset!') → /reload
//         应报：'failure-action' of machine 'v2b_phase2' must be one of "reset", "still" or "decrease"; …
//
//     ★ 关键判据：把**同样的错**写成 JSON 放进数据包（或配置目录），报的**句子应逐字相同**
//       （两条路径共用同一份 MachineSchema）。若两边句子不一样 → 有人把校验搬进了构建器，那是要修的缺陷。
// ============================================================================================
