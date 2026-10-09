package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.BeeBossData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * BeeBoss 的索敌目标（{@link Goal.Flag#TARGET}，挂在 {@code targetSelector} 优先级 0）：
 * <ul>
 *   <li><b>索敌半径 200 格</b>，候选仅限「蜘蛛与洞穴蜘蛛」和「玩家」两类；</li>
 *   <li><b>优先级：蜘蛛/洞穴蜘蛛 &gt; 玩家</b>——蜘蛛在场时绝不打玩家，且正在打玩家时一旦出现蜘蛛立即换目标；</li>
 *   <li>锁定目标后：<b>持杖</b>（远程模式）只重申目标、<b>不</b>进入怒气状态，因此绝不会主动贴身，
 *       站位与放蜂群/回收/休息循环都交给 {@link BeeBossRangedGoal}；<b>不持杖</b>（近战模式）才刷新「持久怒气」，
 *       由原版 {@code Bee.BeeAttackGoal} 负责追击与蛰刺。</li>
 * </ul>
 * <p>本 Goal 只占用 {@link Goal.Flag#TARGET}，不抢 MOVE/LOOK，移动与近战仍由原版蜜蜂 AI 处理。
 * <p>该目标常驻所有蜜蜂的 {@code targetSelector}，但 {@link #canUse()} 对非 BeeBoss 直接返回 false，
 * 因此普通蜜蜂与苦力蜂不受影响。
 */
public class BeeBossTargetGoal extends Goal {

    /** 索敌半径（格）。 */
    public static final double DETECT_RANGE = 200.0;
    /** 已锁定目标超出此距离即放弃（比索敌半径留有余量，避免边缘处反复锁定/丢失抖动）。 */
    public static final double DROP_RANGE = 260.0;
    /** 重新搜索目标的间隔（刻）：200 格范围的全量实体检索较贵，按秒级节流。 */
    public static final int SCAN_INTERVAL = 20;
    /** 每次刷新「持久怒气」的时长（刻）。原版 {@code NeutralMob#updatePersistentAnger} 每刻 -1，
     *  且蜜蜂蛰刺后 {@code Bee#doHurtTarget} 会 stopBeingAngry 清空目标，故必须持续刷新才能连续攻击。 */
    public static final int ANGER_TIME = 600;

    /** 索敌条件：沿用原版战斗条件（含视线判定与队伍/可攻击性判定），只额外限定半径。
     *  即「只有看得见的目标才会被发现」，与原版 NearestAttackableTargetGoal 一致。 */
    private static final TargetingConditions TARGET_CONDITIONS =
        TargetingConditions.forCombat().range(DETECT_RANGE);

    /** 维持条件：已锁定目标后的复查条件，<b>不做视线判定</b>——否则目标一躲到墙后 Boss 就会立刻放弃，
     *  与「发现后持续追击」的预期不符（原版怪物同样不会因短暂失去视线而丢失目标）。 */
    private static final TargetingConditions KEEP_CONDITIONS =
        TargetingConditions.forCombat().range(DROP_RANGE).ignoreLineOfSight();

    private final Bee bee;
    private LivingEntity target;
    /** 下一次允许搜索目标的倒计时（刻）。 */
    private int scanCooldown;
    /** 优先级复查的倒计时（刻）：用于「打玩家时检查是否出现蜘蛛」。 */
    private int priorityCooldown;

    public BeeBossTargetGoal(Bee bee) {
        this.bee = bee;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /** 幂等地把本目标装到蜜蜂的 {@code targetSelector}（优先级 0）。
     *  {@code applyBeeBossDefaults} 会在命令召唤与 NBT 读取时调用，可能对同一只蜜蜂重复执行，
     *  故先查重再添加，避免目标越挂越多。 */
    public static void ensureInstalled(Bee bee) {
        for (WrappedGoal wrapped : bee.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BeeBossTargetGoal) {
                return;
            }
        }
        bee.targetSelector.addGoal(0, new BeeBossTargetGoal(bee));
    }

    private boolean isBoss() {
        return BeeBossData.isBeeBoss(this.bee) && !this.bee.isNoAi() && this.bee.isAlive();
    }

    @Override
    public boolean canUse() {
        if (!this.isBoss()) {
            return false;
        }
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.target = this.findTarget();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.isBoss() || this.target == null || !this.target.isAlive()) {
            return false;
        }
        // 维持阶段用不含视线判定的条件，只校验「仍是合法候选 + 仍在 DROP_RANGE 内」
        return this.bee.canAttack(this.target, KEEP_CONDITIONS);
    }

    @Override
    public void start() {
        this.priorityCooldown = SCAN_INTERVAL;
        this.applyTarget();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        // 优先级维护：蜘蛛/洞穴蜘蛛 > 玩家。当前目标不是蜘蛛时，定期复查是否出现了蜘蛛。
        if (!(this.target instanceof Spider) && --this.priorityCooldown <= 0) {
            this.priorityCooldown = SCAN_INTERVAL;
            Spider spider = this.findNearestSpider();
            if (spider != null) {
                this.target = spider;
            }
        }
        this.applyTarget();
    }

    @Override
    public void stop() {
        // 放弃目标时一并清掉蜜蜂身上残留的持久怒气：否则原版 BeeAttackGoal 仍会带着怒气追着旧目标
        // 跑完剩余怒气时长（ANGER_TIME 刻），与「超出 DROP_RANGE 就放弃」的意图冲突。
        if (this.target != null && this.bee.getTarget() == this.target) {
            this.bee.setTarget(null);
            this.bee.setPersistentAngerTarget(null);
            this.bee.setRemainingPersistentAngerTime(0);
        }
        this.target = null;
        this.scanCooldown = 0;
    }

    /** 重申目标；<b>近战模式</b>下同时维持持久怒气，使原版 {@code Bee.BeeAttackGoal}
     *  （要求 {@code isAngry} 且 {@code !hasStung}）持续追击并蛰刺。
     *  <p><b>远程模式</b>（持蜂巢权杖）下刻意<b>不</b>进入怒气状态：原版近战目标的前提就是 isAngry，
     *  不设怒气即可确保 Boss 不会主动贴身，位移全部交给 {@link BeeBossRangedGoal}。 */
    private void applyTarget() {
        if (this.target == null || !this.target.isAlive()) {
            return;
        }
        this.bee.setTarget(this.target);
        if (BeeBossData.isRangedMode(this.bee)) {
            return;
        }
        this.bee.setPersistentAngerTarget(this.target.getUUID());
        this.bee.setRemainingPersistentAngerTime(ANGER_TIME);
    }

    /** 先找蜘蛛，找不到再找玩家——即「蜘蛛/洞穴蜘蛛 > 玩家」的优先级。 */
    private LivingEntity findTarget() {
        Spider spider = this.findNearestSpider();
        return spider != null ? spider : this.findNearestPlayer();
    }

    /** 最近的蜘蛛或洞穴蜘蛛（{@code CaveSpider extends Spider}，一次检索即覆盖两者）。 */
    private Spider findNearestSpider() {
        AABB box = this.bee.getBoundingBox().inflate(DETECT_RANGE);
        Spider best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Spider spider : this.bee.level().getEntitiesOfClass(Spider.class, box, this::isValidCandidate)) {
            double distSqr = this.bee.distanceToSqr(spider);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = spider;
            }
        }
        return best;
    }

    /** 最近的玩家。玩家总数很少，直接遍历在线玩家列表，比 200 格的世界范围检索便宜得多。 */
    private Player findNearestPlayer() {
        Player best = null;
        double bestSqr = DETECT_RANGE * DETECT_RANGE;
        for (Player player : this.bee.level().players()) {
            if (!isValidCandidate(player)) {
                continue;
            }
            double distSqr = this.bee.distanceToSqr(player);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = player;
            }
        }
        return best;
    }

    /** 搜索阶段的候选过滤：存活、非旁观、非创造（与原版近战目标一致），且通过原版战斗索敌条件（含视线）。 */
    private boolean isValidCandidate(LivingEntity candidate) {
        if (candidate == this.bee || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player player && player.isCreative()) {
            return false;
        }
        return this.bee.canAttack(candidate, TARGET_CONDITIONS);
    }
}
