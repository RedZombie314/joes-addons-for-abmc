package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.PrismarineArrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

public class PrismarineBowItem extends BowItem {

    private static final Predicate<ItemStack> PRISMARINE_ARROWS_AND_ARROW_TAG =
        ARROW_ONLY.or(stack -> stack.is(ModItems.PRISMARINE_ARROW.get()));

    public PrismarineBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return PRISMARINE_ARROWS_AND_ARROW_TAG;
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        if (ammo.is(ModItems.PRISMARINE_ARROW.get())) {
            PrismarineArrow arrow = new PrismarineArrow(level, shooter, ammo.copyWithCount(1), weapon);
            if (isCrit) {
                arrow.setCritArrow(true);
            }
            return arrow;
        }
        return super.createProjectile(level, shooter, weapon, ammo, isCrit);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        if (!projectileStack.is(ModItems.PRISMARINE_ARROW.get())) {
            arrow.setBaseDamage(arrow.getBaseDamage() * 1.5);
        }
        return arrow;
    }
}
