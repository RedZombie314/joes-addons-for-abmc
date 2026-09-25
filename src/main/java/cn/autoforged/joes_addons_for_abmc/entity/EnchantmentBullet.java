package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 魔咒子弹：无重力、沿直线飞行的弹射物（由附魔千纸鹤远程模式发射）。
 * 命中实体/方块时随机二选一效果：
 *   1) 点燃目标（或命中方块时放置火焰）+ 火焰弹呼啸音效；
 *   2) 制造一级火焰爆炸（可破坏方块/可制造火焰）+ volume 0.05 的爆炸音效。
 * 渲染：使用空渲染器（什么都不画），并留有可在此改模型的注释。
 */
public class EnchantmentBullet extends AbstractHurtingProjectile {

    /** 是否已结算过命中效果：防止单个子弹在多 tick 间被反复触发放火/爆炸（导致火不断重现、看似扑不灭）。 */
    private boolean effectApplied = false;

    public EnchantmentBullet(EntityType<? extends EnchantmentBullet> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public EnchantmentBullet(Level level, LivingEntity shooter, double x, double y, double z) {
        super(ModEntities.ENCHANTMENT_BULLET.get(), level);
        this.setPos(x, y, z);
        this.setOwner(shooter);
        this.setNoGravity(true);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.effectApplied) { this.discard(); return; }
        this.effectApplied = true;
        boolean fiery = this.random.nextFloat() < 0.5F;
        if (fiery) {
            // 点燃目标
            if (result.getEntity() instanceof LivingEntity living) {
                living.setRemainingFireTicks(100);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else {
            this.explode();
        }
        this.discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.effectApplied) { this.discard(); return; }
        this.effectApplied = true;
        if (result instanceof BlockHitResult bhr) {
            boolean fiery = this.random.nextFloat() < 0.5F;
            if (fiery) {
                // 命中方块时，在命中点一侧尝试放置火焰（仅当该处能存续火焰，避免产生“扑不掉”的怪火）
                BlockPos pos = bhr.getBlockPos().relative(bhr.getDirection());
                if (this.level().getBlockState(pos).isAir()
                        && (this.level().getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.NETHERRACK)
                            || this.level().getBlockState(pos.below()).canBeReplaced())) {
                    this.level().setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.0F);
            } else {
                this.explode();
            }
        }
        this.discard();
    }

    private void explode() {
        this.level().explode(this, this.getX(), this.getY(), this.getZ(),
            1.0F, true, Level.ExplosionInteraction.MOB);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.05F, 1.0F);
    }
}