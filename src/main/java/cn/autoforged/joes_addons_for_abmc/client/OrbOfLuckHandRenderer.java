package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckRenderer;
import cn.autoforged.joes_addons_for_abmc.item.OrbOfLuckItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Orb of Luck 的第一人称渲染：<b>手臂 + 手臂末端的光球</b>。
 *
 * <h2>为什么要拦下原版</h2>
 * 原版 {@code ItemInHandRenderer#renderArmWithItem} 只在 {@code stack.isEmpty()} 时画手臂
 * （也就是空手才看得到手臂），手上拿着东西时只画物品模型。要让"拿着核心也看得见手臂"，
 * 就只能在这个事件里把原版这一手的渲染整手取消，再按空手的方式自己补画。
 *
 * <h2>画法</h2>
 * <ol>
 *   <li><b>手臂</b>：{@link #renderArm} 是原版 {@code ItemInHandRenderer#renderPlayerArm} 的等价实现
 *       （原方法私有，这里连变换一起搬过来，免得为了它去改 accesstransformer 触发整包重打），
 *       姿态与原版空手完全一致，所以走路/挥动该有的动作都会自动带上。</li>
 *   <li><b>光球</b>：{@link #handPosition} 按"物品在手上"的变换链（挥动位移 → 手臂位置 → 攻击挥动）
 *       算出<b>手在相机空间的位置</b>，随后只取位置、丢掉旋转，在相机空间里画一个平铺在 XY 平面的面片——
 *       这样光球永远正对屏幕保持圆形，位置却跟着手走（挥动时会画出一段弧线）。</li>
 * </ol>
 * 手部渲染所在的坐标空间就是相机空间（原版用一次"乘上投影矩阵的逆"把世界旋转抵消掉了），
 * 因此 XY 平面面片天然正对摄像机，不需要再额外做 billboard 旋转。
 *
 * <h2>位置标定（1080p、手部空间固定 FOV 70°）</h2>
 * <pre>
 *   原版手/物品位置 (0.56, -0.52, -0.72)  ->  屏幕 (600, 1097)   几乎压在下边缘
 *   拳心           (0.671, -0.584, -1.072) ->  屏幕 (483, 960)
 *   指尖           (0.712, -0.525, -1.174) ->  屏幕 (468, 885)
 *   肩 -> 指尖方向 = (0.334, 0.47, -0.817)，6 模型像素 = 0.375 格
 * </pre>
 * 所以 {@link #TIP_OFFSET_PIXELS} 默认 6：把光球从下边缘挪到手的位置。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public final class OrbOfLuckHandRenderer {

    /**
     * 手里那颗光球的半径（手部空间单位 = 格）。
     * <p>
     * 比世界里实体的 {@link OrbOfLuckEntity#RADIUS}（0.40）小得多：手离相机只有 0.72 格，
     * 同样的世界半径放到手上会糊住半个屏幕。0.12 ≈ 拳头大小（手臂模型截面 4 像素 = 0.25 格）。
     */
    private static final float IN_HAND_RADIUS = 0.12F;

    /**
     * 光球沿手臂朝指尖方向的额外偏移，单位：<b>模型像素</b>（1 像素 = 1/16 格，和手臂模型同尺度）。
     * <p>
     * 0 = 停在原版"手/物品"的位置；正值沿手臂往指尖挪，负值往手腕方向退。
     * 6 像素 ≈ 0.375 格：这一步能让光球从屏幕下边缘升到手的位置（见类注释里的实测坐标）。
     */
    private static final float TIP_OFFSET_PIXELS = 6.0F;

    /** 原版手/物品到摄像机的距离（格），用来把半径换算成"屏幕上看起来一样大"。 */
    private static final float HAND_DEPTH_REFERENCE = 0.72F;

    private OrbOfLuckHandRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ItemStack stack = event.getItemStack();
        if (player == null || !(stack.getItem() instanceof OrbOfLuckItem)) {
            return;
        }

        // 原版会画物品模型；这里整个接管（手臂 + 光球）。
        event.setCanceled(true);

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
            ? player.getMainArm()
            : player.getMainArm().getOpposite();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();

        // ---------- 1. 手臂 ----------
        if (!player.isInvisible()) {
            poseStack.pushPose();
            renderArm(poseStack, buffer, event.getPackedLight(),
                event.getEquipProgress(), event.getSwingProgress(), arm, player);
            poseStack.popPose();
        }

        // ---------- 2. 光球 ----------
        // 先取手的位置，再沿手臂轴向指尖方向挪 TIP_OFFSET_PIXELS 个模型像素。
        Vector3f handOffset = handOffset(arm, event.getSwingProgress(), event.getEquipProgress());
        handOffset.fma(TIP_OFFSET_PIXELS / 16.0F, armTipDirection(arm, event.getSwingProgress()));

        // 沿手臂往前挪会让光球离摄像机更远、屏幕上更小；这里按距离等比补偿，保证屏幕上看到的大小不变。
        float radius = IN_HAND_RADIUS * (Math.abs(handOffset.z) / HAND_DEPTH_REFERENCE);

        poseStack.pushPose();
        // 只平移、不旋转：光球保持正对屏幕（圆的），位置跟着手走。
        poseStack.translate(handOffset.x, handOffset.y, handOffset.z);

        float half = radius * OrbOfLuckRenderer.QUAD_HALF_PER_RADIUS;
        float breath = OrbOfLuckEntity.breathAt(player.level().getGameTime(), event.getPartialTick());
        VertexConsumer consumer = buffer.getBuffer(OrbOfLuckRenderType.ORB_OF_LUCK_HAND);
        Matrix4f pose = poseStack.last().pose();
        vertex(consumer, pose, -half, -half, breath, 0.0F, 1.0F);
        vertex(consumer, pose, half, -half, breath, 1.0F, 1.0F);
        vertex(consumer, pose, half, half, breath, 1.0F, 0.0F);
        vertex(consumer, pose, -half, half, breath, 0.0F, 0.0F);
        poseStack.popPose();
    }

    /**
     * "沿手臂指向指尖"的单位方向（手部空间）。
     * <p>
     * 手臂模型（{@code PlayerModel} 的 right_arm）在自身坐标系里从肩往指尖是 +Y 方向，
     * 所以照抄 {@link #renderArm} 的整条旋转链（含挥动项，这样攻击挥臂时光球会跟着指向指尖），
     * 把局部 +Y 变换过来即可。链里的平移不影响方向，不用管。
     */
    private static Vector3f armTipDirection(HumanoidArm arm, float swingProgress) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(swingProgress);
        float f5 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        Matrix4f rotation = new Matrix4f();
        rotation.rotate(Axis.YP.rotationDegrees(f * 45.0F));
        rotation.rotate(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        rotation.rotate(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        rotation.rotate(Axis.ZP.rotationDegrees(f * 120.0F));
        rotation.rotate(Axis.XP.rotationDegrees(200.0F));
        rotation.rotate(Axis.YP.rotationDegrees(f * -135.0F));
        return rotation.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
    }

    /**
     * 原版 {@code ItemInHandRenderer#renderPlayerArm} 的等价实现（该方法私有，这里原样搬过来）。
     * 变换顺序/常量与原版逐行对应，保证空手手臂的姿态、走路摆动完全一致。
     */
    private static void renderArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  float equippedProgress, float swingProgress, HumanoidArm side, LocalPlayer player) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(swingProgress);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * (float) (Math.PI * 2));
        float f4 = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equippedProgress * -0.6F, f4 + -0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        poseStack.translate(f * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        poseStack.translate(f * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance()
            .getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(poseStack, buffer, packedLight, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, packedLight, player);
        }
    }

    /**
     * 求"手相对于相机空间的偏移"：原版 {@code renderArmWithItem} 在"未使用物品"状态下施加的那串变换里，
     * 只有两次平移会改变原点位置（挥动位移 + {@code applyItemArmTransform}），后面的攻击挥动全是绕原点旋转，
     * <b>不会移动原点</b>，所以这里直接把这几个平移加起来即可。
     * <p>
     * 注意别用 {@code poseStack.last().pose()} 去取原点：手部空间的根部带着一次"乘投影矩阵的逆"
     * （GameRenderer#renderItemInHand），那个累积矩阵里的平移再做一次 translate 会把光球推出画面。
     */
    private static Vector3f handOffset(HumanoidArm arm, float swingProgress, float equippedProgress) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // 挥动位移（原版 renderArmWithItem 里 else 分支的前三行）
        float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        float f1 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
        float f2 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        // 挥动位移 + 手臂位置（原版 applyItemArmTransform：side * 0.56, -0.52 + equip * -0.6, -0.72）
        return new Vector3f(
            side * f + side * 0.56F,
            f1 - 0.52F + equippedProgress * -0.6F,
            f2 - 0.72F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y,
                               float breath, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
            .setColor(breath, breath, breath, 1.0F)
            .setUv(u, v);
    }
}
