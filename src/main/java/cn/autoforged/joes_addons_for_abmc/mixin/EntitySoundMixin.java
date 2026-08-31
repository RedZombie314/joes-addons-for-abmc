package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.sound.TransplantHeadSound;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 兜底音效改写：在 {@link Entity#playSound(SoundEvent, float, float)}（声明在 {@link Entity} 上）拦截，
 * 处理 getter 覆盖不到/不生效的场景（如村民“同意/摇头”等显式音效）。
 *
 * 仅依据<b>本实体自身的移植头 NBT</b> 判断，普通生物（无该 NBT）完全不受影响。
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void jafa_playSound(SoundEvent event, float volume, float pitch, CallbackInfo ci) {
        if (!(((Object) this) instanceof LivingEntity self)) return;
        SoundEvent s = TransplantHeadSound.routePlayback(self, event);
        if (s != null) {
            ci.cancel();
            self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
                s, self.getSoundSource(), volume, pitch);
        }
    }
}