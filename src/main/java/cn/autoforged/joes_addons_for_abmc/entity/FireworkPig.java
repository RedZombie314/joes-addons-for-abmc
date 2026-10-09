package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 幸运子事件（{@code entity/firework_pig}）里的<b>烟花猪</b>：一只"屁股喷烟花、自己往前飞"的猪。
 *
 * <h3>行为</h3>
 * <ul>
 *   <li><b>速度</b>：{@link #SPEED_PER_TICK} = 1.0 格/刻 = <b>20 格/秒</b>，方向恒为<b>自身的朝向</b>
 *       （水平方向，朝上/朝下不参与），由 {@code travel} 每刻直接位移，<b>不吃重力、不吃 AI</b>；</li>
 *   <li><b>尾部粒子</b>：每刻从尾巴（朝向反方向 0.6 格、高度半格）向后喷 {@code minecraft:firework} 粒子；</li>
 *   <li><b>消失条件</b>：<b>撞到方块</b>（{@code horizontalCollision} / {@code verticalCollision}）
 *       或飞满 {@link #MAX_FLIGHT_TICKS}（5 秒）；</li>
 *   <li><b>消失点</b>：散射<b>大量</b>烟花粒子（见 {@link #spawnVanishBurst()}）。</li>
 * </ul>
 *
 * <h3>消失点的烟花为什么改成"客户端本地生成"</h3>
 * 之前这里用的是服务端的 {@code level.sendParticles(...)}，<b>看起来写了、实际基本看不到</b>：
 * 那个重载只把粒子包发给<b>消失点 32 格内</b>的玩家（{@code ServerLevel#sendParticles(player,...)} 里的距离闸），
 * 而这只猪以 20 格/秒飞 5 秒 = <b>100 格</b>，消失时玩家早就在 32 格之外了 ——
 * 于是粒子包<b>根本没发给任何人</b>，表现就是"猪飞走了，没有烟花"。
 *
 * <p>现在照抄原版烟花火箭的做法：服务端只广播一个<b>实体事件</b>
 * （{@link #EVENT_VANISH}，走的是"所有正在追踪该实体的玩家"，也就是 10 个区块 = 160 格），
 * 客户端收到后<b>在本地</b>放烟花（原版 {@code createFireworks} 的拖尾烟花 + 一大批火花粒子 + 爆炸音效）。
 * 尾部粒子同理改成客户端本地生成：不占网络，猪只要看得见就一直有尾焰。
 *
 * <p>渲染复用原版 {@code PigRenderer}。
 */
public class FireworkPig extends Pig {

    /** 前进速度：1.0 格/刻 = 20 格/秒（需求）。 */
    public static final double SPEED_PER_TICK = 1.0D;

    /** 最长飞行时间：100 刻 = 5 秒（需求）。 */
    public static final int MAX_FLIGHT_TICKS = 100;

    /**
     * "该消失了"的实体事件码。客户端收到后放烟花。
     * <p>取一个原版猪用不到的值即可 —— 我们仍然会把其它事件码交给 {@code super}，所以不会抢原版的行为。
     */
    public static final byte EVENT_VANISH = 67;

    /** 尾巴粒子每刻喷几颗（客户端本地生成）。 */
    private static final int TAIL_PARTICLES_PER_TICK = 4;

    /** 消失时额外散射的普通火花粒子数量（"大量的烟花粒子"）。 */
    private static final int VANISH_SPARK_COUNT = 220;

    /** 消失时叠几个原版烟花爆点（每个都会炸出一大片带拖尾的火花）。 */
    private static final int VANISH_EXPLOSION_COUNT = 3;

    /** 消失火花的初速度（格/刻）。 */
    private static final double VANISH_SPARK_SPEED = 0.45D;

    /** 已经飞了多少刻。 */
    private int flightTicks;

    public FireworkPig(EntityType<? extends FireworkPig> type, Level level) {
        super(type, level);
        this.setNoAi(true);       // 不要原版猪的 AI：它只管往前飞
        this.setNoGravity(true);  // 位移全部由 travel 决定，别让重力插一脚
    }

    /** 认领飞行朝向（水平方向，俯仰归零）。 */
    public void launch(float yaw) {
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(0.0F);
    }

    /**
     * 飞行方向（水平单位向量）。
     * <p>
     * 直接读<b>实体自身（已同步）的朝向</b>：这样服务端的位移和客户端的尾焰/爆炸用的是同一个朝向，
     * 不需要额外同步任何字段。本体开着 {@code setNoAi(true)}、没有目标也没有寻路，朝向不会被谁改掉。
     */
    private Vec3 flightDirection() {
        return Vec3.directionFromRotation(0.0F, this.getYRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // 尾焰在客户端本地生成（同原版烟花火箭），不受"32 格发包距离"限制
            this.spawnTailParticles();
            return;
        }
        // 撞到方块（水平或垂直方向被挡）→ 立刻消失
        if (this.horizontalCollision || this.verticalCollision) {
            this.vanish();
            return;
        }
        if (++this.flightTicks > MAX_FLIGHT_TICKS) {
            this.vanish();
            return;
        }
    }

    /**
     * 位移：忽略 {@code input}（AI/玩家输入），恒以自身朝向 × {@link #SPEED_PER_TICK} 前进。
     * <p>
     * 客户端直接返回：那边的位移由服务器的位置包插值，自己再算一遍只会来回抖。
     */
    @Override
    public void travel(Vec3 input) {
        if (this.level().isClientSide) {
            return;
        }
        // 头一直朝前、俯仰归零（视觉上"猪头朝着飞的方向"）
        this.setYHeadRot(this.getYRot());
        this.setXRot(0.0F);
        Vec3 motion = this.flightDirection().scale(SPEED_PER_TICK);
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        // move 撞到东西时会改掉 deltaMovement，这里按原速度写回去：
        // 下一帧的位移仍然由自己说了算（碰撞已经在这一帧记进 *_collision 标记里了）。
        this.setDeltaMovement(motion);
        this.hasImpulse = true;
    }

    /** 客户端：尾巴（朝向反方向）向后喷烟花粒子。 */
    private void spawnTailParticles() {
        Vec3 backward = this.flightDirection().scale(-0.6D);
        double y = this.getY() + (double) this.getBbHeight() * 0.5D;
        for (int i = 0; i < TAIL_PARTICLES_PER_TICK; i++) {
            this.level().addParticle(ParticleTypes.FIREWORK,
                this.getX() + backward.x + this.random.nextGaussian() * 0.08D,
                y + this.random.nextGaussian() * 0.08D,
                this.getZ() + backward.z + this.random.nextGaussian() * 0.08D,
                this.random.nextGaussian() * 0.03D, -0.01D, this.random.nextGaussian() * 0.03D);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_VANISH) {
            this.spawnVanishBurst();
            return;
        }
        super.handleEntityEvent(id);
    }

    /**
     * 客户端：消失点的烟花散射。
     * <ol>
     *   <li>先用原版 {@code createFireworks} 叠 {@link #VANISH_EXPLOSION_COUNT} 个随机颜色/形状的爆点 ——
     *       这才是"烟花"该有的样子（带拖尾的火花、按形状铺开）；</li>
     *   <li>再补 {@link #VANISH_SPARK_COUNT} 颗普通火花，朝四面八方均匀散开（需求："大量的烟花粒子"）；</li>
     *   <li>最后在消失点播一声烟花爆炸音效（客户端本地播，按距离自然衰减）。</li>
     * </ol>
     */
    private void spawnVanishBurst() {
        double x = this.getX();
        double y = this.getY() + (double) this.getBbHeight() * 0.5D;
        double z = this.getZ();

        List<FireworkExplosion> explosions = new ArrayList<>(VANISH_EXPLOSION_COUNT);
        for (int i = 0; i < VANISH_EXPLOSION_COUNT; i++) {
            explosions.add(LuckyFireworkRocket.randomExplosion(this.random));
        }
        this.level().createFireworks(x, y, z, 0.0D, 0.0D, 0.0D, explosions);

        for (int i = 0; i < VANISH_SPARK_COUNT; i++) {
            // 球面均匀取向
            double theta = this.random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = 2.0D * this.random.nextDouble() - 1.0D;
            double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
            this.level().addParticle(ParticleTypes.FIREWORK, x, y, z,
                sinPhi * Math.cos(theta) * VANISH_SPARK_SPEED,
                cosPhi * VANISH_SPARK_SPEED,
                sinPhi * Math.sin(theta) * VANISH_SPARK_SPEED);
        }

        this.level().playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
            SoundSource.AMBIENT, 1.0F, 1.0F, false);
    }

    /** 消失：广播"放烟花"事件（发给所有追踪到自己的玩家），然后移除自己。 */
    private void vanish() {
        if (this.level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(this, EVENT_VANISH);
        }
        this.discard();
    }

    /**
     * 被打死时也炸一片烟花（补一条"能看见烟花"的路径）。
     *
     * <p>两只消失条件（撞方块 / 飞满 5 秒）都带烟花，但玩家一刀把猪砍死时原本什么都没有；
     * 这里补上，语义上也说得通 —— 烟花猪死了就该炸开一片烟花。
     * 自己调用 {@code vanish()} 的那条路走的是 {@code DISCARDED}，不会重复炸。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && reason == RemovalReason.KILLED
            && this.level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(this, EVENT_VANISH);
        }
        super.remove(reason);
    }
}
