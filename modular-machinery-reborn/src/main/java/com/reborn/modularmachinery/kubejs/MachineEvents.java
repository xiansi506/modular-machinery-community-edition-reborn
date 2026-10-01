package com.reborn.modularmachinery.kubejs;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.MachineDefinitions;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;

/**
 * The machine event group and the one place a reload cycle is opened.
 *
 * <p>It is a separate class from {@link ModularMachineryKubeJSPlugin} so that the plugin stays a thin adapter:
 * the plugin registers the group and the binding, and this class owns the sequence that matters — <b>clear the
 * script layer, then let the scripts define machines again</b>. Keep those two statements together and in that
 * order, and a script that was deleted stops contributing; swap them or move one away from the other and a
 * machine outlives its script.
 *
 * <pre>{@code
 * // server_scripts/machines.js
 * MachineRegistryEvents.registry(event => {
 *   event.machine('kubejs_furnace')
 *     .localizedName('Scripted Furnace')
 *     .part(1, -1, 0, 'minecraft:stone')
 * })
 * }</pre>
 *
 * <p>The binding a script sees is <b>not</b> this group; it is a KubeJS {@code EventGroupWrapper} around it,
 * built in {@code ModularMachineryKubeJSPlugin#registerBindings}. The wrapper is what turns the group's typed
 * events into callable properties, so handing a script the raw group yields the binding with no events on it.
 *
 * <p>{@code onEvent('machineRegistry', …)} is <b>not</b> an equivalent: KubeJS 6 removed the generic
 * {@code onEvent} binding (its placeholder throws "onEvent() is no longer supported!").
 */
public final class MachineEvents {

    /** The script binding's name and the event group's id. */
    public static final String GROUP_NAME = "MachineRegistryEvents";

    /** The event's own name inside the group. It is the property a script calls on the binding. */
    public static final String REGISTRY_NAME = "registry";

    private static final EventGroup GROUP = EventGroup.of(GROUP_NAME);

    private static final EventHandler REGISTRY = GROUP.server(REGISTRY_NAME,
            () -> MachineRegistryEventJS.class);

    private MachineEvents() {
    }

    /** Called from the plugin's {@code registerEvents()}: KubeJS refuses an unregistered group's events. */
    public static void register() {
        GROUP.register();
    }

    /** The group object scripts receive as the {@code MachineRegistryEvents} binding. */
    public static EventGroup group() {
        return GROUP;
    }

    /**
     * Opens a script cycle and posts the registry event.
     *
     * <p>Called from the plugin's {@code onServerReload()}, which KubeJS runs once the server scripts of this
     * reload have been evaluated and before this mod's loader reads anything — see {@link MachineDefinitions}
     * for the ordering evidence and the merge contract.
     *
     * @return how many machines the listeners registered, so a silent zero is visible in the log
     */
    public static int post() {
        MachineDefinitions.beginCycle();
        MachineRegistryEventJS event = new MachineRegistryEventJS();
        REGISTRY.post((ScriptTypeHolder) ScriptType.SERVER, event);
        ModularMachineryReborn.LOGGER.info("[{}] KubeJS machine registry event posted; {} machine definition(s) "
                        + "were staged by scripts and will be merged by the machine loader",
                ModularMachineryReborn.MOD_ID, event.registeredCount());
        return event.registeredCount();
    }
}
