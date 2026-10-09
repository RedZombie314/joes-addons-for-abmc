package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyDimensionMobs;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让幸运维度里的<b>水生生物把空气当水</b>：直接伪造 {@code Entity#isInWater()}。
 *
 * <p>为什么是这一个方法：原版与"水"有关的关键判断全都读它——
 * <ul>
 *   <li>{@code LivingEntity#travel}（LivingEntity.java:2228-2261）：走水中移动分支，
 *       于是重力被削弱成 {@code getGravity()/16}（缓降）、带水阻力，看起来就是在水里游；</li>
 *   <li>{@code WaterBoundPathNavigation#canUpdatePath}（:26-28）读 {@code mob.isInLiquid()}，
 *       而 {@code isInLiquid() = isInWaterOrBubble() || isInLava()}（Entity.java:1256-1258）
 *       → 水生导航在空气里也能算出路径，鱼群会真的游起来；</li>
 *   <li>{@code SmoothSwimmingMoveControl#tick}（:26-45）两处读它 → 鱼类游动控制照常工作。</li>
 * </ul>
 * 判定顺序按开销排：先 {@code instanceof Mob}（非生物实体立刻出局），再比维度，最后才查分类。
 */
@Mixin(Entity.class)
public class EntityAirSwimMixin {

    @Inject(method = "isInWater", at = @At("HEAD"), cancellable = true)
    private void joes_addons_for_abmc$treatAirAsWater(CallbackInfoReturnable<Boolean> cir) {
        if (LuckyDimensionMobs.swimsInAir((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
