package cn.autoforged.joes_addons_for_abmc.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 「幸运之庙」的结构片段：直接用原版的 {@link TemplateStructurePiece} 来放模板，因此
 * <b>不需要</b>自己写放置逻辑，也不用担心区块裁剪问题——它会：
 * <ul>
 *   <li>{@code placeInWorld(...)} 逐格 {@code setBlock}（flag 2），模板里的<b>空气</b>同样会被写进
 *       世界，所以结构体积内地形的凸起会被替换掉；因为是直接 setBlock 而不是破坏方块，
 *       全程<b>不会产生掉落物</b>；</li>
 *   <li>把 {@code placeSettings} 的包围盒限制成当前区块的可写范围，跨区块写入会被自动忽略；</li>
 *   <li>区块存盘后重新读入时（例如存档退出再进、或邻近区块稍后才生成），按 {@code Template} 字段里
 *       的模板 id 从 {@link StructureTemplateManager} 重新加载模板，因此不依赖内存里的临时引用。</li>
 * </ul>
 */
public class LuckyTemplePiece extends TemplateStructurePiece {

    /** 世界生成期构造：模板放在 origin（= 结构最小角）。 */
    public LuckyTemplePiece(StructureTemplateManager structureTemplateManager, BlockPos origin) {
        super(ModStructures.LUCKY_TEMPLE_PIECE.get(), 0, structureTemplateManager,
            LuckyTempleStructure.TEMPLATE_ID, LuckyTempleStructure.TEMPLATE_ID.toString(),
            makePlaceSettings(), origin);
    }

    /** 反序列化构造：模板由 StructureTemplateManager 按 id 重新加载。 */
    public LuckyTemplePiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
        super(ModStructures.LUCKY_TEMPLE_PIECE.get(), tag, structureTemplateManager, location -> makePlaceSettings());
    }

    private static StructurePlaceSettings makePlaceSettings() {
        return new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
        // 模板里没有结构方块的 DATA 标记，无需处理
    }
}
