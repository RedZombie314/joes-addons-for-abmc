package cn.autoforged.joes_addons_for_abmc.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

@OnlyIn(Dist.CLIENT)
public class LapisFallingBlockRenderer extends FallingBlockRenderer {

    private final BlockRenderDispatcher blockRenderer;

    public LapisFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        if (entity instanceof LapisFallingBlockEntity lapisEntity && lapisEntity.getHasGlint()) {
            BlockState blockstate = lapisEntity.getBlockState();
            if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
                poseStack.pushPose();
                BlockPos blockpos = BlockPos.containing(
                    entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
                poseStack.translate(-0.5, 0.0, -0.5);

                var model = this.blockRenderer.getBlockModel(blockstate);
                RandomSource random = RandomSource.create(blockstate.getSeed(entity.getStartPos()));
                ModelData modelData = ModelData.EMPTY;
                VertexConsumer glintConsumer = buffer.getBuffer(RenderType.entityGlint());

                for (Direction dir : Direction.values()) {
                    var quads = model.getQuads(blockstate, dir, random, modelData, null);
                    for (var quad : quads) {
                        glintConsumer.putBulkData(poseStack.last(), quad,
                            1.0F, 1.0F, 1.0F, 0.4F, packedLight, OverlayTexture.NO_OVERLAY);
                    }
                }
                var quads = model.getQuads(blockstate, null, random, modelData, null);
                for (var quad : quads) {
                    glintConsumer.putBulkData(poseStack.last(), quad,
                        1.0F, 1.0F, 1.0F, 0.4F, packedLight, OverlayTexture.NO_OVERLAY);
                }

                poseStack.popPose();
            }
        }
    }
}
