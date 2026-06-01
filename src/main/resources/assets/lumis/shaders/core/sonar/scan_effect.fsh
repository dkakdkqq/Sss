#version 150

uniform mat4 invViewMat;
uniform mat4 invProjMat;
uniform vec3 pos;
uniform vec3 center;
uniform float radius;
uniform sampler2D depthTex;

uniform float width;
uniform float sharpness;
uniform vec4 outerColor;
uniform vec4 midColor;
uniform vec4 innerColor;
uniform vec4 scanlineColor;
uniform int DebugMode;
uniform vec4 ColorModulator;

in vec2 texCoord;
out vec4 OutColor;

float scanlines() {
    float p1 = fract(gl_FragCoord.y * 0.23);
    float p2 = fract((gl_FragCoord.y + gl_FragCoord.x * 0.045) * 0.165);
    float b1 = smoothstep(0.02, 0.22, p1) * (1.0 - smoothstep(0.58, 0.86, p1));
    float b2 = smoothstep(0.03, 0.18, p2) * (1.0 - smoothstep(0.62, 0.88, p2));
    return clamp(b1 * 0.82 + b2 * 0.36, 0.0, 1.0);
}

vec3 worldpos(vec2 uv, float depth) {
    float z = depth * 2.0 - 1.0;
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePosition = invProjMat * clipSpacePosition;
    viewSpacePosition /= max(viewSpacePosition.w, 1e-6);
    vec4 worldSpacePosition = invViewMat * viewSpacePosition;
    return pos + worldSpacePosition.xyz;
}

void main() {
    vec4 color = vec4(0.0);

    if (DebugMode == 3) {
        OutColor = vec4(1.0, 0.0, 1.0, 0.75);
        return;
    }

    vec2 uv = texCoord;
    float depth = texture(depthTex, uv).r;

    if (DebugMode == 1) {
        float v = (depth >= 1.0) ? 0.0 : (1.0 - depth);
        OutColor = vec4(v, v * 0.35, 0.0, 0.85);
        return;
    }
    if (depth >= 1.0) {
        OutColor = vec4(0.0);
        return;
    }
    vec3 p = worldpos(uv, depth);
    float dist = distance(p, center);

    if (DebugMode == 2) {
        float ringMask = (dist < radius && dist > radius - width) ? 1.0 : 0.0;
        OutColor = vec4(ringMask * 0.45, ringMask * 0.72, 1.0 * ringMask, ringMask * 0.92);
        return;
    }

    if (dist < radius && dist > radius - width) {
        float diff = 1.0 - (radius - dist) / max(width, 1e-5);
        diff = clamp(diff, 0.0, 1.0);

        float edgePower = pow(diff, max(1.0, sharpness * 0.35));
        float line = scanlines();
        float lineMask = 0.20 + 0.80 * line;
        float bodyMask = smoothstep(0.02, 0.22, diff);
        float edgeMask = smoothstep(0.82, 1.0, diff);

        vec4 grad = mix(innerColor, midColor, pow(diff, 0.80));
        grad = mix(grad, outerColor, edgePower);

        vec4 stripe = scanlineColor * (0.28 + 0.72 * line) * (0.48 + 0.52 * bodyMask);
        color = grad * (0.38 + 0.62 * lineMask) + stripe;
        color.rgb += outerColor.rgb * edgeMask * 0.42;
        color.a *= bodyMask * (0.50 + 0.50 * lineMask);
        color.rgb *= 1.35;
        color.a = min(color.a * 1.18, 1.0);

    }

    OutColor = color;
}
