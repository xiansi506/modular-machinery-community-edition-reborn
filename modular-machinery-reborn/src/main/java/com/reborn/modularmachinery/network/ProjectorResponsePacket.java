package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.client.ClientPreview;
import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.MachinePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * C6 第一片（0.104.0）：虚影结构的应答（服务端 → 客户端）——
 * {@link ProjectorRequestPacket} 的回程。载荷是「相对控制器的位置 → 候选方块描述符列表」，
 * 每条描述符用 {@link BlockMatcher#descriptor()} 的原始字符串（{@code modid:block[prop=val]}），
 * 客户端侧 {@link BlockMatcher#parse(String)} 无损重建。
 *
 * <p>解析在公共网络线程代码里完成（{@code BlockMatcher}/{@code MachinePattern} 都是公共类），
 * 客户端类只经 {@code DistExecutor} 点名（与 {@code MePatternSyncPacket} 同一条规矩）：
 * 拿到编好的 {@link MachinePattern} 后进 {@code WorldGhostPreview.startFromRaw}。
 */
public final class ProjectorResponsePacket {

    private final BlockPos attached;
    private final Map<BlockPos, List<String>> descriptors;

    public ProjectorResponsePacket(BlockPos attached, Map<BlockPos, List<String>> descriptors) {
        this.attached = attached;
        this.descriptors = new LinkedHashMap<>(descriptors);
    }

    public ProjectorResponsePacket(FriendlyByteBuf buf) {
        this.attached = buf.readBlockPos();
        int entries = buf.readVarInt();
        Map<BlockPos, List<String>> read = new LinkedHashMap<>(Math.min(entries, 1024));
        for (int i = 0; i < entries; i++) {
            BlockPos rel = buf.readBlockPos();
            int count = buf.readVarInt();
            List<String> raw = new ArrayList<>(Math.min(count, 16));
            for (int j = 0; j < count; j++) {
                raw.add(buf.readUtf());
            }
            read.put(rel, raw);
        }
        this.descriptors = read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.attached);
        buf.writeVarInt(this.descriptors.size());
        for (Map.Entry<BlockPos, List<String>> entry : this.descriptors.entrySet()) {
            buf.writeBlockPos(entry.getKey());
            List<String> raw = entry.getValue();
            buf.writeVarInt(raw.size());
            for (String descriptor : raw) {
                buf.writeUtf(descriptor);
            }
        }
    }

    public static void handle(ProjectorResponsePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // descriptor → matcher 的解析在公共类里做，坏描述符只丢对应候选——
            // 服务端刚序列化出来的数据不该坏，这里只是不让一次编码事故踢掉连接。
            // 某个位置的候选全部无效（或原本就空）时整个位置跳过：Builder.add 明确拒绝
            // 空候选列表（JsonParseException），把空列表塞给它会让一个坏位置毁掉全部后续位置。
            MachinePattern.Builder builder = MachinePattern.builder();
            boolean any = false;
            for (Map.Entry<BlockPos, List<String>> entry : message.descriptors.entrySet()) {
                List<BlockMatcher> matchers = new ArrayList<>(entry.getValue().size());
                for (String raw : entry.getValue()) {
                    try {
                        matchers.add(BlockMatcher.parse(raw));
                        any = true;
                    } catch (Exception e) {
                        // 单条描述符坏：丢弃该候选，其余照常。
                    }
                }
                if (matchers.isEmpty()) {
                    continue;
                }
                builder.add(entry.getKey(), matchers);
            }
            if (!any) {
                return;
            }
            MachinePattern pattern = builder.build();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPreview.ghostPreviewFromServer(message.attached, pattern));
        });
        context.setPacketHandled(true);
    }

    /** 服务端入口：把编好码的结构发给请求投影的玩家。 */
    public static void sendTo(ServerPlayer player, BlockPos attached, Map<BlockPos, List<String>> descriptors) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ProjectorResponsePacket(attached, descriptors));
    }
}
