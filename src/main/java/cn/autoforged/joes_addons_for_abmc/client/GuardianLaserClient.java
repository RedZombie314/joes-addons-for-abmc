package cn.autoforged.joes_addons_for_abmc.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端：<b>守卫者激光的"画一条线"部分</b>（没有任何实体参与）。
 *
 * <h2>它怎么工作</h2>
 * <ol>
 *   <li>服务端打出一发光波时算好"起点 / 终点 / 颜色"，用
 *       {@code GuardianLaserPayload} 发给附近的玩家；</li>
 *   <li>这里把那条线记下来，在它"还活着"的 {@code ticks} 刻里每帧画一遍
 *       （见 {@link #render(RenderLevelStageEvent)}，挂在
 *       {@code ClientEvents#onRenderLevelStage} 上，和蛛网权杖那条蛛丝同一个位置）；</li>
 *   <li>到点自动从表里清掉，所以不需要服务端再发"结束"包。</li>
 * </ol>
 *
 * <h2>画法（参考蛛网权杖那条蛛丝）</h2>
 * immediate 缓冲 + {@link RenderType#entityTranslucent} 直接画四边形，不走模型、不建实体；
 * 贴图直接用原版守卫者激光的 {@code minecraft:textures/entity/guardian_beam.png}
 * （这条路径就是蛛网权杖用 {@code textures/block/cobweb.png} 的同一套：原生贴图按需加载，
 * 见 {@code TextureManager#getTexture} —— 不在图集里的贴图会当场新建一个 {@code SimpleTexture}）：
 * <ul>
 *   <li>光带是<b>十字形</b>：两条互相垂直的带子（固定正交基，与
 *       {@code ClientEvents#renderWebStar} 的十字星同一套写法），从任何角度看都是立体的光柱，
 *       不会退化成一条线；</li>
 *   <li>贴图沿光带方向每 2 格重复一次，并按时间平移 UV；</li>
 *   <li>颜色来自包里的随机色；<b>离消失越近越淡</b>（最后 6 刻淡出），看起来像激光熄灭。</li>
 * </ul>
 */
public final class GuardianLaserClient {

    /** 原版守卫者激光贴图。 */
    private static final ResourceLocation BEAM_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");

    /** 光带粗细（格）：与判定时用的采样半径大致对应。 */
    private static final float BEAM_WIDTH = 0.35F;

    /** 贴图沿光带方向每隔多少格重复一次。 */
    private static final double TILE_LENGTH = 2.0D;

    /** 全亮光照值（0xF000F0）：激光自己发光。 */
    private static final int FULL_BRIGHT = 15728880;

    /** 淡出刻数：最后这么多刻里 alpha 线性降到 0。 */
    private static final float FADE_OUT_TICKS = 6.0F;

    /** 同时存在的激光上限（防异常情况下这张表无限长）。 */
    private static final int MAX_ACTIVE = 24;

    /** 一条正在显示的激光。 */
    private record Laser(Vec3 start, Vec3 end, int color, long expireTick) {
    }

    private static final List<Laser> ACTIVE = new ArrayList<>();

    private GuardianLaserClient() {
    }

    /** 收到服务端的"画一条激光"包（见 {@code ModMain#registerPayloads}）。 */
    public static void activate(double startX, double startY, double startZ,
                                double endX, double endY, double endZ,
                                int color, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;   // 世界还没接上（极早的包）：直接丢掉，免得记下一条用不了的线
        }
        long now = mc.level.getGameTime();
        ACTIVE.add(new Laser(new Vec3(startX, startY, startZ), new Vec3(endX, endY, endZ),
            color, now + Math.max(1, ticks)));
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    /** 由 {@code ClientEvents#onRenderLevelStage} 每帧调用（放在"按住右键才画"的那些线段之前）。 */
    public static void render(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ACTIVE.removeIf(laser -> now > laser.expireTick());
        if (ACTIVE.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        try {
            MultiBufferSource.BufferSource buffer =
                MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(BEAM_TEXTURE));
            // 这个阶段的 PoseStack 原点<b>就是摄像机</b>（原版世界渲染就是这么摆的，
            // 见 {@code ClientEvents#renderWebStar}：那边的顶点也都减掉了摄像机坐标），
            // 所以顶点一律用"世界坐标 − 摄像机坐标"，不要再自己 translate 一次。
            Matrix4f matrix = poseStack.last().pose();
            for (Laser laser : ACTIVE) {
                drawLaser(consumer, matrix, camera, laser, now,
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
            buffer.endBatch();
        } catch (Throwable ignored) {
            // 单帧渲染异常不影响后续帧与主渲染管线（与蛛网/铁链线段同一套写法）
        }
    }

    /** 画一条激光（<b>十字形</b>：两条互相垂直的带子，做法照搬蛛网权杖那条蛛丝）。 */
    private static void drawLaser(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, Laser laser,
                                  long gameTime, float partialTick) {
        Vec3 span = laser.end().subtract(laser.start());
        double length = span.length();
        if (length < 1.0E-4D) {
            return;
        }
        Vec3 forward = span.scale(1.0D / length);

        // 固定正交基（<b>不依赖相机方向</b>，与 ClientEvents#renderWebStar 的十字星完全同一套写法）：
        //   side1 = 世界 up 投影到"垂直于 forward"的平面；
        //   side2 = forward × side1。
        // 两片各由 (side, forward) 张成、互相垂直，于是整条激光是一个立体的十字，
        // 从任何角度看都不会退化成一条线。方向正好竖直时（up 与 forward 平行）投影退化，改用 X/Z 兜底。
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double fU = forward.dot(up);
        Vec3 side1raw = new Vec3(up.x - forward.x * fU, up.y - forward.y * fU, up.z - forward.z * fU);
        Vec3 side1;
        Vec3 side2;
        if (side1raw.length() < 1.0E-6D) {
            side1 = new Vec3(1.0D, 0.0D, 0.0D);
            side2 = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            side1 = side1raw.normalize();
            side2 = forward.cross(side1).normalize();
        }
        double half = BEAM_WIDTH * 0.5D;

        // 剩下的刻数 → 淡出
        double remaining = laser.expireTick() - gameTime - partialTick;
        float alpha = Math.min(1.0F, (float) remaining / FADE_OUT_TICKS);
        if (alpha <= 0.02F) {
            return;
        }
        int color = laser.color();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        // UV 沿光带滚动，让静态贴图看起来像能量在往前流
        float scroll = ((gameTime + partialTick) % 40.0F) / 40.0F;
        float u1 = scroll;
        float u2 = scroll + (float) (length / TILE_LENGTH);

        // 十字的两片各画一次。不需要正反两面都发：RenderType.entityTranslucent 本身是 NO_CULL
        //（见 RenderType.ENTITY_TRANSLUCENT 的 .setCullState(NO_CULL)），两片从哪一面看都会被画出来。
        drawSide(consumer, matrix, camera, laser.start(), laser.end(),
            side1.scale(half), u1, u2, red, green, blue, alpha);
        drawSide(consumer, matrix, camera, laser.start(), laser.end(),
            side2.scale(half), u1, u2, red, green, blue, alpha);
    }

    /** 十字里的一片：沿 {@code offset} 正负方向各撑开半宽，从起点拉到终点。 */
    private static void drawSide(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                 Vec3 start, Vec3 end, Vec3 offset, float u1, float u2,
                                 float red, float green, float blue, float alpha) {
        Vec3 a0 = start.subtract(offset).subtract(camera);
        Vec3 a1 = start.add(offset).subtract(camera);
        Vec3 b1 = end.add(offset).subtract(camera);
        Vec3 b0 = end.subtract(offset).subtract(camera);
        vertex(consumer, matrix, a0, u1, 1.0F, red, green, blue, alpha);
        vertex(consumer, matrix, a1, u1, 0.0F, red, green, blue, alpha);
        vertex(consumer, matrix, b1, u2, 0.0F, red, green, blue, alpha);
        vertex(consumer, matrix, b0, u2, 1.0F, red, green, blue, alpha);
    }

    /** 一个顶点：位置 + 颜色（带淡出 alpha）+ UV + 全亮光照。 */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float u, float v,
                               float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(0.0F, 1.0F, 0.0F);
    }
}
