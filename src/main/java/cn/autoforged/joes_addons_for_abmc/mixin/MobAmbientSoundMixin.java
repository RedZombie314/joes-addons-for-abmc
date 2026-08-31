package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.sound.TransplantHeadSound;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 使“移植头”生物的环境音改用“头来源生物”的环境音。
 *
 * {@code getAmbientSound} 声明在 {@link Mob} 上（而非 {@link LivingEntity}），
 * 因此独立成一个 target 为 {@link Mob} 的 mixin。
 */
@Mixin(Mob.class)
public abstract class MobAmbientSoundMixin {

    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
    private void jafa_getAmbientSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent s = TransplantHeadSound.getAmbient((LivingEntity) (Object) this);
        if (s != null) cir.setReturnValue(s);
    }
}