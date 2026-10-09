package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.ExplosiveArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 爆炸之箭的实体渲染：暂时沿用原版箭的贴图。 */
@OnlyIn(Dist.CLIENT)
public class ExplosiveArrowRenderer extends ArrowRenderer<ExplosiveArrow> {
    private static final ResourceLocation ARROW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public ExplosiveArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosiveArrow entity) {
        return ARROW_TEXTURE;
    }
}
