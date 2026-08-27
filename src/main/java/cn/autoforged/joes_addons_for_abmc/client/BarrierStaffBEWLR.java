package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.StaffBakedModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 权杖覆盖层的自定义渲染器（BEWLR），统一处理两种 billboard 覆盖层：
 * <ul>
 *   <li>屏障权杖：在权杖头部渲染始终面向玩家的屏障“粒子”。</li>
 *   <li>被“无效化”的权杖：在（空）权杖本体之上渲染始终面向玩家的蛛网覆盖层，覆盖权杖贴图。</li>
 * </ul>
 * 由于权杖物品的 getCustomRenderer() 固定返回本实例，而“屏障”与“无效化蛛网”两种状态都会
 * 使模型 isCustomRenderer()==true 进入本渲染路径，因此这里根据持有实体是否处于无效化来选择
 * 渲染屏障还是蛛网覆盖层。
 */
public class BarrierStaffBEWLR extends BlockEntityWithoutLevelRenderer {

    public static final BarrierStaffBEWLR INSTANCE = new BarrierStaffBEWLR();

    // 屏障粒子中心在 staff.json 模型坐标（0-16 像素）中的位置。
    // 注意：renderByItem 进入时 poseStack 已被 ItemRenderer 执行 translate(-0.5,-0.5,-0.5) 居中，
    // 模型 0-16 像素映射到 -0.5~0.5，故像素坐标 p 需换算为 (p - 8) / 16。
    // 相对上一版 (17, 30, 7) 叠加玩家导出偏移 (-1, -2, 9)，得到当前基准 (16, 28, 16)。
    private static final float CENTER_X = (16.0F - 8.0F) / 16.0F;
    private static final float CENTER_Y = (28.0F - 8.0F) / 16.0F;
    private static final float CENTER_Z = (16.0F - 8.0F) / 16.0F;
    // 屏障粒子的半边长（模型像素 4，换算为 -0.5~0.5 空间即为 4/16）
    private static final float HALF_SIZE = 4.0F / 16.0F;

    private static final ResourceLocation BARRIER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/item/barrier.png");
    private static final ResourceLocation COBWEB_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/cobweb.png");

    // 空权杖基础模型（烘焙时注入），被无效化时作为被蛛网覆盖的权杖本体。
    private BakedModel baseModel;

    private BarrierStaffBEWLR() {
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

        // 在进入 getModel 前先读取持有实体 id（getModel 会用 mc.player 重新解析而覆盖 ThreadLocal）。
        int holderId = StaffBakedModel.getCurrentHolderId();
        boolean nullified = holderId >= 0 && CobwebClientState.isNullified(holderId);

        // 1) 渲染权杖本体模型（模型坐标已由 ItemRenderer 应用了 display 变换）
        BakedModel model;
        if (nullified) {
            // 无效化：本体用空权杖（蛛网覆盖在其上），避免仍显示原方块贴图。
            model = baseModel != null ? baseModel : mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        } else {
            model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        }
        for (RenderType renderType : model.getRenderTypes(stack, false)) {
            mc.getItemRenderer().renderModelLists(model, stack, light, overlay, poseStack,
                buffer.getBuffer(renderType));
        }

        // 2) 在地面/手持/展示等上下文渲染始终面向玩家的覆盖层粒子
        poseStack.pushPose();
        poseStack.translate(CENTER_X, CENTER_Y, CENTER_Z);

        // 世界空间（落地/展示框/头盔/无上下文）与第一人称手持：通过“模型视矩阵 × 当前 poseStack”
        // 得到该四边形局部空间到视图空间的旋转，取其共轭即可把四边形对齐到视图空间（-Z 面向屏幕/
        // 相机），从而始终面向玩家。相机旋转会在矩阵乘法中相互抵消，避免对含相机旋转的大矩阵做
        // getNormalizedRotation 时因浮点误差在扭头/转身时产生帧差抽动。GUI 场景的 poseStack 已自带
        // 朝向，直接 -Z 绘制即可。
        if (needsCameraRotation(displayContext)) {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f combined = new Matrix4f(modelView).mul(poseStack.last().pose());
            // 从 combined 的 3x3 旋转部分提取面向相机的旋转。
            // 直接对含 30 倍缩放（背包预览 scale(30,30,-30)）的大矩阵调用 getNormalizedRotation 时，
            // 缩放会放大浮点误差，扭头/转身时提取的角度帧间抖动（抽搐）。这里先把旋转矩阵的
            // 三列分别归一化（旋转*缩放矩阵的列天然正交，归一化即精确的单位正交基），
            // 去掉缩放后再提取纯旋转，数值稳定。
            Vector3f c0 = new Vector3f(combined.m00(), combined.m10(), combined.m20()).normalize();
            Vector3f c1 = new Vector3f(combined.m01(), combined.m11(), combined.m21()).normalize();
            Vector3f c2 = new Vector3f(combined.m02(), combined.m12(), combined.m22()).normalize();
            // 背包预览的 poseStack 含反射缩放 scale(30,30,-30)，使基为左手系（det<0）。
            // 旋转提取假定右手系，遇到反射会得到“镜像”旋转，屏障无法面向相机。取反一列即可
            // 把左手系翻成右手系再提取，得到正确的面向相机旋转；反射仅作用于 z≠0 的坐标，
            // 对 z=0 的平面四边形无位置影响。
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

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(
            nullified ? COBWEB_TEXTURE : BARRIER_TEXTURE));
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

    // 派生到世界空间（落地/展示框/头盔/无上下文）与第一人称手持的展示上下文，其屏障四边形
    // 需要 billboard 使其始终面向相机（屏幕）。GUI 无需处理（poseStack 自带朝向）。
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