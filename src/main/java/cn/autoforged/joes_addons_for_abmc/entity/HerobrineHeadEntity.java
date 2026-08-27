package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.damage.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Herobrine head launched by the Him staff.
 *
 * It behaves like the blue wither skull, but:
 * - flies faster (acceleration power 0.6 instead of 0.1, i.e. 6x the vanilla skull),
 * - every damage it deals (direct contact or explosion) is TRUE damage: it ignores
 *   armor, resistance and enchantments, directly lowering health,
 * - it does NOT apply the wither effect,
 * - its explosion has power 1 and damages both entities and terrain,
 * - any block with an explosion resistance above 0.8 is treated as 0.8 (so even
 *   bedrock can be destroyed), but command blocks, barriers, structure blocks and
 *   other unbreakable blocks stay protected.
 */
public class HerobrineHeadEntity extends WitherSkull {

    // 固定加速度 0.6（原版蓝色凋灵头颅为 0.1，这里是 6 倍；也是本项目上一版 0.3 的 2 倍）。
    // 必须在该实体两个构造里统一设置：客户端通过 (EntityType, Level) 构造从 AddEntity 包重建，
    // 若不设置，客户端本地预测速度（保持原版 0.1）会比服务端推进速度慢，
    // 双方位移偏差随飞行时间累积，最终被客户端硬拉到服务端权威位置，表现为“飞行中突然瞬移十几二十格”。
    private static final float ACCELERATION = 0.6F;

    public HerobrineHeadEntity(EntityType<? extends HerobrineHeadEntity> entityType, Level level) {
        super(entityType, level);
        // 客户端通过该构造从 AddEntity 包重建实体：必须同样设置加速度，
        // 让客户端本地预测与服务端推进速度一致，避免瞬移。
        this.accelerationPower = ACCELERATION;
    }

    public HerobrineHeadEntity(Level level, LivingEntity owner, Vec3 movement) {
        super(ModEntities.HEROBRINE_HEAD.get(), level);
        this.moveTo(owner.getX(), owner.getY(), owner.getZ(), this.getYRot(), this.getXRot());
        this.reapplyPosition();
        // 原版蓝色凋灵头颅加速度基底为 0.1，这里翻两倍到 0.6（速度约为原来的 2 倍）。
        this.accelerationPower = ACCELERATION;
        this.setDeltaMovement(movement.normalize().scale(this.accelerationPower));
        this.hasImpulse = true;
        this.setOwner(owner);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    protected float getInertia() {
        // 速度每刻按 (旧速度 + 0.6) * inertia 更新。inertia 必须显著小于 1，
        // 否则速度会无界增长（例如 0.9 会让终速度高达 5.4 格/刻，飞行后期看起来像瞬移）。
        // 0.75 使终速度收敛到约 1.8 格/刻（为原来 0.9 的两倍），稳定且无需担心瞬移。
        return 0.75F;
    }

    @Override
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, float explosionPower
    ) {
        // Command blocks, barriers, structure blocks (and other unbreakable blocks)
        // cannot be destroyed by the blast; their full resistance is kept.
        if (isProtectedBlock(blockState)) {
            return explosionPower;
        }
        // Everything else (including bedrock) has its explosion resistance treated as
        // at most 0.8, so even normally indestructible blocks can be broken.
        return Math.min(0.8F, explosionPower);
    }

    private static boolean isProtectedBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COMMAND_BLOCK
            || block == Blocks.CHAIN_COMMAND_BLOCK
            || block == Blocks.REPEATING_COMMAND_BLOCK
            || block == Blocks.BARRIER
            || block == Blocks.STRUCTURE_BLOCK
            || block == Blocks.STRUCTURE_VOID
            || block == Blocks.END_PORTAL
            || block == Blocks.END_PORTAL_FRAME
            || block == Blocks.END_GATEWAY
            || block == Blocks.JIGSAW;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Deliberately does NOT call WitherSkull.onHitEntity (which would apply the
        // wither effect). Only the true direct damage is kept, without any wither.
        if (this.level() instanceof ServerLevel serverlevel) {
            Entity entity = result.getEntity();
            DamageSource damagesource = trueDamageSource(serverlevel, this.getOwner());
            if (entity.hurt(damagesource, 8.0F)) {
                this.explode(serverlevel);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel serverlevel) {
            this.explode(serverlevel);
        }
    }

    // True-damage source: bypasses armor, resistance and enchantments.
    private DamageSource trueDamageSource(ServerLevel level, Entity owner) {
        if (owner instanceof LivingEntity living) {
            return level.damageSources().source(ModDamageTypes.HEROBRINE_HEAD.getKey(), this, living);
        }
        return level.damageSources().source(ModDamageTypes.HEROBRINE_HEAD.getKey(), this);
    }

    // Power-1 explosion that damages both entities and terrain. Uses the same
    // true-damage source, so the blast damage also ignores armor/resistance.
    private void explode(ServerLevel serverlevel) {
        DamageSource source = trueDamageSource(serverlevel, this.getOwner());
        serverlevel.explode(this, source, null,
            this.getX(), this.getY(), this.getZ(), 1.0F, false, Level.ExplosionInteraction.MOB);
    }
}