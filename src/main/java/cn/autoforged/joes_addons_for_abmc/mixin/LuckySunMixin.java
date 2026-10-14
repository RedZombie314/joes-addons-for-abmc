package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幸运维度天空的几处改造（都落在 {@code LevelRenderer} 里）：<b>太阳贴图</b>、
 * <b>太阳尺寸</b>、<b>不画月亮</b>、<b>不画云</b>。
 *
 * <p><b>一共四件事</b>：
 * <ol>
 *   <li><b>太阳贴图</b>换成本模组的幸运方块贴图（下面有完整来龙去脉）；</li>
 *   <li><b>太阳尺寸</b>缩到 1/3（{@link #joes_addons_for_abmc$luckySunSize}，用户指定）；</li>
 *   <li><b>月亮不画</b>（{@link #joes_addons_for_abmc$moonPending}，用户指定）；</li>
 *   <li><b>云不画</b>（{@link #joes_addons_for_abmc$noClouds}，用户指定）。</li>
 * </ol>
 *
 * <p><b>只在幸运维度</b>把太阳贴图换成幸运方块贴图
 * （{@code joes_addons_for_abmc:textures/environment/lucky_sun.png}），
 * 并且让这张贴图**按原色显示**。
 *
 * <p>原版太阳贴图是全局资源（{@code minecraft:textures/environment/sun.png}），没有"按维度换"的开关，
 * 直接覆盖那张贴图会影响所有维度。所以这里在 {@code LevelRenderer.renderSky} 里重定向
 * {@code RenderSystem.setShaderTexture(...)}：只有当传入的贴图正是太阳、且当前维度是幸运维度时，
 * 才替换成幸运方块贴图；其它情况原样放行。
 *
 * <p><b>为什么还要改混合模式</b>：原版画太阳之前调用
 * {@code RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)}，即<b>加色混合</b>
 * （{@code dst = src + dst}）。太阳的颜色是"加"到天空上的：白天天空本身已经很亮，
 * 贴图里任何偏亮的像素加上去都会 &gt;1 被截断成纯白 —— 这是数学上的必然，
 * 加色混合下**不可能**原样显示一张贴图。所以这里在太阳真正上传顶点的那一次绘制外面，
 * 临时把混合换成普通的 alpha 混合（{@code dst = src*a + dst*(1-a)}），画完立刻还原加色混合。
 *
 * <p>注入点说明（对应 1.21.1 的字节码）：{@code renderSky} 里 {@code BufferUploader.drawWithShader}
 * 一共只出现在三处 —— 日出日落扇、太阳、月亮，顺序是太阳 → 月亮，中间没有任何其它绘制，
 * 所以"换贴图时挂一个标记、下一次绘制时消费"的做法是确定且无泄漏的；
 * 月亮换贴图时如果太阳的标记还在（太阳那一帧因条件没画），也会顺手把混合状态还原回去。
 *
 * <p>{@code require = 0}：万一将来 MC 改了这段渲染代码导致注入点消失，也只是"这几处改造失效"
 * （太阳恢复原尺寸/月亮和云回来了），不会崩游戏。
 */
@Mixin(LevelRenderer.class)
public class LuckySunMixin {
    /** 幸运方块贴图（作为幸运维度的太阳）。 */
    private static final ResourceLocation LUCKY_SUN_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/environment/lucky_sun.png");

    /** 原版太阳贴图的路径（用于辨认传入的是不是太阳）。 */
    private static final String VANILLA_SUN_PATH = "textures/environment/sun.png";

    /** 原版月亮贴图的路径（用于兜底还原混合状态）。 */
    private static final String VANILLA_MOON_PATH = "textures/environment/moon_phases.png";

    /** 幸运维度 id。 */
    private static final ResourceLocation LUCKY_DIMENSION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dimension");

    /** 太阳贴图已换成幸运方块、等它那次绘制来消费。 */
    private static boolean joes_addons_for_abmc$sunPending = false;

    /** 月亮贴图已设、等它那次绘制来吞掉（幸运维度不画月亮）。 */
    private static boolean joes_addons_for_abmc$moonPending = false;

    /** 换贴图：幸运维度 + 太阳 -> 幸运方块贴图；幸运维度 + 月亮 -> 标记"这次绘制要吞掉"。 */
    @Redirect(method = "renderSky", require = 0,
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void joes_addons_for_abmc$luckyDimensionSun(int shaderTexture, ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
            && LUCKY_DIMENSION.equals(minecraft.level.dimension().location())
            && texture != null) {
            if (VANILLA_SUN_PATH.equals(texture.getPath())) {
                joes_addons_for_abmc$sunPending = true;
                RenderSystem.setShaderTexture(shaderTexture, LUCKY_SUN_TEXTURE);
                return;
            }
            if (VANILLA_MOON_PATH.equals(texture.getPath())) {
                if (joes_addons_for_abmc$sunPending) {
                    // 太阳这一帧没画（条件渲染），别把 alpha 混合泄漏给月亮/星星
                    joes_addons_for_abmc$sunPending = false;
                    joes_addons_for_abmc$additiveBlend();
                }
                // 月亮：原版紧接着就会用这张贴图 drawWithShader 一次，
                // 在这里挂个标记，让那一次绘制被整个吞掉（见下面的 drawWithShader 重定向）。
                // 贴图本身照常设置：虽然不会画，但别让渲染状态跟原版不一样。
                joes_addons_for_abmc$moonPending = true;
            }
        }
        RenderSystem.setShaderTexture(shaderTexture, texture);
    }

    /** 原版画太阳/月亮/星星用的加色混合（{@code dst = src + dst}）。 */
    private static void joes_addons_for_abmc$additiveBlend() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
    }

    /**
     * 画太阳的那一次绘制：临时用 alpha 混合，画完还原加色混合（原版后面的月亮/星星要用）。
     * <p>月亮那一次（{@code moonPending}）则<b>整个不画</b>：把顶点缓冲关掉直接返回 ——
     * 云/月亮都被要求不渲染，而 {@code renderSky} 里 {@code BufferUploader.drawWithShader}
     * 一共只出现在三处（日出日落扇、太阳、月亮），顺序是太阳 → 月亮，
     * 所以这个标记一定是被月亮那一次消费掉的。
     */
    @Redirect(method = "renderSky", require = 0,
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void joes_addons_for_abmc$drawSunWithAlphaBlend(MeshData mesh) {
        if (joes_addons_for_abmc$moonPending) {
            joes_addons_for_abmc$moonPending = false;
            // 不画也要把顶点缓冲还回去：drawWithShader 正常路径会消费掉 MeshData，
            // 这里跳过了它，得自己 close（MeshData implements AutoCloseable）。
            mesh.close();
            return;
        }
        if (!joes_addons_for_abmc$sunPending) {
            BufferUploader.drawWithShader(mesh);
            return;
        }
        joes_addons_for_abmc$sunPending = false;
        RenderSystem.defaultBlendFunc();            // 普通 alpha 混合：贴图原色覆盖天空
        BufferUploader.drawWithShader(mesh);
        joes_addons_for_abmc$additiveBlend();       // 还原原版的加色混合
    }

    /**
     * 幸运维度<b>太阳尺寸缩到 1/3</b>（用户指定：边长 1/3，不是面积）。
     *
     * <p>原版 {@code renderSky} 里 {@code float f12 = 30.0F;}（LevelRenderer.java:1661）是太阳四边形的
     * <b>半边长</b>：四个顶点是 {@code (±f12, 100, ±f12)}，所以边长 60 → 改成 10 之后边长 20（= 1/3）、
     * 面积 1/9，正好对上"边长缩小为原来的 1/3"。太阳距相机固定 100，所以顶点坐标直接就是视觉尺寸。
     *
     * <p>为什么敢直接改常量：这个 {@code 30.0F} 在<b>整个 {@code LevelRenderer} 里只出现一次</b>
     * （对着本版编译产物核过），月亮用的是紧跟其后的 {@code f12 = 20.0F}，不在注入范围内；
     * 这个方法的其它常量（星星的 100.0F、天空盘的 512.0F 等）也都不在同一个方法里。
     *
     * <p>{@code require = 0}：万一将来 MC 改了这段渲染代码、注入点没了，也只是"太阳恢复原尺寸"，不会崩。
     */
    @ModifyConstant(method = "renderSky", require = 0, constant = @Constant(floatValue = 30.0F))
    private float joes_addons_for_abmc$luckySunSize(float original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && LUCKY_DIMENSION.equals(minecraft.level.dimension().location())) {
            return original / 3.0F;
        }
        return original;
    }

    /**
     * 幸运维度<b>不画云</b>（用户指定）。
     *
     * <p>云的唯一渲染入口是 {@code LevelRenderer#renderClouds(...)}（1.21.1 里是 public，
     * 由 {@code renderLevel} 在"玩家没把云设置关掉"时调用，LevelRenderer.java:1213-1219），
     * 所以直接在方法开头取消即可 —— 覆盖 FANCY / FAST 两种云，也不用管
     * {@code DimensionSpecialEffects#renderClouds} 那套（幸运维度现在共用主世界的特效）。
     *
     * <p>{@code require = 0}：注入点没了就只是"云又回来了"。
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void joes_addons_for_abmc$noClouds(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && LUCKY_DIMENSION.equals(minecraft.level.dimension().location())) {
            ci.cancel();
        }
    }
}
