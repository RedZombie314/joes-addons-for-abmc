package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆裂僵尸：被附体空壳的远程召唤物之一。
 * <p>
 * 一只<b>直线飞行、匀加速</b>的僵尸，命中方块或生物后产生<b>威力 2</b> 的爆炸；
 * 飞行满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS} 游戏刻（200 刻 = 10 秒）
 * 也一定会爆炸 —— 这条是它和爆裂猫、苦力怕弹共用的特性。
 * <p>
 * 和普通僵尸的区别：<b>不会燃烧</b>（{@link #isSunSensitive()} 关掉，外加 {@link #fireImmune()}），
 * 也不会在和平难度被清掉（{@link #shouldDespawnInPeaceful()}）—— 它是附体的一次性弹药，
 * 该由飞行控制决定什么时候消失，而不是被日光或难度顺手删掉。
 */
public class OrbExplosiveZombie extends Zombie {

    /** 爆炸威力：2。 */
    public static final float EXPLOSION_RADIUS = 2.0F;

    private final FlyingBombControl bomb = new FlyingBombControl();

    public OrbExplosiveZombie(EntityType<? extends OrbExplosiveZombie> type, Level level) {
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

    /** 不会在白天被点着（僵尸的日光燃烧判定）。 */
    @Override
    protected boolean isSunSensitive() {
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
