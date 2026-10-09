package com.reborn.modularmachinery;

import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.block.ModBlocks;
import com.reborn.modularmachinery.recipe.ModRecipeSerializers;
import com.reborn.modularmachinery.recipe.ModRecipeTypes;
import com.reborn.modularmachinery.machine.MachineLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.reborn.modularmachinery.menu.ModMenus;
import com.reborn.modularmachinery.item.ModItems;
import org.slf4j.Logger;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

@Mod(ModularMachineryReborn.MOD_ID)
public final class ModularMachineryReborn {
    public static final String MOD_ID = "modular_machinery_reborn";
    public static final String DISPLAY_NAME = "Modular Machinery: Community Edition Reborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModularMachineryReborn() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlocks.TABS.register(bus);
        ModRecipeTypes.TYPES.register(bus);
        ModRecipeSerializers.SERIALIZERS.register(bus);
        ModBlocks.BLOCK_ENTITIES.register(bus);
        ModMenus.MENUS.register(bus);
        // 0.27.0: the ModularController compatibility namespace needs its own pair of registers, because a
        // registry name's namespace comes from the DeferredRegister's own mod id. See MocNamespace for why those
        // blocks are registered unconditionally instead of from the config key the original gated them on.
        ModBlocks.registerMocNamespace(bus);
        // The mod's config: one spec, holding the parallel-controller ceilings (M6d, the project's first config
        // values, the override of ParallelControllerData's hardcoded enum numbers) and the upgrade bus's per-tier
        // slot counts (M6b, the same override of UpgradeBusData). The original read both from its own config.
        //
        // THIS MUST STAY THE ONLY registerConfig CALL IN THE MOD. Forge keeps one config per type per mod and keys
        // it on the file name, so a second ModConfig.Type.COMMON registration collides on
        // "modular_machinery_reborn-common.toml" and throws "Config conflict detected!" from ConfigTracker during
        // mod construction — which is exactly how 0.21.0 died at startup, when UpgradeBusConfig registered a
        // second COMMON spec beside ParallelControllerConfig's. Both sections now live in ModConfig's one builder;
        // add new sections there, never here. (Second time a "first of its kind" choice in this project has cost a
        // startup crash — cf. the un-reobfuscated 0.19.0 jar and NoSuchFieldError CREATIVE_MODE_TAB.)
        com.reborn.modularmachinery.config.ModConfig.register();
        // The project's first custom channel; see ModNetwork for why one packet is unavoidable (M6d).
        com.reborn.modularmachinery.network.ModNetwork.register();
        // Machine definitions live in the data pack; AddReloadListenerEvent is a Forge-bus event.
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(new MachineLoader());
            // Upgrade declarations are data too (data/<ns>/upgrade/ plus config/<modid>/upgrade/), and they are
            // not a Forge registry either, so they reload the same way. The two listeners are independent:
            // neither reads the other's output.
            event.addListener(new com.reborn.modularmachinery.upgrade.UpgradeLoader());
        });
        // Registers the synthetic resource pack that models controllers declared by a pack author.
        bus.addListener(com.reborn.modularmachinery.block.GeneratedPacks::onAddPackFinders);
        // Exposes config/modular_machinery_reborn/recipes/ as a data pack, since 1.20.1 recipes can only come
        // from a RecipeManager reload.
        bus.addListener(com.reborn.modularmachinery.recipe.ConfigRecipes::onAddPackFinders);
        // /mm-get_blueprint <machine>, the original's command for obtaining a blueprint.
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.BlueprintCommand::onRegisterCommands);
        // 0.29.0: the construct tool's per-player selection lives on the server, and the original dropped it when
        // the connection did (PlayerStructureSelectionHelper:128-134). A new session must not inherit one.
        MinecraftForge.EVENT_BUS.addListener(
                com.reborn.modularmachinery.selection.ServerSelections::onPlayerLoggedOut);


        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> bus.addListener(com.reborn.modularmachinery.client.ClientSetup::onClientSetup));
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("[{}] {} {} loaded.", MOD_ID, DISPLAY_NAME, version);
        LOGGER.info("[{}] KubeJS integration is optional; when KubeJS is installed, kubejs.plugins.txt registers the machine recipe schema.", MOD_ID);
    }
}
