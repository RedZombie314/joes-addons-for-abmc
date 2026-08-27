package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.GlisteringMelonKnifeItem;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class PrismarineArrow extends AbstractArrow {

    public PrismarineArrow(EntityType<? extends PrismarineArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    public PrismarineArrow(Level level, LivingEntity shooter, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(ModEntities.PRISMARINE_ARROW.get(), shooter, level, pickupItemStack, firedFromWeapon);
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    public PrismarineArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(ModEntities.PRISMARINE_ARROW.get(), x, y, z, level, pickupItemStack, firedFromWeapon);
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.PRISMARINE_ARROW.get());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target instanceof Player player && ModMain.isPlayerBlocking(player.getUUID())) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof GlisteringMelonKnifeItem) {
                    player.spawnAtLocation(held.copy());
                    player.setItemInHand(hand, ItemStack.EMPTY);
                    ModMain.clearPlayerBlocking(player.getUUID());
                    break;
                }
            }
        }
        super.onHitEntity(result);
    }
}
