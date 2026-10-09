package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.BeeBossData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BeeBoss 的朝向 / 蛰针 / 蛰刺 / 怒气注入（蜜蜂含 Beeper，因其继承 Bee）：
 * <ul>
 *   <li>{@code tick} TAIL — 有目标时强制本体朝向目标；蛰针脱落满
 *       {@link BeeBossData#STINGER_REGROW_TICKS} 后再生；</li>
 *   <li>{@code readAdditionalSaveData} — NBT 载入后应用 BeeBoss 默认配置；</li>
 *   <li>{@code doHurtTarget} HEAD — 近战闸门：远程模式下不造成蛰刺；</li>
 *   <li>{@code doHurtTarget} TAIL — 近战命中后补中毒 II，并记录蛰针脱落时刻；</li>
 *   <li>{@code setRemainingPersistentAngerTime} — 远程模式下禁止被写入怒气。</li>
 * </ul>
 * <p>BeeBoss 标志本身不在这里注册同步数据——它存在 NeoForgeData 里，原因见 {@link BeeBossData} 类注释。
 * <p>原版「毒刺脱落后自毙」对 BeeBoss 的屏蔽不在这里，而在 ModMain 的伤害拦截
 * （{@code ModMain#onBeeBossStingDeath}）。
 */
@Mixin(Bee.class)
public abstract class BeeBossMixin {

    /**
     * 每刻收尾（{@code Bee.tick} TAIL），做两件事：
     * <ol>
     *   <li><b>强制面向目标</b>：只靠 {@code LookControl} 不够——它只驱动<b>头部</b>（{@code yHeadRot} / {@code xRot}），
     *       而蜜蜂的移动控制器每刻都会把<b>本体</b>朝向（{@code yRot} / {@code yBodyRot}）覆盖成「飞行方向」，
     *       于是观感上 Boss 是背对敌人侧飞过去的。{@code Bee.tick} 会先跑完 {@code super.tick()}
     *       （内含 aiStep → serverAiStep → 各 AI 目标与 move/look 控制器），所以这个位置一定晚于所有会改写
     *       朝向的逻辑。顺带的好处：权杖齐射的放出点取的是 {@code getLookAngle()}，朝向对齐后蜂群确实朝敌人放。</li>
     *   <li><b>蛰针脱落 → {@link BeeBossData#STINGER_REGROW_TICKS} 后再生</b>：原版 {@code setHasStung(true)}
     *       会正常生效（蛰针真的脱落、期间无法再蛰），一段时间后由这里复位以便继续近战。
     *       （原版蜜蜂脱落即不再生并会自毙；自毙已在 ModMain 侧对 BeeBoss 屏蔽。）</li>
     * </ol>
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void jafa_beeBossTickTail(CallbackInfo ci) {
        Bee self = (Bee) (Object) this;
        if (!BeeBossData.isBeeBoss(self)) {
            return;
        }
        LivingEntity target = self.getTarget();
        if (target != null && target.isAlive()) {
            double dx = target.getX() - self.getX();
            double dz = target.getZ() - self.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            self.setYRot(yaw);
            self.yHeadRot = yaw;
            self.yBodyRot = yaw;
            // 俯仰（xRot）交给 LookControl：把转头/抬头速率放到最大，避免慢慢磨
            self.getLookControl().setLookAt(target, 360.0F, 360.0F);
        }
        if (self.hasStung()) {
            long lostAt = self.getPersistentData().getLong(BeeBossData.STINGER_LOST_TAG);
            if (self.level().getGameTime() - lostAt >= BeeBossData.STINGER_REGROW_TICKS) {
                regrowStinger(self);
            }
        }
    }

    /** 让蛰针再生：把 {@code HasStung} 复位为 false。
     *  <p>原版没有公开的 setter（{@code Bee.setHasStung} 是 private），但读档路径会读这个字段——
     *  于是走一次「自身 NBT 快照 → 只改 HasStung → 读回」的原版通路来实现复位：
     *  快照出自 {@code saveWithoutId}，其余字段原样写回，不会丢数据。
     *  <p>往返期间用 {@link BeeBossData#REGROW_IN_PROGRESS_TAG} 标记，让
     *  {@link #jafa_beeApplyBossDefaults} 跳过默认配置的重放——那次往返只为复位 {@code HasStung}，
     *  不该顺带重发权杖（否则玩家用 /item 清掉的权杖会在每次再生时凭空长回来）。 */
    private static void regrowStinger(Bee bee) {
        CompoundTag snapshot = bee.saveWithoutId(new CompoundTag());
        snapshot.putBoolean(Bee.TAG_HAS_STUNG, false);
        bee.getPersistentData().putBoolean(BeeBossData.REGROW_IN_PROGRESS_TAG, true);
        try {
            bee.readAdditionalSaveData(snapshot);
        } finally {
            bee.getPersistentData().remove(BeeBossData.REGROW_IN_PROGRESS_TAG);
        }
    }

    /** NBT 载入后应用 BeeBoss 默认配置（生命上限 / 体型 / 索敌 / 权杖 / AI 目标）。
     *  <p>标志位由 NeoForge 在 {@code Entity#load} 中先行读入（NeoForgeData），早于
     *  {@code readAdditionalSaveData}，所以这里 {@link BeeBossData#isBeeBoss} 拿到的是正确值。
     *  <p>只有 NBT 里没有 {@code Health}（说明是 {@code /summon} 这类新建而非读档）才补满生命值、
     *  才发默认权杖；否则每次载入都会把受伤的 Boss 治满，并把玩家清掉的权杖补回来。
     *  <p>蛰针再生的内部往返不算「载入」，直接跳过（见 {@link #regrowStinger}）。 */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void jafa_beeApplyBossDefaults(CompoundTag compound, CallbackInfo ci) {
        Bee self = (Bee) (Object) this;
        if (self.getPersistentData().getBoolean(BeeBossData.REGROW_IN_PROGRESS_TAG)) {
            return;
        }
        BeeBossData.applyBeeBossDefaults(self, !compound.contains("Health"));
    }

    /**
     * 本体近战闸门：BeeBoss 在<b>远程模式</b>（持杖且未切换近战）下不下口——直接取消 {@code Bee.doHurtTarget}。
     * <p>取消的连锁效果：无蛰刺伤害、无 {@code BEE_STING} 音效、无中毒，也不会触发
     * {@code setHasStung(true)} 与 {@code stopBeingAngry()}；TAIL 处的中毒补正同样不会执行
     * （{@code setReturnValue} 会让方法立即返回）。
     * <p>近战模式下不拦截：{@link BeeBossData#MELEE_ATTACK_DAMAGE} 点伤害 + 中毒 II
     * （amplifier {@link BeeBossData#STING_POISON_AMPLIFIER}）。
     */
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void jafa_beeBossMeleeGate(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Bee self = (Bee) (Object) this;
        if (BeeBossData.isBeeBoss(self) && BeeBossData.isRangedMode(self)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 远程模式下彻底禁止 BeeBoss 进入怒气状态。
     * <p>原版 {@code Bee.BeeAttackGoal} 的前提是 {@code isAngry()}（即持久怒气 > 0），而怒气还可能由
     * 原版「被谁打就恨谁」（{@code Bee.BeeHurtByOtherGoal}）或 {@code NeutralMob#updatePersistentAnger}
     * 自行写入——那些路径不经过本模组的索敌目标，若放行，Boss 就会在持杖时被拽到目标身边近战。
     * 因此在怒气时长的唯一写入点拦截正值写入，保证远程模式下 isAngry 恒为 false。
     * <p>只拦正值：{@code updatePersistentAnger} 每刻的递减、以及放弃目标时的清零都必须照常生效。
     */
    @Inject(method = "setRemainingPersistentAngerTime", at = @At("HEAD"), cancellable = true)
    private void jafa_beeBossNoAngerInRangedMode(int remainingTicks, CallbackInfo ci) {
        if (remainingTicks <= 0) {
            return;
        }
        Bee self = (Bee) (Object) this;
        if (BeeBossData.isBeeBoss(self) && BeeBossData.isRangedMode(self)) {
            ci.cancel();
        }
    }

    /**
     * BeeBoss 蛰刺的收尾（{@code doHurtTarget} TAIL）：① 按 Boss 规格覆盖中毒；② 记录蛰针脱落时刻。
     * <p>中毒：原版 {@code Bee.doHurtTarget} 给的是 10 秒（困难 18 秒）<b>中毒 I</b>，这里在命中后补一次
     * {@link BeeBossData#STING_POISON_AMPLIFIER} 级（中毒 II）、
     * {@link BeeBossData#STING_POISON_TICKS}（困难 54 秒）的毒。
     * <p>强度更高的同级或更高级效果会被 {@code MobEffectInstance#update} 保留更久/更强的那份，
     * 因此这里不会把目标身上更强的毒降级。
     * <p>蛰针：原版 {@code setHasStung(true)} 已照常生效（蛰针脱落、期间不再蛰），这里只把脱落时刻记进
     * 持久化数据（用游戏刻，读档后仍然有效），再生由 {@code tick} TAIL 负责。
     */
    @Inject(method = "doHurtTarget", at = @At("TAIL"))
    private void jafa_beeBossStingAftermath(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        Bee self = (Bee) (Object) this;
        if (!BeeBossData.isBeeBoss(self) || !(target instanceof LivingEntity living)) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.POISON,
            BeeBossData.stingPoisonTicks(self.level().getDifficulty()),
            BeeBossData.STING_POISON_AMPLIFIER), self);
        if (self.hasStung()) {
            self.getPersistentData().putLong(BeeBossData.STINGER_LOST_TAG, self.level().getGameTime());
        }
    }
}