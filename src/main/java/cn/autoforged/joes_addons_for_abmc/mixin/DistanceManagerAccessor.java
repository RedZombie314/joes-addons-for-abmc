package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 开放 {@link DistanceManager} 的 {@code tickingTicketsTracker}（原 private）的读取访问。
 *
 * <h2>为什么要它</h2>
 * 原版判断一个区块"刻不刻"走的是 {@code DistanceManager#inEntityTickingRange} /
 * {@code inBlockTickingRange}，二者都只读这一个 {@link TickingTracker}
 * （{@code DistanceManager.java:256-262}）。而想往它里面挂票，公开 API 只有
 * {@code addRegionTicket}（它会<b>同时</b>往主票据表和这个表挂票，于是载入半径会被顺带放大），
 * 没有"只往计时表挂一张票"的入口 —— 而那正是原版给玩家做的事
 * （{@code DistanceManager#addPlayer} 里单独的一次 {@code tickingTicketsTracker.addTicket}，
 * 见 {@code DistanceManager.java:236}）。为了让玩家空壳的加载/计时范围与真人玩家**一模一样**，
 * 这里开一个 accessor 拿到它，再照抄原版那一张票。
 *
 * <p>写法与既有的 {@link MobPersistenceAccessor}、{@link ExperienceOrbAccessor} 一致。</p>
 */
@Mixin(DistanceManager.class)
public interface DistanceManagerAccessor {

    @Accessor("tickingTicketsTracker")
    TickingTracker jafa_tickingTicketsTracker();
}
