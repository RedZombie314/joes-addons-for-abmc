#version 150

// ============================================================
// 幸运核心（Orb of Luck）片元着色器
//
// 移植自 Shadertoy 版全屏版本，像素级公式完全一致，只换了坐标来源：
//   Shadertoy:  p = (fragCoord - CENTER_UV * iResolution.xy) / min(w, h)
//               r = |p| / LIGHT_SIZE
//   本着色器:   公告板面片的局部 UV 直接承担坐标
//               p = (uv - 0.5) * SPAN,  r = |p|
// 也就是把"LIGHT_SIZE = 屏幕短边的 10%"这个屏幕空间尺度，换成了"面片半边长"这个世界空间尺度：
//   面片半边长 Q（世界格）  <->  r = SPAN/2  （= 3.0，此处 alpha 已经 ≈ 0.008）
//   世界半径 R1 = 2Q/SPAN   <->  r = 1
// Java 端按 Q = R * SPAN / (2 * VISUAL_EDGE_R) 定面片大小，
// 于是碰撞箱半径 R 正好落在 alpha ≈ 0.1 的那圈上（肉眼看就是"球面"）。
// ============================================================

in vec2 vLocalUv;
in float vBreath;

out vec4 fragColor;

// ============================================================
// 配置区（与原着色器一致）
// ============================================================

// 最弱、最强发光强度
const float MIN_INTENSITY = 1.2;
const float MAX_INTENSITY = 1.35;

// 中心接近白色，外围略偏暖黄
const vec3 CORE_COLOR = vec3(1.0, 0.98, 0.90);
const vec3 GLOW_COLOR = vec3(1.0, 0.90, 0.65);

// 面片跨度：面片边缘中点处 r = SPAN/2。
// 原版在 r=5 才归零，但那一段 alpha < 0.01 根本看不见，白白让面片大 1.7 倍；
// 这里截到 r=3，配合下面的第二段 fade，肉眼看不出差别。
const float SPAN = 6.0;

void main() {
    float r = length((vLocalUv - 0.5) * SPAN);
    float r2 = r * r;

    // --------------------------------------------------------
    // 1. 周期性呼吸（相位由 Java 按游戏时间算出，经顶点色传入）
    // --------------------------------------------------------
    float intensity = mix(MIN_INTENSITY, MAX_INTENSITY, clamp(vBreath, 0.0, 1.0));

    // --------------------------------------------------------
    // 2. 连续衰减的光，没有实体边界
    // --------------------------------------------------------
    // 小而柔和的明亮光核
    float core = exp(-r2 * 5.0);
    // 中层光晕
    float halo = exp(-r2 * 1.2);
    // 更宽、更淡的外围散光
    float outerGlow = exp(-r2 * 0.3);

    float light = intensity * (
        0.65 * core
        + 0.25 * halo
        + 0.10 * outerGlow
    );

    // 远处平滑归零，保证面片边缘完全透明：
    // 第一段照抄原版 fade(3.5 -> 5.0)，第二段把 r>2.2 的尾巴提前收掉（对应上面的 SPAN 截断）。
    float fade = (1.0 - smoothstep(3.5, 5.0, r)) * (1.0 - smoothstep(2.2, 3.0, r));

    float alpha = clamp(light * fade, 0.0, 1.0);

    // 8bit 帧缓冲里 alpha < 0.5/255 的像素本来就画不出来，直接丢弃省混合带宽。
    if (alpha < 0.002) {
        discard;
    }

    // 中心偏白，向外逐渐偏暖黄
    float coreMix = exp(-r2 * 2.0);
    vec3 color = mix(GLOW_COLOR, CORE_COLOR, coreMix);

    // 直通 Alpha（非预乘）：配合 RenderType 的 TRANSLUCENT_TRANSPARENCY
    // (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)，合成结果与原版
    // vec4(color * alpha, alpha) + 预乘混合完全等价。
    fragColor = vec4(color, alpha);
}
