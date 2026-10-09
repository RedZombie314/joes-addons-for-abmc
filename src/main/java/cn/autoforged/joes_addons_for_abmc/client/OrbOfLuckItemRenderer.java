package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Orb of Luck 物品的通用渲染器（{@link BlockEntityWithoutLevelRenderer}）：把物品模型换成光球。
 *
 * <h2>为什么需要它</h2>
 * 物品模型只有"静态贴图"这一种表达能力，而我们要的是那个会呼吸的光球。把物品模型改成
 * {@code builtin/entity}（模型 json 里 {@code "parent": "builtin/entity"}）之后，原版就不再画模型，
 * 而是把渲染交给这里（{@code ItemRenderer#render} 里的 {@code getCustomRenderer().renderByItem(...)}），
 * 于是<b>所有场合</b>——第三人称手持、掉落在地上、展示框、戴在头上、物品栏图标——都能画成光球。
 * （第一人称另有 {@link OrbOfLuckHandRenderer}，因为那边还要额外把手臂画出来。）
 *
 * <h2>两种坐标空间</h2>
 * <ul>
 *   <li><b>GUI</b>：物品空间的 XY 平面本来就正对屏幕，直接在 {@code (0.5, 0.5, 0.5)} 画个面片就是图标。</li>
 *   <li><b>其它场合</b>：物品空间被显示变换转过（第三人称还要带上实体朝向），所以先把当前矩阵
 *       还原成"世界坐标 + 平移到物品中心"，再乘上相机朝向，画成 billboard，这样无论谁拿着、
 *       朝哪边都正对镜头。原版在调用本渲染器前后有 {@code pushPose/popPose} 包着，所以就地改写
 *       栈顶矩阵不会漏给调用方。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class OrbOfLuckItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** 世界里（第三人称/掉落物/展示框/头上）光球的半径（格）。 */
    private static final float WORLD_RADIUS = 0.22F;

    /** GUI 图标半径：GUI 坐标里 1 单位 = 1 个 16 像素槽位，0.30 ≈ 直径 9~10 像素的光球。 */
    private static final float GUI_RADIUS = 0.30F;

    /** 物品空间的中心（原版在这里会把模型平移 -0.5，所以中心是 0.5 而不是 0）。 */
    private static final Vector3f ITEM_CENTER = new Vector3f(0.5F, 0.5F, 0.5F);

    private static OrbOfLuckItemRenderer instance;

    private OrbOfLuckItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** 懒加载：构造时要拿 Minecraft 的几个管理器，必须等游戏初始化完成。 */
    public static OrbOfLuckItemRenderer get() {
        if (instance == null) {
            instance = new OrbOfLuckItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int combinedOverlay) {
        if (!OrbOfLuckRenderType.isReady()) {
            return;
        }

        float radius;
        if (displayContext == ItemDisplayContext.GUI) {
            // 物品栏/创造栏图标：物品空间的 XY 平面正对屏幕，直接画在物品中心。
            radius = GUI_RADIUS;
            poseStack.pushPose();
            poseStack.translate(ITEM_CENTER.x, ITEM_CENTER.y, ITEM_CENTER.z);
        } else {
            // 世界里的各种拿法：先把矩阵还原成"世界坐标 + 平移到物品中心"，再正对相机。
            Matrix4f pose = poseStack.last().pose();
            Vector3f worldCenter = pose.transformPosition(new Vector3f(ITEM_CENTER));
            pose.identity().translate(worldCenter);
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            radius = WORLD_RADIUS;
            poseStack.pushPose();
        }

        float half = radius * OrbOfLuckRenderer.QUAD_HALF_PER_RADIUS;
        float breath = OrbOfLuckEntity.breathAt(levelTime(), 0.0F);
        VertexConsumer consumer = buffer.getBuffer(OrbOfLuckRenderType.ORB_OF_LUCK);
        Matrix4f pose = poseStack.last().pose();
        vertex(consumer, pose, -half, -half, breath, 0.0F, 1.0F);
        vertex(consumer, pose, half, -half, breath, 1.0F, 1.0F);
        vertex(consumer, pose, half, half, breath, 1.0F, 0.0F);
        vertex(consumer, pose, -half, half, breath, 0.0F, 0.0F);

        poseStack.popPose();
    }

    /**
     * 呼吸相位用的游戏时间。
     * <p>
     * 这里没有 partialTick 可用（物品渲染不传），直接用整刻时间：呼吸周期 8 秒 = 160 刻，
     * 1 刻的相位误差约 2%，肉眼看不出来。
     */
    private static long levelTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y,
                               float breath, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
            .setColor(breath, breath, breath, 1.0F)
            .setUv(u, v);
    }
}
