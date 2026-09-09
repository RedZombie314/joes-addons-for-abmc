package cn.autoforged.joes_addons_for_abmc.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 基岩下落方块渲染器：方块实体位于方格中心(X/Z)与格底(Y)，这里把方块模型整体相对实体
 * 平移 (-0.5, 0, -0.5)，使渲染出的 1×1 方块严格对齐其所在格 [x,x+1]×[y,y+1]×[z,z+1]，
 * 不再出现向东/南方向各 0.5 格的偏移。
 */
@OnlyIn(Dist.CLIENT)
public class BedrockFallingBlockRenderer extends FallingBlockRenderer {
    private final BlockRenderDispatcher dispatcher;

    public BedrockFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState blockstate = entity.getBlockState();
        if (blockstate.getRenderShape() == RenderShape.MODEL) {
            Level level = entity.level();
            poseStack.pushPose();
            BlockPos blockpos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
            poseStack.translate(-0.5, 0.0, -0.5);
            var model = this.dispatcher.getBlockModel(blockstate);
            for (var renderType : model.getRenderTypes(blockstate,
                RandomSource.create(blockstate.getSeed(entity.getStartPos())),
                ModelData.EMPTY)) {
                this.dispatcher
                    .getModelRenderer()
                    .tesselateBlock(
                        level,
                        this.dispatcher.getBlockModel(blockstate),
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
            return; // MODEL 分支已完整渲染，交由 mixin 处理非 MODEL 方块（箱子/木桶）外观
        }
        if (!blockstate.isAir()) {
            // 非 MODEL 方块：交给 FallingBlockRendererMixin 回退到物品模型渲染
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FallingBlockEntity entity) {
        return super.getTextureLocation(entity);
    }
}