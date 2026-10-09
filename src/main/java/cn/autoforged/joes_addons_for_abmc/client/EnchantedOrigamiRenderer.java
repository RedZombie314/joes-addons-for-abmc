package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.EnchantedOrigamiEntity;
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

/**
 * 附魔千纸鹤渲染器：把原版 {@code ThrownItemRenderer} 的「永远正对摄像机」改成用实体自己的朝向。
 * <p>
 * 原版投掷物渲染器的姿态只有一步 {@code poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation())}，
 * 也就是拿<strong>相机的</strong> yaw/pitch 摆正面片，所以贴图永远朝向玩家、像一块广告牌。
 * 这里改成按千纸鹤自己的 {@code yRot}/{@code xRot} 摆；
 * {@code EnchantedOrigamiEntity#updateFlight} 早就按飞行方向设置好了这两个角（水平取向 + 爬升俯冲），
 * 以前被广告牌盖掉了，现在直接生效 = 整个身体等同于一个能左右转、能点头的“头”。
 * <p>
 * <b>姿态（关键）</b>：贴图是一张平面纸片，这里让纸片的<strong>面包含飞行方向</strong>
 * （而不是垂直于飞行方向），于是：
 * <ul>
 *   <li>从它<strong>侧方</strong>看 → 正对纸面，看到完整全身；</li>
 *   <li>从它<strong>正前 / 正后</strong>方看（沿飞行轴）→ 纸片侧过来，是一条线。</li>
 * </ul>
 * 这是刻意的取舍：平面贴图在 3D 里绕不开“某个角度会变成线”。
 * <p>
 * <b>贴图在纸片里的摆法</b>：贴图的 +X（右）被对齐到飞行方向、+Y（上）就是世界“上”，
 * 所以鹤身是竖直的、头朝前——靠 {@link #HEAD_ANGLE_DEGREES} 这个常量控制，换贴图只改它。
 * <p>
 * 做法与 {@code ThrownGlisteringMelonKnifeRenderer} 同源（那边是“刀尖对齐飞行方向”）：
 * 外层把纸片本地 +Z 转到实体朝向，内层先把贴图里的“前方”转进 +X、再绕 Y 转到 +Z 交给外层。
 * <p>
 * <b>为什么不用方法参数里的 {@code entityYaw}</b>：那个值是 {@code LivingEntity#getViewYRot}，
 * 对生物返回的是 {@code yHeadRot}（头部朝向，客户端插值出来的、和 yRot 未必一致）。
 * 本实体并不维护头部朝向，语义上也是“整个身体就是头部”，所以显式用 {@code yRotO → getYRot()} 插值。
 * <p>
 * <b>正反面</b>：{@code item/generated} 的模型由 {@code ItemModelGenerator} 生成 <b>SOUTH 与 NORTH 两个面片</b>
 * （背面 UV 已镜像），所以这是一张双面纸片，转过去看背面也是正着的贴图，不会出现“背面透明”。
 */
public class EnchantedOrigamiRenderer extends EntityRenderer<EnchantedOrigamiEntity> {
    /**
     * 贴图里“头”指向相对 +X 轴的角度（模型本地 XY 平面内，逆时针为正）。
     * <p>
     * <b>本贴图是 0°</b>：头在正右方。所以内层不需要额外的面内旋转——贴图 +X 直接当飞行方向、
     * +Y 直接当“上”，鹤身竖直、头朝前。
     * <p>
     * <b>踩过的坑</b>：第一版按“头在正上方”填了 90°，结果整只鹤被多转 90°、头竖直朝地面。
     * 换新贴图时按这张表填即可：头朝右 0°、头朝上 90°、头朝左 180°、头朝下 270°（-90°）。
     * 作用等价于 {@code ThrownGlisteringMelonKnifeRenderer#BLADE_ANGLE_DEGREES}。
     */
    private static final float HEAD_ANGLE_DEGREES = 0.0F;

    private final ItemRenderer itemRenderer;

    public EnchantedOrigamiRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(EnchantedOrigamiEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        // 与原版投掷物一致：刚出现的前 2 刻如果正贴在镜头上就不画，免得纸片糊脸
        if (entity.tickCount < 2
            && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) {
            return;
        }

        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        // 1) 外层：把纸片本地 +Z（内层旋转后即“前方”）转到实体自己的朝向。
        //    实体朝向（水平分量）是 (-sin yRot, ·, cos yRot)，而 Ry(θ)·(+Z) = (sin θ, ·, cos θ)，
        //    所以取 θ = -yRot。
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        //    俯仰用 +xRot（不是 -xRot）：xRot 为负表示爬升
        //    （updateFlight 里 xRot = -atan2(vy, 水平速度)），此时要让纸片“前方”向上抬、头朝上，
        //    而 Rx(+xRot)·(+Z) = (0, -sin xRot, cos xRot) 在 xRot < 0 时向上，方向正确。
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        // 2) 内层：先把贴图里头的方向转到 +X，再绕 Y 转到 +Z（供第 1 步对齐到实体朝向）。
        //    模型向量先被 ZP 旋转、再被 YP 旋转，与 ThrownGlisteringMelonKnifeRenderer 的写法一致。
        //    本贴图头在正右方 → HEAD_ANGLE_DEGREES = 0，这一步实际是恒等变换，保留是为了换贴图时能直接调。
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-HEAD_ANGLE_DEGREES));

        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        // 与原版 ThrownItemRenderer 一样保留父类行为（命名牌等）
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 物品模型在方块图集里，此处仅为满足 EntityRenderer 的抽象方法。 */
    @Override
    public ResourceLocation getTextureLocation(EnchantedOrigamiEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
