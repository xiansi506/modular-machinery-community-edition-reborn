package com.reborn.modularmachinery.machine;

import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One accepted block of a machine structure.
 *
 * <p>The original matched on <b>block + metadata</b> ({@code BlockArray.BlockInformation.matchesState} compared
 * {@code block.equals(...)} and {@code meta == ...}). 1.20.1 has no metadata, so the equivalent unit is
 * <b>block + blockstate properties</b>:
 *
 * <ul>
 *   <li>{@code ns:path} — no properties given, matching the original's "any metadata variant" case, which for
 *       most blocks meant every variant. Here it accepts <b>any</b> state of the block. This is what makes
 *       {@code casings_all} accept every casing type.</li>
 *   <li>{@code ns:path[key=value,...]} — block plus the listed property constraints; unlisted properties are
 *       ignored, so it is a partial-state match.</li>
 * </ul>
 *
 * <p>1.12.2 {@code @meta} syntax is rejected with an explanatory error rather than silently mis-matched, since
 * there is no general metadata-to-property mapping. Ported definitions spell the variant out, e.g.
 * {@code modular_machinery_reborn:blockcasing[casing=reinforced]}.
 */
public final class BlockMatcher {

    private final Block block;
    private final Map<Property<?>, Comparable<?>> required;
    private final String descriptor;

    private BlockMatcher(Block block, Map<Property<?>, Comparable<?>> required, String descriptor) {
        this.block = block;
        this.required = Collections.unmodifiableMap(required);
        this.descriptor = descriptor;
    }

    public static BlockMatcher parse(String raw) {
        if (raw == null) {
            throw new JsonParseException("Null block descriptor in 'elements'");
        }
        String descriptor = raw.trim();
        if (descriptor.isEmpty()) {
            throw new JsonParseException("Empty block descriptor in 'elements'");
        }
        if (descriptor.indexOf('@') >= 0) {
            throw new JsonParseException("Block descriptor '" + raw + "' uses the 1.12.2 '@meta' syntax. "
                    + "Metadata does not exist in 1.20.1; spell the variant out with blockstate properties, "
                    + "for example minecraft:quartz_stairs[facing=east] or "
                    + "modular_machinery_reborn:blockcasing[casing=reinforced].");
        }

        String idPart = descriptor;
        String propsPart = null;
        int bracket = descriptor.indexOf('[');
        if (bracket >= 0) {
            if (!descriptor.endsWith("]")) {
                throw new JsonParseException("Unclosed '[' in block descriptor '" + raw + "'");
            }
            idPart = descriptor.substring(0, bracket).trim();
            propsPart = descriptor.substring(bracket + 1, descriptor.length() - 1);
        }

        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            throw new JsonParseException("Malformed block id '" + idPart + "' in descriptor '" + raw + "'");
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            throw new JsonParseException("Unknown block '" + id + "' in descriptor '" + raw + "'");
        }

        Map<Property<?>, Comparable<?>> required = new LinkedHashMap<>();
        if (propsPart != null && !propsPart.isBlank()) {
            for (String pair : propsPart.split(",")) {
                String[] split = pair.split("=", 2);
                if (split.length != 2) {
                    throw new JsonParseException("Expected property=value in '" + pair.trim()
                            + "' of descriptor '" + raw + "'");
                }
                String key = split[0].trim();
                String value = split[1].trim();
                Property<?> property = block.getStateDefinition().getProperty(key);
                if (property == null) {
                    throw new JsonParseException("Block '" + id + "' has no blockstate property '" + key + "'");
                }
                Comparable<?> parsed = property.getValue(value).orElseThrow(() -> new JsonParseException(
                        "Block '" + id + "' property '" + key + "' has no value '" + value + "'"));
                required.put(property, parsed);
            }
        }
        return new BlockMatcher(block, required, descriptor);
    }

    public boolean matches(BlockState state) {
        if (state.getBlock() != this.block) {
            return false;
        }
        for (Map.Entry<Property<?>, Comparable<?>> entry : this.required.entrySet()) {
            Property<?> property = entry.getKey();
            if (!state.hasProperty(property) || !state.getValue(property).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public Block block() {
        return this.block;
    }

    /**
     * The state a structure preview should draw for this matcher: the block's default state with every required
     * property applied.
     *
     * <p>Applying the properties is what makes a preview legible. A machine built out of
     * {@code blockcasing[casing=reinforced]} and {@code blockcasing[casing=firebox]} would otherwise be drawn as
     * the default casing at every position, and the structure would be a uniform slab.
     */
    public BlockState representativeState() {
        BlockState state = this.block.defaultBlockState();
        for (Map.Entry<Property<?>, Comparable<?>> entry : this.required.entrySet()) {
            state = apply(state, entry.getKey(), entry.getValue());
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState apply(BlockState state, Property<?> property, Comparable<?> value) {
        // The map was built by parsing each property's own value, so the pair is always type-compatible; the
        // generics just cannot express that.
        return state.hasProperty(property) ? state.setValue((Property) property, (Comparable) value) : state;
    }

    /** The descriptor exactly as it appeared in the machine definition. */
    public String descriptor() {
        return this.descriptor;
    }

    @Override
    public String toString() {
        return this.descriptor;
    }
}
