package com.reborn.modularmachinery.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.item.BlueprintItem;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
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
 * ({@code other_mod:its_machine}, with or without quotes).
 *
 * <p>The argument is the natively registered {@link StringArgumentType#greedyString()} — a custom
 * {@code ArgumentType} has no client-side {@code ArgumentTypeInfo}, and the command tree sync packet
 * ({@code ClientboundCommandsPacket}) refuses to serialize it, so the node could never reach a client.
 * Greedy also lets a fully qualified id through without quotes, which the plain string type stops at the
 * colon for. The accepted quoted spelling arrives with its quote characters still attached, so the
 * callback strips one matching pair; everything else (bare names, validation, this mod's namespace
 * default) stays here rather than in argument parsing.
 */
public final class BlueprintCommand {

    private BlueprintCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mm-get_blueprint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("machine", StringArgumentType.greedyString())
                        .executes(context -> {
                            Entity entity = context.getSource().getEntity();
                            if (!(entity instanceof Player player)) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.modular_machinery_reborn.get_blueprint.player_only"));
                                return 0;
                            }
                            String raw = StringArgumentType.getString(context, "machine").strip();
                            // Greedy hands the accepted quoted spelling over with its quotes; one matching
                            // pair is spelling, not part of the id, and anything else keeps its quotes —
                            // which no id may contain, so it fails the parse below like any other bad text.
                            if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
                                raw = raw.substring(1, raw.length() - 1);
                            }
                            ResourceLocation id = raw.indexOf(':') >= 0
                                    ? ResourceLocation.tryParse(raw)
                                    : ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + raw);
                            if (id == null) {
                                // An id that parses nowhere (uppercase, bad characters, stray quotes, extra
                                // words) is an ordinary command error, not an uncaught exception.
                                context.getSource().sendFailure(Component.translatable(
                                        "command.modular_machinery_reborn.get_blueprint.not_found", raw));
                                return 0;
                            }
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
