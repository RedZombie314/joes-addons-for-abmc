package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 掷出的奶桶：下蹲右键奶桶时掷出。落地/砸中后如喷溅药水一样范围生效——
 * 清除范围内所有实体的状态效果、喷洒白色粒子，并生成一个可立即捡起的空桶。
 */
public class ThrownMilkBucket extends ThrowableItemProjectile {

    /** 生效半径（格），约同喷溅药水有效范围。 */
    private static final double SPLASH_RADIUS = 4.0;

    public ThrownMilkBucket(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ThrownMilkBucket(Level level, LivingEntity shooter) {
        super(ModEntities.THROWN_MILK_BUCKET.get(), shooter, level);
        this.setItem(new ItemStack(Items.MILK_BUCKET));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MILK_BUCKET;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03; // 类似喷溅药水
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide()) this.applySplash();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) this.applySplash();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // 兜底销毁（防双触发重复结算）
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    /** 范围清除状态效果 + 白色粒子 + 掉落可立即拾取的空桶。 */
    private void applySplash() {
        Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        double x = this.getX(), y = this.getY(), z = this.getZ();
        var box = this.getBoundingBox().inflate(SPLASH_RADIUS);
        // 清除范围内所有实体的所有状态效果
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            e.removeAllEffects();
        }
        // 白色粒子爆开（类似喷溅药水碎裂的视觉）
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
            x, y + 0.2, z, 40, 0.5, 0.3, 0.5, 0.08);
        // 生成一个可立即捡起的空桶
        ItemEntity bucket = new ItemEntity(serverLevel, x, y + 0.2, z, new ItemStack(Items.BUCKET));
        bucket.setDeltaMovement(0, 0.08, 0);
        bucket.setPickUpDelay(0);
        serverLevel.addFreshEntity(bucket);

        this.discard();
    }
}