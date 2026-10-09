package cn.autoforged.joes_addons_for_abmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.autoforged.joes_addons_for_abmc.client.DarkCloneTextureCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 把黑暗分身用到的一切<b>独立贴图</b>换成二值化版本，从而覆盖本体之外的部分：
 * 盔甲（{@code armorCutoutNoCull}）与各渲染层（村民职业服、生物肤色叠加层等，
 * 走 {@code entityCutoutNoCull} / {@code entityTranslucent} 一族）。
 *
 * <h2>为什么能在工厂层做，而不会污染普通生物</h2>
 * 这些工厂内部都是 {@code SOME_MEMOIZED_FUNCTION.apply(location)}，而 {@code Util.memoize}
 * 的缓存<b>以入参为键</b>。这里在 RETURN 处把返回的 RenderType 换成用「二值化贴图」再取一次的结果，
 * 于是缓存里多出一条以二值化贴图为键的独立条目，原来的键仍指向原版 RenderType——
 * 普通生物拿到的永远是原版那张。
 *
 * <p>递归只会多一层：替换后再进本方法时入参已是动态贴图，
 * {@link DarkCloneTextureCache#posterized} 找不到对应资源文件会返回 {@code null}，随即放行。
 *
 * <h2>手持物为什么不在其中</h2>
 * 物品模型走<b>方块/物品图集</b>（{@code minecraft:atlas/blocks}），它的 UV 是图集坐标，
 * 而图集没有可以单独读取并替换的 PNG 文件，{@link DarkCloneTextureCache#darken} 会直接返回
 * {@code null} 跳过。所以手持物目前仍是原色。
 *
 * <p>只覆盖到独立贴图；主动跳过 {@code eyes}（自发光叠加层），
 * 因为把发光层压成黑白会让末影人/蜘蛛的眼睛变黑、直接看不见。
 */
@Mixin(RenderType.class)
public abstract class DarkCloneTextureOverrideMixin {

    @Inject(
        method = "armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkArmorCutoutNoCull(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.armorCutoutNoCull(dark));
    }

    @Inject(
        method = "entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntitySolid(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entitySolid(dark));
    }

    @Inject(
        method = "entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityCutout(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityCutout(dark));
    }

    @Inject(
        method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityCutoutNoCull(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityCutoutNoCull(dark));
    }

    @Inject(
        method = "entityCutoutNoCullZOffset(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityCutoutNoCullZOffset(ResourceLocation location,
            CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityCutoutNoCullZOffset(dark));
    }

    @Inject(
        method = "itemEntityTranslucentCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkItemEntityTranslucentCull(ResourceLocation location,
            CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.itemEntityTranslucentCull(dark));
    }

    @Inject(
        method = "entityTranslucentCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityTranslucentCull(ResourceLocation location,
            CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityTranslucentCull(dark));
    }

    @Inject(
        method = "entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityTranslucent(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityTranslucent(dark));
    }

    @Inject(
        method = "entityTranslucentEmissive(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("RETURN"), cancellable = true)
    private static void jafa_darkEntityTranslucentEmissive(ResourceLocation location,
            CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation dark = DarkCloneTextureCache.posterized(location);
        if (dark != null) cir.setReturnValue(RenderType.entityTranslucentEmissive(dark));
    }
}
