package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.event.ClientEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders the Herobrine head projectile using the player head model (an 8x8x8
 * cube) with the Herobrine.png texture. The model layer is defined with a
 * 32x16 texture size so the 32x16 player-head texture layout maps 1:1.
 */
@OnlyIn(Dist.CLIENT)
public class HerobrineHeadRenderer extends EntityRenderer<HerobrineHeadEntity> {
    private static final ResourceLocation HEROBRINE_HEAD_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/entity/herobrine_head.png");
    private final SkullModel model;

    public HerobrineHeadRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SkullModel(context.bakeLayer(ClientEvents.HEROBRINE_HEAD_LAYER));
    }

    public static LayerDefinition createHerobrineHeadLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.ZERO);
        return LayerDefinition.create(meshdefinition, 32, 16);
    }

    @Override
    public void render(HerobrineHeadEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 使用者自己发射的 Him 头颅：在第一人称视角下，距离玩家 2.5 格以内时不渲染，
        // 避免头颅贴脸遮挡视线。仅对“本人 owns 的实体”生效，他人视角/第三人称不受影响。
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null
            && entity.getOwner() == mc.player
            && mc.options.getCameraType().isFirstPerson()
            && entity.distanceToSqr(mc.player) < 2.5 * 2.5) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        float yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        VertexConsumer vertexconsumer = buffer.getBuffer(this.model.renderType(HEROBRINE_HEAD_TEXTURE));
        this.model.setupAnim(0.0F, yRot, xRot);
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HerobrineHeadEntity entity) {
        return HEROBRINE_HEAD_TEXTURE;
    }
}
