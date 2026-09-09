#version 150

uniform sampler2D DiffuseSampler;
uniform float FlashTime;
uniform vec2 OutSize;
uniform float HitX;
uniform float HitY;

in vec2 texCoord;
out vec4 fragColor;

// 金色四角星，简单哈希：输入浮点，输出 0..1
float hash1(float x){
    return fract(sin(x * 1234.5678) * 43758.5453123);
}

void main(){
    // 归一化坐标（与 iResolution 对齐；OutSize 由后处理管线自动设为输出帧缓冲尺寸）
    vec2 R  = OutSize;
    vec2 uv = texCoord;

    // 星芒中心：由客户端把“玩家前方 3 格世界点”投影到屏幕 UV（HitX/HitY）。
    vec2 hit = vec2(HitX, HitY);

    // 触发后经过的秒数
    float t = FlashTime;

    // 单次命中闪光的尺寸与宽高比（长边 > 高）
    float baseSize = mix(0.07, 0.10, hash1(0.37));
    float ar       = mix(0.90, 1.0, hash1(2.71));

    // 参数：更亮、摆正（上下左右对齐）
    vec2  aspect    = vec2(R.x/R.y, 1.0);
    float sharpness = 100.0;
    float intensity = 6.0;
    vec3  starColor = vec3(1.0, 0.95, 0.45);

    // 坐标：等比+整体缩放，再各向异性缩放（横向更长）
    vec2 p = (uv - hit) * aspect / baseSize;
    p.x /= ar;

    // 四角星（摆正）：峰值在上下左右方向
    float a = atan(p.y, p.x);
    float r = max(1e-5, length(p));
    float k = abs(cos(2.0*a));
    float s = pow(k, sharpness) / (1.0 + r*60.0);

    // 中心核
    float core = 1.0 / (1.0 + dot(p,p)*90.0);

    // 单次“命中闪光”：仅在触发瞬间亮起并快速衰减（约 0.1 秒后衰减到 ~9%），只闪一次
    float flash = exp(-t * 24.0);

    // 星芒光晕叠加到场景颜色上（保留游戏画面，只在命中瞬间叠加金光星芒）
    vec3 flare = (s + core*0.9) * starColor * intensity * flash;

    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    vec3 col = scene + flare;

    fragColor = vec4(col, 1.0);
}