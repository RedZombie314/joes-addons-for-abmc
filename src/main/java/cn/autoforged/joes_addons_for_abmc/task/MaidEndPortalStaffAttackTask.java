package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.PortalEntity;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 女仆“权杖攻击”任务中，传送门权杖（end_portal_frame）的专属行为。
 * <ul>
 *   <li>每 {@link #PORTAL_INTERVAL} 游戏刻（5 秒）在索敌目标正下方/正上方 {@link #VERTICAL_OFFSET} 格处
 *       各创建一扇水平平放（竖直朝向、面朝上下）的传送门并互相配对；</li>
 *   <li>创建前检查传送门影响范围内是否还有猪人（僵尸猪灵）、女仆自身或主人的其他宠物（含其他女仆，以及主人），
 *       若有则不创建，改为 {@link #SAFETY_RETRY_INTERVAL} 游戏刻（1 秒）后再判定，直至不受影响才创建；</li>
 *   <li>创建 {@link #COLLAPSE_DELAY} 游戏刻（5 刻）后提交塌缩：一对传送门以越来越快的速度相互靠近，
 *       重合后把范围内的实体传送到物理维度并删除这对传送门（与玩家版传送门权杖 R 键收敛一致）。</li>
 * </ul>
 * 该行为只在主手持有传送门权杖且有攻击目标时启动；不持传送门权杖时由
 * {@link TaskStaffAttack} 中的通用近战行为接管（两者通过条件互斥，不会双重攻击）。
 * 目标由 {@link TaskStaffAttack#findAttackTarget} 提供：持传送门权杖时按最大生命值由低到高优先索敌。
 */
public class MaidEndPortalStaffAttackTask extends Behavior<EntityMaid> {
    /** 创建一对传送门的间隔（tick）：每 5 秒（100 刻）一次。 */
    private static final long PORTAL_INTERVAL = 100;
    /** 创建后等待多久提交塌缩（tick）：5 刻。 */
    private static final long COLLAPSE_DELAY = 5;
    /** 目标上下传送门的垂直偏移（格）。 */
    private static final double VERTICAL_OFFSET = 2.0;
    /** 传送门盘面朝向：90° 俯仰使盘面水平平放（竖直朝向、面朝上下），上下夹住目标后竖直收敛。 */
    private static final float HORIZONTAL_PITCH = 90.0F;
    /** 塌缩初速 / 每刻加速度，与玩家版传送门权杖 R 键收敛一致。 */
    private static final double COLLAPSE_SPEED = 0.1;
    private static final double COLLAPSE_ACCEL = 0.05;
    /** 创建前安全检查不通过时，等待多久再判定（tick）：1 秒（20 刻）。 */
    private static final long SAFETY_RETRY_INTERVAL = 20;
    /** 传送门影响范围的横向判定余量（格）：盘面半径 1 + 0.5 判定余量。 */
    private static final double AFFECTED_MARGIN = 1.5;

    /** 下一次创建传送门的游戏时间；-1 表示尚未开始。 */
    private long nextPortalAt = -1;
    /** 已创建但尚未提交塌缩的一对传送门 id（一扇为 -1 表示当前没有待塌缩的对）。 */
    private int pendingPortalA = -1;
    private int pendingPortalB = -1;
    /** 提交塌缩的游戏时间；-1 表示无待提交塌缩。 */
    private long collapseAt = -1;

    public MaidEndPortalStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            1200);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        nextPortalAt = gameTime + PORTAL_INTERVAL;
        pendingPortalA = -1;
        pendingPortalB = -1;
        collapseAt = -1;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isEndPortalStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isEndPortalStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return;

        BehaviorUtils.lookAtEntity(maid, target);

        // 先提交已到期的一对传送门塌缩
        if (collapseAt > 0 && gameTime >= collapseAt) {
            if (pendingPortalA > 0 && pendingPortalB > 0
                && level.getEntity(pendingPortalA) instanceof PortalEntity
                && level.getEntity(pendingPortalB) instanceof PortalEntity) {
                ModMain.submitPortalCollapse(pendingPortalA, pendingPortalB, COLLAPSE_SPEED, COLLAPSE_ACCEL);
                level.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                    SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            pendingPortalA = -1;
            pendingPortalB = -1;
            collapseAt = -1;
        }

        // 每 5 秒在目标上下 2 格创建一对传送门；若影响范围内有猪人/女仆自身/主人的其他宠物（含主人），
        // 则不创建，等 1 秒后再判定，直至不受影响才创建
        if (gameTime >= nextPortalAt) {
            if (isSpawnSafe(level, maid, target)) {
                spawnPortalPair(level, target);
                nextPortalAt = gameTime + PORTAL_INTERVAL;
            } else {
                nextPortalAt = gameTime + SAFETY_RETRY_INTERVAL;
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // 行为中断（换权杖/丢失目标）时清理未塌缩的传送门，避免残留
        if (pendingPortalA > 0) discardPortal(level, pendingPortalA);
        if (pendingPortalB > 0) discardPortal(level, pendingPortalB);
        pendingPortalA = -1;
        pendingPortalB = -1;
        collapseAt = -1;
        nextPortalAt = -1;
        maid.getNavigation().stop();
    }

    /**
     * 判定创建一对传送门是否安全：目标自身除外，若猪人（僵尸猪灵）、女仆自身、主人
     * 或主人的其他宠物（含其他女仆、任意已驯服宠物）位于传送门创建/塌缩的影响范围内，
     * 则不安全，应等待 {@link #SAFETY_RETRY_INTERVAL} 后再判定。
     * <p>
     * 影响范围覆盖上下两扇水平传送门盘面（横向 ±{@link #AFFECTED_MARGIN}）与竖直方向
     * 自下方门底到上方门顶的整个区间（两门只会在该区间内竖直收敛，不会超出）。
     */
    private boolean isSpawnSafe(ServerLevel level, EntityMaid maid, LivingEntity target) {
        double cx = target.getX();
        double cz = target.getZ();
        double ty = target.getY();
        double th = target.getBbHeight();
        AABB affected = new AABB(cx - AFFECTED_MARGIN, ty - VERTICAL_OFFSET - 0.75, cz - AFFECTED_MARGIN,
            cx + AFFECTED_MARGIN, ty + th + VERTICAL_OFFSET + 0.75, cz + AFFECTED_MARGIN);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, affected, e -> e != target && e.isAlive())) {
            if (e == maid) return false;
            if (e instanceof ZombifiedPiglin) return false;
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

    /** 在目标正下方/正上方 {@link #VERTICAL_OFFSET} 格处创建一对互相配对的水平传送门。 */
    private void spawnPortalPair(ServerLevel level, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 below = new Vec3(center.x, target.getY() - VERTICAL_OFFSET, center.z);
        Vec3 above = new Vec3(center.x, target.getY() + target.getBbHeight() + VERTICAL_OFFSET, center.z);

        PortalEntity portalBelow = PortalEntity.create(level, below, 0.0F, HORIZONTAL_PITCH);
        PortalEntity portalAbove = PortalEntity.create(level, above, 0.0F, HORIZONTAL_PITCH);
        level.addFreshEntity(portalBelow);
        level.addFreshEntity(portalAbove);
        portalBelow.setLinkedPortalId(portalAbove.getId());
        portalAbove.setLinkedPortalId(portalBelow.getId());
        pendingPortalA = portalBelow.getId();
        pendingPortalB = portalAbove.getId();
        collapseAt = level.getGameTime() + COLLAPSE_DELAY;
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void discardPortal(ServerLevel level, int portalId) {
        if (level.getEntity(portalId) instanceof PortalEntity portal && !portal.isRemoved()) {
            portal.discard();
        }
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /** 是否为传送门权杖（end_portal_frame 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isEndPortalStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "end_portal_frame".equalsIgnoreCase(blockType);
    }
}
