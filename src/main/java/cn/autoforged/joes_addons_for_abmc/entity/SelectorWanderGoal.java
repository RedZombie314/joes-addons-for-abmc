package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * 幸运方块选择器的「空中游荡」AI。
 * <p>
 * 行为：
 * <ul>
 *   <li><b>朝向</b>：随机挑一个水平朝向，然后沿这个朝向一直飘；<b>每 4~6 秒</b>才换一次朝向。
 *       目标点永远落在「当前朝向的前方 {@link #LEAD_DISTANCE} 格」处，到点了就沿同一朝向再往前推一次，
 *       所以它能持续移动、而朝向在这几秒内保持不变。</li>
 *   <li><b>高度</b>：以<b>距地表 3~10 格</b>为目标，<b>每 4~6 秒</b>重挑一次（与换朝向各用各的计时器，
 *       于是两者会自然错开，看起来不那么机械）。</li>
 *   <li><b>天花板</b>：头顶若有方块（判定用 {@code blocksMotion()}，所以草/水不算），
 *       则高度改为<b>离天花板 3~5 格</b>；两个区间取交集，空间太挤时优先满足天花板约束，
 *       但下限抬到地表 +1、上限压到天花板下方 1 格，免得卡进方块里。</li>
 * </ul>
 * <p>
 * <b>为什么是「推前方点」而不是「一次挑一个远点」</b>：第一版是照原版恶魂的
 * {@code RandomFloatAroundGoal} 写的——到点就随机换个 12 格内的新点。结果选择器飞得快，
 * 一两秒就到位、于是看向的方向也一两秒一变，看起来在抽搐。改成「锁定朝向 + 不断把目标点往前推」后，
 * 朝向只在 4~6 秒的节拍上改变，中间是稳定的直线飘移。
 * <p>
 * 移动：AI 只负责「往哪儿飞」——把目标点交给 {@code MoveControl#setWantedPosition}，
 * 由原版 {@code FlyingMoveControl} + {@code LivingEntity#travel} 负责实际位移，
 * 速度倍率取实体的 {@code getFlightSpeedMultiplier()}（要按情况换速度只改那个方法）。
 * 不走寻路（不产生 Path），到点判定用的是移动控制里那个目标点的距离。
 */
public class SelectorWanderGoal extends Goal {
    /** 目标点落在当前朝向前方多少格：到点了就沿同一朝向再往前推一次，于是持续飘。 */
    private static final double LEAD_DISTANCE = 6.0;
    /** 换朝向的间隔（刻）：4~6 秒。 */
    private static final int HEADING_INTERVAL_MIN = 20 * 4;
    private static final int HEADING_INTERVAL_MAX = 20 * 6;
    /** 距地表的高度区间（格）。 */
    private static final double SURFACE_MIN = 3.0;
    private static final double SURFACE_MAX = 10.0;
    /** 距天花板的高度区间（格）。 */
    private static final double CEILING_MIN = 3.0;
    private static final double CEILING_MAX = 5.0;
    /** 高度重挑的间隔（刻）：4~6 秒。 */
    private static final int HEIGHT_INTERVAL_MIN = 20 * 4;
    private static final int HEIGHT_INTERVAL_MAX = 20 * 6;
    /** 向上找天花板时最多扫多少格。 */
    private static final int CEILING_SCAN = 48;
    /** 「到点」判定：进入 1 格内就认为到位，沿当前朝向把目标点往前推。 */
    private static final double ARRIVE_DIST_SQR = 1.0;

    private final LuckySelectorEntity selector;

    /** 当前朝向（水平单位向量）。 */
    private double headingX;
    private double headingZ;
    /** 是否已经挑过朝向。 */
    private boolean headingPicked;
    /** 下次换朝向的时刻（{@code tickCount}）。 */
    private int nextHeadingPickTick;

    /** 当前目标高度（绝对 y），每 4~6 秒重挑一次。 */
    private double targetY;
    /** 下次重挑高度的时刻（{@code tickCount}）。 */
    private int nextHeightPickTick;

    public SelectorWanderGoal(LuckySelectorEntity selector) {
        this.selector = selector;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 寻物/发送流程期间让位：那段时间由实体自己用移动控制飞（见 LuckySelectorEntity#tickSeek）
        if (this.selector.isBusy()) {
            return false;
        }
        // 被玩家旁观操控时让位：那段时间方向与位移全归玩家（见 LuckySelectorEntity#tickPilot），
        // 交给 AI 只会跟玩家抢方向
        if (this.selector.isPiloted()) {
            return false;
        }
        // 还没挑过朝向：立刻开工
        if (!this.headingPicked) {
            return true;
        }
        // 到换朝向的节拍了
        if (this.selector.tickCount >= this.nextHeadingPickTick) {
            return true;
        }
        MoveControl moveControl = this.selector.getMoveControl();
        // 没有目标点了：沿当前朝向再往前推一次
        // （注意 FlyingMoveControl 会在处理完一次目标后的下一刻把状态翻成 WAIT，
        //   所以这里几乎每刻都会成立——这正是原版飞行生物持续移动的方式）
        if (!moveControl.hasWanted()) {
            return true;
        }
        // 快到了：同样沿当前朝向往前推，保持连续移动
        double dx = moveControl.getWantedX() - this.selector.getX();
        double dy = moveControl.getWantedY() - this.selector.getY();
        double dz = moveControl.getWantedZ() - this.selector.getZ();
        return dx * dx + dy * dy + dz * dz < ARRIVE_DIST_SQR;
    }

    /** 一次性 goal：下完点就退出，飞行交给移动控制。 */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        RandomSource random = this.selector.getRandom();

        // 1) 该换朝向时才换，否则沿用当前朝向
        if (!this.headingPicked || this.selector.tickCount >= this.nextHeadingPickTick) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            this.headingX = Math.cos(angle);
            this.headingZ = Math.sin(angle);
            this.headingPicked = true;
            this.nextHeadingPickTick = this.selector.tickCount + HEADING_INTERVAL_MIN
                + random.nextInt(HEADING_INTERVAL_MAX - HEADING_INTERVAL_MIN + 1);
        }

        // 2) 高度同理：到点了才重挑
        if (this.selector.tickCount >= this.nextHeightPickTick) {
            this.targetY = this.pickTargetY(random);
            this.nextHeightPickTick = this.selector.tickCount + HEIGHT_INTERVAL_MIN
                + random.nextInt(HEIGHT_INTERVAL_MAX - HEIGHT_INTERVAL_MIN + 1);
        }

        // 3) 目标点 = 当前位置沿着当前朝向前方 LEAD_DISTANCE 格，高度取 targetY。
        //    因为始终是"从当前位置沿朝向"，所以移动方向恒等于朝向，朝向在节拍之间不会抖。
        //    第 4 个参数是速度倍率：交给实体的 getFlightSpeedMultiplier()，方便以后按情况改移速。
        double x = this.selector.getX() + this.headingX * LEAD_DISTANCE;
        double z = this.selector.getZ() + this.headingZ * LEAD_DISTANCE;
        this.selector.getMoveControl().setWantedPosition(
            x, this.targetY, z, this.selector.getFlightSpeedMultiplier());
    }

    /**
     * 挑一个目标高度：地表 +{@link #SURFACE_MIN}~{@link #SURFACE_MAX}，
     * 若头顶有天花板则改为天花板 -{@link #CEILING_MAX}~-{@link #CEILING_MIN}，两者取交集。
     */
    private double pickTargetY(RandomSource random) {
        Level level = this.selector.level();
        int x = Mth.floor(this.selector.getX());
        int z = Mth.floor(this.selector.getZ());
        // MOTION_BLOCKING 高度图给的是「最高实心方块上方那一格」的 y，也就是可站立的那个 y
        int surfaceTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        double low = surfaceTop + SURFACE_MIN;
        double high = surfaceTop + SURFACE_MAX;

        int ceilingY = this.findCeiling();
        if (ceilingY != Integer.MIN_VALUE) {
            low = Math.max(low, ceilingY - CEILING_MAX);
            high = Math.min(high, ceilingY - CEILING_MIN);
            if (high < low) {
                // 空间太挤（比如 2 格高的洞）：优先满足「离天花板 3~5 格」，
                // 但下限抬到地表 +1、上限压到天花板下方 1 格，避免直接卡进方块
                low = Math.max(surfaceTop + 1.0, ceilingY - CEILING_MAX);
                high = Math.min(ceilingY - 1.0, Math.max(low, ceilingY - CEILING_MIN));
            }
        }
        if (high < low) {
            high = low;
        }
        return low + random.nextDouble() * (high - low);
    }

    /**
     * 头顶第一个「会挡路」的方块（天花板）的 y；一路扫到 {@link #CEILING_SCAN} 格全是空的就认为没有天花板。
     * <p>
     * 用 {@code blocksMotion()} 而不是 {@code !isAir()}：草、花、火把这类不挡路的方块不该算天花板，
     * 这也和上方 {@code MOTION_BLOCKING} 高度图的判定标准保持一致。
     */
    private int findCeiling() {
        Level level = this.selector.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
            Mth.floor(this.selector.getX()),
            Mth.floor(this.selector.getY()) + 1,
            Mth.floor(this.selector.getZ()));
        for (int i = 0; i < CEILING_SCAN; i++) {
            if (level.getBlockState(cursor).blocksMotion()) {
                return cursor.getY();
            }
            cursor.move(Direction.UP);
        }
        return Integer.MIN_VALUE;
    }
}
