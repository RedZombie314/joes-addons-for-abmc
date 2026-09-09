package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 金色命中闪光：由 {@code /jafa gold_hit_flash} 触发，在玩家正前方约 3 格处播放一段
 * 金色四角星脉动闪光。实现为后处理着色器（PostChain），叠加在场景之上：
 * <p>
 * 触发时记录“玩家眼位 + 视线方向 × 3”的世界锚点，随后每客户端 tick 把该锚点投影到
 * 屏幕 UV（{@code HitX}/{@code HitY}），驱动星芒中心随视角/移动在屏幕上的真实位置；
 * 同时更新 {@code FlashTime} 控制星芒脉动，超过时长后复位。整个渲染沿用原版后处理流程，
 * 无需额外 mixin。
 *
 * @see /jafa gold_hit_flash
 */
public final class GoldHitFlashRenderer {

    private static final ResourceLocation FLASH_LOCATION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "shaders/post/gold_hit_flash.json");

    /** 闪光持续时长（秒）。单次闪光约 0.15 秒衰减殆尽，稍留余量后关闭后处理恢复原版渲染状态。 */
    private static final float DURATION_SECONDS = 0.4F;

    /** 世界锚点：触发瞬间“玩家眼位 + 视线 × 3”的世界坐标，触发后固定不变；每帧投影到屏幕 UV 作为星芒中心。 */
    private static Vec3 anchor = null;

    /** 触发时刻（纳秒）。-1 表示当前无闪光。 */
    private static long flashStartNanos = -1L;

    private GoldHitFlashRenderer() {
    }

    /** 客户端收到触发信号后调用：记录前方 3 格世界锚点、加载闪光后处理并记录开始时刻。 */
    public static void activate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        anchor = null;
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0F);
            anchor = eye.add(look.scale(3.0));
        }
        mc.gameRenderer.loadEffect(FLASH_LOCATION);
        flashStartNanos = System.nanoTime();
    }

    /** 每客户端 tick（Post）调用：推进闪光时间、投影锚点、更新 Hit/FlashTime uniform，超时后复位。 */
    public static void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        if (flashStartNanos < 0L) return;
        float t = (System.nanoTime() - flashStartNanos) / 1_000_000_000.0F;
        if (t >= DURATION_SECONDS) {
            reset();
            return;
        }
        if (mc.gameRenderer == null) return;
        PostChain effect = mc.gameRenderer.currentEffect();
        if (effect == null || !FLASH_LOCATION.toString().equals(effect.getName())) return;

        effect.setUniform("FlashTime", t);
        if (anchor != null) {
            float[] uv = projectAnchorToUv(mc, anchor);
            if (uv != null) {
                effect.setUniform("HitX", uv[0]);
                effect.setUniform("HitY", uv[1]);
            }
        }
    }

    /**
     * 把世界锚点投影到屏幕归一化 UV（左下 (0,0)，右上 (1,1)），供片段着色器定位星芒中心。
     * 手动投影：用相机基向量把锚点转到相机空间（沿 forwards 的前向距离为深度），再用垂直 FOV
     * + 宽高比映射到 NDC。仅当锚点在相机前方且未被近平面裁剪时返回有效值。
     */
    private static float[] projectAnchorToUv(Minecraft mc, Vec3 anchor) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) return null;

        Vec3 camPos = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();

        double dx = anchor.x - camPos.x;
        double dy = anchor.y - camPos.y;
        double dz = anchor.z - camPos.z;

        // 右向量 = -left
        double rx = -left.x(), ry = -left.y(), rz = -left.z();
        // 深度 = 沿视线方向（forwards）的前向距离，正前方为正。注意不能取负：
        // 取负会把“正前方 3 格”的锚点判成负深度，导致投影永被跳过、星芒锁定默认居中。
        double depth = dx * look.x() + dy * look.y() + dz * look.z();
        double rightOffset = dx * rx + dy * ry + dz * rz;                 // 右方向偏移
        double upOffset = dx * up.x() + dy * up.y() + dz * up.z();        // 上方向偏移

        if (depth < 0.05) return null; // 锚点在相机后方或过近

        double fovY = Math.toRadians(70.0);
        double f = 1.0 / Math.tan(fovY * 0.5);
        double aspect = (double) mc.getWindow().getWidth() / (double) mc.getWindow().getHeight();
        double xNdc = (f / aspect) * rightOffset / depth;
        double yNdc = f * upOffset / depth;

        return new float[] {
            (float) ((xNdc + 1.0) * 0.5),
            (float) ((yNdc + 1.0) * 0.5)
        };
    }

    /** 离开世界 / 超时复位：仅当当前后处理确为本闪光时才关闭，避免误关原版或其他模组效果。 */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        flashStartNanos = -1L;
        anchor = null;
        if (mc.gameRenderer != null) {
            PostChain effect = mc.gameRenderer.currentEffect();
            if (effect != null && FLASH_LOCATION.toString().equals(effect.getName())) {
                mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
            }
        }
    }
}