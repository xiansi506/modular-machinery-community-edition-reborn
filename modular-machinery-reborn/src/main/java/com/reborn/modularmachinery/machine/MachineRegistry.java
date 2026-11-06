package com.reborn.modularmachinery.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded machine definitions, replacing the hardcoded 3×3 iron ring and the hardcoded {@code "basic"}
 * machine name.
 *
 * <p>Patterns are pre-rotated for all four horizontal controller facings at load time. The controller's
 * {@code FACING} selects which rotation is tested, mirroring the original
 * {@code TileMultiblockMachineController} which rotated the pattern counter-clockwise until it matched the
 * controller's facing. Testing every rotation instead would let a machine form in an orientation that
 * disagrees with its controller.
 */
public final class MachineRegistry {

    private static volatile Map<ResourceLocation, MachineDefinition> machines = Map.of();
    private static volatile Map<ResourceLocation, MachinePattern[]> rotations = Map.of();
    private record ClientView(Map<ResourceLocation, MachineDefinition> machines,
                              Map<ResourceLocation, MachinePattern[]> rotations) {}
    private static volatile ClientView client = new ClientView(Map.of(), Map.of());

    /**
     * 波 4：每个定义**解析用的那份 JSON**，按 id 存放。
     *
     * <p>它是「同步到客户端」这份工作的载体，而选它当载体有一个明确的理由：
     * 客户端拿到 JSON 之后调**同一个** {@link MachineSchema#read} 重建定义，于是两边的定义逐字段相同，
     * 连所有校验规则、报错文案、以及「某字段省略时的默认值」都必然一致。
     *
     * <p>另一条路是给 {@code MachineDefinition} 的十五个字段各写一套线上格式。那条路要额外处理
     * {@code BlockMatcher}（它持有**活的 {@code Block} 引用**）、{@code MachineModifier}、
     * {@code SmartInterfaceType}、{@code CoreThreadSpec} 和 {@code DynamicPattern}——**五套新格式**，
     * 而每一套都是一份迟早会与解析器分叉的副本。分叉的表现是「单机能跑、联机不行」，
     * 那正是本项目最怕的一类问题。所以这里留的是**输入**，不是**输出的复制品**。
     *
     * <p>空 map 是合法状态（例如客户端断线后），不是错误。
     */
    private static volatile Map<ResourceLocation, com.google.gson.JsonElement> sources = Map.of();

    private MachineRegistry() {
    }

    /** Logical-client data never replaces the integrated server's definitions or source JSON. */
    public static void replaceClient(Map<ResourceLocation, MachineDefinition> loaded) {
        Map<ResourceLocation, MachineDefinition> copy = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        Map<ResourceLocation, MachinePattern[]> variants = new LinkedHashMap<>();
        for (var entry : copy.entrySet()) {
            MachinePattern[] patterns = new MachinePattern[4];
            patterns[0] = entry.getValue().pattern();
            for (int i = 1; i < 4; i++) patterns[i] = patterns[i - 1].rotateYCounterClockwise();
            variants.put(entry.getKey(), patterns);
        }
        client = new ClientView(copy, Collections.unmodifiableMap(variants));
    }

    public static void clearClient() { client = new ClientView(Map.of(), Map.of()); }
    public static Collection<MachineDefinition> clientAll() { return client.machines().values(); }
    public static Optional<MachineDefinition> clientById(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(client.machines().get(id));
    }

    public static Optional<MachineDefinition> byId(@Nullable ResourceLocation id, boolean clientSide) {
        return clientSide ? clientById(id) : byId(id);
    }

    /** Common shape queries have a world when possible; chunk views use the calling logical thread. */
    public static Optional<MachineDefinition> byId(@Nullable ResourceLocation id, BlockGetter world) {
        return byId(id, world instanceof Level level ? level.isClientSide : EffectiveSide.get().isClient());
    }

    public static Optional<MachineDefinition> presentationById(@Nullable ResourceLocation id) {
        return byId(id, EffectiveSide.get().isClient());
    }

    /** Replaces the registry contents; called by {@link MachineLoader} on every resource reload. */
    public static void replace(Map<ResourceLocation, MachineDefinition> loaded) {
        replace(loaded, Map.of());
    }

    /** 只写服务端权威表并保留同步所需的源 JSON；客户端使用 replaceClient。 */
    public static void replace(Map<ResourceLocation, MachineDefinition> loaded,
                               Map<ResourceLocation, com.google.gson.JsonElement> loadedSources) {
        publish(prepare(loaded, loadedSources));
    }

    /** A complete candidate, including all rotations, is built before any live field changes. */
    public record Prepared(Map<ResourceLocation, MachineDefinition> machines,
                           Map<ResourceLocation, MachinePattern[]> rotations,
                           Map<ResourceLocation, com.google.gson.JsonElement> sources) {}

    public static Prepared prepare(Map<ResourceLocation, MachineDefinition> loaded,
                                   Map<ResourceLocation, com.google.gson.JsonElement> loadedSources) {
        // Insertion order is preserved so that, when two definitions could match the same structure, the
        // outcome does not depend on hash iteration order. Map.copyOf would not guarantee that.
        Map<ResourceLocation, MachineDefinition> copy = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        Map<ResourceLocation, MachinePattern[]> rotated = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineDefinition> entry : copy.entrySet()) {
            MachinePattern base = entry.getValue().pattern();
            MachinePattern[] variants = new MachinePattern[4];
            variants[0] = base;
            for (int i = 1; i < 4; i++) {
                variants[i] = variants[i - 1].rotateYCounterClockwise();
            }
            rotated.put(entry.getKey(), variants);
        }
        // 只保留仍然存在的定义的源 JSON：一次 reload 可能删掉了某些机器，而留着它们的 JSON
        // 会让下一次同步把已经删掉的机器又发给客户端。
        Map<ResourceLocation, com.google.gson.JsonElement> kept = new LinkedHashMap<>();
        for (ResourceLocation id : copy.keySet()) {
            com.google.gson.JsonElement source = loadedSources.get(id);
            if (source != null) {
                kept.put(id, source.deepCopy());
            }
        }
        return new Prepared(copy, Collections.unmodifiableMap(rotated), Collections.unmodifiableMap(kept));
    }

    /** ReloadCycle calls this on the apply executor only after every listener succeeded. */
    public static void publish(Prepared prepared) {
        rotations = prepared.rotations();
        sources = prepared.sources();
        machines = prepared.machines();
    }

    /**
     * 每个定义解析用的源 JSON，按定义 id；只有服务端有内容（客户端重建之后它是空的）。
     *
     * <p>同步用的就是这个方法，而不是再去遍历文件——{@code reload} 之后注册表里就是**权威**的一份，
     * 而文件系统可能已经变了。
     */
    public static Map<ResourceLocation, com.google.gson.JsonElement> sources() {
        return sources;
    }

    public static Collection<MachineDefinition> all() {
        return machines.values();
    }

    public static int size() {
        return machines.size();
    }

    public static Optional<MachineDefinition> byId(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id));
    }

    public static boolean contains(@Nullable ResourceLocation id) {
        return id != null && machines.containsKey(id);
    }

    /**
     * Counter-clockwise quarter turns from {@code NORTH} to {@code facing}: north 0, west 1, south 2, east 3.
     */
    public static int rotationStepsFor(Direction facing) {
        Direction direction = Direction.NORTH;
        int steps = 0;
        while (direction != facing && steps < 4) {
            direction = direction.getCounterClockWise();
            steps++;
        }
        return steps % 4;
    }

    /**
     * Finds the first registered machine whose pattern matches the world for the controller's facing.
     *
     * @return the match, or {@code null} when no machine is formed here
     */
    @Nullable
    public static Match findMatch(Level level, BlockPos controller, Direction facing) {
        return findMatch(level, controller, facing, null, null);
    }

    /**
     * Finds a match using the original {@code checkStructure} search order:
     *
     * <ol>
     *   <li><b>The blueprint's machine is tried first.</b> A blueprint in the controller's slot is an explicit
     *       statement of which machine this controller is for, so it outranks everything else — including a
     *       bound controller's own machine, which is what the original did when it checked
     *       {@code getBlueprintMachine()} before {@code parentMachine}.</li>
     *   <li><b>A machine-bound controller then checks its own machine</b>, and never anything else. If that
     *       machine requires a blueprint and the slotted one is not it, nothing forms — the original returned
     *       early here rather than falling through.</li>
     *   <li><b>Otherwise every definition is tried except those requiring a blueprint.</b> The original's
     *       {@code checkAllPatterns} skips them, and that skip is the whole point of {@code requires-blueprint}:
     *       such a machine can only ever form through its blueprint.</li>
     * </ol>
     *
     * @param blueprintMachine the machine named by the blueprint in the controller's slot, or {@code null}
     * @param boundMachine     the machine a per-machine controller is bound to, or {@code null} when generic
     */
    @Nullable
    public static Match findMatch(Level level, BlockPos controller, Direction facing,
                                  @Nullable ResourceLocation blueprintMachine,
                                  @Nullable ResourceLocation boundMachine) {
        return inspectMatch(level, controller, facing, blueprintMachine, boundMachine).match();
    }

    public record Inspection(@Nullable Match match, boolean unloaded) {
        private static final Inspection NO_MATCH = new Inspection(null, false);
    }

    /** A failure reason belongs to this actual attempt, never a second unrotated or maximum-size scan. */
    public static Inspection inspectMatch(Level level, BlockPos controller, Direction facing,
                                          @Nullable ResourceLocation blueprintMachine,
                                          @Nullable ResourceLocation boundMachine) {
        int steps = rotationStepsFor(facing);
        Map<ResourceLocation, MachineDefinition> definitions = level.isClientSide ? client.machines() : machines;
        Map<ResourceLocation, MachinePattern[]> oriented = level.isClientSide ? client.rotations() : rotations;

        Inspection fromBlueprint = Inspection.NO_MATCH;
        if (blueprintMachine != null) {
            fromBlueprint = tryMachine(level, controller, steps, blueprintMachine);
            if (fromBlueprint.match() != null) {
                return fromBlueprint;
            }
        }

        if (boundMachine != null) {
            MachineDefinition bound = definitions.get(boundMachine);
            if (bound != null && bound.requiresBlueprint() && !boundMachine.equals(blueprintMachine)) {
                return Inspection.NO_MATCH;
            }
            return tryMachine(level, controller, steps, boundMachine);
        }

        for (Map.Entry<ResourceLocation, MachinePattern[]> entry : oriented.entrySet()) {
            MachineDefinition machine = definitions.get(entry.getKey());
            if (machine == null || machine.requiresBlueprint()) {
                continue;
            }
            Inspection match = matchMachine(machine, entry.getValue()[steps], level, controller, steps);
            if (match.match() != null) {
                return match;
            }
        }
        // A generic all-pattern search has no single required layout to diagnose. An explicit blueprint does.
        return fromBlueprint;
    }

    @Nullable
    private static Inspection tryMachine(Level level, BlockPos controller, int steps, ResourceLocation id) {
        MachinePattern[] variants = (level.isClientSide ? client.rotations() : rotations).get(id);
        MachineDefinition machine = byId(id, level.isClientSide).orElse(null);
        if (variants == null || machine == null) {
            return Inspection.NO_MATCH;
        }
        return matchMachine(machine, variants[steps], level, controller, steps);
    }

    /**
     * 基座匹配之后再量动态结构，最后给出这台机器的<strong>有效结构</strong>。
     *
     * <p>顺序照抄旧版 {@code TileMultiblockMachineController#checkStructure}：先判基座
     * （{@code pattern.matches(...)}），基座不成立就谈不上动态段；基座成立后
     * {@code matchesDynamicPatternRotation} 逐个量每个动态模式，<strong>任意一个不成立则整台机器不成型</strong>。
     *
     * <p>量出来的段数与方向会<strong>定格</strong>在返回的 {@link Match} 里并随方块实体存进 NBT。
     * 这是必须的：配方的 {@code selector-tag} 里带着段号，而结构长度在世界上是会变的——
     * 每一 tick 重新量一次会让玩家加长一节就把正在跑的合成指到别的段上。
     *
     * @param basePattern 已经按控制器朝向旋转过的基座结构
     */
    @Nullable
    private static Inspection matchMachine(MachineDefinition machine, MachinePattern basePattern,
                                      Level level, BlockPos controller, int steps) {
        // Keep the same alternatives in the initial gate, final dynamic check and returned port structure.
        basePattern = basePattern.withModifierAlternatives(machine.modifiers(), steps);
        MachinePattern.Inspection base = basePattern.inspect(level, controller);
        if (!base.matched()) {
            return new Inspection(null, base.unloaded());
        }
        if (!machine.hasDynamicPatterns()) {
            // 绝大多数机器走这条路：结构固定，有效结构**就是**基座，不做任何合并。
            return new Inspection(new Match(machine, steps, basePattern, List.of()), false);
        }

        MachinePattern effective = basePattern;
        List<DynamicPattern.Status> statuses = new ArrayList<>(machine.dynamicPatterns().size());
        for (DynamicPattern dynamic : machine.dynamicPatterns()) {
            DynamicPattern.Inspection inspected = dynamic.inspect(level, controller, steps);
            DynamicPattern.MatchResult result = inspected.match();
            if (!result.isMatched()) {
                // 一个动态段不成立，整台机器就不成型——旧版在这里直接 return false。
                return new Inspection(null, inspected.unloaded());
            }
            statuses.add(new DynamicPattern.Status(dynamic.name(), result.size(), result.face()));
            effective = effective.withMerged(dynamic.expand(result.size(), steps));
        }
        // 合并之后再整体核对一次。基座与每个动态段各自都验过了，所以这一步在正常情况下必定通过；
        // 它的价值在于覆盖「两个部分在同一格上要求不同方块」这种只在合并后才看得出的冲突——
        // 那正是取并集语义存在的理由，而这里是把「合起来到底成不成」这件事真正问一次。
        MachinePattern.Inspection merged = effective.inspect(level, controller);
        if (!merged.matched()) {
            return new Inspection(null, merged.unloaded());
        }
        return new Inspection(new Match(machine, steps, effective, List.copyOf(statuses)), false);
    }

    /**
     * A formed machine plus the rotation that matched it.
     *
     * @param machine      成型的是哪台机器
     * @param rotationSteps 相对 NORTH 的逆时针四分之一圈数
     * @param pattern      这台机器<strong>在世界里的有效结构</strong>：没有动态结构时就是基座本身，
     *                     有动态结构时是基座与量出来的各段合并后的结果。端口收集、建造预览与结构诊断读的是它，
     *                     而不是 {@code machine.pattern()}——后者只是基座，对动态机器来说是不完整的。
     * @param dynamicPatterns 每个动态模式定格下来的节数与方向；没有动态结构时是空列表
     */
    public record Match(MachineDefinition machine, int rotationSteps, MachinePattern pattern,
                        List<DynamicPattern.Status> dynamicPatterns) {

        /** 这台机器是否有动态段（也就是 {@link #pattern} 是否与基座不同）。 */
        public boolean hasDynamicPatterns() {
            return !this.dynamicPatterns.isEmpty();
        }
    }
}
