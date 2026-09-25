package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 女仆“权杖攻击”任务中，冰块权杖（ice）的专属行为。
 * <ul>
 *   <li>索敌由 {@link TaskStaffAttack#findAttackTarget} 提供：持冰块权杖时自动排除
 *       已被霜冰困住（{@link ModMain#isOverlappingFrost}）的目标；</li>
 *   <li>女仆走近目标至 {@link #FREEZE_RANGE} 格内且可见后，在冷却结束对目标使用权杖：
 *       施加 5 秒挖掘疲劳 + 细雪冻结，并把其碰撞箱触及的方块替换为霜冰（融化后还原），
 *       目标随即被困在原地；</li>
 *   <li>目标已被困住时放弃该目标（清除攻击目标记忆，由索敌重新寻找下一个未被困的敌对生物）。</li>
 * </ul>
 * 该行为只在主手持有冰块权杖且有攻击目标时启动；不持冰块权杖时由
 * {@link TaskStaffAttack} 中的通用近战行为接管（两者通过条件互斥，不会双重攻击）。
 */
public class MaidIceStaffAttackTask extends Behavior<EntityMaid> {
    /** 冻结生效距离（格）：女仆与目标距离 ≤ 该值且可见时才能冻结。 */
    private static final double FREEZE_RANGE = 10.0;
    /** 冻结冷却（tick）：每 3 秒（60 刻）至多冻结一次，防止每刻尝试。 */
    private static final long FREEZE_COOLDOWN = 60;

    /** 下一次冻结的游戏时间；-1 表示尚未开始。 */
    private long nextFreezeAt = -1;

    public MaidIceStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            1200);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        nextFreezeAt = gameTime + FREEZE_COOLDOWN;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isIceStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isIceStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return;

        // 目标已被霜冰困住：放弃该目标，由索敌寻找下一个未被困的敌对生物
        if (ModMain.isOverlappingFrost(target)) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }

        BehaviorUtils.lookAtEntity(maid, target);

        if (maid.distanceToSqr(target) <= FREEZE_RANGE * FREEZE_RANGE && BehaviorUtils.canSee(maid, target)) {
            // 进入冻结范围且可见：冷却结束后，在确认霜冰替换范围内没有女仆自身/主人/其他宠物时
            // 才对目标使用权杖使其被困在霜冰中；不安全则等待下一冷却周期再判定
            if (gameTime >= nextFreezeAt && isFreezeSafe(level, maid, target)) {
                ModMain.freezeLivingEntity(level, target);
                maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                nextFreezeAt = gameTime + FREEZE_COOLDOWN;
            }
        } else if (maid.canBrainMoving()) {
            // 过远/不可见：走近目标（与其它权杖统一 0.6 移速）
            maid.getNavigation().moveTo(target, 0.6);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        nextFreezeAt = -1;
        maid.getNavigation().stop();
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /**
     * 判定冻结是否安全：目标自身除外，若女仆自身、主人或主人的其他宠物（含其他女仆、任意已驯服宠物）
     * 位于将被替换为霜冰的目标碰撞箱区域内，则不安全，应等待下一冷却周期再判定，
     * 避免把贴在一起的玩家/宠物一同冻进霜冰。
     */
    private boolean isFreezeSafe(ServerLevel level, EntityMaid maid, LivingEntity target) {
        AABB affected = target.getBoundingBox().inflate(0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, affected,
            e -> e != target && e.isAlive())) {
            if (e == maid) return false;
            if (isProtectedAlly(maid, e)) return false;
        }
        return true;
    }

    /** 是否受保护：主人、其他女仆、已驯服宠物（含任意玩家的宠物）。 */
    private boolean isProtectedAlly(EntityMaid maid, LivingEntity e) {
        if (e == maid.getOwner()) return true;
        if (e instanceof EntityMaid) return true;
        return e instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;
    }

    /** 是否为冰块权杖（ice 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isIceStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "ice".equals(blockType);
    }
}
