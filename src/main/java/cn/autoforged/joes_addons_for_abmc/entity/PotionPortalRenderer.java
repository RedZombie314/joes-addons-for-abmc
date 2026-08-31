package cn.autoforged.joes_addons_for_abmc.entity;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 随机传送门渲染：绘制一张 1×2 的末地传送门单面盘（竖直）、朝门法线正向，
 * 与传送门权杖的 PortalRenderer 渲染风格一致。
 */
public class PotionPortalRenderer extends EntityRenderer<PotionPortalEntity> {
    private static final ResourceLocation END_PORTAL_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/end_portal.png");

    public PotionPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PotionPortalEntity entity) {
        return END_PORTAL_TEXTURE;
    }

    @Override
    public void render(PotionPortalEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw = entity.getPortalYaw();
        float pitch = entity.getPortalPitch();
        boolean horiz = Math.abs(pitch) > 45.0F;

        // 竖直门：中心在 2 格高度中央；水平门：贴图紧贴门底（方块上/下表面）
        poseStack.translate(0.0, horiz ? 0.0 : 1.0, 0.0);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 竖直门：宽 1、高 2；水平门：1×1（贴面）
        poseStack.translate(-0.5, horiz ? -0.5 : -1.0, 0.0);
        poseStack.scale(1.0F, horiz ? 1.0F : 2.0F, 1.0F);

        renderPortalFace(poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.endPortal());
        Matrix4f matrix = poseStack.last().pose();

        // 正面（法线朝 -Z）
        addQuad(consumer, matrix, packedLight,
            0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, -1);
        // 背面（法线朝 +Z）
        addQuad(consumer, matrix, packedLight,
            1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1);
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, int packedLight,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float nx, float ny, float nz) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(255, 255, 255, 255)
            .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
            .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x1, y1, z1).setColor(255, 255, 255, 255)
            .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
            .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(255, 255, 255, 255)
            .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
            .setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x3, y3, z3).setColor(255, 255, 255, 255)
            .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
            .setNormal(nx, ny, nz);
    }

    @Override
    protected boolean shouldShowName(PotionPortalEntity entity) {
        return false;
    }
}