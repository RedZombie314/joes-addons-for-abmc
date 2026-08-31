package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女巫 Boss（携带 {@code jafa_is_witch_boss} 标签）丢药水加速与弹道规避 mixin。
 *
 * <ol>
 *   <li>投掷间隔加速：原版女巫每次投掷后把 {@code attackTime} 重置为 60 刻再倒数；这里把间隔
 *       字段重定向为 1/3（60 → 20），使丢药水间隔缩短为普通女巫的 1/3（丢药速度 3 倍）。</li>
 *   <li>弹道自伤规避：在每次调用 {@code performRangedAttack} 前调用 {@code ModMain.prepareWitchBossThrow}，
 *       提前模拟药水弹道，若会打到自己则先调整位置再投。</li>
 * </ol>
 * 仅命中带 Boss 标记的女巫，普通女巫/其它远射生物完全不受影响。
 */
@Mixin(RangedAttackGoal.class)
public abstract class RangedAttackGoalMixin {

    @Shadow @Final private Mob mob;
    @Shadow @Final private int attackIntervalMin;
    @Shadow @Final private int attackIntervalMax;

    private static boolean isWitchBoss(Mob m) {
        return m instanceof Witch w && w.getPersistentData().getBoolean("jafa_is_witch_boss");
    }

    @Inject(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"),
        cancellable = true
    )
    private void jafa_witchBossAvoidSelfHit(CallbackInfo ci) {
        if (!(this.mob instanceof Witch w) || !isWitchBoss(w)) return;
        if (!(this.mob.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        // 优先级1：阶段3近战状态 → 完全取消本次投掷，且不做弹道自伤调整身位（近战不投/不规避）
        if (ModMain.isWitchBossInMelee(w)) {
            ci.cancel();
            return;
        }
        // 优先级2：近身逃逸 → 取消本次投掷攻击药水，也不做退位调整（实际丢逃逸药水由 tick 完成）
        if (ModMain.isWitchBossRetreatingNow(w, sl)) {
            ci.cancel();
            return;
        }
        // 否则：提前模拟药水弹道，若会打到自己则先调整位置再投（仅此优先级较低的自伤规避）
        ModMain.prepareWitchBossThrow(w);
    }

    @Redirect(
        method = "tick",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/goal/RangedAttackGoal;attackIntervalMin:I")
    )
    private int jafa_witchBossThirdsIntervalMin(RangedAttackGoal self) {
        return isWitchBoss(this.mob) ? Math.max(1, this.attackIntervalMin / 3) : this.attackIntervalMin;
    }

    @Redirect(
        method = "tick",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/goal/RangedAttackGoal;attackIntervalMax:I")
    )
    private int jafa_witchBossThirdsIntervalMax(RangedAttackGoal self) {
        return isWitchBoss(this.mob) ? Math.max(1, this.attackIntervalMax / 3) : this.attackIntervalMax;
    }
}