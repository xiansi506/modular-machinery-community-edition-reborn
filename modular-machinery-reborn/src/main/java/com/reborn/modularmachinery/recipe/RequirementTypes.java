package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 需求种类的注册表——旧版 {@code RegistriesMM.REQUIREMENT_TYPE_REGISTRY} 的等价物，也是波 5.2 的主交付物。
 *
 * <p><b>它取代了什么。</b>0.32.0 到 0.32.0 为止，需求种类是 {@link ModRecipeSerializers} 里的五个
 * {@code private static final String}（{@code :45-49}），外加**三处**各自知道这五种名字的地方：
 * JSON 的 {@code switch}（{@code :154-200}）、写包的 {@code instanceof} 链（{@code :212-264}）、
 * 读包的 {@code switch}（{@code :303-351}）。所以「加一种需求」在旧结构里等于改主模组三个地方，
 * 而漏改第三处的症状是**一发包就抛异常**（解析和单机都正常）。现在加一种需求 =
 * {@link #register} 一行，见 {@link RequirementType}。
 *
 * <p><b>这个类为什么不持有 {@code DeferredRegister}。</b>与 {@link InterfaceNumberInputParser} 和
 * {@link IngredientArrayParser} 同一条理由，而且这两处正是为此才被拆出去的：持有
 * {@code DeferredRegister} 的类（{@link ModRecipeSerializers}）静态初始化会碰
 * {@code net.minecraftforge.fml.common.Mod}，而那个类只在 FML 自己的类加载器里存在，于是离线复现器**够不到**
 * 那里面的一切。注册表是纯逻辑，放在这里就能被复现器直接驱动——包括「注册一种新需求然后解析它」这条
 * 波 5.2 最关键的路径。
 *
 * <p><b>内置六种的 id 与旧版逐字相同</b>：{@code item} / {@code fluid} / {@code energy} /
 * {@code interface_number_input} / {@code ingredient_array_input} / {@code catalyst}（对照旧
 * {@code RequirementTypesMM.java:33-43} 的 {@code KEY_*}）。
 * 旧版的 {@code catalyst} 虽然注册过种类，但 {@code RequirementTypeCatalyst#createRequirement} 对 JSON
 * 直接返回 {@code null}，所以 JSON 里写 catalyst 在旧版根本解析不出需求；本版 0.65.0 起把它做成真正的
 * 需求类型（见 {@link CatalystParser}），旧版唯一的入口是 CraftTweaker 的 {@code RecipePrimer}。
 * 旧版另有 {@code fluid_pertick} / {@code gas} / {@code gas_pertick} / {@code item_durability} /
 * {@code duration} 五个键，本版不注册，理由各不相同且都已查证
 * （见 {@link MachineRequirementParser} 里未知种类的报错文案与 D18）。{@code duration} **不是需求种类**——
 * 旧版也注册过它，但唯一的用途是给「缩短时长」的修改器当目标，而 {@code RequirementDuration#createRequirement}
 * 直接抛异常；本版对应的是 {@link RecipeModifier.Target#DURATION}。
 */
public final class RequirementTypes {

    /**
     * 注册表本体。
     *
     * <p>{@code LinkedHashMap} 而不是 {@code HashMap}：{@link #ids()} 的结果会拼进报错消息，
     * 「有哪些种类」这句话的**顺序在两次运行之间必须一样**，否则同一份坏配方在两次日志里读起来像是两个问题。
     * 与 {@code MachineRegistry.replace} 用 {@code LinkedHashMap} 保住机器匹配顺序是同一条理由。
     */
    private static final Map<ResourceLocation, RequirementType> BY_ID = new LinkedHashMap<>();
    private static final ThreadLocal<Map<ResourceLocation, RequirementType>> STAGED = new ThreadLocal<>();
    private static long revision;

    private static Map<ResourceLocation, RequirementType> registry() {
        Map<ResourceLocation, RequirementType> staged = STAGED.get();
        return staged == null ? BY_ID : staged;
    }

    /** Publish one synchronous module's type changes only after all its registration callbacks succeed.
     * Other Java/world side effects are outside this registry transaction. */
    public static void registerAtomically(Runnable registration) {
        if (STAGED.get() != null) throw new IllegalStateException("Nested requirement registration transaction");
        long expectedRevision;
        Map<ResourceLocation, RequirementType> staged;
        synchronized (BY_ID) {
            expectedRevision = revision;
            staged = new LinkedHashMap<>(BY_ID);
        }
        STAGED.set(staged);
        try {
            registration.run();
            synchronized (BY_ID) {
                if (revision != expectedRevision) {
                    throw new IllegalStateException("Requirement registry changed during module registration");
                }
                BY_ID.clear();
                BY_ID.putAll(staged);
                revision++;
            }
        } finally {
            STAGED.remove();
        }
    }

    // ------------------------------------------------------------------ 内置六种

    /** {@code item}：配方里写 {@code "type": "…:item"}。 */
    public static final RequirementType ITEM =
            of("item", new ItemType());

    /** {@code fluid}：{@code "perTick": true} 就是旧版的 {@code fluid_pertick}。 */
    public static final RequirementType FLUID =
            of("fluid", new FluidType());

    /** {@code energy}：永远是 per-tick，旧版 {@code RequirementTypeEnergy} 也是这样。 */
    public static final RequirementType ENERGY =
            of("energy", new EnergyType());

    /** {@code interface_number_input}：不消耗资源，只做闸门。 */
    public static final RequirementType INTERFACE_NUMBER_INPUT =
            of("interface_number_input", new InterfaceNumberInputType());

    /** {@code ingredient_array_input}：一组可选的输入，只吃一种。 */
    public static final RequirementType INGREDIENT_ARRAY_INPUT =
            of("ingredient_array_input", new IngredientArrayType());

    /** {@code catalyst}：开工闸门 + 一组配方修改器，只吃一份、不随并行数放大（见 {@link CatalystRequirement}）。 */
    public static final RequirementType CATALYST =
            of("catalyst", new CatalystType());

    // ------------------------------------------------------------------ 注册与查找

    /**
     * 注册一种需求。
     *
     * <p>**重复 id 是错误，不是覆盖。**两种需求用同一个 id，意味着同一份配方文件在两次启动里可能被解析成两种
     * 不同东西，而配方能加载、能跑、看不出区别——这类不一致没有任何症状。
     * 「响亮失败」是本项目一贯做法（`MachineRequirementParser` 对未知种类也是报错），这里照办。
     *
     * <p>id 由注册的那个 {@link RequirementType#id()} 决定，命名空间通常写 {@link ModularMachineryReborn#MOD_ID}。
     * 查找时会丢弃命名空间，见 {@link #byName}。
     *
     * @return 注册进去的那一个，方便把 {@code TYPE} 建成常量
     */
    public static RequirementType register(RequirementType type) {
        synchronized (BY_ID) {
            ResourceLocation id = type.id();
            RequirementType existing = registry().putIfAbsent(id, type);
            if (existing != null) {
                throw new IllegalStateException("Requirement type '" + id + "' is already registered by "
                        + existing.getClass().getName() + "; a type may only be registered once, because the id is "
                        + "what a recipe file's 'type' field resolves to and two types under one id would make the "
                        + "same file mean two different things on two starts. Pick another id.");
            }
            if (STAGED.get() == null) revision++;
            return type;
        }
    }

    /**
     * 摘掉一种需求，返回被摘掉的那一个；没注册过则返回 {@code null}。
     *
     * <p><b>为什么这是公开 API 而不是「测试专用」的复位入口。</b>它来自旧版：那张 Forge 注册表是可以
     * {@code unfreeze()} 再 {@code remove()} 的，旧版正是靠它把依赖未装模组的类型摘掉
     * （{@code IntegrationTypeHelper.java:56-57}）。本版把它直接暴露出来，
     * {@link #removeUnavailable} 也走它。于是复现器不必绕道反射去复位注册表——
     * 「注册一种类型、驱动它、再摘掉」这条路本身就是生产代码走的同一条路。
     *
     * <p>摘掉内置五种会让已经加载的配方失去它们的解析器，所以调用方要想清楚；这里不做限制，
     * 因为限制它需要另一套「哪些是内置」的名单，而那正是本类要避免的东西。
     */
    @Nullable
    public static RequirementType deregister(@Nullable ResourceLocation id) {
        synchronized (BY_ID) {
            RequirementType removed = id == null ? null : registry().remove(id);
            if (removed != null && STAGED.get() == null) revision++;
            return removed;
        }
    }

    /** 已经注册的每一种需求，按注册顺序；给报错消息与诊断用。 */
    public static List<RequirementType> all() {
        synchronized (BY_ID) {
            return List.copyOf(registry().values());
        }
    }

    /** 已经注册的 id，按注册顺序，形如 {@code [modular_machinery_reborn:item, …]}。 */
    public static String ids() {
        synchronized (BY_ID) {
            return registry().keySet().toString();
        }
    }

    /** 按全限定 id 查一种需求，没有时返回 {@code null}。 */
    @Nullable
    public static RequirementType byId(@Nullable ResourceLocation id) {
        synchronized (BY_ID) {
            return id == null ? null : registry().get(id);
        }
    }

    /**
     * 按配方文件里写的那个名字查一种需求，没有时返回 {@code null}。**两次查找，顺序有意义。**
     *
     * <p><b>第一次：丢掉命名空间，按本模组的命名空间找。</b>这是既有行为而不是新决定：改动前的
     * {@code ModRecipeSerializers} 就是这么做的——{@code rawType.substring(colon + 1)} 之后只留 path，
     * 于是 {@code item}（裸写）、{@code modularmachinery:item}（旧模组 id）与
     * {@code modular_machinery_reborn:item}（本模组 id）三者等价。所以历史配方文件不必改一个字。
     * 与 {@code UpgradeRegistry#byName} 的写法一致。
     *
     * <p>代价是「另一个模组的命名空间」在**第一步里**没有区分力——{@code foo:item} 会解析成本模组的
     * {@code item}。这是既有行为的直接后果，保留它是因为改掉会让旧配方文件报错，而那是另一个决定。
     *
     * <p><b>第二次（波 5.2 新增）：按写出来的那个命名空间原样再找一次。</b>没有这一步，兼容模块的注册
     * 就是无效的：模块按惯例用自己的 modid 注册（{@code new ResourceLocation("ae2", "gas")}），
     * 而第一步只会去问 {@code modular_machinery_reborn:gas}——于是它**注册成功了、日志里也列着，
     * 却永远解析不到**，配方一律报「未知需求类型」。这正是本波第一版的实际缺陷，
     * 抓出它的是复现器里「模块注册的 kind 现在能被解析到吗」那一条。
     *
     * <p>顺序不能反：先问本模组命名空间，才能让旧模组 id（{@code modularmachinery:item}）继续归一到内置的
     * {@code item}，而不是先去撞一个可能存在的同名外来注册。
     */
    @Nullable
    public static RequirementType byName(@Nullable String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return null;
        }
        String trimmed = rawType.trim();
        int colon = trimmed.indexOf(':');
        String path = colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
        if (path.isEmpty() || path.indexOf(':') >= 0) {
            return null;
        }
        // 第一步：**照旧行为**——丢掉命名空间，按本模组的命名空间找。历史配方文件因此不必改一个字。
        // `modularmachinery:item` 是旧模组 id，它在这一步就归一到本模组的 `item`，与改动前完全一样
        // （改动前连"这一步"都没有，命名空间一律被丢弃）。
        ResourceLocation normalized = ResourceLocation.tryParse(ModularMachineryReborn.MOD_ID + ":" + path);
        RequirementType type = byId(normalized);
        if (type != null) {
            return type;
        }
        // 第二步（波 5.2 新增）：按**写出来的那个命名空间**原样再找一次。
        //
        // 没有这一步，兼容模块的注册就是无效的：一个模块按惯例用自己的 modid 注册
        // （`new ResourceLocation("ae2", "gas")`），而第一步只会去问
        // `modular_machinery_reborn:gas`，于是它注册成功了、配方文件也写了，却永远解析不到——
        // 症状是"未知需求类型"，而注册表的日志里明明列着它。这一步是本波第一版漏掉的东西，
        // 抓出它的是复现器里「模块注册的 kind 现在能被解析到吗」那一条。
        ResourceLocation literal = colon >= 0 ? ResourceLocation.tryParse(trimmed) : null;
        return byId(literal);
    }

    /**
     * 摘掉依赖的模组没装的种类，旧版
     * {@code IntegrationTypeHelper.filterModIdRequirementTypes}（{@code :45-58}）的等价物。
     *
     * <p><b>为什么要有这个兜底。</b>注册时机本身（见 {@code compat/ModCompat}）已经按「模组装没装」判断过一次，
     * 但那一次判断只覆盖了本模组自己的兼容模块。旧版面对的处境更宽：任何模组都可以往那张 Forge 注册表里放一行，
     * 而那张表不保证与模组加载顺序对齐，所以旧版在**所有注册完成之后**再用这个方法清一遍。
     * 本版保留它，于是「注册时机判断错了」的后果是少一次清理、而不是注册表里留下一种永远不可用的需求。
     *
     * <p>{@code isLoaded} 传进来而不是在这里调 {@code ModList}：这样这一条能被离线断言驱动，
     * 而不用去猜 FML 在哪个阶段才能回答「模组装了没」。
     *
     * @return 被摘掉的 id，按注册顺序；调用方据此打日志
     */
    public static List<ResourceLocation> removeUnavailable(Predicate<String> isLoaded) {
        List<ResourceLocation> removed = new java.util.ArrayList<>();
        // 先快照再摘：在遍历 BY_ID 的过程中删它，就是一边读一边改的那类错误。
        for (RequirementType type : all()) {
            String required = type.requiresModid();
            if (required != null && !isLoaded.test(required)) {
                deregister(type.id());
                removed.add(type.id());
            }
        }
        return removed;
    }

    /**
     * 造一个内置种类并**注册它**。
     *
     * <p>{@code register} 的返回值和 {@code RequirementType.of} 的返回值是同一个对象，所以这里返回哪个都一样；
     * 写成 {@code register(...)} 是为了让「内置五种也会走注册」这件事在代码上**不可能被漏掉**。
     *
     * <p>这不是假想的顾虑：本波的第一版就是漏了这一步——{@code return RequirementType.of(...)} 让五个常量
     * 各自拿到了一个可用的读写器，而注册表是空的（{@code byName("item")} 返回 null、{@code all()} 返回空表）。
     * 症状是**所有配方都报「未知需求类型」**，而五个常量本身看不出任何异常。
     * 抓出它的是复现器恒等那一节的最后一条断言（{@code byName('item')} 与 {@code ITEM} 是同一个），
     * 而不是任何一条「看起来更该抓到它」的断言。
     */
    private static RequirementType of(String path, RequirementType.Handler handler) {
        return register(RequirementType.of(
                new ResourceLocation(ModularMachineryReborn.MOD_ID, path), handler));
    }

    private RequirementTypes() {
    }

    // ------------------------------------------------------------------ 内置五种的实现
    //
    // 五个 Handler 的 read/write/readBody 是**逐行照抄**改动前 ModRecipeSerializers 里那三处的对应分支，
    // 包括每一条报错文案。搬移本身不改行为，改动面只有「谁来分派」。

    /** {@code item}（旧版 {@code RequirementTypeItem}）。 */
    private static final class ItemType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            Ingredient ingredient = LegacyIngredients.parse(
                    MachineRequirementParser.string(json, "item", where), String.valueOf(where));
            int amount = json.has("amount") ? com.reborn.modularmachinery.util.JsonNumbers.integer(json.get("amount"), 0, Integer.MAX_VALUE, where + " amount") : 1;
            int min = json.has("minAmount") ? com.reborn.modularmachinery.util.JsonNumbers.integer(json.get("minAmount"), 0, Integer.MAX_VALUE, where + " minAmount") : amount;
            int max = json.has("maxAmount") ? com.reborn.modularmachinery.util.JsonNumbers.integer(json.get("maxAmount"), 0, Integer.MAX_VALUE, where + " maxAmount") : amount;

            // The original read nbt and nbt-display only when nbt was present, and read nbt-display only
            // inside that branch (RequirementTypeItem.java:95-119). A lone nbt-display was therefore ignored;
            // keep that rather than inventing a display-only template.
            NbtPredicate nbt = null;
            CompoundTag displayTag = null;
            if (json.has("nbt")) {
                nbt = readNbt(json.get("nbt"), "nbt", where);
                if (json.has("nbt-display")) {
                    displayTag = readNbt(json.get("nbt-display"), "nbt-display", where).toTag();
                }
            }

            if (ioType == IOType.INPUT) {
                if (min != max) {
                    throw new JsonParseException("An item input in " + where
                            + " uses minAmount/maxAmount; only outputs may vary their amount. "
                            + "Give inputs a fixed 'amount'.");
                }
                return ItemRequirement.input(ingredient, min, nbt, displayTag,
                        MachineRequirementParser.chance(json));
            }
            ItemStack stack = LegacyIngredients.firstStack(ingredient).copy();
            if (stack.isEmpty()) {
                throw new JsonParseException("An item output in " + where + " resolved to no concrete item");
            }
            if (nbt != null) {
                // The original attached the parsed tag to the produced stack (RequirementItem.java:496-498).
                // Comparison modes are not NBT, so this is the concrete number/pattern, as in the original.
                stack.setTag(nbt.toTag());
            }
            return ItemRequirement.output(stack, min, max, MachineRequirementParser.chance(json), displayTag);
        }

        private static NbtPredicate readNbt(com.google.gson.JsonElement raw, String key, Object where) {
            if (raw == null || (!raw.isJsonObject() && !LiteralNbt.isEncoded(raw))) {
                throw new JsonParseException("The ComponentType '" + key + "' expects a json compound that "
                        + "defines the NBT tag" + ("nbt-display".equals(key)
                        ? " meant to be used for displaying!" : "!"));
            }
            return NbtPredicate.parse(raw, where);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            ItemRequirement item = (ItemRequirement) requirement;
            buf.writeUtf(item.ioType().name());
            buf.writeVarInt(item.minAmount());
            buf.writeVarInt(item.maxAmount());
            if (item.ioType() == IOType.INPUT) {
                Ingredient ingredient = item.ingredient();
                (ingredient == null ? Ingredient.EMPTY : ingredient).toNetwork(buf);
                NbtPredicate nbt = item.nbt();
                buf.writeBoolean(nbt != null);
                if (nbt != null) {
                    buf.writeUtf(nbt.source());
                }
                buf.writeFloat(item.chance());
            } else {
                buf.writeItem(item.outputStack().copyWithCount(1));
                buf.writeFloat(item.chance());
            }
            // nbt-display is a preview override in both directions; the output's actual tag is already in the
            // ItemStack above, so only this optional override travels separately.
            buf.writeNbt(item.displayTag());
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            if (ioType == IOType.INPUT) {
                Ingredient ingredient = Ingredient.fromNetwork(buf);
                NbtPredicate nbt = buf.readBoolean() ? NbtPredicate.parse(buf.readUtf(), "item requirement") : null;
                float chance = buf.readFloat();
                return ItemRequirement.input(ingredient, min, nbt, buf.readNbt(), chance);
            }
            ItemStack stack = buf.readItem();
            float chance = buf.readFloat();
            return ItemRequirement.output(stack, min, max, chance, buf.readNbt());
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.ITEM;
        }
    }

    /** {@code fluid}，不含 per-tick（旧版 {@code RequirementTypeFluid} 与 {@code RequirementTypeFluidPerTick}）。 */
    private static final class FluidType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            String fluidId = MachineRequirementParser.string(json, "fluid", where);
            ResourceLocation key = ResourceLocation.tryParse(fluidId);
            Fluid fluid = key == null ? null : ForgeRegistries.FLUIDS.getValue(key);
            // Forge resolves missing keys to EMPTY; checking only null accepts invalid resources.
            // Check the resolved value so existing registry aliases to usable fluids remain valid.
            if (fluid == null || fluid == Fluids.EMPTY) {
                throw new JsonParseException("Unknown or empty fluid '" + fluidId + "' in " + where);
            }
            int amount = json.has("amount") ? com.reborn.modularmachinery.util.JsonNumbers.integer(json.get("amount"), 0, Integer.MAX_VALUE, where + " amount") : 1;
            boolean perTick = false;
            if (json.has("perTick")) {
                var flag = json.get("perTick");
                if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                    throw new JsonParseException("'perTick' must be true or false in " + where);
                }
                perTick = flag.getAsBoolean();
            }
            return new FluidRequirement(ioType, new FluidStack(fluid, 1), amount,
                    MachineRequirementParser.chance(json), perTick);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            FluidRequirement fluid = (FluidRequirement) requirement;
            buf.writeUtf(fluid.ioType().name());
            fluid.fluidType().writeToPacket(buf);
            buf.writeVarInt(fluid.amount());
            buf.writeFloat(fluid.chance());
            buf.writeBoolean(fluid.perTick());
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            FluidStack fluid = FluidStack.readFromPacket(buf);
            int amount = buf.readVarInt();
            float chance = buf.readFloat();
            boolean perTick = buf.readBoolean();
            return new FluidRequirement(ioType, fluid, amount, chance, perTick);
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.FLUID;
        }
    }

    /** {@code energy}（旧版 {@code RequirementTypeEnergy}）。 */
    private static final class EnergyType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            var rate = json.get("energyPerTick");
            if (rate == null || !rate.isJsonPrimitive() || !rate.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("'energyPerTick' must be a numeric non-negative long in " + where);
            }
            long perTick;
            try {
                perTick = rate.getAsBigDecimal().longValueExact();
                if (perTick < 0L) throw new ArithmeticException("negative rate");
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new JsonParseException("'energyPerTick' must be an exact non-negative long in " + where,
                        exception);
            }
            return new EnergyRequirement(ioType, perTick);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            EnergyRequirement energy = (EnergyRequirement) requirement;
            buf.writeUtf(energy.ioType().name());
            buf.writeVarLong(energy.energyPerTick());
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            return new EnergyRequirement(ioType, buf.readVarLong());
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.ENERGY;
        }
    }

    /** {@code interface_number_input}：正文交给既有解析器（旧版 {@code RequirementTypeInterfaceNumInput}）。 */
    private static final class InterfaceNumberInputType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            return InterfaceNumberInputParser.parse(json, ioType, where);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            InterfaceNumberInputRequirement interfaceRequirement = (InterfaceNumberInputRequirement) requirement;
            buf.writeUtf(interfaceRequirement.ioType().name());
            buf.writeUtf(interfaceRequirement.interfaceType());
            buf.writeFloat(interfaceRequirement.minValue());
            buf.writeFloat(interfaceRequirement.maxValue());
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            String type = buf.readUtf();
            float min = buf.readFloat();
            float max = buf.readFloat();
            return new InterfaceNumberInputRequirement(type, min, max, null);
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.ITEM;
        }
    }

    /** {@code ingredient_array_input}：正文交给既有解析器（旧版 {@code RequirementTypeIngredientArray}）。 */
    private static final class IngredientArrayType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            return IngredientArrayParser.parse(json, ioType, where);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            IngredientArrayRequirement array = (IngredientArrayRequirement) requirement;
            buf.writeUtf(array.ioType().name());
            buf.writeFloat(array.chance());
            NbtPredicate nbt = array.nbt();
            buf.writeBoolean(nbt != null);
            if (nbt != null) {
                buf.writeUtf(nbt.source());
            }
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
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            float chance = buf.readFloat();
            NbtPredicate nbt = buf.readBoolean() ? NbtPredicate.parse(buf.readUtf(), "ingredient array") : null;
            int entries = buf.readVarInt();
            List<IngredientArrayEntry> parsed = new java.util.ArrayList<>(entries);
            for (int index = 0; index < entries; index++) {
                int amount = buf.readVarInt();
                int matches = buf.readVarInt();
                List<ItemStack> alternatives = new java.util.ArrayList<>(matches);
                for (int match = 0; match < matches; match++) {
                    alternatives.add(buf.readItem());
                }
                parsed.add(IngredientArrayEntry.ofAlternatives(alternatives, amount, chance));
            }
            return new IngredientArrayRequirement(parsed, chance, nbt);
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.ITEM;
        }
    }

    /** {@code catalyst}：解析与网络都委托 {@link CatalystParser}，避免两套条目语法。 */
    private static final class CatalystType implements RequirementType.Handler {

        @Override
        public MachineRequirement read(JsonObject json, IOType ioType, Object where) {
            return CatalystParser.parse(json, ioType, where);
        }

        @Override
        public void write(FriendlyByteBuf buf, MachineRequirement requirement) {
            CatalystParser.write(buf, (CatalystRequirement) requirement);
        }

        @Override
        public MachineRequirement readBody(FriendlyByteBuf buf, IOType ioType) {
            return CatalystParser.read(buf);
        }

        @Override
        public RecipeModifier.Target modifierTarget() {
            return RecipeModifier.Target.ITEM;
        }
    }
}
