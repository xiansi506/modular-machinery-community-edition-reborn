package com.reborn.modularmachinery.factory;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.machine.FailureAction;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.RecipeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Everything {@link FactoryEngine} needs from the machine it is running in.
 *
 * <p>The engine is deliberately ignorant of blocks, levels and block entities, for one reason: this project's
 * acceptance rule is that a claim must be provable <b>offline</b>. The controller implements this interface
 * against its formed structure; the offline harness implements it against in-memory ports, and both drive the
 * very same {@link FactoryEngine}. Nothing in this package imports {@code net.minecraft.world.level.Level}.
 *
 * <p>Every method is called on the server thread, from inside {@link FactoryEngine#tick}: the engine has no
 * concurrency of its own (see {@link FactoryThreadModel}).
 */
public interface FactoryHost {
    default RecipeModifiers scriptModifiers() { return RecipeModifiers.EMPTY; }

    /**
     * The controller this engine runs in — C2 收尾（0.89.0）：工厂配方事件的载荷需要一个
     * {@code MachineControllerBlockEntity}（{@code RecipeEvent} 的基字段）。离线复现器返回事件层
     * 已在用的 Unsafe 分配控制器即可；这里只需要它做事件载荷，不触碰世界。
     */
    MachineControllerBlockEntity controller();

    /**
     * The machine's recipe modifiers, recomputed with the structure — the controller's
     * {@code MachineControllerBlockEntity#modifiers()}, i.e. the structure modifiers joined with what the
     * upgrade buses contribute. Read fresh on each use, because a structure change may replace it mid-life.
     */
    RecipeModifiers modifiers();

    /**
     * The ports of the formed structure. One machine has one set of hatches; every thread draws from and
     * deposits into the same ones, exactly as the original's threads all shared
     * {@code foundComponents} / {@code foundHatches}.
     */
    HatchCollection ports();

    /**
     * The core-thread preset the formed machine declares, in declaration order.
     *
     * <p>An empty list is the common case: a machine with no {@code core-threads} has no permanently resident
     * slots and grows ordinary ones on demand.
     */
    List<FactoryThreadModel.CoreThreadSpec> coreThreads();

    /**
     * Current recipe definitions bound to this machine, in recipe priority/weight order. The engine filters
     * recipes already assigned to threads and checks live resources itself; this list may be cached until reload.
     */
    List<MachineRecipe> availableRecipes();

    @Nullable
    default ResourceLocation lockedRecipe() {
        return null;
    }

    default boolean allowsNewRecipe(ResourceLocation id) {
        ResourceLocation locked = lockedRecipe();
        return locked == null || locked.equals(id);
    }

    /** Current definition for a new batch, or null if removed. An already paid craft owns its execution copy.
     * Hosts that prefilter candidates should override this lookup to include definitions currently in progress. */
    @Nullable
    default MachineRecipe currentRecipe(ResourceLocation id) {
        return availableRecipes().stream().filter(recipe -> recipe.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * The machine's parallelism ceiling for a fresh, thread-less settlement: the machine's own contribution
     * plus every parallel controller in the structure, clamped by {@code max-parallelism} and floored at 1 —
     * {@link com.reborn.modularmachinery.machine.ParallelismLimit#resolve} fed by the controller's
     * {@code ParallelControllerCollection}.
     *
     * <p>It is a ceiling <b>before</b> threads: the engine turns it into a per-thread number through
     * {@link FactoryEngine#availableParallelism}, which is where the original did the same subtraction
     * ({@code getAvailableParallelism()}, {@code TileFactoryController.java:400-417}).
     */
    int parallelCeiling();

    /** How many ordinary threads this factory may hold — the machine's {@code max-threads}. */
    int maxThreads();

    /**
     * The formed machine's {@code failure-action}: what one tick that cannot serve a per-tick requirement costs
     * a running craft.
     *
     * <p>The original read it from the machine at the point of use ({@code ActiveMachineRecipe#tick:90-94},
     * {@code machine.getFailureAction()}), and a factory thread ticks the same {@code ActiveMachineRecipe}, so
     * the same field governs both engines here too. It is asked of the host for the same reason
     * {@link #maxThreads()} is: the engine has no machine definition and deliberately no {@code Level}.
     */
    FailureAction failureAction();

    /** The level's random source, for the same chance rolls the single-craft path uses. */
    RandomSource random();

    /** {@link #runRecipeCommands} phase for the recipe's {@code startCommands}. */
    int PHASE_START = 0;

    /** {@link #runRecipeCommands} phase for the recipe's {@code processingCommands}. */
    int PHASE_PROCESSING = 1;

    /** {@link #runRecipeCommands} phase for the recipe's {@code finishCommands}. */
    int PHASE_FINISH = 2;

    /**
     * Runs one of the recipe's three command lists for a factory thread.
     *
     * <p>The engine deliberately has no {@code Level}, so it cannot build a command source; the host does it
     * exactly as the single-craft path does. {@code phase} is one of {@link #PHASE_START},
     * {@link #PHASE_PROCESSING}, {@link #PHASE_FINISH}. The tick is the thread's progress <b>before</b> the
     * current successful tick was counted, which is what the original's {@code ioTick} ordering produced.
     *
     * <p>The phase is an {@code int} rather than the recipe package's enum so that this interface — and every
     * host implementing it, including the offline harness — keeps compiling against a source tree that does not
     * yet have the command layer; the red run depends on exactly that.
     */
    void runRecipeCommands(FactoryThread thread, MachineRecipe recipe, int phase, int tick);

    /**
     * A thread started a recipe: its inputs have just been consumed. The controller marks itself dirty and
     * refreshes what the client sees, which is the original's {@code onThreadRecipeStart}
     * ({@code TileFactoryController.java:274-284}).
     */
    void onThreadStarted(FactoryThread thread);

    /**
     * A thread finished a recipe: outputs have just been deposited. The original's {@code onThreadRecipeFinished}
     * ({@code TileFactoryController.java:304-311}) marked for update and did nothing else — in particular it did
     * <b>not</b> restart anything; the restart happens in the thread's own {@code onFinished} before this fires.
     */
    void onThreadFinished(FactoryThread thread);

    /**
     * A thread could not restart its recipe and has gone idle. Not an event in the original — there,
     * {@code FactoryRecipeThread#tryRestartRecipe} simply nulled the active recipe and requested a new search
     * ({@code FactoryRecipeThread.java:142-159}) — but the controller has to persist the change.
     */
    void onThreadIdle(FactoryThread thread);

    /**
     * The engine's list of what is running changed: a thread was created, removed, started or finished.
     *
     * <p>This is the original's {@code markNoUpdateSync()} / {@code markForUpdateSync()} pair seen from the
     * engine's side, plus the maintenance of {@code controllerStatus} and of the per-tick parallelism ledger.
     * It is called once per engine tick at most.
     */
    void onThreadsChanged();
}
