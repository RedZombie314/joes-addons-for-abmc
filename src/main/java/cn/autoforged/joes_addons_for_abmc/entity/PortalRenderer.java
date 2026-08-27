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

public class PortalRenderer extends EntityRenderer<PortalEntity> {
    private static final ResourceLocation END_PORTAL_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/end_portal.png");

    public PortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PortalEntity entity) {
        return END_PORTAL_TEXTURE;
    }

    @Override
    public void render(PortalEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0.0, entity.getBbHeight() / 2.0 - 0.25, 0.0);

        float yaw = entity.getPortalYaw();
        float pitch = entity.getPortalPitch();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 整扇门以中心点为基准，绘制一张无厚度的单面盘面
        poseStack.translate(-1.0, -1.0, 0.0);
        poseStack.scale(2.0F, 2.0F, 1.0F);

        renderPortalFace(poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.endPortal());
        Matrix4f matrix = poseStack.last().pose();

        // 正面（法线朝 -Z）
        addQuad(consumer, matrix, packedLight,
            0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, -1);
        // 背面（法线朝 +Z），让门的另一侧同样渲染出末地传送门材质
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
    protected boolean shouldShowName(PortalEntity entity) {
        return false;
    }
}
