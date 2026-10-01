package com.reborn.modularmachinery.item;

import com.mojang.logging.LogUtils;
import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.block.MachineControllerBlock;
import com.reborn.modularmachinery.machine.MachineDirectory;
import com.reborn.modularmachinery.network.SelectionSyncPacket;
import com.reborn.modularmachinery.selection.MachineFragmentWriter;
import com.reborn.modularmachinery.selection.SelectionExport;
import com.reborn.modularmachinery.selection.ServerSelections;
import com.reborn.modularmachinery.selection.StructureSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The construct tool ({@code itemconstructtool}): the original's {@code ItemConstructTool} (70 lines), which is a
 * <b>selection-to-JSON tool and not a builder</b>.
 *
 * <h2>What the original does, with line references</h2>
 *
 * <p>There is exactly one handler, {@code onItemUse} — a right click ({@code ItemConstructTool.java:53-68}).
 * Repository-wide searches for {@code onLeftClick}, {@code LeftClick}, {@code attackEntity},
 * {@code setBlockState}, {@code buildStructure} and {@code placeStructure} find nothing for this item: the tool
 * <b>never places a block</b>, and there is no "build" half to port. What it does:
 *
 * <ol>
 *   <li>Gate ({@code :54}): server side, {@code player.isCreative()}, and
 *       {@code server.getPlayerList().canSendCommands(player.getGameProfile())}. Nothing else — no permission
 *       level for the machine, no blueprint, no limit on how many positions may be selected.</li>
 *   <li>Clicked block is a {@code BlockController} ({@code :57-61}): finalize. The selection is compressed to
 *       offsets relative to that controller, rotated until the controller faces north, and written to
 *       {@code machine-<player>-<timestamp>.json} in the machinery directory as a {@code {"parts": …}} fragment;
 *       then the selection is purged and the now-empty selection is synced to the client.</li>
 *   <li>Any other block ({@code :63-64}): toggle that position in the selection and sync it.</li>
 *   <li>Returns {@code EnumActionResult.SUCCESS} unconditionally ({@code :67}).</li>
 * </ol>
 *
 * <h2>Three translations this class has to make, and why</h2>
 *
 * <ol>
 *   <li><b>{@code canSendCommands} → {@code hasPermissions(2)}.</b> 1.12.2's
 *       {@code PlayerList#canSendCommands(GameProfile)} is the "may this player use commands" hook; its body was
 *       not available to read here, so the check is written against 1.20.1's own equivalent, the standard command
 *       permission level 2 ({@code Entity#hasPermissions(int)}, verified present with {@code javap}). Both
 *       candidate readings (op-list membership, or the level-2 permission) answer {@code true} for an ordinary
 *       operator in creative, which is the case the tool exists for. Recorded as D21.</li>
 *   <li><b>Returning {@code PASS} when the gate refuses.</b> 1.20.1 asks the item <i>before</i> the block
 *       ({@code PlayerInteractionManager#useItemOn}), so returning success unconditionally would stop a survival
 *       player from opening a controller's own GUI — which 1.12.2, where the block was asked first, did not do.
 *       Refusing with {@code PASS} is what keeps the original's <i>effective</i> behaviour.</li>
 *   <li><b>One log line the original does not have.</b> The fragment has no {@code registryname} on purpose (see
 *       {@link SelectionExport#toMachineJson}), so this project's loader logs a failure for it while it sits in
 *       the machinery directory — the original logged one too. Rather than leave the author with a scary error
 *       and no next step, the write says in the log what the file is and what to do with it, warns when
 *       positions had to be skipped, and warns when the fragment is very large. Warnings only: nothing is
 *       refused, and the file's bytes are exactly what the original would have written. Recorded as D21.</li>
 * </ol>
 */
public final class ConstructToolItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * From here up, a fragment is worth a warning in the log. Not a cap — the original has none and this refuses
     * nothing — just a line saying how big the file is about to get. 4096 parts is a 64x64 floor.
     */
    private static final int LARGE_FRAGMENT = 4096;

    public ConstructToolItem(Properties properties) {
        super(properties);
    }

    /**
     * The original's gate ({@code ItemConstructTool.java:54}) as pure arithmetic, so the truth table can be
     * asserted offline: the <b>server</b> side, creative mode, and permission to use commands — all three.
     */
    public static boolean maySelect(boolean clientSide, boolean creative, boolean mayUseCommands) {
        return !clientSide && creative && mayUseCommands;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // The click is the tool's on both sides, exactly as the original's unconditional SUCCESS.
            return InteractionResult.SUCCESS;
        }
        // javap-verified: Entity#hasPermissions(int) is 1.20.1's command-permission check (see the class comment).
        if (!maySelect(false, player.isCreative(), player.hasPermissions(2))) {
            // The block keeps its own action: see translation (2) in the class comment.
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MachineControllerBlock) {
            finalizeSelection(serverPlayer, level, pos, state.getValue(MachineControllerBlock.FACING));
            // The original's own order (:60-61): purge, then sync the (now empty) selection.
            ServerSelections.purge(serverPlayer);
            SelectionSyncPacket.sendTo(serverPlayer, List.of());
        } else {
            StructureSelection selection = ServerSelections.toggle(serverPlayer, pos);
            SelectionSyncPacket.sendTo(serverPlayer, selection.positions());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The original's {@code finalizeSelection} ({@code PlayerStructureSelectionHelper.java:77-126}): messages
     * first, then the fragment, then the file.
     */
    private static void finalizeSelection(ServerPlayer player, Level level, BlockPos controller, Direction facing) {
        StructureSelection selection = ServerSelections.get(player);
        if (selection.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.structurebuild.empty"), false);
            return;
        }
        player.displayClientMessage(Component.translatable("message.structurebuild.confirmrotation",
                facing.getName()), false);
        int steps = SelectionExport.rotationSteps(facing);
        if (steps != 0) {
            player.displayClientMessage(Component.translatable("message.structurebuild.confirmrotation.rotating",
                    String.valueOf(steps * 90)), false);
        }

        SelectionExport.ExportPlan plan = SelectionExport.plan(level, selection.positions(), controller, facing);
        if (plan.unavailable() > 0) {
            LOGGER.warn("[{}] {} of {} selected position(s) were left out of the fragment: a chunk without them "
                            + "loaded is skipped rather than read as air, and a block with no registry name cannot "
                            + "be named in a definition. The fragment describes {} of them.",
                    ModularMachineryReborn.MOD_ID, plan.unavailable(), plan.selected(), plan.parts().size());
        }
        if (plan.parts().size() >= LARGE_FRAGMENT) {
            LOGGER.warn("[{}] This fragment has {} parts; the file will be large. Nothing is refused — the "
                            + "original has no size limit either — but consider exporting a smaller selection.",
                    ModularMachineryReborn.MOD_ID, plan.parts().size());
        }
        String json = SelectionExport.toMachineJson(plan.parts());

        MinecraftServer server = player.getServer();
        if (server != null && server.isDedicatedServer()) {
            player.displayClientMessage(Component.translatable("message.structurebuild.warndedicated"), false);
        }

        String fileName = MachineFragmentWriter.fileNameFor(player.getGameProfile().getName(), LocalDateTime.now());
        try {
            Path written = MachineFragmentWriter.write(MachineDirectory.directory(), fileName, json);
            player.displayClientMessage(Component.translatable("message.structurebuild.save",
                    written.getFileName().toString()), false);
            // The hint translation (3): the file is a fragment, and the loader will say so. Say the next step
            // here instead of leaving the author to find the loader's own complaint.
            LOGGER.info("[{}] Wrote a machine <fragment> to {}. It deliberately has no 'registryname', so the "
                            + "loader cannot read it as a machine: copy its \"parts\" array into a machine "
                            + "definition under data/<namespace>/machinery/ (or config/{}/machinery/) and give "
                            + "that definition a \"registryname\".",
                    ModularMachineryReborn.MOD_ID, written, ModularMachineryReborn.MOD_ID);
        } catch (IOException exception) {
            LOGGER.error("[{}] Could not save the machine fragment {}", ModularMachineryReborn.MOD_ID, fileName,
                    exception);
            player.displayClientMessage(Component.translatable("message.structurebuild.fail"), false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // The original's own tooltip line (ItemConstructTool.java:48-50), verbatim.
        tooltip.add(Component.translatable("tooltip.constructtool.creative").withStyle(ChatFormatting.GRAY));
    }
}
