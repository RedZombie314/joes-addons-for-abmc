package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

/**
 * 音符方块宇宙传送门方块。
 *
 * 复用下界传送门的全部机制（站立动画、进入判定、传送延误、薄片模型），仅替换：
 *  - 生成：由 {@link BaseFireBlockMixin} 在音符盒框架内点燃火时创建；
 *  - 传送目标：主世界 <-> 音符方块宇宙，坐标按 8:1 换算（音符宇宙 8 格 = 主世界 1 格）；
 *  - 目标端传送门的查找/创建：照搬原版逻辑（见 {@link NotePortalForcer}），
 *    会优先复用最近的现有传送门，找不到则在合适高度（地表/开阔处）创建新传送门；
 *  - 框架检测：框架必须是音符盒，缺少框架时本方块自动消失。
 */
public class NotePortalBlock extends NetherPortalBlock {

    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(NotePortalBlock::new);

    public NotePortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        Direction.Axis facingAxis = facing.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean flag = portalAxis != facingAxis && facingAxis.isHorizontal();
        // 与下界传送门一致：若相邻方块不再是本传送门且所在框架不再完整，则移除本方块
        return !flag && !facingState.is(this) && !new NotePortalShape(level, currentPos, portalAxis).isComplete()
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 不模仿下界传送门在门口刷僵尸猪灵
    }

    @Override
    @Nullable
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        boolean toNote;
        if (level.dimension() == Level.OVERWORLD) {
            toNote = true;
        } else if (level.dimension() == ModDimensions.NOTE_DIM_LEVEL) {
            toNote = false;
        } else {
            return null; // 该传送门只在主世界与音符宇宙之间往返
        }

        ResourceKey<Level> targetKey = toNote ? ModDimensions.NOTE_DIM_LEVEL : Level.OVERWORLD;
        ServerLevel targetLevel = level.getServer().getLevel(targetKey);
        if (targetLevel == null) {
            return null;
        }

        boolean isNote = targetLevel.dimension() == ModDimensions.NOTE_DIM_LEVEL;
        WorldBorder worldborder = targetLevel.getWorldBorder();
        // 8:1 坐标换算：主世界 -> 音符宇宙 (*8)，音符宇宙 -> 主世界 (/8)
        double d0 = toNote ? 8.0 : (1.0 / 8.0);
        BlockPos exitPos = worldborder.clampToBounds(entity.getX() * d0, entity.getY(), entity.getZ() * d0);
        return this.getExitPortal(targetLevel, entity, pos, exitPos, isNote, worldborder);
    }

    @Nullable
    private DimensionTransition getExitPortal(
        ServerLevel level, Entity entity, BlockPos pos, BlockPos exitPos, boolean isNote, WorldBorder worldBorder
    ) {
        NotePortalForcer forcer = new NotePortalForcer(level);
        Optional<BlockPos> optional = forcer.findClosestPortalPosition(exitPos, isNote, worldBorder);
        BlockUtil.FoundRectangle rectangle;
        DimensionTransition.PostDimensionTransition postTransition;
        if (optional.isPresent()) {
            BlockPos foundPos = optional.get();
            BlockState blockstate = level.getBlockState(foundPos);
            rectangle = BlockUtil.getLargestRectangleAround(
                foundPos,
                blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                NotePortalShape.MAX_WIDTH,
                Direction.Axis.Y,
                NotePortalShape.MAX_HEIGHT,
                p_351970_ -> level.getBlockState(p_351970_) == blockstate
            );
            postTransition = DimensionTransition.PLAY_PORTAL_SOUND.then(p_351967_ -> p_351967_.placePortalTicket(foundPos));
        } else {
            Direction.Axis direction$axis = entity.level().getBlockState(pos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = forcer.createPortal(exitPos, direction$axis);
            if (optional1.isEmpty()) {
                return null; // 目标可能超出世界边界，无法创建
            }
            rectangle = optional1.get();
            postTransition = DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET);
        }

        return getDimensionTransitionFromExit(entity, pos, rectangle, level, postTransition);
    }

    private static DimensionTransition getDimensionTransitionFromExit(
        Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle, ServerLevel level, DimensionTransition.PostDimensionTransition postTransition
    ) {
        BlockState blockstate = entity.level().getBlockState(pos);
        Direction.Axis direction$axis;
        Vec3 vec3;
        if (blockstate.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            direction$axis = blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle sourceRect = BlockUtil.getLargestRectangleAround(
                pos, direction$axis, NotePortalShape.MAX_WIDTH, Direction.Axis.Y, NotePortalShape.MAX_HEIGHT,
                p_351016_ -> entity.level().getBlockState(p_351016_) == blockstate
            );
            vec3 = entity.getRelativePortalPosition(direction$axis, sourceRect);
        } else {
            direction$axis = Direction.Axis.X;
            vec3 = new Vec3(0.5, 0.0, 0.0);
        }

        return createDimensionTransition(
            level, rectangle, direction$axis, vec3, entity, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), postTransition
        );
    }

    /**
     * 按原版算法计算实体在目标传送门内的落点：测量原门相对位置并按两端尺寸比例映射到目标门，
     * 轴向不一致时绕 Y 轴旋转 90°，最后寻找无碰撞安全位置。
     */
    private static DimensionTransition createDimensionTransition(
        ServerLevel level,
        BlockUtil.FoundRectangle rectangle,
        Direction.Axis axis,
        Vec3 offset,
        Entity entity,
        Vec3 speed,
        float yRot,
        float xRot,
        DimensionTransition.PostDimensionTransition postTransition
    ) {
        BlockPos blockpos = rectangle.minCorner;
        BlockState blockstate = level.getBlockState(blockpos);
        Direction.Axis targetAxis = blockstate.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d0 = (double)rectangle.axis1Size;
        double d1 = (double)rectangle.axis2Size;
        EntityDimensions entitydimensions = entity.getDimensions(entity.getPose());
        int i = axis == targetAxis ? 0 : 90;
        Vec3 vec3 = axis == targetAxis ? speed : new Vec3(speed.z, speed.y, -speed.x);
        double d2 = (double)entitydimensions.width() / 2.0 + (d0 - (double)entitydimensions.width()) * offset.x();
        double d3 = (d1 - (double)entitydimensions.height()) * offset.y();
        double d4 = 0.5 + offset.z();
        boolean flag = targetAxis == Direction.Axis.X;
        Vec3 vec31 = new Vec3(
            (double)blockpos.getX() + (flag ? d2 : d4),
            (double)blockpos.getY() + d3,
            (double)blockpos.getZ() + (flag ? d4 : d2)
        );
        Vec3 vec32 = PortalShape.findCollisionFreePosition(vec31, level, entity, entitydimensions);
        return new DimensionTransition(level, vec32, vec3, yRot + (float)i, xRot, postTransition);
    }
}
