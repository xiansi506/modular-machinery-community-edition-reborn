package com.reborn.modularmachinery.magic;

import com.google.gson.*;
import com.reborn.modularmachinery.recipe.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Air is an integer resource; pressure is a per-port condition, never multiplied by parallelism. */
public final class CompressedAirRequirement extends MagicRequirement {
    public final double pressure;
    private static final RequirementType ONCE = create(false), TICK = create(true);
    private static RequirementType create(boolean tick) {
        return RequirementType.of(new ResourceLocation("modular_machinery_reborn", tick ? "compressed_air_pertick" : "compressed_air"),
                new Handler(tick), "pneumaticcraft");
    }
    public static void register() { RequirementTypes.register(ONCE); RequirementTypes.register(TICK); }
    public CompressedAirRequirement(IOType io, int air, double pressure, boolean tick) {
        super(io, MagicFamily.AIR, air, "", air == 0 ? Integer.MIN_VALUE : 0, Integer.MAX_VALUE, tick);
        if (!Double.isFinite(pressure)) throw new IllegalArgumentException("pressure must be finite bar");
        this.pressure = pressure;
    }
    @Override public RequirementType type() { return perTick() ? TICK : ONCE; }
    @Override protected boolean skipFreePortCheck() { return false; }
    @Override protected boolean portReady(MagicPort.Access access) {
        if (ioType() != IOType.INPUT || pressure == 0) return true;
        if (!(access.volume() > 0)) return false;
        double bar = access.amount()/access.volume();
        // Negative pressure means vacuum: the measured pressure must be at or below the requested value.
        return pressure < 0 ? bar <= pressure : bar >= pressure;
    }
    @Override public String describe() {
        return super.describe() + (ioType() == IOType.INPUT ? " (" + (pressure < 0 ? "<= " : ">= ") + pressure + " bar)" : "");
    }
    @Override public String startFailure(com.reborn.modularmachinery.machine.HatchCollection ports) {
        return ioType() == IOType.INPUT ? "craftcheck.failure.compressed_air.input" : "craftcheck.failure.compressed_air.output";
    }
    private record Handler(boolean tick) implements RequirementType.Handler {
        public MachineRequirement read(JsonObject json, IOType io, Object where) {
            String amountKey = json.has("air") ? "air" : "amount";
            JsonElement raw = json.get(amountKey);
            int air = 0;
            if (raw != null) {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw new JsonParseException("air must be an integer mL amount in " + where);
                air = com.reborn.modularmachinery.util.JsonNumbers.integer(raw, 0, Integer.MAX_VALUE, where + " air");
            } else if (io == IOType.OUTPUT) throw new JsonParseException("Missing air for compressed air output in " + where);
            double pressure = 0;
            if (io == IOType.INPUT && !json.has("pressure")) throw new JsonParseException("Missing pressure in bar in " + where);
            if (json.has("pressure")) {
                JsonElement p = json.get("pressure");
                if (!p.isJsonPrimitive() || !p.getAsJsonPrimitive().isNumber()) throw new JsonParseException("pressure must be a number in " + where);
                pressure = com.reborn.modularmachinery.util.JsonNumbers.finite(p, where + " pressure");
            }
            if (json.has("perTick") && (!json.get("perTick").isJsonPrimitive() || !json.get("perTick").getAsJsonPrimitive().isBoolean()
                    || json.get("perTick").getAsBoolean() != tick)) throw new JsonParseException("perTick contradicts compressed_air/compressed_air_pertick in " + where);
            // In the old integration time only informed JEI; recipeTime remains the actual runtime duration.
            if (json.has("time")) {
                com.reborn.modularmachinery.util.JsonNumbers.integer(json.get("time"), 1, Integer.MAX_VALUE, where + " legacy time");
            }
            try { return new CompressedAirRequirement(io, air, pressure, tick); }
            catch (IllegalArgumentException error) { throw new JsonParseException(error.getMessage() + " in " + where, error); }
        }
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            CompressedAirRequirement air = (CompressedAirRequirement) requirement;
            buf.writeUtf(air.ioType().name()); buf.writeVarInt((int)air.amount); buf.writeDouble(air.pressure);
        }
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType io) {
            return new CompressedAirRequirement(io, buf.readVarInt(), buf.readDouble(), tick);
        }
        public RecipeModifier.Target modifierTarget() { return RecipeModifier.Target.AIR; }
    }
}
