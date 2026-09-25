package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持有避雷针权杖的玩家免疫闪电伤害（第一道闸）。
 * <p>
 * 原版所有实体受闪电影响的入口是 {@link Entity#thunderHit}（伤害 + 点燃 + 击退都在其中），
 * 在方法入口直接取消，可从根源上杜绝伤害/火焰/击退；配合
 * {@link LivingEntityLightningImmunityMixin}（hurt 入口）双保险。
 */
@Mixin(Entity.class)
public abstract class EntityThunderHitImmunityMixin {

    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
    private void jafa_lightningRodThunderHit(ServerLevel level, LightningBolt lightning, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player p && ModMain.isHoldingLightningRodStaff(p)) {
            p.setRemainingFireTicks(0);
            ci.cancel();
        }
    }
}
