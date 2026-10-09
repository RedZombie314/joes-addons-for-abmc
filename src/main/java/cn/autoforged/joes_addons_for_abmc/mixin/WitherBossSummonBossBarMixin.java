package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.OrbPossessionSummons;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>己方召唤的凋灵不显示 boss 血条</b>（用户要求）。
 *
 * <h2>为什么收在这里</h2>
 * 凋灵的血条来自它自己的 {@code ServerBossEvent}，而把玩家拉进那个事件的地方只有一个：
 * {@code WitherBoss#startSeenByPlayer(ServerPlayer)}（{@code super} 之后 {@code bossEvent.addPlayer(player)}）。
 * 那个字段是 private，没有 getter，所以只能在这一步拦：<b>只要是附体召唤物，就整段取消</b> ——
 * 玩家从未被加进去，血条自然从头到尾都不出现。
 * <p>
 * 判定用 {@code OrbPossessionSummons.isSummon}（召唤时打的标记），
 * 所以<b>只有玩家空壳叫出来的凋灵</b>没血条；别的凋灵（包括模组自己那套黑暗分身）照旧显示。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossSummonBossBarMixin {

    @Inject(method = "startSeenByPlayer", at = @At("HEAD"), cancellable = true)
    private void jafa_hideBossBarForSummons(ServerPlayer player, CallbackInfo ci) {
        if (OrbPossessionSummons.isSummon((WitherBoss) (Object) this)) {
            ci.cancel();
        }
    }
}
