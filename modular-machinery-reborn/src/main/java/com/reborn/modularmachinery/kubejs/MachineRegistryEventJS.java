package com.reborn.modularmachinery.kubejs;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineDefinitions;
import dev.latvian.mods.kubejs.event.EventJS;

/**
 * The event a KubeJS server script listens to in order to define machines.
 *
 * <pre>{@code
 * MachineRegistryEvents.registry(event => {
 *   event.machine('kubejs_furnace')
 *     .localizedName('Scripted Furnace')
 *     .part(1, -1, 0, 'minecraft:stone')
 *     .part(0, 1, 0, 'modular_machinery_reborn:blockcasing[casing=plain]')
 * })
 * }</pre>
 *
 * <p>It is posted from {@code ModularMachineryKubeJSPlugin#onServerReload}, which KubeJS calls after the server
 * scripts have been evaluated and before this mod's loader reads anything — see {@link MachineDefinitions} for
 * the ordering evidence and the merge contract. Because the event is posted on every server start and every
 * {@code /reload}, a script's {@code registry} block runs again each time and the machine layer describes the
 * scripts that exist <b>now</b>; a script that was deleted last time contributes nothing.
 *
 * <p>Registering the same id twice inside one cycle is not an error: the later definition wins, and the load
 * log names the id. Registering an id a data pack or the config directory also defines is the collision the
 * merge contract resolves in the script's favour, again with a warning that names both origins.
 */
public final class MachineRegistryEventJS extends EventJS {

    /** How many machines this event's listeners registered; reported once so a silent zero is visible. */
    private int registered;

    /**
     * Starts a machine definition. The name is its registry name; a bare path such as {@code kubejs_furnace}
     * gets this mod's namespace, exactly as a definition file's {@code registryname} does.
     */
    public MachineBuilderJS machine(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("machine(id) needs the machine's registry name, e.g. "
                    + "event.machine('kubejs_furnace').");
        }
        if (!(id instanceof String) && !(id instanceof CharSequence)) {
            throw new IllegalArgumentException("machine(id) takes the registry name as a string, but a "
                    + id.getClass().getSimpleName() + " was given. Write event.machine('kubejs_furnace').");
        }
        String name = id.toString();
        if (name.isBlank()) {
            throw new IllegalArgumentException("machine(id) was given an empty name. Write the machine's "
                    + "registry name, e.g. event.machine('kubejs_furnace').");
        }
        return MachineBuilderJS.machine(this, name);
    }

    /**
     * The same thing under the name a pack author is most likely to guess first, so the log's advice and the
     * script's wording do not have to agree on a spelling.
     */
    public MachineBuilderJS register(Object id) {
        return machine(id);
    }

    /** How many machines the listeners registered. Read by the plugin after the event was posted. */
    public int registeredCount() {
        return this.registered;
    }

    /**
     * Called by the builder for its own bookkeeping. Package-private and not a script entry point: a script
     * cannot hand this event a {@link MachineDefinition} it built some other way, because a definition that did
     * not come through {@code MachineSchema} would not have been validated.
     */
    void countRegistered(MachineDefinition definition) {
        this.registered++;
        ModularMachineryReborn.LOGGER.info("[{}] KubeJS defined machine '{}' ({} part(s))",
                ModularMachineryReborn.MOD_ID, definition.id(), definition.pattern().partCount());
    }
}
