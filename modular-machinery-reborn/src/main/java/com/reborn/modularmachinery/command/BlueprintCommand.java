package com.reborn.modularmachinery.command;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.item.BlueprintItem;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /mm-get_blueprint <machine>} — hands the player a blueprint bound to that machine.
 *
 * <p>Same name and same permission level as the original's command. It exists because a blueprint for a machine
 * that sets {@code requires-blueprint} is the only way to build it, so there has to be some way to obtain one
 * outside creative mode. Names are read as a bare path ({@code alloy_furnace}) or fully qualified
 * ({@code other_mod:its_machine}).
 */
public final class BlueprintCommand {

    private BlueprintCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mm-get_blueprint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("machine", StringArgumentType.string())
                        .executes(context -> {
                            Entity entity = context.getSource().getEntity();
                            if (!(entity instanceof Player player)) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.modular_machinery_reborn.get_blueprint.player_only"));
                                return 0;
                            }
                            String raw = StringArgumentType.getString(context, "machine");
                            ResourceLocation id = raw.indexOf(':') >= 0
                                    ? ResourceLocation.tryParse(raw)
                                    : new ResourceLocation(ModularMachineryReborn.MOD_ID, raw);
                            MachineDefinition machine = MachineRegistry.byId(id).orElse(null);
                            if (machine == null) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.modular_machinery_reborn.get_blueprint.not_found", raw));
                                return 0;
                            }
                            ItemStack blueprint = BlueprintItem.forMachine(ModItems.BLUEPRINT.get(), machine.id());
                            if (!player.addItem(blueprint)) {
                                player.drop(blueprint, false);
                            }
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.modular_machinery_reborn.get_blueprint.success",
                                    machine.displayName()), false);
                            return 1;
                        })));
    }
}
