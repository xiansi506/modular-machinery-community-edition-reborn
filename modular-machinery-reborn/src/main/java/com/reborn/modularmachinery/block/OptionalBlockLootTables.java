package com.reborn.modularmachinery.block;

import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Optional block item references must only enter data loading when their registry exists. */
public final class OptionalBlockLootTables {
    private OptionalBlockLootTables() {}
    public static Map<ResourceLocation,byte[]> generate(Predicate<String> loaded) {
        Map<ResourceLocation,byte[]> tables = new LinkedHashMap<>();
        if (loaded.test("ae2")) for (String name : new String[]{"me_item_input_bus", "me_item_output_bus", "me_pattern_provider", "me_pattern_mirror"}) {
            String json = "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"modular_machinery_reborn:"+name+"\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
            tables.put(new ResourceLocation("modular_machinery_reborn", "loot_tables/blocks/"+name+".json"), json.getBytes(StandardCharsets.UTF_8));
        }
        if (loaded.test("fluxnetworks")) {
            for (HatchKind kind : new HatchKind[]{HatchKind.WIRELESS_ENERGY_INPUT, HatchKind.WIRELESS_ENERGY_OUTPUT}) {
                java.util.List<String> pools = new java.util.ArrayList<>();
                for (HatchTier tier : kind.ladder()) {
                    pools.add("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"modular_machinery_reborn:"
                            + kind.itemId(tier) + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},"
                            + "{\"condition\":\"minecraft:block_state_property\",\"block\":\"modular_machinery_reborn:"
                            + kind.id() + "\",\"properties\":{\"size\":\"" + tier.getSerializedName() + "\"}}]}");
                }
                String json = "{\"type\":\"minecraft:block\",\"pools\":[" + String.join(",", pools) + "]}";
                tables.put(new ResourceLocation("modular_machinery_reborn", "loot_tables/blocks/" + kind.id() + ".json"),
                        json.getBytes(StandardCharsets.UTF_8));
            }
        }
        return tables;
    }
}
