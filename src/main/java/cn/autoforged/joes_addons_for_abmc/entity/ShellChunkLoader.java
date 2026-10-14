package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.mixin.DistanceManagerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>让"玩家空壳"（{@link PlayerShellEntity}）像真人玩家一样加载区块</b>：
 * 载入范围 = 服务器的 view-distance，计时范围 = 服务器的 simulation-distance，
 * 与原版给真人玩家挂的票据<b>逐条对应</b>。
 *
 * <h2>原版玩家是怎么加载区块的</h2>
 * 玩家进服时 {@code ChunkMap#updatePlayerStatus} 会调 {@code DistanceManager#addPlayer}
 * （1.21.1 源码 {@code DistanceManager.java:230-254}），它做两件事：
 * <ol>
 *   <li><b>载入</b>：{@code playerTicketManager}（{@code PlayerTicketTracker}，扩散上限 32）
 *       以玩家所在区块为源、按切比雪夫距离向外扩散，凡距离 ≤ <b>视图距离</b>
 *       （{@code serverViewDistance} ← server.properties 的 view-distance）的区块，
 *       每块各挂一张 level = {@code PLAYER_TICKET_LEVEL}（= 31 = {@code ENTITY_TICKING}）的
 *       {@code TicketType.PLAYER} 票 —— 于是这些区块被完整加载与生成；</li>
 *   <li><b>计时</b>：往 {@code tickingTicketsTracker} 里挂<b>一张</b>
 *       level = {@code max(0, 31 - 模拟距离)} 的票（同方法里的 {@code getPlayerTicketLevel()}）。
 *       票等级随距离递增，所以<b>模拟距离</b>（simulation-distance）内的区块等级 ≤ 31，
 *       才真正进入"刻"的状态。</li>
 * </ol>
 * 两件事必须<b>都做</b>：光有载入票，区块是"加载但冻结"的 —— 连空壳自己都不会 tick，
 * 因为 {@code ServerLevel#tick} 每刻对每个实体都要查 {@code inEntityTickingRange}
 * （{@code ServerLevel.java:411}），而那个判断只读计时表。
 *
 * <h2>为什么不用原版 {@code TicketType.PLAYER}</h2>
 * 票据的相等性只看 {@code (类型, 等级, 值)} 三元组（{@code Ticket#equals}），
 * 而原版给玩家挂的载入票正好也是 {@code (PLAYER, 31, 该区块坐标)}。
 * 两者会被 {@code SortedArraySet.addOrGet} 判成同一张票合并掉，于是"空壳消失时撤票"
 * 会顺手把同一区块上<b>真人玩家</b>的那张票一起撤掉 —— 玩家脚下的区块会当场卸载。
 * 换成自己的类型（值 = 空壳 UUID）就永不可能与他人撞车，撤票也只会撤自己的。
 *
 * <h2>撤票时机</h2>
 * <ul>
 *   <li>跨区块 / 换维度 / 服务器改了视图或模拟距离：本类自己按差值增删，只动变化的那一圈；</li>
 *   <li>空壳从世界消失（打死、变形换形态、区块卸载、跨维度搬家）：见
 *       {@link PlayerShellEntity#remove(net.minecraft.world.entity.Entity.RemovalReason)}
 *       里对 {@link #release} 的调用 —— 不撤的话那片区块会被永久锁住；</li>
 *   <li>重进存档（同一 JVM 里换了一个 {@link MinecraftServer} 实例）：票据不落盘，
 *       原版 {@code removeTicketsOnClosing} 也已经清空过，见 {@link #ensureSameServer}。</li>
 * </ul>
 *
 * <h2>刻意没做的部分</h2>
 * 原版 {@code addPlayer} 还会登记 {@code playersPerChunk}（自然刷怪的"附近有玩家"计数）。
 * 那部分需要真的传入一个 {@code ServerPlayer}，且它决定的是"附近刷不刷怪"，
 * 不属于"加载区块"本身 —— 所以空壳附近不会因此自然刷怪，这一点与真人玩家不同（刻意保留）。
 */
public final class ShellChunkLoader {

    /**
     * 我们自己的票据类型。
     * <p>值用空壳自己的 UUID：票据相等性含"值"，所以每具空壳的票互相独立，
     * 多具空壳同处一片区域时，其中一具消失不会把别的空壳的票撤掉。</p>
     */
    private static final TicketType<UUID> SHELL_TICKET =
        TicketType.create("joes_addons_for_abmc:player_shell", (a, b) -> a.compareTo(b));

    /** 每具空壳的票据状态（key = 空壳 UUID）。只在服务端主线程读写。 */
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** 上一次见到的服务器实例，用来识别"重进存档"（见 {@link #ensureSameServer}）。 */
    private static MinecraftServer lastServer;

    private ShellChunkLoader() {
    }

    /**
     * 由空壳每刻调用（仅服务端）。内部只在"跨区块 / 换维度 / 等级或半径变了"时才真的动票据，
     * 平时就是两次比较。
     */
    public static void tick(ServerLevel level, UUID owner, ChunkPos center) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ensureSameServer(server);

        State state = STATES.computeIfAbsent(owner, key -> new State());
        state.owner = owner;

        // 换维度：旧维度那一整片票必须先撤干净，否则旧维度的区块会被永久锁住
        if (state.dimension != null && !state.dimension.equals(level.dimension())) {
            ServerLevel previous = server.getLevel(state.dimension);
            if (previous != null) {
                clear(previous, state);
            } else {
                forget(state);
            }
        }
        state.dimension = level.dimension();

        // 视图/模拟距离取自服务器设置；视图距离的钳制范围照抄 ChunkMap#setServerViewDistance
        int viewDistance = Mth.clamp(server.getPlayerList().getViewDistance(), 2, 32);
        int simulationDistance = Math.max(0, server.getPlayerList().getSimulationDistance());
        int loadLevel = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);   // 31，= 原版 PLAYER_TICKET_LEVEL
        int tickLevel = Math.max(0, loadLevel - simulationDistance);           // = 原版 getPlayerTicketLevel()

        // 服务器改了视图/模拟距离：旧等级的票得按旧等级撤掉（票据相等性含等级，不撤会留下幽灵票）
        if (state.loadLevel != loadLevel || state.tickLevel != tickLevel) {
            clear(level, state);
            state.loadLevel = loadLevel;
            state.tickLevel = tickLevel;
        }

        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();

        // ── 载入票：视图距离内每一区块各一张 level-31 的票（照抄原版 PlayerTicketTracker 的结果）──
        // 只按"新旧两个方块"的差集增删：跨一格区块时通常只有 2×(2·视图距离+1) 个区块进出。
        if (!state.hasCenter || state.center.toLong() != center.toLong() || state.viewDistance != viewDistance) {
            if (state.hasCenter) {
                // ① 撤掉"旧方块有、新方块没有"的
                for (int dx = -state.viewDistance; dx <= state.viewDistance; dx++) {
                    for (int dz = -state.viewDistance; dz <= state.viewDistance; dz++) {
                        int x = state.center.x + dx;
                        int z = state.center.z + dz;
                        if (Math.abs(x - center.x) <= viewDistance && Math.abs(z - center.z) <= viewDistance) {
                            continue;
                        }
                        distanceManager.removeTicket(SHELL_TICKET, new ChunkPos(x, z), state.loadLevel, owner);
                    }
                }
            }
            // ② 补上"新方块有、旧方块没有"的（注意这里的 state.* 仍是旧值，用来判断"已经挂过"）
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int x = center.x + dx;
                    int z = center.z + dz;
                    if (state.hasCenter
                        && Math.abs(x - state.center.x) <= state.viewDistance
                        && Math.abs(z - state.center.z) <= state.viewDistance) {
                        continue;
                    }
                    distanceManager.addTicket(SHELL_TICKET, new ChunkPos(x, z), state.loadLevel, owner);
                }
            }
            state.center = center;
            state.hasCenter = true;
            state.viewDistance = viewDistance;
        }

        // ── 计时票：只挂"一张"，就在空壳当前区块上（照抄原版 addPlayer 的那一次 addTicket）──
        // 等级 = 31 - 模拟距离，向外的区块等级递增，于是模拟距离内等级 ≤ 31 → 实体刻/方块刻。
        long centerLong = center.toLong();
        if (state.hasTickTicket && state.tickChunk != centerLong) {
            tickingTracker(distanceManager)
                .removeTicket(SHELL_TICKET, new ChunkPos(state.tickChunk), state.tickLevel, owner);
            state.hasTickTicket = false;
        }
        if (!state.hasTickTicket) {
            tickingTracker(distanceManager).addTicket(SHELL_TICKET, center, state.tickLevel, owner);
            state.hasTickTicket = true;
            state.tickChunk = centerLong;
        }
    }

    /**
     * 空壳从世界里消失时调用（见 {@link PlayerShellEntity#remove}）：把它名下的票全部撤掉。
     * <p>不撤的话，那片区块会一直"有票"从而永远不卸载 —— 玩家跑多远它都挂着，是明确的泄漏。</p>
     */
    public static void release(ServerLevel level, UUID owner) {
        State state = STATES.remove(owner);
        if (state == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        ServerLevel target = level;
        if (state.dimension != null && server != null && !state.dimension.equals(level.dimension())) {
            // 状态记的维度与当前维度不一致（换维度途中被移除）：旧维度那边的票要撤在旧维度上
            target = server.getLevel(state.dimension);
        }
        if (target != null) {
            clear(target, state);
        }
    }

    /**
     * 票据不落盘：一旦 {@link MinecraftServer} 换了实例（单机"退出存档→再进"就是这样），
     * 旧实例的票早就随 {@code removeTicketsOnClosing} 一起没了，
     * 而本类的静态状态还记着"这片已经挂过" —— 不清掉的话空壳会以为自己有票、再也不去挂，
     * 结果就是它在那片区块里彻底不动。所以换实例即清空。
     */
    private static void ensureSameServer(MinecraftServer server) {
        if (server != lastServer) {
            lastServer = server;
            STATES.clear();
        }
    }

    /** 撤掉一具空壳名下的全部票据（按它自己记录的旧中心/旧半径/旧等级算范围）。 */
    private static void clear(ServerLevel level, State state) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        if (state.hasCenter) {
            for (int dx = -state.viewDistance; dx <= state.viewDistance; dx++) {
                for (int dz = -state.viewDistance; dz <= state.viewDistance; dz++) {
                    distanceManager.removeTicket(SHELL_TICKET,
                        new ChunkPos(state.center.x + dx, state.center.z + dz), state.loadLevel, state.owner);
                }
            }
        }
        if (state.hasTickTicket) {
            tickingTracker(distanceManager)
                .removeTicket(SHELL_TICKET, new ChunkPos(state.tickChunk), state.tickLevel, state.owner);
        }
        forget(state);
    }

    /** 清掉"记录"，不动票据（撤票失败/没撤过的场合用）。 */
    private static void forget(State state) {
        state.hasCenter = false;
        state.center = null;
        state.hasTickTicket = false;
        state.tickChunk = Long.MIN_VALUE;
    }

    private static net.minecraft.server.level.TickingTracker tickingTracker(DistanceManager distanceManager) {
        return ((DistanceManagerAccessor) distanceManager).jafa_tickingTicketsTracker();
    }

    /** 一具空壳当前的票据记录。 */
    private static final class State {
        private UUID owner;
        private ResourceKey<Level> dimension;
        /** 是否已经挂过载入票（含下面这三项记录的范围）。 */
        private boolean hasCenter;
        private ChunkPos center;
        private int viewDistance;
        private int loadLevel = -1;
        private int tickLevel = -1;
        /** 计时票是否已挂，以及挂在哪一区块。 */
        private boolean hasTickTicket;
        private long tickChunk = Long.MIN_VALUE;
    }
}
