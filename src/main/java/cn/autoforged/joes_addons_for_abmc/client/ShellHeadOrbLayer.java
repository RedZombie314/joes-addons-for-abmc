package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 附体空壳头顶的幸运核心光球：<b>模型空间的渲染层</b>（用户指定）。
 *
 * <h2>为什么从"渲染器里事后补画"改成渲染层</h2>
 * 以前是 {@code PlayerShellRenderer#render} 在 {@code super.render} 之后自己算一个位置、
 * 沿<b>身体朝向</b>往头顶上摆一颗公告板。那样有两个硬伤：
 * <ol>
 *   <li>它只认身体 yaw，<b>不跟头</b>：头往哪看、身体什么姿势（重锤滑翔时整个人是躺平的、
 *       头在前方一米外）都跟它无关 —— 光球永远待在"脚底正上方"；</li>
 *   <li>"头顶"那个高度只能靠常量硬凑（碰撞箱 1.8 和模型头 1.875 是两个数，模型一改缩放就得重新配），
 *       滑翔时碰撞箱还会缩到 0.6，拿它当高度光球就会掉到腰上。</li>
 * </ol>
 * 渲染层跑在<b>模型空间</b>里（{@code LivingEntityRenderer#render} 已经铺好了"实体朝向 + 模型缩放 + 翻转"
 * 那一整套变换），于是只要把<b>头部那一节的位移与旋转</b>套上（{@code ModelPart#translateAndRotate}），
 * 光球就自动跟着头走了 —— 头看向哪、身体是站是躺，它都紧贴头顶，不需要任何"头顶高度"常量。
 *
 * <h2>画的时候为什么还要绕一圈</h2>
 * 光球在着色器里是一张<b>正对摄像机</b>的面片（{@code orbofluck}，见 {@link OrbOfLuckRenderer}），
 * 而层里的坐标系带着模型那套旋转与 {@code scale(-1,-1,1)}，直接在里面画面片会被带歪/镜像。
 * 所以这里：
 * <ol>
 *   <li>先在模型空间里把点挪到"头顶靠前"，<b>读出这一点在摄像机相对世界坐标里的位置</b>
 *       （{@code Matrix4f#transformPosition} 把原点变换过去，模型缩放/翻转/实体朝向全都自动算进去了）；</li>
 *   <li>再用"平移到该点 + 摄像机朝向"现拼一个干净的矩阵来画面片 —— 既贴身、又正对镜头，
 *       而且尺寸不受模型缩放影响（与 {@link OrbOfLuckEntity} 自己那颗球的画法完全一致）。</li>
 * </ol>
 *
 * <h2>模型空间的坐标方向（容易记反）</h2>
 * 模型在渲染时被 {@code scale(-1,-1,1)} 翻过，所以在<b>部件自己的局部坐标</b>里：
 * <b>-y 是上、-z 是前</b>（人形模型的头是个 8×8×8 的方块，枢轴在脖子 y=0、方块长到 y=-8）。
 */
@OnlyIn(Dist.CLIENT)
public class ShellHeadOrbLayer extends RenderLayer<PlayerShellEntity, PlayerModel<PlayerShellEntity>> {

    /** 光球半径（格）：与以前一致（世界空间尺寸，不受模型缩放影响）。 */
    private static final float HEAD_ORB_RADIUS = 0.30F;

    /** 光球比<b>模型头顶</b>再往上多少格：0.02（≈ 1/3 像素）—— 用户指定"紧贴"（见旧的实现说明）。 */
    private static final float HEAD_ORB_UP = 0.02F;

    /** 光球沿<b>头部朝向</b>前移多少格（模型空间，会随模型缩放一起缩）：用户指定"靠前"。 */
    private static final float HEAD_ORB_FORWARD = 0.18F;

    /**
     * 头部枢轴 → 头骨顶面的偏移（格，模型空间）：{@code -8 像素 / 16 = -0.5}。
     * <p>人形模型的头是 {@code addBox(-4, -8, -4, 8, 8, 8)}：枢轴（脖子）在 y=0，方块长到 y=-8，
     * 而模型空间 <b>-y 是上</b>，所以头顶就是 -0.5 格。
     */
    private static final float HEAD_TOP_IN_MODEL_SPACE = -8.0F / 16.0F;

    public ShellHeadOrbLayer(RenderLayerParent<PlayerShellEntity, PlayerModel<PlayerShellEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, PlayerShellEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!entity.isOrbAttached() || !OrbOfLuckRenderType.isReady()) {
            return;
        }

        // ① 模型空间里"贴着头部"：先套上头部枢轴的位移+旋转（含头部的偏航/俯仰），
        //    再挪到"头顶（-y 向上 0.5）稍微再上一点、沿头部朝向前移一点"。
        poseStack.pushPose();
        this.getParentModel().head.translateAndRotate(poseStack);
        poseStack.translate(0.0F, HEAD_TOP_IN_MODEL_SPACE - HEAD_ORB_UP, -HEAD_ORB_FORWARD);
        // ② 这一点在"摄像机相对世界坐标"里的位置。必须拷贝一份：出栈之后矩阵就被复用了。
        Matrix4f anchored = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        Vector3f center = anchored.transformPosition(new Vector3f());

        // ③ 公告板：只要"平移到该点 + 正对摄像机"，不带模型那套旋转/翻转，也没有缩放。
        Matrix4f pose = new Matrix4f()
            .translation(center.x, center.y, center.z)
            .rotate(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());

        float half = HEAD_ORB_RADIUS * OrbOfLuckRenderer.QUAD_HALF_PER_RADIUS;
        float breath = OrbOfLuckEntity.breathAt(entity.level().getGameTime(), partialTick);
        VertexConsumer consumer = buffer.getBuffer(OrbOfLuckRenderType.ORB_OF_LUCK);
        // 顶点色 R 通道携带呼吸相位（0..1），与核心本体那颗球同一套 shader 约定。
        vertex(consumer, pose, -half, -half, breath, 0.0F, 1.0F);
        vertex(consumer, pose, half, -half, breath, 1.0F, 1.0F);
        vertex(consumer, pose, half, half, breath, 1.0F, 0.0F);
        vertex(consumer, pose, -half, half, breath, 0.0F, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y,
                               float breath, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
            .setColor(breath, breath, breath, 1.0F)
            .setUv(u, v);
    }
}
