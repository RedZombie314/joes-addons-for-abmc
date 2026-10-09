package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 召唤物的<b>共享索敌</b>：直接抄盟友（那具被附体的"玩家"空壳）当前的目标。
 *
 * <p>空壳每隔 10 刻用 {@link PlayerShellEntity#findPossessionTarget()} 重新索敌一次
 * （已经排除了任何 {@link OrbPossessionSummons} 召唤物），Bob 们只要跟着抄就行 ——
 * 于是所有召唤物与空壳天然集火同一个目标，也不会互相打。
 *
 * <p>挂在 {@code targetSelector} 上、优先级 0；召唤 Bob 时会先把它原本的目标 goal
 * 全部清掉（原版僵尸会自己找玩家/村民/铁傀儡，那样"共享索敌"就会被它自己覆盖掉）。
 * 空壳没目标时本 goal 自动失效，Bob 也就安静待命。
 */
public class OrbSummonTargetGoal extends Goal {

    private final Mob mob;
    /** 盟友（被附体空壳）的 UUID。 */
    private final UUID allyUuid;

    public OrbSummonTargetGoal(Mob mob, UUID allyUuid) {
        this.mob = mob;
        this.allyUuid = allyUuid;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return this.resolveSharedTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.resolveSharedTarget() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.resolveSharedTarget());
    }

    @Override
    public void tick() {
        // 空壳换目标时跟着换（它每 10 刻重扫一次）
        this.mob.setTarget(this.resolveSharedTarget());
    }

    @Override
    public void stop() {
        this.mob.setTarget(null);
    }

    /** 盟友空壳当前的目标；盟友不在/没目标时返回 null。 */
    private LivingEntity resolveSharedTarget() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!(level.getEntity(this.allyUuid) instanceof PlayerShellEntity shell)) {
            return null;
        }
        LivingEntity target = shell.getTarget();
        if (target == null || !target.isAlive() || target == this.mob) {
            return null;
        }
        // 视线被挡住就不认这个目标（参考原版 TargetGoal#mustSee / TargetingConditions#checkLineOfSight）。
        // 否则盟友隔着墙锁住一个敌人时，召唤物会照样冲上去对着墙输出。
        // 走的是<b>空壳那把尺子</b>（{@link PlayerShellEntity#shouldSeePossessionTarget}）：
        // 空壳的视线判定对高个子目标多一步"瞄身体中心"的兜底，召唤物用同一套才不会
        // 出现"空壳看得见、召唤物看不见"的割裂。
        // 这里没有宽限期：空壳那边自己就有 1 秒的"看不见也不换目标"记忆
        // （{@code POSSESS_TARGET_UNSEEN_MEMORY_TICKS}），这一层只做最终过滤。
        return shell.shouldSeePossessionTarget(target) ? target : null;
    }
}
