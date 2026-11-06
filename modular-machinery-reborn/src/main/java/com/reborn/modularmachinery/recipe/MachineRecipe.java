package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.HatchCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A machine recipe: a machine name, a duration, and a list of typed requirements.
 *
 * <p>This is the original schema, not the flat {@code input}/{@code output}/{@code duration}/{@code energy}
 * shortcut of 0.4–0.7:
 *
 * <pre>{@code
 * {
 *   "machine": "alloy_furnace",
 *   "registryName": "alloy_furnace_diamond",
 *   "recipeTime": 5,
 *   "requirements": [
 *     { "type": "...:energy", "io-type": "input", "energyPerTick": 100 },
 *     { "type": "...:item",   "io-type": "input", "item": "minecraft:coal", "amount": 32 },
 *     { "type": "...:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
 *   ]
 * }
 * }</pre>
 *
 * <p>The phase split follows the original lifecycle: {@link #start} consumes inputs, {@link #tick} runs the
 * per-tick requirements, and {@link #finish} produces outputs while rolling each output's chance
 * independently.
 *
 * <h2>Parallelism and modifiers</h2>
 *
 * <p>Both live on this class because both are properties of a <i>settlement</i> rather than of a requirement:
 *
 * <ul>
 *   <li>{@link #parallelism(HatchCollection, RecipeModifiers, int)} reproduces
 *       {@code RecipeCraftingContext#getMaxParallelism} — start from this recipe's own limit, then take the
 *       minimum over every requirement that can be parallelised, returning {@code 0} the moment one reports it
 *       cannot serve even a single copy.</li>
 *   <li>Every lifecycle method takes the machine's {@link RecipeModifiers} so requirement values and chances
 *       can be modified, which is what the original did by hanging a {@code RecipeCraftingContext} off the
 *       craft. The zero-argument overloads remain for callers with no machine attached (JEI, the recipe book,
 *       the plain-container check) and behave exactly as before M6c.</li>
 * </ul>
 *
 * <p>A craft freezes its parallelism when it starts, which is why {@link #setParallelism} exists separately:
 * the original's {@code RecipeCraftingContext#setParallelism} did the same, and only revisited it from
 * {@code canRestartCrafting}.
 */
public final class MachineRecipe implements Recipe<Container> {
    public static final String FAILURE_QUANTITY_RANGE = "craftcheck.failure.quantity.range";
    public static final String FAILURE_TICK = "craftcheck.failure.tick";
    private int executionTick;
    private boolean finalTickPaid;
    public boolean hasPaidFinalTick() { return finalTickPaid; }
    public void markFinalTickPaid() { finalTickPaid = true; }
    private String definitionFingerprint;
    public void setExecutionTick(int tick) { executionTick = Math.max(0, tick); }
    public net.minecraft.nbt.CompoundTag saveExecutionState() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("definition", definitionFingerprint());
        tag.putBoolean("finalTickPaid", finalTickPaid);
        tag.putIntArray("triggered", java.util.stream.IntStream.range(0, requirements.size()).filter(i -> requirements.get(i).triggered()).toArray());
        return tag;
    }

    /** Payment indices are meaningful only for the same definition, never merely the same recipe id. */
    public boolean canRestoreExecutionState(net.minecraft.nbt.CompoundTag tag) {
        return definitionFingerprint().equals(tag.getString("definition"));
    }

    private String definitionFingerprint() {
        if (definitionFingerprint != null) return definitionFingerprint;
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            buffer.writeVarInt(machines.size());
            for (String machine : machines) writeDefinitionText(buffer, machine);
            buffer.writeVarInt(recipeTime);
            buffer.writeVarInt(maxParallelism);
            buffer.writeBoolean(cancelIfPerTickFails);
            commands.write(buffer);
            buffer.writeVarInt(requirements.size());
            for (MachineRequirement requirement : requirements) {
                if (requirement instanceof ItemRequirement item) {
                    // Avoid client share-tag hooks: both private output data and input predicates belong
                    // to the server definition. Ingredient JSON retains custom/tag matching semantics.
                    writeDefinitionText(buffer, item.type().id().toString());
                    writeDefinitionText(buffer, item.ioType().name());
                    buffer.writeVarInt(item.minAmount());
                    buffer.writeVarInt(item.maxAmount());
                    buffer.writeFloat(item.chance());
                    if (item.ioType() == IOType.INPUT) {
                        writeDefinitionText(buffer, item.ingredient().toJson().toString());
                        writeDefinitionText(buffer, item.nbt() == null ? "" : item.nbt().source());
                    } else {
                        buffer.writeNbt(item.outputStack().save(new net.minecraft.nbt.CompoundTag()));
                    }
                    writeDefinitionText(buffer, item.selectorTag() == null ? "" : item.selectorTag());
                    buffer.writeBoolean(item.isParallelizeUnaffected());
                    writeDefinitionText(buffer, item.scriptOptions().toString());
                } else {
                    MachineRequirementParser.write(buffer, requirement);
                }
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            definitionFingerprint = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            return definitionFingerprint;
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", unavailable);
        } finally {
            buffer.release();
        }
    }

    private static void writeDefinitionText(net.minecraft.network.FriendlyByteBuf buffer, String text) {
        buffer.writeByteArray(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public void loadExecutionState(net.minecraft.nbt.CompoundTag tag) {
        finalTickPaid = tag.getBoolean("finalTickPaid");
        for (MachineRequirement requirement : requirements) requirement.setTriggered(false);
        for (int index : tag.getIntArray("triggered")) if (index >= 0 && index < requirements.size()) requirements.get(index).setTriggered(true);
    }

    /**
     * The implicit limit of a recipe that does not set {@code max-parallelism}.
     *
     * <p>The original had no per-recipe parallelism field at all: a recipe's ceiling was the machine's
     * {@code getMaxParallelism()}, and only a CraftTweaker script could lower it through
     * {@code ActiveMachineRecipe#setMaxParallelism}. {@code 0} here means "no opinion — use the machine's",
     * which is the faithful default and keeps every existing recipe behaving exactly as it did.
     */
    public static final int MACHINE_LIMIT = 0;

    private final ResourceLocation id;
    private final List<String> machines;
    private final List<ResourceLocation> machineIds;
    private final String registryName;
    private final int recipeTime;
    private final List<MachineRequirement> requirements;

    /** This recipe's own parallelism ceiling, or {@link #MACHINE_LIMIT} to defer to the machine's. */
    private final int maxParallelism;

    /**
     * The original's {@code cancelIfPerTickFails}: when a per-tick requirement cannot be served, drop this
     * craft (keeping the inputs already paid at start) instead of applying the machine's failure-action.
     */
    private final boolean cancelIfPerTickFails;

    private final RecipeCommands commands;

    /**
     * 配方级作者自定 tooltip 行（0.98.0）：JSON 根字段 {@code tooltips}（字符串或字符串数组）。
     *
     * <p>旧版 {\code MachineRecipe#tooltipList} 的对应物——但旧版只有 CraftTweaker 入口
     * （{\code RecipePrimer.addRecipeTooltip}），JSON 反序列化从不读它；1.20.1 没有 CraftTweaker，
     * 所以入口改为配方 JSON 字段（「CT 入口 → JSON 等价」移植，同 failure-action 的先例）。
     * 展示走旧版 {\code getFormattedTooltip} 的同款规则：翻译键则本地化，否则原文。
     */
    private final List<String> tooltips;

    /**
     * 0.107.0（C1 第一纵切）：适配器声明，旧版 {@code RecipeAdapterAccessor} 的对应物。
     *
     * <p>非 {@code null} 表示本条配方 JSON 顶层带了 {@code adapter} 字段——它是「声明占位」，
     * 不是可执行配方：真实配方由 {@code RecipeAdapterLoader}（reload 尾段）从外部配方表
     * （熔炉 / 父机器）生成后 {@code RecipeManager#replaceRecipes} 回注，占位在回注时被剔除。
     * 旧版把声明反序列化到独立队列（{@code RecipeLoader.RECIPE_ADAPTER_ACCESSORS}），
     * 由 {@code RecipeRegistry.reloadAdapters} 在普通配方注册完之后统一生成——两个版本都是
     * 「声明先占位、生成轮后补」，只是占位的存放处不同（那边是旁路队列，这边是 RecipeManager 本身）。
     */
    @javax.annotation.Nullable
    private final ResourceLocation adapterKey;

    /** 适配器声明的 {@code modifiers} 数组；生成时烘进数值（旧版 {@code RecipeAdapterAccessor#modifiers} 同位）。 */
    private final List<RecipeModifier> adapterModifiers;

    /**
     * 旧版配方级 {@code priority}：数字小的层先被遍历，同层再按 {@link #buildWeight()} 升序。
     *
     * <p>它不是"权重越大越优先"——旧版把它当 TreeMap 的键，{@code getRecipesFor} 按升序拼接，
     * 所以 0 层比 1 层先被看到。缺省 0。
     */
    private final int priority;

    /**
     * 构造顺序号，旧版 {@code MachineRecipe#sortId} 的全局自增计数器。
     *
     * <p><b>这一版把它写进网络包</b>，而旧版没有：客户端也要按同一顺序列 JEI 配方，若两端各自自增，
     * 同一条配方在两端的 {@link #buildWeight()} 会不同。读包时用服务端给的值构造，不推进本端计数器。
     */
    private final int sortId;

    /** 旧版每构造一条配方就 {@code counter++}；{@code AtomicInteger} 只为线程安全，语义不变。 */
    private static final AtomicInteger NEXT_SORT_ID = new AtomicInteger();

    private static int nextSortId() {
        return NEXT_SORT_ID.getAndIncrement();
    }

    public MachineRecipe(ResourceLocation id, String machine, String registryName, int recipeTime,
                         List<MachineRequirement> requirements) {
        this(id, List.of(machine), registryName, recipeTime, requirements, MACHINE_LIMIT, false,
                RecipeCommands.EMPTY);
    }

    public MachineRecipe(ResourceLocation id, String machine, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism) {
        this(id, List.of(machine), registryName, recipeTime, requirements, maxParallelism, false,
                RecipeCommands.EMPTY);
    }

    public MachineRecipe(ResourceLocation id, String machine, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails) {
        this(id, List.of(machine), registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                RecipeCommands.EMPTY);
    }

    /**
     * The canonical constructor. A recipe may name several owning machines (the original's {@code machine}
     * array); every one of them sees the recipe in its recipe list.
     */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, 0, nextSortId());
    }

    /** The same recipe at {@code priority}; the sort id still comes from the global construction counter. */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, priority, nextSortId());
    }

    /**
     * The canonical constructor, including the original's ordering fields.
     *
     * <p>{@code sortId} is supplied rather than allocated for one caller only: the network reader, which must
     * reproduce the server's order instead of advancing its own counter (see {@link #sortId()}).
     */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority, int sortId) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, priority, sortId, List.of());
    }

    /** FromJson overload: author tooltips with an auto-allocated sort id (0.98.0). */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority,
                         List<String> tooltips) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, priority, nextSortId(), tooltips);
    }

        /** The same recipe with author tooltip rows (0.98.0); the serializer and the network reader use this one. */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority, int sortId,
                         List<String> tooltips) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, priority, sortId, tooltips, null, List.of());
    }

    /**
     * 0.107.0：唯一携带适配器声明的构造器——只有序列化器的声明分支会用它
     * （{@code adapterKey != null}）；其余路径经上一构造器传 {@code null}。
     */
    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority,
                         List<String> tooltips, @javax.annotation.Nullable ResourceLocation adapterKey,
                         List<RecipeModifier> adapterModifiers) {
        this(id, machines, registryName, recipeTime, requirements, maxParallelism, cancelIfPerTickFails,
                commands, priority, nextSortId(), tooltips, adapterKey, adapterModifiers);
    }

    public MachineRecipe(ResourceLocation id, List<String> machines, String registryName, int recipeTime,
                         List<MachineRequirement> requirements, int maxParallelism,
                         boolean cancelIfPerTickFails, RecipeCommands commands, int priority, int sortId,
                         List<String> tooltips, @javax.annotation.Nullable ResourceLocation adapterKey,
                         List<RecipeModifier> adapterModifiers) {
        if (machines.isEmpty()) {
            throw new IllegalArgumentException("A machine recipe needs at least one owning machine");
        }
        this.id = id;
        this.machines = List.copyOf(machines);
        List<ResourceLocation> resolved = new ArrayList<>(machines.size());
        for (String machine : machines) {
            resolved.add(resolveMachineId(machine));
        }
        this.machineIds = List.copyOf(resolved);
        this.registryName = registryName;
        this.recipeTime = Math.max(1, recipeTime);
        this.requirements = List.copyOf(requirements);
        this.maxParallelism = Math.max(0, maxParallelism);
        this.cancelIfPerTickFails = cancelIfPerTickFails;
        this.commands = commands == null ? RecipeCommands.EMPTY : commands;
        this.priority = priority;
        this.sortId = sortId;
        this.tooltips = tooltips == null ? List.of() : List.copyOf(tooltips);
        this.adapterKey = adapterKey;
        this.adapterModifiers = adapterModifiers == null ? List.of() : List.copyOf(adapterModifiers);
    }

    private static ResourceLocation resolveMachineId(String machine) {
        ResourceLocation parsed = machine.indexOf(':') >= 0 ? ResourceLocation.tryParse(machine) : null;
        return parsed != null ? parsed : new ResourceLocation(ModularMachineryReborn.MOD_ID, machine);
    }

    /** The first owning machine, kept for callers that only need one name. */
    public String machine() {
        return this.machines.get(0);
    }

    /** Every owning machine, in the order the recipe file named them. */
    public List<String> machines() {
        return this.machines;
    }

    /** Every owning machine resolved to a registry name. */
    public List<ResourceLocation> machineIds() {
        return this.machineIds;
    }

    /** Whether this recipe belongs to {@code machine}. */
    public boolean belongsTo(ResourceLocation machine) {
        return this.machineIds.contains(machine);
    }

    /** The three command lists carried by this recipe. */
    public RecipeCommands commands() {
        return this.commands;
    }

    /** The {@code registryName} the original recipe file carried, or the file path when absent. */
    public String registryName() {
        return this.registryName;
    }

    /**
     * The first {@code machine} field resolved to a registry name.
     *
     * <p>Accepts either a full resource location ({@code modular_machinery_reborn:transformer}) or a bare path
     * ({@code transformer}), which is prefixed with this mod's namespace. The result is compared against the
     * machines in {@code MachineRegistry}.
     */
    public ResourceLocation machineId() {
        return this.machineIds.get(0);
    }

    /**
     * Reads the required {@code machine} field, which the original accepted as one string or a non-empty array
     * of strings (1.12.2 {@code MachineRecipe.java:338-360}).
     */
    public static List<String> parseMachines(JsonObject json, ResourceLocation id) {
        if (!json.has("machine")) {
            throw new JsonParseException("No 'machine'-entry specified!");
        }
        JsonElement element = json.get("machine");
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                throw new JsonParseException("'machine' is an array, but it's empty! Provide at least 1 "
                        + "owning-machine name!");
            }
            List<String> names = new ArrayList<>(array.size());
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Found an element in the array specified in 'machine' that "
                            + "is not a string! " + entry);
                }
                names.add(entry.getAsString());
            }
            return List.copyOf(names);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return List.of(element.getAsString());
        }
        throw new JsonParseException("'machine' has to be either an array of strings or just a string! - "
                + "Found " + element + " instead!");
    }

    public int recipeTime() {
        return this.recipeTime;
    }

    public List<MachineRequirement> requirements() {
        return this.requirements;
    }

    /** The original's {@code priority}: lower layers are visited first. Defaults to {@code 0}. */
    public int priority() {
        return this.priority;
    }

    /**
     * The construction-order number that breaks ties inside one priority layer.
     *
     * <p>On the client this is the value received from the server, not a locally allocated one, so both sides
     * compute the same {@link #buildWeight()} for the same recipe.
     */
    public int sortId() {
        return this.sortId;
    }

    /**
     * 配方级作者自定 tooltip 行（0.98.0）。展示规则照旧版 {\code getFormattedTooltip}：
     * 翻译键则本地化，否则原文——规则在客户端 JEI 侧应用（{\code MachineRecipeText}）。
     */
    public List<String> tooltips() {
        return this.tooltips;
    }

    /** 本条的适配器声明；{@code null} = 普通配方。见字段 javadoc 的声明占位语义。 */
    @javax.annotation.Nullable
    public ResourceLocation adapterKey() {
        return this.adapterKey;
    }

    /** 适配器声明的修饰符列表（不可变），生成轮烘进数值。 */
    public List<RecipeModifier> adapterModifiers() {
        return this.adapterModifiers;
    }

    /** 是否为适配器声明占位——占位不进 JEI、不参与匹配、不同步，只被生成轮消费。 */
    public boolean isAdapterDeclaration() {
        return this.adapterKey != null;
    }

    /**
     * The original's {@code buildWeight()}: the sort id minus every <b>input</b> requirement's sorting weight.
     *
     * <p>Outputs are skipped exactly as the original comparator skipped them ({@code RecipeRegistry:272-282}).
     * Smaller sorts first: every input subtracts its weight, so a recipe with more inputs — or with energy
     * inputs ({@code 50_000_000}) rather than item inputs ({@code 50_000}) — gets a smaller value and is tried
     * earlier. That is the original's way of preferring the "heavier" recipe inside one priority layer.
     *
     * <p>Arithmetic is deliberately plain {@code int}: the original could overflow (a recipe with 43 energy
     * inputs crosses {@link Integer#MIN_VALUE}), and clamping here would silently invent an order the original
     * never had. The verifier asserts the ordinary, non-overflowing case.
     */
    public int buildWeight() {
        int weight = this.sortId;
        for (MachineRequirement requirement : this.requirements) {
            if (requirement.ioType() == IOType.OUTPUT) {
                continue;
            }
            weight -= requirement.sortingWeight();
        }
        return weight;
    }

    /** This recipe's own parallelism ceiling, or {@code 0} when it defers to the machine's. */
    public int maxParallelism() {
        return this.maxParallelism;
    }

    /**
     * Whether a failed per-tick check cancels the whole craft rather than applying the failure-action.
     *
     * <p>This is the original's {@code MachineRecipe#doesCancelRecipeOnPerTickFailure} (1.12.2
     * {@code MachineRecipe.java:227-229}), read from {@code RecipeFailureEvent#isDestructRecipe}. The original
     * allowed that event to overwrite the value; this port has no failure-event layer yet, so the field is
     * consumed directly (registered as a deviation in {@code 迁移日志}).
     */
    public boolean cancelIfPerTickFails() {
        return this.cancelIfPerTickFails;
    }

    /**
     * Reads the optional {@code cancelIfPerTickFails} field, with the original's strict boolean rule.
     *
     * <p>It lives here rather than in the serializer's nested class so the offline verifier can exercise it:
     * {@code ModRecipeSerializers} holds a {@code DeferredRegister} and cannot initialise outside the game. The
     * original accepted only a JSON boolean and rejected everything else at parse time
     * ({@code MachineRecipe.java:381-388}); like the other recipe fields, omitting it means {@code false}.
     */
    public static boolean parseCancelIfPerTickFails(JsonObject json, ResourceLocation id) {
        if (!json.has("cancelIfPerTickFails")) {
            return false;
        }
        JsonElement element = json.get("cancelIfPerTickFails");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException("'cancelIfPerTickFails' has to be a boolean! (if specified) in " + id);
        }
        return element.getAsBoolean();
    }

    /**
     * Reads the optional {@code priority}, with the original's rule: absent means {@code 0}, anything that is not
     * a number is rejected at parse time.
     *
     * <p>The error keeps the original's sentence verbatim (1.12.2 {@code MachineRecipe.java:372-379}) and only
     * appends the recipe id, because "which file?" is the first question a pack author asks. Negative values are
     * accepted as-is: the original used whatever integer was written.
     */
    public static int parsePriority(JsonObject json, ResourceLocation id) {
        if (!json.has("priority")) {
            return 0;
        }
        JsonElement element = json.get("priority");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("'priority' has to be a number! (if specified) in " + id);
        }
        return com.reborn.modularmachinery.util.JsonNumbers.integer(element, Integer.MIN_VALUE, Integer.MAX_VALUE, id + " priority");
    }

    public List<MachineRequirement> requirements(IOType ioType) {
        List<MachineRequirement> out = new ArrayList<>();
        for (MachineRequirement requirement : this.requirements) {
            if (requirement.ioType() == ioType) {
                out.add(requirement);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ parallelism

    /**
     * How long one craft takes, after the machine's {@code duration} modifiers.
     *
     * <p>The original recomputed this on every tick ({@code ActiveMachineRecipe#tick}) and again at start, and
     * clamped it to at least one tick — a modifier may legitimately shorten a craft below a tick, in which case
     * {@code calculateExtraParallelism} multiplied the parallelism by {@code 1 / totalTick} instead.
     */
    public int duration(RecipeModifiers modifiers) {
        if (modifiers.isEmpty()) {
            return this.recipeTime;
        }
        double modified = modifiers.applyDuration(this.recipeTime);
        return RecipeTiming.duration(modified);
    }

    /**
     * The modifiers of catalysts payable after ordinary inputs, with no machine base modifiers.
     *
     * <p>A catalyst is an optional input in the original: absent means the craft runs without it and without
     * its modifiers ({@code CraftCheck.skipComponent()} was not an error), present means it is paid once and its
     * modifiers are folded in. Selection uses shared reservations, not independent presence checks. The caller freezes the result
     * for the lifetime of one craft: the catalyst is consumed at start, so re-asking the ports on every tick
     * would silently strip the modifiers half-way through.
     */
    public RecipeModifiers catalystModifiers(HatchCollection ports) {
        return prepareStart(ports, RecipeModifiers.EMPTY).catalystModifiers();
    }

    /** One immutable decision joins catalyst payment with the modifiers frozen for this start. */
    public record StartPlan(MachineRecipe owner, RecipeModifiers modifiers, RecipeModifiers catalystModifiers,
                            java.util.Set<Integer> catalysts, int parallelism, boolean feasible) {
        public StartPlan { catalysts = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(catalysts)); }
    }

    public StartPlan prepareStart(HatchCollection ports, RecipeModifiers base) {
        java.util.LinkedHashSet<Integer> selected = new java.util.LinkedHashSet<>();
        for (int i = 0; i < requirements.size(); i++) {
            if (requirements.get(i) instanceof CatalystRequirement catalyst
                    && catalyst.hasCatalyst(catalyst.visiblePorts(ports))) selected.add(i);
        }
        while (true) {
            List<RecipeModifier> contributions = new ArrayList<>();
            for (int index : selected) contributions.addAll(((CatalystRequirement) requirements.get(index)).modifiers());
            RecipeModifiers catalysts = RecipeModifiers.of(contributions);
            RecipeModifiers effective = base.andThen(catalysts);
            HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
            boolean ordinary;
            try { ordinary = reserveOrdinaryStart(snapshot, effective); }
            catch (ArithmeticException tooLarge) { ordinary = false; }
            if (!ordinary) {
                if (selected.isEmpty()) return new StartPlan(this, effective, catalysts, selected, parallelism(), false);
                // Optional costs must not block an otherwise affordable ordinary craft. Earlier entries win ties.
                selected.remove(new ArrayList<>(selected).get(selected.size() - 1));
                continue;
            }
            Integer unavailable = null;
            for (int index : selected) {
                CatalystRequirement catalyst = (CatalystRequirement) requirements.get(index);
                if (!catalyst.reserveRequired(catalyst.visiblePorts(snapshot))) { unavailable = index; break; }
            }
            if (unavailable == null) return new StartPlan(this, effective, catalysts, selected, parallelism(), true);
            selected.remove(unavailable);
        }
    }

    private boolean reserveOrdinaryStart(HatchCollection ports, RecipeModifiers modifiers) {
        for (MachineRequirement requirement : requirements) {
            if (requirement.isStartPhase() && !(requirement instanceof CatalystRequirement)
                    && !requirement.reserve(requirement.visiblePorts(ports), modifiers)) return false;
        }
        return true;
    }

    /** Admission may not assume that paying one fractional copy is a prerequisite for paying N.
     * The ordinary one-copy path stays unchanged; only its fractional start refusal needs a search.
     * Used on execution copies. Final events, start checks and transactional payment still run normally. */
    public StartPlan prepareParallelStart(HatchCollection ports, RecipeModifiers base, int ceiling) {
        parallelSearchIncomplete = false;
        StartPlan ordinary = prepareStart(ports, base);
        if (ordinary.feasible() || ceiling <= 1 || requirements.stream().noneMatch(r ->
                r instanceof com.reborn.modularmachinery.magic.MagicRequirement magic
                        && !magic.family.integerUnits() && r.isStartPhase())) return ordinary;
        int previous = parallelism();
        java.util.LinkedHashSet<Integer> selected = new java.util.LinkedHashSet<>();
        for (int i = 0; i < requirements.size(); i++)
            if (requirements.get(i) instanceof CatalystRequirement catalyst
                    && catalyst.hasCatalyst(catalyst.visiblePorts(ports))) selected.add(i);
        while (true) {
            List<RecipeModifier> contributions = new ArrayList<>();
            for (int index : selected) contributions.addAll(((CatalystRequirement) requirements.get(index)).modifiers());
            RecipeModifiers catalysts = RecipeModifiers.of(contributions), effective = base.andThen(catalysts);
            applyDurationMultiplier(effective);
            int count = parallelism(ports, effective, ceiling, selected);
            if (count > 0) {
                setParallelism(count);
                return new StartPlan(this, effective, catalysts, selected, count, true);
            }
            if (parallelSearchIncomplete() || selected.isEmpty()) {
                setParallelism(previous);
                return new StartPlan(this, effective, catalysts, selected, previous, false);
            }
            selected.remove(new ArrayList<>(selected).get(selected.size() - 1));
        }
    }

    private boolean reserveStart(HatchCollection ports, RecipeModifiers modifiers, java.util.Set<Integer> selected) {
        if (!reserveOrdinaryStart(ports, modifiers)) return false;
        for (int index : selected) {
            CatalystRequirement catalyst = (CatalystRequirement) requirements.get(index);
            if (!catalyst.reserveRequired(catalyst.visiblePorts(ports))) return false;
        }
        return true;
    }

    private StartPlan suppliedModifiersPlan(HatchCollection ports, RecipeModifiers modifiers) {
        HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
        java.util.Set<Integer> selected = new java.util.LinkedHashSet<>();
        boolean feasible;
        try { feasible = reserveOrdinaryStart(snapshot, modifiers); }
        catch (ArithmeticException tooLarge) { feasible = false; }
        if (feasible) for (int i = 0; i < requirements.size(); i++) {
            if (requirements.get(i) instanceof CatalystRequirement catalyst
                    && catalyst.reserveRequired(catalyst.visiblePorts(snapshot))) selected.add(i);
        }
        return new StartPlan(this, modifiers, RecipeModifiers.EMPTY, selected, parallelism(), feasible);
    }

    /**
     * The original's {@code RecipeCraftingContext#getMaxParallelism}: the lowest ceiling any parallelisable
     * requirement is willing to serve, starting from this recipe's own limit.
     *
     * <p>{@code 0} means "cannot be parallelised at all right now", which the original used to fall back to a
     * plain, single-copy craft check. That is the safe reading and it is what the controller does too.
     *
     * @param ceiling the machine's own limit, i.e. the original's {@code activeRecipe.getMaxParallelism()}
     */
    public int parallelism(HatchCollection ports, RecipeModifiers modifiers, int ceiling) {
        return parallelism(ports, modifiers, ceiling, null);
    }

    public int parallelism(HatchCollection ports, StartPlan plan, int ceiling) {
        if (plan.owner() != this || !plan.feasible()) return 0;
        return parallelism(ports, plan.modifiers(), ceiling, plan.catalysts());
    }

    private int parallelism(HatchCollection ports, RecipeModifiers modifiers, int ceiling,
                             java.util.Set<Integer> selected) {
        parallelSearchIncomplete = false;
        int limit = this.maxParallelism > 0 ? Math.min(ceiling, this.maxParallelism) : ceiling;
        if (limit <= 0) {
            return 0;
        }
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isParallelizable() || (requirement.ioType() == IOType.OUTPUT && requirement.option("ignore-output-check"))) {
                continue;
            }
            limit = Math.min(limit, requirement.parallelLimit(requirement.visiblePorts(ports), modifiers, limit));
            if (limit <= 0) {
                return 0;
            }
        }
        // 单条需求上界仍可能重复计算相同材料/空间；在共享副本上二分验证整条配方。
        int low = 0, high = limit;
        int[] previous = this.requirements.stream().mapToInt(MachineRequirement::parallelism).toArray();
        try {
            boolean fractional = requirements.stream().anyMatch(r -> r instanceof com.reborn.modularmachinery.magic.MagicRequirement magic
                    && magic.hasCandidateDependentFractionalCost(modifiers)
                    && (inPhase(r, 0, modifiers) || inPhase(r, 1, modifiers)
                        || !r.option("ignore-output-check") && inPhase(r, 2, modifiers)));
            fractional = fractional && !exactFractionalPlanning(ports, modifiers, limit);
            if (fractional) {
                // A non-moving double subtraction is refused by Planned. Consequently feasible counts
                // need not be a prefix. Never apply the monotone binary search to this domain.
                int budget = Math.min(4096, 65536 / Math.max(1, requirements.size()));
                List<GridConstraint> constraints = limit > budget ? fractionalGrids(ports, modifiers, limit) : List.of();
                FractionalParallelSearch.Budget proofBudget = new FractionalParallelSearch.Budget(65536);
                int work = 0;
                for (int candidate = limit; candidate > 0;) {
                    if (++work > budget) { parallelSearchIncomplete = true; return 0; }
                    if (candidate != limit && limit > budget) {
                        if (constraints.isEmpty()) { parallelSearchIncomplete = true; return 0; }
                        int next = candidate;
                        for (GridConstraint constraint : constraints) {
                            if (!constraint.unchanged().getAsBoolean()) { parallelSearchIncomplete = true; return 0; }
                            try { next = Math.min(next, constraint.window().previous(next, proofBudget)); }
                            catch (FractionalParallelSearch.Exhausted exhausted) { parallelSearchIncomplete = true; return 0; }
                        }
                        if (next <= 0) return 0;
                        if (next < candidate) { candidate = next; continue; }
                    }
                    setParallelism(candidate);
                    boolean start = selected == null ? checkPhase(ports, modifiers, 0) : checkStart(ports, modifiers, selected);
                    if (start && checkPhase(ports, modifiers, 1) && checkPhase(ports, modifiers, 2)) return candidate;
                    candidate--;
                }
                return 0;
            }
            while (low < high) {
                int middle = low + (int) (((long) high - low + 1) / 2);
                setParallelism(middle);
                boolean canStart = selected == null ? checkPhase(ports, modifiers, 0)
                        : checkStart(ports, modifiers, selected);
                if (canStart && checkPhase(ports, modifiers, 1)
                        && checkPhase(ports, modifiers, 2)) low = middle;
                else high = middle - 1;
            }
            return low;
        } finally {
            for (int i = 0; i < previous.length; i++) requirements.get(i).setParallelism(previous[i]);
        }
    }

    private boolean parallelSearchIncomplete;
    /** A finite complete search was not possible; zero is not evidence that all candidate counts fail. */
    public boolean parallelSearchIncomplete() { return parallelSearchIncomplete; }

    private record GridConstraint(FractionalParallelSearch.Window window,
                                  java.util.function.BooleanSupplier unchanged) {}
    /** Known linear planners remain monotone when the entire phase fits an exact dyadic lattice.
     * In particular, ordinary integer/half-unit outputs starting from zero must not be over-rejected.
     * Mixed IO on one identity is excluded: fixed inputs can otherwise become feasible only at larger N. */
    private boolean exactFractionalPlanning(HatchCollection ports, RecipeModifiers modifiers, int limit) {
        for (int phase = 0; phase < 3; phase++) {
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Double> amounts = new java.util.HashMap<>();
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Double> quanta = new java.util.HashMap<>();
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Double> balances = new java.util.HashMap<>();
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Integer> directions = new java.util.HashMap<>();
            for (MachineRequirement r : requirements) {
                if (!(r instanceof com.reborn.modularmachinery.magic.MagicRequirement magic)
                        || magic.family.integerUnits() || !inPhase(r, phase, modifiers)
                        || phase == 2 && r.option("ignore-output-check")) continue;
                if (magic.getClass() != com.reborn.modularmachinery.magic.MagicRequirement.class) return false;
                double unit = magic.parallelSearchCost(modifiers, 1);
                double maximum = magic.parallelSearchCost(modifiers, r.isParallelizable() ? limit : 1);
                if (!Double.isFinite(unit) || !Double.isFinite(maximum)) return false;
                for (var port : magic.parallelSearchPorts(r.visiblePorts(ports))) {
                    if (!port.access().linearParallelPlanning()) return false;
                    double before = port.access().amount();
                    if (!Double.isFinite(before) || !Double.isFinite(port.minimum()) || !Double.isFinite(port.maximum())) return false;
                    double quantum = Math.min(FractionalParallelSearch.exactQuantum(unit), Math.min(
                            FractionalParallelSearch.exactQuantum(before), Math.min(
                            FractionalParallelSearch.exactQuantum(port.minimum()), FractionalParallelSearch.exactQuantum(port.maximum()))));
                    amounts.merge(port.key(), Math.nextUp(maximum), (a,b) -> Math.nextUp(a + b));
                    quanta.merge(port.key(), quantum, Math::min); balances.put(port.key(), before);
                    directions.merge(port.key(), r.ioType() == IOType.INPUT ? 1 : 2, (a,b) -> a | b);
                }
            }
            for (var key : amounts.keySet()) {
                double bound = Math.nextUp(Math.abs(balances.get(key)) + amounts.get(key));
                if (directions.get(key) == 3 || !Double.isFinite(bound)
                        || Math.nextUp(bound / quanta.get(key)) > 0x1p53) return false;
            }
        }
        return true;
    }
    /** Conservative envelopes include every matching alias/requirement in each phase. A grid is used
     * only when the first eligible port alone can pay the complete demand throughout the envelope;
     * splitting/clamping or an unknown planner never supplies an unproven exclusion hint. */
    private List<GridConstraint> fractionalGrids(HatchCollection ports, RecipeModifiers modifiers, int limit) {
        List<GridConstraint> result = new ArrayList<>();
        for (int phase = 0; phase < 3; phase++) {
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Double> amounts = new java.util.HashMap<>();
            java.util.Map<com.reborn.modularmachinery.magic.MagicPort.Key, Integer> steps = new java.util.HashMap<>();
            java.util.Map<com.reborn.modularmachinery.magic.MagicRequirement,
                    List<com.reborn.modularmachinery.magic.MagicRequirement.ParallelPort>> options = new java.util.LinkedHashMap<>();
            for (MachineRequirement r : requirements) {
                if (!(r instanceof com.reborn.modularmachinery.magic.MagicRequirement magic)
                        || magic.family.integerUnits() || !inPhase(r, phase, modifiers)
                        || phase == 2 && r.option("ignore-output-check")) continue;
                var candidates = magic.parallelSearchPorts(r.visiblePorts(ports)); options.put(magic, candidates);
                double maximum = magic.getClass() == com.reborn.modularmachinery.magic.MagicRequirement.class
                        ? magic.parallelSearchCost(modifiers, r.isParallelizable() ? limit : 1) : Double.NaN;
                for (var candidate : candidates) {
                    amounts.merge(candidate.key(), maximum, (a,b) -> Math.nextUp(a + b));
                    steps.merge(candidate.key(), 1, Integer::sum);
                }
            }
            for (var entry : options.entrySet()) {
                var requirement = entry.getKey();
                if (!requirement.hasCandidateDependentFractionalCost(modifiers) || entry.getValue().isEmpty()) continue;
                var first = entry.getValue().get(0); var access = first.access();
                double before = access.amount(), amount = amounts.get(first.key());
                double rounding = 4d * steps.get(first.key()) * Math.ulp(Math.nextUp(Math.abs(before) + amount));
                double margin = Math.nextUp(amount + rounding);
                double lower = Math.nextDown(before - margin), upper = Math.nextUp(before + margin);
                if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower < first.minimum() || upper > first.maximum()) continue;
                double quantum = access.parallelQuantum(lower, upper);
                var window = FractionalParallelSearch.window(requirement.parallelSearchCost(modifiers, 1), quantum,
                        requirement.parallelSearchCost(modifiers, limit));
                if (window == null) continue;
                Object snapshot = access.snapshot();
                result.add(new GridConstraint(window, () -> access.unchanged(snapshot)));
            }
        }
        return result;
    }

    /** 每次合成持有独立需求状态，注册表对象始终只作为定义使用。 */
    public MachineRecipe executionCopy() {
        MachineRecipe copy = new MachineRecipe(id, machines, registryName, recipeTime,
                requirements.stream().map(MachineRequirementParser::copy).toList(), maxParallelism,
                cancelIfPerTickFails, commands, priority, sortId, tooltips, adapterKey, adapterModifiers);
        copy.definitionFingerprint = definitionFingerprint();
        return copy;
    }

    /**
     * Freezes how many copies every requirement settles. Called once, when a craft starts; the original did the
     * same through {@code RecipeCraftingContext#setParallelism}.
     */
    public void setParallelism(int parallelism) {
        for (MachineRequirement requirement : this.requirements) {
            requirement.setParallelism(parallelism);
        }
    }

    /**
     * The copies currently settled per craft, as fixed by the last {@link #setParallelism}.
     *
     * <p>Unaffected requirements are skipped: a catalyst keeps its own field on one copy, so reading it first would
     * report 1 for a craft that is actually running several copies.
     */
    public int parallelism() {
        for (MachineRequirement requirement : this.requirements) {
            if (requirement.isParallelizable()) {
                return requirement.parallelism();
            }
        }
        return 1;
    }

    /**
     * Pushes the shortened-duration cost compensation into every per-tick energy requirement.
     *
     * <p>The config decision is {@link ModConfig#enableDurationMultiplier()}: with the original's default
     * ({@code false}) this method writes {@code 1.0} and a duration modifier only changes the tick count. The
     * overload takes the decision explicitly for callers that have already resolved it, and for the offline
     * harness, which has no loaded config file.
     */
    public void applyDurationMultiplier(RecipeModifiers modifiers) {
        applyDurationMultiplier(modifiers, ModConfig.enableDurationMultiplier());
    }

    /** {@link #applyDurationMultiplier(RecipeModifiers)} with the config decision supplied by the caller. */
    public void applyDurationMultiplier(RecipeModifiers modifiers, boolean enabled) {
        float multiplier = modifiers.durationMultiplier(this.recipeTime, enabled);
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof EnergyRequirement energy) {
                energy.setDurationMultiplier(multiplier);
            } else if (requirement instanceof com.reborn.modularmachinery.magic.MagicRequirement magic) {
                magic.setDurationMultiplier(multiplier);
            }
        }
    }

    // ------------------------------------------------------------ craft lifecycle

    /** All one-shot inputs can be paid right now. */
    public boolean canStart(HatchCollection ports) {
        return canStart(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canStart} with modifiers applied to every requirement. */
    public boolean canStart(HatchCollection ports, RecipeModifiers modifiers) {
        return checkPhase(ports, modifiers, 0);
    }

    public boolean canStart(HatchCollection ports, StartPlan plan) {
        return plan.owner() == this && plan.feasible() && plan.parallelism() == parallelism()
                && checkStart(ports, plan.modifiers(), plan.catalysts());
    }

    private boolean checkStart(HatchCollection ports, RecipeModifiers modifiers, java.util.Set<Integer> selected) {
        try { return reserveStart(ports.transactionView(new ResourceTransaction()), modifiers, selected); }
        catch (ArithmeticException tooLarge) { return false; }
    }

    /**
     * The translation key of the first start-phase requirement that refuses this craft, or {@code null} when
     * every one of them is satisfied.
     *
     * <p>It exists because a boolean cannot say <i>why</i>: the controller used to report a bare
     * {@code idle} for every unmet start requirement, which for {@code interface_number_input} would hide the
     * original's own two messages ({@code component.missing.modularmachinery.interface.number},
     * {@code craftcheck.failure.interface.number.notequal}). The same
     * {@link #canSatisfy(MachineRequirement, HatchCollection, RecipeModifiers)} dispatch is used here, so the
     * message is only ever produced by a requirement that really did answer {@code false} — the two cannot
     * disagree.
     */
    @javax.annotation.Nullable
    public String startFailure(HatchCollection ports, RecipeModifiers modifiers) {
        HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
        for (MachineRequirement requirement : this.requirements) {
            if (!requirement.isStartPhase() || requirement instanceof CatalystRequirement) {
                continue;
            }
            try {
                if (requirement.reserve(requirement.visiblePorts(snapshot), modifiers)) {
                    continue;
                }
            } catch (ArithmeticException tooLarge) {
                // Normal preflight already rejects these quantities; diagnostics must not turn refusal into a tick crash.
                return FAILURE_QUANTITY_RANGE;
            }
            String message = requirement.startFailure(ports);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /** Read-only diagnostic for this execution tick, including due delayed requirements. */
    @javax.annotation.Nullable
    public String tickFailure(HatchCollection ports, RecipeModifiers modifiers) {
        HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
        for (MachineRequirement requirement : requirements) {
            if (!inPhase(requirement, 1, modifiers)) continue;
            HatchCollection visible = requirement.visiblePorts(snapshot);
            try {
                if (requirement.reserve(visible, modifiers)) continue;
            } catch (ArithmeticException tooLarge) {
                return FAILURE_QUANTITY_RANGE;
            }
            String message = requirement.startFailure(visible);
            return message == null ? FAILURE_TICK : message;
        }
        return null;
    }

    /** Consumes the one-shot inputs. Only call after {@link #canStart}. */
    public boolean start(HatchCollection ports, RandomSource random) {
        return start(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #start} with modifiers applied to every requirement. */
    public boolean start(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return start(ports, random, suppliedModifiersPlan(ports, modifiers));
    }

    public boolean start(HatchCollection ports, RandomSource random, StartPlan plan) {
        return start(ports, random, plan, () -> true);
    }

    /**
     * Plans the ordinary inputs and selected catalysts before checking a side-effect-free admission guard.
     * A refusal discards the unpaid plan. Existing callers keep their default-allowed start contract.
     * This does not make arbitrary external capability callbacks during commit atomic with admission.
     */
    public boolean start(HatchCollection ports, RandomSource random, StartPlan plan,
                         java.util.function.BooleanSupplier admission) {
        if (!canStart(ports, plan)) return false;
        ResourceTransaction transaction = new ResourceTransaction();
        HatchCollection snapshot = ports.transactionView(transaction);
        for (MachineRequirement requirement : requirements) {
            if (requirement.isStartPhase() && !(requirement instanceof CatalystRequirement)
                    && !requirement.apply(requirement.visiblePorts(snapshot), random, plan.modifiers())) return false;
        }
        for (int index : plan.catalysts()) {
            CatalystRequirement catalyst = (CatalystRequirement) requirements.get(index);
            if (!catalyst.applyRequired(catalyst.visiblePorts(snapshot), random)) return false;
        }
        if (!admission.getAsBoolean() || !transaction.commit()) return false;
        for (MachineRequirement requirement : requirements) requirement.setTriggered(false);
        finalTickPaid = false;
        executionTick = 0;
        return true;
    }

    /** Every per-tick requirement can be served this tick. */
    public boolean canTick(HatchCollection ports) {
        return canTick(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canTick} with modifiers applied to every requirement. */
    public boolean canTick(HatchCollection ports, RecipeModifiers modifiers) {
        return checkPhase(ports, modifiers, 1);
    }

    /** Runs the per-tick requirements: energy and per-tick fluids. */
    public boolean tick(HatchCollection ports, RandomSource random) {
        return tick(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #tick} with modifiers applied to every requirement. */
    public boolean tick(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return settlePhase(ports, random, modifiers, 1);
    }

    /**
     * Every output has room for its <b>guaranteed</b> amount. The chance roll is deliberately not applied here:
     * the original checked the finish with {@code ResultChance.GUARANTEED} so that a craft cannot start ending
     * when its possible result would not fit.
     */
    public boolean canFinish(HatchCollection ports) {
        return canFinish(ports, RecipeModifiers.EMPTY);
    }

    /** {@link #canFinish} with modifiers applied to every requirement. */
    public boolean canFinish(HatchCollection ports, RecipeModifiers modifiers) {
        return checkPhase(ports, modifiers, 2);
    }

    /** Guaranteed tick then finish on one read-only ledger, including room freed/used by that tick. */
    public boolean canTickAndFinish(HatchCollection ports, RecipeModifiers modifiers) {
        HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
        try {
            for (int phase = 1; phase <= 2; phase++) {
                for (MachineRequirement requirement : requirements) {
                    if (phase == 2 && (requirement.option("ignore-output-check")
                            || requirement.triggerTime() > 0 && inPhase(requirement, 1, modifiers))) continue;
                    if (inPhase(requirement, phase, modifiers)
                            && !requirement.reserve(requirement.visiblePorts(snapshot), modifiers)) return false;
                }
            }
            return true;
        } catch (ArithmeticException tooLarge) {
            return false;
        }
    }

    /** Produces the outputs, rolling each output requirement's chance on its own. */
    public boolean finish(HatchCollection ports, RandomSource random) {
        return finish(ports, random, RecipeModifiers.EMPTY);
    }

    /** {@link #finish} with modifiers applied to every requirement. */
    public boolean finish(HatchCollection ports, RandomSource random, RecipeModifiers modifiers) {
        return settlePhase(ports, random, modifiers, 2);
    }

    private boolean inPhase(MachineRequirement requirement, int phase, RecipeModifiers modifiers) {
        if (requirement.triggerTime() > 0) {
            // Finish also reserves unpaid delayed work: scripts may jump past the trigger tick.
            if (phase == 2) return !requirement.triggered();
            int due = RecipeTiming.triggerTick(recipeTime, requirement.triggerTime(), duration(modifiers));
            return phase == 1 && executionTick >= due
                    && (!requirement.triggered() || requirement.option("trigger-repeatable") && executionTick == due);
        }
        return switch (phase) {
            case 0 -> requirement.isStartPhase();
            case 1 -> requirement.isTickPhase();
            case 2 -> requirement.isFinishPhase();
            default -> throw new IllegalArgumentException("Unknown recipe phase " + phase);
        };
    }

    private boolean checkPhase(HatchCollection ports, RecipeModifiers modifiers, int phase) {
        if (phase == 0) {
            StartPlan plan = suppliedModifiersPlan(ports, modifiers);
            return plan.feasible();
        }
        HatchCollection snapshot = ports.transactionView(new ResourceTransaction());
        for (MachineRequirement requirement : requirements) {
            try {
                if (phase == 2 && requirement.option("ignore-output-check")) continue;
                if (inPhase(requirement, phase, modifiers) && !requirement.reserve(requirement.visiblePorts(snapshot), modifiers))
                    return false;
            } catch (ArithmeticException tooLarge) {
                return false;
            }
        }
        return true;
    }

    private boolean settlePhase(HatchCollection ports, RandomSource random, RecipeModifiers modifiers, int phase) {
        if (!checkPhase(ports, modifiers, phase)) return false;
        ResourceTransaction transaction = new ResourceTransaction();
        HatchCollection snapshot = ports.transactionView(transaction);
        for (MachineRequirement requirement : requirements) {
            if (inPhase(requirement, phase, modifiers) && !applyPhase(requirement, snapshot, random, modifiers)) return false;
        }
        if (!transaction.commit()) return false;
        if (phase == 1 || phase == 2) for (MachineRequirement requirement : requirements) {
            if (requirement.triggerTime() > 0 && inPhase(requirement, phase, modifiers)) requirement.setTriggered(true);
        }
        return true;
    }

    // ------------------------------------------------------------ dispatch

    /**
     * Routes a requirement to its modifier-aware method. The original had one implementation per requirement
     * class reading the context's modifier appliers directly; this mod passes that set through virtual methods,
     * including requirements supplied by optional integrations.
     *
     * <p><b>这是 {@code selector-tag} 的落点。</b>每个需求的四个阶段（能不能开工 / 开工结算 / 每 tick 检查 /
     * 结束时结算）都要经过这里或 {@link #applyPhase}，所以在这一处把端口换成
     * {@link MachineRequirement#visiblePorts} 的筛选视图，就同时覆盖了全部阶段——
     * 不会有「能开工但料消耗到别的仓」这种某一阶段漏筛的不一致。
     */
    private static boolean canSatisfy(MachineRequirement requirement, HatchCollection ports,
                                      RecipeModifiers modifiers) {
        HatchCollection visible = requirement.visiblePorts(ports);
        return requirement.canSatisfy(visible, modifiers);
    }

    /** {@link #canSatisfy} 的副作用那一半，所以它同样要把端口筛成需求能看见的那部分。 */
    private static boolean applyPhase(MachineRequirement requirement, HatchCollection ports, RandomSource random,
                                      RecipeModifiers modifiers) {
        HatchCollection visible = requirement.visiblePorts(ports);
        return requirement.isTickPhase()
                ? requirement.tick(visible, random, modifiers)
                : requirement.apply(visible, random, modifiers);
    }

    // ------------------------------------------------------------ Recipe<Container>

    /**
     * A weak check over a plain container: hand-written so the recipe is usable through the vanilla interfaces.
     * Real matching goes through {@link #canStart}, which also sees fluids, energy and every port.
     */
    @Override
    public boolean matches(Container container, Level level) {
        for (MachineRequirement requirement : this.requirements) {
            if (!(requirement instanceof ItemRequirement item) || !item.isStartPhase()) {
                continue;
            }
            if (item.ingredient() == null) {
                continue;
            }
            int found = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                // Same rule the machine uses: ingredient plus NBT template, not the ingredient alone.
                if (item.inputMatches(stack)) {
                    found += stack.getCount();
                }
            }
            if (found < item.amount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return primaryOutput();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return primaryOutput();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof ItemRequirement item && item.isStartPhase() && item.ingredient() != null) {
                list.add(item.ingredient());
            }
        }
        return list;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.MACHINE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.MACHINE.get();
    }

    /** Total FE a craft will draw from energy inputs over its whole duration, before modifiers. */
    public long totalEnergy() {
        long perTick = 0L;
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof EnergyRequirement energy && energy.ioType() == IOType.INPUT) {
                perTick += energy.energyPerTick();
            }
        }
        return perTick * this.recipeTime;
    }

    /** The stack shown as this recipe's result, for JEI and the recipe book. */
    public ItemStack primaryOutput() {
        for (MachineRequirement requirement : this.requirements) {
            if (requirement instanceof ItemRequirement item && item.isFinishPhase()) {
                return item.outputStack();
            }
        }
        return ItemStack.EMPTY;
    }
}
