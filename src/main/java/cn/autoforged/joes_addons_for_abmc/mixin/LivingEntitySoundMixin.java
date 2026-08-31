package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.sound.TransplantHeadSound;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 使“移植头”生物发出头来源生物的声音而非身体本身的声音。
 *
 * 在 {@link LivingEntity#getHurtSound}、{@link LivingEntity#getDeathSound} 入口处拦截：
 * 若该实体带移植头 NBT，则改为返回头来源生物的音效；否则沿用原逻辑（对普通生物完全无影响）。
 *
 * 注：环境音 {@code getAmbientSound} 声明在 {@link Mob} 上，由 {@link MobAmbientSoundMixin} 处理；
 * 显式 {@code playSound} 声明在 {@link Entity} 上，由 {@link EntitySoundMixin} 处理。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySoundMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void jafa_getHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent s = TransplantHeadSound.getHurt((LivingEntity) (Object) this, source);
        if (s != null) cir.setReturnValue(s);
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void jafa_getDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent s = TransplantHeadSound.getDeath((LivingEntity) (Object) this);
        if (s != null) cir.setReturnValue(s);
    }
}