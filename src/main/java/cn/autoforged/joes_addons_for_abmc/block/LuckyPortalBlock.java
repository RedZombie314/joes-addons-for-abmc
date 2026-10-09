package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyPortalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.AABB;
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
        handlePlayerInPortal(player, level, pos);
    }

    /**
     * "玩家站在传送门方块里"的处理。{@link #entityInside} 与
     * {@link #tickSpectatorsOnPortal}（旁观者兜底）共用这一份逻辑。
     */
    public static void handlePlayerInPortal(ServerPlayer player, Level level, BlockPos pos) {
        UUID id = player.getUUID();
        if (PROCESSED_THIS_TICK.contains(id)) return;
        PROCESSED_THIS_TICK.add(id);

        // 创造模式玩家接触传送门时立即传送，无需等待 5 秒。
        // 旁观者同理：他们没有重力、可以悬停不动，而"连续 5 秒待在门里"的计时依赖每刻触发，
        // 旁观者又走不到 entityInside（见 tickSpectatorsOnPortal），干脆进门即走。
        if (player.isCreative() || player.isSpectator()) {
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

    /**
     * 旁观者的每刻兜底扫描：让他们也能与幸运传送门正常交互。
     * <p>
     * 根因：{@code Player#tick()} 里 {@code this.noPhysics = this.isSpectator()}，
     * 而 {@code Entity#move()} 在 {@code noPhysics} 时走的是"直接 setPos"的短路分支
     * （Entity.java:619-622），<b>不会</b>调用 {@code tryCheckInsideBlocks()} ——
     * 也就是说 {@code Block#entityInside} 对旁观者<b>永远不会触发</b>，
     * 传送门自然对他们毫无反应。这里按玩家碰撞箱把脚下的方块扫一遍补上（旁观者已是立即传送）。
     */
    public static void tickSpectatorsOnPortal(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            // 必须遍历快照：handlePlayerInPortal 会 teleportTo 到另一个维度，
            // 而跨维度传送会把玩家从本维度的 players 列表里移除 ——
            // 直接遍历 level.players() 会抛 ConcurrentModificationException（曾经真的崩过）。
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (!player.isSpectator()) continue;
                AABB box = player.getBoundingBox();
                // 与 Entity#checkInsideBlocks 同样的取整方式（各边内缩 1e-7，避免贴边多算一格）
                BlockPos min = BlockPos.containing(box.minX + 1.0E-7, box.minY + 1.0E-7, box.minZ + 1.0E-7);
                BlockPos max = BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7);
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                    if (level.getBlockState(pos).getBlock() instanceof LuckyPortalBlock) {
                        handlePlayerInPortal(player, level, pos.immutable());
                        break;
                    }
                }
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
