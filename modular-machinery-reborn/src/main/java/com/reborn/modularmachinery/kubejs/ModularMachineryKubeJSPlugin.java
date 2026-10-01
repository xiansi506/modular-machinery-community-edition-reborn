package com.reborn.modularmachinery.kubejs;

import com.reborn.modularmachinery.ModularMachineryReborn;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.recipe.schema.JsonRecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/**
 * The optional KubeJS bridge. The mod remains loadable when KubeJS is absent — {@code kubejs.plugins.txt} is
 * read by KubeJS, so nothing here is touched otherwise.
 *
 * <p>Two things live here. {@link #registerRecipeSchemas} is the original bridge (0.27.x): a recipe is written
 * in a script with this mod's own JSON shape. {@link #onServerReload} plus {@link #registerBindings} is the
 * 0.28.0 machine entry point. This class is deliberately a thin adapter — the sequence that makes the machine
 * layer correct (clear it, then let the scripts rebuild it) is in {@link MachineEvents}, and the rules a
 * definition has to satisfy are in {@code MachineSchema}, which the data-pack loader uses too.
 *
 * <p><b>The one rule this adapter has that is easy to get wrong</b> is in {@link #registerBindings}: an event
 * group reaches a script <b>wrapped</b>, because it is the wrapper that turns the group's typed event into a
 * callable property. Adding the raw group makes the binding exist (so the script resolves the name and stops
 * with "Cannot find function …" instead) while exposing no usable event at all.
 *
 * <h2>When this runs, and why the machine API depends on it</h2>
 *
 * <p>KubeJS wraps the data pack's resource manager in {@code WorldLoader$PackConfig#createResourceManager},
 * which is the point both a server start and {@code /reload} pass through. Its {@code ServerScriptManager}
 * reevaluates the server scripts there — for {@code /reload} that is the server scripts again — and then calls
 * {@code KubeJSPlugin#onServerReload} on every plugin. Forge only collects the {@code AddReloadListenerEvent}
 * listeners afterwards, inside {@code ReloadableServerResources#loadResources}, so this method has always
 * finished before {@code MachineLoader#apply} runs.
 *
 * <p>That is what makes the machine layer work at all: the loader is the component that performs the registry
 * replacement ({@code MachineRegistry.replace}), so a definition pushed from a script would be overwritten by
 * the next reload unless the loader itself knew about the script layer. It does — it re-reads
 * {@code MachineDefinitions.staged()} on every {@code apply} — and this method's only job is to make sure that
 * layer describes the <b>current</b> scripts before that happens.
 */
public final class ModularMachineryKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        event.namespace(ModularMachineryReborn.MOD_ID).register("machine", JsonRecipeSchema.SCHEMA);
        event.mapRecipe("modular_machinery_reborn:machine", "modular_machinery_reborn:machine");
        ModularMachineryReborn.LOGGER.info("[{}] KubeJS schema registered: event.recipes.modular_machinery_reborn.machine", ModularMachineryReborn.MOD_ID);
    }

    @Override
    public void registerEvents() {
        MachineEvents.register();
    }

    /**
     * Publishes {@code MachineRegistryEvents} to every script type.
     *
     * <p><b>The binding has to be a KubeJS {@code EventGroupWrapper}, not the group itself.</b> A script's
     * property lookup on a raw {@link dev.latvian.mods.kubejs.event.EventGroup} finds nothing — the group's
     * handler map is reached through the wrapper's {@code get(String)} override, and Rhino does not call it on
     * the group — so {@code MachineRegistryEvents.registry} would be {@code undefined} and the documented line
     * would die with {@code TypeError: Cannot find function registry in object MachineRegistryEvents} before a
     * single machine was staged. That is exactly what shipped in 0.28.0.
     *
     * <p>KubeJS's own {@code BuiltinKubeJSPlugin#registerBindings} wraps every <i>registered</i> group this way,
     * and it runs first: KubeJS puts its own mod at the head of the plugin list. {@code BindingsEvent#add} is a
     * plain {@code Context.addToScope} under the same name, so this method used to <b>overwrite</b> the working
     * wrapper with the raw group. Registering the wrapper here is therefore not a repetition — it is what makes
     * the binding correct regardless of which plugin ran last.
     */
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add(MachineEvents.GROUP_NAME, new EventGroupWrapper(event.getType(), MachineEvents.group()));
    }

    /**
     * Called by KubeJS once the server scripts of this reload have been evaluated.
     *
     * <p>{@link MachineEvents#post()} opens a new cycle first and only then lets the scripts define machines, so
     * a script removed since the last reload leaves nothing behind and one that is still there re-registers its
     * machines for the loader that is about to run.
     */
    @Override
    public void onServerReload() {
        MachineEvents.post();
    }
}
