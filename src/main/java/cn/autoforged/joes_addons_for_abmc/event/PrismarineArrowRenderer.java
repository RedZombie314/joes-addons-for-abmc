package cn.autoforged.joes_addons_for_abmc.event;

import cn.autoforged.joes_addons_for_abmc.entity.PrismarineArrow;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>海晶箭</b>的投射物渲染：直接渲染物品模型本身，并让箭尖始终对着飞行方向。
 *
 * <h2>为什么原来是一支"看不见的箭"</h2>
 * 以前这里继承的是原版 {@code ArrowRenderer}，只把 {@code getTextureLocation} 换成了
 * {@code textures/entity/prismarine_arrow.png}。问题在于原版 {@link net.minecraft.client.renderer.entity.ArrowRenderer}
 * 早在 1.21.1 就不再使用 {@code ArrowModel} 了，而是<b>硬编码顶点 + 硬编码 UV</b>，
 * 只从贴图上取两个固定区域（整图归一化坐标）：
 * <ul>
 *   <li>箭杆：{@code u ∈ [0, 0.5]}，{@code v ∈ [0, 0.15625]}；</li>
 *   <li>箭羽：{@code u ∈ [0, 0.15625]}，{@code v ∈ [0.15625, 0.3125]}。</li>
 * </ul>
 * 也就是"32×32 贴图左上角那条 16×5 的箭杆长条 + 它下面 5×5 的羽片"。
 * 而本模组的 {@code textures/entity/prismarine_arrow.png} 是一张 <b>320×320、把整支箭按对角线画满整张画布</b>的图，
 * 上述两个区域在这张图里<b>一个不透明像素都没有</b>（实测覆盖率 0.0%），
 * 于是箭在游戏里整个是隐形的 —— 这就是"模型贴图不渲染"的真正原因：
 * 不是贴图丢了，而是<b>图文排版和原版箭的 UV 约定对不上</b>，取到的全是透明区。
 *
 * <h2>现在怎么画（与三叉戟同款思路）</h2>
 * 不再用"专门给实体画一张贴图 + 顶点硬编码"那套，而是像原版三叉戟那样
 * <b>把模型本体画出来</b>：这里直接渲染 {@code joes_addons_for_abmc:prismarine_arrow} 这件物品的模型
 * （{@code item/generated}，两面贴图 + 1 像素厚度的立体薄片），
 * 于是"拿在手上 / 掉在地上 / 飞在空中"看到的是同一个东西，也不再有第二套贴图需要维护。
 * <p>
 * 做法与 {@code ThrownGlisteringMelonKnifeRenderer} 同源（那边是"刀尖对齐飞行方向"），连朝向推导都一致：
 * <ol>
 *   <li>外层把模型本地 <b>+Z</b> 转到飞行方向（{@code Ry(yaw) · Rx(-pitch)}）；</li>
 *   <li>内层先把贴图里<b>箭尖所在的对角线</b>转到 +X（{@link #ARROW_ANGLE_DEGREES}），再绕 Y 转到 +Z 交给外层。</li>
 * </ol>
 * 这样箭尖朝前、箭羽在后；整支箭绕自身长轴的滚转固定为<b>箭身竖直</b>
 * （模型面片所在平面同时包含"飞行方向"和"世界上方"，与三叉戟的刃面朝向一致），
 * 于是从侧面能看全整支箭，从正前／正后方看则收成一条线。
 *
 * <h2>尺寸</h2>
 * 渲染缩放 = {@link #REFERENCE_PIXELS} / 贴图宽度，贴图宽度是运行时从物品模型实际用的那张图读出来的，
 * 所以以后换任何分辨率的贴图都会自动按比例换算，不需要改代码。
 * 当前贴图宽 320 px、基准 480 → 缩放 <b>1.5</b>，整支箭约 <b>1.5 格</b>长
 * （与物品模型显示用的 1.5 倍同一档，原版箭实体是 0.9 格）。
 */
@OnlyIn(Dist.CLIENT)
public class PrismarineArrowRenderer extends EntityRenderer<PrismarineArrow> {

    /**
     * 贴图里<b>箭尖方向</b>相对 +X 轴的角度（模型本地 XY 平面内，逆时针为正）。
     * <p>
     * 本贴图是"左下箭羽、右上箭头"的对角线构图，用不透明像素做主成分分析（PCA）量出来的实际轴向是
     * <b>42.5°</b>（不是想当然的 45°）。换构图时按同一方法重新量一下：
     * 沿该方向的投影极值就是箭的全长，投影质量更大的一头是箭头。
     */
    private static final float ARROW_ANGLE_DEGREES = 42.5F;

    /**
     * 缩放基准：{@code 渲染缩放 = REFERENCE_PIXELS / 贴图宽度}。
     * <p>
     * 当前贴图宽 320 px，取 <b>720</b> → 缩放 <b>2.25</b>，贴图里箭的全长约画布 1.008 倍宽，
     * 所以画出来约 <b>2.27 格</b>长（原版箭实体是 0.9 格）。
     * <p>
     * 换算表（贴图宽 320 时）：320 → 1.0 格、480 → 1.5 格、<b>720 → 2.27 格</b>、960 → 3.0 格。
     * 只影响<b>飞行中/插在方块上的这支箭</b>，物品栏图标与拿在手上的大小完全不受影响
     * （那是 {@code models/item/prismarine_arrow.json} 里的 {@code display} 决定的，这里不碰）。
     */
    private static final float REFERENCE_PIXELS = 720.0F;

    /**
     * {@link #textureScale()} 读不到贴图宽度时的兜底宽度（= 当前贴图的真实宽度 320）。
     * <p>
     * 兜底必须给"真实宽度"而不是 1.0：早期版本返回 1.0，于是查表一旦失败，
     * 缩放会静默变成 1.0 —— 正好等于最初那一版的取值，改 {@link #REFERENCE_PIXELS} 就"完全看不出变化"，
     * 排查起来非常费劲。给真实宽度意味着<b>无论查表成不成功，缩放都等于设计值</b>。
     */
    private static final float FALLBACK_TEXTURE_WIDTH = 320.0F;

    private final ItemRenderer itemRenderer;

    /**
     * 要渲染的物品模型。做成实例字段而不是每帧 new：只在本渲染器构造时建一次，
     * 也避免静态 {@code ItemStack} 被别处误改。
     */
    private final ItemStack arrowStack = new ItemStack(ModItems.PRISMARINE_ARROW.get());

    public PrismarineArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PrismarineArrow entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 与原版投射物一致：刚出手的前 2 刻如果正贴在镜头上就不画，免得一支箭糊脸
        if (entity.tickCount < 2
            && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) {
            return;
        }

        poseStack.pushPose();
        // 1) 最外层：把模型本地 +Z 转到飞行方向。
        //    无速度时（插在方块上、或刚生成还没动）退回实体自己的 yRot/xRot，保证始终有一个稳定姿态 ——
        //    这也让插在地上的箭保持它落地那一刻的朝向。
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
        // 2) 内层：先把贴图里的箭尖对角线转到 +X，再绕 Y 转到 +Z（供第 1 步对齐到飞行方向）。
        //    模型向量先被 ZP 旋转、再被 YP 旋转，与 ThrownGlisteringMelonKnifeRenderer 的写法一致。
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-ARROW_ANGLE_DEGREES));

        float scale = textureScale();
        poseStack.scale(scale, scale, scale);

        // ItemDisplayContext.NONE：不套任何 display 变换（item/generated 里没有 none 这项），
        // 模型就是 1×1、以原点为中心的一块薄片，尺寸完全由上面的 scale 决定。
        this.itemRenderer.renderStatic(this.arrowStack, ItemDisplayContext.NONE, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        // 与原版一样保留父类行为（命名牌等）
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 渲染缩放 = {@link #REFERENCE_PIXELS} / 该物品模型实际使用的贴图宽度。
     * <p>宽度是运行时从物品模型（{@code layer0} 对应的 sprite）读出来的，所以贴图换成任何分辨率都会自动换算；
     * 读不到时退回 {@link #FALLBACK_TEXTURE_WIDTH}（真实宽度），保证缩放永远是设计值而不是 1.0。
     */
    private float textureScale() {
        var model = this.itemRenderer.getItemModelShaper().getItemModel(this.arrowStack);
        if (model != null) {
            var sprite = model.getParticleIcon();
            if (sprite != null) {
                int width = sprite.contents().width();
                if (width > 0) {
                    return REFERENCE_PIXELS / (float) width;
                }
            }
        }
        return REFERENCE_PIXELS / FALLBACK_TEXTURE_WIDTH;
    }

    /**
     * 本刻的飞行方向：优先用前后两刻的位置差（比 {@code deltaMovement} 更贴近真实轨迹）。
     * <p>插在方块上的箭位置不再变化、速度也被清零，两个来源都退化 → 返回 null，由调用方走实体朝向。
     */
    private static Vec3 flightDirection(PrismarineArrow entity) {
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
        if (lengthSqr < 1.0E-8) {
            return null;
        }
        double length = Math.sqrt(lengthSqr);
        return new Vec3(dx / length, dy / length, dz / length);
    }

    /**
     * 物品模型用的是方块图集，这里仅为满足 {@link EntityRenderer} 的抽象方法。
     * <p>{@code textures/entity/prismarine_arrow.png} 现在已不再被引用（那张图是按 16×16 物品图标画的，
     * 排版和原版箭实体的 UV 约定对不上），留着只是备份。
     */
    @Override
    public ResourceLocation getTextureLocation(PrismarineArrow entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
