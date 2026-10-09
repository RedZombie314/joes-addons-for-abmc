package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.ThrownGlisteringMelonKnife;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 闪烁西瓜刀·投掷物渲染器。
 * <p>原版 {@link net.minecraft.client.renderer.entity.ThrownItemRenderer} 会把投掷物画成
 * 「永远正对摄像机」的一张平面贴图（广告牌），这里改成直接渲染物品模型本身：
 * 即 {@code item/handheld → item/generated → builtin/generated} 的模型——两层贴图之间还有
 * 1 像素厚度、带正反面的立体物品模型，和拿在手上/掉在地上时看到的是同一个东西。
 * <p>朝向：把贴图里刀刃所在的对角线（{@link #BLADE_ANGLE_DEGREES}）对齐到飞行方向，
 * 于是刀尖始终朝前、刀柄在后；飞行途中不再自转，也不再跟随摄像机。
 * 整刀绕自身长轴的滚转固定为「刀面竖直」（与三叉戟一致），从侧面能看到完整刀身。
 * <p><b>尺寸</b>：贴图（{@code textures/item/glistering_melon_knife.png}）保持原始分辨率，
 * 永远不做任何缩放/重采样；模型大小由 {@link #REFERENCE_PIXELS} 换算得出：
 * {@code scale = REFERENCE_PIXELS / 贴图宽度}（如 800 px 的贴图 → 16/800 = 0.02），
 * 贴图尺寸是运行时从物品模型实际用的那张图读出来的，所以以后换成别的高分辨率贴图会自动生效。
 * 效果等同给生物调 {@code Entity#getScale}，只影响渲染出来的模型大小。
 */
public class ThrownGlisteringMelonKnifeRenderer extends EntityRenderer<ThrownGlisteringMelonKnife> {
    /**
     * 贴图中刀尖相对 +X 轴的角度（模型本地 XY 平面内，逆时针为正）。
     * 本贴图是左下刀柄、右上刀刃的标准 45° 对角线构图，若以后换了构图只需改这个常量。
     */
    private static final float BLADE_ANGLE_DEGREES = 45.0F;
    /**
     * 缩放换算基准：{@code 渲染缩放 = 该值 / 贴图宽度}。
     * <p>本贴图宽 797 px，所以当前渲染缩放 = 510 / 797 ≈ <b>0.64</b>（刀约 0.64 格）。
     * <p>换算规则不变，只调这个基准：基准越大、渲染出来的刀越大
     * （基准 = 目标缩放 × 贴图宽度；如 0.08→64、0.32→255、1.0→797）。
     */
    private static final float REFERENCE_PIXELS = 510.0F;

    private final ItemRenderer itemRenderer;

    public ThrownGlisteringMelonKnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownGlisteringMelonKnife entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return; // 出手瞬间正好损坏：没有可渲染的刀
        // 与原版投掷物一致：刚出手的前 2 刻若贴脸则不画，免得刀糊在镜头上
        if (entity.tickCount < 2
            && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) {
            return;
        }

        poseStack.pushPose();
        // 1) 最外层：把模型本地 +Z 转到飞行方向（无速度时退回实体朝向，保证始终有一个稳定姿态）
        float yaw;
        float pitch;
        Vec3 direction = flightDirection(entity);
        if (direction != null) {
            yaw = (float) (Mth.atan2(direction.x, direction.z) * (180.0 / Math.PI));
            pitch = (float) (Mth.atan2(direction.y,
                Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI));
        } else {
            yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        // 2) 内层：把贴图里的刀尖对角线先转到 +X，再绕 Y 转到 +Z（供上一步对齐到飞行方向）
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-BLADE_ANGLE_DEGREES));
        float scale = textureScale(stack);
        poseStack.scale(scale, scale, scale);

        this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    /**
     * 渲染缩放 = {@link #REFERENCE_PIXELS} / 该物品模型实际使用的贴图宽度。
     * <p>贴图宽度是运行时从物品模型（{@code layer0} 对应的 sprite）读出来的，所以
     * 贴图换成任何分辨率都会自动按「贴图尺寸 : 16」的比例换算，不需要改代码。
     */
    private float textureScale(ItemStack stack) {
        var model = this.itemRenderer.getItemModelShaper().getItemModel(stack);
        if (model != null) {
            int width = model.getParticleIcon().contents().width();
            if (width > 0) {
                return REFERENCE_PIXELS / (float) width;
            }
        }
        return 1.0F;
    }

    /** 本刻的飞行方向：优先用前后两刻的位置差（比 deltaMovement 更贴近真实轨迹）。 */
    private static Vec3 flightDirection(ThrownGlisteringMelonKnife entity) {
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        double lengthSqr = dx * dx + dy * dy + dz * dz;
        if (lengthSqr < 1.0E-8) {
            Vec3 velocity = entity.getDeltaMovement();
            dx = velocity.x;
            dy = velocity.y;
            dz = velocity.z;
            lengthSqr = dx * dx + dy * dy + dz * dz;
        }
        if (lengthSqr < 1.0E-8) return null;
        double length = Math.sqrt(lengthSqr);
        return new Vec3(dx / length, dy / length, dz / length);
    }

    /** 物品模型在方块图集里，此处仅为满足 EntityRenderer 的抽象方法。 */
    @Override
    public ResourceLocation getTextureLocation(ThrownGlisteringMelonKnife entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
