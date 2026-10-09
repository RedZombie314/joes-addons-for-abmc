package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 幸运子事件（{@code entity/reflect_shield}）里的<b>反弹盾牌</b>：一个<b>正常大小</b>的盾牌物品展示实体，
 * 存活 {@link #LIFETIME_TICKS}（5 秒），期间<b>把撞进它体积里的弹射物弹回去</b>。
 *
 * <h3>外观</h3>
 * 就是 {@code minecraft:shield} 的物品展示实体，<b>不放大</b>（6.5.7 起去掉了原先的 10 倍变换）。
 * 物品直接写进物品展示实体的物品槽（{@code getSlot(0).set(...)}，{@code ItemDisplay} 只公开了这一个入口）——
 * 早先用的是 {@code load(NBT)}，但 {@code Entity#load} 在 NBT 里没有 {@code Pos}/{@code Motion} 时会把
 * 坐标与速度清成 {@code (0,0,0)}（{@code Entity.java:1829-1845}），摆位置必须写在它后面，是个容易踩的坑；
 * 走物品槽就没有这个问题。
 *
 * <h3>反弹</h3>
 * 每刻把体积（{@link #REFLECT_RADIUS} 格）内的 {@link Projectile} 扫一遍：
 * 以<b>盾牌中心指向弹射物</b>的方向为法线，只处理"正在往盾牌里钻"的那些（{@code v·n < 0}），
 * 用镜面反射公式 {@code v' = v - 2(v·n)n} 弹出去，再乘一点 {@link #REFLECT_BOOST} 让它飞得更精神。
 * 用"径向法线"而不是"盾牌朝向"是有意的：从背面/侧面来的弹射物用朝向法线会直接穿过去，
 * 看起来就像没生效；径向法线保证"从哪边来就从哪边弹回去"，任何角度都成立。
 */
public class ReflectingShieldDisplay extends Display.ItemDisplay {

    /** 存活时间：100 刻 = 5 秒（需求）。 */
    public static final int LIFETIME_TICKS = 100;

    /**
     * 反弹判定半径（格）。
     * <p>
     * 盾牌是<b>正常大小</b>（约 1 格见方），所以这里只放宽到 1.5 格 —— 判定体积 3×3×3，
     * 比盾牌本体略大一圈，箭擦着盾面过也会被弹开，但不会出现"离盾牌好几格就被弹飞"的怪现象。
     * （6.5.7 之前盾牌放大 10 倍，这里取的是 5.0。）
     */
    public static final double REFLECT_RADIUS = 1.5D;

    /** 反弹后的速度倍率（>1 表示弹回去更有劲）。 */
    private static final double REFLECT_BOOST = 1.15D;

    /** 速度小于这个平方值就当作"停住的弹射物"，不参与反弹。 */
    private static final double MIN_SPEED_SQR = 0.01D;

    /** 反弹音效的最小间隔（刻），免得一堆箭同时命中时声音叠成噪音。 */
    private static final int SOUND_COOLDOWN_TICKS = 6;

    /** 已经存在了多少刻。 */
    private int lifeTicks;

    /** 音效冷却。 */
    private int soundCooldown;

    public ReflectingShieldDisplay(EntityType<? extends ReflectingShieldDisplay> type, Level level) {
        super(type, level);
    }

    /** 摆好外观：把盾牌放进物品展示实体的物品槽（不缩放、不位移）。 */
    public void configure(ItemStack shield) {
        this.getSlot(0).set(shield);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.soundCooldown > 0) {
            this.soundCooldown--;
        }
        if (++this.lifeTicks > LIFETIME_TICKS) {
            this.discard();
            return;
        }
        this.reflectProjectiles();
    }

    /** 把钻进体积里的弹射物弹出去。 */
    private void reflectProjectiles() {
        AABB area = this.getBoundingBox().inflate(REFLECT_RADIUS);
        List<Projectile> projectiles = this.level().getEntitiesOfClass(Projectile.class, area);
        if (projectiles.isEmpty()) {
            return;
        }
        Vec3 center = this.position();
        boolean reflectedAny = false;
        for (Projectile projectile : projectiles) {
            if (projectile.isRemoved()) {
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < MIN_SPEED_SQR) {
                continue;
            }
            Vec3 toProjectile = projectile.position().subtract(center);
            if (toProjectile.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 normal = toProjectile.normalize();
            double dot = velocity.dot(normal);
            if (dot >= 0.0D) {
                continue; // 已经在往外飞了（或者只是从旁边擦过去），不打扰它
            }
            // 镜面反射：v' = v - 2(v·n)n
            Vec3 reflected = velocity.subtract(normal.scale(2.0D * dot)).scale(REFLECT_BOOST);
            projectile.setDeltaMovement(reflected);
            projectile.hasImpulse = true;
            // 让客户端也看到这次速度变化（否则要等下一次位置包才纠正，看起来像"箭穿过去了"）
            projectile.hurtMarked = true;
            reflectedAny = true;
        }
        if (reflectedAny && this.soundCooldown <= 0) {
            this.soundCooldown = SOUND_COOLDOWN_TICKS;
            if (this.level() instanceof ServerLevel level) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
