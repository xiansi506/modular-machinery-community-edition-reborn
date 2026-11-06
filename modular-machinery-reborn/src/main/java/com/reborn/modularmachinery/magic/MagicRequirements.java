package com.reborn.modularmachinery.magic;

import com.google.gson.*;
import com.reborn.modularmachinery.recipe.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class MagicRequirements {
    private static final Map<MagicFamily, RequirementType> TYPES = new EnumMap<>(MagicFamily.class);
    static { for (MagicFamily family : MagicFamily.values()) TYPES.put(family,
            RequirementType.of(new ResourceLocation("modular_machinery_reborn", family.path()), new Handler(family), family.modId)); }
    private MagicRequirements() {}
    public static RequirementType type(MagicFamily family) { return TYPES.get(family); }
    private static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || !Double.isFinite(element.getAsDouble()))
            throw new JsonParseException(key + " must be a finite number");
        return element.getAsDouble();
    }
    private record Handler(MagicFamily family) implements RequirementType.Handler {
        public MachineRequirement read(JsonObject json, IOType io, Object where) {
            if (!json.has("amount") && !(family == MagicFamily.EMBER && json.has("ember"))) throw new JsonParseException("Missing magic amount in " + where);
            String variant = "";
            if (family == MagicFamily.AURA) {
                if (!json.has("aura-type") || !json.get("aura-type").isJsonPrimitive() || !json.get("aura-type").getAsJsonPrimitive().isString())
                    throw new JsonParseException("aura-type must be a resource id");
                ResourceLocation id = ResourceLocation.tryParse(json.get("aura-type").getAsString());
                if (id == null) throw new JsonParseException("Invalid aura-type");
                variant = id.toString();
            } else if (family == MagicFamily.WILL) {
                if (json.has("will-type") && (!json.get("will-type").isJsonPrimitive() || !json.get("will-type").getAsJsonPrimitive().isString()))
                    throw new JsonParseException("will-type must be a string");
                variant = json.has("will-type") ? json.get("will-type").getAsString().toLowerCase(Locale.ROOT) : "default";
                if (!List.of("default", "corrosive", "destructive", "vengeful", "steadfast").contains(variant))
                    throw new JsonParseException("Unknown will-type: " + variant);
            }
            if (json.has("perTick") && (!json.get("perTick").isJsonPrimitive() || !json.get("perTick").getAsJsonPrimitive().isBoolean()))
                throw new JsonParseException("perTick must be a boolean");
            double min = number(json, family == MagicFamily.AURA ? "min-aura" : "min", family == MagicFamily.AURA ? Integer.MIN_VALUE : 0);
            double max = number(json, family == MagicFamily.AURA ? "max-aura" : "max", family == MagicFamily.WILL ? 100 : family == MagicFamily.EMBER ? Double.MAX_VALUE : Integer.MAX_VALUE);
            String amountKey = json.has("amount") ? "amount" : "ember";
            double amount = family.integerUnits() ? com.reborn.modularmachinery.util.JsonNumbers.integer(
                    json.get(amountKey), 0, Integer.MAX_VALUE, where + " " + amountKey) : number(json, amountKey, 0);
            if (family.integerUnits()) {
                String minKey = family == MagicFamily.AURA ? "min-aura" : "min";
                String maxKey = family == MagicFamily.AURA ? "max-aura" : "max";
                if (json.has(minKey)) min = com.reborn.modularmachinery.util.JsonNumbers.integer(json.get(minKey), Integer.MIN_VALUE, Integer.MAX_VALUE, where + " " + minKey);
                if (json.has(maxKey)) max = com.reborn.modularmachinery.util.JsonNumbers.integer(json.get(maxKey), Integer.MIN_VALUE, Integer.MAX_VALUE, where + " " + maxKey);
            }
            try { return new MagicRequirement(io, family, amount, variant, min, max, json.has("perTick") && json.get("perTick").getAsBoolean()); }
            catch (IllegalArgumentException error) { throw new JsonParseException(error.getMessage() + " in " + where, error); }
        }
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            MagicRequirement req = (MagicRequirement) requirement;
            buf.writeUtf(req.ioType().name());
            buf.writeDouble(req.amount); buf.writeUtf(req.variant); buf.writeDouble(req.minimum); buf.writeDouble(req.maximum); buf.writeBoolean(req.perTick());
        }
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType io) {
            return new MagicRequirement(io, family, buf.readDouble(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
        }
        public RecipeModifier.Target modifierTarget() { return RecipeModifier.Target.valueOf(family.name()); }
    }
}
