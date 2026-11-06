package com.reborn.modularmachinery.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import com.reborn.modularmachinery.block.MeGasStorage;
import com.reborn.modularmachinery.machine.HatchCollection;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

/** Only loaded when AE2, Mekanism and Applied Mekanistics are installed. Gas quantities use native mB. */
public final class AppMekGasBridge {
    private AppMekGasBridge() { }
    /**
     * Explicit legacy compatibility registration, including the otherwise unused direct-push callback.
     * @deprecated Normal startup uses {@link #registerProviderBuffers()}; retained behavior for external callers.
     */
    @Deprecated(since = "0.121.79", forRemoval = false)
    public static void register() {
        PatternInputTransfer.setAdditionalInput(AppMekGasBridge::pushGas);
        registerProviderBuffers();
    }
    /** Active provider/isolated-buffer gas support only. Does not touch any explicit legacy callback. */
    public static void registerProviderBuffers() {
        ProviderBuffer.installGas(ProviderGasPort::isGas, ProviderGasPort::new);
    }
    /**
     * Explicit compatibility operation on the caller's planned ports, not a production dispatch route.
     * @deprecated Retained for legacy callers; use the active provider-buffer path for normal dispatch.
     */
    @Deprecated(since = "0.121.79", forRemoval = false)
    public static boolean pushGas(HatchCollection planned, AEKey key, long amount) {
        if (!(key instanceof MekanismKey chemical) || chemical.getForm() != MekanismKey.GAS
                || !(chemical.getStack() instanceof GasStack gas) || gas.isEmpty() || amount < 0) return false;
        long remaining = amount;
        for (Object port : planned.gasInputs()) {
            if (!(port instanceof IGasHandler handler)) continue;
            remaining = handler.insertChemical(new GasStack(gas, remaining), Action.EXECUTE).getAmount();
            if (remaining == 0) return true;
        }
        return remaining == 0;
    }
    @Nullable public static MeGasStorage.Entry encodedGas(ItemStack stack) {
        GenericStack generic = GenericStack.fromItemStack(stack);
        if (generic == null || !(generic.what() instanceof MekanismKey chemical)
                || chemical.getForm() != MekanismKey.GAS || !(chemical.getStack() instanceof GasStack gas)
                || gas.isEmpty() || generic.amount() <= 0) return null;
        return new MeGasStorage.Entry(gas.getTypeRegistryName(), generic.amount(), gas.getTextComponent(), gas.getType().getTint());
    }
    public static MeGasStorage.Network network(IManagedGridNode node, IActionHost host) {
        return new MeGasStorage.Network() {
            private long transfer(ResourceLocation id, long amount, boolean insert) {
                var gas = com.reborn.modularmachinery.compat.mekanism.MeGasTanks.resolve(id);
                var grid = node.getGrid();
                if (gas == null || amount <= 0 || grid == null || !node.isActive() || !node.isOnline()) return 0;
                MekanismKey key = MekanismKey.of(new GasStack(gas, 1));
                if (key == null) return 0;
                long moved = insert
                        ? StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                                key, amount, IActionSource.ofMachine(host), Actionable.MODULATE)
                        : StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(),
                                key, amount, IActionSource.ofMachine(host), Actionable.MODULATE);
                return Math.max(0, Math.min(amount, moved));
            }
            @Override public long insert(ResourceLocation gas, long amount) { return transfer(gas, amount, true); }
            @Override public long extract(ResourceLocation gas, long amount) { return transfer(gas, amount, false); }
        };
    }
}
