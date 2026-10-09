package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 蜂巢权杖左Alt 释放的「友善蜜蜂」永远不会仇视持有该权杖的玩家。
 * 通过拦截 {@link Mob#setTarget}，当目标是带 {@link BeehiveStaffHelper#FRIENDLY_TAG}
 * 标记所指的玩家时直接忽略，从而彻底阻断其攻击该玩家。
 */
@Mixin(Mob.class)
public abstract class BeeFriendlyOwnerMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void jafa_beeFriendlyNeverAggroOwner(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(self instanceof Bee bee)) {
            return;
        }
        if (!(target instanceof Player p)) {
            return;
        }
        CompoundTag tag = bee.getPersistentData();
        if (!tag.hasUUID(BeehiveStaffHelper.FRIENDLY_TAG)) {
            return;
        }
        UUID owner = tag.getUUID(BeehiveStaffHelper.FRIENDLY_TAG);
        if (owner.equals(p.getUUID())) {
            ci.cancel();
        }
    }
}