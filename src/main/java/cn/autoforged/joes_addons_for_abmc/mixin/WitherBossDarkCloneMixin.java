package cn.autoforged.joes_addons_for_abmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.autoforged.joes_addons_for_abmc.entity.DarkCloneHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 凋灵黑暗分身的三个头都不索敌「不该攻击的目标」。
 *
 * <h2>为什么监守者那套收口对凋灵侧头无效</h2>
 * 凋灵的三个头各有自己的目标，存在<b>同步数据槽</b>里
 * （{@code DATA_TARGET_A/B/C}，WitherBoss.java:61-64）：
 * <ul>
 *   <li>主头（槽 0）：由 {@code customServerAiStep} 从 {@code getTarget()} 同步过来（L320-324），
 *       而 {@code getTarget()} 走 {@code Mob#setTarget}，所以已被
 *       {@code LivingChangeTargetEvent} 与清空 targetSelector 拦住——这就是「主头已经正常」的原因；</li>
 *   <li>侧头（槽 1、2）：在 {@code for (int i = 1; i < 3; i++)} 循环里自己选目标（L282 起），
 *       完全绕开 {@code Mob#setTarget} 与 {@code targetSelector}，因此两边都拦不住。</li>
 * </ul>
 *
 * <h2>收口位置</h2>
 * 三个槽无论是「选中新目标」（L314）、「主头同步」（L321）还是「目标失效后清空」（L307），
 * 唯一写入口都是 {@code setAlternativeTarget(int, int)}（WitherBoss.java:571）。
 * 挂在这里一次覆盖三个头，无需碰 {@code customServerAiStep} 那一大段逻辑。
 *
 * <p>取消写入后该槽保持原值：若原本是 0，侧头下一轮会重新随机挑一个附近实体
 * （L310-315 的 {@code TARGETING_CONDITIONS} 检索），挑到不该打的目标就继续被拒，
 * 直到挑到合法目标为止——表现为「不去索敌」，而不是「卡住」。
 *
 * <p><b>覆盖范围</b>：友方黑暗分身（原需求）<b>以及自己的 Owner</b>。
 * 后者是后来补的——「不索敌 Owner」原先只写在 {@code DarkCloneTargetGoal} 里，
 * 而侧头根本不走那个 goal，所以它会攻击生存模式的 Owner。
 *
 * <p>残留缺口：实体 id 会被回收复用，若某个槽里存的旧 id 之后被一个不该打的目标占用，
 * L299 的 {@code canAttack} 会放行并朝它开火。这条由伤害收口
 * （{@code DarkCloneEvents#onDarkCloneDamageGuard}）兜底，不会有实际伤害。
 * 另外侧头在「长时间没有目标」时会朝随机位置盲射（L292），那种溅射也由伤害收口挡住。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossDarkCloneMixin {

    @Inject(method = "setAlternativeTarget", at = @At("HEAD"), cancellable = true)
    private void jafa_skipForbiddenTarget(int head, int entityId, CallbackInfo ci) {
        // entityId <= 0 是「清空该槽」（含主头无目标时写 0），必须放行，否则槽会永远清不掉
        if (entityId <= 0) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level().getEntity(entityId) instanceof LivingEntity target)) return;
        if (DarkCloneHelper.shouldNotAttack(self, target)) {
            ci.cancel();
        }
    }
}
