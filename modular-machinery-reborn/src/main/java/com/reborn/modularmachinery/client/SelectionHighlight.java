package com.reborn.modularmachinery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reborn.modularmachinery.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Draws the construct tool's selection in the world, the counterpart of the original's
 * {@code SelectionBoxRenderHelper} ({@code :35-53}).
 *
 * <p>The original's rule, kept exactly:
 *
 * <ul>
 *   <li>only while the tool is held — main hand <b>or</b> off hand ({@code :41-45});</li>
 *   <li>only for selected positions within {@code distanceSq <= 1024} of the player, i.e. 32 blocks
 *       ({@code :48-50});</li>
 *   <li>a white outline cube per position ({@code RenderingUtils.drawWhiteOutlineCubes}).</li>
 * </ul>
 *
 * <p>This is a new world-space rendering path for this project (the preview renderer draws into a GUI), and it is
 * deliberately a <b>separate class</b>: {@code client.preview.StructurePreviewRenderer} must not be touched. It
 * is registered on the Forge bus from {@code ClientSetup}, so nothing common ever loads it.
 *
 * <p><b>Nothing here can be verified offline.</b> What is checked in game is in {@code 交接文档.md}'s 0.29.0
 * checklist: the cubes appear where they were clicked, disappear when the tool is put away or the player walks
 * more than 32 blocks away, and vanish after a finalize.
 */
@OnlyIn(Dist.CLIENT)
public final class SelectionHighlight {

    /** The original's 1024 squared blocks: {@code Player#getPosition} distance, not eyes. */
    private static final double RANGE_SQUARED = 1024.0D;

    private SelectionHighlight() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!holdsTool(player)) {
            return;
        }
        List<BlockPos> selected = ClientSelection.positions();
        if (selected.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos playerPos = player.blockPosition();
        for (BlockPos pos : selected) {
            if (pos.distSqr(playerPos) > RANGE_SQUARED) {
                continue;
            }
            // A hair larger than the block so the outline does not z-fight with the block's own faces, and
            // camera-relative because that is the frame the stage's pose is in.
            AABB box = new AABB(pos).inflate(0.005D).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** The original accepted either hand ({@code SelectionBoxRenderHelper.java:41-44}). */
    private static boolean holdsTool(Player player) {
        return player.getMainHandItem().is(ModItems.CONSTRUCT_TOOL.get())
                || player.getOffhandItem().is(ModItems.CONSTRUCT_TOOL.get());
    }

    /**
     * The original cleared the client's copy of the selection when the connection dropped
     * ({@code SelectionBoxRenderHelper.java:73-77}); a selection belonging to another server must not be drawn
     * after joining this one.
     */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSelection.clear();
    }
}
