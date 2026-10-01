package com.reborn.modularmachinery.menu;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ModularMachineryReborn.MOD_ID);
    public static final RegistryObject<MenuType<MachineControllerMenu>> MACHINE_CONTROLLER = MENUS.register("machine_controller", () -> IForgeMenuType.create((windowId, inv, data) -> new MachineControllerMenu(windowId, inv, data)));
    /** One menu type serves every hatch family and tier; the screen varies, the window does not. */
    public static final RegistryObject<MenuType<MachineHatchMenu>> MACHINE_HATCH = MENUS.register("machine_hatch", () -> IForgeMenuType.create((windowId, inv, data) -> new MachineHatchMenu(windowId, inv, data)));
    /** The parallel controller's container: no slots, two container-data values (M6d). */
    public static final RegistryObject<MenuType<ParallelControllerMenu>> PARALLEL_CONTROLLER = MENUS.register("parallel_controller", () -> IForgeMenuType.create((windowId, inv, data) -> new ParallelControllerMenu(windowId, inv, data)));
    /** The upgrade bus's container: 3/6/9/12/18 upgrade slots first, then the player's (M6b). */
    public static final RegistryObject<MenuType<UpgradeBusMenu>> UPGRADE_BUS = MENUS.register("upgrade_bus", () -> IForgeMenuType.create((windowId, inv, data) -> new UpgradeBusMenu(windowId, inv, data)));
    /**
     * The factory controller's container (M6e-2). It is a second menu type rather than a flag on
     * {@code machine_controller} because its layout differs: the blueprint slot sits at (255, 8) and the player
     * inventory starts at x = 112, so the two cannot share slot coordinates (the original had two container
     * classes for the same reason). Only the block position travels over the wire; {@code FactoryControllerMenu}'s
     * buffer constructor resolves the block entity from it.
     */
    public static final RegistryObject<MenuType<FactoryControllerMenu>> FACTORY_CONTROLLER = MENUS.register("factory_controller", () -> IForgeMenuType.create((windowId, inv, data) -> new FactoryControllerMenu(windowId, inv, data)));
    private ModMenus() {}
}
