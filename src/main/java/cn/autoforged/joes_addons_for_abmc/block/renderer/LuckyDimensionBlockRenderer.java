package cn.autoforged.joes_addons_for_abmc.block.renderer;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyDimensionBlockEntity;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 幸运维度方块的外观渲染：伪装成随机一个"完整立方体"方块
 * （曾经外面再罩一层 50% 透明黄滤镜，<b>该滤镜已按需求暂时停用</b>）。
 *
 * <p><b>当前状态</b>：两个渲染路径都只画"随机伪装材质"，不再画黄纱 ——
 * 维度内见 {@link LuckyDimensionSectionRenderer}（调用处已注释），维度外见下面 {@link #render} 里被注释掉的
 * {@code renderFilterOverlay} 调用。滤镜的渲染类型、贴图与方法全部保留，以后取消注释即可恢复。
 *
 * <h3>两条取材质路径</h3>
 * <ul>
 *   <li><b>维度外</b>：用方块实体同步过来的贴图（服务端每 10 刻换一次，方块不多，开销可忽略）；</li>
 *   <li><b>幸运维度内</b>：整片地形都是这个方块，改由<b>客户端按坐标哈希自算</b> ——
 *       不写 NBT、不同步、服务端零开销、不发任何包。代价是同一坐标的外观固定不变
 *       （服务端那套"每 10 刻换一次"在几十万方块上不可行）。</li>
 * </ul>
 *
 * <h3>为了"尽量多渲染"做的取舍</h3>
 * <ol>
 *   <li><b>不再 {@code shouldRenderOffScreen} + {@code AABB.INFINITE}</b>：那是给信标这类全局方块用的，
 *       用在整片地形上等于让视锥剔除完全失效（远处/背后/地下的方块全都要走一遍渲染）。现在用
 *       方块自身包围盒 + {@link #getViewDistance()} 限制半径，视锥外直接不派发；</li>
 *   <li><b>六面全被遮挡就整块跳过</b>：地形里绝大多数方块埋在地下，直接省掉；</li>
 *   <li><b>伪装材质用 {@code renderBatched(checkSides=true)}</b>：像区块网格那样剔除被邻居挡住的面，
 *       每个方块通常只剩 1~3 个面，而不是固定 6 个面；</li>
 *   <li><b>黄滤镜只在更近的半径内绘制</b>（半透明绘制最贵），并可被每帧预算兜底
 *       —— <b>当前该滤镜已停用</b>，相关代码保留备用；</li>
 *   <li><b>每帧绘制数量上限</b>（{@link #MAX_DRAWS_PER_FRAME}）：极端情况下宁可少画，也不让客户端卡死
 *       （超出预算的方块退回显示自身贴图）。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public class LuckyDimensionBlockRenderer implements BlockEntityRenderer<LuckyDimensionBlockEntity> {

    private static final ResourceLocation FILTER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "joes_addons_for_abmc", "textures/block/filter.png");

    public static final ResourceLocation LUCKY_DIMENSION_KEY = ResourceLocation.fromNamespaceAndPath(
        ModMain.MODID, "lucky_dimension");

    /**
     * <b>幸运维度内是否渲染随机材质 —— 当前设定：开启。</b>
     *
     * <p>维度内整片地形都是幸运维度方块（数量以十万计），所以这条路径做了三层限制，避免卡顿：
     * <ol>
     *   <li>外观由<b>客户端按坐标哈希自算</b>（不写 NBT、不同步、服务端零开销、不发包）；</li>
     *   <li>只有 {@link #DIMENSION_RENDER_DISTANCE} 格内的方块才画随机材质与黄滤镜
     *       （黄滤镜另有更近的 {@link #DIMENSION_FILTER_DISTANCE} 限制），更远的退回方块自身贴图；</li>
     *   <li>每帧最多画 {@link #MAX_DRAWS_PER_FRAME} 个，超出部分同样退回自身贴图。</li>
     * </ol>
     * 想整体关掉维度内的随机材质（只显示自身贴图），把这里改成 {@code false} 即可。
     */
    private static final boolean RANDOM_MATERIAL_IN_DIMENSION = true;

    /** 幸运维度内伪装材质的渲染半径（格）。越大画得越多、越吃性能。 */
    private static final double DIMENSION_RENDER_DISTANCE = 32.0D;
    /** 幸运维度内黄滤镜的渲染半径（格）。伪装外观已由区块网格全视距绘制，这里只管半透明滤镜。 */
    private static final double DIMENSION_FILTER_DISTANCE = 48.0D;
    /** 每帧最多绘制多少个"随机材质"方块（安全阀；超出部分退回自身贴图）。 */
    private static final int MAX_DRAWS_PER_FRAME = 3000;
    /** 模仿材质绕方块中心放大倍数：1.004 → 每个面外移约 0.002 格，避免与方块自身模型共面。 */
    private static final float MIMIC_SCALE = 1.004F;

    /** 六个滤镜面各自对应的方向，顺序与 renderFilterOverlay 里的 quad 顺序一致。 */
    private static final Direction[] FACE_DIRECTIONS = {
        Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };

    /** 本帧已经画了多少个（维度外装饰方块用不到预算，仅在维度内路径中作为安全阀保留）。 */
    private static int frameDrawCount;

    /** 每帧开始（渲染关卡天空阶段之前）调用一次，重置每帧预算。 */
    public static void resetFrameBudget() {
        frameDrawCount = 0;
    }

    /**
     * <b>每帧必须调用一次（在方块实体通道之后）</b>：把本渲染器用到的几个批次显式提交。
     *
     * <p>原因：方块实体通道结束后，原版只会 flush {@code endLastBatch()} 加固定几个类型
     * （见 {@code LevelRenderer} 里 "blockentities" 段之后那段），普通画质下并<b>不会</b>做全量
     * {@code endBatch()}。我们在 BER 里往区块渲染类型（solid/cutout/translucent…）与自定义滤镜类型里
     * 写四边形，如果不显式提交，就会出现"代码都跑了但屏幕上什么都没有"。
     */
    public static void flushBatches() {
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        source.endBatch(RenderType.solid());
        source.endBatch(RenderType.cutoutMipped());
        source.endBatch(RenderType.cutout());
        source.endBatch(RenderType.translucent());
        source.endBatch(FILTER_TYPE);
    }

    /**
     * 滤镜叠加层专用渲染类型：在原版 {@code entityTranslucent} 的基础上，额外启用
     * {@link RenderStateShard#POLYGON_OFFSET_LAYERING}（多边形深度偏移）。
     *
     * <p>共面的两层（滤镜 vs 被模仿方块表面）靠把顶点往外推是治不好的：偏移量在稍远处就低于深度缓冲精度。
     * 深度偏移按屏幕空间把整层往观察者方向偏一点，与距离无关，因此不会再闪。
     * 这里写深度（{@code COLOR_DEPTH_WRITE}），让后面绘制的半透明伪装材质被正确遮挡。
     */
    private static final RenderType FILTER_TYPE = RenderType.create(
        "joes_lucky_dimension_filter",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(FILTER_TEXTURE, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false));

    private final BlockRenderDispatcher blockRenderer;

    public LuckyDimensionBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public int getViewDistance() {
        // 维度外：给足距离（装饰性方块，数量少）
        // 维度内：只用比渲染半径略大一点的派发距离 —— 方块实体渲染器是按这个距离逐个派发的，
        // 用 256 会让远处成千上万个方块都白跑一次 render()（虽然会被半径判定提前 return）
        if (isClientInLuckyDimension()) {
            return (int) DIMENSION_RENDER_DISTANCE + 8;
        }
        return 256;
    }

    private static boolean isClientInLuckyDimension() {
        Level level = Minecraft.getInstance().level;
        return level != null && LUCKY_DIMENSION_KEY.equals(level.dimension().location());
    }

    @Override
    public AABB getRenderBoundingBox(@NotNull LuckyDimensionBlockEntity blockEntity) {
        // 只覆盖方块自身（+一点点滤镜外扩），让视锥剔除正常工作
        return new AABB(blockEntity.getBlockPos()).inflate(0.1D);
    }

    @Override
    public void render(@NotNull LuckyDimensionBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();

        // 关掉随机贴图时：方块自身模型已被设为 INVISIBLE，这里必须把它补画回来（否则会隐形）
        if (!ModConfig.LUCKY_DIMENSION_RANDOM_TEXTURES.get()) {
            if (level != null) {
                BlockState ownState = blockEntity.getBlockState();
                RenderType ownType = ItemBlockRenderTypes.getChunkRenderType(ownState);
                poseStack.pushPose();
                blockRenderer.renderBatched(ownState, pos, level, poseStack,
                    bufferSource.getBuffer(ownType), false, level.getRandom(), ModelData.EMPTY, ownType);
                poseStack.popPose();
            }
            return;
        }

        if (level == null) return;
        boolean inLuckyDimension = LUCKY_DIMENSION_KEY.equals(level.dimension().location());

        // 开关：维度内不渲染随机材质时直接返回（当前为开启，见 RANDOM_MATERIAL_IN_DIMENSION）
        if (inLuckyDimension && !RANDOM_MATERIAL_IN_DIMENSION) {
            return;
        }

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distanceSqr = cameraPos.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

        // 维度内的一切外观（伪装材质 + 黄色着色）现在全部由区块网格绘制
        //（LuckyDimensionSectionRenderer）。这里必须什么都不画：
        // 之前这里还留着一层"50% 黄纱"的 BER 滤镜，它只对补过方块实体的地表方块、48 格内生效，
        // 于是出现"只有贴近才看到黄色、远处没有"的不一致现象。
        if (inLuckyDimension) {
            return;
        }

        BlockState mimicState = mimicFromBlockEntity(blockEntity);
        if (mimicState == null) {
            return;
        }
        if (mimicState.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        // 六面全被遮挡 → 什么都不用画（地形里绝大多数方块都在地下）
        boolean[] faceVisible = new boolean[6];
        boolean anyVisible = false;
        for (int i = 0; i < 6; i++) {
            faceVisible[i] = !isFaceOccluded(level, pos, FACE_DIRECTIONS[i]);
            anyVisible |= faceVisible[i];
        }
        if (!anyVisible) {
            return;
        }

        frameDrawCount++;

        // 维度外：伪装材质（维度内由区块网格绘制，不走这里）
        poseStack.pushPose();
        // 绕方块中心放大一点点，避免和方块自身模型（0~1）完全共面而 z-fighting
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.scale(MIMIC_SCALE, MIMIC_SCALE, MIMIC_SCALE);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        RenderType mimicType = ItemBlockRenderTypes.getChunkRenderType(mimicState);
        VertexConsumer mimicConsumer = bufferSource.getBuffer(mimicType);
        blockRenderer.renderBatched(mimicState, pos, level, poseStack, mimicConsumer, true,
            level.getRandom(), ModelData.EMPTY, mimicType);
        poseStack.popPose();

        // ===== 黄滤镜：按需求【暂时停用】=====
        // 现在这个方块只显示上面的"随机伪装材质"，不再叠 50% 的 filter.png 黄纱。
        // 逻辑没有删：renderFilterOverlay / FILTER_TYPE / FILTER_TEXTURE / FACE_DIRECTIONS 都还在，
        // 以后要恢复，把下面这段取消注释即可（distanceSqr 就是为它算的，现在暂时没人用）。
        // boolean drawFilter = !inLuckyDimension
        //     || distanceSqr <= DIMENSION_FILTER_DISTANCE * DIMENSION_FILTER_DISTANCE;
        // if (drawFilter) {
        //     renderFilterOverlay(poseStack, bufferSource, faceVisible, packedLight, packedOverlay);
        // }
    }

    /**
     * 客户端 chunk 加载时为幸运维度的地表方块补上方块实体。
     *
     * <p><b>为什么必须这么做</b>：幸运维度的地形是噪声生成器用调色板快速路径铺出来的，这条路径
     * <b>不会为方块创建方块实体</b>；而原版 {@code SectionCompiler} 只把"确实存在方块实体对象"的方块
     * 登记进可渲染列表（{@code handleBlockEntity}），所以渲染器永远不会被派发（表现为 calls=0、
     * 整个维度看不到随机材质）。维度外是玩家手放的方块，本来就有方块实体，所以一直正常。
     *
     * <p>这里只在<b>客户端</b>补：不发包、不写存档、不影响服务端。覆盖范围：
     * 每列最上面那个方块（地表，站上去能看到的绝大部分）+ 其下 5 格中"侧面暴露"的方块（悬崖面）。
     */
    public static void ensureBlockEntities(net.minecraft.client.multiplayer.ClientLevel level,
                                           net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (!LUCKY_DIMENSION_KEY.equals(level.dimension().location())) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        java.util.Set<Integer> dirtySections = new java.util.HashSet<>();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surfaceY = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int dy = 0; dy <= 5; dy++) {
                    int y = surfaceY - dy;
                    if (y < chunk.getMinBuildHeight()) break;
                    pos.set(minX + x, y, minZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.is(cn.autoforged.joes_addons_for_abmc.block.ModBlocks.LUCKY_DIMENSION_BLOCK.get())) continue;
                    // 地表那一格总是补；往下的格子只在"侧面暴露"时补（悬崖面）
                    if (dy > 0 && !isSideExposed(chunk, pos)) continue;
                    if (chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.IMMEDIATE) != null) {
                        dirtySections.add(y >> 4);
                    }
                }
            }
        }
        // 让这些区块段重新编译，把新登记的方块实体带上（ChunkEvent.Load 与网格编译是异步的）
        for (int sectionY : dirtySections) {
            level.setSectionDirtyWithNeighbors(chunk.getPos().x, sectionY, chunk.getPos().z);
        }
    }

    private static boolean isSideExposed(net.minecraft.world.level.chunk.LevelChunk chunk, BlockPos pos) {
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (chunk.getBlockState(pos.relative(direction)).isAir()) return true;
        }
        // 上方是空气也算暴露（地表就属于这种）
        return chunk.getBlockState(pos.above()).isAir();
    }

    /**
     * 维度内：只画黄滤镜（伪装外观由区块网格 {@link LuckyDimensionSectionRenderer} 负责）。
     * 仍受距离半径与每帧预算限制 —— 半透明层是这条路上最贵的部分。
     */
    private void renderDimensionFilterOnly(Level level, BlockPos pos, double distanceSqr, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (distanceSqr > DIMENSION_FILTER_DISTANCE * DIMENSION_FILTER_DISTANCE) {
            return;
        }
        if (frameDrawCount >= MAX_DRAWS_PER_FRAME) {
            return;
        }
        boolean[] faceVisible = new boolean[6];
        boolean anyVisible = false;
        for (int i = 0; i < 6; i++) {
            faceVisible[i] = !isFaceOccluded(level, pos, FACE_DIRECTIONS[i]);
            anyVisible |= faceVisible[i];
        }
        if (!anyVisible) {
            return;
        }
        frameDrawCount++;
        renderFilterOverlay(poseStack, bufferSource, faceVisible, packedLight, packedOverlay);
    }

    /** 维度外：用方块实体同步过来的贴图（服务端每 10 刻换一次）。 */
    @Nullable
    private static BlockState mimicFromBlockEntity(LuckyDimensionBlockEntity blockEntity) {
        ResourceLocation currentTex = blockEntity.getCurrentTexture();
        if (currentTex == null) return null; // 还没同步到 → 这一帧不画（显示方块自身贴图）
        Block mimicBlock = BuiltInRegistries.BLOCK.get(currentTex);
        if (mimicBlock == null || mimicBlock == net.minecraft.world.level.block.Blocks.AIR) return null;
        return mimicBlock.defaultBlockState();
    }

    /**
     * 幸运维度内：按坐标哈希挑一个伪装方块，且<b>逐方块</b>随时间变化。
     *
     * <p>做法：把坐标哈希的低位当作"相位"，用 {@code (gameTime + phase) / 周期} 得到该方块自己的时间桶 ——
     * 于是每个方块在自己到点时换外观，而不是整段一起换（区块段重编译只是把"已到点"的结果刷新出来）。
     *
     * <p>另外会跳过"会被画成透明"的材质：区块半透明层（玻璃/冰/黏液等）在自身模型被隐藏后会让方块透视，
     * 所以这里沿哈希顺序往后试探若干候选，取第一个不透明的。
     */
    @Nullable
    public static BlockState mimicForPosition(BlockPos pos, long gameTime) {
        List<Block> pool = LuckyDimensionBlockEntity.fullCubePool();
        if (pool.isEmpty()) return null;

        long h = pos.asLong();
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);

        long phase;
        long period;
        long bucket;
        if (gameTime < 0) {
            phase = 0L;
            period = 1L;
            bucket = 0L;
        } else {
            // 每个方块拥有自己的周期与相位（由坐标哈希决定）。
            // 周期必须明显大于"区块段重编译间隔（约 9 刻）"：这样每次重编译只有约 1/8 的方块到点，
            // 看起来才是零散逐块地变；若周期也接近 9 刻，段内几乎全部方块会在同一次重编译里一起换。
            // 0.8~1.2 秒（16~24 刻）
            period = 16L + Math.floorMod(h >>> 8, 9L);
            phase = Math.floorMod(h, period);
            bucket = Math.floorDiv(gameTime + phase, period);
        }
        long mixed = h + bucket * 0x9E3779B97F4A7C15L;

        BlockState fallback = null;
        for (int probe = 0; probe < MIMIC_PROBE_LIMIT; probe++) {
            int index = (int) Math.floorMod(mixed + probe, (long) pool.size());
            BlockState candidate = pool.get(index).defaultBlockState();
            if (fallback == null) fallback = candidate;
            if (isUsableMimic(candidate)) return candidate;
        }
        return fallback;
    }

    /** 挑选伪装方块时最多试探多少个候选（用来跳过会被画成透明的材质）。 */
    private static final int MIMIC_PROBE_LIMIT = 16;

    /**
     * 方块 → 能否作为伪装外观的缓存。
     * <p><b>必须是并发容器</b>：区块网格由 ForkJoinPool 工作线程并行编译，
     * 这里会被多个线程同时访问（曾因用 HashMap 抛 ConcurrentModificationException 崩客户端）。
     */
    private static final java.util.Map<Block, Boolean> USABLE_MIMIC_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 这个方块能否当作伪装外观（用来根治"随机到某些材质后方块完全透明"）：
     * <ol>
     *   <li>不能走区块<b>半透明层</b>（玻璃/冰/黏液/树叶一类，自身模型隐藏后就成了透视洞）；</li>
     *   <li>模型必须<b>六个方向都有面</b>（排除末地传送门框架、活塞头、花盆这类"薄片/缺面"模型 ——
     *       它们碰撞箱是满的、也能遮挡邻居，但实际只画一小片，看起来就是透明的）。</li>
     * </ol>
     * 判定按方块缓存，只在第一次遇到时算一次。
     */
    public static boolean isUsableMimic(BlockState state) {
        if (ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent()) return false;
        return USABLE_MIMIC_CACHE.computeIfAbsent(state.getBlock(), block -> {
            BlockState def = block.defaultBlockState();
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(def);
            var random = net.minecraft.util.RandomSource.create(42L);
            // 无 cullface 的模型面会挂在 null 方向下，这种情况直接视为六面齐全
            if (!model.getQuads(def, null, random).isEmpty()) return true;
            for (Direction direction : Direction.values()) {
                if (model.getQuads(def, direction, random).isEmpty()) return false;
            }
            return true;
        });
    }

    private void renderFilterOverlay(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                                     boolean[] faceVisible, int packedLight, int packedOverlay) {
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(FILTER_TYPE);

        float a = 0.5F;
        // 滤镜层沿各面法线的外推量。层级严格有序：自身模型 0 → 模仿材质 +0.002 → 滤镜 +0.006，
        // 再加上滤镜自带的多边形深度偏移，所以三层不会互相穿插/闪烁。
        float m = 0.006F;

        if (faceVisible[0]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                0, 0, 1,  0, 1, 1,  1, 1, 1,  1, 0, 1,  0, 0, 1,  m, a);
        }
        if (faceVisible[1]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                0, 0, 0,  0, 1, 0,  1, 1, 0,  1, 0, 0,  0, 0, -1, m, a);
        }
        if (faceVisible[2]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                0, 0, 0,  0, 1, 0,  0, 1, 1,  0, 0, 1,  -1, 0, 0, m, a);
        }
        if (faceVisible[3]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                1, 0, 1,  1, 1, 1,  1, 1, 0,  1, 0, 0,  1, 0, 0, m, a);
        }
        if (faceVisible[4]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                0, 1, 0,  0, 1, 1,  1, 1, 1,  1, 1, 0,  0, 1, 0, m, a);
        }
        if (faceVisible[5]) {
            quad(consumer, matrix, pose, packedLight, packedOverlay,
                0, 0, 0,  0, 0, 1,  1, 0, 1,  1, 0, 0,  0, -1, 0, m, a);
        }
    }

    /** 邻居方块是否把这一面完全挡住了。 */
    private static boolean isFaceOccluded(@NotNull Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        if (!level.isLoaded(neighborPos)) return false;
        return level.getBlockState(neighborPos).canOcclude();
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                      int packedLight, int packedOverlay,
                      float v1x, float v1y, float v1z,
                      float v2x, float v2y, float v2z,
                      float v3x, float v3y, float v3z,
                      float v4x, float v4y, float v4z,
                      float nx, float ny, float nz,
                      float margin, float a) {
        // 只沿"这个面自己的法线"外推 margin，不在切向也外扩 ——
        // 否则相邻方块的顶面/底面滤镜条带会互相重叠并共面（邻居之间也会 z-fighting）。
        float ox = nx * margin;
        float oy = ny * margin;
        float oz = nz * margin;
        consumer.addVertex(matrix, v1x + ox, v1y + oy, v1z + oz)
            .setColor(1F, 1F, 1F, a).setUv(0, 0).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v2x + ox, v2y + oy, v2z + oz)
            .setColor(1F, 1F, 1F, a).setUv(0, 1).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v3x + ox, v3y + oy, v3z + oz)
            .setColor(1F, 1F, 1F, a).setUv(1, 1).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, v4x + ox, v4y + oy, v4z + oz)
            .setColor(1F, 1F, 1F, a).setUv(1, 0).setOverlay(packedOverlay).setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
    }
}
