package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 女巫 Boss 变种（携带 {@code jafm_is_witch_boss} 标签）的战斗行为微调：
 * 原版女巫在战斗中本应喝下「迅捷药水」时，会有 10% 概率改喝「隐身药水」。
 * 其余行为与普通女巫完全一致。
 *
 * 实现方式：重定向 {@code Witch.aiStep} 中对静态字段 {@code Potions.SWIFTNESS}
 * 的唯一读取点，命中 Boss 后按 10% 概率返回隐身药水。
 */
@Mixin(Witch.class)
public abstract class WitchMixin {

    @Redirect(
        method = "aiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/alchemy/Potions;SWIFTNESS:Lnet/minecraft/core/Holder;")
    )
    private Holder<Potion> jafm_maybeInvisibilityInsteadOfSwiftness() {
        Witch self = (Witch) (Object) this;
        if (self.getPersistentData().getBoolean("jafm_is_witch_boss")
            && self.getRandom().nextFloat() < 0.1F) {
            return Potions.INVISIBILITY;
        }
        return Potions.SWIFTNESS;
    }
}