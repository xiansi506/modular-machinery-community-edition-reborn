package com.reborn.modularmachinery.selection;

import com.google.gson.*;
import com.reborn.modularmachinery.machine.BlockMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Validated author preferences. Only the server applies them to a session. */
public record ConstructOptions(Mode mode, StructureSelection.Operation operation, boolean ignoreAir,
        String states, String matchingNbt, String previewNbt, Format format, String machineId,
        String displayName, Map<String,String> replacements) {
    public enum Mode { SINGLE, BOX }
    public enum Format { FRAGMENT, JSON, KUBEJS }
    public ConstructOptions {
        Objects.requireNonNull(mode);Objects.requireNonNull(operation);Objects.requireNonNull(format);
        states=limited(states,512);matchingNbt=limited(matchingNbt,1024);previewNbt=limited(previewNbt,1024);
        machineId=limited(machineId,128);displayName=limited(displayName,128);
        if(ResourceLocation.tryParse(machineId)==null||displayName.isBlank())throw new IllegalArgumentException("Invalid machine ID or empty display name");
        paths(states);paths(matchingNbt);paths(previewNbt);
        if(replacements.size()>16)throw new IllegalArgumentException("At most 16 material substitutions");
        Map<String,String> checked=new LinkedHashMap<>();
        replacements.forEach((from,to)->{
            ResourceLocation id=ResourceLocation.tryParse(limited(from,128));
            if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("Unknown source block: "+from);
            String descriptor=limited(to,256);BlockMatcher.parse(descriptor);checked.put(id.toString(),descriptor);
        });replacements=Collections.unmodifiableMap(checked);
    }
    public static ConstructOptions defaults(){return new ConstructOptions(Mode.SINGLE,StructureSelection.Operation.TOGGLE,true,"*","","",Format.JSON,"custom:machine","Custom Machine",Map.of());}
    private static String limited(String value,int limit){if(value==null||value.length()>limit)throw new IllegalArgumentException("Tool option exceeds "+limit+" characters");return value.trim();}
    public static List<String> paths(String text){
        if(text.isBlank())return List.of();
        List<String> result=Arrays.stream(text.split(",",-1)).map(String::trim).toList();
        if(result.size()>64||result.stream().anyMatch(s->s.isEmpty()||s.length()>256))throw new IllegalArgumentException("Invalid field selection");return result;
    }
    public JsonObject json(){
        JsonObject o=new JsonObject();o.addProperty("mode",mode.name());o.addProperty("operation",operation.name());o.addProperty("ignoreAir",ignoreAir);
        o.addProperty("states",states);o.addProperty("matchingNbt",matchingNbt);o.addProperty("previewNbt",previewNbt);o.addProperty("format",format.name());
        o.addProperty("machineId",machineId);o.addProperty("displayName",displayName);JsonObject r=new JsonObject();replacements.forEach(r::addProperty);o.add("replacements",r);return o;
    }
    public static ConstructOptions read(JsonObject o){
        Map<String,String> r=new LinkedHashMap<>();o.getAsJsonObject("replacements").entrySet().forEach(e->r.put(e.getKey(),e.getValue().getAsString()));
        return new ConstructOptions(Mode.valueOf(o.get("mode").getAsString()),StructureSelection.Operation.valueOf(o.get("operation").getAsString()),o.get("ignoreAir").getAsBoolean(),
            o.get("states").getAsString(),o.get("matchingNbt").getAsString(),o.get("previewNbt").getAsString(),Format.valueOf(o.get("format").getAsString()),o.get("machineId").getAsString(),o.get("displayName").getAsString(),r);
    }
}
