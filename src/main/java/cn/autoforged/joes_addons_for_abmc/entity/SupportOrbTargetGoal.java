package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * <b>幸运方块"支援生物"的专属索敌</b>（用户指定）：只打附近的<b>幸运核心（附体的玩家）</b>。
 *
 * <h2>用户口径</h2>
 * "如果是送生物过去支援，则生物（若具备攻击性，比如 Bob）的索敌对象将永远是附近 50 格以内的
 * 幸运核心（附体的玩家），且永远不会索敌被幸运核心索敌的玩家。"
 * <ul>
 *   <li><b>只认核心</b>：每 {@link #RESCAN_TICKS} 刻重扫一次，取 {@link #ORB_RANGE} 格内<b>最近</b>的一具
 *       {@link PlayerShellEntity}（= 被核心附体的"玩家"，{@code isOrbAttached()}）。
 *       附近没有核心时<b>不锁任何目标</b>（它就是来支援的，不干别的）。</li>
 *   <li><b>绝不锁被核心索敌的玩家</b>：靠 {@code SupportMobControl#forbidsTargeting} 那道硬闸
 *       （挂在 {@code LivingChangeTargetEvent} 上）。本 goal 本身压根不选玩家，所以那一关只是
 *       为了挡住"别处塞进来的目标 goal"（例如原版 {@code HurtByTargetGoal} 被挨打唤醒）。</li>
 * </ul>
 *
 * <p>装上它的方式与附体召唤物那套一样（{@link SupportMobControl#installGoal}）：先清掉原版
 * "见谁打谁"的目标 goal，再把这一个挂在 {@code targetSelector} 优先级 0。
 * 命令式注册的 goal 不进存档，所以读档/区块重载后会由 {@code SupportMobControl#tick} 兜底自愈。
 */
public class SupportOrbTargetGoal extends Goal {

    /** 索敌半径（格）：用户指定的 50。 */
    public static final double ORB_RANGE = SupportMobControl.ORB_TARGET_RANGE;

    /**
     * 已经锁上的核心跑远了，追出多少格才放弃（格）。
     * <p>
     * 比 {@link #ORB_RANGE} 略大：否则它每走一步都在 50 格这条线上反复"锁上→丢掉"，
     * 表现就是原地抽搐。丢掉之后 {@link #canUse()} 会重新找最近的那一具。
     */
    private static final double KEEP_RANGE = ORB_RANGE * 1.2D;

    /** 重新扫目标的间隔（刻）：0.5 秒（一次 50 格 AABB 查询，不必每刻做）。 */
    private static final int RESCAN_TICKS = 10;

    private final Mob mob;

    /** 当前锁着的那具核心（被附体的"玩家"）；没有就是 null。 */
    @Nullable
    private PlayerShellEntity lockedOrb;

    /** 下一次重扫的 {@code mob.tickCount}。 */
    private int nextScanTick;

    public SupportOrbTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        PlayerShellEntity orb = this.findNearestOrb();
        if (orb == null) {
            return false;
        }
        this.lockedOrb = orb;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        PlayerShellEntity orb = this.lockedOrb;
        if (orb == null || !orb.isAlive() || !orb.isOrbAttached()
            || orb.level() != this.mob.level()) {
            return false;
        }
        return this.mob.distanceToSqr(orb) <= KEEP_RANGE * KEEP_RANGE;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.lockedOrb);
        this.nextScanTick = this.mob.tickCount + RESCAN_TICKS;
    }

    @Override
    public void tick() {
        if (this.mob.tickCount < this.nextScanTick) {
            return;
        }
        this.nextScanTick = this.mob.tickCount + RESCAN_TICKS;
        // 定期重扫：换到更新的那具核心（比如原来那具没了、或者附近又来了一具更近的）
        PlayerShellEntity orb = this.findNearestOrb();
        if (orb != null && orb != this.lockedOrb) {
            this.lockedOrb = orb;
        }
        // 每刻都把目标按最新的锁定结果写回去（原版别处的 goal 可能刚把它改掉）
        this.mob.setTarget(this.lockedOrb);
    }

    @Override
    public void stop() {
        this.lockedOrb = null;
        this.mob.setTarget(null);
    }

    /** 50 格内最近的那具"被核心附体的玩家"；没有就返回 null。 */
    @Nullable
    private PlayerShellEntity findNearestOrb() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return null;
        }
        List<PlayerShellEntity> shells = level.getEntitiesOfClass(PlayerShellEntity.class,
            this.mob.getBoundingBox().inflate(ORB_RANGE),
            shell -> shell.isAlive() && shell.isOrbAttached() && !shell.isSpectator());
        PlayerShellEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerShellEntity shell : shells) {
            double distance = this.mob.distanceToSqr(shell);
            // 盒查询是方的：角落里那具实际可能在 70 格外，这里再按球半径筛一次
            if (distance > ORB_RANGE * ORB_RANGE) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = shell;
            }
        }
        return nearest;
    }
}
