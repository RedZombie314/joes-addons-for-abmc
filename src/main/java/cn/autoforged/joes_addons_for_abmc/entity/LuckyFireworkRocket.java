package cn.autoforged.joes_addons_for_abmc.entity;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 幸运子事件（{@code entity/firework_volley}）里的<b>摇摆烟花火箭</b>：直着往上冲、仰角一直在
 * {@link #MIN_PITCH_DEGREES}~{@link #MAX_PITCH_DEGREES} 之间随机波动的烟花火箭。
 *
 * <h3>为什么要派生一个实体</h3>
 * 原版 {@code FireworkRocketEntity} 每刻会把水平速度乘 1.15、再给 y 加 0.04（"越飞越平"的加速曲线），
 * 于是<b>根本控制不住仰角</b>；而"仰角随机波动"要求每刻都能改一次航向，原版没有这个钩子。
 * 这里派生一个子类，在 {@code tick()} 里<b>每刻重设</b>一次速度：
 * <ul>
 *   <li>水平朝向 {@link #yaw} 在出生时定下（随机方向），之后不变；</li>
 *   <li>仰角 {@link #pitch} 每刻做一次随机游走（幅度 {@link #PITCH_JITTER_DEGREES}），并夹在 60~80 度之间；</li>
 *   <li>速度大小固定 {@link #SPEED}，所以轨迹是一条"抖着往上"的直线，不会自己拐平。</li>
 * </ul>
 *
 * <h3>两个原版私有字段的绕法</h3>
 * {@code FireworkRocketEntity} 的"烟花物品"与"直线飞行（shot at angle）"都在<b>私有</b>实体数据里，
 * 没有公开 setter（{@code setShotAtAngle} 根本不存在）。可行且不碰 mixin 的做法是走它自己的 NBT 读取：
 * {@code readAdditionalSaveData} 会读 {@code FireworksItem} 与 {@code ShotAtAngle}，于是
 * {@link #configure} 拼一个只含这两项（外加 {@code LifeTime}）的 {@link CompoundTag} 交给 {@code load}。
 * 设置 {@code ShotAtAngle = true} 之后，父类 tick 里那段水平加速就<b>整段跳过</b>，速度完全由我们说了算。
 */
public class LuckyFireworkRocket extends FireworkRocketEntity {

    /** 飞行速度（格/刻）：0.5 → 10 格/秒。 */
    public static final double SPEED = 0.5D;

    /** 仰角下限（度）。 */
    public static final double MIN_PITCH_DEGREES = 60.0D;

    /** 仰角上限（度）。 */
    public static final double MAX_PITCH_DEGREES = 80.0D;

    /** 飞行时长（刻）：40 刻 = 2 秒，之后正常炸开（约飞高 20 格）。 */
    public static final int FLIGHT_TICKS = 40;

    /** 每刻仰角随机游走的最大幅度（度）。 */
    private static final double PITCH_JITTER_DEGREES = 4.0D;

    /** 水平朝向（度，出生时定下）。 */
    private float yaw;

    /** 当前仰角（度）。 */
    private double pitch = 70.0D;

    public LuckyFireworkRocket(EntityType<? extends LuckyFireworkRocket> type, Level level) {
        super(type, level);
    }

    /**
     * 配置：烟花物品 + 水平朝向 + 初始仰角，并切到"直线飞行"模式。
     *
     * <p><b>调用顺序要求</b>：本方法内部走 {@code Entity#load}，而 {@code load} 在 NBT 里没有
     * {@code Pos}/{@code Motion} 时会把坐标与速度一并清成 {@code (0,0,0)}
     * （{@code Entity.java:1829-1845} 是先 {@code getList} 拿空表、再 {@code getDouble(0)} 取 0），
     * 所以<b>必须"先 configure、后 moveTo"</b>，否则火箭会从世界原点飞出去。
     *
     * @param firework 带 {@code minecraft:fireworks} 组件的烟花火箭物品（决定炸开的颜色/形状）
     * @param yaw      水平朝向（度）
     * @param pitch    初始仰角（度，会被夹进 60~80）
     */
    public void configure(ItemStack firework, float yaw, double pitch) {
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
        CompoundTag tag = new CompoundTag();
        tag.put("FireworksItem", firework.save(this.registryAccess()));
        tag.putBoolean("ShotAtAngle", true); // 关键：关掉父类的"越飞越平"加速
        tag.putInt("LifeTime", FLIGHT_TICKS);
        this.load(tag);
        this.applyMotion();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            // 仰角随机游走：每刻在 60~80 度之间抖一下（需求："仰角在 60~80 之间随机波动"）
            this.pitch = Mth.clamp(
                this.pitch + (this.random.nextDouble() - 0.5D) * 2.0D * PITCH_JITTER_DEGREES,
                MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
            this.applyMotion();
        }
        super.tick();
    }

    /** 按当前朝向/仰角写速度（父类 tick 在 ShotAtAngle 下不会再改它）。 */
    private void applyMotion() {
        Vec3 direction = Vec3.directionFromRotation((float) -this.pitch, this.yaw);
        this.setDeltaMovement(direction.scale(SPEED));
        this.hasImpulse = true;
    }

    /**
     * 随机颜色 + 随机形状的一个烟花爆点（冲天烟花火箭与烟花猪的消失散射共用）。
     *
     * <p>形状用 {@code FireworkExplosion.Shape.values()} 取，所以将来若有别的模组扩展了形状
     * （NeoForge 的可扩展枚举），这里也会自动用上；拖尾/闪烁也随机，所以每次炸开都不一样。
     */
    public static FireworkExplosion randomExplosion(RandomSource random) {
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        return new FireworkExplosion(
            shapes[random.nextInt(shapes.length)],
            IntList.of(DyeColor.byId(random.nextInt(16)).getFireworkColor()),
            IntList.of(DyeColor.byId(random.nextInt(16)).getFireworkColor()),
            random.nextBoolean(), random.nextBoolean());
    }
}
