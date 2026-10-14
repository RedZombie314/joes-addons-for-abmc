package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.ExplosiveArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>爆炸之箭</b>的实体渲染：箭杆沿用原版箭的贴图，另外在<b>箭头（前端）</b>挂一个 TNT 的立体模型当弹头。
 *
 * <h2>为什么能挂上去（原版箭头渲染的几何）</h2>
 * 1.21.1 的 {@link ArrowRenderer} 已经不用 {@code ArrowModel} 了，而是直接写顶点，姿态是固定的三步：
 * <pre>
 *   mulPose(YP, yRot - 90)     // 偏航
 *   mulPose(ZP, xRot)          // 俯仰（注意是绕 Z，不是绕 X）
 *   mulPose(XP, 45) → scale(0.05625) → translate(-4, 0, 0)
 * </pre>
 * 其中 <b>ZP(+xRot)</b> 这一步的原版推导结果很关键（已按
 * {@code Projectile#shoot} 里 {@code yRot = atan2(v.x, v.z)}、
 * {@code xRot = atan2(v.y, 水平速度)} 逐项核对过）：
 * <pre>
 *   Ry(yRot-90) · Rz(xRot) · (+X) = (sin yRot·cos xRot, sin xRot, cos yRot·cos xRot) = 速度方向
 * </pre>
 * 也就是说：<b>只做前两步旋转之后，本地 +X 轴正好就是这支箭的飞行方向</b>，
 * 而箭杆在本地 X 上占 {@code [-8, +8]}（模型单位）。
 * 贴图 {@code arrow.png} 也印证了这一点：{@code u=0}（= 本地 x=-8）那头是白色羽毛，
 * {@code u=0.5}（= 本地 x=+8）那头是灰色箭头，所以<b>+X 一侧是箭头、-X 一侧是箭羽</b>。
 *
 * <h2>两端各在哪</h2>
 * 顶点还要再经过 {@code scale(0.05625)} 与 {@code translate(-4, 0, 0)}（平移量同样被缩放），
 * 于是模型单位 → 格 的换算是 {@code (x_model - 4) * 0.05625}：
 * <ul>
 *   <li>箭头尖端 x_model=+8 → <b>+0.225 格</b>（实体原点前方，也就是飞行方向那一头）；</li>
 *   <li>灰色箭头的根部 x_model≈+6 → <b>+0.1125 格</b>；</li>
 *   <li>羽片 x_model=-7 → <b>-0.61875 格</b>；</li>
 *   <li>箭杆末端 x_model=-8 → <b>-0.675 格</b>。</li>
 * </ul>
 * 所以箭身全长 0.9 格、粗 0.225 格。<b>TNT 当弹头，就挂在 +X 这一侧的箭头位置</b>——
 * 具体位置见 {@link #TNT_HEAD_OFFSET}。
 *
 * <h2>TNT 用哪个模型</h2>
 * 原版 {@code assets/minecraft/models/item/tnt.json} 的 {@code parent} 就是 {@code minecraft:block/tnt}
 * （继承 {@code block/cube_bottom_top} → {@code block/cube} → {@code block/block}），
 * 所以 <b>TNT 物品模型本身就是那个立体方块</b>，不需要自己搭模型或贴图。
 * 用物品渲染器画它还顺带兼容改了 TNT 外观的资源包/模组。
 * <p>
 * 用 {@link ItemDisplayContext#FIXED}（物品展示框那个上下文）而不是 {@code GROUND}：
 * 在原版 {@code block/block.json} 里 {@code fixed} 是纯 {@code scale 0.5}、不平移不旋转，
 * 配合 {@code ItemRenderer} 内部那句 {@code translate(-0.5, -0.5, -0.5)}，
 * 画出来就是<b>以姿态原点为中心、棱长 0.5 格</b>的方块；{@code ground} 则是 0.25 倍并抬高 3/16 格，
 * 还得自己补位移，麻烦且容易歪。这里再乘一个自己的缩放系数把 0.5 调到想要的大小。
 *
 * <h2>和箭一起转</h2>
 * TNT 的姿态完全复刻箭杆那两步旋转，所以它是"绑在箭上"的：箭怎么翻滚俯冲，炸弹跟着怎么转。
 * 因此当箭插在方块上、箭杆还在 {@code shakeTime} 抖动时，TNT 不跟着抖（原版那步抖动没有复刻）——
 * 视觉上反而更稳，不会连带抖成一团。
 */
@OnlyIn(Dist.CLIENT)
public class ExplosiveArrowRenderer extends ArrowRenderer<ExplosiveArrow> {
    /** 箭杆贴图：与本模组 6.x 一直以来的表现一致，仍是原版箭。 */
    private static final ResourceLocation ARROW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    /**
     * 挂在<b>箭头</b>上的 TNT 物品（原版 {@code item/tnt} 模型的 parent 是 {@code block/tnt}，即立体方块）。
     * 做成实例字段而不是静态字段：只在本渲染器构造时建一次，避免静态 {@code ItemStack} 被别处误改，
     * 也避免每帧新建（箭多的时候会白白制造垃圾）。
     */
    private final ItemStack tntStack = new ItemStack(Items.TNT);

    private final ItemRenderer itemRenderer;

    /**
     * 额外的统一缩放。{@link ItemDisplayContext#FIXED} 下 TNT 本来就是 0.5 格见方，这里再乘 0.65，
     * 最终 <b>0.325 格</b>见方：比 0.225 格粗的箭杆大一圈，一眼能认出是 TNT，
     * 又只占箭身 0.9 格的三分之一左右，不至于把箭杆整个吃掉。嫌大改小、嫌小改大，只动这一个数。
     */
    private static final float TNT_SCALE = 0.65F;

    /**
     * TNT 中心沿本地 X 轴的位置（<b>正 = 往箭头/飞行方向那一头</b>，负 = 往箭羽那一头）。
     * <p>
     * 取 +0.18：TNT 半棱长 0.1625 格，于是它占 {@code [+0.0175, +0.3425]} ——
     * 把整个灰色箭头（+0.1125 ~ +0.225）连同前面一小截箭杆一起包住，
     * 前脸比箭尖（+0.225）再探出约 0.12 格，看起来就是"箭头上顶着一包炸药当弹头"。
     * <p>
     * 想让炸弹整个挂在箭尖之外（完全不挡箭杆）就改成 +0.4 左右；想往后抱一点就改到 +0.1；
     * 想改回挂在箭羽那头，填负值即可（-0.58 就是原来那一版）。
     */
    private static final double TNT_HEAD_OFFSET = 0.18D;

    public ExplosiveArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosiveArrow entity) {
        return ARROW_TEXTURE;
    }

    /**
     * 先按原版画箭杆，再在同一个坐标系里补一个箭头炸弹。
     * <p>
     * {@code super.render} 内部自会 {@code pushPose/popPose}，返回时姿态栈又回到"已平移到实体位置"的干净状态，
     * 所以这里可以从头摆自己的姿态，不用去猜父类留下了什么。
     */
    @Override
    public void render(ExplosiveArrow entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        renderHeadTnt(entity, partialTick, poseStack, buffer, packedLight);
    }

    /** 在箭头处画 TNT：姿态与 {@link ArrowRenderer} 画箭杆的前两步完全一致，之后沿 +X 走到箭头并缩放。 */
    private void renderHeadTnt(ExplosiveArrow entity, float partialTick,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // 与 ArrowRenderer 同款：偏航 + 俯仰。做完这两步，本地 +X 就是飞行方向（箭头那头）、-X 是箭羽。
        poseStack.mulPose(Axis.YP.rotationDegrees(
            Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
            Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        // 沿 +X 走到箭头位置，再整体缩放（先平移后缩放 = 平移量按格算，不受缩放影响，正是我们要的）。
        poseStack.translate(TNT_HEAD_OFFSET, 0.0D, 0.0D);
        poseStack.scale(TNT_SCALE, TNT_SCALE, TNT_SCALE);
        this.itemRenderer.renderStatic(this.tntStack, ItemDisplayContext.FIXED, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }
}
