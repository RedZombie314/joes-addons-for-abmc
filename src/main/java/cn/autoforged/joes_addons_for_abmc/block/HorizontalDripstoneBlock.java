package cn.autoforged.joes_addons_for_abmc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * 横向滴水石锥。
 *
 * 与原版滴水石锥（PointedDripstoneBlock）使用相同模型与贴图，但朝向变为东南西北四个水平方向，
 * 因此只能放置在方块侧面上。
 *
 * 与原版不同：
 * - 不具备往炼药锅里滴入水/岩浆的能力（本类完全不实现滴水逻辑）。
 * - 附着在墙面上时不会因失去支撑而变成下落的方块（无 Fallable 逻辑）。
 * - 仍具备伤害生物的能力：任何生物以一定横向速度撞上对应方向的石锥时，
 *   会造成“动能伤害”，公式为 (相对于该方向的速度 - 0.5) * 2，
 *   伤害类型与鞘翅滑翔撞墙（flyIntoWall）一致。
 */
public class HorizontalDripstoneBlock extends DirectionalBlock {
    public static final MapCodec<HorizontalDripstoneBlock> CODEC = simpleCodec(HorizontalDripstoneBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 沿朝向方向延伸的尖锥形碰撞盒（占据格内，尖端朝向 direction 所指方向）
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 15.0),
        Direction.SOUTH, Block.box(4.0, 4.0, 1.0, 12.0, 12.0, 16.0),
        Direction.WEST, Block.box(0.0, 4.0, 4.0, 15.0, 12.0, 12.0),
        Direction.EAST, Block.box(1.0, 4.0, 4.0, 16.0, 12.0, 12.0)
    );

    public HorizontalDripstoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        // 只能放置在方块侧面（上下不可放置）
        if (clickedFace.getAxis().isVertical()) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected BlockState updateShape(
        BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos
    ) {
        // 失去支撑时不会变成下落方块，而是安排一个刻将其破坏（掉落自身）
        if (!state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 2);
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Vec3 velocity = entity.getDeltaMovement();
        // 相对于石锥朝向的横向相对速度：朝石锥所指方向运动为正（撞上该方向石锥）
        double speed = -(velocity.x * facing.getStepX() + velocity.z * facing.getStepZ());
        if (speed > 0.5) {
            float damage = (float) ((speed - 0.5) * 2.0);
            // 与鞘翅滑翔撞墙伤害同一个伤害类型
            entity.hurt(level.damageSources().flyIntoWall(), damage);
        }
    }
}