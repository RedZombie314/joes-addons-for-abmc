package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给带 {@link BeehiveStaffHelper#CRUSH_UNTIL_TAG} 标记的蜜蜂/玩家在截止前免疫挤压（in_wall/卡方块）伤害。
 * 释放出的蜜蜂、吸收寻路过程中的蜜蜂，以及释放时保持权杖的玩家本身都会获得该免疫。
 */
@Mixin(LivingEntity.class)
public abstract class BeeCrushImmunityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void jafa_beeCrushImmunity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 仅对蜜蜂与玩家生效，避免影响其他生物
        if (!(self instanceof Bee) && !(self instanceof Player)) {
            return;
        }
        CompoundTag tag = self.getPersistentData();
        int until = tag.getInt(BeehiveStaffHelper.CRUSH_UNTIL_TAG);
        // 同时拦截「卡方块窒息(IN_WALL)」与「实体堆叠挤压(CRAMMING)」
        boolean crush = source.typeHolder().unwrapKey()
            .map(k -> k.equals(DamageTypes.IN_WALL) || k.equals(DamageTypes.CRAMMING)).orElse(false);
        if (until > self.tickCount && crush) {
            cir.setReturnValue(false);
        }
    }
}