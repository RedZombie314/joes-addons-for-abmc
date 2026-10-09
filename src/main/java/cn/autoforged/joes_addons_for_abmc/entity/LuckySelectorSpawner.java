package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 幸运方块选择器（{@link LuckySelectorEntity}）的自然生成——<b>只在幸运维度生效</b>。
 *
 * <p><b>触发点</b>：{@link LuckyDimensionSpawner}（由 mixin 挂在
 * {@code ServerLevel#tick} 的 HEAD 上）每游戏刻扫一遍玩家半径内的区块，对每个候选区块调用本类一次。
 * 但本类<b>不是</b>「每个候选区块每刻都尝试一次」——那样会在进入维度的一瞬间把上限一次填满，
 * 几十只挤在同一片区块里，既卡又难看。实际节奏由 {@link #SPAWN_INTERVAL_TICKS}（5 刻）节流：
 * 每 5 刻只允许一次生成尝试（一次 1~4 只），标满 {@link #CAP} 约需 7 秒。
 *
 * <p><b>和原版怪物刷怪的三处关键区别</b>（都是需求）：
 * <ul>
 *   <li><b>不看玩家距离</b>：不要求在玩家 24~128 格之间，也不管附近有没有玩家。
 *       能刷到哪儿只受「扫描半径」与「区块是否在区块刻范围内」限制；</li>
 *   <li><b>不看光照、不看难度、不看群系</b>：不调 {@code SpawnPlacements.checkSpawnRules}，
 *       也不读 {@code monster_spawn_light_level}；</li>
 *   <li><b>不摇权重</b>：该维度只有选择器一种自然生成实体（见 {@code ModBiomes.LUCKY_PLAINS} 的空刷怪表），
 *       所以直接生成，不用原版那套加权随机表。</li>
 * </ul>
 *
 * <p><b>数量上限</b>{@link #CAP} = <b>70</b>（固定值，不按区块换算，
 * 也<b>与玩家数量无关</b>）。上限是<b>硬上限</b>：既拦住新生成，也会把已经超编的存量削掉
 * （见 {@link #trimExcess}）——存量不会被任何其它规则削减（选择器只在离玩家 &gt;128 格时被删），
 * 所以旧存档里超过 70 的那部分会被逐步清掉。
 */
public final class LuckySelectorSpawner {

    /** 原版配比里的除数：17² = 289（{@code NaturalSpawner.MAGIC_NUMBER}，NaturalSpawner.java:54）。 */
    public static final int MAGIC_NUMBER = 17 * 17;

    /**
     * 数量上限：<b>70 只</b>。
     * <p>
     * 来历：最早取原版那个"每 289 个可刷区块 70 只"的 70（固定值，不按区块换算，
     * 也<b>与玩家数量无关</b>）；6.4.31 按需求"减为原来的 1/4"改成 17；
     * 6.4.35 按需求<b>改回 70</b>。
     * <p>
     * 上限是<b>硬上限</b>：既拦住新生成，也会把已经超编的存量削掉
     * （见 {@link #trimExcess}）——存量不会被任何其它规则削减（选择器只在离玩家 &gt;128 格时被删），
     * 所以旧存档里超过 70 的那部分会被逐步清掉。
     * <p>
     * 注意它与"同时有几只在干活"是两件事：上限 70 说的是<b>整个维度一共留几只</b>，
     * 而"一个玩家附近至多 3 只去找目标"由 {@code SelectorWorkSlots} 单独管——
     * 调到 70 只会让维度里游荡的选择器更多，不会让你身边同时扑上 70 只。
     */
    public static final int CAP = 70;

    /**
     * 两次生成尝试之间的间隔（刻）：<b>5 刻</b>（1 秒 4 次）。
     * <p>
     * <b>为什么必须节流</b>：{@link LuckyDimensionSpawner} 每刻会对半径内<b>所有</b>候选区块
     * （128 格 ≈ 208 个）各调一次本类。不节流的话，进入维度的第一刻就会把这 208 次机会连着用掉，
     * 一直到填满上限——表现就是「一口气全刷出来」，而且全挤在扫描顺序上连着的那些区块里（相邻区块
     * 在扫描序里是挨着的），既造成一次卡顿，又让选择器扎堆在某一片。节流之后每 5 刻才落一批，
     * 而每次「第一个被扫到的候选区块」因为扫描起点每刻随机而落在范围里的任意位置，长期分布就均匀了。
     */
    private static final int SPAWN_INTERVAL_TICKS = 5;

    /** 一次刷新出的数量范围（含两端）：1~4 只。 */
    private static final int MIN_GROUP = 1;
    private static final int MAX_GROUP = 4;

    /** 同一批里其它几只相对批次起点的水平偏移范围（格）：±5，和原版"包内游走"同一个量级。 */
    private static final int GROUP_SPREAD = 5;

    /** 单只最多试几个候选柱子（批次起点之外的位置）。 */
    private static final int POSITION_ATTEMPTS = 4;

    /** 超编清理每刻最多删几只（免得一次性删掉一大批造成卡顿）。 */
    private static final int MAX_TRIMS_PER_TICK = 2;

    /**
     * 超编清理时，离任何玩家近于这个距离（格）的选择器一律不动。
     * <p>
     * 玩家身边那只多半是正在用 {@code /jafa seek} 的，或者刚用刷怪蛋放下的，删了就说不清了。
     */
    private static final double TRIM_KEEP_DISTANCE = 32.0D;

    // ===== 每刻缓存（只在服务端主线程用，不需要同步）=====

    private static ServerLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    /** 本刻开始时统计到的选择器数量。 */
    private static int cachedSelectors;
    /** 本刻已经刷出来的数量（一并算进上限判断，防止同一刻冲过上限）。 */
    private static int spawnedThisTick;
    /**
     * 上一次生成尝试所在刻。初值取 {@code -SPAWN_INTERVAL_TICKS}（而不是 {@code Long.MIN_VALUE}）：
     * 后者做减法会溢出，{@code MIN_VALUE > now - 5} 恒成立，节流就永远打不开
     * （同一个坑在本模组的 {@code OrbPossessionSummons}、{@code LuckyMobSpawner} 都踩过）。
     */
    private static long lastSpawnTick = -SPAWN_INTERVAL_TICKS;

    private LuckySelectorSpawner() {
    }

    /**
     * 每刻由 {@link LuckyDimensionSpawner} 对每个候选区块调用一次。
     * <p>
     * 只会真的尝试一次：本刻第一个通过节流与上限检查的调用会占掉这一轮（见 {@link #lastSpawnTick}），
     * 同一刻后面的调用全部直接返回。
     *
     * @param level 该区块所在的维度；不是幸运维度就直接返回
     * @param chunk 候选区块
     */
    public static void trySpawn(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) {
            return;
        }
        ensureTickCache(level);
        if (cachedSelectors + spawnedThisTick >= CAP) {
            return;
        }
        long tick = level.getGameTime();
        if (lastSpawnTick > tick - SPAWN_INTERVAL_TICKS) {
            return; // 节流：还没到下一批
        }
        // 先占掉这一轮，再去找落点：否则同一刻后面那两百多个候选区块会接着刷
        lastSpawnTick = tick;

        RandomSource random = level.random;
        int group = MIN_GROUP + random.nextInt(MAX_GROUP - MIN_GROUP + 1);
        // 批次起点：区块内的随机一列
        int baseX = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int baseZ = chunk.getPos().getMinBlockZ() + random.nextInt(16);

        for (int i = 0; i < group; i++) {
            if (cachedSelectors + spawnedThisTick >= CAP) {
                return;
            }
            BlockPos pos = findGroupPosition(level, baseX, baseZ, i == 0, random);
            if (pos != null && spawnOne(level, pos)) {
                spawnedThisTick++;
            }
        }
    }

    /** 每刻重置缓存：重新统计一次实体数量（顺带把超编的存量削掉）。上限是常数，不需要每刻换算。 */
    private static void ensureTickCache(ServerLevel level) {
        long tick = level.getGameTime();
        if (level == cachedLevel && tick == cachedTick) {
            return;
        }
        cachedLevel = level;
        cachedTick = tick;
        cachedSelectors = countSelectors(level);
        spawnedThisTick = 0;
        if (cachedSelectors > CAP) {
            trimExcess(level);
        }
    }

    /**
     * 统计该维度里存活的选择器数量。
     * {@code getAllEntities()} 遍历的是"当前还在内存里的实体段"，也就是已加载区块里的实体。
     * 内含东西的选择器会带 {@code PersistenceRequired}，不会因为离玩家远而消失，所以它们同样占名额。
     */
    private static int countSelectors(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LuckySelectorEntity selector && selector.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 超编清理：让 {@link #CAP} 成为<b>硬上限</b>——超过的部分从最远的开始删，每刻最多
     * {@link #MAX_TRIMS_PER_TICK} 只。
     * <p>
     * <b>为什么需要它</b>：本维度删选择器只有一条规则——离最近玩家 &gt;128 格
     * （{@code MobLuckyDespawnMixin}）。半径内的存量不会被任何规则削减，于是上限只拦得住<b>新生成</b>，
     * 削不掉<b>存量</b>：旧版本上限还是 196 时刷出来的那一批会一直赖在半径里，
     * 之后无论怎么改上限，玩家看到的都还是那一百多只。这里补上削减。
     * <p>
     * <b>只删"确定没人要"的</b>：内含物品/生物（{@code PersistenceRequired} 或带着东西）的不动，
     * 参与骑乘关系的不动，离任何玩家 32 格以内的不动（那是玩家正在用的或刚放下的）。
     * 三样都不满足才进入候选，然后<b>从远到近</b>删——先把看不到的削掉，玩家身边那圈最后才轮到。
     */
    private static void trimExcess(ServerLevel level) {
        int excess = cachedSelectors - CAP;
        if (excess <= 0) {
            return;
        }
        double keepSqr = TRIM_KEEP_DISTANCE * TRIM_KEEP_DISTANCE;
        List<LuckySelectorEntity> candidates = new ArrayList<>();
        // 距离先算一遍存下来：排序的比较器会被调用很多次，不缓存的话每比一次都要再遍历一遍玩家
        Map<LuckySelectorEntity, Double> distances = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LuckySelectorEntity selector) || !selector.isAlive()) {
                continue;
            }
            if (selector.hasContainedContent() || selector.isPersistenceRequired()) {
                continue;
            }
            if (selector.isPassenger() || selector.isVehicle()) {
                continue;
            }
            double distance = nearestPlayerDistanceSqr(selector, level);
            if (distance < keepSqr) {
                continue;
            }
            candidates.add(selector);
            distances.put(selector, distance);
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(
            (LuckySelectorEntity selector) -> -distances.getOrDefault(selector, Double.MAX_VALUE)));
        int budget = Math.min(excess, MAX_TRIMS_PER_TICK);
        for (int i = 0; i < budget && i < candidates.size(); i++) {
            candidates.get(i).discard();
            cachedSelectors--; // 本刻的生成判断也要跟上，免得刚削完又填回去
        }
    }

    /** 到最近玩家的距离平方；本维度没有玩家时返回 {@code Double.MAX_VALUE}（视为最远、优先削）。 */
    private static double nearestPlayerDistanceSqr(Entity entity, ServerLevel level) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            nearest = Math.min(nearest, player.distanceToSqr(entity));
        }
        return nearest;
    }

    /**
     * 给批次里的第 {@code index} 只找落点：第一只就用批次起点，其余在起点附近 ±{@link #GROUP_SPREAD} 格里试。
     *
     * @return 找到的落点；这一只找不到就返回 null（跳过它，不影响批次里其它几只）
     */
    @Nullable
    private static BlockPos findGroupPosition(
            ServerLevel level, int baseX, int baseZ, boolean first, RandomSource random) {
        if (first) {
            return findSpawnPos(level, baseX, baseZ);
        }
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            int x = baseX + random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
            int z = baseZ + random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
            BlockPos pos = findSpawnPos(level, x, z);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 求一根柱子上的合法落点，不合法返回 null。
     * <p>
     * y 直接取 {@code Heightmap.Types.WORLD_SURFACE}：它的判定谓词就是"非空气"（Heightmap.java:141 与 :25），
     * 于是 {@code Level#getHeight} 给出的正是<b>最高非空气方块的 y 值 + 1</b>
     * （{@code Level.java:383-389} 的那个 {@code +1} 抵消了 {@code ChunkAccess.getHeight} 的 {@code -1}，
     * 见 ChunkAccess.java:195）。用它当落点同时满足两条要求：
     * <ul>
     *   <li><b>尽量在地表</b>：它就是这一列最上面的空位；</li>
     *   <li><b>头顶没有非空气方块</b>：这是该高度图的定义本身，不需要额外扫描。</li>
     * </ul>
     * 另外要求脚下必须是<b>幸运维度方块</b>，这样选择器只会站在该维度的地形之上。
     */
    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir()) {
            // 高度图与方块状态理论上一致，这里只是兜底（例如别的代码刚改过方块、高度图还没更新）
            return null;
        }
        if (!level.getBlockState(pos.below()).is(ModBlocks.LUCKY_DIMENSION_BLOCK.get())) {
            return null;
        }
        return pos;
    }

    /** 生成一只选择器并加入世界。 */
    private static boolean spawnOne(ServerLevel level, BlockPos pos) {
        LuckySelectorEntity selector = ModEntities.LUCKY_SELECTOR.get().create(level);
        if (selector == null) {
            return false;
        }
        RandomSource random = level.random;
        selector.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        // 走一遍正式的生成收尾。难度照传，但本类不拿它做任何判断（需求：生成无视游戏难度）。
        selector.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        // 自然生成的标记：永久发光 + PersistenceRequired 与"是否内含东西"对齐
        selector.markNaturalSpawn();
        return level.addFreshEntity(selector);
    }
}
