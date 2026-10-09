package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 附体空壳的"骷髅式走位"。
 *
 * <p>逻辑照搬原版骷髅用的 {@code RangedBowAttackGoal}，只保留移动部分（拉弓射击换成
 * {@link PlayerShellEntity#tick()} 的抽签攻击）：进射程且看得见就停下脚步、
 * 用 {@code moveControl.strafe(前后, 左右)} 环绕目标，每 20 刻按 30% 概率翻转环绕方向/前后方向，
 * 并根据距离强制前后 —— 离得太近必定后退（"尝试远离目标"），离得太远则不后退、改为靠过去。
 *
 * <h2>近战架势：暂停走位</h2>
 * {@link PlayerShellEntity#isHoldingForMelee()} 为真时（抽到近战事件、且目标已在近战距离内），
 * 本 goal <b>不再走位</b>：停掉导航、把前后/左右输入清零，只保留"转头盯着目标"。
 * 这里故意<b>不禁用 goal 本身</b>（而不是让 canUse 返回 false）：goal 的 LOOK 控制位一撤，
 * 空壳就会保持着最后的朝向发呆；留在 goal 里才能一边站定一边继续盯人。
 * <p>
 * 唯一的例外是<b>监守者</b>：那个判断在 {@code PlayerShellEntity} 那边（对它一律返回 false），
 * 所以本 goal 不需要知道"对面是谁"—— 只要照 {@link PlayerShellEntity#isHoldingForMelee()} 执行，
 * 对监守者就会一直绕圈后退。
 *
 * <h2>出手僵直</h2>
 * 空壳每发起一次攻击（{@code PlayerShellEntity#markAttackUsed}）都会有
 * {@link PlayerShellEntity#POSSESS_NO_MOVE_TICKS} 游戏刻"失去移动 AI"：
 * 本 goal 停步、清零输入，只保留转头盯人 —— 收招味道，也让对手有喘息与闪避的余地。
 * <b>只对玩家目标生效</b>（用户指定）：打生物/Boss 时出手即走，没有这个休息阶段。
 *
 * <h2>危险地形回避</h2>
 * 额外规避<b>水、岩浆、火（含灵魂火）、凋灵玫瑰</b>：
 * <ul>
 *   <li>已经站在里面 → 本 tick 改成朝"远离那个方块"的方向走（{@link #avoidHazards()}）；</li>
 *   <li>走位方向前方有 → 先换一边绕，两边都有就改成后退。</li>
 * </ul>
 * 判定与坐标换算见 {@link OrbHazardAvoidance}。
 *
 * <h2>保持距离模式</h2>
 * 目标是特攻表里要求"尽量远程"的敌人时（{@link OrbPossessedAttackEvents#prefersRanged}：
 * 女巫、苦力怕、铁傀儡、卫道士、满血暮色巫妖、监守者…），走位切成另一套：
 * <ul>
 *   <li><b>绝不主动靠近</b> —— 连"射程外/刚失去视线 → 先靠过去"那条路都不走，一律原地环绕；</li>
 *   <li><b>保持在 {@link #KEEP_MIN_DISTANCE}~{@link #KEEP_MAX_DISTANCE} 格</b>：比下限更近就后退、
 *       区间内只横向绕（前后输入 0）、比上限更远才靠过去；</li>
 *   <li>近战架势也被关掉（见 {@code PlayerShellEntity#mustAlwaysKite}），所以不会站住不动被打。</li>
 * </ul>
 * 条件型特攻（满血巫妖掉血后）会立刻回到普通走位。
 *
 * <h2>近身意愿模式（目标为玩家；用户指定）</h2>
 * 拿到翅膀之前，<b>只要目标是玩家就走这一套</b>（拿到翅膀之后走位整个换成
 * {@link OrbPossessionFlightAI}，不受影响）。它不是"全程近身"，而是<b>两个阶段周期切换</b>，
 * 每 {@link #ENGAGE_PHASE_MIN_TICKS}~{@link #ENGAGE_PHASE_MAX_TICKS} 刻（15~20 秒）随机换一次：
 * <ul>
 *   <li><b>近身阶段</b>：{@link #ENGAGE_STRAFE_DISTANCE}（6 格）内才停下寻路改环绕，6 格外直接冲过去；
 *       环绕时贴进 {@link #ENGAGE_IDLE_DISTANCE} 格就原地横绕，更远一律往前压，
 *       只有被挤到 {@link #ENGAGE_HOLD_DISTANCE} 格内才退半步 —— 于是它会主动贴到玩家脸上打近战；</li>
 *   <li><b>远程阶段</b>：<b>回到骷髅那套走位</b>（不是控距模式）—— 7.5 格内后退、13 格外靠近，
 *       实际在 7.5~13 格一边绕一边放远程，给玩家喘息与拉开的余地；</li>
 *   <li>其余机制（危险地形规避、跳坎、转头盯人、收招僵直、近战架势、seeTime 口径）两阶段完全一致，
 *       也都与骷髅走位一致。第一次锁定玩家时一定从<b>近身阶段</b>开始。</li>
 * </ul>
 * 位置：目标进到 3 格内（{@code MELEE_RANGE}）之后抽签就会走"贴身必近战"，于是近身阶段它会在
 * 玩家脸上反复出刀/捅矛 —— 这正是"愿意近身战斗"要的效果，也是给近战玩家的输出窗口。
 * <p>
 * 一句话对比三套：<b>骷髅</b>=7.5~13 格晃；<b>控距</b>=8~15 格且绝不主动靠近；
 * <b>近身阶段</b>=6 格外冲、之后压在 1.5~2.5 格（远程阶段退回骷髅那套）。
 *
 * <h2>seeTime 只由"真的看不见目标"归零</h2>
 * 进入环绕分支的门槛是 {@code seeTime >= 20}（照搬原版骷髅：连续看见目标 1 秒才开火/环绕）。
 * 上面三种"暂停走位"的分支（危险地形、收招僵直、近战架势）<b>只归零 {@code strafingTime}、
 * 不碰 {@code seeTime}</b>：空壳每 1~2 秒就要出一次手、每次还要僵直
 * {@link PlayerShellEntity#POSSESS_NO_MOVE_TICKS} 刻，暂停分支只要动了 seeTime，
 * 它就永远攒不满那 20 刻，会一直走"射程外或看不见 → 直接朝目标走"那条路 ——
 * 表现就是"不像骷髅那样绕圈走位，只会笔直贴上来"。
 */
public class OrbPossessionKiteGoal extends Goal {

    /** 危险地形的前瞻距离（格）：走位方向上探这么远，是危险就先换边。 */
    private static final double HAZARD_PROBE_DISTANCE = 1.2D;

    /**
     * "保持距离"模式的目标间距区间（格）：特攻表要求"尽量远程"的敌人用（用户指定 8~15 格）。
     * <ul>
     *   <li>比 {@link #KEEP_MIN_DISTANCE} 更近 → 主动后退；</li>
     *   <li>在区间内 → 只横向绕，前后输入 0（<b>不主动靠近</b>）；</li>
     *   <li>比 {@link #KEEP_MAX_DISTANCE} 更远 → 靠过去（"不要太远"，也正好是它的攻击半径）。</li>
     * </ul>
     */
    private static final double KEEP_MIN_DISTANCE = 8.0D;
    private static final double KEEP_MAX_DISTANCE = 15.0D;

    /**
     * <b>近身意愿模式</b>（目标为玩家时用，用户指定）：进入"贴身环绕"的距离（格）。
     * <p>
     * 骷髅那套是 15 格（= 攻击半径）内就停下寻路、开始环绕，于是它永远在 7.5~13 格晃；
     * 这里改成 <b>6 格</b> —— 6 格外一律<b>直接冲过去</b>，进 6 格才开始绕，
     * 所以空壳会主动贴到玩家脸上打近战，而不是远远放风筝。
     */
    private static final double ENGAGE_STRAFE_DISTANCE = 6.0D;

    /**
     * 近身意愿模式下"愿意贴到多近"（格）：只有比这更近才退半步，其余一律往前压。
     * <p>
     * 骷髅/控距那两套都是"太近就后退"，这里把门槛压到几乎贴脸 —— 这就是"愿意近身战斗"的来源。
     * 留一点点后退是为了不和玩家挤成同一个碰撞箱（互相推挤会把两边都顶得乱动）。
     */
    private static final double ENGAGE_HOLD_DISTANCE = 1.5D;

    /**
     * 近身意愿模式下"贴到这么近就停下、改成原地横绕"的距离（格）。
     * <p>
     * 与 {@link #ENGAGE_HOLD_DISTANCE} 之间那一段是<b>死区</b>：前后输入为 0，只横向绕 ——
     * 免得每刻都在阈值两侧反复横跳（观感上是抽搐）。2.5 格仍在近战距离（3 格）以内，
     * 所以它照样会抽到近战签、在玩家脸上出刀。
     */
    private static final double ENGAGE_IDLE_DISTANCE = 2.5D;

    /** 近身/远程阶段最短持续（刻）：15 秒。 */
    private static final int ENGAGE_PHASE_MIN_TICKS = 15 * 20;

    /** 近身/远程阶段最长持续（刻）：20 秒。 */
    private static final int ENGAGE_PHASE_MAX_TICKS = 20 * 20;

    private final PlayerShellEntity mob;
    private final double speedModifier;
    private final float attackRadiusSqr;

    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    /**
     * 本 tick 是不是"保持距离"模式（目标是 {@code alwaysMelee == false} 的那类敌人）。
     * 每 tick 由 {@link OrbPossessedAttackEvents#prefersRanged} 现算：条件型特攻（满血巫妖）
     * 一旦不成立就立刻回到普通走位。
     */
    private boolean keepDistance;

    /** 保持距离模式下当前处于哪一段：-1 = 太近要后退、0 = 区间内只横向绕、1 = 太远要接近。调试用。 */
    private int keepBand;

    /**
     * 本 tick 是不是<b>近身意愿模式</b>（目标为玩家、且还没拿到翅膀 —— 会飞的那段在上面早退了）。
     * <p>
     * 见类注释"近身意愿模式"一节：与骷髅/控距最大的区别是"6 格就压上去、几乎贴脸才退"。
     * <b>它只在"近身阶段"为真</b>：这个值由 {@link #tickEngagePhase} 按 15~20 秒的周期翻。
     */
    private boolean engageMelee;

    /**
     * 近身意愿的当前阶段：true = <b>近身阶段</b>（压上去打近战）、false = <b>远程阶段</b>
     * （退回骷髅那套走位）。只在目标是玩家时有意义。
     */
    private boolean engagePhase = true;

    /**
     * 当前阶段的结束时刻（{@code mob.tickCount}）；<b>-1 = 还没为玩家目标起过计时</b> ——
     * 于是第一次锁定玩家时一定从"近身阶段"开始，而不是一上来就远程。
     */
    private int engagePhaseEndTick = -1;

    public OrbPossessionKiteGoal(PlayerShellEntity mob, double speedModifier, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 重锤动作进行中：走位/抽签全归它管（它自己开滑翔、自己俯冲），这里必须让开 ——
        // 否则地面那套输入会让"自由落体中的空壳"在空中横向漂移，直接把砸击点带偏。
        return this.mob.isOrbAttached()
            && !this.mob.isMaceSequenceActive()
            && this.mob.getTarget() != null
            && this.mob.getTarget().isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || !this.mob.getNavigation().isDone();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.seeTime = 0;
        this.strafingTime = -1;
        // MOVE 类 goal 结束时收尾：留着旧路径的话，goal 停了 mob 还会沿着它自己走
        this.mob.getNavigation().stop();
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        // 重锤动作进行中（理论上 canUse 已经让开了，这里再兜一道）：不插手，交给状态机
        if (this.mob.isMaceSequenceActive()) {
            this.mob.setZza(0.0F);
            this.mob.setXxa(0.0F);
            return;
        }

        // ===== 会飞之后：走位整个换一套（见 OrbPossessionFlightAI）=====
        // 它现在是悬空的（noGravity），下面那套"地面输入 + 跳坎 + 危险地形"对它没有意义，
        // 所以在这里就切走：离地 4~5 格、与目标水平距离 7.5~15 格、永远面向目标。
        if (this.mob.isFlightMode()) {
            if (this.mob.isPossessionMovementLocked()) {
                // 收招僵直：空中悬停一下（只减速、不瞬停，免得看起来像被钉住），只保留转头盯人
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.6D));
                this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }
            OrbPossessionFlightAI.tick(this.mob, target);
            return;
        }

        // 踩在水/岩浆/火/凋灵玫瑰里 → 本 tick 的走位交给"逃离危险"（详见 avoidHazards）
        if (this.avoidHazards()) {
            this.mob.lookAt(target, 30.0F, 30.0F);
            return;
        }

        // 出手后的收招僵直：这 10 刻内"失去移动 AI"（站定，只转头盯人）
        if (this.mob.isPossessionMovementLocked()) {
            this.mob.getNavigation().stop();
            this.mob.setZza(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.lookAt(target, 30.0F, 30.0F);
            // 只把"环绕计时"归零（暂停结束后重新数 20 刻再决定方向），
            // 不动 seeTime —— 那是"连续看见目标多少刻"，归零等于要求空壳在每个攻击窗口之后
            // 再重新确认 1 秒视线。空壳每 1~2 秒出一次手、每次都要僵直 10 刻，两个暂停分支只要
            // 有一个动 seeTime，它就永远攒不满下面那个 seeTime >= 20 的门槛，于是永远走进
            // "else → navigation.moveTo(目标)" 那条路：表现就是"不再像骷髅一样走位、径直走向目标"。
            this.strafingTime = -1;
            return;
        }

        // 近战架势：站定 + 只转头（走位完全停下，出拳交给抽签逻辑）
        if (this.mob.isHoldingForMelee()) {
            this.mob.getNavigation().stop();
            this.mob.setZza(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.lookAt(target, 30.0F, 30.0F);
            // 同上：只归零环绕计时，seeTime 留着，架势一结束就能接着环绕
            this.strafingTime = -1;
            return;
        }

        double distanceSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        // 特攻表要求"尽量远程"的敌人（女巫/苦力怕/铁傀儡/卫道士/满血巫妖/监守者…）→ 保持距离模式。
        // <b>但玩家除外</b>（用户指定）：拿到翅膀之前，与玩家战斗时不用控距，走下面那套近身意愿模式。
        this.keepDistance = !(target instanceof Player)
            && OrbPossessedAttackEvents.prefersRanged(target);
        // 近身意愿模式：目标为玩家时生效，但只是"近身阶段"（见类注释：与远程阶段 15~20 秒一换）
        this.engageMelee = this.tickEngagePhase(target);
        boolean seen = this.mob.shouldSeePossessionTarget(target);
        if (seen != this.seeTime > 0) {
            this.seeTime = 0;
        }
        this.seeTime += seen ? 1 : -1;

        if (this.engageMelee) {
            // 近身意愿（见类注释）：<b>6 格内才停下寻路改环绕，6 格外直接冲过去</b>。
            // 骷髅那套要 15 格内才停，所以它只肯在 7.5~13 格晃 —— 这一条就是"愿意贴上去"的关键。
            if (distanceSqr <= ENGAGE_STRAFE_DISTANCE * ENGAGE_STRAFE_DISTANCE && this.seeTime >= 20) {
                this.mob.getNavigation().stop();
                this.strafingTime++;
            } else {
                this.mob.getNavigation().moveTo(target, this.speedModifier);
                this.strafingTime = -1;
            }
        } else if (distanceSqr <= (double) this.attackRadiusSqr && this.seeTime >= 20) {
            // 进了射程、也看得见：站住，开始走位
            this.mob.getNavigation().stop();
            this.strafingTime++;
        } else if (this.keepDistance && distanceSqr <= KEEP_MAX_DISTANCE * KEEP_MAX_DISTANCE) {
            // 保持距离模式、且已经在 15 格以内：<b>绝不主动靠近</b> ——
            // 不走"先靠过去"那条路（navigation.moveTo 目标），那正是"意外近身"的来源。
            // 直接进入环绕状态，间距全靠下面的前后输入维持（太近后退 / 区间内只横向绕）。
            this.mob.getNavigation().stop();
            this.strafingTime++;
        } else {
            // 射程外或看不见：先靠过去（保持距离模式只在超过 15 格时才会走到这里，即"不要太远"）
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            this.strafingTime = -1;
        }

        if (this.strafingTime >= 20) {
            if ((double) this.mob.getRandom().nextFloat() < 0.3) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if ((double) this.mob.getRandom().nextFloat() < 0.3) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            float forwardInput;
            float sideInput = this.strafingClockwise ? 0.5F : -0.5F;
            if (this.engageMelee) {
                // 近身意愿（见类注释）：<b>贴进 {@link #ENGAGE_IDLE_DISTANCE} 格就原地横绕，</b>
                // 更远一律往前压，只有被挤到 {@link #ENGAGE_HOLD_DISTANCE} 格内才退半步。
                // 骷髅那套在 7.5 格就强制后退、控距那套在 8 格就后退 —— 差别全在这里。
                // 中间留一段"只横绕"的死区，免得在阈值上每刻前后反复横跳。
                if (distanceSqr < ENGAGE_HOLD_DISTANCE * ENGAGE_HOLD_DISTANCE) {
                    forwardInput = -0.5F;
                    this.strafingBackwards = true;
                } else if (distanceSqr < ENGAGE_IDLE_DISTANCE * ENGAGE_IDLE_DISTANCE) {
                    forwardInput = 0.0F;
                    this.strafingBackwards = false;
                } else {
                    forwardInput = 0.5F;
                    this.strafingBackwards = false;
                }
            } else if (this.keepDistance) {
                // 保持距离模式：<b>太近(＜5 格)后退 / 区间内(5~15 格)只横向绕 / 太远(＞15 格)靠过去</b>。
                // 区间内前后输入恒为 0，所以"不主动靠近"是硬保证；超过 15 格才允许靠近（"不要太远"）。
                if (distanceSqr < KEEP_MIN_DISTANCE * KEEP_MIN_DISTANCE) {
                    forwardInput = -0.5F;
                    this.keepBand = -1;
                } else if (distanceSqr > KEEP_MAX_DISTANCE * KEEP_MAX_DISTANCE) {
                    forwardInput = 0.5F;
                    this.keepBand = 1;
                } else {
                    forwardInput = 0.0F;
                    this.keepBand = 0;
                }
                this.strafingBackwards = forwardInput < 0.0F;
            } else {
                // 太远别后退、太近必须后退 —— "尝试远离目标"就是这一句
                if (distanceSqr > (double) (this.attackRadiusSqr * 0.75F)) {
                    this.strafingBackwards = false;
                } else if (distanceSqr < (double) (this.attackRadiusSqr * 0.25F)) {
                    this.strafingBackwards = true;
                }
                forwardInput = this.strafingBackwards ? -0.5F : 0.5F;
            }
            // 危险地形（水/岩浆/火/凋灵玫瑰）在前方就换一边绕；两边都危险就改成后退。
            // 只探一段距离、并且只改这一 tick 的输入，所以不会把走位节奏搞乱。
            if (OrbHazardAvoidance.hazardAhead(this.mob, forwardInput, sideInput, HAZARD_PROBE_DISTANCE)) {
                this.strafingClockwise = !this.strafingClockwise;
                sideInput = -sideInput;
                if (OrbHazardAvoidance.hazardAhead(this.mob, forwardInput, sideInput, HAZARD_PROBE_DISTANCE)) {
                    // 两边都危险 → 改成后退。（保持距离模式下前后输入本来是 0，
                    // 直接取反会得到 -0 = 原地不动，所以这里显式给一个后退值。）
                    this.strafingBackwards = true;
                    forwardInput = this.keepDistance ? -0.5F : -forwardInput;
                }
            }
            this.mob.getMoveControl().strafe(forwardInput, sideInput);
            // 被方块拦住就跳一下：<b>后退</b>时不管方块多高都跳（用户指定），前进时只跳一格高的坎。
            // 放在这里是因为只有这里知道"这 tick 在往哪走"，不必去猜 zza/xxa 在 tick 里的存活时机。
            this.mob.tryJumpOverObstacle(forwardInput < 0.0F);
            this.mob.lookAt(target, 30.0F, 30.0F);
        } else {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    /**
     * 推进"近身 ↔ 远程"的阶段计时，返回本 tick 是不是<b>近身阶段</b>。
     *
     * <p>用户指定：<b>不是全程近身，而是近身 → 远程 → 近身周期切换，每 15~20 秒换一次</b>；
     * 远程阶段仍然调用<b>骷髅那套走位</b>（不是控距模式）。
     *
     * <p>目标不是玩家时这套阶段机完全不参与（返回 false），顺手把计时清成"未起过"，
     * 这样以后换成玩家目标时会重新从"近身阶段"开始。会飞的情况根本走不到这里
     * （{@code tick()} 上面已经早退给 {@link OrbPossessionFlightAI} 了）。
     */
    private boolean tickEngagePhase(LivingEntity target) {
        if (!(target instanceof Player)) {
            this.engagePhase = true;
            this.engagePhaseEndTick = -1;
            return false;
        }
        if (this.engagePhaseEndTick < 0) {
            // 第一次（或刚换了目标）：先来一整段"近身"
            this.engagePhase = true;
            this.engagePhaseEndTick = this.mob.tickCount + rollEngagePhaseTicks();
        } else if (this.mob.tickCount >= this.engagePhaseEndTick) {
            this.engagePhase = !this.engagePhase;
            this.engagePhaseEndTick = this.mob.tickCount + rollEngagePhaseTicks();
        }
        return this.engagePhase;
    }

    /** 掷一段阶段时长：{@link #ENGAGE_PHASE_MIN_TICKS}~{@link #ENGAGE_PHASE_MAX_TICKS} 刻之间随机。 */
    private int rollEngagePhaseTicks() {
        return ENGAGE_PHASE_MIN_TICKS
            + this.mob.getRandom().nextInt(ENGAGE_PHASE_MAX_TICKS - ENGAGE_PHASE_MIN_TICKS + 1);
    }

    /**
     * 本刻是不是<b>近身阶段</b>（对玩家的"近身意愿模式"）。
     * <p>
     * 供铁抓钩攻击当触发条件用（见 {@code OrbPossessedAttackEvents.HookPullEvent}）：
     * 用户指定"AI 处于近战状态"指的就是这一段 —— 它正主动压上去打近战，
     * 玩家却跑到了 5 格之外，于是甩铁链把人拽回来。
     * <p>
     * 注意这个值只在<b>地面走位那一段</b>每刻刷新（{@link #tick()} 里对飞行/重锤有早退），
     * 所以调用方还要自己排除"已经会飞"的情况（那边走位是保持距离，不算近战状态）。
     */
    public boolean isEngageMelee() {
        return this.engageMelee;
    }

    /** 调试用：对玩家时的"近身/远程"阶段与剩余秒数；不是玩家目标就返回空串。 */
    private String describeEngagePhase() {
        if (!(this.mob.getTarget() instanceof Player) || this.engagePhaseEndTick < 0) {
            return "";
        }
        long left = Math.max(0L, this.engagePhaseEndTick - this.mob.tickCount);
        return (this.engagePhase ? " [近身阶段 " : " [远程阶段 ") + (left / 20L) + "s]";
    }

    /**
     * 调试用：本 goal 此刻在做什么（给 {@code /jafa orbinfo} 显示，见
     * {@code PlayerShellEntity#describePossessionKiteState}）。
     * <p>
     * 走位看不见（"站着"和"在环绕"从远处看差不多），所以直接把内部状态念出来：
     * {@code 环绕前进/后退 · 顺/逆时针}、{@code 近身-压上/后退 · 顺/逆时针}（对玩家的近身意愿模式）、
     * {@code 直冲(视线 n/20)}（还没攒够 1 秒视线，正在朝目标走直线）、
     * {@code 收招僵直} / {@code 近战架势}（暂停走位）、{@code 待机}。
     * 目标是玩家时还会在末尾附上 {@code [近身阶段 Ns] / [远程阶段 Ns]}（阶段名 + 剩余秒数）。
     * 只在命令里调用，不影响 AI。
     */
    public String describeState() {
        if (this.mob.isMaceSequenceActive()) {
            return OrbPossessionMaceAttack.describe(this.mob);
        }
        if (this.mob.isFlightMode()) {
            return OrbPossessionFlightAI.describe(this.mob, this.mob.getTarget());
        }
        if (this.mob.isPossessionMovementLocked()) {
            return "收招僵直";
        }
        if (this.mob.isHoldingForMelee()) {
            return "近战架势";
        }
        if (this.engageMelee) {
            return "近身-" + (this.strafingBackwards ? "后退" : "压上")
                + (this.strafingClockwise ? "-顺时针" : "-逆时针") + this.describeEngagePhase();
        }
        if (this.keepDistance) {
            String band = switch (this.keepBand) {
                case -1 -> "后退";
                case 1 -> "接近";
                default -> "环绕";
            };
            return "保持距离-" + band + (this.strafingClockwise ? "-顺时针" : "-逆时针");
        }
        if (this.strafingTime > -1) {
            return "环绕-" + (this.strafingBackwards ? "后退" : "前进")
                + (this.strafingClockwise ? "-顺时针" : "-逆时针") + this.describeEngagePhase();
        }
        if (this.mob.getTarget() == null) {
            return "待机";
        }
        return "直冲(视线" + this.seeTime + "/20)" + this.describeEngagePhase();
    }

    /**
     * 危险地形回避：<b>水、岩浆、火、凋灵玫瑰</b>。
     * <p>
     * 空壳只要站在危险方块里（碰撞箱覆盖到，或正踩在上面），本 tick 就放弃正常走位，
     * 改成"朝远离那个方块的方向走"：把"远离"的世界向量用
     * {@link OrbHazardAvoidance#worldToInput} 拆成前后/左右两个分量喂给 {@code MoveControl#strafe}
     * —— 换算用的是游戏自己的输入向量公式，所以方向不会反。
     * <p>
     * 如果空壳恰好站在危险方块正中心（远离向量退化为零向量），就退回"跟着正常走位走"
     * （返回 false），免得原地不动。
     *
     * @return true = 本 tick 的走位已被危险地形回避接管
     */
    private boolean avoidHazards() {
        net.minecraft.core.BlockPos hazard = OrbHazardAvoidance.hazardAt(this.mob);
        if (hazard == null) {
            return false;
        }
        Vec3 away = this.mob.position().subtract(Vec3.atCenterOf(hazard));
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            return false;
        }
        double[] input = OrbHazardAvoidance.worldToInput(away.normalize(), this.mob.getYRot());
        this.mob.getNavigation().stop();
        this.mob.getMoveControl().strafe((float) input[0], (float) input[1]);
        // 逃离期间把环绕计时归零：脱离危险后会重新按距离决定前进/后退。
        // 同样不碰 seeTime（理由见 tick() 里收招僵直那段）：它只该由"真的看不见目标"来归零。
        this.strafingTime = -1;
        return true;
    }
}
