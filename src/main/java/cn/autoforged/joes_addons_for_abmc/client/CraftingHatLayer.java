package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.item.CraftingTableHatItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * <b>工作台帽子戴在头上时的渲染层</b>：头部装备栏里是 {@link CraftingTableHatItem} 时，
 * 用<b>物品模型本身</b>（{@code models/item/crafting_table_hat.json}，即 Blockbench 导出的那个
 * "帽檐 + 工作台"3D 模型）画在头上。
 *
 * <h3>为什么要自己接一层</h3>
 * 原版对"头上戴的不是盔甲"的物品就是这么画的（{@code CustomHeadLayer}：锚到头部件 → {@code translateToHead}
 * → 用 {@code ItemDisplayContext.HEAD} 渲染），但那条分支<b>明确跳过了 {@code ArmorItem}</b>
 * （{@code CustomHeadLayer.java:103}：{@code !(item instanceof ArmorItem armoritem) || armoritem.getEquipmentSlot() != HEAD}），
 * 因为头盔本来就该由 {@code HumanoidArmorLayer} 用护甲层贴图画。工作台帽子虽然是个 {@code ArmorItem}
 * （不提供护甲值，纯粹为了占头部装备栏），但它的外观是<b>模型</b>而不是护甲层贴图，
 * 所以这里把原版那条被跳过的分支自己接上：<b>渲染步骤与 {@code CustomHeadLayer} 逐行一致</b>，
 * 只是不再跳过，并额外把物品模型换成"帽子模型"。
 *
 * <h3>位置由模型自己的 display.head 决定</h3>
 * 帽子摆在头上的位置/旋转/缩放，全部读模型 JSON 里的 {@code display.head}
 * （Blockbench 的 "Head" 显示槽就是干这个的）。所以位置不满意时<b>不需要改代码</b>：
 * 在 Blockbench 里调 Head 槽、重新导出 json + png 覆盖到
 * {@code assets/joes_addons_for_abmc/models/item/crafting_table_hat.json} 与
 * {@code assets/joes_addons_for_abmc/textures/item/crafting_hat.png} 即可。
 *
 * <h3>护甲层那一边</h3>
 * {@link cn.autoforged.joes_addons_for_abmc.item.ModArmorMaterials#CRAFTING_TABLE_HAT} 的贴图层已经换成
 * 本模组的<b>全透明</b>贴图（{@code textures/models/armor/crafting_table_hat_layer_1.png}），
 * 所以原版头盔那层什么都不画，头上只会看到这里的模型。若不这么做，会同时看到"皮革头盔 + 工作台"两顶帽子。
 *
 * <h3>亮度（{@code gui_light}）</h3>
 * 模型 JSON 里带 {@code "gui_light": "front"}（需求："物品弄亮一些"）。它决定 GUI 里用哪套光照：
 * <ul>
 *   <li>{@code front} → {@code GuiLight.FRONT} → {@code lightLikeBlock() == false} →
 *       {@code GuiGraphics#renderItem} 走 {@code Lighting.setupForFlatItems()}，即<b>正面平光</b>，
 *       各个面都按"朝着镜头"来打光，所以整体明显更亮（平面物品就是这套）；</li>
 *   <li>{@code side}（默认）→ 走 {@code setupFor3DItems()}，用两盏斜上方的平行光，
 *       顶面亮、侧面暗 —— 帽子这种"大顶 + 四面围裙"的模型看上去就偏暗。</li>
 * </ul>
 * 这个字段只影响 GUI/物品栏那一类渲染（唯一的消费点就是 {@code GuiGraphics.java:1288}），
 * 手上的、地上掉的、以及<b>戴在头上</b>的渲染亮度由各自的光照环境决定，不受它影响。
 * 所以以后若在 Blockbench 里重新导出模型，记得保留 GUI Light = Front。
 *
 * <p>覆盖范围与原版鞘翅/本模组翅膀一致：<b>玩家（两种手臂模型）与盔甲架</b>，
 * 注册见 {@code ClientEvents#addCraftingHatLayers}。
 */
@OnlyIn(Dist.CLIENT)
public class CraftingHatLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {

    public CraftingHatLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof CraftingTableHatItem)) {
            return;
        }

        poseStack.pushPose();
        // ① 锚到"头"部件的枢轴（跟着头部转动/低头）—— 与原版 CustomHeadLayer 一样
        this.getParentModel().getHead().translateAndRotate(poseStack);
        // ② 原版给"头上物品"的统一变换（下移 0.25、绕 Y 转 180°、缩放 0.625 并翻转 y/z）
        CustomHeadLayer.translateToHead(poseStack, false);
        // ③ 用 HEAD 上下文渲染物品模型：display.head 的位移/旋转/缩放会在这一步生效
        Minecraft.getInstance().getItemRenderer().renderStatic(head, ItemDisplayContext.HEAD, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }
}
