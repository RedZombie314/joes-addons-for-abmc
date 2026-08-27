package cn.autoforged.joes_addons_for_abmc.block.renderer;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyDimensionBlockEntity;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class LuckyDimensionBlockRenderer implements BlockEntityRenderer<LuckyDimensionBlockEntity> {

    private static final ResourceLocation FILTER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "joes_addons_for_abmc", "textures/block/filter.png");

    private static final ResourceLocation LUCKY_DIMENSION_KEY = ResourceLocation.fromNamespaceAndPath(
        ModMain.MODID, "lucky_dimension");

    private final BlockRenderDispatcher blockRenderer;

    public LuckyDimensionBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull LuckyDimensionBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(@NotNull LuckyDimensionBlockEntity blockEntity) {
        return AABB.INFINITE;
    }

    @Override
    public void render(@NotNull LuckyDimensionBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        boolean useRandomTextures = ModConfig.LUCKY_DIMENSION_RANDOM_TEXTURES.get();
        Level level = blockEntity.getLevel();
        boolean isInLuckyDimension = level != null
            && LUCKY_DIMENSION_KEY.equals(level.dimension().location());

        if (!useRandomTextures || isInLuckyDimension) {
            return;
        }

        ResourceLocation currentTex = blockEntity.getCurrentTexture();
        Block mimicBlock = currentTex != null ? BuiltInRegistries.BLOCK.get(currentTex) : null;
        if (mimicBlock != null) {
            BlockState mimicState = mimicBlock.defaultBlockState();
            poseStack.pushPose();
            blockRenderer.renderSingleBlock(mimicState, poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
            renderFilterOverlay(poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void renderFilterOverlay(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                                      int packedLight, int packedOverlay) {
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(FILTER_TEXTURE));

        float a = 0.5F;
        float m = 0.002F;

        quad(consumer, matrix, pose, packedLight, packedOverlay,
            0, 0, 1,  0, 1, 1,  1, 1, 1,  1, 0, 1,  0, 0, 1,  m, a);
        quad(consumer, matrix, pose, packedLight, packedOverlay,
            0, 0, 0,  0, 1, 0,  1, 1, 0,  1, 0, 0,  0, 0, -1, m, a);
        quad(consumer, matrix, pose, packedLight, packedOverlay,
            0, 0, 0,  0, 1, 0,  0, 1, 1,  0, 0, 1,  -1, 0, 0, m, a);
        quad(consumer, matrix, pose, packedLight, packedOverlay,
            1, 0, 1,  1, 1, 1,  1, 1, 0,  1, 0, 0,  1, 0, 0, m, a);
        quad(consumer, matrix, pose, packedLight, packedOverlay,
            0, 1, 0,  0, 1, 1,  1, 1, 1,  1, 1, 0,  0, 1, 0, m, a);
        quad(consumer, matrix, pose, packedLight, packedOverlay,
            0, 0, 0,  0, 0, 1,  1, 0, 1,  1, 0, 0,  0, -1, 0, m, a);
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                      int packedLight, int packedOverlay,
                      float v1x, float v1y, float v1z,
                      float v2x, float v2y, float v2z,
                      float v3x, float v3y, float v3z,
                      float v4x, float v4y, float v4z,
                      float nx, float ny, float nz,
                      float margin, float a) {
        consumer.addVertex(matrix, shift(v1x, margin), shift(v1y, margin), shift(v1z, margin))
            .setColor(1F, 1F, 1F, a).setUv(0, 0).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, shift(v2x, margin), shift(v2y, margin), shift(v2z, margin))
            .setColor(1F, 1F, 1F, a).setUv(0, 1).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, shift(v3x, margin), shift(v3y, margin), shift(v3z, margin))
            .setColor(1F, 1F, 1F, a).setUv(1, 1).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, shift(v4x, margin), shift(v4y, margin), shift(v4z, margin))
            .setColor(1F, 1F, 1F, a).setUv(1, 0).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
    }

    private static float shift(float val, float margin) {
        if (val <= 0.001F) return -margin;
        if (val >= 0.999F) return 1.0F + margin;
        return val;
    }
}
