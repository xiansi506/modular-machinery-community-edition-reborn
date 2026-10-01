package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.machine.ParallelController;
import com.reborn.modularmachinery.menu.ParallelControllerMenu;
import com.reborn.modularmachinery.menu.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

/**
 * The parallel controller's block entity — the original {@code TileParallelController} (82 lines).
 *
 * <p>Two integers, exactly as the original had them, with the original's NBT keys
 * ({@code TileParallelController.java:33-51} writes {@code maxParallelism} and {@code parallelism}):
 *
 * <ul>
 *   <li>{@link #maxParallelism()} — the tier's ceiling, read from the blockstate at construction and then from
 *       NBT. This is what the screen prints as "最大并行数".</li>
 *   <li>{@link #parallelism()} — the value the player dials in, which is what the machine actually adds. A newly
 *       placed controller starts <b>at its maximum</b>, which is the original's constructor
 *       ({@code :18-21}: {@code this.parallelism = maxParallelism;}).</li>
 * </ul>
 *
 * <p>Both values are mirrored to the screen through the menu's {@code ContainerData}, following the pattern
 * 0.19.0 established for the machine controller (indices 6–9); no packet is used for that direction. The write
 * direction does need a packet, and the reason is recorded in {@link com.reborn.modularmachinery.network.ModNetwork}.
 */
public final class ParallelControllerBlockEntity extends BlockEntity implements MenuProvider, ParallelController {

    /** The original's NBT key. */
    public static final String TAG_MAX_PARALLELISM = "maxParallelism";
    /** The original's NBT key. */
    public static final String TAG_PARALLELISM = "parallelism";

    private final ParallelControllerTier tier;

    private int maxParallelism;
    private int parallelism;

    public ParallelControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PARALLEL_CONTROLLER_ENTITY.get(), pos, state);
        this.tier = state.hasProperty(ParallelControllerBlock.TYPE)
                ? state.getValue(ParallelControllerBlock.TYPE) : ParallelControllerTier.NORMAL;
        // The original read the tier's value out of the blockstate when it created the tile entity
        // (BlockParallelController.java:126-128) and defaulted parallelism to it.
        this.maxParallelism = Math.max(1, this.tier.maxParallelism());
        this.parallelism = this.maxParallelism;
    }

    public ParallelControllerTier tier() {
        return this.tier;
    }

    @Override
    public int maxParallelism() {
        return this.maxParallelism;
    }

    @Override
    public int parallelism() {
        return this.parallelism;
    }

    /**
     * Sets the value the machine adds, clamped to {@code [0, maxParallelism]}.
     *
     * <p>The same clamp the original applied twice: once on the client before sending
     * ({@code GuiContainerParallelController.java:178-190}) and once on the server before accepting
     * ({@code PktParallelControllerUpdate.java:45}). {@code 0} is legal and means "this controller contributes
     * nothing".
     */
    public void setParallelism(int value) {
        int clamped = clampParallelism(value, this.maxParallelism);
        if (clamped != this.parallelism) {
            this.parallelism = clamped;
            setChanged();
        }
    }

    /**
     * The clamp itself, as a pure function so the offline acceptance harness can assert it without a world.
     */
    public static int clampParallelism(int value, int maxParallelism) {
        return Math.max(0, Math.min(Math.max(0, maxParallelism), value));
    }

    public void open(Player player) {
        NetworkHooks.openScreen((ServerPlayer) player, this, worldPosition);
    }

    // ------------------------------------------------------- menu data

    /**
     * Client-side mirrors of the two numbers the screen prints. The block entity has no update tag — the block
     * never changes visually — so a container data slot is the only place these can live on the client, exactly
     * as with the machine controller's parallelism rows and the hatch screens' gauges.
     */
    private int clientMaxParallelism = -1;
    private int clientParallelism = -1;

    /**
     * One of the two values the screen shows. The server answers from the live fields; the client answers from
     * the mirror the menu writes through {@link #setMenuValue}.
     */
    public int menuValue(int index) {
        if (this.level != null && this.level.isClientSide) {
            return switch (index) {
                case 0 -> this.clientMaxParallelism < 0 ? this.maxParallelism : this.clientMaxParallelism;
                case 1 -> this.clientParallelism < 0 ? this.parallelism : this.clientParallelism;
                default -> 0;
            };
        }
        return switch (index) {
            case 0 -> this.maxParallelism;
            case 1 -> this.parallelism;
            default -> 0;
        };
    }

    public void setMenuValue(int index, int value) {
        switch (index) {
            case 0 -> this.clientMaxParallelism = Math.max(1, value);
            case 1 -> this.clientParallelism = Math.max(0, value);
            default -> { }
        }
    }

    // ------------------------------------------------------- screen

    /**
     * The window title. A container screen shows no title of its own, so this reuses the panel's own heading key
     * rather than spending a language key that would never be drawn.
     */
    @Override
    public Component getDisplayName() {
        return Component.translatable(ParallelControllerMenu.TITLE_KEY);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ParallelControllerMenu(id, inventory, this);
    }

    // ------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_MAX_PARALLELISM, this.maxParallelism);
        tag.putInt(TAG_PARALLELISM, this.parallelism);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // The original only overwrote maxParallelism when the key was present, and clamped parallelism down to
        // it (TileParallelController.java:35-43). The floor at 1 and the floor at 0 are added here: a hand-edited
        // NBT tag should not be able to produce a controller that reports a negative contribution.
        if (tag.contains(TAG_MAX_PARALLELISM)) {
            this.maxParallelism = Math.max(1, tag.getInt(TAG_MAX_PARALLELISM));
        }
        this.parallelism = tag.contains(TAG_PARALLELISM)
                ? Math.max(0, Math.min(this.maxParallelism, tag.getInt(TAG_PARALLELISM)))
                : this.maxParallelism;
    }
}
