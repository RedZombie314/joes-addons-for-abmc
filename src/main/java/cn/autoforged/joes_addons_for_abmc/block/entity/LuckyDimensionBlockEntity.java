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

    public static void initFullCubePool() {
        if (poolInitialized) return;
        poolInitialized = true;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) continue;
            BlockState state = block.defaultBlockState();
            if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
                FULL_CUBE_BLOCKS.add(block);
                FULL_CUBE_KEYS.add(key);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LuckyDimensionBlockEntity blockEntity) {
        if (level.isClientSide) return;
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
