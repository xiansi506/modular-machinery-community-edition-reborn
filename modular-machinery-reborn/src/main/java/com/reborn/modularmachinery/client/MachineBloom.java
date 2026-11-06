package com.reborn.modularmachinery.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Deferred emissive mask, depth tested against the world, followed by two blur passes and composition. */
public final class MachineBloom {
    private static final ResourceLocation CHAIN = new ResourceLocation(ModularMachineryReborn.MOD_ID, "shaders/post/machine_bloom.json");
    private static final Map<ResourceLocation, MultiBufferSource.BufferSource> BUFFERS = new LinkedHashMap<>();
    private static final List<BufferBuilder> POOL = new ArrayList<>();
    private static PostChain chain;
    private static RenderTarget main;
    private static int width, height;
    private static boolean pending, failed;
    private static Matrix4f projection, modelView;
    private static VertexSorting sorting;
    private MachineBloom() {}

    public static VertexConsumer capture(ResourceLocation texture) {
        if (failed) return null;
        if (!BUFFERS.containsKey(texture) && BUFFERS.size() >= 64) return null;
        if (!pending) {
            projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            sorting = RenderSystem.getVertexSorting();
            pending = true;
        }
        // One buffer per texture prevents an automatic flush into the world's current framebuffer.
        MultiBufferSource.BufferSource buffers = BUFFERS.computeIfAbsent(texture, ignored -> {
            int index = BUFFERS.size();
            if (POOL.size() <= index) POOL.add(new BufferBuilder(256));
            return MultiBufferSource.immediate(POOL.get(index));
        });
        return buffers.getBuffer(com.reborn.modularmachinery.client.geo.MachineModelRenderTypes.get(texture,
                new com.reborn.modularmachinery.client.geo.BoneRenderStyle(true, true)));
    }

    /** Keep the first failure and attach later cleanup errors; never replace the first cause. */
    private static Throwable accumulate(Throwable primary, Throwable next) {
        if (primary == null) return next;
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }

    /** Each recovery step is protected separately so one failure cannot skip the remaining ones. */
    private static Throwable attempt(Throwable primary, Runnable step) {
        try {
            step.run();
        } catch (Exception | LinkageError error) {
            return accumulate(primary, error);
        }
        return primary;
    }

    private static void rethrow(Throwable primary) {
        if (primary instanceof RuntimeException runtime) throw runtime;
        if (primary instanceof Error error) throw error;
        throw new IllegalStateException("Machine bloom cleanup failed", primary);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !pending) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        var stack = RenderSystem.getModelViewStack();
        RenderTarget mask = null;
        boolean depthCopied = false;
        boolean pushed = false;
        Throwable primary = null;
        boolean bodyDegraded = false;
        try {
            stack.pushPose();
            pushed = true;
            if (chain == null || main != target) {
                if (chain != null) {
                    PostChain previous = chain;
                    chain = null;
                    previous.close();
                }
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), target, CHAIN);
                main = target; width = height = 0;
            }
            if (width != target.width || height != target.height) {
                chain.resize(target.width, target.height); width = target.width; height = target.height;
            }
            mask = chain.getTempTarget("mask");
            mask.setClearColor(0, 0, 0, 0);
            mask.clear(Minecraft.ON_OSX);
            mask.copyDepthFrom(target);
            depthCopied = true;
            mask.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, sorting);
            stack.last().pose().set(modelView);
            RenderSystem.applyModelViewMatrix();
            BUFFERS.values().forEach(MultiBufferSource.BufferSource::endBatch);
            chain.process(event.getPartialTick());
        } catch (Exception | LinkageError error) {
            primary = error;
            if (pushed) {
                // A failure in the actual render body keeps the existing degraded behaviour.
                failed = true;
                bodyDegraded = true;
                ModularMachineryReborn.LOGGER.warn("Machine bloom disabled until resource reload; emissive lighting remains available", error);
            }
        } finally {
            // Final snapshots for the protected steps; mutable bookkeeping stays outside lambdas.
            final RenderTarget cleanupMask = mask;
            final boolean cleanupDepthCopied = depthCopied;
            final boolean cleanupPushed = pushed;
            final var cleanupStack = stack;
            // Plain flag assignments cannot fail, so do them before any cleanup step that can.
            pending = false;
            primary = attempt(primary, () -> {
                if (cleanupDepthCopied && cleanupMask != null) target.copyDepthFrom(cleanupMask);
            });
            primary = attempt(primary, () -> target.bindWrite(true));
            primary = attempt(primary, () -> RenderSystem.setProjectionMatrix(oldProjection, oldSorting));
            primary = attempt(primary, () -> { if (cleanupPushed) cleanupStack.popPose(); });
            primary = attempt(primary, RenderSystem::applyModelViewMatrix);
            primary = attempt(primary, MachineBloom::discard);
            if (primary != null) {
                failed = true;
                // Drop the references first: a failed chain must never be used or closed twice.
                PostChain toClose = chain;
                chain = null;
                main = null;
                if (toClose != null) primary = attempt(primary, toClose::close);
                ModularMachineryReborn.LOGGER.warn("Machine bloom cleanup completed with a retained first failure", primary);
                if (!bodyDegraded) rethrow(primary);
            }
        }
    }

    private static void discard() {
        // Retain bounded native buffers across reloads; release unfinished batches without drawing them.
        // A single failing buffer must not prevent the remaining buffers or the map from being cleared.
        Throwable primary = null;
        for (BufferBuilder builder : POOL) {
            try {
                if (builder.building()) builder.end().release();
            } catch (Exception | LinkageError error) {
                primary = accumulate(primary, error);
            }
        }
        BUFFERS.clear();
        if (primary != null) rethrow(primary);
    }

    public static void reset() {
        RenderSystem.recordRenderCall(() -> {
            // Clear references before close so a failing close cannot be repeated by a later reset.
            PostChain toClose = chain;
            chain = null;
            main = null;
            Throwable primary = null;
            if (toClose != null) primary = attempt(primary, toClose::close);
            primary = attempt(primary, MachineBloom::discard);
            pending = false;
            failed = false;
            if (primary != null) {
                ModularMachineryReborn.LOGGER.warn("Machine bloom reset completed with a retained first failure", primary);
                rethrow(primary);
            }
        });
    }
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
