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
        com.reborn.modularmachinery.magic.MagicBlocks.register();
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
            // C1 第一纵切（0.107.0）：适配器生成轮。必须排在这两个之后——机器注册表先填好（生成轮做
            // 存在性校验）；且 Forge 把本事件收集的监听器追加在原版监听器之后（证据链见 MachineLoader
            // javadoc 0.28.0 节），原版 RecipeManager 就在原版监听器里，所以生成轮 apply 时 manager
            // 已经装满 datapack 配方（含适配器声明占位），扫声明→生成→replaceRecipes 回注（全公开 API）。
            event.addListener(new com.reborn.modularmachinery.recipe.adapter.RecipeAdapterLoader(
                    event.getServerResources().getRecipeManager(), event.getRegistryAccess()));
        });
        // Registers the synthetic resource pack that models controllers declared by a pack author.
        bus.addListener(com.reborn.modularmachinery.block.GeneratedPacks::onAddPackFinders);
        // 波 5.2：兼容模块自注册。放在 FMLCommonSetupEvent（= 所有模组构造完之后）而不是这里，
        // 因为「某个模组装了没」在 mod 构造阶段能不能问**未查到依据**（本机 fmlcore 无源码，
        // ModList.setLoadedMods 是包内方法），而猜错的后果是兼容类型静默地永不注册。
        // 需求类型不是 Forge 注册表内容，所以这个时机不违 D8。理由详见 ModCompat 的类注释。
        bus.addListener(com.reborn.modularmachinery.compat.ModCompat::onCommonSetup);
        // RegisterCapabilitiesEvent is an IModBusEvent, dispatched by ModLoader to each mod's bus.
        // The gameplay bus never receives it; compat modules also seal their list at this stage.
        bus.addListener(
                com.reborn.modularmachinery.compat.ModCompat::onRegisterCapabilities);
        // Exposes config/modular_machinery_reborn/recipes/ as a data pack, since 1.20.1 recipes can only come
        // from a RecipeManager reload.
        bus.addListener(com.reborn.modularmachinery.recipe.ConfigRecipes::onAddPackFinders);
        // /mm-get_blueprint <machine>, the original's command for obtaining a blueprint.
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.BlueprintCommand::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.DiagnoseCommand::onRegisterCommands);
        // F2/F3/F4（0.86.0）：旧版的另外三条命令——手里物品写成描述符、控制器逐台计时报告、机器/配方语法检查。
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.HandCommand::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(
                com.reborn.modularmachinery.command.PerformanceReportCommand::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.SyntaxCommand::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.command.RecipeEditorCommand::register);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.authoring.RecipeEditorService::logout);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.authoring.RecipeEditorService::dimension);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.authoring.RecipeEditorService::respawn);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.authoring.RecipeEditorService::stopped);
        // 0.29.0: the construct tool's per-player selection lives on the server, and the original dropped it when
        // the connection did (PlayerStructureSelectionHelper:128-134). A new session must not inherit one.
        MinecraftForge.EVENT_BUS.addListener(
                com.reborn.modularmachinery.selection.ServerSelections::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.selection.ServerSelections::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.selection.ServerSelections::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(com.reborn.modularmachinery.selection.ServerSelections::onServerStopped);
        // A8（0.106.0）：自动搭建的事件接线（蓝图入槽 / 搭建触发 / 逐 tick 推进 / 登出清理）。
        // 它是实例监听器（三个 @SubscribeEvent 一组），走 register 而不是逐方法 addListener。
        MinecraftForge.EVENT_BUS.register(new com.reborn.modularmachinery.common.assembly.AssemblyHandler());
        // 波 4：把机器定义送到客户端。`OnDatapackSyncEvent` 是这件事**唯一**正确的时机——
        // 它在玩家登录时针对该玩家触发、在 `/reload` 之后针对所有人触发一次（`getPlayer()` 为 null）。
        // 自己监听 `PlayerLoggedInEvent` 会在定义加载之前就发（玩家可能在 reload 中间进服）。
        MinecraftForge.EVENT_BUS.addListener(
                com.reborn.modularmachinery.network.MachineDefinitionSync::onDatapackSync);
        // 断线时清空客户端那一份：否则换一个服务器之后，上一个服务器的机器还留在界面里。
        // `ClientSelection` 有同样的语义（原版在断线时清掉选区）。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MinecraftForge.EVENT_BUS.addListener(
                        com.reborn.modularmachinery.client.ClientMachines::onLoggingOut));
        // A10: the original's MMWorldEventListener, rebuilt on the four Forge block events 1.20.1 has. It only
        // records positions; the controller decides whether any of them fall inside its formed bounds.
        com.reborn.modularmachinery.machine.StructureChangeTracker.register();
        // A11: the original's owner check (EventHandler#onBlockBroken). Placement is enforced in
        // MachineControllerItem#place instead, because the item is what carries the owner tag.
        MinecraftForge.EVENT_BUS.addListener(
                com.reborn.modularmachinery.block.ControllerSecurity::onBlockBreak);


        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> bus.addListener(com.reborn.modularmachinery.client.ClientSetup::onClientSetup));
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("[{}] {} {} loaded.", MOD_ID, DISPLAY_NAME, version);
        LOGGER.info("[{}] KubeJS integration is optional; when KubeJS is installed, kubejs.plugins.txt registers the machine recipe schema.", MOD_ID);
    }
}
