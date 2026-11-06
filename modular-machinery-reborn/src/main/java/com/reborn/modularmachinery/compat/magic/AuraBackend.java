package com.reborn.modularmachinery.compat.magic;

import com.reborn.modularmachinery.magic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import com.reborn.modularmachinery.recipe.ResourceTransaction;
import java.util.*;
import static com.reborn.modularmachinery.compat.magic.ApiReflection.*;

/** Position-scoped Aura views sharing one native-spot candidate ledger and guarded chunk write receipts. */
public final class AuraBackend implements MagicBackend {
    private final MagicHatchBlockEntity hatch;
    private final Class<?> api = type("de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk");
    private final Class<?> auraType = type("de.ellpeck.naturesaura.api.aura.type.IAuraType");
    private static final Class<?>[] LOCATION = {Level.class, BlockPos.class};
    public AuraBackend(MagicHatchBlockEntity hatch) { this.hatch = hatch; }
    public MagicFamily family() { return MagicFamily.AURA; }
    public boolean input() { return hatch.input(); }
    private Object chunk() { return call(api, null,"getAuraChunk",LOCATION,hatch.getLevel(),hatch.getBlockPos()); }
    private String name(Object chunk) {
        if (chunk == null) return "";
        Object type = call(api,chunk,"getType",new Class<?>[0]);
        return type == null ? "" : call(auraType,type,"getName",new Class<?>[0]).toString();
    }
    public String[] variants() {
        Level level=hatch.getLevel();
        if(level==null||level.isClientSide||footprint(level,hatch.getBlockPos())==null)return new String[0];
        String name=name(chunk());return name.isEmpty()?new String[0]:new String[]{name};
    }
    @SuppressWarnings("unchecked") private static net.minecraftforge.common.util.INBTSerializable<CompoundTag> serializer(Object chunk) {
        return (net.minecraftforge.common.util.INBTSerializable<CompoundTag>) chunk;
    }
    private record ViewKey(Level level, BlockPos pos, int radius) {}
    private record TransactionKey(Level level) {}
    private record Saved(Object chunk, String type, CompoundTag data) {}

    private Map<BlockPos,Object> footprint(Level level, BlockPos pos) {
        Map<BlockPos,Object> result = new LinkedHashMap<>();
        int minX = (pos.getX()-1)>>4, maxX = (pos.getX()+1)>>4;
        int minZ = (pos.getZ()-1)>>4, maxZ = (pos.getZ()+1)>>4;
        // Check every required chunk before the optional API may query any of them.
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(!level.hasChunkAt(new BlockPos(x<<4,pos.getY(),z<<4))) return null;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            BlockPos at = new BlockPos(x<<4,0,z<<4);
            Object value=call(api,null,"getAuraChunk",LOCATION,level,at);
            if(value==null)return null;
            result.put(at,value);
        }
        return result;
    }
    private Map<BlockPos,Saved> save(Map<BlockPos,Object> footprint) {
        Map<BlockPos,Saved> result=new LinkedHashMap<>();
        footprint.forEach((at,chunk)->result.put(at,new Saved(chunk,name(chunk),serializer(chunk).serializeNBT().copy())));
        return result;
    }
    private boolean matches(Level level,Map<BlockPos,Saved> saved) {
        for(var entry:saved.entrySet()) {
            BlockPos at=entry.getKey(); Saved before=entry.getValue();
            if(!level.hasChunkAt(at))return false;
            Object current=call(api,null,"getAuraChunk",LOCATION,level,at);
            if(current!=before.chunk()||!name(current).equals(before.type())
                    ||!serializer(current).serializeNBT().equals(before.data()))return false;
        }
        return true;
    }
    public Access access(String variant) {
        Level level = hatch.getLevel(); BlockPos pos = hatch.getBlockPos();
        if (level == null || level.isClientSide) return null;
        pos=pos.immutable();Map<BlockPos,Object> area=footprint(level,pos);if(area==null)return null;
        Object chunk = chunk(); if (!name(chunk).equals(variant)) return null;
        return new View(level,pos,chunk,variant,area);
    }

    private final class View implements Access {
            final Level level;final BlockPos pos;final Object chunk;final String variant;
            final Map<BlockPos,Object> area;
            View(Level level,BlockPos pos,Object chunk,String variant,Map<BlockPos,Object> area){this.level=level;this.pos=pos;this.chunk=chunk;this.variant=variant;this.area=area;}
            public Object identity() { return new ViewKey(level,pos,1); }
            public void prepare(ResourceTransaction transaction) {
                transaction.shared(new TransactionKey(level),()->new Coordinator(level,transaction));
            }
            public Access plan(ResourceTransaction transaction) {
                Coordinator coordinator=transaction.shared(new TransactionKey(level),()->new Coordinator(level,transaction));
                return coordinator.view(this);
            }
            public boolean valid() {
                if(hatch.isRemoved()||hatch.getLevel()!=level||!hatch.getBlockPos().equals(pos))return false;
                Map<BlockPos,Object> current=footprint(level,pos);
                if(current==null||current.size()!=area.size())return false;
                for(var e:area.entrySet())if(current.get(e.getKey())!=e.getValue())return false;
                return AuraBackend.this.chunk()==chunk&&name(chunk).equals(variant);
            }
            public double amount() { return !valid()?Double.NaN:((Number)call(api,null,"getAuraInArea",new Class<?>[]{Level.class,BlockPos.class,int.class},level,pos,1)).intValue(); }
            public double minimum() { return Integer.MIN_VALUE; }
            public double maximum() { return Integer.MAX_VALUE; }
            public double move(double delta, boolean simulate) {
                if (!valid() || !Double.isFinite(delta)) return 0;
                double n = delta >= 0 ? Math.min(Math.floor(delta),maximum()-amount()) : -Math.min(Math.floor(-delta),amount()-minimum());
                int count = (int)Math.min(Integer.MAX_VALUE,Math.abs(n));
                if (count <= 0) return 0;
                long change=n>=0?count:-count;
                BlockPos target=pos;
                for(int hops=0;hops<4096;hops++){
                    long spot=((Number)call(chunk,"getDrainSpot",new Class<?>[]{BlockPos.class},target)).intValue();
                    if(spot+change>=Integer.MIN_VALUE&&spot+change<=Integer.MAX_VALUE)return moveAt(target,change,simulate);
                    if(target.getY()==Integer.MAX_VALUE)return 0;
                    target=target.above();
                }
                return 0; // Bounded native migration search, no partially executed recursion.
            }
            private double moveAt(BlockPos target,long delta,boolean simulate){
                if(!valid()||delta==0||Math.abs(delta)>Integer.MAX_VALUE)return 0;
                long spot=((Number)call(chunk,"getDrainSpot",new Class<?>[]{BlockPos.class},target)).intValue();
                if(spot+delta<Integer.MIN_VALUE||spot+delta>Integer.MAX_VALUE)return 0;
                // Explicit false avoids the convenience storeAura overload stopping at zero.
                int moved = ((Number)call(api,chunk,delta>=0?"storeAura":"drainAura",new Class<?>[]{BlockPos.class,int.class,boolean.class,boolean.class},target,(int)Math.abs(delta),false,simulate)).intValue();
                return delta>=0?moved:-moved;
            }
            public Object snapshot() { if(!valid())throw new IllegalStateException("Aura footprint unavailable");return save(area); }
            @SuppressWarnings("unchecked") public boolean unchanged(Object snapshot) { return valid()&&matches(level,(Map<BlockPos,Saved>)snapshot); }
            public void restore(Object snapshot) { throw new IllegalStateException("Aura rollback requires its guarded transaction footprint coordinator"); }
    }

    /** One owner for shared chunk before/post images; no independent full-chunk overwrites per sampled view. */
    private final class Coordinator {
        final Level level;final ResourceTransaction transaction;
        final Map<BlockPos,Saved> before=new LinkedHashMap<>(),expected=new LinkedHashMap<>();
        final List<View> views=new ArrayList<>();final Set<BlockPos> written=new LinkedHashSet<>();
        final Map<BlockPos,Spot> spots=new LinkedHashMap<>();
        int budget=65536;
        boolean conflict,uncertain;
        private final class Spot {
            final int initial;
            int working;
            Spot(View source,BlockPos at){initial=((Number)call(source.chunk,"getDrainSpot",new Class<?>[]{BlockPos.class},at)).intValue();working=initial;}
        }
        private boolean use(){if(--budget>=0)return true;conflict=true;return false;}
        private double projected(View source,double base){
            long result=(long)base;
            for(var e:spots.entrySet()){
                if(!use())return Double.NaN;
                if(e.getKey().distSqr(source.pos)<=1)result+=(long)e.getValue().working-e.getValue().initial;
            }
            return result<Integer.MIN_VALUE||result>Integer.MAX_VALUE?Double.NaN:result;
        }
        Coordinator(Level level,ResourceTransaction transaction){this.level=level;this.transaction=transaction;transaction.enlist(()->!conflict&&matches(level,before)&&views.stream().allMatch(View::valid),this::rollback);}
        Access view(View source) {
            views.add(source);
            for(var e:save(source.area).entrySet()){before.putIfAbsent(e.getKey(),e.getValue());expected.putIfAbsent(e.getKey(),e.getValue());}
            return new Access(){
                final double base=source.amount();
                public Object identity(){return source.identity();}
                public double amount(){return projected(source,base);}
                public double minimum(){return source.minimum();}
                public double maximum(){return source.maximum();}
                public boolean valid(){return !conflict&&source.valid();}
                public double move(double delta,boolean simulate){
                    if(!Double.isFinite(delta)||!valid())return 0;
                    double working=amount();
                    if(!Double.isFinite(working))return 0;
                    double moved=delta>=0?Math.max(0,Math.min(delta,maximum()-working)):-Math.max(0,Math.min(-delta,working-minimum()));
                    moved=Math.copySign(Math.floor(Math.abs(moved)),moved);
                    if(moved==0||!MagicPort.movementMatches((working+moved)-working,moved))return 0;
                    moved=Math.copySign(Math.min(Math.abs(moved),Integer.MAX_VALUE),moved);
                    long exact=(long)moved;
                    BlockPos target=source.pos;
                    Spot selected=null;
                    for(int hops=0;hops<4096;hops++){
                        if(!use())return 0;
                        Spot spot=spots.computeIfAbsent(target,at->new Spot(source,at));
                        long next=(long)spot.working+exact;
                        if(next>=Integer.MIN_VALUE&&next<=Integer.MAX_VALUE){selected=spot;break;}
                        if(target.getY()==Integer.MAX_VALUE)break;
                        target=target.above();
                    }
                    if(selected==null){if(!simulate)conflict=true;return 0;}
                    if(!simulate){
                        if(!use())return 0;
                        int expectedSpot=selected.working;
                        selected.working=(int)((long)selected.working+exact);
                        double after=projected(source,base);
                        if(!Double.isFinite(after)){conflict=true;return 0;}
                        BlockPos at=target;
                        transaction.record(()->apply(source,working,after,at,expectedSpot,exact));
                    }
                    return moved;
                }
            };
        }
        boolean apply(View source,double start,double after,BlockPos target,int expectedSpot,long delta){
            if(conflict||!source.valid()||!matches(level,expected)||source.amount()!=start)return false;
            if(((Number)call(source.chunk,"getDrainSpot",new Class<?>[]{BlockPos.class},target)).intValue()!=expectedSpot)return false;
            // This operation may fail after native mutation. Only a normally returned postimage is a
            // receipt we can prove belongs to us; exceptions preserve diagnostics rather than blind restore.
            uncertain=true;
            double moved=source.moveAt(target,delta,false);
            for(var e:save(source.area).entrySet()){
                if(!e.getValue().data().equals(expected.get(e.getKey()).data()))written.add(e.getKey());
                expected.put(e.getKey(),e.getValue());
            }
            uncertain=false;
            return moved==delta&&source.amount()==after
                    &&((Number)call(source.chunk,"getDrainSpot",new Class<?>[]{BlockPos.class},target)).longValue()==(long)expectedSpot+delta;
        }
        void rollback(){
            if(uncertain)throw new IllegalStateException("Aura rollback unresolved: native move failed without a proven postimage; current state retained");
            if(written.isEmpty())return;
            for(BlockPos at:written)if(!matches(level,Map.of(at,expected.get(at))))
                throw new IllegalStateException("Aura rollback unresolved: footprint changed/unloaded; external state retained at "+at);
            for(BlockPos at:written){Saved original=before.get(at);serializer(original.chunk()).deserializeNBT(original.data().copy());call(api,original.chunk(),"markDirty",new Class<?>[0]);}
            written.clear();
        }
    }
}
