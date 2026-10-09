package cn.autoforged.joes_addons_for_abmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>头盔栏里挂着地狱疣 → 第一人称视角被地狱疣贴图铺满</b>（wart on a stick 的视觉表现）。
 *
 * <h3>为什么是 GUI 图层，而不是往 ScreenEffectRenderer 里画</h3>
 * <ol>
 *   <li>原版 {@code ScreenEffectRenderer.renderScreenEffect} 里所有全屏叠加层的顶点坐标都是
 *       <b>-1 ~ 1 的归一化空间</b>（见它自己的 renderTex/renderFluid），不是 GUI 像素；用 GUI 像素画在那里
 *       会整块画到屏幕外（第一版就是因此什么都看不到）；</li>
 *   <li>而且那个方法在旁观模式下根本不会被调用（调用点在 {@code GameRenderer} 的手部渲染里），
 *       需求要求"旁观戴着地狱疣的生物"也要生效，所以必须换成 HUD 渲染阶段；</li>
 *   <li>于是改成注册一个 <b>GUI 图层</b>（NeoForge {@code RegisterGuiLayersEvent}），插在原版
 *       {@code VanillaGuiLayers.CAMERA_OVERLAYS}（南瓜模糊、水下、着火那一层）之后 ——
 *       层级与南瓜模糊一致：盖在世界之上、准星/快捷栏之下，且旁观模式下同样会渲染。
 *       画法也照抄原版 {@code Gui#renderTextureOverlay} 的写法（关深度测试、开混合、{@code GuiGraphics.blit}）。</li>
 * </ol>
 *
 * <p>判定用的是<b>摄像机实体</b>（{@code Minecraft#getCameraEntity()}）而不是本地玩家，
 * 所以旁观模式下旁观一只"戴着地狱疣"的生物同样会铺满。
 *
 * <p>贴图用的是原版地狱疣物品贴图，并且只取它<b>不透明的那一小块</b>（16×16 里的 (4,4)-(12,13)），
 * 按 {@value #TILE_WIDTH}×{@value #TILE_HEIGHT} GUI 像素逐块平铺、块与块紧贴，
 * 于是整屏被地狱疣"铺满"。
 */
@OnlyIn(Dist.CLIENT)
public final class WartHelmetOverlay {
    /** 平铺用的贴图：原版地狱疣物品贴图（16×16）。 */
    private static final ResourceLocation WART_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/item/nether_wart.png");
    private static final int TEXTURE_SIZE = 16;
    /** 地狱疣在这张贴图里的不透明范围：x 4~11、y 4~12（用 Pillow 量出来的 alpha bbox）。 */
    private static final float U_OFFSET = 4.0F;
    private static final float V_OFFSET = 4.0F;
    private static final int U_WIDTH = 8;
    private static final int V_HEIGHT = 9;

    /** 每一块地狱疣在屏幕上的大小（GUI 像素）——8:9 的源，等比放大，块之间紧贴。 */
    private static final int TILE_WIDTH = 20;
    private static final int TILE_HEIGHT = 22;

    private WartHelmetOverlay() {
    }

    /** GUI 图层入口（注册在 {@code VanillaGuiLayers.CAMERA_OVERLAYS} 之后）。 */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) return;

        Entity camera = minecraft.getCameraEntity();
        if (!(camera instanceof LivingEntity living)) return;
        if (!living.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHER_WART)) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        for (int y = 0; y < height; y += TILE_HEIGHT) {
            for (int x = 0; x < width; x += TILE_WIDTH) {
                // 最后一行/一列会超出屏幕，交给投影自然裁掉
                graphics.blit(WART_TEXTURE, x, y, TILE_WIDTH, TILE_HEIGHT,
                    U_OFFSET, V_OFFSET, U_WIDTH, V_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
            }
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
