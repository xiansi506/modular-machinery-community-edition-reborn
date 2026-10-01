package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A blueprint bound to one machine, reproducing the original's {@code ItemBlueprint}.
 *
 * <p>The machine's registry name lives in NBT under {@code dynamicmachine}, exactly as the original stored it,
 * so a blueprint written by one is readable by the other. A bound blueprint does two things:
 *
 * <ul>
 *   <li>In a controller's blueprint slot, it makes the controller try <b>that machine first</b>, and for a
 *       machine whose definition sets {@code requires-blueprint} it is the <b>only</b> way the machine can
 *       form — those definitions are skipped by the general search.</li>
 *   <li>Right-clicking opens the machine's structure preview, drawn in 3D with the blocks it needs listed
 *       along the bottom (see {@code client.preview.BlueprintPreviewScreen}). Nothing opens on a
 *       dedicated-server client, where machine definitions are not synced and the registry is empty.</li>
 * </ul>
 *
 * <p>The tooltip names the machine, or reads "empty" for an unbound blueprint. The original shipped one
 * creative entry per registered machine rather than a single item, which is what the creative tab does here
 * too.
 */
public final class BlueprintItem extends Item {

    /** NBT key kept identical to the original's, so blueprints stay interchangeable. */
    public static final String MACHINE_KEY = "dynamicmachine";

    public BlueprintItem(Properties properties) {
        super(properties);
    }

    /** The machine this blueprint is bound to, or {@code null} when it is unbound or malformed. */
    @Nullable
    public static ResourceLocation machineOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlueprintItem)) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MACHINE_KEY)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(MACHINE_KEY));
    }

    public static void setMachine(ItemStack stack, ResourceLocation machineId) {
        stack.getOrCreateTag().putString(MACHINE_KEY, machineId.toString());
    }

    /** A fresh blueprint for one machine, for the creative tab and the command. */
    public static ItemStack forMachine(Item blueprint, ResourceLocation machineId) {
        ItemStack stack = new ItemStack(blueprint);
        setMachine(stack, machineId);
        return stack;
    }

    /**
     * Right-clicking a bound blueprint opens its structure preview, as in the original.
     *
     * <p>The screen is opened from the client only, and through {@link com.reborn.modularmachinery.client.ClientPreview}
     * so that no common class ever references a screen type.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation machineId = machineOf(stack);
        if (machineId == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            MachineDefinition machine = MachineRegistry.byId(machineId).orElse(null);
            if (machine == null) {
                // The definition is unknown here — on a dedicated-server client nothing is. Say so rather than
                // opening an empty screen.
                return InteractionResultHolder.pass(stack);
            }
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.reborn.modularmachinery.client.ClientPreview.openBlueprint(machine));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation machineId = machineOf(stack);
        MachineDefinition machine = MachineRegistry.byId(machineId).orElse(null);
        if (machine == null) {
            tooltip.add(Component.translatable("tooltip.modular_machinery_reborn.blueprint.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(machine.displayName().copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
