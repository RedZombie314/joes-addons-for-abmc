package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.client.OrbOfLuckRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 幸运核心（Orb of Luck）渲染器：画一个正对摄像机的正方形面片，衰减/呼吸/配色全部由 core shader
 * {@code joes_addons_for_abmc:orbofluck} 现算（不依赖任何贴图）。
 *
 * <h2>面片位置</h2>
 * 实体的碰撞箱是原版的"底面对齐坐标"（{@code y .. y + height}），而实体原点在箱子底面，
 * 所以这里先把面片整体上移 {@code height / 2}，让画出来的光球与碰撞箱同心。
 *
 * <h2>面片大小与碰撞箱的关系</h2>
 * 着色器里 {@code r = |(uv - 0.5) * SPAN|}，面片边缘中点对应 {@code r = SPAN/2 = QUAD_EDGE_R}。
 * 取 {@code r = VISUAL_EDGE_R} 那圈（alpha ≈ 0.1，肉眼看到的"球面"）正好落在碰撞箱半径上，于是：
 * <pre>
 *   面片半边长 Q = 碰撞箱半径 R × QUAD_EDGE_R / VISUAL_EDGE_R
 * </pre>
 * 光晕的衰减尾巴伸到碰撞箱外侧约 2 倍，所以站在球面上时外面还裹着一层淡光——这正是原着色器
 * "没有实体边界"的观感。想改成"光晕边界=碰撞箱"，把 VISUAL_EDGE_R 调大即可。
 */
@OnlyIn(Dist.CLIENT)
public class OrbOfLuckRenderer extends EntityRenderer<OrbOfLuckEntity> {

    /** "球面"落在 alpha ≈ 0.1 的那一圈上（数值来自 orbofluck.fsh 的衰减公式）。 */
    private static final float VISUAL_EDGE_R = 1.443F;

    /** 面片边缘中点对应的 r，必须与 orbofluck.fsh 里的 SPAN/2 保持一致。 */
    private static final float QUAD_EDGE_R = 3.0F;

    /** 面片半边长 / 碰撞箱半径 ≈ 2.079。 */
    public static final float QUAD_HALF_PER_RADIUS = QUAD_EDGE_R / VISUAL_EDGE_R;

    /** 本渲染层不使用贴图，但 EntityRenderer 要求返回一个非空位置。 */
    private static final ResourceLocation FALLBACK_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public OrbOfLuckRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(OrbOfLuckEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 附体阶段不画自己的光球：那颗光球由"玩家"身体渲染在头顶
        // （见 client/ShellHeadOrbLayer —— 挂在头部那一节的模型空间渲染层）
        if (entity.getOrbState() == OrbOfLuckEntity.OrbState.POSSESSING) {
            return;
        }

        // 资源重载完成前 shader 可能还是空的，直接跳过。
        if (!OrbOfLuckRenderType.isReady()) {
            return;
        }

        // 半径直接取碰撞箱宽度的一半：尺寸永远和判定一致，
        // 而且未来 AI 若通过 Attributes.SCALE 把核心改大改小，画面会自动跟上。
        float radius = entity.getBbWidth() * 0.5F;
        if (radius <= 0.01F) {
            return;
        }

        float half = radius * QUAD_HALF_PER_RADIUS;
        float breath = OrbOfLuckEntity.breathAt(entity.level().getGameTime(), partialTicks);

        poseStack.pushPose();
        // 碰撞箱底面对齐实体坐标，实体原点是箱底 → 光球中心要抬到箱子中间。
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        // 公告板：转到正对摄像机即可，世界尺寸由 half 决定（不做任何缩放）。
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f pose = poseStack.last().pose();

        VertexConsumer consumer = buffer.getBuffer(OrbOfLuckRenderType.ORB_OF_LUCK);
        // 顶点色 R 通道携带呼吸相位（0..1）——顶点色是 8bit 归一化通道，强度区间留在着色器里配。
        vertex(consumer, pose, -half, -half, breath, 0.0F, 1.0F);
        vertex(consumer, pose, half, -half, breath, 1.0F, 1.0F);
        vertex(consumer, pose, half, half, breath, 1.0F, 0.0F);
        vertex(consumer, pose, -half, half, breath, 0.0F, 0.0F);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float breath, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
            .setColor(breath, breath, breath, 1.0F)
            .setUv(u, v);
    }

    @Override
    public ResourceLocation getTextureLocation(OrbOfLuckEntity entity) {
        return FALLBACK_TEXTURE;
    }
}
