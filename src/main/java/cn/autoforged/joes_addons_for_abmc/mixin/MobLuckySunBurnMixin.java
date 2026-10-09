package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyDimensionMobs;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幸运维度的太阳<b>不点燃亡灵</b>（需求）：本维度里 {@code Mob#isSunBurnTick()} 一律返回 false。
 *
 * <h3>为什么打这一个点就够</h3>
 * 1.21.1 里"白天被晒着火"这件事只有一条总闸：{@code Mob#isSunBurnTick()}（Mob.java:1524-1535，
 * 判据是白天 + 亮度 &gt; 0.5 + 头顶能看见天 + 没在水/细雪里）。全项目只有三个调用点，
 * 而且<b>没有任何子类覆写它</b>：
 * <ul>
 *   <li>{@code Zombie#aiStep}（Zombie.java:237）—— 僵尸/尸壳/溺尸/僵尸村民/僵尸猪灵……；
 *       它前面还有一道"这一种怕不怕太阳"的 {@code isSunSensitive()}（尸壳返回 false，本来就不烧）；</li>
 *   <li>{@code AbstractSkeleton#aiStep}（AbstractSkeleton.java:96）—— 骷髅/流浪者/凋灵骷髅/沼骸；</li>
 *   <li>{@code Phantom#aiStep}（Phantom.java:143）—— 幻翼。</li>
 * </ul>
 * 所以在这里拦住，就等于"本维度的太阳对亡灵无害"，而且不影响任何其它维度（维度判断在最前面）。
 *
 * <p>为什么不用"把维度的 {@code has_skylight} 关掉"这种取巧办法：那会把整个维度的天光、
 * 昼夜亮度、刷怪光照判定一起改掉（本维度是按主世界那套做的），代价远大于收益。
 *
 * <p>放大镜看这一刀的落点：{@code isSunBurnTick} 由服务端与客户端都会调用，但客户端那半边
 * 本来就会被 {@code !this.level().isClientSide} 挡掉；着火状态是服务端 {@code setSecondsOnFire}
 * 之后按实体标志位同步给客户端的，所以只要服务端不再点燃，客户端就看不到火。
 */
@Mixin(Mob.class)
public class MobLuckySunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void joes_addons_for_abmc$noSunBurnInLuckyDimension(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (LuckyDimensionMobs.isLuckyDimension(self.level())) {
            cir.setReturnValue(false);
        }
    }
}
