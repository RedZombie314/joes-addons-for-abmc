package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.client.BarrierStaffHelper;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
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
 *
 * 另外：两个维度的天空色都在这里定死（原版天空颜色由 ClientLevel.getSkyColor()
 * 依据生物群系 skyColor + 昼夜/天气计算，这两个维度都不要那套）：
 * <ul>
 *   <li><b>Creeper Clan</b>：固定绿色 #79B83F；</li>
 *   <li><b>幸运维度</b>：两色渐变 —— <b>天顶</b>用高处色 #F4E8C1（这里返回），
 *       <b>地平线</b>用低处色 #FFFCF1（地平线是"雾色"，由
 *       {@code client/LuckyDimensionSky.Fog} 换掉）。</li>
 * </ul>
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class ClientLevelMixin {

    /** Creeper Clan 维度固定天空颜色 #79B83F（RGB 归一化）。 */
    private static final net.minecraft.world.phys.Vec3 CREEPER_CLAN_SKY =
        new net.minecraft.world.phys.Vec3(0x79 / 255.0, 0xB8 / 255.0, 0x3F / 255.0);

    @Inject(method = "getMarkerParticleTarget", at = @At("HEAD"), cancellable = true)
    private void jafa_getMarkerParticleTarget(CallbackInfoReturnable<Block> cir) {
        if (BarrierStaffHelper.isHoldingBarrierStaff()) {
            cir.setReturnValue(Blocks.BARRIER);
        }
    }

    @ModifyConstant(method = "animateTick", constant = @Constant(intValue = 667))
    private int jafa_animateTickLoop(int constant) {
        if (BarrierStaffHelper.isHoldingBarrierStaff()) {
            return constant * 3;
        }
        return constant;
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void jafa_dimensionSkyColor(net.minecraft.world.phys.Vec3 pos, float partialTick,
                                        CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
        net.minecraft.client.multiplayer.ClientLevel self = (net.minecraft.client.multiplayer.ClientLevel) (Object) this;
        net.minecraft.resources.ResourceLocation dim = self.dimension().location();
        if (dim.equals(ModDimensions.CREEPER_CLAN_DIM_LEVEL.location())) {
            cir.setReturnValue(CREEPER_CLAN_SKY);
            return;
        }
        // 幸运维度：天顶用高处色 #F4E8C1；地平线那半是"雾色"，另由
        // LuckyDimensionSky.Fog 监听 ViewportEvent.ComputeFogColor 换成低处色 #FFFCF1
        //（天空盘是一张水平圆盘 + 柱面雾，越靠地平线雾越浓 —— 不换雾色地平线就还是原版蓝）。
        if (dim.equals(ModDimensions.LUCKY_DIM_LEVEL.location())) {
            cir.setReturnValue(cn.autoforged.joes_addons_for_abmc.client.LuckyDimensionSky.skyColor());
        }
    }
}