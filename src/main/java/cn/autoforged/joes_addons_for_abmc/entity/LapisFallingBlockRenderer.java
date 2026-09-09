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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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
        BlockState blockstate = entity.getBlockState();
        if (blockstate.getRenderShape() == RenderShape.MODEL) {
            // 下落方块实体位于方格中心(X/Z)与格底(Y)：平移(-0.5,0,-0.5)让 1×1 方块严格对齐所在格，
            // 不再向东/南/下偏移半格。此处已完整渲染模型，故直接 return，避免 super 重复渲染。
            Level level = entity.level();
            poseStack.pushPose();
            BlockPos blockpos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
            poseStack.translate(-0.5, 0.0, -0.5);
            var model = this.blockRenderer.getBlockModel(blockstate);
            RandomSource random = RandomSource.create(blockstate.getSeed(entity.getStartPos()));
            ModelData modelData = ModelData.EMPTY;
            for (var renderType : model.getRenderTypes(blockstate, random, modelData)) {
                this.blockRenderer
                    .getModelRenderer()
                    .tesselateBlock(
                        level,
                        this.blockRenderer.getBlockModel(blockstate),
                        blockstate,
                        blockpos,
                        poseStack,
                        buffer.getBuffer(net.neoforged.neoforge.client.RenderTypeHelper.getMovingBlockRenderType(renderType)),
                        false,
                        RandomSource.create(),
                        blockstate.getSeed(entity.getStartPos()),
                        OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY,
                        renderType);
            }
            poseStack.popPose();

            // 附魔辉光覆盖层（同样对齐）
            if (entity instanceof LapisFallingBlockEntity lapisEntity && lapisEntity.getHasGlint()) {
                poseStack.pushPose();
                BlockPos glintPos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
                poseStack.translate(-0.5, 0.0, -0.5);
                var glintModel = this.blockRenderer.getBlockModel(blockstate);
                RandomSource rnd = RandomSource.create(blockstate.getSeed(entity.getStartPos()));
                VertexConsumer glintConsumer = buffer.getBuffer(RenderType.entityGlint());
                for (Direction dir : Direction.values()) {
                    var quads = glintModel.getQuads(blockstate, dir, rnd, modelData, null);
                    for (var quad : quads) {
                        glintConsumer.putBulkData(poseStack.last(), quad,
                            1.0F, 1.0F, 1.0F, 0.4F, packedLight, OverlayTexture.NO_OVERLAY);
                    }
                }
                var quads = glintModel.getQuads(blockstate, null, rnd, modelData, null);
                for (var quad : quads) {
                    glintConsumer.putBulkData(poseStack.last(), quad,
                        1.0F, 1.0F, 1.0F, 0.4F, packedLight, OverlayTexture.NO_OVERLAY);
                }
                poseStack.popPose();
            }
            return; // MODEL 分支已完整渲染；主模型栈已在上面 popPose，非 MODEL 方块由 super 交 mixin 处理
        }
        if (!blockstate.isAir()) {
            // 非 MODEL 方块（箱子/木桶等）：交给 FallingBlockRendererMixin 回退到物品模型渲染
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }
}