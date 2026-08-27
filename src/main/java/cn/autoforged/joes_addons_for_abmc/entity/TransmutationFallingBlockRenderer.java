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

/**
 * 变形专用下落方块渲染器：
 * 与原版 FallingBlockRenderer 不同，这里总是渲染方块贴图——
 * 即使被变方块恰好与玩家脚下地面方块相同，也不会被当作“已放置方块”而跳过渲染。
 */
@OnlyIn(Dist.CLIENT)
public class TransmutationFallingBlockRenderer extends FallingBlockRenderer {
    private final BlockRenderDispatcher dispatcher;

    public TransmutationFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState blockstate = entity.getBlockState();
        if (blockstate.getRenderShape() == RenderShape.MODEL
            && blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            Level level = entity.level();
            poseStack.pushPose();
            BlockPos blockpos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            poseStack.translate(-0.5, 0.0, -0.5);
            var model = this.dispatcher.getBlockModel(blockstate);
            for (var renderType : model.getRenderTypes(blockstate,
                RandomSource.create(blockstate.getSeed(entity.getStartPos())),
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY)) {
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
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                        renderType);
            }
            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FallingBlockEntity entity) {
        return super.getTextureLocation(entity);
    }
}
