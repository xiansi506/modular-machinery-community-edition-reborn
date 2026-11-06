package com.reborn.modularmachinery.authoring;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Small, exact mutations used by the simple canvas. Unrepresented advanced fields remain intact. */
public final class RecipeQuickEdit {
    private RecipeQuickEdit() {}
    public static List<Integer> indices(JsonObject root, boolean input) {
        List<Integer> result = new ArrayList<>();
        if (!root.has("requirements") || !root.get("requirements").isJsonArray()) return result;
        JsonArray array = root.getAsJsonArray("requirements");
        for (int i=0;i<array.size();i++) if (array.get(i).isJsonObject()) {
            JsonObject req = array.get(i).getAsJsonObject();
            if (input == !"output".equals(text(req,"io-type"))) result.add(i);
        }
        return List.copyOf(result);
    }
    public static String text(JsonObject object, String key) {
        JsonElement value=object.get(key);
        return value==null ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
    public static String seconds(JsonObject root) {
        String ticks=text(root,"recipeTime");
        BigDecimal value=new BigDecimal(ticks.isEmpty()?"1":ticks);
        if(value.compareTo(BigDecimal.ONE)<0||value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0)
            throw new IllegalArgumentException("Invalid recipe duration");
        value.toBigIntegerExact();
        return value.divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }
    /** Null means an existing advanced energy requirement must be edited through its own form. */
    public static String energy(JsonObject root) {
        int index=energyIndex(root);
        return index == -2 ? null : index < 0 ? "0" : text(root.getAsJsonArray("requirements").get(index).getAsJsonObject(),"energyPerTick");
    }
    private static int energyIndex(JsonObject root) {
        int found=-1;
        for (int i:indices(root,true)) {
            JsonObject req=root.getAsJsonArray("requirements").get(i).getAsJsonObject();
            if (!"energy".equals(RecipeDocument.kind(req))) continue;
            if (found>=0 || !Set.of("type","io-type","energyPerTick").containsAll(req.keySet())) return -2;
            found=i;
        }
        return found;
    }
    public static List<Integer> visibleIndices(JsonObject root, boolean input) {
        int energy=input?energyIndex(root):-1;
        return indices(root,input).stream().filter(i->i!=energy).toList();
    }
    public static JsonObject overview(JsonObject source, String seconds, String energy) {
        BigDecimal duration=new BigDecimal(seconds.trim());
        if(duration.signum()<=0 || duration.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE).divide(BigDecimal.valueOf(20)))>0)
            throw new IllegalArgumentException("Duration is outside the supported range");
        BigInteger ticks=duration.multiply(BigDecimal.valueOf(20)).toBigIntegerExact();
        if (ticks.signum()<=0 || ticks.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0)
            throw new IllegalArgumentException("Duration must be positive, in whole ticks (0.05 seconds), up to 2147483647 ticks");
        long rate=energy==null || energy.isBlank() ? 0 : new BigDecimal(energy.trim()).longValueExact();
        if (rate<0) throw new IllegalArgumentException("Energy must be a non-negative whole FE/t value");
        JsonObject result=source.deepCopy();result.addProperty("recipeTime",ticks.intValueExact());
        if (energy!=null) {
            int index=energyIndex(result);
            if (index == -2) throw new IllegalArgumentException("Edit advanced energy requirements individually");
            JsonArray requirements=array(result);
            // Keep an existing zero-rate entry in place. Canvas targets refer to stable requirement
            // indices while overview fields are collected before a click/drop/scroll operation.
            if (rate>0 || index>=0) {
                JsonObject req=index<0?RecipeDocument.requirement("energy"):requirements.get(index).getAsJsonObject();req.addProperty("energyPerTick",rate);
                if (index<0) { checkSpace(requirements); requirements.add(req); }
            }
        }
        return result;
    }
    public static JsonArray array(JsonObject root) {
        if (!root.has("requirements")) root.add("requirements",new JsonArray());
        return root.getAsJsonArray("requirements");
    }
    public static void checkSpace(JsonArray array) {
        if (array.size()>=RecipeDocument.MAX_REQUIREMENTS) throw new IllegalArgumentException("At most 64 requirements");
    }
    public static int add(JsonObject root, String kind, boolean input) {
        JsonArray array=array(root);checkSpace(array);JsonObject req=RecipeDocument.requirement(kind);
        req.addProperty("io-type",input?"input":"output");array.add(req);return array.size()-1;
    }
    public static JsonObject resource(JsonObject original,String kind,String id) {
        if (!RecipeDocument.TYPES.contains(kind)) throw new IllegalArgumentException("Unsupported resource type");
        JsonObject next=original!=null&&RecipeDocument.kind(original).equals(kind)?original.deepCopy():RecipeDocument.requirement(kind);
        String key=RecipeDocument.resourceKey(kind);if(!key.isEmpty())next.addProperty(key,id);return next;
    }
    /** Returns false for range/complex amounts: scrolling must never flatten them. */
    public static boolean adjust(JsonObject req, int delta) {
        if (delta==0 || req.has("minAmount") || req.has("maxAmount")) return false;
        String kind=RecipeDocument.kind(req),key=RecipeDocument.amountKey(kind);
        String value=text(req,key);if(value.isBlank())return false;
        BigDecimal current=new BigDecimal(value);
        if(current.scale()>324||current.scale()<-308||current.precision()>1024)return false;
        boolean fractional=kind.equals("will")||kind.equals("ember");
        BigDecimal min=BigDecimal.valueOf(List.of("item","fluid","gas").contains(kind)?1:0);
        BigDecimal max=BigDecimal.valueOf(kind.equals("energy")?Long.MAX_VALUE:Integer.MAX_VALUE);
        if (fractional) max=BigDecimal.valueOf(Double.MAX_VALUE);
        BigDecimal amount=current.max(min).min(max).add(BigDecimal.valueOf(delta)).max(min).min(max);
        if(!fractional)amount.toBigIntegerExact();
        req.addProperty(key,amount);return true;
    }
    public static String suggestedId() {
        return "modular_machinery_reborn:custom/recipe_"+UUID.randomUUID().toString().replace("-","");
    }
}
