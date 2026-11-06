package com.reborn.modularmachinery.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;

/** One dimension-bound editing session. Complete world reads precede each atomic mutation. */
public final class ConstructSession {
    public final StructureSelection selection=new StructureSelection();
    private ConstructOptions options=ConstructOptions.defaults();
    private BlockPos corner,controller;
    private long revision;
    public ConstructExport.Draft draft;
    public int previewPage;
    public long nextExpensiveTick;
    public ConstructOptions options(){return options;}
    public BlockPos corner(){return corner;}
    public BlockPos controller(){return controller;}
    public long revision(){return revision;}
    public void options(ConstructOptions value){options=value;corner=null;changed();}
    public void controller(BlockPos value){controller=value.immutable();changed();}
    public void changed(){revision++;draft=null;previewPage=0;}
    public void undo(){if(corner==null)selection.undo();corner=null;changed();}
    public void redo(){selection.redo();corner=null;changed();}
    public void clear(){selection.clear();corner=null;changed();}
    private BlockPos suggestedOrigin;
    private String suggestedId,suggestedName;
    public boolean needsIdentitySuggestion(BlockPos origin){
        return options.machineId().equals("custom:machine")
                ||(Objects.equals(options.machineId(),suggestedId)&&!origin.equals(suggestedOrigin));
    }
    public void suggestMachineIdentity(BlockPos origin,String id,String name){
        if(!needsIdentitySuggestion(origin))return;
        String nextName=options.displayName().equals("Custom Machine")||Objects.equals(options.displayName(),suggestedName)?name:options.displayName();
        ConstructOptions next=new ConstructOptions(options.mode(),options.operation(),options.ignoreAir(),options.states(),
                options.matchingNbt(),options.previewNbt(),options.format(),id,nextName,options.replacements());
        options=next;changed();suggestedOrigin=origin.immutable();suggestedId=id;suggestedName=name;
    }
    public void click(Level world,BlockPos position){
        BlockPos pos=position.immutable();
        if(options.mode()==ConstructOptions.Mode.BOX&&corner==null){corner=pos;changed();return;}
        BlockPos first=options.mode()==ConstructOptions.Mode.BOX?corner:pos;
        long dx=Math.abs((long)first.getX()-pos.getX())+1,dy=Math.abs((long)first.getY()-pos.getY())+1,dz=Math.abs((long)first.getZ()-pos.getZ())+1;
        if(dx>32768||dy>32768||dz>32768||dx*dy*dz>32768)throw new IllegalArgumentException("Box volume exceeds 32768 blocks");
        List<BlockPos> edited=new ArrayList<>();
        for(BlockPos cursor:BlockPos.betweenClosed(first,pos)){
            if(!world.isLoaded(cursor))throw new IllegalArgumentException("A selection chunk is not loaded");
            if(!options.ignoreAir()||options.operation()==StructureSelection.Operation.REMOVE
                    ||(options.operation()==StructureSelection.Operation.TOGGLE&&selection.contains(cursor))
                    ||!world.getBlockState(cursor).isAir())edited.add(cursor.immutable());
        }
        selection.apply(edited,options.operation());corner=null;changed();
    }
}
