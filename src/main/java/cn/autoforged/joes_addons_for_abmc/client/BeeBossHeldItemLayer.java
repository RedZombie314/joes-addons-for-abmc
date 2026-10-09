package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染 BeeBoss 的蜂巢权杖（普通蜜蜂与 Beeper 均适用）。
 * <p>判定依据是<b>主手物品本身</b>（是否为 {@code bee_nest} 形态的权杖），而不是服务端的 BeeBoss 标志：
 * 标志存在 NeoForgeData 中（不参与同步，原因见 {@code BeeBossData} 类注释），而主手物品会随装备同步下发到客户端，
 * 因此客户端直接看装备即可，无需任何自定义同步数据。
 * <p>物品位置目前为按蜜蜂模型的初版定位（身体前方），后续可按需微调（嘴前偏移等）。
 */
public class BeeBossHeldItemLayer extends RenderLayer<Bee, BeeModel<Bee>> {

    private final ItemRenderer itemRenderer;

    public BeeBossHeldItemLayer(RenderLayerParent<Bee, BeeModel<Bee>> parent, ItemRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, Bee bee,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = bee.getMainHandItem();
        if (stack.isEmpty() || !BeehiveStaffHelper.isBeeStaff(stack)) {
            return;
        }
        poseStack.pushPose();
        // 方向已校准：+X = 蜜蜂左侧、+Y = 下方（视觉）。再左移 4px（+X 0.25）。
        // 保持向前（-Z 0.45）、顺时针 135°（Z 轴 -180°）。
        poseStack.translate(-0.25, 1.05, -0.45);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-180.0F));
        // 物品缩小为原来的 1/4：LivingEntityRenderer 渲染层前已把整体按蜜蜂 scale（5 倍）放大，
        // 这里直接乘 0.25，得到 5×1/4 = 1.25× 基准大小（相对 5 倍蜜蜂约为其 1/4，视觉上明显小于蜜蜂、不再等大同步）。
        poseStack.scale(0.25F, 0.25F, 0.25F);
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, bee.level(), bee.getId());
        poseStack.popPose();
    }
}