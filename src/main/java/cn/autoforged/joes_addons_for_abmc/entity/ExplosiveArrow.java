package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.item.ExplosiveArrowItem;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * <b>爆炸之箭</b>的箭矢实体。
 *
 * <ul>
 *   <li>命中<b>实体</b>：先照常结算箭矢伤害，然后<b>立刻爆炸</b>；</li>
 *   <li>命中<b>方块</b>：照常插在方块上，同时开始 <b>100 刻（5 秒）</b>的倒计时，倒计时结束时爆炸；</li>
 *   <li>爆炸一律用 {@code Level.ExplosionInteraction.NONE}：<b>威力 4、不破坏地形</b>；
 *       <b>不伤及射出者</b>由 {@code ModMain.onExplosionDetonate} 把射手从受影响实体列表里剔除实现
 *       （NeoForge 的 {@code ExplosionEvent.Detonate} 允许修改该列表，剔除后既不吃伤害也不吃击退）；</li>
 *   <li>不可被拾回（{@code Pickup.DISALLOWED}）—— 引信点着的箭不该能捡起来。</li>
 * </ul>
 */
public class ExplosiveArrow extends AbstractArrow {
    /** 命中方块后的引爆倒计时（刻）；< 0 表示还没插到方块上。 */
    private int fuse = -1;

    public ExplosiveArrow(EntityType<? extends ExplosiveArrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public ExplosiveArrow(Level level, LivingEntity shooter, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(ModEntities.EXPLOSIVE_ARROW.get(), shooter, level, pickupItemStack, firedFromWeapon);
        this.pickup = Pickup.DISALLOWED;
    }

    /** 发射器发射时用的构造（位置 + 弹药，没有射击者）。 */
    public ExplosiveArrow(Level level, double x, double y, double z, ItemStack pickupItemStack,
                          ItemStack firedFromWeapon) {
        super(ModEntities.EXPLOSIVE_ARROW.get(), x, y, z, level, pickupItemStack, firedFromWeapon);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.EXPLOSIVE_ARROW.get());
    }

    /** 命中实体：照常结算伤害后立刻引爆。 */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        detonate();
    }

    /** 命中方块：插住不动，并开始倒计时。 */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide && this.fuse < 0) {
            this.fuse = ExplosiveArrowItem.FUSE_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.fuse >= 0 && --this.fuse <= 0) {
            detonate();
        }
    }

    /** 引爆：威力 4、不破坏地形（不伤及射手由爆炸事件那边剔除）。 */
    private void detonate() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        this.fuse = -1;
        serverLevel.explode(this, this.getX(), this.getY(0.5D), this.getZ(),
            ExplosiveArrowItem.EXPLOSION_POWER, Level.ExplosionInteraction.NONE);
        this.discard();
    }
}
