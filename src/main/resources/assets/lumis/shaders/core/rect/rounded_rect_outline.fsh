#version 150

#moj_import <lumis:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float Outline;
uniform vec4 ColorModulator;
uniform vec4 TopLeftColor;
uniform vec4 BottomLeftColor;
uniform vec4 TopRightColor;
uniform vec4 BottomRightColor;

out vec4 OutColor;

vec4 bilinearInterpolation(vec2 uv) {
    vec4 topColor = mix(TopLeftColor, TopRightColor, uv.x);
    vec4 bottomColor = mix(BottomLeftColor, BottomRightColor, uv.x);
    return mix(topColor, bottomColor, uv.y);
}

void main() {
    vec2 center = Size * 0.5;
    vec2 uv = FragCoord;
    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius);

    float outer = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    float inner = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance + Outline);
    float alpha = clamp(outer - inner, 0.0, 1.0);

    vec4 gradientColor = bilinearInterpolation(uv);
    vec4 finalColor = vec4(gradientColor.rgb, gradientColor.a * alpha);

    if (finalColor.a == 0.0) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
