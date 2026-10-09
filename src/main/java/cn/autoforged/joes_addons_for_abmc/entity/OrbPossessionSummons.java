package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * "幸运核心附体的召唤物"的统一标记。
 *
 * <h2>为什么要一个统一标记</h2>
 * 被附体的空壳会带着一帮东西一起打架：召唤出来的 <b>Bob</b>，
 * 以及当成弹药射出去的爆炸性<b>苦力怕/猫/僵尸</b>。这些东西彼此之间绝不能互相索敌 ——
 * 否则 Bob 会追着刚射出去的苦力怕砍、两具空壳会互相打对方的召唤物，白白浪费攻击窗口
 * （爆炸弹还免疫伤害，打了也白打）。所以凡是被附体体系召唤出来的东西都打上同一个标记，
 * 索敌时<b>一律排除任何带标记的实体</b>；将来再加召唤物，只要在生成处 mark 一下即可。
 *
 * <p>标记用 {@code persistentData}（随实体 NBT 落盘、只在服务端，不占同步字段）：
 * Bob 是原版僵尸、爆裂猫/僵尸是原版生物的派生类，没法统统实现同一个接口，
 * 而{@code persistentData} 对任何实体都适用。
 *
 * <p>标记里还记了两件事：
 * <ul>
 *   <li>召唤它的那颗核心 UUID —— 附体结束时按它一次性清场；</li>
 *   <li>它的盟友（那具"玩家"空壳）UUID —— Bob 靠它实现<a href="#share">共享索敌</a>。</li>
 * </ul>
 *
 * <p>本类还负责"共享索敌"这件事的两道保险：
 * {@link #ensureSharedTarget}（事后补做改造，命令式的 goal 不进存档）
 * 与 {@link #forbidsTargeting}（硬闸：召唤物无论如何都不许锁自己人）。
 */
public final class OrbPossessionSummons {

    /** 是不是被附体召唤出来的东西。 */
    private static final String TAG_SUMMON = "jafa_orb_summon";
    /** 召唤它的核心 UUID（附体结束清场用）。 */
    private static final String TAG_ORB = "jafa_orb_summon_orb";
    /** 它的盟友：那具被附体的"玩家"空壳 UUID（共享索敌用）。 */
    private static final String TAG_ALLY = "jafa_orb_summon_ally";
    /** 额外标记：这只是什么角色（用来分别数当前存活数量、给各类召唤设上限）。 */
    private static final String TAG_ROLE = "jafa_orb_summon_role";

    /** 角色：Bob（僵尸帮手）。 */
    public static final String ROLE_BOB = "bob";
    /** 角色：骷髅马骑士的坐骑。 */
    public static final String ROLE_KNIGHT_HORSE = "knight_horse";
    /** 角色：骷髅马骑士的骑手。 */
    public static final String ROLE_KNIGHT_RIDER = "knight_rider";
    /** 角色：己方凋灵。 */
    public static final String ROLE_WITHER = "wither";
    /** 角色：暮色森林僵尸权杖召唤的忠诚僵尸（装了暮色才有）。 */
    public static final String ROLE_TF_LOYAL_ZOMBIE = "tf_loyal_zombie";

    private OrbPossessionSummons() {
    }

    /** 把一个实体登记为"某颗核心的召唤物"，并指定它的盟友空壳。 */
    public static void mark(Entity summon, UUID orbUuid, @Nullable UUID allyUuid) {
        summon.getPersistentData().putBoolean(TAG_SUMMON, true);
        if (orbUuid != null) {
            summon.getPersistentData().putUUID(TAG_ORB, orbUuid);
        }
        if (allyUuid != null) {
            summon.getPersistentData().putUUID(TAG_ALLY, allyUuid);
        }
    }

    /** 这个实体是不是（任意一颗核心的）附体召唤物。传 null 一律返回 false（遍历实体集合时方便直接问）。 */
    public static boolean isSummon(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_SUMMON);
    }

    /**
     * 召唤物的盟友（被附体空壳）UUID；不是召唤物/没记过/传 null 则返回 null。
     * <p>和 {@link #isSummon} 同一约定：调用方常常是"某个伤害来源身上随便取一个实体"，
     * 取到 null 是家常便饭（无实体的伤害源），这里必须容忍。
     */
    @Nullable
    public static UUID allyOf(@Nullable Entity summon) {
        return summon == null || !summon.getPersistentData().hasUUID(TAG_ALLY)
            ? null : summon.getPersistentData().getUUID(TAG_ALLY);
    }

    /**
     * 召唤物属于哪颗核心；不是召唤物/没记过/传 null 则返回 null（同化机制靠它追溯"原初"）。
     * <p><b>必须容忍 null</b>：{@code OrbAssimilation#attackerShellOf} 是从
     * {@code source.getDirectEntity()/getEntity()} 里随便取一个来问的，而大量伤害源两者都是 null
     * （摔落、虚空、命令造成的无实体伤害……）。6.4.31 就是在这里 NPE 崩溃的——
     * 同文件里 {@link #isSummon} 早就写了"传 null 一律返回 false"，这里漏了同一条约定。
     */
    @Nullable
    public static UUID orbOf(@Nullable Entity summon) {
        return summon == null || !summon.getPersistentData().hasUUID(TAG_ORB)
            ? null : summon.getPersistentData().getUUID(TAG_ORB);
    }

    /** 追加标记：这只是什么角色（Bob / 骷髅马骑士的坐骑 / 骑手……）。 */
    public static void markRole(Entity summon, String role) {
        summon.getPersistentData().putString(TAG_ROLE, role);
        // 登记一条日志：把"谁召唤的、归属哪颗核心/哪具壳"写清楚。
        // 排查"空壳没了召唤物却不消失"这类问题时，靠的就是它和下面几条 [召唤物] 日志对时间线。
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[召唤物] 登记: {} role={} orb={} ally={}",
            summon.getType().toShortString(), role, orbOf(summon), allyOf(summon));
    }

    /** 这只召唤物的角色标记；没记过/传 null 返回空串。 */
    public static String roleOf(@Nullable Entity summon) {
        return summon == null ? "" : summon.getPersistentData().getString(TAG_ROLE);
    }

    /**
     * 哪些角色需要"共享索敌"改造（{@link OrbSummonTargetGoal}）。
     * <p>
     * 与召唤处一一对应：Bob、骷髅马骑士的<b>骑手</b>、暮色僵尸权杖的忠诚僵尸。
     * 骷髅马的<b>坐骑</b>本来就不挂目标 goal（它只是载具），爆炸性召唤物（苦力怕/猫/僵尸）
     * 只带标记、也不挂 —— 所以这里按角色白名单来，不给它们乱加。
     * 己方凋灵另由 {@link OrbAllyWitherControl} 管理（那里还要管副头），不在这里重复处理。
     */
    private static final java.util.Set<String> SHARED_TARGET_ROLES = java.util.Set.of(
        ROLE_BOB, ROLE_KNIGHT_RIDER, ROLE_TF_LOYAL_ZOMBIE);

    /**
     * 确认这只召唤物的"共享索敌"改造还在，丢了就重做。
     *
     * <h2>为什么必须自愈</h2>
     * 那套改造是<b>命令式</b>注册到 {@code targetSelector} 上的、<b>不进存档</b>：
     * <ul>
     *   <li>读档 / 区块卸载再加载 → 实体按自己的构造函数重建，原版"见谁打谁"的目标 goal 回来了；</li>
     *   <li><b>被变形药水变形又解除</b> → 变回来时同样是从 NBT 重建的新实例（见
     *       {@code ModMain#respawnTransmutedEntity}），改造一并丢失。</li>
     * </ul>
     * 两种情况下，召唤物都会转头去锁"离自己最近的活体" —— 十有八九就是那具附体空壳
     * （用户反馈：己方凋灵/僵尸/骷髅被变形解除后开始打自己人）。
     * 判定方式与 {@code OrbAllyWitherControl#ensureAllyBehavior} 完全一致：只看 goal 在不在。
     */
    public static void ensureSharedTarget(Entity summon) {
        if (!(summon instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) {
            return;
        }
        if (!SHARED_TARGET_ROLES.contains(roleOf(mob))) {
            return;
        }
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped
            : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof OrbSummonTargetGoal) {
                return;   // 改造还在
            }
        }
        UUID ally = allyOf(mob);
        // 优先问核心"你现在那具主空壳是谁"：壳被变形复原过的话 UUID 会换成新的，
        // 而召唤物身上记的还是召唤那一刻的旧壳（见 rebindAlly / VANISHED_ORBS 的注释）。
        UUID liveVessel = liveVesselOf(mob);
        if (liveVessel != null && !liveVessel.equals(ally)) {
            ally = liveVessel;
            mob.getPersistentData().putUUID(TAG_ALLY, liveVessel);
        }
        // 先清掉原版那套"见谁打谁"，再挂共享索敌；顺手把已经锁上的旧目标丢掉
        // （在清除之前它可能刚把空壳设成目标，不清就会残留到下一轮判定）
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.setTarget(null);
        if (ally != null) {
            mob.targetSelector.addGoal(0, new OrbSummonTargetGoal(mob, ally));
        }
        // 这条日志是"自愈发生过"的唯一证据：正常游戏中它只会在
        // 读档 / 区块重载 / 被变形解除之后出现（同一只最多 5 秒一条，避免异常情况下刷屏）。
        if (mayLog(mob)) {
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[召唤物] 重装共享索敌: {} role={} ally={}",
                mob.getType().toShortString(), roleOf(mob), ally);
        }
    }

    /**
     * 这只召唤物是不是<b>绝对不许</b>把 {@code target} 设成自己的目标。
     *
     * <h2>为什么除了"重装 goal"还要有这么一道硬闸</h2>
     * {@link #ensureSharedTarget} 是"事后补做改造"：它只能修好<b>我们已知的</b>重建路径。
     * 而召唤物的目标 goal 是命令式注册、不进存档的 —— 只要漏掉任何一条重建路径
     * （例如 {@code ModMain#revertLivingShell}：生物↔生物变形复原走的就是它，
     * 而 {@code respawnTransmutedEntity} 只管物品/方块形态），召唤物就会带着原版那套
     * "见谁打谁"复活；此时哪怕只挨了空壳自己一发范围伤害，原版
     * {@code HurtByTargetGoal} 也会把它锁死成"打那具空壳"。
     *
     * <p>所以这里从<b>目标设置</b>本身下手：凡是被附体体系召唤出来的东西，
     * 一律不许把"我们这一侧"（自己的盟友空壳、幸运核心、别的召唤物）设成目标。
     * 这条闸门与 goal 是否丢失无关，任何路径、任何模组塞进来的目标 goal 都绕不过去
     * （挂在 NeoForge 的 {@code LivingChangeTargetEvent} 上，见
     * {@code ModMain#onLivingChangeTarget}）。
     */
    public static boolean forbidsTargeting(Entity attacker, @Nullable Entity target) {
        if (!isSummon(attacker) || target == null) {
            return false;
        }
        if (attacker == target) {
            return true;   // 锁自己毫无意义
        }
        UUID ally = allyOf(attacker);
        if (ally != null && ally.equals(target.getUUID())) {
            return true;   // 自己的盟友：那具附体空壳
        }
        return OrbAllyWitherControl.isAllySide(target);
    }

    /** 索敌兜底日志的节流间隔（刻）：5 秒。 */
    private static final int BLOCK_LOG_INTERVAL_TICKS = 100;

    /** 每只召唤物上次打"阻止索敌"日志的游戏刻（按 UUID 记，避免每刻刷屏）。 */
    private static final java.util.Map<UUID, Long> LAST_BLOCK_LOG_TICK = new java.util.HashMap<>();

    /**
     * 问核心要"它现在那具主空壳"的 UUID（比召唤物身上记的那份可靠：壳被变形复原会换 UUID）。
     * 核心不在 / 不在附体状态就返回 null。
     */
    @Nullable
    private static UUID liveVesselOf(Entity summon) {
        UUID orbUuid = orbOf(summon);
        MinecraftServer server = summon.level().getServer();
        if (orbUuid == null || server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(orbUuid) instanceof OrbOfLuckEntity orb) {
                return orb.getVesselUuid();
            }
        }
        return null;
    }

    /** 打一条"已阻止召唤物索敌自己人"的日志（按实体节流，5 秒内只留一条）。 */
    public static void logBlockedTarget(Entity summon, Entity target) {
        if (!mayLog(summon)) {
            return;
        }
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[召唤物] 已阻止 {}（role={}）索敌自己人 {} —— 该召唤物带着原版\"见谁打谁\"的目标 goal，"
                + "若它本该有共享索敌，0.5 秒内会自愈重装",
            summon.getType().toShortString(), roleOf(summon), target.getType().toShortString());
    }

    /** 这只实体现在能不能打日志（同一只 5 秒内只放一条，防止异常情况下刷屏）。 */
    private static boolean mayLog(Entity entity) {
        long now = entity.level().getGameTime();
        Long last = LAST_BLOCK_LOG_TICK.get(entity.getUUID());
        if (last != null && now - last < BLOCK_LOG_INTERVAL_TICKS) {
            return false;
        }
        if (LAST_BLOCK_LOG_TICK.size() > 64) {
            // 每次变形复原都会换新 UUID，这张表不能无限长
            LAST_BLOCK_LOG_TICK.clear();
        }
        LAST_BLOCK_LOG_TICK.put(entity.getUUID(), now);
        return true;
    }

    /** 定期扫召唤物的间隔（刻）：0.5 秒。 */
    private static final int RESCAN_INTERVAL_TICKS = 10;

    /**
     * 上一次重扫的游戏刻。
     * <p>
     * 初值刻意写成 {@code -RESCAN_INTERVAL_TICKS} 而不是 {@code Long.MIN_VALUE}：
     * 判断是 {@code server.getTickCount() - lastRescanTick >= 间隔}，
     * 而 {@code 0L - Long.MIN_VALUE} 会溢出成负数、让重扫被永久卡死
     * （{@link OrbAllyWitherControl} 里记过这个坑）。
     */
    private static long lastRescanTick = -RESCAN_INTERVAL_TICKS;

    /** 当前这一局的服务器；换了就清状态（同 {@link OrbAllyWitherControl}，静态字段跨存档存活）。 */
    @Nullable
    private static MinecraftServer currentServer;

    /**
     * 由服务端 tick 钩子（{@code ModMain#onServerTickPre}）每刻调用：
     * 每 0.5 秒确认一遍所有召唤物的"共享索敌"还在，丢了就重装（见 {@link #ensureSharedTarget}）。
     * <p>
     * 为什么还要这条定期兜底：读档/区块重载是"静默"发生的，没有可挂的钩子；
     * 而且半秒的窗口里它们最多也就多走几步 —— {@link #forbidsTargeting} 那道闸门
     * （挂在 {@code LivingChangeTargetEvent} 上）保证这半秒里它们也<b>打不到自己人</b>。
     */
    public static void tickSummonGoals(MinecraftServer server) {
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
                // 这个整合包里 getAllEntities() 会混进 null 元素，逐个判空（同 discardAll 的注释）
                if (entity != null && isSummon(entity)) {
                    ensureSharedTarget(entity);
                }
            }
        }
        sweepOwnerless(server);
    }

    // ====================== 主人没了的召唤物：定期兜底清场 ======================

    /**
     * "连着几次重扫都没找到主人"的计数器（按召唤物 UUID）。
     * 见 {@link #sweepOwnerless}：连 {@link #OWNERLESS_STREAK_LIMIT} 次都找不到才动手。
     */
    private static final java.util.Map<UUID, Integer> OWNERLESS_STREAK = new java.util.HashMap<>();

    /** 连续几次重扫（每次 0.5 秒）都找不到主人 = 认定主人没了。约 2 秒。 */
    private static final int OWNERLESS_STREAK_LIMIT = 4;

    /**
     * 清掉"主人已经不在了"的召唤物。
     *
     * <h2>为什么除了钩子还要这一条</h2>
     * 钩子（{@code PlayerShellEntity#remove} / 附体结束时的清场）要求"我们真的收到了那个事件"，
     * 而空壳从世界里消失的花样很多（别的模组直接清、区块层面的处理、存档异常……），
     * 漏掉任何一条，召唤物就永远留在世界上。这里改为<b>只看事实</b>：
     * 每 0.5 秒扫一遍所有召唤物，凡是"核心与空壳<b>两个都</b>已经不在任何维度里"的，
     * 连续 {@link #OWNERLESS_STREAK_LIMIT} 次（约 2 秒，避开区块加载的空窗）就抹掉它，
     * 连带把它变形后的产物一并作废。
     * <p>
     * 与 {@link #VANISHED_ORBS} 的区别：那条名册是"我们知道它没了"，这条是"查不到它了"；
     * 两条同时存在，正是为了既不误杀（名册先复核）、也不漏杀（无主兜底）。
     */
    private static void sweepOwnerless(MinecraftServer server) {
        int destroyed = 0;
        java.util.List<String> detail = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || !isSummon(entity)) {
                    continue;
                }
                if (!ownIsGone(server, entity)) {
                    OWNERLESS_STREAK.remove(entity.getUUID());
                    continue;
                }
                int streak = OWNERLESS_STREAK.merge(entity.getUUID(), 1, Integer::sum);
                if (streak < OWNERLESS_STREAK_LIMIT) {
                    continue;
                }
                OWNERLESS_STREAK.remove(entity.getUUID());
                if (destroyed < 8) {
                    detail.add(entity.getType().toShortString()
                        + "(role=" + roleOf(entity) + ",orb=" + orbOf(entity) + ")");
                }
                // 变形产物也一并作废（按它记的归属去各张变形数据表里打标记）
                cn.autoforged.joes_addons_for_abmc.ModMain
                    .destroyMorphedSummonProducts(allyOf(entity), orbOf(entity));
                entity.discard();
                destroyed++;
            }
        }
        if (destroyed > 0) {
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[召唤物] 主人已不在（核心与空壳都查不到），清掉 {} 只无主召唤物: {}",
                destroyed, detail);
        }
        if (OWNERLESS_STREAK.size() > 256) {
            OWNERLESS_STREAK.clear();   // 召唤物 UUID 会随变形复原更新，表不能无限长
        }
    }

    /** 数一数某颗核心在当前世界还活着几个某角色的召唤物（召唤前用来判断是否已达上限）。 */
    public static int countRole(ServerLevel level, UUID orbUuid, String role) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            // 这套遍历在小整合包里出现过 null 元素（见 discardAll 的注释），一律先判空
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            CompoundTag data = entity.getPersistentData();
            if (!role.equals(data.getString(TAG_ROLE)) || !data.hasUUID(TAG_ORB)) {
                continue;
            }
            if (orbUuid.equals(data.getUUID(TAG_ORB))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 数一数<b>当前服务器所有维度</b>里还活着几个某角色的召唤物，<b>不分是哪颗核心的</b>。
     * <p>
     * 和 {@link #countRole} 的区别只有一条：那个是按核心归属分别数（"我这颗核心叫了几只"），
     * 这个是全场总数（"整个存档一共几只"）。己方凋灵用的是这一条 —— 用户要求
     * <b>在场所有被召唤的凋灵总数不超过 5</b>，也就是说几只空壳同时在场时，
     * 它们共用这一个 5 只的名额，而不是每具壳各叫 5 只（那就是最多 5×壳数 只了）。
     * <p>
     * 遍历口径与 {@link #countRole} 完全一致（判空、只算存活、认角色标记 + 核心标记），
     * 所以"带着召唤标记的凋灵"不管由谁叫出来都会被数进去。
     */
    public static int countRole(MinecraftServer server, String role) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                // 这套遍历在小整合包里出现过 null 元素（见 countRole(ServerLevel, UUID, String) 的注释）
                if (entity == null || !entity.isAlive()) {
                    continue;
                }
                CompoundTag data = entity.getPersistentData();
                if (!role.equals(data.getString(TAG_ROLE)) || !data.hasUUID(TAG_ORB)) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    /**
     * 附体结束时清场：把所有属于这颗核心的召唤物收掉。
     * <p>
     * 不清的话它们会留在世界里（Bob 还是 persistence required 的"奖励怪"体质、永不消失），
     * 一次 20 分钟的附体下来能攒出上百只。
     * <p>
     * {@code level.getAllEntities()} 这条遍历在某些整合包里会吐出 {@code null} 元素
     * （曾经因此在"切回创造模式结束附体"时崩过一次：{@code NPE: entity is null}），
     * 所以这里逐个判空，不假设集合内容是干净的。
     */
    public static void discardAll(MinecraftServer server, UUID orbUuid) {
        if (server == null || orbUuid == null) {
            return;
        }
        int destroyed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || !isSummon(entity) || !entity.getPersistentData().hasUUID(TAG_ORB)) {
                    continue;
                }
                if (orbUuid.equals(entity.getPersistentData().getUUID(TAG_ORB))) {
                    entity.discard();
                    destroyed++;
                }
            }
        }
        // 召唤物"变形之后"的产物（物品/方块/生物壳）身上没有召唤标记，
        // 上面这圈扫不到它们 —— 交给 ModMain 按各张变形数据表清理（那里才知道表在哪）。
        cn.autoforged.joes_addons_for_abmc.ModMain.destroyMorphedSummonProducts(null, orbUuid);
        // 这条日志以前没有，于是"附体正常结束"这条清场路径是静默的，
        // 排查时看不出到底走没走到（见 destroyAllForShell 那条）。
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[召唤物] 附体结束清场: orb={} 召唤物={}只", orbUuid, destroyed);
    }

    // ====================== 空壳消失 → 其召唤物（含变形产物）一并抹掉 ======================

    /** "空壳倒下时，离它多远内的召唤物算同一批"（格）。召唤物永远跟着壳走，128 格已经很宽松。 */
    private static final double SHELL_ORPHAN_RANGE = 128.0D;

    /**
     * "已经消失的空壳"名册（本次进程内）。
     * <p>
     * 只在<b>真的没了</b>时才登记：被杀死/被 /kill/被别的机制清掉。
     * 变形（空壳变成生物/方块/物品）<b>不算</b> —— 那时空壳只是换了形态，马上还要变回来，
     * 它的召唤物必须原样留着（见 {@code PlayerShellEntity#remove} 与
     * {@code ModMain#markMorphing}）。区块卸载/换维度也不算（那是没加载，不是没了）。
     * <p>
     * 它的用处：空壳消失的那一刻，召唤物可能正待在<b>没加载的区块</b>里，碰不到；
     * 于是等它随区块加载时（{@code EntityJoinLevelEvent}）凭这份名册把它抹掉。
     */
    private static final java.util.Set<UUID> VANISHED_SHELLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * "已经离场的核心"名册（同上）。
     * <p>
     * <b>主键必须能用核心 UUID，而不是空壳 UUID</b>：主空壳一旦被变形药水变形再解除，
     * 复原出来的是<b>照 NBT 新造的实体、UUID 会换一个</b>（见 {@code OrbOfLuckEntity#onVesselReverted}），
     * 而召唤物身上记的"盟友"还是召唤那一刻的旧壳 UUID —— 只按空壳 UUID 去找，一只都找不到
     * （实测日志：Bob 记的是 d1a35cb7、骑士记的是 42f66546，而倒下的壳是 075de4f0）。
     * 核心的 UUID 从头到尾不变，所以召唤物的归属一律以它为准。
     */
    private static final java.util.Set<UUID> VANISHED_ORBS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 附体空壳消失了：把"挂在它名下"的一切立刻抹掉 —— 召唤物本体，
     * 以及召唤物被变形之后留下的产物（物品/方块/生物壳，交给
     * {@link cn.autoforged.joes_addons_for_abmc.ModMain#destroyMorphedSummonProducts}）。
     *
     * @param shellUuid 消失的空壳（可空）；{@code null} 表示只按核心清理
     * @param orbUuid   这颗核心的 UUID —— <b>匹配以它为准</b>（空壳会被变形复原换掉 UUID，见
     *                  {@link #VANISHED_ORBS}）
     */
    public static void destroyAllForShell(MinecraftServer server, @Nullable Entity vanishedShell, @Nullable UUID orbUuid) {
        if (server == null) {
            return;
        }
        UUID shellUuid = vanishedShell != null ? vanishedShell.getUUID() : null;
        if (shellUuid != null) {
            VANISHED_SHELLS.add(shellUuid);
        }
        // 核心若不在了，登记进"已离场"名册：往后任何一只召唤物发现自己记的核心没了都会被清掉
        //（包括更早那次附体遗留、没人再管的那些）
        if (orbUuid != null && !entityExists(server, orbUuid)) {
            VANISHED_ORBS.add(orbUuid);
        }
        int destroyed = 0;
        java.util.List<String> orphans = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || !isSummon(entity)) {
                    continue;
                }
                UUID owner = orbOf(entity);
                UUID ally = allyOf(entity);
                // 归属判定：① 本次这颗核心；② 本次倒下的这具壳；③ 一颗已经离场的核心（遗留召唤物）；
                // ④ 它记的那具壳<b>确实不在了</b>、而且那不是"被变形换走了形态"—— 这一条最关键：
                //    壳被变形复原过的话它记的还是旧 UUID，前三条都可能对不上，但"我这具壳没了"这个
                //    事实是对的，所以照样能清掉（变形时旧壳也不在，靠 isUuidCurrentlyMorphed 排除掉）。
                boolean mine = orbUuid != null && orbUuid.equals(owner);
                if (!mine && shellUuid != null) {
                    mine = shellUuid.equals(ally);
                }
                if (!mine && owner != null && VANISHED_ORBS.contains(owner)) {
                    mine = true;
                }
                if (!mine && ally != null && !entityExists(server, ally)
                    && vanishedShell != null && entity.level() == vanishedShell.level()
                    && entity.distanceToSqr(vanishedShell) <= SHELL_ORPHAN_RANGE * SHELL_ORPHAN_RANGE
                    && !cn.autoforged.joes_addons_for_abmc.ModMain.isUuidCurrentlyMorphed(ally)) {
                    // 同一维度、就在刚倒下这具壳附近（召唤物永远跟着壳走），而它记的那具壳确实不在了 ——
                    // 判为同一批。加"附近"这一条是为了不误伤"壳只是所在区块没加载"的远处召唤物。
                    mine = true;
                }
                if (mine) {
                    entity.discard();
                    destroyed++;
                } else if (ownIsGone(server, entity)) {
                    // 谁都不认领、而且它记的主人（核心或壳）确实已经不在了：一起抹掉，免得越积越多
                    orphans.add(entity.getType().toShortString() + " ally=" + ally + " orb=" + owner);
                    entity.discard();
                    destroyed++;
                }
            }
        }
        cn.autoforged.joes_addons_for_abmc.ModMain.destroyMorphedSummonProducts(shellUuid, orbUuid);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[召唤物] 空壳消失，已抹掉它名下的召唤物与变形产物: shell={} orb={} 召唤物={}只 其中无主遗留={}",
            shellUuid, orbUuid, destroyed, orphans);
    }

    /**
     * 这只召唤物记的主人（核心 <b>与</b> 那具壳）是不是<b>两个都</b>确实不在任何维度里了。
     * <p>
     * 两个都查不到才判"无主"：召唤物永远跟着空壳走、空壳又带着核心，所以
     * "自己在加载中、而两个主人都查不到"只可能是主人真的没了，不会是区块没加载
     * （只有一个查不到时宁可放过 —— 那只可能是所在的区块刚好没加载）。
     */
    private static boolean ownIsGone(MinecraftServer server, Entity summon) {
        UUID owner = orbOf(summon);
        UUID ally = allyOf(summon);
        boolean orbGone = owner == null || !entityExists(server, owner);
        boolean allyGone = ally == null || !entityExists(server, ally);
        return orbGone && allyGone;
    }

    /**
     * 这只召唤物的主人（核心 / 那具空壳）是不是"已经没了"。
     * 用于它自己后来才随区块加载的场合（见 {@code ModMain#onEntityJoinLevel}）。
     */
    public static boolean ownerVanished(Entity summon) {
        MinecraftServer server = summon.level().getServer();
        UUID orb = orbOf(summon);
        if (orb != null && VANISHED_ORBS.contains(orb)) {
            if (!entityExists(server, orb)) {
                return true;
            }
            VANISHED_ORBS.remove(orb);   // 复核发现它还活着（那次只是区块卸载）：撤掉条目放行
        }
        UUID ally = allyOf(summon);
        if (ally != null && VANISHED_SHELLS.contains(ally)) {
            if (!entityExists(server, ally)) {
                return true;
            }
            VANISHED_SHELLS.remove(ally);
        }
        return false;
    }

    /** 这个 UUID 的实体现在还在不在（任意维度、已加载）。 */
    private static boolean entityExists(@Nullable MinecraftServer server, UUID uuid) {
        if (server == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 主空壳被重建（变形复原会换 UUID）之后，把它名下召唤物的"盟友"记录改指到新壳上。
     *
     * <h2>为什么非改不可</h2>
     * {@link OrbSummonTargetGoal} 是拿着"盟友 UUID"去找空壳的（构造时就固定了），
     * 召唤物身上也记着同一份。空壳一变形再复原就换了 UUID，于是：
     * <ul>
     *   <li>召唤物<b>再也找不到空壳</b> → 共享索敌永远失效，它们会一直站着发呆；</li>
     *   <li>空壳真死的时候，按旧 UUID 也认不出这些召唤物（见 {@link #destroyAllForShell}）。</li>
     * </ul>
     * 所以复原认领新壳时（{@code OrbOfLuckEntity#onVesselReverted}）顺带把它们全部改指过来。
     */
    public static void rebindAlly(MinecraftServer server, UUID orbUuid, UUID newShellUuid) {
        if (server == null || orbUuid == null || newShellUuid == null) {
            return;
        }
        int rebound = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || !isSummon(entity) || !orbUuid.equals(orbOf(entity))) {
                    continue;
                }
                entity.getPersistentData().putUUID(TAG_ALLY, newShellUuid);
                rebound++;
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    boolean hadGoal = false;
                    for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped
                        : mob.targetSelector.getAvailableGoals()) {
                        if (wrapped.getGoal() instanceof OrbSummonTargetGoal) {
                            hadGoal = true;
                            break;
                        }
                    }
                    // 己方凋灵也装着同一个 goal（由 OrbAllyWitherControl 管），一并换成新壳
                    if (hadGoal || SHARED_TARGET_ROLES.contains(roleOf(mob))) {
                        mob.targetSelector.removeAllGoals(goal -> goal instanceof OrbSummonTargetGoal);
                        mob.targetSelector.addGoal(0, new OrbSummonTargetGoal(mob, newShellUuid));
                        mob.setTarget(null);   // 旧目标多半是拿旧壳算出来的，丢掉重来
                    }
                }
            }
        }
        if (rebound > 0) {
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[召唤物] 主空壳复原换了 UUID，已把 {} 只召唤物改指到新壳 {}", rebound, newShellUuid);
        }
    }

    /** 这份（存起来的）实体 NBT 是不是"某个空壳名下召唤物"的。 */
    public static boolean nbtIsSummonOf(CompoundTag entityNbt, @Nullable UUID shellUuid, @Nullable UUID orbUuid) {
        if (entityNbt == null) {
            return false;
        }
        CompoundTag data = entityNbt.getCompound("NeoForgeData");
        if (!data.getBoolean(TAG_SUMMON)) {
            return false;
        }
        if (shellUuid != null && data.hasUUID(TAG_ALLY) && shellUuid.equals(data.getUUID(TAG_ALLY))) {
            return true;
        }
        return orbUuid != null && data.hasUUID(TAG_ORB) && orbUuid.equals(data.getUUID(TAG_ORB));
    }

    /** 写在持久化数据里的"注定销毁"标记（见 {@link #markNbtOrphaned}）。 */
    private static final String TAG_ORPHAN = "jafa_summon_orphan";

    /**
     * 给一份（存起来的）变形数据打上"注定销毁"：它的主人空壳已经没了，
     * 这份体验卡随之作废 —— 到点不复原、<b>变形解药也无效</b>（见
     * {@code ModMain#respawnTransmutedEntity} / {@code #revertLivingShell} 的判空返回）。
     */
    public static void markNbtOrphaned(CompoundTag entityNbt) {
        if (entityNbt == null) {
            return;
        }
        CompoundTag data = entityNbt.getCompound("NeoForgeData").copy();
        data.putBoolean(TAG_ORPHAN, true);
        entityNbt.put("NeoForgeData", data);
    }

    /** 这份（存起来的）变形数据是不是已经被判"注定销毁"。 */
    public static boolean isNbtOrphaned(CompoundTag entityNbt) {
        return entityNbt != null && entityNbt.getCompound("NeoForgeData").getBoolean(TAG_ORPHAN);
    }
}
