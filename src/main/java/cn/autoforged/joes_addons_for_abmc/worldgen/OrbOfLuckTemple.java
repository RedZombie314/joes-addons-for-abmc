package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「幸运宝珠神庙」（{@code data/joes_addons_for_abmc/structure/orb_of_luck_temple.nbt}）
 * 在<b>幸运维度</b>里的一次性生成。
 *
 * <h3>为什么不用世界生成（structure_set）而用代码放置</h3>
 * 需求里有三条是数据包表达不了的：整个存档<b>只生成一座</b>、要避开<b>玩家初次进入幸运维度所在坐标</b>周围
 * 5 个区块、要在<b>附近最高的地形（小山坡顶部）</b>上生成。所以改成「玩家第一次进入幸运维度时」在主线程上
 * 选址并放置，状态存进 {@link ModMain.SharedCounts}（主世界维度数据，跨会话、多人共享）。
 *
 * <h3>流程（每服务端刻调用一次，全部在主线程）</h3>
 * <ol>
 *   <li><b>记录初入坐标</b>：第一次发现有玩家身处幸运维度时，把他的坐标记进存档（只记一次）；</li>
 *   <li><b>选址</b>：以初入坐标为中心，在 80~192 格（5~12 区块）的环带里用 16 格网格粗扫一遍世界生成高度图，
 *       按高度从高到低取若干候选，逐个用 8 格步长探一遍 26×26 底面：<b>底面高差 ≤ {@link #MAX_SURFACE_SPREAD}</b>
 *       就接受（保证是"山顶平台"而不是陡坡）；近处找不到平台就把范围扩大到 512 格再找一遍；</li>
 *   <li><b>分帧生成区块</b>：神庙 26×26 可能跨 3×3 个区块，每刻最多加载 {@link #CHUNKS_PER_TICK} 个，
 *       避免单刻同步生成太多区块造成卡顿；</li>
 *   <li><b>放置</b>：区块全部就绪后，用真实高度图取底面 26×26 里最低的地表方块 y 作为结构最小角
 *       （保证底面不会悬空），再一次写入整个模板——模板里的空气同样是"方块"，会把结构体积内的地形整片替换掉；
 *       因为是直接 setBlock 而不是破坏方块，<b>不会产生掉落物</b>。放完把「已生成」写进存档，从此不再生成第二座。</li>
 * </ol>
 */
public final class OrbOfLuckTemple {

    /** 结构模板 id（对应 {@code data/joes_addons_for_abmc/structure/orb_of_luck_temple.nbt}）。 */
    private static final ResourceLocation TEMPLATE_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "orb_of_luck_temple");

    /** 初入坐标周围多少区块内禁止生成（结构必须整体落在该范围之外）。 */
    private static final int EXCLUSION_CHUNKS = 5;
    /** 选址环带：候选中心与初入坐标的距离（格）。5 区块 = 80 格。 */
    private static final int MIN_SEARCH_RADIUS = 80;
    /** 第一轮搜索半径：12 区块。 */
    private static final int MAX_SEARCH_RADIUS = 192;
    private static final int COARSE_STEP = 16;
    /** 第二轮（兜底）搜索半径：32 区块，网格放粗以控制采样量。 */
    private static final int FAR_SEARCH_RADIUS = 512;
    private static final int FAR_STEP = 32;
    /** 每轮最多细看多少个候选（按高度从高到低）。 */
    private static final int MAX_CANDIDATES = 16;
    /** 候选底面高差探针的采样步长（世界生成高度图，代价较高，粗一点没关系）。 */
    private static final int PROBE_STEP = 8;
    /** 底面允许的最大高差：超过就认为落在陡坡上，继续看下一个候选。 */
    private static final int MAX_SURFACE_SPREAD = 5;
    /** 每刻最多同步生成几个区块。 */
    private static final int CHUNKS_PER_TICK = 2;
    /** 选址失败时的重试间隔（刻）：正常情况下选址不会失败，这只是防"每刻重扫"的保险。 */
    private static final int RETRY_COOLDOWN_TICKS = 600;
    /** 幸运输核心生成在雕纹石英平台<b>顶面往上</b>多少格。 */
    private static final double ORB_HEIGHT_ABOVE_PLATFORM = 2.0;

    /** 选址失败后的冷却（仅主线程读写）。 */
    private static int retryCooldown = 0;

    private OrbOfLuckTemple() {
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick(MinecraftServer server) {
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        try {
            tickOnce(server);
        } catch (Exception e) {
            // 选址/加载区块/放置都在服务端主线程上跑，异常若冒出去会直接崩服，这里兜住并稍后重试
            retryCooldown = RETRY_COOLDOWN_TICKS;
            ModMain.LOGGER.warn("[orb-temple] 生成流程异常，{} 刻后重试：{}", RETRY_COOLDOWN_TICKS, e.toString());
        }
    }

    private static void tickOnce(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ModMain.SharedCounts counts = ModMain.getSharedCountsStatic(overworld);

        // 已经生成过 → 本存档不再生成第二座
        if (counts.orbTemplePlaced) return;

        ServerLevel lucky = server.getLevel(ModDimensions.LUCKY_DIM_LEVEL);
        if (lucky == null) return;

        // 1) 记录「玩家初次进入幸运维度」的坐标（每个存档只记第一次）
        if (!counts.hasLuckyDimEntry()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.level().dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) continue;
                BlockPos pos = player.blockPosition();
                counts.luckyDimEntryX = pos.getX();
                counts.luckyDimEntryZ = pos.getZ();
                counts.setDirty();
                ModMain.LOGGER.info("[orb-temple] 记录玩家初次进入幸运维度的坐标 ({}, {})", pos.getX(), pos.getZ());
                break;
            }
            if (!counts.hasLuckyDimEntry()) return;
        }

        StructureTemplate template = lucky.getStructureManager().getOrCreate(TEMPLATE_ID);
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            // 模板缺失：记一笔并就此打住，避免每刻刷日志
            counts.orbTemplePlaced = true;
            counts.setDirty();
            ModMain.LOGGER.warn("[orb-temple] 未找到结构模板 {}，放弃生成", TEMPLATE_ID);
            return;
        }

        // 2) 还没选址 → 在初入坐标附近最高、最平缓的地方挑一个原点
        if (counts.orbTempleTarget == null) {
            int[] origin = searchOrigin(lucky, size, counts.luckyDimEntryX, counts.luckyDimEntryZ);
            if (origin == null) {
                retryCooldown = RETRY_COOLDOWN_TICKS;
                ModMain.LOGGER.warn("[orb-temple] 初入坐标 ({}, {}) 附近没找到合法选址，{} 刻后重试",
                    counts.luckyDimEntryX, counts.luckyDimEntryZ, RETRY_COOLDOWN_TICKS);
                return;
            }
            counts.orbTempleTarget = origin;
            counts.setDirty();
            ModMain.LOGGER.info("[orb-temple] 已选定神庙选址 ({}, {}, {})，初入坐标 ({}, {})",
                origin[0], origin[1], origin[2], counts.luckyDimEntryX, counts.luckyDimEntryZ);
            return;
        }

        // 3) 分帧把神庙覆盖的区块生成出来
        // 注意用 getChunkNow 判断"是否已是完整区块"：hasChunk 对生成过程中留下的半成品 proto-chunk 也返回 true，
        // 那样会拿着没生成完的区块去读高度图/写方块。
        int minX = counts.orbTempleTarget[0];
        int minZ = counts.orbTempleTarget[2];
        int loading = 0;
        for (ChunkPos cp : footprintChunks(minX, minZ, size)) {
            if (lucky.getChunkSource().getChunkNow(cp.x, cp.z) != null) continue;
            if (loading >= CHUNKS_PER_TICK) return; // 本刻先加载这么多，下刻继续
            lucky.getChunk(cp.x, cp.z);
            loading++;
        }

        // 4) 区块就绪 → 用真实高度图定底面高度后一次性放置
        int baseY = exactBaseY(lucky, minX, minZ, size);
        BlockPos origin = new BlockPos(minX, baseY, minZ);
        placeTemple(lucky, template, origin);
        // 神庙里那处 2×2×2 雕纹石英平台的中心正上方，放一只 INITIAL 阶段的幸运输核心
        spawnOrbOfLuck(lucky, template, origin);

        counts.orbTemplePlaced = true;
        counts.orbTempleTarget = null;
        counts.setDirty();
        ModMain.LOGGER.info("[orb-temple] 已在幸运维度生成幸运宝珠神庙：{}（底面 {}×{}，地形高差 {}）",
            origin, size.getX(), size.getZ(), surfaceSpread(lucky, minX, minZ, size));
    }

    /** 一个候选选址：结构最小角的 XZ、粗扫得到的地表最低点 y、以及底面高差。 */
    private record Spot(int minX, int minY, int minZ, int spread) {
        int[] toArray() {
            return new int[]{this.minX, this.minY, this.minZ};
        }
    }

    /**
     * 选址：先看近处（12 区块内）有没有"又高又平"的山顶平台，没有再把范围扩大到 32 区块；
     * 都找不到平台就退而求其次，取底面高差最小的那个（山再陡也总得找地方放）。
     *
     * @return 结构最小角 {@code [ox, oy, oz]}；oy 是粗扫（世界生成高度图）得到的底面最低点，
     *         真正放置时会用真实高度图重算并覆盖。完全找不到（候选全被"初入坐标周围 5 区块"否掉）返回 null。
     */
    @Nullable
    private static int[] searchOrigin(ServerLevel level, Vec3i size, int entryX, int entryZ) {
        Spot near = searchRing(level, size, entryX, entryZ, MAX_SEARCH_RADIUS, COARSE_STEP);
        if (near != null && near.spread() <= MAX_SURFACE_SPREAD) return near.toArray();

        Spot far = searchRing(level, size, entryX, entryZ, FAR_SEARCH_RADIUS, FAR_STEP);
        if (far != null && far.spread() <= MAX_SURFACE_SPREAD) return far.toArray();

        Spot fallback;
        if (near != null && far != null) {
            fallback = near.spread() <= far.spread() ? near : far;
        } else {
            fallback = near != null ? near : far;
        }
        return fallback == null ? null : fallback.toArray();
    }

    /**
     * 在 {@code [MIN_SEARCH_RADIUS, radius]} 的环带里找候选：先按单列高度粗扫（step 格网格），
     * 再从高到低细看最多 {@link #MAX_CANDIDATES} 个候选的底面高差，返回其中高差最小的那个。
     */
    @Nullable
    private static Spot searchRing(ServerLevel level, Vec3i size, int entryX, int entryZ, int radius, int step) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        int minSq = MIN_SEARCH_RADIUS * MIN_SEARCH_RADIUS;
        int maxSq = radius * radius;
        List<int[]> samples = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int distSq = dx * dx + dz * dz;
                if (distSq < minSq || distSq > maxSq) continue;
                int x = entryX + dx;
                int z = entryZ + dz;
                int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
                samples.add(new int[]{x, z, top});
            }
        }
        if (samples.isEmpty()) return null;
        samples.sort(Comparator.comparingInt((int[] s) -> s[2]).reversed());

        Spot best = null;
        int checked = 0;
        for (int[] sample : samples) {
            if (checked >= MAX_CANDIDATES) break;
            int minX = sample[0] - size.getX() / 2;
            int minZ = sample[1] - size.getZ() / 2;
            if (insideExclusion(minX, minZ, size, entryX, entryZ)) continue; // 离初入坐标太近，跳过
            checked++;

            int[] range = probeRange(level, generator, randomState, minX, minZ, size);
            int spread = range[1] - range[0];
            if (spread <= MAX_SURFACE_SPREAD) return new Spot(minX, range[0], minZ, spread); // 又高又平：直接采用
            if (best == null || spread < best.spread()) {
                best = new Spot(minX, range[0], minZ, spread);
            }
        }
        return best;
    }

    /** 在底面范围按 {@link #PROBE_STEP} 采样世界生成高度图，返回 {最低地表方块 y, 最高地表方块 y}。 */
    private static int[] probeRange(ServerLevel level, ChunkGenerator generator, RandomState randomState,
                                    int minX, int minZ, Vec3i size) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx < size.getX(); dx += PROBE_STEP) {
            for (int dz = 0; dz < size.getZ(); dz += PROBE_STEP) {
                int top = generator.getBaseHeight(minX + dx, minZ + dz, Heightmap.Types.WORLD_SURFACE_WG,
                    level, randomState) - 1;
                if (top < min) min = top;
                if (top > max) max = top;
            }
        }
        // 结构边缘（右下角）也采一下，避免只看网格点漏掉边缘的低谷
        int cornerTop = generator.getBaseHeight(minX + size.getX() - 1, minZ + size.getZ() - 1,
            Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
        return new int[]{Math.min(min, cornerTop), Math.max(max, cornerTop)};
    }

    /** 用真实高度图（区块已加载）取底面范围内最低的地表方块 y：结构底面就落在这个高度，不会悬空。 */
    private static int exactBaseY(ServerLevel level, int minX, int minZ, Vec3i size) {
        int min = Integer.MAX_VALUE;
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                // Level#getHeight 返回"第一个空位"（地表方块 + 1）
                min = Math.min(min, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minX + dx, minZ + dz) - 1);
            }
        }
        return min;
    }

    /** 真实高度图下的底面高差（仅用于日志）。 */
    private static int surfaceSpread(ServerLevel level, int minX, int minZ, Vec3i size) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minX + dx, minZ + dz) - 1;
                if (top < min) min = top;
                if (top > max) max = top;
            }
        }
        return max - min;
    }

    /** 神庙覆盖到的所有区块。 */
    private static List<ChunkPos> footprintChunks(int minX, int minZ, Vec3i size) {
        List<ChunkPos> list = new ArrayList<>();
        int c0x = minX >> 4;
        int c1x = (minX + size.getX() - 1) >> 4;
        int c0z = minZ >> 4;
        int c1z = (minZ + size.getZ() - 1) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                list.add(new ChunkPos(cx, cz));
            }
        }
        return list;
    }

    /** 结构覆盖的区块是否落在「初入坐标所在区块周围 {@link #EXCLUSION_CHUNKS} 区块」的方形区域内。 */
    private static boolean insideExclusion(int minX, int minZ, Vec3i size, int entryX, int entryZ) {
        int entryChunkX = entryX >> 4;
        int entryChunkZ = entryZ >> 4;
        int distanceX = nearestChunkDistance(minX, minX + size.getX() - 1, entryChunkX);
        int distanceZ = nearestChunkDistance(minZ, minZ + size.getZ() - 1, entryChunkZ);
        return Math.max(distanceX, distanceZ) <= EXCLUSION_CHUNKS;
    }

    /** 方块坐标区间 [minBlock, maxBlock] 覆盖的区块与 entryChunk 的最近区块距离（切比雪夫的单轴分量）。 */
    private static int nearestChunkDistance(int minBlock, int maxBlock, int entryChunk) {
        int first = minBlock >> 4;
        int last = maxBlock >> 4;
        if (entryChunk < first) return first - entryChunk;
        if (entryChunk > last) return entryChunk - last;
        return 0;
    }

    /** 放置整个模板：模板里的空气同样写入（替换结构体积内的地形），直接 setBlock → 没有掉落物。 */
    private static void placeTemple(ServerLevel level, StructureTemplate template, BlockPos origin) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);
        // flag 2|16：同步客户端、不触发邻接更新；不产生掉落物（setBlock 本身也不掉）
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2 | 16);
    }

    /**
     * 在神庙里那处「2×2×2 雕纹石英块平台」的<b>中心正上方</b>生成一只
     * {@code joes_addons_for_abmc:orb_of_luck}，阶段为 {@link OrbOfLuckEntity.OrbState#INITIAL}。
     *
     * <p>平台位置直接从模板里找（不写死坐标），以后挪动平台也跟着走；"中心"取 2×2 四块共用的那个角，
     * "上方"取平台顶面往上 {@link #ORB_HEIGHT_ABOVE_PLATFORM} 格——核心的碰撞箱是<b>底面对齐实体坐标</b>的，
     * 所以这个 y 就是它悬停的那一层。</p>
     */
    private static void spawnOrbOfLuck(ServerLevel level, StructureTemplate template, BlockPos origin) {
        List<StructureTemplate.StructureBlockInfo> platform = template.filterBlocks(
            BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHISELED_QUARTZ_BLOCK, false);
        if (platform.isEmpty()) {
            ModMain.LOGGER.warn("[orb-temple] 模板里没找到雕纹石英平台，跳过宝珠生成");
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int topY = Integer.MIN_VALUE;
        for (StructureTemplate.StructureBlockInfo info : platform) {
            BlockPos p = info.pos();
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            topY = Math.max(topY, p.getY());
        }

        // 2×2 的中心 = 四块共用的那个角（x/z 各 +0.5）；平台顶面 = 最上面那块的上表面（y + 1）
        double x = origin.getX() + (minX + maxX + 1) / 2.0;
        double z = origin.getZ() + (minZ + maxZ + 1) / 2.0;
        double y = origin.getY() + (topY + 1) + ORB_HEIGHT_ABOVE_PLATFORM;

        OrbOfLuckEntity orb = ModEntities.ORB_OF_LUCK.get().create(level);
        if (orb == null) {
            ModMain.LOGGER.warn("[orb-temple] 创建 orb_of_luck 实体失败");
            return;
        }
        orb.setPos(x, y, z);
        orb.setOrbState(OrbOfLuckEntity.OrbState.INITIAL);
        // INITIAL 阶段的核心要一直等着玩家来取（空手右击收走），不允许被 checkDespawn 当成远处野怪刷掉
        orb.setPersistenceRequired();
        level.addFreshEntity(orb);
        ModMain.LOGGER.info("[orb-temple] 已在神庙雕纹石英平台上方生成 orb_of_luck（INITIAL）：({}, {}, {})", x, y, z);
    }
}
