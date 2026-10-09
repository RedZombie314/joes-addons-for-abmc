package cn.autoforged.joes_addons_for_abmc.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * 黑暗分身的材质处理：取生物自身材质，灰化后按「密集带」二值化，并在运行时注册成一张新贴图。
 *
 * <h2>⚠ 方向是反的，这是刻意的，不要「纠正」</h2>
 * 本功能的映射是 <b>亮 → 黑、暗 → 白</b>（下方规则决定了密集带变黑）。
 * 作者已确认这一观感并明确要求保留：生物身上明显浅色的部位，在黑暗分身上是黑的。
 * 后续维护时若觉得「好像弄反了」，那不是 bug。
 *
 * <h2>二值化规则</h2>
 * <ol>
 *   <li>统计整张材质非透明像素的<b>灰度直方图</b>，找到颜色最集中的那一块灰度 {@code m}
 *       （对直方图做 ±{@link #DOMINANT_WINDOW} 的窗口平滑后再取峰值，避免落在孤立噪点上）；</li>
 *   <li>灰度落在 {@code [m-100, m+100]} 内的像素 → <b>黑</b>（区间越界时按 0 / 255 截断）；</li>
 *   <li>灰度落在 {@code [0,10]} 或 {@code [246,255]} 的像素 → <b>永远白</b>，即使它们落在上面的区间内
 *       （这条正是为了在密集带贴近边界时保住极暗/极亮的细节）；</li>
 *   <li>其余像素 → 白。</li>
 * </ol>
 *
 * <h2>贴图作用域</h2>
 * {@link #posterized} 只在「当前正在渲染一个黑暗分身」时才返回替换贴图，
 * 该状态由 {@code DarkCloneTextureCache#setActive} 在 {@code RenderLivingEvent.Pre/Post} 中开关。
 * 因此同一种材质上的普通生物不受影响。
 *
 * <p>图集（{@code minecraft:atlas/blocks} 等）读不到独立文件，{@link #darken} 会返回 {@code null}
 * 从而自动跳过——这也是手持物无法被二值化的根本原因（物品模型走图集 UV）。
 *
 * <h2>像素分量顺序</h2>
 * {@code NativeImage} 的 {@code getPixelRGBA / setPixelRGBA} 虽然叫 RGBA，
 * 但内部按小端读取，整数布局实际是 {@code AABBGGRR}（setter 的形参名就是 {@code abgrColor}），
 * 所以 R 在最低字节。
 */
public final class DarkCloneTextureCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 原材质 -> 二值化后的材质。 */
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

    /** 密集带半径：以主导灰度为心，±该值的灰度全部变黑。 */
    private static final int BAND_RADIUS = 100;
    /** 强制白色的下边界（灰度 <= 该值恒为白）。 */
    private static final int WHITE_LOW = 10;
    /** 强制白色的上边界（灰度 >= 该值恒为白）。 */
    private static final int WHITE_HIGH = 246;
    /** 求主导灰度时的直方图平滑窗口半径。 */
    private static final int DOMINANT_WINDOW = 2;

    /** 当前线程是否正在渲染黑暗分身。渲染是单线程的，用 ThreadLocal 以隔离区块构建等工作线程。 */
    private static final ThreadLocal<Boolean> RENDERING_DARK_CLONE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DarkCloneTextureCache() {
    }

    /** 由 {@code RenderLivingEvent.Pre/Post} 开关。 */
    public static void setActive(boolean active) {
        RENDERING_DARK_CLONE.set(active);
    }

    /**
     * 若当前正在渲染黑暗分身，取该材质对应的二值化版本；否则返回 {@code null}。
     * 供 {@code RenderType} 的各贴图工厂调用。
     */
    @Nullable
    public static ResourceLocation posterized(@Nullable ResourceLocation source) {
        if (!Boolean.TRUE.equals(RENDERING_DARK_CLONE.get())) return null;
        return darken(source);
    }

    /**
     * 取（必要时生成）某个材质对应的二值化版本；源材质不可读时返回 {@code null}，
     * 调用方应当放弃替换并保持原样渲染。
     */
    @Nullable
    public static ResourceLocation darken(@Nullable ResourceLocation source) {
        if (source == null) return null;
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation cached = CACHE.get(source);
        if (cached != null && minecraft.getTextureManager().getTexture(cached, null) != null) {
            return cached;
        }

        ResourceLocation created = build(minecraft, source);
        if (created != null) {
            CACHE.put(source, created);
        } else {
            CACHE.remove(source);
        }
        return created;
    }

    @Nullable
    private static ResourceLocation build(Minecraft minecraft, ResourceLocation source) {
        NativeImage output = null;
        try {
            Resource resource = minecraft.getResourceManager().getResource(source).orElse(null);
            if (resource == null) return null; // 图集、动态贴图等没有独立文件，自动跳过

            NativeImage input;
            try (InputStream stream = resource.open()) {
                input = NativeImage.read(stream);
            }
            try {
                output = posterize(input);
            } finally {
                input.close();
            }

            DynamicTexture texture = new DynamicTexture(output);
            output = null; // 所有权已交给 DynamicTexture，它会负责 close()
            String name = "jafa/dark_clone/" + source.getNamespace() + "/" + source.getPath();
            return minecraft.getTextureManager().register(name, texture);
        } catch (Exception e) {
            LOGGER.warn("[黑暗分身] 无法生成材质 {} 的二值化版本", source, e);
            return null;
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

    private static NativeImage posterize(NativeImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        int dominant = dominantGray(input);
        int low = Math.max(0, dominant - BAND_RADIUS);
        int high = Math.min(255, dominant + BAND_RADIUS);

        NativeImage output = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = input.getPixelRGBA(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha == 0) {
                    output.setPixelRGBA(x, y, 0);
                    continue;
                }
                int gray = luminance(pixel);
                // 落在密集带内 → 黑；但两端边界永远白
                boolean black = gray >= low && gray <= high && gray > WHITE_LOW && gray < WHITE_HIGH;
                int value = black ? 0x00 : 0xFF;
                output.setPixelRGBA(x, y, (alpha << 24) | (value << 16) | (value << 8) | value);
            }
        }
        return output;
    }

    /**
     * 找「颜色最集中的那一块灰度」：对非透明像素做灰度直方图，
     * 用 {@link #DOMINANT_WINDOW} 宽度的滑动窗口求和后取峰值。
     * 加窗口是为了取到「最密集的那一片」而不是某个孤立噪点值。
     */
    private static int dominantGray(NativeImage image) {
        int[] histogram = new int[256];
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (((pixel >>> 24) & 0xFF) == 0) continue;
                histogram[luminance(pixel)]++;
            }
        }

        int bestGray = 128;
        int bestWeight = -1;
        for (int gray = 0; gray < 256; gray++) {
            int weight = 0;
            for (int offset = -DOMINANT_WINDOW; offset <= DOMINANT_WINDOW; offset++) {
                int index = gray + offset;
                if (index >= 0 && index < 256) weight += histogram[index];
            }
            if (weight > bestWeight) {
                bestWeight = weight;
                bestGray = gray;
            }
        }
        return bestGray;
    }

    /** 人眼亮度权重 0.30R + 0.59G + 0.11B 的整数近似。 */
    private static int luminance(int abgrPixel) {
        int red = abgrPixel & 0xFF;
        int green = (abgrPixel >>> 8) & 0xFF;
        int blue = (abgrPixel >>> 16) & 0xFF;
        return (red * 30 + green * 59 + blue * 11) / 100;
    }
}
