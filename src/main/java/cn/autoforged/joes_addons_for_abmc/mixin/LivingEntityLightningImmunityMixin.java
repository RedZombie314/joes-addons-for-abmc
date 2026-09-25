package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持有避雷针权杖的玩家免疫闪电伤害。
 * <p>
 * 在 {@link LivingEntity#hurt} 入口直接拦截（最底层钩子，绕过可能被其它 mod
 * 修改的伤害事件链），同时清除闪电点燃的火焰；配合 {@link EntityThunderHitImmunityMixin}
 * 双保险覆盖不同伤害路径。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLightningImmunityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void jafa_lightningRodImmunity(DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player p)) return;
        if (!(self.level() instanceof ServerLevel)) return;
        if (!ModMain.isHoldingLightningRodStaff(p)) return;
        if (source.is(DamageTypes.LIGHTNING_BOLT) || source.getDirectEntity() instanceof LightningBolt) {
            p.setRemainingFireTicks(0);
            cir.setReturnValue(false);
        }
    }
}
