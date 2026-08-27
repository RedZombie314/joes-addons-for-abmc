package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端铁链钩取状态（铁块权杖）。
 * <p>
 * 铁链在“权杖发出点”与“被抓取的目标”之间渲染成一条<b>弧度很小的锁链曲线</b>：曲线由
 * 二次贝塞尔采样得到，控制点 = 中点 + 轻微下垂 + 甩动滞后，两点间始终绷紧、仅带一点
 * 弯曲，不再使用惯性鞭子（Verlet）模拟（那会造成弯曲过猛、形似抛物线的效果）。
 * <ul>
 *  <li>首节点锚定在玩家发射点（渲染时实时跟随玩家重算，移动/转头即跟手）。</li>
 *  <li>尾节点（“链头”）锚定在目标当前点，由服务端通过 {@link ChainGrabPayload} 周期同步。</li>
 *  <li>甩动滞后基于发出点的平滑速度：甩头时曲线向运动反方向轻微拖曳，幅度远小于原鞭子。</li>
 * </ul>
 * <p>同时保存“未命中发射收回”的短暂动画。起点在渲染时由客户端按玩家实时重算。
 */
public final class ChainBeamClient {

    /** 未命中时“发射 + 收回”动画的持续刻数。 */
    public static final int LAUNCH_DURATION = 10;

    /** 每节链段长度（格）：决定曲线的采样密度（节点间距）。 */
    public static final double SEG_LEN = 0.5;
    /** 链下垂量上限（格）：两点之间锁链的松弛下垂弧度，制造“绳子没绷紧”的真实感；
     *  下垂只发生在接近水平的链段上，竖直的链不弯。 */
    private static final double CURVE_SAG = 0.45;
    /** 下垂随链长的放大系数：sag = min(CURVE_SAG, 链长 × 该系数)。 */
    private static final double CURVE_SAG_PER_LEN = 0.05;
    /** 甩动滞后弧上限（格）：甩头时曲线向运动反方向的小幅拖曳，制造轻微甩尾。 */
    private static final double CURVE_LAG_MAX = 0.35;
    /** 发出点每帧位移 → 滞后偏移的换算系数。 */
    private static final double CURVE_LAG_FACTOR = 2.5;
    /** 发出点速度平滑系数（每帧，0~1）：越大对甩动响应越即时。 */
    private static final double CURVE_LAG_SMOOTH = 0.45;
    /** 节点数上限（含首尾）。链过长时截断，防止性能损耗。 */
    private static final int MAX_NODES = 65;

    private static boolean active;
    private static int mode;
    private static double endX, endY, endZ;
    /** 被钩取的目标实体 id（客户端据此跟踪其实时渲染位置作为链头，-1 表示无）。 */
    private static int endEntityId = -1;
    /** 当前链节点位置（世界坐标，首节点=玩家发射点，尾节点=目标点）。 */
    private static Vec3[] nodes = new Vec3[]{Vec3.ZERO, Vec3.ZERO};
    /** 上帧的发出点位置（用于计算甩动滞后）。 */
    private static Vec3 prevStart;
    /** 发出点的平滑速度，用于计算甩动滞后弧。 */
    private static Vec3 startVelSmooth = Vec3.ZERO;

    /** 未命中发射收回动画的剩余刻数。 */
    private static int launchTicks;
    private static double launchX, launchY, launchZ;

    private ChainBeamClient() {
    }

    /** 开始钩取（服务端首次同步）或拉取过程中的终点更新。链头锚定到目标点。
     *  @param entityId 被钩取目标实体 id（客户端据此跟踪其实时渲染位置作为链头；-1 表示无）。 */
    public static void start(int mode, double sx, double sy, double sz,
                             double ex, double ey, double ez, int entityId) {
        ChainBeamClient.mode = mode;
        ChainBeamClient.endX = ex;
        ChainBeamClient.endY = ey;
        ChainBeamClient.endZ = ez;
        ChainBeamClient.endEntityId = entityId;
        ChainBeamClient.active = true;
        ChainBeamClient.launchTicks = 0;
    }

    /** 停止钩取，清除铁链渲染。 */
    public static void clear() {
        ChainBeamClient.active = false;
        ChainBeamClient.launchTicks = 0;
        ChainBeamClient.prevStart = null;
        ChainBeamClient.endEntityId = -1;
    }

    /** 未命中：发射后自动收回的短动画。 */
    public static void launch(double ex, double ey, double ez) {
        ChainBeamClient.launchX = ex;
        ChainBeamClient.launchY = ey;
        ChainBeamClient.launchZ = ez;
        ChainBeamClient.launchTicks = LAUNCH_DURATION;
        ChainBeamClient.active = false;
        ChainBeamClient.prevStart = null;
        ChainBeamClient.endEntityId = -1;
    }

    /** 当前渲染帧的插值系数（partialTick），用于读取目标实体与渲染画面一致的插值位置。 */
    private static float partialTick = 1.0F;

    /** 每渲染帧推进：递减未命中动画计时；钩取中则更新锁链曲线。起点（首节点）由调用方传入。 */
    public static void tick(Vec3 start, float partialTick) {
        ChainBeamClient.partialTick = partialTick;
        if (launchTicks > 0) {
            launchTicks--;
        }
        if (active) {
            updateChain(start);
        }
    }

    /** 链头（铁链末端）目标点：优先使用被钩实体的实时（渲染帧插值）位置，使铁链与生物严格
     *  重合（拴绳效果，零视觉延迟）；实体不在视野/已消失时回退到服务端周期同步的坐标。 */
    private static Vec3 target() {
        if (endEntityId >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Entity e = mc.level.getEntity(endEntityId);
                if (e != null) {
                    return e.getPosition(partialTick);
                }
            }
        }
        return new Vec3(endX, endY, endZ);
    }

    /** 更新锁链：在“权杖发出点 start”与“链头 target()”之间生成一条弧度很小的锁链曲线。
     *  <p>曲线为二次贝塞尔，控制点 = 中点 + 轻微下垂 + 甩动滞后；两点间始终绷紧、只带一点
     *  弯曲。节点数按距离除以段长决定，沿曲线均匀采样，供渲染沿折线绘制链环。 */
    private static void updateChain(Vec3 start) {
        Vec3 t = target();
        double len = start.distanceTo(t);
        if (len < 1.0E-4) {
            nodes = new Vec3[]{start, t};
            prevStart = start;
            return;
        }

        // 发出点每帧位移 → 平滑 → 甩动滞后偏移（甩头时曲线向运动反方向轻微拖曳）
        Vec3 startVel = prevStart == null ? Vec3.ZERO : start.subtract(prevStart);
        startVelSmooth = startVelSmooth.scale(1.0 - CURVE_LAG_SMOOTH)
            .add(startVel.scale(CURVE_LAG_SMOOTH));
        Vec3 lag = startVelSmooth.scale(-CURVE_LAG_FACTOR);
        double lagLen = lag.length();
        if (lagLen > CURVE_LAG_MAX) {
            lag = lag.scale(CURVE_LAG_MAX / lagLen);
        }

        // 轻微下垂（松驰）：沿“世界竖直向下在垂直于绳方向上的分量”下垂——竖直的链不弯，
        // 越接近水平的链段垂得越多（类似真实锁链的重力下垂），长链垂得稍多一点。
        double sag = Math.min(CURVE_SAG, len * CURVE_SAG_PER_LEN);
        Vec3 mid = start.add(t).scale(0.5);
        Vec3 droop = Vec3.ZERO;
        Vec3 chord = t.subtract(start);
        double chordLen = chord.length();
        if (chordLen > 1.0E-4) {
            Vec3 chordN = chord.scale(1.0 / chordLen);
            double along = -chordN.y;   // 竖直向下的分量在绳方向上的投影（竖直的绳 → 1）
            droop = new Vec3(-chordN.x * along, -1.0 + chordN.y * along, -chordN.z * along)
                .scale(sag);
        }

        // 二次贝塞尔控制点 = 中点 + 下垂 + 滞后；总偏移再钳制在链长比例内（短链弯曲更小）
        Vec3 control = mid.add(droop).add(lag);
        Vec3 off = control.subtract(mid);
        double offLen = off.length();
        double maxOff = Math.min(CURVE_SAG + CURVE_LAG_MAX, len * 0.22);
        if (offLen > maxOff) {
            control = mid.add(off.scale(maxOff / offLen));
        }

        // 沿贝塞尔曲线均匀采样节点（间距约 0.5 格），渲染沿这些节点绘制链环
        int count = Math.min(MAX_NODES, Math.max(2, (int) Math.ceil(len / SEG_LEN) + 1));
        Vec3[] newNodes = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double f = (double) i / (count - 1);
            double u = 1.0 - f;
            newNodes[i] = start.scale(u * u)
                .add(control.scale(2.0 * u * f))
                .add(t.scale(f * f));
        }
        nodes = newNodes;
        prevStart = start;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isLaunching() {
        return launchTicks > 0;
    }

    /** 未命中动画的剩余刻数（渲染时用于计算“伸出→收回”进度）。 */
    public static int getLaunchTicks() {
        return launchTicks;
    }

    public static Vec3 getLaunchEnd() {
        return new Vec3(launchX, launchY, launchZ);
    }

    /** 当前鞭子链节点（起点→链头），渲染沿这些节点绘制链环。 */
    public static Vec3[] getNodes() {
        return nodes;
    }
}
