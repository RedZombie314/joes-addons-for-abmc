package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * "飞行炸弹"共用的飞行控制：<b>直线飞行、匀加速、撞到方块或生物即引爆、飞满
 * {@link #FLY_MAX_LIFETIME_TICKS} 游戏刻必爆</b>。
 *
 * <p>被附体空壳的几种"召唤物/弹药"共用它：苦力怕弹（{@link TntStaffCreeper}）、
 * 爆裂猫（{@link OrbExplosiveCat}）、爆裂僵尸（{@link OrbExplosiveZombie}）。
 * 组合而不是继承：那三种东西的父类各不相同（{@code Creeper}/{@code Cat}/{@code Zombie}），
 * Java 单继承下没法共用一个基类，所以把状态与算法抽成这个小对象，各自持有一个。
 *
 * <h2>为什么设速必须在 super.tick() 之前</h2>
 * {@code Entity#getGravity()} 是 {@code isNoGravity() ? 0 : ...} 的 final 实现，
 * 所以 {@code setNoGravity(true)} 之后 {@code LivingEntity#travel} 里就是"重力减 0"；
 * 而这类生物没有任何会走路的 AI（目标/移动 goal 全被清掉，输入为 0），位移完全由
 * deltaMovement 决定 —— 于是每刻把 deltaMovement 设成"方向 × 当前速度"就得到一条精确的直线。
 * travel 是在 super.tick() 里跑的，所以设速只能放在它前面。
 */
public final class FlyingBombControl {

    /** 共享特性：飞行满这么多游戏刻后<b>一定</b>爆炸（200 刻 = 10 秒）。 */
    public static final int FLY_MAX_LIFETIME_TICKS = 200;

    /** 飞行速度上限（格/刻）：3 格/刻 = 60 格/秒，与箭矢速度一致。 */
    public static final double FLY_MAX_SPEED_PER_TICK = 3.0;

    /**
     * 起飞后多少刻才开始判定"撞到生物"。
     * <p>
     * 弹体出生在发射者的眼睛位置，与发射者本体、以及它头顶那颗隐形的幸运核心（乘客）都重叠，
     * 立刻判定的话会在枪口上自爆。加速度下两刻已离开 1.6 格以上，足够脱开。
     */
    private static final int FLY_ARM_TICKS = 2;

    private static final String TAG_FLYING = "joes_flying";
    private static final String TAG_SPEED = "joes_fly_speed";
    private static final String TAG_ACCEL = "joes_fly_accel";
    private static final String TAG_DIR_X = "joes_fly_dir_x";
    private static final String TAG_DIR_Y = "joes_fly_dir_y";
    private static final String TAG_DIR_Z = "joes_fly_dir_z";
    private static final String TAG_TICKS = "joes_fly_ticks";
    private static final String TAG_SHOOTER = "joes_fly_shooter";

    /** 是否处于飞行模式。 */
    private boolean flying;
    /** 当前速度（格/刻）。 */
    private double speed;
    /** 每刻加速度（格/刻²）。 */
    private double acceleration;
    /** 锁定方向（单位向量）。 */
    private Vec3 direction = Vec3.ZERO;
    /** 已经飞了多少刻。 */
    private int ticks;
    /** 发射者（不炸它，也不炸它身上的乘客）。 */
    @Nullable
    private UUID shooterUuid;

    /**
     * 进入飞行模式：关掉重力、锁定方向，此后每刻由 {@link #tickSpeed} 加速。
     * <p>
     * <b>注意第一个参数是"飞行体自己"（宿主），第二个才是发射者。</b>
     * 无重力与 hasImpulse 必须设在宿主身上 —— 这里曾经写成设在发射者身上，
     * 结果附体空壳每射一次爆炸召唤物就自己失去重力飘起来（而且真正的弹体反而有重力会下垂）。
     *
     * @param host                          飞行体自己（猫/僵尸/苦力怕）
     * @param shooter                       发射者（空壳），只用来记 UUID（不炸它和它的乘客）
     * @param speedPerSecond                出膛速度（格/秒）
     * @param accelerationPerSecondSquared  加速度（格/秒²）
     */
    public void launch(Entity host, @Nullable Entity shooter, Vec3 direction, float speedPerSecond,
                       float accelerationPerSecondSquared) {
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        this.flying = true;
        this.ticks = 0;
        this.direction = direction.normalize();
        this.speed = speedPerSecond / 20.0;
        // 格/秒² → 格/刻²（20 刻/秒，平方就是 400）
        this.acceleration = accelerationPerSecondSquared / 400.0;
        this.shooterUuid = shooter == null ? null : shooter.getUUID();
        host.setNoGravity(true);
        // 每刻速度都在变：标一下 hasImpulse，让追踪包按时发出，客户端不会把它插值成慢吞吞的直线
        host.hasImpulse = true;
        // 附体体系射出的爆炸弹：炸开时不动地形（哪怕 mobGriefing 为 true）——见 OrbExplosionPolicy。
        // 打在这里而不是各召唤物里，是因为苦力怕弹/爆裂猫/爆裂僵尸都从这一处起飞。
        OrbExplosionPolicy.markNoTerrainGrief(host);
    }

    public boolean isFlying() {
        return this.flying;
    }

    /** 已飞行刻数（未在飞行时为 0）。 */
    public int flightTicks() {
        return this.ticks;
    }

    /** 是否已经"解除保险"（可以判定命中生物了）。 */
    public boolean isArmed() {
        return this.ticks >= FLY_ARM_TICKS;
    }

    /** 每刻设速。<b>必须在宿主的 super.tick() 之前调用</b>（见类注释）。 */
    public void tickSpeed(Entity host) {
        if (!this.flying) {
            return;
        }
        // 每刻重申一次"无重力"：直线飞行完全建立在它之上，任何别的代码（或读档）把它改回来，
        // 弹道立刻退化成抛物线。同值 set 不会产生同步包（SynchedEntityData 只在值变化时标脏），
        // 所以这是免费的保险 —— 猫那种小型生物尤其经不起下垂，一格高度差就会撞地提前引爆。
        host.setNoGravity(true);
        this.speed = Math.min(this.speed + this.acceleration, FLY_MAX_SPEED_PER_TICK);
        host.setDeltaMovement(this.direction.scale(this.speed));
        host.hasImpulse = true;
        this.ticks++;
    }

    /**
     * 本刻是否该引爆：命中实心方块、命中生物、或飞满 {@link #FLY_MAX_LIFETIME_TICKS} 刻。
     * <p>
     * 方块的判定复用 TNT 权杖那套（{@code onGround()} + {@code touchingSolidBlock}），
     * 生物的判定要求已解除保险（见 {@link #FLY_ARM_TICKS}）。
     */
    public boolean shouldDetonate(Entity host) {
        if (!this.flying) {
            return false;
        }
        if (this.ticks >= FLY_MAX_LIFETIME_TICKS) {
            return true;
        }
        if (host.onGround() || TntStaffPrimedTnt.touchingSolidBlock(host)) {
            return true;
        }
        return this.isArmed() && this.touchingLivingEntity(host);
    }

    /** 撞到生物即引爆；发射者本人、它身上的乘客（附体时骑在头顶的核心），
     *  以及<b>其它爆炸性实体</b>（见 {@link #isExplosiveEntity}）除外。 */
    private boolean touchingLivingEntity(Entity host) {
        AABB hitBox = host.getBoundingBox().inflate(0.4);
        return !host.level().getEntitiesOfClass(LivingEntity.class, hitBox,
            e -> e != host && e.isAlive() && !e.isSpectator()
                && !isExplosiveEntity(e) && !this.isIgnored(host, e)).isEmpty();
    }

    /**
     * <b>爆炸性实体之间互不引爆</b>（用户指定）：一颗爆炸弹贴到另一颗爆炸物时，<b>不</b>引爆自己。
     *
     * <h2>为什么必须显式排除</h2>
     * 这几种弹体都是<b>生物</b>（苦力怕 / 猫 / 僵尸 / 史莱姆 / 骷髅），而 {@link #touchingLivingEntity}
     * 的判据就是"框里有没有别的生物"。多重射击下一发就是 3~7 颗，而且全都从<b>同一个眼位</b>出发，
     * 刚出膛那几刻彼此是重叠的 —— 不排除的话会在枪口上互相引爆，谁都飞不出去。
     *
     * <h2>为什么按"爆炸性"而不是按"我们的"来判</h2>
     * 用户的原话是"爆炸性实体之间相互接触时不会触发爆炸"，所以苦力怕（含权杖那只）、
     * TNT（含权杖那颗）、火球这些都算；普通生物（玩家、僵尸、骷髅……）照旧一碰就炸。
     */
    public static boolean isExplosiveEntity(Entity entity) {
        return entity instanceof Creeper               // 原版苦力怕 & TntStaffCreeper
            || entity instanceof OrbExplosiveCat
            || entity instanceof OrbExplosiveZombie
            || entity instanceof OrbExplosiveSlime
            || entity instanceof OrbExplosiveSkeleton
            || entity instanceof PrimedTnt             // 原版 TNT & TntStaffPrimedTnt
            || entity instanceof Fireball;             // 恶魂/烈焰人火球等
    }

    /** 不该炸到的目标：发射者，以及发射者身上的乘客。 */
    private boolean isIgnored(Entity host, Entity entity) {
        if (this.shooterUuid == null) {
            return false;
        }
        if (this.shooterUuid.equals(entity.getUUID())) {
            return true;
        }
        if (host.level() instanceof ServerLevel serverLevel) {
            Entity shooter = serverLevel.getEntity(this.shooterUuid);
            if (shooter != null && shooter.hasPassenger(entity)) {
                return true;
            }
        }
        return false;
    }

    /** 飞行状态要进存档：NoGravity 是原版自己存的，只存一半的话读档后会变成悬在空中不动的怪物。 */
    public void save(CompoundTag tag) {
        if (!this.flying) {
            return;
        }
        tag.putBoolean(TAG_FLYING, true);
        tag.putDouble(TAG_SPEED, this.speed);
        tag.putDouble(TAG_ACCEL, this.acceleration);
        tag.putDouble(TAG_DIR_X, this.direction.x);
        tag.putDouble(TAG_DIR_Y, this.direction.y);
        tag.putDouble(TAG_DIR_Z, this.direction.z);
        tag.putInt(TAG_TICKS, this.ticks);
        if (this.shooterUuid != null) {
            tag.putUUID(TAG_SHOOTER, this.shooterUuid);
        }
    }

    public void load(CompoundTag tag) {
        if (!tag.getBoolean(TAG_FLYING)) {
            return;
        }
        this.flying = true;
        this.speed = tag.getDouble(TAG_SPEED);
        this.acceleration = tag.getDouble(TAG_ACCEL);
        this.direction = new Vec3(tag.getDouble(TAG_DIR_X),
            tag.getDouble(TAG_DIR_Y), tag.getDouble(TAG_DIR_Z));
        this.ticks = tag.getInt(TAG_TICKS);
        this.shooterUuid = tag.hasUUID(TAG_SHOOTER) ? tag.getUUID(TAG_SHOOTER) : null;
    }

    /**
     * 共用的爆炸结算：威力由调用方给，炸完自己消失。
     * <p>
     * 这里是 {@link Level.ExplosionInteraction#MOB}（和 TNT 权杖那只苦力怕一致），
     * 但<b>不会破坏地形</b>：附体体系射出的爆炸性实体（苦力怕弹/爆裂猫/爆裂僵尸）在
     * {@link #launch} 时就打过标记，{@link OrbExplosionPolicy} 会在爆炸结算前清空方块破坏清单 ——
     * 即使 {@code mobGriefing} 为 true 也不动地形。实体伤害、击退、音效、粒子都不受影响。
     * <p>
     * 玩家自己用 TNT 权杖扔出去的那只苦力怕没有这个标记（也不走这里），照旧按原版规则破坏地形。
     */
    public static void explode(Entity host, float radius) {
        host.level().explode(host, host.getX(), host.getY(), host.getZ(), radius, false,
            Level.ExplosionInteraction.MOB);
        host.discard();
    }
}
