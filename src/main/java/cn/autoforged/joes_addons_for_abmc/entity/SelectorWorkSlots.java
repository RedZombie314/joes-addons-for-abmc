package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「<b>一个玩家附近至多同时有几个选择器在干活</b>」的调度器（需求 6.4.32）。
 *
 * <h3>规则</h3>
 * {@link #MAX_WORKING_PER_PLAYER} = <b>3</b>：对每个玩家，取他周围
 * {@link #NEAR_RADIUS} 格（128 格，与选择器的搜索半径、消失半径同一个量级）以内的选择器，
 * 只有<b>按距离最近的 3 只</b>会去搜目标、抓东西；其余的一律只跑
 * {@link SelectorWanderGoal}（在维度里随机游荡），**不寻找目标**。
 *
 * <h3>两种选择器永远放行</h3>
 * <ul>
 *   <li>{@link LuckySelectorEntity#isBusy()}：正在抓/正在送的；</li>
 *   <li>{@link LuckySelectorEntity#hasContainedContent()}：手上已经攥着东西、等着 send 的。</li>
 * </ul>
 * 它们<b>不看名额、也不看距离</b>，必须把活干完——理由见 {@link #mayWork} 的注释：
 * 6.4.32 就是因为"按距离授权"把正在干活的选择器踢了出去，让它们攥着东西永久卡死。
 * <p>
 * 剩下的名额（每人 3 个，减去"正在干活的那些"占掉的）按距离发给最近的空手选择器，
 * 所以"一个玩家附近至多 3 只在找目标"在稳态下严格成立。
 *
 * <h3>为什么每刻算一次、并且缓存</h3>
 * 选举结果必须<b>对同一刻内的所有选择器一致</b>（否则会出现"我算你是，你算我不是"），
 * 所以按「维度 + 游戏刻」缓存，同一刻里第一个来问的选择器算一遍，其余直接查表。
 * 用 {@link ServerLevel#getAllEntities()} 全量扫一遍而不是给每个玩家开 128 格包围盒查询：
 * 后者会命中好几千个实体段（128 格半径 = 17×17 个区块 × 约 16 个 y 段），而本维度实体总数
 * 只有几百，全量扫反而便宜——本模组另外几个生成器（{@code LuckyMobSpawner} 等）也都是这么做的。
 *
 * <h3>与玩家数量无关的部分</h3>
 * 每个玩家各有一份 3 个名额（多人一起玩时各自看得见自己的 3 只），但<b>选择器总数上限
 * {@link LuckySelectorSpawner#CAP} 仍然是整个维度共享的一份</b>，不会因为人多而变多。
 */
public final class SelectorWorkSlots {

    /** 一个玩家附近最多同时有几只选择器在干活。 */
    public static final int MAX_WORKING_PER_PLAYER = 3;

    /**
     * "附近"的半径（格）：128。
     * <p>
     * 取这个数的理由：它就是选择器<b>搜索目标</b>的半径（{@link SelectorCollectGoal}），
     * 也是本维度生物消失规则的半径——比它更远的选择器本来也够不着玩家身边的东西，
     * 放它去搜反而会让"我附近到底有几只在干活"这件事失真。
     */
    public static final double NEAR_RADIUS = 128.0D;

    private static final double NEAR_RADIUS_SQR = NEAR_RADIUS * NEAR_RADIUS;

    // ===== 每刻缓存（只在服务端主线程用）=====

    private static ServerLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    /** 本刻被授权"去搜目标"的选择器实体 id 集合。 */
    private static final Set<Integer> ACTIVE = new HashSet<>();

    private SelectorWorkSlots() {
    }

    /**
     * 这只选择器本刻可不可以干收集这活。
     * <p>
     * 两类选择器<b>无条件放行</b>（不看名额、也不看离玩家多远）：
     * <ul>
     *   <li>{@link LuckySelectorEntity#isBusy()}：正在抓/正在送；</li>
     *   <li>{@link LuckySelectorEntity#hasContainedContent()}：手上攥着东西还没送。</li>
     * </ul>
     * <b>这一条是 6.4.33 补的救命规则</b>：6.4.32 刚上线时，"有没有授权"是按"离玩家 128 格以内"算的，
     * 于是一只正在抓着东西的选择器只要飘出 128 格（或者玩家跑远了），就会被取消授权 → goal 被停掉 →
     * {@link SelectorCollectGoal} 的"游荡一会儿再送"阶段被重置 → 而它手里那把东西谁也送不走了
     * （旧的 SEARCH 阶段见到"手里有东西"就直接让位）。带着东西的  选择器 `PersistenceRequired` 为真，
     * 又不受"离玩家 >128 格即删除"的约束 —— 结果就是它带着东西在维度里飘一辈子、还永久占着上限名额。
     * 实测：45 只选择器里 32 只正是这样卡住的，把上限（17）占满，新选择器一只都刷不出来。
     * <p>
     * 换句话说：<b>名额限制只管"要不要开始找新目标"，不管"手上的活干不干完"</b>。
     * 客户端一律返回 false（goal 本来就只在服务端 tick，这只是兜底：不要在客户端算这套东西）。
     */
    public static boolean mayWork(LuckySelectorEntity selector) {
        if (selector.level().isClientSide()) {
            return false;
        }
        ServerLevel level = (ServerLevel) selector.level();
        if (!LuckyDimensionMobs.isLuckyDimension(level)) {
            return false;
        }
        if (selector.isBusy() || selector.hasContainedContent()) {
            return true; // 手上的活必须干完，见上面注释
        }
        ensureTickCache(level);
        return ACTIVE.contains(selector.getId());
    }

    /** 每刻重算一次（第一个来问的选择器触发，其余直接查缓存）。 */
    private static void ensureTickCache(ServerLevel level) {
        long tick = level.getGameTime();
        if (level == cachedLevel && tick == cachedTick) {
            return;
        }
        cachedLevel = level;
        cachedTick = tick;
        rebuild(level);
    }

    /** 对每个非旁观玩家各选 3 只（先收"正在干活的"，再按距离补满空手名额）。 */
    private static void rebuild(ServerLevel level) {
        ACTIVE.clear();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        List<LuckySelectorEntity> selectors = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LuckySelectorEntity selector && selector.isAlive()) {
                selectors.add(selector);
            }
        }
        if (selectors.isEmpty()) {
            return;
        }

        for (ServerPlayer player : players) {
            List<LuckySelectorEntity> near = new ArrayList<>();
            for (LuckySelectorEntity selector : selectors) {
                if (selector.distanceToSqr(player) <= NEAR_RADIUS_SQR) {
                    near.add(selector);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            near.sort(Comparator.comparingDouble(selector -> selector.distanceToSqr(player)));

            int slots = MAX_WORKING_PER_PLAYER;
            // 第一轮：正在干活的先占名额（它们由 mayWork 无条件放行，这里加进 ACTIVE 只是为了
            // 让"3 个名额"把它们的份量算进去，免得旁边再授权 3 只空手的、看起来像 6 只在干活）
            for (LuckySelectorEntity selector : near) {
                if (selector.isBusy() || selector.hasContainedContent()) {
                    ACTIVE.add(selector.getId());
                    slots--;
                }
            }
            // 第二轮：剩下的名额按距离给最近的那些空手选择器。
            // 这里不再判断"是不是已经在 ACTIVE 里"：一只选择器同时靠近两个玩家时，它确实
            // 占着两个人的名额（严格按"每个玩家附近最多 3 只"来算）。
            for (LuckySelectorEntity selector : near) {
                if (slots <= 0) {
                    break;
                }
                ACTIVE.add(selector.getId());
                slots--;
            }
        }
    }

    /** 本刻被授权的选择器数量（调试/命令用）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }
}
