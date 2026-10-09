package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆裂骷髅：被附体空壳的远程召唤物之一（用户指定："召唤会爆炸的骷髅，爆炸威力为 2
 * ——可以理解为换皮的僵尸"）。
 * <p>
 * 因此它就是 {@link OrbExplosiveZombie 爆裂僵尸} 的翻版：<b>直线飞行、匀加速</b>，
 * 命中方块或生物后爆出<b>威力 2</b>，飞满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS}
 * 游戏刻（200 刻 = 10 秒）也一定爆炸。
 * <p>
 * 和普通骷髅的区别：<b>不会燃烧</b>、也不会被和平难度顺手清掉 —— 它是附体的一次性弹药，
 * 该由飞行控制决定什么时候消失。注意骷髅那条日光燃烧走的是
 * {@code AbstractSkeleton#aiStep} 里的 {@link net.minecraft.world.entity.Mob#isSunBurnTick()}，
 * 和僵尸的 {@code isSunSensitive()} 不是一个方法，所以要覆写的是它。
 */
public class OrbExplosiveSkeleton extends Skeleton {

    /** 爆炸威力：2（用户指定）。 */
    public static final float EXPLOSION_RADIUS = 2.0F;

    private final FlyingBombControl bomb = new FlyingBombControl();

    public OrbExplosiveSkeleton(EntityType<? extends OrbExplosiveSkeleton> type, Level level) {
        super(type, level);
        // 踢掉全部 AI goal（而不是 setNoAi(true)！）：
        // Mob#isEffectiveAi() = super.isEffectiveAi() && !isNoAi()，而 LivingEntity#travel() 的第一行
        // 就是 if (isControlledByLocalInstance()) —— 非玩家操控时它等于 isEffectiveAi()。
        // 所以 setNoAi(true) 会让 travel() 整个跳过：位移根本不会结算，弹体原地不动。
        // 清 goal 则只是"没人给它下指令"，travel 照常按 FlyingBombControl 设的 deltaMovement 推进。
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.setPersistenceRequired();
    }

    /** 切为飞行炸弹模式（见 {@link FlyingBombControl}）。 */
    public void launchAsBomb(Entity shooter, Vec3 direction, float speedPerSecond,
                             float accelerationPerSecondSquared) {
        this.bomb.launch(this, shooter, direction, speedPerSecond, accelerationPerSecondSquared);
    }

    public boolean isFlyingBomb() {
        return this.bomb.isFlying();
    }

    /** 白天也不会被点着（骷髅的日光燃烧判定，见 {@code AbstractSkeleton#aiStep}）。 */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    /** 不会燃烧：连火焰伤害免疫一起给了，免得飞过岩浆/火焰时被点燃。 */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /** 和平难度下也不该被顺手清掉：它是附体射出的弹药，不是自然刷出来的怪。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void tick() {
        // 设速必须在 super.tick() 之前：本刻位移是 travel() 用 deltaMovement 算的
        this.bomb.tickSpeed(this);
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.bomb.shouldDetonate(this)) {
            FlyingBombControl.explode(this, EXPLOSION_RADIUS);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.bomb.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.bomb.load(tag);
    }
}
