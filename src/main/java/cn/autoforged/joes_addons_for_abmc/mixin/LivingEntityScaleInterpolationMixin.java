package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.client.SelectorScaleInterpolation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 {@code LivingEntityRenderer#render} 里读体型的那一次 {@code entity.getScale()}（源码 :97）
 * 换成「按帧插值后的缩放」，让被幸运选择器抓着 / 送走的生物体型变化是连续的。
 *
 * <p>逻辑全部在 {@link SelectorScaleInterpolation} 里（那边有完整的来龙去脉，包括
 * 「为什么只能在收到新值的那一格插值，否则会抖」）。
 *
 * <p>{@code require = 0}：万一将来 MC 改了这段渲染代码、注入点没了，也只是「回到逐刻跳变」，
 * 不会连同游戏一起崩（和 {@link LuckySunMixin} 一个路子）。
 * 注入点本身是对着本版<b>编译产物</b>核过的：{@code LivingEntityRenderer#render} 的字节码里
 * {@code LivingEntity.getScale:()F} 只有一处（偏移 355，另一处在 {@code getShadowRadius} 里，不是目标方法），
 * 所以不需要 ordinal。
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityScaleInterpolationMixin {

    /** 把 {@code entity.getScale()} 换成「按帧插值后的缩放」。 */
    @Redirect(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getScale()F"))
    private float joes_addons_for_abmc$interpolatedScale(LivingEntity entity) {
        return SelectorScaleInterpolation.scaleFor(entity);
    }
}
