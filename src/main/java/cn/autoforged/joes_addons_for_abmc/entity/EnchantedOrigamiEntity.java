package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 附魔千纸鹤：一只在天空盘旋飞行的小纸鹤。
 * - 远程模式：向目标发射魔咒子弹；近战模式：贴近目标每 30 刻造成 1 点伤害。
 * - 无视重力、自研飞行（水平逼近 + 竖直收敛到目标高度）。
 * - 拥有收容标签（containerUuid），被收容它的实体视为"朋友"，永不索敌该实体。
 * - 客户端渲染使用简单空渲染器（或可替换为画一张纸片的 model）。
 */
public class EnchantedOrigamiEntity extends Mob implements ItemSupplier {

    /** 主动索敌的距离上限（格）。 */
    private static final double AGGRO_RANGE = 20.0;
    /** 现有目标的脱离距离（格）：距目标超过此值即放弃索敌。 */
    private static final double HURT_THRESHOLD = 40.0;
    /** 飞行移动速度（与僵尸移速一致 0.23）。 */
    private static final double FLIGHT_SPEED = 0.23;
    /** 水平停止距离（格）：距目标水平距离小于此值则不再横向移动。 */
    private static final double HORIZ_STOP = 1.0;
    /** 近战攻击距离（格）。 */
    private static final double MELEE_RANGE = 3.0;
    /** 近战“碰撞箱相贴”停止距离（格）：进入此范围内即停住，除非目标再次移动。 */
    private static final double MELEE_STOP_DIST = 0.5;

    private boolean melee = false;
    /** 收容它的人/实体的 UUID 字符串；"-1" 表示无收容者。 */
    private String containerUuid = "-1";
    /** 攻击/射击冷却计数。 */
    private int attackCooldown = 0;
    /** 远程悬停的随机偏移（以目标中心为准，xz 各 ±3 格内、y 上下 ±0.5 格）。 */
    private double hoverOffX = 0, hoverOffZ = 0, hoverOffY = 0;
    /** 悬停偏移重新随机的倒计时。 */
    private int hoverRecompute = 0;
    /** 剩余存活刻：自然死亡倒计时。默认 5 分钟 = 6000 刻（初次被召唤）。 */
    private int lifeTicks = 6000;

    @Nullable
    private LivingEntity target;

    public EnchantedOrigamiEntity(EntityType<? extends EnchantedOrigamiEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        // 诞生瞬间随机决定战斗模式：近战 20% / 远程 80%；调试模式恒为近战
        this.melee = ModConfig.DEBUG_MODE.get() || this.random.nextFloat() < 0.2F;
    }

    /** 属性：最大生命 10。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0);
    }

    // ===== 访问器 =====
    public boolean isMelee() {
        return this.melee;
    }

    public void setMelee(boolean melee) {
        this.melee = melee;
    }

    public String getContainerUuid() {
        return this.containerUuid;
    }

    public void setContainerUuid(String containerUuid) {
        this.containerUuid = (containerUuid == null || containerUuid.isEmpty()) ? "-1" : containerUuid;
    }

    /** 设置剩余存活刻（小于等于 0 时下一刻自然死亡）。 */
    public void setLifeTicks(int ticks) {
        this.lifeTicks = ticks;
    }

    /** 获取剩余存活刻。 */
    public int getLifeTicks() {
        return this.lifeTicks;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 每 4 游戏刻恢复 1 点血
        if (this.tickCount % 4 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
        // 自然死亡倒计时：服务端每刻递减，归零即死亡（会触发 dropAllDeathLoot 掉落一张纸）
        if (!this.level().isClientSide() && --this.lifeTicks <= 0) {
            this.kill();
            return;
        }
        this.acquireTarget();
        this.updateFlight();
        this.updateAttack();
    }

    // ===== 索敌 =====
    private void acquireTarget() {
        if (this.target != null) {
            // 目标玩家切换为创造/旁观时立即失去对其的仇恨
            if (this.target instanceof net.minecraft.world.entity.player.Player p
                    && (p.isCreative() || p.isSpectator())) {
                this.target = null;
            } else if (!this.target.isAlive() || this.distanceToSqr(this.target) > HURT_THRESHOLD * HURT_THRESHOLD) {
                this.target = null;
            } else {
                return;
            }
        }
        double best = AGGRO_RANGE * AGGRO_RANGE;
        LivingEntity found = null;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(AGGRO_RANGE), this::isValidTarget)) {
            double d = this.distanceToSqr(e);
            if (d < best) {
                best = d;
                found = e;
            }
        }
        this.target = found;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        // 不索敌调用无敌（Invulnerable=1b）的实体
        if (e.isInvulnerable()) return false;
        // 不索敌创造/旁观模式的玩家
        if (e instanceof net.minecraft.world.entity.player.Player p
                && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        // 豁免同类
        if (e instanceof EnchantedOrigamiEntity) return false;
        // 统豁免 唤魔者 / 幻术师
        if (e instanceof Evoker || e instanceof Illusioner) return false;
        // 永不索敌“收容它的实体”
        if (this.isContainerSet()) {
            UUID uuid = this.uuidFromContainer();
            Entity container = (this.level() instanceof ServerLevel sl && uuid != null) ? sl.getEntity(uuid) : null;
            if (container == e) return false;
        }
        return true;
    }

    /** 供 ThrownItemRenderer 使用：渲染为一张“纸”贴图（以后可替换自定义模型）。 */
    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.PAPER);
    }

    // ===== 伤害免疫 =====
    /** 免疫任何来自同类（任意附魔千纸鹤）的伤害（含其魔咒子弹、爆炸），也免疫火焰伤害。 */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 免疫来自任何同类（包括自己）的伤害
        if (source.getEntity() instanceof EnchantedOrigamiEntity) {
            return false;
        }
        // 免疫火焰伤害
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    private UUID uuidFromContainer() {
        try {
            return UUID.fromString(this.containerUuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isContainerSet() {
        return this.containerUuid != null && !this.containerUuid.isEmpty() && !"-1".equals(this.containerUuid);
    }

    // ===== 飞行 =====
    private void updateFlight() {
        if (this.target != null && this.target.isAlive()) {
            // 若索敌目标为凋灵：无论攻击模式都不往上飞，保持原地不动
            if (this.target instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
                this.setDeltaMovement(0, 0, 0);
                this.setNoGravity(true);
                return;
            }

            // 远程：以目标中心为基准缓慢随机游走——xz 偏移 ≤3 格、y 在“目标最高点+1.5”上下 ±0.5 格浮动
            double anchorX = this.target.getX();
            double anchorZ = this.target.getZ();
            double desiredY;
            if (this.melee) {
                desiredY = this.target.getEyeY();
            } else {
                if (--this.hoverRecompute <= 0) {
                    this.hoverRecompute = 15 + this.random.nextInt(15);
                    double ang = this.random.nextDouble() * 2.0 * Math.PI;
                    double r = this.random.nextDouble() * 3.0; // xz 随机偏移 0~3 格
                    this.hoverOffX = Math.cos(ang) * r;
                    this.hoverOffZ = Math.sin(ang) * r;
                    this.hoverOffY = (this.random.nextDouble() - 0.5) * 1.0; // y 上下浮动 ±0.5
                }
                anchorX += this.hoverOffX;
                anchorZ += this.hoverOffZ;
                desiredY = this.target.getBoundingBox().maxY + 1.5 + this.hoverOffY;
            }

            double dx = anchorX - this.getX();
            double dz = anchorZ - this.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);

            // 近战：一旦进入目标碰撞箱（相距 0.5 格以内）就停住不动，除非目标再次移动拉开距离
            if (this.melee
                    && this.getBoundingBox().inflate(MELEE_STOP_DIST)
                        .intersects(this.target.getBoundingBox())) {
                this.setDeltaMovement(0, 0, 0);
                this.setNoGravity(true);
                return;
            }

            // 水平方向逼近（近战近停时减速；远程保持恒定慢速以缓慢绕行）
            double hSpeed;
            if (this.melee) {
                hSpeed = horiz > HORIZ_STOP ? FLIGHT_SPEED : FLIGHT_SPEED * (horiz / HORIZ_STOP);
            } else {
                hSpeed = FLIGHT_SPEED;
            }
            if (horiz < 0.05) hSpeed = 0;
            double vx = horiz > 0.001 ? (dx / horiz) * hSpeed : 0;
            double vz = horiz > 0.001 ? (dz / horiz) * hSpeed : 0;

            // 竖直方向收敛到目标高度
            double vy = Mth.clamp(desiredY - this.getY(), -FLIGHT_SPEED, FLIGHT_SPEED);

            this.setDeltaMovement(vx, vy, vz);

            // 朝向目标旋转（简洁面包片旋转）
            if (horiz > 0.001) {
                this.setYRot((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
                this.yBodyRot = this.getYRot();
                this.setXRot((float) (Math.toDegrees(-Mth.atan2(vy, Math.max(horiz, 0.5)))));
            }
            this.setNoGravity(true);
        } else {
            // 无目标时缓慢落地（0.2 格/秒 = 每刻下落 0.01）。
            // 保留水平动量并轻微阻尼，使玩家/其它生物能推动它（否则动量每刻被清零会显得"推不动"）
            net.minecraft.world.phys.Vec3 mv = this.getDeltaMovement();
            this.setDeltaMovement(mv.x * 0.9, -0.01, mv.z * 0.9);
            this.setNoGravity(true);
        }
    }

    // ===== 攻击 =====
    private void updateAttack() {
        if (this.target == null || !this.target.isAlive()) return;
        if (this.target.distanceToSqr(this) > 16.0 * 16.0) return; // 只在较近时攻击
        this.attackCooldown--;
        if (this.attackCooldown > 0) return;

        if (this.melee) {
            this.attackCooldown = 30;
            if (this.target.distanceToSqr(this) < MELEE_RANGE * MELEE_RANGE) {
                this.target.hurt(this.level().damageSources().mobAttack(this), 1.0F);
            }
        } else {
            // 远程：冷却 30~40 刻
            this.attackCooldown = 30 + this.random.nextInt(11);
            this.fireBullet();
        }
    }

    private void fireBullet() {
        if (!(this.level() instanceof ServerLevel server)) return;
        EnchantmentBullet bullet = new EnchantmentBullet(this.level(), this,
            this.getX(), this.getEyeY(), this.getZ());
        double dx = this.target.getX() - this.getX();
        double dy = this.target.getEyeY() - this.getEyeY();
        double dz = this.target.getZ() - this.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) len = 1;
        bullet.shoot(dx / len, dy / len, dz / len, 1.2F, 0.0F);
        // TODO: 之后可在发射时播放发射音效（如 FIRE_CHARGE_USE / 振翅声）
        this.playSound(SoundEvents.BAT_TAKEOFF, 0.2F, 1.2F);
        server.addFreshEntity(bullet);
    }

    // ===== 死亡掉落：1 张纸 =====
    @Override
    protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource damageSource) {
        super.dropAllDeathLoot(level, damageSource);
        this.spawnAtLocation(new ItemStack(Items.PAPER), 0.1F);
    }

    // ===== NBT 持久化 =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("melee", this.melee);
        tag.putString("containerUuid", this.containerUuid);
        tag.putInt("lifeTicks", this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.melee = tag.getBoolean("melee");
        this.containerUuid = tag.getString("containerUuid");
        if (this.containerUuid.isEmpty()) this.containerUuid = "-1";
        this.lifeTicks = tag.getInt("lifeTicks");
    }

    // 视锥/显现距离保持原样即可，不需要额外覆盖
}