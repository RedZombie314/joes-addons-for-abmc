package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.WingsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>翅膀的渲染层</b>：胸甲栏里穿着 {@link WingsItem} 时，按鞘翅（{@code ElytraModel} 的网格）画出来，
 * 贴图是本模组的 {@code textures/entity/wings.png}（原图，未压缩）。
 *
 * <h3>角度动画（需求：正弦形状）</h3>
 * 原版鞘翅有<b>两套角度</b>：不滑翔时 {@code (xRot, zRot) = (π/12, -π/12)}，滑翔时
 * {@code (π/9, -π/2)}（数值取自原版 {@code ElytraModel#setupAnim}）。这里：
 * <ul>
 *   <li><b>在地面上</b>：按原版不滑翔的角度；潜行时用原版潜行姿态（{@code (2π/9, -π/4)} + 上移 3）；</li>
 *   <li><b>在空中</b>（飞行/滑翔中，即 {@code !onGround()}）：让两套角度之间按
 *       {@code blend = 0.5 - 0.5·cos(2π·t/周期)} 来回插值 —— 这就是一条标准正弦曲线（0→1→0），
 *       于是羽毛在两个角度之间平滑地"扇动"，周期见 {@link #ANIM_PERIOD_TICKS}。
 *       <p>用"是否离地"而不是"abilities.flying"判断，是为了让<b>其它玩家</b>也看得到动画：
 *       技能栏的 mayfly/flying 只同步给本人，离地状态是所有客户端都有的。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class WingsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** 翅膀贴图（实体贴图，按鞘翅模型 64×32 的 UV 布局绘制；这里用的是 10 倍分辨率的原图）。 */
    private static final ResourceLocation WINGS_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/entity/wings.png");

    /** 扇动一个来回的周期（刻）：40 刻 = 2 秒。 */
    private static final float ANIM_PERIOD_TICKS = 40.0F;

    /** 原版两套角度：不滑翔 / 滑翔（数值与 {@code ElytraModel#setupAnim} 一致）。 */
    private static final float IDLE_X_ROT = (float) (Math.PI / 12);
    private static final float IDLE_Z_ROT = (float) (-Math.PI / 12);
    private static final float GLIDE_X_ROT = (float) (Math.PI / 9);
    private static final float GLIDE_Z_ROT = (float) (-Math.PI / 2);
    /** 原版潜行姿态。 */
    private static final float SNEAK_X_ROT = (float) (Math.PI * 2.0 / 9.0);
    private static final float SNEAK_Z_ROT = (float) (-Math.PI / 4);
    private static final float SNEAK_Y_OFFSET = 3.0F;
    private static final float SNEAK_Y_ROT = 0.08726646F;

    private final WingsModel<T> wingsModel;

    public WingsLayer(RenderLayerParent<T, M> renderer, EntityModelSet modelSet) {
        super(renderer);
        // 直接复用原版鞘翅烘焙好的网格（部件名 left_wing / right_wing 相同）
        this.wingsModel = new WingsModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof WingsItem)) return;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        this.getParentModel().copyPropertiesTo(this.wingsModel);
        applyWingAngles(entity, partialTick);

        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffer,
            RenderType.armorCutoutNoCull(WINGS_TEXTURE), chest.hasFoil());
        this.wingsModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** 按"是否离地 / 是否潜行"决定两翼角度：离地时在两个角度之间按正弦来回。 */
    private void applyWingAngles(T entity, float partialTick) {
        if (entity.onGround()) {
            if (entity.isCrouching()) {
                this.wingsModel.setWingAngles(SNEAK_Y_OFFSET, SNEAK_X_ROT, SNEAK_Y_ROT, SNEAK_Z_ROT);
            } else {
                this.wingsModel.setWingAngles(0.0F, IDLE_X_ROT, 0.0F, IDLE_Z_ROT);
            }
            return;
        }
        // 正弦扇动：blend 从 0 平滑到 1 再回到 0
        double phase = (entity.tickCount + partialTick) / ANIM_PERIOD_TICKS * (Math.PI * 2.0);
        float blend = (float) (0.5 - 0.5 * Math.cos(phase));
        this.wingsModel.setWingAngles(0.0F,
            Mth.lerp(blend, IDLE_X_ROT, GLIDE_X_ROT),
            0.0F,
            Mth.lerp(blend, IDLE_Z_ROT, GLIDE_Z_ROT));
    }
}
