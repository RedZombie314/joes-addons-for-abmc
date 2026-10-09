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
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>只在幸运维度</b>把太阳贴图换成幸运方块贴图
 * （{@code joes_addons_for_abmc:textures/environment/lucky_sun.png}），
 * 并且让这张贴图**按原色显示**。
 *
 * <p>原版太阳贴图是全局资源（{@code minecraft:textures/environment/sun.png}），没有"按维度换"的开关，
 * 直接覆盖那张贴图会影响所有维度。所以这里在 {@code LevelRenderer.renderSky} 里重定向
 * {@code RenderSystem.setShaderTexture(...)}：只有当传入的贴图正是太阳、且当前维度是幸运维度时，
 * 才替换成幸运方块贴图；其它情况（月亮、云、末地天空、以及其它维度）原样放行。
 *
 * <p><b>为什么还要改混合模式</b>：原版画太阳之前调用
 * {@code RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)}，即<b>加色混合</b>
 * （{@code dst = src + dst}）。太阳的颜色是"加"到天空上的：白天天空本身已经很亮，
 * 贴图里任何偏亮的像素加上去都会 &gt;1 被截断成纯白 —— 这是数学上的必然，
 * 加色混合下**不可能**原样显示一张贴图。所以这里在太阳真正上传顶点的那一次绘制外面，
 * 临时把混合换成普通的 alpha 混合（{@code dst = src*a + dst*(1-a)}），画完立刻还原加色混合，
 * 这样月亮/星星/云以及其它维度完全不受影响。
 *
 * <p>注入点说明（对应 1.21.1 的字节码）：太阳的 {@code drawWithShader} 之后紧跟着就是月亮的
 * {@code setShaderTexture}，中间没有任何绘制，所以用"换贴图时挂起一个标记、下一次绘制时消费"的做法
 * 是确定且无泄漏的；另外月亮换贴图时如果标记还在（太阳这一帧因条件没画），也会把状态还原回去。
 *
 * <p>{@code require = 0}：万一将来 MC 改了这段渲染代码导致注入点消失，也只是"太阳不换"，不会崩游戏。
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

    /** 换贴图：幸运维度 + 太阳 -> 幸运方块贴图。 */
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
            if (VANILLA_MOON_PATH.equals(texture.getPath()) && joes_addons_for_abmc$sunPending) {
                // 太阳这一帧没画（条件渲染），别把 alpha 混合泄漏给月亮/星星
                joes_addons_for_abmc$sunPending = false;
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            }
        }
        RenderSystem.setShaderTexture(shaderTexture, texture);
    }

    /** 画太阳的那一次绘制：临时用 alpha 混合，画完还原加色混合（原版后面的月亮/星星要用）。 */
    @Redirect(method = "renderSky", require = 0,
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void joes_addons_for_abmc$drawSunWithAlphaBlend(MeshData mesh) {
        if (!joes_addons_for_abmc$sunPending) {
            BufferUploader.drawWithShader(mesh);
            return;
        }
        joes_addons_for_abmc$sunPending = false;
        RenderSystem.defaultBlendFunc();            // 普通 alpha 混合：贴图原色覆盖天空
        BufferUploader.drawWithShader(mesh);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,   // 还原原版的加色混合
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
    }
}
