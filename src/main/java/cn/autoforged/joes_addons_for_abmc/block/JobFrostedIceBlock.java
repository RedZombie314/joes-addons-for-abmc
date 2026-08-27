package cn.autoforged.joes_addons_for_abmc.block;

import java.util.UUID;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.JobFrostedIceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 冰块权杖生成的“霜冰”方块。
 * - 完全自实现（不继承原版 FrostedIceBlock），AGE 从 0~3 推进，还原原版 1/3 的 Age 增长概率；
 *   存在相邻水时必然加速融化。只通过 randomTick 还原原方块，绝不生成水。
 * - 保留完整碰撞箱（能困住生物），但不视为“阻塞/令人窒息”的方块（与玻璃一致），不会窒息生物。
 * - 无论是融化还是被破坏，都会还原为被替换前的原方块（保留其方块实体数据，如容器内容），
 *   并通知 {@link ModMain} 更新该生物的霜冰计数/伤害结算。
 */
public class JobFrostedIceBlock extends HalfTransparentBlock implements EntityBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, MAX_AGE);

    public JobFrostedIceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JobFrostedIceBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    // ============================ 融化逻辑（绝不生成水） ============================

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 存在相邻水时必然融化，否则以 1/3 概率推进 Age（还原原版概率）。
        if (random.nextInt(3) != 0 && !hasWaterNearby(level, pos)) {
            return;
        }
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
        } else {
            // 达到最大年龄即融化 → 还原原方块（不生成水）
            revert(level, pos, true);
        }
    }

    // 本霜冰不调度 tick，也绝不使用原版霜冰的“化成水”逻辑。
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        // 自实现方块：放置时不产生任何额外融化/水逻辑。
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
        // 自实现方块：邻接变化不产生水的生成。
    }

    // ============================ 还原原方块 ============================

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // 该方块被非本方块替换（被挖掘/爆炸/活塞等破坏），视为“非融化破坏”。
        if (!newState.is(this) && !level.isClientSide()) {
            boolean restored = revert((ServerLevel) level, pos, false);
            if (!restored) {
                // 无原方块可还原（异常情况）时，确保旧的霜冰方块实体被清掉。
                level.removeBlockEntity(pos);
            }
        }
        // 不调用 super：还原逻辑已自行处理方块实体（含还原容器内容），
        // 再调 super 可能在还原后又误删掉新生成的方块实体。
    }

    /**
     * 还原原方块，并把融化/破坏事件通知给 ModMain 的霜冰计数记录。
     * isMelt=true 表示最后是融化还原，false 表示被破坏（计数为“破坏”）。
     * 幂等：被 markConsumed 清空后，重复调用为无操作，返回 false 表示未还原。
     */
    private static boolean revert(ServerLevel level, BlockPos pos, boolean isMelt) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof JobFrostedIceBlockEntity fbe)) return false;
        if (!fbe.hasStoredOriginal()) return false;

        BlockState orig = fbe.getOriginalState();
        CompoundTag origData = fbe.getOriginalBlockEntityData();
        // 若原方块是水（如把水中生物冻住），还原时绝不重新生成水源，改还原为空气。
        if (orig.getFluidState().is(FluidTags.WATER)) {
            orig = Blocks.AIR.defaultBlockState();
            origData = null;
        }
        UUID trapped = fbe.getTrappedEntity();
        fbe.markConsumed();

        if (trapped != null) {
            ModMain.onFrostIceReverted(level, trapped, isMelt);
        }

        level.setBlock(pos, orig, Block.UPDATE_ALL);
        if (origData != null) {
            BlockEntity newBe = BlockEntity.loadStatic(pos, orig, origData, level.registryAccess());
            if (newBe != null) {
                level.setBlockEntity(newBe);
            }
        }
        return true;
    }

    // “不窒息生物”由 ModBlocks 中通过 BlockBehaviour.Properties.isSuffocating/isViewBlocking
    // 配置为 false 实现（1.21 中窒息判定基于方块属性谓词，而非覆写方块方法）。

    private static boolean hasWaterNearby(Level level, BlockPos pos) {
        for (BlockPos bp : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getFluidState(bp).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}