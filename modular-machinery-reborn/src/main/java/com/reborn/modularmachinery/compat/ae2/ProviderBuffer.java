package com.reborn.modularmachinery.compat.ae2;

import appeng.api.stacks.*;
import com.reborn.modularmachinery.recipe.ResourceTransaction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;
import java.util.function.*;

/** Unbounded distinct entries and long quantities; one owner snapshot covers all three media. */
public final class ProviderBuffer implements ResourceTransaction.OpaquePort {
    private static Predicate<AEKey> chemicalKey = key -> false;
    private static Function<ProviderBuffer,Object> gasFactory = buffer -> null;
    public static void installGas(Predicate<AEKey> predicate, Function<ProviderBuffer,Object> factory) {
        chemicalKey = Objects.requireNonNull(predicate); gasFactory = Objects.requireNonNull(factory);
    }
    private final LinkedHashMap<AEKey,Long> contents = new LinkedHashMap<>();
    private ListTag unresolved = new ListTag();
    private final Runnable changed;
    private final BooleanSupplier accessible;
    private long revision;
    private long keyRevision;
    private final boolean retainEmptyKeys;
    private final Items items = new Items();
    private final Fluids fluids = new Fluids();
    private Object gas;
    private Supplier<Consumer<com.reborn.modularmachinery.block.MachineControllerBlockEntity>> consumption = () -> controller -> {};
    public ProviderBuffer(Runnable changed, BooleanSupplier accessible) { this(changed, accessible, false); }
    public ProviderBuffer(Runnable changed, BooleanSupplier accessible,
                          Supplier<Consumer<com.reborn.modularmachinery.block.MachineControllerBlockEntity>> consumption) {
        this(changed, accessible, false);
        this.consumption = consumption;
    }
    private ProviderBuffer(Runnable changed, BooleanSupplier accessible, boolean retainEmptyKeys) {
        this.changed=changed; this.accessible=accessible; this.retainEmptyKeys=retainEmptyKeys;
    }
    public boolean accessible() { return accessible.getAsBoolean(); }
    public static boolean supports(AEKey key) { return key instanceof AEItemKey || key instanceof AEFluidKey || chemicalKey.test(key); }
    public long amount(AEKey key) { return contents.getOrDefault(key,0L); }
    public Map<AEKey,Long> entries() {
        Map<AEKey,Long> result=new LinkedHashMap<>(); contents.forEach((key,n)->{if(n>0) result.put(key,n);});
        return Collections.unmodifiableMap(result);
    }
    public List<AEKey> keys(Predicate<AEKey> filter) { return contents.keySet().stream().filter(filter).toList(); }
    public boolean isEmpty() { return unresolved.isEmpty() && contents.values().stream().noneMatch(n->n>0); }
    private void changed() { revision++; changed.run(); }
    public boolean accept(KeyCounter[] holders) {
        LinkedHashMap<AEKey,Long> next=new LinkedHashMap<>(contents);
        try {
            for(KeyCounter holder:holders) for(var entry:holder) {
                long n=entry.getLongValue(); AEKey key=entry.getKey();
                if(n<0 || !supports(key)) return false;
                if(n>0) next.put(key,Math.addExact(next.getOrDefault(key,0L),n));
            }
        } catch(ArithmeticException overflow) { return false; }
        contents.clear(); contents.putAll(next); keyRevision++; changed(); return true;
    }
    public long take(AEKey key,long requested,boolean execute) {
        if(!accessible() || requested<=0) return 0;
        long moved=Math.min(requested,amount(key));
        if(moved>0&&execute){observeConsumption();updateAmount(key,amount(key)-moved);changed();}
        return moved;
    }
    public long returnToNetwork(ToLongBiFunction<AEKey,Long> insert) {
        long total=0;
        for(var entry:entries().entrySet()) {
            long moved=Math.max(0,Math.min(entry.getValue(),insert.applyAsLong(entry.getKey(),entry.getValue())));
            if(moved>0){updateAmount(entry.getKey(),amount(entry.getKey())-moved);changed(); total=total>Long.MAX_VALUE-moved?Long.MAX_VALUE:total+moved;}
        }
        return total;
    }
    private void updateAmount(AEKey key, long amount) {
        if (amount == 0 && !retainEmptyKeys) { contents.remove(key); keyRevision++; }
        else contents.put(key,amount);
    }
    private void replaceContents(Map<AEKey,Long> values, ListTag missing) {
        observeConsumption();
        contents.clear();
        values.forEach((key,n)->{if (n>0 || retainEmptyKeys) contents.put(key,n);});
        unresolved=missing.copy(); keyRevision++; changed();
    }
    private void observeConsumption() {
        if (!com.reborn.modularmachinery.machine.PaidInputScope.capturing()) return;
        Map<AEKey,Long> before = new LinkedHashMap<>(contents);
        Consumer<com.reborn.modularmachinery.block.MachineControllerBlockEntity> owner = consumption.get();
        com.reborn.modularmachinery.machine.PaidInputScope.observe(this,
                () -> before.entrySet().stream().anyMatch(e -> amount(e.getKey()) < e.getValue()), owner);
    }
    public IItemHandler items() { return items; }
    public IFluidHandler fluids() { return fluids; }
    public Object gas() { if(gas==null) gas=gasFactory.apply(this); return gas; }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag(); ListTag list=new ListTag();
        for(var entry:entries().entrySet()) {
            CompoundTag value=new CompoundTag(); value.put("key",entry.getKey().toTagGeneric()); value.putLong("amount",entry.getValue());list.add(value);
        }
        for(Tag value:unresolved) list.add(value.copy());
        tag.put("entries",list); return tag;
    }
    public void load(CompoundTag tag) {
        contents.clear(); unresolved=new ListTag();
        for(Tag raw:tag.getList("entries",Tag.TAG_COMPOUND)) {
            CompoundTag value=(CompoundTag)raw; long n=value.getLong("amount");
            if(n<=0) continue;
            AEKey key=AEKey.fromTagGeneric(value.getCompound("key"));
            if(key==null || !supports(key) || amount(key)>Long.MAX_VALUE-n) {unresolved.add(value.copy());continue;}
            contents.put(key,amount(key)+n);
        }
        keyRevision++; revision++;
    }
    public int rowCount() { return (int)contents.values().stream().filter(n -> n > 0).count() + (unresolved.isEmpty() ? 0 : 1); }
    public List<CompoundTag> preview(int slot,int limit) { return preview(slot,limit,0); }
    public List<CompoundTag> preview(int slot,int limit,int skip) {
        List<CompoundTag> list=new ArrayList<>();
        for(var entry:entries().entrySet()) {
            if(skip>0){skip--;continue;}
            if(list.size()>=limit) break;
            CompoundTag row=new CompoundTag();row.putInt("slot",slot);row.putLong("amount",entry.getValue());
            Component display=entry.getKey().getDisplayName();
            String displayJson=Component.Serializer.toJson(display);
            if(displayJson.length()>2048){String plain=display.getString();displayJson=Component.Serializer.toJson(Component.literal(plain.substring(0,Math.min(256,plain.length()))+"…"));}
            row.putString("name",displayJson);
            AEKey key=entry.getKey();
            row.putString("kind",key instanceof AEItemKey?"item":key instanceof AEFluidKey?"fluid":"gas");
            if(key instanceof AEItemKey item){
                ItemStack stack=item.toStack();CompoundTag icon=stack.save(new CompoundTag());
                // Filled containers can carry entire inventories; previews need an icon, not that storage.
                if(icon.sizeInBytes()>4096)icon=new ItemStack(stack.getItem()).save(new CompoundTag());
                row.put("icon",icon);
            }
            list.add(row);
        }
        if(!unresolved.isEmpty() && skip==0 && list.size()<limit) {
            CompoundTag row=new CompoundTag();row.putInt("slot",slot);row.putLong("amount",unresolved.size());row.putString("kind","unknown");
            row.putString("name",Component.Serializer.toJson(Component.translatable("gui.modular_machinery_reborn.mepatternprovider.unknown")));list.add(row);
        }
        return list;
    }
    @Override public Object transactionView(ResourceTransaction tx) {
        var before=new LinkedHashMap<>(contents); var missing=unresolved.copy(); long expected=revision;
        // Keep the shadow's slot order fixed while several requirements consume different keys.
        ProviderBuffer shadow=new ProviderBuffer(()->{},accessible,true);shadow.contents.putAll(before);shadow.unresolved=missing.copy();
        tx.enlist(()->revision==expected && accessible(),()->replaceContents(before,missing));
        tx.record(()->{replaceContents(shadow.contents,shadow.unresolved);return true;});
        return shadow;
    }
    private final class Items implements IItemHandler,ResourceTransaction.OpaquePort {
        private long cachedRevision=-1;
        private List<AEKey> cachedKeys=List.of();
        private List<AEKey> keys(){
            if(cachedRevision!=keyRevision){cachedKeys=ProviderBuffer.this.keys(k->k instanceof AEItemKey);cachedRevision=keyRevision;}
            return cachedKeys;
        }
        @Override public int getSlots(){return keys().size();}
        @Override public ItemStack getStackInSlot(int slot){var keys=keys();return !accessible()||slot<0||slot>=keys.size()?ItemStack.EMPTY:((AEItemKey)keys.get(slot)).toStack((int)Math.min(Integer.MAX_VALUE,amount(keys.get(slot))));}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return stack;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){var keys=keys();if(slot<0||slot>=keys.size())return ItemStack.EMPTY;var key=(AEItemKey)keys.get(slot);return key.toStack((int)take(key,amount,!simulate));}
        @Override public int getSlotLimit(int slot){return Integer.MAX_VALUE;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return false;}
        @Override public Object transactionView(ResourceTransaction tx){return ((ProviderBuffer)tx.opaque(ProviderBuffer.this)).items();}
    }
    private final class Fluids implements IFluidHandler,ResourceTransaction.OpaquePort {
        private long cachedRevision=-1;
        private List<AEKey> cachedKeys=List.of();
        private List<AEKey> keys(){
            if(cachedRevision!=keyRevision){cachedKeys=ProviderBuffer.this.keys(k->k instanceof AEFluidKey);cachedRevision=keyRevision;}
            return cachedKeys;
        }
        @Override public int getTanks(){return keys().size();}
        @Override public FluidStack getFluidInTank(int tank){var keys=keys();return !accessible()||tank<0||tank>=keys.size()?FluidStack.EMPTY:((AEFluidKey)keys.get(tank)).toStack((int)Math.min(Integer.MAX_VALUE,amount(keys.get(tank))));}
        @Override public int getTankCapacity(int tank){return Integer.MAX_VALUE;}
        @Override public boolean isFluidValid(int tank,FluidStack stack){return false;}
        @Override public int fill(FluidStack stack,FluidAction action){return 0;}
        @Override public FluidStack drain(FluidStack stack,FluidAction action){if(stack.isEmpty())return FluidStack.EMPTY;var key=AEFluidKey.of(stack);return key==null?FluidStack.EMPTY:key.toStack((int)take(key,stack.getAmount(),action.execute()));}
        @Override public FluidStack drain(int amount,FluidAction action){for(AEKey key:keys()){long moved=take(key,amount,action.execute());if(moved>0)return ((AEFluidKey)key).toStack((int)moved);}return FluidStack.EMPTY;}
        @Override public Object transactionView(ResourceTransaction tx){return ((ProviderBuffer)tx.opaque(ProviderBuffer.this)).fluids();}
    }
}
