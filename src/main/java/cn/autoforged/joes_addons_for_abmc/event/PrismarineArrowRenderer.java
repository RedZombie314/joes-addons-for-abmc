package cn.autoforged.joes_addons_for_abmc.event;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.PrismarineArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PrismarineArrowRenderer extends ArrowRenderer<PrismarineArrow> {

    public static final ResourceLocation PRISMARINE_ARROW_LOCATION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/entity/prismarine_arrow.png");

    public PrismarineArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PrismarineArrow entity) {
        return PRISMARINE_ARROW_LOCATION;
    }
}
