package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownGlisteringMelonKnife extends ThrowableItemProjectile {
    public static final float DAMAGE = 40.0F;

    private ItemStack thrownStack = ItemStack.EMPTY;
    private boolean creativeOnly = false;

    public ThrownGlisteringMelonKnife(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ThrownGlisteringMelonKnife(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.THROWN_GLISTERING_MELON_KNIFE.get(), shooter, level);
        this.thrownStack = stack.copy();
        this.setItem(stack.copy());
    }

    public ThrownGlisteringMelonKnife(Level level, double x, double y, double z) {
        super(ModEntities.THROWN_GLISTERING_MELON_KNIFE.get(), x, y, z, level);
    }

    public void setCreativeOnly(boolean creative) {
        this.creativeOnly = creative;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GLISTERING_MELON_KNIFE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        target.hurt(this.damageSources().thrown(this, owner != null ? owner : this), DAMAGE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            if (!creativeOnly) {
                ItemStack toDrop = this.getItem().copy();
                if (toDrop.isEmpty() && !thrownStack.isEmpty()) {
                    toDrop = thrownStack.copy();
                }
                if (!toDrop.isEmpty()) {
                    this.spawnAtLocation(toDrop, 0.1F);
                }
            }
            this.discard();
        }
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
