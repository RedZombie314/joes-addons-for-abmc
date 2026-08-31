package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让“快速装填”魔咒状态效果对被附魔的近战怪物可见生效。
 *
 * 原版怪物的近战攻击间隔由 {@link MeleeAttackGoal#resetAttackCooldown()} 固定为 20 刻，且不走
 * ATTACK_SPEED 属性，因此此前“每级 +25% 攻击速度”的修饰无法体现在怪物身上。本 mixin 改用直接
 * 缩短怪物《两次近战攻击之间的间隔》：等级每 +1 级，攻击速度 ×(1+0.25×等级)（间隔相应缩短，
 * 最小 2 刻），使附魔怪肉眼可见地更频繁挥击。
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {

    @Shadow protected PathfinderMob mob;

    @Shadow private int ticksUntilNextAttack;

    @Inject(method = "resetAttackCooldown", at = @At("HEAD"), cancellable = true)
    private void jafa_quickChargeFasterAttacks(CallbackInfo ci) {
        int quickCharge = ModMain.getEnchantLevel(mob, Enchantments.QUICK_CHARGE);
        if (quickCharge > 0) {
            int interval = (int) Math.round(20.0 / (1.0 + 0.25 * quickCharge));
            this.ticksUntilNextAttack = Math.max(2, interval);
            ci.cancel();
        }
    }
}