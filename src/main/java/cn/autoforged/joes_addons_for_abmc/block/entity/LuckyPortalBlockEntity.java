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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
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
     * 在传送门结构之外 1~2 格处找一个安全落点。
     * 传送门结构为以 portalCenter 为起点的 2x2 区域，因此沿随机方向向外偏移 1~2 格即可落在结构之外。
     */
    private static BlockPos findSafeTeleportPos(ServerLevel targetLevel, BlockPos portalCenter) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int[] d = dirs[targetLevel.getRandom().nextInt(dirs.length)];
        int dist = 1 + targetLevel.getRandom().nextInt(2); // 1 或 2
        int x = portalCenter.getX() + d[0] * dist;
        int z = portalCenter.getZ() + d[1] * dist;
        return findSafePos(targetLevel, x, z);
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

    public static BlockPos detectPortalStructure(ServerLevel level, BlockPos waterPos) {
        int[][] waterOffsets = {{1, 1}, {2, 1}, {1, 2}, {2, 2}};
        int[][] goldOffsets = {
            {1, 0}, {2, 0},
            {0, 1}, {3, 1},
            {0, 2}, {3, 2},
            {1, 3}, {2, 3}
        };

        for (int[] wo : waterOffsets) {
            BlockPos origin = waterPos.offset(-wo[0], 0, -wo[1]);

            boolean valid = true;
            for (int[] gp : goldOffsets) {
                BlockPos checkPos = origin.offset(gp[0], 0, gp[1]);
                if (!level.getBlockState(checkPos).is(Blocks.GOLD_BLOCK)) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            for (int[] pwo : waterOffsets) {
                BlockPos checkPos = origin.offset(pwo[0], 0, pwo[1]);
                FluidState fluid = level.getFluidState(checkPos);
                if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            BlockPos portalCenter = origin.offset(1, 0, 1);
            return portalCenter;
        }

        return null;
    }

    public static void createPortalAtWaterStructure(ServerLevel level, BlockPos waterPos) {
        BlockPos portalCenter = detectPortalStructure(level, waterPos);
        if (portalCenter == null) return;

        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(portalCenter.getX() + 0.5, portalCenter.getY(), portalCenter.getZ() + 0.5);
            level.addFreshEntity(lightning);
        }

        int[][] portalOffsets = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (int[] po : portalOffsets) {
            BlockPos pp = portalCenter.offset(po[0], 0, po[1]);
            level.setBlock(pp, ModBlocks.LUCKY_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        }
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
