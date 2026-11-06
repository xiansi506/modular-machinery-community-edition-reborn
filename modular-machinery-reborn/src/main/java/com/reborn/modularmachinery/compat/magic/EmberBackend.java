package com.reborn.modularmachinery.compat.magic;

import com.reborn.modularmachinery.magic.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import java.lang.reflect.*;
import static com.reborn.modularmachinery.compat.magic.ApiReflection.*;

/** Embers Rekindled 1.4.7 capability, including the receiver's addAmount transfer contract. */
public final class EmberBackend implements MagicBackend {
    private final MagicHatchBlockEntity hatch;
    private final Capability<?> capability = (Capability<?>)field(type("com.rekindled.embers.api.capabilities.EmbersCapabilities"), "EMBER_CAPABILITY");
    private final Object ember;
    private LazyOptional<Object> view;
    private double stored;
    // The config requires reloading the block entity. Keep capacity stable throughout its transactions.
    private final double serverCapacity = MagicConfig.emberCapacity();
    private double clientCapacity = serverCapacity;
    private boolean valid = true;
    public EmberBackend(MagicHatchBlockEntity hatch) {
        this.hatch = hatch;
        Class<?> api = type("com.rekindled.embers.api.power.IEmberCapability");
        ember = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, this::invoke);
        view = LazyOptional.of(() -> ember);
    }
    public MagicFamily family() { return MagicFamily.EMBER; }
    public boolean input() { return hatch.input(); }
    public double capacity() { return hatch.getLevel() != null && hatch.getLevel().isClientSide ? clientCapacity : serverCapacity; }
    public double stored() { return stored; }
    private boolean active() { return valid && !hatch.isRemoved() && hatch.getLevel() != null && !hatch.getLevel().isClientSide; }
    private void stored(double amount) {
        if (!Double.isFinite(amount)) return;
        double next = Math.max(0, Math.min(capacity(), amount));
        if (next != stored) { stored = next; hatch.changed(); }
    }
    public LazyOptional<?> capability(Capability<?> queried) { return queried == capability ? view : null; }
    private Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0]; case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "MMR Ember capability";
            case "getEmber" -> stored; case "getEmberCapacity" -> capacity();
            case "setEmber" -> { if (active()) stored((double)args[0]); yield null; }
            // Capacity belongs to the hatch configuration, not an externally supplied NBT value.
            case "setEmberCapacity" -> null;
            case "addAmount", "removeAmount" -> {
                double wanted = (double)args[0]; boolean execute = (boolean)args[1];
                boolean adding = method.getName().equals("addAmount");
                double moved = !active() || !Double.isFinite(wanted) || wanted < 0 ? 0
                        : Math.min(wanted, Math.max(0, adding ? capacity()-stored : stored));
                if (execute && moved > 0) stored(stored+(adding ? moved : -moved));
                yield moved;
            }
            case "acceptsVolatile" -> capacity() >= 3200;
            case "serializeNBT" -> data();
            case "writeToNBT" -> { ((CompoundTag)args[0]).merge(data()); yield null; }
            case "deserializeNBT" -> { if (active()) read((CompoundTag)args[0]); yield null; }
            case "onContentsChanged" -> { hatch.changed(); yield null; }
            case "invalidate" -> { invalidate(); yield null; }
            case "getCapability" -> args[0] == capability ? view : LazyOptional.empty();
            default -> { if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args); throw new UnsupportedOperationException(method.toString()); }
        };
    }
    private CompoundTag data() { CompoundTag tag = new CompoundTag(); tag.putDouble("embers:ember", stored); tag.putDouble("embers:ember_capacity", capacity()); return tag; }
    @Override public void save(CompoundTag tag) { tag.put("emberStorage", data()); }
    @Override public void read(CompoundTag tag) {
        CompoundTag data = tag.contains("emberStorage") ? tag.getCompound("emberStorage") : tag;
        if (hatch.getLevel() != null && hatch.getLevel().isClientSide && data.contains("embers:ember_capacity")) {
            double capacity = data.getDouble("embers:ember_capacity");
            if (Double.isFinite(capacity) && capacity >= 0 && capacity <= 1000000000d) clientCapacity = capacity;
        }
        double amount = data.getDouble("embers:ember"); stored(Double.isFinite(amount) ? amount : 0);
    }
    @Override public void invalidate() { valid = false; view.invalidate(); }
    @Override public void revive() { valid = true; view = LazyOptional.of(() -> ember); }
    public Access access(String variant) {
        if (!variant.isEmpty()) return null;
        return new Access() {
            public Object identity() { return EmberBackend.this; }
            public double amount() { return stored; }
            public double minimum() { return 0; }
            public double maximum() { return capacity(); }
            public boolean linearParallelPlanning() { return true; }
            public double parallelQuantum(double lower, double upper) {
                return lower > 0 && Double.isFinite(upper) ? Math.ulp(lower) : Double.NaN;
            }
            public boolean valid() { return active(); }
            public double move(double delta, boolean simulate) {
                if (!valid() || !Double.isFinite(delta)) return 0;
                double moved = delta >= 0 ? Math.min(delta, Math.max(0, capacity()-stored)) : -Math.min(-delta, Math.max(0, stored));
                if (!simulate) stored(stored+moved);
                return moved;
            }
            public void restore(Object snapshot) { stored((Double)snapshot); }
        };
    }
}
