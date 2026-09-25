package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 女仆“权杖攻击”任务中，岩浆块权杖（magma_block）的专属行为。
 * <ul>
 *   <li>目标在近战范围（坐下时按 {@link TaskStaffAttack#SITTING_MELEE_RANGE}）→ 近战攻击；</li>
 *   <li>目标较远：在确认恶魂火球的飞行路径与命中爆炸不会波及自身、主人或其它宠物（含女仆）的前提下，
 *       对准目标发射恶魂火球（每 {@link #SHOOT_COOLDOWN} 游戏刻一发），否则走近目标。</li>
 * </ul>
 * 与 Him 权杖（{@link MaidHimStaffAttackTask}）行为模式一致：远距离安全开火、近距离近战。
 * <p>
 * 该行为只在主手持有岩浆块权杖且有攻击目标时启动；不持岩浆块权杖时由
 * {@link TaskStaffAttack} 中的通用近战行为接管（两者通过条件互斥，不会双重攻击）。
 */
public class MaidMagmaStaffAttackTask extends Behavior<EntityMaid> {
    /** 发射恶魂火球所需的最小目标距离（更近则改为近战）。 */
    private static final double MIN_SHOOT_DISTANCE = 6.0;
    /** 友方实体到爆炸点所需的最小距离。 */
    private static final double SAFE_EXPLOSION_DISTANCE = 5.0;
    /** 友方实体到飞行路径所需的最小净空。 */
    private static final double SAFE_PATH_CLEARANCE = 2.0;
    /** 发射恶魂火球的冷却（tick）：每 10 游戏刻一发。 */
    private static final long SHOOT_COOLDOWN = 10;

    public MaidMagmaStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isMagmaStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isMagmaStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return;

        BehaviorUtils.lookAtEntity(maid, target);

        if (maid.isWithinMeleeAttackRange(target)
            // 无法移动（坐下/骑乘/睡觉/被拴绳）时近战自卫范围扩大，覆盖“够不着近战也不射击”的空档。
            || (!maid.canBrainMoving() && maid.distanceTo(target) <= TaskStaffAttack.SITTING_MELEE_RANGE)) {
            doMeleeAttack(maid, target);
        } else if (canShootSafely(maid, target)) {
            // 远程：安全前提下在冷却结束后发射恶魂火球；冷却期间可移动时逼近目标（缩短距离）。
            if (!maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
                shootGhastFireball(maid, target, level);
            } else if (maid.canBrainMoving()) {
                maid.getNavigation().moveTo(target, 0.6);
            }
        } else if (maid.canBrainMoving()) {
            // 不安全（目标不可见/过近/友方在火线或爆炸范围内）：不射击，逼近目标至近战或安全距离
            maid.getNavigation().moveTo(target, 0.6);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /** 是否为岩浆块权杖（magma_block 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isMagmaStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "magma_block".equals(blockType);
    }

    /** 与车万女仆 MaidMeleeAttack 一致：近战攻击并按攻速设置冷却。 */
    private void doMeleeAttack(EntityMaid maid, LivingEntity target) {
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return;
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        maid.swing(InteractionHand.MAIN_HAND);
        maid.doHurtTarget(target);
        double attackSpeed = maid.getAttributeValue(Attributes.ATTACK_SPEED);
        long cooldown = attackSpeed > 0 ? (long) (20 / attackSpeed) : 20;
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldown);
    }

    /**
     * 能否安全发射恶魂火球：目标可见且距离足够，且火球飞行路径与命中爆炸
     * 不会波及自身、主人或其它宠物（含女仆）。任一不满足则不应发射。
     * <p>
     * 射手自身不参与“友方过近”检查（其安全已由 {@link #MIN_SHOOT_DISTANCE} 保证，
     * 火球只会飞离女仆）；创造模式的玩家主人、或 Invulnerable 标签为 true 的宠物，
     * 即使距离过近也不阻止发射。
     */
    private boolean canShootSafely(EntityMaid maid, LivingEntity target) {
        if (!(maid.level() instanceof ServerLevel level)) return false;
        if (!BehaviorUtils.canSee(maid, target)) return false;

        Vec3 from = maid.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        double dist = from.distanceTo(to);
        if (dist < MIN_SHOOT_DISTANCE) return false;

        double inflate = Math.max(dist, SAFE_EXPLOSION_DISTANCE) + SAFE_PATH_CLEARANCE;
        List<Entity> candidates = level.getEntities(maid, maid.getBoundingBox().inflate(inflate));
        for (Entity e : candidates) {
            // 目标与射手自身不参与“友方过近”检查。
            if (e == target || e == maid) continue;
            if (!isProtectedAlly(maid, e)) continue;
            // 豁免：创造模式的玩家主人 / Invulnerable 标签为 true 的宠物，距离过近也不阻止发射。
            if (isExemptFromFriendlyCheck(e)) continue;
            // 爆炸点（目标中心）不得靠近友方
            if (e.distanceToSqr(to) < SAFE_EXPLOSION_DISTANCE * SAFE_EXPLOSION_DISTANCE) {
                return false;
            }
            // 飞行路径（女仆眼位 -> 目标中心）不得贴近友方
            if (distanceToSegment(e.position(), from, to) < SAFE_PATH_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    /** 豁免规则：创造模式玩家，或 Invulnerable 标签为 true 的宠物，不因距离目标过近而阻止发射。 */
    private boolean isExemptFromFriendlyCheck(Entity e) {
        if (e instanceof Player player) {
            return player.getAbilities().instabuild;
        }
        return e.isInvulnerable();
    }

    /** 是否受保护：主人、其它女仆、已驯服宠物（含主人的宠物）。射手自身不在此列（见 canShootSafely）。 */
    private boolean isProtectedAlly(EntityMaid maid, Entity e) {
        LivingEntity owner = maid.getOwner();
        if (owner != null && e == owner) return true;
        if (e instanceof EntityMaid) return true;
        return e instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;
    }

    /** 点 p 到线段 ab 的垂直距离。 */
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        Vec3 ap = p.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-4) return ap.length();
        double t = Math.max(0.0, Math.min(1.0, ap.dot(ab) / len2));
        return ap.subtract(ab.scale(t)).length();
    }

    /** 对准目标发射一颗恶魂火球（与玩家版岩浆块权杖一致的 LargeFireball），并设置发射冷却。 */
    private void shootGhastFireball(EntityMaid maid, LivingEntity target, ServerLevel level) {
        Vec3 from = maid.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 dir = to.subtract(from).normalize();
        LargeFireball fireball = new LargeFireball(level, maid, dir, 1);
        fireball.setPos(from.x, from.y - 0.2, from.z);
        fireball.setDeltaMovement(dir.scale(3.0 / 20.0));
        level.addFreshEntity(fireball);
        level.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
            SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.5F, 1.0F);
        maid.swing(InteractionHand.MAIN_HAND);
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, SHOOT_COOLDOWN);
    }
}
