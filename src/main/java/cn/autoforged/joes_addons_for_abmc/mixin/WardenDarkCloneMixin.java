package cn.autoforged.joes_addons_for_abmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.autoforged.joes_addons_for_abmc.entity.DarkCloneHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * 让监守者的黑暗分身从<b>源头</b>上不把「不该攻击的目标」当作目标。
 *
 * <h2>为什么监守者必须单独处理</h2>
 * 它的选目标完全走<b>脑记忆</b>（{@code Warden#customServerAiStep} → {@code AngerManagement}
 * → 把结果写进 {@code MemoryModuleType.ATTACK_TARGET}，见 Warden.java:543），
 * 既不经过 {@code Mob#setTarget}，也不看 {@code targetSelector}
 * ——所以 {@code LivingChangeTargetEvent} 收口与清空目标 goal 对它统统无效。
 *
 * <p>原版这里本来就有一条同类排除：
 * <pre>
 * // Warden#canTargetEntity, Warden.java:398
 * && livingEntity.getType() != EntityType.ARMOR_STAND
 * && livingEntity.getType() != EntityType.WARDEN
 * </pre>
 * 本 mixin 把这条扩成「{@link DarkCloneHelper#shouldNotAttack} 判定的目标都不算目标」，
 * 形式与原版完全一致：让 {@code canTargetEntity} 直接返回 false。
 * {@code AngerManagement} 就是用这个方法当过滤器的（Warden.java:114），
 * 因此监守者根本不会对这些目标产生怒气，也就不会有追击、咆哮与音爆。
 *
 * <p><b>覆盖范围</b>：友方黑暗分身（原需求）<b>以及自己的 Owner</b>。
 * 后者是后来补的——「不索敌 Owner」原先只写在 {@code DarkCloneTargetGoal} 里，
 * 而监守者根本不走那个 goal，所以它会攻击生存模式的 Owner。
 * 对非黑暗分身的监守者，{@code shouldNotAttack} 一律返回 false，原版行为不变。
 */
@Mixin(Warden.class)
public abstract class WardenDarkCloneMixin {

    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void jafa_ignoreForbiddenTargets(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof LivingEntity target)) return;
        if (DarkCloneHelper.shouldNotAttack((LivingEntity) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }
}
