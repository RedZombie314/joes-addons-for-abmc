package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 解除原版 /enchant 命令的附魔等级限制。
 *
 * 原版 {@link EnchantCommand} 限制附魔等级不能超过该魔咒的 {@code maxLevel}（生存最高可附等级）。
 * 本 mixin 将等价上限放宽到 99，从而允许给任意物品在 0~99 之间附魔。
 */
@Mixin(EnchantCommand.class)
public abstract class EnchantCommandMixin {

    /** /enchant 允许的最高等级。 */
    private static final int JAFM_MAX_ENCHANT_LEVEL = 99;

    @Redirect(
        method = "enchant",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I")
    )
    private static int jafm_liftedLevelCap(Enchantment enchantment) {
        return JAFM_MAX_ENCHANT_LEVEL;
    }
}