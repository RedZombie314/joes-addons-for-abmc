#version 150

uniform vec2 OutSize;              // = Shadertoy iResolution（PostChain 自动设为帧缓冲尺寸）
uniform float ElapsedTime;         // 触发后经过的秒数（Java 端计算）
uniform float Count;               // 点的数量（2 或 3，Java 端生成）
uniform float StartPos0X; uniform float StartPos0Y; uniform float StartPos0Z;
uniform float StartPos1X; uniform float StartPos1Y; uniform float StartPos1Z;
uniform float StartPos2X; uniform float StartPos2Y; uniform float StartPos2Z;
uniform sampler2D DiffuseSampler; // 场景颜色（透明叠加：背景 = 游戏画面）

in vec2 texCoord;
out vec4 fragColor;

// ===== 配置区（独立静态测试：固定几何，点汇聚到屏幕中心）=====
const vec3 TARGET = vec3(0.0, 0.0, 0.0);
const float ACCELERATION = 1.25;
const vec3 CAMERA_POSITION = vec3(0.0, 0.0, 6.0);
const float CAMERA_FOV = 60.0;
const float NEAR_CLIP = 0.05;
const float INTENSITY = 1.0 / 3.0; // 发光强度缩减为 1/3

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float h = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
    return length(p - (a + h * ab));
}

void getCameraBasis(out vec3 right, out vec3 up, out vec3 forward) {
    vec3 direction = TARGET - CAMERA_POSITION;
    forward = length(direction) > 0.0001 ? normalize(direction) : vec3(0.0, 0.0, -1.0);
    vec3 referenceUp = vec3(0.0, 1.0, 0.0);
    if (abs(dot(forward, referenceUp)) > 0.999) {
        referenceUp = vec3(0.0, 0.0, 1.0);
    }
    right = normalize(cross(forward, referenceUp));
    up = normalize(cross(right, forward));
}

vec3 worldToCamera(vec3 worldPos, vec3 right, vec3 up, vec3 forward) {
    vec3 relative = worldPos - CAMERA_POSITION;
    return vec3(dot(relative, right), dot(relative, up), dot(relative, forward));
}

vec2 cameraToScreen(vec3 cameraPos) {
    float focal = 1.0 / tan(radians(CAMERA_FOV) * 0.5);
    vec2 projected = cameraPos.xy * focal / cameraPos.z;
    return 0.5 * OutSize + 0.5 * OutSize.y * projected;
}

bool clipSegmentToNearPlane(inout vec3 a, inout vec3 b) {
    bool aBehind = a.z < NEAR_CLIP;
    bool bBehind = b.z < NEAR_CLIP;
    if (aBehind && bBehind) return false;
    if (aBehind) {
        float h = (NEAR_CLIP - a.z) / (b.z - a.z);
        a = mix(a, b, h);
        a.z = NEAR_CLIP;
    } else if (bBehind) {
        float h = (NEAR_CLIP - b.z) / (a.z - b.z);
        b = mix(b, a, h);
        b.z = NEAR_CLIP;
    }
    return true;
}

// 由 Java 端传入的 float 分量重建第 i 个点的世界起点坐标
vec3 getStartPos(int i) {
    if (i == 0) return vec3(StartPos0X, StartPos0Y, StartPos0Z);
    if (i == 1) return vec3(StartPos1X, StartPos1Y, StartPos1Z);
    return vec3(StartPos2X, StartPos2Y, StartPos2Z);
}

void main() {
    vec2 fragCoord = texCoord * OutSize;

    vec3 col = vec3(0.0);

    float t = ElapsedTime;
    int count = int(Count);

    float travel = 0.5 * ACCELERATION * t * t;

    float scale = min(OutSize.x, OutSize.y);
    float halfSize = 4.0 * max(1.5, scale * 0.003);
    float lineHalfWidth = halfSize * 0.5;

    vec3 cameraRight, cameraUp, cameraForward;
    getCameraBasis(cameraRight, cameraUp, cameraForward);

    vec3 worldPositions[3];
    vec3 cameraPositions[3];

    for (int i = 0; i < 3; i++) {
        worldPositions[i] = TARGET;
        cameraPositions[i] = vec3(0.0);
        if (i >= count) {
            continue;
        }
        vec3 startPos = getStartPos(i);
        float distanceToTarget = length(TARGET - startPos);
        bool arrived = travel >= distanceToTarget;
        if (arrived) {
            worldPositions[i] = TARGET;
        } else {
            float progress = travel / max(distanceToTarget, 0.0001);
            worldPositions[i] = mix(startPos, TARGET, progress);
        }
        cameraPositions[i] = worldToCamera(worldPositions[i], cameraRight, cameraUp, cameraForward);
    }

    // 白线（发光强度 × INTENSITY）
    for (int i = 0; i < 3; i++) {
        if (i >= count) {
            break;
        }
        for (int j = 0; j < 3; j++) {
            if (j <= i || j >= count) {
                continue;
            }
            vec3 a = cameraPositions[i];
            vec3 b = cameraPositions[j];
            if (!clipSegmentToNearPlane(a, b)) {
                continue;
            }
            vec2 screenA = cameraToScreen(a);
            vec2 screenB = cameraToScreen(b);
            float d = segmentDistance(fragCoord, screenA, screenB);
            float line = 1.0 - smoothstep(lineHalfWidth - 0.5, lineHalfWidth + 0.5, d);
            float glow = exp(-(d * d) / (lineHalfWidth * lineHalfWidth * 6.0));
            col += vec3(INTENSITY * 4.0 * (line + 0.18 * glow));
        }
    }

    // 白点（正方形，发光强度 × INTENSITY）
    for (int i = 0; i < 3; i++) {
        if (i >= count) {
            break;
        }
        vec3 cameraPos = cameraPositions[i];
        if (cameraPos.z < NEAR_CLIP) {
            continue;
        }
        vec2 center = cameraToScreen(cameraPos);
        vec2 p = fragCoord - center;
        float d = max(abs(p.x), abs(p.y));
        float square = 1.0 - smoothstep(halfSize - 0.5, halfSize + 0.5, d);
        float glow = exp(-dot(p, p) / (halfSize * halfSize * 6.0));
        col += vec3(INTENSITY * 4.0 / 3.0 * (square + 0.18 * glow));
    }

    // 透明背景：直接叠加在游戏画面上
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(clamp(scene + col, 0.0, 1.0), 1.0);
}