package com.reborn.modularmachinery.upgrade;

import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An upgrade the pack author declared in JSON — the data-driven counterpart of the original
 * {@code github.kasuminova.mmce.common.upgrade.UpgradeType}.
 *
 * <p>Two differences from the original are deliberate and load-bearing:
 *
 * <ul>
 *   <li><b>Compatibility is stored as registry names, not machine objects.</b> The original held
 *       {@code Set<DynamicMachine>} and filled it while a CraftTweaker script loaded. In this project machine
 *       definitions come from a data pack and a {@code /reload} replaces every {@code MachineDefinition}
 *       instance ({@code MachineLoader} calls {@code MachineRegistry.replace}), so an object reference taken at
 *       declaration time would point at the previous generation. A registry name survives that.</li>
 *   <li><b>Whitelist and blacklist may not both be declared.</b> The original only found out when a script
 *       added the same machine to both, and threw from {@code addCompatibleMachine}. Here it is a load error
 *       with a message that says what to write instead.</li>
 * </ul>
 *
 * <p>{@code level} mirrors the original field, which the original's own builder documents as having no effect
 * ({@code MachineUpgradeBuilder#newBuilder}: "等级（暂无作用）"). It is parsed and exposed so that definitions
 * stay faithful, and nothing reads it — inventing a meaning here would silently diverge from the original.
 *
 * <h2>M6b: modifiers and stacking</h2>
 *
 * <p>M6a was a data layer with no consumer, so it could describe an upgrade without saying what it <b>does</b>.
 * The bus is the consumer, and "what an upgrade does" is a {@link RecipeModifier} — the same type a machine
 * definition's {@code modifiers} array carries, reaching the recipe engine through the same
 * {@link com.reborn.modularmachinery.recipe.RecipeModifiers}. Two fields were added in 0.21.0:
 *
 * <ul>
 *   <li>{@code modifiers} — the recipe modifiers this upgrade contributes while it sits in a bus belonging to a
 *       machine that accepts it. The original had no declaration for this: a CraftTweaker script attached an
 *       event handler through {@code MachineUpgradeBuilder#addModifier(boolean, String, RecipeModifier)}
 *       ({@code MachineUpgradeBuilder.java:138-159}), and the handler called
 *       {@code controller.addModifier(key, modifier)} when a {@code MachineTickEvent} started. In this project
 *       there is no script bridge (D7), so the modifier is declared, and the bus reads it.</li>
 *   <li>{@code stackable} — the original's {@code stackAble} argument. When {@code false}, a stack of {@code n}
 *       carrier items contributes the modifier <b>once</b>; when {@code true}, the modifier is applied
 *       {@code n} times, which the original implemented by multiplying a {@code MULTIPLY} modifier's value by
 *       itself {@code n} times ({@code :152-156}, {@code RecipeModifier#multiply}). Additive modifiers behave
 *       identically either way in the original, and here they do too.</li>
 * </ul>
 *
 * @param id                  the upgrade's registry name, e.g. {@code modular_machinery_reborn:example_speed}
 * @param localizedName       the literal {@code localizedname}, or the id's path when the field is absent
 * @param level               the original's {@code level}; parsed only, never evaluated
 * @param maxStackSize        how many of this upgrade one item may carry; at least 1
 * @param dynamic             {@code true} when each copy keeps its own NBT on the item, as the original's
 *                            {@code DynamicMachineUpgrade} did; {@code false} keeps all state on the bus
 * @param compatibleMachines  whitelist. When non-empty only these machines accept the upgrade
 * @param incompatibleMachines blacklist. Only consulted when the whitelist is empty
 * @param descriptions        extra tooltip lines on a carrier item, verbatim from the original's
 *                            {@code SimpleMachineUpgrade#descriptions}
 * @param modifiers           the recipe modifiers this upgrade contributes; empty means "does nothing yet"
 * @param stackable           whether the contributed modifier is applied once per carrier item, the original's
 *                            {@code stackAble}
 */
public record UpgradeType(ResourceLocation id,
                          String localizedName,
                          float level,
                          int maxStackSize,
                          boolean dynamic,
                          Set<ResourceLocation> compatibleMachines,
                          Set<ResourceLocation> incompatibleMachines,
                          List<String> descriptions,
                          List<RecipeModifier> modifiers,
                          boolean stackable) {

    public UpgradeType {
        maxStackSize = Math.max(1, maxStackSize);
        compatibleMachines = Set.copyOf(new LinkedHashSet<>(compatibleMachines));
        incompatibleMachines = Set.copyOf(new LinkedHashSet<>(incompatibleMachines));
        descriptions = List.copyOf(descriptions);
        modifiers = List.copyOf(modifiers);
    }

    /**
     * An upgrade that carries no modifier — what every declaration written before M6b meant. Kept so M6a's
     * callers (and the offline harness) need not spell out the two new fields.
     */
    public UpgradeType(ResourceLocation id, String localizedName, float level, int maxStackSize, boolean dynamic,
                       Set<ResourceLocation> compatibleMachines, Set<ResourceLocation> incompatibleMachines,
                       List<String> descriptions) {
        this(id, localizedName, level, maxStackSize, dynamic, compatibleMachines, incompatibleMachines,
                descriptions, List.of(), false);
    }

    /** Whether this upgrade changes any recipe value at all. */
    public boolean hasModifiers() {
        return !this.modifiers.isEmpty();
    }

    /**
     * Display name, following {@code MachineDefinition#displayName}: the translation key
     * {@code <namespace>.<path>} wins when present, otherwise the literal {@code localizedname} is used.
     */
    public Component displayName() {
        return Component.translatableWithFallback(this.id.getNamespace() + "." + this.id.getPath(),
                this.localizedName);
    }

    /**
     * The original's {@code UpgradeType#isCompatible} semantics, unchanged: a non-empty whitelist decides on
     * its own, otherwise a non-empty blacklist excludes, otherwise everything is compatible.
     */
    public boolean isCompatible(ResourceLocation machineId) {
        if (!this.compatibleMachines.isEmpty()) {
            return this.compatibleMachines.contains(machineId);
        }
        if (!this.incompatibleMachines.isEmpty()) {
            return !this.incompatibleMachines.contains(machineId);
        }
        return true;
    }

    /** True when the declaration names specific machines at all, i.e. compatibility is not "everything". */
    public boolean isRestricted() {
        return !this.compatibleMachines.isEmpty() || !this.incompatibleMachines.isEmpty();
    }

    @Override
    public String toString() {
        return "UpgradeType[" + this.id + ", maxStack=" + this.maxStackSize
                + (this.dynamic ? ", dynamic" : ", fixed") + (isRestricted() ? ", restricted" : "")
                + ", modifiers=" + this.modifiers.size() + (this.stackable ? ", stackable" : "") + "]";
    }
}
