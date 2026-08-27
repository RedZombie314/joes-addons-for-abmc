package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;

/**
 * 玩家变形专用下落方块实体：
 * - 完全静止：不应用重力、不落地固化成方块、不因 600 tick 超时消失，
 *   位置由服务端 {@code makeTransmutedFollowPlayer} 每 tick 直接设置（贴地面跟随玩家）；
 * - 渲染沿用 FallingBlockRenderer，显示被变形的方块贴图。
 */
public class TransmutationFallingBlockEntity extends FallingBlockEntity {

    private BlockState myBlockState = Blocks.STONE.defaultBlockState();
    @Nullable
    public CompoundTag myBlockData;

    public TransmutationFallingBlockEntity(EntityType<? extends TransmutationFallingBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.dropItem = false;
        this.disableDrop();
        this.setNoGravity(true);
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
    }

    @Override
    public BlockState getBlockState() {
        return this.myBlockState;
    }

    @Override
    public void tick() {
        // 完全静止：不固化、不超时消失；位置由 makeTransmutedFollowPlayer 每 tick 直接设置
        if (this.myBlockState.isAir()) {
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("MyBlockState", NbtUtils.writeBlockState(this.myBlockState));
        if (this.myBlockData != null) {
            compound.put("MyTileEntityData", this.myBlockData);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.myBlockState = NbtUtils.readBlockState(
            this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
            compound.getCompound("MyBlockState"));
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
        this.setPos(packet.getX(), packet.getY(), packet.getZ());
        this.setStartPos(this.blockPosition());
    }
}
