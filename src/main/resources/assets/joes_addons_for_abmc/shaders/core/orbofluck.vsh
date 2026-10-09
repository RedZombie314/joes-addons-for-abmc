#version 150

// 幸运核心（Orb of Luck）的公告板顶点着色器。
// 面片由 OrbOfLuckRenderer 生成：正对摄像机的正方形，边长 = 2 * 半边长（见该类的 QUAD_HALF_PER_RADIUS）。
// 这里只负责把局部 UV 和顶点色（呼吸相位）传给片元着色器。

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vLocalUv;
out float vBreath;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vLocalUv = UV0;
    // 顶点色是 8bit 归一化通道，只能可靠地承载 0..1；
    // 因此 Java 端传的是"呼吸相位"（0=最弱，1=最强），强度区间留在 fsh 里配置。
    vBreath = Color.r;
}
