package com.reborn.modularmachinery.selection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The construct tool's export arithmetic: what the original's {@code PlayerStructureSelectionHelper} and
 * {@code BlockArray.serializeAsMachineJson} did, with everything that does not need a {@code Level} separated out
 * so it can be asserted offline.
 *
 * <h2>What the original does, with line references</h2>
 *
 * <ol>
 *   <li>{@code compressAsArray} ({@code PlayerStructureSelectionHelper.java:159-179}) turns each selected
 *       position into {@code pos.subtract(controllerPos)} with the block's state descriptor and, for a tile
 *       entity, its NBT with {@code x}/{@code y}/{@code z} removed.</li>
 *   <li>{@code finalizeSelection} ({@code :84-94}) then rotates that whole array counter-clockwise until the
 *       clicked controller faces north, counting 90° per step for the player's message.</li>
 *   <li>{@code serializeAsMachineJson} ({@code BlockArray.java:395-443}) writes the fragment: {@code parts} is the
 *       <b>only</b> root field, each part carries {@code x}, {@code y}, {@code z}, an optional {@code nbt}, and an
 *       {@code elements} array.</li>
 * </ol>
 *
 * <h2>Two deliberate translations of the block-state descriptor</h2>
 *
 * <p>The original wrote {@code ns:block@meta} ({@code BlockArray.java:422-424}), where the metadata is the whole
 * state. 1.20.1 has no metadata, so the faithful equivalent of "the state that was there" is <b>every property,
 * named</b>: {@code ns:block[facing=north,half=bottom,…]}. That is also exactly what this project's own
 * {@code BlockMatcher} reads, so the tool's output is accepted by the same schema a data pack uses (asserted in
 * harness section AA4). The properties are written in name order rather than in the block's own registration
 * order, so the file is stable across releases.
 *
 * <p>The {@code nbt} object's keys are sorted for the same reason: 1.20.1's {@code CompoundTag} is backed by a
 * {@code HashMap}, so its iteration order is not defined. JSON objects are unordered, so sorting changes nothing
 * but the file's readability.
 */
public final class SelectionExport {

    private SelectionExport() {
    }

    /**
     * How many counter-clockwise quarter turns take {@code facing} to north — the number of rotations the
     * original applied before writing ({@code PlayerStructureSelectionHelper.java:85-94}).
     *
     * <p>Vertical directions cannot occur: a controller's {@code FACING} is
     * {@code BlockStateProperties.HORIZONTAL_FACING}. The original's
     * {@code while (face != NORTH) { face = face.rotateYCCW(); } } would spin forever on one, since
     * {@code UP.rotateYCCW() == UP}; this returns 0 instead, which is a guard against a loop the original only
     * escaped because its controller could not face up either.
     */
    public static int rotationSteps(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return 0;
        }
        int steps = 0;
        Direction current = facing;
        while (current != Direction.NORTH) {
            current = current.getCounterClockWise();
            steps++;
        }
        return steps;
    }

    /**
     * One quarter turn counter-clockwise, exactly the original's {@code MiscUtils.rotateYCCW}:
     * {@code (x, y, z) -> (z, y, -x)}. The same rule as this project's
     * {@code MachinePattern.rotateYCounterClockwise}, which is what makes the fragment load unchanged.
     */
    public static BlockPos rotate(BlockPos pos, int steps) {
        BlockPos out = pos;
        for (int i = 0; i < Math.floorMod(steps, 4); i++) {
            out = new BlockPos(out.getZ(), out.getY(), -out.getX());
        }
        return out;
    }

    /**
     * The fragment's positions: each selected position relative to the controller, in the selection's own order,
     * rotated as if the controller faced north.
     *
     * <p>The controller's own position, if the player selected it too, becomes {@code (0,0,0)} and stays there —
     * the original kept it, and this project's loader drops it at load time
     * ({@code MachineSchema.java:231-234}, "the controller occupies the origin").
     */
    public static List<BlockPos> offsets(List<BlockPos> selection, BlockPos controller, Direction facing) {
        int steps = rotationSteps(facing);
        List<BlockPos> out = new ArrayList<>(selection.size());
        for (BlockPos pos : selection) {
            out.add(rotate(pos.subtract(controller), steps));
        }
        return List.copyOf(out);
    }

    /**
     * One part per position, dropping the ones whose sample is {@code null}, in order.
     *
     * @throws IllegalArgumentException when the two lists are different lengths — a caller that zipped them
     *                                  wrongly would otherwise write a fragment whose positions and blocks do
     *                                  not correspond, which is worse than a crash here
     */
    public static List<ExportPart> parts(List<BlockPos> offsets, List<BlockSample> samples) {
        if (offsets.size() != samples.size()) {
            throw new IllegalArgumentException("The construct tool was given " + offsets.size() + " position(s) "
                    + "but " + samples.size() + " sample(s); they are read pairwise, so they must correspond");
        }
        List<ExportPart> out = new ArrayList<>(offsets.size());
        for (int i = 0; i < offsets.size(); i++) {
            BlockSample sample = samples.get(i);
            if (sample == null) {
                continue;
            }
            out.add(new ExportPart(offsets.get(i), sample.descriptor(), sample.nbtJson()));
        }
        return List.copyOf(out);
    }

    /**
     * The descriptor a machine definition writes for one block state: the block's registry name plus every
     * property, in name order.
     *
     * <p>Uses {@code BuiltInRegistries.BLOCK} rather than {@code ForgeRegistries.BLOCKS}: both name the same
     * registry at runtime, and the vanilla one is the one this project can also read offline, which is what makes
     * the round trip assertable (harness section AA's registry probe).
     *
     * @throws IllegalStateException when the block is not registered at all, in which case no descriptor could
     *                              ever match it. {@link #sampleAt} turns that into "position unavailable"
     */
    public static String describe(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            throw new IllegalStateException("The block " + state.getBlock() + " has no registry name, so no "
                    + "descriptor could be written for it");
        }
        StringBuilder out = new StringBuilder(id.toString());
        List<Property<?>> properties = new ArrayList<>(state.getProperties());
        if (properties.isEmpty()) {
            return out.toString();
        }
        properties.sort(Comparator.comparing(Property::getName));
        out.append('[');
        for (int i = 0; i < properties.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            appendProperty(out, state, properties.get(i));
        }
        return out.append(']').toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void appendProperty(StringBuilder out, BlockState state, Property<?> property) {
        out.append(property.getName()).append('=');
        out.append(((Property) property).getName(state.getValue((Property) property)));
    }

    /** The original removed the coordinates a tile entity writes about itself ({@code :170-172}). */
    public static CompoundTag withoutCoordinates(CompoundTag tag) {
        CompoundTag out = tag.copy();
        out.remove("x");
        out.remove("y");
        out.remove("z");
        return out;
    }

    /** One NBT tag as JSON — the shape the original's {@code NBTJsonSerializer} wrote into {@code "nbt"}. */
    public static String nbtToJson(CompoundTag tag) {
        return toJson(tag).toString();
    }

    private static JsonElement toJson(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            JsonObject out = new JsonObject();
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            for (String key : keys) {
                out.add(key, toJson(compound.get(key)));
            }
            return out;
        }
        if (tag instanceof ListTag list) {
            JsonArray out = new JsonArray();
            for (int i = 0; i < list.size(); i++) {
                out.add(toJson(list.get(i)));
            }
            return out;
        }
        if (tag instanceof StringTag string) {
            return new JsonPrimitive(string.getAsString());
        }
        if (tag instanceof ByteTag number) {
            return new JsonPrimitive(number.getAsByte());
        }
        if (tag instanceof ShortTag number) {
            return new JsonPrimitive(number.getAsShort());
        }
        if (tag instanceof IntTag number) {
            return new JsonPrimitive(number.getAsInt());
        }
        if (tag instanceof LongTag number) {
            return new JsonPrimitive(number.getAsLong());
        }
        if (tag instanceof FloatTag number) {
            return new JsonPrimitive(number.getAsFloat());
        }
        if (tag instanceof DoubleTag number) {
            return new JsonPrimitive(number.getAsDouble());
        }
        if (tag instanceof ByteArrayTag array) {
            JsonArray out = new JsonArray();
            for (byte value : array.getAsByteArray()) {
                out.add(value);
            }
            return out;
        }
        if (tag instanceof IntArrayTag array) {
            JsonArray out = new JsonArray();
            for (int value : array.getAsIntArray()) {
                out.add(value);
            }
            return out;
        }
        if (tag instanceof LongArrayTag array) {
            JsonArray out = new JsonArray();
            for (long value : array.getAsLongArray()) {
                out.add(value);
            }
            return out;
        }
        // The end tag is the only other kind, and it cannot appear as a value.
        return JsonNull.INSTANCE;
    }

    /**
     * The machine-definition fragment, byte for byte as the original's {@code serializeAsMachineJson} wrote it:
     * four-space indents, the platform line separator, and {@code parts} as the only root field.
     *
     * <p><b>No {@code registryname}</b>: the original wrote a fragment for the author to paste into a definition,
     * not a definition, and this project's loader therefore logs a load failure for the file while it sits in the
     * machinery directory. That is the original's behaviour, kept deliberately — writing a {@code registryname}
     * would register a controller block for the file at the next launch, which is a different thing entirely.
     * {@code ConstructToolItem} says so in the log when it writes the file.
     *
     * <p>Every part carries exactly one element, because the tool reads exactly one state per position. (The
     * original's {@code elements} array could hold several samples for the preview's cycle-replaceable blocks;
     * nothing the construct tool reads produces more than one.)
     */
    public static String toMachineJson(List<ExportPart> parts) {
        String newline = System.lineSeparator();
        String move = "    ";
        StringBuilder out = new StringBuilder();
        out.append('{').append(newline);
        out.append(move).append("\"parts\": [").append(newline);
        for (int i = 0; i < parts.size(); i++) {
            ExportPart part = parts.get(i);
            BlockPos pos = part.position();
            out.append(move).append(move).append('{').append(newline);
            out.append(move).append(move).append(move).append("\"x\": ").append(pos.getX()).append(',')
                    .append(newline);
            out.append(move).append(move).append(move).append("\"y\": ").append(pos.getY()).append(',')
                    .append(newline);
            out.append(move).append(move).append(move).append("\"z\": ").append(pos.getZ()).append(',')
                    .append(newline);
            if (part.nbtJson() != null) {
                out.append(move).append(move).append(move).append("\"nbt\": ").append(part.nbtJson())
                        .append(',').append(newline);
            }
            out.append(move).append(move).append(move).append("\"elements\": [").append(newline);
            out.append(move).append(move).append(move).append(move).append('"').append(part.descriptor())
                    .append('"').append(newline);
            out.append(move).append(move).append(move).append(']').append(newline);
            out.append(move).append(move).append('}');
            if (i < parts.size() - 1) {
                out.append(',');
            }
            out.append(newline);
        }
        out.append(move).append(']');
        out.append('}');
        return out.toString();
    }

    // =====================================================================================================
    // The level-facing half: everything above is pure, everything below needs the world.
    // =====================================================================================================

    /**
     * What the tool is about to write, and what it had to leave out.
     *
     * @param parts       the fragment's entries, in selection order
     * @param selected    how many positions were selected
     * @param unavailable how many of them could not be read (see {@link #sampleAt})
     */
    public record ExportPlan(List<ExportPart> parts, int selected, int unavailable) {
    }

    /**
     * The whole export for one finalize click.
     *
     * <p><b>One deliberate divergence from the original.</b> {@code compressAsArray} called
     * {@code world.getBlockState(pos)} for every selected position; in 1.20.1 that method loads the chunk
     * ({@code Level.getChunkAt}), on the server thread, for a position the player may have selected minutes ago.
     * This project's structure matching already refuses to do that ({@code MachinePattern.matches} guards with
     * {@code level.isLoaded}), so a position in an unloaded chunk is <b>skipped and counted</b> rather than
     * silently read as air — a fragment that says {@code minecraft:air} at a position would be a wrong definition
     * that looks deliberate. The caller reports the count; the original had no such case to report because it
     * simply loaded the chunk.
     */
    public static ExportPlan plan(Level level, List<BlockPos> selection, BlockPos controller, Direction facing) {
        List<BlockSample> samples = new ArrayList<>(selection.size());
        int unavailable = 0;
        for (BlockPos pos : selection) {
            BlockSample sample = sampleAt(level, pos);
            if (sample == null) {
                unavailable++;
            }
            samples.add(sample);
        }
        return new ExportPlan(parts(offsets(selection, controller, facing), samples), selection.size(), unavailable);
    }

    /**
     * One position's block and tile entity, or {@code null} when it cannot be read: an unloaded chunk, or a block
     * with no registry name (neither of which can appear in a definition).
     *
     * <p>The NBT source is {@code BlockEntity.saveWithoutMetadata()}, the 1.20.1 counterpart of the original's
     * {@code te.writeToNBT(cmp)}, and the coordinates are removed exactly as the original removed them.
     */
    @Nullable
    public static BlockSample sampleAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        String descriptor;
        try {
            descriptor = describe(level.getBlockState(pos));
        } catch (IllegalStateException exception) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        String nbt = entity == null ? null : nbtToJson(withoutCoordinates(entity.saveWithoutMetadata()));
        return new BlockSample(descriptor, nbt);
    }
}
