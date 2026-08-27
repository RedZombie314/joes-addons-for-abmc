package cn.autoforged.joes_addons_for_abmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 被“无效化”权杖的渲染器（BEWLR）：渲染空权杖模型，并在权杖模型贴图之上
 * 绘制一个始终面向玩家的蛛网 billboard，与屏障权杖的实现思路一致。
 *
 * 仅在权杖被无效化时（StaffBakedModel 返回对应自定义渲染模型）被调用。
 */
public class CobwebStaffBEWLR extends BlockEntityWithoutLevelRenderer {

    public static final CobwebStaffBEWLR INSTANCE = new CobwebStaffBEWLR();

    // 空权杖在 staff.json 模型坐标（0-16 像素）中头部所在区域，取其中心作为蛛网覆盖层位置。
    // 进入 renderByItem 时 poseStack 已居中到 -0.5~0.5，故像素坐标 p 换算为 (p - 8) / 16。
    private static final float CENTER_X = (8.0F - 8.0F) / 16.0F;
    private static final float CENTER_Y = (21.0F - 8.0F) / 16.0F;
    private static final float CENTER_Z = (8.0F - 8.0F) / 16.0F;
    private static final float HALF_SIZE = 0.28F;

    private static final ResourceLocation COBWEB_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/cobweb.png");

    // 空权杖基础模型（在 StaffBakedModel 烘焙时注入），nullified 状态渲染为其 + 蛛网覆盖层。
    private BakedModel baseModel;

    private CobwebStaffBEWLR() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels());
    }

    /** 烘焙空权杖模型时注入基础模型引用。 */
    public void setBaseModel(BakedModel baseModel) {
        this.baseModel = baseModel;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();

        // 渲染空权杖本体模型（覆盖层永远绘制在其纹理之上）
        BakedModel model = baseModel != null ? baseModel : mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        for (RenderType renderType : model.getRenderTypes(stack, false)) {
            mc.getItemRenderer().renderModelLists(model, stack, light, overlay, poseStack,
                buffer.getBuffer(renderType));
        }

        // 在权杖头部位置绘制始终面向玩家的蛛网 billboard
        poseStack.pushPose();
        poseStack.translate(CENTER_X, CENTER_Y, CENTER_Z);

        if (needsCameraRotation(displayContext)) {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f combined = new Matrix4f(modelView).mul(poseStack.last().pose());
            Vector3f c0 = new Vector3f(combined.m00(), combined.m10(), combined.m20()).normalize();
            Vector3f c1 = new Vector3f(combined.m01(), combined.m11(), combined.m21()).normalize();
            Vector3f c2 = new Vector3f(combined.m02(), combined.m12(), combined.m22()).normalize();
            float det = c0.x * (c1.y * c2.z - c1.z * c2.y)
                      - c0.y * (c1.x * c2.z - c1.z * c2.x)
                      + c0.z * (c1.x * c2.y - c1.y * c2.x);
            if (det < 0.0F) {
                c2.mul(-1.0F);
            }
            Matrix3f basis = new Matrix3f(c0.x, c1.x, c2.x, c0.y, c1.y, c2.y, c0.z, c1.z, c2.z);
            Quaternionf billboard = new Quaternionf();
            basis.getNormalizedRotation(billboard);
            billboard.conjugate();
            poseStack.mulPose(billboard);
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(COBWEB_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        float x1 = -HALF_SIZE, x2 = HALF_SIZE;
        float y1 = -HALF_SIZE, y2 = HALF_SIZE;
        consumer.addVertex(matrix, x1, y1, 0).setColor(255, 255, 255, 255).setUv(0, 1)
            .setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x1, y2, 0).setColor(255, 255, 255, 255).setUv(0, 0)
            .setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y2, 0).setColor(255, 255, 255, 255).setUv(1, 0)
            .setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y1, 0).setColor(255, 255, 255, 255).setUv(1, 1)
            .setOverlay(overlay).setLight(light).setNormal(0, 0, 1);

        poseStack.popPose();
    }

    private static boolean needsCameraRotation(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.NONE
            || ctx == ItemDisplayContext.GROUND
            || ctx == ItemDisplayContext.HEAD
            || ctx == ItemDisplayContext.FIXED
            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }
}