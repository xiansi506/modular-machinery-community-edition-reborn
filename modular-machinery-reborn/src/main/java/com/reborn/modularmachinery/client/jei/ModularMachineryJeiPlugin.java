package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.item.BlueprintItem;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI registration for this mod, reproducing the original's {@code ModIntegrationJEI} shape: <b>one recipe
 * category per machine</b>, titled with that machine's localized name, each with its own recipe type and its own
 * catalysts.
 *
 * <p>The original did exactly this. {@code ModIntegrationJEI#registerCategories} looped over
 * {@code MachineRegistry.getRegistry()} and added a {@code CategoryDynamicRecipe} per machine;
 * {@code #register} gave each machine its recipes under
 * {@code "modularmachinery.recipes." + machine.getRegistryName().getPath()}; and each machine's own controller
 * block, plus a blueprint bound to it, became that category's catalysts.
 *
 * <p><b>Why this is possible in JEI 15.49, contrary to what 0.16.2 assumed.</b> A category's identity is fixed
 * when the plugin's {@code registerCategories} runs, but that does not happen at mod-construction or plugin
 * discovery time. {@code mezz.jei.forge.startup.StartEventObserver} holds a {@code LISTENING} state and only
 * transitions to {@code JEI_STARTED} -- which runs {@code startRunnable}, i.e. {@code JeiStarter#start()} then
 * {@code PluginLoader.createRecipeCategories} then this method -- from a
 * {@code ClientPlayerNetworkEvent.LoggingIn}, and when {@code shouldWaitForRecipes()} is true, only once the
 * matching {@code RecipesUpdatedEvent} has also arrived. Both come far later than the resource reload that fills
 * {@link MachineRegistry} through {@code MachineLoader}. A game log confirms the ordering in practice: this mod
 * logged {@code Loaded 1 machine definition(s)} at 00:18:54 and JEI's plugin loading ran at 00:19:01.
 */
@JeiPlugin
public final class ModularMachineryJeiPlugin implements IModPlugin {

    /**
     * One recipe type per machine, keyed by the machine's registry name.
     *
     * <p>Cached rather than created per call because JEI re-runs the whole plugin pipeline on every recipe update
     * (the {@code restart()} path in {@code StartEventObserver}), and the {@link RecipeType} is what ties a
     * category to its recipes and to its catalysts; handing JEI a fresh equal-but-distinct object each time would
     * be needless churn.
     */
    private static final Map<ResourceLocation, RecipeType<MachineRecipe>> RECIPE_TYPES = new ConcurrentHashMap<>();

    /** The recipe type of one machine; also the id JEI records for that category in its own config files. */
    static RecipeType<MachineRecipe> typeFor(ResourceLocation machineId) {
        return RECIPE_TYPES.computeIfAbsent(machineId, id -> new RecipeType<>(id, MachineRecipe.class));
    }

    /**
     * The machines this JEI start is registering, fixed by {@link #registerCategories}.
     *
     * <p>Held in a field rather than recomputed per callback so that the three callbacks cannot disagree. JEI
     * refuses a recipe whose type has no registered category ({@code RecipeTypeDataMap}: "A recipe category must
     * be registered in order to use this recipe type"), which a client whose machine recipes arrived between two
     * of the calls could otherwise trigger.
     */
    private List<MachineGroup> groups;

    /** One machine, its definition when the client has one, and every recipe of it JEI should be given. */
    private record MachineGroup(ResourceLocation id, MachineDefinition machine, List<MachineRecipe> recipes) {
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ModularMachineryReborn.MOD_ID, "jei_plugin");
    }

    /**
     * A blueprint is machine-specific: the original registered a subtype interpreter returning the machine's
     * registry name ({@code ModIntegrationJEI#registerItemSubtypes}), which is what makes "click this machine's
     * blueprint to see its recipes" work. JEI 15.49's equivalent for NBT-carried identity is this one call.
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.useNbtForSubtypes(ModItems.BLUEPRINT.get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new StructurePreviewCategory(helper));

        // Recomputed on every JEI start and never reused: JEI runs the whole plugin pipeline again after a recipe
        // update, and by then the machine registry the previous list was built from may have been replaced. This
        // callback is always the first of the three, which is what fixes the list the other two consume.
        this.groups = collectGroups();

        // One category per machine, titled with the machine's localized name -- the original's loop.
        for (MachineGroup group : this.groups) {
            registration.addRecipeCategories(
                    new MachineRecipeCategory(helper, group.id(), group.machine(), group.recipes()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        // One structure preview per loaded machine. The registry is filled from the server's reload, so on a
        // dedicated-server client it is empty and this category simply has nothing to show.
        List<MachineDefinition> machines = List.copyOf(MachineRegistry.all());
        if (!machines.isEmpty()) {
            registration.addRecipes(StructurePreviewCategory.TYPE, machines);
        }

        // The per-machine categories come from registerCategories, which JEI always runs first. If it somehow
        // did not, adding a recipe for a type JEI has no category for is a hard error, so nothing is added.
        if (this.groups == null) {
            return;
        }
        for (MachineGroup group : this.groups) {
            if (!group.recipes().isEmpty()) {
                registration.addRecipes(typeFor(group.id()), group.recipes());
            }
        }
    }

    /**
     * Catalysts, per machine, as the original had them: that machine's own controller, and a blueprint bound to
     * it. The original's generic controller was a catalyst for the <b>structure preview</b> category only, and
     * that is kept here.
     *
     * <p>One honest difference: a machine declared by a data pack alone has no controller block of its own, and
     * the generic controller really can form it, so the generic controller stands in as that machine's catalyst
     * rather than leaving its recipes with no way in.
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (RegistryObject<Item> controller : ModBlocks.controllerItems()) {
            registration.addRecipeCatalyst(new ItemStack(controller.get()), StructurePreviewCategory.TYPE);
        }
        registration.addRecipeCatalyst(new ItemStack(ModItems.BLUEPRINT.get()), StructurePreviewCategory.TYPE);

        if (this.groups == null) {
            return;
        }
        for (MachineGroup group : this.groups) {
            RecipeType<MachineRecipe> type = typeFor(group.id());
            registration.addRecipeCatalyst(new ItemStack(ModBlocks.controllerItemFor(group.id())), type);
            // A blueprint bound to this machine, as the original registered one per machine
            // (ItemBlueprint.setAssociatedMachine(stack, machine)); registerItemSubtypes keeps them distinct.
            registration.addRecipeCatalyst(BlueprintItem.forMachine(ModItems.BLUEPRINT.get(), group.id()), type);
        }
    }

    /**
     * The machines JEI gets a category for, in registry order, each with its own recipes.
     *
     * <p>Two sources, because a client may have only one of them: {@link MachineRegistry}, which
     * {@code MachineLoader} fills from the config directory and the data pack, and the machine names carried by
     * the recipes the server synced. A dedicated-server client has no machine definitions at all, so without the
     * second source it would get no machine recipe pages whatsoever.
     */
    private static List<MachineGroup> collectGroups() {
        Map<ResourceLocation, MachineDefinition> definitions = new LinkedHashMap<>();
        for (MachineDefinition machine : MachineRegistry.all()) {
            definitions.put(machine.id(), machine);
        }

        Map<ResourceLocation, List<MachineRecipe>> byMachine = new LinkedHashMap<>();
        for (MachineRecipe recipe : machineRecipes()) {
            byMachine.computeIfAbsent(recipe.machineId(), id -> new ArrayList<>()).add(recipe);
        }

        List<MachineGroup> groups = new ArrayList<>(definitions.size() + byMachine.size());
        for (Map.Entry<ResourceLocation, MachineDefinition> entry : definitions.entrySet()) {
            groups.add(new MachineGroup(entry.getKey(), entry.getValue(),
                    List.copyOf(byMachine.getOrDefault(entry.getKey(), List.of()))));
        }
        for (Map.Entry<ResourceLocation, List<MachineRecipe>> entry : byMachine.entrySet()) {
            if (!definitions.containsKey(entry.getKey())) {
                groups.add(new MachineGroup(entry.getKey(), null, List.copyOf(entry.getValue())));
            }
        }
        return groups;
    }

    private static List<MachineRecipe> machineRecipes() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MACHINE.get());
    }
}
