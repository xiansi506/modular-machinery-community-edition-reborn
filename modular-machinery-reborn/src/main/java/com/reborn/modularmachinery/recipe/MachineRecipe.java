package com.reborn.modularmachinery.recipe;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine recipe: a machine name, a duration, and a list of typed requirements.
 *
 * <p>This is the original schema, not the flat {@code input}/{@code output}/{@code duration}/{@code energy}
 * shortcut of 0.4–0.7:
 *
 * <pre>{@code
 * {
 *   "machine": "alloy_furnace",
 *   "registryName": "alloy_furnace_diamond",
 *   "recipeTime": 5,
 *   "requirements": [
 *     { "type": "...:energy", "io-type": "input", "energyPerTick": 100 },
 *     { "type": "...:item",   "io-type": "input", "item": "minecraft:coal", "amount": 32 },
 *     { "type": "...:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
 *   ]
 * }
 * }</pre>
 *
 * <p>The phase split follows the original lifecycle: {@link #start} consumes inputs, {@link #tick} runs the
 * per-tick requirements, and {@link #finish} produces outputs while rolling each output's chance
 * independently.
 *
 * <h2>Parallelism and modifiers</h2>
 *
 * <p>Both live on this class because both are properties of a <i>settlement</i> rather than of a requirement:
 *
 * <ul>
 *   <li>{@link #parallelism(HatchCollection, RecipeModifiers, int)} reproduces
 *       {@code RecipeCraftingContext#getMaxParallelism} — start from this recipe's own limit, then take the
 *       minimum over every requirement that can be parallelised, returning {@code 0} the moment one reports it
 *       cannot serve even a single copy.</li>
 *   <li>Every lifecycle method takes the machine's {@link RecipeModifiers} so requirement values and chances
 *       can be modified, which is what the original did by hanging a {@code RecipeCraftingContext} off the
 *       craft. The zero-argument overloads remain for callers with no machine attached (JEI, the recipe book,
 *       the plain-container check) and behave exactly as before M6c.</li>
 * </ul>
 *
 * <p>A craft freezes its parallelism when it starts, which is why {@link #setParallelism} exists separately:
 * the original's {@code RecipeCraftingContext#setParallelism} did the same, and only revisited it from
 * {@code canRestartCrafting}.
 */
public final class MachineRecipe implements Recipe<Container> {

    /**
     * The implicit limit of a recipe that does not set {@code max-parallelism}.
     *
     * <p>The original had no per-recipe parallelism field at all: a recipe's ceiling was the machine's
     * {@code getMaxParallelism()}, and only a CraftTweaker script could lower it through
     * {@code ActiveMachineRecipe#setMaxParallelism}. {@code 0} here means "no opinion — use the machine's",
     * which is the faithful default and keeps every existing recipe behaving exactly as it did.
     */
    public static final int MACHINE_LIMIT = 0;

    private final ResourceLocation id;
    private final String machine;
    private final String registryName;
    private final int recipeTime;
    private final List<MachineRequirement> requirements;

    /** This recipe's own parallelism ceiling, or {@link #MACHINE_LIMIT} to defer to the machine's. */
    private final int maxParallelism;

    private ResourceLocation machineId;

    public MachineRecipe(ResourceLocation id, String machine, String registryName, int recipeTime,
                         List<MachineRequirement> requirements) {
        this(id, machine, registryName, recipeTime, requirements, MACHINE_LIMIT);
    }

    public MachineRecipe(ResourceLocation id, String machine, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism) {
        this.id = id;
        this.machine = machine;
        this.registryName = registryName;
        this.recipeTime = Math.max(1, recipeTime);
        this.requirements = List.copyOf(requirements);
        this.maxParallelism = Math.max(0, maxParallelism);
    }

    public String machine() {
        return this.machine;
    }

    /** The {@code registryName} the original recipe file carried, or the file path when absent. */
    public String registryName() {
        return this.registryName;
    }

    /**
     * The {@code machine} field resolved to a registry name.
     *
     * <p>Accepts either a full resource location ({@code modular_machinery_reborn:transformer}) or a bare path
     * ({@code transformer}), which is prefixed with this mod's namespace. The result is compared against the
     * machines in {@code MachineRegistry}.
     */
    public ResourceLocation machineId() {
        if (this.machineId == null) {
            ResourceLocation parsed = this.machine.indexOf(':') >= 0 ? ResourceLocation.tryParse(this.machine) : null;
            this.machineId = parsed != null
                    ? parsed
                    : new ResourceLocation(ModularMachineryReborn.MOD_ID, this.machine);
        }
        return this.machineId;
    }

    public int recipeTime() {
        return this.recipeTime;
    }

    public List<MachineRequirement> requirements() {
        return this.requirements;
    }

    /** This recipe's own parallelism ceiling, or {@code 0} when it defers to the machine's. */
    public int maxParallelism() {
        return this.maxParallelism;
    }

    public List<MachineRequirement> requirements(IOType ioType) {
        List<MachineRequirement> out = new ArrayList<>();
        for (MachineRequirement requirement : this.requirements) {
            if (requirement.ioType() == ioType) {
                out.add(requirement);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ parallelism

    /**
     * How long one craft takes, after the machine's {@code duration} modifiers.
     *
     * <p>The original recomputed this on every tick ({@code ActiveMachineRecipe#tick}) and again at start, and
     * clamped it to at least one tick — a modifier may legitimately shorten a craft below a tick, in which case
     * {@code calculateExtraParallelism} multiplied the parallelism by {@code 1 / totalTick} instead.
     */
    public int duration(RecipeModifiers modifiers) {
        if (modifiers.isEmpty()) {
            return this.recipeTime;
        }
        double modified = modifiers.applyDuration(this.recipeTime);
        return modified < 1.0 ? 1 : (int) Math.round(modified);
    }

    /**
     * The original's {@code RecipeCraftingContext#getMaxParallelism}: the lowest ceiling any parallelisable
     * requirement is willing to serve, starting from this recipe's own limit.
     *
     * <p>{@code 0} means "cannot be parallelised at all right now", which the original used to fall back to a
     * plain, single-copy craft check. That is the safe reading and it is what the controller does too.
     *
     * @param ceiling the machine's own limit, i.e. the original's {@code activeRecipe.getMaxParallelism()}
     */
    public int parallelism(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        int limit = this.maxParallelism > 0 ? Math.min(ceiling, this.maxParallelism) : ceiling;
        if (limit <= 0) {
            return 0;
        }
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isParallelizable()) {
                continue;
            }
            limit = Math.min(limit, requirement.parallelLimit(ports, modifiers, limit));
            if (limit <= 0) {
                return 0;
            }
        }
        return limit;
    }

    /**
     * Freezes how many copies every requirement settles. Called once, when a craft starts; the original did the
     * same through {@code RecipeCraftingContext#setParallelism}.
     */
    public void setParallelism(int parallelism) {
        for (MachineRequirement requirement : this.requirements) {
            requirement.setParallelism(parallelism);
        }
    }

    /** The copies currently settled per craft, as fixed by the last {@link #setParallelism}. */
    public int parallelism() {
        return this.requirements.isEmpty() ? 1 : this.requirements.get(0).parallelism();
    }

    /** Pushes the shortened-duration cost compensation into every per-tick energy requirement. */
    public void applyDurationMultiplier(RecipeModifiers modifiers) {
        float multiplier = modifiers.isEmpty() ? 1.0F : modifiers.durationMultiplier(this.recipeTime);
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof EnergyRequirement energy) {
                energy.setDurationMultiplier(multiplier);
            }
        }
    }

    // ------------------------------------------------------------ craft lifecycle

    /** All one-shot inputs can be paid right now. */
    public boolean canStart(HatchCollection ports) {
        return canStart(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canStart} with modifiers applied to every requirement. */
    public boolean canStart(HatchCollection ports, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isStartPhase()) {
                continue;
            }
            if (!canSatisfy(requirement, ports, modifiers)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The translation key of the first start-phase requirement that refuses this craft, or {@code null} when
     * every one of them is satisfied.
     *
     * <p>It exists because a boolean cannot say <i>why</i>: the controller used to report a bare
     * {@code idle} for every unmet start requirement, which for {@code interface_number_input} would hide the
     * original's own two messages ({@code component.missing.modularmachinery.interface.number},
     * {@code craftcheck.failure.interface.number.notequal}). The same
     * {@link #canSatisfy(MachineRequirement, HatchCollection, RecipeModifiers)} dispatch is used here, so the
     * message is only ever produced by a requirement that really did answer {@code false} — the two cannot
     * disagree.
     */
    @javax.annotation.Nullable
    public String startFailure(HatchCollection ports, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isStartPhase()) {
                continue;
            }
            if (canSatisfy(requirement, ports, modifiers)) {
                continue;
            }
            String message = requirement.startFailure(ports);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /** Consumes the one-shot inputs. Only call after {@link #canStart}. */
    public boolean start(HatchCollection ports, RandomSource random) {
        return start(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #start} with modifiers applied to every requirement. */
    public boolean start(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isStartPhase()) {
                continue;
            }
            if (!applyPhase(requirement, ports, random, modifiers)) {
                return false;
            }
        }
        return true;
    }

    /** Every per-tick requirement can be served this tick. */
    public boolean canTick(HatchCollection ports) {
        return canTick(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canTick} with modifiers applied to every requirement. */
    public boolean canTick(HatchCollection ports, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isTickPhase()) {
                continue;
            }
            if (!canSatisfy(requirement, ports, modifiers)) {
                return false;
            }
        }
        return true;
    }

    /** Runs the per-tick requirements: energy and per-tick fluids. */
    public boolean tick(HatchCollection ports, RandomSource random) {
        return tick(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #tick} with modifiers applied to every requirement. */
    public boolean tick(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isTickPhase()) {
                continue;
            }
            if (!applyPhase(requirement, ports, random, modifiers)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every output has room for its <b>guaranteed</b> amount. The chance roll is deliberately not applied here:
     * the original checked the finish with {@code ResultChance.GUARANTEED} so that a craft cannot start ending
     * when its possible result would not fit.
     */
    public boolean canFinish(HatchCollection ports) {
        return canFinish(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canFinish} with modifiers applied to every requirement. */
    public boolean canFinish(HatchCollection ports, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isFinishPhase()) {
                continue;
            }
            if (!canSatisfy(requirement, ports, modifiers)) {
                return false;
            }
        }
        return true;
    }

    /** Produces the outputs, rolling each output requirement's chance on its own. */
    public boolean finish(HatchCollection ports, RandomSource random) {
        return finish(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #finish} with modifiers applied to every requirement. */
    public boolean finish(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isFinishPhase()) {
                continue;
            }
            if (!applyPhase(requirement, ports, random, modifiers)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ dispatch

    /**
     * Routes a requirement to its modifier-aware method. The original had one implementation per requirement
     * class reading the context's modifier appliers directly; this mod has to pass the set in, and the
     * {@code instanceof} ladder here is the single place that knows which overload to call.
     */
    private static boolean canSatisfy(MachineRequirement requirement, HatchCollection ports,
                                      RecipeModifiers modifiers) {
        if (requirement instanceof ItemRequirement item) {
            return item.canSatisfy(ports, modifiers);
        }
        if (requirement instanceof FluidRequirement fluid) {
            return fluid.canSatisfy(ports, modifiers);
        }
        if (requirement instanceof EnergyRequirement energy) {
            return energy.canSatisfy(ports, modifiers);
        }
        return requirement.canSatisfy(ports);
    }

    private static boolean applyPhase(MachineRequirement requirement, HatchCollection ports, RandomSource random,
                                      RecipeModifiers modifiers) {
        if (requirement instanceof ItemRequirement item) {
            return item.apply(ports, random, modifiers);
        }
        if (requirement instanceof FluidRequirement fluid) {
            return requirement.isTickPhase()
                    ? fluid.tick(ports, random, modifiers)
                    : fluid.apply(ports, random, modifiers);
        }
        if (requirement instanceof EnergyRequirement energy) {
            return energy.tick(ports, random, modifiers);
        }
        return requirement.isTickPhase()
                ? requirement.tick(ports, random)
                : requirement.apply(ports, random);
    }

    // ------------------------------------------------------------ Recipe<Container>

    /**
     * A weak check over a plain container: hand-written so the recipe is usable through the vanilla interfaces.
     * Real matching goes through {@link #canStart}, which also sees fluids, energy and every port.
     */
    @Override
    public boolean matches(Container container, Level level) {
        for (MachineRequirement requirement : this.requirements) {
            if (!(requirement instanceof ItemRequirement item) || !item.isStartPhase()) {
                continue;
            }
            Ingredient ingredient = item.ingredient();
            if (ingredient == null) {
                continue;
            }
            int found = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    found += stack.getCount();
                }
            }
            if (found < item.amount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return primaryOutput();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return primaryOutput();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof ItemRequirement item && item.isStartPhase() && item.ingredient() != null) {
                list.add(item.ingredient());
            }
        }
        return list;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.MACHINE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.MACHINE.get();
    }

    /** Total FE a craft will draw from energy inputs over its whole duration, before modifiers. */
    public long totalEnergy() {
        long perTick = 0L;
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof EnergyRequirement energy && energy.ioType() == IOType.INPUT) {
                perTick += energy.energyPerTick();
            }
        }
        return perTick * this.recipeTime;
    }

    /** The stack shown as this recipe's result, for JEI and the recipe book. */
    public ItemStack primaryOutput() {
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof ItemRequirement item && item.isFinishPhase()) {
                return item.outputStack();
            }
        }
        return ItemStack.EMPTY;
    }
}
