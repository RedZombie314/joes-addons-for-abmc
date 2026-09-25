package cn.autoforged.joes_addons_for_abmc.entity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * 可持有结构渲染器：参照原版 {@link DisplayRenderer.BlockDisplayRenderer}。整个结构作为一个
 * 刚体渲染——先对整个 PoseStack 应用「偏移 + 绕结构中心旋转 + 三轴镜像」的整体变换，
 * 再按各方块原始相对偏移铺开。这样旋转/镜像时方块朝向跟随整个结构（而非逐方块各转各的）。
 * <p>镜像用负缩放实现，会翻转面朝向，为避免内翻/掉面，镜像时用无剔除（cull-off）的 RenderType 渲染。</p>
 */
@OnlyIn(Dist.CLIENT)
public class HoldableStructureRenderer extends DisplayRenderer<HoldableStructureEntity, HoldableStructureEntity.StructureRenderState> {

    /** 无剔除的离方块 RenderType（用于镜像时避免负缩放翻转面朝向导致内翻/掉面）。 */
    private static final RenderType BLOCK_NO_CULL = RenderType.create(
        "joes_block_no_cull",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        4194304,
        true,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setShaderState(RenderStateShard.RENDERTYPE_SOLID_SHADER)
            .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(true)
    );

    private final BlockRenderDispatcher blockRenderer;

    public HoldableStructureRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Nullable
    @Override
    protected HoldableStructureEntity.StructureRenderState getSubState(HoldableStructureEntity entity) {
        return entity.structureRenderState();
    }

    @Override
    protected void renderInner(HoldableStructureEntity entity, HoldableStructureEntity.StructureRenderState renderState,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        if (renderState == null || renderState.blocks() == null) return;
        java.util.List<HoldableStructureEntity.StructureBlock> blocks = renderState.blocks();

        // 结构包围盒中心（[rel, rel+1] 的完整格中心）
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (HoldableStructureEntity.StructureBlock sb : blocks) {
            if (sb == null) continue;
            double ox = sb.pos().getX(), oy = sb.pos().getY(), oz = sb.pos().getZ();
            minX = Math.min(minX, ox); minY = Math.min(minY, oy); minZ = Math.min(minZ, oz);
            maxX = Math.max(maxX, ox + 1); maxY = Math.max(maxY, oy + 1); maxZ = Math.max(maxZ, oz + 1);
        }
        float cx = (float)((minX + maxX) / 2.0);
        float cy = (float)((minY + maxY) / 2.0);
        float cz = (float)((minZ + maxZ) / 2.0);

        boolean mirrorX = entity.getMirrorX(), mirrorY = entity.getMirrorY(), mirrorZ = entity.getMirrorZ();
        boolean noCull = ((mirrorX ? 1 : 0) + (mirrorY ? 1 : 0) + (mirrorZ ? 1 : 0)) % 2 == 1;

        // 整体刚体变换：偏移 → 平移到中心 → 绕中心旋转 → 三轴镜像(负缩放) → 平移回。
        poseStack.pushPose();

        float ax = entity.getAvatarX(), ay = entity.getAvatarY(), az = entity.getAvatarZ();
        poseStack.translate(ax, ay, az);
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(entity.getAvatarRX()),
            (float) Math.toRadians(entity.getAvatarRY()),
            (float) Math.toRadians(entity.getAvatarRZ())));
        poseStack.scale(mirrorX ? -1.0F : 1.0F, mirrorY ? -1.0F : 1.0F, mirrorZ ? -1.0F : 1.0F);
        poseStack.translate(-cx, -cy, -cz);

        for (HoldableStructureEntity.StructureBlock sb : blocks) {
            if (sb == null || sb.state() == null) continue;
            BlockState state = sb.state();
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
            poseStack.pushPose();
            poseStack.translate(sb.pos().getX(), sb.pos().getY(), sb.pos().getZ());
            if (noCull) {
                this.blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, BLOCK_NO_CULL);
            } else {
                this.blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}