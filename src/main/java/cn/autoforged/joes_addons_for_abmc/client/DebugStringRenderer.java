package cn.autoforged.joes_addons_for_abmc.client;

import java.util.ArrayList;
import java.util.List;
import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.network.DebugStringPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调试端点/线段渲染：在 2~3 个端点处各渲染一个 whitedot（面向相机的 billboard），并在每对
 * 端点之间渲染被拉伸的 string 贴图（3 个端点时组成三角形）。由 {@code /jafa debug_string}
 * 命令触发。每次命令生成一组新的端点（不覆盖旧组），每组渲染约 5 秒后自动消失。
 * 端点抵达玩家位置后停止渲染该点的 whitedot，但线段与未抵达的端点仍会渲染。
 */
public final class DebugStringRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ABMC-DebugString");

    private static final ResourceLocation DOT_TEX =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/particle/whitedot.png");
    private static final ResourceLocation STRING_TEX =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/particle/string.png");

    // 叠加混合（additive）渲染类型：把像素颜色叠加到帧缓冲上，使贴图更亮、呈发光效果。
    // 仅 string 使用；whitedot 因端点处会被明亮 string 淹没，故用普通半透明混合保持清晰。
    private static final RenderType STRING_RENDER_TYPE = createAdditive(
        "jafa_string_additive", STRING_TEX);
    // whitedot 用普通半透明混合（非叠加），在端点处保持清晰，不被明亮的 string 淹没。
    private static final RenderType DOT_RENDER_TYPE = createTranslucent(
        "jafa_whitedot", DOT_TEX);

    private static RenderType createAdditive(String name, ResourceLocation tex) {
        // bufferSize 设得足够大：命令高频率执行时同帧会有大量线段顶点。
        // 每条线 2 块丝带 × 4 顶点 = 8 顶点，262144 可容纳数千条线。关闭 sortOnUpload，
        // 避免大量四边形排序带来的开销。
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                262144, false, false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true));
    }

    private static RenderType createTranslucent(String name, ResourceLocation tex) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                262144, false, false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true));
    }

    // string 贴图为 10x4，线段沿 U（10px）方向，V（4px）为厚度方向
    private static final float STRING_THICKNESS = 0.075F;
    // whitedot 世界尺寸（边长）。略大于 string 厚度，使圆点能在端点上突出
    private static final float DOT_SIZE = 0.21F;
    // 每组渲染持续时长（游戏刻，5 秒）
    private static final long DURATION_TICKS = 100L;
    // 端点汇聚目标相对玩家头部的世界坐标偏移（X/Y/Z）。
    // 由 X/C/V/B/N/M 键微调、P 键导出后由作者在源码中固化。
    public static final float DEFAULT_CONVERGE_OFFSET_X = 0.5F;
    public static final float DEFAULT_CONVERGE_OFFSET_Y = -0.42F;
    public static final float DEFAULT_CONVERGE_OFFSET_Z = 0.8F;
    public static float convergeOffsetX = DEFAULT_CONVERGE_OFFSET_X;
    public static float convergeOffsetY = DEFAULT_CONVERGE_OFFSET_Y;
    public static float convergeOffsetZ = DEFAULT_CONVERGE_OFFSET_Z;

    /** 离开世界时重置：清空所有端点组并把汇聚偏移恢复为默认值，防止跨存档残留。 */
    public static void reset() {
        synchronized (shapes) {
            shapes.clear();
        }
        convergeOffsetX = DEFAULT_CONVERGE_OFFSET_X;
        convergeOffsetY = DEFAULT_CONVERGE_OFFSET_Y;
        convergeOffsetZ = DEFAULT_CONVERGE_OFFSET_Z;
    }
    // 汇聚耗时范围（秒）：0.25~1 秒，各端点抵达时间差不超过 0.25 秒
    private static final double MIN_DURATION = 0.25, MAX_DURATION = 1.0;
    private static final double MAX_SPREAD = 0.25;

    /** 一组同时生成的端点（2 或 3 个），各自按抵达时刻汇聚到玩家处。 */
    private static final class ActiveShape {
        final double[][] start;      // [n][3] 起点坐标
        final long[] arriveTick;     // 每个端点的抵达时刻
        final long baseTick;         // 开始时刻
        final long expiryTick;       // 过期时刻
        ActiveShape(double[][] start, long[] arriveTick, long baseTick, long expiryTick) {
            this.start = start;
            this.arriveTick = arriveTick;
            this.baseTick = baseTick;
            this.expiryTick = expiryTick;
        }
    }

    private static final List<ActiveShape> shapes = new ArrayList<>();

    private DebugStringRenderer() {
    }

    /** 客户端收到端点坐标后新增一组渲染（不覆盖旧组）。 */
    public static void activate(DebugStringPayload payload) {
        double[][] pts = payload.points();
        Minecraft mc = Minecraft.getInstance();
        long baseTick = mc.level != null ? mc.level.getGameTime() : 0L;
        long expiry = (mc.level != null ? mc.level.getGameTime() : 0L) + DURATION_TICKS;
        int n = pts.length;
        long[] arrive = new long[n];
        double durA = MIN_DURATION + Math.random() * (MAX_DURATION - MIN_DURATION);
        for (int i = 0; i < n; i++) {
            double dur = i == 0 ? durA
                : Math.max(MIN_DURATION, Math.min(MAX_DURATION, durA + (Math.random() * 2.0 - 1.0) * MAX_SPREAD));
            arrive[i] = baseTick + (long) (dur * 20.0);
        }
        synchronized (shapes) {
            shapes.add(new ActiveShape(pts, arrive, baseTick, expiry));
            LOGGER.info("[DBG] activate: points={} totalShapes={} baseTick={}",
                n, shapes.size(), baseTick);
        }
    }

    /** 在世界渲染阶段绘制所有调试端点与线段。 */
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        // 清理：一旦某个形状的所有端点都已抵达汇聚点（最后一个端点到达），立即移除该形状，
        // 避免它塌缩成一点后仍空挂 5 秒导致不可见的旧形状大量累积。硬性过期时间作为兜底。
        List<ActiveShape> snapshot;
        synchronized (shapes) {
            shapes.removeIf(s -> now >= maxArrive(s) || now > s.expiryTick);
            snapshot = new ArrayList<>(shapes);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double nowF = now + partialTick; // 连续时间（含帧间分数）
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        // 汇聚目标 = 玩家眼位 + 头部坐标系汇聚偏移，端点最终汇聚到该位置。
        // 必须以玩家眼位为基准，而非第三人称摄像头位置：第三人称摄像头在“眼位 - 视线×距离”处，
        // 玩家抬头时摄像头会反向（向下）移动，若以摄像头为基准则汇聚点在上下摆头时会反向/几乎不动。
        // 偏移按持有者头部朝向（含 yaw 与上下摆头俯仰）旋转，使汇聚点相对头部的位置（与角度）
        // 保持恒定、与身体无关；玩家上下摆头时汇聚点随头部一同旋转（X 右、Y 上、Z 前均以头部视觉角度为准）。
        // 偏移量取自已绑定的配置 line_converge_offset（第一人称应用 converge_offset_x/y/z，
        // 第三人称/其他持有者额外叠加 third_person_offset_x/y/z），与红石激光/蛛网光束/附魔连线等一致，
        // 让玩家能在配置里直接调整汇聚点位置。（不再使用此前的内部微调偏移，否则改配置不生效）
        Vec3 conv = cn.autoforged.joes_addons_for_abmc.ModMain.applyLineEmitterOffset(
            mc.player, mc.player.getEyePosition());
        double px = conv.x;
        double py = conv.y;
        double pz = conv.z;

        // 单缓冲源：把所有形状的线段/端点顶点一次性写入同一个缓冲，帧末统一 endBatch() 刷新一次。
        // 相比逐形状各自 endBatch()，单次刷新不会在共享 GPU 缓冲间反复切换绑定状态，从而避免
        // “第二个形状画不出来”以及缓冲状态互相覆盖/破坏主渲染管线的问题。
        // renderString/renderDot 只负责往缓冲写顶点，真正绘制由 endBatch() 触发。
        // 异常统一在整帧外层捕获并记录，避免单个问题拖垮整帧渲染。
        try {
            MultiBufferSource.BufferSource buf =
                MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
            for (ActiveShape s : snapshot) {
                int n = s.start.length;
                Vec3[] pos = new Vec3[n];
                for (int i = 0; i < n; i++) {
                    double t = progress(nowF, s.baseTick, s.arriveTick[i]);
                    pos[i] = new Vec3(
                        lerp(s.start[i][0], px, t),
                        lerp(s.start[i][1], py, t),
                        lerp(s.start[i][2], pz, t));
                }
                // 每对端点之间渲染线段（3 个端点时组成三角形）
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        renderString(ps, cam, pos[i], pos[j], buf);
                    }
                }
                // 端点抵达后停止渲染该点的 whitedot；线段与未抵达端点仍渲染
                for (int i = 0; i < n; i++) {
                    if (nowF < s.arriveTick[i]) {
                        renderDot(ps, mc, cam, pos[i], buf);
                    }
                }
            }
            buf.endBatch();
        } catch (Throwable t) {
            LOGGER.error("[DBG] render threw:", t);
        }
    }

    /** 从开始时刻到抵达时刻的插值进度（0~1，含越界钳制）。nowF 为连续时间。 */
    private static double progress(double nowF, long baseTick, long arriveTick) {
        long span = arriveTick - baseTick;
        if (span <= 0L) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (nowF - baseTick) / (double) span));
    }

    /** 该形状最晚一个端点的抵达时刻（全部端点到齐即视为已收敛完毕）。 */
    private static long maxArrive(ActiveShape s) {
        long m = s.arriveTick[0];
        for (int i = 1; i < s.arriveTick.length; i++) {
            if (s.arriveTick[i] > m) {
                m = s.arriveTick[i];
            }
        }
        return m;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 在 a、b 两点之间渲染被拉伸的 string 十字交叉丝带：
     *  两块丝带绕线段轴垂直交叉（一块面向相机、另一块与之垂直），从任意角度都有实体光束感。
     *  @return true 表示该线段因长度退化（两点几乎重合）而跳过未绘制 */
    private static boolean renderString(PoseStack ps, Vec3 cam, Vec3 a, Vec3 b,
                                        MultiBufferSource ms) {
        double rx = b.x - a.x, ry = b.y - a.y, rz = b.z - a.z;
        double lenSq = rx * rx + ry * ry + rz * rz;
        if (lenSq < 1.0E-8) {
            return true;
        }
        double len = Math.sqrt(lenSq);
        Vec3 u = new Vec3(rx / len, ry / len, rz / len);

        // 第一块丝带厚度方向：把 相机→线段中点 投影到垂直于线段方向的平面上
        Vec3 mid = new Vec3((a.x + b.x) / 2.0, (a.y + b.y) / 2.0, (a.z + b.z) / 2.0);
        Vec3 toCam = cam.subtract(mid);
        Vec3 v1 = toCam.subtract(u.scale(toCam.dot(u)));
        double l1 = v1.length();
        if (l1 < 1.0E-8) {
            Vec3 base = Math.abs(u.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            v1 = u.cross(base);
            l1 = v1.length();
            if (l1 < 1.0E-8) {
                return true;
            }
        }
        v1 = v1.scale(1.0 / l1);

        // 第二块丝带与第一块垂直交叉（绕线段轴旋转 90°）
        Vec3 v2 = u.cross(v1);
        double l2 = v2.length();
        if (l2 < 1.0E-8) {
            return true;
        }
        v2 = v2.scale(1.0 / l2);

        ps.pushPose();
        ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);

        Matrix4f mat = ps.last().pose();

        // 两块垂直交叉的丝带：U 沿 a→b 拉伸，V 跨各自厚度方向
        VertexConsumer consumer = ms.getBuffer(STRING_RENDER_TYPE);
        drawRibbon(consumer, mat, v1, rx, ry, rz);
        drawRibbon(consumer, mat, v2, rx, ry, rz);

        ps.popPose();
        return false;
    }

    /** 绘制一块沿半厚度方向铺开的丝带四边形。 */
    private static void drawRibbon(VertexConsumer c, Matrix4f mat, Vec3 halfDir,
                                   double rx, double ry, double rz) {
        float hx = (float) (halfDir.x * STRING_THICKNESS / 2.0);
        float hy = (float) (halfDir.y * STRING_THICKNESS / 2.0);
        float hz = (float) (halfDir.z * STRING_THICKNESS / 2.0);
        addQuad(c, mat,
            hx, hy, hz, 0, 0,
            -hx, -hy, -hz, 0, 1,
            (float) (-hx + rx), (float) (-hy + ry), (float) (-hz + rz), 1, 1,
            (float) (hx + rx), (float) (hy + ry), (float) (hz + rz), 1, 0);
    }

    /** 在世界坐标点处渲染一个始终面向相机的 whitedot。 */
    private static void renderDot(PoseStack ps, Minecraft mc, Vec3 cam, Vec3 pos,
                                  MultiBufferSource ms) {
        // 把圆点中心沿 相机→点 方向微调，使其略高于 string，避免 z 冲突
        double tx = pos.x - cam.x, ty = pos.y - cam.y, tz = pos.z - cam.z;
        double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double nudge = 0.01 / (dist > 1.0E-4 ? dist : 1.0);
        double cx = tx + tx * nudge;
        double cy = ty + ty * nudge;
        double cz = tz + tz * nudge;

        ps.pushPose();
        ps.translate(cx, cy, cz);
        // billboard：用相机持有者（玩家）的朝向反向旋转，使四边形正面朝向相机
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));

        Matrix4f mat = ps.last().pose();
        float s = DOT_SIZE / 2.0F;
        VertexConsumer consumer = ms.getBuffer(DOT_RENDER_TYPE);
        addQuad(consumer, mat,
            -s, -s, 0, 0, 1,
            -s, s, 0, 0, 0,
            s, s, 0, 1, 0,
            s, -s, 0, 1, 1);

        ps.popPose();
    }

    private static void addQuad(VertexConsumer c, Matrix4f mat,
                                float x0, float y0, float z0, float u0, float v0,
                                float x1, float y1, float z1, float u1, float v1,
                                float x2, float y2, float z2, float u2, float v2,
                                float x3, float y3, float z3, float u3, float v3) {
        c.addVertex(mat, x0, y0, z0).setColor(255, 255, 255, 255)
            .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        c.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255)
            .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        c.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255)
            .setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        c.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255)
            .setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
    }
}