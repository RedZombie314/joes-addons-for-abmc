package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.BrewingStaffCloudEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 酿造台权杖药水云的渲染器：状态效果云完全由粒子渲染，本渲染器不绘制任何模型。
 * 注册它是为了给 {@code brewing_staff_cloud} 实体类型提供非空渲染器，
 * 否则 EntityRenderDispatcher 在部分 mixin（如 ReplayMod）下取到 null 渲染器会崩溃。
 */
public class BrewingStaffCloudRenderer extends EntityRenderer<BrewingStaffCloudEntity> {

    public BrewingStaffCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BrewingStaffCloudEntity entity, float yRot, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 状态效果云无模型：粒子由 AreaEffectCloud.tick 自行生成
    }

    @Override
    public ResourceLocation getTextureLocation(BrewingStaffCloudEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "textures/entity/brewing_staff_cloud.png");
    }
}
