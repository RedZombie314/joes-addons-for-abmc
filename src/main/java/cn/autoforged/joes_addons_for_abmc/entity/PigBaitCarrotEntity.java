package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 幸运子事件（{@code item/pig_bait_carrot}）里的<b>诱猪胡萝卜</b>：一个"猪会追着跑"的胡萝卜掉落物。
 *
 * <h3>行为（逐条对应需求）</h3>
 * <ul>
 *   <li><b>吸引 20 格内的猪快速聚拢</b>：每 {@link #ATTRACT_INTERVAL_TICKS} 刻给半径
 *       {@link #ATTRACT_RADIUS} 内的每只猪下一次 {@code getNavigation().moveTo(胡萝卜, 1.6)}；
 *       猪自己的随机漫步每刻都在改路径，所以要<b>反复下</b>，只下一次会被它自己顶掉；</li>
 *   <li><b>玩家捡不起来</b>：构造时就 {@code setNeverPickUp()}（拾取延迟 32767）。
 *       注意它<b>不是</b>"谁都不能拿"——漏斗与漏斗矿车走的是 {@code HopperBlockEntity} 那条路，
 *       那条路<b>不看拾取延迟</b>，所以照样能把它吸进容器里（需求里那句"物品栏里的名字"就是这么看到的）；</li>
 *   <li><b>碰到猪的碰撞箱就消失</b>：每刻做一次精确的 {@code AABB.intersects} 判定；</li>
 *   <li><b>1 分钟内没被"捡走"就消失</b>：{@link #LIFETIME_TICKS} = 1200 刻（比原版掉落物的 6000 刻短得多）；
 *       这里的"没被捡走"指的是没被猪吃掉 —— 玩家本来就捡不走。</li>
 * </ul>
 *
 * <p>渲染复用原版 {@code ItemEntityRenderer}（物品栏里那件物品是带自定义名字的胡萝卜，
 * 名字见事件类 {@code LuckyPigBaitCarrotEvent}）。
 */
public class PigBaitCarrotEntity extends ItemEntity {

    /** 吸引半径（格）。 */
    public static final double ATTRACT_RADIUS = 20.0D;

    /** 存活时间：1200 刻 = 1 分钟（需求）。 */
    public static final int LIFETIME_TICKS = 1200;

    /** 多久重新下令一次（刻）。 */
    private static final int ATTRACT_INTERVAL_TICKS = 5;

    /** 吸引时用的移动速度倍率（原版 {@code TemptGoal} 用的是 1.2，这里更快一点 = "快速聚拢"）。 */
    private static final double LURE_SPEED = 1.6D;

    /** 判定"碰到猪"时向外放宽的一点点余量。 */
    private static final double TOUCH_MARGIN = 0.5D;

    public PigBaitCarrotEntity(EntityType<? extends PigBaitCarrotEntity> type, Level level) {
        super(type, level);
        // 玩家捡不走（漏斗照样能吸，见类注释）
        this.setNeverPickUp();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        int age = this.getAge();
        if (age >= LIFETIME_TICKS) {
            this.discard();
            return;
        }
        if (age % ATTRACT_INTERVAL_TICKS == 0) {
            this.attractPigs();
        }
        this.discardIfTouchingPig();
    }

    /** 把 {@link #ATTRACT_RADIUS} 内的猪都叫过来（每只都重新下一条通往自己的路径）。 */
    private void attractPigs() {
        List<Pig> pigs = this.level().getEntitiesOfClass(Pig.class,
            this.getBoundingBox().inflate(ATTRACT_RADIUS));
        if (pigs.isEmpty()) {
            return;
        }
        for (Pig pig : pigs) {
            pig.getNavigation().moveTo(this, LURE_SPEED);
        }
        // 一点点粒子，表示"这玩意儿在招猪"（也能让玩家一眼看出范围中心在哪）
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.COMPOSTER,
                this.getX(), this.getY() + 0.2D, this.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    /** 和任何一只猪的碰撞箱相交就消失（猪"吃掉"了它）。 */
    private void discardIfTouchingPig() {
        for (Pig pig : this.level().getEntitiesOfClass(Pig.class,
            this.getBoundingBox().inflate(TOUCH_MARGIN))) {
            if (pig.getBoundingBox().intersects(this.getBoundingBox())) {
                if (this.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        this.getX(), this.getY() + 0.2D, this.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.0D);
                }
                this.discard();
                return;
            }
        }
    }
}
