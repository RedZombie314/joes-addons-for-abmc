package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.BeeperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Bee;

/**
 * 苦力蜂（Beeper）渲染器：使用原版蜜蜂模型，贴图保持普通 5 态切换（不额外改动翅膀贴图）。
 * 自爆时：
 * - 通过 {@link #getWhiteOverlayProgress} 驱动 LivingEntityRenderer 自带的白色闪烁叠加层（类苦力怕闪光）；
 * - 在 render() 里按引燃进度对整体做轻微膨胀。
 */
public class BeeperRenderer extends BeeRenderer {

    private static final ResourceLocation BEEPER =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "textures/entity/beeper/beeper.png");
    private static final ResourceLocation BEEPER_ANGRY =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "textures/entity/beeper/beeper_angry.png");
    private static final ResourceLocation BEEPER_ANGRY_NECTAR =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "textures/entity/beeper/beeper_angry_nectar.png");
    private static final ResourceLocation BEEPER_NECTAR =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "textures/entity/beeper/beeper_nectar.png");

    public BeeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 手持物品渲染层（BeeBoss）由 ClientEvents 的 AddLayers 通用注册，Beeper 作为 BeeRenderer 一并获得
    }

    /** 贴图切换：普通/花粉；愤怒贴图只在即将自爆（swell&gt;0）时出现（沾花粉则用愤怒花粉贴图）。 */
    @Override
    public ResourceLocation getTextureLocation(Bee bee) {
        if (bee instanceof BeeperEntity b && b.getSwell() > 0) {
            return bee.hasNectar() ? BEEPER_ANGRY_NECTAR : BEEPER_ANGRY;
        }
        return bee.hasNectar() ? BEEPER_NECTAR : BEEPER;
    }

    /** 引燃时按进度返回白色闪烁值（0..1），驱动 LivingEntityRenderer 自带的白色闪光叠加层。
     *  白度 = 基础白(0.35) + 进度白(0.65*k)，再乘剧烈闪烁因子，使刚引燃即明显泛白、临近爆炸接近全白。 */
    @Override
    protected float getWhiteOverlayProgress(Bee entity, float partialTick) {
        if (entity instanceof BeeperEntity b && b.getSwell() > 0) {
            float k = Math.min(1.0F, b.getSwell() / (float) BeeperEntity.MAX_SWELL);
            float flicker = 0.75F + 0.25F * Mth.sin(entity.tickCount * 1.4F);
            return Mth.clamp((0.35F + 0.65F * k) * flicker, 0.0F, 1.0F);
        }
        return 0.0F;
    }

    /** 引燃时横向膨胀（X/Z 变大）+ Y 方向轻微抖动（1~1.05 波动），类似苦力怕"变胖"的膨胀。 */
    @Override
    protected void scale(Bee entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        if (entity instanceof BeeperEntity b && b.getSwell() > 0) {
            float k = Math.min(1.0F, b.getSwell() / (float) BeeperEntity.MAX_SWELL);
            float s = 1.0F + k * 0.45F;
            // Y 正方向倍率在 1~1.05 之间波动
            float wobble = 1.0F + 0.05F * (0.5F + 0.5F * Mth.sin(entity.tickCount * 0.5F));
            poseStack.scale(s, wobble, s);
        }
    }
}