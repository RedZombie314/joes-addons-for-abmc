package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让变形药水对所有生物（包括原版宣称免疫效果的 Boss/铁傀儡/村民等）都生效。
 *  原版 LivingEntity.addEffect() 会先调 canBeAffected(effect) 过滤免疫效果；
 *  本 Mixin 在 canBeAffected 返回 false 时额外检查：如果是 TRANSMUTATION 效果，强制返回 true。
 *  覆盖两条路径：喷溅药水（ThrownPotion.doSplash → addEffect）和滞留云（AreaEffectCloud 每 tick → addEffect）。
 *
 *  注意 1.21.1 中 canBeAffected 的参数已从 MobEffect 改为 MobEffectInstance。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTransmutationMixin {

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void jafa_forceTransmutationAlwaysApplied(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (instance.getEffect() == ModMobEffects.TRANSMUTATION.get()) {
            cir.setReturnValue(true);
        }
    }
}
