package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆裂猫：被附体空壳的远程召唤物之一。
 * <p>
 * 一只<b>直线飞行、匀加速</b>的猫，命中方块或生物后产生<b>威力 1</b> 的爆炸；
 * 飞行满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS} 游戏刻（200 刻 = 10 秒）
 * 也一定会爆炸 —— 这条是它和爆裂僵尸、苦力怕弹共用的特性。
 * <p>
 * 飞行/引爆的判定全在 {@link FlyingBombControl} 里（那里也解释了两者的关系）；
 * 这里只负责"我是猫"的外观、爆炸威力，以及不吃自己这一套 AI。
 */
public class OrbExplosiveCat extends Cat {

    /** 爆炸威力：1（原版苦力怕是 3）。 */
    public static final float EXPLOSION_RADIUS = 1.0F;

    private final FlyingBombControl bomb = new FlyingBombControl();

    public OrbExplosiveCat(EntityType<? extends OrbExplosiveCat> type, Level level) {
        super(type, level);
        // 踢掉全部 AI goal（而不是 setNoAi(true)！）：
        // Mob#isEffectiveAi() = super.isEffectiveAi() && !isNoAi()，而 LivingEntity#travel() 的第一行
        // 就是 if (isControlledByLocalInstance()) —— 非玩家操控时它等于 isEffectiveAi()。
        // 所以 setNoAi(true) 会让 travel() 整个跳过：位移根本不会结算，弹体原地不动。
        // 清 goal 则只是"没人给它下指令"，travel 照常按 FlyingBombControl 设的 deltaMovement 推进。
        // （幸运骷髅骑士那边踩过同一个坑，注释里写着同样的话。）
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
