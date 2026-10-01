package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.client.preview.BlueprintPreviewScreen;
import com.reborn.modularmachinery.machine.MachineDefinition;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry points, kept in their own class so nothing common ever loads a screen type.
 *
 * <p>Callers reach these through {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ...)}, which is the
 * same pattern the mod already uses for its client setup.
 */
public final class ClientPreview {

    private ClientPreview() {
    }

    public static void openBlueprint(MachineDefinition machine) {
        Minecraft.getInstance().setScreen(new BlueprintPreviewScreen(machine));
    }
}
