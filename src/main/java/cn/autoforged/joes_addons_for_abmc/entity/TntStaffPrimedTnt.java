package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import cn.autoforged.joes_addons_for_abmc.entity.StaffExplosionDamageCalculator;

/**
 * TNT 权杖丢出的特制 TNT：
 * - 初始引信 9999，投掷飞行途中不会自行爆炸；
 * - 一旦接触任何方块（地面/墙壁/天花板）立即将引信设为 1，下一游戏刻即爆炸；
 * - 可由投掷者左键发送引爆信号（{@link #quickFuse()}）在空中直接引爆；
 * - 爆炸不伤害投掷者本人（自定义爆炸伤害计算器）；
 * - 持久化 owner 的 UUID，使存档重载后仍能被投掷者左键引爆（原版仅保存 fuse/block_state，不保存 owner）。
 */
public class TntStaffPrimedTnt extends PrimedTnt {

    private static final int INITIAL_FUSE = 9999;
    private static final String TAG_JOES_OWNER = "joes_owner";

    @Nullable
    private UUID ownerUuid;

    public TntStaffPrimedTnt(EntityType<? extends TntStaffPrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    public TntStaffPrimedTnt(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        super(level, x, y, z, owner);
        this.ownerUuid = owner != null ? owner.getUUID() : null;
        this.setFuse(INITIAL_FUSE);
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.ownerUuid != null) compound.putUUID(TAG_JOES_OWNER, this.ownerUuid);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID(TAG_JOES_OWNER)) this.ownerUuid = compound.getUUID(TAG_JOES_OWNER);
    }

    /** 投掷者左键引爆：将引信设为 1，服务端于下一游戏刻爆炸。 */
    public void quickFuse() {
        if (this.level().isClientSide) return;
        if (this.getFuse() > 1) this.setFuse(1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        // 接触实心方块（地面/墙壁/天花板）后立即缩短引信 -> 下一游戏刻爆炸。
        // 注意：只判断方块接触，不使用 horizontalCollision/verticalCollision（它们在被玩家等实体
        // 阻挡时也会置位，会导致 TNT/苦力怕在投掷者身边瞬爆而看不见弹体）。
        if (this.getFuse() > 1 && (this.onGround() || touchingSolidBlock(this))) {
            this.setFuse(1);
        }
    }

    /**
     * 判断实体当前是否已接触实心方块（而非实体）。TNT 与苦力怕共用此判定。
     * 将实体包围盒略微外扩后，若任一相交的方块为实心方块即视为接触。
     */
    static boolean touchingSolidBlock(Entity entity) {
        Level level = entity.level();
        AABB box = entity.getBoundingBox().inflate(0.05);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void explode() {
        LivingEntity owner = this.getOwner();
        ExplosionDamageCalculator damageCalculator = new StaffExplosionDamageCalculator(owner);
        this.level().explode(
            this,
            Explosion.getDefaultDamageSource(this.level(), this),
            damageCalculator,
            this.getX(),
            this.getY(0.0625),
            this.getZ(),
            4.0F,
            false,
            Level.ExplosionInteraction.TNT);
    }
}