package cn.autoforged.joes_addons_for_abmc.block.renderer;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class LuckyPortalBlockRenderer implements BlockEntityRenderer<LuckyPortalBlockEntity> {

    private static final ResourceLocation PORTAL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ModMain.MODID, "textures/block/lucky_portal.png");

    public LuckyPortalBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull LuckyPortalBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(@NotNull LuckyPortalBlockEntity blockEntity) {
        return AABB.INFINITE;
    }

    @Override
    public void render(@NotNull LuckyPortalBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        long gameTime = level.getGameTime();
        int frame = (int) ((gameTime / 4) % 32);
        float vMin = frame / 32.0f;
        float vMax = (frame + 1) / 32.0f;

        int fullBright = LightTexture.FULL_BRIGHT;
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(PORTAL_TEXTURE));

        float portalTop = 6.0f / 16.0f;
        float portalBot = 0.0f / 16.0f;

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            0, portalTop, 0,  0, portalTop, 1,  1, portalTop, 1,  1, portalTop, 0,  0, 1, 0);

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            0, portalBot, 1,  0, portalBot, 0,  1, portalBot, 0,  1, portalBot, 1,  0, -1, 0);

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            0, portalBot, 0,  0, portalTop, 0,  0, portalTop, 1,  0, portalBot, 1,  -1, 0, 0);

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            1, portalBot, 1,  1, portalTop, 1,  1, portalTop, 0,  1, portalBot, 0,  1, 0, 0);

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            0, portalBot, 1,  0, portalTop, 1,  1, portalTop, 1,  1, portalBot, 1,  0, 0, 1);

        renderFace(consumer, matrix, pose, fullBright, packedOverlay, vMin, vMax,
            0, portalBot, 0,  0, portalTop, 0,  1, portalTop, 0,  1, portalBot, 0,  0, 0, -1);
    }

    private void renderFace(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                            int packedLight, int packedOverlay,
                            float vMin, float vMax,
                            float v1x, float v1y, float v1z,
                            float v2x, float v2y, float v2z,
                            float v3x, float v3y, float v3z,
                            float v4x, float v4y, float v4z,
                            float nx, float ny, float nz) {
        consumer.addVertex(matrix, v1x, v1y, v1z)
            .setColor(1F, 1F, 1F, 1F).setUv(0, vMin).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v2x, v2y, v2z)
            .setColor(1F, 1F, 1F, 1F).setUv(0, vMax).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v3x, v3y, v3z)
            .setColor(1F, 1F, 1F, 1F).setUv(1, vMax).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v4x, v4y, v4z)
            .setColor(1F, 1F, 1F, 1F).setUv(1, vMin).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
    }
}
