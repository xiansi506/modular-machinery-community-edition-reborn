package com.reborn.modularmachinery.client.preview;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reborn.modularmachinery.machine.BlockMatcher;
import com.reborn.modularmachinery.machine.MachinePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.Map;

/**
 * C6 第一片（0.104.0）：机器投影仪的世界幽灵预览。
 *
 * <p>对已选择机器的控制器使用投影仪后，按旧版 {@code BlockArrayPreviewRenderHelper}（367 行）的机制
 * 在世界里逐层画出「还缺什么」：
 *
 * <ul>
 *   <li><b>逐层投影</b>（旧版 {@code updateLayers :335-348} / {@code tick :130-149}）：一次只画
 *       最低的还没放完整的那一层；当前层补齐后自动推进到上一层；整机全部就位后虚影自动消失。</li>
 *   <li><b>跳过已匹配的块</b>（旧版 {@code batchBlocks :257-260}）：每层里已经放对的方块不画，
 *       画的是该位置 {@link BlockMatcher#representativeState()} 的代表方块。</li>
 *   <li><b>0.125 偏移 + 0.75 缩放</b>（旧版 {@code :270-272}）：虚影略小于真实方块，一眼区分
 *       「引导」与「现实」。</li>
 *   <li><b>反色混合 + 无深度测试</b>（旧版 {@code :179-181}：{@code disableDepth} +
 *       {@code blendFunc(GL_ONE_MINUS_DST_COLOR, GL_DST_COLOR)}）：1.20.1 里对应一个自定义
 *       {@link RenderType}（见 {@link GhostShards}）。</li>
 *   <li><b>32 格距离自动清除</b>（旧版 {@code :132-135}，{@code distanceSq >= 1024}）。</li>
 * </ul>
 *
 * <p><b>结构从服务端来</b>（{@code startFromRaw}，经 {@code ProjectorResponsePacket}）：虚影要按
 * 控制器定格的节数与朝向画，而 {@code rotationSteps}/{@code dynamicPatterns} 只在服务端结构
 * 检查里存在，不在方块实体的同步 tag 里——旧版纯客户端读注册表的做法在直推架构下不成立，
 * 结构组装（{@code MachineStructures.ghostStructure}）整个放在服务端。所以本类拿到的
 * pattern 已经是旋转后的绝对相对坐标，客户端不做任何组装。
 *
 * <p>与 {@code SelectionHighlight} 同样的注册纪律：全部挂在 Forge 总线（由 {@code ClientSetup} 注册），
 * 本类只被客户端代码加载。所引用的渲染 API（{@code renderSingleBlock} 七参重载、
 * {@code RenderType.create} 公开构造、{@code RenderStateShard} 的受保护 shard 常量、
 * {@code TransparencyStateShard} 公开构造）已逐一用 javap 对着 1.20.1 mapped jar 验过。
 *
 * <p><b>未实机验证项</b>（列入交接文档 0.104.0 清单）：反色混合在新渲染管线的观感、
 * 虚影深度穿透（NO_DEPTH_TEST 让虚影隔墙可见——旧版同款，但观感要实机确认）、
 * 层推进与清除的时序、大型机器（万块级）的请求/应答延迟。
 */
@OnlyIn(Dist.CLIENT)
public final class WorldGhostPreview {

    /** The original's 1024 squared blocks: {@code BlockArrayPreviewRenderHelper :132-133}. */
    private static final double RANGE_SQUARED = 1024.0D;

    /** 编好码的结构：相对控制器坐标，已按控制器朝向旋转，含 modifier 候选。 */
    private static MachinePattern pattern;
    /** 附着的控制器世界坐标（虚影基准点）。 */
    private static BlockPos attached;
    /** 当前渲染的层（相对控制器的 Y 偏移）；NO_LAYER 表示没有待补的层，负 Y 也是有效层。 */
    private static final int NO_LAYER = Integer.MIN_VALUE;
    private static int renderedLayer = NO_LAYER;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;

    /**
     * 幽灵渲染类型——构造与 shard 常量访问全部住在 {@link GhostShards} 里
     * （protected static 成员只允许在声明类的子类体内访问，见其说明）。
     */
    private static final RenderType GHOST_TYPE = GhostShards.GHOST_TYPE;

    /**
     * {@code RenderStateShard} 的那些常量是 {@code protected static}——JLS 6.6.2 只允许在
     * 声明类的子类体内摸到它们，空壳子类是 Forge modding 的惯例通道。所以幽灵 RenderType
     * 的整个构造表达式都放在这里，外部只引用本类的 {@code GHOST_TYPE} 产物。1.20.1 的
     * {@code RenderStateShard(String, Runnable, Runnable)} 与 {@code TransparencyStateShard}
     * 构造都是 public（javap 已验）。反色混合 shard 也住在这里。
     */
    private static final class GhostShards extends RenderStateShard {

        /** 旧版 {@code blendFunc(GL_ONE_MINUS_DST_COLOR, GL_DST_COLOR)}（:181）的 1.20.1 对应物。 */
        static final TransparencyStateShard GHOST_REVERSE_BLEND = new TransparencyStateShard(
                "modular_machinery_reborn_ghost_reverse_blend",
                () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                            GlStateManager.DestFactor.DST_COLOR);
                },
                () -> {
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableBlend();
                });

        /**
         * 幽灵渲染类型。旧版把混合/深度状态直接压在 GL 状态机上；1.20.1 的对应物是一个
         * Composite RenderType。与普通半透明的差别在混合因子：{@code ONE_MINUS_DST_COLOR ×
         * src + DST_COLOR × dst}——已绘制的世界颜色参与源项，落点是「反转叠加」的引导感，
         * 而不是均匀半透明。深度测试关掉（旧版 {@code disableDepth}），颜色写、深度不写。
         */
        static final RenderType GHOST_TYPE = RenderType.create(
                "modular_machinery_reborn_ghost",
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                262144,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                        .setTextureState(BLOCK_SHEET)
                        .setTransparencyState(GHOST_REVERSE_BLEND)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private GhostShards() {
            super("modular_machinery_reborn_ghost_shards", () -> { }, () -> { });
        }
    }

    private WorldGhostPreview() {
    }

    /** 投影是否已在某个控制器上激活（投影仪用它判断「再次使用=取消」）。 */
    public static boolean isActiveAt(BlockPos pos) {
        return pattern != null && attached != null && attached.equals(pos);
    }

    /**
     * 从服务端编好码的结构开始投影（{@code ClientPreview.ghostPreviewFromServer} 落点）。
     * pattern 已含动态段与 modifier 候选、已按控制器朝向旋转——客户端只置位与找第一层。
     */
    public static boolean startFromRaw(BlockPos attachedPos, MachinePattern structure) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || attachedPos == null || structure == null || structure.positions().isEmpty()) {
            return false;
        }
        pattern = structure;
        rebuildStructuredLayers();
        attached = attachedPos.immutable();
        renderedLayer = NO_LAYER;
        dimension = level.dimension();
        updateLayers(level);
        return true;
    }

    /** 取消投影（走远 32 格、断线、再次对同一控制器使用投影仪、整机建成都会走到这里）。 */
    public static void clear() {
        pattern = null;
        structuredLayers = List.of();
        attached = null;
        renderedLayer = NO_LAYER;
        dimension = null;
    }

    /** 断线清场——别的服务器的投影不该跟着玩家过来（同 {@code SelectionHighlight} 的纪律）。 */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /**
     * 层推进与清除，每 client tick 一次，逻辑逐条对照旧版 {@code tick :130-149}：
     * 下面的层被拆了就回退重画（{@code hasLowerLayer && !match(layer-1)}）；当前层补齐后
     * 检查整机（全齐→清除，否则推进下一层）。
     */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (pattern == null || attached == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !level.dimension().equals(dimension)) {
            clear();
            return;
        }
        if (minecraft.player.blockPosition().distSqr(attached) >= RANGE_SQUARED) {
            clear();
            return;
        }
        if (renderedLayer == NO_LAYER) {
            if (pattern.matches(level, attached)) clear();
            else updateLayers(level);
            return;
        }
        // P10-17（0.121.37）：回退检查不再只看紧邻下一层——旧逻辑 `layerMatches(renderedLayer - 1)`
        // 对"Y=0 与 Y=2 之间隔空层"和"连续三层拆了最底层"都会漏（下层被拆但 renderedLayer-1 仍完整）。
        // 现在按**实际有方块的层**（结构索引，start/clear/换结构时重建）从低到高找第一个未完成的层：
        // 它比当前层低就重扫（回退到最低未完成层），比当前层高说明当前层已补齐（旧逻辑同效）；
        // 等于当前层则保持投影。层枚举只走结构里真实存在的 y（不按 min..max 逐整数枚举坐标），
        // 每层的完整性检查复用 {@link #layerMatches} 的既有语义。
        int lowest = lowestIncompleteLayer(level);
        if (lowest == NO_LAYER) {
            if (pattern.matches(level, attached)) clear();
            else updateLayers(level);
        } else if (lowest != renderedLayer) {
            updateLayers(level);
        }
    }

    /**
     * 世界渲染：在半透明层之后画当前待补层里尚未就位的方块。
     * 相机相对平移与 {@code SelectionHighlight} 同理——阶段给的 pose 就在那个坐标系里；
     * 0.125 偏移 + 0.75 缩放是旧版 {@code :270-272} 的原样搬移。
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (pattern == null || attached == null || renderedLayer == NO_LAYER) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : pattern.positions().entrySet()) {
            BlockPos rel = entry.getKey();
            if (rel.getY() != renderedLayer) {
                continue;
            }
            List<BlockMatcher> matchers = entry.getValue();
            if (matchers.isEmpty()) {
                continue;
            }
            BlockPos worldPos = attached.offset(rel);
            if (matchesAny(matchers, level.getBlockState(worldPos))) {
                continue;
            }
            BlockState ghost = matchers.get(0).representativeState();
            if (ghost == null || ghost.isAir()) {
                continue;
            }
            pose.pushPose();
            pose.translate(worldPos.getX() - camera.x, worldPos.getY() - camera.y, worldPos.getZ() - camera.z);
            // The original's inset: 0.125 offset then 0.75 scale (:270-272).
            pose.translate(0.125D, 0.125D, 0.125D);
            pose.scale(0.75F, 0.75F, 0.75F);
            int light = LevelRenderer.getLightColor(level, worldPos);
            dispatcher.renderSingleBlock(ghost, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, GHOST_TYPE);
            pose.popPose();
        }
        buffers.endBatch(GHOST_TYPE);
    }

    /**
     * P10-17（0.121.37）：结构里**实际有方块**的层（升序）。在 start/clear（即 {@code pattern}
     * 赋值/清空）时重建，tick 只读它——不按 min..max 逐整数枚举坐标，也不建跨世界监听。
     */
    private static java.util.List<Integer> structuredLayers = List.of();

    private static void rebuildStructuredLayers() {
        if (pattern == null) {
            structuredLayers = List.of();
            return;
        }
        java.util.TreeSet<Integer> ys = new java.util.TreeSet<>();
        for (BlockPos pos : pattern.positions().keySet()) {
            ys.add(pos.getY());
        }
        structuredLayers = List.copyOf(ys);
    }

    /**
     * 从低到高找第一个未放完整的实际层；全部完成返回 {@link #NO_LAYER}。
     * 只遍历 {@link #structuredLayers}（结构真实存在的 y），每层复用 {@link #layerMatches}。
     */
    private static int lowestIncompleteLayer(ClientLevel level) {
        for (int y : structuredLayers) {
            if (!layerMatches(level, y)) {
                return y;
            }
        }
        return NO_LAYER;
    }

    /**
     * 从低到高找第一个未放完整的层；全部完整则 {@code renderedLayer = NO_LAYER}
     * （此时渲染与推进都自然停摆，整机匹配交给 tick 清除）。
     *
     * <p>P10-17 返工（0.121.38）：层枚举统一走 {@link #structuredLayers}（结构真实存在的 y，
     * start/clear/换结构时重建）。0.121.37 只把回退检查切到了实际层索引，这里残留旧版
     * {@code for (y = min..max)} 逐整数枚举——空层靠 {@link #layerMatches} 的「无条目即完整」
     * 跳过，语义虽等价，但 Y=0 与 Y=10000 两层实块的结构会在 start 时做约一万次全位置表扫描。
     * 现在与 {@link #lowestIncompleteLayer} 同一实现：扫描次数只随实际层数增长，
     * 不随高度跨度增长；start/clear/换结构/回退/前进各入口全部经此收敛。不加载区块、
     * 不建世界全局监听的边界不变。
     */
    private static void updateLayers(ClientLevel level) {
        renderedLayer = NO_LAYER;
        if (pattern == null || attached == null) {
            return;
        }
        renderedLayer = lowestIncompleteLayer(level);
    }

    /**
     * 单层是否放完整。与 {@code MachinePattern.matches} 的谓词同义（加载检查 + 任一候选命中），
     * 但只看一层；空的候选列表视为「无可判定」，不阻拦层推进——数据空洞不该把虚影卡死
     * （代价：这种病态数据下 tick 的整机匹配会一直失败并反复重算，幂等无害）。
     */
    private static boolean layerMatches(ClientLevel level, int layer) {
        if (pattern == null || attached == null) {
            return false;
        }
        for (Map.Entry<BlockPos, List<BlockMatcher>> entry : pattern.positions().entrySet()) {
            if (entry.getKey().getY() != layer) {
                continue;
            }
            List<BlockMatcher> matchers = entry.getValue();
            if (matchers.isEmpty()) {
                continue;
            }
            BlockPos worldPos = attached.offset(entry.getKey());
            if (!level.isLoaded(worldPos) || !matchesAny(matchers, level.getBlockState(worldPos))) {
                return false;
            }
        }
        return true;
    }

    /** 同 {@code MachinePattern.anyMatches}：任一候选的 {@link BlockMatcher#matches(BlockState)} 命中即可。 */
    private static boolean matchesAny(List<BlockMatcher> matchers, BlockState state) {
        for (BlockMatcher matcher : matchers) {
            if (matcher.matches(state)) {
                return true;
            }
        }
        return false;
    }
}
