package com.reborn.modularmachinery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One item requirement's {@code nbt} block, parsed with the original's matching rules.
 *
 * <p>The original parsed this JSON with its own {@code NBTJsonDeserializer} and compared the result with
 * {@code NBTMatchingHelper} (both in {@code hellfirepvp.modularmachinery.common.util.nbt}). The comparison is
 * <b>not</b> plain NBT equality: it is a template match with the following properties, each kept here because a
 * pack that relied on it would otherwise silently stop matching:
 *
 * <ul>
 *   <li>A compound is a <b>subset</b>: every key written in the requirement must exist on the item, with the
 *       required value; extra keys on the item are ignored.</li>
 *   <li>A list is a <b>multiset</b>: every required element must find a different element of the item's list,
 *       in any order. An empty required list would match any list, but the original's parser rejects
 *       {@code []} outright, and a missing list fails at the parent compound's key lookup.</li>
 *   <li>A string is a <b>regular expression</b>, matched against the whole string, case-insensitively.</li>
 *   <li>A number is a <b>same-type</b> comparison: {@code "5"} only matches an {@code IntTag}, never a
 *       {@code LongTag} or a {@code StringTag}. A leading {@code <=}, {@code <}, {@code ==}, {@code >=} or
 *       {@code >} turns it into a range; the number written is the requirement's value, so {@code "<=5"} means
 *       the item's value is at most 5.</li>
 *   <li>{@code "true"}/{@code "false"} become a byte tag 1/0, as the original's parser did.</li>
 * </ul>
 *
 * <h2>JSON types</h2>
 *
 * <p>The original's parser read every scalar with {@code readQuotedString}, so a value had to be a JSON
 * <b>string</b>: {@code {"energy": ">=1000"}} worked, {@code {"energy": 1000}} did not. That rule is enforced
 * here too, with the original's message shape. A list's elements must all have the same NBT type, exactly as
 * an NBT list does; the original rejected a mixed list at parse time.
 *
 * <h2>Writing a tag</h2>
 *
 * <p>For an <b>output</b>, the original attached the parsed tag to the produced stack
 * ({@code RequirementItem.java:496-498}) and for {@code ingredient_array_input} to each output entry
 * ({@code RequirementIngredientArray.java:304-315}). A comparison is not a concrete value, so writing one out
 * uses the number that was written and the pattern's source text — the same materialisation the original's
 * subclasses produced, because {@code NBTComparableInteger} is an {@code NBTTagInt} and
 * {@code NBTPatternString} is an {@code NBTTagString}. {@link #toTag()} does that; it is only used for outputs
 * and previews, never for matching.
 */
public final class NbtPredicate {

    // The original's regexes, copied verbatim (NBTJsonDeserializer.java:28-34), including the requirement that a
    // fractional number has a decimal point before an exponent: "1e5" is not a number there, it is a pattern.
    private static final Pattern DOUBLE_NOSUFFIX =
            Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE =
            Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT =
            Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYTE =
            Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG =
            Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT =
            Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT =
            Pattern.compile("[-+]?(?:0|[1-9][0-9]*)", Pattern.CASE_INSENSITIVE);

    private final CompoundNode root;
    private final String source;

    private NbtPredicate(CompoundNode root, String source) {
        this.root = root;
        this.source = source;
    }

    /**
     * Parses one {@code nbt} object. {@code where} is the recipe location used in errors, the same object the
     * requirement reader already carries.
     */
    public static NbtPredicate parse(JsonElement json, Object where) {
        if (LiteralNbt.isEncoded(json)) {
            return new NbtPredicate(literalCompound(LiteralNbt.decode(json, where)), json.toString());
        }
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException("The ComponentType 'nbt' expects a json compound that defines the NBT "
                    + "tag! (in " + where + ")");
        }
        JsonObject object = json.getAsJsonObject();
        Node parsed = parseNode(object, where, "nbt");
        return new NbtPredicate((CompoundNode) parsed, object.toString());
    }

    /** Concrete display data: strings are literal, so JSON text components and texture URLs are valid. */
    public static CompoundTag previewTag(JsonElement json, Object where) {
        if (LiteralNbt.isEncoded(json)) return LiteralNbt.decode(json, where);
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException("The ComponentType 'preview-nbt' expects a json compound! (in " + where + ")");
        }
        return (CompoundTag) parseNode(json, where, "preview-nbt", true).toTag();
    }

    /** Parses the text form, used by the network reader and by tests. */
    public static NbtPredicate parse(String json, Object where) {
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new JsonParseException("The 'nbt' block in " + where + " is not valid JSON: " + e.getMessage(), e);
        }
        return parse(element, where);
    }

    /** The canonical JSON text this predicate was parsed from, for the network. */
    public String source() {
        return this.source;
    }

    /** Whether this predicate asks for nothing at all ({@code {}}). */
    public boolean isEmpty() {
        return this.root.children.isEmpty();
    }

    /**
     * Whether an item's NBT matches. {@code null} means the item has no tag at all: only an empty template
     * ({@code {}}) matches that, which is the original's {@code NBTMatchingHelper:37-39}.
     */
    public boolean matches(@Nullable CompoundTag itemTag) {
        if (itemTag == null) {
            return this.root.children.isEmpty();
        }
        return this.root.matches(itemTag);
    }

    /**
     * The concrete tag an output or a preview carries. Comparison modes are not NBT, so they are dropped and
     * the written number is used; a string pattern is written as its source text.
     */
    public CompoundTag toTag() {
        return (CompoundTag) this.root.toTag();
    }

    /**
     * F2（0.86.0，{@code /mm-hand}）：把物品的**实际** NBT 反写成本方言的模板 JSON，值全部是字符串
     * （解析端只读字符串），数字带类型后缀（{@code 1b}/{@code 1s}/{@code 1}/{@code 1l}/{@code 1.0f}/{@code 1.0d}），
     * 这样打印出来的整块可以粘回配方的 {@code nbt} 里去匹配同一个物品。
     *
     * <p><b>回环的边界</b>（粘回去之前要自己看一眼的）：字符串在匹配端是**正则**，带正则元字符
     * （{@code . ( [ } 等）的字面量粘回去不一定还是字面量；{@code NaN}/{@code Infinity} 的浮点、
     * int/long 数组这几种方言不认识，按原样文本写出（写出后解析端会当成字符串正则，匹配不上原值）。
     * 这是展示用途的最好努力，不是序列化承诺——与旧版 {@code NBTJsonSerializer} 的地位相同。
     */
    public static JsonElement tagToTemplateJson(Tag tag) {
        if (tag instanceof StringTag string) {
            return new JsonPrimitive(string.getAsString());
        }
        if (tag instanceof ByteTag byteTag) {
            return new JsonPrimitive(byteTag.getAsByte() + "b");
        }
        if (tag instanceof ShortTag shortTag) {
            return new JsonPrimitive(shortTag.getAsShort() + "s");
        }
        if (tag instanceof IntTag intTag) {
            return new JsonPrimitive(String.valueOf(intTag.getAsInt()));
        }
        if (tag instanceof LongTag longTag) {
            return new JsonPrimitive(longTag.getAsLong() + "l");
        }
        if (tag instanceof FloatTag floatTag) {
            return new JsonPrimitive(floatTag.getAsFloat() + "f");
        }
        if (tag instanceof DoubleTag doubleTag) {
            return new JsonPrimitive(doubleTag.getAsDouble() + "d");
        }
        if (tag instanceof ListTag list) {
            JsonArray array = new JsonArray(list.size());
            for (Tag element : list) {
                array.add(tagToTemplateJson(element));
            }
            return array;
        }
        if (tag instanceof CompoundTag compound) {
            JsonObject object = new JsonObject();
            for (String key : compound.getAllKeys()) {
                object.add(key, tagToTemplateJson(compound.get(key)));
            }
            return object;
        }
        return new JsonPrimitive(String.valueOf(tag));
    }

    private static CompoundNode literalCompound(CompoundTag tag) {
        Map<String, Node> children = new LinkedHashMap<>();
        tag.getAllKeys().stream().sorted().forEach(key -> {
            Tag value = tag.get(key);
            children.put(key, value instanceof CompoundTag compound
                    ? literalCompound(compound) : new LiteralNode(value));
        });
        return new CompoundNode(children);
    }

    /** Literal snapshots keep scalar types, list order, empty containers and typed arrays. */
    private static final class LiteralNode implements Node {
        private final Tag value;
        private LiteralNode(Tag value) { this.value = value.copy(); }
        @Override public byte tagType() { return value.getId(); }
        @Override public boolean matches(Tag actual) { return value.equals(actual); }
        @Override public Tag toTag() { return value.copy(); }
    }

    // ------------------------------------------------------------------ nodes

    private interface Node {
        byte tagType();

        boolean matches(Tag actual);

        Tag toTag();
    }

    private static final class CompoundNode implements Node {
        private final Map<String, Node> children;

        private CompoundNode(Map<String, Node> children) {
            this.children = children;
        }

        @Override
        public byte tagType() {
            return Tag.TAG_COMPOUND;
        }

        @Override
        public boolean matches(Tag actual) {
            if (!(actual instanceof CompoundTag compound)) {
                return false;
            }
            for (Map.Entry<String, Node> entry : this.children.entrySet()) {
                Tag found = compound.get(entry.getKey());
                if (found == null || !entry.getValue().matches(found)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Tag toTag() {
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<String, Node> entry : this.children.entrySet()) {
                tag.put(entry.getKey(), entry.getValue().toTag());
            }
            return tag;
        }
    }

    private static final class ListNode implements Node {
        private final List<Node> elements;
        private final byte elementType;

        private ListNode(List<Node> elements) {
            this.elements = elements;
            this.elementType = elements.isEmpty() ? Tag.TAG_END : elements.get(0).tagType();
        }

        @Override
        public byte tagType() {
            return Tag.TAG_LIST;
        }

        @Override
        public boolean matches(Tag actual) {
            if (!(actual instanceof ListTag list)) {
                return false;
            }
            if (this.elements.isEmpty()) {
                // The original's parser never produced an empty list; if one is constructed programmatically,
                // matching any list is the original's matchList behaviour (:82-84).
                return true;
            }
            if (list.isEmpty() || list.getElementType() != this.elementType) {
                return false;
            }
            if (this.elements.size() > list.size()) return false;
            boolean[][] edges = new boolean[this.elements.size()][list.size()];
            for (int i = 0; i < this.elements.size(); i++) {
                for (int j = 0; j < list.size(); j++) edges[i][j] = this.elements.get(i).matches(list.get(j));
            }
            int[] owner = new int[list.size()], matched = new int[this.elements.size()];
            java.util.Arrays.fill(owner, -1);
            java.util.Arrays.fill(matched, -1);
            for (int i = 0; i < this.elements.size(); i++) {
                if (!augment(edges, i, owner, matched)) return false;
            }
            return true;
        }

        /** Iterative augmenting paths find a full one-to-one allocation, without permutations or recursion. */
        private static boolean augment(boolean[][] edges, int root, int[] owner, int[] matched) {
            int[] via = new int[owner.length], queue = new int[matched.length];
            java.util.Arrays.fill(via, -1);
            int head = 0, tail = 0;
            queue[tail++] = root;
            while (head < tail) {
                int expected = queue[head++];
                for (int actual = 0; actual < owner.length; actual++) {
                    if (!edges[expected][actual] || via[actual] != -1) continue;
                    via[actual] = expected;
                    if (owner[actual] != -1) {
                        queue[tail++] = owner[actual];
                        continue;
                    }
                    for (int at = actual; at != -1;) {
                        int nextExpected = via[at];
                        int previous = matched[nextExpected];
                        matched[nextExpected] = at;
                        owner[at] = nextExpected;
                        at = previous;
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public Tag toTag() {
            ListTag tag = new ListTag();
            for (Node element : this.elements) {
                tag.add(element.toTag());
            }
            return tag;
        }
    }

    private enum NumberKind {
        BYTE(Tag.TAG_BYTE),
        SHORT(Tag.TAG_SHORT),
        INT(Tag.TAG_INT),
        LONG(Tag.TAG_LONG),
        FLOAT(Tag.TAG_FLOAT),
        DOUBLE(Tag.TAG_DOUBLE);

        private final byte tagType;

        NumberKind(byte tagType) {
            this.tagType = tagType;
        }
    }

    private enum ComparisonMode {
        LESS_EQUAL("<="),
        EQUAL("=="),
        GREATER_EQUAL(">="),
        LESS("<"),
        GREATER(">");

        private final String identifier;

        ComparisonMode(String identifier) {
            this.identifier = identifier;
        }

        private boolean testByte(byte original, byte toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }

        private boolean testShort(short original, short toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }

        private boolean testInt(int original, int toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }

        private boolean testLong(long original, long toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }

        private boolean testFloat(float original, float toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }

        private boolean testDouble(double original, double toTest) {
            return switch (this) {
                case LESS -> toTest < original;
                case LESS_EQUAL -> toTest <= original;
                case EQUAL -> toTest == original;
                case GREATER_EQUAL -> toTest >= original;
                case GREATER -> toTest > original;
            };
        }
    }

    private static final class NumberNode implements Node {
        private final NumberKind kind;
        private final ComparisonMode mode;
        private final Number number;

        private NumberNode(NumberKind kind, ComparisonMode mode, Number number) {
            this.kind = kind;
            this.mode = mode;
            this.number = number;
        }

        @Override
        public byte tagType() {
            return this.kind.tagType;
        }

        @Override
        public boolean matches(Tag actual) {
            if (actual.getId() != this.kind.tagType) {
                return false;
            }
            return switch (this.kind) {
                case BYTE -> this.mode.testByte(this.number.byteValue(), ((ByteTag) actual).getAsByte());
                case SHORT -> this.mode.testShort(this.number.shortValue(), ((ShortTag) actual).getAsShort());
                case INT -> this.mode.testInt(this.number.intValue(), ((IntTag) actual).getAsInt());
                case LONG -> this.mode.testLong(this.number.longValue(), ((LongTag) actual).getAsLong());
                case FLOAT -> this.mode.testFloat(this.number.floatValue(), ((FloatTag) actual).getAsFloat());
                case DOUBLE -> this.mode.testDouble(this.number.doubleValue(), ((DoubleTag) actual).getAsDouble());
            };
        }

        @Override
        public Tag toTag() {
            return switch (this.kind) {
                case BYTE -> ByteTag.valueOf(this.number.byteValue());
                case SHORT -> ShortTag.valueOf(this.number.shortValue());
                case INT -> IntTag.valueOf(this.number.intValue());
                case LONG -> LongTag.valueOf(this.number.longValue());
                case FLOAT -> FloatTag.valueOf(this.number.floatValue());
                case DOUBLE -> DoubleTag.valueOf(this.number.doubleValue());
            };
        }
    }

    private static final class StringNode implements Node {
        private final String source;
        private final Pattern pattern;

        private StringNode(String source) {
            this(source, false);
        }

        private StringNode(String source, boolean literal) {
            this.source = source;
            this.pattern = Pattern.compile(literal ? Pattern.quote(source) : source, Pattern.CASE_INSENSITIVE);
        }

        @Override
        public byte tagType() {
            return Tag.TAG_STRING;
        }

        @Override
        public boolean matches(Tag actual) {
            return actual instanceof StringTag string && this.pattern.matcher(string.getAsString()).matches();
        }

        @Override
        public Tag toTag() {
            return StringTag.valueOf(this.source);
        }
    }

    /** A literal byte, the original's {@code "true"}/{@code "false"}. */
    private static final class ExactByteNode implements Node {
        private final byte value;

        private ExactByteNode(byte value) {
            this.value = value;
        }

        @Override
        public byte tagType() {
            return Tag.TAG_BYTE;
        }

        @Override
        public boolean matches(Tag actual) {
            return actual instanceof ByteTag byteTag && byteTag.getAsByte() == this.value;
        }

        @Override
        public Tag toTag() {
            return ByteTag.valueOf(this.value);
        }
    }

    // ------------------------------------------------------------------ parsing

    private static Node parseNode(JsonElement json, Object where, String path) {
        return parseNode(json, where, path, false);
    }

    private static Node parseNode(JsonElement json, Object where, String path, boolean concrete) {
        if (json.isJsonObject()) {
            Map<String, Node> children = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (key.isEmpty()) {
                    throw new JsonParseException("An NBT key in " + where + " at " + path
                            + " is empty. The original rejected an empty key (Expected non-empty key).");
                }
                children.put(key, parseNode(entry.getValue(), where, path + "." + key, concrete));
            }
            return new CompoundNode(children);
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.isEmpty() && !concrete) {
                throw new JsonParseException("The NBT list at " + path + " in " + where + " is empty. The "
                        + "original's parser rejected an empty list (Expected value, NBTJsonDeserializer:211-245); "
                        + "remove the key rather than writing [].");
            }
            List<Node> elements = new ArrayList<>(array.size());
            byte type = -1;
            for (int index = 0; index < array.size(); index++) {
                Node element = parseNode(array.get(index), where, path + "[" + index + "]", concrete);
                if (type == -1) {
                    type = element.tagType();
                } else if (element.tagType() != type) {
                    throw new JsonParseException("The NBT list at " + path + " in " + where + " mixes "
                            + typeName(type) + " and " + typeName(element.tagType()) + ". An NBT list has one "
                            + "element type; the original rejected this (Unable to insert "
                            + typeName(element.tagType()) + " into ListTag of type " + typeName(type) + ").");
                }
                elements.add(element);
            }
            return new ListNode(elements);
        }
        if (json.isJsonPrimitive() && (concrete || json.getAsJsonPrimitive().isString())) {
            return parseScalar(json.getAsString(), where, path, concrete);
        }
        throw new JsonParseException("The NBT value at " + path + " in " + where + " must be written as a "
                + "quoted string. The original read every scalar with a quoted string, so write \"5\", "
                + "\">=1000\" or \"Foo.*\", not a bare number or boolean.");
    }

    private static Node parseScalar(String raw, Object where, String path, boolean concrete) {
        if (raw.isEmpty()) {
            if (concrete) return new StringNode("", true);
            // NBTJsonDeserializer#readTypedValue rejected an empty quoted string (:112-118).
            throw new JsonParseException("The NBT value at " + path + " in " + where + " is empty. The "
                    + "original rejected an empty quoted value (Expected value).");
        }
        ComparisonMode mode = peekMode(raw);
        String body = mode == null ? raw : raw.substring(mode.identifier.length());
        ComparisonMode effective = mode == null ? ComparisonMode.EQUAL : mode;
        try {
            if (FLOAT.matcher(body).matches()) {
                return new NumberNode(NumberKind.FLOAT, effective,
                        Float.parseFloat(body.substring(0, body.length() - 1)));
            }
            if (BYTE.matcher(body).matches()) {
                return new NumberNode(NumberKind.BYTE, effective,
                        Byte.parseByte(body.substring(0, body.length() - 1)));
            }
            if (LONG.matcher(body).matches()) {
                return new NumberNode(NumberKind.LONG, effective,
                        Long.parseLong(body.substring(0, body.length() - 1)));
            }
            if (SHORT.matcher(body).matches()) {
                return new NumberNode(NumberKind.SHORT, effective,
                        Short.parseShort(body.substring(0, body.length() - 1)));
            }
            if (INT.matcher(body).matches()) {
                return new NumberNode(NumberKind.INT, effective, Integer.parseInt(body));
            }
            if (DOUBLE.matcher(body).matches()) {
                return new NumberNode(NumberKind.DOUBLE, effective,
                        Double.parseDouble(body.substring(0, body.length() - 1)));
            }
            if (DOUBLE_NOSUFFIX.matcher(body).matches()) {
                return new NumberNode(NumberKind.DOUBLE, effective, Double.parseDouble(body));
            }
            if ("true".equalsIgnoreCase(body)) {
                return new ExactByteNode((byte) 1);
            }
            if ("false".equalsIgnoreCase(body)) {
                return new ExactByteNode((byte) 0);
            }
        } catch (NumberFormatException ignored) {
            // The original swallowed this too and fell through to a pattern (NBTJsonDeserializer:154-155).
        }

        // The original put the comparison prefix back when the rest turned out not to be a number
        // (NBTJsonDeserializer:157-159), so "<abc" is a pattern that includes the '<'.
        String source = mode == null ? body : mode.identifier + body;
        try {
            return new StringNode(source, concrete);
        } catch (PatternSyntaxException e) {
            throw new JsonParseException("The NBT pattern \"" + source + "\" at " + path + " in " + where
                    + " is not a valid regular expression: " + e.getDescription(), e);
        }
    }

    /**
     * The original's {@code ComparisonMode.peekMode}, with the length guard it lacked: a one-character value
     * used to throw {@code StringIndexOutOfBoundsException} before it could be treated as a pattern
     * (NBTJsonDeserializer called it on every scalar).
     */
    @Nullable
    private static ComparisonMode peekMode(String value) {
        for (ComparisonMode mode : ComparisonMode.values()) {
            if (value.length() >= mode.identifier.length() && value.startsWith(mode.identifier)) {
                return mode;
            }
        }
        return null;
    }

    private static String typeName(byte tagType) {
        try {
            return TagTypes.getType(tagType).getName().toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "TAG_" + tagType;
        }
    }
}
