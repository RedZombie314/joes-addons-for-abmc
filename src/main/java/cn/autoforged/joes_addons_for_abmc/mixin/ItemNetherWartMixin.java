package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 地狱疣可食用：让地狱疣具备"进食"使用动画（EAT）与 32 刻（1.6 秒）使用时长。
 * 实际进食结算（白名单/一次性/饱食度/营养）由 ModMain 的事件处理完成。
 */
@Mixin(net.minecraft.world.item.Item.class)
public abstract class ItemNetherWartMixin {

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void jafa_netherWartUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (stack.is(net.minecraft.world.item.Items.NETHER_WART)) {
            cir.setReturnValue(UseAnim.EAT);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void jafa_netherWartUseDuration(ItemStack stack, LivingEntity entity,
                                            CallbackInfoReturnable<Integer> cir) {
        if (stack.is(net.minecraft.world.item.Items.NETHER_WART)) {
            cir.setReturnValue(32);
        }
    }
}
