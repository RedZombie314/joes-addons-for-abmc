package cn.autoforged.joes_addons_for_abmc.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * 滴水石权杖召唤的“群组”单块下落方块实体。
 *
 * 行为：
 * 1. 整群（柱形，横截面 1×1）以 15±1 格/秒的初速度向上射出，vertical direction 均为 up
 *    （通过 initFromState 传入 thick/vertical_direction=up 的滴水石锥方块状态）。
 * 2. 群组的 base（最底部，columnIndex=0）作为领袖负责物理；其余成员跟随领袖位置。
 * 3. 无重力、无视周围方块（noPhysics=true），仅向上运动。
 * 4. 当 base 即将离开地表（下方方块不再为固体）时，整个群组立即回落到地表位置固化。
 * 5. 飞行中任意一块接触生物时：造成 |下落方块竖向速度 - 生物竖向速度|*5 的动能伤害
 *    （flyIntoWall），并把生物赋予“上升速度-0.5”的竖向速度。
 */
public class DripstoneFallingBlockEntity extends FallingBlockEntity {

    private BlockState myBlockState = Blocks.STONE.defaultBlockState();

    /** 群组唯一编号（同一根石柱的所有块共享） */
    public int groupId = -1;
    /** 0 = base（最底部），1 = frustum，2 = middle，3 = tip */
    public int columnIndex = 0;
    /** 领袖实体 id（base 为自己） */
    public int leaderId = -1;
    /** 上升速度（格/秒） */
    public double launchSpeedBlkPerSec = 15.0;
    /** 固化锚点（右键的方块位置） */
    public long anchorX = 0;
    public long anchorY = 0;
    public long anchorZ = 0;
    /** 召唤者实体 id（用于免伤与击杀归属） */
    public int summonerId = -1;
    /** 群组自基座向上“出土”的高度（格），等于群组成员数 */
    public int riseHeight = 4;
    /** 当前竖向速度（格/刻），恒定向上“出土” */
    public double vy = 0.0;
    /** 固化后自毁倒计时（刻）。固化后由领袖计数，到 0 时摧毁所有方块并移除自身 */
    public int lifeTicks = 0;

    /** 客户端同步的竖向速度 */
    private static final EntityDataAccessor<Float> DATA_VY =
        SynchedEntityData.defineId(DripstoneFallingBlockEntity.class, EntityDataSerializers.FLOAT);
    /** 客户端同步的固化标记（固化后领袖降为不可见的倒计时器） */
    private static final EntityDataAccessor<Boolean> DATA_SOLIDIFIED =
        SynchedEntityData.defineId(DripstoneFallingBlockEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean solidified = false;
    private final Set<Integer> recentlyHit = new IntOpenHashSet();

    public DripstoneFallingBlockEntity(EntityType<? extends DripstoneFallingBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.dropItem = false;
        this.disableDrop();
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VY, 0.0F);
        builder.define(DATA_SOLIDIFIED, false);
    }

    public void initFromState(BlockState state) {
        this.myBlockState = state;
        this.blocksBuilding = true;
    }

    /** 设置客户端同步的竖向速度（用于创建时通知客户端初始速度） */
    public void setClientVy(double vy) {
        this.entityData.set(DATA_VY, (float) vy);
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

        if (this.level().isClientSide) {
            // 固化后领袖成为不可见的倒计时器，停止移动
            if (!this.entityData.get(DATA_SOLIDIFIED)) {
                // 客户端依赖服务端同步的竖向速度 playback 出土/回落
                this.setDeltaMovement(0.0, this.entityData.get(DATA_VY), 0.0);
                this.move(MoverType.SELF, this.getDeltaMovement());
            } else {
                this.setDeltaMovement(0.0, 0.0, 0.0);
            }
            this.handlePortal();
            return;
        }

        if (this.getId() == this.leaderId) {
            this.tickLeader();
        } else {
            this.tickFollower();
        }
        this.handlePortal();
    }

    /** 领袖（base）：整柱恒定向上“出土”，base 到达方块上表面时整组固化 */
    private void tickLeader() {
        // 固化后：领袖暂停移动，仅作倒计时，3 秒（60 刻）后整组自毁
        if (this.solidified) {
            this.setDeltaMovement(0.0, 0.0, 0.0);
            if (--this.lifeTicks <= 0) {
                this.destroyGroup();
            }
            return;
        }

        // 恒定速度向上（无重力、不悬浮回落）
        this.vy = this.launchSpeedBlkPerSec / 20.0;
        this.setDeltaMovement(0.0, this.vy, 0.0);
        this.entityData.set(DATA_VY, (float) this.vy);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.damageCreatures();

        // base 下表面到达锚点顶面 → 整组立即固化
        double baseBottom = this.getBoundingBox().minY;
        double anchorTop = this.anchorY + 1.0;
        if (baseBottom >= anchorTop - 0.05) {
            this.solidifyGroup();
        }
    }

    /** 成员：跟随领袖位置（保持相对高度差），并同样造成接触伤害 */
    private void tickFollower() {
        Entity leader = this.level().getEntity(this.leaderId);
        if (leader == null || !leader.isAlive()) {
            this.discard();
            return;
        }
        this.vy = leader.getDeltaMovement().y;
        this.setPos(leader.getX(), leader.getY() + this.columnIndex, leader.getZ());
        this.setDeltaMovement(0.0, this.vy, 0.0);
        this.entityData.set(DATA_VY, (float) this.vy);
        this.damageCreatures();
    }

    /** 接触生物的动能伤害 + 上抛 |
     *  伤害 = |下落方块竖向速度 - 生物竖向速度| * 5（格/秒）
     *  上抛 = 上升速度 - 0.5（格/秒）
     *  召唤者免疫本次伤害；若生物因此死亡，击杀归属记为召唤者。 */
    private void damageCreatures() {
        if (this.recentlyHit.isEmpty()
            && this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox()).isEmpty()) {
            return;
        }
        Entity summoner = this.level().getEntity(this.summonerId);
        DamageSource source;
        if (summoner instanceof LivingEntity livingSummoner) {
            source = new DamageSource(this.level().damageSources().flyIntoWall().typeHolder(), livingSummoner);
        } else {
            source = this.level().damageSources().flyIntoWall();
        }
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox())) {
            if (target.isDeadOrDying() || this.recentlyHit.contains(target.getId())
                || this.summonerId == target.getId()) {
                continue;
            }
            this.recentlyHit.add(target.getId());

            double fallingVy = this.launchSpeedBlkPerSec;
            double creatureVy = target.getDeltaMovement().y * 20.0;
            double diff = Math.abs(fallingVy - creatureVy);
            float damage = (float) (diff * 5.0);
            target.hurt(source, damage);

            double newVy = (fallingVy - 0.5) / 20.0;
            target.setDeltaMovement(target.getDeltaMovement().x, newVy, target.getDeltaMovement().z);
        }
    }

    /** 整组立刻固化：将每块按锚点自下而上放置为滴水石锥方块，并移除所有下落方块实体 */
    private void solidifyGroup() {
        if (this.solidified) return;
        this.solidified = true;

        ServerLevel serverLevel = (ServerLevel) this.level();
        BlockPos anchor = new BlockPos((int) this.anchorX, (int) this.anchorY, (int) this.anchorZ);
        AABB searchBox = new AABB(anchor).inflate(16.0);

        for (DripstoneFallingBlockEntity member :
            serverLevel.getEntitiesOfClass(DripstoneFallingBlockEntity.class, searchBox, e -> e.groupId == this.groupId)) {
            BlockPos targetPos = anchor.above(member.columnIndex + 1);
            if (serverLevel.isLoaded(targetPos)) {
                BlockState existing = serverLevel.getBlockState(targetPos);
                if (existing.isAir() || existing.canBeReplaced()) {
                    serverLevel.setBlock(targetPos, member.myBlockState, 3);
                }
            }
            // 成员全部移除；领袖（自己）保留为不可见的倒计时器
            if (member != this) {
                member.discard();
            }
        }

        // 领袖转为不可见计时器，固化 3 秒（60 刻）后自毁
        this.lifeTicks = 60;
        this.setInvisible(true);
        this.entityData.set(DATA_SOLIDIFIED, true);

        // 召唤者上抛：石锥整柱钻出时，只要召唤者在锚点附近（≤3 格）就被赋予同样的
        // 竖向上升速度（无伤害）。因召唤者通常站在锚点旁而非正上方，单纯靠碰撞盒
        // 很难命中，故按距离判定以保证“击飞”稳定生效。
        Entity summoner = serverLevel.getEntity(this.summonerId);
        if (summoner instanceof LivingEntity livingSummoner && !livingSummoner.isDeadOrDying()) {
            double dx = livingSummoner.getX() - (anchor.getX() + 0.5);
            double dy = livingSummoner.getY() - (anchor.getY() + 1.0);
            double dz = livingSummoner.getZ() - (anchor.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= 9.0) {
                double newVy = (this.launchSpeedBlkPerSec - 0.5) / 20.0;
                livingSummoner.setDeltaMovement(
                    livingSummoner.getDeltaMovement().x, newVy, livingSummoner.getDeltaMovement().z);
            }
        }
    }

    /** 整组自毁：将群组对应位置的滴水石锥方块设为空气并播放方块破坏动画，随之移除自身 */
    private void destroyGroup() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        BlockPos anchor = new BlockPos((int) this.anchorX, (int) this.anchorY, (int) this.anchorZ);
        for (int i = 0; i < this.riseHeight; i++) {
            BlockPos targetPos = anchor.above(i + 1);
            if (serverLevel.isLoaded(targetPos)) {
                BlockState state = serverLevel.getBlockState(targetPos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.PointedDripstoneBlock) {
                    // 方块破坏事件（粒子 + 音效）
                    serverLevel.levelEvent(2001, targetPos, Block.getId(state));
                    serverLevel.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("MyBlockState", NbtUtils.writeBlockState(this.myBlockState));
        compound.putInt("GroupId", this.groupId);
        compound.putInt("ColumnIndex", this.columnIndex);
        compound.putInt("LeaderId", this.leaderId);
        compound.putDouble("LaunchSpeed", this.launchSpeedBlkPerSec);
        compound.putLong("AnchorX", this.anchorX);
        compound.putLong("AnchorY", this.anchorY);
        compound.putLong("AnchorZ", this.anchorZ);
        compound.putBoolean("Solidified", this.solidified);
        compound.putInt("SummonerId", this.summonerId);
        compound.putInt("RiseHeight", this.riseHeight);
        compound.putDouble("Vy", this.vy);
        compound.putInt("LifeTicks", this.lifeTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.myBlockState = NbtUtils.readBlockState(
            this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
            compound.getCompound("MyBlockState"));
        this.groupId = compound.getInt("GroupId");
        this.columnIndex = compound.getInt("ColumnIndex");
        this.leaderId = compound.getInt("LeaderId");
        this.launchSpeedBlkPerSec = compound.getDouble("LaunchSpeed");
        this.anchorX = compound.getLong("AnchorX");
        this.anchorY = compound.getLong("AnchorY");
        this.anchorZ = compound.getLong("AnchorZ");
        this.solidified = compound.getBoolean("Solidified");
        this.summonerId = compound.getInt("SummonerId");
        this.riseHeight = compound.getInt("RiseHeight");
        this.vy = compound.getDouble("Vy");
        this.lifeTicks = compound.getInt("LifeTicks");
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