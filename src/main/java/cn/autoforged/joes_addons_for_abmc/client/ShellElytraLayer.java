package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>空壳的鞘翅渲染层</b>：胸甲栏里是原版鞘翅时按鞘翅画出来，并且<b>滑翔/俯冲时两翼是张开的</b>。
 *
 * <h2>为什么不用原版 {@code ElytraLayer}</h2>
 * 原版 {@code ElytraModel#setupAnim} 判断"张不张开"只认 {@code entity.isFallFlying()}，
 * 而实测里空壳在客户端拿不到这个标记（{@code DATA_SHARED_FLAGS_ID} 的第 7 位）——
 * 于是重锤俯冲时身体是躺平的（姿势走 {@code Pose.FALL_FLYING}，是同步的），
 * <b>鞘翅却一直保持收拢的样子</b>。
 * <p>
 * 所以这一层同时认两个条件：{@code isFallFlying() || getPose() == Pose.FALL_FLYING}。
 * 后者由 {@code PlayerShellEntity#tick} 在滑翔期间设置、并且是<b>同步字段</b>（{@code DATA_POSE}），
 * 客户端一定拿得到 —— 只要身体躺平了，翅膀就张开，两者永远一致。
 *
 * <p>角度数值照原版 {@code ElytraModel#setupAnim}：
 * 收拢 {@code (xRot, zRot) = (π/12, -π/12)}，张开 {@code (π/9, -π/2)}；潜行姿态 {@code (2π/9, -π/4)} + 上移 3。
 */
@OnlyIn(Dist.CLIENT)
public class ShellElytraLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    /** 原版鞘翅贴图。 */
    private static final ResourceLocation ELYTRA_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    private static final float IDLE_X_ROT = (float) (Math.PI / 12);
    private static final float IDLE_Z_ROT = (float) (-Math.PI / 12);
    private static final float GLIDE_X_ROT = (float) (Math.PI / 9);
    private static final float GLIDE_Z_ROT = (float) (-Math.PI / 2);
    private static final float SNEAK_X_ROT = (float) (Math.PI * 2.0 / 9.0);
    private static final float SNEAK_Z_ROT = (float) (-Math.PI / 4);
    private static final float SNEAK_Y_OFFSET = 3.0F;
    private static final float SNEAK_Y_ROT = 0.08726646F;

    private final WingsModel<T> elytraModel;

    public ShellElytraLayer(RenderLayerParent<T, M> renderer, EntityModelSet modelSet) {
        super(renderer);
        // 复用原版鞘翅烘焙好的网格（部件名 left_wing / right_wing）
        this.elytraModel = new WingsModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        this.getParentModel().copyPropertiesTo(this.elytraModel);
        applyWingAngles(entity);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffer,
            RenderType.armorCutoutNoCull(ELYTRA_TEXTURE), chest.hasFoil());
        this.elytraModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** 滑翔/俯冲 → 两翼张开；潜行 → 原版潜行姿态；其余 → 收拢。 */
    private void applyWingAngles(T entity) {
        if (isGliding(entity)) {
            this.elytraModel.setWingAngles(0.0F, GLIDE_X_ROT, 0.0F, GLIDE_Z_ROT);
            return;
        }
        if (entity.isCrouching()) {
            this.elytraModel.setWingAngles(SNEAK_Y_OFFSET, SNEAK_X_ROT, SNEAK_Y_ROT, SNEAK_Z_ROT);
            return;
        }
        this.elytraModel.setWingAngles(0.0F, IDLE_X_ROT, 0.0F, IDLE_Z_ROT);
    }

    /**
     * 是否算"在滑翔"：原版的滑翔标记，或（空壳在客户端拿不到它时的替代）
     * 服务端同步过来的 {@code Pose.FALL_FLYING} —— 后者由 {@link PlayerShellEntity#tick} 设置。
     */
    private static boolean isGliding(LivingEntity entity) {
        return entity.isFallFlying() || entity.getPose() == Pose.FALL_FLYING;
    }
}
