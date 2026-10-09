package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆裂史莱姆：被附体空壳的远程召唤物之一（用户指定："召唤会爆炸的史莱姆，爆炸威力为 1
 * ——可以理解为换皮的猫"）。
 * <p>
 * 因此它就是 {@link OrbExplosiveCat 爆裂猫} 的翻版：<b>直线飞行、匀加速</b>，
 * 命中方块或生物后爆出<b>威力 1</b>，飞满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS}
 * 游戏刻（200 刻 = 10 秒）也一定爆炸。飞行/引爆的判定全在 {@link FlyingBombControl} 里，
 * 这里只负责"我是史莱姆"的外观、爆炸威力，以及不吃自己这一套 AI。
 * <p>
 * 与爆裂猫/僵尸的唯一差别是<b>父类</b>：{@link Slime} 的尺寸由同步字段 {@code ID_SIZE} 决定
 * （默认 1 = 最小号），所以构造时显式调到 {@link #SIZE}（2 = 中号）——
 * 最小号只有 0.5 格，飞起来几乎看不见。
 */
public class OrbExplosiveSlime extends Slime {

    /** 爆炸威力：1（用户指定；原版苦力怕是 3）。 */
    public static final float EXPLOSION_RADIUS = 1.0F;

    /** 体型尺寸：2 = 中号史莱姆（碰撞箱 1.04 格，看得清）。 */
    private static final int SIZE = 2;

    private final FlyingBombControl bomb = new FlyingBombControl();

    public OrbExplosiveSlime(EntityType<? extends OrbExplosiveSlime> type, Level level) {
        super(type, level);
        // 尺寸：setSize 同时会重算碰撞箱与最大生命（2×2 = 4 点）；它是弹药，血量无所谓
        this.setSize(SIZE, true);
        // 踢掉全部 AI goal（而不是 setNoAi(true)！）：
        // Mob#isEffectiveAi() = super.isEffectiveAi() && !isNoAi()，而 LivingEntity#travel() 的第一行
        // 就是 if (isControlledByLocalInstance()) —— 非玩家操控时它等于 isEffectiveAi()。
        // 所以 setNoAi(true) 会让 travel() 整个跳过：位移根本不会结算，弹体原地不动。
        // 清 goal 则只是"没人给它下指令"，travel 照常按 FlyingBombControl 设的 deltaMovement 推进。
        // 对史莱姆还多一层意义：它的移动完全由 SlimeMoveControl 驱动，
        // 没有 goal 去启动那套移动控制，deltaMovement 就不会被它改写。
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

    /**
     * 和平难度下也不该被顺手清掉：它是附体射出的弹药，不是自然刷出来的怪
     * （原版史莱姆的实现在和平难度会被整体清除，见 {@code Slime#shouldDespawnInPeaceful}）。
     */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * 关掉原版史莱姆的"死亡分裂"。
     * <p>
     * {@code Slime#remove} 在"尺寸 &gt; 1 且正在死亡"时会原地生成一群小史莱姆 —— 那对一只
     * 自然生成的史莱姆是对的，但对"附体射出来的弹药"就是漏怪：爆炸/被击杀都会多出一批
     * 普通的敌对史莱姆（它们身上没有任何附体标记，也永远不会被清场）。
     * 这里把尺寸先压到 1 就等于关掉分裂（判定条件是 {@code getSize() > 1}），
     * 不影响任何外观 —— 反正这一刻它已经在被移除了。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.getSize() > 1) {
            this.setSize(1, false);
        }
        super.remove(reason);
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
