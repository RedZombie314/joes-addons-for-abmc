package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEvent;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEventCategory;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 幸运维度里的<b>随机生物</b>自然生成。
 *
 * <p><b>规则</b>：
 * <ul>
 *   <li>只刷原版生物（命名空间 {@code minecraft}），排除<b>监守者 / 末影龙 / 凋灵</b>；</li>
 *   <li>一次只刷一只（不是原版那种 1~4 的"包"）；</li>
 *   <li>数量上限 <b>{@link #MOB_CAP}</b>（平铺；<b>与玩家数量无关</b>——多个玩家共享同一份）；</li>
 *   <li><b>按面积配额，尽量铺匀</b>：每个区块最多留 {@link #MOBS_PER_CHUNK} 只
 *       （= 上限 ÷ 半径内的区块数），所以不会出现"某一片挤满、别处一只没有"；</li>
 *   <li>生成节奏由 <b>{@link #SPAWN_INTERVAL_TICKS}</b> 控制：当前 1 刻，即<b>每刻最多一只</b>；</li>
 *   <li>范围<b>128 格</b>：筛选在 {@link LuckyDimensionSpawner} 里做（它按 128 格另开了一份去重集合）；</li>
 *   <li><b>不看光照、不看难度</b>（不调 {@code SpawnPlacements}，也不读 {@code monster_spawn_light_level}）；</li>
 *   <li>落点地表 : 地下 = <b>9 : 1</b>（与掉落物同一条规则，共用
 *       {@link LuckyDimensionSpawner#findSpawnPos}）；</li>
 *   <li><b>幸运生物 : 普通生物 = 1 : 4</b>：走幸运那条时，直接执行<b>幸运生物池</b>
 *       （{@link #CREATURE_POOL_IDS}）里某个"幸运实体"子事件（见 {@link #spawnLuckyCreature}），
 *       因此猫群、Bob、猪塔、女巫+恶魂、骷髅骑士、狼，以及 6.5.5 新加的远古守卫者、兔子骑鸡、金粒猪、
 *       雪傀儡骑蝙蝠、金马、烟花猪、苦力怕都是<b>原样的配置</b>，不存在"再抄一份走偏"的问题。
 *       注意池子是<b>白名单</b>：同属"幸运实体"分类但刷的不是生物的那几个（落铁砧、冲天烟花、反弹盾牌、
 *       落红石块+TNT）不会被当成自然生成刷出来。</li>
 * </ul>
 *
 * <p>生成出来的生物会被贴上维度规则：不捡装备、和平共处 AI、清掉 {@code PersistenceRequired}
 * （详见 {@link LuckyDimensionMobs}）。水生生物还会进入"空气即水"模式。
 */
public final class LuckyMobSpawner {

    /**
     * 数量上限。<b>280</b>（需求 6.4.32："生物数量上限进一步削减为 280"）。
     * <p>
     * 历史：原本 70 → 700 → 350（6.4.31 减半）→ 280。
     * <p>
     * <b>与玩家数量无关</b>：这是整个维度共享的一个平铺上限（多个玩家一起玩也是这一份，不会翻倍）。
     * 每区块的密度配额 {@link #MOBS_PER_CHUNK} 由它自动换算，所以上限变化时分布密度跟着一起变。
     */
    public static final int MOB_CAP = 280;

    /** 关闭生成间隔限制用的值（必须声明在 {@link #SPAWN_INTERVAL_TICKS} 之前：Java 不允许静态字段前向引用）。 */
    public static final int NO_INTERVAL = 0;

    /**
     * 两次生成之间至少间隔多少刻。<b>当前 1 刻 = 每刻最多一只</b>（每秒 20 只）。
     * <p>
     * <b>为什么从 {@link #NO_INTERVAL}（不节流）改成 1</b>：不节流时，进入维度的那一刻会把
     * <b>当前已加载</b>的候选区块全部走一遍、一路填到上限——而刚进维度时已加载的正好是玩家身边那几圈，
     * 于是 700 只全砸在门口，形成"一进门一堆、跑开就空"的分布；这一下同时还是几百次生成的卡顿源。
     * 改成每刻一只之后：<ul>
     *   <li>不会再有"一刻几百只"的卡顿；</li>
     *   <li>落点每刻重新从随机起点扫，所以是<b>一只一只均匀撒到整个 128 格范围</b>，而不是灌进门口那几圈；</li>
     *   <li>上限照样能到：本维度只有「离玩家 &gt;128 格」这一条删除规则，生物不会莫名其妙消失，
     *       20 只/秒 × 约 18 秒就能把 {@link #MOB_CAP}（350）填满（早先填不满是因为当时还有"闲置消失"）。</li>
     * </ul>
     */
    public static final int SPAWN_INTERVAL_TICKS = 1;

    /**
     * 每个区块最多留几只生物 = {@link #MOB_CAP} ÷ 半径内的区块数（≈ 280 / 208 ≈ 1.35）。
     * <p>
     * <b>为什么需要它</b>：{@link #MOB_CAP} 是"整个维度共享一个总数"，它<b>只管总数、不管分布</b>——
     * 只要总数没到 280，谁先被扫到谁就能刷。于是门口那几圈先把总数吃满，玩家跑开之后别处就没有名额了，
     * 表现就是"进维度一堆、跑几十格就稀"。
     * <p>
     * 加上这条之后，每个区块各自有个小配额，总数自然就是"上限 ÷ 区块数 × 区块数 = 上限"，
     * 而分布是<b>均匀</b>的：先被填的都是空区块，密集的区块会被跳过，玩家跑到哪儿，
     * 哪儿的密度都一样（每区块 1~2 只，约每 16~20 格一只）。
     * <p>
     * 取值刻意用<b>小数平均值</b>而不是 `ceil`：判据是 {@code 本区块只数 >= 1.35}，所以 1 只的区块还能再补一只、
     * 2 只的区块就跳过——最终每块都停在 1~2 只，总数正好在上限附近，不会出现"都堆在 2 只、总数超上限"
     * 或者"只填到 1 只、总数够不着上限"。半径内区块数与玩家数量无关，所以这个密度也<b>与玩家数量无关</b>。
     * <p>
     * 例外：{@link #spawnLuckyCreature} 的幸运事件会一次刷出一整群（猫群、狼群、猪塔……），
     * 它们可能让某个区块短暂超过配额——那是需求里"幸运生物"该有的样子，超了就跳过它、去填别处。
     */
    public static final double MOBS_PER_CHUNK = MOB_CAP
        / (double) LuckyDimensionSpawner.nominalChunkCount(LuckyDimensionSpawner.MOB_SPAWN_RADIUS_BLOCKS);

    /** 幸运生物与普通生物的权重比 1 : 4。 */
    private static final int LUCKY_WEIGHT = 1;
    private static final int NORMAL_WEIGHT = 4;

    /** 地表与地下的权重比 9 : 1（与掉落物一致）。 */
    /** 落点抽签里"抽到地下"的百分比（10 = 10%）；{@link LuckyPendingSpawns} 播种时也用这个值。 */
    public static final int UNDERGROUND_CHANCE = 10;

    /** 一只生物最多试几种体型/类型（大生物在地表可能塞不下）。 */
    private static final int FIT_ATTEMPTS = 6;

    /** 明确排除的三只。 */
    private static final Set<EntityType<?>> EXCLUDED = Set.of(
        EntityType.WARDEN, EntityType.ENDER_DRAGON, EntityType.WITHER);

    /**
     * <b>幸运生物池</b>：幸运"实体"分类里，哪些子事件是"刷生物的"、
     * 因而可以被幸运维度当成自然生成直接刷出来（{@link #spawnLuckyCreature}）。
     *
     * <h3>为什么是白名单而不是"排除掉不是生物的"</h3>
     * 早期写法是"取全部幸运实体子事件，再排掉唯一那个不刷生物的（{@code entity/falling_volley}）"。
     * 6.5.5 一次加了 12 个子事件，其中<b>不刷生物</b>的从 1 个变成 4 个
     * （落铁砧 {@code entity/falling_anvil}、冲天烟花 {@code entity/firework_volley}、
     * 反弹盾牌 {@code entity/reflect_shield} 也都在"幸运实体"分类里，但它们刷的是装置/道具，不是生物），
     * 黑名单会越拖越长、还容易漏；而且需求原话就是"<b>上述生物记得加入幸运生物池</b>"——
     * 也就是"生物"这件事要<b>显式登记</b>。于是反过来：只认这张名单，不在名单里的一律不自然生成。
     *
     * <p>名单里的 id 必须和事件类 {@code register()} 里写的 id 完全一致，否则那只生物在幸运维度里就不刷
     * （不会有报错，只是静默少了）。反过来某条名字写错了也不会崩：{@link #luckyCreatureEvents} 是按
     * {@link LuckyEvents#eventsOf} 现取现比，对不上的直接不进候选表。
     */
    private static final Set<ResourceLocation> CREATURE_POOL_IDS = Set.of(
        // ===== 原有 =====
        creatureId("entity/cats"),             // 猫群（猫 + 豹猫）
        creatureId("entity/zombie_bob"),       // 僵尸 Bob
        creatureId("entity/pig_tower"),        // 猪塔（猪 + 村民）
        creatureId("entity/witch_ghast"),      // 女巫 + 恶魂
        creatureId("entity/skeleton_knight"),  // 骷髅骑士（骷髅 + 骷髅马）
        creatureId("entity/wolf"),             // 狼
        // ===== 6.5.5 新增（需求 1~5、8、11 里的"生物"）=====
        creatureId("entity/elder_guardian"),   // 远古守卫者
        creatureId("entity/rabbit_chicken"),   // 骑着鸡的兔子
        creatureId("entity/gold_nugget_pig"),  // 头顶喷金粒的猪
        creatureId("entity/snow_golem_bat"),   // 骑着蝙蝠的雪傀儡
        creatureId("entity/golden_horse"),     // 金马铠 + 马鞍的马
        creatureId("entity/firework_pig"),     // 烟花猪
        creatureId("entity/creeper"));         // 苦力怕

    /** 幸运生物池里的 id 便捷构造（命名空间 = 本模组）。 */
    private static ResourceLocation creatureId(String path) {        return ResourceLocation.fromNamespaceAndPath(ModMain.MODID, path);
    }

    /** 原版生物池（注册表启动后不变，算一次即可）。 */
    private static List<EntityType<?>> spawnPool;

    // ===== 每刻缓存 =====

    private static ServerLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    /** 本刻统计到的生物数量（不含选择器，它有自己的上限）。 */
    private static int cachedMobs;
    /** 本刻已经刷出来的数量。 */
    private static int spawnedThisTick;
    /**
     * 上一次生成发生在哪一刻（用于 {@link #SPAWN_INTERVAL_TICKS} 限速）。
     * <p>
     * <b>初值刻意写成 {@code -SPAWN_INTERVAL_TICKS}，绝不是 {@code Long.MIN_VALUE}：</b>
     * 下面那道闸算的是 {@code now - lastSpawnTick}，而 {@code 小数字 - Long.MIN_VALUE} 在 64 位有符号里
     * 会溢出一个<b>大负数</b>（2^63 放不下），于是"距上次生成还差多少刻"永远小于间隔，闸门被<b>永久关死</b>
     * ——6.3.115 就是这样让生物一只都刷不出来的。（同一个坑在本项目里已经踩过并写明过：
     * {@code OrbPossessionSummons:255-257}、{@code OrbSkeletonKnightFlight:61-63}、
     * {@code OrbAllyWitherControl:92-94}。）
     */
    private static long lastSpawnTick = -SPAWN_INTERVAL_TICKS;

    private LuckyMobSpawner() {
    }

    /**
     * 候选区块（128 格内）被 {@link LuckyDimensionSpawner} 逐个交进来，每刻每区块最多尝试一次。
     * 真正能刷出一只还要过三道闸：<b>总上限</b>、<b>生成间隔</b>与<b>本区块的密度配额</b>。
     */
    public static void trySpawn(ServerLevel level, LevelChunk chunk) {
        // 6.5.23 起<b>不再被调用</b>：自然生成改由 {@link LuckyPendingSpawns}（"待生成位"）驱动，
        // 玩家 50 格内才解除生成、56 格外收回。本类保留的是它被复用的两样东西：
        // 生物池（spawnPool）与幸运生物事件（runLuckyCreatureEventAt），以及那几个密度常量
        // （MOBS_PER_CHUNK / MOB_CAP）作为新系统参数的出处。整段旧的连续生成逻辑先留着不删，方便对照。
        ensureTickCache(level);
        if (cachedMobs + spawnedThisTick >= MOB_CAP) {
            return;
        }
        // 间隔闸：上一只在 SPAWN_INTERVAL_TICKS 刻之内就不再刷。
        // 判据写成 `lastSpawnTick > now - SPAWN_INTERVAL_TICKS`（等价于"距上次生成不足间隔"），
        // 而不是 `now - lastSpawnTick < SPAWN_INTERVAL_TICKS`：后者一旦 lastSpawnTick 是哨兵/极小值
        // 就会把减法算溢出（见字段注释里那个坑），前者只做 `now - 常量` 这一步，永远不会溢出。
        long now = level.getGameTime();
        if (SPAWN_INTERVAL_TICKS > NO_INTERVAL
            && lastSpawnTick > now - SPAWN_INTERVAL_TICKS) {
            return;
        }
        // 密度闸：这个区块已经够密就跳过，把名额留给空区块（分布均匀的关键，见 MOBS_PER_CHUNK）
        if (countMobsInChunk(level, chunk) >= MOBS_PER_CHUNK) {
            return;
        }
        // 幸运生物 : 普通生物 = 1 : 4（每 5 次里有 1 次走幸运那条）
        if (level.random.nextInt(LUCKY_WEIGHT + NORMAL_WEIGHT) < LUCKY_WEIGHT) {
            if (spawnLuckyCreature(level, chunk)) {
                spawnedThisTick++;
                lastSpawnTick = now;
            }
            return;
        }
        if (spawnRandomVanillaMob(level, chunk)) {
            spawnedThisTick++;
            lastSpawnTick = now;
        }
    }

    /**
     * 这个区块里现在有几只生物（不含选择器——它们有自己的上限，不该占用生物的密度配额）。
     * <p>
     * 用整根区块柱的包围盒去查实体段，所以只会命中这个区块 xz 范围内的生物；一次查询的开销与
     * 「这根柱子里有多少生物」成正比，逐区块加起来就是「本刻所有生物各被访问一次」的量级，
     * 和 {@link #ensureTickCache} 那次全量统计同阶。
     * <p>
     * 上限满了之后根本不会走到这里（前面那道闸先返回），所以稳态下这个查询一次都不做。
     */
    private static int countMobsInChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        AABB column = new AABB(
            pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
        int count = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, column)) {
            if (mob.isAlive() && !(mob instanceof LuckySelectorEntity)) {
                count++;
            }
        }
        return count;
    }

    /** 每刻重置缓存：顺手把水生生物的每刻维护跑掉（省一遍实体扫描）。 */
    private static void ensureTickCache(ServerLevel level) {
        long tick = level.getGameTime();
        if (level == cachedLevel && tick == cachedTick) {
            return;
        }
        cachedLevel = level;
        cachedTick = tick;
        spawnedThisTick = 0;
        cachedMobs = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive() && !(mob instanceof LuckySelectorEntity)) {
                cachedMobs++;
                LuckyDimensionMobs.tickAquatic(mob);
            }
        }
    }

    /** 普通分支：从原版生物池里随机抽一种，塞得下就生成。 */
    private static boolean spawnRandomVanillaMob(ServerLevel level, LevelChunk chunk) {
        BlockPos pos = LuckyDimensionSpawner.findSpawnPos(level, chunk, UNDERGROUND_CHANCE);
        if (pos == null) {
            return false;
        }
        return spawnRandomMobAt(level, pos) != null;
    }

    /**
     * 在<b>指定坐标</b>生成一只随机原版生物（生物池 → 碰撞校验 → 摆放 → 生成收尾）。
     * <p>
     * 抽出来给"待生成位"用（{@link LuckyPendingSpawns}）：那种系统在<b>解除变形</b>的那一刻
     * 才知道自己要生成在哪（位里存着地表坐标），所以不能再从 {@code LevelChunk} 现找落点。
     */
    public static Mob spawnRandomMobAt(ServerLevel level, BlockPos pos) {
        List<EntityType<?>> pool = spawnPool();
        if (pool.isEmpty()) {
            return null;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < FIT_ATTEMPTS; attempt++) {
            EntityType<?> type = pool.get(random.nextInt(pool.size()));
            if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
                continue;
            }
            return spawnMob(level, type, pos);
        }
        return null;
    }

    /** 幸运分支：执行一个"幸运实体"子事件（它自己会刷出对应的一群/一只生物）。 */
    private static boolean spawnLuckyCreature(ServerLevel level, LevelChunk chunk) {
        BlockPos pos = LuckyDimensionSpawner.findSpawnPos(level, chunk, UNDERGROUND_CHANCE);
        if (pos == null) {
            return false;
        }
        return runLuckyCreatureEventAt(level, pos);
    }

    /**
     * 在<b>指定坐标</b>执行一个"幸运实体"子事件（猫群 / 猪塔 / 女巫+恶魂 / 骷髅骑士 / 狼……）。
     * <p>
     * 同 {@link #spawnRandomMobAt}：给"待生成位"用。事件刷出来的可能是一群，
     * 调用方（{@code LuckyPendingSpawns}）会在事后就近收集这些实体并登记，所以这里只管执行。
     */
    public static boolean runLuckyCreatureEventAt(ServerLevel level, BlockPos pos) {
        List<LuckyEvent> events = luckyCreatureEvents(level);
        if (events.isEmpty()) {
            return false;
        }
        LuckyEvent event = events.get(level.random.nextInt(events.size()));
        LuckyDimensionMobs.beginNaturalSpawn();
        try {
            // 原样执行：玩家参数传 null（自然生成没有"破坏方块的玩家"）
            event.run(level, pos, null);
        } finally {
            LuckyDimensionMobs.endNaturalSpawn();
        }
        return true;
    }

    /** 幸运生物池里的那些子事件（每次现取，数据包/注册表改动都能跟上；不在池里的不刷）。 */
    private static List<LuckyEvent> luckyCreatureEvents(ServerLevel level) {
        List<LuckyEvent> list = new ArrayList<>();
        for (LuckyEvent event : LuckyEvents.eventsOf(level, LuckyEventCategory.LUCKY_ENTITY)) {
            if (CREATURE_POOL_IDS.contains(event.id())) {
                list.add(event);
            }
        }
        return list;
    }

    /** 造一只、摆好位置、走完生成收尾，然后加进世界（加进去的那一刻会被套上维度规则）。 */
    private static Mob spawnMob(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
            level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        LuckyDimensionMobs.beginNaturalSpawn();
        try {
            // 需求 1：本维度永远不刷 CanPickUpLoot=1b 的生物
            mob.setCanPickUpLoot(false);
            return level.addFreshEntity(mob) ? mob : null;
        } finally {
            LuckyDimensionMobs.endNaturalSpawn();
        }
    }

    /**
     * 原版生物池：命名空间 {@code minecraft}、且不属于 {@link MobCategory#MISC}。
     * <p>
     * 用分类过滤掉的东西正好是"不是生物"的那些（掉落物、船、矿车、展示实体、画、经验球……），
     * 以及不是自然生物的铁傀儡/雪傀儡（它们在原版里就是 MISC）。
     * 想要更精细的名单（比如把巨人、远古守卫者也排掉）改这里即可。
     */
    private static List<EntityType<?>> spawnPool() {
        if (spawnPool == null) {
            List<EntityType<?>> list = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!"minecraft".equals(id.getNamespace())) {
                    continue;
                }
                if (type.getCategory() == MobCategory.MISC) {
                    continue;
                }
                if (EXCLUDED.contains(type)) {
                    continue;
                }
                list.add(type);
            }
            spawnPool = List.copyOf(list);
        }
        return spawnPool;
    }
}
