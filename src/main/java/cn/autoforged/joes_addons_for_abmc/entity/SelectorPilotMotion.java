package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 旁观操控的<b>运动模型</b>：服务端与客户端<b>共用同一份代码</b>，这样客户端本地预测出来的轨迹与
 * 服务端权威轨迹才会逐刻一致（有一点实现差异，预测就会慢慢漂走、然后被纠正包拽回来，看起来就是抖）。
 *
 * <h3>模型</h3>
 * <ol>
 *   <li>方向：W/S 沿视线（含俯仰）、A/D 沿该朝向的水平左右、空格纯向上；多键同按先相加再归一化，
 *       任何组合的合成速度都是 {@link #SPEED_PER_TICK}；</li>
 *   <li>速度：每刻最多改变 {@link #ACCEL_PER_TICK}，向"方向 × 速度上限"逼近——按住就是加速到匀速，
 *       松手（目标速度 0）就是同样的减速度滑停。</li>
 * </ol>
 *
 * <h3>速度（需求 6.5.18：所有移速提升至两倍）</h3>
 * 原本 {@code 4.317 米/秒}（原版玩家步行速度），现在 <b>8.634 米/秒</b> = {@code 0.4317} 格/刻。
 * 加速度也同比翻倍，这样"加速到全速 / 松手滑停"的时间仍是 <b>约 0.22 秒</b>——只把速度翻倍会让起步和
 * 刹车显得拖沓。要保留原来的加速度手感（起步用 0.43 秒）就把 {@link #ACCEL_PER_TICK} 改回 0.05。
 *
 * <h3>为什么需要 {@link #RESYNC_DISTANCE}</h3>
 * 客户端预测的位置与服务端权威位置之间必然差着"一个传输来回"（约一刻的位移，当前速度下 0.43 格）。
 * 客户端拿到服务端位置包时，如果无脑照抄，就会"自己往前走一步 → 被一刻前的旧位置拽回来"，
 * 看起来是高频抖动；所以只有在差距大到 {@link #RESYNC_DISTANCE}（撞墙、被传送、区块没加载）时才认账，
 * 平时以自己的预测为准。两个模型完全一致，所以这个差距不会自己变大。
 */
public final class SelectorPilotMotion {

    /** 速度上限：8.634 米/秒 = 0.4317 格/刻（需求：所有移速 ×2，原值 4.317 米/秒）。 */
    public static final double SPEED_PER_TICK = 4.317D * 2.0D / 20.0D;

    /** 加速度（格/刻²）：0.1 → 约 4.3 刻（0.22 秒）加到全速，松手用同样的减速度滑停。 */
    public static final double ACCEL_PER_TICK = 0.05D * 2.0D;

    /** 速度平方小于它就当作停住了；滑停到这个程度就把控制权还给 AI。 */
    public static final double REST_SQR = 1.0E-4D * 4.0D;

    /**
     * 客户端预测与服务端权威位置差到这个距离（格）以上才认账并纠正。
     * <p>
     * 2.0 的来历：当前速度一刻走 0.43 格，稳态差距就是这么多，留约 4 倍余量；
     * 真撞上墙时差距每刻增加 0.43 格，约 4~5 刻就会触发纠正——既不会因为正常延迟误纠，
     * 也不会让"穿墙"持续太久。
     */
    public static final double RESYNC_DISTANCE = 2.0D;

    private SelectorPilotMotion() {
    }

    /**
     * 一步速度更新：把当前速度朝"方向 × 速度上限"逼近 {@link #ACCEL_PER_TICK}。
     *
     * @param currentVelocity 当前速度（客户端传自己记的预测速度，服务端传实体的 deltaMovement）
     */
    public static Vec3 step(Vec3 currentVelocity, float yaw, float pitch,
                            boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
        Vec3 target = direction(yaw, pitch, forward, backward, left, right, jump).scale(SPEED_PER_TICK);
        return approach(currentVelocity, target, ACCEL_PER_TICK);
    }

    /** 松手滑停：目标速度是 0 的一步（和加速共用同一段代码，所以减速度就等于加速度）。 */
    public static Vec3 coast(Vec3 currentVelocity) {
        return approach(currentVelocity, Vec3.ZERO, ACCEL_PER_TICK);
    }

    /** 速度是否已经小到可以算"停住了"。 */
    public static boolean atRest(Vec3 velocity) {
        return velocity.lengthSqr() < REST_SQR;
    }

    /**
     * 方向向量（单位长度）。
     * <ul>
     *   <li>视线用原版 {@code Entity#calculateViewVector} 的同一套公式
     *       （{@code (-sin(yaw)cos(pitch), -sin(pitch), cos(yaw)cos(pitch))}），
     *       这样"抬头按 W 往上飞"与画面看到的朝向完全一致；</li>
     *   <li>"左"取 yaw 逆时针 90°：{@code (cos(yaw), 0, sin(yaw))}，与原版
     *       {@code Entity#getInputVector} 一致（A 是左、D 是右）；</li>
     *   <li>空格是个纯竖直分量，所以"W + 空格"就是斜着往上飞。</li>
     * </ul>
     */
    public static Vec3 direction(float yaw, float pitch,
                                 boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
        double f = (forward ? 1.0D : 0.0D) - (backward ? 1.0D : 0.0D);
        double s = (left ? 1.0D : 0.0D) - (right ? 1.0D : 0.0D);
        double u = jump ? 1.0D : 0.0D;
        if (f == 0.0D && s == 0.0D && u == 0.0D) {
            return Vec3.ZERO;
        }
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float yawRad = yaw * Mth.DEG_TO_RAD;
        double cosPitch = Mth.cos(pitchRad);
        Vec3 look = new Vec3(-Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
        Vec3 leftVec = new Vec3(Mth.cos(yawRad), 0.0D, Mth.sin(yawRad));
        Vec3 direction = look.scale(f).add(leftVec.scale(s)).add(0.0D, u, 0.0D);
        return direction.lengthSqr() < 1.0E-8D ? Vec3.ZERO : direction.normalize();
    }

    /** 让 {@code current} 以每刻最多 {@code maxDelta} 的步长逼近 {@code target}。 */
    public static Vec3 approach(Vec3 current, Vec3 target, double maxDelta) {
        Vec3 delta = target.subtract(current);
        double length = delta.length();
        if (length <= maxDelta) {
            return target;
        }
        return current.add(delta.scale(maxDelta / length));
    }
}
