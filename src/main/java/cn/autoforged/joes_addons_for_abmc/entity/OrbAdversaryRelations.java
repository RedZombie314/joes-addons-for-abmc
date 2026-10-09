package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>"红僵尸关系"（Red_Zombie 专属规则）</b>：只服务于一条用户指定的特例 ——
 *
 * <blockquote>
 * 名为 {@code SunnySeren} 的附体空壳<b>不攻击</b>名为 {@code Red_Zombie} 的玩家<b>及其宠物</b>，
 * 但<b>照常索敌其它一切生物</b>；此外它还会索敌
 * "Red_Zombie 攻击过的被附体玩家空壳"与"正在攻击 Red_Zombie 的被附体玩家空壳"。
 * </blockquote>
 *
 * <p>注意这条特例<b>不是</b>"只打那几类" —— 正常索敌原样保留，
 * 这里只提供两件东西：
 * <ul>
 *   <li><b>排除项</b>：{@link #isProtectedPlayer} / {@link #isPetOf}
 *       （正常索敌里要把这两类剔掉）；</li>
 *   <li><b>例外目标</b>：{@link #relationRank}（那两类与被附体空壳之间"结了梁子"的壳）。</li>
 * </ul>
 *
 * <h2>为什么要一张表</h2>
 * "Red_Zombie 攻击过的玩家空壳"不是能从当前世界状态读出来的东西 —— 打过就是发生过的事，
 * 打完就散了（原版 {@code lastHurtByMob} 只记"谁打了我"，<b>正方向没有记录</b>）。
 * 所以这里维护一张<b>有向</b>的"谁打过谁"表，两个方向都记：
 * <ul>
 *   <li>{@link #recordPlayerAttack}：那位玩家打了某个实体 —— 若那是个玩家空壳就记下来
 *       （用于"Red_Zombie <b>攻击</b>的壳"）；</li>
 *   <li>{@link #recordShellAttack}：某个实体（空壳本体或它的召唤物）打了那位玩家 —— 记下那具壳
 *       （用于"<b>攻击</b> Red_Zombie 的壳"）。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * 条目带<b>游戏刻时间戳</b>，超过 {@link #MEMORY_TICKS} 没再发生就作废 ——
 * 否则"很久以前打过一次"会被永远记着，导致 SunnySeren 一直去追杀一个早就无关的壳。
 * 换存档/换服务器时整张表清空（在 {@link #tick} 里按服务器引用判断）：
 * 新存档的游戏刻从头开始，旧刻数会让 {@code now - at} 算成一个大负数，
 * 旧记录就会被当成"刚刚发生"而永久生效（{@code OrbAllyWitherControl} 里踩过同一个坑）。
 */
public final class OrbAdversaryRelations {

    /** 特例里那位"不攻击"的玩家名。 */
    private static final String PROTECTED_PLAYER_NAME = "Red_Zombie";

    /** 特例里那具"有特殊规则"的空壳名（皮肤名）。 */
    private static final String SPECIAL_SHELL_NAME = "SunnySeren";

    /** 一条记录的存活时间（游戏刻）：2 分钟 —— 够长到"打完一架还记得"，又短到不翻陈年旧账。 */
    private static final long MEMORY_TICKS = 2 * 60 * 20L;

    /** 当前这张表属于哪一个服务器（换了就整体清空）。 */
    @Nullable
    private static MinecraftServer currentServer;

    /** 那位玩家 UUID → 他打过的<b>玩家空壳</b> UUID → 最后一次发生的游戏刻。 */
    private static final Map<UUID, Map<UUID, Long>> ATTACKED_SHELLS = new ConcurrentHashMap<>();

    /** 那位玩家 UUID → 打过他的<b>玩家空壳</b> UUID → 最后一次发生的游戏刻。 */
    private static final Map<UUID, Map<UUID, Long>> SHELLS_ATTACKING = new ConcurrentHashMap<>();

    private OrbAdversaryRelations() {
    }

    // ====================== 判定：谁是"那位玩家"/"那具壳" ======================

    /** 这个实体是不是特例里那位"不攻击"的玩家（按<b>玩家名</b>认，不是 UUID）。 */
    public static boolean isProtectedPlayer(@Nullable Entity entity) {
        return entity instanceof Player player
            && PROTECTED_PLAYER_NAME.equals(player.getGameProfile().getName());
    }

    /**
     * 这具空壳是不是特例里那具"有特殊规则"的空壳。
     * <p>判据用<b>皮肤名</b>（{@link PlayerShellEntity#getSkinTexture()}）—— 那就是这具壳的"名字"：
     * 附体壳取被附体玩家的名字，刷怪蛋/同化壳取随机到的玩家名或蛋的自定义名
     * （见 {@code OrbPlayerShellSpawnEggItem#resolveSkinName}）。
     */
    public static boolean isSpecialShell(@Nullable PlayerShellEntity shell) {
        return shell != null && SPECIAL_SHELL_NAME.equals(shell.getSkinTexture());
    }

    /** 这个世界里那位"被保护玩家"；不在线/不存在返回 null。 */
    @Nullable
    public static ServerPlayer findProtectedPlayer(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PROTECTED_PLAYER_NAME.equals(player.getGameProfile().getName())) {
                return player;
            }
        }
        return null;
    }

    /**
     * 这个实体是不是那位玩家<b>饲养的宠物</b>。
     * <p>两类都算：
     * <ul>
     *   <li>原版可驯服动物（{@link TamableAnimal}：狼/猫/鹦鹉/马……）—— 看 {@code getOwnerUUID()}；</li>
     *   <li>车万女仆（装了那个模组才有）—— 走 {@code ModMain#isMaidOwnedBy} 的反射判定。</li>
     * </ul>
     * 注意：<b>玩家空壳不算宠物</b>（哪怕名字叫 Red_Zombie）—— 用户明确说了
     * "不适用于同名的玩家空壳"，玩家空壳走"被他打过 / 正在打他"那两条规则。
     */
    public static boolean isPetOf(@Nullable Entity entity, ServerPlayer owner) {
        if (entity == null || owner == null || entity instanceof PlayerShellEntity) {
            return false;
        }
        if (entity instanceof TamableAnimal tamed && owner.getUUID().equals(tamed.getOwnerUUID())) {
            return true;
        }
        // 女仆：先用类型 id 挡一道（避免对每个普通生物都做一次反射，见 mightBeMaid）
        return mightBeMaid(entity) && ModMain.isMaidOwnedBy(entity, owner);
    }

    /**
     * 快路径：这个实体<b>可能</b>是女仆吗？只做"实体类型 id 是不是那个模组的命名空间"这一层判断，
     * 不加载任何女仆的类。
     * <p>
     * 为什么要它：{@link #isPetOf} 会被"候选过滤"对范围内每一个生物调一次（每 10 刻一轮），
     * 而 {@code ModMain#isMaidOwnedBy} 内部是 {@code Class.forName} + 方法查找的反射。
     * 先用类型 id 挡掉 99.9% 的普通生物，反射就只在真的遇到女仆时才发生。
     */
    private static boolean mightBeMaid(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getType().builtInRegistryHolder().key().location().getNamespace()
            .equals("touhou_little_maid");
    }

    // ====================== 记录：谁打过谁 ======================

    /**
     * 那位玩家<b>打了</b>某个实体：如果那是个玩家空壳，记下来。
     * <p>两个入口都调它：{@code AttackEntityEvent}（近战，原版只有这一条路）与伤害事件
     * （远程/爆炸/召唤物打过去时 {@code AttackEntityEvent} 不会触发）。
     */
    public static void recordPlayerAttack(@Nullable Player attacker, @Nullable Entity victim) {
        if (!isProtectedPlayer(attacker) || !(victim instanceof PlayerShellEntity shell)) {
            return;   // 只管"那位玩家 → 玩家空壳"这一个方向
        }
        touch(ATTACKED_SHELLS, attacker.getUUID(), shell.getUUID(), shell.level().getGameTime());
    }

    /**
     * 某个实体<b>打了那位玩家</b>：顺着它找到"出手的那具玩家空壳"并记下来
     * （直接来源是壳本体、或者是壳的召唤物/弹射物，都算在那具壳头上）。
     *
     * @param victim   被打了的实体（必须是那位玩家才有意义）
     * @param attacker 动手的实体（壳本体 / 它的召唤物 / 它的弹射物）
     */
    public static void recordShellAttack(@Nullable Entity victim, @Nullable Entity attacker) {
        if (!isProtectedPlayer(victim) || attacker == null) {
            return;
        }
        PlayerShellEntity shell = resolveAttackerShell(attacker);
        if (shell == null) {
            return;
        }
        touch(SHELLS_ATTACKING, victim.getUUID(), shell.getUUID(), attacker.level().getGameTime());
    }

    /**
     * 顺着"动手的实体"找那具玩家空壳：本体直接就是；召唤物/弹射物则顺着
     * {@code OrbPossessionSummons} 的核心标记 → 那颗核心 → <b>骑在它上面的那具壳</b>
     * （与 {@code OrbAssimilation#possessorShellOf} 同一套口径）。
     */
    @Nullable
    private static PlayerShellEntity resolveAttackerShell(Entity attacker) {
        if (attacker instanceof PlayerShellEntity shell) {
            return shell;
        }
        UUID orbUuid = OrbPossessionSummons.orbOf(attacker);
        if (orbUuid == null || attacker.level().getServer() == null) {
            return null;
        }
        for (var level : attacker.level().getServer().getAllLevels()) {
            Entity found = level.getEntity(orbUuid);
            if (!(found instanceof OrbOfLuckEntity orb)) {
                continue;
            }
            for (Entity passenger : orb.getPassengers()) {
                if (passenger instanceof PlayerShellEntity shell) {
                    return shell;
                }
            }
            return null;   // 核心在，但骑着的不是玩家空壳
        }
        return null;
    }

    // ====================== 查询：SunnySeren 该不该打这具壳 ======================

    /** 这具壳是不是"那位玩家<b>打过</b>的壳"（且在记忆期内）。 */
    public static boolean isShellAttackedByProtected(ServerPlayer protectedPlayer, PlayerShellEntity shell) {
        return isFresh(ATTACKED_SHELLS, protectedPlayer.getUUID(), shell.getUUID(),
            shell.level().getGameTime());
    }

    /** 这具壳是不是"正在<b>攻击</b>那位玩家的壳"（且在记忆期内）。 */
    public static boolean isShellAttackingProtected(ServerPlayer protectedPlayer, PlayerShellEntity shell) {
        return isFresh(SHELLS_ATTACKING, protectedPlayer.getUUID(), shell.getUUID(),
            shell.level().getGameTime());
    }

    /**
     * 这具被附体空壳与那位玩家的"梁子"有多急 —— SunnySeren 用它决定要不要把
     * 这具平时被当成"自己人"的壳临时算进目标里。
     *
     * @return {@code 0} = 正在攻击那位玩家（最急，护主）；
     *         {@code 1} = 那位玩家攻击过它（帮打）；
     *         {@code -1} = 与那位玩家无关，照旧不打
     */
    public static int relationRank(ServerPlayer protectedPlayer, PlayerShellEntity shell) {
        if (isShellAttackingProtected(protectedPlayer, shell)) {
            return 0;
        }
        if (isShellAttackedByProtected(protectedPlayer, shell)) {
            return 1;
        }
        return -1;
    }

    // ====================== 内部 ======================

    private static void touch(Map<UUID, Map<UUID, Long>> table, UUID key, UUID value, long now) {
        table.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(value, now);
    }

    private static boolean isFresh(Map<UUID, Map<UUID, Long>> table, UUID key, UUID value, long now) {
        Map<UUID, Long> inner = table.get(key);
        if (inner == null) {
            return false;
        }
        Long at = inner.get(value);
        return at != null && now - at >= 0L && now - at <= MEMORY_TICKS;
    }

    /**
     * 由服务端 tick 钩子每刻调用：登记/切换服务器，并清掉过期条目。
     * <p>换服务器（退出重进另一个存档）时必须整张表清掉 —— 新存档的游戏刻从头开始，
     * 而表里存的是旧存档的刻数，{@code now - at} 会算出大负数（{@link #isFresh} 里
     * 那条 {@code >= 0} 也顺带挡了一层）。
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (currentServer != server) {
            currentServer = server;
            ATTACKED_SHELLS.clear();
            SHELLS_ATTACKING.clear();
            return;
        }
        long now = server.getTickCount();
        sweep(ATTACKED_SHELLS, now);
        sweep(SHELLS_ATTACKING, now);
    }

    private static void sweep(Map<UUID, Map<UUID, Long>> table, long now) {
        for (Map<UUID, Long> inner : table.values()) {
            inner.entrySet().removeIf(entry -> now - entry.getValue() > MEMORY_TICKS);
        }
        table.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** 调试/排查用（{@code /jafa orbinfo} 之类）：当前记着几条关系。 */
    public static String describe() {
        int attacked = ATTACKED_SHELLS.values().stream().mapToInt(Map::size).sum();
        int attacking = SHELLS_ATTACKING.values().stream().mapToInt(Map::size).sum();
        return "Red_Zombie打过" + attacked + "具壳/正在打他" + attacking + "具壳";
    }
}
