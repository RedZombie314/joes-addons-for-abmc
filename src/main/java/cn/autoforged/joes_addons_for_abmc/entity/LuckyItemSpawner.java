package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * 幸运维度里掉落物（{@link LuckyItemEntity}）的自然生成。
 *
 * <p><b>和 {@link LuckySelectorSpawner} 完全独立</b>：两者各有各的上限、各有各的每刻统计，
 * 互不影响（需求里的「属于不同的分区」）。触发点也一样：{@link LuckyDimensionSpawner} 每刻
 * 扫一遍候选区块，对每个区块调用本类一次。
 *
 * <p><b>规则</b>：
 * <ul>
 *   <li>数量上限 {@link #ITEM_CAP}（<b>350</b>）：整个维度共享一份平铺上限，
 *       <b>与玩家数量无关</b>；</li>
 *   <li><b>按面积配额，尽量铺匀</b>：每个区块最多留 {@link #ITEMS_PER_CHUNK} 个
 *       （= 原版密度 2800/289 ≈ 9.69 个/区块 的<b>一半</b> ≈ 4.84 个/区块，需求：
 *       "参考刷新随机生物的区块密度逻辑，把原来的物品刷新密度减半"）。
 *       掉落物<b>不会动</b>、只会原地待满 5 分钟，所以"先被扫到的区块把名额吃光"这件事比生物更明显——
 *       这条闸就是用来兜住它的：某个区块攒够 4 个就跳过，把名额让给别的区块；</li>
 *   <li>生成节奏由 {@link #SPAWN_INTERVAL_TICKS} 控制：当前 <b>1 刻 = 每刻最多一件</b>
 *       （与 {@link LuckyMobSpawner} 同一套做法）。不节流的话，进维度那一刻会把当前已加载的候选区块
 *       一口气走完、几百件东西砸在门口那一两圈里；每刻一件则是<b>一件一件均匀撒到整个范围</b>，
 *       {@link #ITEM_CAP} 件大约 18 秒填满；</li>
 *   <li><b>「可刷区块」的范围</b>：玩家周围 {@link LuckyDimensionSpawner#ITEM_SPAWN_RADIUS_BLOCKS} 格（当前 256）
 *       以内、且在区块刻范围内的已加载区块。原版刷怪被写死在 128 格（ServerChunkCache.java:367-375
 *       → ChunkMap.java:983-989），我们没有去动那个数（它还管着原版随机刻），而是自己扫一遍。</li>
 *   <li>一次只刷 1 只（1 个实体 = 1 件物品，数量 1）；</li>
 *   <li>不看玩家距离、不看光照、不看难度；</li>
 *   <li>落点：<b>9/10 在地表</b>（该列的最高非空气方块的 y 值 + 1，即高度图 {@code WORLD_SURFACE}），
 *       <b>1/10 在「最低建筑高度 ~ 该列地表」之间随机挑一个空气方块</b>
 *       （见 {@link LuckyDimensionSpawner#findSpawnPos}）；</li>
 *   <li>物品从 {@link LuckyItemPool} 里抽（全部原版生存可获取的物品 + 幸运物品池）。</li>
 * </ul>
 *
 * <p>生命周期不用管：它就是普通掉落物，会在 {@code ItemEntity.LIFETIME} 即 6000 刻 = <b>5 分钟</b>
 * 后自然消失（ItemEntity.java:39/55/203-208），且 {@code Age}/{@code Lifespan} 都会存档，读档后接着算。
 */
public final class LuckyItemSpawner {

    /** 原版配比里的除数：17² = 289（{@code NaturalSpawner.MAGIC_NUMBER}，NaturalSpawner.java:54）。 */
    public static final int MAGIC_NUMBER = 17 * 17;

    /**
     * 原版配比：每 {@link #MAGIC_NUMBER} 个可刷区块允许存活的掉落物数量（2800 ≈ 9.69 个/区块）。
     * <p>
     * <b>现在只用来推导密度闸</b>（{@link #ITEMS_PER_CHUNK}），不再当本维度的上限用——
     * 上限由需求给定的 {@link #ITEM_CAP} 直接决定（原来按这个配比换算出来是 ≈7790，太离谱了）。
     */
    private static final int VANILLA_ITEMS_PER_MAGIC_NUMBER = 2800;

    /**
     * 本维度掉落物的数量上限（<b>350</b>）。
     * <p>
     * 来历：6.4.30 按需求定成 350（原来按原版配比换算是 ≈7790）；6.4.31 减半到 175；
     * 6.4.32 削减到 70；6.4.34 按需求<b>改回 350</b>。
     * <p>
     * <b>与玩家数量无关</b>：整个维度共享这一个平铺上限（多个玩家一起玩也是这一份，不会翻倍），
     * 与选择器上限 {@code LuckySelectorSpawner.CAP}、生物上限 {@link LuckyMobSpawner#MOB_CAP} 同一原则。
     */
    public static final int ITEM_CAP = 350;

    /** 密度减半的除数（需求："把原来的物品刷新密度减半"）。 */
    private static final int DENSITY_DIVISOR = 2;

    /**
     * 每个区块最多留几个掉落物 = 原版密度 ÷ {@value #DENSITY_DIVISOR}（2800 / 289 / 2 ≈ 4.84）。
     * <p>
     * 取<b>小数平均值</b>而不是 {@code ceil}，和 {@link LuckyMobSpawner#MOBS_PER_CHUNK} 同一个理由：
     * 判据是 {@code 本区块件数 >= 4.84}，所以 4 件的区块还能再补一件、5 件就跳过，
     * 每个区块稳定停在 4~5 件。
     * <p>
     * 注意它的量级：{@link #ITEM_CAP}（350）摊到 256 格半径的 812 个区块上，平均每区块才 0.43 件，
     * 所以这条闸<b>平时根本不会触发</b>；它兜的是"极端聚集"——某一片区块攒到 5 件以上时跳过它，
     * 逼着生成器去别处，不至于在门口一小片地方堆满。
     * <p>
     * 需求里的"物品刷新量"改的是总数 {@link #ITEM_CAP}，<b>不动这条密度闸</b>——
     * 它早先已经按需求定成"原版密度的一半"，是两个独立的量。
     */
    public static final double ITEMS_PER_CHUNK =
        VANILLA_ITEMS_PER_MAGIC_NUMBER / (double) MAGIC_NUMBER / DENSITY_DIVISOR;

    /** 地表与地下的权重比 9:1 —— 每 10 次里有 1 次走地下随机空气方块（落点算法见 {@link LuckyDimensionSpawner#findSpawnPos}）。 */
    private static final int UNDERGROUND_CHANCE = 10;

    /** 关闭生成间隔限制用的值（必须声明在 {@link #SPAWN_INTERVAL_TICKS} 之前：Java 不允许静态字段前向引用）。 */
    public static final int NO_INTERVAL = 0;

    /**
     * 两次生成之间至少间隔多少刻。<b>当前 1 刻 = 每刻最多一件</b>（每秒 20 件）。
     * <p>
     * 与 {@link LuckyMobSpawner#SPAWN_INTERVAL_TICKS} 完全同一套理由：不节流时进入维度的那一刻
     * 会把<b>当前已加载</b>的候选区块全部走一遍、一路填到上限，而刚进维度时已加载的正好是玩家身边那几圈，
     * 于是几百件东西全砸在门口（既是卡顿源，也是"密度不均"的根源）；改成每刻一件之后，
     * 落点每刻重新从随机起点扫，就是一件一件均匀撒到整个 256 格范围。
     * <p>
     * 不想节流的话把它改成 {@link #NO_INTERVAL}（生成逻辑里的闸会自动失效）。
     */
    public static final int SPAWN_INTERVAL_TICKS = 1;

    // ===== 每刻缓存（与选择器各自独立）=====

    private static ServerLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    /** 本刻开始时统计到的掉落物数量。 */
    private static int cachedItems;
    /** 本刻已经刷出来的数量。 */
    private static int spawnedThisTick;

    /**
     * 上一次生成发生在哪一刻（用于 {@link #SPAWN_INTERVAL_TICKS} 限速）。
     * <p>
     * <b>初值刻意写成 {@code -SPAWN_INTERVAL_TICKS}，绝不是 {@code Long.MIN_VALUE}：</b>
     * 下面那道闸算的是 {@code now - lastSpawnTick}，而 {@code 小数字 - Long.MIN_VALUE} 在 64 位有符号里
     * 会溢出一个<b>大负数</b>，于是闸门会被<b>永久关死</b>（同一个坑在 {@link LuckyMobSpawner} 里写明过）。
     */
    private static long lastSpawnTick = -SPAWN_INTERVAL_TICKS;

    private LuckyItemSpawner() {
    }

    /**
     * 由 {@link LuckyDimensionSpawner} 每刻对每个候选区块调用一次（与选择器同一个驱动，各自独立计数）。
     * 真正能刷出一件还要过三道闸：<b>总上限</b>、<b>生成间隔</b>与<b>本区块的密度配额</b>。
     */
    public static void trySpawn(ServerLevel level, LevelChunk chunk) {
        // 6.5.23 起<b>不再被调用</b>：物品自然生成改由 {@link LuckyPendingSpawns}（"待生成位"）驱动，
        // 玩家 50 格内才解除生成、56 格外收回；本类保留的是密度常量（ITEMS_PER_CHUNK / ITEM_CAP）
        // 与 LuckyItemPool 那一套抽物品的规则，新的生成走 {@code LuckyPendingSpawns#spawnItemEntity}。
        if (!level.dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) {
            return;
        }
        ensureTickCache(level);
        // 1) 总上限
        if (cachedItems + spawnedThisTick >= ITEM_CAP) {
            return;
        }
        // 2) 间隔闸：上一件在 SPAWN_INTERVAL_TICKS 刻之内就不再刷。
        // 判据写成 `lastSpawnTick > now - SPAWN_INTERVAL_TICKS`（等价于"距上次生成不足间隔"），
        // 而不是 `now - lastSpawnTick < SPAWN_INTERVAL_TICKS`：后者一旦 lastSpawnTick 是哨兵/极小值
        // 就会把减法算溢出（见字段注释里那个坑），前者只做 `now - 常量` 这一步，永远不会溢出。
        long now = level.getGameTime();
        if (SPAWN_INTERVAL_TICKS > NO_INTERVAL
            && lastSpawnTick > now - SPAWN_INTERVAL_TICKS) {
            return;
        }
        // 3) 密度闸：这个区块已经够密就跳过，把名额留给空区块（见 ITEMS_PER_CHUNK）
        if (countItemsInChunk(level, chunk) >= ITEMS_PER_CHUNK) {
            return;
        }
        if (spawnOne(level, chunk)) {
            spawnedThisTick++;
            lastSpawnTick = now;
        }
    }

    /** 每刻重置缓存：重新统计一次实体数量。上限是常数，不需要每刻换算。 */
    private static void ensureTickCache(ServerLevel level) {
        long tick = level.getGameTime();
        if (level == cachedLevel && tick == cachedTick) {
            return;
        }
        cachedLevel = level;
        cachedTick = tick;
        cachedItems = countItems(level);
        spawnedThisTick = 0;
    }

    /** 统计该维度里存活的幸运掉落物数量（只算本模组这一种，和选择器互不干扰）。 */
    private static int countItems(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LuckyItemEntity && entity.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 这个区块里现在有几件幸运掉落物（{@link #ITEMS_PER_CHUNK} 密度闸的判据）。
     * <p>
     * 与 {@link LuckyMobSpawner} 数生物那套完全一样：用整根区块柱的包围盒查实体段，所以只命中这个区块
     * xz 范围内的掉落物。只数 {@link LuckyItemEntity}——普通掉落物（玩家丢的、生物掉的）不占这个配额，
     * 否则玩家在同一个区块里丢一地东西就会把自然生成卡住。
     * <p>
     * 总上限满了之后根本不会走到这里（前面那道闸先返回），所以稳态下这个查询一次都不做。
     */
    private static int countItemsInChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        AABB column = new AABB(
            pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
        int count = 0;
        for (LuckyItemEntity item : level.getEntitiesOfClass(LuckyItemEntity.class, column)) {
            if (item.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** 抽一件物品、找一个落点、生成一个掉落物实体。 */
    private static boolean spawnOne(ServerLevel level, LevelChunk chunk) {
        ItemStack stack = LuckyItemPool.roll(level);
        if (stack.isEmpty()) {
            return false;
        }
        BlockPos pos = LuckyDimensionSpawner.findSpawnPos(level, chunk, UNDERGROUND_CHANCE);
        if (pos == null) {
            return false;
        }
        LuckyItemEntity entity = ModEntities.LUCKY_ITEM.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.setItem(stack);
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        // 自然生成的标记：谁也别想走过去把它捡走（只能右键）
        entity.markNaturalSpawn();
        return level.addFreshEntity(entity);
    }
}
