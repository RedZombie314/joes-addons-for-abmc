package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>幸运方块"支援生物"</b>的统一管理（用户指定）。
 *
 * <h2>这是什么</h2>
 * 操控幸运选择器把一只生物"送到某位玩家身边"时，那只生物是从
 * {@code SupportLuckyBlock} 里放出来的（见 {@code SupportGift#spawnStoredMob}）。
 * 用户要求：<b>具备攻击性</b>的那种（比如 Bob）落地后，
 * <ul>
 *   <li>索敌对象<b>永远</b>是 {@link SupportOrbTargetGoal#ORB_RANGE} 格以内的<b>幸运核心（附体的玩家）</b>；</li>
 *   <li><b>永远不锁</b>"被幸运核心索敌的玩家" —— 那位是它在帮忙的人，不是敌人。</li>
 * </ul>
 *
 * <h2>三道保险（与附体召唤物那套同款做法）</h2>
 * <ol>
 *   <li>{@link #markAndInstall}：放出来的那一刻就清掉原版"见谁打谁"的目标 goal，挂上
 *       {@link SupportOrbTargetGoal}；</li>
 *   <li>{@link #tick}：每 0.5 秒扫一遍所有支援生物，确认那个 goal 还在（命令式注册的 goal
 *       <b>不进存档</b>，读档/区块重载/被变形复原都会丢），丢了就重装；</li>
 *   <li>{@link #forbidsTargeting}：挂在 {@code LivingChangeTargetEvent} 上的<b>硬闸</b> ——
 *       不管目标是谁塞进来的，只要"这名玩家正被某颗核心索敌"，就不许锁。</li>
 * </ol>
 *
 * <p>标记写在实体的 {@code persistentData} 里（随 NBT 落盘、只在服务端），
 * 这样"这只生物是支援送来的"这件事跨读档也认得出。
 */
public final class SupportMobControl {

    /** "这只是幸运方块支援送来的生物"标记。 */
    private static final String TAG_SUPPORT_MOB = "jafa_support_mob";

    /** 支援生物的索敌半径（格）：用户指定的 50。 */
    public static final double ORB_TARGET_RANGE = 50.0D;

    /**
     * "不许锁被核心索敌的玩家"那道硬闸的搜索半径（格）。
     * <p>
     * 比索敌半径大一圈：支援生物绝大多数时间都在自己那具核心的 50 格内，
     * 而"被核心索敌的玩家"可能正被另一具更远的核心追杀 —— 100 格足够覆盖整场战斗。
     */
    private static final double ORB_GATE_RANGE = 100.0D;

    private SupportMobControl() {
    }

    /**
     * 刚放出来一只"支援送来的生物"：够凶的（{@link #isAggressive}）登记 + 装专属索敌。
     * <p>
     * 由 {@code SupportGift#spawnStoredMob} 调用；不是生物（物品）或不够凶的（牛、猪……）直接忽略 ——
     * 它们的索敌本来也打不了核心，硬塞一个目标 goal 只会让它们莫名其妙地追着核心跑。
     */
    public static void markAndInstall(@Nullable Entity spawned) {
        if (!(spawned instanceof Mob mob) || !mob.isAlive()) {
            return;
        }
        if (!isAggressive(mob)) {
            return;
        }
        mob.getPersistentData().putBoolean(TAG_SUPPORT_MOB, true);
        installGoal(mob);
        ModMain.LOGGER.info("[支援] 送来的 {} 已登记为支援生物：只索敌 {} 格内的幸运核心（附体玩家），"
                + "且绝不锁被核心索敌的玩家",
            mob.getType().toShortString(), (int) ORB_TARGET_RANGE);
    }

    /**
     * 这只生物算不算"具备攻击性"（用户口径：比如 Bob）。
     * <p>
     * 两条判据任一成立即可：
     * <ul>
     *   <li>原版 {@link Enemy} 标记 —— 一切敌对生物（僵尸 Bob、骷髅、苦力怕……）；</li>
     *   <li>它自己已经带了目标 goal —— 覆盖"非敌对但会打架"的（铁傀儡、驯服的狼……）。</li>
     * </ul>
     * 牛/猪/羊这类两条都不成立，直接跳过。
     */
    private static boolean isAggressive(Mob mob) {
        return mob instanceof Enemy || !mob.targetSelector.getAvailableGoals().isEmpty();
    }

    /** 这只实体是不是"幸运方块支援送来的生物"（传 null 一律 false）。 */
    public static boolean isSupportMob(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_SUPPORT_MOB);
    }

    /**
     * 装上"只打幸运核心"的索敌：先清掉原版那套"见谁打谁"，再挂 {@link SupportOrbTargetGoal}。
     * <p>
     * 必须清干净的理由和附体召唤物那条一样：僵尸的原版目标 goal 会自己去找玩家/村民/铁傀儡，
     * 那样"索敌对象永远是附近的核心"这条要求就会被它自己覆盖掉。
     */
    public static void installGoal(Mob mob) {
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.setTarget(null);
        mob.targetSelector.addGoal(0, new SupportOrbTargetGoal(mob));
    }

    /**
     * 这只支援生物是不是<b>绝对不许</b>把 {@code target} 设成目标（用户指定：
     * "永远不会索敌被幸运核心索敌的玩家"）。
     */
    public static boolean forbidsTargeting(Entity attacker, @Nullable Entity target) {
        if (!isSupportMob(attacker) || !(target instanceof Player player) || attacker == target) {
            return false;
        }
        return isTargetedByAnyOrb(attacker, player);
    }

    /** 这名玩家此刻是不是正被附近某具"被核心附体的玩家"（附体空壳）索敌。 */
    private static boolean isTargetedByAnyOrb(Entity from, Player player) {
        if (!(from.level() instanceof ServerLevel level)) {
            return false;
        }
        List<PlayerShellEntity> shells = level.getEntitiesOfClass(PlayerShellEntity.class,
            from.getBoundingBox().inflate(ORB_GATE_RANGE),
            shell -> shell.isAlive() && shell.isOrbAttached());
        for (PlayerShellEntity shell : shells) {
            if (shell.getTarget() == player) {
                return true;
            }
        }
        return false;
    }

    /** 定期重扫的间隔（刻）：0.5 秒。 */
    private static final int RESCAN_INTERVAL_TICKS = 10;

    /** 上一次重扫的游戏刻（初值写成负间隔：第一次一定扫，且不会像 Long.MIN_VALUE 那样溢出）。 */
    private static long lastRescanTick = -RESCAN_INTERVAL_TICKS;

    /** 当前这一局的服务器；换了就清状态（静态字段跨存档存活）。 */
    @Nullable
    private static MinecraftServer currentServer;

    /**
     * 由服务端 tick 钩子（{@code ModMain#onServerTickPre}）每刻调用：每 0.5 秒确认一遍
     * 所有支援生物的"只打核心"索敌还在，丢了就重装（见 {@link #installGoal}）。
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (server != currentServer) {
            currentServer = server;
            lastRescanTick = -RESCAN_INTERVAL_TICKS;
        }
        if (server.getTickCount() - lastRescanTick < RESCAN_INTERVAL_TICKS) {
            return;
        }
        lastRescanTick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                // 这个整合包里 getAllEntities() 会混进 null 元素，逐个判空
                if (entity == null || !isSupportMob(entity)) {
                    continue;
                }
                if (entity instanceof Mob mob && mob.isAlive()) {
                    ensureGoal(mob);
                }
            }
        }
    }

    /** 确认这只支援生物的专属索敌还在，丢了就重装。 */
    public static void ensureGoal(Mob mob) {
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SupportOrbTargetGoal) {
                return;   // 还在
            }
        }
        installGoal(mob);
        // 只在"真的需要重装"时打一条：正常游戏里它只会在读档 / 区块重载之后出现
        if (mayLog(mob)) {
            ModMain.LOGGER.info("[支援] 重装支援生物索敌: {}", mob.getType().toShortString());
        }
    }

    /** "阻止索敌"日志的节流间隔（刻）：5 秒。 */
    private static final int BLOCK_LOG_INTERVAL_TICKS = 100;

    /** 每只支援生物上次打日志的游戏刻。 */
    private static final Map<UUID, Long> LAST_BLOCK_LOG_TICK = new HashMap<>();

    /** 打一条"已阻止支援生物索敌它要帮的那位玩家"的日志（按实体节流）。 */
    public static void logBlockedTarget(Entity summon, Entity target) {
        if (!mayLog(summon)) {
            return;
        }
        ModMain.LOGGER.info("[支援] 已阻止支援生物 {} 索敌 {} —— 那位正被幸运核心索敌，是它要帮的人",
            summon.getType().toShortString(), target.getType().toShortString());
    }

    /** 这只实体现在能不能打日志（同一只 5 秒内只放一条，防止刷屏）。 */
    private static boolean mayLog(Entity entity) {
        long now = entity.level().getGameTime();
        Long last = LAST_BLOCK_LOG_TICK.get(entity.getUUID());
        if (last != null && now - last < BLOCK_LOG_INTERVAL_TICKS) {
            return false;
        }
        if (LAST_BLOCK_LOG_TICK.size() > 64) {
            LAST_BLOCK_LOG_TICK.clear();   // 变形复原会换新 UUID，这张表不能无限长
        }
        LAST_BLOCK_LOG_TICK.put(entity.getUUID(), now);
        return true;
    }
}
