package cn.autoforged.joes_addons_for_abmc.block;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creeper Clan 传送门的框架检测，逻辑照搬原版 {@link net.minecraft.world.level.portal.PortalShape}，
 * 仅将“整圈黑曜石框架”替换为 Creeper Clan 特有的框架材质：
 * <ul>
 *  <li>底部横条：黑曜石；</li>
 *  <li>顶部横条 + 左右竖条：TNT（即“除下边外另外三边是 TNT”）。</li>
 * </ul>
 *
 * 尺寸规则（以开孔/开口为准）：
 *  - 玩家手动搭建的框架开孔 2×3~22×22（见 {@link #isPlayerValid()}，用于激活检测）；
 *  - 完整性检测沿用原版 2×3 下限（见 {@link #isValid()}/{@link #isComplete()}，用于自动生成的
 *    小传送门不被自毁逻辑误删）。
 *
 * 其余行为与原版一致：
 *  - 火可以出现在开孔内部任意位置（会向下走到底部框架条再扫描）；
 *  - 自动沿 X / Z 两个轴向检测，轴向判断正确；
 *  - 检测完整/空腔两种形态，用于激活时判断与破坏时自毁判断。
 */
public class CreeperPortalShape {
    /** 开孔最小宽度（即外围 4 格宽框架的内孔，与图示一致）。 */
    private static final int MIN_WIDTH = 2;
    /** 开孔最大宽度（22）。 */
    public static final int MAX_WIDTH = 22;
    /** 开孔最小高度（3）。 */
    private static final int MIN_HEIGHT = 3;
    /** 开孔最大高度（22）。 */
    public static final int MAX_HEIGHT = 22;

    /** 底部框架必须是黑曜石。 */
    private static final BlockBehaviour.StatePredicate FRAME_BOTTOM =
        (state, level, pos) -> state.is(Blocks.OBSIDIAN);
    /** 顶部与左右两侧框架必须是 TNT。 */
    private static final BlockBehaviour.StatePredicate FRAME_EDGE =
        (state, level, pos) -> state.is(Blocks.TNT);

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;

    public static Optional<CreeperPortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        return findPortalShape(level, bottomLeft, p -> p.isPlayerValid() && p.numPortalBlocks == 0, axis);
    }

    public static Optional<CreeperPortalShape> findPortalShape(LevelAccessor level, BlockPos bottomLeft, Predicate<CreeperPortalShape> predicate, Direction.Axis axis) {
        Optional<CreeperPortalShape> optional = Optional.of(new CreeperPortalShape(level, bottomLeft, axis)).filter(predicate);
        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis direction$axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            return Optional.of(new CreeperPortalShape(level, bottomLeft, direction$axis)).filter(predicate);
        }
    }

    public CreeperPortalShape(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = this.calculateBottomLeft(bottomLeft);
        if (this.bottomLeft == null) {
            this.bottomLeft = bottomLeft;
            this.width = 1;
            this.height = 1;
        } else {
            this.width = this.calculateWidth();
            if (this.width > 0) {
                this.height = this.calculateHeight();
            }
        }
    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos) {
        int i = Math.max(this.level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);

        while (pos.getY() > i && isEmpty(this.level.getBlockState(pos.below()))) {
            pos = pos.below();
        }

        Direction direction = this.rightDir.getOpposite();
        int j = this.getDistanceUntilEdgeAboveFrame(pos, direction) - 1;
        return j < 0 ? null : pos.relative(direction, j);
    }

    private int calculateWidth() {
        int i = this.getDistanceUntilEdgeAboveFrame(this.bottomLeft, this.rightDir);
        return i >= MIN_WIDTH && i <= MAX_WIDTH ? i : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= MAX_WIDTH; i++) {
            mutable.set(pos).move(direction, i);
            BlockState blockstate = this.level.getBlockState(mutable);
            if (!isEmpty(blockstate)) {
                if (FRAME_EDGE.test(blockstate, this.level, mutable)) {
                    return i;
                }
                break;
            }

            BlockState below = this.level.getBlockState(mutable.move(Direction.DOWN));
            if (!FRAME_BOTTOM.test(below, this.level, mutable)) {
                break;
            }
        }

        return 0;
    }

    private int calculateHeight() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int i = this.getDistanceUntilTop(mutable);
        return i >= MIN_HEIGHT && i <= MAX_HEIGHT && this.hasTopFrame(mutable, i) ? i : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos pos, int distanceToTop) {
        for (int i = 0; i < this.width; i++) {
            BlockPos.MutableBlockPos top = pos.set(this.bottomLeft).move(Direction.UP, distanceToTop).move(this.rightDir, i);
            if (!FRAME_EDGE.test(this.level.getBlockState(top), this.level, top)) {
                return false;
            }
        }
        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos pos) {
        for (int i = 0; i < MAX_HEIGHT; i++) {
            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, -1);
            if (!FRAME_EDGE.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, this.width);
            if (!FRAME_EDGE.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            for (int j = 0; j < this.width; j++) {
                pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, j);
                BlockState blockstate = this.level.getBlockState(pos);
                if (!isEmpty(blockstate)) {
                    return i;
                }

                if (blockstate.is(ModBlocks.CREEPER_PORTAL.get())) {
                    this.numPortalBlocks++;
                }
            }
        }

        return MAX_HEIGHT;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(Blocks.FIRE) || state.is(ModBlocks.CREEPER_PORTAL.get());
    }

    /** 开孔左下角（即底部黑曜石横条左端对应的开孔角）。 */
    @Nullable
    public BlockPos getBottomLeft() {
        return this.bottomLeft;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /**
     * 收集框架内所有 TNT 方块位置（含顶部横条、左右竖条以及四个边角）。
     * 用于点火时“同时点燃框架内所有 TNT”。
     */
    public java.util.List<BlockPos> collectFrameTnt() {
        java.util.List<BlockPos> tnts = new java.util.ArrayList<>();
        if (this.bottomLeft == null || this.width <= 0 || this.height <= 0) {
            return tnts;
        }
        // 顶部横条：开孔顶上一行（bottomLeft + height），整条含两端边角
        for (int i = -1; i <= this.width; i++) {
            BlockPos p = this.bottomLeft.relative(this.rightDir, i).above(this.height);
            if (this.level.getBlockState(p).is(Blocks.TNT)) {
                tnts.add(p);
            }
        }
        // 左右竖条：左右两列（含底部边角、顶部边角），y 从 -1 到 height-1
        for (int y = -1; y < this.height; y++) {
            BlockPos left = this.bottomLeft.relative(this.rightDir.getOpposite(), 1).above(y);
            if (this.level.getBlockState(left).is(Blocks.TNT)) {
                tnts.add(left);
            }
            BlockPos right = this.bottomLeft.relative(this.rightDir, this.width).above(y);
            if (this.level.getBlockState(right).is(Blocks.TNT)) {
                tnts.add(right);
            }
        }
        return tnts;
    }

    public boolean isValid() {
        return this.bottomLeft != null
            && this.width >= MIN_WIDTH
            && this.width <= MAX_WIDTH
            && this.height >= MIN_HEIGHT
            && this.height <= MAX_HEIGHT;
    }

    /** 玩家手动搭建框架时的激活判定：要求开孔 2×3~22×22。 */
    public boolean isPlayerValid() {
        return this.isValid();
    }

    public void createPortalBlocks() {
        BlockState blockstate = ModBlocks.CREEPER_PORTAL.get().defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(
                this.bottomLeft,
                this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1))
            .forEach(p -> this.level.setBlock(p, blockstate, 18));
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }
}
