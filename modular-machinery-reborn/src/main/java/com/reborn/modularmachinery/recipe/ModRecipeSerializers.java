package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

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
 * <p><b>波 5.2：这个类不再知道「有哪些需求种类」。</b>它原来持有五个
 * {@code private static final String} 常量，并在**三处**各自把那些名字硬编码一遍——JSON 解析的
 * {@code switch}、写包的 {@code instanceof} 链、读包的 {@code switch}。那三处现在都不在了：
 * 种类住在 {@link RequirementTypes} 这张注册表里，每条需求自己知道自己是哪一种
 * （{@link MachineRequirement#type()}），解析与过网由 {@link MachineRequirementParser} 分派。
 * 于是「加一种需求」不再等于改这个文件——那正是兼容模块（{@code compat/}）成立的前提。
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

    /** Shared parser for loading and authoring; constructing it does not require a registered serializer. */
    public static final class MachineSerializer implements RecipeSerializer<MachineRecipe> {

        @Override
        public MachineRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<String> machines = MachineRecipe.parseMachines(json, id);
            String registryName = json.has("registryName") ? json.get("registryName").getAsString() : id.getPath();
            int recipeTime = json.has("recipeTime") ? com.reborn.modularmachinery.util.JsonNumbers.integer(
                    json.get("recipeTime"), 1, Integer.MAX_VALUE, id + " recipeTime") : 100;
            if (recipeTime < 1) {
                throw new JsonParseException("'recipeTime' of " + id + " must be at least 1");
            }

            // 0.107.0（C1）：适配器声明——JSON 顶层可选 `adapter`（如 "minecraft:furnace" 或另一台机器的 id）
            // 与 `modifiers` 数组。声明场景下 requirements 可省略（真实需求由生成轮从外部配方表产出）。
            ResourceLocation adapterKey = parseAdapter(json, id);
            List<RecipeModifier> adapterModifiers = parseAdapterModifiers(json, id);

            List<MachineRequirement> requirements;
            JsonElement rawRequirements = json.get("requirements");
            if (rawRequirements == null || !rawRequirements.isJsonArray()) {
                if (adapterKey == null) {
                    throw new JsonParseException("Missing 'requirements' array in " + id);
                }
                requirements = List.of();
            } else {
                JsonArray array = rawRequirements.getAsJsonArray();
                if (array.isEmpty() && adapterKey == null) {
                    throw new JsonParseException("Empty 'requirements' in " + id);
                }
                requirements = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        throw new JsonParseException("A requirement of " + id + " is not a JSON object");
                    }
                    // 需求的解析与 `selector-tag` 的挂载都在 MachineRequirementParser 里：它对每种需求类型
                    // 含义完全相同，所以整条路径只有一处。种类分派则交给 RequirementTypes 注册表。
                    try {
                        requirements.add(MachineRequirementParser.parse(element.getAsJsonObject(), id));
                    } catch (JsonParseException invalid) {
                        throw new JsonParseException(id + " requirements[" + requirements.size() + "]: "
                                + invalid.getMessage(), invalid);
                    }
                }
            }
            MachineRecipe recipe = new MachineRecipe(id, machines, registryName, recipeTime, requirements,
                    parseMaxParallelism(json, id), MachineRecipe.parseCancelIfPerTickFails(json, id),
                    RecipeCommands.parse(json), MachineRecipe.parsePriority(json, id),
                    parseTooltips(json, id), adapterKey, adapterModifiers);
            for (int i = 0; i < machines.size(); i++)
                MachineRequirementParser.validateText(machines.get(i), "machine[" + i + "]", id);
            MachineRequirementParser.validateText(registryName, "registryName", id);
            for (int i = 0; i < recipe.tooltips().size(); i++)
                MachineRequirementParser.validateText(recipe.tooltips().get(i), "tooltips[" + i + "]", id);
            for (var commands : List.of(recipe.commands().startCommands(), recipe.commands().processingCommands(),
                    recipe.commands().finishCommands())) {
                for (int i = 0; i < commands.size(); i++)
                    MachineRequirementParser.validateText(commands.get(i).command(), "commands[" + i + "]", id);
            }
            // Exercise the actual registered writers too, including currently installed requirement types.
            FriendlyByteBuf probe = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { toNetwork(probe, recipe); }
            catch (RuntimeException invalid) {
                throw new JsonParseException(id + " recipe wire fields cannot be encoded: " + invalid.getMessage(), invalid);
            } finally { probe.release(); }
            return recipe;
        }

        /**
         * 0.107.0（C1）：顶层可选 {@code adapter} 字段——适配器声明的标志。
         *
         * <p>旧版 {@code RecipeAdapterAccessor.Deserializer:124-136} 的同款规则：字符串、完整资源位置
         * （裸路径不做本模组命名空间前缀——与 {@code machine} 字段不同，旧版对 adapter 就是全 id 严格解析）。
         * {@code minecraft:furnace} 走熔炉适配器；其余按另一台机器的 id 走机器间复用。
         */
        private static ResourceLocation parseAdapter(JsonObject json, ResourceLocation id) {
            if (!json.has("adapter")) {
                return null;
            }
            JsonElement element = json.get("adapter");
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("'adapter' of " + id + " must be a string, e.g. "
                        + "\"minecraft:furnace\" or the full id of another machine");
            }
            ResourceLocation parsed = ResourceLocation.tryParse(element.getAsString());
            if (parsed == null) {
                throw new JsonParseException("'adapter' of " + id + " is '" + element.getAsString()
                        + "', which is not a valid resource location");
            }
            return parsed;
        }

        /**
         * 0.107.0（C1）：适配器声明的 {@code modifiers} 数组，字段名与元素形状照旧版
         * {@code RecipeAdapterAccessor.Deserializer:139-150}（target/io/operation/multiplier/affectChance），
         * 解析直接复用 {@link RecipeModifier#parse}——旧版 GSON 反序列化的就是这五个字段。
         */
        private static List<RecipeModifier> parseAdapterModifiers(JsonObject json, ResourceLocation id) {
            if (!json.has("modifiers")) {
                return List.of();
            }
            JsonElement element = json.get("modifiers");
            if (!element.isJsonArray()) {
                throw new JsonParseException("'modifiers' of " + id + " must be an array of modifier objects");
            }
            List<RecipeModifier> modifiers = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                modifiers.add(RecipeModifier.parse(entry, id));
            }
            return List.copyOf(modifiers);
        }

        /**
         * 0.98.0：配方级作者自定 tooltip 行，根字段 {@code tooltips}——单字符串或字符串数组。
         *
         * <p>旧版 {\code MachineRecipe#tooltipList} 的内容与展示规则原样保留，但旧版**没有 JSON 读入**
         * （{\code RecipeLoader} 构造时传的是 CT primer 攒下的列表，入口只有
         * {\code RecipePrimer.addRecipeTooltip}）；1.20.1 没有 CraftTweaker，所以入口改为本字段
         * （「CT 入口 → JSON 等价」移植，同 failure-action 的先例）。展示规则（翻译键则本地化、
         * 否则原文）在客户端 {\code MachineRecipeText} 应用。
         */
        private static List<String> parseTooltips(JsonObject json, ResourceLocation id) {
            if (!json.has("tooltips")) {
                return List.of();
            }
            JsonElement element = json.get("tooltips");
            if (element.isJsonPrimitive()) {
                return List.of(element.getAsString());
            }
            if (!element.isJsonArray()) {
                throw new JsonParseException("'tooltips' of " + id + " must be a string or an array of strings");
            }
            JsonArray array = element.getAsJsonArray();
            List<String> tooltips = new ArrayList<>(array.size());
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Every entry of 'tooltips' in " + id
                            + " must be a string (a translation key or literal text)");
                }
                tooltips.add(entry.getAsString());
            }
            return List.copyOf(tooltips);
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
            int value = com.reborn.modularmachinery.util.JsonNumbers.integer(element, 1, Integer.MAX_VALUE, id + " max-parallelism");
            if (value < 1) {
                throw new JsonParseException("'max-parallelism' of " + id + " is " + value + ", but a recipe may "
                        + "not run fewer than one copy. Write at least 1, or remove the field to let the "
                        + "machine's own max-parallelism decide.");
            }
            return value;
        }

        @Override
        public MachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            int machineCount = buf.readVarInt();
            List<String> machines = new ArrayList<>(machineCount);
            for (int i = 0; i < machineCount; i++) {
                machines.add(buf.readUtf());
            }
            String registryName = buf.readUtf();
            int recipeTime = buf.readVarInt();
            int maxParallelism = buf.readVarInt();
            boolean cancelIfPerTickFails = buf.readBoolean();
            // 旧版没有把这两个字段发到客户端：客户端从不同步配方。这一版客户端要列 JEI，所以必须发；
            // 尤其是 sortId——让客户端自己构造会得到不同的 buildWeight，同一台机器的配方顺序就会两端不一致。
            int priority = buf.readVarInt();
            int sortId = buf.readVarInt();
            RecipeCommands commands = RecipeCommands.read(buf);
            int count = buf.readVarInt();
            List<MachineRequirement> requirements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                requirements.add(MachineRequirementParser.read(buf));
            }
            // 0.98.0：tooltips 随网络同步——客户端 JEI 的 tooltip 也要显示（vanilla 配方同步走
            // fromNetwork，见 ClientboundUpdateRecipesPacket 的反编译确认）。
            int tooltipCount = buf.readVarInt();
            List<String> tooltips = new ArrayList<>(tooltipCount);
            for (int i = 0; i < tooltipCount; i++) {
                tooltips.add(buf.readUtf());
            }
            return new MachineRecipe(id, machines, registryName, recipeTime, requirements, maxParallelism,
                    cancelIfPerTickFails, commands, priority, sortId, tooltips);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MachineRecipe recipe) {
            buf.writeVarInt(recipe.machines().size());
            for (String machine : recipe.machines()) {
                buf.writeUtf(machine);
            }
            buf.writeUtf(recipe.registryName());
            buf.writeVarInt(recipe.recipeTime());
            buf.writeVarInt(recipe.maxParallelism());
            buf.writeBoolean(recipe.cancelIfPerTickFails());
            buf.writeVarInt(recipe.priority());
            buf.writeVarInt(recipe.sortId());
            recipe.commands().write(buf);
            buf.writeVarInt(recipe.requirements().size());
            for (MachineRequirement requirement : recipe.requirements()) {
                MachineRequirementParser.write(buf, requirement);
            }
            buf.writeVarInt(recipe.tooltips().size());
            for (String tooltip : recipe.tooltips()) {
                buf.writeUtf(tooltip);
            }
        }
    }

    // --------------------------------------------------------------- network
    //
    // 需求的两端都不再转发到私有方法：种类分派住在 MachineRequirementParser 里，因为那是**能离机驱动**的
    // 地方，而本类不能（它持有 DeferredRegister，静态初始化会碰 net.minecraftforge.fml.common.Mod）。
    // 这里留一层同名的私有转发除了让调用点看起来没变，没有任何作用——而「看起来没变」正是这一波要拆掉的东西。
}
