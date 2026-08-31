package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.client.BarrierStaffHelper;
import net.minecraft.client.particle.BlockMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 屏障消失速度加快 mixin（作用于 BlockMarker）。
 *
 * 屏障方块标记粒子（BLOCK_MARKER）默认寿命为 80 tick。当屏障方块被移除后，该粒子仍会
 * 存留至寿命结束，表现为屏障贴图缓慢消失。本 mixin 在持有屏障权杖时将粒子寿命缩短为原来的
 * 1/3（80 -> 27），使屏障贴图消失速度与出现速度一样加快约 3 倍。
 *
 * 说明：BlockMarker 构造函数中通过 {@code this.lifetime = 80} 设置寿命。此处使用
 * @ModifyConstant 直接改写该常量，避免因 lifetime 字段继承自 Particle 且无 refMap 而
 * 导致的 @Shadow 字段定位失败。
 */
@Mixin(BlockMarker.class)
public abstract class BlockMarkerParticleMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 80))
    private int jafa_speedupDisappear(int constant) {
        if (BarrierStaffHelper.isHoldingBarrierStaff()) {
            return 27;
        }
        return constant;
    }
}