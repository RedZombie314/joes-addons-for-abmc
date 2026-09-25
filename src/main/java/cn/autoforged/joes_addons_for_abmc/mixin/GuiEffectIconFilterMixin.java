package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * HUD 效果图标过滤：附魔状态效果（含击退）与变形状态不在玩家视角 HUD 上渲染图标。
 * <p>
 * 原版 Gui.renderEffects 以 {@link MobEffectInstance#showIcon()} 决定是否绘制图标，
 * 但服务端重注入的 showIcon=false 因时长/等级未变化而不会同步到客户端，故直接在此
 * 过滤渲染集合，保证无论来源（权杖、/effect、药水）的图标都被隐藏（效果本身仍生效）。
 */
@Mixin(Gui.class)
public abstract class GuiEffectIconFilterMixin {

    @Redirect(method = "renderEffects",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> jafa_filterHiddenEffectIcons(LocalPlayer player) {
        Collection<MobEffectInstance> all = player.getActiveEffects();
        if (all.isEmpty()) return all;
        List<MobEffectInstance> filtered = new ArrayList<>(all.size());
        for (MobEffectInstance inst : all) {
            if (!ModMain.isHiddenEffectIcon(inst.getEffect())) filtered.add(inst);
        }
        return filtered;
    }
}
