package com.reborn.modularmachinery.selection;

import com.google.gson.*;
import com.reborn.modularmachinery.block.*;
import com.reborn.modularmachinery.machine.*;
import com.reborn.modularmachinery.recipe.LiteralNbt;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/** One validated source definition feeds both JSON and KubeJS. The world is checked again at save time. */
public final class ConstructExport {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int PAGE_SIZE=8000;
    public record Draft(long revision,String filename,String content){
        public int pages(){return Math.max(1,(content.length()+PAGE_SIZE-1)/PAGE_SIZE);}
        public String page(int page){
            int start=Math.min(content.length(),Math.max(0,page)*PAGE_SIZE),end=Math.min(content.length(),start+PAGE_SIZE);
            if(start>0&&start<content.length()&&Character.isLowSurrogate(content.charAt(start)))start--;
            if(end<content.length()&&Character.isLowSurrogate(content.charAt(end)))end--;
            return content.substring(start,end);
        }
    }
    private ConstructExport(){}
    public static CompoundTag filter(CompoundTag source,String selection){
        CompoundTag clean=SelectionExport.withoutCoordinates(source);List<String> paths=ConstructOptions.paths(selection);
        if(paths.contains("*"))return clean;CompoundTag out=new CompoundTag();
        for(String path:paths){
            String[] segments=path.startsWith("/")?path.substring(1).split("/",-1):new String[]{path};
            if(segments.length>32)throw new IllegalArgumentException("NBT path is too deep");
            for(int i=0;i<segments.length;i++)segments[i]=segments[i].replace("~1","/").replace("~0","~");
            Tag value=clean;
            for(String key:segments){value=value instanceof CompoundTag compound?compound.get(key):null;if(value==null)break;}
            if(value==null)continue;
            CompoundTag to=out;
            for(int i=0;i<segments.length-1;i++){
                if(!to.contains(segments[i],Tag.TAG_COMPOUND))to.put(segments[i],new CompoundTag());to=to.getCompound(segments[i]);
            }
            to.put(segments[segments.length-1],value.copy());
        }return out;
    }
    public static String descriptor(String descriptor,String fields){
        int bracket=descriptor.indexOf('[');if(bracket<0)return descriptor;
        List<String> keep=ConstructOptions.paths(fields);if(keep.contains("*"))return descriptor;
        List<String> properties=Arrays.stream(descriptor.substring(bracket+1,descriptor.length()-1).split(","))
                .filter(p->keep.contains(p.substring(0,p.indexOf('=')))).toList();
        return descriptor.substring(0,bracket)+(properties.isEmpty()?"":"["+String.join(",",properties)+"]");
    }
    public static JsonObject definition(Level world,ConstructSession session){
        BlockPos origin=session.controller();
        if(origin==null||!world.isLoaded(origin)||!(world.getBlockState(origin).getBlock() instanceof MachineControllerBlock))throw new IllegalArgumentException("Click a loaded machine controller first");
        int steps=SelectionExport.rotationSteps(world.getBlockState(origin).getValue(MachineControllerBlock.FACING));
        ConstructOptions options=session.options();JsonArray parts=new JsonArray();
        for(BlockPos at:session.selection.positions()){
            if(!world.isLoaded(at))throw new IllegalArgumentException("A selected chunk is not loaded");
            if(at.equals(origin))continue;BlockState state=world.getBlockState(at);if(options.ignoreAir()&&state.isAir())continue;
            String original=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            for(int i=0;i<steps;i++)state=state.rotate(Rotation.COUNTERCLOCKWISE_90);
            String description=descriptor(options.replacements().getOrDefault(original,SelectionExport.describe(state)),options.states());
            BlockPos offset=SelectionExport.rotate(at.subtract(origin),steps);JsonObject part=new JsonObject();
            part.addProperty("x",offset.getX());part.addProperty("y",offset.getY());part.addProperty("z",offset.getZ());part.addProperty("elements",description);
            // Fetch and serialize the entity only when at least one NBT filter will consume the snapshot.
            boolean wantsNbt=!options.matchingNbt().isBlank()||!options.previewNbt().isBlank();
            BlockEntity entity=wantsNbt?world.getBlockEntity(at):null;
            if(entity!=null&&!(entity instanceof MachineCasingBlockEntity)&&!options.replacements().containsKey(original)){
                CompoundTag tag=entity.saveWithoutMetadata();
                if(!options.matchingNbt().isBlank())part.add("nbt",LiteralNbt.encode(filter(tag,options.matchingNbt())));
                if(!options.previewNbt().isBlank())part.add("preview-nbt",LiteralNbt.encode(filter(tag,options.previewNbt())));
            }parts.add(part);
        }
        if(parts.isEmpty())throw new IllegalArgumentException("No blocks remain after filtering");
        JsonObject definition=new JsonObject();definition.addProperty("registryname",options.machineId());definition.addProperty("localizedname",options.displayName());
        definition.addProperty("requires-blueprint",false);definition.add("parts",parts);MachineSchema.read(definition,"construct tool export",Map.of(),true);return definition;
    }
    public static String content(JsonObject definition,ConstructOptions.Format format){
        if(format==ConstructOptions.Format.FRAGMENT){JsonObject fragment=new JsonObject();fragment.add("parts",definition.get("parts"));return JSON.toJson(fragment)+"\n";}
        if(format==ConstructOptions.Format.JSON)return JSON.toJson(definition)+"\n";
        return "MachineRegistryEvents.registry(event => {\n  event.machine("+JSON.toJson(definition.get("registryname"))+")\n    .localizedName("+JSON.toJson(definition.get("localizedname"))+")\n    .requiresBlueprint(false)\n    .field(\"parts\", "+JSON.toJson(definition.get("parts"))+")\n    .register()\n})\n";
    }
    public static Draft prepare(Level world,ConstructSession session,String player){
        String content=content(definition(world,session),session.options().format());if(content.length()>4*1024*1024)throw new IllegalArgumentException("Export exceeds 4 MiB characters");
        String name=MachineFragmentWriter.fileNameFor(player,LocalDateTime.now());if(session.options().format()==ConstructOptions.Format.KUBEJS)name=name.substring(0,name.length()-5)+".js";
        return new Draft(session.revision(),name,content);
    }
    public static Path save(Level world,ConstructSession session,Path directory)throws IOException{
        Draft draft=session.draft;if(draft==null||draft.revision()!=session.revision())throw new IllegalArgumentException("Preview the current selection before exporting");
        if(!content(definition(world,session),session.options().format()).equals(draft.content()))throw new IllegalArgumentException("World data changed; refresh the preview");
        Path saved=MachineFragmentWriter.write(directory,draft.filename(),draft.content());session.clear();return saved;
    }
}
