package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「潜影盒」作为交易费用的交易接受任意颜色的潜影盒（含未染色）。
 *
 * 原版 MerchantOffer.satisfiedBy 会按具体物品（stack.is(item)）匹配费用，因此一栏交易
 * 只能接受一种颜色的潜影盒。本 mixin 在费用为任意潜影盒时，放宽物品身份校验为「任意
 * 潜影盒」，但仍按费用携带的 CONTAINER 数据组件校验内容物（决定交易是否匹配）。
 * 由于该方法是服务端与客户端共用的，客户端交易界面（MerchantContainer.updateSellItem）
 * 与服务端结算（MerchantResultSlot.onTake）都会接受任意颜色的潜影盒。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {

    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
    private void jafa_acceptAnyShulkerBox(ItemStack playerOfferA, ItemStack playerOfferB,
                                          CallbackInfoReturnable<Boolean> cir) {
        MerchantOffer self = (MerchantOffer) (Object) this;
        ItemCost costA = self.getItemCostA();
        Item costItem = costA.item().value();
        // 仅当费用是潜影盒、且玩家放入的也是潜影盒时，放宽颜色校验
        if (isShulkerBox(costItem) && isShulkerBox(playerOfferA.getItem())) {
            boolean ok = costA.components().test((DataComponentHolder) playerOfferA)
                && playerOfferA.getCount() >= 1
                && playerOfferB.isEmpty();
            cir.setReturnValue(ok);
        }
    }

    private static boolean isShulkerBox(Item item) {
        return item == Items.SHULKER_BOX
            || item == Items.WHITE_SHULKER_BOX
            || item == Items.ORANGE_SHULKER_BOX
            || item == Items.MAGENTA_SHULKER_BOX
            || item == Items.LIGHT_BLUE_SHULKER_BOX
            || item == Items.YELLOW_SHULKER_BOX
            || item == Items.LIME_SHULKER_BOX
            || item == Items.PINK_SHULKER_BOX
            || item == Items.GRAY_SHULKER_BOX
            || item == Items.LIGHT_GRAY_SHULKER_BOX
            || item == Items.CYAN_SHULKER_BOX
            || item == Items.PURPLE_SHULKER_BOX
            || item == Items.BLUE_SHULKER_BOX
            || item == Items.BROWN_SHULKER_BOX
            || item == Items.GREEN_SHULKER_BOX
            || item == Items.RED_SHULKER_BOX
            || item == Items.BLACK_SHULKER_BOX;
    }
}