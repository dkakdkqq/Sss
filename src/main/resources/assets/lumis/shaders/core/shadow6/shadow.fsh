#version 150

uniform vec2 Size;
uniform float Softness;
uniform float Radius;
uniform vec4 LeftTopColor;
uniform vec4 LeftBottomColor;
uniform vec4 CenterTopColor;
uniform vec4 CenterBottomColor;
uniform vec4 RightTopColor;
uniform vec4 RightBottomColor;

in vec2 texCoord;
out vec4 fragColor;

float dstfn(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

vec4 interpolate3Colors(vec4 left, vec4 center, vec4 right, float t) {
    if (t < 0.5) {
        return mix(left, center, t * 2.0);
    } else {
        return mix(center, right, (t - 0.5) * 2.0);
    }
}

vec4 gradient6Interpolation(vec2 uv) {
    vec4 leftColor = mix(LeftTopColor, LeftBottomColor, uv.y);
    vec4 centerColor = mix(CenterTopColor, CenterBottomColor, uv.y);
    vec4 rightColor = mix(RightTopColor, RightBottomColor, uv.y);
    return interpolate3Colors(leftColor, centerColor, rightColor, uv.x);
}

void main() {
    vec2 pixel = texCoord * Size;
    vec2 centre = 0.5 * Size;

    vec4 color = gradient6Interpolation(texCoord);

    float dist = dstfn(centre - pixel, centre - Radius - Softness, Radius);
    float shadow = 1.0 - smoothstep(-Softness, Softness, dist);

    fragColor = vec4(color.rgb, color.a * shadow);
}