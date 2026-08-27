package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PortalEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_YAW =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LINKED_PORTAL =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFESPAN =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FLIPPED =
        SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.BOOLEAN);

    public int linkedPortalId = -1;
    public int lifespan = 100;

    public PortalEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    public static PortalEntity create(ServerLevel level, Vec3 pos, float yaw, float pitch) {
        PortalEntity entity = new PortalEntity(ModEntities.PORTAL.get(), level);
        entity.setPos(pos);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.xo = pos.x;
        entity.yo = pos.y;
        entity.zo = pos.z;
        entity.xRotO = pitch;
        entity.yRotO = yaw;
        entity.setLinkedPortalId(-1);
        entity.lifespan = 400;
        entity.setPortalYaw(yaw);
        entity.setPortalPitch(pitch);
        return entity;
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = this.position().x;
        double y = this.position().y;
        double z = this.position().z;
        return new AABB(x - 1.0, y, z - 0.25, x + 1.0, y + 2.0, z + 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
        builder.define(DATA_LINKED_PORTAL, -1);
        builder.define(DATA_LIFESPAN, 400);
        builder.define(DATA_FLIPPED, false);
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

    public int getLinkedPortalId() {
        return entityData.get(DATA_LINKED_PORTAL);
    }

    public void setLinkedPortalId(int id) {
        entityData.set(DATA_LINKED_PORTAL, id);
    }

    public int getPortalLifespan() {
        return entityData.get(DATA_LIFESPAN);
    }

    public void setPortalLifespan(int ticks) {
        entityData.set(DATA_LIFESPAN, ticks);
    }

    public boolean isFlipped() {
        return entityData.get(DATA_FLIPPED);
    }

    public void setFlipped(boolean flipped) {
        entityData.set(DATA_FLIPPED, flipped);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        // 传送门实体不应拦截/偏转弹射物，箭等应直接穿过并由服务端传送逻辑处理
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("PortalYaw")) this.setPortalYaw(tag.getFloat("PortalYaw"));
        if (tag.contains("PortalPitch")) this.setPortalPitch(tag.getFloat("PortalPitch"));
        if (tag.contains("LinkedPortal")) this.setLinkedPortalId(tag.getInt("LinkedPortal"));
        if (tag.contains("Lifespan")) {
            this.lifespan = tag.getInt("Lifespan");
            this.setPortalLifespan(this.lifespan);
        }
        if (tag.contains("Flipped")) this.setFlipped(tag.getBoolean("Flipped"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("PortalYaw", this.getPortalYaw());
        tag.putFloat("PortalPitch", this.getPortalPitch());
        tag.putInt("LinkedPortal", this.getLinkedPortalId());
        tag.putInt("Lifespan", this.lifespan);
        tag.putBoolean("Flipped", this.isFlipped());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setYRot(packet.getYRot());
        this.setPortalYaw(packet.getYRot());
        this.setPortalPitch(packet.getXRot());
    }

    public Vec3 getPortalNormal() {
        float yawRad = (float) Math.toRadians(this.getPortalYaw());
        float pitchRad = (float) Math.toRadians(this.getPortalPitch());
        Vec3 normal = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        // 整对统一翻转：flipped 时取反整扇门的法线，从而反转出口侧
        return this.isFlipped() ? normal.scale(-1.0) : normal;
    }

    public Vec3 getPortalRight() {
        float yawRad = (float) Math.toRadians(this.getPortalYaw());
        return new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
    }

    public Vec3 getPortalUp() {
        float yawRad = (float) Math.toRadians(this.getPortalYaw());
        float pitchRad = (float) Math.toRadians(this.getPortalPitch());
        return new Vec3(
            Math.sin(yawRad) * Math.sin(pitchRad),
            Math.cos(pitchRad),
            -Math.cos(yawRad) * Math.sin(pitchRad)
        );
    }
}
