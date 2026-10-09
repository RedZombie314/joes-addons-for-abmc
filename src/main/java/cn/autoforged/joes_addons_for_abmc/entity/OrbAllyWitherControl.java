package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.UUID;

/**
 * 己方凋灵的<b>副头目标锁定</b>。
 *
 * <h2>为什么需要它</h2>
 * 原版 {@code WitherBoss} 只有<b>主头</b>跟着 {@code getTarget()} 走；两颗<b>副头</b>在
 * 自己的"备选目标槽"为空时，会自己做一次随机挑选：
 *
 * <pre>{@code
 * } else {
 *     List<LivingEntity> list = this.level().getNearbyEntities(
 *         LivingEntity.class, TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0, 8.0, 20.0));
 *     if (!list.isEmpty()) this.setAlternativeTarget(i, list.get(this.random.nextInt(list.size())).getId());
 * }
 * }</pre>
 *
 * 那份 {@code TARGETING_CONDITIONS} 只排除创造/旁观，分不清敌我 —— 于是副头会<b>随机瞄准
 * 幸运核心、被附体的玩家空壳、以及我们自己的召唤物</b>。这就是"己方凋灵的副头仍会攻击
 * 附体玩家"的原因。（主头因为用 {@code getTarget()}，本来是正常的。）
 *
 * <h2>怎么修</h2>
 * 那段随机挑选只在"槽位 ≤ 0"时才发生，所以只要在<b>凋灵自己的 AI 跑之前</b>把两个副头槽位
 * 一直填成正数，随机挑选就永远不会发生：
 * <ul>
 *   <li>有目标 → 锁定共享目标（三颗头一起打同一个敌人）；</li>
 *   <li>没有目标 → 锁定<b>凋灵自己</b>。{@code canAttack(self)} 为假，于是它每刻都会把槽位
 *       清成 0、但<b>不会</b>进入随机挑选分支 —— 表现就是"没敌人时副头不射击"，
 *       既不打自己人，也不会乱打路过的生物。</li>
 * </ul>
 * 本类的 {@link #tick()} 挂在 {@code ServerTickEvent.Pre} 上（{@code ModMain.onServerTickPre}），
 * 服务端刻一开始就跑，早于该刻内所有实体 AI，所以每刻都能抢在凋灵前面填好槽位。
 *
 * <p>另外还提供 {@link #isAllySide(Entity)}：给"友伤兜底"用 —— 万一还是被凋灵之首糊到
 * （比如自己走进弹道里），那一击对我们这一侧不结算。
 *
 * <h2>为什么"己方改造"要每刻确认、名册还只许记 UUID</h2>
 * 让己方凋灵不打自己人，靠的是两件事：
 * <ol>
 *   <li><b>主头</b>：清掉原版那套目标 goal（原版会挑最近的活体，分不清敌我），只装一个
 *       {@link OrbSummonTargetGoal}"抄盟友空壳的目标"；</li>
 *   <li><b>副头</b>：本类每刻把两个副头槽位填成正数，掐掉原版那次随机挑选。</li>
 * </ol>
 * 第 1 条是<b>命令式</b>注册到 {@code targetSelector} 上的、<b>不进存档</b>；
 * 而凋灵自己（连同 {@code registerGoals()} 里那套"见谁打谁"）会随存档重新加载。所以读档后：
 * <ul>
 *   <li>凋灵顶着原版目标 goal 复活 → 主头立刻去锁最近的活体（十有八九就是那具附体空壳），
 *       副头再跟着主头一起打它；</li>
 *   <li>如果名册记的是 {@code ServerLevel}、或者用 {@code Long.MIN_VALUE} 当重扫时间戳的初值，
 *       这条自愈路径会<b>整条失效</b> —— 见 {@link Ally} 与 {@link #lastRescanTick} 两处注释里
 *       记的那两个坑（陈旧 ServerLevel、相减溢出）。</li>
 * </ul>
 * 于是这里做了三件事：名册只记 UUID、按<b>当前</b>服务器的维度去找实体、每刻确认改造还在
 * （{@link #ensureAllyBehavior}）。无论读档、区块反复加载还是别的模组清了 goal，最多 1 刻自愈。
 */
public final class OrbAllyWitherControl {

    /** 凋灵副头的备选目标槽位（0 是主头，1/2 是副头）。 */
    private static final int[] SIDE_HEADS = {1, 2};

    /**
     * 名册条目：<b>只记 UUID</b>，不记 {@code ServerLevel}。
     * <p>
     * 曾经记的是 {@code (ServerLevel, UUID)}，那是个陷阱：模组的静态字段<b>跨存档/跨局</b>活着
     * （退出到标题再进世界时类不会重新加载），于是名册里留着一堆<b>上一局的死 ServerLevel</b>；
     * 又因为 UUID 不变，{@code isRegistered()} 依然为真，新一局那只凋灵就永远轮不到"己方改造"。
     * 只存 UUID + 用<b>当前</b>服务器去找实体，从根上避开这类陈旧引用（见 {@link #findWither}）。
     */
    private record Ally(UUID witherUuid) {
    }

    private static final java.util.List<Ally> ALLIES = new ArrayList<>();

    /** 重新扫描的间隔（刻）：0.5 秒。见 {@link #rescan}。 */
    private static final int RESCAN_INTERVAL_TICKS = 10;

    /**
     * 上一次重扫的游戏刻。
     * <p>
     * <b>别用 {@code Long.MIN_VALUE} 当初值</b>：这段判断是
     * {@code server.getTickCount() - lastRescanTick >= RESCAN_INTERVAL_TICKS}，
     * 而 {@code 0L - Long.MIN_VALUE} 在 64 位有符号里会<b>溢出成负数</b>（2^63 放不下），
     * 于是条件永远为假 —— 周期重扫等于被永久关掉（本类曾经就踩了这个坑：
     * 己方凋灵在召唤那一刻被改造得好好的，一读档就再也回不到名册里，副头随即开始打空壳）。
     * 用 {@code -RESCAN_INTERVAL_TICKS} 既没有溢出风险，又能让第一次调用立刻生效。
     */
    private static long lastRescanTick = -RESCAN_INTERVAL_TICKS;

    /**
     * 当前这一局的服务器实例。
     * <p>
     * 用它判断"换了一局"（退出存档重进、换世界）：换了就把名册清掉、并把重扫时间戳重置成
     * {@link #lastRescanTick} 里那个负数哨兵（新一局的 tick 从 0 重新开始，
     * 旧时间戳只会让重扫更晚生效）。
     */
    @Nullable
    private static MinecraftServer currentServer;

    private OrbAllyWitherControl() {
    }

    /**
     * 登记一只己方凋灵（由 {@link OrbPossessedAttackEvents} 的召唤事件调用），
     * 顺便把"己方改造"做上（清原版目标 goal + 装共享索敌，见 {@link #applyAllyBehavior}）。
     *
     * @param allyUuid 盟友（那具附体空壳）的 UUID，共享索敌靠它找目标
     */
    public static void register(WitherBoss wither, @Nullable UUID allyUuid) {
        applyAllyBehavior(wither, allyUuid);
        ALLIES.add(new Ally(wither.getUUID()));
    }

    /**
     * 把一只凋灵改造成"己方凋灵"：清掉原版那套"见谁打谁"的目标 goal，
     * 只留一个"抄盟友空壳当前目标"的 {@link OrbSummonTargetGoal}。
     * <p>
     * 盟友 UUID 拿不到（标记缺失）时只清不加：那样它谁都不打，也比回头去打自己人好。
     */
    private static void applyAllyBehavior(WitherBoss wither, @Nullable UUID allyUuid) {
        wither.targetSelector.removeAllGoals(goal -> true);
        if (allyUuid != null) {
            wither.targetSelector.addGoal(0, new OrbSummonTargetGoal(wither, allyUuid));
        }
    }

    /**
     * 确认"己方改造"还在，丢了就重做（读档后必走这条路，理由见类注释）。
     * <p>
     * 每刻调用，正常情况只是一次目标 goal 集合的遍历。
     */
    private static void ensureAllyBehavior(WitherBoss wither) {
        for (WrappedGoal wrapped : wither.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof OrbSummonTargetGoal) {
                return;   // 改造还在，什么都不用做
            }
        }
        applyAllyBehavior(wither, OrbPossessionSummons.allyOf(wither));
    }

    /**
     * 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。
     *
     * <p>除了锁定已在名册上的凋灵，还会<b>定期重新扫描</b>一遍已加载实体，把"带召唤标记的凋灵"
     * 补进名册 —— 这一点是必须的：名册只在内存里，退出重进存档后凋灵会随区块重新加载。
     * 重扫之后这种情况最多只存在半秒，而且友伤兜底一直在，不会再打到我们这一侧。
     */
    public static void tick(MinecraftServer server) {
        // 换了一局（退出存档重进/换世界）：名册里的 UUID 虽然还在，但游戏刻从头开始了。
        // 时间戳这里必须写成"允许立刻重扫"的值（见 lastRescanTick 的注释：负数哨兵 + 相减，
        // 不能拿 Long.MIN_VALUE 去减，否则第一次比较就溢出成假、重扫被永久卡住）。
        if (server != currentServer) {
            currentServer = server;
            ALLIES.clear();
            lastRescanTick = -RESCAN_INTERVAL_TICKS;
        }
        if (server.getTickCount() - lastRescanTick >= RESCAN_INTERVAL_TICKS) {
            lastRescanTick = server.getTickCount();
            rescan(server);
        }
        if (ALLIES.isEmpty()) {
            return;
        }
        ListIterator<Ally> iterator = ALLIES.listIterator();
        while (iterator.hasNext()) {
            Ally ally = iterator.next();
            WitherBoss wither = findWither(server, ally.witherUuid());
            if (wither == null || !wither.isAlive()) {
                iterator.remove();
                continue;
            }
            // "己方改造"是命令式的、不进存档：读档/区块重载后必须补做，否则主头会自己去打那具空壳
            ensureAllyBehavior(wither);
            pinSideHeads(wither);
        }
    }

    /**
     * 在<b>当前</b>服务器里按 UUID 找那只凋灵（跨维度找；没加载/已死返回 null）。
     * <p>
     * 刻意不缓存 {@code ServerLevel}：那是上一局的对象，拿它去查实体要么拿到旧世界的死实体、
     * 要么查不到，两种情况都会让"己方改造"落空（见 {@link Ally} 的注释）。
     */
    @Nullable
    private static WitherBoss findWither(MinecraftServer server, UUID witherUuid) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(witherUuid) instanceof WitherBoss wither) {
                return wither;
            }
        }
        return null;
    }

    /** 把所有已加载的"己方凋灵"补进名册（重进存档后的自愈路径）。 */
    private static void rescan(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                // 这个整合包里 getAllEntities() 会混进 null 元素，逐个判空
                if (!(entity instanceof WitherBoss wither) || !wither.isAlive()) {
                    continue;
                }
                if (OrbPossessionSummons.isSummon(wither) && !isRegistered(wither.getUUID())) {
                    ALLIES.add(new Ally(wither.getUUID()));
                }
            }
        }
    }

    private static boolean isRegistered(UUID witherUuid) {
        for (Ally ally : ALLIES) {
            if (ally.witherUuid().equals(witherUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把两颗副头的槽位填成正数：有敌人就锁敌人，没敌人就锁自己（详见类注释）。
     * <p>
     * 这里是<b>无条件覆盖</b>：不管槽位里原本是谁（原版随机挑的、读档后遗留的陈旧 id、
     * 还是别处塞进来的自己人），每刻都会被改回"共享目标 / 自己"。
     * 所以只要本方法在跑，副头就不可能出现"自己去打附体空壳"这种场面 ——
     * 反过来说，一旦副头真的在打空壳，就说明<b>这只凋灵根本没被本类接管</b>
     * （名册失效、实体是上一局的死对象等，见 {@link Ally} 与 {@link #findWither} 的注释）。
     */
    private static void pinSideHeads(WitherBoss wither) {
        LivingEntity target = wither.getTarget();
        int shared = target != null && target.isAlive() ? target.getId() : wither.getId();
        for (int head : SIDE_HEADS) {
            if (wither.getAlternativeTarget(head) != shared) {
                wither.setAlternativeTarget(head, shared);
            }
        }
    }

    /**
     * 这个实体是不是"我们这一侧"（幸运核心 / 被附体的空壳 / 一切附体召唤物）。
     * <p>
     * 只用来做友伤兜底：己方凋灵的攻击（含副头乱射、半血爆发）落到这一侧时直接不结算。
     */
    public static boolean isAllySide(Entity entity) {
        if (entity instanceof OrbOfLuckEntity) {
            return true;
        }
        if (entity instanceof PlayerShellEntity shell && shell.isOrbAttached()) {
            return true;
        }
        return OrbPossessionSummons.isSummon(entity);
    }
}
