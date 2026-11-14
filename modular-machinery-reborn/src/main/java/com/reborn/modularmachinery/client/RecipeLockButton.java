package com.reborn.modularmachinery.client;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.network.ControllerRecipeLockPacket;
import com.reborn.modularmachinery.network.ModNetwork;
import com.reborn.modularmachinery.recipe.MachineRecipe;
import com.reborn.modularmachinery.recipe.MachineRecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Shared recipe-lock control for the regular and factory controller screens. */
final class RecipeLockButton extends Button {
    private static final String KEY = "gui.modular_machinery_reborn.recipe_lock.";
    private final MachineControllerBlockEntity controller;
    private Component lastTooltip;

    RecipeLockButton(Screen parent, AbstractContainerMenu menu, MachineControllerBlockEntity controller, int x, int y) {
        super(x, y, 20, 18, Component.translatable(KEY + "button"),
                button -> open(parent, menu, controller), DEFAULT_NARRATION);
        this.controller = controller;
        refresh();
    }

    void refresh() {
        ResourceLocation locked = controller.lockedRecipe();
        active = controller.isFormed() || locked != null;
        setMessage(Component.translatable(KEY + (locked == null ? "button" : "button_locked")));
        Component current = locked == null ? Component.translatable(KEY + "auto")
                : Component.translatable(KEY + "selected", locked);
        var tooltip = Component.translatable(KEY + "title").append("\n").append(current);
        String failure = controller.startFailureKey();
        String cause = controller.failureCauseText();
        if (locked != null && cause != null && !cause.isBlank()) {
            tooltip.append("\n").append(Component.literal(cause));
        } else if (locked != null && failure != null) {
            tooltip.append("\n").append(Component.translatable(failure));
        }
        if (!active) tooltip.append("\n").append(Component.translatable(KEY + "form_first"));
        if (!Objects.equals(lastTooltip, tooltip)) {
            lastTooltip = tooltip;
            setTooltip(Tooltip.create(tooltip));
        }
    }

    private static void open(Screen parent, AbstractContainerMenu menu, MachineControllerBlockEntity controller) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.containerMenu != menu) return;
        var choices = new LinkedHashMap<String, Component>();
        choices.put("", Component.translatable(KEY + "auto"));
        ResourceLocation machine = controller.machineId();
        if (controller.isFormed() && machine != null) {
            for (MachineRecipe recipe : MachineRecipeIndex.forMachine(mc.level.getRecipeManager(), machine)) {
                var output = recipe.getResultItem(mc.level.registryAccess());
                Component name = Component.translatable(recipe.registryName());
                if (!output.isEmpty() && !output.getHoverName().getString().equals(name.getString())) {
                    name = name.copy().append(" / ").append(output.getHoverName());
                }
                choices.put(recipe.getId().toString(), name);
            }
        }
        mc.setScreen(new RecipeResourcePicker(Component.translatable(KEY + "title"), parent, choices.keySet(), "", value -> {
            if (mc.player != null && mc.player.containerMenu == menu) {
                ResourceLocation recipe = value.isEmpty() ? null : ResourceLocation.tryParse(value);
                ModNetwork.CHANNEL.sendToServer(new ControllerRecipeLockPacket(menu.containerId, controller.getBlockPos(), recipe));
            }
        }, choices::get));
    }
}
