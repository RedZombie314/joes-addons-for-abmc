package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class LapisFallingBlockEntity extends FallingBlockEntity {

    public static final byte STATE_FLOATING = 1;
    public static final byte STATE_LAUNCHED = 2;

    private static final EntityDataAccessor<Byte> DATA_LAPIS_STATE =
        SynchedEntityData.defineId(LapisFallingBlockEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_HAS_GLINT =
        SynchedEntityData.defineId(LapisFallingBlockEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockState myBlockState = Blocks.STONE.defaultBlockState();
    @Nullable
    public CompoundTag myBlockData;

    public Vec3 launchDirection = Vec3.ZERO;
    public double launchSpeed = 1.5;
    public long creationGameTime = 0;
    public int internalTick = 0;
    public boolean hasLanded = false;
    public boolean grabbed = false;

    public LapisFallingBlockEntity(EntityType<? extends LapisFallingBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.dropItem = false;
        this.disableDrop();
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LAPIS_STATE, STATE_FLOATING);
        builder.define(DATA_HAS_GLINT, true);
    }

    public byte getLapisState() {
        return this.entityData.get(DATA_LAPIS_STATE);
    }

    public void setLapisState(byte state) {
        this.entityData.set(DATA_LAPIS_STATE, state);
    }

    public boolean getHasGlint() {
        return this.entityData.get(DATA_HAS_GLINT);
    }

    public void setHasGlint(boolean glint) {
        this.entityData.set(DATA_HAS_GLINT, glint);
    }

    @Override
    public BlockState getBlockState() {
        return this.myBlockState;
    }

    public void initFromBlock(Level level, BlockPos pos, BlockState state) {
        this.myBlockState = state.hasProperty(BlockStateProperties.WATERLOGGED)
            ? state.setValue(BlockStateProperties.WATERLOGGED, false)
            : state;
        this.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setStartPos(pos);
        if (state.hasBlockEntity()) {
            var be = level.getBlockEntity(pos);
            if (be != null) {
                this.myBlockData = be.saveWithFullMetadata(level.registryAccess());
            }
        }
        level.removeBlock(pos, false);
    }

    public void initFromBlockState(BlockState state) {
        this.myBlockState = state;
        this.blocksBuilding = true;
    }

    @Override
    public void tick() {
        if (this.myBlockState.isAir()) {
            this.discard();
            return;
        }

        this.internalTick++;

        if (this.level().isClientSide) {
            if (!this.isNoGravity()) {
                this.applyGravity();
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
            }
            return;
        }

        byte state = getLapisState();
        if (state == STATE_FLOATING) {
            this.time = 0;
            if (!this.grabbed) {
                this.setDeltaMovement(0, 0.05, 0);
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else if (state == STATE_LAUNCHED) {
            tickLaunched();
        } else {
            super.tick();
        }

        this.handlePortal();
    }

    private void tickLaunched() {
        this.time = 0;
        this.setDeltaMovement(this.launchDirection.scale(this.launchSpeed));
        this.move(MoverType.SELF, this.getDeltaMovement());

        Direction closestFace = findClosestNearbyBlockFace();
        if (closestFace != null) {
            tryPlaceBlock();
        }
    }

    private Direction findClosestNearbyBlockFace() {
        AABB box = this.getBoundingBox().inflate(0.01);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);

        Direction bestFace = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 entityCenter = this.position();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = this.level().getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;

                    Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    Vec3 toBlock = blockCenter.subtract(entityCenter);
                    double absX = Math.abs(toBlock.x);
                    double absY = Math.abs(toBlock.y);
                    double absZ = Math.abs(toBlock.z);

                    if (absX >= absY && absX >= absZ) {
                        if (absX < bestDist) {
                            bestDist = absX;
                            bestFace = toBlock.x > 0 ? Direction.EAST : Direction.WEST;
                        }
                    } else if (absY >= absX && absY >= absZ) {
                        if (absY < bestDist) {
                            bestDist = absY;
                            bestFace = toBlock.y > 0 ? Direction.UP : Direction.DOWN;
                        }
                    } else {
                        if (absZ < bestDist) {
                            bestDist = absZ;
                            bestFace = toBlock.z > 0 ? Direction.SOUTH : Direction.NORTH;
                        }
                    }
                }
            }
        }
        return bestFace;
    }

    private void tryPlaceBlock() {
        if (this.hasLanded) return;
        this.hasLanded = true;

        BlockPos blockpos = this.blockPosition();
        if (!this.level().isLoaded(blockpos)) {
            this.discard();
            return;
        }

        BlockState existing = this.level().getBlockState(blockpos);
        if (existing.isAir() || existing.canBeReplaced()) {
            if (this.level().setBlock(blockpos, this.myBlockState, 3)) {
                ((ServerLevel) this.level())
                    .getChunkSource()
                    .chunkMap
                    .broadcast(this, new ClientboundBlockUpdatePacket(blockpos, this.level().getBlockState(blockpos)));
                if (this.myBlockData != null && this.myBlockState.hasBlockEntity()) {
                    var be = this.level().getBlockEntity(blockpos);
                    if (be != null) {
                        CompoundTag tag = be.saveWithoutMetadata(this.level().registryAccess());
                        for (String key : this.myBlockData.getAllKeys()) {
                            tag.put(key, this.myBlockData.get(key).copy());
                        }
                        try {
                            be.loadWithComponents(tag, this.level().registryAccess());
                        } catch (Exception ignored) {
                        }
                        be.setChanged();
                    }
                }
            }
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("MyBlockState", NbtUtils.writeBlockState(this.myBlockState));
        compound.putInt("InternalTick", this.internalTick);
        compound.putLong("CreationGameTime", this.creationGameTime);
        compound.putDouble("DX", this.launchDirection.x);
        compound.putDouble("DY", this.launchDirection.y);
        compound.putDouble("DZ", this.launchDirection.z);
        compound.putDouble("LaunchSpeed", this.launchSpeed);
        compound.putByte("LapisState", getLapisState());
        compound.putBoolean("HasGlint", getHasGlint());
        compound.putBoolean("HasLanded", this.hasLanded);
        if (this.myBlockData != null) {
            compound.put("MyTileEntityData", this.myBlockData);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.myBlockState = NbtUtils.readBlockState(
            this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
            compound.getCompound("MyBlockState"));
        this.internalTick = compound.getInt("InternalTick");
        this.creationGameTime = compound.getLong("CreationGameTime");
        this.launchDirection = new Vec3(compound.getDouble("DX"), compound.getDouble("DY"), compound.getDouble("DZ"));
        this.launchSpeed = compound.getDouble("LaunchSpeed");
        if (compound.contains("LapisState")) {
            setLapisState(compound.getByte("LapisState"));
        }
        if (compound.contains("HasGlint")) {
            setHasGlint(compound.getBoolean("HasGlint"));
        }
        this.hasLanded = compound.getBoolean("HasLanded");
        if (compound.contains("MyTileEntityData", 10)) {
            this.myBlockData = compound.getCompound("MyTileEntityData").copy();
        }
        if (this.myBlockState.isAir()) {
            this.myBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.myBlockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }
}
