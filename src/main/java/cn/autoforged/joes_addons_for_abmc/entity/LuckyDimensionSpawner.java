package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * 幸运维度自然生成的<b>驱动</b>：每游戏刻扫一遍「玩家周围一定半径内的区块」，把候选区块交给
 * {@link LuckySelectorSpawner}、{@link LuckyItemSpawner} 与 {@link LuckyMobSpawner} 各尝试。
 * 另外它也是三种生成共用的<b>落点计算</b>（{@link #findSpawnPos}）。
 *
 * <h3>为什么不挂 ServerLevel#tickChunk</h3>
 * 那样虽然省事，但 {@code tickChunk} 的调用点被原版卡在「区块中心距某个非旁观玩家 &lt; 128 格」上
 * （{@code ServerChunkCache#tickChunks} → {@code ChunkMap#anyPlayerCloseEnoughForSpawning}，
 * 距离平方 &lt; 16384，见 ServerChunkCache.java:367-375 与 ChunkMap.java:983-989）。
 * 这个 128 同时也管着原版的<b>随机刻</b>和<b>原版刷怪</b>——直接改它等于把整个游戏的这两件事
 * 一起改掉（所有维度、所有存档），副作用太大。所以改成：原版那份保持原样，我们自己走一遍扫描。
 *
 * <p><b>三套半径，各管各的</b>：
 * <ul>
 *   <li>掉落物 {@link #ITEM_SPAWN_RADIUS_BLOCKS}（256 格）——掉落物不会因为离玩家远而消失，
 *       所以放宽到 256 是纯赚；</li>
 *   <li>选择器 {@link #SELECTOR_SPAWN_RADIUS_BLOCKS}（128 格）——空手的选择器离玩家超过 128 格
 *       就会被原版规则删除（{@code Mob#removeWhenFarAway} 默认 true），所以刷在 128~256 格之间的
 *       选择器活不过一刻，那段范围对它是白刷。收回 128 之后，上限里的每一只都能真的活着；</li>
 *   <li>生物 {@link #MOB_SPAWN_RADIUS_BLOCKS}（128 格，按需求）——同理，需求 4 的 128 格删除规则
 *       决定了它们也只需要在 128 格内生成。</li>
 * </ul>
 * 三者各自一份去重集合，免得某个玩家的圆和另一个玩家的圆重叠时同一区块被算两次。
 *
 * <p><b>扫描范围还会被模拟距离夹住</b>：只有真正在「区块刻范围」内的区块才会被处理
 * （{@link ServerLevel#shouldTickBlocksAt}），因为范围之外的区块实体是不刻的——在那里刷出来的
 * 掉落物会永远停在原地、年龄不涨，既不会消失也白占名额。
 *
 * <p><b>成本</b>：每个玩家每刻扫 (2r+1)² 个区块坐标（r 取最大半径与模拟距离中较小者）。
 * 半径 16 区块时是 33×33 = 1089 次「去重 + 距离判断」，命中的区块才做区块查询与生成尝试。
 * 三个上限都刷满之后，生成尝试会在第一次判断时退出，稳态开销就是这次扫描本身。
 */
public final class LuckyDimensionSpawner {

    /** 掉落物的生成半径（格）：原版锁 128，这里放宽到 256。 */
    public static final int ITEM_SPAWN_RADIUS_BLOCKS = 256;

    /**
     * 选择器的生成半径（格）：<b>128</b>。
     * <p>
     * 与"离玩家 &gt;128 格即被删除"的规则对齐（见上面类注释）。想让选择器也能刷到 256 格，
     * 就得同时处理"刷出来就被删"这件事（例如让它一直保持 {@code PersistenceRequired}），
     * 否则只是白白生成又立刻消失。
     */
    public static final int SELECTOR_SPAWN_RADIUS_BLOCKS = 128;

    /** 生物的生成半径（格，需求给的 128）。 */
    public static final int MOB_SPAWN_RADIUS_BLOCKS = 128;

    /** 扫描用的最大半径（区块数）= 三套半径里最大的那个。 */
    private static final int SCAN_RADIUS_CHUNKS = ITEM_SPAWN_RADIUS_BLOCKS / 16;

    /** 各自一份去重集合（多个玩家的范围重叠时，同一区块只处理一次）。 */
    private static final LongSet VISITED_SELECTOR = new LongOpenHashSet();
    private static final LongSet VISITED_ITEM = new LongOpenHashSet();
    private static final LongSet VISITED_MOB = new LongOpenHashSet();

    private LuckyDimensionSpawner() {
    }

    /** 每游戏刻调用一次（见 {@code ServerLevelLuckySpawnMixin}）。 */
    public static void tickLevel(ServerLevel level) {
        if (!level.dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) {
            return;
        }
        VISITED_SELECTOR.clear();
        VISITED_ITEM.clear();
        VISITED_MOB.clear();

        int radiusChunks = Math.min(SCAN_RADIUS_CHUNKS,
            level.getServer().getPlayerList().getSimulationDistance());
        if (radiusChunks <= 0) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            // 旁观者不算 —— 与原版 playerIsCloseEnoughForSpawning 的处理一致
            if (player.isSpectator()) {
                continue;
            }
            int centerX = player.chunkPosition().x;
            int centerZ = player.chunkPosition().z;
            int size = radiusChunks * 2 + 1;
            int total = size * size;
            /*
             * 扫描起点每刻随机（环绕扫描整个方形区域）。
             * 这不是随手加的花样：三个上限都可能在一刻之内被填满，而填满之前只有"先被扫到的那些区块"
             * 拿得到生成机会 —— 固定顺序会让生物全挤在方形的同一个角上（曾经的表现就是
             * "一大群挤在三四个区块、其余地方一只都没有"）。随机起点让每一刻先被扫到的区块是随机的，
             * 长期下来候选范围内就是均匀的。
             */
            int start = level.random.nextInt(total);
            for (int i = 0; i < total; i++) {
                int index = start + i;
                if (index >= total) {
                    index -= total;
                }
                int chunkX = centerX + index % size - radiusChunks;
                int chunkZ = centerZ + index / size - radiusChunks;
                long key = ChunkPos.asLong(chunkX, chunkZ);

                boolean withSelector = !VISITED_SELECTOR.contains(key)
                    && withinRadius(chunkX, chunkZ, player, SELECTOR_SPAWN_RADIUS_BLOCKS);
                boolean withItem = !VISITED_ITEM.contains(key)
                    && withinRadius(chunkX, chunkZ, player, ITEM_SPAWN_RADIUS_BLOCKS);
                boolean withMobs = !VISITED_MOB.contains(key)
                    && withinRadius(chunkX, chunkZ, player, MOB_SPAWN_RADIUS_BLOCKS);
                if (!withSelector && !withItem && !withMobs) {
                    continue;
                }
                // 只处理真正在跑的区块：范围外的区块实体不刻，刷在那里的东西既不会消失也不会动
                if (!level.shouldTickBlocksAt(key)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (withSelector) {
                    VISITED_SELECTOR.add(key);
                    LuckySelectorSpawner.trySpawn(level, chunk);
                }
                if (withItem) {
                    VISITED_ITEM.add(key);
                    // 需求 6.5.23：自然生成改走"待生成位"——这里只负责<b>播种</b>（每区块 1~2 个位，幂等），
                    // 真正的"解除变形生成实体"由 LuckyPendingSpawns 按"玩家 50 格内 / 56 格外收回"来推。
                    // 位置仍用原来那套 findSpawnPos，所以地表分布与旧版一致。
                    LuckyPendingSpawns.seedChunk(level, chunk);
                }
                if (withMobs) {
                    VISITED_MOB.add(key);
                    LuckyPendingSpawns.seedChunk(level, chunk);
                }
            }
        }
        // 需求 6.5.23：播完种，再统一推进"待生成位"（解除 / 收回）。
        // 每刻只调一次（不是每个区块一次）：里面做的是距离比较 + 限量生成，与扫描半径无关。
        LuckyPendingSpawns.tick(level);
    }

    /**
     * 半径内的区块数（按区块中心到圆心的距离算，与 {@link #withinRadius} 同一套算法），
     * 只跟半径有关、<b>与玩家数量无关</b>——三个生成上限都用它换算，保证"多一个玩家不会让上限变大"。
     * <p>
     * 结果在首次调用时算好并缓存（半径是常量，注册表/世界状态都不影响它）。
     */
    public static int nominalChunkCount(int radiusBlocks) {
        Integer cached = NOMINAL_CHUNK_COUNTS.get(radiusBlocks);
        if (cached != null) {
            return cached;
        }
        int radiusChunks = radiusBlocks / 16 + 1;
        int radiusSqr = radiusBlocks * radiusBlocks;
        int count = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int cx = dx * 16 + 8;
                int cz = dz * 16 + 8;
                if (cx * cx + cz * cz < radiusSqr) {
                    count++;
                }
            }
        }
        NOMINAL_CHUNK_COUNTS.put(radiusBlocks, count);
        return count;
    }

    /** {@link #nominalChunkCount} 的缓存（键是半径，值域很小）。 */
    private static final java.util.Map<Integer, Integer> NOMINAL_CHUNK_COUNTS = new java.util.HashMap<>();

    /**
     * 求一个落点：<b>9/10 在地表</b>，<b>1/10 在地下</b>（三种生成共用）。
     * <p>
     * <b>地表</b>：y 取该列的 {@code Heightmap.Types.WORLD_SURFACE}，它的谓词就是"非空气"
     * （Heightmap.java:141/25），所以 {@code Level#getHeight} 给出的正是<b>最高非空气方块的 y 值 + 1</b>
     * ——也就是这一列最上面的空位，头顶按定义没有非空气方块。
     * <p>
     * <b>地下</b>：在 {@code [最低建筑高度, 地表)} 之间随机取 y，要求那格是空气（挖空出来的洞穴之类）；
     * 试几次都不中就退回地表那一格，不会因为"这一列没有洞穴"而白刷一次。
     *
     * @param undergroundChance 走地下的概率分母（10 = 1/10 地下、9/10 地表）
     * @return 落点；这一列连地表都不可用时返回 null
     */
    @Nullable
    public static BlockPos findSpawnPos(ServerLevel level, LevelChunk chunk, int undergroundChance) {
        RandomSource random = level.random;
        int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

        if (random.nextInt(undergroundChance) == 0) {
            int minY = level.getMinBuildHeight();
            int range = surface - minY;
            for (int attempt = 0; attempt < UNDERGROUND_ATTEMPTS && range > 1; attempt++) {
                BlockPos pos = new BlockPos(x, minY + random.nextInt(range), z);
                if (level.getBlockState(pos).isAir()) {
                    return pos;
                }
            }
        }

        if (surface <= level.getMinBuildHeight() || surface >= level.getMaxBuildHeight()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, surface, z);
        return level.getBlockState(pos).isAir() ? pos : null;
    }

    /** 地下候选点最多随机试几次（都不是空气就退回地表）。 */
    private static final int UNDERGROUND_ATTEMPTS = 4;

    /** 区块中心到玩家的水平距离是否落在半径内（原版也是按区块中心、按圆算的）。 */
    private static boolean withinRadius(int chunkX, int chunkZ, ServerPlayer player, int radiusBlocks) {
        double dx = (double) (chunkX * 16 + 8) - player.getX();
        double dz = (double) (chunkZ * 16 + 8) - player.getZ();
        return dx * dx + dz * dz < (double) radiusBlocks * radiusBlocks;
    }
}
