package com.reborn.modularmachinery.block;

import com.reborn.modularmachinery.compat.ae2.Ae2ItemBusEntityFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;

/**
 * C4（0.73.0）：AE2 ME 物品输入/输出总线方块。
 *
 * <p>和普通仓口一样，一个方块家族只带一个 {@code size} 属性；ME 家族的阶梯只有一项
 * （{@link HatchTier#ME_INPUT} 或 {@link HatchTier#ME_OUTPUT}），只为复用仓口的方块状态、
 * 物品模型与右键打开菜单的路径。方块实体由 {@link Ae2ItemBusEntityFactory} 创建，
 * 所以本类不 import 任何 {@code appeng} 类型。
 *
 * <p>为什么是两个嵌套类而不是一个类加一个静态 {@code SIZE}：
 * {@code createBlockStateDefinition} 在 {@link Block} 构造器里执行，早于实例字段赋值，
 * 所以属性必须是具体类的静态常量；而输入/输出两个 {@link HatchKind} 的
 * {@code sizeProperty()} 是**不同实例**，各自只该放自己的 tier。
 *
 * <p>0.73.2：这两个属性现在都允许 {@link HatchTier#ME_INPUT} 与 {@link HatchTier#ME_OUTPUT}
 * 两个值。不是语义需要，而是 Minecraft 拒绝「可能值只有一个」的方块状态属性
 * （{@code StateDefinition.Builder#add} 会抛 {@code IllegalArgumentException}）——0.73.1 的启动崩溃
 * 正是这个原因。真正的 tier 由 {@link #tier(BlockState)} 固定为家族阶梯里的唯一项，状态值只用于
 * 让属性合法、让物品放置有状态可写。
 */
public abstract class MeItemBusBlock extends MachineHatchBlock {

    protected MeItemBusBlock(HatchKind kind, Properties properties) {
        super(kind, properties);
        if (!kind.isMeBus()) {
            throw new IllegalArgumentException("MeItemBusBlock needs a ME bus kind, got " + kind);
        }
    }

    /**
     * 0.73.2：ME 总线的 tier 永远是这个家族阶梯里的唯一项，不读方块状态。
     *
     * <p>方块状态属性为了通过 {@code StateDefinition.Builder#add} 的「至少两个可能值」检查，
     * 必须同时列出两个 ME tier（见类注释）。若放任 {@code state.getValue(SIZE)} 决定容量，
     * 一条 {@code /setblock ... [size=me_output]} 就能让输入总线拿到 36 个槽。这里把状态值降级为
     * 只用于「属性合法/物品放置写值」，容量仍由 {@link #kind()} 决定。
     */
    @Override
    public HatchTier tier(BlockState state) {
        return kind().ladder().get(0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Ae2ItemBusEntityFactory.create(pos, state);
    }

    /** 16 个配置槽 + 16 个内部槽的输入总线。 */
    public static final class Input extends MeItemBusBlock {

        public static final EnumProperty<HatchTier> SIZE = HatchKind.ME_ITEM_INPUT.sizeProperty();

        public Input(Properties properties) {
            super(HatchKind.ME_ITEM_INPUT, properties);
            registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.ME_INPUT));
        }

        @Override
        public EnumProperty<HatchTier> sizeProperty() {
            return SIZE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE);
        }
    }

    /** 36 个输出槽的输出总线。 */
    public static final class Output extends MeItemBusBlock {

        public static final EnumProperty<HatchTier> SIZE = HatchKind.ME_ITEM_OUTPUT.sizeProperty();

        public Output(Properties properties) {
            super(HatchKind.ME_ITEM_OUTPUT, properties);
            registerDefaultState(defaultBlockState().setValue(SIZE, HatchTier.ME_OUTPUT));
        }

        @Override
        public EnumProperty<HatchTier> sizeProperty() {
            return SIZE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE);
        }
    }
}
