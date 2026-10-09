package com.reborn.modularmachinery.factory;

import com.reborn.modularmachinery.machine.MachineDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The numbers and the naming rules of the factory's thread model, in one place so the offline acceptance
 * harness can assert them without a world (the same reason {@code ParallelismLimit} is a pure function).
 *
 * <h2>What this slice's "threads" are — and are not</h2>
 *
 * <p>The original's own class comment says it plainly ({@code FactoryRecipeThread.java:33-38}):
 * <em>"不是真正意义上的线程 / Not a thread in the true sense of the word"</em>. A thread there is a
 * <b>recipe-running state slot</b>: its own active recipe, its own crafting context, its own frozen
 * parallelism, its own progress counter. Nothing on it runs concurrently — every one of them is ticked in a
 * loop from the server thread ({@code TileFactoryController#doThreadRecipeTick}).
 *
 * <p>So the factory's contribution is <b>not</b> a thread pool, and it is not the same as parallelism:
 *
 * <ul>
 *   <li><b>parallelism</b> (M6c/M6d) is <i>one</i> recipe settled {@code N} times in one settlement: the
 *       requirements multiply their amounts by {@code N} and the whole thing takes one duration.</li>
 *   <li><b>a factory thread</b> is <i>one</i> recipe settled <i>once</i>; several threads let a single
 *       controller settle several <b>different</b> recipes at the same time. Each thread has its own progress
 *       bar, its own inputs already paid, and its own completion.</li>
 * </ul>
 *
 * <p>This project keeps the original's synchronous model on purpose. The scoping document's risk table
 * (分册 §5.1) rates M6e "high" precisely because the original ran recipe search in a {@code ForkJoinTask} and
 * warns that a synchronous state machine is "语义等价 / semantically equivalent" and acceptable. The engine
 * therefore has no executor, no shared queue and no synchronisation: {@link FactoryEngine#tick} runs the slots
 * in order, in the calling thread, and each slot's whole lifecycle ({@code canStart} / {@code start} / tick /
 * {@code canFinish} / {@code finish}) happens inside that call. That also removes the original's
 * {@code SequentialTaskExecutor} / {@code waitToExecute} / search-timeout machinery entirely — there is no
 * asynchronous result to wait for.
 */
public final class FactoryThreadModel {

    /**
     * How many factory threads a machine has unless its definition says otherwise — the original's
     * {@code Config.defaultFactoryMaxThread}, which {@code AbstractMachine} initialised {@code maxThreads}
     * from ({@code AbstractMachine.java:24}).
     *
     * <p>M6e-2 moved the number into the config ({@code factory-system.default-factory-max-thread}), so this is
     * the value that key contributes rather than a literal. {@code MachineDefinition.DEFAULT_MAX_THREADS} reads
     * the same key, so the loader's fallback and the model's fallback cannot drift apart.
     *
     * <p>The original's field is initialised to {@code 20} but its config read defaults to {@code 10} and
     * overwrites the field, so {@code 10} is the observable default; see
     * {@link com.reborn.modularmachinery.config.ModConfig#factoryDefaultMaxThread()}.
     */
    public static final int DEFAULT_MAX_THREADS =
            com.reborn.modularmachinery.config.ModConfig.factoryDefaultMaxThread();

    /**
     * The original's {@code Config.enableFactoryControllerByDefault} ({@code Config.java:38-47}), which
     * initialised {@code AbstractMachine.hasFactory} ({@code AbstractMachine.java:27}).
     *
     * <p>It defaults to {@code false} in the original as well, so "a machine has a factory only when its
     * definition says so" is the faithful reading. M6e-2 made it the config key
     * {@code factory-system.enable-factory-controller-bydefault}; this constant is what that key contributes.
     */
    public static final boolean DEFAULT_HAS_FACTORY =
            com.reborn.modularmachinery.config.ModConfig.factoryControllerEnabledByDefault();

    /**
     * How long an idle ordinary thread lives before it is dropped, in ticks — the original's
     * {@code FactoryRecipeThread.IDLE_TIME_OUT = 200} (ten seconds).
     */
    public static final int IDLE_TIME_OUT = 200;

    /** How often the idle sweep runs, the original's {@code ticksExisted % 20 != 0} guard. */
    public static final int IDLE_SWEEP_INTERVAL = 20;

    /** The original's key for the factory's thread count, {@code MachineModifier#setMaxThreads}. */
    public static final String JSON_MAX_THREADS = "max-threads";
    /** The original's key for {@code factoryOnly}, {@code DynamicMachinePreDeserializer:85-89}. */
    public static final String JSON_FACTORY_ONLY = "factory-only";
    /** The original's key for {@code hasFactory}, {@code DynamicMachinePreDeserializer:77-83}. */
    public static final String JSON_HAS_FACTORY = "has-factory";
    /** This project's root field for the core-thread preset; the original only had a CraftTweaker entry point. */
    public static final String JSON_CORE_THREADS = "core-threads";

    /**
     * {@code max-threads}: how many <b>ordinary</b> threads a factory controller may hold — the original's
     * {@code AbstractMachine#getMaxThreads} ({@code AbstractMachine.java:133-135}).
     *
     * <p>This is the <b>definition's</b> half of the original's total. The original's
     * {@code TileFactoryController#getMaxThreads} then added the controller's own {@code extraThreadCount} to it
     * ({@code TileFactoryController.java:455-457}), which is why that addition lives on the controller
     * ({@link com.reborn.modularmachinery.block.MachineControllerBlockEntity}) rather than here: a definition is
     * immutable, and the extra count is per-controller runtime state that survives a save.
     *
     * <p>Callers asking "how many threads may this controller hold" must therefore add the two; a caller asking
     * "what did the pack author declare" wants this method alone.
     */
    public static int maxThreads(MachineDefinition definition) {
        if (definition == null) {
            return DEFAULT_MAX_THREADS;
        }
        return Math.max(0, definition.maxThreads());
    }

    /**
     * {@code has-factory}: whether this machine may run on a factory controller at all.
     *
     * <p>Both halves have to agree, and that is exactly what makes the harness's negative check meaningful: a
     * factory controller <b>block</b> carries the flag (so it is always a factory), and it also only forms
     * machines whose definition carries the flag. Take either away and there is no factory.
     */
    public static boolean allowsFactory(MachineDefinition definition) {
        return definition != null && definition.hasFactory();
    }

    /** The whole decision, in one place so the harness drives production code rather than a copy of it. */
    public static boolean factoryEnabled(boolean blockIsFactory, MachineDefinition definition) {
        return blockIsFactory && allowsFactory(definition);
    }

    /** How many threads one factory may hold: the core preset plus {@link #maxThreads}. */
    public static int totalThreads(MachineDefinition definition, List<CoreThreadSpec> coreThreads) {
        return (coreThreads == null ? 0 : coreThreads.size()) + maxThreads(definition);
    }

    private FactoryThreadModel() {
    }

    /**
     * The number a thread shows in the factory screen, the original's {@code GuiFactoryController:268-280}
     * listing "线程 #%s" for each ordinary slot in list order.
     *
     * <p>It is derived from the thread's position when the engine created it and then kept, rather than being
     * recomputed from the current index: threads leave the list (idle time-out, structure change), and a
     * renumbering label would quietly lie about which slot the player is looking at.
     */
    public static int ordinaryThreadNumber(int creationOrdinal) {
        return creationOrdinal + 1;
    }

    /**
     * A core thread's preset: the name it is keyed by and the fixed recipe set it may run — the original's
     * {@code DynamicMachine#coreThreadPreset} plus {@code FactoryRecipeThread#recipeSet}
     * ({@code FactoryRecipeThread.java:44, 54-66}).
     *
     * <p>An empty {@code recipes} list means "no fixed set", which the original read as
     * {@code RecipeRegistry.getRecipesFor(machine)} ({@code FactoryRecipeThread.java:163}). A non-empty list
     * pins the thread to those recipes and nothing else.
     */
    public record CoreThreadSpec(String name, List<ResourceLocation> recipes) {

        public CoreThreadSpec {
            recipes = List.copyOf(recipes);
        }
    }
}
