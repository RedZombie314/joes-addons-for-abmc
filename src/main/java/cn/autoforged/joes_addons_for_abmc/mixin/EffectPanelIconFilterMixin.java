package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 物品栏界面右侧效果面板图标过滤：附魔状态效果（含击退）与变形状态不在面板上渲染。
 * <p>
 * 原版 {@code EffectRenderingInventoryScreen.renderEffects} 遍历所有活动效果并绘制
 * （不检查 showIcon），因此必须在此过滤渲染集合，使隐藏效果既不显示图标也不显示名称/时长。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectPanelIconFilterMixin {

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
