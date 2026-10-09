package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.client.model.LuckySelectorModel;
import cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 幸运方块选择器的渲染器。
 * <p>
 * 用 {@link MobRenderer}：模型是 Blockbench 导出的原版 {@code EntityModel}（自带骨骼），
 * 交给 MobRenderer 后朝向/命名牌/受击红闪都由原版处理，不需要自己摆姿态。
 * <p>
 * 阴影视半径给 {@code 0.0F}：选择框是个线框，投影会显得很脏。
 */
public class LuckySelectorRenderer extends MobRenderer<LuckySelectorEntity, LuckySelectorModel> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ModMain.MODID, "textures/entity/lucky_selector/outer.png");

    public LuckySelectorRenderer(EntityRendererProvider.Context context) {
        super(context, new LuckySelectorModel(context.bakeLayer(LuckySelectorModel.LAYER_LOCATION)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(LuckySelectorEntity entity) {
        return TEXTURE;
    }
}
