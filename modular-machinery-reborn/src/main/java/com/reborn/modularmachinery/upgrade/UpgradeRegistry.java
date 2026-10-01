package com.reborn.modularmachinery.upgrade;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The live upgrade registry: which upgrades exist, and which items carry them.
 *
 * <p>Two maps, kept apart on purpose, exactly as the original kept {@code UPGRADES} and {@code ITEM_UPGRADES}
 * apart in {@code RegistryUpgrade}. Separation is what lets one upgrade hang off several items, and lets a pack
 * author restate the item mapping without touching the type.
 *
 * <p>Replaced wholesale on every reload ({@link #replace}), the same way {@code MachineRegistry.replace} rebuilds
 * the machine registry — a reload must not leave a stale type behind.
 *
 * <p>Nothing here is a Forge registry. Upgrades are data, they have no engine-side object identity, and being
 * reloadable is the point.
 */
public final class UpgradeRegistry {

    private static volatile Map<ResourceLocation, UpgradeType> types = Map.of();
    private static volatile Map<Item, UpgradeTarget.Targets> itemTargets = Map.of();

    private UpgradeRegistry() {
    }

    /** Every declared upgrade, keyed by registry name. Immutable. */
    public static Map<ResourceLocation, UpgradeType> all() {
        return types;
    }

    /** One upgrade by registry name, or {@code null}. */
    @Nullable
    public static UpgradeType byId(@Nullable ResourceLocation id) {
        return id == null ? null : types.get(id);
    }

    /**
     * One upgrade by a name written by hand — the {@code upgrade} field of an item mapping, or a bare path
     * that should be read in this mod's namespace. Returns {@code null} for an unknown or malformed name so
     * callers can report the failure with their own wording.
     */
    @Nullable
    public static UpgradeType byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        ResourceLocation id = name.indexOf(':') >= 0
                ? ResourceLocation.tryParse(name)
                : ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + name);
        return id == null ? null : types.get(id);
    }

    /** Every declaration attached to one item, or {@code null} when the item carries no upgrade. */
    @Nullable
    public static UpgradeTarget.Targets targetsFor(ItemStack stack) {
        return stack.isEmpty() ? null : itemTargets.get(stack.getItem());
    }

    /** Every declaration attached to one item class, or {@code null}. */
    @Nullable
    public static UpgradeTarget.Targets targetsFor(Item item) {
        return itemTargets.get(item);
    }

    /** True when the item is declared as an upgrade carrier. The original's {@code supportsUpgrade}. */
    public static boolean supportsUpgrade(ItemStack stack) {
        return !stack.isEmpty() && itemTargets.containsKey(stack.getItem());
    }

    /** Every item that carries at least one upgrade, in load order. */
    public static Map<Item, UpgradeTarget.Targets> itemTargets() {
        return itemTargets;
    }

    /**
     * Installs a freshly loaded registry. Called once per reload, after both passes succeeded.
     *
     * <p>Any machine name a declaration mentions that has no loaded definition is reported <b>once</b>, here,
     * rather than at parse time: a data pack may declare upgrades before or after the machines they name, and
     * definitions themselves may be missing because the pack that holds them is not installed.
     */
    public static void replace(Map<ResourceLocation, UpgradeType> loadedTypes,
                               Map<Item, UpgradeTarget.Targets> loadedTargets) {
        types = Map.copyOf(loadedTypes);
        itemTargets = Map.copyOf(loadedTargets);
    }

    /**
     * Machine names referenced by the current types that have no definition right now, given a lookup. Used by
     * the loader to warn once per reload.
     */
    public static List<ResourceLocation> unresolvedMachines(Predicate<ResourceLocation> exists) {
        List<ResourceLocation> missing = new ArrayList<>();
        for (UpgradeType type : types.values()) {
            for (ResourceLocation machine : type.compatibleMachines()) {
                if (!exists.test(machine) && !missing.contains(machine)) {
                    missing.add(machine);
                }
            }
            for (ResourceLocation machine : type.incompatibleMachines()) {
                if (!exists.test(machine) && !missing.contains(machine)) {
                    missing.add(machine);
                }
            }
        }
        return missing;
    }
}
