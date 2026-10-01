package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and syncs machine recipes.
 *
 * <p>Requirement {@code type} ids are accepted with either namespace so the original files load unchanged:
 * {@code modularmachinery:item} and {@code modular_machinery_reborn:item} are the same thing, and a bare
 * {@code item} works too. Five kinds exist from 0.26.0 on — {@code item}, {@code fluid}, {@code energy},
 * {@code interface_number_input} and {@code ingredient_array_input}. The original's remaining kinds
 * ({@code gas}, {@code gas_pertick}, {@code fluid_pertick}) are not implemented, because they need a gas API
 * this port does not have; an unknown kind is a load error rather than a silently ignored requirement.
 *
 * <p><b>{@code item_durability} and {@code catalyst} are deliberately absent, and are not gaps.</b> Neither
 * exists as a usable requirement type in the original: {@code RequirementTypeItemDurability#createRequirement}
 * returns {@code null} and no code ever constructs it, and the catalyst type has no registration entry at all
 * (its only constructors were the CraftTweaker bridge). See D18 in {@code 移植方案-v2.md}.
 */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ModularMachineryReborn.MOD_ID);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> MACHINE =
            SERIALIZERS.register("machine", MachineSerializer::new);

    private static final String KIND_ITEM = "item";
    private static final String KIND_FLUID = "fluid";
    private static final String KIND_ENERGY = "energy";
    private static final String KIND_INTERFACE_NUMBER_INPUT = "interface_number_input";
    private static final String KIND_INGREDIENT_ARRAY_INPUT = "ingredient_array_input";

    private ModRecipeSerializers() {
    }

    private static final class MachineSerializer implements RecipeSerializer<MachineRecipe> {

        @Override
        public MachineRecipe fromJson(ResourceLocation id, JsonObject json) {
            String machine = requireString(json, "machine", id);
            String registryName = json.has("registryName") ? json.get("registryName").getAsString() : id.getPath();
            int recipeTime = json.has("recipeTime") ? json.get("recipeTime").getAsInt() : 100;
            if (recipeTime < 1) {
                throw new JsonParseException("'recipeTime' of " + id + " must be at least 1");
            }

            JsonElement rawRequirements = json.get("requirements");
            if (rawRequirements == null || !rawRequirements.isJsonArray()) {
                throw new JsonParseException("Missing 'requirements' array in " + id);
            }
            JsonArray array = rawRequirements.getAsJsonArray();
            if (array.isEmpty()) {
                throw new JsonParseException("Empty 'requirements' in " + id);
            }

            List<MachineRequirement> requirements = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("A requirement of " + id + " is not a JSON object");
                }
                requirements.add(parseRequirement(element.getAsJsonObject(), id));
            }
            return new MachineRecipe(id, machine, registryName, recipeTime, requirements,
                    parseMaxParallelism(json, id));
        }

        /**
         * The optional per-recipe parallelism ceiling, {@code max-parallelism}.
         *
         * <p>The original had no such recipe field — a recipe's ceiling was always the machine's — which is why
         * omitting it (the default, {@link MachineRecipe#MACHINE_LIMIT}) keeps every existing recipe behaving
         * exactly as it did. Writing it is how a single recipe says "even on a machine that can run 512 copies,
         * this one may run at most 8".
         */
        private static int parseMaxParallelism(JsonObject json, ResourceLocation id) {
            if (!json.has("max-parallelism")) {
                return MachineRecipe.MACHINE_LIMIT;
            }
            JsonElement element = json.get("max-parallelism");
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("'max-parallelism' of " + id + " must be a number. Write a whole "
                        + "number of copies, e.g. \"max-parallelism\": 8, or remove the field to let the "
                        + "machine's own max-parallelism decide.");
            }
            int value = element.getAsInt();
            if (value < 1) {
                throw new JsonParseException("'max-parallelism' of " + id + " is " + value + ", but a recipe may "
                        + "not run fewer than one copy. Write at least 1, or remove the field to let the "
                        + "machine's own max-parallelism decide.");
            }
            return value;
        }

        @Override
        public MachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String machine = buf.readUtf();
            String registryName = buf.readUtf();
            int recipeTime = buf.readVarInt();
            int maxParallelism = buf.readVarInt();
            int count = buf.readVarInt();
            List<MachineRequirement> requirements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                requirements.add(readRequirement(buf));
            }
            return new MachineRecipe(id, machine, registryName, recipeTime, requirements, maxParallelism);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MachineRecipe recipe) {
            buf.writeUtf(recipe.machine());
            buf.writeUtf(recipe.registryName());
            buf.writeVarInt(recipe.recipeTime());
            buf.writeVarInt(recipe.maxParallelism());
            buf.writeVarInt(recipe.requirements().size());
            for (MachineRequirement requirement : recipe.requirements()) {
                writeRequirement(buf, requirement);
            }
        }
    }

    // ------------------------------------------------------------------ json
    private static MachineRequirement parseRequirement(JsonObject json, ResourceLocation recipeId) {
        String where = "a requirement of " + recipeId;
        String rawType = requireString(json, "type", where);
        int colon = rawType.indexOf(':');
        String kind = (colon >= 0 ? rawType.substring(colon + 1) : rawType).toLowerCase(Locale.ROOT);
        IOType ioType = IOType.byName(requireString(json, "io-type", where));
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0F;

        switch (kind) {
            case KIND_ITEM -> {
                Ingredient ingredient = LegacyIngredients.parse(requireString(json, "item", where), where);
                int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
                int min = json.has("minAmount") ? json.get("minAmount").getAsInt() : amount;
                int max = json.has("maxAmount") ? json.get("maxAmount").getAsInt() : amount;
                if (ioType == IOType.INPUT) {
                    if (min != max) {
                        throw new JsonParseException("An item input in " + where
                                + " uses minAmount/maxAmount; only outputs may vary their amount. "
                                + "Give inputs a fixed 'amount'.");
                    }
                    return ItemRequirement.input(ingredient, min);
                }
                ItemStack stack = LegacyIngredients.firstStack(ingredient);
                if (stack.isEmpty()) {
                    throw new JsonParseException("An item output in " + where + " resolved to no concrete item");
                }
                return ItemRequirement.output(stack, min, max, chance);
            }
            case KIND_FLUID -> {
                String fluidId = requireString(json, "fluid", where);
                ResourceLocation key = ResourceLocation.tryParse(fluidId);
                Fluid fluid = key == null ? null : ForgeRegistries.FLUIDS.getValue(key);
                if (fluid == null) {
                    throw new JsonParseException("Unknown fluid '" + fluidId + "' in " + where);
                }
                int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
                boolean perTick = json.has("perTick") && json.get("perTick").getAsBoolean();
                return new FluidRequirement(ioType, new FluidStack(fluid, 1), amount, chance, perTick);
            }
            case KIND_ENERGY -> {
                long perTick = json.has("energyPerTick") ? json.get("energyPerTick").getAsLong() : 0L;
                return new EnergyRequirement(ioType, perTick);
            }
            case KIND_INTERFACE_NUMBER_INPUT -> {
                return InterfaceNumberInputParser.parse(json, ioType, where);
            }
            case KIND_INGREDIENT_ARRAY_INPUT -> {
                return IngredientArrayParser.parse(json, ioType, where);
            }
            default -> throw new JsonParseException("Unknown requirement type '" + rawType + "' in " + where
                    + "'. M2 implements item, fluid and energy; M6d-b adds interface_number_input; 0.26.0 adds "
                    + "ingredient_array_input. The original's gas, gas_pertick and fluid_pertick kinds are not "
                    + "ported because they need a gas API; its item_durability and catalyst kinds are not "
                    + "requirement types at all and are not gaps (D18).");
        }
    }

    private static String requireString(JsonObject json, String key, Object where) {
        if (!json.has(key)) {
            throw new JsonParseException("Missing '" + key + "' in " + where);
        }
        return json.get(key).getAsString();
    }

    // --------------------------------------------------------------- network

    private static void writeRequirement(FriendlyByteBuf buf, MachineRequirement requirement) {
        if (requirement instanceof ItemRequirement item) {
            buf.writeUtf(KIND_ITEM);
            buf.writeUtf(item.ioType().name());
            buf.writeVarInt(item.minAmount());
            buf.writeVarInt(item.maxAmount());
            if (item.ioType() == IOType.INPUT) {
                Ingredient ingredient = item.ingredient();
                (ingredient == null ? Ingredient.EMPTY : ingredient).toNetwork(buf);
            } else {
                buf.writeItem(item.outputStack().copyWithCount(1));
                buf.writeFloat(item.chance());
            }
        } else if (requirement instanceof FluidRequirement fluid) {
            buf.writeUtf(KIND_FLUID);
            buf.writeUtf(fluid.ioType().name());
            fluid.fluidType().writeToPacket(buf);
            buf.writeVarInt(fluid.amount());
            buf.writeFloat(fluid.chance());
            buf.writeBoolean(fluid.perTick());
        } else if (requirement instanceof EnergyRequirement energy) {
            buf.writeUtf(KIND_ENERGY);
            buf.writeUtf(energy.ioType().name());
            buf.writeVarLong(energy.energyPerTick());
        } else if (requirement instanceof InterfaceNumberInputRequirement interfaceRequirement) {
            buf.writeUtf(KIND_INTERFACE_NUMBER_INPUT);
            buf.writeUtf(interfaceRequirement.ioType().name());
            buf.writeUtf(interfaceRequirement.interfaceType());
            buf.writeFloat(interfaceRequirement.minValue());
            buf.writeFloat(interfaceRequirement.maxValue());
        } else if (requirement instanceof IngredientArrayRequirement array) {
            buf.writeUtf(KIND_INGREDIENT_ARRAY_INPUT);
            buf.writeUtf(array.ioType().name());
            buf.writeFloat(array.chance());
            buf.writeVarInt(array.entryCount());
            for (int index = 0; index < array.entryCount(); index++) {
                buf.writeVarInt(array.entryAmount(index));
                // Each entry travels as the concrete stacks it may consume. An entry parsed from a tag arrived
                // here through the shared reader, so the client re-matches exactly the same items; only the
                // "#tag" spelling of the tooltip is not carried, which is display-only.
                var matches = array.entryStacks(index);
                buf.writeVarInt(matches.size());
                for (ItemStack match : matches) {
                    buf.writeItem(match);
                }
            }
        } else {
            throw new IllegalStateException("Unsupported requirement " + requirement.getClass());
        }
    }

    private static MachineRequirement readRequirement(FriendlyByteBuf buf) {
        String kind = buf.readUtf();
        IOType ioType = IOType.valueOf(buf.readUtf());
        switch (kind) {
            case KIND_ITEM -> {
                int min = buf.readVarInt();
                int max = buf.readVarInt();
                if (ioType == IOType.INPUT) {
                    return ItemRequirement.input(Ingredient.fromNetwork(buf), min);
                }
                ItemStack stack = buf.readItem();
                float chance = buf.readFloat();
                return ItemRequirement.output(stack, min, max, chance);
            }
            case KIND_FLUID -> {
                FluidStack fluid = FluidStack.readFromPacket(buf);
                int amount = buf.readVarInt();
                float chance = buf.readFloat();
                boolean perTick = buf.readBoolean();
                return new FluidRequirement(ioType, fluid, amount, chance, perTick);
            }
            case KIND_ENERGY -> {
                return new EnergyRequirement(ioType, buf.readVarLong());
            }
            case KIND_INTERFACE_NUMBER_INPUT -> {
                String type = buf.readUtf();
                float min = buf.readFloat();
                float max = buf.readFloat();
                return new InterfaceNumberInputRequirement(type, min, max, null);
            }
            case KIND_INGREDIENT_ARRAY_INPUT -> {
                float chance = buf.readFloat();
                int entries = buf.readVarInt();
                List<IngredientArrayEntry> parsed = new ArrayList<>(entries);
                for (int index = 0; index < entries; index++) {
                    int amount = buf.readVarInt();
                    int matches = buf.readVarInt();
                    ItemStack first = ItemStack.EMPTY;
                    for (int match = 0; match < matches; match++) {
                        ItemStack stack = buf.readItem();
                        if (match == 0) {
                            first = stack;
                        }
                    }
                    parsed.add(IngredientArrayEntry.ofItem(first, amount, chance));
                }
                return new IngredientArrayRequirement(parsed, chance);
            }
            default -> throw new IllegalStateException("Unknown requirement kind '" + kind + "' on the wire");
        }
    }
}
