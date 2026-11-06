package com.reborn.modularmachinery.machine;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Position-based invalidation; weak world/owner keys never keep unloaded worlds alive. */
public final class InputDependencyIndex<T> {
    private static final class World<T> {
        final Map<BlockPos,WeakHashMap<T,Boolean>> listeners = new HashMap<>();
        final Map<Long,WeakHashMap<T,Boolean>> chunks = new HashMap<>();
        final WeakHashMap<T,Set<BlockPos>> positions = new WeakHashMap<>();
        final WeakHashMap<T,Boolean> dirty = new WeakHashMap<>();
    }
    private final WeakHashMap<Object,World<T>> worlds = new WeakHashMap<>();

    public synchronized void watch(Object world, T owner, Collection<BlockPos> positions) {
        forget(world,owner);
        if (positions.isEmpty()) return;
        World<T> state = worlds.computeIfAbsent(world,ignored->new World<>());
        Set<BlockPos> keys = Set.copyOf(positions);
        state.positions.put(owner,keys);
        for (BlockPos key : keys) {
            state.listeners.computeIfAbsent(key,ignored->new WeakHashMap<>()).put(owner,true);
            state.chunks.computeIfAbsent(chunk(key),ignored->new WeakHashMap<>()).put(owner,true);
        }
    }

    public synchronized void forget(Object world, T owner) {
        World<T> state = worlds.get(world);
        if (state == null) return;
        Set<BlockPos> positions = state.positions.remove(owner);
        state.dirty.remove(owner);
        if (positions != null) for (BlockPos key : positions) {
            var listeners = state.listeners.get(key);
            if (listeners != null) { listeners.remove(owner); if (listeners.isEmpty()) state.listeners.remove(key); }
            long chunk = chunk(key);
            var chunkListeners = state.chunks.get(chunk);
            if (chunkListeners != null) {
                chunkListeners.remove(owner);
                if (chunkListeners.isEmpty()) state.chunks.remove(chunk);
            }
        }
        if (state.positions.isEmpty()) worlds.remove(world);
    }

    public synchronized void changed(Object world, BlockPos position) {
        World<T> state = worlds.get(world);
        if (state == null) return;
        var listeners = state.listeners.get(position);
        if (listeners != null) for (T owner : listeners.keySet()) if (owner != null) state.dirty.put(owner,true);
    }

    /** Chunk lifecycle invalidates only owners already watching that chunk; never queries world contents. */
    public synchronized void changedChunk(Object world, int x, int z) {
        World<T> state = worlds.get(world);
        if (state == null) return;
        var listeners = state.chunks.get(net.minecraft.world.level.ChunkPos.asLong(x,z));
        if (listeners != null) for (T owner : listeners.keySet()) if (owner != null) state.dirty.put(owner,true);
    }

    private static long chunk(BlockPos position) {
        return net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
    }

    /** Repeated changes merge until this owner's next tick. */
    public synchronized boolean takeDirty(Object world, T owner) {
        World<T> state = worlds.get(world);
        return state != null && state.dirty.remove(owner) != null;
    }
}
