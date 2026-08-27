package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyPortalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LuckyPortalBlock extends BaseEntityBlock {
    public static final MapCodec<LuckyPortalBlock> CODEC = simpleCodec(LuckyPortalBlock::new);

    private static final VoxelShape PORTAL_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    private static final VoxelShape COLLISION_SHAPE = Shapes.empty();

    private static final Map<UUID, long[]> PORTAL_TIMERS = new HashMap<>();

    private static final Set<UUID> PROCESSED_THIS_TICK = new HashSet<>();

    public LuckyPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PORTAL_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LuckyPortalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        if (PROCESSED_THIS_TICK.contains(id)) return;
        PROCESSED_THIS_TICK.add(id);

        // 创造模式玩家接触传送门时立即传送，无需等待 5 秒
        if (player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LuckyPortalBlockEntity portalBe) {
                PORTAL_TIMERS.remove(id);
                portalBe.handleTeleport(player);
            }
            return;
        }

        long[] data = PORTAL_TIMERS.computeIfAbsent(id, k -> new long[]{0, 0});
        long currentTick = level.getGameTime();

        if (data[1] == currentTick - 1) {
            data[0]++;
        } else {
            data[0] = 1;
        }
        data[1] = currentTick;

        if (data[0] >= 100) {
            PORTAL_TIMERS.remove(id);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LuckyPortalBlockEntity portalBe) {
                portalBe.handleTeleport(player);
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    public static void clearProcessedThisTick() {
        PROCESSED_THIS_TICK.clear();
    }

    public static void removePortalTimer(UUID uuid) {
        PORTAL_TIMERS.remove(uuid);
    }

    public static void resetPortalTimers() {
        PORTAL_TIMERS.clear();
        PROCESSED_THIS_TICK.clear();
    }
}
