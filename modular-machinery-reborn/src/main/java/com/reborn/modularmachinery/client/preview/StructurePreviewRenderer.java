package com.reborn.modularmachinery.client.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.reborn.modularmachinery.machine.StructurePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;

import java.util.List;
import java.util.Set;

/**
 * Draws a {@link StructurePreview} as blocks inside a GUI.
 *
 * <p>This is written for 1.20.1 rather than ported line-for-line: the original drove an 847-line
 * {@code WorldSceneRenderer} that built a fake world and rendered it through the chunk pipeline, with its own
 * camera, mouse look and compile-cache thread. The same camera convention is reproduced here with a small
 * perspective pass over the normal block renderer, which is enough for the few dozen blocks in a preview.
 *
 * <p>What it does instead:
 *
 * <ul>
 *   <li>Uses the original orbit camera and a perspective projection, then maps that image into the GUI viewport.
 *       The model itself remains in the normal +Y-up world basis, so its top faces keep the same lighting as an
 *       in-world block.</li>
 *   <li>Pushes the whole structure forward in Z so it sits in front of the panel that was already drawn, the
 *       way vanilla puts container slot items at z=100.</li>
 *   <li>Lights the blocks the way terrain is lit: {@code Lighting.setupLevel(viewMatrix)} with the view
 *       rotation that also transforms the vertices, instead of {@code setupFor3DItems()}, whose light
 *       directions are a fixed GUI-item orientation unrelated to this camera. That is what makes a preview
 *       face as bright as the same block in the world; see the comment in {@code render()}.</li>
 * </ul>
 *
 * <p>The projection/model-view state is saved and restored around the draw so JEI and the surrounding screen keep
 * their normal GUI matrices.
 */
public final class StructurePreviewRenderer {
    private static long lastFailureReport;

    /** The original widget called the vertical angle yaw and started it at 25 degrees. */
    public static final float DEFAULT_YAW = 25.0F;
    /** The original widget called the freely rotating horizontal angle pitch and started it at -135 degrees. */
    public static final float DEFAULT_PITCH = -135.0F;
    /** The original clamped its vertical angle to just short of straight up or down. */
    public static final float MIN_YAW = -89.9F;
    public static final float MAX_YAW = 89.9F;
    public static final float MIN_ZOOM = 0.35F;
    public static final float MAX_ZOOM = 4.0F;

    private StructurePreviewRenderer() {
    }

    /**
     * Finds the nearest preview block under a panel-space mouse position.
     *
     * <p>The same perspective camera and block-sized bounds as the renderer are used, so selection follows the
     * rotated, zoomed and panned model instead of a flat screen-space approximation. The preview currently draws
     * each block through the vanilla model renderer, so picking uses its unit-cell bounds; custom non-cubic model
     * geometry may therefore have a slightly larger selectable area than its visible faces.
     */
    public static StructurePreview.Block pickBlockAt(StructurePreview preview, List<StructurePreview.Block> blocks,
                                                       double mouseX, double mouseY, double centerX, double centerY,
                                                       float boxPixels, float yaw, float pitch, float zoom) {
        if (preview == null || preview.isEmpty() || blocks.isEmpty() || boxPixels <= 0.0F) {
            return null;
        }
        double nx = (mouseX - centerX) * 2.0 / boxPixels;
        double ny = (centerY - mouseY) * 2.0 / boxPixels;
        // The fitting square is smaller than the real viewport. The caller clips mouse input;
        // rays outside this square can still hit geometry visible near the viewport's sides.

        BlockPos size = preview.size();
        // Square in long: a sparse structure near x=46340 overflows the int square-sum to a negative
        // value, whose sqrt is NaN and kills the whole projection. Both pick and render share this rule.
        float diagonal = (float) Math.sqrt((double) size.getX() * size.getX()
                + (double) size.getY() * size.getY() + (double) size.getZ() * size.getZ());
        float cameraDistance = Math.max(diagonal * 0.55F,
                Math.max(1.0F, diagonal * 1.18F) / Math.max(0.01F, zoom));
        // The pick ray is built in double with the same camera convention: a sparse structure puts the
        // camera tens of thousands of blocks out, where a float inverseVP lands the ray thousands of
        // blocks off its aim and nothing can ever be picked. The scale stays the one the render uses.
        Matrix4d perspective = new Matrix4d().perspective(Math.toRadians(60.0), 1.0,
                0.05, Math.max(100.0, cameraDistance * 8.0));
        Matrix4d view = cameraViewD(cameraDistance, yaw, pitch);
        Matrix4d inverseViewProjection = perspective.mul(view).invert();
        Vector3d near = unproject(inverseViewProjection, nx, ny, -1.0);
        Vector3d far = unproject(inverseViewProjection, nx, ny, 1.0);
        if (near == null || far == null) {
            return null;
        }

        Vector3d direction = far.sub(near).normalize();
        double centerBlockX = (preview.min().getX() + preview.max().getX() + 1.0) / 2.0;
        double centerBlockY = (preview.min().getY() + preview.max().getY() + 1.0) / 2.0;
        double centerBlockZ = (preview.min().getZ() + preview.max().getZ() + 1.0) / 2.0;
        near.add(centerBlockX, centerBlockY, centerBlockZ);

        StructurePreview.Block closest = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (StructurePreview.Block block : blocks) {
            BlockPos pos = block.pos();
            double distance = rayBox(near, direction, pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
            if (distance >= 0.0 && distance < nearest) {
                nearest = distance;
                closest = block;
            }
        }
        return closest;
    }

    private static Vector3d unproject(Matrix4d inverse, double x, double y, double z) {
        Vector4d point = new Vector4d(x, y, z, 1.0).mul(inverse);
        if (Math.abs(point.w) < 1.0E-7) {
            return null;
        }
        return new Vector3d(point.x / point.w, point.y / point.w, point.z / point.w);
    }

    /** The pick camera as a world-to-camera matrix, the double twin of {@link #cameraView(float, float, float)}. */
    private static Matrix4d cameraViewD(float distance, float yaw, float pitch) {
        double horizontal = Math.toRadians(pitch);
        double vertical = Math.toRadians(yaw);
        Vector3d eye = new Vector3d(Math.cos(horizontal), Math.tan(vertical), Math.sin(horizontal))
                .normalize().mul(distance);
        return new Matrix4d().lookAt(eye, new Vector3d(), new Vector3d(0.0, 1.0, 0.0));
    }

    private static double rayBox(Vector3d origin, Vector3d direction,
                                 double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        double[] origins = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] mins = {minX, minY, minZ};
        double[] maxs = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-8) {
                if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) {
                    return -1.0;
                }
                continue;
            }
            double first = (mins[axis] - origins[axis]) / directions[axis];
            double second = (maxs[axis] - origins[axis]) / directions[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (near > far) {
                return -1.0;
            }
        }
        return far < 0.0 ? -1.0 : near;
    }

    /**
     * Clips drawing to a rectangle given in the <b>caller's own</b> coordinates.
     *
     * <p>{@code GuiGraphics#enableScissor} takes absolute screen coordinates — it does not consult the pose —
     * but JEI calls a category's {@code draw} with the pose already translated to the recipe's origin. Passing
     * local coordinates straight through therefore puts the scissor somewhere else entirely: for a JEI category
     * the rectangle lands over the top-left corner of the window, the structure is clipped away in full, and all
     * that is left is the panel's dark background. Reading the translation back out of the pose is correct
     * wherever the caller happens to be.
     */
    public static void clip(GuiGraphics graphics, int x, int y, int width, int height) {
        Matrix4f matrix = graphics.pose().last().pose();
        int originX = Math.round(matrix.m30());
        int originY = Math.round(matrix.m31());
        graphics.enableScissor(originX + x, originY + y, originX + x + width, originY + y + height);
    }

    /**
     * Draws the structure centred on the given point, fitted into a box of {@code boxPixels}.
     *
     * <p>The transform follows the old {@code WorldSceneRendererWidget}: {@code pitch} is the horizontal
     * orbit angle and {@code yaw} is the vertical angle. The perspective viewport already uses a conventional
     * +Y-up camera, so no axis-flipping scale is needed.
     *
     * @param zoom 1 fits the structure with a margin; larger values zoom in
     */
    public static void render(GuiGraphics graphics, StructurePreview preview, float centerX, float centerY,
                              float boxPixels, float yaw, float pitch, float zoom) {
        if (preview == null) {
            return;
        }
        render(graphics, preview, preview.blocks(), 0, centerX, centerY, boxPixels, yaw, pitch, zoom, null);
    }

    /**
     * Draws a subset of a structure's blocks, optionally cycling each position to one of its alternatives.
     *
     * <p>{@code blocks} is what actually gets drawn — the slice preview passes one horizontal layer — while the
     * camera still fits {@code preview} as a whole, which is what keeps a layer in place when the user steps
     * through the structure instead of the model jumping around. {@code variant} is the cycling index; each
     * block takes it modulo its own number of accepted alternatives, so it is ignored unless the caller turns
     * cycling on. Neither parameter touches the projection, the camera or the lighting below.
     */
    public static void render(GuiGraphics graphics, StructurePreview preview, List<StructurePreview.Block> blocks,
                              int variant, float centerX, float centerY, float boxPixels,
                              float yaw, float pitch, float zoom, BlockPos selectedBlock) {
        render(graphics, preview, blocks, variant, centerX, centerY, boxPixels, yaw, pitch, zoom,
                selectedBlock, Set.of());
    }

    /** Draws the preview and highlights every position belonging to the selected modifier entry. */
    public static void render(GuiGraphics graphics, StructurePreview preview, List<StructurePreview.Block> blocks,
                              int variant, float centerX, float centerY, float boxPixels,
                              float yaw, float pitch, float zoom, BlockPos selectedBlock,
                              Set<BlockPos> modifierPositions) {
        render(graphics,preview,blocks,variant,centerX,centerY,boxPixels,yaw,pitch,zoom,selectedBlock,
                modifierPositions,null,false);
    }

    public static void render(GuiGraphics graphics, StructurePreview preview, List<StructurePreview.Block> blocks,
                              int variant, float centerX, float centerY, float boxPixels,
                              float yaw, float pitch, float zoom, BlockPos selectedBlock,
                              Set<BlockPos> modifierPositions, net.minecraft.resources.ResourceLocation machineId,
                              boolean formed) {
        if (preview == null || preview.isEmpty() || blocks.isEmpty()) {
            return;
        }

        BlockPos size = preview.size();
        // The original renderer used a real perspective camera.  Keep the same
        // camera convention here: the model stays in world coordinates (+Y up),
        // and the projection, rather than an X-axis half-turn, converts its image
        // to the GUI's top-left origin.  That is what keeps the visible top faces
        // lit like the in-world model instead of showing the dark underside.
        // Square in long, the same rule pickBlockAt uses: an int square-sum overflows to NaN.
        float diagonal = (float) Math.sqrt((double) size.getX() * size.getX()
                + (double) size.getY() * size.getY() + (double) size.getZ() * size.getZ());
        // Keep the camera just outside the bounding sphere even at maximum zoom;
        // otherwise the near plane can enter a tall structure and make its front
        // blocks disappear while dragging/scrolling.
        float cameraDistance = Math.max(diagonal * 0.55F,
                Math.max(1.0F, diagonal * 1.18F) / Math.max(0.01F, zoom));
        float centerBlockX = (preview.min().getX() + preview.max().getX() + 1.0F) / 2.0F;
        float centerBlockY = (preview.min().getY() + preview.max().getY() + 1.0F) / 2.0F;
        float centerBlockZ = (preview.min().getZ() + preview.max().getZ() + 1.0F) / 2.0F;

        PoseStack pose = graphics.pose();
        Matrix4f callerPose = new Matrix4f(pose.last().pose());
        float originX = callerPose.m30();
        float originY = callerPose.m31();
        float originZ = callerPose.m32();
        float absoluteCenterX = originX + centerX;
        float absoluteCenterY = originY + centerY;

        Minecraft minecraft = Minecraft.getInstance();
        float guiWidth = minecraft.getWindow().getGuiScaledWidth();
        float guiHeight = minecraft.getWindow().getGuiScaledHeight();
        Matrix4f projection = perspectiveProjection(guiWidth, guiHeight, absoluteCenterX, absoluteCenterY,
                boxPixels, cameraDistance);

        // The panel/background may still be queued in GuiGraphics' immediate
        // buffer.  Submit it under the normal GUI matrices before replacing the
        // projection, otherwise the panel would be transformed by the camera too.
        graphics.flush();

        // GUI rendering normally has a large negative model-view translation so
        // that z=100 is in front of the panel.  The custom projection below owns
        // the depth range, so temporarily remove that translation and restore it
        // before returning to JEI/the screen.
        PoseStack modelView = RenderSystem.getModelViewStack();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        pose.pushPose();
        modelView.pushPose();
        try (PreviewBufferBatch batch = new PreviewBufferBatch()) {
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();

            // Cancel the recipe/screen origin in the caller's pose.  The custom
            // projection receives absolute GUI coordinates, so this also works
            // when JEI has translated the category before calling draw().
            Matrix4f view = cameraView(cameraDistance, yaw, pitch);
            pose.translate(-originX, -originY, -originZ);
            applyCameraView(pose,view);
            pose.translate(-centerBlockX, -centerBlockY, -centerBlockZ);

            // Light the blocks the way the world does. Terrain calls
            // Lighting.setupLevel(poseStack.last().pose()) -- the same matrix that transforms its vertices --
            // so the diffuse light directions are rotated into view space by the actual view rotation.
            // setupFor3DItems() instead rotates them by a fixed GUI-item orientation
            // (GlStateManager.setupGui3DDiffuseLighting applies rotationYXZ(1.0821, 3.2376, 0) on top of
            // rotateYXZ(-0.3927, 2.3562, 0)), which has nothing to do with this camera: every face comes out
            // darker than the same block in the world, which reads as the structure being lit from inside.
            // The matrix must carry no translation -- setupLevelDiffuseLighting transforms the light vectors
            // as w=1 points, so a translation would skew the directions rather than rotate them.
            Lighting.setupLevel(new Matrix4f(view).setTranslation(0.0F, 0.0F, 0.0F));
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            PreviewBlockEntities entities = minecraft.level == null ? null
                    : PreviewBlockEntities.prepare(preview, blocks, variant, minecraft.level,machineId,formed);
            var entityDispatcher = minecraft.getBlockEntityRenderDispatcher();
            for (StructurePreview.Block block : blocks) {
                pose.pushPose();
                try {
                    pose.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
                    var state = block.variantState(variant);
                    // Animated blocks already have an item-render fallback in renderSingleBlock. Skip that
                    // fallback when drawing the actual preview entity, otherwise chests are rendered twice.
                    if (entities != null && entities.modelFailed(block.pos())) continue;
                    if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED
                            || entities == null || !entities.hasRenderer(block.pos(), entityDispatcher)) {
                        dispatcher.renderSingleBlock(state, pose, batch.source,
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                entities == null ? net.minecraftforge.client.model.data.ModelData.EMPTY
                                        : entities.modelData(block.pos()), null);
                    }
                    if (entities != null && !entities.renderSafely(block.pos(), pose, batch.source,
                            LightTexture.FULL_BRIGHT, entityDispatcher)) throw new IllegalStateException("Preview entity batch failed");
                } catch (RuntimeException | LinkageError error) {
                    if (entities != null && !entities.rendererFailed(block.pos())) entities.failModel(block.pos(), error);
                    throw error;
                } finally { pose.popPose(); }
            }
            if (entities != null) for (StructurePreview.Block block : blocks) {
                var state = block.variantState(variant);
                if (state.getFluidState().isEmpty() || entities.modelFailed(block.pos())) continue;
                try {
                    PreviewFluidRenderer.render(dispatcher, entities.level(), block, variant, pose, batch.source);
                } catch (RuntimeException | LinkageError error) {
                    entities.failModel(block.pos(), error);
                    throw error;
                }
            }
            if (!modifierPositions.isEmpty()) {
                VertexConsumer lines = batch.source.getBuffer(RenderType.lines());
                for (BlockPos pos : modifierPositions) {
                    if (blocks.stream().anyMatch(block -> block.pos().equals(pos))) {
                        LevelRenderer.renderLineBox(pose, lines,
                                pos.getX() - 0.002, pos.getY() - 0.002, pos.getZ() - 0.002,
                                pos.getX() + 1.002, pos.getY() + 1.002, pos.getZ() + 1.002,
                                0.0F, 1.0F, 0.5F, 1.0F);
                    }
                }
                batch.source.endBatch(RenderType.lines());
            }
            if (selectedBlock != null && blocks.stream().anyMatch(block -> block.pos().equals(selectedBlock))) {
                VertexConsumer lines = batch.source.getBuffer(RenderType.lines());
                LevelRenderer.renderLineBox(pose, lines,
                        selectedBlock.getX() - 0.002, selectedBlock.getY() - 0.002, selectedBlock.getZ() - 0.002,
                        selectedBlock.getX() + 1.002, selectedBlock.getY() + 1.002, selectedBlock.getZ() + 1.002,
                        0.25F, 0.9F, 1.0F, 1.0F);
                batch.source.endBatch(RenderType.lines());
            }
            batch.submit();
        } catch (RuntimeException | LinkageError error) {
            // The scene records the offending block and skips it until rebuilt or reloaded.
            // The private batch is discarded before the GUI projection is restored.
            long now = net.minecraft.Util.getMillis();
            if (now - lastFailureReport > 5000) {
                lastFailureReport = now;
                com.reborn.modularmachinery.ModularMachineryReborn.LOGGER.warn("Could not draw structure preview", error);
            }
        } finally {
            // GameRenderer enters screen rendering with 3D item lighting.  Keep
            // that baseline for the ingredient row and any following widgets.
            try {
                Lighting.setupFor3DItems();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.defaultBlendFunc();
            } finally {
                try {
                    modelView.popPose();
                    RenderSystem.applyModelViewMatrix();
                } finally {
                    try { RenderSystem.setProjectionMatrix(savedProjection, savedSorting); }
                    finally { pose.popPose(); }
                }
            }
        }
    }

    /** PoseStack.mulPoseMatrix does not transform normals in 1.20.1. */
    public static void applyCameraView(PoseStack pose, Matrix4f view) {
        pose.mulPoseMatrix(view);
        pose.last().normal().mul(new org.joml.Matrix3f(view).normal());
    }

    /** Builds a perspective camera projection whose viewport is the preview rectangle in GUI pixels. */
    private static Matrix4f perspectiveProjection(float guiWidth, float guiHeight, float centerX, float centerY,
                                                   float boxPixels, float cameraDistance) {
        float near = 0.05F;
        float far = Math.max(100.0F, cameraDistance * 8.0F);
        Matrix4f perspective = new Matrix4f().perspective((float) Math.toRadians(60.0F), 1.0F, near, far);

        // Transform the perspective NDC cube into the GUI's full-screen NDC cube.
        //
        // No Y flip here. GameRenderer builds the GUI projection as
        // setOrtho(0, width, height, 0, 1000, 21000) -- bottom = height, top = 0 -- so in the GUI's NDC
        // +Y already points up the screen (GUI y=0 maps to NDC +1). The perspective matrix produces the
        // ordinary +Y-up NDC too, so the two agree and the vertical axis must be scaled by a POSITIVE
        // factor. A negative factor flips the image upside down AND, because it makes the projection's
        // determinant negative, mirrors triangle winding -- which reverses back-face culling, so the
        // culled faces are the front ones and a block shows its own inside. Both symptoms had the same
        // cause, and this one sign.
        Matrix4f viewport = new Matrix4f().identity();
        viewport.m00(boxPixels / guiWidth);
        viewport.m11(boxPixels / guiHeight);
        // The vanilla GUI panel is around depth 0.9 after GameRenderer's GUI
        // projection.  Keep the preview in front of it (depth 0.10 .. 0.20),
        // while preserving enough range for block-to-block occlusion.
        viewport.m22(0.05F);
        viewport.m30(2.0F * centerX / guiWidth - 1.0F);
        viewport.m31(1.0F - 2.0F * centerY / guiHeight);
        // GUI panels are drawn at z=0.  Map the model into the same depth ordering
        // used by vanilla GUI items: the nearest camera points are just in front of
        // the panel and farther points approach the panel's depth.
        viewport.m32(0.15F);
        return viewport.mul(perspective);
    }

    /** Returns the old renderer's orbit camera as a conventional world-to-camera matrix. */
    private static Matrix4f cameraView(float distance, float yaw, float pitch) {
        double horizontal = Math.toRadians(pitch);
        double vertical = Math.toRadians(yaw);
        Vector3f eye = new Vector3f((float) Math.cos(horizontal), (float) Math.tan(vertical),
                (float) Math.sin(horizontal)).normalize().mul(distance);
        return new Matrix4f().lookAt(eye, new Vector3f(), new Vector3f(0.0F, 1.0F, 0.0F));
    }
}
