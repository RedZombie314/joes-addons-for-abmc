package cn.autoforged.joes_addons_for_abmc.entity;

import java.lang.reflect.Field;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

import cn.autoforged.joes_addons_for_abmc.damage.ModDamageTypes;

/**
 * TNT 权杖 1% 概率丢出的特制苦力怕：
 * - ignited=true（闪烁充能外观），自设引信 9999，不会自行爆炸；
 * - ExplosionRadius 在 2~6 随机取值，Powered 在 true/false 随机取值；
 * - 接触任意方块或投掷者左键引爆（fuse 设为 1）后立即爆炸；
 * - 免疫任何伤害（无法被玩家/环境杀死）；
 * - 与 TNT 相反，其爆炸会对玩家（包括投掷者本人）造成伤害；
 * - 持久化 owner、fuse、爆炸半径，重载后行为保持一致。
 *
 * 实现要点：苦力怕完全点燃后原版会在 30 刻内自爆。这里覆写 tick()，每次调用 super.tick()
 * （保证重力/移动等基础逻辑）后，通过反射把私有 swell 压低到 maxSwell 之下并做脉冲闪烁，
 * 从而“一直点燃却不自爆”，爆炸时机完全由本类的 fuse 控制。
 */
public class TntStaffCreeper extends Creeper {

    private static final int INITIAL_FUSE = 9999;
    private static final String TAG_JOES_OWNER = "joes_owner";
    private static final String TAG_JOES_FUSE = "joes_fuse";
    private static final String TAG_JOES_RADIUS = "joes_radius";

    // 苦力怕的 swell / maxSwell / DATA_IS_POWERED 均为私有，需反射访问。
    private static final Field SWELL_FIELD;
    private static final Field MAX_SWELL_FIELD;
    private static final Field DATA_IS_POWERED_FIELD;

    static {
        Field swell = null;
        Field maxSwell = null;
        Field powered = null;
        try {
            swell = Creeper.class.getDeclaredField("swell");
            swell.setAccessible(true);
            maxSwell = Creeper.class.getDeclaredField("maxSwell");
            maxSwell.setAccessible(true);
            powered = Creeper.class.getDeclaredField("DATA_IS_POWERED");
            powered.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("TntStaffCreeper 反射字段失败", e);
        }
        SWELL_FIELD = swell;
        MAX_SWELL_FIELD = maxSwell;
        DATA_IS_POWERED_FIELD = powered;
    }

    private int fuse = INITIAL_FUSE;
    private int explosionRadius = 3;
    private boolean powered = false;
    @Nullable
    private UUID ownerUuid;

    public TntStaffCreeper(EntityType<? extends TntStaffCreeper> entityType, Level level) {
        super(entityType, level);
        // 作为一种被投掷的炸弹，移除普通苦力怕的移动/AI 目标行为，避免其四处游荡
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
    }

    public void setExplosionRadius(int radius) {
        this.explosionRadius = radius;
    }

    public void setPoweredState(boolean powered) {
        this.powered = powered;
        try {
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Boolean> accessor = (EntityDataAccessor<Boolean>) DATA_IS_POWERED_FIELD.get(null);
            this.entityData.set(accessor, powered);
        } catch (IllegalAccessException ignored) {
        }
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.ownerUuid != null) compound.putUUID(TAG_JOES_OWNER, this.ownerUuid);
        compound.putInt(TAG_JOES_FUSE, this.fuse);
        compound.putInt(TAG_JOES_RADIUS, this.explosionRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID(TAG_JOES_OWNER)) this.ownerUuid = compound.getUUID(TAG_JOES_OWNER);
        if (compound.contains(TAG_JOES_FUSE, 3)) this.fuse = compound.getInt(TAG_JOES_FUSE);
        if (compound.contains(TAG_JOES_RADIUS, 3)) this.explosionRadius = compound.getInt(TAG_JOES_RADIUS);
    }

    /** 免疫任何伤害：任何伤害源都无法对本品造成伤害或使其死亡。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** 投掷者左键引爆：将 fuse 设为 1，服务端于下一游戏刻爆炸。 */
    public void quickFuse() {
        if (this.level().isClientSide) return;
        if (this.fuse > 1) this.fuse = 1;
    }

    @Override
    public void tick() {
        // 完整的基础移动/重力逻辑（包括 Creeper.tick 中原有的 swell 充能判定）
        super.tick();

        if (this.level().isClientSide) return;

        // 每次 tick 后把私有 swell 压回安全区间并在下方做脉冲闪烁，
        // 使其一直保持“点燃”外观却不触发原版 30 刻自然爆炸。
        try {
            int maxSwell = (Integer) MAX_SWELL_FIELD.get(this);
            int safeMax = Math.max(1, maxSwell - 2);
            int pulse = (this.tickCount * 5) % safeMax;
            SWELL_FIELD.setInt(this, pulse);
            this.setSwellDir(1);
        } catch (IllegalAccessException ignored) {
        }

        if (this.fuse > 1) {
            // 只判定实心方块接触（复用 TNT 的判定），避免与投掷者等实体碰撞时瞬爆
            if (this.onGround() || TntStaffPrimedTnt.touchingSolidBlock(this)) {
                this.fuse = 1;
            } else {
                this.fuse--;
            }
        }
        if (this.fuse <= 1) {
            this.explodeNow();
        }
    }

    private void explodeNow() {
        if (this.level().isClientSide) return;
        // 与 TNT 相反：不保护任何玩家，使用默认爆炸伤害（包括投掷者本人）
        float multiplier = this.isPowered() ? 2.0F : 1.0F;
        this.dead = true;
        // 使用自定义伤害类型：死亡事件据此识别“被特制苦力怕炸死”，并播报专属死亡信息。
        // 该类型的 death.attack 文案在 lang 中为空，可吞掉原版死亡播报，避免双重播报。
        DamageSource source = this.level().damageSources()
            .source(ModDamageTypes.JOES_TNT_STAFF_CREEPER.getKey(), this, this);
        this.level().explode(
            this,
            source,
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            (float) this.explosionRadius * multiplier,
            false,
            Level.ExplosionInteraction.MOB);
        this.discard();
    }
}