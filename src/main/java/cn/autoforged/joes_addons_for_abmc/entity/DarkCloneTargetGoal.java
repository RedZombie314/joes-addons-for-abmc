package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 黑暗分身的通用索敌 goal，由 {@link DarkCloneHelper#installTargetGoal} 挂在
 * {@code Mob#targetSelector} 上（并已清空原版全部选目标 goal，所以它是唯一的目标来源）。
 *
 * <p>写成 goal 而不是「每个 tick 直接 {@code setTarget}」的原因：goal 天然参与原版的
 * 目标调度（{@code GoalSelector.tick()}、控制标记、与移动 / 攻击 goal 的协作），
 * 直接改 {@code setTarget} 会和原版行为互相覆盖。
 *
 * <p>不具备攻击力的分身（羊这类被动生物）直接 {@code canUse() == false}，
 * 于是不占用任何目标状态，完全保持原版 AI——这正是需求里「不会有特殊行为」的落点。
 */
public class DarkCloneTargetGoal extends Goal {

    /** 两次扫描之间的间隔（tick）。目标存活期间不会扫描，因此这个节流只在「没目标」时生效。 */
    private static final int SCAN_INTERVAL_TICKS = 10;

    private final Mob mob;
    private long nextScanTick;
    @Nullable
    private LivingEntity pendingTarget;

    public DarkCloneTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!DarkCloneAttachments.isDarkClone(this.mob)) return false;
        if (!DarkCloneHelper.hasAttackCapability(this.mob)) return false;

        long now = this.mob.level().getGameTime();
        if (now < this.nextScanTick) return false;
        this.nextScanTick = now + SCAN_INTERVAL_TICKS;

        this.pendingTarget = DarkCloneHelper.pickTarget(this.mob);
        return this.pendingTarget != null;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.pendingTarget);
        this.pendingTarget = null;
    }

    @Override
    public boolean canContinueToUse() {
        return DarkCloneHelper.canTarget(this.mob, this.mob.getTarget());
    }

    @Override
    public void stop() {
        this.pendingTarget = null;
        this.mob.setTarget(null);
    }
}
