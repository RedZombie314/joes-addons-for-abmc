package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 烈焰手杖发射的恶魂火球。
 * <p>行为与恶魂火球（{@code LargeFireball}）一致：命中生物 6 点伤害、命中后爆炸并消失；
 * 区别只有两点：
 * <ol>
 *   <li><b>受重力影响</b>：原版火球在 {@code AbstractHurtingProjectile#tick} 里只做
 *       「惯性 + 沿速度方向的加速度」，本身<b>不施加重力</b>；这里每刻补一次
 *       {@code applyGravity()}（重力值 {@link #GRAVITY}，与原版箭矢一致）。</li>
 *   <li><b>爆炸威力 2</b>（恶魂火球是 1）。</li>
 * </ol>
 * 之所以要单独做一个实体（而不是直接用 {@code EntityType.FIREBALL}）：重力和爆炸威力都必须写进
 * {@code tick()} / {@code onHit()}，而 {@code LargeFireball} 的 {@code explosionPower} 是私有的。
 * 这里直接继承 {@link Fireball}——它没有覆写 {@code onHit}，所以 {@code super.onHit} 走的就是
 * {@code Projectile#onHit} 的原版派发（命中生物 / 命中方块），爆炸则完全由本类掌控。
 */
public class BlazeStaffFireball extends Fireball {
    /** 爆炸威力（恶魂火球 = 1）。 */
    public static final int EXPLOSION_POWER = 2;
    /** 每刻重力加速度（与原版箭矢的 0.05 一致）。 */
    public static final double GRAVITY = 0.05D;

    public BlazeStaffFireball(EntityType<? extends BlazeStaffFireball> entityType, Level level) {
        super(entityType, level);
    }

    public BlazeStaffFireball(EntityType<? extends BlazeStaffFireball> entityType, Level level,
                              LivingEntity owner, Vec3 movement) {
        super(entityType, owner, movement, level);
    }

    @Override
    public void tick() {
        super.tick();
        // super 已经完成本刻位移；这里补的重力作用于下一刻的位移（客户端/服务端一致，不会有位置回拉）
        if (!this.isNoGravity()) {
            this.applyGravity();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    /** 与恶魂火球相同的 6 点伤害。 */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity target = result.getEntity();
            Entity owner = this.getOwner();
            DamageSource source = this.damageSources().fireball(this, owner);
            target.hurt(source, 6.0F);
            EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
        }
    }

    /** 先走原版命中派发（{@code Projectile#onHit}），再用威力 2 爆炸并消失。 */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            boolean grief = EventHooks.canEntityGrief(this.level(), this.getOwner());
            this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                (float) EXPLOSION_POWER, grief, Level.ExplosionInteraction.MOB);
            this.discard();
        }
    }
}
