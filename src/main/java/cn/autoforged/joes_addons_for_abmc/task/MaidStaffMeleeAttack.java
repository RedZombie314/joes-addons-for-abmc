package cn.autoforged.joes_addons_for_abmc.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/**
 * 女仆“权杖攻击”职业的通用近战行为（用于被动属性型权杖；Him 权杖由 MaidHimStaffAttackTask 负责）。
 *
 * 与女仆自带的 MaidMeleeAttack 相比：
 * 1. 不再依赖传感器缓存的 NEAREST_VISIBLE_LIVING_ENTITIES——坐姿/远离“家”时传感器以家为中心的
 *    搜索框常不含目标，导致近战永远无法触发；改为直接以目标距离 + 视线判断；
 * 2. 女仆无法移动（坐下/骑乘/睡觉/被拴绳，{@code canBrainMoving()==false}）时，近战自卫范围扩大到
 *    {@link TaskStaffAttack#SITTING_MELEE_RANGE}（5.5 格），消除“够不着近战、也够不着射击”的空档。
 */
public class MaidStaffMeleeAttack extends Behavior<EntityMaid> {

    public MaidStaffMeleeAttack() {
        super(ImmutableMap.of(
            MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
            MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        LivingEntity target = getAttackTarget(maid);
        return target != null && target.isAlive()
            && isWithinMeleeRange(maid, target)
            && maid.getSensing().hasLineOfSight(target);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null) return;
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        maid.swing(InteractionHand.MAIN_HAND);
        maid.doHurtTarget(target);
        double attackSpeed = maid.getAttributeValue(Attributes.ATTACK_SPEED);
        long cooldown = attackSpeed > 0 ? (long) (20 / attackSpeed) : 20;
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldown);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        // 一击即止：冷却期间由 ATTACK_COOLING_DOWN 阻止该行为重启，冷却结束即可再次攻击。
        return false;
    }

    /** 站姿用女仆原版近战范围；无法移动时用更大的自卫范围。 */
    private boolean isWithinMeleeRange(EntityMaid maid, LivingEntity target) {
        if (maid.canBrainMoving()) {
            return maid.isWithinMeleeAttackRange(target);
        }
        return maid.distanceTo(target) <= TaskStaffAttack.SITTING_MELEE_RANGE;
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }
}
