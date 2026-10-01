package com.reborn.modularmachinery.machine;

import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The hatches belonging to one formed machine, bucketed by family and direction.
 *
 * <p>The original collected its structure's {@code MachineComponent}s the same way and then let the recipe
 * requirement engine draw from whichever component could serve a requirement. M3 keeps the simple recipe model
 * of M1 but routes it through these ports instead of the controller's own slots: items come from item input
 * hatches, FE from energy input hatches, and results go to item output hatches.
 *
 * <p>Fluid hatches are collected and fully functional as storage (pipes can fill and drain them), but the
 * current recipe type has no fluid fields — those arrive with the requirement engine.
 *
 * <h2>M6d-b: the smart-interface values travel with the ports</h2>
 *
 * <p>{@code interface_number_input} is a requirement that reads a number the controller holds rather than one a
 * hatch holds, and the recipe lifecycle has no per-controller context object to pass it through
 * ({@code RecipeCraftingContext} is M2's deliberate omission — the modifiers are method parameters instead). The
 * per-craft object the lifecycle does already pass to every requirement is this one, and it is rebuilt from the
 * controller on every structure check, so the value source rides along here.
 *
 * <p>It is a {@link java.util.function.Supplier} rather than a snapshot because a player may edit the value
 * while a craft is running: the requirement re-reads it on each check, which is what reading a live block
 * entity's field did in the original.
 */
public final class HatchCollection {

    public static final HatchCollection EMPTY = new HatchCollection(List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), () -> SmartInterfaceValueSource.NONE, () -> null);

    private final List<IItemHandler> itemInputs;
    private final List<IItemHandler> itemOutputs;
    private final List<IFluidHandler> fluidInputs;
    private final List<IFluidHandler> fluidOutputs;
    private final List<IEnergyStorage> energyInputs;
    private final List<IEnergyStorage> energyOutputs;

    /**
     * Where a smart-interface requirement reads its value. Never {@code null}: {@link
     * SmartInterfaceValueSource#NONE} is the honest answer for a collection built without a controller, and it
     * answers {@code null} for every type — which is the original's "no interface of that type found" case.
     */
    private final java.util.function.Supplier<SmartInterfaceValueSource> smartInterfaces;

    /**
     * The formed machine's definition, for the one thing a requirement needs from it: the interface type's own
     * {@code notequal} message. It is a supplier for the same reason the values are — the machine a controller
     * forms can change between structure checks.
     */
    private final java.util.function.Supplier<MachineDefinition> machine;

    private HatchCollection(List<IItemHandler> itemInputs, List<IItemHandler> itemOutputs,
                            List<IFluidHandler> fluidInputs, List<IFluidHandler> fluidOutputs,
                            List<IEnergyStorage> energyInputs, List<IEnergyStorage> energyOutputs,
                            java.util.function.Supplier<SmartInterfaceValueSource> smartInterfaces,
                            java.util.function.Supplier<MachineDefinition> machine) {
        this.itemInputs = List.copyOf(itemInputs);
        this.itemOutputs = List.copyOf(itemOutputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.energyInputs = List.copyOf(energyInputs);
        this.energyOutputs = List.copyOf(energyOutputs);
        this.smartInterfaces = smartInterfaces;
        this.machine = machine;
    }

    public List<IItemHandler> itemInputs() {
        return this.itemInputs;
    }

    public List<IItemHandler> itemOutputs() {
        return this.itemOutputs;
    }

    public List<IFluidHandler> fluidInputs() {
        return this.fluidInputs;
    }

    public List<IFluidHandler> fluidOutputs() {
        return this.fluidOutputs;
    }

    public List<IEnergyStorage> energyInputs() {
        return this.energyInputs;
    }

    public List<IEnergyStorage> energyOutputs() {
        return this.energyOutputs;
    }

    /**
     * Where a smart-interface requirement reads the value it compares against: the controller this collection was
     * gathered from.
     *
     * <p>Called on every check rather than captured once, so an edit made while a craft runs is seen at once —
     * the original read the live {@code SmartInterfaceData} object the same way.
     */
    public SmartInterfaceValueSource smartInterfaces() {
        SmartInterfaceValueSource source = this.smartInterfaces.get();
        return source == null ? SmartInterfaceValueSource.NONE : source;
    }

    /** The value stored for one declared interface type, or {@code null} when there is none. */
    @javax.annotation.Nullable
    public Float smartInterfaceValue(String type) {
        return smartInterfaces().valueOf(type);
    }

    /**
     * The formed machine's declared interface type of that name, or {@code null} when the machine declares no
     * such type — the original's {@code DynamicMachine#getSmartInterfaceType}, reached the same way the value is.
     */
    @javax.annotation.Nullable
    public SmartInterfaceType declaredSmartInterface(String type) {
        MachineDefinition definition = this.machine.get();
        return definition == null ? null : definition.smartInterface(type);
    }

    public boolean isEmpty() {
        return this.itemInputs.isEmpty() && this.itemOutputs.isEmpty() && this.fluidInputs.isEmpty()
                && this.fluidOutputs.isEmpty() && this.energyInputs.isEmpty() && this.energyOutputs.isEmpty();
    }

    public int hatchCount() {
        return this.itemInputs.size() + this.itemOutputs.size() + this.fluidInputs.size()
                + this.fluidOutputs.size() + this.energyInputs.size() + this.energyOutputs.size();
    }

    /** Total FE currently held by the structure's energy input hatches. */
    public long storedEnergy() {
        long total = 0L;
        for (IEnergyStorage storage : this.energyInputs) {
            total += storage.getEnergyStored();
        }
        return total;
    }

    /** Total FE the structure's energy input hatches can hold. */
    public long storedEnergyCapacity() {
        long total = 0L;
        for (IEnergyStorage storage : this.energyInputs) {
            total += storage.getMaxEnergyStored();
        }
        return total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<IItemHandler> itemInputs = new ArrayList<>();
        private final List<IItemHandler> itemOutputs = new ArrayList<>();
        private final List<IFluidHandler> fluidInputs = new ArrayList<>();
        private final List<IFluidHandler> fluidOutputs = new ArrayList<>();
        private final List<IEnergyStorage> energyInputs = new ArrayList<>();
        private final List<IEnergyStorage> energyOutputs = new ArrayList<>();

        /** Defaults to "nothing", so a collection built without a controller keeps behaving as it always did. */
        private SmartInterfaceValueSource smartInterfaces = SmartInterfaceValueSource.NONE;

        /** Defaults to "no machine", for the same reason. */
        private java.util.function.Supplier<MachineDefinition> machine = () -> null;

        /**
         * Where smart-interface values come from — the controller the structure was gathered from. The single
         * argument is the very object whose own accessor the requirement needs, which is the original's access
         * path with the one intermediate block removed.
         */
        public Builder smartInterfaces(SmartInterfaceValueSource source) {
            this.smartInterfaces = source == null ? SmartInterfaceValueSource.NONE : source;
            return this;
        }

        /** The formed machine's definition, for the interface type's own {@code notequal} message. */
        public Builder machine(MachineDefinition definition) {
            this.machine = () -> definition;
            return this;
        }

        /** Routes one hatch's storage into the bucket its kind and direction call for. */
        public Builder add(com.reborn.modularmachinery.block.HatchKind kind,
                           IItemHandler items, IFluidHandler fluids, IEnergyStorage energy) {
            switch (kind.family()) {
                case ITEM -> {
                    if (items != null) {
                        (kind.isInput() ? this.itemInputs : this.itemOutputs).add(items);
                    }
                }
                case FLUID -> {
                    if (fluids != null) {
                        (kind.isInput() ? this.fluidInputs : this.fluidOutputs).add(fluids);
                    }
                }
                case ENERGY -> {
                    if (energy != null) {
                        (kind.isInput() ? this.energyInputs : this.energyOutputs).add(energy);
                    }
                }
            }
            return this;
        }

        /**
         * Registers a bare handler as a port. Used to fold the controller's own slots and buffer in as fallback
         * ports so the recipe engine sees a single uniform set of ports.
         */
        public Builder addItemHandler(IItemHandler handler, boolean input) {
            if (handler != null) {
                (input ? this.itemInputs : this.itemOutputs).add(handler);
            }
            return this;
        }

        public Builder addFluidHandler(IFluidHandler handler, boolean input) {
            if (handler != null) {
                (input ? this.fluidInputs : this.fluidOutputs).add(handler);
            }
            return this;
        }

        public Builder addEnergyStorage(IEnergyStorage storage, boolean input) {
            if (storage != null) {
                (input ? this.energyInputs : this.energyOutputs).add(storage);
            }
            return this;
        }

        public HatchCollection build() {
            return new HatchCollection(this.itemInputs, this.itemOutputs, this.fluidInputs, this.fluidOutputs,
                    this.energyInputs, this.energyOutputs, () -> this.smartInterfaces, this.machine);
        }
    }
}
