package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 改进原版魔咒等级显示。
 *
 * 原版 {@link Enchantment#getFullname} 使用 "enchantment.level.N" 语言键，
 * 而该键只定义到 10（X），一旦等级较高（例如 99）就会渲染成原始的
 * "enchantment.level.99" 裸文本。本 mixin 从 1 到 99 全部按罗马数字显示。
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentNameMixin {

    @Inject(method = "getFullname", at = @At("HEAD"), cancellable = true)
    private static void jafa_romanFullname(Holder<Enchantment> enchantment, int level,
                                           CallbackInfoReturnable<Component> cir) {
        MutableComponent mutablecomponent = enchantment.value().description().copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            mutablecomponent.append(CommonComponents.SPACE).append(Component.literal(toRoman(level)));
        }

        cir.setReturnValue(mutablecomponent);
    }

    /** 将 0~99 的数字转换为罗马数字；100 及以上直接返回十进制数字。 */
    private static String toRoman(int n) {
        if (n <= 0) return Integer.toString(n);
        if (n >= 100) return Integer.toString(n);
        if (n < 10) return ONES[n];
        return TENS[n / 10] + ONES[n % 10];
    }

    private static final String[] TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
}