package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「这个物品/生物已经被某个幸运选择器锁定了」的登记处。
 *
 * <h3>为什么需要它</h3>
 * 选择器的自动收集 AI（{@link SelectorCollectGoal}）和 {@code /jafa seek} 都是"各自独立地找最近的目标"，
 * 彼此不知道对方在看什么。于是同一个掉落物往往被好几只选择器同时锁定、一起飞过去抢；
 * 抢输的那些白飞一趟，抢赢的那只把东西收走后，其余的还得重新找。需求要的是：
 * <b>一个目标一旦被某只选择器锁定/抓取，其它选择器就不再选它</b>。
 *
 * <h3>认领的时机</h3>
 * 在"决定抓它"的那一刻认领（{@code startSeekMob} / {@code startSeekItem}），也就是锁定，而不是等飞到它头上。
 * 释放有三个出口：抓取被中止（{@link LuckySelectorEntity#abortSeek()}）、
 * send 收尾（东西已经进名单、从世界上没了）、以及选择器自己被移除。
 *
 * <h3>为什么是"临时"的</h3>
 * 认领只是一次抓取期间的占位，<b>不写进实体 NBT</b>：它是服务端主线程上的一个静态表，
 * 退出存档、跨维度、跨会话都自然清空。这样就不会出现"选择器没了、目标被永久拉黑"的存档污染。
 * 万一某个认领的清理路径漏了（例如选择器连同区块一起被卸载），还有 {@link #TIMEOUT_TICKS} 兜底：
 * 超时的记录视为失效，并且会在下次查询时被就地清掉。
 */
public final class SelectorTargetClaims {

    /**
     * 一条认领记录。
     *
     * @param claimerId 占着它的选择器实体 id
     * @param dimension 认领时所在维度（跨维度撞 UUID 时用来判失效）
     * @param tick      认领时的游戏刻（超时用）
     */
    private record Claim(int claimerId, ResourceKey<Level> dimension, long tick) {
    }

    /** 目标 UUID -> 认领记录。只在服务端主线程读写，不需要同步。 */
    private static final Map<UUID, Claim> CLAIMS = new HashMap<>();

    /**
     * 认领有效期（刻）：900 = 45 秒。
     * <p>
     * 远大于一次完整抓取（4 倍速飞过去 + 抓 2 秒 + 游荡最多 10 秒 + send 1 秒），
     * 又短到"万一没人释放"时不会真的把目标永久拉黑。
     */
    private static final long TIMEOUT_TICKS = 900;

    private SelectorTargetClaims() {
    }

    /**
     * 试着把目标记在自己名下。
     *
     * @return 认领成功返回 true；已经被别的选择器占着（且记录没失效）返回 false
     */
    public static boolean claim(Entity target, LuckySelectorEntity claimer) {
        if (isClaimedByOther(target, claimer)) {
            return false;
        }
        CLAIMS.put(target.getUUID(), new Claim(claimer.getId(), target.level().dimension(),
            target.level().getGameTime()));
        return true;
    }

    /** 释放自己占的目标（只有占用者本人能释放，别人调用是空操作）。 */
    public static void release(Entity target, LuckySelectorEntity claimer) {
        Claim claim = CLAIMS.get(target.getUUID());
        if (claim != null && claim.claimerId() == claimer.getId()) {
            CLAIMS.remove(target.getUUID());
        }
    }

    /** 释放某只选择器占着的全部目标（它被移除时的兜底）。 */
    public static void releaseAllOf(LuckySelectorEntity claimer) {
        if (CLAIMS.isEmpty()) {
            return;
        }
        CLAIMS.values().removeIf(claim -> claim.claimerId() == claimer.getId());
    }

    /** 目标是不是被<b>别的</b>选择器占着（自己占的不算）。顺带把失效的旧记录清掉。 */
    public static boolean isClaimedByOther(Entity target, LuckySelectorEntity self) {
        Claim claim = CLAIMS.get(target.getUUID());
        if (claim == null || claim.claimerId() == self.getId()) {
            return false;
        }
        if (isStale(target, claim)) {
            CLAIMS.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 记录是否已经失效：维度对不上、超时、或者占着它的选择器已经不在了。 */
    private static boolean isStale(Entity target, Claim claim) {
        if (!claim.dimension().equals(target.level().dimension())) {
            return true;
        }
        if (target.level().getGameTime() - claim.tick() > TIMEOUT_TICKS) {
            return true;
        }
        return !(target.level().getEntity(claim.claimerId()) instanceof LuckySelectorEntity);
    }
}
