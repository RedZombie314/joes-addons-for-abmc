package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.client.BarrierStaffHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端屏障粒子渲染 mixin（作用于 ClientLevel）。
 *
 * 原版机制：仅当玩家在创造模式手持屏障物品时，ClientLevel.getMarkerParticleTarget()
 * 才会返回屏障方块，随后 animateTick() 的采样循环（667 次）在周围随机命中屏障方块时
 * 生成 BLOCK_MARKER 粒子，从而在屏障方块周围显示出贴图。
 *
 * 本 mixin 实现：
 * 1. 持有屏障权杖时（任意模式），getMarkerParticleTarget() 返回屏障方块，使周围屏障
 *    方块像手持屏障物品一样显示贴图。
 * 2. 持有屏障权杖时，将 animateTick() 的采样循环次数放大 3 倍（667 -> 2001），使屏障
 *    贴图出现速度（渲染速度）比手持屏障物品时快 3 倍。
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "getMarkerParticleTarget", at = @At("HEAD"), cancellable = true)
    private void jafm_getMarkerParticleTarget(CallbackInfoReturnable<Block> cir) {
        if (BarrierStaffHelper.isHoldingBarrierStaff()) {
            cir.setReturnValue(Blocks.BARRIER);
        }
    }

    @ModifyConstant(method = "animateTick", constant = @Constant(intValue = 667))
    private int jafm_animateTickLoop(int constant) {
        if (BarrierStaffHelper.isHoldingBarrierStaff()) {
            return constant * 3;
        }
        return constant;
    }
}