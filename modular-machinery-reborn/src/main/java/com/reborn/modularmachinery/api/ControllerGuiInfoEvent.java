package com.reborn.modularmachinery.api;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets addons add lines to the machine controller screen, posted once per frame while the screen draws.
 *
 * <p>This is the 1.20.1 replacement for the original's {@code ControllerGUIRenderEvent}, which served exactly
 * this purpose and was part of the public surface the original exposed to addons. Subscribe on the Forge event
 * bus and call {@link #addInfo}:
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onControllerInfo(ControllerGuiInfoEvent event) {
 *     event.addInfo(Component.literal("My addon: " + event.controller().progress()));
 * }
 * }</pre>
 *
 * <p>Lines are wrapped to the same width as the built-in ones. Posting happens on the client, so the controller
 * available here is the client-side block entity.
 */
public class ControllerGuiInfoEvent extends Event {

    private final MachineControllerBlockEntity controller;
    private final List<Component> extraInfo = new ArrayList<>();

    public ControllerGuiInfoEvent(MachineControllerBlockEntity controller) {
        this.controller = controller;
    }

    public MachineControllerBlockEntity controller() {
        return this.controller;
    }

    /** Adds one line; long lines are wrapped by the screen. */
    public void addInfo(Component line) {
        this.extraInfo.add(line);
    }

    public List<Component> extraInfo() {
        return List.copyOf(this.extraInfo);
    }
}
