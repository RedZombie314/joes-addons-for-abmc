package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 玩家空壳：仅使用玩家模型渲染的实体，自带一个字符串实体数据 SkinTexture。
 * - 无任何 AI（不注册任何目标/移动目标），也不会自己移动。
 * - 默认 20 点生命值，可被击杀；由变形药水创造时，被杀死会连带杀死原生物。
 * - 皮肤由 SkinTexture 决定（客户端渲染时从网上拉取对应玩家皮肤）。
 */
public class PlayerShellEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE =
        SynchedEntityData.defineId(PlayerShellEntity.class, EntityDataSerializers.STRING);
    private static final String TAG_SKIN = "SkinTexture";
    private static final String TAG_ORIGIN_NBT = "TransmutationOrigin";
    private static final String TAG_ORIGIN_PLAYER = "OriginPlayerUuid";
    private static final String TAG_ORIGIN_KILLER = "OriginKillerUuid";
    private static final String TAG_REMAINING_TICKS = "RemainingTicks";

    // 由变形药水创造时记录的原生物信息（仅服务端使用，随实体 NBT 持久化）
    private CompoundTag originNbt;
    private UUID originPlayerUuid;
    private UUID originKillerUuid;
    // 剩余变形时长（tick），用于倒计时结束时变回原生物；-1 表示非变形来源（如 /summon）
    private int remainingTicks = -1;

    public PlayerShellEntity(EntityType<? extends PlayerShellEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 允许右键互动穿透变形空壳（不阻挡玩家点击/交互）。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_TEXTURE, "");
    }

    public void setSkinTexture(String value) {
        this.entityData.set(DATA_SKIN_TEXTURE, value == null ? "" : value);
    }

    public String getSkinTexture() {
        return this.entityData.get(DATA_SKIN_TEXTURE);
    }

    /** 记录变形初始信息：原生物 NBT、玩家（若原生物是玩家）、击杀责任玩家。 */
    public void setTransmutationOrigin(CompoundTag nbt, UUID playerUuid, UUID killerUuid) {
        this.originNbt = nbt;
        this.originPlayerUuid = playerUuid;
        this.originKillerUuid = killerUuid;
    }

    public CompoundTag getOriginNbt() {
        return originNbt;
    }

    public UUID getOriginPlayerUuid() {
        return originPlayerUuid;
    }

    public UUID getOriginKillerUuid() {
        return originKillerUuid;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = remainingTicks;
    }

    @Override
    public Component getDisplayName() {
        String skin = getSkinTexture();
        if (skin != null && !skin.isBlank()) {
            return Component.literal(skin);
        }
        return super.getDisplayName();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_SKIN, getSkinTexture());
        if (originNbt != null) {
            tag.put(TAG_ORIGIN_NBT, originNbt);
        }
        if (originPlayerUuid != null) {
            tag.putUUID(TAG_ORIGIN_PLAYER, originPlayerUuid);
        }
        if (originKillerUuid != null) {
            tag.putUUID(TAG_ORIGIN_KILLER, originKillerUuid);
        }
        if (remainingTicks >= 0) {
            tag.putInt(TAG_REMAINING_TICKS, remainingTicks);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinTexture(tag.getString(TAG_SKIN));
        if (tag.contains(TAG_ORIGIN_NBT)) {
            originNbt = tag.getCompound(TAG_ORIGIN_NBT);
        }
        if (tag.contains(TAG_ORIGIN_PLAYER)) {
            originPlayerUuid = tag.getUUID(TAG_ORIGIN_PLAYER);
        }
        if (tag.contains(TAG_ORIGIN_KILLER)) {
            originKillerUuid = tag.getUUID(TAG_ORIGIN_KILLER);
        }
        this.remainingTicks = tag.contains(TAG_REMAINING_TICKS)
            ? tag.getInt(TAG_REMAINING_TICKS) : -1;
    }

    @Override
    protected void registerGoals() {
        // 无任何 AI 目标
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        // 玩家空壳不拾取任何物品（保持“不互动”设定），
        // 也避免触发 Mob.canReplaceCurrentItem -> getApproximateAttackDamageWithItem 的属性查询
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 玩家空壳可被攻击（默认 20 生命值），但不会与任何东西互动
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // 由变形药水创造的空壳被杀死时：连带杀死原生物（玩家/宠物/生物）
        if (this.level() instanceof ServerLevel serverLevel) {
            ModMain.handleLivingShellDeath(serverLevel, this, source);
        }
        super.die(source);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}