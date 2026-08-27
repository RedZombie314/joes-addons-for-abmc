package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class BedrockFallingBlockEntity extends FallingBlockEntity {

    private BlockState myBlockState = Blocks.STONE.defaultBlockState();
    @Nullable
    public CompoundTag myBlockData;

    public Vec3 moveDirection = Vec3.ZERO;
    public double speed = 1.5;
    public boolean gravitized = false;
    public int gravityStartTick = 0;
    public int aboveHitTick = 0;
    public long creationGameTime = 0;
    public int internalTick = 0;
    public boolean hasLanded = false;

    public BedrockFallingBlockEntity(EntityType<? extends BedrockFallingBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.dropItem = false;
        this.disableDrop();
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void initFromBlock(Level level, BlockPos pos, BlockState state) {
        this.myBlockState = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
            ? state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false)
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

    @Override
    public BlockState getBlockState() {
        return this.myBlockState;
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

        if (this.isNoGravity() && !this.gravitized) {
            handleForwardMovement();
        } else {
            handleGravityMovement();
        }

        this.handlePortal();
    }

    private void handleForwardMovement() {
        long gameTime = ((ServerLevel) this.level()).getGameTime();

        if (this.creationGameTime > 0 && gameTime - this.creationGameTime > 1200) {
            this.gravitized = true;
            this.setNoGravity(false);
            this.noPhysics = false;
            this.gravityStartTick = this.internalTick;
            return;
        }

        if (this.creationGameTime > 0 && gameTime - this.creationGameTime < 20) {
            this.setDeltaMovement(this.moveDirection.scale(this.speed));
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }

        this.setDeltaMovement(this.moveDirection.scale(this.speed));
        this.move(MoverType.SELF, this.getDeltaMovement());

        Direction closestFace = findClosestNearbyBlockFace();
        if (closestFace != null) {
            if (this.myBlockState.is(Blocks.TNT)) {
                if (!this.level().isClientSide) {
                    PrimedTnt primedTnt = new PrimedTnt(this.level(), this.getX(), this.getY(), this.getZ(), null);
                    primedTnt.setFuse(5);
                    this.level().addFreshEntity(primedTnt);
                }
                this.discard();
                return;
            }

            double bounceSpeed = 0.4 + this.random.nextDouble() * 0.2;

            Vec3 curVel = this.getDeltaMovement();
            double vx = curVel.x;
            double vy = curVel.y;
            double vz = curVel.z;

            switch (closestFace) {
                case EAST:  vx = -bounceSpeed; break;
                case WEST:  vx = bounceSpeed; break;
                case UP:    vy = -bounceSpeed; break;
                case DOWN:  vy = bounceSpeed; break;
                case SOUTH: vz = -bounceSpeed; break;
                case NORTH: vz = bounceSpeed; break;
            }

            this.setDeltaMovement(new Vec3(vx, vy, vz));

            this.gravitized = true;
            this.setNoGravity(false);
            this.noPhysics = false;
            this.gravityStartTick = this.internalTick;
        }
    }

    private Direction findClosestNearbyBlockFace() {
        AABB box = this.getBoundingBox().inflate(0.5);
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

    private void handleGravityMovement() {
        if (this.gravityStartTick > 0 && this.internalTick > this.gravityStartTick + 100) {
            tryPlaceBlock();
            return;
        }

        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());

        boolean aboveCooldownActive = (this.aboveHitTick > 0 && this.internalTick - this.aboveHitTick < 40);

        if (!aboveCooldownActive) {
            AABB box = this.getBoundingBox();

            if (checkBelowForSolidify(box)) return;
            if (checkSideForSolidify(box)) return;
        }

        checkAboveHit();

        if (this.onGround() || this.horizontalCollision) {
            tryPlaceBlock();
            return;
        }

        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
    }

    private boolean checkBelowForSolidify(AABB box) {
        int minX = Mth.floor(box.minX - 0.5);
        int maxX = Mth.ceil(box.maxX + 0.5);
        int minZ = Mth.floor(box.minZ - 0.5);
        int maxZ = Mth.ceil(box.maxZ + 0.5);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = Mth.floor(box.minY); y >= Mth.floor(box.minY) - 1; y--) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState state = this.level().getBlockState(bp);
                    if (state.isAir()) continue;

                    double dist = box.minY - (bp.getY() + 1);
                    if (dist >= 0 && dist <= 0.4) {
                        tryPlaceBlock();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkSideForSolidify(AABB box) {
        double threshold = 0.05;

        int minX = Mth.floor(box.minX - 1);
        int maxX = Mth.ceil(box.maxX + 1);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ - 1);
        int maxZ = Mth.ceil(box.maxZ + 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState state = this.level().getBlockState(bp);
                    if (state.isAir()) continue;

                    double distEast = bp.getX() - box.maxX;
                    double distWest = box.minX - (bp.getX() + 1);
                    double distSouth = bp.getZ() - box.maxZ;
                    double distNorth = box.minZ - (bp.getZ() + 1);

                    if ((distEast >= 0 && distEast <= threshold) ||
                        (distWest >= 0 && distWest <= threshold) ||
                        (distSouth >= 0 && distSouth <= threshold) ||
                        (distNorth >= 0 && distNorth <= threshold)) {
                        tryPlaceBlock();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkAboveHit() {
        AABB box = this.getBoundingBox();
        int minX = Mth.floor(box.minX - 0.5);
        int maxX = Mth.ceil(box.maxX + 0.5);
        int minZ = Mth.floor(box.minZ - 0.5);
        int maxZ = Mth.ceil(box.maxZ + 0.5);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = Mth.ceil(box.maxY); y <= Mth.ceil(box.maxY) + 1; y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState state = this.level().getBlockState(bp);
                    if (state.isAir()) continue;

                    double dist = box.maxY - bp.getY();
                    if (dist >= -0.05) {
                        this.aboveHitTick = this.internalTick;
                        Vec3 vel = this.getDeltaMovement();
                        if (vel.y > -0.01) {
                            this.setDeltaMovement(new Vec3(vel.x, -Math.abs(vel.y) - 0.1, vel.z));
                        }
                        return;
                    }
                }
            }
        }
    }

    private void tryPlaceBlock() {
        if (this.hasLanded) return;
        this.hasLanded = true;

        BlockPos blockpos = this.blockPosition();
        if (!this.level().isLoaded(blockpos)) {
            this.discard();
            return;
        }

        if (this.myBlockState.is(Blocks.TNT)) {
            if (!this.level().isClientSide) {
                PrimedTnt primedTnt = new PrimedTnt(this.level(), this.getX(), this.getY(), this.getZ(), null);
                primedTnt.setFuse(5);
                primedTnt.setDeltaMovement(Vec3.ZERO);
                this.level().addFreshEntity(primedTnt);
            }
            this.discard();
            return;
        }

        BlockState existing = this.level().getBlockState(blockpos);
        if (existing.isAir() || existing.canBeReplaced()) {
            if (this.level().setBlock(blockpos, this.myBlockState, 3)) {
                ((net.minecraft.server.level.ServerLevel) this.level())
                    .getChunkSource()
                    .chunkMap
                    .broadcast(this, new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(blockpos, this.level().getBlockState(blockpos)));
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("MyBlockState", NbtUtils.writeBlockState(this.myBlockState));
        compound.putInt("InternalTick", this.internalTick);
        compound.putLong("CreationGameTime", this.creationGameTime);
        compound.putDouble("DX", this.moveDirection.x);
        compound.putDouble("DY", this.moveDirection.y);
        compound.putDouble("DZ", this.moveDirection.z);
        compound.putDouble("Speed", this.speed);
        compound.putBoolean("Gravitized", this.gravitized);
        compound.putInt("GravityStartTick", this.gravityStartTick);
        compound.putInt("AboveHitTick", this.aboveHitTick);
        compound.putBoolean("HasLanded", this.hasLanded);
        if (this.myBlockData != null) {
            compound.put("MyTileEntityData", this.myBlockData);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.myBlockState = NbtUtils.readBlockState(this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), compound.getCompound("MyBlockState"));
        this.internalTick = compound.getInt("InternalTick");
        this.creationGameTime = compound.getLong("CreationGameTime");
        this.moveDirection = new Vec3(compound.getDouble("DX"), compound.getDouble("DY"), compound.getDouble("DZ"));
        this.speed = compound.getDouble("Speed");
        this.gravitized = compound.getBoolean("Gravitized");
        this.gravityStartTick = compound.getInt("GravityStartTick");
        this.aboveHitTick = compound.getInt("AboveHitTick");
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
