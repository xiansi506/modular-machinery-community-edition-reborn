package com.reborn.modularmachinery.authoring;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.recipe.*;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The editor retains source JSON; runtime objects are used for validation, never for lossy reserialization. */
public final class RecipeDocument {
    public static final int MAX_SOURCE = 16000, MAX_BYTES = 24000, MAX_REQUIREMENTS = 64;
    /** The deepest real container tree the editor reads: the root object counts as 1. */
    public static final int MAX_DEPTH = 64;
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final List<String> TYPES = List.of("item", "fluid", "gas", "energy", "mana", "aura", "lifeessence", "will", "compressed_air", "ember");
    public enum Format { JSON, KUBEJS }
    private RecipeDocument() {}

    public static ResourceLocation id(String value) {
        ResourceLocation id = value.contains(":") ? ResourceLocation.tryParse(value) : null;
        if (id == null || value.length()>256) throw new IllegalArgumentException("Use a complete resource ID: namespace:path (at most 256 characters)");
        for(String segment:id.getPath().split("/",-1))if(segment.isEmpty()||segment.equals(".")||segment.equals(".."))throw new IllegalArgumentException("Recipe ID must have a safe nonempty resource path");
        return id;
    }

    public static JsonObject read(String source) {
        if (source.length() > MAX_SOURCE || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Recipe exceeds 16000 characters / 24000 UTF-8 bytes");
        checkDepth(source);
        JsonElement json = JsonParser.parseString(source);
        if (!json.isJsonObject()) throw new IllegalArgumentException("Recipe must be a JSON object");
        return json.getAsJsonObject();
    }

    /**
     * P01-04（0.121.47）：深度上限必须与真正解析它的语法一致。
     *
     * <p>旧版在 {@code JsonParser} 之前自己扫字符，只认双引号字符串。而 {@code JsonParser}
     * 用宽松的 {@code JsonReader} 解析（见 {@code JsonParser.parseReader(JsonReader)} 自己调
     * {@code setLenient(true)}），同样接受单引号字符串、注释和未加引号的键名。于是一个装满
     * {@code }} 的单引号值可以把计数压到负数，让 200 层嵌套“通过”上限 64 的读取——限制本身
     * 失效，只是后面的规范化复检有时又挡住了它。
     *
     * <p>这里改用<b>解析器自己的 tokenizer</b>做深度检查：{@link JsonReader} 是迭代的（不会
     * 递归），语法与随后的 {@code JsonParser} 完全同源，所以不存在“半解析器”与真实语法不一致
     * 的缝。容器深度按真实结构计数（根对象计 1），在任何 {@code toString}／{@code deepCopy}／
     * 表单构建／递归消费者之前完成，因此 16000 字符能表达的极深树不会先把栈走穿。
     *
     * <p>只做深度检查：接受／拒绝的语法边界仍由 {@code JsonParser} 决定，单引号、注释这些既有的
     * 宽松写法不会被悄悄改成严格拒绝；错误仍是可恢复的受控异常，原文由调用方保留。
     */
    private static void checkDepth(String source) {
        JsonReader reader = new JsonReader(new StringReader(source));
        reader.setLenient(true); // the same setting JsonParser applies before it parses
        int depth = 0;
        try {
            while (true) {
                JsonToken token = reader.peek();
                if (token == JsonToken.END_DOCUMENT) return;
                // Plain comparisons, not a switch over the enum: a switch would add a synthetic
                // RecipeDocument$1 class to the shipped jar for no behavioural gain.
                if (token == JsonToken.BEGIN_OBJECT) {
                    if (++depth > MAX_DEPTH) throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
                    reader.beginObject();
                } else if (token == JsonToken.BEGIN_ARRAY) {
                    if (++depth > MAX_DEPTH) throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
                    reader.beginArray();
                } else if (token == JsonToken.END_OBJECT) {
                    reader.endObject(); depth--;
                } else if (token == JsonToken.END_ARRAY) {
                    reader.endArray(); depth--;
                } else if (token == JsonToken.NAME) {
                    reader.nextName();
                } else {
                    reader.skipValue();
                }
            }
        } catch (IOException error) {
            // MalformedJsonException is an IOException; JsonParser reports the same class of failure.
            throw new JsonSyntaxException(error);
        } catch (IllegalStateException error) {
            throw new JsonSyntaxException("Malformed JSON: " + error.getMessage(), error);
        }
    }

    public static JsonObject create(String machine) {
        JsonObject root = new JsonObject(); root.addProperty("type", "modular_machinery_reborn:machine");
        root.addProperty("machine", machine); root.addProperty("recipeTime", 100);
        JsonArray requirements = new JsonArray(); requirements.add(requirement("item")); root.add("requirements", requirements);
        return root;
    }

    public static JsonObject requirement(String type) {
        JsonObject req = new JsonObject(); req.addProperty("type", type); req.addProperty("io-type", "input");
        req.addProperty(type.equals("energy") ? "energyPerTick" : type.equals("compressed_air") ? "air" : "amount", 1);
        switch (type) {
            case "item" -> req.addProperty("item", "minecraft:iron_ingot");
            case "fluid" -> req.addProperty("fluid", "minecraft:water");
            case "gas" -> req.addProperty("gas", "mekanism:oxygen");
            case "aura" -> req.addProperty("aura-type", "naturesaura:overworld");
            case "will" -> req.addProperty("will-type", "default");
            case "compressed_air" -> req.addProperty("pressure", 0);
        }
        return req;
    }

    public static String kind(JsonObject req) {
        String type = req.get("type").getAsString(); int colon = type.indexOf(':');
        if (colon >= 0) type = type.substring(colon + 1);
        return type.endsWith("_pertick") ? type.substring(0, type.length()-8) : type;
    }

    /**
     * P01-01（0.121.39）：表单结构校验——原文切回表单前，检查表单控件真正依赖的类型前提。
     *
     * <p>{@link #read} 只保证顶层是 JSON 对象；而 {@code RecipeEditorScreen} 的需求页会直接
     * {@code getAsJsonArray("requirements")}、对每项 {@code getAsJsonObject()}、读
     * {@code type} 的字符串、以及 {@code perTick}/{@code parallelize-unaffected} 的布尔——
     * 语法合法但结构不符的原文（{@code requirements:[1]}、{@code {}}、{@code [{}]}、缺
     * {@code type}、布尔字段写成数字/字符串等）会让这些直接读取抛出未捕获异常。此校验
     * 在切换到表单页之前运行：返回 {@code null} 表示表单可显示；否则返回一条带
     * {@code requirements[序号].字段} 位置的错误，屏幕据此留在原文页并完整保留原文。
     *
     * <p>只做表单可显示性的结构检查：不验证服务端配方语义（金额范围、资源存在性等仍由
     * 验证回执负责），不触碰 NBT 数据、脚本字符串或未知高级需求的自定义键——未知但结构
     * 合法的需求（对象 + 非空字符串 type）保持原样通过，原表单对它们的 rawHelp 入口不变。
     */
    public static String formStructureError(JsonObject root) {
        if (!root.has("requirements")) return null;
        JsonElement reqs = root.get("requirements");
        if (!(reqs instanceof JsonArray array)) return "requirements must be a JSON array, got " + describe(reqs) + "; fix the raw source before opening the form";
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!(entry instanceof JsonObject req)) return "requirements[" + i + "] must be a JSON object, got " + describe(entry);
            if (!req.has("type")) return "requirements[" + i + "].type is missing; write the requirement type as a string";
            JsonElement type = req.get("type");
            if (!(type instanceof JsonPrimitive primitive) || !primitive.isString() || primitive.getAsString().isBlank())
                return "requirements[" + i + "].type must be a non-empty string, got " + describe(type);
            // The form reads these booleans directly on every known requirement kind; a number or
            // string there would throw outside the collect guard. Unknown kinds never reach that read.
            if (TYPES.contains(kindOf(primitive.getAsString()))) {
                for (String key : List.of("perTick", "parallelize-unaffected")) {
                    if (req.has(key) && !(req.get(key) instanceof JsonPrimitive flag && flag.isBoolean()))
                        return "requirements[" + i + "]." + key + " must be a boolean, got " + describe(req.get(key));
                }
            }
        }
        return null;
    }

    /** The kind of a type string without requiring an object — for the structural check above. */
    private static String kindOf(String type) {
        int colon = type.indexOf(':');
        if (colon >= 0) type = type.substring(colon + 1);
        return type.endsWith("_pertick") ? type.substring(0, type.length()-8) : type;
    }

    /** A short reader-facing name for a JSON value's type, used in locatable structure errors. */
    private static String describe(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonObject()) return "an object";
        if (value.isJsonArray()) return "an array";
        if (value.getAsJsonPrimitive().isString()) return "a string";
        if (value.getAsJsonPrimitive().isBoolean()) return "a boolean";
        return "a number";
    }
    public static String resourceKey(String kind) {
        return switch (kind) { case "item", "fluid", "gas" -> kind; case "aura" -> "aura-type"; case "will" -> "will-type"; default -> ""; };
    }
    public static String amountKey(String kind) { return kind.equals("energy") ? "energyPerTick" : kind.equals("compressed_air") ? "air" : "amount"; }
    public static boolean perTick(JsonObject req) {
        return kind(req).equals("energy") || req.get("type").getAsString().endsWith("_pertick") || (req.has("perTick") && req.get("perTick").getAsBoolean());
    }
    public static void perTick(JsonObject req, boolean value) {
        String kind = kind(req);
        if (kind.equals("gas") || kind.equals("compressed_air")) {
            req.addProperty("type", kind + (value ? "_pertick" : "")); req.remove("perTick");
        } else if (!kind.equals("item") && !kind.equals("energy")) { req.addProperty("type",kind); req.addProperty("perTick", value); }
    }

    public static BigDecimal number(String value) {
        try { BigDecimal result = new BigDecimal(value.trim()); if (result.precision() > 64 || Math.abs((long)result.scale()) > 64) throw new NumberFormatException(); return result; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number: " + value); }
    }
    public static void integer(JsonObject json, String key, long min, long max) {
        if (!json.has(key)) return;
        JsonElement raw = json.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be a number");
        try { BigInteger n = number(raw.getAsString()).toBigIntegerExact(); if (n.compareTo(BigInteger.valueOf(min)) < 0 || n.compareTo(BigInteger.valueOf(max)) > 0) throw new ArithmeticException(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max); }
    }

    public static MachineRecipe validate(ResourceLocation recipeId, JsonObject root) {
        read(root.toString());
        if (!"modular_machinery_reborn:machine".equals(root.get("type").getAsString())) throw new IllegalArgumentException("Wrong recipe type");
        integer(root, "recipeTime", 1, Integer.MAX_VALUE); integer(root, "max-parallelism", 1, Integer.MAX_VALUE);
        JsonArray requirements = root.getAsJsonArray("requirements");
        if (requirements != null) {
            if (requirements.size() > MAX_REQUIREMENTS) throw new IllegalArgumentException("At most 64 requirements per editor draft");
            for (JsonElement element : requirements) {
                JsonObject req = element.getAsJsonObject(); String kind = kind(req);
                if(kind.equals("item") && req.has("nbt-display") && !req.has("nbt")) throw new IllegalArgumentException("nbt-display requires nbt; use an empty {} match template if needed");
                if (List.of("item", "fluid", "gas").contains(kind)) {
                    integer(req, "amount", 1, Integer.MAX_VALUE); integer(req, "minAmount", 1, Integer.MAX_VALUE); integer(req, "maxAmount", 1, Integer.MAX_VALUE);
                }
                if (kind.equals("energy")) integer(req, "energyPerTick", 0, Long.MAX_VALUE);
                if (req.has("chance")) {
                    double chance = number(req.get("chance").getAsString()).doubleValue();
                    if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be in 0..1");
                }
                if (kind.equals("item") && "output".equals(req.get("io-type").getAsString()) && req.get("item").getAsString().startsWith("#"))
                    throw new IllegalArgumentException("Choose a concrete item for output, not a tag");
            }
        }
        return new ModRecipeSerializers.MachineSerializer().fromJson(recipeId, root);
    }

    public static String content(ResourceLocation id, JsonObject root, Format format) {
        if (format == Format.JSON) return JSON.toJson(root) + "\n";
        // Parse JSON in Java: a JS object literal would round long FE values beyond 2^53.
        return "ServerEvents.recipes(event => {\n  const MMRJson = Java.loadClass('com.google.gson.JsonParser')\n  event.custom(MMRJson.parseString("
            + new Gson().toJson(root.toString()) + ").getAsJsonObject()).id(" + new Gson().toJson(id.toString()) + ")\n})\n";
    }

    public static String unit(String kind) {
        return switch (kind) { case "item" -> "items"; case "fluid", "gas" -> "mB"; case "energy" -> "FE"; case "mana" -> "Mana"; case "aura" -> "Aura"; case "lifeessence" -> "LP"; case "will" -> "Will"; case "compressed_air" -> "mL"; case "ember" -> "Ember"; default -> "units"; };
    }

    public static List<String> summary(JsonObject root) {
        List<String> result = new ArrayList<>();
        String duration=root.has("recipeTime")?root.get("recipeTime").getAsString():"100";
        result.add("20 tick = 1 s | " + duration + " tick | max-parallelism: " + (root.has("max-parallelism") ? root.get("max-parallelism") : "machine"));
        result.add("Base values for ONE craft; chance, scripts, modifiers, triggers and parallelism may change totals.");
        if (root.has("requirements")) for (JsonElement element : root.getAsJsonArray("requirements")) {
            JsonObject req = element.getAsJsonObject(); String kind = kind(req), key = amountKey(kind), resource = resourceKey(kind);
            if(!TYPES.contains(kind)) {result.add(req.get("type").getAsString()+": advanced requirement; see source and JEI after installation.");continue;}
            JsonElement raw = req.get(key); if (raw == null && kind.equals("ember")) raw = req.get("ember");
            if (raw == null && kind.equals("compressed_air")) raw = req.get("amount");
            // The fluid parser defaults an omitted amount to 1, the same default items get; the summary must not contradict it.
            String amount = raw == null ? (kind.equals("item") || kind.equals("fluid") ? "1" : "0") : raw.getAsString();
            String total = perTick(req) && (raw != null || kind.equals("fluid")) ? " | base total " + number(amount).multiply(number(duration)).stripTrailingZeros().toPlainString() + " " + unit(kind) : "";
            if (req.has("minAmount") || req.has("maxAmount")) { amount = (req.has("minAmount") ? req.get("minAmount") : amount) + ".." + (req.has("maxAmount") ? req.get("maxAmount") : amount); total = ""; }
            result.add(req.get("io-type").getAsString() + " " + kind + " " + (req.has(resource) ? req.get(resource).getAsString() + " " : "") + amount + " " + unit(kind) + (perTick(req) ? "/tick" : "/craft") + total
                + (req.has("chance") ? " | chance " + req.get("chance") : "") + (req.has("selector-tag") ? " | selector " + req.get("selector-tag") : "") + (req.has("pressure") ? " | " + req.get("pressure") + " bar" : ""));
        }
        return result;
    }

    /** Heuristic only: custom/dynamic ports and selector visibility still require gameplay verification. */
    public static List<String> portWarnings(JsonObject root, MachineDefinition machine) {
        Set<String> warnings = new LinkedHashSet<>();
        if (!root.has("requirements")) return List.of();
        for (JsonElement entry : root.getAsJsonArray("requirements")) {
            JsonObject req = entry.getAsJsonObject(); String kind = kind(req), io = req.get("io-type").getAsString();
            String token = kind.equals("gas") ? "fluid" : kind.equals("lifeessence") ? "lifeessence" : kind;
            if (!TYPES.contains(kind)) continue;
            boolean found = machine.pattern().positions().values().stream().flatMap(Collection::stream).anyMatch(m -> m.descriptor().contains(token) && m.descriptor().contains(io));
            if (!found) warnings.add("No obvious " + kind + " " + io + " port in base pattern of " + machine.id() + "; check dynamic/custom ports.");
            if (req.has("selector-tag") && !machine.hasDynamicPatterns()) {
                String tag = req.get("selector-tag").getAsString();
                if (machine.pattern().positions().keySet().stream().noneMatch(pos -> tag.equals(machine.pattern().tag(pos)))) warnings.add("Missing base selector-tag '" + tag + "' in " + machine.id());
            }
        }
        return List.copyOf(warnings);
    }
}
