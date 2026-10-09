package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>"附近有玩家在蹲起（T-bag）"的观测表</b>（服务端每刻更新一遍），
 * 供附体空壳"跟着一起蹲"用（见 {@code PlayerShellEntity#tickCrouchMimic}）。
 *
 * <h2>为什么要单独做一张表</h2>
 * <ol>
 *   <li><b>判定"这是 T-bag 而不是随便蹲一下"</b>：时间窗内蹲起 {@value #MIN_CROUCHES} 次以上才算 ——
 *       单纯按住 Shift 走路不该让空壳跟着蹲；</li>
 *   <li><b>把"发起者的节奏"量出来</b>：用户指定空壳的等待时间要跟着发起者走，所以要记下它最近一次
 *       "按住多久"（{@code holdTicks}）与"松开多久后又按下"（{@code gapTicks}）；</li>
 *   <li><b>只更新一次</b>：几具空壳同时看着同一个玩家时，各记各的会把一次"按下"记成好几次，
 *       节奏全乱。放在这里每刻统一走一遍，空壳只读。</li>
 * </ol>
 *
 * <p>时间一律用 {@code level.getGameTime()}（各维度共享同一份世界时间）。
 */
public final class CrouchMimic {

    /** 时间窗内至少蹲起这么多次，才算"在 T-bag"（单纯蹲一下不算）。 */
    private static final int MIN_CROUCHES = 2;

    /** 判定时间窗（刻）：2 秒。蹲起间隔超过它就算"换了一轮"。 */
    private static final int WINDOW_TICKS = 40;

    /** 空壳模仿时"按住/松开"时长的上下限（刻）：发起者给的节奏会被夹进这个范围，防止 0 刻抖动。 */
    public static final int HOLD_MIN_TICKS = 2;
    public static final int HOLD_MAX_TICKS = 40;
    public static final int GAP_MIN_TICKS = 2;
    public static final int GAP_MAX_TICKS = 40;

    /** 默认节奏（刻）：发起者的数据还没量全时先用它（0.3 秒，正常 T-bag 的速度）。 */
    public static final int DEFAULT_HOLD_TICKS = 6;
    public static final int DEFAULT_GAP_TICKS = 6;

    /** 认为"附近"的距离（格）：与空壳索敌用的那一档一致。 */
    public static final double NEARBY_RADIUS = 16.0D;

    /** 玩家 UUID → 观测记录。玩家下线/换维度时由 {@link #tick} 顺手清掉。 */
    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private CrouchMimic() {
    }

    /** 一位玩家的蹲起观测记录。 */
    private static final class Watch {
        boolean crouching;
        long pressTick;              // 本次"按下"的世界刻
        long lastReleaseTick = -1L;  // 上一次"松开"的世界刻
        int holdTicks = DEFAULT_HOLD_TICKS;   // 最近一次按住多久
        int gapTicks = DEFAULT_GAP_TICKS;     // 最近一次"松开→再按下"隔了多久
        int crouchCount;             // 当前时间窗内蹲起了几次
        long windowStartTick;        // 当前窗口的起点
        long lastCrouchTick = -1L;   // 最近一次"按下"的世界刻（空壳靠它判断"又蹲了一次"）
        long tbagUntilTick;          // T-bag 状态至少保留到这一刻
        boolean tbagging;
    }

    /** 每服务端刻更新一遍（由 {@code ModMain#onServerTickPre} 调）。 */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Watch watch = WATCHES.computeIfAbsent(player.getUUID(), key -> new Watch());
            // 旁观/死亡/骑在载具上都不算（骑乘时客户端不让下蹲）
            boolean down = player.isCrouching() && !player.isSpectator() && player.isAlive();

            if (down != watch.crouching) {
                if (down) {
                    // 按下：先记下"上一次松开到这次按下"的间隔，再累加窗口内的次数
                    if (watch.lastReleaseTick >= 0L) {
                        watch.gapTicks = clamp((int) (now - watch.lastReleaseTick), GAP_MIN_TICKS, GAP_MAX_TICKS);
                    }
                    watch.pressTick = now;
                    watch.lastCrouchTick = now;
                    if (now - watch.windowStartTick > WINDOW_TICKS) {
                        watch.windowStartTick = now;
                        watch.crouchCount = 0;
                    }
                    watch.crouchCount++;
                    if (watch.crouchCount >= MIN_CROUCHES) {
                        watch.tbagging = true;
                        watch.tbagUntilTick = now + WINDOW_TICKS;
                    }
                } else {
                    // 松开：量出这一按住了多久
                    watch.holdTicks = clamp((int) (now - watch.pressTick), HOLD_MIN_TICKS, HOLD_MAX_TICKS);
                    watch.lastReleaseTick = now;
                }
                watch.crouching = down;
            }

            // 窗口过去了还没再蹲 → 不再算 T-bag
            if (watch.tbagging && now > watch.tbagUntilTick) {
                watch.tbagging = false;
            }
        }
        // 表很小，但换存档/大量玩家进出时也别无限涨
        if (WATCHES.size() > 64) {
            WATCHES.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
    }

    /** 这位玩家现在算不算"在蹲起（T-bag）"。 */
    public static boolean isTBagging(ServerPlayer player) {
        Watch watch = WATCHES.get(player.getUUID());
        return watch != null && watch.tbagging;
    }

    /** 这位玩家最近一次"按下下蹲键"的世界刻（没有记录时返回 -1）。空壳靠它判断"又蹲了一次"。 */
    public static long lastCrouchTick(ServerPlayer player) {
        Watch watch = WATCHES.get(player.getUUID());
        return watch == null ? -1L : watch.lastCrouchTick;
    }

    /** 发起者的节奏：按住多久（{@code holdTicks}）、松开后停多久（{@code gapTicks}），单位刻。 */
    public record Rhythm(int holdTicks, int gapTicks) {
    }

    /** 读某位玩家的节奏（没记录时给默认值）。 */
    public static Rhythm rhythmOf(ServerPlayer player) {
        Watch watch = WATCHES.get(player.getUUID());
        return watch == null
            ? new Rhythm(DEFAULT_HOLD_TICKS, DEFAULT_GAP_TICKS)
            : new Rhythm(watch.holdTicks, watch.gapTicks);
    }

    /**
     * 找出"这具空壳附近正在 T-bag"的所有玩家（同维度、{@value #NEARBY_RADIUS} 格内）。
     */
    public static java.util.List<ServerPlayer> nearbyTBaggingPlayers(PlayerShellEntity shell) {
        if (!(shell.level() instanceof ServerLevel level) || level.getServer() == null) {
            return java.util.List.of();
        }
        java.util.List<ServerPlayer> found = new java.util.ArrayList<>(2);
        double maxDist = NEARBY_RADIUS * NEARBY_RADIUS;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || !isTBagging(player)) {
                continue;
            }
            if (player.distanceToSqr(shell) <= maxDist) {
                found.add(player);
            }
        }
        return found;
    }

    /**
     * 从"附近正在 T-bag 的玩家"里<b>随机</b>挑一位（用户指定：有好几个人在蹲时，空壳随机看其中一个）。
     * <p>没找到返回 null（那就别跟了）。
     */
    @Nullable
    public static ServerPlayer randomTBaggingPlayer(PlayerShellEntity shell) {
        java.util.List<ServerPlayer> found = nearbyTBaggingPlayers(shell);
        if (found.isEmpty()) {
            return null;
        }
        return found.get(shell.getRandom().nextInt(found.size()));
    }

    /** 这位玩家还在不在"附近"（空壳跟蹲期间用它决定要不要接受"重置次数"）。 */
    public static boolean isNearby(PlayerShellEntity shell, ServerPlayer player) {
        return player.level() == shell.level()
            && player.distanceToSqr(shell) <= NEARBY_RADIUS * NEARBY_RADIUS;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
