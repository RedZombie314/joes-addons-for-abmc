package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让药水（直饮/喷溅/滞留）允许堆叠，最大堆叠数为 16（原版默认 1）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxSizeMixin {

    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void jafa_potionMaxStack(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        Item item = self.getItem();
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            cir.setReturnValue(16);
        }
    }
}