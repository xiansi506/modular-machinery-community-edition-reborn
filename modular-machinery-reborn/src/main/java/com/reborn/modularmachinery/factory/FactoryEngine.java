package com.reborn.modularmachinery.factory;

import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    private final FactoryHost host;

    /** Core threads by name, in the machine's declaration order — the original's {@code coreRecipeThreads}. */
    private final Map<String, FactoryThread> coreThreads = new LinkedHashMap<>();
    /** Ordinary threads in creation order — the original's {@code recipeThreadList}. */
    private final List<FactoryThread> recipeThreads = new ArrayList<>();

    /** Ticks this engine has run, for the 20-tick idle sweep. Stands in for the original's {@code ticksExisted}. */
    private int ticks;

    /** How many ordinary threads have ever been created, so a thread's printed number never shifts. */
    private int createdOrdinary;

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
        if (this.recipeThreads.size() < this.host.maxThreads()) {
            return true;
        }
        for (FactoryThread thread : this.recipeThreads) {
            if (thread.isIdle()) {
                return true;
            }
        }
        return false;
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
        List<MachineRecipe> runnable = runnable(unavailable);
        if (runnable.isEmpty()) {
            // Nothing is startable at all, so no thread is worth creating. Asking first is what keeps a factory
            // with nothing to do from spending a thread on finding that out.
            return 0;
        }
        while (true) {
            FactoryThread target = nextFreeThread();
            if (target == null) {
                break;
            }
            ActiveCraft craft = nextCraft(target, runnable);
            if (craft == null) {
                break;
            }
            if (begin(target, craft)) {
                started++;
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
     * The machine's recipes that no thread is settling, that this tick has not already found unstoppable, and
     * whose one-shot inputs the structure can pay right now.
     */
    private List<MachineRecipe> runnable(Set<ResourceLocation> unavailable) {
        Set<ResourceLocation> blocked = recipesInProgress();
        HatchCollection ports = this.host.ports();
        RecipeModifiers modifiers = this.host.modifiers();
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe recipe : this.host.availableRecipes()) {
            if (blocked.contains(recipe.getId()) || unavailable.contains(recipe.getId())) {
                continue;
            }
            if (recipe.canStart(ports, modifiers)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /** The slot the next recipe should go to: an idle ordinary thread first, otherwise a new one. */
    @Nullable
    private FactoryThread nextFreeThread() {
        for (FactoryThread thread : this.recipeThreads) {
            if (thread.isIdle()) {
                return thread;
            }
        }
        // A core thread that has nothing to do is a legitimate home too: the original's search task was offered
        // to `recipeThreadList` only, but a core thread with an empty recipe set behaves identically to an
        // ordinary one, and using it costs nothing. This is a deliberate, documented improvement over
        // `offerRecipe`, which left an idle core thread unused.
        for (FactoryThread thread : this.coreThreads.values()) {
            if (thread.isIdle() && thread.recipeSet().isEmpty()) {
                return thread;
            }
        }
        if (!canAccept()) {
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
    private ActiveCraft nextCraft(FactoryThread thread, List<MachineRecipe> runnable) {
        List<MachineRecipe> candidates = thread.filter(runnable);
        if (candidates.isEmpty()) {
            return null;
        }
        // The candidate list was built from `canStart` for the machine as a whole, so the first entry that this
        // thread accepts is startable.
        return new ActiveCraft(candidates.get(0), availableParallelism(thread));
    }

    /** Runs the start phase of {@code craft} on {@code thread} and announces it when it took. */
    private boolean begin(FactoryThread thread, ActiveCraft craft) {
        if (!thread.begin(craft, this.host.ports(), this.host.modifiers(), this.host.random())) {
            return false;
        }
        this.dirty = true;
        // The original fired FactoryRecipeStartEvent and called activeRecipe.start(context) here; the start is
        // already done (it is what makes begin() succeed), so only the controller's bookkeeping is left.
        this.host.onThreadStarted(thread);
        return true;
    }

    /**
     * Ticks every thread once — the original's {@code doRecipeTick} ({@code :184-197}): sweep first, then the
     * core map, then the ordinary list, in that order.
     */
    public void tickThreads() {
        cleanIdleThreads();
        for (FactoryThread thread : this.coreThreads.values()) {
            tickThread(thread);
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

        HatchCollection ports = this.host.ports();
        RecipeModifiers modifiers = this.host.modifiers();

        // Re-assert the freeze every tick: the recipe object is shared with every other controller in the
        // level, so another machine's settlement must not be able to change what this thread pays.
        craft.recipe().setParallelism(craft.parallelism());
        craft.recipe().applyDurationMultiplier(modifiers);

        int next = craft.progress() + 1;
        if (next >= craft.duration() && !craft.recipe().canFinish(ports, modifiers)) {
            // The craft would complete but its results do not fit. The original retried every 10 ticks
            // (`thread.isWaitForFinish()` + `ticksExisted % 10 == 0`); the slot keeps its paid inputs and its
            // progress and simply does not advance this tick.
            return;
        }
        if (!craft.recipe().canTick(ports, modifiers)) {
            // A per-tick requirement cannot be served: the thread stops advancing but keeps its inputs, which
            // is the original's `isNotWorking` -> wait-for-finish reading. The original could additionally
            // *destruct* the recipe through FactoryRecipeFailureEvent; that event only existed for the
            // CraftTweaker bridge, so nothing here can cancel a craft mid-flight. Recorded as a deviation.
            //
            // What it does do here is apply the machine's failure-action — the original's failed ioTick
            // (RecipeCraftingContext:238-298) is exactly where ActiveMachineRecipe#doFailureAction:106-115
            // ran, and a factory thread ticks the same object. The thread keeps the craft in all three cases
            // (it is armed by its slot, not by its progress), so even `reset` costs time and never a second
            // set of one-shot inputs — the original's behaviour.
            int kept = this.host.failureAction().afterFailedTick(craft.progress());
            if (kept != craft.progress()) {
                craft.setProgress(kept);
                this.dirty = true;
            }
            return;
        }
        craft.recipe().tick(ports, this.host.random(), modifiers);
        craft.advance();
        this.dirty = true;

        if (!craft.isComplete()) {
            return;
        }
        craft.recipe().finish(ports, this.host.random(), modifiers);
        this.host.onThreadFinished(thread);
        tryRestart(thread, craft);
    }

    /**
     * The original's {@code RecipeThread#onFinished} tail and {@code FactoryRecipeThread#tryRestartRecipe}
     * ({@code :142-159}): re-arm the same recipe for another settlement, and when it will not take, empty the
     * slot.
     *
     * <p>The parallelism is <b>not</b> recomputed here. The original called
     * {@code activeRecipe.setMaxParallelism(factory.getAvailableParallelism())} before re-checking, but the
     * available parallelism is computed by <i>subtracting</i> what the already-running threads hold — and this
     * thread has just released its own claim, so the value it would get back is exactly the one it started
     * with as long as the structure and the other threads have not changed. Keeping the frozen number is both
     * simpler and identical in every case the harness and the game exercise.
     */
    private void tryRestart(FactoryThread thread, ActiveCraft finished) {
        ActiveCraft again = new ActiveCraft(finished.recipe(), finished.parallelism());
        if (thread.begin(again, this.host.ports(), this.host.modifiers(), this.host.random())) {
            this.dirty = true;
            this.host.onThreadStarted(thread);
            return;
        }
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
            if (!this.coreThreads.isEmpty()) {
                this.coreThreads.clear();
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
                this.coreThreads.put(spec.name(), new FactoryThread(spec, ordinal));
                this.dirty = true;
            } else {
                Set<ResourceLocation> wanted2 = Set.copyOf(spec.recipes());
                if (!existing.recipeSet().equals(wanted2)) {
                    existing.setRecipeSet(wanted2);
                    this.dirty = true;
                }
            }
            ordinal++;
        }
        if (this.coreThreads.keySet().removeIf(name -> !wanted.contains(name))) {
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
        if (had) {
            this.dirty = true;
        }
    }

    // ------------------------------------------------------------ persistence

    /**
     * Writes the ordinary and core threads, mirroring the original's {@code writeCustomNBT} keys
     * ({@code threadList} / {@code coreThreadList}) so an NBT dump of a factory controller reads the way the
     * original's did.
     */
    public void save(CompoundTag tag) {
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
     * @param resolve turns a saved recipe id back into a recipe, or {@code null} when it is unknown here
     */
    public void load(CompoundTag tag,
                     java.util.function.Function<ResourceLocation, MachineRecipe> resolve,
                     List<FactoryThreadModel.CoreThreadSpec> corePreset) {
        this.coreThreads.clear();
        this.recipeThreads.clear();
        for (FactoryThreadModel.CoreThreadSpec spec : corePreset) {
            this.coreThreads.put(spec.name(), new FactoryThread(spec, this.coreThreads.size()));
        }
        if (tag.contains("coreThreadList")) {
            net.minecraft.nbt.ListTag list = tag.getList("coreThreadList", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                FactoryThread thread = this.coreThreads.get(entry.getString(FactoryThread.TAG_CORE_THREAD_NAME));
                if (thread != null) {
                    restore(thread, entry, resolve);
                }
            }
        }
        if (tag.contains("threadList")) {
            net.minecraft.nbt.ListTag list = tag.getList("threadList", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FactoryThread thread = new FactoryThread(this.createdOrdinary++);
                restore(thread, list.getCompound(i), resolve);
                this.recipeThreads.add(thread);
            }
        }
    }

    private void restore(FactoryThread thread, CompoundTag entry,
                         java.util.function.Function<ResourceLocation, MachineRecipe> resolve) {
        FactoryThread.SavedCraft saved = FactoryThread.load(entry);
        if (saved == null) {
            return;
        }
        MachineRecipe recipe = resolve.apply(saved.recipeId());
        if (recipe == null) {
            return;
        }
        ActiveCraft craft = new ActiveCraft(recipe, 1);
        craft.setProgress(saved.progress());
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

        private int progress;
        private int duration;

        ActiveCraft(MachineRecipe recipe, int parallelism) {
            this.recipe = recipe;
            this.recipeId = recipe.getId();
            this.parallelism = Math.max(1, parallelism);
            this.duration = Math.max(1, recipe.recipeTime());
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

        void advance() {
            this.progress++;
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
            return this.recipeId + " " + this.progress + "/" + this.duration + " x" + this.parallelism;
        }
    }
}
