package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * 「幸运之庙」结构：只生成在<b>平原</b>生物群系（由
 * {@code worldgen/structure/lucky_temple.json} 的 "biomes" 决定），只生成在<b>海平面以上</b>的
 * 地表，且<b>嵌进地表</b>——结构最低一层的方块取代生成高度处的方块（其余被地形挡住的部分由模板里
 * 的空气直接替换掉，没有掉落物）。
 *
 * <h3>生成高度怎么来的</h3>
 * <p>原版结构（如沙漠神殿）用 {@code Structure#getLowestY} 取「最上面那块实心方块的 y」作为结构
 * 最小角，本结构同样如此，只是把 4 个角扩成整个 7×7 底面的逐列采样：取 49 列里<b>最低</b>的地表方块
 * 高度 {@code minSurface}，把模板最小角放在 {@code (x, minSurface, z)}。于是：</p>
 * <ul>
 *   <li>最低那一列：模板最底层正好盖住那一格地表方块 → 嵌入地表；</li>
 *   <li>更高的列：地形会伸进模板体积内部，但模板里的空气会把它们替换掉（玩家看到的是被削平的
 *       平整地面 + 坐在上面的庙），这就是「同时替换掉该位置的所有方块」。</li>
 * </ul>
 *
 * <h3>额外的地形要求</h3>
 * <ul>
 *   <li>{@code minSurface > 海平面}：只在水面之上的地表生成（水里/河里的列地表高度 ≤ 海平面，直接否掉）；</li>
 *   <li>底面 49 列的最大高差 ≤ {@link #MAX_SURFACE_SPREAD}：地形太陡（悬崖、山脊）就不生成，
 *       免得出现半悬空半埋山的难看样子。</li>
 * </ul>
 *
 * <h3>生成概率</h3>
 * <p>概率不在这里，而在 {@code worldgen/structure_set/lucky_temples.json}：用原版的
 * {@code minecraft:random_spread} 摆放方式，「尝试生成」的密度参数（spacing 34 / separation 8）
 * 与平原村庄完全一致，等于说每个 34×34 区块的网格里会挑一个区块尝试生成一次；再叠加这里的
 * 生物群系（平原）与地形判定，最终密度与原版平原村庄在平原里的密度相当。</p>
 */
public class LuckyTempleStructure extends Structure {

    public static final MapCodec<LuckyTempleStructure> CODEC = simpleCodec(LuckyTempleStructure::new);

    /** 建筑模板：{@code data/joes_addons_for_abmc/structure/lucky_temple.nbt}。 */
    public static final ResourceLocation TEMPLATE_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_temple");

    /** 底面范围内允许的最大地表高差，超过就认为地形太陡，放弃生成。 */
    private static final int MAX_SURFACE_SPREAD = 3;

    public LuckyTempleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StructureTemplate template = context.structureTemplateManager().getOrCreate(TEMPLATE_ID);
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return Optional.empty();
        }

        // 放在该区块正中：7×7 的底面（x/z 各 ±3）始终落在同一个区块内，放置时不会被区块边界切开
        ChunkPos chunkPos = context.chunkPos();
        int minX = chunkPos.getMiddleBlockX() - size.getX() / 2;
        int minZ = chunkPos.getMiddleBlockZ() - size.getZ() / 2;

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                // getFirstOccupiedHeight = 该列最上面那块实心方块的 y（世界生成用的噪声高度图）
                int surface = context.chunkGenerator().getFirstOccupiedHeight(
                    minX + dx, minZ + dz, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
                if (surface < minSurface) {
                    minSurface = surface;
                }
                if (surface > maxSurface) {
                    maxSurface = surface;
                }
            }
        }

        // 只生成在海平面以上的地表
        if (minSurface <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        // 地表过陡不生成
        if (maxSurface - minSurface > MAX_SURFACE_SPREAD) {
            return Optional.empty();
        }

        // 结构最小角 = (底面中心, 最低地表高度)：最底层方块取代该高度处的地表方块（嵌入地表）
        BlockPos origin = new BlockPos(minX, minSurface, minZ);
        return Optional.of(new GenerationStub(origin, builder ->
            builder.addPiece(new LuckyTemplePiece(context.structureTemplateManager(), origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.LUCKY_TEMPLE.get();
    }
}
