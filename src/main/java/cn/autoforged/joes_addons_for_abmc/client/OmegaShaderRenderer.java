package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/**
 * Omega 着色器静态测试：由 {@code /jafa omega_shader} 触发，播放一段“3D 点汇聚到屏幕中心”的
 * 后处理着色器视觉（白色发光连线 + 常亮正方形点），背景透明叠加在游戏画面上。
 * <p>
 * 触发时客户端本地生成随机点（数量 + 各点世界起点，替代 Shadertoy 的 Buffer A 与着色器内的 hash 生成），
 * 并据这些点算出所有点汇聚到中心所需的时间；每客户端 tick 推进 {@code ElapsedTime} 并把点坐标作为
 * uniform 写入着色器，一旦到达汇聚时刻立即复位结束。点生成在 Java 侧是唯一数据源，保证结束时刻与
 * 着色器实际渲染的点完全一致（避免 CPU/GPU 的 sin 精度差异导致提前/滞后结束）。
 *
 * @see /jafa omega_shader
 */
public final class OmegaShaderRenderer {

    private static final ResourceLocation LOCATION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "shaders/post/omega_absorb.json");

    /** 本次运行所有点汇聚到中心所需的时间（秒）。 */
    private static float arriveSeconds = 0.0F;

    /** 触发时刻（纳秒）。-1 表示当前无效果。 */
    private static long startNanos = -1L;

    /** 本次运行的点数量（2 或 3）。 */
    private static int count = 2;

    /** 本次运行的各点世界起点坐标（i<count 有效，其余置为 TARGET=原点）。 */
    private static final float[][] startPos = new float[3][3];

    private OmegaShaderRenderer() {
    }

    /** 客户端收到触发信号后调用：生成随机点、算出汇聚时刻、加载后处理并记录开始时刻。 */
    public static void activate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        generatePoints((float) Math.random());
        mc.gameRenderer.loadEffect(LOCATION);
        startNanos = System.nanoTime();
    }

    /** 每客户端 tick（Post）调用：推进时间并写入 ElapsedTime/Count/StartPos uniform，到达汇聚时刻后立即复位。 */
    public static void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        if (startNanos < 0L) return;
        float t = (System.nanoTime() - startNanos) / 1_000_000_000.0F;
        if (t >= arriveSeconds) {
            reset();
            return;
        }
        if (mc.gameRenderer == null) return;
        PostChain effect = mc.gameRenderer.currentEffect();
        if (effect == null || !LOCATION.toString().equals(effect.getName())) return;
        effect.setUniform("ElapsedTime", t);
        effect.setUniform("Count", (float) count);
        effect.setUniform("StartPos0X", startPos[0][0]);
        effect.setUniform("StartPos0Y", startPos[0][1]);
        effect.setUniform("StartPos0Z", startPos[0][2]);
        effect.setUniform("StartPos1X", startPos[1][0]);
        effect.setUniform("StartPos1Y", startPos[1][1]);
        effect.setUniform("StartPos1Z", startPos[1][2]);
        effect.setUniform("StartPos2X", startPos[2][0]);
        effect.setUniform("StartPos2Y", startPos[2][1]);
        effect.setUniform("StartPos2Z", startPos[2][2]);
    }

    /** 离开世界 / 超时复位：仅当当前后处理确为本效果时才关闭，避免误关原版或其他模组效果。 */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        startNanos = -1L;
        if (mc.gameRenderer != null) {
            PostChain effect = mc.gameRenderer.currentEffect();
            if (effect != null && LOCATION.toString().equals(effect.getName())) {
                mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
            }
        }
    }

    /**
     * 由种子生成本次运行的点（数量 + 各点世界起点坐标 + 汇聚所需时间）——唯一数据源。
     * 着色器只读取这里生成的坐标（不再自行 hash），因此 arriveSeconds 与着色器实际渲染的点完全一致，
     * 保证“所有点都抵达终点才结束”。几何常量 SPAWN_RANGE=(2,1.5,1)、ACCELERATION=1.25 与着色器一致。
     */
    private static void generatePoints(float seed) {
        double baseSeed = seed * 1000.0;
        count = 2 + (hash11(baseSeed + 17.0) >= 0.5 ? 1 : 0);
        double maxDist = 0.0;
        for (int i = 0; i < 3; i++) {
            if (i < count) {
                double s = baseSeed + (double) i * 73.0;
                double ox = (hash11(s + 1.0) * 2.0 - 1.0) * 2.0;   // SPAWN_RANGE.x = 2.0
                double oy = (hash11(s + 9.0) * 2.0 - 1.0) * 1.5;   // SPAWN_RANGE.y = 1.5
                double oz = (hash11(s + 23.0) * 2.0 - 1.0) * 1.0;  // SPAWN_RANGE.z = 1.0
                startPos[i][0] = (float) ox;
                startPos[i][1] = (float) oy;
                startPos[i][2] = (float) oz;
                double dist = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (dist > maxDist) {
                    maxDist = dist;
                }
            } else {
                startPos[i][0] = 0.0f;
                startPos[i][1] = 0.0f;
                startPos[i][2] = 0.0f;
            }
        }
        // travel = 0.5 * ACCELERATION * t*t >= maxDist  =>  t = sqrt(2*maxDist/ACCELERATION)
        arriveSeconds = (float) Math.sqrt(2.0 * maxDist / 1.25);
    }

    /** 与原版着色器一致的 sin 哈希（输入任意浮点，输出 0..1）。 */
    private static double hash11(double p) {
        double v = Math.sin(p * 127.1 + 311.7) * 43758.5453;
        return v - Math.floor(v);
    }
}