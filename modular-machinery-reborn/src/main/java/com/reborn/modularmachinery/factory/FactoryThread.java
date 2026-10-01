package com.reborn.modularmachinery.factory;

import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * One factory thread: a recipe-running state slot, the equivalent of the original's {@code FactoryRecipeThread}
 * (291 lines) reduced to what actually runs.
 *
 * <h2>What the original's class comment claims, and what its code does</h2>
 *
 * <p>The comment says <em>"不是真正意义上的线程 / Not a thread in the true sense of the word"</em>
 * ({@code FactoryRecipeThread.java:33-38}) and the code backs that up completely. {@code FactoryRecipeThread}
 * extends {@code RecipeThread}, and its whole surface is state:
 *
 * <ul>
 *   <li>{@code activeRecipe} / {@code status} / {@code permanentModifiers} / {@code semiPermanentModifiers} /
 *       {@code searchTask} belong to the parent;</li>
 *   <li>the factory adds {@code isCoreThread}, {@code threadName}, {@code recipeSet} and {@code idleTime};</li>
 *   <li>the only method with a loop in it is {@code addRecipe}'s {@code recipeSet.add};</li>
 *   <li>{@code onTick()} is one call into {@code activeRecipe.tick} — no {@code Thread}, no executor, no
 *       {@code synchronized}, not even a {@code volatile}.</li>
 * </ul>
 *
 * <p>The name "thread" is a metaphor for "independent progress bar", and this class keeps the metaphor while
 * dropping the misleading machinery. Two of the parent's five modifier maps are also dropped:
 * {@code semiPermanentModifiers} existed so a CraftTweaker callback could inject a modifier for one craft
 * ({@code MachineUpgradeBuilder#addModifier} hung a {@code MachineTickEvent} on it), and this project has no
 * script bridge (D7). {@code permanentModifiers} is kept because core-thread presets can carry it.
 *
 * <h2>Lifecycle, as {@link FactoryEngine} drives it</h2>
 *
 * <ol>
 *   <li>idle — no {@link #active} recipe;</li>
 *   <li>{@link #begin} — freeze this thread's parallelism, run the recipe's {@code canStart}/{@code canTick}/
 *       {@code start}, and if that succeeds become running;</li>
 *   <li>{@link #tick} once per engine tick — the same call order the single-craft path uses
 *       ({@code canFinish} gate, {@code canTick}, {@code tick}), advancing {@link #progress};</li>
 *   <li>on reaching the duration, {@code canFinish} + {@code finish}, then <b>immediately try to restart the
 *       same recipe</b> — the original's {@code RecipeThread#onFinished} ends with
 *       {@code tryRestartRecipe()} ({@code RecipeThread.java:85}), which is why a factory with enough inputs
 *       never shows a thread going idle between two crafts;</li>
 *   <li>if the restart fails, become idle and let the engine assign a different recipe later.</li>
 * </ol>
 *
 * <p>A core thread additionally has a fixed {@link #recipeSet}; an ordinary one runs whatever the engine
 * offers it. That is the whole difference, and it is the original's too
 * ({@code FactoryRecipeThread#createRecipeSearchTask} passes {@code recipeSet} for a core thread and the
 * machine's full registry for an ordinary one, {@code FactoryRecipeThread.java:161-172}).
 */
public final class FactoryThread {

    /** The original's NBT key for a thread's crafting status. */
    public static final String TAG_STATUS = "status";
    /** The original's NBT key for a core thread's name. */
    public static final String TAG_CORE_THREAD_NAME = "coreThreadName";
    /** The original's NBT key for the running recipe. */
    public static final String TAG_ACTIVE_RECIPE = "activeRecipe";
    /** The original's NBT key for the progress within it. */
    public static final String TAG_PROGRESS = "progress";

    private final boolean coreThread;
    @Nullable
    private final String threadName;
    private final int ordinal;

    /** The fixed recipe set of a core thread; empty means "anything the machine offers". */
    private Set<ResourceLocation> recipeSet = Set.of();

    private int idleTime;

    @Nullable
    private FactoryEngine.ActiveCraft active;

    /** Core thread, created from the machine's {@code core-threads} preset. */
    FactoryThread(FactoryThreadModel.CoreThreadSpec spec, int ordinal) {
        this.coreThread = true;
        this.threadName = spec.name();
        this.ordinal = ordinal;
        this.recipeSet = Set.copyOf(spec.recipes());
    }

    /** Ordinary thread, created on demand when a recipe has nowhere to go. */
    FactoryThread(int ordinal) {
        this.coreThread = false;
        this.threadName = null;
        this.ordinal = ordinal;
    }

    // ------------------------------------------------------------ identity

    /** {@code true} for a thread that always exists, the original's {@code isCoreThread}. */
    public boolean isCoreThread() {
        return this.coreThread;
    }

    /**
     * The core thread's name, or {@code null} for an ordinary one — the original's {@code threadName}, which
     * was only ever set for a core thread ({@code FactoryRecipeThread.java:180-182}).
     */
    @Nullable
    public String threadName() {
        return this.threadName;
    }

    /** The ordinary thread's number, as the screen shows it, or {@code 0} for a core thread. */
    public int threadNumber() {
        return this.coreThread ? 0 : FactoryThreadModel.ordinaryThreadNumber(this.ordinal);
    }

    /** A stable label for diagnostics, log lines and NBT: the name for a core thread, {@code #n} otherwise. */
    public String label() {
        return this.coreThread ? "core:" + this.threadName : "#" + threadNumber();
    }

    /** The fixed recipe set of a core thread; empty when the thread may run anything the machine offers. */
    public Set<ResourceLocation> recipeSet() {
        return this.recipeSet;
    }

    void setRecipeSet(Set<ResourceLocation> recipes) {
        this.recipeSet = Set.copyOf(recipes);
    }

    /** {@code true} while this thread holds a recipe, the original's {@code !isIdle()}. */
    public boolean isWorking() {
        return this.active != null;
    }

    /** The original's {@code isIdle()}: no active recipe. */
    public boolean isIdle() {
        return this.active == null;
    }

    /** How many ticks this thread has been idle, the original's {@code idleTime}, swept at 200. */
    public int idleTime() {
        return this.idleTime;
    }

    // ------------------------------------------------------------ the running craft

    @Nullable
    public FactoryEngine.ActiveCraft active() {
        return this.active;
    }

    /** The running recipe's id, or {@code null} when idle. */
    @Nullable
    public ResourceLocation recipeId() {
        return this.active == null ? null : this.active.recipeId();
    }

    /** The running recipe, or {@code null} when idle. */
    @Nullable
    public MachineRecipe recipe() {
        return this.active == null ? null : this.active.recipe();
    }

    /** Progress within the running craft, the original's {@code ActiveMachineRecipe#getTick}. */
    public int progress() {
        return this.active == null ? 0 : this.active.progress();
    }

    /** How long the running craft takes, after duration modifiers. */
    public int duration() {
        return this.active == null ? 0 : this.active.duration();
    }

    /**
     * The copies this thread settles per settlement — frozen when it started, the original's
     * {@code ActiveMachineRecipe#getParallelism}.
     *
     * <p><b>Why it lives here and not on the recipe.</b> The original held an {@code ActiveMachineRecipe}
     * <i>per thread</i>, so the number was per thread by construction. This project's {@code MachineRecipe} is
     * a shared, immutable registry object and its {@code parallelism} is stored on the requirement instances,
     * which means two threads settling two recipes are fine but the same recipe settled twice would not be.
     * {@link FactoryEngine#begin} therefore refuses to hand one recipe to two threads at once, and this field
     * is what the engine's ledger and the diagnostics read rather than asking the recipe back.
     */
    public int parallelism() {
        return this.active == null ? 0 : this.active.parallelism();
    }

    /** Fraction of the craft completed, for progress reporting. */
    public float fraction() {
        if (this.active == null || this.active.duration() <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, this.active.progress() / (float) this.active.duration());
    }

    // ------------------------------------------------------------ engine-driven transitions

    /**
     * Arms a craft and runs its start phase, in the order the single-craft path uses:
     * {@code canStart} → {@code canTick} → {@code start}.
     *
     * <p><b>Why the slot is armed before {@code start}.</b> {@code start} is what consumes a craft's one-shot
     * inputs, so if it were allowed to half-succeed the thread would be idle while holding paid inputs. Arming
     * the slot first means every outcome after that point is observable: {@link #commit} keeps a start that
     * worked, and {@link #clear} drops one that did not. The pre-checks run first, so the common "cannot start"
     * answer costs no consumption at all.
     */
    boolean begin(FactoryEngine.ActiveCraft craft, HatchCollection ports, RecipeModifiers modifiers,
                  RandomSource random) {
        MachineRecipe recipe = craft.recipe();
        int parallelism = Math.max(1, craft.parallelism());
        recipe.setParallelism(parallelism);
        recipe.applyDurationMultiplier(modifiers);
        if (!recipe.canStart(ports, modifiers) || !recipe.canTick(ports, modifiers)) {
            return false;
        }
        this.active = craft;
        this.idleTime = 0;
        if (!recipe.start(ports, random, modifiers)) {
            this.active = null;
            return false;
        }
        craft.withDuration(recipe.duration(modifiers));
        return true;
    }

    /** Re-arms the same recipe for another settlement, after one finished. */
    void restart(FactoryEngine.ActiveCraft craft) {
        this.active = craft;
        this.idleTime = 0;
    }

    /** Empties the slot. The engine decides whether that means "search again" or "go away". */
    void clear() {
        this.active = null;
        this.idleTime = 0;
    }

    /**
     * The sweep's condition, the original's
     * {@code thread.isIdle() && thread.idleTime >= IDLE_TIME_OUT} ({@code TileFactoryController.java:518}).
     */
    boolean expired() {
        return this.active == null && this.idleTime >= FactoryThreadModel.IDLE_TIME_OUT;
    }

    void age() {
        this.idleTime++;
    }

    // ------------------------------------------------------------ persistence

    /**
     * Mirrors the original's {@code FactoryRecipeThread#serialize} in shape — {@code status},
     * {@code activeRecipe}, {@code coreThreadName} — with this project's simpler craft representation: the
     * original stored a whole {@code ActiveMachineRecipe} (its tick, its parallelism, its per-requirement
     * state), while here the recipe is a registry object looked up by id and the only per-thread state is the
     * progress and the frozen parallelism.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_STATUS, this.active == null ? "idle" : "crafting");
        if (this.coreThread && this.threadName != null) {
            tag.putString(TAG_CORE_THREAD_NAME, this.threadName);
        }
        if (this.active != null) {
            tag.putString(TAG_ACTIVE_RECIPE, this.active.recipeId().toString());
            tag.putInt(TAG_PROGRESS, this.active.progress());
        }
        return tag;
    }

    /** The recipe id and progress a saved tag names, or {@code null} when the thread was idle. */
    @Nullable
    static SavedCraft load(CompoundTag tag) {
        if (!tag.contains(TAG_ACTIVE_RECIPE)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_ACTIVE_RECIPE));
        if (id == null) {
            return null;
        }
        return new SavedCraft(id, Math.max(0, tag.getInt(TAG_PROGRESS)));
    }

    /** What a saved thread tag carries: which recipe and how far into it. */
    record SavedCraft(ResourceLocation recipeId, int progress) {
    }

    /** Length of the recipe list this thread is restricted to, for diagnostics. */
    public int recipeSetSize() {
        return this.recipeSet.size();
    }

    /** Whether {@code recipe} is one this thread is allowed to run. An empty set allows everything. */
    public boolean accepts(MachineRecipe recipe) {
        return this.recipeSet.isEmpty() || this.recipeSet.contains(recipe.getId());
    }

    /** The recipes this thread may run, out of a candidate list. */
    List<MachineRecipe> filter(List<MachineRecipe> candidates) {
        if (this.recipeSet.isEmpty()) {
            return candidates;
        }
        java.util.List<MachineRecipe> out = new java.util.ArrayList<>(candidates.size());
        for (MachineRecipe recipe : candidates) {
            if (this.recipeSet.contains(recipe.getId())) {
                out.add(recipe);
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "FactoryThread[" + label() + (this.active == null
                ? ", idle " + this.idleTime + "t"
                : ", " + this.active.recipeId() + " " + this.active.progress() + "/" + this.active.duration()
                        + " x" + this.active.parallelism()) + "]";
    }
}
