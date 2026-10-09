package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「被幸运选择器抓着 / 送走」的生物的体型按帧插值。由 {@code LivingEntityScaleInterpolationMixin}
 * 从 {@code LivingEntityRenderer#render} 里调用。
 *
 * <h3>要解决什么</h3>
 * 生物没有 {@code Display} 那种变换插值：体型只能靠 {@code Attributes.SCALE}，而属性是
 * <b>每刻同步一次、客户端收到就立刻生效</b>（{@code ServerEntity#sendChanges} 里
 * {@code getAttributesToSync()} 发完即清，ServerEntity.java:333-340；那段在 {@code updateInterval}
 * 闸门<b>之外</b>，所以确实每刻都发）。20 刻/秒 = 每 50 毫秒跳一档，无论分成几步，看上去都是
 * 「跳几下就没了」。这里在渲染时把两次同步之间补起来：客户端 60 帧/秒，两次同步之间能插出约 3 帧。
 *
 * <h3>关键：插值只能在「新值到来的那一格」里做</h3>
 * 一对关键帧是 {@code [上一刻的值, 这一刻的值]}，插值系数取当前 tick 已走完的比例
 * （{@code partialTick}，每 tick 从 0 重新开始）。于是<b>只在收到新值的那一格</b>里它才是
 * 「从上一刻扫到这一刻」；那一格过去之后还继续插，就成了每 tick 从 {@code 上一刻} 扫到 {@code 这一刻}、
 * 到 tick 边界又跳回 {@code 上一刻} 的锯齿——也就是「大小一直在小幅度跳动，尽管整体不变」。
 * 所以这里记下新值到达的那一 tick，只有当前 tick 还等于它时才插值，否则直接停在最新值。
 *
 * <p>这个坑只在「体型暂时不变」的段落里露馅：seek 收起阶段、抓着不动的持有阶段、send 的上升阶段。
 * 真正在变小的时候（seek 的压缩段、send 的缩小段）每刻都有新值，本来就一直是连续下滑的。
 *
 * <h3>作用范围</h3>
 * 只对 {@link LuckySelectorEntity#isScaleAnimatedBySelector(LivingEntity)} 为真的实体生效——
 * 也就是 {@code SCALE} 属性上挂着本模组那两个修饰符（{@code selector_carry_fit} /
 * {@code selector_send_shrink}）的生物。修饰符随属性一起同步，所以两端判据一致；
 * 巨型生物、玩家变形等其它任何缩放都不走这里。
 */
public final class SelectorScaleInterpolation {

    /** 每只被动画的生物的关键帧（实体卸载后由 {@link WeakHashMap} 自动回收）。 */
    private static final Map<LivingEntity, Keyframes> KEYFRAMES = new WeakHashMap<>();

    private SelectorScaleInterpolation() {
    }

    /**
     * 这只生物此刻应当被渲染成多大。
     *
     * @return 插值后的缩放；不在本模组动画中的实体原样返回 {@code entity.getScale()}
     */
    public static float scaleFor(LivingEntity entity) {
        float target = entity.getScale();
        if (!LuckySelectorEntity.isScaleAnimatedBySelector(entity)) {
            // 不在动画中：清掉状态，这样下次动画的第一帧就是它当时的真实大小（不会从旧值跳过来）。
            // 这个调用对**每一只**被渲染的生物每帧都会跑，所以表空时连哈希都不算。
            if (!KEYFRAMES.isEmpty()) {
                KEYFRAMES.remove(entity);
            }
            return target;
        }
        long now = entity.level().getGameTime();
        Keyframes keyframes = KEYFRAMES.get(entity);
        if (keyframes == null) {
            // 第一次看到它：没有「上一刻」可插，两个值都先设成当前值
            KEYFRAMES.put(entity, new Keyframes(now, target));
            return target;
        }
        if (keyframes.target != target) {
            // 新的一刻到了：上一刻的值挪成插值起点，并记下这一格
            keyframes.previous = keyframes.target;
            keyframes.target = target;
            keyframes.tick = now;
        }
        if (keyframes.previous == keyframes.target || keyframes.tick != now) {
            // 没有起点，或者「新值到来的那一格」已经过去 —— 见类注释：再插就成锯齿抖动了
            keyframes.previous = keyframes.target;
            return target;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return Mth.lerp(partialTick, keyframes.previous, keyframes.target);
    }

    /** 一对关键帧，外加「新值是在哪一 tick 到的」。 */
    private static final class Keyframes {
        /** 上一刻的缩放。 */
        private float previous;
        /** 这一刻（最新同步到的）缩放。 */
        private float target;
        /** 新值到达的 tick（{@code Level#getGameTime()}）。 */
        private long tick;

        private Keyframes(long tick, float value) {
            this.tick = tick;
            this.previous = value;
            this.target = value;
        }
    }
}
