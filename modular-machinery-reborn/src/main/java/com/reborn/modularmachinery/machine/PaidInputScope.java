package com.reborn.modularmachinery.machine;

import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Successful controller-tick consumption receipts, not a public finish/cancel event or optional-mod link. */
public final class PaidInputScope {
    private static final ThreadLocal<Map<Object, Receipt>> CURRENT = new ThreadLocal<>();
    private record Receipt(BooleanSupplier decreased, Consumer<MachineControllerBlockEntity> owner) {}
    private PaidInputScope() {}
    public static boolean capturing() { return CURRENT.get() != null; }
    public static void observe(Object source, BooleanSupplier decreased, Consumer<MachineControllerBlockEntity> owner) {
        Map<Object, Receipt> receipts = CURRENT.get();
        if (receipts != null) receipts.putIfAbsent(source, new Receipt(decreased, owner));
    }
    public static void run(MachineControllerBlockEntity controller, Runnable action) {
        Map<Object, Receipt> previous = CURRENT.get();
        Map<Object, Receipt> receipts = new IdentityHashMap<>();
        CURRENT.set(receipts);
        try {
            action.run();
            for (Receipt receipt : receipts.values()) if (receipt.decreased().getAsBoolean()) receipt.owner().accept(controller);
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
