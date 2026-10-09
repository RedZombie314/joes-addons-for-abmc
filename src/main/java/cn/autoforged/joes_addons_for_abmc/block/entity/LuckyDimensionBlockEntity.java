package cn.autoforged.joes_addons_for_abmc.block.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LuckyDimensionBlockEntity extends BlockEntity {
    private static final List<Block> FULL_CUBE_BLOCKS = new ArrayList<>();
    private static final List<ResourceLocation> FULL_CUBE_KEYS = new ArrayList<>();
    private static final Set<ResourceLocation> USED_TEXTURES = Collections.synchronizedSet(new HashSet<>());
    private static boolean poolInitialized = false;

    private static final ResourceLocation LUCKY_DIMENSION_KEY = ResourceLocation.fromNamespaceAndPath(
        ModMain.MODID, "lucky_dimension");

    private ResourceLocation currentTexture = null;
    private long lastChangeTick = -1;
    private int positionOffset = -1;

    public LuckyDimensionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUCKY_DIMENSION_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * 收集"可以拿来当伪装外观"的方块池。
     *
     * <p>判定全部走游戏自身的数据（不维护黑名单）：
     * <ol>
     *   <li>{@code getRenderShape() == RenderShape.MODEL} —— 排掉 {@code INVISIBLE}（屏障、光源、结构空位、
     *       水/岩浆、移动中的活塞…）与 {@code ENTITYBLOCK_ANIMATED}（箱子、告示牌、旗帜、潜影盒、床、钟、
     *       头颅、饰纹陶罐…）。这两类用 {@code renderSingleBlock} 画不出任何东西，
     *       正是"随机材质完全不显示"的原因；</li>
     *   <li>{@code !hasBlockEntity()} —— 外观依赖方块实体数据的方块不做伪装；</li>
     *   <li>{@code isCollisionShapeFullBlock} + 轮廓形状（{@code getShape}）外接盒三个方向都接近 1 格 ——
     *       保证确实是<b>完整立方体</b>（堆肥桶、炼药锅、灵魂沙、土径等只有一半/凹陷形状的会被排掉）。</li>
     * </ol>
     */
    public static synchronized void initFullCubePool() {
        if (poolInitialized) return;
        // 先填进临时表，最后再置位 + 发布，避免并发（区块网格在工作线程上编译）读到半成品
        List<Block> blocks = new ArrayList<>();
        List<ResourceLocation> keys = new ArrayList<>();
        // 固定选取池（按需求）：干草块、黄色羊毛、黄色混凝土、黄色混凝土粉末、黄色陶瓦、
        // 鹿角珊瑚块、沙子、粗金块、竹板
        // （原先是"遍历全部方块注册表自动筛选"，现改为固定这几个；
        //   橙色羊毛/橙色混凝土/橙色陶瓦/橙色混凝土粉末已按需求移除）
        for (Block block : new Block[]{
            Blocks.HAY_BLOCK,
            Blocks.YELLOW_WOOL,
            Blocks.YELLOW_CONCRETE,
            Blocks.YELLOW_CONCRETE_POWDER,
            Blocks.YELLOW_TERRACOTTA,
            Blocks.HORN_CORAL_BLOCK,
            Blocks.SAND,
            Blocks.RAW_GOLD_BLOCK,
            Blocks.BAMBOO_PLANKS}) {
            if (block == Blocks.AIR) continue;
            BlockState state = block.defaultBlockState();
            if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
            if (state.hasBlockEntity()) continue;
            if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
            // 必须是"完全不透明、能挡住邻居面"的方块：否则玻璃/冰/黏液/树叶这类半透明材质
            // 会被当成伪装外观，让幸运维度方块变得能透视（需求明确排除）
            if (!state.canOcclude()) continue;
            net.minecraft.world.phys.shapes.VoxelShape outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (outline.isEmpty()) continue;
            net.minecraft.world.phys.AABB bounds = outline.bounds();
            if (bounds.getXsize() < 0.999D || bounds.getYsize() < 0.999D || bounds.getZsize() < 0.999D) continue;
            blocks.add(block);
            keys.add(BuiltInRegistries.BLOCK.getKey(block));
        }
        FULL_CUBE_BLOCKS.addAll(blocks);
        FULL_CUBE_KEYS.addAll(keys);
        poolInitialized = true;
    }

    /** 伪装方块池（供客户端渲染器按坐标哈希取用；首次调用会构建）。 */
    public static List<Block> fullCubePool() {
        initFullCubePool();
        return FULL_CUBE_BLOCKS;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LuckyDimensionBlockEntity blockEntity) {
        if (level.isClientSide) return;
        // 幸运维度内：整片地形都是这个方块，外观改由客户端按坐标哈希自算（见渲染器）。
        // 这里必须什么都不做 —— 否则每 10 刻每个方块都要 sendBlockUpdated 发一次方块更新包，
        // 几十万个方块就是"整个维度摆烂"的元凶。
        if (isInLuckyDimension(level)) return;

        if (blockEntity.positionOffset < 0) {
            blockEntity.positionOffset = Math.floorMod(pos.asLong(), 10);
        }

        long gameTime = level.getGameTime();
        if ((gameTime + blockEntity.positionOffset) % 10 == 0 && gameTime != blockEntity.lastChangeTick) {
            blockEntity.lastChangeTick = gameTime;
            blockEntity.pickRandomTexture();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            if (isInLuckyDimension(level)) return;
            if (positionOffset < 0) {
                positionOffset = Math.floorMod(worldPosition.asLong(), 10);
            }
            if (currentTexture == null) {
                lastChangeTick = level.getGameTime();
                pickRandomTexture();
            }
        }
    }

    private static boolean isInLuckyDimension(Level level) {
        return LUCKY_DIMENSION_KEY.equals(level.dimension().location());
    }

    private void pickRandomTexture() {
        initFullCubePool();
        if (FULL_CUBE_BLOCKS.isEmpty()) return;

        if (currentTexture != null) {
            USED_TEXTURES.remove(currentTexture);
        }

        List<Integer> availableIndices = new ArrayList<>();
        for (int i = 0; i < FULL_CUBE_KEYS.size(); i++) {
            if (!USED_TEXTURES.contains(FULL_CUBE_KEYS.get(i))) {
                availableIndices.add(i);
            }
        }

        if (availableIndices.isEmpty()) {
            availableIndices = new ArrayList<>();
            for (int i = 0; i < FULL_CUBE_KEYS.size(); i++) {
                availableIndices.add(i);
            }
        }

        int idx = availableIndices.get(level.random.nextInt(availableIndices.size()));
        currentTexture = FULL_CUBE_KEYS.get(idx);
        USED_TEXTURES.add(currentTexture);

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public ResourceLocation getCurrentTexture() {
        return currentTexture;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (currentTexture != null) {
            tag.putString("currentTexture", currentTexture.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("currentTexture")) {
            currentTexture = ResourceLocation.tryParse(tag.getString("currentTexture"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide && currentTexture != null) {
            USED_TEXTURES.remove(currentTexture);
        }
    }

    public static void clearUsedTextures() {
        USED_TEXTURES.clear();
        FULL_CUBE_BLOCKS.clear();
        FULL_CUBE_KEYS.clear();
        poolInitialized = false;
    }
}
