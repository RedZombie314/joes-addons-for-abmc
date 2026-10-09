package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>岩浆牢笼</b>：被附体空壳的一种"仅针对玩家"的攻击 —— 直接把玩家关进一个铁栅栏 + 岩浆的笼子里。
 *
 * <h2>结构从哪来</h2>
 * 用户上传的 {@code uploads/lava_cage.nbt} 已原样放进模组资源
 * （{@code data/joes_addons_for_abmc/structure/lava_cage.nbt}），这里在第一次使用时把它读进来：
 * <ul>
 *   <li>尺寸 <b>4 × 7 × 4</b>：四面是 7 格高的<b>铁栅栏</b>围墙（底面那一圈也是栅栏，中间留空），
 *       内部 2×2 全空，<b>最顶层（第 7 层）的内部是 4 个岩浆源</b> —— 放下去之后岩浆会顺着内部往下淌；</li>
 *   <li>结构自带的 {@code air} 方块<b>不放置</b>（见 {@link #place}：只替换空气，空气本身写得再多也没意义）。</li>
 * </ul>
 *
 * <h2>放置规则（用户指定）</h2>
 * <ul>
 *   <li>水平方向<b>以玩家为中心</b>铺开（4×4 的结构，玩家落在内部那 2×2 里）；</li>
 *   <li>结构<b>最低的一层与玩家脚底的 y 对齐</b>；</li>
 *   <li><b>只替换空气</b>：目标位置不是空气就跳过那一格 —— 所以笼子该缺的角会缺，不会把地形啃掉；</li>
 *   <li>放置带 {@link Block#UPDATE_ALL}：岩浆要能正常开始流动（流体刻是放置时排的），
 *       所以不能像神庙那样关掉邻居更新。</li>
 * </ul>
 *
 * <h2>为什么自己解析 NBT 而不用 {@code StructureTemplate}</h2>
 * {@code StructureTemplate} 的方块表（{@code palettes}）是私有的，公开 API 只能"按某种方块过滤"，
 * 拿不到全量列表；而这里需要"逐格判断目标是不是空气"，只能自己遍历。
 * 结构格式很简单（{@code size} / {@code palette} / {@code blocks}），
 * 而且这份结构的 {@code DataVersion} 就是 1.21.1 的 3955，不需要走 DataFixer。
 */
public final class OrbLavaCage {

    /** 结构文件在资源包里的位置（{@code FileToIdConverter("structure", ".nbt")} 的同一套换算）。 */
    private static final ResourceLocation FILE_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "structure/lava_cage.nbt");

    /** 解析出来的一格：相对坐标（已按最低点归一化）+ 方块状态。 */
    private record Piece(BlockPos offset, BlockState state) {
    }

    /** 解析结果 + 它对应的资源管理器（资源包重载后会自动重新读）。 */
    @Nullable
    private static ResourceManager cachedManager;
    private static List<Piece> cachedPieces = List.of();
    private static int cachedSizeX;
    private static int cachedSizeZ;

    private OrbLavaCage() {
    }

    /**
     * 在目标（玩家）身上铺一座岩浆牢笼。
     *
     * @return 是否真的放下了至少一格（读不到结构 / 全被挡住时返回 false）
     */
    public static boolean place(ServerLevel level, LivingEntity target) {
        if (!ensureLoaded(level)) {
            return false;
        }
        // 水平居中：结构宽度的一半往左/往后再挪，使玩家落在结构内部
        int originX = Mth.floor(target.getX()) - cachedSizeX / 2;
        int originZ = Mth.floor(target.getZ()) - cachedSizeZ / 2;
        // 最低一层与玩家脚底对齐（Piece 的相对坐标已经把最低点归一到 0）
        int originY = Mth.floor(target.getY());

        int placed = 0;
        for (Piece piece : cachedPieces) {
            BlockPos pos = new BlockPos(originX + piece.offset().getX(),
                originY + piece.offset().getY(),
                originZ + piece.offset().getZ());
            // 只替换空气：别的方块（包括水、草）一律不动
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            level.setBlock(pos, piece.state(), Block.UPDATE_ALL);
            placed++;
        }
        if (placed > 0) {
            ModMain.LOGGER.info("[附体] 岩浆牢笼: 目标={} 中心=({},{},{}) 放置={} 格（结构 {}×{}）",
                target.getName().getString(), originX, originY, originZ, placed,
                cachedSizeX, cachedSizeZ);
        } else {
            ModMain.LOGGER.info("[附体] 岩浆牢笼: 目标={} 处没有可放置的空气格，本次不生效",
                target.getName().getString());
        }
        return placed > 0;
    }

    /** 确保结构已经解析过（资源管理器换了就重新读，例如数据包重载）。 */
    private static boolean ensureLoaded(ServerLevel level) {
        if (level.getServer() == null) {
            return false;
        }
        ResourceManager manager = level.getServer().getResourceManager();
        if (manager == cachedManager) {
            return !cachedPieces.isEmpty();
        }
        cachedManager = manager;
        cachedPieces = List.of();
        try (InputStream in = manager.open(FILE_ID)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            cachedPieces = parse(level, tag);
            ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
            cachedSizeX = sizeTag.size() > 0 ? sizeTag.getInt(0) : 0;
            cachedSizeZ = sizeTag.size() > 2 ? sizeTag.getInt(2) : 0;
        } catch (Exception exception) {
            ModMain.LOGGER.error("[附体] 岩浆牢笼结构读取失败: {}", FILE_ID, exception);
            return false;
        }
        return !cachedPieces.isEmpty();
    }

    /**
     * 解析结构 NBT：{@code palette} 查方块状态、{@code blocks} 逐格取相对坐标，
     * 再把整份结构平移到"最低点 = (0,0,0)"（这样放置时 y 直接取玩家脚底就行）。
     * <p>
     * 结构自带的空气格直接丢掉：放置规则本来就是"只替换空气"，留着只是白跑循环。
     */
    private static List<Piece> parse(ServerLevel level, CompoundTag tag) {
        HolderGetter<Block> lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(lookup, paletteTag.getCompound(i)));
        }
        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);

        // 第一遍：记录原始相对坐标，顺便求出最低点
        List<BlockPos> rawPositions = new ArrayList<>(blocksTag.size());
        List<BlockState> rawStates = new ArrayList<>(blocksTag.size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag one = blocksTag.getCompound(i);
            ListTag posTag = one.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            int stateIndex = one.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(stateIndex);
            if (state.isAir()) {
                continue;   // 结构里的空气不参与放置
            }
            rawPositions.add(pos);
            rawStates.add(state);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        if (rawPositions.isEmpty()) {
            return List.of();
        }
        // 第二遍：整体平移到最低点为原点（y 归一化之后，"最低一层"就是 offset.y == 0）
        List<Piece> pieces = new ArrayList<>(rawPositions.size());
        for (int i = 0; i < rawPositions.size(); i++) {
            BlockPos pos = rawPositions.get(i);
            pieces.add(new Piece(new BlockPos(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ),
                rawStates.get(i)));
        }
        return pieces;
    }
}
