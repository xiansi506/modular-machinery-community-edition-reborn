package com.reborn.modularmachinery.compat.fluxnetworks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.view.*;
import java.util.List;

public final class FluxHatchJade implements IServerExtensionProvider<WirelessFluxHatchBlockEntity, CompoundTag> {
    public static void register(IWailaCommonRegistration registration) {
        registration.registerEnergyStorage(new FluxHatchJade(), WirelessFluxHatchBlockEntity.class);
    }
    @Override public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel level,
                                                            WirelessFluxHatchBlockEntity hatch, boolean details) {
        var buffer = hatch.energyStorage();
        return List.of(new ViewGroup<>(List.of(EnergyView.of(buffer.getEnergyStoredL(), buffer.getMaxEnergyStoredL()))));
    }
    @Override public ResourceLocation getUid() { return new ResourceLocation("modular_machinery_reborn", "hatch_energy_storage"); }
    @Override public int getDefaultPriority() { return -1000; }
}
