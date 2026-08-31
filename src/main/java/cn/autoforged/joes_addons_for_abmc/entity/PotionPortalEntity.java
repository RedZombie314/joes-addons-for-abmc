package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 随机传送药水的传送门实体：1×2×1 碰撞箱（宽1、高2、深1）。
 * 一次酿造生成一对门：入口门（entrance，具备传送能力的"第一个传送门"）与出口门（exit）。
 * 单向：只有入口门会把穿过的实体搬运到出口门前；出口门不具备传送能力。
 * 任一实体穿过入口门后，两扇门在 10 个游戏刻后关闭。
 */
public class PotionPortalEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_YAW =
        SynchedEntityData.defineId(PotionPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
        SynchedEntityData.defineId(PotionPortalEntity.class, EntityDataSerializers.FLOAT);

    // 服务端字段（无需同步到客户端）
    public int partnerId = -1;          // 配对门的实体 id
    public boolean entrance = false;    // 是否入口（具备传送能力）
    public int deadlineTicks = -1;      // 触发搬运后距关闭的剩余刻数，-1 表示尚未触发
    public int lifetime = 40;           // 开启 40 游戏刻内无实体通过则关闭（入口门计时）

    public PotionPortalEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    /** 创建一个随机传送门实体，pos 为其中心/贴面点，yaw/pitch 决定门朝向（法线方向）。
     * pitch≈±90 表示水平门（贴方块上/下表面）；其余为竖直门。 */
    public static PotionPortalEntity create(ServerLevel level, Vec3 pos, float yaw, float pitch, boolean isEntrance) {
        PotionPortalEntity entity = new PotionPortalEntity(ModEntities.POTION_PORTAL.get(), level);
        entity.setPos(pos);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.xo = pos.x;
        entity.yo = pos.y;
        entity.zo = pos.z;
        entity.xRotO = pitch;
        entity.yRotO = yaw;
        entity.setPortalYaw(yaw);
        entity.setPortalPitch(pitch);
        entity.entrance = isEntrance;
        return entity;
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = this.position().x;
        double y = this.position().y;
        double z = this.position().z;
        float p = this.getPortalPitch();
        if (Math.abs(p) > 45.0F) {
            // 水平门（贴方块上/下表面）：贴图朝上由底下向上延伸，朝下则由底下向下延伸
            if (p > 0.0F) {
                return new AABB(x - 0.5, y - 2.0, z - 0.5, x + 0.5, y, z + 0.5);
            }
            return new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 2.0, z + 0.5);
        }
        return new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 2.0, z + 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
    }

    public float getPortalYaw() {
        return entityData.get(DATA_YAW);
    }

    public void setPortalYaw(float yaw) {
        entityData.set(DATA_YAW, yaw);
    }

    public float getPortalPitch() {
        return entityData.get(DATA_PITCH);
    }

    public void setPortalPitch(float pitch) {
        entityData.set(DATA_PITCH, pitch);
    }

    /** 门朝向的法线：pitch=0 为竖直门（水平法线）；pitch≈±90 为水平门（法线朝上/朝下）。 */
    public Vec3 getPortalNormal() {
        float yawRad = (float) Math.toRadians(this.getPortalYaw());
        float pitchRad = (float) Math.toRadians(this.getPortalPitch());
        float cy = (float) Math.cos(yawRad);
        float sy = (float) Math.sin(yawRad);
        float cp = (float) Math.cos(pitchRad);
        float sp = (float) Math.sin(pitchRad);
        return new Vec3(-sy * cp, -sp, cy * cp);
    }

    /** 贴图平面内、垂直于法线的水平方向向量（用于求贴图薄盒）。 */
    public Vec3 getPortalRight() {
        float yawRad = (float) Math.toRadians(this.getPortalYaw());
        return new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
    }

    /**
     * 传送检测所用的“贴图薄盒”（非整个碰撞箱）：
     * 只有实体碰撞箱触碰到传送门贴图所在的薄矩形才触发传送。
     * 竖直门：1×2 贴图，薄片厚度 0.06 沿法线；水平门：1×1 贴图，薄片厚度 0.06 沿竖直。
     */
    public AABB getPortalFaceBox() {
        Vec3 c = this.position();
        float p = this.getPortalPitch();
        if (Math.abs(p) > 45.0F) {
            return new AABB(c.x - 0.5, c.y - 0.03, c.z - 0.5, c.x + 0.5, c.y + 0.03, c.z + 0.5);
        }
        Vec3 n = this.getPortalNormal();
        Vec3 r = this.getPortalRight();
        double ix = Math.abs(r.x) * 0.5 + Math.abs(n.x) * 0.03;
        double iz = Math.abs(r.z) * 0.5 + Math.abs(n.z) * 0.03;
        return new AABB(c.x - ix, c.y, c.z - iz, c.x + ix, c.y + 2.0, c.z + iz);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel sl)) return;

        // 关闭倒计时：若已触发（deadline>=0）则递减，到 0 时连同配对门一起关闭
        if (this.deadlineTicks >= 0) {
            this.deadlineTicks--;
            if (this.deadlineTicks <= 0) {
                closeSelfAndPartner(sl);
                return;
            }
        }

        // 只有入口门具备传送能力并做兜底寿命计时
        if (!this.entrance) return;

        if (this.lifetime-- <= 0) {
            closeSelfAndPartner(sl);
            return;
        }

        for (Entity traveller : sl.getEntities(this, this.getPortalFaceBox(),
            e -> !(e instanceof PotionPortalEntity) && e.isAlive())) {
            // 传送滞环：刚被搬出来的实体若尚未离开，不再立即搬运（单向，仅防同一实体抖回）
            if (traveller instanceof Projectile) {
                // 弹射物也允许被单向搬运一次
            }
            Entity partnerEnt = sl.getEntity(this.partnerId);
            if (!(partnerEnt instanceof PotionPortalEntity dest) || dest.isRemoved()) {
                // 配对门缺失：直接关闭
                closeSelfAndPartner(sl);
                return;
            }

            Vec3 destPos = dest.position()
                .add(0.0, 1.0 - traveller.getBbHeight() / 2.0, 0.0)
                .add(dest.getPortalNormal().scale(0.5));

            traveller.teleportTo(destPos.x, destPos.y, destPos.z);
            traveller.hurtMarked = true;

            // 速度合成：把沿入口门法线的速度分量取消并转加为向上的初速度；
            // 其余方向的水平/竖直速度保持矢量叠加（例：快速冲向墙面 → 冲向速度接入向上；下坠砸地 → 下坠速度变为向上）
            Vec3 velIn = traveller.getDeltaMovement();
            Vec3 n = this.getPortalNormal();
            double vn = velIn.dot(n);
            Vec3 lateral = velIn.subtract(n.scale(vn));
            double up = 0.3 + Math.abs(vn);
            Vec3 outVel = lateral.add(new Vec3(0.0, up, 0.0));
            traveller.setDeltaMovement(outVel);

            // 传送后播放末影人的传送音效（开启时不播放）
            sl.playSound(null, destPos.x, destPos.y, destPos.z,
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

            if (traveller instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.connection.teleport(destPos.x, destPos.y, destPos.z, sp.getYRot(), sp.getXRot());
            } else {
                net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket tp =
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(traveller);
                for (Player viewer : sl.getPlayers(p -> p.distanceToSqr(destPos) < 256.0 * 256.0)) {
                    ((net.minecraft.server.level.ServerPlayer) viewer).connection.send(tp);
                    ((net.minecraft.server.level.ServerPlayer) viewer).connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(traveller));
                }
            }

            // 检测到实体穿过：4 游戏刻后两扇门一起关闭
            this.deadlineTicks = 4;
            if (dest.deadlineTicks < 0) {
                dest.deadlineTicks = 4;
            }
            // 每刻最多搬运一个实体
            break;
        }
    }

    private void closeSelfAndPartner(ServerLevel sl) {
        if (this.partnerId > 0) {
            Entity partnerEnt = sl.getEntity(this.partnerId);
            if (partnerEnt != null && !partnerEnt.isRemoved()) {
                partnerEnt.discard();
            }
            this.partnerId = -1;
        }
        if (!this.isRemoved()) {
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("PortalYaw")) this.setPortalYaw(tag.getFloat("PortalYaw"));
        if (tag.contains("PortalPitch")) this.setPortalPitch(tag.getFloat("PortalPitch"));
        if (tag.contains("PartnerId")) this.partnerId = tag.getInt("PartnerId");
        if (tag.contains("Entrance")) this.entrance = tag.getBoolean("Entrance");
        if (tag.contains("Deadline")) this.deadlineTicks = tag.getInt("Deadline");
        if (tag.contains("Lifetime")) this.lifetime = tag.getInt("Lifetime");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putFloat("PortalYaw", this.getPortalYaw());
        tag.putFloat("PortalPitch", this.getPortalPitch());
        tag.putInt("PartnerId", this.partnerId);
        tag.putBoolean("Entrance", this.entrance);
        tag.putInt("Deadline", this.deadlineTicks);
        tag.putInt("Lifetime", this.lifetime);
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setPortalYaw(packet.getYRot());
        this.setPortalPitch(packet.getXRot());
    }
}