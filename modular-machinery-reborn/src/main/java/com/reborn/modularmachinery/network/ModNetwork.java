package com.reborn.modularmachinery.network;

import com.reborn.modularmachinery.ModularMachineryReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import java.util.Optional;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The project's first custom network channel.
 *
 * <h2>Why a packet is needed at all</h2>
 *
 * <p>The scoping document recommended the menu route instead (M6d row, §5.1), and everything it can carry
 * <b>does</b> travel through the menu here: the controller's ceiling and current value reach the screen as
 * {@code ContainerData} in {@code ParallelControllerMenu}, exactly like the machine controller's parallelism
 * rows added in 0.19.0.
 *
 * <p>The divergence is only in the <b>other</b> direction, and it is forced by the wire format of the vanilla
 * menu channel. {@code ServerboundContainerButtonClickPacket} — the only vanilla client → server hook a menu
 * offers — encodes <em>both</em> of its fields as a single byte:
 *
 * <pre>
 * ServerboundContainerButtonClickPacket(FriendlyByteBuf buf):
 *     readByte()   // container id
 *     readByte()   // button id
 * </pre>
 *
 * <p>verified with {@code javap -c -p} against {@code joined-1.20.1-20230612.114412-srg.jar}. A parallel
 * controller's ceiling is 512 by default and {@code max-parallelism} runs to 2048 and beyond, so the original's
 * "send the absolute value the player typed" ({@code GuiContainerParallelController.java:178-190}) cannot be
 * expressed in one signed byte. Sending deltas instead would need a multi-click encoding with server-side
 * reassembly state, which is strictly worse than one varint message.
 *
 * <p>So: <b>menu {@code ContainerData} for server → client, one packet for client → server</b>.
 */
public final class ModNetwork {

    // 0.109.0: provider actions and bounded buffer previews require both peers to update together.
    // 0.118.5: magic requirement bodies include the IO prefix consumed by the common recipe reader.
    // 0.119.0: compressed-air requirements and two additional optional hatch families.
    // 0.119.1: selection snapshots carry a dimension; generated NBT snapshots retain literal tag types.
    // 0.120.0: server-authoritative authoring actions and paged export previews.
    // 0.121.0: recipe editor validation tickets and bounded draft/export packets.
    // 0.121.59: lossless int menu slots and field-specific hatch group operations require both peers.
    private static final String PROTOCOL_VERSION = "13";
    private static final int ID_PARALLEL_CONTROLLER_UPDATE = 0;
    private static final int ID_SMART_INTERFACE_UPDATE = 1;
    private static final int ID_SELECTION_SYNC = 2;
    private static final int ID_MACHINE_DEFINITION_SYNC = 3;
    private static final int ID_HATCH_GROUP_UPDATE = 4;
    private static final int ID_CLIPBOARD_COPY = 5;
    private static final int ID_ME_FLUID_CONFIG = 6;
    private static final int ID_ME_PATTERN = 7;
    private static final int ID_ME_PATTERN_SYNC = 8;
    private static final int ID_ME_PATTERN_PROVIDER_ACTION = 9;
    private static final int ID_PROJECTOR_REQUEST = 10;
    private static final int ID_PROJECTOR_RESPONSE = 11;
    private static final int ID_CONSTRUCT_ACTION = 12;
    private static final int ID_CONSTRUCT_STATE = 13;
    private static final int ID_RECIPE_EDITOR_ACTION = 14;
    private static final int ID_RECIPE_EDITOR_STATE = 15;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModularMachineryReborn.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(ID_PARALLEL_CONTROLLER_UPDATE, ParallelControllerUpdatePacket.class,
                ParallelControllerUpdatePacket::encode,
                ParallelControllerUpdatePacket::new,
                ParallelControllerUpdatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_SMART_INTERFACE_UPDATE, SmartInterfaceUpdatePacket.class,
                SmartInterfaceUpdatePacket::encode,
                SmartInterfaceUpdatePacket::new,
                SmartInterfaceUpdatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_SELECTION_SYNC, SelectionSyncPacket.class,
                SelectionSyncPacket::encode,
                SelectionSyncPacket::new,
                SelectionSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_MACHINE_DEFINITION_SYNC, MachineDefinitionSyncPacket.class,
                MachineDefinitionSyncPacket::encode,
                MachineDefinitionSyncPacket::new,
                MachineDefinitionSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_HATCH_GROUP_UPDATE, HatchGroupUpdatePacket.class,
                HatchGroupUpdatePacket::encode,
                HatchGroupUpdatePacket::new,
                HatchGroupUpdatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_CLIPBOARD_COPY, ClipboardCopyPacket.class,
                ClipboardCopyPacket::encode,
                ClipboardCopyPacket::new,
                ClipboardCopyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_ME_FLUID_CONFIG, MeFluidConfigPacket.class,
                MeFluidConfigPacket::encode,
                MeFluidConfigPacket::new,
                MeFluidConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_ME_PATTERN, MePatternPacket.class,
                MePatternPacket::encode,
                MePatternPacket::new,
                MePatternPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_ME_PATTERN_SYNC, MePatternSyncPacket.class,
                MePatternSyncPacket::encode,
                MePatternSyncPacket::new,
                MePatternSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_ME_PATTERN_PROVIDER_ACTION, MePatternProviderActionPacket.class,
                MePatternProviderActionPacket::encode,
                MePatternProviderActionPacket::new,
                MePatternProviderActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_PROJECTOR_REQUEST, ProjectorRequestPacket.class,
                ProjectorRequestPacket::encode,
                ProjectorRequestPacket::new,
                ProjectorRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_PROJECTOR_RESPONSE, ProjectorResponsePacket.class,
                ProjectorResponsePacket::encode,
                ProjectorResponsePacket::new,
                ProjectorResponsePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_CONSTRUCT_ACTION, ConstructToolActionPacket.class, ConstructToolActionPacket::encode, ConstructToolActionPacket::new, ConstructToolActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_CONSTRUCT_STATE, ConstructToolStatePacket.class, ConstructToolStatePacket::encode, ConstructToolStatePacket::new, ConstructToolStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(ID_RECIPE_EDITOR_ACTION, RecipeEditorActionPacket.class, RecipeEditorActionPacket::encode, RecipeEditorActionPacket::new, RecipeEditorActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(ID_RECIPE_EDITOR_STATE, RecipeEditorStatePacket.class, RecipeEditorStatePacket::encode, RecipeEditorStatePacket::new, RecipeEditorStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private ModNetwork() {
    }
}
