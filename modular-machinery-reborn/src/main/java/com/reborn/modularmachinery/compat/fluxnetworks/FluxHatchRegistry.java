package com.reborn.modularmachinery.compat.fluxnetworks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

/** Only called after the registration-time Flux Networks presence check. */
public final class FluxHatchRegistry {
    private FluxHatchRegistry() {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static RegistryObject<BlockEntityType<?>> register(DeferredRegister<BlockEntityType<?>> registry,
                                                             RegistryObject<Block> block, boolean input) {
        RegistryObject registered = registry.register(input ? "wireless_energy_input" : "wireless_energy_output",
                () -> BlockEntityType.Builder.of(input ? WirelessFluxHatchBlockEntity.Input::new
                        : WirelessFluxHatchBlockEntity.Output::new, block.get()).build(null));
        return (RegistryObject<BlockEntityType<?>>) registered;
    }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean input) {
        return input ? new WirelessFluxHatchBlockEntity.Input(pos, state)
                : new WirelessFluxHatchBlockEntity.Output(pos, state);
    }
}
