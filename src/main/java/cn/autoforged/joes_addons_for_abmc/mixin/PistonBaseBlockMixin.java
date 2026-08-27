package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 红石权杖活塞信号放行 mixin。
 *
 * 原版活塞延长前会在 {@link PistonBaseBlock#getNeighborSignal} 检查相邻是否有真实红石信号；
 * 红石权杖通过排队 {@link net.minecraft.world.level.Level#blockEvent} 的方式驱动活塞延长
 * （这样才能让客户端收到 ClientboundBlockEventPacket 并播放完整动画），因此需要在此处
 * 对“权杖当前正驱动的活塞”直接返回 true，从而无需在活塞旁放置任何临时红石方块。
 *
 * 仅在服务端生效：客户端没有 STAFF_EXTEND_PISTONS 登记，行为与原版一致。
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @Inject(method = "getNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void jafm_staffDrivenSignal(SignalGetter signalGetter, BlockPos pos, Direction direction,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (signalGetter instanceof Level level && ModMain.isPistonStaffExtending(level.dimension(), pos)) {
            cir.setReturnValue(true);
        }
    }
}
