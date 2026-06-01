#version 150

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;

uniform vec4 LeftTopColor;
uniform vec4 LeftBottomColor;
uniform vec4 CenterTopColor;
uniform vec4 CenterBottomColor;
uniform vec4 RightTopColor;
uniform vec4 RightBottomColor;

out vec4 OutColor;

float roundedBoxSDF(vec2 center, vec2 size, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x = (center.y > 0.0) ? radius.x : radius.y;
    vec2 q = abs(center) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
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
    vec2 center = Size * 0.5;
    vec2 uv = FragCoord;

    vec4 gradientColor = gradient6Interpolation(uv);

    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);

    vec4 finalColor = vec4(gradientColor.rgb, gradientColor.a * alpha);

    if (finalColor.a == 0.0) {
        discard;
    }

    OutColor = finalColor;
}