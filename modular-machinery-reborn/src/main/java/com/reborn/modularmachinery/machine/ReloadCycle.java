package com.reborn.modularmachinery.machine;

import com.google.gson.JsonElement;
import com.reborn.modularmachinery.upgrade.UpgradeRegistry;
import com.reborn.modularmachinery.upgrade.UpgradeTarget;
import com.reborn.modularmachinery.upgrade.UpgradeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Owns only this mod's published reload data, not arbitrary other mods' globals or script side effects.
 * The small SimpleReloadInstance lifecycle hook captures the real owning future. Listener tasks keep
 * their original session even after fail-fast failure; a late task can never fall back to live publish.
 */
public final class ReloadCycle {
    private static final Map<ResourceManager, Session> ACTIVE = new IdentityHashMap<>();
    private static final Map<Object, Session> OWNERS = new IdentityHashMap<>();
    private static final ThreadLocal<Session> APPLYING = new ThreadLocal<>();

    private ReloadCycle() {}

    private static final class Session {
        final ResourceManager resources;
        final Executor applyExecutor;
        MachineRegistry.Prepared machines;
        UpgradeRegistry.Prepared upgrades;
        boolean closed;

        Session(ResourceManager resources, Executor applyExecutor) {
            this.resources = resources;
            this.applyExecutor = applyExecutor;
        }

        synchronized void requireOpen() {
            if (closed) throw new IllegalStateException("MMCER reload candidate belongs to an ended cycle");
        }

        synchronized void finish(boolean success) {
            requireOpen();
            closed = true;
            if (success) {
                // All validation/allocation happened while staging. These installs invoke no scripts,
                // world capabilities or dependent listeners between them. Normal server readers share
                // this apply executor; this is not a cross-thread database transaction guarantee.
                if (machines != null) MachineRegistry.publish(machines);
                if (upgrades != null) UpgradeRegistry.publish(upgrades);
            }
            machines = null;
            upgrades = null;
        }
    }

    /** Called by the constructor hook before it starts listeners; ResourceManager identity is unchanged. */
    public static List<PreparableReloadListener> begin(Object owner, ResourceManager resources,
                                                      List<PreparableReloadListener> listeners, Executor applyExecutor) {
        Session session = new Session(resources, applyExecutor);
        synchronized (ACTIVE) {
            if (ACTIVE.containsKey(resources))
                throw new IllegalStateException("Overlapping MMCER reloads on the same ResourceManager are unsupported");
            ACTIVE.put(resources, session);
            OWNERS.put(owner, session);
        }
        List<PreparableReloadListener> wrapped = new ArrayList<>(listeners.size());
        for (PreparableReloadListener listener : listeners) wrapped.add(new Listener(listener, session));
        return wrapped;
    }

    /** Returns a future that cannot report success before candidate publication on the apply executor. */
    public static <T> CompletableFuture<T> complete(Object owner, CompletableFuture<T> allDone) {
        Session session;
        synchronized (ACTIVE) { session = OWNERS.get(owner); }
        if (session == null) throw new IllegalStateException("Missing MMCER reload owner");
        CompletableFuture<T> committed = allDone.handleAsync((value, failure) -> {
            session.finish(failure == null);
            if (failure != null) throw failure instanceof CompletionException completion
                    ? completion : new CompletionException(failure);
            return value;
        }, session.applyExecutor);
        return committed.whenComplete((value, failure) -> {
            synchronized (session) {
                // Also close if an executor rejected the completion task itself.
                if (!session.closed) session.finish(false);
            }
            synchronized (ACTIVE) {
                ACTIVE.remove(session.resources, session);
                OWNERS.remove(owner, session);
            }
        });
    }

    private record Listener(PreparableReloadListener original, Session session) implements PreparableReloadListener {
        @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                Executor preparationExecutor, Executor applyExecutor) {
            try {
                return original.reload(barrier, resources, preparationProfiler, reloadProfiler,
                        scoped(preparationExecutor, session), scoped(applyExecutor, session));
            } catch (RuntimeException | Error failure) {
                // A synchronously throwing listener must still close the real owner as a failed future.
                return CompletableFuture.failedFuture(failure);
            }
        }
        @Override public String getName() { return original.getName(); }
    }

    private static Executor scoped(Executor executor, Session session) {
        return action -> executor.execute(() -> {
            Session previous = APPLYING.get();
            APPLYING.set(session);
            try { action.run(); }
            finally {
                if (previous == null) APPLYING.remove(); else APPLYING.set(previous);
            }
        });
    }

    public static void machines(Map<ResourceLocation, MachineDefinition> loaded,
                                Map<ResourceLocation, JsonElement> sources) {
        MachineRegistry.Prepared candidate = MachineRegistry.prepare(loaded, sources);
        Session session = APPLYING.get();
        if (session == null) { MachineRegistry.publish(candidate); return; }
        synchronized (session) {
            session.requireOpen();
            session.machines = candidate;
        }
    }

    public static void upgrades(Map<ResourceLocation, UpgradeType> types, Map<Item, UpgradeTarget.Targets> targets) {
        UpgradeRegistry.Prepared candidate = UpgradeRegistry.prepare(types, targets);
        Session session = APPLYING.get();
        if (session == null) { UpgradeRegistry.publish(candidate); return; }
        synchronized (session) {
            session.requireOpen();
            session.upgrades = candidate;
        }
    }

    /** Only dependent loading uses this candidate view; gameplay and login snapshots stay published-only. */
    public static boolean machineExists(ResourceLocation id) {
        Session session = APPLYING.get();
        if (session == null) return MachineRegistry.contains(id);
        synchronized (session) {
            session.requireOpen();
            return session.machines == null ? MachineRegistry.contains(id) : session.machines.machines().containsKey(id);
        }
    }

    /** Diagnostic/test counter, never a way to commit or manually repair a cycle. */
    public static int activeCycles() { synchronized (ACTIVE) { return ACTIVE.size(); } }
}
