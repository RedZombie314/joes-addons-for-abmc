package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyDimensionMobs;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幸运维度里的末影人<b>不会尝试搬运方块</b>（需求 1）。
 *
 * <p>末影人搬方块这件事完全由 {@code EnderMan.EndermanTakeBlockGoal} 负责
 * （EnderMan.java:617-654：{@code canUse} 掷 1/20 的概率决定"这次要不要去挖一块"，
 * {@code tick} 真的去挖并 {@code setCarriedBlock}）。所以只要在 {@code canUse} 处按维度返回 false，
 * 它就连"想搬"的念头都不会有——顺带也不会破坏地形。
 *
 * <p>目标类是包私有的静态内部类，用 {@code targets} 指名道姓；那个私有的 {@code enderman} 字段
 * 用 {@code @Shadow} 拿到（EnderMan.java:618）。{@code require = 0}：万一以后类名/方法名变了，
 * 也只是回到原版行为，不会崩。
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public class EnderManTakeBlockMixin {

    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void joes_addons_for_abmc$neverTakeBlocks(CallbackInfoReturnable<Boolean> cir) {
        if (LuckyDimensionMobs.isLuckyDimension(this.enderman.level())) {
            cir.setReturnValue(false);
        }
    }
}
