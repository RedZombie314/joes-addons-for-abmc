package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.WitchTransmutedPoison;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>女巫Boss</b>的支援行为：周围有「被变形药水变成其他生物（非亡灵）且生命值 &gt; 1 点」的目标时，
 * 投掷剧毒 II，冷却 2 秒。
 *
 * <p>逻辑都在 {@link WitchTransmutedPoison} 里（含"只对女巫Boss生效"的标签判断），这里只负责每刻调用一次。
 * 挂在 {@code aiStep} 的入口，和 {@code WitchMixin}（同为女巫Boss 专用）用的是同一个注入点，两者互不影响。
 */
@Mixin(Witch.class)
public abstract class WitchTransmutedPoisonMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void jafa_witchPoisonTransmutedPrey(CallbackInfo ci) {
        WitchTransmutedPoison.tick((Witch) (Object) this);
    }
}
