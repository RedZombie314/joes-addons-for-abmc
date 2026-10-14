package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 幸运维度的天空染色：<b>地平线 → 天顶</b> 两色渐变（用户指定）。
 *
 * <ul>
 *   <li>{@link #COLOR_LOW} = <b>#FFFCF1</b>：低处色 —— 用在<b>地平线</b>；</li>
 *   <li>{@link #COLOR_HIGH} = <b>#F4E8C1</b>：高处色 —— 用在<b>天顶</b>。</li>
 * </ul>
 *
 * <h2>地平线为什么是"另一个颜色"（这个 bug 的根因）</h2>
 * 原版的天空其实由<b>两个</b>颜色源拼出来，改一个不够：
 * <ol>
 *   <li><b>天顶</b>：{@code ClientLevel#getSkyColor(Vec3 pos, float partialTick)} 的返回值，
 *       被当作"天空盘"的顶点色（{@code LevelRenderer.java:1610-1620}）；</li>
 *   <li><b>地平线</b>：<b>雾色</b>。天空盘是一张<b>水平圆盘</b>——圆心在相机上方 16 格、半径 512 格
 *       （{@code LevelRenderer#buildSkyDisc}），圆盘按<b>柱面雾</b>着色，而画天空前刚调过
 *       {@code FogRenderer.levelFogColor()}（{@code LevelRenderer.java:1614}）。
 *       天空雾的区间是 {@code FOG_SKY: start = 0、end = 渲染距离}
 *       （{@code FogRenderer.java:268-271}），柱面雾只看水平距离 ——
 *       于是<b>正上方（水平距离 0）一点雾都没有 → 原色；越往地平线（水平距离 → 渲染距离以上）
 *       越接近 100% 雾色</b>。这就是"天顶变了、地平线还是原版蓝"的原因：
 *       蓝来自生物群系的 {@code fogColor}（主世界那套 #C0D8FF），跟天空色是两个源。</li>
 * </ol>
 * 所以这里两处都要改：{@link #COLOR_HIGH} 给"天空盘"（{@code getSkyColor}），
 * {@link #COLOR_LOW} 给雾色（{@link Fog} 里监听 {@code ViewportEvent.ComputeFogColor}）。
 * 顺带的好处是渐变是<b>真的</b>：圆盘上的雾量随水平距离连续变化，雾色又随
 * {@code levelFogColor()} 一起作用于远景地形与云，所以天空与远景空气会连成一片。
 *
 * <h2>昼夜</h2>
 * 两处现在都是<b>纯色</b>，不随昼夜/天气变化（与原版固定天空色的维度一个路子）。
 * 想让夜晚压暗，就在 {@link #skyColor()} 和 {@link Fog#onComputeFogColor} 里乘一个亮度系数
 * （爬行者维度那套是 {@code 0.55 + 0.45 * brightness}）。
 *
 * <h2>备用：整片天空随高度偏移</h2>
 * {@link #at(double)} 是上一版的做法（按相机 y 在低处色/高处色之间插值，整片天空同一个颜色）。
 * <b>当前未接线</b>：那样一来"天顶色"在低空会退化成低处色，渐变反而不见了。
 * 真要那种效果，把 {@code ClientLevelMixin} 里的 {@code LuckyDimensionSky.skyColor()}
 * 换成 {@code LuckyDimensionSky.at(pos.y)} 即可。
 */
@OnlyIn(Dist.CLIENT)
public final class LuckyDimensionSky {

    /** 颜色1 <b>#FFFCF1</b>：y 越低越接近它 → 用在<b>地平线</b>（雾色）。 */
    public static final Vec3 COLOR_LOW = fromHex(0xFFFCF1);

    /** 颜色2 <b>#F4E8C1</b>：y 越高越接近它 → 用在<b>天顶</b>（天空盘颜色）。 */
    public static final Vec3 COLOR_HIGH = fromHex(0xF4E8C1);

    /** 幸运维度 id（与 {@code ClientLevelMixin} 同一口径）。 */
    private static final ResourceLocation LUCKY_DIMENSION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dimension");

    /** {@link #at(double)} 用的两端高度（世界 y，仅在启用"按高度偏移"那一版时才有意义）。 */
    public static final double Y_LOW = -64.0D;
    public static final double Y_HIGH = 320.0D;

    private LuckyDimensionSky() {
    }

    /** 天顶色（{@code ClientLevel#getSkyColor} 的返回值，即"天空盘"的颜色）。 */
    public static Vec3 skyColor() {
        return COLOR_HIGH;
    }

    /**
     * <b>备用（当前未接线）</b>：按相机高度 y 在两色之间线性插值，用于"整片天空随高度整体偏移"。
     * <p>{@code Y_LOW = -64}、{@code Y_HIGH = 320} 取的是幸运维度的建造范围
     * （{@code ModDimensions.LUCKY_DIM_TYPE}：min_y = -64、height = 384 → 顶面 320）；
     * 想让过渡更明显就把这两个数收窄（例如 40 / 200）。
     * <p>{@code clampedLerp}：t &lt; 0 取低处色、t &gt; 1 取高处色，不会外推出越界颜色。
     */
    public static Vec3 at(double y) {
        double t = (y - Y_LOW) / (Y_HIGH - Y_LOW);
        return new Vec3(
            Mth.clampedLerp(COLOR_LOW.x, COLOR_HIGH.x, t),
            Mth.clampedLerp(COLOR_LOW.y, COLOR_HIGH.y, t),
            Mth.clampedLerp(COLOR_LOW.z, COLOR_HIGH.z, t));
    }

    /** {@code 0xRRGGBB} → 0.0~1.0 的三分量（把用户给的 # 颜色原样写在代码里）。 */
    private static Vec3 fromHex(int rgb) {
        return new Vec3(
            ((rgb >> 16) & 0xFF) / 255.0D,
            ((rgb >> 8) & 0xFF) / 255.0D,
            (rgb & 0xFF) / 255.0D);
    }

    /**
     * 地平线/远景空气的颜色：把幸运维度的<b>雾色</b>换成 {@link #COLOR_LOW}。
     *
     * <p>接入点 {@code ViewportEvent.ComputeFogColor}：它由
     * {@code ClientHooks.getFogColor} 在 {@code FogRenderer#setupColor} 的<b>最后一步</b>发出
     * （{@code ClientHooks.java:433-445}），而且<b>事件里改过的值会被采用</b>
     * （{@code fluidFogColor.set(event.getRed(), ...)}）。这一步之后没人再动这三个分量，
     * 所以不需要担心被覆盖；{@code setupColor} 每帧在 {@code LevelRenderer#renderLevel} 开头跑一次
     * （{@code LevelRenderer.java:955}），随后 {@code levelFogColor()} 把它交给
     * 地形雾、天空盘（:1614）和云（:1769）。
     *
     * <p>三种情况不抢：相机在<b>水/岩浆/细雪</b>里（{@code getFluidInCamera() != NONE}），
     * 以及玩家有<b>失明/黑暗</b>效果 —— 那几套雾有自己的语义，糊成奶白色就坏了。
     */
    @EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
    public static final class Fog {

        @SubscribeEvent
        public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !LUCKY_DIMENSION.equals(level.dimension().location())) {
                return;
            }
            if (event.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
                return;   // 水/岩浆/细雪：那是另一套雾
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null
                && (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS))) {
                return;   // 失明/黑暗：保持原版的压暗
            }
            event.setRed((float) COLOR_LOW.x);
            event.setGreen((float) COLOR_LOW.y);
            event.setBlue((float) COLOR_LOW.z);
        }
    }
}
