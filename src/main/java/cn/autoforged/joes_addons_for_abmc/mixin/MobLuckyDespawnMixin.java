package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyDimensionMobs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幸运维度的消失规则：<b>整个接管 {@code Mob#checkDespawn}</b>，本维度只有一条删除规则——
 * 「{@code PersistenceRequired} 为 false 的生物，离最近玩家超过 128 格就立刻删除」（需求 4），
 * 无论什么生物。
 *
 * <h3>为什么要"整个接管"而不是只在 128 格外补一刀</h3>
 * 原版 {@code Mob#checkDespawn}（Mob.java:745-770）里除了 128 格那条，还有一条<b>更狠的"闲置消失"</b>：
 * <pre>
 *   if (noActionTime &gt; 600 &amp;&amp; random.nextInt(800) == 0 &amp;&amp; 距玩家 &gt; 32 格 &amp;&amp; removeWhenFarAway(...)) discard();
 * </pre>
 * {@code noActionTime} 只在"有目标"或"跑到玩家 32 格内"时才归零（Mob.java:763-765 与 setTarget）。
 * 而本维度按需求是<b>和平共处</b>：谁都不会有目标 —— 于是每一只的 {@code noActionTime} 都在单调增长，
 * 30 秒后就开始每刻 1/800 的掷骰。结果就是：
 * <ul>
 *   <li>生物的平均寿命 ≈ 600 + 800 = 1400 刻（约 70 秒）；</li>
 *   <li>按 {@code SPAWN_INTERVAL_TICKS = 20}（每刻 1/20 只）算，稳定数量只有
 *       {@code 1400 / 20 = 70} 只左右 —— 上限 700 永远够不着；</li>
 *   <li>而这 70 只撒在半径 128 格（256×256 格）的范围里，玩家近处自然就"看不到几只"
 *       （这正是"地表平均不超过 5 只"的来历）。</li>
 * </ul>
 * 所以在本维度直接把原版那套取消掉，只保留需求 4 那一条：这样数量才会真的往 {@code MOB_CAP}
 * 上堆，玩家才看得到。
 *
 * <p><b>连带效果</b>（都是刻意的）：
 * <ul>
 *   <li>原版的 {@code removeWhenFarAway} 闸门不再参与 —— 动物/村民等覆写成 false 的生物同样按 128 格规则删除，
 *       这才是需求 4 要的「无论什么生物」；</li>
 *   <li>和平难度不再自动清除怪物（本维度按需求不看难度：生成端也不看）；</li>
 *   <li>NeoForge 的 {@code MobDespawnEvent} 在本维度不再触发（它是在原版 checkDespawn 内部抛的）。</li>
 * </ul>
 *
 * <p>两处刻意留的余地：{@code isPersistenceRequired()} 为 true 的不动（命名过的、被拴住的等）；
 * <b>正被骑乘的</b>不动（{@code isVehicle()}），否则玩家骑着的马会在跑远的一瞬间凭空消失。
 * 没有玩家（{@code getNearestPlayer} 返回 null，例如玩家都离开这个维度）时不处理。
 * 每刻对每个生物都会被调用，所以维度判断放在最前面，非本维度零额外开销。
 *
 * <p>另外这里还兼一个完全无关的职责：<b>抓取残留自愈</b>，见
 * {@code LuckySelectorEntity#healOrphanedCapture(Mob)}——选择器抓住一只生物之后、骑上去之前
 * 被区块卸载时（那条路不走 {@code removePassenger}），被抓的生物会留在「无AI + 无重力」状态里，
 * 这里给它一个自愈出口。这段放在维度判断<b>之前</b>（选择器在哪个维度都可能被抓），
 * 靠两个标志位预筛，常态下零额外开销。
 */
@Mixin(Mob.class)
public class MobLuckyDespawnMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void joes_addons_for_abmc$luckyDimensionDespawn(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        // 抓取残留自愈（见 LuckySelectorEntity#healOrphanedCapture）：必须放在维度判断之前——
        // 选择器在哪个维度都可能被抓，而这个 Mixin 是本模组唯一一个「每只生物每刻都会经过」的地方
        // （ServerLevel.java:403-410 每刻对每个实体调 checkDespawn）。
        // 先用两个标志位筛：大多数生物既没有 NoAI 也没有关重力，读完两个字段就过去了，不会碰 NBT。
        if (self.isNoAi() && self.isNoGravity()) {
            cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity.healOrphanedCapture(self);
        }
        if (!LuckyDimensionMobs.isLuckyDimension(self.level())) {
            return; // 其它维度走原版那套
        }
        // 本维度完全接管：原版的"闲置消失 / 和平难度清除 / removeWhenFarAway"全部不生效
        ci.cancel();

        if (self.isPersistenceRequired() || self.isVehicle()) {
            return;
        }
        Entity nearest = self.level().getNearestPlayer(self, -1.0D);
        if (nearest == null) {
            return;
        }
        if (nearest.distanceToSqr(self) > LuckyDimensionMobs.DESPAWN_DISTANCE * LuckyDimensionMobs.DESPAWN_DISTANCE) {
            self.discard();
        }
    }
}
