package com.reborn.modularmachinery.command;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachinePattern;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRequirement;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import com.reborn.modularmachinery.recipe.RecipeOrder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * {@code /mm-diagnose} —— 把脚下（或最近）的那台控制器**实际看到的东西**打印出来。
 *
 * <h2>为什么需要这条命令</h2>
 *
 * <p>{@code selector-tag} 是<strong>看不见</strong>的：结构成型之后，世界里只有一堆长得一样的仓口方块，
 * 没有任何东西告诉玩家某个仓叫 {@code input_line_0} 还是 {@code input_line_1}——而配方的成败完全取决于
 * 这个看不见的名字。实测时正好卡在这里：一台<strong>成型且配方已加载</strong>的机器不开工，
 * 而唯一的线索是状态行上一句「没有相匹配的配方」，它同时覆盖了好几种完全不同的原因：
 *
 * <ul>
 *   <li>这台机器**没有**任何配方（定义里的 {@code registryname} 与配方的 {@code machine} 对不上）；</li>
 *   <li>有配方，但**每条都开工不了**（料放错了段、输出仓没地方、标签拼错）。</li>
 * </ul>
 *
 * <p>这条命令把这两种情况分开：它列出结构里<strong>每个位置</strong>的标签、控制器实际收集到的
 * <strong>每个端口</strong>及其标签，然后<strong>逐条</strong>列出这台机器的配方各自能不能开工、以及不能时
 * 缺什么。看一次就能定位，不必再数坐标。
 *
 * <p>这也是本项目对「日志里没有报错不提供任何信息」那条教训的同一套应对：一个看不见的状态，
 * 就必须有办法把它<strong>问出来</strong>。
 */
public final class DiagnoseCommand {

    private DiagnoseCommand() {
    }

    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("mm-diagnose")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource())));
    }

    private static int run(CommandSourceStack source) {
        // F6 第二片（0.87.1）：界面文案全部走翻译键（command.modular_machinery_reborn.diagnose.*）；
        // 纯数据行（坐标、端口描述、能力名）保留 literal。键名与 %s 个数由脚本静态核对过。
        final String d = "command.modular_machinery_reborn.diagnose.";
        Level level = source.getLevel();
        MachineControllerBlockEntity controller = findController(source, level);
        if (controller == null) {
            source.sendFailure(Component.translatable(d + "no_controller"));
            return 0;
        }

        BlockPos pos = controller.getBlockPos();
        source.sendSuccess(() -> Component.translatable(d + "header", pos.toShortString()), false);
        source.sendSuccess(() -> Component.translatable(d + "formed_status", controller.isFormed(),
                controller.status().name(), controller.status().translationKey()), false);
        if (!controller.isFormed()) {
            source.sendSuccess(() -> Component.translatable(d + "not_formed_note"), false);
            return 1;
        }

        var machineId = controller.machineId();
        MachineDefinition machine = machineId == null ? null : MachineRegistry.byId(machineId).orElse(null);
        source.sendSuccess(() -> Component.translatable(d + "machine_line", machineId,
                machine == null ? Component.translatable(d + "machine_unknown_suffix") : Component.empty()),
                false);
        if (machine == null) {
            return 1;
        }

        // ---- 结构里的标签（定义展开之后的，也就是运行时真正用的那一份）--------------------------
        source.sendSuccess(() -> Component.translatable(d + "section_tags"), false);
        MachinePattern pattern = machine.pattern();
        if (!machine.hasDynamicPatterns()) {
            source.sendSuccess(() -> Component.translatable(d + "no_dynamic"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(d + "dynamic_count",
                    controller.dynamicPatterns().size()), false);
            for (var status : controller.dynamicPatterns()) {
                source.sendSuccess(() -> Component.translatable(d + "dynamic_pattern",
                        status.patternName(), status.size(), status.face().getName()), false);
            }
        }
        if (!pattern.hasTags() && !machine.hasDynamicPatterns()) {
            source.sendSuccess(() -> Component.translatable(d + "no_tags"), false);
        }
        for (Map.Entry<BlockPos, String> entry : new java.util.TreeMap<>(runtimeTags(controller, machine)).entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey().toShortString()
                    + " -> " + entry.getValue()), false);
        }

        // ---- 实际收集到的端口及其标签 ---------------------------------------------------------
        HatchCollection hatches = controller.hatches();
        source.sendSuccess(() -> Component.translatable(d + "section_ports", hatches.hatchCount()), false);
        for (String line : hatches.describePorts()) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }

        // ---- 输入组（A2）---------------------------------------------------------------------
        // 组号是另一个看不见的状态：玩家把仓口分到几组、正在跑的是哪一组，只看世界看不出来。
        source.sendSuccess(() -> Component.translatable(d + "section_groups"), false);
        final Component groupSummary = Component.translatable(d + "group_summary", hatches.groupIds(),
                controller.activeGroupId() == null ? "-" : controller.activeGroupId());
        source.sendSuccess(() -> groupSummary, false);

        // ---- 结构展开后**带标签**的每一格，以及那一格里实际是什么 --------------------------------
        //
        // 这一段是「0 个端口」时的定位手段：把**相对坐标、世界坐标、标签、实际方块**并排打出来。
        // 仓口没被收集到只有两种可能——要么它们不在结构认为的位置上（这里一眼可见），
        // 要么它们在、但那一格不是本模组的仓口方块（这里会显示成别的东西）。
        Map<BlockPos, String> tags = runtimeTags(controller, machine);
        Map<BlockPos, ?> positions = runtimePositions(controller, machine);
        source.sendSuccess(() -> Component.translatable(d + "section_tagged_positions"), false);
        for (Map.Entry<BlockPos, String> entry : new java.util.TreeMap<>(tags).entrySet()) {
            BlockPos at = pos.offset(entry.getKey());
            String block = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock()));
            boolean isHatch = level.getBlockEntity(at)
                    instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity;
            source.sendSuccess(() -> Component.translatable(d + "tagged_position",
                    entry.getKey().toShortString(), at.toShortString(), entry.getValue(), block,
                    isHatch ? Component.translatable(d + "hatch_marker") : Component.empty()), false);
        }
        if (tags.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(d + "no_tagged_positions"), false);
        }

        // ---- 不带标签的结构位置，好确认「带标签的格子」到底落在世界里的哪儿 -----------------------
        source.sendSuccess(() -> Component.translatable(d + "section_plain_positions"), false);
        int plainShown = 0;
        for (BlockPos relative : new java.util.TreeMap<>(
                runtimePositions(controller, machine)).keySet()) {
            if (tags.containsKey(relative)) {
                continue;
            }
            if (plainShown++ >= 24) {
                break;
            }
            BlockPos at = pos.offset(relative);
            String block = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock()));
            source.sendSuccess(() -> Component.translatable(d + "plain_position",
                    relative.toShortString(), at.toShortString(), block), false);
        }

        // ---- 直接扫描世界，找出**真实存在**的仓口，不管结构怎么算 --------------------------------
        //
        // 这一段是这套诊断里最可靠的一块：它不依赖任何位置推算。成型之后仓口必然在控制器附近
        // （结构最多十几格），所以直接把范围内所有 `MachineHatchBlockEntity` 找出来，
        // 用它自己的位置去和结构的位置对表——如果结构与世界对不上，这里会立刻显示成
        // 「某个仓口不在结构的任何位置上」。
        source.sendSuccess(() -> Component.translatable(d + "section_world_hatches"), false);
        int foundHatches = 0;
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -16; dy <= 16; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(at)
                            instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch)) {
                        continue;
                    }
                    foundHatches++;
                    BlockPos relative = at.subtract(pos);
                    String tag = tags.get(relative);
                    boolean inStructure = tag != null || positions.containsKey(relative);
                    source.sendSuccess(() -> Component.translatable(d + "world_hatch",
                            relative.toShortString(), at.toShortString(), hatch.kind(),
                            tag == null ? "-" : tag, hatch.groupDescription(),
                            inStructure ? Component.empty()
                                    : Component.translatable(d + "not_in_structure"),
                            hatch.autoIoDescription()), false);
                }
            }
        }
        final int hatchTotal = foundHatches;
        source.sendSuccess(() -> Component.translatable(d + "hatch_scan_summary", hatchTotal,
                hatches.hatchCount()), false);
        if (foundHatches > 0 && hatches.hatchCount() == 0) {
            source.sendSuccess(() -> Component.translatable(d + "hatch_scan_warning"), false);
        }

        // ---- 兼容端口（波 5.3：Mekanism 的气体）-------------------------------------------------
        // 为什么单列一节：气体端口是**看不见**的——流体仓的界面只画流体，所以「气体进没进去」
        // 在游戏里没有任何显示。而这条路上有四个分岔（模块没注册 / 能力对象没拿到 / 管道没问 /
        // 问了但被拒），它们在日志里长得一模一样。这一节把四个都变成可读的一行。
        // 波 6：染色。分三段问，因为「没变色」有几种完全不同的原因：
        // 定义没解析出颜色 / 控制器没拿到 / 拿到的没同步给客户端 / 渲染层没 tint 层。
        // 解析、下发与邻近部件状态在这里可查；客户端最终渲染仍需实机确认。
        source.sendSuccess(() -> Component.translatable(d + "section_color"), false);
        {
            final Component definitionColor = controller.machineId() == null
                    ? Component.translatable(d + "color_missing")
                    : com.reborn.modularmachinery.machine.MachineRegistry.byId(controller.machineId())
                            .map(definition -> Component.literal(
                                    String.format("#%06X", definition.color())))
                            .orElseGet(() -> Component.translatable(d + "color_missing"));
            source.sendSuccess(() -> Component.translatable(d + "color_definition", definitionColor),
                    false);
            final Component controllerColor = controller.machineColor() == null
                    ? Component.translatable(d + "color_not_distributed")
                    : Component.literal(String.format("#%06X", controller.machineColor()));
            source.sendSuccess(() -> Component.translatable(d + "color_controller", controllerColor),
                    false);
            int hatchesWithColor = 0;
            int hatchesSeen = 0;
            int casingsWithColor = 0;
            int casingsSeen = 0;
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = -16; dy <= 16; dy++) {
                    for (int dz = -16; dz <= 16; dz++) {
                        var nearby = level.getBlockEntity(pos.offset(dx, dy, dz));
                        if (nearby instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch) {
                            hatchesSeen++;
                            if (hatch.machineColor() != null) {
                                hatchesWithColor++;
                            }
                        } else if (nearby instanceof com.reborn.modularmachinery.block.MachineCasingBlockEntity casing) {
                            casingsSeen++;
                            if (casing.machineColor() != null) {
                                casingsWithColor++;
                            }
                        }
                    }
                }
            }
            final int seen = hatchesSeen;
            final int colored = hatchesWithColor;
            source.sendSuccess(() -> Component.translatable(d + "color_hatches", colored, seen), false);
            final int coloredCasings = casingsWithColor;
            final int seenCasings = casingsSeen;
            source.sendSuccess(() -> Component.translatable(d + "color_casings", coloredCasings,
                    seenCasings), false);
        }

        source.sendSuccess(() -> Component.translatable(d + "section_compat"), false);
        source.sendSuccess(() -> Component.translatable(d + "mapper_count",
                com.reborn.modularmachinery.compat.ModCompat.portMapperCount()), false);
        source.sendSuccess(() -> Component.translatable(d + "gas_ready",
                com.reborn.modularmachinery.compat.mekanism.MekanismModule.gasPortReady()), false);

        // ---- 流体能力与 Jade ---------------------------------------------------------------
        // Jade 显示流体走的是「向方块实体要 ForgeCapabilities.FLUID_HANDLER，然后逐罐读
        // getTankCapacity/getFluidInTank」这一条**标准**路（由 Jade 的 CommonProxy#wrapFluidStorage →
        // JadeForgeUtils#fromFluidHandler 实现，本模组不参与）。所以「Jade 不显示液体」只有两种可能：
        // 方块实体没交出那个能力，或者交出来了但罐是空的。这一节把两者分开。
        source.sendSuccess(() -> Component.translatable(d + "section_fluid"), false);
        // 闸门计数放在最前面：它回答「有没有发生过『罐里真有液体、却被闸门读成 0』」。
        // 这是「游戏内界面看得见液体、Jade 只显示 0」这类现象的唯一可能来源，而它发生在一瞬，
        // 只能靠计数事后问出来。
        source.sendSuccess(() -> Component.literal("  "
                + com.reborn.modularmachinery.block.MachineHatchBlockEntity.gateCounters()), false);
        // Jade 那个替换器的可观测性：它已失败过两次，而两次在游戏里表现一样（提示毫无变化）——
        // 「回调没被调用」与「调用了但没定位到」要的修法完全不同。
        source.sendSuccess(() -> Component.translatable(d + "jade_replacer",
                com.reborn.modularmachinery.integration.jade.FluidStorageSuppressor.counters()), false);
        source.sendSuccess(() -> Component.translatable(d + "jade_own_bar",
                com.reborn.modularmachinery.integration.jade.HatchFluidStorageProvider.diagnostics()),
                false);
        // 气体读盘：所有者报过「上一会话的 brine 在新会话里是空的」。那一行让它可判定。
        source.sendSuccess(() -> Component.translatable(d + "gas_restore",
                com.reborn.modularmachinery.compat.mekanism.MekanismModule.gasRestoreCounters()), false);
        // **完整模拟 Jade 的服务端流程**：Jade 的 CommonProxy#wrapFluidStorage 做的是
        //   目标 instanceof CapabilityProvider（方块实体都是）
        //   → provider.getCapability(ForgeCapabilities.FLUID_HANDLER)   ← 注意是**无方向**的那次调用
        //   → 拿不到就 null；拿到就逐罐 getTankCapacity / getFluidInTank
        // 这一行把这条链**原样跑一遍**（走父类 CapabilityProvider，与本类重写的那次调用区分开），
        // 于是「Jade 会看到什么」不再是推断，而是这一行的读数。
        //
        // **逐个仓都报**（0.56.0 修正）：上一版只报「扫描到的第一个」流体仓，而所有者看的是另一个，
        // 于是那一行显示「空」、看起来像与界面的 32000 mB 矛盾——一次**探针自己造成的误导**。
        // 探针报错比没有探针更糟，所以这里改为每行都带坐标，并且不再替玩家挑一个。
        source.sendSuccess(() -> Component.translatable(d + "jade_sim_title"), false);
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -16; dy <= 16; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(at)
                            instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch)
                            || hatch.kind().family()
                                    != com.reborn.modularmachinery.block.HatchKind.Family.FLUID) {
                        continue;
                    }
                    var viaProvider = hatch.getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER);
                    Component summary;
                    if (!viaProvider.isPresent()) {
                        summary = Component.translatable(d + "jade_sim_no_handler");
                    } else {
                        var handler = viaProvider.orElse(null);
                        var stack = handler.getFluidInTank(0);
                        Component contents = stack.isEmpty()
                                ? Component.translatable(d + "jade_sim_empty")
                                : Component.literal(stack.getFluid().builtInRegistryHolder().key().location()
                                        + " x" + stack.getAmount() + " mB");
                        summary = Component.translatable(d + "jade_sim_tank", handler.getTanks(),
                                handler.getTankCapacity(0), contents);
                    }
                    source.sendSuccess(() -> Component.translatable(d + "jade_sim_entry",
                            at.toShortString(), summary), false);
                }
            }
        }
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -16; dy <= 16; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(at)
                            instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch)) {
                        continue;
                    }
                    if (hatch.kind().family() != com.reborn.modularmachinery.block.HatchKind.Family.FLUID) {
                        continue;
                    }
                    // 用**空方向**查询，与 Jade 的做法一致（JadeForgeUtils 走 CapabilityProvider 的无方向查询）。
                    //
                    // 同时报**罐的真值**（绕开能力层直接问方块实体自己的罐）与**通过能力读到的值**。
                    // 两者必须一致；不一致就说明「能力层给出的答案」与「罐里真实的东西」不同——
                    // 而那正是「游戏内界面看得见、Jade 看不见」这一类现象的分岔点。
                    var handler = hatch.getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null);
                    // 内容要**手写**成「什么液体、多少」：FluidStack 没有覆写 toString()，
                    // 直接拼对象只会打出 `net.minecraftforge.fluids.FluidStack@2b3b25cc`——
                    // 那一行等于没问（探针说废话比不说更糟）。同理容量也要按 Jade 的判据写出。
                    Component contents = Component.translatable(d + "no_capability");
                    int capacity = -1;
                    if (handler.isPresent()) {
                        var tank = handler.orElse(null);
                        capacity = tank.getTankCapacity(0);
                        net.minecraftforge.fluids.FluidStack inTank = tank.getFluidInTank(0);
                        contents = inTank.isEmpty()
                                ? Component.translatable(d + "empty_tank")
                                : Component.literal(inTank.getFluid().builtInRegistryHolder().key().location()
                                        + " x" + inTank.getAmount() + " mB");
                    }
                    // 罐的真值：直接问方块实体，不经过能力层。
                    net.minecraftforge.fluids.FluidStack raw = hatch.fluidTank() == null
                            ? net.minecraftforge.fluids.FluidStack.EMPTY
                            : hatch.fluidTank().getFluid();
                    Component rawText = raw.isEmpty()
                            ? Component.translatable(d + "empty_tank")
                            : Component.literal(raw.getFluid().builtInRegistryHolder().key().location()
                                    + " x" + raw.getAmount() + " mB");
                    // **关键对比**：本类重写的 getCapability 与父类（Forge 的
                    // CapabilityDispatcher，也就是「别的模组用 AttachCapabilitiesEvent 挂上来的能力」）
                    // 会不会给出**不同的处理器**。如果不同，就说明有第三方能力盖在本模组之上——
                    // 而 Jade 走的正是父类那条路（它按 CapabilityProvider 查），于是它读到别人的空罐。
                    Component dispatcher = Component.translatable(d + "super_no_handler");
                    var viaSuper = hatch.fluidCapabilityViaSuper();
                    if (viaSuper.isPresent()) {
                        var other = viaSuper.orElse(null);
                        var stack = other.getFluidInTank(0);
                        Component superContents = stack.isEmpty()
                                ? Component.translatable(d + "empty_tank")
                                : Component.literal(stack.getFluid().builtInRegistryHolder().key().location()
                                        + " x" + stack.getAmount() + " mB");
                        dispatcher = Component.translatable(d + "super_tank", other.getTanks(),
                                other.getTankCapacity(0), superContents,
                                handler.isPresent() && other == handler.orElse(null));
                    }
                    // contents / dispatcher 在本循环体内被重新赋值，lambda 只能引用实际上的最终变量。
                    final Component contentsFinal = contents;
                    final Component dispatcherFinal = dispatcher;
                    source.sendSuccess(() -> Component.translatable(d + "tank_compare",
                            at.toShortString(), hatch.kind(), rawText, contentsFinal,
                            dispatcherFinal), false);
                }
            }
        }
        source.sendSuccess(() -> Component.translatable(d + "gas_ports_collected",
                hatches.gasInputs().size(), hatches.gasOutputs().size()), false);
        source.sendSuccess(() -> Component.translatable(d + "queried_caps_title"), false);
        var queried = com.reborn.modularmachinery.compat.ModCompat.queriedCapabilitiesNamed();
        if (queried.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(d + "queried_caps_empty"), false);
        } else {
            for (String capability : queried) {
                source.sendSuccess(() -> Component.literal("    " + capability), false);
            }
        }
        source.sendSuccess(() -> Component.translatable(d + "gas_half_title"), false);
        int gasTanks = 0;
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -16; dy <= 16; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(at)
                            instanceof com.reborn.modularmachinery.block.MachineHatchBlockEntity hatch)) {
                        continue;
                    }
                    String gasContents = com.reborn.modularmachinery.compat.mekanism.MekanismModule
                            .describeGasPort(hatch);
                    if (gasContents == null) {
                        continue;
                    }
                    gasTanks++;
                    source.sendSuccess(() -> Component.translatable(d + "gas_half_entry",
                            at.toShortString(), hatch.kind(), gasContents), false);
                    // 插入尝试单独一行：这是「管道到底灌过没有」的唯一直接证据，
                    // 藏在一行长文本的末尾会被读漏。
                    final String tally = com.reborn.modularmachinery.compat.mekanism.MekanismModule
                            .describeGasInserts(hatch);
                    if (tally != null) {
                        source.sendSuccess(() -> Component.translatable(d + "insert_tally", tally),
                                false);
                    }
                }
            }
        }
        final int gasTankTotal = gasTanks;
        if (gasTanks == 0) {
            source.sendSuccess(() -> Component.translatable(d + "gas_none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(d + "gas_total", gasTankTotal), false);
        }

        // ---- 这台机器的配方，逐条说清能不能开工 ------------------------------------------------
        source.sendSuccess(() -> Component.translatable(d + "section_recipes"), false);
        int total = 0;
        int runnable = 0;
        // 和控制器同一顺序：诊断时列出的「第一条失败原因」必须就是控制器实际先试的那条。
        for (MachineRecipe recipe : RecipeOrder.forMachine(
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MACHINE.get()), machine.id())) {
            total++;
            // A2：与控制器同一规则——按组号升序，第一组能 canStart 的就是会被锁住的组。
            // 对整份混合端口问 canStart 会算上「A 组的料 + B 组的料」，那正是隔离要排除的假成功。
            HatchCollection lockedScope = null;
            int lockedGroup = -1;
            boolean lockedCanTick = false;
            String failure = null;
            HatchCollection lastScope = hatches;
            for (int groupId : hatches.groupIds()) {
                HatchCollection scope = hatches.withGroup(groupId);
                lastScope = scope;
                if (recipe.canStart(scope, controller.modifiers())) {
                    lockedScope = scope;
                    lockedGroup = groupId;
                    lockedCanTick = recipe.canTick(scope, controller.modifiers());
                    break;
                }
                failure = recipe.startFailure(scope, controller.modifiers());
            }
            boolean ok = lockedScope != null && lockedCanTick;
            if (ok) {
                runnable++;
            }
            final boolean okFinal = ok;
            final boolean startFinal = lockedScope != null;
            final boolean tickFinal = lockedCanTick;
            final int lockedFinal = lockedGroup;
            final String failureFinal = failure;
            final HatchCollection requirementScope = lockedScope != null ? lockedScope : lastScope;
            source.sendSuccess(() -> Component.translatable(d + "recipe_line", recipe.getId(), okFinal,
                    startFinal ? Component.translatable(d + "recipe_locked_group", lockedFinal)
                            : Component.empty(),
                    okFinal ? Component.empty()
                            : Component.translatable(d + "recipe_fail_detail", startFinal, tickFinal,
                                    failureFinal == null
                                            ? Component.translatable(d + "recipe_no_reason")
                                            : Component.literal(failureFinal))),
                    false);
            // 每条需求单独说：它绑了哪个标签、在那个标签下能不能满足。这是定位「料放错段」最快的一条。
            for (MachineRequirement requirement : recipe.requirements()) {
                String tag = requirement.selectorTag();
                String satisfied = String.valueOf(requirement.canSatisfy(requirement.visiblePorts(requirementScope)));
                source.sendSuccess(() -> Component.translatable(d + "requirement_line",
                        requirement.getClass().getSimpleName(), requirement.ioType(),
                        tag == null ? Component.translatable(d + "no_tag") : Component.literal(tag),
                        satisfied), false);
            }
        }
        if (total == 0) {
            source.sendSuccess(() -> Component.translatable(d + "no_recipes"), false);
        } else {
            final int t = total;
            final int r = runnable;
            source.sendSuccess(() -> Component.translatable(d + "recipe_summary", t, r), false);
        }
        return 1;
    }

    /**
     * 运行时真正生效的位置标签：把基座与量出来的各段合并之后的那一份。
     *
     * <p>直接读 {@code machine.pattern().tags()} 对动态机器是**不完整**的（那只是基座），
     * 而诊断要回答的正是「动态段的标签是什么」，所以这里按控制器定格下来的节数重新展开一次。
     */
    private static Map<BlockPos, String> runtimeTags(MachineControllerBlockEntity controller,
                                                     MachineDefinition machine) {
        return runtimeStructure(controller, machine).tags();
    }

    /** 同上，但要的是位置集合（用于列出不带标签的格子）。 */
    private static Map<BlockPos, ?> runtimePositions(MachineControllerBlockEntity controller,
                                                     MachineDefinition machine) {
        return runtimeStructure(controller, machine).positions();
    }

    /**
     * 重塑这台机器在当前朝向下<strong>实际生效</strong>的结构。
     *
     * <p><b>两条必须遵守的规则，而第一版这里两条都错了：</b>
     *
     * <ol>
     *   <li><b>从定义的基座出发，不要从 {@code machine.pattern()} 出发。</b>注册表里的 {@code MachinePattern}
     *       是**按控制器朝向预先旋转好**的那一份（{@code MachineRegistry.rotations[steps]}）。第一版拿它当起点，
     *       又把动态段按同一个朝向转了一次——旋转被应用了两遍，动态段落到了错的位置上，
     *       于是合并时标签全丢。诊断表与实际不符，比没有诊断更糟：它把排查引向了错误的方向。</li>
     *   <li><b>先把各段在朝向 0 下合并，再整体旋转。</b>否则动态段的局部坐标（朝向 0 下写的）与
     *       基座的坐标（已旋转）不在同一个坐标系里相加。</li>
     * </ol>
     *
     * <p>0.104.0：实现移到 {@code MachineStructures}——投影仪的世界虚影需要同一份组装逻辑，
     * 复制会漂移（诊断说一套、虚影画一套），规则原文也随实现迁了过去。
     */
    private static MachinePattern runtimeStructure(MachineControllerBlockEntity controller,
                                                   MachineDefinition machine) {
        return com.reborn.modularmachinery.machine.MachineStructures.runtimeStructure(controller, machine);
    }

    /**
     * 找到离命令执行者最近的控制器：8 格立方范围内最近者（距离相同按扫描顺序）。
     *
     * <p>P02-08（0.121.37，加载边界子项）：候选位置先经 {@link Level#isLoaded(BlockPos)} 检查——
     * 该查询只读区块加载状态、不会创建区块；未加载位置的 {@code getBlockEntity} 会以
     * {@code create=true} 请求 FULL 区块，把邻近区块拉进内存。扫描范围限定在 8 格立方，
     * 当前改为只枚举现有已加载区块的BE集合，不再固定4913次逐位置lookup，也不建全局索引；
     * 方法注释里的"准星指向"兜底当前并无实现，本批同样不新增。
     */
    @Nullable
    private static MachineControllerBlockEntity findController(CommandSourceStack source, Level level) {
        BlockPos origin = BlockPos.containing(source.getPosition());
        MachineControllerBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return null;
        for (int cx = (origin.getX() - 8) >> 4; cx <= (origin.getX() + 8) >> 4; cx++) {
            for (int cz = (origin.getZ() - 8) >> 4; cz <= (origin.getZ() + 8) >> 4; cz++) {
                var chunk = server.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos at = entity.getBlockPos();
                    if (entity.isRemoved() || entity.getLevel() != level
                            || Math.abs((long) at.getX() - origin.getX()) > 8
                            || Math.abs((long) at.getY() - origin.getY()) > 8
                            || Math.abs((long) at.getZ() - origin.getZ()) > 8) continue;
                    if (entity instanceof MachineControllerBlockEntity controller) {
                        double distance = at.distSqr(origin);
                        if (distance < bestDistance || distance == bestDistance && best != null
                                && scanOrder(at, best.getBlockPos()) < 0) {
                            bestDistance = distance;
                            best = controller;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The former dx/dy/dz loop's deterministic order, independent of chunk/map iteration order. */
    private static int scanOrder(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }
}
