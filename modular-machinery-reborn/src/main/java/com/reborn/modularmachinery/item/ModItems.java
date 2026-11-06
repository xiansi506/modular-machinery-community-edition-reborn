package com.reborn.modularmachinery.item;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Core standalone items carried over from the 1.12.2 registry. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModularMachineryReborn.MOD_ID);
    public static final RegistryObject<Item> BLUEPRINT = ITEMS.register("itemblueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MODULARIUM = register("itemmodularium", 64);
    /**
     * 0.29.0: the construct tool grew its behaviour — selection, highlight, and the machine-JSON export
     * ({@link ConstructToolItem}). The projector beside it is still the original's 13-line inert item on purpose:
     * the owner deferred it (移植方案-v2.md §9).
     */
    public static final RegistryObject<Item> CONSTRUCT_TOOL = ITEMS.register("itemconstructtool",
            () -> new ConstructToolItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RECIPE_EDITOR = ITEMS.register("recipe_editor",
            () -> new RecipeEditorItem(new Item.Properties().stacksTo(1)));
    /**
     * 0.104.0（C6 第一片）：投影仪不再是 0.29.0 时的惰性物品（当年原主点名的缓办项）——
     * 对成型控制器使用即可开关世界幽灵预览（{@link MachineProjectorItem} →
     * {@code client.preview.WorldGhostPreview}），逐层引导补齐机器。
     */
    public static final RegistryObject<Item> MACHINE_PROJECTOR =
            ITEMS.register("machine_projector",
                    () -> new MachineProjectorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> REDSTONE_SIGNAL = register("redstonesignal", 64);
    public static final RegistryObject<Item> WRENCH = register("wrench", 1);
    private static RegistryObject<Item> register(String id, int stackSize) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(stackSize)));
    }
    private ModItems() {}
}
