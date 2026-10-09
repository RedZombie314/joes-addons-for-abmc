package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.item.OrbOfLuckLock;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「核心锁在主手」在客户端的丢弃拦截。
 *
 * <p><b>为什么服务端拦了还不够：</b>原版 {@code LocalPlayer#drop} 是「先本地删、再发包」——
 * <pre>
 *   ItemStack itemstack = this.getInventory().removeFromSelected(fullStack);   // 本地先删掉
 *   this.connection.send(new ServerboundPlayerActionPacket(DROP_ITEM, ...));   // 然后才告诉服务端
 * </pre>
 * 服务端那边被 {@code OrbOfLuckItem#onDroppedByPlayer} 拒了（物品留在服务端手里），
 * 于是两端状态分叉：客户端手看着空了，服务端手里其实还攥着核心。后果有两个——
 * 手持物品看起来消失了；而且捡取判定 {@code player.getItemInHand(hand).isEmpty()} 是在服务端跑的，
 * 服务端手不空 → 别的核心也捡不起来。
 *
 * <p>所以这里在客户端把同一条规则也走一遍：直接返回 false，本地不删、包也不发，
 * 两端从始至终一致（连挥手动画都不会播）。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerOrbLockMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void jafa_orbLockDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        // 判据与 {@code OrbOfLuckItem#onDroppedByPlayer} 完全一致：选中的那件是不是核心、且允许上锁。
        // （所以"背包里放着核心、手上丢别的东西"照旧能丢，只是核心本身丢不出去。）
        if (OrbOfLuckLock.dropBlocked(self)) {
            cir.setReturnValue(false);
        }
    }
}
