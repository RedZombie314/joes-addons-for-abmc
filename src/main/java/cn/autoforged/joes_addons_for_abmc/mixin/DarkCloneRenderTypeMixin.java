package cn.autoforged.joes_addons_for_abmc.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.autoforged.joes_addons_for_abmc.client.DarkCloneTextureCache;
import cn.autoforged.joes_addons_for_abmc.entity.DarkCloneAttachments;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 黑暗分身的黑白外观：<b>直接把本体绘制用的贴图换掉</b>，而不是在本体之上再叠一层重画。
 *
 * <p>为什么不叠一层：叠加意味着同一个模型被画两遍、深度值几乎相同，深度测试在 LEQUAL 下
 * 靠浮点精度决定谁胜出——必然出现 z-fighting（这正是之前的 bug）。
 * 用缩放微调也治不好：模型缩放是绕模型原点做的，正面朝向镜头的面未必被推向镜头，
 * 而缩放系数放大到能压住精度误差时，头/手等部件又会相对脚底位移，出现「头浮起来」。
 * 换成替换贴图后只有一次绘制，z-fighting 从根上不存在，顺带省掉一遍顶点提交。
 *
 * <p>注入点选 {@code getRenderType} 的 HEAD 并完整重写 bodyVisible 分支：
 * 只替换贴图、其余逻辑（{@code this.model.renderType(...)} 允许模型自定义渲染类型）与
 * 原版保持一字不差，因此带自发光眼睛等特殊 renderType 的模型也不会被破坏。
 *
 * <p>覆盖边界：只覆盖继承 {@code LivingEntityRenderer} 的渲染器。
 * GeckoLib 的 {@code GeoEntityRenderer} 直接继承 {@code EntityRenderer}，拿不到这里。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class DarkCloneRenderTypeMixin {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void jafa_darkCloneRenderType(LivingEntity entity, boolean bodyVisible, boolean translucent,
            boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        // translucent 分支对应「本体不可见但对该玩家可见」（半透明显现），不属于正常可见本体；
        // glowing 轮廓分支同理。两者都保持原版行为。
        if (translucent || !bodyVisible) return;

        ResourceLocation dark = darkCloneTexture(entity);
        if (dark == null) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, ?> renderer = (LivingEntityRenderer<LivingEntity, ?>) (Object) this;
        cir.setReturnValue(renderer.getModel().renderType(dark));
    }

    /**
     * 取该生物应当使用的二值化贴图；不是黑暗分身或贴图不可读时返回 {@code null} 表示保持原样。
     * 先用 {@code isDarkClone} 短路，因此普通生物连贴图查询都不会发生。
     */
    @Nullable
    private ResourceLocation darkCloneTexture(LivingEntity entity) {
        if (!DarkCloneAttachments.isDarkClone(entity)) return null;
        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, ?> renderer = (LivingEntityRenderer<LivingEntity, ?>) (Object) this;
        return DarkCloneTextureCache.darken(renderer.getTextureLocation(entity));
    }
}
