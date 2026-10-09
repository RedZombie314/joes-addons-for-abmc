package cn.autoforged.joes_addons_for_abmc.block.renderer;

import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

/**
 * 幸运维度地形外观的<b>区块网格</b>实现：统一亮度的伪装材质（以及曾经的 50% 黄色混合滤镜）。
 *
 * <p><b>当前状态：黄滤镜已按需求停用</b> —— 幸运维度的地形只显示"随机伪装材质"本身，不再叠那层 50% 黄纱。
 * 滤镜的逻辑与实现（{@link #drawVeil}、{@link #VEIL_ALPHA}、{@link #FILTER_TEXTURE}、{@link #quadCorners}）
 * 全部原样保留，只是把 {@link #renderSection} 里的调用处注释掉了；以后要用，把那一处
 * （连同开头 {@code filterSprite} 的取值）取消注释即可。
 *
 * <h3>三个坑，各自的解法（滤镜启用时的记录，保留备用）</h3>
 * <ol>
 *   <li><b>两层几乎共面 ⇒ z-fighting</b>：滤镜层若外扩几毫米，远处深度精度不足就会闪，外扩大又露外壳。
 *       解法：外扩量取 <b>0</b>（顶点与伪装材质<b>逐位重合</b>）—— 光栅化出的深度值逐位相同，
 *       深度测试是 {@code LEQUAL}，等值判定确定，所以滤镜稳定盖在上面，与距离无关，不可能闪。</li>
 *   <li><b>原版管线的方向性明暗 ⇒ 侧面比顶面暗</b>：解法是自己用 {@code putBulkData} 写伪装材质的四边形，
 *       顶点颜色由我们给定（白色），于是六个面亮度一致。</li>
 *   <li><b>乘算着色 ⇒ 暗部更暗</b>：顶点颜色只能做乘法（减法），永远压暗蓝通道。
 *       所以黄色改用<b>混合</b>实现：一层 50% 透明度的黄纱叠在上面，
 *       数学上是 {@code 结果 = 0.5×材质 + 0.5×黄色} —— 暗部被"抬"向黄色（更亮），而不是被压暗。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class LuckyDimensionSectionRenderer {
    /** 黄纱透明度：0.5 → 结果 = 0.5×材质 + 0.5×黄色（暗部因此被提亮）。 */
    private static final float VEIL_ALPHA = 0.5F;

    private static final ResourceLocation FILTER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "block/filter");

    private LuckyDimensionSectionRenderer() {
    }

    /** 越界只报一次（同一个 JVM 里），免得每个区段重建都刷屏。 */
    private static boolean regionMismatchReported;

    /** 见 renderSection 里那段越界防护：批次给的 region 与本次回调的区块对不上时走这里。 */
    private static void warnRegionMismatchOnce(BlockPos origin, RuntimeException e) {
        if (regionMismatchReported) {
            return;
        }
        regionMismatchReported = true;
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.warn(
            "[幸运维度渲染] 区块网格与本次几何回调的区段不匹配（起点 {}）：{}；已跳过该格，不再崩溃",
            origin, e.toString());
    }

    public static void addGeometry(AddSectionGeometryEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        if (!LuckyDimensionBlockRenderer.LUCKY_DIMENSION_KEY.equals(level.dimension().location())) return;
        BlockPos origin = event.getSectionOrigin();
        event.addRenderer(context -> renderSection(context, origin));
    }

    private static void renderSection(AddSectionGeometryEvent.SectionRenderingContext context, BlockPos origin) {        boolean random = ModConfig.LUCKY_DIMENSION_RANDOM_TEXTURES.get();
        BlockAndTintGetter region = context.getRegion();
        PoseStack poseStack = context.getPoseStack();
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        ClientLevel level = Minecraft.getInstance().level;
        long gameTime = level == null ? 0L : level.getGameTime();
        // 【黄滤镜停用中】滤镜贴图取值：恢复画黄纱时把这两行一起取消注释（drawVeil 需要它）
        // TextureAtlasSprite filterSprite =
        //     Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FILTER_TEXTURE);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 坐标先抄成 int：这是个<b>延迟回调</b>（见 addGeometry 的 addRenderer），
        // 万一把可变 BlockPos 实例留到批次执行时才用，抄成 int 就完全不受它后来被改的影响。
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int dy = 0; dy < 16; dy++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    pos.set(ox + dx, oy + dy, oz + dz);
                    /*
                     * 越界防护（6.5.32）：区块网格只带 <b>3×3 个区块</b>（{@code RenderChunkRegion} 内部数组
                     * 长度 9），而几何回调是在 "Batching sections" 批次里延迟执行的。一旦批次给出的 region
                     * 与本次回调自己的区块对不上，读<b>本区段自己那一格</b>就会越界，
                     * 抛出 {@code ArrayIndexOutOfBoundsException: Index 49 out of bounds for length 9}
                     * 并直接崩掉客户端（实测崩溃报告：错误报告-2026-10-6_23.39.37）。
                     * 这里把单次读取罩住：读不到就跳过这一格（整段都读不到 = region 确实对不上，
                     * 那段就整个不画），宁可少画一次"伪装材质"，也不能让渲染线程把游戏带走。
                     */
                    BlockState state;
                    try {
                        state = region.getBlockState(pos);
                    } catch (RuntimeException e) {
                        warnRegionMismatchOnce(origin, e);
                        continue;
                    }
                    if (!state.is(ModBlocks.LUCKY_DIMENSION_BLOCK.get())) continue;

                    BlockState mimic = random ? LuckyDimensionBlockRenderer.mimicForPosition(pos, gameTime) : null;
                    if (mimic == null || mimic.getRenderShape() != RenderShape.MODEL) {
                        // 兜底（含"关闭随机贴图"配置）：画方块自身模型，绝不留空
                        drawBlockPlain(context, blockRenderer, region, poseStack, state, pos, dx, dy, dz);
                        continue;
                    }

                    drawBlockUniform(context, blockRenderer, region, poseStack, mimic, pos, dx, dy, dz);

                    // ===== 黄滤镜（50% 黄色混合层）：按需求【暂时停用】=====
                    // 现在幸运维度的地形只显示上面的"随机伪装材质"，不再叠这层黄纱。
                    // 逻辑没有删：drawVeil / VEIL_ALPHA / FILTER_TEXTURE / quadCorners 都还在，
                    // 以后要恢复，把下面这段取消注释、并把方法开头 filterSprite 的取值一起取消注释即可。
                    // 备注（原设计）：黄纱只属于"随机材质"这套外观，配置关掉随机材质时本来就不叠；
                    //                   而且只给"至少有一个面朝外"的方块画，埋在地下的方块不画。
                    // if (random && hasExposedFace(region, pos)) {
                    //     drawVeil(context, region, poseStack, filterSprite, pos, dx, dy, dz);
                    // }
                }
            }
        }
    }

    /** 原版管线绘制（只用于兜底 / 关闭随机贴图时画方块自身模型）。 */
    private static void drawBlockPlain(AddSectionGeometryEvent.SectionRenderingContext context,
                                       BlockRenderDispatcher blockRenderer, BlockAndTintGetter region,
                                       PoseStack poseStack, BlockState state, BlockPos pos,
                                       int dx, int dy, int dz) {
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
        var buffer = context.getOrCreateChunkBuffer(type);
        RandomSource random = RandomSource.create(state.getSeed(pos));
        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        blockRenderer.renderBatched(state, pos, region, poseStack, buffer, true, random,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY, type);
        poseStack.popPose();
    }

    /**
     * 伪装材质：逐四边形写出，顶点颜色<b>白色</b>（因此没有原版的方向性明暗，六面亮度一致），
     * 只画朝向未被遮挡的面。黄色不在这里乘 —— 交给 {@link #drawVeil} 用混合实现，避免暗部被压暗。
     */
    private static void drawBlockUniform(AddSectionGeometryEvent.SectionRenderingContext context,
                                         BlockRenderDispatcher blockRenderer, BlockAndTintGetter region,
                                         PoseStack poseStack, BlockState mimic, BlockPos pos,
                                         int dx, int dy, int dz) {
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(mimic);
        var buffer = context.getOrCreateChunkBuffer(type);
        var model = blockRenderer.getBlockModel(mimic);
        RandomSource random = RandomSource.create(mimic.getSeed(pos));
        int light = LevelRenderer.getLightColor(region, region.getBlockState(pos), pos);

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        var pose = poseStack.last();

        for (Direction direction : Direction.values()) {
            if (region.getBlockState(pos.relative(direction)).canOcclude()) continue;
            for (var quad : model.getQuads(mimic, direction, random)) {
                buffer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
        for (var quad : model.getQuads(mimic, null, random)) {
            buffer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    /** 这个方块是否至少有一个面朝外。 */
    private static boolean hasExposedFace(BlockAndTintGetter region, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!region.getBlockState(pos.relative(direction)).is(ModBlocks.LUCKY_DIMENSION_BLOCK.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 50% 黄色滤镜（混合）：写进区块半透明层。
     * <p>顶点与伪装材质的面<b>完全重合（外扩 0）</b>——见类注释第 1 条，这是"不会 z-fighting"的关键。
     * <p>数学效果是 {@code 0.5×材质 + 0.5×黄色}：暗部被抬向黄色（变亮），因此不存在"乘算把深色压得更深"的问题。
     * <p>光照取方块位置的实际光照，使滤镜与伪装材质同步随环境变暗/变亮。
     */
    private static void drawVeil(AddSectionGeometryEvent.SectionRenderingContext context,
                                 BlockAndTintGetter region, PoseStack poseStack, TextureAtlasSprite sprite,
                                 BlockPos pos, int dx, int dy, int dz) {
        var buffer = context.getOrCreateChunkBuffer(RenderType.translucent());
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int light = LevelRenderer.getLightColor(region, region.getBlockState(pos), pos);

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        var pose = poseStack.last();

        for (Direction direction : Direction.values()) {
            float nx = direction.getStepX(), ny = direction.getStepY(), nz = direction.getStepZ();
            float[][] corners = quadCorners(direction);
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose.pose(), corners[i][0], corners[i][1], corners[i][2])
                    .setColor(1.0F, 1.0F, 1.0F, VEIL_ALPHA)
                    .setUv((i == 1 || i == 2) ? u1 : u0, (i >= 2) ? v1 : v0)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
            }
        }
        poseStack.popPose();
    }

    /** 每个方向对应的四个角，顺序为从外侧看的逆时针（半透明层开启背面剔除）。 */
    private static float[][] quadCorners(Direction direction) {
        return switch (direction) {
            case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case DOWN -> new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case SOUTH -> new float[][]{{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}};
            case NORTH -> new float[][]{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
            case WEST -> new float[][]{{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}};
            case EAST -> new float[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
        };
    }
}
