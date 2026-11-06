package com.reborn.modularmachinery.compat.ae2;

import appeng.api.stacks.*;
import com.reborn.modularmachinery.machine.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import java.util.*;
import java.util.function.*;
import static com.reborn.modularmachinery.compat.ae2.MePatternProviderBlockEntity.WorkMode;

/** Server-owned storage and mode state. A pending return never discards an unaccepted remainder. */
public final class ProviderState {
    private final Runnable changed, topologyChanged;
    private final BooleanSupplier available;
    public final ProviderBuffer shared;
    public final ProviderBuffer[] isolated = new ProviderBuffer[36];
    public final ItemStackHandler manualItems;
    public final FluidTank manualFluid;
    private final BitSet returning = new BitSet(36);
    private boolean returnAll, locked;
    private WorkMode mode = WorkMode.DEFAULT, pendingMode;
    private AEItemKey activePattern;
    private int group = -1;
    private int[] groups = new int[36];
    private UUID inputIncarnation = UUID.randomUUID();
    private boolean identityNeedsSave = true;
    private UUID lockCycle = UUID.randomUUID();
    public UUID lockCycle() { return lockCycle; }
    public void persistInputIdentity() { if(identityNeedsSave){identityNeedsSave=false;changed.run();} }
    public ProviderState(Runnable changed, Runnable topologyChanged, BooleanSupplier available) {
        this(changed,topologyChanged,available,controller -> {});
    }
    public ProviderState(Runnable changed, Runnable topologyChanged, BooleanSupplier available,
                         Consumer<com.reborn.modularmachinery.block.MachineControllerBlockEntity> consumed) {
        this.changed=changed; this.topologyChanged=topologyChanged;this.available=available;
        Arrays.fill(groups,-1);
        shared=new ProviderBuffer(changed,()->available.getAsBoolean()&&!returnAll,()->{
            UUID observed=lockCycle;
            return controller -> {if(locked&&observed.equals(lockCycle))consumed.accept(controller);};
        });
        for(int i=0;i<isolated.length;i++){ final int slot=i; isolated[i]=new ProviderBuffer(changed,()->available.getAsBoolean()&&!returnAll&&!returning.get(slot)); }
        manualItems=new com.reborn.modularmachinery.recipe.ResourceTransaction.NativeItems(2){@Override protected void onContentsChanged(int slot){changed.run();}};
        manualFluid=new com.reborn.modularmachinery.recipe.ResourceTransaction.NativeTank(Integer.MAX_VALUE){@Override protected void onContentsChanged(){changed.run();}};
    }
    public WorkMode mode(){return mode;}
    public int group(){return group;}
    public boolean pending(){return returnAll||!returning.isEmpty();}
    public boolean busy(){return returnAll || mode==WorkMode.CRAFTING_LOCK_MODE&&locked || mode==WorkMode.BLOCKING_MODE&&!shared.isEmpty();}
    public boolean accept(int slot,AEItemKey pattern,KeyCounter[] inputs) {
        if(slot<0||slot>=36||pattern==null||busy()||returning.get(slot))return false;
        if(mode==WorkMode.ENHANCED_BLOCKING_MODE&&!shared.isEmpty()&&activePattern!=null&&!pattern.equals(activePattern))return false;
        ProviderBuffer buffer=mode==WorkMode.ISOLATION_INPUT?isolated[slot]:shared;
        if(!buffer.accept(inputs))return false;
        activePattern=pattern;
        if(mode==WorkMode.CRAFTING_LOCK_MODE){lockCycle=UUID.randomUUID();locked=true;}
        changed.run();return true;
    }
    public boolean locked(){return locked;}
    public void unlock(){if(locked){locked=false;changed.run();}}
    public void setMode(WorkMode next){
        if(returnAll){pendingMode=next;changed.run();return;}
        if(next==mode)return;
        if((mode==WorkMode.ISOLATION_INPUT)!=(next==WorkMode.ISOLATION_INPUT)){
            pendingMode=next;requestReturnAll();
        }else {mode=next;locked=false;activePattern=null;changed.run();topologyChanged.run();}
    }
    public void setGroup(int value){if(value< -1||value>65535||value==group)return;group=value;changed.run();topologyChanged.run();}
    public void patternChanged(int slot){if(!isolated[slot].isEmpty()){returning.set(slot);changed.run();}}
    public void requestReturnAll(){returnAll=true;locked=false;returning.set(0,36);changed.run();topologyChanged.run();}
    public void tickReturn(ToLongBiFunction<AEKey,Long> insert){
        if(!pending())return;
        if(returnAll) shared.returnToNetwork(insert);
        for(int i=returning.nextSetBit(0);i>=0;i=returning.nextSetBit(i+1)){
            isolated[i].returnToNetwork(insert);if(isolated[i].isEmpty()){returning.clear(i);changed.run();}
        }
        if(returnAll&&returning.isEmpty()&&shared.isEmpty()){
            returnAll=false; if(pendingMode!=null){mode=pendingMode;pendingMode=null;}activePattern=null;
            changed.run();topologyChanged.run();
        }
    }
    public void appendInputs(HatchCollection.Builder builder,String tag,MachineInputProvider.GroupAllocator allocator){
        appendInputs(builder,tag,allocator,new net.minecraft.resources.ResourceLocation("modular_machinery_reborn:unscoped"),net.minecraft.core.BlockPos.ZERO);
    }
    public void appendInputs(HatchCollection.Builder builder,String tag,MachineInputProvider.GroupAllocator allocator,
                             net.minecraft.resources.ResourceLocation dimension,net.minecraft.core.BlockPos position){
        persistInputIdentity();
        // Manual inputs are shared by every isolated pattern, as in the original provider.
        int manualGroup=mode==WorkMode.ISOLATION_INPUT?-1:group;
        builder.addItemPort(new GuardedItems(),true,tag,manualGroup);
        builder.addFluidPort(new GuardedFluid(),true,tag,manualGroup);
        if(mode==WorkMode.ISOLATION_INPUT){
            for(int i=0;i<36;i++){
                int allocated=allocator.reserve(isolated[i],groups[i]);
                if(allocated<0)continue; // Exhaustion must never turn an isolated buffer into general inputs.
                builder.isolatedGroup(allocated,new InputGroupBinding.Key(inputIncarnation,i,dimension,position.asLong()));
                append(builder,tag,allocated,isolated[i]);
            }
        }else append(builder,tag,group,shared);
    }
    private static void append(HatchCollection.Builder builder,String tag,int group,ProviderBuffer buffer){
        builder.addItemPort(buffer.items(),true,tag,group).addFluidPort(buffer.fluids(),true,tag,group);
        Object gas=buffer.gas();if(gas!=null)builder.addGasPort(gas,true,tag,group);
    }
    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putInt("mode",mode.ordinal());tag.putInt("group",group);tag.putIntArray("groups",groups.clone());
        tag.putUUID("input_incarnation",inputIncarnation);
        tag.putUUID("lock_cycle",lockCycle);
        tag.putBoolean("locked",locked);tag.putBoolean("return_all",returnAll);tag.putLongArray("returning",returning.toLongArray());
        if(pendingMode!=null)tag.putInt("pending_mode",pendingMode.ordinal());
        if(activePattern!=null)tag.put("active",activePattern.toTagGeneric());
        tag.put("shared",shared.save());ListTag buffers=new ListTag();for(var buffer:isolated)buffers.add(buffer.save());tag.put("isolated",buffers);
        tag.put("manual_items",manualItems.serializeNBT());tag.put("manual_fluid",manualFluid.writeToNBT(new CompoundTag()));return tag;
    }
    public void load(CompoundTag tag,int legacyMode,boolean legacyLock){
        if(tag.hasUUID("lock_cycle"))lockCycle=tag.getUUID("lock_cycle");
        if(tag.hasUUID("input_incarnation")){inputIncarnation=tag.getUUID("input_incarnation");identityNeedsSave=false;}
        else identityNeedsSave=true; // Keep this object's generated identity stable across repeated legacy loads.
        mode=readMode(tag.contains("mode")?tag.getInt("mode"):legacyMode);locked=tag.contains("mode")?tag.getBoolean("locked"):legacyLock&&mode==WorkMode.CRAFTING_LOCK_MODE;
        group=tag.contains("group")?Math.max(-1,Math.min(65535,tag.getInt("group"))):-1;
        groups=tag.getIntArray("groups").clone();if(groups.length!=36){groups=new int[36];Arrays.fill(groups,-1);}
        pendingMode=tag.contains("pending_mode")?readMode(tag.getInt("pending_mode")):null;
        returnAll=tag.getBoolean("return_all");returning.clear();BitSet saved=BitSet.valueOf(tag.getLongArray("returning"));for(int i=0;i<36;i++)if(saved.get(i))returning.set(i);
        AEKey key=tag.contains("active")?AEKey.fromTagGeneric(tag.getCompound("active")):null;activePattern=key instanceof AEItemKey item?item:null;
        shared.load(tag.getCompound("shared"));ListTag list=tag.getList("isolated",Tag.TAG_COMPOUND);for(int i=0;i<36;i++)isolated[i].load(i<list.size()?list.getCompound(i):new CompoundTag());
        manualItems.deserializeNBT(tag.getCompound("manual_items"));manualFluid.readFromNBT(tag.getCompound("manual_fluid"));
    }
    private static WorkMode readMode(int value){return value>=0&&value<WorkMode.values().length?WorkMode.values()[value]:WorkMode.DEFAULT;}
    public CompoundTag summary(){return summary(0);}
    public CompoundTag summary(int requestedPage){
        CompoundTag tag=new CompoundTag();tag.putInt("group",group);tag.putBoolean("pending",pending());tag.putBoolean("return_all",returnAll);tag.putBoolean("locked",locked);
        if(pendingMode!=null)tag.putInt("pending_mode",pendingMode.ordinal());
        int total=shared.rowCount();for(var buffer:isolated)total+=buffer.rowCount();
        int pages=Math.max(1,(total+31)/32),page=Math.max(0,Math.min(pages-1,requestedPage)),skip=page*32;
        tag.putInt("page",page);tag.putInt("pages",pages);tag.putInt("total",total);
        ListTag rows=new ListTag();
        List<ProviderBuffer> all=new ArrayList<>();all.add(shared);all.addAll(Arrays.asList(isolated));
        for(int i=0;i<all.size()&&rows.size()<32;i++){
            var buffer=all.get(i);int count=buffer.rowCount();if(skip>=count){skip-=count;continue;}
            for(var row:buffer.preview(i-1,32-rows.size(),skip))rows.add(row);skip=0;
        }
        tag.put("rows",rows);tag.put("manual_fluid",manualFluid.writeToNBT(new CompoundTag()));return tag;
    }
    private boolean readable(){return available.getAsBoolean()&&!returnAll;}
    private final class GuardedItems implements net.minecraftforge.items.IItemHandler,com.reborn.modularmachinery.recipe.ResourceTransaction.OpaquePort {
        public int getSlots(){return 2;} public ItemStack getStackInSlot(int slot){return readable()?manualItems.getStackInSlot(slot):ItemStack.EMPTY;}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return stack;}
        public ItemStack extractItem(int slot,int n,boolean simulate){return readable()?manualItems.extractItem(slot,n,simulate):ItemStack.EMPTY;}
        public int getSlotLimit(int slot){return manualItems.getSlotLimit(slot);}public boolean isItemValid(int slot,ItemStack stack){return false;}
        public Object transactionView(com.reborn.modularmachinery.recipe.ResourceTransaction tx){tx.enlist(ProviderState.this::readable,()->{});return tx.items(manualItems);}
    }
    private final class GuardedFluid implements net.minecraftforge.fluids.capability.IFluidHandler,com.reborn.modularmachinery.recipe.ResourceTransaction.OpaquePort {
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int tank){return readable()?manualFluid.getFluidInTank(tank):FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return manualFluid.getCapacity();}public boolean isFluidValid(int tank,FluidStack stack){return false;}
        public int fill(FluidStack stack,FluidAction action){return 0;}public FluidStack drain(FluidStack stack,FluidAction action){return readable()?manualFluid.drain(stack,action):FluidStack.EMPTY;}
        public FluidStack drain(int n,FluidAction action){return readable()?manualFluid.drain(n,action):FluidStack.EMPTY;}
        public Object transactionView(com.reborn.modularmachinery.recipe.ResourceTransaction tx){tx.enlist(ProviderState.this::readable,()->{});return tx.fluids(manualFluid);}
    }
}
