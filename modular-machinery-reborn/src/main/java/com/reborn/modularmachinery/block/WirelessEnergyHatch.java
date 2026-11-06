package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.energy.LongEnergyStorage;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;
import java.util.UUID;

/** Common-side boundary: a pack without Flux Networks never resolves its device classes. */
public interface WirelessEnergyHatch {
    LongEnergyStorage energyStorage();
    void serverTick();
    void openNetwork(Player player);
    void setOwner(UUID owner);
    @Nullable Integer machineColor();
    void setMachineColor(@Nullable Integer color);
    int networkColor();
}
