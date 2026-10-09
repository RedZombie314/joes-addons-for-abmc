package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * "会飞的附体空壳"的走位 AI。
 *
 * <h2>什么时候生效</h2>
 * 远程低概率特攻"飞行"（{@code OrbPossessedAttackEvents.FlightEvent}）出手后，
 * 空壳进入飞行形态（{@link PlayerShellEntity#isFlightMode()}）。此后走位不再用
 * {@link OrbPossessionKiteGoal} 那套"骷髅式走位"，而是每刻直接写速度 —— 原因很直接：
 * 它现在是<b>悬空</b>的（{@code noGravity}），
 * {@code MoveControl#strafe} 那套地面输入对它毫无意义。
 *
 * <h2>走位规则（用户指定）</h2>
 * <ul>
 *   <li><b>离地高度：地面之上 4~5 格</b> —— "地面"取高度图
 *       {@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES}（<b>含水面</b>），
 *       所以飞到海面上时它贴着<b>水面之上</b> 4~5 格，不会一头扎进水里；
 *       <b>高出 5 格时以 5 格/秒（0.25 格/刻）匀速下降</b>（用户指定，见
 *       {@link #DESCEND_SPEED_PER_TICK}）—— 被打到高空、或重锤那套收场之后，
 *       它就是一边水平走位、一边按这个固定速度沉回悬停高度，而不是"忽快忽慢地飘"；</li>
 *   <li><b>与目标的水平距离：7.5~15 格</b> —— 比 7.5 近就往外退、比 15 远就靠过来、
 *       区间内则<b>绕着目标转圈</b>（横向漂移，方向每几秒随机翻一次）；</li>
 *   <li><b>始终面向目标</b>（转头由 {@code LookControl} 负责，这里只调一次）；</li>
 *   <li>头顶有天花板时压低高度，绝不往上顶方块（低矮洞穴里就贴着能飞的高度悬停）。</li>
 * </ul>
 *
 * <h2>速度写法</h2>
 * 与"附体召唤的骷髅马骑士"（{@link OrbSkeletonKnightFlight}）和"附魔千纸鹤"
 * （{@code EnchantedOrigamiEntity}）同一套：先算出本刻<b>想要的速度</b>，
 * 再按 {@link #STEER} 比例把当前速度朝它靠拢 —— 看起来有惯性，不会像瞬移。
 */
public final class OrbPossessionFlightAI {

    /** 巡航速度（格/刻；0.34 ≈ 6.8 格/秒）。 */
    private static final double CRUISE_SPEED = 0.34D;

    /** 每刻把速度朝"想要的速度"靠拢的比例（越小越飘）。 */
    private static final double STEER = 0.22D;

    /** 离地高度区间（格）：用户指定"保持在地面 4~5 格"。 */
    private static final double ABOVE_GROUND_MIN = 4.0D;
    private static final double ABOVE_GROUND_MAX = 5.0D;

    /**
     * <b>高出悬停区间（&gt; {@value #ABOVE_GROUND_MAX} 格）时的下降速度</b>：5 格/秒 = 0.25 格/刻，
     * <b>匀速</b>（用户指定）。
     * <p>
     * 与下面那套 {@link #STEER} 指数逼近的区别就是"匀速"两个字：逼近是"越接近目标速度越慢"，
     * 而且竖直方向和水平目标是同一个合速度，高空时还会被水平意图带着往上飘 ——
     * 观感上就是"忽快忽慢、半天不下来"。所以一旦高出 5 格，竖直速度直接<b>钉死</b>在这个值上，
     * 只有水平分量继续走逼近。
     */
    private static final double DESCEND_SPEED_PER_TICK = 5.0D / 20.0D;

    /** 与目标的水平距离区间（格）：用户指定 7.5~15。 */
    private static final double KEEP_MIN_DISTANCE = 7.5D;
    private static final double KEEP_MAX_DISTANCE = 15.0D;

    /** 横向绕圈的切向速度占比（0 = 不绕圈，只做径向调整）。 */
    private static final double ORBIT_SPEED_FACTOR = 0.55D;

    /**
     * 悬停期间（离地 ≤ {@value #ABOVE_GROUND_MAX} 格）"想降回 4~5 格"时每刻最多降多少格
     * （{@code 0.5} 格/刻 = 10 格/秒）。
     * <p>
     * 这条限速是必须的：没有它的话，"想降回地面之上 4~5 格"会被翻译成一个巨大的向下速度，
     * 空壳会像石头一样瞬间砸到地面（观感上就是"瞬移"）。
     * <p>
     * <b>高出 5 格时不走这条</b>：那一段由 {@link #DESCEND_SPEED_PER_TICK}（5 格/秒、匀速）
     * 直接接管竖直速度。
     */
    private static final double MAX_DESCENT_PER_TICK = 0.5D;

    /** 绕圈方向翻转的间隔（刻）：4~7 秒随机一次。 */
    private static final int ORBIT_FLIP_MIN_TICKS = 80;
    private static final int ORBIT_FLIP_MAX_TICKS = 140;

    private OrbPossessionFlightAI() {
    }

    /**
     * 每刻推进一次（由 {@link OrbPossessionKiteGoal} 在"会飞"分支里调用）。
     *
     * @param shell  会飞的附体空壳
     * @param target 当前目标
     */
    public static void tick(PlayerShellEntity shell, LivingEntity target) {
        // 无重力：飞行形态是"悬停"，不该被重力往下拽
        shell.setNoGravity(true);

        double dx = shell.getX() - target.getX();
        double dz = shell.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // ---- 水平：把距离拉回 [7.5, 15]，并在区间内绕着目标转圈 ----
        double wantedHorizontal;
        if (horizontal < KEEP_MIN_DISTANCE) {
            wantedHorizontal = KEEP_MIN_DISTANCE;   // 太近：往外退（"不许贴脸"）
        } else if (horizontal > KEEP_MAX_DISTANCE) {
            wantedHorizontal = KEEP_MAX_DISTANCE;   // 太远：靠近（"也不要掉队"）
        } else {
            wantedHorizontal = horizontal;          // 区间内：距离保持不变，只绕圈
        }

        // 从目标指向自己的单位向量（重合时的退化情况随便给个方向）
        double outX = horizontal > 1.0E-4 ? dx / horizontal : 1.0D;
        double outZ = horizontal > 1.0E-4 ? dz / horizontal : 0.0D;

        // 径向分量：本刻想站到的水平位置（相对目标）
        double radialX = outX * wantedHorizontal;
        double radialZ = outZ * wantedHorizontal;

        // 切向分量：绕圈（方向定期翻转，免得一直同一个方向打转）
        double[] orbit = shell.flightOrbitStep(ORBIT_FLIP_MIN_TICKS, ORBIT_FLIP_MAX_TICKS);
        double tangentX = -outZ * orbit[0];
        double tangentZ = outX * orbit[0];

        double goalX = target.getX() + radialX + tangentX * ORBIT_SPEED_FACTOR * wantedHorizontal * 0.25D;
        double goalZ = target.getZ() + radialZ + tangentZ * ORBIT_SPEED_FACTOR * wantedHorizontal * 0.25D;

        // ---- 竖直：地面之上 4~5 格，且不顶天花板 ----
        double wantedY = wantedAltitude(shell, shell.getX(), shell.getZ());

        Vec3 toGoal = new Vec3(goalX - shell.getX(), wantedY - shell.getY(), goalZ - shell.getZ());
        Vec3 wanted = toGoal.length() < 0.35D
            ? Vec3.ZERO
            : toGoal.normalize().scale(CRUISE_SPEED);

        Vec3 current = shell.getDeltaMovement();
        Vec3 next = current.add(wanted.subtract(current).scale(STEER));
        if (tooHighAboveGround(shell)) {
            // <b>离地高于 5 格 → 以 5 格/秒匀速下降</b>（用户指定）。
            // 竖直速度直接写死、不走 STEER：见 DESCEND_SPEED_PER_TICK 的注释。
            // 水平分量照旧（该退该绕都不受影响），所以它是一边横着走位、一边匀速往下沉。
            next = new Vec3(next.x, -DESCEND_SPEED_PER_TICK, next.z);
        }
        shell.setDeltaMovement(next);
        shell.hasImpulse = true;

        // 始终面向目标（只在这里调一次：水平朝向由 LookControl 拧过去）
        shell.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /**
     * <b>飞行形态的通用规则：离地高于 {@value #ABOVE_GROUND_MAX} 格就以
     * {@value #DESCEND_SPEED_PER_TICK} 格/刻（<b>5 格/秒</b>）匀速下降</b>（用户指定）。
     *
     * <h2>为什么要有"飞控之外"的第二个调用点</h2>
     * 飞控挂在走位 goal 上，而那个 goal 的 {@code canUse} 要求"有活的目标"。目标一死 / 一跑出索敌范围，
     * 这段 AI 整段不跑 —— 而这具壳是 {@code noGravity} 的，于是它会<b>永远挂在当时那个高度上</b>
     * （正是用户反馈的"被打到高处之后一直不下来"）。所以除了 {@link #tick} 里那一处，
     * {@code PlayerShellEntity#tick()} 也每刻问一次这个方法（见那边的调用点）。
     *
     * @return true = 这条规则生效了（竖直速度已被钉成匀速下降）
     */
    static boolean descendIfTooHigh(PlayerShellEntity shell) {
        if (shell.level().isClientSide() || !tooHighAboveGround(shell)) {
            return false;
        }
        Vec3 movement = shell.getDeltaMovement();
        shell.setDeltaMovement(movement.x, -DESCEND_SPEED_PER_TICK, movement.z);
        shell.hasImpulse = true;
        return true;
    }

    /** 现在是不是"高出悬停区间"（离地 &gt; {@value #ABOVE_GROUND_MAX} 格）。 */
    static boolean tooHighAboveGround(PlayerShellEntity shell) {
        return shell.getY() - groundHeight(shell) > ABOVE_GROUND_MAX;
    }

    /**
     * <b>没有目标时的待命悬停区间</b>（格）：距地面 3~4 格（用户指定）。
     * <p>比有目标时的 {@link #ABOVE_GROUND_MIN}~{@link #ABOVE_GROUND_MAX}（4~5 格）低一档：
     * 没有人可打的时候就贴在低处待命，等索敌找到新目标再由飞控带回 4~5 格。
     */
    private static final double IDLE_HOVER_MIN = 3.0D;
    private static final double IDLE_HOVER_MAX = 4.0D;

    /**
     * <b>没有目标时的"降回待命悬停高度"</b>（用户指定）：<b>保持飞行形态</b>，以
     * {@link #DESCEND_SPEED_PER_TICK}（5 格/秒）下降，到"距地面 {@value #IDLE_HOVER_MIN}~{@value #IDLE_HOVER_MAX} 格"
     * 就停住 —— 不落地（它会飞，没必要站桩），也不再往上飘。
     *
     * <p>与 {@link #descendIfTooHigh} 的分工：那个是"有目标时的高位修正"（降到 4~5 格就停手，
     * 平时悬停是常态）；这个是"没目标时的待命高度"（目标高度低一档）。
     *
     * <p><b>非飞行形态不走这里</b>：不会飞的壳没目标时就是普通自由落体，落地交给水/末影珍珠那套
     * 落地保护（见 {@code OrbPossessionMaceAttack#tryStartEmergencyWaterSave}）。
     *
     * <p>下降期间<b>每刻清空坠落距离</b>：这是一次"飞行形态自己控的缓降"，不是自由落体，
     * 不该因为从高处下来就吃一记摔落伤害（与 {@code OrbPossessionMaceAttack} 的 PEARL 阶段同一个道理）。
     */
    static void descendToIdleHover(PlayerShellEntity shell) {
        if (shell.level().isClientSide()) {
            return;
        }
        if (shell.getY() - groundHeight(shell) <= IDLE_HOVER_MAX) {
            return;   // 已经在 3~4 格（或更低）了：停住不动
        }
        Vec3 movement = shell.getDeltaMovement();
        shell.setDeltaMovement(movement.x, -DESCEND_SPEED_PER_TICK, movement.z);
        shell.fallDistance = 0.0F;
        shell.hasImpulse = true;
    }

    /** 脚下地面（含水面）的高度；拿不到关卡信息时退回实体当前高度（等价于"离地 0"）。 */
    private static double groundHeight(PlayerShellEntity shell) {
        if (!(shell.level() instanceof ServerLevel level)) {
            return shell.getY();
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Mth.floor(shell.getX()), Mth.floor(shell.getZ()));
    }

    /**
     * 目标高度：脚下地面（含水面）之上 {@link #ABOVE_GROUND_MIN}~{@link #ABOVE_GROUND_MAX} 格的中点，
     * 但若头顶有天花板就压低到天花板下面 —— 低矮空间里"贴着能飞的高度"也不会撞头。
     */
    private static double wantedAltitude(PlayerShellEntity shell, double x, double z) {
        double wanted = shell.getY();
        if (shell.level() instanceof ServerLevel level) {
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
            wanted = groundY + (ABOVE_GROUND_MIN + ABOVE_GROUND_MAX) * 0.5D;
            // 天花板：从当前高度往上找第一格"挡住头顶"的方块，把目标高度压到它下面 1.5 格
            int fromY = Mth.floor(shell.getY());
            int toY = Mth.floor(wanted) + 1;
            for (int y = fromY + 1; y <= toY; y++) {
                BlockPos pos = BlockPos.containing(x, y, z);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    wanted = Math.min(wanted, y - 1.5D);
                    break;
                }
            }
        }
        // 别低于"当前能降到的下限"（免得一帧之内砸到地面）：下降速度按
        // {@link #MAX_DESCENT_PER_TICK} 限速，升回目标高度则不限（本来就是往上拉）。
        return Math.max(wanted, shell.getY() - MAX_DESCENT_PER_TICK);
    }

    /** 供 {@code /jafa orbinfo} 调试显示用的一句人话。 */
    public static String describe(PlayerShellEntity shell, LivingEntity target) {
        if (target == null) {
            return "飞行-悬停";
        }
        double dx = shell.getX() - target.getX();
        double dz = shell.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        String band = horizontal < KEEP_MIN_DISTANCE ? "后退"
            : horizontal > KEEP_MAX_DISTANCE ? "接近" : "环绕";
        double height = heightAboveGround(shell);
        // 高出悬停区间时竖直速度是钉死的 5 格/秒匀速下降，单独标出来（用户要的就是这一段）
        String vertical = height > ABOVE_GROUND_MAX ? "/下降中" : "";
        return String.format("飞行-%s%s/离地%.1f格", band, vertical, height);
    }

    /** 当前离地（含水面）多少格；拿不到关卡信息时返回 0。 */
    private static double heightAboveGround(PlayerShellEntity shell) {
        return shell.getY() - groundHeight(shell);
    }
}
