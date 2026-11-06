package com.reborn.modularmachinery.machine;

import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The machines a KubeJS server script defined, and the merge rule that keeps them alive across reloads.
 *
 * <h2>Why a holder rather than a direct push into {@link MachineRegistry}</h2>
 *
 * <p>{@link MachineRegistry#replace} is a whole-table replacement and {@link MachineLoader} calls it on every
 * data-pack reload — that is the machinery that makes {@code /reload} pick up an edited data pack, and it is
 * also why a definition pushed straight from a script would be gone by the next reload. The survey predicted
 * exactly that defect, so the script's definitions are <b>staged here</b> and re-applied by the loader:
 *
 * <pre>
 *   KubeJS server script ──▶ MachineDefinitions.stage(...)     (during script execution)
 *   KubeJSPlugin#onServerReload ──▶ MachineDefinitions.beginCycle()  (scripts loaded, before the loader)
 *   MachineLoader#apply ──▶ data pack + config dir, then MachineDefinitions.applyScriptLayer(...)
 * </pre>
 *
 * <p>The last step re-reads {@link #staged()} rather than trusting what a previous cycle left behind, which is
 * what makes "remove the script, the machine goes away" true without a second cleanup path. Both callers run on
 * the server thread; the fields are nevertheless {@code volatile} and every mutation swaps the whole map, so a
 * reader can never observe a half-built table.
 *
 * <h2>The merge contract, in one place</h2>
 *
 * <ol>
 *   <li><b>Precedence on an id collision</b> (highest first): a KubeJS script, then
 *       {@code config/modular_machinery_reborn/machinery/}, then the data pack. The config directory already
 *       beat the data pack for a {@code registryname} collision (D9 — it is this machine's explicit local
 *       override); a script is the same statement made at runtime by the same person, and it is also the one
 *       whose author can be pointed at a file to delete. Every id a script <b>shadows</b> is logged by name with
 *       both origins, so the override is never silent.</li>
 *   <li><b>Removing a script</b> removes its machines at the next reload: the layer is re-read, and a definition
 *       that was not staged this cycle is simply not merged. Nothing is cached across cycles.</li>
 *   <li><b>Repeated reloads are idempotent.</b> The same scripts staged twice produce the same table, because
 *       staging replaces by id inside the layer and the layer replaces by id inside the merged table.</li>
 * </ol>
 *
 * <h2>What a script-defined machine can never have</h2>
 *
 * <p>No per-machine controller block, and no per-machine factory controller block. Blocks are registered while
 * the mod is being constructed, and a script runs long after the registry is frozen (D8); the same statement is
 * already true of a machine that lives only in a data pack, and {@code MachineLoader} says so in the log for
 * both. Blueprints are <b>not</b> affected: they are minted on demand from whatever {@link MachineRegistry}
 * holds ({@code ModBlocks.TAB} → {@code BlueprintItem.forMachine}), so a script machine gets one for free.
 */
public final class MachineDefinitions {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The script layer as staged this cycle, in staging order. Replaced wholesale, never mutated in place. */
    private static volatile Map<ResourceLocation, MachineDefinition> staged = Map.of();

    /**
     * 波 4：脚本层每个定义**解析用的那份 JSON**，按定义 id。
     *
     * <p>{@code MachineBuilderJS} 本来就是「往一个 {@code JsonObject} 里追加字段、再交给
     * {@link MachineSchema#read}」，所以这份 JSON 是现成的，不需要在解析之后反向生成一份
     * （那是第二份真相，而它与解析器分叉的方式无法察觉）。客户端靠它重建同一批定义。
     */
    private static volatile Map<ResourceLocation, com.google.gson.JsonObject> stagedSources = Map.of();

    /**
     * Whether {@link #beginCycle()} has run at least once. Used only by the startup path: a data-pack reload
     * without KubeJS still calls {@link #applyScriptLayer}, and the answer is an empty layer either way.
     */
    private static volatile boolean cycleStarted = false;

    private MachineDefinitions() {
    }

    /**
     * Stages one definition, replacing any earlier definition of the same id. Called from the KubeJS machine
     * API while a server script runs; also applies the result immediately, so a machine defined by a script is
     * visible even if the caller somehow runs outside a reload.
     */
    public static void stage(MachineDefinition definition) {
        stage(definition, null);
    }

    /**
     * {@link #stage(MachineDefinition)} plus the JSON it was parsed from — 波 4 的同步要靠它。
     *
     * <p>两个参数的版本保留，因为**不是所有调用者都有 JSON**：离线复现器与某些内嵌用法直接构造
     * {@code MachineDefinition}。那种情况下定义本身照旧可用，只是它**无法被同步到客户端**——
     * 而这个事实必须是明确的，不能靠「反向生成一份 JSON」来掩盖：反向生成的东西一旦与
     * {@code MachineSchema} 的规则不一致，客户端拿到的就是一份与服务器不同的定义。
     */
    public static void stage(MachineDefinition definition, @javax.annotation.Nullable com.google.gson.JsonObject source) {
        Map<ResourceLocation, MachineDefinition> next = new LinkedHashMap<>(staged);
        next.put(definition.id(), definition);
        staged = Collections.unmodifiableMap(next);
        if (source != null) {
            Map<ResourceLocation, com.google.gson.JsonObject> sources = new LinkedHashMap<>(stagedSources);
            // Freeze the client sync source at registration time: the caller (typically a KubeJS builder)
            // keeps mutating its live tree, and a shared reference would let post-register edits silently
            // change what the client rebuilds while the server keeps the registered structure.
            sources.put(definition.id(), source.deepCopy());
            stagedSources = Collections.unmodifiableMap(sources);
        }
    }

    /**
     * 脚本层每个定义解析用的 JSON；没有的那几个（直接构造的定义）不在里面。
     *
     * <p>返回的是<strong>快照的深拷贝</strong>：调用方改写拿到的树不会改到注册表里的注册时快照，
     * 快照也不会被读取侧意外污染。
     */
    public static Map<ResourceLocation, com.google.gson.JsonObject> stagedSources() {
        Map<ResourceLocation, com.google.gson.JsonObject> copies = new LinkedHashMap<>();
        stagedSources.forEach((id, source) -> copies.put(id, source.deepCopy()));
        return Collections.unmodifiableMap(copies);
    }

    /**
     * Starts a new script cycle: everything staged so far is dropped.
     *
     * <p>{@code KubeJSPlugin#onServerReload} calls this <b>before</b> it lets the scripts define machines, which
     * is what makes the layer describe the current set of scripts rather than the union of every script ever
     * loaded. It is deliberately not called by {@link #stage} — a caller that stages without starting a cycle
     * (the offline harness, or an add-on) should not wipe what is already there.
     */
    public static void beginCycle() {
        cycleStarted = true;
        staged = Map.of();
        stagedSources = Map.of();
    }

    /** The definitions staged by the current cycle, in staging order. */
    public static Map<ResourceLocation, MachineDefinition> staged() {
        return staged;
    }

    /** How many definitions the current cycle staged. */
    public static int stagedCount() {
        return staged.size();
    }

    /** Whether {@link #beginCycle()} has ever run — i.e. whether a script cycle is in progress. */
    public static boolean cycleStarted() {
        return cycleStarted;
    }

    /**
     * Merges the script layer over definitions the loader already read, highest precedence last.
     *
     * <p>Mutates {@code target} in place and returns it, because {@link MachineLoader} wants the same map it
     * built for the data pack and the config directory. Ids a script <b>replaced</b> are logged at warn level
     * together with both origins: an override that happens silently is indistinguishable from a definition
     * that failed to load.
     *
     * @return the ids this call shadowed, in the order they were applied, so the rule is assertable offline
     */
    public static List<ResourceLocation> applyScriptLayer(Map<ResourceLocation, MachineDefinition> target) {
        List<ResourceLocation> shadowed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, MachineDefinition> entry : staged().entrySet()) {
            if (target.containsKey(entry.getKey())) {
                shadowed.add(entry.getKey());
            }
            target.put(entry.getKey(), entry.getValue());
        }
        for (ResourceLocation id : shadowed) {
            LOGGER.warn("[{}] The KubeJS script layer defines '{}', which a data pack or the config directory "
                            + "also defines; the script wins for this server. Script definitions take precedence "
                            + "over {} and over the data pack — remove it from one of the two, or give the "
                            + "machine a different registryname.",
                    ModularMachineryReborn.MOD_ID, id, MachineLoader.displayDirectory());
        }
        if (!staged().isEmpty()) {
            LOGGER.info("[{}] {} machine definition(s) came from the KubeJS script layer{}",
                    ModularMachineryReborn.MOD_ID, staged().size(),
                    shadowed.isEmpty() ? "" : " and shadowed " + shadowed.size() + " other definition(s)");
        }
        return List.copyOf(shadowed);
    }

    /**
     * The ids present in both layers, without applying anything — the same rule {@link #applyScriptLayer}
     * reports, split out so a caller that only wants the answer (the offline harness, which cannot call the
     * logger's host freely) does not have to mutate a table to get it.
     */
    public static List<ResourceLocation> shadowedIds(Map<ResourceLocation, MachineDefinition> baseline) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : staged().keySet()) {
            if (baseline.containsKey(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** One staged definition by id, or {@code null} — a convenience for diagnostics and tests. */
    @Nullable
    public static MachineDefinition staged(ResourceLocation id) {
        return staged().get(id);
    }
}
