package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * <b>末影龙特攻</b>：把"附体这一侧"打出的伤害一律换成<b>爆炸伤害</b>再结算。
 *
 * <h2>为什么需要这个 mixin</h2>
 * 末影龙只接受两类伤害（见 {@code EnderDragon#hurt(EnderDragonPart, DamageSource, float)}）：
 * <ol>
 *   <li>{@code source.getEntity() instanceof Player} —— 攻击者本人是玩家；</li>
 *   <li>{@code source.is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)} —— 该标签的内容就是
 *       {@code #minecraft:is_explosion}（1.21.1 数据包里就这么写的）。</li>
 * </ol>
 * 被附体的玩家空壳<b>不是玩家</b>，所以它的箭矢、近战、三叉戟、飞刀原本对末影龙
 * <b>一点伤害都不结算</b>（连受击反馈都没有）。改成爆炸伤害后它们就都能正常打伤末影龙了。
 *
 * <h2>为什么钩在这里，而不是伤害事件</h2>
 * 末影龙的所有伤害入口最终都汇到 {@code hurt(EnderDragonPart, …)}：部位命中由
 * {@code EnderDragonPart#hurt} 直接调它，而 {@code hurt(DamageSource, float)} 也只是转调
 * {@code this.hurt(this.body, source, amount)}。而上面那道门槛就在这个方法的第一行 ——
 * <b>不满足门槛时原版根本不会走到 {@code LivingEntity#hurt}</b>，
 * 也就不会有 {@code LivingIncomingDamageEvent} 可以监听。所以必须在这一层改伤害来源。
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {

    /** 把 DamageSource 参数换成（必要时）爆炸版本；见 {@link ModMain#possessionDamageForEnderDragon}。 */
    @ModifyVariable(
        method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;"
            + "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"),
        argsOnly = true)
    private DamageSource jafa_explosionDamageForPossessionAttacks(DamageSource source) {
        return ModMain.possessionDamageForEnderDragon(source);
    }
}
