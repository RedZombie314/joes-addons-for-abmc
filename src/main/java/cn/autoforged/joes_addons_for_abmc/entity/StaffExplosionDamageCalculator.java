package cn.autoforged.joes_addons_for_abmc.entity;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;

/**
 * TNT 权杖专用爆炸伤害计算器：使爆炸不伤害投掷者本人。
 */
public class StaffExplosionDamageCalculator extends ExplosionDamageCalculator {

    @Nullable
    private final Entity protectedEntity;

    public StaffExplosionDamageCalculator(@Nullable Entity protectedEntity) {
        this.protectedEntity = protectedEntity;
    }

    @Override
    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return entity != protectedEntity;
    }
}