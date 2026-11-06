package com.reborn.modularmachinery.magic;

import com.reborn.modularmachinery.recipe.ResourceTransaction;
import javax.annotation.Nullable;

/** A resource channel. World-backed channels identify the shared environment, rather than the hatch. */
public interface MagicPort {
    MagicFamily family();
    boolean input();
    @Nullable Access access(String variant);
    default MagicPort transactionView(ResourceTransaction transaction) {
        MagicPort source = this;
        return new MagicPort() {
            public MagicFamily family() { return source.family(); }
            public boolean input() { return source.input(); }
            public Access access(String variant) {
                Access access = source.access(variant);
                if(access != null) access.prepare(transaction);
                return access == null ? null : transaction.shared(new Key(source.family(), access.identity(), variant),
                        () -> access.plan(transaction));
            }
        };
    }
    record Key(MagicFamily family, Object identity, String variant) {}
    interface Access {
        /** Enlist shared backend state before constructing the per-view shared entry (no recursive map writes). */
        default void prepare(ResourceTransaction transaction) {}
        /** Environmental backends may coordinate spatial read/write footprints within this transaction. */
        default Access plan(ResourceTransaction transaction) { return new Planned(this, transaction); }
        Object identity();
        double amount();
        double minimum();
        double maximum();
        /** Effective mL volume, for a pneumatic pressure gate; zero for other resources. */
        default double volume() { return 0; }
        /** Signed change; returns the signed amount actually moved. */
        double move(double delta, boolean simulate);
        default boolean valid() { return true; }
        /** Opt-in search contract: every planned balance/movement in this closed range is an integer
         * multiple of the returned positive power-of-two quantum. Reads must remain stable and pure.
         * Unknown/custom planners return NaN; a hint never replaces actual shared payment checks. */
        default double parallelQuantum(double lowerBalance, double upperBalance) { return Double.NaN; }
        /** Pure linear capacity planning on the candidate balance, with no quantity-dependent permission
         * or custom side effects. Enables a monotone path only when all intermediate dyadic arithmetic
         * is proved exact and each shared identity has a single IO direction in that phase. */
        default boolean linearParallelPlanning() { return false; }
        default Object snapshot() { return amount(); }
        default boolean unchanged(Object snapshot) { return valid() && Double.compare(amount(), (Double) snapshot) == 0; }
        default void restore(Object snapshot) {
            double delta = (Double) snapshot - amount();
            if (!same(move(delta, false), delta)) throw new IllegalStateException("Magic resource rollback refused");
        }
    }
    static boolean same(double a, double b) {
        if (a == b) return true;
        return a != 0 && b != 0 && Double.isFinite(a) && Double.isFinite(b)
                && Math.abs(a-b) <= 8*Math.max(Math.ulp(a),Math.ulp(b));
    }
    static boolean movementMatches(double actual, double wanted) {
        return wanted == 0 ? actual == 0 : actual != 0 && Double.isFinite(actual)
                && Math.abs(actual-wanted) <= Math.max(Math.abs(wanted)*1e-9,8*Math.ulp(wanted));
    }
    final class Planned implements Access {
        private final Access source;
        private final ResourceTransaction transaction;
        private double working;
        Planned(Access source, ResourceTransaction transaction) {
            this.source = source; this.transaction = transaction; working = source.amount();
            Object snapshot = source.snapshot();
            transaction.enlist(() -> source.unchanged(snapshot), () -> source.restore(snapshot));
        }
        public Object identity() { return source.identity(); }
        public double amount() { return working; }
        public double minimum() { return source.minimum(); }
        public double maximum() { return source.maximum(); }
        public double volume() { return source.volume(); }
        public boolean valid() { return source.valid(); }
        public double move(double delta, boolean simulate) {
            if (!Double.isFinite(delta) || !valid()) return 0;
            double allowed = delta >= 0 ? Math.max(0,Math.min(delta, maximum() - working)) : -Math.max(0,Math.min(-delta, working - minimum()));
            double moved = allowed;
            if (moved != 0 && !movementMatches((working+moved)-working,moved)) return 0;
            if (!simulate && moved != 0) {
                double expected = working;
                working += moved;
                transaction.record(() -> source.valid() && same(source.amount(), expected)
                        && same(source.move(moved, false), moved) && same(source.amount(), expected + moved)
                        && movementMatches(source.amount()-expected,moved));
            }
            return moved;
        }
    }
}
