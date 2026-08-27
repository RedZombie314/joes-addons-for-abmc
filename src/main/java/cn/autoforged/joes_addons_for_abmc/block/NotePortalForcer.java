package cn.autoforged.joes_addons_for_abmc.block;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModPoiTypes;

/**
 * 音符传送门的查找/创建逻辑，照搬原版 {@link net.minecraft.world.level.portal.PortalForcer}。
 *
 * 行为与原版下界传送门完全一致：
 *  - 传送时优先在 POI 中查找最近的现有传送门（主世界 257×257、音符宇宙 33×33 方域，
 *    欧几里得距离最近，同距离取 Y 最低）；
 *  - 找不到时在水平 16 格内寻找合适位置创建传送门（长边朝向与原门一致，Y 使用地表高度，
 *    优先 4×3 开孔，退而求其次 4×1 开孔）；
 *  - 实在找不到时在目标坐标强制生成一个带音符盒平台的传送门（Y 限制在 70~高度上限-9）。
 */
public class NotePortalForcer {
    public static final int TICKET_RADIUS = 3;
    private static final int NETHER_PORTAL_RADIUS = 16;
    private static final int OVERWORLD_PORTAL_RADIUS = 128;
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_BOX = 3;
    private static final int FRAME_HEIGHT_START = -1;
    private static final int FRAME_HEIGHT_END = 4;
    private static final int FRAME_WIDTH_START = -1;
    private static final int FRAME_WIDTH_END = 3;
    private static final int FRAME_BOX_START = -1;
    private static final int FRAME_BOX_END = 2;
    private static final int NOTHING_FOUND = -1;
    protected final ServerLevel level;

    public NotePortalForcer(ServerLevel level) {
        this.level = level;
    }

    public Optional<BlockPos> findClosestPortalPosition(BlockPos exitPos, boolean isNote, WorldBorder worldBorder) {
        PoiManager poimanager = this.level.getPoiManager();
        int i = isNote ? NETHER_PORTAL_RADIUS : OVERWORLD_PORTAL_RADIUS;
        poimanager.ensureLoadedAndValid(this.level, exitPos, i);
        return poimanager.getInSquare(p_230634_ -> p_230634_.is(ModPoiTypes.NOTE_PORTAL), exitPos, i, PoiManager.Occupancy.ANY)
            .map(PoiRecord::getPos)
            .filter(worldBorder::isWithinBounds)
            .filter(p_352047_ -> this.level.getBlockState(p_352047_).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
            .min(Comparator.<BlockPos>comparingDouble(p_352046_ -> p_352046_.distSqr(exitPos)).thenComparingInt(Vec3i::getY));
    }

    public Optional<BlockUtil.FoundRectangle> createPortal(BlockPos pos, Direction.Axis axis) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        double d0 = -1.0;
        BlockPos blockpos = null;
        double d1 = -1.0;
        BlockPos blockpos1 = null;
        WorldBorder worldborder = this.level.getWorldBorder();
        int i = Math.min(this.level.getMaxBuildHeight(), this.level.getMinBuildHeight() + this.level.getLogicalHeight()) - 1;
        int j = 1;
        BlockPos.MutableBlockPos mutable = pos.mutable();

        for (BlockPos.MutableBlockPos current : BlockPos.spiralAround(pos, NETHER_PORTAL_RADIUS, Direction.EAST, Direction.SOUTH)) {
            int k = Math.min(i, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, current.getX(), current.getZ()));
            if (worldborder.isWithinBounds(current) && worldborder.isWithinBounds(current.move(direction, 1))) {
                current.move(direction.getOpposite(), 1);

                for (int l = k; l >= this.level.getMinBuildHeight(); l--) {
                    current.setY(l);
                    if (this.canPortalReplaceBlock(current)) {
                        int i1 = l;

                        while (l > this.level.getMinBuildHeight() && this.canPortalReplaceBlock(current.move(Direction.DOWN))) {
                            l--;
                        }

                        if (l + 4 <= i) {
                            int j1 = i1 - l;
                            if (j1 <= 0 || j1 >= 3) {
                                current.setY(l);
                                if (this.canHostFrame(current, mutable, direction, 0)) {
                                    double d2 = pos.distSqr(current);
                                    if (this.canHostFrame(current, mutable, direction, -1)
                                        && this.canHostFrame(current, mutable, direction, 1)
                                        && (d0 == -1.0 || d0 > d2)) {
                                        d0 = d2;
                                        blockpos = current.immutable();
                                    }

                                    if (d0 == -1.0 && (d1 == -1.0 || d1 > d2)) {
                                        d1 = d2;
                                        blockpos1 = current.immutable();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (d0 == -1.0 && d1 != -1.0) {
            blockpos = blockpos1;
            d0 = d1;
        }

        if (d0 == -1.0) {
            int k1 = Math.max(this.level.getMinBuildHeight() - -1, 70);
            int i2 = i - 9;
            if (i2 < k1) {
                return Optional.empty();
            }

            blockpos = new BlockPos(pos.getX() - direction.getStepX() * 1, Mth.clamp(pos.getY(), k1, i2), pos.getZ() - direction.getStepZ() * 1)
                .immutable();
            blockpos = worldborder.clampToBounds(blockpos);
            Direction direction1 = direction.getClockWise();

            for (int i3 = -1; i3 < 2; i3++) {
                for (int j3 = 0; j3 < 2; j3++) {
                    for (int k3 = -1; k3 < 3; k3++) {
                        // 音符盒平台 + 空气（相当于原版黑曜石平台）
                        BlockState blockstate1 = k3 < 0 ? Blocks.NOTE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        mutable.setWithOffset(
                            blockpos, j3 * direction.getStepX() + i3 * direction1.getStepX(), k3, j3 * direction.getStepZ() + i3 * direction1.getStepZ()
                        );
                        this.level.setBlockAndUpdate(mutable, blockstate1);
                    }
                }
            }
        }

        for (int l1 = -1; l1 < 3; l1++) {
            for (int j2 = -1; j2 < 4; j2++) {
                if (l1 == -1 || l1 == 2 || j2 == -1 || j2 == 3) {
                    mutable.setWithOffset(blockpos, l1 * direction.getStepX(), j2, l1 * direction.getStepZ());
                    this.level.setBlock(mutable, Blocks.NOTE_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        BlockState blockstate = ModBlocks.NOTE_PORTAL.get().defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);

        for (int k2 = 0; k2 < 2; k2++) {
            for (int l2 = 0; l2 < 3; l2++) {
                mutable.setWithOffset(blockpos, k2 * direction.getStepX(), l2, k2 * direction.getStepZ());
                this.level.setBlock(mutable, blockstate, 18);
            }
        }

        return Optional.of(new BlockUtil.FoundRectangle(blockpos.immutable(), 2, 3));
    }

    private boolean canPortalReplaceBlock(BlockPos.MutableBlockPos pos) {
        BlockState blockstate = this.level.getBlockState(pos);
        return blockstate.canBeReplaced() && blockstate.getFluidState().isEmpty();
    }

    private boolean canHostFrame(BlockPos originalPos, BlockPos.MutableBlockPos offsetPos, Direction p_direction, int offsetScale) {
        Direction direction = p_direction.getClockWise();

        for (int i = -1; i < 3; i++) {
            for (int j = -1; j < 4; j++) {
                offsetPos.setWithOffset(
                    originalPos, p_direction.getStepX() * i + direction.getStepX() * offsetScale, j, p_direction.getStepZ() * i + direction.getStepZ() * offsetScale
                );
                if (j < 0 && !this.level.getBlockState(offsetPos).isSolid()) {
                    return false;
                }

                if (j >= 0 && !this.canPortalReplaceBlock(offsetPos)) {
                    return false;
                }
            }
        }

        return true;
    }
}
