package com.reborn.modularmachinery.menu;

import net.minecraft.world.inventory.ContainerData;

/** Menu-only wire view: selected logical ints use their original low slot and an appended high slot.
 * Raw received halves are retained independently of clamping in the logical setters/getters.
 * Existing small values and already segmented long values are passed through unchanged.
 * Each half arrives separately; this is lossless transport, not an atomic snapshot protocol.
 */
final class IntContainerData implements ContainerData {
    private final ContainerData logical;
    private final int[] indices;
    private final int[] received;

    IntContainerData(ContainerData logical, int... indices) {
        this.logical = logical;
        this.indices = indices.clone();
        this.received = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= logical.getCount()) throw new IllegalArgumentException("Invalid int slot");
            for (int j = 0; j < i; j++) if (indices[j] == indices[i]) throw new IllegalArgumentException("Duplicate int slot");
        }
    }

    @Override public int get(int index) {
        if (index >= logical.getCount()) return (logical.get(indices[index - logical.getCount()]) >>> 16) & 0xffff;
        for (int selected : indices) if (selected == index) return logical.get(index) & 0xffff;
        return logical.get(index);
    }

    @Override public void set(int index, int value) {
        for (int i = 0; i < indices.length; i++) {
            if (index == indices[i] || index == logical.getCount() + i) {
                received[i] = index == indices[i]
                        ? (received[i] & 0xffff0000) | (value & 0xffff)
                        : (received[i] & 0xffff) | ((value & 0xffff) << 16);
                logical.set(indices[i], received[i]);
                return;
            }
        }
        logical.set(index, value);
    }

    @Override public int getCount() { return logical.getCount() + indices.length; }
}
