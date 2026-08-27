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
 * 音符方块宇宙传送门的框架检测，逻辑照搬原版 {@link net.minecraft.world.level.portal.PortalShape}，
 * 仅将“黑曜石框架”替换为“音符盒框架”。
 *
 * 尺寸规则分两套：
 *  - 玩家手动搭建的框架要求开孔 4×5~23×23（见 {@link #isPlayerValid()}，用于激活检测）；
 *  - 完整性检测沿用原版 2×3 下限（见 {@link #isValid()}/{@link #isComplete()}，用于自动生成的
 *    2×3 小传送门不被自毁逻辑误删）。
 *
 * 优点与原版一致：
 *  - 火可以出现在开孔内部任意位置（会向下走到底部框架条再扫描）；
 *  - 自动沿 X / Z 两个轴向检测，轴向判断正确；
 *  - 检测完整/空腔两种形态，用于激活时判断与破坏时自毁判断。
 */
public class NotePortalShape {
    // 尺寸一律以“开孔（开口）”为准。用户规定的外围框架长宽为 4×5~23×23，
    // 框架四边各占 1 格，故开孔尺寸 = 外围尺寸 - 2（即 2×3~21×21）。
    private static final int MIN_WIDTH = 2;   // 开孔最小宽度（外围 4 格宽框架，同原版 2×3 开口）
    public static final int MAX_WIDTH = 21;   // 开孔最大宽度（外围 23 格宽框架）
    private static final int MIN_HEIGHT = 3;  // 开孔最小高度（外围 5 格高框架，同原版 2×3 开口）
    public static final int MAX_HEIGHT = 21;  // 开孔最大高度（外围 23 格高框架）
    private static final int PLAYER_MIN_WIDTH = MIN_WIDTH;   // 玩家手动框架最小开孔宽度
    private static final int PLAYER_MIN_HEIGHT = MIN_HEIGHT; // 玩家手动框架最小开孔高度
    /** 框架必须是音符盒（角落方块可为其他方块）。 */
    private static final BlockBehaviour.StatePredicate FRAME =
        (state, level, pos) -> state.is(Blocks.NOTE_BLOCK);

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;

    public static Optional<NotePortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        return findPortalShape(level, bottomLeft, p_77727_ -> p_77727_.isPlayerValid() && p_77727_.numPortalBlocks == 0, axis);
    }

    public static Optional<NotePortalShape> findPortalShape(LevelAccessor level, BlockPos bottomLeft, Predicate<NotePortalShape> predicate, Direction.Axis axis) {
        Optional<NotePortalShape> optional = Optional.of(new NotePortalShape(level, bottomLeft, axis)).filter(predicate);
        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis direction$axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            return Optional.of(new NotePortalShape(level, bottomLeft, direction$axis)).filter(predicate);
        }
    }

    public NotePortalShape(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
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
                if (FRAME.test(blockstate, this.level, mutable)) {
                    return i;
                }
                break;
            }

            BlockState below = this.level.getBlockState(mutable.move(Direction.DOWN));
            if (!FRAME.test(below, this.level, mutable)) {
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
            if (!FRAME.test(this.level.getBlockState(top), this.level, top)) {
                return false;
            }
        }
        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos pos) {
        for (int i = 0; i < MAX_HEIGHT; i++) {
            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, -1);
            if (!FRAME.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, this.width);
            if (!FRAME.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            for (int j = 0; j < this.width; j++) {
                pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, j);
                BlockState blockstate = this.level.getBlockState(pos);
                if (!isEmpty(blockstate)) {
                    return i;
                }

                if (blockstate.is(ModBlocks.NOTE_PORTAL.get())) {
                    this.numPortalBlocks++;
                }
            }
        }

        return MAX_HEIGHT;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(Blocks.FIRE) || state.is(ModBlocks.NOTE_PORTAL.get());
    }

    public boolean isValid() {
        return this.bottomLeft != null
            && this.width >= MIN_WIDTH
            && this.width <= MAX_WIDTH
            && this.height >= MIN_HEIGHT
            && this.height <= MAX_HEIGHT;
    }

    /** 玩家手动搭建框架时的激活判定：要求开孔 4×5~23×23（自动生成的小传送门不满足，不会被误激活/误自毁）。 */
    public boolean isPlayerValid() {
        return this.bottomLeft != null
            && this.width >= PLAYER_MIN_WIDTH
            && this.width <= MAX_WIDTH
            && this.height >= PLAYER_MIN_HEIGHT
            && this.height <= MAX_HEIGHT;
    }

    public void createPortalBlocks() {
        BlockState blockstate = ModBlocks.NOTE_PORTAL.get().defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(
                this.bottomLeft,
                this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1))
            .forEach(p_77725_ -> this.level.setBlock(p_77725_, blockstate, 18));
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }
}
