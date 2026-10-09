package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 己方空壳丢出去的缓降药水（"目标拿重锤且没有缓降"时的那一招特攻）。
 *
 * <h2>为什么不能直接用原版 {@link ThrownPotion}</h2>
 * 原版投射物有一条"别打到自己人"的起手保护（{@code Projectile#checkLeftOwner()}）：
 * 只要这枚投射物的（沿运动方向展开 + 膨胀 1 格的）碰撞箱还压着<b>和投掷者同一载具</b>的
 * 任何实体，{@code leftOwner} 就是 false，而 {@code canHitEntity} 在 false 时<b>谁都打不到</b> ——
 * 连目标都打不到。
 * <p>
 * 问题在于"同一载具"这个条件：投掷者是那具空壳，而<b>空壳自己 </b>当然和空壳同一载具，
 * 于是药水出手后的头一两格（膨胀 1 格 + 瓶身 0.25 格 + 每刻 1 格位移）内处于"还没离开投掷者"的状态，
 * 这时贴脸站在空壳面前的玩家会被整瓶药水<b>穿过去</b>，瓶子不碎、也不会给玩家挂上缓降。
 *
 * <h2>怎么改</h2>
 * 覆写 {@link #canHitEntity(Entity)}：<b>只</b>挡投掷者本体，其余一律按原版有效性判定
 * （{@code canBeHitByProjectile()}）。这样药水一离开空壳就能命中任何人 ——
 * 既不会在出手瞬间糊自己一脸，也不会再穿过贴脸的玩家。
 * <p>
 * 顺带说明：实体类型沿用原版的 {@link EntityType#POTION}，所以客户端照样用原版的
 * 药水渲染器画它，不需要额外注册实体类型/渲染器。
 */
public class OrbSlowFallingPotion extends ThrownPotion {

    public OrbSlowFallingPotion(Level level, LivingEntity owner) {
        super(EntityType.POTION, level);
        this.setOwner(owner);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!target.canBeHitByProjectile()) {
            return false;
        }
        // 原版这里还有一道 `this.leftOwner || !owner.isPassengerOfSameVehicle(target)`：
        // 那道判断会把"刚出手、还贴着投掷者"的这段路程变成无敌区，见类注释。
        // 我们只保留"别打投掷者自己"这一条最朴素的意义。
        return target != this.getOwner();
    }

    /**
     * 成就「轮到我不吃这套了」：<b>手里拿着重锤</b>时被这瓶药水砸中即授予。
     * <p>
     * 范围照抄原版 {@code ThrownPotion#applySplash} 的口径：碰撞箱外扩 {@code 4 × 2 × 4}、
     * 且命中距离平方 {@code < 16}（直接被砸中的那个实体无视距离，原版对它用 {@code d1 = 1.0}）。
     * "拿着重锤"用与投掷条件同一套判据（主手或副手持重锤，见
     * {@code OrbPossessedAttackEvents#holdsMace}）—— 药水本来就是照着这条条件丢出来的，
     * 但玩家可能在空中把重锤换掉，所以这里按<b>被砸中的那一刻</b>再判一次。
     * <p>
     * 放在 {@code super.onHit} <b>之前</b>：原版那边结算完就会 {@code discard()} 掉药水。
     */
    @Override
    protected void onHit(HitResult result) {
        if (!this.level().isClientSide && this.getOwner() instanceof PlayerShellEntity) {
            Entity direct = result.getType() == HitResult.Type.ENTITY
                ? ((EntityHitResult) result).getEntity() : null;
            for (LivingEntity hit : this.level().getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(4.0, 2.0, 4.0))) {
                if (hit != direct && this.distanceToSqr(hit) >= 16.0) continue;
                if (hit instanceof net.minecraft.server.level.ServerPlayer sp
                    && (sp.getMainHandItem().is(Items.MACE) || sp.getOffhandItem().is(Items.MACE))) {
                    cn.autoforged.joes_addons_for_abmc.ModMain.awardAdvancement(
                        sp, cn.autoforged.joes_addons_for_abmc.ModMain.NOT_TODAY_ADV);
                }
            }
        }
        super.onHit(result);
    }
}
