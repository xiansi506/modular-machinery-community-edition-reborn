package com.reborn.modularmachinery.compat.magic;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.magic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import static com.reborn.modularmachinery.compat.magic.ApiReflection.*;

/** Blood Magic 3: bound orb -> SoulNetwork -> persistent LP cache; Will stays in its real world chunk. */
public final class BloodMagicBackend implements MagicBackend {
    private final MagicHatchBlockEntity hatch;
    private final Class<?> orbApi = type("wayoftime.bloodmagic.common.item.IBloodOrb");
    private final Class<?> bindingApi = type("wayoftime.bloodmagic.common.item.IBindable");
    private final Class<?> bindingType = type("wayoftime.bloodmagic.core.data.Binding");
    private final Class<?> networkApi = type("wayoftime.bloodmagic.core.data.SoulNetwork");
    private final Class<?> ticketType = type("wayoftime.bloodmagic.core.data.SoulTicket");
    private final Class<?> willApi = type("wayoftime.bloodmagic.demonaura.WorldDemonWillHandler");
    private final Class<?> willType = type("wayoftime.bloodmagic.api.compat.EnumDemonWillType");
    public BloodMagicBackend(MagicHatchBlockEntity hatch) { this.hatch = hatch; }
    public MagicFamily family() { return hatch.family(); }
    public boolean input() { return hatch.input(); }
    private ItemStack orb() { return hatch.orbs().getSlots() == 0 ? ItemStack.EMPTY : hatch.orbs().getStackInSlot(0); }
    public boolean acceptsOrb(ItemStack stack) { return !stack.isEmpty() && orbApi.isInstance(stack.getItem()) && bindingApi.isInstance(stack.getItem()); }
    public int orbCapacity() {
        ItemStack stack = orb(); if (!acceptsOrb(stack)) return 0;
        Object orb = call(orbApi,stack.getItem(),"getOrb",new Class<?>[]{ItemStack.class},stack);
        return orb == null ? 0 : Math.max(0,((Number)call(orb,"getCapacity",new Class<?>[0])).intValue());
    }
    private Object network() {
        ItemStack stack = orb(); if (!acceptsOrb(stack)) return null;
        try {
            Object binding = call(bindingApi,stack.getItem(),"getBinding",new Class<?>[]{ItemStack.class},stack);
            return binding == null ? null : call(type("wayoftime.bloodmagic.util.helper.NetworkHelper"),null,"getSoulNetwork",new Class<?>[]{bindingType},binding);
        } catch (IllegalArgumentException | NullPointerException malformedBinding) { return null; }
    }
    public boolean boundOrb() { return orbCapacity() > 0 && network() != null; }
    private Object ticket(int amount) {
        try { return ticketType.getConstructor(int.class).newInstance(amount); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @Override public void tick() {
        if (family() != MagicFamily.LIFEESSENCE || hatch.getLevel() == null || hatch.getLevel().isClientSide || hatch.getLevel().getGameTime() % 5 != 0) return;
        Object network = network(); int capacity = orbCapacity(); if (network == null || capacity <= 0) return;
        int before = ((Number)call(networkApi,network,"getCurrentEssence",new Class<?>[0])).intValue();
        if (input()) {
            int wanted = Math.min(Math.max(0,capacity/10-hatch.stored()),Math.max(0,before));
            if (wanted <= 0) return;
            int moved = ((Number)call(networkApi,network,"syphon",new Class<?>[]{ticketType},ticket(wanted))).intValue();
            if (moved < 0 || moved > Integer.MAX_VALUE-hatch.stored()) { call(networkApi,network,"setCurrentEssence",new Class<?>[]{int.class},before); return; }
            hatch.stored(hatch.stored()+moved);
        } else if (hatch.stored() > 0) {
            // Native SoulNetwork.add computes int (before + ticket.amount) before min(maximum, ...)
            // and returns an int (after - before). Both wrap for an extreme cached balance.
            // Work out a long-safe request bounded by the current orb's headroom and by int range;
            // anything beyond that stays in the cache for a later refill instead of being dropped.
            long balance = before;
            long request = Math.min((long) hatch.stored(),
                    Math.min((long) capacity - balance, (long) Integer.MAX_VALUE - balance));
            if (request <= 0) return;
            int moved, after;
            try {
                moved = ((Number)call(networkApi,network,"add",new Class<?>[]{ticketType,int.class},
                        ticket((int) request),capacity)).intValue();
                after = ((Number)call(networkApi,network,"getCurrentEssence",new Class<?>[0])).intValue();
            } catch (RuntimeException | LinkageError error) {
                // No blind refund and no balance rewrite: a Fill event may already have side effects.
                ModularMachineryReborn.LOGGER.warn("Blood Magic LP fill failed; hatch cache retained", error);
                return;
            }
            long credited = (long) after - balance;
            if (moved != credited || credited < 0 || credited > request) {
                // The observed network delta is authoritative. Settle by it only when it is in range;
                // otherwise keep the cache and make the inconsistency observable. Neither branch
                // claims to roll back the whole event or silently repair a negative balance.
                if (credited >= 0 && credited <= request) {
                    ModularMachineryReborn.LOGGER.warn("Blood Magic LP fill returned " + moved
                            + " but the network changed by " + credited + "; settling by the observed balance");
                    hatch.stored(hatch.stored() - (int) credited);
                } else {
                    ModularMachineryReborn.LOGGER.warn("Blood Magic LP fill inconsistency (moved=" + moved
                            + ", credited=" + credited + ", request=" + request + "); hatch cache retained");
                }
                return;
            }
            hatch.stored(hatch.stored() - (int) credited);
        }
    }
    public String[] variants() { return family() == MagicFamily.WILL ? new String[]{"default","corrosive","destructive","vengeful","steadfast"} : new String[]{""}; }
    private record ChunkKey(Level level, long pos) {}
    public Access access(String variant) {
        if (family() == MagicFamily.LIFEESSENCE) {
            if (!variant.isEmpty()) return null;
            ItemStack expected = orb().copy();
            return new Access() {
                public Object identity() { return hatch; }
                public boolean valid() { return !hatch.isRemoved() && hatch.getLevel() != null && !hatch.getLevel().isClientSide && boundOrb() && ItemStack.matches(expected,orb()); }
                public double amount() { return hatch.stored(); }
                public double minimum() { return 0; }
                public double maximum() { return Math.max(hatch.stored(), input() ? orbCapacity()/10 : orbCapacity()); }
                public double move(double delta, boolean simulate) {
                    if (!valid() || !Double.isFinite(delta)) return 0;
                    double n = delta >= 0 ? Math.min(Math.floor(delta),maximum()-amount()) : -Math.min(Math.floor(-delta),amount());
                    if (!simulate) hatch.stored((int)(amount()+n)); return n;
                }
                public void restore(Object snapshot) { hatch.stored(((Double)snapshot).intValue()); }
            };
        }
        if (!List.of(variants()).contains(variant)) return null;
        Level level = hatch.getLevel(); BlockPos pos = hatch.getBlockPos();
        if (level == null || level.isClientSide || !level.hasChunkAt(pos)) return null;
        Object type = constant(willType,variant);
        return new Access() {
            public Object identity() { return new ChunkKey(level,new net.minecraft.world.level.ChunkPos(pos).toLong()); }
            public boolean valid() { return !hatch.isRemoved() && hatch.getLevel() == level && level.hasChunkAt(pos); }
            public double amount() { return ((Number)call(willApi,null,"getCurrentWill",new Class<?>[]{Level.class,BlockPos.class,willType},level,pos,type)).doubleValue(); }
            public double minimum() { return 0; }
            public double maximum() { return Double.MAX_VALUE; }
            public boolean linearParallelPlanning() { return true; }
            public double parallelQuantum(double lower, double upper) {
                return lower > 0 && Double.isFinite(upper) ? Math.ulp(lower) : Double.NaN;
            }
            public double move(double delta, boolean simulate) {
                if (!valid() || !Double.isFinite(delta)) return 0;
                double n = delta >= 0 ? delta : -Math.min(-delta,amount());
                double moved = ((Number)call(willApi,null,n >= 0 ? "fillWill" : "drainWill",new Class<?>[]{Level.class,BlockPos.class,willType,double.class,boolean.class},level,pos,type,Math.abs(n),!simulate)).doubleValue();
                return n >= 0 ? moved : -moved;
            }
        };
    }
}
