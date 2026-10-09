package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.BeeBossData;
import cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * BeeBoss 持杖时的<b>远程行为</b>（{@link Goal.Flag#MOVE} + {@link Goal.Flag#LOOK}，goalSelector 优先级 -1）：
 * 只在<b>远程模式</b>（持杖且未切换近战，见 {@link BeeBossData#isRangedMode}）下运行，负责整条
 * 「放蜂群 → 回收 → 休息」循环，并全程维持在目标 {@link #STANDOFF_DISTANCE} 格的站位：
 *
 * <ol>
 *   <li><b>READY</b>：手上有蜂群且目标进入 {@link #RELEASE_DISTANCE} → 按玩家左键的逻辑把蜂群全部放出，转 HARASS；
 *       若手上没有蜂群（读档后蜂群还在外面、或已被收回）→ 转 RECALL 去收。</li>
 *   <li><b>HARASS</b>：观察本次放出蜜蜂的 {@code HasStung}，每 {@link #SWARM_CHECK_INTERVAL} 刻统计一次，
 *       超过 {@link #STUNG_NUMERATOR}/{@link #STUNG_DENOMINATOR}（3/5）已蛰刺即转 RECALL；
 *       超过 {@link #HARASS_MAX_TICKS} 未达标也回收（兜底）。</li>
 *   <li><b>RECALL</b>：像玩家那样「右键长按」——每刻调用 {@link BeehiveStaffHelper#absorbTick} 吸附周围 50 格内的蜜蜂，
 *       同时飞向<b>存活蜂群的质心</b>（Boss 不飞过去就吸不到）。</li>
 *   <li><b>REST</b>：休息 {@link #REST_TICKS}（统一 10 秒）后回到 READY，把手上的蜂群重新放出去索敌。</li>
 * </ol>
 *
 * <p><b>目标切换 / 目标消失的处理</b>：
 * <ul>
 *   <li>换到新目标时，把<b>仍然留有蛰针</b>（{@code !hasStung()}）的蜂群重新指定到新目标，蜂群继续作战，
 *       3/5 的回收判定照旧；已蛰过的蜜蜂没有蛰针可用，不再参与。</li>
 *   <li>目标消失（被打死/跑掉）时给索敌目标 {@link #TARGET_LOST_GRACE_TICKS} 的宽限去找下一个敌人；
 *       宽限内锁到新目标就继续用这批蜂群，锁不到就把蜂群收回来——不会出现「目标死了蜂群扔在外面不管」。</li>
 * </ul>
 *
 * <p>实现方式：非回收阶段始终朝「目标周围 {@link #STANDOFF_DISTANCE} 格、沿当前方位」的那一点飞——
 * 比它远就自然靠近，比它近就自然拉开，一条规则同时覆盖进退。
 * 目标进入 {@link #KEEP_AWAY_DISTANCE} 格内时，额外直接给一个向外的速度立即脱离
 * （蜜蜂的平滑 MoveControl 会忽略 {@code moveTo} 的速度参数，只有直接设速度才走得快，
 * 这与 {@link BeehiveStaffHelper} 吸收蜜蜂时提速的做法一致）。
 *
 * <p>远程模式下 Boss 不进入怒气状态，原版 {@code Bee.BeeAttackGoal} 就不会运行，
 * 因此本目标是唯一的位移来源，不会出现「一边后退一边被近战目标拽过去」的情况。
 */
public class BeeBossRangedGoal extends Goal {

    /** 站位环半径：远程模式下与目标的期望停留距离。 */
    public static final double STANDOFF_DISTANCE = 20.0;

    /** 贴身警戒线：目标近于此距离时主动后撤（与站位环同值——需求已取消原先的 50 格）。 */
    public static final double KEEP_AWAY_DISTANCE = 20.0;

    /** 释放蜂群的触发距离：飞近到站位环附近就放出（略大于环半径，避免刚好卡在环外不动）。
     *  近距离释放同时保证蜂群能在仇视时长内扑到目标。 */
    public static final double RELEASE_DISTANCE = 28.0;

    /** 回收时以「存活蜂群质心」为目标，飞到这个距离内即停（再近就是挤进蜂团里了）。 */
    public static final double RECALL_ARRIVE_DISTANCE = 12.0;

    /** 「右键长按」回收的最长持续时长（刻）：15 秒——够它从站位环飞到蜂群身边并等蜜蜂飞进吸收范围。 */
    public static final int RECALL_TICKS = 300;

    /** 回收期间「连续这么多刻没再吸到蜜蜂」就认为附近已收干净，提前结束回收（不必空吸满 15 秒）。 */
    public static final int RECALL_IDLE_ABORT_TICKS = 6 * 20;

    /** 休息时长（刻）：统一 10 秒。 */
    public static final int REST_TICKS = 10 * 20;

    /** 蜂群在外作战的最长时长（刻）：超过就无条件回收。
     *  兜底用——万一蜂群追不上目标（目标跑远/被卡住），Boss 不会永远停在 HARASS 不回收。 */
    public static final int HARASS_MAX_TICKS = 60 * 20;

    /** 目标消失后等待新目标出现的宽限期（刻）：2 秒。
     *  索敌目标是每刻都可能锁到下一个敌人的，给一小段宽限就不必因为「目标刚死」而白跑一次回收。 */
    public static final int TARGET_LOST_GRACE_TICKS = 2 * 20;

    /** 判定「蜂群耗尽」的蛰刺比例：超过 3/5 即回收（{@code stung * 5 > total * 3}）。 */
    public static final int STUNG_NUMERATOR = 3;
    public static final int STUNG_DENOMINATOR = 5;

    /** 蜂群蛰刺比例的检查间隔（刻）。 */
    private static final int SWARM_CHECK_INTERVAL = 20;

    /** 站位环死区：与环半径的偏差在此范围内就保持悬停，不重新下发路径，避免抖动。 */
    private static final double DEADBAND = 6.0;

    /** 重新下发路径的间隔（刻）。 */
    private static final int REPATH_INTERVAL = 10;

    /** 脱离贴身时直接施加的向外速度（格/刻）。 */
    private static final double ESCAPE_SPEED = 0.6;

    /** 蜂群循环阶段。 */
    private enum Phase {
        /** 手上有蜂群，等待目标进入释放距离后放出。 */
        READY,
        /** 蜂群在外面作战，统计其 HasStung。 */
        HARASS,
        /** 正在「右键长按」回收周围的蜜蜂。 */
        RECALL,
        /** 回收完毕，休息 10 秒。 */
        REST
    }

    private final Bee bee;
    private int repathCooldown;
    /** 进入站位环死区时的悬停锚点：留在环上时持续朝这个点导航，避免飞行动物因没有导航目标而缓慢下沉。 */
    private Vec3 hoverAnchor;
    /** 当前阶段。 */
    private Phase phase = Phase.READY;
    /** 当前阶段剩余刻数（RECALL / REST 用）。 */
    private int phaseTicks;
    /** 已放出的蜂群在外作战了多久（刻）。 */
    private int harassTicks;
    /** 当前无目标已持续多少刻（用于目标消失后的宽限）。 */
    private int targetLostTicks;
    /** 上一次处理的目标，用于检测「换了目标」。 */
    private LivingEntity lastTarget;
    /** 蜂群是否还在外面（放出后置位、回收结束时清除）：决定「没有目标」时是否还要继续跑完回收流程。 */
    private boolean swarmDeployed;
    /** 本次放出、用于统计 HasStung 的蜜蜂。 */
    private final List<Bee> deployed = new ArrayList<>();
    /** 蜂群蛰刺比例的检查倒计时（刻）。 */
    private int swarmCheckCooldown;
    /** 回收阶段：上一次记录到的权杖蜜蜂数，用于判断是否还在陆续吸到。 */
    private int recallLastCount;
    /** 回收阶段：连续多少刻没再吸到蜜蜂。 */
    private int recallIdleTicks;

    public BeeBossRangedGoal(Bee bee) {
        this.bee = bee;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** 幂等地把本目标装到 {@code goalSelector}，优先级 -1。
     *  <p>取 -1 有两个原因：一是原版蜜蜂的 goalSelector 已占满 0~9，避免同优先级在有序集合里互相挤掉；
     *  二是要压过优先级 0 的原版 {@code Bee.BeeAttackGoal}，确保本目标优先拿到 MOVE/LOOK。 */
    public static void ensureInstalled(Bee bee) {
        for (WrappedGoal wrapped : bee.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BeeBossRangedGoal) {
                return;
            }
        }
        bee.goalSelector.addGoal(-1, new BeeBossRangedGoal(bee));
    }

    private boolean inRangedMode() {
        return BeeBossData.isBeeBoss(this.bee) && BeeBossData.isRangedMode(this.bee)
            && !this.bee.isNoAi() && this.bee.isAlive();
    }

    private boolean hasTarget() {
        LivingEntity target = this.bee.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canUse() {
        // 有目标就正常工作；没有目标但蜂群还在外面也必须继续（否则蜂群就扔在外面不管了）
        return this.inRangedMode() && (this.hasTarget() || this.swarmDeployed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.phase = Phase.READY;
    }

    @Override
    public void stop() {
        this.phase = Phase.READY;
        this.phaseTicks = 0;
        this.harassTicks = 0;
        this.targetLostTicks = 0;
        this.lastTarget = null;
        this.swarmDeployed = false;
        this.deployed.clear();
        this.hoverAnchor = null;
        this.bee.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.bee.getTarget();
        if (target != null && !target.isAlive()) {
            target = null; // 保险：目标已死就当作没有目标（宽限逻辑会接手）
        }
        if (target != null) {
            this.targetLostTicks = 0;
            // 换目标：把还留着蛰针的蜂群改指到新目标（已蛰过的没有蛰针可用，不参与）
            if (target != this.lastTarget) {
                this.retargetSwarm(target);
                this.lastTarget = target;
            }
            this.bee.getLookControl().setLookAt(target, 360.0F, 360.0F);
        } else {
            this.targetLostTicks++;
        }

        this.tickSwarmCycle(target);

        if (this.phase == Phase.RECALL) {
            // 回收阶段：飞向蜂群质心，否则蜂群远在 50 格吸收半径之外，一只也吸不到
            this.flyTo(this.recallPoint(target), RECALL_ARRIVE_DISTANCE);
        } else if (target != null) {
            this.keepDistance(target);
        }
    }

    /** 蜂群循环状态机：放蜂群 → 观察 HasStung → 回收 → 休息 → 再放。{@code target} 可能为 null（失去目标）。 */
    private void tickSwarmCycle(LivingEntity target) {
        ItemStack staff = this.bee.getMainHandItem();
        if (this.phase == Phase.READY) {
            if (BeehiveStaffHelper.countBees(staff) <= 0) {
                // 手上没有蜂群：直接转入回收，把外面（或周围）的蜜蜂收回来
                this.enterRecall();
                return;
            }
            if (target == null) {
                return; // 有蜂群但没有目标：原地等（本目标会因「无目标且蜂群不在外」而自行结束）
            }
            if (this.bee.distanceToSqr(target) > RELEASE_DISTANCE * RELEASE_DISTANCE) {
                return; // 还没飞近到释放距离，先继续靠近
            }
            this.deployed.clear();
            this.deployed.addAll(BeehiveStaffHelper.releaseAllToTarget(this.bee, target,
                BeehiveStaffHelper.RELEASED_BEE_ANGER_TICKS_BOSS));
            this.swarmDeployed = true;
            this.harassTicks = 0;
            this.swarmCheckCooldown = SWARM_CHECK_INTERVAL;
            this.phase = Phase.HARASS;
        } else if (this.phase == Phase.HARASS) {
            if (target == null) {
                // 目标消失：宽限期内等索敌目标找下一个敌人；找不到就把蜂群收回来
                if (this.targetLostTicks >= TARGET_LOST_GRACE_TICKS) {
                    this.enterRecall();
                }
                return;
            }
            this.harassTicks++;
            if (this.harassTicks >= HARASS_MAX_TICKS) {
                this.enterRecall(); // 兜底：蜂群迟迟蛰不到目标也不能一直等
                return;
            }
            if (--this.swarmCheckCooldown > 0) {
                return;
            }
            this.swarmCheckCooldown = SWARM_CHECK_INTERVAL;
            if (this.isSwarmSpent()) {
                this.enterRecall();
            }
        } else if (this.phase == Phase.RECALL) {
            this.tickRecall(staff);
        } else if (this.phase == Phase.REST) {
            if (--this.phaseTicks <= 0) {
                this.phase = Phase.READY;
                this.deployed.clear();
            }
        }
    }

    /** 「右键长按」回收：每刻都吸，连续一段时间吸不到就提前结束。 */
    private void tickRecall(ItemStack staff) {
        BeehiveStaffHelper.absorbTick(this.bee);
        int count = BeehiveStaffHelper.countBees(staff);
        if (count > this.recallLastCount) {
            this.recallLastCount = count;
            this.recallIdleTicks = 0;
        } else if (++this.recallIdleTicks >= RECALL_IDLE_ABORT_TICKS) {
            this.finishRecall();
            return;
        }
        if (--this.phaseTicks <= 0) {
            this.finishRecall();
        }
    }

    private void enterRecall() {
        this.phase = Phase.RECALL;
        this.phaseTicks = RECALL_TICKS;
        this.recallLastCount = BeehiveStaffHelper.countBees(this.bee.getMainHandItem());
        this.recallIdleTicks = 0;
        this.hoverAnchor = null;
    }

    /** 回收结束：一只都没收回（蜂群全灭、或周围已无蜂可收）说明远程手段用尽，
     *  按需求直接切换为近战模式；否则休息 {@link #REST_TICKS}（统一 10 秒）后重新放出蜂群。 */
    private void finishRecall() {
        this.swarmDeployed = false;
        if (BeehiveStaffHelper.countBees(this.bee.getMainHandItem()) <= 0) {
            BeeBossData.setMeleeMode(this.bee, true);
            return;
        }
        this.phase = Phase.REST;
        this.phaseTicks = REST_TICKS;
    }

    /** 把「仍然留有蛰针」的蜂群改指到新目标；已蛰过（没有蛰针）的不再参与。该回收时仍按原规则回收。 */
    private void retargetSwarm(LivingEntity target) {
        if (!this.swarmDeployed) {
            return;
        }
        for (Bee bee : this.deployed) {
            if (!bee.isAlive() || bee.isRemoved() || bee.hasStung()) {
                continue;
            }
            bee.setTarget(target);
            bee.setPersistentAngerTarget(target.getUUID());
            bee.setRemainingPersistentAngerTime(BeehiveStaffHelper.RELEASED_BEE_ANGER_TICKS_BOSS);
        }
    }

    /** 本次放出的蜜蜂中，{@code HasStung} 为 true 的是否已超过 3/5（一只不剩也算耗尽）。 */
    private boolean isSwarmSpent() {
        if (this.deployed.isEmpty()) {
            return true;
        }
        int stung = 0;
        for (Bee bee : this.deployed) {
            if (bee.hasStung()) {
                stung++;
            }
        }
        return stung * STUNG_DENOMINATOR > this.deployed.size() * STUNG_NUMERATOR;
    }

    /** 回收时要去的位置：存活蜂群的质心；一只都不剩时退化为「目标周围 {@link #STANDOFF_DISTANCE} 格」，
     *  目标也没有就原地悬停。 */
    private Vec3 recallPoint(LivingEntity target) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int count = 0;
        for (Bee bee : this.deployed) {
            if (bee.isAlive() && !bee.isRemoved()) {
                sumX += bee.getX();
                sumY += bee.getY();
                sumZ += bee.getZ();
                count++;
            }
        }
        if (count > 0) {
            return new Vec3(sumX / count, sumY / count, sumZ / count);
        }
        if (target == null) {
            return this.bee.position();
        }
        Vec3 toBee = this.bee.position().subtract(target.position());
        return toBee.lengthSqr() < 1.0E-6
            ? target.position().add(0.0, 1.0, 0.0)
            : target.position().add(toBee.normalize().scale(STANDOFF_DISTANCE));
    }

    /** 站位：飞向「目标周围 {@link #STANDOFF_DISTANCE} 格、沿当前方位」的那一点：比它远就靠近、比它近就拉开。
     *  目标进入 {@link #KEEP_AWAY_DISTANCE} 时额外直接给向外的速度，立刻脱离。 */
    private void keepDistance(LivingEntity target) {
        double distSqr = this.bee.distanceToSqr(target);
        if (distSqr < KEEP_AWAY_DISTANCE * KEEP_AWAY_DISTANCE) {
            Vec3 away = this.bee.position().subtract(target.position());
            Vec3 dir = away.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
            this.bee.setDeltaMovement(dir.scale(ESCAPE_SPEED));
        }
        double dist = Math.sqrt(distSqr);
        if (Math.abs(dist - STANDOFF_DISTANCE) <= DEADBAND) {
            this.hover();
            return;
        }
        Vec3 toBee = this.bee.position().subtract(target.position());
        Vec3 ring = toBee.lengthSqr() < 1.0E-6
            ? target.position().add(0.0, 1.0, 0.0)
            : target.position().add(toBee.normalize().scale(STANDOFF_DISTANCE));
        this.repathTo(ring);
    }

    /** 飞向一个具体坐标；已进入 arriveDistance 就保持悬停。 */
    private void flyTo(Vec3 dest, double arriveDistance) {
        if (this.bee.position().distanceToSqr(dest) <= arriveDistance * arriveDistance) {
            this.hover();
            return;
        }
        this.repathTo(dest);
    }

    /** 朝悬停锚点导航：没有任何导航目标时，飞行动物会受重力缓慢下沉到地面。 */
    private void hover() {
        if (this.hoverAnchor == null) {
            this.hoverAnchor = this.bee.position();
        }
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL;
        this.bee.getNavigation().moveTo(this.hoverAnchor.x, this.hoverAnchor.y, this.hoverAnchor.z, 1.0);
    }

    /** 按固定间隔下发一次寻路指令（避免每刻重算路径）。 */
    private void repathTo(Vec3 dest) {
        this.hoverAnchor = null;
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL;
        this.bee.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0);
    }
}
