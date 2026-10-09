package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.network.OrbHookChainPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 附体空壳的<b>铁抓钩拉扯</b>（用户指定）：铁抓钩签出手之后，这一整段"甩链 → 收链"的过程在这里推进。
 *
 * <h2>一边拉、一边盯三件事</h2>
 * 会话每刻做四件事：
 * <ol>
 *   <li><b>拉</b>：按"玩家 → 空壳"方向给玩家一个固定的拉扯速度（{@link #PULL_SPEED} 格/刻）。
 *       玩家的位移是客户端算的，所以这里除了改服务端实体自己的速度，还要
 *       {@link ClientboundSetEntityMotionPacket 把速度发给他}—— 与玩家挨击退时同一条路
 *       （见 {@code ModMain} 里那条"物理维度反冲"用的也是这个包）。</li>
 *   <li><b>拉到就松</b>：距离进到 {@link #ARRIVE_DISTANCE}（= 近战距离 3 格）以内 → 收链，
 *       并让空壳<b>立刻重抽一次近战</b>（"拽过来就打"）。</li>
 *   <li><b>被方块挡住就中断</b>（用户指定）：两种都算 —— ①视线被方块挡断
 *       （用空壳那把尺子 {@link PlayerShellEntity#shouldSeePossessionTarget}，它只被方块碰撞箱挡住）；
 *       ②玩家被方块卡住、连续 {@link #STUCK_TICKS} 刻一点都没能拉近。
 *       中断时立刻让空壳重抽一次近战（"AI 会立刻抽取下一次近战攻击"）。</li>
 *   <li><b>兜底</b>：{@link #MAX_PULL_TICKS} 刻还没拉到就收链（免得铁链挂在半空）。</li>
 * </ol>
 *
 * <h2>视觉</h2>
 * 铁链本身不在服务端做实体，只把"空壳 id + 玩家 id"发给附近玩家
 * （{@link OrbHookChainPayload}），客户端照着这两个实体的实时位置画一串铁链 ——
 * 与玩家自己用铁抓钩时那条链同一套贴图与画法（见 {@code ChainBeamClient} / {@code ClientEvents}）。
 * 拉扯期间每 {@link #RESYNC_INTERVAL_TICKS} 刻重发一次，免得中途走进视野的玩家看不到。
 */
public final class OrbHookPull {

    /** 拉扯最长持续（刻）：2 秒。 */
    private static final int MAX_PULL_TICKS = 40;

    /** 拉到这个距离以内就算"已经拉到近战距离"，收链（= 近战距离 3 格）。 */
    private static final double ARRIVE_DISTANCE = OrbPossessedAttackEvents.MELEE_RANGE;

    /**
     * 每刻施加给玩家的拉扯速度（格/刻）：24 格/秒。
     * <p>
     * 比原版击退（约 0.4 格/刻）狠得多 —— 这是"收链"，不是打一下，得让玩家挣不脱，
     * 从二十几格拉回 3 格大约十几刻（0.5~0.8 秒），看得清也不拖沓。
     */
    private static final double PULL_SPEED = 1.2D;

    /** 判定"玩家被方块卡住"的连续刻数：这么多刻都没能更近一点就中断。 */
    private static final int STUCK_TICKS = 8;

    /** 单刻至少接近多少格才算"在往回收"（格）：比这还小就当这一发没拉动。 */
    private static final double PROGRESS_EPSILON = 0.02D;

    /** 把铁链发给多大范围内的玩家（格）：要覆盖整条最多 24 格的链，外加玩家跑动的余量。 */
    private static final double BROADCAST_RADIUS = 64.0D;

    /** 拉扯期间每隔多少刻重发一次铁链（给中途进入视野的玩家补上）。 */
    private static final int RESYNC_INTERVAL_TICKS = 10;

    /** 每个空壳最多同时有一条铁链：按空壳 UUID 记会话（它的目标只有一个，够用）。 */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private OrbHookPull() {
    }

    /** 一次拉扯会话。 */
    private static final class Session {
        /** 被拉的玩家。 */
        UUID targetUuid;
        int targetId;
        /** 这次拉扯的截止游戏刻。 */
        long endTick;
        /** 上一次记录的最近距离（只减不增）：用来判断"有没有真的被拉近"。 */
        double bestDistance;
        /** 连续多少刻没能更近一点（见 {@link #STUCK_TICKS}）。 */
        int stuckTicks;
        /** 上次重发铁链的游戏刻。 */
        long lastSyncTick;
    }

    /** 这具空壳此刻是不是正在拉人（供 {@code PlayerShellEntity} 在拉扯期间不重抽签）。 */
    public static boolean isPulling(PlayerShellEntity shell) {
        return SESSIONS.containsKey(shell.getUUID());
    }

    /**
     * 甩出铁链：登记一次拉扯会话并通知客户端画链。
     * <p>
     * 由 {@code OrbPossessedAttackEvents.HookPullEvent#run} 调用（音效那里已经播过）。
     */
    public static void start(PlayerShellEntity shell, Player target) {
        if (!(shell.level() instanceof ServerLevel level)) {
            return;
        }
        if (SESSIONS.containsKey(shell.getUUID())) {
            return;   // 一条链一次：已经在拉人就不重开
        }
        Session session = new Session();
        session.targetUuid = target.getUUID();
        session.targetId = target.getId();
        session.endTick = level.getGameTime() + MAX_PULL_TICKS;
        session.bestDistance = shell.distanceTo(target);
        session.lastSyncTick = level.getGameTime();
        SESSIONS.put(shell.getUUID(), session);
        broadcast(level, shell, target);
        ModMain.LOGGER.info("[铁抓钩] {} 朝 {} 甩出铁链（距离 {} 格）",
            shell.getType().toShortString(), target.getGameProfile().getName(),
            String.format("%.1f", session.bestDistance));
    }

    /**
     * 每游戏刻推进所有拉扯会话（由 {@code ModMain#onServerTickPre} 调用）。
     */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty() || server == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            PlayerShellEntity shell = findShell(server, entry.getKey());
            // 空壳没了 / 不再附体：铁链直接收掉（客户端也会因为实体找不到而自己清掉）
            if (shell == null || !shell.isAlive() || !shell.isOrbAttached()) {
                it.remove();
                clearVisual(shell, session);
                continue;
            }
            Entity targetEntity = shell.level().getEntity(session.targetId);
            if (!(targetEntity instanceof Player player) || !player.isAlive()) {
                it.remove();
                clearVisual(shell, session);
                continue;
            }
            // 目标切创造/旁观（或成了"被保护玩家"）：不该再拖，直接收链（不重抽，交给正常索敌放人）
            if (!shell.canStillAttackTarget(player)) {
                it.remove();
                clearVisual(shell, session);
                continue;
            }
            // 空壳中途换了索敌目标（玩家跑掉、旁边冒出更好的目标）：这条链也就没有意义了，收掉
            if (shell.getTarget() != player) {
                it.remove();
                clearVisual(shell, session);
                continue;
            }
            ServerLevel level = (ServerLevel) shell.level();

            // ① 视线被方块挡断 → 中断 + 立刻重抽近战（用户指定的"被方块挡住"）
            if (!shell.shouldSeePossessionTarget(player)) {
                it.remove();
                finish(shell, session, "铁链被方块挡住（看不见玩家）", true);
                continue;
            }

            double distance = shell.distanceTo(player);

            // ② 已经拉进近战距离 → 收链 + 立刻重抽近战（"拽过来就打"）
            if (distance <= ARRIVE_DISTANCE) {
                it.remove();
                finish(shell, session, "已拉到近战距离 " + String.format("%.1f", distance) + " 格", false);
                continue;
            }

            // ③ 超时兜底
            if (level.getGameTime() >= session.endTick) {
                it.remove();
                broadcastStop(level, shell, player);
                ModMain.LOGGER.info("[铁抓钩] 拉扯超时（{} 刻），收链：距目标 {} 格",
                    MAX_PULL_TICKS, String.format("%.1f", distance));
                continue;
            }

            // ④ 拉
            pull(shell, player);

            // ⑤ 玩家被方块卡住（一直没能更近）→ 同样算"被方块挡住"→ 中断 + 立刻重抽近战
            if (distance >= session.bestDistance - PROGRESS_EPSILON) {
                session.stuckTicks++;
            } else {
                session.stuckTicks = 0;
                session.bestDistance = distance;
            }
            if (session.stuckTicks >= STUCK_TICKS) {
                it.remove();
                finish(shell, session, "玩家被方块挡住（连续 " + STUCK_TICKS + " 刻没能拉近）", true);
                continue;
            }

            // ⑥ 定期重发铁链（拉扯中的新观众也能看到）
            if (level.getGameTime() - session.lastSyncTick >= RESYNC_INTERVAL_TICKS) {
                session.lastSyncTick = level.getGameTime();
                broadcast(level, shell, player);
            }
        }
    }

    /**
     * 把玩家朝空壳拉一步。
     * <p>
     * 玩家不吃摔落伤害（{@code fallDistance} 清零）：被铁链拖着走不该摔伤。
     * 速度同步那一句与玩家挨击退同一条路 —— 服务端改 {@code deltaMovement} 只影响服务端自己的模拟，
     * 玩家客户端得靠这个包才会真的被拽动。
     */
    private static void pull(PlayerShellEntity shell, Player player) {
        Vec3 toward = shell.getEyePosition().subtract(player.getEyePosition());
        if (toward.lengthSqr() < 1.0E-6D) {
            return;
        }
        player.setDeltaMovement(toward.normalize().scale(PULL_SPEED));
        player.fallDistance = 0.0F;
        player.hasImpulse = true;
        player.hurtMarked = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }

    /**
     * 收链收场：记日志 + 让空壳<b>立刻重抽一次近战</b>（用户指定）。
     *
     * @param interrupted true = 被方块挡住而中断（"AI 会立刻抽取下一次近战攻击"那句）；
     *                    false = 正常把玩家拉到了近战距离（接下来当然是接着打近战）
     */
    private static void finish(PlayerShellEntity shell, Session session, String reason, boolean interrupted) {
        if (shell.level() instanceof ServerLevel level) {
            Entity target = level.getEntity(session.targetId);
            broadcastStop(level, shell, target);
            if (interrupted) {
                // 铁链崩断的一声（正常收链不补音效，链本来就是悄悄收回来的）
                level.playSound(null, shell.getX(), shell.getY(), shell.getZ(),
                    SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 1.1F);
            }
        }
        shell.requestImmediateMeleeRedraw();
        ModMain.LOGGER.info(interrupted
                ? "[铁抓钩] 拉扯中断（{}），立刻重抽下一次近战攻击：{}"
                : "[铁抓钩] 拉扯结束（{}），立刻重抽下一次近战攻击：{}",
            reason, shell.getType().toShortString());
    }

    /** 会话结束但没有"中断"语义（超时/目标没了）时，只把客户端的链清掉。 */
    private static void clearVisual(PlayerShellEntity shell, Session session) {
        if (shell == null) {
            return;   // 空壳都没了：客户端会因为实体找不到而自己清掉
        }
        if (shell.level() instanceof ServerLevel level) {
            broadcastStop(level, shell, level.getEntity(session.targetId));
        }
    }

    /** 通知附近玩家："这条链挂在这两个实体之间"。 */
    private static void broadcast(ServerLevel level, PlayerShellEntity shell, Player target) {
        PacketDistributor.sendToPlayersNear(level, null, shell.getX(), shell.getY(), shell.getZ(),
            BROADCAST_RADIUS, new OrbHookChainPayload(shell.getId(), target.getId()));
    }

    /** 通知附近玩家：这条链没了。 */
    private static void broadcastStop(ServerLevel level, PlayerShellEntity shell, Entity target) {
        // 终点用链的另一头（玩家）所在的位置广播：玩家可能已经被拉到十几格之外
        double x = target != null ? (shell.getX() + target.getX()) * 0.5D : shell.getX();
        double y = target != null ? (shell.getY() + target.getY()) * 0.5D : shell.getY();
        double z = target != null ? (shell.getZ() + target.getZ()) * 0.5D : shell.getZ();
        // 按空壳 id 单独收：同一时刻可能有好几具空壳在钩不同的人
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, BROADCAST_RADIUS,
            OrbHookChainPayload.remove(shell.getId()));
    }

    /** 按 UUID 找那具空壳（任意维度）。 */
    private static PlayerShellEntity findShell(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) instanceof PlayerShellEntity shell) {
                return shell;
            }
        }
        return null;
    }
}
