package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 「幸运之庙」（lucky_temple）自然生成相关的注册项。
 *
 * <p>本 mod 之前的女巫Boss小屋走的是「拦截原版结构 + 主线程延迟放置」的路子；「幸运之庙」是一个
 * 完全独立的新结构，因此按原版的方式注册 {@link StructureType} 与 {@link StructurePieceType}，
 * 再由数据包 JSON 描述生物群系与生成概率：</p>
 * <ul>
 *   <li>{@code data/joes_addons_for_abmc/worldgen/structure/lucky_temple.json} —— 结构本体
 *       （type = {@code joes_addons_for_abmc:lucky_temple}，biomes = {@code minecraft:plains}）；</li>
 *   <li>{@code data/joes_addons_for_abmc/worldgen/structure_set/lucky_temples.json} —— 生成概率
 *       （random_spread，间距/间隔与平原村庄一致）；</li>
 *   <li>{@code data/joes_addons_for_abmc/structure/lucky_temple.nbt} —— 建筑模板。</li>
 * </ul>
 */
public final class ModStructures {

    /** 结构类型注册表（对应 worldgen/structure/*.json 里的 "type" 字段）。 */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, ModMain.MODID);

    /** 结构片段类型注册表：区块存盘后重新读入结构片段时按 id 反序列化，缺了会崩。 */
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, ModMain.MODID);

    private static final StructureType<LuckyTempleStructure> LUCKY_TEMPLE_TYPE = () -> LuckyTempleStructure.CODEC;

    /** 反序列化构造 = (StructureTemplateManager, CompoundTag)，对应 StructureTemplateType。 */
    private static final StructurePieceType LUCKY_TEMPLE_PIECE_TYPE =
        (StructurePieceType.StructureTemplateType) LuckyTemplePiece::new;

    public static final Supplier<StructureType<?>> LUCKY_TEMPLE =
        STRUCTURE_TYPES.register("lucky_temple", () -> LUCKY_TEMPLE_TYPE);

    public static final Supplier<StructurePieceType> LUCKY_TEMPLE_PIECE =
        PIECE_TYPES.register("lucky_temple", () -> LUCKY_TEMPLE_PIECE_TYPE);

    private ModStructures() {
    }
}
