package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截原版沼泽小屋（SwampHutPiece）的生成，按概率替换为自定义 witchbosshut 结构。
 * 概率规则：1% 或连续 200 座未出现则保底。
 * 调试模式下，仅第一座小屋必定替换，后续恢复正常概率。
 *
 * 线程注意：postProcess 在世界生成 worker 线程执行，严禁访问 SavedData（非线程安全，
 * 会导致区块生成 future 永不完成、客户端等不到地形而卡死）。所有状态走 ModMain 的
 * GEN_* 并发内存结构，由主线程 tick 统一持久化。
 * 另：postProcess 的 box 参数是整个区块可写范围，真实片段包围盒须取 this.getBoundingBox()。
 */
@Mixin(SwampHutPiece.class)
public abstract class SwampHutPieceMixin {

    @Inject(method = "postProcess", at = @At("HEAD"), cancellable = true)
    private void jafa_replaceWithBossHut(WorldGenLevel level,
                                          StructureManager structureManager,
                                          ChunkGenerator generator, RandomSource random,
                                          BoundingBox box, ChunkPos chunkPos, BlockPos pos,
                                          CallbackInfo ci) {
        // 真实片段包围盒（非区块裁剪盒）；原版沼泽小屋角点 = 区块最小 XZ
        BoundingBox pieceBox = ((StructurePiece) (Object) this).getBoundingBox();
        int cx = pieceBox.minX() + (pieceBox.maxX() - pieceBox.minX()) / 2;
        int cz = pieceBox.minZ() + (pieceBox.maxZ() - pieceBox.minZ()) / 2;
        long centerChunk = new ChunkPos(new BlockPos(cx, 0, cz)).toLong();

        // 已处理过的小屋：阻止原版生成（重复 postProcess 调用幂等）
        if (ModMain.GEN_BOSS_HUT_CHUNKS.contains(centerChunk)) {
            ci.cancel();
            return;
        }

        if (ModMain.genRollBossHut(random, ModConfig.DEBUG_MODE.get())) {
            // 登记成功（寻岸失败返回 false 时回退原版小屋）
            if (ModMain.scheduleBossHut(level, pieceBox)) {
                ModMain.GEN_BOSS_HUT_CHUNKS.add(centerChunk);
                ci.cancel(); // 阻止原版小屋生成
            }
        }
    }
}
