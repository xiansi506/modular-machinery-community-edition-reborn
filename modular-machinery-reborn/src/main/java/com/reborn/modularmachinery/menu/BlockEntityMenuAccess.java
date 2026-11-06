package com.reborn.modularmachinery.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A menu must keep addressing the same loaded entity, not just its former position. */
final class BlockEntityMenuAccess {
    private BlockEntityMenuAccess() {}

    static boolean stillValid(Player player, BlockEntity entity) {
        if (entity == null || entity.isRemoved() || entity.getLevel() != player.level()) return false;
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        return level != null && pos.closerToCenterThan(player.position(), 8.0)
                && level.hasChunkAt(pos) && level.getBlockEntity(pos) == entity;
    }
}
