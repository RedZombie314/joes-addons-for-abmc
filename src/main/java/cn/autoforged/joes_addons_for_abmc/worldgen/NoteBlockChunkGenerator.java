package cn.autoforged.joes_addons_for_abmc.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 音符方块宇宙专用的区块生成器。
 *
 * 在完成生物群系装饰（树木、结构如村庄等）后，将本 chunk 内所有生成的原木/木变种
 * （各种原木与对应的木块，通过 {@link BlockTags#LOGS} 判定，排除去皮变种）替换为音符盒。
 */
public class NoteBlockChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<NoteBlockChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        p_ -> p_.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoteBlockChunkGenerator::generatorSettings)
            )
            .apply(p_, p_.stable(NoteBlockChunkGenerator::new))
    );

    private static final BlockState NOTE_BLOCK = Blocks.NOTE_BLOCK.defaultBlockState();

    public NoteBlockChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        replaceLogsWithNoteBlocks(chunk);
        fixLeafDistances(level, chunk);
    }

    private void replaceLogsWithNoteBlocks(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 跳过全空气的区块分层，大幅降低生成开销
        for (int k = chunk.getHeightAccessorForGeneration().getMinSection();
             k < chunk.getHeightAccessorForGeneration().getMaxSection();
             k++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(k));
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = k * 16;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (isUnstrippedLog(state)) {
                            pos.set(minX + lx, sectionBottomY + ly, minZ + lz);
                            chunk.setBlockState(pos, NOTE_BLOCK, false);
                        }
                    }
                }
            }
        }
    }

    private static boolean isUnstrippedLog(BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }
        Block block = state.getBlock();
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && !key.getPath().contains("stripped");
    }

    private static final byte[][] DIRS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * 音符方块宇宙的树叶衰减逻辑：树叶不再依赖原木，而是检测周围是否存在音符盒
     * 来决定其自身是否在随机刻消失。
     * <p>
     * 做法：以每个音符盒为源做 BFS，把距离音符盒 6 格以内的树叶标记为 PERSISTENT（永不衰减）。
     * 身边没有音符盒的孤立树叶保持原样，会照常衰减消失。
     */
    private void fixLeafDistances(WorldGenLevel level, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        // 收集本 chunk 内的音符盒作为 BFS 源
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = chunk.getHeightAccessorForGeneration().getMinSection();
             k < chunk.getHeightAccessorForGeneration().getMaxSection();
             k++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(k));
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = k * 16;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (state.is(Blocks.NOTE_BLOCK)) {
                            pos.set(minX + lx, sectionBottomY + ly, minZ + lz);
                            queue.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), 0});
                        }
                    }
                }
            }
        }
        if (queue.isEmpty()) return;

        // BFS：从音符盒向 6 邻方向扩散，遇到树叶则标记为 PERSISTENT，透过空气/原木继续扩散
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int dist = cur[3];
            if (dist >= 6) continue; // 只影响距离 6 格以内的树叶
            for (byte[] dir : DIRS) {
                int nx = cur[0] + dir[0];
                int ny = cur[1] + dir[1];
                int nz = cur[2] + dir[2];
                BlockPos nextPos = new BlockPos(nx, ny, nz);
                BlockState next = level.getBlockState(nextPos);
                if (next.getBlock() instanceof LeavesBlock) {
                    if (!next.getValue(LeavesBlock.PERSISTENT)) {
                        level.setBlock(nextPos, next.setValue(LeavesBlock.PERSISTENT, true), 2);
                    }
                    queue.add(new int[]{nx, ny, nz, dist + 1});
                } else if (next.isAir() || isUnstrippedLog(next)) {
                    // 透过空气与原木继续扩散（原木通常已被替换为音符盒，这里做兜底）
                    queue.add(new int[]{nx, ny, nz, dist + 1});
                }
            }
        }
    }
}