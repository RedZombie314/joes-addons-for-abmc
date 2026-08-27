package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.entity.HerobrineHeadEntity;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆“权杖攻击”任务中，Him 权杖（herobrine_head）的专属行为。
 * <ul>
 *   <li>近战模式（默认，血量 ≥ 最大生命值的 1/3）：先对准目标传送到其身边，随后近战攻击；
 *       但以下情况不会传送，改为远程模式：
 *       <ul>
 *         <li>女仆处于坐下状态（目标在攻击范围之外时不传送）；</li>
 *         <li>目标会飞且位于高空，传送后落地摔伤将超过当前生命值的 1/2。</li>
 *       </ul>
 *   </li>
 *   <li>远程模式（血量 &lt; 最大生命值的 1/3，或上述不传送的情形）：在确保 Him 头颅不会波及自身、
 *       主人或其它宠物（含女仆）的前提下，对准目标发射 Him 头颅（每 4 游戏刻一枚）；
 *       若目标过近或不能安全发射，则改用近战/接近。</li>
 * </ul>
 * <p>
 * 该行为只在主手持有 Him 权杖且有攻击目标时启动；不持 Him 权杖时由
 * {@link TaskStaffAttack} 中的通用近战行为接管（两者通过条件互斥，不会双重攻击）。
 */
public class MaidHimStaffAttackTask extends Behavior<EntityMaid> {
    private static final double MIN_SHOOT_DISTANCE = 6.0;      // 发射头颅所需的最小目标距离（更近则改为近战）
    private static final double SAFE_EXPLOSION_DISTANCE = 5.0; // 友方实体到爆炸点所需的最小距离
    private static final double SAFE_PATH_CLEARANCE = 2.0;     // 友方实体到飞行路径所需的最小净空
    private static final long SHOOT_COOLDOWN = 4;              // 发射头颅的冷却（tick）：每 4 游戏刻一枚
    private static final long TELEPORT_COOLDOWN = 60;          // 传送的冷却（tick）：3 秒 = 60 刻

    private static final Map<UUID, Long> TELEPORT_COOLDOWNS = new HashMap<>();

    public MaidHimStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isHoldingHimStaff(maid) && getAttackTarget(maid) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isHoldingHimStaff(maid) && getAttackTarget(maid) != null;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return;

        BehaviorUtils.lookAtEntity(maid, target);
        boolean lowHealth = maid.getHealth() < maid.getMaxHealth() / 3.0F;

        if (maid.isWithinMeleeAttackRange(target)
            // 无法移动（坐下/骑乘/睡觉/被拴绳）时近战自卫范围扩大，覆盖“够不着近战也不射击”的空档。
            || (!maid.canBrainMoving() && maid.distanceTo(target) <= TaskStaffAttack.SITTING_MELEE_RANGE)) {
            // 无论何种模式：目标过近时一律左键近战攻击。
            doMeleeAttack(maid, target);
        } else if (lowHealth || shouldUseRanged(maid, target, level)) {
            // 远程模式（低血量 / 无法移动 / 传送会有致命摔伤）：在发射冷却结束后、
            // 安全前提下对准目标发射 Him 头颅（每 SHOOT_COOLDOWN 游戏刻一枚）。
            if (!maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                && canShootSafely(maid, target)) {
                shootHerobrineHead(maid, target, level);
            } else if (maid.canBrainMoving()) {
                // 仅可移动时逼近目标；坐下/骑乘/睡觉/被拴绳等无法移动时原地站桩，
                // 绝不移动或瞬移，等待冷却结束或安全条件满足（目标进入近战范围则走近战自卫）。
                maid.getNavigation().moveTo(target, 1.0);
            }
        } else {
            // 近战模式：传送至目标身边；无可用位置则走过去。
            if (teleportNear(maid, target, level)) {
                maid.getNavigation().stop();
            } else {
                maid.getNavigation().moveTo(target, 1.0);
            }
        }
    }

    /**
     * 是否应改用远程攻击（此时不进行传送）：
     * <ul>
     *   <li>女仆无法移动（坐下/骑乘/睡觉/被拴绳，{@code canBrainMoving()==false}）：
     *       目标在攻击范围之外时不再传送，改远程站桩攻击；</li>
     *   <li>目标会飞且其距离地面的高度足以让女仆传送到它身边后落地摔伤
     *       （摔伤 = 高度 - 3 格）超过当前生命值的 1/2。</li>
     * </ul>
     */
    private boolean shouldUseRanged(EntityMaid maid, LivingEntity target, ServerLevel level) {
        if (!maid.canBrainMoving()) return true;
        if (isFlyingTarget(target)) {
            double groundY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target.blockPosition()).getY();
            double fallDistance = target.getY() - groundY;
            // 原版摔伤：超过 3 格才开始掉血，每格 1 点。
            if (fallDistance > maid.getHealth() / 2.0 + 3.0) {
                return true;
            }
        }
        return false;
    }

    /** 目标是否会飞：具备飞行速度属性（蜂、鹦鹉、恼鬼、幻翼、潜影贝等）。 */
    private boolean isFlyingTarget(LivingEntity target) {
        return target.getAttribute(Attributes.FLYING_SPEED) != null
            && target.getAttributeValue(Attributes.FLYING_SPEED) > 0.0;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /** 主手是否持有 Him 权杖。 */
    private static boolean isHoldingHimStaff(EntityMaid maid) {
        return isHimStaff(maid.getMainHandItem());
    }

    /** 是否为 Him 权杖（herobrine_head 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isHimStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "herobrine_head".equals(blockType);
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

    /** 传送至目标身边（带冷却）。与玩家 Him 权杖右键传送一致：在目标周围半径 2 的球面上寻找无障碍落点。 */
    private boolean teleportNear(EntityMaid maid, LivingEntity target, ServerLevel level) {
        long now = level.getGameTime();
        Long last = TELEPORT_COOLDOWNS.get(maid.getUUID());
        if (last != null && now - last < TELEPORT_COOLDOWN) return false;
        TELEPORT_COOLDOWNS.put(maid.getUUID(), now);

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 chosen = null;
        for (int attempt = 0; attempt < 256; attempt++) {
            double u = maid.getRandom().nextDouble();
            double theta = maid.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * u - 1.0);
            Vec3 offset = new Vec3(
                Math.sin(phi) * Math.cos(theta),
                Math.cos(phi),
                Math.sin(phi) * Math.sin(theta)).scale(2.0);
            Vec3 candidate = center.add(offset);
            if (isSpotClear(level, candidate)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) return false;

        maid.teleportTo(chosen.x, chosen.y, chosen.z);
        level.playSound(null, chosen.x, chosen.y, chosen.z,
            SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private boolean isSpotClear(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        BlockPos pAbove = p.above();
        BlockState state = level.getBlockState(p);
        BlockState stateAbove = level.getBlockState(pAbove);
        return (state.isAir() || state.getBlock() == Blocks.WATER || state.getCollisionShape(level, p).isEmpty())
            && (stateAbove.isAir() || stateAbove.getBlock() == Blocks.WATER || stateAbove.getCollisionShape(level, pAbove).isEmpty());
    }

    /**
     * 能否安全发射 Him 头颅：目标可见且距离足够，且头颅飞行路径与命中爆炸
     * 不会波及自身、主人或其它宠物（含女仆）。任一不满足则不应发射。
     * <p>
     * 射手自身不参与“友方过近”检查（其安全已由 {@link #MIN_SHOOT_DISTANCE} 保证，
     * 头颅只会飞离女仆）；创造模式的玩家主人、或 Invulnerable 标签为 true 的宠物，
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

    /** 对准目标发射一颗 Him 头颅，并设置发射冷却。 */
    private void shootHerobrineHead(EntityMaid maid, LivingEntity target, ServerLevel level) {
        Vec3 from = maid.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 dir = to.subtract(from).normalize();
        HerobrineHeadEntity skull = new HerobrineHeadEntity(level, maid, dir);
        skull.setPos(from.x, from.y, from.z);
        level.addFreshEntity(skull);
        level.levelEvent(null, 1024, maid.blockPosition(), 0);
        maid.swing(InteractionHand.MAIN_HAND);
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, SHOOT_COOLDOWN);
    }
}
