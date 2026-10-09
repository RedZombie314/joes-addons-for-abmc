package cn.autoforged.joes_addons_for_abmc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * 给<b>骷髅马/僵尸马</b>补一个马凯渲染层。
 *
 * <p>原版把马凯画在 {@code HorseArmorLayer} 里，可那一层只挂在 {@code HorseRenderer} 上，
 * 而骷髅马走的是 {@code UndeadHorseRenderer}（没有这一层）—— 于是给骷髅马穿马凯是"穿了但看不见"。
 * 本模组附体召唤的骷髅马骑士会给马穿 30% 铁 / 30% 金马凯，所以需要把它画出来。
 *
 * <p>渲染代码与 {@code HorseArmorLayer} 等价，只是把泛型从 {@code Horse} 放宽到
 * {@link AbstractHorse}（原版那一层没法直接复用到骷髅马上，因为它的泛型写死成 {@code Horse}）。
 */
public class UndeadHorseArmorLayer extends RenderLayer<AbstractHorse, HorseModel<AbstractHorse>> {

    /** 马凯模型（所有马共用同一套 {@code HORSE_ARMOR} 模型层）。 */
    private final HorseModel<AbstractHorse> armorModel;

    public UndeadHorseArmorLayer(RenderLayerParent<AbstractHorse, HorseModel<AbstractHorse>> renderer,
                                 EntityModelSet modelSet) {
        super(renderer);
        this.armorModel = new HorseModel<>(modelSet.bakeLayer(ModelLayers.HORSE_ARMOR));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractHorse horse,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack stack = horse.getBodyArmorItem();
        if (!(stack.getItem() instanceof AnimalArmorItem armor)
            || armor.getBodyType() != AnimalArmorItem.BodyType.EQUESTRIAN) {
            return;
        }
        this.getParentModel().copyPropertiesTo(this.armorModel);
        this.armorModel.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTicks);
        this.armorModel.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        int color = stack.is(ItemTags.DYEABLE)
            ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536))
            : -1;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(armor.getTexture()));
        this.armorModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
    }

    /**
     * 骷髅马/僵尸马的渲染器：就是原版 {@code UndeadHorseRenderer} 加一层上面的马凯层。
     * <p>
     * 注册在 {@code EntityType.SKELETON_HORSE} 上（见 {@link ClientEvents}）—— 这会覆盖原版那一个。
     * 僵尸马保持原版不动。
     */
    public static class Renderer extends UndeadHorseRenderer {

        public Renderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
            super(context, ModelLayers.SKELETON_HORSE);
            this.addLayer(new UndeadHorseArmorLayer(this, context.getModelSet()));
        }
    }
}
