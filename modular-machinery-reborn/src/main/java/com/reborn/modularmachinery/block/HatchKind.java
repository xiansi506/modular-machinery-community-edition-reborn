package com.reborn.modularmachinery.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.List;

/**
 * The six hatch families of the original mod, in the original input/output × item/fluid/energy split,
 * plus the two AE2 ME item buses and two Flux Networks wireless energy families. ME buses require AE2;
 * wireless energy hatches require Flux Networks. The ME buses are only registered when AE2 is
 * installed; the enum constants exist unconditionally so the rest of the code can talk about them
 * without loading any {@code appeng} class.
 *
 * <p>Each family owns exactly one registered block whose {@code size} property selects the tier,
 * mirroring the original {@code blockinputbus} / {@code blockoutputbus} / {@code blockenergyinputhatch}
 * / {@code blockenergyoutputhatch} / {@code blockfluidinputhatch} / {@code blockfluidoutputhatch}
 * layout. The block state property is created per family because the three ladders differ.
 */
public enum HatchKind implements StringRepresentable {

    ITEM_INPUT("item_input_hatch", HatchTier.ITEM_LADDER, true, Family.ITEM),
    ITEM_OUTPUT("item_output_hatch", HatchTier.ITEM_LADDER, false, Family.ITEM),
    FLUID_INPUT("fluid_input_hatch", HatchTier.FLUID_LADDER, true, Family.FLUID),
    FLUID_OUTPUT("fluid_output_hatch", HatchTier.FLUID_LADDER, false, Family.FLUID),
    ENERGY_INPUT("energy_input_hatch", HatchTier.ENERGY_LADDER, true, Family.ENERGY),
    ENERGY_OUTPUT("energy_output_hatch", HatchTier.ENERGY_LADDER, false, Family.ENERGY),

    /**
     * C4（0.73.0）：AE2 ME 物品输入总线。旧版一个方块、固定 16 个内部槽 + 16 个配置槽，没有 tier
     * 阶梯；这里的 {@link #ladder()} 只有 {@link HatchTier#ME_INPUT} 一项，只为复用仓口的方块状态
     * 与物品模型。只有装了 AE2 才注册这个方块。
     *
     * <p>0.73.2：方块状态的 {@code size} 属性必须同时允许两个 ME tier。Minecraft 的
     * {@code StateDefinition.Builder#add} 会拒绝「可能值只有一个」的属性
     * （{@code IllegalArgumentException: Block{...} attempted use property size with <= 1 possible values}），
     * 0.73.1 就是在这里启动崩溃的。{@link #ladder()} 仍然只有一项，物品与容量只看它；
     * 多出来的那个状态值由 {@code MeItemBusBlock#tier} 忽略。
     */
    ME_ITEM_INPUT("me_item_input_bus", List.of(HatchTier.ME_INPUT),
            List.of(HatchTier.ME_INPUT, HatchTier.ME_OUTPUT), true, Family.ITEM),

    /** C4（0.73.0）：AE2 ME 物品输出总线，固定 36 个输出槽；0.73.2 起状态属性同样允许两个 ME tier。 */
    ME_ITEM_OUTPUT("me_item_output_bus", List.of(HatchTier.ME_OUTPUT),
            List.of(HatchTier.ME_INPUT, HatchTier.ME_OUTPUT), false, Family.ITEM),

    WIRELESS_ENERGY_INPUT("wireless_energy_input_hatch", HatchTier.ENERGY_LADDER, true, Family.ENERGY),
    WIRELESS_ENERGY_OUTPUT("wireless_energy_output_hatch", HatchTier.ENERGY_LADDER, false, Family.ENERGY);

    /** Which storage a hatch of this family carries. */
    public enum Family {
        ITEM,
        FLUID,
        ENERGY
    }

    private final String id;
    private final List<HatchTier> ladder;
    private final boolean input;
    private final Family family;
    private final EnumProperty<HatchTier> sizeProperty;

    HatchKind(String id, List<HatchTier> ladder, boolean input, Family family) {
        this(id, ladder, ladder, input, family);
    }

    /**
     * @param stateValues the values the blockstate {@code size} property may take. For every family except
     *                    the ME buses this is the ladder itself. The ME buses have a one-entry ladder but need
     *                    a two-value property, because a blockstate property with a single possible value is
     *                    rejected at block construction (see {@link #ME_ITEM_INPUT}).
     */
    HatchKind(String id, List<HatchTier> ladder, List<HatchTier> stateValues, boolean input, Family family) {
        this.id = id;
        this.ladder = ladder;
        this.input = input;
        this.family = family;
        this.sizeProperty = EnumProperty.create("size", HatchTier.class,
                stateValues.toArray(new HatchTier[0]));
    }

    /** Registry path of the family block, e.g. {@code item_input_hatch}. */
    public String id() {
        return this.id;
    }

    /** The tiers this family actually has, in the original order. */
    public List<HatchTier> ladder() {
        return this.ladder;
    }

    /** {@code true} for the input side of a family, {@code false} for the output side. */
    public boolean isInput() {
        return this.input;
    }

    public Family family() {
        return this.family;
    }

    /**
     * 这个仓口能不能被玩家分到一个「输入组」（A2，勘测 §4 第 115 行）。
     *
     * <p>旧版只有两个家族回答 {@code true}：物品输入总线
     * （{@code TileItemInputBus.java:141-144}）与流体输入仓
     * （{@code base/TileFluidTank.java:144-147}，且仅 {@code IOType.INPUT}——输出仓继承同一个方法但
     * 那一半是 {@code false}）。输出仓与能量仓从来不可分组：旧版
     * {@code TileColorableMachineComponent#getGroupId} 对它们一律答 {@code -1}（通用）。
     *
     * <p>为什么这条规则住在枚举里而不是 UI 里：它同时决定「仓口能不能存组号」
     * （{@code MachineHatchBlockEntity#saveAdditional}）、「端口收集时带不带组号」
     * （{@code MachineControllerBlockEntity#collectHatches}）和「右键能不能打开分组界面」。
     * 三处如果各写一遍，迟早会有一处忘了改。
     */
    public boolean canGroupInput() {
        // C4：旧版 MEItemInputBus.canGroupInput() 返回 true（它继承 TileItemInputBus 的分组能力）。
        return this == ITEM_INPUT || this == FLUID_INPUT || this == ME_ITEM_INPUT;
    }

    /** C4（0.73.0）：这是不是两个 AE2 ME 物品总线之一。 */
    public boolean isMeBus() {
        return this == ME_ITEM_INPUT || this == ME_ITEM_OUTPUT;
    }

    public boolean isWirelessEnergy() {
        return this == WIRELESS_ENERGY_INPUT || this == WIRELESS_ENERGY_OUTPUT;
    }

    /** The {@code size} blockstate property for this family. */
    public EnumProperty<HatchTier> sizeProperty() {
        return this.sizeProperty;
    }

    /** Registry path of a single tier's block item, e.g. {@code item_input_hatch_tiny}. */
    public String itemId(HatchTier tier) {
        // The ME buses have exactly one tier and their block id is already the complete name; appending
        // "_me_input" would make the item id differ from the block id for no reason.
        if (isMeBus()) {
            return this.id;
        }
        return this.id + "_" + tier.getSerializedName();
    }

    /** Translation key of a single tier's block item. */
    public String itemTranslationKey(HatchTier tier) {
        return "block.modular_machinery_reborn." + itemId(tier);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
