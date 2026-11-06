package com.reborn.modularmachinery.factory;

import com.reborn.modularmachinery.api.event.FactoryRecipeFailureEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeFinishEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeStartEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeTickEvent;
import com.reborn.modularmachinery.api.event.MachineEvents;
import com.reborn.modularmachinery.api.event.Phase;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.EnergyUsage;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * The multi-thread engine of a factory controller: several <b>different</b> recipes settled at the same time,
 * one per {@link FactoryThread}.
 *
 * <p>This class is the counterpart of the running half of the original's {@code TileFactoryController}
 * (757 lines): {@code doSyncStep}, {@code doRecipeTick}, {@code doThreadRecipeTick},
 * {@code searchAndStartRecipe}, {@code offerRecipe}, {@code cleanIdleTimeoutThread}, {@code updateCoreThread},
 * {@code getAvailableParallelism} and {@code hasIdleThread}. It deliberately does <b>not</b> carry any of the
 * original's asynchronous scaffolding, and the next section says why for each piece.
 *
 * <h2>Fidelity: what was ported and what was dropped</h2>
 *
 * <table border="1">
 *   <caption>Original piece by piece</caption>
 *   <tr><th>Original</th><th>Here</th><th>Why</th></tr>
 *   <tr>
 *     <td>{@code workMode} ASYNC / SEMI_SYNC / SYNC, {@code ModularMachinery.EXECUTE_MANAGER}, per-machine
 *         {@code executeGroupId}, {@code TimeRecorder#addTask(…, usedTimeAvg)}</td>
 *     <td>absent</td>
 *     <td>An execution strategy for the <i>controller tick itself</i>, not for recipes. The scoping document
 *         (§5.1) explicitly permits the synchronous reading, and this project's controller is already
 *         synchronous; a "work mode" whose alternatives all do the same arithmetic is the misleading kind of
 *         fidelity.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code FactoryRecipeSearchTask} + {@code SequentialTaskExecutor} + {@code waitToExecute} +
 *         {@code searchTask.isDone()} + search timeout</td>
 *     <td>synchronous {@link #searchAndStart} every tick</td>
 *     <td>The original moved the recipe <i>search</i> to a {@code ForkJoinTask} only to keep the server thread
 *         free. With the search synchronous there is no task to wait for, and the executor that existed to
 *         sequence the returned tasks (and to block on a timeout) has nothing left to do. What survives is the
 *         observable behaviour: a slot is filled in the same tick the work becomes available.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code currentRecipeSearchDelay()} interval, {@code recipeResearchRetryCounter}</td>
 *     <td>{@link #searchAndStart} attempts a start every tick while a slot is free</td>
 *     <td>A pure throttle around an expensive asynchronous search. A synchronous walk over a machine's own
 *         recipe list is cheaper than the original's async submission, and a retry counter around it would only
 *         delay a craft the player can already see is possible.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code RecipeCraftingContextPool}, {@code RecipeCraftingContext}, {@code CraftingStatus}</td>
 *     <td>{@link ActiveCraft} plus the controller's existing {@code ControllerStatus}</td>
 *     <td>M2's engine has no crafting-context object: a recipe carries its modifiers as a method argument, and
 *         a settlement's state is (recipe, parallelism, progress). Introducing a context object here purely to
 *         mirror a class name would add a layer nothing reads.</td>
 *   </tr>
 *   <tr>
 *     <td>FactoryRecipe(Start|Tick|Finish|Failure)Event, {@code ControllerGuiInfoEvent}</td>
 *     <td>absent</td>
 *     <td>Those events existed for the CraftTweaker bridge (D7 excludes it). The controller's own
 *         {@code ControllerGuiInfoEvent} extension point is unaffected: this engine does not touch the
 *         single-craft path.</td>
 *   </tr>
 * </table>
 *
 * <p>Everything that <i>is</i> behaviour is kept, including the parts that look like bugs: the flat
 * {@code maxThreads} bound on ordinary threads (the original's {@code size() > getMaxThreads()} guard lets the
 * list reach {@code maxThreads + 1}; {@link #canAccept} reproduces that), the separate core-thread map that is
 * <i>not</i> bounded by {@code maxThreads}, the core thread's fixed recipe set, the 20-tick idle sweep with
 * {@code IDLE_TIME_OUT}, and the {@code max(1, …)} floor on the remaining parallelism.
 */
public final class FactoryEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_NEXT_ORDINARY_IDENTITY = "nextOrdinaryIdentity";
    private static final String TAG_NEXT_CORE_IDENTITY = "nextCoreIdentity";

    private final FactoryHost host;

    /** Core threads by name, in the machine's declaration order — the original's {@code coreRecipeThreads}. */
    private final Map<String, FactoryThread> coreThreads = new LinkedHashMap<>();
    /** Ordinary threads in creation order — the original's {@code recipeThreadList}. */
    private final List<FactoryThread> recipeThreads = new ArrayList<>();

    /** Ticks this engine has run, for the 20-tick idle sweep. Stands in for the original's {@code ticksExisted}. */
    private int ticks;

    /** How many ordinary threads have ever been created, so a thread's printed number never shifts. */
    private int createdOrdinary;
    private int createdCore;

    /** Set by any transition that changes what the controller should persist or show. */
    private boolean dirty;

    public FactoryEngine(FactoryHost host) {
        this.host = host;
    }

    // ------------------------------------------------------------ accessors

    /** The core threads, in declaration order. */
    public List<FactoryThread> coreThreads() {
        return List.copyOf(this.coreThreads.values());
    }

    /** The ordinary threads, in creation order. */
    public List<FactoryThread> ordinaryThreads() {
        return List.copyOf(this.recipeThreads);
    }

    /** Every thread: core ones first, then ordinary ones — the original's {@code getRecipeThreadList}. */
    public List<FactoryThread> allThreads() {
        List<FactoryThread> all = new ArrayList<>(this.coreThreads.size() + this.recipeThreads.size());
        all.addAll(this.coreThreads.values());
        all.addAll(this.recipeThreads);
        return all;
    }

    public int coreThreadCount() {
        return this.coreThreads.size();
    }

    public int ordinaryThreadCount() {
        return this.recipeThreads.size();
    }

    /** How many threads are actually running a recipe right now. */
    public int workingThreadCount() {
        int count = 0;
        for (FactoryThread thread : allThreads()) {
            if (thread.isWorking()) {
                count++;
            }
        }
        return count;
    }

    /** The original's {@code isWorking()}: at least one thread is crafting. */
    public boolean isWorking() {
        for (FactoryThread thread : allThreads()) {
            if (thread.isWorking()) {
                return true;
            }
        }
        return false;
    }

    /** All non-idle core and ordinary threads, as in the original TOP collectRequirementEnergy. */
    public EnergyUsage energyUsage() {
        var total = EnergyUsage.ZERO;
        for (FactoryThread thread : allThreads()) {
            ActiveCraft craft = thread.active();
            if (craft != null) total = total.plus(EnergyUsage.of(
                    craft.recipe(), effectiveModifiers(thread, craft), craft.parallelism()));
        }
        return total;
    }

    /** Frozen structural/catalyst rules plus the current controller and thread script contributions. */
    private RecipeModifiers effectiveModifiers(FactoryThread thread, ActiveCraft craft) {
        return craft.modifiers().without(craft.scriptModifiers)
                .andThen(host.scriptModifiers()).andThen(thread.scriptState().modifiers());
    }

    /** How many ordinary threads the machine allows — the original's {@code getMaxThreads()}. */
    public int maxOrdinaryThreads() {
        return this.host.maxThreads();
    }

    /** Clears the dirty flag and answers whether it had been set. */
    public boolean consumeDirty() {
        boolean was = this.dirty;
        this.dirty = false;
        return was;
    }

    // ------------------------------------------------------------ the tick

    /**
     * One engine tick — the original's {@code doSyncStep} ({@code TileFactoryController.java:130-161}), in its
     * order:
     *
     * <pre>
     * onMachineTick(START)
     * &lt;synchronous recipe search&gt;          // was executeSeqTask + the search task
     * if (hasIdleThread()) searchAndStartRecipe();
     * updateCoreThread();
     * if (threads exist) { doRecipeTick(); mark update; }
     * onMachineTick(END)
     * </pre>
     *
     * <p>The caller (the controller) owns the phases, the redstone gate and the structure check; this method
     * runs only for a formed, unpowered factory.
     */
    public void tick() {
        this.ticks++;
        syncCoreThreads();
        if (canAccept()) {
            searchAndStart();
        }
        tickThreads();
        if (consumeDirty()) {
            this.host.onThreadsChanged();
        }
    }

    /**
     * The original's {@code hasIdleThread()} ({@code TileFactoryController.java:521-533}): a slot is free when
     * the ordinary list has not reached the machine's {@code max-threads}, or when one of the existing slots
     * has stopped working.
     *
     * <p>It is public because the factory screen asks the same question the recipe search does: the original's
     * {@code GuiFactoryController:248-251} drew its status block only {@code if (factory.hasIdleThread())}.
     */
    public boolean hasIdleThread() {
        return canAccept();
    }

    /** See {@link #hasIdleThread()} — the same test, named for the search that uses it. */
    public boolean canAccept() {
        for (FactoryThread thread : coreThreads.values()) if (thread.isIdle()) return true;
        return ordinaryAdmission(null);
    }

    /** Paid settlements drain after shrink; only a new batch needs current ordinary capacity. */
    private boolean ordinaryAdmission(@Nullable FactoryThread replacing) {
        int working = 0;
        for (FactoryThread thread : recipeThreads) {
            if (thread != replacing && thread.isWorking()) working++;
        }
        return working < Math.max(0, host.maxThreads());
    }

    private boolean admits(FactoryThread thread) {
        return thread.isCoreThread()
                ? host.coreThreads().stream().anyMatch(spec -> spec.name().equals(thread.threadName()))
                : ordinaryAdmission(thread);
    }

    /**
     * The original's {@code searchAndStartRecipe} ({@code :322-359}) fused with {@code offerRecipe}
     * ({@code :432-453}): find a recipe no other thread is already settling, and give it a slot.
     *
     * <p><b>Why "no other thread" matters.</b> A recipe's inputs are paid when its settlement <i>starts</i>
     * (M2's lifecycle), so a second thread handed the same recipe would pay for them a second time while the
     * first thread had already reserved them. The original had the same hazard and avoided it by never
     * offering a recipe that was already in {@code getActiveRecipeList()}. Threads become candidate-free as
     * soon as they take one, which is what makes the harness's "N threads run different recipes" claim
     * observable rather than arranged.
     *
     * @return how many slots were filled, for diagnostics and tests
     */
    public int searchAndStart() {
        int started = 0;
        Set<ResourceLocation> unavailable = new LinkedHashSet<>();
        List<Candidate> runnable = runnable(unavailable);
        if (runnable.isEmpty()) {
            // Nothing is startable at all, so no thread is worth creating. Asking first is what keeps a factory
            // with nothing to do from spending a thread on finding that out.
            return 0;
        }
        for (FactoryThread core : List.copyOf(coreThreads.values())) {
            if (core.isWorking()) continue;
            ActiveCraft craft = nextCraft(core, runnable);
            if (craft == null) continue;
            if (begin(core, craft)) started++;
            else unavailable.add(craft.recipeId());
            runnable = runnable(unavailable);
        }
        Set<FactoryThread> triedIdle = new java.util.HashSet<>();
        while (!runnable.isEmpty()) {
            int previousCount = recipeThreads.size();
            FactoryThread target = nextFreeThread(triedIdle);
            if (target == null) {
                break;
            }
            ActiveCraft craft = nextCraft(target, runnable);
            if (craft == null) {
                // An existing slot may reject these recipes through its own recipe list/modifiers.
                // Try later slots or one fresh unrestricted slot before declaring the factory blocked.
                if (recipeThreads.size() > previousCount) {
                    // A check callback may deliberately configure the new slot even when it vetoes start.
                    if (!target.hasScriptRecipes() && target.scriptState().isEmpty()) recipeThreads.remove(target);
                    break;
                }
                triedIdle.add(target);
                continue;
            }
            if (begin(target, craft)) {
                started++;
                triedIdle.clear();
                runnable = runnable(unavailable);
                continue;
            }
            // The recipe passed the pre-check and the start phase still refused it — a per-tick requirement that
            // cannot be paid, say. Excluding it and trying the next candidate is the original's behaviour: a
            // failed offer was never an error, and the slot simply stays idle.
            unavailable.add(craft.recipeId());
            runnable = runnable(unavailable);
        }
        return started;
    }

    /**
     * The machine's (recipe, group) pairs that no thread is settling, that this tick has not already found
     * unstoppable, and whose one-shot inputs the structure can pay right now in that group.
     */
    private List<Candidate> runnable(Set<ResourceLocation> unavailable) {
        Set<ResourceLocation> blocked = recipesInProgress();
        HatchCollection ports = this.host.ports();
        RecipeModifiers modifiers = this.host.modifiers();
        List<Candidate> out = new ArrayList<>();
        boolean threadRules = allThreads().stream().anyMatch(t -> !t.scriptState().modifiers().isEmpty());
        for (MachineRecipe recipe : this.host.availableRecipes()) {
            if (blocked.contains(recipe.getId()) || unavailable.contains(recipe.getId())) {
                continue;
            }
            // Every candidate group is tried separately, in ascending order, the way the original's
            // canStartCrafting walked its sorted group keys and locked the first one that paid. A recipe that
            // pays in two different groups is two candidates: which one a thread gets decides which ports it
            // will keep drawing from for the rest of the settlement.
            for (int group : ports.groupIds()) {
                HatchCollection groupPorts = ports.withGroup(group);
                // A candidate is checked on a single-copy execution view. Its final allocation and catalyst
                // payments are recomputed when the thread starts; registry definitions remain untouched.
                MachineRecipe candidate = recipe.executionCopy();
                candidate.setParallelism(1);
                MachineRecipe.StartPlan plan = candidate.prepareParallelStart(groupPorts, modifiers, availableParallelism(null));
                if (candidate.canStart(groupPorts, plan) || threadRules) {
                    out.add(new Candidate(recipe, group));
                }
            }
        }
        return out;
    }

    /**
     * A definition and the port group that can fund one copy. A thread prepares its own final start plan.
     */
    private record Candidate(MachineRecipe recipe, int group) {
    }

    /** The slot the next recipe should go to: an idle ordinary thread first, otherwise a new one. */
    @Nullable
    private FactoryThread nextFreeThread(Set<FactoryThread> triedIdle) {
        if (ordinaryAdmission(null)) {
            FactoryThread idle = this.recipeThreads.stream()
                    .filter(thread -> thread.isIdle() && !triedIdle.contains(thread))
                    .min(java.util.Comparator.comparingInt(FactoryThread::ordinal)).orElse(null);
            if (idle != null) return idle;
        }
        // A core thread that has nothing to do is a legitimate home too: the original's search task was offered
        // to `recipeThreadList` only, but a core thread with an empty recipe set behaves identically to an
        // ordinary one, and using it costs nothing. This is a deliberate, documented improvement over
        // `offerRecipe`, which left an idle core thread unused.
        for (FactoryThread thread : this.coreThreads.values()) {
            if (thread.isIdle() && thread.recipeSet().isEmpty() && !triedIdle.contains(thread)) {
                return thread;
            }
        }
        if (!ordinaryAdmission(null) || recipeThreads.size() >= host.maxThreads()) {
            return null;
        }
        // `createdOrdinary` is the next unused identity. At MAX there is no safe non-negative number left
        // (the displayed number is ordinal + 1), so refuse a new slot rather than overflow or reuse one.
        if (this.createdOrdinary == Integer.MAX_VALUE) {
            LOGGER.error("[{}] Factory ordinary-thread identity space is exhausted; refusing to create another slot.",
                    com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID);
            return null;
        }
        FactoryThread created = new FactoryThread(this.createdOrdinary++);
        this.recipeThreads.add(created);
        return created;
    }

    /**
     * The next recipe for {@code thread}: the first of the machine's available recipes that the thread accepts,
     * that no other thread is settling and whose one-shot inputs the structure can pay.
     *
     * <p>The last condition is a pure pre-check; {@link FactoryThread#begin} re-runs it as part of the real
     * lifecycle, because the pre-check and the start must not be allowed to disagree.
     */
    @Nullable
    private ActiveCraft nextCraft(FactoryThread thread, List<Candidate> runnable) {
        for (Candidate candidate : runnable) {
            if (thread.accepts(candidate.recipe())) {
                // The candidate list was built from `canStart` for that group, so the first entry this thread
                // accepts is startable.
                ActiveCraft craft = prepareCraft(candidate.recipe(), candidate.group(), thread);
                if (craft != null) return craft;
            }
        }
        return null;
    }

    /** Runs the start phase of {@code craft} on {@code thread} and announces it when it took. */
    private boolean begin(FactoryThread thread, ActiveCraft craft) {
        if (!thread.accepts(craft.recipe()) || !admits(thread)) return false;
        if (craft.inputBinding().group(this.host.ports(), craft.group()) == null) return false;
        if (!thread.begin(craft, craft.inputBinding().scope(this.host.ports(), craft.group()), this.host.random(),
                () -> admits(thread))) {
            return false;
        }
        this.dirty = true;
        // The original fired FactoryRecipeStartEvent and called activeRecipe.start(context) here; the start is
        // already done (it is what makes begin() succeed), so only the controller's bookkeeping is left.
        this.host.onThreadStarted(thread);
        // C2 收尾（0.89.0）：开工成后发事件，与单控制器同一条规矩——只读不可取消（旧版「取消照样开工」
        // 是 1.12 残留，不复制；要否决开工用 RecipeCheckEvent.setFailed，0.88.0 起可否决）。
        MachineEvents.post(new FactoryRecipeStartEvent(this.host.controller(), this.host.controller().machineId(),
                craft.recipe(), craft.progress(), craft.duration(), craft.parallelism(), thread.ordinal()));
        // B8: startCommands run once the craft is really armed for this thread.
        this.host.runRecipeCommands(thread, craft.recipe(), FactoryHost.PHASE_START, 0);
        return true;
    }

    private ActiveCraft prepareCraft(MachineRecipe definition, int group, FactoryThread thread) {
        com.reborn.modularmachinery.machine.InputGroupBinding binding = com.reborn.modularmachinery.machine.InputGroupBinding.capture(host.ports(), group);
        HatchCollection ports = host.ports().withGroup(group);
        RecipeModifiers base = host.modifiers().andThen(thread.scriptState().modifiers());
        MachineRecipe recipe = definition.executionCopy();
        recipe.setExecutionTick(0);
        recipe.setParallelism(1);
        MachineRecipe.StartPlan plan = recipe.prepareParallelStart(ports, base, availableParallelism(thread));
        if (!recipe.canStart(ports, plan)) return null;
        recipe.applyDurationMultiplier(plan.modifiers());
        int computedParallelism = recipe.parallelism(ports, plan, availableParallelism(thread));
        if (recipe.parallelSearchIncomplete()) return null;
        int parallelism = Math.max(1, computedParallelism);
        var checkStart = MachineEvents.post(new com.reborn.modularmachinery.api.event.RecipeCheckEvent(
                host.controller(), host.controller().machineId(), Phase.START, recipe, 0, recipe.duration(plan.modifiers()), parallelism).forThread(thread.ordinal()));
        if (checkStart.isCanceled()) return null;
        parallelism = checkStart.resolvedParallelism(parallelism);
        recipe.setParallelism(parallelism);
        base = host.modifiers().andThen(thread.scriptState().modifiers());
        plan = recipe.prepareStart(ports, base);
        recipe.applyDurationMultiplier(plan.modifiers());
        if (!recipe.canStart(ports, plan) || !recipe.canTick(ports, plan.modifiers())) return null;
        var checkEnd = MachineEvents.post(new com.reborn.modularmachinery.api.event.RecipeCheckEvent(
                host.controller(), host.controller().machineId(), Phase.END, recipe, 0, recipe.duration(plan.modifiers()), parallelism).forThread(thread.ordinal()));
        if (checkEnd.isCanceled()) return null;
        parallelism = checkEnd.resolvedParallelism(parallelism);
        recipe.setParallelism(parallelism);
        base = host.modifiers().andThen(thread.scriptState().modifiers());
        if (thread.isCoreThread() && host.coreThreads().stream().noneMatch(spec -> spec.name().equals(thread.threadName()))) return null;
        return new ActiveCraft(recipe, parallelism, group, base).withInputBinding(binding).withScriptModifiers(host.scriptModifiers().andThen(thread.scriptState().modifiers()));
    }

    /**
     * Ticks every thread once — the original's {@code doRecipeTick} ({@code :184-197}): sweep first, then the
     * core map, then the ordinary list, in that order.
     */
    public void tickThreads() {
        cleanIdleThreads();
        // Completion may retire a core, and callbacks may replace a slot. Preserve this tick's order
        // without iterating a live map; a removed/replaced snapshot entry must not receive a stale tick.
        for (FactoryThread thread : List.copyOf(this.coreThreads.values())) {
            if (this.coreThreads.get(thread.threadName()) == thread) tickThread(thread);
        }
        for (FactoryThread thread : this.recipeThreads) {
            tickThread(thread);
        }
    }

    /**
     * One thread's tick — the original's {@code doThreadRecipeTick} ({@code :202-257}), with its order kept:
     * idle threads age, a finished-but-blocked thread retries every 10 ticks, per-tick requirements are paid,
     * and a completed craft finishes and immediately tries to restart.
     */
    private void tickThread(FactoryThread thread) {
        ActiveCraft craft = thread.active();
        if (craft == null) {
            thread.age();
            return;
        }

        if (craft.inputBinding().group(this.host.ports(), craft.group()) == null) return; // Paid source unavailable: pause, never rebind.
        HatchCollection ports = craft.inputBinding().scope(this.host.ports(), craft.group());
        RecipeModifiers modifiers = effectiveModifiers(thread, craft);
        craft.withDuration(craft.recipe().duration(modifiers));

        // Re-assert the freeze every tick: the recipe object is shared with every other controller in the
        // level, so another machine's settlement must not be able to change what this thread pays.
        craft.recipe().setParallelism(craft.parallelism());
        craft.recipe().applyDurationMultiplier(modifiers);

        // As on the ordinary controller, reaching a reduced target needs no extra paid tick.
        // Keep the persisted finish-only phase; unpaid delayed requirements are settled by finish.
        if (craft.isComplete()) craft.recipe().markFinalTickPaid();
        if (!craft.recipe().hasPaidFinalTick()) {
            // C2 收尾（0.89.0）：本 tick 前发 Tick/START，可取消——取消 = 整 tick 跳过、什么都不扣
            // （旧版 PreTick 的 isFailure 短路）。
            FactoryRecipeTickEvent startEvent = MachineEvents.post(new FactoryRecipeTickEvent(
                    this.host.controller(), this.host.controller().machineId(), craft.recipe(), craft.progress(),
                    craft.duration(), craft.parallelism(), thread.ordinal(), Phase.START));
            craft.recipe().setExecutionTick(craft.progress());
            if (startEvent.isCanceled()) {
                return;
            }

            long next = (long) craft.progress() + 1;
            boolean tickReady = craft.recipe().canTick(ports, modifiers);
            if (next >= craft.duration() && tickReady && !craft.recipe().canTickAndFinish(ports, modifiers)) {
                // The craft would complete but its results do not fit. The original retried every 10 ticks
                // (`thread.isWaitForFinish()` + `ticksExisted % 10 == 0`); the slot keeps its paid inputs and its
                // progress and simply does not advance this tick.
                return;
            }
            if (!tickReady || !craft.recipe().tick(ports, this.host.random(), modifiers)) {
                // A per-tick requirement cannot be served. 0.89.0（C2 收尾）起走事件：默认值仍是配方的
                // cancelIfPerTickFails（旧版 FactoryRecipeFailureEvent 的 destructRecipe 默认，
                // TileFactoryController:293），监听器可按次改写——0.75.0–0.88.0 期间直接读字段的已记差异
                // 就此收回。true = 清空槽位（回到空闲，下次可再开工）；false = 走机器 failure-action
                // （已付一次性输入不返还、槽位保持武装——旧版行为）。
                String cause = craft.recipe().tickFailure(ports, modifiers);
                FactoryRecipeFailureEvent event = MachineEvents.post(new FactoryRecipeFailureEvent(
                        this.host.controller(), this.host.controller().machineId(), craft.recipe(), craft.progress(),
                        craft.duration(), craft.parallelism(), thread.ordinal(),
                        cause == null ? MachineRecipe.FAILURE_TICK : cause, craft.recipe().cancelIfPerTickFails()));
                if (event.destructRecipe()) {
                    thread.clear();
                    this.dirty = true;
                    this.host.onThreadIdle(thread);
                    return;
                }
                // Otherwise apply the machine's failure-action — the original's failed ioTick
                // (RecipeCraftingContext:238-298) is exactly where ActiveMachineRecipe#doFailureAction:106-115
                // ran, and a factory thread ticks the same object. The thread keeps the craft (it is armed by its
                // slot, not by its progress), so even `reset` costs time and never a second set of one-shot
                // inputs — the original's behaviour.
                int kept = this.host.failureAction().afterFailedTick(craft.progress());
                if (kept != craft.progress()) {
                    craft.setProgress(kept);
                    this.dirty = true;
                }
                return;
            }
            // B8: same ordering as the single-craft path -- processingCommands see the progress before this
            // successful tick was counted.
            this.host.runRecipeCommands(thread, craft.recipe(), FactoryHost.PHASE_PROCESSING, craft.progress());
            // C2 收尾：本 tick 资源已扣、进度未加之前发 Tick/END，可取消——取消 = 资源留扣、进度不加
            // （旧版 preventProgressing 的等价物；本引擎没有「回退已扣资源」的通道，差异只体现在进度上）。
            FactoryRecipeTickEvent endEvent = MachineEvents.post(new FactoryRecipeTickEvent(
                    this.host.controller(), this.host.controller().machineId(), craft.recipe(), craft.progress(),
                    craft.duration(), craft.parallelism(), thread.ordinal(), Phase.END));
            if (endEvent.isCanceled()) {
                return;
            }
            craft.advance();
            this.dirty = true;
        }

        if (!craft.isComplete() && !craft.recipe().hasPaidFinalTick()) {
            return;
        }
        craft.recipe().markFinalTickPaid();
        if (!craft.recipe().canFinish(ports, modifiers)) return;
        // C2 小件（0.91.0）：结算前发 ResultChanceCreateEvent，与单控制器同一点；不替换时用宿主随机。
        com.reborn.modularmachinery.api.event.ResultChanceCreateEvent chance =
                MachineEvents.post(new com.reborn.modularmachinery.api.event.ResultChanceCreateEvent(
                        this.host.controller(), this.host.controller().machineId(), craft.recipe(),
                        craft.progress(), craft.duration(), craft.parallelism(), this.host.random()).forThread(thread.ordinal()));
        if (!craft.recipe().finish(ports, chance.random(), modifiers)) {
            // Do not pay or emit the final tick again when finish is retried.
            return;
        }
        // B8: finishCommands run after the outputs have been settled.
        this.host.runRecipeCommands(thread, craft.recipe(), FactoryHost.PHASE_FINISH, 0);
        this.host.onThreadFinished(thread);
        // C2 收尾：产物已结算、完成命令已跑，重开工之前发 Finish——只读（旧版 TileFactoryController:305）。
        MachineEvents.post(new FactoryRecipeFinishEvent(this.host.controller(), this.host.controller().machineId(),
                craft.recipe(), craft.progress(), craft.duration(), craft.parallelism(), thread.ordinal()));
        thread.scriptState().clearCraft();
        tryRestart(thread, craft);
    }

    /**
     * The original's {@code RecipeThread#onFinished} tail and {@code FactoryRecipeThread#tryRestartRecipe}
     * ({@code :142-159}): re-arm the same recipe for another settlement, and when it will not take, empty the
     * slot.
     *
     * <p>A new settlement resolves the current recipe definition after reload, then re-evaluates current
     * resources and catalyst payments. The finished slot is
     * excluded from the machine's parallel ledger while computing its new allowance.
     */
    private void tryRestart(FactoryThread thread, ActiveCraft finished) {
        MachineRecipe current = host.currentRecipe(finished.recipeId());
        boolean withdrawn = thread.isCoreThread()
                && host.coreThreads().stream().noneMatch(spec -> spec.name().equals(thread.threadName()));
        if (current == null || !admits(thread) || !thread.accepts(current) || withdrawn) {
            thread.clear();
            if (withdrawn) this.coreThreads.remove(thread.threadName(), thread);
            dirty = true; host.onThreadIdle(thread); return;
        }
        Integer currentGroup = finished.inputBinding().group(host.ports(), finished.group());
        ActiveCraft again = currentGroup == null ? null : prepareCraft(current, currentGroup, thread);
        if (again != null && begin(thread, again)) return;
        thread.clear();
        this.dirty = true;
        this.host.onThreadIdle(thread);
    }

    /**
     * The original's {@code cleanIdleTimeoutThread} ({@code :514-519}): every 20 ticks, drop the ordinary
     * threads that have been idle for {@code IDLE_TIME_OUT}. Core threads are never dropped — that is what
     * makes them core.
     */
    public void cleanIdleThreads() {
        if (this.ticks % FactoryThreadModel.IDLE_SWEEP_INTERVAL != 0) {
            return;
        }
        if (this.recipeThreads.removeIf(FactoryThread::expired)) {
            this.dirty = true;
        }
    }

    /**
     * The original's {@code updateCoreThread} ({@code :477-509}): keep the core-thread map in step with the
     * machine's declaration — add what appeared, drop what disappeared, refresh the fixed recipe sets.
     *
     * <p>The original re-checked only every 20 ticks once the map was non-empty because the definition could
     * only change through CraftTweaker at runtime. Here a definition only changes across a reload, and the
     * controller resets the engine when it notices a different machine formed, so the check is cheap and runs
     * every tick.
     */
    public void syncCoreThreads() {
        List<FactoryThreadModel.CoreThreadSpec> declared = this.host.coreThreads();
        if (declared.isEmpty()) {
            if (this.coreThreads.values().removeIf(FactoryThread::isIdle)) {
                this.dirty = true;
            }
            return;
        }

        Set<String> wanted = new LinkedHashSet<>();
        int ordinal = 0;
        for (FactoryThreadModel.CoreThreadSpec spec : declared) {
            wanted.add(spec.name());
            FactoryThread existing = this.coreThreads.get(spec.name());
            if (existing == null) {
                // `createdCore` is the next unused creation ordinal (the identity is -2-createdCore). At MAX
                // there is no safe core identity left; skip the new preset rather than overflow or reuse one.
                if (this.createdCore == Integer.MAX_VALUE) {
                    LOGGER.error("[{}] Factory core-thread identity space is exhausted; '{}' cannot be created now.",
                            com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, spec.name());
                } else {
                    this.coreThreads.put(spec.name(), new FactoryThread(spec, createdCore++));
                    this.dirty = true;
                }
            } else {
                Set<ResourceLocation> wanted2 = Set.copyOf(spec.recipes());
                if (!existing.hasScriptRecipes() && !existing.recipeSet().equals(wanted2)) {
                    existing.setRecipeSet(wanted2);
                    this.dirty = true;
                }
            }
            ordinal++;
        }
        if (this.coreThreads.entrySet().removeIf(entry -> !wanted.contains(entry.getKey()) && entry.getValue().isIdle())) {
            this.dirty = true;
        }
    }

    /**
     * The original's {@code getAvailableParallelism} ({@code :400-417}): the machine's ceiling minus what the
     * <b>other</b> threads have already claimed, floored at 1.
     *
     * <p>The ceiling comes from the host because it is a structure calculation
     * ({@code ParallelismLimit.resolve} over the parallel controllers found in the formed machine), while the
     * subtraction is the engine's business — it is the only object that knows which threads hold what.
     * {@code exclude} is the slot about to be handed the recipe: the original never had to name it because it
     * always asked on behalf of a thread that had not started yet, and naming it keeps that property.
     */
    public int availableParallelism(@Nullable FactoryThread exclude) {
        long remaining = this.host.parallelCeiling();
        for (FactoryThread thread : allThreads()) {
            if (thread == exclude || thread.isIdle()) {
                continue;
            }
            remaining -= Math.max(0, thread.parallelism() - 1);
        }
        return (int) Math.max(1L, remaining);
    }

    /** The recipes a thread is settling right now, by id. */
    public Set<ResourceLocation> recipesInProgress() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (FactoryThread thread : allThreads()) {
            ResourceLocation id = thread.recipeId();
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    // ------------------------------------------------------------ structure changes

    /**
     * Forgets every thread. The original's {@code resetRecipe()} ({@code :382-386}) did exactly this on a
     * structure change: a thread's state is only meaningful while the structure that produced its ports stands.
     *
     * <p>Crafts in flight are abandoned rather than completed: their inputs were already paid into the ports
     * they were started with, and those ports may not even exist any more. That matches the single-craft path,
     * which drops its progress on a structure change too ({@code abortCraft}).
     */
    public void reset() {
        boolean had = !this.coreThreads.isEmpty() || !this.recipeThreads.isEmpty();
        this.coreThreads.clear();
        this.recipeThreads.clear();
        this.createdOrdinary = 0;
        this.createdCore = 0;
        if (had) {
            this.dirty = true;
        }
    }

    // ------------------------------------------------------------ persistence

    /**
     * Writes the ordinary and core threads, mirroring the original's {@code writeCustomNBT} keys
     * ({@code threadList} / {@code coreThreadList}) so an NBT dump of a factory controller reads the way the
     * original's did.
     *
     * <p>0.121.51 adds the slot identity on each entry and the next-identity high water marks on the engine.
     * The high water marks are written even when no thread survives, so retired identities are not reused.
     */
    public void save(CompoundTag tag) {
        tag.putInt(TAG_NEXT_ORDINARY_IDENTITY, this.createdOrdinary);
        tag.putInt(TAG_NEXT_CORE_IDENTITY, this.createdCore);
        if (!this.recipeThreads.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (FactoryThread thread : this.recipeThreads) {
                list.add(thread.save());
            }
            tag.put("threadList", list);
        }
        if (!this.coreThreads.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (FactoryThread thread : this.coreThreads.values()) {
                list.add(thread.save());
            }
            tag.put("coreThreadList", list);
        }
    }

    /**
     * Restores what {@link #save} wrote. A thread whose recipe is no longer registered is restored <b>idle</b>
     * rather than dropped: the thread is a structural slot, and the recipe may simply be in a data pack that is
     * loading later.
     *
     * <p>0.121.51 restores the slot identities and the next-identity high water marks. Missing identities (old
     * saves) migrate deterministically in list/preset order; malformed or duplicate identities are ignored and
     * the entry gets the next unused identity. Replaying the same snapshot is therefore idempotent, retired
     * identities stay retired through the high water marks, and a counter at its maximum refuses a new slot
     * instead of overflowing or reusing one.
     *
     * @param resolve turns a saved recipe id back into a recipe, or {@code null} when it is unknown here
     */
    public void load(CompoundTag tag,
                     java.util.function.Function<ResourceLocation, MachineRecipe> resolve,
                     List<FactoryThreadModel.CoreThreadSpec> corePreset) {
        this.coreThreads.clear();
        this.recipeThreads.clear();

        // Restoration is determined by this snapshot, not by how often the live engine has read it.
        // Keep the live allocation high water separately: migrating an old snapshot must neither consume
        // fresh identities on every replay nor allow future new slots to reuse retired identities.
        int nextOrdinary = readHighWater(tag, TAG_NEXT_ORDINARY_IDENTITY);
        int nextCore = readHighWater(tag, TAG_NEXT_CORE_IDENTITY);

        Map<Integer, Integer> coreOccurrences = new HashMap<>();
        Map<Integer, Integer> ordinaryOccurrences = new HashMap<>();
        Map<String, CompoundTag> savedCoreByName = new LinkedHashMap<>();
        if (tag.contains("coreThreadList")) {
            net.minecraft.nbt.ListTag list = tag.getList("coreThreadList", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                Integer identity = FactoryThread.savedIdentity(entry, true);
                if (identity != null) {
                    coreOccurrences.merge(identity, 1, Integer::sum);
                    nextCore = Math.max(nextCore, FactoryThread.coreCreationOrdinal(identity) + 1);
                }
                savedCoreByName.put(entry.getString(FactoryThread.TAG_CORE_THREAD_NAME), entry);
            }
        }

        for (FactoryThreadModel.CoreThreadSpec spec : corePreset) {
            CompoundTag entry = savedCoreByName.get(spec.name());
            Integer identity = entry == null ? null : FactoryThread.savedIdentity(entry, true);
            if (identity != null && coreOccurrences.getOrDefault(identity, 0) > 1) {
                identity = null;
            }
            if (identity != null) {
                FactoryThread thread = new FactoryThread(spec, FactoryThread.coreCreationOrdinal(identity));
                this.coreThreads.put(spec.name(), thread);
                restore(thread, entry, resolve);
                continue;
            }
            if (nextCore == Integer.MAX_VALUE) {
                LOGGER.error("[{}] Factory core-thread identity space is exhausted; '{}' was not restored.",
                        com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, spec.name());
                continue;
            }
            FactoryThread thread = new FactoryThread(spec, nextCore++);
            this.coreThreads.put(spec.name(), thread);
            if (entry != null) {
                restore(thread, entry, resolve);
            }
        }

        // A withdrawn declaration does not erase the batch it already paid for. Retain only a valid
        // active execution; empty/invalid unknown slots are not declarations to resurrect. The normal
        // restore path enforces current recipe ownership/fingerprint and preserves frozen payment state.
        for (Map.Entry<String, CompoundTag> savedEntry : savedCoreByName.entrySet()) {
            String name = savedEntry.getKey();
            CompoundTag entry = savedEntry.getValue();
            if (name.isBlank() || corePreset.stream().anyMatch(spec -> spec.name().equals(name))
                    || FactoryThread.load(entry) == null) continue;
            Integer identity = FactoryThread.savedIdentity(entry, true);
            if (identity != null && coreOccurrences.getOrDefault(identity, 0) > 1) identity = null;
            if (identity == null && nextCore == Integer.MAX_VALUE) {
                LOGGER.error("[{}] Factory core-thread identity space is exhausted; retiring '{}' was not restored.",
                        com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, name);
                continue;
            }
            int creation = identity == null ? nextCore : FactoryThread.coreCreationOrdinal(identity);
            FactoryThread thread = new FactoryThread(new FactoryThreadModel.CoreThreadSpec(name, List.of()), creation);
            restore(thread, entry, resolve);
            if (thread.isWorking()) {
                this.coreThreads.put(name, thread);
                if (identity == null) nextCore++;
            }
        }

        if (tag.contains("threadList")) {
            net.minecraft.nbt.ListTag list = tag.getList("threadList", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                Integer saved = FactoryThread.savedIdentity(entry, false);
                if (saved != null) {
                    ordinaryOccurrences.merge(saved, 1, Integer::sum);
                    // Reserve every saved identity before assigning any missing/duplicate entry. Otherwise
                    // an earlier fallback can steal a later valid identity (e.g. [missing, 0]).
                    nextOrdinary = Math.max(nextOrdinary, saved + 1);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                Integer identity = FactoryThread.savedIdentity(entry, false);
                if (identity != null && ordinaryOccurrences.getOrDefault(identity, 0) > 1) {
                    identity = null;
                }
                if (identity != null) {
                    nextOrdinary = Math.max(nextOrdinary, identity + 1);
                } else if (nextOrdinary == Integer.MAX_VALUE) {
                    LOGGER.error("[{}] Factory ordinary-thread identity space is exhausted; saved slot {} was not restored.",
                            com.reborn.modularmachinery.ModularMachineryReborn.MOD_ID, i);
                    continue;
                } else {
                    identity = nextOrdinary++;
                }
                FactoryThread thread = new FactoryThread(identity);
                restore(thread, entry, resolve);
                this.recipeThreads.add(thread);
            }
        }

        this.createdOrdinary = Math.max(this.createdOrdinary, nextOrdinary);
        this.createdCore = Math.max(this.createdCore, nextCore);
    }

    /** Snapshot-local migration starts at zero for legacy/malformed metadata; live high water is merged later. */
    private static int readHighWater(CompoundTag tag, String key) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_INT) ? Math.max(0, tag.getInt(key)) : 0;
    }

    private void restore(FactoryThread thread, CompoundTag entry,
                         java.util.function.Function<ResourceLocation, MachineRecipe> resolve) {
        thread.loadScriptState(entry);
        FactoryThread.SavedCraft saved = FactoryThread.load(entry);
        if (saved == null) {
            return;
        }
        MachineRecipe recipe = resolve.apply(saved.recipeId());
        if (recipe == null || !recipe.belongsTo(this.host.controller().machineId())
                || !recipe.canRestoreExecutionState(entry.getCompound("scriptExecution"))) {
            thread.scriptState().clearCraft();
            return;
        }
        com.reborn.modularmachinery.machine.InputGroupBinding binding = com.reborn.modularmachinery.machine.InputGroupBinding.load(entry, saved.group());
        HatchCollection groupPorts = binding.scope(this.host.ports(), saved.group());
        if (!binding.resolved()) LOGGER.warn("Factory paid input group {} has no unambiguous saved source; preserving suspended work.", saved.group());
        RecipeModifiers effective = saved.modifiers() != null ? saved.modifiers()
                : this.host.modifiers().andThen(recipe.catalystModifiers(groupPorts));
        ActiveCraft craft = new ActiveCraft(recipe, saved.parallelism(), saved.group(), effective).withInputBinding(binding);
        if (entry.contains("scriptModifiers")) craft.withScriptModifiers(RecipeModifiers.load(entry.getList("scriptModifiers", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        if (saved.duration() > 0) craft.withDuration(saved.duration());
        craft.setProgress(saved.progress());
        craft.recipe().loadExecutionState(entry.getCompound("scriptExecution"));
        thread.restart(craft);
    }

    /** The engine's tick counter, for the sweep interval and for tests. */
    public int ticks() {
        return this.ticks;
    }

    @Override
    public String toString() {
        return "FactoryEngine[core=" + this.coreThreads.size() + ", ordinary=" + this.recipeThreads.size()
                + ", working=" + workingThreadCount() + ", ticks=" + this.ticks + "]";
    }

    // ------------------------------------------------------------ one settlement

    /**
     * One thread's settlement: which recipe, how many copies it settles, how far along it is, and how long it
     * takes. The counterpart of the original's {@code ActiveMachineRecipe} <b>as a thread holds it</b> —
     * deliberately not a shared object, for the reason spelled out on {@link FactoryThread#parallelism()}.
     *
     * <p>Mutability is confined to {@link #progress()}, which only the engine's own tick can advance.
     */
    public static final class ActiveCraft {

        private final MachineRecipe recipe;
        private final ResourceLocation recipeId;
        private final int parallelism;
        private final int group;
        private com.reborn.modularmachinery.machine.InputGroupBinding inputBinding = com.reborn.modularmachinery.machine.InputGroupBinding.manual();
        public com.reborn.modularmachinery.machine.InputGroupBinding inputBinding() { return inputBinding; }
        ActiveCraft withInputBinding(com.reborn.modularmachinery.machine.InputGroupBinding value) { inputBinding = value; return this; }
        private final RecipeModifiers startBaseModifiers;
        private RecipeModifiers modifiers;
        private RecipeModifiers scriptModifiers = RecipeModifiers.EMPTY;
        public RecipeModifiers scriptModifiers() { return scriptModifiers; }
        ActiveCraft withScriptModifiers(RecipeModifiers value) { scriptModifiers = value; return this; }

        private int progress;
        private int duration;

        ActiveCraft(MachineRecipe recipe, int parallelism, int group, RecipeModifiers modifiers) {
            this.recipe = recipe.executionCopy();
            this.recipeId = recipe.getId();
            this.parallelism = Math.max(1, parallelism);
            this.group = group;
            this.startBaseModifiers = modifiers;
            this.modifiers = modifiers;
            this.duration = Math.max(1, recipe.duration(modifiers));
        }

        public MachineRecipe recipe() {
            return this.recipe;
        }

        public ResourceLocation recipeId() {
            return this.recipeId;
        }

        /** The copies this settlement moves per requirement — the original's {@code ActiveMachineRecipe}. */
        public int parallelism() {
            return this.parallelism;
        }

        /**
         * The port group this settlement draws from, frozen like the parallelism — the original's
         * {@code RecipeCraftingContext#getGroupId} once {@code canStartCrafting} had locked a group.
         */
        public int group() {
            return this.group;
        }

        /**
         * The modifiers this settlement froze at its start, catalyst modifiers included only for catalysts that
         * were present in that group's ports then. Re-asking the ports later is wrong: the catalyst is consumed
         * by {@code start}.
         */
        public RecipeModifiers modifiers() {
            return this.modifiers;
        }

        MachineRecipe.StartPlan planStart(HatchCollection ports) {
            this.recipe.setParallelism(this.parallelism);
            MachineRecipe.StartPlan plan = this.recipe.prepareStart(ports, this.startBaseModifiers);
            this.modifiers = plan.modifiers();
            return plan;
        }

        /** Ticks already settled, the original's {@code ActiveMachineRecipe#getTick}. */
        public int progress() {
            return this.progress;
        }

        /** Ticks the settlement takes after duration modifiers. */
        public int duration() {
            return this.duration;
        }

        public boolean isComplete() {
            return this.progress >= this.duration;
        }

        /**
         * Saturates at {@link Integer#MAX_VALUE}: the next tick after the last payable tick must not wrap
         * back to a negative progress. A craft already sitting at MAX goes through the existing final-tick /
         * finish flow below instead of advancing again.
         */
        void advance() {
            if (this.progress != Integer.MAX_VALUE) {
                this.progress++;
            }
        }

        void setProgress(int value) {
            this.progress = Math.max(0, value);
        }

        ActiveCraft withDuration(int ticks) {
            this.duration = Math.max(1, ticks);
            return this;
        }

        @Override
        public String toString() {
            return this.recipeId + " " + this.progress + "/" + this.duration + " x" + this.parallelism
                    + " group=" + this.group;
        }
    }
}
