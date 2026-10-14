package cn.autoforged.joes_addons_for_abmc.block.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import cn.autoforged.joes_addons_for_abmc.block.LuckyPortalBlock;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LuckyPortalBlockEntity extends BlockEntity {
    private BlockPos returnPos;
    private ResourceKey<Level> returnDim;

    public LuckyPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUCKY_PORTAL_BLOCK_ENTITY.get(), pos, state);
    }

    public void setReturnTarget(ResourceKey<Level> dim, BlockPos pos) {
        this.returnDim = dim;
        this.returnPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void handleTeleport(ServerPlayer player) {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        if (returnPos != null && returnDim != null) {
            ServerLevel targetLevel = serverLevel.getServer().getLevel(returnDim);
            if (targetLevel != null) {
                BlockPos teleportPos = findSafeTeleportPos(targetLevel, returnPos);
                player.teleportTo(targetLevel,
                    teleportPos.getX() + 0.5,
                    teleportPos.getY(),
                    teleportPos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            }
            return;
        }

        ResourceLocation luckyDimLoc = ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dimension");

        if (level.dimension() == Level.OVERWORLD) {
            ServerLevel luckyLevel = serverLevel.getServer().getLevel(ModDimensions.LUCKY_DIM_LEVEL);
            if (luckyLevel == null) return;

            int x = worldPosition.getX();
            int z = worldPosition.getZ();
            BlockPos safePos = findSafePos(luckyLevel, x, z);
            BlockPos portalCenter = safePos;

            createPortalStructureAt(luckyLevel, portalCenter, Level.OVERWORLD, worldPosition);

            setReturnTarget(ModDimensions.LUCKY_DIM_LEVEL, portalCenter);
            propagateReturnTargetToNeighbors(ModDimensions.LUCKY_DIM_LEVEL, portalCenter);

            BlockPos teleportPos = findSafeTeleportPos(luckyLevel, portalCenter);
            player.teleportTo(luckyLevel,
                teleportPos.getX() + 0.5,
                teleportPos.getY(),
                teleportPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        } else if (level.dimension().location().equals(luckyDimLoc)) {
            ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) return;

            int x = worldPosition.getX();
            int z = worldPosition.getZ();
            BlockPos safePos = findSafePos(overworld, x, z);
            BlockPos portalCenter = safePos;

            createPortalStructureAt(overworld, portalCenter, ModDimensions.LUCKY_DIM_LEVEL, worldPosition);

            setReturnTarget(Level.OVERWORLD, portalCenter);
            propagateReturnTargetToNeighbors(Level.OVERWORLD, portalCenter);

            BlockPos teleportPos = findSafeTeleportPos(overworld, portalCenter);
            player.teleportTo(overworld,
                teleportPos.getX() + 0.5,
                teleportPos.getY(),
                teleportPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }
    }

    /**
     * 落地位置与门的水平距离下限（格）。<b>不能小于 2</b>。
     *
     * <p>原因：目的地那一扇门是以 {@code portalCenter} 为原点的 <b>2×2</b> 区域
     * （{@link #createPortalStructureAt} 的 {@code portalOffsets} = offset 0~1），
     * 所以轴向偏移 <b>1</b> 格时 x/z 会正好与门所占的 0~1 重叠 —— 落点就在门里。
     * 而门没有碰撞箱（{@code LuckyPortalBlock.COLLISION_SHAPE = Shapes.empty()}），
     * 玩家会被直接"托"在门上一格然后穿下去，最终站在门方块所在的格子里；
     * 创造/旁观模式又是"碰到立刻传送"（{@code LuckyPortalBlock.handlePlayerInPortal}），
     * 于是下一 tick 立刻被弹回原维度 —— 这就是"进单格门有概率被传回主世界"的根因。
     * 偏移 ≥ 2 时 x/z 与 0~1 不可能重叠，落点必然在结构外。</p>
     */
    private static final int LANDING_DISTANCE_MIN = 2;

    /**
     * 往外最多试到几格。用于避开"脚下又是一扇门"的落点（例如附近已经有别的传送门结构）。
     * 4 个方向 × 这个区间逐个校验，都拿到第 4 项；实在全不合格才退到兜底。
     */
    private static final int LANDING_DISTANCE_MAX = 6;

    /**
     * 在传送门结构之外找一个安全落点。
     *
     * <p>从 {@link #LANDING_DISTANCE_MIN}（= 2，保证在 2×2 结构外）开始逐格往外试，
     * 每个距离下 4 个方向随机排序，取第一个"落点干净"的列（见 {@link #isLandingClear}）。</p>
     */
    private static BlockPos findSafeTeleportPos(ServerLevel targetLevel, BlockPos portalCenter) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        // 方向顺序随机（Fisher-Yates，用世界自己的随机源），避免每次都被安排到同一侧
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = targetLevel.getRandom().nextInt(i + 1);
            int[] tmp = dirs[i];
            dirs[i] = dirs[j];
            dirs[j] = tmp;
        }

        BlockPos fallback = null;
        for (int dist = LANDING_DISTANCE_MIN; dist <= LANDING_DISTANCE_MAX; dist++) {
            for (int[] d : dirs) {
                BlockPos candidate = findSafePos(targetLevel,
                    portalCenter.getX() + d[0] * dist,
                    portalCenter.getZ() + d[1] * dist);
                if (fallback == null) {
                    fallback = candidate;
                }
                if (isLandingClear(targetLevel, candidate)) {
                    return candidate;
                }
            }
        }
        // 极端情况（周围一圈全是门）：给结构外最靠里的那一个，宁可落点不理想也不要原地弹回去。
        return fallback != null
            ? fallback
            : findSafePos(targetLevel, portalCenter.getX() + LANDING_DISTANCE_MIN, portalCenter.getZ());
    }

    /**
     * 落点是否"干净"：从落点往下走到真正踩得住的方块，<b>途中（含停下来的那一格）不能有幸运传送门方块</b>。
     *
     * <p>{@link #findSafePos} 只保证"落点是空气"，而门没有碰撞箱、会被它当成"地面"：
     * 玩家会被放在门的正上方一格，然后穿过门落进门格子里 → 创造模式下一 tick 就被传回去。
     * 这里把"脚下是不是实心"换成"会不会半路遇见门"来判定，正是为了挡住这种情况。</p>
     *
     * @return true = 可以落
     */
    private static boolean isLandingClear(ServerLevel level, BlockPos landing) {
        BlockPos.MutableBlockPos p = landing.mutable();
        int minY = level.getMinBuildHeight();
        while (p.getY() > minY) {
            BlockPos below = p.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock() instanceof LuckyPortalBlock) {
                return false;
            }
            if (!belowState.getCollisionShape(level, below).isEmpty()) {
                return true;
            }
            p.move(0, -1, 0);
        }
        return true;
    }

    private void propagateReturnTargetToNeighbors(ResourceKey<Level> targetDim, BlockPos targetPos) {
        if (level == null || level.isClientSide()) return;
        int[][] neighborOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] no : neighborOffsets) {
            BlockPos adjPos = worldPosition.offset(no[0], 0, no[1]);
            if (level.getBlockState(adjPos).getBlock() instanceof LuckyPortalBlock) {
                BlockEntity be = level.getBlockEntity(adjPos);
                if (be instanceof LuckyPortalBlockEntity adjBe) {
                    if (adjBe.returnPos == null) {
                        adjBe.setReturnTarget(targetDim, targetPos);
                    }
                }
            }
        }
    }

    public static BlockPos findSafePos(ServerLevel level, int x, int z) {
        int y = level.getMaxBuildHeight() - 1;
        while (y > level.getMinBuildHeight()) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                BlockPos above = pos.above();
                if (level.getBlockState(above).isAir() && level.getBlockState(above.above()).isAir()) {
                    return above;
                }
            }
            y--;
        }
        return new BlockPos(x, 64, z);
    }

    public static BlockPos createPortalStructureAt(ServerLevel level, BlockPos center,
                                                    ResourceKey<Level> returnDim, BlockPos returnPos) {
        int[][] goldOffsets = {
            {0, -1}, {1, -1},
            {-1, 0}, {2, 0},
            {-1, 1}, {2, 1},
            {0, 2}, {1, 2}
        };

        int[][] portalOffsets = {
            {0, 0}, {1, 0},
            {0, 1}, {1, 1}
        };

        for (int[] go : goldOffsets) {
            BlockPos gp = center.offset(go[0], 0, go[1]);
            level.setBlock(gp, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }

        BlockPos firstPortalPos = null;
        for (int[] po : portalOffsets) {
            BlockPos pp = center.offset(po[0], 0, po[1]);
            level.setBlock(pp, ModBlocks.LUCKY_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity be = level.getBlockEntity(pp);
            if (be instanceof LuckyPortalBlockEntity pbe) {
                pbe.setReturnTarget(returnDim, returnPos);
            }
            if (firstPortalPos == null) firstPortalPos = pp;
        }

        return firstPortalPos != null ? firstPortalPos : center;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (returnPos != null && returnDim != null) {
            tag.putInt("returnX", returnPos.getX());
            tag.putInt("returnY", returnPos.getY());
            tag.putInt("returnZ", returnPos.getZ());
            tag.putString("returnDim", returnDim.location().toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("returnX") && tag.contains("returnDim")) {
            int x = tag.getInt("returnX");
            int y = tag.getInt("returnY");
            int z = tag.getInt("returnZ");
            returnPos = new BlockPos(x, y, z);
            String dimStr = tag.getString("returnDim");
            returnDim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.tryParse(dimStr));
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
}
