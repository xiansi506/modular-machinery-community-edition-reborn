package com.reborn.modularmachinery.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.machine.MachineDefinition;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.machine.MachineSchema;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 波 4：把服务端那一批机器定义送到客户端。
 *
 * <h2>为什么必须先有这个包</h2>
 *
 * <p>{@link MachineRegistry} 只在服务端被填充（{@code MachineLoader.apply} → {@code replace}，
 * 那是服务端的数据包加载），而客户端侧有四处在读它：JEI 的结构预览分类、JEI 的每台机器分类、
 * 升级总线界面的机器名、以及建造预览。单人游戏里这些"看起来正常"只是因为进程同时是服务端；
 * 连到专用服务器时注册表是空的，那些界面就没有内容。代码里原本也自认了这一点——
 * 「on a dedicated-server client it is empty and this category simply has nothing to show」。
 *
 * <h2>为什么传的是「定义解析用的那份 JSON」</h2>
 *
 * <p>客户端收到之后跑<strong>同一个</strong> {@link MachineSchema#read}（严格模式开，
 * 与脚本路径一致）。这样两边逐字段相同，连「某字段省略时的默认值」都必然一致，而这个项目里
 * 已经有过一次教训：同一个 schema 有两个各自演进的入口，就会给出两份不同的定义。
 *
 * <p>另一条路是给 {@code MachineDefinition} 的十五个字段各写一套线上格式。那条路要额外处理
 * {@code BlockMatcher}（它持有<strong>活的 {@code Block} 引用</strong>）、{@code MachineModifier}、
 * {@code SmartInterfaceType}、{@code CoreThreadSpec} 与 {@code DynamicPattern}——五套新格式，
 * 每一套都是一份迟早会与解析器分叉的副本。
 *
 * <h2>线上格式</h2>
 *
 * <pre>
 *   int    count
 *   count × { utf id, utf json }
 * </pre>
 *
 * <p>与 {@code SelectionSyncPacket} 同一条纪律：<strong>计数是外部数据，先夹住再拿去预分配</strong>，
 * 否则一个伪造的计数就能让客户端按它的大小申请内存。
 */
public final class MachineDefinitionSyncPacket {

    /** 一次消息最多宣布多少台机器，仅用于预分配——真实的包不会接近这个数。 */
    private static final int INITIAL_CAPACITY_CAP = 1024;

    /** 单份定义的 JSON 长度上限。超长的定义是损坏或恶意的，宁可整包拒绝也不要吃掉内存。 */
    private static final int MAX_JSON_LENGTH = 1 << 20;
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;

    private final Map<ResourceLocation, String> definitions;

    public MachineDefinitionSyncPacket(Map<ResourceLocation, String> definitions) {
        validateSnapshot(definitions);
        this.definitions = Map.copyOf(definitions);
    }

    public MachineDefinitionSyncPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_PACKET_BYTES / 4) throw new io.netty.handler.codec.DecoderException("Invalid machine definition count");
        int start = buf.readerIndex();
        Map<ResourceLocation, String> read = new LinkedHashMap<>(
                Math.max(0, Math.min(count, INITIAL_CAPACITY_CAP)));
        for (int i = 0; i < count; i++) {
            ResourceLocation id = ResourceLocation.tryParse(buf.readUtf());
            String json = buf.readUtf(MAX_JSON_LENGTH);
            if (id == null) {
                // 一个读不出来的 id 意味着这一项的其余内容丢失了，剩下的字节无法再解释——
                // 所以整包放弃，而不是猜着继续读。
                throw new io.netty.handler.codec.DecoderException(
                        "Machine definition sync carried an unreadable id at entry " + i);
            }
            read.put(id, json);
            if (buf.readerIndex() - start + 4 > MAX_PACKET_BYTES)
                throw new io.netty.handler.codec.DecoderException("Machine definition snapshot exceeds 2MiB");
        }
        this.definitions = Map.copyOf(read);
    }

    public Map<ResourceLocation, String> definitions() {
        return this.definitions;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.definitions.size());
        for (Map.Entry<ResourceLocation, String> entry : this.definitions.entrySet()) {
            buf.writeUtf(entry.getKey().toString());
            buf.writeUtf(entry.getValue(), MAX_JSON_LENGTH);
        }
    }

    public static void handle(MachineDefinitionSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 客户端专用，走 DistExecutor，这样任何 common 类都不会直接提到客户端状态。
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.reborn.modularmachinery.client.ClientMachines.accept(message.definitions())));
        context.setPacketHandled(true);
    }

    /**
     * 把当前注册表里的定义与它们的源 JSON 打成一个包发出去。
     *
     * <p>直接读 {@link MachineRegistry#sources()}，而不是再去遍历文件：{@code reload} 之后注册表里
     * 就是**权威**的一份，而文件系统可能已经变了。
     *
     * <p>没有源 JSON 的定义（直接构造的那种，例如离线复现器造的）会被<strong>跳过并计数</strong>，
     * 然后记一条日志。它不能假装成功：客户端少一台机器与客户端有一台错误的机器是两回事，
     * 而后者才是真正难查的。
     */
    public static Map<ResourceLocation, String> snapshot() {
        return snapshot(MachineRegistry.all(), MachineRegistry.sources());
    }

    /** Also used to validate a complete candidate before the loader publishes it. */
    public static Map<ResourceLocation, String> snapshot(java.util.Collection<MachineDefinition> definitions,
                                                        Map<ResourceLocation, JsonElement> sources) {
        Map<ResourceLocation, String> out = new LinkedHashMap<>();
        for (MachineDefinition definition : definitions) {
            com.google.gson.JsonObject adopted = definition.synchronizationSource();
            JsonElement raw = sources.get(definition.id());
            if (adopted == null && raw != null) adopted = MachineSchema.synchronizationSource(raw.getAsJsonObject(), Map.of(), definition);
            if (adopted != null) out.put(definition.id(), adopted.toString());
        }
        int missing = definitions.size() - out.size();
        if (missing > 0) {
            ModularMachineryReborn.LOGGER.warn("[{}] {} machine definition(s) have no source JSON and cannot be "
                            + "sent to clients; they will be missing there. Definitions constructed directly "
                            + "(rather than parsed from JSON) cannot be synchronised.",
                    ModularMachineryReborn.MOD_ID, missing);
        }
        validateSnapshot(out);
        return out;
    }

    private static void validateSnapshot(Map<ResourceLocation, String> definitions) {
        long bytes = 4;
        for (var entry : definitions.entrySet()) {
            if (entry.getValue().length() > MAX_JSON_LENGTH)
                throw new IllegalArgumentException(entry.getKey() + " synchronization JSON exceeds 1MiB characters");
            // Include conservative VarInt prefixes for both UTF fields, not only the JSON payload.
            bytes += 10L + entry.getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > MAX_PACKET_BYTES) throw new IllegalArgumentException("Machine definition snapshot exceeds 2MiB");
        }
    }

    /** 解析一份定义 JSON；解析失败时返回 {@code null} 并把原因记下来（一份坏定义不该让整次同步失败）。 */
    @javax.annotation.Nullable
    public static MachineDefinition parse(ResourceLocation id, String json, boolean strict) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return MachineSchema.read(element, "synced '" + id + "'", Map.of(), strict);
        } catch (Exception exception) {
            ModularMachineryReborn.LOGGER.error("[{}] Failed to read the synced machine definition {}",
                    ModularMachineryReborn.MOD_ID, id, exception);
            return null;
        }
    }

    /**
     * 客户端重建时该用的解析模式——<strong>与服务端加载时完全相同</strong>，也就是宽容模式。
     *
     * <p>这个常量的存在是因为我第一版在这里写错了，而写错的方式值得记下来：
     * 我当时用了 {@code strict = true}，理由是「客户端应该严格一点」。但 {@code strict} 在这套
     * schema 里只控制<strong>一件事</strong>——是否拒绝<strong>未知根字段</strong>——而
     * {@code MachineLoader.readMachine} 走的是三参数的 {@code read}，也就是
     * {@code strict = false}（宽容）。
     *
     * <p>于是：一份含未知根字段的定义（例如作者按更新的格式写的、或只是打错了一个字段名）
     * 在服务端<strong>加载成功</strong>，到了客户端<strong>被拒</strong>——客户端静默少一台机器。
     * 那正是本项目最怕的一类缺陷：没有报错、两边不一致、而症状看起来像「这台机器在联机时不工作」。
     *
     * <p>正确的做法是<strong>忠实重建服务端的那一份</strong>，而不是拿更严的规则重新审一遍：
     * 服务端已经用它的规则接受过这份定义了。客户端的职责是复现，不是二次把关。
     */
    public static final boolean CLIENT_PARSE_STRICT = false;

    /** 客户端重建一份同步来的定义。 */
    @javax.annotation.Nullable
    public static MachineDefinition parseForClient(ResourceLocation id, String json) {
        return parse(id, json, CLIENT_PARSE_STRICT);
    }
}
